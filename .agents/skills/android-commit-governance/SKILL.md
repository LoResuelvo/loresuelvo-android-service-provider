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

## Atomicity

- One commit represents one complete logical boundary. A scenario ordinarily
  contains several such commits; batch granularity never sets their count.
- Each commit leaves the repository compilable and testable, includes the
  dependencies required by its boundary, and is independently reversible.
- A commit may cross files and layers when that is necessary for a coherent
  vertical boundary. Do not split by file, layer, or line count.
- Keep documentation/tooling changes separate from product behavior.
- Stage only the intended files; never include secrets, `local.properties`,
  generated outputs, or `.delivery/runtime/`.
- Preserve the exact staged snapshot used for delivery evidence.
- Intermediate commits may keep the active scenario `@wip`. Its removal
  belongs in the final functional commit that makes the scenario GREEN.
- Never create empty, tag-only, comment-only, or artificial closure commits.
- Do not combine unrelated boundaries to reduce the number of commits or leave
  a pushed commit dependent on files that are still uncommitted.

## Before committing

```bash
git status --short --branch
git diff --check
```

For an application change, use the policy-selected `delivery_prepare` MCP
operation after staging. For human CLI use, the matching `make
delivery-prepare` target is the documented entry point. A documentation-only
change still needs a valid Gate `NONE` receipt when agent evidence is enabled.

Prepare, commit, and—when authorized—push each atomic boundary before starting
the next boundary. Do not accumulate several local commits for one push. If
HEAD or staging changes externally, discard the stale receipt, inspect the
tree again, and prepare the new exact snapshot.

Do not execute hooks with `--no-verify`, and do not use
`DELIVERY_SKIP_CI_CHECK`. If preparation fails, fix the causal issue or
escalate; do not commit around it.

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
