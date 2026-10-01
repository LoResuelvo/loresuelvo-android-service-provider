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

## Batch implementation and acceptance

1. Define all Gherkin before production work, with explicit functional
   approval and `@wip` for pending scenarios. The user creates the initial
   scenario commit; agents do not take over that commit.
2. Implement the assigned batch end to end with meaningful step definitions,
   test doubles, and JVM tests of real use cases or ViewModels. Scenarios
   within the batch may be implemented together. Mandatory pre-implementation
   RED and intermediate test executions are not required.
3. The orchestrator reviews requirements, production code, and tests directly.
   The retained developer fixes findings before commit validation.
4. At the commit checkpoint, remove `@wip` for completed scenarios, stage their
   functional implementation and tests, and run `delivery_prepare` with
   `prepare_commit`. Confirm that the runner executed the completed scenarios;
   skipped or missing scenarios are not proof. Keep later-batch scenarios
   tagged. Do not create tag-only commits or routine `close_scenario` receipts.
5. Use `delivery_test` at this checkpoint for focused evidence or diagnosis
   only when needed beyond the selected gate. Inspect its actual selection:
   scenario mode selects a feature runner, not one scenario by name. Fix
   failures and obtain a passed exact-snapshot gate before committing.
6. Include required Activity/navigation/lifecycle test code in the final
   planned batch. Execute instrumented tests only in final User Story
   verification, after all batches pass review and device-free checks.

Approved scenario wording is immutable without renewed functional approval.
Batching changes execution cadence, not required acceptance coverage. Preserve
meaningful tests for error branches, security, and regression behavior; use
existing JUnit4, Robolectric/Compose, MockWebServer, and Turbine as appropriate.
Test-layer ownership remains in `android-testability-governance`.

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
