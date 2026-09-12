import crypto from "node:crypto";
import { execFileSync } from "node:child_process";
import { findRepoRoot, assertSafeRepoPath } from "./repo-root.mjs";
import { loadDeliveryPolicy } from "./policy-loader.mjs";
import { captureGitSnapshot, extractUsId } from "./git-snapshot.mjs";
import { runGate } from "./run-gate.mjs";
import {
  recordCommitEvidence,
  verifyCommitEvidence,
  loadEvidenceRecord,
} from "./delivery-ledger.mjs";
import { saveDeliveryContext } from "./delivery-context.mjs";
import { createDeliveryJob, spawnJobWorker, findActiveDeliveryJob } from "./jobs.mjs";

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

export async function verifyHeadDelivery({
  repoRoot,
  intent = "close_us",
  usId = null,
  scopeFiles = [],
  force = false,
  executeCheck = null,
  mode = "sync",
  async: isAsync = false,
  // Internal marker used by job-runner. The worker already owns its job and
  // must execute Gate D instead of discovering that same job as active.
  workerJobId = null,
} = {}) {
  const root = findRepoRoot(repoRoot);
  const policy = await loadDeliveryPolicy({ repoRoot: root });
  const policyHash = policy.sourceHash || policy.hash;

  // 1. Capture snapshot to validate working tree
  let snapshot;
  try {
    snapshot = await captureGitSnapshot({ cwd: root });
  } catch (error) {
    return {
      verified: false,
      status: "blocked",
      reason: "GIT_ERROR",
      message: `Failed to capture working tree state: ${String(error.message || "unknown").split("\n")[0]}`,
    };
  }

  // 2. Validate working tree cleanliness
  if ((snapshot.unstagedConflicts || []).length > 0) {
    return {
      verified: false,
      status: "blocked",
      reason: "UNSTAGED_CONFLICT",
      message: `Unstaged conflicts detected: ${snapshot.unstagedConflicts.join(", ")}`,
    };
  }
  if ((snapshot.stagedFiles || []).length > 0) {
    return {
      verified: false,
      status: "blocked",
      reason: "DIRTY_WORKTREE",
      message: `Working tree has staged changes outside committed HEAD: ${snapshot.stagedFiles.join(", ")}`,
    };
  }
  if ((snapshot.unrelatedUnstaged || []).length > 0) {
    return {
      verified: false,
      status: "blocked",
      reason: "DIRTY_WORKTREE",
      message: `Working tree has unstaged modifications: ${snapshot.unrelatedUnstaged.join(", ")}`,
    };
  }
  if ((snapshot.untracked || []).length > 0) {
    return {
      verified: false,
      status: "blocked",
      reason: "DIRTY_WORKTREE",
      message: `Working tree has untracked files: ${snapshot.untracked.join(", ")}`,
    };
  }

  // 3. Obtain headSha
  const headSha = snapshot.headSha;
  if (!headSha || headSha === "UNKNOWN") {
    return {
      verified: false,
      status: "blocked",
      reason: "GIT_ERROR",
      message: "Failed to resolve HEAD commit",
    };
  }

  const parentsLine = execFileSync("git", ["rev-list", "--parents", "-n", "1", headSha], {
    cwd: root,
    encoding: "utf8",
  }).trim();
  const [, ...parents] = parentsLine.split(/\s+/).filter(Boolean);
  if (parents.length > 1) {
    return {
      verified: false,
      status: "blocked",
      reason: "MERGE_COMMIT_NOT_ALLOWED",
      message: "verify_head does not support merge commits with multiple parents",
    };
  }

  const requestedScope = [...new Set(scopeFiles || [])].sort();
  const requestedUsId = normalizeUsId(usId);

  // 4. Check if headSha already possesses valid Gate D evidence matching intent, scope, and policy
  if (!force) {
    const verifiedHead = await verifyCommitEvidence({ repoRoot: root, commitSha: headSha });
    if (verifiedHead.valid && verifiedHead.entry && verifiedHead.record) {
      const entry = verifiedHead.entry;
      const record = verifiedHead.record;
      const evidenceScope = [
        ...new Set(record.gate?.parameters?.scopeFeatures || entry.scopeFiles || []),
      ].sort();
      const evidencePolicyHash = record.policy?.hash || entry.policyHash;
      const evidenceUsId = normalizeUsId(entry.usId);

      const isGateD =
        (record.gate?.id === "D" || entry.gateId === "D") &&
        record.status === "passed" &&
        entry.status === "passed";
      const matchesIntent = entry.intent === intent;
      const matchesPolicy = evidencePolicyHash === policyHash;
      const matchesScope =
        evidenceScope.length > 0 &&
        (requestedScope.length === 0 || samePaths(requestedScope, evidenceScope));
      const matchesUs = !requestedUsId || !evidenceUsId || requestedUsId === evidenceUsId;

      if (isGateD && matchesIntent && matchesPolicy && matchesScope && matchesUs) {
        return {
          verified: true,
          status: "passed",
          headSha,
          gate: "D",
          cached: true,
          summary: record.summary || {
            passed: record.checks?.length || 0,
            failed: 0,
            skipped: 0,
            durationMs: 0,
          },
          diagnostics: record.diagnostics || [],
          checks: record.checks || [],
          evidence: { recordPath: entry.recordPath },
          scopeFiles: evidenceScope,
          usId: entry.usId || null,
          intent,
        };
      }
    }
  }

  // 5. If no valid evidence for headSha, execute Gate D. The completed
  // feature scope is explicit so closure cannot silently expand based on
  // recent history or an unrelated file in the HEAD commit.
  let commitFiles = [];
  try {
    const rawFiles = execFileSync(
      "git",
      ["diff-tree", "--root", "--no-commit-id", "--name-only", "-r", "-z", headSha],
      { cwd: root, encoding: "buffer" }
    );
    commitFiles = rawFiles.toString("utf8").split("\0").filter(Boolean).sort();
  } catch {
    commitFiles = [];
  }
  const resolvedScope = requestedScope;

  if (resolvedScope.length === 0) {
    return {
      verified: false,
      status: "blocked",
      reason: "MISSING_SCOPE_FOR_GATE_D",
      message:
        "Gate D requires at least one feature path to verify that completed scope has no @wip tags",
    };
  }

  for (const feature of resolvedScope) {
    assertSafeRepoPath(root, feature, "Scope feature");
    try {
      execFileSync("git", ["cat-file", "-e", `${headSha}:${feature}`], {
        cwd: root,
        stdio: ["ignore", "ignore", "ignore"],
      });
    } catch {
      return {
        verified: false,
        status: "blocked",
        reason: "SCOPE_FILE_MISSING",
        message: `Scope feature file is unavailable at HEAD: ${feature}`,
      };
    }
  }

  // Gate D can exceed an MCP client's request deadline.  A verify-head job
  // keeps the exact HEAD/scope identity in a recoverable record while the
  // worker runs the same synchronous implementation below.
  const shouldRunAsJob =
    !workerJobId &&
    (isAsync || mode === "job" || (mode === "auto" && !executeCheck));
  if (shouldRunAsJob) {
    const scopeKey = resolvedScope.join("\n");
    const runKey = `verify-head-${crypto
      .createHash("sha256")
      .update(`${headSha}|${intent}|${policyHash}|${scopeKey}`)
      .digest("hex")}`;
    const activeJob = await findActiveDeliveryJob({ repoRoot: root, runKey });
    if (activeJob) {
      return {
        verified: false,
        status: "running",
        jobId: activeJob.jobId,
        headSha,
        gate: "D",
        scopeFiles: resolvedScope,
        message: `Gate D verification job '${activeJob.jobId}' is already running. Use delivery_job_wait to await completion.`,
      };
    }

    const job = await createDeliveryJob({
      repoRoot: root,
      type: "verify_head",
      params: {
        intent,
        usId,
        scopeFiles: resolvedScope,
        force,
        mode: "sync",
      },
      runKey,
      snapshotHash: snapshot.snapshotHash,
      gateId: "D",
    });
    await spawnJobWorker({ repoRoot: root, jobId: job.jobId });
    return {
      verified: false,
      status: "job_started",
      jobId: job.jobId,
      headSha,
      gate: "D",
      scopeFiles: resolvedScope,
      message: `Gate D verification started as recoverable background job '${job.jobId}'. Use delivery_job_wait to await completion.`,
    };
  }

  let inferredUsId = null;
  try {
    const committedMessage = execFileSync("git", ["log", "-1", "--format=%B", headSha], {
      cwd: root,
      encoding: "utf8",
    });
    inferredUsId = extractUsId(committedMessage);
  } catch {
    // ignore
  }

  const effectiveUsId =
    requestedUsId || inferredUsId || snapshot.proposedUsId || snapshot.primaryRecentUsId || null;
  const gateDefinition = policy.gates.D;
  const reasonCode = intent === "close_batch" ? "INTENT_CLOSE_BATCH" : "INTENT_CLOSE_US";

  const inspection = {
    schemaVersion: 1,
    snapshotHash: snapshot.snapshotHash,
    repository: {
      branch: snapshot.branch,
      headSha,
      usId: effectiveUsId,
    },
    policy: {
      version: policy.version,
      hash: policyHash,
    },
    gate: {
      id: "D",
      reasonCodes: [reasonCode],
      checkIds: gateDefinition.checkIds,
      parameters: {
        scopeFeatures: resolvedScope,
      },
      postPushChecks: gateDefinition.postPushChecks || ["ci_green"],
    },
    diagnostics: [],
  };

  const outcome = await runGate({
    inspection,
    snapshot,
    policy,
    repoRoot: root,
    force,
    ...(executeCheck ? { executeCheck } : {}),
  });

  if (outcome.status !== "passed") {
    return {
      verified: false,
      status: outcome.status,
      headSha,
      gate: "D",
      cached: Boolean(outcome.cached),
      summary: outcome.summary,
      diagnostics: outcome.diagnostics || [],
      checks: outcome.checks || [],
      evidence: outcome.evidence || { recordPath: null },
      failure: outcome.failure || null,
      reason: outcome.failure?.checkId || "GATE_D_FAILED",
      message:
        outcome.failure?.message ||
        outcome.diagnostics?.[0]?.message ||
        "Gate D checks failed",
    };
  }

  // 6. Record evidence in the ledger tied directly to headSha
  const treeSha = execFileSync("git", ["rev-parse", `${headSha}^{tree}`], {
    cwd: root,
    encoding: "utf8",
  }).trim();

  const loadedRecord = await loadEvidenceRecord({
    repoRoot: root,
    recordPath: outcome.evidence.recordPath,
  });

  await recordCommitEvidence({
    repoRoot: root,
    commitSha: headSha,
    status: "passed",
    verificationStatus: "passed",
    snapshotHash: outcome.snapshotHash,
    runKey: outcome.runKey,
    recordPath: outcome.evidence.recordPath,
    recordDigest: loadedRecord.digest,
    branch: snapshot.branch,
    parentSha: parents[0] || null,
    treeSha,
    stagedFiles: commitFiles,
    gateId: "D",
    policyHash,
    intent,
    usId: effectiveUsId,
    scopeFiles: resolvedScope,
  });

  try {
    await saveDeliveryContext({
      repoRoot: root,
      snapshot,
      intent,
      usId: effectiveUsId,
      scopeFiles: resolvedScope,
    });
  } catch {
    // context saving is best-effort
  }

  return {
    verified: true,
    status: "passed",
    headSha,
    gate: "D",
    cached: false,
    summary: outcome.summary,
    diagnostics: outcome.diagnostics || [],
    checks: outcome.checks || [],
    evidence: outcome.evidence,
    scopeFiles: resolvedScope,
    usId: effectiveUsId,
    intent,
  };
}
