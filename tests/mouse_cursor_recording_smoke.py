#!/usr/bin/env python3
"""Static checks for the second complaint batch: cursor skins, the mouse sensitivity split,
recordings that must never look like an archive, the FPS readout that must not sit on its
placeholder, and the dashboard skin switch.

Run from the repository root. These are source and asset invariants, chosen because they are the
things that silently regress: a setting that writes the wrong preference key, a preset folder that
ships without its manifest, a readout whose polling is gated on a condition the window may never
satisfy again.
"""

import json
import os
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app_pojavlauncher", "src", "main")
JAVA = os.path.join(MAIN, "java", "net", "kdt", "pojavlaunch")
CURSORS = os.path.join(MAIN, "assets", "cursors")
failures = []


def read(*parts):
    with open(os.path.join(*parts), encoding="utf-8") as handle:
        return handle.read()


def check(name, condition, detail=""):
    if condition:
        print("  ok   " + name)
    else:
        print("  FAIL " + name + ((" :: " + detail) if detail else ""))
        failures.append(name)


def png_size(path):
    """(width, height, color type) from the IHDR chunk - no image library needed."""
    with open(path, "rb") as handle:
        head = handle.read(33)
    if head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        return None
    width, height = struct.unpack(">II", head[16:24])
    return width, height, head[25]


print("1. cursor presets ship inside the APK")
check("cursor asset folder exists", os.path.isdir(CURSORS), CURSORS)
manifest_path = os.path.join(CURSORS, "manifest.json")
entries = []
if os.path.isfile(manifest_path):
    try:
        entries = json.load(open(manifest_path, encoding="utf-8"))
    except Exception as error:  # noqa: BLE001 - the manifest is the picker's data source
        check("manifest.json parses", False, str(error))
check("manifest lists presets", len(entries) >= 8, "found %d" % len(entries))
check("every entry has file and name",
      all(isinstance(e, dict) and e.get("file") and e.get("name") and e.get("id") for e in entries),
      str(entries[:2]))
for entry in entries:
    path = os.path.join(CURSORS, entry["file"])
    info = png_size(path) if os.path.isfile(path) else None
    check("preset %s is a decodable RGBA PNG" % entry["file"],
          info is not None and info[2] == 6 and info[0] == info[1] and info[0] <= 256,
          str(info))
check("no raw upload left in the asset folder",
      not [f for f in os.listdir(CURSORS) if f.startswith("images") or f.endswith(".jpeg")],
      str(os.listdir(CURSORS)))

print("2. the skin is applied where the cursor is drawn")
view = read(os.path.join(JAVA, "game", "GameCursorView.java"))
check("GameCursorView can take a bitmap skin", "setCursorSkin" in view and "aerixSkin" in view)
check("the skin honours the chosen opacity", "aerixSkinPaint.setAlpha" in view)
check("the skin honours the chosen anchor", "aerixSkinCentered" in view)
check("the built-in arrow still draws when no skin is set", "cursorDrawable.draw(canvas)" in view)
activity = read(os.path.join(JAVA, "game", "GameActivity.java"))
check("the launcher applies the skin and reports it",
      "AerixCursorSkin.INSTANCE.currentBitmap" in activity and "Aerix cursor:" in activity)
skin = read(os.path.join(JAVA, "aerix", "client"), "AerixCursorSkin.kt")
check("the skin persists its choice", 'putString(KEY_SKIN' in skin and "fun select" in skin)
check("the gallery import explains its outcome", "could not be decoded" in skin)
check("opacity is clamped, not trusted", "coerceIn(20, 100)" in skin)

print("3. mouse sensitivity: two settings, two effects")
prefs = read(os.path.join(JAVA, "prefs", "LauncherPreferences.java"))
check("a preference of its own for in-game aim", "PREF_GAME_MOUSE_SENSITIVITY" in prefs)
check("the old key is carried over, not reset", 'getInt("gameMouseSensitivity"' in prefs)
check("mouse percent values are clamped", "clampPercent" in prefs)
capture = read(os.path.join(JAVA, "customcontrols", "mouse"), "AndroidPointerCapture.java")
check("the aim path uses the game sensitivity", "PREF_GAME_MOUSE_SENSITIVITY" in capture)
check("the launcher cursor path keeps its own speed",
      "applyMotionVector(view, LauncherPreferences.PREF_MOUSESPEED)" in capture)
touch = read(os.path.join(JAVA, "game"), "TouchEventProcessor.java")
check("touch look follows the same split", "PREF_GAME_MOUSE_SENSITIVITY" in touch)
quick = read(os.path.join(JAVA, "prefs"), "QuickSettingSideDialog.java")
check("the in-game slider writes the in-game key", 'putInt("gameMouseSensitivity"' in quick)
check("the in-game slider no longer writes the cursor key", 'putInt("mousespeed"' not in quick)
check("cancelling the in-game slider restores the in-game value",
      "PREF_GAME_MOUSE_SENSITIVITY = mOriginalMouseSpeed" in quick)

print("4. recordings are MP4, never a mystery archive")
output = read(os.path.join(JAVA, "recording"), "RecordingOutput.kt")
check("stray recordings are re-filed", "fun recoverStrays" in output)
check("a recovered file is never left as .partial", 'removeSuffix(".partial")' in output)
check("recovery is honest about an unplayable salvage", "-unfinished.mp4" in output)
controller = read(os.path.join(JAVA, "recording"), "RecorderController.kt")
check("opening the recordings list runs recovery", "RecordingOutput.recoverStrays" in controller)
check("the list still offers only real videos", 'extension.equals("mp4", true)' in controller)

print("5. the FPS readout must leave its placeholder")
fps = read(os.path.join(JAVA, "game"), "FpsView.java")
check("polling no longer waits for window focus",
      "getWindowVisibility() == VISIBLE && hasWindowFocus()" not in fps)
check("a stall is reported instead of silence", "fps_overlay_stalled" in fps)
check("a missing source names its remedy", "fps_overlay_unavailable_reason" in fps)
check("the game's own counter is the second source", "ClientFpsProbe.read()" in fps)
probe = read(os.path.join(JAVA, "aerix", "client"), "ClientFpsProbe.kt")
check("the probe reads the running instance only", "activeGameDir" in probe)
strings = read(os.path.join(MAIN, "res", "values", "strings.xml"))
check("the waiting text is still a waiting text", ">-- FPS<" in strings)
check("the reason strings exist", "fps_overlay_stalled" in strings and "fps_overlay_unavailable_reason" in strings)

print("6. memory the device can actually honour")
memory = read(os.path.join(JAVA, "utils", "jre"), "InstanceMemory.java")
check("a practical heap ceiling exists", "safeMb" in memory)
check("the ceiling keeps room for the renderer", "systemReserve" in memory)
memory_pkg = read(os.path.join(JAVA, "utils", "jre"), "LaunchMemory.java")
check("a stored over-allocation heals at launch", "clamp" in memory_pkg and "safeMaxMb" in memory_pkg)
check("the policy itself stays free of Android types, so it can be compiled alone",
      "import android" not in memory and "android.content" not in memory and "android.os" not in memory,
      "InstanceMemory is javac-compiled on its own by tests/InstanceMemorySmokeTest.java")
runner = read(os.path.join(JAVA, "utils", "jre"), "GameRunner.java")
check("the launch clamps and says so", "LaunchMemory.clamp" in runner and "autoram_capped_msg" in runner)
check("the toast reports the device total", "deviceRamMb" in runner)
bridge = read(os.path.join(JAVA, "aerix", "bridge"), "PojavSettingsBridge.kt")
check("the settings slider is capped to the ceiling", "max = { safeMaxRam(it) }" in bridge)
settings = read(os.path.join(JAVA, "aerix", "ui", "screens"), "SettingsScreen.kt")
check("the Compose slider is capped too", "safeMaxRam(activity)" in settings)
java_fragment = read(os.path.join(JAVA, "prefs", "screens"), "LauncherPreferenceJavaFragment.java")
check("Pojav's own page shares the ceiling", "InstanceMemory.safeMb" in java_fragment)
check("the capped message takes the device total", "%1$d MB of %2$d MB device RAM" in strings)

print("7. Discord says what the player is doing")
presence = read(os.path.join(JAVA, "aerix", "discord"), "AerixDiscordPresence.kt")
check("the activity line reaches the card", "fun launcherActivity" in presence)
check("the card falls back to the page name", "launcherActivityDetail ?: InLauncher.stateFor(section)" in presence)
check("leaving for the game clears the line", "launcherActivityDetail = null" in presence)
backend = read(os.path.join(JAVA, "aerix", "social"), "SocialBackend.kt")
check("reporting an activity hands it to Discord before sign-in",
      backend.index("AerixDiscordPresence.launcherActivity") < backend.index("if (!signedIn) return"))
check("clearing only removes this screen's line", "fun clearLauncherActivity" in backend)
discover = read(os.path.join(JAVA, "aerix", "ui", "screens"), "DiscoverScreen.kt")
check("the pack search is announced", "Hunting for" in discover)
editor = read(os.path.join(JAVA, "aerix", "ui", "screens"), "InstanceEditorScreen.kt")
check("editing an instance is announced", 'aerixLauncherActivity("Editing' in editor)

print("8. the launcher fills the display")
launcher = read(os.path.join(JAVA, "LauncherActivity.java"))
rail = launcher[launcher.index("setOnApplyWindowInsetsListener"):launcher.index("setContentView(R.layout.activity_pojav_launcher")]
check("the cutout is not reserved as a black rail",
      "AerixWindowInsets.INSTANCE.update(bars.left, top, bars.right, bottom)" in rail)
check("the bar is horizontal-only and never the cutout's depth",
      "Type.displayCutout()" in rail and "bars.left, top, bars.right, bottom" in rail)
# androidx.core.graphics.Insets publishes fields; getLeft() et al. do not exist there and the
# Java compile fails on them, so the getter form has to stay out of this block.
check("the insets are read the way this androidx version exposes them",
      ".getLeft()" not in rail and ".getTop()" not in rail and ".getBottom()" not in rail)
check("the reserve never double-counts the system bars",
      "int mask = WindowInsetsCompat.Type.displayCutout();" not in launcher)

print("9. the dashboard skin switches without a restart")
aerix_prefs = read(os.path.join(JAVA, "aerix", "bridge"), "AerixPreferences.kt")
check("two skins, named as the players named them",
      'PREMIUM("Premium UI")' in aerix_prefs and 'CLEAN("Clean UI")' in aerix_prefs)
check("the choice is persisted", 'putString(KEY_UI_STYLE, value.name)' in aerix_prefs)
check("the choice is Compose state, so no restart is needed",
      "var uiStyle by mutableStateOf" in aerix_prefs)
home = read(os.path.join(JAVA, "aerix", "ui", "screens"), "HomeScreen.kt")
check("the switch sits at the top of the dashboard", "AerixUiStyleSwitch" in home)
check("the two layouts animate into position",
      "animateDpAsState" in home and "animateFloatAsState" in home)
check("the pills move with a sliding highlight", 'label = "uiStylePill"' in home)

print("10. mouse settings live where they were asked for")
section = read(os.path.join(JAVA, "aerix", "ui", "screens"), "AerixMouseSettingsSection.kt")
check("the section offers the presets and the gallery", "presets(context)" in section and "OpenDocument" in section)
check("the preview uses the real size and opacity", "AerixCursorSkin.fitIn" in section and "graphicsLayer" in section)
check("size and opacity are both sliders", section.count("MiraiIntSliderSettingsCard(") >= 2)
check("the two sensitivities are labelled apart",
      "Look sensitivity (in game)" in section and "Launcher cursor speed" in section)
check("every import the file needs is declared",
      "import androidx.compose.foundation.combinedClickable" in section)
settings_screen = read(os.path.join(JAVA, "aerix", "ui", "screens"), "SettingsScreen.kt")
check("the settings page mounts the section", "AerixMouseSettingsSection()" in settings_screen)
overlay = read(os.path.join(JAVA, "aerix", "client"), "AerixClientOverlayView.kt")
# LocalContext.current is a composable getter, so the page hoists it once and the click handlers
# use the hoisted value - asserting on the hoist is what keeps that from being "re-fixed" backwards.
check("the in-game overlay gets cursor size", "AerixCursorSkin.setScale(pageContext" in overlay)
check("the page hoists its context out of the callbacks", "val pageContext = LocalContext.current" in overlay)
check("the in-game overlay switches the FPS pill", "fpsOverlayEnabled" in overlay)
game_prefs = read(os.path.join(JAVA, "aerix", "client"), "AerixClientStore.kt")
check("that switch is persisted", 'putBoolean("fpsOverlayEnabled"' in game_prefs)
check("and it drives the readout in client instances",
      "getFpsOverlayEnabled()" in activity)

print("11. the 60 Hz ceiling is lifted, and said out loud")
check("the unlock names what it measured", "Panel serving" in overlay)
check("it writes both launcher caps", 'putBoolean("force_vsync", false)' in overlay)
check("it releases the game's own limit", 'ClientPerformanceSettings.set("enableVsync", "false")' in overlay)
check("it applies to the live window", "DisplayRefreshMode.applyTo" in overlay)
check("a fast panel asks for its rate by default", "DisplayRefreshMode.isAvailable(ctx)" in prefs)

print("12. typed text is not dropped by a full queue")
backend_glfw = read(os.path.join(JAVA, "game", "platform", "backend"), "GLFWBackend.java")
check("long text is sent in paced bursts", "TEXT_CHUNK_CODEPOINTS" in backend_glfw)
check("bursts are spaced about a frame apart", "TEXT_CHUNK_INTERVAL_MS" in backend_glfw)
check("the first characters still go immediately", "if (burst == 0) GLFW.sendBulkUnicodeEvent" in backend_glfw)
check("chunking is by code point, not char", "offsetByCodePoints" in backend_glfw)

print("13. modpacks can be installed with or without the client")
client_screen = read(os.path.join(JAVA, "aerix", "ui", "screens"), "AerixClientScreen.kt")
check("the client tab can search for packs", "AssetType.MODPACK" in client_screen)
check("install asks with three answers",
      'Text("Install with client")' in client_screen and 'Text("Install without client")' in client_screen
      and 'Text("Cancel")' in client_screen)
check("a pack keeps its own mods", "updateOwnMods" in client_screen)
check("the new instance is found by diffing, not guessing", "instance.name !in before" in client_screen)
check("the pack install goes through the launcher's own path",
      "PojavAssetBridge.install(context, project, file)" in client_screen)

print()
if failures:
    print("FAILED %d check(s):" % len(failures))
    for name in failures:
        print("  - " + name)
    sys.exit(1)
print("all checks passed")
