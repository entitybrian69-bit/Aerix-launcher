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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.Tools

/** Keeps the game menu, floating REC pill, permissions, and `:recorder` service lifecycle together. */
class RecorderController(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences("aerix_recorder", 0)
    private var pending: RecordingOptions.Config? = null
    private var dialog: AlertDialog? = null
    private var startRequested = false
    private var awaitingConsentSheet = false

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
            Toast.makeText(activity, "No compatible H.264 video encoder found on this device.", Toast.LENGTH_LONG).show()
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

        TextView(activity).apply {
            text = "Isolated hardware H.264 MP4 recorder · Saves to Movies/Aerix."
            textSize = 12f
            setPadding(0, (10 * density).toInt(), 0, 0)
            body.addView(this)
        }

        dialog = AlertDialog.Builder(activity)
            .setTitle("Screen Recorder")
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Start recording") { _, _ ->
                val chosenQuality = availableQualities.getOrElse(quality.selectedItemPosition) { preferred.quality }
                val chosenFps = availableFps.getOrElse(fps.selectedItemPosition) { preferred.fps }
                val chosenAudio = audioSources.getOrElse(audio.selectedItemPosition) { RecordingOptions.AudioSource.GAME }
                val match = configs.firstOrNull { it.quality == chosenQuality && it.fps == chosenFps }
                    ?: configs.firstOrNull { it.quality == chosenQuality }
                    ?: preferred
                start(match.copy(audioSource = chosenAudio))
            }
            .show()
    }

    private fun start(config: RecordingOptions.Config) {
        prefs.edit()
            .putInt("quality", config.quality)
            .putInt("fps", config.fps)
            .putString("audio_source", config.audioSource.name)
            .apply()
        pending = config
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
                .putExtra("quality", config.quality)
                .putExtra("fps", config.fps)
                .putExtra("width", config.width)
                .putExtra("height", config.height)
                .putExtra("bitrate", config.bitrate)
                .putExtra("encoder", config.encoder)
                .putExtra("audioSource", config.audioSource.name)
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
    }
}
