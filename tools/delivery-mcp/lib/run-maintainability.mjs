/**
 * Compose maintainability analysis is deliberately disabled for the first
 * Android delivery release. Keeping this adapter explicit prevents a web
 * analyzer from being imported or accidentally executed.
 */
export async function runMaintainabilityAudit({ stagedFiles = [] } = {}) {
  return {
    status: "not_applicable",
    filesReviewed: [],
    signalCount: 0,
    signals: [],
    truncated: false,
    analyzer: "android-compose",
    reason: "Analyzer disabled until an Android-specific implementation exists",
  };
}

