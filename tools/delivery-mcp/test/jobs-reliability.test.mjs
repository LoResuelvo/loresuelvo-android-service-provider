import test from "node:test";
import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  createDeliveryJob,
  getDeliveryJob,
  releaseJobRunLock,
  spawnJobWorker,
  updateDeliveryJob,
} from "../lib/jobs.mjs";

async function createRuntimeRoot(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-reliability-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.mkdir(path.join(root, ".delivery/runtime/jobs"), { recursive: true });
  await fs.mkdir(path.join(root, ".delivery/runtime/locks"), { recursive: true });
  return root;
}

async function writeLock(root, runKey, owner) {
  await fs.writeFile(
    path.join(root, ".delivery/runtime/locks", `${runKey}.lock`),
    `${JSON.stringify(owner)}\n`,
    "utf8"
  );
}

test("run-lock cleanup only removes a lock owned by the job worker", async (t) => {
  const root = await createRuntimeRoot(t);
  const runKey = "run-owner-check";
  const job = await createDeliveryJob({ repoRoot: root, runKey });
  const running = await updateDeliveryJob({
    repoRoot: root,
    jobId: job.jobId,
    updates: { status: "running", pid: process.pid, startedAt: new Date().toISOString() },
  });

  await writeLock(root, runKey, { pid: process.pid, acquiredAt: new Date().toISOString() });
  assert.equal(await releaseJobRunLock(root, running), true);
  await assert.rejects(
    fs.access(path.join(root, ".delivery/runtime/locks", `${runKey}.lock`)),
    /ENOENT/
  );

  await writeLock(root, runKey, { pid: 9999999, acquiredAt: new Date().toISOString() });
  assert.equal(await releaseJobRunLock(root, running), false);
  await fs.access(path.join(root, ".delivery/runtime/locks", `${runKey}.lock`));
});

test("asynchronous worker spawn failures become compact failures and preserve another lock", async (t) => {
  const root = await createRuntimeRoot(t);
  const runKey = "run-spawn-error";
  const job = await createDeliveryJob({ repoRoot: root, runKey });
  await writeLock(root, runKey, { pid: process.pid, acquiredAt: new Date().toISOString() });

  const child = new EventEmitter();
  child.pid = undefined;
  child.unref = () => {};
  const spawnFn = () => {
    queueMicrotask(() => {
      const error = new Error("permission denied\nsecret=do-not-leak");
      error.code = "EACCES";
      child.emit("error", error);
    });
    return child;
  };

  assert.equal(await spawnJobWorker({ repoRoot: root, jobId: job.jobId, spawnFn }), null);
  const failed = await getDeliveryJob({ repoRoot: root, jobId: job.jobId });
  assert.equal(failed.status, "failed");
  assert.equal(failed.error.code, "JOB_WORKER_SPAWN_FAILED");
  assert.equal(failed.error.message, "permission denied");
  await fs.access(path.join(root, ".delivery/runtime/locks", `${runKey}.lock`));
});
