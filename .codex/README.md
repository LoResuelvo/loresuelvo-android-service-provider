# Codex delivery integration

This directory contains the optional, versioned Codex delivery guard for the Android service-provider repository. The MCP server and neutral delivery CLI are project tooling; the client registration in `config.toml` is clone-local and intentionally ignored by Git.

The versioned Git hooks in `.githooks/` and the delivery CLI are the canonical workflow for every developer. Codex users can additionally register the MCP server locally and enable the anticipatory guard from `hooks.json`. The guard is read-only: it only checks for a prepared receipt before a `git commit` command and never runs tests or delivery gates.

## Clone setup

1. Install the isolated delivery package with `make delivery-install`.
2. Install the Git hooks once with `make delivery-hooks-install`.
3. Register the local MCP server from the repository root:

   ```bash
   codex mcp add loresuelvo-delivery -- scripts/with-node-24.sh node tools/delivery-mcp/server.mjs
   ```

   To avoid approval prompts for this server, keep the following in your local `.codex/config.toml` or `~/.codex/config.toml`:

   ```toml
   [mcp_servers.loresuelvo-delivery]
   default_tools_approval_mode = "approve"
   ```

4. Approve the optional local `PreToolUse` hook when the Codex client requests it.

The repository does not store credentials, tokens, or machine-specific paths in `.codex/`. The local `config.toml` is ignored; client-specific agent configuration remains ignored under `.agents/`.

## Delivery interfaces

The MCP server exposes the same delivery operations as the neutral CLI: inspection, focused delivery tests, preparation, context, CI inspection, recoverable job waiting, HEAD verification, and finalization. `make delivery-*` targets delegate to the package entry points and do not duplicate policy or gate logic.
