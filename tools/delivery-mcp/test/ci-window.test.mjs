import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";
import { evaluateCiWindow, recordCommitEvidence } from "../lib/delivery-ledger.mjs";
import { MockCiProvider } from "../lib/ci-provider.mjs";
import { runPostCommitHook, runPrePushHook } from "../lib/git-hooks.mjs";

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

async function createGitLedgerRoot(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-git-window-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  execFileSync("git", ["init", "-b", "main"], { cwd: root, stdio: "ignore" });
  execFileSync("git", ["config", "user.name", "Delivery Tests"], { cwd: root });
  execFileSync("git", ["config", "user.email", "delivery-tests@example.com"], { cwd: root });
  execFileSync("git", ["config", "commit.gpgsign", "false"], { cwd: root });
  await fs.mkdir(path.join(root, ".delivery/runtime/ledger"), { recursive: true });
  await fs.mkdir(path.join(root, ".delivery/runtime/locks"), { recursive: true });
  await fs.mkdir(path.join(root, ".delivery/runtime/ci"), { recursive: true });
  await fs.mkdir(path.join(root, ".delivery/schemas"), { recursive: true });
  await fs.copyFile(
    path.join(ROOT, ".delivery", "schemas", "ci-inspection-result.schema.json"),
    path.join(root, ".delivery", "schemas", "ci-inspection-result.schema.json"),
  );
  await fs.copyFile(
    path.join(ROOT, ".delivery", "schemas", "policy.schema.json"),
    path.join(root, ".delivery", "schemas", "policy.schema.json"),
  );
  await fs.copyFile(path.join(ROOT, ".delivery", "policy.v1.json"), path.join(root, ".delivery", "policy.v1.json"));
  await fs.writeFile(path.join(root, "README.md"), "# Initial\n", "utf8");
  execFileSync("git", ["add", "README.md"], { cwd: root });
  execFileSync("git", ["commit", "-m", "chore: initialize CI window fixture"], {
    cwd: root,
    stdio: "ignore",
  });
  return root;
}

async function commitWindowFixture(root, fileName, contents, message) {
  await fs.writeFile(path.join(root, fileName), contents, "utf8");
  execFileSync("git", ["add", fileName], { cwd: root });
  execFileSync("git", ["commit", "-m", message], { cwd: root, stdio: "ignore" });
  const post = await runPostCommitHook({ repoRoot: root });
  await recordCommitEvidence({
    repoRoot: root,
    commitSha: post.commitSha,
    verificationStatus: "not_run",
    notRunReason: "human_commit_no_receipt",
    branch: "main",
    parentSha: null,
    treeSha: execFileSync("git", ["rev-parse", "HEAD^{tree}"], { cwd: root, encoding: "utf8" }).trim(),
    stagedFiles: [fileName],
  });
  return post;
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

test("CI window ignores ledger entries that are no longer reachable from the history anchor", async (t) => {
  const root = await createGitLedgerRoot(t);
  const remoteDir = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-window-remote-"));
  t.after(() => fs.rm(remoteDir, { recursive: true, force: true }));
  execFileSync("git", ["init", "--bare", "-b", "main"], { cwd: remoteDir });
  execFileSync("git", ["remote", "add", "origin", remoteDir], { cwd: root });
  execFileSync("git", ["push", "-u", "origin", "main"], { cwd: root, stdio: "ignore" });

  const baseSha = execFileSync("git", ["rev-parse", "HEAD"], {
    cwd: root,
    encoding: "utf8",
  }).trim();
  const provider = new MockCiProvider();

  for (let index = 1; index <= 4; index += 1) {
    const discarded = await commitWindowFixture(
      root,
      `discarded-${index}.txt`,
      `${index}\n`,
      `chore: discarded commit ${index}`,
    );
    provider.setFixture(discarded.commitSha, { status: "not_found" });
    execFileSync("git", ["reset", "--hard", baseSha], { cwd: root, stdio: "ignore" });
  }

  const current = await commitWindowFixture(root, "current.txt", "current\n", "chore: current commit");
  provider.setFixture(current.commitSha, { status: "not_found" });

  const window = await evaluateCiWindow({
    repoRoot: root,
    policy,
    ciProvider: provider,
    historyHeadSha: current.commitSha,
  });
  assert.equal(window.allowed, true, JSON.stringify(window));
  assert.equal(window.pendingCount, 1);

  const push = await runPrePushHook({
    repoRoot: root,
    stdinLines: [`refs/heads/main ${current.commitSha} refs/heads/main ${baseSha}`],
    ciProvider: provider,
  });
  assert.equal(push.passed, true, JSON.stringify(push));
});

test("a cancelled CI run is superseded only by a green git descendant", async (t) => {
  const root = await createGitLedgerRoot(t);
  const provider = new MockCiProvider();

  const cancelled = await commitWindowFixture(root, "cancelled.txt", "cancelled\n", "chore: cancelled CI run");
  provider.setFixture(cancelled.commitSha, { status: "cancelled" });

  const green = await commitWindowFixture(root, "green.txt", "green\n", "chore: green descendant");
  provider.setFixture(green.commitSha, { status: "passed" });

  const current = await commitWindowFixture(root, "current.txt", "current\n", "chore: current delivery");
  provider.setFixture(current.commitSha, { status: "not_found" });

  const result = await evaluateCiWindow({
    repoRoot: root,
    policy,
    ciProvider: provider,
    historyHeadSha: current.commitSha,
  });

  assert.equal(result.allowed, true, JSON.stringify(result));
  assert.equal(result.pendingCount, 1);
  assert.equal(
    result.activeIncidents.allIncidents.find((incident) => incident.failedSha === cancelled.commitSha).status,
    "superseded",
  );
});

test("a cancelled CI run without a green descendant remains an active repair incident", async (t) => {
  const root = await createGitLedgerRoot(t);
  const provider = new MockCiProvider();
  const cancelled = await commitWindowFixture(root, "cancelled.txt", "cancelled\n", "chore: cancelled CI run");
  provider.setFixture(cancelled.commitSha, { status: "cancelled" });

  const result = await evaluateCiWindow({
    repoRoot: root,
    policy,
    ciProvider: provider,
    historyHeadSha: cancelled.commitSha,
  });

  assert.equal(result.allowed, false);
  assert.equal(result.reason, "PRIOR_COMMIT_CI_FAILED");
  assert.equal(result.failedSha, cancelled.commitSha);
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
