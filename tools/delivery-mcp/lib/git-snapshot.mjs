import { execFile } from "node:child_process";
import crypto from "node:crypto";
import util from "node:util";
import { findRepoRoot } from "./repo-root.mjs";

const execFileAsync = util.promisify(execFile);

const US_ID_REGEX = /\[([0-9]+(?:\.[0-9]+)?)\]/;
const US_ID_ALL_REGEX = /\[([0-9]+(?:\.[0-9]+)?)\]/g;
const DEFAULT_LIMITS = {
  maxStagedFiles: 500,
  maxDiffSizeBytes: 2 * 1024 * 1024,
};

export function extractUsId(message) {
  if (!message || typeof message !== "string") return null;
  const match = message.match(US_ID_REGEX);
  return match ? match[1] : null;
}

export function extractAllUsIds(message) {
  if (!message || typeof message !== "string") return [];
  const matches = [...message.matchAll(US_ID_ALL_REGEX)];
  return [...new Set(matches.map((m) => m[1]))];
}

export async function runGit(args, cwd) {
  try {
    const { stdout, stderr } = await execFileAsync("git", args, {
      cwd,
      maxBuffer: 20 * 1024 * 1024,
      encoding: "buffer",
    });
    return { stdout, stderr, error: null };
  } catch (error) {
    return { stdout: Buffer.alloc(0), stderr: Buffer.from(error.message || ""), error };
  }
}

export function parsePorcelainStatus(porcelainBuffer) {
  const staged = [];
  const unstaged = [];
  const untracked = [];

  if (porcelainBuffer.includes(0)) {
    // NUL-delimited (-z) format
    const parts = [];
    let start = 0;
    for (let i = 0; i < porcelainBuffer.length; i++) {
      if (porcelainBuffer[i] === 0) {
        if (i > start) {
          parts.push(porcelainBuffer.subarray(start, i).toString("utf8"));
        }
        start = i + 1;
      }
    }
    if (start < porcelainBuffer.length) {
      const remaining = porcelainBuffer.subarray(start).toString("utf8");
      if (remaining) parts.push(remaining);
    }

    for (let i = 0; i < parts.length; i++) {
      const entry = parts[i];
      if (entry.length < 3) continue;
      const x = entry[0];
      const y = entry[1];
      const file = entry.slice(3);

      // In -z format, renames/copies (R or C) are followed by the old path in the next NUL block
      if (x === "R" || x === "C" || y === "R" || y === "C") {
        i += 1;
      }

      if (file === ".delivery/runtime" || file === ".delivery/runtime/" || file.startsWith(".delivery/runtime/")) {
        continue;
      }

      if (x === "?" && y === "?") {
        untracked.push(file);
      } else {
        if (x !== " " && x !== "?") {
          staged.push({ file, status: x });
        }
        if (y !== " " && y !== "?") {
          unstaged.push({ file, status: y });
        }
      }
    }
    return { staged, unstaged, untracked };
  }

  // Newline-delimited fallback
  const text = porcelainBuffer.toString("utf8");
  const lines = text.split("\n").filter(Boolean);

  for (const line of lines) {
    if (line.length < 4) continue;
    const x = line[0];
    const y = line[1];
    let file = line.slice(3).trim();
    if (file.includes(" -> ")) {
      file = file.split(" -> ")[1].trim();
    }
    file = file.replace(/^"|"$/g, "");

    if (file === ".delivery/runtime" || file === ".delivery/runtime/" || file.startsWith(".delivery/runtime/")) {
      continue;
    }

    if (x === "?" && y === "?") {
      untracked.push(file);
    } else {
      if (x !== " " && x !== "?") {
        staged.push({ file, status: x });
      }
      if (y !== " " && y !== "?") {
        unstaged.push({ file, status: y });
      }
    }
  }

  return { staged, unstaged, untracked };
}

function assertGitSucceeded(result, operation) {
  if (!result.error) return;
  const message = result.stderr.toString("utf8").trim().split("\n")[0];
  throw new Error(`Git ${operation} failed: ${message || "unknown error"}`);
}

async function collectRecentUsFiles({ recentCommits, usId, repoRoot }) {
  if (!usId) return [];

  const matchingCommits = recentCommits.filter((commit) => extractUsId(commit.message) === usId);
  const results = await Promise.all(
    matchingCommits.map((commit) =>
      runGit(["diff-tree", "--no-commit-id", "--name-only", "-r", "-z", commit.sha], repoRoot)
    )
  );
  const files = [];

  for (const result of results) {
    if (result.error) continue;
    for (const file of result.stdout.toString("utf8").split("\0").filter(Boolean)) {
      if (!files.includes(file)) files.push(file);
    }
  }

  return files;
}

export async function captureGitSnapshot({
  cwd,
  proposedCommitMessage = "",
  limits = DEFAULT_LIMITS,
} = {}) {
  const repoRoot = findRepoRoot(cwd);

  // 1. HEAD SHA
  const headRes = await runGit(["rev-parse", "HEAD"], repoRoot);
  assertGitSucceeded(headRes, "rev-parse HEAD");
  const headSha = headRes.stdout.toString("utf8").trim() || "UNKNOWN";

  // 2. Current branch
  const branchRes = await runGit(["rev-parse", "--abbrev-ref", "HEAD"], repoRoot);
  assertGitSucceeded(branchRes, "rev-parse branch");
  const branch = branchRes.stdout.toString("utf8").trim() || "HEAD";

  // 3. Status porcelain with -z
  const statusRes = await runGit(["status", "--porcelain", "-z"], repoRoot);
  assertGitSucceeded(statusRes, "status");
  const { staged, unstaged, untracked } = parsePorcelainStatus(statusRes.stdout);

  const stagedFilePaths = staged.map((s) => s.file);
  const unstagedFilePaths = unstaged.map((s) => s.file);

  // Check unstaged conflict: unstaged changes on files already staged
  const stagedSet = new Set(stagedFilePaths);
  const unstagedConflicts = unstagedFilePaths.filter((file) => stagedSet.has(file));
  const unrelatedUnstaged = unstagedFilePaths.filter((file) => !stagedSet.has(file));

  // 4. Staged diff content, size and hash
  const diffRes = await runGit(["diff", "--cached", "--binary"], repoRoot);
  assertGitSucceeded(diffRes, "staged diff");
  const diffBuffer = diffRes.stdout;
  const diffSizeBytes = diffBuffer.length;

  let stagedTreeSha = "UNKNOWN";
  if (staged.length === 0) {
    const headTreeRes = await runGit(["rev-parse", "HEAD^{tree}"], repoRoot);
    if (!headTreeRes.error) {
      stagedTreeSha = headTreeRes.stdout.toString("utf8").trim();
    }
  }
  if (stagedTreeSha === "UNKNOWN") {
    const treeRes = await runGit(["write-tree"], repoRoot);
    if (treeRes.error) {
      const fallbackTree = await runGit(["rev-parse", "HEAD^{tree}"], repoRoot);
      if (!fallbackTree.error) {
        stagedTreeSha = fallbackTree.stdout.toString("utf8").trim();
      } else {
        assertGitSucceeded(treeRes, "write-tree");
      }
    } else {
      stagedTreeSha = treeRes.stdout.toString("utf8").trim();
    }
  }

  const hash = crypto.createHash("sha256");
  hash.update(diffBuffer);
  const snapshotHash = hash.digest("hex");

  // 5. Recent commits (last 20)
  const logRes = await runGit(["log", "-n", "20", "--format=%H %s"], repoRoot);
  assertGitSucceeded(logRes, "recent log");
  const logLines = logRes.stdout.toString("utf8").split("\n").filter(Boolean);
  const recentCommits = logLines.map((line) => {
    const spaceIndex = line.indexOf(" ");
    return {
      sha: spaceIndex !== -1 ? line.slice(0, spaceIndex) : line,
      message: spaceIndex !== -1 ? line.slice(spaceIndex + 1) : "",
    };
  });

  // 6. Infer US ID & check for contradictions
  const proposedUsIds = extractAllUsIds(proposedCommitMessage);
  const proposedUsId = proposedUsIds[0] || null;

  // Find recent US IDs from commit history
  const recentUsIds = [];
  for (const commit of recentCommits) {
    const id = extractUsId(commit.message);
    if (id && !recentUsIds.includes(id)) {
      recentUsIds.push(id);
    }
  }

  const primaryRecentUsId = recentUsIds[0] || null;

  // Criterion 5: Explicit US ID in proposed commit message takes priority.
  // A new US is NOT contradictory just because the previous commit had another ID.
  const usId = proposedUsId || primaryRecentUsId;
  const isContradictoryUsId = proposedUsIds.length > 1;

  const recentUsFiles = await collectRecentUsFiles({ recentCommits, usId, repoRoot });
  const maxDiffSizeBytes = limits.maxDiffSizeBytes ?? DEFAULT_LIMITS.maxDiffSizeBytes;
  const maxStagedFiles = limits.maxStagedFiles ?? DEFAULT_LIMITS.maxStagedFiles;

  return {
    repoRoot,
    branch,
    headSha,
    stagedFiles: stagedFilePaths,
    stagedCount: stagedFilePaths.length,
    unstagedConflicts,
    unrelatedUnstaged,
    untracked,
    diffSizeBytes,
    stagedDiffText: diffBuffer.toString("utf8"),
    snapshotHash,
    stagedTreeSha,
    proposedCommitMessage,
    proposedUsId,
    recentUsIds,
    recentUsFiles,
    primaryRecentUsId,
    usId,
    isContradictoryUsId,
    diffTooLarge: diffSizeBytes > maxDiffSizeBytes,
    tooManyFiles: stagedFilePaths.length > maxStagedFiles,
    cacheable:
      unstagedConflicts.length === 0 &&
      unrelatedUnstaged.length === 0 &&
      untracked.length === 0,
  };
}
