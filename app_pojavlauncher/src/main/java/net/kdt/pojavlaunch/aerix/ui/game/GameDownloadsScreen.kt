/*
 * Aerix Launcher in-game downloader.
 *
 * Resource packs, shaders and worlds are searched and installed while the game keeps running.
 * The results are filtered by the version of the running instance, and everything is written into
 * that instance's folders (resourcepacks/, shaderpacks/, saves/).
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

package net.kdt.pojavlaunch.aerix.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.AssetPlatform
import net.kdt.pojavlaunch.aerix.bridge.AssetProject
import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.DownloadError
import net.kdt.pojavlaunch.aerix.bridge.GameDownloadTarget
import net.kdt.pojavlaunch.aerix.bridge.PojavGameDownloadsBridge
import net.kdt.pojavlaunch.aerix.ui.screens.rememberRemoteBitmap
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface

/**
 * Content types the in-game downloader offers, in the order of the tabs.
 * Modpacks and single mods are left to the launcher itself: they only take effect after a restart.
 */
private val DOWNLOAD_TYPES = listOf(
    Triple(AssetType.RESOURCE_PACK, R.string.aerix_ingame_download_type_resourcepacks, R.drawable.aerix_ic_photo_library_outlined),
    Triple(AssetType.SHADER_PACK, R.string.aerix_ingame_download_type_shaders, R.drawable.aerix_ic_wallpaper),
    Triple(AssetType.WORLD, R.string.aerix_ingame_download_type_worlds, R.drawable.aerix_ic_videogame_asset_outlined)
)

/**
 * The downloader page of the in-game menu.
 *
 * @param target instance the game runs, which decides the folder and the version filter
 * @param accent accent colour of the current launcher theme
 * @param onBack leaves the downloader
 * @param onStorageProblem called when the folder of the instance cannot be written to
 */
@Composable
fun GameDownloadsScreen(
    target: GameDownloadTarget,
    accent: Color,
    onBack: () -> Unit,
    onStorageProblem: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = target.mcVersion

    var type by remember { mutableStateOf(DOWNLOAD_TYPES.first().first) }
    // The platform picked in the launcher settings is the one the downloader starts on.
    var platform by remember { mutableStateOf(AerixPreferences.assetPlatformSource) }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var projects by remember { mutableStateOf<List<AssetProject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<DownloadError?>(null) }
    var versionRelaxed by remember { mutableStateOf(false) }
    var storageFailure by remember { mutableStateOf<DownloadError?>(null) }
    var installingId by remember { mutableStateOf<String?>(null) }
    var statusLine by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Folder of the running instance: created and checked once per content type.
    LaunchedEffect(type, reload) {
        storageFailure = PojavGameDownloadsBridge.prepareDirectory(type)
        installed = PojavGameDownloadsBridge.installed(type)
    }

    LaunchedEffect(type, platform, query, reload) {
        loading = true
        failure = null
        // Wait for the rest of the word while the player types. Not on an empty query: deleting
        // the search has to bring the list back at once.
        if (query.isNotEmpty()) delay(450)
        val result = PojavGameDownloadsBridge.search(context, platform, type, query, target)
        projects = result.page?.projects.orEmpty()
        failure = result.error
        versionRelaxed = result.versionRelaxed
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(AerixSpacing.md)) {
        DownloadsHeader(
            target = target,
            accent = accent,
            onBack = onBack
        )
        Spacer(Modifier.height(AerixSpacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            SegmentedRow(
                labels = DOWNLOAD_TYPES.map { stringResource(it.second) },
                selectedIndex = DOWNLOAD_TYPES.indexOfFirst { it.first == type },
                accent = accent,
                onSelect = { index ->
                    type = DOWNLOAD_TYPES[index].first
                    statusLine = null
                },
                modifier = Modifier.weight(1f)
            )
            SegmentedRow(
                labels = AssetPlatform.entries.map { it.displayName },
                selectedIndex = AssetPlatform.entries.indexOf(platform),
                accent = accent,
                onSelect = { index ->
                    platform = AssetPlatform.entries[index]
                    statusLine = null
                },
                modifier = Modifier.weight(0.85f)
            )
        }

        Spacer(Modifier.height(AerixSpacing.smCompact))
        SearchField(
            query = query,
            accent = accent,
            hint = stringResource(R.string.aerix_ingame_download_search, stringResource(DOWNLOAD_TYPES.first { it.first == type }.second)),
            onQueryChange = { query = it }
        )

        if (versionRelaxed && version != null && projects.isNotEmpty()) {
            Spacer(Modifier.height(AerixSpacing.xs))
            Text(
                text = stringResource(R.string.aerix_ingame_download_version_relaxed, version),
                color = AerixSurface.textMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        storageFailure?.let { error ->
            Spacer(Modifier.height(AerixSpacing.smCompact))
            MessageCard(
                text = storageMessage(context, error),
                accent = accent,
                actionLabel = stringResource(R.string.aerix_ingame_download_storage_action),
                onAction = onStorageProblem
            )
        }

        Spacer(Modifier.height(AerixSpacing.smCompact))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && projects.isEmpty() -> LoadingRow(accent)
                failure != null -> MessageCard(
                    text = errorMessage(context, failure!!),
                    accent = accent,
                    actionLabel = stringResource(R.string.aerix_ingame_download_retry),
                    onAction = { reload++ }
                )
                projects.isEmpty() -> MessageCard(
                    text = if (version == null) {
                        stringResource(R.string.aerix_ingame_download_empty_any)
                    } else {
                        stringResource(R.string.aerix_ingame_download_empty, version)
                    },
                    accent = accent,
                    actionLabel = stringResource(R.string.aerix_ingame_download_retry),
                    onAction = { reload++ }
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = AerixSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                ) {
                    items(projects, key = { it.id }) { project ->
                        DownloadRow(
                            project = project,
                            accent = accent,
                            installed = isInstalledByFingerprint(project, installed),
                            installing = installingId == project.id,
                            busy = installingId != null,
                            onInstall = {
                                scope.launch {
                                    installingId = project.id
                                    statusLine = context.getString(R.string.aerix_ingame_download_working)
                                    val outcome = PojavGameDownloadsBridge.install(context, type, project, target)
                                    installingId = null
                                    statusLine = if (outcome.isSuccess) {
                                        installed = PojavGameDownloadsBridge.installed(type)
                                        "✔ " + context.getString(
                                            R.string.aerix_ingame_download_done, outcome.fileName.orEmpty()
                                        )
                                    } else {
                                        errorMessage(context, outcome.error ?: DownloadError.FILE)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        statusLine?.let { line ->
            Spacer(Modifier.height(AerixSpacing.xs))
            Text(
                text = line,
                color = AerixSurface.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Name based check of what is already in the instance folder; the same rule the launcher uses. */
private fun isInstalledByFingerprint(project: AssetProject, installed: Set<String>): Boolean =
    installed.isNotEmpty() && installed.any { fingerprint ->
        val slug = project.title.lowercase().replace(" ", "")
        fingerprint == project.title.lowercase() ||
            (slug.length >= 4 && fingerprint.replace("-", "").replace("_", "").replace(" ", "").contains(slug))
    }

@Composable
private fun DownloadsHeader(target: GameDownloadTarget, accent: Color, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_arrow_left_rounded),
                contentDescription = stringResource(R.string.aerix_ingame_menu_close),
                tint = AerixSurface.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(AerixSpacing.xs))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.aerix_ingame_download_title),
                color = AerixSurface.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.aerix_ingame_download_subtitle, target.instanceName.ifEmpty { "?" }),
                color = AerixSurface.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.45f), CircleShape)
                .padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_check),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(AerixSpacing.xs))
            Text(
                text = target.versionLabel,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SegmentedRow(
    labels: List<String>,
    selectedIndex: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AerixRadii.control)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, AerixSurface.borderSoft, shape)
            .padding(AerixSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AerixRadii.compact))
                    .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(vertical = AerixSpacing.xsPlus),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) accent else AerixSurface.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, accent: Color, hint: String, onQueryChange: (String) -> Unit) {
    val shape = RoundedCornerShape(AerixRadii.control)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (query.isEmpty()) AerixSurface.borderSoft else accent.copy(alpha = 0.6f), shape)
            .padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.smCompact),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.aerix_ic_search),
            contentDescription = null,
            tint = AerixSurface.textMuted,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(AerixSpacing.sm))
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AerixSurface.textPrimary, fontSize = 13.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(text = hint, color = AerixSurface.textMuted, fontSize = 12.sp, maxLines = 1)
                }
                inner()
            }
        )
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_close),
                    contentDescription = stringResource(R.string.aerix_ingame_download_clear),
                    tint = AerixSurface.textMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    project: AssetProject,
    accent: Color,
    installed: Boolean,
    installing: Boolean,
    busy: Boolean,
    onInstall: () -> Unit
) {
    val shape = RoundedCornerShape(AerixRadii.cardSmall)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "downloadRow")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, AerixSurface.borderSoft, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !busy && !installed,
                role = Role.Button,
                onClick = onInstall
            )
            .padding(AerixSpacing.smCompact),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The icon of the project, through the same cache the Discover screen fills, so a page of
        // results shares what it downloaded with the launcher. The letter stays as the fallback
        // while the icon arrives, and for projects that have none.
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(AerixRadii.compact))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = project.title.take(1).uppercase(),
                color = accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            rememberRemoteBitmap(project.iconUrl, targetMinEdgePx = 64)?.let { icon ->
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(AerixSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.title,
                color = AerixSurface.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildString {
                if (project.author.isNotBlank()) append(project.author)
                if (project.downloads > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(formatDownloads(project.downloads))
                }
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    color = AerixSurface.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(AerixSpacing.sm))
        InstallAction(accent = accent, installed = installed, installing = installing, enabled = !busy, onInstall = onInstall)
    }
}

@Composable
private fun InstallAction(accent: Color, installed: Boolean, installing: Boolean, enabled: Boolean, onInstall: () -> Unit) {
    val shape = RoundedCornerShape(AerixRadii.compact)
    when {
        installing -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = accent,
            strokeWidth = 2.dp
        )
        installed -> Row(
            modifier = Modifier
                .clip(shape)
                .background(AerixSurface.success.copy(alpha = 0.16f))
                .padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_check),
                contentDescription = null,
                tint = AerixSurface.success,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(AerixSpacing.xxs))
            Text(text = stringResource(R.string.aerix_ingame_download_installed), color = AerixSurface.success, fontSize = 10.sp)
        }
        else -> Box(
            modifier = Modifier
                .clip(shape)
                .background(accent.copy(alpha = if (enabled) 0.18f else 0.08f))
                .clickable(enabled = enabled, role = Role.Button, onClick = onInstall)
                .padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.xsPlus)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_cloud_download),
                    contentDescription = null,
                    tint = if (enabled) accent else AerixSurface.textMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(AerixSpacing.xs))
                Text(
                    text = stringResource(R.string.aerix_ingame_download_install),
                    color = if (enabled) accent else AerixSurface.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun LoadingRow(accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = AerixSpacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = accent, strokeWidth = 2.dp)
        Spacer(Modifier.width(AerixSpacing.smPlus))
        Text(
            text = stringResource(R.string.aerix_ingame_download_searching),
            color = AerixSurface.textSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MessageCard(text: String, accent: Color, actionLabel: String, onAction: () -> Unit) {
    val shape = RoundedCornerShape(AerixRadii.cardSmall)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, AerixSurface.borderSoft, shape)
            .padding(AerixSpacing.smPlus),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = AerixSurface.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(AerixSpacing.sm))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(AerixRadii.compact))
                .background(accent.copy(alpha = 0.18f))
                .clickable(role = Role.Button, onClick = onAction)
                .padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.xsPlus)
        ) {
            Text(text = actionLabel, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun errorMessage(context: Context, error: DownloadError): String = when (error) {
    DownloadError.OFFLINE -> context.getString(R.string.aerix_ingame_download_offline)
    DownloadError.API -> context.getString(R.string.aerix_ingame_download_api_error)
    DownloadError.KEY -> context.getString(R.string.aerix_ingame_download_key_error)
    DownloadError.NO_TARGET -> context.getString(R.string.aerix_ingame_download_no_instance)
    DownloadError.STORAGE -> context.getString(R.string.aerix_ingame_download_storage_error)
    DownloadError.FILE -> context.getString(R.string.aerix_ingame_download_failed)
}

private fun storageMessage(context: Context, error: DownloadError): String = when (error) {
    DownloadError.NO_TARGET -> context.getString(R.string.aerix_ingame_download_no_instance)
    else -> context.getString(R.string.aerix_ingame_download_storage_error)
}

private fun formatDownloads(downloads: Long): String {
    if (downloads < 1000) return "$downloads"
    if (downloads < 1_000_000) return "${downloads / 1000}k"
    return "${downloads / 1_000_000}M"
}
