#!/usr/bin/env python3
"""Guard controller startup, focus scrolling, and the entity-heavy PvP profile."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

map_store = (main / "customcontrols/gamepad/GamepadMapStore.java").read_text()
gamepad = (main / "customcontrols/gamepad/Gamepad.java").read_text()
dpad = (main / "customcontrols/gamepad/GamepadDpad.java").read_text()
joystick = (main / "customcontrols/gamepad/GamepadJoystick.java").read_text()
platform = (main / "game/platform/Platform.java").read_text()
focus_helper = (main / "aerix/components/BringFocusedItemIntoView.kt").read_text()
settings_components = (main / "aerix/components/MiraiSettingsComponents.kt").read_text()
nav = (main / "aerix/ui/AerixNavigationRail.kt").read_text()
game_menu = (main / "aerix/ui/game/GameMenuScreen.kt").read_text()
client_components = (main / "aerix/client/LiquidCrystalComponents.kt").read_text()
client_overlay = (main / "aerix/client/AerixClientOverlayView.kt").read_text()
performance = (main / "aerix/client/ClientPerformanceSettings.kt").read_text()

# Do not reload the JSON mapping three times on the first controller input; prime it during game setup.
assert "public static synchronized void ensureLoaded()" in map_store
assert "if (sMapStore == null) load();" in map_store
assert "loadIfNecessary" not in map_store
assert "loadGamepadMaps(false)" in gamepad and "GamepadMapStore.ensureLoaded()" in gamepad
assert platform.index("GamepadMapStore.ensureLoaded()") < platform.index("createRemapperManager(view)")
assert "SOURCE_DPAD" in dpad and "device.getSources()" in dpad
assert "event.getActionMasked() != MotionEvent.ACTION_MOVE" in joystick
assert "InputDevice.SOURCE_JOYSTICK" in joystick and "InputDevice.SOURCE_GAMEPAD" in joystick

# Keep focus-visible controls inside scrollable menus and bound expanded option lists on compact screens.
assert "BringIntoViewRequester" in focus_helper and "requester.bringIntoView()" in focus_helper
assert "itemsIndexed(items)" in settings_components and "heightIn(max = 280.dp)" in settings_components
assert ".bringFocusedItemIntoView()" in settings_components
assert ".bringFocusedItemIntoView()" in nav and ".bringFocusedItemIntoView()" in game_menu
assert ".bringFocusedItemIntoView()" in client_components and ".bringFocusedItemIntoView()" in client_overlay

pvp_profile = performance.split('"PvP" -> mapOf(', 1)[1].split("\n", 1)[0]
assert '"renderDistance" to "6"' in pvp_profile
assert '"entityDistanceScaling" to "0.5"' in pvp_profile

print("Controller startup, focus scrolling, and PvP performance smoke checks passed")
