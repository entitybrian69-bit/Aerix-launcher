package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import net.kdt.pojavlaunch.aerix.bridge.*
import net.kdt.pojavlaunch.aerix.theme.AerixSurface

/** A scrollable, searchable catalog rather than a short hard-coded dropdown. */
@Composable
internal fun MinecraftVersionPicker(selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    var versions by remember { mutableStateOf<List<AssetGameVersion>>(emptyList()) }
    var kind by rememberSaveable { mutableStateOf(MinecraftVersionKind.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var showTypes by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val revision = PojavCreateInstanceBridge.versionTableRevision
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f), shape = RoundedCornerShape(20.dp), color = AerixSurface.panelRaised) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Minecraft versions", Modifier.weight(1f), color = AerixSurface.textPrimary, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box {
                        OutlinedButton(onClick = { showTypes = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(kind.label) }
                        DropdownMenu(showTypes, { showTypes = false }) {
                            MinecraftVersionKind.entries.forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) }, onClick = { kind = option; showTypes = false })
                            }
                        }
                    }
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("Search / exact version") }, singleLine = true)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(error.orEmpty(), Modifier.weight(1f), color = AerixSurface.textSecondary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { retry++ }) { Text("Retry") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { OutlinedButton(onClick = { onSelect(null); onDismiss() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("All versions · no version restriction") } }
                    items(visible, key = { it.id }) { version ->
                        OutlinedButton(onClick = { onSelect(version.id); onDismiss() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(version.id + if (version.id == selected) " · Selected" else "", Modifier.weight(1f))
                            Text(version.kind.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (visible.isEmpty() && !busy) item { Text("No matching versions. Try All types or enter the exact version.", color = AerixSurface.textSecondary) }
                    val exact = query.trim()
                    if (exact.length in 1..80 && Regex("[a-zA-Z0-9._+\\-]+").matches(exact) && versions.none { it.id == exact }) {
                        item { OutlinedButton(onClick = { onSelect(exact); onDismiss() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Use exact version: $exact") } }
                    }
                }
                Text("Snapshot includes pre-releases and release candidates. Content must still be published for the chosen version.", color = AerixSurface.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
