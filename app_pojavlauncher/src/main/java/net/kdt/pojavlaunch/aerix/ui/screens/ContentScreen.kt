/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/ModsManagerScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/ResourcePackManageScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/ShadersManagerScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/SavesManagerScreen.kt
 * One page with a tab per content type of the selected Pojav instance: search, enable/disable
 * (mods), delete with confirmation, import from the system picker and open the folder.
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

import android.text.format.DateFormat
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.ContentEntry
import net.kdt.pojavlaunch.aerix.bridge.ContentKind
import net.kdt.pojavlaunch.aerix.bridge.PojavContentBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.PillTabs
import net.kdt.pojavlaunch.aerix.components.QuietEmptyState
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import java.util.Date

@Composable
fun ContentScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kinds = ContentKind.entries
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val kind = kinds[tab.coerceIn(0, kinds.size - 1)]
    var entries by remember { mutableStateOf<List<ContentEntry>?>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ContentEntry?>(null) }
    val accent = AerixThemeManager.currentAccent()
    val selected = PojavInstanceBridge.selected
    val failedText = stringResource(R.string.aerix_content_failed)
    val importedText = stringResource(R.string.aerix_content_imported)

    LaunchedEffect(kind, revision, selected?.source) {
        loading = true
        entries = PojavContentBridge.list(kind)
        loading = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var ok = 0
                uris.forEach { if (PojavContentBridge.import(context, kind, it)) ok++ }
                Toast.makeText(context, if (ok == uris.size) importedText else failedText, Toast.LENGTH_SHORT).show()
                revision++
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = AerixSurface.panelRaised,
            title = { Text(stringResource(R.string.aerix_content_delete), color = AerixSurface.textPrimary) },
            text = { Text(stringResource(R.string.aerix_content_delete_confirm, entry.name), color = AerixSurface.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        if (!PojavContentBridge.delete(entry)) Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
                        revision++
                    }
                }) { Text(stringResource(R.string.aerix_content_delete), color = AerixSurface.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary)
                }
            }
        )
    }

    AerixPage(
        title = stringResource(R.string.aerix_content_title),
        subtitle = selected?.let { stringResource(R.string.aerix_content_subtitle, it.name) } ?: stringResource(R.string.no_instance),
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                AerixGhostButton(stringResource(R.string.mcl_button_open_directory), R.drawable.aerix_ic_folder_outlined, {
                    PojavContentBridge.openFolder(activity, kind)
                })
                AerixGhostButton(stringResource(R.string.aerix_download_install), R.drawable.aerix_ic_download, {
                    AerixDownloadRequest.open(AssetType.entries.first { it.folder == kind }, navigate)
                })
                AerixPrimaryButton(stringResource(R.string.aerix_content_import), R.drawable.aerix_ic_add, {
                    picker.launch(kind.importMimeTypes)
                }, enabled = selected != null)
            }
        }
    ) {
        PillTabs(
            tabs = kinds.map { stringResource(it.titleRes) },
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.aerix_content_search), color = AerixSurface.textMuted) },
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

        val list = entries
        val shown = remember(list, query) {
            list.orEmpty().filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        }
        when {
            list == null -> QuietEmptyState(R.drawable.aerix_ic_warning_outlined, stringResource(R.string.no_instance))
            !loading && shown.isEmpty() -> QuietEmptyState(kind.iconRes, stringResource(R.string.aerix_content_empty))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                items(shown, key = { it.file.absolutePath }) { entry ->
                    ContentRow(
                        entry = entry,
                        kind = kind,
                        onToggle = { enabled ->
                            scope.launch {
                                if (!PojavContentBridge.setEnabled(entry, enabled)) {
                                    Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
                                }
                                revision++
                            }
                        },
                        onDelete = { pendingDelete = entry }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentRow(entry: ContentEntry, kind: ContentKind, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val accent = AerixThemeManager.currentAccent()
    val shape = RoundedCornerShape(AerixRadii.panelSmall)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AerixSurface.panelRaised.copy(alpha = 0.42f), shape)
            .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        Icon(
            painter = painterResource(if (entry.isDirectory && kind != ContentKind.SAVES) R.drawable.aerix_ic_folder_outlined else kind.iconRes),
            contentDescription = null,
            tint = if (entry.enabled) accent else AerixSurface.textMuted,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = if (entry.enabled) AerixSurface.textPrimary else AerixSurface.textMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (entry.enabled) null else TextDecoration.LineThrough
            )
            Text(
                text = Formatter.formatShortFileSize(context, entry.sizeBytes) + " · " +
                    DateFormat.getMediumDateFormat(context).format(Date(entry.lastModified)),
                color = AerixSurface.textSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        if (kind.toggleable) {
            Switch(
                checked = entry.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AerixSurface.onAccent,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = AerixSurface.textSecondary,
                    uncheckedTrackColor = AerixSurface.panelTrack
                )
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_delete_outlined),
                contentDescription = stringResource(R.string.aerix_content_delete),
                tint = AerixSurface.danger
            )
        }
    }
}
