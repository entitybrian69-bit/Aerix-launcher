package net.kdt.pojavlaunch.aerix.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.kdt.pojavlaunch.R
import kotlin.math.ceil

/** Shared intro playback flag so popups, tours, and video wallpapers wait until the intro finishes. */
object AerixIntroState {
    var active by mutableStateOf(false)
        internal set
}

/**
 * Full-bleed hardware-accelerated intro splash player (`R.raw.aerix_intro`).
 *
 * Uses a `SurfaceView` behind `mainContent` (while `mainContent.alpha == 0f`) so the hardware
 * video decoder renders 1080p60 frames directly through Hardware Composer with zero GPU texture
 * copies while Jetpack Compose warms up underneath. Once playback finishes, `mainContent` fades
 * smoothly from 0 -> 1 over the final video frame before releasing the player.
 */
class AerixIntroController(
    private val activity: Activity,
    private val mainContent: View,
    private val onFinished: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
    private val introContainer: FrameLayout
    private val surfaceView: SurfaceView
    private val coverView: View
    private val touchBlocker: View

    private var mediaPlayer: MediaPlayer? = null
    private var videoWidth = 1920
    private var videoHeight = 1080
    private var finishing = false
    private var cleanedUp = false

    private val watchdogRunnable = Runnable { finishIntro(animate = true) }
    private val revealCoverFallback = Runnable { revealVideoSurface() }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (finishing || cleanedUp) return
            startPlayback(holder)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            applyCenterCropLayout()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            releasePlayer()
        }
    }

    init {
        AerixIntroState.active = true
        activity.window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        mainContent.alpha = 0f

        introContainer = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = true
            clipToPadding = true
        }

        surfaceView = SurfaceView(activity)
        coverView = View(activity).apply {
            setBackgroundColor(Color.BLACK)
        }
        touchBlocker = View(activity).apply {
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, _ -> true }
        }

        introContainer.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        introContainer.addView(
            coverView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        introContainer.addOnLayoutChangeListener { _, left, top, right, bottom, oldL, oldT, oldR, oldB ->
            if ((right - left) != (oldR - oldL) || (bottom - top) != (oldB - oldT)) {
                applyCenterCropLayout()
            }
        }

        contentRoot.addView(
            introContainer,
            0,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        contentRoot.addView(
            touchBlocker,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        surfaceView.holder.addCallback(surfaceCallback)
        // Safety deadline in case surface creation or prepareAsync stalls on an unusual device.
        handler.postDelayed(watchdogRunnable, STARTUP_WATCHDOG_MS)
    }

    private fun startPlayback(holder: SurfaceHolder) {
        releasePlayer()
        val player = MediaPlayer()
        mediaPlayer = player
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            val afd = activity.resources.openRawResourceFd(R.raw.aerix_intro)
                ?: error("Missing intro raw resource")
            afd.use { fd ->
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            player.setDisplay(holder)
            player.isLooping = false
            player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            player.setOnVideoSizeChangedListener { _, width, height ->
                if (width > 0 && height > 0) {
                    videoWidth = width
                    videoHeight = height
                    applyCenterCropLayout()
                }
            }
            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    handler.removeCallbacks(revealCoverFallback)
                    revealVideoSurface()
                }
                false
            }
            player.setOnPreparedListener { mp ->
                if (finishing || cleanedUp) return@setOnPreparedListener
                if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                    videoWidth = mp.videoWidth
                    videoHeight = mp.videoHeight
                    applyCenterCropLayout()
                }
                val durationMs = mp.duration.takeIf { it in 1000..20_000 } ?: DEFAULT_DURATION_MS
                handler.removeCallbacks(watchdogRunnable)
                handler.postDelayed(watchdogRunnable, durationMs + 1_200L)
                mp.start()
                // Reveal after a short beat even on drivers that omit MEDIA_INFO_VIDEO_RENDERING_START.
                handler.postDelayed(revealCoverFallback, 220L)
            }
            player.setOnCompletionListener {
                finishIntro(animate = true)
            }
            player.setOnErrorListener { _, _, _ ->
                finishIntro(animate = false)
                true
            }
            player.prepareAsync()
        }.onFailure {
            finishIntro(animate = false)
        }
    }

    private fun revealVideoSurface() {
        if (cleanedUp || coverView.visibility == View.GONE) return
        coverView.animate().cancel()
        coverView.visibility = View.GONE
    }

    private fun applyCenterCropLayout() {
        val cw = introContainer.width
        val ch = introContainer.height
        val vw = videoWidth
        val vh = videoHeight
        if (cw <= 0 || ch <= 0 || vw <= 0 || vh <= 0) return
        val scale = maxOf(cw.toFloat() / vw.toFloat(), ch.toFloat() / vh.toFloat())
        val targetW = ceil(vw * scale).toInt().coerceAtLeast(cw)
        val targetH = ceil(vh * scale).toInt().coerceAtLeast(ch)
        val lp = surfaceView.layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.width != targetW || lp.height != targetH) {
            lp.width = targetW
            lp.height = targetH
            lp.gravity = Gravity.CENTER
            surfaceView.layoutParams = lp
        }
    }

    fun onPause() {
        finishIntro(animate = false)
    }

    fun onDestroy() {
        finishIntro(animate = false)
    }

    private fun finishIntro(animate: Boolean) {
        if (cleanedUp) return
        if (finishing) {
            if (!animate) cleanup()
            return
        }
        finishing = true
        handler.removeCallbacks(watchdogRunnable)
        handler.removeCallbacks(revealCoverFallback)
        (touchBlocker.parent as? ViewGroup)?.removeView(touchBlocker)

        if (!animate || activity.isFinishing || activity.isDestroyed) {
            mainContent.animate().cancel()
            mainContent.alpha = 1f
            cleanup()
            return
        }

        // Hold the final frame during the crossfade so the home screen reveals smoothly.
        runCatching {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.pause()
            }
        }
        mainContent.animate()
            .alpha(1f)
            .setDuration(FADE_OUT_MS)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { cleanup() }
            .start()
    }

    private fun releasePlayer() {
        val mp = mediaPlayer ?: return
        mediaPlayer = null
        runCatching {
            mp.setOnPreparedListener(null)
            mp.setOnCompletionListener(null)
            mp.setOnErrorListener(null)
            mp.setOnInfoListener(null)
            mp.setOnVideoSizeChangedListener(null)
            if (mp.isPlaying) mp.stop()
            mp.setDisplay(null)
            mp.release()
        }
    }

    private fun cleanup() {
        if (cleanedUp) return
        cleanedUp = true
        finishing = true
        handler.removeCallbacks(watchdogRunnable)
        handler.removeCallbacks(revealCoverFallback)
        surfaceView.holder.removeCallback(surfaceCallback)
        releasePlayer()
        (touchBlocker.parent as? ViewGroup)?.removeView(touchBlocker)
        (introContainer.parent as? ViewGroup)?.removeView(introContainer)
        mainContent.alpha = 1f
        activity.window.setBackgroundDrawable(null)
        AerixIntroState.active = false
        onFinished()
    }

    companion object {
        private const val DEFAULT_DURATION_MS = 5_000
        private const val STARTUP_WATCHDOG_MS = 4_000L
        private const val FADE_OUT_MS = 260L
    }
}
