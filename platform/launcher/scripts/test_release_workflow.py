"""Hermetic staging tests. Only fake systemd, disposable worlds and test signing keys."""
import hashlib
import importlib.util
import io
import json
import os
import pathlib
import subprocess
import tarfile
import tempfile
import threading
import unittest
from unittest.mock import patch


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, pathlib.Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


client = load('client_release', 'deploy-release.py')
server = load('server_release', 'server-release.py')


class Fixtures(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='release-workflow-test-')
        self.root = pathlib.Path(self.temporary.name)
        (self.root / 'secrets').mkdir()
        (self.root / 'backups').mkdir()

    def tearDown(self):
        self.temporary.cleanup()

    def sign(self, manifest):
        payload = self.root / 'payload'
        payload.write_bytes(client.canonical(manifest))
        signature = subprocess.check_output(['openssl', 'pkeyutl', '-sign', '-rawin', '-inkey', str(self.root / 'secrets/release-signing.pem'), '-in', str(payload)], stderr=subprocess.DEVNULL)
        return {'manifest': manifest, 'signature': signature.hex()}

    def initial_client(self):
        key = self.root / 'secrets/release-signing.pem'
        subprocess.run(['openssl', 'genpkey', '-algorithm', 'ED25519', '-out', str(key)], check=True, stderr=subprocess.DEVNULL)
        key.chmod(0o600)
        self.public = subprocess.check_output(['openssl', 'pkey', '-in', str(key), '-pubout', '-outform', 'DER'], stderr=subprocess.DEVNULL).hex()[-64:]
        self.old = self.manifest('alpha-9.0.0', 100, b'old')
        version = self.root / 'distribution/releases/versions/alpha-9.0.0'
        version.mkdir(parents=True)
        (version / 'manifest.json').write_bytes(client.canonical(self.sign(self.old)))
        objects = self.root / 'distribution/releases/objects'
        objects.mkdir()
        (objects / self.old['files'][0]['sha256']).write_bytes(b'old')
        channels = self.root / 'distribution/releases/channels'
        channels.mkdir()
        self.channel = channels / 'alpha'
        self.channel.symlink_to('../versions/alpha-9.0.0', target_is_directory=True)
        self.expected = hashlib.sha256(client.canonical(self.old)).hexdigest()

    def manifest(self, version, sequence, contents):
        digest = hashlib.sha256(contents).hexdigest()
        return {'schemaVersion': 1, 'version': version, 'sequence': sequence, 'platform': 'windows-x86_64', 'minecraftVersion': '1.21.1', 'neoForgeVersion': '21.1.251', 'files': [{'path': 'launch.json', 'size': len(contents), 'sha256': digest, 'url': 'https://api.recreated.ru/releases/objects/' + digest, 'mode': 'managed'}]}

    def client_archive(self, manifest, contents=None):
        archive = self.root / 'client.tar'
        with tarfile.open(archive, 'w') as package:
            directory = tarfile.TarInfo('releases/objects')
            directory.type = tarfile.DIRTYPE
            package.addfile(directory)
            payload = client.canonical(manifest)
            member = tarfile.TarInfo('releases/versions/' + manifest['version'] + '/manifest.canonical.json')
            member.size = len(payload)
            package.addfile(member, io.BytesIO(payload))
            if contents is not None:
                member = tarfile.TarInfo('releases/objects/' + manifest['files'][0]['sha256'])
                member.size = len(contents)
                package.addfile(member, io.BytesIO(contents))
        return archive

    def assert_old(self):
        self.assertEqual(os.readlink(self.channel), '../versions/alpha-9.0.0')
        self.assertEqual(json.loads((self.channel / 'manifest.json').read_bytes())['manifest'], self.old)

    def initial_server(self):
        game = self.root / 'minecraft'
        (game / 'world').mkdir(parents=True)
        (game / 'world/level.dat').write_bytes(b'existing world')
        (game / 'mods').mkdir()
        (game / 'mods/replace.jar').write_bytes(b'old mod')
        (game / 'mods/personal.jar').write_bytes(b'unmanaged')
        (game / 'config').mkdir()
        (game / 'config/personal.properties').write_bytes(b'personal=true')
        (game / 'server.properties').write_text('level-name=world\nonline-mode=false\n')
        self.game = game

    def server_archive(self, files=None, remove=None):
        files = files if files is not None else {'mods/replace.jar': b'new mod', 'mods/voice-test.jar': b'voice', 'config/voicechat/server.properties': b'port=24454'}
        manifest = {'schemaVersion': 1, 'version': 'alpha-9.0.1', 'files': [{'path': p, 'size': len(b), 'sha256': hashlib.sha256(b).hexdigest()} for p, b in files.items()], 'remove': remove or []}
        archive = self.root / 'server.tar'
        with tarfile.open(archive, 'w') as package:
            for name, content in [('server/server-release.json', client.canonical(manifest))] + [('server/objects/' + hashlib.sha256(b).hexdigest(), b) for b in dict.fromkeys(files.values())]:
                member = tarfile.TarInfo(name)
                member.size = len(content)
                package.addfile(member, io.BytesIO(content))
        return archive


@unittest.skipIf(os.name == 'nt', 'Channel symlink atomic swap is a Linux deployment primitive; run on isolated Linux staging')
class ClientTests(Fixtures):
    def setUp(self):
        super().setUp()
        self.initial_client()

    def test_success_reuses_blob_verifies_before_commit(self):
        candidate = self.manifest('alpha-9.0.1', 101, b'old')
        archive = self.client_archive(candidate)
        def probe(url, contents):
            self.assert_old()
            envelope = json.loads(contents)
            self.assertEqual(envelope['manifest'], candidate)
            self.assertEqual(url, 'https://api.recreated.ru/releases/versions/alpha-9.0.1/manifest.json')
        result = client.publish(self.root, archive, candidate['version'], self.expected, self.public, probe=probe)
        self.assertEqual(result['status'], 'PUBLISHED')
        self.assertEqual(os.readlink(self.channel), '../versions/alpha-9.0.1')
        self.assertEqual(len(list((self.root / 'distribution/releases/objects').iterdir())), 1)

    def test_corrupt_uploaded_blob_leaves_channel(self):
        candidate = self.manifest('alpha-9.0.1', 101, b'new')
        with self.assertRaisesRegex(RuntimeError, 'hash/size'):
            client.publish(self.root, self.client_archive(candidate, b'bad'), candidate['version'], self.expected, self.public)
        self.assert_old()

    def test_corrupt_reused_blob_leaves_channel(self):
        (self.root / 'distribution/releases/objects' / self.old['files'][0]['sha256']).write_bytes(b'bad')
        candidate = self.manifest('alpha-9.0.1', 101, b'old')
        with self.assertRaisesRegex(RuntimeError, 'hash/size'):
            client.publish(self.root, self.client_archive(candidate), candidate['version'], self.expected, self.public)
        self.assert_old()

    def test_stale_predecessor_and_sequence_collision(self):
        candidate = self.manifest('alpha-9.0.1', 100, b'new')
        archive = self.client_archive(candidate, b'new')
        with self.assertRaisesRegex(RuntimeError, 'Alpha changed'):
            client.publish(self.root, archive, candidate['version'], '0' * 64, self.public)
        with self.assertRaisesRegex(RuntimeError, 'greater sequence'):
            client.publish(self.root, archive, candidate['version'], self.expected, self.public)
        self.assert_old()

    def test_https_failure_and_immutable_collision(self):
        candidate = self.manifest('alpha-9.0.1', 101, b'new')
        archive = self.client_archive(candidate, b'new')
        def failed_probe(*args):
            raise RuntimeError('fake TLS failure')
        with self.assertRaisesRegex(RuntimeError, 'TLS failure'):
            client.publish(self.root, archive, candidate['version'], self.expected, self.public, probe=failed_probe)
        self.assert_old()
        (self.root / 'distribution/releases/versions/alpha-9.0.1/manifest.json').write_text('{}')
        with self.assertRaisesRegex(RuntimeError, 'Immutable'):
            client.publish(self.root, archive, candidate['version'], self.expected, self.public, probe=lambda *args: None)
        self.assert_old()

    def test_atomic_switch_failure_leaves_channel(self):
        candidate = self.manifest('alpha-9.0.1', 101, b'new')
        archive = self.client_archive(candidate, b'new')
        replace = os.replace
        def failing_replace(source, target):
            if pathlib.Path(target) == self.channel:
                raise OSError('fake atomic swap failure')
            return replace(source, target)
        with patch.object(client.os, 'replace', side_effect=failing_replace), self.assertRaisesRegex(OSError, 'atomic swap'):
            client.publish(self.root, archive, candidate['version'], self.expected, self.public, probe=lambda *args: None)
        self.assert_old()

    def test_wrong_key_and_unsafe_archive(self):
        candidate = self.manifest('alpha-9.0.1', 101, b'new')
        archive = self.client_archive(candidate, b'new')
        with self.assertRaises(Exception):
            client.publish(self.root, archive, candidate['version'], self.expected, '0' * 64)
        self.assert_old()
        with tarfile.open(archive, 'a') as package:
            bad = tarfile.TarInfo('../../world/level.dat')
            bad.size = 3
            package.addfile(bad, io.BytesIO(b'bad'))
        with self.assertRaisesRegex(RuntimeError, 'Unsafe'):
            client.publish(self.root, archive, candidate['version'], self.expected, self.public)
        self.assert_old()

    def test_concurrent_publication_second_operator_cannot_race(self):
        candidate = self.manifest('alpha-9.0.1', 101, b'new')
        archive = self.client_archive(candidate, b'new')
        reached = threading.Event()
        resume = threading.Event()
        failures = []
        def probe(*args):
            reached.set()
            if not resume.wait(10):
                raise RuntimeError('Test synchronization timeout')
        def winner():
            try:
                client.publish(self.root, archive, candidate['version'], self.expected, self.public, probe=probe)
            except Exception as error:
                failures.append(error)
        thread = threading.Thread(target=winner)
        thread.start()
        try:
            self.assertTrue(reached.wait(10))
            with self.assertRaises(BlockingIOError):
                client.publish(self.root, archive, candidate['version'], self.expected, self.public, probe=lambda *args: None)
            self.assert_old()
        finally:
            resume.set()
            thread.join(10)
        self.assertFalse(thread.is_alive())
        self.assertEqual(failures, [])
        self.assertEqual(os.readlink(self.channel), '../versions/alpha-9.0.1')
class FakeSystemd:
    def __init__(self, fail=False, game=None):
        self.running = True
        self.fail = fail
        self.game = game
        self.events = []

    def stop(self, require_save=True):
        self.running = False
        self.events.append('stop/save')

    def start_ready(self, timeout):
        self.running = True
        self.events.append('start')
        if self.fail:
            if self.game:
                (self.game / 'world/level.dat').write_bytes(b'post-start world')
            raise RuntimeError('fake missing-mod error; no Done')
        self.events.append('Done')
        return 'f' * 32

    def verify_ready(self, timeout):
        if not self.running or self.fail:
            raise RuntimeError('Current server not ready')
        return 'f' * 32


class ServerTests(Fixtures):
    def setUp(self):
        super().setUp()
        self.initial_server()

    def test_dry_run_no_service_actions_no_production_files(self):
        adapter = FakeSystemd()
        archive = self.server_archive()
        before = sorted(str(p.relative_to(self.root)) for p in self.root.rglob('*'))
        result = server.deploy(self.root, archive, 'alpha-9.0.1', None, adapter=adapter, dry_run=True)
        self.assertEqual(result['status'], 'DRY_RUN')
        self.assertEqual(adapter.events, [])
        self.assertEqual(before, sorted(str(p.relative_to(self.root)) for p in self.root.rglob('*')))
        self.assertEqual({e['action'] for e in result['plan']['operations']}, {'added', 'changed'})

    def test_no_changes_still_requires_current_server_ready(self):
        adapter = FakeSystemd()
        archive = self.server_archive(files={'mods/replace.jar': b'old mod'})
        plan = server.deploy(self.root, archive, 'alpha-9.0.1', None, dry_run=True)
        report = server.deploy(self.root, archive, 'alpha-9.0.1', plan['planSha256'], adapter=adapter)
        self.assertEqual(report['status'], 'NO_CHANGES')
        self.assertEqual(adapter.events, [])
        self.assertTrue(report['ready'])
        adapter.running = False
        with self.assertRaisesRegex(RuntimeError, 'not ready'):
            server.deploy(self.root, archive, 'alpha-9.0.1', plan['planSha256'], adapter=adapter)

    def test_deploy_backup_before_apply_done_and_files_only_rollback(self):
        adapter = FakeSystemd()
        archive = self.server_archive()
        plan = server.deploy(self.root, archive, 'alpha-9.0.1', None, adapter=adapter, dry_run=True)
        copy = server.copy_atomic
        def checked_copy(source, target, metadata=None):
            self.assertFalse(adapter.running)
            self.assertTrue(list((self.root / 'backups').glob('server-deploy-*/world.tar')))
            return copy(source, target, metadata)
        with patch.object(server, 'copy_atomic', side_effect=checked_copy):
            result = server.deploy(self.root, archive, 'alpha-9.0.1', plan['planSha256'], adapter=adapter)
        self.assertEqual(result['status'], 'DEPLOYED')
        self.assertFalse(result['channelChanged'])
        self.assertEqual(adapter.events, ['stop/save', 'start', 'Done'])
        backup = pathlib.Path(result['backup'])
        self.assertEqual((backup / 'files/mods/replace.jar').read_bytes(), b'old mod')
        with tarfile.open(backup / 'world.tar') as package:
            self.assertEqual(package.extractfile('world/level.dat').read(), b'existing world')
        (self.game / 'world/level.dat').write_bytes(b'new gameplay')
        rolled = server.rollback(self.root, backup, adapter=adapter)
        self.assertEqual(rolled['status'], 'ROLLED_BACK')
        self.assertEqual((self.game / 'mods/replace.jar').read_bytes(), b'old mod')
        self.assertFalse((self.game / 'mods/voice-test.jar').exists())
        self.assertEqual((self.game / 'mods/personal.jar').read_bytes(), b'unmanaged')
        self.assertEqual((self.game / 'config/personal.properties').read_bytes(), b'personal=true')
        self.assertEqual((self.game / 'world/level.dat').read_bytes(), b'new gameplay')

    def test_failed_ready_stops_and_requires_explicit_rollback(self):
        adapter = FakeSystemd(fail=True, game=self.game)
        archive = self.server_archive()
        plan = server.deploy(self.root, archive, 'alpha-9.0.1', None, dry_run=True)
        with self.assertRaisesRegex(RuntimeError, 'client channel unchanged'):
            server.deploy(self.root, archive, 'alpha-9.0.1', plan['planSha256'], adapter=adapter)
        self.assertFalse(adapter.running)
        backup = next((self.root / 'backups').glob('server-deploy-*'))
        self.assertEqual(json.loads((backup / 'state.json').read_bytes())['status'], 'FAILED')
        adapter.fail = False
        server.rollback(self.root, backup, adapter=adapter, restore_world=True)
        self.assertEqual((self.game / 'world/level.dat').read_bytes(), b'existing world')
        preserved = list(self.game.glob('world.before-rollback-*'))
        self.assertEqual(len(preserved), 1)
        self.assertEqual((preserved[0] / 'level.dat').read_bytes(), b'post-start world')

    def test_stop_failure_never_changes_files(self):
        adapter = FakeSystemd()
        adapter.stop = lambda: (_ for _ in ()).throw(RuntimeError('save not confirmed'))
        archive = self.server_archive()
        plan = server.deploy(self.root, archive, 'alpha-9.0.1', None, dry_run=True)
        with self.assertRaisesRegex(RuntimeError, 'deployment failed'):
            server.deploy(self.root, archive, 'alpha-9.0.1', plan['planSha256'], adapter=adapter)
        self.assertEqual((self.game / 'mods/replace.jar').read_bytes(), b'old mod')
        self.assertFalse((self.game / 'mods/voice-test.jar').exists())

    def test_stale_plan_and_removal_precondition(self):
        adapter = FakeSystemd()
        archive = self.server_archive()
        plan = server.deploy(self.root, archive, 'alpha-9.0.1', None, dry_run=True)
        (self.game / 'mods/replace.jar').write_bytes(b'changed separately')
        with self.assertRaisesRegex(RuntimeError, 'changed since dry-run'):
            server.deploy(self.root, archive, 'alpha-9.0.1', plan['planSha256'], adapter=adapter)
        self.assertEqual(adapter.events, [])
        archive = self.server_archive(files={}, remove=[{'path': 'mods/personal.jar', 'expectedSha256': '0' * 64}])
        with self.assertRaisesRegex(RuntimeError, 'Removal precondition'):
            server.deploy(self.root, archive, 'alpha-9.0.1', None, dry_run=True)

    def test_hash_mismatch_protected_paths_and_explicit_removal(self):
        archive = self.server_archive(files={'../world/level.dat': b'danger'})
        with self.assertRaisesRegex(RuntimeError, 'Unsafe'):
            server.deploy(self.root, archive, 'alpha-9.0.1', None, dry_run=True)
        archive = self.server_archive(files={}, remove=[{'path': 'mods/replace.jar', 'expectedSha256': hashlib.sha256(b'old mod').hexdigest()}])
        plan = server.deploy(self.root, archive, 'alpha-9.0.1', None, dry_run=True)
        adapter = FakeSystemd()
        result = server.deploy(self.root, archive, 'alpha-9.0.1', plan['planSha256'], adapter=adapter)
        self.assertFalse((self.game / 'mods/replace.jar').exists())
        server.rollback(self.root, result['backup'], adapter=adapter)
        self.assertEqual((self.game / 'mods/replace.jar').read_bytes(), b'old mod')

    def test_bad_server_object_rejected_before_stop(self):
        archive = self.server_archive()
        with tempfile.TemporaryDirectory() as directory:
            package_root = pathlib.Path(directory)
            with tarfile.open(archive) as package:
                package.extractall(package_root, filter='data')
            for blob in (package_root / 'server/objects').iterdir():
                blob.write_bytes(b'bad')
            with tarfile.open(archive, 'w') as package:
                package.add(package_root / 'server', arcname='server')
        adapter = FakeSystemd()
        with self.assertRaisesRegex(RuntimeError, 'hash/size'):
            server.deploy(self.root, archive, 'alpha-9.0.1', '0' * 64, adapter=adapter)
        self.assertEqual(adapter.events, [])

    def test_rollback_conflict_preserves_independently_modified_file(self):
        adapter = FakeSystemd()
        archive = self.server_archive()
        plan = server.deploy(self.root, archive, 'alpha-9.0.1', None, dry_run=True)
        result = server.deploy(self.root, archive, 'alpha-9.0.1', plan['planSha256'], adapter=adapter)
        (self.game / 'mods/voice-test.jar').write_bytes(b'independent change')
        with self.assertRaisesRegex(RuntimeError, 'Rollback conflict'):
            server.rollback(self.root, result['backup'], adapter=adapter)
        self.assertFalse(adapter.running)
        self.assertEqual((self.game / 'mods/voice-test.jar').read_bytes(), b'independent change')
        self.assertEqual(json.loads((pathlib.Path(result['backup']) / 'state.json').read_bytes())['status'], 'ROLLING_BACK')


class ReadinessTests(unittest.TestCase):
    def test_current_invocation_done_not_stale_ready(self):
        class Ready(server.Systemd):
            def active(self): return True
            def invocation(self): return 'a' * 32
            def logs(self, invocation):
                self.last = invocation
                return 'Done (2.000s)! For help, type "help"'
        adapter = Ready()
        with patch.object(server.subprocess, 'run'):
            self.assertEqual(adapter.start_ready(1), 'a' * 32)
        self.assertEqual(adapter.last, 'a' * 32)
        adapter.active = lambda: False
        with patch.object(server.subprocess, 'run'), self.assertRaisesRegex(RuntimeError, 'before ready'):
            adapter.start_ready(1)

    def test_auto_restart_and_fatal_even_with_done_rejected(self):
        class Ready(server.Systemd):
            def active(self): return True
            def invocation(self): return next(self.ids)
            def logs(self, invocation): return 'Done (2.000s)!\nFailed to load registries'
        adapter = Ready()
        adapter.ids = iter(['a' * 32, 'b' * 32])
        with patch.object(server.subprocess, 'run'), self.assertRaisesRegex(RuntimeError, 'before ready'):
            adapter.start_ready(1)
        adapter.ids = iter(['a' * 32, 'a' * 32])
        with patch.object(server.subprocess, 'run'), self.assertRaisesRegex(RuntimeError, 'Fatal startup'):
            adapter.start_ready(1)


if __name__ == '__main__':
    unittest.main(verbosity=2)
