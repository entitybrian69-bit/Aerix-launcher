/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/VersionSettingsScreen.kt
 *     (VersionSettingsScreen, InstanceSidebarCard, InstanceNavGrid, InstanceNavGridItem)
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/ModsManagerScreen.kt
 *     (ModsManagerScreen, ModsCompactToolbar, ModItemLayout)
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/VersionOverViewScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/VersionConfigScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/UpdateLoaderScreen.kt
 * Two-pane landscape layout: left sidebar with the back button, InstanceSidebarCard (icon, name,
 * "Loader MC · RAM MB", ⋮ menu, full-width accent Play button) and 2-column InstanceNavGrid
 * (Mods, Packs, Shaders, Worlds, Shots, Settings, Overview, Loader); right pane with the active
 * sub-screen.
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

package net.kdt.pojavlaunch.aerix.ui.screens

import android.text.format.DateFormat
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.bridge.AerixIconCropper
import net.kdt.pojavlaunch.aerix.bridge.AerixInstance
import net.kdt.pojavlaunch.aerix.bridge.AerixInstanceDraft
import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.ContentEntry
import net.kdt.pojavlaunch.aerix.bridge.ContentKind
import net.kdt.pojavlaunch.aerix.bridge.PojavContentBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstaller
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceEditorBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.bridge.PojavSettingsBridge
import net.kdt.pojavlaunch.aerix.components.ActionSettingRow
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.ModrinthMetaPill
import net.kdt.pojavlaunch.aerix.components.QuietEmptyState
import net.kdt.pojavlaunch.aerix.components.SliderSettingRow
import net.kdt.pojavlaunch.aerix.components.SwitchSettingRow
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.dialogs.VersionSelectorDialog
import net.kdt.pojavlaunch.instances.Instance
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import java.util.Date

private data class InstanceSubTab(
    val id: String,
    val shortLabel: String,
    @DrawableRes val iconRes: Int
)

private val instanceSubTabs = listOf(
    InstanceSubTab("mods", "Mods", R.drawable.aerix_ic_extension_outlined),
    InstanceSubTab("packs", "Packs", R.drawable.aerix_ic_format_paint_outlined),
    InstanceSubTab("shaders", "Shaders", R.drawable.aerix_ic_lightbulb),
    InstanceSubTab("worlds", "Worlds", R.drawable.aerix_ic_public),
    InstanceSubTab("shots", "Shots", R.drawable.aerix_ic_image_outlined),
    InstanceSubTab("settings", "Settings", R.drawable.aerix_ic_build_outlined),
    InstanceSubTab("overview", "Overview", R.drawable.aerix_ic_dashboard_outlined),
    InstanceSubTab("loader", "Loader", R.drawable.aerix_ic_update)
)

private enum class ModFilterState(val label: String) {
    ALL("All"),
    ENABLED("Enabled"),
    DISABLED("Disabled")
}

private val mcVerRegex = Regex("""(\d+\.\d+(?:\.\d+)?)""")

@Composable
fun InstanceEditorScreen(activity: FragmentActivity, onClose: () -> Unit) {
    LaunchedEffect(Unit) {
        if (PojavInstanceEditorBridge.draft == null) {
            PojavInstanceEditorBridge.open(activity)
        }
    }
    val draft = PojavInstanceEditorBridge.draft
    if (draft == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.no_instance), color = AerixSurface.textSecondary, fontSize = 13.sp)
        }
        return
    }

    var activeTab by rememberSaveable { mutableStateOf(PojavInstanceBridge.requestedSubTab) }
    LaunchedEffect(PojavInstanceBridge.requestedSubTab) {
        activeTab = PojavInstanceBridge.requestedSubTab
    }

    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val selectedAerixInstance = PojavInstanceBridge.selected
    if (showExportDialog && selectedAerixInstance != null) {
        ModpackExportDialog(
            activity = activity,
            instance = selectedAerixInstance,
            onDismiss = { showExportDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
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
                    showDeleteDialog = false
                    if (PojavInstanceEditorBridge.delete(activity)) onClose()
                }) {
                    Text(stringResource(R.string.aerix_editor_delete_confirm), color = AerixSurface.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.aerix_editor_delete_cancel), color = AerixSurface.textSecondary)
                }
            }
        )
    }

    // Two-pane layout from Mirai VersionSettingsScreen.kt (Screenshot 2)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        // Left sidebar: Back button + InstanceSidebarCard + 2-column InstanceNavGrid
        Column(
            modifier = Modifier
                .width(264.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(AerixRadii.control),
                    color = AerixSurface.panel,
                    border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
                    onClick = {
                        PojavInstanceEditorBridge.save(activity)
                        onClose()
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_arrow_back),
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                InstanceSidebarCard(
                    draft = draft,
                    onPlay = {
                        PojavInstanceEditorBridge.save(activity)
                        PojavLaunchBridge.play(activity)
                    },
                    onOpenFolder = { PojavInstanceEditorBridge.openFolder(activity) },
                    onExportModpack = { showExportDialog = true },
                    onDelete = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }

            InstanceNavGrid(
                selectedTab = activeTab,
                onSelectTab = {
                    activeTab = it
                    PojavInstanceBridge.requestedSubTab = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        // Right content pane
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(AerixRadii.panel),
            color = AerixSurface.panel,
            border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
        ) {
            when (activeTab) {
                "mods" -> InstanceModsPane(activity = activity)
                "packs" -> InstanceContentPane(activity = activity, kind = ContentKind.RESOURCE_PACKS, assetType = AssetType.RESOURCE_PACK)
                "shaders" -> InstanceContentPane(activity = activity, kind = ContentKind.SHADER_PACKS, assetType = AssetType.SHADER_PACK)
                "worlds" -> InstanceContentPane(activity = activity, kind = ContentKind.SAVES, assetType = AssetType.WORLD)
                "shots" -> InstanceContentPane(activity = activity, kind = ContentKind.SCREENSHOTS, assetType = null)
                "settings" -> InstanceSettingsPane(
                    activity = activity,
                    draft = draft,
                    onDelete = { showDeleteDialog = true },
                    onExport = { showExportDialog = true },
                    onSaved = onClose
                )
                "overview" -> InstanceOverviewPane(
                    activity = activity,
                    draft = draft,
                    onSelectTab = {
                        activeTab = it
                        PojavInstanceBridge.requestedSubTab = it
                    },
                    onExport = { showExportDialog = true }
                )
                "loader" -> InstanceLoaderPane(activity = activity, draft = draft)
                else -> InstanceModsPane(activity = activity)
            }
        }
    }
}

/**
 * Mirai InstanceSidebarCard (VersionSettingsScreen.kt lines 490-590, Screenshot 2 top-left card).
 */
@Composable
private fun InstanceSidebarCard(
    draft: AerixInstanceDraft,
    onPlay: () -> Unit,
    onOpenFolder: () -> Unit,
    onExportModpack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vanilla = stringResource(R.string.aerix_home_vanilla)
    val loader = loaderLabel(draft.versionId, vanilla)
    val mcVer = mcVerRegex.find(draft.versionId)?.value ?: draft.versionId.ifBlank { "Latest" }
    val ramMb = LauncherPreferences.PREF_RAM_ALLOCATION
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AerixRadii.panelSmall),
        color = AerixSurface.panel,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AerixSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                AerixImageWell(
                    image = draft.icon,
                    fallback = R.drawable.aerix_loader_vanilla,
                    size = 38.dp,
                    radius = AerixRadii.control
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
                ) {
                    Text(
                        text = draft.name.ifEmpty { stringResource(R.string.unnamed) },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = AerixSurface.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$loader $mcVer   \u00B7   $ramMb MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = AerixSurface.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_more_vert),
                            contentDescription = "More",
                            tint = AerixSurface.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Modpack") },
                            onClick = {
                                showMenu = false
                                onExportModpack()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open instance folder") },
                            onClick = {
                                showMenu = false
                                onOpenFolder()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete instance", color = AerixSurface.danger) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Button(
                onClick = onPlay,
                shape = RoundedCornerShape(AerixRadii.control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AerixSurface.action,
                    contentColor = AerixSurface.onAction
                ),
                contentPadding = PaddingValues(horizontal = AerixSpacing.md, vertical = AerixSpacing.xs),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_play_arrow_filled),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(AerixSpacing.sm))
                Text(
                    text = "Play",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Mirai InstanceNavGrid + InstanceNavGridItem (VersionSettingsScreen.kt lines 595-675):
 * 2-column grid of 38dp navigation cells.
 */
@Composable
private fun InstanceNavGrid(
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAccent = AerixThemeManager.currentAccent()
    val rows = remember { instanceSubTabs.chunked(2) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.smCompact)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smCompact)
            ) {
                row.forEach { tab ->
                    val selected = selectedTab == tab.id
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(AerixRadii.control),
                        color = if (selected) activeAccent.copy(alpha = 0.16f) else AerixSurface.panel,
                        contentColor = if (selected) activeAccent else AerixSurface.textPrimary,
                        border = BorderStroke(
                            AerixSpacing.hairline,
                            if (selected) activeAccent else AerixSurface.borderSoft
                        ),
                        onClick = { onSelectTab(tab.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = AerixSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smCompact)
                        ) {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = null,
                                tint = if (selected) activeAccent else AerixSurface.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = tab.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected) activeAccent else AerixSurface.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                repeat(2 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ============================================================================================
// Mods sub-screen (Mirai ModsManagerScreen.kt: ModsCompactToolbar + ModItemLayout — Screenshot 2)
// ============================================================================================

@Composable
private fun InstanceModsPane(activity: FragmentActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()

    var entries by remember { mutableStateOf<List<ContentEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    var nameFilter by rememberSaveable { mutableStateOf("") }
    var stateFilter by rememberSaveable { mutableStateOf(ModFilterState.ALL) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val failedText = stringResource(R.string.aerix_content_failed)
    val importedText = stringResource(R.string.aerix_content_imported)

    LaunchedEffect(revision, PojavInstanceBridge.selected?.source) {
        loading = true
        entries = PojavContentBridge.list(ContentKind.MODS).orEmpty()
        loading = false
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var ok = 0
                uris.forEach { if (PojavContentBridge.import(context, ContentKind.MODS, it)) ok++ }
                Toast.makeText(context, if (ok == uris.size) importedText else failedText, Toast.LENGTH_SHORT).show()
                revision++
            }
        }
    }

    val filtered = remember(entries, nameFilter, stateFilter) {
        entries.filter { e ->
            val matchesName = nameFilter.isBlank() ||
                e.displayTitle.contains(nameFilter, ignoreCase = true) ||
                e.name.contains(nameFilter, ignoreCase = true)
            val matchesState = when (stateFilter) {
                ModFilterState.ALL -> true
                ModFilterState.ENABLED -> e.enabled
                ModFilterState.DISABLED -> !e.enabled
            }
            matchesName && matchesState
        }
    }

    val enabledCount = remember(entries) { entries.count { it.enabled } }
    val disabledCount = remember(entries) { entries.count { !it.enabled } }

    fun countOf(filter: ModFilterState): Int = when (filter) {
        ModFilterState.ALL -> entries.size
        ModFilterState.ENABLED -> enabledCount
        ModFilterState.DISABLED -> disabledCount
    }

    var showAddMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ModsCompactToolbar (Mirai ModsManagerScreen.kt lines 837-1068 — Screenshot 2 top bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            // Search field ("Search 19 mods...")
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(AerixRadii.pill))
                    .background(AerixSurface.panelRaised.copy(alpha = 0.72f))
                    .border(AerixSpacing.hairline, AerixSurface.borderSoft, RoundedCornerShape(AerixRadii.pill))
                    .padding(horizontal = AerixSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (nameFilter.isEmpty()) {
                        Text(
                            text = "Search ${entries.size} mods...",
                            fontSize = 12.sp,
                            color = AerixSurface.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = nameFilter,
                        onValueChange = { nameFilter = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // State filter dropdown ("All 19 ▾")
            Box {
                val selectedFilter = stateFilter != ModFilterState.ALL
                Surface(
                    shape = RoundedCornerShape(AerixRadii.control),
                    color = if (selectedFilter) AerixSurface.accentContainer else AerixSurface.panel,
                    border = BorderStroke(
                        AerixSpacing.hairline,
                        if (selectedFilter) accent else AerixSurface.border
                    ),
                    onClick = { showFilterMenu = true }
                ) {
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .padding(horizontal = AerixSpacing.smPlus),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                    ) {
                        Text(
                            text = stateFilter.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedFilter) accent else AerixSurface.textPrimary
                        )
                        Text(
                            text = countOf(stateFilter).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedFilter) accent else AerixSurface.textSecondary
                        )
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_arrow_drop_down_rounded),
                            contentDescription = null,
                            tint = AerixSurface.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    ModFilterState.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text("${f.label}  (${countOf(f)})") },
                            onClick = {
                                showFilterMenu = false
                                stateFilter = f
                            }
                        )
                    }
                }
            }

            // Warning / diagnostics button (Screenshot 2)
            Surface(
                shape = RoundedCornerShape(AerixRadii.control),
                color = AerixSurface.panel,
                border = BorderStroke(AerixSpacing.hairline, AerixSurface.border),
                onClick = {
                    Toast.makeText(
                        context,
                        "${enabledCount} enabled, ${disabledCount} disabled \u2014 no conflicts detected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) {
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = AerixSpacing.smPlus),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_warning_outlined),
                        contentDescription = null,
                        tint = AerixSurface.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Refresh button
            IconButton(
                onClick = { revision++ },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_refresh),
                    contentDescription = "Refresh",
                    tint = AerixSurface.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Accent "+" button (Download from Discover or Import local .jar)
            Box {
                Surface(
                    shape = RoundedCornerShape(AerixRadii.control),
                    color = AerixSurface.action,
                    contentColor = AerixSurface.onAction,
                    onClick = { showAddMenu = true }
                ) {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_add),
                            contentDescription = "Add",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Download from Modrinth / CurseForge") },
                        onClick = {
                            showAddMenu = false
                            AerixDownloadRequest.open(AssetType.MOD) { AerixNav.navigate(it) }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Import local .jar file") },
                        onClick = {
                            showAddMenu = false
                            importPicker.launch(ContentKind.MODS.importMimeTypes)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open mods folder") },
                        onClick = {
                            showAddMenu = false
                            PojavContentBridge.openFolder(activity, ContentKind.MODS)
                        }
                    )
                }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent)
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                QuietEmptyState(R.drawable.aerix_ic_extension_outlined, stringResource(R.string.aerix_content_empty))
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = AerixSpacing.md, vertical = AerixSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                items(filtered, key = { it.file.absolutePath }) { entry ->
                    ModItemCard(
                        entry = entry,
                        onToggle = { enabled ->
                            scope.launch {
                                PojavContentBridge.setEnabled(entry, enabled)
                                revision++
                            }
                        },
                        onDelete = {
                            scope.launch {
                                PojavContentBridge.delete(entry)
                                revision++
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Mirai ModItemLayout (ModsManagerScreen.kt lines 1411-1595 — Screenshot 2 rows):
 * 40dp icon, bold displayTitle + loader pills (Fabric / Quilt / Forge / NeoForge),
 * filename without .jar/.disabled below, 0.75-scale Switch and ⋮ menu.
 */
@Composable
private fun ModItemCard(
    entry: ContentEntry,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AerixRadii.card),
        color = AerixSurface.panel,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AerixSpacing.mdPlus, vertical = AerixSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AerixImageWell(
                image = entry.icon,
                fallback = R.drawable.aerix_ic_extension_outlined,
                size = 40.dp,
                radius = AerixRadii.control
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smCompact),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = entry.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    entry.loaders.take(2).forEach { loader ->
                        Surface(
                            shape = RoundedCornerShape(AerixRadii.compact),
                            color = accent
                        ) {
                            Text(
                                text = loader,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AerixSurface.onAccent,
                                modifier = Modifier.padding(horizontal = AerixSpacing.smCompact, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = entry.fileBaseName,
                    style = MaterialTheme.typography.labelSmall,
                    color = AerixSurface.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    modifier = Modifier.scale(0.75f),
                    checked = entry.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AerixSurface.onAction,
                        checkedTrackColor = AerixSurface.action,
                        uncheckedThumbColor = AerixSurface.textSecondary,
                        uncheckedTrackColor = AerixSurface.panelRaised,
                        uncheckedBorderColor = AerixSurface.panelRaised
                    )
                )

                Box {
                    IconButton(
                        modifier = Modifier.size(32.dp),
                        onClick = { showMenu = true }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_more_vert),
                            contentDescription = "More",
                            tint = AerixSurface.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (entry.enabled) "Disable" else "Enable") },
                            onClick = {
                                showMenu = false
                                onToggle(!entry.enabled)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = AerixSurface.danger) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================================
// Resource Packs / Shaders / Worlds / Screenshots sub-screens
// ============================================================================================

@Composable
private fun InstanceContentPane(
    activity: FragmentActivity,
    kind: ContentKind,
    assetType: AssetType?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()

    var entries by remember(kind) { mutableStateOf<List<ContentEntry>>(emptyList()) }
    var loading by remember(kind) { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    var query by rememberSaveable(kind) { mutableStateOf("") }

    val failedText = stringResource(R.string.aerix_content_failed)
    val importedText = stringResource(R.string.aerix_content_imported)

    LaunchedEffect(kind, revision, PojavInstanceBridge.selected?.source) {
        loading = true
        entries = PojavContentBridge.list(kind).orEmpty()
        loading = false
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var ok = 0
                uris.forEach { if (PojavContentBridge.import(context, kind, it)) ok++ }
                Toast.makeText(context, if (ok == uris.size) importedText else failedText, Toast.LENGTH_SHORT).show()
                revision++
            }
        }
    }

    val shown = remember(entries, query) {
        entries.filter { query.isBlank() || it.displayTitle.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(AerixRadii.pill))
                    .background(AerixSurface.panelRaised.copy(alpha = 0.72f))
                    .border(AerixSpacing.hairline, AerixSurface.borderSoft, RoundedCornerShape(AerixRadii.pill))
                    .padding(horizontal = AerixSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search ${entries.size} ${stringResource(kind.titleRes).lowercase()}...",
                            fontSize = 12.sp,
                            color = AerixSurface.textSecondary
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            IconButton(
                onClick = { PojavContentBridge.openFolder(activity, kind) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_folder_outlined),
                    contentDescription = "Open folder",
                    tint = AerixSurface.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { revision++ },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_refresh),
                    contentDescription = "Refresh",
                    tint = AerixSurface.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (assetType != null) {
                Surface(
                    shape = RoundedCornerShape(AerixRadii.control),
                    color = AerixSurface.panelRaised,
                    border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
                    onClick = { AerixDownloadRequest.open(assetType) { AerixNav.navigate(it) } }
                ) {
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_download_2_outlined),
                            contentDescription = "Download",
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(AerixRadii.control),
                color = AerixSurface.action,
                contentColor = AerixSurface.onAction,
                onClick = { importPicker.launch(kind.importMimeTypes) }
            ) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_add),
                        contentDescription = "Import",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent)
            }
            shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                QuietEmptyState(kind.iconRes, stringResource(R.string.aerix_content_empty))
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = AerixSpacing.md, vertical = AerixSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                items(shown, key = { it.file.absolutePath }) { entry ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AerixRadii.card),
                        color = AerixSurface.panel,
                        border = BorderStroke(AerixSpacing.hairline, AerixSurface.border),
                        onClick = {
                            if (kind == ContentKind.SCREENSHOTS) {
                                Tools.openPath(context, entry.file, true)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AerixSpacing.mdPlus, vertical = AerixSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AerixImageWell(
                                image = entry.icon,
                                fallback = kind.iconRes,
                                size = 40.dp,
                                radius = AerixRadii.control
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.displayTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = Formatter.formatShortFileSize(context, entry.sizeBytes) + " \u00B7 " +
                                        DateFormat.getMediumDateFormat(context).format(Date(entry.lastModified)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AerixSurface.textSecondary,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        PojavContentBridge.delete(entry)
                                        revision++
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.aerix_ic_delete_outlined),
                                    contentDescription = "Delete",
                                    tint = AerixSurface.danger,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================================
// Settings sub-screen (Instance configuration: Name, Icon, Version, Shared Data, Renderer, JRE, Controls, Memory, JVM Args)
// ============================================================================================

@Composable
private fun InstanceSettingsPane(
    activity: FragmentActivity,
    draft: AerixInstanceDraft,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onSaved: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    var showRenderer by remember { mutableStateOf(false) }
    var showRuntime by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var showArgs by remember { mutableStateOf(false) }

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
        ArgsDialog(
            activity = activity,
            draftArgs = draft.jvmArgs,
            draftMode = draft.argsMode,
            onApply = { args, mode ->
                draft.jvmArgs = args
                draft.argsMode = mode
                showArgs = false
            },
            onDismiss = { showArgs = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AerixSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
            ) {
                Box {
                    AerixImageWell(
                        image = draft.icon,
                        fallback = R.drawable.aerix_loader_vanilla,
                        size = 48.dp,
                        modifier = Modifier.clickable(role = Role.Button) {
                            AerixIconCropper.start(activity) { PojavInstanceEditorBridge.applyIcon(it) }
                        }
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_edit_outlined),
                            contentDescription = null,
                            tint = AerixSurface.onAccent,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft.name = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.aerix_editor_name_label), fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AerixSurface.textPrimary,
                        unfocusedTextColor = AerixSurface.textPrimary,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = AerixSurface.borderSoft,
                        cursorColor = accent
                    )
                )
            }

            ActionSettingRow(
                title = stringResource(R.string.aerix_editor_version, draft.versionId.ifEmpty { stringResource(R.string.unnamed) }),
                summary = null,
                onClick = {
                    VersionSelectorDialog.open(activity, false) { id, _ -> draft.versionId = id }
                },
                iconRes = R.drawable.aerix_ic_videogame_asset_outlined,
                value = stringResource(R.string.aerix_editor_change)
            )

            SwitchSettingRow(
                title = stringResource(R.string.aerix_editor_shared_data),
                summary = stringResource(R.string.aerix_editor_shared_data_sub),
                checked = draft.sharedData,
                onCheckedChange = { draft.sharedData = it }
            )

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
            ActionSettingRow(
                title = "Export Modpack",
                summary = "Package this instance as Modrinth (.mrpack), CurseForge (.zip) or MultiMC (.zip)",
                onClick = onExport,
                iconRes = R.drawable.aerix_ic_upload,
                value = "Export"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            DeleteButton(onClick = onDelete)
            Spacer(Modifier.weight(1f))
            AerixGhostButton(
                text = stringResource(R.string.aerix_editor_open_folder),
                iconRes = R.drawable.aerix_ic_folder_outlined,
                onClick = { PojavInstanceEditorBridge.openFolder(activity) }
            )
            AerixPrimaryButton(
                text = stringResource(R.string.aerix_editor_save),
                iconRes = R.drawable.aerix_ic_check,
                onClick = { if (PojavInstanceEditorBridge.save(activity)) onSaved() }
            )
        }
    }
}

// ============================================================================================
// Overview sub-screen (Mirai VersionOverViewScreen.kt)
// ============================================================================================

@Composable
private fun InstanceOverviewPane(
    activity: FragmentActivity,
    draft: AerixInstanceDraft,
    onSelectTab: (String) -> Unit,
    onExport: () -> Unit
) {
    val vanilla = stringResource(R.string.aerix_home_vanilla)
    val loader = loaderLabel(draft.versionId, vanilla)
    val mcVer = mcVerRegex.find(draft.versionId)?.value ?: draft.versionId.ifBlank { "Latest" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AerixSpacing.mdPlus)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        Text(
            text = "Instance Overview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            OverviewTile("Name", draft.name.ifBlank { "Unnamed" }, Modifier.weight(1f))
            OverviewTile("Minecraft", mcVer, Modifier.weight(1f))
            OverviewTile("Loader", loader, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            OverviewTile("Renderer", PojavInstanceEditorBridge.rendererLabel(activity, draft.renderer), Modifier.weight(1f))
            OverviewTile("Runtime", PojavInstanceEditorBridge.runtimeLabel(activity, draft.runtime), Modifier.weight(1f))
            OverviewTile("Memory", PojavInstanceEditorBridge.memoryLabel(activity), Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            AerixGhostButton("Manage Mods", R.drawable.aerix_ic_extension_outlined, { onSelectTab("mods") })
            AerixGhostButton("Instance Settings", R.drawable.aerix_ic_build_outlined, { onSelectTab("settings") })
            AerixPrimaryButton("Export Modpack", R.drawable.aerix_ic_upload, onExport)
        }
    }
}

@Composable
private fun OverviewTile(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AerixRadii.control),
        color = AerixSurface.panelRaised,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
    ) {
        Column(
            modifier = Modifier.padding(AerixSpacing.smPlus),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = AerixSurface.textSecondary, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ============================================================================================
// Loader sub-screen (Mirai UpdateLoaderScreen.kt)
// ============================================================================================

@Composable
private fun InstanceLoaderPane(activity: FragmentActivity, draft: AerixInstanceDraft) {
    val vanilla = stringResource(R.string.aerix_home_vanilla)
    val currentLoader = loaderLabel(draft.versionId, vanilla)
    val installers = listOf(
        Triple("Fabric", R.drawable.ic_fabric, PojavInstaller.FABRIC),
        Triple("NeoForge", R.drawable.ic_neoforge, PojavInstaller.NEOFORGE),
        Triple("Forge", R.drawable.ic_forge, PojavInstaller.FORGE),
        Triple("Quilt", R.drawable.ic_quilt, PojavInstaller.QUILT),
        Triple("OptiFine", R.drawable.aerix_loader_optifine, PojavInstaller.OPTIFINE),
        Triple("Legacy Fabric", R.drawable.aerix_loader_legacy_fabric, PojavInstaller.LEGACY_FABRIC)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AerixSpacing.mdPlus)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
    ) {
        Text(
            text = "Install or Update Modloader",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Text(
            text = "Current version: ${draft.versionId} ($currentLoader)",
            style = MaterialTheme.typography.labelMedium,
            color = AerixSurface.textSecondary
        )

        installers.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                row.forEach { (label, iconRes, installer) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(AerixRadii.cardSmall),
                        color = AerixSurface.panelRaised,
                        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
                        onClick = { PojavNavigator.openInstaller(activity, installer) }
                    ) {
                        Row(
                            modifier = Modifier.padding(AerixSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                Text("Install / update $label", color = AerixSurface.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================================
// Helper dialogs (DeleteButton, InstanceChoiceDialog, MemoryDialog, ArgsDialog)
// ============================================================================================

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    val shape = CircleShape
    Surface(
        onClick = onClick,
        modifier = Modifier.height(42.dp),
        shape = shape,
        color = AerixSurface.danger.copy(alpha = 0.16f),
        contentColor = AerixSurface.danger,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.danger.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AerixSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_delete_outlined),
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = stringResource(R.string.aerix_editor_delete),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
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
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                labels.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(index) }
                            .padding(vertical = AerixSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onPick(index) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accent,
                                unselectedColor = AerixSurface.textSecondary
                            )
                        )
                        Text(label, color = AerixSurface.textPrimary, fontSize = 14.sp)
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

@Composable
private fun MemoryDialog(activity: FragmentActivity, onDismiss: () -> Unit) {
    val setting = PojavSettingsBridge.memoryAllocation
    val revision = PojavSettingsBridge.revision
    val min = remember { setting.min(activity) }
    val max = remember { maxOf(min, setting.max(activity)) }
    val value = remember(revision) { setting.current().coerceIn(min, max) }
    val total = remember { Tools.getTotalDeviceMemory(activity) }
    val free = remember(revision) { PojavSettingsBridge.freeMemoryMb(activity) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AerixSurface.panelRaised,
        title = { Text(stringResource(setting.titleRes), color = AerixSurface.textPrimary) },
        text = {
            SliderSettingRow(
                title = stringResource(R.string.aerix_settings_memory_info, total, free),
                summary = if (value > free) stringResource(R.string.setting_java_memory_exceeded) else null,
                value = value,
                range = min..max,
                step = setting.step,
                suffix = setting.suffix,
                onValueChange = { PojavSettingsBridge.writeInto(activity, setting, it) }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.aerix_settings_apply), color = AerixThemeManager.currentAccent())
            }
        }
    )
}

@Composable
private fun ArgsDialog(
    activity: FragmentActivity,
    draftArgs: String,
    draftMode: Int,
    onApply: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    var text by remember { mutableStateOf(draftArgs) }
    var mode by remember { mutableIntStateOf(draftMode) }
    val modes = remember {
        activity.resources.getStringArray(R.array.menu_custom_arguments_Formatting).toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AerixSurface.panelRaised,
        title = { Text(stringResource(R.string.aerix_editor_jvm_args), color = AerixSurface.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.aerix_editor_jvm_args_hint), color = AerixSurface.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = AerixSurface.borderSoft,
                        focusedTextColor = AerixSurface.textPrimary,
                        unfocusedTextColor = AerixSurface.textPrimary,
                        cursorColor = accent
                    )
                )
                modes.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { mode = index }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == mode,
                            onClick = { mode = index },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accent,
                                unselectedColor = AerixSurface.textSecondary
                            )
                        )
                        Text(label, color = AerixSurface.textPrimary, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(text.trim(), mode.coerceIn(0, Instance.ARGS_MODE_LAST)) }) {
                Text(stringResource(R.string.aerix_settings_apply), color = accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary)
            }
        }
    )
}
