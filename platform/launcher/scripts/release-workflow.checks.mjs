import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { prepare } from './release-workflow.mjs';
import { canonicalBytes, digest } from './release-common.mjs';
import { initialize } from './init-release-source.mjs';

async function fixture(t) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'recreated-workflow-test-'));
  t.after(() => fs.rm(dir, { recursive: true, force: true }));
  const client = path.join(dir, 'client');
  const output = path.join(dir, 'output');
  async function put(relative, bytes='fixture') {
    const p = path.join(client, relative);
    await fs.mkdir(path.dirname(p), { recursive: true }); await fs.writeFile(p, bytes);
  }
  for (const p of ['libraries/base.jar','instances/alpha/mods/base.jar','assets/log_configs/log.xml',
    'runtime/java-21/bin/java.exe','runtime/java-21/conf/base.properties','runtime/java-21/lib/base.dll','runtime/java-21/legal/LICENSE','runtime/java-21/release',
    'versions/1.21.1/1.21.1.json','versions/1.21.1/1.21.1.jar','versions/neoforge-21.1.251/neoforge-21.1.251.json',
    'instances/alpha/config/base.toml','instances/alpha/config/ftbquests/quests.snbt','instances/alpha/defaultconfigs/base.toml',
    'instances/alpha/resourcepacks/CreateServerQuests/pack.mcmeta']) await put(p);
  await put('assets/indexes/17.json', '{"objects":{}}');
  const artifact = p => ({path:p,sha256:digest(Buffer.from('fixture'))});
  await put('launch.json', JSON.stringify({schemaVersion:1,minecraftVersion:'1.21.1',neoForgeVersion:'21.1.251',libraries:[artifact('libraries/base.jar')],mods:[artifact('instances/alpha/mods/base.jar')],runtimeArtifacts:[],jvm:[],game:[]}));
  const built = spawnSync(process.execPath, [fileURLToPath(new URL('./publish-release.mjs', import.meta.url)),
    '--instance',client,'--output',output,'--version','alpha-9.0.0','--sequence','100'], {encoding:'utf8'});
  assert.equal(built.status,0,built.stderr);
  const previousManifest = JSON.parse(await fs.readFile(path.join(output,'releases/versions/alpha-9.0.0/manifest.canonical.json')));
  const keys = crypto.generateKeyPairSync('ed25519');
  const rawPublic = keys.publicKey.export({type:'spki',format:'der'}).subarray(-32).toString('hex');
  const previous = path.join(dir,'previous.json'); const publicKey = path.join(dir,'public.hex');
  await fs.writeFile(publicKey,rawPublic);
  await fs.writeFile(previous,JSON.stringify({manifest:previousManifest,signature:crypto.sign(null,canonicalBytes(previousManifest),keys.privateKey).toString('hex')}));
  const args = {instance:client,output,previous,'public-key':publicKey,version:'alpha-9.0.1'};
  return {dir,client,output,put,args,previousManifest};
}

test('draft auto-discovers client/server mod and nested config; leaves input/personal data intact',async t=>{
  const f=await fixture(t); const before=await fs.readFile(path.join(f.client,'launch.json'));
  await f.put('instances/alpha/mods/voice-test.jar','voice');
  await f.put('instances/alpha/mods/modsv06.rar','archive');
  await f.put('instances/alpha/config/voicechat/client.properties','volume=1');
  await f.put('instances/alpha/options.txt','personal');
  await f.put('instances/alpha/saves/personal/level.dat','world');
  await f.put('instances/alpha/config/token.properties','never publish');
  const server=path.join(f.dir,'server'); await fs.mkdir(path.join(server,'mods'),{recursive:true});
  await fs.writeFile(path.join(server,'mods/voice-test.jar'),'voice');
  await fs.mkdir(path.join(server,'config/voicechat'),{recursive:true});
  await fs.writeFile(path.join(server,'config/voicechat/server.properties'),'port=24454');
  const plan=await prepare({...f.args,'server-source':server});
  assert.equal(plan.sequence,101); assert.equal(plan.previousVersion,'alpha-9.0.0');
  assert.ok(plan.client.added.includes('instances/alpha/mods/voice-test.jar'));
  assert.ok(plan.client.added.includes('instances/alpha/config/voicechat/client.properties'));
  assert.equal(plan.server.files.length,2);
  assert.deepEqual(await fs.readFile(path.join(f.client,'launch.json')),before);
  const manifest=JSON.parse(await fs.readFile(path.join(f.output,'releases/versions/alpha-9.0.1/manifest.canonical.json')));
  assert.ok(!manifest.files.some(e=>/options|level.dat|modsv06|token/.test(e.path)));
  const launchEntry=manifest.files.find(e=>e.path==='launch.json');
  const launch=JSON.parse(await fs.readFile(path.join(f.output,'releases/objects',launchEntry.sha256)));
  assert.equal(launch.mods.length,2);
  assert.equal(manifest.files.find(e=>e.path.includes('voicechat/client')).mode,'seed');
  assert.equal(await fs.readFile(path.join(f.client,'instances/alpha/options.txt'),'utf8'),'personal');
});
test('dry-run identifies add/change/remove and delta archive contains only new hashes',async t=>{
  const f=await fixture(t);
  await fs.unlink(path.join(f.client,'instances/alpha/mods/base.jar'));
  await f.put('instances/alpha/mods/replacement.jar','replacement');
  await f.put('instances/alpha/config/base.toml','changed=1');
  const plan=await prepare(f.args);
  assert.ok(plan.client.removed.includes('instances/alpha/mods/base.jar'));
  assert.ok(plan.client.changed.includes('instances/alpha/config/base.toml'));
  const inventory=path.join(f.dir,'inventory'); await fs.writeFile(inventory,f.previousManifest.files.map(e=>e.sha256).join('\n'));
  const archive=path.join(f.dir,'delta.tar');
  const result=spawnSync(process.execPath,[fileURLToPath(new URL('./package-release.mjs',import.meta.url)),'--source',f.output,'--version','alpha-9.0.1','--inventory',inventory,'--archive',archive],{encoding:'utf8'});
  assert.equal(result.status,0,result.stderr);
  const names=spawnSync('tar',['-tf',archive],{encoding:'utf8'}).stdout.split(/\r?\n/);
  const oldHashes=new Set(f.previousManifest.files.map(e=>e.sha256));
  for(const p of names.filter(p=>/^releases\/objects\/[a-f0-9]{64}$/.test(p))) assert.ok(!oldHashes.has(p.split('/').at(-1)));
  assert.equal(JSON.parse(result.stdout).newObjects,plan.newObjectsVsPrevious);
});
test('rejects sequence collision, current version reuse, bad signature and unsafe mod names',async t=>{
  const f=await fixture(t);
  await assert.rejects(prepare({...f.args,sequence:'100'}),/greater sequence/);
  await assert.rejects(prepare({...f.args,version:'alpha-9.0.0'}),/New version/);
  const envelope=JSON.parse(await fs.readFile(f.args.previous)); envelope.manifest.sequence++;
  await fs.writeFile(f.args.previous,JSON.stringify(envelope));
  await assert.rejects(prepare(f.args),/signature failed/);
});
test('rejects client/server shared mod drift, unpinned removal and runtime artifact drift',async t=>{
  const f=await fixture(t); const server=path.join(f.dir,'server');
  await fs.mkdir(path.join(server,'mods'),{recursive:true}); await fs.writeFile(path.join(server,'mods/base.jar'),'different');
  await assert.rejects(prepare({...f.args,'server-source':server}),/bytes differ/);
  await fs.writeFile(path.join(server,'mods/base.jar'),'fixture'); await fs.writeFile(path.join(server,'remove.json'),'[{"path":"mods/old.jar"}]');
  await assert.rejects(prepare({...f.args,'server-source':server}),/expectedSha256/);
  await f.put('libraries/base.jar','tampered');
  await assert.rejects(prepare(f.args),/artifact drift/);
});
test('unpublished draft may be corrected without editing launch metadata manually',async t=>{
  const f=await fixture(t); const first=await prepare(f.args);
  assert.equal(first.newObjectsVsPrevious,0);
  assert.deepEqual(first.client,{added:[],changed:[],removed:[],seedNotice:first.client.seedNotice});
  await f.put('instances/alpha/mods/base.jar','new mod bytes');
  const second=await prepare(f.args);
  assert.notEqual(first.manifestSha256,second.manifestSha256);
  assert.ok(second.client.changed.includes('instances/alpha/mods/base.jar'));
});
test('source initialization copies signed allowlist only; bad hash never installs partial source',async t=>{
  const f=await fixture(t); const target=path.join(f.dir,'new-source');
  const args={manifest:f.args.previous,'public-key':f.args['public-key'],instance:target,objects:path.join(f.output,'releases/objects')};
  const result=await initialize(args); assert.equal(result.files,f.previousManifest.files.length);
  assert.ok(!(await fs.readdir(path.join(target,'instances/alpha'))).includes('saves'));
  const entry=f.previousManifest.files.find(e=>e.path==='instances/alpha/mods/base.jar');
  await fs.writeFile(path.join(target,entry.path),'new developer draft');
  assert.equal(digest(await fs.readFile(path.join(args.objects,entry.sha256))),entry.sha256);
  await assert.rejects(initialize(args),/new empty source/);
  await fs.writeFile(path.join(args.objects,entry.sha256),'bad cache');
  const badTarget=path.join(f.dir,'bad-source');
  await assert.rejects(initialize({...args,instance:badTarget}),/hash mismatch/);
  assert.equal(await fs.stat(badTarget).catch(()=>null),null);
});
