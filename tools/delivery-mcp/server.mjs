import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import { pathToFileURL } from "node:url";
import { inspectDelivery } from "./lib/inspect-delivery.mjs";
import { prepareDelivery } from "./lib/prepare-delivery.mjs";
import {
  DeliveryInspectInputSchema,
  DeliveryPrepareInputSchema,
  DeliveryCiInputSchema,
  DeliveryFinalizeInputSchema,
  DeliveryVerifyHeadInputSchema,
  DeliveryTestInputSchema,
  DeliveryJobWaitInputSchema,
  DeliveryJobCancelInputSchema,
  formatInputIssues,
} from "./lib/input-schema.mjs";
import { inspectCi } from "./lib/ci-provider.mjs";
import { finalizeDelivery, verifyHeadDelivery } from "./lib/delivery-finalize.mjs";
import { testDelivery } from "./lib/test-delivery.mjs";
import { redactSecrets } from "./lib/redact-secrets.mjs";
import { waitForJob, cancelDeliveryJob } from "./lib/jobs.mjs";

const intentProperty = {
  type: "string",
  enum: ["prepare_commit", "close_scenario", "close_batch", "close_us", "repair_ci"],
  description: "Delivery intent for this boundary",
};

const commonProperties = {
  intent: intentProperty,
  proposedCommitMessage: {
    type: "string",
    description: "Optional commit message proposed by the agent",
  },
  featureFile: {
    type: "string",
    description: "Feature path required for Gate B when it cannot be inferred",
  },
  scenarioName: {
    type: "string",
    description: "Optional scenario name recorded as delivery context",
  },
  scopeFiles: {
    type: "array",
    items: { type: "string" },
    maxItems: 100,
    description: "Completed feature paths that define Gate D @wip scope",
  },
  repairsSha: {
    type: "string",
    description: "Optional failed commit SHA being repaired by repair_ci",
  },
};

export const server = new Server(
  { name: "loresuelvo-delivery", version: "1.3.0" },
  { capabilities: { tools: {} } }
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: "delivery_inspect",
      description:
        "Inspects staged changes, recent US commits, and maintainability; deterministically selects the applicable gate without running it.",
      inputSchema: {
        type: "object",
        properties: commonProperties,
        required: ["intent"],
      },
      annotations: {
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false,
      },
    },
    {
      name: "delivery_prepare",
      description:
        "Inspects the staged snapshot, executes its deterministic local gate, and returns compact cached evidence. It never commits or pushes.",
      inputSchema: {
        type: "object",
        properties: {
          ...commonProperties,
          acknowledgement: {
            type: "object",
            description:
              "Explicit acknowledgement of maintainability signals for the exact staged snapshot",
            properties: {
              snapshotHash: {
                type: "string",
                description:
                  "64-character SHA-256 hash of the exact staged snapshot being acknowledged",
              },
              reason: {
                type: "string",
                description:
                  "Optional global reason or context for the maintainability decisions",
              },
              decisions: {
                description:
                  "Decisions as a signalId-to-justification map or as structured decision entries",
                oneOf: [
                  {
                    type: "object",
                    additionalProperties: { type: "string", minLength: 12 },
                  },
                  {
                    type: "array",
                    items: {
                      type: "object",
                      properties: {
                        id: { type: "string" },
                        signalId: { type: "string" },
                        reason: { type: "string" },
                        justification: { type: "string" },
                      },
                    },
                  },
                ],
              },
            },
            required: ["snapshotHash"],
          },
          force: {
            type: "boolean",
            description: "Re-run checks instead of reusing cached evidence",
          },
          mode: {
            type: "string",
            enum: ["sync", "job", "auto"],
            description: "Execution mode: 'sync' waits for completion, 'job' returns immediately with a jobId, 'auto' runs Gate C/D/R as a recoverable job. Default is 'auto'.",
          },
          async: {
            type: "boolean",
            description: "Run as an asynchronous job returning a jobId immediately; alias for mode: 'job'.",
          },
        },
        required: ["intent"],
      },
      annotations: {
        readOnlyHint: false,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false,
      },
    },
    {
      name: "delivery_ci_inspect",
      description: "Inspects GitHub Actions CI status for a given commit SHA",
      inputSchema: {
        type: "object",
        properties: {
          sha: {
            type: "string",
            description: "Commit SHA to inspect CI status for",
          },
        },
        required: ["sha"],
      },
      annotations: {
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: true,
      },
    },
    {
      name: "delivery_finalize",
      description:
        "Closes a batch or User Story with exact Gate D evidence on HEAD, no @wip in scope, pushed commits, and valid ledger entries. A batch may close with CI pending; a User Story requires green CI for every relevant commit. Supports bounded waiting for in-flight CI runs.",
      inputSchema: {
        type: "object",
        properties: {
          intent: {
            type: "string",
            enum: ["close_us", "close_batch"],
            description: "Delivery boundary to finalize; defaults to close_us",
          },
          usId: {
            type: "string",
            description: "Optional User Story identifier used to find its commits",
          },
          scopeFiles: {
            type: "array",
            items: { type: "string" },
            maxItems: 100,
            description: "Feature files whose completed scope must match Gate D evidence",
          },
          waitForCi: {
            type: "boolean",
            description: "Wait for in-flight CI runs to complete within bounded timeout; defaults to false",
          },
          timeoutMs: {
            type: "integer",
            description: "Maximum milliseconds to wait for CI (100 to 1800000, default 900000)",
          },
          pollIntervalMs: {
            type: "integer",
            description: "Polling interval in milliseconds (50 to 60000, default 10000)",
          },
          mode: {
            type: "string",
            enum: ["sync", "job", "auto"],
            description: "Execution mode: 'sync' waits for completion, 'job' returns immediately with a jobId, 'auto' runs as job for extended CI waits. Default is 'auto'.",
          },
          async: {
            type: "boolean",
            description: "Run as an asynchronous job returning a jobId immediately; alias for mode: 'job'.",
          },
        },
      },
      annotations: {
        readOnlyHint: false,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: true,
      },
    },
    {
      name: "delivery_job_wait",
      description:
        "Waits for a recoverable delivery background job (e.g. Gate D or CI wait) to complete within a bounded long-poll timeout without busy looping.",
      inputSchema: {
        type: "object",
        properties: {
          jobId: {
            type: "string",
            description: "The unique identifier of the background delivery job to await",
          },
          timeoutMs: {
            type: "integer",
            description: "Maximum milliseconds to wait for the job (100 to 180000, default 60000)",
          },
        },
        required: ["jobId"],
      },
      annotations: {
        readOnlyHint: false,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false,
      },
    },
    {
      name: "delivery_job_cancel",
      description:
        "Cancels a queued or running recoverable delivery job after verifying its worker identity, and releases its delivery lock.",
      inputSchema: {
        type: "object",
        properties: {
          jobId: {
            type: "string",
            minLength: 1,
            maxLength: 100,
            pattern: "^[a-zA-Z0-9_-]+$",
            description: "The unique identifier of the background delivery job to cancel",
          },
          reason: {
            type: "string",
            maxLength: 500,
            description: "Optional cancellation reason",
          },
        },
        required: ["jobId"],
      },
      annotations: {
        readOnlyHint: false,
        destructiveHint: true,
        idempotentHint: true,
        openWorldHint: false,
      },
    },
    {
      name: "delivery_verify_head",
      description:
        "Verifies Gate D on the current HEAD commit and records evidence without requiring an additional commit",
      inputSchema: {
        type: "object",
        properties: {
          intent: {
            type: "string",
            enum: ["close_us", "close_batch"],
            description: "Delivery intent to verify on HEAD; defaults to close_us",
          },
          usId: {
            type: "string",
            description: "Optional User Story identifier",
          },
          scopeFiles: {
            type: "array",
            items: { type: "string" },
            maxItems: 100,
            description: "Completed feature paths to verify with Gate D",
          },
          force: {
            type: "boolean",
            description: "Re-run checks instead of reusing cached evidence",
          },
          mode: {
            type: "string",
            enum: ["sync", "job", "auto"],
            description: "Execution mode: 'sync' waits for Gate D, 'job' returns a recoverable jobId, and 'auto' chooses a job when no focused executor is supplied. Default is 'sync'.",
          },
          async: {
            type: "boolean",
            description: "Run Gate D as an asynchronous job; alias for mode: 'job'.",
          },
        },
      },
      annotations: {
        readOnlyHint: false,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false,
      },
    },
    {
      name: "delivery_test",
      description:
        "Executes focused TDD validation (affected, unit, scenario, or diagnostic check) over the working tree without running raw internal commands.",
      inputSchema: {
        type: "object",
        properties: {
          mode: {
            type: "string",
            enum: ["affected", "unit", "scenario", "diagnostic"],
            description: "Functional validation mode; defaults to 'affected'",
          },
          executionMode: {
            type: "string",
            enum: ["sync", "job", "auto"],
            description:
              "Execution mode independent from the functional mode: 'sync' waits, 'job' returns a recoverable jobId, and 'auto' backgrounds affected/scenario or long diagnostic checks. Focused unit tests remain synchronous in auto; defaults to 'auto'.",
          },
          async: {
            type: "boolean",
            description: "Alias for executionMode: 'job'.",
          },
          testFiles: {
            type: "array",
            items: { type: "string" },
            maxItems: 20,
            description: "Array of test files for mode: unit",
          },
          featureFile: {
            type: "string",
            description: "Feature file path for mode: scenario",
          },
          scenarioName: {
            type: "string",
            description: "Optional scenario name filter for mode: scenario",
          },
          checkId: {
            type: "string",
            description: "Check identifier from policy catalog for mode: diagnostic",
          },
          force: {
            type: "boolean",
            description: "Re-run checks instead of using cache",
          },
        },
      },
      annotations: {
        readOnlyHint: false,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false,
      },
    },
  ],
}));

function inspectionError(code, message) {
  return {
    schemaVersion: 1,
    status: "blocked",
    snapshotHash: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    repository: { branch: "UNKNOWN", headSha: "UNKNOWN", usId: null },
    policy: { version: 1, hash: "UNKNOWN" },
    gate: {
      id: "NONE",
      reasonCodes: [code],
      checkIds: [],
      checks: [],
      parameters: {},
      postPushChecks: [],
    },
    maintainability: {
      status: "not_applicable",
      filesReviewed: [],
      signalCount: 0,
      signals: [],
      truncated: false,
    },
    diagnostics: [{ code, message, retryable: false }],
  };
}

function executionError(code, message) {
  return {
    schemaVersion: 1,
    status: "blocked",
    snapshotHash: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    runKey: null,
    cached: false,
    policy: { version: 1, hash: "UNKNOWN" },
    gate: {
      id: "NONE",
      reasonCodes: [code],
      checkIds: [],
      parameters: {},
      postPushChecks: [],
    },
    summary: { passed: 0, failed: 0, skipped: 0, durationMs: 0 },
    checks: [],
    diagnostics: [{ code, message, retryable: false }],
    evidence: { recordPath: null },
  };
}

function finalizationError(reason, message) {
  return {
    finalized: false,
    status: "blocked",
    reason,
    message,
  };
}

function toolResponse(result, isError = false) {
  return {
    content: [{ type: "text", text: JSON.stringify(result) }],
    isError,
  };
}

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const name = request.params.name;

  if (name === "delivery_job_wait") {
    const parsed = DeliveryJobWaitInputSchema.safeParse(request.params.arguments || {});
    if (!parsed.success) {
      return toolResponse(
        {
          status: "blocked",
          code: "INVALID_ARGUMENTS",
          message: formatInputIssues(parsed.error),
        },
        true
      );
    }
    try {
      const result = await waitForJob(parsed.data);
      const isError = ["failed", "timed_out", "cancelled"].includes(result.status);
      return toolResponse(result, isError);
    } catch (error) {
      const message = redactSecrets(String(error.message || "Job wait error")).split("\n")[0];
      return toolResponse({ status: "blocked", code: "INTERNAL_ERROR", message }, true);
    }
  }

  if (name === "delivery_job_cancel") {
    const parsed = DeliveryJobCancelInputSchema.safeParse(request.params.arguments || {});
    if (!parsed.success) {
      return toolResponse(
        {
          status: "blocked",
          code: "INVALID_ARGUMENTS",
          message: formatInputIssues(parsed.error),
        },
        true
      );
    }
    try {
      const result = await cancelDeliveryJob(parsed.data);
      return toolResponse(result, result.status === "cancelled" && result.error?.code !== "JOB_CANCELLED");
    } catch (error) {
      const message = redactSecrets(String(error.message || "Job cancellation error")).split("\n")[0];
      return toolResponse({ status: "blocked", code: "INTERNAL_ERROR", message }, true);
    }
  }

  if (name === "delivery_finalize") {
    const parsed = DeliveryFinalizeInputSchema.safeParse(request.params.arguments || {});
    if (!parsed.success) {
      return toolResponse(
        finalizationError("INVALID_ARGUMENTS", formatInputIssues(parsed.error)),
        true
      );
    }
    try {
      const result = await finalizeDelivery(parsed.data);
      const isError = !result.finalized && result.status !== "job_started" && result.status !== "running";
      return toolResponse(result, isError);
    } catch (error) {
      const message = redactSecrets(String(error.message || "Delivery finalization error")).split(
        "\n"
      )[0];
      return toolResponse(finalizationError("INTERNAL_ERROR", message), true);
    }
  }

  if (name === "delivery_verify_head") {
    const parsed = DeliveryVerifyHeadInputSchema.safeParse(request.params.arguments || {});
    if (!parsed.success) {
      return toolResponse(
        {
          verified: false,
          status: "blocked",
          reason: "INVALID_ARGUMENTS",
          message: formatInputIssues(parsed.error),
        },
        true
      );
    }
    try {
      const result = await verifyHeadDelivery(parsed.data);
      const isError = !result.verified && !["job_started", "running"].includes(result.status);
      return toolResponse(result, isError);
    } catch (error) {
      const message = redactSecrets(String(error.message || "Delivery verify head error")).split(
        "\n"
      )[0];
      return toolResponse(
        {
          verified: false,
          status: "blocked",
          reason: "INTERNAL_ERROR",
          message,
        },
        true
      );
    }
  }

  if (name === "delivery_ci_inspect") {
    const parsed = DeliveryCiInputSchema.safeParse(request.params.arguments || {});
    if (!parsed.success) {
      return toolResponse({ error: formatInputIssues(parsed.error) }, true);
    }
    try {
      const result = await inspectCi({ sha: parsed.data.sha });
      const failed = ["failed", "timed_out", "provider_error"].includes(result.status);
      return toolResponse(result, failed);
    } catch (error) {
      const message = redactSecrets(String(error.message || "CI inspection error")).split("\n")[0];
      return toolResponse({ error: message }, true);
    }
  }

  if (name === "delivery_test") {
    const parsed = DeliveryTestInputSchema.safeParse(request.params.arguments || {});
    if (!parsed.success) {
      return toolResponse(
        {
          status: "error",
          mode: request.params.arguments?.mode || "affected",
          cached: false,
          durationMs: 0,
          counts: { passed: 0, failed: 0, skipped: 0 },
          diagnostics: [
            {
              code: "INVALID_ARGUMENTS",
              message: formatInputIssues(parsed.error),
              retryable: false,
            },
          ],
        },
        true
      );
    }
    try {
      const result = await testDelivery(parsed.data);
      const isError = result.status === "error" || result.status === "failed";
      return toolResponse(result, isError);
    } catch (error) {
      const message = redactSecrets(String(error.message || "Delivery test error")).split("\n")[0];
      return toolResponse(
        {
          status: "error",
          mode: parsed.data.mode || "affected",
          cached: false,
          durationMs: 0,
          counts: { passed: 0, failed: 0, skipped: 0 },
          diagnostics: [
            {
              code: "INTERNAL_ERROR",
              message,
              retryable: false,
            },
          ],
        },
        true
      );
    }
  }

  const isPrepare = name === "delivery_prepare";
  if (!isPrepare && name !== "delivery_inspect") {
    return toolResponse(inspectionError("UNKNOWN_TOOL", `Unknown tool requested: ${name}`), true);
  }

  const schema = isPrepare ? DeliveryPrepareInputSchema : DeliveryInspectInputSchema;
  const parsed = schema.safeParse(request.params.arguments || {});
  if (!parsed.success) {
    const result = (isPrepare ? executionError : inspectionError)(
      "INVALID_ARGUMENTS",
      formatInputIssues(parsed.error)
    );
    return toolResponse(result, true);
  }

  try {
    const result = isPrepare
      ? await prepareDelivery({
          ...parsed.data,
          mode: parsed.data.mode || (parsed.data.async ? "job" : "auto"),
        })
      : (await inspectDelivery(parsed.data)).result;
    const failed =
      isPrepare && !["passed", "no_changes", "job_started", "running"].includes(result.status);
    return toolResponse(result, failed);
  } catch (error) {
    const message = redactSecrets(String(error.message || "Unexpected delivery error")).split("\n")[0];
    return toolResponse(
      (isPrepare ? executionError : inspectionError)("INTERNAL_ERROR", message),
      true
    );
  }
});

async function run() {
  await server.connect(new StdioServerTransport());
}

const invokedAsEntryPoint = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (invokedAsEntryPoint) {
  run().catch((error) => {
    console.error(`Fatal server error: ${String(error.message || "unknown").split("\n")[0]}`);
    process.exit(1);
  });
}
