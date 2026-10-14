/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/SettingsScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/LauncherSettingsScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/RendererSettingsScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/ControlSettingsScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/GameSettingsScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/layouts/(all .kt files)
 * Appearance rows write AerixPreferences; every other row is a PojavSetting from PojavSettingsBridge
 * and writes the PojavLauncher key it names. Each category links to the original Pojav screen.
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

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.bridge.AerixColorTheme
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.AerixTransitionType
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.bridge.PojavSetting
import net.kdt.pojavlaunch.aerix.bridge.PojavSettingCategory
import net.kdt.pojavlaunch.aerix.bridge.PojavSettingsBridge
import net.kdt.pojavlaunch.aerix.components.ActionSettingRow
import net.kdt.pojavlaunch.aerix.components.PillTabs
import net.kdt.pojavlaunch.aerix.components.SliderSettingRow
import net.kdt.pojavlaunch.aerix.components.SwitchSettingRow
import net.kdt.pojavlaunch.aerix.components.TextSettingRow
import net.kdt.pojavlaunch.aerix.bridge.AssetPlatform
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

@Composable
fun SettingsScreen(activity: FragmentActivity) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val categories = PojavSettingsBridge.categories
    val tabTitles = listOf(stringResource(R.string.aerix_settings_cat_appearance)) + categories.map { stringResource(it.titleRes) }

    AerixPage(
        title = stringResource(R.string.aerix_settings_title),
        subtitle = stringResource(R.string.aerix_settings_subtitle)
    ) {
        PillTabs(
            tabs = tabTitles,
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
        if (tab == 0) {
            AppearanceSettings(activity)
        } else {
            PojavCategorySettings(activity, categories[tab - 1])
        }
    }
}

@Composable
private fun AppearanceSettings(activity: FragmentActivity) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showThemeChoice by remember { mutableStateOf(false) }
    var showTransitionChoice by remember { mutableStateOf(false) }
    var showPlatformChoice by remember { mutableStateOf(false) }
    val platformLabels = listOf(
        AssetPlatform.MODRINTH.name to stringResource(R.string.aerix_download_platform_modrinth),
        AssetPlatform.CURSEFORGE.name to stringResource(R.string.aerix_download_platform_curseforge)
    )
    val transitionLabels = listOf(
        AerixTransitionType.CLOSE.name to stringResource(R.string.aerix_settings_transition_close),
        AerixTransitionType.JELLY_BOUNCE.name to stringResource(R.string.aerix_settings_transition_jelly),
        AerixTransitionType.BOUNCE.name to stringResource(R.string.aerix_settings_transition_bounce),
        AerixTransitionType.SLICE_IN.name to stringResource(R.string.aerix_settings_transition_slice)
    )
    val aerixLabel = stringResource(R.string.aerix_settings_theme_aerix)
    val customLabel = stringResource(R.string.aerix_settings_theme_custom)

    if (showThemeChoice) {
        ChoiceDialog(
            title = stringResource(R.string.aerix_settings_color_theme),
            entries = listOf(AerixColorTheme.AERIX.name to aerixLabel, AerixColorTheme.CUSTOM.name to customLabel),
            current = AerixPreferences.colorTheme.name,
            onDismiss = { showThemeChoice = false },
            onPick = {
                AerixPreferences.saveColorTheme(AerixColorTheme.valueOf(it))
                showThemeChoice = false
            }
        )
    }
    if (showTransitionChoice) {
        ChoiceDialog(
            title = stringResource(R.string.aerix_settings_transition),
            entries = transitionLabels,
            current = AerixPreferences.swapAnimateType.name,
            onDismiss = { showTransitionChoice = false },
            onPick = {
                AerixPreferences.saveSwapAnimateType(AerixTransitionType.valueOf(it))
                showTransitionChoice = false
            }
        )
    }
    if (showPlatformChoice) {
        ChoiceDialog(
            title = stringResource(R.string.aerix_settings_asset_platform),
            entries = platformLabels,
            current = AerixPreferences.assetPlatformSource.name,
            onDismiss = { showPlatformChoice = false },
            onPick = {
                AerixPreferences.saveAssetPlatformSource(AssetPlatform.valueOf(it))
                showPlatformChoice = false
            }
        )
    }
    if (showColorPicker) {
        HsvColorDialog(
            initial = Color(AerixPreferences.customColor),
            onDismiss = { showColorPicker = false },
            onApply = { color ->
                AerixPreferences.saveCustomColor(color.toArgb())
                AerixPreferences.saveColorTheme(AerixColorTheme.CUSTOM)
                showColorPicker = false
            }
        )
    }

    SettingsList {
        item {
            ActionSettingRow(
                title = stringResource(R.string.aerix_settings_color_theme),
                summary = stringResource(R.string.aerix_settings_color_theme_sub),
                onClick = { showThemeChoice = true },
                iconRes = R.drawable.aerix_ic_style_outlined,
                value = if (AerixPreferences.colorTheme == AerixColorTheme.CUSTOM) customLabel else aerixLabel
            )
        }
        item {
            ActionSettingRow(
                title = stringResource(R.string.aerix_settings_custom_color),
                summary = stringResource(R.string.aerix_settings_custom_color_sub),
                onClick = { showColorPicker = true },
                iconRes = R.drawable.aerix_ic_format_paint_outlined,
                value = "#%06X".format(AerixPreferences.customColor and 0xFFFFFF)
            )
        }
        item {
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
            SliderSettingRow(
                title = stringResource(R.string.aerix_settings_bg_blur),
                summary = stringResource(R.string.aerix_settings_bg_blur_sub),
                value = AerixPreferences.backgroundBlur,
                range = 0..20,
                step = 1,
                suffix = " dp",
                onValueChange = { AerixPreferences.saveBackgroundBlur(it) },
                iconRes = R.drawable.aerix_ic_photo_library_outlined
            )
        }
        item {
            SwitchSettingRow(
                title = stringResource(R.string.aerix_settings_fullscreen),
                summary = stringResource(R.string.aerix_settings_fullscreen_sub),
                checked = AerixPreferences.fullScreen,
                onCheckedChange = { value ->
                    AerixPreferences.saveFullScreen(value)
                    // Same call BaseActivity makes; LauncherActivity ignores the notch in portrait only.
                    val portrait = activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                    Tools.setInsetsMode(activity, value, portrait)
                },
                iconRes = R.drawable.aerix_ic_video_settings
            )
        }
        item {
            SliderSettingRow(
                title = stringResource(R.string.aerix_settings_animate_speed),
                summary = stringResource(R.string.aerix_settings_animate_speed_sub),
                value = AerixPreferences.animateSpeed,
                range = 0..10,
                step = 1,
                suffix = "",
                onValueChange = { AerixPreferences.saveAnimateSpeed(it) },
                iconRes = R.drawable.aerix_ic_rocket_launch_filled
            )
        }
        item {
            ActionSettingRow(
                title = stringResource(R.string.aerix_settings_transition),
                summary = stringResource(R.string.aerix_settings_transition_sub),
                onClick = { showTransitionChoice = true },
                iconRes = R.drawable.aerix_ic_style_outlined,
                value = transitionLabels.first { it.first == AerixPreferences.swapAnimateType.name }.second
            )
        }
        item {
            SliderSettingRow(
                title = stringResource(R.string.aerix_settings_animate_extent),
                summary = stringResource(R.string.aerix_settings_animate_extent_sub),
                value = AerixPreferences.animateExtent,
                range = 0..10,
                step = 1,
                suffix = "",
                onValueChange = { AerixPreferences.saveAnimateExtent(it) },
                iconRes = R.drawable.aerix_ic_touch_app_outlined
            )
        }
        item {
            SwitchSettingRow(
                title = stringResource(R.string.aerix_settings_festival),
                summary = stringResource(R.string.aerix_settings_festival_sub),
                checked = AerixPreferences.festivalEffects,
                onCheckedChange = { AerixPreferences.saveFestivalEffects(it) },
                iconRes = R.drawable.aerix_ic_schedule_outlined
            )
        }
        item {
            SliderSettingRow(
                title = stringResource(R.string.aerix_logs_text_size),
                summary = stringResource(R.string.aerix_logs_text_size_sub),
                value = AerixPreferences.logTextSize,
                range = 5..20,
                step = 1,
                suffix = " sp",
                onValueChange = { AerixPreferences.saveLogTextSize(it) },
                iconRes = R.drawable.aerix_ic_terminal_outlined
            )
        }
        item {
            ActionSettingRow(
                title = stringResource(R.string.aerix_settings_asset_platform),
                summary = stringResource(R.string.aerix_settings_asset_platform_sub),
                onClick = { showPlatformChoice = true },
                iconRes = R.drawable.aerix_ic_download,
                value = platformLabels.first { it.first == AerixPreferences.assetPlatformSource.name }.second
            )
        }
        item {
            TextSettingRow(
                title = stringResource(R.string.aerix_settings_curseforge_key),
                summary = stringResource(R.string.aerix_settings_curseforge_key_sub),
                value = AerixPreferences.curseForgeApiKey,
                onValueChange = { AerixPreferences.saveCurseForgeApiKey(it) },
                iconRes = R.drawable.aerix_ic_link
            )
        }
        item {
            ActionSettingRow(
                title = stringResource(R.string.aerix_settings_classic_all),
                summary = stringResource(R.string.aerix_settings_classic_all_sub),
                onClick = { PojavNavigator.openClassicSettings(activity) },
                iconRes = R.drawable.aerix_ic_settings_filled
            )
        }
        item {
            ActionSettingRow(
                title = stringResource(R.string.aerix_settings_classic_home),
                summary = stringResource(R.string.aerix_settings_classic_home_sub),
                onClick = { PojavNavigator.openClassicHome(activity) },
                iconRes = R.drawable.aerix_ic_home_filled
            )
        }
    }
}

@Composable
private fun PojavCategorySettings(activity: FragmentActivity, category: PojavSettingCategory) {
    // Reading the revision here makes every row re-read its PREF_* value after a write.
    val revision = PojavSettingsBridge.revision
    val visible = remember(category, revision) { category.settings.filter { it.visible(activity) } }
    var choice by remember { mutableStateOf<PojavSetting.Choice?>(null) }

    choice?.let { setting ->
        ChoiceDialog(
            title = stringResource(setting.titleRes),
            entries = remember(setting) { setting.entries(activity) },
            current = setting.current(),
            onDismiss = { choice = null },
            onPick = {
                PojavSettingsBridge.writeString(activity, setting.key, it)
                choice = null
            }
        )
    }

    SettingsList {
        items(visible, key = { "${category.id}:${it.key}" }) { setting ->
            PojavSettingRow(activity, setting, revision) { choice = it }
        }
        item {
            ActionSettingRow(
                title = stringResource(R.string.aerix_settings_open_classic),
                summary = stringResource(R.string.aerix_settings_open_classic_sub),
                onClick = { PojavSettingsBridge.openClassic(activity, category) },
                iconRes = category.iconRes
            )
        }
    }
}

@Composable
private fun PojavSettingRow(
    activity: FragmentActivity,
    setting: PojavSetting,
    @Suppress("UNUSED_PARAMETER") revision: Int,
    onChoice: (PojavSetting.Choice) -> Unit
) {
    val title = stringResource(setting.titleRes)
    val summary = setting.summaryRes?.let { stringResource(it) }
    when (setting) {
        is PojavSetting.Toggle -> SwitchSettingRow(
            title = title,
            summary = summary,
            checked = setting.current(),
            onCheckedChange = { PojavSettingsBridge.writeBoolean(activity, setting.key, it) }
        )

        is PojavSetting.Slider -> {
            val min = setting.min(activity)
            val max = maxOf(min, setting.max(activity))
            val value = setting.current().coerceIn(min, max)
            val fullSummary = if (setting.key == "allocation") {
                val total = Tools.getTotalDeviceMemory(activity)
                val free = PojavSettingsBridge.freeMemoryMb(activity)
                buildString {
                    if (summary != null) append(summary).append('\n')
                    append(stringResource(R.string.aerix_settings_memory_info, total, free))
                    if (value > free) append('\n').append(stringResource(R.string.setting_java_memory_exceeded))
                }
            } else summary
            SliderSettingRow(
                title = title,
                summary = fullSummary,
                value = value,
                range = min..max,
                step = setting.step,
                suffix = setting.suffix,
                onValueChange = { PojavSettingsBridge.writeInt(activity, setting.key, it) }
            )
        }

        is PojavSetting.Text -> TextSettingRow(
            title = title,
            summary = summary,
            value = setting.current(),
            onValueChange = { PojavSettingsBridge.writeString(activity, setting.key, it) }
        )

        is PojavSetting.Choice -> {
            val entries = remember(setting) { setting.entries(activity) }
            val current = setting.current()
            ActionSettingRow(
                title = title,
                summary = summary,
                onClick = { onChoice(setting) },
                value = entries.firstOrNull { it.first == current }?.second ?: current
            )
        }

        is PojavSetting.Action -> ActionSettingRow(
            title = title,
            summary = summary,
            onClick = { setting.run.invoke(activity) }
        )
    }
}

@Composable
private fun SettingsList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm),
        content = content
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    entries: List<Pair<String, String>>,
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AerixSurface.panelRaised,
        title = { Text(title, color = AerixSurface.textPrimary) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                entries.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(value) }
                            .padding(vertical = AerixSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == current,
                            onClick = { onPick(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = AerixSurface.textSecondary)
                        )
                        Text(label, color = AerixSurface.textPrimary, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary) }
        }
    )
}

/** Hue / saturation / brightness picker for the custom accent (replaces com.movtery.colorpicker). */
@Composable
private fun HsvColorDialog(initial: Color, onDismiss: () -> Unit, onApply: (Color) -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AerixSurface.panelRaised,
        title = { Text(stringResource(R.string.aerix_settings_custom_color), color = AerixSurface.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(color)
                        .border(1.dp, AerixSurface.borderSoft, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("#%06X".format(color.toArgb() and 0xFFFFFF), color = if (brightness > 0.6f) Color.Black else Color.White, fontSize = 13.sp)
                }
                ColorSlider(stringResource(R.string.aerix_settings_hue), hue, 0f..360f) { hue = it }
                ColorSlider(stringResource(R.string.aerix_settings_saturation), saturation, 0f..1f) { saturation = it }
                ColorSlider(stringResource(R.string.aerix_settings_brightness), brightness, 0f..1f) { brightness = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(color) }) { Text(stringResource(R.string.aerix_settings_apply), color = color) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary) }
        }
    )
}

@Composable
private fun ColorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, color = AerixSurface.textSecondary, fontSize = 11.sp)
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = AerixSurface.textPrimary,
                activeTrackColor = AerixThemeManager.currentAccent(),
                inactiveTrackColor = AerixSurface.panelTrack
            )
        )
    }
}
