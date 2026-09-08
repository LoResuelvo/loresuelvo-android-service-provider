import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { assertSafeRepoPath, findRepoRoot } from "../lib/repo-root.mjs";

test("findRepoRoot accepts an explicit Android repository root", async (t) => {
  const repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), "android-repo-root-"));
  t.after(() => fs.rm(repoRoot, { recursive: true, force: true }));

  assert.equal(findRepoRoot(repoRoot), path.resolve(repoRoot));
});

test("findRepoRoot rejects explicit traversal before resolving it", () => {
  assert.throws(
    () => findRepoRoot("tmp/../outside"),
    /path traversal rejected/
  );
  assert.throws(() => findRepoRoot(42), /must be a string/);
});

test("assertSafeRepoPath accepts Android files and normalizes separators", async (t) => {
  const repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), "android-safe-path-"));
  t.after(() => fs.rm(repoRoot, { recursive: true, force: true }));

  assert.equal(
    assertSafeRepoPath(repoRoot, "./app/src/main/java/com/loresuelvo/serviceprovider/Main.kt"),
    "app/src/main/java/com/loresuelvo/serviceprovider/Main.kt"
  );
  assert.equal(
    assertSafeRepoPath(repoRoot, "app\\src\\test\\resources\\features\\welcome.feature"),
    "app/src/test/resources/features/welcome.feature"
  );
  assert.equal(
    assertSafeRepoPath(repoRoot, ".delivery/runtime/logs/run.log"),
    ".delivery/runtime/logs/run.log"
  );
});

test("assertSafeRepoPath rejects empty, traversal, and outside paths", async (t) => {
  const repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), "android-safe-path-"));
  t.after(() => fs.rm(repoRoot, { recursive: true, force: true }));

  assert.throws(() => assertSafeRepoPath(repoRoot, ""), /cannot be empty/);
  assert.throws(() => assertSafeRepoPath(repoRoot, "../secrets.txt"), /path traversal/);
  assert.throws(() => assertSafeRepoPath(repoRoot, "app/../../secrets.txt"), /path traversal/);
  assert.throws(() => assertSafeRepoPath(repoRoot, path.resolve(repoRoot, "..", "secrets.txt")), /resolves outside repository/);
  assert.throws(() => assertSafeRepoPath(repoRoot, null), /cannot be empty/);
});
