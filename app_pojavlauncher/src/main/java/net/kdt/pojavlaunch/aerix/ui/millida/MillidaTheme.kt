/*
 * Aerix Millida Shell - shared visual language.
 *
 * Design contract (docs/millida-shell.md): near-black chrome, flat panels with stepped/notched
 * pixel corners and a hard offset shadow, one bright lime-green primary action, a blue "START HERE"
 * instance card, white pixel text, and a five-tab bottom bar whose active tab is a green block.
 * Motion stays stepped, transitions stay hard cuts, and taps keep the blocky square-wave sound.
 */
package net.kdt.pojavlaunch.aerix.ui.millida

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import net.kdt.pojavlaunch.R

/** One step per sixth of the animation; Millida's UI snaps, it never glides. */
object MillidaMotion {
    val StepEasing: Easing = object : Easing {
        override fun transform(fraction: Float): Float {
            val steps = 6
            return (Math.floor(fraction.toDouble() * steps).toInt().coerceAtMost(steps)) / steps.toFloat()
        }
    }

    fun <T> stepped(durationMillis: Int = 180): TweenSpec<T> = tween<T>(durationMillis, easing = StepEasing)
    const val CUT_MS = 0
}

object MillidaPalette {
    val Bg = Color(0xFF141416)
    val Bar = Color(0xFF101012)
    val Panel = Color(0xFF1E1E21)
    val PanelSoft = Color(0xFF29292D)
    val Chip = Color(0xFF333337)
    val Edge = Color(0xFF3B3B40)
    val Outline = Color(0xFF08080A)
    val Shadow = Color(0xFF070708)
    val Text = Color(0xFFF4F4F6)
    val Muted = Color(0xFFA6A6AE)
    val Green = Color(0xFF5CD65C)
    val GreenDark = Color(0xFF2F8A34)
    val GreenDeep = Color(0xFF173F1A)
    val Blue = Color(0xFF1D4EA8)
    val BlueDark = Color(0xFF102F6C)
    val Orange = Color(0xFFB8791A)
    val Pink = Color(0xFFB3204B)
    val Red = Color(0xFFC42F4B)
    val Purple = Color(0xFF5B3D9E)
    val Teal = Color(0xFF177A7A)
    val Yellow = Color(0xFFE8C84A)
    val Grass = Color(0xFF3C9C3E)
    val GrassDark = Color(0xFF1D5A22)
    val Scene = Color(0xFF103A18)
    val SceneGlow = Color(0xFF2C7A32)
}

val MillidaFont = FontFamily(Font(R.font.minecraft_ten))

/** Coloured promo cards: Millida gives each destination its own flat colour and white pixel label. */
enum class MillidaTone(
    val fill: Color,
    val dark: Color,
    val highlight: Color,
    val label: Int,
) {
    GREEN(MillidaPalette.Green, MillidaPalette.GreenDark, Color(0xFF9BF29B), R.string.aerix_millida_tone_play),
    BLUE(MillidaPalette.Blue, MillidaPalette.BlueDark, Color(0xFF6FA8F5), R.string.aerix_millida_tone_instance),
    ORANGE(MillidaPalette.Orange, Color(0xFF7A4C10), Color(0xFFF2B84C), R.string.aerix_millida_tone_builds),
    PINK(MillidaPalette.Pink, Color(0xFF741431), Color(0xFFF07098), R.string.aerix_millida_tone_shop),
    PURPLE(MillidaPalette.Purple, Color(0xFF382464), Color(0xFFB79AF5), R.string.aerix_millida_tone_character),
    TEAL(MillidaPalette.Teal, Color(0xFF0E4F4F), Color(0xFF6BD4D4), R.string.aerix_millida_tone_social),
    RED(MillidaPalette.Red, Color(0xFF7A1C2E), Color(0xFFF27A8E), R.string.aerix_millida_tone_servers),
    YELLOW(MillidaPalette.Yellow, Color(0xFF9A7C1E), Color(0xFFFFEFA0), R.string.aerix_millida_tone_news),
}
