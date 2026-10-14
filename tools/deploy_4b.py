"""Explicit controlled 4B cutover; run only from protected VDS staging."""
import pathlib, subprocess, datetime, secrets, shutil, hashlib, time, urllib.request, os, sys

def run(*args): return subprocess.check_output(args, text=True).strip()
root=pathlib.Path('/opt/create-server'); stage=pathlib.Path(__file__).parent
backup=root/'backups'/('4b-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
backup.mkdir(mode=0o700); os.chmod(backup,0o700)
print('BACKUP',backup,flush=True)
def copy(src, name):
 p=pathlib.Path(src)
 if p.exists(): shutil.copy2(p,backup/name)
copy(root/'backend/app.jar','backend.jar');copy(root/'minecraft/mods/createcityexpansioncore-1.0.1.jar','ccec.jar')
copy('/etc/caddy/Caddyfile','Caddyfile');copy(root/'secrets/backend.env','backend.env')
run('tar','-cpf',str(backup/'configuration.tar'),'/etc/systemd/system/create-server-minecraft.service','/etc/systemd/system/create-server-backend.service',str(root/'secrets'),str(root/'minecraft/server.properties'),str(root/'minecraft/world/serverconfig'))
for name,expected in zip(['backend.jar','ccec.jar'],sys.argv[1:],strict=True):
 assert hashlib.sha256((stage/name).read_bytes()).hexdigest()==expected
run('apt-get','update');run('apt-get','install','-y','stunnel4')
run('systemctl','stop','create-server-minecraft')
if run('systemctl','show','create-server-minecraft','-p','MainPID','--value')!='0':raise RuntimeError('Minecraft process not stopped')
run('systemctl','stop','create-server-backend')
try:
 with (backup/'database.dump').open('wb') as out: subprocess.run(['sudo','-u','postgres','pg_dump','-Fc','create_server'],stdout=out,check=True)
 run('pg_restore','--list',str(backup/'database.dump'))
 run('tar','-cpf',str(backup/'world.tar'),'-C',str(root/'minecraft'),'world')
 run('tar','-tf',str(backup/'world.tar'))
 for f in backup.iterdir():
  if f.is_file():os.chmod(f,0o600)
 secret=secrets.token_urlsafe(48)
 env=root/'secrets/backend.env';lines=[x for x in env.read_text().splitlines() if not x.startswith(('GAME_SERVER_ID=','CCEC_BACKEND_SECRET='))]
 env.write_text('\n'.join(lines)+f'\nGAME_SERVER_ID=recreated-alpha\nCCEC_BACKEND_SECRET={secret}\n');os.chmod(env,0o600)
 mcenv=root/'secrets/minecraft-game.env';mcenv.write_text(f'CCEC_AUTH_TRANSPORT=TLS_GATEWAY\nCCEC_BACKEND_SECRET={secret}\n');os.chmod(mcenv,0o600)
 def install(src,dest):
  tmp=pathlib.Path(str(dest)+'.4b-new');shutil.copyfile(src,tmp);os.chmod(tmp,0o640);shutil.chown(tmp,'createserver','createserver');os.replace(tmp,dest)
 install(stage/'backend.jar',root/'backend/app.jar');install(stage/'ccec.jar',root/'minecraft/mods/createcityexpansioncore-1.0.1.jar')
 config=root/'minecraft/world/serverconfig/createcityexpansioncore-authentication.toml'
 config.write_text('[authentication]\nmode = "REQUIRED"\nbackendBaseUrl = "http://127.0.0.1:8080"\nserverId = "recreated-alpha"\n');os.chmod(config,0o640);shutil.chown(config,'createserver','createserver')
 drop=pathlib.Path('/etc/systemd/system/create-server-minecraft.service.d');drop.mkdir(exist_ok=True)
 shutil.copyfile(stage/'minecraft-auth.conf',drop/'game-auth.conf')
 shutil.copyfile(stage/'create-server-game-tls.service','/etc/systemd/system/create-server-game-tls.service')
 install(stage/'stunnel-game.conf',root/'minecraft/stunnel-game.conf')
 renewal=pathlib.Path('/usr/local/sbin/create-server-game-cert-refresh')
 renewal.write_text('''#!/bin/sh
set -eu
src=/var/lib/caddy/.local/share/caddy/certificates/acme-v02.api.letsencrypt.org-directory/api.recreated.ru
dst=/opt/create-server/secrets
openssl x509 -in "$src/api.recreated.ru.crt" -checkend 86400 -noout >/dev/null
if ! cmp -s "$src/api.recreated.ru.crt" "$dst/game-fullchain.pem" || ! cmp -s "$src/api.recreated.ru.key" "$dst/game-private-key.pem"; then
 install -m 600 "$src/api.recreated.ru.crt" "$dst/game-fullchain.pem.new"
 install -m 600 "$src/api.recreated.ru.key" "$dst/game-private-key.pem.new"
 mv "$dst/game-fullchain.pem.new" "$dst/game-fullchain.pem"
 mv "$dst/game-private-key.pem.new" "$dst/game-private-key.pem"
 if systemctl is-active --quiet create-server-game-tls; then systemctl restart create-server-game-tls; fi
fi
''');os.chmod(renewal,0o700);run(str(renewal))
 pathlib.Path('/etc/systemd/system/create-server-game-cert-refresh.service').write_text('[Unit]\nDescription=Refresh Minecraft TLS credential from Caddy\n[Service]\nType=oneshot\nExecStart=/usr/local/sbin/create-server-game-cert-refresh\n')
 pathlib.Path('/etc/systemd/system/create-server-game-cert-refresh.timer').write_text('[Unit]\nDescription=Check Minecraft TLS certificate renewal\n[Timer]\nOnBootSec=2min\nOnUnitActiveSec=1h\nPersistent=true\n[Install]\nWantedBy=timers.target\n')
 caddy=pathlib.Path('/etc/caddy/Caddyfile');old=caddy.read_text()
 extra='''
 @gameCapability {
  method POST
  path_regexp gameCapability ^/api/auth/devices/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/game-session-capability$
 }
 handle @gameCapability {
  reverse_proxy 127.0.0.1:8080
 }
 @gameTicket {
  method POST
  path /api/game-sessions
 }
 handle @gameTicket {
  reverse_proxy 127.0.0.1:8080
 }
 @gameLogout {
  method DELETE
  path /api/launcher/sessions/current
 }
 handle @gameLogout {
  reverse_proxy 127.0.0.1:8080
 }
'''
 candidate=stage/'Caddyfile';candidate.write_text(old.replace('api.recreated.ru {','api.recreated.ru {\n'+extra,1))
 run('caddy','validate','--config',str(candidate),'--adapter','caddyfile')
 run('systemctl','daemon-reload');run('systemctl','start','create-server-backend')
 for _ in range(60):
  try:
   health=urllib.request.urlopen('http://127.0.0.1:8080/api/health',timeout=2).read().decode()
   if 'UP' in health:break
  except Exception:pass
  time.sleep(1)
 else:raise RuntimeError('Backend health failed')
 print('BACKEND',health,flush=True)
 assert run('sudo','-u','postgres','psql','-d','create_server','-Atc',"SELECT success FROM flyway_schema_history WHERE version='7'")=='t'
 shutil.copyfile(candidate,caddy);run('systemctl','reload','caddy')
 run('systemctl','start','create-server-minecraft')
 invocation=run('systemctl','show','create-server-minecraft','-p','InvocationID','--value')
 for _ in range(180):
  if run('systemctl','show','create-server-minecraft','-p','InvocationID','--value')!=invocation:raise RuntimeError('Minecraft restarted unexpectedly')
  log=run('journalctl',f'_SYSTEMD_INVOCATION_ID={invocation}','--no-pager','-o','cat')
  if 'Done (' in log:break
  time.sleep(1)
 else:raise RuntimeError('Minecraft readiness failed')
 run('systemctl','enable','--now','create-server-game-tls','create-server-game-cert-refresh.timer')
 print('READY',flush=True)
except Exception:
 subprocess.run(['systemctl','stop','create-server-game-tls','create-server-minecraft'])
 print('DEPLOYMENT STOPPED; preserve backup and inspect before any rollback',flush=True)
 raise
