import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  clearDeliveryContext,
  consumeDeliveryContext,
  inferWipRemovalScenario,
  loadDeliveryContext,
  saveDeliveryContext,
  validateDeliveryContext,
} from "../lib/delivery-context.mjs";

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

async function createContextRepo(t) {
  const repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-context-"));
  t.after(() => fs.rm(repoRoot, { recursive: true, force: true }));
  await fs.mkdir(path.join(repoRoot, ".delivery", "schemas"), { recursive: true });
  await fs.copyFile(
    path.join(sourceRoot, ".delivery", "schemas", "delivery-context.schema.json"),
    path.join(repoRoot, ".delivery", "schemas", "delivery-context.schema.json")
  );
  return repoRoot;
}

const snapshot = {
  branch: "feature/android-delivery",
  headSha: "a".repeat(40),
  snapshotHash: "b".repeat(64),
};

test("saveDeliveryContext and loadDeliveryContext preserve Android scope and numeric work item", async (t) => {
  const repoRoot = await createContextRepo(t);
  const saved = await saveDeliveryContext({
    repoRoot,
    snapshot,
    intent: "close_scenario",
    usId: "33",
    featureFile: "app/src/test/resources/features/welcome.feature",
    scenarioName: "Provider welcome",
    scopeFiles: [
      "app/src/test/resources/features/welcome.feature",
      "app/src/test/java/com/loresuelvo/serviceprovider/bdd/WelcomeCucumberTest.kt",
    ],
  });

  assert.equal(saved.schemaVersion, 1);
  assert.equal(saved.branch, snapshot.branch);
  assert.equal(saved.headSha, snapshot.headSha);
  assert.equal(saved.snapshotHash, snapshot.snapshotHash);
  assert.equal(saved.intent, "close_scenario");
  assert.equal(saved.usId, "33");
  assert.equal(saved.featureFile, "app/src/test/resources/features/welcome.feature");
  assert.equal(saved.consumed, false);
  assert.deepEqual(await loadDeliveryContext({ repoRoot }), saved);
  assert.equal(validateDeliveryContext({ context: saved, snapshot }).valid, true);
});

test("validateDeliveryContext expires on identity changes and detects numeric work-item conflicts", async (t) => {
  const repoRoot = await createContextRepo(t);
  const context = await saveDeliveryContext({
    repoRoot,
    snapshot,
    intent: "close_us",
    usId: "33",
    scopeFiles: ["app/src/main/java/com/loresuelvo/serviceprovider/Welcome.kt"],
  });

  const headMismatch = validateDeliveryContext({
    context,
    snapshot: { ...snapshot, headSha: "c".repeat(40) },
  });
  assert.equal(headMismatch.reason, "CONTEXT_HEAD_MISMATCH");
  assert.equal(headMismatch.expired, true);

  const treeMismatch = validateDeliveryContext({
    context,
    snapshot: { ...snapshot, snapshotHash: "d".repeat(64) },
  });
  assert.equal(treeMismatch.reason, "CONTEXT_SNAPSHOT_MISMATCH");
  assert.equal(treeMismatch.expired, true);

  const branchMismatch = validateDeliveryContext({
    context,
    snapshot: { ...snapshot, branch: "main" },
  });
  assert.equal(branchMismatch.reason, "CONTEXT_BRANCH_MISMATCH");
  assert.equal(branchMismatch.expired, true);

  const conflict = validateDeliveryContext({
    context,
    snapshot,
    proposedCommitMessage: "feat[34]: use a different work item",
  });
  assert.equal(conflict.valid, false);
  assert.equal(conflict.conflict, true);
  assert.equal(conflict.reason, "CONTEXT_US_CONFLICT");

  assert.equal(
    validateDeliveryContext({
      context,
      snapshot,
      proposedCommitMessage: "feat[33]: finish Android delivery",
    }).valid,
    true
  );
  // The legacy spelling is rejected by commit validation and is not treated as
  // a matching context identifier here.
  assert.equal(
    validateDeliveryContext({
      context,
      snapshot,
      proposedCommitMessage: "feat[US-33]: legacy work item",
    }).valid,
    true
  );
});

test("consumeDeliveryContext marks the receipt stale and clear removes it", async (t) => {
  const repoRoot = await createContextRepo(t);
  const context = await saveDeliveryContext({ repoRoot, snapshot, intent: "prepare_commit", usId: "33" });
  const consumed = await consumeDeliveryContext({ repoRoot, context });
  assert.equal(consumed.consumed, true);
  assert.match(consumed.consumedAt, /^\d{4}-\d{2}-\d{2}T/);

  const validation = validateDeliveryContext({ context: consumed, snapshot });
  assert.equal(validation.valid, false);
  assert.equal(validation.expired, true);
  assert.equal(validation.reason, "CONTEXT_ALREADY_CONSUMED");

  assert.deepEqual(await clearDeliveryContext({ repoRoot }), { cleared: true });
  assert.equal(await loadDeliveryContext({ repoRoot }), null);
  assert.deepEqual(await consumeDeliveryContext({ repoRoot }), { consumed: false, reason: "NO_CONTEXT" });
});

test("saveDeliveryContext rejects traversal in feature and scope paths", async (t) => {
  const repoRoot = await createContextRepo(t);
  await assert.rejects(
    () => saveDeliveryContext({
      repoRoot,
      snapshot,
      featureFile: "../outside.feature",
    }),
    /path traversal/
  );
  await assert.rejects(
    () => saveDeliveryContext({
      repoRoot,
      snapshot,
      scopeFiles: ["app/src/test/resources/features/../../outside.feature"],
    }),
    /path traversal/
  );
});

test("inferWipRemovalScenario recognizes one Android feature scenario only", () => {
  const diff = [
    "diff --git a/app/src/test/resources/features/welcome.feature b/app/src/test/resources/features/welcome.feature",
    "--- a/app/src/test/resources/features/welcome.feature",
    "+++ b/app/src/test/resources/features/welcome.feature",
    "@@ -4,3 +4,2 @@",
    "-  @wip",
    "   Scenario: Provider welcome",
  ].join("\n");

  const file = "app/src/test/resources/features/welcome.feature";
  const inferred = inferWipRemovalScenario(diff, [file]);
  assert.deepEqual(inferred, {
    inferred: true,
    intent: "close_scenario",
    featureFile: file,
    scenarioName: "Provider welcome",
  });
  assert.equal(inferWipRemovalScenario(diff + "\n+  @wip", [file]), null);
  assert.equal(inferWipRemovalScenario(diff, [file, "app/src/test/resources/features/other.feature"]), null);
  assert.equal(inferWipRemovalScenario(diff, ["app/src/main/README.md"]), null);
});

