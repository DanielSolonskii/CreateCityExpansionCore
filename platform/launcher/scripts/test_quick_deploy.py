import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('quick_deploy_server', Path(__file__).with_name('quick-deploy-server.py'))
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


class BackupRetention(unittest.TestCase):
    def fixture(self, root, number, time, status='DEPLOYED'):
        directory = root / ('server-deploy-' + str(number).zfill(32))
        directory.mkdir()
        world = directory / 'world.tar'
        with tarfile.open(world, 'w') as archive:
            data = b'fixture'
            item = tarfile.TarInfo('world/data/createcityexpansioncore_economy.dat')
            item.size = len(data)
            archive.addfile(item, io.BytesIO(data))
        (directory / 'state.json').write_text(json.dumps({'status': status, 'worldSha256': hashlib.sha256(world.read_bytes()).hexdigest()}))
        (directory / 'keep.jar').write_bytes(b'rollback jar')
        os.utime(world, (time, time))
        return directory

    def test_keeps_latest_world_and_all_jar_receipts(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp).resolve()
            old = self.fixture(root, 1, 100)
            new = self.fixture(root, 2, 200)
            result = helper.retain(new, root)
            self.assertEqual(result['removed'], 1)
            self.assertFalse((old / 'world.tar').exists())
            self.assertTrue((new / 'world.tar').exists())
            self.assertTrue((old / 'keep.jar').exists())
            self.assertTrue((old / 'state.json').exists())
            self.assertEqual(helper.retain(new, root)['removed'], 0)

    def test_corrupt_new_backup_preserves_old(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp).resolve()
            old = self.fixture(root, 1, 100)
            new = self.fixture(root, 2, 200)
            with (new / 'world.tar').open('ab') as stream:
                stream.write(b'corruption')
            with self.assertRaises(ValueError):
                helper.retain(new, root)
            self.assertTrue((old / 'world.tar').exists())

    def test_newer_or_unresolved_backup_prevents_all_deletion(self):
        for newer, status in [(300, 'DEPLOYED'), (150, 'FAILED')]:
            with self.subTest(newer=newer, status=status), tempfile.TemporaryDirectory() as temp:
                root = Path(temp).resolve()
                old = self.fixture(root, 1, 100)
                new = self.fixture(root, 2, 200)
                other = self.fixture(root, 3, newer, status)
                with self.assertRaises(ValueError):
                    helper.retain(new, root)
                self.assertTrue((old / 'world.tar').exists())
                self.assertTrue((other / 'world.tar').exists())


if __name__ == '__main__':
    unittest.main()
