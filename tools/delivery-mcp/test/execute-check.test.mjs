import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  executeCheck,
  extractLocations,
  resolveCheck,
  summarizeFailureOutput,
} from "../lib/execute-check.mjs";
import { redactSecrets } from "../lib/redact-secrets.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

test("summarizeFailureOutput strips stack noise and stays bounded", () => {
  const output = [
    "e: expected provider count to be 2",
    "    at WelcomeTestKt (app/src/test/java/com/example/WelcomeTest.kt:10:2)",
    "Received: 0",
    "node:internal/process/task_queues:95:5",
    "additional context",
  ].join("\n");
  assert.deepEqual(summarizeFailureOutput(output, 2), [
    "e: expected provider count to be 2",
    "Received: 0",
  ]);
});

test("extractLocations finds Android file and line references", () => {
  const output = [
    "e: app/src/main/java/com/loresuelvo/serviceprovider/Welcome.kt:25: unresolved reference",
    "at app/src/test/java/com/loresuelvo/serviceprovider/WelcomeTest.kt:42:15",
  ].join("\n");
  const locations = extractLocations(output);
  assert.ok(locations.includes("app/src/main/java/com/loresuelvo/serviceprovider/Welcome.kt:25"));
  assert.ok(locations.includes("app/src/test/java/com/loresuelvo/serviceprovider/WelcomeTest.kt:42"));
});

test("redactSecrets removes credentials before summaries or logs are persisted", () => {
  const raw = [
    "Authorization: Bearer android-secret-token-123456",
    "token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature",
    "password: 'secretPassword123'",
    "api_key=sk-1234567890abcdef1234567890",
    "-----BEGIN RSA PRIVATE KEY-----",
    "private material",
    "-----END RSA PRIVATE KEY-----",
  ].join("\n");

  const redacted = redactSecrets(raw);
  assert.ok(!redacted.includes("android-secret-token-123456"));
  assert.ok(!redacted.includes("eyJhbGciOiJIUzI1Ni"));
  assert.ok(!redacted.includes("secretPassword123"));
  assert.ok(!redacted.includes("sk-1234567890abcdef"));
  assert.ok(!redacted.includes("private material"));
  assert.match(redacted, /REDACTED/);
});

test("resolveCheck accepts only exact Android policy commands", () => {
  const repoRoot = process.cwd();
  const check = resolveCheck({
    checkId: "jvm_test_dev",
    definition: {
      kind: "command",
      label: "Dev JVM tests",
      command: "make",
      args: ["test", "FLAVOR=Dev"],
      display: "make test FLAVOR=Dev",
      timeoutMs: 900000,
    },
    repoRoot,
  });
  assert.deepEqual(check.args, ["test", "FLAVOR=Dev"]);

  assert.throws(
    () => resolveCheck({
      checkId: "jvm_test_dev",
      definition: {
        kind: "command",
        label: "Dev JVM tests",
        command: "make",
        args: ["test", "FLAVOR=Dev", "--debug"],
        display: "make test FLAVOR=Dev --debug",
      },
      repoRoot,
    }),
    /Unsafe or unauthorized command/
  );
});

test("resolveCheck rejects traversal before command authorization", () => {
  const repoRoot = process.cwd();
  assert.throws(
    () => resolveCheck({
      checkId: "e2e_feature",
      definition: {
        kind: "command",
        label: "Android feature",
        command: "make",
        args: ["e2e", "FLAVOR=Dev", "FEATURE={featureFile}"],
        requires: ["featureFile"],
      },
      parameters: { featureFile: "../../../etc/passwd.feature" },
      repoRoot,
    }),
    /path traversal|resolves outside repository/
  );

  assert.throws(
    () => resolveCheck({
      checkId: "no_wip_in_scope",
      definition: {
        kind: "builtin",
        label: "No @wip",
        handler: "no_wip_in_scope",
        requires: ["scopeFeatures"],
      },
      parameters: { scopeFeatures: ["../outside.feature"] },
      repoRoot,
    }),
    /path traversal|resolves outside repository/
  );
});

test("no_wip_in_scope checks only declared Android feature scope", async (t) => {
  const repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), "android-no-wip-"));
  t.after(() => fs.rm(repoRoot, { recursive: true, force: true }));
  const featureFile = "app/src/test/resources/features/welcome.feature";
  await fs.mkdir(path.dirname(path.join(repoRoot, featureFile)), { recursive: true });
  await fs.writeFile(
    path.join(repoRoot, featureFile),
    "Feature: Provider welcome\n\n  @wip\n  Scenario: Sign in\n",
    "utf8"
  );

  const check = resolveCheck({
    checkId: "no_wip_in_scope",
    definition: {
      kind: "builtin",
      label: "No @wip",
      handler: "no_wip_in_scope",
      requires: ["scopeFeatures"],
    },
    parameters: { scopeFeatures: [featureFile] },
    repoRoot,
  });
  const result = await executeCheck({ check, repoRoot, limits: {} });
  assert.equal(result.status, "failed");
  assert.equal(result.diagnostic.code, "WIP_TAG_IN_COMPLETED_SCOPE");
  assert.deepEqual(result.summaryLines, [
    featureFile + ":3: @wip remains in completed scope",
  ]);
  assert.deepEqual(result.locations, [featureFile + ":3"]);
});

test("command execution times out and records a bounded diagnostic", async () => {
  const repoRoot = ROOT;
  const result = await executeCheck({
    check: {
      id: "delivery_unit",
      kind: "command",
      label: "Delivery tooling tests",
      command: "npm",
      args: ["--prefix", "tools/delivery-mcp", "test"],
      timeoutMs: 1,
    },
    repoRoot,
    logPath: ".delivery/runtime/logs/android-timeout-contract-" + process.pid + ".log",
    limits: { maxCheckLogBytes: 1024, maxFailureSummaryLines: 4 },
  });

  assert.equal(result.status, "failed");
  assert.equal(result.diagnostic.code, "CHECK_TIMEOUT");
  assert.equal(result.outputTruncated, false);
});
