import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { z } from "zod";
import { findRepoRoot, assertSafeRepoPath } from "./repo-root.mjs";
import { verifyCommitEvidence, acquireRepairLock } from "./delivery-ledger.mjs";
import { inspectCi } from "./ci-provider.mjs";
import { extractUsId } from "./git-snapshot.mjs";

const sha = z.string().regex(/^[a-f0-9]{40}$/);
export const ManualClosureInputSchema = z.object({
  usId: z.string().regex(/^[0-9]+(?:\.[0-9]+)?$/),
  anchorSha: sha,
  verifiedSha: sha,
  operator: z.string().trim().min(4).max(120),
  reason: z.string().trim().min(24).max(2000),
}).strict();
const AuditSchema = ManualClosureInputSchema.extend({
  schemaVersion: z.literal(1),
  status: z.literal("accepted_manual"),
  recordedAt: z.string().datetime(),
  origin: z.string(),
  scopeFiles: z.array(z.string()).min(1),
  coveredShas: z.array(sha).min(1),
  ciUrl: z.string().nullable(),
  auditId: z.string().regex(/^[a-f0-9]{64}$/),
});
const directory = ".delivery/runtime/manual-closures";
const git = (root, ...args) => execFileSync("git", args, {
  cwd: root, encoding: "utf8", timeout: 15000, stdio: ["ignore", "pipe", "pipe"],
}).trim();
const digest = ({ auditId, ...value }) => crypto.createHash("sha256").update(JSON.stringify(value)).digest("hex");
function requireCondition(value, code) { if (!value) throw Object.assign(new Error(code), { code }); }
function ancestor(root, older, newer) { git(root, "merge-base", "--is-ancestor", older, newer); }

async function verifiedScope(root, entry) {
  ancestor(root, entry.verifiedSha, entry.anchorSha);
  const proof = await verifyCommitEvidence({ repoRoot: root, commitSha: entry.verifiedSha });
  requireCondition(proof.valid && proof.entry.usId === entry.usId && proof.entry.intent === "close_us" &&
    proof.record.status === "passed" && proof.record.gate.id === "D" &&
    proof.record.checks.some(check => check.id === "e2e_dev" && check.status === "passed"), "MANUAL_CLOSURE_REQUIRES_PRIOR_GATE_D");
  // Only publishing, documentation and Delivery maintenance may follow the tested app.
  const changes = git(root, "diff", "--name-only", entry.verifiedSha, entry.anchorSha).split("\n").filter(Boolean);
  requireCondition(changes.every(file => /^(?:\.github\/workflows\/|tools\/delivery-mcp\/|docs\/|\.agents\/skills\/|\.delivery\/README\.md$|AGENTS\.md$|README\.md$|Makefile$)/.test(file)), "MANUAL_CLOSURE_APP_CHANGED");
  return proof.record.gate.parameters.scopeFeatures;
}

/** Read a separate operator decision. Never create, copy or rewrite a gate receipt. */
export async function readManualClosure({ repoRoot, usId } = {}) {
  if (!/^[0-9]+(?:\.[0-9]+)?$/.test(usId || "")) return null;
  const root = findRepoRoot(repoRoot);
  const relative = `${directory}/${usId}.json`;
  assertSafeRepoPath(root, relative, "Manual closure audit");
  let text;
  try { text = await fs.readFile(path.join(root, relative), "utf8"); }
  catch (error) { if (error.code === "ENOENT") return null; throw error; }
  const record = AuditSchema.parse(JSON.parse(text));
  requireCondition(record.usId === usId && record.auditId === digest(record) &&
    record.origin === git(root, "config", "--get", "remote.origin.url"), "MANUAL_CLOSURE_AUDIT_INVALID");
  ancestor(root, record.anchorSha, "HEAD");
  const scope = await verifiedScope(root, record);
  requireCondition(JSON.stringify(scope) === JSON.stringify(record.scopeFiles) &&
    JSON.stringify(git(root, "rev-list", `${record.verifiedSha}..${record.anchorSha}`).split("\n").filter(Boolean)) === JSON.stringify(record.coveredShas), "MANUAL_CLOSURE_AUDIT_INVALID");
  return record;
}

export function manualClosureResult(root, record, scopeFiles = []) {
  if (!record || (scopeFiles.length && JSON.stringify([...scopeFiles].sort()) !== JSON.stringify([...record.scopeFiles].sort()))) return null;
  const laterSubjects = git(root, "log", "--format=%s", `${record.anchorSha}..HEAD`).split("\n");
  if (laterSubjects.some(subject => extractUsId(subject) === record.usId)) return null;
  return { finalized: true, status: "accepted_manual", usId: record.usId, acceptedSha: record.anchorSha,
    verifiedSha: record.verifiedSha, scopeFiles: record.scopeFiles, auditId: record.auditId,
    message: "Closed by explicit user acceptance of Staging; prior instrumented evidence remains attached to its original SHA." };
}

export async function acceptManualClosure({ repoRoot, ciProvider = null, ...input } = {}) {
  let release;
  try {
    const root = findRepoRoot(repoRoot);
    const values = ManualClosureInputSchema.parse(input);
    release = await acquireRepairLock({ repoRoot: root, lockName: `manual-closure-${values.usId.replaceAll('.', '-')}` });
    const previous = await readManualClosure({ repoRoot: root, usId: values.usId });
    if (previous) {
      requireCondition(Object.entries(values).every(([key, value]) => previous[key] === value), "MANUAL_CLOSURE_CONFLICT");
      return { finalized: true, status: "accepted_manual", idempotent: true, auditId: previous.auditId, acceptedSha: previous.anchorSha };
    }
    requireCondition(git(root, "rev-parse", "HEAD") === values.anchorSha, "MANUAL_CLOSURE_HEAD_MISMATCH");
    const remote = git(root, "ls-remote", "origin", "refs/heads/main").split(/\s+/)[0];
    requireCondition(remote === values.anchorSha, "MANUAL_CLOSURE_UNPUBLISHED_HEAD");
    const scopeFiles = await verifiedScope(root, values);
    requireCondition(scopeFiles?.length, "MANUAL_CLOSURE_SCOPE_MISSING");
    const ci = await inspectCi({ repoRoot: root, sha: values.anchorSha, provider: ciProvider });
    requireCondition(ci.sha === values.anchorSha && ci.status === "passed", "MANUAL_CLOSURE_CI_NOT_GREEN");
    requireCondition(git(root, "rev-parse", "HEAD") === values.anchorSha, "MANUAL_CLOSURE_HEAD_MISMATCH");
    const record = { ...values, schemaVersion: 1, status: "accepted_manual", recordedAt: new Date().toISOString(),
      origin: git(root, "config", "--get", "remote.origin.url"), scopeFiles,
      coveredShas: git(root, "rev-list", `${values.verifiedSha}..${values.anchorSha}`).split("\n").filter(Boolean), ciUrl: ci.url || null };
    requireCondition(record.coveredShas.length, "MANUAL_CLOSURE_NO_PUBLICATION_CHANGES");
    const audited = AuditSchema.parse({ ...record, auditId: digest(record) });
    const relative = `${directory}/${values.usId}.json`;
    assertSafeRepoPath(root, relative, "Manual closure audit");
    await fs.mkdir(path.join(root, directory), { recursive: true, mode: 0o700 });
    await fs.writeFile(path.join(root, relative), `${JSON.stringify(audited, null, 2)}\n`, { flag: "wx", mode: 0o600 });
    return { ...manualClosureResult(root, audited), idempotent: false, recordPath: relative };
  } catch (error) {
    return { finalized: false, status: "blocked", reason: error.code || "MANUAL_CLOSURE_INVALID" };
  } finally { if (release) await release(); }
}
