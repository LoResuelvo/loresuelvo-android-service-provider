# Quality checklist

- Each changed symbol has a clear purpose and a bounded responsibility.
- Domain, data, UI, and delivery concerns remain behind their proper
  boundaries.
- ViewModels expose complete immutable states and typed events.
- Public APIs are minimal; no speculative setters, exports, or extension
  points were added.
- DTO mapping and error translation are tested at the boundary.
- JVM tests cover critical logic and regression branches.
- Instrumented tests cover only Activity, navigation, and device behavior.
- A BDD scenario is removed from `@wip` only when its observable behavior is
  GREEN.
- No duplicate abstraction was created before it had an actual caller.
- `git diff --check` is clean and generated delivery state is excluded.
