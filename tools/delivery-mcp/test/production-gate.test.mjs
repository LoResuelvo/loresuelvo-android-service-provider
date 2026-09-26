import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { analyzeProductionGate } from '../lib/production-gate.mjs';
import { buildAndroidGraph, traceAndroidImpact } from '../lib/android-impact-graph.mjs';
import { parseAndroidSources } from '../lib/android-source-facts.mjs';
import { readFeatureGateTree } from '../lib/feature-gate.mjs';
import { loadDeliveryPolicy } from '../lib/policy-loader.mjs';
import { selectGate } from '../lib/select-gate.mjs';
import { resolveCheck, executeFeatureDeviceCheck } from '../lib/execute-check.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const featureFile = 'app/src/test/resources/features/example.feature';
const sourceFile = 'app/src/main/java/example/ui/Screen.kt';
const helper = 'app/src/main/java/example/Helper.kt';
const host = 'app/src/main/java/example/navigation/Host.kt';
const unit = 'app/src/test/java/example/ScreenTest.kt';
const device = 'app/src/androidTest/java/example/ScreenDeviceTest.kt';
const node = (id, name, references = [], extra = {}) => ({ id, kind: 'kotlin', pkg: 'example', declarations: [name],
  references, imports: [], tests: [], supers: [], flags: [], ...extra });
function fixture() {
  const facts = [node(sourceFile, 'Screen', ['Helper']), node(helper, 'Helper'), node(host, 'Host', ['Screen']),
    node(unit, 'ScreenTest', ['Screen'], { tests: ['example.ScreenTest'] }),
    node(device, 'ScreenDeviceTest', ['Host'], { tests: ['example.ScreenDeviceTest'] })];
  const features = [{ featureFile, entryPoints: ['example.Screen'], integrationFiles: [host], deviceTestClasses: ['example.ScreenDeviceTest'] }];
  return { facts, features };
}
function trace(data, files = [helper, unit]) {
  const graph = buildAndroidGraph(data.facts);
  return traceAndroidImpact({ graph, other: graph, files, features: data.features, runners: [], featureFile });
}

test('production and test changes use feature ownership rather than file-body hashes; D/R remain full', async () => {
  const impact = { ...trace(fixture()), featureFile, scope: 'production_feature', reason: 'ANDROID_ISOLATED_FEATURE', runnerClass: 'example.ExampleCucumberTest' };
  assert.deepEqual(impact.testClasses, ['example.ScreenTest']);
  assert.deepEqual(impact.deviceTestClasses, ['example.ScreenDeviceTest']);
  const policy = await loadDeliveryPolicy({ repoRoot: ROOT });
  for (const [intent, expected] of [['close_scenario', 'B'], ['prepare_commit', 'C'], ['close_batch', 'D'], ['close_us', 'D'], ['repair_ci', 'R']]) {
    const result = selectGate({ policy, intent, featureFile, repairsSha: 'a'.repeat(40), snapshot: { stagedFiles: [sourceFile, unit] }, dependencyImpact: impact });
    assert.equal(result.gate.id, expected);
    assert.deepEqual(result.gate.checkIds, expected === 'B' ? ['android_device', 'lint_dev', 'feature_jvm_dev', 'feature_device_dev'] : policy.gates[expected].checkIds);
    assert.deepEqual(result.gate.postPushChecks, policy.gates[expected].postPushChecks);
  }
  const inferred = selectGate({ policy, intent: 'close_scenario', snapshot: { stagedFiles: [sourceFile, featureFile] }, dependencyImpact: impact });
  assert.equal(inferred.gate.parameters.featureFile, featureFile);
});

test('shared consumers, unknown cycles, unsupported syntax, DI, navigation and missing coverage fail closed', () => {
  const cases = [
    d => { d.facts.push(node('app/src/main/java/example/Other.kt', 'Other', ['Helper'])); d.features.push({ ...d.features[0], featureFile: 'other.feature', entryPoints: ['example.Other'] }); },
    d => d.facts.push(node('app/src/main/java/example/Unknown.kt', 'Unknown', ['Helper'])),
    d => { d.facts[1].references = ['Cycle']; d.facts[0].references = []; d.facts.push(node('app/src/main/java/example/Cycle.kt', 'Cycle', ['Helper'])); },
    d => d.facts[1].flags.push('syntax_error'),
    d => d.facts[1].flags.push('implicit_calls'),
    d => d.facts.push(node('app/src/main/java/example/di/Module.kt', 'Module', ['Helper'], { flags: ['dynamic_module'] })),
    d => d.facts[2].references.push('Helper'),
    d => { d.features[0].deviceTestClasses = []; },
    d => { d.features[0].deviceTestClasses = ['example.MissingTest']; },
    d => { d.facts[3].references = []; },
  ];
  for (const mutate of cases) { const d = fixture(); mutate(d); assert.throws(() => trace(d), /ANDROID_/, String(mutate)); }
});

test('resource entries, alias imports, inheritance and shared Cucumber glue retain consumers', () => {
  const d = fixture();
  const xml = 'app/src/main/res/values/strings.xml';
  d.facts.push(node(xml + '#string/title', 'title', [], { kind: 'resource', pkg: 'new title' }));
  d.facts[0].references.push('title');
  let graph = buildAndroidGraph(d.facts);
  const other = buildAndroidGraph(d.facts.map(n => n.id.startsWith(xml) ? { ...n, pkg: 'old title' } : n));
  assert.equal(traceAndroidImpact({ graph, other, files: [xml], features: d.features, runners: [], featureFile }).deviceTestClasses.length, 1);
  d.facts[0].references = ['Alias']; d.facts[0].imports = ['example.Helper.Nested'];
  assert.equal(trace(d, [helper]).testClasses.length, 1);
  d.facts.push(node('app/src/main/java/example/Impl.kt', 'Impl', ['Helper'], { supers: ['Helper'] }));
  assert.equal(trace(d, ['app/src/main/java/example/Impl.kt']).testClasses.length, 1);
  d.facts.push(node('app/src/test/java/example/shared/Steps.kt', 'Steps', ['Helper'], { pkg: 'example.shared' }));
  graph = buildAndroidGraph(d.facts);
  assert.throws(() => traceAndroidImpact({ graph, other: graph, files: [helper], features: d.features,
    runners: [{ featureFile, glue: ['example.shared'] }, { featureFile: 'other.feature', glue: ['example.shared'] }], featureFile }), /ANDROID_SHARED/);
});

test('device selector requires exact classes and falls back on empty successful execution only', async () => {
  const policy = await loadDeliveryPolicy({ repoRoot: ROOT });
  const check = resolveCheck({ checkId: 'feature_device_dev', definition: policy.checkCatalog.feature_device_dev,
    parameters: { deviceTestClasses: ['example.DialogDeviceTest'] }, repoRoot: ROOT });
  for (const invalid of ['*', 'example.Test;echo', '../Test', 'example.Test#method', 'example.Test,example.OtherTest']) {
    assert.throws(() => resolveCheck({ checkId: check.id, definition: policy.checkCatalog.feature_device_dev,
      parameters: { deviceTestClasses: [invalid] }, repoRoot: ROOT }), /exact test classes/);
  }
  for (const [status, output, expectedCalls] of [['passed', 'Finished 3 tests on device', 1], ['passed', 'Finished 0 tests on device', 2], ['passed', 'BUILD SUCCESSFUL', 2], ['failed', 'Assertion failed', 1]]) {
    const calls = [];
    const result = await executeFeatureDeviceCheck({ check, repoRoot: ROOT, logPath: 'unused', execute: async ({ check: command }) => {
      calls.push(command); return { status, rawOutput: output, durationMs: 7 };
    } });
    assert.equal(calls.length, expectedCalls);
    assert.equal(result.durationMs, expectedCalls * 7);
    assert.deepEqual(calls[0].args, ['./gradlew', ':app:connectedDevDebugAndroidTest', '-Pandroid.testInstrumentationRunnerArguments.class=example.DialogDeviceTest']);
    if (expectedCalls === 2) assert.deepEqual(calls[1].args, ['e2e', 'FLAVOR=Dev']);
    if (status === 'failed') assert.equal(result.status, 'failed');
  }
});


test('every selected device class needs positive execution; failures do not become fallback passes', async () => {
  const policy = await loadDeliveryPolicy({ repoRoot: ROOT });
  const check = resolveCheck({ checkId: 'feature_device_dev', definition: policy.checkCatalog.feature_device_dev,
    parameters: { deviceTestClasses: ['example.FirstTest', 'example.SecondTest'] }, repoRoot: ROOT });
  const calls = [];
  const result = await executeFeatureDeviceCheck({ check, repoRoot: ROOT, logPath: 'unused', execute: async ({ check: command }) => {
    calls.push(command); return { status: 'passed', rawOutput: calls.length === 2 ? 'Finished 0 tests on device' : 'Finished 2 tests on device', durationMs: 1 };
  } });
  assert.equal(calls.length, 3);
  assert.deepEqual(calls[2].args, ['e2e', 'FLAVOR=Dev']);
  assert.equal(result.durationMs, 3);
});

test('parser unavailability is C, never isolated impact', () => {
  const runner = 'app/src/test/java/example/ExampleCucumberTest.kt';
  const tree = new Map([[featureFile, 'Feature: Example\n Scenario: Runs\n  Given example\n'],
    [sourceFile, 'package example\nclass Screen'],
    [runner, 'package example\n@RunWith(Cucumber::class)\n@CucumberOptions(features = ["classpath:features/example.feature"], glue = ["example.steps"])\nclass ExampleCucumberTest']]);
  const result = analyzeProductionGate({ repoRoot: ROOT, before: tree, after: tree, files: [sourceFile], featureFile,
    features: fixture().features, parse: () => { throw new Error('Parser unavailable'); } });
  assert.equal(result.scope, 'full');
  assert.equal(result.reason, 'ANDROID_ANALYSIS_UNAVAILABLE');
});

// This integration proof uses the installed compiler, never downloads a parser for Node-only CI.
test('real Kotlin graph isolates mixed feature edits and preserves the US-53 regression corpus', async t => {
  let probe;
  try { probe = parseAndroidSources({ repoRoot: ROOT, sources: new Map([['Probe.kt', 'package example\nimport example.Port as Alias\nclass Probe : Alias\nprivate fun Hidden() = 1']]) }); }
  catch (error) {
    if (error.code === 'ENOENT') { t.skip('Installed Kotlin compiler unavailable; production selection fails closed to C'); return; }
    throw error;
  }
  assert.deepEqual(probe[0].declarations, ['Probe']);
  assert.ok(probe[0].supers.includes('example.Port'));
  const reflected = parseAndroidSources({ repoRoot: ROOT, sources: new Map([['Reflection.kt', 'package example\nimport java.lang.Class.forName as hidden\nfun dynamic() = hidden("example.Probe")']]) });
  assert.ok(reflected[0].flags.includes('reflection'));
  const policy = await loadDeliveryPolicy({ repoRoot: ROOT });
  const features = policy.analysis.dependencyImpact.features;
  const git = (...args) => execFileSync('git', args, { cwd: ROOT, encoding: 'utf8', stdio: ['ignore','pipe','pipe'] }).trim();
  const before = readFeatureGateTree(ROOT, git('rev-parse', 'HEAD'));
  const scenarios = [];
  for (const [featureIndex, stem] of [[0, 'ui/screens/conversation/ProviderProposalViewModel'], [3, 'ui/screens/messages/MessagesListViewModel']]) {
    const file = `app/src/main/java/com/loresuelvo/serviceprovider/${stem}.kt`;
    const testFile = `app/src/test/java/com/loresuelvo/serviceprovider/${stem}Test.kt`;
    const after = new Map(before); after.set(file, before.get(file) + '\n// reviewed implementation change\n');
    after.set(testFile, before.get(testFile) + '\n// companion test change\n');
    scenarios.push({ before, after, files: [file, testFile], featureFile: features[featureIndex].featureFile, gate: 'B' });
  }
  const proposal = 'app/src/main/java/com/loresuelvo/serviceprovider/ui/screens/conversation/ProviderProposalViewModel.kt';
  const stateChange = new Map(before);
  stateChange.set(proposal, before.get(proposal).replaceAll('proposal_rejected', 'proposal_rejected_v2'));
  scenarios.push({ before, after: stateChange, files: [proposal], featureFile: features[0].featureFile,
    gate: 'C', reason: 'ANDROID_SAVED_STATE_CONTRACT_CHANGED' });
  const viewFeature = 'app/src/test/resources/features/proposals/view-service-proposals.feature';
  const viewRecord = features.find(feature => feature.featureFile === viewFeature);
  assert.ok(viewRecord);
  const viewScreen = 'app/src/main/java/com/loresuelvo/serviceprovider/ui/screens/proposals/ServiceProposalListScreen.kt';
  const viewDevice = 'com.loresuelvo.serviceprovider.acceptance.proposals.ServiceProposalNavigationAcceptanceTest';
  assert.ok(before.has('app/src/test/java/com/loresuelvo/serviceprovider/bdd/proposals/view/ViewServiceProposalsCucumberTest.kt'));
  assert.ok(before.has('app/src/androidTest/java/com/loresuelvo/serviceprovider/acceptance/proposals/ServiceProposalNavigationAcceptanceTest.kt'));
  assert.deepEqual(viewRecord.deviceTestClasses, [viewDevice]);
  const changed = file => { const after = new Map(before); after.set(file, before.get(file) + '\n// reviewed implementation change\n'); return after; };
  for (const file of [
    viewScreen,
    'app/src/main/java/com/loresuelvo/serviceprovider/ui/proposals/ServiceProposalListViewModel.kt',
  ]) scenarios.push({ before, after: changed(file), files: [file], featureFile: viewFeature,
    gate: 'B', deviceTestClasses: [viewDevice] });
  for (const file of [
    'app/src/main/java/com/loresuelvo/serviceprovider/ui/screens/home/ProviderHomeScreen.kt',
    'app/src/main/java/com/loresuelvo/serviceprovider/ui/navigation/LoResuelvoNavHost.kt',
    'app/src/main/java/com/loresuelvo/serviceprovider/di/NetworkModule.kt',
  ]) scenarios.push({ before, after: changed(file), files: [file], featureFile: viewFeature, gate: 'C' });
  scenarios.push({ before, after: changed(viewScreen), files: [viewScreen], featureFile: viewFeature,
    features: features.map(feature => feature.featureFile === viewFeature ? { ...feature, deviceTestClasses: [] } : feature),
    gate: 'C', reason: 'ANDROID_FEATURE_COVERAGE_MISSING' });
  const corpus = JSON.parse(await fs.readFile(new URL('./fixtures/us53-gate-paths.json', import.meta.url)));
  for (const entry of corpus.filter(e => e.gate !== 'B')) {
    let old = before, after = before;
    try { old = readFeatureGateTree(ROOT, git('rev-parse', `${entry.sha}^`)); after = readFeatureGateTree(ROOT, git('rev-parse', entry.sha)); }
    catch { /* A shallow checkout still validates conservative path handling. */ }
    scenarios.push({ ...entry, before: old, after, featureFile: features[0].featureFile });
  }
  // Deduplicate immutable syntax across snapshots; every scenario still rebuilds both graphs.
  const sources = new Map(), keys = new Map();
  for (const scenario of scenarios) for (const tree of [scenario.before, scenario.after]) for (const [file, code] of tree) {
    if (!/\.(kt|xml)$/.test(file)) continue;
    const key = file + '\0' + code;
    if (!keys.has(key)) { const id = `${keys.size}/${file}`; keys.set(key, id); sources.set(id, code); }
  }
  const parsed = parseAndroidSources({ repoRoot: ROOT, sources });
  const byFile = new Map();
  for (const fact of parsed) { const key = fact.id.split('#')[0]; if (!byFile.has(key)) byFile.set(key, []); byFile.get(key).push(fact); }
  const parse = ({ sources }) => [...sources].flatMap(([id, code]) => {
    const file = id.slice(2), key = keys.get(file + '\0' + code);
    return (byFile.get(key) || []).map(fact => ({ ...fact, id: id + fact.id.slice(key.length) }));
  });
  for (const scenario of scenarios) {
    const impact = analyzeProductionGate({ repoRoot: ROOT, ...scenario, features: scenario.features || features,
      sourceTopology: policy.analysis.dependencyImpact.sourceTopology, parse });
    if (scenario.reason) assert.equal(impact.reason, scenario.reason);
    if (scenario.deviceTestClasses) {
      assert.deepEqual(impact.deviceTestClasses, scenario.deviceTestClasses);
      assert.equal(impact.runnerClass, 'com.loresuelvo.serviceprovider.bdd.proposals.view.ViewServiceProposalsCucumberTest');
    }
    const result = selectGate({ policy, snapshot: { stagedFiles: scenario.files }, intent: scenario.gate === 'R' ? 'repair_ci' : 'close_scenario',
      featureFile: scenario.featureFile, repairsSha: 'a'.repeat(40), dependencyImpact: impact });
    assert.equal(result.gate.id, scenario.gate, `${scenario.sha || scenario.files[0]}: ${JSON.stringify(impact)}`);
    if (scenario.deviceTestClasses) for (const intent of ['close_batch', 'close_us', 'repair_ci']) {
      const fullGate = selectGate({ policy, snapshot: { stagedFiles: scenario.files }, intent,
        featureFile: viewFeature, repairsSha: 'a'.repeat(40), dependencyImpact: impact });
      assert.equal(fullGate.gate.id, intent === 'repair_ci' ? 'R' : 'D');
    }
  }
});
