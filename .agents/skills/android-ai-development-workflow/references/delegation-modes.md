# Android delegation contracts

Use a bootstrap contract for every new developer or lost context. A developer
continuing with the full bootstrap in context needs only a brief update of
changed facts, not another formal contract. Fill fields with observed facts,
mark unknowns explicitly, and give the developer the approved scenario text
it needs without requiring it to rediscover the whole User Story plan.

## Bootstrap contract

```text
Identification and mode:
- Numeric User Story ID, batch, and assigned scenario IDs:
- Canonical plan key/status and approved batch scope:
- Conduction: USER_GUIDED | AGENT_ORCHESTRATED
- Batch: 1..3; one implementation commit

Base state:
- Verified HEAD SHA, branch, and upstream:
- Staged/unstaged tree state and owner of existing changes:
- Known CI state by SHA, pending window count, or unknown:
- Relevant receipts, feature-baseline SHA, and closure-preflight result:

Scenarios:
- Feature path, runner, and exact approved Gherkin for assigned scenarios:
- Assigned scenario IDs, @wip state, and completed batches:

Android context:
- Relevant package paths, symbols, navigation destinations, and resources:
- Compose state/events, domain ports/use-case interfaces, and test seams:
- API endpoint/DTO/mapper and DI contracts only when this scope needs them:
- Verified device/UI lesson and reproducible interaction recipe, or none:
- Structural graph evidence and coverage/fallback, if discovery mattered:

Governance:
- Allowed scope:
- Strict prohibitions:
- Escalation conditions:
- Required skills:

Batch outcome:
- Complete observable result and assigned acceptance criteria:
- Existing code to reuse and allowed files/symbols:
- Required interfaces/dependencies and exclusions:
- Single proposed commit subject and Delivery intent:
- JVM/BDD, lint/build proof at commit preparation:
- Orchestrator review checkpoint before test execution:
- Instrumented coverage included in the final batch, executed only at closure:

Ownership and close:
- Editing, staging, commit, and push owners:
- Shared-checkout exclusivity:
- Open risks:
- Batch close condition:
- Next progress checkpoint and any running job ID:
```

Do not put raw commands, copied logs, or manually calculated gates in the
contract. The policy and Delivery MCP choose the gate. Copy only approved
Gherkin in the assigned scope. Do not invent clean-tree, green-CI, or device
facts. The user owns the initial scenario commit. The developer implements
its batch end to end, pauses for orchestrator review, fixes findings, and
runs checks at commit preparation. No intermediate RED/GREEN or per-scenario
commits are required. Keep missing device proof explicit until final verification.

When graph evidence informs the task, include its project and generation,
evidence tier and bounded scope, queries/pagination, qualified symbols and
paths, material call traces, coverage ranges/reasons, exact source fallback,
and unresolved questions. Do not send a raw graph transcript.

## Same-developer continuation

When the bootstrap remains in context, send a short update only for changed
HEAD/tree/CI facts, closed scenarios, revised batch scope, new risks or
device lessons, and the next action. New scope or a lost bootstrap requires a
fresh bootstrap, even for the same developer.

## Execution reference

The parent workflow skill defines granularity, ownership, and continuation;
`android-bdd-tdd-process` defines acceptance validation and `@wip` handling. Delivery
intent, receipts, jobs, and CI recovery follow `.delivery/README.md` and
`android-testing-gates`. Keep this contract limited to facts for one handoff.

## Compact handoff

```text
Status: WORKING | BLOCKED | READY_FOR_REVIEW | DONE
Active batch/task and next checkpoint:
GREEN scenarios:
Relevant SHAs and receipts:
Contract or decision changes:
Material paths changed:
Active causal diagnosis (if any):
Verified device/UI lesson and interaction recipe for the next developer:
Tree state:
Known CI state by SHA and pending window count:
Pending final device coverage and verification:
Next permitted action:
Blocking prerequisite and owner, or none:
Running job ID, or none:
```

Never include successful logs, raw stack traces, full diffs, or MCP payloads.

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

Run only one Gradle/device job at a time. Do not query or use a device until
final User Story verification. Then reuse an available physical device;
report a missing device instead of starting an emulator unless authorized.
Keep the plan and handoff in persistent local storage; never copy receipts.
