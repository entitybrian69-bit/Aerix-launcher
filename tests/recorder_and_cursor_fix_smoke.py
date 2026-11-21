#!/usr/bin/env python3
"""Regression checks for MP4 recorder save/playback fixes and virtual cursor restoration."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

service = (JAVA / "recording/ScreenRecordingService.kt").read_text()
audio = (JAVA / "recording/RecordingAudio.kt").read_text()
timestamps = (JAVA / "recording/RecordingTimestamps.kt").read_text()
output = (JAVA / "recording/RecordingOutput.kt").read_text()
library = (JAVA / "recording/RecordingLibrary.kt").read_text()
options = (JAVA / "recording/RecordingOptions.kt").read_text()
file_bridge = (JAVA / "aerix/bridge/PojavFileBridge.kt").read_text()
tools = (JAVA / "Tools.java").read_text()
game_view = (JAVA / "game/GameView.java").read_text()
game_activity = (JAVA / "game/GameActivity.java").read_text()

# 1. Recorder MP4 saving & Files playback
assert "hasPreStartKeyframe" in service and "hasPreStartAudio" in service
assert "AUDIO_WAIT_TIMEOUT_MS" in service and "forceVideoOnly" in service
assert "out.position(info.offset)" in service and "out.limit(info.offset + info.size)" in service
assert service.index("videoEncoder?.signalEndOfInputStream()") < service.index("virtualDisplay?.release()", service.index("private fun stopRecording()"))
assert "thread.join(" not in audio, "RecordingAudio.stop must not block worker thread"
assert "if (videoBase == null) videoBase = if (raw >= epochUs) epochUs else raw" in timestamps
assert "video.widthAlignment.coerceAtLeast(8)" in options and "video.heightAlignment.coerceAtLeast(8)" in options
assert "while (checked < 30)" in output
assert 'file.extension.equals("mp4", ignoreCase = true)' in file_bridge
assert "RecordingLibrary.view(context, file, share)" in file_bridge
assert "MimeTypeMap.getSingleton().getMimeTypeFromExtension" in tools
assert "MediaStore.MediaColumns.RELATIVE_PATH" in library

# 2. Virtual cursor restoration on overlay / popup / touch return
assert "isPhysicalMouseDevice(MotionEvent event)" in game_view
assert "restoreVirtualCursorIfTouch()" in game_view
assert "noteHardwarePointer(false)" in game_view
assert "MotionEvent.ACTION_HOVER_EXIT" in game_view
assert "onWindowFocusChanged(boolean hasWindowFocus)" in game_view
assert "restoreVirtualCursorIfTouch()" in game_activity and "TOOL_TYPE_FINGER" in game_activity

# 3. Download cancellation UX & insertMetadata immediate shutdown + FileProvider sharing
downloader = (JAVA / "downloader/Downloader.java").read_text()
complete_meta = (JAVA / "downloader/CompleteMetadataTask.java").read_text()
mojson = (JAVA / "tasks/MoJsonDownloader.java").read_text()
done_listener = (JAVA / "lifecycle/ContextAwareDoneListener.java").read_text()
manifest = (ROOT / "app_pojavlauncher/src/main/AndroidManifest.xml").read_text()
shared_paths = (ROOT / "app_pojavlauncher/src/main/res/xml/shared_paths.xml").read_text()

assert "try (ExecutorService executorService" not in downloader
assert "executorService.shutdownNow();" in downloader
assert "downloadString(new URL(mMetadata.url + \".sha1\"), 2000)" in complete_meta
assert "throw new PresentableException(R.string.dock_cancelled, R.string.dock_cancelled_detail);" in mojson
assert "DownloadControl.CancelledException" in done_listener
assert "FileProvider.getUriForFile(" in tools and "ClipData.newRawUri(" in tools
assert "${applicationId}.files" in manifest and "@xml/shared_paths" in manifest
assert "<root-path" in shared_paths
assert "IS_PENDING} = 0" in library and "SIZE} > 0" in library
assert "Could not open MediaStore output stream" in library

print("Recorder MP4 save/playback, virtual cursor restoration, download cancel & file save: smoke checks passed")
