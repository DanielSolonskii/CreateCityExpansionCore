import importlib.util
from pathlib import Path
import tempfile
import unittest
import json
import os

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).with_name('publish-launcher.py'))
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)
def signer(m): return {'manifest': m, 'signature': 'ab' * 64}
class PublicationTest(unittest.TestCase):
    def test_stage_approve_immutable_and_failure(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'public'; exe = Path(temp) / 'installer.exe'; exe.write_bytes(b'MZtest')
            p.publish(root, '0.1.1', exe, p.digest(exe), 1, False, signer, True)
            self.assertFalse(os.path.lexists(root / 'latest'))
            p.approve(root, '0.1.1', p.digest(exe), signer)
            first = (root / 'latest').readlink()
            with self.assertRaises(ValueError): p.publish(root, '0.1.1', exe, p.digest(exe), 2, True, signer, True)
            with self.assertRaises(ValueError): p.publish(root, '0.1.2', exe, '0'*64, 2, True, signer, True)
            with self.assertRaises(ValueError): p.publish(root, '0.1.2', exe, p.digest(exe), 1, True, signer, True)
            p.publish(root, '0.1.2', exe, p.digest(exe), 2, True, signer, True)
            self.assertEqual((root / 'latest').readlink(), first)
            self.assertFalse((root / 'versions/0.1.2/update.json').exists())
            p.approve(root, '0.1.2', p.digest(exe), signer)
            self.assertTrue(json.loads((root / 'latest/update.json').read_text())['manifest']['approved'])
            self.assertEqual((root / 'latest').readlink(), Path('versions/0.1.2'))
            with self.assertRaises(ValueError): p.approve(root, '0.1.1', p.digest(exe), signer)
            for d in root.rglob('*'):
                if d.is_dir() and not d.is_symlink(): d.chmod(0o700)
    def test_signing_failure_preserves_channel(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'public'; exe = Path(temp) / 'installer.exe'; exe.write_bytes(b'MZtest')
            p.publish(root, '0.1.1', exe, p.digest(exe), 1, False, signer)
            def fail(_): raise ValueError('sign failure')
            with self.assertRaises(ValueError): p.publish(root, '0.1.2', exe, p.digest(exe), 2, True, fail)
            self.assertEqual((root / 'latest').readlink(), Path('versions/0.1.1'))
            self.assertFalse((root / 'versions/0.1.2').exists())
            for d in root.rglob('*'):
                if d.is_dir() and not d.is_symlink(): d.chmod(0o700)
    def test_rejects_invalid_artifact(self):
        with tempfile.TemporaryDirectory() as temp:
            exe = Path(temp) / 'a'; exe.write_bytes(b'not PE')
            with self.assertRaises(ValueError): p.publish(Path(temp)/'root', '../bad', exe, p.digest(exe), 1, False, signer)
            with self.assertRaises(ValueError): p.publish(Path(temp)/'root', '0.1.1', exe, p.digest(exe), 1, False, signer)
if __name__ == '__main__': unittest.main()
