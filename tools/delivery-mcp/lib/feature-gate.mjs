import { execFileSync } from "node:child_process";

const TEST_SOURCE = /^app\/src\/test\/(?:java|kotlin)\/([A-Za-z0-9_/]+)\.kt$/;
const FEATURE = /^app\/src\/test\/resources\/features\/[A-Za-z0-9_./-]+\.feature$/;
const full = (reason) => ({ scope: "full_jvm", reason, testClasses: [] });

// Read immutable Git blobs, never working-tree content or a persistent impact cache.
export function readFeatureGateTree(repoRoot, tree) {
  if (!/^[a-f0-9]{40}$/.test(tree)) throw new Error("Missing immutable Git tree");
  const options = { cwd: repoRoot, maxBuffer: 20 * 1024 * 1024, timeout: 10000 };
  const entries = execFileSync("git", ["ls-tree", "-rz", tree, "--", "app/src", "app/build.gradle.kts", "build.gradle.kts", "settings.gradle.kts", "gradle/libs.versions.toml", "buildSrc"], options)
    .toString("utf8").split("\0").filter(Boolean)
    .map((entry) => entry.match(/^(\d+) blob ([a-f0-9]{40})\t(.+)$/))
    .filter((entry) => entry && (/\.(?:kts?|java|feature|xml|toml)$/.test(entry[3]) || entry[3].startsWith("buildSrc/")));
  if (entries.some((entry) => entry[1] !== "100644" && entry[1] !== "100755")) throw new Error("Unsupported source mode");
  if (!entries.length) return new Map();
  const output = execFileSync("git", ["cat-file", "--batch"], {
    ...options, input: entries.map((entry) => entry[2]).join("\n") + "\n",
  });
  let offset = 0;
  return new Map(entries.map((entry) => {
    const end = output.indexOf(10, offset);
    const header = output.subarray(offset, end).toString("utf8").match(/^([a-f0-9]{40}) blob (\d+)$/);
    if (!header || header[1] !== entry[2]) throw new Error("Invalid Git blob response");
    const size = Number(header[2]);
    const source = output.subarray(end + 1, end + 1 + size).toString("utf8");
    offset = end + size + 2;
    return [entry[3], source];
  }));
}

function withoutComments(source) {
  // Preserve strings so package references through reflection are not hidden.
  return source.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*|\/\*[\s\S]*?\*\//g, (token) => {
    if (!token.startsWith("/")) return token;
    if (token.slice(2).includes("/*")) throw new Error("Nested Kotlin comments require full JVM");
    return token.replace(/[^\n]/g, " ");
  });
}

function kotlinFile(file, source) {
  const match = file.match(TEST_SOURCE);
  if (!match) throw new Error("Unsupported test source");
  const code = withoutComments(source);
  const parts = match[1].split("/");
  const name = parts.pop();
  const pkg = code.match(/^package\s+([A-Za-z0-9_.]+)\s*$/m)?.[1];
  if (!pkg || pkg !== parts.join(".")) throw new Error("Package does not match path");
  return { file, code, pkg, name };
}

function runnerOptions(source) {
  if (!source.code.includes("CucumberOptions")) {
    if (/io\.cucumber\.junit|Cucumber::class/.test(source.code)) throw new Error("Unmapped Cucumber runner");
    return null;
  }
  if (!/@RunWith\(Cucumber::class\)/.test(source.code)) throw new Error("Unsupported Cucumber runner");
  const blocks = [...source.code.matchAll(/@CucumberOptions\s*\(([^)]*)\)/g)];
  if (blocks.length !== 1) throw new Error("Ambiguous Cucumber options");
  const options = {};
  const remaining = blocks[0][1].replace(/(features|glue|plugin)\s*=\s*\[([^\]]*)\]|tags\s*=\s*"([^"\\]*)"/g, (match, key, list, tags) => {
    key ||= "tags";
    if (options[key]) throw new Error("Duplicate runner option");
    if (key === "tags") options[key] = tags;
    else {
      const values = [...list.matchAll(/"([A-Za-z0-9_:./-]+)"/g)].map((item) => item[1]);
      if (!values.length || list.replace(/"[A-Za-z0-9_:./-]+"|[\s,]/g, "")) throw new Error("Dynamic runner option");
      options[key] = values;
    }
    return "";
  });
  if (remaining.replace(/[\s,]/g, "") || !options.features || !options.glue ||
      (options.tags && options.tags !== "not @wip") ||
      (options.plugin || []).some((plugin) => !["pretty", "summary"].includes(plugin))) throw new Error("Filtered or unsupported runner");
  return { ...source, ...options };
}

export function featureRunners(tree) {
  return [...tree].filter(([file]) => TEST_SOURCE.test(file))
    .map(([file, source]) => runnerOptions(kotlinFile(file, source))).filter(Boolean)
    .map(runner => ({ ...runner, featureFile: runner.features.length === 1 && runner.features[0].endsWith('.feature')
      ? runner.features[0].replace('classpath:', 'app/src/test/resources/') : null,
      runnerClass: `${runner.pkg}.${runner.name}` }));
}

function oneTestClass(source) {
  const names = [...source.code.matchAll(/\b(?:class|object|interface|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)/g)].map((match) => match[1]);
  return source.name.endsWith("Test") && names.length === 1 && names[0] === source.name &&
    new RegExp(`^class\\s+${source.name}\\b`, "m").test(source.code);
}

// ponytail: package-level scans overestimate dependencies; use a compiler graph
// if source volume or production-impact analysis requires finer resolution.
// No production change is eligible: Compose/navigation, Hilt, DTOs/mappers,
// resources and device coverage retain their existing C/D/R boundaries.
function analyzeTree(files, featureFile, tree) {
  if (!tree.has(featureFile) || files.some((file) => !tree.has(file))) return full("SOURCE_ADDED_OR_REMOVED");
  if ([...tree.keys()].some((file) => /^app\/src\/test[^/]+\/.*\.(?:kt|java)$/.test(file))) return full("UNSUPPORTED_TEST_SOURCE_SET");
  if ([...tree].some(([file, code]) => file.endsWith(".gradle.kts") && /sourceSets|srcDirs?|testFixtures|apply\s*\(\s*from/.test(code))) return full("CUSTOM_TEST_TOPOLOGY");
  const sources = [...tree].filter(([file]) => /^app\/src\/test\/(?:java|kotlin)\/.*\.(?:kt|java)$/.test(file)).map(([file, source]) => kotlinFile(file, source));
  if (sources.some(({ code }) => /Class\.forName|loadClass\s*\(|ServiceLoader|(?:kotlin|java\.lang)\.reflect/.test(code))) return full("DYNAMIC_TEST_CONSUMERS");
  const runners = sources.map(runnerOptions).filter(Boolean);
  const target = featureFile.replace("app/src/test/resources/", "classpath:");
  const owners = runners.filter(({ features }) => features.some((value) => target === value || target.startsWith(value.replace(/\/$/, "") + "/")));
  if (owners.length !== 1 || owners[0].features.length !== 1 || owners[0].features[0] !== target || !oneTestClass(owners[0])) return full("RUNNER_NOT_UNIQUE_OR_EXACT");
  const runner = owners[0];
  const affected = new Set(sources.filter(({ file }) => files.includes(file)).map(({ pkg }) => pkg));
  let previousSize;
  do {
    previousSize = affected.size;
    for (const source of sources) {
      const references = source.code.replace(/[\s`]/g, "");
      if ([...affected].some((pkg) => references.includes(`${pkg}.`) || references.includes(`"${pkg}"`))) affected.add(source.pkg);
    }
  } while (affected.size !== previousSize);
  const classes = new Set([`${runner.pkg}.${runner.name}`]);
  for (const source of sources.filter(({ pkg }) => affected.has(pkg))) {
    if (source.file === runner.file) continue;
    if (runners.some((candidate) => candidate.file === source.file)) return full("SHARED_RUNNER_CONSUMER");
    const glueOwners = runners.filter(({ glue }) => glue.some((pkg) => source.pkg === pkg || source.pkg.startsWith(pkg + ".")));
    if (glueOwners.length) {
      if (glueOwners.length !== 1 || glueOwners[0] !== runner) return full("SHARED_GLUE_CONSUMER");
      // A JUnit test under glue still needs its own class filter.
      if (source.name.endsWith("Test") || /@Test\b|\bclass\s+\w+Test\b/.test(source.code)) {
        if (!oneTestClass(source)) return full("UNSUPPORTED_TEST_CLASS");
        classes.add(`${source.pkg}.${source.name}`);
      }
    } else {
      if (!oneTestClass(source)) return full("UNMAPPED_JVM_SUPPORT");
      classes.add(`${source.pkg}.${source.name}`);
    }
  }
  return { scope: "feature", reason: "ISOLATED_JVM_FEATURE", runnerClass: `${runner.pkg}.${runner.name}`, testClasses: [...classes].sort() };
}

export function analyzeFeatureGate({ files, featureFile, before, after }) {
  if (!FEATURE.test(featureFile) || featureFile.split("/").includes("..") || !files.length || files.some((file) =>
    file !== featureFile && (!TEST_SOURCE.test(file) || file.endsWith("CucumberTest.kt")))) return full("NON_ISOLATED_CHANGE");
  try {
    const oldResult = analyzeTree(files, featureFile, before);
    if (oldResult.scope !== "feature") return oldResult;
    const result = analyzeTree(files, featureFile, after);
    if (result.scope !== "feature") return result;
    if (result.runnerClass !== oldResult.runnerClass) return full("RUNNER_CHANGED");
    return { ...result, testClasses: [...new Set([...oldResult.testClasses, ...result.testClasses])].sort() };
  } catch (error) {
    return { ...full("UNSUPPORTED_OR_UNAVAILABLE_SOURCE"), detail: error.message };
  }
}

export function inspectFeatureGate({ repoRoot, snapshot, featureFile }) {
  try {
    return analyzeFeatureGate({
      files: snapshot.stagedFiles, featureFile,
      before: readFeatureGateTree(repoRoot, snapshot.headSha),
      after: readFeatureGateTree(repoRoot, snapshot.stagedTreeSha),
    });
  } catch {
    return full("GIT_TREE_UNAVAILABLE");
  }
}
