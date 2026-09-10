---
name: android-compose-quality-governance
description: Apply when adding or reviewing Compose screens, routes, state collection, navigation, accessibility, or adaptive UI behavior.
---
# android-compose-quality-governance

Load this skill when a change touches a composable, Compose route, navigation
graph, UI state collection, one-shot UI effect, or device-size behavior.

## Do not load

Do not load it for domain-only, API-only, documentation-only, or delivery
tooling changes that do not change the Compose boundary.

## Route and screen boundary

- A Route owns ViewModel acquisition, navigation callbacks, and mapping from
  UI effects to navigation.
- A Screen is stateless: it receives immutable state and callbacks, and does
  not construct repositories, use cases, or Android services.
- Keep reusable visual pieces below the Screen. Do not turn the navigation
  composition root into a collection of unrelated screens.
- ViewModels expose immutable `StateFlow<UiState>` and typed events. Do not
  mutate Compose state from a repository or data adapter.

## Lifecycle and effects

- Collect production flows with `collectAsStateWithLifecycle()`. Use plain
  `collectAsState()` only in previews or tests, or document why lifecycle-aware
  collection is unavailable.
- Declare `lifecycle-runtime-compose` explicitly in the version catalog and
  module when using the lifecycle-aware collector; do not rely on a transitive
  dependency.
- Collect one-shot events with a lifecycle-aware `LaunchedEffect`; do not put
  navigation, snackbars, permission launches, or activity results in the
  composable body.
- Use stable keys for `LaunchedEffect` and `DisposableEffect`. Cancel or clean
  up listeners, WebSockets, players, and recorders in the matching effect.
- Use `rememberSaveable` for local transient UI state and `SavedStateHandle`
  for state that must survive recreation. Route arguments must be restorable.
- Disable duplicate submissions while an operation is in flight and expose
  loading, empty, success, and typed failure states explicitly.

## Navigation

- Keep route arguments typed and encoded at the navigation boundary.
- Define the authenticated start destination from the session contract; do not
  navigate to a placeholder and repair it later with ad-hoc effects.
- Specify back behavior, `popUpTo`, `launchSingleTop`, deep links, and logout
  behavior when adding a route.
- Navigation tests belong in instrumented tests when they cross an Activity or
  real NavHost; a stateless Screen can be tested on the JVM.

## Accessibility and adaptation

- Give interactive controls a localized accessible label and meaningful
  semantics. Decorative images use `contentDescription = null`.
- Keep visible text in Android resources. Do not use content descriptions as a
  substitute for visible labels or test IDs.
- Use Material tokens and window-aware layouts. Review compact, medium, and
  expanded widths, landscape, font scale, RTL, dark theme, and touch targets.
- Do not encode important information only through color, animation, or
  position. Motion must not block the user and should respect reduced-motion
  behavior when the product exposes that preference.

## Review and tests

For a UI change, verify the affected state branches, recomposition behavior,
loading/double-submit behavior, accessibility semantics, localization, and
navigation/back behavior. Add a focused JVM Compose test for a stateless
component and an instrumented test for Activity, Hilt, navigation, permission,
or device-bound behavior. Use the repository's existing delivery/testing
workflow for the final gate; this skill does not replace it.
