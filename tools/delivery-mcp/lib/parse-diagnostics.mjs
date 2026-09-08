import crypto from "node:crypto";
import { redactSecrets } from "./redact-secrets.mjs";

const ANSI_ESCAPE = /\u001b\[[0-?]*[ -/]*[@-~]|\u001b\].*?(?:\u0007|\u001b\\)/g;
const NOISE = [/^\s*at\s+/i, /^\s*node:internal\//i, /^\s*BUILD SUCCESSFUL/i, /^\s*Task :.*UP-TO-DATE/i];
const FAILURE_SIGNAL = /(?:error|failed|failure|exception|expected|received|not found|timed? out|cannot|unable|assertion|fatal)/i;
const LOCATION = /(?:^|[\s(])((?:[A-Za-z]:)?[A-Za-z0-9._/\\-]+\.(?:kt|java|feature|gradle(?:\.kts)?|properties|xml))(?::|\()([0-9]+)/g;

export function stripAnsi(text) {
  return typeof text === "string" ? text.replace(ANSI_ESCAPE, "") : "";
}

export function cleanLine(line) {
  return stripAnsi(String(line || "")).replace(/\s+/g, " ").trim();
}

export function isNoiseLine(line) {
  const value = cleanLine(line);
  return !value || /^[=-_*~#]{4,}$/.test(value) || NOISE.some((pattern) => pattern.test(value));
}

export function normalizePathLocation(rawPath) {
  let value = String(rawPath || "").replaceAll("\\", "/").replace(/^\.\//, "");
  for (const marker of ["/loresuelvo-android-service-provider/", "/loresuelvo-android-consumer/"]) {
    const index = value.lastIndexOf(marker);
    if (index >= 0) value = value.slice(index + marker.length);
  }
  return value;
}

export function extractLocations(text, max = 6) {
  const locations = [];
  const source = stripAnsi(String(text || ""));
  for (const line of source.split(/\r?\n/)) {
    LOCATION.lastIndex = 0;
    let match;
    while ((match = LOCATION.exec(line)) && locations.length < max) {
      const location = `${normalizePathLocation(match[1])}:${match[2]}`;
      if (!locations.includes(location)) locations.push(location);
    }
    if (locations.length >= max) break;
  }
  return locations;
}

export function deduplicateLines(lines, maxLines = 6) {
  const result = [];
  const seen = new Set();
  for (const line of lines || []) {
    const value = cleanLine(line);
    const key = value.toLowerCase();
    if (!value || isNoiseLine(value) || seen.has(key)) continue;
    seen.add(key);
    result.push(redactSecrets(value).slice(0, 300));
    if (result.length >= maxLines) break;
  }
  return result;
}

export function computeFailureSignature({ checkId, exitCode, message, locations }) {
  const raw = `${checkId || "unknown"}|${exitCode ?? "none"}|${String(message || "").trim().toLowerCase().replace(/\s+/g, " ")}|${[...(locations || [])].sort().join(";")}`;
  return crypto.createHash("sha256").update(raw).digest("hex");
}

function countsFromOutput(text, family) {
  const source = String(text || "");
  let passed = 0;
  let failed = 0;
  let skipped = 0;

  const junit = source.match(/(\d+)\s+tests?\s+(?:completed|found|run)[^\n]*(?:,\s*(\d+)\s+failed)?/i);
  if (junit) {
    const total = Number(junit[1]);
    failed = Number(junit[2] || 0);
    passed = Math.max(0, total - failed);
  }
  const passedMatch = source.match(/(?:tests?|scenarios?|steps?)\s+passed\s*[:=]?\s*(\d+)/i) || source.match(/(\d+)\s+(?:tests?|scenarios?|steps?)\s+passed/i);
  const failedMatch = source.match(/(?:tests?|scenarios?|steps?)\s+failed\s*[:=]?\s*(\d+)/i) || source.match(/(\d+)\s+(?:tests?|scenarios?|steps?)\s+failed/i);
  const skippedMatch = source.match(/(?:tests?|scenarios?|steps?)\s+(?:skipped|ignored)\s*[:=]?\s*(\d+)/i);
  if (passedMatch) passed = Number(passedMatch[1]);
  if (failedMatch) failed = Number(failedMatch[1]);
  if (skippedMatch) skipped = Number(skippedMatch[1]);
  const cucumber = source.match(/(\d+)\s+scenarios?\s*\([^)]*failed[^)]*\)/i);
  if (cucumber && failed === 0) failed = Number(cucumber[1]);
  if (family === "instrumented" && /FAILURES!!!/i.test(source) && failed === 0) failed = 1;
  return { passed, failed, skipped };
}

export function parseAndroidDiagnostics(output, { maxSummaryLines = 6, maxLocations = 6, family = "android" } = {}) {
  const clean = stripAnsi(String(output || ""));
  const lines = clean.split(/\r?\n/).map(cleanLine).filter((line) => line && !isNoiseLine(line));
  const preferred = lines.filter((line) => FAILURE_SIGNAL.test(line));
  const summaryLines = deduplicateLines(preferred.length ? preferred : lines.slice(-maxSummaryLines), maxSummaryLines);
  return {
    family,
    locations: extractLocations(clean, maxLocations),
    summaryLines,
    message: summaryLines[0] || `${family} check failed`,
    counts: countsFromOutput(clean, family),
  };
}

export function parseKotlinDiagnostics(output, options = {}) {
  return parseAndroidDiagnostics(output, { ...options, family: "kotlin" });
}

export function parseGradleDiagnostics(output, options = {}) {
  return parseAndroidDiagnostics(output, { ...options, family: "gradle" });
}

export function parseAndroidLintDiagnostics(output, options = {}) {
  return parseAndroidDiagnostics(output, { ...options, family: "android_lint" });
}

export function parseJUnitDiagnostics(output, options = {}) {
  return parseAndroidDiagnostics(output, { ...options, family: "junit4" });
}

export function parseCucumberJvmDiagnostics(output, options = {}) {
  return parseAndroidDiagnostics(output, { ...options, family: "cucumber_jvm" });
}

export function parseNodeTestDiagnostics(output, options = {}) {
  return parseAndroidDiagnostics(output, { ...options, family: "node_test" });
}

export function parseGenericDiagnostics(output, options = {}) {
  return parseAndroidDiagnostics(output, { ...options, family: "generic" });
}

// Backwards-compatible names are harmless and keep generic callers typed.
export const parseCucumberDiagnostics = parseCucumberJvmDiagnostics;
export const parseTscDiagnostics = parseKotlinDiagnostics;

export function detectDiagnosticFamily({ check, command = "", args = [], output = "" } = {}) {
  const id = String(check?.id || "");
  const argString = args.join(" ");
  if (id === "delivery_unit" || (command === "npm" && argString.includes("delivery-mcp"))) return "node_test";
  if (/^lint_/i.test(id) || /\blint\b/i.test(argString)) return "android_lint";
  if (/^e2e_/i.test(id) || /connected.*AndroidTest/i.test(argString)) return "instrumented";
  if (/^build_/i.test(id) || /assemble.*Debug/i.test(argString)) return "gradle_build";
  if (/^jvm_test_/i.test(id) || /test.*DebugUnitTest/i.test(argString)) return "junit4";
  if (/cucumber|scenario|steps/i.test(argString)) return "cucumber_jvm";
  if (/FAILURES!!!|Execution failed for task :app:/i.test(output)) return "gradle";
  if (/e:\s+[^\n]+\.kt:\d+|\.kt:\d+:\d+:/i.test(output)) return "kotlin";
  return "generic";
}

export function parseDiagnostics({
  check,
  command,
  args,
  output,
  outputTail = "",
  outputTruncated = false,
  exitCode = 0,
  signal = null,
  timedOut = false,
  error = null,
  maxSummaryLines = 6,
  maxLocations = 6,
} = {}) {
  const main = Buffer.isBuffer(output) ? output.toString("utf8") : String(output || "");
  const tail = Buffer.isBuffer(outputTail) ? outputTail.toString("utf8") : String(outputTail || "");
  const text = outputTruncated && tail ? `${main}\n${tail}` : main;
  const passed = !timedOut && !error && exitCode === 0 && !signal;
  const family = detectDiagnosticFamily({ check, command, args, output: text });
  if (passed) {
    const counts = countsFromOutput(text, family);
    return {
      family,
      passed: true,
      code: null,
      message: null,
      summaryLines: [],
      locations: [],
      counts: counts.passed || counts.failed || counts.skipped ? counts : { passed: 1, failed: 0, skipped: 0 },
    };
  }
  if (timedOut) {
    const message = `${check?.label || check?.id || "Command"} timed out`;
    return { family, passed: false, code: "CHECK_TIMEOUT", message, summaryLines: [message], locations: [], counts: { passed: 0, failed: 1, skipped: 0 } };
  }
  if (error) {
    const message = cleanLine(error.message || "Process failed to start");
    return { family, passed: false, code: "CHECK_START_FAILED", message, summaryLines: [message], locations: [], counts: { passed: 0, failed: 1, skipped: 0 } };
  }

  let parsed;
  switch (family) {
    case "android_lint": parsed = parseAndroidLintDiagnostics(text, { maxSummaryLines, maxLocations }); break;
    case "junit4": parsed = parseJUnitDiagnostics(text, { maxSummaryLines, maxLocations }); break;
    case "cucumber_jvm": parsed = parseCucumberJvmDiagnostics(text, { maxSummaryLines, maxLocations }); break;
    case "kotlin": parsed = parseKotlinDiagnostics(text, { maxSummaryLines, maxLocations }); break;
    default: parsed = parseAndroidDiagnostics(text, { maxSummaryLines, maxLocations, family }); break;
  }
  const summaryLines = parsed.summaryLines.length ? parsed.summaryLines : [`Process exited with code ${exitCode ?? 1}`];
  const message = redactSecrets(cleanLine(parsed.message || summaryLines[0])).slice(0, 300);
  let code = "CHECK_FAILED";
  if (family === "android_lint") code = "ANDROID_LINT_FAILED";
  else if (family === "junit4" || family === "cucumber_jvm") code = "JVM_TEST_FAILED";
  else if (family === "instrumented") code = "INSTRUMENTED_TEST_FAILED";
  else if (family === "gradle_build") code = "ANDROID_BUILD_FAILED";
  else if (family === "node_test") code = "DELIVERY_TEST_FAILED";
  return {
    family,
    passed: false,
    code,
    message,
    summaryLines: summaryLines.slice(0, maxSummaryLines).map((line) => redactSecrets(cleanLine(line)).slice(0, 300)),
    locations: parsed.locations.slice(0, maxLocations),
    counts: parsed.counts || { passed: 0, failed: 1, skipped: 0 },
  };
}

