/*
 * Aerix Launcher — the v2 glass language.
 *
 * Every screen in the rebuild is drawn from these pieces: a glass surface that presses with a
 * spring, a specular band that glides across it, touch ripples at the exact finger position, a
 * breathing glow for whatever is alive, and small live widgets (presence dot, waveform, aurora).
 *
 * The point is that "everything reacts": nothing here is a static rectangle. A screen that only
 * wants to look right can use [GlassSurface]; a screen that wants motion gets it for free.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.glass

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import kotlin.math.sin

/** Colour tokens for the rebuild. Same palette as the launcher, one place to change it. */
object Glass {
    val canvas get() = AerixSurface.canvas
    val panel get() = AerixSurface.panel
    val raised get() = AerixSurface.panelRaised
    val track get() = AerixSurface.panelTrack
    val border get() = AerixSurface.border
    val borderSoft get() = AerixSurface.borderSoft
    val accent @Composable get() = AerixSurface.accent
    val accentStatic = AerixSurface.accentDefault
    val lilac = AerixSurface.accentSecondary
    val onAccent = AerixSurface.onAccentDefault
    val textPrimary = Color(0xFFF1F6FA)
    val textSecondary = Color(0xFFA8B9C5)
    val textMuted = Color(0xFF7D909D)
    val success = Color(0xFF73E5B0)
    val warning = Color(0xFFFFC777)
    val danger = Color(0xFFFF7189)
    val story = Color(0xFFE11D48)
}

object GlassMotion {
    const val PRESS = 140
    const val RIPPLE = 900
    const val SWEEP = 2600
    const val BREATH = 2600
    const val SCREEN = 200
}

/** One touch, spreading outward from [origin]. */
private class Ripple(val origin: Offset, val anim: Animatable<Float, AnimationVector1D>)

/**
 * Glass card with life in it.
 *
 * - presses with a spring to [pressScale] and back
 * - glides a specular band across its face while idle (or only on press with [sweepOnPress])
 * - spawns ripple rings at the finger on every tap
 * - optional [glow] draws a breathing halo, for whatever is "live"
 * - optional [selected] adds the mint rim used for the focused card
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color = Glass.raised,
    borderColor: Color = Glass.border,
    selected: Boolean = false,
    glow: Boolean = false,
    sweepOnPress: Boolean = false,
    pressScale: Float = 0.97f,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val accent = Glass.accent
    val ripples = remember { mutableStateListOf<Ripple>() }
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
        label = "glassScale"
    )
    val breath by rememberInfiniteTransition(label = "glassBreath").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GlassMotion.BREATH, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glassBreathValue"
    )
    val sweep by rememberInfiniteTransition(label = "glassSweep").animateFloat(
        initialValue = -0.7f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(tween(GlassMotion.SWEEP, easing = LinearEasing)),
        label = "glassSweepValue"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // A focused card sits a little closer to the player.
                shadowElevation = if (selected) 24f else 8f
            }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = (tint.alpha + 0.12f).coerceAtMost(1f)),
                        tint.copy(alpha = (tint.alpha - 0.08f).coerceAtLeast(0.35f))
                    )
                )
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent.copy(alpha = 0.85f) else borderColor,
                shape = shape
            )
            .pointerInput(onClick, onLongClick) {
                if (onClick == null && onLongClick == null) return@pointerInput
                detectTapGestures(
                    onPress = { position ->
                        pressed = true
                        val ripple = Ripple(position, Animatable(0f))
                        ripples += ripple
                        scope.launch {
                            ripple.anim.animateTo(1f, tween(GlassMotion.RIPPLE, easing = FastOutSlowInEasing))
                            ripples.remove(ripple)
                        }
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) onClick?.invoke()
                    },
                    onLongPress = onLongClick?.let { { it() } }
                )
            }
    ) {
        // Breathing halo behind a live surface.
        if (glow) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.20f * breath), Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.7f
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.12f)
                )
            }
        }
        // Specular band.
        Canvas(modifier = Modifier.matchParentSize()) {
            val position = if (sweepOnPress) (if (pressed) 0.5f else -0.7f) else sweep
            val bandWidth = size.width * 0.45f
            val x = size.width * position
            rotate(10f, pivot = Offset(size.width / 2f, size.height / 2f)) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent),
                        startX = x - bandWidth,
                        endX = x + bandWidth
                    ),
                    topLeft = Offset(x - bandWidth, 0f),
                    size = Size(bandWidth * 2f, size.height)
                )
            }
        }
        // Ripple rings on top of the fill but under the content.
        Canvas(modifier = Modifier.matchParentSize()) {
            ripples.forEach { ripple ->
                val v = ripple.anim.value
                val radius = size.minDimension * (0.05f + 0.45f * v)
                drawCircle(
                    color = accent.copy(alpha = (1f - v) * 0.30f),
                    radius = radius,
                    center = ripple.origin,
                    style = Stroke(width = 2.dp.toPx() * (1f - v) + 0.5f)
                )
            }
        }
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** A labelled glass tile: icon over text, the shape used by the in-game menu and quick actions. */
@Composable
fun GlassTile(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    badge: Int = 0,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        selected = selected,
        sweepOnPress = true,
        contentPadding = 12.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) icon()
            if (subtitle == null) {
                Box(
                    modifier = Modifier.height(6.dp)
                )
            }
            Text(
                text = title,
                color = Glass.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Glass.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Glass.story)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (badge > 9) "9+" else badge.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Presence dot: green online, lilac in a party, grey offline, with a slow pulse when live. */
@Composable
fun PresenceDot(state: Presence, size: Dp = 8.dp) {
    val color = when (state) {
        Presence.ONLINE -> Glass.success
        Presence.PARTY -> Glass.lilac
        Presence.AWAY -> Glass.warning
        Presence.OFFLINE -> Glass.textMuted
    }
    val pulse by rememberInfiniteTransition(label = "presence").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastSlowOut), RepeatMode.Reverse),
        label = "presencePulse"
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { alpha = if (state == Presence.OFFLINE) 0.6f else pulse }
            .clip(RoundedCornerShape(size / 2))
            .background(color)
    )
}

private val FastSlowOut = FastOutSlowInEasing

enum class Presence { ONLINE, PARTY, AWAY, OFFLINE }

/** The live voice waveform: bars driven by [level] (0..1), never a fixed picture. */
@Composable
fun LiveWaveform(
    level: Float,
    modifier: Modifier = Modifier,
    bars: Int = 28,
    color: Color = Glass.accentStatic
) {
    val clock = rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "wavePhase"
    )
    Canvas(modifier = modifier) {
        val barWidth = size.width / (bars * 1.6f)
        repeat(bars) { index ->
            val phase = clock + index * 0.45f
            val wobble = 0.35f + 0.65f * (0.5f + 0.5f * sin(phase))
            val height = size.height * (0.15f + 0.85f * wobble * (0.35f + 0.65f * level.coerceIn(0f, 1f)))
            val x = index * barWidth * 1.6f
            drawLine(
                color = color.copy(alpha = 0.55f + 0.45f * wobble),
                start = Offset(x, size.height / 2f - height / 2f),
                end = Offset(x, size.height / 2f + height / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Aurora + drifting motes, drawn behind a screen so it is never a flat colour. */
@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val accent = Glass.accent
    val time by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "auroraTime"
    )
    BoxWithConstraints(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val drift = sin(time * 6.2831855f) * w * 0.06f
            drawOval(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.10f * intensity), Color.Transparent),
                    center = Offset(w * 0.7f + drift, h * 0.12f),
                    radius = w * 0.5f
                ),
                topLeft = Offset(w * 0.2f + drift, -h * 0.3f),
                size = Size(w * 0.9f, h * 0.8f)
            )
            drawOval(
                brush = Brush.radialGradient(
                    listOf(Glass.lilac.copy(alpha = 0.08f * intensity), Color.Transparent),
                    center = Offset(w * 0.18f - drift, h * 0.05f),
                    radius = w * 0.4f
                ),
                topLeft = Offset(-w * 0.15f - drift, -h * 0.32f),
                size = Size(w * 0.7f, h * 0.7f)
            )
        }
    }
}

/** Small icon chip used in every header: presses, springs, never inert. */
@Composable
fun GlassIconButton(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 40.dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size / 2),
        tint = if (selected) Glass.accent.copy(alpha = 0.9f) else Glass.track.copy(alpha = 0.85f),
        selected = selected,
        pressScale = 0.9f,
        contentPadding = 0.dp,
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

/** Anything that is loading gets a sweeping shimmer instead of a dead bar. */
@Composable
fun ShimmerBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val accent = Glass.accent
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmerValue"
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Glass.track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = progress.coerceIn(0f, 1f); transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                .clip(RoundedCornerShape(height / 2))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.85f - 0.5f * shimmer),
                            accent
                        )
                    )
                )
        )
    }
}
