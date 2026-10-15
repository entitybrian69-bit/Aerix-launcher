/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/layouts/_SettingsCard.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/layouts/List.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/layouts/Slider.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/layouts/TextInput.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/layouts/Enum.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/layouts/VersionSettingsLayout.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/components/Sliders.kt
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

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.PojavPluginBridge
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.game.renderer.def.Renderers
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import kotlin.math.roundToInt

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

/** Compact Modrinth-style search bar with leading search icon and optional clear button. */
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

/** Base Mirai SettingsCard surface (`_SettingsCard.kt`). */
@Composable
fun MiraiSettingsCard(
    position: CardPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = rememberSettingsCardShape(position)
    Surface(
        modifier = modifier,
        shape = shape,
        color = AerixSurface.panel,
        contentColor = Color.White,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
    ) {
        val clickMod = if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(innerPadding)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(innerPadding)
        }
        Column(modifier = clickMod, content = content)
    }
}

@Composable
fun MiraiTitleAndSummary(
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )
        if (!summary.isNullOrEmpty()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.70f)
            )
        }
    }
}

/** Mirai SwitchSettingsCard (`Switch.kt`). */
@Composable
fun MiraiSwitchSettingsCard(
    title: String,
    summary: String? = null,
    checked: Boolean,
    position: CardPosition = CardPosition.Single,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    MiraiSettingsCard(
        position = position,
        modifier = Modifier.fillMaxWidth(),
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = AerixSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiraiTitleAndSummary(
                title = title,
                summary = summary,
                modifier = Modifier.weight(1f)
            )
            DefaultSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

/**
 * Mirai inline-expanding ListSettingsCard (`List.kt` — Photos 1, 2, 3, and 5).
 * Tapping the header expands an animated radio list directly inside the card rather than opening a popup dialog.
 */
@Composable
fun <E> MiraiListSettingsCard(
    items: List<E>,
    currentId: String,
    defaultId: String,
    title: String,
    position: CardPosition = CardPosition.Single,
    modifier: Modifier = Modifier,
    summary: String? = null,
    getItemText: @Composable (E) -> String,
    getItemId: (E) -> String,
    getItemSummary: (@Composable (E) -> Unit)? = null,
    enabled: Boolean = true,
    autoCollapse: Boolean = true,
    selectedIdText: (@Composable () -> Unit)? = null,
    middleLayout: (@Composable ColumnScope.() -> Unit)? = null,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    onValueChange: (E) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedItem = remember(items, currentId, defaultId) {
        items.firstOrNull { getItemId(it) == currentId }
            ?: items.firstOrNull { getItemId(it) == defaultId }
            ?: items.firstOrNull()
    }
    val selectedText = if (selectedItem != null) getItemText(selectedItem) else currentId

    MiraiSettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = position
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { expanded = !expanded }
                    .padding(all = AerixSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MiraiTitleAndSummary(
                        title = title,
                        summary = summary
                    )
                    middleLayout?.invoke(this)
                    if (selectedIdText != null) {
                        selectedIdText()
                    } else {
                        Text(
                            modifier = Modifier.alpha(0.7f),
                            text = "Selected: $selectedText",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    trailingIcon?.invoke(this)
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) -180f else 0f,
                        animationSpec = tween(durationMillis = 220),
                        label = "listChevronRotation"
                    )
                    IconButton(
                        modifier = Modifier
                            .size(34.dp)
                            .rotate(rotation),
                        enabled = enabled,
                        onClick = { expanded = !expanded }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_arrow_drop_down_rounded),
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = Color.White
                        )
                    }
                }
            }

            AnimatedVisibility(
                modifier = Modifier.fillMaxWidth(),
                visible = expanded,
                enter = expandVertically(animationSpec = tween(durationMillis = 220)),
                exit = shrinkVertically(animationSpec = tween(durationMillis = 200)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AerixSpacing.sm,
                            end = AerixSpacing.sm,
                            bottom = AerixSpacing.sm
                        )
                ) {
                    items.forEach { item ->
                        val itemId = getItemId(item)
                        val isSelected = itemId == currentId || (items.none { getItemId(it) == currentId } && item == selectedItem)
                        val itemName = getItemText(item)
                        MiraiSimpleListItem(
                            selected = isSelected,
                            itemName = itemName,
                            summary = getItemSummary?.let { summarySlot ->
                                { summarySlot.invoke(item) }
                            },
                            onClick = {
                                if (autoCollapse) expanded = false
                                onValueChange(item)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Radio item inside `MiraiListSettingsCard` (`SimpleListItem` from `Layouts.kt`). */
@Composable
fun MiraiSimpleListItem(
    selected: Boolean,
    itemName: String,
    modifier: Modifier = Modifier,
    summary: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val accent = AerixThemeManager.currentAccent()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = AerixSpacing.sm, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = accent,
                unselectedColor = Color.White.copy(alpha = 0.70f)
            )
        )
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = itemName,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            summary?.invoke()
        }
    }
}

/**
 * Mirai SimpleTextSlider (`Sliders.kt` — Photo 4):
 * Thick pill slider on the left + cyan badge showing `<value><suffix> < >` with fine-tuning buttons and tap-to-input dialog.
 */
@Composable
fun MiraiSimpleTextSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    toInt: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    suffix: String? = null,
    enabled: Boolean = true,
    fineTuningControl: Boolean = false,
    fineTuningStep: Float = 1f,
    appendContent: @Composable RowScope.() -> Unit = {}
) {
    val accent = AerixThemeManager.currentAccent()
    var showEditDialog by remember { mutableStateOf(false) }
    var editInput by remember(value) {
        mutableStateOf(if (toInt) value.roundToInt().toString() else String.format("%.2f", value))
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = AerixSurface.panelRaised,
            title = { Text("Enter Value", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = editInput,
                    onValueChange = { editInput = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = {
                        Text(
                            "Range: ${valueRange.start.roundToInt()} - ${valueRange.endInclusive.roundToInt()}${suffix ?: ""}",
                            color = AerixSurface.textSecondary
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = AerixSurface.borderSoft
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = editInput.trim().toFloatOrNull()
                    if (parsed != null) {
                        val clamped = parsed.coerceIn(valueRange)
                        onValueChange(clamped)
                        onValueChangeFinished()
                    }
                    showEditDialog = false
                }) {
                    Text("OK", color = accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = AerixSurface.textSecondary)
                }
            }
        )
    }

    fun changeValue(newVal: Float, finished: Boolean) {
        val next = (if (toInt) newVal.roundToInt().toFloat() else newVal).coerceIn(valueRange)
        onValueChange(next)
        if (finished) onValueChangeFinished()
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = { changeValue(it, false) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = AerixSurface.panelTrack,
                disabledThumbColor = accent.copy(alpha = 0.45f),
                disabledActiveTrackColor = accent.copy(alpha = 0.45f),
                disabledInactiveTrackColor = AerixSurface.panelTrack.copy(alpha = 0.5f)
            ),
            modifier = Modifier.weight(1f)
        )
        Surface(
            modifier = Modifier
                .padding(start = 12.dp)
                .animateContentSize(),
            color = if (enabled) accent else accent.copy(alpha = 0.40f),
            contentColor = AerixSurface.onAccent,
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = enabled) { showEditDialog = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val textVal = if (toInt) "${value.roundToInt()}" else String.format("%.2f", value)
                    Text(
                        text = textVal,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = AerixSurface.onAccent
                    )
                    if (suffix != null) {
                        Text(
                            text = suffix,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = AerixSurface.onAccent
                        )
                    }
                }
                if (fineTuningControl) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        modifier = Modifier.size(24.dp),
                        enabled = enabled && value > valueRange.start,
                        onClick = { changeValue(value - fineTuningStep, true) }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_arrow_back),
                            contentDescription = "Decrease",
                            tint = AerixSurface.onAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    IconButton(
                        modifier = Modifier.size(24.dp),
                        enabled = enabled && value < valueRange.endInclusive,
                        onClick = { changeValue(value + fineTuningStep, true) }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_chevron_right),
                            contentDescription = "Increase",
                            tint = AerixSurface.onAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        appendContent()
    }
}

/** Mirai IntSliderSettingsCard (`Slider.kt`). */
@Composable
fun MiraiIntSliderSettingsCard(
    value: Int,
    title: String,
    position: CardPosition = CardPosition.Single,
    modifier: Modifier = Modifier,
    summary: String? = null,
    valueRange: IntRange,
    steps: Int = 0,
    suffix: String? = null,
    enabled: Boolean = true,
    fineTuningControl: Boolean = true,
    fineTuningStep: Int = 1,
    previewText: (@Composable ColumnScope.() -> Unit)? = null,
    onValueChange: (Int) -> Unit = {},
    onValueFinished: (Int) -> Unit = {}
) {
    var currentVal by remember(value) { mutableIntStateOf(value) }
    MiraiSettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = position
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = AerixSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiraiTitleAndSummary(title = title, summary = summary)
            MiraiSimpleTextSlider(
                modifier = Modifier.fillMaxWidth(),
                value = currentVal.toFloat(),
                enabled = enabled,
                onValueChange = {
                    currentVal = it.roundToInt()
                    onValueChange(currentVal)
                },
                onValueChangeFinished = { onValueFinished(currentVal) },
                toInt = true,
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                steps = steps,
                suffix = suffix,
                fineTuningControl = fineTuningControl,
                fineTuningStep = fineTuningStep.toFloat()
            )
            previewText?.invoke(this)
        }
    }
}

/**
 * Mirai ToggleableIntSliderSettingsCard (`VersionSettingsLayout.kt` — Photo 4):
 * Used for per-instance Memory Allocation with a slider + `<value>MB < >` pill + enable Checkbox on the right.
 */
@Composable
fun MiraiToggleableIntSliderSettingsCard(
    currentValue: Int,
    defaultValue: Int,
    title: String,
    position: CardPosition = CardPosition.Middle,
    modifier: Modifier = Modifier,
    summary: String? = null,
    valueRange: IntRange,
    suffix: String? = null,
    enabled: Boolean = true,
    fineTuningControl: Boolean = true,
    onValueChange: (Int) -> Unit = {},
    onValueFinished: (Int) -> Unit = {}
) {
    val accent = AerixThemeManager.currentAccent()
    var checked by remember(currentValue) { mutableStateOf(currentValue >= valueRange.first) }
    var value by remember(currentValue, defaultValue) {
        mutableIntStateOf(currentValue.takeIf { it >= valueRange.first } ?: defaultValue)
    }

    MiraiSettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = position,
        innerPadding = PaddingValues(
            start = AerixSpacing.lg,
            end = AerixSpacing.sm,
            top = AerixSpacing.lg,
            bottom = AerixSpacing.sm
        )
    ) {
        MiraiTitleAndSummary(
            modifier = Modifier.padding(end = AerixSpacing.sm),
            title = title,
            summary = summary
        )
        MiraiSimpleTextSlider(
            modifier = Modifier.fillMaxWidth(),
            value = value.toFloat(),
            enabled = enabled && checked,
            onValueChange = {
                value = it.roundToInt()
                onValueChange(value)
            },
            onValueChangeFinished = { onValueFinished(value) },
            toInt = true,
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            suffix = suffix,
            fineTuningControl = fineTuningControl,
            appendContent = {
                Checkbox(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = { isChecked ->
                        checked = isChecked
                        if (isChecked) {
                            value = defaultValue
                            onValueFinished(value)
                        } else {
                            onValueFinished(-1)
                        }
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = accent,
                        checkmarkColor = AerixSurface.onAccent,
                        uncheckedColor = Color.White.copy(alpha = 0.70f)
                    )
                )
            }
        )
    }
}

/** Mirai Three-State setting (`Follow Global`, `Enable`, `Disable`) used in `VersionConfigScreen.kt` (Photo 3). */
enum class MiraiFollowGlobalState(val label: String) {
    FOLLOW_GLOBAL("Follow Global"),
    ENABLE("Enable"),
    DISABLE("Disable")
}

/**
 * Mirai StatefulDropdownMenuFollowGlobal (`VersionSettingsLayout.kt` — Photo 3):
 * Shows `Title` + `Summary` on the left, and `<StateLabel> [GearIcon]` on the right with a dropdown menu.
 */
@Composable
fun MiraiStatefulDropdownMenuFollowGlobal(
    currentState: MiraiFollowGlobalState,
    title: String,
    position: CardPosition = CardPosition.Middle,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onValueChange: (MiraiFollowGlobalState) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = AerixThemeManager.currentAccent()

    MiraiSettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = position,
        innerPadding = PaddingValues(all = AerixSpacing.lg),
        onClick = if (enabled) ({ expanded = !expanded }) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            MiraiTitleAndSummary(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = AerixSpacing.md),
                title = title,
                summary = summary
            )
            Row(
                modifier = Modifier.align(Alignment.CenterVertically),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = currentState.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                IconButton(
                    modifier = Modifier.size(34.dp),
                    enabled = enabled,
                    onClick = { expanded = !expanded }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_settings_filled),
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = MaterialTheme.shapes.large,
                    containerColor = AerixSurface.panelRaised
                ) {
                    MiraiFollowGlobalState.entries.forEach { state ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = state.label,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                RadioButton(
                                    selected = currentState == state,
                                    onClick = {
                                        onValueChange(state)
                                        expanded = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = accent,
                                        unselectedColor = Color.White.copy(alpha = 0.7f)
                                    )
                                )
                            },
                            onClick = {
                                onValueChange(state)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Mirai TextInputSettingsCard (`TextInput.kt` — Photo 4). */
@Composable
fun MiraiTextInputSettingsCard(
    value: String,
    title: String,
    position: CardPosition = CardPosition.Middle,
    modifier: Modifier = Modifier,
    summary: String? = null,
    label: String = "Follow global if left blank",
    onValueChange: (String) -> Unit = {},
    singleLine: Boolean = true
) {
    val accent = AerixThemeManager.currentAccent()
    MiraiSettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = position,
        innerPadding = PaddingValues(
            start = AerixSpacing.lg,
            end = AerixSpacing.lg,
            top = AerixSpacing.lg,
            bottom = AerixSpacing.md
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiraiTitleAndSummary(title = title, summary = summary)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                textStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                onValueChange = { raw ->
                    val cleaned = if (singleLine) raw.replace("\n", "").replace("\r", "") else raw
                    onValueChange(cleaned)
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                },
                singleLine = singleLine,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = AerixSurface.borderSoft,
                    cursorColor = accent
                )
            )
        }
    }
}

/** Mirai EnumSettingsCard (`Enum.kt`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <E> MiraiEnumSettingsCard(
    items: List<E>,
    currentValue: E,
    title: String,
    position: CardPosition = CardPosition.Single,
    modifier: Modifier = Modifier,
    summary: String? = null,
    getItemText: @Composable (E) -> String,
    onValueChange: (E) -> Unit = {}
) {
    val accent = AerixThemeManager.currentAccent()
    MiraiSettingsCard(
        modifier = modifier.fillMaxWidth(),
        position = position,
        innerPadding = PaddingValues(all = AerixSpacing.lg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiraiTitleAndSummary(title = title, summary = summary)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onValueChange(item) }
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentValue == item,
                            onClick = { onValueChange(item) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accent,
                                unselectedColor = Color.White.copy(alpha = 0.70f)
                            )
                        )
                        Text(
                            text = getItemText(item),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Built-in renderer metadata ported from `/home/user/mirai` (`Renderers.kt` + `renderers/*.kt`)
 * matching Photos 1, 2, and 5 (`Krypton Wrapper`, `MobileGlues`, `GL4ES`, `vgpu - (up to 1.16.5, fast)`,
 * `VGPU 1.3.6β`, `LTW Legacy`, `LTW (Large Thin Wrapper)`, `Kopper Zink`, `VirGLRenderer`, `Freedreno (Adreno)`).
 */
data class MiraiRendererEntry(
    val uniqueId: String,
    val displayName: String,
    val backendRendererId: String?,
    val summary: String? = null,
    val minVer: String? = null,
    val maxVer: String? = null
)

private const val PREF_MIRAI_GLOBAL_RENDERER_ID = "aerix_mirai_global_renderer_id"
private const val PREF_MIRAI_INSTANCE_RENDERER_PREFIX = "aerix_mirai_instance_renderer_"

fun miraiRendererEntries(context: Context, includeFollowGlobal: Boolean): List<MiraiRendererEntry> {
    val list = ArrayList<MiraiRendererEntry>()
    if (includeFollowGlobal) {
        list.add(
            MiraiRendererEntry(
                uniqueId = "",
                displayName = "Follow Global",
                backendRendererId = null
            )
        )
    }
    list.addAll(
        listOf(
            MiraiRendererEntry(
                uniqueId = "opengles3",
                displayName = "Krypton Wrapper",
                backendRendererId = Renderers.NGGL4ES_RENDERER,
                minVer = "1.17",
                maxVer = "26.2"
            ),
            MiraiRendererEntry(
                uniqueId = "opengles3_mobileglues",
                displayName = "MobileGlues",
                backendRendererId = Renderers.MOBILEGLUES_RENDERER,
                minVer = "1.17",
                maxVer = "26.3"
            ),
            MiraiRendererEntry(
                uniqueId = "opengles2",
                displayName = "GL4ES",
                backendRendererId = Renderers.GL4ES_RENDERER,
                maxVer = "1.16.5"
            ),
            MiraiRendererEntry(
                uniqueId = "opengles2_vgpu",
                displayName = "vgpu - (up to 1.16.5, fast)",
                backendRendererId = Renderers.SFPEW_RENDERER,
                summary = "( Performative and balance for version above 1.16.5)",
                maxVer = "1.16.5"
            ),
            MiraiRendererEntry(
                uniqueId = "opengles2_vgpu_1",
                displayName = "VGPU 1.3.6\u03B2",
                backendRendererId = Renderers.SFPEW_RENDERER,
                summary = "(Compat with all version and very reliable)",
                maxVer = "1.16.5"
            ),
            MiraiRendererEntry(
                uniqueId = "opengles2_ltwlegacy",
                displayName = "LTW Legacy",
                backendRendererId = Renderers.GL4ES_RENDERER,
                summary = "OpenGL 1.x/2.1 translation layer for Minecraft 1.8 - 1.16.5, built from source.",
                minVer = "1.8",
                maxVer = "1.16.5"
            ),
            MiraiRendererEntry(
                uniqueId = "opengles3_ltw",
                displayName = "LTW (Large Thin Wrapper)",
                backendRendererId = Renderers.LTW_RENDERER,
                summary = "Incomplete OpenGL 3.2 core wrapper on OpenGL ES 3; game, mod, and device compatibility varies.",
                minVer = "1.17"
            ),
            MiraiRendererEntry(
                uniqueId = "opengles3_desktopgl_zink_kopper",
                displayName = "Kopper Zink",
                backendRendererId = Renderers.ZINK_RENDERER,
                maxVer = "26.2"
            ),
            MiraiRendererEntry(
                uniqueId = "gallium_virgl",
                displayName = "VirGLRenderer",
                backendRendererId = Renderers.LEGACYZINK_RENDERER,
                maxVer = "26.2"
            ),
            MiraiRendererEntry(
                uniqueId = "gallium_freedreno",
                displayName = "Freedreno (Adreno)",
                backendRendererId = Renderers.FREEDRENO_RENDERER,
                maxVer = "26.2"
            )
        )
    )
    runCatching { PojavPluginBridge.loadPlugins(context) }.getOrDefault(emptyList()).forEach { plugin ->
        if (list.none { it.uniqueId == plugin.pluginTag }) {
            list.add(
                MiraiRendererEntry(
                    uniqueId = plugin.pluginTag,
                    displayName = plugin.displayName,
                    backendRendererId = plugin.pluginTag,
                    summary = plugin.summary.ifBlank { "External Renderer Plugin (${plugin.packageName})" },
                    minVer = plugin.minMCVer,
                    maxVer = plugin.maxMCVer
                )
            )
        }
    }
    return list
}

fun getMiraiGlobalRendererId(context: Context): String {
    val entries = miraiRendererEntries(context, includeFollowGlobal = false)
    val saved = LauncherPreferences.DEFAULT_PREF.getString(PREF_MIRAI_GLOBAL_RENDERER_ID, null)
    if (!saved.isNullOrEmpty() && entries.any { it.uniqueId == saved }) {
        return saved
    }
    val backend = LauncherPreferences.PREF_RENDERER
    return entries.firstOrNull { it.backendRendererId == backend }?.uniqueId
        ?: entries.first().uniqueId
}

fun setMiraiGlobalRenderer(entry: MiraiRendererEntry) {
    val backend = entry.backendRendererId ?: Renderers.NGGL4ES_RENDERER
    LauncherPreferences.PREF_RENDERER = backend
    LauncherPreferences.DEFAULT_PREF.edit()
        .putString("renderer", backend)
        .putString(PREF_MIRAI_GLOBAL_RENDERER_ID, entry.uniqueId)
        .apply()
}

fun getMiraiInstanceRendererId(context: Context, instanceKey: String, backendRendererId: String?): String {
    if (backendRendererId.isNullOrEmpty()) return ""
    val entries = miraiRendererEntries(context, includeFollowGlobal = true)
    val saved = LauncherPreferences.DEFAULT_PREF.getString(PREF_MIRAI_INSTANCE_RENDERER_PREFIX + instanceKey, null)
    if (!saved.isNullOrEmpty()) {
        val matched = entries.firstOrNull { it.uniqueId == saved }
        if (matched != null && matched.backendRendererId == backendRendererId) {
            return matched.uniqueId
        }
    }
    return entries.firstOrNull { it.backendRendererId == backendRendererId }?.uniqueId ?: backendRendererId
}

fun saveMiraiInstanceRendererId(instanceKey: String, entry: MiraiRendererEntry) {
    LauncherPreferences.DEFAULT_PREF.edit()
        .putString(PREF_MIRAI_INSTANCE_RENDERER_PREFIX + instanceKey, entry.uniqueId)
        .apply()
}

/**
 * Mirai RendererSummaryLayout (`RendererSettingsScreen.kt` — Photos 1, 2, and 5):
 * Renders optional renderer summary + `MC Version Support: >= minVer <= maxVer`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MiraiRendererSummaryLayout(renderer: MiraiRendererEntry) {
    FlowRow(
        modifier = Modifier.alpha(0.7f),
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        renderer.summary?.let { summaryText ->
            Text(
                text = summaryText,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
        val minVer = renderer.minVer
        val maxVer = renderer.maxVer
        if (minVer != null || maxVer != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "MC Version Support:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                if (minVer != null) {
                    Text(
                        text = ">= $minVer",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                if (maxVer != null) {
                    Text(
                        text = "<= $maxVer",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}
