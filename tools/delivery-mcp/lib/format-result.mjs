import { validateInspectionResult } from "./validate-schema.mjs";

export function formatInspectionResult({
  snapshot,
  gateResult,
  maintainability,
  policy,
  repoRoot,
} = {}) {
  const diagnostics = [...(gateResult?.diagnostics || [])];

  if (maintainability?.operationalDiagnostic) {
    diagnostics.push(maintainability.operationalDiagnostic);
  }

  // Sanitize diagnostics: ensure no stack traces or raw errors leak
  const maxDiagnostics = policy?.limits?.maxDiagnostics ?? 20;
  const sanitizedDiagnostics = diagnostics.slice(0, maxDiagnostics).map((d) => {
    const item = {
      code: String(d.code || "DELIVERY_UNKNOWN"),
      message: String(d.message || "An unspecified inspection error occurred").split("\n")[0],
      retryable: Boolean(d.retryable),
    };
    if (d.file) item.file = String(d.file);
    if (typeof d.line === "number") item.line = d.line;
    return item;
  });

  const maxSignals = policy?.limits?.maxSignals ?? 20;
  const sanitizedSignals = (maintainability?.signals || []).slice(0, maxSignals).map((s) => ({
    id: String(s.id || `${s.rule || "unknown"}:${s.file || "unknown"}:${s.line || 1}`),
    rule: String(s.rule || "unknown"),
    file: String(s.file || "unknown"),
    line: typeof s.line === "number" ? s.line : 1,
    observed: s.observed ?? 0,
    threshold: s.threshold ?? 0,
    message: String(s.message || "").split("\n")[0],
  }));

  const result = {
    schemaVersion: 1,
    status: gateResult?.status || "ready",
    snapshotHash: snapshot?.snapshotHash || "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    repository: {
      branch: snapshot?.branch || "HEAD",
      headSha: snapshot?.headSha || "UNKNOWN",
      usId: snapshot?.usId || null,
    },
    policy: {
      version: policy?.version || 1,
      hash: policy?.sourceHash || "UNKNOWN",
    },
    gate: {
      id: gateResult?.gate?.id || "NONE",
      reasonCodes: gateResult?.gate?.reasonCodes || [],
      checkIds: gateResult?.gate?.checkIds || [],
      checks: gateResult?.gate?.checks || [],
      parameters: gateResult?.gate?.parameters || {},
      postPushChecks: gateResult?.gate?.postPushChecks || [],
    },
    impact: gateResult?.impact || {
      gate: "NONE",
      reasonCodes: [],
      consumerCount: 0,
      affectedFeatures: 0,
      confidence: "high",
    },
    maintainability: {
      status: maintainability?.status || "not_applicable",
      filesReviewed: maintainability?.filesReviewed || [],
      signalCount: maintainability?.signalCount ?? sanitizedSignals.length,
      signals: sanitizedSignals,
      truncated: Boolean(
        maintainability?.truncated ||
          (maintainability?.signalCount ?? sanitizedSignals.length) > sanitizedSignals.length
      ),
    },
    diagnostics: sanitizedDiagnostics,
  };

  if (result.status === "review_required") {
    result.requiredAcknowledgement = buildRequiredAcknowledgement({
      snapshotHash: result.snapshotHash,
      maintainability: result.maintainability,
    });
  }

  validateInspectionResult(result, repoRoot);

  return result;
}

export function buildRequiredAcknowledgement({ snapshotHash = "", maintainability } = {}) {
  const hash = snapshotHash || "";
  const rawSignals = maintainability?.signals || [];
  const signals = rawSignals.map((s) => ({
    id: s.id || `${s.rule || "unknown"}:${s.file || "unknown"}:${s.line || 1}`,
    rule: s.rule || "unknown",
    file: s.file || "unknown",
    line: typeof s.line === "number" ? Math.floor(s.line) : 1,
    message: String(s.message || "").split("\n")[0],
  }));

  return {
    snapshotHash: hash,
    signals,
    template: {
      snapshotHash: hash,
      decisions: Object.fromEntries(
        signals.map((s) => [s.id, "Provide at least 12 characters explaining this decision"])
      ),
    },
  };
}
