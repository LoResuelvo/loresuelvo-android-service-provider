# android-commit-governance

Load this skill when creating a commit, preparing a pull request, or reviewing
commit history. Do not load it for an uncommitted code edit unless the task
also crosses a commit boundary.

## Commit format

Use the repository contract exactly:

```text
<type>[33]: imperative English description
```

Examples:

```text
feat[33]: add provider category loading
test[33]: cover WelcomeViewModel failure states
docs[33]: document Android delivery gates
```

Allowed types are `feat`, `fix`, `refactor`, `test`, `chore`, `docs`,
`build`, `ci`, `perf`, and `style`. The subject is concise, imperative,
English, and has no trailing period. Do not use `[US-33]`, a parenthesized
scope, or an invented story number.

## Atomicity

- One commit represents one logical change.
- Keep documentation/tooling changes separate from product behavior.
- Stage only the intended files; never include secrets, `local.properties`,
  generated outputs, or `.delivery/runtime/`.
- Preserve the exact staged snapshot used for delivery evidence.

## Before committing

```bash
git status --short --branch
git diff --check
```

For an application change, use the policy-selected `delivery_prepare` MCP
operation after staging. For human CLI use, the matching `make
delivery-prepare` target is the documented entry point. A documentation-only
change still needs a valid Gate `NONE` receipt when agent evidence is enabled.

Do not execute hooks with `--no-verify`, and do not use
`DELIVERY_SKIP_CI_CHECK`. If preparation fails, fix the causal issue or
escalate; do not commit around it.

## Pull requests

Use an English title with the same `<type>[33]: description` format. Describe:

- what changed and why;
- files or areas touched;
- validation and blocked prerequisites;
- residual risks or disabled analyzers.

Keep the PR atomic. Do not claim CI parity when Staging credentials or the
Pixel 6/API 34 emulator were unavailable.

## Review checklist

- Does the subject match `<type>[33]: imperative English description`?
- Is the change tied to the agreed numeric identifier 33?
- Is the commit atomic and free of generated state or secrets?
- Was the staged snapshot prepared with the delivery contract?
- Are validation results and remaining risks known?
