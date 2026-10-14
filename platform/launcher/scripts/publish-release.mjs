// Build an explicit content-addressed release from a prepared instance.
// No recursive game-directory import. Production signing is done ONLY on VDS.
import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { regularFile, safePath } from './release-common.mjs';
const args = Object.fromEntries(process.argv.slice(2).reduce((r, v, i, a) => i % 2 ? r : [...r, [v.replace(/^--/, ''), a[i + 1]]], []));
const root = path.resolve(args.instance ?? path.join(process.env.LOCALAPPDATA, 'Recreated'));
const output = path.resolve(args.output ?? 'releases');
const version = args.version;
const sequence = Number(args.sequence);
const origin = new URL(args.origin ?? 'https://api.recreated.ru');
if (!/^alpha-\d+\.\d+\.\d+$/.test(version ?? '') || !Number.isSafeInteger(sequence) || sequence < 1 || origin.pathname !== '/') throw new Error('Specify --version alpha-x.y.z --sequence N --origin https://host');
const launchBytes = await fs.readFile(args['launch-file'] ?? await regularFile(root, 'launch.json'));
const launch = JSON.parse(launchBytes);
if (launch.minecraftVersion !== '1.21.1' || launch.neoForgeVersion !== '21.1.251') throw new Error('Unexpected standalone baseline');
const selected = new Map();
const hash = b => crypto.createHash('sha256').update(b).digest('hex');
const protectedPath = p => /(^|\/)(saves|screenshots|logs|crash-reports|cache|caches|world|accounts|tokens|secrets|\.git)(\/|$)|fingerprint|\.bak$|(^|\/)(options.txt|servers.dat|usercache.json|usernamecache.json)$/i.test(p);
async function add(relative, mode = 'managed') {
  if (protectedPath(relative) || relative.includes('\\') || relative.split('/').some(c => !c || c === '.' || c === '..')) throw new Error(`Protected path: ${relative}`);
  safePath(relative);
  if (mode === 'seed' && /token|credential|password|account|secret/i.test(relative)) throw new Error('Secret-named config excluded from distribution');
  const bytes = relative === 'launch.json' ? launchBytes : await fs.readFile(await regularFile(root, relative));
  const sha256 = hash(bytes);
  const object = path.join(output, 'releases', 'objects', sha256);
  await fs.mkdir(path.dirname(object), { recursive: true });
  if (await fs.stat(object).catch(() => null)) {
    if (hash(await fs.readFile(object)) !== sha256) throw new Error('Corrupt existing object');
  } else await fs.writeFile(object, bytes, { flag: 'wx' });
  selected.set(relative, { path: relative, size: bytes.length, sha256, url: new URL(`/releases/objects/${sha256}`, origin).href, mode });
}
async function walk(relative, mode = 'managed') {
  for (const entry of await fs.readdir(path.join(root, relative), { withFileTypes: true })) {
    if (entry.isSymbolicLink()) throw new Error('Symlink in distribution input');
    const next = relative + '/' + entry.name;
    if (protectedPath(next)) continue;
    if (entry.isDirectory()) await walk(next, mode);
    else if (entry.isFile()) await add(next, mode);
  }
}
await add('launch.json');
for (const item of [...launch.libraries, ...launch.mods, ...launch.runtimeArtifacts]) {
  if (hash(await fs.readFile(path.join(root, item.path))) !== item.sha256) throw new Error(`Baseline artifact drift: ${item.path}`);
  await add(item.path);
}
await add('assets/indexes/17.json');
const assets = JSON.parse(await fs.readFile(path.join(root, 'assets/indexes/17.json')));
for (const object of Object.values(assets.objects)) await add(`assets/objects/${object.hash.slice(0, 2)}/${object.hash}`);
await walk('assets/log_configs');
// Only the Java runtime needed by the game, not JDK build tools/jmods/source archives.
for (const directory of ['bin', 'conf', 'lib', 'legal']) await walk('runtime/java-21/' + directory);
await add('runtime/java-21/release');
for (const relative of ['versions/1.21.1/1.21.1.json', 'versions/1.21.1/1.21.1.jar', 'versions/neoforge-21.1.251/neoforge-21.1.251.json']) await add(relative);
await walk('instances/alpha/resourcepacks/CreateServerQuests');
// Explicit config entry classes, never generated cache/history directories.
for (const entry of await fs.readdir(path.join(root, 'instances/alpha/config'), { withFileTypes: true })) {
  if (entry.isFile() && /\.(toml|snbt|json5|cfg|properties)$/.test(entry.name) && !/iris|sodium|fingerprint|cache|bak|token|account|secret/i.test(entry.name)) await add('instances/alpha/config/' + entry.name, 'seed');
}
await walk('instances/alpha/config/ftbquests', 'seed');
await walk('instances/alpha/defaultconfigs', 'seed');
// Workflow mode also includes safe nested mod configs (e.g. voicechat/*.properties).
// Legacy publication retains its original selection for immutable alpha-0.1.0.
if (args['launch-file']) {
  async function configs(relative) {
    for (const entry of await fs.readdir(path.join(root, relative), { withFileTypes: true })) {
      const next = relative + '/' + entry.name;
      if (entry.isSymbolicLink()) throw new Error('Symlink in config input');
      if (protectedPath(next) || /iris|sodium|fingerprint|cache|bak|token|account|secret|password|credential/i.test(entry.name)) continue;
      safePath(next);
      if (entry.isDirectory()) await configs(next);
      else if (entry.isFile() && /\.(toml|snbt|json|json5|cfg|properties)$/.test(entry.name)) await add(next, 'seed');
    }
  }
  await configs('instances/alpha/config');
}
const manifest = { schemaVersion: 1, version, sequence, platform: 'windows-x86_64', minecraftVersion: '1.21.1', neoForgeVersion: '21.1.251', files: [...selected.values()].sort((a, b) => a.path.localeCompare(b.path, 'en')) };
function canonical(v) { return Array.isArray(v) ? v.map(canonical) : v && typeof v === 'object' ? Object.fromEntries(Object.keys(v).sort().map(k => [k, canonical(v[k])])) : v; }
const payload = Buffer.from(JSON.stringify(canonical(manifest)));
const directory = path.join(output, 'releases', 'versions', version);
await fs.mkdir(directory, { recursive: true });
const canonicalFile = path.join(directory, 'manifest.canonical.json');
const previous = await fs.readFile(canonicalFile).catch(() => null);
if (previous && !previous.equals(payload) && args.draft !== 'true') throw new Error('Immutable version already has a different manifest; use a new version');
await fs.writeFile(canonicalFile, payload);
await fs.writeFile(path.join(directory, 'manifest.preview.json'), JSON.stringify(manifest, null, 2));
console.log(JSON.stringify({ version, files: manifest.files.length, bytes: manifest.files.reduce((n, f) => n + f.size, 0), manifestSha256: hash(payload), output }));
