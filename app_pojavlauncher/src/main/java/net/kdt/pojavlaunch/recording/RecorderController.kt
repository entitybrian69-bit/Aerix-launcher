package net.kdt.pojavlaunch.recording

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.theme.AerixLauncherTheme
import net.kdt.pojavlaunch.game.GameActivity
import java.io.File

/**
 * Coordinates the in-game recorder panel, live REC indicator pill, permissions,
 * and `:recorder` [ScreenRecordingService] lifecycle.
 */
class RecorderController @JvmOverloads constructor(
    private val activity: Activity,
    private val instanceDir: File? = null
) {
    private val prefs = activity.getSharedPreferences("aerix_recorder", 0)
    private val mainHandler = Handler(Looper.getMainLooper())

    private data class PendingStart(
        val config: RecordingOptions.Config,
        val audioSource: RecordingOptions.AudioSource,
        val withControls: Boolean
    )

    private var pending: PendingStart? = null
    private var dialog: AlertDialog? = null
    private var panelOverlay: FrameLayout? = null
    private var stateReceiver: BroadcastReceiver? = null
    private var startRequested = false
    private var awaitingConsentSheet = false
    private var controlsInRecording = prefs.getBoolean(WITH_CONTROLS, true)
    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop

    private val surfaceMessenger = Messenger(
        Handler(Looper.getMainLooper()) { msg: Message ->
            when (msg.what) {
                ScreenRecordingService.MSG_CLEAN_SURFACE -> {
                    val data = msg.data
                    data.classLoader = Surface::class.java.classLoader
                    val surface = if (Build.VERSION.SDK_INT >= 33) {
                        data.getParcelable("surface", Surface::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        data.getParcelable<Surface>("surface")
                    }
                    val width = data.getInt("width")
                    val height = data.getInt("height")
                    val fps = data.getInt("fps")
                    val attached = surface != null &&
                        (activity as? GameActivity)?.attachCleanRecorderSurface(surface, width, height, fps) == true
                    if (!attached) {
                        runCatching { surface?.release() }
                        Toast.makeText(
                            activity,
                            "Clean game capture could not attach on this device.",
                            Toast.LENGTH_LONG
                        ).show()
                        stop()
                    }
                    true
                }
                ScreenRecordingService.MSG_DETACH_SURFACE -> {
                    (activity as? GameActivity)?.detachCleanRecorderSurface()
                    true
                }
                else -> false
            }
        }
    )

    var options: List<RecordingOptions.Config> = emptyList()
        private set

    val savedQuality: Int get() = prefs.getInt("quality", RecordingOptions.DEFAULT_QUALITY)
    val savedFps: Int get() = prefs.getInt("fps", RecordingOptions.DEFAULT_FPS)
    val savedAudio: RecordingOptions.AudioSource
        get() = RecordingOptions.AudioSource.saved(prefs.getString("audio_source", null))
    val savedWithControls: Boolean get() = controlsInRecording

    @SuppressLint("ClickableViewAccessibility")
    private val indicator = TextView(activity).apply {
        val density = activity.resources.displayMetrics.density
        text = "⋮⋮ ● REC"
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
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        setOnTouchListener { v, event ->
            val container = v.parent as? View ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = v.x
                    startY = v.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (dx * dx + dy * dy) > touchSlop * touchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        val maxX = (container.width - v.width).coerceAtLeast(0).toFloat()
                        val maxY = (container.height - v.height).coerceAtLeast(0).toFloat()
                        v.x = (startX + dx).coerceIn(0f, maxX)
                        v.y = (startY + dy).coerceIn(0f, maxY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val maxX = (container.width - v.width).coerceAtLeast(1).toFloat()
                        val maxY = (container.height - v.height).coerceAtLeast(1).toFloat()
                        prefs.edit()
                            .putFloat(INDICATOR_NX, (v.x / maxX).coerceIn(0f, 1f))
                            .putFloat(INDICATOR_NY, (v.y / maxY).coerceIn(0f, 1f))
                            .apply()
                    } else {
                        stop()
                    }
                    dragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private val parentLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (indicator.visibility == View.VISIBLE) {
            applySavedIndicatorPosition()
        }
    }

    private val timerTick = object : Runnable {
        override fun run() {
            if (RecordingState.phase != RecordingState.Phase.RECORDING) return
            updateIndicatorText()
            mainHandler.postDelayed(this, 1000L)
        }
    }

    private val stopWatchdog = Runnable {
        if (RecordingState.isActive() || startRequested) {
            startRequested = false
            RecordingState.update(RecordingState.Phase.IDLE, "")
        }
    }

    private val stateListener: () -> Unit = {
        mainHandler.removeCallbacks(timerTick)
        val shouldShow = RecordingState.isActive()
        indicator.visibility = if (shouldShow) View.VISIBLE else View.GONE
        updateIndicatorText()
        if (shouldShow) {
            indicator.post { applySavedIndicatorPosition() }
        }
        if (RecordingState.phase == RecordingState.Phase.RECORDING) {
            mainHandler.removeCallbacks(stopWatchdog)
            startRequested = false
            mainHandler.postDelayed(timerTick, 1000L)
        } else {
            if (RecordingState.phase == RecordingState.Phase.STOPPING || !RecordingState.isActive()) {
                (activity as? GameActivity)?.detachCleanRecorderSurface()
            }
            if (!RecordingState.isActive()) {
                mainHandler.removeCallbacks(stopWatchdog)
                startRequested = false
            }
        }
    }

    init {
        stateReceiver = RecordingState.register(activity)
        val density = activity.resources.displayMetrics.density
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = (46 * density).toInt()
            marginEnd = (12 * density).toInt()
        }
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        contentRoot?.addView(indicator, params)
        contentRoot?.addOnLayoutChangeListener(parentLayoutListener)
        RecordingState.addListener(stateListener)
    }

    fun isPanelOpen(): Boolean = panelOverlay != null

    fun show() {
        dialog?.dismiss()
        if (instanceDir == null || !instanceDir.isDirectory) {
            Toast.makeText(activity, "The running instance directory is unavailable.", Toast.LENGTH_LONG).show()
            return
        }
        if (RecordingState.isActive() || startRequested) {
            openPanel(options)
            if (options.isEmpty()) {
                val screen = screenSize()
                PojavApplication.sExecutorService.execute {
                    val configs = runCatching { RecordingOptions.supported(screen.first, screen.second) }
                        .getOrDefault(emptyList())
                    if (configs.isNotEmpty()) {
                        Tools.runOnUiThread { options = configs }
                    }
                }
            }
            return
        }
        if (options.isNotEmpty()) {
            openPanel(options)
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

    internal fun showOptions(configs: List<RecordingOptions.Config>) {
        if (configs.isEmpty()) {
            dialog = AlertDialog.Builder(activity)
                .setTitle("Screen Recorder")
                .setMessage("No compatible H.264 video encoder was found on this device.")
                .setPositiveButton("Recordings") { _, _ -> showRecordings() }
                .setNegativeButton("Close", null)
                .show()
            return
        }
        options = configs
        openPanel(configs)
    }

    private fun openPanel(configs: List<RecordingOptions.Config>) {
        dismissPanel()
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        (activity as? GameActivity)?.onRecorderPanelVisibilityChanged(true)
        val compose = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                AerixLauncherTheme {
                    RecorderPanel(
                        configs = configs,
                        initialQuality = savedQuality,
                        initialFps = savedFps,
                        initialAudio = savedAudio,
                        initialWithControls = savedWithControls,
                        onStart = { config, audio, withControls -> startWithChoice(config, audio, withControls) },
                        onStop = { stop() },
                        onToggleControlsDuringRecording = { enabled -> setControlsDuringRecording(enabled) },
                        onRecordings = { showRecordingsList() },
                        onDismiss = { dismissPanel() }
                    )
                }
            }
        }
        val host = FrameLayout(activity).apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            addView(
                compose,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        root.addView(
            host,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        host.requestFocus()
        panelOverlay = host
        RecorderUiState.show.value = true
    }

    fun dismissPanel() {
        RecorderUiState.show.value = false
        val host = panelOverlay ?: return
        panelOverlay = null
        (host.parent as? ViewGroup)?.removeView(host)
        (activity as? GameActivity)?.onRecorderPanelVisibilityChanged(false)
    }

    fun startWithChoice(
        config: RecordingOptions.Config,
        audio: RecordingOptions.AudioSource,
        withControls: Boolean
    ) {
        dismissPanel()
        start(config, audio, withControls)
    }

    fun showRecordingsList() {
        dismissPanel()
        showRecordings()
    }

    private fun showRecordings() {
        val root = instanceDir ?: return
        val directory = runCatching { RecordingPaths.directory(root, false) }.getOrElse {
            Toast.makeText(activity, "Cannot open this instance's recordings folder.", Toast.LENGTH_LONG).show()
            return
        }
        PojavApplication.sExecutorService.execute {
            val strays = runCatching { RecordingOutput.recoverStrays(activity, directory) }.getOrDefault(emptyList())
            val files = directory.listFiles()
                ?.filter { it.isFile && it.extension.equals("mp4", true) }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            Tools.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (files.isEmpty()) {
                    val unfinished = strays.count { !it.playable }
                    val msg = when {
                        strays.isNotEmpty() && unfinished == 0 ->
                            "Recovered ${strays.size} recording(s) into this instance's MP4 list."
                        unfinished > 0 ->
                            "$unfinished interrupted recording(s) kept as -unfinished.mp4."
                        else -> "No recordings in this instance yet."
                    }
                    dialog = AlertDialog.Builder(activity)
                        .setTitle("Instance recordings")
                        .setMessage(msg)
                        .setPositiveButton("Back to Recorder") { _, _ -> show() }
                        .setNegativeButton("Close", null)
                        .show()
                    return@runOnUiThread
                }
                dialog = AlertDialog.Builder(activity)
                    .setTitle("Instance recordings")
                    .setItems(files.map { it.name }.toTypedArray()) { _, which ->
                        val file = files.getOrNull(which) ?: return@setItems
                        dialog = AlertDialog.Builder(activity)
                            .setTitle(file.name)
                            .setItems(arrayOf("Open video", "Share video", "Delete video")) { _, action ->
                                if (action == 2) {
                                    if (file.delete()) {
                                        Toast.makeText(activity, "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
                                    }
                                    showRecordings()
                                } else {
                                    RecordingLibrary.view(activity, file, action == 1)
                                }
                            }
                            .setNegativeButton("Back") { _, _ -> showRecordings() }
                            .show()
                    }
                    .setNeutralButton("Back to Recorder") { _, _ -> show() }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }

    private fun start(
        config: RecordingOptions.Config,
        audio: RecordingOptions.AudioSource = savedAudio,
        withControls: Boolean = true
    ) {
        controlsInRecording = withControls
        prefs.edit()
            .putInt("quality", config.quality)
            .putInt("fps", config.fps)
            .putString("audio_source", audio.name)
            .putBoolean(WITH_CONTROLS, withControls)
            .apply()

        pending = PendingStart(config, audio, withControls)
        val needsAudioPerm = audio != RecordingOptions.AudioSource.NONE
        if (needsAudioPerm &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            awaitingConsentSheet = true
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        proceedAfterAudioPermission()
    }

    private fun proceedAfterAudioPermission() {
        val current = pending ?: run {
            awaitingConsentSheet = false
            return
        }
        if (current.withControls || current.audioSource.game) {
            requestCaptureConsent()
        } else {
            pending = null
            awaitingConsentSheet = false
            launchService(current, Activity.RESULT_CANCELED, null)
        }
    }

    private fun requestCaptureConsent() {
        val manager = activity.getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            pending = null
            awaitingConsentSheet = false
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

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != REQUEST_AUDIO) return false
        val current = pending ?: run {
            awaitingConsentSheet = false
            return true
        }
        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            pending = current.copy(audioSource = RecordingOptions.AudioSource.NONE)
            Toast.makeText(activity, "Audio permission skipped — recording video only.", Toast.LENGTH_SHORT).show()
        }
        proceedAfterAudioPermission()
        return true
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CAPTURE) return false
        val current = pending
        pending = null
        awaitingConsentSheet = false
        if (resultCode != Activity.RESULT_OK || data == null || current == null) {
            Toast.makeText(activity, "Screen recording cancelled.", Toast.LENGTH_SHORT).show()
            return true
        }
        launchService(current, resultCode, data)
        return true
    }

    private fun launchService(current: PendingStart, resultCode: Int, consentData: Intent?) {
        startRequested = true
        val intent = Intent(activity, ScreenRecordingService::class.java)
            .setAction(ScreenRecordingService.START)
            .putExtra("resultCode", resultCode)
            .putExtra("instanceRoot", instanceDir?.absolutePath)
            .putExtra("quality", current.config.quality)
            .putExtra("fps", current.config.fps)
            .putExtra("width", current.config.width)
            .putExtra("height", current.config.height)
            .putExtra("bitrate", current.config.bitrate)
            .putExtra("encoder", current.config.encoder)
            .putExtra("audio_source", current.audioSource.name)
            .putExtra(ScreenRecordingService.EXTRA_WITH_CONTROLS, current.withControls)
            .putExtra(ScreenRecordingService.EXTRA_SURFACE_MESSENGER, surfaceMessenger)
        if (consentData != null) {
            intent.putExtra("consent", Intent(consentData))
        }
        runCatching {
            ContextCompat.startForegroundService(activity, intent)
        }.onFailure { err ->
            startRequested = false
            Toast.makeText(activity, err.message ?: "Cannot start screen recorder", Toast.LENGTH_LONG).show()
        }
    }

    fun stop() {
        startRequested = false
        awaitingConsentSheet = false
        (activity as? GameActivity)?.detachCleanRecorderSurface()
        mainHandler.removeCallbacks(stopWatchdog)
        mainHandler.postDelayed(stopWatchdog, 4_500L)
        val stopIntent = Intent(activity, ScreenRecordingService::class.java)
            .setAction(ScreenRecordingService.STOP)
        val delivered = runCatching {
            ContextCompat.startForegroundService(activity, stopIntent)
        }.isSuccess
        if (!delivered) {
            mainHandler.removeCallbacks(stopWatchdog)
            RecordingState.update(RecordingState.Phase.IDLE, "")
        }
    }

    fun setControlsDuringRecording(visible: Boolean) {
        controlsInRecording = visible
        prefs.edit().putBoolean(WITH_CONTROLS, visible).apply()
        if (RecordingState.isActive()) {
            Toast.makeText(
                activity,
                "Saved — applies to your next recording.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun onControlsRequestedByPlayer() {
        // Touch controls stay visible on screen in every recording mode.
    }

    fun onGameStopped() {
        // Never cancel a pending recording while the system permission/capture sheet is open.
        if (awaitingConsentSheet) return
        if (!activity.isChangingConfigurations && (startRequested || RecordingState.isActive())) {
            stop()
        }
    }

    fun destroy() {
        mainHandler.removeCallbacks(timerTick)
        mainHandler.removeCallbacks(stopWatchdog)
        dialog?.dismiss()
        dialog = null
        dismissPanel()
        (activity as? GameActivity)?.detachCleanRecorderSurface()
        RecordingState.removeListener(stateListener)
        (indicator.parent as? ViewGroup)?.let { parent ->
            parent.removeOnLayoutChangeListener(parentLayoutListener)
            parent.removeView(indicator)
        }
        stateReceiver?.let { receiver ->
            runCatching { activity.unregisterReceiver(receiver) }
        }
        stateReceiver = null
        pending = null
        awaitingConsentSheet = false
        if (startRequested || RecordingState.isActive()) stop()
    }

    private fun applySavedIndicatorPosition() {
        val parent = indicator.parent as? View ?: return
        if (parent.width <= 0 || parent.height <= 0 || indicator.width <= 0 || indicator.height <= 0) return
        val maxX = (parent.width - indicator.width).coerceAtLeast(0).toFloat()
        val maxY = (parent.height - indicator.height).coerceAtLeast(0).toFloat()
        if (prefs.contains(INDICATOR_NX) && prefs.contains(INDICATOR_NY)) {
            val nx = prefs.getFloat(INDICATOR_NX, 0.9f).coerceIn(0f, 1f)
            val ny = prefs.getFloat(INDICATOR_NY, 0.1f).coerceIn(0f, 1f)
            indicator.x = (nx * maxX).coerceIn(0f, maxX)
            indicator.y = (ny * maxY).coerceIn(0f, maxY)
        } else {
            indicator.x = indicator.x.coerceIn(0f, maxX)
            indicator.y = indicator.y.coerceIn(0f, maxY)
        }
    }

    private fun updateIndicatorText() {
        indicator.text = when (RecordingState.phase) {
            RecordingState.Phase.RECORDING -> {
                val startedAt = RecordingState.startedAtElapsedMs
                if (startedAt > 0L) {
                    val secs = ((SystemClock.elapsedRealtime() - startedAt) / 1000L).coerceAtLeast(0L)
                    "⋮⋮ ● REC %02d:%02d · Stop".format(secs / 60L, secs % 60L)
                } else {
                    "⋮⋮ ● REC · Stop"
                }
            }
            RecordingState.Phase.STARTING -> "⋮⋮ ● Starting…"
            RecordingState.Phase.STOPPING -> "⋮⋮ ● Saving…"
            RecordingState.Phase.IDLE -> "⋮⋮ ● REC"
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val bounds = if (Build.VERSION.SDK_INT >= 30) activity.windowManager.currentWindowMetrics.bounds else null
        val dm = activity.resources.displayMetrics
        val width = bounds?.width() ?: dm.widthPixels
        val height = bounds?.height() ?: dm.heightPixels
        return maxOf(width, height) to minOf(width, height)
    }

    companion object {
        const val REQUEST_AUDIO = 0x52C1
        const val REQUEST_CAPTURE = 0x52C2
        private const val WITH_CONTROLS = "with_controls"
        private const val INDICATOR_NX = "indicator_nx"
        private const val INDICATOR_NY = "indicator_ny"
    }
}
