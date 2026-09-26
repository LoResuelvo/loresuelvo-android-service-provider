---
name: android-testing-gates
description: Apply before pull requests, releases, merges to main, or when diagnosing delivery-gate execution; use BDD/TDD for small behavior iterations.
---
# android-testing-gates

Load this skill before a pull request, release, merge to `main`, or when
diagnosing delivery-gate execution. Use `android-bdd-tdd-process` for a small
local behavior iteration.

## Canonical delivery execution

Agents use `delivery_test` for focused TDD and `delivery_prepare` for a staged
commit boundary. The policy in `.delivery/policy.v1.json` selects the gate and
exact allowlisted commands; do not calculate a gate manually or pass arbitrary
shell text. Long checks return a job ID and are awaited with bounded
`delivery_job_wait`; use `delivery_job_cancel` only for an explicit,
auditable cancellation.

The test topology and gate table live in `.delivery/README.md`; the
deterministic classification and check catalog live in
`.delivery/policy.v1.json`.

Gate C/D instrumented tests cannot be silently skipped. If no device or
emulator is available, return a blocked diagnostic. Gate R needs real Staging
credentials and the failed CI `repairsSha`; Dev is not CI parity.

`close_scenario` retains Gate C for Android impact unless the enabled analyzer
proves an isolated production feature with scoped device coverage; it does not
close future scenarios in the same feature. Gate D is for `close_batch` and
`close_us` with completed feature scope. Only actual tag lines count as `@wip`.

Use `delivery_test(mode="unit", testFiles=[...])` for exact Kotlin JVM test
classes. Keep package paths aligned with their class names. Scenario mode may
run a unique runner for its feature; `scenarioName` does not filter individual
scenarios. Affected mode focuses only when every changed path is a runnable JVM
test class. Other cases run the complete Dev JVM task. Check the returned
`selection` before treating a result as focused proof. Focused TDD does not
authorize skipping a gate. Serialize Gradle and device checks.

For low-risk scenario closure, Gate B may run an isolated feature runner and
affected JVM classes using HEAD and staged-tree consumer analysis. Inspect its
check IDs and impact reasons; uncertain test-only scope falls back to full JVM.
The Android feature-impact analyzer can select B for production and companion
test changes proven to belong to one covered feature in both Git trees. It requires
lint, scoped JVM/device checks and the device prerequisite. Shared or unsupported
Android impact retains C. See `.delivery/README.md` for ownership and fallback rules. Gate C/D/R checks and prerequisites remain intact.

The current CI workflow uses Java 17 and a prewarmed Pixel 6/API 34 x86_64
emulator. Regenerate its cache through the checked-in
`.github/workflows/avd-bootstrap.yml` workflow and increment its
`cache_version` input when the emulator configuration changes. Do not document
or assume a device-management target.

## Shadow rollout and evidence

Keep `DELIVERY_REQUIRE_EVIDENCE` disabled while the delivery unit tests,
smoke, classification matrix, and representative inspections are being
validated. Receipts are cryptographically bound to HEAD, the staged snapshot,
policy, intent, and scope; a changed snapshot invalidates them. Hooks check
format and provide advisory evidence/CI diagnostics but never run test suites.

## CI failure repair

Stop ordinary pushes when CI fails. Inspect the exact SHA with
`delivery_ci_inspect`. Its short excerpt is a lead, not a complete log: if
inconclusive, open that failed run/job once and inspect its terminal operation
and causal output. Emulator boot or ADB warnings alone do not establish an
infrastructure failure; an Espresso assertion identifies an application/test
failure. Distinguish runner provisioning from workflow failures before
escalating, and preserve the diagnosis in handoffs.

Make one atomic fix. Agents call `delivery_prepare(intent="repair_ci",
repairsSha=<failed SHA>)` for Gate R's single-use receipt; the human path is
documented in `.delivery/README.md`. Agents still require passed preparation.
Never use `--no-verify` or `DELIVERY_SKIP_CI_CHECK`. A cancelled run is
resolved only by a reachable descendant with passed CI. Workflow files and
workflow-job failures are `HUMAN_ONLY`; escalate runner provisioning failures
that require environment repair.
