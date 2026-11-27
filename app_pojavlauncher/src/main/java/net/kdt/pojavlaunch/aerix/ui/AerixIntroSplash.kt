package net.kdt.pojavlaunch.aerix.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.RawRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.kdt.pojavlaunch.R

/** Shared intro playback flag so popups, tours, and video wallpapers wait until the intro finishes. */
object AerixIntroState {
    var active by mutableStateOf(false)
        internal set
}

/**
 * Full-bleed hardware-accelerated intro splash player (`R.raw.aerix_intro`, with automatic
 * 720p30 Constrained-Baseline fallback `R.raw.aerix_intro_compat` for low-end devices).
 *
 * Uses a `TextureView` overlay above `mainContent` rather than a behind-window `SurfaceView` so
 * low-end GPUs and OEM compositors never exhibit `SurfaceView` hole-punch black screens, and
 * probes `MediaCodecList` + RAM to pick the 720p30 compat stream automatically on devices that lack
 * hardware 1080p60 H.264 decoding (or falls back dynamically if the primary stream stalls/errors).
 */
class AerixIntroController(
    private val activity: Activity,
    private val mainContent: View,
    private val onFinished: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
    private val introContainer: FrameLayout
    private val textureView: TextureView
    private val coverView: View
    private val touchBlocker: View

    private var mediaPlayer: MediaPlayer? = null
    private var videoSurface: Surface? = null
    private var videoWidth = 1920
    private var videoHeight = 1080
    private var activeResId: Int = if (shouldPreferCompatIntro(activity)) R.raw.aerix_intro_compat else R.raw.aerix_intro
    private var fallbackAttempted = (activeResId == R.raw.aerix_intro_compat)
    private var firstFrameRendered = false
    private var finishing = false
    private var cleanedUp = false

    private val watchdogRunnable = Runnable { finishIntro(animate = true) }
    private val frameStallRunnable = Runnable {
        if (cleanedUp || finishing || firstFrameRendered) return@Runnable
        val st = textureView.surfaceTexture
        if (!fallbackAttempted && st != null && activeResId != R.raw.aerix_intro_compat) {
            fallbackToCompat(st)
        } else {
            finishIntro(animate = true)
        }
    }
    private val revealCoverFallback = Runnable {
        if (!firstFrameRendered && mediaPlayer?.isPlaying == true) {
            revealVideoSurface()
        }
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            if (finishing || cleanedUp) return
            applyCenterCropTransform()
            startPlayback(surfaceTexture, activeResId)
        }

        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            applyCenterCropTransform()
        }

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
            releasePlayer()
            return true
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
            if (!firstFrameRendered) {
                firstFrameRendered = true
                handler.removeCallbacks(frameStallRunnable)
                handler.removeCallbacks(revealCoverFallback)
                revealVideoSurface()
            }
        }
    }

    init {
        AerixIntroState.active = true
        activity.window.setBackgroundDrawable(ColorDrawable(Color.BLACK))

        introContainer = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = true
            clipToPadding = true
        }

        textureView = TextureView(activity)
        coverView = View(activity).apply {
            setBackgroundColor(Color.BLACK)
        }
        touchBlocker = View(activity).apply {
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, _ -> true }
        }

        introContainer.addView(
            textureView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
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
                applyCenterCropTransform()
            }
        }

        contentRoot.addView(
            introContainer,
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

        textureView.surfaceTextureListener = surfaceTextureListener
        if (textureView.isAvailable) {
            textureView.surfaceTexture?.let { startPlayback(it, activeResId) }
        }
        // Safety deadline in case SurfaceTexture creation or prepareAsync stalls on an unusual device.
        handler.postDelayed(watchdogRunnable, STARTUP_WATCHDOG_MS)
    }

    private fun fallbackToCompat(surfaceTexture: SurfaceTexture) {
        if (cleanedUp || finishing || fallbackAttempted) {
            finishIntro(animate = false)
            return
        }
        fallbackAttempted = true
        activeResId = R.raw.aerix_intro_compat
        startPlayback(surfaceTexture, activeResId)
    }

    private fun startPlayback(surfaceTexture: SurfaceTexture, @RawRes resId: Int) {
        releasePlayer()
        firstFrameRendered = false
        handler.removeCallbacks(frameStallRunnable)
        handler.removeCallbacks(revealCoverFallback)

        val player = MediaPlayer()
        mediaPlayer = player
        val surface = Surface(surfaceTexture)
        videoSurface = surface

        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            val afd = activity.resources.openRawResourceFd(resId)
                ?: error("Missing intro raw resource")
            afd.use { fd ->
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            player.setSurface(surface)
            player.isLooping = false
            player.setOnVideoSizeChangedListener { _, width, height ->
                if (width > 0 && height > 0) {
                    videoWidth = width
                    videoHeight = height
                    applyCenterCropTransform()
                }
            }
            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    firstFrameRendered = true
                    handler.removeCallbacks(frameStallRunnable)
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
                    applyCenterCropTransform()
                }
                val durationMs = mp.duration.takeIf { it in 1000..20_000 } ?: DEFAULT_DURATION_MS
                handler.removeCallbacks(watchdogRunnable)
                handler.postDelayed(watchdogRunnable, durationMs + 1_200L)
                mp.start()
                handler.postDelayed(revealCoverFallback, 260L)
                handler.postDelayed(frameStallRunnable, FRAME_STALL_FALLBACK_MS)
            }
            player.setOnCompletionListener {
                finishIntro(animate = true)
            }
            player.setOnErrorListener { _, _, _ ->
                val st = textureView.surfaceTexture
                if (!fallbackAttempted && st != null && resId != R.raw.aerix_intro_compat) {
                    fallbackToCompat(st)
                } else {
                    finishIntro(animate = false)
                }
                true
            }
            handler.postDelayed(frameStallRunnable, FRAME_STALL_FALLBACK_MS)
            player.prepareAsync()
        }.onFailure {
            val st = textureView.surfaceTexture
            if (!fallbackAttempted && st != null && resId != R.raw.aerix_intro_compat) {
                fallbackToCompat(st)
            } else {
                finishIntro(animate = false)
            }
        }
    }

    private fun revealVideoSurface() {
        if (cleanedUp || coverView.visibility == View.GONE) return
        coverView.animate().cancel()
        coverView.visibility = View.GONE
    }

    private fun applyCenterCropTransform() {
        val cw = textureView.width.takeIf { it > 0 } ?: introContainer.width
        val ch = textureView.height.takeIf { it > 0 } ?: introContainer.height
        val vw = videoWidth
        val vh = videoHeight
        if (cw <= 0 || ch <= 0 || vw <= 0 || vh <= 0) return

        // Scale normalized TextureView coordinates equally along X/Y proportionally to aspect ratio
        // so the video fills the entire screen (`maxOf`) without letterboxing or distortion.
        val scale = maxOf(cw.toFloat() / vw.toFloat(), ch.toFloat() / vh.toFloat())
        val scaledW = vw * scale
        val scaledH = vh * scale
        val sx = scaledW / cw.toFloat()
        val sy = scaledH / ch.toFloat()
        val matrix = Matrix().apply {
            setScale(sx, sy, cw / 2f, ch / 2f)
        }
        textureView.setTransform(matrix)
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
        handler.removeCallbacks(frameStallRunnable)
        handler.removeCallbacks(revealCoverFallback)
        (touchBlocker.parent as? ViewGroup)?.removeView(touchBlocker)

        mainContent.alpha = 1f
        if (!animate || activity.isFinishing || activity.isDestroyed) {
            introContainer.animate().cancel()
            cleanup()
            return
        }

        // Hold the final frame during the crossfade so the home screen reveals smoothly.
        runCatching {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.pause()
            }
        }
        introContainer.animate()
            .alpha(0f)
            .setDuration(FADE_OUT_MS)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { cleanup() }
            .start()
    }

    private fun releasePlayer() {
        val mp = mediaPlayer
        mediaPlayer = null
        if (mp != null) {
            runCatching {
                mp.setOnPreparedListener(null)
                mp.setOnCompletionListener(null)
                mp.setOnErrorListener(null)
                mp.setOnInfoListener(null)
                mp.setOnVideoSizeChangedListener(null)
                if (mp.isPlaying) mp.stop()
                mp.setSurface(null)
                mp.release()
            }
        }
        val surface = videoSurface
        videoSurface = null
        if (surface != null) {
            runCatching { surface.release() }
        }
    }

    private fun cleanup() {
        if (cleanedUp) return
        cleanedUp = true
        finishing = true
        handler.removeCallbacks(watchdogRunnable)
        handler.removeCallbacks(frameStallRunnable)
        handler.removeCallbacks(revealCoverFallback)
        textureView.surfaceTextureListener = null
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
        private const val FRAME_STALL_FALLBACK_MS = 1_200L
        private const val FADE_OUT_MS = 260L

        internal fun shouldPreferCompatIntro(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                if (am.isLowRamDevice) return true
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                if (memInfo.totalMem in 1L..4_294_967_296L) {
                    return true
                }
            }
            return !supportsHardware1080p60Avc()
        }

        private fun supportsHardware1080p60Avc(): Boolean {
            return runCatching {
                val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                for (info in list.codecInfos) {
                    if (info.isEncoder || isSoftwareDecoder(info)) continue
                    val caps = runCatching { info.getCapabilitiesForType("video/avc") }.getOrNull() ?: continue
                    val vCaps = caps.videoCapabilities ?: continue
                    if (vCaps.areSizeAndRateSupported(1920, 1080, 60.0)) {
                        return@runCatching true
                    }
                }
                false
            }.getOrDefault(false)
        }

        private fun isSoftwareDecoder(info: MediaCodecInfo): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return info.isSoftwareOnly
            }
            val lower = info.name.lowercase()
            return lower.startsWith("omx.google.") || lower.startsWith("c2.android.") || lower.contains(".sw.")
        }
    }
}
