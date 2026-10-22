import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile
import hashlib
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('removed', Path(__file__).resolve().parents[1] / 'verify_removed_renderer.py')
removed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(removed)

class RemovedRendererTest(unittest.TestCase):
    def container(self, name, content=b'fixture'):
        temporary = tempfile.NamedTemporaryFile(suffix='.apk')
        self.addCleanup(temporary.close)
        with zipfile.ZipFile(temporary.name, 'w') as archive:
            archive.writestr(name, content)
        return temporary.name

    def test_other_renderers_are_allowed(self):
        removed.verify(self.container('lib/arm64-v8a/libmobileglues.so'))

    def test_retired_assets_native_and_probe_are_rejected(self):
        for name in ('assets/renderer-bundles/rustgl/index.json', 'lib/arm64-v8a/librust_gl.so',
                     'lib/arm64-v8a/libaerix_vulkan_probe.so'):
            with self.subTest(name=name), self.assertRaises(ValueError):
                removed.verify(self.container(name))

    def test_renamed_native_payload_is_rejected(self):
        with patch.object(removed, 'RETIRED_HASHES', {hashlib.sha256(b'fixture').hexdigest()}):
            with self.assertRaises(ValueError):
                removed.verify(self.container('lib/arm64-v8a/librenamed.so'))
