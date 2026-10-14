/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/components/AerixComponents.kt
 *     (AerixPillTabRow, AerixPillTab, AerixSectionHeader)
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/components/Switch.kt (DefaultSwitch)
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/home/MiraiHomeDashboard.kt
 *     (ModrinthMetaPill, ModrinthCompactSearchField)
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/layouts/_SettingsCard.kt
 *     (CardPosition, rememberSettingsCardShape, SettingsCardColumn)
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

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

/** Mirai AerixPillTabRow: liquid-glass pill bar used by Settings, Discover and Instance Management. */
@Composable
fun AerixPillTabRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = AerixSpacing.xs, vertical = AerixSpacing.xs),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .liquidGlass(
                shape = CircleShape,
                tint = AerixSurface.glassTint,
                strength = 0.72f,
                elevation = AerixMetrics.glassSelectedElevation
            )
            .selectableGroup()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Mirai AerixPillTab: one pill item inside [AerixPillTabRow]. */
@Composable
fun AerixPillTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val activeAccent = AerixThemeManager.currentAccent()
    val outline by animateColorAsState(
        targetValue = if (selected) AerixSurface.borderHighlight else Color.Transparent,
        animationSpec = tween(durationMillis = 160),
        label = "aerixTabOutline"
    )
    val contentColor = if (selected) activeAccent else AerixSurface.textSecondary
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = AerixMetrics.pillTabHeight)
            .then(
                if (selected) {
                    Modifier.liquidGlass(
                        shape = CircleShape,
                        tint = activeAccent,
                        strength = 0.95f,
                        elevation = AerixMetrics.glassSelectedElevation
                    )
                } else Modifier
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.48f),
        border = BorderStroke(AerixSpacing.hairline, outline),
        shadowElevation = if (selected) AerixSpacing.xs else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** Mirai ModrinthMetaPill: small badge used in Renderer Settings and Instance Management. */
@Composable
fun ModrinthMetaPill(
    text: String,
    highlighted: Boolean = false,
    backgroundColor: Color = if (highlighted) AerixSurface.accentContainer else AerixSurface.panelRaised,
    textColor: Color = if (highlighted) AerixSurface.modrinthBrand else AerixSurface.textSecondary,
    borderColor: Color? = if (highlighted) AerixSurface.modrinthBrand.copy(alpha = 0.45f) else null
) {
    val shape = RoundedCornerShape(AerixRadii.compact)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(backgroundColor)
            .then(if (borderColor != null) Modifier.border(AerixSpacing.hairline, borderColor, shape) else Modifier)
            .padding(horizontal = AerixSpacing.smTight, vertical = AerixSpacing.xxs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1
        )
    }
}

/** Mirai ModrinthCompactSearchField: 36dp search field with leading magnifying glass and clear button. */
@Composable
fun ModrinthCompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AerixRadii.controlSmall)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(AerixSurface.panel)
            .border(AerixSpacing.hairline, AerixSurface.borderSoft, shape)
            .padding(horizontal = AerixSpacing.smPlus),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smCompact)
    ) {
        Icon(
            painter = painterResource(R.drawable.aerix_ic_search),
            contentDescription = null,
            tint = AerixSurface.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = AerixSurface.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(AerixThemeManager.currentAccent()),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_close),
                contentDescription = null,
                tint = AerixSurface.textSecondary,
                modifier = Modifier
                    .size(15.dp)
                    .clickable { onValueChange("") }
            )
        }
    }
}

/** Mirai DefaultSwitch: M3 Switch with the rotating check/close icon inside the thumb. */
@Composable
fun DefaultSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(
        checkedThumbColor = AerixSurface.onAccent,
        checkedTrackColor = AerixThemeManager.currentAccent(),
        uncheckedThumbColor = AerixSurface.textSecondary,
        uncheckedTrackColor = AerixSurface.panelTrack,
        uncheckedBorderColor = AerixSurface.borderSoft
    )
) {
    val trackColor = if (checked) AerixThemeManager.currentAccent() else AerixSurface.panelTrack
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            val rotation by animateFloatAsState(if (checked) 0f else -180f, label = "switchThumbRotation")
            Crossfade(modifier = Modifier.rotate(rotation), targetState = checked, label = "switchThumbIcon") { state ->
                Icon(
                    modifier = Modifier.size(16.dp),
                    painter = painterResource(if (state) R.drawable.aerix_ic_check else R.drawable.aerix_ic_close),
                    contentDescription = null,
                    tint = trackColor
                )
            }
        },
        colors = colors
    )
}

/** Mirai CardPosition + rememberSettingsCardShape: grouped settings cards with 32dp outer corners and 4dp inner seams. */
enum class CardPosition { Top, Middle, Bottom, Single }

@Composable
fun rememberSettingsCardShape(
    position: CardPosition,
    outerShape: Dp = AerixRadii.hero,
    innerShape: Dp = AerixRadii.tiny
): Shape = remember(position, outerShape, innerShape) {
    when (position) {
        CardPosition.Top -> RoundedCornerShape(
            topStart = outerShape, topEnd = outerShape,
            bottomStart = innerShape, bottomEnd = innerShape
        )
        CardPosition.Middle -> RoundedCornerShape(innerShape)
        CardPosition.Bottom -> RoundedCornerShape(
            topStart = innerShape, topEnd = innerShape,
            bottomStart = outerShape, bottomEnd = outerShape
        )
        CardPosition.Single -> RoundedCornerShape(outerShape)
    }
}

@Composable
fun SettingsCardColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.xxs),
        content = content
    )
}
