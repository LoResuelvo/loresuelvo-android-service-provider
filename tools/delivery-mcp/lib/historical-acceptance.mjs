import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { z } from "zod";
import { findRepoRoot, assertSafeRepoPath } from "./repo-root.mjs";
import { inspectCi } from "./ci-provider.mjs";
import { acquireRepairLock, queryCommitEvidence } from "./delivery-ledger.mjs";
import { DeliveryHistoricalAcceptanceInputSchema } from "./input-schema.mjs";
import { extractUsId } from "./git-snapshot.mjs";
import { wipTagLines } from "./gherkin-tags.mjs";

const AUDIT_DIR = ".delivery/runtime/historical-acceptance";
const AuditContentSchema = DeliveryHistoricalAcceptanceInputSchema.extend({
  schemaVersion: z.literal(1),
  kind: z.literal("accepted_historical"),
  recordedAt: z.string().datetime(),
  anchorTree: z.string().regex(/^[a-f0-9]{40}$/),
  baselineTree: z.string().regex(/^[a-f0-9]{40}$/),
  originHash: z.string().regex(/^[a-f0-9]{64}$/),
});
const AuditSchema = AuditContentSchema.extend({ auditId: z.string().regex(/^[a-f0-9]{64}$/) });
const digest = value => crypto.createHash("sha256").update(value).digest("hex");
const auditDigest = value => {
  const { auditId, ...content } = value;
  return digest(JSON.stringify(AuditContentSchema.parse(content)));
};

function refuse(code) { const error = new Error(code); error.code = code; throw error; }
function git(root, args) {
  return execFileSync("git", args, { cwd: root, encoding: "utf8", timeout: 10000,
    stdio: ["ignore", "pipe", "pipe"], maxBuffer: 5 * 1024 * 1024 }).trim();
}
function ancestor(root, older, newer) {
  try { git(root, ["merge-base", "--is-ancestor", older, newer]); }
  catch { refuse("HISTORICAL_ANCESTRY_MISMATCH"); }
}
function remoteTip(root) {
  const output = git(root, ["ls-remote", "origin", "refs/heads/main"]);
  const matches = output.split(/\r?\n/).map(line => line.split(/\s+/))
    .filter(parts => parts[1] === "refs/heads/main");
  if (matches.length !== 1 || !/^[a-f0-9]{40}$/.test(matches[0][0])) refuse("HISTORICAL_REMOTE_UNKNOWN");
  return matches[0][0];
}
function baselinePaths(root, entry) {
  const parents = git(root, ["rev-list", "--parents", "-n", "1", entry.baselineSha]).split(" ");
  if (parents.length !== 2) refuse("HISTORICAL_BASELINE_NOT_SCENARIOS");
  const changes = git(root, ["diff-tree", "--no-commit-id", "--name-status", "--no-renames", "-r", entry.baselineSha])
    .split("\n").filter(Boolean).map(line => line.split("\t"));
  let features = 0;
  for (const [status, file] of changes) {
    if (!["A", "M"].includes(status)) refuse("HISTORICAL_BASELINE_NOT_SCENARIOS");
    if (/^app\/src\/test\/resources\/features\/[^\s]+\.feature$/.test(file)) {
      assertSafeRepoPath(root, file, "Historical feature");
      if (!wipTagLines(git(root, ["show", `${entry.baselineSha}:${file}`])).length) refuse("HISTORICAL_BASELINE_NOT_SCENARIOS");
      features++;
    } else if (file !== "README.md" && !/^docs\/[^\s]+\.md$/.test(file)) {
      refuse("HISTORICAL_BASELINE_NOT_SCENARIOS");
    }
  }
  if (!features || extractUsId(git(root, ["show", "-s", "--format=%s", entry.baselineSha])) !== entry.usId) {
    refuse("HISTORICAL_BASELINE_NOT_SCENARIOS");
  }
}
function validateGit(root, entry, registering = false) {
  for (const name of ["failedSha", "correctionSha", "passedSha", "baselineSha", "anchorSha"]) {
    if (git(root, ["rev-parse", "--verify", `${entry[name]}^{commit}`]) !== entry[name]) refuse("HISTORICAL_COMMIT_MISMATCH");
  }
  if (entry.failedSha === entry.correctionSha || entry.correctionSha === entry.passedSha) refuse("HISTORICAL_ANCESTRY_MISMATCH");
  ancestor(root, entry.failedSha, entry.correctionSha);
  ancestor(root, entry.correctionSha, entry.passedSha);
  ancestor(root, entry.baselineSha, entry.passedSha);
  ancestor(root, entry.passedSha, entry.anchorSha);
  const head = git(root, ["rev-parse", "HEAD"]);
  const remote = remoteTip(root);
  if (registering && (head !== entry.anchorSha || remote !== entry.anchorSha)) refuse("HISTORICAL_ANCHOR_MISMATCH");
  ancestor(root, entry.anchorSha, head);
  ancestor(root, entry.anchorSha, remote);
  if (digest(git(root, ["config", "--get", "remote.origin.url"])) !== entry.originHash ||
      git(root, ["rev-parse", `${entry.anchorSha}^{tree}`]) !== entry.anchorTree ||
      git(root, ["rev-parse", `${entry.baselineSha}^{tree}`]) !== entry.baselineTree) refuse("HISTORICAL_REPOSITORY_MISMATCH");
  baselinePaths(root, entry);
}
async function validateEvidence(root, entry) {
  const evidence = await queryCommitEvidence({ repoRoot: root, commitSha: entry.baselineSha });
  if (!["missing", "verified"].includes(evidence.state)) refuse("HISTORICAL_BASELINE_EVIDENCE_INVALID");
  return evidence;
}
async function validateCi(root, entry, provider, registering = false, signal = null) {
  const passed = await inspectCi({ repoRoot: root, sha: entry.passedSha, provider, signal });
  if (passed.sha !== entry.passedSha || passed.status !== "passed") refuse("HISTORICAL_CI_NOT_GREEN");
  if (registering) {
    const failed = await inspectCi({ repoRoot: root, sha: entry.failedSha, provider, signal });
    if (failed.sha !== entry.failedSha || !["failed", "cancelled", "timed_out"].includes(failed.status)) refuse("HISTORICAL_FAILURE_NOT_CONFIRMED");
  }
}

/** Read and revalidate explicit historical exceptions; never manufacture gate evidence. */
export async function readHistoricalAcceptance({ repoRoot, ciProvider = null, signal = null } = {}) {
  const root = findRepoRoot(repoRoot);
  assertSafeRepoPath(root, AUDIT_DIR, "Historical acceptance audit");
  let files;
  try { files = await fs.readdir(path.join(root, AUDIT_DIR)); }
  catch (error) { if (error.code === "ENOENT") return []; throw error; }
  const records = [];
  for (const file of files.filter(name => name.endsWith(".json"))) {
    signal?.throwIfAborted();
    const entry = AuditSchema.parse(JSON.parse(await fs.readFile(path.join(root, AUDIT_DIR, file), "utf8")));
    if (entry.auditId !== auditDigest(entry) || file !== `${entry.failedSha}-${entry.baselineSha}.json`) refuse("HISTORICAL_AUDIT_CORRUPT");
    validateGit(root, entry);
    await validateEvidence(root, entry);
    await validateCi(root, entry, ciProvider, false, signal);
    records.push(entry);
  }
  return records;
}

/** Explicit operator acceptance of one reviewed human correction and one scenario baseline. */
export async function recordHistoricalAcceptance({ repoRoot, ciProvider = null, ...input } = {}) {
  let release;
  try {
    const root = findRepoRoot(repoRoot);
    const values = DeliveryHistoricalAcceptanceInputSchema.parse(input);
    release = await acquireRepairLock({ repoRoot: root, targetSha: values.failedSha, timeoutMs: 100 });
    const records = await readHistoricalAcceptance({ repoRoot: root, ciProvider });
    const previous = records.find(entry => entry.failedSha === values.failedSha || entry.baselineSha === values.baselineSha);
    if (previous) {
      if (Object.keys(values).some(key => previous[key] !== values[key])) refuse("HISTORICAL_ACCEPTANCE_CONFLICT");
      return { accepted: true, status: "accepted_historical", idempotent: true, auditId: previous.auditId };
    }
    const entry = {
      ...values, schemaVersion: 1, kind: "accepted_historical", recordedAt: new Date().toISOString(),
      anchorTree: git(root, ["rev-parse", `${values.anchorSha}^{tree}`]),
      baselineTree: git(root, ["rev-parse", `${values.baselineSha}^{tree}`]),
      originHash: digest(git(root, ["config", "--get", "remote.origin.url"])),
    };
    validateGit(root, entry, true);
    if ((await validateEvidence(root, entry)).state !== "missing") refuse("HISTORICAL_EVIDENCE_ALREADY_EXISTS");
    await validateCi(root, entry, ciProvider, true);
    validateGit(root, entry, true);
    const record = { ...entry, auditId: auditDigest(entry) };
    const relative = `${AUDIT_DIR}/${entry.failedSha}-${entry.baselineSha}.json`;
    assertSafeRepoPath(root, relative, "Historical acceptance audit");
    const destination = path.join(root, relative);
    const temporary = `${destination}.${process.pid}.tmp`;
    await fs.mkdir(path.dirname(destination), { recursive: true, mode: 0o700 });
    await fs.writeFile(temporary, `${JSON.stringify(record, null, 2)}\n`, { flag: "wx", mode: 0o600 });
    await fs.rename(temporary, destination);
    return { accepted: true, status: "accepted_historical", idempotent: false, auditId: record.auditId, recordPath: relative };
  } catch (error) {
    return { accepted: false, status: "blocked", reason: error.code || "HISTORICAL_ACCEPTANCE_INVALID" };
  } finally { if (release) await release(); }
}
