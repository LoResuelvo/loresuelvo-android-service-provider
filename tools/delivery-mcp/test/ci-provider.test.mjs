import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  GitHubActionsProvider,
  MockCiProvider,
  inspectCi,
  resolveRepository,
  setCiProvider,
} from "../lib/ci-provider.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

test("MockCiProvider normalizes queued, running, passed, failed, and missing CI", async () => {
  const sha = "a".repeat(40);
  const provider = new MockCiProvider({
    [sha]: { status: "queued", workflow: { id: 101, name: "Android CI" } },
  });
  assert.equal((await provider.inspectCommit(sha)).status, "queued");
  provider.setFixture(sha, { status: "in_progress" });
  assert.equal((await provider.inspectCommit(sha)).status, "in_progress");
  provider.setFixture(sha, { status: "passed" });
  assert.equal((await provider.inspectCommit(sha)).retryable, false);
  provider.setFixture(sha, {
    status: "failed",
    failure: { message: "Gradle task failed", excerpt: "Execution failed for :app:testDevDebugUnitTest" },
  });
  const failed = await provider.inspectCommit(sha);
  assert.equal(failed.status, "failed");
  assert.equal(failed.retryable, true);
  assert.match(failed.failure.message, /Gradle/);
  assert.match(failed.failure.excerpt, /Execution failed/);
  assert.equal((await provider.inspectCommit("b".repeat(40))).status, "not_found");
  provider.setFixture(sha, { status: "timed_out" });
  assert.equal((await provider.inspectCommit(sha)).retryable, true);
});

test("GitHubActionsProvider maps GitHub run and failed-step details to bounded diagnostics", () => {
  const provider = new GitHubActionsProvider({ repo: "LoResuelvo/loresuelvo-android-service-provider" });
  assert.equal(
    provider.normalizeRun("abc1234", {
      databaseId: 555,
      name: "Android CI",
      status: "completed",
      conclusion: "success",
      url: "https://github.com/example/run/555",
    }).status,
    "passed"
  );
  const failed = provider.normalizeRun("abc1234", {
    databaseId: 556,
    name: "Android CI",
    status: "completed",
    conclusion: "failure",
  });
  assert.equal(failed.status, "failed");
  assert.equal(failed.retryable, true);
  assert.match(failed.failure.message, /abc1234/);

  const details = provider.failureFromJobs([
    {
      databaseId: 10,
      name: "lint-and-unit-tests",
      conclusion: "failure",
      steps: [
        { name: "Checkout", conclusion: "success" },
        { name: "Run delivery contracts", conclusion: "failure" },
      ],
    },
    { databaseId: 11, name: "instrumented-tests", conclusion: "cancelled", steps: [] },
  ]);
  assert.deepEqual(details.failedJobs, ["lint-and-unit-tests", "instrumented-tests"]);
  assert.equal(details.firstJobId, 10);
  assert.equal(details.failure.message, "Job 'lint-and-unit-tests' failed at step 'Run delivery contracts'");
  assert.match(details.failure.excerpt, /Step: Run delivery contracts/);
});

test("repository resolution is explicit, environment, remote, then fail-closed", async (t) => {
  const previous = process.env.GITHUB_REPOSITORY;
  delete process.env.GITHUB_REPOSITORY;
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-ci-resolution-"));
  t.after(async () => {
    if (previous === undefined) delete process.env.GITHUB_REPOSITORY;
    else process.env.GITHUB_REPOSITORY = previous;
    await fs.rm(root, { recursive: true, force: true });
  });
  try {
    await fs.mkdir(path.join(root, ".delivery", "schemas"), { recursive: true });
    await fs.copyFile(
      path.join(ROOT, ".delivery", "schemas", "ci-inspection-result.schema.json"),
      path.join(root, ".delivery", "schemas", "ci-inspection-result.schema.json"),
    );
    assert.equal(resolveRepository({ repo: "Acme/example", repoRoot: root }), "Acme/example");
    process.env.GITHUB_REPOSITORY = "LoResuelvo/loresuelvo-android-service-provider";
    assert.equal(resolveRepository({ repoRoot: root }), "LoResuelvo/loresuelvo-android-service-provider");
    delete process.env.GITHUB_REPOSITORY;
    assert.equal(resolveRepository({ repoRoot: root }), null);
    const provider = new GitHubActionsProvider({ repoRoot: root, token: null });
    assert.equal(provider.repo, null);
    const result = await provider.inspectCommit("c".repeat(40), { repoRoot: root });
    assert.equal(result.status, "provider_error");
    assert.match(result.failure.message, /Unable to resolve/);
  } finally {
    if (previous === undefined) delete process.env.GITHUB_REPOSITORY;
    else process.env.GITHUB_REPOSITORY = previous;
  }
});

test("inspectCi and MockCiProvider share the normalized Android CI result", async () => {
  const sha = "d".repeat(40);
  const provider = new MockCiProvider({ [sha]: { status: "passed", workflow: { id: 777, name: "Android CI" } } });
  setCiProvider(provider);
  try {
    const result = await inspectCi({ sha });
    assert.equal(result.status, "passed");
    assert.equal(result.workflow.id, 777);
    assert.equal(result.sha, sha);
  } finally {
    setCiProvider(null);
  }
});
