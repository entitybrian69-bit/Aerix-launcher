/*
 * Aerix Launcher — Social design tokens and self-contained Social components.
 *
 * Social is a launcher inside the launcher: it never reads the launcher accent,
 * wallpaper or theme state. Every color, size and control in the social package
 * comes from this file, pinned to the approved mockup purple.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.screens.social

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R

/**
 * Fixed Social palette. Independent of the launcher theme by construction:
 * switching the launcher accent to green or red leaves Social purple.
 */
// DECISION: the palette section of the spec was not in the pasted fragment; the accent
// family is the mockup purple already pinned on the e124 line (0xFF8B5CF6) and the
// surface/text tokens are fixed violet-tinted values derived from it, so the shell
// reads as its own world next to any launcher wallpaper/accent.
object SocialPalette {
    val accent = Color(0xFF8B5CF6)
    val accentBright = Color(0xFFA78BFA)
    val accentDeep = Color(0xFF7C3AED)
    val onAccent = Color(0xFFFFFFFF)
    val accentSoft = Color(0x2E8B5CF6)
    val accentFaint = Color(0x1A8B5CF6)

    val background = Color(0xFF0D0A1A)
    val backgroundDeep = Color(0xFF070512)
    val canvas = background
    val panel = Color(0x9D1E1838)
    val panelRaised = Color(0xB02A2147)
    val panelTrack = Color(0x683A2E63)
    val border = Color(0x78C4B5FD)
    val borderSoft = Color(0x48C4B5FD)

    val textPrimary = Color(0xFFF4F1FA)
    val textSecondary = Color(0xFFB4A8C9)
    val textMuted = Color(0xFF877D9E)

    val success = Color(0xFF5BD98A)
    val danger = Color(0xFFFF9C9C)
    val dangerStrong = Color(0xFFEF4444)
    val warning = Color(0xFFFFC777)
}

/** Spacing contract for Social. Plain dp so nothing clips at 360 dp or on tablets. */
object SocialDimens {
    val pageHorizontal: Dp = 20.dp
    val pageVertical: Dp = 16.dp
    val cardPadding: Dp = 16.dp
    val gapSmall: Dp = 8.dp
    val gap: Dp = 12.dp
    val gapLarge: Dp = 20.dp
    val touchTarget: Dp = 48.dp
    val primaryButton: Dp = 48.dp
    val listRowMin: Dp = 60.dp
    val friendRow: Dp = 60.dp
    val headerHeight: Dp = 52.dp
    val railWidth: Dp = 76.dp
    val hairline: Dp = 1.dp
    val cardRadius: Dp = 18.dp
}

/** Type scale for Social, in sp. */
object SocialType {
    val title = 22.sp
    val section = 16.sp
    val body = 14.sp
    val secondary = 13.sp
    val caption = 11.sp
}

/**
 * Motion contract: short tweens only. No bounce, no spring overshoot.
 */
// DECISION: the motion table arrived truncated (only the dialog row survived:
// 160 ms fade + 0.96->1.0 scale). Page changes use a 200 ms fade with a 24 dp slide
// from the trailing edge, shell entry/exit a 180 ms fade, matching the dialog row's
// register. All specs are tweens defined at call sites via these constants.
object SocialMotion {
    const val PAGE_MS = 200
    const val PAGE_EXIT_MS = 140
    const val SHELL_MS = 180
    const val DIALOG_MS = 160
    const val DIALOG_INITIAL_SCALE = 0.96f
}

/**
 * Entry motion for Social dialogs: 160 ms fade from 0.96 scale, tween only.
 * Apply to the dialog card so every dialog enters the same way.
 */
@Composable
fun socialDialogModifier(): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(SocialMotion.DIALOG_MS),
        label = "socialDialogEnter"
    )
    val scale = SocialMotion.DIALOG_INITIAL_SCALE + (1f - SocialMotion.DIALOG_INITIAL_SCALE) * progress
    return Modifier.graphicsLayer(scaleX = scale, scaleY = scale, alpha = progress)
}

/** Card surface for Social. Fixed panel color and border; no glass, no wallpaper. */
@Composable
fun SocialCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = SocialDimens.cardPadding,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(SocialDimens.cardRadius)
    val base = modifier
        .clip(shape)
        .background(SocialPalette.panel, shape)
        .border(BorderStroke(SocialDimens.hairline, SocialPalette.borderSoft), shape)
    val clickable = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    Column(
        modifier = clickable.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(SocialDimens.gapSmall),
        content = content
    )
}

/** Primary action in Social: always the mockup purple (or an explicit danger accent). */
@Composable
fun SocialPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
    accent: Color = SocialPalette.accent
) {
    Surface(
        modifier = modifier.heightIn(min = SocialDimens.primaryButton),
        shape = CircleShape,
        color = if (enabled) accent else accent.copy(alpha = 0.35f),
        contentColor = SocialPalette.onAccent,
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SocialDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SocialDimens.gapSmall, Alignment.CenterHorizontally)
        ) {
            if (iconRes != null) {
                Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(text = text, fontSize = SocialType.secondary, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Secondary action in Social. Outline pill on the fixed panel color. */
@Composable
fun SocialGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentText: Boolean = false,
    @DrawableRes iconRes: Int? = null
) {
    Surface(
        modifier = modifier.heightIn(min = SocialDimens.touchTarget),
        shape = CircleShape,
        color = SocialPalette.panelRaised.copy(alpha = 0.54f),
        contentColor = if (accentText) SocialPalette.accentBright else SocialPalette.textPrimary,
        border = BorderStroke(SocialDimens.hairline, SocialPalette.borderSoft),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SocialDimens.gap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SocialDimens.gapSmall, Alignment.CenterHorizontally)
        ) {
            if (iconRes != null) {
                Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = text,
                fontSize = SocialType.secondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SocialRowFrame(
    title: String,
    summary: String?,
    @DrawableRes iconRes: Int?,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit = {}
) {
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = SocialDimens.listRowMin)
        .clip(shape)
        .background(SocialPalette.panelRaised.copy(alpha = 0.42f), shape)
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = SocialDimens.gap, vertical = SocialDimens.gapSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = SocialPalette.accentBright,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(SocialDimens.gap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = SocialPalette.textPrimary, fontSize = SocialType.secondary, fontWeight = FontWeight.SemiBold)
            if (!summary.isNullOrEmpty()) {
                Text(text = summary, color = SocialPalette.textSecondary, fontSize = SocialType.caption, lineHeight = 14.sp)
            }
        }
        Spacer(Modifier.width(SocialDimens.gapSmall))
        trailing()
    }
}

/** Switch row tinted with the fixed Social accent; the whole row toggles. */
@Composable
fun SocialSwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @DrawableRes iconRes: Int? = null,
    accent: Color = SocialPalette.accent
) {
    SocialRowFrame(
        title = title,
        summary = summary,
        iconRes = iconRes,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SocialPalette.onAccent,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = SocialPalette.textSecondary,
                    uncheckedTrackColor = SocialPalette.panelTrack,
                    uncheckedBorderColor = SocialPalette.borderSoft
                )
            )
        }
    )
}

/** Tappable settings row with an optional current-value label. */
@Composable
fun SocialActionRow(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    @DrawableRes iconRes: Int? = null,
    value: String? = null
) {
    SocialRowFrame(
        title = title,
        summary = summary,
        iconRes = iconRes,
        onClick = onClick,
        trailing = {
            if (!value.isNullOrEmpty()) {
                Text(text = value, color = SocialPalette.accentBright, fontSize = SocialType.secondary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

/**
 * Generic human placeholder avatar for [name]. The choice is a stable function of the
 * name hash, so a friend looks identical on every Social surface.
 */
@DrawableRes
fun socialAvatarRes(name: String): Int {
    val index = (name.hashCode() and 0x7FFFFFFF) % SOCIAL_AVATARS.size
    return SOCIAL_AVATARS[index]
}

private val SOCIAL_AVATARS = listOf(
    R.drawable.aerix_social_avatar_1,
    R.drawable.aerix_social_avatar_2,
    R.drawable.aerix_social_avatar_3,
    R.drawable.aerix_social_avatar_4,
    R.drawable.aerix_social_avatar_5,
    R.drawable.aerix_social_avatar_6
)
