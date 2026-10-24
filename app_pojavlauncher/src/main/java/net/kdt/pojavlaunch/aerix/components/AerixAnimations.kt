/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Twenty five animated wallpapers, drawn at run time. Nothing here is a video file: each scene is
 * layered gradients, silhouettes, light and particles, so all 25 together cost the APK nothing.
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The signature motion of a scene. Everything else - sky, hills, water, particles - is shared, so a
 * scene is defined by the light it has and the colour it is made of rather than by bespoke code.
 */
enum class SceneFeature { SUN, MOON, AURORA, LAVA, WAVES, PORTAL, GEODE, PULSE, RAYS, MIST, NONE }

/** One animated wallpaper. */
data class AerixAnimation(
    val id: String,
    val title: String,
    val biome: String,
    val accent: Color,
    val feature: SceneFeature,
    val particles: WallpaperEffect,
    /** Sky, top to horizon. Two or three stops read best. */
    val sky: List<Color>,
    /** Distant to near silhouettes, painted back to front. */
    val hills: List<Color>,
    /** Water or ground plane at the bottom, null when the scene has none. */
    val water: Color? = null
)

private fun scene(
    id: String,
    title: String,
    biome: String,
    accent: Color,
    feature: SceneFeature,
    particles: WallpaperEffect,
    sky: List<Color>,
    hills: List<Color>,
    water: Color? = null
) = AerixAnimation(id, title, biome, accent, feature, particles, sky, hills, water)

/** The 25 animated wallpapers, in the same order as the 25 photographic ones. */
val AerixAnimations: List<AerixAnimation> = listOf(
    scene("anim_01_lush_cavern", "Lush Cavern Glow", "Lush Caves • Cavern light", Color(0xFF2DD4BF), SceneFeature.RAYS, WallpaperEffect.FIREFLIES,
        listOf(Color(0xFF05231F), Color(0xFF0B3B33), Color(0xFF14614F)), listOf(Color(0xFF0A2E28), Color(0xFF0D3A31), Color(0xFF15574A)), Color(0xFF1E6F5C)),
    scene("anim_02_cherry_grove", "Cherry Grove Drift", "Cherry Grove • Petal fall", Color(0xFFF472B6), SceneFeature.SUN, WallpaperEffect.PETALS,
        listOf(Color(0xFF3A1B33), Color(0xFF7C3F63), Color(0xFFE8A0BE)), listOf(Color(0xFF4A2242), Color(0xFF6B3355), Color(0xFF8E4A6C))),
    scene("anim_03_sunset_river", "Golden Sunset", "River Valley • Sunset", Color(0xFFF59E0B), SceneFeature.SUN, WallpaperEffect.MOTES,
        listOf(Color(0xFF2B1440), Color(0xFF8E3B2E), Color(0xFFF0A44A)), listOf(Color(0xFF3B2338), Color(0xFF5C3346), Color(0xFF7A4453)), Color(0xFFC97A3A)),
    scene("anim_04_crystal_river", "Crystal River", "Birch River • Morning", Color(0xFF06B6D4), SceneFeature.RAYS, WallpaperEffect.MOTES,
        listOf(Color(0xFF07202E), Color(0xFF12556B), Color(0xFF63C7D6)), listOf(Color(0xFF0B3442), Color(0xFF124755), Color(0xFF1C6472)), Color(0xFF2E93A6)),
    scene("anim_05_nether_lava", "Nether Emberfall", "Nether Wastes • Lava light", Color(0xFFF97316), SceneFeature.LAVA, WallpaperEffect.EMBERS,
        listOf(Color(0xFF1A0705), Color(0xFF4A130A), Color(0xFFA33212)), listOf(Color(0xFF240B07), Color(0xFF3C140C), Color(0xFF5C2113))),
    scene("anim_06_the_end", "The End Void", "The End • Obsidian pillars", Color(0xFFA855F7), SceneFeature.PORTAL, WallpaperEffect.STARS,
        listOf(Color(0xFF06040F), Color(0xFF150C2A), Color(0xFF2F1B4E)), listOf(Color(0xFF0C0821), Color(0xFF1A1136), Color(0xFF2A1A4C))),
    scene("anim_07_ocean_arch", "Sunlit Arch", "Warm Ocean • Surf", Color(0xFF38BDF8), SceneFeature.WAVES, WallpaperEffect.MOTES,
        listOf(Color(0xFF06304C), Color(0xFF1C7BA8), Color(0xFF9FE6F5)), listOf(Color(0xFF0A4763), Color(0xFF12688A), Color(0xFF1D8BAE)), Color(0xFF2FA8C9)),
    scene("anim_08_moonlit_lake", "Moonlit Lake", "Night Forest • Moonlight", Color(0xFF818CF8), SceneFeature.MOON, WallpaperEffect.STARS,
        listOf(Color(0xFF050A1B), Color(0xFF0E1B3D), Color(0xFF243A6B)), listOf(Color(0xFF081026), Color(0xFF101C3A), Color(0xFF1B2C55)), Color(0xFF1B3462)),
    scene("anim_09_flower_meadow", "Flower Meadow", "Flower Forest • Sunrise", Color(0xFFEC4899), SceneFeature.SUN, WallpaperEffect.PETALS,
        listOf(Color(0xFF33204A), Color(0xFF9B5A7A), Color(0xFFF3B6A8)), listOf(Color(0xFF3E2C3C), Color(0xFF5C4450), Color(0xFF7C5E62))),
    scene("anim_10_spruce_mist", "Spruce Mist", "Old Growth Taiga • Fog", Color(0xFF84CC16), SceneFeature.MIST, WallpaperEffect.MOTES,
        listOf(Color(0xFF0C1A16), Color(0xFF23403A), Color(0xFF6E8F84)), listOf(Color(0xFF0B211C), Color(0xFF16342C), Color(0xFF234A3E))),
    scene("anim_11_cherry_canopy", "Honeybee Canopy", "Cherry Canopy • Sunbeams", Color(0xFFFBBF24), SceneFeature.RAYS, WallpaperEffect.PETALS,
        listOf(Color(0xFF3B1E12), Color(0xFF8A4A24), Color(0xFFE7B45C)), listOf(Color(0xFF43261A), Color(0xFF6B3C25), Color(0xFF8F5330))),
    scene("anim_12_snowy_peaks", "Snowy Peaks", "Jagged Peaks • Cold light", Color(0xFF60A5FA), SceneFeature.SUN, WallpaperEffect.SNOW,
        listOf(Color(0xFF0B1B33), Color(0xFF2E5C8A), Color(0xFFBFD9EF)), listOf(Color(0xFF14263D), Color(0xFF243B55), Color(0xFF3A5673))),
    scene("anim_13_glacier", "Glacier Aurora", "Frozen Peaks • Aurora", Color(0xFF22D3EE), SceneFeature.AURORA, WallpaperEffect.SNOW,
        listOf(Color(0xFF04121F), Color(0xFF0A3145), Color(0xFF16607B)), listOf(Color(0xFF08202E), Color(0xFF103546), Color(0xFF1A4A5E)), Color(0xFF1E6377)),
    scene("anim_14_plains_village", "Village Morning", "Plains Village • Hearth smoke", Color(0xFF10B981), SceneFeature.SUN, WallpaperEffect.MOTES,
        listOf(Color(0xFF12303A), Color(0xFF3F7C7A), Color(0xFFD8D3A8)), listOf(Color(0xFF183A34), Color(0xFF245046), Color(0xFF32685A))),
    scene("anim_15_end_portal", "Portal Starfield", "Stronghold • Portal", Color(0xFF14B8A6), SceneFeature.PORTAL, WallpaperEffect.STARS,
        listOf(Color(0xFF03110F), Color(0xFF0A2A28), Color(0xFF11584F)), listOf(Color(0xFF061C1A), Color(0xFF0C2E2B), Color(0xFF154440))),
    scene("anim_16_ocean_monument", "Deep Monument", "Deep Ocean • Current", Color(0xFF3B82F6), SceneFeature.WAVES, WallpaperEffect.BUBBLES,
        listOf(Color(0xFF05182E), Color(0xFF0A3355), Color(0xFF12629B)), listOf(Color(0xFF071F38), Color(0xFF0B2F4E), Color(0xFF114367)), Color(0xFF1A5E8C)),
    scene("anim_17_starry_night", "Starry Night", "Plains Night • Solas", Color(0xFF8B5CF6), SceneFeature.MOON, WallpaperEffect.STARS,
        listOf(Color(0xFF040616), Color(0xFF0C1130), Color(0xFF1D2352)), listOf(Color(0xFF070A20), Color(0xFF0F1430), Color(0xFF191F44)), Color(0xFF16224E)),
    scene("anim_18_crater_village", "Crater Village", "Cherry Basin • Evening", Color(0xFFFB7185), SceneFeature.SUN, WallpaperEffect.PETALS,
        listOf(Color(0xFF2A1424), Color(0xFF7B3550), Color(0xFFE08C86)), listOf(Color(0xFF33192A), Color(0xFF4E263C), Color(0xFF6C374F)), Color(0xFF8A4760)),
    scene("anim_19_sinkhole_falls", "Sinkhole Falls", "Dripstone • Waterfall", Color(0xFF2DD4BF), SceneFeature.RAYS, WallpaperEffect.FIREFLIES,
        listOf(Color(0xFF061B1C), Color(0xFF0E3A3A), Color(0xFF2D7F72)), listOf(Color(0xFF08231F), Color(0xFF103430), Color(0xFF1A4C42)), Color(0xFF226B5C)),
    scene("anim_20_sakura_sunbeam", "Sakura Sunbeams", "Cherry Glade • Light shafts", Color(0xFFE879F9), SceneFeature.RAYS, WallpaperEffect.PETALS,
        listOf(Color(0xFF2A1030), Color(0xFF7A2E63), Color(0xFFE7A1CE)), listOf(Color(0xFF33173A), Color(0xFF54224E), Color(0xFF743160))),
    scene("anim_21_crimson_forest", "Crimson Embers", "Crimson Forest • Embers", Color(0xFFE11D48), SceneFeature.LAVA, WallpaperEffect.EMBERS,
        listOf(Color(0xFF1C040A), Color(0xFF4E0A1C), Color(0xFF9C1B33)), listOf(Color(0xFF240711), Color(0xFF3A0D1B), Color(0xFF571628))),
    scene("anim_22_amethyst_geode", "Amethyst Geode", "Geode • Crystal shimmer", Color(0xFFA855F7), SceneFeature.GEODE, WallpaperEffect.MOTES,
        listOf(Color(0xFF150A24), Color(0xFF3A1A5C), Color(0xFF7A48A8)), listOf(Color(0xFF1C0F2E), Color(0xFF2C1745), Color(0xFF432461)), Color(0xFF4E2C74)),
    scene("anim_23_bamboo_dawn", "Bamboo Dawn", "Bamboo Jungle • Sunrise", Color(0xFF84CC16), SceneFeature.RAYS, WallpaperEffect.MOTES,
        listOf(Color(0xFF161E0A), Color(0xFF3F5C1A), Color(0xFFD7C86A)), listOf(Color(0xFF1C2A0C), Color(0xFF2C4212), Color(0xFF3F5C1C))),
    scene("anim_24_badlands_canyon", "Canyon River", "Badlands • Heat haze", Color(0xFFF97316), SceneFeature.SUN, WallpaperEffect.MOTES,
        listOf(Color(0xFF2A1408), Color(0xFF7A3A18), Color(0xFFE0A05A)), listOf(Color(0xFF3A1E10), Color(0xFF5C2F1A), Color(0xFF7E4423)), Color(0xFF8A4E2A)),
    scene("anim_25_deep_dark", "Sculk Abyss", "Deep Dark • Sculk pulse", Color(0xFF06B6D4), SceneFeature.PULSE, WallpaperEffect.FIREFLIES,
        listOf(Color(0xFF02090D), Color(0xFF062A31), Color(0xFF0B4A52)), listOf(Color(0xFF041417), Color(0xFF08242A), Color(0xFF0D3940)))
)

/** The animation with this id, or null when the id names a photograph instead. */
fun animationById(id: String): AerixAnimation? = AerixAnimations.firstOrNull { it.id == id }

/** True when [id] selects one of the animated wallpapers rather than a photo. */
fun isAnimationId(id: String): Boolean = id.startsWith("anim_")

/**
 * Draws one animated wallpaper full screen: sky, light, layered landscape, water and particles, all
 * driven by a single time value read inside the draw block, so a frame is a redraw and never a
 * recomposition.
 */
@Composable
fun AnimatedScene(animation: AerixAnimation, motion: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scene")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sceneTime"
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawScene(animation, if (motion) time else 0f)
        }
        if (motion) {
            WallpaperParticleLayer(
                effect = animation.particles,
                accent = animation.accent,
                modifier = Modifier.fillMaxSize()
            )
            WallpaperLightLayer(
                accent = animation.accent,
                effect = animation.particles,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private const val TAU = 6.2831855f

/**
 * Paints a scene at a fixed moment. The wallpaper grid uses this for its previews: a scene drawn at
 * a chosen time is far closer to the real thing than any gradient stand-in, and a still frame costs
 * nothing while twenty five of them sit on screen.
 */
fun DrawScope.paintScenePreview(animation: AerixAnimation, moment: Float = 0.18f) {
    drawScene(animation, moment)
}

private fun DrawScope.drawScene(animation: AerixAnimation, t: Float) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.62f

    // 1. Sky, with a slow vertical drift so the gradient itself moves.
    val skyShift = 0.04f * sin(t * TAU)
    drawRect(
        brush = Brush.verticalGradient(
            colors = animation.sky,
            startY = -h * skyShift,
            endY = horizon + h * skyShift
        )
    )

    // 2. The signature light of the scene, behind the landscape.
    when (animation.feature) {
        SceneFeature.SUN -> drawSun(w, horizon, t, animation.accent, setting = true)
        SceneFeature.MOON -> drawSun(w, horizon * 0.62f, t, Color(0xFFDCE6FF), setting = false)
        SceneFeature.AURORA -> drawAurora(w, horizon, t)
        SceneFeature.LAVA -> drawLavaLight(w, h, horizon, t)
        SceneFeature.PORTAL -> drawPortal(w, horizon, t, animation.accent)
        SceneFeature.GEODE -> drawGeode(w, horizon, t, animation.accent)
        SceneFeature.PULSE -> drawPulse(w, horizon, t, animation.accent)
        SceneFeature.RAYS -> drawRays(w, h, horizon, t, animation.accent)
        SceneFeature.MIST -> drawMist(w, h, horizon, t)
        SceneFeature.WAVES, SceneFeature.NONE -> drawSun(w, horizon, t, animation.accent, setting = false)
    }

    // 2b. Clouds, built the way Minecraft builds them: long flat slabs of blocks sliding across the
    //     sky, each row at its own height and speed.
    val cloudBlock = max(5f, w / 46f)
    for (row in 0 until 3) {
        val rowY = h * (0.10f + 0.085f * row)
        val speed = 0.02f + 0.012f * row
        val span = w * 1.6f
        val offset = ((t * span * speed * 6f) + row * span * 0.37f) % span
        var blocks = 0
        while (blocks < 9) {
            val segment = (row * 5 + blocks * 3) % 11
            if (segment < 5) {
                val bx = -w * 0.3f + offset - span * 0.5f + blocks * span * 0.11f
                drawRect(
                    color = Color.White.copy(alpha = 0.055f + 0.012f * row),
                    topLeft = Offset(bx, rowY + (row % 2) * h * 0.012f),
                    size = Size(cloudBlock * (2 + segment % 3), cloudBlock)
                )
            }
            blocks++
        }
    }

    // 3. Land, painted back to front as blocky terrain: every column is one block wide and its
    //    height snaps to whole blocks, so the ground reads as cubes instead of as a smooth hill.
    //    Each layer drifts at its own speed, which is what reads as depth.
    val block = max(6f, w / 26f)
    animation.hills.forEachIndexed { index, color ->
        val depth = (index + 1f) / animation.hills.size
        val baseY = horizon - h * 0.10f + h * 0.085f * index
        val amplitude = h * (0.035f + 0.03f * index)
        val phase = index * 1.7f
        val drift = sin(t * TAU * (0.10f + 0.04f * index) + phase) * w * 0.010f * depth
        var x = -w * 0.1f + drift
        while (x <= w * 1.1f) {
            val wave = sin((x / w) * 3.1f + phase + t * TAU * 0.12f) * amplitude * 0.6f +
                sin((x / w) * 7.3f + phase * 2f) * amplitude * 0.28f
            val steps = (wave / (block * 0.9f)).toInt()
            val top = baseY - steps * block * 0.9f
            drawRect(
                color = color,
                topLeft = Offset(x, top),
                size = Size(block + 0.7f, h - top)
            )
            x += block
        }
    }

    // 3b. Trees on the nearest ground: two-block trunks under square canopies that lean with the
    //     wind, the way they do in game.
    val front = animation.hills.lastOrNull()
    if (front != null) {
        val trunkColor = Color(front.red * 0.42f, front.green * 0.34f, front.blue * 0.30f, 1f)
        val leafColor = Color(
            (front.red * 0.55f + animation.accent.red * 0.18f).coerceIn(0f, 1f),
            (front.green * 0.62f + animation.accent.green * 0.14f).coerceIn(0f, 1f),
            (front.blue * 0.48f + animation.accent.blue * 0.12f).coerceIn(0f, 1f),
            1f
        )
        val treeBaseY = h * 0.78f
        val trunkBlock = block * 0.9f
        for (i in 0 until 4) {
            val sway = sin(t * TAU * 0.22f + i * 1.3f) * block * 0.35f
            val treeX = w * (0.12f + 0.24f * i) + sway
            // Trunk: three blocks tall, one block wide.
            drawRect(
                color = trunkColor,
                topLeft = Offset(treeX, treeBaseY - trunkBlock * 3f),
                size = Size(trunkBlock, trunkBlock * 3f)
            )
            // Canopy: a plus-shaped cluster of leaf blocks, slightly wider than the trunk.
            val canopyTop = treeBaseY - trunkBlock * 6.4f
            for (cx in -1..1) {
                for (cy in 0..2) {
                    if ((cx == -1 || cx == 1) && cy == 0) continue
                    drawRect(
                        color = leafColor,
                        topLeft = Offset(treeX + cx * trunkBlock, canopyTop + cy * trunkBlock),
                        size = Size(trunkBlock + 0.7f, trunkBlock + 0.7f)
                    )
                }
            }
        }
    }

    // 4. Water or ground in front, with the ripples that make it read as liquid.
    val water = animation.water
    if (water != null) {
        val waterTop = h * 0.80f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(water.copy(alpha = 0.95f), water.copy(alpha = 0.55f)),
                startY = waterTop,
                endY = h
            ),
            topLeft = Offset(0f, waterTop),
            size = Size(w, h - waterTop)
        )
        // Ripple lines: each one a slow sine, lit from the scene's own accent.
        val lines = 12
        for (i in 0 until lines) {
            val y = waterTop + (h - waterTop) * (i + 0.5f) / lines
            val amplitude = (h - waterTop) * 0.012f * (1f + i * 0.08f)
            val path = Path().apply {
                var x = 0f
                moveTo(0f, y)
                while (x <= w) {
                    lineTo(x, y + sin((x / w) * 9.4f + t * TAU * (1.2f + i * 0.06f) + i) * amplitude)
                    x += w / 30f
                }
            }
            drawPath(
                path,
                color = animation.accent.copy(alpha = 0.10f + 0.05f * sin(t * TAU * 2f + i)),
                style = Stroke(width = max(1f, h * 0.0016f))
            )
        }
    }

    // 5. Vignette, so text stays readable over any scene.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0xFF04101A).copy(alpha = 0.42f)),
            center = Offset(w * 0.5f, horizon * 0.8f),
            radius = max(w, h) * 0.78f
        )
    )
}

private fun DrawScope.drawSun(w: Float, horizon: Float, t: Float, color: Color, setting: Boolean) {
    val h = size.height
    val x = w * (0.62f + 0.10f * sin(t * TAU * 0.6f))
    val y = if (setting) horizon - h * 0.10f + h * 0.02f * sin(t * TAU) else horizon * 0.55f
    val radius = h * 0.09f
    // Halo, core, and a warm wash across the horizon.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(x, y),
            radius = radius * 5.2f
        ),
        radius = radius * 5.2f,
        center = Offset(x, y)
    )
    // Minecraft's own sun and moon are square textures, so the core is a square with a soft glow
    // behind it rather than a disc.
    drawRect(
        color = Color(0xFFFFF6E0).copy(alpha = 0.94f),
        topLeft = Offset(x - radius, y - radius),
        size = Size(radius * 2f, radius * 2f)
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, color.copy(alpha = 0.18f)),
            startY = horizon - h * 0.18f,
            endY = horizon
        ),
        topLeft = Offset(0f, horizon - h * 0.18f),
        size = Size(w, h * 0.18f)
    )
}

private fun DrawScope.drawAurora(w: Float, horizon: Float, t: Float) {
    val bands = 4
    for (band in 0 until bands) {
        val baseY = horizon * (0.38f + band * 0.09f)
        val hue = if (band % 2 == 0) Color(0xFF4EF0C0) else Color(0xFF8F7BFF)
        val path = Path()
        path.moveTo(-w * 0.05f, baseY)
        var x = -w * 0.05f
        while (x <= w * 1.05f) {
            val y = baseY + sin((x / w) * 2.6f + t * TAU * 1.1f + band) * size.height * 0.045f
            path.lineTo(x, y)
            x += w / 28f
        }
        drawPath(
            path,
            brush = Brush.verticalGradient(
                colors = listOf(hue.copy(alpha = 0f), hue.copy(alpha = 0.28f), Color.Transparent),
                startY = baseY - size.height * 0.12f,
                endY = baseY + size.height * 0.10f
            ),
            style = Stroke(width = size.height * 0.035f)
        )
    }
}

private fun DrawScope.drawLavaLight(w: Float, h: Float, horizon: Float, t: Float) {
    // Cracks of light across the ground, breathing like a cooling flow.
    val cracks = 7
    for (i in 0 until cracks) {
        val baseY = horizon + h * (0.05f + 0.05f * i)
        val path = Path()
        var x = 0f
        path.moveTo(0f, baseY)
        while (x <= w) {
            val y = baseY +
                sin((x / w) * 5.2f + i * 1.3f + t * TAU * 0.5f) * h * 0.016f +
                sin((x / w) * 13f + i) * h * 0.006f
            path.lineTo(x, y)
            x += w / 26f
        }
        val pulse = 0.35f + 0.35f * sin(t * TAU * 1.5f + i * 0.9f)
        drawPath(path, Color(0xFFFF6A1A).copy(alpha = 0.10f + pulse * 0.12f), style = Stroke(width = h * 0.012f))
        drawPath(path, Color(0xFFFFD08A).copy(alpha = 0.25f + pulse * 0.35f), style = Stroke(width = h * 0.003f))
    }
}

private fun DrawScope.drawPortal(w: Float, horizon: Float, t: Float, accent: Color) {
    val center = Offset(w * 0.5f, horizon * 0.66f)
    val base = minOf(w, size.height) * 0.16f
    for (ring in 0 until 5) {
        val radius = base * (1f + ring * 0.42f)
        val alpha = 0.30f - ring * 0.045f
        drawCircle(
            color = accent.copy(alpha = alpha),
            radius = radius,
            center = center,
            style = Stroke(width = size.height * 0.004f)
        )
    }
    // Swirl: a few arcs rotating at different speeds.
    for (arm in 0 until 7) {
        val angle = t * TAU * (1f + arm * 0.12f) + arm * 0.9f
        val r = base * (1.2f + 0.5f * sin(t * TAU * 2f + arm))
        val dot = Offset(center.x + cos(angle) * r, center.y + sin(angle) * r * 0.9f)
        drawCircle(Color(0xFFEAD9FF).copy(alpha = 0.55f), radius = size.height * 0.006f, center = dot)
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.45f), Color.Transparent),
            center = center,
            radius = base * 3.4f
        ),
        radius = base * 3.4f,
        center = center
    )
}

private fun DrawScope.drawGeode(w: Float, horizon: Float, t: Float, accent: Color) {
    // Crystal facets catching light one after another.
    val facets = 9
    for (i in 0 until facets) {
        val x = w * (0.12f + 0.76f * (i / (facets - 1f)))
        val y = horizon * (0.5f + 0.22f * sin(i * 1.7f))
        val facetW = w * 0.055f
        val facetH = size.height * (0.10f + 0.05f * abs(sin(i * 2.1f)))
        val path = Path().apply {
            moveTo(x, y - facetH)
            lineTo(x + facetW, y)
            lineTo(x, y + facetH)
            lineTo(x - facetW, y)
            close()
        }
        val shimmer = 0.18f + 0.24f * abs(sin(t * TAU * 1.3f + i * 0.8f))
        drawPath(path, accent.copy(alpha = shimmer))
        drawPath(path, Color(0xFFE9D8FF).copy(alpha = 0.35f + 0.3f * shimmer), style = Stroke(width = size.height * 0.0025f))
    }
}

private fun DrawScope.drawPulse(w: Float, h: Float, t: Float, accent: Color) {
    // Sculk: rings spreading from the floor.
    val center = Offset(w * 0.5f, h * 0.86f)
    for (ring in 0 until 6) {
        val phase = (t + ring / 6f) % 1f
        val radius = max(w, h) * 0.10f + phase * max(w, h) * 0.55f
        drawCircle(
            color = accent.copy(alpha = (1f - phase) * 0.20f),
            radius = radius,
            center = center,
            style = Stroke(width = size.height * 0.006f)
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent),
            center = center,
            radius = max(w, h) * 0.45f
        ),
        radius = max(w, h) * 0.45f,
        center = center
    )
}

private fun DrawScope.drawRays(w: Float, h: Float, horizon: Float, t: Float, accent: Color) {
    val origin = Offset(w * (0.35f + 0.10f * sin(t * TAU * 0.4f)), horizon * 0.42f)
    val rays = 8
    for (i in 0 until rays) {
        val angle = -0.35f + i * 0.16f + 0.05f * sin(t * TAU + i)
        val length = h * 1.2f
        val end = Offset(origin.x + cos(angle + 1.2f) * length, origin.y + sin(angle + 0.2f) * length)
        val alpha = 0.05f + 0.05f * sin(t * TAU * 1.2f + i * 0.7f)
        drawLine(
            color = Color(0xFFFFF3D0).copy(alpha = alpha),
            start = origin,
            end = end,
            strokeWidth = w * (0.05f + 0.02f * (i % 3))
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF3D0).copy(alpha = 0.35f), accent.copy(alpha = 0.10f), Color.Transparent),
            center = origin,
            radius = h * 0.42f
        ),
        radius = h * 0.42f,
        center = origin
    )
}

private fun DrawScope.drawMist(w: Float, h: Float, horizon: Float, t: Float) {
    for (layer in 0 until 3) {
        val y = horizon - h * 0.05f + h * 0.06f * layer
        val drift = ((t * (0.6f + layer * 0.25f)) % 1f) * w
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.10f - layer * 0.02f), Color.Transparent),
                startX = -w + drift,
                endX = drift + w * 0.6f
            ),
            topLeft = Offset(0f, y),
            size = Size(w, h * 0.16f)
        )
    }
}
