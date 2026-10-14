"""Explicit staged/approved Launcher publication; shares only the game Ed25519 trust root."""
import argparse
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import subprocess

FILENAME = 'RE-CREATED-Launcher-x64-setup.exe'
VERSION = re.compile(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?\Z')

def digest(path):
    with Path(path).open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()

def sync_directory(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try: os.fsync(descriptor)
    finally: os.close(descriptor)

def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()

def sign(manifest, keyfile, public_key):
    keyfile = Path(keyfile)
    if keyfile.is_symlink() or not keyfile.is_file() or keyfile.stat().st_mode & 0o077:
        raise ValueError('Signing key must be a protected server-only file')
    derived = subprocess.check_output(['openssl', 'pkey', '-in', str(keyfile), '-pubout', '-outform', 'DER'], stderr=subprocess.DEVNULL)
    if derived.hex() != '302a300506032b6570032100' + public_key:
        raise ValueError('Signing key does not match Launcher trust root')
    with tempfile.TemporaryDirectory() as temp:
        payload = Path(temp) / 'payload'
        payload.write_bytes(canonical(manifest))
        signature = subprocess.check_output(['openssl', 'pkeyutl', '-sign', '-rawin', '-inkey', str(keyfile), '-in', str(payload)], stderr=subprocess.DEVNULL)
    return {'manifest': manifest, 'signature': signature.hex()}

def approve(root, version, expected, signer):
    root = Path(root)
    release = root / 'versions' / version
    if not VERSION.fullmatch(version) or not re.fullmatch('[a-f0-9]{64}', expected): raise ValueError('Invalid approval')
    metadata = json.loads((release / 'release.json').read_text())
    envelope = json.loads((release / 'candidate.json').read_text())
    m = envelope['manifest']
    if metadata['sha256'] != expected or m['sha256'] != expected or digest(release / FILENAME) != expected or m['version'] != version:
        raise ValueError('Approval hash mismatch')
    latest = root / 'latest'
    if (latest / 'update.json').exists():
        previous = json.loads((latest / 'update.json').read_text())['manifest']
        if m['sequence'] <= previous['sequence']: raise ValueError('Approval would downgrade channel sequence')
    approval = release / 'update.json'
    if approval.exists(): raise ValueError('Version already approved')
    approved_manifest = dict(m, approved=True)
    approved_envelope = signer(approved_manifest)
    # New immutable approval metadata; never overwrite installer or candidate.
    os.chmod(release, 0o755)
    try:
        with approval.open('xb') as file:
            file.write(canonical(approved_envelope)); file.flush(); os.fsync(file.fileno())
        os.chmod(approval, 0o444)
    finally: os.chmod(release, 0o555)
    sync_directory(release)
    link = root / ('.latest-' + version)
    if os.path.lexists(link): raise ValueError('Stale approval pointer; inspect manually')
    os.symlink('versions/' + version, link)
    os.replace(link, latest)
    sync_directory(root)
    return metadata

def publish(root, version, installer, expected, sequence=None, mandatory=False, signer=None, stage_only=False):
    if not VERSION.fullmatch(version) or not re.fullmatch('[a-f0-9]{64}', expected):
        raise ValueError('Invalid version/hash')
    installer = Path(installer)
    if installer.is_symlink() or not installer.is_file() or digest(installer) != expected:
        raise ValueError('Uploaded installer SHA-256 mismatch')
    with installer.open('rb') as file:
        if file.read(2) != b'MZ': raise ValueError('Not a Windows executable')
    root = Path(root)
    root.mkdir(mode=0o755, parents=True, exist_ok=True)
    versions = root / 'versions'
    versions.mkdir(mode=0o755, exist_ok=True)
    with (root / '.publish.lock').open('a') as lock:
        os.chmod(lock.name, 0o600)
        fcntl.flock(lock, fcntl.LOCK_EX)
        release = versions / version
        if release.exists(): raise ValueError('Version already published; use a new version')
        if sequence is None or sequence < 1 or signer is None: raise ValueError('Signed self-update metadata required')
        existing = [json.loads(p.read_text())['manifest']['sequence'] for p in versions.glob('*/candidate.json')]
        if existing and sequence <= max(existing): raise ValueError('Sequence must increase across staged releases')
        staging = Path(tempfile.mkdtemp(prefix='.pending-', dir=root))
        try:
            target = staging / FILENAME
            shutil.copyfile(installer, target)
            if digest(target) != expected: raise ValueError('Stored installer SHA-256 mismatch')
            metadata = dict(version=version, filename=FILENAME, size=target.stat().st_size,
                sha256=expected, publishedAt=datetime.datetime.now(datetime.timezone.utc).isoformat())
            (staging / 'release.json').write_text(json.dumps(metadata, indent=2) + '\n')
            manifest = dict(schemaVersion=1, kind='launcher', version=version, sequence=sequence,
                installerUrl='https://api.recreated.ru/launcher/versions/' + version + '/' + FILENAME,
                size=target.stat().st_size, sha256=expected, mandatory=mandatory, approved=False)
            (staging / 'candidate.json').write_bytes(canonical(signer(manifest)))
            for file in staging.iterdir():
                with file.open('rb') as handle: os.fsync(handle.fileno())
                os.chmod(file, 0o444)
            os.chmod(staging, 0o555)
            sync_directory(staging)
            os.rename(staging, release)
            sync_directory(versions)
            if not stage_only: approve(root, version, expected, signer)
            return metadata
        finally:
            if staging.exists():
                os.chmod(staging, 0o700)
                shutil.rmtree(staging)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('version')
    parser.add_argument('installer', help='Use - for --approve-existing')
    parser.add_argument('sha256')
    parser.add_argument('--sequence', type=int)
    parser.add_argument('--mandatory', action='store_true')
    parser.add_argument('--stage', action='store_true')
    parser.add_argument('--approve-existing', action='store_true')
    parser.add_argument('--public-key', required=True)
    args = parser.parse_args()
    os.umask(0o022)
    try:
        root = Path('/opt/create-server/launcher-distribution')
        root.mkdir(parents=True, exist_ok=True)
        if args.approve_existing:
            with (root / '.publish.lock').open('a') as lock:
                fcntl.flock(lock, fcntl.LOCK_EX)
                # Verify signature again before activating the candidate.
                envelope = json.loads((root / 'versions' / args.version / 'candidate.json').read_text())
                checked = sign(envelope['manifest'], '/opt/create-server/secrets/release-signing.pem', args.public_key)
                if checked != envelope: raise ValueError('Staged signature mismatch')
                info = approve(root, args.version, args.sha256, lambda manifest: sign(manifest, '/opt/create-server/secrets/release-signing.pem', args.public_key))
        else:
            info = publish(root, args.version, args.installer, args.sha256, args.sequence, args.mandatory,
                lambda manifest: sign(manifest, '/opt/create-server/secrets/release-signing.pem', args.public_key), args.stage)
        print(('STAGED' if args.stage else 'APPROVED') + ' version=' + info['version'] + ' sha256=' + info['sha256'])
    except Exception as error:
        # No environment, SSH keys, signing keys or application secrets are involved.
        print('PUBLICATION_STOPPED: ' + str(error))
        raise SystemExit(1)
