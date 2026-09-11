import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { MockCiProvider } from "../lib/ci-provider.mjs";
import {
  acquireRepairLock,
  getRepairAuthorization,
  recordCommitEvidence,
  saveRepairAuthorization,
} from "../lib/delivery-ledger.mjs";
import {
  hasUnrecoveredRepair,
  isRepairAuthorizationRecovered,
  recoverStaleRepairAuthorization,
} from "../lib/repair-recovery.mjs";

async function fixtureRepo(t) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-repair-recovery-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  execFileSync("git", ["init", "-b", "main"], { cwd: root, stdio: "ignore" });
  execFileSync("git", ["config", "user.name", "Delivery Tests"], { cwd: root });
  execFileSync("git", ["config", "user.email", "delivery-tests@example.com"], { cwd: root });
  await fs.cp(new URL("../../../.delivery/schemas/", import.meta.url), path.join(root, ".delivery/schemas"), { recursive: true });
  await fs.writeFile(path.join(root, "README.md"), "fixture\n", "utf8");
  execFileSync("git", ["add", "README.md"], { cwd: root });
  execFileSync("git", ["commit", "-m", "chore: initialize fixture"], { cwd: root, stdio: "ignore" });
  const targetSha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  const treeSha = execFileSync("git", ["rev-parse", "HEAD^{tree}"], { cwd: root, encoding: "utf8" }).trim();
  await recordCommitEvidence({
    repoRoot: root,
    commitSha: targetSha,
    verificationStatus: "not_run",
    notRunReason: "CI_FAILURE_FIXTURE",
    branch: "main",
    parentSha: null,
    treeSha,
    stagedFiles: ["README.md"],
    usId: "35",
  });
  const remote = await fs.mkdtemp(path.join(os.tmpdir(), "android-repair-origin-"));
  t.after(() => fs.rm(remote, { recursive: true, force: true }));
  execFileSync("git", ["init", "--bare", "-b", "main", remote], { stdio: "ignore" });
  execFileSync("git", ["remote", "add", "origin", remote], { cwd: root });
  execFileSync("git", ["push", "-u", "origin", "main"], { cwd: root, stdio: "ignore" });
  await fs.writeFile(path.join(root, "repair.txt"), "repair\n", "utf8");
  execFileSync("git", ["add", "repair.txt"], { cwd: root });
  execFileSync("git", ["commit", "-m", "fix[35]: repair fixture"], { cwd: root, stdio: "ignore" });
  const expectedAuthorizationCommitSha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  return { root, repoRoot: root, remote, targetSha, expectedAuthorizationCommitSha, treeSha };
}

async function authorizeFixture({ root, targetSha, expectedAuthorizationCommitSha }) {
  await saveRepairAuthorization({
    repoRoot: root,
    authorization: {
      targetSha,
      commitSha: expectedAuthorizationCommitSha,
      state: "submitted",
      snapshotHash: "a".repeat(64),
    },
  });
}

function failedCi(targetSha, expectedAuthorizationCommitSha, expectedStatus = "not_found") {
  const provider = new MockCiProvider();
  provider.setFixture(targetSha, { status: "failed" });
  if (expectedStatus !== "not_found") provider.setFixture(expectedAuthorizationCommitSha, { status: expectedStatus });
  return provider;
}

test("recovery is audited, preserves the prior binding, and is idempotent", async (t) => {
  const fixture = await fixtureRepo(t);
  await authorizeFixture(fixture);
  const provider = failedCi(fixture.targetSha, fixture.expectedAuthorizationCommitSha);

  const first = await recoverStaleRepairAuthorization({ ...fixture, ciProvider: provider });
  assert.equal(first.status, "recovered");
  assert.equal(await isRepairAuthorizationRecovered({
    repoRoot: fixture.root,
    targetSha: fixture.targetSha,
    commitSha: fixture.expectedAuthorizationCommitSha,
  }), true);
  const current = await getRepairAuthorization({ repoRoot: fixture.root, targetSha: fixture.targetSha });
  assert.equal(current.commitSha, null);
  const events = await fs.readdir(path.join(fixture.root, ".delivery/runtime/repair-recovery"));
  assert.equal(events.length, 1);

  const historicalRecordedAt = new Date(Date.parse(first.recordedAt) - 1).toISOString();
  assert.equal(await hasUnrecoveredRepair({
    repoRoot: fixture.root,
    targetSha: fixture.targetSha,
    ledgerEntries: [
      { repairsSha: fixture.targetSha, commitSha: fixture.expectedAuthorizationCommitSha, recordedAt: historicalRecordedAt },
      { repairsSha: fixture.targetSha, commitSha: "f".repeat(40), recordedAt: first.recordedAt },
    ],
  }), false);
  assert.equal(await hasUnrecoveredRepair({
    repoRoot: fixture.root,
    targetSha: fixture.targetSha,
    ledgerEntries: [
      { repairsSha: fixture.targetSha, commitSha: fixture.expectedAuthorizationCommitSha, recordedAt: new Date(Date.parse(first.recordedAt) + 1).toISOString() },
    ],
  }), true);

  const second = await recoverStaleRepairAuthorization({ ...fixture, ciProvider: provider });
  assert.equal(second.status, "already_recovered");
  assert.equal((await fs.readdir(path.join(fixture.root, ".delivery/runtime/repair-recovery"))).length, 1);

  await fs.writeFile(path.join(fixture.root, "repair-next.txt"), "next repair\n", "utf8");
  execFileSync("git", ["add", "repair-next.txt"], { cwd: fixture.root });
  execFileSync("git", ["commit", "-m", "fix[35]: prepare a newer repair"], { cwd: fixture.root, stdio: "ignore" });
  const newerCommitSha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: fixture.root, encoding: "utf8" }).trim();
  await saveRepairAuthorization({
    repoRoot: fixture.root,
    authorization: {
      targetSha: fixture.targetSha,
      commitSha: newerCommitSha,
      state: "bound_to_commit",
      snapshotHash: "b".repeat(64),
    },
  });

  const repeated = await recoverStaleRepairAuthorization({ ...fixture, ciProvider: provider });
  assert.equal(repeated.status, "already_recovered");
  const newerAuthorization = await getRepairAuthorization({ repoRoot: fixture.root, targetSha: fixture.targetSha });
  assert.equal(newerAuthorization.commitSha, newerCommitSha);
  assert.equal(newerAuthorization.state, "bound_to_commit");
});

test("recovery refuses mismatched, remote, provider, and inactive states", async (t) => {
  const cases = ["mismatch", "remote", "provider", "inactive", "lock", "audit", "authorization"];
  for (const name of cases) {
    const fixture = await fixtureRepo(t);
    await authorizeFixture(fixture);
    if (name === "mismatch") {
      await fs.writeFile(path.join(fixture.root, "alternate.txt"), "alternate\n", "utf8");
      execFileSync("git", ["add", "alternate.txt"], { cwd: fixture.root });
      execFileSync("git", ["commit", "-m", "chore: alternate fixture"], { cwd: fixture.root, stdio: "ignore" });
      const alternate = execFileSync("git", ["rev-parse", "HEAD"], { cwd: fixture.root, encoding: "utf8" }).trim();
      const result = await recoverStaleRepairAuthorization({ ...fixture, expectedAuthorizationCommitSha: alternate, ciProvider: failedCi(fixture.targetSha, alternate) });
      assert.equal(result.reason, "REPAIR_AUTHORIZATION_COMMIT_MISMATCH", name);
    } else if (name === "remote") {
      execFileSync("git", ["push", "origin", "main"], { cwd: fixture.root, stdio: "ignore" });
      const result = await recoverStaleRepairAuthorization({ ...fixture, ciProvider: failedCi(fixture.targetSha, fixture.expectedAuthorizationCommitSha) });
      assert.equal(result.reason, "AUTHORIZATION_COMMIT_ALREADY_REMOTE", name);
    } else if (name === "lock") {
      const release = await acquireRepairLock({ repoRoot: fixture.root, targetSha: fixture.targetSha });
      try {
        const result = await recoverStaleRepairAuthorization({ ...fixture, lockTimeoutMs: 0, ciProvider: failedCi(fixture.targetSha, fixture.expectedAuthorizationCommitSha) });
        assert.equal(result.reason, "REPAIR_PUSH_ACTIVE", name);
      } finally {
        await release();
      }
    } else if (name === "audit") {
      const auditDir = path.join(fixture.root, ".delivery/runtime/repair-recovery");
      await fs.mkdir(auditDir, { recursive: true });
      await fs.writeFile(path.join(auditDir, "malformed.json"), "not-json\n", "utf8");
      const result = await recoverStaleRepairAuthorization({ ...fixture, ciProvider: failedCi(fixture.targetSha, fixture.expectedAuthorizationCommitSha) });
      assert.equal(result.reason, "REPAIR_RECOVERY_AUDIT_UNREADABLE", name);
    } else if (name === "authorization") {
      const authPath = path.join(fixture.root, ".delivery/runtime/repair-auth", `${fixture.targetSha}.json`);
      await fs.writeFile(authPath, "not-json\n", "utf8");
      const result = await recoverStaleRepairAuthorization({ ...fixture, ciProvider: failedCi(fixture.targetSha, fixture.expectedAuthorizationCommitSha) });
      assert.equal(result.reason, "REPAIR_AUTHORIZATION_UNREADABLE", name);
    } else {
      const provider = failedCi(fixture.targetSha, fixture.expectedAuthorizationCommitSha);
      if (name === "provider") provider.setFixture(fixture.expectedAuthorizationCommitSha, { status: "provider_error" });
      if (name === "inactive") provider.setFixture(fixture.targetSha, { status: "passed" });
      const result = await recoverStaleRepairAuthorization({ ...fixture, ciProvider: provider });
      const reason = name === "provider"
        ? "CI_PROVIDER_ERROR"
        : "TARGET_INCIDENT_NOT_ACTIVE";
      assert.equal(result.reason, reason, name);
    }
  }
});

test("recovery refuses every recognized authorization CI status", async (t) => {
  const statuses = ["queued", "in_progress", "passed", "failed", "cancelled", "timed_out"];
  for (const ciStatus of statuses) {
    const fixture = await fixtureRepo(t);
    await authorizeFixture(fixture);
    const provider = failedCi(fixture.targetSha, fixture.expectedAuthorizationCommitSha, ciStatus);
    const result = await recoverStaleRepairAuthorization({ ...fixture, ciProvider: provider });
    assert.equal(result.reason, "AUTHORIZATION_COMMIT_CI_RECOGNIZED", ciStatus);
    assert.equal(result.ciStatus, ciStatus, ciStatus);
  }
});

test("two concurrent recovery calls serialize per repository and do not cross roots", async (t) => {
  const left = await fixtureRepo(t);
  const right = await fixtureRepo(t);
  await authorizeFixture(left);
  await authorizeFixture(right);
  const leftProvider = failedCi(left.targetSha, left.expectedAuthorizationCommitSha);
  const rightProvider = failedCi(right.targetSha, right.expectedAuthorizationCommitSha);
  const [leftResults, rightResult] = await Promise.all([
    Promise.all([
      recoverStaleRepairAuthorization({ ...left, ciProvider: leftProvider }),
      recoverStaleRepairAuthorization({ ...left, ciProvider: leftProvider }),
    ]),
    recoverStaleRepairAuthorization({ ...right, ciProvider: rightProvider }),
  ]);
  assert.deepEqual(leftResults.map((result) => result.status).sort(), ["already_recovered", "recovered"]);
  assert.equal(rightResult.status, "recovered");
  assert.equal((await fs.readdir(path.join(left.root, ".delivery/runtime/repair-recovery"))).length, 1);
  assert.equal((await fs.readdir(path.join(right.root, ".delivery/runtime/repair-recovery"))).length, 1);
  assert.equal(await hasUnrecoveredRepair({
    repoRoot: left.root,
    targetSha: left.targetSha,
    ledgerEntries: [{ repairsSha: left.targetSha, commitSha: left.expectedAuthorizationCommitSha, recordedAt: new Date(0).toISOString() }],
  }), false);
});
