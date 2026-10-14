// SSH stdin carries code + public artifact metadata, not credentials. No remote writes.
import fs from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { argumentsOf } from './release-common.mjs';
const args = argumentsOf(process.argv.slice(2));
if (!/^(?!-)[a-zA-Z0-9_.@:-]+$/.test(args.ssh ?? '')) throw new Error('Invalid SSH target');
const common = (await fs.readFile(new URL('./deploy-release.py', import.meta.url))).toString('base64');
const server = (await fs.readFile(new URL('./server-release.py', import.meta.url))).toString('base64');
const manifest = (await fs.readFile(args.manifest)).toString('base64');
const readyCode = args['require-ready'] === 'true' ? 'ready=s.Systemd().verify_ready(1)' : 'ready=None';
const program = `import base64,types,sys,json,pathlib,hashlib\nc=types.ModuleType('release_common')\nc.__name__='release_common'\nexec(base64.b64decode('${common}'),c.__dict__)\nsys.modules['release_common']=c\ns=types.ModuleType('server_inspect')\nexec(base64.b64decode('${server}'),s.__dict__)\nm=json.loads(base64.b64decode('${manifest}'))\np=s.plan_release(pathlib.Path('/opt/create-server/minecraft'),m)\n${readyCode}\nprint(json.dumps({'status':'DRY_RUN','plan':p,'planSha256':hashlib.sha256(c.canonical(p)).hexdigest(),'readyInvocation':ready,'channelChanged':False}))\n`;
const result = spawnSync('ssh', ['-o', 'BatchMode=yes', args.ssh, 'python3 -'], { input: program, encoding: 'utf8', windowsHide: true, timeout: 300000 });
if (result.status !== 0) throw new Error('Read-only server inspection failed: ' + result.stderr);
const report = JSON.parse(result.stdout);
await fs.writeFile(args.output, JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
