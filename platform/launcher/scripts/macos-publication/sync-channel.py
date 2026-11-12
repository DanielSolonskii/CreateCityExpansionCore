#!/usr/bin/env python3
"""Explicit game-channel sync. Uses the deployed Mac overlay, not Windows natives.

Two channels cannot switch in one filesystem transaction. This step follows the
Windows commit; failure must be retried with sync-macos, not a new Windows publish.
"""
import argparse
import datetime
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import tempfile
import urllib.request
import uuid

spec = importlib.util.spec_from_file_location('mac_refresh', Path(__file__).with_name('refresh-channel.py'))
refresh = importlib.util.module_from_spec(spec)
spec.loader.exec_module(refresh)


def public_manifest(url):
    # Manifests only; no credentials or arbitrary third-party origins.
    with urllib.request.urlopen(url, timeout=30) as response:
        data = response.read(16 * 1024 * 1024 + 1)
    if len(data) > 16 * 1024 * 1024:
        raise ValueError('Public manifest exceeds limit')
    return json.loads(data)


def sync(root, overlay, key, public_key, expected_version=None, expected_source=None, reader=public_manifest):
    root = Path(root).resolve()
    if not re.fullmatch(r'[a-f0-9]{64}', public_key):
        raise ValueError('Invalid public key')
    if expected_version and not re.fullmatch(r'alpha-\d+\.\d+\.\d+', expected_version):
        raise ValueError('Invalid source version')
    if expected_source and not re.fullmatch(r'[a-f0-9]{64}', expected_source):
        raise ValueError('Invalid source digest')
    # Same lock as deploy-release.py, then the existing Mac publisher lock.
    # Prevent another official Windows publish between source pin and Mac switch.
    with (root.parent / 'secrets/client-publish.lock').open('a') as windows_lock, (root / '.macos-publication.lock').open('a') as mac_lock:
        fcntl.flock(windows_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        fcntl.flock(mac_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        source_file = root / 'releases/channels/alpha/manifest.json'
        source_bytes = source_file.read_bytes()
        source = json.loads(source_bytes)
        manifest = source['manifest']
        if expected_version and manifest['version'] != expected_version:
            raise ValueError('Windows channel changed; inspect current release before retry')
        if expected_source and refresh.sha(refresh.canonical(manifest)) != expected_source:
            raise ValueError('Windows release digest differs from successful publication')
        channel = root / 'releases/channels/alpha-macos-aarch64'
        # Prepare and verify without promoting: preserves old channel on validation,
        # signature, object, native-overlay or public HTTPS failure.
        candidate = refresh.refresh(root, overlay, key, public_key, False)
        envelope = json.loads((root / 'releases/versions' / candidate['version'] / 'manifest.json').read_bytes())
        url = 'https://api.recreated.ru/releases/versions/' + candidate['version'] + '/manifest.json'
        if reader(url) != envelope:
            raise ValueError('Public Mac candidate differs; current Mac channel retained')
        with tempfile.TemporaryDirectory() as work:
            refresh.verify(envelope, public_key, Path(work))
        if source_file.read_bytes() != source_bytes:
            raise ValueError('Source changed during candidate validation; Mac channel retained')
        backup = None
        changed = not (channel / 'manifest.json').exists() or json.loads((channel / 'manifest.json').read_bytes()) != envelope
        if changed:
            backup = root.parent / 'backups' / ('macos-channel-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:8])
            backup.mkdir(mode=0o700)
            if (channel / 'manifest.json').exists():
                saved = backup / 'previous-manifest.json'
                saved.write_bytes((channel / 'manifest.json').read_bytes())
                saved.chmod(0o600)
            if channel.is_symlink():
                (backup / 'previous-target.txt').write_text(os.readlink(channel))
            candidate = refresh.refresh(root, overlay, key, public_key, True)
        if source_file.read_bytes() != source_bytes:
            raise ValueError('Source drift detected; inspect both public channels')
        if reader('https://api.recreated.ru/releases/channels/alpha-macos-aarch64/manifest.json') != envelope:
            raise RuntimeError('Mac promotion may have committed; verify public channel before retry')
        return {**candidate, 'macChannelChanged': changed, 'backup': str(backup) if backup else None,
                'publicHttpsVerified': True, 'sourceVersion': manifest['version'], 'windowsChannelChanged': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path('/opt/create-server/distribution'))
    parser.add_argument('--overlay', type=Path, default=Path('/opt/create-server/macos-publication/overlay.json'))
    parser.add_argument('--key', type=Path, default=Path('/opt/create-server/secrets/release-signing.pem'))
    parser.add_argument('--public-key', required=True)
    parser.add_argument('--expected-version')
    parser.add_argument('--expected-source')
    args = parser.parse_args()
    try:
        print(json.dumps(sync(args.root, args.overlay, args.key, args.public_key, args.expected_version, args.expected_source)))
    except Exception as error:
        # No private keys, environment values or response bodies in diagnostics.
        print(json.dumps({'status': 'MAC_SYNC_FAILED', 'reason': str(error), 'windowsPublicationNotRolledBack': True}))
        raise SystemExit(1)
