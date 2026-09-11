# Android Delivery Workflow

This directory contains the versioned delivery contract for the LoResuelvo Android service-provider app.

The delivery MCP lives in [`tools/delivery-mcp/`](../tools/delivery-mcp/). It
owns policy loading, file classification, gate selection, bounded command
execution, evidence formatting, jobs, and receipts. The server exposes exactly
these tools:

- `delivery_inspect` and `delivery_prepare` for staged-snapshot inspection and
  gate execution;
- `delivery_test` for focused TDD checks;
- `delivery_job_wait` and `delivery_job_cancel` for recoverable jobs;
- `delivery_ci_inspect` for one SHA's GitHub Actions status;
- `delivery_verify_head` for Gate D evidence on an existing HEAD;
- `delivery_finalize` for batch and User Story closure.

## Policy

policy.v1.json is the Android provider policy. It keeps the first release conservative:

- production Kotlin changes use the Android A gate
- domain Kotlin changes use the A gate
- Android data, dependency-injection, UI, resources, manifests, build infrastructure, and instrumented tests use Gate C
- JVM tests and BDD feature files use Gate 0
- delivery tooling uses Gate A plus the `delivery_unit` check
- workflow edits are HUMAN_ONLY
- unknown functional files fall back to Gate C

The dependency-impact, Cucumber-impact, and maintainability analyzers are
represented explicitly in the policy and disabled until Android-specific
adapters exist. Disabled analyzers return `not_applicable` and are neither
imported nor executed.

## Safe checks

The policy allowlist is intentionally exact. Delivery commands are selected by
check ID and run with bounded output and timeouts. Runtime jobs, logs, caches,
and generated evidence belong under `.delivery/runtime/` and are ignored by
Git. The required runtime is Node.js 24 LTS (`>=24 <25`).

Use the repository Make targets from the repository root so Node 24 is
discovered and validated consistently:

```bash
make delivery-install
make delivery-test
make delivery-smoke
make delivery-mcp
```

The CLI entry point is `tools/delivery-mcp/cli.mjs`; use the repository Make
targets (`make delivery-inspect ARGS="..."`, `make delivery-prepare
ARGS="..."`, `make delivery-context ARGS="..."`, `make delivery-ci
ARGS="--sha <commit-sha>"`, `make delivery-finalize ARGS="..."`,
`make delivery-verify-head ARGS="..."`, and the hook targets). The package scripts are
`test`, `smoke`, `mcp`, and `cli`; its package-local CLI is equivalent, for
example:

```bash
npm --prefix tools/delivery-mcp run cli -- inspect --intent prepare_commit
npm --prefix tools/delivery-mcp run cli -- test --mode unit
npm --prefix tools/delivery-mcp run cli -- ci --sha <commit-sha>
npm --prefix tools/delivery-mcp run cli -- hooks status
```

Recoverable jobs returned by prepare, test, verification, or finalization can
be controlled without an MCP client:

```bash
make delivery-job-wait ARGS="--job-id <job-id> --timeout-ms 60000"
make delivery-job-cancel ARGS="--job-id <job-id> --reason 'No longer needed'"
make delivery-repair-recover ARGS="--target-sha <failed-sha> --expected-authorization-commit-sha <never-remote-sha>"
```

`make delivery-test ARGS="..."` delegates to that CLI test command for
focused TDD modes. The policy's delivery-tooling Gate A check is the complete
unit suite `npm --prefix tools/delivery-mcp test`.

The repository's canonical GNU Make entry point is `Makefile`. Android
targets (`build`, `lint`, `test`, `e2e`, `clean`, and `devices`) delegate
toolchain setup and command execution through
`scripts/with-android-env.sh`. Delivery targets, repository Git hooks, and
Codex delivery entry points similarly delegate Node 24 discovery and
validation through `scripts/with-node-24.sh`; they do not require an Android
emulator.

The safe Android checks delegated to `make` are exactly:

```text
make test FLAVOR=Dev
make lint FLAVOR=Dev
make build FLAVOR=Dev
make e2e FLAVOR=Dev
make test FLAVOR=Staging
make lint FLAVOR=Staging
make build FLAVOR=Staging
make e2e FLAVOR=Staging
```

The Android wrapper discovers Java 17 and the Android SDK from explicit
environment values, `local.properties`, standard locations, or a
repository-sibling `.toolchains` directory. The instrumented check still
needs a connected device or emulator.

## Commit format

Delivery work uses the canonical imperative English format
`<type>[<us-number>]: description`. The bracketed value is the numeric User
Story identifier from the issue title, not the GitHub issue number. For
example, work for `US-35` in issue `#7` uses
`feat[35]: establish provider signup session`. Parenthesized scopes and
spellings such as `[US-35]` are rejected.
The allowed types are `feat`, `fix`, `refactor`, `test`, `chore`, `docs`,
`build`, `ci`, `perf`, and `style`.

## CI and Android topology

The checked-in CI workflow runs the delivery package tests and smoke check in
parallel with Java 17, Staging lint/JVM/build checks, and instrumented tests on
a prewarmed Pixel 6/API 34 x86_64 emulator. The checked-in
`.github/workflows/avd-bootstrap.yml` workflow regenerates the AVD cache when
needed. Gate C and Gate D use Dev
instrumented tests; Gate R reproduces the Staging checks and requires the
failed CI SHA plus Staging credentials. Do not substitute Dev for Gate R.

Gate D also requires no `@wip` tags in the declared feature scope and a green
post-push CI result. Gate R is a single-use repair path. If no emulator,
credentials, or CI provider access is available, return a blocked diagnostic;
do not silently skip or weaken the check.

CI repository resolution is deterministic: an explicit repository argument
takes precedence, then `GITHUB_REPOSITORY`, then a GitHub `origin` remote. A
missing repository or provider failure is reported as `provider_error`, never
as a green result. The policy delivery window limits concurrent commits and
in-flight work; a full window is a retryable blocked condition. Delivery only
inspects the requested SHA and does not manually poll unrelated runs.

`delivery_verify_head` validates current HEAD against its ledger entry,
receipt digest, parent, and tree. `delivery_finalize` records a batch or User
Story result only after the selected gate and post-push checks are complete;
finalization cannot turn missing or stale evidence into a pass.

## Evidence and recovery

Receipts are cryptographically bound to HEAD, the exact staged snapshot,
policy, intent, and scope. A changed staged file invalidates the receipt.
Long Gate C/D/R, HEAD verification, and bounded CI waits may return a `jobId`;
await it with `delivery_job_wait`; `delivery_job_cancel` is cooperative and
leaves a recoverable cancelled/failed job record, so a caller may retry with a
fresh snapshot. Do not busy-poll. CI repair starts with
`delivery_ci_inspect` and has two auditable paths:

- Agents call `delivery_prepare` with intent `repair_ci` and the exact
  `repairsSha`; Gate R issues a single-use repair receipt.
- Humans may inspect or clear delivery context with `make delivery-context`.
  Hooks do not apply context to commit-message validation; post-commit consumes
  it only when an exact prepared receipt is successfully bound. Use the
  explicit `delivery_prepare`/`delivery_inspect` operations for a repair
  receipt and remote-CI delegation.

Never use `--no-verify` or `DELIVERY_SKIP_CI_CHECK`. A cancelled CI run is
resolved only when a reachable descendant has passed CI; cancellation without
that green descendant remains a repair incident.

The workflow remains in shadow mode: `DELIVERY_REQUIRE_EVIDENCE` is disabled,
and hooks are not installed automatically. Delivery hooks are advisory for
runtime state and never block a human commit or push; only `commit-msg` may
block, and only for stateless deterministic commit-message format. A valid
prepared receipt may be bound by `post-commit`; ordinary human commits and
invalid/missing receipts are never recorded as `not_run`. An autonomous agent
must still prepare its exact staged snapshot. Install hooks only after the Node
contracts, Android smoke matrix, and real-repository checks pass.

If a remote rejects a repair push after authorization, recover the stale
never-remote binding explicitly with `delivery-repair-recover`. The operation
requires both the failed target SHA and the expected authorization commit SHA,
checks the target remains the active failed incident, confirms the authorization
commit is absent from remote Git and CI, and appends an audit event without
deleting prior evidence. Ambiguous Git, ledger, lock, or provider state is
refused closed. Repeating the same request is idempotent.

See [`AGENTS.md`](../AGENTS.md) for the repository contract and
[`policy.v1.json`](policy.v1.json) plus
[`policy.schema.json`](schemas/policy.schema.json) for the versioned rules.

## Schemas

The schemas directory contains the versioned JSON Schemas consumed by the MCP.
Keep policy and schema changes together, and add or update focused tests in
`tools/delivery-mcp/test/` when the contract changes.
