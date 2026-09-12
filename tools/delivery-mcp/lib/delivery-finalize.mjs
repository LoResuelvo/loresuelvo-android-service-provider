import { execFileSync } from "node:child_process";
import { findRepoRoot, assertSafeRepoPath } from "./repo-root.mjs";
import {
  queryCommitEvidence,
  verifyCommitEvidence,
  resolveRepairChain,
  getCommitEvidence,
  listCommitEvidence,
  getActiveCiIncidents,
} from "./delivery-ledger.mjs";
import { extractUsId } from "./git-snapshot.mjs";
import { inspectCi } from "./ci-provider.mjs";
import { loadDeliveryPolicy } from "./policy-loader.mjs";
import { summarizeFailureOutput } from "./execute-check.mjs";
import { redactSecrets } from "./redact-secrets.mjs";
import { createDeliveryJob, spawnJobWorker, findActiveDeliveryJob } from "./jobs.mjs";

const BATCH_PENDING_CI_STATUSES = new Set(["queued", "in_progress", "not_found"]);
// A missing run is not in flight: polling it forever can turn a provider
// lookup failure into a false green closure. Batch closure keeps its legacy
// window semantics, but close_us must treat not_found as terminal.
const CI_PENDING_STATUSES = new Set(["queued", "in_progress"]);
const CI_TERMINAL_FAILURE_STATUSES = new Set(["failed", "cancelled", "timed_out", "provider_error"]);

export function toCompactCi(ci) {
  if (!ci) return null;
  const compact = {
    sha: ci.sha,
    status: ci.status,
    workflow: ci.workflow ? { id: ci.workflow.id, name: ci.workflow.name } : null,
    url: ci.url || null,
  };
  if (ci.failure) {
    let excerpt = null;
    if (ci.failure.excerpt) {
      const lines = summarizeFailureOutput(ci.failure.excerpt, 6);
      excerpt =
        lines.length > 0
          ? lines.join("\n")
          : redactSecrets(String(ci.failure.excerpt)).split("\n").slice(0, 6).join("\n");
    }
    compact.failure = {
      message: ci.failure.message ? redactSecrets(String(ci.failure.message)).split("\n")[0] : null,
      ...(excerpt ? { excerpt } : {}),
    };
  }
  return compact;
}

function normalizeUsId(usId) {
  if (!usId || typeof usId !== "string") return null;
  const normalized = usId.trim();
  if (!/^[0-9]+(?:\.[0-9]+)?$/.test(normalized)) {
    throw new Error("User Story identifier must contain digits only");
  }
  return normalized;
}

function samePaths(left = [], right = []) {
  const normalizedLeft = [...new Set(left)].sort();
  const normalizedRight = [...new Set(right)].sort();
  return JSON.stringify(normalizedLeft) === JSON.stringify(normalizedRight);
}

async function relevantCommitShas(root, headSha, usId) {
  const normalizedUsId = normalizeUsId(usId);
  if (!normalizedUsId) return [headSha];

  const output = execFileSync("git", ["log", "-n", "200", "--format=%H%x00%s%x00", headSha], {
    cwd: root,
    encoding: "buffer",
  });
  const fields = output.toString("utf8").split("\0").filter(Boolean);
  const matches = [];
  const logShas = [];
  for (let index = 0; index + 1 < fields.length; index += 2) {
    const sha = fields[index].trim();
    const subject = fields[index + 1].trim();
    logShas.push(sha);
    if (normalizeUsId(extractUsId(subject)) === normalizedUsId) matches.push(sha);
  }

  for (const sha of logShas) {
    if (matches.includes(sha)) continue;
    try {
      const entry = await getCommitEvidence({ repoRoot: root, commitSha: sha });
      if (entry && normalizeUsId(entry.usId) === normalizedUsId) {
        matches.push(sha);
      }
    } catch {
      // ignore
    }
  }

  const toCheck = [...matches];
  while (toCheck.length > 0) {
    const currentSha = toCheck.pop();
    try {
      const entry = await getCommitEvidence({ repoRoot: root, commitSha: currentSha });
      if (entry?.repairsSha) {
        const target = entry.repairsSha.toLowerCase();
        if (!matches.includes(target) && logShas.map((s) => s.toLowerCase()).includes(target)) {
          matches.push(target);
          toCheck.push(target);
        }
      }
    } catch {
      // ignore
    }
  }

  if (!matches.includes(headSha)) matches.unshift(headSha);
  return [...new Set(matches)];
}

function readCommittedFeature(root, headSha, featurePath) {
  assertSafeRepoPath(root, featurePath, "Scope feature");
  return execFileSync("git", ["show", `${headSha}:${featurePath}`], {
    cwd: root,
    encoding: "utf8",
    maxBuffer: 5 * 1024 * 1024,
  });
}

export async function finalizeDelivery({
  repoRoot,
  intent = "close_us",
  usId = null,
  scopeFiles = [],
  ciProvider = null,
  waitForCi = false,
  timeoutMs = 900000,
  pollIntervalMs = 10000,
  sleepFn = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  unpushedCommitsResolver = null,
  mode = "sync",
  async: isAsync = false,
} = {}) {
  const root = findRepoRoot(repoRoot);
  try {
    await listCommitEvidence({ repoRoot: root });
  } catch (error) {
    if (
      error?.code === "LEDGER_CORRUPT" ||
      error?.code === "LEDGER_INCONSISTENT" ||
      error?.message?.includes("LEDGER_CORRUPT")
    ) {
      return {
        finalized: false,
        status: "blocked",
        reason: "LEDGER_CORRUPT",
        message: "Cannot finalize: delivery ledger is corrupt and cannot be safely recovered.",
      };
    }
    throw error;
  }
  const policy = await loadDeliveryPolicy({ repoRoot: root });

  let headSha;
  try {
    headSha = execFileSync("git", ["rev-parse", "HEAD"], {
      cwd: root,
      encoding: "utf8",
    }).trim();
  } catch (error) {
    return {
      finalized: false,
      status: "blocked",
      reason: "GIT_ERROR",
      message: `Failed to resolve HEAD commit: ${String(error.message || "unknown").split("\n")[0]}`,
    };
  }

  const verifiedHead = await verifyCommitEvidence({ repoRoot: root, commitSha: headSha });
  if (!verifiedHead.valid) {
    return {
      finalized: false,
      status: "blocked",
      reason: "INVALID_HEAD_EVIDENCE",
      message: `Finalizing requires exact local evidence for HEAD (${verifiedHead.reason})`,
    };
  }

  const evidenceRecord = verifiedHead.record;
  if (evidenceRecord.gate?.id !== "D" || evidenceRecord.status !== "passed") {
    return {
      finalized: false,
      status: "blocked",
      reason: "GATE_D_REQUIRED",
      message: `Finalizing requires passed Gate D evidence on HEAD; observed '${evidenceRecord.gate?.id || "NONE"}'`,
    };
  }
  if (verifiedHead.entry.intent !== intent) {
    return {
      finalized: false,
      status: "blocked",
      reason: "INTENT_EVIDENCE_MISMATCH",
      message: `Finalizing '${intent}' requires matching HEAD evidence; observed '${verifiedHead.entry.intent || "none"}'`,
    };
  }

  const evidenceScope = evidenceRecord.gate?.parameters?.scopeFeatures || [];
  const requestedScope = [...new Set(scopeFiles || [])];
  if (evidenceScope.length === 0) {
    return {
      finalized: false,
      status: "blocked",
      reason: "MISSING_GATE_D_SCOPE",
      message: "Gate D evidence does not contain a completed feature scope",
    };
  }
  if (requestedScope.length > 0 && !samePaths(requestedScope, evidenceScope)) {
    return {
      finalized: false,
      status: "blocked",
      reason: "SCOPE_EVIDENCE_MISMATCH",
      message: "Requested closure scope does not match the scope verified by Gate D",
    };
  }

  const wipViolations = [];
  for (const feature of evidenceScope) {
    let content;
    try {
      content = readCommittedFeature(root, headSha, feature);
    } catch {
      return {
        finalized: false,
        status: "blocked",
        reason: "SCOPE_FILE_MISSING",
        message: `Committed feature scope file is unavailable at HEAD: ${feature}`,
      };
    }
    content.split(/\r?\n/).forEach((line, index) => {
      if (/(?:^|\s)@wip(?:\s|$)/.test(line)) wipViolations.push(`${feature}:${index + 1}`);
    });
  }

  if (wipViolations.length > 0) {
    return {
      finalized: false,
      status: "blocked",
      reason: "WIP_IN_SCOPE",
      message: `Cannot finalize: @wip remains in committed scope at ${wipViolations.slice(0, 6).join(", ")}`,
      locations: wipViolations.slice(0, 20),
    };
  }

  let unpushedCommits = [];
  let remoteVerification = "passed";
  if (typeof unpushedCommitsResolver === "function") {
    try {
      const resolved = await unpushedCommitsResolver({ repoRoot: root, headSha });
      unpushedCommits = Array.isArray(resolved) ? resolved : (resolved ? [resolved] : []);
    } catch (error) {
      remoteVerification = "unavailable";
      return {
        finalized: false,
        status: "blocked",
        reason: "REMOTE_VERIFICATION_UNAVAILABLE",
        remoteReason: "UPSTREAM_UNAVAILABLE",
        remoteVerification,
        message: `Cannot finalize: unable to verify whether HEAD is pushed (${String(error?.message || "remote check failed").split("\n")[0].slice(0, 160)}).`,
      };
    }
  } else {
    let remoteVerified = false;
    try {
      const unpushed = execFileSync("git", ["log", "@{u}..HEAD", "--oneline"], {
        cwd: root,
        encoding: "utf8",
        stdio: ["ignore", "pipe", "pipe"],
      }).trim();
      if (unpushed) unpushedCommits = unpushed.split("\n");
      remoteVerified = true;
    } catch {
      try {
        const unpushed = execFileSync("git", ["log", "origin/main..HEAD", "--oneline"], {
          cwd: root,
          encoding: "utf8",
          stdio: ["ignore", "pipe", "pipe"],
        }).trim();
        if (unpushed) unpushedCommits = unpushed.split("\n");
        remoteVerified = true;
      } catch {
        // Neither upstream nor origin/main could be resolved.
      }
    }
    if (!remoteVerified) {
      remoteVerification = "unavailable";
      return {
        finalized: false,
        status: "blocked",
        reason: "REMOTE_VERIFICATION_UNAVAILABLE",
        remoteReason: "UPSTREAM_UNAVAILABLE",
        remoteVerification,
        message: "Cannot finalize: unable to verify whether HEAD is pushed to an upstream or origin/main.",
      };
    }
  }

  if (unpushedCommits.length > 0) {
    return {
      finalized: false,
      status: "blocked",
      reason: "UNPUSHED_COMMITS",
      message: `Cannot finalize: unpushed commits include ${unpushedCommits.slice(0, 3).join("; ")}`,
    };
  }

  const requestedUsId = normalizeUsId(usId);
  const evidenceUsId = normalizeUsId(verifiedHead.entry.usId);
  if (requestedUsId && evidenceUsId && requestedUsId !== evidenceUsId) {
    return {
      finalized: false,
      status: "blocked",
      reason: "US_EVIDENCE_MISMATCH",
      message: `Requested User Story '${requestedUsId}' does not match HEAD evidence '${evidenceUsId}'`,
    };
  }

  const effectiveUsId = requestedUsId || evidenceUsId || null;
  const shas = await relevantCommitShas(root, headSha, effectiveUsId);
  const unverifiedCommits = [];
  for (const sha of shas) {
    const evidence = await queryCommitEvidence({ repoRoot: root, commitSha: sha });
    if (evidence.state === "missing") {
      return {
        finalized: false,
        status: "blocked",
        reason: "MISSING_COMMIT_EVIDENCE",
        message: `Commit ${sha.slice(0, 8)} lacks delivery ledger entry`,
        sha,
        supersededFailures: [],
        pendingFailures: [],
        failedRepairs: [],
        invalidRepairs: [],
      };
    }
    if (evidence.state === "corrupt") {
      return {
        finalized: false,
        status: "blocked",
        reason: "CORRUPT_COMMIT_EVIDENCE",
        message: `Commit ${sha.slice(0, 8)} has corrupt delivery evidence (${evidence.reason})`,
        sha,
        supersededFailures: [],
        pendingFailures: [],
        failedRepairs: [],
        invalidRepairs: [],
      };
    }
    if (evidence.state === "not_run") {
      unverifiedCommits.push(sha);
    }
  }

  const startTime = Date.now();

  while (true) {
    let repairResolution;
    let activeIncidents = [];
    try {
      repairResolution = await resolveRepairChain({
        repoRoot: root,
        commits: shas,
        ciProvider,
      });
      activeIncidents = await getActiveCiIncidents({ repoRoot: root, ciProvider });
    } catch (error) {
      if (
        error?.code === "LEDGER_CORRUPT" ||
        error?.code === "LEDGER_INCONSISTENT" ||
        error?.message?.includes("LEDGER_CORRUPT")
      ) {
        return {
          finalized: false,
          status: "blocked",
          reason: "LEDGER_CORRUPT",
          message: "Cannot finalize: delivery ledger is corrupt and cannot be safely recovered.",
        };
      }
      throw error;
    }
    const supersededFailures = [
      ...(repairResolution.supersededFailures || []),
      ...((activeIncidents.allIncidents || [])
        .filter((incident) => incident.status === "superseded")
        .map((incident) => incident.failedSha)),
    ].filter((sha, index, all) => all.indexOf(sha) === index);
    const failedRepairs = repairResolution.failedRepairs || [];
    const invalidRepairs = repairResolution.invalidRepairs || [];
    const supersededSet = new Set(supersededFailures.map((s) => s.toLowerCase()));

    const unresolvedIncidents = activeIncidents.filter((inc) =>
      ["repair_required", "repair_failed", "repair_prepared", "repair_submitted"].includes(inc.status) &&
      !supersededSet.has(inc.failedSha.toLowerCase())
    );

    if (invalidRepairs.length > 0) {
      return {
        finalized: false,
        status: "blocked",
        reason: "INVALID_REPAIRS",
        message: `Cannot finalize: observed ${invalidRepairs.length} invalid repair relation(s)`,
        supersededFailures,
        pendingFailures: [],
        failedRepairs,
        invalidRepairs,
        activeCiIncidents: unresolvedIncidents,
      };
    }

    const shouldRunAsJob =
      isAsync ||
      mode === "job" ||
      (waitForCi && mode === "auto" && !ciProvider && timeoutMs > 60000);

    if (shouldRunAsJob) {
      const jobRunKey = `finalize-${headSha}-${intent}`;
      const activeJob = await findActiveDeliveryJob({ repoRoot: root, runKey: jobRunKey });
      if (activeJob) {
        return {
          finalized: false,
          status: "running",
          jobId: activeJob.jobId,
          message: `Finalization job '${activeJob.jobId}' is already running. Use delivery_job_wait to await completion.`,
        };
      }

      const job = await createDeliveryJob({
        repoRoot: root,
        type: "finalize",
        params: {
          intent,
          usId,
          scopeFiles,
          waitForCi: true,
          timeoutMs,
          pollIntervalMs,
          mode: "sync",
        },
        runKey: jobRunKey,
        snapshotHash: headSha,
        gateId: "D",
      });

      await spawnJobWorker({ repoRoot: root, jobId: job.jobId });

      return {
        finalized: false,
        status: "job_started",
        jobId: job.jobId,
        message: `Finalization with CI wait started as recoverable background job '${job.jobId}'. Use delivery_job_wait to await completion.`,
      };
    }

    const ciResults = [];
    const pendingCi = [];
    const pendingFailures = [];
    const pendingShas = [];
    let terminalFailureCi = null;
    let terminalFailureSha = null;

    for (const sha of shas) {
      const ci = await inspectCi({ sha, repoRoot: root, provider: ciProvider });
      ciResults.push(ci);
      const normalizedSha = sha.toLowerCase();

      if (supersededSet.has(normalizedSha)) {
        // Historical failure formally superseded by a green repair or a green
        // descendant after CI concurrency cancellation; it does not block.
        continue;
      }

      if (ci.status !== "passed") {
        const isPending = waitForCi
          ? CI_PENDING_STATUSES.has(ci.status)
          : (ci.status === "in_progress" || ci.status === "queued");

        if (intent === "close_batch" && !waitForCi && BATCH_PENDING_CI_STATUSES.has(ci.status)) {
          pendingCi.push(ci);
          continue;
        }

        if (isPending) {
          pendingShas.push(sha);
        } else {
          if (!terminalFailureCi) {
            terminalFailureCi = ci;
            terminalFailureSha = sha;
          }
        }

        pendingFailures.push(sha);
      }
    }

    // Salida temprana inmediata ante fallo de CI
    if (terminalFailureCi) {
      return {
        finalized: false,
        status: "blocked",
        reason: "CI_NOT_GREEN",
        message: `CI for commit ${terminalFailureSha.slice(0, 8)} is '${terminalFailureCi.status || "failed"}'${terminalFailureCi.failure?.message ? `: ${terminalFailureCi.failure.message}` : ""}`,
        sha: terminalFailureSha,
        ci: toCompactCi(terminalFailureCi),
        supersededFailures,
        pendingFailures,
        failedRepairs,
        invalidRepairs,
        activeCiIncidents: unresolvedIncidents,
      };
    }

    // Bloqueo si existen incidentes activos no subsanados
    if (unresolvedIncidents.length > 0) {
      const targetInc = unresolvedIncidents[0];
      const deliveryLabel = intent === "close_batch" ? "batch" : "User Story";
      return {
        finalized: false,
        status: "blocked",
        reason: "ACTIVE_CI_INCIDENT",
        message: `Cannot finalize ${deliveryLabel}: active CI incident on commit ${targetInc.failedSha.slice(0, 8)} (${targetInc.status}) has not been resolved.`,
        sha: targetInc.failedSha,
        activeIncident: targetInc,
        activeCiIncidents: unresolvedIncidents,
        supersededFailures,
        pendingFailures,
        failedRepairs,
        invalidRepairs,
      };
    }

    // Cierre exitoso: todos los commits requeridos pasaron CI
    if (pendingFailures.length === 0 && pendingCi.length === 0) {
      const deliveryLabel = intent === "close_batch" ? "Batch" : "User Story";
      return {
        finalized: true,
        status: "passed",
        intent,
        usId: effectiveUsId,
        headSha,
        shas,
        unverifiedCommits,
        remoteVerification: "passed",
        pendingCi: [],
        maxInFlightCommits: policy.ci.maxInFlightCommits,
        message: `${deliveryLabel} ${effectiveUsId ? `'${effectiveUsId}' ` : ""}finalized with Gate D and green CI`,
        ci: ciResults.map(toCompactCi),
        supersededFailures,
        pendingFailures: [],
        failedRepairs,
        invalidRepairs,
        activeCiIncidents: [],
      };
    }

    // Si waitForCi es false: comportamiento inmediato actual
    if (!waitForCi) {
      if (pendingFailures.length > 0) {
        const firstFailedSha = pendingFailures[0];
        const firstFailedCi = ciResults.find((c) => c.sha.toLowerCase() === firstFailedSha.toLowerCase());
        const isPending = firstFailedCi && (firstFailedCi.status === "in_progress" || firstFailedCi.status === "queued");

        return {
          finalized: false,
          status: isPending ? "in_progress" : "blocked",
          reason: isPending ? "CI_IN_PROGRESS" : "CI_NOT_GREEN",
          message: `CI for commit ${firstFailedSha.slice(0, 8)} is '${firstFailedCi?.status || "failed"}'${firstFailedCi?.failure?.message ? `: ${firstFailedCi.failure.message}` : ""}`,
          sha: firstFailedSha,
          ci: toCompactCi(firstFailedCi),
          supersededFailures,
          pendingFailures,
          failedRepairs,
          invalidRepairs,
        };
      }

      if (intent === "close_batch" && pendingCi.length > policy.ci.maxInFlightCommits) {
        return {
          finalized: false,
          status: "blocked",
          reason: "CI_PENDING_WINDOW_EXCEEDED",
          message: `Cannot close batch: ${pendingCi.length} commits remain in flight (maximum ${policy.ci.maxInFlightCommits})`,
          pendingCi: pendingCi.map(toCompactCi),
          maxInFlightCommits: policy.ci.maxInFlightCommits,
          supersededFailures,
          pendingFailures,
          failedRepairs,
          invalidRepairs,
        };
      }

      const hasPendingCi = pendingCi.length > 0;
      return {
        finalized: true,
        status: hasPendingCi ? "passed_pending_ci" : "passed",
        intent,
        usId: effectiveUsId,
        headSha,
        shas,
        unverifiedCommits,
        remoteVerification: hasPendingCi ? "pending" : "passed",
        pendingCi: pendingCi.map(toCompactCi),
        maxInFlightCommits: policy.ci.maxInFlightCommits,
        message: `Batch ${effectiveUsId ? `'${effectiveUsId}' ` : ""}closed locally with Gate D; ${pendingCi.length} CI run(s) remain pending`,
        ci: ciResults.map(toCompactCi),
        supersededFailures,
        pendingFailures,
        failedRepairs,
        invalidRepairs,
      };
    }

    // Espera acotada con waitForCi: true
    const elapsed = Date.now() - startTime;
    if (elapsed >= timeoutMs) {
      return {
        finalized: false,
        status: "in_progress",
        reason: "CI_TIMEOUT",
        pending: pendingShas,
        message: "Timed out waiting for CI completion",
      };
    }

    const remaining = timeoutMs - elapsed;
    const sleepDuration = Math.min(pollIntervalMs, remaining);
    await sleepFn(sleepDuration);
  }
}

export { verifyHeadDelivery } from "./verify-head.mjs";
