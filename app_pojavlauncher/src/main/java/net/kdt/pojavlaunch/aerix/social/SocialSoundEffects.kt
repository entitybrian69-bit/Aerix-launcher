package net.kdt.pojavlaunch.aerix.social

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Low-latency Messenger-style UI sound engine powered by Android's hardware-mixed [SoundPool].
 *
 * Synthesizes sculpted 44.1 kHz 16-bit PCM acoustic waveforms once into `cacheDir/aerix_social_sfx`
 * so playback takes < 5 ms, never steals audio focus from music/Minecraft, and adds 0 KB of binary
 * asset bloat to the repository.
 */
object SocialSoundEffects {
    private const val SAMPLE_RATE = 44_100
    private const val SFX_VERSION = "v2"

    private val initialized = AtomicBoolean(false)
    private val initExecutor = Executors.newSingleThreadExecutor()

    @Volatile private var soundPool: SoundPool? = null
    @Volatile private var tapSoundId: Int = 0
    @Volatile private var peerTypingSoundId: Int = 0
    @Volatile private var sentSoundId: Int = 0
    @Volatile private var receivedSoundId: Int = 0
    @Volatile private var seenSoundId: Int = 0
    @Volatile private var statusSoundId: Int = 0
    @Volatile private var ready: Boolean = false

    private val lastTapMs = AtomicLong(0L)
    private val lastPeerTypingMs = AtomicLong(0L)
    private val lastSeenMs = AtomicLong(0L)
    private val lastReceivedMs = AtomicLong(0L)

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val app = context.applicationContext
        initExecutor.execute {
            runCatching {
                val dir = File(app.cacheDir, "aerix_social_sfx_$SFX_VERSION").apply { mkdirs() }
                val tapFile = ensureWav(dir, "tap.wav") { buildKeyTapPcm() }
                val peerFile = ensureWav(dir, "peer_typing.wav") { buildPeerTypingPcm() }
                val sentFile = ensureWav(dir, "sent.wav") { buildMessageSentPcm() }
                val recvFile = ensureWav(dir, "received.wav") { buildMessageReceivedPcm() }
                val seenFile = ensureWav(dir, "seen.wav") { buildMessageSeenPcm() }
                val statusFile = ensureWav(dir, "status.wav") { buildStatusChangePcm() }

                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val pool = SoundPool.Builder()
                    .setMaxStreams(6)
                    .setAudioAttributes(attrs)
                    .build()

                var loadedCount = 0
                pool.setOnLoadCompleteListener { _, _, status ->
                    if (status == 0) {
                        loadedCount++
                        if (loadedCount >= 6) ready = true
                    }
                }

                tapSoundId = pool.load(tapFile.absolutePath, 1)
                peerTypingSoundId = pool.load(peerFile.absolutePath, 1)
                sentSoundId = pool.load(sentFile.absolutePath, 1)
                receivedSoundId = pool.load(recvFile.absolutePath, 1)
                seenSoundId = pool.load(seenFile.absolutePath, 1)
                statusSoundId = pool.load(statusFile.absolutePath, 1)
                soundPool = pool
            }
        }
    }

    /** Soft capacitive Messenger key-tap tick while typing. */
    fun playTypingTick() {
        if (!SocialBackend.chatSoundsEnabled || !ready) return
        val now = System.currentTimeMillis()
        val prev = lastTapMs.get()
        if (now - prev < 38L) return
        lastTapMs.set(now)
        val pitchJitter = 0.96f + Random.nextFloat() * 0.08f
        play(tapSoundId, volume = 0.28f, rate = pitchJitter)
    }

    /** Soft airy bubble bloop when seeing the other person start or continue typing. */
    fun playPeerTyping() {
        if (!SocialBackend.chatSoundsEnabled || !ready) return
        val now = System.currentTimeMillis()
        val prev = lastPeerTypingMs.get()
        if (now - prev < 2_200L) return
        lastPeerTypingMs.set(now)
        play(peerTypingSoundId, volume = 0.36f, rate = 1.0f)
    }

    /** Crisp upward Messenger "pop-whoosh" when sending a message. */
    fun playMessageSent() {
        if (!SocialBackend.chatSoundsEnabled || !ready) return
        play(sentSoundId, volume = 0.52f, rate = 1.0f)
    }

    /** Warm Messenger two-note chime when a reply arrives. */
    fun playMessageReceived() {
        if (!SocialBackend.chatSoundsEnabled || !ready) return
        val now = System.currentTimeMillis()
        val prev = lastReceivedMs.get()
        if (now - prev < 180L) return
        lastReceivedMs.set(now)
        play(receivedSoundId, volume = 0.62f, rate = 1.0f)
    }

    /** Delicate high crystalline droplet when a sent message turns `✓✓ Seen`. */
    fun playMessageSeen() {
        if (!SocialBackend.chatSoundsEnabled || !ready) return
        val now = System.currentTimeMillis()
        val prev = lastSeenMs.get()
        if (now - prev < 450L) return
        lastSeenMs.set(now)
        play(seenSoundId, volume = 0.42f, rate = 1.0f)
    }

    /** Subtle glass tone when switching status mode. */
    fun playStatusChanged() {
        if (!SocialBackend.chatSoundsEnabled || !ready) return
        play(statusSoundId, volume = 0.38f, rate = 1.0f)
    }

    private fun play(soundId: Int, volume: Float, rate: Float) {
        if (soundId == 0) return
        runCatching {
            soundPool?.play(soundId, volume, volume, 1, 0, rate)
        }
    }

    private fun ensureWav(dir: File, name: String, generator: () -> ShortArray): File {
        val out = File(dir, name)
        if (out.isFile && out.length() > 64L) return out
        val pcm = generator()
        writeMono16Wav(out, pcm)
        return out
    }

    private fun writeMono16Wav(file: File, samples: ShortArray) {
        val dataSize = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16) // PCM chunk size
        buf.putShort(1) // AudioFormat = 1 (PCM)
        buf.putShort(1) // NumChannels = 1 (Mono)
        buf.putInt(SAMPLE_RATE)
        buf.putInt(SAMPLE_RATE * 2) // ByteRate
        buf.putShort(2) // BlockAlign
        buf.putShort(16) // BitsPerSample
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (s in samples) buf.putShort(s)
        FileOutputStream(file).use { it.write(buf.array()) }
    }

    /** 22 ms warm woodblock/capacitive tap. */
    private fun buildKeyTapPcm(): ShortArray {
        val total = (SAMPLE_RATE * 0.022).toInt()
        val out = ShortArray(total)
        var phase = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 920.0 * exp(-t * 95.0) + 210.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = sin(PI * (i.toDouble() / total).coerceIn(0.0, 1.0)).pow(0.35) * exp(-t * 145.0)
            val sample = (sin(phase) * 0.78 + sin(phase * 2.3) * 0.22) * env * 0.55
            out[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** 135 ms Messenger-style soft double bubble bloop when peer starts typing. */
    private fun buildPeerTypingPcm(): ShortArray {
        val total = (SAMPLE_RATE * 0.135).toInt()
        val out = ShortArray(total)
        var phase1 = 0.0
        var phase2 = 0.0
        val secondOffset = (SAMPLE_RATE * 0.055).toInt()
        for (i in 0 until total) {
            var v = 0.0
            // Bubble 1: 370 -> 510 Hz
            val t1 = i.toDouble() / SAMPLE_RATE
            if (t1 < 0.065) {
                val f1 = 370.0 + (510.0 - 370.0) * (t1 / 0.065).coerceIn(0.0, 1.0)
                phase1 += 2.0 * PI * f1 / SAMPLE_RATE
                val env1 = sin(PI * (t1 / 0.065)) * exp(-t1 * 18.0)
                v += sin(phase1) * env1 * 0.48
            }
            // Bubble 2: 460 -> 640 Hz
            if (i >= secondOffset) {
                val t2 = (i - secondOffset).toDouble() / SAMPLE_RATE
                if (t2 < 0.072) {
                    val f2 = 460.0 + (640.0 - 460.0) * (t2 / 0.072).coerceIn(0.0, 1.0)
                    phase2 += 2.0 * PI * f2 / SAMPLE_RATE
                    val env2 = sin(PI * (t2 / 0.072)) * exp(-t2 * 16.0)
                    v += sin(phase2) * env2 * 0.55
                }
            }
            out[i] = (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** 95 ms Messenger upward "pop-whoosh" on message send (460 Hz -> 1120 Hz). */
    private fun buildMessageSentPcm(): ShortArray {
        val total = (SAMPLE_RATE * 0.095).toInt()
        val out = ShortArray(total)
        var phase = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = (i.toDouble() / total).coerceIn(0.0, 1.0)
            val freq = 460.0 + (1120.0 - 460.0) * progress.pow(1.45)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val attack = (t / 0.008).coerceIn(0.0, 1.0)
            val decay = (1.0 - progress).pow(0.75) * exp(-t * 14.0)
            val env = attack * decay
            val sample = (sin(phase) * 0.85 + sin(phase * 2.0) * 0.15) * env * 0.72
            out[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** 195 ms Messenger warm two-tone incoming message chime (G5 784 Hz -> D6 1174.66 Hz). */
    private fun buildMessageReceivedPcm(): ShortArray {
        val total = (SAMPLE_RATE * 0.195).toInt()
        val out = ShortArray(total)
        val note2Start = (SAMPLE_RATE * 0.068).toInt()
        var p1 = 0.0
        var p2 = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / SAMPLE_RATE
            p1 += 2.0 * PI * 783.99 / SAMPLE_RATE
            val a1 = (t / 0.005).coerceIn(0.0, 1.0)
            val e1 = a1 * exp(-t * 19.0)
            var v = (sin(p1) * 0.82 + sin(p1 * 2.0) * 0.18) * e1 * 0.52

            if (i >= note2Start) {
                val t2 = (i - note2Start).toDouble() / SAMPLE_RATE
                p2 += 2.0 * PI * 1174.66 / SAMPLE_RATE
                val a2 = (t2 / 0.005).coerceIn(0.0, 1.0)
                val e2 = a2 * exp(-t2 * 16.5)
                v += (sin(p2) * 0.84 + sin(p2 * 2.0) * 0.16) * e2 * 0.64
            }
            // Smooth tail fade on final 6ms
            val tail = ((total - i).toDouble() / (SAMPLE_RATE * 0.006)).coerceIn(0.0, 1.0)
            out[i] = ((v * tail).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** 68 ms delicate Messenger "Seen" droplet (1180 Hz -> 1620 Hz). */
    private fun buildMessageSeenPcm(): ShortArray {
        val total = (SAMPLE_RATE * 0.068).toInt()
        val out = ShortArray(total)
        var phase = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / SAMPLE_RATE
            val p = (i.toDouble() / total).coerceIn(0.0, 1.0)
            val freq = 1180.0 + (1620.0 - 1180.0) * p.pow(1.2)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = (0.5 - 0.5 * cos(2.0 * PI * p.coerceAtMost(0.5))) * exp(-t * 28.0)
            val sample = sin(phase) * env * 0.56
            out[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** 115 ms gentle glass shimmer for status switch. */
    private fun buildStatusChangePcm(): ShortArray {
        val total = (SAMPLE_RATE * 0.115).toInt()
        val out = ShortArray(total)
        var p1 = 0.0
        var p2 = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / SAMPLE_RATE
            p1 += 2.0 * PI * 659.25 / SAMPLE_RATE // E5
            p2 += 2.0 * PI * 987.77 / SAMPLE_RATE // B5
            val attack = (t / 0.006).coerceIn(0.0, 1.0)
            val env = attack * exp(-t * 22.0)
            val v = (sin(p1) * 0.52 + sin(p2) * 0.48) * env * 0.52
            out[i] = (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
