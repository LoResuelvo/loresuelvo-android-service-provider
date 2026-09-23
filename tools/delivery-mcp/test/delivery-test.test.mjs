import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import {
  testDelivery,
  validateTestFilePath,
  validateFeatureFilePath,
  parseTestCounts,
  shouldUseDeliveryTestJob,
} from "../lib/test-delivery.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
const kotlinTest = "app/src/test/java/com/loresuelvo/serviceprovider/ui/auth/WelcomeViewModelTest.kt";
const feature = "app/src/test/resources/features/auth/provider-welcome.feature";
const passingExecutor = async ({ command, args, logPath }) => ({
  passed: true,
  durationMs: 1,
  exitCode: 0,
  rawOutput: command === "npm" ? "ℹ pass 3\nℹ fail 0" : "3 tests completed, 0 failed",
  counts: { passed: 3, failed: 0, skipped: 0 },
  logPath,
  command,
  args,
});

test("delivery_test validates Kotlin Test.kt and Android feature paths", () => {
  assert.equal(validateTestFilePath(ROOT, kotlinTest), kotlinTest);
  assert.equal(validateFeatureFilePath(ROOT, feature), feature);
  assert.throws(() => validateTestFilePath(ROOT, "app/src/test/Foo.kt"), /Only Kotlin/);
  assert.throws(() => validateTestFilePath(ROOT, "../outside/Test.kt"), /Invalid test file path/);
  assert.throws(() => validateTestFilePath(ROOT, "app/src/androidTest/java/DeviceTest.kt"), /Only Kotlin/);
  assert.throws(() => validateTestFilePath(ROOT, "app/src/main/java/FooTest.kt"), /Only Kotlin/);
  assert.throws(() => validateFeatureFilePath(ROOT, "features/foo.feature"), /under app\/src\/test\/resources\/features/);
});

test("delivery_test parses JVM/Node counts", () => {
  assert.deepEqual(parseTestCounts("3 tests completed, 1 failed"), { found: true, counts: { passed: 2, failed: 1, skipped: 0 } });
  assert.deepEqual(parseTestCounts("ℹ pass 4\nℹ fail 0"), { found: true, counts: { passed: 4, failed: 0, skipped: 0 } });
});

test("delivery_test filters Kotlin unit classes but keeps scenario validation complete", async () => {
  const commands = [];
  const executeFn = async (request) => {
    commands.push([request.command, ...request.args]);
    return passingExecutor(request);
  };
  const unit = await testDelivery({
    repoRoot: ROOT,
    mode: "unit",
    testFiles: [kotlinTest],
    executionMode: "sync",
    force: true,
    executeFn,
  });
  assert.equal(unit.status, "passed");
  assert.equal(unit.mode, "unit");
  assert.deepEqual(commands[0], [
    "scripts/with-android-env.sh", "./gradlew", ":app:testDevDebugUnitTest",
    "--tests", "com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModelTest",
  ]);

  const scenario = await testDelivery({
    repoRoot: ROOT,
    mode: "scenario",
    featureFile: feature,
    scenarioName: "Provider sees the welcome screen",
    executionMode: "sync",
    force: true,
    executeFn,
  });
  assert.equal(scenario.status, "passed");
  assert.equal(scenario.mode, "scenario");
  assert.deepEqual(commands[1], ["make", "test", "FLAVOR=Dev"]);

  await testDelivery({ repoRoot: ROOT, mode: "unit", executionMode: "sync", force: true, executeFn });
  assert.deepEqual(commands[2], ["make", "test", "FLAVOR=Dev"]);
});

test("TDD cache invalidates when an already-dirty production file changes", async (t) => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-tdd-cache-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const git = (...args) => execFileSync("git", args, { cwd: root, stdio: "ignore" });
  git("init");
  git("config", "user.name", "Delivery Tests");
  git("config", "user.email", "delivery-tests@example.com");
  git("config", "commit.gpgsign", "false");
  await fs.cp(path.join(ROOT, ".delivery/schemas"), path.join(root, ".delivery/schemas"), { recursive: true });
  await fs.copyFile(path.join(ROOT, ".delivery/policy.v1.json"), path.join(root, ".delivery/policy.v1.json"));
  await fs.writeFile(path.join(root, ".gitignore"), ".delivery/runtime/\n");
  const file = "app/src/test/java/example/ExampleTest.kt";
  await fs.mkdir(path.dirname(path.join(root, file)), { recursive: true });
  await fs.writeFile(path.join(root, file), "package example\nclass ExampleTest\n");
  await fs.writeFile(path.join(root, "Production.kt"), "val value = 0\n");
  git("add", ".");
  git("commit", "-m", "Initial fixture");
  let executions = 0;
  const options = { repoRoot: root, mode: "unit", testFiles: [file], executionMode: "sync",
    executeFn: async (request) => { executions++; return passingExecutor(request); } };
  await fs.writeFile(path.join(root, "Production.kt"), "val value = 1\n");
  assert.equal((await testDelivery(options)).cached, false);
  assert.equal((await testDelivery(options)).cached, true);
  await fs.writeFile(path.join(root, "Production.kt"), "val value = 2\n");
  assert.equal((await testDelivery(options)).cached, false);
  assert.equal(executions, 2);
});

test("delivery_test rejects mixed test runtimes and unsafe execution mode", async () => {
  const mixed = await testDelivery({
    repoRoot: ROOT,
    mode: "unit",
    testFiles: [kotlinTest, "tools/delivery-mcp/test/android-contract.test.mjs"],
    executionMode: "sync",
    force: true,
    executeFn: passingExecutor,
  });
  assert.equal(mixed.status, "error");
  assert.equal(mixed.diagnostics[0].code, "MIXED_TEST_RUNTIMES");

  const invalid = await testDelivery({ repoRoot: ROOT, mode: "unit", executionMode: "shell" });
  assert.equal(invalid.status, "error");
  assert.equal(invalid.diagnostics[0].code, "INVALID_EXECUTION_MODE");
});

test("delivery_test job selection is conservative", () => {
  assert.equal(shouldUseDeliveryTestJob({ mode: "unit", executionMode: "auto", executeDefault: true }), false);
  assert.equal(shouldUseDeliveryTestJob({ mode: "affected", executionMode: "auto", executeDefault: true }), true);
  assert.equal(shouldUseDeliveryTestJob({ mode: "scenario", executionMode: "auto", executeDefault: true }), true);
  assert.equal(shouldUseDeliveryTestJob({ mode: "unit", executionMode: "job", executeDefault: true }), true);
  assert.equal(shouldUseDeliveryTestJob({ mode: "affected", executionMode: "auto", workerJobId: "job-1", executeDefault: true }), false);
});
