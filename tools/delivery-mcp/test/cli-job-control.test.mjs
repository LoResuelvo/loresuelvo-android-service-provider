import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import { readFileSync } from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
const CLI = path.join(ROOT, "tools/delivery-mcp/cli.mjs");
const JOBS_DIR = path.join(ROOT, ".delivery/runtime/jobs");
let sequence = 0;

function runCli(args) {
  return spawnSync(process.execPath, [CLI, ...args], {
    cwd: ROOT,
    env: { ...process.env },
    encoding: "utf8",
  });
}

async function writeJob(t, overrides = {}) {
  const jobId = `cli-job-${process.pid}-${Date.now()}-${sequence++}`;
  const job = {
    jobId,
    type: "prepare",
    status: "passed",
    createdAt: new Date().toISOString(),
    queueLeaseUntil: new Date(Date.now() + 60_000).toISOString(),
    startedAt: new Date().toISOString(),
    finishedAt: new Date().toISOString(),
    pid: null,
    workerToken: null,
    workerIdentity: null,
    runKey: null,
    snapshotHash: null,
    gateId: null,
    params: {},
    result: { status: "passed", summary: { passed: 1, failed: 0, skipped: 0, durationMs: 1 }, diagnostics: [] },
    error: null,
    ...overrides,
  };
  const filePath = path.join(JOBS_DIR, `${jobId}.json`);
  await fs.mkdir(JOBS_DIR, { recursive: true });
  await fs.writeFile(filePath, `${JSON.stringify(job, null, 2)}\n`, "utf8");
  t.after(() => fs.rm(filePath, { force: true }));
  return jobId;
}

test("job-wait returns a terminal result and supports the job wait alias", async (t) => {
  const jobId = await writeJob(t);
  const result = runCli(["job", "wait", "--job-id", jobId, "--timeout-ms", "100"]);

  assert.equal(result.status, 0, `CLI failed:\nstdout=${result.stdout}\nstderr=${result.stderr}`);
  const parsed = JSON.parse(result.stdout);
  assert.equal(parsed.jobId, jobId);
  assert.equal(parsed.status, "passed");
  assert.equal(parsed.summary.passed, 1);
});

test("job-wait uses a bounded timeout for an active job", async (t) => {
  const jobId = await writeJob(t, {
    status: "queued",
    startedAt: null,
    finishedAt: null,
    result: null,
  });
  const result = runCli(["job-wait", "--job-id", jobId, "--timeout-ms", "100"]);

  assert.equal(result.status, 0, `CLI failed:\nstdout=${result.stdout}\nstderr=${result.stderr}`);
  const parsed = JSON.parse(result.stdout);
  assert.equal(parsed.jobId, jobId);
  assert.equal(parsed.status, "running");
  assert.match(parsed.message, /still in progress/);
});

test("job-cancel cancels a queued job and preserves its reason", async (t) => {
  const jobId = await writeJob(t, {
    status: "queued",
    startedAt: null,
    finishedAt: null,
    result: null,
  });
  const result = runCli([
    "job-cancel",
    "--job-id",
    jobId,
    "--reason",
    "Human stopped the background delivery check",
  ]);

  assert.equal(result.status, 0, `CLI failed:\nstdout=${result.stdout}\nstderr=${result.stderr}`);
  const parsed = JSON.parse(result.stdout);
  assert.equal(parsed.jobId, jobId);
  assert.equal(parsed.status, "cancelled");
  assert.equal(parsed.error.code, "JOB_CANCELLED");
  assert.equal(parsed.error.message, "Human stopped the background delivery check");
});

test("job control rejects unsafe identifiers and documents the Make targets", () => {
  const invalid = runCli(["job-wait", "--job-id", "../escape"]);
  assert.equal(invalid.status, 1);
  const parsed = JSON.parse(invalid.stdout);
  assert.equal(parsed.status, "blocked");
  assert.match(parsed.diagnostics[0].message, /Invalid job identifier/);

  const makefile = readFileSync(path.join(ROOT, "Makefile"), "utf8");
  assert.match(makefile, /delivery-job-wait:/);
  assert.match(makefile, /\$\(DELIVERY_CLI\) job-wait \$\(ARGS\)/);
  assert.match(makefile, /delivery-job-cancel:/);
  assert.match(makefile, /\$\(DELIVERY_CLI\) job-cancel \$\(ARGS\)/);
});
