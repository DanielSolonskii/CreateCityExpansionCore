// Local preparation only. No SSH, channel switching, signing key or service actions.
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { argumentsOf, canonicalBytes, digest, diffFiles, regularFile, safePath, serverPath, verifiedEnvelope } from './release-common.mjs';

export async function prepare(args) {
  const version = args.version;
  if (!/^alpha-\d+\.\d+\.\d+$/.test(version ?? '')) throw new Error('Invalid alpha version');
  const root = path.resolve(args.instance);
  const output = path.resolve(args.output);
  if (output === root || output.startsWith(root + path.sep)) throw new Error('Output must be outside the client source');
  const previous = (await verifiedEnvelope(args.previous, args['public-key'] ?? fileURLToPath(new URL('../src-tauri/release-public-key.hex', import.meta.url)))).manifest;
  const sequence = args.sequence && args.sequence !== 'auto' ? Number(args.sequence) : previous.sequence + 1;
  if (version === previous.version || !Number.isSafeInteger(sequence) || sequence <= previous.sequence || sequence > 2147483647) throw new Error('New version and greater sequence required');
  const originalLaunch = await fs.readFile(await regularFile(root, 'launch.json'));
  const launch = JSON.parse(originalLaunch);
  const mods = [];
  const names = new Set();
  for (const entry of await fs.readdir(path.join(root, 'instances/alpha/mods'), { withFileTypes: true })) {
    if (entry.isSymbolicLink()) throw new Error('Symlink in mod input');
    if (!entry.isFile() || !entry.name.endsWith('.jar')) continue;
    const relative = safePath('instances/alpha/mods/' + entry.name);
    if (names.has(relative.toLowerCase())) throw new Error('Case-insensitive duplicate mod');
    names.add(relative.toLowerCase());
    mods.push({ path: relative, sha256: digest(await fs.readFile(await regularFile(root, relative))) });
  }
  if (!mods.length) throw new Error('Empty modpack refused');
  const unchangedMods = launch.mods.length === mods.length && launch.mods.every(old => mods.some(item => item.path === old.path && item.sha256 === old.sha256));
  launch.mods = mods.sort((a, b) => a.path.localeCompare(b.path, 'en'));
  const prepared = path.join(output, 'prepared', version);
  await fs.mkdir(prepared, { recursive: true });
  const launchFile = path.join(prepared, 'launch.json');
  await fs.writeFile(launchFile, unchangedMods ? originalLaunch : JSON.stringify(launch, null, 2) + '\n');
  const publisher = fileURLToPath(new URL('./publish-release.mjs', import.meta.url));
  const child = spawnSync(process.execPath, [publisher, '--instance', root, '--launch-file', launchFile, '--version', version,
    '--sequence', String(sequence), '--output', output, '--origin', 'https://api.recreated.ru', '--draft', 'true'], { encoding: 'utf8', windowsHide: true });
  if (child.status !== 0) throw new Error('Release preparation failed: ' + child.stderr);
  const manifest = JSON.parse(await fs.readFile(path.join(output, 'releases/versions', version, 'manifest.canonical.json')));
  const paths = new Set();
  for (const entry of manifest.files) {
    safePath(entry.path);
    if (paths.has(entry.path.toLowerCase())) throw new Error('Case-insensitive duplicate manifest path');
    paths.add(entry.path.toLowerCase());
  }
  const previousHashes = new Set(previous.files.map(f => f.sha256));
  const newObjects = new Map(manifest.files.filter(f => !previousHashes.has(f.sha256)).map(f => [f.sha256, f.size]));
  const server = { schemaVersion: 1, version, files: [], remove: [] };
  if (args['server-source']) {
    const serverRoot = path.resolve(args['server-source']);
    async function collect(relative) {
      const directory = path.join(serverRoot, relative);
      const entries = await fs.readdir(directory, { withFileTypes: true }).catch(e => { if (e.code === 'ENOENT') return []; throw e; });
      for (const entry of entries) {
        const p = relative + '/' + entry.name;
        if (entry.isSymbolicLink()) throw new Error('Symlink in server source');
        if (entry.isDirectory()) { if (relative === 'mods') throw new Error('Nested server mods refused'); await collect(p); }
        else if (entry.isFile()) {
          serverPath(p);
          const bytes = await fs.readFile(await regularFile(serverRoot, p));
          const sha256 = digest(bytes);
          const blob = path.join(prepared, 'server/objects', sha256);
          await fs.mkdir(path.dirname(blob), { recursive: true });
          await fs.writeFile(blob, bytes);
          server.files.push({ path: p, size: bytes.length, sha256 });
          // A shared client/server filename must contain identical bytes.
          const clientMod = manifest.files.find(f => f.path === 'instances/alpha/' + p);
          if (p.startsWith('mods/') && clientMod && clientMod.sha256 !== sha256) throw new Error('Client/server shared mod bytes differ: ' + p);
        }
      }
    }
    for (const d of ['mods', 'config', 'defaultconfigs']) await collect(d);
    const removalFile = path.join(serverRoot, 'remove.json');
    server.remove = JSON.parse(await fs.readFile(removalFile).catch(e => { if (e.code === 'ENOENT') return Buffer.from('[]'); throw e; }));
    if (!Array.isArray(server.remove)) throw new Error('remove.json must be an array');
    for (const removal of server.remove) {
      serverPath(removal.path);
      if (!/^[0-9a-f]{64}$/.test(removal.expectedSha256 ?? '') || server.files.some(f => f.path === removal.path)) throw new Error('Removal requires expectedSha256 and cannot overlap installs');
    }
  }
  const serverPaths = [...server.files, ...server.remove].map(f => f.path.toLowerCase());
  if (new Set(serverPaths).size !== serverPaths.length) throw new Error('Duplicate server paths');
  server.files.sort((a, b) => a.path.localeCompare(b.path, 'en'));
  await fs.mkdir(path.join(prepared, 'server/objects'), { recursive: true });
  await fs.writeFile(path.join(prepared, 'server/server-release.json'), canonicalBytes(server));
  const plan = {
    version, sequence, expectedPrevious: digest(canonicalBytes(previous)), previousVersion: previous.version,
    manifestSha256: digest(canonicalBytes(manifest)), serverManifestSha256: digest(canonicalBytes(server)),
    client: diffFiles(previous.files, manifest.files), newObjectsVsPrevious: newObjects.size,
    newBytesVsPrevious: [...newObjects.values()].reduce((a, b) => a + b, 0),
    server: { files: server.files, remove: server.remove }, output, prepared,
    notice: 'LOCAL ONLY. No channel, service, network configuration or production files changed.'
  };
  const planFile = path.join(prepared, 'plan.json');
  await fs.writeFile(planFile, canonicalBytes(plan));
  return plan;
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { console.log(JSON.stringify(await prepare(argumentsOf(process.argv.slice(2))), null, 2)); }
  catch (e) { console.error(e.message); process.exitCode = 1; }
}
