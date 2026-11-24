#!/usr/bin/env python3
"""Regression checks for .partial recording finalization and automatic .mp4 conversion."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

output = (JAVA / "recording/RecordingOutput.kt").read_text()
service = (JAVA / "recording/ScreenRecordingService.kt").read_text()
main_fragment = (JAVA / "aerix/ui/AerixMainFragment.kt").read_text()
file_bridge = (JAVA / "aerix/bridge/PojavFileBridge.kt").read_text()

# 1. RecordingOutput.finish() must never pre-create an empty destination file before renameTo()
#    (on FUSE/OEM mounts, renameTo fails if destination already exists, leaving .partial behind).
assert "destination.createNewFile()" not in output, "createNewFile() before renameTo breaks FUSE mounts"
assert "uniqueDestination(" in output
assert "moveToMp4(" in output
assert "Files.move(" in output
assert "outputStream()" in output

# 2. Existing .partial files must automatically convert to .mp4 on launcher startup and file browsing.
assert "fun recoverAllInstances(context: Context)" in output
assert "synchronized(recoveryLock)" in output
assert "RecordingOutput.recoverAllInstances" in main_fragment
assert "RecordingOutput.recoverStrays" in file_bridge

# 3. ScreenRecordingService must flush muxer & output synchronously on task removal / destroy.
assert "override fun onTaskRemoved(" in service
assert "finalizeSynchronously()" in service
assert "CountDownLatch(1)" in service

print("Recording .partial finalization and automatic .mp4 conversion smoke checks passed")
