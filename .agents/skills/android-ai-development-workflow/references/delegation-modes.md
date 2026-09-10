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
- Delivery intent and proposed commit message:
- Minimum artifacts:

Ownership and close:
- Editing, staging, commit, and push owners:
- Shared-checkout exclusivity:
- Open risks:
- Batch close condition:
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
GREEN scenarios:
Relevant SHAs and receipts:
Contract or decision changes:
Material paths changed:
Active causal diagnosis (if any):
Tree state:
Known CI state:
Next permitted action:
```

Never include successful logs, raw stack traces, full diffs, or MCP payloads.
