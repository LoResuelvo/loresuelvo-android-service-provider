---
name: android-static-analysis-governance
description: Apply when configuring or reviewing automated Android quality checks, lint, architecture guards, forbidden patterns, or complexity analysis.
---
# android-static-analysis-governance

Load this skill when adding, changing, or reviewing lint/static-analysis
configuration, architecture checks, maintainability thresholds, or CI quality
signals.

## Do not load

Do not load it for a product-only change that does not alter or require a
static check. Use the maintainability and testability skills for code-design
and test-design decisions.

## Source of truth

- Inspect `build.gradle.kts`, `gradle/libs.versions.toml`, CI, and the delivery
  policy before choosing a command or claiming a check exists.
- Run analyzers that are actually configured. Do not invent Detekt, Ktlint,
  dependency-impact, or complexity commands that the repository does not have.
- A disabled or unavailable analyzer is `not_applicable` or a reported gap,
  never an implicit pass.
- Keep thresholds and gate selection in the repository's policy/tooling. Do
  not duplicate a second, conflicting policy inside a skill.

## Required quality signals

For application-layer changes, review the available signals for:

- compilation and Android Lint;
- forbidden imports across `domain`, `data`, and `ui`;
- DTOs leaking into domain or UI;
- direct UI-to-data construction;
- mutable global objects;
- direct logging or sensitive debug instrumentation;
- hardcoded user-visible strings;
- oversized files/functions and excessive branching when a configured
  analyzer can measure them.

The expected architecture checks are zero-match checks, not suggestions:

```bash
grep -RInE 'import (android\.|dagger|hilt|okhttp3|retrofit2|kotlinx\.serialization)' \
  app/src/main/java/*/*/domain/ || true
grep -RIn 'import .*\.data\.' app/src/main/java/*/*/ui/ || true
```

Adapt package paths to the repository. Inspect every match; do not hide a
violation by broadening an exclusion.

## Findings and suppressions

- Fix errors and architecture violations before closing the change.
- Treat warnings that affect correctness, security, lifecycle, or testability
  as actionable unless a current, specific exception explains them.
- Keep suppressions narrow, local, and justified. Never suppress an entire
  package or analyzer to make a gate green.
- Do not commit generated reports, IDE state, local properties, or copied
  analyzer output.

## Delivery integration

For the service provider, use the MCP delivery operation and the gate selected
by `.delivery/policy.v1.json`; do not calculate a gate manually or run an
arbitrary replacement command. If a quality analyzer is disabled by policy,
report that limitation in the handoff.

For the consumer, use the repository's configured `make`, Gradle, and CI
checks. Keep the same distinction between a check that ran green and a check
that is not configured.

## Review output

Report the checks that ran, their result, checks that were unavailable or
disabled, new suppressions, and residual quality gaps. A green compiler or
lint run alone is not evidence that architecture and maintainability checks
also ran.

