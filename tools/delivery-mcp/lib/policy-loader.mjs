import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { findRepoRoot, assertSafeRepoPath } from "./repo-root.mjs";
import { validateAgainstSchema } from "./validate-schema.mjs";

export const DELIVERY_POLICY_PATH = ".delivery/policy.v1.json";

const REQUIRED_GATES = ["NONE", "0", "A", "B", "C", "D", "R"];

// Commands are compared as an exact executable/argument tuple. This is the
// single allowlist shared by policy validation and command execution.
export const SAFE_COMMANDS = new Set([
  JSON.stringify(["npm", "--prefix", "tools/delivery-mcp", "test"]),
  JSON.stringify(["make", "test", "FLAVOR=Dev"]),
  JSON.stringify(["make", "lint", "FLAVOR=Dev"]),
  JSON.stringify(["make", "build", "FLAVOR=Dev"]),
  JSON.stringify(["make", "e2e", "FLAVOR=Dev"]),
  JSON.stringify(["make", "test", "FLAVOR=Staging"]),
  JSON.stringify(["make", "lint", "FLAVOR=Staging"]),
  JSON.stringify(["make", "build", "FLAVOR=Staging"]),
  JSON.stringify(["make", "e2e", "FLAVOR=Staging"]),
]);

export const SAFE_BUILTINS = new Set(["no_wip_in_scope", "ci_green"]);

function assertPositiveInteger(value, field) {
  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`Invalid delivery policy: ${field} must be a positive integer`);
  }
}

function assertSafeCheck(checkId, definition) {
  if (!definition || typeof definition !== "object") {
    throw new Error(`Invalid delivery policy: missing check definition for ${checkId}`);
  }

  if (definition.kind === "command") {
    const signature = JSON.stringify([definition.command, ...(definition.args || [])]);
    if (!SAFE_COMMANDS.has(signature)) {
      throw new Error(`Unsafe delivery command rejected for check ${checkId}`);
    }
    assertPositiveInteger(definition.timeoutMs, `checkCatalog.${checkId}.timeoutMs`);
    return;
  }

  if (definition.kind === "builtin" && SAFE_BUILTINS.has(definition.handler)) return;

  throw new Error(`Unsupported delivery check kind for ${checkId}`);
}

function validateClassification(classification) {
  if (!classification || !Array.isArray(classification.rules) || !classification.fallback) {
    throw new Error("Invalid delivery policy: classification rules and fallback are required");
  }
  const ids = new Set();
  for (const rule of classification.rules) {
    if (typeof rule.id !== "string" || !rule.id) {
      throw new Error("Invalid delivery policy: classification rule id is required");
    }
    if (ids.has(rule.id)) {
      throw new Error(`Invalid delivery policy: duplicate classification rule '${rule.id}'`);
    }
    ids.add(rule.id);
    for (const pattern of rule.match?.patterns || []) {
      try {
        new RegExp(pattern);
      } catch {
        throw new Error(`Invalid delivery policy: malformed classification pattern in '${rule.id}'`);
      }
    }
  }
}

function validateAnalysis(policy) {
  const analyzers = policy.analysis;
  if (!analyzers || typeof analyzers !== "object") {
    throw new Error("Invalid delivery policy: analysis configuration is required");
  }
  for (const key of ["dependencyImpact", "cucumberImpact", "maintainability"]) {
    const analyzer = analyzers[key];
    if (!analyzer || typeof analyzer.enabled !== "boolean" || typeof analyzer.adapter !== "string" || !analyzer.adapter) {
      throw new Error(`Invalid delivery policy: analysis.${key} must define enabled and adapter`);
    }
  }
  // The first Android release intentionally keeps all unavailable analyzers
  // disabled. Enabling one without an implementation would be unsafe.
  if (analyzers.dependencyImpact.enabled || analyzers.cucumberImpact.enabled || analyzers.maintainability.enabled) {
    throw new Error("Android delivery analyzers are disabled until their adapters are implemented");
  }
}

function validatePolicy(policy) {
  assertPositiveInteger(policy?.version, "version");
  if (!policy?.checkCatalog || !policy?.gates || !policy?.limits || !policy?.ci) {
    throw new Error("Invalid delivery policy: checkCatalog, gates, limits, and ci are required");
  }

  validateAnalysis(policy);
  validateClassification(policy.classification);

  for (const limit of [
    "maxStagedFiles",
    "maxDiffSizeBytes",
    "maxSignals",
    "maxDiagnostics",
    "maxCheckLogBytes",
    "maxFailureSummaryLines",
  ]) {
    assertPositiveInteger(policy.limits[limit], `limits.${limit}`);
  }

  assertPositiveInteger(policy.ci.maxInFlightCommits, "ci.maxInFlightCommits");

  for (const [checkId, definition] of Object.entries(policy.checkCatalog)) {
    if (!/^[a-z][a-z0-9_]*$/.test(checkId)) {
      throw new Error(`Invalid delivery check identifier: ${checkId}`);
    }
    assertSafeCheck(checkId, definition);
  }

  for (const gateId of REQUIRED_GATES) {
    const gate = policy.gates[gateId];
    if (!gate || gate.id !== gateId || !Array.isArray(gate.checkIds) || !Array.isArray(gate.postPushChecks)) {
      throw new Error(`Invalid delivery policy: gate ${gateId} is incomplete`);
    }
    for (const checkId of gate.checkIds) {
      if (!policy.checkCatalog[checkId]) {
        throw new Error(`Invalid delivery policy: gate ${gateId} references unknown check ${checkId}`);
      }
    }
    for (const postPushCheck of gate.postPushChecks) {
      if (!SAFE_BUILTINS.has(postPushCheck)) {
        throw new Error(`Invalid delivery policy: gate ${gateId} references unsupported post-push check ${postPushCheck}`);
      }
    }
  }
}

export async function loadDeliveryPolicy({ repoRoot } = {}) {
  const root = findRepoRoot(repoRoot);
  assertSafeRepoPath(root, DELIVERY_POLICY_PATH, "Delivery policy");
  const absolutePath = path.resolve(root, DELIVERY_POLICY_PATH);
  const source = await fs.readFile(absolutePath, "utf8");
  let policy;
  try {
    policy = JSON.parse(source);
  } catch (error) {
    throw new Error(`Invalid delivery policy JSON: ${error.message}`);
  }
  validateAgainstSchema(policy, "policy.schema.json", root);
  validatePolicy(policy);
  return {
    ...policy,
    sourceHash: crypto.createHash("sha256").update(source).digest("hex"),
  };
}

