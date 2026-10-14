// Developer-only preparation for this reference PC, NOT a clean-PC installer/updater.
import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';

const root = path.join(process.env.LOCALAPPDATA, 'Recreated');
const reference = path.join(process.env.APPDATA, '.minecraft');
const javaSource = process.env.RECREATED_JAVA_SOURCE;
const installer = process.env.RECREATED_NEOFORGE_INSTALLER;
if (!javaSource || !installer) throw new Error('Set RECREATED_JAVA_SOURCE and RECREATED_NEOFORGE_INSTALLER');
if (await fs.stat(path.join(root, 'launch.json')).catch(() => null)) throw new Error('Instance already prepared; refusing overwrite');
const game = path.join(root, 'instances', 'alpha');
await fs.mkdir(game, { recursive: true });
const digest = (data, algorithm = 'sha1') => crypto.createHash(algorithm).update(data).digest('hex');
async function download(url) {
  if (!['piston-meta.mojang.com', 'piston-data.mojang.com', 'libraries.minecraft.net', 'maven.neoforged.net', 'resources.download.minecraft.net'].includes(new URL(url).hostname)) throw new Error('Unexpected download host');
  const response = await fetch(url, { signal: AbortSignal.timeout(120000) });
  if (!response.ok) throw new Error(`Download failed ${response.status}: ${new URL(url).hostname}`);
  return Buffer.from(await response.arrayBuffer());
}
async function artifact(item, destination, candidate) {
  let bytes = candidate ? await fs.readFile(candidate).catch(() => null) : null;
  if (!bytes || digest(bytes) !== item.sha1) bytes = await download(item.url);
  if (digest(bytes) !== item.sha1) throw new Error(`Artifact hash mismatch: ${destination}`);
  await fs.mkdir(path.dirname(destination), { recursive: true });
  await fs.writeFile(destination, bytes);
}
const catalog = JSON.parse((await download('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')).toString());
const release = catalog.versions.find(v => v.id === '1.21.1');
const metadataBytes = await download(release.url);
if (digest(metadataBytes) !== release.sha1) throw new Error('Minecraft metadata hash mismatch');
const vanilla = JSON.parse(metadataBytes);
await fs.mkdir(path.join(root, 'versions', '1.21.1'), { recursive: true });
await fs.writeFile(path.join(root, 'versions', '1.21.1', '1.21.1.json'), metadataBytes);
await artifact(vanilla.downloads.client, path.join(root, 'versions', '1.21.1', '1.21.1.jar'), path.join(reference, 'versions', '1.21.1', '1.21.1.jar'));
function allowed(rules = []) {
  if (!rules.length) return true;
  let result = false;
  for (const rule of rules) {
    const os = rule.os;
    if (rule.features || (os?.name && os.name !== 'windows') || (os?.arch && !['x86_64', 'amd64'].includes(os.arch))) continue;
    result = rule.action === 'allow';
  }
  return result;
}
for (const lib of vanilla.libraries.filter(l => allowed(l.rules))) {
  const item = lib.downloads?.artifact;
  if (item) await artifact(item, path.join(root, 'libraries', item.path), path.join(reference, 'libraries', item.path));
}
// Empty official-launcher shape only to satisfy the installer; no account/profile import.
await fs.writeFile(path.join(root, 'launcher_profiles.json'), JSON.stringify({ profiles: {}, settings: {}, version: 3 }));
await fs.cp(javaSource, path.join(root, 'runtime', 'java-21'), { recursive: true, errorOnExist: true, force: false });
const java = path.join(root, 'runtime', 'java-21', 'bin', 'java.exe');
const javaVersion = spawnSync(java, ['-version'], { encoding: 'utf8', windowsHide: true });
if (javaVersion.status !== 0 || !/version "21\./.test(javaVersion.stderr)) throw new Error('Java 21 required');
const install = spawnSync(java, ['-jar', installer, '--installClient', root], { cwd: root, encoding: 'utf8', windowsHide: true, maxBuffer: 16 * 1024 * 1024 });
await fs.mkdir(path.join(root, 'logs'), { recursive: true });
await fs.writeFile(path.join(root, 'logs', 'neoforge-install.log'), (install.stdout ?? '') + (install.stderr ?? ''));
if (install.status !== 0) throw new Error('NeoForge installer failed; see Recreated/logs/neoforge-install.log');
const neoPath = path.join(root, 'versions', 'neoforge-21.1.251', 'neoforge-21.1.251.json');
const neo = JSON.parse(await fs.readFile(neoPath, 'utf8'));
if (neo.mainClass !== 'cpw.mods.bootstraplauncher.BootstrapLauncher') throw new Error('Unexpected NeoForge main class');
const libraryMap = new Map();
for (const lib of [...vanilla.libraries, ...neo.libraries].filter(l => allowed(l.rules))) {
  // NeoForge overrides vanilla dependency versions by group/artifact/classifier.
  const parts = lib.name.split(':');
  libraryMap.set(`${parts[0]}:${parts[1]}:${parts[3] ?? ''}`, lib);
}
const libraries = [];
for (const lib of libraryMap.values()) {
  const item = lib.downloads?.artifact;
  const parts = lib.name.split(':');
  const relative = item?.path ?? `${parts[0].replaceAll('.', '/')}/${parts[1]}/${parts[2]}/${parts[1]}-${parts[2]}${parts[3] ? '-' + parts[3] : ''}.jar`;
  const target = path.join(root, 'libraries', relative);
  if (item?.sha1) await artifact(item, target, path.join(reference, 'libraries', relative));
  const bytes = await fs.readFile(target);
  libraries.push({ path: 'libraries/' + relative, sha256: digest(bytes, 'sha256') });
}
// FML ignoreList names the selected version jar. Keep the official vanilla bytes
// under that name so they are excluded from its transforming module layer.
const selectedClient = 'versions/neoforge-21.1.251/neoforge-21.1.251.jar';
await fs.copyFile(path.join(root, 'versions/1.21.1/1.21.1.jar'), path.join(root, selectedClient));
libraries.push({ path: selectedClient, sha256: digest(await fs.readFile(path.join(root, selectedClient)), 'sha256') });
await artifact(vanilla.assetIndex, path.join(root, 'assets', 'indexes', '17.json'), path.join(reference, 'assets', 'indexes', '17.json'));
const assets = JSON.parse(await fs.readFile(path.join(root, 'assets', 'indexes', '17.json')));
let count = 0;
for (const item of Object.values(assets.objects)) {
  const relative = `${item.hash.slice(0, 2)}/${item.hash}`;
  await artifact({ sha1: item.hash, url: `https://resources.download.minecraft.net/${relative}` }, path.join(root, 'assets', 'objects', relative), path.join(reference, 'assets', 'objects', relative));
  count++;
}
await artifact(vanilla.logging.client.file, path.join(root, 'assets', 'log_configs', vanilla.logging.client.file.id));
for (const folder of ['mods', 'config', 'defaultconfigs']) {
  const source = path.join(reference, folder);
  await fs.cp(source, path.join(game, folder), { recursive: true, filter: async file => {
    const relative = path.relative(source, file).replaceAll('\\', '/');
    if ((await fs.lstat(file)).isSymbolicLink()) return false;
    if (folder === 'mods') return !relative || relative.endsWith('.jar');
    return !/(^|\/)(world|cache|history|accounts?)(\/|$)|cache|fingerprint|\.bak$|\.log$/i.test(relative);
  } });
}
await fs.cp(path.join(reference, 'resourcepacks', 'CreateServerQuests'), path.join(game, 'resourcepacks', 'CreateServerQuests'), { recursive: true });
// Fresh options, never import key bindings, servers.dat, credentials or user history.
await fs.writeFile(path.join(game, 'options.txt'), 'resourcePacks:["vanilla","mod_resources","file/CreateServerQuests"]\n');
const mods = [];
for (const name of (await fs.readdir(path.join(game, 'mods'))).sort()) {
  mods.push({ path: `instances/alpha/mods/${name}`, sha256: digest(await fs.readFile(path.join(game, 'mods', name)), 'sha256') });
}
const ccec = mods.find(m => m.path.endsWith('/createcityexpansioncore-1.0.1.jar'));
if (ccec?.sha256 !== '8113289f9a055ba0fc8f69903272986378d6b26ba5848dcbe8a3765542112d37') throw new Error('CCEC alpha baseline mismatch');
function args(values) { return (values ?? []).flatMap(v => typeof v === 'string' ? [v] : allowed(v.rules) ? v.value ?? v.values : []); }
const manifest = {
  schemaVersion: 1, minecraftVersion: '1.21.1', neoForgeVersion: '21.1.251',
  mainClass: neo.mainClass, libraries, mods, runtimeArtifacts: [],
  jvm: [...args(vanilla.arguments.jvm), ...args(neo.arguments.jvm)].filter(v => v !== undefined),
  game: [...args(vanilla.arguments.game), ...args(neo.arguments.game)].filter(v => v !== undefined),
  logging: vanilla.logging.client.argument.replace('${path}', '${assets_root}/log_configs/' + vanilla.logging.client.file.id),
  preparedAt: new Date().toISOString(), assetObjects: count,
};
// FML resolves these installer-produced jars outside the ordinary classpath.
for (const folder of ['libraries/net/neoforged/neoforge/21.1.251', 'libraries/net/minecraft/client/1.21.1-20240808.144430']) {
  for (const name of await fs.readdir(path.join(root, folder))) {
    if (!name.endsWith('.jar')) continue;
    const relative = folder + '/' + name;
    manifest.runtimeArtifacts.push({ path: relative, sha256: digest(await fs.readFile(path.join(root, relative)), 'sha256') });
  }
}
await fs.writeFile(path.join(root, 'launch.json'), JSON.stringify(manifest, null, 2));
console.log(`Prepared ${root}; ${libraries.length} clean dependencies, ${mods.length} mods, ${count} assets`);
