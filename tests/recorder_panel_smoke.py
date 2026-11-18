#!/usr/bin/env python3
"""The screen recorder's options must be a panel a player can use mid-game.

The old surface was an `AlertDialog` with three `Spinner`s: three taps to change the frame rate, two
of the four choices invisible at any moment, and a paragraph of explanation under a checkbox. The
replacement is a Compose panel, and these checks are about the wiring that makes it behave - not
about how it looks, which a test cannot judge.

What must stay true:

* Every option the panel offers comes from what the device can actually encode, so nothing on screen
  can be chosen and then fail.
* The panel is hosted by the in-game overlay, takes the touches while it is open, and gives them back
  when it closes. A panel under a live game that still receives taps is worse than no panel.
* Back closes the panel before it closes the menu or leaves the game.
* One recorder state: the choices are saved through the same controller that starts the take.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"
panel = (JAVA / "recording/RecorderPanel.kt").read_text()
controller = (JAVA / "recording/RecorderController.kt").read_text()
ui_state = (JAVA / "recording/RecorderUiState.kt").read_text()
overlay = (JAVA / "aerix/client/AerixClientOverlayView.kt").read_text()
activity = (JAVA / "game/GameActivity.java").read_text()
options = (JAVA / "recording/RecordingOptions.kt").read_text()

problems = []


def check(condition, message):
    if not condition:
        problems.append(message)


# 1. A single owner of the recorder state, shared by the controller and the panel.
check("object RecorderUiState" in ui_state, "the panel and controller need one shared state object")
check("val show = mutableStateOf(false)" in ui_state, "the panel's visibility must be Compose state")
check("val active = mutableStateOf(false)" in ui_state, "the panel must know whether a take is running")
for name in ("show", "active", "detail"):
    check(re.search(rf"\bRecorderUiState\.{name}\b", controller) is not None,
          f"the controller must drive RecorderUiState.{name}")
    check(re.search(rf"\bRecorderUiState\.{name}\b", overlay) is not None or name != "show",
          f"the overlay must read RecorderUiState.{name}")

# 2. The options shown are the options this device has, and the pair is resolved before starting.
check("configs.firstOrNull { it.quality == quality && it.fps == fps }" in panel,
      "the chosen quality/fps pair must be resolved against a real encoder config")
check('"No compatible video encoder on this device."' in panel,
      "a pair with no encoder must be reported, not silently started")
check("resolved?.let { \"Recording at" in panel,
      "the panel must show the size and bitrate the take will actually use")
check("configs.map { it.quality }" in panel and "configs.map { it.fps }" in panel,
      "the presets must come from RecordingOptions.supported, not a hardcoded list")
check("RecordingOptions.supported(" in controller,
      "the controller is what enumerates encoders; the panel must not do it itself")

# 3. The panel is hosted by the in-game overlay, above the menu.
check("RecorderPanel(" in overlay, "the overlay must host the panel")
check("import net.kdt.pojavlaunch.recording.RecorderPanel" in overlay,
      "the panel must be imported where it is used")
host_at = overlay.index("RecorderPanel(")
boot_at = overlay.index("CrimsonBootSplash()")
check(host_at > boot_at,
      "the panel must be drawn last, so it sits above the boot splash, menu and HUD")

# 4. Touches reach the panel and never the game while it is open.
check("RecorderUiState.show.value)" in overlay.replace(" ", "") or
      "RecorderUiState.show.value\n" in overlay,
      "the open panel must count as a blocking modal")
blocking = overlay[overlay.index("fun isBlockingModalOpen()"):]
blocking = blocking[:blocking.index("\n    }")]
check("RecorderUiState.show.value" in blocking,
      "isBlockingModalOpen must include the recorder panel, or the game takes its taps")
check("if (visibility == VISIBLE && net.kdt.pojavlaunch.recording.RecorderUiState" in overlay,
      "back must close the panel, and only while this overlay is the one on screen")
back = overlay[overlay.index("fun handleBackPress()"):]
back = back[:back.index("fun isBlockingModalOpen")]
check(back.index("RecorderUiState.show.value") < back.index("menuSubPage"),
      "back must close the recorder panel before it walks the client menu back")

# 5. One path into a take, so the saved choice and the started take cannot disagree.
check("fun startWithChoice(" in controller, "the panel must hand its choices to the controller")
check("controlsInRecording = withControls" in controller,
      "the controls choice must be applied through the controller, not just remembered by the panel")
check("RecorderUiState.show.value = false" in controller[controller.index("fun startWithChoice("):][:400],
      "starting a take must close the panel it was configured in")
check("putBoolean(WITH_CONTROLS, withControls)" in controller,
      "the controls choice must be saved, or the next panel opens on the wrong value")

# 6. The old spinner dialog must be gone, not merely unused.
check("Spinner(" not in controller, "the spinner-based options dialog must be deleted")
check("legacyShowOptions" not in controller, "no dead copy of the old dialog may be left behind")
check("ArrayAdapter" not in controller, "the spinner adapter has no other user")
# The only stock dialog allowed to remain is the "this device has no H.264 encoder" dead end, which
# explains one thing and offers the recordings list. Everything with a choice in it is the panel.
dialogs = [line.strip() for line in controller.splitlines() if 'setTitle("Screen Recorder")' in line]
check(len(dialogs) <= 1,
      "the options are the panel now; a stock dialog must not also offer settings")

# 7. The activity exposes the controller the panel reads from.
check("public net.kdt.pojavlaunch.recording.RecorderController getAerixRecorder()" in activity,
      "GameActivity must expose its recorder for the panel")
check("getAerixRecorder()" in overlay, "the overlay must fetch the controller from the activity")
check("val savedQuality" in controller and "val savedFps" in controller,
      "the panel opens on the last used settings, so they must be readable")
check("fun showRecordingsList()" in controller,
      "the recordings list must stay reachable from the panel")

# 8. Every audio source the panel can show must exist in the options enum.
for source in ("GAME", "MICROPHONE", "BOTH", "NONE"):
    check(source in options, f"audio source {source} must exist for the panel's picker")

# 9. Nothing may draw the panel bigger than the screen.
check("fillMaxWidth(0.92f)" in panel, "the panel must be inset from the screen edges")

# 10. Stay wired into CI.
for workflow in ("aerix-debug.yml", "release.yml"):
    text = (ROOT / ".github/workflows" / workflow).read_text()
    check("recorder_panel_smoke.py" in text, f"{workflow} must run this check")

if problems:
    print("recorder panel check: FAILED")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print("recorder panel check: the options are one-tap presets, owned by the controller, and modal")
