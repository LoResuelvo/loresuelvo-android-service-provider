import { z } from "zod";

const NumericUsIdSchema = z
  .string()
  .regex(/^[0-9]+$/, "User Story identifier must contain digits only");

export const DeliveryInspectInputSchema = z.object({
  intent: z
    .enum(["prepare_commit", "close_scenario", "close_batch", "close_us", "repair_ci"])
    .default("prepare_commit"),
  proposedCommitMessage: z.string().max(500).optional(),
  featureFile: z.string().max(500).optional(),
  scenarioName: z.string().max(500).optional(),
  scopeFiles: z.array(z.string().max(500)).max(100).default([]),
  repairsSha: z.string().min(7).max(40).optional(),
});

export const DeliveryPrepareInputSchema = DeliveryInspectInputSchema.extend({
  force: z.boolean().default(false),
  mode: z.enum(["sync", "job", "auto"]).default("auto"),
  async: z.boolean().optional(),
  acknowledgement: z
    .object({
      snapshotHash: z.string().length(64),
      reason: z.string().max(1000).optional(),
      decisions: z
        .union([
          z.record(z.string(), z.string().min(12)),
          z.array(
            z.object({
              id: z.string().optional(),
              signalId: z.string().optional(),
              reason: z.string().optional(),
              justification: z.string().optional(),
            })
          ),
        ])
        .optional(),
    })
    .optional(),
});

export const DeliveryContextInputSchema = z.object({
  action: z.enum(["set", "inspect", "clear", "consume"]).default("set"),
  intent: z
    .enum(["prepare_commit", "close_scenario", "close_batch", "close_us", "repair_ci"])
    .optional(),
  usId: NumericUsIdSchema.optional(),
  featureFile: z.string().max(500).optional(),
  scenarioName: z.string().max(500).optional(),
  scopeFiles: z.array(z.string().max(500)).max(100).default([]),
  repairsSha: z.string().min(7).max(40).optional(),
});

export const DeliveryCiInputSchema = z.object({
  sha: z.string().min(7).max(40),
});

export const DeliveryFinalizeInputSchema = z.object({
  intent: z.enum(["close_us", "close_batch"]).default("close_us"),
  usId: NumericUsIdSchema.optional(),
  scopeFiles: z.array(z.string().max(500)).default([]),
  repairsSha: z.string().min(7).max(40).optional(),
  waitForCi: z.boolean().default(false),
  timeoutMs: z.number().int().min(100).max(1800000).default(900000),
  pollIntervalMs: z.number().int().min(50).max(60000).default(10000),
  mode: z.enum(["sync", "job", "auto"]).default("auto"),
  async: z.boolean().optional(),
});

export const DeliveryJobWaitInputSchema = z.object({
  jobId: z
    .string()
    .min(1)
    .max(100)
    .regex(/^[a-zA-Z0-9_-]+$/, "Invalid job identifier"),
  timeoutMs: z.number().int().min(100).max(180000).default(60000),
});

export const DeliveryJobCancelInputSchema = z.object({
  jobId: z
    .string()
    .min(1)
    .max(100)
    .regex(/^[a-zA-Z0-9_-]+$/, "Invalid job identifier"),
  reason: z.string().max(500).optional(),
});

export const DeliveryVerifyHeadInputSchema = z.object({
  intent: z.enum(["close_us", "close_batch"]).default("close_us"),
  usId: NumericUsIdSchema.optional(),
  scopeFiles: z.array(z.string().max(500)).max(100).default([]),
  force: z.boolean().default(false),
  mode: z.enum(["sync", "job", "auto"]).default("sync"),
  async: z.boolean().optional(),
});

export const DeliveryTestInputSchema = z.object({
  mode: z.enum(["affected", "unit", "scenario", "diagnostic"]).default("affected"),
  // `mode` selects the functional test operation. Keep job execution as a
  // separate field so callers can request a recoverable worker without
  // colliding with the existing affected/unit/scenario/diagnostic contract.
  executionMode: z.enum(["sync", "job", "auto"]).default("auto"),
  async: z.boolean().optional(),
  // Keep focused-test input bounded so an accidental request cannot expand
  // into an unreviewable command line/context payload.
  testFiles: z.array(z.string().max(500)).max(20).optional(),
  featureFile: z.string().max(500).optional(),
  scenarioName: z.string().max(500).optional(),
  checkId: z.string().max(100).optional(),
  force: z.boolean().default(false),
});

export function formatInputIssues(error) {
  return error.issues
    .slice(0, 5)
    .map((issue) => `${issue.path.join(".") || "input"}: ${issue.message}`)
    .join(", ");
}
