/**
 * Cucumber impact is disabled for Cucumber JVM in the Android provider.
 * Gate 0/B run the complete JVM test task until a focused runner exists.
 */
export const CUCUMBER_IMPACT_INDEX_PATH = ".delivery/runtime/indexes/cucumber-impact-v1.json";

export function analyzeCucumberImpact({ files = [] } = {}) {
  return {
    status: "not_applicable",
    analyzer: "android-jvm-cucumber-impact",
    gate: "0",
    reasonCodes: ["ANDROID_ANALYSIS_NOT_APPLICABLE"],
    consumerCount: 0,
    affectedFeatures: 0,
    confidence: "low",
    files: [...files],
  };
}

export function loadOrBuildCucumberImpactIndex() {
  return {
    status: "not_applicable",
    analyzer: "android-jvm-cucumber-impact",
    features: [],
    stepDefinitions: [],
  };
}

export function isStepDefinitionFile(filePath) {
  return /(?:^|\/)app\/src\/test\/java\/.*\.kt$/.test(String(filePath || ""));
}

export function isCucumberSupportFile() {
  return false;
}

