/**
 * Placeholder for Kotlin dependency impact analysis.
 *
 * The Android v1 policy disables this analyzer. Returning a typed
 * not_applicable result is safer than importing the web TypeScript graph or
 * guessing which Kotlin consumers are affected.
 */
export function analyzeTypeScriptImpact({ files = [] } = {}) {
  return {
    status: "not_applicable",
    analyzer: "android-dependency-impact",
    gate: "C",
    reasonCodes: ["ANDROID_ANALYSIS_NOT_APPLICABLE"],
    consumerCount: 0,
    affectedFeatures: 0,
    confidence: "low",
    files: [...files],
  };
}

export function analyzeKotlinDependencyImpact(options = {}) {
  return analyzeTypeScriptImpact(options);
}

