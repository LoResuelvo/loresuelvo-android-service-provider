import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import {
  captureGitSnapshot,
  extractAllUsIds,
  extractUsId,
  parsePorcelainStatus,
} from "../lib/git-snapshot.mjs";

async function createGitRepo(t) {
  const repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), "android-git-snapshot-"));
  t.after(() => fs.rm(repoRoot, { recursive: true, force: true }));
  execFileSync("git", ["init", "-b", "main"], { cwd: repoRoot });
  execFileSync("git", ["config", "user.name", "Delivery Test"], { cwd: repoRoot });
  execFileSync("git", ["config", "user.email", "delivery@example.test"], { cwd: repoRoot });
  execFileSync("git", ["config", "commit.gpgsign", "false"], { cwd: repoRoot });
  await fs.writeFile(path.join(repoRoot, "README.md"), "# Android provider\n", "utf8");
  execFileSync("git", ["add", "README.md"], { cwd: repoRoot });
  execFileSync("git", ["commit", "-m", "chore: initialize Android provider"], { cwd: repoRoot });
  return repoRoot;
}

test("extractUsId accepts integer work-item ids and rejects dotted or legacy forms", () => {
  assert.equal(extractUsId("feat[33]: add provider delivery"), "33");
  assert.equal(extractUsId("fix[33.1]: repair provider test"), null);
  assert.equal(extractUsId("refactor[1.2.3]: simplify adapter"), null);
  assert.equal(extractUsId("feat[US-33]: legacy spelling"), null);
  assert.equal(extractUsId("docs: update Android README"), null);
  assert.equal(extractUsId(null), null);
});

test("extractAllUsIds returns unique numeric ids in message order", () => {
  assert.deepEqual(extractAllUsIds("feat[33]: merge [33] and [34]"), ["33", "34"]);
  assert.deepEqual(extractAllUsIds("feat[US-33]: legacy"), []);
  assert.deepEqual(extractAllUsIds("chore: no work item"), []);
});

test("parsePorcelainStatus classifies Android staged, unstaged, and untracked files", () => {
  const porcelain = Buffer.from([
    "M  app/src/main/java/com/loresuelvo/serviceprovider/Main.kt",
    " M app/src/main/res/values/strings.xml",
    "MM domain/category/Category.kt",
    "A  tools/delivery-mcp/test/android-contract.test.mjs",
    "?? app/src/test/resources/features/welcome.feature",
    "?? .delivery/runtime/logs/ignored.log",
  ].join("\n"));

  const result = parsePorcelainStatus(porcelain);
  assert.deepEqual(result.staged.map((entry) => entry.file), [
    "app/src/main/java/com/loresuelvo/serviceprovider/Main.kt",
    "domain/category/Category.kt",
    "tools/delivery-mcp/test/android-contract.test.mjs",
  ]);
  assert.deepEqual(result.unstaged.map((entry) => entry.file), [
    "app/src/main/res/values/strings.xml",
    "domain/category/Category.kt",
  ]);
  assert.deepEqual(result.untracked, ["app/src/test/resources/features/welcome.feature"]);
});

test("parsePorcelainStatus supports NUL-delimited names with spaces", () => {
  const entries = [
    "M  app/src/main/java/com/example/Provider Screen.kt",
    " M app/src/test/resources/features/provider welcome.feature",
    "?? tools/delivery-mcp/test/new test.test.mjs",
  ];
  const buffer = Buffer.concat(entries.map((entry) => Buffer.concat([
    Buffer.from(entry, "utf8"),
    Buffer.from([0]),
  ])));
  const result = parsePorcelainStatus(buffer);
  assert.deepEqual(result.staged.map((entry) => entry.file), ["app/src/main/java/com/example/Provider Screen.kt"]);
  assert.deepEqual(result.unstaged.map((entry) => entry.file), ["app/src/test/resources/features/provider welcome.feature"]);
  assert.deepEqual(result.untracked, ["tools/delivery-mcp/test/new test.test.mjs"]);
});

test("captureGitSnapshot binds the staged Android tree and proposed work item", async (t) => {
  const repoRoot = await createGitRepo(t);
  const sourcePath = path.join(repoRoot, "app/src/main/java/com/loresuelvo/serviceprovider/Welcome.kt");
  await fs.mkdir(path.dirname(sourcePath), { recursive: true });
  await fs.writeFile(sourcePath, "package com.loresuelvo.serviceprovider\nclass Welcome\n", "utf8");
  execFileSync("git", ["add", "app/src/main/java/com/loresuelvo/serviceprovider/Welcome.kt"], { cwd: repoRoot });

  const snapshot = await captureGitSnapshot({
    cwd: repoRoot,
    proposedCommitMessage: "feat[33]: add provider welcome",
  });

  assert.equal(snapshot.repoRoot, path.resolve(repoRoot));
  assert.equal(snapshot.branch, "main");
  assert.equal(snapshot.stagedCount, 1);
  assert.deepEqual(snapshot.stagedFiles, ["app/src/main/java/com/loresuelvo/serviceprovider/Welcome.kt"]);
  assert.equal(snapshot.proposedUsId, "33");
  assert.equal(snapshot.usId, "33");
  assert.equal(snapshot.isContradictoryUsId, false);
  assert.match(snapshot.headSha, /^[a-f0-9]{40}$/);
  assert.match(snapshot.stagedTreeSha, /^[a-f0-9]{40}$/);
  assert.match(snapshot.snapshotHash, /^[a-f0-9]{64}$/);
  assert.equal(snapshot.unstagedConflicts.length, 0);
  assert.equal(snapshot.unrelatedUnstaged.length, 0);
  assert.equal(snapshot.untracked.length, 0);
  assert.equal(snapshot.cacheable, true);
});
