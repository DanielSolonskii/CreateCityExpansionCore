// One entry point; existing Release.ps1 remains responsible for deployment safety.
import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { canonicalBytes, digest, regularFile, verifiedEnvelope } from './release-common.mjs';

const scripts = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(scripts, '../../..');
const deployRoot = path.join(repo, 'build/quick-deploy');
const sshTarget = 'root@186.246.28.214';
const keyFile = path.join(scripts, '../src-tauri/release-public-key.hex');

export function nextVersion(version) {
  const m = /^alpha-(\d+)\.(\d+)\.(\d+)$/.exec(version);
  if (!m || !Number.isSafeInteger(Number(m[3]) + 1)) throw new Error('Invalid current release version');
  return `alpha-${m[1]}.${m[2]}.${Number(m[3]) + 1}`;
}

export function checkPlan(plan, expectedJar, modpack) {
  if (modpack) {
    const prefix = 'instances/alpha/mods/';
    const permitted = new Set(['launch.json', ...modpack.install.map(m => prefix + m.name), ...modpack.clientRemove.map(m => prefix + m.name)]);
    for (const p of [...plan.client.added, ...plan.client.changed, ...plan.client.removed]) {
      if (!permitted.has(p) && !/^instances\/alpha\/mods\/createcityexpansioncore-[^/]+\.jar$/.test(p)) throw new Error('Unexpected modpack client diff: ' + p);
      if (plan.client.removed.includes(p) && !modpack.clientRemove.some(m => prefix + m.name === p)) throw new Error('Unexpected removal: ' + p);
    }
    const expected = [{path:'mods/createcityexpansioncore-1.0.2.jar',sha256:expectedJar}, ...modpack.install.filter(m=>m.server).map(m=>({path:'mods/'+m.name,sha256:m.sha256}))];
    if (plan.server.files.length !== expected.length || expected.some(e=>!plan.server.files.some(f=>f.path===e.path&&f.sha256===e.sha256))
        || JSON.stringify([...plan.server.remove].sort((a,b)=>a.path.localeCompare(b.path))) !== JSON.stringify(modpack.serverRemove.map(m=>({path:'mods/'+m.name,expectedSha256:m.sha256})).sort((a,b)=>a.path.localeCompare(b.path)))) throw new Error('Unexpected modpack server diff');
    return;
  }
  const changed = [...plan.client.added, ...plan.client.changed, ...plan.client.removed];
  if (changed.some(p => p !== 'launch.json' && !/^instances\/alpha\/mods\/createcityexpansioncore-[^/]+\.jar$/.test(p))
      || plan.client.added.length || plan.client.removed.length
      || plan.server.remove.length || plan.server.files.length !== 1
      || plan.server.files[0].path !== 'mods/createcityexpansioncore-1.0.2.jar'
      || plan.server.files[0].sha256 !== expectedJar) throw new Error('Unexpected release diff; inspect plan.json');
}

export function checkEnvelope(envelope, publicKey, version, platform) {
  if (!/^[a-f0-9]{128}$/.test(envelope.signature ?? '')) throw new Error('Invalid signature encoding');
  const key = crypto.createPublicKey({ key: Buffer.from('302a300506032b6570032100' + publicKey, 'hex'), format: 'der', type: 'spki' });
  if (!crypto.verify(null, canonicalBytes(envelope.manifest), key, Buffer.from(envelope.signature, 'hex'))) throw new Error('Public release signature failed');
  const m = envelope.manifest;
  const expectedVersion = platform === 'windows-x86_64' ? m.version === version : new RegExp('^' + version.replaceAll('.', '\\.') + '-mac\\.[1-9][0-9]*$').test(m.version);
  if (!expectedVersion || m.platform !== platform || m.schemaVersion !== 1 || !Array.isArray(m.files)) throw new Error('Public channel differs from candidate');
  return m;
}

export function replaceMod(launch, bytes) {
  const mods = launch.mods.filter(m => /^instances\/alpha\/mods\/createcityexpansioncore(?:[-_.][^/]*)?\.jar$/i.test(m.path));
  if (mods.length !== 1) throw new Error('Expected exactly one managed CCEC mod');
  mods[0].sha256 = digest(bytes);
  return mods[0].path;
}

async function json(file) { return JSON.parse(await fs.readFile(file)); }
async function writeJson(file, value) {
  await fs.writeFile(file + '.tmp', JSON.stringify(value, null, 2) + '\n');
  await fs.rename(file + '.tmp', file);
}

async function download(url) {
  if (!url.startsWith('https://api.recreated.ru/releases/')) throw new Error('Unexpected release origin');
  const r = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(60000) });
  if (!r.ok) throw new Error(`Release HTTPS ${r.status}`);
  const bytes = Buffer.from(await r.arrayBuffer());
  if (bytes.length > 16 * 1024 ** 2) throw new Error('Unexpected release object size');
  return bytes;
}

async function findCache() {
  const candidates = [];
  for (const parent of [path.join(repo, 'build'), deployRoot]) {
    for (const e of await fs.readdir(parent, { withFileTypes: true }).catch(() => [])) {
      if (!e.isDirectory() || e.isSymbolicLink()) continue;
      const p = path.join(parent, e.name, 'workflow/releases/objects');
      const stat = await fs.lstat(p).catch(() => null);
      if (stat?.isDirectory() && !stat.isSymbolicLink()) candidates.push({ path: p, time: stat.mtimeMs });
    }
  }
  return candidates.sort((a, b) => b.time - a.time)[0]?.path;
}

export async function main(argv) {
  let dryRun = false, resume, clientFirst = false, skipRestartDelay = false, buildProject;
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--client-first') clientFirst = true;
    else if (argv[i] === '--skip-restart-delay') skipRestartDelay = true;
    else if (argv[i] === '--build-project' && argv[i + 1]) buildProject = path.resolve(argv[++i]);
    else if (argv[i] === '--dry-run') dryRun = true;
    else if (argv[i] === '--resume' && argv[i + 1]) resume = argv[++i];
    else throw new Error('Usage: ./Deploy.ps1 [-DryRun] [-Resume build/quick-deploy/<run>]');
  }
  await fs.mkdir(deployRoot, { recursive: true });
  const lockPath = path.join(deployRoot, 'deploy.lock');
  const lock = await fs.open(lockPath, 'wx').catch(() => { throw new Error(`Another deploy may be running. Inspect ${lockPath}; never remove an active lock.`); });
  let work, state, stateFile, log;
  try {
    await lock.writeFile(JSON.stringify({ pid: process.pid, started: new Date().toISOString() }));
    work = resume ? path.resolve(repo, resume) : path.join(deployRoot, new Date().toISOString().replace(/[:.]/g, '-') + '-' + crypto.randomUUID().slice(0, 8));
    const relative = path.relative(deployRoot, work);
    if (!relative || relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('Resume must be a run under build/quick-deploy');
    await fs.mkdir(work, { recursive: true });
    if ((await fs.lstat(work)).isSymbolicLink()) throw new Error('Symlink run refused');
    stateFile = path.join(work, 'state.json'); log = path.join(work, 'deploy.log');
    state = resume ? await json(stateFile) : { schemaVersion: 1, repo, done: [], started: new Date().toISOString() };
    if (state.schemaVersion !== 1 || state.repo !== repo || !Array.isArray(state.done)) throw new Error('Invalid resume state');
    if (state.running === 'server' && !state.done.includes('server')) throw new Error('Server deployment outcome is uncertain; inspect deploy.log and remote backup state before retrying.');
    if (!resume && process.env.CCEC_DEPLOY_MODPACK) {
      state.modpack = await json(path.resolve(process.env.CCEC_DEPLOY_MODPACK));
      if (state.modpack.schemaVersion !== 1 || !['install','clientRemove','serverRemove'].every(k=>Array.isArray(state.modpack[k]))) throw new Error('Invalid modpack update');
      for (const m of [...state.modpack.install,...state.modpack.clientRemove,...state.modpack.serverRemove]) {
        if (!/^[a-zA-Z0-9_.+\-]+\.jar$/.test(m.name)|| !/^[a-f0-9]{64}$/.test(m.sha256) || /^createcityexpansioncore/i.test(m.name)) throw new Error('Invalid pinned mod');
      }
      if (new Set(state.modpack.install.map(m=>m.name.toLowerCase())).size!==state.modpack.install.length) throw new Error('Duplicate mod install');
    }
    if (!resume) { state.clientFirst = clientFirst; state.skipRestartDelay = skipRestartDelay; state.buildProject = buildProject; }
    clientFirst = state.clientFirst === true; skipRestartDelay = state.skipRestartDelay === true; buildProject = state.buildProject;
    const save = () => writeJson(stateFile, state);
    const run = async (program, args, input) => {
      const output = await fs.open(log, 'a');
      await output.write(`\n[${new Date().toISOString()}] ${program} ${args.join(' ')}\n`);
      try {
        await new Promise((resolve, reject) => {
          const child = spawn(program, args, { cwd: repo, windowsHide: true, stdio: [input === undefined ? 'ignore' : 'pipe', output.fd, output.fd] });
          child.once('error', reject);
          child.once('close', code => code === 0 ? resolve() : reject(new Error(`${program} failed (exit ${code}); see ${log}`)));
          if (input !== undefined) { child.stdin.on('error', () => {}); child.stdin.end(input); }
        });
      } finally { await output.close(); }
    };
    const step = async (name, action) => {
      if (state.done.includes(name)) return;
      console.log(`[deploy] ${name}`);
      state.running = name; await save();
      await action();
      state.done.push(name); state.running = null; await save();
    };
    const remote = async (action, ...args) => {
      if (!['warn', 'verify', 'retain'].includes(action)
          || args.some(value => typeof value !== 'string' || !/^(?:[a-f0-9]{64}|\/opt\/create-server\/backups\/server-deploy-[a-f0-9]{32})$/.test(value))) throw new Error('Invalid remote helper argument');
      await run('ssh', ['-o', 'BatchMode=yes', '-o', 'ConnectTimeout=15', sshTarget, 'python3 - ' + [action, ...args].join(' ')], await fs.readFile(path.join(scripts, 'quick-deploy-server.py')));
    };
    const client = path.join(work, 'client'), server = path.join(work, 'server'), workflow = path.join(work, 'workflow');
    const previousFile = path.join(work, 'previous.json');
    const release = action => run('pwsh', ['-NoProfile', '-File', path.join(scripts, 'Release.ps1'), action, state.version,
      '-Instance', client, '-ServerSource', server, '-Output', workflow, '-Previous', previousFile, '-SshTarget', sshTarget, ...(clientFirst && action === 'publish' ? ['-ClientFirst'] : [])]);
    await step('baseline', async () => {
      await run(process.execPath, [path.join(scripts, 'fetch-release.mjs'), '--output', previousFile]);
      const { manifest } = await verifiedEnvelope(previousFile, keyFile);
      state.version = nextVersion(manifest.version); state.previousHash = digest(canonicalBytes(manifest));
    });
    await step('build', async () => {
      const properties = await fs.readFile(path.join(repo, 'gradle.properties'), 'utf8');
      const version = /^mod_version=([a-zA-Z0-9.+_-]+)\s*$/m.exec(properties)?.[1];
      if (!version) throw new Error('Cannot resolve mod_version');
      await run('pwsh', ['-NoProfile', '-File', path.join(scripts, 'Build-DeployCandidate.ps1'), '-Project', buildProject || repo, '-ModVersion', version]);
      const built = path.join(buildProject || repo, 'build/libs', `createcityexpansioncore-${version}.jar`);
      const bytes = await fs.readFile(built); state.jarHash = digest(bytes); state.modVersion = version;
      await fs.writeFile(path.join(work, 'candidate.jar'), bytes);
      await run('git', ['rev-parse', 'HEAD']); await run('git', ['status', '--short']);
    });
    await step('source', async () => {
      const args = [path.join(scripts, 'init-release-source.mjs'), '--manifest', previousFile, '--instance', client];
      const cache = await findCache(); if (cache) args.push('--objects', cache);
      await run(process.execPath, args);
    });
    await step('candidate', async () => {
      const bytes = await fs.readFile(path.join(work, 'candidate.jar'));
      if (digest(bytes) !== state.jarHash) throw new Error('Candidate drift');
      const launch = await json(path.join(client, 'launch.json'));
      const modPath = replaceMod(launch, bytes);
      await regularFile(client, modPath);
      await fs.writeFile(path.join(client, modPath), bytes);
      await writeJson(path.join(client, 'launch.json'), launch);
      await fs.mkdir(path.join(server, 'mods'), { recursive: true });
      await fs.writeFile(path.join(server, 'mods/createcityexpansioncore-1.0.2.jar'), bytes);
      if (state.modpack) {
        for (const m of state.modpack.clientRemove) {
          const file=await regularFile(client,'instances/alpha/mods/'+m.name);
          if (digest(await fs.readFile(file))!==m.sha256) throw new Error('Client removal hash mismatch: '+m.name);
          await fs.unlink(file);
        }
        for (const m of state.modpack.install) {
          const content=await fs.readFile(m.source);
          if(digest(content)!==m.sha256)throw new Error('Mod download drift: '+m.name);
          await fs.writeFile(path.join(client,'instances/alpha/mods',m.name),content);
          if(m.server)await fs.writeFile(path.join(server,'mods',m.name),content);
        }
        await writeJson(path.join(server,'remove.json'),state.modpack.serverRemove.map(m=>({path:'mods/'+m.name,expectedSha256:m.sha256})));
        launch.mods=launch.mods.filter(m=>!state.modpack.clientRemove.some(r=>'instances/alpha/mods/'+r.name===m.path));
        for(const m of state.modpack.install){launch.mods=launch.mods.filter(e=>e.path!=='instances/alpha/mods/'+m.name);launch.mods.push({path:'instances/alpha/mods/'+m.name,sha256:m.sha256});}
        await writeJson(path.join(client,'launch.json'),launch);
      }
    });
    const prepared = path.join(workflow, 'prepared', state.version);
    const pinnedManifest = state.manifestHash;
    // Re-read candidate files and live compatibility on resume, before any write to production.
    if (resume) state.done = state.done.filter(name => !['dry-run', 'verify'].includes(name));
    await step('dry-run', async () => {
      await release('dry-run'); const plan = await json(path.join(prepared, 'plan.json'));
      checkPlan(plan, state.jarHash, state.modpack);
      if (pinnedManifest && pinnedManifest !== plan.manifestSha256) throw new Error('Prepared candidate changed; start a new run');
      state.manifestHash = plan.manifestSha256;
    });
    if (dryRun) {
      console.log(`[deploy] DRY_RUN_OK ${state.version}; no server restart or channel changes\nResume: ./Deploy.ps1 -Resume "${path.relative(repo, work)}"\nLog: ${log}`);
      return;
    }
    const plan = await json(path.join(prepared, 'plan.json'));
    const inspection = await json(path.join(prepared, 'server-dry-run.json'));
    if (!state.done.includes('server') && !plan.client.changed.length && !inspection.plan.operations.length) {
      await remote('verify', state.jarHash);
      state.status = 'NO_CHANGES'; await save();
      console.log(`[deploy] NO_CHANGES CCEC ${state.modVersion}; no restart or publication needed\nReceipt: ${stateFile}`);
      return;
    }
    const deployServer = async () => {
    await step('server', async () => {
      const current = await download('https://api.recreated.ru/releases/channels/alpha/manifest.json');
      const currentFile = path.join(work, 'before-server.json'); await fs.writeFile(currentFile, current);
      const { manifest } = await verifiedEnvelope(currentFile, keyFile);
      if (digest(canonicalBytes(manifest)) !== (clientFirst ? state.manifestHash : state.previousHash)) throw new Error('Channel changed since preparation; start a new run');
      if (inspection.plan.operations.length && !skipRestartDelay) {
        await remote('warn');
        console.log('[deploy] Restart warning sent; waiting 60 seconds');
        await new Promise(resolve => setTimeout(resolve, 60000));
      }
      await release('server-deploy');
      const receipt = await json(path.join(prepared, 'server-deployed.json'));
      if (receipt.manifestSha256 !== state.manifestHash || !receipt.report.ready) throw new Error('Server receipt mismatch');
      state.backup = receipt.report.backup ?? null;
    });
    };
    const deployClients = async () => {
    await step('windows', async () => {
      const bytes = await download('https://api.recreated.ru/releases/channels/alpha/manifest.json');
      const file = path.join(work, 'before-publish.json'); await fs.writeFile(file, bytes);
      const { manifest } = await verifiedEnvelope(file, keyFile); const hash = digest(canonicalBytes(manifest));
      if (hash === state.manifestHash) return; // Publish may have committed before SSH disconnected.
      if (hash !== state.previousHash) throw new Error('Windows channel drift; do not overwrite another release');
      await release('publish');
    });
    await step('macos', async () => {
      await run('pwsh', ['-NoProfile', '-File', path.join(scripts, 'Sync-MacGameChannel.ps1'), '-ExpectedVersion', state.version, '-ExpectedManifest', state.manifestHash]);
    });
    };
    if (clientFirst) { await deployClients(); await deployServer(); }
    else { await deployServer(); await deployClients(); }
    await step('verify', async () => {
      const publicKey = (await fs.readFile(keyFile, 'utf8')).trim();
      const reports = [];
      const candidateLaunch = await json(path.join(client, 'launch.json'));
      for (const [channel, platform] of [['alpha', 'windows-x86_64'], ['alpha-macos-aarch64', 'macos-aarch64']]) {
        const envelope = JSON.parse(await download(`https://api.recreated.ru/releases/channels/${channel}/manifest.json`));
        const manifest = checkEnvelope(envelope, publicKey, state.version, platform);
        if (channel === 'alpha' && digest(canonicalBytes(manifest)) !== state.manifestHash) throw new Error('Published manifest mismatch');
        const mods = manifest.files.filter(f => /^instances\/alpha\/mods\/createcityexpansioncore(?:[-_.][^/]*)?\.jar$/i.test(f.path));
        if (mods.length !== 1) throw new Error('Public CCEC count mismatch');
        for (const entry of [mods[0], manifest.files.find(f => f.path === 'launch.json')]) {
          if (!entry || entry.url !== `https://api.recreated.ru/releases/objects/${entry.sha256}`) throw new Error('Unexpected artifact URL');
          const bytes = await download(entry.url);
          if (digest(bytes) !== entry.sha256 || bytes.length !== entry.size) throw new Error('Public artifact integrity failure');
          if (entry === mods[0] && entry.sha256 !== state.jarHash) throw new Error('Public CCEC differs from server candidate');
          if (entry.path === 'launch.json' && JSON.stringify(JSON.parse(bytes).game) !== JSON.stringify(candidateLaunch.game)) throw new Error('Game arguments changed during publication');
        }
        reports.push({ channel, version: manifest.version, signatureVerified: true });
      }
      await remote('verify', state.jarHash);
      state.channels = reports;
    });
    await step('backup-retention', async () => { if (state.backup) await remote('retain', state.backup); });
    state.status = 'COMPLETE'; state.finished = new Date().toISOString(); await save();
    console.log(`[deploy] COMPLETE CCEC ${state.modVersion}; ${state.channels.map(c => c.version).join(', ')}; server ready; ${state.backup ? 'one world backup' : 'existing backups preserved'}\nReceipt: ${stateFile}`);
  } catch (error) {
    if (state && stateFile) { state.error = error.message; await writeJson(stateFile, state).catch(() => {}); }
    if (work) console.error(`[deploy] STOPPED. Resume: ./Deploy.ps1 -Resume "${path.relative(repo, work)}"\nLog: ${log}`);
    throw error;
  } finally { await lock.close(); await fs.unlink(lockPath); }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
}
