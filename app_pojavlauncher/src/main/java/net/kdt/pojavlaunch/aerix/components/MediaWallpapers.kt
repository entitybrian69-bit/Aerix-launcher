/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Real footage wallpapers: clips that ship with the launcher, and clips the user imports.
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
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Footage that ships inside the APK, played straight from resources. */
@Composable
fun RawVideoWallpaper(
    resId: Int,
    motion: Boolean,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    VideoSurface(
        motion = motion,
        modifier = modifier,
        onError = onError,
        prepare = { player ->
            val fd = context.resources.openRawResourceFd(resId) ?: error("Clip not found")
            fd.use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        }
    )
}

/** Footage the user imported. */
@Composable
fun VideoWallpaper(
    file: File,
    motion: Boolean,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {}
) {
    VideoSurface(
        motion = motion,
        modifier = modifier,
        onError = onError,
        prepare = { player -> player.setDataSource(file.absolutePath) }
    )
}

/**
 * The player.
 *
 * A TextureView rather than a VideoView, because a VideoView letterboxes: a 16:9 clip on a tall
 * phone would leave black bars down the sides. The surface is scaled and centred so the clip covers
 * the screen and is cropped the way a wallpaper should be.
 *
 * Muted, looping, paused in place when motion is off, and released as soon as it leaves the screen.
 */
@Composable
private fun VideoSurface(
    motion: Boolean,
    modifier: Modifier,
    onError: () -> Unit,
    prepare: (MediaPlayer) -> Unit
) {
    val context = LocalContext.current
    var videoAspect by remember { mutableStateOf(0f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val textureView = remember(context) {
        TextureView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(textureView) {
        val media = MediaPlayer()
        player = media
        var surface: Surface? = null

        fun cropToFill() = cropTransform(textureView, videoAspect)

        val attach = {
            runCatching {
                val texture = textureView.surfaceTexture
                if (texture != null) {
                    val created = Surface(texture)
                    surface = created
                    media.setSurface(created)
                    prepare(media)
                    media.prepareAsync()
                }
            }.onFailure { onError() }
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = attach()
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = cropToFill()
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }

        media.setOnPreparedListener { m ->
            m.isLooping = true
            m.setVolume(0f, 0f)
            m.setOnVideoSizeChangedListener { _, width, height ->
                if (width > 0 && height > 0) {
                    videoAspect = width.toFloat() / height.toFloat()
                    cropToFill()
                }
            }
            m.seekTo(1)
            if (motion) m.start()
            cropToFill()
        }
        media.setOnErrorListener { _, _, _ ->
            onError()
            true
        }

        if (textureView.isAvailable) attach()

        onDispose {
            player = null
            runCatching {
                media.setOnPreparedListener(null)
                media.setOnErrorListener(null)
                media.setSurface(null)
                media.release()
            }
            runCatching { surface?.release() }
            textureView.surfaceTextureListener = null
        }
    }

    // The motion switch pauses and resumes in place, so the clip carries on where it stopped.
    LaunchedEffect(motion, player) {
        val media = player ?: return@LaunchedEffect
        runCatching {
            if (motion) {
                if (!media.isPlaying) media.start()
            } else if (media.isPlaying) {
                media.pause()
            }
        }
    }

    AndroidView(modifier = modifier, factory = { textureView })
}

/** Scales a video surface so the clip covers its view and is centred, instead of letterboxed. */
private fun cropTransform(view: TextureView, aspect: Float) {
    val viewW = view.width.toFloat()
    val viewH = view.height.toFloat()
    if (viewW <= 0f || viewH <= 0f || aspect <= 0f) return
    val viewAspect = viewW / viewH
    val scale = if (viewAspect > aspect) viewAspect / aspect else aspect / viewAspect
    view.setTransform(Matrix().apply { setScale(scale, scale, viewW / 2f, viewH / 2f) })
}

/**
 * Plays an animated GIF or WebP behind the launcher.
 *
 * These are the smallest real animations there are, and Android decodes them itself, so nothing is
 * added to the app. On Android 8 and older, which cannot animate them, the first frame is shown.
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

/** A still of a bundled clip, for its card in the grid. */
@Composable
fun rememberRawClipThumbnail(resId: Int): ImageBitmap? {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, resId) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    context.resources.openRawResourceFd(resId)?.use { fd ->
                        retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    }
                    retriever.getFrameAtTime(1_500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()?.asImageBitmap()
        }
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
