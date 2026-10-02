import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { recordHistoricalAcceptance, readHistoricalAcceptance } from "../lib/historical-acceptance.mjs";
import { MockCiProvider } from "../lib/ci-provider.mjs";
import { recordCommitEvidence, queryCommitEvidence, getActiveCiIncidents } from "../lib/delivery-ledger.mjs";
import { inspectClosureReadiness } from "../lib/closure-preflight.mjs";
import { finalizeDelivery, verifyHeadDelivery } from "../lib/delivery-finalize.mjs";
import { prepareDelivery } from "../lib/prepare-delivery.mjs";
import { DeliveryHistoricalAcceptanceInputSchema } from "../lib/input-schema.mjs";

const feature = "app/src/test/resources/features/calendar.feature";
const git = (root, ...args) => execFileSync("git", args, { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
async function write(root, file, content) {
  await fs.mkdir(path.dirname(path.join(root, file)), { recursive: true });
  await fs.writeFile(path.join(root, file), content);
}
function commit(root, subject) { git(root, "add", "."); git(root, "commit", "-m", subject); return git(root, "rev-parse", "HEAD"); }
async function fixture(t, baselineChange = "feature") {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "delivery-history-"));
  const remote = await fs.mkdtemp(path.join(os.tmpdir(), "delivery-history-origin-"));
  t.after(() => Promise.all([fs.rm(root, { recursive: true, force: true }), fs.rm(remote, { recursive: true, force: true })]));
  git(root, "init", "-b", "main");
  git(root, "config", "user.name", "Delivery Tests"); git(root, "config", "user.email", "test@example.com");
  git(root, "config", "commit.gpgsign", "false");
  await fs.cp(new URL("../../../.delivery/schemas/", import.meta.url), path.join(root, ".delivery/schemas"), { recursive: true });
  await fs.copyFile(new URL("../../../.delivery/policy.v1.json", import.meta.url), path.join(root, ".delivery/policy.v1.json"));
  await fs.copyFile(new URL("../../../.gitignore", import.meta.url), path.join(root, ".gitignore"));
  await write(root, "broken-test.txt", "failed assertion\n");
  await write(root, "docs/old.md", "old document\n");
  const failedSha = commit(root, "chore: initialize failing history");
  await recordCommitEvidence({ repoRoot: root, commitSha: failedSha, verificationStatus: "not_run",
    notRunReason: "HISTORICAL_FIXTURE", treeSha: git(root, "rev-parse", "HEAD^{tree}"), branch: "main", usId: "56", stagedFiles: ["broken-test.txt"] });
  const originalLedger = await fs.readFile(path.join(root, ".delivery/runtime/ledger.json"), "utf8");
  await fs.rm(path.join(root, "broken-test.txt"));
  const correctionSha = commit(root, "fix: remove nondeterministic assertion");
  await write(root, feature, "Feature: Calendar\n  @wip\n  Scenario: connect\n    Given provider is ready\n");
  if (baselineChange === "app") await write(root, "app/src/main/java/Profile.kt", "class Profile\n");
  if (baselineChange === "tooling") await write(root, "tools/delivery-mcp/new.mjs", "export const gate = 1;\n");
  if (baselineChange === "delete") await fs.rm(path.join(root, "docs/old.md"));
  if (baselineChange === "rename") await fs.rename(path.join(root, "docs/old.md"), path.join(root, "docs/new.md"));
  const baselineSha = commit(root, "test[57]: approve pending calendar scenarios");
  git(remote, "init", "--bare", "-b", "main"); git(root, "remote", "add", "origin", remote); git(root, "push", "-u", "origin", "main");
  const values = { failedSha, correctionSha, baselineSha, passedSha: baselineSha, anchorSha: baselineSha,
    usId: "57", operator: "Codex on behalf of the user", reason: "User explicitly accepts the reviewed human correction and the historical pending-scenario baseline." };
  const provider = new MockCiProvider({ [failedSha]: { status: "failed" }, [baselineSha]: { status: "passed" } });
  return { root, remote, values, provider, originalLedger,
    accept: overrides => recordHistoricalAcceptance({ repoRoot: root, ciProvider: provider, ...values, ...overrides }) };
}
const passingCheck = async ({ check, logPath }) => ({ id: check.id, status: "passed", durationMs: 1, exitCode: 0,
  summaryLines: ["fixture passed"], locations: [], logPath, diagnostic: null });

// These fixtures fake external CI/check execution, not history, gate selection or evidence binding.
test("explicit history acceptance is idempotent, preserves evidence and stays distinct from verified", async t => {
  const f = await fixture(t);
  assert.equal((await getActiveCiIncidents({ repoRoot: f.root, ciProvider: f.provider })).length, 1);
  const result = await f.accept(); assert.equal(result.status, "accepted_historical"); assert.equal(result.accepted, true);
  const audit = await fs.readFile(path.join(f.root, result.recordPath), "utf8");
  const repeated = await f.accept(); assert.equal(repeated.idempotent, true); assert.equal(repeated.auditId, result.auditId);
  assert.equal(await fs.readFile(path.join(f.root, result.recordPath), "utf8"), audit);
  assert.equal(await fs.readFile(path.join(f.root, ".delivery/runtime/ledger.json"), "utf8"), f.originalLedger);
  assert.equal((await queryCommitEvidence({ repoRoot: f.root, commitSha: f.values.baselineSha })).state, "missing");
  const incidents = await getActiveCiIncidents({ repoRoot: f.root, ciProvider: f.provider });
  assert.equal(incidents.length, 0); assert.equal(incidents.allIncidents[0].status, "accepted_historical");
  const preflight = await inspectClosureReadiness({ repoRoot: f.root, usId: "57", ciProvider: f.provider });
  assert.equal(preflight.status, "ready"); assert.equal(preflight.commits[0].state, "accepted_historical");
  assert.deepEqual(preflight.acceptedHistoricalCommits, [f.values.baselineSha]);
  const finalize = await finalizeDelivery({ repoRoot: f.root, usId: "57", ciProvider: f.provider, unpushedCommitsResolver: () => [] });
  assert.equal(finalize.reason, "INVALID_HEAD_EVIDENCE"); assert.equal(finalize.finalized, false);
});

test("acceptance requires exact SHAs, actor, reason, lineage, published anchor and green authoritative CI", async t => {
  const f = await fixture(t);
  assert.equal(DeliveryHistoricalAcceptanceInputSchema.safeParse({ ...f.values, failedSha: "abc1234" }).success, false);
  assert.equal(DeliveryHistoricalAcceptanceInputSchema.safeParse({ ...f.values, reason: "accepted" }).success, false);
  assert.equal((await f.accept({ correctionSha: f.values.failedSha })).accepted, false);
  assert.equal((await f.accept({ anchorSha: f.values.correctionSha })).accepted, false);
  f.provider.setFixture(f.values.baselineSha, { status: "provider_error" });
  assert.equal((await f.accept()).accepted, false);
  f.provider.setFixture(f.values.baselineSha, { status: "in_progress" });
  assert.equal((await f.accept()).accepted, false);
  f.provider.setFixture(f.values.baselineSha, { status: "passed" });
  f.provider.setFixture(f.values.failedSha, { status: "passed" });
  assert.equal((await f.accept()).accepted, false);
  assert.deepEqual(await fs.readdir(path.join(f.root, ".delivery/runtime/historical-acceptance")).catch(() => []), []);
});

test("baseline refuses app/tooling changes, deletions and renames", async t => {
  for (const change of ["app", "tooling", "delete", "rename"]) {
    const f = await fixture(t, change);
    assert.equal((await f.accept()).reason, "HISTORICAL_BASELINE_NOT_SCENARIOS", change);
  }
});

test("existing baseline evidence cannot be replaced with acceptance", async t => {
  const f = await fixture(t);
  await recordCommitEvidence({ repoRoot: f.root, commitSha: f.values.baselineSha, verificationStatus: "not_run",
    notRunReason: "DO_NOT_OVERRIDE", parentSha: f.values.correctionSha,
    treeSha: git(f.root, "rev-parse", "HEAD^{tree}"), branch: "main", usId: "57", stagedFiles: [feature] });
  assert.equal((await f.accept()).accepted, false);
  const state = await queryCommitEvidence({ repoRoot: f.root, commitSha: f.values.baselineSha });
  assert.equal(state.state, "not_run");
});

test("corrupt audit, changed remote and provider failures refuse consumption", async t => {
  const f = await fixture(t);
  const result = await f.accept();
  f.provider.setFixture(f.values.baselineSha, { status: "failed" });
  await assert.rejects(readHistoricalAcceptance({ repoRoot: f.root, ciProvider: f.provider }), /HISTORICAL_CI_NOT_GREEN/);
  f.provider.setFixture(f.values.baselineSha, { status: "passed" });
  git(f.root, "remote", "set-url", "origin", f.root);
  await assert.rejects(readHistoricalAcceptance({ repoRoot: f.root, ciProvider: f.provider }), /HISTORICAL_REPOSITORY_MISMATCH/);
  git(f.root, "remote", "set-url", "origin", f.remote);
  const file = path.join(f.root, result.recordPath);
  const record = JSON.parse(await fs.readFile(file, "utf8")); record.reason = "Tampered acceptance without the user's original authority.";
  await fs.writeFile(file, JSON.stringify(record));
  await assert.rejects(readHistoricalAcceptance({ repoRoot: f.root, ciProvider: f.provider }), /HISTORICAL_AUDIT_CORRUPT/);
  assert.equal((await inspectClosureReadiness({ repoRoot: f.root, usId: "57", ciProvider: f.provider })).status, "blocked");
});

test("later and unrelated commits stay unaccepted, preparation keeps Gate C and final HEAD keeps Gate D/device/CI", async t => {
  const f = await fixture(t); assert.equal((await f.accept()).accepted, true);
  await write(f.root, "app/src/main/java/com/loresuelvo/serviceprovider/ui/Profile.kt", "class Profile\n");
  git(f.root, "add", "app/src/main/java");
  const prepared = await prepareDelivery({ repoRoot: f.root, intent: "prepare_commit", proposedCommitMessage: "feat[57]: complete calendar",
    ciProvider: f.provider, executeCheck: passingCheck, mode: "sync" });
  assert.equal(prepared.status, "passed"); assert.equal(prepared.gate.id, "C");
  assert.deepEqual(prepared.gate.checkIds, ["lint_dev", "jvm_test_dev", "build_dev"]);
  await write(f.root, feature, "Feature: Calendar\n  Scenario: connected\n    Given provider is ready\n");
  const current = commit(f.root, "feat[57]: complete calendar"); git(f.root, "push", "origin", "main");
  const preflight = await inspectClosureReadiness({ repoRoot: f.root, usId: "57", ciProvider: f.provider });
  assert.equal(preflight.status, "blocked"); assert.equal(preflight.commits.find(commit => commit.sha === current).state, "missing");
  const checks = [];
  const verified = await verifyHeadDelivery({ repoRoot: f.root, usId: "57", intent: "close_us", scopeFiles: [feature],
    executeCheck: async args => { checks.push(args.check.id); return passingCheck(args); } });
  assert.equal(verified.verified, true); assert.ok(checks.includes("android_device")); assert.ok(checks.includes("e2e_dev"));
  f.provider.setFixture(current, { status: "failed" });
  const blocked = await finalizeDelivery({ repoRoot: f.root, usId: "57", intent: "close_us", scopeFiles: [feature],
    ciProvider: f.provider, unpushedCommitsResolver: () => [] });
  assert.equal(blocked.finalized, false);
  f.provider.setFixture(current, { status: "passed" });
  const finalized = await finalizeDelivery({ repoRoot: f.root, usId: "57", intent: "close_us", scopeFiles: [feature],
    ciProvider: f.provider, unpushedCommitsResolver: () => [] });
  assert.equal(finalized.finalized, true);
  assert.deepEqual(finalized.acceptedHistoricalCommits, [f.values.baselineSha]);
  assert.deepEqual(finalized.acceptedHistoricalFailures, [f.values.failedSha]);
  assert.equal((await queryCommitEvidence({ repoRoot: f.root, commitSha: f.values.baselineSha })).state, "missing");
});
