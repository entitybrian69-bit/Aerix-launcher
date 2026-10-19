package net.kdt.pojavlaunch.recording

import android.content.ContentValues
import android.content.Context
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Streams directly to the final file. Pending MediaStore rows are exposed only after MP4 finalization. */
internal class RecordingOutput(private val context: Context) {
    private var uri: Uri? = null
    private var file: File? = null
    private var descriptor: ParcelFileDescriptor? = null
    private val name = "Aerix-${SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date())}.mp4"

    fun open(): MediaMuxer {
        if (Build.VERSION.SDK_INT >= 29) {
            uri = requireNotNull(context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Aerix")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                })) { "Cannot create recording in Movies/Aerix" }
            descriptor = requireNotNull(context.contentResolver.openFileDescriptor(uri!!, "rw"))
            return MediaMuxer(descriptor!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
        val directory = File(requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)), "Aerix")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create recording folder" }
        file = File(directory, name)
        return MediaMuxer(file!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun finish(success: Boolean): String {
        runCatching { descriptor?.close() }
        descriptor = null
        if (!success) {
            uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            file?.delete()
            return ""
        }
        uri?.let {
            check(context.contentResolver.update(it, ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }, null, null) > 0) { "Cannot publish recording" }
            return "Movies/Aerix/$name"
        }
        val path = requireNotNull(file).absolutePath
        MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("video/mp4"), null)
        return path
    }
}
