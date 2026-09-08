import test from "node:test";
import assert from "node:assert/strict";
import {
  chmodSync,
  copyFileSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { delimiter, dirname, join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const REPOSITORY_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../../..");
const WRAPPER_SOURCE = join(REPOSITORY_ROOT, "scripts", "with-node-24.sh");

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function makeSandbox(prefix) {
  return mkdtempSync(join(REPOSITORY_ROOT, `.node-24-test-${prefix}-`));
}

function isolatedEnvironment(overrides = {}) {
  const environment = { ...process.env };
  for (const name of ["DELIVERY_NODE", "NODE_OPTIONS"]) delete environment[name];
  return { ...environment, ...overrides };
}

function makeFakeNode(root, version, directoryName = "bin") {
  const binaryDirectory = join(root, directoryName);
  mkdirSync(binaryDirectory, { recursive: true });
  const nodeBinary = join(binaryDirectory, "node");
  writeFileSync(
    nodeBinary,
    `#!/bin/sh
if [ "$1" = "--version" ]; then
  printf '%s\\n' '${version}'
  exit 0
fi
printf 'node=%s\\narg1=%s\\narg2=%s\\narg3=%s\\npath=%s\\n' "$0" "$1" "$2" "$3" "$PATH"
`,
    "utf8",
  );
  chmodSync(nodeBinary, 0o755);
  return nodeBinary;
}

function makeProject(root) {
  const project = join(root, "repo");
  const scripts = join(project, "scripts");
  mkdirSync(scripts, { recursive: true });
  const wrapper = join(scripts, "with-node-24.sh");
  copyFileSync(WRAPPER_SOURCE, wrapper);
  chmodSync(wrapper, 0o755);
  return { project, wrapper };
}

function runWrapper(wrapper, args, environment, cwd) {
  return spawnSync(wrapper, args, {
    cwd,
    env: environment,
    encoding: "utf8",
  });
}

test("discovers Node.js 24 from PATH and preserves argv without shell evaluation", () => {
  const sandbox = makeSandbox("path");
  try {
    const { project, wrapper } = makeProject(sandbox);
    const nodeBinary = makeFakeNode(join(sandbox, "path-node"), "v24.20.0");
    const marker = join(sandbox, "must-not-exist");
    const payload = `literal;touch ${marker} $(touch ${marker})`;
    const result = runWrapper(
      wrapper,
      ["node", "-e", "probe", payload, "a b"],
      isolatedEnvironment({ PATH: `${dirname(nodeBinary)}${delimiter}/usr/bin` }),
      project,
    );

    assert.equal(result.status, 0, `wrapper failed:\nstdout=${result.stdout}\nstderr=${result.stderr}`);
    assert.equal(result.stderr, "");
    assert.match(result.stdout, /^node=/m);
    assert.match(result.stdout, /^arg1=-e$/m);
    assert.match(result.stdout, /^arg2=probe$/m);
    assert.match(result.stdout, new RegExp(`^arg3=${escapeRegex(payload)}$`, "m"));
    assert.match(result.stdout, new RegExp(`^path=${dirname(nodeBinary)}(?:${delimiter}|$)`, "m"));
    assert.equal(existsSync(marker), false);
  } finally {
    rmSync(sandbox, { recursive: true, force: true });
  }
});

test("prefers a repository-sibling Node.js 24 over an older PATH installation", () => {
  const sandbox = makeSandbox("sibling");
  try {
    const { project, wrapper } = makeProject(sandbox);
    const pathNode = makeFakeNode(join(sandbox, "path-node"), "v20.19.2");
    makeFakeNode(join(sandbox, ".toolchains", "node-v24.20.0-linux-x64"), "v24.20.0");
    const result = runWrapper(
      wrapper,
      ["node", "--version"],
      isolatedEnvironment({ PATH: `${dirname(pathNode)}${delimiter}/usr/bin` }),
      project,
    );

    assert.equal(result.status, 0, `wrapper failed:\nstdout=${result.stdout}\nstderr=${result.stderr}`);
    assert.equal(result.stdout.trim(), "v24.20.0");
  } finally {
    rmSync(sandbox, { recursive: true, force: true });
  }
});

test("honors an explicit DELIVERY_NODE environment override", () => {
  const sandbox = makeSandbox("env-override");
  try {
    const { project, wrapper } = makeProject(sandbox);
    const pathNode = makeFakeNode(join(sandbox, "node-20"), "v20.19.2");
    const explicitNode = makeFakeNode(join(sandbox, "node-24"), "v24.20.0");
    const result = runWrapper(
      wrapper,
      ["node", "--version"],
      isolatedEnvironment({
        DELIVERY_NODE: explicitNode,
        PATH: `${dirname(pathNode)}${delimiter}/usr/bin`,
      }),
      project,
    );

    assert.equal(result.status, 0, `wrapper failed:\nstdout=${result.stdout}\nstderr=${result.stderr}`);
    assert.equal(result.stdout.trim(), "v24.20.0");
  } finally {
    rmSync(sandbox, { recursive: true, force: true });
  }
});

test("rejects an explicit wrong-major DELIVERY_NODE with a clear diagnostic", () => {
  const sandbox = makeSandbox("wrong-explicit");
  try {
    const { project, wrapper } = makeProject(sandbox);
    const wrongNode = makeFakeNode(join(sandbox, "node-20"), "v20.19.2");
    const result = runWrapper(
      wrapper,
      ["--node", wrongNode, "node", "--version"],
      isolatedEnvironment({ PATH: "/usr/bin" }),
      project,
    );

    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Node\.js 20 .*Node\.js 24 is required/);
    assert.match(result.stderr, new RegExp(escapeRegex(wrongNode)));
  } finally {
    rmSync(sandbox, { recursive: true, force: true });
  }
});

test("rejects a wrong-major PATH installation when no Node.js 24 is available", () => {
  const sandbox = makeSandbox("wrong-path");
  try {
    const { project, wrapper } = makeProject(sandbox);
    const wrongNode = makeFakeNode(join(sandbox, "node-20"), "v20.19.2");
    const result = runWrapper(
      wrapper,
      ["node", "--version"],
      isolatedEnvironment({ PATH: `${dirname(wrongNode)}${delimiter}/usr/bin` }),
      project,
    );

    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Node\.js 20 .*Node\.js 24 is required/);
  } finally {
    rmSync(sandbox, { recursive: true, force: true });
  }
});

test("reports usage when no command is supplied", () => {
  const sandbox = makeSandbox("usage");
  try {
    const { wrapper } = makeProject(sandbox);
    const result = spawnSync(wrapper, [], { encoding: "utf8" });
    assert.equal(result.status, 64);
    assert.match(result.stderr, /^Usage: with-node-24\.sh \[--node <path-or-command>\] <command>/);
    assert.match(result.stderr, /Run a command with Node\.js 24 discovery\./);
  } finally {
    rmSync(sandbox, { recursive: true, force: true });
  }
});

test("routes every delivery Make target through the Node.js 24 wrapper", () => {
  const makefile = readFileSync(join(REPOSITORY_ROOT, "Makefile"), "utf8");
  assert.match(makefile, /NODE24_ENV := \.\/scripts\/with-node-24\.sh/);
  assert.match(makefile, /DELIVERY_NODE_ARGS = .*--node \$\(DELIVERY_NODE\)/);
  for (const target of [
    "delivery-install",
    "delivery-mcp",
    "delivery-test",
    "delivery-smoke",
    "delivery-inspect",
    "delivery-prepare",
    "delivery-context",
    "delivery-ci",
    "delivery-finalize",
    "delivery-verify-head",
    "delivery-hooks-install",
    "delivery-hooks-status",
  ]) {
    assert.match(makefile, new RegExp(`${target}:[\\s\\S]*?\\n\\t\\$\\(DELIVERY_(?:RUN|CLI)\\b`), target);
  }
});
