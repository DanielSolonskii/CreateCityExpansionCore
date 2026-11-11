#!/usr/bin/env python3
"""Derive and sign the separate Mac channel on the publication server.

Runs on Linux after a normal Windows game release. No service stops, no changes
to Windows releases. Overlay contains the curated Java and ARM64 native objects.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()

def sha(data):
    return hashlib.sha256(data).hexdigest()

def openssl(*args):
    return subprocess.check_output(['openssl', *map(str, args)], stderr=subprocess.PIPE)

def verify(envelope, public_key, work):
    message = work / 'verify.json'
    signature = work / 'verify.sig'
    public = work / 'public.der'
    message.write_bytes(canonical(envelope['manifest']))
    signature.write_bytes(bytes.fromhex(envelope['signature']))
    public.write_bytes(bytes.fromhex('302a300506032b6570032100' + public_key))
    openssl('pkeyutl', '-verify', '-pubin', '-keyform', 'DER', '-inkey', public,
            '-rawin', '-in', message, '-sigfile', signature)

def checked_object(root, entry):
    digest = entry['sha256']
    if len(digest) != 64 or any(c not in '0123456789abcdef' for c in digest):
        raise ValueError('Invalid SHA-256')
    file = root / 'releases/objects' / digest
    if file.is_symlink() or not file.is_file() or file.stat().st_size != entry['size']:
        raise ValueError('Missing or unsafe object: ' + entry['path'])
    h = hashlib.sha256()
    with file.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    if h.hexdigest() != digest:
        raise ValueError('Corrupt object: ' + entry['path'])
    if entry['url'] != 'https://api.recreated.ru/releases/objects/' + digest:
        raise ValueError('Unexpected object origin')
    return file

def safe_path(path):
    return isinstance(path, str) and not any(c in path for c in '\\:\0') and all(
        p not in ('', '.', '..') for p in path.split('/'))

def refresh(root, overlay_file, key_file, public_key, promote):
    with tempfile.TemporaryDirectory(prefix='recreated-macos-') as temporary:
        work = Path(temporary)
        derived = openssl('pkey', '-in', key_file, '-pubout', '-outform', 'DER')[-32:].hex()
        if derived != public_key:
            raise ValueError('Publication key does not match launcher trust root')
        source = json.loads((root / 'releases/channels/alpha/manifest.json').read_bytes())
        verify(source, public_key, work)
        m = source['manifest']
        if m['platform'] != 'windows-x86_64' or m['minecraftVersion'] != '1.21.1' or m['neoForgeVersion'] != '21.1.251':
            raise ValueError('Source runtime changed; prepare and test a new Mac overlay')
        overlay = json.loads(overlay_file.read_bytes())
        entries = overlay['files']
        for entry in entries:
            p = entry['path']
            if not safe_path(p) or entry['mode'] != 'managed' or not (
                    p.startswith('runtime/java-21/') or p.startswith('libraries/org/lwjgl/') and p.endswith('-natives-macos-arm64.jar') or
                    p == 'libraries/ca/weblite/java-objc-bridge/1.1/java-objc-bridge-1.1.jar'):
                raise ValueError('Unexpected overlay entry')
            checked_object(root, entry)
        launch_entry = next(f for f in m['files'] if f['path'] == 'launch.json')
        launch = json.loads(checked_object(root, launch_entry).read_bytes())
        libraries = [f for f in launch['libraries'] if '-natives-' not in f['path']]
        lwjgl = [f['path'].removesuffix('.jar') + '-natives-macos-arm64.jar'
                 for f in libraries if f['path'].startswith('libraries/org/lwjgl/')]
        natives = [f for f in entries if f['path'].startswith('libraries/org/lwjgl/')]
        if set(lwjgl) != {f['path'] for f in natives}:
            raise ValueError('LWJGL changed; prepare and test a new Mac native overlay')
        launch['libraries'] = libraries + [{'path': f['path'], 'sha256': f['sha256']} for f in entries if f['path'].startswith('libraries/')]
        launch['jvm'] = [v for v in launch['jvm'] if not v.startswith('-XX:HeapDumpPath=MojangTricks')]
        launch_bytes = canonical(launch)
        digest = sha(launch_bytes)
        object_file = root / 'releases/objects' / digest
        if not object_file.exists():
            with object_file.open('xb') as stream:
                stream.write(launch_bytes)
            object_file.chmod(0o644)
        elif object_file.read_bytes() != launch_bytes:
            raise ValueError('Object collision')
        files = [f for f in m['files'] if f['path'] != 'launch.json' and
                 not f['path'].startswith('runtime/') and '-natives-' not in f['path']]
        files += entries
        files.append({'path': 'launch.json', 'size': len(launch_bytes), 'sha256': digest,
                      'url': 'https://api.recreated.ru/releases/objects/' + digest, 'mode': 'managed'})
        if len({f['path'].lower() for f in files}) != len(files):
            raise ValueError('Duplicate release paths')
        for entry in files:
            if not safe_path(entry['path']):
                raise ValueError('Unsafe source path')
            checked_object(root, entry)
        candidate = {**m, 'platform': 'macos-aarch64', 'version': m['version'] + '-mac.1',
                     'sequence': m['sequence'] * 1000 + 1, 'files': sorted(files, key=lambda f: f['path'])}
        channel = root / 'releases/channels/alpha-macos-aarch64'
        if (channel / 'manifest.json').exists():
            previous = json.loads((channel / 'manifest.json').read_bytes())
            verify(previous, public_key, work)
            if previous['manifest']['sequence'] > candidate['sequence']:
                raise ValueError('Mac channel is ahead of source; refusing downgrade')
            if previous['manifest']['sequence'] == candidate['sequence'] and previous['manifest'] != candidate:
                raise ValueError('Immutable Mac release differs; use a new release sequence')
        manifest_file = work / 'manifest.json'
        manifest_file.write_bytes(canonical(candidate))
        signature = openssl('pkeyutl', '-sign', '-rawin', '-inkey', key_file, '-in', manifest_file)
        envelope = {'manifest': candidate, 'signature': signature.hex()}
        verify(envelope, public_key, work)
        version_dir = root / 'releases/versions' / candidate['version']
        version_dir.mkdir(exist_ok=True)
        destination = version_dir / 'manifest.json'
        encoded = canonical(envelope)
        if destination.exists() and destination.read_bytes() != encoded:
            raise ValueError('Version already exists with different content')
        if not destination.exists():
            destination.write_bytes(encoded)
            destination.chmod(0o644)
        if promote:
            # Compare source immediately before channel commit.
            if json.loads((root / 'releases/channels/alpha/manifest.json').read_bytes()) != source:
                raise ValueError('Windows source changed; retry against current release')
            temporary_link = channel.with_name('.alpha-macos-next-' + str(os.getpid()))
            os.symlink('../versions/' + candidate['version'], temporary_link)
            os.replace(temporary_link, channel)
        return {'version': candidate['version'], 'files': len(files), 'sequence': candidate['sequence'],
                'macChannelChanged': promote, 'windowsChannelChanged': False, 'servicesRestarted': False}

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path('/opt/create-server/distribution'))
    parser.add_argument('--overlay', type=Path, required=True)
    parser.add_argument('--key', type=Path, default=Path('/opt/create-server/secrets/release-signing.pem'))
    parser.add_argument('--public-key', required=True)
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    with (args.root / '.macos-publication.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        print(json.dumps(refresh(args.root, args.overlay, args.key, args.public_key, args.promote)))
