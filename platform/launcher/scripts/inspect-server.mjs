// SSH stdin carries code + public artifact metadata, not credentials. No remote writes.
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { argumentsOf } from './release-common.mjs';
const args = argumentsOf(process.argv.slice(2));
if (!/^(?!-)[a-zA-Z0-9_.@:-]+$/.test(args.ssh ?? '')) throw new Error('Invalid SSH target');
const common = (await fs.readFile(new URL('./deploy-release.py', import.meta.url))).toString('base64');
const server = (await fs.readFile(new URL('./server-release.py', import.meta.url))).toString('base64');
const manifestBytes = await fs.readFile(args.manifest);
const manifest = manifestBytes.toString('base64');
const objects = {};
for (const entry of JSON.parse(manifestBytes).files) {
  if (!/^mods\/createcityexpansioncore(?:[-_.][^/]*)?\.jar$/i.test(entry.path)) continue;
  if (!/^[a-f0-9]{64}$/.test(entry.sha256)) throw new Error('Invalid CCEC candidate hash');
  const candidate = await fs.readFile(path.join(path.dirname(args.manifest), 'objects', entry.sha256));
  if (candidate.length !== entry.size || createHash('sha256').update(candidate).digest('hex') !== entry.sha256) throw new Error('CCEC candidate hash/size mismatch');
  objects[entry.sha256] = candidate.toString('base64');
}
const objectPayload = Buffer.from(JSON.stringify(objects)).toString('base64');
const readyCode = args['require-ready'] === 'true' ? 'ready=s.Systemd().verify_ready(1)' : 'ready=None';
const program = `import base64,types,sys,json,pathlib,hashlib\nc=types.ModuleType('release_common')\nc.__name__='release_common'\nexec(base64.b64decode('${common}'),c.__dict__)\nsys.modules['release_common']=c\ns=types.ModuleType('server_inspect')\nexec(base64.b64decode('${server}'),s.__dict__)\nm=json.loads(base64.b64decode('${manifest}'))\nobjects={k:base64.b64decode(v) for k,v in json.loads(base64.b64decode('${objectPayload}')).items()}\np=s.plan_release(pathlib.Path('/opt/create-server/minecraft'),m,objects)\n${readyCode}\nprint(json.dumps({'status':'DRY_RUN','plan':p,'planSha256':hashlib.sha256(c.canonical(p)).hexdigest(),'readyInvocation':ready,'channelChanged':False}))\n`;
const result = spawnSync('ssh', ['-o', 'BatchMode=yes', args.ssh, 'python3 -'], { input: program, encoding: 'utf8', windowsHide: true, timeout: 300000, maxBuffer: 16 * 1024 * 1024 });
if (result.status !== 0) throw new Error('Read-only server inspection failed: ' + result.stderr);
const report = JSON.parse(result.stdout);
await fs.writeFile(args.output, JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
