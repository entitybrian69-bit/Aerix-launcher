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
assert 'import android.widget.CheckBox' in controller
assert 'Record with controls' in controller
assert 'isChecked = controlsInRecording' in controller
assert 'private var controlsInRecording = prefs.getBoolean(WITH_CONTROLS, true)' in controller, \
    'the session choice must survive an activity recreation mid-recording'
assert 'start(match.copy(audioSource = chosenAudio), withControls.isChecked)' in controller
assert 'controlsInRecording = withControls' in controller
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
# the dialog must not promise something Android cannot do: it says the buttons stop being drawn
hint = controller.split('fun describeControls')[1].split('\n        }')[0]
assert 'Android records the whole screen' in hint and 'keep taking touches' in hint
assert 'Tap your hide/show controls button' in hint

print('Recorder wiring smoke test passed')
