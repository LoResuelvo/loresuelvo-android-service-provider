# AGENTS.md — LoResuelvo Android Service Provider

Last updated: 2026-09-10.

This is the canonical contract for agents working in this repository. Read it
before loading a skill. Human setup belongs in [`README.md`](README.md), and
the operational delivery reference belongs in
[`.delivery/README.md`](.delivery/README.md).

## Working agreement

1. Read this file and load every skill directly relevant to the task; do not
   load unrelated skills.
2. Keep source code, tests, diagnostics, comments, skills, agent rules,
   commit messages, and internal documentation in English. User-visible text
   remains in localized Android resources.
3. Inspect the working tree before editing. Preserve unrelated user changes.
4. Do not use `--no-verify`, `DELIVERY_SKIP_CI_CHECK`, destructive Git
   commands, or copied runtime evidence.
5. The target repository is the only implementation workspace for this
   migration. Do not modify sibling repositories.

## Repository scope and current baseline

This application serves LoResuelvo providers. Its package is
`com.loresuelvo.serviceprovider`; flavors are `Dev`, `Staging`, and `Prod`.
The current walking skeleton contains the Welcome journey, Hilt, Retrofit,
OkHttp, Auth0, Navigation Compose, and Cucumber JVM infrastructure. There is
no image-upload workflow in this application; do not add an image-upload skill
until that product flow exists.

The stack is:

- Kotlin 2.0.21, Android Gradle Plugin 8.13.2, and the Gradle wrapper.
- Jetpack Compose, Material 3, Navigation Compose, and StateFlow/UDF.
- Hilt with KAPT/KSP, Retrofit 2.11.0, OkHttp 4.12.0, and Auth0 SDK 2.11.0.
- JUnit4, MockK, Turbine, Robolectric, MockWebServer, Cucumber JVM, and Hilt
  Android testing.
- Node.js 24 LTS for `tools/delivery-mcp`. Its package declares
  `engines.node` as `>=24 <25`.

Use JDK 17 to run Gradle. The Android module currently compiles against Java
11 bytecode, while the CI and local toolchain requirement is JDK 17.

The canonical GNU Make entry point is `Makefile`. Its Android targets route
Gradle, ADB, and instrumented-test commands through
`scripts/with-android-env.sh`, which is the shared boundary for Java and
Android SDK environment setup. Keep machine-specific toolchain values in the
shell or ignored `local.properties`; never encode them in Make targets or
documentation.

Delivery Make targets, repository Git hooks, and Codex delivery entry points
route through `scripts/with-node-24.sh`. The wrapper honors `DELIVERY_NODE`,
then discovers a repository-sibling `.toolchains` Node 24 installation or a
Node 24 executable on `PATH`; it rejects other Node major versions.

## Architecture

```text
ui → domain/usecase → domain
data ───────────────→ domain
```

`domain/` contains pure entities, ports, typed outcomes, and use cases.
`domain/usecase/` orchestrates ports without infrastructure imports.
`data/` owns adapters, HTTP, Auth0, Android storage, DTOs, and mappers.
`ui/` owns composables, ViewModels, navigation, state, and events.

Examples in the provider application are:

- `domain/category/CategoryRepository.kt` and
  `domain/usecase/category/GetCategoriesUseCase.kt`;
- `data/api/ApiCategoryRepository.kt`, `data/api/BackendApi.kt`, and
  `data/api/mapper/CategoryMapper.kt`;
- `ui/auth/WelcomeViewModel.kt` and
  `ui/screens/auth/WelcomeScreen.kt`.

### Dependency rules

- Inner layers must not import `data`, `ui`, `android.*`, Dagger/Hilt,
  OkHttp, Retrofit, or serialization.
- DTOs belong only in `data/api/dto/`; backend `snake_case` must not leak into
  domain or UI types.
- Mappers only translate transport data. Business rules belong in domain or
  use cases.
- Each use case is a class with one `operator fun invoke(...)` and follows the
  `VerbSubjectUseCase` naming convention.
- Outcomes and failures are typed `sealed interface`s, never generic error
  strings.
- UI exposes immutable `StateFlow` state and handles events in ViewModels.
- Do not add mutable global `object`s. Use Hilt injection instead.

Validate domain purity and UI boundaries with these checks (zero matches are
expected):

```bash
grep -RInE 'import (com\.loresuelvo\.serviceprovider\.(data|ui)|android\.|dagger|hilt|okhttp3|retrofit2|kotlinx\.serialization)' \
  app/src/main/java/com/loresuelvo/serviceprovider/domain/
grep -RIn 'import com\.loresuelvo\.serviceprovider\.data\.' \
  app/src/main/java/com/loresuelvo/serviceprovider/ui/
```

### Hilt and security

- `LoresuelvoApp` is annotated with `@HiltAndroidApp`.
- `MainActivity` is annotated with `@AndroidEntryPoint` and only hosts
  `LoResuelvoNav`.
- ViewModels use `@HiltViewModel` and constructor injection; routes use
  `hiltViewModel()`.
- Process-wide Retrofit, OkHttp, repositories, and the encrypted session
  store belong in `SingletonComponent` modules.
- Never log tokens, request/response payloads, or credentials.
- Secrets and flavor values come from `local.properties`, Gradle properties,
  or CI environment variables. Never commit `local.properties`.
- Cleartext HTTP is limited to the Dev network-security overlay. Staging and
  production are HTTPS-only.

## Test topology

Do not confuse the following layers:

| Layer                     | Command                 | Scope                                                    |
| ------------------------- | ----------------------- | -------------------------------------------------------- |
| JVM unit and Cucumber JVM | `make test FLAVOR=Dev`  | `testDevDebugUnitTest`; no device                        |
| Android Lint              | `make lint FLAVOR=Dev`  | `lintDevDebug`                                           |
| Debug build               | `make build FLAVOR=Dev` | `assembleDevDebug`                                       |
| Instrumented UI           | `make e2e FLAVOR=Dev`   | `connectedDevDebugAndroidTest`; device/emulator required |

Gherkin files live under `app/src/test/resources/features/`. Cucumber glue
and runners live under `app/src/test/java/com/loresuelvo/serviceprovider/bdd/`.
The provider has no reliable feature-file-to-runner command, so delivery Gate
0 and Gate B run the complete Dev JVM test task. Instrumented tests live under
`app/src/androidTest/` and are not Cucumber scenarios.

Useful focused checks are:

```bash
scripts/with-android-env.sh ./gradlew :app:testDevDebugUnitTest --tests '*WelcomeViewModelTest*'
scripts/with-android-env.sh ./gradlew :app:testDevDebugUnitTest --tests '*WelcomeCucumberTest'
```

Do not call instrumented tests acceptance scenarios: the JVM Cucumber layer
contains acceptance specifications, while device tests verify Android UI
boundaries.

## Delivery workflow

The delivery runtime is isolated in `tools/delivery-mcp/` and uses Node 24.
The policy in `.delivery/policy.v1.json` is the single source of truth for
classification and gates. Safe commands are exact allowlisted commands:

```text
npm --prefix tools/delivery-mcp test
make test FLAVOR=Dev
make lint FLAVOR=Dev
make build FLAVOR=Dev
make e2e FLAVOR=Dev
make test FLAVOR=Staging
make lint FLAVOR=Staging
make build FLAVOR=Staging
make e2e FLAVOR=Staging
```

Agents use the MCP operations `delivery_test`, `delivery_inspect`,
`delivery_prepare`, `delivery_job_wait`, `delivery_job_cancel`,
`delivery_verify_head`, `delivery_ci_inspect`, and `delivery_finalize`.
Humans can use the matching `make delivery-*` targets. The executor uses
`shell: false`, rejects arbitrary commands and environment assignments, and
keeps generated evidence under `.delivery/runtime/`.

For a recoverable background job, humans use `make delivery-job-wait
ARGS="--job-id <job-id>"` or `make delivery-job-cancel ARGS="--job-id
<job-id>"`; agents use the corresponding MCP operations.

### Gates

| Gate   | Checks                                                                                      | Use                                                       |
| ------ | ------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| `NONE` | none                                                                                        | Documentation-only or empty diff                          |
| `0`    | complete Dev JVM test task                                                                  | BDD feature/glue compatibility                            |
| `A`    | Dev JVM tests; delivery tooling also runs delivery unit tests                               | Isolated domain Kotlin or delivery tooling                |
| `B`    | complete Dev JVM test task                                                                  | Closing one BDD scenario                                  |
| `C`    | Dev lint, JVM tests, build, instrumented UI                                                 | Shared UI, DI, data, resource, manifest, or build changes |
| `D`    | no `@wip`, Gate C checks, and post-push CI green                                            | Complete batch or User Story                              |
| `R`    | delivery tests plus Staging lint, JVM tests, build, instrumented UI, and post-push CI green | One-time CI repair for `repairsSha`                       |

Gate selection is conservative. Ambiguous Kotlin or build changes select Gate
C; missing analyzers never produce Gate `NONE`. Disabled dependency-impact,
Cucumber-impact, and maintainability analyzers report `not_applicable` and are
not imported or executed.

Gate C and Gate D require a reachable device/emulator for `make e2e`; a
blocked environment must report the missing prerequisite instead of silently
skipping the check. Gate R requires real Staging credentials and must not
substitute Dev for CI parity.

### Shadow mode, hooks, and repair

Keep `DELIVERY_REQUIRE_EVIDENCE` disabled during shadow validation. Run the
delivery unit tests, smoke test, classification matrix, and representative
shadow inspections before installing or enforcing hooks. Hooks are lightweight:
they validate commit format, staged-snapshot receipts, and the CI window; they
never execute test suites.

Do not bypass a failed CI check with `--no-verify` or
`DELIVERY_SKIP_CI_CHECK`. For a failed remote SHA, inspect it with
`delivery_ci_inspect`, stage the atomic repair, and prepare with intent
`repair_ci` and that exact `repairsSha`. Gate R produces a single-use repair
receipt. Workflow changes and workflow CI failures are `HUMAN_ONLY` and must
be escalated.

## Commits and CI

The canonical commit format is:

```text
<type>[<us-number>]: imperative English description
```

Use the numeric User Story identifier in the issue title. For example, work
for `US-35` uses `[35]` even when GitHub assigns the issue a different number.

Use one of `feat`, `fix`, `refactor`, `test`, `chore`, `docs`, `build`, `ci`,
`perf`, or `style`. Keep commits atomic, stage exact files, and prepare the
staged snapshot before committing. A receipt is bound to the staged tree,
policy, intent, scope, and HEAD; changing any of these invalidates it.

Batch granularity does not determine commit count. A `SCENARIO` or
`SCENARIO_GROUP` authorizes the atomic commits needed within its approved
behavioral scope. Each commit must be coherent, compilable, testable, and
independently reversible; do not split by file or layer, and do not combine
unrelated boundaries to reduce commit count. Intermediate commits may keep the
active scenario `@wip`. Remove `@wip` only in the final functional commit that
makes that scenario GREEN, and never create an artificial closure-only commit.

The checked-in CI uses Java 17, Staging credentials, and a prewarmed Pixel
6/API 34 x86_64 emulator provided by `ReactiveCircus/android-emulator-runner`.
The AVD cache is generated manually through
`.github/workflows/avd-bootstrap.yml`; increment its `cache_version` input when
the emulator configuration changes. The delivery CI window is limited by the
policy (`maxInFlightCommits` is currently four). Do not manually poll runs.

## Skill routing

Load only the relevant skill:

- [android-clean-architecture](.agents/skills/android-clean-architecture/SKILL.md) for
  `domain/`, `data/`, `ui/`, or dependency-boundary changes.
- [android-bdd-tdd-process](.agents/skills/android-bdd-tdd-process/SKILL.md) for
  behavior, scenarios, step definitions, and tests.
- [android-testing-gates](.agents/skills/android-testing-gates/SKILL.md) before a PR,
  release, merge, or delivery-gate diagnosis.
- [android-api-client-governance](.agents/skills/android-api-client-governance/SKILL.md)
  for Retrofit, DTO, mapper, interceptor, or network changes.
- [android-hilt-governance](.agents/skills/android-hilt-governance/SKILL.md) for Hilt
  modules, bindings, ViewModels, or Hilt Android tests.
- [android-commit-governance](.agents/skills/android-commit-governance/SKILL.md) for
  commits, PRs, or history review.
- [android-doc-governance](.agents/skills/android-doc-governance/SKILL.md) for this
  contract, README, CLAUDE, skills, or documented commands.
- [android-compose-quality-governance](.agents/skills/android-compose-quality-governance/SKILL.md)
  for Compose screens, state collection, navigation, accessibility, or
  adaptive UI.
- [android-maintainability-governance](.agents/skills/android-maintainability-governance/SKILL.md)
  for code review, refactors, complexity, coupling, or oversized files.
- [android-static-analysis-governance](.agents/skills/android-static-analysis-governance/SKILL.md)
  for lint, architecture guards, forbidden patterns, or configured quality
  analyzers.
- [android-testability-governance](.agents/skills/android-testability-governance/SKILL.md)
  for deterministic test design, DI boundaries, state/effect coverage, or
  testability reviews.
- [android-us-delivery](.agents/skills/android-us-delivery/SKILL.md) for a complete
  User Story delivery lifecycle.
- [android-ai-development-workflow](.agents/skills/android-ai-development-workflow/SKILL.md)
  when coordinating agent batches and handoffs.

## Agent and human boundaries

Agents must keep delivery evidence and commits attributable to the exact
staged snapshot. Humans own workflow changes, unavailable credentials,
environment repair, and any requested `HUMAN_ONLY` action. A clean
documentation-only Gate `NONE` run is never sufficient proof that the Android
workflow is ready for enforcement.

Before handoff, report changed paths, checks run, checks blocked with their
precise reason, hook/enforcement state, CI SHA state, disabled analyzers, and
remaining human work. Do not include copied logs, tokens, or generated runtime
state in the report.
