#!/usr/bin/env python3
"""Verify Horizon build staging and process/data isolation without game downloads."""
import importlib.util
import io
from pathlib import Path
import tempfile
import zipfile
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
app = root / 'app_pojavlauncher/src/main'
spec = importlib.util.spec_from_file_location('horizon_stage', root / 'tools/prepare_horizon.py')
mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
assert len(mod.CHECKSUMS) == 4 and all(len(s) == 64 for s in mod.CHECKSUMS.values())
with tempfile.TemporaryDirectory() as tmp:
    tmp = Path(tmp)
    for abi, (cls, arch) in {'arm64-v8a': (2, 183), 'armeabi-v7a': (1, 40), 'x86': (1, 3), 'x86_64': (2, 62)}.items():
        fake = bytearray(128); fake[:4] = b'\x7fELF'; fake[4:6] = bytes([cls, 1]); fake[18:20] = arch.to_bytes(2, 'little')
        zipfile_path = tmp / (abi + '.apk')
        with zipfile.ZipFile(zipfile_path, 'w') as z: z.writestr(f'lib/{abi}/libhorizon_android.so', fake)
        extracted = tmp / abi / 'lib.so'
        with zipfile.ZipFile(zipfile_path) as z: mod.copy_member(z, f'lib/{abi}/libhorizon_android.so', extracted)
        mod.check_elf(extracted, abi)
        try: mod.check_elf(extracted, 'x86_64' if abi != 'x86_64' else 'x86')
        except ValueError: pass
        else: raise AssertionError('Wrong ABI accepted')

# Refuse an upstream (launcher-only) binary, even when its ABI is correct.
with tempfile.TemporaryDirectory() as directory:
    path = Path(directory) / 'engine.so'
    fake = bytearray(128); fake[:4] = b'\x7fELF'; fake[4:6] = bytes([2, 1]); fake[18:20] = (183).to_bytes(2, 'little')
    path.write_bytes(fake)
    try: mod.check_direct_engine(path, 'arm64-v8a')
    except ValueError: pass
    else: raise AssertionError('Stock launcher binary must not be packaged')
    path.write_bytes(fake + b'aerixStatus')
    mod.check_direct_engine(path, 'arm64-v8a')
    try: mod.stage(Path(directory) / 'output')
    except FileNotFoundError as error: assert 'Missing patched Rust engine' in str(error)
    else: raise AssertionError('Missing direct game engine must fail closed')

patch = (root / 'horizon/patches/direct-game.patch').read_text()
assert 'self.action(events)' in patch and 'events.exit()' in patch
assert 'Do not create or draw the Horizon launcher window' in patch
assert 'aerix_status' in patch

android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(app / 'AndroidManifest.xml').getroot().find('application')
activity = next(x for x in manifest.findall('activity') if x.get(android+'name') == 'dev.horizon.android.HorizonActivity')
provider = next(x for x in manifest.findall('provider') if x.get(android+'name') == 'dev.horizon.android.HorizonDocumentsProvider')
assert activity.get(android+'process') == provider.get(android+'process') == ':horizon'
assert activity.get(android+'exported') == 'false'
assert provider.get(android+'permission') == 'android.permission.MANAGE_DOCUMENTS'
activity_source = (app / 'java/dev/horizon/android/HorizonActivity.java').read_text()
provider_source = (app / 'java/dev/horizon/android/HorizonDocumentsProvider.java').read_text()
assert 'getFilesDir()' in activity_source and '"horizon-rust"' in activity_source
assert '"horizon-rust"' in provider_source
assert 'nativeDocumentResult' in activity_source and 'nativeCall' in provider_source
assert 'public void aerixStatus(String message)' in activity_source
assert 'Cancel and return to Aerix' in activity_source and 'Return to Aerix and tap Play to retry' in activity_source
assert 'aerixOfflineName' in activity_source
assert 'Play Rust Edition' in (app / 'java/net/kdt/pojavlaunch/aerix/ui/screens/HorizonScreen.kt').read_text()
assert 'procName.endsWith(":horizon")' in (app / 'java/net/kdt/pojavlaunch/PojavApplication.java').read_text()
assert 'LauncherSection.HORIZON -> HorizonScreen(activity)' in (app / 'java/net/kdt/pojavlaunch/aerix/ui/AerixLauncherScreen.kt').read_text()
assert 'Intent(activity, HorizonActivity::class.java)' in (app / 'java/net/kdt/pojavlaunch/aerix/ui/screens/HorizonScreen.kt').read_text()
assert 'prepareHorizon' in (root / 'app_pojavlauncher/build.gradle').read_text()
print('Horizon artifact staging, isolated process and UI wiring smoke checks passed')
