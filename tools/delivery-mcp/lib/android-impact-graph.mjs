const sourceFile = node => node.id.split('#')[0];
const isProduction = node => sourceFile(node).startsWith('app/src/main/');
const isJvm = node => sourceFile(node).startsWith('app/src/test/');
const isDevice = node => sourceFile(node).startsWith('app/src/androidTest/');
const reject = (reason, detail) => { throw Object.assign(new Error(reason), { detail }); };

function add(index, key, value) {
  if (!index.has(key)) index.set(key, new Set());
  index.get(key).add(value);
}

// ponytail: simple-name edges overestimate impact; add semantic resolution only if measured fallbacks justify it.
export function buildAndroidGraph(facts) {
  const nodes = new Map(facts.map(node => [node.id, node]));
  const symbols = new Map(), consumers = new Map();
  for (const node of facts) {
    if (node.flags.includes('syntax_error') || node.flags.includes('unknown_declaration') || node.flags.includes('unknown_import')) reject('ANDROID_SOURCE_UNSUPPORTED');
    for (const declaration of node.declarations) {
      add(symbols, declaration, node.id);
      if (node.kind === 'kotlin') add(symbols, `${node.pkg}.${declaration}`, node.id);
    }
  }
  for (const node of facts) {
    const refs = node.kind === 'resource'
      ? node.references.flatMap(text => [...text.matchAll(/@(?:[A-Za-z0-9_.]+:)?(?:\+)?[A-Za-z_]+\/([A-Za-z0-9_]+)/g)].map(match => match[1]))
      : node.references;
    for (const reference of [...refs, ...node.imports.filter(name => !name.endsWith('.*'))]) {
      let name = reference;
      while (name) {
        for (const owner of symbols.get(name) || []) if (owner !== node.id) add(consumers, owner, node.id);
        if (!name.includes('.')) break;
        name = name.slice(0, name.lastIndexOf('.'));
      }
    }
    for (const wildcard of node.imports.filter(name => name.endsWith('.*'))) {
      const pkg = wildcard.slice(0,-2);
      for (const owner of facts.filter(candidate => candidate.pkg === pkg)) if (owner.id !== node.id) add(consumers, owner.id, node.id);
    }
    // Dynamic dispatch: a concrete implementation can affect consumers of its port.
    if (isProduction(node)) for (const parent of node.supers) {
      for (const owner of symbols.get(parent) || []) if (owner !== node.id && isProduction(nodes.get(owner))) add(consumers, node.id, owner);
    }
  }
  const dependencies = new Map();
  for (const [producer, users] of consumers) for (const user of users) add(dependencies, user, producer);
  return { nodes, symbols, consumers, dependencies };
}

function featureRoots(graph, features) {
  const owners = new Map();
  for (const feature of features) for (const name of feature.entryPoints) {
    const candidates = [...(graph.symbols.get(name) || [])].filter(id => isProduction(graph.nodes.get(id)));
    if (candidates.length > 1) reject('ANDROID_AMBIGUOUS_ENTRY_POINT');
    for (const candidate of candidates) add(owners, candidate, feature.featureFile);
  }
  return owners;
}

function changedNodes(files, graph, other) {
  const seeds = [];
  for (const file of files) {
    if (file.endsWith('.feature')) continue;
    for (const node of graph.nodes.values()) {
      if (sourceFile(node) !== file) continue;
      if (node.kind === 'resource' && other.nodes.get(node.id)?.pkg === node.pkg) continue;
      seeds.push(node.id);
    }
  }
  return seeds;
}

function traceSeed({ graph, other, files, features, runners, featureFile, seed }) {
  const roots = featureRoots(graph, features);
  const selected = features.find(feature => feature.featureFile === featureFile);
  if (!selected || !selected.entryPoints.length || !selected.deviceTestClasses.length) reject('ANDROID_FEATURE_COVERAGE_MISSING');
  if (selected.entryPoints.some(name => !(graph.symbols.get(name)?.size))) reject('ANDROID_ENTRY_POINT_MISSING');
  const owners = new Set(), jvmClasses = new Set(), deviceClasses = new Set();
  const reached = new Set(), queue = [seed];
  const allowedHosts = new Set(selected.integrationFiles);
  for (let i=0; i<queue.length; i++) {
    const id = queue[i];
    if (reached.has(id)) continue;
    reached.add(id);
    const node = graph.nodes.get(id);
    if (node.flags.some(flag => ['implicit_calls','reflection','dynamic_member','unsupported_resource','unsupported_test'].includes(flag))) reject('ANDROID_IMPACT_UNSUPPORTED');
    const nodeOwners = roots.get(id) || new Set();
    for (const owner of nodeOwners) owners.add(owner);
    if (isJvm(node)) {
      for (const name of node.tests) jvmClasses.add(name);
      const glueOwners = runners.filter(runner => runner.glue.some(pkg => node.pkg === pkg || node.pkg.startsWith(pkg+'.')));
      for (const runner of glueOwners) owners.add(runner.featureFile);
    }
    if (isDevice(node)) {
      for (const name of node.tests) deviceClasses.add(name);
      if (files.includes(sourceFile(node))) {
        for (const feature of features) if (node.tests.some(name => feature.deviceTestClasses.includes(name))) owners.add(feature.featureFile);
      }
    }
    const next = [...(graph.consumers.get(id) || [])].filter(consumer => {
      const target = graph.nodes.get(consumer);
      if (isProduction(target) && target.flags.includes('binds_module')) return false; // Inheritance edges already include the bound ports.
      return !(nodeOwners.has(featureFile) && allowedHosts.has(sourceFile(target)));
    });
    if (isProduction(node) && !nodeOwners.size && !next.length) reject('ANDROID_UNOWNED_PRODUCTION');
    if (isProduction(node) && (node.flags.includes('dynamic_module') || /\/(?:di|navigation|platform)\//.test(sourceFile(node)))) reject('ANDROID_SHARED_BOUNDARY', id);
    if (!isProduction(node) && !next.length && !node.tests.length &&
        !runners.some(runner => runner.glue.some(pkg => node.pkg === pkg || node.pkg.startsWith(pkg+'.')))) reject('ANDROID_UNMAPPED_TEST_SUPPORT');
    queue.push(...next);
  }
  // A changed JVM test must also be attributable when its references run toward production.
  if (isJvm(graph.nodes.get(seed)) && !owners.size) {
    const visited = new Set(), pending = [seed];
    for (let i = 0; i < pending.length; i++) {
      const id = pending[i];
      if (visited.has(id)) continue;
      visited.add(id);
      for (const owner of roots.get(id) || []) owners.add(owner);
      pending.push(...(graph.dependencies.get(id) || []));
    }
  }
  if (owners.size !== 1 || !owners.has(featureFile)) reject('ANDROID_SHARED_OR_UNKNOWN_FEATURE');
  for (const name of selected.deviceTestClasses) {
    if (![...graph.nodes.values()].some(node => isDevice(node) && node.tests.includes(name))) reject('ANDROID_DEVICE_COVERAGE_MISSING');
    deviceClasses.add(name);
  }
  if (!deviceClasses.size) reject('ANDROID_DEVICE_COVERAGE_MISSING');
  return { reached: [...reached].sort(), testClasses: [...jvmClasses].sort(), deviceTestClasses: [...deviceClasses].sort() };
}

export function traceAndroidImpact(input) {
  const results = changedNodes(input.files, input.graph, input.other).map(seed => traceSeed({ ...input, seed }));
  if (!results.length) reject('ANDROID_NO_PROVEN_IMPACT');
  const union = key => [...new Set(results.flatMap(result => result[key]))].sort();
  return { reached: union('reached'), testClasses: union('testClasses'), deviceTestClasses: union('deviceTestClasses') };
}
