package net.kdt.pojavlaunch.recording

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/**
 * Hardware-first encoder discovery and preset geometry for the redesigned zero-copy recorder.
 *
 * Frame rates are restricted to smooth display-friendly cadences (24/30/48/60 FPS) with balanced
 * H.264 bitrates so mobile memory bandwidth and GPU encoders are never saturated while gaming.
 */
object RecordingOptions {
    const val DEFAULT_QUALITY = 720
    const val DEFAULT_FPS = 30
    const val MIN_BITRATE = 500_000L
    const val MAX_BITRATE = 16_000_000L

    val qualities = listOf(240, 360, 480, 720, 1080)
    val frameRates = listOf(24, 30, 48, 60)

    enum class AudioSource(val label: String, val game: Boolean, val microphone: Boolean) {
        GAME("Game audio", true, false),
        MICROPHONE("Microphone", false, true),
        BOTH("Game audio + mic", true, true),
        NONE("No audio", false, false);

        companion object {
            fun available(sdkInt: Int = Build.VERSION.SDK_INT): List<AudioSource> =
                if (sdkInt >= 29) entries else listOf(MICROPHONE, NONE)

            fun defaultForApi(sdkInt: Int = Build.VERSION.SDK_INT): AudioSource =
                if (sdkInt >= 29) GAME else MICROPHONE

            fun saved(name: String?, sdkInt: Int = Build.VERSION.SDK_INT): AudioSource {
                val parsed = entries.firstOrNull { it.name == name }
                if (parsed != null && parsed in available(sdkInt)) return parsed
                return defaultForApi(sdkInt)
            }
        }
    }

    data class Size(val width: Int, val height: Int)

    data class Config(
        val quality: Int,
        val fps: Int,
        val width: Int,
        val height: Int,
        val bitrate: Long,
        val encoder: String,
        val hardwareAccelerated: Boolean = true
    ) {
        val label: String
            get() = "${quality}p · ${fps} FPS"
    }

    @Volatile
    private var cachedKey: Pair<Int, Int>? = null
    @Volatile
    private var cachedConfigs: List<Config> = emptyList()

    fun size(screenWidth: Int, screenHeight: Int, quality: Int, alignW: Int, alignH: Int): Size {
        require(screenWidth > 0 && screenHeight > 0) { "Screen size must be positive" }
        require(quality in qualities) { "Unknown quality: $quality" }
        require(alignW > 0 && alignH > 0) { "Alignment must be positive" }

        val wStep = maxOf(2, alignW)
        val hStep = maxOf(2, alignH)
        val landscape = screenWidth >= screenHeight
        val shortSide = minOf(screenWidth, screenHeight)
        val longSide = maxOf(screenWidth, screenHeight)
        // Never upscale past the real display size, and preserve the screen aspect ratio.
        val targetShort = minOf(quality, shortSide)
        val targetLong = (targetShort.toLong() * longSide / shortSide).toInt().coerceAtLeast(targetShort)

        val rawW = if (landscape) targetLong else targetShort
        val rawH = if (landscape) targetShort else targetLong
        val alignedW = ((rawW / wStep) * wStep).coerceAtLeast(wStep)
        val alignedH = ((rawH / hStep) * hStep).coerceAtLeast(hStep)
        return Size(alignedW, alignedH)
    }

    fun bitrate(width: Int, height: Int, fps: Int): Long {
        val raw = (width.toLong() * height * fps * 0.08).toLong()
        return raw.coerceIn(MIN_BITRATE, MAX_BITRATE)
    }

    fun hardware(name: String, isHardwareAccelerated: Boolean?, isSoftwareOnly: Boolean?): Boolean {
        val lower = name.lowercase()
        if (lower.startsWith("omx.google.") ||
            lower.startsWith("c2.android.") ||
            lower.contains(".sw.") ||
            lower.contains("avc.sw") ||
            lower.contains("ffmpeg") ||
            lower == "unknown"
        ) {
            return false
        }
        if (isSoftwareOnly == true || isHardwareAccelerated == false) return false
        if (isHardwareAccelerated == true) return true
        return lower.startsWith("omx.") || lower.startsWith("c2.") || lower.contains("encoder")
    }

    fun bestMatch(configs: List<Config>, preferredQuality: Int, preferredFps: Int): Config? {
        if (configs.isEmpty()) return null
        configs.firstOrNull { it.quality == preferredQuality && it.fps == preferredFps }?.let { return it }
        configs.filter { it.quality <= preferredQuality && it.fps <= preferredFps }
            .maxWithOrNull(compareBy<Config> { it.quality }.thenBy { it.fps })
            ?.let { return it }
        return configs.firstOrNull { it.quality == DEFAULT_QUALITY && it.fps == DEFAULT_FPS }
            ?: configs.firstOrNull { it.quality == 480 && it.fps == 30 }
            ?: configs.first()
    }

    fun supported(screenWidth: Int, screenHeight: Int): List<Config> {
        if (screenWidth <= 0 || screenHeight <= 0) return emptyList()
        val key = screenWidth to screenHeight
        cachedKey?.let { if (it == key && cachedConfigs.isNotEmpty()) return cachedConfigs }

        val encoders = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
        }.getOrNull().orEmpty()

        val hwEncoders = encoders.filter { info ->
            val hw = if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else null
            val sw = if (Build.VERSION.SDK_INT >= 29) info.isSoftwareOnly else null
            hardware(info.name, hw, sw)
        }

        val results = probeEncoders(screenWidth, screenHeight, hwEncoders, isHardware = true, maxQuality = 1080, maxFps = 60)
            .ifEmpty {
                // Only if a device genuinely has zero hardware AVC encoders, allow a capped fallback
                // (max 480p30) so the recorder still works instead of hard-failing.
                probeEncoders(screenWidth, screenHeight, encoders, isHardware = false, maxQuality = 480, maxFps = 30)
            }

        cachedKey = key
        cachedConfigs = results
        return results
    }

    private fun probeEncoders(
        screenWidth: Int,
        screenHeight: Int,
        encoders: List<MediaCodecInfo>,
        isHardware: Boolean,
        maxQuality: Int,
        maxFps: Int
    ): List<Config> {
        val picked = LinkedHashMap<Pair<Int, Int>, Config>()
        for (info in encoders) {
            val caps = runCatching { info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC) }.getOrNull() ?: continue
            if (!caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)) continue
            val video = caps.videoCapabilities ?: continue
            val alignW = video.widthAlignment.coerceAtLeast(2)
            val alignH = video.heightAlignment.coerceAtLeast(2)

            for (quality in qualities) {
                if (quality > maxQuality) continue
                val targetSize = runCatching { size(screenWidth, screenHeight, quality, alignW, alignH) }.getOrNull()
                    ?: continue
                for (fps in frameRates) {
                    if (fps > maxFps) continue
                    val pair = quality to fps
                    if (pair in picked) continue
                    val supported = runCatching {
                        video.isSizeSupported(targetSize.width, targetSize.height) &&
                            video.areSizeAndRateSupported(targetSize.width, targetSize.height, fps.toDouble())
                    }.getOrDefault(false)
                    if (!supported) continue

                    val range = video.bitrateRange
                    val bps = bitrate(targetSize.width, targetSize.height, fps)
                        .coerceIn(range.lower.toLong(), range.upper.toLong())
                    picked[pair] = Config(
                        quality = quality,
                        fps = fps,
                        width = targetSize.width,
                        height = targetSize.height,
                        bitrate = bps,
                        encoder = info.name,
                        hardwareAccelerated = isHardware
                    )
                }
            }
        }
        return picked.values.sortedWith(compareBy<Config> { it.quality }.thenBy { it.fps })
    }
}
