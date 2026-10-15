/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/components/LiquidGlass.kt
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

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface

/**
 * Glass sheen + rim treatment shared by every Aerix panel, rail and button.
 *
 * A shadow that the system draws needs an extra layer for every panel, and the lists of the
 * launcher are full of them. Only the panels that really float get one, the flat ones get a
 * drawn edge instead: it looks the same and it costs nothing while the list scrolls.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = AerixSurface.glassTint,
    strength: Float = 1f,
    elevation: Dp = AerixMetrics.glassSurfaceElevation,
    clipContent: Boolean = true
): Modifier {
    val intensity = strength.coerceIn(0f, 1f)
    val systemShadow = elevation >= 2.dp
    val shadowed = if (systemShadow) {
        this.shadow(
            elevation = elevation * intensity,
            shape = shape,
            clip = false,
            ambientColor = AerixSurface.glassShadow,
            spotColor = AerixSurface.glassShadow
        )
    } else {
        this
    }
    val glassModifier = if (clipContent) shadowed.clip(shape) else shadowed

    return glassModifier.drawWithCache {
        val width = size.width.coerceAtLeast(1f)
        val height = size.height.coerceAtLeast(1f)
        val sheen = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.15f * intensity),
                tint.copy(alpha = 0.10f * intensity),
                AerixSurface.glassBlue.copy(alpha = 0.075f * intensity),
                AerixSurface.glassViolet.copy(alpha = 0.045f * intensity),
                Color.Transparent
            ),
            start = Offset.Zero,
            end = Offset(width, height)
        )
        val rim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.58f * intensity),
                AerixSurface.glassBlue.copy(alpha = 0.30f * intensity),
                AerixSurface.glassViolet.copy(alpha = 0.22f * intensity),
                Color.White.copy(alpha = 0.42f * intensity)
            ),
            start = Offset.Zero,
            end = Offset(width, height)
        )
        val outline = shape.createOutline(size, layoutDirection, this)
        val rimWidth = AerixSpacing.hairline.toPx() * intensity
        // The drawn shadow of the flat panels, the outline a little lower and a little darker.
        val edgeOffset = if (systemShadow) 0f else (elevation.toPx() * intensity).coerceAtLeast(0f)

        onDrawWithContent {
            if (edgeOffset > 0f) {
                translate(top = edgeOffset) {
                    drawOutline(
                        outline = outline,
                        color = AerixSurface.glassShadow,
                        style = Stroke(width = edgeOffset.coerceAtLeast(1f))
                    )
                }
            }
            drawRect(brush = sheen)
            drawContent()
            if (rimWidth > 0f) {
                drawOutline(outline = outline, brush = rim, style = Stroke(width = rimWidth))
            }
        }
    }
}
