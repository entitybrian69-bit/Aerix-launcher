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
assert 'Recordings' in controller
# A recording must be openable by other apps, so the list goes through the media library copy
# instead of a path inside app-private storage that no player can read.
assert 'RecordingLibrary.view(activity, file, action == 1)' in controller
library = (ROOT / 'recording/RecordingLibrary.kt').read_text()
assert 'Movies/$ALBUM' in library or 'DIRECTORY_MOVIES' in library
assert 'IS_PENDING' in library and 'FLAG_GRANT_READ_URI_PERMISSION' in library
assert 'Tools.openPath(context, file, share)' in library, 'the document provider stays the fallback'
assert 'RecordingLibrary.publish(context, destination)' in output
assert 'mediaUri' in output and 'Saved(destination.absolutePath, valid, media)' in output
assert 'worker.post { stopRecording() }' in service
assert 'requestedAudio.microphone' in service
assert 'worker.postDelayed(audioTimeout' in service
assert 'MediaCodec.createByCodecName(name)' in service and 'candidate.setCallback(callback, worker)' in service
assert 'MediaCodec.BUFFER_FLAG_KEY_FRAME' in service
assert 'pendingAudioFormat' in service

# "Record with controls" off must fade the control layer, never hide it: a GONE or INVISIBLE control
# stops receiving touches, and a player who cannot jump until the recording ends is not a feature.
controls = (ROOT / 'customcontrols/ControlLayout.java').read_text()
assert 'private float mRecordingOpacity = 1.0f;' in controls
setter = controls.split('public void setRecordingOpacity(float opacity)')[1][:700]
assert 'updateButtonOpacity();' in setter, 'fading must go through the one path that sets button alpha'
assert 'setVisibility' not in setter and 'GONE' not in setter and 'INVISIBLE' not in setter, \
    'the recording fade must not touch view visibility'
for site in ('mButtonsOpacity = Math.clamp((float) LauncherPreferences.PREF_BUTTON_TRANSPARENCY / 100, 0, 1) * mRecordingOpacity',
             'this.mButtonsOpacity = (float) LauncherPreferences.PREF_BUTTON_TRANSPARENCY / 100 * mRecordingOpacity'):
    assert site in controls, 'the recording fade must compose with the player transparency setting at ' + site
# 0% transparency is an existing, used setting, which is what makes alpha safe to drive here.
repo = Path(__file__).resolve().parents[1]
assert 'app2:min="0"' in (repo / 'app_pojavlauncher/src/main/res/xml/pref_control.xml').read_text(), \
    'the transparency slider reaching 0% is the precedent that alpha can carry this feature'

# The recorder owns the session choice, persists it, and restores the controls on every exit path.
# The checkbox and the spinners are gone: the choice now comes from the Compose panel, which hands it
# over through startWithChoice and nothing else. These checks follow the choice, not the widget.
panel = (ROOT / 'recording/RecorderPanel.kt').read_text()
assert 'Record with controls' in panel, 'the choice must still be offered, now in the panel'
assert 'var withControls by remember { mutableStateOf(initialWithControls) }' in panel, \
    'the panel must open on the saved choice'
assert 'onStart(config, audio, withControls)' in panel, 'the panel must report the toggle with the start'
assert 'controlsInRecording = withControls' in controller
assert 'dismissPanel()' in controller.split('fun startWithChoice(')[1][:400], \
    'starting a take must close the panel it was configured in'
assert 'CheckBox' not in controller, 'the old dialog widget must be deleted, not left unused'
assert 'Spinner(' not in controller, 'the old dialog widgets must be deleted, not left unused'
assert '.putBoolean(WITH_CONTROLS, withControls)' in controller
assert 'private const val WITH_CONTROLS = "with_controls"' in controller
assert 'start(config: RecordingOptions.Config, withControls: Boolean = true)' in controller, \
    'record-with-controls must default on, matching the old behavior for existing installs'
assert 'applyControlsToRecording()' in controller.split('private val stateListener')[1].split('\n    }')[0], \
    'the fade must follow the recorder phase, not just the button press'
assert 'val ghosted = !controlsInRecording && RecordingState.isActive()' in controller, \
    'the controls must come back whenever no recording is running, including a cancelled consent sheet'
game_controls = game.split('public void setControlsCapturedInRecording')[1].split('\n    }')[0]
assert 'mControlLayout.setRecordingOpacity(captured ? 1f : 0f)' in game_controls
assert 'if (mControlLayout == null) return' in game_controls
assert controller.index('(activity as? GameActivity)?.setControlsCapturedInRecording(true)') < \
    controller.index('RecordingState.removeListener(stateListener)'), \
    'destroying the recorder must restore the controls before it stops listening for the stop signal'

# Ghosting the controls must be escapable and must be announced: the player cannot be left with an
# invisible control layer they did not ask for, and only the *displayed* pixels can be excluded.
assert 'fun onControlsRequestedByPlayer()' in controller
player_hook = controller.split('fun onControlsRequestedByPlayer()')[1].split('\n    }')[0]
assert 'if (!ghostingControls) return' in player_hook, 'only an active fade should be cancelled by the player'
assert 'controlsInRecording = true' in player_hook
assert 'prefs.edit()' not in player_hook, \
    "the player's escape hatch must not rewrite the saved setting: the next recording stays clean"
fade = controller.split('private fun applyControlsToRecording()')[1].split('\n    }')[0]
assert 'setControlsCapturedInRecording(!ghosted)' in fade
assert 'cleanCaptureActive' in fade, \
    'fading is only the fallback now: while the encoder is fed the game surface, the controls stay visible'
assert 'Toast.makeText(' in fade and 'hide/show controls button' in fade, \
    'fading the controls out has to tell the player they are still there and still work'
assert 'ghostingControls = false' in controller.split('fun destroy()')[1][:600]
cl = (ROOT / 'customcontrols/ControlLayout.java').read_text()
toggle = cl.split('public void toggleControlVisible()')[1].split('\n\t}')[0]
assert 'mOnPlayerControlsToggle.onPlayerToggled()' in toggle, \
    'the players own toggle has to reach the recorder'
assert 'interface OnPlayerControlsToggle' in cl and 'setOnPlayerControlsToggle' in cl
# the fade stays a fade: no visibility change anywhere on this path
assert 'setVisibility' not in toggle
assert 'mControlLayout.setOnPlayerControlsToggle' in game and 'mRecorder.onControlsRequestedByPlayer()' in game
# The panel's caption must describe the mechanism that actually runs, and must not overpromise: a
# device that refuses a surface readback gets the fade, and the wording has to say so before the
# player commits. The long two-paragraph explanation that used to swap under a checkbox is now one
# short line per state - the switch is the choice, so the caption only says what that choice means.
hint = panel.split('private fun ControlsRow(')[1]
assert "Game frames only" in hint, \
    'the caption has to name the capture source, not just the visual effect'
assert 'never appear in the video' in hint and 'stay tappable' in hint, \
    'the promise the mode makes has to be the one stated'
assert 'Your touch controls appear in the video.' in hint
# The render-path caveat is still surfaced - it moved to the recorder, which is the side that knows
# whether the device refused the frames, rather than to a caption written before the take.
assert 'onCleanCaptureUnavailable' in controller and 'would not give up the game' in controller, \
    'a device that refuses the frames must still be told about it, at the moment it happens'
assert 'readbackIsDirect' in (ROOT / 'recording/GameCapture.kt').read_text()

print('Recorder wiring smoke test passed')
