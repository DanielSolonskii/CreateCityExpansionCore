"""Explicit Backend/Bot-only controlled deployment. No Minecraft or schema changes."""
import datetime, hashlib, json, os, pathlib, shutil, subprocess, sys, time, urllib.request

root=pathlib.Path('/opt/create-server');stage=pathlib.Path(__file__).parent
def run(*args):return subprocess.check_output(args,text=True).strip()
def digest(path):return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()
for name,expected in zip(['backend.jar','bot.tar'],sys.argv[1:],strict=True):
    assert digest(stage/name)==expected,'Uploaded artifact mismatch'
assert run('systemctl','is-active','create-server-backend')=='active'
assert run('systemctl','is-active','create-server-telegram-bot')=='active'
assert run('systemctl','is-active','create-server-minecraft')=='active'
mc_invocation=run('systemctl','show','create-server-minecraft','-p','InvocationID','--value')
protected=list((root/'secrets').glob('*'))+list(pathlib.Path('/etc/wireguard').glob('*.conf'))
protected+=[root/'minecraft/mods/createcityexpansioncore-1.0.1.jar',root/'minecraft/server.properties',root/'minecraft/world/serverconfig/createcityexpansioncore-authentication.toml']
before={str(p):digest(p) for p in protected if p.is_file()}
history=run('sudo','-u','postgres','psql','-d','create_server','-Atc','SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank')
backup=root/'backups'/('menu-ux-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
backup.mkdir(mode=0o700);print('BACKUP',backup,flush=True)
shutil.copy2(root/'backend/app.jar',backup/'backend.jar')
run('tar','-cpf',str(backup/'bot.tar'),'-C',str(root/'telegram-bot'),'.')
run('tar','-cpf',str(backup/'configuration.tar'),'/opt/create-server/secrets','/etc/systemd/system','/etc/wireguard','/etc/caddy/Caddyfile')
for p in backup.iterdir():os.chmod(p,0o600)
try:
    run('systemctl','stop','create-server-telegram-bot','create-server-backend')
    with (backup/'database.dump').open('wb') as output:
        subprocess.run(['sudo','-u','postgres','pg_dump','-Fc','create_server'],stdout=output,check=True)
    os.chmod(backup/'database.dump',0o600)
    run('pg_restore','--list',str(backup/'database.dump'))
    temp=root/'backend/app.jar.menu-new';shutil.copyfile(stage/'backend.jar',temp)
    os.chmod(temp,0o640);shutil.chown(temp,'createserver','createserver');os.replace(temp,root/'backend/app.jar')
    assert digest(root/'backend/app.jar')==sys.argv[1]
    run('systemctl','start','create-server-backend')
    for _ in range(90):
        try:
            health=json.load(urllib.request.urlopen('http://127.0.0.1:8080/api/health',timeout=2))
            if health.get('status')=='UP':break
        except Exception:pass
        time.sleep(1)
    else:raise RuntimeError('Backend health failed')
    assert history==run('sudo','-u','postgres','psql','-d','create_server','-Atc','SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank'),'Migration history changed'
    print('BACKEND UP; Flyway V1-V9 unchanged',flush=True)
    extracted=stage/'bot-runtime';extracted.mkdir(mode=0o700)
    run('tar','-xf',str(stage/'bot.tar'),'-C',str(extracted))
    dist=next(extracted.iterdir())
    for folder in ['bin','lib']:
        target=root/'telegram-bot'/folder
        os.replace(target,stage/('old-bot-'+folder))
        shutil.copytree(dist/folder,target)
        run('chown','-R','root:createtelegram',str(target));run('chmod','-R','g+rX,o-rwx',str(target))
    os.chmod(root/'telegram-bot/bin/create-server-telegram-bot',0o750)
    for installed in dist.rglob('*'):
        if installed.is_file():assert digest(installed)==digest(root/'telegram-bot'/installed.relative_to(dist)),'Installed Bot artifact mismatch'
    run('systemctl','start','create-server-telegram-bot')
    time.sleep(3)
    assert before=={str(p):digest(p) for p in protected if p.is_file()},'Protected configuration changed'
    assert mc_invocation==run('systemctl','show','create-server-minecraft','-p','InvocationID','--value'),'Minecraft restarted unexpectedly'
    for service in ['create-server-backend','create-server-telegram-bot','create-server-minecraft','wg-quick@wgtelegram','create-server-game-tls']:
        assert run('systemctl','is-active',service)=='active',service+' not active'
    print('Backend/Bot ACTIVE; Minecraft invocation unchanged; secrets/WireGuard/4B unchanged',flush=True)
except Exception:
    subprocess.run(['systemctl','stop','create-server-telegram-bot','create-server-backend'])
    print('DEPLOYMENT STOPPED; Backend/Bot backups preserved; Minecraft not touched',flush=True)
    raise
