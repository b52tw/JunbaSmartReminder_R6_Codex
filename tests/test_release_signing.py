"""Exercise real apksigner with disposable keys; never use production credentials."""
import base64
import hashlib
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[1]
RELEASE_APK = Path(sys.argv.pop(1)).resolve()
DEBUG_APK = Path(sys.argv.pop(1)).resolve()


class ReleaseSigningTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='junba-signing-tests-')
        cls.root = Path(cls.temp.name)
        cls.env = {k: v for k, v in os.environ.items() if not k.startswith('JUNBA_')}
        cls.env.update(JUNBA_STORE_PASSWORD=secrets.token_hex(20), JUNBA_KEY_ALIAS='test-only')
        cls.env['JUNBA_KEY_PASSWORD'] = cls.env['JUNBA_STORE_PASSWORD']
        cls.env['RUNNER_TEMP'] = str(cls.root)
        key = cls.root / 'fixture.jks'
        args = ['-keystore', str(key), '-storepass:env', 'JUNBA_STORE_PASSWORD',
                '-alias', cls.env['JUNBA_KEY_ALIAS']]
        subprocess.run(['keytool', '-genkeypair', *args, '-keypass:env', 'JUNBA_KEY_PASSWORD',
                        '-storetype', 'JKS', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2',
                        '-dname', 'CN=Junba disposable test only'], env=cls.env,
                       stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=True)
        certificate = subprocess.run(['keytool', '-exportcert', *args], env=cls.env,
                                     capture_output=True, check=True).stdout
        cls.env['JUNBA_EXPECTED_CERT_SHA256'] = hashlib.sha256(certificate).hexdigest()
        cls.env['JUNBA_KEYSTORE_BASE64'] = base64.b64encode(key.read_bytes()).decode('ascii')

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def sign(self, name, overrides=None, apk=RELEASE_APK):
        environment = dict(self.env)
        environment.update(overrides or {})
        output = self.root / name
        result = subprocess.run(['bash', str(REPO / 'tools/sign_release_apk.sh'), str(apk), str(output)],
                                env=environment, text=True, capture_output=True)
        for secret in ['JUNBA_STORE_PASSWORD', 'JUNBA_KEY_PASSWORD', 'JUNBA_KEYSTORE_BASE64']:
            value = environment.get(secret, '')
            if len(value) >= 16:
                self.assertNotIn(value, result.stdout + result.stderr)
        self.assertEqual(list(self.root.glob('junba-signing.*')), [], 'private signing directory leaked')
        self.assertEqual(list(self.root.glob('.junba-apk-*')), [], 'staging APK leaked')
        return result, output

    def test_same_key_signs_repeatably(self):
        for name in ['signed-first.apk', 'signed-second.apk']:
            result, output = self.sign(name)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue(output.is_file())
            self.assertIn(self.env['JUNBA_EXPECTED_CERT_SHA256'], result.stdout)
            # Independently verify the published output, not just the temporary input.
            apksigner = Path(self.env['ANDROID_HOME']) / 'build-tools/35.0.0/apksigner'
            verify = subprocess.run([str(apksigner), 'verify', '--print-certs', str(output)],
                                    env=self.env, text=True, capture_output=True, check=True)
            self.assertIn(self.env['JUNBA_EXPECTED_CERT_SHA256'], verify.stdout)

    def test_missing_secret_stops_without_output(self):
        result, output = self.sign('missing.apk', {'JUNBA_KEYSTORE_BASE64': ''})
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(output.exists())

    def test_invalid_base64_is_cleaned(self):
        result, output = self.sign('invalid.apk', {'JUNBA_KEYSTORE_BASE64': 'invalid!'})
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(output.exists())

    def test_wrong_password_is_cleaned(self):
        result, output = self.sign('wrong-password.apk', {'JUNBA_STORE_PASSWORD': secrets.token_hex(20)})
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(output.exists())

    def test_wrong_certificate_never_publishes(self):
        result, output = self.sign('wrong-cert.apk', {'JUNBA_EXPECTED_CERT_SHA256': '0' * 64})
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(output.exists())

    def test_malformed_certificate_stops(self):
        result, output = self.sign('malformed-cert.apk', {'JUNBA_EXPECTED_CERT_SHA256': 'not-sha256'})
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(output.exists())

    def test_existing_output_is_preserved(self):
        existing = self.root / 'existing.apk'
        existing.write_bytes(b'preserve-existing-file')
        result, output = self.sign('existing.apk')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(output.read_bytes(), b'preserve-existing-file')

    def test_debug_apk_is_rejected(self):
        result, output = self.sign('debug.apk', apk=DEBUG_APK)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(output.exists())

    def test_colon_separated_uppercase_fingerprint(self):
        cert = self.env['JUNBA_EXPECTED_CERT_SHA256'].upper()
        result, output = self.sign('formatted-cert.apk', {'JUNBA_EXPECTED_CERT_SHA256': ':'.join(
            cert[i:i+2] for i in range(0, len(cert), 2))})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(output.is_file())


if __name__ == '__main__':
    unittest.main()
