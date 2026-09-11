import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";
import {
  getHooksStatus,
  installHooks,
  runCommitMsgHook,
  runPostCommitHook,
  runPreCommitHook,
  runPrePushHook,
  validateCommitMessage,
} from "../lib/git-hooks.mjs";
import {
  getCommitEvidence,
  getLastPreparedEvidence,
  getRepairAuthorization,
  recordPreparedEvidence,
  saveRepairAuthorization,
} from "../lib/delivery-ledger.mjs";
import { captureGitSnapshot } from "../lib/git-snapshot.mjs";
import { loadDeliveryContext, saveDeliveryContext } from "../lib/delivery-context.mjs";
import { MockCiProvider } from "../lib/ci-provider.mjs";

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

async function createTempRepo(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-hooks-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  execFileSync("git", ["init", "-b", "main"], { cwd: root, stdio: "ignore" });
  execFileSync("git", ["config", "user.name", "Delivery Tests"], { cwd: root });
  execFileSync("git", ["config", "user.email", "delivery-tests@example.com"], { cwd: root });
  execFileSync("git", ["config", "commit.gpgsign", "false"], { cwd: root });
  await fs.mkdir(path.join(root, ".delivery/runtime"), { recursive: true });
  await fs.mkdir(path.join(root, ".delivery/schemas"), { recursive: true });
  await fs.copyFile(
    path.join(sourceRoot, ".delivery", "schemas", "delivery-context.schema.json"),
    path.join(root, ".delivery", "schemas", "delivery-context.schema.json"),
  );
  await fs.copyFile(
    path.join(sourceRoot, ".delivery", "schemas", "ci-inspection-result.schema.json"),
    path.join(root, ".delivery", "schemas", "ci-inspection-result.schema.json"),
  );
  await fs.copyFile(
    path.join(sourceRoot, ".delivery", "schemas", "policy.schema.json"),
    path.join(root, ".delivery", "schemas", "policy.schema.json"),
  );
  await fs.copyFile(
    path.join(sourceRoot, ".delivery", "schemas", "execution-result.schema.json"),
    path.join(root, ".delivery", "schemas", "execution-result.schema.json"),
  );
  await fs.copyFile(path.join(sourceRoot, ".delivery", "policy.v1.json"), path.join(root, ".delivery", "policy.v1.json"));
  await fs.mkdir(path.join(root, ".githooks"), { recursive: true });
  await fs.writeFile(path.join(root, "README.md"), "# Fixture\n", "utf8");
  execFileSync("git", ["add", "README.md"], { cwd: root });
  execFileSync("git", ["commit", "-m", "chore: initialize delivery fixture"], {
    cwd: root,
    stdio: "ignore",
  });
  return root;
}

async function writePreparedRecord(root, { snapshotHash, runKey, policyHash }) {
  const recordPath = `.delivery/runtime/records/${runKey}.json`;
  const record = {
    schemaVersion: 1,
    status: "passed",
    snapshotHash,
    runKey,
    cached: false,
    policy: { version: 1, hash: policyHash },
    gate: {
      id: "A",
      reasonCodes: ["ANDROID_TEST_EVIDENCE"],
      checkIds: ["jvm_test_dev"],
      parameters: {},
      postPushChecks: [],
    },
    summary: { passed: 1, failed: 0, skipped: 0, durationMs: 1 },
    checks: [{ id: "jvm_test_dev", status: "passed", durationMs: 1 }],
    diagnostics: [],
    evidence: { recordPath },
  };
  const raw = `${JSON.stringify(record, null, 2)}\n`;
  await fs.mkdir(path.dirname(path.join(root, recordPath)), { recursive: true });
  await fs.writeFile(path.join(root, recordPath), raw, { mode: 0o600 });
  return { recordPath, recordDigest: crypto.createHash("sha256").update(raw).digest("hex") };
}

test("commit governance accepts the Android types and numeric [33] only", () => {
  for (const type of ["feat", "fix", "refactor", "test", "chore", "docs", "build", "ci", "perf", "style"]) {
    assert.equal(validateCommitMessage(`${type}[33]: update delivery contract`).valid, true, type);
  }
  assert.equal(validateCommitMessage("revert[33]: restore prior behavior").reason, "INVALID_TYPE");
  assert.equal(validateCommitMessage("feat[US-33]: update delivery contract").reason, "INVALID_US_ID");
  assert.equal(validateCommitMessage("feat[33.1]: update delivery contract").reason, "INVALID_US_ID");
  assert.equal(validateCommitMessage("feat(ui): update delivery contract").reason, "PAREN_SCOPE_FORBIDDEN");
  assert.equal(validateCommitMessage("feat(agent): update delivery contract").reason, "AGENT_SCOPE_FORBIDDEN");
  assert.equal(validateCommitMessage("feat[33]: ").reason, "EMPTY_DESCRIPTION");
});

test("hooks install configures .githooks and reports each executable hook", async (t) => {
  const root = await createTempRepo(t);
  for (const name of ["pre-commit", "commit-msg", "post-commit", "pre-push"]) {
    await fs.writeFile(path.join(root, ".githooks", name), "#!/usr/bin/env sh\nexit 0\n", "utf8");
  }
  assert.equal((await getHooksStatus({ repoRoot: root })).configured, false);
  const installed = await installHooks({ repoRoot: root });
  assert.deepEqual(installed, { installed: true, hooksPath: ".githooks" });
  const status = await getHooksStatus({ repoRoot: root });
  assert.equal(status.configured, true);
  assert.equal(status.configuredPath, ".githooks");
  for (const hook of Object.values(status.hooks)) {
    assert.deepEqual(hook, { exists: true, executable: true });
  }
});

test("pre-commit remains advisory even when evidence is required", async (t) => {
  const root = await createTempRepo(t);
  const previous = process.env.DELIVERY_REQUIRE_EVIDENCE;
  delete process.env.DELIVERY_REQUIRE_EVIDENCE;
  try {
    const shadow = await runPreCommitHook({ repoRoot: root });
    assert.equal(shadow.passed, true);
    assert.equal(shadow.verified, false);
    assert.match(shadow.warning, /not_run/);

    process.env.DELIVERY_REQUIRE_EVIDENCE = "1";
    const strict = await runPreCommitHook({ repoRoot: root });
    assert.equal(strict.passed, true);
    assert.equal(strict.advisory, true);
    assert.equal(strict.reason, "MISSING_PREPARED_EVIDENCE");
  } finally {
    if (previous === undefined) delete process.env.DELIVERY_REQUIRE_EVIDENCE;
    else process.env.DELIVERY_REQUIRE_EVIDENCE = previous;
  }
});

test("commit-msg validates a file and rejects the legacy [US-33] spelling", async (t) => {
  const root = await createTempRepo(t);
  const validPath = path.join(root, "valid-message.txt");
  const invalidPath = path.join(root, "invalid-message.txt");
  await fs.writeFile(validPath, "perf[33]: optimize delivery checks\n", "utf8");
  await fs.writeFile(invalidPath, "feat[US-33]: add provider workflow\n", "utf8");
  assert.equal((await runCommitMsgHook({ repoRoot: root, messageFilePath: validPath })).passed, true);
  const invalid = await runCommitMsgHook({ repoRoot: root, messageFilePath: invalidPath });
  assert.equal(invalid.passed, false);
  assert.equal(invalid.reason, "INVALID_US_ID");

  const fractionalPath = path.join(root, "fractional-message.txt");
  await fs.writeFile(fractionalPath, "feat[33.1]: add provider workflow\n", "utf8");
  const fractional = await runCommitMsgHook({ repoRoot: root, messageFilePath: fractionalPath });
  assert.equal(fractional.passed, false);
  assert.equal(fractional.reason, "INVALID_US_ID");

  const staleSnapshot = await captureGitSnapshot({ cwd: root });
  await saveDeliveryContext({ repoRoot: root, snapshot: staleSnapshot, intent: "close_us", usId: "99" });
  const staleContext = await runCommitMsgHook({ repoRoot: root, messageFilePath: validPath });
  assert.equal(staleContext.passed, true);
  assert.equal(Object.hasOwn(staleContext, "contextValidation"), false);
});

test("post-commit remains advisory and does not mutate evidence", async (t) => {
  const root = await createTempRepo(t);
  await fs.writeFile(path.join(root, "manual.txt"), "human change\n", "utf8");
  execFileSync("git", ["add", "manual.txt"], { cwd: root });
  execFileSync("git", ["commit", "-m", "docs[33]: record manual change"], {
    cwd: root,
    stdio: "ignore",
  });
  const sha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  const result = await runPostCommitHook({ repoRoot: root });
  assert.equal(result.recorded, false);
  assert.equal(result.advisory, true);
  assert.equal(result.commitSha, sha);
  const entry = await getCommitEvidence({ repoRoot: root, commitSha: sha });
  assert.equal(entry, null);
});

test("post-commit binds and consumes an exact prepared receipt", async (t) => {
  const root = await createTempRepo(t);
  await fs.writeFile(path.join(root, "prepared.txt"), "prepared\n", "utf8");
  execFileSync("git", ["add", "prepared.txt"], { cwd: root });
  const snapshot = await captureGitSnapshot({ cwd: root });
  const policyHash = "a".repeat(64);
  const runKey = "b".repeat(64);
  const evidence = await writePreparedRecord(root, { snapshotHash: snapshot.snapshotHash, runKey, policyHash });
  await recordPreparedEvidence({
    repoRoot: root,
    snapshot,
    inspection: { gate: { id: "A" }, policy: { hash: policyHash } },
    runKey,
    status: "passed",
    recordPath: evidence.recordPath,
    usId: "35",
  });

  execFileSync("git", ["commit", "-m", "feat[35]: bind prepared receipt"], { cwd: root, stdio: "ignore" });
  const post = await runPostCommitHook({ repoRoot: root });
  assert.equal(post.recorded, true, JSON.stringify(post));
  assert.equal(post.verificationStatus, "passed");
  const entry = await getCommitEvidence({ repoRoot: root, commitSha: post.commitSha });
  assert.equal(entry.verificationStatus, "passed");
  assert.equal(entry.parentSha, snapshot.headSha);
  assert.deepEqual(entry.stagedFiles, ["prepared.txt"]);
  assert.equal((await getLastPreparedEvidence({ repoRoot: root })).consumedByCommitSha, post.commitSha);
});

test("post-commit leaves a created commit accepted when prepared evidence is corrupt", async (t) => {
  const root = await createTempRepo(t);
  await fs.writeFile(path.join(root, ".delivery/runtime/last-prepared.json"), "not-json\n", { mode: 0o600 });
  await fs.writeFile(path.join(root, "corrupt.txt"), "corrupt\n", "utf8");
  execFileSync("git", ["add", "corrupt.txt"], { cwd: root });
  execFileSync("git", ["commit", "-m", "docs[35]: tolerate corrupt receipt"], { cwd: root, stdio: "ignore" });
  const post = await runPostCommitHook({ repoRoot: root });
  assert.equal(post.recorded, false);
  assert.equal(post.advisory, true);
  assert.equal(post.reason, "MISSING_PREPARED_EVIDENCE");
  assert.ok(post.commitSha);
});

test("manual repair context remains advisory and unconsumed for one push", async (t) => {
  const root = await createTempRepo(t);
  const remoteDir = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-remote-"));
  t.after(() => fs.rm(remoteDir, { recursive: true, force: true }));
  execFileSync("git", ["init", "--bare", "-b", "main"], { cwd: remoteDir });
  execFileSync("git", ["remote", "add", "origin", remoteDir], { cwd: root });
  execFileSync("git", ["push", "-u", "origin", "main"], { cwd: root, stdio: "ignore" });

  await fs.writeFile(path.join(root, "broken.txt"), "broken\n", "utf8");
  execFileSync("git", ["add", "broken.txt"], { cwd: root });
  execFileSync("git", ["commit", "-m", "fix: introduce failure"], { cwd: root, stdio: "ignore" });
  const failedPost = await runPostCommitHook({ repoRoot: root });
  assert.equal(failedPost.recorded, false);
  assert.equal(failedPost.advisory, true);
  execFileSync("git", ["push", "origin", "main"], { cwd: root, stdio: "ignore" });

  const mockCi = new MockCiProvider();
  mockCi.setFixture(failedPost.commitSha, { status: "failed" });

  await fs.writeFile(path.join(root, "broken.txt"), "fixed\n", "utf8");
  execFileSync("git", ["add", "broken.txt"], { cwd: root });
  const snapshot = await captureGitSnapshot({ cwd: root });
  await saveDeliveryContext({
    repoRoot: root,
    snapshot,
    intent: "repair_ci",
    repairsSha: failedPost.commitSha,
  });

  execFileSync("git", ["commit", "-m", "fix: repair failed commit"], { cwd: root, stdio: "ignore" });
  const repairPost = await runPostCommitHook({ repoRoot: root });
  assert.equal(repairPost.recorded, false);
  assert.equal(repairPost.advisory, true);
  assert.equal((await loadDeliveryContext({ repoRoot: root })).consumed, false);

  const pushLine = `refs/heads/main ${repairPost.commitSha} refs/heads/main ${failedPost.commitSha}`;
  const previousStrict = process.env.DELIVERY_REQUIRE_EVIDENCE;
  process.env.DELIVERY_REQUIRE_EVIDENCE = "1";
  try {
    const strictPush = await runPrePushHook({ repoRoot: root, stdinLines: [pushLine], ciProvider: mockCi });
    assert.equal(strictPush.passed, true);
    assert.equal(strictPush.advisory, true);
  } finally {
    if (previousStrict === undefined) delete process.env.DELIVERY_REQUIRE_EVIDENCE;
    else process.env.DELIVERY_REQUIRE_EVIDENCE = previousStrict;
  }

  const humanPush = await runPrePushHook({ repoRoot: root, stdinLines: [pushLine], ciProvider: mockCi });
  assert.equal(humanPush.passed, true, JSON.stringify(humanPush));
});

test("manual repair context is not retained when the staged tree changes", async (t) => {
  const root = await createTempRepo(t);

  await fs.writeFile(path.join(root, "repair.txt"), "first\n", "utf8");
  execFileSync("git", ["add", "repair.txt"], { cwd: root });
  const snapshot = await captureGitSnapshot({ cwd: root });
  await saveDeliveryContext({
    repoRoot: root,
    snapshot,
    intent: "repair_ci",
    repairsSha: snapshot.headSha,
  });

  await fs.writeFile(path.join(root, "extra.txt"), "changed after context\n", "utf8");
  execFileSync("git", ["add", "extra.txt"], { cwd: root });
  execFileSync("git", ["commit", "-m", "fix: changed repair snapshot"], { cwd: root, stdio: "ignore" });
  const post = await runPostCommitHook({ repoRoot: root });

  assert.equal(post.recorded, false);
  assert.equal(post.advisory, true);
  assert.equal((await loadDeliveryContext({ repoRoot: root })).consumed, false);
});

test("pre-push ignores delivery runtime bypass state and remains advisory", async (t) => {
  const root = await createTempRepo(t);
  const previous = process.env.DELIVERY_SKIP_CI_CHECK;
  process.env.DELIVERY_SKIP_CI_CHECK = "1";
  try {
    const result = await runPrePushHook({ repoRoot: root, stdinLines: [] });
    assert.equal(result.passed, true);
    assert.equal(result.advisory, true);
  } finally {
    if (previous === undefined) delete process.env.DELIVERY_SKIP_CI_CHECK;
    else process.env.DELIVERY_SKIP_CI_CHECK = previous;
  }
});

test("remote-rejected pushes do not consume repair authorization through pre-push", async (t) => {
  const root = await createTempRepo(t);
  const remoteDir = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-rejected-remote-"));
  t.after(() => fs.rm(remoteDir, { recursive: true, force: true }));
  execFileSync("git", ["init", "--bare", "-b", "main"], { cwd: remoteDir, stdio: "ignore" });
  execFileSync("git", ["remote", "add", "origin", remoteDir], { cwd: root });
  execFileSync("git", ["push", "-u", "origin", "main"], { cwd: root, stdio: "ignore" });
  const targetSha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();

  await fs.writeFile(path.join(root, "repair.txt"), "repair\n", "utf8");
  execFileSync("git", ["add", "repair.txt"], { cwd: root });
  execFileSync("git", ["commit", "-m", "fix[35]: rejected repair"], { cwd: root, stdio: "ignore" });
  const repairSha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  await saveRepairAuthorization({
    repoRoot: root,
    authorization: { targetSha, commitSha: repairSha, state: "bound_to_commit", attemptCount: 0, snapshotHash: "b".repeat(64) },
  });
  const beforePush = await getRepairAuthorization({ repoRoot: root, targetSha });

  const prePush = await runPrePushHook({
    repoRoot: root,
    stdinLines: [`refs/heads/main ${repairSha} refs/heads/main ${targetSha}`],
  });
  assert.equal(prePush.passed, true);
  assert.equal(prePush.advisory, true);
  assert.deepEqual(await getRepairAuthorization({ repoRoot: root, targetSha }), beforePush);

  const rejectHook = path.join(remoteDir, "hooks", "pre-receive");
  await fs.writeFile(rejectHook, "#!/bin/sh\nexit 1\n", "utf8");
  await fs.chmod(rejectHook, 0o755);
  assert.throws(() => execFileSync("git", ["push", "origin", "main"], { cwd: root, stdio: "ignore" }));
  const authorization = await getRepairAuthorization({ repoRoot: root, targetSha });
  assert.deepEqual(authorization, beforePush);
});
