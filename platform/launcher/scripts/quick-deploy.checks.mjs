import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { canonicalBytes, digest } from './release-common.mjs';
import { checkEnvelope, checkPlan, nextVersion, replaceMod, main } from './quick-deploy.mjs';

test('explicit modpack update allows only pinned additions and removals', () => {
  const hash='a'.repeat(64), extra='b'.repeat(64);
  const pack={install:[{name:'shared.jar',sha256:extra,server:true}],clientRemove:[{name:'justzoom.jar',sha256:hash}],serverRemove:[]};
  const plan={client:{added:['instances/alpha/mods/shared.jar'],changed:['launch.json'],removed:['instances/alpha/mods/justzoom.jar']},server:{files:[{path:'mods/createcityexpansioncore-1.0.2.jar',sha256:hash},{path:'mods/shared.jar',sha256:extra}],remove:[]}};
  checkPlan(plan,hash,pack);
  assert.throws(()=>checkPlan(plan,hash));
  for(const change of [p=>p.client.changed.push('instances/alpha/config/foo.toml'),p=>p.client.removed.push('instances/alpha/mods/create.jar'),p=>p.server.files[1].sha256=hash,p=>p.server.files.push({path:'mods/clientonly.jar',sha256:extra}),p=>p.server.remove.push({path:'mods/foo.jar',expectedSha256:hash})]) {
    const bad=structuredClone(plan);change(bad);assert.throws(()=>checkPlan(bad,hash,pack));
  }
});

test('next version is derived from signed current release, not a fixed historical version', () => {
  assert.equal(nextVersion('alpha-0.1.41'), 'alpha-0.1.42');
  assert.equal(nextVersion('alpha-1.2.999'), 'alpha-1.2.1000');
  for (const value of ['alpha-0.1.41-mac.1', 'latest', '../alpha-1.2.3']) assert.throws(() => nextVersion(value));
});

test('candidate replacement preserves auto-connect, other mods and filename', () => {
  const launch = { game: ['--quickPlayMultiplayer', '186.246.28.214:25565'], mods: [
    { path: 'instances/alpha/mods/createcityexpansioncore-1.0.2.jar', sha256: 'old' },
    { path: 'instances/alpha/mods/create.jar', sha256: 'unchanged' }
  ] };
  const before = structuredClone(launch), bytes = Buffer.from('new candidate');
  assert.equal(replaceMod(launch, bytes), before.mods[0].path);
  assert.deepEqual(launch.game, before.game); assert.deepEqual(launch.mods[1], before.mods[1]);
  assert.equal(launch.mods[0].sha256, digest(bytes));
  assert.throws(() => replaceMod({ mods: [] }, bytes));
  assert.throws(() => replaceMod({ mods: [before.mods[0], before.mods[0]] }, bytes));
});

test('release diff rejects config changes, deletion and a mismatched server JAR', () => {
  const hash = 'a'.repeat(64);
  const plan = { client: { added: [], changed: ['launch.json', 'instances/alpha/mods/createcityexpansioncore-1.0.2.jar'], removed: [] },
    server: { files: [{ path: 'mods/createcityexpansioncore-1.0.2.jar', sha256: hash }], remove: [] } };
  checkPlan(plan, hash);
  for (const change of [
    p => p.client.changed.push('instances/alpha/config/something.toml'),
    p => p.client.removed.push('instances/alpha/mods/create.jar'),
    p => p.server.files[0].sha256 = 'b'.repeat(64),
    p => p.server.remove.push('mods/create.jar'),
    p => p.server.files.push({ path: 'config/foo.toml', sha256: hash })
  ]) { const copy = structuredClone(plan); change(copy); assert.throws(() => checkPlan(copy, hash)); }
});

test('public verification accepts signed Windows/Mac and rejects tampering, wrong version and wrong platform', () => {
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ed25519');
  const raw = publicKey.export({ format: 'der', type: 'spki' }).subarray(-32).toString('hex');
  const sign = manifest => ({ manifest, signature: crypto.sign(null, canonicalBytes(manifest), privateKey).toString('hex') });
  const windows = { schemaVersion: 1, version: 'alpha-0.1.42', platform: 'windows-x86_64', files: [] };
  checkEnvelope(sign(windows), raw, 'alpha-0.1.42', 'windows-x86_64');
  const mac = { ...windows, version: 'alpha-0.1.42-mac.1', platform: 'macos-aarch64' };
  checkEnvelope(sign(mac), raw, 'alpha-0.1.42', 'macos-aarch64');
  const tampered = sign(windows); tampered.manifest = { ...windows, sequence: 999 };
  assert.throws(() => checkEnvelope(tampered, raw, 'alpha-0.1.42', 'windows-x86_64'));
  assert.throws(() => checkEnvelope(sign(windows), raw, 'alpha-0.1.43', 'windows-x86_64'));
  assert.throws(() => checkEnvelope(sign(windows), raw, 'alpha-0.1.42', 'macos-aarch64'));
});

test('resume after an uncertain server outcome stops before executing any stage', async () => {
  const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
  const root = path.join(repo, 'build/quick-deploy');
  await fs.mkdir(root, { recursive: true });
  const work = await fs.mkdtemp(path.join(root, 'uncertain-test-'));
  try {
    const state = { schemaVersion: 1, repo, done: ['baseline'], running: 'server' };
    await fs.writeFile(path.join(work, 'state.json'), JSON.stringify(state));
    await assert.rejects(main(['--resume', work]), /outcome is uncertain/);
    const after = JSON.parse(await fs.readFile(path.join(work, 'state.json')));
    assert.deepEqual(after.done, state.done);
    assert.equal(after.running, 'server');
    await assert.rejects(fs.stat(path.join(work, 'deploy.log')), { code: 'ENOENT' });
  } finally { await fs.rm(work, { recursive: true, force: true }); }
});
