import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
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
import { getCommitEvidence } from "../lib/delivery-ledger.mjs";

async function createTempRepo(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-hooks-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  execFileSync("git", ["init", "-b", "main"], { cwd: root, stdio: "ignore" });
  execFileSync("git", ["config", "user.name", "Delivery Tests"], { cwd: root });
  execFileSync("git", ["config", "user.email", "delivery-tests@example.com"], { cwd: root });
  execFileSync("git", ["config", "commit.gpgsign", "false"], { cwd: root });
  await fs.mkdir(path.join(root, ".delivery/runtime"), { recursive: true });
  await fs.mkdir(path.join(root, ".githooks"), { recursive: true });
  await fs.writeFile(path.join(root, "README.md"), "# Fixture\n", "utf8");
  execFileSync("git", ["add", "README.md"], { cwd: root });
  execFileSync("git", ["commit", "-m", "chore: initialize delivery fixture"], {
    cwd: root,
    stdio: "ignore",
  });
  return root;
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

test("pre-commit stays lightweight in shadow mode and blocks only when evidence is required", async (t) => {
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
    assert.equal(strict.passed, false);
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
});

test("post-commit records an unverified human commit without consuming a receipt", async (t) => {
  const root = await createTempRepo(t);
  await fs.writeFile(path.join(root, "manual.txt"), "human change\n", "utf8");
  execFileSync("git", ["add", "manual.txt"], { cwd: root });
  execFileSync("git", ["commit", "-m", "docs[33]: record manual change"], {
    cwd: root,
    stdio: "ignore",
  });
  const sha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  const result = await runPostCommitHook({ repoRoot: root });
  assert.equal(result.recorded, true);
  assert.equal(result.commitSha, sha);
  assert.equal(result.verificationStatus, "not_run");
  const entry = await getCommitEvidence({ repoRoot: root, commitSha: sha });
  assert.equal(entry.verificationStatus, "not_run");
  assert.equal(entry.usId, "33");
});

test("pre-push rejects the deprecated CI bypass before inspecting Git state", async (t) => {
  const root = await createTempRepo(t);
  const previous = process.env.DELIVERY_SKIP_CI_CHECK;
  process.env.DELIVERY_SKIP_CI_CHECK = "1";
  try {
    const result = await runPrePushHook({ repoRoot: root, stdinLines: [] });
    assert.equal(result.passed, false);
    assert.equal(result.reason, "DEPRECATED_CI_BYPASS_REJECTED");
  } finally {
    if (previous === undefined) delete process.env.DELIVERY_SKIP_CI_CHECK;
    else process.env.DELIVERY_SKIP_CI_CHECK = previous;
  }
});
