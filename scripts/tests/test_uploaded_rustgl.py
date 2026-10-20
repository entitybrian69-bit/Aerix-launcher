import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import import_uploaded_rustgl as upload
import verify_rustgl_apk as verifier


class UploadedRustGlTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.archive = SCRIPTS.parent / 'third_party/rustgl-upload/native-libraries.zip'
        self.output = self.root / 'bundle'

    def test_real_uploaded_bytes_are_imported_without_source_patches(self):
        upload.prepare(self.archive, self.output, authorized=True)
        index = json.loads((self.output / 'index.json').read_text())
        self.assertFalse(index['redistributionApproved'])
        self.assertFalse(index['upstreamSourceRevisionVerified'])
        self.assertEqual('native-archive-sha1-NOT-upstream-git', index['revisionKind'])
        self.assertEqual(4, len(index['releases']))
        with zipfile.ZipFile(self.archive) as archive:
            for abi in upload.LIBRARIES:
                self.assertEqual(archive.read(f'lib/{abi}/librust_gl.so'),
                    (self.output / upload.VERSION / abi / 'librust_gl.so').read_bytes())
        apk = self.root / 'synthetic-container.apk'  # Real native payload, NOT an installable APK.
        with zipfile.ZipFile(apk, 'w') as z:
            for file in self.output.rglob('*'):
                if file.is_file(): z.write(file, 'assets/renderer-bundles/rustgl/' + str(file.relative_to(self.output)))
        verifier.verify(apk)
        with zipfile.ZipFile(apk, 'a') as z:
            z.writestr('assets/renderer-bundles/rustgl/0.3.2/arm64-v8a/librust_gl.so', b'old')
        with self.assertRaises(AssertionError): verifier.verify(apk)

    def test_explicit_prebuilt_policy_is_required(self):
        with self.assertRaisesRegex(ValueError, 'authorization'):
            upload.prepare(self.archive, self.output)
        self.assertFalse(self.output.exists())

    def test_changed_archive_is_rejected(self):
        altered = self.root / 'altered.zip'
        altered.write_bytes(self.archive.read_bytes() + b'changed')
        with self.assertRaisesRegex(ValueError, 'Unrecognized'):
            upload.prepare(altered, self.output, authorized=True)
        self.assertFalse(self.output.exists())

    def test_stale_output_cannot_mix_previous_bundles(self):
        self.output.mkdir()
        sentinel = self.output / 'old'
        sentinel.write_text('preserve')
        with self.assertRaisesRegex(ValueError, 'absent'):
            upload.prepare(self.archive, self.output, authorized=True)
        self.assertEqual('preserve', sentinel.read_text())
