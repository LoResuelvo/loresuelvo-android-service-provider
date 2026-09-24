import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";
import { inspectClosureReadiness } from "../lib/closure-preflight.mjs";
import { verifyHeadDelivery } from "../lib/verify-head.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

async function createRepo(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-closure-preflight-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  execFileSync("git", ["init", "-b", "main"], { cwd: root, stdio: "ignore" });
  execFileSync("git", ["config", "user.name", "Delivery Tests"], { cwd: root });
  execFileSync("git", ["config", "user.email", "delivery-tests@example.com"], { cwd: root });
  execFileSync("git", ["config", "commit.gpgsign", "false"], { cwd: root });
  await fs.mkdir(path.join(root, ".delivery/schemas"), { recursive: true });
  for (const name of ["ci-inspection-result", "delivery-context", "execution-result", "inspection-result", "policy"]) {
    await fs.copyFile(path.join(ROOT, `.delivery/schemas/${name}.schema.json`), path.join(root, `.delivery/schemas/${name}.schema.json`));
  }
  await fs.copyFile(path.join(ROOT, ".delivery/policy.v1.json"), path.join(root, ".delivery/policy.v1.json"));
  await fs.writeFile(path.join(root, "README.md"), "# Fixture\n");
  execFileSync("git", ["add", "."], { cwd: root });
  execFileSync("git", ["commit", "-m", "chore: initialize fixture"], { cwd: root, stdio: "ignore" });
  return root;
}

async function commitFeature(root, name, message) {
  const feature = `app/src/test/resources/features/${name}.feature`;
  await fs.mkdir(path.dirname(path.join(root, feature)), { recursive: true });
  await fs.writeFile(path.join(root, feature), "Feature: Provider\n  Scenario: ready\n    Given provider is ready\n");
  execFileSync("git", ["add", feature], { cwd: root });
  execFileSync("git", ["commit", "-m", message], { cwd: root, stdio: "ignore" });
  const sha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  return { sha, feature };
}

const passingCheck = async ({ check, logPath }) => ({
  id: check.id,
  status: "passed",
  durationMs: 1,
  exitCode: 0,
  summaryLines: ["passed"],
  locations: [],
  logPath,
  diagnostic: null,
});

test("closure preflight finds missing baseline evidence before Gate D closure", async (t) => {
  const root = await createRepo(t);
  const baseline = await commitFeature(root, "baseline", "test[53]: approve provider scenarios");
  const current = await commitFeature(root, "current", "feat[53]: complete provider journey");
  const gate = await verifyHeadDelivery({ repoRoot: root, intent: "close_us", usId: "53", scopeFiles: [current.feature], executeCheck: passingCheck });
  assert.equal(gate.verified, true);

  const result = await inspectClosureReadiness({ repoRoot: root, usId: "53", requiredShas: [baseline.sha.slice(0, 8)] });
  assert.equal(result.status, "blocked");
  assert.equal(result.reason, "COMMIT_EVIDENCE_NOT_VERIFIED");
  assert.equal(result.commits.find((commit) => commit.sha === baseline.sha).state, "missing");
  assert.equal(result.commits.find((commit) => commit.sha === current.sha).state, "verified");
});

test("closure preflight accepts verified HEAD and rejects unrelated required SHAs", async (t) => {
  const root = await createRepo(t);
  const current = await commitFeature(root, "current", "feat[53]: complete provider journey");
  const gate = await verifyHeadDelivery({ repoRoot: root, intent: "close_us", usId: "53", scopeFiles: [current.feature], executeCheck: passingCheck });
  assert.equal(gate.verified, true);
  const ready = await inspectClosureReadiness({ repoRoot: root, usId: "53" });
  assert.equal(ready.status, "ready");
  assert.deepEqual(ready.commits.map((commit) => commit.sha), [current.sha]);
  const invalid = await inspectClosureReadiness({ repoRoot: root, usId: "53", requiredShas: ["f".repeat(40)] });
  assert.equal(invalid.status, "blocked");
  assert.equal(invalid.reason, "COMMIT_NOT_IN_HEAD_HISTORY");
});

test("closure preflight permits a new story without requiring unrelated HEAD evidence", async (t) => {
  const root = await createRepo(t);
  const baselineSha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  const result = await inspectClosureReadiness({ repoRoot: root, usId: "54" });
  assert.equal(result.status, "ready");
  assert.equal(result.reason, "NO_EXISTING_US_COMMITS");
  assert.deepEqual(result.commits, []);
  const required = await inspectClosureReadiness({ repoRoot: root, usId: "54", requiredShas: [baselineSha] });
  assert.equal(required.status, "blocked");
  assert.equal(required.commits[0].state, "missing");
  const ledgerFiles = await fs.readdir(path.join(root, ".delivery/runtime/ledger")).catch(() => []);
  assert.deepEqual(ledgerFiles, []);
});
