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
  while (true) {
    const evaluation = await evaluateCiWindow({
      repoRoot: root,
      policy,
      ciProvider,
      intent: "prepare_commit",
    });
    const summary = {
      reason: evaluation.reason || null,
      pendingCount: evaluation.pendingCount ?? 0,
      maxInFlightCommits: evaluation.maxInFlightCommits ?? policy.ci.maxInFlightCommits,
      ...(evaluation.failedSha ? { failedSha: evaluation.failedSha } : {}),
    };
    if (evaluation.allowed) return { status: "ready", ...summary };
    if (evaluation.reason !== "CI_WINDOW_FULL") return { status: "blocked", ...summary };
    const remaining = deadline - nowFn();
    if (remaining <= 0) return { status: "timed_out", ...summary };
    await sleepFn(Math.min(pollIntervalMs, remaining));
  }
}
