package net.kdt.pojavlaunch.recording

/** Surface frames use a monotonic clock; AAC is fed session-relative PCM timestamps. */
internal class RecordingTimestamps(private val epochUs: Long) {
    private var videoBase: Long? = null
    private var lastVideo = -1L
    private var lastAudio = -1L
    fun video(raw: Long): Long {
        if (videoBase == null) videoBase = raw
        // Anchor directly to the encoder surface's own clock so sleep-skewed uptime/boottime clocks never clamp frames to 0.
        lastVideo = maxOf(lastVideo + 1, (raw - videoBase!!).coerceAtLeast(0))
        return lastVideo
    }
    fun audio(relative: Long): Long {
        // Some AAC encoders expose negative priming timestamps. MediaMuxer rejects negative PTS.
        lastAudio = maxOf(lastAudio + 1, relative.coerceAtLeast(0))
        return lastAudio
    }
}
