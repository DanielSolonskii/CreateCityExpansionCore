// Explicit local source initialization from signed artifacts, never a whole player directory.
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { argumentsOf, canonicalBytes, digest, safePath, verifiedEnvelope } from './release-common.mjs';

export async function initialize(args) {
  const envelope = await verifiedEnvelope(args.manifest, args['public-key'] ?? fileURLToPath(new URL('../src-tauri/release-public-key.hex', import.meta.url)));
  const target = path.resolve(args.instance);
  if (await fs.lstat(target).catch(e => { if(e.code === 'ENOENT') return null; throw e; })) throw new Error('Use a new empty source path; never overwrite a prepared draft');
  const parent = path.dirname(target);
  await fs.mkdir(parent, {recursive:true});
  let ancestor = parent;
  while (path.dirname(ancestor) !== ancestor) {
    if ((await fs.lstat(ancestor)).isSymbolicLink()) throw new Error('Symlink source parent refused');
    ancestor = path.dirname(ancestor);
  }
  const temporary = await fs.mkdtemp(path.join(parent,'release-source-'));
  const seen = new Set();
  try {
    for (const entry of envelope.manifest.files) {
      safePath(entry.path);
      const managed = entry.path === 'launch.json' || /^(libraries|assets|versions|runtime\/java-21)\//.test(entry.path)
        || /^instances\/alpha\/mods\/[^/]+\.jar$/.test(entry.path) || entry.path.startsWith('instances/alpha/resourcepacks/CreateServerQuests/');
      const seed = /^instances\/alpha\/(config|defaultconfigs)\//.test(entry.path);
      if (!((entry.mode === 'managed' && managed) || (entry.mode === 'seed' && seed)) || seen.has(entry.path.toLowerCase())
        || !/^[a-f0-9]{64}$/.test(entry.sha256) || entry.url !== `https://api.recreated.ru/releases/objects/${entry.sha256}`
        || !Number.isSafeInteger(entry.size) || entry.size < 0 || entry.size > 2 * 1024**3) throw new Error('Invalid source allowlist');
      seen.add(entry.path.toLowerCase());
      const cached = args.objects ? path.join(args.objects,entry.sha256) : null;
      let bytes;
      if (cached && await fs.stat(cached).catch(() => null)) {
        if ((await fs.lstat(cached)).isSymbolicLink()) throw new Error('Symlink cached object refused');
        bytes = await fs.readFile(cached);
      } else {
        const response = await fetch(entry.url,{redirect:'error',signal:AbortSignal.timeout(120000)});
        if (!response.ok) throw new Error('Source object download failed');
        bytes = Buffer.from(await response.arrayBuffer());
      }
      if (bytes.length !== entry.size || digest(bytes) !== entry.sha256) throw new Error('Source object hash mismatch');
      const destination = path.join(temporary,entry.path);
      await fs.mkdir(path.dirname(destination),{recursive:true});
      await fs.writeFile(destination,bytes,{flag:'wx'}); // Copies, not hardlinks into shared blobs.
    }
    for (const directory of ['instances/alpha/config/ftbquests','instances/alpha/defaultconfigs','instances/alpha/resourcepacks/CreateServerQuests']) await fs.mkdir(path.join(temporary,directory),{recursive:true});
    await fs.writeFile(path.join(temporary,'.release-source.json'),canonicalBytes({version:envelope.manifest.version,sequence:envelope.manifest.sequence,manifestSha256:digest(canonicalBytes(envelope.manifest))}));
    await fs.rename(temporary,target);
    return {source:target,version:envelope.manifest.version,files:seen.size};
  } finally { await fs.rm(temporary,{recursive:true,force:true}); }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { console.log(JSON.stringify(await initialize(argumentsOf(process.argv.slice(2))))); }
  catch(e) { console.error(e.message); process.exitCode=1; }
}
