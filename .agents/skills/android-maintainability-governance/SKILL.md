---
name: android-maintainability-governance
description: Apply when reviewing, refactoring, or extending Android code whose size, complexity, coupling, or responsibility could make future changes unsafe.
---
# android-maintainability-governance

Load this skill for code review, refactors, large features, navigation changes,
ViewModel growth, or changes that increase cross-layer coupling. The canonical
convention is in [AGENTS.md](../../../AGENTS.md); this skill applies it during
review.

## Do not load

Do not load it for a small isolated edit, documentation-only change, or a
mechanical formatting change with no design impact.

## Responsibility and size

Use these as review triggers for new or touched code, not as a reason to split
cohesive code mechanically or demand repository-wide cleanup:

- ordinary functions normally take 0–3 inputs; review above 4 and document a
  cohesion exception above 6;
- injected dependencies above 5;
- production source file over 300 lines;
- production class or ViewModel over 250 lines;
- function or composable over 60 lines;
- navigation composition root over 250 lines;
- a function with more than one independent business responsibility.

When a trigger is crossed, either extract a cohesive collaborator or record a
concrete reason to keep the code together and the next extraction seam.

Compose parameters and DTO fields are context-specific exceptions. Preserve a
cohesive visual state/event API and required backend fields; do not hide
unrelated values or collaborators in a parameter/dependency bag to lower a
count.

## Boundaries

- Keep domain/use-case code independent of Android, UI, HTTP, serialization,
  and Hilt. Enforce this with import checks.
- Keep DTOs, mappers, repositories, SDK adapters, and storage in `data/`.
- Keep UI dependent on ports/use cases, not concrete data adapters.
- Prefer one responsibility per file and one public operation per use case.
- Do not introduce mutable global objects to avoid wiring a dependency.

## Complexity and coupling

- Prefer early returns and typed outcomes over deeply nested branching.
- Extract repeated transformations into a named pure function or mapper.
- Keep ViewModels as state/effect orchestrators; move business workflows to
  use cases and platform work to injected adapters. Keep constructor
  dependencies explicit rather than hiding broad ownership in a dependency
  bag.
- Avoid passing framework types, DTOs, or large mutable state graphs across
  boundaries.
- Review fan-out before changing a high-use symbol. Update its focused tests
  and inspect callers before changing its contract.

## Refactor safety

Before extracting code, identify the behavior and callers it owns. Preserve
the public contract, move tests with the responsibility, and run the narrowest
relevant checks before broader validation. Do not mix a structural refactor
with an unrelated product behavior change.

If the repository's maintainability analyzer is disabled, report the review
triggers and residual exceptions explicitly. A disabled analyzer is not proof
that the code passed maintainability review.

## Review output

For a material maintainability change, report:

1. the responsibility or coupling that changed;
2. the extracted or intentionally retained seams;
3. focused tests and boundary checks run;
4. remaining large or high-coupling areas.
