# android-testing-gates

Load this skill before a pull request, release, merge to `main`, or when
diagnosing delivery-gate execution. Use `android-bdd-tdd-process` for a small
local behavior iteration.

## Test layers

- `make test FLAVOR=Dev`: JVM unit tests and Cucumber JVM; no device.
- `make lint FLAVOR=Dev`: Android Lint.
- `make build FLAVOR=Dev`: debug APK compilation and generated-code checks.
- `make e2e FLAVOR=Dev`: instrumented UI tests under `androidTest`; a device
  or emulator is required.
- `make test-all-once FLAVOR=Dev`: JVM plus instrumented tests.
- `make ci FLAVOR=Dev`: build, lint, JVM, and instrumented checks.

## Canonical delivery execution

Agents use `delivery_test` for focused TDD and `delivery_prepare` for a staged
commit boundary. The policy in `.delivery/policy.v1.json` selects the gate and
exact allowlisted commands; do not calculate a gate manually or pass arbitrary
shell text. Long checks return a job ID and are awaited with bounded
`delivery_job_wait`; use `delivery_job_cancel` only for an explicit,
auditable cancellation.

The isolated delivery package itself is checked with:

```bash
npm --prefix tools/delivery-mcp test
```

## Required gates

| Gate | Checks |
| --- | --- |
| `NONE` | No executable checks for documentation-only or empty diffs |
| `0` | Complete Dev JVM test task for feature/glue compatibility |
| `A` | Dev JVM tests; delivery tooling also runs delivery unit tests |
| `B` | Complete Dev JVM test task when closing one BDD scenario |
| `C` | Dev lint, JVM tests, build, and instrumented UI |
| `D` | No `@wip`, all Gate C checks, and post-push CI green |
| `R` | Delivery tests plus Staging lint, JVM tests, build, instrumented UI, and post-push CI green |

Gate C/D instrumented tests cannot be silently skipped. If no device or
emulator is available, return a blocked diagnostic. Gate R needs real Staging
credentials and the failed CI `repairsSha`; Dev is not CI parity.

## Focused and full commands

```bash
./gradlew :app:testDevDebugUnitTest --tests '*WelcomeViewModelTest*'
make lint FLAVOR=Dev
make test FLAVOR=Dev
make build FLAVOR=Dev
make e2e FLAVOR=Dev
```

The current CI workflow uses Java 17 and a Pixel 6/API 34 x86_64 emulator.
There is no checked-in AVD bootstrap workflow or prewarmed snapshot. Do not
document or assume a device-management target.

## Shadow rollout and evidence

Keep `DELIVERY_REQUIRE_EVIDENCE` disabled while the delivery unit tests,
smoke, classification matrix, and representative inspections are being
validated. Receipts are cryptographically bound to HEAD, the staged snapshot,
policy, intent, and scope; a changed snapshot invalidates them. Hooks check
format, receipts, and the CI window but never run test suites.

## CI failure repair

Stop ordinary pushes when CI fails. Inspect the exact SHA with
`delivery_ci_inspect`, make one atomic fix, and call `delivery_prepare` with
intent `repair_ci` and that exact `repairsSha`. Gate R issues a single-use
repair receipt. Never use `--no-verify` or `DELIVERY_SKIP_CI_CHECK`.
Workflow files and workflow-job failures are `HUMAN_ONLY` and must be
escalated.

## Review checklist

- JVM tests do not depend on a device or real backend.
- Instrumented tests use deterministic fakes for UI wiring.
- No secrets or token/payload logging appears in the diff.
- No generated files, debug artifacts, or `.delivery/runtime/` are committed.
- A blocked prerequisite is reported rather than replaced with a weaker check.
- `git diff --check` is clean.
