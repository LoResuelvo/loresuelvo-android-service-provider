# AGENTS.md — LoResuelvo Android Service Provider

Last updated: 2026-09-24.

This is the repository-wide agent contract. Read it before work, then load only
the skills relevant to the task. Use [`README.md`](README.md) for human setup,
[`.delivery/README.md`](.delivery/README.md) for Delivery operations, and
`.delivery/policy.v1.json` for deterministic classification and gates.

## Working agreement

- Inspect the working tree before editing and preserve unrelated changes. This
  repository is the only implementation workspace; do not modify siblings.
- Keep code, tests, diagnostics, comments, skills, agent rules, commit messages,
  and internal documentation in English. Put user-visible text in localized
  Android resources.
- Never use `--no-verify`, `DELIVERY_SKIP_CI_CHECK`, destructive Git commands,
  copied runtime evidence, or committed `local.properties` and secrets.
- Load a skill for the boundary being changed; do not load every skill by
  default. Follow the skill routing below.

## Application boundaries

The provider app uses package `com.loresuelvo.serviceprovider` and `Dev`,
`Staging`, and `Prod` flavors. Android checks use JDK 17 through
`scripts/with-android-env.sh`; Delivery uses Node 24 through
`scripts/with-node-24.sh`. Use `Makefile` targets or the documented wrappers,
and keep machine-specific paths in the environment or ignored
`local.properties`; never hardcode them in Make targets or documentation.

```text
ui → domain/usecase → domain
data ───────────────→ domain
```

Keep domain pure and infrastructure in data adapters. UI owns composables,
ViewModels, state, events, and navigation; it must not depend directly on
`data/`. DTOs and wire names stay in `data/api/`; mappers do not own business
rules. Use typed outcomes, immutable `StateFlow` UI state, and injected
dependencies rather than mutable global objects. The
[clean-architecture skill](.agents/skills/android-clean-architecture/SKILL.md)
owns the detailed dependency rules and import checks.

Never log tokens, credentials, or request/response payloads. Secrets and
flavor values come from `local.properties`, Gradle properties, or CI
environment variables. Cleartext HTTP is limited
to the Dev overlay; Staging and Prod require HTTPS. Use the API, Hilt, and
Compose skills for their respective implementation boundaries.

## Testing and Delivery

JVM unit and Cucumber tests run without a device; instrumented Android tests
require a device or emulator. Cucumber JVM scenarios are the acceptance
specifications; device tests verify Android UI boundaries. Use the BDD and
testability skills for test design, and the testing-gates skill for delivery
validation.

Delivery MCP selects checks from `.delivery/policy.v1.json`; do not infer a
weaker gate or replace it with a focused test. Use `delivery_test` for focused
RED/GREEN, `delivery_prepare` for the exact staged snapshot, and
`delivery_verify_head` plus `delivery_finalize` for closure. See
[the Delivery reference](.delivery/README.md) for the complete MCP surface,
gate table, safe commands, jobs, receipts, CI, and recovery. A missing device,
credential, or provider connection is a blocked prerequisite, never a pass.
Disabled analyzers are `not_applicable`, not evidence of quality.

Before implementing a User Story, run `delivery_closure_preflight` with its
numeric ID and any known feature-baseline SHA; resolve missing historical
evidence before dispatch. Keep one active scenario, one implementation writer,
one canonical checkout, and one Gradle/device job at a time. Do not start an
emulator without authorization. Use
[the orchestration skill](.agents/skills/android-ai-development-workflow/SKILL.md)
for scoped outside-in tasks, developer handoffs, CI window handling, and
progress. Local prompts must not define a competing policy.

## Commits and CI

Use `<type>[<us-number>]: imperative English description`, where the bracketed
number comes from the User Story title rather than the GitHub issue number.
Stage only the intended files and prepare that exact snapshot before committing;
changing HEAD, stage, policy, intent, or scope invalidates its receipt.

Each commit must be coherent, compilable, testable, and independently
reversible. Batch or scenario granularity does not set the commit count. Do
not split mechanically by file or layer, and do not accumulate independent
boundaries into a mega-commit. Intermediate commits may keep the active
scenario `@wip`; remove it with the functional commit that makes the scenario
GREEN, never in a closure-only commit. The
[commit skill](.agents/skills/android-commit-governance/SKILL.md) owns boundary
planning, allowed types, preparation, and PR details.

Continue permitted work while CI is pending. When the policy CI window is
full, use bounded `delivery_ci_window_wait`; a failed SHA needs diagnosis and
the `repair_ci` path before ordinary pushes. Do not manually poll. Workflow
changes and workflow-job failures are `HUMAN_ONLY`; agents escalate them.

## Skill routing

Load only the skills that match the current change:

| Change | Skill |
| --- | --- |
| Domain, data, UI, or dependency direction | [android-clean-architecture](.agents/skills/android-clean-architecture/SKILL.md) |
| Observable behavior, Gherkin, or tests | [android-bdd-tdd-process](.agents/skills/android-bdd-tdd-process/SKILL.md) |
| Delivery gates, release, merge, or CI diagnosis | [android-testing-gates](.agents/skills/android-testing-gates/SKILL.md) |
| HTTP, Retrofit, DTOs, or mappers | [android-api-client-governance](.agents/skills/android-api-client-governance/SKILL.md) |
| Hilt graph, bindings, or Hilt tests | [android-hilt-governance](.agents/skills/android-hilt-governance/SKILL.md) |
| Compose, navigation, or UI state | [android-compose-quality-governance](.agents/skills/android-compose-quality-governance/SKILL.md) |
| Commits, PRs, or history review | [android-commit-governance](.agents/skills/android-commit-governance/SKILL.md) |
| Agent contracts or documentation | [android-doc-governance](.agents/skills/android-doc-governance/SKILL.md) |
| Complexity, coupling, or oversized code | [android-maintainability-governance](.agents/skills/android-maintainability-governance/SKILL.md) |
| Configured analyzers or architecture guards | [android-static-analysis-governance](.agents/skills/android-static-analysis-governance/SKILL.md) |
| Deterministic tests or DI/state seams | [android-testability-governance](.agents/skills/android-testability-governance/SKILL.md) |
| Complete User Story lifecycle | [android-us-delivery](.agents/skills/android-us-delivery/SKILL.md) |
| Agent batches and handoffs | [android-ai-development-workflow](.agents/skills/android-ai-development-workflow/SKILL.md) |

## Handoff

Keep evidence and commits attributable to their exact staged snapshot.
Humans own workflow changes, unavailable credentials, environment repair,
and requested `HUMAN_ONLY` actions. Documentation-only Gate `NONE` evidence
does not prove the Android workflow is ready for enforcement.

Report changed paths, checks run, blocked checks and causes, hook/enforcement
state, CI state by SHA, disabled analyzers, and remaining human work. Do not
copy logs, tokens, or generated runtime state into the report.
