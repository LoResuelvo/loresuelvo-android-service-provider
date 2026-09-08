import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";
import { resolveReview, prepareDelivery } from "../lib/prepare-delivery.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

const inspection = {
  schemaVersion: 1,
  status: "review_required",
  snapshotHash: "a".repeat(64),
  gate: { id: "A", reasonCodes: ["MAINTAINABILITY_REVIEW"], checkIds: ["delivery_unit"], postPushChecks: [] },
  maintainability: {
    status: "review_required",
    signalCount: 2,
    signals: [
      { id: "longFunction:app/src/main/Foo.kt:10", rule: "longFunction", file: "app/src/main/Foo.kt", line: 10 },
      { id: "complexity:app/src/main/Foo.kt:30", rule: "complexity", file: "app/src/main/Foo.kt", line: 30 },
    ],
  },
  diagnostics: [],
};

test("maintainability review requires an acknowledgement for the exact snapshot", () => {
  const missing = resolveReview(inspection, null);
  assert.equal(missing.accepted, false);
  assert.equal(missing.status, "review_required");
  assert.equal(missing.diagnostic.code, "MAINTAINABILITY_ACK_REQUIRED");
  assert.equal(missing.requiredAcknowledgement.snapshotHash, inspection.snapshotHash);

  const mismatch = resolveReview(inspection, {
    snapshotHash: "b".repeat(64),
    decisions: Object.fromEntries(inspection.maintainability.signals.map((signal) => [signal.id, "Reviewed with sufficient detail"])),
  });
  assert.equal(mismatch.accepted, false);
  assert.equal(mismatch.status, "blocked");
  assert.equal(mismatch.diagnostic.code, "MAINTAINABILITY_HASH_MISMATCH");
});

test("maintainability review rejects generic, incomplete, and short decisions", () => {
  const generic = resolveReview({ ...inspection }, { snapshotHash: inspection.snapshotHash, reason: "approve all" });
  assert.equal(generic.diagnostic.code, "MAINTAINABILITY_DECISIONS_INCOMPLETE");

  const incomplete = resolveReview(inspection, {
    snapshotHash: inspection.snapshotHash,
    decisions: { [inspection.maintainability.signals[0].id]: "Reviewed this signal carefully" },
  });
  assert.match(incomplete.diagnostic.message, /missing:/);

  const short = resolveReview(inspection, {
    snapshotHash: inspection.snapshotHash,
    decisions: {
      [inspection.maintainability.signals[0].id]: "too short",
      [inspection.maintainability.signals[1].id]: "Reviewed this signal carefully",
    },
  });
  assert.match(short.diagnostic.message, /justification < 12 chars/);
});

test("maintainability review accepts explicit per-signal decisions", () => {
  const result = resolveReview(inspection, {
    snapshotHash: inspection.snapshotHash,
    decisions: {
      [inspection.maintainability.signals[0].id]: "The function remains cohesive in this Android change",
      [inspection.maintainability.signals[1].id]: "The branching is intentional for provider state handling",
    },
    reason: "Reviewed each signal",
  });
  assert.equal(result.accepted, true);
  assert.equal(result.review.status, "acknowledged");
  assert.equal(Object.keys(result.review.decisions).length, 2);
});

test("truncated maintainability signals cannot be acknowledged partially", () => {
  const result = resolveReview(
    {
      ...inspection,
      maintainability: { ...inspection.maintainability, signalCount: 21, truncated: true },
    },
    {
      snapshotHash: inspection.snapshotHash,
      decisions: Object.fromEntries(inspection.maintainability.signals.map((signal) => [signal.id, "Reviewed this signal carefully"])),
    },
  );
  assert.equal(result.accepted, false);
  assert.equal(result.diagnostic.code, "MAINTAINABILITY_SIGNAL_LIMIT_EXCEEDED");
});

async function createTempRepo(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-prepare-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  execFileSync("git", ["init", "-b", "main"], { cwd: root, stdio: "ignore" });
  execFileSync("git", ["config", "user.name", "Delivery Tests"], { cwd: root });
  execFileSync("git", ["config", "user.email", "delivery-tests@example.com"], { cwd: root });
  execFileSync("git", ["config", "commit.gpgsign", "false"], { cwd: root });

  await fs.mkdir(path.join(root, ".delivery", "schemas"), { recursive: true });
  for (const schema of [
    "ci-inspection-result.schema.json",
    "delivery-context.schema.json",
    "execution-result.schema.json",
    "inspection-result.schema.json",
    "policy.schema.json",
  ]) {
    await fs.copyFile(path.join(ROOT, ".delivery", "schemas", schema), path.join(root, ".delivery", "schemas", schema));
  }
  await fs.copyFile(path.join(ROOT, ".delivery", "policy.v1.json"), path.join(root, ".delivery", "policy.v1.json"));
  await fs.writeFile(path.join(root, "README.md"), "# Android service provider\n", "utf8");
  execFileSync("git", ["add", "."], { cwd: root });
  execFileSync("git", ["commit", "-m", "chore: initialize Android fixture"], { cwd: root, stdio: "ignore" });
  return root;
}

test("prepareDelivery runs the selected Android feature gate and records prepared evidence", async (t) => {
  const repoRoot = await createTempRepo(t);
  const feature = "app/src/test/resources/features/provider.feature";
  await fs.mkdir(path.dirname(path.join(repoRoot, feature)), { recursive: true });
  await fs.writeFile(path.join(repoRoot, feature), "Feature: Provider\n  Scenario: welcomes a provider\n    Given the service provider app is open\n", "utf8");
  execFileSync("git", ["add", feature], { cwd: repoRoot });

  const result = await prepareDelivery({
    repoRoot,
    intent: "prepare_commit",
    executeCheck: async ({ check, logPath }) => ({
      id: check.id,
      status: "passed",
      durationMs: 1,
      exitCode: 0,
      summaryLines: [`${check.id} passed`],
      locations: [],
      logPath,
      diagnostic: null,
    }),
    force: true,
  });

  assert.equal(result.status, "passed");
  assert.equal(result.gate.id, "0");
  assert.ok(result.evidence.recordPath);
  assert.ok(result.snapshotHash);
});
