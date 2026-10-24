/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Wallpapers page, a line by line port of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/WallpaperPage.kt
 * Layout, order and behaviour are the ones of that file: the floating control bar (accent swatch,
 * "N Minecraft HD Wallpapers & Dynamic Themes", "Active: X • Theme: Y" line, "Dim n%" slider,
 * "Solid Dark" button, accent "+ Custom" button) and the three column grid of preset cards whose
 * image carries the theme chip and whose footer carries the title and the biome • shader line.
 * The AlertDialog-free section of the page uses AerixThemeManager and AerixPreferences, the adapters
 * that replace MiraiThemeManager and AllSettings.
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.theme.AerixWallpaperPreset
import net.kdt.pojavlaunch.aerix.components.AerixAnimations
import net.kdt.pojavlaunch.aerix.components.AerixAnimation
import net.kdt.pojavlaunch.aerix.components.isAnimationId

@Composable
fun WallpapersScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeAccent = AerixThemeManager.currentAccent()
    val failedText = stringResource(R.string.aerix_wallpaper_failed)

    val selectedId = AerixPreferences.selectedWallpaper
    var showAnimations by rememberSaveable { mutableStateOf(isAnimationId(selectedId)) }
    val selectedPreset = remember(selectedId) { AerixThemeManager.wallpapers.firstOrNull { it.id == selectedId } }
    val selectedAnimation = remember(selectedId) { AerixAnimations.firstOrNull { it.id == selectedId } }
    val opacitySetting = AerixPreferences.backgroundOpacity

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { AerixThemeManager.applyCustomUri(context, uri) }
                    .onFailure { Toast.makeText(context, failedText, Toast.LENGTH_LONG).show() }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
    ) {
        // ---- header and controls bar ----------------------------------------------------------
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(AerixRadii.cardSmall),
                    tint = AerixSurface.glassTint,
                    strength = 0.9f,
                    elevation = AerixMetrics.glassFloatingElevation
                ),
            shape = RoundedCornerShape(AerixRadii.cardSmall),
            color = AerixSurface.panelRaised.copy(alpha = 0.33f),
            border = BorderStroke(AerixSpacing.hairline, activeAccent.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
            ) {
                // Left side of the bar: the one switch for everything that moves, and the blur
                // meter that governs every wallpaper rather than a single one.
                Surface(
                    shape = RoundedCornerShape(AerixRadii.compact),
                    color = if (AerixPreferences.themeMotion) activeAccent.copy(alpha = 0.22f) else AerixSurface.panel,
                    border = BorderStroke(
                        AerixSpacing.hairline,
                        if (AerixPreferences.themeMotion) activeAccent else AerixSurface.borderSoft
                    ),
                    onClick = { AerixPreferences.saveThemeMotion(!AerixPreferences.themeMotion) }
                ) {
                    Text(
                        text = stringResource(
                            if (AerixPreferences.themeMotion) R.string.aerix_wallpaper_motion_on
                            else R.string.aerix_wallpaper_motion_off
                        ),
                        color = if (AerixPreferences.themeMotion) activeAccent else AerixSurface.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.smCompact)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.aerix_wallpaper_blur, AerixPreferences.backgroundBlur),
                        color = AerixSurface.textSecondary,
                        fontSize = 10.sp
                    )
                    Slider(
                        value = AerixPreferences.backgroundBlur.toFloat(),
                        onValueChange = { AerixPreferences.saveBackgroundBlur(it.toInt()) },
                        valueRange = 0f..20f,
                        modifier = Modifier
                            .width(104.dp)
                            .height(22.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = activeAccent,
                            activeTrackColor = activeAccent,
                            inactiveTrackColor = AerixSurface.canvas
                        )
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (showAnimations) {
                            stringResource(R.string.aerix_wallpaper_header, AerixAnimations.size)
                        } else {
                            stringResource(R.string.aerix_wallpaper_header, AerixThemeManager.wallpapers.size)
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            selectedAnimation != null -> "${selectedAnimation.title} \u00B7 ${selectedAnimation.biome}"
                            selectedPreset != null -> stringResource(
                                R.string.aerix_wallpaper_active_line,
                                selectedPreset.title,
                                selectedPreset.themeName
                            )
                            selectedId == AerixThemeManager.WALLPAPER_CUSTOM -> stringResource(R.string.aerix_wallpaper_active_custom)
                            else -> stringResource(R.string.aerix_wallpaper_active_dark)
                        },
                        color = activeAccent,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Dim slider, compact like the Mirai page
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                ) {
                    Text(
                        text = stringResource(R.string.aerix_wallpaper_dim, opacitySetting),
                        color = AerixSurface.textSecondary,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = opacitySetting.toFloat(),
                        onValueChange = { AerixPreferences.saveBackgroundOpacity(it.toInt()) },
                        valueRange = 35f..95f,
                        modifier = Modifier
                            .width(96.dp)
                            .height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = activeAccent,
                            activeTrackColor = activeAccent,
                            inactiveTrackColor = AerixSurface.canvas
                        )
                    )
                }

                // Solid dark
                Surface(
                    shape = RoundedCornerShape(AerixRadii.compact),
                    color = if (selectedId == AerixThemeManager.WALLPAPER_NONE) activeAccent.copy(alpha = 0.2f) else AerixSurface.panel,
                    border = BorderStroke(
                        AerixSpacing.hairline,
                        if (selectedId == AerixThemeManager.WALLPAPER_NONE) activeAccent else AerixSurface.borderSoft
                    ),
                    onClick = { AerixThemeManager.clearWallpaper(context) }
                ) {
                    Text(
                        text = stringResource(R.string.aerix_wallpaper_solid_dark),
                        color = if (selectedId == AerixThemeManager.WALLPAPER_NONE) activeAccent else AerixSurface.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.smCompact)
                    )
                }

                // Import a custom image
                Surface(
                    shape = RoundedCornerShape(AerixRadii.compact),
                    color = activeAccent,
                    contentColor = AerixSurface.onAccent,
                    onClick = { picker.launch("image/*") }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.smCompact),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = stringResource(R.string.aerix_wallpaper_custom_button),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // ---- two grids: the 25 photographs, and the 25 animated scenes -----------------------
        // Both live on this page; the buttons switch which one is showing.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            listOf(
                stringResource(R.string.aerix_wallpaper_tab_wallpaper) to false,
                stringResource(R.string.aerix_wallpaper_tab_animations) to true
            ).forEach { (label, animations) ->
                val active = showAnimations == animations
                Surface(
                    shape = RoundedCornerShape(AerixRadii.compact),
                    color = if (active) activeAccent.copy(alpha = 0.22f) else AerixSurface.panel,
                    border = BorderStroke(
                        AerixSpacing.hairline,
                        if (active) activeAccent else AerixSurface.borderSoft
                    ),
                    onClick = { showAnimations = animations }
                ) {
                    Text(
                        text = label,
                        color = if (active) activeAccent else AerixSurface.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.xsPlus)
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = AerixSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
        ) {
            if (showAnimations) {
                items(items = AerixAnimations, key = { it.id }) { animation ->
                    AnimationCard(
                        animation = animation,
                        isSelected = selectedId == animation.id,
                        onClick = { AerixThemeManager.applyAnimation(animation) }
                    )
                }
            } else {
                items(
                    items = AerixThemeManager.wallpapers,
                    key = { it.id }
                ) { preset ->
                    WallpaperPresetCard(
                        preset = preset,
                        isSelected = selectedId == preset.id,
                        onClick = { AerixThemeManager.applyPreset(preset) }
                    )
                }
            }
        }
    }
}

/**
 * One of the 25 animated scenes in the grid.
 *
 * The preview is painted from the scene's own sky and accent rather than run live: twenty five
 * running scenes at once would cost far more than it shows, and tapping the card puts the real
 * thing full screen immediately.
 */
@Composable
private fun AnimationCard(animation: AerixAnimation, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AerixRadii.control)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = AerixSurface.panelRaised,
        border = BorderStroke(
            width = if (isSelected) 2.dp else AerixSpacing.hairline,
            color = if (isSelected) animation.accent else AerixSurface.panelRaised
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Brush.verticalGradient(animation.sky))
            ) {
                // Silhouettes and the scene's glow, so every preview is recognisably its own scene.
                animation.hills.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.34f + 0.12f * index)
                            .align(Alignment.BottomCenter)
                            .background(color)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .align(Alignment.TopEnd)
                        .padding(end = AerixSpacing.sm)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(animation.accent.copy(alpha = 0.55f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(AerixSpacing.xsPlus),
                    shape = RoundedCornerShape(AerixRadii.compact),
                    color = AerixSurface.canvas.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = stringResource(R.string.aerix_animation_live),
                        color = animation.accent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(AerixSpacing.xsPlus)) {
                Text(
                    text = animation.title,
                    color = AerixSurface.textPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = animation.biome,
                    color = AerixSurface.textSecondary,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WallpaperPresetCard(preset: AerixWallpaperPreset, isSelected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    // Same thumbnail policy as the Mirai page: decoded from the bundled asset at card size.
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, preset.assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(preset.assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 8 })
                        ?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AerixRadii.control))
            .liquidGlass(
                shape = RoundedCornerShape(AerixRadii.control),
                tint = if (isSelected) preset.accentColor else AerixSurface.glassTint,
                strength = if (isSelected) 0.95f else 0.7f,
                elevation = if (isSelected) AerixMetrics.glassSelectedElevation else AerixMetrics.glassSubtleElevation
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AerixRadii.control),
        color = AerixSurface.panelRaised.copy(alpha = 0.34f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else AerixSpacing.hairline,
            color = if (isSelected) preset.accentColor else AerixSurface.panelRaised
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                val image = thumbnail
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = preset.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(AerixSurface.panelRaised))
                }

                // Bottom gradient so the theme chip stays readable
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, AerixSurface.canvas.copy(alpha = 0.8f))
                            )
                        )
                )

                // Theme colour pill, bottom left of the image
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(AerixSpacing.smCompact),
                    shape = RoundedCornerShape(AerixRadii.micro),
                    color = AerixSurface.panelGlassTint.copy(alpha = 0.66f),
                    border = BorderStroke(AerixSpacing.hairline, preset.accentColor.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AerixSpacing.smCompact, vertical = AerixSpacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(preset.accentColor)
                        )
                        Text(
                            text = preset.themeName,
                            color = preset.accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Active badge, top right of the image
                if (isSelected) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(AerixSpacing.smCompact),
                        shape = RoundedCornerShape(AerixRadii.micro),
                        color = preset.accentColor,
                        contentColor = AerixSurface.onAccent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AerixSpacing.smCompact, vertical = AerixSpacing.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.tiny)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_check),
                                contentDescription = stringResource(R.string.aerix_wallpaper_active),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = stringResource(R.string.aerix_wallpaper_active),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AerixSpacing.smNarrow, vertical = AerixSpacing.smCompact),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.hairline)
            ) {
                Text(
                    text = preset.title,
                    color = if (isSelected) preset.accentColor else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = preset.biome,
                    color = AerixSurface.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
