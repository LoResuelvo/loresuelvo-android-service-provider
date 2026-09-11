import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { findRepoRoot, assertSafeRepoPath } from "./repo-root.mjs";
import { redactSecrets } from "./redact-secrets.mjs";

const JOB_ID_REGEX = /^[A-Za-z0-9_-]+$/;
const JOBS_DIR = ".delivery/runtime/jobs";
const TERMINAL_JOB_STATUSES = new Set(["passed", "failed", "timed_out", "cancelled"]);
// A job is written before its worker is spawned.  If the MCP process dies in
// that small window, the queued record must eventually stop being considered
// active so the next invocation can recover instead of being deduplicated
// forever.
export const DEFAULT_JOB_QUEUE_LEASE_MS = 60_000;
const PROCESS_START_SKEW_MS = 5_000;

export function getJobsDir(repoRoot) {
  const root = findRepoRoot(repoRoot);
  return path.resolve(root, JOBS_DIR);
}

export function validateJobId(jobId) {
  if (!jobId || typeof jobId !== "string" || !JOB_ID_REGEX.test(jobId)) {
    throw new Error(`Invalid job ID: ${String(jobId || "").slice(0, 50)}`);
  }
}

export function getJobFilePath(repoRoot, jobId) {
  const root = findRepoRoot(repoRoot);
  validateJobId(jobId);
  const relative = path.posix.join(JOBS_DIR, `${jobId}.json`);
  assertSafeRepoPath(root, relative, "Job path");
  return path.resolve(root, relative);
}

async function writeJobAtomic(root, jobId, jobData) {
  const filePath = getJobFilePath(root, jobId);
  await fs.mkdir(path.dirname(filePath), { recursive: true, mode: 0o700 });
  const tempPath = `${filePath}.${process.pid}.${Date.now()}.${crypto.randomBytes(2).toString("hex")}.tmp`;
  await fs.writeFile(tempPath, `${JSON.stringify(jobData, null, 2)}\n`, { mode: 0o600 });
  await fs.rename(tempPath, filePath);
}

export function isProcessAlive(pid) {
  if (!pid || typeof pid !== "number") return false;
  try {
    process.kill(pid, 0);
    return true;
  } catch (err) {
    return err.code === "EPERM";
  }
}

/**
 * Return the kernel identity for a process when procfs is available.  A PID
 * alone is not an identity: after a worker exits the same PID can belong to an
 * unrelated process.  The Linux start-time ticks are stable for the lifetime
 * of a process and are therefore persisted with the job.
 */
export async function readProcessIdentity(pid) {
  if (!Number.isInteger(pid) || pid <= 0 || process.platform === "win32") return null;
  try {
    const raw = await fs.readFile(`/proc/${pid}/stat`, "utf8");
    const closeParen = raw.lastIndexOf(")");
    if (closeParen < 0) return null;
    const fields = raw.slice(closeParen + 2).trim().split(/\s+/);
    // After the command name, fields[0] is stat field 3.  Start time is
    // stat field 22, hence index 19 here.
    const startTimeTicks = fields[19];
    if (!startTimeTicks || !/^\d+$/.test(startTimeTicks)) return null;
    let command = null;
    try {
      command = (await fs.readFile(`/proc/${pid}/cmdline`)).toString("utf8").replaceAll("\0", " ").trim() || null;
    } catch {
      // The command line may be unreadable even while stat is available.
    }
    return { pid, startTimeTicks, ...(command ? { command } : {}) };
  } catch {
    return null;
  }
}

function sameProcessIdentity(left, right) {
  return Boolean(
    left &&
      right &&
      String(left.startTimeTicks || "") === String(right.startTimeTicks || "")
  );
}

function sameFileIdentity(left, right) {
  return Boolean(left && right && left.dev === right.dev && left.ino === right.ino);
}

async function readProcessToken(pid) {
  if (!Number.isInteger(pid) || pid <= 0 || process.platform === "win32") return null;
  try {
    const env = (await fs.readFile(`/proc/${pid}/environ`)).toString("utf8").split("\0");
    const value = env.find((item) => item.startsWith("DELIVERY_JOB_TOKEN="));
    return value ? value.slice("DELIVERY_JOB_TOKEN=".length) : null;
  } catch {
    return null;
  }
}

async function processMatchesJob(job) {
  if (!job || !Number.isInteger(job.pid) || !isProcessAlive(job.pid)) {
    return { alive: false, verified: false };
  }

  const currentIdentity = await readProcessIdentity(job.pid);
  if (job.workerIdentity) {
    if (!sameProcessIdentity(currentIdentity, job.workerIdentity)) {
      return { alive: false, verified: false, reason: "WORKER_IDENTITY_MISMATCH" };
    }
  } else if (!currentIdentity) {
    // Legacy records without an identity are kept conservatively for reads,
    // but are never eligible for a destructive signal.
    return { alive: true, verified: false, reason: "WORKER_IDENTITY_UNKNOWN" };
  } else {
    // A legacy job can still be reported as active, but without a persisted
    // identity it is never safe to signal the PID (it may have been reused).
    return { alive: true, verified: false, reason: "WORKER_IDENTITY_UNKNOWN" };
  }

  if (job.workerToken) {
    const token = await readProcessToken(job.pid);
    // procfs environments can be hidden by the host.  The start-time
    // identity remains sufficient in that case; when a token is observable it
    // must match exactly.
    if (token !== null && token !== job.workerToken) {
      return { alive: false, verified: false, reason: "WORKER_TOKEN_MISMATCH" };
    }
  }

  const startedAt = Date.parse(job.startedAt || job.createdAt || "");
  if (!Number.isFinite(startedAt) || startedAt > Date.now() + PROCESS_START_SKEW_MS) {
    return { alive: false, verified: false, reason: "WORKER_START_TIME_INVALID" };
  }
  return { alive: true, verified: Boolean(currentIdentity) };
}

export async function killProcessTree(pid, signal = "SIGTERM", expectedIdentity = null) {
  if (!isProcessAlive(pid)) return false;
  if (expectedIdentity) {
    const current = await readProcessIdentity(pid);
    if (!sameProcessIdentity(current, expectedIdentity)) return false;
  }
  if (process.platform !== "win32") {
    try {
      process.kill(-pid, signal);
      return true;
    } catch {
      // Process group kill failed, try direct kill
    }
  }
  try {
    process.kill(pid, signal);
    return true;
  } catch {
    // already gone
  }
  return false;
}

export async function createDeliveryJob({
  repoRoot,
  type = "prepare",
  params = {},
  runKey = null,
  snapshotHash = null,
  gateId = null,
  workerToken = undefined,
  queueLeaseMs = DEFAULT_JOB_QUEUE_LEASE_MS,
}) {
  const root = findRepoRoot(repoRoot);
  const timestamp = Date.now();
  const suffix = crypto.randomBytes(4).toString("hex");
  const jobId = `job-${timestamp}-${suffix}`;

  const job = {
    jobId,
    type,
    status: "queued",
    createdAt: new Date(timestamp).toISOString(),
    queueLeaseUntil: new Date(timestamp + Math.max(1_000, Number(queueLeaseMs) || DEFAULT_JOB_QUEUE_LEASE_MS)).toISOString(),
    startedAt: null,
    finishedAt: null,
    pid: null,
    workerToken: workerToken !== undefined ? workerToken : crypto.randomBytes(16).toString("hex"),
    workerIdentity: null,
    runKey,
    snapshotHash,
    gateId,
    params,
    result: null,
    error: null,
  };

  await writeJobAtomic(root, jobId, job);
  return job;
}

export async function getDeliveryJob({ repoRoot, jobId }) {
  const root = findRepoRoot(repoRoot);
  const filePath = getJobFilePath(root, jobId);
  try {
    const content = await fs.readFile(filePath, "utf8");
    return JSON.parse(content);
  } catch (error) {
    if (error.code === "ENOENT") return null;
    if (error instanceof SyntaxError) {
      // Retry once in case of brief read-during-rename collision
      await new Promise((r) => setTimeout(r, 50));
      try {
        const retryContent = await fs.readFile(filePath, "utf8");
        return JSON.parse(retryContent);
      } catch {
        return null;
      }
    }
    throw error;
  }
}

export async function updateDeliveryJob({ repoRoot, jobId, updates }) {
  const root = findRepoRoot(repoRoot);
  const current = await getDeliveryJob({ repoRoot: root, jobId });
  if (!current) throw new Error(`Job not found: ${jobId}`);
  const effectiveUpdates = { ...updates };
  // Never let the parent-side spawn bookkeeping (status: running) or a late
  // signal handler overwrite a terminal result that the worker already
  // persisted.  This also closes the fast-worker race where a tiny check can
  // finish before spawnJobWorker records the child PID.
  if (
    TERMINAL_JOB_STATUSES.has(current.status) &&
    Object.prototype.hasOwnProperty.call(effectiveUpdates, "status") &&
    effectiveUpdates.status !== current.status
  ) {
    return current;
  }
  if (
    Object.prototype.hasOwnProperty.call(effectiveUpdates, "pid") &&
    Number.isInteger(effectiveUpdates.pid) &&
    effectiveUpdates.pid > 0
  ) {
    if (!Object.prototype.hasOwnProperty.call(effectiveUpdates, "workerIdentity")) {
      const identity = await readProcessIdentity(effectiveUpdates.pid);
      if (identity) effectiveUpdates.workerIdentity = identity;
    }
    if (!Object.prototype.hasOwnProperty.call(effectiveUpdates, "workerToken")) {
      const token = await readProcessToken(effectiveUpdates.pid);
      if (token) effectiveUpdates.workerToken = token;
    }
  }
  const updated = { ...current, ...effectiveUpdates };
  await writeJobAtomic(root, jobId, updated);
  return updated;
}

function jobAgeMs(job, now = Date.now()) {
  const created = Date.parse(job?.createdAt || "");
  if (Number.isFinite(created)) return Math.max(0, now - created);
  return Number.POSITIVE_INFINITY;
}

function queuedLeaseExpired(job, now = Date.now()) {
  const leaseUntil = Date.parse(job?.queueLeaseUntil || "");
  if (Number.isFinite(leaseUntil)) return now >= leaseUntil;
  return jobAgeMs(job, now) >= DEFAULT_JOB_QUEUE_LEASE_MS;
}

async function runLockBelongsToJob(root, job) {
  if (!job?.runKey || !Number.isInteger(job.pid) || job.pid <= 0) return false;
  const lockPath = path.resolve(root, ".delivery/runtime/locks", `${job.runKey}.lock`);
  let raw;
  let lockStat;
  try {
    raw = await fs.readFile(lockPath, "utf8");
    lockStat = await fs.lstat(lockPath);
  } catch (error) {
    return error.code !== "ENOENT" ? false : false;
  }

  let owner;
  try {
    owner = JSON.parse(raw);
  } catch {
    // Legacy delivery-evidence locks were sometimes plain markers.  They can
    // only be reclaimed when the worker identity is still demonstrably this
    // job, or when the marker predates a dead job (never when a newer
    // execution could own it).
    const currentIdentity = await readProcessIdentity(job.pid);
    if (job.workerIdentity && currentIdentity && sameProcessIdentity(currentIdentity, job.workerIdentity)) {
      return true;
    }
    const startedAt = Date.parse(job.startedAt || job.createdAt || "");
    if (
      !isProcessAlive(job.pid) &&
      Number.isFinite(startedAt) &&
      lockStat.mtimeMs <= startedAt + PROCESS_START_SKEW_MS
    ) {
      return true;
    }
    // A lock without an owner record cannot otherwise be safely attributed to
    // a job, so leave it untouched.
    return false;
  }
  if (!owner || Number(owner.pid) !== job.pid) return false;

  const acquiredAt = Date.parse(owner.acquiredAt || "");
  const startedAt = Date.parse(job.startedAt || job.createdAt || "");
  // The run lock is acquired by the worker after the job is started.  A lock
  // issued before this job's start belongs to an older/newer execution and
  // must not be removed during orphan recovery.
  if (
    Number.isFinite(acquiredAt) &&
    Number.isFinite(startedAt) &&
    acquiredAt + PROCESS_START_SKEW_MS < startedAt
  ) {
    return false;
  }

  const currentIdentity = await readProcessIdentity(job.pid);
  if (job.workerIdentity && currentIdentity && !sameProcessIdentity(currentIdentity, job.workerIdentity)) {
    return false;
  }
  // If the process is gone, the persisted worker identity plus the lock's PID
  // and acquisition timestamp are the best available ownership proof.  If it
  // is alive but no identity was persisted, fail closed.
  if (currentIdentity && !job.workerIdentity) return false;
  return true;
}

export async function releaseJobRunLock(root, job) {
  if (!(await runLockBelongsToJob(root, job))) return false;
  const lockPath = path.resolve(root, ".delivery/runtime/locks", `${job.runKey}.lock`);
  try {
    const beforeUnlink = await fs.lstat(lockPath);
    // Recheck identity immediately before unlinking to close the PID-reuse
    // race between ownership validation and the destructive operation.
    if (!(await runLockBelongsToJob(root, job))) return false;
    const afterRecheck = await fs.lstat(lockPath);
    if (!sameFileIdentity(beforeUnlink, afterRecheck)) return false;
    await fs.unlink(lockPath);
    return true;
  } catch (error) {
    if (error.code === "ENOENT") return false;
    throw error;
  }
}

async function markJobFailed(root, job, code, message) {
  await releaseJobRunLock(root, job);
  return updateDeliveryJob({
    repoRoot: root,
    jobId: job.jobId,
    updates: {
      status: "failed",
      finishedAt: new Date().toISOString(),
      error: { code, message },
    },
  });
}

export async function findActiveDeliveryJob({ repoRoot, runKey }) {
  if (!runKey) return null;
  const root = findRepoRoot(repoRoot);
  const jobsDir = getJobsDir(root);
  let files = [];
  try {
    files = await fs.readdir(jobsDir);
  } catch (error) {
    if (error.code === "ENOENT") return null;
    throw error;
  }

  for (const file of files) {
    if (!file.endsWith(".json")) continue;
    const jobId = file.slice(0, -5);
    try {
      const job = await getDeliveryJob({ repoRoot: root, jobId });
      if (job && job.runKey === runKey && ["queued", "running"].includes(job.status)) {
        if (job.status === "queued" && !job.pid && queuedLeaseExpired(job)) {
          await markJobFailed(
            root,
            job,
            "JOB_QUEUE_EXPIRED",
            "Delivery job remained queued without a worker until its lease expired"
          );
          continue;
        }

        if (job.pid) {
          const processState = await processMatchesJob(job);
          if (!processState.alive) {
            await markJobFailed(
              root,
              job,
              processState.reason || "WORKER_CRASHED",
              processState.reason === "WORKER_IDENTITY_MISMATCH"
                ? "Delivery job PID no longer belongs to its original worker"
                : "Job worker process terminated unexpectedly"
            );
            continue;
          }
        } else if (job.status === "running" && queuedLeaseExpired({ ...job, queueLeaseUntil: job.startedAt })) {
          await markJobFailed(root, job, "JOB_WORKER_MISSING", "Running delivery job has no worker PID");
          continue;
        }
        return job;
      }
    } catch {
      // ignore
    }
  }
  return null;
}

export async function spawnJobWorker({ repoRoot, jobId, spawnFn = spawn }) {
  const root = findRepoRoot(repoRoot);
  validateJobId(jobId);
  const runnerPath = path.resolve(root, "tools/delivery-mcp/lib/job-runner.mjs");

  const job = await getDeliveryJob({ repoRoot: root, jobId });
  if (!job) throw new Error(`Job not found: ${jobId}`);
  if (TERMINAL_JOB_STATUSES.has(job.status)) return job.pid;

  const spawnOptions = {
    cwd: root,
    env: {
      ...process.env,
      NODE_ENV: process.env.NODE_ENV || "",
      DELIVERY_JOB_ID: jobId,
      ...(job.workerToken ? { DELIVERY_JOB_TOKEN: job.workerToken } : {}),
    },
    detached: process.platform !== "win32",
    stdio: ["ignore", "ignore", "ignore"],
  };

  let child;
  try {
    child = spawnFn(process.execPath, [runnerPath, jobId, root], spawnOptions);
    child.unref?.();
  } catch (error) {
    const message = redactSecrets(String(error?.message || "Unable to spawn delivery job worker"))
      .split("\n")[0]
      .slice(0, 240);
    await markJobFailed(root, job, "JOB_WORKER_SPAWN_FAILED", message);
    return null;
  }

  // ChildProcess reports executable/OS failures asynchronously.  Wait for
  // the first spawn/error event so callers never observe a permanently
  // running job after an immediate spawn failure.
  const spawnOutcome = await new Promise((resolve) => {
    let settled = false;
    const finish = (value) => {
      if (settled) return;
      settled = true;
      resolve(value);
    };
    child.once?.("spawn", () => finish({ ok: true }));
    child.once?.("error", (error) => finish({ ok: false, error }));
    // Test seams and unusual ChildProcess implementations may not emit
    // `spawn`; a present PID is enough to continue with normal bookkeeping.
    setImmediate(() => {
      if (!settled && child.pid) finish({ ok: true });
    });
  });

  if (!spawnOutcome.ok) {
    const message = redactSecrets(String(spawnOutcome.error?.message || "Unable to spawn delivery job worker"))
      .split("\n")[0]
      .slice(0, 240);
    await markJobFailed(root, job, "JOB_WORKER_SPAWN_FAILED", message);
    return null;
  }

  const workerIdentity = await readProcessIdentity(child.pid);
  await updateDeliveryJob({
    repoRoot: root,
    jobId,
    updates: {
      pid: child.pid,
      status: "running",
      startedAt: new Date().toISOString(),
      ...(workerIdentity ? { workerIdentity } : {}),
    },
  });

  return child.pid;
}

export async function cancelDeliveryJob({ repoRoot, jobId, reason = "Cancelled by user" }) {
  const root = findRepoRoot(repoRoot);
  const job = await getDeliveryJob({ repoRoot: root, jobId });
  if (!job) throw new Error(`Job not found: ${jobId}`);

  if (["passed", "failed", "timed_out", "cancelled"].includes(job.status)) {
    return job;
  }

  const processState = job.pid ? await processMatchesJob(job) : { alive: false, verified: false };
  if (processState.alive && processState.verified) {
    await killProcessTree(job.pid, "SIGTERM", job.workerIdentity);
    await new Promise((r) => setTimeout(r, 500));
    const stillOwned = await processMatchesJob(job);
    if (stillOwned.alive && stillOwned.verified) {
      await killProcessTree(job.pid, "SIGKILL", job.workerIdentity);
    }
  }

  await releaseJobRunLock(root, job);

  const updated = await updateDeliveryJob({
    repoRoot: root,
    jobId,
    updates: {
      status: "cancelled",
      finishedAt: new Date().toISOString(),
      error: {
        code: processState.alive && !processState.verified ? "JOB_CANCELLED_UNVERIFIED_WORKER" : "JOB_CANCELLED",
        message:
          processState.alive && !processState.verified
            ? `${reason}; worker identity could not be verified, so no signal was sent`
            : reason,
      },
    },
  });

  return updated;
}

export async function cleanupOrphanedDeliveryJobs({ repoRoot }) {
  const root = findRepoRoot(repoRoot);
  const jobsDir = getJobsDir(root);
  let files = [];
  try {
    files = await fs.readdir(jobsDir);
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw error;
  }

  const cleaned = [];
  for (const file of files) {
    if (!file.endsWith(".json")) continue;
    const jobId = file.slice(0, -5);
    try {
      const job = await getDeliveryJob({ repoRoot: root, jobId });
      if (job && ["queued", "running"].includes(job.status)) {
        if (job.status === "queued" && !job.pid && queuedLeaseExpired(job)) {
          await markJobFailed(
            root,
            job,
            "JOB_QUEUE_EXPIRED",
            "Delivery job remained queued without a worker until its lease expired"
          );
          cleaned.push(jobId);
        } else if (job.pid) {
          const processState = await processMatchesJob(job);
          if (!processState.alive) {
            await markJobFailed(
              root,
              job,
              processState.reason || "WORKER_CRASHED",
              processState.reason === "WORKER_IDENTITY_MISMATCH"
                ? "Orphaned job PID no longer belongs to its original worker"
                : "Orphaned job worker process died"
            );
            cleaned.push(jobId);
          }
        } else if (job.status === "running" && queuedLeaseExpired({ ...job, queueLeaseUntil: job.startedAt })) {
          await markJobFailed(root, job, "JOB_WORKER_MISSING", "Running delivery job has no worker PID");
          cleaned.push(jobId);
        }
      }
    } catch {
      // ignore
    }
  }
  return cleaned;
}

function formatTerminalJobResult(job) {
  // A CI wait timeout is a terminal outcome of the background job.  Do not
  // return the inner finalize result's legacy `in_progress` status, otherwise
  // callers cannot distinguish "worker finished waiting" from "worker is
  // still running".
  if (job.status === "timed_out") {
    const result = job.result && typeof job.result === "object" ? job.result : {};
    const diagnostics = Array.isArray(result.diagnostics) ? result.diagnostics : [];
    return {
      ...result,
      jobId: job.jobId,
      status: "timed_out",
      reason: result.reason || "JOB_TIMED_OUT",
      message: result.message || job.error?.message || "Delivery job exceeded execution deadline",
      diagnostics:
        diagnostics.length > 0
          ? diagnostics
          : [
              {
                code: job.error?.code || "JOB_TIMED_OUT",
                message: result.message || job.error?.message || "Delivery job timed out",
                retryable: true,
              },
            ],
    };
  }

  if (job.result) {
    return {
      jobId: job.jobId,
      ...job.result,
    };
  }

  if (job.status === "cancelled") {
    return {
      jobId: job.jobId,
      status: "cancelled",
      message: job.error?.message || "Delivery job was cancelled",
      diagnostics: [
        {
          code: "JOB_CANCELLED",
          message: job.error?.message || "Delivery job was cancelled",
          retryable: false,
        },
      ],
    };
  }

  if (job.status === "timed_out") {
    return {
      jobId: job.jobId,
      status: "timed_out",
      message: job.error?.message || "Delivery job exceeded execution deadline",
      diagnostics: [
        {
          code: "JOB_TIMED_OUT",
          message: job.error?.message || "Delivery job timed out",
          retryable: true,
        },
      ],
    };
  }

  return {
    jobId: job.jobId,
    status: job.status || "failed",
    message: job.error?.message || "Delivery job failed",
    diagnostics: [
      {
        code: job.error?.code || "JOB_FAILED",
        message: job.error?.message || "Delivery job failed without result",
        retryable: true,
      },
    ],
  };
}

export async function waitForJob({
  repoRoot,
  jobId,
  timeoutMs = 60000,
  pollIntervalMs = 500,
  sleepFn = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
}) {
  const root = findRepoRoot(repoRoot);
  validateJobId(jobId);

  const startedWait = Date.now();
  while (true) {
    const job = await getDeliveryJob({ repoRoot: root, jobId });
    if (!job) {
      return {
        jobId,
        status: "failed",
        diagnostics: [
          {
            code: "JOB_NOT_FOUND",
            message: `Delivery job '${jobId}' does not exist`,
            retryable: false,
          },
        ],
      };
    }

    if (["queued", "running"].includes(job.status)) {
      if (job.status === "queued" && !job.pid && queuedLeaseExpired(job)) {
        const failedJob = await markJobFailed(
          root,
          job,
          "JOB_QUEUE_EXPIRED",
          "Delivery job remained queued without a worker until its lease expired"
        );
        return formatTerminalJobResult(failedJob);
      }

      if (job.pid) {
        const processState = await processMatchesJob(job);
        if (!processState.alive) {
          await sleepFn(100);
          const fresh = await getDeliveryJob({ repoRoot: root, jobId });
          if (fresh && TERMINAL_JOB_STATUSES.has(fresh.status)) {
            return formatTerminalJobResult(fresh);
          }
          const failedJob = await markJobFailed(
            root,
            job,
            processState.reason || "JOB_WORKER_CRASHED",
            processState.reason === "WORKER_IDENTITY_MISMATCH"
              ? "Job PID no longer belongs to its original worker"
              : "Job worker process died unexpectedly"
          );
          return formatTerminalJobResult(failedJob);
        }
      } else if (job.status === "running" && queuedLeaseExpired({ ...job, queueLeaseUntil: job.startedAt })) {
        const failedJob = await markJobFailed(
          root,
          job,
          "JOB_WORKER_MISSING",
          "Running delivery job has no worker PID"
        );
        return formatTerminalJobResult(failedJob);
      }
    }

    if (TERMINAL_JOB_STATUSES.has(job.status)) {
      return formatTerminalJobResult(job);
    }

    const elapsed = Date.now() - startedWait;
    if (elapsed >= timeoutMs) {
      const jobDuration = job.startedAt
        ? Date.now() - new Date(job.startedAt).getTime()
        : elapsed;
      return {
        jobId,
        status: "running",
        durationMs: jobDuration,
        gateId: job.gateId,
        message: `Delivery job '${jobId}' is still in progress (${Math.round(jobDuration / 1000)}s elapsed). Call delivery_job_wait to continue waiting.`,
      };
    }

    const remaining = timeoutMs - elapsed;
    await sleepFn(Math.min(pollIntervalMs, remaining));
  }
}
