/*
 * Aerix Launcher Social layer.
 *
 * The Social surface now uses the launcher's own glass theme: the same wallpaper, the same
 * AerixSurface tokens and the same accent the Play dashboard uses, so Social looks like a
 * second screen of Aerix instead of a different app.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.components.AerixBackground
import net.kdt.pojavlaunch.aerix.social.data.SocialLink
import net.kdt.pojavlaunch.aerix.theme.AerixSurface

/**
 * Social palette. Everything is the launcher's own token; the accent follows the wallpaper
 * the player picked on the Play dashboard, so both launchers stay in sync.
 */
object SocialColors {
    val accent: Color @Composable get() = AerixSurface.accent
    val accentBright: Color @Composable get() = AerixSurface.accentSecondary
    val backgroundTop: Color get() = AerixSurface.canvas
    val backgroundBottom: Color get() = AerixSurface.canvas
    val panel: Color get() = AerixSurface.panel
    val panelRaised: Color get() = AerixSurface.panelRaised
    val panelSoft: Color get() = AerixSurface.panelTrack
    val textPrimary: Color get() = AerixSurface.textPrimary
    val textSecondary: Color get() = AerixSurface.textSecondary
    val textMuted: Color get() = AerixSurface.textMuted
    val border: Color get() = AerixSurface.borderSoft
    val borderStrong: Color get() = AerixSurface.border
    val danger: Color get() = AerixSurface.danger
    val success: Color get() = AerixSurface.success
    val warning: Color get() = AerixSurface.warning
    val online: Color get() = AerixSurface.success
    val scrim: Color get() = Color(0xD9060F16)
    val onAccent: Color @Composable get() = AerixSurface.onAccent
    val glassShine: Color get() = AerixSurface.glassShine
    val storyRing: Color get() = Color(0xFFE11D48)
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

/** The launcher wallpaper, with the Social glass panels drawn on top of it. */
@Composable
fun SocialBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AerixBackground(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    // Only enough tint to keep text readable; the wallpaper must stay visible.
                    Brush.verticalGradient(
                        listOf(Color(0x1404101A), Color(0x26060F16), Color(0x3304101A))
                    )
                )
        ) {
            content()
        }
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
            .background(
                Brush.verticalGradient(
                    listOf(SocialColors.panelRaised.copy(alpha = 0.82f), SocialColors.panel.copy(alpha = 0.72f))
                )
            )
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

/** Generic avatar. [story] adds the crimson 24h story ring. */
@Composable
fun SocialAvatar(
    avatar: Int,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    story: Boolean = false
) {
    // The story ring breathes slowly so a new story catches the eye without being noisy.
    val ringPulse by animateFloatAsState(
        targetValue = if (story) 1f else 0.85f,
        animationSpec = tween(900),
        label = "storyRing"
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { alpha = if (story) ringPulse else 1f }
            .clip(CircleShape)
            .background(if (story) SocialColors.storyRing else Color.Transparent)
            .padding(if (story) 2.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(socialAvatarRes(avatar)),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(SocialColors.panelRaised)
        )
    }
}

/** Shared press feedback: every tappable Social control springs down a little. */
@Composable
fun rememberPressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
        label = "socialPress"
    )
    return scale
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
    val accent = SocialColors.accent
    val fill = when {
        !enabled -> accent.copy(alpha = 0.35f)
        danger -> SocialColors.danger
        else -> accent
    }
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val shape = RoundedCornerShape(SocialDimens.controlRadius)
    Row(
        modifier = modifier
            .heightIn(min = SocialDimens.touch)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (danger) Color.White else SocialColors.onAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = if (danger) Color.White else SocialColors.onAccent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
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
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val shape = RoundedCornerShape(SocialDimens.controlRadius)
    Row(
        modifier = modifier
            .heightIn(min = SocialDimens.touch)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(SocialColors.panelSoft.copy(alpha = if (enabled) 0.85f else 0.4f))
            .border(BorderStroke(1.dp, SocialColors.border), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = SocialColors.textPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = SocialColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

/** Small square icon button used by the top bar and the chat composer. */
@Composable
fun SocialIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = SocialDimens.touch,
    iconSize: Dp = 20.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val background by animateColorAsState(
        targetValue = if (selected) SocialColors.accent else SocialColors.panelSoft.copy(alpha = 0.85f),
        animationSpec = tween(180),
        label = "iconButtonBackground"
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .border(BorderStroke(1.dp, SocialColors.border), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (selected) SocialColors.onAccent else SocialColors.textPrimary,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun SocialChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val background by animateColorAsState(
        targetValue = if (selected) SocialColors.accent.copy(alpha = 0.3f) else SocialColors.panelSoft.copy(alpha = 0.8f),
        animationSpec = tween(180),
        label = "chipBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) SocialColors.accent else SocialColors.border,
        animationSpec = tween(180),
        label = "chipBorder"
    )
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) SocialColors.textPrimary else SocialColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
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
            checkedThumbColor = SocialColors.onAccent,
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
fun SocialSliderRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialBody(title, color = SocialColors.textPrimary, size = 14.sp, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
            SocialBody("$value%", size = 12.sp, color = SocialColors.textMuted)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = SocialColors.accent,
                activeTrackColor = SocialColors.accent,
                inactiveTrackColor = SocialColors.panelSoft
            )
        )
    }
}

/** A text field in the Social glass style. */
@Composable
fun SocialTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = SocialColors.textPrimary, fontSize = 15.sp),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SocialDimens.controlRadius))
            .background(SocialColors.panelSoft)
            .border(BorderStroke(1.dp, SocialColors.border), RoundedCornerShape(SocialDimens.controlRadius))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) SocialBody(placeholder, size = 14.sp, color = SocialColors.textMuted)
                inner()
            }
        }
    )
}

/** Row showing one platform link; tapping opens the destination. */
@Composable
fun SocialLinkRow(link: SocialLink, onOpen: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SocialDimens.touch)
            .clip(RoundedCornerShape(SocialDimens.controlRadius))
            .background(SocialColors.panelSoft)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(linkPlatformIcon(link.platform)),
            contentDescription = link.platform.label,
            tint = SocialColors.accentBright,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            SocialBody(link.platform.label, size = 13.sp, color = SocialColors.textPrimary, weight = FontWeight.Medium)
            SocialBody(link.url, size = 11.sp, color = SocialColors.textMuted, maxLines = 1)
        }
        if (onRemove != null) {
            SocialIconButton(
                iconRes = R.drawable.aerix_ic_close,
                contentDescription = "Remove link",
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.aerix_social_ic_chevron),
                contentDescription = null,
                tint = SocialColors.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@DrawableRes
fun linkPlatformIcon(platform: net.kdt.pojavlaunch.aerix.social.data.LinkPlatform): Int = when (platform) {
    net.kdt.pojavlaunch.aerix.social.data.LinkPlatform.YOUTUBE -> R.drawable.aerix_social_ic_youtube
    net.kdt.pojavlaunch.aerix.social.data.LinkPlatform.INSTAGRAM -> R.drawable.aerix_social_ic_instagram
    net.kdt.pojavlaunch.aerix.social.data.LinkPlatform.DISCORD -> R.drawable.aerix_social_ic_discord
    net.kdt.pojavlaunch.aerix.social.data.LinkPlatform.X -> R.drawable.aerix_social_ic_x
    net.kdt.pojavlaunch.aerix.social.data.LinkPlatform.TIKTOK -> R.drawable.aerix_social_ic_tiktok
    net.kdt.pojavlaunch.aerix.social.data.LinkPlatform.TWITCH -> R.drawable.aerix_social_ic_twitch
    net.kdt.pojavlaunch.aerix.social.data.LinkPlatform.WEB -> R.drawable.aerix_ic_public
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
        SocialIconButton(
            iconRes = R.drawable.aerix_ic_arrow_back,
            contentDescription = "Back",
            onClick = onBack
        )
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
            .background(SocialColors.scrim.copy(alpha = 0.85f * progress))
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
                .background(
                Brush.verticalGradient(
                    listOf(SocialColors.panelRaised.copy(alpha = 0.82f), SocialColors.panel.copy(alpha = 0.72f))
                )
            )
                .border(BorderStroke(1.dp, SocialColors.borderStrong), RoundedCornerShape(22.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** Page wrapper: fixed 20/16dp padding and a 16dp rhythm between blocks. */
@Composable
fun SocialPage(
    padding: PaddingValues = PaddingValues(SocialDimens.pagePaddingH, SocialDimens.pagePaddingV),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}
