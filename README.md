# LoResuelvo Android Service Provider

Android application for LoResuelvo providers, built with Kotlin and Jetpack
Compose. Agent rules and repository conventions live in
[`AGENTS.md`](AGENTS.md). Delivery operations are documented in
[`.delivery/README.md`](.delivery/README.md).

## Requirements

- JDK 17 (`java -version`).
- Android SDK Platform 35, Build Tools 35, Platform Tools, and command-line
  tools.
- Node.js 24 LTS (`scripts/with-node-24.sh node --version`) for
  `tools/delivery-mcp`.
- GNU `make` and Bash.
- An Android emulator or device only for instrumented checks (`make e2e`).

The Gradle module targets Java 11 bytecode, but Gradle must run on JDK 17.

### Configure the Android SDK

Set the SDK and Java variables in your shell when they are not already
configured by your development environment:

```bash
# Example location; use the Java 17 installation provided by your environment.
export JAVA_HOME="$HOME/.jdks/jdk-17"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export PATH="$ANDROID_HOME/emulator:$PATH"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Verify the installation:

```bash
scripts/with-android-env.sh sdkmanager --version
scripts/with-android-env.sh java -version
scripts/with-node-24.sh node --version
```

Every Android Make target delegates to `scripts/with-android-env.sh`. The
wrapper applies the configured JDK and Android SDK environment before running
Gradle, ADB, or the instrumented-test helper, so local and CI invocations use
the same entry point. It honors explicit `JAVA_HOME`, `ANDROID_SDK_ROOT`, and
`ANDROID_HOME` values, the SDK entry in `local.properties`, and standard
repository-sibling toolchain locations. It does not install toolchains or
start an emulator; a missing JDK or SDK fails with an actionable diagnostic.
Keep machine-specific SDK values in your shell or the ignored
`local.properties` file; never commit them.

Every delivery Make target, installed Git hook, and Codex delivery entry point
delegates to `scripts/with-node-24.sh`. The wrapper honors an explicit
`DELIVERY_NODE`, otherwise it discovers a repository-sibling `.toolchains`
Node 24 installation or a Node 24 executable on `PATH`. It rejects other Node
major versions with an actionable diagnostic.

## Setup

1. Clone the repository and enter its root.
2. Create `local.properties` with Dev values, for example:

   ```properties
   AUTH0_DOMAIN=loresuelvo-dev.auth0.com
   AUTH0_CLIENT_ID=your_dev_client_id
   AUTH0_SCHEME=com.loresuelvo.provider
   AUTH0_AUDIENCE=http://localhost:8080
   API_URL=http://10.0.2.2:8080
   ```

   `AUTH0_AUDIENCE` is the logical Auth0 API identifier. For a physical
   device, `API_URL` must be reachable from that device; with a local server,
   `scripts/with-android-env.sh adb reverse tcp:8080 tcp:8080` and
   `API_URL=http://127.0.0.1:8080` are convenient Dev settings.
3. Install the isolated delivery tooling:

   ```bash
   make delivery-install
   ```

4. Build the Dev variant:

   ```bash
   make build FLAVOR=Dev
   ```

Staging and Prod values are injected through the corresponding
`*_STAGING` and `*_PROD` Gradle properties or CI secrets. Staging and Prod
must use HTTPS endpoints.

## CI emulator cache

The CI instrumented-test job restores a prewarmed Pixel 6/API 34 emulator
snapshot. Run the manual
`.github/workflows/avd-bootstrap.yml` workflow with an incremented
`cache_version` input when the emulator configuration changes.

## Commands

All Android targets accept `FLAVOR=Dev|Staging|Prod`; Dev is the default.

| Command | Purpose |
| --- | --- |
| `make help` | List available targets. |
| `make build` | Assemble the selected debug APK through the Android environment wrapper. |
| `make lint` | Run Android Lint through the Android environment wrapper. |
| `make test` | Run JVM unit tests and Cucumber JVM through the Android environment wrapper. |
| `make e2e` | Run instrumented UI tests through the wrapper; requires a device/emulator. |
| `make test-all-once` | Run JVM and instrumented tests. |
| `make ci` | Run build, lint, JVM, and instrumented checks. |
| `make clean` | Remove Gradle build outputs through the Android environment wrapper. |
| `make devices` | List ADB devices through the Android environment wrapper. |
| `make delivery-install` | Install the isolated Node delivery package. |
| `make delivery-mcp` | Start the Delivery MCP server. |
| `make delivery-test ARGS="..."` | Run focused delivery TDD checks through the CLI. |
| `make delivery-smoke` | Verify MCP startup and tool discovery. |
| `make delivery-inspect ARGS="--intent prepare_commit"` | Inspect the staged snapshot and selected gate. |
| `make delivery-prepare ARGS="--intent prepare_commit"` | Run the policy-selected pre-commit gate. |
| `make delivery-context ARGS="--inspect"` | Inspect validated delivery context. |
| `make delivery-context ARGS="--intent repair_ci --repairs-sha <sha>"` | Bind an exact human CI-repair context after staging. |
| `make delivery-ci ARGS="--sha <commit-sha>"` | Inspect CI for a commit SHA. |
| `make delivery-verify-head ARGS="--intent close_us --scope <feature>"` | Record Gate D evidence for the current HEAD. |
| `make delivery-finalize ARGS="--intent close_us --scope <feature>"` | Finalize a batch or User Story. |
| `make delivery-job-wait ARGS="--job-id <job-id>"` | Await a recoverable delivery job for a bounded interval. |
| `make delivery-job-cancel ARGS="--job-id <job-id>"` | Cooperatively cancel a recoverable delivery job. |
| `make delivery-hooks-install` | Install the repository Git hooks locally. |
| `make delivery-hooks-status` | Report hook and enforcement state. |

For a focused JVM test during local iteration:

```bash
scripts/with-android-env.sh ./gradlew :app:testDevDebugUnitTest --tests '*WelcomeViewModelTest*'
scripts/with-android-env.sh ./gradlew :app:testDevDebugUnitTest --tests '*WelcomeCucumberTest'
```

The delivery policy deliberately uses the complete Dev JVM task for BDD Gate 0
and Gate B because this repository has no reliable feature-file-to-runner
command. Do not silently replace a blocked instrumented run with a unit run.

Commits use the canonical format
`<type>[<us-number>]: imperative English description`, where the bracketed
value is the User Story identifier from the issue title rather than the
GitHub issue number. Keep each commit atomic and prepare its exact staged snapshot
through the Delivery MCP.

## Android test topology

- Features: `app/src/test/resources/features/`.
- Cucumber glue and runners:
  `app/src/test/java/com/loresuelvo/serviceprovider/bdd/`.
- JVM tests: `app/src/test/`; no device is required.
- Instrumented UI tests: `app/src/androidTest/`; a device or emulator is
  required.
- The provider Welcome examples are `provider-welcome.feature`,
  `WelcomeViewModelTest`, and `WelcomeScreenAcceptanceTest`.

## CI

The checked-in workflow runs Java 17, Staging lint and JVM tests, instrumented
tests on a prewarmed Pixel 6/API 34 x86_64 emulator, and a Staging build.
Delivery tooling checks run in a separate parallel job. Run the manual
`.github/workflows/avd-bootstrap.yml` workflow when the AVD cache needs to be
regenerated. Local emulator availability is independent from CI; use `make devices`
before `make e2e`.

Required Staging values are supplied as CI secrets:

`AUTH0_DOMAIN_STAGING`, `AUTH0_CLIENT_ID_STAGING`, `AUTH0_AUDIENCE_STAGING`,
`API_URL_STAGING`, and `AUTH0_SCHEME_STAGING`.

## Delivery and hooks

The Delivery MCP is the canonical path for agent checks. It supports
`delivery_test`, `delivery_inspect`, `delivery_prepare`, job waiting and
cancellation, CI inspection, HEAD verification, and finalization. Generated
receipts, logs, jobs, and ledger state remain under `.delivery/runtime/`.

The first rollout is shadow mode: `DELIVERY_REQUIRE_EVIDENCE` remains disabled
until delivery tests, smoke, the Android gate matrix, and real-repository
checks pass. Hooks never run test suites. Do not use `--no-verify` or
`DELIVERY_SKIP_CI_CHECK`; agent CI repairs require the failed SHA and Gate R.
Humans may delegate the repair verification to remote CI by recording an exact
`repair_ci` context after the final `git add`:

```bash
make delivery-context ARGS="--intent repair_ci --repairs-sha <failed-sha> [--us-id <id>]"
git commit -m "fix: repair the failed commit"
git push origin main
```

The context is accepted only when the commit parent, branch, staged tree, and
message match. The repair commit remains `not_run`, so
`DELIVERY_REQUIRE_EVIDENCE=1` still blocks it.

## Troubleshooting

### `SDK location not found`

Set `ANDROID_HOME`/`ANDROID_SDK_ROOT`, or add `sdk.dir` to `local.properties`.

### `No connected devices`

Start an API 34+ emulator or connect a device, verify `make devices`, and then
run `make e2e FLAVOR=Dev`. A blocked device check must remain visible in the
delivery result.

### `git push` reports a prior CI failure

Inspect the exact SHA first:

```bash
make delivery-ci ARGS="--sha <failed-sha>"
```

Agents must run the Staging Gate R repair flow. Humans can either do the same
or record the exact staged repair context shown above. A cancelled run that was
superseded by a green descendant is resolved automatically; an actual failed
run still requires an explicit repair.

### Staging build reports missing variables

Provide all `*_STAGING` values before running `make lint`, `make test`,
`make build`, or `make e2e` with `FLAVOR=Staging`. Do not substitute Dev values
and call the result CI parity.

### Delivery package or Node version failure

Run `scripts/with-node-24.sh node --version`, then `make delivery-install`.
Read [`AGENTS.md`](AGENTS.md) and [`.delivery/README.md`](.delivery/README.md)
for policy, evidence, job recovery, and repair details.
