import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { runGate } from "../lib/run-gate.mjs";
import { loadDeliveryPolicy } from "../lib/policy-loader.mjs";
import { checkAndroidDevice } from "../lib/android-device.mjs";
import { validateExecutionResult } from "../lib/validate-schema.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

test("every gate stops on a failed check even without optional diagnostics", async (t) => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-gate-results-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.cp(path.join(ROOT, ".delivery/schemas"), path.join(root, ".delivery/schemas"), { recursive: true });
  const feature = "app/src/test/resources/features/example.feature";
  await fs.mkdir(path.dirname(path.join(root, feature)), { recursive: true });
  await fs.writeFile(path.join(root, feature), "Feature: Example\n");
  const policy = await loadDeliveryPolicy({ repoRoot: ROOT });
  for (const gateId of ["0", "A", "B", "C", "D", "R"]) {
    const gate = { ...policy.gates[gateId], reasonCodes: [], parameters: { scopeFeatures: [feature], repairsSha: "a".repeat(40) } };
    const inspection = { snapshotHash: "a".repeat(64), repository: { headSha: "b".repeat(40) },
      policy: { version: policy.version, hash: policy.sourceHash }, gate, diagnostics: [] };
    let calls = 0;
    const result = await runGate({ inspection, snapshot: { stagedFiles: [feature], cacheable: false }, policy, repoRoot: root,
      executeCheck: async ({ check }) => { calls++; return { id: check.id, status: "failed", durationMs: 1, exitCode: 1, summaryLines: ["Assertion failed"] }; } });
    assert.equal(result.status, "failed", gateId);
    assert.equal(result.summary.failed, 1, gateId);
    assert.equal(calls, 1, gateId);
    assert.equal(result.summary.skipped, gate.checkIds.length - 1, gateId);
    assert.deepEqual(result.gate.postPushChecks, policy.gates[gateId].postPushChecks);
    assert.throws(() => validateExecutionResult({ ...result, status: "passed" }, root), /failed check/);
    if (["C", "D", "R"].includes(gateId)) {
      const executed = [];
      const blocked = await runGate({ inspection, snapshot: { stagedFiles: [feature], cacheable: false }, policy, repoRoot: root,
        executeCheck: async ({ check }) => {
          executed.push(check.id);
          return { id: check.id, status: check.id === "android_device" ? "blocked" : "passed", durationMs: 1,
            summaryLines: [], diagnostic: check.id === "android_device" ? { code: "ANDROID_DEVICE_UNAVAILABLE", message: "No device", retryable: true } : null };
        } });
      assert.equal(blocked.status, "blocked", gateId);
      assert.equal(blocked.summary.failed, 0);
      assert.deepEqual(executed, gateId === "D" ? ["no_wip_in_scope", "android_device"] : ["android_device"]);
    }
  }
});

test("device prerequisite accepts only authorized devices and never starts an emulator", async () => {
  const query = async (listing, serial) => checkAndroidDevice({ repoRoot: ROOT, serial, run: async (command, args) => {
    assert.equal(command, "scripts/with-android-env.sh");
    assert.deepEqual(args, ["adb", "devices"]);
    return { stdout: listing };
  } });
  const header = "List of devices attached\n";
  assert.equal((await query(header + "test-device\tdevice\n", "")).status, "passed");
  for (const listing of [header, header + "test-device\toffline\n", header + "test-device\tunauthorized\n", "unknown output"]) {
    assert.equal((await query(listing, "")).status, "blocked");
  }
  assert.equal((await query(header + "test-device\tdevice\n", "missing")).status, "blocked");
  const unavailable = await checkAndroidDevice({ repoRoot: ROOT, run: async () => { throw new Error("toolchain unavailable"); } });
  assert.equal(unavailable.diagnostic.code, "ANDROID_DEVICE_QUERY_FAILED");
});
