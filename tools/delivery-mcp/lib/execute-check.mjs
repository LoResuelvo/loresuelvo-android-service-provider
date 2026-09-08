import { spawn } from "node:child_process";
import fs from "node:fs";
import fsPromises from "node:fs/promises";
import path from "node:path";
import { redactSecrets } from "./redact-secrets.mjs";
import { SAFE_COMMANDS } from "./policy-loader.mjs";
import { assertSafeRepoPath } from "./repo-root.mjs";
import {
  parseDiagnostics,
  extractLocations,
  stripAnsi,
  computeFailureSignature,
} from "./parse-diagnostics.mjs";

export { extractLocations, computeFailureSignature };

const ANSI_ESCAPE = /\u001b\[[0-?]*[ -/]*[@-~]/g;
const FAILURE_SIGNAL = /(?:error|failed|failure|expected|received|not found|timed? out|×|✗)/i;
const FEATURE_PATH = /^app\/src\/test\/resources\/features\/[A-Za-z0-9._/-]+\.feature$/;

function safeFeaturePath(repoRoot, value) {
  assertSafeRepoPath(repoRoot, String(value || ""), "Feature path");
  const normalized = String(value || "").replaceAll("\\", "/").replace(/^\.\//, "");
  if (!FEATURE_PATH.test(normalized)) {
    throw new Error(`Invalid feature path: ${value || "<empty>"}`);
  }
  const absolutePath = path.resolve(repoRoot, normalized);
  if (!fs.existsSync(absolutePath)) {
    throw new Error(`Feature path not found: ${normalized}`);
  }
  const realRepoRoot = fs.realpathSync(repoRoot);
  const realFeaturePath = fs.realpathSync(absolutePath);
  const relative = path.relative(realRepoRoot, realFeaturePath);
  if (relative.startsWith("..") || path.isAbsolute(relative)) {
    throw new Error(`Feature path resolves outside repository: ${normalized}`);
  }
  if (!fs.statSync(realFeaturePath).isFile()) {
    throw new Error(`Feature path is not a regular file: ${normalized}`);
  }
  return normalized;
}

function normalizedParameters(parameters, repoRoot) {
  const result = { ...parameters };
  if (parameters.featureFile) {
    result.featureFile = safeFeaturePath(repoRoot, parameters.featureFile);
  }
  if (Array.isArray(parameters.scopeFeatures)) {
    result.scopeFeatures = [...new Set(parameters.scopeFeatures.map((file) => safeFeaturePath(repoRoot, file)))].sort();
  }
  return result;
}

function requiredValuePresent(value) {
  return Array.isArray(value) ? value.length > 0 : Boolean(value);
}

function assertCommandAllowed(command, args, dynamicAllowlist = null) {
  const exactSig = JSON.stringify([command, ...args]);
  if (SAFE_COMMANDS.has(exactSig)) return;

  if (
    dynamicAllowlist === "focused_delivery_node_test" &&
    command === "node" &&
    args[0] === "--test" &&
    args.length > 1 &&
    args.slice(1).every((argument) =>
      /^tools\/delivery-mcp\/test\/[A-Za-z0-9._-]+\.test\.mjs$/.test(argument)
    )
  ) {
    return;
  }

  throw new Error(`Unsafe or unauthorized command rejected by allowlist: ${command} ${args.join(" ")}`);
}

export function resolveCheck({ checkId, definition, parameters = {}, repoRoot }) {
  if (!definition) throw new Error(`Unknown delivery check: ${checkId}`);
  const normalized = normalizedParameters(parameters, repoRoot);

  for (const required of definition.requires || []) {
    if (!requiredValuePresent(normalized[required])) {
      throw new Error(`Check ${checkId} requires parameter ${required}`);
    }
  }

  if (definition.kind !== "command") {
    return { id: checkId, ...definition, parameters: normalized };
  }

  const args = definition.args.map((argument) =>
    argument.replace(/\{([A-Za-z][A-Za-z0-9]*)\}/g, (match, key) => {
      const value = normalized[key];
      if (!requiredValuePresent(value) || Array.isArray(value)) {
        throw new Error(`Check ${checkId} cannot resolve parameter ${key}`);
      }
      return String(value);
    })
  );

  assertCommandAllowed(definition.command, args);

  return { id: checkId, ...definition, args, parameters: normalized };
}

export function summarizeFailureOutput(output, maxLines = 6) {
  const redacted = redactSecrets(String(output || ""));
  const lines = redacted
    .replace(ANSI_ESCAPE, "")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !/^at\s/.test(line) && !/^node:internal\//.test(line));
  const preferred = lines.filter((line) => FAILURE_SIGNAL.test(line));
  const source = preferred.length > 0 ? preferred : lines.slice(-maxLines);
  const unique = [];

  for (const line of source) {
    const compact = line.replace(/\s+/g, " ").slice(0, 300);
    if (compact && !unique.includes(compact)) unique.push(compact);
    if (unique.length >= maxLines) break;
  }
  return unique;
}

async function executeCommandCheck({ check, repoRoot, logPath, limits = {} }) {
  assertSafeRepoPath(repoRoot, logPath, "Log path");
  assertCommandAllowed(check.command, check.args, check.dynamicAllowlist);

  const absoluteLogPath = path.resolve(repoRoot, logPath);
  await fsPromises.mkdir(path.dirname(absoluteLogPath), { recursive: true });
  const realRepoRoot = fs.realpathSync(repoRoot);
  const realLogDirectory = fs.realpathSync(path.dirname(absoluteLogPath));
  const directoryRelative = path.relative(realRepoRoot, realLogDirectory);
  if (directoryRelative.startsWith("..") || path.isAbsolute(directoryRelative)) {
    throw new Error(`Log directory resolves outside repository: ${logPath}`);
  }
  if (fs.existsSync(absoluteLogPath)) {
    const realExistingLog = fs.realpathSync(absoluteLogPath);
    const logRelative = path.relative(realRepoRoot, realExistingLog);
    if (logRelative.startsWith("..") || path.isAbsolute(logRelative)) {
      throw new Error(`Log path resolves outside repository: ${logPath}`);
    }
  }
  const capturedChunks = [];
  let capturedBytes = 0;
  let outputTail = Buffer.alloc(0);
  let outputTruncated = false;
  const startedAt = Date.now();
  const maxLogBytes = limits.maxCheckLogBytes ?? 5242880;
  const maxSummaryLines = limits.maxFailureSummaryLines ?? 6;

  const child = spawn(check.command, check.args, {
    cwd: repoRoot,
    env: process.env,
    shell: false,
    stdio: ["ignore", "pipe", "pipe"],
    detached: process.platform !== "win32",
  });

  function capture(chunk) {
    const buffer = Buffer.from(chunk);
    outputTail = Buffer.concat([outputTail, buffer]).subarray(-20000);
    const remaining = maxLogBytes - capturedBytes;
    if (remaining > 0) {
      const captured = buffer.subarray(0, remaining);
      capturedChunks.push(captured);
      capturedBytes += captured.length;
    }
    if (buffer.length > remaining) outputTruncated = true;
  }

  child.stdout.on("data", capture);
  child.stderr.on("data", capture);

  let timedOut = false;
  let forceKillTimeout;
  function signalProcessTree(signal) {
    const pid = child.pid;
    if (!pid) return;

    if (process.platform !== "win32") {
      try {
        process.kill(-pid, signal);
      } catch {
        try {
          process.kill(pid, signal);
        } catch {
          // Process already exited.
        }
      }
    } else {
      try {
        process.kill(pid, signal);
      } catch {
        // Process already exited.
      }
    }
  }

  const timeout = setTimeout(() => {
    timedOut = true;
    signalProcessTree("SIGTERM");
    forceKillTimeout = setTimeout(() => signalProcessTree("SIGKILL"), 1500);
  }, check.timeoutMs);

  const outcome = await new Promise((resolve) => {
    let settled = false;
    const finish = (value) => {
      if (settled) return;
      settled = true;
      resolve(value);
    };
    child.on("error", (error) => finish({ exitCode: null, error }));
    child.on("close", (exitCode, signal) => finish({ exitCode, signal, error: null }));
  });
  clearTimeout(timeout);
  if (timedOut) signalProcessTree("SIGKILL");
  if (forceKillTimeout) clearTimeout(forceKillTimeout);

  const capturedOutput = Buffer.concat(capturedChunks).toString("utf8");
  const outputTailText = outputTail.toString("utf8");
  const safeCapturedOutput = redactSecrets(capturedOutput);
  const safeOutputTail = redactSecrets(outputTailText);
  const safeLog = outputTruncated
    ? `${safeCapturedOutput}\n[delivery runner truncated this log]\n[delivery runner output tail]\n${safeOutputTail}`
    : safeCapturedOutput;
  await fsPromises.writeFile(absoluteLogPath, safeLog, { flag: "wx", mode: 0o600 });

  const durationMs = Date.now() - startedAt;
  const passed = !timedOut && !outcome.error && outcome.exitCode === 0 && !outcome.signal;

  const parsedDiag = parseDiagnostics({
    check,
    command: check.command,
    args: check.args,
    output: safeCapturedOutput,
    outputTail: safeOutputTail,
    outputTruncated,
    exitCode: outcome.exitCode,
    signal: outcome.signal,
    timedOut,
    error: outcome.error,
    maxSummaryLines,
    maxLocations: maxSummaryLines,
  });

  const summaryLines = passed ? [] : parsedDiag.summaryLines;
  const locations = passed ? [] : parsedDiag.locations;
  const counts = parsedDiag.counts;
  const code = parsedDiag.code || (timedOut ? "CHECK_TIMEOUT" : outcome.error ? "CHECK_START_FAILED" : "CHECK_FAILED");
  const message = passed
    ? ""
    : redactSecrets(parsedDiag.message || summaryLines[0] || `${check.label} failed`);

  let file = undefined;
  let line = undefined;
  if (!passed && locations.length > 0) {
    const parts = locations[0].split(":");
    if (parts.length >= 2) {
      file = parts[0];
      const parsedLine = Number.parseInt(parts[1], 10);
      if (!Number.isNaN(parsedLine)) line = parsedLine;
    }
  }

  return {
    id: check.id,
    status: passed ? "passed" : "failed",
    durationMs,
    exitCode: outcome.exitCode,
    summaryLines,
    locations,
    counts,
    logPath,
    rawOutput: outputTruncated ? `${capturedOutput}\n${outputTailText}` : capturedOutput,
    outputTail: outputTailText,
    outputTruncated,
    code,
    message,
    diagnostic: passed
      ? null
      : {
          code,
          checkId: check.id,
          message,
          retryable: true,
          ...(file ? { file } : {}),
          ...(line !== undefined ? { line } : {}),
        },
  };
}

async function executeNoWipCheck({ check, repoRoot }) {
  const startedAt = Date.now();
  const findings = [];

  for (const featureFile of check.parameters.scopeFeatures) {
    assertSafeRepoPath(repoRoot, featureFile, "Feature scope file");
    const absolute = path.resolve(repoRoot, featureFile);
    const realRepoRoot = fs.realpathSync(repoRoot);
    const realFeature = fs.realpathSync(absolute);
    const relative = path.relative(realRepoRoot, realFeature);
    if (relative.startsWith("..") || path.isAbsolute(relative)) {
      return {
        id: check.id,
        status: "failed",
        durationMs: Date.now() - startedAt,
        exitCode: null,
        summaryLines: [`Feature scope file resolves outside repository: ${featureFile}`],
        locations: [featureFile],
        counts: { passed: 0, failed: 1, skipped: 0 },
        diagnostic: {
          code: "SCOPE_FILE_OUTSIDE_REPO",
          checkId: check.id,
          message: `Feature scope file resolves outside repository: ${featureFile}`,
          file: featureFile,
          retryable: false,
        },
      };
    }
    let source;
    try {
      source = await fsPromises.readFile(absolute, "utf8");
    } catch {
      return {
        id: check.id,
        status: "failed",
        durationMs: Date.now() - startedAt,
        exitCode: null,
        summaryLines: [`Feature scope file not found: ${featureFile}`],
        locations: [featureFile],
        counts: { passed: 0, failed: 1, skipped: 0 },
        diagnostic: {
          code: "SCOPE_FILE_MISSING",
          checkId: check.id,
          message: `Feature scope file not found: ${featureFile}`,
          file: featureFile,
          retryable: false,
        },
      };
    }

    source.split(/\r?\n/).forEach((line, index) => {
      if (/(?:^|\s)@wip(?:\s|$)/.test(line)) {
        findings.push({ file: featureFile, line: index + 1 });
      }
    });
  }

  const summaryLines = findings.slice(0, 6).map(({ file, line }) => `${file}:${line}: @wip remains in completed scope`);
  const locations = findings.map(({ file, line }) => `${file}:${line}`);
  const first = findings[0];
  return {
    id: check.id,
    status: findings.length === 0 ? "passed" : "failed",
    durationMs: Date.now() - startedAt,
    exitCode: findings.length === 0 ? 0 : 1,
    summaryLines,
    locations,
    counts: {
      passed: findings.length === 0 ? check.parameters.scopeFeatures.length : 0,
      failed: findings.length > 0 ? findings.length : 0,
      skipped: 0,
    },
    diagnostic: first
      ? {
          code: "WIP_TAG_IN_COMPLETED_SCOPE",
          checkId: check.id,
          message: `${findings.length} @wip tag(s) remain in completed scope`,
          file: first.file,
          line: first.line,
          retryable: false,
        }
      : null,
  };
}

export async function executeCheck({ check, repoRoot, logPath, limits }) {
  if (check.kind === "command") {
    return executeCommandCheck({ check, repoRoot, logPath, limits });
  }
  if (check.kind === "builtin" && check.handler === "no_wip_in_scope") {
    return executeNoWipCheck({ check, repoRoot });
  }
  throw new Error(`Unsupported delivery check: ${check.id}`);
}
