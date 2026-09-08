# Codex delivery integration

This directory contains optional Codex configuration for the Android service-provider repository.

The versioned Git hooks in `.githooks/` and the delivery CLI are the canonical workflow for every developer. Codex users can additionally load the MCP server from `config.toml` and enable the anticipatory guard from `hooks.json`. The guard is read-only: it only checks for a prepared receipt before a `git commit` command and never runs tests or delivery gates.

## Clone setup

1. Install the isolated delivery package with `make delivery-install`.
2. Install the Git hooks once with `make delivery-hooks-install`.
3. Start a Codex task from this repository so it can load `.codex/config.toml`.
4. Approve the optional local `PreToolUse` hook when the Codex client requests it.

The repository does not store credentials, tokens, or machine-specific paths in `.codex/`. Client-specific agent configuration remains ignored under `.agents/`.

## Delivery interfaces

The MCP server exposes the same delivery operations as the neutral CLI: inspection, focused delivery tests, preparation, context, CI inspection, recoverable job waiting, HEAD verification, and finalization. `make delivery-*` targets delegate to the package entry points and do not duplicate policy or gate logic.
