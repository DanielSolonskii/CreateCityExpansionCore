"""Explicit static publication. All validation precedes the final atomic channel swap.
No Caddy configuration, service, firewall or gameplay changes.
"""
import argparse
import contextlib
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request
import uuid


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()


def sha_file(filename):
    with open(filename, 'rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def safe_relative(value):
    if not isinstance(value, str) or len(value) > 220 or not value.isascii() or re.search(r'[\\:<>"|?*\x00-\x1f]', value):
        raise RuntimeError('Unsafe path')
    parts = value.split('/')
    for part in parts:
        if not part or part in ('.', '..') or part.endswith(('.', ' ')) or re.match(r'(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)', part):
            raise RuntimeError('Unsafe path segment')
        if part.lower() in ('saves', 'screenshots', 'logs', 'crash-reports', 'cache', 'caches', 'world', 'accounts', 'tokens', 'secrets', '.git', '.recreated', 'options.txt', 'servers.dat', 'usercache.json', 'usernamecache.json') or 'fingerprint' in part.lower() or part.lower().endswith('.bak'):
            raise RuntimeError('Protected path')
    return parts


def safe_file(root, relative):
    current = pathlib.Path(root)
    if current.is_symlink():
        raise RuntimeError('Symlink root')
    for part in safe_relative(relative):
        current /= part
        if current.is_symlink():
            raise RuntimeError('Symlink ancestor')
    return current


@contextlib.contextmanager
def lock(filename):
    filename.parent.mkdir(parents=True, exist_ok=True)
    if filename.is_symlink():
        raise RuntimeError('Symlink lock refused')
    with open(filename, 'a+b') as stream:
        if os.name == 'nt':
            import msvcrt
            stream.write(b'0')
            stream.flush()
            stream.seek(0)
            msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield


def atomic_write(filename, contents, mode=0o600):
    filename.parent.mkdir(parents=True, exist_ok=True)
    temporary = filename.with_name(filename.name + '.' + uuid.uuid4().hex + '.next')
    try:
        with open(temporary, 'xb') as stream:
            stream.write(contents)
            stream.flush()
            os.fsync(stream.fileno())
        temporary.chmod(mode)
        os.replace(temporary, filename)
    finally:
        temporary.unlink(missing_ok=True)


def extract(archive, destination, allowed):
    with tarfile.open(archive) as package:
        names = set()
        for member in package.getmembers():
            name = member.name.rstrip('/')
            safe_relative(name)
            if name in names or not allowed(name, member.isdir()) or not (member.isfile() or member.isdir()):
                raise RuntimeError('Unsafe/duplicate archive member')
            names.add(name)
        package.extractall(destination, filter='data')


def validate_manifest(m, version):
    if set(m) != {'schemaVersion', 'version', 'sequence', 'platform', 'minecraftVersion', 'neoForgeVersion', 'files'}:
        raise RuntimeError('Unexpected manifest schema')
    if m['schemaVersion'] != 1 or m['version'] != version or type(m['sequence']) is not int or not 1 <= m['sequence'] <= 2147483647 or m['platform'] != 'windows-x86_64' or m['minecraftVersion'] != '1.21.1' or m['neoForgeVersion'] != '21.1.251':
        raise RuntimeError('Incompatible manifest')
    if not isinstance(m['files'], list) or not 1 <= len(m['files']) <= 30000:
        raise RuntimeError('Invalid manifest file count')
    paths = set()
    total = 0
    for e in m['files']:
        if set(e) != {'path', 'size', 'sha256', 'url', 'mode'}:
            raise RuntimeError('Unexpected entry schema')
        p = e['path']
        safe_relative(p)
        managed = p == 'launch.json' or p.startswith(('libraries/', 'assets/', 'versions/', 'runtime/java-21/', 'instances/alpha/resourcepacks/CreateServerQuests/')) or re.fullmatch(r'instances/alpha/mods/[^/]+\.jar', p)
        seeded = p.startswith(('instances/alpha/config/', 'instances/alpha/defaultconfigs/'))
        if not ((e['mode'] == 'managed' and managed) or (e['mode'] == 'seed' and seeded)) or p.lower() in paths:
            raise RuntimeError('Invalid allowlist')
        paths.add(p.lower())
        if type(e['size']) is not int or not 0 <= e['size'] <= 2 * 1024**3 or not re.fullmatch('[0-9a-f]{64}', e['sha256']) or e['url'] != 'https://api.recreated.ru/releases/objects/' + e['sha256']:
            raise RuntimeError('Invalid object metadata')
        total += e['size']
    if total > 20 * 1024**3:
        raise RuntimeError('Release too large')


def check_object(blob, entry):
    if blob.is_symlink() or not blob.is_file() or blob.stat().st_size != entry['size'] or sha_file(blob) != entry['sha256']:
        raise RuntimeError('Release object hash/size mismatch')


def verify_signature(envelope, raw_key, temporary):
    public = temporary / 'public.der'
    public.write_bytes(bytes.fromhex('302a300506032b6570032100' + raw_key))
    payload = temporary / 'verify-payload'
    payload.write_bytes(canonical(envelope['manifest']))
    signature = temporary / 'signature'
    signature.write_bytes(bytes.fromhex(envelope['signature']))
    subprocess.run(['openssl', 'pkeyutl', '-verify', '-pubin', '-keyform', 'DER', '-inkey', str(public), '-rawin', '-in', str(payload), '-sigfile', str(signature)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def public_check(url, expected):
    # Read the immutable version, never switch the channel for a smoke check.
    with urllib.request.urlopen(url, timeout=30) as response:
        if response.geturl() != url or response.read(16 * 1024**2 + 1) != expected:
            raise RuntimeError('Public candidate manifest verification failed')


def publish(root, archive, version, expected_previous, raw_key, public_origin='https://api.recreated.ru', probe=public_check):
    root = pathlib.Path(root).resolve()
    if not re.fullmatch(r'alpha-\d+\.\d+\.\d+', version) or not re.fullmatch('[0-9a-f]{64}', expected_previous) or not re.fullmatch('[0-9a-f]{64}', raw_key):
        raise RuntimeError('Invalid publication parameters')
    channel = root / 'distribution/releases/channels/alpha'
    keyfile = root / 'secrets/release-signing.pem'
    if keyfile.is_symlink() or not keyfile.is_file() or (os.name != 'nt' and keyfile.stat().st_mode & 0o077):
        raise RuntimeError('Signing key must be a protected regular server-side file')
    with lock(root / 'secrets/client-publish.lock'), tempfile.TemporaryDirectory(prefix='release-', dir=root) as staging:
        incoming = pathlib.Path(staging)
        if not channel.is_symlink() or not re.fullmatch(r'\.\./versions/alpha-\d+\.\d+\.\d+', os.readlink(channel)):
            raise RuntimeError('Existing alpha channel required')
        previous = json.loads((channel / 'manifest.json').read_bytes())
        verify_signature(previous, raw_key, incoming)
        if hashlib.sha256(canonical(previous['manifest'])).hexdigest() != expected_previous:
            raise RuntimeError('Alpha changed since preparation: re-run dry-run with a new version')
        derived = subprocess.check_output(['openssl', 'pkey', '-in', str(keyfile), '-pubout', '-outform', 'DER'], stderr=subprocess.DEVNULL)
        if derived.hex() != '302a300506032b6570032100' + raw_key:
            raise RuntimeError('Signing key does not match Launcher public key')
        prefix = 'releases/versions/' + version + '/manifest.canonical.json'
        extract(archive, incoming, lambda p, d: (not d and (p == prefix or bool(re.fullmatch('releases/objects/[0-9a-f]{64}', p)))) or (d and p in ('releases', 'releases/objects', 'releases/versions', 'releases/versions/' + version)))
        payload_file = incoming / prefix
        payload = payload_file.read_bytes()
        manifest = json.loads(payload)
        validate_manifest(manifest, version)
        if canonical(manifest) != payload or manifest['sequence'] <= previous['manifest']['sequence'] or version == previous['manifest']['version']:
            raise RuntimeError('Canonical payload/new version/greater sequence required')
        objects = root / 'distribution/releases/objects'
        uploaded = incoming / 'releases/objects'
        uploaded.mkdir(parents=True, exist_ok=True)
        expected_hashes = {e['sha256'] for e in manifest['files']}
        for blob in uploaded.iterdir():
            if blob.name not in expected_hashes or blob.is_symlink() or not blob.is_file():
                raise RuntimeError('Unexpected uploaded object')
            if sha_file(blob) != blob.name:
                raise RuntimeError('Release object hash/size mismatch')
        for entry in manifest['files']:
            blob = safe_file(incoming, 'releases/objects/' + entry['sha256'])
            if not blob.exists():
                blob = safe_file(root / 'distribution', 'releases/objects/' + entry['sha256'])
            check_object(blob, entry)
        signature = subprocess.check_output(['openssl', 'pkeyutl', '-sign', '-rawin', '-inkey', str(keyfile), '-in', str(payload_file)], stderr=subprocess.DEVNULL)
        envelope = canonical({'manifest': manifest, 'signature': signature.hex()})
        version_dir = safe_file(root / 'distribution', 'releases/versions/' + version)
        candidate = version_dir / 'manifest.json'
        if candidate.exists() and json.loads(candidate.read_bytes()) != json.loads(envelope):
            raise RuntimeError('Immutable version already exists with different content')
        objects.mkdir(parents=True, exist_ok=True)
        for source in (incoming / 'releases/objects').iterdir():
            destination = safe_file(root / 'distribution', 'releases/objects/' + source.name)
            if destination.exists():
                if sha_file(destination) != source.name:
                    raise RuntimeError('Corrupt existing blob')
            else:
                source.chmod(0o644)
                os.replace(source, destination)
        # Re-read every final object, including reused ones, after upload/install.
        for entry in manifest['files']:
            check_object(safe_file(root / 'distribution', 'releases/objects/' + entry['sha256']), entry)
        version_dir.mkdir(parents=True, exist_ok=True)
        version_dir.chmod(0o755)
        atomic_write(candidate, envelope, 0o644)
        probe(public_origin + '/releases/versions/' + version + '/manifest.json', envelope)
        backup = root / 'backups' / ('client-publish-' + uuid.uuid4().hex)
        backup.mkdir(parents=True, mode=0o700)
        atomic_write(backup / 'previous-alpha.json', canonical(previous))
        atomic_write(backup / 'previous-alpha-target.txt', os.readlink(channel).encode())
        report = {'status': 'VERIFIED', 'version': version, 'sequence': manifest['sequence'], 'files': len(manifest['files']), 'backup': str(backup), 'previousVersion': previous['manifest']['version']}
        atomic_write(backup / 'report.json', canonical(report))
        # Remove staging BEFORE commit: cleanup failure must not change the channel.
        shutil.rmtree(incoming)
        incoming.mkdir()
        next_channel = channel.with_name('alpha-next-' + uuid.uuid4().hex)
        try:
            next_channel.symlink_to('../versions/' + version, target_is_directory=True)
            os.replace(next_channel, channel)  # Last fallible production operation.
        finally:
            next_channel.unlink(missing_ok=True)
        report['status'] = 'PUBLISHED'
        return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', default='/opt/create-server')
    parser.add_argument('--archive', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--expected-previous', required=True)
    parser.add_argument('--public-key', required=True, help='Pinned public hex, not a private key')
    args = parser.parse_args()
    try:
        print(json.dumps(publish(args.root, pathlib.Path(args.archive), args.version, args.expected_previous, args.public_key)))
    except Exception as error:
        print(json.dumps({'status': 'FAILED', 'channelChanged': False, 'error': str(error)}))
        raise SystemExit(1)


if __name__ == '__main__':
    main()
