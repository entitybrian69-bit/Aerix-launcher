/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Instance settings page: identity (name, icon, version, shared data) and launch settings
 * (renderer, Java runtime, control layout, memory, JVM arguments), plus delete / open folder / save.
 * Follows the layout of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/VersionConfigScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/VersionOverViewScreen.kt
 * Every value is read from and written to the PojavLauncher Instance by
 * net.kdt.pojavlaunch.aerix.bridge.PojavInstanceEditorBridge with the exact rules of
 * net.kdt.pojavlaunch.fragments.InstanceEditorFragment.
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

package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.bridge.AerixIconCropper
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceEditorBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavSettingsBridge
import net.kdt.pojavlaunch.aerix.components.ActionSettingRow
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.GlassPanel
import net.kdt.pojavlaunch.aerix.components.MetaPill
import net.kdt.pojavlaunch.aerix.components.SectionLabel
import net.kdt.pojavlaunch.aerix.components.SliderSettingRow
import net.kdt.pojavlaunch.aerix.components.SwitchSettingRow
import net.kdt.pojavlaunch.aerix.theme.AerixLayoutDefaults
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.instances.Instance
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog

@Composable
fun InstanceEditorScreen(activity: FragmentActivity, onClose: () -> Unit) {
    val draft = PojavInstanceEditorBridge.draft
    if (draft == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.no_instance), color = AerixSurface.textSecondary, fontSize = 13.sp)
        }
        return
    }

    val accent = AerixThemeManager.currentAccent()
    var showDelete by remember { mutableStateOf(false) }
    var showRenderer by remember { mutableStateOf(false) }
    var showRuntime by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var showArgs by remember { mutableStateOf(false) }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = AerixSurface.panelRaised,
            title = { Text(stringResource(R.string.aerix_editor_delete_title), color = AerixSurface.textPrimary) },
            text = {
                Text(
                    stringResource(R.string.aerix_editor_delete_message, draft.name.ifEmpty { draft.versionId }),
                    color = AerixSurface.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    if (PojavInstanceEditorBridge.delete(activity)) onClose()
                }) {
                    Text(stringResource(R.string.aerix_editor_delete_confirm), color = AerixSurface.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.aerix_editor_delete_cancel), color = AerixSurface.textSecondary)
                }
            }
        )
    }

    if (showRenderer) {
        val choices = remember { PojavInstanceEditorBridge.rendererChoices(activity) }
        InstanceChoiceDialog(
            title = stringResource(R.string.aerix_editor_renderer),
            labels = choices.map { it.label },
            selectedIndex = choices.indexOfFirst { it.id == draft.renderer },
            onDismiss = { showRenderer = false },
            onPick = { index ->
                draft.renderer = choices[index].id
                showRenderer = false
            }
        )
    }

    if (showRuntime) {
        val choices = remember { PojavInstanceEditorBridge.runtimeChoices(activity) }
        InstanceChoiceDialog(
            title = stringResource(R.string.aerix_editor_runtime),
            labels = choices.map { it.label },
            selectedIndex = choices.indexOfFirst { it.name == draft.runtime },
            onDismiss = { showRuntime = false },
            onPick = { index ->
                draft.runtime = choices[index].name
                showRuntime = false
            }
        )
    }

    if (showMemory) {
        MemoryDialog(activity = activity, onDismiss = { showMemory = false })
    }

    if (showArgs) {
        ArgsDialog(activity = activity, draftArgs = draft.jvmArgs, draftMode = draft.argsMode,
            onApply = { args, mode ->
                draft.jvmArgs = args
                draft.argsMode = mode
                showArgs = false
            },
            onDismiss = { showArgs = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AerixLayoutDefaults.pagePadding()),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        // ---- header: icon, name, version pill -------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_arrow_back),
                    contentDescription = stringResource(R.string.aerix_create_close),
                    tint = AerixSurface.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box {
                AerixImageWell(
                    image = draft.icon,
                    fallback = R.drawable.aerix_loader_vanilla,
                    size = 44.dp,
                    modifier = Modifier.clickable(role = Role.Button) {
                        AerixIconCropper.start(activity) { PojavInstanceEditorBridge.applyIcon(it) }
                    }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderHighlight), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_edit_outlined),
                        contentDescription = stringResource(R.string.aerix_editor_icon_hint),
                        tint = AerixSurface.onAccent,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            Text(
                text = draft.name.ifEmpty { stringResource(R.string.unnamed) },
                color = AerixSurface.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            MetaPill(text = draft.versionId.ifEmpty { stringResource(R.string.unnamed) })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            // ---- identity --------------------------------------------------------------------
            GlassPanel(modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = AerixSpacing.mdPlus) {
                SectionLabel(title = stringResource(R.string.aerix_editor_identity))
                Text(
                    text = stringResource(R.string.aerix_editor_identity_hint),
                    color = AerixSurface.textSecondary,
                    fontSize = 11.sp
                )
                val nameShape = RoundedCornerShape(AerixRadii.control)
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft.name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.aerix_editor_name_label), fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AerixSurface.textPrimary,
                        unfocusedTextColor = AerixSurface.textPrimary,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = AerixSurface.borderSoft,
                        cursorColor = accent,
                        focusedLabelColor = accent,
                        unfocusedLabelColor = AerixSurface.textSecondary
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(nameShape)
                        .background(AerixSurface.panelRaised.copy(alpha = 0.34f))
                        .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft), nameShape)
                        .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.aerix_editor_version, draft.versionId.ifEmpty { stringResource(R.string.unnamed) }),
                        color = AerixSurface.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    AerixGhostButton(text = stringResource(R.string.aerix_editor_change), iconRes = null, onClick = {
                        VersionSelectorDialog.open(activity, false) { id, _ -> draft.versionId = id }
                    })
                }

                SwitchSettingRow(
                    title = stringResource(R.string.aerix_editor_shared_data),
                    summary = stringResource(R.string.aerix_editor_shared_data_sub),
                    checked = draft.sharedData,
                    onCheckedChange = { draft.sharedData = it }
                )

                ActionSettingRow(
                    title = stringResource(R.string.aerix_editor_open_classic),
                    summary = stringResource(R.string.aerix_editor_open_classic_sub),
                    onClick = { PojavInstanceBridge.editClassic(activity) },
                    iconRes = R.drawable.aerix_ic_edit_outlined
                )
            }

            // ---- launch settings -------------------------------------------------------------
            GlassPanel(modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = AerixSpacing.mdPlus) {
                SectionLabel(title = stringResource(R.string.aerix_editor_launch_settings))
                ActionSettingRow(
                    title = stringResource(R.string.aerix_editor_renderer),
                    summary = null,
                    onClick = { showRenderer = true },
                    iconRes = R.drawable.aerix_ic_video_settings,
                    value = PojavInstanceEditorBridge.rendererLabel(activity, draft.renderer)
                )
                ActionSettingRow(
                    title = stringResource(R.string.aerix_editor_runtime),
                    summary = null,
                    onClick = { showRuntime = true },
                    iconRes = R.drawable.aerix_ic_java,
                    value = PojavInstanceEditorBridge.runtimeLabel(activity, draft.runtime)
                )
                ActionSettingRow(
                    title = stringResource(R.string.aerix_editor_control_layout),
                    summary = null,
                    onClick = { PojavInstanceEditorBridge.pickControlLayout(activity) },
                    iconRes = R.drawable.aerix_ic_touch_app_outlined,
                    value = PojavInstanceEditorBridge.controlLayoutLabel(activity, draft.controlLayout)
                )
                ActionSettingRow(
                    title = stringResource(R.string.aerix_editor_memory),
                    summary = null,
                    onClick = { showMemory = true },
                    iconRes = R.drawable.aerix_ic_java,
                    value = PojavInstanceEditorBridge.memoryLabel(activity)
                )
                ActionSettingRow(
                    title = stringResource(R.string.aerix_editor_jvm_args),
                    summary = null,
                    onClick = { showArgs = true },
                    iconRes = R.drawable.aerix_ic_terminal_outlined,
                    value = draft.jvmArgs.ifEmpty { stringResource(R.string.aerix_settings_value_none) }
                )
            }
        }

        // ---- bottom bar ----------------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            DeleteButton(onClick = { showDelete = true })
            Spacer(Modifier.weight(1f))
            AerixGhostButton(
                text = stringResource(R.string.aerix_editor_open_folder),
                iconRes = R.drawable.aerix_ic_folder_outlined,
                onClick = { PojavInstanceEditorBridge.openFolder(activity) }
            )
            AerixPrimaryButton(
                text = stringResource(R.string.aerix_editor_save),
                iconRes = R.drawable.aerix_ic_check,
                onClick = { if (PojavInstanceEditorBridge.save(activity)) onClose() }
            )
        }
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    val shape = CircleShape
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(AerixSurface.dangerContainer.copy(alpha = 0.75f), shape)
            .border(BorderStroke(AerixSpacing.hairline, AerixSurface.danger), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AerixSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
    ) {
        Icon(
            painter = painterResource(R.drawable.aerix_ic_delete_outlined),
            contentDescription = null,
            tint = AerixSurface.danger,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(R.string.aerix_editor_delete),
            color = AerixSurface.danger,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InstanceChoiceDialog(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AerixSurface.panelRaised,
        title = { Text(title, color = AerixSurface.textPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                labels.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AerixRadii.controlSmall))
                            .clickable(role = Role.RadioButton) { onPick(index) }
                            .padding(vertical = AerixSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onPick(index) },
                            colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = AerixSurface.textSecondary)
                        )
                        Text(label, color = AerixSurface.textPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary)
            }
        }
    )
}

/** The launcher-wide memory row (LauncherPreferences "allocation") edited from the instance page. */
@Composable
private fun MemoryDialog(activity: FragmentActivity, onDismiss: () -> Unit) {
    val setting = PojavSettingsBridge.memoryAllocation
    val min = setting.min(activity)
    val max = maxOf(min, setting.max(activity))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AerixSurface.panelRaised,
        title = { Text(stringResource(R.string.aerix_editor_memory), color = AerixSurface.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                Text(
                    text = stringResource(R.string.aerix_settings_memory_info, Tools.getTotalDeviceMemory(activity), PojavSettingsBridge.freeMemoryMb(activity)),
                    color = AerixSurface.textSecondary,
                    fontSize = 11.sp
                )
                SliderSettingRow(
                    title = stringResource(R.string.mcl_memory_allocation),
                    summary = null,
                    value = setting.current().coerceIn(min, max),
                    range = min..max,
                    step = setting.step,
                    suffix = setting.suffix,
                    onValueChange = { PojavSettingsBridge.writeInto(activity, setting, it) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aerix_editor_save), color = AerixThemeManager.currentAccent()) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary) }
        }
    )
}

/** Instance JVM arguments and their Instance.argsMode combination rule. */
@Composable
private fun ArgsDialog(
    activity: FragmentActivity,
    draftArgs: String,
    draftMode: Int,
    onApply: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    var args by remember { mutableStateOf(draftArgs) }
    var mode by remember { mutableStateOf(draftMode) }
    val launcherArgs = PojavSettingsBridge.javaArguments.current()
    val modes = listOf(
        Instance.ARGS_MODE_REPLACE to R.string.aerix_editor_arg_mode_instance,
        Instance.ARGS_MODE_MERGE_DEFAULT_FIRST to R.string.aerix_editor_arg_mode_default_first,
        Instance.ARGS_MODE_MERGE_INSTANCE_FIRST to R.string.aerix_editor_arg_mode_instance_first
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AerixSurface.panelRaised,
        title = { Text(stringResource(R.string.aerix_editor_jvm_args), color = AerixSurface.textPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.aerix_editor_jvm_args_sub),
                    color = AerixSurface.textSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = stringResource(R.string.aerix_editor_jvm_args_launcher, launcherArgs.ifEmpty { stringResource(R.string.aerix_settings_value_none) }),
                    color = AerixSurface.textMuted,
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = args,
                    onValueChange = { args = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AerixSurface.textPrimary,
                        unfocusedTextColor = AerixSurface.textPrimary,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = AerixSurface.borderSoft,
                        cursorColor = accent
                    )
                )
                Text(stringResource(R.string.aerix_editor_arg_mode), color = AerixSurface.textSecondary, fontSize = 11.sp)
                modes.forEach { (value, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AerixRadii.controlSmall))
                            .clickable(role = Role.RadioButton) { mode = value }
                            .padding(vertical = AerixSpacing.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == value,
                            onClick = { mode = value },
                            colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = AerixSurface.textSecondary)
                        )
                        Text(stringResource(labelRes), color = AerixSurface.textPrimary, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(args, mode) }) {
                Text(stringResource(R.string.aerix_editor_save), color = accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary)
            }
        }
    )
}
