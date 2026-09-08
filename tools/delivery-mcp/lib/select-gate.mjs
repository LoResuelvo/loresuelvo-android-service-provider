import { classifyFiles, isDeliveryControlPlanePath, normalizePath } from "./classify-files.mjs";
import { findRepoRoot } from "./repo-root.mjs";

function policyGate(policy, gateId) {
  const gate = policy?.gates?.[gateId];
  if (!gate) throw new Error(`Delivery policy does not define gate ${gateId}`);
  return gate;
}

function substituteDisplay(display, parameters = {}) {
  return String(display || "").replace(/\{([A-Za-z][A-Za-z0-9]*)\}/g, (match, key) => {
    const value = parameters[key];
    if (Array.isArray(value)) return value.join(", ");
    return value === undefined || value === null || value === "" ? match : String(value);
  });
}

function buildGate(policy, gateId, reasonCodes, parameters = {}, extraCheckIds = []) {
  const definition = policyGate(policy, gateId);
  const checkIds = [...new Set([...(definition.checkIds || []), ...extraCheckIds])];
  const checks = checkIds.map((checkId) => {
    const definitionForCheck = policy.checkCatalog?.[checkId];
    if (!definitionForCheck) throw new Error(`Delivery policy does not define check ${checkId}`);
    return substituteDisplay(definitionForCheck.display, parameters);
  });
  return {
    id: gateId,
    reasonCodes: [...new Set(reasonCodes)],
    checkIds,
    checks,
    parameters,
    postPushChecks: definition.postPushChecks || [],
  };
}

function featurePaths(paths) {
  return [...new Set((paths || []).filter(Boolean).map(normalizePath).filter((file) => file.endsWith(".feature")))].sort();
}

function resolveFeatureScope({ featureFile, scopeFiles, snapshot }) {
  return featurePaths([
    featureFile,
    ...(scopeFiles || []),
    ...(snapshot?.stagedFiles || []),
    ...(snapshot?.recentUsFiles || []),
  ]);
}

function diagnostic(diagnostics, code, message, retryable = false) {
  diagnostics.push({ code, message, retryable });
}

function initialStatus({ snapshot, diagnostics, policy }) {
  let status = "ready";
  if (!snapshot) return status;
  if (snapshot.diffTooLarge) {
    status = "blocked";
    diagnostic(diagnostics, "DIFF_TOO_LARGE", `Staged diff exceeds maximum allowed limit of ${policy.limits.maxDiffSizeBytes} bytes`);
  }
  if (snapshot.tooManyFiles) {
    status = "blocked";
    diagnostic(diagnostics, "TOO_MANY_FILES", `Staged files count (${snapshot.stagedFiles.length}) exceeds maximum limit of ${policy.limits.maxStagedFiles}`);
  }
  if ((snapshot.unstagedConflicts || []).length) {
    status = "blocked";
    diagnostic(diagnostics, "UNSTAGED_CONFLICT", `Unstaged changes detected in already-staged files: ${snapshot.unstagedConflicts.join(", ")}`);
  }
  if (snapshot.isContradictoryUsId) {
    if (status !== "blocked") status = "needs_input";
    diagnostic(diagnostics, "CONTRADICTORY_US_ID", `Proposed US ID (${snapshot.proposedUsId}) contradicts recent commit history (${snapshot.primaryRecentUsId})`);
  }
  if ((snapshot.unrelatedUnstaged || []).length || (snapshot.untracked || []).length) {
    status = "blocked";
    const count = (snapshot.unrelatedUnstaged || []).length + (snapshot.untracked || []).length;
    diagnostic(diagnostics, "DIRTY_WORKTREE_OUTSIDE_SNAPSHOT", `Working tree has ${count} unstaged or untracked path(s) outside the staged snapshot`);
  }
  const dirtyControlPlane = [
    ...(snapshot.unrelatedUnstaged || []),
    ...(snapshot.untracked || []),
  ].filter((file) => isDeliveryControlPlanePath(file, policy));
  if (dirtyControlPlane.length) {
    status = "blocked";
    diagnostic(diagnostics, "DELIVERY_CONTROL_PLANE_DIRTY", `Unstaged delivery control-plane changes would make gate evidence ambiguous: ${dirtyControlPlane.join(", ")}`);
  }
  return status;
}

function disabledImpact(gate) {
  return {
    gate: gate.id,
    reasonCodes: ["ANDROID_ANALYSIS_NOT_APPLICABLE"],
    consumerCount: 0,
    affectedFeatures: 0,
    confidence: "high",
    parameters: { analyzers: "disabled" },
  };
}

export function selectGate({
  intent = "prepare_commit",
  featureFile = "",
  scopeFiles = [],
  repairsSha = "",
  snapshot,
  policy,
  maintainability = { status: "not_applicable", signalCount: 0, signals: [] },
  repoRoot = null,
  // Kept in the signature for clients that still pass the generic names.
  cucumberImpact = null,
  typeScriptImpact = null,
  dependencyImpact = null,
} = {}) {
  const diagnostics = [];
  const stagedFiles = snapshot?.stagedFiles || [];
  if (!stagedFiles.length) {
    const gate = buildGate(policy, "NONE", ["NO_STAGED_CHANGES"]);
    return { gate, status: "no_changes", diagnostics, impact: disabledImpact(gate) };
  }

  let status = initialStatus({ snapshot, diagnostics, policy });
  const classified = classifyFiles(stagedFiles, policy);
  if (classified.hasHumanOnly) {
    status = "blocked";
    diagnostic(diagnostics, "HUMAN_ONLY_CHANGE", "Workflow files are HUMAN_ONLY and require human handling.");
  }

  const closesHighRiskScenario = intent === "close_scenario" && classified.hasGateCTrigger;
  let gate;
  if (intent === "repair_ci") {
    gate = buildGate(policy, "R", ["INTENT_REPAIR_CI"], { repairsSha });
    if (!repairsSha) {
      if (status !== "blocked") status = "needs_input";
      diagnostic(diagnostics, "MISSING_REPAIRS_SHA", "intent 'repair_ci' requires repairsSha to be specified");
    }
  } else if (intent === "close_batch" || intent === "close_us" || closesHighRiskScenario) {
    const scope = resolveFeatureScope({ featureFile, scopeFiles, snapshot });
    const reason = closesHighRiskScenario
      ? "INTENT_CLOSE_HIGH_RISK_SCENARIO"
      : intent === "close_batch" ? "INTENT_CLOSE_BATCH" : "INTENT_CLOSE_US";
    gate = buildGate(policy, "D", [reason], { scopeFeatures: scope });
    if (!scope.length && status !== "blocked") {
      status = "needs_input";
      diagnostic(diagnostics, "MISSING_SCOPE_FOR_GATE_D", "Gate D requires at least one feature path to verify completed scope");
    }
  } else if (classified.hasGateCTrigger) {
    gate = buildGate(policy, "C", ["SHARED_OR_HIGH_RISK_CHANGES"]);
  } else if (intent === "close_scenario") {
    const candidates = featurePaths([featureFile, ...stagedFiles]);
    const target = candidates.length === 1 ? candidates[0] : "";
    gate = buildGate(policy, "B", ["INTENT_CLOSE_SCENARIO_LOW_RISK"], { featureFile: target });
    if (!target && status !== "blocked") {
      status = "needs_input";
      diagnostic(
        diagnostics,
        candidates.length > 1 ? "AMBIGUOUS_FEATURE_FOR_GATE_B" : "MISSING_FEATURE_FOR_GATE_B",
        candidates.length > 1
          ? `Gate B requires exactly one feature, but resolved ${candidates.length}: ${candidates.join(", ")}`
          : "Gate B requires exactly one feature path, inferred or explicitly declared"
      );
    }
  } else if (classified.hasOnlyGate0) {
    gate = buildGate(policy, "0", ["BDD_FEATURE_OR_JVM_TEST_CHANGES"]);
  } else if (classified.hasIsolatedProduction || classified.hasDeliveryTooling) {
    const reasons = [];
    const extra = [];
    if (classified.hasIsolatedProduction) reasons.push("ISOLATED_ANDROID_CODE");
    if (classified.hasDeliveryTooling) {
      reasons.push("DELIVERY_TOOLING_CHANGED");
      extra.push("delivery_unit");
    }
    gate = buildGate(policy, "A", reasons, {}, extra);
  } else if (classified.hasOnlyDocsOrConfig) {
    gate = buildGate(policy, "NONE", ["DOCS_ONLY"]);
  } else {
    // Unknown Android paths fail closed to Gate C. This includes unclassified
    // Kotlin/build-system changes and avoids false high-confidence NONE.
    gate = buildGate(policy, "C", ["UNKNOWN_FUNCTIONAL_ANDROID_CHANGE"]);
  }

  if (maintainability?.operationalDiagnostic) {
    status = "blocked";
    diagnostics.push(maintainability.operationalDiagnostic);
  } else if (maintainability?.status === "review_required" || (maintainability?.signalCount || 0) > 0) {
    if (status === "ready") status = "review_required";
    diagnostic(diagnostics, "MAINTAINABILITY_SIGNALS", `${maintainability.signalCount} maintainability signal(s) detected; review required before commit`);
  }

  return {
    gate,
    status,
    diagnostics: diagnostics.slice(0, policy.limits.maxDiagnostics),
    impact: disabledImpact(gate),
  };
}

