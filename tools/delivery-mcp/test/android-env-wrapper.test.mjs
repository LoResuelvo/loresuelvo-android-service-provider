import test from "node:test";
import assert from "node:assert/strict";
import { chmodSync, copyFileSync, existsSync, mkdirSync, mkdtempSync, realpathSync, rmSync, writeFileSync } from "node:fs";
import { delimiter, join, dirname, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const REPOSITORY_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../../..");
const WRAPPER_SOURCE = join(REPOSITORY_ROOT, "scripts", "with-android-env.sh");
const PROBE = [
  "printf 'java=%s\\nsdk=%s\\nhome=%s\\navd=%s\\nuser=%s\\npath=%s\\narg=%s\\n' \"$JAVA_HOME\" \"$ANDROID_SDK_ROOT\" \"${ANDROID_HOME-}\" \"${ANDROID_AVD_HOME-}\" \"${ANDROID_USER_HOME-}\" \"$PATH\" \"$1\"",
].join("\n");

function makeSandbox(prefix) {
  return mkdtempSync(join(REPOSITORY_ROOT, `.android-env-test-${prefix}-`));
}

function isolatedEnvironment(overrides = {}) {
  const environment = { ...process.env };
  for (const name of ["JAVA_HOME", "ANDROID_SDK_ROOT", "ANDROID_HOME", "ANDROID_AVD_HOME", "ANDROID_USER_HOME", "LOCALAPPDATA"]) {
    delete environment[name];
  }
  return { ...environment, ...overrides };
}

function makeJavaHome(root, version = "17.0.20", directoryName = `jdk-${version}`) {
  const home = join(root, directoryName);
  const binaryDirectory = join(home, "bin");
  mkdirSync(binaryDirectory, { recursive: true });
  const javaBinary = join(binaryDirectory, "java");
  writeFileSync(javaBinary, `#!/bin/sh\nprintf '%s\\n' 'openjdk version "${version}"' >&2\n`);
  chmodSync(javaBinary, 0o755);
  return home;
}

function makeSdk(root) {
  const sdk = join(root, "android-sdk");
    for (const relativePath of ["platform-tools", "emulator", "cmdline-tools/latest/bin", "cmdline-tools/12.0/bin"]) {
    mkdirSync(join(sdk, relativePath), { recursive: true });
  }
  return sdk;
}

function makeProject(root) {
  const project = join(root, "repo");
  mkdirSync(join(project, "scripts"), { recursive: true });
  const wrapper = join(project, "scripts", "with-android-env.sh");
  copyFileSync(WRAPPER_SOURCE, wrapper);
  chmodSync(wrapper, 0o755);
  return { project, wrapper };
}

function runWrapper(wrapper, args, environment, cwd = dirname(dirname(wrapper))) {
  return spawnSync(wrapper, args, {
    cwd,
    env: environment,
    encoding: "utf8",
  });
}

function parseProbe(result) {
  assert.equal(result.status, 0, `wrapper failed:\nstdout=${result.stdout}\nstderr=${result.stderr}`);
  const observed = Object.fromEntries(
    result.stdout
      .trimEnd()
      .split("\n")
      .map((line) => {
        const separator = line.indexOf("=");
        return [line.slice(0, separator), line.slice(separator + 1)];
      }),
  );
  observed.javaHome = observed.java;
  observed.sdkRoot = observed.sdk;
  observed.androidHome = observed.home;
  observed.path = observed.path.split(delimiter);
  observed.argv = [observed.arg];
  return observed;
}

function probeArgs(payload = "") {
  return ["/bin/sh", "-c", PROBE, "probe", payload];
}

test("preserves explicit toolchain variables and argv while prepending child PATH entries", () => {
  const sandbox = makeSandbox("explicit");
  try {
    const { wrapper, project } = makeProject(sandbox);
    const javaHome = makeJavaHome(join(sandbox, "explicit"));
    const sdkRoot = makeSdk(join(sandbox, "explicit"));
    const avdHome = join(sandbox, "explicit-avd");
    const userHome = join(sandbox, "explicit-user-home");
    const sentinelPath = join(sandbox, "sentinel-path");
    const marker = join(sandbox, "should-not-exist");
    const payload = `literal;touch ${marker} $(touch ${marker})`;
    const result = runWrapper(
      wrapper,
      probeArgs(payload),
      isolatedEnvironment({
        JAVA_HOME: javaHome,
        ANDROID_SDK_ROOT: sdkRoot,
        ANDROID_AVD_HOME: avdHome,
        ANDROID_USER_HOME: userHome,
        PATH: `/usr/bin${delimiter}${sentinelPath}`,
      }),
      project,
    );
    const observed = parseProbe(result);

    assert.deepEqual(observed.argv, [payload]);
    assert.equal(observed.javaHome, javaHome);
    assert.equal(observed.sdkRoot, sdkRoot);
    assert.equal(observed.androidHome, sdkRoot);
    assert.equal(observed.avd, avdHome);
    assert.equal(observed.user, userHome);
    assert.equal(observed.path[0], join(javaHome, "bin"));
    assert.equal(observed.path[1], join(sdkRoot, "platform-tools"));
    assert.equal(observed.path[2], join(sdkRoot, "emulator"));
    assert.ok(observed.path.includes(join(sdkRoot, "cmdline-tools/latest/bin")));
    assert.ok(observed.path.includes(join(sdkRoot, "cmdline-tools/12.0/bin")));
    assert.equal(observed.path.at(-1), sentinelPath);
    assert.equal(result.stderr, "");
    assert.equal(existsSync(marker), false);
  } finally {
    rmSync(sandbox, { recursive: true, force: true });
  }
});

test("discovers Java 17 and the Android SDK from the repository sibling toolchains", () => {
  const sandbox = makeSandbox("sibling");
  try {
    const { wrapper, project } = makeProject(sandbox);
    const toolchains = join(sandbox, ".toolchains");
    const javaHome = makeJavaHome(toolchains, "17.0.20.1+1");
    const sdkRoot = makeSdk(toolchains);
    const avdHome = join(toolchains, "android-avd");
    const userHome = join(toolchains, "android-user-home");
    mkdirSync(avdHome, { recursive: true });
    mkdirSync(userHome, { recursive: true });
    const observed = parseProbe(
      runWrapper(
        wrapper,
        probeArgs(),
        isolatedEnvironment({ HOME: join(sandbox, "home"), PATH: `/usr/bin${delimiter}${join(sandbox, "path")}` }),
        project,
      ),
    );

    assert.equal(observed.javaHome, realpathSync(javaHome));
    assert.equal(observed.sdkRoot, realpathSync(sdkRoot));
    assert.equal(observed.androidHome, realpathSync(sdkRoot));
    assert.equal(observed.avd, realpathSync(avdHome));
    assert.equal(observed.user, realpathSync(userHome));
    assert.equal(observed.path[0], join(realpathSync(javaHome), "bin"));
    assert.equal(observed.path[1], join(realpathSync(sdkRoot), "platform-tools"));
  } finally {
    rmSync(sandbox, { recursive: true, force: true });
  }
});

test("discovers standard home-directory Java and Android SDK locations", () => {
  const sandbox = makeSandbox("standard");
  try {
    const { wrapper, project } = makeProject(sandbox);
    const home = join(sandbox, "home");
    const javaHome = makeJavaHome(join(home, ".sdkman", "candidates", "java"), "17.0.12", "17.0.12");
    const sdkRoot = join(home, "Android", "Sdk");
    mkdirSync(join(sdkRoot, "platform-tools"), { recursive: true });
    const observed = parseProbe(
      runWrapper(
        wrapper,
        probeArgs(),
        isolatedEnvironment({ HOME: home, PATH: `/usr/bin${delimiter}${join(sandbox, "path")}` }),
        project,
      ),
    );

    assert.equal(observed.javaHome, realpathSync(javaHome));
    assert.equal(observed.sdkRoot, realpathSync(sdkRoot));
  } finally {
    rmSync(sandbox, { recursive: true, force: true });
  }
});

test("reports invalid explicit environment values in precise English diagnostics", () => {
  const sandbox = makeSandbox("errors");
  try {
    const { wrapper, project } = makeProject(sandbox);
    const missingJava = join(sandbox, "missing-java");
    const javaHome = makeJavaHome(join(sandbox, "valid"));
    const sdkRoot = makeSdk(join(sandbox, "valid"));

    const invalidJava = runWrapper(
      wrapper,
      ["/bin/true"],
      isolatedEnvironment({ JAVA_HOME: missingJava, ANDROID_SDK_ROOT: sdkRoot }),
      project,
    );
    assert.notEqual(invalidJava.status, 0);
    assert.match(invalidJava.stderr, /android-env: error: JAVA_HOME points to .*does not exist/);

    const invalidSdk = runWrapper(
      wrapper,
      ["/bin/true"],
      isolatedEnvironment({ JAVA_HOME: javaHome, ANDROID_SDK_ROOT: join(sandbox, "missing-sdk") }),
      project,
    );
    assert.notEqual(invalidSdk.status, 0);
    assert.match(invalidSdk.stderr, /android-env: error: ANDROID_SDK_ROOT points to .*does not exist/);

    const wrongJava = makeJavaHome(join(sandbox, "wrong"), "11.0.26");
    const wrongVersion = runWrapper(
      wrapper,
      ["/bin/true"],
      isolatedEnvironment({ JAVA_HOME: wrongJava, ANDROID_SDK_ROOT: sdkRoot }),
      project,
    );
    assert.notEqual(wrongVersion.status, 0);
    assert.match(wrongVersion.stderr, /Java 11 .*Java 17 is required/);
  } finally {
    rmSync(sandbox, { recursive: true, force: true });
  }
});

test("requires a command and emits usage diagnostics when none is supplied", () => {
  const sandbox = makeSandbox("usage");
  try {
    const { wrapper } = makeProject(sandbox);
    const result = spawnSync(wrapper, [], { encoding: "utf8" });
    assert.equal(result.status, 64);
    assert.match(result.stderr, /^Usage: with-android-env\.sh <command>/);
    assert.match(result.stderr, /Run a command with Java 17 and Android SDK environment discovery\./);
  } finally {
    rmSync(sandbox, { recursive: true, force: true });
  }
});
