import fs from "node:fs/promises";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { findRepoRoot } from "./repo-root.mjs";
import { consumeDeliveryContext } from "./delivery-context.mjs";
import { captureGitSnapshot } from "./git-snapshot.mjs";
import {
  consumePreparedEvidence,
  getLastPreparedEvidence,
  queryCommitEvidence,
  recordCommitEvidence,
  verifyPreparedEvidence,
} from "./delivery-ledger.mjs";


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

/**
 * Validates commit message structure according to Lo Resuelvo commit governance.
 * - Allowed types follow repository commit governance, including feat for product work
 * - Rejects scopes in parentheses like '(agent)' or '(scope)'
 */
export function validateCommitMessage(rawMessage) {
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

  return { valid: true, type, usId: usId || null, description };
}

export async function runPreCommitHook({ repoRoot } = {}) {
  const root = findRepoRoot(repoRoot);

  let snapshot;
  try {
    snapshot = await captureGitSnapshot({ cwd: root });
  } catch (error) {
    return {
      passed: true,
      advisory: true,
      verified: false,
      reason: "GIT_ERROR",
      warning: `Delivery pre-commit check unavailable: ${error.message}`,
    };
  }

  let receipt;
  try {
    receipt = await verifyPreparedEvidence({ repoRoot: root, snapshot });
  } catch (error) {
    return {
      passed: true,
      advisory: true,
      verified: false,
      reason: "DELIVERY_STATE_UNAVAILABLE",
      warning: `Delivery pre-commit check unavailable: ${error.message}`,
    };
  }
  const verifiedReason = receipt.reason;

  if (receipt.valid) {
    return {
      passed: true,
      advisory: true,
      verified: true,
      gateId: receipt.prepared.gateId || "NONE",
      prepared: receipt.prepared,
    };
  }

  return {
    passed: true,
    advisory: true,
    verified: false,
    reason: verifiedReason,
    warning: `Proceeding without verified delivery evidence (not_run). Use delivery_prepare to verify gates locally.`,
  };
}

export async function runCommitMsgHook({ repoRoot, messageFilePath } = {}) {
  const root = findRepoRoot(repoRoot);
  if (!messageFilePath) {
    throw new Error("Missing commit message file path parameter");
  }

  const absPath = path.isAbsolute(messageFilePath)
    ? messageFilePath
    : path.resolve(root, messageFilePath);
  const content = await fs.readFile(absPath, "utf8");
  const validation = validateCommitMessage(content);
  if (!validation.valid) {
    return {
      passed: false,
      reason: validation.reason,
      message: validation.message,
    };
  }

  return { passed: true, validation };
}

function postCommitAdvisory(reason, warning, commitSha = null) {
  return {
    recorded: false,
    advisory: true,
    reason,
    warning,
    ...(commitSha ? { commitSha } : {}),
  };
}

function readCommittedSnapshot(root, commitSha) {
  const parents = execFileSync("git", ["rev-list", "--parents", "-n", "1", commitSha], {
    cwd: root, encoding: "utf8",
  }).trim().split(/\s+/).slice(1);
  const branch = execFileSync("git", ["rev-parse", "--abbrev-ref", "HEAD"], {
    cwd: root, encoding: "utf8",
  }).trim();
  const treeSha = execFileSync("git", ["rev-parse", `${commitSha}^{tree}`], {
    cwd: root, encoding: "utf8",
  }).trim();
  const rawFiles = execFileSync(
    "git",
    ["diff-tree", "--root", "--no-commit-id", "--name-only", "-r", "-z", commitSha],
    { cwd: root, encoding: "buffer" },
  );
  return {
    parentSha: parents[0] || null,
    branch,
    treeSha,
    stagedFiles: rawFiles.toString("utf8").split("\0").filter(Boolean).sort(),
  };
}

export async function runPostCommitHook({ repoRoot } = {}) {
  let root;
  let commitSha;
  try {
    root = findRepoRoot(repoRoot);
    commitSha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  } catch (error) {
    return postCommitAdvisory("GIT_ERROR", `Delivery post-commit check unavailable: ${error.message}`);
  }

  let existing;
  try {
    existing = await queryCommitEvidence({ repoRoot: root, commitSha });
  } catch (error) {
    return postCommitAdvisory("DELIVERY_STATE_UNAVAILABLE", `Delivery evidence could not be read: ${error.message}`, commitSha);
  }
  if (existing.valid) {
    return {
      recorded: true,
      advisory: true,
      commitSha,
      ledgerEntry: existing.entry,
      reused: true,
      verificationStatus: "passed",
    };
  }
  if (existing.state === "not_run") {
    return postCommitAdvisory(existing.reason || "EXISTING_NOT_RUN_EVIDENCE", "Existing unverified evidence was left unchanged.", commitSha);
  }
  if (existing.state === "corrupt") {
    return postCommitAdvisory("CORRUPT_COMMIT_EVIDENCE", "Delivery evidence is corrupt; the created commit remains accepted.", commitSha);
  }

  let prepared;
  try {
    prepared = await getLastPreparedEvidence({ repoRoot: root });
  } catch (error) {
    return postCommitAdvisory("DELIVERY_STATE_UNAVAILABLE", `Prepared delivery evidence could not be read: ${error.message}`, commitSha);
  }
  if (!prepared) {
    return postCommitAdvisory("MISSING_PREPARED_EVIDENCE", "No prepared delivery receipt matched this commit.", commitSha);
  }

  let committed;
  try {
    committed = readCommittedSnapshot(root, commitSha);
  } catch (error) {
    return postCommitAdvisory("GIT_ERROR", `Committed snapshot could not be read: ${error.message}`, commitSha);
  }

  let receipt;
  try {
    receipt = await verifyPreparedEvidence({
      repoRoot: root,
      prepared,
      snapshot: {
        snapshotHash: prepared.snapshotHash,
        headSha: committed.parentSha,
        stagedTreeSha: committed.treeSha,
        branch: committed.branch,
        stagedFiles: committed.stagedFiles,
      },
    });
  } catch (error) {
    return postCommitAdvisory("DELIVERY_STATE_UNAVAILABLE", `Prepared delivery evidence could not be verified: ${error.message}`, commitSha);
  }
  if (!receipt.valid) {
    return postCommitAdvisory(receipt.reason || "PREPARED_EVIDENCE_MISMATCH", "Prepared delivery evidence did not match the created commit.", commitSha);
  }

  let ledgerEntry;
  try {
    ledgerEntry = await recordCommitEvidence({
      repoRoot: root,
      commitSha,
      verificationStatus: "passed",
      snapshotHash: prepared.snapshotHash,
      runKey: prepared.runKey,
      recordPath: prepared.recordPath,
      recordDigest: prepared.recordDigest,
      branch: committed.branch,
      parentSha: committed.parentSha,
      treeSha: committed.treeSha,
      stagedFiles: committed.stagedFiles,
      gateId: prepared.gateId,
      policyHash: prepared.policyHash,
      intent: prepared.intent,
      usId: prepared.usId,
      featureFile: prepared.featureFile,
      scenarioName: prepared.scenarioName,
      scopeFiles: prepared.scopeFiles,
      repairsSha: prepared.repairsSha,
      supersedes: prepared.supersedes,
      repairStatus: prepared.repairStatus,
      repairedFailure: prepared.repairedFailure,
    });
  } catch (error) {
    return postCommitAdvisory("DELIVERY_EVIDENCE_RECORD_FAILED", `Delivery evidence could not be recorded: ${error.message}`, commitSha);
  }

  try { await consumePreparedEvidence({ repoRoot: root, commitSha }); } catch {}
  try { await consumeDeliveryContext({ repoRoot: root }); } catch {}
  return { recorded: true, advisory: true, commitSha, ledgerEntry, verificationStatus: "passed" };
}

export async function runPrePushHook({ repoRoot } = {}) {
  return {
    passed: true,
    advisory: true,
    reason: "DELIVERY_RUNTIME_ADVISORY",
    warning: "Delivery pre-push enforcement is advisory; run delivery_prepare/inspect explicitly for evidence and CI state.",
  };
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
