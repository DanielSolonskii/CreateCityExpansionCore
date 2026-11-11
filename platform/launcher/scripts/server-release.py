"""Explicit Minecraft file deployment/rollback. Never reads or switches client channels."""
import argparse
import gzip
import hashlib
import importlib.util
import io
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
import struct
import zipfile

if 'release_common' in sys.modules:
    common = sys.modules['release_common']
else:
    spec = importlib.util.spec_from_file_location('release_common', pathlib.Path(__file__).with_name('deploy-release.py'))
    common = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(common)
SERVICE = 'create-server-minecraft.service'
SAVE_FORMATS = 'META-INF/ccec-save-formats.json'
CCEC_JAR = re.compile(r'mods/createcityexpansioncore(?:[-_.][^/]*)?\.jar', re.I)


def saved_versions(filename, keys):
    """Read only requested integer headers; validate bounded Minecraft SavedData NBT."""
    if filename.is_symlink() or not filename.is_file() or filename.stat().st_size > 64 * 1024**2:
        raise RuntimeError('Unsafe or oversized SavedData: ' + filename.name)
    with open(filename, 'rb') as source:
        compressed = source.read(2) == b'\x1f\x8b'
    opener = gzip.open if compressed else open
    with opener(filename, 'rb') as source:
        raw = source.read(128 * 1024**2 + 1)
    if len(raw) > 128 * 1024**2:
        raise RuntimeError('Oversized SavedData NBT: ' + filename.name)
    data = memoryview(raw)
    offset = 0
    budget = 1000000
    wanted = {tuple(['data'] + key.split('.')): key for key in keys}
    result = {}

    def take(count):
        nonlocal offset
        if count < 0 or offset + count > len(data):
            raise RuntimeError('Truncated SavedData NBT: ' + filename.name)
        value = data[offset:offset + count]
        offset += count
        return value

    def number(fmt):
        return struct.unpack(fmt, take(struct.calcsize(fmt)))[0]

    def name():
        return bytes(take(number('>H'))).decode('utf-8', errors='replace')

    def payload(kind, path, depth):
        nonlocal budget
        budget -= 1
        if depth > 64 or budget < 0:
            raise RuntimeError('SavedData NBT nesting/item limit: ' + filename.name)
        if path in wanted:
            if kind != 3:
                raise RuntimeError('SavedData version must be TAG_Int: ' + filename.name)
            result[wanted[path]] = number('>i')
            return
        sizes = {1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}
        if kind in sizes:
            take(sizes[kind])
        elif kind in (7, 11, 12):
            count = number('>i')
            if count < 0:
                raise RuntimeError('Negative SavedData NBT array size')
            take(count * {7: 1, 11: 4, 12: 8}[kind])
        elif kind == 8:
            take(number('>H'))
        elif kind == 9:
            subtype, count = number('>B'), number('>i')
            if count < 0 or count > budget or not 0 <= subtype <= 12 or (subtype == 0 and count):
                raise RuntimeError('Invalid SavedData NBT list')
            for _ in range(count):
                payload(subtype, None, depth + 1)
        elif kind == 10:
            names = set()
            while True:
                subtype = number('>B')
                if subtype == 0:
                    break
                child = name()
                if child in names:
                    raise RuntimeError('Duplicate SavedData NBT tag: ' + child)
                names.add(child)
                payload(subtype, path + (child,) if path is not None else None, depth + 1)
        else:
            raise RuntimeError('Invalid SavedData NBT tag')

    if number('>B') != 10:
        raise RuntimeError('SavedData NBT root must be a compound')
    name()
    payload(10, (), 0)
    if offset != len(data):
        raise RuntimeError('Trailing SavedData NBT bytes: ' + filename.name)
    return result


def save_formats(jar):
    try:
        with zipfile.ZipFile(io.BytesIO(jar) if isinstance(jar, bytes) else jar) as package:
            entry = package.getinfo(SAVE_FORMATS)
            if entry.file_size > 32768:
                raise RuntimeError('Oversized CCEC save compatibility metadata')
            metadata = json.loads(package.read(entry))
    except (KeyError, zipfile.BadZipFile, json.JSONDecodeError) as error:
        raise RuntimeError('CCEC candidate has no valid save compatibility metadata') from error
    if set(metadata) != {'schemaVersion', 'savedData'} or metadata['schemaVersion'] != 1 or not isinstance(metadata['savedData'], dict):
        raise RuntimeError('Invalid CCEC save compatibility metadata')
    for filename, headers in metadata['savedData'].items():
        if not re.fullmatch(r'(createcityexpansioncore_economy|ccec_cargo_[a-z_]+|ccec_public_fleet)', filename) or not isinstance(headers, dict) or not headers:
            raise RuntimeError('Invalid CCEC SavedData name/headers')
        for key, rule in headers.items():
            if not re.fullmatch(r'[A-Za-z][A-Za-z0-9]*(\.[A-Za-z][A-Za-z0-9]*)*', key) or not isinstance(rule, dict) or not {'min', 'max'} <= set(rule) <= {'min', 'max', 'optional'}:
                raise RuntimeError('Invalid CCEC SavedData version rule')
            if type(rule['min']) is not int or type(rule['max']) is not int or not 0 <= rule['min'] <= rule['max'] or ('optional' in rule and type(rule['optional']) is not bool):
                raise RuntimeError('Invalid CCEC SavedData version range')
    if 'createcityexpansioncore_economy' not in metadata['savedData']:
        raise RuntimeError('CCEC candidate omits Economy compatibility')
    return metadata['savedData']


def check_save_compatibility(world, manifest, objects):
    candidates = [entry for entry in manifest['files'] if CCEC_JAR.fullmatch(entry['path'])]
    removed = any(CCEC_JAR.fullmatch(entry['path']) for entry in manifest['remove'])
    if not candidates and not removed:
        return []
    if len(candidates) != 1:
        raise RuntimeError('Exactly one CCEC candidate required; refusing CCEC removal')
    entry = candidates[0]
    if objects is None:
        raise RuntimeError('CCEC candidate bytes required for save compatibility preflight')
    jar = objects.get(entry['sha256']) if isinstance(objects, dict) else common.safe_file(objects, entry['sha256'])
    if isinstance(jar, bytes):
        if len(jar) != entry['size'] or hashlib.sha256(jar).hexdigest() != entry['sha256']:
            raise RuntimeError('CCEC preflight object hash/size mismatch')
    else:
        common.check_object(jar, entry)
    formats = save_formats(jar)
    directory = world / 'data'
    if directory.is_symlink():
        raise RuntimeError('SavedData directory symlink refused')
    report = []
    if not directory.exists():
        return report
    for filename in sorted(directory.glob('*.dat')):
        stem = filename.stem
        if stem != 'createcityexpansioncore_economy' and not stem.startswith('ccec_cargo_'):
            continue
        if stem not in formats:
            raise RuntimeError('CCEC candidate cannot read SavedData: ' + filename.name)
        rules = formats[stem]
        versions = saved_versions(filename, rules)
        for key, rule in rules.items():
            if key not in versions:
                if rule.get('optional', False):
                    continue
                raise RuntimeError('Missing SavedData version: ' + filename.name + ':' + key)
            value = versions[key]
            if not rule['min'] <= value <= rule['max']:
                raise RuntimeError(f'Incompatible SavedData {filename.name}:{key}={value}; candidate supports {rule["min"]}..{rule["max"]}')
        report.append({'file': filename.name, 'versions': versions})
    return report


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


def plan_release(game, manifest, objects=None):
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
    compatibility = check_save_compatibility(world, manifest, objects)
    return {'version': manifest['version'], 'world': world_name, 'operations': operations, 'saveCompatibility': compatibility}


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
        objects = incoming / 'server/objects'
        plan = plan_release(game, manifest, objects)
        plan_hash = hashlib.sha256(common.canonical(plan)).hexdigest()
        if dry_run:
            return {'status': 'DRY_RUN', 'plan': plan, 'planSha256': plan_hash, 'channelChanged': False}
        with common.lock(root / 'secrets/server-deploy.lock'):
            if plan_hash != expected_plan:
                raise RuntimeError('Server changed since dry-run; inspect new plan')
            if plan_release(game, manifest, objects) != plan:
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
                if plan_release(game, manifest, objects) != plan:
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
        # A files-only rollback must never start an older CCEC against newer data.
        # Legacy JARs without a declared format range require manual recovery.
        rollback_files = []
        rollback_objects = {}
        rollback_removed = []
        for e in state['changes']:
            if not CCEC_JAR.fullmatch(e['path']):
                continue
            if e['before']:
                original = common.safe_file(backup / 'files', e['path'])
                rollback_files.append({'path': e['path'], 'sha256': e['before'], 'size': original.stat().st_size})
                rollback_objects[e['before']] = original.read_bytes()
            else:
                rollback_removed.append({'path': e['path']})
        _, current_world = world_path(game)
        rollback_manifest = {'files': rollback_files, 'remove': rollback_removed}
        check_save_compatibility(current_world, rollback_manifest, rollback_objects)
        adapter.stop(require_save=False)
        check_save_compatibility(current_world, rollback_manifest, rollback_objects)
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
