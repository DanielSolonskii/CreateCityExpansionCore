"""One explicit production deployment; preserve schema, identities and network config."""
import datetime, hashlib, json, os, pathlib, shutil, subprocess, sys, tarfile, time, urllib.request
root = pathlib.Path('/opt/create-server')
stage = pathlib.Path(__file__).resolve().parent
def run(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.DEVNULL).strip()
def sha(path):
    return hashlib.file_digest(pathlib.Path(path).open('rb'), 'sha256').hexdigest()
def sql(query):
    return run('sudo','-u','postgres','psql','-d','create_server','-Atc',query)
def protected():
    paths = list((root/'secrets').glob('*')) + list(pathlib.Path('/etc/wireguard').glob('*.conf'))
    paths += [root/'minecraft/server.properties', root/'minecraft/world/serverconfig/createcityexpansioncore-authentication.toml']
    return {str(p):sha(p) for p in paths if p.is_file()}
def healthy():
    for _ in range(60):
        try:
            data = json.load(urllib.request.urlopen('http://127.0.0.1:8080/api/health',timeout=3))
            if data.get('status') == 'UP': return
        except Exception: pass
        time.sleep(1)
    raise RuntimeError('Backend health failed')
try:
    phase = sys.argv[1]
    if phase == 'backup':
        expected = sys.argv[2:]
        assert len(expected)==2 and [sha(stage/'backend.jar'),sha(stage/'bot.tar')]==expected
        for service in ['create-server-backend','create-server-telegram-bot']:
            assert run('systemctl','is-active',service)=='active'
        backup = root/'backups'/('launcher-news-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
        backup.mkdir(mode=0o700)
        before = {'backup':str(backup),'expected':expected,'protected':protected(),
                  'schema':sql('SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank'),
                  'accounts':hashlib.sha256(sql('SELECT id,status FROM platform_user ORDER BY id').encode()).hexdigest(),
                  'route':run('ip','route','show','default'),'rules':run('ip','rule')}
        shutil.copy2(root/'backend/app.jar',backup/'backend.jar')
        run('tar','-cpf',str(backup/'bot.tar'),'-C',str(root/'telegram-bot'),'.')
        run('tar','-cpf',str(backup/'configuration.tar'),'/opt/create-server/secrets','/etc/systemd/system','/etc/wireguard','/etc/caddy/Caddyfile')
        run('systemctl','stop','create-server-telegram-bot','create-server-backend')
        with (backup/'database.dump').open('wb') as out:
            subprocess.run(['sudo','-u','postgres','pg_dump','-Fc','create_server'],stdout=out,stderr=subprocess.DEVNULL,check=True)
        run('pg_restore','--list',str(backup/'database.dump'))
        for p in backup.iterdir(): os.chmod(p,0o600)
        (stage/'state.json').write_text(json.dumps(before)); os.chmod(stage/'state.json',0o600)
        print('BACKUP',backup,flush=True)
    elif phase == 'backend':
        s=json.loads((stage/'state.json').read_text()); assert sha(stage/'backend.jar')==s['expected'][0]
        target=root/'backend/app.jar.news-new'; shutil.copyfile(stage/'backend.jar',target)
        os.chmod(target,0o640); shutil.chown(target,'createserver','createserver'); os.replace(target,root/'backend/app.jar')
        run('systemctl','start','create-server-backend'); healthy()
        assert sql('SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank')==s['schema']
        posts=json.load(urllib.request.urlopen('http://127.0.0.1:8080/api/launcher/news',timeout=5))
        assert isinstance(posts,list) and len(posts)<=50
        assert all(set(p)=={'id','title','body','releaseVersion','publishedAt','hasPhoto'} for p in posts)
        assert protected()==s['protected']
        print('BACKEND_UP; schema unchanged; public news DTO checked; posts='+str(len(posts)),flush=True)
    elif phase == 'bot':
        s=json.loads((stage/'state.json').read_text()); healthy(); assert sha(stage/'bot.tar')==s['expected'][1]
        extracted=stage/'bot-runtime'; extracted.mkdir(mode=0o700)
        with tarfile.open(stage/'bot.tar') as archive:
            assert all(not pathlib.PurePosixPath(m.name).is_absolute() and '..' not in pathlib.PurePosixPath(m.name).parts and not m.issym() and not m.islnk() for m in archive.getmembers())
            archive.extractall(extracted,filter='data')
        dist=next(extracted.iterdir())
        for folder in ['bin','lib']:
            target=root/'telegram-bot'/folder
            os.replace(target,stage/('old-bot-'+folder)); shutil.copytree(dist/folder,target)
            run('chown','-R','root:createtelegram',str(target)); run('chmod','-R','g+rX,o-rwx',str(target))
        os.chmod(root/'telegram-bot/bin/create-server-telegram-bot',0o750)
        for p in dist.rglob('*'):
            if p.is_file(): assert sha(p)==sha(root/'telegram-bot'/p.relative_to(dist))
        run('systemctl','start','create-server-telegram-bot'); time.sleep(8)
        for service in ['create-server-backend','create-server-telegram-bot','wg-quick@wgtelegram','create-server-game-tls']:
            assert run('systemctl','is-active',service)=='active'
        assert protected()==s['protected'] and run('ip','route','show','default')==s['route'] and run('ip','rule')==s['rules']
        print('BOT_ACTIVE; artifacts verified; secrets/WireGuard/routes unchanged',flush=True)
    else: raise ValueError('Unknown phase')
except Exception as error:
    print('DEPLOYMENT HALTED:',type(error).__name__, '; retain protected staging/backups for rollback',flush=True)
    sys.exit(1)
