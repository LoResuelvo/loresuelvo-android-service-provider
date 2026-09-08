import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { evaluateCiWindow, recordCommitEvidence } from "../lib/delivery-ledger.mjs";
import { MockCiProvider } from "../lib/ci-provider.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

async function createLedgerRoot(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-window-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.mkdir(path.join(root, ".delivery/runtime/ledger"), { recursive: true });
  await fs.mkdir(path.join(root, ".delivery/runtime/locks"), { recursive: true });
  await fs.mkdir(path.join(root, ".delivery/runtime/ci"), { recursive: true });
  await fs.mkdir(path.join(root, ".delivery/schemas"), { recursive: true });
  await fs.copyFile(
    path.join(ROOT, ".delivery", "schemas", "ci-inspection-result.schema.json"),
    path.join(root, ".delivery", "schemas", "ci-inspection-result.schema.json"),
  );
  return root;
}

const policy = { ci: { maxInFlightCommits: 4 } };

async function addNotRun(root, sha, usId = "33") {
  return recordCommitEvidence({
    repoRoot: root,
    commitSha: sha,
    verificationStatus: "not_run",
    notRunReason: "human_commit_no_receipt",
    branch: "main",
    parentSha: null,
    treeSha: "f".repeat(40),
    stagedFiles: ["README.md"],
    usId,
  });
}

test("CI window allows fewer than four pending commits and blocks a full window", async (t) => {
  const root = await createLedgerRoot(t);
  const shas = Array.from({ length: 4 }, (_, index) => `${(index + 1).toString(16)}${"a".repeat(39)}`);
  for (const sha of shas) await addNotRun(root, sha);
  const provider = new MockCiProvider(Object.fromEntries(shas.map((sha) => [sha, { status: "queued" }])));

  const full = await evaluateCiWindow({ repoRoot: root, policy, ciProvider: provider });
  assert.equal(full.allowed, false);
  assert.equal(full.reason, "CI_WINDOW_FULL");
  assert.equal(full.pendingCount, 4);

  provider.setFixture(shas[0], { status: "passed" });
  const reopened = await evaluateCiWindow({ repoRoot: root, policy, ciProvider: provider });
  assert.equal(reopened.allowed, true);
  assert.equal(reopened.pendingCount, 3);
});

test("CI window accounts for the commit currently being prepared", async (t) => {
  const root = await createLedgerRoot(t);
  const shas = Array.from({ length: 3 }, (_, index) => `${(index + 1).toString(16)}${"b".repeat(39)}`);
  for (const sha of shas) await addNotRun(root, sha);
  const provider = new MockCiProvider(Object.fromEntries(shas.map((sha) => [sha, { status: "queued" }])));

  const allowed = await evaluateCiWindow({ repoRoot: root, policy, ciProvider: provider, commitCount: 1 });
  assert.equal(allowed.allowed, true);
  assert.equal(allowed.inFlightCount, 4);

  const blocked = await evaluateCiWindow({ repoRoot: root, policy, ciProvider: provider, commitCount: 2 });
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.reason, "CI_PENDING_WINDOW_EXCEEDED");
  assert.equal(blocked.inFlightCount, 5);
});

test("CI failures and provider errors fail closed before ordinary delivery proceeds", async (t) => {
  const root = await createLedgerRoot(t);
  const failedSha = "c".repeat(40);
  await addNotRun(root, failedSha);
  const failedProvider = new MockCiProvider({ [failedSha]: { status: "failed" } });
  const failed = await evaluateCiWindow({ repoRoot: root, policy, ciProvider: failedProvider });
  assert.equal(failed.allowed, false);
  assert.equal(failed.reason, "PRIOR_COMMIT_CI_FAILED");
  assert.equal(failed.failedSha, failedSha);

  const providerError = new MockCiProvider({ [failedSha]: { status: "provider_error" } });
  const blocked = await evaluateCiWindow({ repoRoot: root, policy, ciProvider: providerError });
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.reason, "CI_PROVIDER_ERROR");
});

test("repair intent can proceed only as an explicitly scoped repair", async (t) => {
  const root = await createLedgerRoot(t);
  const failedSha = "d".repeat(40);
  await addNotRun(root, failedSha);
  const provider = new MockCiProvider({ [failedSha]: { status: "failed" } });

  const mismatch = await evaluateCiWindow({
    repoRoot: root,
    policy,
    ciProvider: provider,
    intent: "repair_ci",
    repairsSha: "e".repeat(40),
  });
  assert.equal(mismatch.allowed, false);
  assert.equal(mismatch.reason, "REPAIR_TARGET_MISMATCH");

  const matching = await evaluateCiWindow({
    repoRoot: root,
    policy,
    ciProvider: provider,
    intent: "repair_ci",
    repairsSha: failedSha,
  });
  assert.equal(matching.allowed, true);
  assert.equal(matching.isRepair, true);
  assert.equal(matching.activeIncident.failedSha, failedSha);
});
