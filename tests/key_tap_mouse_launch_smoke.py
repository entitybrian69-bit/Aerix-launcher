#!/usr/bin/env python3
"""The three things this change is for, checked where they can be checked without a device.

1. **Tap a key.** A ghost/hack client opens on a key binding, and a touch layout has no button for
   most of them. The panel must send a real held press, because a single-frame tap is missed by a
   client that polls `wasPressed()` once per tick.
2. **A physical mouse.** Buttons have to arrive as the numbering the game expects, and the launcher
   must stop drawing its own arrow over the mouse's.
3. **A launch that dies must leave a record.** "Kicked back to the home screen" with no log is the
   report that cannot be acted on, so the breadcrumb keeps the run-up and the exception.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

panel = (JAVA / "aerix/client/AerixKeyTapPanel.kt").read_text()
window = (JAVA / "aerix/client/AerixKeyTapWindow.kt").read_text()
activity = (JAVA / "game/GameActivity.java").read_text()
menu = (JAVA / "aerix/ui/game/GameMenuScreen.kt").read_text()
menu_view = (JAVA / "aerix/ui/game/GameMenuOverlayView.kt").read_text()
game_view = (JAVA / "game/GameView.java").read_text()
cursor_view = (JAVA / "game/GameCursorView.java").read_text()
diagnostics = (JAVA / "lifecycle/LaunchDiagnostics.java").read_text()
app = (JAVA / "PojavApplication.java").read_text()
log_screen = (JAVA / "aerix/ui/screens/LogScreen.kt").read_text()
mouse_screen = (JAVA / "aerix/ui/screens/AerixMouseSettingsSection.kt").read_text()
keycodes = (JAVA / "utils/KeycodeUtils.java").read_text()

problems = []


def check(condition, message):
    if not condition:
        problems.append(message)


# ── 1. Tap a key ───────────────────────────────────────────────────────────────────────────────
check("fun AerixKeyTapPanel(" in panel, "the panel must exist as a composable")
check("KeycodeUtils.generateKeyName()" in panel and "KeycodeUtils.getValueByIndex" in panel,
      "the key list must come from the launcher's own table, not a second hand-written one")
check("KeyEvent.KEYCODE_SHIFT_RIGHT" in panel,
      "Right Shift is the button a touch layout has no key for, so it must be offered")
check("suggested" in panel, "the likely keys must be offered first, not buried in the full grid")
check("onDismiss" in panel, "the player must be able to close it")

# Java cannot call a @Composable, so the window has to be Kotlin.
check("AerixLauncherTheme(" not in activity,
      "a @Composable cannot be called from Java; the window must be hosted in Kotlin")
check("AerixKeyTapWindow.show(" in activity,
      "the activity must ask the Kotlin host to show the panel")
check("fun AerixKeyTapPanel(" in window or "AerixKeyTapPanel(" in window,
      "the Kotlin host is what composes the panel")
check("fun interface AerixKeyTapCallback" in window,
      "Java cannot pass a method reference to a Kotlin function type: the lambda returns Unit, "
      "so void does not match. A named interface is what the activity can implement.")
check("setViewTreeLifecycleOwner" in window and "findViewTreeLifecycleOwner" in window,
      "a ComposeView in a dialog window has no owners and would silently never compose")
check("findViewTreeOnBackPressedDispatcherOwner" in window,
      "the back-dispatcher finder must be imported as well as the setter; the overlay already hit this")
check("DISMISS_GRACE_MS" in window,
      "a device that reports the opening tap twice would close the panel instantly; the pause menu "
      "already guards this, and the panel must too")
check("DisposeOnDetachedFromWindowOrReleasedFromPool" in window,
      "closing the panel must dispose the composition rather than leak a recomposer")

# A held press, not a one-frame tap.
held = activity[activity.index("public void sendHeldKeyPress(int keyCode)"):]
held = held[:held.index("\n    }")]
check("postDelayed" in held,
      "the key must stay down for a few frames; sendKeyPress down+up in one call is missed by "
      "clients that poll once per tick")
check("PLATFORM.sendKeyEvent(keyCode, true" in held and "PLATFORM.sendKeyEvent(keyCode, false" in held,
      "both halves of the press must go through the same platform call a control button uses")
check("setModifiers(keyCode, true)" in held,
      "a shift press must also set the modifier, or a client reading the modifier sees nothing")

# Reachable from the pause menu.
check("fun onOpenKeyTap()" in menu, "the pause menu must expose the panel")
check("override fun onOpenKeyTap()" in menu_view,
      "the placeholder callbacks must satisfy the interface or the menu cannot compile")
row_at = menu.find("aerix_ingame_menu_tap_key")
check(row_at >= 0, "the row must use a string resource")
check("onOpenKeyTap()" in menu[menu.find("aerix_ingame_menu_tap_key") - 400:
                              menu.find("aerix_ingame_menu_tap_key") + 400],
      "the row must call the callback, not just exist")

# ── 2. The physical mouse ─────────────────────────────────────────────────────────────────────
# Android's BUTTON_PRIMARY is 1; the game's backend takes GLFW numbering where left is 0. Passing
# the Android constant straight through made every left click arrive as the right button.
check("private static int glfwButton(int androidButton)" in game_view,
      "Android button constants must be translated to the game's numbering")
mapping = game_view[game_view.index("private static int glfwButton"):]
mapping = mapping[:mapping.index("\n    }")]
check("case MotionEvent.BUTTON_PRIMARY: return 0" in mapping,
      "the left button must become 0, which is what the game reads as the left button")
check("case MotionEvent.BUTTON_SECONDARY: return 1" in mapping,
      "the right button must become 1")
check("sendMouseButton(glfwButton(event.getActionButton())" in game_view,
      "the translated value is what has to be sent")
check("sendMouseButton(event.getActionButton()" not in game_view,
      "the raw Android constant must not be sent anywhere")

# The launcher must stop drawing its own arrow once a real mouse is driving the pointer.
check("private boolean hardwarePointer" in cursor_view and "private boolean pointerOwnedByGame" in cursor_view,
      "the cursor view must know whether a hardware pointer is in charge")
draw = cursor_view[cursor_view.index("protected void onDraw"):]
draw = draw[:draw.index("\n    }")]
check("if (hardwarePointer && pointerOwnedByGame) return;" in draw,
      "with a mouse present the view must draw nothing: Android already moves the real pointer, "
      "and a second arrow left at a stale position is the bug being fixed")
check("public void setHardwarePointer(boolean present)" in cursor_view,
      "GameView has to be able to tell the cursor view")
check("noteHardwarePointer(true)" in game_view,
      "the hover and touch paths are where a mouse announces itself")
check("setHardwarePointer(present)" in game_view, "and that has to reach the view")
check("isTouchExplorationEnabled()" in game_view,
      "a cursor can only stay wrong in two ways, and only one of them is code: the log has to say "
      "which, because Android draws no system pointer at all when touch exploration is off")
check("AppendToLog" in (JAVA / "game/GameView.java").read_text().replace("appendToLog", "AppendToLog"),
      "the probe has to reach the game log, or it is not a probe")

# The settings section must not claim the cursor skin styles a real mouse.
check("Virtual mouse on start" in mouse_screen,
      "the mouse settings must cover the virtual mouse, which is what fights a real one")
check("real mouse draws Android's own pointer" in mouse_screen,
      "the section must state plainly which pointer the skin applies to")
check(mouse_screen.count('title = "Look sensitivity (in game)"') == 1,
      "exactly one in-game sensitivity row: a duplicate would be two sliders writing one value")

# ── 3. A launch that dies leaves a record ────────────────────────────────────────────────────
check("public static void fail(Context context, String stage, Throwable error)" in diagnostics,
      "the breadcrumb needs an entry that carries the reason and the exception")
check("printStackTrace" in diagnostics, "the stack trace is the part that identifies the cause")
check("History (oldest first)" in diagnostics,
      "the run-up to the failure is what says where it died, not just where it ended")
check("MAX_ENTRIES" in diagnostics, "a breadcrumb must stay small")
check("synchronized" in diagnostics,
      "two processes can write this at once; a torn file is worse than no file")
check("LaunchDiagnostics.fail(" in app and "uncaught exception in the game process" in app,
      "the game process crash handler is exactly the 'kicked back to the home screen' path")
for reason in ("the selected instance could not be loaded", "game setup failed", "starting Minecraft threw"):
    check(reason in activity, "the launch must record: " + reason)
check("which this build does not have" in activity or "did not load" in activity,
      "a refused renderer is a launch that stops for a reason, and must say which")

# The failure has to be the first thing on the Log screen, not below two game logs.
check('LogSource("Launch status (failed)"' in log_screen,
      "a failed launch must be labelled as such in the log list")
check("sources.add(0, LogSource(\"Launch status (failed)\"" in log_screen,
      "it must be first: a player looking for why they were kicked should not scroll")

# LaunchDiagnostics.mark() must keep working: the launch path calls it everywhere.
check("public static void mark(Context context, String stage)" in diagnostics,
      "the existing mark() entry point must survive")

if problems:
    print("launch, key tap and mouse check: FAILED")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print("launch, key tap and mouse check: the panel presses a held key, a mouse gets its own pointer, "
      "and a dead launch leaves a reason behind")
