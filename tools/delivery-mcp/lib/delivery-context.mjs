import fs from "node:fs/promises";
import path from "node:path";
import { assertSafeRepoPath, findRepoRoot } from "./repo-root.mjs";
import { validateDeliveryContextResult } from "./validate-schema.mjs";
import { extractUsId } from "./git-snapshot.mjs";

export const DELIVERY_CONTEXT_PATH = ".delivery/runtime/context.json";

function normalizeUsId(usId) {
  if (!usId || typeof usId !== "string") return null;
  const normalized = usId.trim();
  if (!/^[0-9]+$/.test(normalized)) {
    throw new Error("User Story identifier must contain digits only");
  }
  return normalized;
}

export async function loadDeliveryContext({ repoRoot } = {}) {
  const root = findRepoRoot(repoRoot);
  const absolutePath = path.resolve(root, DELIVERY_CONTEXT_PATH);
  try {
    const raw = await fs.readFile(absolutePath, "utf8");
    const parsed = JSON.parse(raw);
    return parsed;
  } catch (error) {
    if (error.code === "ENOENT" || error instanceof SyntaxError) {
      return null;
    }
    throw error;
  }
}

export async function saveDeliveryContext({
  repoRoot,
  snapshot,
  intent = "prepare_commit",
  usId = null,
  featureFile = null,
  scenarioName = null,
  scopeFiles = [],
  repairsSha = null,
} = {}) {
  const root = findRepoRoot(repoRoot);
  const absolutePath = path.resolve(root, DELIVERY_CONTEXT_PATH);
  assertSafeRepoPath(root, DELIVERY_CONTEXT_PATH, "Delivery context path");

  if (featureFile) {
    assertSafeRepoPath(root, featureFile, "Feature file");
  }
  const cleanScopeFiles = [];
  for (const file of scopeFiles || []) {
    assertSafeRepoPath(root, file, "Scope file");
    cleanScopeFiles.push(file);
  }

  const context = {
    schemaVersion: 1,
    createdAt: new Date().toISOString(),
    branch: snapshot?.branch || "HEAD",
    headSha: snapshot?.headSha || "UNKNOWN",
    snapshotHash: snapshot?.snapshotHash || "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    intent,
    usId: normalizeUsId(usId),
    featureFile: featureFile ? String(featureFile).trim() : null,
    scenarioName: scenarioName ? String(scenarioName).trim() : null,
    scopeFiles: cleanScopeFiles,
    repairsSha: repairsSha ? String(repairsSha).trim() : null,
    consumed: false,
    consumedAt: null,
  };

  validateDeliveryContextResult(context, root);

  const tempPath = `${absolutePath}.${process.pid}.${Date.now()}.tmp`;
  await fs.mkdir(path.dirname(absolutePath), { recursive: true, mode: 0o700 });
  await fs.writeFile(tempPath, `${JSON.stringify(context, null, 2)}\n`, { mode: 0o600 });
  await fs.rename(tempPath, absolutePath);

  return context;
}

export async function clearDeliveryContext({ repoRoot } = {}) {
  const root = findRepoRoot(repoRoot);
  const absolutePath = path.resolve(root, DELIVERY_CONTEXT_PATH);
  try {
    await fs.unlink(absolutePath);
    return { cleared: true };
  } catch (error) {
    if (error.code === "ENOENT") {
      return { cleared: true };
    }
    throw error;
  }
}

export async function consumeDeliveryContext({ repoRoot, context } = {}) {
  const root = findRepoRoot(repoRoot);
  const absolutePath = path.resolve(root, DELIVERY_CONTEXT_PATH);
  const current = context || (await loadDeliveryContext({ repoRoot: root }));
  if (!current) {
    return { consumed: false, reason: "NO_CONTEXT" };
  }
  const updated = {
    ...current,
    consumed: true,
    consumedAt: new Date().toISOString(),
  };

  validateDeliveryContextResult(updated, root);

  const tempPath = `${absolutePath}.${process.pid}.${Date.now()}.tmp`;
  await fs.mkdir(path.dirname(absolutePath), { recursive: true, mode: 0o700 });
  await fs.writeFile(tempPath, `${JSON.stringify(updated, null, 2)}\n`, { mode: 0o600 });
  await fs.rename(tempPath, absolutePath);

  return updated;
}

export function validateDeliveryContext({
  context,
  snapshot,
  proposedCommitMessage = "",
} = {}) {
  if (!context) {
    return { valid: false, reason: "NO_CONTEXT" };
  }

  if (context.consumed) {
    return {
      valid: false,
      expired: true,
      reason: "CONTEXT_ALREADY_CONSUMED",
      message: `Active delivery context was already consumed at ${context.consumedAt}`,
    };
  }

  if (context.headSha !== snapshot?.headSha) {
    return {
      valid: false,
      expired: true,
      reason: "CONTEXT_HEAD_MISMATCH",
      message: `Active delivery context bound to HEAD ${context.headSha.slice(0, 8)} expired (current HEAD is ${snapshot?.headSha?.slice(0, 8)})`,
    };
  }

  if (context.snapshotHash !== snapshot?.snapshotHash) {
    return {
      valid: false,
      expired: true,
      reason: "CONTEXT_SNAPSHOT_MISMATCH",
      message: `Active delivery context bound to snapshot ${context.snapshotHash.slice(0, 8)} expired because staged diff changed`,
    };
  }

  if (context.branch !== snapshot?.branch) {
    return {
      valid: false,
      expired: true,
      reason: "CONTEXT_BRANCH_MISMATCH",
      message: `Active delivery context bound to branch ${context.branch} does not match current branch ${snapshot?.branch}`,
    };
  }

  // Conflict check: explicit US ID in proposed commit message vs context US ID
  const msgUsId = extractUsId(proposedCommitMessage);
  if (msgUsId && context.usId) {
    const normMsg = normalizeUsId(msgUsId);
    const normCtx = normalizeUsId(context.usId);
    if (normMsg !== normCtx && msgUsId !== context.usId) {
      return {
        valid: false,
        conflict: true,
        reason: "CONTEXT_US_CONFLICT",
        message: `Proposed commit message US ID (${msgUsId}) contradicts active delivery context US ID (${context.usId})`,
      };
    }
  }

  return { valid: true, context };
}

/**
 * Infer if a single scenario had its @wip tag removed in the staged diff of a single feature.
 */
export function inferWipRemovalScenario(stagedDiffText, stagedFiles) {
  if (!stagedDiffText || !Array.isArray(stagedFiles)) return null;

  const featureFiles = stagedFiles.filter((file) => file.endsWith(".feature"));
  if (featureFiles.length !== 1) return null;

  const targetFeature = featureFiles[0];
  const lines = stagedDiffText.split(/\r?\n/);
  const removedWipIndexes = [];
  let addedWipCount = 0;

  for (const [index, line] of lines.entries()) {
    if (line.startsWith("---") || line.startsWith("+++")) continue;
    if (line.startsWith("-") && /(?:^|\s)@wip(?:\s|$)/.test(line.slice(1))) {
      removedWipIndexes.push(index);
    }
    if (line.startsWith("+") && /(?:^|\s)@wip(?:\s|$)/.test(line.slice(1))) {
      addedWipCount += 1;
    }
  }

  if (removedWipIndexes.length !== 1 || addedWipCount !== 0) return null;

  const removedIndex = removedWipIndexes[0];
  let scenarioName = null;
  for (let index = removedIndex + 1; index < lines.length; index += 1) {
    const diffLine = lines[index];
    if (diffLine.startsWith("diff --git ") || diffLine.startsWith("@@")) break;
    if (diffLine.startsWith("---") || diffLine.startsWith("+++")) continue;

    const sourceLine = /^[ +\-]/.test(diffLine) ? diffLine.slice(1).trim() : diffLine.trim();
    if (!sourceLine || sourceLine.startsWith("#") || sourceLine.startsWith("@")) continue;

    const scenarioMatch = sourceLine.match(
      /^(?:Scenario(?: Outline)?|Example|Escenario|Esquema del escenario):\s*(.+)$/i
    );
    if (scenarioMatch) {
      scenarioName = scenarioMatch[1].trim();
    }
    break;
  }

  if (scenarioName) {
    return {
      inferred: true,
      intent: "close_scenario",
      featureFile: targetFeature,
      scenarioName,
    };
  }

  return null;
}
