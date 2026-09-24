---
name: android-us-delivery
description: "Deliver a LoResuelvo Android User Story from approved scenarios through validated integration."
---

# Android User Story Delivery

Use this skill for a complete provider User Story or feature. It defines the
delivery lifecycle; architecture, BDD, testing, API, Hilt, and commit details
remain in their dedicated skills.

## Do not load

Do not load it for an isolated scenario, small refactor, or documentation-only
change. Use the skill for the specific boundary instead.

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

Before implementation, run `delivery_closure_preflight` for the numeric US ID
and known feature-baseline SHA. Resolve a missing ledger entry at this point.
Then use `android-ai-development-workflow` for batch ownership, boundaries,
handoffs, and CI window handling; use `android-bdd-tdd-process` for the actual
RED/GREEN sequence. Complete one scenario before starting the next. Apply the
architecture, API, Hilt, Compose, and testability skills only where the active
change crosses their boundaries. Delivery selects every staged gate; focused
TDD checks never replace it.

## Closing a batch or User Story

Close a batch only when its declared feature scope has no `@wip`. For a clean
HEAD, call `delivery_verify_head` and `delivery_finalize` with the same
`close_batch` or `close_us` intent and exact scope. A batch may finish with
`passed_pending_ci`; a User Story requires `finalized: true, status: passed`.

If a feature still contains future `@wip` scenarios, report the completed
scenario or batch and do not claim formal batch closure.

## Evidence, jobs, and repair

Follow `.delivery/README.md` for receipt validity, jobs, and recovery, and
`android-testing-gates` for CI diagnosis. Never bypass hooks or substitute a
weaker check for an unavailable device or Staging credential.

Before handoff, read the [quality checklist](references/checklist-quality.md)
and [security checklist](references/checklist-security.md). Report scenarios,
SHAs/receipts, changed paths, blocked checks and causes, CI state, hook state,
disabled analyzers, and the next permitted action without copying logs.
