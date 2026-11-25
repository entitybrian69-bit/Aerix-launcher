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
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Parcelable
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import net.kdt.pojavlaunch.R
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Isolated `:recorder` foreground service running a zero-copy hardware video pipeline:
 * `MediaProjection` -> `VirtualDisplay` -> hardware H.264 `MediaCodec` Surface -> `MediaMuxer`.
 *
 * All MediaCodec, MediaMuxer, VirtualDisplay, and MediaProjection operations execute sequentially
 * on a single dedicated [HandlerThread] to eliminate cross-thread races and driver crashes.
 */
class ScreenRecordingService : Service() {
    companion object {
        const val START = "net.al.aerixlauncher.recorder.START"
        const val STOP = "net.al.aerixlauncher.recorder.STOP"
        const val EXTRA_WITH_CONTROLS = "with_controls"
        const val EXTRA_SURFACE_MESSENGER = "surface_messenger"
        const val MSG_CLEAN_SURFACE = 1
        const val MSG_DETACH_SURFACE = 2
        private const val TAG = "AerixRecorderService"
        private const val CHANNEL_ID = "aerix_recorder"
        private const val NOTIF_ID = 4401
        private const val STOP_TIMEOUT_MS = 3_000L
        private const val AUDIO_WAIT_TIMEOUT_MS = 600L
        private const val MAX_PRE_START_BYTES = 4 * 1024 * 1024
    }

    private data class PendingSample(val isVideo: Boolean, val data: ByteBuffer, val info: MediaCodec.BufferInfo)

    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler

    private var foregroundPromoted = false
    private var active = false
    private var stopping = false
    private var finished = false

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var output: RecordingOutput? = null
    private var audio: RecordingAudio? = null
    private var timestamps: RecordingTimestamps? = null
    private var surfaceMessenger: Messenger? = null

    private var expectAudio = false
    private var audioFinished = true
    private var videoEos = false
    private var muxerStarted = false
    private var videoTrack = -1
    private var audioTrack = -1
    private var pendingVideoFormat: MediaFormat? = null
    private var pendingAudioFormat: MediaFormat? = null
    private var hasPreStartKeyframe = false
    private var hasPreStartAudio = false
    private var muxedVideoFrames = 0
    private var muxedAudioFrames = 0
    private var sessionDetail = ""

    private val preStartSamples = ArrayList<PendingSample>()
    private var preStartBytes = 0

    private val stopTimeout = Runnable { finishStop() }
    private val audioStartTimeout = Runnable {
        if (!muxerStarted && active && !finished) {
            expectAudio = false
            tryStartMuxer(forceVideoOnly = true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        workerThread = HandlerThread("AerixRecorder", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
        worker = Handler(workerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val sourceName = intent?.getStringExtra("audio_source")
        val audioSource = RecordingOptions.AudioSource.saved(sourceName)
        val wantsMic = audioSource.microphone
        val withControls = intent?.getBooleanExtra(EXTRA_WITH_CONTROLS, true) ?: true
        val needsProjection = withControls || audioSource.game

        // Always promote synchronously before any validation/stopSelf to satisfy Android 14+ FGS timing.
        if (!foregroundPromoted) {
            if (!promoteToForeground(wantsMic, needsProjection)) {
                RecordingState.publish(
                    this,
                    RecordingState.Phase.IDLE,
                    notice = "Screen recorder permission blocked by system"
                )
                stopSelf()
                return START_NOT_STICKY
            }
        }

        when (action) {
            START -> {
                val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
                val consent = parcelableExtra<Intent>(intent, "consent")
                val messenger = parcelableExtra<Messenger>(intent, EXTRA_SURFACE_MESSENGER)
                val instancePath = intent.getStringExtra("instanceRoot")
                val quality = intent.getIntExtra("quality", RecordingOptions.DEFAULT_QUALITY)
                val fps = intent.getIntExtra("fps", RecordingOptions.DEFAULT_FPS)
                val width = intent.getIntExtra("width", 1280)
                val height = intent.getIntExtra("height", 720)
                val bitrate = intent.getLongExtra("bitrate", RecordingOptions.bitrate(width, height, fps))
                val encoder = intent.getStringExtra("encoder").orEmpty()

                if (instancePath.isNullOrBlank() || (needsProjection && (resultCode != Activity.RESULT_OK || consent == null))) {
                    RecordingState.publish(
                        this,
                        RecordingState.Phase.IDLE,
                        notice = "Screen capture consent was missing"
                    )
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }

                worker.post {
                    startRecording(
                        resultCode = resultCode,
                        consent = consent,
                        instanceRoot = File(instancePath),
                        config = RecordingOptions.Config(quality, fps, width, height, bitrate, encoder),
                        audioSource = audioSource,
                        withControls = withControls,
                        messenger = messenger
                    )
                }
            }
            STOP -> {
                worker.post {
                    if (active) stopRecording() else {
                        RecordingState.publish(this, RecordingState.Phase.IDLE, "")
                        stopForegroundAndSelf()
                    }
                }
            }
            else -> {
                worker.post {
                    if (!active) stopForegroundAndSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        finalizeSynchronously()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        finalizeSynchronously()
        if (::workerThread.isInitialized) {
            workerThread.quitSafely()
        }
        super.onDestroy()
    }

    private fun finalizeSynchronously() {
        if (!::worker.isInitialized || !::workerThread.isInitialized || !workerThread.isAlive) return
        val latch = CountDownLatch(1)
        val posted = worker.post {
            try {
                if (active && !finished) {
                    finishStop()
                }
            } finally {
                latch.countDown()
            }
        }
        if (posted) {
            runCatching { latch.await(1_500L, TimeUnit.MILLISECONDS) }
        }
    }

    private fun promoteToForeground(wantsMic: Boolean, needsProjection: Boolean): Boolean {
        ensureNotificationChannel()
        val notification = buildNotification("Preparing screen recorder…")
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                val hasMicPerm = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val type = when {
                    needsProjection && wantsMic && hasMicPerm ->
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    needsProjection ->
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    wantsMic && hasMicPerm ->
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    else ->
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                try {
                    startForeground(NOTIF_ID, notification, type)
                } catch (sec: SecurityException) {
                    if (needsProjection && type != ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) {
                        startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                    } else if (!needsProjection && type != ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) {
                        startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else {
                        throw sec
                    }
                }
            } else if (Build.VERSION.SDK_INT >= 29) {
                if (needsProjection) {
                    startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(NOTIF_ID, notification)
                }
            } else {
                startForeground(NOTIF_ID, notification)
            }
            foregroundPromoted = true
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to promote recording service to foreground", t)
            false
        }
    }

    private fun startRecording(
        resultCode: Int,
        consent: Intent?,
        instanceRoot: File,
        config: RecordingOptions.Config,
        audioSource: RecordingOptions.AudioSource,
        withControls: Boolean,
        messenger: Messenger?
    ) {
        if (active) return
        active = true
        stopping = false
        finished = false
        videoEos = false
        muxerStarted = false
        videoTrack = -1
        audioTrack = -1
        pendingVideoFormat = null
        pendingAudioFormat = null
        hasPreStartKeyframe = false
        hasPreStartAudio = false
        muxedVideoFrames = 0
        muxedAudioFrames = 0
        preStartSamples.clear()
        preStartBytes = 0
        surfaceMessenger = messenger
        sessionDetail = if (withControls) config.label else "${config.label} · No controls"
        timestamps = RecordingTimestamps(System.nanoTime() / 1_000L)

        RecordingState.publish(this, RecordingState.Phase.STARTING, sessionDetail)

        val out = RecordingOutput(this, instanceRoot)
        output = out
        val mediaMuxer = runCatching { out.open() }.getOrElse { err ->
            failStart("Cannot open recording file", err)
            return
        }
        muxer = mediaMuxer

        val needsProjection = withControls || audioSource.game
        var proj: MediaProjection? = null
        if (needsProjection) {
            val projMgr = getSystemService(MediaProjectionManager::class.java)
            proj = consent?.let {
                runCatching { projMgr?.getMediaProjection(resultCode, it) }.getOrNull()
            }
            if (proj == null) {
                failStart("Screen capture session could not be created", null)
                return
            }
            projection = proj

            // Android 14+ mandates registering a callback BEFORE calling createVirtualDisplay().
            val projCb = object : MediaProjection.Callback() {
                override fun onStop() {
                    worker.post { if (active) stopRecording() }
                }

                override fun onCapturedContentResize(width: Int, height: Int) {
                    // VirtualDisplay scales automatically to the configured encoder surface.
                }

                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                    // Keep recording cleanly without disrupting the session.
                }
            }
            projectionCallback = projCb
            proj.registerCallback(projCb, worker)
        }

        val startedPair = createConfiguredVideoEncoder(config)
        if (startedPair == null) {
            failStart("No working hardware video encoder found", null)
            return
        }
        val (codec, surface) = startedPair
        videoEncoder = codec
        inputSurface = surface

        if (withControls) {
            val activeProj = proj
            if (activeProj == null) {
                failStart("Screen capture session could not be created", null)
                return
            }
            val dpi = resources.displayMetrics.densityDpi.coerceAtLeast(160)
            val display = runCatching {
                activeProj.createVirtualDisplay(
                    "AerixRecorder",
                    config.width,
                    config.height,
                    dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    worker
                )
            }.getOrNull()
            if (display == null) {
                failStart("Could not create virtual display", null)
                return
            }
            virtualDisplay = display
        } else {
            val target = surfaceMessenger
            val sent = if (target != null) {
                runCatching {
                    val msg = Message.obtain().apply {
                        what = MSG_CLEAN_SURFACE
                        data = Bundle().apply {
                            putParcelable("surface", surface)
                            putInt("width", config.width)
                            putInt("height", config.height)
                            putInt("fps", config.fps)
                        }
                    }
                    target.send(msg)
                }.isSuccess
            } else {
                false
            }
            if (!sent) {
                failStart("Could not attach clean game surface for recording", null)
                return
            }
        }

        val hasAudioPerm = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (audioSource != RecordingOptions.AudioSource.NONE && hasAudioPerm) {
            expectAudio = true
            audioFinished = false
            audio = RecordingAudio(worker, audioSource, proj, audioListener).also { it.start() }
            worker.postDelayed(audioStartTimeout, AUDIO_WAIT_TIMEOUT_MS)
        } else {
            expectAudio = false
            audioFinished = true
        }

        updateNotification("Recording · $sessionDetail")
        RecordingState.publish(
            this,
            RecordingState.Phase.RECORDING,
            sessionDetail,
            startedAt = SystemClock.elapsedRealtime()
        )
    }

    private fun createConfiguredVideoEncoder(config: RecordingOptions.Config): Pair<MediaCodec, Surface>? {
        val candidates = ArrayList<() -> MediaCodec>(2)
        if (config.encoder.isNotBlank()) {
            candidates.add { MediaCodec.createByCodecName(config.encoder) }
        }
        candidates.add { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC) }

        for (factory in candidates) {
            for (richFormat in listOf(true, false)) {
                var codec: MediaCodec? = null
                var surface: Surface? = null
                try {
                    codec = factory()
                    codec.setCallback(videoCallback, worker)
                    codec.configure(buildVideoFormat(config, richFormat), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    surface = codec.createInputSurface()
                    codec.start()
                    return codec to surface
                } catch (err: Throwable) {
                    Log.w(TAG, "Encoder setup failed (rich=$richFormat): ${err.message}")
                    runCatching { surface?.release() }
                    runCatching { codec?.release() }
                }
            }
        }
        return null
    }

    private fun buildVideoFormat(config: RecordingOptions.Config, rich: Boolean): MediaFormat {
        return MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, config.width, config.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate.toInt())
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            if (rich) {
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L)
            }
        }
    }

    private val videoCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            if (finished || pendingVideoFormat != null) return
            pendingVideoFormat = format
            tryStartMuxer()
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            try {
                val out = codec.getOutputBuffer(index)
                val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                val isEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                if (out != null && info.size > 0 && !isConfig && !finished) {
                    val isSync = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                    val pts = timestamps?.video(info.presentationTimeUs) ?: info.presentationTimeUs.coerceAtLeast(0L)
                    val cleanFlags = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()
                    if (muxerStarted) {
                        if (muxedVideoFrames > 0 || isSync) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val sampleInfo = MediaCodec.BufferInfo().apply {
                                set(info.offset, info.size, pts, cleanFlags)
                            }
                            muxer?.writeSampleData(videoTrack, out, sampleInfo)
                            muxedVideoFrames++
                        }
                    } else if (hasPreStartKeyframe || isSync) {
                        if (preStartBytes + info.size > MAX_PRE_START_BYTES && isSync) {
                            // Keep only audio samples + this fresh IDR keyframe so GOP remains intact.
                            preStartSamples.removeAll { it.isVideo }
                            preStartBytes = preStartSamples.sumOf { it.info.size }
                            hasPreStartKeyframe = false
                        }
                        if (preStartBytes + info.size <= MAX_PRE_START_BYTES) {
                            val copy = ByteBuffer.allocateDirect(info.size)
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            copy.put(out)
                            copy.flip()
                            val copyInfo = MediaCodec.BufferInfo().apply {
                                set(0, info.size, pts, cleanFlags)
                            }
                            preStartSamples += PendingSample(isVideo = true, data = copy, info = copyInfo)
                            preStartBytes += info.size
                            if (isSync) hasPreStartKeyframe = true
                            tryStartMuxer()
                        }
                    }
                }
                if (isEos && stopping) {
                    videoEos = true
                    checkFinishStop()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Video sample write error", t)
            } finally {
                runCatching { codec.releaseOutputBuffer(index, false) }
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(TAG, "Video encoder error", e)
            stopRecording()
        }
    }

    private val audioListener = object : RecordingAudio.Listener {
        override fun onAudioFormat(format: MediaFormat) {
            if (finished || pendingAudioFormat != null) return
            pendingAudioFormat = format
            tryStartMuxer()
        }

        override fun onAudioSample(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            if (finished) return
            val pts = timestamps?.audio(info.presentationTimeUs) ?: info.presentationTimeUs.coerceAtLeast(0L)
            val sampleInfo = MediaCodec.BufferInfo().apply {
                set(0, info.size, pts, info.flags)
            }
            if (muxerStarted && audioTrack >= 0) {
                runCatching {
                    muxer?.writeSampleData(audioTrack, buffer, sampleInfo)
                    muxedAudioFrames++
                }
            } else if (!muxerStarted && expectAudio && preStartBytes + info.size <= MAX_PRE_START_BYTES) {
                preStartSamples += PendingSample(isVideo = false, data = buffer, info = sampleInfo)
                preStartBytes += info.size
                hasPreStartAudio = true
                tryStartMuxer()
            }
        }

        override fun onAudioFinished() {
            audioFinished = true
            if (!muxerStarted && !hasPreStartAudio) {
                expectAudio = false
                tryStartMuxer(forceVideoOnly = true)
            }
            if (stopping) checkFinishStop()
        }

        override fun onAudioWarning(message: String) {
            RecordingState.publish(
                this@ScreenRecordingService,
                RecordingState.phase,
                sessionDetail,
                notice = message
            )
        }
    }

    private fun tryStartMuxer(forceVideoOnly: Boolean = false) {
        if (muxerStarted || finished) return
        val m = muxer ?: return
        val vFormat = pendingVideoFormat ?: return
        if (!hasPreStartKeyframe) return

        val includeAudio = expectAudio && pendingAudioFormat != null && hasPreStartAudio
        if (expectAudio && !includeAudio && !forceVideoOnly) return

        videoTrack = runCatching { m.addTrack(vFormat) }.getOrElse { err ->
            Log.w(TAG, "Failed adding video track", err)
            stopRecording()
            return
        }
        if (includeAudio) {
            audioTrack = runCatching { m.addTrack(pendingAudioFormat!!) }.getOrElse { err ->
                Log.w(TAG, "Failed adding audio track; continuing video-only", err)
                expectAudio = false
                -1
            }
        } else {
            expectAudio = false
            audioTrack = -1
        }

        val started = runCatching { m.start() }.isSuccess
        if (!started) {
            stopRecording()
            return
        }
        worker.removeCallbacks(audioStartTimeout)
        muxerStarted = true
        for (sample in preStartSamples) {
            val track = if (sample.isVideo) videoTrack else audioTrack
            if (track < 0) continue
            runCatching {
                m.writeSampleData(track, sample.data, sample.info)
                if (sample.isVideo) muxedVideoFrames++ else muxedAudioFrames++
            }
        }
        preStartSamples.clear()
        preStartBytes = 0
    }

    private fun stopRecording() {
        if (!active || stopping) return
        stopping = true
        worker.removeCallbacks(audioStartTimeout)
        RecordingState.publish(this, RecordingState.Phase.STOPPING, sessionDetail)
        updateNotification("Saving recording…")

        notifyDetachSurface()
        val signaled = runCatching { videoEncoder?.signalEndOfInputStream() }.isSuccess
        if (!signaled) videoEos = true

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        audio?.stop()
        worker.postDelayed(stopTimeout, STOP_TIMEOUT_MS)
        checkFinishStop()
    }

    private fun checkFinishStop() {
        if (stopping && videoEos && audioFinished) {
            finishStop()
        }
    }

    private fun finishStop() {
        if (finished) return
        finished = true
        worker.removeCallbacks(stopTimeout)
        worker.removeCallbacks(audioStartTimeout)
        notifyDetachSurface()
        surfaceMessenger = null

        if (!muxerStarted && hasPreStartKeyframe) {
            tryStartMuxer(forceVideoOnly = true)
        }

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { videoEncoder?.stop() }
        runCatching { videoEncoder?.release() }
        videoEncoder = null
        runCatching { inputSurface?.release() }
        inputSurface = null

        val hasFrames = muxerStarted && muxedVideoFrames > 0
        val muxerStoppedCleanly = if (hasFrames) {
            runCatching { muxer?.stop() }.onFailure { Log.w(TAG, "Muxer stop failed", it) }.isSuccess
        } else {
            false
        }
        runCatching { muxer?.release() }
        muxer = null

        projectionCallback?.let { cb -> runCatching { projection?.unregisterCallback(cb) } }
        projectionCallback = null
        runCatching { projection?.stop() }
        projection = null

        val saved = runCatching { output?.finish(muxerStoppedCleanly) }.getOrNull()
        output = null
        audio = null
        preStartSamples.clear()
        preStartBytes = 0
        active = false
        stopping = false

        val notice = when {
            saved == null -> "Recording failed — no video frames captured"
            saved.playable && saved.mediaUri != null -> "Saved to Movies/Aerix"
            saved.playable -> "Saved to instance recordings"
            else -> "Recording saved as incomplete clip"
        }
        RecordingState.publish(this, RecordingState.Phase.IDLE, "", notice = notice)
        stopForegroundAndSelf()
    }

    private fun failStart(message: String, error: Throwable?) {
        Log.e(TAG, message, error)
        worker.removeCallbacks(stopTimeout)
        worker.removeCallbacks(audioStartTimeout)
        notifyDetachSurface()
        surfaceMessenger = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { videoEncoder?.release() }
        videoEncoder = null
        runCatching { inputSurface?.release() }
        inputSurface = null
        runCatching { muxer?.release() }
        muxer = null
        projectionCallback?.let { cb -> runCatching { projection?.unregisterCallback(cb) } }
        projectionCallback = null
        runCatching { projection?.stop() }
        projection = null
        runCatching { output?.finish(false) }
        output = null
        active = false
        stopping = false
        finished = true

        RecordingState.publish(this, RecordingState.Phase.IDLE, "", notice = message, longNotice = true)
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        foregroundPromoted = false
        if (Build.VERSION.SDK_INT >= 24) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { stopForeground(true) }
        }
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Recorder",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, ScreenRecordingService::class.java).setAction(STOP)
        val stopPending = if (Build.VERSION.SDK_INT >= 26) {
            PendingIntent.getForegroundService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.aerix_ic_record)
            .setContentTitle("Aerix Screen Recorder")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        runCatching { mgr.notify(NOTIF_ID, buildNotification(text)) }
    }

    private fun notifyDetachSurface() {
        val target = surfaceMessenger ?: return
        runCatching { target.send(Message.obtain().apply { what = MSG_DETACH_SURFACE }) }
    }

    private inline fun <reified T : Parcelable> parcelableExtra(intent: Intent, key: String): T? {
        return if (Build.VERSION.SDK_INT >= 33) {
            runCatching { intent.getParcelableExtra(key, T::class.java) }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            runCatching { intent.getParcelableExtra<T>(key) }.getOrNull()
        }
    }
}
