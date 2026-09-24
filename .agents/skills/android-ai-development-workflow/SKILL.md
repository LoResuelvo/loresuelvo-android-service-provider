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

For a planned User Story, resolve its exact
`.agents/local/plans/ANDROID-US-<ID>.md` path and require an `approved` or
`active` plan. Check its base against current HEAD history and reconcile
intervening changes, approved scenarios, external contracts, and open
questions. Resolve material drift before dispatch; a changed functional
contract needs renewed approval.
Keep the plan history current as scenarios and decisions change.

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

Before delegation, confirm the Delivery MCP surface listed in `.delivery/README.md` is
available. Run `delivery_closure_preflight` with the numeric US ID and known
feature-baseline SHA. Resolve or escalate each reported evidence gap before
implementation; context notes cannot create historical receipts.

## Small tasks and outside-in boundaries inside a batch

Before dispatch, break only the active scenario into the smallest useful
behavioral tasks. For each task name the observable result, existing code to
reuse, allowed files/symbols, required interfaces, focused proof, and
exclusions. Use the outside-in sequence and test boundaries in
`android-bdd-tdd-process`; assign only work the active scenario needs. Propose
commit boundaries for that scenario before implementation, each with its
result, required dependencies, focused GREEN proof, Delivery intent, and
subject. Revisit the map when the implementation reveals a dependency.

Keep RED, GREEN, and a small refactor with the same developer. A task or layer
is not automatically a commit. Commit when its complete logical boundary is
coherent, compilable, testable, and independently reversible; a scenario may
need one or several commits. Do not accumulate separable boundaries into an
unreviewable commit or split a necessary vertical boundary into broken
layer-only commits. The developer owns staging, preparation, commit, and
authorized push unless the handoff assigns another owner. Finish each
independently GREEN boundary before starting the next; do not implement the
whole scenario and attempt to split it into commits afterward. Stop scope
growth and report necessary contract changes.

Carry verified device/UI lessons between batches, including IME dismissal,
BottomBar touch targets, and native picker behavior when relevant. Use one
covering review for spec compliance, simplicity, MVVM/navigation, and test
quality before preparation. For material or uncertain changes, delegate that
review read-only after the implementation writer stops. The retained developer
fixes findings. Do not repeat unchanged tests or start open-ended refactors.

## Handoff contract

Use the [delegation contract](references/delegation-modes.md) for bootstrap,
same-developer updates, and compact handoff fields. Give every new developer a
filled bootstrap with the exact approved Gherkin, active outside-in
checkpoints, and tentative commit boundaries. Do not ask them to reconstruct
the full plan. Include verified device/UI lessons, the active causal diagnosis,
and the next permitted action.

Follow that contract's progress/resource protocol, including immediate blocker
reports and controller reconciliation of idle workers. Serialize Gradle/device
jobs and do not launch an emulator without authorization.

If HEAD or staging changes externally, inspect again, preserve unrelated work,
and regenerate preparation evidence.

## Batch execution

For `SCENARIO` and `SCENARIO_GROUP`, follow `android-bdd-tdd-process` for
intermediate and final functional boundaries, and `android-commit-governance`
for preparation and commits. Advance to another scenario only inside an
approved `SCENARIO_GROUP`.

Continue permitted work while CI is pending. If preparation reports
`CI_WINDOW_FULL`, call bounded `delivery_ci_window_wait` and retry preparation
only when it returns `ready`. On a failed SHA, stop ordinary pushes and follow
the repair path. `delivery_job_wait` applies only to a returned job ID.

For `MICROSTEP`, the developer stops after validation without staging,
preparing, committing, or pushing; those owners remain with the orchestrator
unless the contract says otherwise.

## Repair and escalation

For failed CI, use the exact SHA and `repair_ci` path in
`android-testing-gates`. Preserve the causal diagnosis across handoffs;
workflow changes and workflow-job failures are `HUMAN_ONLY`.

## Closing

For a clean completed HEAD, use matching `close_batch` or `close_us` intents
in `delivery_verify_head` and `delivery_finalize`. A batch may return
`finalized: true, status: passed_pending_ci`; User Story closure requires
`finalized: true, status: passed`. Mark the plan complete only after that
User Story result.
