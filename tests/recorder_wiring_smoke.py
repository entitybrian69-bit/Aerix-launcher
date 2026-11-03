#!/usr/bin/env python3
"""Regression checks for recorder lifecycle and per-instance output wiring."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch'
controller = (ROOT / 'recording/RecorderController.kt').read_text()
service = (ROOT / 'recording/ScreenRecordingService.kt').read_text()
output = (ROOT / 'recording/RecordingOutput.kt').read_text()
game = (ROOT / 'game/GameActivity.java').read_text()
assert 'RecorderController(this, instance.getInstanceDirectory())' in game
assert '.putExtra("instanceRoot", instanceDir?.absolutePath)' in controller
assert 'RecordingPaths.directory(instanceRoot, true)' in output
assert 'MediaExtractor' in output and 'SAMPLE_FLAG_SYNC' in output
assert '-incomplete.mp4' in output
assert 'Recordings' in controller and 'Tools.openPath(activity, file, action == 1)' in controller
assert 'worker.post { stopRecording() }' in service
assert 'requestedAudio.microphone' in service
assert 'worker.postDelayed(audioTimeout' in service
assert 'MediaCodec.createByCodecName(name)' in service and 'candidate.setCallback(callback, worker)' in service
assert 'MediaCodec.BUFFER_FLAG_KEY_FRAME' in service
assert 'pendingAudioFormat' in service
print('Recorder wiring smoke test passed')
