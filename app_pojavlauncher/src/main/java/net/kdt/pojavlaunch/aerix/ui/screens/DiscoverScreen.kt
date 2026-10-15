/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/DownloadScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/assets/search/SearchAssetsScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/assets/elements/_Search.Result.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/assets/download/DownloadAssetsScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/assets/elements/DownloadAssetsElements.kt
 * Top pill bar (Modpacks, Mods, Resource Packs, Shaders, Worlds, Install Vanilla, Favorites),
 * filter bar (Search..., Modrinth/CurseForge dropdown, Category dropdown, MC version dropdown,
 * Loader dropdown, Sort dropdown), 3-column ResultProjectLayout cards with Installed/Install button
 * and Favorite toggle, floating PageController ("1 / N  <  >"), and the in-page project detail
 * view with hero card, screenshots carousel between the hero and the grouped version list, and
 * project links rail.
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
import android.text.format.Formatter
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.AssetFile
import net.kdt.pojavlaunch.aerix.bridge.AssetPage
import net.kdt.pojavlaunch.aerix.bridge.AssetFacet
import net.kdt.pojavlaunch.aerix.bridge.AssetFacets
import net.kdt.pojavlaunch.aerix.bridge.AssetPlatform
import net.kdt.pojavlaunch.aerix.bridge.AssetProject
import net.kdt.pojavlaunch.aerix.bridge.AssetProjectDetails
import net.kdt.pojavlaunch.aerix.bridge.AssetScreenshot
import net.kdt.pojavlaunch.aerix.bridge.AssetSort
import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.InstanceTarget
import net.kdt.pojavlaunch.aerix.bridge.PojavAssetBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavCreateInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixPillTab
import net.kdt.pojavlaunch.aerix.components.AerixPillTabRow
import net.kdt.pojavlaunch.aerix.components.ModrinthMetaPill
import net.kdt.pojavlaunch.aerix.components.QuietEmptyState
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.utils.DownloadUtils
import java.io.ByteArrayOutputStream

/** Decoded remote images (icons + screenshots), keyed by URL; sized in kilobytes. */
private val remoteBitmapCache = object : LruCache<String, ImageBitmap>(16 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        (value.width * value.height * 4 / 1024).coerceAtLeast(1)
}

@Composable
internal fun rememberRemoteBitmap(url: String?, targetMinEdgePx: Int = 96): ImageBitmap? {
    val state = produceState(initialValue = url?.let { remoteBitmapCache.get(it) }, url) {
        if (url.isNullOrBlank() || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ByteArrayOutputStream().also { DownloadUtils.download(url, it) }.toByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= targetMinEdgePx &&
                    bounds.outHeight / (sample * 2) >= targetMinEdgePx
                ) {
                    sample *= 2
                }
                val bitmap = BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
                bitmap?.asImageBitmap()?.also { remoteBitmapCache.put(url, it) }
            }.getOrNull()
        }
    }
    return state.value
}

private fun formatDownloadCount(count: Long): String = when {
    count >= 1_000_000_000L -> "%.1fB".format(count / 1_000_000_000.0)
    count >= 1_000_000L -> {
        val m = count / 1_000_000.0
        if (m >= 100.0) "${count / 1_000_000L}M" else "%.1fM".format(m)
    }
    count >= 1_000L -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

private val commonMcVersions = listOf(
    "26.3", "26.2", "26.1",
    "1.21.4", "1.21.3", "1.21.1", "1.21",
    "1.20.6", "1.20.4", "1.20.2", "1.20.1",
    "1.19.4", "1.19.2",
    "1.18.2", "1.17.1", "1.16.5", "1.12.2", "1.8.9", "1.7.10"
)

private val filterLoaders = listOf(
    "fabric" to "Fabric",
    "neoforge" to "NeoForge",
    "forge" to "Forge",
    "quilt" to "Quilt"
)

private enum class DiscoverTab(val label: String, @DrawableRes val iconRes: Int, val assetType: AssetType?) {
    MODPACKS("Modpacks", R.drawable.aerix_ic_package_2_outlined, AssetType.MODPACK),
    MODS("Mods", R.drawable.aerix_ic_extension_outlined, AssetType.MOD),
    RESOURCE_PACKS("Resource Packs", R.drawable.aerix_ic_format_paint_outlined, AssetType.RESOURCE_PACK),
    SHADERS("Shaders", R.drawable.aerix_ic_lightbulb, AssetType.SHADER_PACK),
    WORLDS("Worlds", R.drawable.aerix_ic_public, AssetType.WORLD),
    INSTALL_VANILLA("Install Vanilla", R.drawable.aerix_ic_videogame_asset_outlined, null),
    FAVORITES("Favorites", R.drawable.aerix_ic_favorite_filled, null)
}

/** Lets other screens open Discover on a specific content tab (e.g. from Instance Management "+"). */
object AerixDownloadRequest {
    var type by mutableStateOf<AssetType?>(null)

    fun open(type: AssetType, navigate: (LauncherSection) -> Unit) {
        this.type = type
        navigate(LauncherSection.DISCOVER)
    }
}

@Composable
fun DiscoverScreen(activity: FragmentActivity, @Suppress("UNUSED_PARAMETER") navigate: (LauncherSection) -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(DiscoverTab.MODS) }
    var activeProject by remember { mutableStateOf<AssetProject?>(null) }

    val requested = AerixDownloadRequest.type
    if (requested != null) {
        selectedTab = DiscoverTab.entries.firstOrNull { it.assetType == requested } ?: DiscoverTab.MODS
        activeProject = null
        AerixDownloadRequest.type = null
    }

    BackHandler(enabled = activeProject != null) {
        activeProject = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        // Top category pill bar (Mirai DownloadScreen.kt lines 152-160)
        AerixPillTabRow(modifier = Modifier.fillMaxWidth()) {
            DiscoverTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                AerixPillTab(
                    selected = selected,
                    onClick = {
                        if (tab == DiscoverTab.INSTALL_VANILLA) {
                            AerixNav.navigate(LauncherSection.CREATE_INSTANCE)
                        } else {
                            selectedTab = tab
                            activeProject = null
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        val detailProject = activeProject
        if (detailProject != null) {
            AssetDetailContent(
                activity = activity,
                project = detailProject,
                onBack = { activeProject = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else if (selectedTab == DiscoverTab.FAVORITES) {
            FavoritesContent(
                onOpenProject = { activeProject = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            val type = selectedTab.assetType ?: AssetType.MOD
            SearchAssetsContent(
                activity = activity,
                type = type,
                onOpenProject = { activeProject = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
fun DownloadScreen(activity: FragmentActivity) {
    DiscoverScreen(activity = activity, navigate = { AerixNav.navigate(it) })
}

// ============================================================================================
// Search & filter bar + 3-column grid + floating PageController (Mirai SearchAssetsScreen.kt)
// ============================================================================================

@Composable
private fun SearchAssetsContent(
    activity: FragmentActivity,
    type: AssetType,
    onOpenProject: (AssetProject) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var platform by rememberSaveable { mutableStateOf(AerixPreferences.assetPlatformSource) }
    var query by rememberSaveable(type) { mutableStateOf("") }
    var submittedQuery by rememberSaveable(type) { mutableStateOf("") }
    var category by rememberSaveable(type) { mutableStateOf<String?>(null) }
    var mcVersion by rememberSaveable(type) { mutableStateOf<String?>(null) }
    var loader by rememberSaveable(type) { mutableStateOf<String?>(null) }
    var sort by rememberSaveable(type) { mutableStateOf(AssetSort.RELEVANCE) }
    var pageIndex by rememberSaveable(type) { mutableIntStateOf(1) }

    var page by remember { mutableStateOf<AssetPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var installedFingerprints by remember { mutableStateOf<Set<String>>(emptySet()) }

    // The filters of the source: every Minecraft version it knows, and the categories of the
    // content type as that source names them. They follow the platform switch, because the two
    // sources do not offer the same ones - CurseForge has categories Modrinth never had, and the
    // other way round. The lists the type ships with stand in until the answer arrives.
    var facets by remember(type) { mutableStateOf(AssetFacets()) }
    val fallbackCategories = remember(type) {
        type.categories.map { (id, label) -> AssetFacet(id, label) }
    }
    // Before the source answers - and if it never does - the versions are the ones the launcher
    // already has: the table behind the create screen, complete and offline. The short list is
    // only there for a fresh install that has not downloaded that table yet.
    val localVersions = remember {
        runCatching { PojavCreateInstanceBridge.gameVersions().map { AssetFacet(it.id, it.id) } }
            .getOrDefault(emptyList())
    }
    val versions = remember(facets, localVersions) {
        facets.versions.ifEmpty { localVersions.ifEmpty { commonMcVersions.map { AssetFacet(it, it) } } }
    }
    val categories = remember(facets, fallbackCategories) { facets.categories.ifEmpty { fallbackCategories } }

    val selectedInstance = PojavInstanceBridge.selected

    LaunchedEffect(type, selectedInstance?.source) {
        installedFingerprints = PojavAssetBridge.installedFingerprints(type)
    }

    LaunchedEffect(type, platform) {
        facets = AssetFacets()
        facets = PojavAssetBridge.facets(context, platform, type)
    }

    LaunchedEffect(type, platform, submittedQuery, category, mcVersion, loader, sort, pageIndex, reloadNonce) {
        loading = true
        failed = false
        val offset = (pageIndex - 1).coerceAtLeast(0) * PojavAssetBridge.PAGE_SIZE
        val result = PojavAssetBridge.search(
            context = context,
            platform = platform,
            type = type,
            query = submittedQuery.trim(),
            mcVersion = mcVersion,
            loader = if (type.supportsLoaderFilter) loader else null,
            offset = offset,
            category = category,
            sort = sort
        )
        page = result
        failed = result == null
        loading = false
    }

    var showPlatformMenu by remember { mutableStateOf(false) }
    // The two long lists - every version of the game, every category of the source - are picked in
    // a dialog, not in a dropdown menu: see FacetPicker for why.
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showVersionPicker by remember { mutableStateOf(false) }
    var showLoaderMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        // Compact Horizontal Search & Filter Bar (Mirai SearchAssetsScreen.kt lines 357-609)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smCompact)
        ) {
            // Search Box
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(AerixRadii.controlSmall))
                    .background(AerixSurface.panelRaised)
                    .border(AerixSpacing.hairline, AerixSurface.borderSoft, RoundedCornerShape(AerixRadii.controlSmall))
                    .padding(horizontal = AerixSpacing.smPlus),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smCompact)
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_search),
                    contentDescription = null,
                    tint = AerixSurface.textSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable {
                            pageIndex = 1
                            submittedQuery = query
                        }
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search...",
                            style = MaterialTheme.typography.bodySmall,
                            color = AerixSurface.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            pageIndex = 1
                            submittedQuery = it
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(AerixThemeManager.currentAccent()),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            pageIndex = 1
                            submittedQuery = query
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_close),
                        contentDescription = null,
                        tint = AerixSurface.textSecondary,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable {
                                query = ""
                                submittedQuery = ""
                                pageIndex = 1
                            }
                    )
                }
            }

            // Platform Dropdown Pill (Modrinth / CurseForge)
            Box {
                DiscoverFilterDropdownPill(
                    label = platform.displayName,
                    onClick = { showPlatformMenu = true }
                )
                DropdownMenu(
                    expanded = showPlatformMenu,
                    onDismissRequest = { showPlatformMenu = false }
                ) {
                    AssetPlatform.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.displayName) },
                            onClick = {
                                showPlatformMenu = false
                                if (platform != p) {
                                    platform = p
                                    AerixPreferences.saveAssetPlatformSource(p)
                                    // The categories and the version list belong to the source that
                                    // answered them, so both are cleared for the new one.
                                    category = null
                                    mcVersion = null
                                    pageIndex = 1
                                }
                            }
                        )
                    }
                }
            }

            // Category pill. The list is the source's own and can be a couple of hundred entries.
            if (categories.isNotEmpty()) {
                DiscoverFilterDropdownPill(
                    label = "Category: ${categories.firstOrNull { it.id == category }?.label ?: "All"}",
                    onClick = { showCategoryPicker = true }
                )
            }

            // MC Version pill. The list is every version of the game the source knows.
            DiscoverFilterDropdownPill(
                label = "MC: ${mcVersion ?: "All"}",
                onClick = { showVersionPicker = true }
            )

            // Loader Dropdown Pill ("Loader: All ▾")
            if (type.supportsLoaderFilter) {
                Box {
                    val loaderLabel = filterLoaders.firstOrNull { it.first == loader }?.second ?: "All"
                    DiscoverFilterDropdownPill(
                        label = "Loader: $loaderLabel",
                        onClick = { showLoaderMenu = true }
                    )
                    DropdownMenu(
                        expanded = showLoaderMenu,
                        onDismissRequest = { showLoaderMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Loaders") },
                            onClick = {
                                showLoaderMenu = false
                                loader = null
                                pageIndex = 1
                            }
                        )
                        filterLoaders.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showLoaderMenu = false
                                    loader = id
                                    pageIndex = 1
                                }
                            )
                        }
                    }
                }
            }

            // Sort Dropdown Pill ("Sort: Relevance ▾")
            Box {
                DiscoverFilterDropdownPill(
                    label = "Sort: ${sort.label}",
                    onClick = { showSortMenu = true }
                )
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    AssetSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label) },
                            onClick = {
                                showSortMenu = false
                                sort = s
                                pageIndex = 1
                            }
                        )
                    }
                }
            }
        }

        // Outside the scrolling pill row, because a dialog is a window of its own.
        if (showVersionPicker) {
            FacetPicker(
                title = "Minecraft version",
                allLabel = "All Versions",
                facets = versions,
                selectedId = mcVersion,
                onPick = {
                    mcVersion = it
                    pageIndex = 1
                    showVersionPicker = false
                },
                onDismiss = { showVersionPicker = false }
            )
        }
        if (showCategoryPicker) {
            FacetPicker(
                title = "Category",
                allLabel = "All Categories",
                facets = categories,
                selectedId = category,
                onPick = {
                    category = it
                    pageIndex = 1
                    showCategoryPicker = false
                },
                onDismiss = { showCategoryPicker = false }
            )
        }

        // Results area
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val columns = when {
                maxWidth > maxHeight && maxWidth >= 1040.dp -> 4
                maxWidth > maxHeight && maxWidth >= 640.dp -> 3
                else -> 2
            }
            val currentPage = page
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AerixThemeManager.currentAccent())
                }
                failed -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    QuietEmptyState(R.drawable.aerix_ic_warning_outlined, stringResource(R.string.aerix_download_error))
                    AerixGhostButton(
                        text = stringResource(R.string.aerix_library_refresh),
                        iconRes = R.drawable.aerix_ic_refresh,
                        onClick = { reloadNonce++ }
                    )
                }
                currentPage == null || currentPage.projects.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    QuietEmptyState(R.drawable.aerix_ic_search, stringResource(R.string.aerix_download_empty))
                }
                else -> {
                    val rows = remember(currentPage.projects, columns) {
                        currentPage.projects.chunked(columns)
                    }
                    val totalPages = ((currentPage.total + PojavAssetBridge.PAGE_SIZE - 1) / PojavAssetBridge.PAGE_SIZE)
                        .coerceAtLeast(1)

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = AerixSpacing.xs, bottom = 68.dp),
                            verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
                        ) {
                            items(
                                count = rows.size,
                                key = { idx -> rows[idx].joinToString("_") { "${it.platform}:${it.id}" } }
                            ) { idx ->
                                val rowItems = rows[idx]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
                                ) {
                                    rowItems.forEach { project ->
                                        val isInstalled = PojavAssetBridge.isProjectInstalled(project, installedFingerprints)
                                        val isFavorite = remember(AerixPreferences.favoritesJson, project.id) {
                                            PojavAssetBridge.isFavorite(project)
                                        }
                                        ResultProjectLayout(
                                            project = project,
                                            isInstalled = isInstalled,
                                            isFavorite = isFavorite,
                                            onFavoriteClick = { PojavAssetBridge.toggleFavorite(project) },
                                            onClick = { onOpenProject(project) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(columns - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Floating PageController bottom-end ("1 / 3862   <   >")
                        PageController(
                            pageNumber = pageIndex,
                            totalPages = totalPages,
                            onPrevious = { if (pageIndex > 1) pageIndex-- },
                            onNext = { if (pageIndex < totalPages) pageIndex++ },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = AerixSpacing.sm, end = AerixSpacing.smCompact)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverFilterDropdownPill(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.height(36.dp),
        shape = RoundedCornerShape(AerixRadii.controlSmall),
        color = AerixSurface.panelRaised,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AerixSpacing.smPlus),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AerixSurface.textPrimary,
                maxLines = 1
            )
            Icon(
                painter = painterResource(R.drawable.aerix_ic_arrow_drop_down_rounded),
                contentDescription = null,
                tint = AerixSurface.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun PageController(
    pageNumber: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(AerixRadii.card),
        color = AerixSurface.panel,
        contentColor = AerixSurface.textPrimary,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(start = AerixSpacing.md, end = AerixSpacing.md),
                text = "$pageNumber / $totalPages",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            IconButton(
                enabled = pageNumber > 1,
                onClick = onPrevious
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_arrow_left_rounded),
                    contentDescription = stringResource(R.string.aerix_download_prev)
                )
            }
            IconButton(
                enabled = pageNumber < totalPages,
                onClick = onNext
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_arrow_right_rounded),
                    contentDescription = stringResource(R.string.aerix_download_next)
                )
            }
        }
    }
}

/**
 * Mirai ResultProjectLayout (_Search.Result.kt lines 452-610):
 * 40dp icon + bold title + download count pill, 2-line description, loader badge pill +
 * Favorite toggle, and full-width 40dp Install / Installed button.
 */
@Composable
internal fun ResultProjectLayout(
    project: AssetProject,
    isInstalled: Boolean,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = rememberRemoteBitmap(project.iconUrl, targetMinEdgePx = 96)
    val accent = AerixThemeManager.currentAccent()
    val primaryBadgeText = remember(project.loaders, project.platform) {
        val loaderLabels = project.loaders.take(2).joinToString(" \u2022 ")
        loaderLabels.ifBlank { project.platform.displayName }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AerixRadii.cardSmall),
        color = AerixSurface.panelRaised,
        contentColor = Color.White,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AerixSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            // Top Row: 40dp Icon + Bold Title + Download Count Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
            ) {
                AerixImageWell(
                    image = icon,
                    fallback = R.drawable.aerix_ic_extension_outlined,
                    size = 40.dp,
                    radius = AerixRadii.controlSmall
                )

                Text(
                    modifier = Modifier.weight(1f),
                    text = project.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val downloadsText = remember(project.downloads) { formatDownloadCount(project.downloads) }
                Surface(
                    shape = RoundedCornerShape(AerixRadii.control),
                    color = AerixSurface.panel
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.tiny),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.tiny)
                    ) {
                        Icon(
                            modifier = Modifier.size(12.dp),
                            painter = painterResource(R.drawable.aerix_ic_download_2_outlined),
                            contentDescription = null,
                            tint = AerixSurface.textSecondary
                        )
                        Text(
                            text = downloadsText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AerixSurface.textPrimary
                        )
                    }
                }
            }

            // Middle: 2-line Summary Description
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = project.description,
                fontSize = 12.sp,
                color = AerixSurface.textSecondary,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Loader badge pill + Favorite button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(AerixRadii.control),
                    color = AerixSurface.panel
                ) {
                    Text(
                        text = primaryBadgeText,
                        modifier = Modifier.padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.xs),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AerixSurface.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FavoriteToggleLabel(
                    isFavorite = isFavorite,
                    onClick = onFavoriteClick,
                    modifier = Modifier.height(32.dp)
                )
            }

            // Full-width Install / Installed button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(AerixRadii.controlSmall),
                color = if (isInstalled) AerixSurface.accentContainer else accent,
                contentColor = if (isInstalled) accent else AerixSurface.onAccent,
                onClick = onClick
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isInstalled) "Installed" else "Install",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteToggleLabel(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = AerixThemeManager.currentAccent()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AerixRadii.control))
            .clickable(onClick = onClick)
            .padding(horizontal = AerixSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
    ) {
        Icon(
            painter = painterResource(
                if (isFavorite) R.drawable.aerix_ic_favorite_filled
                else R.drawable.aerix_ic_favorite_outlined
            ),
            contentDescription = null,
            tint = if (isFavorite) accent else AerixSurface.textSecondary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = if (isFavorite) "Saved" else "Favorite",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isFavorite) accent else AerixSurface.textSecondary
        )
    }
}

// ============================================================================================
// Favorites tab
// ============================================================================================

@Composable
private fun FavoritesContent(
    onOpenProject: (AssetProject) -> Unit,
    modifier: Modifier = Modifier
) {
    val favorites = remember(AerixPreferences.favoritesJson) { PojavAssetBridge.favorites() }
    if (favorites.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            QuietEmptyState(R.drawable.aerix_ic_favorite_outlined, "No favorite projects saved yet")
        }
        return
    }
    BoxWithConstraints(modifier = modifier) {
        val columns = when {
            maxWidth > maxHeight && maxWidth >= 1040.dp -> 4
            maxWidth > maxHeight && maxWidth >= 640.dp -> 3
            else -> 2
        }
        val rows = remember(favorites, columns) { favorites.chunked(columns) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
        ) {
            items(rows.size, key = { idx -> rows[idx].joinToString("_") { "${it.platform}:${it.id}" } }) { idx ->
                val rowItems = rows[idx]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
                ) {
                    rowItems.forEach { project ->
                        ResultProjectLayout(
                            project = project,
                            isInstalled = false,
                            isFavorite = true,
                            onFavoriteClick = { PojavAssetBridge.toggleFavorite(project) },
                            onClick = { onOpenProject(project) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ============================================================================================
// Project detail view (Mirai DownloadAssetsScreen.kt: Hero + ScreenshotsRail + FilterRow + grouped versions + LinksRail)
// ============================================================================================

private class VersionGroup(
    val gameVersion: String,
    val loaderLabel: String?,
    val isAdapted: Boolean,
    val files: List<AssetFile>
)

@Composable
private fun AssetDetailContent(
    activity: FragmentActivity,
    project: AssetProject,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()

    var details by remember(project.id) { mutableStateOf<AssetProjectDetails?>(null) }
    var files by remember(project.id) { mutableStateOf<List<AssetFile>?>(null) }
    var target by remember { mutableStateOf(InstanceTarget(null, null)) }
    var loadingFiles by remember(project.id) { mutableStateOf(true) }
    var installingFile by remember { mutableStateOf<String?>(null) }
    var showOnlyReleases by remember { mutableStateOf(true) }
    var searchMcVersion by remember { mutableStateOf("") }

    val installedToast = stringResource(R.string.aerix_download_installed)
    val failedToast = stringResource(R.string.aerix_download_failed)

    LaunchedEffect(project.id, project.platform) {
        loadingFiles = true
        target = PojavAssetBridge.instanceTarget()
        launch { details = PojavAssetBridge.details(context, project) }
        files = PojavAssetBridge.files(context, project)
        loadingFiles = false
    }

    val releaseRegex = remember { Regex("""^\d+\.\d+(\.\d+)?$""") }
    val groups = remember(files, target, showOnlyReleases, searchMcVersion) {
        val all = files ?: emptyList()
        val map = LinkedHashMap<Pair<String, String?>, MutableList<AssetFile>>()
        for (f in all) {
            val loader = f.loaders.firstOrNull()?.replaceFirstChar { it.uppercase() }
            val mcVers = f.gameVersions.ifEmpty { listOf("Any") }
            for (gv in mcVers) {
                if (showOnlyReleases && gv != "Any" && !releaseRegex.matches(gv)) continue
                if (searchMcVersion.isNotBlank() && !gv.contains(searchMcVersion, ignoreCase = true)) continue
                map.getOrPut(gv to loader) { ArrayList() }.add(f)
            }
        }
        map.entries.map { (key, list) ->
            val (gv, ldr) = key
            val adapted = (target.mcVersion == null || gv == target.mcVersion) &&
                (target.loader == null || ldr == null || ldr.equals(target.loader, ignoreCase = true))
            VersionGroup(gv, ldr, adapted, list)
        }.sortedWith(compareByDescending<VersionGroup> { it.isAdapted })
    }

    val latestFile = remember(groups, files) {
        groups.firstOrNull { it.isAdapted }?.files?.firstOrNull()
            ?: groups.firstOrNull()?.files?.firstOrNull()
            ?: files?.firstOrNull()
    }

    val runInstall: (AssetFile) -> Unit = { file ->
        if (installingFile == null) {
            installingFile = file.fileName
            scope.launch {
                val ok = PojavAssetBridge.install(context, project, file)
                installingFile = null
                Toast.makeText(
                    context,
                    if (ok) installedToast.format(file.fileName) else failedToast,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val screenshots = details?.screenshots.orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        // 1. Hero Header Card (Mirai DownloadHeroCard)
        item(key = "detail_hero") {
            DownloadHeroCard(
                project = details?.project ?: project,
                followers = details?.followers,
                latestFile = latestFile,
                installing = installingFile != null,
                onBack = onBack,
                onDownloadLatest = { latestFile?.let(runInstall) }
            )
        }

        // 2. Screenshots Carousel between hero and version list (Screenshot 7: Mirai ScreenshotsRail)
        if (screenshots.isNotEmpty()) {
            item(key = "detail_screenshots") {
                ScreenshotsRail(screenshots = screenshots)
            }
        }

        // 3. Filter Row (Show only Minecraft release versions + Search MC versions...)
        item(key = "detail_filters") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                ) {
                    Surface(
                        shape = RoundedCornerShape(AerixRadii.controlSmall),
                        color = if (showOnlyReleases) accent.copy(alpha = 0.18f) else AerixSurface.panel,
                        border = BorderStroke(AerixSpacing.hairline, if (showOnlyReleases) accent else AerixSurface.borderSoft),
                        onClick = { showOnlyReleases = !showOnlyReleases }
                    ) {
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .padding(horizontal = AerixSpacing.smPlus),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_check),
                                contentDescription = null,
                                tint = if (showOnlyReleases) accent else AerixSurface.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Show only Minecraft release versions",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (showOnlyReleases) accent else AerixSurface.textPrimary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(AerixRadii.controlSmall))
                            .background(AerixSurface.panel)
                            .border(AerixSpacing.hairline, AerixSurface.borderSoft, RoundedCornerShape(AerixRadii.controlSmall))
                            .padding(horizontal = AerixSpacing.smPlus),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (searchMcVersion.isEmpty()) {
                                Text(
                                    text = "Search MC versions...",
                                    fontSize = 12.sp,
                                    color = AerixSurface.textMuted
                                )
                            }
                            BasicTextField(
                                value = searchMcVersion,
                                onValueChange = { searchMcVersion = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                cursorBrush = SolidColor(accent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                HorizontalDivider(color = AerixSurface.borderSoft)
            }
        }

        // 4. Grouped Version Items (Mirai AssetsVersionItemLayout)
        when {
            loadingFiles -> item(key = "detail_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AerixSpacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accent)
                }
            }
            groups.isEmpty() -> item(key = "detail_empty") {
                QuietEmptyState(R.drawable.aerix_ic_warning_outlined, stringResource(R.string.aerix_download_no_files))
            }
            else -> items(
                items = groups,
                key = { "${it.gameVersion}_${it.loaderLabel ?: "any"}" }
            ) { group ->
                VersionGroupCard(
                    group = group,
                    installingFile = installingFile,
                    onInstallFile = runInstall
                )
            }
        }

        // 5. Related Links Rail
        val d = details
        if (d != null && listOfNotNull(d.sourceUrl, d.issuesUrl, d.wikiUrl, d.discordUrl).isNotEmpty()) {
            item(key = "detail_links") {
                LinksRail(activity = activity, details = d)
            }
        }
    }
}

@Composable
private fun DownloadHeroCard(
    project: AssetProject,
    followers: Long?,
    latestFile: AssetFile?,
    installing: Boolean,
    onBack: () -> Unit,
    onDownloadLatest: () -> Unit
) {
    val icon = rememberRemoteBitmap(project.iconUrl, targetMinEdgePx = 128)
    val accent = AerixThemeManager.currentAccent()
    val isFavorite = remember(AerixPreferences.favoritesJson, project.id) {
        PojavAssetBridge.isFavorite(project)
    }
    val typeLabel = when (project.type) {
        AssetType.MODPACK -> "Modpacks"
        AssetType.MOD -> "Mods"
        AssetType.RESOURCE_PACK -> "Resource Packs"
        AssetType.SHADER_PACK -> "Shaders"
        AssetType.WORLD -> "Worlds"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AerixRadii.panel),
        color = AerixSurface.panel,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = AerixSpacing.mdPlus),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AerixSurface.panelRaised)
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_arrow_back),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            AerixImageWell(
                image = icon,
                fallback = R.drawable.aerix_ic_extension_outlined,
                size = 84.dp,
                radius = AerixRadii.card
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
            ) {
                ModrinthMetaPill(text = typeLabel, highlighted = true)
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (project.author.isNotBlank()) {
                    Text(
                        text = "by ${project.author}",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (project.description.isNotBlank()) {
                    Text(
                        text = project.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AerixSurface.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = buildString {
                        append(formatDownloadCount(project.downloads))
                        append(" downloads")
                        if (followers != null && followers > 0) {
                            append("  \u2022  ")
                            append(formatDownloadCount(followers))
                            append(" followers")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AerixSurface.textSecondary
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                FavoriteToggleLabel(
                    isFavorite = isFavorite,
                    onClick = { PojavAssetBridge.toggleFavorite(project) },
                    modifier = Modifier.height(34.dp)
                )
                Button(
                    onClick = onDownloadLatest,
                    enabled = latestFile != null && !installing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = AerixSurface.onAccent
                    ),
                    shape = RoundedCornerShape(AerixRadii.control),
                    contentPadding = PaddingValues(horizontal = AerixSpacing.mdPlus, vertical = AerixSpacing.smPlus)
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(R.drawable.aerix_ic_download_2_outlined),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(AerixSpacing.smCompact))
                    Text(
                        text = if (installing) "Installing..." else "Download Latest",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/**
 * Mirai ScreenshotsRail + ScreenshotItemLayout (DownloadAssetsScreen.kt line 917,
 * DownloadAssetsElements.kt line 541): horizontal carousel of 260dp project screenshots.
 */
@Composable
private fun ScreenshotsRail(screenshots: List<AssetScreenshot>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = AerixSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        items(screenshots, key = { it.url }) { shot ->
            ScreenshotCard(screenshot = shot, width = 260.dp)
        }
    }
}

@Composable
private fun ScreenshotCard(screenshot: AssetScreenshot, width: Dp) {
    val bitmap = rememberRemoteBitmap(screenshot.url, targetMinEdgePx = 360)
    val shape = RoundedCornerShape(AerixRadii.control)
    Surface(
        modifier = Modifier.width(width),
        shape = shape,
        color = AerixSurface.panel,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(146.dp)
                    .background(AerixSurface.panelRaised),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = screenshot.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = AerixThemeManager.currentAccent(),
                        strokeWidth = 2.dp
                    )
                }
            }
            if (!screenshot.title.isNullOrBlank() || !screenshot.description.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    screenshot.title?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    screenshot.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = AerixSurface.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mirai AssetsVersionItemLayout (DownloadAssetsElements.kt lines 223-475):
 * expandable version group header (adapted check icon, gameVersion, loader pill) + individual file rows.
 */
@Composable
private fun VersionGroupCard(
    group: VersionGroup,
    installingFile: String?,
    onInstallFile: (AssetFile) -> Unit
) {
    val context = LocalContext.current
    val accent = AerixThemeManager.currentAccent()
    var expanded by rememberSaveable(group.gameVersion, group.loaderLabel) {
        mutableStateOf(group.isAdapted)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AerixRadii.card),
        color = AerixSurface.panel,
        border = BorderStroke(
            if (group.isAdapted) 1.5.dp else AerixSpacing.hairline,
            if (group.isAdapted) accent.copy(alpha = 0.75f) else AerixSurface.borderSoft
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = AerixSpacing.mdPlus, vertical = AerixSpacing.smPlus),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                if (group.isAdapted) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_check),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = group.gameVersion,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                group.loaderLabel?.let { ldr ->
                    ModrinthMetaPill(text = ldr, highlighted = group.isAdapted)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${group.files.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AerixSurface.textSecondary
                )
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_arrow_drop_down_rounded),
                    contentDescription = null,
                    tint = AerixSurface.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AerixSpacing.mdPlus, end = AerixSpacing.mdPlus, bottom = AerixSpacing.smPlus),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                ) {
                    HorizontalDivider(color = AerixSurface.borderSoft)
                    group.files.forEach { file ->
                        val busy = installingFile == file.fileName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AerixRadii.control))
                                .background(AerixSurface.panelRaised.copy(alpha = 0.55f))
                                .clickable(enabled = installingFile == null && file.url != null) {
                                    onInstallFile(file)
                                }
                                .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = file.releaseType.take(1).uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accent
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name.ifBlank { file.fileName },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildList {
                                        if (file.fileName.isNotBlank()) add(file.fileName)
                                        if (file.sizeBytes > 0) add(Formatter.formatShortFileSize(context, file.sizeBytes))
                                        if (file.datePublished.isNotBlank()) add(file.datePublished)
                                    }.joinToString("  \u2022  "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AerixSurface.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = accent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.aerix_ic_download_2_outlined),
                                    contentDescription = "Download",
                                    tint = accent,
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

@Composable
private fun LinksRail(activity: FragmentActivity, details: AssetProjectDetails) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
    ) {
        Text(
            text = "Links",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            details.sourceUrl?.let { url ->
                AerixGhostButton("Source", R.drawable.aerix_ic_terminal_outlined, { PojavNavigator.openUrl(activity, url) })
            }
            details.wikiUrl?.let { url ->
                AerixGhostButton("Wiki", R.drawable.aerix_ic_description_outlined, { PojavNavigator.openUrl(activity, url) })
            }
            details.issuesUrl?.let { url ->
                AerixGhostButton("Issues", R.drawable.aerix_ic_warning_outlined, { PojavNavigator.openUrl(activity, url) })
            }
            details.discordUrl?.let { url ->
                AerixGhostButton("Discord", R.drawable.aerix_ic_link, { PojavNavigator.openUrl(activity, url) })
            }
        }
    }
}

/**
 * The picker for the two long filter lists of this screen - every Minecraft version the source
 * knows, and every category it has.
 *
 * It is a dialog and not a dropdown menu on purpose. A dropdown menu measures its content for the
 * intrinsic size it sizes itself by, and a lazy list cannot answer that: the framework threw
 * "Asking for intrinsic measurements of SubcomposeLayout layouts is not supported" the moment one
 * was opened, which took the app down. A dialog measures its content the ordinary way, so a list of
 * several hundred entries can be laid out lazily inside it and searched.
 */
@Composable
private fun FacetPicker(
    title: String,
    allLabel: String,
    facets: List<AssetFacet>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    var filterText by remember { mutableStateOf("") }
    val matches = remember(facets, filterText) {
        if (filterText.isBlank()) facets
        else facets.filter { it.label.contains(filterText.trim(), ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 300.dp, max = 460.dp)
                    .clip(RoundedCornerShape(AerixRadii.dialog))
                    .background(AerixSurface.panelRaised)
                    // Swallows the taps that land on the panel instead of the scrim.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
                    .padding(vertical = AerixSpacing.smCompact)
            ) {
                Text(
                    text = title.uppercase(),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.xxs)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.smCompact)
                        .clip(RoundedCornerShape(AerixRadii.control))
                        .background(AerixSurface.panelTrack)
                        .padding(horizontal = AerixSpacing.smTight, vertical = AerixSpacing.smCompact)
                ) {
                    if (filterText.isEmpty()) {
                        Text(
                            text = "Search...",
                            fontSize = 12.sp,
                            color = AerixSurface.textMuted
                        )
                    }
                    BasicTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        singleLine = true,
                        textStyle = TextStyle(color = AerixSurface.textPrimary, fontSize = 12.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                FacetPickerRow(
                    label = allLabel,
                    selected = selectedId == null,
                    accent = accent,
                    onClick = { onPick(null) }
                )
                HorizontalDivider(color = AerixSurface.borderSoft)
                if (matches.isEmpty()) {
                    Text(
                        text = "Nothing matches",
                        fontSize = 12.sp,
                        color = AerixSurface.textMuted,
                        modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.smTight)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items(matches, key = { it.id }) { facet ->
                            FacetPickerRow(
                                label = facet.label,
                                selected = facet.id == selectedId,
                                accent = accent,
                                onClick = { onPick(facet.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One row of [FacetPicker]: the label, and the accent and a tick when it is the picked one. */
@Composable
private fun FacetPickerRow(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
            .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.smTight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
    ) {
        Text(
            text = label,
            color = if (selected) accent else AerixSurface.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_check),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
