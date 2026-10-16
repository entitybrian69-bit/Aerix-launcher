/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: the download dialog of mirai-launcher- (Zalith Launcher 2 fork): what a file needs on the
 * left, which instances to install it into on the right, Cancel and Install underneath; plus the
 * progress panel that the same install collapses into when it is minimized.
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/assets/download/DependenciesDialog.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/assets/download/DownloadAssetsScreen.kt
 * Zalith Launcher 2 Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixInstallQueue
import net.kdt.pojavlaunch.aerix.bridge.AerixInstallStatus
import net.kdt.pojavlaunch.aerix.bridge.AerixInstallTask
import net.kdt.pojavlaunch.aerix.bridge.AssetFile
import net.kdt.pojavlaunch.aerix.bridge.AssetTarget
import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.InstanceTarget
import net.kdt.pojavlaunch.aerix.bridge.ResolvedDependency
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

/**
 * The dialog a download goes through, laid out as in Zalith Launcher 2: dependencies on one side,
 * the instances to install into on the other, Cancel and Install at the bottom. On a screen too
 * narrow for two panes the same content stacks, dependencies first.
 *
 * [onInstall] is handed the ticks the user left in place and nothing else, so the queue installs
 * exactly what was on screen when Install was pressed.
 */
@Composable
internal fun AssetInstallDialog(
    file: AssetFile,
    type: AssetType,
    targets: List<AssetTarget>,
    dependencies: List<ResolvedDependency>,
    detected: InstanceTarget?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onInstall: (List<AssetTarget>, List<ResolvedDependency>) -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    // Ticked dependencies are remembered per project id, so reopening the dialog keeps the choices.
    val dependencyTicks = remember(dependencies) {
        mutableStateMapOf<String, Boolean>().apply {
            dependencies.forEach { put(it.project.id + it.project.platform, it.required) }
        }
    }
    val targetTicks = remember(targets) {
        mutableStateMapOf<String, Boolean>().apply {
            // Every instance is ticked to begin with: installing into several at once is the point
            // of the picker, and unticking is one tap.
            targets.forEach { put(it.name + it.versionId, true) }
        }
    }

    val isModpack = type == AssetType.MODPACK
    val pickedTargets = targets.filter { targetTicks[target.name + target.versionId] == true }
    val pickedDependencies = dependencies.filter { dependencyTicks[it.project.id + it.project.platform] == true }
    val canInstall = if (isModpack || targets.isEmpty()) true else pickedTargets.isNotEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AerixSurface.canvas.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .widthIn(min = 300.dp, max = 720.dp)
                    .padding(AerixSpacing.md)
            ) {
                val wide = maxWidth >= 560.dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AerixRadii.dialog))
                        .background(AerixSurface.panelRaised)
                        .border(AerixSpacing.hairline, AerixSurface.borderSoft, RoundedCornerShape(AerixRadii.dialog))
                        // Swallows the taps meant for the panel instead of the scrim behind it.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { }
                        .padding(AerixSpacing.lg)
                ) {
                    InstallHeader(file = file, onDismiss = onDismiss)

                    if (isModpack) {
                        Text(
                            text = stringResource(R.string.aerix_install_modpack_note),
                            color = AerixSurface.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = AerixSpacing.sm)
                        )
                    } else if (wide) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .padding(top = AerixSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                        ) {
                            DependencyPane(
                                dependencies = dependencies,
                                ticks = dependencyTicks,
                                loading = loading,
                                accent = accent,
                                listMaxHeight = 360.dp,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            TargetPane(
                                targets = targets,
                                fileVersions = file.gameVersions,
                                ticks = targetTicks,
                                detected = detected,
                                ticksChanged = { key, checked -> targetTicks[key] = checked },
                                accent = accent,
                                listMaxHeight = 360.dp,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(top = AerixSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                        ) {
                            DependencyPane(
                                dependencies = dependencies,
                                ticks = dependencyTicks,
                                loading = loading,
                                accent = accent,
                                listMaxHeight = 190.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TargetPane(
                                targets = targets,
                                fileVersions = file.gameVersions,
                                ticks = targetTicks,
                                detected = detected,
                                ticksChanged = { key, checked -> targetTicks[key] = checked },
                                accent = accent,
                                listMaxHeight = 190.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AerixSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        AerixGhostButton(
                            text = stringResource(R.string.aerix_install_cancel),
                            iconRes = null,
                            onClick = onDismiss
                        )
                        AerixPrimaryButton(
                            text = if (pickedTargets.size > 1 && !isModpack) {
                                stringResource(R.string.aerix_install_confirm_to, pickedTargets.size)
                            } else {
                                stringResource(R.string.aerix_install_confirm)
                            },
                            iconRes = R.drawable.aerix_ic_download,
                            enabled = canInstall,
                            onClick = { onInstall(pickedTargets, pickedDependencies) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallHeader(file: AssetFile, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.aerix_install_title, file.name),
                color = AerixSurface.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = file.versionNumber,
                color = AerixSurface.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = AerixSurface.textSecondary,
            onClick = onDismiss
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_close),
                contentDescription = stringResource(R.string.aerix_install_cancel),
                modifier = Modifier.padding(AerixSpacing.xs).size(18.dp)
            )
        }
    }
}

/** Left pane of the dialog: what the file needs besides itself, one tick box per project. */
@Composable
private fun DependencyPane(
    dependencies: List<ResolvedDependency>,
    ticks: MutableMap<String, Boolean>,
    loading: Boolean,
    accent: Color,
    listMaxHeight: Dp,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        PaneTitle(text = stringResource(R.string.aerix_install_dependencies), accent = accent)
        when {
            loading && dependencies.isEmpty() -> PaneNote(
                text = stringResource(R.string.aerix_install_dependencies_loading),
                loading = true
            )
            dependencies.isEmpty() -> PaneNote(
                text = stringResource(R.string.aerix_install_dependencies_none),
                loading = false
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = listMaxHeight),
                contentPadding = PaddingValues(vertical = AerixSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
            ) {
                items(dependencies, key = { it.project.platform.name + it.project.id }) { dependency ->
                    DependencyRow(
                        dependency = dependency,
                        checked = ticks[dependency.project.id + dependency.project.platform] == true,
                        accent = accent,
                        onCheckedChange = { checked ->
                            ticks[dependency.project.id + dependency.project.platform] = checked
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DependencyRow(
    dependency: ResolvedDependency,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    val icon = rememberRemoteBitmap(dependency.project.iconUrl, targetMinEdgePx = 48)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AerixRadii.controlSmall))
            .background(if (checked) accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable { onCheckedChange(!checked) }
            .padding(end = AerixSpacing.sm, vertical = AerixSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = accent,
                checkmarkColor = AerixSurface.onAccent,
                uncheckedColor = AerixSurface.textMuted
            )
        )
        AerixImageWell(
            image = icon,
            fallback = R.drawable.aerix_ic_extension_outlined,
            size = 28.dp,
            radius = AerixRadii.micro
        )
        Spacer(modifier = Modifier.width(AerixSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dependency.project.title,
                color = AerixSurface.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val dependencyFile = dependency.file
            Text(
                text = dependencyFile?.fileName
                    ?: stringResource(R.string.aerix_install_dependency_missing),
                color = if (dependencyFile == null) AerixSurface.warning else AerixSurface.textMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(AerixSpacing.xs))
        DependencyChip(required = dependency.required, accent = accent)
    }
}

@Composable
private fun DependencyChip(required: Boolean, accent: Color) {
    val tint = if (required) accent else AerixSurface.textMuted
    Surface(
        shape = RoundedCornerShape(AerixRadii.pill),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(AerixSpacing.hairline, tint.copy(alpha = 0.5f))
    ) {
        Text(
            text = stringResource(
                if (required) R.string.aerix_install_dependency_required
                else R.string.aerix_install_dependency_optional
            ),
            color = tint,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = AerixSpacing.smCompact, vertical = 1.dp)
        )
    }
}

/** Right pane of the dialog: the instances to install into, several at once. */
@Composable
private fun TargetPane(
    targets: List<AssetTarget>,
    /** Versions the file itself covers: an instance outside them gets a warning, not a blocked tick. */
    fileVersions: List<String>,
    ticks: MutableMap<String, Boolean>,
    detected: InstanceTarget?,
    ticksChanged: (String, Boolean) -> Unit,
    accent: Color,
    listMaxHeight: Dp,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        PaneTitle(text = stringResource(R.string.aerix_install_targets), accent = accent)
        val detectedLabel = detected?.let { target ->
            listOfNotNull(
                target.mcVersion,
                target.loader?.replaceFirstChar { it.uppercase() }
            ).joinToString(" · ")
        }
        if (!detectedLabel.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.aerix_install_targets_detected, detectedLabel),
                color = AerixSurface.textMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = AerixSpacing.xs, vertical = 1.dp)
            )
        }
        if (targets.isEmpty()) {
            PaneNote(text = stringResource(R.string.aerix_install_targets_none), loading = false)
            return@Column
        }
        val allTicked = targets.all { ticks[it.name + it.versionId] == true }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AerixSpacing.xs, vertical = AerixSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(AerixRadii.pill),
                color = accent.copy(alpha = 0.12f),
                onClick = { targets.forEach { ticksChanged(it.name + it.versionId, !allTicked) } }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AerixSpacing.smPlus, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                ) {
                    Icon(
                        painter = painterResource(
                            if (allTicked) R.drawable.aerix_ic_deselect else R.drawable.aerix_ic_select_all
                        ),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(
                            if (allTicked) R.string.aerix_install_deselect_all
                            else R.string.aerix_install_select_all
                        ),
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = listMaxHeight),
            contentPadding = PaddingValues(vertical = AerixSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
        ) {
            items(targets, key = { it.name + it.versionId }) { target ->
                TargetRow(
                    target = target,
                    mismatch = target.mcVersion != null && fileVersions.isNotEmpty() &&
                        fileVersions.none { it.equals(target.mcVersion, ignoreCase = true) },
                    checked = ticks[target.name + target.versionId] == true,
                    accent = accent,
                    onCheckedChange = { checked -> ticksChanged(target.name + target.versionId, checked) }
                )
            }
        }
    }
}

@Composable
private fun TargetRow(
    target: AssetTarget,
    /** True when the file has no build for the instance's Minecraft version. */
    mismatch: Boolean,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(AerixRadii.controlSmall),
        color = if (checked) accent.copy(alpha = 0.12f) else AerixSurface.panel,
        border = BorderStroke(
            AerixSpacing.hairline,
            if (checked) accent.copy(alpha = 0.55f) else AerixSurface.borderSoft
        ),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = AerixSpacing.sm, top = AerixSpacing.xxs, bottom = AerixSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = accent,
                    checkmarkColor = AerixSurface.onAccent,
                    uncheckedColor = AerixSurface.textMuted
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.name,
                    color = AerixSurface.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = target.subtitle,
                    color = AerixSurface.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (target.detected || mismatch) {
                Spacer(modifier = Modifier.width(AerixSpacing.xs))
                if (mismatch) {
                    TargetBadge(
                        text = stringResource(R.string.aerix_install_target_mismatch, target.mcVersion.orEmpty()),
                        tint = AerixSurface.warning
                    )
                } else {
                    TargetBadge(text = stringResource(R.string.aerix_install_target_detected), tint = accent)
                }
            }
        }
    }
}

@Composable
private fun TargetBadge(text: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(AerixRadii.pill),
        color = tint.copy(alpha = 0.16f)
    ) {
        Text(
            text = text,
            color = tint,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = AerixSpacing.smCompact, vertical = 1.dp)
        )
    }
}

@Composable
private fun PaneTitle(text: String, accent: Color) {
    Text(
        text = text.uppercase(),
        color = accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(horizontal = AerixSpacing.xs)
    )
}

@Composable
private fun PaneNote(text: String, loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AerixSpacing.xs, vertical = AerixSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = AerixSurface.textMuted
            )
        }
        Text(text = text, color = AerixSurface.textMuted, fontSize = 11.sp)
    }
}

// ============================================================================================
// The running install: the panel the dialog becomes, and the pill it collapses into
// ============================================================================================

/**
 * Progress of everything the queue is doing, with a Minimize button. This is what the plan dialog
 * turns into once Install is pressed, and it is also what the pill reopens, so the same install can
 * be watched from any section of the launcher.
 */
@Composable
internal fun AerixInstallPanel(onMinimize: () -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val tasks = AerixInstallQueue.tasks
    val anyRunning = AerixInstallQueue.running

    Dialog(
        onDismissRequest = onMinimize,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AerixSurface.canvas.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onMinimize() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 300.dp, max = 520.dp)
                    .padding(AerixSpacing.md)
                    .clip(RoundedCornerShape(AerixRadii.dialog))
                    .background(AerixSurface.panelRaised)
                    .border(AerixSpacing.hairline, AerixSurface.borderSoft, RoundedCornerShape(AerixRadii.dialog))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
                    .padding(AerixSpacing.lg)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.aerix_install_tasks).uppercase(),
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (tasks.any { it.finished }) {
                        Surface(
                            shape = RoundedCornerShape(AerixRadii.pill),
                            color = AerixSurface.panel,
                            onClick = { AerixInstallQueue.clearFinished() }
                        ) {
                            Text(
                                text = stringResource(R.string.aerix_install_clear_finished),
                                color = AerixSurface.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = AerixSpacing.smPlus, vertical = 4.dp)
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(top = AerixSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        InstallTaskCard(task = task, accent = accent)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AerixSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    AerixGhostButton(
                        text = stringResource(
                            if (anyRunning) R.string.aerix_install_minimize
                            else R.string.aerix_install_dismiss
                        ),
                        iconRes = if (anyRunning) R.drawable.aerix_ic_arrow_drop_down_rounded else null,
                        // With nothing running the button also clears the finished cards, so the
                        // pill does not outlive the install it was reporting.
                        onClick = {
                            if (!anyRunning) AerixInstallQueue.clearFinished()
                            onMinimize()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallTaskCard(task: AerixInstallTask, accent: Color) {
    val statusColor = when (task.status) {
        AerixInstallStatus.DONE -> AerixSurface.success
        AerixInstallStatus.FAILED -> AerixSurface.danger
        AerixInstallStatus.CANCELLED -> AerixSurface.textMuted
        else -> accent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AerixRadii.cardSmall))
            .background(AerixSurface.panel)
            .border(AerixSpacing.hairline, AerixSurface.borderSoft, RoundedCornerShape(AerixRadii.cardSmall))
            .padding(AerixSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.projectTitle,
                    color = AerixSurface.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = task.title,
                    color = AerixSurface.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (task.finished) {
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    contentColor = AerixSurface.textMuted,
                    onClick = { AerixInstallQueue.dismiss(task) }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_close),
                        contentDescription = stringResource(R.string.aerix_install_dismiss),
                        modifier = Modifier.padding(AerixSpacing.xs).size(14.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(AerixRadii.pill),
                    color = AerixSurface.danger.copy(alpha = 0.14f),
                    onClick = { AerixInstallQueue.cancel(task) }
                ) {
                    Text(
                        text = stringResource(R.string.aerix_install_cancel_task),
                        color = AerixSurface.danger,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = AerixSpacing.smPlus, vertical = 4.dp)
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = { if (task.finished) 1f else task.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(AerixRadii.pill)),
            color = statusColor,
            trackColor = AerixSurface.panelTrack
        )

        val line = when (task.status) {
            AerixInstallStatus.QUEUED -> stringResource(R.string.aerix_install_queued)
            AerixInstallStatus.RUNNING -> if (task.totalSteps == 1) {
                stringResource(R.string.aerix_install_started, task.title)
            } else {
                stringResource(
                    R.string.aerix_install_progress_summary,
                    task.completedSteps,
                    task.totalSteps
                ) + " · " + task.currentStep
            }
            AerixInstallStatus.DONE -> if (task.dependencySteps > 0) {
                stringResource(R.string.aerix_install_done_deps, task.title, task.dependencySteps)
            } else {
                stringResource(R.string.aerix_install_done, task.title)
            }
            AerixInstallStatus.CANCELLED -> stringResource(R.string.aerix_install_cancelled)
            AerixInstallStatus.FAILED -> task.failure.ifBlank { stringResource(R.string.aerix_install_failed) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            Text(
                text = line,
                color = if (task.finished && task.status != AerixInstallStatus.DONE) statusColor
                else AerixSurface.textSecondary,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (task.targetSummary.isNotBlank()) {
                Text(
                    text = stringResource(R.string.aerix_install_into, task.targetSummary),
                    color = AerixSurface.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * The minimized install: a pill that sits over whatever page is open. Tapping it brings the panel
 * back; the download itself never stopped.
 */
@Composable
internal fun AerixInstallPill(onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val accent = AerixThemeManager.currentAccent()
    val task = AerixInstallQueue.activeTasks.firstOrNull() ?: AerixInstallQueue.tasks.firstOrNull() ?: return
    val running = AerixInstallQueue.running
    val tint = when (task.status) {
        AerixInstallStatus.DONE -> AerixSurface.success
        AerixInstallStatus.FAILED, AerixInstallStatus.CANCELLED -> AerixSurface.danger
        else -> accent
    }
    val expandLabel = stringResource(R.string.aerix_install_expand)
    val label = when {
        running -> stringResource(
            R.string.aerix_install_progress_summary,
            task.completedSteps,
            task.totalSteps
        )
        task.status == AerixInstallStatus.DONE -> stringResource(R.string.aerix_install_done, task.title)
        else -> stringResource(R.string.aerix_install_failed)
    }
    Surface(
        modifier = modifier
            .widthIn(max = 260.dp)
            .semantics { contentDescription = expandLabel },
        shape = RoundedCornerShape(AerixRadii.pill),
        color = AerixSurface.panelRaised,
        border = BorderStroke(AerixSpacing.hairline, tint.copy(alpha = 0.6f)),
        onClick = onExpand
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = tint
                )
            } else {
                Icon(
                    painter = painterResource(
                        if (task.status == AerixInstallStatus.DONE) R.drawable.aerix_ic_check
                        else R.drawable.aerix_ic_warning_filled
                    ),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = task.projectTitle,
                    color = AerixSurface.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    color = tint,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
