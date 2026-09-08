import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

/**
 * Resolves the repository root stably, avoiding accidental reliance on process.cwd().
 *
 * Priority:
 * 1. An explicitly provided repoRoot if valid (rejects path traversal).
 * 2. git rev-parse --show-toplevel starting from process.cwd().
 * 3. Relative navigation from this module's location (tools/delivery-mcp/lib/ -> root).
 * 4. Fallback to process.cwd().
 */
export function findRepoRoot(optionalStartDir) {
  if (optionalStartDir !== undefined && optionalStartDir !== null && optionalStartDir !== "") {
    if (typeof optionalStartDir !== "string") {
      throw new Error("Invalid repoRoot: must be a string");
    }
    const normalized = optionalStartDir.replaceAll("\\", "/");
    if (normalized.split("/").includes("..")) {
      throw new Error(`Repository root path traversal rejected: ${optionalStartDir}`);
    }
    return path.resolve(optionalStartDir);
  }

  // 1. If process.cwd() directly contains .delivery/policy.v1.json
  const cwd = process.cwd();
  if (fs.existsSync(path.join(cwd, ".delivery", "policy.v1.json"))) {
    return cwd;
  }

  // 2. Try git rev-parse --show-toplevel
  try {
    const gitRoot = execFileSync("git", ["rev-parse", "--show-toplevel"], {
      cwd,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
    if (gitRoot && fs.existsSync(path.join(gitRoot, ".delivery", "policy.v1.json"))) {
      return gitRoot;
    }
  } catch {
    // Git lookup failed or not a git repository
  }

  // 3. Relative to this file: ../../../ from tools/delivery-mcp/lib/
  try {
    const moduleDir = path.dirname(fileURLToPath(import.meta.url));
    const candidate = path.resolve(moduleDir, "../../..");
    if (fs.existsSync(path.join(candidate, ".delivery", "policy.v1.json"))) {
      return candidate;
    }
  } catch {
    // ignore
  }

  return cwd;
}

/**
 * Validates that a path is strictly inside the repoRoot and does not use path traversal.
 */
export function assertSafeRepoPath(repoRoot, targetPath, label = "Path") {
  if (typeof targetPath !== "string" || !targetPath.trim()) {
    throw new Error(`${label} cannot be empty`);
  }
  const normalized = targetPath.replaceAll("\\", "/").replace(/^\.\//, "");
  const segments = normalized.split("/");
  if (segments.includes("..")) {
    throw new Error(`${label} contains path traversal: ${targetPath}`);
  }
  const absolute = path.resolve(repoRoot, normalized);
  const relative = path.relative(repoRoot, absolute);
  if (relative.startsWith("..") || path.isAbsolute(relative)) {
    throw new Error(`${label} resolves outside repository: ${targetPath}`);
  }
  return normalized;
}
