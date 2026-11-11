"""Explicit owner-authorized Backend/Bot deployment. Never prints credentials or message bodies."""
import datetime,hashlib,json,os,pathlib,shlex,shutil,subprocess,sys,tarfile,time,urllib.request,urllib.error
root=pathlib.Path('/opt/create-server');stage=pathlib.Path(__file__).resolve().parent

def run(*a):return subprocess.check_output(a,text=True,stderr=subprocess.DEVNULL).strip()
def sha(p):
 with pathlib.Path(p).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def sql(q):return run('sudo','-u','postgres','psql','-d','create_server','-Atc',q)
def get(url):
 with urllib.request.urlopen(url,timeout=10) as r:return r.read()
def health():
 for _ in range(60):
  try:
   if json.loads(get('http://127.0.0.1:8080/api/health'))['status']=='UP':return
  except Exception:pass
  time.sleep(1)
 raise RuntimeError('Backend health gate failed')
def save(s):
 p=stage/'state.json';p.write_text(json.dumps(s));p.chmod(0o600)
def fingerprint():return hashlib.sha256(sql('SELECT u.id,u.status,c.username,c.password_hash FROM platform_user u LEFT JOIN account_credentials c ON c.platform_user_id=u.id ORDER BY u.id').encode()).hexdigest()
def telegram():
 env={}
 for line in (root/'secrets/telegram-bot.env').read_text().splitlines():
  if line.strip() and not line.lstrip().startswith('#'):
   k,v=shlex.split(line,comments=True)[0].split('=',1);env[k]=v
 cfg='url = "https://api.telegram.org/bot'+env['TELEGRAM_BOT_TOKEN']+'/getMe"\nmax-time = 20\nsilent\n'
 p=subprocess.run(['runuser','-u','createtelegram','--','curl','--config','-'],input=cfg,text=True,capture_output=True);assert p.returncode==0 and json.loads(p.stdout)['ok'] is True
phase=sys.argv[1]
if phase=='backup':
 assert not (stage/'state.json').exists();assert len(sys.argv)==4
 assert sha(stage/'backend.jar')==sys.argv[2] and sha(stage/'bot.tar')==sys.argv[3]
 for service in ['create-server-backend','create-server-telegram-bot','create-server-minecraft']:assert run('systemctl','is-active',service)=='active'
 health();backup=root/'backups'/('community-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));backup.mkdir(mode=0o700)
 shutil.copy2(root/'backend/app.jar',backup/'backend.jar');run('tar','-cpf',str(backup/'bot.tar'),'-C',str(root/'telegram-bot'),'bin','lib')
 with (backup/'database.dump').open('wb') as out:subprocess.run(['sudo','-u','postgres','pg_dump','-Fc','create_server'],stdout=out,stderr=subprocess.DEVNULL,check=True)
 run('pg_restore','--list',str(backup/'database.dump'))
 s={'backup':str(backup),'backend':sys.argv[2],'bot':sys.argv[3],'accounts':fingerprint(),'schema':sql('SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank'),'wg':sha('/etc/wireguard/wgtelegram.conf'),'routes':run('ip','route','show','default'),'caddy':sha('/etc/caddy/Caddyfile'),'backendEnv':sha(root/'secrets/backend.env'),'botEnv':sha(root/'secrets/telegram-bot.env')}
 for p in backup.iterdir():p.chmod(0o600)
 save(s);print(json.dumps({'status':'BACKUP_VERIFIED','backup':str(backup)}),flush=True)
elif phase=='deploy':
 s=json.loads((stage/'state.json').read_text());assert sha(stage/'backend.jar')==s['backend'] and sha(stage/'bot.tar')==s['bot'];health()
 extracted=stage/'bot-next';extracted.mkdir()
 with tarfile.open(stage/'bot.tar') as t:
  for m in t.getmembers():
   p=pathlib.PurePosixPath(m.name);assert not p.is_absolute() and '..' not in p.parts and not m.issym() and not m.islnk() and (m.isdir() or m.isfile())
  t.extractall(extracted,filter='data')
 dist=extracted/'create-server-telegram-bot';assert (dist/'bin/create-server-telegram-bot').is_file() and (dist/'lib').is_dir()
 run('systemctl','stop','create-server-telegram-bot','create-server-backend')
 try:
  target=root/'backend/app.jar';old=target.stat();temporary=stage/'backend-install.jar';shutil.copy2(stage/'backend.jar',temporary);os.chown(temporary,old.st_uid,old.st_gid);temporary.chmod(old.st_mode&0o777);os.replace(temporary,target)
  run('systemctl','start','create-server-backend');health()
  history=sql('SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank');assert history.startswith(s['schema']+'\n') and sql('SELECT MAX(version::int) FROM flyway_schema_history')=='12';assert fingerprint()==s['accounts']
  for folder in ['bin','lib']:
   target=root/'telegram-bot'/folder;assert target.resolve().is_relative_to(root/'telegram-bot');os.replace(target,stage/('old-bot-'+folder));shutil.copytree(dist/folder,target);run('chown','-R','root:createtelegram',str(target));run('chmod','-R','g+rX,o-rwx',str(target))
  os.chmod(root/'telegram-bot/bin/create-server-telegram-bot',0o750);run('systemctl','start','create-server-telegram-bot');time.sleep(8);telegram()
  assert run('systemctl','is-active','create-server-telegram-bot')=='active';assert run('systemctl','show','-p','NRestarts','--value','create-server-telegram-bot')=='0'
  assert sha(root/'backend/app.jar')==s['backend'] and fingerprint()==s['accounts'];assert sha('/etc/caddy/Caddyfile')==s['caddy'] and sha('/etc/wireguard/wgtelegram.conf')==s['wg'] and run('ip','route','show','default')==s['routes'];assert sha(root/'secrets/backend.env')==s['backendEnv'] and sha(root/'secrets/telegram-bot.env')==s['botEnv']
  for path in ['/api/internal/community/claim','/api/internal/game/community']:
   try:urllib.request.urlopen(urllib.request.Request('https://api.recreated.ru'+path,data=b'{}',headers={'Content-Type':'application/json'}),timeout=10);raise AssertionError('Public internal route')
   except urllib.error.HTTPError as e:assert e.code in (401,403,404)
  s['deployed']=True;save(s);print(json.dumps({'status':'DEPLOYED','schema':'12','health':'UP','telegram':'getMe OK','accountsPreserved':True,'configurationPreserved':True,'backup':s['backup']}),flush=True)
 except Exception:
  s['failed']=True;save(s);print(json.dumps({'status':'FAILED','backup':s['backup']}),flush=True);raise
else:raise RuntimeError('Unknown phase')
