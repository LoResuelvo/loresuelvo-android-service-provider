import test from "node:test";
import assert from "node:assert/strict";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadDeliveryPolicy, SAFE_COMMANDS } from "../lib/policy-loader.mjs";
import { classifyFile, classifyFiles } from "../lib/classify-files.mjs";
import { selectGate } from "../lib/select-gate.mjs";
import { resolveCheck } from "../lib/execute-check.mjs";
import { extractUsId, extractAllUsIds } from "../lib/git-snapshot.mjs";
import { validateCommitMessage } from "../lib/git-hooks.mjs";
import { resolveRepository, GitHubActionsProvider, MockCiProvider } from "../lib/ci-provider.mjs";
import { analyzeTypeScriptImpact } from "../lib/dependency-impact.mjs";
import { analyzeCucumberImpact } from "../lib/impact-index.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
const policy = await loadDeliveryPolicy({ repoRoot: ROOT });

test("Android policy validates with all analyzers disabled", () => {
  assert.deepEqual(policy.maintainabilityThresholds, {});
  assert.equal(policy.analysis.dependencyImpact.enabled, false);
  assert.equal(policy.analysis.cucumberImpact.enabled, false);
  assert.equal(policy.analysis.maintainability.enabled, false);
  assert.deepEqual(Object.keys(policy.gates).sort(), ["0", "A", "B", "C", "D", "NONE", "R"]);
});

test("numeric User Story extraction accepts [33] and rejects [US-33]", () => {
  assert.equal(extractUsId("feat[33]: add provider delivery"), "33");
  assert.deepEqual(extractAllUsIds("fix[33]: x and docs[34]: y"), ["33", "34"]);
  assert.equal(extractUsId("feat[US-33]: add provider delivery"), null);
  assert.equal(validateCommitMessage("feat[33]: add provider delivery").valid, true);
  assert.equal(validateCommitMessage("feat[US-33]: add provider delivery").reason, "INVALID_US_ID");
});

test("Android classification covers the required path families", () => {
  const cases = [
    ["app/src/main/java/com/loresuelvo/serviceprovider/Foo.kt", "production_kotlin", false],
    ["app/src/main/java/com/loresuelvo/serviceprovider/domain/Foo.kt", "isolated_domain_kotlin", false],
    ["app/src/main/java/com/loresuelvo/serviceprovider/data/Foo.kt", "data_kotlin", true],
    ["app/src/main/java/com/loresuelvo/serviceprovider/di/Foo.kt", "di_kotlin", true],
    ["app/src/main/java/com/loresuelvo/serviceprovider/ui/Foo.kt", "compose_ui", true],
    ["app/src/test/java/com/loresuelvo/serviceprovider/FooTest.kt", "jvm_test", false],
    ["app/src/test/resources/features/auth/provider-welcome.feature", "bdd_feature", false],
    ["app/src/androidTest/java/com/loresuelvo/serviceprovider/FooTest.kt", "instrumented_test", true],
    ["app/src/main/res/values/strings.xml", "android_resource", true],
    ["app/src/main/res/drawable-nodpi/logo.png", "android_resource", true],
    ["app/src/main/res/mipmap-hdpi/ic_launcher.webp", "android_resource", true],
    ["docs/images/architecture.png", "non_code_docs_config", false],
    ["app/src/main/AndroidManifest.xml", "android_manifest", true],
    ["app/src/staging/AndroidManifest.xml", "android_manifest", true],
    ["build.gradle.kts", "build_infrastructure", true],
    ["app/build.gradle.kts", "build_infrastructure", true],
    ["gradle/libs.versions.toml", "build_infrastructure", true],
    ["gradle.properties", "build_infrastructure", true],
    ["gradlew", "build_infrastructure", true],
    ["Makefile", "build_infrastructure", true],
    ["scripts/run_acceptance_tests.sh", "build_infrastructure", true],
    ["tools/delivery-mcp/lib/select-gate.mjs", "delivery_tooling", false],
    [".delivery/policy.v1.json", "delivery_tooling", false],
    [".githooks/pre-commit", "delivery_tooling", false],
    [".codex/config.toml", "delivery_tooling", false],
    [".agents/skills/android-testing-gates/SKILL.md", "non_code_docs_config", false],
    ["AGENTS.md", "non_code_docs_config", false],
    [".github/workflows/ci.yml", "human_only", false],
  ];
  for (const [file, category, gateC] of cases) {
    const result = classifyFile(file, policy);
    assert.equal(result.category, category, file);
    assert.equal(result.isGateCTrigger, gateC, file);
  }
});

test("gate selection is conservative and Android-specific", () => {
  const gateFor = (file, intent = "prepare_commit", extra = {}) =>
    selectGate({ policy, intent, snapshot: { stagedFiles: [file], unstagedConflicts: [], unrelatedUnstaged: [], untracked: [] }, ...extra });
  assert.equal(gateFor("app/src/main/java/com/loresuelvo/serviceprovider/domain/Foo.kt").gate.id, "A");
  assert.equal(gateFor("app/src/main/java/com/loresuelvo/serviceprovider/data/Foo.kt").gate.id, "C");
  assert.equal(gateFor("app/src/test/resources/features/auth/provider-welcome.feature").gate.id, "0");
  assert.equal(gateFor("tools/delivery-mcp/lib/select-gate.mjs").gate.id, "A");
  assert.equal(gateFor(".github/workflows/generated").status, "blocked");
  assert.equal(gateFor("some/unknown/path.kt").gate.id, "C");
  assert.equal(gateFor("AGENTS.md").gate.id, "NONE");
  const scenario = gateFor("app/src/test/resources/features/auth/provider-welcome.feature", "close_scenario", { featureFile: "app/src/test/resources/features/auth/provider-welcome.feature" });
  assert.equal(scenario.gate.id, "B");
  const batch = gateFor("app/src/test/resources/features/auth/provider-welcome.feature", "close_batch", { scopeFiles: ["app/src/test/resources/features/auth/provider-welcome.feature"] });
  assert.equal(batch.gate.id, "D");
  assert.ok(batch.gate.postPushChecks.includes("ci_green"));
});

test("policy and executor accept only exact Android commands", () => {
  assert.equal(SAFE_COMMANDS.size, 9);
  for (const [checkId, definition] of Object.entries(policy.checkCatalog)) {
    if (definition.kind !== "command") continue;
    const resolved = resolveCheck({ checkId, definition, parameters: {}, repoRoot: ROOT });
    assert.deepEqual([resolved.command, ...resolved.args], [definition.command, ...definition.args]);
  }
  assert.throws(
    () => resolveCheck({ checkId: "unsafe", definition: { kind: "command", command: "sh", args: ["-c", "echo unsafe"], timeoutMs: 1000 }, repoRoot: ROOT }),
    /Unsafe or unauthorized command/
  );
});

test("disabled analyzers return not_applicable without guessing impact", () => {
  assert.equal(analyzeTypeScriptImpact({ files: ["app/src/main/Foo.kt"] }).status, "not_applicable");
  assert.equal(analyzeCucumberImpact({ files: ["app/src/test/resources/features/foo.feature"] }).status, "not_applicable");
});

test("CI repository resolution prefers explicit/env/remote target", () => {
  assert.equal(resolveRepository({ repo: "Acme/example", repoRoot: ROOT }), "Acme/example");
  assert.equal(resolveRepository({ repoRoot: ROOT }), "LoResuelvo/loresuelvo-android-service-provider");
  const provider = new GitHubActionsProvider({ repoRoot: ROOT, token: null });
  assert.equal(provider.repo, "LoResuelvo/loresuelvo-android-service-provider");
  const mock = new MockCiProvider({ abc1234: { status: "passed" } });
  return mock.inspectCommit("abc1234", { repoRoot: ROOT }).then((result) => assert.equal(result.status, "passed"));
});
