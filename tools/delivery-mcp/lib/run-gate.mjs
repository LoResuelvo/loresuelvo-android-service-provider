import crypto from "node:crypto";
import path from "node:path";
import { executeCheck as executeCheckDefault, resolveCheck } from "./execute-check.mjs";
import {
  acquireRunLock,
  computeRunKey,
  createRunArtifacts,
  loadCachedFailure,
  loadCachedSuccess,
  saveRunEvidence,
} from "./delivery-evidence.mjs";
import { validateExecutionResult } from "./validate-schema.mjs";

function computeFailureSignature({ checkId, exitCode, message, locations }) {
  const normalizedMessage = (message || "").trim().toLowerCase().replace(/\s+/g, " ");
  const normalizedLocations = [...(locations || [])].sort().join(";");
  const raw = `${checkId}|${exitCode ?? "none"}|${normalizedMessage}|${normalizedLocations}`;
  return crypto.createHash("sha256").update(raw).digest("hex");
}

function checkResultForOutput(result) {
  const output = {
    id: result.id,
    status: result.status,
    durationMs: result.durationMs,
    exitCode: result.exitCode ?? null,
    summaryLines: result.summaryLines || [],
  };
  if (result.logPath) output.logPath = result.logPath;
  if (result.counts) output.counts = result.counts;
  return output;
}

function baseResult({ inspection, runKey, status, diagnostics = [] }) {
  return {
    schemaVersion: 1,
    status,
    snapshotHash: inspection.snapshotHash,
    runKey,
    cached: false,
    policy: inspection.policy,
    gate: {
      id: inspection.gate.id,
      reasonCodes: inspection.gate.reasonCodes,
      checkIds: inspection.gate.checkIds,
      parameters: inspection.gate.parameters,
      postPushChecks: inspection.gate.postPushChecks,
    },
    summary: { passed: 0, failed: 0, skipped: inspection.gate.checkIds.length, durationMs: 0 },
    checks: [],
    diagnostics,
    evidence: { recordPath: null },
  };
}

function resolveGateChecks({ inspection, policy, repoRoot }) {
  return inspection.gate.checkIds.map((checkId) =>
    resolveCheck({
      checkId,
      definition: policy.checkCatalog[checkId],
      parameters: inspection.gate.parameters,
      repoRoot,
    })
  );
}

export async function runGate({
  inspection,
  snapshot,
  policy,
  repoRoot,
  review = { status: "not_required" },
  executeCheck = executeCheckDefault,
  force = false,
} = {}) {
  const runKey = computeRunKey({ inspection, snapshot });
  let checks;

  try {
    checks = resolveGateChecks({ inspection, policy, repoRoot });
  } catch (error) {
    const res = baseResult({
      inspection,
      runKey,
      status: "needs_input",
      diagnostics: [
        ...inspection.diagnostics,
        {
          code: "CHECK_INPUT_INVALID",
          message: String(error.message || "Invalid gate input").split("\n")[0],
          retryable: false,
        },
      ].slice(0, policy.limits.maxDiagnostics),
    });
    validateExecutionResult(res, repoRoot);
    return res;
  }

  // 1. Re-use cached green evidence on identical snapshot
  if (!force) {
    const cachedSuccess = await loadCachedSuccess({
      repoRoot,
      runKey,
      cacheable: snapshot.cacheable,
    });
    if (cachedSuccess) return cachedSuccess;
  }

  // 2. Re-use cached identical failure on identical snapshot (Criterion 19 & 20)
  if (!force) {
    const cachedFailure = await loadCachedFailure({
      repoRoot,
      runKey,
      cacheable: snapshot.cacheable,
    });
    if (cachedFailure) {
      if (cachedFailure.failure) {
        cachedFailure.failure.attemptCount = (cachedFailure.failure.attemptCount || 1) + 1;
        await saveRunEvidence({ repoRoot, result: cachedFailure, cacheable: snapshot.cacheable });
      }
      return { ...cachedFailure, cached: true };
    }
  }

  // 3. Acquire run lock to prevent concurrent runs
  let releaseLock;
  try {
    releaseLock = await acquireRunLock({ repoRoot, runKey });
  } catch (error) {
    const res = baseResult({
      inspection,
      runKey,
      status: "blocked",
      diagnostics: [
        ...inspection.diagnostics,
        {
          code: error.code || "DELIVERY_LOCK_FAILED",
          message: String(error.message || "Unable to acquire delivery run lock").split("\n")[0],
          retryable: true,
        },
      ].slice(0, policy.limits.maxDiagnostics),
    });
    validateExecutionResult(res, repoRoot);
    return res;
  }

  try {
    const artifacts = await createRunArtifacts({ repoRoot, runKey });
    const startedAt = Date.now();
    const completedChecks = [];
    let failureDiagnostic = null;
    let failedCheck = null;

    for (const check of checks) {
      const logPath = path.posix.join(artifacts.logDirectory, `${check.id}.log`);
      let completed;
      try {
        completed = await executeCheck({
          check,
          repoRoot,
          logPath,
          limits: policy.limits,
        });
      } catch (error) {
        completed = {
          id: check.id,
          status: "failed",
          durationMs: 0,
          exitCode: 1,
          summaryLines: [String(error.message || "Unexpected check execution failure").split("\n")[0]],
          locations: [],
          logPath,
          diagnostic: {
            code: "CHECK_EXECUTION_ERROR",
            checkId: check.id,
            message: String(error.message || "Unexpected check execution failure").split("\n")[0],
            retryable: true,
          },
        };
      }
      completedChecks.push(checkResultForOutput(completed));
      if (completed.status === "failed") {
        failedCheck = completed;
        failureDiagnostic = completed.diagnostic;
        break;
      }
    }

    const failed = failureDiagnostic ? 1 : 0;
    const passed = completedChecks.filter((check) => check.status === "passed").length;

    let failureObj = null;
    if (failedCheck) {
      const maxLines = policy.limits.maxFailureSummaryLines ?? 6;
      const locations = (failedCheck.locations || []).slice(0, maxLines);
      const msg = failedCheck.diagnostic?.message || failedCheck.summaryLines[0] || "Check failed";
      const signature = computeFailureSignature({
        checkId: failedCheck.id,
        exitCode: failedCheck.exitCode,
        message: msg,
        locations,
      });

      failureObj = {
        signature,
        code: failedCheck.diagnostic?.code || "CHECK_FAILED",
        checkId: failedCheck.id,
        exitCode: failedCheck.exitCode ?? 1,
        message: msg,
        locations,
        summaryLines: (failedCheck.summaryLines || []).slice(0, maxLines),
        attemptCount: 1,
        logPath: failedCheck.logPath || "",
      };
    }

    const result = {
      ...baseResult({
        inspection,
        runKey,
        status: failed ? "failed" : "passed",
        diagnostics: [
          ...inspection.diagnostics,
          ...(failureDiagnostic ? [failureDiagnostic] : []),
        ].slice(0, policy.limits.maxDiagnostics),
      }),
      review,
      summary: {
        passed,
        failed,
        skipped: checks.length - completedChecks.length,
        durationMs: Date.now() - startedAt,
      },
      checks: completedChecks,
      evidence: { recordPath: artifacts.recordPath },
    };

    if (failureObj) {
      result.failure = failureObj;
    }

    validateExecutionResult(result, repoRoot);

    await saveRunEvidence({ repoRoot, result, cacheable: snapshot.cacheable });
    return result;
  } finally {
    await releaseLock();
  }
}
