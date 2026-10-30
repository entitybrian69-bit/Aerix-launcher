package net.kdt.pojavlaunch.aerix.client

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object LiquidCrystalPalette {
    val IceWhite = Color(0xFFF6FDFF)
    val IceSub = Color(0xFFBFE4EE)
    val IceMuted = Color(0xFF83AAB8)
    val PrismCyan = Color(0xFF9AF6FF)
    val PrismMint = Color(0xFF8DEFE0)
    val OwnerCrimson = Color(0xFFFF4769)
    val CoOwnerSky = Color(0xFF4AD4FF)
    val GoldStar = Color(0xFFFFD166)

    // Ultra-transparent Liquid Crystal fills
    val GlassFillTop = Color(0x28FFFFFF)
    val GlassFillMid = Color(0x160E2433)
    val GlassFillBot = Color(0x228DEFE0)

    val GlassStrongTop = Color(0x36FFFFFF)
    val GlassStrongMid = Color(0x260A1B28)
    val GlassStrongBot = Color(0x2E8DEFE0)

    val SpecularRimTop = Color(0xCCFFFFFF)
    val SpecularRimMid = Color(0x559AF6FF)
    val SpecularRimBot = Color(0x998DEFE0)
}

@Composable
fun LiquidCrystalCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    strong: Boolean = false,
    accentBorder: Color? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val fillBrush = remember(strong) {
        Brush.linearGradient(
            colors = if (strong) {
                listOf(
                    LiquidCrystalPalette.GlassStrongTop,
                    LiquidCrystalPalette.GlassStrongMid,
                    LiquidCrystalPalette.GlassStrongBot
                )
            } else {
                listOf(
                    LiquidCrystalPalette.GlassFillTop,
                    LiquidCrystalPalette.GlassFillMid,
                    LiquidCrystalPalette.GlassFillBot
                )
            }
        )
    }
    val rimBrush = remember(accentBorder) {
        if (accentBorder != null) {
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.90f), accentBorder, accentBorder.copy(alpha = 0.55f))
            )
        } else {
            Brush.linearGradient(
                listOf(
                    LiquidCrystalPalette.SpecularRimTop,
                    LiquidCrystalPalette.SpecularRimMid,
                    LiquidCrystalPalette.SpecularRimBot
                )
            )
        }
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(fillBrush, shape)
            .border(1.dp, rimBrush, shape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun LiquidCrystalPill(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color = LiquidCrystalPalette.PrismCyan,
    badge: String? = null,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(50)
    val bgBrush = Brush.linearGradient(
        if (selected) {
            listOf(Color.White.copy(alpha = 0.34f), accent.copy(alpha = 0.28f))
        } else {
            listOf(Color.White.copy(alpha = 0.13f), Color(0x1A0B1D2B))
        }
    )
    val rimBrush = Brush.linearGradient(
        if (selected) {
            listOf(Color.White, accent)
        } else {
            listOf(Color.White.copy(alpha = 0.55f), accent.copy(alpha = 0.28f))
        }
    )

    Row(
        modifier = modifier
            .clip(shape)
            .background(bgBrush, shape)
            .border(1.dp, rimBrush, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) LiquidCrystalPalette.IceWhite else LiquidCrystalPalette.IceSub,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!badge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.26f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badge,
                    color = LiquidCrystalPalette.IceWhite,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun LiquidCrystalPrimaryButton(
    text: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    accent: Color = LiquidCrystalPalette.PrismCyan,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val fill = Brush.linearGradient(
        if (enabled) {
            listOf(
                Color.White.copy(alpha = 0.38f),
                accent.copy(alpha = 0.36f),
                LiquidCrystalPalette.PrismMint.copy(alpha = 0.32f)
            )
        } else {
            listOf(Color.White.copy(alpha = 0.10f), Color(0x180E1E2A))
        }
    )
    val border = Brush.linearGradient(
        if (enabled) listOf(Color.White, accent, LiquidCrystalPalette.PrismMint)
        else listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.1f))
    )

    Column(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .border(1.2.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = LiquidCrystalPalette.IceWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = LiquidCrystalPalette.IceSub,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun LiquidCrystalModuleToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: String? = null,
    expandedContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    LiquidCrystalCard(
        cornerRadius = 16.dp,
        strong = checked,
        accentBorder = if (checked) LiquidCrystalPalette.PrismCyan else null,
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        color = LiquidCrystalPalette.IceWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!badge.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(LiquidCrystalPalette.PrismCyan.copy(alpha = 0.22f))
                                .border(0.8.dp, LiquidCrystalPalette.PrismCyan.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(badge, color = LiquidCrystalPalette.IceWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = LiquidCrystalPalette.IceSub,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = LiquidCrystalPalette.PrismCyan.copy(alpha = 0.55f),
                    uncheckedThumbColor = LiquidCrystalPalette.IceSub,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.14f)
                )
            )
        }
        if (expandedContent != null) {
            AnimatedVisibility(visible = checked) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = expandedContent
                )
            }
        }
    }
}

/**
 * Interactive 360°-rotatable Minecraft Character + Flowing 3D Physics Cape / Spread Elytra + 3D Hat Preview!
 */
@Composable
fun Interactive3DCosmeticStage(
    capeSpec: ClientCapeSpec,
    hatSpec: ClientHatSpec,
    showElytraMode: Boolean,
    animatedShimmer: Boolean,
    playerName: String,
    modifier: Modifier = Modifier
) {
    var yawDeg by remember { mutableFloatStateOf(158f) } // Default angled back view showing Cape & Hat!
    val inf = rememberInfiniteTransition(label = "capeWave")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    yawDeg = (yawDeg + dragAmount * 0.65f) % 360f
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.52f
            val scale = (size.height / 240f).coerceIn(0.65f, 1.6f)

            // Floor Liquid Crystal Pedestal Ring
            drawOval(
                brush = Brush.radialGradient(
                    listOf(
                        Color(capeSpec.topHex).copy(alpha = 0.38f),
                        LiquidCrystalPalette.PrismCyan.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy + 78f * scale),
                    radius = 78f * scale
                ),
                topLeft = Offset(cx - 72f * scale, cy + 62f * scale),
                size = Size(144f * scale, 30f * scale)
            )
            drawOval(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset(cx - 56f * scale, cy + 66f * scale),
                size = Size(112f * scale, 22f * scale),
                style = Stroke(width = 1.4f)
            )

            val rad = yawDeg * (PI / 180.0)
            val facingBack = cos(rad) < 0.0 // yaw ~ 90..270 shows back (cape in front of torso)
            val perspectiveSkew = sin(rad).toFloat() * 10f * scale
            val limbSwing = sin(phase.toDouble()).toFloat() * 5f * scale

            fun drawCapeOrElytra() {
                val topCol = Color(capeSpec.topHex)
                val botCol = Color(capeSpec.bottomHex)
                val embCol = Color(capeSpec.emblemHex)
                val shimmerAlpha = if (animatedShimmer) (0.20f + 0.18f * abs(sin(phase))) else 0.10f

                if (showElytraMode) {
                    val spread = (40f + 5f * sin(phase)) * scale
                    val leftWing = Path().apply {
                        moveTo(cx - 4f * scale, cy - 26f * scale)
                        lineTo(cx - spread, cy - 10f * scale)
                        lineTo(cx - spread * 0.82f, cy + 44f * scale)
                        lineTo(cx - 8f * scale, cy + 28f * scale)
                        close()
                    }
                    val rightWing = Path().apply {
                        moveTo(cx + 4f * scale, cy - 26f * scale)
                        lineTo(cx + spread, cy - 10f * scale)
                        lineTo(cx + spread * 0.82f, cy + 44f * scale)
                        lineTo(cx + 8f * scale, cy + 28f * scale)
                        close()
                    }
                    val wingBrush = Brush.verticalGradient(
                        listOf(topCol, botCol),
                        startY = cy - 26f * scale,
                        endY = cy + 46f * scale
                    )
                    drawPath(leftWing, wingBrush)
                    drawPath(rightWing, wingBrush)
                    drawPath(leftWing, embCol.copy(alpha = 0.85f), style = Stroke(width = 2f))
                    drawPath(rightWing, embCol.copy(alpha = 0.85f), style = Stroke(width = 2f))
                } else {
                    // Flowing 8-segment cloth physics cape!
                    val segs = 8
                    val capeTopY = cy - 26f * scale
                    val capeH = 66f * scale
                    val capeW = 34f * scale
                    for (s in 0 until segs) {
                        val t0 = s / segs.toFloat()
                        val t1 = (s + 1) / segs.toFloat()
                        val wave0 = sin(phase - t0 * 2.6f) * (3.5f + t0 * 6f) * scale
                        val wave1 = sin(phase - t1 * 2.6f) * (3.5f + t1 * 6f) * scale
                        val xOff0 = perspectiveSkew * 0.35f + wave0 * 0.45f
                        val xOff1 = perspectiveSkew * 0.35f + wave1 * 0.45f
                        val y0 = capeTopY + capeH * t0
                        val y1 = capeTopY + capeH * t1
                        val segPath = Path().apply {
                            moveTo(cx - capeW * 0.5f + xOff0, y0)
                            lineTo(cx + capeW * 0.5f + xOff0, y0)
                            lineTo(cx + capeW * 0.54f + xOff1, y1)
                            lineTo(cx - capeW * 0.54f + xOff1, y1)
                            close()
                        }
                        val cMix = Color(
                            red = topCol.red + (botCol.red - topCol.red) * t0,
                            green = topCol.green + (botCol.green - topCol.green) * t0,
                            blue = topCol.blue + (botCol.blue - topCol.blue) * t0,
                            alpha = 0.96f
                        )
                        drawPath(segPath, cMix)
                        if (animatedShimmer && s == ((phase * 2f).toInt().mod(segs))) {
                            drawPath(segPath, Color.White.copy(alpha = shimmerAlpha))
                        }
                    }
                    // Crest Star on back of cape
                    val starCx = cx + perspectiveSkew * 0.35f + sin(phase - 1.1f) * 2.4f * scale
                    val starCy = capeTopY + capeH * 0.45f
                    drawCircle(embCol, radius = 5.5f * scale, center = Offset(starCx, starCy))
                    drawCircle(Color.White, radius = 2.6f * scale, center = Offset(starCx, starCy))
                }
            }

            fun drawPlayerBody() {
                // Legs
                drawRoundRect(
                    color = Color(0xFF142434),
                    topLeft = Offset(cx - 14f * scale, cy + 20f * scale + limbSwing * 0.4f),
                    size = Size(13f * scale, 42f * scale),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                drawRoundRect(
                    color = Color(0xFF101D2B),
                    topLeft = Offset(cx + 1f * scale, cy + 20f * scale - limbSwing * 0.4f),
                    size = Size(13f * scale, 42f * scale),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                // Arms
                drawRoundRect(
                    color = Color(0xFF1C3B50),
                    topLeft = Offset(cx - 27f * scale, cy - 25f * scale - limbSwing * 0.5f),
                    size = Size(12f * scale, 42f * scale),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = Color(0xFF1C3B50),
                    topLeft = Offset(cx + 15f * scale, cy - 25f * scale + limbSwing * 0.5f),
                    size = Size(12f * scale, 42f * scale),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // Torso
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF214A63), Color(0xFF12293A))),
                    topLeft = Offset(cx - 15f * scale, cy - 26f * scale),
                    size = Size(30f * scale, 46f * scale),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // Head
                val headLeft = cx - 16f * scale + perspectiveSkew * 0.2f
                val headTop = cy - 58f * scale
                drawRoundRect(
                    color = if (facingBack) Color(0xFF1C2B39) else Color(0xFFD9A886),
                    topLeft = Offset(headLeft, headTop),
                    size = Size(32f * scale, 32f * scale),
                    cornerRadius = CornerRadius(5f, 5f)
                )
                // Hair top
                drawRoundRect(
                    color = Color(0xFF1C2B39),
                    topLeft = Offset(headLeft, headTop),
                    size = Size(32f * scale, 10f * scale),
                    cornerRadius = CornerRadius(5f, 5f)
                )
                if (!facingBack) {
                    // Eyes
                    drawRect(Color.White, Offset(headLeft + 6f * scale, headTop + 16f * scale), Size(6f * scale, 4f * scale))
                    drawRect(LiquidCrystalPalette.PrismCyan, Offset(headLeft + 9f * scale, headTop + 16f * scale), Size(3f * scale, 4f * scale))
                    drawRect(Color.White, Offset(headLeft + 20f * scale, headTop + 16f * scale), Size(6f * scale, 4f * scale))
                    drawRect(LiquidCrystalPalette.PrismCyan, Offset(headLeft + 20f * scale, headTop + 16f * scale), Size(3f * scale, 4f * scale))
                }

                // 3D Hat-Layer Accessory Preview!
                val hatColor = Color(hatSpec.accentHex)
                when (hatSpec.id) {
                    "golden_crown" -> {
                        drawRect(hatColor, Offset(headLeft - 2f * scale, headTop - 5f * scale), Size(36f * scale, 8f * scale))
                        drawCircle(LiquidCrystalPalette.PrismCyan, 3f * scale, Offset(headLeft + 16f * scale, headTop - 1f * scale))
                    }
                    "crystal_halo" -> {
                        val floatY = headTop - 11f * scale + sin(phase) * 2.5f * scale
                        drawOval(
                            color = hatColor,
                            topLeft = Offset(headLeft + 2f * scale, floatY),
                            size = Size(28f * scale, 8f * scale),
                            style = Stroke(width = 3f * scale)
                        )
                    }
                    "cyber_visor", "pixel_shades" -> {
                        drawRoundRect(
                            color = hatColor,
                            topLeft = Offset(headLeft - 2f * scale, headTop + 13f * scale),
                            size = Size(36f * scale, 7f * scale),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    }
                    "pro_headset" -> {
                        drawRect(hatColor, Offset(headLeft - 4f * scale, headTop + 10f * scale), Size(5f * scale, 14f * scale))
                        drawRect(hatColor, Offset(headLeft + 31f * scale, headTop + 10f * scale), Size(5f * scale, 14f * scale))
                        drawRect(hatColor, Offset(headLeft - 2f * scale, headTop - 3f * scale), Size(36f * scale, 4f * scale))
                    }
                    "crimson_bandana", "sakura_crown" -> {
                        drawRect(hatColor, Offset(headLeft - 2f * scale, headTop + 5f * scale), Size(36f * scale, 6f * scale))
                    }
                }
            }

            if (facingBack) {
                drawPlayerBody()
                drawCapeOrElytra()
            } else {
                drawCapeOrElytra()
                drawPlayerBody()
            }
        }

        // Floating Nametag + Drag-to-Rotate Hint
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x55081722))
                    .border(0.8.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("✦", color = LiquidCrystalPalette.PrismCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(playerName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    capeSpec.badgeText?.let { b ->
                        val bCol = when (b) {
                            "(OWNER)" -> LiquidCrystalPalette.OwnerCrimson
                            "(CO-OWNER)" -> LiquidCrystalPalette.CoOwnerSky
                            else -> LiquidCrystalPalette.PrismMint
                        }
                        Text(b, color = bCol, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        Text(
            text = "↔ Drag horizontally to rotate 360°",
            color = LiquidCrystalPalette.IceSub.copy(alpha = 0.78f),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
    }
}

/**
 * Shared Procedural Crosshair + 1.16.5 Attack Cooldown Bar Canvas Renderer!
 */
fun DrawScope.drawAerixCrosshair(
    center: Offset,
    shape: CrosshairShape,
    armSizePx: Float,
    gapPx: Float,
    thickPx: Float,
    outlinePx: Float,
    mainColor: Color,
    cooldownProgress: Float? = null, // 0f..1f when < 1f shows 1.16.5 sword recharge bar
    hitmarkerAlpha: Float = 0f
) {
    val outlineCol = Color.Black.copy(alpha = (mainColor.alpha * 0.78f).coerceIn(0f, 1f))

    fun strokeLine(a: Offset, b: Offset) {
        if (outlinePx > 0.2f) {
            drawLine(outlineCol, a, b, strokeWidth = thickPx + outlinePx * 2f, cap = StrokeCap.Round)
        }
        drawLine(mainColor, a, b, strokeWidth = thickPx, cap = StrokeCap.Round)
    }

    when (shape) {
        CrosshairShape.TACTICAL_GAP -> {
            strokeLine(Offset(center.x - gapPx - armSizePx, center.y), Offset(center.x - gapPx, center.y))
            strokeLine(Offset(center.x + gapPx, center.y), Offset(center.x + gapPx + armSizePx, center.y))
            strokeLine(Offset(center.x, center.y - gapPx - armSizePx), Offset(center.x, center.y - gapPx))
            strokeLine(Offset(center.x, center.y + gapPx), Offset(center.x, center.y + gapPx + armSizePx))
        }
        CrosshairShape.CLASSIC_PLUS -> {
            strokeLine(Offset(center.x - armSizePx, center.y), Offset(center.x + armSizePx, center.y))
            strokeLine(Offset(center.x, center.y - armSizePx), Offset(center.x, center.y + armSizePx))
        }
        CrosshairShape.PRECISION_DOT -> {
            if (outlinePx > 0.2f) drawCircle(outlineCol, radius = thickPx * 1.4f + outlinePx, center = center)
            drawCircle(mainColor, radius = thickPx * 1.4f, center = center)
        }
        CrosshairShape.CIRCLE -> {
            val r = (armSizePx + gapPx).coerceAtLeast(4f)
            if (outlinePx > 0.2f) drawCircle(outlineCol, r, center, style = Stroke(thickPx + outlinePx * 2f))
            drawCircle(mainColor, r, center, style = Stroke(thickPx))
        }
        CrosshairShape.CIRCLE_DOT -> {
            val r = (armSizePx + gapPx).coerceAtLeast(5f)
            if (outlinePx > 0.2f) drawCircle(outlineCol, r, center, style = Stroke(thickPx + outlinePx * 2f))
            drawCircle(mainColor, r, center, style = Stroke(thickPx))
            drawCircle(mainColor, radius = thickPx * 1.1f, center = center)
        }
        CrosshairShape.X_HIT -> {
            val d0 = gapPx * 0.7f
            val d1 = (gapPx + armSizePx) * 0.75f
            strokeLine(Offset(center.x - d1, center.y - d1), Offset(center.x - d0, center.y - d0))
            strokeLine(Offset(center.x + d0, center.y - d0), Offset(center.x + d1, center.y - d1))
            strokeLine(Offset(center.x - d1, center.y + d1), Offset(center.x - d0, center.y + d0))
            strokeLine(Offset(center.x + d0, center.y + d0), Offset(center.x + d1, center.y + d1))
        }
        CrosshairShape.CHEVRON -> {
            strokeLine(Offset(center.x - armSizePx, center.y + armSizePx * 0.65f), center)
            strokeLine(center, Offset(center.x + armSizePx, center.y + armSizePx * 0.65f))
        }
        CrosshairShape.DIAMOND -> {
            val r = (armSizePx + gapPx * 0.5f).coerceAtLeast(5f)
            val p = Path().apply {
                moveTo(center.x, center.y - r)
                lineTo(center.x + r, center.y)
                lineTo(center.x, center.y + r)
                lineTo(center.x - r, center.y)
                close()
            }
            if (outlinePx > 0.2f) drawPath(p, outlineCol, style = Stroke(thickPx + outlinePx * 2f))
            drawPath(p, mainColor, style = Stroke(thickPx))
        }
    }

    // Hitmarker flash on melee strike
    if (hitmarkerAlpha > 0.02f) {
        val hmCol = LiquidCrystalPalette.PrismCyan.copy(alpha = hitmarkerAlpha.coerceIn(0f, 1f))
        val r0 = gapPx + armSizePx * 0.55f
        val r1 = r0 + 7f
        drawLine(hmCol, Offset(center.x - r1, center.y - r1), Offset(center.x - r0, center.y - r0), 2f)
        drawLine(hmCol, Offset(center.x + r0, center.y - r0), Offset(center.x + r1, center.y - r1), 2f)
        drawLine(hmCol, Offset(center.x - r1, center.y + r1), Offset(center.x - r0, center.y + r0), 2f)
        drawLine(hmCol, Offset(center.x + r0, center.y + r0), Offset(center.x + r1, center.y + r1), 2f)
    }

    // 1.16.5 Combat Sword Cooldown Recharge Bar (0.625s for Sword = 1.6 attack speed)
    if (cooldownProgress != null && cooldownProgress < 0.995f) {
        val barW = 34f
        val barH = 3.5f
        val topY = center.y + gapPx + armSizePx + 10f
        drawRoundRect(
            color = Color(0x8808141E),
            topLeft = Offset(center.x - barW * 0.5f, topY),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = if (cooldownProgress >= 0.92f) LiquidCrystalPalette.PrismMint else LiquidCrystalPalette.PrismCyan,
            topLeft = Offset(center.x - barW * 0.5f, topY),
            size = Size(barW * cooldownProgress.coerceIn(0f, 1f), barH),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }
}
