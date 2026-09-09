---
name: android-bdd-tdd-process
description: Apply when adding observable behavior, changing a provider journey, or writing BDD scenarios and tests; use testing gates for delivery validation.
---
# android-bdd-tdd-process

Load this skill when adding behavior, changing a provider journey, or adding
a BDD scenario and its tests.

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

1. Write or update the Gherkin scenario before production code.
2. Add the smallest step definitions needed to fail for the right reason.
3. Add or update JVM unit tests for the behavior and error branches.
4. Implement the smallest production change.
5. Refactor while the focused tests remain green.
6. Add an instrumented UI test when the change crosses Activity, navigation,
   or real Android boundaries.

Each scenario has a stable ID such as `01-PWB`, one action per step, and one
`When`. Keep scenario state in a world/context, never in mutable globals. Use
deterministic fakes for use cases and ViewModels; use mocks only for
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

## Commands

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

## Anti-patterns

- Testing JVM behavior on a device or against a real backend.
- Asserting localized strings in JVM BDD tests.
- Sharing mutable state between scenarios.
- Adding a scenario without a deterministic fake or test backend.
- Silently replacing a blocked instrumented run with a JVM run.
