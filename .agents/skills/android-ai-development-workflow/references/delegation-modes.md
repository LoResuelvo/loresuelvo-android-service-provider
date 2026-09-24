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
- Granularity: MICROSTEP | SCENARIO | SCENARIO_GROUP

Base state:
- Verified HEAD SHA, branch, and upstream:
- Staged/unstaged tree state and owner of existing changes:
- Known CI state by SHA, pending window count, or unknown:
- Relevant receipts, feature-baseline SHA, and closure-preflight result:

Scenarios:
- Feature path, runner, and exact approved Gherkin for assigned scenarios:
- Active scenario ID, @wip state, and prior GREEN scenario IDs:

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

Next boundary:
- Observable behavior:
- Ordered outside-in checkpoints for the active scenario, each with proof:
- Tentative commit boundaries: result, required files/dependencies, focused
  GREEN proof, Delivery intent, and proposed subject for each:
- Active scenario task: existing code to reuse, allowed files/symbols:
- Required input/output interfaces and dependencies:
- Focused RED test and expected failure; GREEN proof and exclusions:
- Expected artifacts and review checkpoint:

Ownership and close:
- Editing, staging, commit, and push owners:
- Shared-checkout exclusivity:
- Open risks:
- Batch close condition:
- Next progress checkpoint and any running job ID:
```

Do not put raw commands, copied logs, or manually calculated gates in the
contract. The policy and Delivery MCP choose the gate. Copy only the approved
Gherkin within the assigned scope; do not paste the entire plan or invent
clean-tree, green-CI, or device facts. For `MICROSTEP`, name the owner of any
later staging/commit/push and mark its commit map "none" because the developer
stops after validation.
Order active-scenario checkpoints by evidence: a runnable behavior RED, the
smallest needed UI/domain/data seam with focused GREEN proof, then
ViewModel/platform wiring and full scenario GREEN. Omit layers the behavior
does not need; a checkpoint is not automatically a commit.
Draft the commit boundaries before editing, then revise them when dependencies
become clear. Finish, prepare, and commit each independently GREEN boundary
before building the next independent one. Combine a slice with its required
dependency when it cannot compile or pass its focused tests alone; split a
newly discovered independent concern. Do not build the whole scenario and
divide its uncommitted diff retrospectively. A boundary map is a forecast, not
a commit quota or permission for layer-only commits.

When graph evidence informs the task, include its project and generation,
evidence tier and bounded scope, queries/pagination, qualified symbols and
paths, material call traces, coverage ranges/reasons, exact source fallback,
and unresolved questions. Do not send a raw graph transcript.

## Same-developer continuation

When the bootstrap remains in context, send a short update only for changed
HEAD/tree/CI facts, closed scenarios, revised commit boundaries, new risks or
device lessons, and the next action. New scope or a lost bootstrap requires a
fresh bootstrap, even for the same developer.

## Execution reference

The parent workflow skill defines granularity, ownership, and continuation;
`android-bdd-tdd-process` defines RED/GREEN and `@wip` closure. Delivery
intent, receipts, jobs, and CI recovery follow `.delivery/README.md` and
`android-testing-gates`. Keep this contract limited to facts for one handoff.

## Compact handoff

```text
Status: WORKING | BLOCKED | READY_FOR_REVIEW | DONE
Active scenario/task and next checkpoint:
GREEN scenarios:
Relevant SHAs and receipts:
Contract or decision changes:
Material paths changed:
Active causal diagnosis (if any):
Verified device/UI lesson and interaction recipe for the next developer:
Tree state:
Known CI state by SHA and pending window count:
Next permitted action:
Blocking prerequisite and owner, or none:
Running job ID, or none:
```

Never include successful logs, raw stack traces, full diffs, or MCP payloads.

## Small-task example

For a Profile retry scenario, a tentative map might be: `test[35]` for
compilable step glue and a fake after Gate 0 passes; `feat[35]` for retry
behavior with a focused ViewModel test; then `feat[35]` for the screen callback
and `@wip` removal when the full scenario runs GREEN. If the ViewModel slice
needs the callback to be testable, combine those boundaries. Do not prebuild
later identity or payment behavior; review each finished boundary before
preparing it. This example does not set a commit count.

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
