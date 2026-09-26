import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";
import { analyzeFeatureGate, readFeatureGateTree } from "../lib/feature-gate.mjs";
import { inspectDelivery } from "../lib/inspect-delivery.mjs";
import { loadDeliveryPolicy } from "../lib/policy-loader.mjs";
import { selectGate } from "../lib/select-gate.mjs";
import { resolveCheck, executeFeatureJvmCheck } from "../lib/execute-check.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

const base = "app/src/test/java/example/";
const feature = "app/src/test/resources/features/proposal.feature";
const runner = `${base}proposal/ProposalCucumberTest.kt`;
const steps = `${base}proposal/ProposalSteps.kt`;
const unit = `${base}unit/ProposalTest.kt`;
const sources = () => new Map([
  [feature, "Feature: Proposal\n  Scenario: Send\n    Given a proposal\n"],
  [runner, `package example.proposal
import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith
@RunWith(Cucumber::class)
@CucumberOptions(features = ["classpath:features/proposal.feature"], glue = ["example.proposal"], plugin = ["pretty", "summary"])
class ProposalCucumberTest
`],
  [steps, "package example.proposal\nclass ProposalSteps\n"],
  [unit, "package example.unit\nclass ProposalTest\n"],
]);
const analyze = (files, before = sources(), after = before) => analyzeFeatureGate({ files, featureFile: feature, before, after });

test("feature gate selects one runner and affected JVM classes from both trees", () => {
  assert.deepEqual(analyze([feature, steps, unit]).testClasses, ["example.proposal.ProposalCucumberTest", "example.unit.ProposalTest"]);
  assert.equal(analyze([feature, steps]).scope, "feature");
});

test("feature gate preserves full JVM for shared glue and consumers", () => {
  const shared = sources();
  shared.set(`${base}other/OtherCucumberTest.kt`, shared.get(runner).replaceAll("example.proposal", "example.other").replace("ProposalCucumberTest", "OtherCucumberTest").replace("proposal.feature", "other.feature").replace('glue = ["example.other"]', 'glue = ["example.proposal"]'));
  assert.equal(analyze([steps], shared).scope, "full_jvm");
  const consumer = sources();
  consumer.set(`${base}other/OtherSteps.kt`, "package example.other\nimport example.proposal.ProposalSteps as SharedSteps\nclass OtherSteps\n");
  assert.equal(analyze([steps], consumer).scope, "full_jvm");
  assert.equal(analyze([steps], consumer, sources()).scope, "full_jvm", "removed consumers still count");
});

test("feature gate fails closed for production, platform, tooling, runner edits and unavailable sources", () => {
  for (const file of [
    "app/src/main/java/example/ui/CommonViewModel.kt", "app/src/main/java/example/di/Module.kt",
    "app/src/main/java/example/data/Dto.kt", "app/src/main/java/example/data/Mapper.kt",
    "app/src/main/java/example/ui/NavHost.kt", "app/src/main/res/values/strings.xml",
    "app/src/androidTest/java/example/DeviceTest.kt", "app/build.gradle.kts",
    "tools/delivery-mcp/lib/select-gate.mjs", runner, `${base}MissingTest.kt`,
  ]) assert.equal(analyze([feature, file]).scope, "full_jvm", file);
});

test("feature gate rejects ambiguous runners, filtered runners, and extra top-level test classes", () => {
  for (const source of [
    sources().get(runner).replace('plugin =', 'name = ["Only one scenario"], plugin ='),
    sources().get(runner).replace('plugin =', 'tags = "@subset", plugin ='),
  ]) {
    const tree = sources();
    tree.set(runner, source);
    assert.equal(analyze([feature], tree).scope, "full_jvm");
  }
  const extra = sources();
  extra.set(unit, "package example.unit\nclass ProposalTest\nclass AnotherTest\n");
  assert.equal(analyze([unit], extra).scope, "full_jvm");
  const duplicate = sources();
  duplicate.set(`${base}proposal/OtherCucumberTest.kt`, duplicate.get(runner).replaceAll("ProposalCucumberTest", "OtherCucumberTest"));
  assert.equal(analyze([feature], duplicate).scope, "full_jvm");
});

test("feature gate includes wildcard consumers and rejects unsupported source sets and reflection", () => {
  const tree = sources();
  tree.set(`${base}consumer/ConsumerTest.kt`, "package example.consumer\nimport example.unit.*\nclass ConsumerTest\n");
  assert.ok(analyze([unit], tree).testClasses.includes("example.consumer.ConsumerTest"));
  tree.set("app/src/testDev/java/example/DevTest.kt", "package example\nclass DevTest\n");
  assert.equal(analyze([unit], tree).scope, "full_jvm");
  tree.delete("app/src/testDev/java/example/DevTest.kt");
  tree.set("app/build.gradle.kts", 'sourceSets { getByName("test").java.srcDir("extra-tests") }');
  assert.equal(analyze([unit], tree).scope, "full_jvm");
  tree.delete("app/build.gradle.kts");
  tree.set(unit, tree.get(unit) + '\nval dynamic = Class.forName("example.proposal.ProposalSteps")');
  assert.equal(analyze([unit], tree).scope, "full_jvm");
});

test("staged Gate B uses immutable trees and dirty snapshots remain blocked", async (t) => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-feature-gate-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const git = (...args) => execFileSync("git", args, { cwd: root, stdio: "pipe" });
  git("init");
  git("config", "user.name", "Delivery Tests");
  git("config", "user.email", "delivery-tests@example.com");
  git("config", "commit.gpgsign", "false");
  await fs.cp(path.join(ROOT, ".delivery/schemas"), path.join(root, ".delivery/schemas"), { recursive: true });
  await fs.copyFile(path.join(ROOT, ".delivery/policy.v1.json"), path.join(root, ".delivery/policy.v1.json"));
  await fs.writeFile(path.join(root, ".gitignore"), ".delivery/runtime/\n");
  for (const [file, source] of sources()) {
    await fs.mkdir(path.dirname(path.join(root, file)), { recursive: true });
    await fs.writeFile(path.join(root, file), source);
  }
  git("add", ".");
  git("commit", "-m", "test[53]: establish fixture");
  await fs.appendFile(path.join(root, steps), "// isolated change\n");
  git("add", steps);
  const input = { repoRoot: root, intent: "close_scenario", featureFile: feature };
  const selected = (await inspectDelivery(input)).result;
  assert.equal(selected.status, "ready");
  assert.deepEqual(selected.gate.checkIds, ["feature_jvm_dev"]);
  assert.deepEqual(selected.gate.parameters.testClasses, ["example.proposal.ProposalCucumberTest"]);
  await fs.appendFile(path.join(root, unit), "// outside snapshot\n");
  assert.equal((await inspectDelivery(input)).result.status, "blocked");
});

test("feature execution falls back only on empty selection and includes all execution time", async () => {
  const policy = await loadDeliveryPolicy({ repoRoot: ROOT });
  const check = resolveCheck({
    checkId: "feature_jvm_dev", definition: policy.checkCatalog.feature_jvm_dev, repoRoot: ROOT,
    parameters: { featureFile: "app/src/test/resources/features/proposals/provider-service-proposal.feature",
      runnerClass: "example.proposal.ProposalCucumberTest", testClasses: ["example.proposal.ProposalCucumberTest", "example.unit.ProposalTest"] },
  });
  const calls = [];
  const result = await executeFeatureJvmCheck({ check, repoRoot: ROOT, logPath: "unused", execute: async ({ check: command }) => {
    calls.push(command);
    return calls.length === 1
      ? { id: check.id, status: "failed", durationMs: 7, rawOutput: "No tests found for given includes:" }
      : { id: check.id, status: "passed", durationMs: 11 };
  } });
  assert.equal(result.status, "passed");
  assert.equal(result.durationMs, 18);
  assert.deepEqual(calls[1].args, ["test", "FLAVOR=Dev"]);
  let executions = 0;
  const failed = await executeFeatureJvmCheck({ check, repoRoot: ROOT, logPath: "unused", execute: async () => {
    executions++;
    return { status: "failed", durationMs: 1, rawOutput: "Assertion failed" };
  } });
  assert.equal(failed.status, "failed");
  assert.equal(executions, 1);
  calls.length = 0;
  await executeFeatureJvmCheck({ check, repoRoot: ROOT, logPath: "unused", execute: async ({ check: command }) => {
    calls.push(command);
    return { status: "passed", durationMs: 1 };
  } });
  assert.deepEqual(calls.map((command) => command.args.slice(2)), [
    ["--tests", "example.proposal.ProposalCucumberTest"], ["--tests", "example.unit.ProposalTest"],
  ]);
  for (const invalid of ["*Test", "example.Test --info", "../Test", "example.Test;whoami"]) {
    assert.throws(() => resolveCheck({ checkId: check.id, definition: policy.checkCatalog.feature_jvm_dev, repoRoot: ROOT,
      parameters: { ...check.parameters, testClasses: [check.parameters.runnerClass, invalid] } }), /exact test classes/);
  }
});

test("US-53 regression corpus never downgrades high-risk closures or CI repairs", async () => {
  const policy = await loadDeliveryPolicy({ repoRoot: ROOT });
  const git = (...args) => execFileSync("git", args, { cwd: ROOT, encoding: "utf8" }).trim();
  const proposal = "app/src/test/resources/features/proposals/provider-service-proposal.feature";
  // Keep the path corpus available in shallow CI checkouts. Exact historical
  // source analysis is also exercised locally when those Git objects exist.
  const corpus = JSON.parse(await fs.readFile(new URL("./fixtures/us53-gate-paths.json", import.meta.url), "utf8"));
  const current = readFeatureGateTree(ROOT, git("rev-parse", "HEAD"));
  const shallowCase = corpus.find(entry => entry.sha === "81b3219");
  const shallowImpact = analyzeFeatureGate({ files: shallowCase.files, featureFile: proposal,
    before: current, after: current });
  const shallowGate = selectGate({ policy, intent: "close_scenario", featureFile: proposal,
    snapshot: { stagedFiles: shallowCase.files }, cucumberImpact: shallowImpact });
  assert.deepEqual(shallowGate.gate.checkIds, ["jvm_test_dev"]);
  for (const { sha, gate: expectedGate, files } of corpus) {
    let before = current;
    let after = current;
    let historicalTreesAvailable = true;
    try {
      before = readFeatureGateTree(ROOT, git("rev-parse", `${sha}^`));
      after = readFeatureGateTree(ROOT, git("rev-parse", sha));
    } catch {
      // Classification assertions still run; no historical execution is claimed.
      historicalTreesAvailable = false;
    }
    const impact = analyzeFeatureGate({ files, featureFile: proposal,
      before, after });
    const result = selectGate({ policy, intent: expectedGate === "R" ? "repair_ci" : "close_scenario",
      repairsSha: "a".repeat(40), featureFile: proposal, snapshot: { stagedFiles: files }, cucumberImpact: impact });
    assert.equal(result.gate.id, expectedGate, sha);
    if (expectedGate !== "B") assert.deepEqual(result.gate.checkIds, policy.gates[expectedGate].checkIds, sha);
    if (sha === "81b3219") assert.deepEqual(result.gate.checkIds,
      [historicalTreesAvailable ? "feature_jvm_dev" : "jvm_test_dev"]);
    for (const intent of ["close_batch", "close_us"]) {
      const closure = selectGate({ policy, intent, featureFile: proposal, snapshot: { stagedFiles: files }, cucumberImpact: impact });
      assert.deepEqual(closure.gate.checkIds, policy.gates.D.checkIds);
      assert.deepEqual(closure.gate.postPushChecks, ["ci_green"]);
    }
  }
});
