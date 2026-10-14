import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

export const canonical = v => Array.isArray(v) ? v.map(canonical) : v && typeof v === 'object'
  ? Object.fromEntries(Object.keys(v).sort().map(k => [k, canonical(v[k])])) : v;
export const canonicalBytes = v => Buffer.from(JSON.stringify(canonical(v)));
export const digest = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
export function argumentsOf(argv) {
  if (argv.length % 2 || argv.some((v, i) => i % 2 === 0 && !v.startsWith('--'))) throw new Error('Use --name value arguments');
  return Object.fromEntries(argv.reduce((r, v, i) => i % 2 ? r : [...r, [v.slice(2), argv[i + 1]]], []));
}
export function safePath(p) {
  if (typeof p !== 'string' || p.length > 220 || !/^[\x20-\x7e]+$/.test(p) || /[\\:<>"|?*]/.test(p)) throw new Error('Unsafe release path');
  for (const part of p.split('/')) {
    if (!part || part === '.' || part === '..' || /[. ]$/.test(part) || /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(part)
      || /^(saves|screenshots|logs|crash-reports|cache|caches|world|accounts|tokens|secrets|\.git|\.recreated|options.txt|servers.dat|usercache.json|usernamecache.json)$/i.test(part)
      || /fingerprint|\.bak$/i.test(part)) throw new Error('Protected release path');
  }
  return p;
}
export function serverPath(p) {
  safePath(p);
  if (!( /^mods\/[^/]+\.jar$/.test(p) || /^(config|defaultconfigs)\/.+\.(toml|snbt|json|json5|cfg|properties)$/.test(p))
    || /token|credential|password|account|secret/i.test(p)) throw new Error('Outside server allowlist');
  return p;
}
export async function regularFile(root, relative) {
  safePath(relative);
  let current = path.resolve(root);
  for (const part of ['', ...relative.split('/')]) {
    if (part) current = path.join(current, part);
    if ((await fs.lstat(current)).isSymbolicLink()) throw new Error('Symlink/junction in release input');
  }
  if (!(await fs.stat(current)).isFile()) throw new Error('Not a regular release file');
  return current;
}
export async function verifiedEnvelope(filename, keyFile) {
  const envelope = JSON.parse(await fs.readFile(filename));
  const rawKey = (await fs.readFile(keyFile, 'utf8')).trim();
  if (!/^[0-9a-f]{64}$/.test(rawKey) || !/^[0-9a-f]{128}$/.test(envelope.signature ?? '')) throw new Error('Invalid signing metadata');
  const key = crypto.createPublicKey({ key: Buffer.from('302a300506032b6570032100' + rawKey, 'hex'), format: 'der', type: 'spki' });
  if (!crypto.verify(null, canonicalBytes(envelope.manifest), key, Buffer.from(envelope.signature, 'hex'))) throw new Error('Previous manifest signature failed');
  const m = envelope.manifest;
  if (m.schemaVersion !== 1 || !/^alpha-\d+\.\d+\.\d+$/.test(m.version) || !Number.isSafeInteger(m.sequence) || m.sequence < 1 || !Array.isArray(m.files)) throw new Error('Invalid previous release');
  return envelope;
}
export function diffFiles(previous, next) {
  const old = new Map(previous.map(f => [f.path, f]));
  const current = new Map(next.map(f => [f.path, f]));
  return {
    added: next.filter(f => !old.has(f.path)).map(f => f.path),
    changed: next.filter(f => old.has(f.path) && (old.get(f.path).sha256 !== f.sha256 || old.get(f.path).mode !== f.mode)).map(f => f.path),
    removed: previous.filter(f => !current.has(f.path)).map(f => f.path),
    seedNotice: 'Existing client config seeds are NOT overwritten or deleted; changed seeds affect new installations only.'
  };
}
