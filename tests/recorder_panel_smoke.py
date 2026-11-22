#!/usr/bin/env python3
"""Smoke checks for the redesigned in-game screen recorder panel and controller."""
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


# 1. Shared reactive Compose state between controller and panel, never drawn inside AerixClientOverlayView.
check("object RecorderUiState" in ui_state, "RecorderUiState must exist")
for name in ("show", "active", "phase", "detail", "startedAtElapsedMs"):
    check(re.search(rf"\bval {name}\b", ui_state) is not None, f"RecorderUiState.{name} missing")
    if name != "show":
        check(re.search(rf"\bRecorderUiState\.{name}\b", panel) is not None,
              f"panel must read RecorderUiState.{name}")
    check(re.search(rf"\bRecorderUiState\.{name}\b", overlay) is None,
          f"overlay must not read RecorderUiState.{name}")
check("RecorderPanel(" not in overlay, "panel must not live inside AerixClientOverlayView")

# 2. Presets come from hardware-validated RecordingOptions.supported and resolve cleanly.
check("configs.firstOrNull { it.quality == quality && it.fps == fps }" in panel,
      "chosen quality/fps pair must resolve against supported encoder configs")
check("No compatible video encoder" in panel,
      "unsupported pairs must be reported clearly")
check("configs.map { it.quality }" in panel and "configs.map { it.fps }" in panel,
      "presets must come from RecordingOptions.supported")
check("RecordingOptions.supported(" in controller,
      "controller must enumerate encoders via RecordingOptions.supported")
check("DEFAULT_QUALITY = 720" in options and "DEFAULT_FPS = 30" in options,
      "default preset must be smooth 720p @ 30 FPS (no 24fps pulldown judder)")
check("listOf(24, 30, 48, 60)" in options,
      "frame rates must stay within smooth hardware-friendly bounds (24/30/48/60 FPS)")

# 3. In-activity Compose overlay host (no fragile Dialog + ViewTreeOwner hacks).
check("ComposeView(activity)" in controller and "android.R.id.content" in controller,
      "controller must mount ComposeView directly in android.R.id.content")
check("ViewCompositionStrategy.DisposeOnDetachedFromWindow" in controller,
      "closing panel must dispose ComposeView cleanly")
check("ensureViewTreeOwners" not in controller and "Dialog(" not in controller,
      "old fragile Dialog + ViewTreeOwner hack must be removed")
check("isPanelOpen()" in controller and "mRecorder.isPanelOpen()" in activity,
      "GameActivity.dispatchKeyEvent must close open recorder panel on Back/Escape")
check("KEYCODE_BACK" in activity and "KEYCODE_ESCAPE" in activity,
      "GameActivity must intercept Back and Escape for the recorder panel")

# 4. Lifecycle & one-path start.
for exit_point in ("fun dismissPanel()", "fun destroy()"):
    check("dismissPanel()" in controller[controller.index(exit_point):][:400],
          exit_point + " must close the panel")
check("fun startWithChoice(" in controller, "panel must hand choices to startWithChoice")
check("dismissPanel()" in controller[controller.index("fun startWithChoice("):][:300],
      "starting a take must close the panel")
check("fun showRecordingsList()" in controller, "recordings list must be reachable from panel")
check("fillMaxWidth(0.92f)" in panel, "panel must be inset from screen edges")

# 5. Old spinner widgets removed.
check("Spinner(" not in controller and "ArrayAdapter" not in controller,
      "old Spinner dialog widgets must stay deleted")

# 6. Activity exposes controller & all audio sources exist.
check("public net.kdt.pojavlaunch.recording.RecorderController getAerixRecorder()" in activity,
      "GameActivity must expose getAerixRecorder()")
check("public void onRecorderPanelVisibilityChanged(boolean shown)" in activity,
      "GameActivity must release pointer capture / focus when recorder panel opens")
for source in ("GAME", "MICROPHONE", "BOTH", "NONE"):
    check(source in options, f"audio source {source} must exist")

# 8. Recorder button & responsiveness regressions stay fixed.
check("AerixWindowInsets.padding()" in panel,
      "panel must respect system bar / cutout insets so buttons are never clipped")
check("candidates.filter { it.quality <= quality }" in panel,
      "tapping an FPS chip must auto-pick a compatible resolution instead of disabling Start Recording")
check("onToggleControlsDuringRecording(" in panel and "setControlsDuringRecording(" in controller,
      "controls visibility toggle must also work while recording is active")
check("stopWatchdog" in controller,
      "stop() must reset state via watchdog if :recorder died")
check("onRecorderPanelVisibilityChanged(" in controller,
      "controller must notify GameActivity when panel opens/closes")

# 7. Stay wired into CI.
for workflow in ("aerix-debug.yml", "release.yml"):
    text = (ROOT / ".github/workflows" / workflow).read_text()
    check("recorder_panel_smoke.py" in text, f"{workflow} must run recorder_panel_smoke.py")

if problems:
    print("recorder panel check: FAILED")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print("recorder panel check: redesigned in-activity panel, hardware presets, and lifecycle verified")
