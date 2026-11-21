package net.kdt.pojavlaunch.recording

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecorderControllerTest {
    private lateinit var activity: Activity
    private lateinit var controller: RecorderController
    private val normal = RecordingOptions.Config(720, 30, 1280, 720, 2_200_000L, "c2.qti.avc.encoder")
    private val high = RecordingOptions.Config(1080, 60, 1920, 1080, 9_900_000L, "c2.qti.avc.encoder")

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        shadowOf(activity.application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        activity.getSharedPreferences("aerix_recorder", 0).edit().clear().commit()
        RecordingState.update(RecordingState.Phase.IDLE)
        idle()
        controller = RecorderController(activity)
    }

    @After fun cleanup() {
        controller.destroy()
        RecordingState.update(RecordingState.Phase.IDLE)
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun defaultsTo720p30AndGameAudio() {
        assertEquals(720, controller.savedQuality)
        assertEquals(30, controller.savedFps)
        assertEquals(RecordingOptions.AudioSource.GAME, controller.savedAudio)
        assertTrue(controller.savedWithControls)
    }

    @Test fun startWithChoiceRequestsSystemConsentImmediatelyNotCapture() {
        controller.startWithChoice(normal, RecordingOptions.AudioSource.GAME, withControls = false)
        idle()
        val request = shadowOf(activity).nextStartedActivityForResult
        assertNotNull(request)
        assertEquals(RecorderController.REQUEST_CAPTURE, request.requestCode)
        assertNull(shadowOf(activity.application).nextStartedService)
        assertFalse(controller.savedWithControls)
        assertTrue(controller.onActivityResult(RecorderController.REQUEST_CAPTURE, Activity.RESULT_CANCELED, null))
        assertNull(shadowOf(activity.application).nextStartedService)
    }

    @Test fun consentResultStartsServiceImmediatelyAndOnGameStoppedDoesNotCancelPendingConsent() {
        controller.startWithChoice(high, RecordingOptions.AudioSource.BOTH, withControls = true)
        idle()
        // Transient onStop while Android's MediaProjection dialog is visible must NOT wipe pending!
        controller.onGameStopped()
        assertTrue(controller.onActivityResult(RecorderController.REQUEST_CAPTURE, Activity.RESULT_OK, Intent("consent")))
        val start = shadowOf(activity.application).nextStartedService
        assertNotNull(start)
        assertEquals(ScreenRecordingService.START, start.action)
        assertEquals(1080, start.getIntExtra("quality", 0))
        assertEquals(60, start.getIntExtra("fps", 0))
        assertEquals(1920, start.getIntExtra("width", 0))
        assertEquals("c2.qti.avc.encoder", start.getStringExtra("encoder"))
        assertEquals("BOTH", start.getStringExtra("audio_source"))

        controller.onGameStopped()
        assertEquals(ScreenRecordingService.STOP, shadowOf(activity.application).nextStartedService.action)
    }

    @Test fun deniedMicrophoneDegradesGracefullyToVideoOnlyCaptureConsent() {
        shadowOf(activity.application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        controller.startWithChoice(normal, RecordingOptions.AudioSource.GAME, withControls = true)
        idle()
        assertEquals(RecorderController.REQUEST_AUDIO, shadowOf(activity).nextStartedActivityForResult.requestCode)
        assertTrue(controller.onRequestPermissionsResult(RecorderController.REQUEST_AUDIO, intArrayOf(PackageManager.PERMISSION_DENIED)))
        assertEquals(RecorderController.REQUEST_CAPTURE, shadowOf(activity).nextStartedActivityForResult.requestCode)
        controller.onActivityResult(RecorderController.REQUEST_CAPTURE, Activity.RESULT_OK, Intent("consent"))
        assertEquals("NONE", shadowOf(activity.application).nextStartedService.getStringExtra("audio_source"))
    }

    @Test fun indicatorTracksStateAndIsRemovedOnDestroy() {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val indicator = root.getChildAt(root.childCount - 1)
        assertEquals(View.GONE, indicator.visibility)
        RecordingState.update(RecordingState.Phase.RECORDING, "720p · 30 FPS")
        idle()
        assertEquals(View.VISIBLE, indicator.visibility)
        RecordingState.update(RecordingState.Phase.IDLE)
        idle()
        assertEquals(View.GONE, indicator.visibility)
        controller.destroy()
        assertNull(indicator.parent)
    }

    @Test fun audioSourceSelectionSurvivesControllerRecreation() {
        controller.startWithChoice(normal, RecordingOptions.AudioSource.MICROPHONE, withControls = false)
        idle()
        controller.destroy()
        controller = RecorderController(activity)
        assertEquals(RecordingOptions.AudioSource.MICROPHONE, controller.savedAudio)
        assertFalse(controller.savedWithControls)
    }
}
