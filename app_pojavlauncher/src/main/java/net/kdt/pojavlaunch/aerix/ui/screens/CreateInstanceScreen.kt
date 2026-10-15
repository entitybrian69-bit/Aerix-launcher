/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/game/SelectGameVersionScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/game/DownloadGameWithAddonScreen.kt
 *
 * Full-height landscape two-pane layout:
 *   - Compact top bar with Back button, live selection badge, Import .mrpack/.zip button, and Create Instance button.
 *   - Left pane (dedicated 100% to Minecraft Version selection): compact category filter pills + search field
 *     at the top, and a full-height scrollable LazyColumn of Minecraft versions so versions are always
 *     clearly visible and never cramped.
 *   - Right pane: Instance Name, Mod Loader 2x3 selector grid (Vanilla, Fabric, Quilt, Forge, NeoForge, OptiFine)
 *     with inline loader version list, and optional Custom JVM Arguments.
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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.JMinecraftVersionList
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.CreateLoader
import net.kdt.pojavlaunch.aerix.bridge.LoaderVersionItem
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceCreatorBridge
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.ModrinthCompactSearchField
import net.kdt.pojavlaunch.aerix.components.ModrinthMetaPill
import net.kdt.pojavlaunch.aerix.components.QuietEmptyState
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

private enum class VersionCategory(val label: String, val typeId: String?) {
    RELEASES("Releases", "release"),
    SNAPSHOTS("Snapshots", "snapshot"),
    BETA("Old Beta", "old_beta"),
    ALPHA("Old Alpha", "old_alpha"),
    ALL("All", null)
}

@Composable
fun CreateInstanceScreen(
    activity: FragmentActivity,
    onDone: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()

    var instanceName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(VersionCategory.RELEASES) }
    var versionSearch by remember { mutableStateOf("") }
    var refreshTick by remember { mutableIntStateOf(0) }
    val allVersions = remember(refreshTick) { PojavInstanceCreatorBridge.loadVersions() }

    val filteredVersions = remember(allVersions, selectedCategory, versionSearch) {
        allVersions.filter { v ->
            val matchesCat = selectedCategory.typeId == null || v.type == selectedCategory.typeId
            val matchesSearch = versionSearch.isBlank() || v.id.contains(versionSearch.trim(), ignoreCase = true)
            matchesCat && matchesSearch
        }
    }

    var selectedVersion by remember(allVersions) {
        mutableStateOf(
            allVersions.firstOrNull { it.type == "release" }?.id
                ?: allVersions.firstOrNull()?.id
                ?: "1.21.4"
        )
    }

    var selectedLoader by remember { mutableStateOf(CreateLoader.VANILLA) }
    var loaderVersions by remember { mutableStateOf<List<LoaderVersionItem>>(emptyList()) }
    var selectedLoaderVersion by remember { mutableStateOf<LoaderVersionItem?>(null) }
    var loadingLoaders by remember { mutableStateOf(false) }
    var customJvmArgs by remember { mutableStateOf("") }

    LaunchedEffect(selectedVersion, selectedLoader) {
        if (selectedLoader == CreateLoader.VANILLA) {
            loaderVersions = emptyList()
            selectedLoaderVersion = null
            loadingLoaders = false
        } else {
            loadingLoaders = true
            PojavInstanceCreatorBridge.fetchLoaderVersions(selectedVersion, selectedLoader) { list ->
                loaderVersions = list
                selectedLoaderVersion = list.firstOrNull { it.stable } ?: list.firstOrNull()
                loadingLoaders = false
            }
        }
    }

    val modpackPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            PojavInstanceCreatorBridge.importModpack(
                activity = activity,
                uri = uri,
                customName = instanceName.trim().takeIf { it.isNotEmpty() },
                onSuccess = onDone
            )
        }
    }

    val defaultInstanceName = remember(selectedVersion, selectedLoader) {
        if (selectedLoader == CreateLoader.VANILLA) {
            selectedVersion
        } else {
            "$selectedVersion (${selectedLoader.label})"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        // ====================================================================================
        // 1. Compact Single-Line Top Bar
        // ====================================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(AerixRadii.control),
                    color = AerixSurface.panel,
                    border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
                    onClick = onDone
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
                Text(
                    text = "Create New Instance",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )
                ModrinthMetaPill(
                    text = buildString {
                        append("MC ")
                        append(selectedVersion)
                        append(" \u2022 ")
                        append(selectedLoader.label)
                        selectedLoaderVersion?.let {
                            append(" ")
                            append(it.id)
                        }
                    },
                    highlighted = true
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                AerixGhostButton(
                    text = "Import .mrpack / .zip",
                    iconRes = R.drawable.aerix_ic_folder_outlined,
                    enabled = !PojavInstanceCreatorBridge.busy,
                    onClick = {
                        modpackPicker.launch(
                            arrayOf(
                                "application/zip",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    }
                )
                AerixPrimaryButton(
                    text = "Create Instance",
                    iconRes = R.drawable.aerix_ic_check,
                    enabled = !PojavInstanceCreatorBridge.busy && selectedVersion.isNotBlank(),
                    onClick = {
                        PojavInstanceCreatorBridge.createInstance(
                            activity = activity,
                            instanceName = instanceName.trim().ifEmpty { defaultInstanceName },
                            mcVersion = selectedVersion,
                            loader = selectedLoader,
                            loaderVersion = selectedLoaderVersion,
                            jvmArgs = customJvmArgs,
                            onSuccess = onDone
                        )
                    }
                )
            }
        }

        // Progress banner when installing a modloader or importing a modpack
        if (PojavInstanceCreatorBridge.busy || PojavInstanceCreatorBridge.statusMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AerixRadii.cardSmall),
                color = AerixSurface.panelRaised,
                border = BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.xsPlus),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = PojavInstanceCreatorBridge.statusMessage ?: "Working...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (PojavInstanceCreatorBridge.busy) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = accent,
                            trackColor = AerixSurface.panelTrack
                        )
                    }
                }
            }
        }

        // ====================================================================================
        // 2. Full-Height Two-Pane Landscape Content
        // ====================================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            // --------------------------------------------------------------------------------
            // LEFT PANE: Dedicated 100% to Minecraft Version Selection (Mirai SelectGameVersionScreen)
            // --------------------------------------------------------------------------------
            Surface(
                modifier = Modifier
                    .weight(1.12f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(AerixRadii.panel),
                color = AerixSurface.panel,
                border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AerixSpacing.smPlus),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.smCompact)
                ) {
                    // Category filter pills + Refresh button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VersionCategory.entries.forEach { category ->
                                val active = selectedCategory == category
                                Surface(
                                    shape = CircleShape,
                                    color = if (active) accent.copy(alpha = 0.22f) else AerixSurface.panelRaised,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (active) accent else AerixSurface.borderSoft
                                    ),
                                    onClick = { selectedCategory = category }
                                ) {
                                    Text(
                                        text = category.label,
                                        color = if (active) accent else AerixSurface.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            modifier = Modifier.size(30.dp),
                            onClick = { refreshTick++ }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_refresh),
                                contentDescription = "Refresh Versions",
                                tint = AerixSurface.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Compact 36dp search field
                    ModrinthCompactSearchField(
                        value = versionSearch,
                        onValueChange = { versionSearch = it },
                        placeholder = "Search Minecraft version (e.g. 1.21.4, 1.20.1, 1.16.5, 1.8.9)...",
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Full-height scrollable Minecraft Version list
                    if (filteredVersions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            QuietEmptyState(
                                iconRes = R.drawable.aerix_ic_package_2_outlined,
                                text = "No matching Minecraft versions found"
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredVersions, key = { it.id }) { ver ->
                                MinecraftVersionListItem(
                                    version = ver,
                                    selected = ver.id == selectedVersion,
                                    accent = accent,
                                    onClick = { selectedVersion = ver.id }
                                )
                            }
                        }
                    }
                }
            }

            // --------------------------------------------------------------------------------
            // RIGHT PANE: Instance Name, Mod Loader (2x3 Grid + Loader Version Picker), JVM Args
            // --------------------------------------------------------------------------------
            Surface(
                modifier = Modifier
                    .weight(0.88f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(AerixRadii.panel),
                color = AerixSurface.panel,
                border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(AerixSpacing.smPlus),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
                ) {
                    // 1. Instance Name
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "INSTANCE NAME",
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        OutlinedTextField(
                            value = instanceName,
                            onValueChange = { instanceName = it },
                            placeholder = {
                                Text(
                                    text = defaultInstanceName,
                                    color = AerixSurface.textMuted,
                                    fontSize = 13.sp
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = AerixSurface.borderSoft,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = accent
                            )
                        )
                    }

                    HorizontalDivider(color = AerixSurface.borderSoft)

                    // 2. Mod Loader 2x3 Grid
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "MOD LOADER",
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "For MC $selectedVersion",
                                color = AerixSurface.textSecondary,
                                fontSize = 11.sp
                            )
                        }

                        val loaders = CreateLoader.entries
                        loaders.chunked(3).forEach { rowLoaders ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowLoaders.forEach { loader ->
                                    val active = selectedLoader == loader
                                    LoaderChoiceChip(
                                        loader = loader,
                                        selected = active,
                                        accent = accent,
                                        onClick = { selectedLoader = loader },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Inline Loader Version Picker when a non-Vanilla loader is selected
                        if (selectedLoader != CreateLoader.VANILLA) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${selectedLoader.label.uppercase()} VERSION",
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            when {
                                loadingLoaders -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = accent,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Fetching ${selectedLoader.label} versions for $selectedVersion...",
                                            color = AerixSurface.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                loaderVersions.isEmpty() -> {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = AerixSurface.panelRaised,
                                        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
                                    ) {
                                        Text(
                                            text = "No ${selectedLoader.label} builds found for Minecraft $selectedVersion",
                                            color = AerixSurface.textSecondary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 170.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AerixSurface.panelRaised.copy(alpha = 0.6f))
                                            .border(
                                                AerixSpacing.hairline,
                                                AerixSurface.borderSoft,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .verticalScroll(rememberScrollState())
                                            .padding(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        loaderVersions.forEach { item ->
                                            val active = selectedLoaderVersion?.id == item.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (active) accent.copy(alpha = 0.16f)
                                                        else Color.Transparent
                                                    )
                                                    .clickable { selectedLoaderVersion = item }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    RadioButton(
                                                        selected = active,
                                                        onClick = { selectedLoaderVersion = item },
                                                        colors = RadioButtonDefaults.colors(
                                                            selectedColor = accent,
                                                            unselectedColor = AerixSurface.textSecondary
                                                        ),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = item.label,
                                                        color = if (active) Color.White else AerixSurface.textPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (item.stable) {
                                                    ModrinthMetaPill(text = "Stable", highlighted = active)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = AerixSurface.borderSoft)

                    // 3. Optional Custom JVM Arguments
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "CUSTOM JVM ARGUMENTS (OPTIONAL)",
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        OutlinedTextField(
                            value = customJvmArgs,
                            onValueChange = { customJvmArgs = it },
                            placeholder = {
                                Text(
                                    text = "Leave blank to follow global JVM arguments",
                                    color = AerixSurface.textMuted,
                                    fontSize = 12.sp
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = AerixSurface.borderSoft,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = accent
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinecraftVersionListItem(
    version: JMinecraftVersionList.Version,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val typeBadge = when (version.type) {
        "release" -> "Release"
        "snapshot" -> "Snapshot"
        "old_beta" -> "Beta"
        "old_alpha" -> "Alpha"
        else -> version.type ?: "Custom"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = if (selected) accent.copy(alpha = 0.16f) else AerixSurface.panelRaised.copy(alpha = 0.72f),
        border = BorderStroke(
            width = if (selected) 1.5.dp else AerixSpacing.hairline,
            color = if (selected) accent else AerixSurface.borderSoft
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = accent,
                        unselectedColor = AerixSurface.textSecondary
                    ),
                    modifier = Modifier.size(20.dp)
                )
                Image(
                    painter = painterResource(R.drawable.aerix_loader_vanilla),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = version.id,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ModrinthMetaPill(
                text = typeBadge,
                highlighted = selected || version.type == "release"
            )
        }
    }
}

@Composable
private fun LoaderChoiceChip(
    loader: CreateLoader,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val iconRes = when (loader) {
        CreateLoader.VANILLA -> R.drawable.aerix_loader_vanilla
        CreateLoader.FABRIC -> R.drawable.ic_fabric
        CreateLoader.QUILT -> R.drawable.ic_quilt
        CreateLoader.FORGE -> R.drawable.ic_forge
        CreateLoader.NEOFORGE -> R.drawable.ic_neoforge
        CreateLoader.OPTIFINE -> R.drawable.aerix_loader_optifine
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (selected) accent.copy(alpha = 0.18f) else AerixSurface.panelRaised,
        border = BorderStroke(
            width = if (selected) 1.5.dp else AerixSpacing.hairline,
            color = if (selected) accent else AerixSurface.borderSoft
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = loader.label,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = loader.label,
                color = if (selected) Color.White else AerixSurface.textPrimary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
