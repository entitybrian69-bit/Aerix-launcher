#!/usr/bin/env python3
"""Regression checks for the redesigned zero-copy, crash-proof screen recorder."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"
REC = SRC / "recording"

# 1. Laggy/crashy CPU readback classes must be completely removed.
assert not (REC / "GameSurfaceFeed.kt").exists(), "GameSurfaceFeed.kt must be deleted"
assert not (REC / "GameCapture.kt").exists(), "GameCapture.kt must be deleted"
assert not (ROOT / "tests/GameCaptureTest.kt").exists(), "GameCaptureTest.kt must be deleted"
assert not (ROOT / "tests/GameSurfaceFeedOfflineTest.kt").exists(), "GameSurfaceFeedOfflineTest.kt must be deleted"
assert not (ROOT / "tests/game_surface_capture_smoke.py").exists(), "game_surface_capture_smoke.py must be deleted"

controller = (REC / "RecorderController.kt").read_text()
service = (REC / "ScreenRecordingService.kt").read_text()
audio = (REC / "RecordingAudio.kt").read_text()
output = (REC / "RecordingOutput.kt").read_text()
library = (REC / "RecordingLibrary.kt").read_text()
options = (REC / "RecordingOptions.kt").read_text()
panel = (REC / "RecorderPanel.kt").read_text()
game = (SRC / "game/GameActivity.java").read_text()
game_view = (SRC / "game/GameView.java").read_text()

# 2. Zero CPU pixel readback anywhere in recorder or game views.
for forbidden in ("PixelCopy", "ImageWriter", "getBitmap(", "getRecordingSurfaceView"):
    assert forbidden not in controller, f"controller must not reference {forbidden}"
    assert forbidden not in service, f"service must not reference {forbidden}"
    assert forbidden not in game, f"GameActivity must not reference {forbidden}"
    assert forbidden not in game_view, f"GameView must not reference {forbidden}"

# 3. Hardware VirtualDisplay -> MediaCodec Surface pipeline + Android 14+ safety in :recorder.
assert "createVirtualDisplay(" in service and "VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR" in service
assert "proj.registerCallback(projCb, worker)" in service, "MediaProjection.Callback must register before createVirtualDisplay"
assert service.index("proj.registerCallback(") < service.index("proj.createVirtualDisplay("), \
    "Android 14+ requires registering MediaProjection.Callback before createVirtualDisplay"
assert "onCapturedContentResize" in service and "onCapturedContentVisibilityChanged" in service
assert "if (!foregroundPromoted)" in service and "promoteToForeground(" in service
assert service.index("promoteToForeground(") < service.index("when (action)"), \
    "startForeground must execute synchronously before any early return"
assert "FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION" in service
assert "FOREGROUND_SERVICE_TYPE_MICROPHONE" in service
assert "signalEndOfInputStream()" in service
assert "STOP_TIMEOUT_MS" in service, "stop must have a bounded failsafe timeout"
assert "KEY_PROFILE" not in service and "KEY_MAX_B_FRAMES" not in service and "KEY_MAX_FPS_TO_ENCODER" not in service, \
    "fragile vendor keys must not be forced on encoder configuration"

# 4. Non-blocking, zero-allocation, immediately-primed audio capture.
assert "READ_NON_BLOCKING" in audio, "audio capture must use non-blocking reads to avoid silence stalls and stop ANRs"
assert "READ_BLOCKING" not in audio, "blocking AudioRecord.read must not be used"
assert "queueSilentPrimeFrame(" in audio, "AAC encoder must be primed immediately so MediaMuxer never stalls"
assert "48_000" in audio, "48 kHz native AudioFlinger rate must be preferred"

# 5. Controller lifecycle, consent safety, and immediate foreground launch.
assert "RecorderController(this, instance.getInstanceDirectory())" in game
assert '.putExtra("instanceRoot", instanceDir?.absolutePath)' in controller
assert '.putExtra("audio_source", current.audioSource.name)' in controller
assert "postDelayed(" not in controller[controller.index("fun onActivityResult"):controller.index("fun stop()")], \
    "onActivityResult must startForegroundService immediately without a 220ms race delay"
assert "if (awaitingConsentSheet) return" in controller, \
    "onGameStopped must not discard pending consent while system permission dialog is open"
assert "unregisterReceiver(receiver)" in controller, "state BroadcastReceiver must be unregistered in destroy()"
assert "RecordingLibrary.view(activity, file, action == 1)" in controller
assert "RecordingOutput.recoverStrays" in controller

# 6. Output verification & MediaStore publishing.
assert "RecordingPaths.directory(instanceRoot, true)" in output
assert "MediaExtractor" in output and "SAMPLE_FLAG_SYNC" in output
assert "-incomplete.mp4" in output
assert "RecordingLibrary.publish(context, destination)" in output
assert "Movies/" in library and "IS_PENDING" in library and "FLAG_GRANT_READ_URI_PERMISSION" in library
assert "Tools.openPath(context, file, share)" in library

# 7. Clean control-fade via alpha (never View.GONE / View.INVISIBLE).
controls = (SRC / "customcontrols/ControlLayout.java").read_text()
assert "private float mRecordingOpacity = 1.0f;" in controls
setter = controls.split("public void setRecordingOpacity(float opacity)")[1][:700]
assert "updateButtonOpacity();" in setter
assert "setVisibility" not in setter and "GONE" not in setter and "INVISIBLE" not in setter
assert "setControlsCapturedInRecording(!ghosted)" in controller
assert "Record with controls" in panel

print("Recorder wiring smoke test passed")
