package net.kdt.pojavlaunch.recording

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.util.Log
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.instances.Instances
import java.io.File
import java.nio.file.Files
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
    private val recoveryLock = Any()

    /** Pick a non-existing `.mp4` destination without pre-creating a 0-byte placeholder. */
    private fun uniqueDestination(parent: File, baseName: String, suffix: String): File {
        var index = 0
        while (true) {
            val candidateName = if (index == 0) "$baseName$suffix" else "$baseName-$index$suffix"
            val candidate = File(parent, candidateName)
            if (!candidate.exists()) return candidate
            index++
        }
    }

    /**
     * Move [source] to [destination] without pre-creating [destination].
     *
     * Android FUSE / MediaProvider mounts across many devices fail `File.renameTo` when a
     * placeholder destination already exists (from `createNewFile()`). Fall back to `Files.move`
     * and buffered copy+delete if `renameTo` still refuses on a device mount.
     */
    private fun moveToMp4(source: File, destination: File): Boolean {
        destination.parentFile?.let { if (!it.exists()) it.mkdirs() }
        if (runCatching { source.renameTo(destination) }.getOrDefault(false)) {
            return true
        }
        if (Build.VERSION.SDK_INT >= 26) {
            val moved = runCatching {
                Files.move(source.toPath(), destination.toPath())
                true
            }.getOrDefault(false)
            if (moved) return true
        }
        return try {
            val expectedLength = source.length()
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output, 64 * 1024)
                    output.flush()
                }
            }
            if (destination.isFile && destination.length() == expectedLength) {
                runCatching { source.delete() }
                true
            } else {
                runCatching { destination.delete() }
                false
            }
        } catch (error: Exception) {
            Log.w("AerixRecorder", "Failed to move recording to ${destination.name}", error)
            runCatching { destination.delete() }
            false
        }
    }

    /** Extractor checks MP4 structure and first sync sample, not actual hardware decoding. */
    fun playable(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return false
            extractor.selectTrack(track)
            var checked = 0
            while (checked < 30) {
                val size = extractor.sampleSize
                if (size < 0) break
                if (size > 0 && (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    return true
                }
                if (!extractor.advance()) break
                checked++
            }
            false
        } catch (error: Exception) {
            Log.w("AerixRecorder", "Saved MP4 validation failed", error)
            false
        } finally { extractor.release() }
    }


    /**
     * Re-file recordings an interrupted session or earlier `.partial` rename bug left behind.
     *
     * A crash, a low-memory kill, swiping the launcher away mid-recording, or a blocked `renameTo`
     * leaves `Aerix-<stamp>.partial` in the instance's own recordings folder. Convert playable
     * `.partial` files into `.mp4` and publish them to the gallery, and convert unplayable `.partial`
     * files into `-unfinished.mp4` so no `.partial` file stays behind.
     */
    fun recoverStrays(context: Context, directory: File): List<Saved> = synchronized(recoveryLock) {
        if (!directory.isDirectory) return@synchronized emptyList()
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
                val playableFile = uniqueDestination(parent, base, ".mp4")
                if (moveToMp4(stray, playableFile)) {
                    MediaScannerConnection.scanFile(
                        context, arrayOf(playableFile.absolutePath), arrayOf("video/mp4"), null
                    )
                    val media = runCatching { RecordingLibrary.publish(context, playableFile) }.getOrNull()
                    recovered += Saved(playableFile.absolutePath, true, media)
                }
                continue
            }
            // The file died with the process, so its index never got written: no player can seek it.
            // Renaming still beats leaving a `.partial` extension behind.
            val salvage = uniqueDestination(parent, base, "-unfinished.mp4")
            if (moveToMp4(stray, salvage)) {
                recovered += Saved(salvage.absolutePath, false)
            }
        }
        recovered
    }

    /** Scan every instance's `recordings/` folder and convert any stray `.partial` files to `.mp4`. */
    fun recoverAllInstances(context: Context): List<Saved> {
        runCatching { Tools.ensureStorageConstantsIfUnset() }
        val dirs = LinkedHashSet<File>()
        runCatching {
            Instances.getInstancePath()?.listFiles()?.forEach { instanceDir ->
                if (instanceDir.isDirectory) {
                    val rec = File(instanceDir, "recordings")
                    if (rec.isDirectory) dirs += rec
                }
            }
        }
        runCatching {
            val sharedRec = File(Instances.getSharedDataDirectory(), "recordings")
            if (sharedRec.isDirectory) dirs += sharedRec
        }
        if (dirs.isEmpty()) return emptyList()
        val all = ArrayList<Saved>()
        for (dir in dirs) {
            runCatching { all += recoverStrays(context, dir) }
        }
        return all
    }
    }

    fun finish(success: Boolean): Saved? {
        val source = temporary ?: return null
        temporary = null
        if (!source.isFile || source.length() == 0L) { source.delete(); return null }
        val containerPlayable = validVideo(source)
        if (!success && containerPlayable) {
            Log.w("AerixRecorder", "Muxer reported stop failure, but MP4 container is playable; saving as .mp4")
        }
        val valid = containerPlayable
        val parent = source.parentFile ?: instanceRoot
        val baseName = source.name.removeSuffix(".partial")
        val destination = uniqueDestination(parent, baseName, if (valid) ".mp4" else "-incomplete.mp4")
        if (!moveToMp4(source, destination)) return Saved(source.absolutePath, false)
        if (valid) MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf("video/mp4"), null)
        // The instance folder is app-private on Android 10 and up, so publishing a copy into
        // Movies/Aerix is what makes the video openable, shareable and visible in a gallery.
        val media = if (valid) runCatching { RecordingLibrary.publish(context, destination) }
            .onFailure { Log.w("AerixRecorder", "Recording could not be published", it) }
            .getOrNull() else null
        return Saved(destination.absolutePath, valid, media)
    }
}
