#!/usr/bin/env python3
"""Check the max-refresh-rate switch is wired end to end, not just defined.

The behaviour of the chooser itself is tested by `tests/RefreshModeChoiceOfflineTest.kt` and the
request path by `tests/DisplayRefreshModeAndroidTest.kt` (both compiled by CI with kotlinc). What
this file guards is the part those cannot see: that a preference exists under the key the XML writes,
that both settings surfaces expose the same key, that the game window applies and re-applies the
request, that the outcome is read back and said out loud, and that nothing reaches for a mechanism
that needs a permission a launcher does not have.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root / 'app_pojavlauncher/src/main'
android = '{http://schemas.android.com/apk/res/android}'
tools = '{http://schemas.android.com/tools}'
KEY = 'requestMaxRefreshRate'

prefs = (app / 'java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java').read_text()
adapter = (app / 'java/net/kdt/pojavlaunch/utils/DisplayRefreshMode.kt').read_text()
choice = (app / 'java/net/kdt/pojavlaunch/utils/RefreshModeChoice.kt').read_text()
video_xml = (app / 'res/xml/pref_video.xml').read_text()
bridge = (app / 'java/net/kdt/pojavlaunch/aerix/bridge/PojavSettingsBridge.kt').read_text()
settings = (app / 'java/net/kdt/pojavlaunch/aerix/ui/screens/SettingsScreen.kt').read_text()
legacy = (app / 'java/net/kdt/pojavlaunch/prefs/screens/LauncherPreferenceVideoFragment.java').read_text()
game = (app / 'java/net/kdt/pojavlaunch/game/GameActivity.java').read_text()
fps = (app / 'java/net/kdt/pojavlaunch/game/FpsView.java').read_text()
strings = (app / 'res/values/strings.xml').read_text()
workflow = (root / '.github/workflows/aerix-debug.yml').read_text()

# The key is declared once, in the Kotlin object, and every other file points at it.
assert f'const val PREF_KEY = "{KEY}"' in adapter, 'the preference key must live in DisplayRefreshMode'
assert f'DEFAULT_PREF.getBoolean(DisplayRefreshMode.PREF_KEY, false)' in prefs, \
    'PREF_MAX_REFRESH_RATE must be loaded from the shared key, off by default'
assert re.search(r'public static boolean PREF_MAX_REFRESH_RATE = false;', prefs), \
    'the field must exist with an explicit false default for the first read'

# The classic preference screen and the two launcher settings surfaces all use that key.
video_prefs = ET.parse(app / 'res/xml/pref_video.xml').getroot()
keys = [e.get(android + 'key') for e in video_prefs.iter() if e.get(android + 'key')]
assert KEY in keys, f'{KEY} must be a preference in pref_video.xml, saw {keys}'
switch = [e for e in video_prefs.iter() if e.get(android + 'key') == KEY][0]
assert switch.tag.endswith('SwitchPreferenceCompat'), 'the max-refresh row must be a switch'
assert switch.get(android + 'defaultValue') == 'false', 'the switch must default off: it costs battery'
assert 'android:title="@string/aerix_max_refresh_title"' in video_xml, \
    'the row must be titled from resources like its neighbours'
assert 'android:summary="@string/aerix_max_refresh_desc"' in video_xml, \
    'the row must describe itself from resources'
assert f'PojavSetting.Toggle(DisplayRefreshMode.PREF_KEY, R.string.aerix_max_refresh_title' in bridge, \
    'the registry-driven settings screen must carry the same toggle'
assert f'PojavSettingsBridge.writeBoolean(activity, DisplayRefreshMode.PREF_KEY, isOn)' in settings, \
    'the Compose settings screen must write through the bridge so commit() still runs'
assert 'maxRefreshEnabled = isOn' in settings, \
    'the Compose row must hold its own state so the summary updates when the switch is flipped'

# Both settings surfaces report what the panel can do, rather than restating the description.
for name, text in (('Compose', settings), ('classic', legacy)):
    assert 'ceilingRefreshHz' in text, f'the {name} row must read the display ceiling'
    assert 'activeRefreshHz' in text, f'the {name} row must read the live rate'

# The game window is where the request is actually made, and it is made again after layout changes.
assert 'private void updateDisplayRefreshMode()' in game, 'the request must be applied from the game window'
assert 'updateDisplayRefreshMode();' in game, 'the request must be called, not just defined'
assert game.count('updateDisplayRefreshMode();') >= 2, \
    'the request must be re-applied after configuration changes as well as on first layout'
assert 'DisplayRefreshMode.applyTo(this)' in game and 'DisplayRefreshMode.clear(this)' in game, \
    'the game must both apply the request and hand the display back'
assert 'postDelayed' in game.split('private void updateDisplayRefreshMode()')[1][:2400], \
    'the outcome must be read back after the window settles, not assumed from the call'
assert 'R.string.aerix_max_refresh_refused' in game, \
    'a device that keeps the panel at 60Hz must say so instead of leaving the player guessing'
assert 'R.string.aerix_max_refresh_done' in game, \
    'a device that did switch must say that too, in the same breath'

# The in-game readout gains the panel rate, which is the number that settles whether it worked.
assert 'fps_overlay_value_hz' in fps and 'PREF_MAX_REFRESH_RATE' in fps, \
    'the FPS overlay must show the refresh rate while the switch is on'
for name in ('fps_overlay_value_hz', 'aerix_max_refresh_title', 'aerix_max_refresh_desc',
             'aerix_max_refresh_summary', 'aerix_max_refresh_unavailable',
             'aerix_max_refresh_refused', 'aerix_max_refresh_done'):
    assert f'name="{name}"' in strings, f'strings.xml must define {name}'
names = re.findall(r'<string name="([^"]+)"', strings)
dupes = sorted({n for n in names if names.count(n) > 1})
assert not dupes, f'strings.xml has duplicate names: {dupes}'

# The decision must stay where it can be tested, and the Android layer must stay thin.
assert 'import android.' not in choice, 'RefreshModeChoice must not know about Android'
assert 'fun choose(' in choice and 'fun ceilingHz(' in choice and 'needsModeSwitch' in choice
assert 'getSystemService' in adapter and 'supportedModes' in adapter
assert 'Build.VERSION.SDK_INT >= Build.VERSION_CODES.R' in adapter, \
    'Display.getMode/getPossibleRefreshRates need an API 30 guard'
assert 'Build.VERSION.SDK_INT >= Build.VERSION_CODES.S' in adapter, \
    'Window.setFrameRate needs an API 31 guard'
assert 'FRANE' not in adapter and 'FRAME_RATE_COMPATIBILITY_DEFAULT' in adapter
assert 'getPhysicalRefreshRate' in adapter, 'rounded refresh rates must not be used for comparison'

# No mechanism here needs a permission: only documented window levers, no settings/global writes.
for forbidden in ('Settings.System', 'Settings.Global', 'WRITE_SECURE_SETTINGS', 'su -c',
                   'performClick', 'debug.sf.', 'window_freeform'):
    assert forbidden not in adapter, f'{forbidden} is not available to an unprivileged launcher'
ids = [t for t in re.findall(r'preferredDisplayModeId', adapter)]
assert len(ids) >= 3, 'the mode id must be written, compared and cleared in one place only'
assert 'preferredDisplayModeId' not in game and 'preferredDisplayModeId' not in settings, \
    'the window attribute must have exactly one owner'

# CI must actually run the Kotlin checks; a test nobody invokes is documentation.
for referenced in ('tests/RefreshModeChoiceOfflineTest.kt', 'tests/DisplayRefreshModeAndroidTest.kt',
                   'tests/refresh_display_stubs', 'tests/max_refresh_rate_smoke.py'):
    assert referenced in workflow or referenced in str(Path(__file__).name), \
        f'{referenced} must be wired into aerix-debug.yml'
assert 'DisplayRefreshMode.kt' in workflow, 'CI must compile the adapter against the stubs'
assert (root / 'tests/refresh_display_stubs').is_dir() and \
    list((root / 'tests/refresh_display_stubs').glob('*.kt')), 'the android stubs must exist'

print('max refresh rate: preference, both settings surfaces, window request, read-back and CI wiring '
      'all point at ' + KEY)
