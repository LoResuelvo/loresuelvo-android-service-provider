# Android delegation contracts

Use a bootstrap contract for every new developer or lost context. Use a delta
contract only for the same developer continuing a `SCENARIO_GROUP`.

## Bootstrap contract

```text
Identification and mode:
- User Story and batch:
- Conduction: USER_GUIDED | AGENT_ORCHESTRATED
- Granularity: MICROSTEP | SCENARIO | SCENARIO_GROUP

Base state:
- HEAD and branch:
- Working-tree state:
- Known CI state:
- Relevant receipts and SHAs (no logs):

Scenarios:
- Active provider scenarios and observable criteria:
- Completed scenarios:

Android context:
- Package, paths, symbols, and material invariants:
- Feature/glue, domain, data, UI, and test boundaries:

Governance:
- Allowed scope:
- Strict prohibitions:
- Escalation conditions:
- Required skills:

Next boundary:
- Observable behavior:
- Active scenario task: existing code to reuse, allowed files/symbols:
- Required input/output interfaces and dependencies:
- Focused proof and explicit exclusions:
- Delivery intent and proposed commit message:
- Minimum artifacts:

Ownership and close:
- Editing, staging, commit, and push owners:
- Shared-checkout exclusivity:
- Open risks:
- Batch close condition:
- Next progress checkpoint and any running job ID:
```

Do not put raw commands, copied logs, or manually calculated gates in the
contract. The policy and Delivery MCP choose the gate.

## Delta contract

```text
Inherited HEAD, tree, and known CI:
Scenarios closed since the last handoff:
Active batch and scenarios:
Current granularity:
Material scope, prohibition, or invariant changes:
New required skills:
New evidence or risks:
Next atomic boundary and delivery intent:
Commit message proposal:
Owner or shared-checkout changes:
New continuation, escalation, or close conditions:
```

## Granularity rules

- `MICROSTEP`: one observable behavior; no commit or push by the developer.
- `SCENARIO`: one approved scenario; authorize every atomic commit required to
  reach GREEN rather than a predetermined count; stop at GREEN or escalation.
- `SCENARIO_GROUP`: two or three related scenarios; each must be GREEN before
  continuation, each may contain several atomic commits, and the group degrades
  to `SCENARIO` when coupling or ambiguity appears.

An intermediate atomic commit must be coherent, compilable, testable, and
independently reversible. It may leave the active scenario `@wip`. The final
functional commit that makes the scenario GREEN removes `@wip`; never create a
separate tag-only or closure-only commit.

## Delivery and closure

Use `delivery_test` during RED/GREEN. At every atomic commit boundary, stage
exactly and call `delivery_prepare`; wait for a returned job ID with bounded
`delivery_job_wait`, then commit and, when authorized, push before starting the
next boundary. Use `prepare_commit` for intermediate boundaries and
`close_scenario` for the final functional boundary that makes a scenario
GREEN. For a completed HEAD, call `delivery_verify_head` with `close_batch` or
`close_us`, then use the same intent in `delivery_finalize`. Do not create
empty commits to manufacture evidence.

## Compact handoff

```text
Status: WORKING | BLOCKED | READY_FOR_REVIEW | DONE
Active scenario/task and next checkpoint:
GREEN scenarios:
Relevant SHAs and receipts:
Contract or decision changes:
Material paths changed:
Active causal diagnosis (if any):
Tree state:
Known CI state:
Next permitted action:
Blocking prerequisite and owner, or none:
Running job ID, or none:
```

Never include successful logs, raw stack traces, full diffs, or MCP payloads.

## Small-task example

For a Profile retry scenario, first arrange a failed account port and observe
the real ViewModel's error state. Next prove that retry reloads the account,
then wire the existing screen callback if needed. The same developer owns
these steps and the final functional commit. Do not prebuild identity or
payment behavior from later scenarios. Review the finished diff against the
approved scenario, reuse opportunities, MVVM/navigation boundaries, and tests
before preparing it; task count does not set commit count.

## Progress and resources

The developer acknowledges scope, owners, and the next task before editing.
Report on task completion, before a long check, immediately on a blocker, and
after each commit. Never end a delegated turn with only a promise to continue:
resume the task or return an explicit handoff using the status fields above.

The orchestrator tracks the active agent/task and reconciles its state after
each bounded wait. If idle without a handoff, request the missing report; if
blocked, resolve the prerequisite or narrow the task before redispatch. Keep
the user informed at least once per minute during active work. No replacement
worker may race the existing writer or a running gate.

Run only one Gradle/device job at a time. Reuse an available physical device;
report a missing device instead of starting an emulator unless authorized.
Keep the plan and handoff in persistent local storage; never copy receipts.
