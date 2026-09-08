# LoResuelvo Android Service Provider

Android application for LoResuelvo providers, built with Kotlin and Jetpack
Compose. Agent rules and repository conventions live in
[`AGENTS.md`](AGENTS.md). Delivery operations are documented in
[`.delivery/README.md`](.delivery/README.md).

## Requirements

- JDK 17 (`java -version`).
- Android SDK Platform 35, Build Tools 35, Platform Tools, and command-line
  tools.
- Node.js 24 LTS (`node --version`) for `tools/delivery-mcp`.
- GNU `make` and Bash.
- An Android emulator or device only for instrumented checks (`make e2e`).

The Gradle module targets Java 11 bytecode, but Gradle must run on JDK 17.

### Configure the Android SDK

Set the SDK variables in your shell:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export PATH="$ANDROID_HOME/emulator:$PATH"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Verify the installation:

```bash
sdkmanager --version
java -version
node --version
```

If the SDK is installed elsewhere, put `sdk.dir=/absolute/path/to/Android/Sdk`
in a local `local.properties` file. That file is ignored and must not be
committed.

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
   `adb reverse tcp:8080 tcp:8080` and `API_URL=http://127.0.0.1:8080` are
   convenient Dev settings.
3. Install the isolated delivery tooling:

   ```bash
   npm ci --prefix tools/delivery-mcp
   ```

4. Build the Dev variant:

   ```bash
   make build FLAVOR=Dev
   ```

Staging and Prod values are injected through the corresponding
`*_STAGING` and `*_PROD` Gradle properties or CI secrets. Staging and Prod
must use HTTPS endpoints.

## Commands

All Android targets accept `FLAVOR=Dev|Staging|Prod`; Dev is the default.

| Command | Purpose |
| --- | --- |
| `make help` | List available targets. |
| `make build` | Assemble the selected debug APK. |
| `make lint` | Run Android Lint. |
| `make test` | Run JVM unit tests and Cucumber JVM. |
| `make e2e` | Run instrumented UI tests; requires a device/emulator. |
| `make test-all-once` | Run JVM and instrumented tests. |
| `make ci` | Run build, lint, JVM, and instrumented checks. |
| `make clean` | Remove Gradle build outputs. |
| `make devices` | List ADB devices. |
| `make delivery-install` | Install the isolated Node delivery package. |
| `make delivery-mcp` | Start the Delivery MCP server. |
| `make delivery-test ARGS="..."` | Run focused delivery TDD checks through the CLI. |
| `make delivery-smoke` | Verify MCP startup and tool discovery. |
| `make delivery-inspect ARGS="--intent prepare_commit"` | Inspect the staged snapshot and selected gate. |
| `make delivery-prepare ARGS="--intent prepare_commit"` | Run the policy-selected pre-commit gate. |
| `make delivery-context ARGS="--inspect"` | Inspect validated delivery context. |
| `make delivery-ci ARGS="--sha <commit-sha>"` | Inspect CI for a commit SHA. |
| `make delivery-verify-head ARGS="--intent close_us --scope <feature>"` | Record Gate D evidence for the current HEAD. |
| `make delivery-finalize ARGS="--intent close_us --scope <feature>"` | Finalize a batch or User Story. |
| `make delivery-hooks-install` | Install the repository Git hooks locally. |
| `make delivery-hooks-status` | Report hook and enforcement state. |

For a focused JVM test during local iteration:

```bash
./gradlew :app:testDevDebugUnitTest --tests '*WelcomeViewModelTest*'
./gradlew :app:testDevDebugUnitTest --tests '*WelcomeCucumberTest'
```

The delivery policy deliberately uses the complete Dev JVM task for BDD Gate 0
and Gate B because this repository has no reliable feature-file-to-runner
command. Do not silently replace a blocked instrumented run with a unit run.

Commits use the canonical migration format
`<type>[33]: imperative English description`; keep each commit atomic and
prepare its exact staged snapshot through the Delivery MCP.

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
tests on a Pixel 6/API 34 x86_64 emulator, and a Staging build. It does not use
a prewarmed snapshot and there is no checked-in AVD bootstrap workflow. Local
emulator availability is independent from CI; use `make devices` before
`make e2e`.

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
`DELIVERY_SKIP_CI_CHECK`; CI repairs require the failed SHA and Gate R.

## Troubleshooting

### `SDK location not found`

Set `ANDROID_HOME`/`ANDROID_SDK_ROOT`, or add `sdk.dir` to `local.properties`.

### `No connected devices`

Start an API 34+ emulator or connect a device, verify `adb devices`, and then
run `make e2e FLAVOR=Dev`. A blocked device check must remain visible in the
delivery result.

### Staging build reports missing variables

Provide all `*_STAGING` values before running `make lint`, `make test`,
`make build`, or `make e2e` with `FLAVOR=Staging`. Do not substitute Dev values
and call the result CI parity.

### Delivery package or Node version failure

Check `node --version` is 24.x, then run `npm ci --prefix tools/delivery-mcp`.
Read [`AGENTS.md`](AGENTS.md) and [`.delivery/README.md`](.delivery/README.md)
for policy, evidence, job recovery, and repair details.
