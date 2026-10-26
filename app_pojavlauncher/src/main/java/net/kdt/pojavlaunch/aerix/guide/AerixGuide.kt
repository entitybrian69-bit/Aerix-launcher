/*
 * Aerix Launcher UI layer for PojavLauncher.
 * First-run guide: a spotlight tour over the launcher, after Zalith Launcher 2's guide package.
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

package net.kdt.pojavlaunch.aerix.guide

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

/** One step of the tour. A null [anchor] shows the card centred, with nothing highlighted. */
data class GuideStep(
    val anchor: String?,
    val title: String,
    val body: String,
    /** Last step: the button says "Start". */
    val isLast: Boolean = false
)

/**
 * Where the highlighted parts of the launcher are.
 *
 * A component publishes its own bounds by tagging itself with [guideAnchor]; the overlay reads them
 * back. Same idea as Zalith Launcher 2's GuideKeys/GuideRegistry: the guide never guesses at layout,
 * it asks the components where they are, so it stays correct on any screen size or rail width.
 */
class GuideRegistry {
    internal val anchors = mutableStateMapOf<String, Rect>()
}

val LocalGuideRegistry = staticCompositionLocalOf<GuideRegistry?> { null }

/**
 * Marks this element as the target of the tour step named [key].
 *
 * Bounds are in window coordinates, which is what the overlay is measured in too, so nothing has to
 * be converted between the two.
 */
@Composable
fun Modifier.guideAnchor(key: String): Modifier {
    val registry = LocalGuideRegistry.current ?: return this
    return this.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        if (registry.anchors[key] != bounds) registry.anchors[key] = bounds
    }
}

/** Finite, with a real area: bounds from a detached or not-yet-measured component are neither. */
private fun Rect.isUsable(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
        right > left && bottom > top

/**
 * The tour itself: a dark scrim with a cut-out around the current step's component, and a card
 * explaining it. Tapping anywhere advances; the last step closes it.
 *
 * The cut-out is made by drawing the scrim and then punching a rounded hole through it with
 * [BlendMode.Clear] on an offscreen layer - the same approach as Zalith's GuideOverlay, and the
 * reason the highlighted component shows through at full brightness while everything else dims.
 */
@Composable
fun AerixGuideOverlay(
    steps: List<GuideStep>,
    onFinished: () -> Unit
) {
    if (steps.isEmpty()) return
    var index by remember { mutableStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val registry = LocalGuideRegistry.current
    val anchor = step.anchor?.let { registry?.anchors?.get(it) }
    val accent = AerixThemeManager.currentAccent()
    val fade by animateFloatAsState(1f, tween(220), label = "guideFade")
    var origin by remember { mutableStateOf(Offset.Zero) }

    // A step whose component is not on screen yet waits for it instead of pointing at nothing.
    LaunchedEffect(index) {
        if (step.anchor == null) return@LaunchedEffect
        var waited = 0
        while (registry?.anchors?.get(step.anchor) == null && waited < 20) {
            kotlinx.coroutines.delay(100)
            waited++
        }
    }

    val next: () -> Unit = {
        if (index >= steps.lastIndex) {
            onFinished()
        } else {
            index++
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                // Where this overlay starts in the window, so anchor bounds can be expressed
                // relative to it: the overlay lives inside the window insets, the anchors do not.
                val inWindow = coordinates.localToWindow(Offset.Zero)
                if (inWindow != origin) origin = inWindow
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = next
            )
    ) {
        // Bounded sizes only: an unbounded pass would make every "is there room" test meaningless.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 0f
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else 0f
        val density = androidx.compose.ui.platform.LocalDensity.current
        val holePadding = with(density) { 6.dp.toPx() }
        val corner = with(density) { 18.dp.toPx() }
        val cardPadding = with(density) { 16.dp.toPx() }
        val cardSpaceGuess = with(density) { 190.dp.toPx() }

        // The spotlit component in overlay pixels, clipped to the overlay itself.
        //
        // Anchor bounds are not guaranteed to be on canvas. A step can point at a rail item that
        // sits left of this overlay, at a component that is still sliding in, or at one that has
        // scrolled away - and a stale or off-canvas rect used to turn into a negative padding,
        // which Compose rejects with "Padding must be non-negative" (the 1.5.5 guide crash).
        // Whatever is not visibly on screen now simply gets the centred card and no hole.
        val frame = Rect(0f, 0f, width, height)
        val spotlight = anchor
            ?.let { Rect(it.left - origin.x, it.top - origin.y, it.right - origin.x, it.bottom - origin.y) }
            ?.takeIf { it.isUsable() }
            ?.intersect(frame)
            ?.takeIf { it.width > 2f && it.height > 2f }

        // Padding in dp for a pixel offset, clamped into the overlay: never negative, never larger
        // than the space it is applied to, and never NaN or infinite on an odd measurement.
        val paddingPx: (Float, Float) -> Dp = { px, limit ->
            val safe = if (px.isFinite()) px.coerceIn(0f, maxOf(limit - 1f, 0f)) else 0f
            with(density) { safe.toDp() }
        }

        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = fade
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            // The scrim, then the hole punched out of it.
            drawRect(color = Color(0xFF050B12).copy(alpha = 0.82f))
            val hole = spotlight?.let {
                Rect(
                    left = (it.left - holePadding).coerceIn(0f, width),
                    top = (it.top - holePadding).coerceIn(0f, height),
                    right = (it.right + holePadding).coerceIn(0f, width),
                    bottom = (it.bottom + holePadding).coerceIn(0f, height)
                )
            }
            if (hole != null && hole.width > 0f && hole.height > 0f) {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(hole.left, hole.top),
                    size = Size(hole.width, hole.height),
                    cornerRadius = CornerRadius(corner, corner),
                    blendMode = BlendMode.Clear
                )
                // A soft ring so the edge of the hole reads on a busy wallpaper.
                drawRoundRect(
                    color = accent.copy(alpha = 0.75f),
                    topLeft = Offset(hole.left, hole.top),
                    size = Size(hole.width, hole.height),
                    cornerRadius = CornerRadius(corner, corner),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Position the card: beside the spotlight when there is room, otherwise above or below it,
        // and centred for the steps that highlight nothing.
        val card: @Composable (Modifier) -> Unit = { modifier ->
            GuideCard(
                step = step,
                index = index,
                total = steps.size,
                accent = accent,
                modifier = modifier
            )
        }

        if (spotlight == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                card(Modifier.padding(AerixSpacing.lg))
            }
        } else {
            val spaceBelow = height - spotlight.bottom
            val spaceAbove = spotlight.top
            val spaceRight = width - spotlight.right
            if (spaceRight > width * 0.34f) {
                // Wide layouts: the card sits to the right of the spotlight, clear of it.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = paddingPx(spotlight.right + cardPadding, width), end = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) { card(Modifier) }
            } else if (spaceBelow >= cardSpaceGuess || spaceBelow >= spaceAbove) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingPx(spotlight.bottom + cardPadding, height), end = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) { card(Modifier) }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = paddingPx(height - spotlight.top + cardPadding, height)),
                    contentAlignment = Alignment.BottomCenter
                ) { card(Modifier) }
            }
        }
    }
}

/** The card: a title, a line or two of explanation, and the progress of the tour. */
@Composable
private fun GuideCard(
    step: GuideStep,
    index: Int,
    total: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(AerixRadii.panel),
        color = AerixSurface.panelRaised.copy(alpha = 0.96f),
        contentColor = AerixSurface.textPrimary,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(AerixSpacing.md)) {
            Text(
                text = step.title,
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = step.body,
                color = AerixSurface.textPrimary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = AerixSpacing.xs)
            )
            Row(
                modifier = Modifier
                    .padding(top = AerixSpacing.sm)
                    .clip(RoundedCornerShape(AerixRadii.compact))
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
            ) {
                Text(
                    text = if (step.isLast) "Tap to start" else "Tap anywhere to continue",
                    color = accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${index + 1}/$total",
                    color = AerixSurface.textSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
