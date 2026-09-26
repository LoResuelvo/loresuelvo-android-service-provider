import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { execFileSync } from 'node:child_process';

function jar(cache, group, artifact, version) {
  const folder = path.join(cache, group, artifact, version);
  const found = fs.readdirSync(folder).flatMap(hash => fs.readdirSync(path.join(folder, hash))
    .filter(name => name === `${artifact}-${version}.jar`).map(name => path.join(folder, hash, name)));
  if (found.length !== 1) throw new Error(`Unavailable Kotlin parser dependency: ${artifact}`);
  return found[0];
}

export function parseAndroidSources({ repoRoot, sources }) {
  const version = fs.readFileSync(path.join(repoRoot, 'gradle/libs.versions.toml'), 'utf8').match(/^kotlin\s*=\s*"([0-9.]+)"/m)?.[1];
  if (version !== '2.0.21') throw new Error('Unsupported Kotlin compiler version');
  const cache = path.join(process.env.GRADLE_USER_HOME || path.join(os.homedir(), '.gradle'), 'caches/modules-2/files-2.1');
  const dependencies = [
    ['org.jetbrains.kotlin','kotlin-compiler-embeddable',version], ['org.jetbrains.kotlin','kotlin-stdlib',version],
    ['org.jetbrains.kotlin','kotlin-script-runtime',version], ['org.jetbrains.kotlin','kotlin-reflect','1.6.10'],
    ['org.jetbrains.intellij.deps','trove4j','1.0.20200330'], ['org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.6.4'],
  ].map(args => jar(cache, ...args));
  const encode = value => Buffer.from(value).toString('base64');
  const input = [...sources].map(([file, source]) => `${encode(file)}\t${encode(source)}`).join('\n') + '\n';
  const output = execFileSync('scripts/with-android-env.sh', ['java', '-Xmx512m', '-cp', dependencies.join(path.delimiter),
    'tools/delivery-mcp/android/KotlinImpact.java'], { cwd: repoRoot, input, encoding: 'utf8', timeout: 30000, maxBuffer: 32 * 1024 * 1024 });
  return output.split(/\r?\n/).filter(Boolean).map(line => {
    const fields = line.split('\t').map(value => Buffer.from(value,'base64').toString('utf8'));
    if (fields.length !== 9) throw new Error('Invalid Kotlin parser response');
    const [id, kind, pkg, ...lists] = fields;
    const [declarations, references, imports, tests, supers, flags] = lists.map(value => value ? value.split('\n') : []);
    return { id, kind, pkg, declarations, references, imports, tests, supers, flags };
  });
}
