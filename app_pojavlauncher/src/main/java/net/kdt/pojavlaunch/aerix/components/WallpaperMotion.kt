/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Wallpaper motion: the picture itself moves, light sweeps across it, and a particle layer chosen
 * from the biome the wallpaper shows drifts over the top.
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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * What floats over a wallpaper. Picked from what the picture actually shows, so "Cherry Blossom
 * Grove" gets petals and "Nether Lava Cavern" gets embers without any per-wallpaper art.
 */
enum class WallpaperEffect { NONE, PETALS, EMBERS, SNOW, STARS, FIREFLIES, BUBBLES, MOTES }

/**
 * The effect for a wallpaper id. Every bundled wallpaper is covered; a custom image gets motes,
 * which read as sunlight on any photograph.
 */
fun wallpaperEffectFor(id: String): WallpaperEffect = when (id) {
    "wp_01_lush_caves" -> WallpaperEffect.FIREFLIES
    "wp_02_cherry_blossom" -> WallpaperEffect.PETALS
    "wp_03_sunset_river" -> WallpaperEffect.MOTES
    "wp_04_crystal_river" -> WallpaperEffect.MOTES
    "wp_05_nether_fortress" -> WallpaperEffect.EMBERS
    "wp_06_the_end" -> WallpaperEffect.STARS
    "wp_07_ocean_arch" -> WallpaperEffect.MOTES
    "wp_08_moonlit_lake" -> WallpaperEffect.STARS
    "wp_09_flower_meadow" -> WallpaperEffect.PETALS
    "wp_10_spruce_mist" -> WallpaperEffect.MOTES
    "wp_11_cherry_bee" -> WallpaperEffect.PETALS
    "wp_12_snowy_valley" -> WallpaperEffect.SNOW
    "wp_13_frozen_glacier" -> WallpaperEffect.SNOW
    "wp_14_cozy_village" -> WallpaperEffect.MOTES
    "wp_15_end_portal" -> WallpaperEffect.STARS
    "wp_16_ocean_monument" -> WallpaperEffect.BUBBLES
    "wp_17_night_clouds" -> WallpaperEffect.STARS
    "wp_18_crater_harbor" -> WallpaperEffect.PETALS
    "wp_19_sinkhole_falls" -> WallpaperEffect.FIREFLIES
    "wp_20_sakura_sunbeams" -> WallpaperEffect.PETALS
    "wp_21_crimson_forest" -> WallpaperEffect.EMBERS
    "wp_22_amethyst_geode" -> WallpaperEffect.MOTES
    "wp_23_bamboo_dawn" -> WallpaperEffect.MOTES
    "wp_24_badlands_canyon" -> WallpaperEffect.MOTES
    "wp_25_deep_dark" -> WallpaperEffect.FIREFLIES
    else -> WallpaperEffect.MOTES
}

/** How wide the picture breathes, in multiples of the screen. */
private const val ZOOM_MIN = 1.10f
private const val ZOOM_MAX = 1.22f

/**
 * The wallpaper itself, moving.
 *
 * A still photo cannot have its trees sway, but it can be made to *feel* like it is breathing: the
 * picture is drawn as many narrow strips, each nudged on its own by a slow wave. That produces a
 * per-region shimmer over the entire frame - water, haze, drifting air - instead of one flat pan,
 * and it works on every Android version because it is plain drawing, not a shader.
 *
 * On top of that the whole image slowly breathes and pans, so the composition moves as a whole
 * while its parts move against each other.
 */
@Composable
fun AnimatedWallpaper(bitmap: ImageBitmap, motion: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wallpaperMotion")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wallpaperTime"
    )
    Canvas(modifier) {
        val t = time
        val imageW = bitmap.width
        val imageH = bitmap.height
        if (imageW <= 0 || imageH <= 0 || size.width <= 0f || size.height <= 0f) return@Canvas

        // Cover the screen, then add the breathing zoom on top so no edge is ever exposed.
        val zoom = if (motion) ZOOM_MIN + (ZOOM_MAX - ZOOM_MIN) * (0.5f + 0.5f * sin((t * 2f * PI_F).toFloat())) else 1f
        val cover = max(size.width / imageW, size.height / imageH) * zoom
        val drawW = imageW * cover
        val drawH = imageH * cover
        // A slow pan, kept inside the zoom margin.
        val panX = if (motion) (drawW - size.width) * 0.5f * (0.5f + 0.5f * sin((t * 2f * PI_F + 1.7f).toFloat())) else (drawW - size.width) * 0.5f
        val panY = if (motion) (drawH - size.height) * 0.5f * (0.5f + 0.5f * sin((t * 2f * PI_F * 0.7f + 0.4f).toFloat())) else (drawH - size.height) * 0.5f
        val left = -panX
        val top = -panY

        if (!motion) {
            drawCropped(bitmap, left, top, drawW, drawH, 0f, 0f, 0f)
            return@Canvas
        }

        // Strip warp: each column gets its own offset, so the whole frame shimmers.
        val strips = STRIP_COUNT
        val amplitude = size.width * 0.010f
        val drift = size.height * 0.006f
        for (index in 0 until strips) {
            // Overlap by a pixel: a seam between strips would be visible on a bright wallpaper.
            val x0 = size.width * index / strips
            val x1 = size.width * (index + 1) / strips + 1f
            val phase = index * 0.42f
            val wave = sin((t * 2f * PI_F).toFloat() + phase) * amplitude +
                sin((t * 4f * PI_F).toFloat() + phase * 1.7f) * amplitude * 0.45f
            val bob = cos((t * 2f * PI_F * 0.8f).toFloat() + phase * 0.6f) * drift
            drawCropped(bitmap, left, top, drawW, drawH, x0 + wave, x1 + wave, bob)
        }
    }
}

/** 2π as a constant so the trigonometric phases stay readable. */
private const val PI_F = 3.1415927f

/** Draws the columns between [x0] and [x1], sampled from the matching part of the picture. */
private fun DrawScope.drawCropped(
    bitmap: ImageBitmap,
    left: Float,
    top: Float,
    drawW: Float,
    drawH: Float,
    x0: Float,
    x1: Float,
    yOffset: Float
) {
    val imageW = bitmap.width
    val imageH = bitmap.height
    if (drawW <= 0f || drawH <= 0f) return
    // With no column given, the whole picture is one strip.
    val fromX = if (x0 == 0f && x1 == 0f) 0f else x0
    val toX = if (x0 == 0f && x1 == 0f) size.width else x1

    val srcLeft = ((fromX - left) / drawW * imageW).coerceIn(0f, imageW.toFloat())
    val srcRight = ((toX - left) / drawW * imageW).coerceIn(0f, imageW.toFloat())
    val srcWidth = (srcRight - srcLeft)
    if (srcWidth < 0.5f) return

    val dstX = fromX.toInt()
    val dstY = (top + yOffset).toInt()
    drawImageRect(
        image = bitmap,
        srcOffset = IntOffset(srcLeft.toInt(), 0),
        srcSize = IntSize(srcWidth.toInt().coerceAtLeast(1), imageH),
        dstOffset = IntOffset(dstX, dstY),
        dstSize = IntSize((toX - fromX).toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1))
    )
}

/** Columns used by the strip warp. More is smoother, and 44 is invisible to the eye on a phone. */
private const val STRIP_COUNT = 44

/**
 * Light moving across the whole picture: two sun washes that drift, a band of haze that crosses the
 * frame, and the theme accent breathing underneath. This is what makes the parts of a wallpaper
 * that have no particles still feel lit and moving.
 */
@Composable
fun WallpaperLightLayer(accent: Color, effect: WallpaperEffect, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wallpaperLight")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wallpaperLightTime"
    )
    Canvas(modifier) {
        val t = time
        val w = size.width
        val h = size.height
        val diagonal = max(w, h)

        // Two warm washes drifting in opposite corners: the light source appears to move.
        val sunX = w * (0.72f + 0.18f * sin((t * 2f * PI_F).toFloat()))
        val sunY = h * (0.20f + 0.10f * cos((t * 2f * PI_F).toFloat()))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFF0C9).copy(alpha = 0.20f), Color.Transparent),
                center = Offset(sunX, sunY),
                radius = diagonal * 0.60f
            ),
            radius = diagonal * 0.60f,
            center = Offset(sunX, sunY)
        )
        val coolX = w * (0.18f + 0.14f * cos((t * 2f * PI_F * 0.8f + 2.1f).toFloat()))
        val coolY = h * (0.78f + 0.08f * sin((t * 2f * PI_F * 0.8f).toFloat()))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AerixGlassBlue.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(coolX, coolY),
                radius = diagonal * 0.55f
            ),
            radius = diagonal * 0.55f,
            center = Offset(coolX, coolY)
        )

        // A band of light that crosses the frame and comes back, like sun through a moving canopy.
        val bandX = w * (-0.4f + 1.8f * frac(t * 2f))
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.055f), Color.Transparent),
                start = Offset(bandX, 0f),
                end = Offset(bandX + w * 0.55f, h)
            )
        )

        // The theme accent breathing under everything, so the wallpaper belongs to the theme.
        val breath = 0.05f + 0.035f * sin((t * 2f * PI_F * 2f).toFloat())
        drawRect(color = accent.copy(alpha = breath))

        // Warm shimmer for fire, cold sparkle for snow and stars, so each biome lights differently.
        val tint = when (effect) {
            WallpaperEffect.EMBERS -> Color(0xFFFF8A3D).copy(alpha = 0.06f + 0.03f * sin((t * 2f * PI_F * 3f).toFloat()))
            WallpaperEffect.SNOW -> Color(0xFFCFE9FF).copy(alpha = 0.05f)
            WallpaperEffect.STARS -> Color(0xFFB7A9FF).copy(alpha = 0.05f)
            WallpaperEffect.BUBBLES -> Color(0xFF9FE8FF).copy(alpha = 0.05f)
            WallpaperEffect.PETALS -> Color(0xFFFFC7E1).copy(alpha = 0.05f)
            WallpaperEffect.FIREFLIES -> Color(0xFFCBFF8F).copy(alpha = 0.045f)
            WallpaperEffect.MOTES, WallpaperEffect.NONE -> Color.Transparent
        }
        if (tint.alpha > 0f) drawRect(color = tint)
    }
}

/** Mirrors AerixSurface.glassBlue without pulling the theme object into a draw scope. */
private val AerixGlassBlue = Color(0xFF91E9FF)

private fun frac(value: Float): Float {
    val v = value % 1f
    return if (v < 0f) v + 1f else v
}

/**
 * The particle layer for [effect], drawn over the wallpaper and under the scrim.
 *
 * Every particle is a pure function of the shared time value, and the time is read inside the draw
 * block, so a frame costs a few dozen shapes and no recomposition at all.
 */
@Composable
fun WallpaperParticleLayer(effect: WallpaperEffect, accent: Color, modifier: Modifier = Modifier) {
    if (effect == WallpaperEffect.NONE) return
    val particles = remember(effect) { buildParticles(effect) }
    val transition = rememberInfiniteTransition(label = "wallpaperParticles")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wallpaperParticleTime"
    )
    Canvas(modifier) {
        val t = time
        val palette = paletteFor(effect, accent)
        val unit = min(size.width, size.height)
        for (particle in particles) {
            drawParticle(effect, particle, t, palette, unit)
        }
    }
}

/** One particle. Positions are fractions of the screen so the layer scales with any device. */
private class Particle(
    val x: Float,
    val y: Float,
    val speed: Float,
    val size: Float,
    val sway: Float,
    val phase: Float,
    val alpha: Float,
    val tint: Int
)

private fun buildParticles(effect: WallpaperEffect): List<Particle> {
    val count = when (effect) {
        WallpaperEffect.NONE -> 0
        WallpaperEffect.PETALS -> 48
        WallpaperEffect.EMBERS -> 58
        WallpaperEffect.SNOW -> 72
        WallpaperEffect.STARS -> 80
        WallpaperEffect.FIREFLIES -> 34
        WallpaperEffect.BUBBLES -> 30
        WallpaperEffect.MOTES -> 52
    }
    // Fixed seed per effect: the same wallpaper always has the same pattern, so it never
    // reshuffles when you navigate away and back.
    val random = Random(effect.ordinal * 7919 + 13)
    return List(count) {
        val fast = effect == WallpaperEffect.EMBERS || effect == WallpaperEffect.BUBBLES
        Particle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            // How many screen heights the particle crosses in one 24 second cycle.
            speed = if (fast) 0.55f + random.nextFloat() * 0.85f else 0.12f + random.nextFloat() * 0.35f,
            size = when (effect) {
                WallpaperEffect.PETALS -> 0.006f + random.nextFloat() * 0.005f
                WallpaperEffect.SNOW -> 0.002f + random.nextFloat() * 0.003f
                WallpaperEffect.STARS -> 0.0012f + random.nextFloat() * 0.0022f
                WallpaperEffect.EMBERS -> 0.0016f + random.nextFloat() * 0.0026f
                WallpaperEffect.FIREFLIES -> 0.0022f + random.nextFloat() * 0.0022f
                WallpaperEffect.BUBBLES -> 0.004f + random.nextFloat() * 0.006f
                else -> 0.0016f + random.nextFloat() * 0.0028f
            },
            sway = 0.008f + random.nextFloat() * 0.030f,
            phase = random.nextFloat() * 6.283f,
            alpha = when (effect) {
                WallpaperEffect.STARS -> 0.35f + random.nextFloat() * 0.5f
                WallpaperEffect.SNOW -> 0.45f + random.nextFloat() * 0.45f
                WallpaperEffect.PETALS -> 0.35f + random.nextFloat() * 0.4f
                WallpaperEffect.MOTES -> 0.12f + random.nextFloat() * 0.2f
                else -> 0.4f + random.nextFloat() * 0.5f
            },
            tint = random.nextInt(3)
        )
    }
}

private fun paletteFor(effect: WallpaperEffect, accent: Color): List<Color> = when (effect) {
    WallpaperEffect.PETALS -> listOf(
        Color(0xFFFFD1E8), Color(0xFFF9A8D4), accent.copy(alpha = 1f)
    )
    WallpaperEffect.EMBERS -> listOf(
        Color(0xFFFFB347), Color(0xFFFF7A3D), Color(0xFFFFD98A)
    )
    WallpaperEffect.SNOW -> listOf(Color(0xFFFFFFFF), Color(0xFFE8F4FF), Color(0xFFD6E9FF))
    WallpaperEffect.STARS -> listOf(Color(0xFFFFFFFF), Color(0xFFE9EDFF), accent.copy(alpha = 1f))
    WallpaperEffect.FIREFLIES -> listOf(
        Color(0xFFD7FF9E), Color(0xFFBFEF6B), accent.copy(alpha = 1f)
    )
    WallpaperEffect.BUBBLES -> listOf(Color(0xFFD7F4FF), accent.copy(alpha = 1f), Color(0xFFFFFFFF))
    WallpaperEffect.MOTES, WallpaperEffect.NONE -> listOf(
        Color(0xFFFFFFFF), Color(0xFFFFF3D6), accent.copy(alpha = 1f)
    )
}

private fun DrawScope.drawParticle(
    effect: WallpaperEffect,
    particle: Particle,
    t: Float,
    palette: List<Color>,
    unit: Float
) {
    val color = palette[particle.tint % palette.size]
    val radius = particle.size * unit
    when (effect) {
        WallpaperEffect.STARS -> {
            // Fixed in place, only the brightness moves, which is what a night sky does.
            val twinkle = 0.35f + 0.65f * abs(sin(t * 6.283f * 3f + particle.phase))
            drawCircle(
                color.copy(alpha = particle.alpha * twinkle),
                radius = radius,
                center = Offset(particle.x * size.width, particle.y * size.height)
            )
        }
        WallpaperEffect.FIREFLIES -> {
            val x = wrap(particle.x + sin(t * 6.283f + particle.phase) * 0.04f) * size.width
            val y = wrap(particle.y + cos(t * 6.283f * 0.7f + particle.phase * 1.3f) * 0.04f) * size.height
            val pulse = 0.35f + 0.65f * abs(sin(t * 6.283f * 1.6f + particle.phase))
            drawCircle(color.copy(alpha = particle.alpha * pulse * 0.30f), radius = radius * 3.4f, center = Offset(x, y))
            drawCircle(color.copy(alpha = particle.alpha * pulse), radius = radius, center = Offset(x, y))
        }
        WallpaperEffect.BUBBLES -> {
            val y = (1f - wrap(particle.y + particle.speed * t)) * size.height
            val x = particle.x * size.width + sin(t * 6.283f * 1.4f + particle.phase) * particle.sway * size.width
            drawCircle(color.copy(alpha = particle.alpha), radius = radius, center = Offset(x, y), style = Stroke(width = 1.3f))
        }
        WallpaperEffect.EMBERS -> {
            val y = (1f - wrap(particle.y + particle.speed * t)) * size.height
            val x = particle.x * size.width + sin(t * 6.283f * 2f + particle.phase) * particle.sway * size.width
            val flicker = 0.45f + 0.55f * abs(sin(t * 6.283f * 2.6f + particle.phase))
            drawCircle(color.copy(alpha = particle.alpha * flicker), radius = radius, center = Offset(x, y))
        }
        else -> {
            // Petals, snow and motes all fall; only the shape and the speed differ.
            val y = wrap(particle.y + particle.speed * t) * size.height
            val x = particle.x * size.width + sin(t * 6.283f + particle.phase) * particle.sway * size.width
            if (effect == WallpaperEffect.PETALS) {
                drawOval(
                    color.copy(alpha = particle.alpha),
                    topLeft = Offset(x - radius, y - radius * 0.55f),
                    size = Size(radius * 2f, radius * 1.1f)
                )
            } else {
                drawCircle(color.copy(alpha = particle.alpha), radius = radius, center = Offset(x, y))
            }
        }
    }
}

/** Keeps a value inside 0..1 so a particle that leaves one edge comes back in the other. */
private fun wrap(value: Float): Float {
    val wrapped = value % 1f
    return if (wrapped < 0f) wrapped + 1f else wrapped
}
