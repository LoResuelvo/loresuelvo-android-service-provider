import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  isGitCommitCommand,
  parseCodexHookInput,
} from "./delivery-guard.mjs";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

test("Codex guard recognizes commit commands and ignores ordinary shell commands", () => {
  for (const command of [
    "git commit -m 'docs[33]: update documentation'",
    "git -C app -c user.name=bot commit -m 'test[33]: add coverage'",
    "DELIVERY_REQUIRE_EVIDENCE=1 rtk git --no-pager commit -m 'chore[33]: update tooling'",
    "bash -lc 'git commit -m \"fix[33]: repair guard\"'",
    "rtk git add README.md\nrtk git commit -m 'docs[33]: update readme'",
  ]) {
    assert.equal(isGitCommitCommand(command), true, command);
  }

  for (const command of [
    "git status",
    "git add README.md",
    "echo 'git commit -m docs'",
    "bash -lc 'echo git commit'",
    "node -e \"console.log('git commit')\"",
  ]) {
    assert.equal(isGitCommitCommand(command), false, command);
  }
});

test("Codex guard parses the official PreToolUse payload", () => {
  const parsed = parseCodexHookInput(
    JSON.stringify({
      tool_name: "Bash",
      tool_input: { command: "git commit -m 'docs[33]: update readme'" },
    })
  );
  assert.deepEqual(parsed, {
    toolName: "Bash",
    rawCommand: "git commit -m 'docs[33]: update readme'",
  });
});

test("Codex hook configuration points to the repository-relative guard", async () => {
  const config = JSON.parse(await fs.readFile(path.join(repositoryRoot, ".codex/hooks.json"), "utf8"));
  assert.deepEqual(config.hooks.PreToolUse[0], {
    matcher: "^Bash$",
    hooks: [
      {
        type: "command",
        command: "node \"$(git rev-parse --show-toplevel)/.codex/delivery-guard.mjs\"",
        timeout: 1200,
        statusMessage: "Verifying delivery evidence",
      },
    ],
  });
});

test("versioned Git hooks delegate to the delivery CLI without running suites", async () => {
  const expectedCommands = {
    "pre-commit": "node tools/delivery-mcp/cli.mjs hook pre-commit",
    "commit-msg": "node tools/delivery-mcp/cli.mjs hook commit-msg \"$1\"",
    "post-commit": "node tools/delivery-mcp/cli.mjs hook post-commit",
    "pre-push": "node tools/delivery-mcp/cli.mjs hook pre-push",
  };

  for (const [hookName, command] of Object.entries(expectedCommands)) {
    const hookPath = path.join(repositoryRoot, ".githooks", hookName);
    const hook = await fs.readFile(hookPath, "utf8");
    const mode = (await fs.stat(hookPath)).mode;
    assert.ok(mode & 0o111, `${hookName} must be executable`);
    assert.match(hook, /#!\/usr\/bin\/env sh/);
    assert.match(hook, /set -e/);
    assert.match(hook, new RegExp(command.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
    assert.doesNotMatch(hook, /gradlew|make (test|lint|build|e2e)/);
  }
});
