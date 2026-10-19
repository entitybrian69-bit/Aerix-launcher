package net.kdt.pojavlaunch.recording

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import kotlin.math.roundToInt

/** Values are independent; an encoder must support the exact size/rate pair before consent. */
object RecordingOptions {
    val qualities = listOf(240, 360, 480, 720, 1080)
    val frameRates = listOf(15, 24, 30, 48, 60, 90, 120)
    const val DEFAULT_QUALITY = 480
    const val DEFAULT_FPS = 24

    data class Size(val width: Int, val height: Int)
    data class Config(val quality: Int, val fps: Int, val width: Int, val height: Int,
                      val bitrate: Int, val encoder: String)

    fun size(screenWidth: Int, screenHeight: Int, quality: Int, widthAlignment: Int, heightAlignment: Int): Size {
        require(screenWidth > 0 && screenHeight > 0 && quality in qualities)
        require(widthAlignment > 0 && heightAlignment > 0)
        val scale = quality.toDouble() / minOf(screenWidth, screenHeight)
        fun align(value: Double, alignment: Int) = maxOf(alignment, (value.toInt() / alignment) * alignment)
        return Size(align(screenWidth * scale, widthAlignment), align(screenHeight * scale, heightAlignment))
    }

    fun bitrate(width: Int, height: Int, fps: Int): Int =
        (width.toDouble() * height * fps * 0.12).roundToInt().coerceIn(300_000, 40_000_000)

    fun hardware(name: String, reportedHardware: Boolean?, reportedSoftware: Boolean?): Boolean {
        if (reportedSoftware == true || reportedHardware == false) return false
        val lower = name.lowercase(java.util.Locale.ROOT)
        return !lower.startsWith("omx.google.") && !lower.startsWith("c2.android.") &&
            !lower.contains("software") && !lower.contains(".sw.") && !lower.startsWith("omx.ffmpeg.") && (reportedHardware == true || lower.startsWith("omx.") || lower.startsWith("c2."))
    }

    /** Query off the UI thread. No software fallback that could steal CPU from Minecraft. */
    fun supported(screenWidth: Int, screenHeight: Int): List<Config> {
        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { codec ->
            codec.isEncoder && codec.supportedTypes.any { it.equals("video/avc", true) } &&
                hardware(codec.name, if (Build.VERSION.SDK_INT >= 29) codec.isHardwareAccelerated else null,
                    if (Build.VERSION.SDK_INT >= 29) codec.isSoftwareOnly else null)
        }
        return qualities.flatMap { quality -> frameRates.mapNotNull { fps ->
            codecs.firstNotNullOfOrNull { codec -> runCatching {
                val caps = codec.getCapabilitiesForType("video/avc")
                if (MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface !in caps.colorFormats) return@runCatching null
                val video = caps.videoCapabilities ?: return@runCatching null
                val size = size(screenWidth, screenHeight, quality, video.widthAlignment, video.heightAlignment)
                if (!video.areSizeAndRateSupported(size.width, size.height, fps.toDouble())) return@runCatching null
                Config(quality, fps, size.width, size.height,
                    video.bitrateRange.clamp(bitrate(size.width, size.height, fps)), codec.name)
            }.getOrNull() }
        } }
    }
}
