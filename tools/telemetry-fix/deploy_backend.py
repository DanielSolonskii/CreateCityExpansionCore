import pathlib,subprocess,hashlib,os,shutil,datetime,time,json,urllib.request,sys
root=pathlib.Path('/opt/create-server');stage=pathlib.Path(__file__).parent
def run(*args):return subprocess.check_output(args,text=True).strip()
def digest(p):return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()
assert digest(stage/'backend.jar')==sys.argv[1]
others=['create-server-minecraft','create-server-telegram-bot','wg-quick@wgtelegram']
before={s:run('systemctl','show',s,'-p','InvocationID','--value') for s in others}
protected=list((root/'secrets').glob('*'))+list(pathlib.Path('/etc/wireguard').glob('*.conf'))
secrets={str(p):digest(p) for p in protected if p.is_file()}
history=run('sudo','-u','postgres','psql','-d','create_server','-Atc','SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank')
backup=root/'backups'/('telemetry-fix-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));backup.mkdir(mode=0o700)
shutil.copy2(root/'backend/app.jar',backup/'backend.jar');os.chmod(backup/'backend.jar',0o600)
with (backup/'database.dump').open('wb') as f:subprocess.run(['sudo','-u','postgres','pg_dump','-Fc','create_server'],stdout=f,check=True)
os.chmod(backup/'database.dump',0o600);run('pg_restore','--list',str(backup/'database.dump'))
print('BACKUP',backup,flush=True)
def healthy():
 for _ in range(50):
  try:
   if json.load(urllib.request.urlopen('http://127.0.0.1:8080/api/health',timeout=2))['status']=='UP':return
  except Exception:pass
  time.sleep(1)
 raise RuntimeError('Backend health failed')
try:
 run('systemctl','stop','create-server-backend')
 temp=root/'backend/app.jar.telemetry-new';shutil.copyfile(stage/'backend.jar',temp);os.chmod(temp,0o640);shutil.chown(temp,'createserver','createserver');os.replace(temp,root/'backend/app.jar')
 run('systemctl','start','create-server-backend');healthy()
 assert history==run('sudo','-u','postgres','psql','-d','create_server','-Atc','SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank')
 assert secrets=={str(p):digest(p) for p in protected if p.is_file()}
 for s,invocation in before.items():
  assert run('systemctl','is-active',s)=='active'
  assert run('systemctl','show',s,'-p','InvocationID','--value')==invocation
 print('Backend UP; migration history unchanged; Minecraft/Bot/WireGuard not restarted',flush=True)
except Exception:
 run('systemctl','stop','create-server-backend');shutil.copyfile(backup/'backend.jar',root/'backend/app.jar');shutil.chown(root/'backend/app.jar','createserver','createserver');os.chmod(root/'backend/app.jar',0o640)
 run('systemctl','start','create-server-backend');print('FAILED; previous Backend restored',flush=True);raise SystemExit(1)
