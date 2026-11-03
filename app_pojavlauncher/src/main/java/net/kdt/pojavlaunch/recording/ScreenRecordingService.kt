package net.kdt.pojavlaunch.recording

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.core.content.ContextCompat
import net.kdt.pojavlaunch.R
import java.nio.ByteBuffer
import java.io.File

/**
 * Hardware Surface-to-H.264 + optional AAC MP4 recorder isolated in `:recorder` so `MediaCodec`
 * and `VirtualDisplay` never collide with `:game`'s OpenGL/Vulkan translation wrappers or JVM
 * signal handlers, and always satisfy Android 13–16 ForegroundService timing rules.
 */
class ScreenRecordingService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("Aerix screen recorder")
    private lateinit var worker: Handler

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var surface: Surface? = null
    private var videoEncoder: MediaCodec? = null
    private var audio: RecordingAudio? = null
    private var muxer: MediaMuxer? = null
    private var output: RecordingOutput? = null

    private var audioEnabledForSession = false
    private var videoTrack = -1
    private var audioTrack = -1
    private var pendingAudioFormat: MediaFormat? = null
    private var muxerStarted = false
    private var samples = 0
    private var videoSamples = 0
    private var videoHasKeyFrame = false
    @Volatile private var startPending = false
    @Volatile private var stopping = false
    @Volatile private var finished = false
    private var videoEnded = false
    private var audioEnded = false

    private class Pending(val track: Int, val bytes: ByteArray, val pts: Long, val flags: Int)
    private val pendingSamples = ArrayList<Pending>()
    private var pendingBytes = 0
    private lateinit var timestamps: RecordingTimestamps

    private val stopTimeout = Runnable { finish(videoSamples > 0 && muxerStarted) }
    private val audioTimeout = Runnable {
        if (!finished && audioEnabledForSession && audioTrack < 0) {
            try { dropAudio("Audio encoder produced no samples") }
            catch (error: Throwable) { finish(false, error.message ?: "Could not start video output") }
        }
    }
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (!stopping && !finished && ::worker.isInitialized && thread.isAlive) {
                worker.post { stopRecording() }
            }
        }
    }

    private fun startMuxerWhenReady() {
        val ready = videoTrack >= 0 && (!audioEnabledForSession || audioTrack >= 0)
        if (muxerStarted || !ready) return
        val activeMuxer = muxer ?: return
        activeMuxer.start()
        muxerStarted = true
        for (sample in pendingSamples) {
            val direct = ByteBuffer.allocateDirect(sample.bytes.size).apply {
                put(sample.bytes)
                flip()
            }
            val info = MediaCodec.BufferInfo().apply { set(0, sample.bytes.size, sample.pts, sample.flags) }
            activeMuxer.writeSampleData(sample.track, direct, info)
        }
        pendingSamples.clear()
        pendingBytes = 0
    }

    private fun encoded(codec: MediaCodec, index: Int, source: MediaCodec.BufferInfo, trackIndex: Int, isAudio: Boolean) {
        if (source.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0 || source.size <= 0 || trackIndex < 0) return
        if (!isAudio && !videoHasKeyFrame) {
            if (source.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME == 0) return
            videoHasKeyFrame = true
        }
        val buffer = codec.getOutputBuffer(index) ?: return
        buffer.position(source.offset)
        buffer.limit(source.offset + source.size)
        val pts = if (isAudio) timestamps.audio(source.presentationTimeUs) else timestamps.video(source.presentationTimeUs)
        if (muxerStarted) {
            val info = MediaCodec.BufferInfo().apply { set(source.offset, source.size, pts, source.flags) }
            muxer?.writeSampleData(trackIndex, buffer, info)
        } else {
            if (pendingBytes + source.size > 6 * 1024 * 1024) {
                // If audio never started emitting format, drop back to video-only muxing immediately
                if (audioEnabledForSession && audioTrack < 0 && videoTrack >= 0) {
                    dropAudio("Audio did not start in time")
                    if (finished) return
                    buffer.position(source.offset)
                    buffer.limit(source.offset + source.size)
                    val info = MediaCodec.BufferInfo().apply { set(source.offset, source.size, pts, source.flags) }
                    muxer?.writeSampleData(trackIndex, buffer, info)
                } else {
                    return
                }
            } else {
                val bytes = ByteArray(source.size)
                buffer.get(bytes)
                pendingSamples.add(Pending(trackIndex, bytes, pts, source.flags))
                pendingBytes += bytes.size
            }
        }
        samples++
        if (!isAudio) videoSamples++
    }

    private fun finishWhenDrained() {
        if (stopping && videoEnded && (!audioEnabledForSession || audioEnded)) {
            if (::worker.isInitialized) worker.removeCallbacks(stopTimeout)
            finish(videoSamples > 0 && muxerStarted)
        }
    }

    override fun onCreate() {
        super.onCreate()
        thread.start()
        worker = Handler(thread.looper)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> {
                RecordingState.update(RecordingState.Phase.STOPPING, "Saving MP4...", this)
                if (::worker.isInitialized && thread.isAlive) worker.post { stopRecording() }
                return START_NOT_STICKY
            }
            START -> Unit
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        var requestedAudio = RecordingOptions.AudioSource.saved(intent.getStringExtra("audioSource"))
        val hasAudioPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasAudioPerm && requestedAudio.hasAudio) {
            requestedAudio = RecordingOptions.AudioSource.NONE
        }

        // CRITICAL (Android 12–16): ALWAYS call startForeground IMMEDIATELY before any check that
        // could throw, and fall back to MEDIA_PROJECTION-only if OEM rejects MICROPHONE FGS type.
        var fgStarted = false
        if (Build.VERSION.SDK_INT >= 29) {
            if (Build.VERSION.SDK_INT >= 30 && requestedAudio.microphone) {
                try {
                    startForeground(
                        NOTIFICATION,
                        notification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                    fgStarted = true
                } catch (sec: Throwable) {
                    Log.w(TAG, "Microphone FGS rejected by OS, falling back to video-only MediaProjection", sec)
                    requestedAudio = if (requestedAudio.game) RecordingOptions.AudioSource.GAME else RecordingOptions.AudioSource.NONE
                }
            }
            if (!fgStarted) {
                try {
                    startForeground(
                        NOTIFICATION,
                        notification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                    fgStarted = true
                } catch (err: Throwable) {
                    Log.e(TAG, "MediaProjection startForeground failed", err)
                }
            }
        } else {
            runCatching {
                startForeground(NOTIFICATION, notification())
                fgStarted = true
            }
        }

        if (!fgStarted || Build.VERSION.SDK_INT < 29) {
            toast("Android 10+ screen capture permission is required")
            RecordingState.update(RecordingState.Phase.IDLE, "", this)
            stopSelf()
            return START_NOT_STICKY
        }

        if (startPending || (RecordingState.isActive() && !finished && videoEncoder != null)) {
            return START_NOT_STICKY
        }

        try {
            val token = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra("consent", Intent::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra("consent")
            } ?: error("Screen capture permission is required")

            val config = RecordingOptions.Config(
                quality = intent.getIntExtra("quality", 0),
                fps = intent.getIntExtra("fps", 0),
                width = intent.getIntExtra("width", 0),
                height = intent.getIntExtra("height", 0),
                bitrate = intent.getIntExtra("bitrate", 0),
                encoder = intent.getStringExtra("encoder").orEmpty(),
                audioSource = requestedAudio
            )
            require(
                config.width > 0 && config.height > 0 && config.fps > 0 &&
                    config.bitrate > 0 && config.encoder.isNotEmpty()
            ) { "Invalid recorder configuration" }

            val rootPath = intent.getStringExtra("instanceRoot") ?: error("Missing running instance directory")
            val instanceRoot = File(rootPath)
            RecordingPaths.directory(instanceRoot, false)
            finished = false
            stopping = false
            startPending = true
            RecordingState.update(RecordingState.Phase.STARTING, "Starting recorder...", this)
            worker.post { begin(token, config, instanceRoot) }
        } catch (error: Throwable) {
            worker.post { finish(false, error.message ?: "Cannot start recorder") }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!finished && ::worker.isInitialized && thread.isAlive) {
            worker.post { stopRecording() }
        } else {
            stopSelf()
        }
    }

    private fun begin(token: Intent, config: RecordingOptions.Config, instanceRoot: File) {
        if (Build.VERSION.SDK_INT < 29) {
            finish(false)
            return
        }
        try {
            val epochUs = System.nanoTime() / 1000
            timestamps = RecordingTimestamps(epochUs)
            output = RecordingOutput(this, instanceRoot)
            muxer = output!!.open()

            val callback = object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    if (finished || codec !== videoEncoder) return
                    try {
                        videoTrack = muxer!!.addTrack(format)
                        startMuxerWhenReady()
                    } catch (error: Throwable) {
                        finish(false, error.message ?: "Invalid video format")
                    }
                }

                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    if (finished || codec !== videoEncoder) return
                    try {
                        encoded(codec, index, info, videoTrack, false)
                        if (!finished) runCatching { codec.releaseOutputBuffer(index, false) }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            videoEnded = true
                            finishWhenDrained()
                        }
                    } catch (error: Throwable) {
                        finish(false, error.message ?: "Cannot write video")
                    }
                }

                override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) {
                    if (!finished && codec === videoEncoder) finish(false, "Video encoder: ${error.diagnosticInfo}")
                }
            }

            fun videoFormat(optional: Boolean) = MediaFormat.createVideoFormat("video/avc", config.width, config.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                if (optional && Build.VERSION.SDK_INT >= 29) {
                    setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                    setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                    setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, config.fps.toFloat())
                }
            }

            // Some vendors advertise a codec but reject its configuration. Resetting a codec
            // removes its asynchronous callback, so retry on a fresh codec with its callback set.
            var lastError: Throwable? = null
            var ready = false
            val candidates = runCatching { RecordingOptions.encoderCandidates(config) }.getOrDefault(listOf(config.encoder))
            for (name in candidates) {
                for (optional in listOf(true, false)) {
                    var candidate: MediaCodec? = null
                    var candidateSurface: Surface? = null
                    try {
                        candidate = MediaCodec.createByCodecName(name)
                        candidate.setCallback(callback, worker)
                        candidate.configure(videoFormat(optional), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        candidateSurface = candidate.createInputSurface()
                        candidate.start()
                        videoEncoder = candidate
                        surface = candidateSurface
                        ready = true
                        break
                    } catch (error: Throwable) {
                        lastError = error
                        Log.w(TAG, "Encoder $name rejected configuration (optional=$optional)", error)
                        runCatching { candidate?.release() }
                        runCatching { candidateSurface?.release() }
                    }
                }
                if (ready) break
            }
            if (!ready) throw IllegalStateException("No AVC encoder could start at ${config.quality}p ${config.fps}fps; try a lower setting", lastError)

            val manager = getSystemService(MediaProjectionManager::class.java)
            projection = requireNotNull(manager.getMediaProjection(Activity.RESULT_OK, token)) {
                "Screen capture session expired"
            }
            projection!!.registerCallback(projectionCallback, worker)

            audioEnabledForSession = config.audioSource.hasAudio
            audioEnded = !audioEnabledForSession

            if (audioEnabledForSession) {
                try {
                    audio = RecordingAudio(this, worker, epochUs, config.audioSource, object : RecordingAudio.Listener {
                        override fun format(format: MediaFormat) {
                            if (!finished && audioEnabledForSession) pendingAudioFormat = format
                        }

                        override fun sample(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                            if (finished || !audioEnabledForSession || info.size <= 0 ||
                                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
                            try {
                                if (audioTrack < 0) {
                                    val format = pendingAudioFormat ?: return
                                    // Do not create an empty AAC track on devices where capture never starts.
                                    audioTrack = muxer!!.addTrack(format)
                                    worker.removeCallbacks(audioTimeout)
                                    startMuxerWhenReady()
                                }
                                encoded(codec, index, info, audioTrack, true)
                            } catch (error: Throwable) {
                                try { dropAudio("Audio track unavailable: ${error.message}") }
                                catch (fatal: Throwable) { finish(false, fatal.message ?: "Cannot write video") }
                            }
                        }

                        override fun ended() {
                            audioEnded = true
                            finishWhenDrained()
                        }

                        override fun error(message: String) {
                            try { dropAudio(message) }
                            catch (fatal: Throwable) { finish(false, fatal.message ?: "Cannot write video") }
                        }
                    })
                    audio!!.start(projection!!)
                    worker.postDelayed(audioTimeout, 4_000L)
                } catch (audioErr: Throwable) {
                    Log.w(TAG, "Audio capture unavailable, continuing video-only", audioErr)
                    runCatching { audio?.release() }
                    audio = null
                    audioEnabledForSession = false
                    audioEnded = true
                }
            }

            display = requireNotNull(projection!!.createVirtualDisplay(
                "AerixRecorder",
                config.width,
                config.height,
                resources.displayMetrics.densityDpi.coerceAtLeast(160),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                worker
            )) { "Could not create the screen capture display" }

            val activeAudioLabel = if (audioEnabledForSession) config.audioSource.label else "Video only"
            RecordingState.update(
                RecordingState.Phase.RECORDING,
                "${config.quality}p · ${config.fps} FPS · $activeAudioLabel",
                this
            )
            toast("Recording started (${config.quality}p ${config.fps}fps)")
        } catch (error: Throwable) {
            Log.e(TAG, "Recorder failed to start", error)
            finish(false, error.message ?: "Unable to start hardware recording")
        }
    }

    private fun dropAudio(reason: String) {
        Log.w(TAG, "$reason; continuing video recording")
        worker.removeCallbacks(audioTimeout)
        audioEnabledForSession = false
        audioEnded = true
        runCatching { audio?.release() }
        audio = null
        startMuxerWhenReady()
        finishWhenDrained()
        toast("Audio unavailable; continuing video only")
    }

    private fun stopRecording() {
        if (stopping || finished) return
        stopping = true
        RecordingState.update(RecordingState.Phase.STOPPING, "Saving MP4...", this)
        audio?.stop()
        runCatching { display?.release() }
        display = null
        val signaled = runCatching { videoEncoder?.signalEndOfInputStream() ?: return@runCatching false; true }.getOrDefault(false)
        if (!signaled) videoEnded = true
        if (!audioEnabledForSession || audio == null) audioEnded = true
        worker.postDelayed(stopTimeout, 2500)
        finishWhenDrained()
    }

    private fun finish(usable: Boolean, failure: String? = null) {
        if (finished) return
        finished = true
        if (::worker.isInitialized) {
            worker.removeCallbacks(stopTimeout)
            worker.removeCallbacks(audioTimeout)
        }
        startPending = false
        runCatching { audio?.release() }
        audio = null
        runCatching { display?.release() }; display = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }; projection = null
        runCatching { videoEncoder?.stop() }
        runCatching { videoEncoder?.release() }; videoEncoder = null
        runCatching { surface?.release() }; surface = null

        var muxed = usable && muxerStarted && videoSamples > 0
        if (muxerStarted) {
            muxed = runCatching { muxer?.stop(); muxed }
                .onFailure { Log.e(TAG, "Muxer stop failed", it) }
                .getOrDefault(false)
        }
        runCatching { muxer?.release() }; muxer = null

        val saved = runCatching { output?.finish(muxed) }.getOrElse { error ->
            Log.e(TAG, "Output finalize failed", error)
            null
        }
        val result = when {
            saved?.playable == true -> "Saved to ${saved.path}"
            saved != null -> "Recording incomplete; kept for recovery at ${saved.path}"
            failure != null -> failure
            else -> "Recording discarded (no video data)"
        }
        toast(result)
        RecordingState.update(RecordingState.Phase.IDLE, result, this)
        runCatching {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
        thread.quitSafely()
    }

    private fun notification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Screen recorder", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, ScreenRecordingService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(R.drawable.notif_icon)
            .setContentTitle("Recording Minecraft")
            .setContentText("Hardware MP4 capture active")
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(android.R.drawable.ic_media_pause, "Stop", stop).build()
            )
            .build()
    }

    private fun toast(message: String) = main.post {
        runCatching { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
    }

    override fun onDestroy() {
        // MediaCodec, muxer and display must only be touched on their callback handler thread.
        if (!finished && ::worker.isInitialized && thread.isAlive) {
            if (!stopping) worker.post { stopRecording() }
            // If stopping, retain the worker until EOS or the bounded drain timeout.
        } else {
            thread.quitSafely()
        }
        super.onDestroy()
    }

    companion object {
        const val START = "net.kdt.pojavlaunch.recording.START"
        const val STOP = "net.kdt.pojavlaunch.recording.STOP"
        private const val CHANNEL = "aerix_screen_recorder"
        private const val NOTIFICATION = 4109
        private const val TAG = "AerixRecorder"
    }
}
