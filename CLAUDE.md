# CLAUDE.md — LoResuelvo Android Service Provider

[`AGENTS.md`](AGENTS.md) is the canonical context for this repository. Read
it before acting and load the matching skill from `skills/` for the task.

Quick facts:

- Provider package: `com.loresuelvo.serviceprovider`.
- Android build: Kotlin, Compose, Hilt, Retrofit, Auth0, and Cucumber JVM.
- Delivery tooling: Node.js 24 LTS in `tools/delivery-mcp/`.
- CI emulator: Pixel 6/API 34; no bootstrap workflow or prewarmed snapshot.
- Use `make test`, `make lint`, `make build`, and `make e2e` according to the
  test topology in `AGENTS.md`.
- Commit format: `<type>[33]: imperative English description`.
- Agent delivery uses the Delivery MCP and exact staged-snapshot evidence;
  never use `--no-verify` or `DELIVERY_SKIP_CI_CHECK`.
