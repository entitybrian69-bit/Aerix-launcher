/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/theme/Theme.kt (aerixDark scheme)
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/theme/Type.kt
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

package net.kdt.pojavlaunch.aerix.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import net.kdt.pojavlaunch.aerix.bridge.AerixColorTheme
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences

private val systemSans = FontFamily.SansSerif
private val defaults = Typography()

private fun TextStyle.aerixText(weight: FontWeight = fontWeight ?: FontWeight.Normal) = copy(
    fontFamily = systemSans,
    fontWeight = weight
)

val AerixTypography = Typography(
    displayLarge = defaults.displayLarge.aerixText(FontWeight.Bold),
    displayMedium = defaults.displayMedium.aerixText(FontWeight.Bold),
    displaySmall = defaults.displaySmall.aerixText(FontWeight.Bold),
    headlineLarge = defaults.headlineLarge.aerixText(FontWeight.Bold),
    headlineMedium = defaults.headlineMedium.aerixText(FontWeight.SemiBold),
    headlineSmall = defaults.headlineSmall.aerixText(FontWeight.SemiBold),
    titleLarge = defaults.titleLarge.aerixText(FontWeight.SemiBold),
    titleMedium = defaults.titleMedium.aerixText(FontWeight.Medium),
    titleSmall = defaults.titleSmall.aerixText(FontWeight.Medium),
    bodyLarge = defaults.bodyLarge.aerixText(),
    bodyMedium = defaults.bodyMedium.aerixText(),
    bodySmall = defaults.bodySmall.aerixText(),
    labelLarge = defaults.labelLarge.aerixText(FontWeight.Medium),
    labelMedium = defaults.labelMedium.aerixText(FontWeight.Medium),
    labelSmall = defaults.labelSmall.aerixText(FontWeight.Medium),
)

private val aerixDark = darkColorScheme(
    primary = AerixSurface.accentDefault,
    onPrimary = AerixSurface.onAccentDefault,
    primaryContainer = AerixSurface.accentContainerDefault,
    onPrimaryContainer = AerixSurface.onAccentContainer,
    secondary = AerixSurface.accentSecondary,
    onSecondary = AerixSurface.canvas,
    secondaryContainer = AerixSurface.panelRaised,
    onSecondaryContainer = AerixSurface.textPrimary,
    tertiary = AerixSurface.success,
    onTertiary = AerixSurface.canvas,
    tertiaryContainer = AerixSurface.panelRaised,
    onTertiaryContainer = AerixSurface.textPrimary,
    error = AerixSurface.danger,
    onError = AerixSurface.canvas,
    errorContainer = AerixSurface.errorDarkContainer,
    onErrorContainer = AerixSurface.textPrimary,
    background = AerixSurface.canvas,
    onBackground = AerixSurface.textPrimary,
    surface = AerixSurface.panel,
    onSurface = AerixSurface.textPrimary,
    surfaceVariant = AerixSurface.panelRaised,
    onSurfaceVariant = AerixSurface.textSecondary,
    outline = AerixSurface.textMuted,
    outlineVariant = AerixSurface.border,
    scrim = Color.Black,
    inverseSurface = AerixSurface.lightTextPrimary,
    inverseOnSurface = AerixSurface.lightPanel,
    inversePrimary = AerixSurface.accentLight,
    surfaceDim = AerixSurface.canvas,
    surfaceBright = AerixSurface.panelRaised,
    surfaceContainerLowest = AerixSurface.canvas,
    surfaceContainerLow = AerixSurface.panelTrack,
    surfaceContainer = AerixSurface.canvas,
    surfaceContainerHigh = AerixSurface.panel,
    surfaceContainerHighest = AerixSurface.panelRaised,
)

/**
 * The CUSTOM theme in Zalith builds a full tonal palette through MaterialKolor. The port keeps the
 * Aerix glass surfaces and swaps only the accent roles, which is what the Aerix shell renders.
 */
private fun customAccentScheme(accent: Color): ColorScheme {
    val onAccent = if (accent.luminance() > 0.45f) AerixSurface.onAccentDefault else Color.White
    return aerixDark.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.33f),
        onPrimaryContainer = AerixSurface.textPrimary,
        inversePrimary = accent
    )
}

@Composable
fun AerixLauncherTheme(content: @Composable () -> Unit) {
    val theme = AerixPreferences.colorTheme
    val custom = AerixPreferences.customColor
    val scheme = remember(theme, custom) {
        when (theme) {
            AerixColorTheme.AERIX -> aerixDark
            AerixColorTheme.CUSTOM -> customAccentScheme(Color(custom))
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AerixTypography,
        content = content
    )
}
