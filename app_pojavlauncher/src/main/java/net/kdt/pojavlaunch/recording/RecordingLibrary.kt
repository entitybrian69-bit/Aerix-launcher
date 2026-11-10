package net.kdt.pojavlaunch.recording

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import net.kdt.pojavlaunch.Tools
import java.io.File

/**
 * Makes a finished recording something a player can actually open.
 *
 * The MP4 lives inside the instance folder, which on Android 10 and later is app-private storage:
 * other apps cannot read it, so "Open video" looked like a broken button. A playable copy is therefore
 * published into the system media library under `Movies/Aerix`, and the `content://` URI that the
 * media provider hands back is what players, the Files app and share sheets get to use.
 *
 * Every step is best effort: if the media provider refuses (a read-only external mount, a work
 * profile, a full disk), the file is still on disk and the caller falls back to the launcher's own
 * document provider, exactly as before.
 */
object RecordingLibrary {
    private const val TAG = "AerixRecorder"
    private const val ALBUM = "Aerix"

    /** @return the media URI of a published copy of [file], or null when publishing was impossible. */
    fun publish(context: Context, file: File): Uri? {
        if (!file.isFile || file.length() <= 0L) return null
        findByPath(context, file.absolutePath)?.let { return it }
        if (Build.VERSION.SDK_INT >= 29) return copyIntoMediaStore(context, file)
        return copyToPublicDirectoryAndScan(context, file)
    }

    /**
     * Opens or shares the recording. The media copy is preferred because every app understands a
     * media URI; the launcher's document URI is the fallback for files that could not be published.
     */
    fun view(context: Context, file: File, share: Boolean) {
        val media = runCatching { publish(context, file) }.getOrNull()
        if (media != null) {
            val opened = runCatching {
                val intent = if (share) {
                    Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, media)
                } else {
                    Intent(Intent.ACTION_VIEW).setDataAndType(media, "video/mp4")
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return
        }
        runCatching { Tools.openPath(context, file, share) }.onFailure { error ->
            Log.w(TAG, "Neither the media copy nor the document provider could open the recording", error)
            Toast.makeText(
                context,
                "No app can open this video. It is saved at ${file.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** @return a media URI already in the library for [path], for files an older build scanned. */
    private fun findByPath(context: Context, path: String): Uri? {
        val collection = collection()
        val cursor = runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DATA} = ?",
                arrayOf(path),
                null
            )
        }.getOrNull() ?: return null
        return try {
            if (cursor.moveToFirst()) android.content.ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
        } finally {
            cursor.close()
        }
    }

    private fun copyIntoMediaStore(context: Context, file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/$ALBUM")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = runCatching { resolver.insert(collection(), values) }.getOrNull() ?: return null
        return runCatching {
            resolver.openOutputStream(target)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output, 1 shl 16) }
            } ?: return null
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(target, values, null, null)
            target
        }.onFailure { error ->
            Log.w(TAG, "Could not publish the recording to the media library", error)
            runCatching { resolver.delete(target, null, null) }
        }.getOrNull()
    }

    /** Pre-Q has no scoped insert; copy into the shared Movies folder and let the scanner find it. */
    private fun copyToPublicDirectoryAndScan(context: Context, file: File): Uri? {
        @Suppress("DEPRECATION")
        val root = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
        val album = File(root, ALBUM)
        if (!album.isDirectory && !album.mkdirs()) return null
        val target = File(album, file.name)
        if (!target.isFile) {
            runCatching { file.copyTo(target, overwrite = false) }
                .onFailure { error ->
                    Log.w(TAG, "Could not copy the recording into Movies/$ALBUM", error)
                    return null
                }
        }
        android.media.MediaScannerConnection.scanFile(
            context, arrayOf(target.absolutePath), arrayOf("video/mp4"), null
        )
        return runCatching { Uri.fromFile(target) }.getOrNull()
    }

    private fun collection(): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    /** Display name of a media URI, used to tell the player what is about to open. */
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        val cursor: Cursor? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) cursor.getString(0) else null
        } finally {
            cursor?.close()
        }
    }.getOrNull()
}
