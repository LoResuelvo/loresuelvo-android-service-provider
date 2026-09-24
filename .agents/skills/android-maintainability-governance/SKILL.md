---
name: android-maintainability-governance
description: Apply when reviewing, refactoring, or extending Android code whose size, complexity, coupling, or responsibility could make future changes unsafe.
---
# android-maintainability-governance

Load this skill for code review, refactors, large features, navigation changes,
ViewModel growth, or changes that increase cross-layer coupling.

## Do not load

Do not load it for a small isolated edit, documentation-only change, or a
mechanical formatting change with no design impact.

## Responsibility and size

For new or touched code, ordinary functions should normally take 0–3 inputs.
Review a signature with more than 4 and record an explicit cohesion exception
above 6. Review a class or ViewModel with more than 5 injected dependencies.
Review functions over 60 lines, classes, ViewModels, or navigation composition
roots over 250 lines, and files over 300 lines. Also review a function with
more than one independent business responsibility. These are manual review
triggers, not automated gates or reasons for mechanical splitting or
repository-wide cleanup.

When a trigger is crossed, either extract a cohesive collaborator or record a
concrete reason to keep the code together, the next extraction seam, and the
focused proof.

Compose parameters and DTO fields are context-specific exceptions. Preserve a
cohesive visual state/event API and required backend fields; do not hide
unrelated values or collaborators in a parameter/dependency bag to lower a
count.

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
