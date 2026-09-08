import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  acquireRunLock,
  computeRunKey,
  createRunArtifacts,
  loadCachedFailure,
  loadCachedSuccess,
  saveRunEvidence,
} from "../lib/delivery-evidence.mjs";

test("computeRunKey is deterministic for the Android inspection identity", () => {
  const inspection = {
    snapshotHash: "a".repeat(64),
    repository: { headSha: "b".repeat(40) },
    policy: { hash: "c".repeat(64), version: 1 },
    gate: { id: "A", checkIds: ["jvm_test_dev"], parameters: {} },
  };
  const first = computeRunKey({
    inspection,
    snapshot: { stagedFiles: ["app/src/main/java/Welcome.kt"] },
  });
  const reordered = computeRunKey({
    inspection: {
      gate: { parameters: {}, checkIds: ["jvm_test_dev"], id: "A" },
      policy: { version: 1, hash: "c".repeat(64) },
      repository: { headSha: "b".repeat(40) },
      snapshotHash: "a".repeat(64),
    },
    snapshot: { stagedFiles: ["app/src/main/java/Welcome.kt"] },
  });
  assert.equal(first, reordered);
  assert.match(first, /^[a-f0-9]{64}$/);
});

test("run locks serialize equivalent Android executions", async (t) => {
  const repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), "android-evidence-lock-"));
  t.after(() => fs.rm(repoRoot, { recursive: true, force: true }));
  const release = await acquireRunLock({ repoRoot, runKey: "d".repeat(64) });
  try {
    await assert.rejects(
      () => acquireRunLock({ repoRoot, runKey: "d".repeat(64) }),
      (error) => error.code === "DELIVERY_RUN_IN_PROGRESS"
    );
  } finally {
    await release();
  }
  const reacquired = await acquireRunLock({ repoRoot, runKey: "d".repeat(64) });
  await reacquired();
});

test("run artifacts and cache writes stay below .delivery/runtime", async (t) => {
  const repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), "android-evidence-cache-"));
  t.after(() => fs.rm(repoRoot, { recursive: true, force: true }));
  const runKey = "e".repeat(64);
  const artifacts = await createRunArtifacts({ repoRoot, runKey });
  assert.match(artifacts.logDirectory, /^\.delivery\/runtime\/logs\//);
  assert.match(artifacts.recordPath, /^\.delivery\/runtime\/runs\//);

  const result = {
    status: "passed",
    runKey,
    evidence: { recordPath: artifacts.recordPath },
    snapshotHash: "f".repeat(64),
  };
  await saveRunEvidence({ repoRoot, result, cacheable: true });

  assert.deepEqual(await loadCachedSuccess({ repoRoot, runKey, cacheable: true }), {
    ...result,
    cached: true,
  });
  assert.equal(await loadCachedFailure({ repoRoot, runKey, cacheable: true }), null);
  assert.deepEqual(JSON.parse(await fs.readFile(path.join(repoRoot, ".delivery/runtime/latest.json"), "utf8")), result);
});

test("failed run evidence uses a separate failure cache key", async (t) => {
  const repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), "android-evidence-failure-"));
  t.after(() => fs.rm(repoRoot, { recursive: true, force: true }));
  const runKey = "1".repeat(64);
  const result = {
    status: "failed",
    runKey,
    evidence: { recordPath: null },
    snapshotHash: "2".repeat(64),
  };
  await saveRunEvidence({ repoRoot, result, cacheable: true });
  assert.equal(await loadCachedSuccess({ repoRoot, runKey, cacheable: true }), null);
  assert.deepEqual(await loadCachedFailure({ repoRoot, runKey, cacheable: true }), {
    ...result,
    cached: true,
  });
});

