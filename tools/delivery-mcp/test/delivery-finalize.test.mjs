import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";
import { finalizeDelivery, verifyHeadDelivery } from "../lib/delivery-finalize.mjs";
import { MockCiProvider } from "../lib/ci-provider.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

async function createTempRepo(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-delivery-finalize-"));
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
  await fs.copyFile(path.join(ROOT, ".gitignore"), path.join(root, ".gitignore"));
  await fs.writeFile(path.join(root, "README.md"), "# Android service provider\n", "utf8");
  execFileSync("git", ["add", "."], { cwd: root });
  execFileSync("git", ["commit", "-m", "chore: initialize Android fixture"], { cwd: root, stdio: "ignore" });
  return root;
}

function headSha(repoRoot) {
  return execFileSync("git", ["rev-parse", "HEAD"], { cwd: repoRoot, encoding: "utf8" }).trim();
}

async function commitFeature(repoRoot, relativePath, content, message) {
  await fs.mkdir(path.dirname(path.join(repoRoot, relativePath)), { recursive: true });
  await fs.writeFile(path.join(repoRoot, relativePath), content, "utf8");
  execFileSync("git", ["add", relativePath], { cwd: repoRoot });
  execFileSync("git", ["commit", "-m", message], { cwd: repoRoot, stdio: "ignore" });
  return headSha(repoRoot);
}

async function commitPolicyWithGateDChecks(repoRoot, checkIds) {
  const policyPath = path.join(repoRoot, ".delivery", "policy.v1.json");
  const policy = JSON.parse(await fs.readFile(policyPath, "utf8"));
  policy.gates.D.checkIds = checkIds;
  await fs.writeFile(policyPath, `${JSON.stringify(policy, null, 2)}\n`, "utf8");
  execFileSync("git", ["add", ".delivery/policy.v1.json"], { cwd: repoRoot });
  execFileSync("git", ["commit", "-m", "chore: configure Gate D fixture"], {
    cwd: repoRoot,
    stdio: "ignore",
  });
}

const passingCheck = async ({ check, logPath }) => ({
  id: check.id,
  status: "passed",
  durationMs: 1,
  exitCode: 0,
  summaryLines: [`${check.id} passed`],
  locations: [],
  logPath,
  diagnostic: null,
});

async function finalizeLocally(options) {
  return finalizeDelivery({ unpushedCommitsResolver: () => [], ...options });
}

test("finalizeDelivery requires exact Gate D evidence for HEAD", async (t) => {
  const repoRoot = await createTempRepo(t);
  const result = await finalizeLocally({ repoRoot, intent: "close_us", usId: "33" });
  assert.equal(result.finalized, false);
  assert.equal(result.status, "blocked");
  assert.equal(result.reason, "INVALID_HEAD_EVIDENCE");
});

test("verifyHeadDelivery records Gate D without creating a commit, then finalization closes [33]", async (t) => {
  const repoRoot = await createTempRepo(t);
  const feature = "app/src/test/resources/features/provider.feature";
  const sha = await commitFeature(
    repoRoot,
    feature,
    "Feature: Provider\n  Scenario: provider is ready\n    Given the service provider is authenticated\n",
    "test[33]: complete provider scenario",
  );

  const verified = await verifyHeadDelivery({
    repoRoot,
    intent: "close_us",
    usId: "33",
    scopeFiles: [feature],
    executeCheck: passingCheck,
  });
  assert.equal(verified.verified, true);
  assert.equal(verified.status, "passed");
  assert.equal(verified.gate, "D");
  assert.equal(verified.cached, false);
  assert.equal(verified.headSha, sha);
  assert.equal(headSha(repoRoot), sha);

  const cached = await verifyHeadDelivery({
    repoRoot,
    intent: "close_us",
    usId: "33",
    scopeFiles: [feature],
    executeCheck: () => {
      throw new Error("cached HEAD evidence must not execute checks again");
    },
  });
  assert.equal(cached.verified, true);
  assert.equal(cached.cached, true);

  const finalized = await finalizeLocally({
    repoRoot,
    intent: "close_us",
    usId: "33",
    scopeFiles: [feature],
    ciProvider: new MockCiProvider({ [sha]: { status: "passed" } }),
  });
  assert.equal(finalized.finalized, true);
  assert.equal(finalized.status, "passed");
  assert.equal(finalized.headSha, sha);
  assert.deepEqual(finalized.shas, [sha]);
  assert.deepEqual(finalized.ci.map((ci) => ci.status), ["passed"]);
});

test("verifyHeadDelivery uses the default executor when none is injected", async (t) => {
  const repoRoot = await createTempRepo(t);
  await commitPolicyWithGateDChecks(repoRoot, ["no_wip_in_scope"]);
  const feature = "app/src/test/resources/features/provider.feature";
  const sha = await commitFeature(
    repoRoot,
    feature,
    "Feature: Provider\n  Scenario: provider is ready\n    Given the service provider is authenticated\n",
    "test[33]: complete provider scenario",
  );

  const verified = await verifyHeadDelivery({
    repoRoot,
    intent: "close_us",
    usId: "33",
    scopeFiles: [feature],
  });

  assert.equal(verified.verified, true);
  assert.equal(verified.status, "passed");
  assert.equal(verified.headSha, sha);
  assert.deepEqual(verified.checks.map((check) => check.id), ["no_wip_in_scope"]);
});

test("verifyHeadDelivery blocks dirty worktrees and missing feature scope", async (t) => {
  const repoRoot = await createTempRepo(t);
  const feature = "app/src/test/resources/features/provider.feature";
  await commitFeature(
    repoRoot,
    feature,
    "Feature: Provider\n  Scenario: ready\n    Given the provider is open\n",
    "test[33]: add provider feature",
  );

  await fs.appendFile(path.join(repoRoot, feature), "  # local edit\n", "utf8");
  const dirty = await verifyHeadDelivery({ repoRoot, intent: "close_us", usId: "33", scopeFiles: [feature] });
  assert.equal(dirty.verified, false);
  assert.equal(dirty.status, "blocked");
  assert.ok(["DIRTY_WORKTREE", "UNSTAGED_CONFLICT"].includes(dirty.reason));

  execFileSync("git", ["restore", feature], { cwd: repoRoot });
  const missingScope = await verifyHeadDelivery({ repoRoot, intent: "close_us", usId: "33" });
  assert.equal(missingScope.verified, false);
  assert.equal(missingScope.status, "blocked");
  assert.equal(missingScope.reason, "MISSING_SCOPE_FOR_GATE_D");
});

test("finalizeDelivery inspects committed Android scope and blocks @wip", async (t) => {
  const repoRoot = await createTempRepo(t);
  const feature = "app/src/test/resources/features/provider-wip.feature";
  const sha = await commitFeature(
    repoRoot,
    feature,
    "Feature: Provider\n  @wip\n  Scenario: pending provider flow\n    Given the provider is open\n",
    "test[33]: leave provider scenario pending",
  );
  const verified = await verifyHeadDelivery({
    repoRoot,
    intent: "close_us",
    usId: "33",
    scopeFiles: [feature],
    executeCheck: passingCheck,
  });
  assert.equal(verified.verified, true);

  const result = await finalizeLocally({
    repoRoot,
    intent: "close_us",
    usId: "33",
    scopeFiles: [feature],
    ciProvider: new MockCiProvider({ [sha]: { status: "passed" } }),
  });
  assert.equal(result.finalized, false);
  assert.equal(result.reason, "WIP_IN_SCOPE");
  assert.ok(result.locations.includes(`${feature}:2`));
});

test("verifyHeadDelivery and finalizeDelivery reject non-numeric User Story ids", async (t) => {
  const repoRoot = await createTempRepo(t);
  const feature = "app/src/test/resources/features/provider.feature";
  const sha = await commitFeature(
    repoRoot,
    feature,
    "Feature: Provider\n  Scenario: ready\n    Given the provider is open\n",
    "test[33]: add provider feature",
  );

  await assert.rejects(
    () => verifyHeadDelivery({ repoRoot, intent: "close_us", usId: "33.1", scopeFiles: [feature] }),
    /digits only/,
  );

  const verified = await verifyHeadDelivery({
    repoRoot,
    intent: "close_us",
    usId: "33",
    scopeFiles: [feature],
    executeCheck: passingCheck,
  });
  assert.equal(verified.verified, true);
  await assert.rejects(
    () => finalizeLocally({
      repoRoot,
      intent: "close_us",
      usId: "US-33",
      scopeFiles: [feature],
      ciProvider: new MockCiProvider({ [sha]: { status: "passed" } }),
    }),
    /digits only/,
  );
});
