/*
 * Aerix Blocky Shell - shared visual language.
 *
 * Design contract (docs/blocky-shell.md): every option has a BLACK outline, its OWN colour and
 * theme, and white pixel text saying what it is. Motion is stepped rather than eased smoothly,
 * transitions are hard cuts, shadows are hard square steps, and every tap makes a blocky sound.
 */
package net.kdt.pojavlaunch.aerix.ui.blocky

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import net.kdt.pojavlaunch.R

/** One step per sixth of the animation: blocky motion, never a smooth glide. */
object BlockyMotion {
    val StepEasing: Easing = object : Easing {
        override fun transform(fraction: Float): Float {
            val steps = 6
            return (Math.floor(fraction.toDouble() * steps).toInt().coerceAtMost(steps)) / steps.toFloat()
        }
    }

    fun <T> stepped(durationMillis: Int = 180): TweenSpec<T> =
        tween<T>(durationMillis, easing = StepEasing)

    /** A hard cut, used for the shell's section transitions. */
    const val CUT_MS = 0
}

/** The single outline colour used by EVERY option, exactly as specified. */
val BlockyOutline = Color(0xFF07090D)
val BlockyHardShadow = Color(0xDD05070B)
val BlockyPanel = Color(0xF0111824)
val BlockyPanelSoft = Color(0xFF1B2735)
val BlockyWhite = Color(0xFFF7FAFF)
val BlockyMuted = Color(0xFFBAC7D6)
val BlockyFont = FontFamily(Font(R.font.minecraft_ten))

/** Each option gets its own theme: a distinct fill, a darker stepped shade and a pattern. */
enum class BlockyTheme(
    val fill: Color,
    val shade: Color,
    val pattern: BlockyPattern
) {
    GRASS(Color(0xFF4EA83C), Color(0xFF2A6624), BlockyPattern.CHECKER),
    LIME(Color(0xFF8CC63F), Color(0xFF4D7A21), BlockyPattern.DOTS),
    GOLD(Color(0xFFF2B933), Color(0xFF9A7215), BlockyPattern.STRIPES),
    ORANGE(Color(0xFFF07B2F), Color(0xFF9A451A), BlockyPattern.STEPS),
    RED(Color(0xFFDC4351), Color(0xFF8A2330), BlockyPattern.BRICKS),
    PINK(Color(0xFFEE6FA8), Color(0xFF993C6B), BlockyPattern.DOTS),
    VIOLET(Color(0xFF8B5CF6), Color(0xFF533193), BlockyPattern.CHECKER),
    INDIGO(Color(0xFF5A64D8), Color(0xFF343A85), BlockyPattern.STRIPES),
    BLUE(Color(0xFF2E7AD4), Color(0xFF17457F), BlockyPattern.STEPS),
    CYAN(Color(0xFF22B8D6), Color(0xFF126B80), BlockyPattern.BOLTS),
    TEAL(Color(0xFF18A98D), Color(0xFF0C6355), BlockyPattern.GRID),
    SLATE(Color(0xFF54637A), Color(0xFF2C3646), BlockyPattern.BRICKS),
    COCOA(Color(0xFFA26A46), Color(0xFF603A24), BlockyPattern.CHECKER),
    MAGENTA(Color(0xFFC744B8), Color(0xFF76216E), BlockyPattern.BOLTS)
}

enum class BlockyPattern { CHECKER, STRIPES, DOTS, STEPS, GRID, BRICKS, BOLTS }
