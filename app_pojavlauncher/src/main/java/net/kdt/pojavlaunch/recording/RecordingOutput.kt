package net.kdt.pojavlaunch.recording

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mux to a temporary file in the actual instance; retain incomplete data for recovery. */
internal class RecordingOutput(private val context: Context, private val instanceRoot: File) {
    data class Saved(val path: String, val playable: Boolean)
    private var temporary: File? = null

    fun open(): MediaMuxer {
        val directory = RecordingPaths.directory(instanceRoot, true)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date())
        val target = File.createTempFile("Aerix-$stamp-", ".partial", directory)
        temporary = target
        return MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /** Extractor checks MP4 structure and first sync sample, not actual hardware decoding. */
    private fun validVideo(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return false
            extractor.selectTrack(track)
            extractor.sampleSize > 0 && extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
        } catch (error: Exception) {
            Log.w("AerixRecorder", "Saved MP4 validation failed", error)
            false
        } finally { extractor.release() }
    }

    fun finish(success: Boolean): Saved? {
        val source = temporary ?: return null
        temporary = null
        if (!source.isFile || source.length() == 0L) { source.delete(); return null }
        val valid = success && validVideo(source)
        val name = source.name.removeSuffix(".partial") + if (valid) ".mp4" else "-incomplete.mp4"
        val destination = File(source.parentFile, name)
        // Reserve exclusively; never intentionally replace another recording.
        if (!destination.createNewFile()) return Saved(source.absolutePath, false)
        if (!source.renameTo(destination)) { destination.delete(); return Saved(source.absolutePath, false) }
        if (valid) MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf("video/mp4"), null)
        return Saved(destination.absolutePath, valid)
    }
}
