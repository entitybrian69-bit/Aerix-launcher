package net.kdt.pojavlaunch.recording

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/** Activity-owned permission/settings UI; no capture without a fresh system consent result. */
class RecorderController(private val activity: Activity) {
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs = activity.getSharedPreferences("aerix_recorder", 0)
    private var dialog: AlertDialog? = null
    private var pending: RecordingOptions.Config? = null
    private var disposed = false
    private var startRequested = false
    private val indicator = TextView(activity).apply {
        text = "● REC · Stop"
        setTextColor(Color.WHITE)
        setBackgroundColor(0xCC8B1820.toInt())
        val padding = (8 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding / 2, padding, padding / 2)
        contentDescription = "Recording in progress. Tap to stop and save."
        visibility = View.GONE
        setOnClickListener { show() }
    }
    private val stateListener = Runnable {
        indicator.visibility = if (RecordingState.isActive()) View.VISIBLE else View.GONE
        indicator.text = when (RecordingState.phase) {
            RecordingState.Phase.RECORDING -> "● REC · Stop"
            RecordingState.Phase.STARTING -> "● Starting…"
            RecordingState.Phase.STOPPING -> "● Saving…"
            else -> ""
        }
        if (!RecordingState.isActive()) startRequested = false
    }
    init {
        activity.findViewById<FrameLayout>(android.R.id.content).addView(indicator,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = (8 * activity.resources.displayMetrics.density).toInt()
                rightMargin = topMargin
            })
        RecordingState.addListener(stateListener)
    }

    fun show() {
        if (Build.VERSION.SDK_INT < 29) {
            toast("Screen recording with audio requires Android 10 or newer")
            return
        }
        if (disposed || dialog?.isShowing == true) return
        if (RecordingState.isActive() || startRequested) {
            dialog = AlertDialog.Builder(activity).setTitle("Screen recorder")
                .setMessage("${RecordingState.detail}\nStop recording and save the MP4?")
                .setPositiveButton("Stop & save") { _, _ -> stop() }
                .setNegativeButton("Keep recording", null).show()
            return
        }
        if (pending != null) { toast("Finish the Android screen-capture permission prompt first"); return }
        dialog = AlertDialog.Builder(activity).setTitle("Performance Recorder")
            .setMessage("Checking hardware encoder support…").setNegativeButton("Cancel", null).show()
        val loading = dialog
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getRealMetrics(metrics)
        executor.execute {
            val result = runCatching { RecordingOptions.supported(metrics.widthPixels, metrics.heightPixels) }
            activity.runOnUiThread {
                if (disposed || activity.isFinishing || activity.isDestroyed || loading?.isShowing != true) return@runOnUiThread
                loading.dismiss()
                result.onSuccess { showOptions(it) }.onFailure { toast("Cannot query recording encoders: ${it.message}") }
            }
        }
    }

    internal fun showOptions(configs: List<RecordingOptions.Config>) {
        if (configs.isEmpty()) { toast("No supported hardware H.264 recording combinations on this device"); return }
        val density = activity.resources.displayMetrics.density
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * density).toInt(); setPadding(p, p / 2, p, p / 2)
        }
        fun label(text: String) = TextView(activity).apply { this.text = text; body.addView(this) }
        label("Resolution (short edge)")
        val quality = Spinner(activity).also { body.addView(it) }
        quality.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
            RecordingOptions.qualities.map { "${it}p" })
        label("Frame rate target")
        val fps = Spinner(activity).also { body.addView(it) }
        // Every requested value is shown; unsupported choices cannot enable Start.
        fps.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
            RecordingOptions.frameRates.map { "$it FPS" })
        label("Audio source")
        val audioSource = Spinner(activity).also { body.addView(it) }
        audioSource.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
            RecordingOptions.AudioSource.entries.map { it.label })
        audioSource.setSelection(RecordingOptions.AudioSource.saved(prefs.getString("audio_source", null)).ordinal)
        val info = label("")
        val saveLocation = if (Build.VERSION.SDK_INT >= 29) "Movies/Aerix" else "this app's Movies/Aerix folder (removed on uninstall)"
        label("Choose game audio, microphone, or both. Android audio permission is required for every mode, including internal game audio. Game-only never opens the microphone. For both, use headphones to avoid echo. If game sound is missing, check Minecraft volume; device or audio-backend restrictions can block internal capture. If game-only stays silent, try Settings → Game → Forcefully use OpenSL for audio, then restart Minecraft. This can help on devices with Oboe capture issues.\nSaves to $saveLocation.\nHigher settings add load and heat. FPS is a target, not a guarantee; 120 FPS cannot add frames the game/capture never produces.\nAndroid may capture notifications or other content: choose app-only capture if offered. Recording stops when you leave the game.")
        val preferred = configs.firstOrNull { it.quality == prefs.getInt("quality", 480) && it.fps == prefs.getInt("fps", 24) }
            ?: configs.firstOrNull { it.quality == 480 && it.fps == 24 } ?: configs.first()
        quality.setSelection(RecordingOptions.qualities.indexOf(preferred.quality))
        fps.setSelection(RecordingOptions.frameRates.indexOf(preferred.fps))
        fun selected() = configs.firstOrNull {
            it.quality == RecordingOptions.qualities[quality.selectedItemPosition] &&
                it.fps == RecordingOptions.frameRates[fps.selectedItemPosition]
        }
        val created = AlertDialog.Builder(activity).setTitle("Performance Recorder").setView(
            android.widget.ScrollView(activity).apply { addView(body) })
            .setPositiveButton("Start recording", null).setNegativeButton("Cancel", null).create()
        dialog = created
        created.setOnShowListener {
            fun refresh() {
                val option = selected()
                info.text = option?.let { "${it.width} × ${it.height} · ${it.bitrate / 1_000_000.0} Mbps\nHardware encoder: ${it.encoder}" }
                    ?: "Unsupported resolution/FPS combination — choose another setting."
                created.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = option != null
            }
            val listener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = refresh()
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = refresh()
            }
            quality.onItemSelectedListener = listener; fps.onItemSelectedListener = listener
            refresh()
            created.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val option = selected() ?: return@setOnClickListener
                val source = RecordingOptions.AudioSource.entries[audioSource.selectedItemPosition]
                prefs.edit().putInt("quality", option.quality).putInt("fps", option.fps)
                    .putString("audio_source", source.name).apply()
                pending = option.copy(audioSource = source)
                created.dismiss()
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
                } else requestCaptureConsent()

            }
        }
        created.show()
    }

    private fun requestCaptureConsent() {
        try {
            val manager = activity.getSystemService(MediaProjectionManager::class.java)
            activity.startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
        } catch (error: Exception) { pending = null; toast("Cannot request screen capture: ${error.message}") }
    }

    fun onRequestPermissionsResult(requestCode: Int, grants: IntArray): Boolean {
        if (requestCode != REQUEST_AUDIO) return false
        if (!disposed && pending != null && grants.isNotEmpty() && grants[0] == PackageManager.PERMISSION_GRANTED) requestCaptureConsent()
        else { pending = null; if (!disposed) toast("Audio recording permission is required; recording cancelled") }
        return true
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CAPTURE) return false
        val option = pending
        pending = null
        if (resultCode != Activity.RESULT_OK || data == null || option == null || disposed) {
            if (!disposed) toast("Screen recording cancelled")
            return true
        }
        val intent = Intent(activity, ScreenRecordingService::class.java).setAction(ScreenRecordingService.START)
            .putExtra("consent", data).putExtra("quality", option.quality).putExtra("fps", option.fps)
            .putExtra("width", option.width).putExtra("height", option.height).putExtra("bitrate", option.bitrate)
            .putExtra("encoder", option.encoder).putExtra("audio_source", option.audioSource.name)
        try { startRequested = true; ContextCompat.startForegroundService(activity, intent) }
        catch (error: Exception) { startRequested = false; toast("Cannot start recorder: ${error.message}") }
        return true
    }

    fun stop() {
        if (startRequested || RecordingState.isActive()) {
            runCatching { activity.startService(Intent(activity, ScreenRecordingService::class.java).setAction(ScreenRecordingService.STOP)) }
                .onFailure { toast("Could not request stop; use Android's recording notification to stop") }
            startRequested = false
        }
    }
    fun onGameStopped() { stop() }
    fun destroy() {
        disposed = true
        dialog?.dismiss()
        RecordingState.removeListener(stateListener)
        (indicator.parent as? android.view.ViewGroup)?.removeView(indicator)
        executor.shutdownNow()
    }
    private fun toast(message: String) { Toast.makeText(activity, message, Toast.LENGTH_LONG).show() }
    companion object { const val REQUEST_CAPTURE = 7143; const val REQUEST_AUDIO = 7144 }
}
