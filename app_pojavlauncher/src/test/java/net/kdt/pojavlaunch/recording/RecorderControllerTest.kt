package net.kdt.pojavlaunch.recording

import android.Manifest
import android.content.pm.PackageManager
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class RecorderControllerTest {
    private lateinit var activity: Activity
    private lateinit var controller: RecorderController
    private val normal = RecordingOptions.Config(480, 24, 1066, 480, 1_500_000, "vendor.hardware")
    private val high = RecordingOptions.Config(1080, 60, 2400, 1080, 18_000_000, "vendor.hardware")

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
    private fun allSpinners(view: View): List<Spinner> = when (view) {
        is Spinner -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { allSpinners(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun options(): AlertDialog {
        controller.showOptions(listOf(normal, high))
        idle()
        return ShadowAlertDialog.getLatestAlertDialog()
    }

    @Test fun separateSelectorsShowEveryRequestedChoiceAndDefaultTo480p24() {
        val dialog = options()
        val spinners = allSpinners(dialog.window!!.decorView)
        assertEquals(2, spinners.size)
        assertEquals(5, spinners[0].count)
        assertEquals(7, spinners[1].count)
        assertEquals("480p", spinners[0].selectedItem)
        assertEquals("24 FPS", spinners[1].selectedItem)
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
    }
    @Test fun unsupportedPairCannotStartButSupportedPairCan() {
        val dialog = options()
        val spinners = allSpinners(dialog.window!!.decorView)
        spinners[0].setSelection(4) // 1080p/24 is not reported as supported.
        idle()
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        spinners[1].setSelection(RecordingOptions.frameRates.indexOf(60))
        idle()
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
    }
    @Test fun startRequestsSystemConsentNotImmediateCapture() {
        val dialog = options()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        val request = shadowOf(activity).nextStartedActivityForResult
        assertNotNull(request)
        assertEquals(RecorderController.REQUEST_CAPTURE, request.requestCode)
        assertNull(shadowOf(activity.application).nextStartedService)
        assertFalse(RecordingState.isActive())
        assertTrue(controller.onActivityResult(RecorderController.REQUEST_CAPTURE, Activity.RESULT_CANCELED, null))
        assertNull(shadowOf(activity.application).nextStartedService)
    }
    @Test fun consentResultStartsServiceWithExactSelectedConfigAndLeavingRequestsStop() {
        options().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertTrue(controller.onActivityResult(RecorderController.REQUEST_CAPTURE, Activity.RESULT_OK, Intent("test-consent")))
        val start = shadowOf(activity.application).nextStartedService
        assertEquals(ScreenRecordingService.START, start.action)
        assertEquals(480, start.getIntExtra("quality", 0))
        assertEquals(24, start.getIntExtra("fps", 0))
        assertEquals(1066, start.getIntExtra("width", 0))
        assertEquals("vendor.hardware", start.getStringExtra("encoder"))
        controller.onGameStopped()
        assertEquals(ScreenRecordingService.STOP, shadowOf(activity.application).nextStartedService.action)
    }
    @Test fun unrelatedResultOrMissingPendingConsentCannotStartRecording() {
        assertFalse(controller.onActivityResult(5, Activity.RESULT_OK, Intent()))
        assertTrue(controller.onActivityResult(RecorderController.REQUEST_CAPTURE, Activity.RESULT_OK, Intent()))
        assertNull(shadowOf(activity.application).nextStartedService)
    }
    @Test fun indicatorTracksStateAndIsRemovedWhenActivityControllerIsDestroyed() {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val indicator = root.getChildAt(root.childCount - 1)
        assertEquals(View.GONE, indicator.visibility)
        RecordingState.update(RecordingState.Phase.RECORDING, "480p · 24 FPS")
        idle()
        assertEquals(View.VISIBLE, indicator.visibility)
        RecordingState.update(RecordingState.Phase.IDLE)
        idle()
        assertEquals(View.GONE, indicator.visibility)
        controller.destroy()
        assertNull(indicator.parent)
    }
    @Test fun deniedMicrophonePermissionNeverStartsCapture() {
        shadowOf(activity.application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        options().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        assertTrue(controller.onRequestPermissionsResult(RecorderController.REQUEST_AUDIO, intArrayOf(PackageManager.PERMISSION_DENIED)))
        assertNull(shadowOf(activity.application).nextStartedService)
    }

}
