/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/WallpaperGalleryScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/theme/MiraiThemeManager.kt (presets, custom wallpaper)
 * Wallpapers ship in assets/wallpapers; selection is stored by AerixPreferences ("selected_wallpaper_id").
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

package net.kdt.pojavlaunch.aerix.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.components.QuickActionTile
import net.kdt.pojavlaunch.aerix.components.SliderSettingRow
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.theme.AerixWallpaperPreset

@Composable
fun WallpapersScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedId = AerixPreferences.selectedWallpaper
    val failedText = stringResource(R.string.aerix_wallpaper_failed)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { AerixThemeManager.applyCustomUri(context, uri) }
                    .onFailure { Toast.makeText(context, failedText, Toast.LENGTH_LONG).show() }
            }
        }
    }

    AerixPage(
        title = stringResource(R.string.aerix_wallpaper_title),
        subtitle = stringResource(R.string.aerix_wallpaper_subtitle)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 200.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SliderSettingRow(
                    title = stringResource(R.string.aerix_settings_bg_opacity),
                    summary = stringResource(R.string.aerix_settings_bg_opacity_sub),
                    value = AerixPreferences.backgroundOpacity,
                    range = 20..100,
                    step = 1,
                    suffix = " %",
                    onValueChange = { AerixPreferences.saveBackgroundOpacity(it) },
                    iconRes = R.drawable.aerix_ic_wallpaper
                )
            }
            item {
                QuickActionTile(
                    iconRes = R.drawable.aerix_ic_photo_library_outlined,
                    title = stringResource(R.string.aerix_wallpaper_custom),
                    subtitle = stringResource(R.string.aerix_wallpaper_custom_sub),
                    accent = if (selectedId == AerixThemeManager.WALLPAPER_CUSTOM) AerixThemeManager.currentAccent() else AerixSurface.glassBlue,
                    onClick = { picker.launch("image/*") }
                )
            }
            item {
                QuickActionTile(
                    iconRes = R.drawable.aerix_ic_close,
                    title = stringResource(R.string.aerix_wallpaper_none),
                    subtitle = stringResource(R.string.aerix_wallpaper_none_sub),
                    accent = if (selectedId == AerixThemeManager.WALLPAPER_NONE) AerixThemeManager.currentAccent() else AerixSurface.glassRose,
                    onClick = { AerixThemeManager.clearWallpaper(context) }
                )
            }
            items(AerixThemeManager.wallpapers) { preset ->
                WallpaperCard(
                    preset = preset,
                    selected = preset.id == selectedId,
                    onClick = { AerixThemeManager.applyPreset(preset) }
                )
            }
        }
    }
}

@Composable
private fun WallpaperCard(preset: AerixWallpaperPreset, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, preset.assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(preset.assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(AerixRadii.cardLarge)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .liquidGlass(shape = shape, tint = preset.accentColor, strength = if (selected) 0.9f else 0.5f)
            .border(BorderStroke(if (selected) 2.dp else AerixSpacing.hairline, if (selected) preset.accentColor else AerixSurface.borderSoft), shape)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        val image = thumbnail
        if (image != null) {
            Image(bitmap = image, contentDescription = preset.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(AerixSurface.panelRaised))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, AerixSurface.canvas.copy(alpha = 0.85f))))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(AerixSpacing.smPlus)
        ) {
            Text(text = preset.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(preset.accentColor))
                Text(text = preset.themeName, color = AerixSurface.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AerixSpacing.sm)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(preset.accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(painter = painterResource(R.drawable.aerix_ic_check), contentDescription = stringResource(R.string.aerix_wallpaper_active), tint = AerixSurface.onAccentDefault, modifier = Modifier.size(16.dp))
            }
        }
    }
}
