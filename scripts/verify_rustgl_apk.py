#!/usr/bin/env python3
"""Verify generated APK assets against the exact native-bundle manifest."""
import hashlib
import json
import sys
import zipfile


def verify(path):
    base = "assets/renderer-bundles/rustgl"
    with zipfile.ZipFile(path) as archive:
        index = json.loads(archive.read(base + "/index.json"))
        assert index["schema"] == 1 and index["backend"] == "gles"
        assert index.get("redistributionApproved") or index.get("experimentalUserAuthorized")
        seen = set()
        for release in index["releases"]:
            abi = release["abi"]
            for entry in release["files"]:
                data = archive.read(f"{base}/{release['version']}/{abi}/{entry['name']}")
                assert len(data) == entry["bytes"] and hashlib.sha256(data).hexdigest() == entry["sha256"], entry["name"]
                if entry["name"] == "librust_gl.so":
                    assert data[:4] == b"\x7fELF"
                    seen.add(abi)
        assert seen == {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}, seen
    print(f"{path}: RustGL GLES bundle present and checksummed for all four ABIs")


if __name__ == "__main__":
    for apk in sys.argv[1:]:
        verify(apk)
