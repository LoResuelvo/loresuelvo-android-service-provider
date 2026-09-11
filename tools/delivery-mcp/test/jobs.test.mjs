import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { execFileSync, spawn } from "node:child_process";
import {
  cancelDeliveryJob,
  cleanupOrphanedDeliveryJobs,
  createDeliveryJob,
  findActiveDeliveryJob,
  getDeliveryJob,
  isProcessAlive,
  validateJobId,
  waitForJob,
  updateDeliveryJob,
} from "../lib/jobs.mjs";

async function createTempRepo(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-jobs-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  execFileSync("git", ["init", "-b", "main"], { cwd: root, stdio: "ignore" });
  execFileSync("git", ["config", "user.name", "Delivery Tests"], { cwd: root });
  execFileSync("git", ["config", "user.email", "delivery-tests@example.com"], { cwd: root });
  await fs.mkdir(path.join(root, ".delivery", "runtime", "jobs"), { recursive: true });
  await fs.mkdir(path.join(root, ".delivery", "runtime", "locks"), { recursive: true });
  await fs.writeFile(path.join(root, "README.md"), "# Fixture\n", "utf8");
  execFileSync("git", ["add", "README.md"], { cwd: root });
  execFileSync("git", ["commit", "-m", "chore: initialize delivery fixture"], {
    cwd: root,
    stdio: "ignore",
  });
  return root;
}

test("jobs create, read, and update state with a safe job identifier", async (t) => {
  const repoRoot = await createTempRepo(t);
  const job = await createDeliveryJob({
    repoRoot,
    type: "prepare",
    params: { intent: "prepare_commit" },
    runKey: "run-key",
    snapshotHash: "snapshot-hash",
    gateId: "A",
  });

  assert.match(job.jobId, /^job-[0-9]+-[a-z0-9]+$/);
  assert.equal(job.status, "queued");
  assert.equal((await getDeliveryJob({ repoRoot, jobId: job.jobId })).runKey, "run-key");

  const updated = await updateDeliveryJob({
    repoRoot,
    jobId: job.jobId,
    updates: { status: "running", pid: process.pid, startedAt: new Date().toISOString() },
  });
  assert.equal(updated.status, "running");
  assert.equal(updated.pid, process.pid);
  assert.ok(updated.workerIdentity, "running jobs persist a process identity when available");
});

test("jobs reject unsafe identifiers and report process liveness", async (t) => {
  const repoRoot = await createTempRepo(t);
  for (const jobId of ["", "../escape", "job/1", "job with spaces", null]) {
    assert.throws(() => validateJobId(jobId), /Invalid job ID/);
  }
  await assert.rejects(
    getDeliveryJob({ repoRoot, jobId: "../escape" }),
    /Invalid job ID/
  );
  assert.equal(isProcessAlive(process.pid), true);
  assert.equal(isProcessAlive(9999999), false);
  assert.equal(isProcessAlive(null), false);
});

test("waitForJob returns terminal results and times out active jobs without changing them", async (t) => {
  const repoRoot = await createTempRepo(t);
  const passed = await createDeliveryJob({ repoRoot, type: "prepare" });
  await updateDeliveryJob({
    repoRoot,
    jobId: passed.jobId,
    updates: {
      status: "passed",
      result: { status: "passed", summary: { passed: 2, failed: 0, skipped: 0, durationMs: 3 } },
    },
  });
  const terminal = await waitForJob({ repoRoot, jobId: passed.jobId, timeoutMs: 50 });
  assert.equal(terminal.status, "passed");
  assert.equal(terminal.summary.passed, 2);

  const active = await createDeliveryJob({ repoRoot, type: "prepare" });
  await updateDeliveryJob({
    repoRoot,
    jobId: active.jobId,
    updates: { status: "running", pid: process.pid, startedAt: new Date().toISOString() },
  });
  const started = Date.now();
  const timeout = await waitForJob({ repoRoot, jobId: active.jobId, timeoutMs: 120, pollIntervalMs: 25 });
  assert.ok(Date.now() - started >= 100);
  assert.equal(timeout.status, "running");
  assert.match(timeout.message, /still in progress/);
  assert.equal((await getDeliveryJob({ repoRoot, jobId: active.jobId })).status, "running");
});

test("createDeliveryJob and updateDeliveryJob support worker tokens deterministically", async (t) => {
  const repoRoot = await createTempRepo(t);
  const explicitToken = "explicit-worker-token-xyz";
  const job = await createDeliveryJob({ repoRoot, type: "prepare", workerToken: explicitToken });
  assert.equal(job.workerToken, explicitToken);

  const updated = await updateDeliveryJob({
    repoRoot,
    jobId: job.jobId,
    updates: { status: "running", pid: process.pid, startedAt: new Date().toISOString(), workerToken: explicitToken },
  });
  assert.equal(updated.status, "running");
  assert.equal(updated.workerToken, explicitToken);
});

test("job state and run-key locks remain isolated across repository roots", async (t) => {
  const roots = await Promise.all([createTempRepo(t), createTempRepo(t)]);
  const runKey = "same-run-key";
  const [left, right] = await Promise.all(roots.map((repoRoot, index) => createDeliveryJob({
    repoRoot,
    runKey,
    type: index === 0 ? "prepare" : "inspect",
    workerToken: `token-${index}`,
  })));

  assert.notEqual(left.jobId, right.jobId);
  assert.equal((await findActiveDeliveryJob({ repoRoot: roots[0], runKey })).jobId, left.jobId);
  assert.equal((await findActiveDeliveryJob({ repoRoot: roots[1], runKey })).jobId, right.jobId);
  assert.equal((await getDeliveryJob({ repoRoot: roots[0], jobId: right.jobId })), null);
  assert.equal((await getDeliveryJob({ repoRoot: roots[1], jobId: left.jobId })), null);
  assert.equal((await getDeliveryJob({ repoRoot: roots[0], jobId: left.jobId })).workerToken, "token-0");
  assert.equal((await getDeliveryJob({ repoRoot: roots[1], jobId: right.jobId })).workerToken, "token-1");
});

test("queued jobs expire and dead workers are recovered with a terminal diagnostic", async (t) => {
  const repoRoot = await createTempRepo(t);
  const queued = await createDeliveryJob({ repoRoot, runKey: "expired-queue" });
  await updateDeliveryJob({
    repoRoot,
    jobId: queued.jobId,
    updates: { queueLeaseUntil: new Date(Date.now() - 1).toISOString() },
  });
  assert.equal(await findActiveDeliveryJob({ repoRoot, runKey: "expired-queue" }), null);
  assert.equal((await getDeliveryJob({ repoRoot, jobId: queued.jobId })).error.code, "JOB_QUEUE_EXPIRED");

  const runKey = "dead-worker";
  const lockPath = path.join(repoRoot, ".delivery/runtime/locks", `${runKey}.lock`);
  await fs.writeFile(lockPath, "lock\n", "utf8");
  const dead = await createDeliveryJob({ repoRoot, runKey });
  await updateDeliveryJob({
    repoRoot,
    jobId: dead.jobId,
    updates: { status: "running", pid: 9999999, startedAt: new Date().toISOString() },
  });
  const recovered = await waitForJob({ repoRoot, jobId: dead.jobId, timeoutMs: 500 });
  assert.equal(recovered.status, "failed");
  assert.equal(recovered.diagnostics[0].code, "JOB_WORKER_CRASHED");
  await assert.rejects(fs.access(lockPath), /ENOENT/);
});

test("cancelDeliveryJob stops its verified worker and releases its run lock", async (t) => {
  const repoRoot = await createTempRepo(t);
  const runKey = "cancel-worker";
  const lockPath = path.join(repoRoot, ".delivery/runtime/locks", `${runKey}.lock`);
  const job = await createDeliveryJob({ repoRoot, runKey });
  const worker = spawn(process.execPath, ["-e", "setInterval(() => {}, 1000)"], { stdio: "ignore" });
  t.after(() => {
    try {
      worker.kill("SIGKILL");
    } catch {}
  });
  await updateDeliveryJob({
    repoRoot,
    jobId: job.jobId,
    updates: { status: "running", pid: worker.pid, startedAt: new Date().toISOString() },
  });
  const running = await getDeliveryJob({ repoRoot, jobId: job.jobId });
  await fs.writeFile(lockPath, `${JSON.stringify({ pid: worker.pid, acquiredAt: new Date().toISOString() })}\n`, "utf8");

  const cancelled = await cancelDeliveryJob({ repoRoot, jobId: job.jobId, reason: "Cancelled by test" });
  assert.equal(cancelled.status, "cancelled");
  assert.equal(cancelled.error.code, "JOB_CANCELLED");
  assert.equal((await getDeliveryJob({ repoRoot, jobId: job.jobId })).pid, running.pid);
  await assert.rejects(fs.access(lockPath), /ENOENT/);
});

test("cleanupOrphanedDeliveryJobs repairs dead workers and leaves completed jobs unchanged", async (t) => {
  const repoRoot = await createTempRepo(t);
  const dead = await createDeliveryJob({ repoRoot, runKey: "orphan" });
  await updateDeliveryJob({
    repoRoot,
    jobId: dead.jobId,
    updates: { status: "running", pid: 9999999, startedAt: new Date().toISOString() },
  });
  const complete = await createDeliveryJob({ repoRoot });
  await updateDeliveryJob({ repoRoot, jobId: complete.jobId, updates: { status: "passed" } });

  const cleaned = await cleanupOrphanedDeliveryJobs({ repoRoot });
  assert.deepEqual(cleaned, [dead.jobId]);
  assert.equal((await getDeliveryJob({ repoRoot, jobId: dead.jobId })).status, "failed");
  assert.equal((await getDeliveryJob({ repoRoot, jobId: complete.jobId })).status, "passed");
});
