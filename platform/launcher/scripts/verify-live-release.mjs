import crypto from 'node:crypto';
import fs from 'node:fs/promises';
const url = 'https://api.recreated.ru/releases/channels/alpha/manifest.json';
const response = await fetch(url);
if (!response.ok) throw new Error('Manifest HTTPS failed');
const envelope = await response.json();
function canonical(v) { return Array.isArray(v) ? v.map(canonical) : v && typeof v === 'object' ? Object.fromEntries(Object.keys(v).sort().map(k => [k, canonical(v[k])])) : v; }
const rawKey = (await fs.readFile(new URL('../src-tauri/release-public-key.hex', import.meta.url), 'utf8')).trim();
const key = crypto.createPublicKey({ key: Buffer.from('302a300506032b6570032100' + rawKey, 'hex'), format: 'der', type: 'spki' });
if (!crypto.verify(null, Buffer.from(JSON.stringify(canonical(envelope.manifest))), key, Buffer.from(envelope.signature, 'hex'))) throw new Error('Manifest signature failed');
for (const selected of ['launch.json', 'runtime/java-21/bin/java.exe', 'instances/alpha/mods/createcityexpansioncore-1.0.1.jar', 'assets/indexes/17.json']) {
  const entry = envelope.manifest.files.find(f => f.path === selected);
  const downloaded = await fetch(entry.url); if (!downloaded.ok) throw new Error('Object HTTPS failed');
  const bytes = Buffer.from(await downloaded.arrayBuffer());
  if (bytes.length !== entry.size || crypto.createHash('sha256').update(bytes).digest('hex') !== entry.sha256) throw new Error('Live object hash mismatch');
  console.log('Verified HTTPS object:', entry.path, entry.size);
}
console.log('Manifest signature/HTTPS/version:', envelope.manifest.version, envelope.manifest.files.length);
