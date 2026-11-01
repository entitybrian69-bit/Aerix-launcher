#!/usr/bin/env python3
"""Reject retired renderer payloads and its dedicated probe in distributable APKs."""
import hashlib
import sys
import zipfile

# Exact original binaries, including renamed copies. Never redistribute these again.
RETIRED_HASHES = {
    'f7d1876591add2c6dcb15aea2e9eae22a7eea3fede68210a539a84075c15b49f',
    '106e086b6b5b111bdf0eb63f895035ce8911e013775888573487a3092ba3d271',
    'd1e43b9f27a39b36c144a71b8555bf881c3347d56fcdc494ace7fb124f54436e',
    '507ad2e47bec36a33d662f530b91b5ddc67ea8120bc3a2d8d4560cd4582f458d',
}

def verify(path):
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            name = entry.filename.lower()
            if any(marker in name for marker in ('rustgl', 'rust_gl', 'rust_renderer', 'aerix_vulkan_probe')):
                raise ValueError(f'Retired renderer entry in APK: {entry.filename}')
            if name.endswith('.so'):
                digest = hashlib.sha256()
                with archive.open(entry) as stream:
                    for block in iter(lambda: stream.read(1024 * 1024), b''):
                        digest.update(block)
                if digest.hexdigest() in RETIRED_HASHES:
                    raise ValueError(f'Renamed retired native payload in APK: {entry.filename}')
    print(f'{path}: no retired renderer payload or probe')

if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise SystemExit('Usage: verify_removed_renderer.py APK [APK ...]')
    for apk in sys.argv[1:]:
        verify(apk)
