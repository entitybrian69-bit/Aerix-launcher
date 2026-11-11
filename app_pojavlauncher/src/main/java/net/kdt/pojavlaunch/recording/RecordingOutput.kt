package net.kdt.pojavlaunch.recording

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mux to a temporary file in the actual instance; retain incomplete data for recovery. */
internal class RecordingOutput(private val context: Context, private val instanceRoot: File) {
    /** [mediaUri] is the copy in the system media library, which is what other apps can open. */
    data class Saved(val path: String, val playable: Boolean, val mediaUri: Uri? = null)
    private var temporary: File? = null

    fun open(): MediaMuxer {
        val directory = RecordingPaths.directory(instanceRoot, true)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date())
        val target = File.createTempFile("Aerix-$stamp-", ".partial", directory)
        temporary = target
        return MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /** Extractor checks MP4 structure and first sync sample, not actual hardware decoding. */
    private fun validVideo(file: File): Boolean = playable(file)

    companion object {
    /** Extractor checks MP4 structure and first sync sample, not actual hardware decoding. */
    fun playable(file: File): Boolean {
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


    /**
     * Re-file recordings an interrupted session left behind.
     *
     * A crash, a low-memory kill, or swiping the launcher away mid-recording leaves
     * `Aerix-<stamp>.partial` in the instance's own recordings folder. Nothing lists that name, and
     * a file manager that has never seen the extension offers to unpack it as an archive - which is
     * exactly how "they recorded a video and it came out as some zip file" reached us. Nothing may
     * be left with an extension that lies about what it is: re-publish it as an MP4 when the
     * structure survived, and name it honestly when it did not.
     */
    fun recoverStrays(context: Context, directory: File): List<Saved> {
        val cutoff = System.currentTimeMillis() - 5_000L
        val strays = runCatching {
            directory.listFiles()?.filter {
                it.isFile && it.name.endsWith(".partial") && it.lastModified() < cutoff
            }?.sortedBy { it.lastModified() }.orEmpty()
        }.getOrNull().orEmpty()
        val recovered = ArrayList<Saved>(strays.size)
        for (stray in strays) {
            if (stray.length() == 0L) { runCatching { stray.delete() }; continue }
            val base = stray.name.removeSuffix(".partial")
            val parent = stray.parentFile ?: continue
            if (playable(stray)) {
                val playableFile = File(parent, "$base.mp4")
                if (runCatching { stray.renameTo(playableFile) }.getOrDefault(false)) {
                    MediaScannerConnection.scanFile(
                        context, arrayOf(playableFile.absolutePath), arrayOf("video/mp4"), null
                    )
                    val media = runCatching { RecordingLibrary.publish(context, playableFile) }.getOrNull()
                    recovered += Saved(playableFile.absolutePath, true, media)
                }
                continue
            }
            // The file died with the process, so its index never got written: no player can seek it.
            // Renaming still beats leaving an extension that invites the archive tooling.
            val salvage = File(parent, "$base-unfinished.mp4")
            if (runCatching { stray.renameTo(salvage) }.getOrDefault(false)) {
                recovered += Saved(salvage.absolutePath, false)
            }
        }
        return recovered
    }
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
        // The instance folder is app-private on Android 10 and up, so publishing a copy into
        // Movies/Aerix is what makes the video openable, shareable and visible in a gallery.
        val media = if (valid) runCatching { RecordingLibrary.publish(context, destination) }
            .onFailure { Log.w("AerixRecorder", "Recording could not be published", it) }
            .getOrNull() else null
        return Saved(destination.absolutePath, valid, media)
    }
}
