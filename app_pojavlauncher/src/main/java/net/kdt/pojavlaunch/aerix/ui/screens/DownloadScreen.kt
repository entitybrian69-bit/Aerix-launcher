/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/DownloadScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/DownloadModScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/DownloadResourcePackScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/DownloadShadersScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/DownloadSavesScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/assets/search/SearchAssetsScreen.kt
 * Search, version listing and installs go through PojavAssetBridge (Pojav ApiHandler + DownloadUtils).
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

import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.AssetFile
import net.kdt.pojavlaunch.aerix.bridge.AssetPlatform
import net.kdt.pojavlaunch.aerix.bridge.AssetProject
import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.InstanceTarget
import net.kdt.pojavlaunch.aerix.bridge.PojavAssetBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavContentBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.PillTabs
import net.kdt.pojavlaunch.aerix.components.QuietEmptyState
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.utils.DownloadUtils
import java.io.ByteArrayOutputStream

/** Decoded project icons, keyed by URL; sized in kilobytes. */
private val iconCache = object : LruCache<String, ImageBitmap>(8 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
}

@Composable
private fun rememberRemoteIcon(url: String?): ImageBitmap? {
    val state = produceState(initialValue = url?.let { iconCache.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ByteArrayOutputStream().also { DownloadUtils.download(url, it) }.toByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 96 && bounds.outHeight / (sample * 2) >= 96) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                bitmap?.asImageBitmap()?.also { iconCache.put(url, it) }
            }.getOrNull()
        }
    }
    return state.value
}

private fun formatCount(count: Long): String = when {
    count >= 1_000_000_000L -> "%.1fB".format(count / 1_000_000_000.0)
    count >= 1_000_000L -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000L -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

private fun AssetType.titleRes(): Int = when (this) {
    AssetType.MOD -> R.string.aerix_content_mods
    AssetType.RESOURCE_PACK -> R.string.aerix_content_resourcepacks
    AssetType.SHADER_PACK -> R.string.aerix_content_shaderpacks
    AssetType.WORLD -> R.string.aerix_content_saves
}

private fun AssetPlatform.titleRes(): Int = when (this) {
    AssetPlatform.MODRINTH -> R.string.aerix_download_platform_modrinth
    AssetPlatform.CURSEFORGE -> R.string.aerix_download_platform_curseforge
}

/** Lets other screens open the download screen on a given tab. */
object AerixDownloadRequest {
    var type by mutableStateOf<AssetType?>(null)

    fun open(type: AssetType, navigate: (LauncherSection) -> Unit) {
        this.type = type
        navigate(LauncherSection.DOWNLOADS)
    }
}

@Composable
fun DownloadScreen(activity: FragmentActivity) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val accent = AerixThemeManager.currentAccent()
    val types = AssetType.entries
    var typeIndex by rememberSaveable { mutableIntStateOf(0) }
    AerixDownloadRequest.type?.let { requested ->
        typeIndex = types.indexOf(requested)
        AerixDownloadRequest.type = null
    }
    val type = types[typeIndex.coerceIn(0, types.size - 1)]
    // Re-evaluated when the user key changes in settings.
    val keyRevision = AerixPreferences.curseForgeApiKey
    val platforms = remember(type, keyRevision) { PojavAssetBridge.platforms(context, type) }
    var platform by rememberSaveable { mutableStateOf(AerixPreferences.assetPlatformSource) }
    if (platforms.isNotEmpty() && platform !in platforms) platform = platforms.first()
    var queryInput by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var matchInstance by rememberSaveable { mutableStateOf(true) }
    val selected = PojavInstanceBridge.selected
    var target by remember { mutableStateOf(InstanceTarget(null, null)) }
    var projects by remember { mutableStateOf<List<AssetProject>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var openProject by remember { mutableStateOf<AssetProject?>(null) }
    val scope = rememberCoroutineScope()
    var pageJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(selected?.source) { target = PojavAssetBridge.instanceTarget() }

    suspend fun loadPage(offset: Int) {
        loading = true
        failed = false
        val page = PojavAssetBridge.search(
            context, platform, type, query,
            if (matchInstance) target.mcVersion else null,
            if (matchInstance) target.loader else null,
            offset
        )
        if (page == null) {
            failed = true
        } else {
            projects = if (offset == 0) page.projects else projects + page.projects
            total = page.total
        }
        loading = false
    }

    // Any filter change starts a fresh search from offset 0.
    LaunchedEffect(type, platform, query, matchInstance, target.mcVersion, target.loader, platforms, retry) {
        pageJob?.cancel()
        projects = emptyList()
        total = 0
        if (platforms.isEmpty()) {
            loading = false
            return@LaunchedEffect
        }
        loadPage(0)
    }

    openProject?.let { project ->
        VersionDialog(
            project = project,
            type = type,
            target = if (matchInstance) target else InstanceTarget(null, null),
            onDismiss = { openProject = null }
        )
    }

    AerixPage(
        title = stringResource(R.string.aerix_download_title),
        subtitle = selected?.let { stringResource(R.string.aerix_download_subtitle, it.name) } ?: stringResource(R.string.no_instance),
        trailing = {
            AerixGhostButton(stringResource(R.string.aerix_nav_content), R.drawable.aerix_ic_folder_outlined, {
                PojavContentBridge.openFolder(activity, type.folder)
            })
        }
    ) {
        PillTabs(
            tabs = types.map { stringResource(it.titleRes()) },
            selected = typeIndex,
            onSelect = { typeIndex = it },
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            if (platforms.size > 1) {
                PillTabs(
                    tabs = platforms.map { stringResource(it.titleRes()) },
                    selected = platforms.indexOf(platform).coerceAtLeast(0),
                    onSelect = { platform = platforms[it] }
                )
            } else if (platforms.size == 1) {
                Text(stringResource(platforms[0].titleRes()), color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.aerix_download_match_instance), color = AerixSurface.textPrimary, fontSize = 12.sp)
                val filter = listOfNotNull(target.mcVersion, target.loader).joinToString(" · ")
                if (filter.isNotEmpty()) Text(filter, color = AerixSurface.textMuted, fontSize = 10.sp)
            }
            Switch(
                checked = matchInstance,
                onCheckedChange = { matchInstance = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AerixSurface.onAccent,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = AerixSurface.textSecondary,
                    uncheckedTrackColor = AerixSurface.panelTrack
                )
            )
        }
        OutlinedTextField(
            value = queryInput,
            onValueChange = { queryInput = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.aerix_download_search), color = AerixSurface.textMuted) },
            leadingIcon = { Icon(painterResource(R.drawable.aerix_ic_search), null, tint = AerixSurface.textSecondary) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                query = queryInput.trim()
                focus.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AerixRadii.control),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AerixSurface.textPrimary,
                unfocusedTextColor = AerixSurface.textPrimary,
                focusedBorderColor = accent,
                unfocusedBorderColor = AerixSurface.borderSoft,
                cursorColor = accent
            )
        )

        when {
            platforms.isEmpty() -> QuietEmptyState(R.drawable.aerix_ic_warning_outlined, stringResource(R.string.aerix_download_no_platform))
            failed && projects.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                QuietEmptyState(R.drawable.aerix_ic_warning_outlined, stringResource(R.string.aerix_download_failed))
                AerixGhostButton(stringResource(R.string.aerix_download_retry), R.drawable.aerix_ic_refresh, {
                    retry++
                })
            }
            loading && projects.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent)
            }
            projects.isEmpty() -> QuietEmptyState(type.folder.iconRes, stringResource(R.string.aerix_download_empty))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                items(projects, key = { it.platform.name + it.id }) { project ->
                    ProjectRow(project, type) { openProject = project }
                }
                if (projects.size < total) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(AerixSpacing.sm), contentAlignment = Alignment.Center) {
                            if (loading) {
                                CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
                            } else {
                                AerixGhostButton(stringResource(R.string.aerix_download_load_more), R.drawable.aerix_ic_download, {
                                    if (pageJob?.isActive != true) {
                                        pageJob = scope.launch { loadPage(projects.size) }
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(project: AssetProject, type: AssetType, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AerixRadii.panelSmall)
    val icon = rememberRemoteIcon(project.iconUrl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AerixSurface.panelRaised.copy(alpha = 0.42f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        AerixImageWell(image = icon, fallback = type.folder.iconRes, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                Text(
                    text = project.title,
                    color = AerixSurface.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (project.author.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.aerix_download_by, project.author),
                        color = AerixSurface.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = project.description,
                color = AerixSurface.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)) {
            Icon(painterResource(R.drawable.aerix_ic_download), null, tint = AerixSurface.textMuted, modifier = Modifier.size(14.dp))
            Text(formatCount(project.downloads), color = AerixSurface.textMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun VersionDialog(project: AssetProject, type: AssetType, target: InstanceTarget, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()
    var files by remember(project) { mutableStateOf<List<AssetFile>?>(null) }
    var failed by remember(project) { mutableStateOf(false) }
    var showAll by remember(project) { mutableStateOf(false) }
    var installing by remember(project) { mutableStateOf<AssetFile?>(null) }
    val installedText = stringResource(R.string.aerix_download_installed, project.title)
    val installFailedText = stringResource(R.string.aerix_download_install_failed)

    LaunchedEffect(project) {
        val result = PojavAssetBridge.files(context, project)
        if (result == null) failed = true else files = result
    }

    val shown = remember(files, showAll, target) {
        files.orEmpty().filter { file ->
            showAll ||
                ((target.mcVersion == null || target.mcVersion in file.gameVersions) &&
                    (type != AssetType.MOD || target.loader == null || file.loaders.isEmpty() || target.loader in file.loaders))
        }
    }

    AlertDialog(
        onDismissRequest = { if (installing == null) onDismiss() },
        containerColor = AerixSurface.panelRaised,
        title = { Text(project.title, color = AerixSurface.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                if (target.mcVersion != null || target.loader != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.aerix_download_show_all),
                            color = AerixSurface.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = showAll,
                            onCheckedChange = { showAll = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AerixSurface.onAccent,
                                checkedTrackColor = accent,
                                uncheckedThumbColor = AerixSurface.textSecondary,
                                uncheckedTrackColor = AerixSurface.panelTrack
                            )
                        )
                    }
                }
                when {
                    failed -> Text(stringResource(R.string.aerix_download_failed), color = AerixSurface.danger, fontSize = 13.sp)
                    files == null -> Box(modifier = Modifier.fillMaxWidth().padding(AerixSpacing.lg), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                    shown.isEmpty() -> Text(stringResource(R.string.aerix_download_no_versions), color = AerixSurface.textSecondary, fontSize = 13.sp)
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                    ) {
                        items(shown) { file ->
                            VersionRow(
                                file = file,
                                busy = installing === file,
                                enabled = installing == null && file.url != null,
                                onClick = {
                                    installing = file
                                    scope.launch {
                                        val ok = PojavAssetBridge.install(type, file)
                                        installing = null
                                        Toast.makeText(context, if (ok) installedText else installFailedText, Toast.LENGTH_SHORT).show()
                                        if (ok) onDismiss()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = installing == null) {
                Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary)
            }
        }
    )
}

@Composable
private fun VersionRow(file: AssetFile, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AerixSurface.panel.copy(alpha = 0.5f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, color = AerixSurface.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = (file.loaders + file.gameVersions.take(4)).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = AerixSurface.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (file.url == null) Text(stringResource(R.string.aerix_download_unavailable), color = AerixSurface.warning, fontSize = 11.sp)
        }
        if (busy) {
            CircularProgressIndicator(color = accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                painterResource(R.drawable.aerix_ic_download),
                contentDescription = stringResource(R.string.aerix_download_install),
                tint = if (enabled) accent else AerixSurface.textMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
