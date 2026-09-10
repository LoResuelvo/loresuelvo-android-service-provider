---
name: android-ai-development-workflow
description: "Coordinate Android User Story batches between an orchestrator and developers with bounded handoffs, Delivery MCP evidence, and CI-aware repair."
---

# Android AI Development Workflow

Use this skill when an orchestrator delegates an Android User Story or a
bounded batch to another agent. BDD, gates, commits, architecture, and API
rules live in their dedicated skills.

## Conduction and batch size

Declare two independent choices:

- `USER_GUIDED`: the user approves relevant decisions and boundaries.
- `AGENT_ORCHESTRATED`: the orchestrator follows an approved plan and asks the
  user only for a real escalation.

Choose one batch granularity:

- `MICROSTEP`: one observable behavior; validate and stop without committing.
- `SCENARIO`: complete one approved scenario and its atomic boundaries.
- `SCENARIO_GROUP`: complete two or three related, low-coupling scenarios,
  making each GREEN before starting the next.

Use `MICROSTEP` for ambiguity or high risk, `SCENARIO` ordinarily, and
`SCENARIO_GROUP` only when continuation conditions are predictable.

Granularity limits behavioral scope and reporting cadence; it never fixes the
number of commits. `SCENARIO` and `SCENARIO_GROUP` authorize every atomic,
deployable boundary required inside the approved scenarios.

## Agent lifecycle and tools

Keep one clean developer context per batch. The orchestrator retains the plan,
decisions, and compact state; the developer owns only the assigned boundary.
The same developer persists through the atomic commits of that batch and,
within an approved `SCENARIO_GROUP`, through its consecutive scenarios. Rotate
when the batch is GREEN or at an escalation; an exceptional rotation may occur
at another deployable commit. Never rotate in the middle of a gate or to reset
a CI diagnosis.

Before delegation, confirm access to the complete Delivery MCP surface:
`delivery_test`, `delivery_inspect`, `delivery_prepare`, `delivery_job_wait`,
`delivery_job_cancel`, `delivery_verify_head`, `delivery_ci_inspect`, and
`delivery_finalize`. An agent without the required surface must stop rather
than invent a raw-command substitute.

Developers do not poll CI or remain idle after a push. Long operations return a
job ID; use bounded `delivery_job_wait`. Delivery evidence is tied to the
exact staged snapshot and HEAD.

## Handoff contract

Use the [delegation contract](references/delegation-modes.md) when preparing a
handoff. Transmit facts specific to the Android batch:

- User Story, batch, conduction, and granularity;
- HEAD, branch, working-tree state, known CI, and relevant receipts (not logs);
- active and completed provider scenarios;
- package paths, symbols, contracts, and material invariants;
- allowed scope, strict prohibitions, escalation conditions, and required
  skills;
- observable next boundary, delivery intent, owners, and close condition.

The next developer must not rediscover stable repository rules from a copied
transcript. If another agent changes HEAD or staging in the shared checkout,
pause, inspect again, preserve unrelated work, and regenerate preparation
evidence.

## Batch execution

For `SCENARIO` and `SCENARIO_GROUP`, the developer works one scenario and one
atomic boundary at a time:

1. follows the Android BDD/TDD loop with `delivery_test` for the active
   boundary;
2. applies the relevant architecture, API, Hilt, and testing skills;
3. when the boundary is coherent, compilable, and GREEN at its own test layer,
   stages it exactly and calls `delivery_prepare` with `prepare_commit`;
4. with `status: passed`, commits and, when authorized, pushes that boundary
   before starting the next one; intermediate commits may leave the outer
   scenario `@wip`;
5. in the final functional boundary, makes the complete scenario GREEN,
   removes its `@wip`, and prepares with `close_scenario`;
6. commits with
   `<type>[<us-number>]: imperative English description`, using the User Story
   identifier from the issue title, only after `status: passed`, then pushes when
   authorized;
7. advances to the next scenario only inside an approved `SCENARIO_GROUP`;
   otherwise emits the compact handoff and ends the batch.

For `MICROSTEP`, the developer stops after validation without staging,
preparing, committing, or pushing; those owners remain with the orchestrator
unless the contract says otherwise.

Never target a commit count. Do not commit RED work, split by file or layer,
leave a commit dependent on uncommitted code, combine unrelated boundaries, or
create a tag-only/closure-only commit.

## Repair and escalation

When CI fails, stop ordinary pushes. Inspect the exact failed SHA with
`delivery_ci_inspect`, prepare one atomic fix using intent `repair_ci` and the
same `repairsSha`, and use Gate R's one-time receipt. Do not use `--no-verify`
or `DELIVERY_SKIP_CI_CHECK`. Workflow changes and workflow-job failures are
`HUMAN_ONLY`; stop and escalate them. Follow the diagnostic protocol in
`android-testing-gates` and preserve the causal signature across handoffs.

## Closing

When declared feature files are complete and contain no `@wip`, verify HEAD
with the matching `close_batch` or `close_us` intent, then call
`delivery_finalize`. A User Story is complete only when finalization returns
`finalized: true` and `status: passed`; a pending CI state is not completion.

The compact handoff reports GREEN scenarios, SHAs/receipts, changed paths,
contract decisions, active diagnosis, tree and CI state, and the next action.
It contains no raw logs, tracebacks, full diffs, or MCP transcripts.
