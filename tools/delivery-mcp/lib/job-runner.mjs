#!/usr/bin/env node
import { findRepoRoot } from "./repo-root.mjs";
import { getDeliveryJob, updateDeliveryJob, readProcessIdentity } from "./jobs.mjs";
import { prepareDelivery } from "./prepare-delivery.mjs";
import { finalizeDelivery, verifyHeadDelivery } from "./delivery-finalize.mjs";
import { redactSecrets } from "./redact-secrets.mjs";
import { testDelivery } from "./test-delivery.mjs";
import { pathToFileURL } from "node:url";

async function run() {
  const jobId = process.argv[2];
  const repoRootArg = process.argv[3];
  if (!jobId) {
    console.error("Missing jobId argument");
    process.exit(1);
  }

  const root = findRepoRoot(repoRootArg);
  const job = await getDeliveryJob({ repoRoot: root, jobId });
  if (!job) {
    console.error(`Job not found: ${jobId}`);
    process.exit(1);
  }

  if (["passed", "failed", "timed_out", "cancelled"].includes(job.status)) {
    process.exit(0);
  }

  const workerToken = process.env.DELIVERY_JOB_TOKEN || job.workerToken || null;
  const workerIdentity = await readProcessIdentity(process.pid);
  await updateDeliveryJob({
    repoRoot: root,
    jobId,
    updates: {
      status: "running",
      pid: process.pid,
      startedAt: job.startedAt || new Date().toISOString(),
      ...(workerToken ? { workerToken } : {}),
      ...(workerIdentity ? { workerIdentity } : {}),
    },
  });

  const onSignal = async (sig) => {
    try {
      await updateDeliveryJob({
        repoRoot: root,
        jobId,
        updates: {
          status: "cancelled",
          finishedAt: new Date().toISOString(),
          error: { code: "CANCELLED", message: `Terminated by signal ${sig}` },
        },
      });
    } catch {
      // ignore
    }
    process.exit(143);
  };
  process.on("SIGTERM", () => onSignal("SIGTERM"));
  process.on("SIGINT", () => onSignal("SIGINT"));

  try {
    if (job.type === "finalize") {
      const result = await finalizeDelivery({
        ...job.params,
        repoRoot: root,
        mode: "sync",
      });
      const timedOut = result.reason === "CI_TIMEOUT" || result.status === "timed_out";
      const jobStatus = result.finalized ? "passed" : timedOut ? "timed_out" : "failed";
      const storedResult = timedOut ? { ...result, status: "timed_out" } : result;
      await updateDeliveryJob({
        repoRoot: root,
        jobId,
        updates: {
          status: jobStatus,
          finishedAt: new Date().toISOString(),
          result: storedResult,
        },
      });
    } else if (job.type === "verify_head") {
      const result = await verifyHeadDelivery({
        ...job.params,
        repoRoot: root,
        mode: "sync",
        workerJobId: jobId,
      });
      await updateDeliveryJob({
        repoRoot: root,
        jobId,
        updates: {
          status: result.verified ? "passed" : "failed",
          finishedAt: new Date().toISOString(),
          result,
        },
      });
    } else if (job.type === "test") {
      const result = await testDelivery({
        ...job.params,
        repoRoot: root,
        // The worker already owns this record. This marker bypasses job
        // deduplication and keeps a recovered worker from enqueueing itself.
        executionMode: "sync",
        async: false,
        workerJobId: jobId,
      });
      const timedOut =
        result.status === "failed" &&
        (result.failure?.code === "CHECK_TIMEOUT" ||
          result.diagnostics?.some?.((diagnostic) => diagnostic.code === "CHECK_TIMEOUT"));
      const jobStatus = timedOut ? "timed_out" : result.status === "passed" ? "passed" : "failed";
      const storedResult = timedOut ? { ...result, status: "timed_out" } : result;
      await updateDeliveryJob({
        repoRoot: root,
        jobId,
        updates: {
          status: jobStatus,
          finishedAt: new Date().toISOString(),
          result: storedResult,
        },
      });
    } else {
      const outcome = await prepareDelivery({
        ...job.params,
        repoRoot: root,
        mode: "sync",
        workerJobId: jobId,
      });
      await updateDeliveryJob({
        repoRoot: root,
        jobId,
        updates: {
          status: outcome.status === "passed" ? "passed" : "failed",
          finishedAt: new Date().toISOString(),
          result: outcome,
        },
      });
    }
  } catch (error) {
    const message = redactSecrets(String(error.message || "Unknown worker error")).split("\n")[0];
    await updateDeliveryJob({
      repoRoot: root,
      jobId,
      updates: {
        status: "failed",
        finishedAt: new Date().toISOString(),
        error: { code: error.code || "WORKER_ERROR", message },
      },
    });
  }
}

const invokedAsEntryPoint = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (invokedAsEntryPoint) {
  run().catch((err) => {
    console.error("Fatal job runner error:", err);
    process.exit(1);
  });
}
