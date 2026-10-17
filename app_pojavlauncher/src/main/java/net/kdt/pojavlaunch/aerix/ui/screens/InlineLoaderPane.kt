package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixLoader
import net.kdt.pojavlaunch.aerix.bridge.AerixLoaderVersion
import net.kdt.pojavlaunch.aerix.bridge.PojavCreateInstanceBridge
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.ModrinthCompactSearchField
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface

/** All loader selections stay inside the instance editor, including OptiFine and BTA. */
@Composable
internal fun InlineLoaderPane(activity: FragmentActivity, currentVersion: String, onBackToEditor: (() -> Unit)? = null) {
    val loaders = listOf(AerixLoader.FABRIC, AerixLoader.NEOFORGE, AerixLoader.FORGE,
        AerixLoader.QUILT, AerixLoader.LEGACY_FABRIC, AerixLoader.OPTIFINE, AerixLoader.BTA)
    var loader by rememberSaveable { mutableStateOf(AerixLoader.FABRIC) }
    var gameVersion by remember(loader) { mutableStateOf<String?>(null) }
    var games by remember(loader) { mutableStateOf(emptyList<String>()) }
    var builds by remember(loader, gameVersion) { mutableStateOf(emptyList<AerixLoaderVersion>()) }
    var selectedBuild by remember(loader, gameVersion) { mutableStateOf<AerixLoaderVersion?>(null) }
    var group by rememberSaveable { mutableStateOf("Supported") }
    var onlyStable by rememberSaveable { mutableStateOf(true) }
    var gameQuery by rememberSaveable(loader) { mutableStateOf("") }
    var buildQuery by rememberSaveable(loader) { mutableStateOf("") }
    var loadingGames by remember(loader) { mutableStateOf(false) }
    var loadingBuilds by remember(loader, gameVersion) { mutableStateOf(false) }
    var gamesError by remember(loader) { mutableStateOf<String?>(null) }
    var buildsError by remember(loader, gameVersion) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var chooseLoader by remember { mutableStateOf(false) }
    var chooseVersion by remember(loader) { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val revision = PojavCreateInstanceBridge.versionTableRevision

    LaunchedEffect(loader, revision, retry) {
        gamesError = null
        if (!loader.usesGameVersion) return@LaunchedEffect
        loadingGames = true
        try {
            val available = withContext(Dispatchers.IO) {
                PojavCreateInstanceBridge.loaderGameVersions(loader)
            }
            games = available
            if (gameVersion !in available) {
                gameVersion = available.firstOrNull { it == currentVersion || currentVersion.endsWith("-$it") || currentVersion.startsWith("$it-") }
                    ?: available.firstOrNull()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            gamesError = e.localizedMessage ?: "Could not load Minecraft versions"
        } finally {
            loadingGames = false
        }
    }

    LaunchedEffect(loader, gameVersion, retry) {
        selectedBuild = null
        builds = emptyList()
        buildsError = null
        if (loader.usesGameVersion && gameVersion == null) return@LaunchedEffect
        loadingBuilds = true
        try {
            builds = withContext(Dispatchers.IO) {
                PojavCreateInstanceBridge.loadLoaderVersions(loader, gameVersion)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            buildsError = e.localizedMessage ?: "Could not load loader builds"
        } finally {
            loadingBuilds = false
        }
    }

    val visibleBuilds = builds.filter {
        (if (loader == AerixLoader.BTA) it.group == group else !onlyStable || it.stable) &&
            it.label.contains(buildQuery, ignoreCase = true)
    }
    LaunchedEffect(visibleBuilds) {
        if (selectedBuild !in visibleBuilds) selectedBuild = visibleBuilds.firstOrNull()
    }
    val installing = PojavCreateInstanceBridge.installing
    val canInstall = !installing && !loadingGames && !loadingBuilds &&
        gamesError == null && buildsError == null && selectedBuild in visibleBuilds && selectedBuild != null

    if (chooseLoader) {
        AlertDialog(
            onDismissRequest = { chooseLoader = false },
            title = { Text("Choose modloader") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(loaders) { option ->
                        OutlinedButton(onClick = { loader = option; chooseLoader = false },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !installing) {
                            Icon(painterResource(option.iconRes), null, Modifier.size(24.dp), tint = Color.Unspecified)
                            Spacer(Modifier.width(16.dp))
                            Text(option.displayName, Modifier.weight(1f))
                            if (loader == option) Text("Selected")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseLoader = false }) { Text("Close") } }
        )
    }
    if (showFilters) {
        AlertDialog(onDismissRequest = { showFilters = false }, title = { Text("Build filters") }, text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Stable builds only", Modifier.weight(1f))
                Switch(checked = onlyStable, onCheckedChange = { onlyStable = it })
            }
        }, confirmButton = { TextButton(onClick = { showFilters = false }) { Text("Done") } })
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 620.dp && maxHeight >= 380.dp
        val showLoaderIcons = maxWidth >= 420.dp
        val shortViewport = maxHeight < 300.dp
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(if (shortViewport) 6.dp else 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBackToEditor != null) IconButton(onClick = onBackToEditor, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.aerix_ic_arrow_back), "Back to instance", tint = AerixSurface.textPrimary)
                }
                OutlinedButton(onClick = { chooseLoader = true }, enabled = !installing,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    if (showLoaderIcons) {
                        Icon(painterResource(loader.iconRes), null, Modifier.size(22.dp), tint = Color.Unspecified)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(loader.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!wide) {
                    OutlinedButton(onClick = { chooseVersion = !chooseVersion }, enabled = !installing,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(if (chooseVersion) "Back to builds" else if (loader == AerixLoader.BTA) group
                            else "MC ${gameVersion ?: "Choose"}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (wide || chooseVersion) {
                    LoaderListPanel(
                        title = if (loader == AerixLoader.BTA) "BTA channel" else "Minecraft version",
                        query = gameQuery, onQuery = { gameQuery = it },
                        entries = if (loader == AerixLoader.BTA) listOf("Supported", "Untested", "Nightly")
                            else games.filter { it.contains(gameQuery, ignoreCase = true) },
                        selected = if (loader == AerixLoader.BTA) group else gameVersion,
                        onSelect = {
                            if (!installing) {
                                if (loader == AerixLoader.BTA) group = it else gameVersion = it
                                chooseVersion = false
                            }
                        },
                        loading = loadingGames, error = gamesError, onRetry = { retry++ },
                        modifier = Modifier.weight(1f).fillMaxHeight(), showSearch = loader != AerixLoader.BTA
                    )
                }
                if (wide || !chooseVersion) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ModrinthCompactSearchField(buildQuery, { buildQuery = it }, "Search ${loader.displayName} builds", Modifier.weight(1f))
                            if (loader != AerixLoader.BTA) {
                                TextButton(onClick = { showFilters = true }) { Text(if (onlyStable) "Stable" else "All builds") }
                            }
                        }
                        LoaderListBody(
                            entries = visibleBuilds.map { it.id to it.label }, selected = selectedBuild?.id,
                            onSelect = { id -> if (!installing) selectedBuild = visibleBuilds.firstOrNull { it.id == id } },
                            loading = loadingBuilds || loadingGames, error = buildsError ?: gamesError,
                            onRetry = { retry++ }, modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            AerixPrimaryButton(if (installing) "Installing…" else "Install selected build", R.drawable.aerix_ic_download,
                modifier = Modifier.fillMaxWidth(), enabled = canInstall && !chooseVersion,
                onClick = {
                    val build = selectedBuild ?: return@AerixPrimaryButton
                    PojavCreateInstanceBridge.install(activity, loader, gameVersion, build,
                        PojavCreateInstanceBridge.suggestedName(loader, gameVersion, build), null) {}
                })
            if (!shortViewport) Text("Creates a new instance; your current instance is unchanged.",
                color = AerixSurface.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LoaderListPanel(
    title: String, query: String, onQuery: (String) -> Unit, entries: List<String>, selected: String?,
    onSelect: (String) -> Unit, loading: Boolean, error: String?, onRetry: () -> Unit,
    modifier: Modifier = Modifier, showSearch: Boolean = true
) {
    Surface(modifier, shape = RoundedCornerShape(AerixRadii.panel), color = AerixSurface.panel,
        border = BorderStroke(1.dp, AerixSurface.borderSoft)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold)
            if (showSearch) ModrinthCompactSearchField(query, onQuery, "Search versions", Modifier.fillMaxWidth())
            LoaderListBody(entries.map { it to it }, selected, onSelect, loading, error, onRetry, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoaderListBody(
    entries: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit,
    loading: Boolean, error: String?, onRetry: () -> Unit, modifier: Modifier = Modifier
) {
    when {
        loading -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        error != null -> Column(modifier) {
            Text(error, color = AerixSurface.textSecondary)
            TextButton(onClick = onRetry) { Text("Retry") }
        }
        entries.isEmpty() -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No matching versions", color = AerixSurface.textSecondary)
                TextButton(onClick = onRetry) { Text("Refresh") }
            }
        }
        else -> LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(entries, key = { it.first }) { (id, label) ->
                val checked = selected == id
                Surface(onClick = { onSelect(id) }, shape = RoundedCornerShape(AerixRadii.control),
                    color = if (checked) AerixSurface.accent.copy(alpha = 0.16f) else AerixSurface.panelRaised,
                    border = BorderStroke(1.dp, if (checked) AerixSurface.accent else AerixSurface.borderSoft)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(checked, onClick = null)
                        Text(label, color = AerixSurface.textPrimary, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
