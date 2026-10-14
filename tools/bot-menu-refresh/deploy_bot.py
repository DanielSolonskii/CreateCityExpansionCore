"""Explicit Bot-only deployment. Run on the VDS from a protected staging directory."""
import datetime, hashlib, os, pathlib, shutil, subprocess, sys, time

root = pathlib.Path('/opt/create-server')
stage = pathlib.Path(__file__).parent
def run(*args):
    return subprocess.check_output(args, text=True).strip()
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
assert digest(stage / 'bot.tar') == sys.argv[1], 'Upload hash mismatch'
assert run('systemctl', 'is-active', 'create-server-telegram-bot') == 'active'
untouched = ['create-server-backend', 'create-server-minecraft', 'wg-quick@wgtelegram']
invocations = {s: run('systemctl', 'show', s, '-p', 'InvocationID', '--value') for s in untouched}
protected = list((root / 'secrets').glob('*')) + list(pathlib.Path('/etc/wireguard').glob('*.conf'))
protected += [root / 'backend/app.jar', pathlib.Path('/etc/systemd/system/create-server-telegram-bot.service')]
before = {p: digest(p) for p in protected if p.is_file()}
backup = root / 'backups' / ('bot-menu-refresh-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
backup.mkdir(mode=0o700)
run('tar', '-cpf', str(backup / 'bot.tar'), '-C', str(root / 'telegram-bot'), 'bin', 'lib')
shutil.copy2(root / 'secrets/telegram-bot.env', backup / 'telegram-bot.env')
shutil.copy2('/etc/systemd/system/create-server-telegram-bot.service', backup / 'bot.service')
for p in backup.iterdir(): os.chmod(p, 0o600)
print('BACKUP', backup, flush=True)
extracted = stage / 'runtime'
extracted.mkdir(mode=0o700)
run('tar', '-xf', str(stage / 'bot.tar'), '-C', str(extracted))
dist = next(extracted.iterdir())
try:
    run('systemctl', 'stop', 'create-server-telegram-bot')
    for name in ['bin', 'lib']:
        target = root / 'telegram-bot' / name
        os.replace(target, stage / ('old-' + name))
        shutil.copytree(dist / name, target)
        run('chown', '-R', 'root:createtelegram', str(target))
        run('chmod', '-R', 'g+rX,o-rwx', str(target))
    os.chmod(root / 'telegram-bot/bin/create-server-telegram-bot', 0o750)
    for file in dist.rglob('*'):
        if file.is_file(): assert digest(file) == digest(root / 'telegram-bot' / file.relative_to(dist))
    run('systemctl', 'start', 'create-server-telegram-bot')
    time.sleep(4)
    assert run('systemctl', 'is-active', 'create-server-telegram-bot') == 'active'
    for s, invocation in invocations.items():
        assert run('systemctl', 'is-active', s) == 'active'
        assert run('systemctl', 'show', s, '-p', 'InvocationID', '--value') == invocation
    assert before == {p: digest(p) for p in before}, 'Protected file changed'
    print('Bot ACTIVE; installed files verified; Backend/Minecraft/WireGuard unchanged', flush=True)
except Exception:
    subprocess.run(['systemctl', 'stop', 'create-server-telegram-bot'], check=False)
    for name in ['bin', 'lib']:
        old = stage / ('old-' + name)
        target = root / 'telegram-bot' / name
        if old.exists():
            if target.exists(): os.replace(target, stage / ('failed-' + name))
            os.replace(old, target)
    subprocess.run(['systemctl', 'start', 'create-server-telegram-bot'], check=False)
    print('DEPLOY FAILED; prior Bot restored; backup retained', flush=True)
    raise SystemExit(1)
