import { inspectDelivery } from "./inspect-delivery.mjs";
import { runGate } from "./run-gate.mjs";
import { findRepoRoot } from "./repo-root.mjs";
import { validateExecutionResult } from "./validate-schema.mjs";
import { recordPreparedEvidence, verifyPreparedEvidence, evaluateCiWindow } from "./delivery-ledger.mjs";
import { saveDeliveryContext } from "./delivery-context.mjs";
import { computeRunKey } from "./delivery-evidence.mjs";
import { createDeliveryJob, spawnJobWorker, findActiveDeliveryJob } from "./jobs.mjs";
import { buildRequiredAcknowledgement } from "./format-result.mjs";

export { buildRequiredAcknowledgement } from "./format-result.mjs";

function stoppedResult(inspection, status, extraDiagnostic, repoRoot) {
  const diagnostics = [
    ...inspection.diagnostics,
    ...(extraDiagnostic ? [extraDiagnostic] : []),
  ].slice(0, 20);
  const res = {
    schemaVersion: 1,
    status,
    snapshotHash: inspection.snapshotHash,
    runKey: null,
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
  if (status === "review_required") {
    res.requiredAcknowledgement =
      inspection?.requiredAcknowledgement ||
      buildRequiredAcknowledgement({
        snapshotHash: inspection?.snapshotHash,
        maintainability: inspection?.maintainability,
      });
  }
  validateExecutionResult(res, repoRoot);
  return res;
}

export function resolveReview(inspection, acknowledgement) {
  if (inspection.status !== "review_required") {
    return { accepted: true, review: { status: "not_required" } };
  }

  // 1. If acknowledgement is missing completely, require review
  if (!acknowledgement) {
    return {
      accepted: false,
      status: "review_required",
      diagnostic: {
        code: "MAINTAINABILITY_ACK_REQUIRED",
        message:
          "Review each maintainability signal, then acknowledge this exact snapshot with per-signal decisions",
        retryable: false,
      },
      requiredAcknowledgement:
        inspection?.requiredAcknowledgement ||
        buildRequiredAcknowledgement({
          snapshotHash: inspection?.snapshotHash,
          maintainability: inspection?.maintainability,
        }),
    };
  }

  // 2. Snapshot hash must match exactly
  if (acknowledgement.snapshotHash !== inspection.snapshotHash) {
    return {
      accepted: false,
      status: "blocked",
      diagnostic: {
        code: "MAINTAINABILITY_HASH_MISMATCH",
        message: `Acknowledgement snapshotHash (${acknowledgement.snapshotHash}) does not match current snapshot (${inspection.snapshotHash})`,
        retryable: false,
      },
    };
  }

  // 3. Must cover every signal with a stable id and justification (>= 12 chars).
  // Criterion 10: "No aceptar bypass genérico de todas las señales."
  const signals = inspection.maintainability?.signals || [];
  if (
    inspection.maintainability?.truncated ||
    (inspection.maintainability?.signalCount || 0) > signals.length
  ) {
    return {
      accepted: false,
      status: "blocked",
      diagnostic: {
        code: "MAINTAINABILITY_SIGNAL_LIMIT_EXCEEDED",
        message:
          "Maintainability signals exceed the policy display limit. Reduce the changed scope or resolve signals before preparing the commit",
        retryable: false,
      },
    };
  }
  const decisionsMap = new Map();

  if (acknowledgement.decisions) {
    if (Array.isArray(acknowledgement.decisions)) {
      for (const item of acknowledgement.decisions) {
        if (!item || typeof item !== "object") continue;
        const id = item.id || item.signalId;
        const reason = item.reason || item.justification;
        if (id !== undefined && id !== null && reason !== undefined && reason !== null) {
          decisionsMap.set(String(id).trim(), String(reason).trim());
        }
      }
    } else if (typeof acknowledgement.decisions === "object") {
      for (const [id, reason] of Object.entries(acknowledgement.decisions)) {
        if (id !== undefined && id !== null && reason !== undefined && reason !== null) {
          decisionsMap.set(String(id).trim(), String(reason).trim());
        }
      }
    }
  }

  // If no per-signal decisions provided, generic bypass is rejected
  if (decisionsMap.size === 0 && signals.length > 0) {
    return {
      accepted: false,
      status: "blocked",
      diagnostic: {
        code: "MAINTAINABILITY_DECISIONS_INCOMPLETE",
        message:
          "Generic bypass rejected. Each maintainability signal must have an explicit decision with id and justification of at least 12 characters",
        retryable: false,
      },
    };
  }

  const missingSignals = [];
  const invalidSignals = [];

  for (const signal of signals) {
    const primaryId = signal.id ? String(signal.id).trim() : null;
    const fallbackId = `${signal.rule}:${signal.file}:${signal.line}`.trim();
    const targetId = primaryId || fallbackId;
    const justification = (primaryId && decisionsMap.get(primaryId)) || decisionsMap.get(fallbackId);
    if (!justification) {
      missingSignals.push(targetId);
    } else if (justification.length < 12) {
      invalidSignals.push(targetId);
    }
  }

  if (missingSignals.length > 0 || invalidSignals.length > 0) {
    const missingDesc = missingSignals.length > 0 ? `missing: [${missingSignals.join(", ")}]` : "";
    const invalidDesc = invalidSignals.length > 0 ? `justification < 12 chars: [${invalidSignals.join(", ")}]` : "";
    const details = [missingDesc, invalidDesc].filter(Boolean).join("; ");
    return {
      accepted: false,
      status: "blocked",
      diagnostic: {
        code: "MAINTAINABILITY_DECISIONS_INCOMPLETE",
        message: `Maintainability decisions incomplete (${details})`,
        retryable: false,
      },
    };
  }

  return {
    accepted: true,
    review: {
      status: "acknowledged",
      snapshotHash: acknowledgement.snapshotHash,
      decisions: Object.fromEntries(decisionsMap),
      reason: acknowledgement.reason || "Per-signal maintainability decisions accepted",
    },
  };
}

export async function prepareDelivery({
  repoRoot,
  acknowledgement,
  force = false,
  ciProvider = null,
  provider = null,
  executeCheck = null,
  mode = "sync",
  async: isAsync = false,
  // Internal marker used by job-runner. The worker already owns the job, so
  // it must not discover and deduplicate against its own running record.
  workerJobId = null,
  ...inspectionInput
} = {}) {
  const root = findRepoRoot(repoRoot);
  const effectiveProvider = provider || ciProvider || null;
  const context = await inspectDelivery({
    repoRoot: root,
    ciProvider: effectiveProvider,
    provider: effectiveProvider,
    ...inspectionInput,
  });
  const { result: inspection, snapshot, policy, resolvedInput } = context;

  if (inspection.status === "no_changes") {
    return stoppedResult(inspection, "no_changes", null, root);
  }
  if (inspection.status === "blocked" || inspection.status === "needs_input") {
    return stoppedResult(inspection, inspection.status, null, root);
  }

  // Single compact CI window and incident evaluation before running local gates
  const ciEvaluation = await evaluateCiWindow({
    repoRoot: root,
    policy,
    ciProvider: effectiveProvider,
    intent: resolvedInput.intent,
    repairsSha: resolvedInput.repairsSha,
    historyHeadSha: snapshot.headSha,
  });

  if (!ciEvaluation.allowed) {
    const diagCode = ciEvaluation.code || ciEvaluation.reason || "CI_EVALUATION_BLOCKED";
    const extraDiag = {
      code: diagCode,
      message: ciEvaluation.message,
      retryable: Boolean(ciEvaluation.retryable),
      ...(ciEvaluation.failedSha ? { failedSha: ciEvaluation.failedSha } : {}),
    };
    const stopped = stoppedResult(inspection, "blocked", extraDiag, root);
    if (ciEvaluation.failedSha) {
      stopped.failedSha = ciEvaluation.failedSha;
    }
    if (ciEvaluation.pendingCount !== undefined) {
      stopped.pendingCount = ciEvaluation.pendingCount;
      stopped.maxInFlightCommits = ciEvaluation.maxInFlightCommits;
    }
    return stopped;
  }


  if (!force) {
    const prepared = await verifyPreparedEvidence({
      repoRoot: root,
      snapshot,
      inspection,
      intent: resolvedInput.intent,
      repairsSha: resolvedInput.repairsSha,
    });
    if (prepared.valid) return { ...prepared.record, cached: true };
  }

  const review = resolveReview(inspection, acknowledgement);
  if (!review.accepted) {
    return stoppedResult(inspection, review.status || "review_required", review.diagnostic, root);
  }

  const runKey = computeRunKey({ inspection, snapshot });

  // 1. Re-use existing active job for the exact snapshot if running
  const activeJob = workerJobId
    ? null
    : await findActiveDeliveryJob({ repoRoot: root, runKey });
  if (activeJob) {
    return {
      schemaVersion: 1,
      status: "running",
      jobId: activeJob.jobId,
      snapshotHash: inspection.snapshotHash,
      runKey,
      cached: false,
      policy: inspection.policy,
      gate: inspection.gate,
      summary: { passed: 0, failed: 0, skipped: inspection.gate.checkIds.length, durationMs: 0 },
      checks: [],
      diagnostics: [],
      evidence: { recordPath: null },
      message: `Delivery job '${activeJob.jobId}' is already running for this snapshot. Use delivery_job_wait to await completion.`,
    };
  }

  // 2. Decide if execution should run as a background job
  const requestedMode = inspectionInput.mode || mode;
  const isLongGate = ["C", "D", "R"].includes(inspection.gate.id);
  const shouldRunAsJob =
    isAsync ||
    requestedMode === "job" ||
    (requestedMode === "auto" && isLongGate && !executeCheck);

  if (shouldRunAsJob) {
    const job = await createDeliveryJob({
      repoRoot: root,
      type: "prepare",
      params: {
        ...inspectionInput,
        intent: resolvedInput.intent,
        proposedCommitMessage: resolvedInput.proposedCommitMessage,
        featureFile: resolvedInput.featureFile,
        scenarioName: resolvedInput.scenarioName,
        scopeFiles: resolvedInput.scopeFiles,
        repairsSha: resolvedInput.repairsSha,
        acknowledgement,
        force,
      },
      runKey,
      snapshotHash: inspection.snapshotHash,
      gateId: inspection.gate.id,
    });

    await spawnJobWorker({ repoRoot: root, jobId: job.jobId });

    return {
      schemaVersion: 1,
      status: "job_started",
      jobId: job.jobId,
      snapshotHash: inspection.snapshotHash,
      runKey,
      cached: false,
      policy: inspection.policy,
      gate: inspection.gate,
      summary: { passed: 0, failed: 0, skipped: inspection.gate.checkIds.length, durationMs: 0 },
      checks: [],
      diagnostics: [],
      evidence: { recordPath: null },
      message: `Execution for Gate ${inspection.gate.id} started as recoverable background job '${job.jobId}'. Use delivery_job_wait to await completion.`,
    };
  }

  const outcome = await runGate({
    inspection,
    snapshot,
    policy,
    repoRoot: root,
    review: review.review,
    force,
    ...(executeCheck ? { executeCheck } : {}),
  });

  if (outcome.status === "passed" && resolvedInput.intent !== "prepare_commit") {
    await saveDeliveryContext({
      repoRoot: root,
      snapshot,
      intent: resolvedInput.intent,
      usId: resolvedInput.usId,
      featureFile: resolvedInput.featureFile,
      scenarioName: resolvedInput.scenarioName,
      scopeFiles: resolvedInput.scopeFiles,
      repairsSha: resolvedInput.repairsSha,
    });
  }

  await recordPreparedEvidence({
    repoRoot: root,
    snapshot,
    inspection,
    intent: resolvedInput.intent,
    usId: resolvedInput.usId,
    featureFile: resolvedInput.featureFile,
    scenarioName: resolvedInput.scenarioName,
    scopeFiles: resolvedInput.scopeFiles,
    runKey: outcome.runKey,
    status: outcome.status,
    recordPath: outcome.evidence?.recordPath,
    repairsSha: resolvedInput.repairsSha,
    repairStatus: resolvedInput.intent === "repair_ci" ? "unverified" : null,
  });

  return outcome;
}
