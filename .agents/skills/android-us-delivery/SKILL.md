---
name: android-us-delivery
description: "Deliver a LoResuelvo Android User Story from approved scenarios through validated integration."
---

# Android User Story Delivery

Use this skill for a complete provider User Story or feature. It defines the
delivery lifecycle; architecture, BDD, testing, API, Hilt, and commit details
remain in their dedicated skills.

## Preparation

1. Confirm `git status --short --branch`, the User Story scope, and the
   provider paths affected.
2. Write every acceptance scenario before production code. Give each scenario
   one `When`, a stable ID, and observable outcomes. The current provider
   example is `app/src/test/resources/features/auth/provider-welcome.feature`.
3. Present the scenarios and wait for explicit functional approval. Commit the
   approved feature contract separately before production work.
4. Confirm the functional boundary and the owner of staging, commit, and push.
5. Do not include `.github/workflows/**` in an agent change; workflow changes
   are `HUMAN_ONLY`.

## Development loop

Work outside-in in an approved batch:

1. Add the smallest Gherkin step and deterministic JVM fake in RED.
2. Add focused tests for domain, repository, or `WelcomeViewModel` behavior.
3. Implement the smallest production change and connect the Compose route only
   at the integration boundary.
4. Add or update an instrumented test when Activity, navigation, or device
   behavior is involved.
5. Run `delivery_test` for focused TDD. Gate 0 and Gate B use the complete Dev
   JVM task because no reliable feature-file runner exists.
6. At every coherent, compilable, independently testable boundary, stage the
   exact change and call `delivery_prepare`; with `status: passed`, commit and
   when authorized push before starting the next boundary. Do not target a
   commit count.
7. When the complete scenario is GREEN, remove its `@wip` tag in that final
   functional change and prepare it with intent `close_scenario`.
8. Commit only after preparation returns `status: passed`, using
   `<type>[<us-number>]: imperative English description`, where the bracketed
   value is the User Story identifier from the issue title rather than the
   GitHub issue number. Push immediately when the batch contract
   authorizes it.
9. Continue to another scenario only inside an approved `SCENARIO_GROUP` after
   the current scenario is GREEN; otherwise hand off and end the batch.

Use `make test`, `make lint`, `make build`, and `make e2e` for human checks or
focused diagnosis. The policy-selected MCP operation remains the authoritative
pre-commit path; do not hand-calculate a gate or use arbitrary commands.

## Closing a batch or User Story

Close a batch only when every feature file declared for it is complete and has
no `@wip`. For a clean HEAD, record the final gate with
`delivery_verify_head` using the matching `close_batch` or `close_us` intent.
Then call `delivery_finalize` with that same intent. A User Story is complete
only when it returns `finalized: true` and `status: passed`, including required
CI evidence.

If a feature still contains future `@wip` scenarios, report the completed
scenario or batch and do not claim formal batch closure.

## Evidence, jobs, and repair

- Receipts are bound to HEAD, the exact staged snapshot, policy, intent, and
  scope. Any staged change invalidates a receipt.
- Long Gate C/D/R runs and finalization may return a `jobId`; wait with
  bounded `delivery_job_wait` and never busy-poll.
- A failed CI SHA requires `delivery_ci_inspect`, an atomic fix, and
  `delivery_prepare` with intent `repair_ci` plus that exact `repairsSha`.
- Gate R is one-time authorization for the repair push. It requires Staging
  credentials and must not substitute Dev.
- Never use `--no-verify` or `DELIVERY_SKIP_CI_CHECK`.
- Workflow edits and workflow CI failures stop with `HUMAN_ONLY` escalation.

Before handoff, read the [quality checklist](references/checklist-quality.md)
and [security checklist](references/checklist-security.md). Report scenarios,
SHAs/receipts, changed paths, blocked checks and causes, CI state, hook state,
disabled analyzers, and the next permitted action without copying logs.
