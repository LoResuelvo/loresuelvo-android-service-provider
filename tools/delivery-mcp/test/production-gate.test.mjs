import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { productionContract, sourceHash, analyzeProductionGate } from '../lib/production-gate.mjs';
import { readFeatureGateTree } from '../lib/feature-gate.mjs';
import { loadDeliveryPolicy } from '../lib/policy-loader.mjs';
import { selectGate } from '../lib/select-gate.mjs';
import { inspectDelivery } from '../lib/inspect-delivery.mjs';
import { runGate } from '../lib/run-gate.mjs';
import { resolveCheck, executeFeatureDeviceCheck } from '../lib/execute-check.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const featureFile = 'app/src/test/resources/features/example.feature';
const sourceFile = 'app/src/main/java/example/ui/Dialog.kt';
const caller = 'app/src/main/java/example/ui/Route.kt';
const unit = 'app/src/test/java/example/DialogTest.kt';
const runner = 'app/src/test/java/example/bdd/ExampleCucumberTest.kt';
const device = 'app/src/androidTest/java/example/DialogDeviceTest.kt';
function fixture() {
  const before = new Map([
    [featureFile, 'Feature: Example\n Scenario: Show\n  Given dialog\n'],
    [sourceFile, 'package example.ui\n@Composable\nfun Dialog(enabled: Boolean = true) { Text(size = 8) }\n'],
    [caller, 'package example.ui\nfun Route() { Dialog() }\n'],
    [unit, 'package example\nclass DialogTest { @Test fun shows() { Dialog() } }\n'],
    [runner, 'package example.bdd\n@RunWith(Cucumber::class)\n@CucumberOptions(features = ["classpath:features/example.feature"], glue = ["example.bdd"])\nclass ExampleCucumberTest\n'],
    [device, 'package example\nclass DialogDeviceTest { @Test fun shows() { Route() } }\n'],
  ]);
  const rule = { featureFile, sourceFile, symbol: 'Dialog', ...productionContract(before.get(sourceFile), 'Dialog'),
    consumers: [caller, unit, device], boundarySymbols: ['Route'], testClasses: ['example.DialogTest'],
    deviceTestClasses: ['example.DialogDeviceTest'], pinnedFiles: Object.fromEntries([caller, unit, runner, device].map(file => [file, sourceHash(before.get(file))])) };
  const after = new Map(before);
  after.set(sourceFile, before.get(sourceFile).replace('size = 8', 'size = 10'));
  return { before, after, rule };
}
const analyze = ({ before, after, rule }, files = [sourceFile]) => analyzeProductionGate({ before, after, scopes: [rule], files, featureFile });

test('reviewed production body uses scoped JVM and device checks, while D/R remain full', async () => {
  const data = fixture();
  const impact = analyze(data);
  assert.equal(impact.scope, 'production_feature');
  const policy = await loadDeliveryPolicy({ repoRoot: ROOT });
  for (const [intent, expected] of [['close_scenario', 'B'], ['prepare_commit', 'C'], ['close_batch', 'D'], ['close_us', 'D'], ['repair_ci', 'R']]) {
    const result = selectGate({ policy, intent, featureFile, repairsSha: 'a'.repeat(40), snapshot: { stagedFiles: [sourceFile] }, dependencyImpact: impact });
    assert.equal(result.gate.id, expected);
    assert.deepEqual(result.gate.checkIds, expected === 'B'
      ? ['android_device', 'lint_dev', 'feature_jvm_dev', 'feature_device_dev'] : policy.gates[expected].checkIds);
    assert.deepEqual(result.gate.postPushChecks, policy.gates[expected].postPushChecks);
  }
});

test('production pilot fails closed on API, dependency, ownership, coverage and unsupported changes', () => {
  const changes = [
    data => data.after.set(sourceFile, data.after.get(sourceFile).replace('Boolean', 'String')),
    data => data.after.set(sourceFile, data.after.get(sourceFile) + '\nfun Other() {}'),
    data => data.after.set(sourceFile, data.after.get(sourceFile).replace('Text(size = 10)', 'Navigation.navigate("elsewhere")')),
    data => data.after.set(sourceFile, data.after.get(sourceFile).replace('Text(size = 10)', 'Text(size = 10) /* nested /* comment */ */')),
    data => data.after.set(caller, data.after.get(caller) + '// changed boundary'),
    data => data.after.set(device, data.after.get(device) + '// changed coverage'),
    data => data.after.set('app/src/main/java/example/Shared.kt', 'fun Shared() { Dialog() }'),
    data => data.before.set('app/src/main/java/example/Removed.kt', 'fun Shared() { Dialog() }'),
    data => data.after.set('app/src/main/java/example/NewEntry.kt', 'fun Entry() { Route() }'),
    data => data.after.set('app/src/main/java/example/Dynamic.kt', 'Class.forName("example.ui.DialogKt")'),
    data => data.after.set('app/src/dev/java/example/Variant.kt', 'fun variant() = 1'),
    data => data.after.delete(sourceFile),
    data => data.before.delete(sourceFile),
    data => data.after.delete(runner),
  ];
  for (const change of changes) {
    const data = fixture(); change(data);
    assert.equal(analyze(data).scope, 'full', String(change));
  }
  for (const extra of ['app/src/main/java/example/ui/CommonViewModel.kt', 'app/src/main/java/example/di/Bindings.kt',
    'app/src/main/java/example/data/Dto.kt', 'app/src/main/java/example/data/Mapper.kt', 'app/src/main/res/values/strings.xml',
    'app/src/main/java/example/ui/Navigation.kt', 'app/build.gradle.kts', device, unit]) {
    assert.equal(analyze(fixture(), [sourceFile, extra]).scope, 'full', extra);
  }
});

test('production selection uses staged Git trees and blocks dirty worktrees', async t => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'android-production-scope-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const git = (...args) => execFileSync('git', args, { cwd: root, stdio: 'pipe' });
  const data = fixture();
  git('init'); git('config', 'user.name', 'Delivery Tests'); git('config', 'user.email', 'tests@example.com'); git('config', 'commit.gpgsign', 'false');
  await fs.cp(path.join(ROOT, '.delivery/schemas'), path.join(root, '.delivery/schemas'), { recursive: true });
  const policy = JSON.parse(await fs.readFile(path.join(ROOT, '.delivery/policy.v1.json'), 'utf8'));
  policy.analysis.dependencyImpact.scopes = [data.rule];
  await fs.writeFile(path.join(root, '.delivery/policy.v1.json'), JSON.stringify(policy));
  await fs.writeFile(path.join(root, '.gitignore'), '.delivery/runtime/\n');
  for (const [file, source] of data.before) {
    await fs.mkdir(path.dirname(path.join(root, file)), { recursive: true });
    await fs.writeFile(path.join(root, file), source);
  }
  git('add', '.'); git('commit', '-m', 'test[53]: establish impact fixture');
  await fs.writeFile(path.join(root, sourceFile), data.after.get(sourceFile)); git('add', sourceFile);
  const input = { repoRoot: root, intent: 'close_scenario', featureFile };
  const inspection = await inspectDelivery(input);
  assert.equal(inspection.result.gate.id, 'B');
  const calls = [];
  const blocked = await runGate({ inspection: inspection.result, snapshot: { ...inspection.snapshot, cacheable: false },
    policy: inspection.policy, repoRoot: root, executeCheck: async ({ check }) => {
      calls.push(check.id); return { id: check.id, status: 'blocked', durationMs: 0, summaryLines: ['Device unavailable'] };
    } });
  assert.equal(blocked.status, 'blocked');
  assert.deepEqual(calls, ['android_device']);
  await fs.appendFile(path.join(root, sourceFile), '// unstaged\n');
  assert.equal((await inspectDelivery(input)).result.status, 'blocked');
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

test('US-53 production changes retain historical high-risk and repair gates', async () => {
  const policy = await loadDeliveryPolicy({ repoRoot: ROOT });
  const corpus = JSON.parse(await fs.readFile(new URL('./fixtures/us53-gate-paths.json', import.meta.url)));
  const git = (...args) => execFileSync('git', args, { cwd: ROOT, encoding: 'utf8' }).trim();
  const current = readFeatureGateTree(ROOT, git('rev-parse', 'HEAD'));
  for (const { sha, gate, files } of corpus.filter(entry => entry.gate !== 'B')) {
    let before = current; let after = current;
    try { before = readFeatureGateTree(ROOT, git('rev-parse', `${sha}^`)); after = readFeatureGateTree(ROOT, git('rev-parse', sha)); } catch { /* Shallow checkout: retain path assertions. */ }
    const impact = analyzeProductionGate({ before, after, files, featureFile: policy.analysis.dependencyImpact.scopes[0].featureFile, scopes: policy.analysis.dependencyImpact.scopes });
    const result = selectGate({ policy, intent: gate === 'R' ? 'repair_ci' : 'close_scenario', featureFile,
      snapshot: { stagedFiles: files }, repairsSha: 'a'.repeat(40), dependencyImpact: impact });
    assert.equal(result.gate.id, gate, sha);
  }
});
