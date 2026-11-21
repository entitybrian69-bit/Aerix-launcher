#!/usr/bin/env python3
"""Smoke checks ensuring device-specific stutter traps stay fixed."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'app_pojavlauncher/src/main'

prefs = (APP / 'java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java').read_text(encoding='utf-8')
video_xml = (APP / 'res/xml/pref_video.xml').read_text(encoding='utf-8')
java_runner = (APP / 'java/net/kdt/pojavlaunch/utils/jre/JavaRunner.java').read_text(encoding='utf-8')
affinity = (APP / 'jni/affinity.c').read_text(encoding='utf-8')
overlay = (APP / 'java/net/kdt/pojavlaunch/aerix/client/AerixClientOverlayView.kt').read_text(encoding='utf-8')
bridge = (APP / 'java/net/kdt/pojavlaunch/aerix/client/AerixClientInputBridge.kt').read_text(encoding='utf-8')

# 1. isDevicePowerful must not enable Sustained Performance Mode or Force VSync or turn off SurfaceView.
assert 'DEFAULT_PREF.getBoolean("sustainedPerformance", false)' in prefs, \
    'Sustained Performance Mode must default off so PowerHAL does not throttle CPU/GPU clocks'
assert 'DEFAULT_PREF.getBoolean("alternate_surface", true)' in prefs, \
    'SurfaceView must default on across all devices to avoid TextureView GPU/UI-thread stutter'
assert 'DEFAULT_PREF.getBoolean("force_vsync", false)' in prefs, \
    'Force VSync must default off to prevent 60->30/120->60 frame-pacing drops'
assert 'DEFAULT_PREF.getBoolean("sustainedPerformance", isDevicePowerful)' not in prefs
assert 'DEFAULT_PREF.getBoolean("alternate_surface", isDevicePowerful)' not in prefs
assert 'DEFAULT_PREF.getBoolean("force_vsync", isDevicePowerful)' not in prefs

alt_block = video_xml.split('android:key="alternate_surface"')[0].split('<androidx.preference.SwitchPreferenceCompat')[-1]
assert 'android:defaultValue="true"' in alt_block, 'pref_video.xml alternate_surface must default true'

# 2. JVM GC tuning is wired into JavaRunner and respects client VMs.
assert 'JvmGcTuning.appendDefaultGcArgs(userArguments, memoryMb, allowG1Gc)' in java_runner
assert '"client".equals(vmPath.getParentFile().getName())' in java_runner

# 3. CPU affinity pins to the big-core cluster rather than a single core ID.
assert 'aerix_select_big_cores' in affinity
assert 'CPU_SET_S(big_core_id' not in affinity

# 4. AerixClientOverlayView must keep log reading and Debug.getPss off the main thread.
assert 'withContext(Dispatchers.IO) { AerixClientInputBridge.readLatestLogChunk() }' in overlay
assert 'readLatestLogChunk()' in bridge
assert 'android.os.Debug.getPss()' in overlay
pss_section = overlay.split('android.os.Debug.getPss()')[0].splitlines()[-5:]
assert any('Dispatchers.IO' in line for line in pss_section), \
    'Debug.getPss() must run inside Dispatchers.IO'
fast_line = [l for l in overlay.splitlines() if 'val needsFastUpdates =' in l or 'AerixClientStore.cpsPillEnabled ||' in l]
assert 'AerixClientStore.perfPillEnabled' not in overlay.split('val needsFastUpdates =')[1].split('delay(')[0], \
    '1-Hz perf/fps overlays must not force 100ms main-thread wakeups'

print('stutter_fixes_smoke: all checks passed')
