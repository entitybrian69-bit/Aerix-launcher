/*
 * Aerix Launcher — motion primitives for the v2 rebuild.
 *
 * These are the pieces that make a tap feel physical: a card that tilts toward the finger,
 * springs back elastically, keeps a specular highlight where you touch it and settles with a
 * soft overshoot. Everything is driven by the touch position, never by a scripted loop, so no
 * two presses look the same.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.aerix.ui.glass.Glass
import net.kdt.pojavlaunch.aerix.ui.glass.GlassMotion

/** One ripple, drawn in scene space so it can spill outside the card it started in. */
private class Spill(val origin: Offset, val anim: Animatable<Float, AnimationVector1D>)

/**
 * A card with real physicality.
 *
 * - tilts up to [maxTilt] degrees toward the finger while held (parallax on its content)
 * - springs back with a touch of overshoot on release
 * - ripple rings spill from the touch point, clipped to the card's shape
 * - a specular highlight follows the finger
 * - [entranceDelayMs] staggers the card in with a rise + fade when a screen appears
 */
@Composable
fun InteractiveCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color = Glass.raised,
    borderColor: Color = Glass.border,
    selected: Boolean = false,
    maxTilt: Float = 4.5f,
    pressScale: Float = 0.975f,
    entranceDelayMs: Int = 0,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val accent = Glass.accent
    val scope = rememberCoroutineScope()
    val ripples = remember { mutableStateListOf<Spill>() }
    var pressed by remember { mutableStateOf(false) }
    var pressAt by remember { mutableStateOf(Offset.Unspecified) }

    // Entrance: a 40ms-staggered rise, so a grid assembles instead of appearing.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (entranceDelayMs > 0) kotlinx.coroutines.delay(entranceDelayMs.toLong())
        enter.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )
    val tiltX by animateFloatAsState(
        targetValue = if (pressed && pressAt != Offset.Unspecified) 0f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "cardTiltX"
    )

    // Live rim for the focused card: it breathes, so the eye keeps landing on it.
    val breath by rememberInfiniteTransition(label = "cardBreath").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cardBreathValue"
    )

    Box(
        modifier = modifier
            .zIndex(if (pressed) 2f else 0f)
            .graphicsLayer {
                val e = enter.value
                alpha = e
                val lift = (1f - e) * 18f
                translationY = lift
                scaleX = scale * (0.96f + 0.04f * e)
                scaleY = scale * (0.96f + 0.04f * e)
                // Tilt follows the finger: pushing near an edge leans that edge away.
                if (pressed && pressAt != Offset.Unspecified) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    rotationY = ((pressAt.x - cx) / cx) * maxTilt
                    rotationX = -((pressAt.y - cy) / cy) * maxTilt
                } else {
                    rotationY = tiltX
                    rotationX = 0f
                }
                cameraDistance = 24f * density
            }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = (tint.alpha + 0.10f).coerceAtMost(1f)), tint)
                )
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent.copy(alpha = 0.55f + 0.35f * breath) else borderColor,
                shape = shape
            )
            .pointerInput(onClick) {
                if (onClick == null) return@pointerInput
                detectTapGestures(
                    onPress = { position ->
                        pressed = true
                        pressAt = position
                        val ripple = Spill(position, Animatable(0f))
                        ripples += ripple
                        scope.launch {
                            ripple.anim.animateTo(1f, tween(GlassMotion.RIPPLE, easing = FastOutSlowInEasing))
                            ripples.remove(ripple)
                        }
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) onClick.invoke()
                    }
                )
            }
    ) {
        // Ripples are painted above the fill, below the content.
        Canvas(modifier = Modifier.matchParentSize()) {
            ripples.forEach { ripple ->
                val v = ripple.anim.value
                val radius = size.minDimension * (0.06f + 0.55f * v)
                drawCircle(
                    color = accent.copy(alpha = (1f - v) * 0.28f),
                    radius = radius,
                    center = ripple.origin,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx() * (1f - v) + 0.4f
                    )
                )
            }
        }
        // Specular highlight parked under the finger while held.
        if (pressed && pressAt != Offset.Unspecified) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                        center = pressAt,
                        radius = size.minDimension * 0.9f
                    ),
                    radius = size.minDimension * 0.9f,
                    center = pressAt
                )
            }
        }
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** A pill that fills from the left and springs its knob: the v2 toggle. */
@Composable
fun SpringToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val accent = Glass.accent
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "toggle"
    )
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) accent.copy(alpha = 0.9f) else Glass.track)
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .pointerInput(checked) { detectTapGestures { onCheckedChange(!checked) } }
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    translationX = progress * 20.dp.toPx()
                    val s = 1f - 0.12f * (1f - progress)
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(9.dp))
                .background(if (checked) Glass.onAccent else Glass.textPrimary.copy(alpha = 0.85f))
        )
    }
}

/** Segmented control (All / Vanilla / Modded) whose selection slides, never snaps. */
@Composable
fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Glass.accent
    val move by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "segment"
    )
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        val itemWidth = maxWidth / options.size
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(Glass.track.copy(alpha = 0.55f))
                .border(1.dp, Glass.borderSoft, RoundedCornerShape(14.dp))
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { translationX = move * itemWidth.toPx() }
                    .size(itemWidth, 38.dp)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent)
            )
            Row(modifier = Modifier.fillMaxSize()) {
                options.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .size(itemWidth, 38.dp)
                            .pointerInput(index) { detectTapGestures { onSelect(index) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (index == selectedIndex) Glass.onAccent else Glass.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** A number that counts up instead of jumping, used for badges and counters. */
@Composable
fun AnimatedCount(value: Int, modifier: Modifier = Modifier, fontSize: Int = 40) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "count"
    )
    Text(
        text = animated.toInt().toString(),
        modifier = modifier,
        color = Glass.textPrimary,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold
    )
}

/** Skeleton shimmer: replaces every "loading" state with something alive. */
@Composable
fun SkeletonLine(widthFraction: Float = 0.6f, height: Dp = 12.dp, modifier: Modifier = Modifier) {
    val shimmer by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "skeletonValue"
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Glass.track.copy(alpha = 0.5f),
                        Glass.track.copy(alpha = 0.5f + 0.35f * (1f - kotlin.math.abs(shimmer - 0.5f) * 2f)),
                        Glass.track.copy(alpha = 0.5f)
                    )
                )
            )
    ) {
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0f })
    }
}

/** A chip ("Manage", "Mods") that lifts and brightens under the finger. */
@Composable
fun ActionChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "chipScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Glass.accent.copy(alpha = 0.18f) else Glass.track.copy(alpha = 0.55f))
            .border(1.dp, if (selected) Glass.accent.copy(alpha = 0.7f) else Glass.borderSoft, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) onClick()
                    }
                )
            }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Glass.accent else Glass.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Screen title with the letterspacing from the concepts. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = Glass.textPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Vertical rhythm shared by the v2 screens. */
@Composable
fun ColumnSpacer(height: Dp) {
    Column(modifier = Modifier.height(height)) {}
}
