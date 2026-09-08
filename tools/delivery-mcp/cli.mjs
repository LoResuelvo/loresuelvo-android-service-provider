#!/usr/bin/env node
import { inspectDelivery } from "./lib/inspect-delivery.mjs";
import { prepareDelivery } from "./lib/prepare-delivery.mjs";
import {
  DeliveryInspectInputSchema,
  DeliveryPrepareInputSchema,
  DeliveryContextInputSchema,
  DeliveryCiInputSchema,
  DeliveryFinalizeInputSchema,
  DeliveryVerifyHeadInputSchema,
  DeliveryTestInputSchema,
  formatInputIssues,
} from "./lib/input-schema.mjs";
import { inspectCi } from "./lib/ci-provider.mjs";
import { finalizeDelivery, verifyHeadDelivery } from "./lib/delivery-finalize.mjs";
import { testDelivery } from "./lib/test-delivery.mjs";
import {
  loadDeliveryContext,
  saveDeliveryContext,
  clearDeliveryContext,
  consumeDeliveryContext,
  validateDeliveryContext,
} from "./lib/delivery-context.mjs";
import {
  runPreCommitHook,
  runCommitMsgHook,
  runPostCommitHook,
  runPrePushHook,
  installHooks,
  getHooksStatus,
} from "./lib/git-hooks.mjs";
import { captureGitSnapshot } from "./lib/git-snapshot.mjs";
import { findRepoRoot } from "./lib/repo-root.mjs";
import { redactSecrets } from "./lib/redact-secrets.mjs";

function usage() {
  return `Usage:
  make delivery-inspect ARGS="[options]"
  make delivery-prepare ARGS="[options]"
  make delivery-context ARGS="[options]"
  make delivery-ci ARGS="--sha <commit-sha>"
  make delivery-finalize ARGS="--intent <close_us|close_batch> [options]"
  make delivery-verify-head ARGS="[--intent close_us] [--us-id 33] [--scope-files ...]"
  make delivery-test ARGS="[options]"
  make delivery-hooks-install
  make delivery-hooks-status

Options for delivery:inspect / delivery:prepare:
  --intent <prepare_commit|close_scenario|close_batch|close_us|repair_ci>
  --message <commit message>
  --feature <app/src/test/resources/features/...feature>
  --scenario <scenario name>
  --repairs-sha <commit-sha>                     Failed commit SHA required when intent is repair_ci
  --scope <app/src/test/resources/features/...feature> Repeat for a batch or US scope
  --acknowledge-snapshot <sha256>                Required to accept maintainability signals
  --acknowledge-decision <signalId>=<reason>     Per-signal decision (repeatable)
  --acknowledge-decisions-json '<json>'          JSON map of signalId -> justification
  --acknowledge-reason <reason>                  Optional context reason for signals
  --force                                        Re-run checks instead of reusing cached evidence
  --pretty                                       Pretty-print JSON instead of compact JSON
  --help

Options for test:
  --mode <affected|unit|scenario|diagnostic>     Validation mode (default: affected)
  --execution-mode <sync|job|auto>               Recoverable execution mode for delivery_test
  --test-file <path>                             Unit test file (repeatable)
  --test-files <comma-separated paths>           Comma-separated test files
  --feature <app/src/test/resources/features/...feature> Feature file path
  --scenario <scenario name>                     Scenario name filter
  --check <checkId>                              Catalog check identifier
  --check-id <checkId>                           Alias for --check
  --force                                        Re-run checks instead of reusing cached evidence

Options for delivery:context:
  --intent <close_scenario|close_batch|close_us|prepare_commit|repair_ci>
  --feature <features/...feature>
  --scenario <scenario name>
  --repairs-sha <commit-sha>
  --scope-files <comma-separated feature files>
  --scope <feature file>                         (repeatable)
  --us-id <numeric-id>
  --inspect, --show                              Display active delivery context
  --clear                                        Clear active delivery context
  --consume                                      Mark active delivery context as consumed

Options for delivery:finalize:
  --intent <close_us|close_batch>
  --us-id <numeric-id>
  --repairs-sha <commit-sha>
  --scope <app/src/test/resources/features/...feature> Repeat for the completed scope
  --wait-for-ci                                  Wait for in-flight CI runs to complete
  --timeout-ms <ms>                              Timeout in milliseconds (default: 900000)
  --poll-interval-ms <ms>                        Polling interval in milliseconds (default: 10000)

Options for delivery:verify-head:
  --intent <close_us|close_batch>
  --us-id <numeric-id>
  --scope <app/src/test/resources/features/...feature> Repeat for the completed scope
  --scope-files <comma-separated feature files>
  --force                                        Re-run checks instead of reusing cached evidence`;
}

function takeValue(args, index, option) {
  const value = args[index + 1];
  if (!value || value.startsWith("--")) throw new Error(`${option} requires a value`);
  return value;
}

function parseArguments(argv) {
  const args = [...argv];
  let command = "inspect";
  let subAction = "";
  let hookArgs = [];

  if (["inspect", "prepare", "context", "hooks", "hook", "ci", "finalize", "verify-head", "verify_head", "test"].includes(args[0])) {
    command = args.shift();
    if (command === "verify_head") command = "verify-head";
  }

  if (command === "hooks" || command === "hook") {
    subAction = args.shift() || "";
    hookArgs = args;
    return { command, subAction, hookArgs, input: {}, pretty: false, help: false };
  }

  const input = { intent: "prepare_commit", scopeFiles: [] };
  let contextAction = "set";
  let pretty = false;
  let acknowledgementSnapshot = "";
  let acknowledgementReason = "";
  const acknowledgementDecisions = {};

  for (let index = 0; index < args.length; index += 1) {
    const option = args[index];
    if (option === "--help" || option === "-h") {
      return { help: true, command, contextAction, input, pretty };
    }
    if (option === "--pretty") {
      pretty = true;
      continue;
    }
    if (option === "--force") {
      input.force = true;
      continue;
    }
    if (option === "--wait-for-ci") {
      input.waitForCi = true;
      continue;
    }
    if (option === "--async") {
      input.async = true;
      continue;
    }
    if (option === "--inspect" || option === "--show") {
      contextAction = "inspect";
      continue;
    }
    if (option === "--clear") {
      contextAction = "clear";
      continue;
    }
    if (option === "--consume") {
      contextAction = "consume";
      continue;
    }

    const value = takeValue(args, index, option);
    index += 1;
    if (option === "--mode") input.mode = value;
    else if (option === "--execution-mode") input.executionMode = value;
    else if (option === "--test-file") {
      input.testFiles = input.testFiles || [];
      input.testFiles.push(value);
    } else if (option === "--test-files") {
      input.testFiles = input.testFiles || [];
      const files = value.split(",").map((f) => f.trim()).filter(Boolean);
      input.testFiles.push(...files);
    } else if (option === "--check" || option === "--check-id") input.checkId = value;
    else if (option === "--intent") input.intent = value;
    else if (option === "--message") input.proposedCommitMessage = value;
    else if (option === "--feature") input.featureFile = value;
    else if (option === "--scenario") input.scenarioName = value;
    else if (option === "--us-id") input.usId = value;
    else if (option === "--sha") input.sha = value;
    else if (option === "--repairs-sha") input.repairsSha = value;
    else if (option === "--scope") input.scopeFiles.push(value);
    else if (option === "--timeout-ms") input.timeoutMs = Number.parseInt(value, 10);
    else if (option === "--poll-interval-ms") input.pollIntervalMs = Number.parseInt(value, 10);
    else if (option === "--scope-files") {
      const files = value.split(",").map((f) => f.trim()).filter(Boolean);
      input.scopeFiles.push(...files);
    } else if (option === "--acknowledge-snapshot") acknowledgementSnapshot = value;
    else if (option === "--acknowledge-reason") acknowledgementReason = value;
    else if (option === "--acknowledge-decision") {
      const eqIdx = value.indexOf("=");
      if (eqIdx === -1) {
        throw new Error("--acknowledge-decision format must be <signalId>=<justification>");
      }
      const sigId = value.slice(0, eqIdx).trim();
      const just = value.slice(eqIdx + 1).trim();
      acknowledgementDecisions[sigId] = just;
    } else if (option === "--acknowledge-decisions-json") {
      try {
        const parsedJson = JSON.parse(value);
        Object.assign(acknowledgementDecisions, parsedJson);
      } catch (e) {
        throw new Error(`Invalid JSON in --acknowledge-decisions-json: ${e.message}`);
      }
    } else {
      throw new Error(`Unknown option: ${option}`);
    }
  }

  if (acknowledgementSnapshot || Object.keys(acknowledgementDecisions).length > 0 || acknowledgementReason) {
    input.acknowledgement = {
      snapshotHash: acknowledgementSnapshot,
      ...(acknowledgementReason ? { reason: acknowledgementReason } : {}),
      ...(Object.keys(acknowledgementDecisions).length > 0 ? { decisions: acknowledgementDecisions } : {}),
    };
  }
  return { help: false, command, contextAction, input, pretty };
}

function exitCode(command, status) {
  if (command === "inspect") {
    return status === "ready" || status === "no_changes" ? 0 : 2;
  }
  if (status === "passed" || status === "no_changes" || status === "job_started" || status === "running") return 0;
  return status === "failed" ? 3 : 2;
}

function writeJson(value, pretty) {
  process.stdout.write(`${JSON.stringify(value, null, pretty ? 2 : 0)}\n`);
}

async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString("utf8");
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  if (options.help) {
    process.stdout.write(`${usage()}\n`);
    return;
  }

  const root = findRepoRoot();

  // 1. Hooks management (make delivery-hooks-install / delivery-hooks-status)
  if (options.command === "hooks") {
    if (options.subAction === "install") {
      const res = await installHooks({ repoRoot: root });
      writeJson(res, options.pretty);
      process.exitCode = 0;
      return;
    }
    if (options.subAction === "status") {
      const res = await getHooksStatus({ repoRoot: root });
      writeJson(res, options.pretty);
      process.exitCode = 0;
      return;
    }
    throw new Error(`Unknown hooks action: ${options.subAction}`);
  }

  // 2. Hook invocations from .githooks/*
  if (options.command === "hook") {
    const hookName = options.subAction;
    if (hookName === "pre-commit") {
      const res = await runPreCommitHook({ repoRoot: root });
      if (res.passed) {
        if (res.verified) {
          process.stdout.write(`[delivery-hook] pre-commit passed (gate: ${res.gateId || "NONE"})\n`);
        } else {
          process.stdout.write(`[delivery-hook] pre-commit: ${res.warning || "unverified commit (not_run)"}\n`);
        }
        process.exitCode = 0;
      } else {
        process.stderr.write(`[delivery-hook] pre-commit failed: ${res.message}\n`);
        process.exitCode = 1;
      }
      return;
    }
    if (hookName === "commit-msg") {
      const messageFile = options.hookArgs[0];
      const res = await runCommitMsgHook({ repoRoot: root, messageFilePath: messageFile });
      if (res.passed) {
        process.stdout.write(`[delivery-hook] commit-msg passed (type: ${res.validation.type})\n`);
        process.exitCode = 0;
      } else {
        process.stderr.write(`[delivery-hook] commit-msg failed: ${res.message}\n`);
        process.exitCode = 1;
      }
      return;
    }
    if (hookName === "post-commit") {
      const res = await runPostCommitHook({ repoRoot: root });
      if (res.recorded) {
        const label = res.verificationStatus === "not_run" ? "unverified (not_run)" : "verified";
        process.stdout.write(`[delivery-hook] post-commit: recorded ${label} evidence for ${res.commitSha.slice(0, 8)}\n`);
        process.exitCode = 0;
      } else {
        process.stderr.write(
          `[delivery-hook] post-commit: evidence was not recorded (${res.reason || "unknown"})\n`
        );
        process.exitCode = 1;
      }
      return;
    }
    if (hookName === "pre-push") {
      const rawStdin = await readStdin();
      const stdinLines = rawStdin.trim().split("\n").filter(Boolean);
      const res = await runPrePushHook({ repoRoot: root, stdinLines });
      if (res.passed) {
        process.stdout.write(`[delivery-hook] pre-push passed\n`);
        process.exitCode = 0;
      } else {
        process.stderr.write(`[delivery-hook] pre-push failed: ${res.message}\n`);
        process.exitCode = 1;
      }
      return;
    }
    throw new Error(`Unknown hook: ${hookName}`);
  }

  // 3. Delivery context management
  if (options.command === "context") {
    if (options.contextAction === "clear") {
      const res = await clearDeliveryContext({ repoRoot: root });
      writeJson(res, options.pretty);
      process.exitCode = 0;
      return;
    }
    if (options.contextAction === "inspect") {
      const context = await loadDeliveryContext({ repoRoot: root });
      if (!context) {
        writeJson({ active: false, context: null }, options.pretty);
        process.exitCode = 0;
        return;
      }
      const snapshot = await captureGitSnapshot({ cwd: root });
      const validation = validateDeliveryContext({ context, snapshot });
      writeJson({ active: validation.valid, context, validation }, options.pretty);
      process.exitCode = 0;
      return;
    }
    if (options.contextAction === "consume") {
      const res = await consumeDeliveryContext({ repoRoot: root });
      writeJson(res, options.pretty);
      process.exitCode = 0;
      return;
    }

    const parsed = DeliveryContextInputSchema.safeParse(options.input);
    if (!parsed.success) throw new Error(formatInputIssues(parsed.error));

    const snapshot = await captureGitSnapshot({ cwd: root });
    const saved = await saveDeliveryContext({
      repoRoot: root,
      snapshot,
      intent: parsed.data.intent,
      usId: parsed.data.usId,
      featureFile: parsed.data.featureFile,
      scenarioName: parsed.data.scenarioName,
      scopeFiles: parsed.data.scopeFiles,
      repairsSha: parsed.data.repairsSha,
    });
    writeJson(saved, options.pretty);
    process.exitCode = 0;
    return;
  }

  // 4. CI inspection
  if (options.command === "ci") {
    const parsed = DeliveryCiInputSchema.safeParse(options.input);
    if (!parsed.success) throw new Error(formatInputIssues(parsed.error));
    const res = await inspectCi({ sha: parsed.data.sha, repoRoot: root });
    writeJson(res, options.pretty);
    process.exitCode = res.status === "passed" ? 0 : ["failed", "timed_out", "provider_error"].includes(res.status) ? 3 : 2;
    return;
  }

  // 5. Finalize delivery
  if (options.command === "finalize") {
    const parsed = DeliveryFinalizeInputSchema.safeParse(options.input);
    if (!parsed.success) throw new Error(formatInputIssues(parsed.error));
    const res = await finalizeDelivery({ repoRoot: root, ...parsed.data });
    writeJson(res, options.pretty);
    process.exitCode = res.finalized ? 0 : 2;
    return;
  }

  // 5.5. Verify HEAD delivery
  if (options.command === "verify-head") {
    const parsed = DeliveryVerifyHeadInputSchema.safeParse(options.input);
    if (!parsed.success) throw new Error(formatInputIssues(parsed.error));
    const res = await verifyHeadDelivery({ repoRoot: root, ...parsed.data });
    writeJson(res, options.pretty);
    process.exitCode = res.verified ? 0 : 2;
    return;
  }

  // 5.8. Test delivery (delivery_test)
  if (options.command === "test") {
    const parsed = DeliveryTestInputSchema.safeParse(options.input);
    if (!parsed.success) throw new Error(formatInputIssues(parsed.error));
    const res = await testDelivery({ repoRoot: root, ...parsed.data });
    writeJson(res, options.pretty);
    process.exitCode = ["passed", "no_changes", "job_started", "running"].includes(res.status)
      ? 0
      : res.status === "failed" ? 3 : 2;
    return;
  }

  // 6. Delivery inspect / prepare
  const schema = options.command === "prepare" ? DeliveryPrepareInputSchema : DeliveryInspectInputSchema;
  const parsed = schema.safeParse(options.input);
  if (!parsed.success) throw new Error(formatInputIssues(parsed.error));

  const result =
    options.command === "prepare"
      ? await prepareDelivery({ ...parsed.data, repoRoot: root })
      : (await inspectDelivery({ ...parsed.data, repoRoot: root })).result;
  writeJson(result, options.pretty);
  process.exitCode = exitCode(options.command, result.status);
}

main().catch((error) => {
  writeJson(
    {
      schemaVersion: 1,
      status: "blocked",
      diagnostics: [
        {
          code: "DELIVERY_CLI_ERROR",
          message: redactSecrets(String(error.message || "Delivery command failed")).split("\n")[0],
          retryable: false,
        },
      ],
    },
    false
  );
  process.exitCode = 1;
});
