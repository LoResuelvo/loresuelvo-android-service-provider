import crypto from "node:crypto";
import fs from "node:fs";
import fsPromises from "node:fs/promises";
import path from "node:path";
import { findRepoRoot, assertSafeRepoPath } from "./repo-root.mjs";
import { loadDeliveryPolicy } from "./policy-loader.mjs";
import { redactSecrets } from "./redact-secrets.mjs";
import { executeCheck, resolveCheck, computeFailureSignature } from "./execute-check.mjs";
import { parsePorcelainStatus, runGit } from "./git-snapshot.mjs";
import { createDeliveryJob, findActiveDeliveryJob, spawnJobWorker } from "./jobs.mjs";
import { normalizePath } from "./classify-files.mjs";

const ANDROID_TEST_EXTENSION = /Test\.kt$/;
const DELIVERY_TEST_EXTENSION = /\.test\.mjs$/;
const FEATURE_PATH_REGEX = /^app\/src\/test\/resources\/features\/[A-Za-z0-9._/-]+\.feature$/;
const SAFE_PATH_CHARS = /^[A-Za-z0-9._/-]+$/;
const DEFAULT_TEST_TIMEOUT_MS = 900000;
const TDD_RUNTIME_DIR = ".delivery/runtime/tdd";
const EXECUTION_MODES = new Set(["sync", "job", "auto"]);

export function getTestExtension(filePath) {
  const normalized = normalizePath(filePath);
  if (ANDROID_TEST_EXTENSION.test(normalized)) return ".kt";
  if (DELIVERY_TEST_EXTENSION.test(normalized) && normalized.startsWith("tools/delivery-mcp/test/")) return ".test.mjs";
  return null;
}

function pathError(code, message) {
  const error = new Error(message);
  error.code = code;
  return error;
}

function assertRegularRepoFile(repoRoot, normalized, label, notFoundCode) {
  assertSafeRepoPath(repoRoot, normalized, label);
  const absolute = path.resolve(repoRoot, normalized);
  if (!fs.existsSync(absolute)) throw pathError(notFoundCode, `${label} not found: ${normalized}`);
  let real;
  try {
    real = fs.realpathSync(absolute);
  } catch {
    throw pathError("INVALID_TEST_FILE", `${label} cannot be resolved: ${normalized}`);
  }
  const repoReal = fs.realpathSync(repoRoot);
  const relative = path.relative(repoReal, real);
  if (relative.startsWith("..") || path.isAbsolute(relative)) {
    throw pathError("PATH_OUTSIDE_REPO", `${label} resolves outside repository: ${normalized}`);
  }
  if (!fs.statSync(real).isFile()) throw pathError("INVALID_TEST_FILE", `${label} is not a regular file: ${normalized}`);
  return normalized;
}

export function validateTestFilePath(repoRoot, filePath) {
  if (typeof filePath !== "string" || !filePath.trim()) throw pathError("INVALID_TEST_FILE", "Test file path cannot be empty");
  const normalized = normalizePath(filePath);
  if (normalized.startsWith("-") || /[\*\?\[\]\{\}]/.test(normalized) || normalized.split("/").includes("..")) {
    throw pathError(normalized.split("/").includes("..") ? "PATH_TRAVERSAL" : "INVALID_TEST_FILE", `Invalid test file path: ${filePath}`);
  }
  if (!SAFE_PATH_CHARS.test(normalized)) throw pathError("INVALID_TEST_FILE", `Invalid characters in test path: ${filePath}`);
  if (!getTestExtension(normalized)) {
    throw pathError("INVALID_TEST_FILE", "Only Kotlin files ending in Test.kt or delivery .test.mjs files are supported");
  }
  return assertRegularRepoFile(repoRoot, normalized, "Test file path", "TEST_FILE_NOT_FOUND");
}

export function validateFeatureFilePath(repoRoot, filePath) {
  if (typeof filePath !== "string" || !filePath.trim()) throw pathError("INVALID_FEATURE_FILE", "Feature file path cannot be empty");
  const normalized = normalizePath(filePath);
  if (normalized.startsWith("-") || /[\*\?\[\]\{\}]/.test(normalized) || normalized.split("/").includes("..")) {
    throw pathError(normalized.split("/").includes("..") ? "PATH_TRAVERSAL" : "INVALID_FEATURE_FILE", `Invalid feature file path: ${filePath}`);
  }
  if (!FEATURE_PATH_REGEX.test(normalized) || !SAFE_PATH_CHARS.test(normalized)) {
    throw pathError("INVALID_FEATURE_FILE", `Feature path must be under app/src/test/resources/features/: ${filePath}`);
  }
  return assertRegularRepoFile(repoRoot, normalized, "Feature file path", "FEATURE_FILE_NOT_FOUND");
}

export function scenarioHasWipTag(repoRoot, featureFile, scenarioName) {
  if (!scenarioName) return false;
  const source = fs.readFileSync(path.resolve(repoRoot, featureFile), "utf8");
  let pending = [];
  let featureTags = [];
  let seenFeature = false;
  for (const raw of source.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    if (line.startsWith("@")) {
      pending.push(...line.split(/\s+/).filter((tag) => tag.startsWith("@")));
      continue;
    }
    const feature = line.match(/^(?:Feature|Funcionalidad):\s*(.+)$/i);
    if (feature) {
      featureTags = pending;
      pending = [];
      seenFeature = true;
      continue;
    }
    const scenario = line.match(/^(?:Scenario|Scenario Outline|Escenario|Esquema del escenario):\s*(.+)$/i);
    if (scenario) {
      const match = seenFeature && scenario[1].trim() === scenarioName;
      const result = featureTags.includes("@wip") || pending.includes("@wip");
      pending = [];
      if (match) return result;
      continue;
    }
    if (/^(?:Rule|Background|Regla|Antecedentes):/i.test(line)) pending = [];
    else pending = [];
  }
  return false;
}

export function validateScenarioName(name) {
  if (!name) return null;
  if (typeof name !== "string" || /[\r\n\0]/.test(name)) throw pathError("INVALID_SCENARIO_NAME", "Scenario name contains illegal control characters");
  const value = name.trim();
  if (!value) return null;
  if (value.length > 500 || /[\$\x60\\"]/.test(value)) throw pathError("INVALID_SCENARIO_NAME", "Scenario name contains unsafe characters or exceeds 500 characters");
  return value;
}

export function parseTestCounts(output) {
  const text = String(output || "");
  let passed = 0;
  let failed = 0;
  let skipped = 0;
  let found = false;
  const nodePass = text.match(/ℹ\s+pass\s+(\d+)/);
  const nodeFail = text.match(/ℹ\s+fail\s+(\d+)/);
  const nodeSkip = text.match(/ℹ\s+(?:skipped|cancelled)\s+(\d+)/);
  if (nodePass || nodeFail) {
    passed = Number(nodePass?.[1] || 0);
    failed = Number(nodeFail?.[1] || 0);
    skipped = Number(nodeSkip?.[1] || 0);
    found = true;
  }
  const gradle = text.match(/(\d+)\s+tests?\s+(?:completed|found|run)/i);
  if (gradle) {
    const total = Number(gradle[1]);
    const failedMatch = text.match(/(\d+)\s+failed/i);
    failed = Number(failedMatch?.[1] || 0);
    passed = Math.max(0, total - failed);
    found = true;
  }
  const cucumber = text.match(/(\d+)\s+scenarios?\s*\(([^)]*)\)/i);
  if (cucumber) {
    const details = cucumber[2];
    const p = details.match(/(\d+)\s+passed/i);
    const f = details.match(/(\d+)\s+failed/i);
    const s = details.match(/(\d+)\s+(?:skipped|pending|undefined)/i);
    passed = Number(p?.[1] || 0);
    failed = Number(f?.[1] || 0);
    skipped = Number(s?.[1] || 0);
    found = true;
  }
  return { found, counts: { passed, failed, skipped } };
}

export function computeFileHash(absolutePath) {
  try {
    return crypto.createHash("sha256").update(fs.readFileSync(absolutePath)).digest("hex");
  } catch {
    return null;
  }
}

export function computeTddCacheKey(identity) {
  return crypto.createHash("sha256").update(JSON.stringify(identity)).digest("hex");
}

function cachePath(repoRoot, cacheKey) {
  const relative = path.posix.join(TDD_RUNTIME_DIR, "cache", `${cacheKey}.json`);
  assertSafeRepoPath(repoRoot, relative, "TDD cache path");
  return path.resolve(repoRoot, relative);
}

async function readCache(repoRoot, cacheKey) {
  try {
    const value = JSON.parse(await fsPromises.readFile(cachePath(repoRoot, cacheKey), "utf8"));
    return value && value.cached === false ? { ...value, cached: true } : value;
  } catch (error) {
    if (error.code === "ENOENT" || error instanceof SyntaxError) return null;
    throw error;
  }
}

async function writeCache(repoRoot, cacheKey, value) {
  const target = cachePath(repoRoot, cacheKey);
  await fsPromises.mkdir(path.dirname(target), { recursive: true, mode: 0o700 });
  const temp = `${target}.${process.pid}.${Date.now()}.tmp`;
  await fsPromises.writeFile(temp, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
  await fsPromises.rename(temp, target);
}

async function inputFingerprint(repoRoot, paths = []) {
  const entries = [];
  for (const relative of [...new Set(paths)].sort()) {
    entries.push({ path: relative, hash: computeFileHash(path.resolve(repoRoot, relative)) || "MISSING" });
  }
  const status = await runGit(["status", "--porcelain", "-z", "--untracked-files=all"], repoRoot);
  const rawStatus = status.error ? `ERROR:${status.error.message}` : status.stdout.toString("utf8");
  const head = await runGit(["rev-parse", "HEAD"], repoRoot);
  return computeTddCacheKey({
    head: head.error ? "NO_GIT_HEAD" : head.stdout.toString("utf8").trim(),
    status: crypto.createHash("sha256").update(rawStatus).digest("hex"),
    entries,
  });
}

export function shouldUseDeliveryTestJob({ mode, executionMode = "auto", diagnosticTimeoutMs = 0, workerJobId = null, executeDefault = true } = {}) {
  if (workerJobId) return false;
  if (executionMode === "job") return true;
  if (executionMode !== "auto" || !executeDefault) return false;
  return mode === "affected" || mode === "scenario" || (mode === "diagnostic" && Number(diagnosticTimeoutMs) > DEFAULT_TEST_TIMEOUT_MS);
}

function emptyResult(mode, diagnostics = [], extra = {}) {
  return {
    status: "error",
    mode,
    cached: false,
    durationMs: 0,
    counts: { passed: 0, failed: 0, skipped: 0 },
    diagnostics,
    ...extra,
  };
}

function resultFromExecution({ mode, execResult, logPath, checkId = null }) {
  const passed = Boolean(execResult?.passed);
  const counts = execResult?.counts || parseTestCounts(execResult?.rawOutput).counts;
  const message = passed ? "" : redactSecrets(execResult?.message || execResult?.summaryLines?.[0] || `${mode} execution failed`);
  const failure = passed ? null : {
    signature: computeFailureSignature({ checkId: checkId || mode, exitCode: execResult?.exitCode, message, locations: execResult?.locations || [] }),
    code: execResult?.code || (execResult?.timedOut ? "CHECK_TIMEOUT" : "TEST_FAILED"),
    checkId: checkId || mode,
    message,
    summaryLines: (execResult?.summaryLines || [message]).slice(0, 6),
    locations: (execResult?.locations || []).slice(0, 6),
    exitCode: execResult?.exitCode ?? null,
    logPath: execResult?.logPath || logPath,
  };
  return {
    status: passed ? "passed" : "failed",
    mode,
    ...(checkId ? { checkId } : {}),
    cached: false,
    durationMs: execResult?.durationMs || 0,
    counts: counts || { passed: passed ? 1 : 0, failed: passed ? 0 : 1, skipped: 0 },
    logPath: execResult?.logPath || logPath,
    ...(failure ? { failure, diagnostics: [{ code: failure.code, message: failure.message, retryable: true }] } : { diagnostics: [] }),
  };
}

async function enqueue({ repoRoot, mode, executionMode, params, cacheKey }) {
  const runKey = `delivery-test-${cacheKey}`;
  const active = await findActiveDeliveryJob({ repoRoot, runKey });
  if (active) {
    return {
      status: "running",
      mode,
      executionMode: "job",
      jobId: active.jobId,
      cached: false,
      durationMs: 0,
      counts: { passed: 0, failed: 0, skipped: 0 },
      diagnostics: [],
      message: `delivery_test job '${active.jobId}' is already running. Use delivery_job_wait to await completion.`,
    };
  }
  const job = await createDeliveryJob({ repoRoot, type: "test", params: { ...params, executionMode: "sync", async: false }, runKey, snapshotHash: cacheKey, gateId: null });
  await spawnJobWorker({ repoRoot, jobId: job.jobId });
  return {
    status: "job_started",
    mode,
    executionMode: "job",
    jobId: job.jobId,
    cached: false,
    durationMs: 0,
    counts: { passed: 0, failed: 0, skipped: 0 },
    diagnostics: [],
    message: `Execution for delivery_test mode '${mode}' started as recoverable job '${job.jobId}'. Use delivery_job_wait to await completion.`,
  };
}

async function executeDeliveryCheck({ repoRoot, checkId, parameters = {}, executeFn = null, timeoutMs, mode }) {
  const policy = await loadDeliveryPolicy({ repoRoot });
  const definition = policy.checkCatalog?.[checkId];
  if (!definition) return emptyResult(mode, [{ code: "UNKNOWN_CHECK", message: `Unknown checkId '${checkId}'`, retryable: false }], { checkId });
  let check;
  try {
    check = resolveCheck({ checkId, definition, parameters, repoRoot });
  } catch (error) {
    return emptyResult(mode, [{ code: error.code || "CHECK_INPUT_INVALID", message: redactSecrets(error.message), retryable: false }], { checkId });
  }

  const logId = crypto.randomUUID?.().slice(0, 8) || Date.now().toString(36);
  const logPath = path.posix.join(TDD_RUNTIME_DIR, "logs", `${mode}-${checkId}-${logId}.log`);
  let result;
  if (executeFn) {
    result = await executeFn({ command: check.command, args: check.args, cwd: repoRoot, timeoutMs: check.timeoutMs || timeoutMs, logPath, check });
  } else {
    const outcome = await executeCheck({ check, repoRoot, logPath, limits: policy.limits });
    result = {
      passed: outcome.status === "passed",
      durationMs: outcome.durationMs,
      exitCode: outcome.exitCode,
      summaryLines: outcome.summaryLines,
      locations: outcome.locations,
      rawOutput: outcome.rawOutput || "",
      counts: outcome.counts,
      timedOut: outcome.diagnostic?.code === "CHECK_TIMEOUT",
      code: outcome.code || outcome.diagnostic?.code,
      message: outcome.message || outcome.diagnostic?.message,
      logPath: outcome.logPath || logPath,
    };
  }
  return resultFromExecution({ mode, execResult: result, logPath, checkId });
}

async function executeNodeDeliveryTests({ repoRoot, testFiles, executeFn = null, timeoutMs, mode }) {
  const policy = await loadDeliveryPolicy({ repoRoot });
  const logId = crypto.randomUUID?.().slice(0, 8) || Date.now().toString(36);
  const logPath = path.posix.join(TDD_RUNTIME_DIR, "logs", `${mode}-delivery-unit-${logId}.log`);
  const check = {
    id: "delivery_unit",
    kind: "command",
    label: "Focused delivery tooling tests",
    command: "node",
    args: ["--test", ...testFiles],
    dynamicAllowlist: "focused_delivery_node_test",
    display: `node --test ${testFiles.join(" ")}`,
    timeoutMs,
  };
  let result;
  if (executeFn) {
    result = await executeFn({ command: check.command, args: check.args, cwd: repoRoot, timeoutMs, logPath, check });
  } else {
    const outcome = await executeCheck({ check, repoRoot, logPath, limits: policy.limits });
    result = {
      passed: outcome.status === "passed",
      durationMs: outcome.durationMs,
      exitCode: outcome.exitCode,
      summaryLines: outcome.summaryLines,
      locations: outcome.locations,
      rawOutput: outcome.rawOutput || "",
      counts: outcome.counts,
      timedOut: outcome.diagnostic?.code === "CHECK_TIMEOUT",
      code: outcome.code || outcome.diagnostic?.code,
      message: outcome.message || outcome.diagnostic?.message,
      logPath: outcome.logPath || logPath,
    };
  }
  return resultFromExecution({ mode, execResult: result, logPath, checkId: "delivery_unit" });
}

export async function testDelivery({
  repoRoot,
  mode = "affected",
  executionMode = "auto",
  async: isAsync = false,
  testFiles = [],
  featureFile = "",
  scenarioName = "",
  checkId = "",
  force = false,
  timeoutMs = DEFAULT_TEST_TIMEOUT_MS,
  executeFn = null,
  workerJobId = null,
} = {}) {
  const root = findRepoRoot(repoRoot);
  if (!EXECUTION_MODES.has(executionMode)) {
    return emptyResult(mode, [{ code: "INVALID_EXECUTION_MODE", message: `Unknown executionMode: '${executionMode}'`, retryable: false }], { executionMode });
  }

  const files = Array.isArray(testFiles) ? testFiles : [];
  const validFiles = [];
  for (const file of files) {
    try {
      validFiles.push(validateTestFilePath(root, file));
    } catch (error) {
      return emptyResult(mode, [{ code: error.code || "INVALID_TEST_FILE", message: redactSecrets(error.message), retryable: false }]);
    }
  }

  if (mode === "unit") {
    const deliveryFiles = validFiles.filter((file) => file.endsWith(".test.mjs"));
    const kotlinFiles = validFiles.filter((file) => file.endsWith(".kt"));
    if (deliveryFiles.length && kotlinFiles.length) {
      return emptyResult("unit", [{ code: "MIXED_TEST_RUNTIMES", message: "Kotlin and Node delivery tests must be run separately", retryable: false }]);
    }
    const selectedCheck = deliveryFiles.length ? "node_test" : "jvm_test_dev";
    const policy = await loadDeliveryPolicy({ repoRoot: root });
    const cacheKey = computeTddCacheKey({ mode, selectedCheck, files: validFiles, timeoutMs, policyHash: policy.sourceHash, fingerprint: await inputFingerprint(root, validFiles) });
    if (!force) {
      const cached = await readCache(root, cacheKey);
      if (cached) return { ...cached, mode: "unit" };
    }
    if (isAsync || shouldUseDeliveryTestJob({ mode, executionMode, workerJobId, executeDefault: !executeFn })) {
      return enqueue({ repoRoot: root, mode: "unit", executionMode, params: { mode: "unit", testFiles: validFiles, force, timeoutMs }, cacheKey });
    }
    const result = deliveryFiles.length
      ? await executeNodeDeliveryTests({ repoRoot: root, testFiles: deliveryFiles, executeFn, timeoutMs, mode: "unit" })
      : await executeDeliveryCheck({ repoRoot: root, checkId: selectedCheck, executeFn, timeoutMs, mode: "unit" });
    await writeCache(root, cacheKey, result);
    return result;
  }

  if (mode === "scenario") {
    if (!featureFile) return emptyResult("scenario", [{ code: "MISSING_PARAMETER", message: "Mode 'scenario' requires featureFile", retryable: false }]);
    let feature;
    try {
      feature = validateFeatureFilePath(root, featureFile);
      validateScenarioName(scenarioName);
    } catch (error) {
      return emptyResult("scenario", [{ code: error.code || "INVALID_FEATURE_FILE", message: redactSecrets(error.message), retryable: false }]);
    }
    const policy = await loadDeliveryPolicy({ repoRoot: root });
    const cacheKey = computeTddCacheKey({ mode, featureFile: feature, scenarioName: scenarioName || null, timeoutMs, policyHash: policy.sourceHash, fingerprint: await inputFingerprint(root, [feature]) });
    if (!force) {
      const cached = await readCache(root, cacheKey);
      if (cached) return { ...cached, mode: "scenario" };
    }
    if (isAsync || shouldUseDeliveryTestJob({ mode, executionMode, workerJobId, executeDefault: !executeFn })) {
      return enqueue({ repoRoot: root, mode, executionMode, params: { mode, featureFile: feature, scenarioName: scenarioName || "", force, timeoutMs }, cacheKey });
    }
    // Focused Cucumber JVM execution is intentionally unavailable; run full Dev JVM task.
    const result = await executeDeliveryCheck({ repoRoot: root, checkId: "jvm_test_dev", executeFn, timeoutMs, mode });
    await writeCache(root, cacheKey, result);
    return result;
  }

  if (mode === "diagnostic") {
    if (!checkId) return emptyResult("diagnostic", [{ code: "MISSING_PARAMETER", message: "Mode 'diagnostic' requires checkId", retryable: false }]);
    const policy = await loadDeliveryPolicy({ repoRoot: root });
    const definition = policy.checkCatalog?.[checkId];
    if (!definition) return emptyResult("diagnostic", [{ code: "UNKNOWN_CHECK", message: `Unknown checkId '${checkId}'`, retryable: false }], { checkId });
    const parameters = featureFile ? { featureFile } : {};
    let resolved;
    try {
      resolved = resolveCheck({ checkId, definition, parameters, repoRoot: root });
    } catch (error) {
      return emptyResult("diagnostic", [{ code: error.code || "CHECK_INPUT_INVALID", message: redactSecrets(error.message), retryable: false }], { checkId });
    }
    const cacheKey = computeTddCacheKey({ mode, checkId, args: resolved.args || [], policyHash: policy.sourceHash, fingerprint: await inputFingerprint(root, []) });
    if (!force) {
      const cached = await readCache(root, cacheKey);
      if (cached) return { ...cached, mode, checkId };
    }
    if (isAsync || shouldUseDeliveryTestJob({ mode, executionMode, diagnosticTimeoutMs: resolved.timeoutMs, workerJobId, executeDefault: !executeFn })) {
      return enqueue({ repoRoot: root, mode, executionMode, params: { mode, checkId, featureFile, force, timeoutMs }, cacheKey });
    }
    const result = await executeDeliveryCheck({ repoRoot: root, checkId, parameters, executeFn, timeoutMs, mode });
    await writeCache(root, cacheKey, result);
    return result;
  }

  const status = await runGit(["status", "--porcelain", "-z", "--untracked-files=all"], root);
  if (status.error) return emptyResult("affected", [{ code: "GIT_STATUS_FAILED", message: redactSecrets(status.error.message || "Unable to read Git status"), retryable: true }]);
  const parsed = parsePorcelainStatus(status.stdout);
  const changed = [...new Set([...parsed.staged, ...parsed.unstaged].map((entry) => entry.file || entry).concat(parsed.untracked))].filter((file) => !file.startsWith(".delivery/runtime/"));
  if (!changed.length) return { status: "passed", mode: "affected", cached: false, durationMs: 0, counts: { passed: 0, failed: 0, skipped: 0 }, diagnostics: [{ code: "NO_CHANGES", message: "Working tree has no changes to test", retryable: false }] };

  const policy = await loadDeliveryPolicy({ repoRoot: root });
  const cacheKey = computeTddCacheKey({ mode: "affected", changed: changed.sort(), policyHash: policy.sourceHash, fingerprint: await inputFingerprint(root, changed) });
  if (!force) {
    const cached = await readCache(root, cacheKey);
    if (cached) return { ...cached, mode: "affected" };
  }
  if (isAsync || shouldUseDeliveryTestJob({ mode: "affected", executionMode, workerJobId, executeDefault: !executeFn })) {
    return enqueue({ repoRoot: root, mode: "affected", executionMode, params: { mode: "affected", force, timeoutMs }, cacheKey });
  }
  const result = await executeDeliveryCheck({ repoRoot: root, checkId: "jvm_test_dev", executeFn, timeoutMs, mode: "affected" });
  await writeCache(root, cacheKey, result);
  return result;
}
