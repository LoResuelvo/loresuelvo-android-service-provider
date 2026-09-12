---
name: android-bdd-tdd-process
description: Apply when adding observable behavior, changing a provider journey, or writing BDD scenarios and tests; use testing gates for delivery validation.
---
# android-bdd-tdd-process

Load this skill when adding behavior, changing a provider journey, or adding
a BDD scenario and its tests. The canonical maintainability and test
architecture convention is in [AGENTS.md](../../../AGENTS.md).

## Do not load

Do not load it for a documentation-only change, delivery-policy change, or
refactor with no observable behavior. Use `android-testing-gates` for release
and merge validation.

## Test layers and terminology

- Gherkin features live under `app/src/test/resources/features/`.
- Cucumber glue and JVM runners live under
  `app/src/test/java/com/loresuelvo/serviceprovider/bdd/`.
- JVM unit tests live under `app/src/test/` and require no device.
- Instrumented UI tests live under `app/src/androidTest/` and require a device
  or emulator. Do not call these Cucumber scenarios.

The current journey is `features/auth/provider-welcome.feature`, with glue in
`bdd/auth/welcome/` and the `WelcomeCucumberTest` runner.

## Required loop

1. Write every Gherkin acceptance scenario before production code, present the
   scenarios for functional approval, and mark approved pending scenarios
   `@wip`.
2. Add the smallest step definitions needed to fail for the right reason.
3. Add or update JVM unit tests for the behavior and error branches.
4. Implement the smallest production change.
5. Refactor while the focused tests remain green.
6. Add an instrumented UI test when the change crosses Activity, navigation,
   or real Android boundaries.

After approval, scenario wording is immutable: do not rewrite, remove, or
weaken `Given`, `When`, or `Then` without renewed functional approval. Complete
one scenario in GREEN before starting the next, including inside a
`SCENARIO_GROUP`.

Use `delivery_test` for the interactive RED/GREEN loop. A RED result never
authorizes a commit. A scenario may advance through several committed internal
boundaries while its outer Gherkin remains `@wip`; each boundary must be
coherent, compilable, and GREEN at its own test layer. On the final functional
boundary, make the complete scenario GREEN, remove `@wip`, stage that exact
change, and call `delivery_prepare` with `close_scenario`.

Each scenario has a stable ID such as `01-PWB`, one action per step, and one
`When`. Keep scenario state in a world/context, never in mutable globals.
Steps over 20 lines and worlds over 250 lines need a cohesion review. `Given`
arranges one meaningful prerequisite, `When` invokes the real owned use case
or ViewModel, and `Then` observes that same production instance or its
observable effect. Fake external ports; use mocks only for meaningful
interaction assertions. Never use `Thread.sleep`; use coroutine schedulers or
Compose idling.

Existing Gherkin files use the feature's declared language for product-facing
steps. Keep code, glue, test names, diagnostics, and supporting documentation
in English.

## Test boundaries

| Behavior | Location | Main tools |
| --- | --- | --- |
| Domain/use case | `app/src/test/.../domain/` | JUnit4, fakes, MockK |
| Repository/HTTP | `app/src/test/.../data/api/` | MockWebServer, OkHttp |
| ViewModel | `app/src/test/.../ui/` | `runTest`, Turbine |
| Composable without Activity | `app/src/test/.../ui/` | Robolectric/Compose |
| Activity/navigation/device | `app/src/androidTest/` | Compose test, Espresso, Hilt |

Assert typed outcomes and observable effects in JVM tests, not localized UI
strings. Resolve localized strings through the Activity in instrumented tests
when locale-dependent UI is under test.

## Human or focused diagnostic commands

Focused JVM checks:

```bash
./gradlew :app:testDevDebugUnitTest --tests '*WelcomeViewModelTest*'
./gradlew :app:testDevDebugUnitTest --tests '*WelcomeCucumberTest'
```

Full provider validation:

```bash
make test FLAVOR=Dev
make e2e FLAVOR=Dev
make build FLAVOR=Dev
```

`make e2e` runs `connectedDevDebugAndroidTest` and needs an available device.
Delivery Gate 0 and Gate B intentionally run the complete Dev JVM task because
there is no reliable feature-file-to-runner command.

Agents use these raw commands only for focused diagnosis when the processed
Delivery MCP result is insufficient; they are not the ordinary TDD loop.

## Anti-patterns

- Testing JVM behavior on a device or against a real backend.
- Asserting localized strings in JVM BDD tests.
- Sharing mutable state between scenarios.
- Adding a scenario without a deterministic fake or test backend.
- Empty prerequisites, disconnected assertion-only mocks, or assertions that
  merely repeat fixture constants.
- Silently replacing a blocked instrumented run with a JVM run.
