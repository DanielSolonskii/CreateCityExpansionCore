"""SavedData downgrade regression tests; disposable worlds, no SSH or live server."""
import gzip
import hashlib
import io
import json
import struct
import unittest
import zipfile

from test_release_workflow import Fixtures, FakeSystemd, server


def string(value):
    encoded = value.encode('utf-8')
    return struct.pack('>H', len(encoded)) + encoded


def integer(name, value):
    return b'\x03' + string(name) + struct.pack('>i', value)


def compound(name, body):
    return b'\x0a' + string(name) + body + b'\x00'


def saved_data(version, commerce=None, extra=b''):
    body = integer('FormatVersion', version) + extra
    if commerce is not None:
        body += compound('CargoCommerce', integer('Version', commerce))
    return gzip.compress(compound('', compound('data', body) + integer('DataVersion', 3955)))


def metadata(maximum=16):
    return {'schemaVersion': 1, 'savedData': {
        'createcityexpansioncore_economy': {
            'FormatVersion': {'min': 0, 'max': maximum},
            'CargoCommerce.Version': {'min': 1, 'max': 2, 'optional': True},
        },
        'ccec_cargo_shipments': {'Version': {'min': 1, 'max': 2}},
    }}


def jar(formats):
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w') as package:
        package.writestr('META-INF/neoforge.mods.toml', 'modId="createcityexpansioncore"')
        if formats is not None:
            package.writestr(server.SAVE_FORMATS, json.dumps(formats))
    return output.getvalue()


class SaveCompatibilityTests(Fixtures):
    def setUp(self):
        super().setUp()
        self.initial_server()
        self.data = self.game / 'world/data'
        self.data.mkdir()
        self.economy = self.data / 'createcityexpansioncore_economy.dat'
        self.economy.write_bytes(saved_data(15, 1))
        self.installed = self.game / 'mods/createcityexpansioncore-1.0.2.jar'
        self.installed.write_bytes(jar(metadata(15)))

    def candidate(self, formats=None):
        return self.server_archive(files={
            'mods/createcityexpansioncore-1.0.2.jar': jar(metadata() if formats is None else formats)
        })

    def inspect(self, archive):
        return server.deploy(self.root, archive, 'alpha-9.0.1', None, dry_run=True)

    def assert_preflight_refuses(self, archive, text):
        adapter = FakeSystemd()
        old = self.installed.read_bytes()
        original_data = self.economy.read_bytes()
        with self.assertRaisesRegex(RuntimeError, text):
            server.deploy(self.root, archive, 'alpha-9.0.1', None, adapter=adapter)
        self.assertEqual(adapter.events, [])
        self.assertEqual(self.installed.read_bytes(), old)
        self.assertEqual(self.economy.read_bytes(), original_data)
        self.assertEqual(list((self.root / 'backups').iterdir()), [])

    def test_production_economy15_rejects_legacy13_before_stop(self):
        self.assert_preflight_refuses(self.candidate(metadata(13)), 'FormatVersion=15; candidate supports 0..13')

    def test_missing_metadata_is_not_assumed_compatible(self):
        archive = self.server_archive(files={'mods/createcityexpansioncore-1.0.2.jar': jar(None)})
        self.assert_preflight_refuses(archive, 'no valid save compatibility metadata')

    def test_nested_cargo_commerce_and_shipments_versions_checked(self):
        self.economy.write_bytes(saved_data(16, 3))
        self.assert_preflight_refuses(self.candidate(), 'CargoCommerce.Version=3')
        self.economy.write_bytes(saved_data(15, 1))
        (self.data / 'ccec_cargo_shipments.dat').write_bytes(gzip.compress(compound('', compound('data', integer('Version', 3)))))
        self.assert_preflight_refuses(self.candidate(), 'ccec_cargo_shipments.dat:Version=3')

    def test_unknown_cargo_registry_is_not_ignored(self):
        (self.data / 'ccec_cargo_new_feature.dat').write_bytes(b'unknown')
        self.assert_preflight_refuses(self.candidate(), 'cannot read SavedData: ccec_cargo_new_feature')

    def test_optional_old_commerce_missing_but_required_version_present(self):
        self.economy.write_bytes(saved_data(0))
        report = self.inspect(self.candidate())
        self.assertEqual(report['plan']['saveCompatibility'], [{
            'file': self.economy.name, 'versions': {'FormatVersion': 0}
        }])
        self.economy.write_bytes(gzip.compress(compound('', compound('data', b''))))
        self.assert_preflight_refuses(self.candidate(), 'Missing SavedData version')

    def test_compatible_deploy_reports_headers_without_changing_world(self):
        before = self.economy.read_bytes()
        adapter = FakeSystemd()
        archive = self.candidate()
        plan = self.inspect(archive)
        self.assertEqual(plan['plan']['saveCompatibility'][0]['versions'], {'FormatVersion': 15, 'CargoCommerce.Version': 1})
        self.assertEqual(server.deploy(self.root, archive, 'alpha-9.0.1', plan['planSha256'], adapter=adapter)['status'], 'DEPLOYED')
        self.assertEqual(adapter.events, ['stop/save', 'start', 'Done'])
        self.assertEqual(self.economy.read_bytes(), before)

    def test_after_stop_final_save_is_rechecked_before_install(self):
        adapter = FakeSystemd()
        archive = self.candidate()
        plan = self.inspect(archive)
        before = self.installed.read_bytes()
        def stop(require_save=True):
            adapter.running = False
            adapter.events.append('stop/save')
            self.economy.write_bytes(saved_data(17, 2))
        adapter.stop = stop
        with self.assertRaisesRegex(RuntimeError, 'deployment failed'):
            server.deploy(self.root, archive, 'alpha-9.0.1', plan['planSha256'], adapter=adapter)
        self.assertEqual(self.installed.read_bytes(), before)
        self.assertNotIn('start', adapter.events)
        self.assertEqual(list((self.root / 'backups').glob('server-deploy-*/world.tar')), [])

    def test_files_only_rollback_refuses_newer_saved_data_before_stop(self):
        adapter = FakeSystemd()
        archive = self.candidate()
        plan = self.inspect(archive)
        report = server.deploy(self.root, archive, 'alpha-9.0.1', plan['planSha256'], adapter=adapter)
        self.economy.write_bytes(saved_data(16, 2))
        before = self.installed.read_bytes()
        events = adapter.events.copy()
        with self.assertRaisesRegex(RuntimeError, 'FormatVersion=16; candidate supports 0..15'):
            server.rollback(self.root, report['backup'], adapter=adapter)
        self.assertEqual(adapter.events, events)
        self.assertEqual(self.installed.read_bytes(), before)
        self.assertEqual(server.saved_versions(self.economy, ['FormatVersion']), {'FormatVersion': 16})

    def test_ccec_removal_rejected_before_stop(self):
        archive = self.server_archive(files={}, remove=[{
            'path': 'mods/createcityexpansioncore-1.0.2.jar',
            'expectedSha256': hashlib.sha256(self.installed.read_bytes()).hexdigest(),
        }])
        self.assert_preflight_refuses(archive, 'refusing CCEC removal')

    def test_corrupt_nbt_rejected_without_service_action(self):
        for raw in (b'\x0a\x00', compound('', compound('data', integer('FormatVersion', 15)))[:-1],
                    compound('', compound('data', integer('FormatVersion', 15) * 2)),
                    compound('', compound('data', b'\x09' + string('bad') + b'\x03' + struct.pack('>i', -1)))):
            with self.subTest(raw=raw):
                self.economy.write_bytes(gzip.compress(raw))
                self.assert_preflight_refuses(self.candidate(), 'NBT')

    def test_nbt_parser_skips_all_native_payload_types(self):
        extra = b''.join(bytes([kind]) + string('scalar' + str(kind)) + b'\x00' * size
                         for kind, size in ((1, 1), (2, 2), (4, 8), (5, 4), (6, 8)))
        for kind, width in ((7, 1), (11, 4), (12, 8)):
            extra += bytes([kind]) + string('array' + str(kind)) + struct.pack('>i', 2) + b'\x00' * (2 * width)
        extra += b'\x08' + string('text') + string('Маршрут')
        extra += b'\x09' + string('Parcels') + b'\x0a' + struct.pack('>i', 2) + b'\x00\x00'
        self.economy.write_bytes(saved_data(15, 1, extra))
        self.assertEqual(server.saved_versions(self.economy, ['FormatVersion', 'CargoCommerce.Version']), {'FormatVersion': 15, 'CargoCommerce.Version': 1})

    def test_invalid_metadata_and_candidate_bytes_rejected(self):
        formats = metadata()
        formats['savedData']['createcityexpansioncore_economy']['FormatVersion']['max'] = True
        self.assert_preflight_refuses(self.candidate(formats), 'Invalid CCEC SavedData version range')
        with self.assertRaisesRegex(RuntimeError, 'hash/size mismatch'):
            server.check_save_compatibility(self.game / 'world', {
                'files': [{'path': 'mods/createcityexpansioncore.jar', 'size': 3, 'sha256': '0' * 64}], 'remove': []
            }, {'0' * 64: b'bad'})


if __name__ == '__main__':
    unittest.main(verbosity=2)
