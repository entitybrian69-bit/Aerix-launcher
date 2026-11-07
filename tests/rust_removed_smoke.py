#!/usr/bin/env python3
"""Ensure the Rust integration is removed without changing Java launch wiring."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root / 'app_pojavlauncher/src/main'
android = '{http://schemas.android.com/apk/res/android}'
application = ET.parse(app / 'AndroidManifest.xml').getroot().find('application')
assert application is not None
for element in application:
    assert 'dev.horizon.android' not in element.get(android + 'name', '')
assert not (app / 'java/dev/horizon/android').exists()
assert not (app / 'assets/third_party/horizon').exists()
assert not (root / 'horizon').exists()
assert not (root / 'tools/prepare_horizon.py').exists()
assert not (root / '.github/workflows/horizon-native-check.yml').exists()
for path in [root / 'app_pojavlauncher/build.gradle',
             app / 'java/net/kdt/pojavlaunch/PojavApplication.java',
             *list((root / '.github/workflows').glob('*.yml'))]:
    text = path.read_text()
    for removed in ('prepareHorizon', 'AERIX_HORIZON_LIB_DIR', 'horizon-native-check', ':horizon'):
        assert removed not in text, (path, removed)
shell = (app / 'java/net/kdt/pojavlaunch/aerix/ui/AerixLauncherScreen.kt').read_text()
rail = (app / 'java/net/kdt/pojavlaunch/aerix/ui/AerixNavigationRail.kt').read_text()
home = (app / 'java/net/kdt/pojavlaunch/aerix/ui/screens/HomeScreen.kt').read_text()
assert 'HorizonScreen' not in shell and 'HORIZON' not in rail
assert 'AerixNavigationRail(' in shell and 'HomeScreen(activity, navigate)' in shell
assert 'PojavLaunchBridge.play(activity)' in home
assert 'CompactSkinStageCard' not in home
print('Rust integration removed; original navigation and Java play remain wired')
