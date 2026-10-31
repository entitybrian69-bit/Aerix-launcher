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
import android.os.Build
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
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
import net.kdt.pojavlaunch.aerix.theme.ThemeMotion

private var wallpaperCacheKey: String? = null
private var wallpaperCacheBitmap: ImageBitmap? = null

private fun decodeWallpaper(context: Context, id: String, softness: Int = 1): ImageBitmap? {
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
    val metrics = context.resources.displayMetrics
    // softness > 1 decodes the picture at a fraction of the screen resolution. Drawn back up with
    // bilinear filtering that reads as a blur, which is the only way to offer the blur meter on
    // devices below Android 12, where a real blur effect does not exist.
    val target = (maxOf(metrics.widthPixels, metrics.heightPixels) / softness).coerceAtLeast(1)
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
    val blurDp = AerixPreferences.backgroundBlur
    // Android 12+ blurs with a render effect; below that the bitmap is decoded smaller, so the
    // meter still does something on every device this launcher supports.
    val renderBlur = blurDp > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val softness = if (blurDp > 0 && !renderBlur) (1 + blurDp / 4).coerceIn(1, 6) else 1
    val cacheKey = "$id|$revision|$softness"
    var image by remember(cacheKey) { mutableStateOf(wallpaperCacheBitmap.takeIf { wallpaperCacheKey == cacheKey }) }
    LaunchedEffect(cacheKey) {
        if (animationById(id) != null) return@LaunchedEffect
        if (wallpaperCacheKey != cacheKey || wallpaperCacheBitmap == null) {
            val decoded = withContext(Dispatchers.IO) { runCatching { decodeWallpaper(context, id, softness) }.getOrNull() }
            wallpaperCacheKey = cacheKey
            wallpaperCacheBitmap = decoded
            image = decoded
        }
    }
    // One switch for the whole launcher: the accent motion and everything on this page obey it.
    val motion = ThemeMotion.rememberEnabled()
    val animation = animationById(id)
    Box(modifier.fillMaxSize().background(AerixSurface.canvas)) {
        // Footage that ships with the launcher: played straight from the APK, no bitmap involved.
        if (AerixThemeManager.isBundledClipId(id)) {
            val clip = bundledClipById(id.removePrefix(AerixThemeManager.WALLPAPER_CLIP_ASSET_PREFIX))
            if (clip != null) {
                RawVideoWallpaper(resId = clip.rawRes, motion = motion, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(AerixThemeManager.screenScrim()))
                content()
                return@Box
            }
        }
        // An imported wallpaper is played, not drawn: a clip or an animated image the user brought.
        if (AerixThemeManager.isClipId(id)) {
            val file = AerixThemeManager.clipFile(context, id)
            if (file != null && file.isFile) {
                MediaWallpaper(file = file, motion = motion, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(AerixThemeManager.screenScrim()))
                content()
                return@Box
            }
        }
        // An animated wallpaper is drawn, not decoded: it needs no bitmap and no asset.
        if (animation != null) {
            AnimatedScene(
                animation = animation,
                motion = motion,
                modifier = Modifier.fillMaxSize().let { if (renderBlur) it.blur(blurDp.dp * 0.5f) else it }
            )
            Box(Modifier.fillMaxSize().background(AerixThemeManager.screenScrim()))
            content()
            return@Box
        }
        val bitmap = image
        if (bitmap != null) {
            // The 25 photographs and any custom picture are still pictures: they are drawn exactly
            // as they are, with the blur on top when it is set. No drift, no light layer, no
            // particles over them. Motion belongs to the animations, not to a photograph.
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (renderBlur) it.blur(blurDp.dp) else it },
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(AerixThemeManager.screenScrim()))
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
