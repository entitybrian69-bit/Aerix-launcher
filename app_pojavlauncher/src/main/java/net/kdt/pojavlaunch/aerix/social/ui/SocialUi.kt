/*
 * Aerix Launcher Social layer.
 *
 * The Social palette is deliberately independent from AerixSurface: it never reads the
 * launcher accent, so the second launcher keeps its own identity whatever theme the
 * user picked for the Play dashboard.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R

/** Fixed Social palette from the reference screens. */
object SocialColors {
    val accent = Color(0xFF8B5CF6)
    val accentBright = Color(0xFFA78BFA)
    val backgroundTop = Color(0xFF140F26)
    val backgroundBottom = Color(0xFF1B1436)
    val panel = Color(0xFF241C42)
    val panelRaised = Color(0xFF2E2452)
    val panelSoft = Color(0xFF1E1738)
    val textPrimary = Color(0xFFF3F0FF)
    val textSecondary = Color(0xFFB4A9D6)
    val textMuted = Color(0xFF8A7FB0)
    val border = Color(0x33A78BFA)
    val borderStrong = Color(0x66A78BFA)
    val danger = Color(0xFFE4576C)
    val success = Color(0xFF4ADE80)
    val online = Color(0xFF4ADE80)
    val scrim = Color(0xCC0B0718)
}

object SocialMotion {
    const val SHELL_MS = 240
    const val SCREEN_MS = 200
    const val TAB_MS = 120
    const val CHAT_MS = 180
    const val DIALOG_MS = 160
}

object SocialDimens {
    val pagePaddingH = 20.dp
    val pagePaddingV = 16.dp
    val railWidth = 84.dp
    val touch = 48.dp
    val row = 60.dp
    val cardRadius = 20.dp
    val controlRadius = 14.dp
}

@Composable
fun SocialBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SocialColors.backgroundTop, SocialColors.backgroundBottom, Color(0xFF221A3F))
                )
            )
    ) {
        content()
    }
}

@Composable
fun SocialPanel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(SocialDimens.pagePaddingV),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(SocialDimens.cardRadius))
            .background(SocialColors.panel)
            .border(BorderStroke(1.dp, SocialColors.border), RoundedCornerShape(SocialDimens.cardRadius))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun SocialSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = SocialColors.textPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

@Composable
fun SocialTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, color = SocialColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
fun SocialBody(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SocialColors.textSecondary,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text,
        color = color,
        fontSize = size,
        fontWeight = weight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        lineHeight = (size.value + 5).sp,
        modifier = modifier
    )
}

@DrawableRes
fun socialAvatarRes(index: Int): Int = when (index) {
    2 -> R.drawable.aerix_social_avatar_2
    3 -> R.drawable.aerix_social_avatar_3
    4 -> R.drawable.aerix_social_avatar_4
    5 -> R.drawable.aerix_social_avatar_5
    6 -> R.drawable.aerix_social_avatar_6
    else -> R.drawable.aerix_social_avatar_1
}

@Composable
fun SocialAvatar(avatar: Int, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(socialAvatarRes(avatar)),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SocialColors.panelRaised)
    )
}

@Composable
fun SocialPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
    danger: Boolean = false
) {
    val fill = when {
        !enabled -> SocialColors.accent.copy(alpha = 0.35f)
        danger -> SocialColors.danger
        else -> SocialColors.accent
    }
    Surface(
        modifier = modifier.heightIn(min = SocialDimens.touch),
        shape = RoundedCornerShape(SocialDimens.controlRadius),
        color = fill,
        contentColor = Color.White,
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconRes != null) {
                Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SocialGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null
) {
    Surface(
        modifier = modifier.heightIn(min = SocialDimens.touch),
        shape = RoundedCornerShape(SocialDimens.controlRadius),
        color = SocialColors.panelRaised,
        contentColor = SocialColors.textPrimary,
        border = BorderStroke(1.dp, SocialColors.border),
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconRes != null) {
                Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SocialToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = SocialColors.accent,
            checkedBorderColor = SocialColors.accent,
            uncheckedThumbColor = SocialColors.textMuted,
            uncheckedTrackColor = SocialColors.panelSoft,
            uncheckedBorderColor = SocialColors.border
        )
    )
}

@Composable
fun SocialToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes iconRes: Int? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SocialDimens.row)
            .clip(RoundedCornerShape(SocialDimens.controlRadius))
            .background(SocialColors.panelSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (iconRes != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SocialColors.accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(iconRes), contentDescription = null, tint = SocialColors.accentBright, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            SocialBody(title, color = SocialColors.textPrimary, size = 14.sp, weight = FontWeight.Medium)
            if (subtitle != null) SocialBody(subtitle, size = 12.sp, color = SocialColors.textMuted)
        }
        SocialToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SocialDropdownRow(
    title: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes iconRes: Int? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SocialDimens.row)
                .clip(RoundedCornerShape(SocialDimens.controlRadius))
                .background(SocialColors.panelSoft)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconRes != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SocialColors.accent.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(iconRes), contentDescription = null, tint = SocialColors.accentBright, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                SocialBody(title, color = SocialColors.textPrimary, size = 14.sp, weight = FontWeight.Medium)
                if (subtitle != null) SocialBody(subtitle, size = 12.sp, color = SocialColors.textMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SocialBody(value, size = 12.sp, color = SocialColors.textSecondary, maxLines = 1)
                Icon(
                    painter = painterResource(R.drawable.aerix_social_ic_chevron),
                    contentDescription = null,
                    tint = SocialColors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SocialColors.panelRaised
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { SocialBody(option, color = SocialColors.textPrimary, size = 14.sp) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
fun SocialTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SocialDimens.pagePaddingH, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(SocialDimens.touch),
            shape = CircleShape,
            color = SocialColors.panelRaised,
            border = BorderStroke(1.dp, SocialColors.border),
            onClick = onBack
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_arrow_back),
                    contentDescription = "Back",
                    tint = SocialColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            SocialTitle(title)
            if (subtitle != null) SocialBody(subtitle, size = 13.sp, color = SocialColors.textSecondary)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun SocialDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // 160ms fade with a 0.96 -> 1.0 scale, as specified for every dialog in Social.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(SocialMotion.DIALOG_MS),
        label = "socialDialog"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SocialColors.scrim.copy(alpha = SocialColors.scrim.alpha * progress))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    alpha = progress
                    val scale = 0.96f + 0.04f * progress
                    scaleX = scale
                    scaleY = scale
                }
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(SocialColors.panelRaised)
                .border(BorderStroke(1.dp, SocialColors.borderStrong), RoundedCornerShape(22.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** Wrapper that animates the entrance of a page the way the reference flow does. */
@Composable
fun SocialPage(padding: PaddingValues = PaddingValues(SocialDimens.pagePaddingH, SocialDimens.pagePaddingV), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

internal val socialFadeSpec = tween<Float>(SocialMotion.SCREEN_MS)
