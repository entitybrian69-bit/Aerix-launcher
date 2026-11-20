package net.kdt.pojavlaunch.recording

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.aerix.theme.AerixLauncherTheme
import net.kdt.pojavlaunch.game.GameActivity
import java.io.File

/** Keeps the game menu, floating REC pill, permissions, and `:recorder` service lifecycle together. */
class RecorderController @JvmOverloads constructor(private val activity: Activity, private val instanceDir: File? = null) {
    private val prefs = activity.getSharedPreferences("aerix_recorder", 0)
    private var pending: RecordingOptions.Config? = null
    private var dialog: AlertDialog? = null

    /** The Compose options panel's window, while it is up. Separate from [dialog]: the stock dialog is
     *  only ever the "this device cannot encode H.264" dead end now, and one must never close the
     *  other by accident. */
    private var panelDialog: Dialog? = null
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

    /**
     * What this device can encode, kept so the Compose panel can be re-opened without asking the
     * codec list again - enumerating encoders takes long enough that doing it per open would show a
     * blank frame first.
     */
    var options: List<RecordingOptions.Config> = emptyList()
        private set

    /** Saved choices, so the panel opens on what was used last time instead of on a fixed default. */
    val savedQuality: Int get() = prefs.getInt("quality", RecordingOptions.DEFAULT_QUALITY)
    val savedFps: Int get() = prefs.getInt("fps", RecordingOptions.DEFAULT_FPS)
    val savedAudio: RecordingOptions.AudioSource
        get() = RecordingOptions.AudioSource.saved(prefs.getString("audio_source", null))
    val savedWithControls: Boolean get() = controlsInRecording

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
        // The panel draws the take it is attached to: whether one is running, and the clock the
        // recording carries. Kept here because this listener already fires on every phase change.
        RecorderUiState.active.value = RecordingState.isActive()
        RecorderUiState.detail.value = when (RecordingState.phase) {
            RecordingState.Phase.RECORDING -> RecordingState.detail.ifBlank { "● REC" }
            RecordingState.Phase.STARTING -> "STARTING"
            RecordingState.Phase.STOPPING -> "SAVING"
            RecordingState.Phase.IDLE -> ""
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
            // A take is running: the panel itself says so, shows the clock, and offers to stop. One
            // surface for the whole recorder instead of a Compose panel for starting it and a stock
            // dialog for stopping it.
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

    /**
     * The options panel, drawn in Compose over the game instead of an `AlertDialog` full of spinners.
     *
     * All the recorder logic below is the same: the panel only collects four choices and hands them
     * back through [startWithChoice]. What changed is that every option is one tap and the chosen
     * pair is resolved and shown before the take starts, rather than discovered afterwards.
     */
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
        options = configs
        openPanel(configs)
    }

    /**
     * Shows the Compose panel in its own window.
     *
     * It cannot live inside the in-game overlay: that view is GONE on anything that is not an Aerix
     * Client instance, so a player who opened the recorder from the in-game menu on a vanilla
     * instance got silence - the state said "open" and nothing was drawn. A dialog belongs to the
     * activity, not to one of its children, so it opens from wherever the recorder can be reached.
     *
     * It is also why the panel now gets back for free: a dialog with a back handler consumes the
     * key before the activity ever sees it, instead of being rescued out of `dispatchKeyEvent`.
     */
    private fun openPanel(configs: List<RecordingOptions.Config>) {
        dismissPanel()
        val context = activity as? Context ?: return
        val view = ComposeView(context).apply {
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
                        onRecordings = { showRecordingsList() },
                        onDismiss = { dismissPanel() }
                    )
                }
            }
        }
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen).apply {
            setContentView(view)
            window?.setDimAmount(0f)
            setOnDismissListener { RecorderUiState.show.value = false }
            setCanceledOnTouchOutside(false)
        }
        // A dialog owns a window of its own, and that window has none of the lifecycle, saved-state or
        // back-dispatcher owners a composition needs. Without them the ComposeView inflates, finds no
        // owner, and silently never composes - the same blank result as the bug this is fixing, from a
        // different direction. AerixLoadingWallpaper does this for its layer; this does it for the
        // dialog's decor view and the panel view inside it.
        dialog.window?.let { window ->
            ensureViewTreeOwners(window.decorView)
            view.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
        }
        // Back closes the panel, and only the panel: the game keeps running underneath.
        dialog.setOnKeyListener { _, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) &&
                event.action == KeyEvent.ACTION_UP
            ) {
                dismissPanel()
                true
            } else {
                false
            }
        }
        dialog.show()
        panelDialog = dialog
        RecorderUiState.show.value = true
    }


    /**
     * Gives [view] the owners the activity provides. A ComposeView added straight to a dialog window
     * has none of them, and a composition that cannot find a lifecycle owner never starts.
     */
    @android.annotation.SuppressLint("RestrictedApi")
    private fun ensureViewTreeOwners(view: View) {
        val owner = activity
        // Everything from the view up to the dialog's root: a composition walks up from its own
        // ComposeView, so the closest ancestor that carries the owners is what matters.
        val chain = generateSequence(view) { it.parent as? View }.toList()
        for (target in chain) {
            if (target.findViewTreeLifecycleOwner() == null && owner is LifecycleOwner) {
                target.setViewTreeLifecycleOwner(owner)
            }
            if (target.findViewTreeViewModelStoreOwner() == null && owner is ViewModelStoreOwner) {
                target.setViewTreeViewModelStoreOwner(owner)
            }
            if (target.findViewTreeSavedStateRegistryOwner() == null && owner is SavedStateRegistryOwner) {
                target.setViewTreeSavedStateRegistryOwner(owner)
            }
            if (target.findViewTreeOnBackPressedDispatcherOwner() == null &&
                owner is OnBackPressedDispatcherOwner
            ) {
                target.setViewTreeOnBackPressedDispatcherOwner(owner)
            }
        }
    }

    /** Closes the panel, if one is open, without touching the take that may be running behind it. */
    private fun dismissPanel() {
        RecorderUiState.show.value = false
        val dialog = panelDialog
        panelDialog = null
        if (dialog != null && dialog.isShowing) dialog.dismiss()
    }

    /** The choices the Compose panel hands back, resolved against what this device can encode. */
    fun startWithChoice(config: RecordingOptions.Config, audio: RecordingOptions.AudioSource, withControls: Boolean) {
        // The panel is showing the options; it has no part in the take itself.
        dismissPanel()
        controlsInRecording = withControls
        start(config.copy(audioSource = audio), withControls)
    }

    /** The Recordings list, opened from the panel. */
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
        // Whatever an earlier crash left as ".partial" is re-filed before the list is built, so a
        // recording is either in this list or explained - never a mystery archive in the folder.
        val strays = runCatching { RecordingOutput.recoverStrays(activity, directory) }.getOrDefault(emptyList())
        val files = directory.listFiles()?.filter { it.isFile && it.extension.equals("mp4", true) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        if (files.isEmpty()) {
            val unfinished = strays.count { !it.playable }
            Toast.makeText(
                activity,
                when {
                    strays.isNotEmpty() && unfinished == 0 ->
                        "Recovered ${strays.size} recording(s) into this instance's MP4 list."
                    unfinished > 0 ->
                        "$unfinished interrupted recording(s) kept as -unfinished.mp4 beside the " +
                            "real ones: the video data is there but the index never got written, so " +
                            "a player may refuse to seek it."
                    else -> "No recordings in this instance yet."
                },
                Toast.LENGTH_LONG
            ).show()
            if (files.isEmpty()) return
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
        // The panel is a window of its own now, so it has to be taken down explicitly: leaking it
        // would leave a Crimson sheet with no game behind it over whatever launches next.
        dismissPanel()
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
