/*
 * Aerix Blocky Shell - blocky sound.
 *
 * Every sound is a square wave quantised to 4-bit steps with a four-step (not smooth) envelope,
 * so taps, emotes, transitions and rewards all sound like a chiptune. The WAVs are synthesised
 * once into cacheDir and played by SoundPool: no binary assets, no download size, no audio focus
 * stolen from Minecraft.
 */
package net.kdt.pojavlaunch.aerix.ui.blocky

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin

object BlockySound {
    private const val SAMPLE_RATE = 22_050
    private const val VERSION = "v1"

    enum class Kind {
        TAP, OPEN, CLOSE, STEP, WAVE, DANCE, JUMP, ACTION, CHEER, REWARD, LAUNCH, ERROR
    }

    private val started = AtomicBoolean(false)
    private val loaded = AtomicInteger(0)

    @Volatile private var pool: SoundPool? = null
    @Volatile private var ready = false
    private val ids = IntArray(Kind.values().size)

    private fun notes(kind: Kind): List<Pair<Double, Int>> = when (kind) {
        Kind.TAP -> listOf(660.0 to 26, 880.0 to 22)
        Kind.OPEN -> listOf(330.0 to 30, 494.0 to 30, 660.0 to 46)
        Kind.CLOSE -> listOf(660.0 to 30, 494.0 to 30, 330.0 to 44)
        Kind.STEP -> listOf(220.0 to 22)
        Kind.WAVE -> listOf(523.0 to 60, 659.0 to 60, 784.0 to 90)
        Kind.DANCE -> listOf(392.0 to 48, 523.0 to 48, 659.0 to 48, 523.0 to 48, 784.0 to 90)
        Kind.JUMP -> listOf(294.0 to 34, 440.0 to 34, 587.0 to 60, 880.0 to 90)
        Kind.ACTION -> listOf(196.0 to 34, 294.0 to 34, 196.0 to 34, 392.0 to 70)
        Kind.CHEER -> listOf(523.0 to 46, 659.0 to 46, 784.0 to 46, 1046.0 to 120)
        Kind.REWARD -> listOf(523.0 to 55, 659.0 to 55, 784.0 to 55, 1046.0 to 55, 1318.0 to 150)
        Kind.LAUNCH -> listOf(220.0 to 40, 330.0 to 40, 440.0 to 40, 587.0 to 40, 880.0 to 130)
        Kind.ERROR -> listOf(220.0 to 70, 165.0 to 110)
    }

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        Executors.newSingleThreadExecutor().execute {
            runCatching {
                val dir = File(app.cacheDir, "aerix_blocky_sfx_$VERSION").apply { mkdirs() }
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val p = SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(attrs)
                    .build()
                p.setOnLoadCompleteListener { _, _, status ->
                    if (status == 0 && loaded.incrementAndGet() >= Kind.values().size) ready = true
                }
                for (kind in Kind.values()) {
                    val file = File(dir, kind.name.lowercase(Locale.ROOT) + ".wav")
                    if (!file.isFile || file.length() < 64L) writeWav(file, synth(kind))
                    ids[kind.ordinal] = p.load(file.absolutePath, 1)
                }
                pool = p
            }
        }
    }

    fun play(kind: Kind) {
        if (!AerixPreferences.blockySound || !ready) return
        val id = ids[kind.ordinal]
        if (id == 0) return
        runCatching { pool?.play(id, 0.36f, 0.36f, 1, 0, 1.0f) }
    }

    /** The emote engine's id -> its own blocky jingle. */
    fun playEmote(emote: String) {
        play(
            when (emote) {
                "wave" -> Kind.WAVE
                "dance" -> Kind.DANCE
                "jump" -> Kind.JUMP
                "cheer" -> Kind.CHEER
                else -> Kind.ACTION
            }
        )
    }

    /** Crunchy 4-bit square wave with a stepped envelope - no smooth fades anywhere. */
    private fun synth(kind: Kind): ShortArray {
        val sequence = notes(kind)
        val total = sequence.sumOf { (SAMPLE_RATE * it.second / 1000.0).toInt() }
        val out = ShortArray(total)
        val envelopes = doubleArrayOf(0.9, 0.68, 0.44, 0.2)
        var cursor = 0
        for ((freq, millis) in sequence) {
            val count = (SAMPLE_RATE * millis / 1000.0).toInt()
            var phase = 0.0
            for (i in 0 until count) {
                val progress = i.toDouble() / count
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val square = if (sin(phase) >= 0.0) 1.0 else -1.0
                val level = envelopes[(progress * 4.0).toInt().coerceAtMost(3)]
                val value = (Math.round(square * level * 8.0) / 8.0).coerceIn(-1.0, 1.0)
                out[cursor + i] = (value * Short.MAX_VALUE).toInt().toShort()
            }
            cursor += count
        }
        return out
    }

    private fun writeWav(file: File, samples: ShortArray) {
        val dataSize = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(1)
        buf.putInt(SAMPLE_RATE)
        buf.putInt(SAMPLE_RATE * 2)
        buf.putShort(2)
        buf.putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (sample in samples) buf.putShort(sample)
        FileOutputStream(file).use { it.write(buf.array()) }
    }
}
