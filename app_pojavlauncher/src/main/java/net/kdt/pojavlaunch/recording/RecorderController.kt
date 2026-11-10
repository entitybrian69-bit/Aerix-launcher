package net.kdt.pojavlaunch.recording

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.game.GameActivity
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import java.io.File

/** Keeps the game menu, floating REC pill, permissions, and `:recorder` service lifecycle together. */
class RecorderController @JvmOverloads constructor(private val activity: Activity, private val instanceDir: File? = null) {
    private val prefs = activity.getSharedPreferences("aerix_recorder", 0)
    private var pending: RecordingOptions.Config? = null
    private var dialog: AlertDialog? = null
    private var startRequested = false
    private var awaitingConsentSheet = false
    /**
     * Whether the current (or next) session should show the on-screen touch controls. Read back from
     * the saved choice on construction, so a rotation in the middle of a clean recording resumes with
     * the controls still faded out instead of popping them back into the video.
     */
    private var controlsInRecording = prefs.getBoolean(WITH_CONTROLS, true)
    /** Whether the control layer is faded out right now, so the fade and its notice fire once each. */
    private var ghostingControls = false
    /**
     * Whether this session is encoding the game's own frames, so the controls can stay exactly where the
     * player expects them: they are not part of that image, so there is nothing to hide.
     */
    private var captureCleanRequested = false
    private var captureGaveUp = false
    /**
     * Reads the game's frames on demand for `:recorder`. Kept for the life of the controller because the
     * encoder's input surface is useless to us once the activity is gone, and the recorder must never be
     * left producing from a dead view.
     */
    private var gameSurfaceFeed: GameSurfaceFeed? = null

    /** Created only for a take that asks for clean footage, and shut down with the activity. */
    private fun ensureGameSurfaceFeed(): GameSurfaceFeed =
        gameSurfaceFeed ?: GameSurfaceFeed(
            surfaceProvider = { (activity as? GameActivity)?.recordingSurfaceView },
            onFallback = { reason -> onCleanCaptureUnavailable(reason) }
        ).also { gameSurfaceFeed = it }

    private val indicator = TextView(activity).apply {
        val density = activity.resources.displayMetrics.density
        text = "● REC"
        textSize = 11f
        setTextColor(Color.WHITE)
        setPadding((10 * density).toInt(), (5 * density).toInt(), (10 * density).toInt(), (5 * density).toInt())
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setColor(0xDD8E1125.toInt())
            setStroke((1 * density).toInt().coerceAtLeast(1), 0xFFFF2E4C.toInt())
        }
        visibility = View.GONE
        setOnClickListener { stop() }
    }

    private val stateListener = Runnable {
        indicator.visibility = if (RecordingState.isActive()) View.VISIBLE else View.GONE
        indicator.text = when (RecordingState.phase) {
            RecordingState.Phase.RECORDING -> "● REC · Stop"
            RecordingState.Phase.STARTING -> "● Starting…"
            RecordingState.Phase.STOPPING -> "● Saving…"
            RecordingState.Phase.IDLE -> "● REC"
        }
        if (RecordingState.phase == RecordingState.Phase.RECORDING) {
            startRequested = false
        } else if (!RecordingState.isActive()) {
            startRequested = false
        }
        applyControlsToRecording()
    }

    /**
     * Fades the control layer out for the duration of a recording that asked for clean footage, and
     * back in as soon as no recording is running. Keyed off the phase rather than off [start], because
     * the consent sheet can be cancelled, revoked by the system, or outlived by this activity - and
     * every one of those lands in `IDLE`, which is the only reliable signal that nothing is being
     * captured any more. It covers `STARTING` on purpose too: the mirrored display is already being
     * fed to the encoder by then, so waiting for `RECORDING` would put the buttons in the first second
     * of a video that was supposed to be free of them.
     */
    private fun applyControlsToRecording() {
        val ghosted = !controlsInRecording && RecordingState.isActive() && !cleanCaptureActive
        if (ghosted == ghostingControls) return
        ghostingControls = ghosted
        (activity as? GameActivity)?.setControlsCapturedInRecording(!ghosted)
        if (ghosted) {
            // The buttons are still there and still pressed exactly where they always were, so this has
            // to say what happened rather than leave the player staring at a screen missing its controls.
            Toast.makeText(
                activity,
                "Controls are hidden while this recording runs, but still work. " +
                    "Tap your hide/show controls button to make them visible again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** False once the recorder has said it is mirroring the display after all, or with no session running. */
    private val cleanCaptureActive: Boolean
        get() = captureCleanRequested && !captureGaveUp && RecordingState.isActive()

    /**
     * The recorder could not use the game's frames, so the fallback the player actually asked for applies:
     * stop drawing the controls (they keep working) for the rest of this take. Said out loud, because a
     * video that quietly contains the buttons after they were asked not to is worse than an annoying toast.
     */
    private fun onCleanCaptureUnavailable(reason: String) {
        if (!captureCleanRequested || captureGaveUp) return
        captureGaveUp = true
        applyControlsToRecording()
        Toast.makeText(
            activity,
            "This device would not give up the game's frames ($reason), so the controls are hidden for " +
                "the recording instead - they still work where they were.",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * The player asked for their controls back mid-take, so honor it for the rest of the recording: a
     * clean-video option that leaves someone hunting for the jump button is not a feature, and an
     * explicit visibility tap can only mean the fade went too far. This does not change the saved
     * setting, so the next recording is clean again unless they say otherwise.
     */
    fun onControlsRequestedByPlayer() {
        if (!ghostingControls) return
        controlsInRecording = true
        applyControlsToRecording()
        Toast.makeText(activity, "Controls will be in this recording.", Toast.LENGTH_SHORT).show()
    }

    init {
        RecordingState.registerSyncReceiver(activity)
        val density = activity.resources.displayMetrics.density
        // Place ● REC pill cleanly in safe zone (below top Pojav buttons, never overlapping A or MOUSE)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = (46 * density).toInt()
            marginEnd = (12 * density).toInt()
        }
        activity.findViewById<ViewGroup>(android.R.id.content).addView(indicator, params)
        RecordingState.addListener(stateListener)
    }

    fun show() {
        dialog?.dismiss()
        if (instanceDir == null || !instanceDir.isDirectory) {
            Toast.makeText(activity, "The running instance directory is unavailable.", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(activity, "Screen recording requires Android 10 or newer.", Toast.LENGTH_LONG).show()
            return
        }
        if (RecordingState.isActive() || startRequested) {
            dialog = AlertDialog.Builder(activity)
                .setTitle("Screen Recorder")
                .setMessage("${RecordingState.detail.ifBlank { "Recording in progress" }}\nStop recording and save the MP4?")
                .setPositiveButton("Stop & Save") { _, _ -> stop() }
                .setNegativeButton("Keep recording", null)
                .show()
            return
        }
        val screen = screenSize()
        PojavApplication.sExecutorService.execute {
            val configs = runCatching { RecordingOptions.supported(screen.first, screen.second) }
                .getOrDefault(emptyList())
            Tools.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) showOptions(configs)
            }
        }
    }

    private fun showOptions(configs: List<RecordingOptions.Config>) {
        if (configs.isEmpty()) {
            dialog = AlertDialog.Builder(activity)
                .setTitle("Screen Recorder")
                .setMessage("No compatible H.264 video encoder was found on this device.")
                .setPositiveButton("Recordings") { _, _ -> showRecordings() }
                .setNegativeButton("Close", null)
                .show()
            return
        }
        val density = activity.resources.displayMetrics.density
        val pad = (18 * density).toInt()
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (10 * density).toInt(), pad, 0)
        }
        fun label(text: String) = TextView(activity).apply {
            this.text = text
            body.addView(this)
        }

        val availableQualities = configs.map { it.quality }.distinct().sorted()
        val availableFps = configs.map { it.fps }.distinct().sorted()
        val audioSources = RecordingOptions.AudioSource.values().toList()

        label("Quality")
        val quality = Spinner(activity).also { body.addView(it) }
        quality.adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            availableQualities.map { "${it}p" }
        )

        label("Frame rate")
        val fps = Spinner(activity).also { body.addView(it) }
        fps.adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            availableFps.map { "$it FPS" }
        )

        label("Audio")
        val audio = Spinner(activity).also { body.addView(it) }
        audio.adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            audioSources.map { it.label }
        )

        val savedQ = prefs.getInt("quality", RecordingOptions.DEFAULT_QUALITY)
        val savedF = prefs.getInt("fps", RecordingOptions.DEFAULT_FPS)
        val preferred = configs.firstOrNull { it.quality == savedQ && it.fps == savedF }
            ?: configs.firstOrNull { it.quality == 720 && it.fps == 30 }
            ?: configs.firstOrNull { it.quality == 480 && it.fps == 30 }
            ?: configs.first()

        quality.setSelection(availableQualities.indexOf(preferred.quality).coerceAtLeast(0))
        fps.setSelection(availableFps.indexOf(preferred.fps).coerceAtLeast(0))
        val savedAudio = RecordingOptions.AudioSource.saved(prefs.getString("audio_source", null))
        audio.setSelection(audioSources.indexOf(savedAudio).coerceAtLeast(0))

        label("Controls")
        val withControls = CheckBox(activity).apply {
            text = "Record with controls"
            isChecked = controlsInRecording
            body.addView(this)
        }
        val controlsHint = TextView(activity).apply {
            textSize = 12f
            body.addView(this)
        }
        fun describeControls(included: Boolean) {
            controlsHint.text = if (included) {
                "On: the whole screen is recorded, so the controls you see are in the video too."
            } else {
                val path = if (GameCapture.readbackIsDirect(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE)) {
                    " Your TextureView render path hands frames back directly."
                } else {
                    " Your SurfaceView render path is the one some devices refuse, and then the controls " +
                        "are hidden for the take instead."
                }
                "Off: the video is built from the game's own frames, so the buttons stay on screen, stay " +
                    "tappable, and never appear in the recording. If this device refuses to hand its " +
                    "frames back, Aerix falls back to not drawing them while the recording runs - they " +
                    "still take touches, like the transparency slider at 0%." + path
            }
        }
        // The dialog's wording follows the checkbox instead of describing whatever was saved last time,
        // because "record with controls" reads backwards if the caption never moves.
        withControls.setOnCheckedChangeListener { _, checked -> describeControls(checked) }
        describeControls(withControls.isChecked)

        TextView(activity).apply {
            text = "H.264 MP4 · Saved in this instance's recordings folder and in Movies/Aerix, so a gallery or player can open it."
            textSize = 12f
            setPadding(0, (10 * density).toInt(), 0, 0)
            body.addView(this)
        }

        dialog = AlertDialog.Builder(activity)
            .setTitle("Screen Recorder")
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Recordings") { _, _ -> showRecordings() }
            .setPositiveButton("Start recording") { _, _ ->
                val chosenQuality = availableQualities.getOrElse(quality.selectedItemPosition) { preferred.quality }
                val chosenFps = availableFps.getOrElse(fps.selectedItemPosition) { preferred.fps }
                val chosenAudio = audioSources.getOrElse(audio.selectedItemPosition) { RecordingOptions.AudioSource.GAME }
                val match = configs.firstOrNull { it.quality == chosenQuality && it.fps == chosenFps }
                    ?: configs.firstOrNull { it.quality == chosenQuality }
                    ?: preferred
                start(match.copy(audioSource = chosenAudio), withControls.isChecked)
            }
            .show()
    }


    private fun showRecordings() {
        val root = instanceDir ?: return
        val directory = runCatching { RecordingPaths.directory(root, false) }.getOrElse {
            Toast.makeText(activity, "Cannot open this instance's recordings folder.", Toast.LENGTH_LONG).show()
            return
        }
        val files = directory.listFiles()?.filter { it.isFile && it.extension.equals("mp4", true) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        if (files.isEmpty()) {
            Toast.makeText(activity, "No recordings in this instance yet.", Toast.LENGTH_LONG).show()
            return
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle("Instance recordings")
            .setItems(files.map { it.name }.toTypedArray()) { _, which ->
                val file = files.getOrNull(which) ?: return@setItems
                dialog = AlertDialog.Builder(activity).setTitle(file.name)
                    .setItems(arrayOf("Open video", "Share video")) { _, action ->
                        // Goes through the media library copy: a file inside the app's own storage is
                        // unreadable to every other app on Android 10 and later.
                        RecordingLibrary.view(activity, file, action == 1)
                    }.setNegativeButton("Cancel", null).show()
            }.setNegativeButton("Close", null).show()
    }

    private fun start(config: RecordingOptions.Config, withControls: Boolean = true) {
        controlsInRecording = withControls
        captureGaveUp = false
        // Unchecking "record with controls" is the request to capture the game's frames instead of the
        // display, which is what makes the controls stay visible while staying out of the video.
        captureCleanRequested = !withControls
        val chosen = config.copy(
            capture = if (captureCleanRequested) RecordingOptions.CaptureMode.GAME else RecordingOptions.CaptureMode.MIRROR
        )
        prefs.edit()
            .putInt("quality", config.quality)
            .putInt("fps", config.fps)
            .putString("audio_source", config.audioSource.name)
            .putBoolean(WITH_CONTROLS, withControls)
            .apply()
        // The phase callback covers the normal path; this one is for a session started while the
        // recorder is already active, where no phase change would arrive to fade the controls out.
        // With the game surface feeding the encoder there is nothing to fade, so this is a no-op.
        applyControlsToRecording()
        pending = chosen
        if (config.audioSource.hasAudio &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            awaitingConsentSheet = true
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        requestCaptureConsent()
    }

    private fun requestCaptureConsent() {
        val manager = activity.getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            Toast.makeText(activity, "Screen capture is unavailable on this device.", Toast.LENGTH_LONG).show()
            return
        }
        awaitingConsentSheet = true
        runCatching {
            activity.startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
        }.onFailure { err ->
            awaitingConsentSheet = false
            pending = null
            Toast.makeText(activity, err.message ?: "Unable to open screen capture prompt", Toast.LENGTH_LONG).show()
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQUEST_AUDIO) return
        val current = pending ?: run {
            awaitingConsentSheet = false
            return
        }
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestCaptureConsent()
        } else {
            // Gracefully degrade to Video-Only rather than blocking screen recording!
            pending = current.copy(audioSource = RecordingOptions.AudioSource.NONE)
            Toast.makeText(activity, "Audio permission skipped — recording video only.", Toast.LENGTH_SHORT).show()
            requestCaptureConsent()
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CAPTURE) return false
        val config = pending
        pending = null
        if (resultCode != Activity.RESULT_OK || data == null || config == null) {
            awaitingConsentSheet = false
            Toast.makeText(activity, "Screen recording cancelled.", Toast.LENGTH_SHORT).show()
            return true
        }
        startRequested = true
        // Wait 220ms so GameActivity finishes onStart()/onResume() after returning from Android's
        // MediaProjectionPermissionActivity (required for Android 14+ TOP-state FGS validation &
        // prevents ColorOS/OneUI transition pause from cancelling the recorder).
        val consentCopy = Intent(data)
        Tools.MAIN_HANDLER.postDelayed({
            awaitingConsentSheet = false
            if (activity.isFinishing || activity.isDestroyed) {
                startRequested = false
                return@postDelayed
            }
            val intent = Intent(activity, ScreenRecordingService::class.java)
                .setAction(ScreenRecordingService.START)
                .putExtra("consent", consentCopy)
                .putExtra("instanceRoot", instanceDir?.absolutePath)
                .putExtra("quality", config.quality)
                .putExtra("fps", config.fps)
                .putExtra("width", config.width)
                .putExtra("height", config.height)
                .putExtra("bitrate", config.bitrate)
                .putExtra("encoder", config.encoder)
                .putExtra("audioSource", config.audioSource.name)
            if (config.capture == RecordingOptions.CaptureMode.GAME) {
                // The recorder needs a way back into this process: it is the game's frames it wants, and
                // only this process can read them.
                intent.putExtra(GameCapture.EXTRA_CLEAN_VIDEO, true)
                    .putExtra(GameCapture.EXTRA_FEED, ensureGameSurfaceFeed().messenger)
            }
            runCatching {
                ContextCompat.startForegroundService(activity, intent)
            }.onFailure { err ->
                startRequested = false
                Toast.makeText(activity, err.message ?: "Cannot start screen recorder", Toast.LENGTH_LONG).show()
            }
        }, 220L)
        return true
    }

    fun stop() {
        startRequested = false
        awaitingConsentSheet = false
        runCatching {
            activity.startService(
                Intent(activity, ScreenRecordingService::class.java)
                    .setAction(ScreenRecordingService.STOP)
            )
        }
    }

    fun onGameStopped() {
        // Ignore transient stops while the Android permission / screen-capture consent sheet is open!
        if (awaitingConsentSheet) return
        if (activity.isFinishing || activity.isChangingConfigurations) {
            if (!activity.isChangingConfigurations && (startRequested || RecordingState.isActive())) {
                stop()
            }
        }
    }

    fun destroy() {
        dialog?.dismiss()
        dialog = null
        // Never leave the controls faded out on the strength of a recording this controller is about
        // to stop: the phase callback may not arrive before the activity goes away.
        ghostingControls = false
        (activity as? GameActivity)?.setControlsCapturedInRecording(true)
        // A producer left attached to the encoder's input surface would keep reading a view that is
        // going away, so this process stops feeding whether or not the recorder asked it to.
        gameSurfaceFeed?.shutdown()
        gameSurfaceFeed = null
        RecordingState.removeListener(stateListener)
        if (startRequested || RecordingState.isActive()) stop()
    }

    private fun screenSize(): Pair<Int, Int> {
        val bounds = if (Build.VERSION.SDK_INT >= 30) activity.windowManager.currentWindowMetrics.bounds else null
        val dm = activity.resources.displayMetrics
        val width = bounds?.width() ?: dm.widthPixels
        val height = bounds?.height() ?: dm.heightPixels
        return maxOf(width, height) to minOf(width, height)
    }

    companion object {
        private const val REQUEST_AUDIO = 0x52C1
        private const val REQUEST_CAPTURE = 0x52C2
        /** Session preference: are the on-screen touch controls part of the recording? */
        private const val WITH_CONTROLS = "with_controls"
    }
}
