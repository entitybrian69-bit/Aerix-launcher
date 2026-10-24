/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Imported wallpapers: video clips and animated images, played full screen behind the interface.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Video containers the platform can decode. Anything else is treated as an animated image. */
private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "m4v", "mov", "3gp")

/** What the file picker offers when adding wallpapers. */
val MEDIA_WALLPAPER_MIME = arrayOf("video/*", "image/gif", "image/webp", "image/*")

internal fun File.isVideoClip(): Boolean = extension.lowercase() in VIDEO_EXTENSIONS

/**
 * Plays a video clip behind the launcher.
 *
 * Muted, looping, and stopped the moment it leaves the screen so it never decodes in the background.
 * With motion off it holds the first frame, which is what motion off means everywhere else.
 */
@Composable
fun VideoWallpaper(
    file: File,
    motion: Boolean,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    val videoView = remember(context) {
        VideoView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    AndroidView(modifier = modifier, factory = { videoView })

    DisposableEffect(file.absolutePath) {
        val player = videoView
        player.setOnPreparedListener { media: MediaPlayer ->
            media.isLooping = true
            media.setVolume(0f, 0f)
            if (motion) player.start()
        }
        player.setOnErrorListener { _, _, _ ->
            onError()
            true
        }
        // A short clip loops by restarting, so this is what makes it look seamless.
        player.setOnCompletionListener { it.start() }
        runCatching {
            player.setVideoURI(Uri.fromFile(file))
            player.seekTo(1)
        }
        onDispose {
            runCatching { player.stopPlayback() }
            player.setOnPreparedListener(null)
            player.setOnErrorListener(null)
            player.setOnCompletionListener(null)
        }
    }

    LaunchedEffect(motion) {
        if (motion) {
            if (!videoView.isPlaying) videoView.start()
        } else {
            if (videoView.isPlaying) videoView.pause()
            videoView.seekTo(1)
        }
    }
}

/**
 * Plays an animated GIF or WebP behind the launcher.
 *
 * These are the smallest real animations there are - a two second looping clip is often under a
 * megabyte - and Android decodes them itself, so nothing is added to the app. On Android 8 and
 * older, which cannot animate them, the first frame is shown instead.
 */
@Composable
fun AnimatedImageWallpaper(
    file: File,
    motion: Boolean,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {}
) {
    val drawable = remember(file.absolutePath) { decodeWallpaperImage(file, onError) }

    AndroidView(
        modifier = modifier,
        factory = {
            ImageView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { view ->
            view.setImageDrawable(drawable)
            val animated = drawable as? AnimatedImageDrawable
            if (animated != null) {
                if (motion) animated.start() else animated.stop()
            }
        }
    )
}

private fun decodeWallpaperImage(file: File, onError: () -> Unit): Drawable? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
    } else {
        BitmapFactory.decodeFile(file.absolutePath)?.let { BitmapDrawable(null, it) }
    }
}.getOrElse {
    onError()
    null
}

/** Displays whichever kind of imported wallpaper [file] is. */
@Composable
fun MediaWallpaper(
    file: File,
    motion: Boolean,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {}
) {
    if (file.isVideoClip()) {
        VideoWallpaper(file = file, motion = motion, modifier = modifier, onError = onError)
    } else {
        AnimatedImageWallpaper(file = file, motion = motion, modifier = modifier, onError = onError)
    }
}

/** Copies an imported wallpaper into [dir], with a readable, unique file name. */
fun importMediaWallpaper(dir: File, displayName: String?, source: java.io.InputStream): File {
    dir.mkdirs()
    val raw = (displayName ?: "clip").substringAfterLast('/').substringAfterLast('\\')
    val base = raw.substringBeforeLast('.').ifBlank { "clip" }
        .replace(Regex("[^A-Za-z0-9 _-]"), "_")
        .take(40)
    val ext = raw.substringAfterLast('.', "").lowercase().ifEmpty { "mp4" }
    var target = File(dir, "$base.$ext")
    var index = 2
    while (target.exists()) {
        target = File(dir, "$base $index.$ext")
        index++
    }
    source.use { input -> target.outputStream().use { output -> input.copyTo(output, 256 * 1024) } }
    return target
}

/** A still of an imported clip, for its card in the grid. */
@Composable
fun rememberClipThumbnail(file: File): ImageBitmap? {
    val thumb by produceState<ImageBitmap?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { decodeClipStill(file)?.asImageBitmap() }
    }
    return thumb
}

private fun decodeClipStill(file: File): Bitmap? = runCatching {
    if (file.isVideoClip()) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            runCatching { retriever.release() }
        }
    } else {
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = 2 }
        )
    }
}.getOrNull()
