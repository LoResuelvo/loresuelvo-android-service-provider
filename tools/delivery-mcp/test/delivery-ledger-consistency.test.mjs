import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  acquireLedgerLock,
  getLedgerState,
  listCommitEvidence,
  LEDGER_DIR,
  LEDGER_FILE,
} from "../lib/delivery-ledger.mjs";

function notRunEntry(commitSha, reason = "NO_PREPARED_RECEIPT") {
  return {
    schemaVersion: 2,
    commitSha,
    status: "not_run",
    verificationStatus: "not_run",
    notRunReason: reason,
    branch: "main",
    parentSha: null,
    treeSha: "a".repeat(40),
    stagedFiles: [],
    usId: "33",
    recordedAt: "2026-09-07T00:00:00.000Z",
    snapshotHash: null,
    runKey: null,
    recordPath: null,
    recordDigest: null,
    gateId: null,
    policyHash: null,
    intent: null,
    featureFile: null,
    scenarioName: null,
    scopeFiles: [],
    repairsSha: null,
    supersedes: [],
    repairStatus: null,
    repairedFailure: null,
    repairAuthState: null,
    repairAuthSha: null,
    repairPushConsumed: false,
    repairPushConsumedAt: null,
  };
}

async function createLedgerRepo(t) {
  const repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), "android-ledger-consistency-"));
  t.after(() => fs.rm(repoRoot, { recursive: true, force: true }));
  await fs.mkdir(path.join(repoRoot, LEDGER_DIR), { recursive: true });
  return repoRoot;
}

async function writePair(repoRoot, entry) {
  await fs.writeFile(
    path.join(repoRoot, LEDGER_DIR, entry.commitSha + ".json"),
    JSON.stringify(entry, null, 2) + "\n",
    "utf8"
  );
  await fs.writeFile(
    path.join(repoRoot, LEDGER_FILE),
    JSON.stringify({ [entry.commitSha]: entry }, null, 2) + "\n",
    "utf8"
  );
}

test("listCommitEvidence and getLedgerState wait for an in-flight ledger transaction", async (t) => {
  const repoRoot = await createLedgerRepo(t);
  const commitSha = "b".repeat(40);
  const oldEntry = notRunEntry(commitSha, "old-reason");
  const newEntry = notRunEntry(commitSha, "new-reason");
  await writePair(repoRoot, oldEntry);

  const release = await acquireLedgerLock({ repoRoot });
  let listSettled = false;
  let stateSettled = false;
  const listPromise = listCommitEvidence({ repoRoot }).then((value) => {
    listSettled = true;
    return value;
  });
  const statePromise = getLedgerState({ repoRoot }).then((value) => {
    stateSettled = true;
    return value;
  });

  try {
    await fs.writeFile(
      path.join(repoRoot, LEDGER_DIR, commitSha + ".json"),
      JSON.stringify(newEntry, null, 2) + "\n",
      "utf8"
    );
    await new Promise((resolve) => setTimeout(resolve, 60));
    assert.equal(listSettled, false);
    assert.equal(stateSettled, false);
    await fs.writeFile(
      path.join(repoRoot, LEDGER_FILE),
      JSON.stringify({ [commitSha]: newEntry }, null, 2) + "\n",
      "utf8"
    );
  } finally {
    await release();
  }

  const [entries, state] = await Promise.all([listPromise, statePromise]);
  assert.equal(entries.length, 1);
  assert.equal(entries[0].notRunReason, "new-reason");
  assert.equal(state.state, "VALID_LEDGER");
});

test("listCommitEvidence rebuilds a missing consolidated ledger from individual Android records", async (t) => {
  const repoRoot = await createLedgerRepo(t);
  const commitSha = "c".repeat(40);
  const entry = notRunEntry(commitSha);
  await fs.writeFile(
    path.join(repoRoot, LEDGER_DIR, commitSha + ".json"),
    JSON.stringify(entry, null, 2) + "\n",
    "utf8"
  );

  const entries = await listCommitEvidence({ repoRoot });
  assert.equal(entries.length, 1);
  assert.equal(entries[0].commitSha, commitSha);
  assert.deepEqual(JSON.parse(await fs.readFile(path.join(repoRoot, LEDGER_FILE), "utf8")), {
    [commitSha]: entry,
  });
  assert.equal((await getLedgerState({ repoRoot })).state, "VALID_LEDGER");
});

test("getLedgerState and listCommitEvidence detect divergent individual and consolidated records", async (t) => {
  const repoRoot = await createLedgerRepo(t);
  const commitSha = "d".repeat(40);
  const entry = notRunEntry(commitSha);
  await writePair(repoRoot, entry);

  const consolidated = { ...entry, repairStatus: "tampered" };
  await fs.writeFile(
    path.join(repoRoot, LEDGER_FILE),
    JSON.stringify({ [commitSha]: consolidated }, null, 2) + "\n",
    "utf8"
  );

  const state = await getLedgerState({ repoRoot });
  assert.equal(state.state, "LEDGER_INCONSISTENT");
  assert.equal(state.reason, "ENTRY_MISMATCH");
  await assert.rejects(
    () => listCommitEvidence({ repoRoot }),
    (error) => error.code === "LEDGER_INCONSISTENT"
  );
});
