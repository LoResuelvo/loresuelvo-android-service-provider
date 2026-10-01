---
name: android-commit-governance
description: Apply when creating a commit, preparing a pull request, or reviewing commit history under the repository commit contract.
---
# android-commit-governance

Load this skill when creating a commit, preparing a pull request, or reviewing
commit history. Do not load it for an uncommitted code edit unless the task
also crosses a commit boundary.

## Commit format

Use the repository contract exactly:

```text
<type>[<us-number>]: imperative English description
```

The bracketed value is the numeric User Story identifier from the issue title,
not the GitHub issue number. Resolve it before preparing the commit. For
example, `US-35` in GitHub issue `#7` uses `[35]`.

Examples:

```text
feat[35]: establish provider signup session
test[36]: cover provider profile validation
docs[37]: document coverage-zone behavior
```

Allowed types are `feat`, `fix`, `refactor`, `test`, `chore`, `docs`,
`build`, `ci`, `perf`, and `style`. The subject is concise, imperative,
English, and has no trailing period. Do not use `[US-33]`, a parenthesized
scope, or an invented story number.

## Batch boundaries

- The user owns the first commit with approved scenarios. Plan at most three
  implementation batches, one developer and one implementation commit each,
  in both guided and orchestrated modes.
- Each batch delivers a coherent end-to-end outcome with its production code
  and tests. It must compile, pass checks, and be independently reversible.
  Do not split it into mandatory commits per scenario, layer, or internal task.
- Before editing, record the batch result, dependencies, acceptance coverage,
  validation scope, owner, and English commit subject.
- The orchestrator reviews the completed batch directly; the same developer
  fixes findings. Run tests only at the commit-validation checkpoint, fixing
  and revalidating failures before commit. No mandatory intermediate RED/GREEN.
- Remove `@wip` for completed scenarios in the functional batch commit. Keep
  future scenarios tagged; do not create tag-only or empty closure commits.
- Include instrumented coverage in the final planned batch; execute it only
  in final US verification. Necessary final/CI repair commits remain with the
  responsible developer and do not create another implementation batch.
- Preserve unrelated changes and exclude secrets, `local.properties`, generated
  outputs, and `.delivery/runtime/`. Keep unrelated tooling/docs work separate.

## Before committing

Review the complete batch against its approved requirements and test coverage.
Stage all required dependencies together. Use `prepare_commit` for ordinary
batch preparation and `repair_ci` for a failed CI SHA. Avoid routine
per-scenario closure or redundant formal batch verification; `close_us` still
certifies the complete final scope.

Put `Delivery-Verify-US: <numeric ID>` in the last functional or coverage
commit's Git trailers. Its ID must match the subject. That push automatically
requests the full instrumented CI suite; no manual dispatch or empty trigger
commit is needed. Include the trailer again on final verification fixes so
the replacement SHA receives full CI verification.

Before preparing a partial staged diff, compare it with the remaining
unstaged changes. Delivery checks run in the checkout, so a passing gate can
depend on code that the commit omits. Include every required dependency in the
same boundary; if the proposed slices cannot stand alone, make one cohesive
commit instead of manufacturing smaller ones afterward.

```bash
git status --short --branch
git diff --check
```

For an application change, use the policy-selected `delivery_prepare` MCP
operation after staging. For human CLI use, the matching `make
delivery-prepare` target is the documented entry point. A documentation-only
change still needs a valid Gate `NONE` receipt when agent evidence is enabled.

Prepare, commit, and—when authorized—push each batch before starting
the next batch. Do not accumulate several local commits for one push. If
HEAD or staging changes externally, discard the stale receipt, inspect the
tree again, and prepare the new exact snapshot.

Do not execute hooks with `--no-verify`, and do not use
`DELIVERY_SKIP_CI_CHECK`. If preparation fails, fix the causal issue or
escalate; do not commit around it.

For a human CI repair that delegates verification to remote CI, run
`make delivery-context ARGS="--intent repair_ci --repairs-sha <failed-sha> [--us-id <id>]"`
after the final `git add`. Hooks are advisory for runtime evidence regardless
of `DELIVERY_REQUIRE_EVIDENCE`; ordinary human commits are not recorded as
`not_run`. Context is consumed only when post-commit binds an exact prepared
receipt. Agents still require passed preparation; use explicit delivery
operations for repair authorization and remote-CI delegation.

## Pull requests

Use an English title with the same
`<type>[<us-number>]: description` format. Describe:

- what changed and why;
- files or areas touched;
- validation and blocked prerequisites;
- residual risks or disabled analyzers.

Keep the PR atomic. Do not claim CI parity when Staging credentials or the
Pixel 6/API 34 emulator were unavailable.

## Review checklist

- Does the subject match
  `<type>[<us-number>]: imperative English description`?
- Is the bracketed value the User Story identifier from the issue title?
- Is the commit atomic and free of generated state or secrets?
- Was the staged snapshot prepared with the delivery contract?
- Are validation results and remaining risks known?
