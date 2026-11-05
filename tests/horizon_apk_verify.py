#!/usr/bin/env python3
"""Verify that the distributed ARM debug APK contains the real native Horizon engine."""
from io import BytesIO
from pathlib import Path
import sys
import zipfile

apk = Path(sys.argv[1])
with zipfile.ZipFile(apk) as archive:
    names = set(archive.namelist())
    for abi, cls, machine in [('arm64-v8a', 2, 183), ('armeabi-v7a', 1, 40)]:
        member = f'lib/{abi}/libhorizon_android.so'
        assert member in names, f'Missing Rust engine for {abi}'
        native = archive.read(member)
        assert b'aerixStatus' in native, f'{abi} contains the upstream launcher instead of the direct-game engine'
        header = native[:20]
        assert header[:4] == b'\x7fELF' and header[4] == cls and header[5] == 1
        assert int.from_bytes(header[18:20], 'little') == machine
    assert 'assets/engine-data.zip' in names, 'Missing generated Rust engine tables'
    with zipfile.ZipFile(BytesIO(archive.read('assets/engine-data.zip'))) as data:
        assert 'horizon-resources.json' in data.namelist()
    assert 'assets/third_party/horizon/LICENSE.txt' in names
    assert 'assets/third_party/horizon/THIRD_PARTY_NOTICES.md' in names
print('APK contains verified ARM native Horizon libraries, generated tables and notices')
