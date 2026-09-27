import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";

test("CI executes devices only for a final commit trailer matching its User Story", async t => {
  const workflow = await fs.readFile(new URL("../../../.github/workflows/ci.yml", import.meta.url), "utf8");
  const block = workflow.match(/      - id: filter\n        shell: bash\n        run: \|\n((?:          .*\n)+)/)?.[1];
  assert.ok(block, "exercise the actual CI decision script");
  const script = block.replace(/^          /gm, "");
  assert.match(workflow, /if: needs\.changes\.outputs\.instrumented == 'true'/);
  assert.doesNotMatch(workflow, /workflow_dispatch|paths-filter/);
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "android-ci-trigger-"));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const git = args => execFileSync("git", args, { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
  git(["init", "-b", "main"]);
  git(["config", "user.name", "Delivery Tests"]);
  git(["config", "user.email", "delivery-tests@example.com"]);
  git(["config", "commit.gpgsign", "false"]);
  const output = path.join(root, "output");
  const cases = [
    ["feat[54]: integrate proposal navigation", "false"],
    ["test[54]: cover proposal navigation\n\nDelivery-Verify-US: 54", "true"],
    ["fix[54]: repair final proposal verification\n\nDelivery-Verify-US: 54", "true"],
    ["test[54.1]: cover proposal detail\n\nDelivery-Verify-US: 54.1", "true"],
    ["test[54]: cover proposal navigation\n\nDelivery-Verify-US: 55", null],
    ["test[55]: mention [54]: without changing the story\n\nDelivery-Verify-US: 54", null],
    ["test[54]: cover proposal navigation\n\nDelivery-Verify-US: 54\nDelivery-Verify-US: 54", null],
    ["test[54]: cover proposal navigation\n\nDelivery-Verify-US: invalid", null],
  ];
  for (const [message, expected] of cases) {
    git(["commit", "--allow-empty", "-m", message]);
    await fs.writeFile(output, "");
    const execute = () => execFileSync("bash", ["-e", "-o", "pipefail", "-c", script], {
      cwd: root, env: { ...process.env, GITHUB_OUTPUT: output }, stdio: ["ignore", "pipe", "pipe"],
    });
    if (expected === null) assert.throws(execute, /Delivery-Verify-US/);
    else {
      execute();
      assert.equal(await fs.readFile(output, "utf8"), `instrumented=${expected}\n`, message);
    }
  }
});
