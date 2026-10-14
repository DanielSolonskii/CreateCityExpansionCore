"""Explicit Minecraft file deployment/rollback. Never reads or switches client channels."""
import argparse
import hashlib
import importlib.util
import json
import os
import pathlib
import re
import shutil
import subprocess
import tarfile
import tempfile
import time
import uuid
import sys

if 'release_common' in sys.modules:
    common = sys.modules['release_common']
else:
    spec = importlib.util.spec_from_file_location('release_common', pathlib.Path(__file__).with_name('deploy-release.py'))
    common = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(common)
SERVICE = 'create-server-minecraft.service'


def allowed(p):
    common.safe_relative(p)
    if not (re.fullmatch(r'mods/[^/]+\.jar', p) or re.fullmatch(r'(config|defaultconfigs)/.+\.(toml|snbt|json|json5|cfg|properties)', p)) or re.search(r'token|credential|password|account|secret', p, re.I):
        raise RuntimeError('Outside server allowlist')
    return p


def load_package(archive, version, incoming):
    common.extract(archive, incoming, lambda p, d: (d and p in ('server', 'server/objects')) or (not d and (p == 'server/server-release.json' or bool(re.fullmatch('server/objects/[0-9a-f]{64}', p)))))
    manifest = json.loads((incoming / 'server/server-release.json').read_bytes())
    if set(manifest) != {'schemaVersion', 'version', 'files', 'remove'} or manifest['schemaVersion'] != 1 or manifest['version'] != version or not re.fullmatch(r'alpha-\d+\.\d+\.\d+', version):
        raise RuntimeError('Invalid server release')
    if not isinstance(manifest['files'], list) or not isinstance(manifest['remove'], list) or len(manifest['files']) + len(manifest['remove']) > 10000:
        raise RuntimeError('Invalid server file count')
    names = set()
    for e in manifest['files'] + manifest['remove']:
        allowed(e['path'])
        if e['path'].lower() in names:
            raise RuntimeError('Duplicate server path')
        names.add(e['path'].lower())
    for e in manifest['files']:
        if set(e) != {'path', 'size', 'sha256'} or type(e['size']) is not int or not 0 <= e['size'] <= 2 * 1024**3 or not re.fullmatch('[0-9a-f]{64}', e['sha256']):
            raise RuntimeError('Invalid server metadata')
        common.check_object(incoming / 'server/objects' / e['sha256'], e)
    for e in manifest['remove']:
        if set(e) != {'path', 'expectedSha256'} or not re.fullmatch('[0-9a-f]{64}', e['expectedSha256']):
            raise RuntimeError('Removal must pin expected hash')
    return manifest


def world_path(game):
    props = common.safe_file(game, 'server.properties').read_text()
    names = [line.split('=', 1)[1].strip() for line in props.splitlines() if line.startswith('level-name=')]
    name = names[-1] if names else 'world'
    # World is deliberately not a deployment artifact. Strict directory-only resolution.
    if not name or name.split('/')[0] in ('mods', 'config', 'defaultconfigs', 'libraries', 'logs', 'backups', 'secrets') or '\\' in name or ':' in name or pathlib.PurePosixPath(name).is_absolute() or any(p in ('', '.', '..') for p in name.split('/')):
        raise RuntimeError('Unsafe level-name; resolve manually before deployment')
    current = game
    for p in name.split('/'):
        current /= p
        if current.is_symlink():
            raise RuntimeError('World symlink refused')
    if not current.is_dir():
        raise RuntimeError('Existing world directory required')
    return name, current


def tree_size(root):
    total = 0
    for folder, dirs, files in os.walk(root):
        for name in dirs + files:
            item = pathlib.Path(folder) / name
            if item.is_symlink():
                raise RuntimeError('Symlink in world refused')
        total += sum((pathlib.Path(folder) / name).stat().st_size for name in files)
    return total


def plan_release(game, manifest):
    world_name, world = world_path(game)
    operations = []
    for e in manifest['files']:
        target = common.safe_file(game, e['path'])
        if target.exists() and not target.is_file():
            raise RuntimeError('Non-file destination')
        before = common.sha_file(target) if target.exists() else None
        if before != e['sha256']:
            operations.append({**e, 'action': 'changed' if before else 'added', 'before': before})
    for e in manifest['remove']:
        target = common.safe_file(game, e['path'])
        if not target.exists():
            continue  # Already removed; repeat deployment/verification is idempotent.
        if not target.is_file() or common.sha_file(target) != e['expectedSha256']:
            raise RuntimeError('Removal precondition mismatch: ' + e['path'])
        operations.append({'path': e['path'], 'action': 'removed', 'before': e['expectedSha256']})
    return {'version': manifest['version'], 'world': world_name, 'operations': operations}


class Systemd:
    def validate(self, game):
        values = subprocess.check_output(['systemctl', 'show', SERVICE, '--property=User', '--property=WorkingDirectory', '--property=ExecStop'], text=True)
        if 'User=createserver\n' not in values or 'WorkingDirectory=' + str(game) + '\n' not in values or str(game / 'stop-linux.sh') not in values:
            raise RuntimeError('Unexpected service user/directory/console stop; do not deploy blindly')

    def active(self):
        return subprocess.run(['systemctl', 'is-active', '--quiet', SERVICE]).returncode == 0

    def invocation(self):
        return subprocess.check_output(['systemctl', 'show', SERVICE, '--property=InvocationID', '--value'], text=True).strip()

    def logs(self, invocation):
        if not re.fullmatch('[0-9a-f]{32}', invocation):
            raise RuntimeError('Missing current systemd invocation ID')
        return subprocess.check_output(['journalctl', '_SYSTEMD_INVOCATION_ID=' + invocation, '--no-pager', '-o', 'cat'], text=True)

    def stop(self, require_save=True):
        was_active = self.active() or subprocess.check_output(['systemctl', 'show', SERVICE, '--property=MainPID', '--value'], text=True).strip() != '0'
        invocation = self.invocation() if was_active else None
        subprocess.run(['systemctl', 'stop', SERVICE], check=True, timeout=210)
        if self.active() or subprocess.check_output(['systemctl', 'show', SERVICE, '--property=MainPID', '--value'], text=True).strip() != '0':
            raise RuntimeError('Minecraft did not stop')
        result = subprocess.check_output(['systemctl', 'show', SERVICE, '--property=Result', '--value'], text=True).strip()
        if require_save and was_active and (result != 'success' or 'All dimensions are saved' not in self.logs(invocation)):
            raise RuntimeError('Graceful stop/save not confirmed; files not replaced')

    def start_ready(self, timeout):
        subprocess.run(['systemctl', 'reset-failed', SERVICE], check=True)
        subprocess.run(['systemctl', 'start', SERVICE], check=True, timeout=60)
        return self.verify_ready(timeout)

    def verify_ready(self, timeout):
        # Read-only check also used for an idempotent deployment and before promotion.
        invocation = self.invocation()
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.invocation() != invocation or not self.active():
                raise RuntimeError('Minecraft stopped/restarted before ready')
            logs = self.logs(invocation)
            if re.search(r'Done \([^\n]+\)!', logs):
                if re.search(r'Failed to start the minecraft server|Exception in server tick loop|Missing mandatory dependencies|Failed to load registries', logs, re.I):
                    raise RuntimeError('Fatal startup error despite ready marker')
                return invocation
            time.sleep(2)
        raise RuntimeError('Timed out waiting for current invocation Done')


def save_state(backup, state):
    common.atomic_write(backup / 'state.json', common.canonical(state))


def copy_atomic(source, target, metadata=None):
    new_dirs = []
    current = target.parent
    while not current.exists():
        new_dirs.append(current)
        current = current.parent
    target.parent.mkdir(parents=True, exist_ok=True)
    pending = target.with_name(target.name + '.' + uuid.uuid4().hex + '.next')
    try:
        shutil.copyfile(source, pending)
        with open(pending, 'r+b') as stream:
            os.fsync(stream.fileno())
        pending.chmod(metadata['mode'] if metadata else 0o640)
        if os.name != 'nt':
            if metadata:
                os.chown(pending, metadata['uid'], metadata['gid'])
            else:
                import pwd
                account = pwd.getpwnam('createserver')
                os.chown(pending, account.pw_uid, account.pw_gid)
            for directory in new_dirs:
                os.chown(directory, metadata['uid'] if metadata else account.pw_uid, metadata['gid'] if metadata else account.pw_gid)
                directory.chmod(0o750)
        os.replace(pending, target)
    finally:
        pending.unlink(missing_ok=True)


def deploy(root, archive, version, expected_plan, timeout=180, adapter=None, dry_run=False):
    root = pathlib.Path(root).resolve()
    game = root / 'minecraft'
    if game.is_symlink() or not game.is_dir():
        raise RuntimeError('Existing regular Minecraft directory required')
    adapter = adapter or Systemd()
    if not dry_run and hasattr(adapter, 'validate'):
        adapter.validate(game)
    # Dry-run extracts only into OS temporary storage, never writes production.
    with tempfile.TemporaryDirectory(prefix='server-inspect-') as directory:
        incoming = pathlib.Path(directory)
        manifest = load_package(archive, version, incoming)
        plan = plan_release(game, manifest)
        plan_hash = hashlib.sha256(common.canonical(plan)).hexdigest()
        if dry_run:
            return {'status': 'DRY_RUN', 'plan': plan, 'planSha256': plan_hash, 'channelChanged': False}
        with common.lock(root / 'secrets/server-deploy.lock'):
            if plan_hash != expected_plan:
                raise RuntimeError('Server changed since dry-run; inspect new plan')
            if plan_release(game, manifest) != plan:
                raise RuntimeError('Concurrent server file change')
            for pending in (root / 'backups').glob('server-deploy-*/state.json'):
                if json.loads(pending.read_bytes())['status'] not in ('DEPLOYED', 'ROLLED_BACK'):
                    raise RuntimeError('Unresolved previous server deployment: ' + str(pending.parent))
            if not plan['operations']:
                invocation = adapter.verify_ready(timeout)
                return {'status': 'NO_CHANGES', 'version': version, 'ready': True, 'invocation': invocation, 'channelChanged': False}
            _, world = world_path(game)
            needed = tree_size(world) + sum(common.safe_file(game, e['path']).stat().st_size for e in plan['operations'] if e['before']) + sum(e.get('size', 0) for e in plan['operations']) + 128 * 1024**2
            if shutil.disk_usage(root).free < needed:
                raise RuntimeError('Insufficient disk for world/file backup and staging')
            backup = root / 'backups' / ('server-deploy-' + uuid.uuid4().hex)
            backup.mkdir(parents=True, mode=0o700)
            state = {'status': 'STOPPING', 'version': version, 'plan': plan, 'changes': [], 'originals': {}, 'channelChanged': False}
            save_state(backup, state)
            try:
                adapter.stop()
                if plan_release(game, manifest) != plan:
                    raise RuntimeError('Files changed during graceful stop; abort and inspect')
                tree_size(world)  # Re-check links after the final save, before backup.
                state['status'] = 'BACKING_UP'
                save_state(backup, state)
                with tarfile.open(backup / 'world.tar', 'w') as package:
                    package.add(world, arcname=plan['world'], recursive=True)
                with open(backup / 'world.tar', 'r+b') as stream:
                    os.fsync(stream.fileno())
                state['worldSha256'] = common.sha_file(backup / 'world.tar')
                for e in plan['operations']:
                    if e['before']:
                        target = common.safe_file(game, e['path'])
                        original = backup / 'files' / e['path']
                        original.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy2(target, original)
                        with open(original, 'r+b') as stream:
                            os.fsync(stream.fileno())
                        if common.sha_file(original) != e['before']:
                            raise RuntimeError('Backup hash mismatch before apply')
                        stat = target.stat()
                        state['originals'][e['path']] = {'sha256': e['before'], 'mode': stat.st_mode & 0o777, 'uid': stat.st_uid, 'gid': stat.st_gid}
                state['status'] = 'APPLYING'
                save_state(backup, state)
                for e in plan['operations']:
                    target = common.safe_file(game, e['path'])
                    state['changes'].append(e)
                    save_state(backup, state)  # Record intent before changing a live file.
                    if e['action'] == 'removed':
                        target.unlink()
                    else:
                        copy_atomic(incoming / 'server/objects' / e['sha256'], target, state['originals'].get(e['path']))
                        common.check_object(target, e)
                state['status'] = 'STARTING'
                save_state(backup, state)
                state['invocation'] = adapter.start_ready(timeout)
                state['status'] = 'DEPLOYED'
                save_state(backup, state)
                return {'status': 'DEPLOYED', 'version': version, 'backup': str(backup), 'ready': True, 'channelChanged': False}
            except Exception as error:
                # Keep the failed release stopped. Never kill -9 or auto-switch clients.
                try:
                    adapter.stop(require_save=False)
                except Exception:
                    pass
                state['status'] = 'FAILED'
                state['error'] = str(error)
                save_state(backup, state)
                raise RuntimeError('Server deployment failed; client channel unchanged. Inspect/rollback: ' + str(backup)) from error


def rollback(root, backup, timeout=180, adapter=None, restore_world=False):
    root = pathlib.Path(root).resolve()
    backup = pathlib.Path(backup).resolve()
    if backup.parent != (root / 'backups').resolve() or not re.fullmatch('server-deploy-[0-9a-f]{32}', backup.name) or backup.is_symlink():
        raise RuntimeError('Unknown backup directory')
    game = root / 'minecraft'
    adapter = adapter or Systemd()
    with common.lock(root / 'secrets/server-deploy.lock'):
        state = json.loads((backup / 'state.json').read_bytes())
        if state['status'] == 'ROLLED_BACK':
            return {'status': 'ROLLED_BACK', 'backup': str(backup), 'channelChanged': False}
        for e in state['changes']:
            allowed(e['path'])
            if e['before']:
                original = common.safe_file(backup / 'files', e['path'])
                if common.sha_file(original) != e['before']:
                    raise RuntimeError('Backup hash mismatch')
        adapter.stop(require_save=False)
        state['status'] = 'ROLLING_BACK'
        save_state(backup, state)
        for e in reversed(state['changes']):
            target = common.safe_file(game, e['path'])
            current = common.sha_file(target) if target.is_file() else None
            # A previously interrupted rollback can already have restored before.
            if current == e['before']:
                continue
            if current not in (None, e.get('sha256')):
                raise RuntimeError('Rollback conflict; file changed independently: ' + e['path'])
            if e['before']:
                copy_atomic(common.safe_file(backup / 'files', e['path']), target, state['originals'][e['path']])
            elif target.exists():
                target.unlink()
        if restore_world:
            # Opt-in only: stopping a bad startup does not imply permission to erase gameplay.
            world_name, world = world_path(game)
            if world_name != state['plan']['world'] or common.sha_file(backup / 'world.tar') != state.get('worldSha256'):
                raise RuntimeError('World backup mismatch')
            with tarfile.open(backup / 'world.tar') as package:
                for member in package.getmembers():
                    p = pathlib.PurePosixPath(member.name)
                    if p.is_absolute() or '..' in p.parts or not (member.isfile() or member.isdir()) or not (member.name == world_name or member.name.startswith(world_name + '/')):
                        raise RuntimeError('Unsafe world archive')
                preserved = world.with_name(world.name + '.before-rollback-' + uuid.uuid4().hex)
                os.replace(world, preserved)  # Recoverable, never delete the current world.
                try:
                    package.extractall(game, filter='data')
                    if os.name != 'nt':
                        import pwd
                        account = pwd.getpwnam('createserver')
                        for folder, dirs, files in os.walk(world):
                            os.chown(folder, account.pw_uid, account.pw_gid)
                            for filename in files:
                                os.chown(pathlib.Path(folder) / filename, account.pw_uid, account.pw_gid)
                except Exception:
                    state['error'] = 'World restore incomplete; original current world preserved at ' + str(preserved)
                    save_state(backup, state)
                    raise
        state['invocation'] = adapter.start_ready(timeout)
        state['status'] = 'ROLLED_BACK'
        save_state(backup, state)
        return {'status': 'ROLLED_BACK', 'backup': str(backup), 'worldRestored': restore_world, 'channelChanged': False}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=('dry-run', 'deploy', 'rollback'))
    parser.add_argument('--root', default='/opt/create-server')
    parser.add_argument('--archive')
    parser.add_argument('--version')
    parser.add_argument('--expected-plan')
    parser.add_argument('--backup')
    parser.add_argument('--restore-world', action='store_true')
    parser.add_argument('--timeout', type=int, default=180)
    args = parser.parse_args()
    try:
        if args.action == 'rollback':
            report = rollback(args.root, args.backup, args.timeout, restore_world=args.restore_world)
        else:
            report = deploy(args.root, args.archive, args.version, args.expected_plan, args.timeout, dry_run=args.action == 'dry-run')
        print(json.dumps(report))
    except Exception as error:
        print(json.dumps({'status': 'FAILED', 'channelChanged': False, 'error': str(error)}))
        raise SystemExit(1)


if __name__ == '__main__':
    main()
