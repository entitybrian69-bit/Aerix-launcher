package net.kdt.pojavlaunch.recording

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
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
import android.view.Surface
import android.widget.Toast
import net.kdt.pojavlaunch.R

/** Hardware surface -> asynchronous encoder -> MP4. No frame readback, bitmap copying or render hooks. */
class ScreenRecordingService : Service() {
    companion object {
        const val START = "net.kdt.pojavlaunch.recording.START"
        const val STOP = "net.kdt.pojavlaunch.recording.STOP"
        private const val CHANNEL = "aerix_recording"
        private const val NOTIFICATION = 7142
    }
    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    @Volatile private var stopRequested = false
    // Everything below is owned by the encoder thread.
    private var completed = false
    private var stopping = false
    private var codec: MediaCodec? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var surface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var output: RecordingOutput? = null
    private var muxerStarted = false
    private var track = -1
    private var samples = 0L
    private val stopTimeout = Runnable { finish(samples > 0, "Recording stopped") }
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { requestStop() }
    }

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("Aerix video encoder", android.os.Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        worker = Handler(thread.looper)
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopRequested = true
            if (started) {
                RecordingState.update(RecordingState.Phase.STOPPING)
                worker.post { requestStop() }
            } else stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != START || started) {
            if (!started) stopSelf()
            return START_NOT_STICKY
        }
        started = true
        try {
            val notification = notification()
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(NOTIFICATION, notification)
            RecordingState.update(RecordingState.Phase.STARTING)
            val token = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("consent", Intent::class.java)
                else @Suppress("DEPRECATION") (intent.getParcelableExtra("consent") as? Intent)
            requireNotNull(token) { "Screen recording permission missing" }
            val config = RecordingOptions.Config(intent.getIntExtra("quality", 480), intent.getIntExtra("fps", 24),
                intent.getIntExtra("width", 0), intent.getIntExtra("height", 0),
                intent.getIntExtra("bitrate", 0), intent.getStringExtra("encoder") ?: "")
            require(config.quality in RecordingOptions.qualities && config.fps in RecordingOptions.frameRates &&
                config.width > 0 && config.height > 0 && config.bitrate > 0 && config.encoder.isNotEmpty())
            worker.post { begin(token, config) }
        } catch (error: Exception) {
            worker.post { finish(false, error.message ?: "Cannot start recorder") }
        }
        return START_NOT_STICKY // Never reuse a consumed MediaProjection consent token after process death.
    }

    private fun begin(token: Intent, config: RecordingOptions.Config) {
        try {
            if (stopRequested) { finish(false, "Recording cancelled"); return }
            output = RecordingOutput(this)
            muxer = output!!.open()
            codec = MediaCodec.createByCodecName(config.encoder)
            codec!!.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit
                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    if (completed) return
                    try {
                        check(!muxerStarted) { "Unexpected encoder format change" }
                        track = muxer!!.addTrack(format)
                        muxer!!.start()
                        muxerStarted = true
                    } catch (error: Exception) { finish(false, error.message ?: "Cannot create video track") }
                }
                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    if (completed) return
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(muxerStarted) { "Video data arrived before format" }
                            val buffer = requireNotNull(codec.getOutputBuffer(index))
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer!!.writeSampleData(track, buffer, info)
                            samples++
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) finish(samples > 0, "Recording stopped")
                    } catch (error: Exception) { finish(false, error.message ?: "Recording failed (check free storage)") }
                }
                override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) {
                    finish(false, "Encoder unavailable: ${error.diagnosticInfo}")
                }
            }, worker)
            val format = MediaFormat.createVideoFormat("video/avc", config.width, config.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                setInteger(MediaFormat.KEY_PRIORITY, 1)
                if (Build.VERSION.SDK_INT >= 29) setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, config.fps.toFloat())
            }
            codec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec!!.createInputSurface()
            codec!!.start()
            projection = requireNotNull(getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(Activity.RESULT_OK, token))
            projection!!.registerCallback(projectionCallback, worker)
            if (stopRequested) { finish(false, "Recording cancelled"); return }
            display = requireNotNull(projection!!.createVirtualDisplay("Aerix recorder", config.width, config.height,
                resources.displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, worker))
            RecordingState.update(RecordingState.Phase.RECORDING, "${config.quality}p · ${config.fps} FPS target")
        } catch (error: Exception) { finish(false, error.message ?: "Unable to start hardware recording") }
    }

    private fun requestStop() {
        if (completed || stopping) return
        stopping = true
        RecordingState.update(RecordingState.Phase.STOPPING)
        runCatching { display?.release() }
        display = null
        try {
            codec?.signalEndOfInputStream() ?: run { finish(false, "No recording data"); return }
            worker.postDelayed(stopTimeout, 3000)
        } catch (_: Exception) { finish(samples > 0, "Recording stopped") }
    }

    private fun finish(keep: Boolean, message: String) {
        if (completed) return
        completed = true
        worker.removeCallbacks(stopTimeout)
        var success = keep && muxerStarted && samples > 0
        runCatching { display?.release() }; display = null
        runCatching { codec?.stop() }; runCatching { codec?.release() }; codec = null
        runCatching { surface?.release() }; surface = null
        if (muxerStarted) runCatching { muxer?.stop() }.onFailure { success = false }
        runCatching { muxer?.release() }; muxer = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }; projection = null
        val path = runCatching { output?.finish(success).orEmpty() }.getOrElse {
            runCatching { output?.finish(false) }; success = false; ""
        }
        val result = if (success) "Recording saved: $path" else if (keep) "Could not finalize recording; unfinished file removed" else message
        RecordingState.update(RecordingState.Phase.IDLE, result)
        main.post {
            Toast.makeText(this, result, Toast.LENGTH_LONG).show()
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
            stopSelf()
        }
        thread.quitSafely()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Screen recording", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, ScreenRecordingService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder.setSmallIcon(R.drawable.aerix_ic_record).setContentTitle("Aerix screen recording")
            .setContentText("Video only · tap Stop to save").setOngoing(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "Stop recording", stop).build()).build()
    }

    override fun onDestroy() {
        if (!started) thread.quitSafely()
        else if (thread.isAlive) worker.post { finish(samples > 0, "Recorder closed") }
        super.onDestroy()
    }
}
