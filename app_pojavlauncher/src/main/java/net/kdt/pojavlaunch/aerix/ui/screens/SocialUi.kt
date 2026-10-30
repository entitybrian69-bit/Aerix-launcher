/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Shared pieces of the social pages added with profiles: remote images, glass cards, the form
 * fields, and the image picker that uploads to the project's storage bucket.
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

package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.social.SocialPresenceCodec
import net.kdt.pojavlaunch.aerix.social.SocialPresenceMode
import net.kdt.pojavlaunch.aerix.social.SocialRealtime
import net.kdt.pojavlaunch.aerix.social.SocialRole
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.theme.ThemeMotion
import okhttp3.Request
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.sin

/** Pictures downloaded once per session: avatars and banners are asked for on every screen. */
private val remoteImageCache = HashMap<String, ImageBitmap?>()

/** Stores a freshly uploaded picture in the session cache so it renders immediately without re-downloading. */
fun cacheSocialImage(url: String, bytes: ByteArray) {
    val decoded = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
    if (decoded != null) {
        remoteImageCache[url] = decoded
    }
}

@Composable
fun SocialRemoteImage(
    url: String?,
    fallbackIcon: Int,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentScale: ContentScale = ContentScale.Crop
) {
    val ctx = LocalContext.current
    var bitmap by remember(url) { mutableStateOf(remoteImageCache[url]) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        val cached = remoteImageCache[url]
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val bytes: ByteArray? = when {
                    url.startsWith("content://") -> {
                        ctx.contentResolver.openInputStream(Uri.parse(url))?.use { it.readBytes() }
                    }
                    url.startsWith("/") -> {
                        java.io.File(url).takeIf { it.isFile }?.readBytes()
                    }
                    else -> {
                        val req = Request.Builder().url(url).get().build()
                        SocialRealtime.restClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) null else resp.body?.bytes()
                        }
                    }
                }
                if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                else null
            }.getOrNull()
        }
        if (loaded != null) {
            remoteImageCache[url] = loaded
        }
        bitmap = loaded
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(AerixSurface.panelTrack)
            .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft), shape),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
        } else {
            Icon(
                painter = painterResource(fallbackIcon),
                contentDescription = null,
                tint = AerixSurface.textMuted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** One glass card: the surface every section of the profile pages sits on. */
@Composable
fun SocialPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AerixRadii.cardLarge),
    content: @Composable () -> Unit
) {
    Surface(
        shape = shape,
        color = AerixSurface.panelRaised,
        contentColor = AerixSurface.textPrimary,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
        modifier = modifier.liquidGlass(
            shape = shape,
            tint = AerixSurface.panelGlassTint,
            strength = 0.9f,
            elevation = AerixMetrics.glassSubtleElevation
        )
    ) {
        content()
    }
}

@Composable
fun SocialGlassInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    supporting: String? = null
) {
    val accent = AerixThemeManager.currentAccent()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        enabled = enabled,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        shape = RoundedCornerShape(AerixRadii.control),
        supportingText = if (supporting == null) null else {
            { Text(supporting, fontSize = 10.sp, color = AerixSurface.textMuted) }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AerixSurface.textPrimary,
            unfocusedTextColor = AerixSurface.textPrimary,
            disabledTextColor = AerixSurface.textMuted,
            focusedLabelColor = accent,
            unfocusedLabelColor = AerixSurface.textMuted,
            disabledLabelColor = AerixSurface.textMuted,
            focusedBorderColor = accent,
            unfocusedBorderColor = AerixSurface.borderSoft,
            disabledBorderColor = AerixSurface.borderSoft,
            cursorColor = accent
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Solid accent button, or glass button when [filled] is false. Compresses on press. */
@Composable
fun SocialButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "socialPress"
    )
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(RoundedCornerShape(AerixRadii.control))
            .background(if (filled) accent else AerixSurface.panelTrack)
            .border(
                BorderStroke(
                    AerixSpacing.hairline,
                    if (filled) accent.copy(alpha = 0.85f) else AerixSurface.border
                ),
                RoundedCornerShape(AerixRadii.control)
            )
            .clickable(interactionSource = interaction, indication = null) {
                if (enabled) onClick()
            }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (filled) AerixSurface.canvas else AerixSurface.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A small tinted pill: ranks, countries, counts. */
@Composable
fun SocialTag(text: String, tint: Color = AerixThemeManager.currentAccent(), modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .border(BorderStroke(AerixSpacing.hairline, tint.copy(alpha = 0.5f)), CircleShape)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1)
    }
}

/**
 * Verified account badge:
 * - `(OWNER)` in crimson red (`entitybrian69@gmail.com`)
 * - `(CO-OWNER)` in sky blue (`kushaladhikari982648@gmail.com`)
 */
@Composable
fun SocialRoleBadge(
    role: SocialRole,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val baseColor = Color(role.colorArgb)
    val motion = ThemeMotion.rememberEnabled()
    val liveColor = ThemeMotion.rememberPulse(baseColor, active = true, enabled = motion)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(baseColor.copy(alpha = 0.18f))
            .border(BorderStroke(1.dp, liveColor.copy(alpha = 0.72f)), CircleShape)
            .padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 1.5.dp else 2.5.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = role.tag,
            fontSize = if (compact) 9.5.sp else 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = baseColor,
            maxLines = 1
        )
    }
}

/**
 * Renders any player's chosen name with `(OWNER)` (crimson red) or `(CO-OWNER)` (sky blue)
 * automatically placed right beside it. Also auto-extracts wire role suffixes as a fallback.
 */
@Composable
fun SocialNameWithRole(
    name: String,
    role: SocialRole? = null,
    fontSize: TextUnit = 13.5.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = AerixSurface.textPrimary,
    compactBadge: Boolean = false,
    prefixText: String? = null,
    prefixColor: Color = AerixSurface.textMuted,
    suffixText: String? = null,
    suffixColor: Color = AerixSurface.textMuted,
    modifier: Modifier = Modifier
) {
    val (extractedClean, extractedRole) = remember(name, role) {
        val pair = SocialRole.extract(name)
        (pair.first ?: name) to (role ?: pair.second)
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (!prefixText.isNullOrEmpty()) {
            Text(
                text = prefixText,
                fontSize = fontSize,
                fontWeight = FontWeight.Normal,
                color = prefixColor,
                maxLines = 1
            )
        }
        Text(
            text = extractedClean,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (extractedRole != null) {
            SocialRoleBadge(role = extractedRole, compact = compactBadge)
        }
        if (!suffixText.isNullOrBlank()) {
            Text(
                text = suffixText,
                fontSize = (fontSize.value - 2f).coerceAtLeast(9.5f).sp,
                color = suffixColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun presenceModeColor(mode: SocialPresenceMode?): Color = when (mode) {
    SocialPresenceMode.ONLINE -> AerixSurface.success
    SocialPresenceMode.IDLE -> AerixSurface.warning
    SocialPresenceMode.DND -> AerixSurface.danger
    SocialPresenceMode.INVISIBLE, null -> AerixSurface.textMuted
}

@Composable
fun SocialStatusDot(
    mode: SocialPresenceMode?,
    size: Dp = 10.dp,
    borderWidth: Dp = 1.5.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(presenceModeColor(mode))
            .border(borderWidth, AerixSurface.canvas, CircleShape)
    )
}

/**
 * Messenger-style 3-dot staggered bouncing wave animation.
 */
@Composable
fun SocialTypingDots(
    color: Color = AerixThemeManager.currentAccent(),
    dotSize: Dp = 5.5.dp,
    bounceDp: Dp = 4.dp,
    spacing: Dp = 3.5.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "messengerTypingWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "typingPhase"
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        for (idx in 0..2) {
            val shifted = ((phase - idx * 0.17f) % 1f + 1f) % 1f
            val wave = if (shifted < 0.55f) {
                sin((shifted / 0.55f) * PI).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            val scale = 0.80f + wave * 0.34f
            val alpha = 0.42f + wave * 0.58f
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        translationY = -wave * bounceDp.toPx()
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * Compact rich-presence typing indicator row (`[• • •] sahilll is typing`) for headers and friend rows.
 */
@Composable
fun SocialTypingIndicatorRow(
    text: String,
    tint: Color = AerixThemeManager.currentAccent(),
    fontSize: TextUnit = 10.5.sp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f))
                .border(BorderStroke(AerixSpacing.hairline, tint.copy(alpha = 0.45f)), CircleShape)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            SocialTypingDots(
                color = tint,
                dotSize = 4.5.dp,
                bounceDp = 3.dp,
                spacing = 2.8.dp
            )
        }
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Full Messenger-style animated chat bubble shown at the bottom of the open conversation stream
 * whenever the other player (or party member) is actively typing.
 */
@Composable
fun SocialTypingChatBubble(
    typerName: String,
    avatarUrl: String? = null,
    showNameCaption: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accent = AerixThemeManager.currentAccent()
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomEnd = 16.dp,
        bottomStart = 4.dp
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            SocialRemoteImage(
                url = avatarUrl,
                fallbackIcon = R.drawable.aerix_ic_person_outlined,
                modifier = Modifier.size(28.dp),
                shape = CircleShape
            )
            SocialStatusDot(
                mode = SocialPresenceMode.ONLINE,
                size = 8.5.dp,
                borderWidth = 1.dp
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showNameCaption) {
                Text(
                    text = "$typerName is typing…",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Surface(
                shape = bubbleShape,
                color = AerixSurface.panelRaised,
                border = BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.45f)),
                modifier = Modifier.liquidGlass(
                    shape = bubbleShape,
                    tint = AerixSurface.panelGlassTint,
                    strength = 0.85f,
                    elevation = AerixMetrics.glassSubtleElevation
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SocialTypingDots(
                        color = accent,
                        dotSize = 6.5.dp,
                        bounceDp = 4.8.dp,
                        spacing = 4.5.dp
                    )
                }
            }
        }
    }
}

/**
 * Full AerixSurface liquid-glass Change Status dialog:
 * supports BOTH **Auto (Smart)** status (Green in Launcher, Yellow in background, Offline without Wi-Fi)
 * AND Manual availability override (`Online` / `Idle` / `Do Not Disturb` / `Appear Offline`),
 * custom status message, quick gaming presets, Looking to Play, and Messenger Chat Sounds.
 */
@Composable
fun SocialStatusDialog(
    onDismiss: () -> Unit,
    onSaved: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()
    var autoSelected by remember { mutableStateOf(SocialBackend.isAutoPresence) }
    var mode by remember { mutableStateOf(SocialBackend.myPresenceMode) }
    var customText by remember { mutableStateOf(SocialBackend.myCustomStatus.orEmpty()) }
    var lookingToPlay by remember { mutableStateOf(SocialBackend.myLookingToPlay) }
    var soundsEnabled by remember { mutableStateOf(SocialBackend.chatSoundsEnabled) }
    var saving by remember { mutableStateOf(false) }

    val networkOnline = SocialBackend.isNetworkAvailable
    val inForeground = SocialBackend.isLauncherInForeground
    val previewEffectiveMode: SocialPresenceMode? = when {
        !networkOnline -> null
        autoSelected -> if (inForeground) SocialPresenceMode.ONLINE else SocialPresenceMode.IDLE
        else -> mode
    }

    val myName = SocialBackend.myEffectiveDisplayName()
    val myRole = SocialBackend.myRole
    val previewSummary = remember(autoSelected, mode, customText, lookingToPlay, networkOnline, inForeground) {
        if (!networkOnline) {
            "Offline · Wi-Fi disconnected"
        } else {
            val clean = customText.trim().takeIf { it.isNotEmpty() }
            val activeMode = previewEffectiveMode ?: SocialPresenceMode.ONLINE
            val suffix = if (autoSelected) " (Auto)" else ""
            when (activeMode) {
                SocialPresenceMode.INVISIBLE -> if (clean != null) "Appear Offline · $clean (hidden)" else "Appear Offline (others see Offline)"
                SocialPresenceMode.DND -> if (clean != null) "Do Not Disturb · $clean" else "Do Not Disturb"
                SocialPresenceMode.IDLE -> if (clean != null) "Idle$suffix · $clean" else "Idle$suffix"
                SocialPresenceMode.ONLINE -> clean ?: if (lookingToPlay) "Online$suffix · Looking to play" else "Online$suffix"
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        SocialPanel(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Change Status",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AerixSurface.textPrimary
                        )
                        Text(
                            "Smart Auto presence or manual override — synced live",
                            fontSize = 11.sp,
                            color = AerixSurface.textMuted
                        )
                    }
                    SocialIconBubble(R.drawable.aerix_ic_close, "Close", onDismiss)
                }

                // Live self preview card
                Surface(
                    shape = RoundedCornerShape(AerixRadii.control),
                    color = AerixSurface.panelTrack,
                    border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            SocialRemoteImage(
                                url = SocialBackend.myProfile?.avatarUrl,
                                fallbackIcon = R.drawable.aerix_ic_person_outlined,
                                modifier = Modifier.size(38.dp),
                                shape = CircleShape
                            )
                            SocialStatusDot(mode = previewEffectiveMode, size = 11.dp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            SocialNameWithRole(
                                name = myName,
                                role = myRole,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = previewSummary,
                                fontSize = 11.sp,
                                color = presenceModeColor(previewEffectiveMode),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Availability mode: Auto (Smart) + Manual modes
                Text(
                    "Availability Mode",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AerixSurface.textSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 1. Smart Automatic mode
                    val autoRowShape = RoundedCornerShape(AerixRadii.controlSmall)
                    val autoTint = AerixSurface.success
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(autoRowShape)
                            .background(
                                if (autoSelected) autoTint.copy(alpha = 0.16f)
                                else AerixSurface.panelTrack.copy(alpha = 0.65f)
                            )
                            .border(
                                BorderStroke(
                                    if (autoSelected) 1.2.dp else AerixSpacing.hairline,
                                    if (autoSelected) autoTint.copy(alpha = 0.85f) else AerixSurface.borderSoft
                                ),
                                autoRowShape
                            )
                            .clickable { autoSelected = true }
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SocialStatusDot(
                            mode = if (!networkOnline) null else if (inForeground) SocialPresenceMode.ONLINE else SocialPresenceMode.IDLE,
                            size = 11.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Auto (Smart)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (autoSelected) autoTint else AerixSurface.textPrimary
                            )
                            Text(
                                "Green in Launcher · Yellow in background · Offline when Wi-Fi is off",
                                fontSize = 10.5.sp,
                                color = AerixSurface.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (autoSelected) {
                            SocialTag("Auto", autoTint)
                        }
                    }

                    // 2. Manual override modes
                    SocialPresenceMode.values().forEach { candidate ->
                        val active = !autoSelected && mode == candidate
                        val tint = presenceModeColor(candidate)
                        val rowShape = RoundedCornerShape(AerixRadii.controlSmall)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .background(
                                    if (active) tint.copy(alpha = 0.16f)
                                    else AerixSurface.panelTrack.copy(alpha = 0.65f)
                                )
                                .border(
                                    BorderStroke(
                                        if (active) 1.2.dp else AerixSpacing.hairline,
                                        if (active) tint.copy(alpha = 0.8f) else AerixSurface.borderSoft
                                    ),
                                    rowShape
                                )
                                .clickable {
                                    autoSelected = false
                                    mode = candidate
                                }
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SocialStatusDot(mode = candidate, size = 11.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${candidate.label} (Manual)",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) tint else AerixSurface.textPrimary
                                )
                                Text(
                                    candidate.description,
                                    fontSize = 10.5.sp,
                                    color = AerixSurface.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (active) {
                                SocialTag("Manual", tint)
                            }
                        }
                    }
                }

                // Custom status text + Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SocialGlassInput(
                        value = customText,
                        onValueChange = { customText = it.take(52) },
                        label = "Custom status message (optional)",
                        modifier = Modifier.weight(1f)
                    )
                    if (customText.isNotEmpty()) {
                        SocialButton("Clear", filled = false) { customText = "" }
                    }
                }

                // Quick presets
                Text(
                    "Quick Presets",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AerixSurface.textMuted
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SocialPresenceCodec.presets.forEach { preset ->
                        val selected = !autoSelected &&
                            customText.equals(preset.statusText, ignoreCase = true) &&
                            mode == preset.mode
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) accent.copy(alpha = 0.22f) else AerixSurface.panelTrack)
                                .border(
                                    BorderStroke(AerixSpacing.hairline, if (selected) accent else AerixSurface.borderSoft),
                                    CircleShape
                                )
                                .clickable {
                                    customText = preset.statusText
                                    autoSelected = false
                                    mode = preset.mode
                                    preset.lookingToPlay?.let { lookingToPlay = it }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                preset.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) accent else AerixSurface.textSecondary
                            )
                        }
                    }
                }

                // Looking to play + Messenger sounds pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SocialButton(
                            label = if (lookingToPlay) "Looking to play ✓" else "Looking to play",
                            filled = lookingToPlay
                        ) {
                            lookingToPlay = !lookingToPlay
                        }
                        SocialButton(
                            label = if (soundsEnabled) "Chat Sounds ✓" else "Sounds Off",
                            filled = soundsEnabled
                        ) {
                            soundsEnabled = !soundsEnabled
                            SocialBackend.toggleChatSounds(soundsEnabled)
                        }
                    }
                    Text(
                        "${customText.length}/52",
                        fontSize = 10.5.sp,
                        color = AerixSurface.textMuted
                    )
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SocialButton(
                        label = "Reset Auto",
                        filled = false,
                        enabled = !saving && (!autoSelected || customText.isNotEmpty() || lookingToPlay)
                    ) {
                        saving = true
                        scope.launch {
                            SocialBackend.enableAutoStatus(null, false)
                            saving = false
                            onSaved?.invoke()
                            onDismiss()
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SocialButton("Cancel", filled = false, enabled = !saving, onClick = onDismiss)
                        SocialButton(
                            label = if (saving) "Saving…" else "Save Status",
                            filled = true,
                            enabled = !saving
                        ) {
                            saving = true
                            scope.launch {
                                val cleanMsg = customText.trim().ifEmpty { null }
                                if (autoSelected) {
                                    SocialBackend.enableAutoStatus(
                                        customStatus = cleanMsg,
                                        looking = lookingToPlay
                                    )
                                } else {
                                    SocialBackend.updateMyStatus(
                                        mode = mode,
                                        customStatus = cleanMsg,
                                        looking = lookingToPlay,
                                        manualOverride = true
                                    )
                                }
                                saving = false
                                onSaved?.invoke()
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The bar at the top of a profile page: back arrow, title, optional action on the right. */
@Composable
fun SocialPageBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    role: SocialRole? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        if (onBack != null) {
            SocialIconBubble(R.drawable.aerix_ic_arrow_back, "Back", onBack)
        }
        SocialNameWithRole(
            name = title,
            role = role,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AerixSurface.textPrimary,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
fun SocialIconBubble(iconRes: Int, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "iconBubble"
    )
    Box(
        modifier = Modifier
            .size(32.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(AerixSurface.panelTrack)
            .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft), CircleShape)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = AerixSurface.textPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** One row of the followers / following / friends lists. */
@Composable
fun SocialPersonRow(
    avatarUrl: String?,
    title: String,
    subtitle: String?,
    badge: String? = null,
    role: SocialRole? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "personPress"
    )
    Surface(
        shape = RoundedCornerShape(AerixRadii.card),
        color = AerixSurface.panelRaised,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SocialRemoteImage(
                url = avatarUrl,
                fallbackIcon = R.drawable.aerix_ic_person_outlined,
                modifier = Modifier.size(36.dp),
                shape = CircleShape
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                SocialNameWithRole(
                    name = title,
                    role = role,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AerixSurface.textPrimary,
                    compactBadge = true
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        color = AerixSurface.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (badge != null) SocialTag(badge)
        }
    }
}

/** A picture the player picked, already decoded and shown in a clickable box of a real fixed size. */
@Composable
fun SocialPickBox(
    label: String,
    url: String?,
    fallbackIcon: Int,
    shape: Shape,
    width: Dp? = null,
    height: Dp = 76.dp,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "pickBoxPress"
    )
    val boxSize = if (width != null) {
        Modifier.size(width = width, height = height)
    } else {
        Modifier.fillMaxWidth().height(height)
    }
    Box(
        modifier = modifier
            .then(boxSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        SocialRemoteImage(
            url = url,
            fallbackIcon = fallbackIcon,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            contentScale = contentScale
        )
        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .clip(CircleShape)
                .background(AerixSurface.canvas.copy(alpha = 0.72f))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(label, fontSize = 9.sp, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Opens the system photo picker, shrinks the chosen picture and hands the bytes back. Skins and
 * capes keep their pixels exactly (they are 64 px art and must not be resampled); photos are
 * capped so an upload from a 108 MP camera still fits in a second.
 */
@Composable
fun rememberPicturePicker(
    maxDimension: Int,
    keepPixels: Boolean,
    onError: (String) -> Unit = {},
    onPicked: (ByteArray, String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentOnError by rememberUpdatedState(onError)
    val mainHandler = remember(context) { android.os.Handler(context.mainLooper) }
    val handleUri: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            Thread {
                runCatching { encodePicture(context, uri, maxDimension, keepPixels) }
                    .onSuccess { result ->
                        mainHandler.post {
                            if (result != null) {
                                currentOnPicked(result.first, result.second)
                            } else {
                                currentOnError("Could not decode the selected image.")
                            }
                        }
                    }
                    .onFailure { err ->
                        mainHandler.post {
                            currentOnError(err.message ?: "Could not read the selected image.")
                        }
                    }
            }.start()
        }
    }
    val contentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), handleUri)
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), handleUri)
    return {
        try {
            contentLauncher.launch("image/*")
        } catch (_: Throwable) {
            try {
                documentLauncher.launch(arrayOf("image/*"))
            } catch (err: Throwable) {
                currentOnError(err.message ?: "No image picker app found on this device.")
            }
        }
    }
}

private fun encodePicture(
    context: Context,
    uri: Uri,
    maxDimension: Int,
    keepPixels: Boolean
): Pair<ByteArray, String>? {
    val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?.takeIf { it.isNotEmpty() } ?: return null

    if (keepPixels) {
        val check = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
        val isPng = rawBytes.size >= 8 &&
            rawBytes[0] == 0x89.toByte() &&
            rawBytes[1] == 0x50.toByte() &&
            rawBytes[2] == 0x4E.toByte() &&
            rawBytes[3] == 0x47.toByte()
        if (isPng) {
            check.recycle()
            return rawBytes to "png"
        }
        val stream = ByteArrayOutputStream()
        check.compress(Bitmap.CompressFormat.PNG, 100, stream)
        check.recycle()
        return stream.toByteArray() to "png"
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    if (maxDimension > 0) {
        while (maxOf(bounds.outWidth / (sample * 2), bounds.outHeight / (sample * 2)) >= maxDimension) {
            sample *= 2
        }
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    val original = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return null
    val longest = maxOf(original.width, original.height)
    val scaled = if (maxDimension <= 0 || longest <= maxDimension) {
        original
    } else {
        val ratio = maxDimension.toFloat() / longest
        Bitmap.createScaledBitmap(
            original,
            (original.width * ratio).toInt().coerceAtLeast(1),
            (original.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 88, stream)
    if (scaled !== original) scaled.recycle()
    original.recycle()
    return stream.toByteArray() to "jpg"
}
