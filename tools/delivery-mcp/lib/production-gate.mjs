import crypto from "node:crypto";
import { readFeatureGateTree, analyzeFeatureGate } from "./feature-gate.mjs";

export const sourceHash = (source) => crypto.createHash("sha256").update(source).digest("hex");
const full = (reason) => ({ scope: "full", reason, testClasses: [] });

// A bounded, reviewed UI pilot, not a Kotlin dependency analyzer. Unknown syntax,
// new dependencies/consumers, and drift in integration coverage retain Gate C.
export function productionContract(source, symbol) {
  const tokens = source.match(/\/\*[\s\S]*?\*\/|\/\/[^\n]*|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[A-Za-z_$][\w$]*|\d+(?:\.\d+)?|[^\s]/g) || [];
  if (tokens.some((token) => token.startsWith("/*") && token.slice(2).includes("/*"))) throw new Error("Nested comment");
  const code = tokens.filter((token) => !token.startsWith("//") && !token.startsWith("/*"));
  if (code.includes("`")) throw new Error("Unsupported identifier");
  const start = code.indexOf("fun");
  if (start < 0 || code[start + 1] !== symbol || code[start + 2] !== "(") throw new Error("Unsupported declaration");
  let end = start + 2;
  let depth = 0;
  do {
    if (code[end] === "(") depth++;
    if (code[end] === ")") depth--;
    end++;
  } while (depth && end < code.length);
  if (depth || code[end] !== "{") throw new Error("Unsupported function body");
  const signature = sourceHash(JSON.stringify(code.slice(0, end)));
  const vocabulary = sourceHash(JSON.stringify([...new Set(code.filter((token) => !/^\d/.test(token)))].sort()));
  for (let i = end; i < code.length; i++) {
    if (code[i] === "{") depth++;
    if (code[i] === "}") depth--;
    if (depth < 0 || (depth === 0 && i !== code.length - 1)) throw new Error("Additional declaration");
  }
  if (depth) throw new Error("Unbalanced body");
  return { signature, vocabulary };
}

export function analyzeProductionGate({ files, featureFile, before, after, scopes = [] }) {
  const rule = scopes.find((scope) => scope.featureFile === featureFile && files.includes(scope.sourceFile));
  if (!rule || files.some((file) => file !== rule.sourceFile && file !== featureFile)) return full("PRODUCTION_SCOPE_NOT_REVIEWED");
  try {
    if (rule.deviceTestClasses.length !== 1 || !rule.testClasses.length) return full("PRODUCTION_COVERAGE_MISSING");
    for (const tree of [before, after]) {
      if ([...tree].some(([file, source]) => /\.(?:kt|java)$/.test(file) &&
          (/Class\.forName|loadClass\(|ServiceLoader|(?:kotlin|java\.lang)\.reflect/.test(source.replace(/\s+/g, "")) ||
           /^app\/src\/(?!main\/|test\/|androidTest\/)/.test(file)))) return full("PRODUCTION_DYNAMIC_OR_VARIANT_SOURCE");
      if (!tree.has(rule.sourceFile)) return full("PRODUCTION_SOURCE_ADDED_OR_REMOVED");
      const contract = productionContract(tree.get(rule.sourceFile), rule.symbol);
      if (contract.signature !== rule.signature || contract.vocabulary !== rule.vocabulary) return full("PRODUCTION_CONTRACT_CHANGED");
      for (const [file, hash] of Object.entries(rule.pinnedFiles)) {
        if (!tree.has(file) || sourceHash(tree.get(file)) !== hash) return full("PRODUCTION_COVERAGE_DRIFT");
      }
      for (const [sourceSet, classes] of [["test", rule.testClasses], ["androidTest", rule.deviceTestClasses]]) {
        for (const name of classes) {
          const file = `app/src/${sourceSet}/java/${name.replaceAll(".", "/")}.kt`;
          if (!Object.hasOwn(rule.pinnedFiles, file) || !/@Test\b/.test(tree.get(file))) return full("PRODUCTION_COVERAGE_MISSING");
        }
      }
      const consumers = [...tree].filter(([file, source]) => file !== rule.sourceFile && source.includes(rule.symbol)).map(([file]) => file).sort();
      if (JSON.stringify(consumers) !== JSON.stringify([...rule.consumers].sort())) return full("PRODUCTION_CONSUMERS_CHANGED");
      if ([...tree].some(([file, source]) => file.startsWith("app/src/main/") && file !== rule.sourceFile &&
          rule.boundarySymbols.some((symbol) => source.includes(symbol)) && !Object.hasOwn(rule.pinnedFiles, file))) {
        return full("PRODUCTION_BOUNDARY_CONSUMERS_CHANGED");
      }
    }
    // The exact runner and all test consumers must remain understood in both trees.
    const impact = analyzeFeatureGate({ files: [featureFile], featureFile, before, after });
    if (impact.scope !== "feature") return full(impact.reason);
    return { ...impact, scope: "production_feature", reason: "REVIEWED_UI_FEATURE",
      testClasses: [...new Set([...impact.testClasses, ...rule.testClasses])].sort(),
      deviceTestClasses: rule.deviceTestClasses };
  } catch {
    return full("PRODUCTION_ANALYSIS_UNAVAILABLE");
  }
}

export function inspectProductionGate({ repoRoot, snapshot, featureFile, scopes }) {
  try {
    return analyzeProductionGate({ files: snapshot.stagedFiles, featureFile, scopes,
      before: readFeatureGateTree(repoRoot, snapshot.headSha),
      after: readFeatureGateTree(repoRoot, snapshot.stagedTreeSha) });
  } catch {
    return full("PRODUCTION_ANALYSIS_UNAVAILABLE");
  }
}
