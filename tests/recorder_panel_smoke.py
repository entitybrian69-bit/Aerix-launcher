#!/usr/bin/env python3
"""The screen recorder's options must be a panel a player can use mid-game.

The old surface was an `AlertDialog` with three `Spinner`s: three taps to change the frame rate, two
of the four choices invisible at any moment, and a paragraph of explanation under a checkbox. The
replacement is a Compose panel, and these checks are about the wiring that makes it behave - not
about how it looks, which a test cannot judge.

What must stay true:

* Every option the panel offers comes from what the device can actually encode, so nothing on screen
  can be chosen and then fail.
* The panel is a window of its own, owned by the recorder controller rather than by the in-game
  overlay: the overlay is GONE on a non-Client instance, so a panel drawn there was invisible on
  exactly the instances where the menu still opens the recorder. It takes the touches while it is
  open, and gives them back when it closes.
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
    if name != "show":
        # `show` is the controller's own switch now - a dialog either exists or it does not, so the
        # panel has nothing to read. The live phase is still shared, because the panel draws it.
        check(re.search(rf"\bRecorderUiState\.{name}\b", panel) is not None,
              f"the panel must read RecorderUiState.{name}")
    check(re.search(rf"\bRecorderUiState\.{name}\b", overlay) is None,
          f"the overlay must not read RecorderUiState.{name}: it no longer draws the panel")

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

# 3. The panel is a window of its own, owned by the controller.
#    It used to be hosted inside AerixClientOverlayView, which is visibility=GONE on any instance
#    that is not an Aerix Client - so on a vanilla instance the recorder set "open", drew nothing,
#    and looked broken. A dialog belongs to the activity, so it opens from wherever it is reached.
check("RecorderPanel(" not in overlay,
      "the panel must not be hosted by the overlay: that view is GONE on non-Client instances")
check("Dialog(" in controller and "setContentView(view)" in controller,
      "the panel must be a dialog window the controller opens")
check("RecorderPanel(" in controller, "the dialog is what composes the panel")
check("ComposeView(context)" in controller, "the dialog hosts a ComposeView")

# A ComposeView in a dialog window has no lifecycle/ViewModel/saved-state owners, and a composition
# without a lifecycle owner silently never starts - which looks exactly like the bug being fixed.
check("setViewTreeLifecycleOwner" in controller and "setViewTreeSavedStateRegistryOwner" in controller,
      "the dialog's view tree must be given the activity's owners or the panel never composes")
check("ensureViewTreeOwners(window.decorView)" in controller,
      "the owners go on the dialog's decor view, which is the tree the ComposeView lives in")
check("ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool" in controller,
      "closing the panel must dispose the composition, not leak a recomposer")

# 4. Touches reach the panel and never the game while it is open, and back closes only the panel.
check("setCanceledOnTouchOutside(false)" in controller,
      "a stray tap outside must not dismiss the panel that is being configured")
check("KeyEvent.KEYCODE_BACK" in controller and "KeyEvent.KEYCODE_ESCAPE" in controller,
      "back and escape must close the panel")
check("RecorderUiState.show.value" not in overlay,
      "the overlay must no longer gate touches or back on recorder state it does not draw")

# The window must be taken down on every exit: a leaked one is a Crimson sheet over the next launch.
for exit_point in ("fun dismissPanel()", "fun destroy()"):
    check("dismissPanel()" in controller[controller.index(exit_point):][:400],
          exit_point + " must close the panel window")
check("panelDialog = null" in controller and "dialog.isShowing" in controller,
      "the panel window must be dropped, not just flagged closed")
check("dismissPanel()" in controller[controller.index("fun startWithChoice("):][:600],
      "starting a take must close the panel it was configured in")
# The "already recording" path reached the panel state without ever making a window - the same bug.
in_progress = controller[controller.index("if (RecordingState.isActive() || startRequested)"):]
check("openPanel(" in in_progress[:400],
      "the recording-in-progress path must open the same window, not just set the flag")

# 4b. Every symbol this change reaches for must be imported. CI is the only Kotlin compiler this
#     project has, so a missing import costs a whole build to discover - one already did.
import_lines = [line for line in controller.splitlines() if line.startswith("import ")]
imports = {line[len("import "):].strip() for line in import_lines}
used = {
    "findViewTreeOnBackPressedDispatcherOwner", "setViewTreeOnBackPressedDispatcherOwner",
    "findViewTreeLifecycleOwner", "setViewTreeLifecycleOwner",
    "findViewTreeViewModelStoreOwner", "setViewTreeViewModelStoreOwner",
    "findViewTreeSavedStateRegistryOwner", "setViewTreeSavedStateRegistryOwner",
    "OnBackPressedDispatcherOwner", "LifecycleOwner", "ViewModelStoreOwner",
    "SavedStateRegistryOwner", "ViewCompositionStrategy", "ComposeView",
    "AerixLauncherTheme", "Dialog", "Context", "KeyEvent",
}
imported_names = {line[len("import "):].strip().split(".")[-1] for line in import_lines}
missing = sorted(sym for sym in used if sym not in imported_names and sym in controller)
check(not missing, "used but not imported in RecorderController, which fails the build: " + ", ".join(missing))

dupes = sorted({line for line in import_lines if import_lines.count(line) > 1})
check(not dupes, "duplicate imports: " + ", ".join(dupes))

# 5. One path into a take, so the saved choice and the started take cannot disagree.
check("fun startWithChoice(" in controller, "the panel must hand its choices to the controller")
check("controlsInRecording = withControls" in controller,
      "the controls choice must be applied through the controller, not just remembered by the panel")
check("dismissPanel()" in controller[controller.index("fun startWithChoice("):][:600],
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
check("getAerixRecorder()" in activity.split("public net.kdt.pojavlaunch.recording.RecorderController")[1],
      "the activity must return the controller the panel reads from")
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
