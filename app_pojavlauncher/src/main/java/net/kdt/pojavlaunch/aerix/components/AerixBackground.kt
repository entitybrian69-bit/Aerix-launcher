/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/CaveBackground.kt
 * Zalith Launcher 2 Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

private var wallpaperCacheKey: String? = null
private var wallpaperCacheBitmap: ImageBitmap? = null

/**
 * Decodes the wallpaper. [maxSize] is the longest side the picture may have; a small size gives a
 * picture that is blurred by the scaling of the screen instead of by an expensive shader.
 */
private fun decodeWallpaper(context: Context, id: String, maxSize: Int): ImageBitmap? {
    val bytes: ByteArray? = when (id) {
        AerixThemeManager.WALLPAPER_NONE -> null
        AerixThemeManager.WALLPAPER_CUSTOM -> {
            val file = AerixThemeManager.customWallpaperFile(context)
            if (file.exists()) file.readBytes() else null
        }
        else -> {
            val preset = AerixThemeManager.wallpapers.firstOrNull { it.id == id } ?: AerixThemeManager.wallpapers.first()
            runCatching { context.assets.open(preset.assetPath).use { it.readBytes() } }.getOrNull()
        }
    }
    if (bytes == null) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val target = maxSize.coerceAtLeast(1)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

/**
 * Full-bleed wallpaper with the Aerix canvas scrim on top. Decoding runs off the main thread and
 * the bitmap is cached process-wide, same as the Aerix CaveBackground.
 */
@Composable
fun AerixBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    val id = AerixPreferences.selectedWallpaper
    val revision = AerixThemeManager.wallpaperRevision
    val blur = AerixPreferences.backgroundBlur
    // A blurred wallpaper is drawn from a small picture: a full screen blur shader costs a lot of
    // graphics power on every frame, the scaled up small picture looks the same and is free.
    val blurred = blur > 0
    val cacheKey = "$id|$revision|${if (blurred) "blur" else "sharp"}"
    var image by remember(cacheKey) { mutableStateOf(wallpaperCacheBitmap.takeIf { wallpaperCacheKey == cacheKey }) }
    LaunchedEffect(cacheKey) {
        if (wallpaperCacheKey != cacheKey || wallpaperCacheBitmap == null) {
            val metrics = context.resources.displayMetrics
            val screen = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
            val wanted = if (blurred) screen / 14 else screen
            val decoded = withContext(Dispatchers.IO) { runCatching { decodeWallpaper(context, id, wanted) }.getOrNull() }
            wallpaperCacheKey = cacheKey
            wallpaperCacheBitmap = decoded
            image = decoded
        }
    }
    val scrim = AerixThemeManager.screenScrim()
    Box(modifier.fillMaxSize().background(AerixSurface.canvas)) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(scrim))
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(AerixSurface.canvas, AerixSurface.panelRaised)))
            )
        }
        content()
    }
}
