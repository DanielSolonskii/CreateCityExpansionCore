"""SSH-stdin helper. No financial SavedData is exported to the workstation."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tarfile


def retain(backup, root=Path('/opt/create-server/backups')):
    root = root.resolve()
    backup = Path(backup)
    if (backup.parent != root or not re.fullmatch(r'server-deploy-[a-f0-9]{32}', backup.name)
            or backup.is_symlink() or backup.resolve().parent != root):
        raise ValueError('Invalid backup path')
    state = json.loads((backup / 'state.json').read_bytes())
    world = backup / 'world.tar'
    if state['status'] != 'DEPLOYED' or world.is_symlink():
        raise ValueError('Latest deployment backup is not complete')
    h = hashlib.sha256()
    with world.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    if h.hexdigest() != state['worldSha256']:
        raise ValueError('World backup hash mismatch')
    with tarfile.open(world) as archive:
        if not any(n.endswith('/data/createcityexpansioncore_economy.dat') for n in archive.getnames()):
            raise ValueError('Economy data missing from backup')
    older = []
    for candidate in root.glob('server-deploy-*/world.tar'):
        if candidate == world:
            continue
        if (candidate.is_symlink() or candidate.parent.is_symlink()
                or candidate.resolve().parent.parent != root
                or candidate.stat().st_mtime_ns > world.stat().st_mtime_ns):
            raise ValueError('Unsafe or newer world backup; retention stopped')
        old_state = json.loads((candidate.parent / 'state.json').read_bytes())
        if old_state['status'] not in ('DEPLOYED', 'ROLLED_BACK'):
            raise ValueError('Unresolved deployment backup; retention stopped')
        older.append(candidate)
    # Validate the entire set before deleting any old world archive. Keep JAR/state backups.
    for candidate in older:
        candidate.unlink()
    return {'worldBackup': str(world), 'sha256': h.hexdigest(), 'removed': len(older)}


def main(argv):
    if argv == ['warn']:
        fd = os.open('/opt/create-server/minecraft/console.fifo', os.O_WRONLY | os.O_NONBLOCK)
        try:
            os.write(fd, ('tellraw @a ' + json.dumps({'text': 'CCEC: обновление и перезапуск сервера через минуту. Завершите текущие действия.', 'color': 'gold'}, ensure_ascii=False) + '\n').encode())
        finally:
            os.close(fd)
        return {'warningSent': True}
    if len(argv) == 2 and argv[0] == 'verify':
        if not re.fullmatch(r'[a-f0-9]{64}', argv[1]):
            raise ValueError('Invalid candidate hash')
        subprocess.run(['systemctl', 'is-active', '--quiet', 'create-server-minecraft.service'], check=True, timeout=15)
        jar = Path('/opt/create-server/minecraft/mods/createcityexpansioncore-1.0.2.jar')
        if hashlib.sha256(jar.read_bytes()).hexdigest() != argv[1]:
            raise ValueError('Installed server JAR differs')
        result = subprocess.run(['ss', '-H', '-ltn', 'sport', '=', ':25565'], check=True, capture_output=True, timeout=15)
        if not result.stdout.strip():
            raise ValueError('Minecraft port is not listening')
        return {'serverActive': True, 'jarSha256': argv[1], 'port': 25565}
    if len(argv) == 2 and argv[0] == 'retain':
        import fcntl
        with Path('/opt/create-server/secrets/server-deploy.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            return retain(argv[1])
    raise ValueError('Unknown operation')


if __name__ == '__main__':
    print(json.dumps(main(sys.argv[1:])))
