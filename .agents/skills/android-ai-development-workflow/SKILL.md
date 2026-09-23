---
name: android-ai-development-workflow
description: "Coordinate Android User Story batches between an orchestrator and developers with bounded handoffs, Delivery MCP evidence, and CI-aware repair."
---

# Android AI Development Workflow

Use this skill when an orchestrator delegates an Android User Story or a
bounded batch to another agent. BDD, gates, commits, architecture, and API
rules live in their dedicated skills.

Do not load this skill for an isolated edit or review with no delegation.

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

Run one batch, one active scenario, and one implementation writer at a time.
Use the canonical checkout for implementation, staging, receipts, and commits.
The orchestrator must not edit the developer's files concurrently. A read-only
reviewer may inspect a completed boundary; it does not start the next batch.
Do not create temporary worktrees or per-worker MCP clients for this workflow.

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

## Small tasks inside a batch

Before dispatch, break only the active scenario into the smallest useful
behavioral tasks. For each task name the observable result, existing code to
reuse, allowed files/symbols, required interfaces, focused proof, and exclusions.
Start outside-in with the scenario and real ViewModel/use-case behavior. Add
data or platform code only when that behavior requires it. Do not build all
repositories, then all ViewModels, then all screens for future scenarios.

Keep RED, GREEN, and a small refactor with the same developer. A task is not
automatically a commit or a new subagent. Commit when its complete behavioral
boundary is independently testable and reversible; one commit can be enough.
The developer owns staging, preparation, commit, and authorized push unless
the handoff explicitly assigns another owner. MICROSTEP never implies that
commit ownership. Stop scope growth and report necessary contract changes.

Use one covering review for spec compliance, simplicity, MVVM/navigation, and
test quality before preparation. For material or uncertain changes, delegate
that review read-only after the implementation writer stops. Reviewers report
findings; the retained developer fixes them. Do not repeat unchanged tests or
start open-ended refactors in a review loop.

These task-brief and review practices borrow from Superpowers' writing-plans
and subagent-driven-development; no installation or additional skill is required.
The Android scenario, gate, and commit contracts remain authoritative.

## Handoff contract

Use the [delegation contract](references/delegation-modes.md) when preparing a
handoff. Transmit facts specific to the Android batch:

- scope, interfaces, exclusions, owners, and focused proof;
- HEAD/tree/CI, active scenario/task, receipts, and any running job ID;
- `WORKING`, `BLOCKED`, `READY_FOR_REVIEW`, or `DONE`, and next action.

Follow that contract's progress/resource protocol, including immediate blocker
reports and controller reconciliation of idle workers. Serialize Gradle/device
jobs and do not launch an emulator without authorization.

If HEAD or staging changes externally, inspect again, preserve unrelated work,
and regenerate preparation evidence.

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

Never target a commit count, commit RED work, split by layer, or create
closure-only commits. Each boundary must stand without uncommitted code.

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
