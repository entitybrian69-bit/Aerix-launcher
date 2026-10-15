/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The window that opens before something is downloaded: the dependencies of the file on the left,
 * the instances the file can be installed into on the right. The instance that the launcher
 * detected is marked and picked, every dependency can be left out and the version of every
 * dependency can be chosen.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AssetDependency
import net.kdt.pojavlaunch.aerix.bridge.AssetDependencyType
import net.kdt.pojavlaunch.aerix.bridge.AssetFile
import net.kdt.pojavlaunch.aerix.bridge.AssetInstallTarget
import net.kdt.pojavlaunch.aerix.bridge.AssetProject
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.ModrinthMetaPill
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

/**
 * The window that opens when something is downloaded. It shows the dependencies of the file with
 * the version that can be picked for each of them, and the instances the file goes into.
 */
@Composable
fun AssetInstallDialog(
    project: AssetProject,
    file: AssetFile,
    targets: List<AssetInstallTarget>,
    dependencies: List<AssetDependency>,
    detectedId: String?,
    onDismiss: () -> Unit,
    onInstall: (List<AssetInstallTarget>, List<Pair<AssetDependency, AssetFile>>) -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    val fallbackId = detectedId ?: targets.firstOrNull()?.id
    var selectedIds by remember(targets, detectedId) { mutableStateOf(setOfNotNull(fallbackId)) }
    var included by remember(dependencies) {
        mutableStateOf(
            dependencies.filter { it.type == AssetDependencyType.REQUIRED }.map { it.projectId }.toSet()
        )
    }
    var chosenVersions by remember(dependencies) { mutableStateOf(emptyMap<String, AssetFile>()) }
    var versionMenu by remember { mutableStateOf<String?>(null) }

    val pickedTargets = targets.filter { it.id in selectedIds }
    val pickedDependencies = dependencies.filter { it.projectId in included }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 760.dp)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(AerixRadii.panel),
            color = AerixSurface.panelRaised,
            border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
        ) {
            Column(
                modifier = Modifier.padding(AerixSpacing.mdPlus),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Install ${project.title}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = file.name.ifBlank { file.fileName },
                            style = MaterialTheme.typography.labelSmall,
                            color = AerixSurface.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_close),
                            contentDescription = "Close",
                            tint = AerixSurface.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = AerixSurface.borderSoft)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                ) {
                    // Dependencies of the file
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                    ) {
                        Text(
                            text = "Dependencies",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        if (dependencies.isEmpty()) {
                            Text(
                                text = "This file has no dependencies.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AerixSurface.textSecondary
                            )
                        }
                        for (dependency in dependencies) {
                            val selectedVersion = chosenVersions[dependency.projectId]
                                ?: dependency.preferredVersion()
                            val checked = dependency.projectId in included
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AerixRadii.cardSmall),
                                color = AerixSurface.panel,
                                border = BorderStroke(
                                    if (checked) 1.5.dp else AerixSpacing.hairline,
                                    if (checked) accent.copy(alpha = 0.7f) else AerixSurface.borderSoft
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(AerixSpacing.smPlus),
                                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                                    ) {
                                        val icon = rememberRemoteBitmap(dependency.iconUrl, targetMinEdgePx = 96)
                                        AerixImageWell(
                                            image = icon,
                                            fallback = R.drawable.aerix_ic_extension_outlined,
                                            size = 40.dp,
                                            radius = AerixRadii.controlSmall
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = dependency.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = buildString {
                                                    append(if (dependency.isRequired) "Required" else "Optional")
                                                    if (dependency.downloads > 0) {
                                                        append("  \u2022  ")
                                                        append(formatDownloadCount(dependency.downloads))
                                                        append(" downloads")
                                                    }
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AerixSurface.textSecondary
                                            )
                                        }
                                    }
                                    if (dependency.description.isNotBlank()) {
                                        Text(
                                            text = dependency.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AerixSurface.textSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { value ->
                                                included = if (value) {
                                                    included + dependency.projectId
                                                } else {
                                                    included - dependency.projectId
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = accent,
                                                uncheckedColor = AerixSurface.textSecondary,
                                                checkmarkColor = AerixSurface.onAccent
                                            )
                                        )
                                        ModrinthMetaPill(
                                            text = if (dependency.isRequired) "Required" else "Optional",
                                            highlighted = checked
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (dependency.versions.isNotEmpty()) {
                                            Box {
                                                Surface(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(AerixRadii.controlSmall))
                                                        .clickable { versionMenu = dependency.projectId }
                                                        .border(
                                                            AerixSpacing.hairline,
                                                            AerixSurface.borderSoft,
                                                            RoundedCornerShape(AerixRadii.controlSmall)
                                                        )
                                                        .padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.xs),
                                                    color = AerixSurface.panelRaised
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                                                    ) {
                                                        Text(
                                                            text = selectedVersion?.versionNumber
                                                                ?: selectedVersion?.name
                                                                ?: "version",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color.White,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Icon(
                                                            painter = painterResource(R.drawable.aerix_ic_arrow_drop_down_rounded),
                                                            contentDescription = null,
                                                            tint = AerixSurface.textSecondary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = versionMenu == dependency.projectId,
                                                    onDismissRequest = { versionMenu = null }
                                                ) {
                                                    for (version in dependency.versions) {
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    text = version.versionNumber.ifBlank { version.name },
                                                                    color = AerixSurface.textPrimary
                                                                )
                                                            },
                                                            onClick = {
                                                                chosenVersions = chosenVersions +
                                                                    (dependency.projectId to version)
                                                                versionMenu = null
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Instances the file goes into
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                    ) {
                        Text(
                            text = "Install for which game versions?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        val detected = targets.firstOrNull { it.id == detectedId }
                        if (detected != null) {
                            Text(
                                text = buildString {
                                    append("Detected: ")
                                    append(detected.mcVersion ?: detected.versionId)
                                    detected.loader?.let {
                                        append(" \u2022 ")
                                        append(it.replaceFirstChar { c -> c.uppercase() })
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = accent
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                        ) {
                            items(targets, key = { it.id }) { target ->
                                val checked = target.id in selectedIds
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedIds = if (checked) {
                                                selectedIds - target.id
                                            } else {
                                                selectedIds + target.id
                                            }
                                        },
                                    shape = RoundedCornerShape(AerixRadii.cardSmall),
                                    color = AerixSurface.panel,
                                    border = BorderStroke(
                                        if (checked) 1.5.dp else AerixSpacing.hairline,
                                        if (checked) accent.copy(alpha = 0.7f) else AerixSurface.borderSoft
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(AerixSpacing.smPlus),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { value ->
                                                selectedIds = if (value) {
                                                    selectedIds + target.id
                                                } else {
                                                    selectedIds - target.id
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = accent,
                                                uncheckedColor = AerixSurface.textSecondary,
                                                checkmarkColor = AerixSurface.onAccent
                                            )
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(AerixRadii.controlSmall))
                                                .background(AerixSurface.panelRaised),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.aerix_ic_videogame_asset_outlined),
                                                contentDescription = null,
                                                tint = accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = target.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = buildList {
                                                    target.mcVersion?.let { add(it) }
                                                    target.loader?.let {
                                                        add(it.replaceFirstChar { c -> c.uppercase() })
                                                    }
                                                    target.loaderVersion?.let { add(it) }
                                                }.joinToString("  \u2022  "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AerixSurface.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (target.id == detectedId) {
                                            ModrinthMetaPill(text = "Detected", highlighted = true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = AerixSurface.borderSoft)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm, Alignment.End)
                ) {
                    AerixGhostButton(
                        text = "Cancel",
                        iconRes = R.drawable.aerix_ic_close,
                        onClick = onDismiss
                    )
                    AerixPrimaryButton(
                        text = "Install",
                        iconRes = R.drawable.aerix_ic_download_2_outlined,
                        enabled = pickedTargets.isNotEmpty(),
                        onClick = {
                            val pairs = pickedDependencies.mapNotNull { dependency ->
                                val version = chosenVersions[dependency.projectId]
                                    ?: dependency.preferredVersion()
                                    ?: return@mapNotNull null
                                dependency to version
                            }
                            onInstall(pickedTargets, pairs)
                        }
                    )
                }
            }
        }
    }
}
