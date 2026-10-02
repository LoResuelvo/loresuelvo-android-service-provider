import { execFileSync } from "node:child_process";
import { findRepoRoot } from "./repo-root.mjs";
import { queryCommitEvidence } from "./delivery-ledger.mjs";
import { readHistoricalAcceptance } from "./historical-acceptance.mjs";
import { relevantCommitShas } from "./delivery-finalize.mjs";

function resolveRequiredSha(root, value, headSha) {
  if (!/^[a-f0-9]{7,40}$/i.test(value)) {
    return { status: "blocked", reason: "INVALID_COMMIT_SHA", sha: value };
  }
  let sha;
  try {
    sha = execFileSync("git", ["rev-parse", "--verify", `${value}^{commit}`], {
      cwd: root,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
    execFileSync("git", ["merge-base", "--is-ancestor", sha, headSha], {
      cwd: root,
      stdio: "ignore",
    });
  } catch {
    return { status: "blocked", reason: "COMMIT_NOT_IN_HEAD_HISTORY", sha: value };
  }
  return { status: "ready", sha };
}

export async function inspectClosureReadiness({ repoRoot, usId, requiredShas = [], ciProvider = null } = {}) {
  const root = findRepoRoot(repoRoot);
  if (!/^[0-9]+(?:\.[0-9]+)?$/.test(usId || "")) {
    return { status: "blocked", reason: "INVALID_US_ID", commits: [] };
  }
  const headSha = execFileSync("git", ["rev-parse", "HEAD"], {
    cwd: root,
    encoding: "utf8",
  }).trim();
  const explicit = requiredShas.map((value) => resolveRequiredSha(root, value, headSha));
  const invalid = explicit.find((item) => item.status === "blocked");
  if (invalid) return { status: "blocked", reason: invalid.reason, sha: invalid.sha, headSha, usId, commits: [] };

  const selected = await relevantCommitShas(root, headSha, usId, { includeHead: false });
  const shas = [...new Set([...selected, ...explicit.map((item) => item.sha)])];
  let historicalAcceptance;
  try { historicalAcceptance = await readHistoricalAcceptance({ repoRoot: root, ciProvider }); }
  catch (error) { return { status: "blocked", reason: error.code || "HISTORICAL_AUDIT_INVALID", headSha, usId, commits: [] }; }
  const commits = [];
  for (const sha of shas) {
    const evidence = await queryCommitEvidence({ repoRoot: root, commitSha: sha });
    const accepted = evidence.state === "missing" && historicalAcceptance.find(entry => entry.baselineSha === sha && entry.usId === usId);
    commits.push({
      sha,
      state: accepted ? "accepted_historical" : evidence.state,
      ...(accepted ? { acceptanceId: accepted.auditId } : {}),
      reason: evidence.reason,
      gateId: evidence.entry?.gateId || null,
      intent: evidence.entry?.intent || null,
    });
  }
  const firstGap = commits.find((commit) => !["verified", "accepted_historical"].includes(commit.state));
  return {
    status: firstGap ? "blocked" : "ready",
    reason: firstGap ? "COMMIT_EVIDENCE_NOT_VERIFIED" : (commits.length === 0 ? "NO_EXISTING_US_COMMITS" : null),
    headSha,
    usId,
    acceptedHistoricalCommits: commits.filter(commit => commit.state === "accepted_historical").map(commit => commit.sha),
    commits,
  };
}
