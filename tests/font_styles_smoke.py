#!/usr/bin/env python3
"""Check the 35 bundled style choices, license records and control-editor button layout."""
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root / 'app_pojavlauncher/src/main'
java = app / 'java/net/kdt/pojavlaunch'
manager = (java / 'aerix/theme/AerixFonts.kt').read_text()
choices = re.findall(r'AerixFontChoice\("([^"]+)",\s*"([^"]+)"(?:,\s*R\.font\.([a-z0-9_]+))?\)', manager)
assert len(choices) == 35 and len({item[0] for item in choices}) == 35
assert {'default','minecraft','pressstart2p','silkscreen','vt323'} <= {item[0] for item in choices}
assert 'SharedPreferences' in manager and 'getString(KEY' in manager and 'putString(KEY, choice.id)' in manager
for _, _, res in choices:
    if res:
        path = app / 'res/font' / (res + '.ttf')
        assert path.is_file() and path.stat().st_size > 1000, res
        assert path.read_bytes()[:4] in (b'\x00\x01\x00\x00', b'OTTO'), res
base = app / 'assets/third_party/fonts'
manifest = json.loads((base / 'MANIFEST.json').read_text())
assert len(manifest['fonts']) == 33
assert {item['id'] for item in manifest['fonts']} == {key for key, _, _ in choices} - {'default','minecraft'}
for font in manifest['fonts']:
    data = (app / 'res/font' / (font['resource'] + '.ttf')).read_bytes()
    assert hashlib.sha256(data).hexdigest() == font['sha256']
    assert b'O' in (base / font['license']).read_bytes() and (base / font['license']).stat().st_size > 1000
ui = (java / 'aerix/ui/screens/SettingsScreen.kt').read_text()
theme = (java / 'aerix/theme/AerixTheme.kt').read_text()
assert 'AerixNav.TAB_STYLES' in ui and 'FontStylesTab(activity)' in ui
assert 'fontFamily = previewFamily' in ui and 'AerixFonts.select(activity, choice)' in ui
assert 'aerixTypography(AerixFonts.selected().family())' in theme
assert 'fontFamily = family' in theme
styles = ET.parse(app / 'res/values/aerix_font_styles.xml').getroot()
assert {s.get('name') for s in styles.findall('style')} == {'AerixFont_' + key for key, _, _ in choices}
assert 'AerixFonts.applyNativeTheme(this)' in (java / 'BaseActivity.java').read_text()
assert 'activity.theme.applyStyle(style, true)' in manager
ns = '{http://schemas.android.com/apk/res/android}'
editor = ET.parse(app / 'res/layout/activity_custom_controls.xml').getroot()
frame = editor.find('FrameLayout')
assert frame is not None and frame.get(ns+'layout_width') == frame.get(ns+'layout_height') == 'match_parent'
button = frame.find('net.kdt.pojavlaunch.customcontrols.handleview.DrawerPullButton')
assert button is not None and button.get(ns+'layout_gravity') == 'top|start'
assert button.get(ns+'layout_width') == button.get(ns+'layout_height') == 'wrap_content'
assert list(frame).index(button) > next(i for i, v in enumerate(frame) if v.tag.endswith('ControlLayout'))
activity = (java / 'CustomControlsActivity.java').read_text()
assert 'mPullDrawerButton.setOnClickListener' in activity
print('35 font styles, persistence, previews and floating editor button: smoke checks passed')
