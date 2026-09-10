import fs from "node:fs/promises";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { findRepoRoot } from "./repo-root.mjs";
import {
  loadDeliveryContext,
  consumeDeliveryContext,
  validateDeliveryContext,
} from "./delivery-context.mjs";
import { captureGitSnapshot, extractUsId } from "./git-snapshot.mjs";
import {
  consumePreparedEvidence,
  recordCommitEvidence,
  getLastPreparedEvidence,
  listCommitEvidence,
  verifyPreparedEvidence,
  queryCommitEvidence,
  markRepairPushConsumed,
  resolveRepairChain,
  validateRepairLineage,
  acquireRepairLock,
  authorizeRepairPush,
  getActiveCiIncidents,
  evaluateCiWindow,
  matchesTarget,
} from "./delivery-ledger.mjs";
import { inspectCi } from "./ci-provider.mjs";
import { loadDeliveryPolicy } from "./policy-loader.mjs";


const ALLOWED_TYPES = new Set([
  "chore",
  "feat",
  "docs",
  "test",
  "ci",
  "fix",
  "refactor",
  "build",
  "style",
  "perf",
]);

function normalizeUsId(usId) {
  if (!usId || typeof usId !== "string") return null;
  return usId.trim();
}

/**
 * Validates commit message structure according to Lo Resuelvo commit governance.
 * - Allowed types follow repository commit governance, including feat for product work
 * - Rejects scopes in parentheses like '(agent)' or '(scope)'
 * - Validates US ID against active context
 */
export function validateCommitMessage(rawMessage, activeContext = null) {
  if (!rawMessage || typeof rawMessage !== "string") {
    return { valid: false, reason: "EMPTY_MESSAGE", message: "Commit message cannot be empty" };
  }

  // Strip git comments and leading/trailing empty lines
  const lines = rawMessage
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"));

  if (lines.length === 0) {
    return { valid: false, reason: "EMPTY_MESSAGE", message: "Commit message cannot be empty" };
  }

  const subject = lines[0];

  // 1. Rejects (agent) and any parentheses
  if (subject.includes("(agent)")) {
    return {
      valid: false,
      reason: "AGENT_SCOPE_FORBIDDEN",
      message: "Commit message cannot contain '(agent)'",
    };
  }
  if (/\([^)]*\)/.test(subject)) {
    return {
      valid: false,
      reason: "PAREN_SCOPE_FORBIDDEN",
      message:
        "Scopes in parentheses are forbidden in commit messages. Use <type>[XX]: description or <type>: description.",
    };
  }

  // 2. Format: <type>[33]: <description> or <type>: <description>.
  // The migration contract uses a numeric User Story id; [US-33] must not
  // silently become an unassociated commit.
  const match = subject.match(/^([a-zA-Z]+)(?:\[([0-9]+)\])?:\s+(.+)$/);
  if (!match) {
    if (/^[a-zA-Z]+\[[0-9]+\]:\s*$/.test(subject)) {
      return {
        valid: false,
        reason: "EMPTY_DESCRIPTION",
        message: "Commit message description cannot be empty",
      };
    }
    if (/^[a-zA-Z]+\[[^\]]+\]:/.test(subject)) {
      return {
        valid: false,
        reason: "INVALID_US_ID",
        message: "User Story identifiers must be numeric, for example '<type>[33]: description'; '[US-33]' is not valid.",
      };
    }
    return {
      valid: false,
      reason: "INVALID_FORMAT",
      message:
        "Invalid commit message format. Expected '<type>[XX]: description' or '<type>: description'",
    };
  }

  const [, rawType, usId, description] = match;
  const type = rawType.toLowerCase();

  if (!ALLOWED_TYPES.has(type)) {
    return {
      valid: false,
      reason: "INVALID_TYPE",
      message: `Invalid commit type '${rawType}'. Allowed types are: ${Array.from(ALLOWED_TYPES).join(", ")}`,
    };
  }

  if (!description || description.trim().length === 0) {
    return {
      valid: false,
      reason: "EMPTY_DESCRIPTION",
      message: "Commit message description cannot be empty",
    };
  }

  // 3. Validate US ID against active context
  if (activeContext && !activeContext.consumed) {
    if (activeContext.usId) {
      if (usId) {
        const normMsgUs = normalizeUsId(usId);
        const normCtxUs = normalizeUsId(activeContext.usId);
        if (normMsgUs !== normCtxUs && usId !== activeContext.usId) {
          return {
            valid: false,
            reason: "CONTEXT_US_CONFLICT",
            message: `Commit message US ID (${usId}) contradicts active delivery context US ID (${activeContext.usId})`,
          };
        }
      } else if (activeContext.intent === "close_us") {
        return {
          valid: false,
          reason: "MISSING_US_IN_MESSAGE",
          message: `Active delivery context specifies US '${activeContext.usId}', which must be declared as [${activeContext.usId}] in commit message`,
        };
      }
    }
  }

  return { valid: true, type, usId: usId || null, description };
}

function rejectDeprecatedCiBypass() {
  if (process.env.DELIVERY_SKIP_CI_CHECK !== undefined) {
    return {
      passed: false,
      reason: "DEPRECATED_CI_BYPASS_REJECTED",
      message:
        "DELIVERY_SKIP_CI_CHECK is deprecated and forbidden. Use repair_ci workflow for CI failure remediation.",
    };
  }
  return null;
}

export async function runPreCommitHook({ repoRoot } = {}) {
  const deprecatedBypass = rejectDeprecatedCiBypass();
  if (deprecatedBypass) return deprecatedBypass;

  const root = findRepoRoot(repoRoot);
  const requireEvidence = process.env.DELIVERY_REQUIRE_EVIDENCE === "1";

  let snapshot;
  try {
    snapshot = await captureGitSnapshot({ cwd: root });
  } catch (error) {
    return {
      passed: false,
      reason: "GIT_ERROR",
      message: `Failed to capture staged snapshot: ${error.message}`,
    };
  }

  const receipt = await verifyPreparedEvidence({ repoRoot: root, snapshot });
  const verifiedReason = receipt.reason;

  if (receipt.valid) {
    return {
      passed: true,
      verified: true,
      gateId: receipt.prepared.gateId || "NONE",
      prepared: receipt.prepared,
    };
  }

  if (requireEvidence) {
    return {
      passed: false,
      verified: false,
      reason: verifiedReason,
      message: `Delivery evidence required (DELIVERY_REQUIRE_EVIDENCE=1). Invoke delivery_prepare for the staged snapshot before committing.`,
    };
  }

  return {
    passed: true,
    verified: false,
    reason: verifiedReason,
    warning: `Proceeding without verified delivery evidence (not_run). Use delivery_prepare to verify gates locally.`,
  };
}

export async function runCommitMsgHook({ repoRoot, messageFilePath } = {}) {
  const deprecatedBypass = rejectDeprecatedCiBypass();
  if (deprecatedBypass) return deprecatedBypass;

  const root = findRepoRoot(repoRoot);
  if (!messageFilePath) {
    throw new Error("Missing commit message file path parameter");
  }

  const absPath = path.isAbsolute(messageFilePath)
    ? messageFilePath
    : path.resolve(root, messageFilePath);
  const content = await fs.readFile(absPath, "utf8");
  const storedContext = await loadDeliveryContext({ repoRoot: root });
  let activeContext = null;
  let contextValidation = null;

  if (storedContext) {
    try {
      const snapshot = await captureGitSnapshot({ cwd: root, proposedCommitMessage: content });
      contextValidation = validateDeliveryContext({
        context: storedContext,
        snapshot,
        proposedCommitMessage: content,
      });
      if (contextValidation.valid || contextValidation.conflict) {
        activeContext = storedContext;
      }
    } catch {
      contextValidation = { valid: false, expired: true, reason: "CONTEXT_SNAPSHOT_UNAVAILABLE" };
    }
  }

  const validation = validateCommitMessage(content, activeContext);
  if (!validation.valid) {
    return {
      passed: false,
      reason: validation.reason,
      message: validation.message,
    };
  }

  return { passed: true, validation, contextValidation };
}

async function getMatchingManualRepairContext({ repoRoot, committedMessage, parentSha, branch, treeSha }) {
  try {
    const context = await loadDeliveryContext({ repoRoot });
    const messageValidation = validateCommitMessage(committedMessage, context);
    const repairsShaIsValid = /^[a-f0-9]{7,40}$/i.test(context?.repairsSha || "");
    const matchesCommittedSnapshot =
      context?.consumed === false &&
      context.intent === "repair_ci" &&
      repairsShaIsValid &&
      context.headSha === parentSha &&
      context.branch === branch &&
      Boolean(context.stagedTreeSha) &&
      context.stagedTreeSha === treeSha &&
      messageValidation.valid;
    return matchesCommittedSnapshot ? context : null;
  } catch {
    return null;
  }
}

export async function runPostCommitHook({ repoRoot } = {}) {
  const root = findRepoRoot(repoRoot);
  let commitSha = "";
  try {
    commitSha = execFileSync("git", ["rev-parse", "HEAD"], {
      cwd: root,
      encoding: "utf8",
    }).trim();
  } catch (error) {
    throw new Error(`Failed to resolve HEAD commit: ${error.message}`);
  }

  const alreadyRecorded = await queryCommitEvidence({ repoRoot: root, commitSha });
  if (alreadyRecorded.valid) {
    return {
      recorded: true,
      commitSha,
      ledgerEntry: alreadyRecorded.entry,
      reused: true,
      verificationStatus: "passed",
    };
  }
  if (alreadyRecorded.state === "corrupt") {
    return {
      recorded: false,
      blocked: true,
      commitSha,
      reason: "CORRUPT_COMMIT_EVIDENCE",
      evidenceReason: alreadyRecorded.reason,
    };
  }
  if (alreadyRecorded.state === "not_run") {
    return {
      recorded: true,
      commitSha,
      ledgerEntry: alreadyRecorded.entry,
      reused: true,
      verificationStatus: "not_run",
      reason: alreadyRecorded.reason,
    };
  }

  const committedMessage = execFileSync("git", ["log", "-1", "--format=%B", commitSha], {
    cwd: root,
    encoding: "utf8",
  });
  const committedMessageValidation = validateCommitMessage(committedMessage);
  const inferredUsId = committedMessageValidation.valid
    ? committedMessageValidation.usId
    : extractUsId(committedMessage);

  const parentsLine = execFileSync("git", ["rev-list", "--parents", "-n", "1", commitSha], {
    cwd: root,
    encoding: "utf8",
  }).trim();
  const [, ...parents] = parentsLine.split(/\s+/).filter(Boolean);
  const branch = execFileSync("git", ["rev-parse", "--abbrev-ref", "HEAD"], {
    cwd: root,
    encoding: "utf8",
  }).trim();
  const treeSha = execFileSync("git", ["rev-parse", "HEAD^{tree}"], {
    cwd: root,
    encoding: "utf8",
  }).trim();

  const manualRepairContext = await getMatchingManualRepairContext({
    repoRoot: root,
    committedMessage,
    parentSha: parents[0] || null,
    branch,
    treeSha,
  });

  let commitFiles = [];
  try {
    const rawFiles = execFileSync(
      "git",
      ["diff-tree", "--root", "--no-commit-id", "--name-only", "-r", "-z", commitSha],
      { cwd: root, encoding: "buffer" }
    );
    commitFiles = rawFiles.toString("utf8").split("\0").filter(Boolean).sort();
  } catch {
    commitFiles = [];
  }

  const prepared = await getLastPreparedEvidence({ repoRoot: root });
  let matchingReceipt = null;

  if (prepared && parents.length <= 1) {
    const receipt = await verifyPreparedEvidence({
      repoRoot: root,
      prepared,
      snapshot: {
        snapshotHash: prepared.snapshotHash,
        headSha: parents[0] || null,
        stagedTreeSha: treeSha,
        branch,
        stagedFiles: commitFiles,
      },
    });
    if (receipt.valid) matchingReceipt = receipt.prepared;
  }

  if (matchingReceipt) {
    const ledgerEntry = await recordCommitEvidence({
      repoRoot: root,
      commitSha,
      verificationStatus: "passed",
      snapshotHash: matchingReceipt.snapshotHash,
      runKey: matchingReceipt.runKey,
      recordPath: matchingReceipt.recordPath,
      recordDigest: matchingReceipt.recordDigest,
      branch,
      parentSha: matchingReceipt.parentHeadSha,
      treeSha,
      stagedFiles: matchingReceipt.stagedFiles || commitFiles,
      gateId: matchingReceipt.gateId,
      policyHash: matchingReceipt.policyHash,
      intent: matchingReceipt.intent,
      usId: matchingReceipt.usId || inferredUsId,
      featureFile: matchingReceipt.featureFile,
      scenarioName: matchingReceipt.scenarioName,
      scopeFiles: matchingReceipt.scopeFiles,
      repairsSha: matchingReceipt.repairsSha || null,
      supersedes: matchingReceipt.supersedes || [],
      repairStatus: matchingReceipt.repairStatus || null,
      repairedFailure: matchingReceipt.repairedFailure || null,
    });
    await consumePreparedEvidence({ repoRoot: root, commitSha });
    await consumeDeliveryContext({ repoRoot: root });

    return {
      recorded: true,
      commitSha,
      ledgerEntry,
      verificationStatus: "passed",
    };
  }

  // Human commits remain unverified, but an exact repair context preserves the
  // failed SHA so pre-push can validate lineage and authorize one CI attempt.
  const notRunReason = manualRepairContext
    ? "MANUAL_REPAIR_WITHOUT_RECEIPT"
    : !prepared
    ? "NO_PREPARED_RECEIPT"
    : prepared.consumedByCommitSha
    ? "STALE_PREPARED_RECEIPT"
    : "PREPARED_EVIDENCE_MISMATCH";

  const ledgerEntry = await recordCommitEvidence({
    repoRoot: root,
    commitSha,
    verificationStatus: "not_run",
    notRunReason,
    branch,
    parentSha: parents[0] || null,
    treeSha,
    stagedFiles: commitFiles,
    intent: manualRepairContext?.intent || "prepare_commit",
    usId: manualRepairContext?.usId || inferredUsId,
    repairsSha: manualRepairContext?.repairsSha || null,
    manualRepairContextValidated: Boolean(manualRepairContext),
  });

  if (manualRepairContext) {
    await consumeDeliveryContext({ repoRoot: root, context: manualRepairContext });
  }

  return {
    recorded: true,
    commitSha,
    ledgerEntry,
    verificationStatus: "not_run",
    reason: notRunReason,
  };
}

export async function runPrePushHook({ repoRoot, stdinLines = [], ciProvider = null } = {}) {
  const deprecatedBypass = rejectDeprecatedCiBypass();
  if (deprecatedBypass) return deprecatedBypass;

  const root = findRepoRoot(repoRoot);
  const requireEvidence = process.env.DELIVERY_REQUIRE_EVIDENCE === "1";
  let policy = null;
  let maxInFlightCommits = null;

  try {
    policy = await loadDeliveryPolicy({ repoRoot: root });
    maxInFlightCommits = policy.ci.maxInFlightCommits;
  } catch (error) {
    return {
      passed: false,
      reason: "INVALID_DELIVERY_POLICY",
      message: `Pre-push blocked: cannot load CI policy (${error.message}).`,
    };
  }

  try {
    await listCommitEvidence({ repoRoot: root });
  } catch (error) {
    if (
      error?.code === "LEDGER_CORRUPT" ||
      error?.code === "LEDGER_INCONSISTENT" ||
      error?.message?.includes("LEDGER_CORRUPT")
    ) {
      return {
        passed: false,
        reason: "LEDGER_CORRUPT",
        message: "Pre-push blocked: delivery ledger is corrupt and cannot be safely recovered.",
      };
    }
    throw error;
  }

  for (const line of stdinLines) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const parts = trimmed.split(/\s+/);
    if (parts.length < 4) continue;

    const [, localSha, , remoteSha] = parts;

    // Branch deletion (localSha is zeroes)
    if (/^0+$/.test(localSha)) continue;

    let revRange = "";
    if (/^0+$/.test(remoteSha)) {
      // Remote does not exist yet; find commits not in remote branches
      revRange = `${localSha} --not --remotes`;
    } else {
      revRange = `${remoteSha}..${localSha}`;
    }

    let commits = [];
    try {
      const args = ["rev-list", ...revRange.split(" ").filter(Boolean)];
      const out = execFileSync("git", args, { cwd: root, encoding: "utf8" });
      commits = out.trim().split("\n").filter(Boolean);
    } catch {
      commits = [localSha];
    }

    // Policy: One commit, one push
    if (commits.length > 1) {
      return {
        passed: false,
        reason: "MULTIPLE_COMMITS_PUSH",
        message: `Policy enforces 'one commit, one push'. Multiple commits detected for push (${commits.length} commits): ${commits.join(", ")}`,
      };
    }

    let localEvidence = null;
    for (const sha of commits) {
      const evidence = await queryCommitEvidence({ repoRoot: root, commitSha: sha });
      localEvidence = evidence;

      if (evidence.state === "corrupt") {
        return {
          passed: false,
          reason: "INVALID_COMMIT_EVIDENCE",
          evidenceReason: evidence.reason,
          message: `Commit ${sha.slice(0, 8)} has corrupted or altered delivery evidence (${evidence.reason}).`,
        };
      }

      if (evidence.state === "missing") {
        return {
          passed: false,
          reason: "INVALID_COMMIT_EVIDENCE",
          evidenceReason: evidence.reason,
          message: `Commit ${sha.slice(0, 8)} does not have valid delivery evidence (${evidence.reason}). Run delivery prepare and create a matching commit.`,
        };
      }

      if (evidence.state === "not_run") {
        if (requireEvidence) {
          return {
            passed: false,
            reason: "UNVERIFIED_COMMIT_PUSH_BLOCKED",
            evidenceReason: evidence.reason,
            message: `Push blocked: commit ${sha.slice(0, 8)} was not verified locally and DELIVERY_REQUIRE_EVIDENCE=1.`,
          };
        }
        // In normal mode, not_run commits are permitted.
      }

      const commitMessage = execFileSync("git", ["log", "-1", "--format=%B", sha], {
        cwd: root,
        encoding: "utf8",
      });
      const messageValidation = validateCommitMessage(commitMessage);
      if (!messageValidation.valid) {
        return {
          passed: false,
          reason: "INVALID_PUSHED_COMMIT_MESSAGE",
          message: `Commit ${sha.slice(0, 8)} has an invalid message: ${messageValidation.message}`,
        };
      }
    }

    const currentSet = new Set(commits);
    const localEntry = localEvidence?.entry;
    const isRepair =
      localEntry?.intent === "repair_ci" ||
      localEntry?.gateId === "R" ||
      Boolean(localEntry?.repairsSha);

    const ciEvaluation = await evaluateCiWindow({
      repoRoot: root,
      policy,
      ciProvider,
      intent: isRepair ? "repair_ci" : (localEntry?.intent || "prepare_commit"),
      repairsSha: localEntry?.repairsSha,
      targetSha: localEntry?.repairsSha,
      excludeShas: currentSet,
      commitCount: commits.length,
      historyHeadSha: localSha,
    });

    if (!ciEvaluation.allowed) {
      if (ciEvaluation.reason === "LEDGER_CORRUPT" || ciEvaluation.code === "LEDGER_CORRUPT") {
        return {
          passed: false,
          reason: "LEDGER_CORRUPT",
          message: "Pre-push blocked: delivery ledger is corrupt and cannot be safely recovered.",
        };
      }
      if (ciEvaluation.reason === "CI_PROVIDER_ERROR" || ciEvaluation.code === "CI_PROVIDER_ERROR") {
        return {
          passed: false,
          reason: "CI_PROVIDER_ERROR",
          message: "Pre-push blocked: CI provider returned an error. Cannot determine remote CI safely.",
        };
      }
      if (ciEvaluation.reason === "CI_INSPECTION_FAILED" || ciEvaluation.code === "CI_INSPECTION_FAILED") {
        return {
          passed: false,
          reason: "CI_INSPECTION_FAILED",
          message: "Pre-push blocked: could not inspect remote CI.",
        };
      }
      if (ciEvaluation.reason === "PRIOR_COMMIT_CI_FAILED" || ciEvaluation.code === "REPAIR_REQUIRED") {
        const failedSha = ciEvaluation.failedSha || ciEvaluation.sha;
        return {
          passed: false,
          reason: "PRIOR_COMMIT_CI_FAILED",
          code: "REPAIR_REQUIRED",
          message: `Pre-push blocked: prior commit ${failedSha.slice(0, 8)} failed CI in GitHub Actions. Fix the failure before pushing new commits.`,
          sha: failedSha,
          activeIncident: ciEvaluation.activeIncident,
        };
      }
      if (ciEvaluation.reason === "CI_PENDING_WINDOW_EXCEEDED" || ciEvaluation.code === "CI_WINDOW_FULL") {
        return {
          passed: false,
          reason: "CI_PENDING_WINDOW_EXCEEDED",
          code: "CI_WINDOW_FULL",
          message: `Pre-push blocked: this push would create ${ciEvaluation.inFlightCount} commits in flight (maximum ${ciEvaluation.maxInFlightCommits}). Wait for CI to complete.`,
          pendingCount: ciEvaluation.pendingCount,
          inFlightCount: ciEvaluation.inFlightCount,
          maxInFlightCommits: ciEvaluation.maxInFlightCommits,
        };
      }
      return {
        passed: false,
        reason: ciEvaluation.reason || "CI_EVALUATION_BLOCKED",
        message: ciEvaluation.message,
        sha: ciEvaluation.failedSha || localSha,
      };
    }

    const supersededSet = ciEvaluation.supersededSet || new Set();

    if (ciEvaluation.matchingIncident) {
      // Commit being pushed is a valid repair candidate for an active incident
      const targetSha = ciEvaluation.matchingIncident.failedSha;

      let releaseRepairLock = null;
      try {
        releaseRepairLock = await acquireRepairLock({ repoRoot: root, targetSha });
      } catch (lockError) {
        return {
          passed: false,
          reason: "CONCURRENT_PUSH_IN_PROGRESS",
          message: `Pre-push blocked: concurrent push in progress for repair of commit ${targetSha.slice(0, 8)}.`,
          sha: localSha,
        };
      }

      try {
        let targetValidation;
        try {
          targetValidation = await validateRepairLineage({
            repoRoot: root,
            repairSha: localSha,
            targetSha,
            ciProvider,
            supersededSet,
          });
        } catch (error) {
          if (
            error?.code === "LEDGER_CORRUPT" ||
            error?.code === "LEDGER_INCONSISTENT" ||
            error?.message?.includes("LEDGER_CORRUPT")
          ) {
            return {
              passed: false,
              reason: "LEDGER_CORRUPT",
              message: "Pre-push blocked: delivery ledger is corrupt and cannot be safely recovered.",
            };
          }
          throw error;
        }
        if (!targetValidation.valid) {
          return {
            passed: false,
            reason: targetValidation.reason,
            message: targetValidation.message,
            sha: localSha,
          };
        }

        const authResult = await authorizeRepairPush({
          repoRoot: root,
          targetSha,
          commitSha: localSha,
          ciProvider,
          lockHeld: true,
        });

        if (!authResult.authorized) {
          return {
            passed: false,
            reason: authResult.reason || "REPAIR_RECEIPT_ALREADY_CONSUMED",
            message:
              authResult.message ||
              `Pre-push blocked: repair authorization for commit ${targetSha.slice(0, 8)} has already been consumed for a push.`,
            sha: localSha,
          };
        }

        localEntry.repairAuthState = authResult.state;
        localEntry.repairPushConsumed = true;
      } finally {
        if (releaseRepairLock) {
          await releaseRepairLock();
        }
      }

      // Repair push authorized, proceed (does not count against ordinary pending window)
      continue;
    }

    // If local commit claims to be a repair but there were no active incidents:
    if (isRepair) {
      const repairTarget = localEntry?.repairsSha ? String(localEntry.repairsSha) : null;
      let releaseRepairLock = null;
      if (repairTarget && !localEntry?.repairPushConsumed) {
        try {
          releaseRepairLock = await acquireRepairLock({ repoRoot: root, targetSha: repairTarget });
        } catch (lockError) {
          return {
            passed: false,
            reason: "CONCURRENT_PUSH_IN_PROGRESS",
            message: `Pre-push blocked: concurrent push in progress for repair of commit ${repairTarget.slice(0, 8)}.`,
            sha: localSha,
          };
        }
      }

      try {
        const selfValidation = await validateRepairLineage({
          repoRoot: root,
          repairSha: localSha,
          ciProvider,
          supersededSet,
        });
        if (!selfValidation.valid) {
          return {
            passed: false,
            reason: selfValidation.reason,
            message: selfValidation.message,
            sha: localSha,
          };
        }

        if (repairTarget && !localEntry.repairPushConsumed) {
          const authResult = await authorizeRepairPush({
            repoRoot: root,
            targetSha: repairTarget,
            commitSha: localSha,
            ciProvider,
            lockHeld: true,
          });

          if (!authResult.authorized) {
            return {
              passed: false,
              reason: authResult.reason || "REPAIR_RECEIPT_ALREADY_CONSUMED",
              message:
                authResult.message ||
                `Pre-push blocked: repair authorization for commit ${repairTarget.slice(0, 8)} has already been consumed for a push.`,
              sha: localSha,
            };
          }

          localEntry.repairAuthState = authResult.state;
          localEntry.repairPushConsumed = true;
        }
      } finally {
        if (releaseRepairLock) {
          await releaseRepairLock();
        }
      }
    }
  }

  return { passed: true };
}

export async function installHooks({ repoRoot } = {}) {
  const root = findRepoRoot(repoRoot);
  execFileSync("git", ["config", "core.hooksPath", ".githooks"], { cwd: root });

  const hooksDir = path.resolve(root, ".githooks");
  try {
    const entries = await fs.readdir(hooksDir);
    for (const entry of entries) {
      const hookPath = path.join(hooksDir, entry);
      await fs.chmod(hookPath, 0o755);
    }
  } catch {
    // ignore
  }

  return { installed: true, hooksPath: ".githooks" };
}

export async function getHooksStatus({ repoRoot } = {}) {
  const root = findRepoRoot(repoRoot);
  let configuredPath = "";
  try {
    configuredPath = execFileSync("git", ["config", "core.hooksPath"], {
      cwd: root,
      encoding: "utf8",
    }).trim();
  } catch {
    configuredPath = "";
  }

  const hooks = ["pre-commit", "commit-msg", "post-commit", "pre-push"];
  const hookStatuses = {};
  for (const hook of hooks) {
    const absPath = path.resolve(root, ".githooks", hook);
    try {
      const stat = await fs.stat(absPath);
      const isExecutable = (stat.mode & 0o111) !== 0;
      hookStatuses[hook] = { exists: true, executable: isExecutable };
    } catch {
      hookStatuses[hook] = { exists: false, executable: false };
    }
  }

  return {
    configured: configuredPath === ".githooks",
    configuredPath,
    hooks: hookStatuses,
  };
}
