---
name: android-bdd-tdd-process
description: Apply when adding observable behavior, changing a provider journey, or writing BDD scenarios and tests; use testing gates for delivery validation.
---
# android-bdd-tdd-process

Load this skill when adding behavior, changing a provider journey, or adding
a BDD scenario and its tests. Use `android-testability-governance` for
test-layer ownership and `android-maintainability-governance` for size or
responsibility reviews.

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

One provider example is `features/auth/provider-welcome.feature`, with glue in
`bdd/auth/welcome/` and the `WelcomeCucumberTest` runner.

## Required outside-in loop

1. Write Gherkin acceptance scenarios before production code, present them
   for functional approval, and mark approved pending scenarios `@wip`.
2. Add the smallest step definitions and test doubles that exercise the real
   owned use case or ViewModel. Observe a relevant RED through a runnable
   focused test before production code. With `@wip` present, Cucumber skips
   that scenario. `delivery_test(mode="scenario", featureFile=<feature path>)`
   selects a feature runner when available and cannot by itself prove its RED. To observe
   Cucumber RED, temporarily remove `@wip` in the working tree and restore it
   before an intermediate commit. A coherent glue-only boundary may be
   committed after its staged `delivery_prepare(intent="prepare_commit")`
   passes Gate 0.
3. Add focused JVM tests for behavior and error branches; implement the
   smallest production change. Use a stateless Composable, pure use case,
   repository adapter, or ViewModel as needed. Test each relevant boundary
   with Robolectric/Compose, JUnit4, MockWebServer, or Turbine.
4. Refactor while focused tests stay GREEN. Add an instrumented UI test when
   the change crosses Activity, navigation, or real Android boundaries.
5. For the final functional boundary, remove `@wip` in the working tree so
   the active Cucumber scenario runs. Verify focused GREEN
   with `delivery_test(mode="scenario", featureFile=<feature path>)`. Confirm
   a runner includes the feature and inspect the Cucumber report or test log
   to see that the active scenario executed; a green suite with a filtered or
   missing scenario is insufficient. Stage the functional change and tag
   removal together, then call `delivery_prepare(intent="close_scenario")`.
   Commit only after `status: passed`; push when authorized.

After approval, scenario wording is immutable: do not rewrite, remove, or
weaken `Given`, `When`, or `Then` without renewed functional approval. Complete
one scenario in GREEN before starting the next, including inside a
`SCENARIO_GROUP`.

Use `delivery_test(mode="unit", testFiles=[...])` for exact focused JVM classes
when available. Use `delivery_test` for RED/GREEN. A RED result never
authorizes a commit. A scenario may advance through one or several committed
internal boundaries while its outer Gherkin remains `@wip`; each boundary
must be coherent, compilable, independently reversible, and GREEN at its own
test layer. Focused TDD results do not replace the staged policy gate. Use
the numeric User Story ID, not the scenario ID, in commit messages.

Each scenario has a stable ID such as `01-PWB`, one action per step, and one
`When`. Keep scenario state in a world/context, never in mutable globals.
The world owns and closes its test scopes, dispatchers, persistence, and other
resources during setup and teardown.
Steps over 20 lines and worlds over 250 lines need a cohesion review. `Given`
arranges one meaningful prerequisite, `When` invokes the real owned use case
or ViewModel, and `Then` observes that same production instance or its
observable effect. Fake external ports; use mocks only for meaningful
interaction assertions. Never use `Thread.sleep`; use coroutine schedulers or
Compose idling.

Existing Gherkin files use the feature's declared language for product-facing
steps. Keep code, glue, test names, diagnostics, and supporting documentation
in English.

Test-layer ownership is defined in `android-testability-governance`. Assert
typed outcomes and observable effects in JVM tests, not localized UI strings;
resolve locale-dependent strings through the Activity in instrumented tests.
Use focused Gradle diagnostics through the Android wrapper only when processed
Delivery output is insufficient; see `.delivery/README.md` for the test
topology. Gate 0 runs the complete Dev JVM task. Gate B may select an isolated
feature and affected JVM classes; inspect the gate's actual check IDs and scope.
