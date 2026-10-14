"""Explicit production telemetry V9 deployment; existing secrets/configuration preserved."""
import pathlib, subprocess, shutil, os, hashlib, datetime, time, urllib.request, json, sys
root=pathlib.Path('/opt/create-server'); stage=pathlib.Path(__file__).parent
def run(*args): return subprocess.check_output(args,text=True).strip()
def digest(p): return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()
for name,expected in zip(['backend.jar','bot.tar','ccec.jar'],sys.argv[1:],strict=True):
    assert digest(stage/name)==expected, 'Artifact hash mismatch'
admin=run('sudo','-u','postgres','psql','-d','create_server','-Atc',"SELECT t.telegram_user_id FROM telegram_identity t JOIN account_credentials a ON a.platform_user_id=t.platform_user_id WHERE a.username='drannch1k'")
assert admin=='652868839', 'Admin identity changed; stop'
backup=root/'backups'/('telemetry-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
backup.mkdir(mode=0o700); print('BACKUP',backup,flush=True)
shutil.copy2(root/'backend/app.jar',backup/'backend.jar')
shutil.copy2(root/'minecraft/mods/createcityexpansioncore-1.0.1.jar',backup/'ccec.jar')
run('tar','-cpf',str(backup/'bot.tar'),'-C',str(root/'telegram-bot'),'.')
run('tar','-cpf',str(backup/'configuration.tar'),'/opt/create-server/secrets','/etc/systemd/system','/etc/caddy/Caddyfile',str(root/'minecraft/server.properties'),str(root/'minecraft/world/serverconfig'))
auth=root/'minecraft/world/serverconfig/createcityexpansioncore-authentication.toml'
authhash=digest(auth)
secrets_before={p.name:digest(p) for p in (root/'secrets').iterdir() if p.is_file()}
mods_before={p.name:digest(p) for p in (root/'minecraft/mods').glob('*.jar') if p.name!='createcityexpansioncore-1.0.1.jar'}
def properties():return dict(line.split('=',1) for line in (root/'minecraft/server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
properties_before=properties()
def install(src,dest):
    temp=pathlib.Path(str(dest)+'.reports-new');shutil.copyfile(src,temp);os.chmod(temp,0o640);shutil.chown(temp,'createserver','createserver');os.replace(temp,dest)
try:
    run('systemctl','stop','create-server-telegram-bot','create-server-minecraft','create-server-backend')
    assert run('systemctl','show','create-server-minecraft','-p','MainPID','--value')=='0'
    with (backup/'database.dump').open('wb') as output:
        subprocess.run(['sudo','-u','postgres','pg_dump','-Fc','create_server'],stdout=output,check=True)
    run('pg_restore','--list',str(backup/'database.dump'))
    run('tar','-cpf',str(backup/'world.tar'),'-C',str(root/'minecraft'),'world')
    run('tar','-tf',str(backup/'world.tar'))
    for p in backup.iterdir(): os.chmod(p,0o600)
    install(stage/'backend.jar',root/'backend/app.jar')
    run('systemctl','start','create-server-backend')
    for attempt in range(90):
        try:
            health=json.load(urllib.request.urlopen('http://127.0.0.1:8080/api/health',timeout=2))
            if health.get('status')=='UP': break
        except Exception: pass
        time.sleep(1)
    else: raise RuntimeError('Backend health failed')
    assert run('sudo','-u','postgres','psql','-d','create_server','-Atc',"SELECT success FROM flyway_schema_history WHERE version='9'")=='t'
    print('BACKEND UP; V9 successful',flush=True)
    extracted=stage/'bot-runtime';extracted.mkdir(mode=0o700)
    run('tar','-xf',str(stage/'bot.tar'),'-C',str(extracted))
    dist=next(extracted.iterdir())
    for folder in ['bin','lib']:
        target=root/'telegram-bot'/folder
        # Preserve old tree in backup already; retire rather than delete.
        os.replace(target,stage/('old-bot-'+folder))
        shutil.copytree(dist/folder,target)
        run('chown','-R','root:createtelegram',str(target))
        run('chmod','-R','g+rX,o-rwx',str(target))
    os.chmod(root/'telegram-bot/bin/create-server-telegram-bot',0o750)
    run('systemctl','start','create-server-telegram-bot')
    install(stage/'ccec.jar',root/'minecraft/mods/createcityexpansioncore-1.0.1.jar')
    assert digest(auth)==authhash, '4B config modified'
    run('systemctl','start','create-server-minecraft')
    invocation=run('systemctl','show','create-server-minecraft','-p','InvocationID','--value')
    for attempt in range(180):
        assert run('systemctl','show','create-server-minecraft','-p','InvocationID','--value')==invocation, 'Minecraft unexpectedly restarted'
        log=run('journalctl',f'_SYSTEMD_INVOCATION_ID={invocation}','--no-pager','-o','cat')
        if 'Done (' in log: break
        time.sleep(1)
    else: raise RuntimeError('Minecraft not ready')
    print('MINECRAFT Done; 4B config unchanged',flush=True)
    assert secrets_before=={p.name:digest(p) for p in (root/'secrets').iterdir() if p.is_file()}, 'Secrets changed'
    assert mods_before=={p.name:digest(p) for p in (root/'minecraft/mods').glob('*.jar') if p.name!='createcityexpansioncore-1.0.1.jar'}, 'Other mods changed'
    assert properties_before==properties(), 'Server properties changed'
    for attempt in range(30):
        count=run('sudo','-u','postgres','psql','-d','create_server','-Atc',"SELECT count(*) FROM server_bridge_boot WHERE online AND received_at>now()-interval '90 seconds'")
        if int(count)>0:break
        time.sleep(2)
    else:raise RuntimeError('No production heartbeat received')
    print('POSTGRES heartbeat/server snapshot received; secrets/properties/other mods unchanged',flush=True)
    print(run('systemctl','is-active','create-server-backend','create-server-telegram-bot','create-server-minecraft','wg-quick@wgtelegram','create-server-game-tls'),flush=True)
except Exception:
    subprocess.run(['systemctl','stop','create-server-telegram-bot','create-server-minecraft','create-server-backend'])
    print('DEPLOYMENT STOPPED; backups preserved; inspect before rollback',flush=True)
    raise
