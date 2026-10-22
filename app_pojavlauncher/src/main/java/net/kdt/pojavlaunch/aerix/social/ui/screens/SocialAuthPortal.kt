/*
 * Aerix Launcher Social layer.
 *
 * The Google entry screen, built from the chosen concept: a mint energy portal holding the
 * Google mark, the player standing at its edge, one wide "Continue with Google" pill and two
 * quiet links underneath.
 *
 * Everything moves and everything reacts to touch: the portal breathes and its arcs rotate, the
 * player bobs, dust drifts, and a press sends ripples out of the finger, fires light arcs into
 * the portal and flares the player's rim light. Idle motion never stops, so the screen never
 * looks like a still image.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.auth.GoogleAccount
import net.kdt.pojavlaunch.aerix.social.auth.GoogleAuth
import net.kdt.pojavlaunch.aerix.social.auth.GoogleAuthResult
import net.kdt.pojavlaunch.aerix.social.auth.GoogleSignInVariant
import net.kdt.pojavlaunch.aerix.social.ui.SocialBody
import net.kdt.pojavlaunch.aerix.social.ui.SocialColors
import net.kdt.pojavlaunch.aerix.social.ui.SocialDimens
import kotlin.math.min
import kotlin.math.sin

/** One touch, spreading: [anim] runs 0..1 and the drawing fades as it grows. */
private class PortalRipple(val origin: Offset, val anim: Animatable<Float, AnimationVector1D>)

/** A drifting mote of light in the air around the portal. */
private class Mote(val x: Float, val y: Float, val radius: Float, val speed: Float, val phase: Float)

/**
 * The Google entry screen: portal, player, one wide pill. Real Credential Manager sign-in
 * happens on tap; nothing is typed here.
 */
@Composable
fun PortalAuthScreen(
    inProgress: Boolean,
    googleError: String?,
    stage: String?,
    lastEmail: String,
    onStartGoogle: (creatingAccount: Boolean, variant: GoogleSignInVariant) -> Unit,
    onStartBrowser: () -> Unit,
    browserReady: Boolean,
    onNotNow: () -> Unit
) {
    val busy = inProgress
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // The flow itself runs in the ViewModel (see SocialProfileViewModel.signInWithGoogle), so
    // leaving the app to pick a Google account cannot cancel it and leave this screen stuck.
    fun startGoogle(creating: Boolean, variant: GoogleSignInVariant = GoogleSignInVariant.SHEET) {
        if (busy) return
        onStartGoogle(creating, variant)
    }

    // Touch feedback state, shared by every animated layer of the scene.
    val ripples = remember { mutableStateListOf<PortalRipple>() }
    val power = remember { Animatable(0f) }
    var portalCenter by remember { mutableStateOf(Offset.Zero) }
    var pillCenter by remember { mutableStateOf(Offset.Zero) }
    var pillTopLeft by remember { mutableStateOf(Offset.Zero) }

    fun touch(origin: Offset) {
        val ripple = PortalRipple(origin, Animatable(0f))
        ripples += ripple
        scope.launch {
            power.snapTo(1f)
            power.animateTo(0f, tween(1400, easing = LinearOutSlowInEasing))
        }
        scope.launch {
            ripple.anim.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing))
            ripples.remove(ripple)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val accent = SocialColors.accent
        val accentBright = SocialColors.accentBright
        val short = min(maxWidth.value, maxHeight.value)
        val portalSize = (short * 0.52f).coerceIn(180f, 420f).dp

        Box(modifier = Modifier.fillMaxSize()) {
            AmbientLayer(busy = busy, power = { power.value }, accent = accent, accentBright = accentBright)

            // The portal sits slightly above centre so the pill has room underneath.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-(short * 0.06f)).dp)
                    .size(portalSize)
                    .onGloballyPositioned { coords ->
                        portalCenter = coords.positionInParent() +
                            Offset(coords.size.width / 2f, coords.size.height / 2f)
                    }
            ) {
                PortalRing(
                    busy = busy,
                    power = { power.value },
                    modifier = Modifier.fillMaxSize()
                )
                Image(
                    painter = painterResource(R.drawable.aerix_social_ic_google),
                    contentDescription = "Google",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(portalSize * 0.42f)
                        .graphicsLayer { alpha = 0.95f }
                )
            }

            // The player, standing at the portal's edge, leaning in while the button is held.
            BlockyPlayer(
                power = { power.value },
                busy = busy,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = -(portalSize * 0.34f), y = (portalSize * 0.30f))
                    .size(portalSize * 0.52f)
            )

            // Controls: one wide pill, two quiet links, then the small print.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Where the flow is right now: opening the sheet, signing in, or what failed.
                if (busy && !stage.isNullOrBlank()) {
                    StatusPill(text = stage, accent = true)
                }
                if (!busy && googleError != null) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 460.dp)
                            .clip(RoundedCornerShape(SocialDimens.controlRadius))
                            .background(Color(0xCC2A1218))
                            .border(1.dp, SocialColors.danger.copy(alpha = 0.8f), RoundedCornerShape(SocialDimens.controlRadius))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SocialBody(
                            "Google sign-in did not finish",
                            size = 12.sp,
                            color = SocialColors.danger,
                            weight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        SocialBody(googleError, size = 11.sp, color = SocialColors.textPrimary)
                        SocialBody(
                            "Full trace: launcher Logs, tag AerixGoogle.",
                            size = 10.sp,
                            color = SocialColors.textMuted
                        )
                        // Two escapes from a refused sheet: the other Play services picker, or
                        // the exact text to paste back to me.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LinkText(
                                text = "Try other picker",
                                enabled = !busy,
                                onClick = {
                                    startGoogle(creating = false, variant = GoogleSignInVariant.PICKER)
                                }
                            )
                            LinkText(
                                text = "Copy details",
                                enabled = true,
                                onClick = { clipboard.setText(AnnotatedString(googleError.orEmpty())) }
                            )
                        }
                    }
                }

                PortalGoogleButton(
                    text = when {
                        busy && !stage.isNullOrBlank() -> stage
                        busy -> "Waiting for Google…"
                        else -> "Continue with Google"
                    },
                    enabled = !busy,
                    accent = accent,
                    onTap = { startGoogle(creating = false) },
                    // Ripples are drawn in scene coordinates, so the finger position inside the
                    // pill is shifted by the pill's own offset.
                    onPress = { offsetInPill -> touch(pillTopLeft + offsetInPill) },
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(0.62f)
                        .onGloballyPositioned { coords ->
                            pillTopLeft = coords.positionInParent()
                            pillCenter = pillTopLeft + Offset(coords.size.width / 2f, coords.size.height / 2f)
                        }
                )

                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinkText(
                        text = "Create an account",
                        enabled = !busy,
                        onClick = { startGoogle(creating = true) }
                    )
                    LinkText(text = "Not now", enabled = !busy, onClick = onNotNow)
                }

                // The escape hatch from the Play services sheet: the same Google login, in the
                // browser. Shown with the other links so it is there before anything fails.
                if (browserReady) {
                    LinkText(
                        text = if (busy) "Waiting for the browser…" else "Sign in in the browser",
                        enabled = !busy,
                        onClick = onStartBrowser
                    )
                }
                SocialBody("Privacy · Terms", size = 10.sp, color = SocialColors.textMuted.copy(alpha = 0.8f))
            }

            // Account chip: the address this device last signed in with.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = SocialDimens.pagePaddingH, vertical = 8.dp)
                    .heightIn(min = 32.dp)
                    .clip(CircleShape)
                    .background(Color(0x55121F2B))
                    .border(1.dp, SocialColors.border, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(SocialColors.panelSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_person_outlined),
                        contentDescription = null,
                        tint = SocialColors.textPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = lastEmail.ifBlank { "Not signed in" },
                    color = SocialColors.textPrimary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 190.dp)
                )
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_check),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(12.dp)
                )
            }

            // Ripples on top of everything, so a tap is felt across the whole scene.
            Canvas(modifier = Modifier.fillMaxSize()) {
                ripples.forEach { ripple ->
                    val v = ripple.anim.value
                    val radius = size.minDimension * (0.03f + 0.22f * v)
                    drawCircle(
                        color = accent.copy(alpha = (1f - v) * 0.35f),
                        radius = radius,
                        center = ripple.origin,
                        style = Stroke(width = 2.dp.toPx() * (1f - v) + 0.5f)
                    )
                }
                // Light arcs from the pill into the portal while a press is fading out.
                val p = power.value
                if (p > 0.02f && pillCenter != Offset.Zero && portalCenter != Offset.Zero) {
                    for (index in 0..1) {
                        val bend = if (index == 0) -90f else 70f
                        val control = Offset(
                            (pillCenter.x + portalCenter.x) / 2f + bend,
                            min(pillCenter.y, portalCenter.y) - 40f + index * 20f
                        )
                        val path = Path().apply {
                            moveTo(pillCenter.x, pillCenter.y)
                            quadraticBezierTo(control.x, control.y, portalCenter.x, portalCenter.y)
                        }
                        drawPath(
                            path = path,
                            color = accentBright.copy(alpha = p * (0.55f - index * 0.18f)),
                            style = Stroke(width = (1.6f - index * 0.4f).dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ layers ----- */

/** Aurora, drifting motes and a soft vignette: the screen never sits still. */
@Composable
private fun AmbientLayer(
    busy: Boolean,
    power: () -> Float,
    accent: Color,
    accentBright: Color
) {
    val clock = rememberInfiniteTransition(label = "ambient")
    val time by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "time"
    )
    val motes = remember {
        val random = java.util.Random(7)
        List(26) {
            Mote(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = 0.8f + random.nextFloat() * 2.2f,
                speed = 0.3f + random.nextFloat() * 0.9f,
                phase = random.nextFloat()
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Aurora ribbons: two wide, very soft bands sliding across the top half.
        val drift = sin(time * 2f * Math.PI.toFloat()) * w * 0.08f
        drawRect(
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                startY = 0f,
                endY = h * 0.55f
            )
        )
        drawOval(
            brush = Brush.radialGradient(
                listOf(accentBright.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(w * 0.72f + drift, h * 0.18f),
                radius = w * 0.42f
            ),
            topLeft = Offset(w * 0.34f + drift, -h * 0.22f),
            size = Size(w * 0.8f, h * 0.6f)
        )
        drawOval(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(w * 0.2f - drift, h * 0.1f),
                radius = w * 0.34f
            ),
            topLeft = Offset(-w * 0.12f, -h * 0.26f),
            size = Size(w * 0.62f, h * 0.55f)
        )

        // Motes rise slowly and twinkle; a press pulls them toward the finger.
        val boost = 1f + power() * 2.2f
        motes.forEach { mote ->
            val progress = ((time * mote.speed + mote.phase) % 1f)
            val x = mote.x * w + sin(progress * 6.28f * 2f) * 10f
            val y = h * (1f - progress) - (mote.speed * 20f)
            val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(progress * 6.28f * 3f))
            drawCircle(
                color = accentBright.copy(alpha = (0.25f + 0.35f * twinkle) * (if (busy) 1f else 0.85f)),
                radius = mote.radius.dp.toPx() * boost,
                center = Offset(x, y)
            )
        }

        // Vignette keeps the pill readable over a busy wallpaper.
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0x33030B12), Color(0x66030B12)),
                startY = h * 0.45f,
                endY = h
            )
        )
    }
}

/** The portal: breathing glow, counter-rotating arcs, ground ripples. */
@Composable
private fun PortalRing(
    busy: Boolean,
    power: () -> Float,
    modifier: Modifier = Modifier
) {
    val accent = SocialColors.accent
    val accentBright = SocialColors.accentBright
    val transition = rememberInfiniteTransition(label = "portal")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (busy) 6000 else 18000, easing = LinearEasing)),
        label = "spin"
    )
    val counterSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "counterSpin"
    )
    val breath by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.78f * breath
        val hot = power()

        // Halo
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    accent.copy(alpha = 0.34f + hot * 0.3f),
                    accent.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * 1.55f
            ),
            radius = radius * 1.55f,
            center = center
        )
        // Glass interior
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color(0xCC0B2B2C),
                    Color(0x8A0C2230),
                    Color(0x33071219)
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
        // Rim
        drawCircle(
            color = accentBright.copy(alpha = 0.75f + hot * 0.2f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(
            color = accent.copy(alpha = 0.30f),
            radius = radius * 0.9f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        // Two arcs orbiting in opposite directions, plus one that appears on touch.
        rotate(spin, center) {
            drawArc(
                color = accentBright.copy(alpha = 0.95f),
                startAngle = 0f,
                sweepAngle = 78f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = accent.copy(alpha = 0.5f),
                startAngle = 150f,
                sweepAngle = 44f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        rotate(-counterSpin, center) {
            drawArc(
                color = accent.copy(alpha = 0.45f),
                startAngle = 300f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 1.06f, center.y - radius * 1.06f),
                size = Size(radius * 2.12f, radius * 2.12f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        if (hot > 0.01f) {
            rotate(spin * 0.6f, center) {
                drawArc(
                    color = Color.White.copy(alpha = 0.35f * hot),
                    startAngle = 40f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 1.02f, center.y - radius * 1.02f),
                    size = Size(radius * 2.04f, radius * 2.04f),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Ground: the portal is standing on a surface, so its light pools around it.
        for (index in 0..2) {
            val spread = 1f + index * 0.16f
            drawOval(
                color = accent.copy(alpha = 0.26f - index * 0.07f),
                topLeft = Offset(center.x - radius * spread, center.y + radius * 0.92f),
                size = Size(radius * 2f * spread, radius * 0.42f * spread),
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
    }
}

/** A blocky player silhouette with a mint rim light that flares on touch. */
@Composable
private fun BlockyPlayer(
    power: () -> Float,
    busy: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = SocialColors.accent
    val clock = rememberInfiniteTransition(label = "player")
    val time by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (busy) 1400 else 3200, easing = LinearEasing)),
        label = "playerTime"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val hot = power()
        val bob = sin(time * 6.28f * 2f) * h * 0.012f
        val lean = hot * w * 0.05f

        // Rim glow behind the body.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.28f + hot * 0.35f), Color.Transparent),
                center = Offset(w * 0.45f, h * 0.42f),
                radius = w * 0.7f
            ),
            radius = w * 0.7f,
            center = Offset(w * 0.45f, h * 0.42f)
        )

        val dark = Color(0xFF08131C)
        val rim = accent.copy(alpha = 0.55f + hot * 0.45f)
        val headSize = w * 0.34f
        val bodyWidth = w * 0.36f
        val bodyHeight = h * 0.34f
        val legWidth = w * 0.15f
        val legHeight = h * 0.30f
        val baseX = w * 0.5f - bodyWidth / 2f + lean
        val bodyTop = h * 0.36f + bob
        val headTop = bodyTop - headSize - h * 0.015f
        val corner = CornerRadius(3.dp.toPx())

        // Legs, slightly apart and swaying.
        val step = sin(time * 6.28f) * w * 0.015f
        drawRoundRect(dark, Offset(baseX + step, bodyTop + bodyHeight), Size(legWidth, legHeight), corner)
        drawRoundRect(dark, Offset(baseX + bodyWidth - legWidth - step, bodyTop + bodyHeight), Size(legWidth, legHeight), corner)
        // Body, arms, head.
        drawRoundRect(dark, Offset(baseX, bodyTop), Size(bodyWidth, bodyHeight), corner)
        drawRoundRect(dark, Offset(baseX - legWidth * 0.85f, bodyTop + h * 0.02f), Size(legWidth * 0.8f, bodyHeight * 0.9f), corner)
        drawRoundRect(dark, Offset(baseX + bodyWidth + legWidth * 0.05f, bodyTop + h * 0.02f), Size(legWidth * 0.8f, bodyHeight * 0.9f), corner)
        drawRoundRect(dark, Offset(w * 0.5f - headSize / 2f + lean, headTop), Size(headSize, headSize), corner)

        // Rim light on the portal side of every block.
        val rimWidth = 1.6.dp.toPx()
        drawRoundRect(rim, Offset(baseX, bodyTop), Size(rimWidth, bodyHeight), corner)
        drawRoundRect(rim, Offset(w * 0.5f - headSize / 2f + lean, headTop), Size(rimWidth, headSize), corner)
        drawRoundRect(rim, Offset(baseX + step, bodyTop + bodyHeight), Size(rimWidth, legHeight), corner)
        // A touch brightens the face, as if the portal light reaches it.
        if (hot > 0.01f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.White.copy(alpha = 0.18f * hot), Color.Transparent),
                    startX = w * 0.5f - headSize / 2f,
                    endX = w * 0.5f + headSize / 2f
                ),
                topLeft = Offset(w * 0.5f - headSize / 2f + lean, headTop),
                size = Size(headSize, headSize),
                cornerRadius = corner
            )
        }
    }
}

/** The wide pill: real Google mark, spring press, specular sweep, haptic ripple. */
@Composable
private fun PortalGoogleButton(
    text: String,
    enabled: Boolean,
    accent: Color,
    onTap: () -> Unit,
    onPress: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
        label = "pillScale"
    )
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0.55f,
        animationSpec = tween(180),
        label = "pillGlow"
    )
    val sweep = rememberInfiniteTransition(label = "sweep")
    val sweepPosition by sweep.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "sweepPosition"
    )

    Box(
        modifier = modifier
            .heightIn(min = 54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = 18f * glow
                shape = CircleShape
                clip = false
            }
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (enabled) Color(0xFFB9FBEF) else accent.copy(alpha = 0.4f),
                        if (enabled) accent else accent.copy(alpha = 0.32f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { position ->
                        pressed = true
                        onPress(position)
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) onTap()
                    }
                )
            }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // Specular band gliding across the glass.
            val bandWidth = size.width * 0.35f
            val x = size.width * sweepPosition
            rotate(12f, pivot = Offset(size.width / 2f, size.height / 2f)) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                        startX = x - bandWidth,
                        endX = x + bandWidth
                    ),
                    topLeft = Offset(x - bandWidth, 0f),
                    size = Size(bandWidth * 2f, size.height)
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_social_ic_google),
                    contentDescription = null,
                    tint = Color(0xFF1B1B1F),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                color = SocialColors.onAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Quiet bottom links that underline and brighten on press. */
@Composable
private fun LinkText(text: String, enabled: Boolean, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (pressed) 1f else 0.72f, tween(140), label = "linkAlpha")
    Text(
        text = text,
        color = SocialColors.textPrimary.copy(alpha = if (enabled) alpha else 0.4f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) onClick()
                    }
                )
            }
            .padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

/** Small pill that says what the flow is doing, so the button is never silently dead. */
@Composable
private fun StatusPill(text: String, accent: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent) SocialColors.accent.copy(alpha = 0.16f) else SocialColors.panelSoft)
            .border(
                1.dp,
                if (accent) SocialColors.accent.copy(alpha = 0.6f) else SocialColors.border,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (accent) SocialColors.accent else SocialColors.textMuted)
        )
        SocialBody(text, size = 11.sp, color = if (accent) SocialColors.accent else SocialColors.textSecondary)
    }
}
