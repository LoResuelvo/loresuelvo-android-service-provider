import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { findRepoRoot, assertSafeRepoPath } from "./repo-root.mjs";
import { inspectCi } from "./ci-provider.mjs";
import {
  acquireRepairLock,
  getActiveCiIncidents,
  getRepairAuthorization,
  matchesTarget,
} from "./delivery-ledger.mjs";

const RECOVERY_DIR = ".delivery/runtime/repair-recovery";
const AUTH_DIR = ".delivery/runtime/repair-auth";
const ACTIVE_INCIDENT_STATES = new Set(["repair_required", "repair_failed", "repair_prepared", "repair_submitted"]);
const SHA_PATTERN = /^[a-f0-9]{7,40}$/i;

function blocked(reason, message, details = {}) {
  return { recovered: false, status: "blocked", reason, message, ...details };
}

function resolveCommit(root, sha) {
  try {
    return execFileSync("git", ["rev-parse", `${sha}^{commit}`], {
      cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"],
    }).trim().toLowerCase();
  } catch {
    return null;
  }
}

async function writeAtomic(root, relative, value) {
  assertSafeRepoPath(root, relative, "Repair recovery path");
  const target = path.resolve(root, relative);
  const temp = `${target}.${process.pid}.${crypto.randomBytes(4).toString("hex")}.tmp`;
  await fs.mkdir(path.dirname(target), { recursive: true, mode: 0o700 });
  await fs.writeFile(temp, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
  await fs.rename(temp, target);
}

async function readRecoveryEvents(root, targetSha) {
  let files;
  try {
    files = await fs.readdir(path.resolve(root, RECOVERY_DIR));
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw new Error("REPAIR_RECOVERY_AUDIT_UNREADABLE");
  }
  const events = [];
  for (const file of files.filter((name) => name.endsWith(".json") && !name.endsWith(".tmp"))) {
    let event;
    try {
      event = JSON.parse(await fs.readFile(path.join(root, RECOVERY_DIR, file), "utf8"));
    } catch {
      throw new Error("REPAIR_RECOVERY_AUDIT_UNREADABLE");
    }
    if (event?.schemaVersion !== 1 || event?.kind !== "repair_authorization_recovery" ||
        !event.previousAuthorization || !event.recoveryId || !event.recordedAt ||
        !Number.isFinite(Date.parse(event.recordedAt)) ||
        !SHA_PATTERN.test(String(event.targetSha || "")) ||
        !SHA_PATTERN.test(String(event.expectedAuthorizationCommitSha || ""))) {
      throw new Error("REPAIR_RECOVERY_AUDIT_UNREADABLE");
    }
    if (matchesTarget(event.targetSha, targetSha)) events.push(event);
  }
  return events;
}

export async function isRepairAuthorizationRecovered({ repoRoot, targetSha, commitSha } = {}) {
  if (!targetSha || !commitSha) return false;
  const root = findRepoRoot(repoRoot);
  const events = await readRecoveryEvents(root, targetSha);
  return events.some((event) => matchesTarget(event.expectedAuthorizationCommitSha, commitSha));
}

export async function hasUnrecoveredRepair({ repoRoot, ledgerEntries, targetSha } = {}) {
  const root = findRepoRoot(repoRoot);
  const events = await readRecoveryEvents(root, targetSha);
  for (const entry of ledgerEntries || []) {
    if (!entry.repairsSha || !matchesTarget(entry.repairsSha, targetSha)) continue;
    const entryAt = Date.parse(entry.recordedAt || "");
    if (Number.isFinite(entryAt) && events.some((event) => entryAt <= Date.parse(event.recordedAt))) continue;
    return true;
  }
  return false;
}

async function resetAuthorization(root, targetSha, authorization) {
  await writeAtomic(root, path.join(AUTH_DIR, `${targetSha}.json`), {
    ...authorization,
    targetSha,
    commitSha: null,
    state: "prepared",
    updatedAt: new Date().toISOString(),
  });
}

async function assertAuthorizationReadable(root, targetSha) {
  try {
    const parsed = JSON.parse(await fs.readFile(path.resolve(root, AUTH_DIR, `${targetSha}.json`), "utf8"));
    if (!parsed || typeof parsed !== "object" ||
        (parsed.targetSha && !SHA_PATTERN.test(String(parsed.targetSha))) ||
        (parsed.commitSha && !SHA_PATTERN.test(String(parsed.commitSha)))) {
      throw new Error("REPAIR_AUTHORIZATION_UNREADABLE");
    }
  } catch (error) {
    if (error.code !== "ENOENT") throw new Error("REPAIR_AUTHORIZATION_UNREADABLE");
  }
}

async function inspectCiSafely({ repoRoot, sha, provider }) {
  try {
    const result = await inspectCi({ repoRoot, sha, provider });
    return result?.status === "provider_error" ? null : result;
  } catch {
    return null;
  }
}

function remoteOriginMainTip(root) {
  const output = execFileSync("git", ["ls-remote", "origin", "refs/heads/main"], {
    cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"],
  });
  const remoteSha = output.split(/\r?\n/).map((value) => value.trim().split(/\s+/)).find((parts) => parts[1] === "refs/heads/main")?.[0];
  if (!/^[a-f0-9]{40}$/i.test(remoteSha || "")) throw new Error("REMOTE_STATE_UNKNOWN");
  const resolved = resolveCommit(root, remoteSha);
  if (!resolved) throw new Error("REMOTE_STATE_UNKNOWN");
  return resolved;
}

function remoteContains(root, ancestor, tip) {
  try {
    execFileSync("git", ["merge-base", "--is-ancestor", ancestor, tip], {
      cwd: root, stdio: ["ignore", "ignore", "ignore"],
    });
    return true;
  } catch (error) {
    if (error.status === 1) return false;
    throw error;
  }
}

/** Recover one stale authorization after proving it never reached remote CI/Git. */
export async function recoverStaleRepairAuthorization({
  repoRoot,
  targetSha,
  expectedAuthorizationCommitSha,
  ciProvider = null,
  lockTimeoutMs = 100,
} = {}) {
  let root;
  try {
    root = findRepoRoot(repoRoot);
  } catch {
    return blocked("GIT_STATE_UNKNOWN", "Repository state could not be resolved.");
  }
  if (!SHA_PATTERN.test(String(targetSha || "")) || !SHA_PATTERN.test(String(expectedAuthorizationCommitSha || ""))) {
    return blocked("INVALID_ARGUMENTS", "Recovery requires targetSha and expectedAuthorizationCommitSha.");
  }
  const target = resolveCommit(root, String(targetSha).trim());
  const expected = resolveCommit(root, String(expectedAuthorizationCommitSha).trim());
  if (!target || !expected || target === expected) {
    return blocked("GIT_STATE_UNKNOWN", "Target or authorization commit could not be resolved.");
  }

  let release;
  try {
    release = await acquireRepairLock({ repoRoot: root, targetSha: target, timeoutMs: lockTimeoutMs });
  } catch (error) {
    return blocked(error.code === "REPAIR_LOCK_TIMEOUT" ? "REPAIR_PUSH_ACTIVE" : "REPAIR_LOCK_UNAVAILABLE",
      "Recovery refused because a repair push is active or the lock is ambiguous.");
  }
  try {
    let events;
    try {
      events = await readRecoveryEvents(root, target);
    } catch {
      return blocked("REPAIR_RECOVERY_AUDIT_UNREADABLE", "Recovery audit state is ambiguous.");
    }
    const existing = events.find((event) => matchesTarget(event.expectedAuthorizationCommitSha, expected));
    if (existing) {
      return { recovered: true, status: "already_recovered", idempotent: true, targetSha: target,
        expectedAuthorizationCommitSha: expected, recoveryId: existing.recoveryId, recordedAt: existing.recordedAt };
    }

    let authorization;
    try {
      await assertAuthorizationReadable(root, target);
      authorization = await getRepairAuthorization({ repoRoot: root, targetSha: target });
    } catch {
      return blocked("REPAIR_AUTHORIZATION_UNREADABLE", "Repair authorization state is ambiguous.");
    }
    if (!authorization?.commitSha) return blocked("REPAIR_AUTHORIZATION_NOT_FOUND", "No bound repair authorization was found.");
    if (!matchesTarget(authorization.commitSha, expected)) {
      return blocked("REPAIR_AUTHORIZATION_COMMIT_MISMATCH", "The expected authorization commit does not match the ledger.");
    }

    let remote;
    try {
      remote = remoteContains(root, expected, remoteOriginMainTip(root));
    } catch {
      return blocked("REMOTE_STATE_UNKNOWN", "Git could not determine remote membership.");
    }
    if (remote) return blocked("AUTHORIZATION_COMMIT_ALREADY_REMOTE", "The authorization commit is already remote.");

    const authCi = await inspectCiSafely({ repoRoot: root, sha: expected, provider: ciProvider });
    if (!authCi) return blocked("CI_PROVIDER_ERROR", "CI status for the authorization commit is ambiguous.");
    if (authCi.status !== "not_found") return blocked("AUTHORIZATION_COMMIT_CI_RECOGNIZED", "CI recognizes the authorization commit.", { ciStatus: authCi.status });

    const targetCi = await inspectCiSafely({ repoRoot: root, sha: target, provider: ciProvider });
    if (!targetCi) return blocked("CI_PROVIDER_ERROR", "CI status for the failed target is ambiguous.");
    if (!["failed", "cancelled", "timed_out"].includes(targetCi.status)) {
      return blocked("TARGET_INCIDENT_NOT_ACTIVE", "The failed target is no longer in a failed CI state.", { ciStatus: targetCi.status });
    }

    let incidents;
    try {
      incidents = await getActiveCiIncidents({ repoRoot: root, ciProvider });
    } catch {
      return blocked("CI_INCIDENT_STATE_UNKNOWN", "Active CI incident state is ambiguous.");
    }
    const active = (incidents.activeCiIncidents || incidents).find((incident) => matchesTarget(incident.failedSha, target));
    if (!active || !ACTIVE_INCIDENT_STATES.has(active.status)) {
      return blocked("TARGET_INCIDENT_NOT_ACTIVE", "The failed target is not the active repair incident.");
    }

    const recordedAt = new Date().toISOString();
    const recoveryId = `recovery-${Date.now()}-${process.pid}-${crypto.randomBytes(4).toString("hex")}`;
    const event = {
      schemaVersion: 1,
      kind: "repair_authorization_recovery",
      recoveryId,
      recordedAt,
      targetSha: target,
      expectedAuthorizationCommitSha: expected,
      reason: "STALE_NEVER_REMOTE",
      previousAuthorization: authorization,
    };
    await writeAtomic(root, path.join(RECOVERY_DIR, `${recoveryId}.json`), event);
    await resetAuthorization(root, target, authorization);
    return { recovered: true, status: "recovered", idempotent: false, targetSha: target,
      expectedAuthorizationCommitSha: expected, recoveryId, recordedAt };
  } finally {
    await release();
  }
}
