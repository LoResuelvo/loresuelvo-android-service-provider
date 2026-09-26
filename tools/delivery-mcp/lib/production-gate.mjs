import crypto from 'node:crypto';
import { readFeatureGateTree, analyzeFeatureGate, featureRunners } from './feature-gate.mjs';
import { parseAndroidSources } from './android-source-facts.mjs';
import { buildAndroidGraph, traceAndroidImpact } from './android-impact-graph.mjs';

const full = (reason, detail) => ({ scope: 'full', reason, ...(detail ? { detail } : {}), testClasses: [] });
const source = file => /^app\/src\/(?:main|test|androidTest)\/(?:java|kotlin)\/.+\.kt$/.test(file);
const resource = file => /^app\/src\/main\/res\/values(?:-[\w-]+)?\/[^/]+\.xml$/.test(file);
const boundary = file => /\/(?:di|navigation|platform)\//.test(file);

// Each source is parsed once per evaluation; neither worktree contents nor cached facts select a gate.
function parseTrees(repoRoot, before, after, parse) {
  const input = new Map();
  for (const [file, code] of before) if (file.endsWith('.kt') || file.endsWith('.xml')) input.set(`b/${file}`, code);
  for (const [file, code] of after) if ((file.endsWith('.kt') || file.endsWith('.xml')) && before.get(file) !== code) input.set(`a/${file}`, code);
  const facts = parse({ repoRoot, sources: input });
  const oldFacts = facts.filter(node => node.id.startsWith('b/')).map(node => ({ ...node, id: node.id.slice(2) }));
  const newFacts = [...oldFacts.filter(node => after.get(node.id.split('#')[0]) === before.get(node.id.split('#')[0])),
    ...facts.filter(node => node.id.startsWith('a/')).map(node => ({ ...node, id: node.id.slice(2) }))];
  return [oldFacts, newFacts];
}

export function analyzeProductionGate({ repoRoot, files, featureFile, before, after, features = [], sourceTopology = {}, parse = parseAndroidSources }) {
  const selected = features.find(feature => feature.featureFile === featureFile);
  const hosts = new Set(features.flatMap(feature => feature.integrationFiles));
  if (!selected || !files.length || files.some(file => file !== featureFile &&
      (!source(file) && !resource(file) || boundary(file) || hosts.has(file) || file.endsWith('CucumberTest.kt')))) return full('ANDROID_SHARED_OR_UNSUPPORTED_PATH');
  try {
    for (const tree of [before, after]) {
      for (const [file, hash] of Object.entries(sourceTopology)) {
        if (!tree.has(file) || crypto.createHash('sha256').update(tree.get(file)).digest('hex') !== hash) return full('ANDROID_SOURCE_TOPOLOGY_CHANGED');
      }
      if ([...tree].some(([file, code]) =>
          file.startsWith('buildSrc/') || /\.java$/.test(file) || /^app\/src\/(?!main\/|test\/|androidTest\/).*\.kt$/.test(file) ||
          (file.endsWith('.gradle.kts') && /sourceSets|srcDirs?|testFixtures|apply\s*\(\s*from/.test(code)))) return full('ANDROID_UNSUPPORTED_SOURCE_TOPOLOGY');
    }
    const runner = analyzeFeatureGate({ files: [featureFile], featureFile, before, after });
    if (runner.scope !== 'feature') return full(runner.reason);
    const facts = parseTrees(repoRoot, before, after, parse);
    if (facts.some(tree => tree.some(node => node.flags.includes('reflection') || node.references.includes('getIdentifier')))) return full('ANDROID_DYNAMIC_CONSUMERS');
    const graphs = facts.map(buildAndroidGraph);
    for (const file of files.filter(file => file.startsWith('app/src/main/'))) {
      const state = graph => graph.nodes.get(file)?.flags.find(flag => flag.startsWith('state_contract='));
      if (state(graphs[0]) !== state(graphs[1])) return full('ANDROID_SAVED_STATE_CONTRACT_CHANGED');
    }
    const impacts = graphs.map((graph, index) => traceAndroidImpact({ graph, other: graphs[1-index], files,
      features, runners: featureRunners(index ? after : before), featureFile }));
    const union = key => [...new Set(impacts.flatMap(impact => impact[key]))].sort();
    return { scope: 'production_feature', featureFile, reason: 'ANDROID_ISOLATED_FEATURE', runnerClass: runner.runnerClass,
      testClasses: [...new Set([runner.runnerClass, ...union('testClasses')])].sort(), deviceTestClasses: union('deviceTestClasses') };
  } catch (error) {
    return full(error.message.startsWith('ANDROID_') ? error.message : 'ANDROID_ANALYSIS_UNAVAILABLE', error.detail || error.message);
  }
}

export function inspectProductionGate({ repoRoot, snapshot, featureFile, features, sourceTopology }) {
  try {
    return analyzeProductionGate({ repoRoot, files: snapshot.stagedFiles, featureFile, features, sourceTopology,
      before: readFeatureGateTree(repoRoot, snapshot.headSha), after: readFeatureGateTree(repoRoot, snapshot.stagedTreeSha) });
  } catch (error) { return full('ANDROID_ANALYSIS_UNAVAILABLE', error.message); }
}
