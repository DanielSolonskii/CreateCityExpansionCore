import pathlib,subprocess,time,urllib.request
def run(*args):return subprocess.check_output(args,text=True).strip()
stage=pathlib.Path('/opt/create-server/secrets/4b-stage-20261004')
c=stage/'Caddyfile'
s=c.read_text()
for name in ['gameCapability','gameTicket','gameLogout']:
 s=s.replace('handle @'+name+' { reverse_proxy 127.0.0.1:8080 }','handle @'+name+' {\n  reverse_proxy 127.0.0.1:8080\n }')
c.write_text(s)
run('caddy','validate','--config',str(c),'--adapter','caddyfile')
run('systemctl','daemon-reload')
run('systemctl','start','create-server-backend')
for _ in range(60):
 try:
  h=urllib.request.urlopen('http://127.0.0.1:8080/api/health',timeout=2).read().decode()
  if 'UP' in h:break
 except Exception:pass
 time.sleep(1)
else:raise RuntimeError('Backend failed; STOP deployment')
assert run('sudo','-u','postgres','psql','-d','create_server','-Atc',"SELECT success FROM flyway_schema_history WHERE version='7'")=='t'
print('BACKEND V7 UP',flush=True)
pathlib.Path('/etc/caddy/Caddyfile').write_text(s)
run('systemctl','reload','caddy')
run('systemctl','start','create-server-minecraft')
inv=run('systemctl','show','create-server-minecraft','-p','InvocationID','--value')
for _ in range(180):
 if run('systemctl','show','create-server-minecraft','-p','InvocationID','--value')!=inv:raise RuntimeError('Minecraft restart: STOP deployment')
 log=run('journalctl',f'_SYSTEMD_INVOCATION_ID={inv}','--no-pager','-o','cat')
 if 'Done (' in log:break
 time.sleep(1)
else:
 run('systemctl','stop','create-server-minecraft');raise RuntimeError('No Done: STOP deployment')
run('systemctl','enable','--now','create-server-game-tls','create-server-game-cert-refresh.timer')
print('MINECRAFT READY',flush=True)
