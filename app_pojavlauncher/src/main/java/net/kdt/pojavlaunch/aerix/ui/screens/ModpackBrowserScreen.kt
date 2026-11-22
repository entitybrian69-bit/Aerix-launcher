package net.kdt.pojavlaunch.aerix.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed as lazyListItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import net.kdt.pojavlaunch.aerix.theme.AerixLauncherTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.bridge.AerixWindowInsets
import net.kdt.pojavlaunch.aerix.components.AerixBackground
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.GlassPanel
import net.kdt.pojavlaunch.aerix.components.PillTabs
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ImageReceiver
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult

private enum class ModpackSourceFilter(val label: String) {
    ALL("All"),
    MODRINTH("Modrinth"),
    CURSEFORGE("CurseForge")
}

@Composable
fun ModpackBrowserScreen(
    modpackApi: ModpackApi,
    tasksRunning: Boolean,
    onImportLocal: () -> Unit,
    onPickMcVersion: (current: String?, onPicked: (String?) -> Unit) -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val iconCache = remember { ModIconCache() }

    var query by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf(ModpackSourceFilter.ALL) }
    var mcVersion by remember { mutableStateOf<String?>(null) }

    var allItems by remember { mutableStateOf<List<ModItem>>(emptyList()) }
    var currentResult by remember { mutableStateOf<SearchResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var isLastPage by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    var selectedItem by remember { mutableStateOf<ModItem?>(null) }

    fun runSearch(resetQuery: String = query, version: String? = mcVersion) {
        searchJob?.cancel()
        isLoading = true
        isLoadingMore = false
        isLastPage = false
        errorMessage = null
        currentResult = null
        allItems = emptyList()
        val filters = SearchFilters().apply {
            isModpack = true
            name = resetQuery.trim()
            this.mcVersion = version?.takeIf { it.isNotBlank() }
        }
        searchJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { modpackApi.searchMod(filters, null) }.getOrNull()
            }
            isLoading = false
            if (result == null) {
                errorMessage = context.getString(R.string.search_modpack_error)
                return@launch
            }
            val page = result.results?.toList().orEmpty()
            currentResult = result
            allItems = page
            isLastPage = page.isEmpty() || page.size >= result.totalResultCount
            if (page.isEmpty()) {
                errorMessage = context.getString(R.string.search_modpack_no_result)
            }
        }
    }

    fun loadNextPage() {
        val prev = currentResult ?: return
        if (isLoading || isLoadingMore || isLastPage) return
        isLoadingMore = true
        val filters = SearchFilters().apply {
            isModpack = true
            name = query.trim()
            this.mcVersion = mcVersion?.takeIf { it.isNotBlank() }
        }
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { modpackApi.searchMod(filters, prev) }.getOrNull()
            }
            isLoadingMore = false
            if (next == null) {
                isLastPage = true
                return@launch
            }
            val appended = next.results?.toList().orEmpty()
            currentResult = next
            if (appended.isEmpty()) {
                isLastPage = true
            } else {
                val existingKeys = allItems.mapTo(HashSet()) { "${it.apiSource}_${it.id}" }
                val fresh = appended.filter { existingKeys.add("${it.apiSource}_${it.id}") }
                allItems = allItems + fresh
                if (allItems.size >= next.totalResultCount || fresh.isEmpty()) {
                    isLastPage = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        runSearch("", null)
    }

    val filteredItems = remember(allItems, sourceFilter) {
        when (sourceFilter) {
            ModpackSourceFilter.ALL -> allItems
            ModpackSourceFilter.MODRINTH -> allItems.filter { it.apiSource == Constants.SOURCE_MODRINTH }
            ModpackSourceFilter.CURSEFORGE -> allItems.filter { it.apiSource == Constants.SOURCE_CURSEFORGE }
        }
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, filteredItems.size, isLastPage, isLoading, isLoadingMore) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= (info.totalItemsCount - 3).coerceAtLeast(0)
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && !isLoading && !isLoadingMore && !isLastPage && allItems.isNotEmpty()) {
                loadNextPage()
            }
        }
    }

    AerixBackground {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(AerixWindowInsets.padding())
                .padding(horizontal = AerixSpacing.lg, vertical = AerixSpacing.md)
        ) {
            val wideBrand = maxWidth >= 780.dp
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
            ) {
                // Top bar matching the selected horizontal glass design
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_aerix_mark),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        if (wideBrand) {
                            Column {
                                Text(
                                    text = "Aerix",
                                    color = AerixSurface.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Modpacks",
                                    color = AerixSurface.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Search pill
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .liquidGlass(shape = CircleShape, strength = 0.7f),
                        shape = CircleShape,
                        color = AerixSurface.panel.copy(alpha = 0.58f),
                        border = BorderStroke(1.dp, AerixSurface.borderSoft)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = AerixSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_search),
                                contentDescription = null,
                                tint = AerixSurface.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.hint_search_modpack),
                                        color = AerixSurface.textMuted,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = AerixSurface.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(AerixSurface.accent),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        focusManager.clearFocus()
                                        runSearch(query, mcVersion)
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
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            query = ""
                                            runSearch("", mcVersion)
                                        }
                                )
                            }
                        }
                    }

                    // Source filter pills: All | Modrinth | CurseForge
                    PillTabs(
                        tabs = ModpackSourceFilter.entries.map { it.label },
                        selected = sourceFilter.ordinal,
                        onSelect = { idx ->
                            sourceFilter = ModpackSourceFilter.entries[idx]
                        }
                    )

                    // Optional MC version filter chip
                    Surface(
                        modifier = Modifier
                            .height(40.dp)
                            .liquidGlass(shape = CircleShape, strength = 0.6f),
                        shape = CircleShape,
                        color = if (mcVersion != null) AerixSurface.accentContainer else AerixSurface.panel.copy(alpha = 0.55f),
                        border = BorderStroke(
                            1.dp,
                            if (mcVersion != null) AerixSurface.accent.copy(alpha = 0.7f) else AerixSurface.borderSoft
                        ),
                        onClick = {
                            if (mcVersion != null) {
                                mcVersion = null
                                runSearch(query, null)
                            } else {
                                onPickMcVersion(mcVersion) { picked ->
                                    val clean = picked?.takeIf { it.isNotBlank() }
                                    mcVersion = clean
                                    runSearch(query, clean)
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AerixSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = mcVersion ?: "MC: All",
                                color = if (mcVersion != null) AerixSurface.accent else AerixSurface.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (mcVersion != null) {
                                Icon(
                                    painter = painterResource(R.drawable.aerix_ic_close),
                                    contentDescription = null,
                                    tint = AerixSurface.accent,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    // Import Local primary pill button
                    AerixPrimaryButton(
                        text = "Import Local",
                        iconRes = R.drawable.aerix_ic_download,
                        enabled = !tasksRunning,
                        onClick = onImportLocal,
                        modifier = Modifier.height(40.dp)
                    )

                    // Frosted circular Home button
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .liquidGlass(shape = CircleShape, tint = AerixSurface.accent, strength = 0.7f),
                        shape = CircleShape,
                        color = AerixSurface.panelRaised.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, AerixSurface.accent.copy(alpha = 0.6f)),
                        onClick = onHome
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_home_filled),
                                contentDescription = stringResource(R.string.aerix_nav_back_home),
                                tint = AerixSurface.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Content area: 2-column smoky glass card grid
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        isLoading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                GlassPanel(contentPadding = AerixSpacing.lg) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.5.dp,
                                            color = AerixSurface.accent
                                        )
                                        Text(
                                            text = "Searching modpacks…",
                                            color = AerixSurface.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                        filteredItems.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                GlassPanel(contentPadding = AerixSpacing.xl) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                                    ) {
                                        Text(
                                            text = errorMessage ?: stringResource(R.string.search_modpack_no_result),
                                            color = if (errorMessage != null && allItems.isEmpty()) AerixSurface.warning else AerixSurface.textSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                                            if (sourceFilter != ModpackSourceFilter.ALL && allItems.isNotEmpty()) {
                                                AerixGhostButton(
                                                    text = "Show All",
                                                    iconRes = null,
                                                    onClick = { sourceFilter = ModpackSourceFilter.ALL }
                                                )
                                            }
                                            AerixGhostButton(
                                                text = "Retry",
                                                iconRes = R.drawable.aerix_ic_refresh,
                                                onClick = { runSearch(query, mcVersion) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minSize = 310.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = AerixSpacing.md),
                                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                            ) {
                                itemsIndexed(
                                    items = filteredItems,
                                    key = { _, item -> "${item.apiSource}_${item.id}" }
                                ) { _, item ->
                                    ModpackCard(
                                        item = item,
                                        iconCache = iconCache,
                                        onClick = { selectedItem = item }
                                    )
                                }
                                if (isLoadingMore) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = AerixSpacing.sm),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = AerixSurface.accent
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
    }

    selectedItem?.let { item ->
        ModpackDetailDialog(
            item = item,
            modpackApi = modpackApi,
            iconCache = iconCache,
            tasksRunning = tasksRunning,
            onDismiss = { selectedItem = null },
            onInstall = { detail, versionIdx ->
                selectedItem = null
                modpackApi.handleModpackInstallation(context.applicationContext, detail, versionIdx)
            }
        )
    }
}

@Composable
private fun ModpackCard(
    item: ModItem,
    iconCache: ModIconCache,
    onClick: () -> Unit
) {
    val thumb = rememberModIcon(iconCache, item.iconCacheTag, item.imageUrl)
    val isModrinth = item.apiSource == Constants.SOURCE_MODRINTH
    GlassPanel(
        radius = AerixRadii.card,
        contentPadding = AerixSpacing.md,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(AerixRadii.control))
                    .background(AerixSurface.canvas.copy(alpha = 0.7f))
                    .border(BorderStroke(1.dp, AerixSurface.borderSoft), RoundedCornerShape(AerixRadii.control)),
                contentAlignment = Alignment.Center
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(if (isModrinth) R.drawable.ic_modrinth else R.drawable.ic_curseforge),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title.orEmpty(),
                    color = AerixSurface.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.description.orEmpty(),
                    color = AerixSurface.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Image(
                        painter = painterResource(if (isModrinth) R.drawable.ic_modrinth else R.drawable.ic_curseforge),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isModrinth) "Modrinth" else "CurseForge",
                        color = if (isModrinth) AerixSurface.modrinthBrand else AerixSurface.curseForgeBrand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Icon(
                painter = painterResource(R.drawable.aerix_ic_chevron_right),
                contentDescription = null,
                tint = AerixSurface.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ModpackDetailDialog(
    item: ModItem,
    modpackApi: ModpackApi,
    iconCache: ModIconCache,
    tasksRunning: Boolean,
    onDismiss: () -> Unit,
    onInstall: (ModDetail, Int) -> Unit
) {
    var loading by remember(item) { mutableStateOf(true) }
    var detail by remember(item) { mutableStateOf<ModDetail?>(null) }
    var selectedIdx by remember(item) { mutableIntStateOf(0) }
    val thumb = rememberModIcon(iconCache, item.iconCacheTag, item.imageUrl)
    val isModrinth = item.apiSource == Constants.SOURCE_MODRINTH

    LaunchedEffect(item) {
        loading = true
        detail = withContext(Dispatchers.IO) {
            runCatching { modpackApi.getModDetails(item) }.getOrNull()
        }
        selectedIdx = 0
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            radius = AerixRadii.panel,
            contentPadding = AerixSpacing.lg,
            color = AerixSurface.canvas.copy(alpha = 0.92f),
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(AerixRadii.control))
                        .background(AerixSurface.panelRaised)
                        .border(BorderStroke(1.dp, AerixSurface.borderSoft), RoundedCornerShape(AerixRadii.control)),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(if (isModrinth) R.drawable.ic_modrinth else R.drawable.ic_curseforge),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = item.title.orEmpty(),
                        color = AerixSurface.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Image(
                            painter = painterResource(if (isModrinth) R.drawable.ic_modrinth else R.drawable.ic_curseforge),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isModrinth) "Modrinth" else "CurseForge",
                            color = AerixSurface.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (!item.description.isNullOrBlank()) {
                Text(
                    text = item.description.orEmpty(),
                    color = AerixSurface.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "SELECT VERSION",
                color = AerixSurface.textSecondary,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp, max = 180.dp)
                    .clip(RoundedCornerShape(AerixRadii.control))
                    .background(AerixSurface.panel.copy(alpha = 0.55f))
                    .border(BorderStroke(1.dp, AerixSurface.borderSoft), RoundedCornerShape(AerixRadii.control))
            ) {
                when {
                    loading -> {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AerixSurface.accent
                            )
                            Text("Loading versions…", color = AerixSurface.textSecondary, fontSize = 12.sp)
                        }
                    }
                    detail == null || detail?.versionNames.isNullOrEmpty() -> {
                        Text(
                            text = stringResource(R.string.search_modpack_download_error),
                            color = AerixSurface.warning,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(AerixSpacing.md)
                        )
                    }
                    else -> {
                        val versions = detail!!.versionNames.toList()
                        LazyColumn(
                            contentPadding = PaddingValues(AerixSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            lazyListItemsIndexed(versions) { idx, name ->
                                val picked = idx == selectedIdx
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(AerixRadii.compact),
                                    color = if (picked) AerixSurface.accentContainer else Color.Transparent,
                                    border = if (picked) BorderStroke(1.dp, AerixSurface.accent.copy(alpha = 0.7f)) else null,
                                    onClick = { selectedIdx = idx }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = name,
                                            color = if (picked) AerixSurface.accent else AerixSurface.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (picked) {
                                            Icon(
                                                painter = painterResource(R.drawable.aerix_ic_check),
                                                contentDescription = null,
                                                tint = AerixSurface.accent,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm, Alignment.End)
            ) {
                AerixGhostButton(
                    text = stringResource(android.R.string.cancel),
                    iconRes = null,
                    onClick = onDismiss
                )
                AerixPrimaryButton(
                    text = stringResource(R.string.generic_install),
                    iconRes = R.drawable.aerix_ic_download,
                    enabled = !loading && detail != null && !detail?.versionNames.isNullOrEmpty() && !tasksRunning,
                    onClick = { detail?.let { onInstall(it, selectedIdx) } }
                )
            }
        }
    }
}

@Composable
private fun rememberModIcon(iconCache: ModIconCache, tag: String, url: String?): ImageBitmap? {
    var bitmap by remember(tag, url) { mutableStateOf<ImageBitmap?>(null) }
    DisposableEffect(tag, url) {
        if (url.isNullOrBlank()) return@DisposableEffect onDispose {}
        var active = true
        val receiver = ImageReceiver { bm: Bitmap? ->
            if (bm != null) {
                Tools.runOnUiThread {
                    if (active) bitmap = bm.asImageBitmap()
                }
            }
        }
        iconCache.getImage(receiver, tag, url)
        onDispose {
            active = false
            iconCache.cancelImage(receiver)
        }
    }
    return bitmap
}

object ModpackBrowserHost {
    fun interface VersionPickedCallback {
        fun onPicked(version: String?)
    }

    fun interface VersionPicker {
        fun pick(current: String?, callback: VersionPickedCallback)
    }

    @JvmStatic
    fun newTasksRunningState(initial: Boolean): MutableState<Boolean> = mutableStateOf(initial)

    @JvmStatic
    fun createView(
        fragment: Fragment,
        modpackApi: ModpackApi,
        tasksRunningState: MutableState<Boolean>,
        onImportLocal: Runnable,
        onPickMcVersion: VersionPicker,
        onHome: Runnable
    ): ComposeView {
        val activity = fragment.requireActivity()
        activity.window.decorView.let { decor ->
            if (decor.findViewTreeLifecycleOwner() == null) decor.setViewTreeLifecycleOwner(activity)
            if (decor.findViewTreeViewModelStoreOwner() == null) decor.setViewTreeViewModelStoreOwner(activity)
            if (decor.findViewTreeSavedStateRegistryOwner() == null) decor.setViewTreeSavedStateRegistryOwner(activity)
            if (decor.findViewTreeOnBackPressedDispatcherOwner() == null) decor.setViewTreeOnBackPressedDispatcherOwner(activity)
        }
        return ComposeView(fragment.requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AerixLauncherTheme {
                    ModpackBrowserScreen(
                        modpackApi = modpackApi,
                        tasksRunning = tasksRunningState.value,
                        onImportLocal = { onImportLocal.run() },
                        onPickMcVersion = { current, cb ->
                            onPickMcVersion.pick(current) { picked -> cb(picked) }
                        },
                        onHome = { onHome.run() }
                    )
                }
            }
        }
    }
}

