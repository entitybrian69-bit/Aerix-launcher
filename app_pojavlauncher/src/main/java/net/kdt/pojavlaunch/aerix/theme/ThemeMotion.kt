/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Motion for the theme itself: the accent pulses, selected elements glow, and it all stops the
 * moment the switch in Settings is turned off.
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

package net.kdt.pojavlaunch.aerix.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences

/**
 * The animated half of the theme: the accent breathes and selected elements carry a soft glow.
 *
 * The setting is a Compose state read straight out of [AerixPreferences], which is why turning it
 * off takes effect on the spot - there is nothing to reload and no reason to restart the launcher.
 * Elements that are not selected never start an animation at all, so a screen full of cards costs
 * the same as one glowing card.
 */
object ThemeMotion {
    /**
     * Whether motion should run now: the user's switch is on **and** the system is not asking for
     * animations to be removed. Both are read as state, so a change applies immediately.
     */
    @Composable
    fun rememberEnabled(): Boolean {
        val context = LocalContext.current
        val preference = AerixPreferences.themeMotion
        val systemAllows = remember(context) {
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                ) > 0f
            }.getOrDefault(true)
        }
        return preference && systemAllows
    }

    /**
     * [base] with the accent breathing between full strength and a little dimmer. Returns [base]
     * untouched - and starts no animation - unless [active] and the setting is on.
     */
    @Composable
    fun rememberPulse(base: Color, active: Boolean, enabled: Boolean = rememberEnabled()): Color {
        if (!enabled || !active) return base
        val transition = rememberInfiniteTransition(label = "themeMotion")
        val factor by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.58f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "accentPulse"
        )
        return base.copy(alpha = base.alpha * factor)
    }
}

/**
 * A soft accent bloom laid *over* the element, drawn as a gradient rather than a blur: a blur pass
 * per card is what makes this kind of effect stutter on a mid-range phone, and a gradient looks the
 * same for a fraction of the cost.
 *
 * It is drawn over the content because a card's own opaque background would hide anything drawn
 * behind it. The alpha stays low enough that text on top of it is still perfectly readable.
 */
fun Modifier.accentGlow(color: Color, active: Boolean, enabled: Boolean): Modifier {
    if (!enabled || !active) return this
    return drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.16f),
                    color.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxOf(size.width, size.height) * 0.78f
            )
        )
    }
}

/**
 * Stacked borders that read as a glowing lining: two faint rings outside a solid one.
 *
 * [active] selects whether it is drawn at all; the caller passes the colour it wants, which lets a
 * pulsing accent carry the whole effect without any extra animation here.
 */
fun Modifier.accentRing(
    color: Color,
    shape: Shape,
    active: Boolean,
    enabled: Boolean,
    width: Dp = 1.dp
): Modifier {
    if (!enabled || !active) return this
    return this
        .border(width * 4f, color.copy(alpha = 0.08f), shape)
        .border(width * 2.2f, color.copy(alpha = 0.20f), shape)
        .border(width, color, shape)
}
