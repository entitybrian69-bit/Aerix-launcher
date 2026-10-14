/*
 * Aerix Launcher UI layer for PojavLauncher.
 * "Create New Instance" page. Loader tiles, Minecraft version list and the install action follow
 * mirai-launcher-:
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/game/DownloadGameWithAddonScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/game/DownloadGameScreen.kt
 * Every install is executed by net.kdt.pojavlaunch.aerix.bridge.PojavCreateInstanceBridge, which calls the
 * PojavLauncher backend paths of the installer fragments (see that file's header).
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

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixIconCropper
import net.kdt.pojavlaunch.aerix.bridge.AerixLoader
import net.kdt.pojavlaunch.aerix.bridge.AerixLoaderVersion
import net.kdt.pojavlaunch.aerix.bridge.AerixVersionType
import net.kdt.pojavlaunch.aerix.bridge.PojavCreateInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstaller
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.GlassPanel
import net.kdt.pojavlaunch.aerix.components.QuietEmptyState
import net.kdt.pojavlaunch.aerix.components.SectionLabel
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.theme.AerixLayoutDefaults
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

/** Version tabs, the same grouping Pojav's VersionListAdapter builds. */
private val versionTabs = listOf(
    AerixVersionType.INSTALLED to R.string.mcl_setting_veroption_installed,
    AerixVersionType.RELEASE to R.string.mcl_setting_veroption_release,
    AerixVersionType.SNAPSHOT to R.string.mcl_setting_veroption_snapshot,
    AerixVersionType.OLD_BETA to R.string.mcl_setting_veroption_oldbeta,
    AerixVersionType.OLD_ALPHA to R.string.mcl_setting_veroption_oldalpha
)

@Composable
fun CreateInstanceScreen(activity: FragmentActivity, onClose: () -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val revision = PojavCreateInstanceBridge.versionTableRevision
    val versions = remember(revision) { PojavCreateInstanceBridge.gameVersions() }

    var tab by rememberSaveable { mutableIntStateOf(1) }
    var filter by rememberSaveable { mutableStateOf("") }
    var selectedVersion by remember { mutableStateOf<String?>(null) }
    var loader by remember { mutableStateOf(AerixLoader.VANILLA) }
    var onlyStable by remember { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var nameEdited by rememberSaveable { mutableStateOf(false) }
    var icon by remember { mutableStateOf<Bitmap?>(null) }
    var loaderVersions by remember { mutableStateOf<List<AerixLoaderVersion>?>(null) }
    var loaderBuild by remember { mutableStateOf<AerixLoaderVersion?>(null) }
    var loadingBuilds by remember { mutableStateOf(false) }
    var buildsFailed by remember { mutableStateOf(false) }

    // Newest release preselected, exactly like the version the Pojav spinner offers first.
    LaunchedEffect(versions) {
        if (selectedVersion == null) {
            selectedVersion = versions.firstOrNull { it.type == AerixVersionType.RELEASE }?.id
        }
    }

    // Loader builds for the picked loader and version; cancelled automatically when either changes.
    LaunchedEffect(loader, selectedVersion) {
        loaderBuild = null
        buildsFailed = false
        if (!loader.usesGameVersion) {
            loadingBuilds = true
            loaderVersions = withContext(Dispatchers.IO) {
                runCatching { PojavCreateInstanceBridge.loadLoaderVersions(loader, null) }
                    .onFailure { buildsFailed = true }
                    .getOrDefault(emptyList())
            }
            loadingBuilds = false
        } else if (selectedVersion != null) {
            loadingBuilds = true
            loaderVersions = withContext(Dispatchers.IO) {
                runCatching { PojavCreateInstanceBridge.loadLoaderVersions(loader, selectedVersion) }
                    .onFailure { buildsFailed = true }
                    .getOrDefault(emptyList())
            }
            loadingBuilds = false
        } else {
            loaderVersions = null
        }
    }

    val visibleBuilds = remember(loaderVersions, onlyStable) {
        loaderVersions?.filter { !onlyStable || it.stable } ?: emptyList()
    }
    LaunchedEffect(visibleBuilds) {
        loaderBuild = visibleBuilds.firstOrNull()
    }

    val suggestedName = remember(loader, selectedVersion, loaderBuild) {
        PojavCreateInstanceBridge.suggestedName(loader, selectedVersion, loaderBuild)
    }
    LaunchedEffect(suggestedName) {
        if (!nameEdited) name = suggestedName
    }

    val canInstall = !PojavCreateInstanceBridge.installing &&
        selectedVersion != null &&
        (!loader.usesGameVersion || loaderBuild != null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AerixLayoutDefaults.pagePadding()),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_arrow_back),
                    contentDescription = stringResource(R.string.aerix_create_close),
                    tint = AerixSurface.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.aerix_create_title),
                    color = AerixSurface.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = stringResource(R.string.aerix_create_subtitle),
                    color = AerixSurface.textSecondary,
                    fontSize = 12.sp
                )
            }
            AerixGhostButton(
                text = stringResource(R.string.aerix_create_classic),
                iconRes = R.drawable.aerix_ic_add,
                onClick = { PojavNavigator.openInstaller(activity, PojavInstaller.PROFILE_TYPE) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            // ---- version column ------------------------------------------------------------------
            GlassPanel(modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = AerixSpacing.mdPlus) {
                SectionLabel(title = stringResource(R.string.aerix_create_instance_info))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                    AerixImageWell(
                        image = icon?.asImageBitmap(),
                        fallback = R.drawable.aerix_loader_vanilla,
                        size = 38.dp,
                        modifier = Modifier.clickable(role = Role.Button) {
                            AerixIconCropper.start(activity) { icon = it }
                        }
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameEdited = it.isNotEmpty()
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.aerix_create_name_hint), color = AerixSurface.textMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AerixSurface.textPrimary,
                            unfocusedTextColor = AerixSurface.textPrimary,
                            focusedBorderColor = accent,
                            unfocusedBorderColor = AerixSurface.borderSoft,
                            cursorColor = accent
                        )
                    )
                }

                Spacer(Modifier.height(AerixSpacing.xs))
                SectionLabel(title = stringResource(R.string.aerix_create_version_heading))
                VersionTabs(selected = tab, onSelect = { tab = it })
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_search),
                            contentDescription = null,
                            tint = AerixSurface.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    placeholder = { Text(stringResource(R.string.aerix_create_filter_hint), color = AerixSurface.textMuted, fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AerixSurface.textPrimary,
                        unfocusedTextColor = AerixSurface.textPrimary,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = AerixSurface.borderSoft,
                        cursorColor = accent
                    )
                )

                val tabType = versionTabs[tab].first
                val rows = remember(versions, tabType, filter) {
                    versions.filter { it.type == tabType }
                        .filter { filter.isBlank() || it.id.contains(filter.trim(), ignoreCase = true) }
                        .map { it.id }
                }
                if (rows.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        QuietEmptyState(
                            iconRes = R.drawable.aerix_ic_package_2_outlined,
                            text = stringResource(
                                if (filter.isBlank() && versions.isEmpty()) R.string.aerix_create_versions_empty
                                else R.string.aerix_create_filter_empty
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.smCompact)
                    ) {
                        items(rows, key = { it }) { id ->
                            VersionRow(
                                label = if (tabType == AerixVersionType.INSTALLED) id
                                else stringResource(R.string.aerix_create_loader_version, "Minecraft", id),
                                selected = id == selectedVersion,
                                onClick = { selectedVersion = id }
                            )
                        }
                    }
                }
            }

            // ---- loader column -------------------------------------------------------------------
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
            ) {
                GlassPanel(modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = AerixSpacing.mdPlus) {
                    SectionLabel(title = stringResource(R.string.aerix_create_loader_heading))
                    val tiles = AerixLoader.entries.toList()
                    tiles.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smCompact)
                        ) {
                            row.forEach { entry ->
                                LoaderTile(
                                    loader = entry,
                                    selected = entry == loader,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        loader = entry
                                        onlyStable = true
                                    }
                                )
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(AerixSpacing.smCompact))
                    }

                    if (loader.usesGameVersion) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                            Text(
                                text = if (loaderBuild != null) {
                                    stringResource(R.string.aerix_create_loader_version, loader.displayName, loaderBuild?.label ?: "")
                                } else {
                                    stringResource(R.string.aerix_create_loader_loading)
                                },
                                color = if (loaderBuild != null) AerixSurface.textPrimary else AerixSurface.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = stringResource(R.string.aerix_create_only_stable), color = AerixSurface.textSecondary, fontSize = 11.sp)
                            Switch(
                                checked = onlyStable,
                                onCheckedChange = { onlyStable = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AerixSurface.onAccent,
                                    checkedTrackColor = accent,
                                    uncheckedThumbColor = AerixSurface.textSecondary,
                                    uncheckedTrackColor = AerixSurface.panelTrack
                                )
                            )
                        }
                        when {
                            loadingBuilds -> Text(
                                text = stringResource(R.string.aerix_create_loader_loading),
                                color = AerixSurface.textSecondary,
                                fontSize = 11.sp
                            )
                            visibleBuilds.isEmpty() -> Text(
                                text = if (buildsFailed) stringResource(R.string.aerix_create_failed)
                                else stringResource(
                                    R.string.aerix_create_loader_empty,
                                    loader.displayName,
                                    selectedVersion ?: stringResource(R.string.aerix_create_versions_empty)
                                ),
                                color = AerixSurface.warning,
                                fontSize = 11.sp
                            )
                            else -> BuildList(
                                builds = visibleBuilds,
                                selected = loaderBuild,
                                onSelect = { loaderBuild = it }
                            )
                        }
                    }
                }

                ImportModpackRow(onClick = {
                    PojavNavigator.openInstaller(activity, PojavInstaller.MODPACK)
                })

                AerixPrimaryButton(
                    text = stringResource(if (PojavCreateInstanceBridge.installing) R.string.aerix_create_installing else R.string.aerix_create_install),
                    iconRes = R.drawable.aerix_ic_download,
                    onClick = {
                        PojavCreateInstanceBridge.install(
                            context = activity,
                            loader = loader,
                            gameVersion = selectedVersion,
                            loaderVersion = loaderBuild,
                            name = name,
                            icon = icon
                        ) { created ->
                            if (created) onClose() else PojavInstanceBridge.reload()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canInstall
                )
            }
        }
    }
}

@Composable
private fun VersionTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .liquidGlass(shape = CircleShape, strength = 0.6f)
            .background(AerixSurface.panel, CircleShape)
            .padding(AerixSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
    ) {
        versionTabs.forEachIndexed { index, (_, labelRes) ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AerixThemeManager.currentAccent() else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = AerixSpacing.smPlus),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(labelRes),
                    color = if (isSelected) AerixSurface.onAccent else AerixSurface.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun VersionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val shape = RoundedCornerShape(AerixRadii.control)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(AerixSurface.panelRaised.copy(alpha = if (selected) 0.5f else 0.3f))
            .border(BorderStroke(AerixSpacing.hairline, if (selected) accent else AerixSurface.borderSoft), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = AerixSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        Text(
            text = label,
            color = AerixSurface.textPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_check),
                    contentDescription = null,
                    tint = AerixSurface.onAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun LoaderTile(loader: AerixLoader, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val shape = RoundedCornerShape(AerixRadii.cardSmall)
    Column(
        modifier = modifier
            .height(74.dp)
            .clip(shape)
            .liquidGlass(shape = shape, tint = accent, strength = if (selected) 0.9f else 0.5f)
            .background(AerixSurface.panelRaised.copy(alpha = if (selected) 0.42f else 0.28f), shape)
            .border(BorderStroke(if (selected) 2.dp else AerixSpacing.hairline, if (selected) accent else AerixSurface.borderSoft), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(AerixSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(loader.iconRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(26.dp)
        )
        Text(
            text = loader.displayName,
            color = if (selected) accent else AerixSurface.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(loader.taglineRes),
            color = AerixSurface.textSecondary,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BuildList(builds: List<AerixLoaderVersion>, selected: AerixLoaderVersion?, onSelect: (AerixLoaderVersion) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(76.dp),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
    ) {
        items(builds, key = { it.id }) { build ->
            VersionRow(label = build.label, selected = build.id == selected?.id, onClick = { onSelect(build) })
        }
    }
}

@Composable
private fun ImportModpackRow(onClick: () -> Unit) {
    val shape = RoundedCornerShape(AerixRadii.cardSmall)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .liquidGlass(shape = shape, strength = 0.7f, elevation = AerixMetrics.glassFloatingElevation)
            .background(AerixSurface.panelRaised.copy(alpha = 0.66f), shape)
            .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        Icon(
            painter = painterResource(R.drawable.aerix_ic_import_modpack),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(26.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.aerix_create_import_modpack),
                color = AerixSurface.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.aerix_create_import_modpack_sub),
                color = AerixSurface.textSecondary,
                fontSize = 11.sp
            )
        }
        Icon(
            painter = painterResource(R.drawable.aerix_ic_chevron_right),
            contentDescription = null,
            tint = AerixSurface.textSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}
