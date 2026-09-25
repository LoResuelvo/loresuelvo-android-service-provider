import { findRepoRoot } from "./repo-root.mjs";
import { loadDeliveryPolicy } from "./policy-loader.mjs";
import { evaluateCiWindow } from "./delivery-ledger.mjs";

export async function waitForCiWindow({
  repoRoot,
  timeoutMs = 30000,
  pollIntervalMs = 5000,
  ciProvider = null,
  sleepFn = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  nowFn = () => Date.now(),
} = {}) {
  const root = findRepoRoot(repoRoot);
  const policy = await loadDeliveryPolicy({ repoRoot: root });
  const deadline = nowFn() + timeoutMs;
  let lastSummary = null;
  while (true) {
    const timeForEvaluation = deadline - nowFn();
    if (timeForEvaluation <= 0) {
      return {
        status: "timed_out",
        reason: lastSummary?.reason || "CI_WINDOW_EVALUATION_TIMEOUT",
        maxInFlightCommits: policy.ci.maxInFlightCommits,
        ...lastSummary,
      };
    }
    const controller = new AbortController();
    let timeout;
    const expired = new Promise((resolve) => {
      timeout = setTimeout(() => {
        controller.abort();
        resolve(null);
      }, timeForEvaluation);
    });
    let evaluation;
    try {
      evaluation = await Promise.race([
        evaluateCiWindow({
          repoRoot: root,
          policy,
          ciProvider,
          intent: "prepare_commit",
          signal: controller.signal,
        }),
        expired,
      ]);
    } catch (error) {
      if (!controller.signal.aborted) throw error;
      evaluation = null;
    } finally {
      clearTimeout(timeout);
    }
    if (controller.signal.aborted || !evaluation) {
      return {
        status: "timed_out",
        reason: "CI_WINDOW_EVALUATION_TIMEOUT",
        maxInFlightCommits: policy.ci.maxInFlightCommits,
      };
    }
    const summary = {
      reason: evaluation.reason || null,
      pendingCount: evaluation.pendingCount ?? 0,
      maxInFlightCommits: evaluation.maxInFlightCommits ?? policy.ci.maxInFlightCommits,
      ...(evaluation.failedSha ? { failedSha: evaluation.failedSha } : {}),
    };
    lastSummary = summary;
    if (evaluation.allowed) return { status: "ready", ...summary };
    if (evaluation.reason !== "CI_WINDOW_FULL") return { status: "blocked", ...summary };
    const remaining = deadline - nowFn();
    if (remaining <= 0) return { status: "timed_out", ...summary };
    await sleepFn(Math.min(pollIntervalMs, remaining));
  }
}
