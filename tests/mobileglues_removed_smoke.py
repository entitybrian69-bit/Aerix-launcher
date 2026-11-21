#!/usr/bin/env python3
"""Ensure MobileGlues is completely removed while SFPEW and retired-tag cleanup remain intact."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root / 'app_pojavlauncher/src/main'
android = '{http://schemas.android.com/apk/res/android}'

assert not (root / 'app_pojavlauncher/libs/MobileGlues-release.aar').exists()
assert not (app / 'java/net/kdt/pojavlaunch/mobileglues').exists()
assert not (app / 'aidl/net/kdt/pojavlaunch/mobileglues').exists()
assert not (app / 'jni/mobileglues').exists()
assert not (app / 'java/net/kdt/pojavlaunch/game/renderer/MobileGluesWarning.java').exists()
assert not (app / 'java/net/kdt/pojavlaunch/aerix/ui/screens/MobileGluesWarningDialog.kt').exists()
assert not (app / 'res/values/mobileglues_strings.xml').exists()
assert not (app / 'res/drawable/ic_mg_renderer.png').exists()
assert not list((app / 'res/drawable').glob('avatar_*.webp'))

manifest = ET.parse(app / 'AndroidManifest.xml').getroot()
application = manifest.find('application')
assert application is not None
for element in application:
    name = element.get(android + 'name', '')
    process = element.get(android + 'process', '')
    assert 'mobileglues' not in name.lower(), name
    assert ':mgquery' not in process, process

files_to_check = [
    app / 'java/net/kdt/pojavlaunch/PojavApplication.java',
    app / 'java/net/kdt/pojavlaunch/game/renderer/def/Renderers.java',
    app / 'java/net/kdt/pojavlaunch/game/renderer/GameRenderer.java',
    app / 'java/net/kdt/pojavlaunch/game/renderer/RendererCache.java',
    app / 'java/net/kdt/pojavlaunch/game/renderer/RendererDefaults.java',
    app / 'java/net/kdt/pojavlaunch/game/renderer/impl/GLESRenderSpec.java',
    app / 'java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java',
    app / 'java/net/kdt/pojavlaunch/prefs/screens/LauncherPreferenceVideoFragment.java',
    app / 'java/net/kdt/pojavlaunch/fragments/InstanceEditorFragment.java',
    app / 'java/net/kdt/pojavlaunch/aerix/bridge/PojavSettingsBridge.kt',
    app / 'java/net/kdt/pojavlaunch/aerix/ui/screens/SettingsScreen.kt',
    app / 'java/net/kdt/pojavlaunch/aerix/ui/screens/InstanceEditorScreen.kt',
    app / 'jni/CMakeLists.txt',
    app / 'res/xml/pref_video.xml',
    app / 'res/values/strings.xml',
    app / 'res/values/aerix_strings.xml',
]
forbidden = (
    'MOBILEGLUES_RENDERER',
    'MgRenderSpec',
    'MGApplication',
    'MobileGluesWarning',
    'MobileGluesActivity',
    'MgQueryService',
    'mobileglues_info_getter',
    'libmobileglues.so',
    'MG_DIR_PATH',
    'renderer_settings',
    ':mgquery',
)
for path in files_to_check:
    text = path.read_text()
    for token in forbidden:
        assert token not in text, (path, token)

cleanup = (app / 'java/net/kdt/pojavlaunch/aerix/bridge/RetiredRendererCleanup.java').read_text()
assert '"opengles_mobileglues"' in cleanup and '"mobileglues"' in cleanup

gles = (app / 'java/net/kdt/pojavlaunch/game/renderer/impl/GLESRenderSpec.java').read_text()
assert 'SFPEWRenderSpec' in gles
assert 'libltw.so' in gles

print('MobileGlues completely removed; SFPEW and retired-renderer cleanup verified')
