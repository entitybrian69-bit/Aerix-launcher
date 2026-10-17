package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import net.kdt.pojavlaunch.aerix.bridge.*
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

/** An inline content-browser workspace. No dialog, separate window or nested type menu. */
@Composable
internal fun MinecraftVersionPicker(
    selected: String?,
    kind: MinecraftVersionKind,
    onKindChange: (MinecraftVersionKind) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    var versions by remember { mutableStateOf<List<AssetGameVersion>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val revision = PojavCreateInstanceBridge.versionTableRevision
    val grid = rememberLazyGridState()
    val accent = AerixThemeManager.currentAccent()
    LaunchedEffect(retry, revision) {
        busy = true
        error = null
        val manifest = PojavCreateInstanceBridge.gameVersions()
            .filter { it.type != AerixVersionType.INSTALLED }
            .map { AssetGameVersion(it.id, MinecraftVersionFilters.classify(it.id, it.type.name.lowercase())) }
        versions = manifest.distinctBy { it.id }
        try {
            versions = (PojavAssetBridge.minecraftVersions() + manifest).distinctBy { it.id }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message
        } finally { busy = false }
    }
    val visible = remember(versions, query, kind) {
        versions.filter { MinecraftVersionFilters.matches(kind, it.kind) && it.id.contains(query.trim(), true) }
    }
    LaunchedEffect(kind, query) { grid.scrollToItem(0) }
    BoxWithConstraints(modifier) {
        val compact = maxHeight < 340.dp
        val sideFilters = maxWidth >= 600.dp && maxHeight < 400.dp
        Surface(
            Modifier.fillMaxSize(), shape = RoundedCornerShape(20.dp), color = AerixSurface.panel,
            border = BorderStroke(1.dp, AerixSurface.borderSoft)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(if (sideFilters) Modifier.width(150.dp) else Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (sideFilters) "Versions" else "Minecraft versions", color = AerixSurface.textPrimary,
                            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        if (!compact) Text("Current: ${selected ?: "All versions"}", color = AerixSurface.textSecondary,
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (sideFilters) VersionSearch(query, onQueryChange, Modifier.weight(1f))
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") }
                }
                if (!sideFilters) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MinecraftVersionKind.entries.forEach { option ->
                            FilterChip(selected = kind == option, onClick = { onKindChange(option) },
                                label = { Text(option.label) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    VersionSearch(query, onQueryChange, Modifier.fillMaxWidth())
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = accent)
                if (error != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(error.orEmpty(), Modifier.weight(1f), color = AerixSurface.textSecondary,
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { retry++ }) { Text("Retry") }
                }
                val choose: (String?) -> Unit = { onSelect(it); onDismiss() }
                if (sideFilters) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LazyColumn(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(MinecraftVersionKind.entries) { option ->
                                FilterChip(selected = kind == option, onClick = { onKindChange(option) },
                                    label = { Text(option.label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                            }
                        }
                        VersionResults(visible, selected, query, versions, busy, grid, choose, Modifier.weight(1f).fillMaxHeight())
                    }
                } else VersionResults(visible, selected, query, versions, busy, grid, choose, Modifier.weight(1f))
                if (!compact) Text("${visible.size} matching versions · Snapshot includes pre-releases and release candidates.",
                    color = AerixSurface.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VersionSearch(query: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = query, onValueChange = onChange, modifier = modifier,
        placeholder = { Text("Search / exact version", maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
        trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text("Clear") } }
    )
}

@Composable
private fun VersionResults(
    visible: List<AssetGameVersion>, selected: String?, query: String, versions: List<AssetGameVersion>,
    busy: Boolean, grid: LazyGridState, onSelect: (String?) -> Unit, modifier: Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp), state = grid, modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 4.dp)
    ) {
        item(key = "all") { VersionTile("All versions", "No version restriction", selected == null) { onSelect(null) } }
        items(visible, key = { "version:${it.id}" }) { version ->
            VersionTile(version.id, version.kind.label, version.id == selected) { onSelect(version.id) }
        }
        val exact = query.trim()
        if (exact.length in 1..80 && Regex("[a-zA-Z0-9._+\\-]+").matches(exact) && versions.none { it.id == exact }) {
            item(key = "exact") { VersionTile(exact, "Use exact version", exact == selected) { onSelect(exact) } }
        }
        if (visible.isEmpty() && !busy) item(key = "empty") {
            Text("No matches. Try All types or another search.", color = AerixSurface.textSecondary,
                modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VersionTile(title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).semantics { selected = isSelected },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) accent.copy(alpha = 0.14f) else AerixSurface.panelRaised,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) accent else AerixSurface.borderSoft)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = if (isSelected) accent else AerixSurface.textPrimary, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = AerixSurface.textSecondary, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
