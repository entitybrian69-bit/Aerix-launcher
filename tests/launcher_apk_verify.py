#!/usr/bin/env python3
"""Ensure a built APK does not carry the removed Rust engine or activity."""
from pathlib import Path
import sys
import zipfile

with zipfile.ZipFile(Path(sys.argv[1])) as apk:
    names = apk.namelist()
    for name in names:
        assert not name.endswith('/libhorizon_android.so'), name
        assert name != 'assets/engine-data.zip', name
        assert not name.startswith('assets/third_party/horizon/'), name
        if name.startswith('classes') and name.endswith('.dex'):
            dex = apk.read(name)
            assert b'Ldev/horizon/android/HorizonActivity;' not in dex
            assert b'Ldev/horizon/android/HorizonDocumentsProvider;' not in dex
print('APK contains no Horizon Rust engine, activity or generated data')
