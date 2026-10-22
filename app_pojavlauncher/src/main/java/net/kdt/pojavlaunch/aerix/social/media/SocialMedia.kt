/*
 * Aerix Launcher Social layer.
 *
 * Real device media: a picked document is copied into the app's own storage (so it keeps working
 * after the picker's permission expires), its real name/size/mime are read, and images are
 * decoded straight from that file for the chat preview.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A file that really exists on this device, ready to be sent as an attachment. */
data class PickedFile(
    val path: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String
) {
    val sizeLabel: String
        get() = when {
            sizeBytes >= 1024L * 1024L -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
            sizeBytes >= 1024L -> "${sizeBytes / 1024} KB"
            else -> "$sizeBytes B"
        }
}

object SocialMedia {

    private fun dir(context: Context): File = File(context.filesDir, "social_media").apply { mkdirs() }

    /**
     * Copies [uri] into the app sandbox and returns the real file. Copying is what makes a
     * "file of any size" work: the chat keeps a stable path instead of a transient content URI.
     */
    suspend fun copyToStorage(context: Context, uri: Uri): PickedFile? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var name = "attachment"
            var size = 0L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0) ?: name
                        size = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                    }
                }
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val extension = name.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: ""
            val target = File(dir(context), "${UUID.randomUUID()}" + if (extension.isNotBlank()) ".$extension" else "")
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            PickedFile(
                path = target.absolutePath,
                displayName = name,
                sizeBytes = if (size > 0L) size else target.length(),
                mimeType = mime
            )
        }.getOrNull()
    }

    /** Decoded small, off the main thread — used for the image/GIF preview in a bubble. */
    suspend fun thumbnail(path: String, maxWidth: Int = 720): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxWidth) sample *= 2
            val bitmap: Bitmap? = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            bitmap?.asImageBitmap()
        }.getOrNull()
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}
