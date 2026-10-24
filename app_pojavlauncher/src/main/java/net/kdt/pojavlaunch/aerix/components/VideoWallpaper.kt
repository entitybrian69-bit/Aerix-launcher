/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Video wallpapers: any clip the user brings plays behind the interface, full screen, muted, looping.
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

import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * Plays [file] behind the launcher.
 *
 * This is the only way to get genuinely animated footage - a recorded clip, not something drawn -
 * and it costs the APK nothing, because the video lives in the app's own files directory. It is
 * muted (a launcher has no business making noise), loops seamlessly, and stops the moment the
 * composable leaves the screen so it never keeps decoding in the background.
 *
 * With the launcher's motion switch off, the clip is held on its first frame instead of playing,
 * so "motion off" means the same thing here as everywhere else.
 */
@Composable
fun VideoWallpaper(
    file: File,
    motion: Boolean,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val videoView = remember(context) {
        VideoView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { videoView }
    )

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
        runCatching {
            player.setVideoURI(Uri.fromFile(file))
            player.seekTo(1)
        }
        onDispose {
            runCatching { player.stopPlayback() }
            player.setOnPreparedListener(null)
            player.setOnErrorListener(null)
        }
    }

    // Follow the motion switch without restarting the clip from the beginning.
    LaunchedEffect(motion) {
        if (motion) {
            if (!videoView.isPlaying) videoView.start()
        } else {
            if (videoView.isPlaying) videoView.pause()
            videoView.seekTo(1)
        }
    }
}

/** Copies a picked clip into the app's files directory so it survives a restart. */
fun installVideoWallpaper(filesDir: File, source: java.io.InputStream, fileName: String = "aerix_video_wallpaper"): File {
    val target = File(filesDir, fileName)
    source.use { input -> target.outputStream().use { output -> input.copyTo(output, 256 * 1024) } }
    return target
}
