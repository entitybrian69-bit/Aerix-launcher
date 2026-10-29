package net.kdt.pojavlaunch.aerix.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.aerix.bridge.*
import net.kdt.pojavlaunch.aerix.theme.AerixSurface

@Stable
internal class ContentSelectionState {
    var selecting by mutableStateOf(false)
    var selected by mutableStateOf<Set<String>>(emptySet())
    var busy by mutableStateOf(false)
    var pendingDelete by mutableStateOf<List<ContentEntry>?>(null)
    fun toggle(entry: ContentEntry) { if (!busy) selected = ContentListPolicy.toggle(selected, entry.file.absolutePath) }
    fun done() { selecting = false; selected = emptySet() }
}

/** Shared by Mods, Packs, Shaders, Worlds and Screenshots; selection never changes a row's key. */
@Composable
internal fun ContentSelectionControls(
    kind: ContentKind,
    entries: List<ContentEntry>,
    visible: List<ContentEntry>,
    state: ContentSelectionState,
    loading: Boolean,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val targets = entries.filter { it.file.absolutePath in state.selected }
    val hidden = targets.count { entry -> visible.none { it.file.absolutePath == entry.file.absolutePath } }
    LaunchedEffect(entries) {
        state.selected = state.selected.intersect(entries.map { it.file.absolutePath }.toSet())
    }
    BackHandler(enabled = state.selecting || state.busy) { if (!state.busy) state.done() }
    fun execute(action: ContentBulkAction, snapshot: List<ContentEntry>) {
        if (state.busy || snapshot.isEmpty()) return
        state.busy = true
        scope.launch {
            try {
                val result = applyContentBatch(kind, snapshot, action)
                state.selected = (state.selected - result.succeeded) + result.failed
                if (result.failed.isNotEmpty()) state.selecting = true
                Toast.makeText(context, if (result.failed.isEmpty()) "${result.succeeded.size} item(s) updated"
                    else "${result.succeeded.size} succeeded · ${result.failed.size} failed; failed items remain selected", Toast.LENGTH_LONG).show()
                onChanged()
            } finally { state.busy = false }
        }
    }
    // A fixed-height row avoids moving the viewport just by entering selection mode.
    Row(Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!state.selecting) {
            TextButton(onClick = { state.selecting = true }, enabled = !loading && !state.busy && entries.isNotEmpty()) { Text("Select items") }
        } else {
            Text(if (hidden == 0) "${targets.size} selected" else "${targets.size} selected · $hidden hidden",
                color = AerixSurface.textPrimary, style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { state.selected = state.selected + visible.map { it.file.absolutePath } }, enabled = !state.busy && !loading) { Text("Select visible") }
            TextButton(onClick = { state.selected = emptySet() }, enabled = !state.busy && targets.isNotEmpty()) { Text("Clear") }
            if (kind.toggleable) {
                TextButton(onClick = { execute(ContentBulkAction.ENABLE, targets) }, enabled = !state.busy && !loading && targets.isNotEmpty()) { Text("Enable") }
                TextButton(onClick = { execute(ContentBulkAction.DISABLE, targets) }, enabled = !state.busy && !loading && targets.isNotEmpty()) { Text("Disable") }
            }
            TextButton(onClick = { state.pendingDelete = targets.toList() }, enabled = !state.busy && !loading && targets.isNotEmpty()) { Text("Delete", color = AerixSurface.danger) }
            TextButton(onClick = { state.done() }, enabled = !state.busy) { Text("Done") }
        }
        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
    val pending = state.pendingDelete
    if (pending != null) AlertDialog(
        onDismissRequest = { state.pendingDelete = null },
        title = { Text("Delete ${pending.size} item(s)?") },
        text = { Text(pending.take(5).joinToString("\n") { it.displayTitle } +
            (if (pending.size > 5) "\n…and ${pending.size - 5} more" else "") +
            "\n\nThis permanently deletes these files" + (if (kind == ContentKind.SAVES) " and all data in these worlds. Back up important saves first." else ". This cannot be undone.")) },
        confirmButton = { TextButton(onClick = { state.pendingDelete = null; execute(ContentBulkAction.DELETE, pending) }) { Text("Delete", color = AerixSurface.danger) } },
        dismissButton = { TextButton(onClick = { state.pendingDelete = null }) { Text("Cancel") } }
    )
}
