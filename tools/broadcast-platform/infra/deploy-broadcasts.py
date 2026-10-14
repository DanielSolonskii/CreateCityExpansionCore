"""Explicit, gated Backend/Bot V10 deployment. Run only on the authorized VDS."""
import datetime,hashlib,json,os,pathlib,shutil,subprocess,sys,time,urllib.request
root=pathlib.Path('/opt/create-server');stage=pathlib.Path(__file__).parent
def run(*args):return subprocess.check_output(args,text=True,stderr=subprocess.DEVNULL).strip()
def digest(p):return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()
def history():return run('sudo','-u','postgres','psql','-d','create_server','-Atc','SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank')
def identities():return hashlib.sha256(run('sudo','-u','postgres','psql','-d','create_server','-Atc',"SELECT u.id,u.status,c.username,c.password_hash,t.telegram_user_id FROM platform_user u LEFT JOIN account_credentials c ON c.platform_user_id=u.id LEFT JOIN telegram_identity t ON t.platform_user_id=u.id ORDER BY u.id").encode()).hexdigest()
def protected():
    files=list((root/'secrets').glob('*'))+list(pathlib.Path('/etc/wireguard').glob('*.conf'))
    files+=[root/'minecraft/server.properties',root/'minecraft/world/serverconfig/createcityexpansioncore-authentication.toml']
    return {str(p):digest(p) for p in files if p.is_file()}
def state():return json.loads((stage/'state.json').read_text())
def healthy():
    for n in range(60):
        try:
            h=json.load(urllib.request.urlopen('http://127.0.0.1:8080/api/health',timeout=2))
            if h.get('status')=='UP' and h.get('checks',{}).get('postgres')=='UP':return
        except Exception:pass
        if n in (0,20,40):print('Waiting for Backend health...',flush=True)
        time.sleep(1)
    raise RuntimeError('Backend health gate failed')
phase=sys.argv[1]
try:
    if phase=='backup':
        for service in ['create-server-backend','create-server-telegram-bot','create-server-minecraft']:
            assert run('systemctl','is-active',service)=='active'
        expected=sys.argv[2:];assert len(expected)==2
        assert digest(stage/'backend.jar')==expected[0] and digest(stage/'bot.tar')==expected[1]
        before={'protected':protected(),'history':history(),'identities':identities(),
            'minecraft':run('systemctl','show','create-server-minecraft','-p','InvocationID','--value'),
            'route':run('ip','route','show','default'),'rules':run('ip','rule'),'hashes':expected}
        assert before['history'].splitlines()[-1].startswith('9|')
        backup=root/'backups'/('broadcasts-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
        backup.mkdir(mode=0o700);before['backup']=str(backup)
        shutil.copy2(root/'backend/app.jar',backup/'backend.jar')
        run('tar','-cpf',str(backup/'bot.tar'),'-C',str(root/'telegram-bot'),'.')
        run('tar','-cpf',str(backup/'configuration.tar'),'/opt/create-server/secrets','/etc/systemd/system','/etc/wireguard','/etc/caddy/Caddyfile')
        # Quiesce account mutations/Bot before a consistent production DB backup; Minecraft stays running.
        run('systemctl','stop','create-server-telegram-bot','create-server-backend')
        with (backup/'database.dump').open('wb') as out:
            subprocess.run(['sudo','-u','postgres','pg_dump','-Fc','create_server'],stdout=out,stderr=subprocess.DEVNULL,check=True)
        run('pg_restore','--list',str(backup/'database.dump'))
        for p in backup.iterdir():os.chmod(p,0o600)
        (stage/'state.json').write_text(json.dumps(before));os.chmod(stage/'state.json',0o600)
        print('BACKUP',backup,'verified; Backend/Bot stopped; Minecraft unchanged',flush=True)
    elif phase=='backend':
        s=state();assert digest(stage/'backend.jar')==s['hashes'][0]
        if digest(root/'backend/app.jar')!=s['hashes'][0]:
            temp=root/'backend/app.jar.broadcast-new';shutil.copyfile(stage/'backend.jar',temp)
            os.chmod(temp,0o640);shutil.chown(temp,'createserver','createserver');os.replace(temp,root/'backend/app.jar')
        run('systemctl','start','create-server-backend');healthy()
        new=history().splitlines();old=s['history'].splitlines()
        assert new[:-1]==old and new[-1].startswith('10|') and new[-1].endswith('|t'),'Flyway gate failed'
        assert identities()==s['identities'],'Existing account data changed'
        print('Backend health UP; Flyway V10 applied; V1-V9 and existing accounts unchanged',flush=True)
    elif phase=='bot':
        s=state();healthy();assert digest(stage/'bot.tar')==s['hashes'][1]
        extracted=stage/'bot-runtime';extracted.mkdir(mode=0o700)
        # Local Gradle-generated artifact, never extract arbitrary remote archives.
        import tarfile
        with tarfile.open(stage/'bot.tar') as archive:
            for member in archive.getmembers():
                path=pathlib.PurePosixPath(member.name)
                assert not path.is_absolute() and '..' not in path.parts and not member.issym() and not member.islnk()
            archive.extractall(extracted,filter='data')
        dist=next(extracted.iterdir())
        for folder in ['bin','lib']:
            target=root/'telegram-bot'/folder
            assert target.resolve().parent==root/'telegram-bot'
            os.replace(target,stage/('old-bot-'+folder));shutil.copytree(dist/folder,target)
            run('chown','-R','root:createtelegram',str(target));run('chmod','-R','g+rX,o-rwx',str(target))
        os.chmod(root/'telegram-bot/bin/create-server-telegram-bot',0o750)
        for p in dist.rglob('*'):
            if p.is_file():assert digest(p)==digest(root/'telegram-bot'/p.relative_to(dist))
        run('systemctl','start','create-server-telegram-bot');time.sleep(3)
        for service in ['create-server-backend','create-server-telegram-bot','create-server-minecraft','wg-quick@wgtelegram','create-server-game-tls']:
            assert run('systemctl','is-active',service)=='active'
        assert protected()==s['protected'] and run('ip','route','show','default')==s['route'] and run('ip','rule')==s['rules']
        assert run('systemctl','show','create-server-minecraft','-p','InvocationID','--value')==s['minecraft']
        print('Backend/Bot ACTIVE; Minecraft, secrets, WireGuard and routes unchanged',flush=True)
    else:raise ValueError('Unknown phase')
except Exception:
    print('DEPLOYMENT HALTED; no next phase permitted; inspect protected backup before rollback. Error details suppressed.',flush=True)
    sys.exit(1)
