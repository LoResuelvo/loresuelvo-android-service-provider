import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import {
  consumePreparedEvidence,
  getCommitEvidence,
  getLastPreparedEvidence,
  queryCommitEvidence,
  recordCommitEvidence,
  recordPreparedEvidence,
  verifyCommitEvidence,
  verifyPreparedEvidence,
  LEDGER_DIR,
  LEDGER_FILE,
} from "../lib/delivery-ledger.mjs";

const sourceRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), "../../..");

async function createGitRepo(t) {
  const repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-ledger-"));
  t.after(() => fs.rm(repoRoot, { recursive: true, force: true }));
  execFileSync("git", ["init", "-b", "main"], { cwd: repoRoot });
  execFileSync("git", ["config", "user.name", "Delivery Test"], { cwd: repoRoot });
  execFileSync("git", ["config", "user.email", "delivery@example.test"], { cwd: repoRoot });
  execFileSync("git", ["config", "commit.gpgsign", "false"], { cwd: repoRoot });
  await fs.mkdir(path.join(repoRoot, ".delivery", "schemas"), { recursive: true });
  await fs.copyFile(
    path.join(sourceRoot, ".delivery", "schemas", "execution-result.schema.json"),
    path.join(repoRoot, ".delivery", "schemas", "execution-result.schema.json")
  );
  await fs.writeFile(path.join(repoRoot, "README.md"), "# Android provider\n", "utf8");
  execFileSync("git", ["add", "README.md"], { cwd: repoRoot });
  execFileSync("git", ["commit", "-m", "chore: initialize Android provider"], { cwd: repoRoot });
  return repoRoot;
}

async function commitAndroidFile(repoRoot, name, message = "feat[33]: update provider") {
  const file = path.join(repoRoot, "app", "src", "main", "java", "com", "loresuelvo", "serviceprovider", name);
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(file, "package com.loresuelvo.serviceprovider\nclass Provider\n", "utf8");
  execFileSync("git", ["add", "."], { cwd: repoRoot });
  execFileSync("git", ["commit", "-m", message], { cwd: repoRoot });
  const commitSha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: repoRoot, encoding: "utf8" }).trim();
  const parentSha = execFileSync("git", ["rev-parse", "HEAD^"], { cwd: repoRoot, encoding: "utf8" }).trim();
  const treeSha = execFileSync("git", ["rev-parse", "HEAD^{tree}"], { cwd: repoRoot, encoding: "utf8" }).trim();
  return { commitSha, parentSha, treeSha, stagedFile: path.relative(repoRoot, file) };
}

function executionRecord({ snapshotHash, runKey, gateId = "A", policyHash }) {
  return {
    schemaVersion: 1,
    status: "passed",
    snapshotHash,
    runKey,
    cached: false,
    policy: { version: 1, hash: policyHash },
    gate: {
      id: gateId,
      reasonCodes: ["ANDROID_TEST_EVIDENCE"],
      checkIds: ["jvm_test_dev"],
      parameters: {},
      postPushChecks: [],
    },
    summary: { passed: 1, failed: 0, skipped: 0, durationMs: 1 },
    checks: [{ id: "jvm_test_dev", status: "passed", durationMs: 1 }],
    diagnostics: [],
    evidence: { recordPath: null },
  };
}

async function writeExecutionRecord(repoRoot, commitSha, values) {
  const recordPath = ".delivery/runtime/records/" + commitSha + ".json";
  const record = executionRecord(values);
  record.evidence.recordPath = recordPath;
  const raw = JSON.stringify(record, null, 2) + "\n";
  await fs.mkdir(path.join(repoRoot, ".delivery", "runtime", "records"), { recursive: true });
  await fs.writeFile(path.join(repoRoot, recordPath), raw, { mode: 0o600 });
  return {
    recordPath,
    recordDigest: crypto.createHash("sha256").update(raw).digest("hex"),
    record,
  };
}

test("queryCommitEvidence reports missing then preserves an Android not_run receipt", async (t) => {
  const repoRoot = await createGitRepo(t);
  const commit = await commitAndroidFile(repoRoot, "Welcome.kt");

  const missing = await queryCommitEvidence({ repoRoot, commitSha: commit.commitSha });
  assert.deepEqual(
    { valid: missing.valid, state: missing.state, reason: missing.reason },
    { valid: false, state: "missing", reason: "MISSING_EVIDENCE_IN_LEDGER" }
  );

  const entry = await recordCommitEvidence({
    repoRoot,
    commitSha: commit.commitSha,
    verificationStatus: "not_run",
    notRunReason: "NO_PREPARED_RECEIPT",
    branch: "main",
    parentSha: commit.parentSha,
    treeSha: commit.treeSha,
    stagedFiles: [commit.stagedFile],
    usId: "33",
  });

  assert.equal(entry.schemaVersion, 2);
  assert.equal(entry.status, "not_run");
  assert.equal(entry.verificationStatus, "not_run");
  assert.equal(entry.notRunReason, "NO_PREPARED_RECEIPT");
  assert.equal(entry.usId, "33");
  assert.equal(entry.recordPath, null);
  assert.equal(entry.recordDigest, null);

  const result = await verifyCommitEvidence({ repoRoot, commitSha: commit.commitSha });
  assert.equal(result.valid, false);
  assert.equal(result.state, "not_run");
  assert.equal(result.reason, "NO_PREPARED_RECEIPT");
  assert.equal((await getCommitEvidence({ repoRoot, commitSha: commit.commitSha })).commitSha, commit.commitSha);
});

test("passed Android evidence is verified against Git identity and immutable record digest", async (t) => {
  const repoRoot = await createGitRepo(t);
  const commit = await commitAndroidFile(repoRoot, "Category.kt");
  const snapshotHash = "b".repeat(64);
  const runKey = "c".repeat(64);
  const policyHash = "d".repeat(64);
  const evidence = await writeExecutionRecord(repoRoot, commit.commitSha, {
    snapshotHash,
    runKey,
    policyHash,
  });

  await recordCommitEvidence({
    repoRoot,
    commitSha: commit.commitSha,
    verificationStatus: "passed",
    snapshotHash,
    runKey,
    recordPath: evidence.recordPath,
    recordDigest: evidence.recordDigest,
    branch: "main",
    parentSha: commit.parentSha,
    treeSha: commit.treeSha,
    stagedFiles: [commit.stagedFile],
    gateId: "A",
    policyHash,
    intent: "prepare_commit",
    usId: "33",
  });

  const verified = await queryCommitEvidence({ repoRoot, commitSha: commit.commitSha });
  assert.equal(verified.valid, true);
  assert.equal(verified.state, "verified");
  assert.equal(verified.record.status, "passed");

  const changed = { ...evidence.record, summary: { ...evidence.record.summary, durationMs: 99 } };
  await fs.writeFile(
    path.join(repoRoot, evidence.recordPath),
    JSON.stringify(changed, null, 2) + "\n",
    { mode: 0o600 }
  );
  const tampered = await queryCommitEvidence({ repoRoot, commitSha: commit.commitSha });
  assert.equal(tampered.valid, false);
  assert.equal(tampered.state, "corrupt");
  assert.equal(tampered.reason, "EVIDENCE_RECORD_CHANGED");
});

test("prepared Android evidence binds snapshot, policy, gate, and is single-use", async (t) => {
  const repoRoot = await createGitRepo(t);
  const commit = await commitAndroidFile(repoRoot, "Auth.kt");
  const snapshot = {
    branch: "main",
    headSha: commit.parentSha,
    stagedTreeSha: commit.treeSha,
    snapshotHash: "e".repeat(64),
    stagedFiles: [commit.stagedFile],
  };
  const policyHash = "f".repeat(64);
  const runKey = "1".repeat(64);
  const evidence = await writeExecutionRecord(repoRoot, "prepared", {
    snapshotHash: snapshot.snapshotHash,
    runKey,
    policyHash,
  });
  const inspection = { gate: { id: "A" }, policy: { hash: policyHash } };

  const prepared = await recordPreparedEvidence({
    repoRoot,
    snapshot,
    inspection,
    intent: "prepare_commit",
    usId: "33",
    scopeFiles: [commit.stagedFile],
    runKey,
    status: "passed",
    recordPath: evidence.recordPath,
  });

  assert.equal(prepared.schemaVersion, 2);
  assert.equal(prepared.usId, "33");
  assert.equal(prepared.gateId, "A");
  assert.equal((await getLastPreparedEvidence({ repoRoot })).runKey, runKey);

  const valid = await verifyPreparedEvidence({
    repoRoot,
    snapshot,
    inspection,
    intent: "prepare_commit",
  });
  assert.equal(valid.valid, true);
  assert.equal(valid.record.status, "passed");

  const wrongPolicy = await verifyPreparedEvidence({
    repoRoot,
    snapshot,
    inspection,
    intent: "prepare_commit",
    policyHash: "0".repeat(64),
  });
  assert.equal(wrongPolicy.valid, false);
  assert.equal(wrongPolicy.reason, "POLICY_MISMATCH");

  const consumed = await consumePreparedEvidence({ repoRoot, commitSha: commit.commitSha });
  assert.equal(consumed.consumedByCommitSha, commit.commitSha);
  const stale = await verifyPreparedEvidence({ repoRoot, snapshot, inspection, intent: "prepare_commit" });
  assert.equal(stale.valid, false);
  assert.equal(stale.reason, "STALE_PREPARED_EVIDENCE");
});

test("ledger paths are confined to ignored runtime and reject invalid commit identifiers", async (t) => {
  const repoRoot = await createGitRepo(t);
  assert.match(LEDGER_DIR, /^\.delivery\/runtime\//);
  assert.match(LEDGER_FILE, /^\.delivery\/runtime\//);
  await assert.rejects(
    () => recordCommitEvidence({ repoRoot, commitSha: "../outside", verificationStatus: "not_run" }),
    /Invalid commit SHA/
  );
});

