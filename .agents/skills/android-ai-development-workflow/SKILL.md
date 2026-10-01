---
name: android-ai-development-workflow
description: "Coordinate Android User Story batches between an orchestrator and developers with bounded handoffs, Delivery MCP evidence, and CI-aware repair."
---

# Android AI Development Workflow

Use for guided or orchestrated User Story batches. Boundary-specific rules
remain in the architecture, API, BDD, commit, and testing skills.

## Conduction and ownership

- `USER_GUIDED`: the user approves relevant decisions and batch boundaries.
- `AGENT_ORCHESTRATED`: the orchestrator executes the approved plan and asks
  only for genuine escalation.

Both modes use at most three implementation batches per User Story. The
user owns the initial approved-scenario commit, outside that batch count.
Each batch has one retained developer and one implementation commit covering
its complete outcome. Do not impose intermediate scenario, layer, or task
commits. Do not require individual scenarios to pass before implementing
another scenario in the same batch.

The orchestrator is the reviewer: inspect requirements, simplicity,
architecture/navigation, security, and test coverage directly. Do not spawn
another reviewer. The developer pauses editing for this review and fixes its
findings before running commit validation. In guided work without delegation,
the coordinating agent still performs the review explicitly.

Keep one active batch, one implementation writer, one canonical checkout,
and one Gradle/device job. Do not create temporary worktrees or worker MCP
clients. The orchestrator must not edit the developer's files concurrently.
Retain the developer through review, failed checks, commit, and CI diagnosis;
rotate only at a completed batch or a necessary escalation.

## Preparation and handoff

Read `.agents/local/plans/ANDROID-US-<ID>.md`; require an approved or active
plan and the user's approved feature-baseline commit. Reconcile HEAD,
scenarios, external contracts, and open decisions before dispatch.
Confirm the Delivery MCP surface in `.delivery/README.md` and run
`delivery_closure_preflight` with the numeric US ID and feature-baseline SHA.
Resolve historical evidence gaps before implementation.

Give each new developer the [delegation contract](references/delegation-modes.md):
exact approved scenarios, complete batch outcome, reuse, allowed paths,
interfaces, exclusions, proposed commit subject, validation scope, and final
device coverage. A continuing developer needs only changed facts. Do not ask
it to rediscover the full plan or provide a new handoff per scenario.

## Batch execution

1. The developer implements the entire assigned outcome and its tests.
   Mandatory intermediate RED/GREEN executions are removed; no fabricated
   RED evidence is required. Keep unfinished scenarios tagged `@wip`.
2. The orchestrator reviews the complete diff against acceptance criteria
   and code/test guidelines; the same developer addresses findings.
3. At commit preparation, enable completed scenarios by removing `@wip`,
   stage only the complete batch, and run policy-selected `delivery_prepare`
   with `prepare_commit`. Inspect actual Cucumber execution in the resulting
   proof. Use `delivery_test` at this checkpoint only for missing focused
   evidence or diagnosis; do not duplicate checks already covered by the gate.
4. Fix failed checks and repeat the necessary validation; changes invalidate
   snapshot-bound evidence. Commit only after passed preparation, then push
   when authorized. Do not create routine per-scenario closure receipts.
5. Record the batch SHA, proof, review result, CI state, and remaining scope.
   A batch with future `@wip` in its feature is completed implementation,
   not formal Delivery feature closure. Do not run redundant `close_batch`
   verification merely as a progress checkpoint.

Continue permitted work while CI is pending. On `CI_WINDOW_FULL`, use bounded
`delivery_ci_window_wait`; a failed SHA requires `repair_ci` before ordinary
pushes. Await returned check jobs with bounded `delivery_job_wait`.
Workflow changes and workflow-job failures remain `HUMAN_ONLY`.
If HEAD/staging changes externally, preserve unrelated work and prepare again.

## Final verification

Include necessary instrumented test code in the final planned batch. Do not
query devices or execute instrumented tests during implementation or commit
preparation. After all batches pass review and JVM/BDD, lint, and build checks,
run the full instrumented suite as the final verification phase. The final
meaningful commit carries `Delivery-Verify-US: <numeric ID>` for instrumented
CI. Do not launch an emulator without authorization.

The final developer owns device/CI repairs and their necessary fix commits;
these are corrections of the final batch, not additional feature batches.
Re-review changed behavior, prepare each repair, and repeat affected final
verification. Never suppress a required repair to satisfy the planned count.
Use matching `delivery_verify_head` and `delivery_finalize(close_us)` for the
final exact HEAD. Only `finalized: true, status: passed` completes the US.
Keep the plan current and report evidence without copying runtime logs.
