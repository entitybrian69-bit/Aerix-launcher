#!/usr/bin/env python3
"""Ensure a built APK carries the Trinkies pipeline and none of the removed Rust engine."""
import io
import json
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

# The launcher is the only thing that bakes cosmetics onto the served skin, and the packaged Fabric
# JAR is the only thing that reads the wardrobe back. Both live inside the APK, so check them here
# instead of trusting that a Gradle copy task still points where it used to: the two defects this
# branch actually hit were a catalog row missing its cape path, and a class that never got packaged.
with zipfile.ZipFile(Path(sys.argv[1])) as apk:
    names = apk.namelist()
    jars = [name for name in names if name.endswith('.jar') and 'components/aerix_client/' in name]
    assert jars, 'APK carries no Aerix client mod JAR under components/aerix_client'
    payload = None
    for name in jars:
        with zipfile.ZipFile(io.BytesIO(apk.read(name))) as mod:
            entries = set(mod.namelist())
            if 'assets/trinkies/catalog.json' not in entries:
                continue
            payload = (name, mod.read('assets/trinkies/catalog.json'), entries)
            break
    assert payload, f'no packaged JAR carries the Trinkies catalog (tried {jars})'
    jar_name, catalog_bytes, entries = payload
    for required in (
        'aerix/client/mod/TrinkiesLoadout.class',
        'aerix/client/mod/AerixClientMod.class',
        'assets/trinkies/manifest.json',
    ):
        assert required in entries, f'{jar_name} is missing {required}'
    catalog = json.loads(catalog_bytes)
    assert catalog.get('item_count') == 300, f'{jar_name} ships {catalog.get("item_count")} catalog items'
    back = [item for item in catalog.get('items', []) if item.get('category') == 'back']
    assert back and all(item.get('cape_texture') for item in back), (
        'back items in the packaged catalog carry no cape patch, so the launcher would serve an '
        'unbaked cape'
    )
    # The launcher reads the pack from its own assets, not out of the JAR, so the two copies have to
    # agree or a player can equip something the in-game catalog does not know about.
    launcher_catalog = [name for name in names if name.endswith('assets/trinkies/catalog.json')]
    assert launcher_catalog, "the launcher's own asset copy of the Trinkies catalog is not packaged"
    assert json.loads(apk.read(launcher_catalog[0])) == json.loads(catalog_bytes.decode('utf-8')), (
        'the launcher and the mod ship different Trinkies catalogs'
    )
print(f'APK carries the Trinkies pipeline: {jar_name} with 300 catalog items and the wardrobe reader')
