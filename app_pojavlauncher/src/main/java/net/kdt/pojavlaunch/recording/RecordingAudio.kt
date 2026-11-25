package net.kdt.pojavlaunch.recording

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Process
import android.util.Log
import java.nio.ByteBuffer

/**
 * Low-overhead, non-blocking AAC audio capture for game playback and/or microphone.
 *
 * Key design choices vs the old recorder:
 * - Uses [AudioRecord.READ_NON_BLOCKING] on a 20 ms monotonic loop so silence never stalls capture
 *   and stopping never deadlocks inside a blocking JNI read.
 * - Primes the AAC encoder with one silent frame immediately on startup so `INFO_OUTPUT_FORMAT_CHANGED`
 *   arrives in milliseconds instead of stalling video muxing for seconds.
 * - Pre-allocates reusable PCM buffers (zero per-frame heap allocations).
 */
internal class RecordingAudio(
    private val worker: Handler,
    private val requested: RecordingOptions.AudioSource,
    private val projection: MediaProjection?,
    private val listener: Listener
) {
    interface Listener {
        fun onAudioFormat(format: MediaFormat)
        fun onAudioSample(buffer: ByteBuffer, info: MediaCodec.BufferInfo)
        fun onAudioFinished()
        fun onAudioWarning(message: String)
    }

    private val tag = "AerixRecorderAudio"
    private val sampleRate = pickSampleRate()
    private val framesPerChunk = sampleRate / 50 // 20 ms frames (960 @ 48 kHz, 882 @ 44.1 kHz)
    private val bytesPerChunk = framesPerChunk * 4 // 16-bit stereo

    @Volatile
    private var running = false
    private var gameRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var captureThread: Thread? = null
    private var effectiveSource = RecordingOptions.AudioSource.NONE
    private var finishedReported = false

    fun start() {
        if (requested == RecordingOptions.AudioSource.NONE) {
            reportFinished()
            return
        }

        if (requested.game) {
            gameRecord = openGameRecord()
            if (gameRecord == null) {
                worker.post { listener.onAudioWarning("Game audio capture is unavailable on this device") }
            }
        }
        if (requested.microphone) {
            micRecord = openMicRecord()
            if (micRecord == null) {
                worker.post { listener.onAudioWarning("Microphone capture is unavailable") }
            }
        }

        effectiveSource = resolveSource(gameRecord != null, micRecord != null)
        if (effectiveSource == RecordingOptions.AudioSource.NONE) {
            releaseRecords()
            reportFinished()
            return
        }

        val aac = runCatching { createAacEncoder() }.getOrElse { err ->
            Log.w(tag, "Failed to initialize AAC encoder", err)
            releaseRecords()
            worker.post { listener.onAudioWarning("Audio encoder unavailable; recording video only") }
            reportFinished()
            return
        }
        encoder = aac

        // Prime with 20 ms of silence at PTS=0 so AAC emits csd-0 immediately and MediaMuxer
        // never stalls waiting for the game to play its first sound.
        queueSilentPrimeFrame(aac)
        drainEncoder(aac, endOfStream = false, waitUs = 5_000L)

        runCatching { gameRecord?.startRecording() }
        runCatching { micRecord?.startRecording() }

        running = true
        captureThread = Thread({ captureLoop(aac) }, "AerixRecorderAudio").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        val thread = captureThread
        captureThread = null
        if (thread != null) {
            thread.interrupt()
        } else {
            releaseRecords()
            reportFinished()
        }
    }

    private fun captureLoop(aac: MediaCodec) {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
        val gamePcm = ShortArray(framesPerChunk * 2)
        val micPcm = ShortArray(framesPerChunk)
        var framesSubmitted = framesPerChunk.toLong()
        val startNs = System.nanoTime()
        var gameErrors = 0
        var micErrors = 0

        try {
            while (running) {
                val targetNs = startNs + (framesSubmitted * 1_000_000_000L) / sampleRate
                val waitNs = targetNs - System.nanoTime()
                if (waitNs > 1_500_000L) {
                    try {
                        Thread.sleep(waitNs / 1_000_000L, (waitNs % 1_000_000L).toInt())
                    } catch (_: InterruptedException) {
                        if (!running) break
                    }
                }
                if (!running) break

                val game = gameRecord
                if (game != null && effectiveSource.game) {
                    val ok = readNonBlocking(game, gamePcm, framesPerChunk * 2)
                    if (!ok) {
                        gameErrors++
                        if (gameErrors >= 30) {
                            runCatching { game.stop(); game.release() }
                            gameRecord = null
                            effectiveSource = resolveSource(false, micRecord != null)
                            worker.post { listener.onAudioWarning("Game audio stream paused by device") }
                        }
                    } else {
                        gameErrors = 0
                    }
                }

                val mic = micRecord
                if (mic != null && effectiveSource.microphone) {
                    val ok = readNonBlocking(mic, micPcm, framesPerChunk)
                    if (!ok) {
                        micErrors++
                        if (micErrors >= 30) {
                            runCatching { mic.stop(); mic.release() }
                            micRecord = null
                            effectiveSource = resolveSource(gameRecord != null, false)
                            worker.post { listener.onAudioWarning("Microphone stream interrupted") }
                        }
                    } else {
                        micErrors = 0
                    }
                }

                val ptsUs = (framesSubmitted * 1_000_000L) / sampleRate
                val inIndex = runCatching { aac.dequeueInputBuffer(10_000L) }.getOrDefault(-1)
                if (inIndex >= 0) {
                    val input = aac.getInputBuffer(inIndex)
                    if (input != null && input.capacity() >= bytesPerChunk) {
                        input.clear()
                        AudioMixer.mix(gamePcm, micPcm, framesPerChunk, input, effectiveSource)
                        aac.queueInputBuffer(inIndex, 0, bytesPerChunk, ptsUs, 0)
                        framesSubmitted += framesPerChunk
                    } else {
                        runCatching { aac.queueInputBuffer(inIndex, 0, 0, ptsUs, 0) }
                    }
                } else {
                    // Keep sample clock advancing cleanly even if the encoder momentarily backs up.
                    framesSubmitted += framesPerChunk
                }

                drainEncoder(aac, endOfStream = false)
            }

            // Signal clean EOS and flush remaining AAC packets.
            val eosPtsUs = (framesSubmitted * 1_000_000L) / sampleRate
            val eosIndex = runCatching { aac.dequeueInputBuffer(20_000L) }.getOrDefault(-1)
            if (eosIndex >= 0) {
                runCatching {
                    aac.queueInputBuffer(eosIndex, 0, 0, eosPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            }
            drainEncoder(aac, endOfStream = true)
        } catch (t: Throwable) {
            Log.w(tag, "Audio capture stopped with error", t)
        } finally {
            releaseRecords()
            runCatching { aac.stop() }
            runCatching { aac.release() }
            encoder = null
            reportFinished()
        }
    }

    private fun readNonBlocking(record: AudioRecord, dst: ShortArray, targetSamples: Int): Boolean {
        var total = 0
        while (total < targetSamples) {
            val read = runCatching {
                record.read(dst, total, targetSamples - total, AudioRecord.READ_NON_BLOCKING)
            }.getOrDefault(AudioRecord.ERROR_INVALID_OPERATION)
            if (read < 0) {
                dst.fill(0, total, targetSamples)
                return false
            }
            if (read == 0) break
            total += read
        }
        if (total < targetSamples) {
            dst.fill(0, total, targetSamples)
        }
        return true
    }

    private fun createAacEncoder(): MediaCodec {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bytesPerChunk * 2)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        return codec
    }

    private fun queueSilentPrimeFrame(aac: MediaCodec) {
        val inIndex = runCatching { aac.dequeueInputBuffer(25_000L) }.getOrDefault(-1)
        if (inIndex < 0) return
        val input = aac.getInputBuffer(inIndex) ?: return
        if (input.capacity() < bytesPerChunk) return
        input.clear()
        val zero = ShortArray(framesPerChunk * 2)
        AudioMixer.mix(zero, ShortArray(framesPerChunk), framesPerChunk, input, RecordingOptions.AudioSource.NONE)
        runCatching { aac.queueInputBuffer(inIndex, 0, bytesPerChunk, 0L, 0) }
    }

    private fun drainEncoder(aac: MediaCodec, endOfStream: Boolean, waitUs: Long = 0L) {
        val info = MediaCodec.BufferInfo()
        val maxLoops = if (endOfStream) 32 else 8
        for (i in 0 until maxLoops) {
            val timeout = when {
                endOfStream -> 10_000L
                i == 0 -> waitUs
                else -> 0L
            }
            val index = runCatching {
                aac.dequeueOutputBuffer(info, timeout)
            }.getOrDefault(MediaCodec.INFO_TRY_AGAIN_LATER)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = aac.outputFormat
                    worker.post { listener.onAudioFormat(format) }
                }
                index >= 0 -> {
                    val out = aac.getOutputBuffer(index)
                    val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    val isEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    if (out != null && info.size > 0 && !isConfig) {
                        val copy = ByteBuffer.allocateDirect(info.size)
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        copy.put(out)
                        copy.flip()
                        val sampleInfo = MediaCodec.BufferInfo().apply {
                            set(0, info.size, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
                        }
                        worker.post { listener.onAudioSample(copy, sampleInfo) }
                    }
                    runCatching { aac.releaseOutputBuffer(index, false) }
                    if (isEos) return
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openGameRecord(): AudioRecord? {
        if (Build.VERSION.SDK_INT < 29) return null
        val proj = projection ?: return null
        return runCatching {
            val config = AudioPlaybackCaptureConfiguration.Builder(proj)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val minBytes = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(bytesPerChunk * 4)
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBytes * 2)
                .setAudioPlaybackCaptureConfig(config)
                .build()
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        }.onFailure { Log.w(tag, "openGameRecord failed", it) }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun openMicRecord(): AudioRecord? {
        return runCatching {
            val minBytes = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(framesPerChunk * 2 * 4)
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBytes * 2
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        }.onFailure { Log.w(tag, "openMicRecord failed", it) }.getOrNull()
    }

    private fun releaseRecords() {
        runCatching { gameRecord?.stop() }
        runCatching { gameRecord?.release() }
        gameRecord = null
        runCatching { micRecord?.stop() }
        runCatching { micRecord?.release() }
        micRecord = null
    }

    private fun resolveSource(hasGame: Boolean, hasMic: Boolean): RecordingOptions.AudioSource = when {
        hasGame && hasMic -> RecordingOptions.AudioSource.BOTH
        hasGame -> RecordingOptions.AudioSource.GAME
        hasMic -> RecordingOptions.AudioSource.MICROPHONE
        else -> RecordingOptions.AudioSource.NONE
    }

    private fun pickSampleRate(): Int {
        val rate48k = AudioRecord.getMinBufferSize(
            48_000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        return if (rate48k > 0) 48_000 else 44_100
    }

    private fun reportFinished() {
        if (finishedReported) return
        finishedReported = true
        worker.post { listener.onAudioFinished() }
    }
}
