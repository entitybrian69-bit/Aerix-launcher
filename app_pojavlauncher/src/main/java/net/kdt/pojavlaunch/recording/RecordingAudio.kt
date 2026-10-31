package net.kdt.pojavlaunch.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Opens only the selected sources. PCM/mixing and AAC input stay off the video thread. */
@RequiresApi(29)
internal class RecordingAudio(
    private val context: Context,
    private val handler: Handler,
    private val epochUs: Long,
    private val source: RecordingOptions.AudioSource,
    private val listener: Listener
) {
    interface Listener {
        fun format(format: MediaFormat)
        fun sample(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo)
        fun ended()
        fun error(message: String)
    }

    private val indices = LinkedBlockingQueue<Int>(64)
    private var game: AudioRecord? = null
    private var mic: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var reader: Thread? = null
    @Volatile private var stopping = false
    @Volatile private var closed = false

    fun start(projection: MediaProjection) {
        if (!source.hasAudio) return
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            "Audio capture permission is required"
        }
        val stereo = AudioFormat.Builder()
            .setSampleRate(RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val mono = AudioFormat.Builder()
            .setSampleRate(RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        if (source.game) {
            val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUid(android.os.Process.myUid())
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            game = AudioRecord.Builder()
                .setAudioFormat(stereo)
                .setAudioPlaybackCaptureConfig(capture)
                .setBufferSizeInBytes(
                    maxOf(
                        16384,
                        AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                    )
                )
                .build()
        }
        if (source.microphone) {
            mic = AudioRecord.Builder()
                .setAudioFormat(mono)
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setBufferSizeInBytes(
                    maxOf(
                        8192,
                        AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    )
                )
                .build()
        }
        check(
            (!source.game || game?.state == AudioRecord.STATE_INITIALIZED) &&
                (!source.microphone || mic?.state == AudioRecord.STATE_INITIALIZED)
        ) {
            "Device cannot initialize ${source.label}"
        }

        encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
        encoder!!.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                if (closed) return
                // If queue is momentarily full before reader drains, drop oldest or ignore safely rather than crashing
                indices.offer(index)
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                if (!closed) {
                    try {
                        listener.format(format)
                    } catch (t: Throwable) {
                        if (!closed) listener.error(t.message ?: "Audio format error")
                    }
                }
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                if (closed) return
                try {
                    listener.sample(codec, index, info)
                    if (!closed) runCatching { codec.releaseOutputBuffer(index, false) }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) listener.ended()
                } catch (t: Throwable) {
                    if (!closed) listener.error(t.message ?: "Cannot encode audio")
                }
            }

            override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) {
                if (!closed) listener.error("Audio encoder: ${error.diagnosticInfo}")
            }
        }, handler)

        encoder!!.configure(
            MediaFormat.createAudioFormat("audio/mp4a-latm", RATE, 2).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAMES * 4)
            },
            null,
            null,
            MediaCodec.CONFIGURE_FLAG_ENCODE
        )
        encoder!!.start()
        game?.startRecording()
        mic?.startRecording()
        check(
            (!source.game || game?.recordingState == AudioRecord.RECORDSTATE_RECORDING) &&
                (!source.microphone || mic?.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        ) {
            "${source.label} must be available"
        }
        reader = Thread({ readLoop() }, "Aerix audio capture").apply { start() }
    }

    private fun readLoop() {
        val gamePcm = ShortArray(FRAMES * 2)
        val micPcm = ShortArray(FRAMES)
        var submitted = 0L
        val startUs = (System.nanoTime() / 1000 - epochUs).coerceAtLeast(0)
        fun read(record: AudioRecord, buffer: ShortArray): Boolean {
            var offset = 0
            while (offset < buffer.size && !stopping && !closed) {
                val count = record.read(buffer, offset, buffer.size - offset, AudioRecord.READ_BLOCKING)
                if (count < 0) {
                    if (stopping || closed) return false
                    error("Audio capture failed ($count)")
                }
                if (count == 0) {
                    SystemClock.sleep(2)
                    continue
                }
                offset += count
            }
            return offset == buffer.size
        }
        try {
            while (!stopping && !closed &&
                (!source.game || read(game!!, gamePcm)) &&
                (!source.microphone || read(mic!!, micPcm))
            ) {
                var index: Int? = null
                while (index == null && !stopping && !closed) index = indices.poll(100, TimeUnit.MILLISECONDS)
                if (index == null || closed) break
                val codec = encoder ?: break
                val input = codec.getInputBuffer(index)?.apply { clear() } ?: continue
                AudioMixer.mix(gamePcm, micPcm, FRAMES, input, source)
                codec.queueInputBuffer(index, 0, FRAMES * 4, startUs + submitted * 1_000_000 / RATE, 0)
                submitted += FRAMES
            }
            if (!closed) {
                val index = indices.poll(800, TimeUnit.MILLISECONDS)
                if (index != null && !closed) {
                    runCatching {
                        encoder?.queueInputBuffer(
                            index,
                            0,
                            0,
                            startUs + submitted * 1_000_000 / RATE,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            if (!closed && !stopping) {
                handler.post {
                    if (!closed) listener.error(t.message ?: "Audio capture failed")
                }
            }
        }
    }

    fun stop() {
        stopping = true
        runCatching { game?.stop() }
        runCatching { mic?.stop() }
    }

    fun release() {
        closed = true
        stop()
        reader?.interrupt()
        runCatching { reader?.join(500) }
        runCatching { game?.release() }; game = null
        runCatching { mic?.release() }; mic = null
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }; encoder = null
    }

    companion object {
        private const val RATE = 48000
        private const val FRAMES = 1024
    }
}
