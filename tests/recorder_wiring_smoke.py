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
assert service.index("proj.registerCallback(") < service.index(".createVirtualDisplay("), \
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
state_src = (REC / "RecordingState.kt").read_text()
assert "ContextCompat.registerReceiver" not in state_src, \
    "RecordingState must not use ContextCompat.registerReceiver (breaks cloned packages on Android < 13 with DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)"
assert "Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU" in state_src and "Context.RECEIVER_NOT_EXPORTED" in state_src, \
    "RecordingState must gate Context.RECEIVER_NOT_EXPORTED on API 33+"
assert "RecordingLibrary.view(activity, file, action == 1)" in controller
assert "RecordingOutput.recoverStrays" in controller

# 6. Output verification & MediaStore publishing.
assert "RecordingPaths.directory(instanceRoot, true)" in output
assert "MediaExtractor" in output and "SAMPLE_FLAG_SYNC" in output
assert "-incomplete.mp4" in output
assert "RecordingLibrary.publish(context, destination)" in output
assert "Movies/" in library and "IS_PENDING" in library and "FLAG_GRANT_READ_URI_PERMISSION" in library
assert "Tools.openPath(context, file, share)" in library

# 7. Clean GPU SurfaceTexture tee when recording without controls (controls stay visible on screen)
#    + draggable live REC overlay pill.
tee = (REC / "RecordingSurfaceTee.kt").read_text()
for forbidden in ("PixelCopy", "ImageWriter", "getBitmap(", "getRecordingSurfaceView"):
    assert forbidden not in tee, f"RecordingSurfaceTee must not use CPU readback ({forbidden})"
assert "GL_TEXTURE_EXTERNAL_OES" in tee and "eglPresentationTimeANDROID" in tee
assert "attachCleanRecorderSurface(" in game and "detachCleanRecorderSurface(" in game
assert "mSurfaceTee" in game_view
assert "EXTRA_WITH_CONTROLS" in controller and "EXTRA_SURFACE_MESSENGER" in controller
assert "MSG_CLEAN_SURFACE" in service and "MSG_DETACH_SURFACE" in service
assert "FOREGROUND_SERVICE_TYPE_SPECIAL_USE" in service
assert "setControlsCapturedInRecording(false)" not in controller, \
    "touch controls must remain visible to the player on screen when recording without controls"
assert "Record with controls" in panel and "stay visible on your screen" in panel
assert "MotionEvent.ACTION_MOVE" in controller and "INDICATOR_NX" in controller and "INDICATOR_NY" in controller, \
    "live REC indicator overlay must be draggable and persist its position"

print("Recorder wiring smoke test passed")
