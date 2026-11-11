#!/usr/bin/env python3
"""Ensure a built APK carries none of the removed Rust engine and none of the removed Trinkies.

The Trinkies cosmetics pipeline was removed deliberately, so this also fails the build if any of it
creeps back into a shipped artifact: a packaged catalog, the equipped.json reader, the wardrobe
screen in a dex, or the client mod JAR that nothing installs any more. An APK that quietly regains
them is not the APK anyone asked for.
"""
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

with zipfile.ZipFile(Path(sys.argv[1])) as apk:
    names = apk.namelist()
    for name in names:
        assert not name.endswith('assets/trinkies/catalog.json'), (
            f'{name}: the removed Trinkies catalog is packaged again'
        )

    for name in names:
        # Nothing in a shipped APK may reach for the removed pipeline, and the client mod is no longer
        # embedded because no launcher code deploys it any more: an asset nothing reads is weight the
        # user carries for a feature that was deliberately thrown out.
        assert 'trinkies' not in name.lower(), f'{name}: a removed Trinkies asset is packaged again'
        assert not name.endswith('components/aerix_client/aerix_client_mod.jar'), (
            f'{name}: the client mod JAR is packaged but nothing installs it'
        )
    for dex_name in [n for n in names if n.startswith('classes') and n.endswith('.dex')]:
        dex = apk.read(dex_name)
        assert b'TrinkiesWardrobeScreen' not in dex, f'{dex_name} still contains the wardrobe screen'
        assert b'aerix/client/mod/TrinkiesLoadout' not in dex, f'{dex_name} still contains the reader'
print('APK carries no Trinkies catalog, reader, or wardrobe screen')
