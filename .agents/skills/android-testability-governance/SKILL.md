---
name: android-testability-governance
description: Apply when designing tests, changing DI or state boundaries, or reviewing whether Android behavior can be tested deterministically without a real backend or device.
---
# android-testability-governance

Load this skill when adding behavior, changing ViewModel state, changing Hilt
bindings, adding a repository, or reviewing test coverage and determinism.
The canonical maintainability and test-architecture convention is in
[AGENTS.md](../../../AGENTS.md).

## Do not load

Do not load it for documentation-only changes or a delivery-gate execution
that does not alter test design. Use the repository testing-gates skill for
the final delivery gate.

## Test boundary

| Behavior | Preferred test | Boundary |
| --- | --- | --- |
| Entity or use case | JVM unit test | No Android, Hilt, or network |
| Repository, mapper, interceptor | JVM + MockWebServer | Assert wire contract and typed failures |
| ViewModel state/effects | JVM + `runTest`/Turbine | Inject fakes and a test dispatcher |
| Stateless composable | JVM Compose/Robolectric | Assert semantics and state branches |
| Activity, NavHost, Hilt, permissions | Instrumented | Deterministic fake modules |

Do not move a fast JVM test to a device merely because the feature has a UI.
Add an instrumented test when the behavior crosses an Activity, navigation,
permission, lifecycle, or real Android service boundary.

## Determinism

- Never use a real backend, Auth0, WebSocket, clock, random source, or file
  system in a unit or Cucumber test unless that dependency is the subject of
  the test.
- Exercise real owned use cases and ViewModels; use fakes for external ports
  and mocks only for meaningful interaction assertions. Do not let a mock
  manufacture the assertion without production behavior connecting it.
- Control coroutine execution with `runTest`, a test dispatcher, and virtual
  time. Give each test explicit ownership of its coroutine scope, dispatcher,
  persistent state, and teardown. Do not use `Thread.sleep` or arbitrary
  polling.
- Test success, loading, empty, network, server, unauthorized, cancellation,
  retry, and duplicate-submit branches where they are observable.
- Close MockWebServer, WebSockets, players, recorders, and other resources in
  teardown.
- Do not use empty prerequisites, fixture-constant assertions, or disconnected
  assertion-only mocks as behavioral proof.

## Hilt and session tests

- An instrumented test that launches `MainActivity` declares
  `@HiltAndroidTest`, `HiltAndroidRule` at order 0, then the Compose rule.
- Call `hiltRule.inject()` before launching or asserting the Activity.
- Use `@TestInstallIn` for suite-wide fakes and `@UninstallModules` for a
  class-local replacement.
- When mutating a session observed by production navigation, obtain the same
  SingletonComponent binding through an `@EntryPoint`; do not construct a
  second session store locally.

## Acceptance and UI assertions

Keep behavioral acceptance scenarios on the JVM when they can exercise ports,
use cases, and ViewModels directly. Assert typed outcomes and observable
effects there. Assert localized strings, accessibility semantics, navigation,
and device behavior in Compose/instrumented tests through the Activity's
resources.

## Review

For a new behavior, document which layer proves the contract, which fake owns
the external dependency, and which failure branches remain uncovered. The
provider's final commands and receipts remain governed by Delivery MCP and
`.delivery/policy.v1.json`.
