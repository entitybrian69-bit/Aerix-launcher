/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/ui/FileManagerRootScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/ui/FmMainPage.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/ui/FmEditorScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/ui/FmTrashScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/viewmodel/FmClipboard.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/viewmodel/NavHistory.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/viewmodel/SortConfig.kt
 * File operations go through PojavFileBridge; roots are Pojav's own folders.
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.FileItem
import net.kdt.pojavlaunch.aerix.bridge.FileSort
import net.kdt.pojavlaunch.aerix.bridge.FileSortField
import net.kdt.pojavlaunch.aerix.bridge.PojavFileBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.TrashEntry
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.PillTabs
import net.kdt.pojavlaunch.aerix.components.QuietEmptyState
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import java.io.File
import java.util.Date

/** Survives section switches, like mirai's FmClipboard. */
private object FmClipboard {
    var files by mutableStateOf<List<File>>(emptyList())
    var move by mutableStateOf(false)
}

private class NameRequest(val titleRes: Int, val initial: String, val onConfirm: suspend (String) -> Boolean)

private fun fileIcon(item: File, directory: Boolean): Int {
    if (directory) return R.drawable.aerix_ic_folder_filled
    return when (item.extension.lowercase()) {
        "zip", "mrpack" -> R.drawable.aerix_ic_folder_zip_outlined
        "jar" -> R.drawable.aerix_ic_java
        "png", "jpg", "jpeg", "webp", "gif", "bmp" -> R.drawable.aerix_ic_photo_library_outlined
        "log" -> R.drawable.aerix_ic_terminal_outlined
        else -> if (PojavFileBridge.isText(item)) R.drawable.aerix_ic_description_outlined else R.drawable.aerix_ic_text_snippet_filled
    }
}

@Composable
private fun aerixFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AerixSurface.textPrimary,
    unfocusedTextColor = AerixSurface.textPrimary,
    focusedBorderColor = AerixThemeManager.currentAccent(),
    unfocusedBorderColor = AerixSurface.borderSoft,
    cursorColor = AerixThemeManager.currentAccent()
)

@Composable
fun FileManagerScreen(activity: FragmentActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()
    val selectedInstance = PojavInstanceBridge.selected
    val roots = remember(selectedInstance?.source) { PojavFileBridge.roots() }
    var rootIndex by rememberSaveable { mutableIntStateOf(0) }
    val root = roots[rootIndex.coerceIn(0, roots.size - 1)]
    var currentPath by rememberSaveable(root.dir.absolutePath) { mutableStateOf(root.dir.absolutePath) }
    val current = File(currentPath)
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<File?>(null) }

    var sortField by rememberSaveable { mutableStateOf(FileSortField.NAME) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var foldersFirst by rememberSaveable { mutableStateOf(true) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    var items by remember { mutableStateOf<List<FileItem>?>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FileItem>?>(null) }
    var selection by remember { mutableStateOf<Set<File>>(emptySet()) }
    var nameRequest by remember { mutableStateOf<NameRequest?>(null) }
    var deleteRequest by remember { mutableStateOf<List<File>?>(null) }
    var archivePrompt by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }

    val failedText = stringResource(R.string.aerix_files_failed)
    val doneText = stringResource(R.string.aerix_files_done)

    fun runOp(block: suspend () -> Boolean) {
        scope.launch {
            busy = true
            val ok = block()
            busy = false
            Toast.makeText(context, if (ok) doneText else failedText, Toast.LENGTH_SHORT).show()
            selection = emptySet()
            revision++
        }
    }

    LaunchedEffect(currentPath, sortField, descending, foldersFirst, showHidden, revision) {
        loading = true
        current.mkdirs()
        items = PojavFileBridge.list(current, FileSort(sortField, descending, foldersFirst), showHidden)
        loading = false
    }
    LaunchedEffect(query, currentPath, showHidden, revision) {
        if (query.isBlank()) {
            searchResults = null
        } else {
            delay(300)
            searchResults = PojavFileBridge.search(current, query.trim(), showHidden)
        }
    }
    LaunchedEffect(currentPath) { selection = emptySet() }

    val atRoot = current.absolutePath == root.dir.absolutePath
    BackHandler(enabled = editing == null && (showTrash || selection.isNotEmpty() || query.isNotEmpty() || !atRoot)) {
        when {
            showTrash -> showTrash = false
            selection.isNotEmpty() -> selection = emptySet()
            query.isNotEmpty() -> query = ""
            else -> current.parentFile?.let { currentPath = it.absolutePath }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) runOp { PojavFileBridge.import(context, uris, current) == uris.size }
    }

    editing?.let { file ->
        FileEditorPane(file = file, onClose = {
            editing = null
            revision++
        })
        return
    }

    nameRequest?.let { request ->
        NameDialog(request = request, onDismiss = { nameRequest = null }, onConfirm = { name ->
            nameRequest = null
            runOp { request.onConfirm(name) }
        })
    }
    deleteRequest?.let { files ->
        DeleteDialog(count = files.size, onDismiss = { deleteRequest = null }, onConfirm = { permanent ->
            deleteRequest = null
            runOp {
                val done = if (permanent) PojavFileBridge.deletePermanently(files) else PojavFileBridge.trash(files)
                done == files.size
            }
        })
    }
    archivePrompt?.let { archive ->
        AlertDialog(
            onDismissRequest = { archivePrompt = null },
            containerColor = AerixSurface.panelRaised,
            title = { Text(archive.name, color = AerixSurface.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text(stringResource(R.string.aerix_files_archive_prompt), color = AerixSurface.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    archivePrompt = null
                    runOp { PojavFileBridge.extract(archive) != null }
                }) { Text(stringResource(R.string.aerix_files_extract), color = accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    archivePrompt = null
                    PojavFileBridge.open(activity, archive, false)
                }) { Text(stringResource(R.string.aerix_files_open_with), color = AerixSurface.textSecondary) }
            }
        )
    }

    val compressDefault = stringResource(R.string.aerix_files_archive_default)

    AerixPage(
        title = stringResource(if (showTrash) R.string.aerix_files_trash else R.string.aerix_files_title),
        subtitle = if (showTrash) stringResource(R.string.aerix_files_trash_sub) else current.absolutePath,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                AerixGhostButton(
                    stringResource(if (showTrash) R.string.aerix_files_title else R.string.aerix_files_trash),
                    if (showTrash) R.drawable.aerix_ic_folder_outlined else R.drawable.aerix_ic_delete_outlined,
                    { showTrash = !showTrash }
                )
                if (!showTrash) {
                    AerixPrimaryButton(stringResource(R.string.aerix_files_import), R.drawable.aerix_ic_upload, {
                        importer.launch(arrayOf("*" + "/" + "*"))
                    })
                }
            }
        }
    ) {
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = accent, trackColor = AerixSurface.panelTrack)
        if (showTrash) {
            TrashPane(onChanged = { revision++ })
            return@AerixPage
        }

        if (roots.size > 1) {
            PillTabs(
                tabs = roots.map { stringResource(it.titleRes) },
                selected = rootIndex.coerceIn(0, roots.size - 1),
                onSelect = {
                    rootIndex = it
                    query = ""
                },
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }

        // Toolbar: up, breadcrumb, sort and creation actions.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
        ) {
            IconButton(onClick = { current.parentFile?.let { currentPath = it.absolutePath } }, enabled = !atRoot) {
                Icon(painterResource(R.drawable.aerix_ic_arrow_back), stringResource(R.string.aerix_files_up),
                    tint = if (atRoot) AerixSurface.textMuted else AerixSurface.textPrimary)
            }
            Breadcrumb(root = root.dir, current = current, rootLabel = stringResource(root.titleRes), modifier = Modifier.weight(1f)) {
                currentPath = it.absolutePath
            }
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(painterResource(R.drawable.aerix_ic_sort), stringResource(R.string.aerix_files_sort), tint = AerixSurface.textPrimary)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, containerColor = AerixSurface.panelRaised) {
                    listOf(
                        FileSortField.NAME to R.string.aerix_files_sort_name,
                        FileSortField.SIZE to R.string.aerix_files_sort_size,
                        FileSortField.DATE to R.string.aerix_files_sort_date,
                        FileSortField.TYPE to R.string.aerix_files_sort_type
                    ).forEach { (field, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label), color = if (sortField == field) accent else AerixSurface.textPrimary) },
                            onClick = { sortField = field }
                        )
                    }
                    HorizontalDivider(color = AerixSurface.borderSoft)
                    MenuCheck(stringResource(R.string.aerix_files_sort_desc), descending) { descending = it }
                    MenuCheck(stringResource(R.string.aerix_files_folders_first), foldersFirst) { foldersFirst = it }
                    MenuCheck(stringResource(R.string.aerix_files_show_hidden), showHidden) { showHidden = it }
                }
            }
            IconButton(onClick = {
                nameRequest = NameRequest(R.string.aerix_files_new_folder, "") { PojavFileBridge.createFolder(current, it) }
            }) {
                Icon(painterResource(R.drawable.aerix_ic_create_new_folder_filled), stringResource(R.string.aerix_files_new_folder), tint = AerixSurface.textPrimary)
            }
            IconButton(onClick = {
                nameRequest = NameRequest(R.string.aerix_files_new_file, "") { PojavFileBridge.createFile(current, it) }
            }) {
                Icon(painterResource(R.drawable.aerix_ic_add), stringResource(R.string.aerix_files_new_file), tint = AerixSurface.textPrimary)
            }
            IconButton(onClick = { revision++ }) {
                Icon(painterResource(R.drawable.aerix_ic_refresh), stringResource(R.string.aerix_files_refresh), tint = AerixSurface.textPrimary)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.aerix_files_search), color = AerixSurface.textMuted) },
            leadingIcon = { Icon(painterResource(R.drawable.aerix_ic_search), null, tint = AerixSurface.textSecondary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(painterResource(R.drawable.aerix_ic_close), null, tint = AerixSurface.textSecondary)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AerixRadii.control),
            colors = aerixFieldColors()
        )

        val clip = FmClipboard.files
        if (selection.isNotEmpty()) {
            ActionBar {
                Text(
                    stringResource(R.string.aerix_files_selected, selection.size),
                    color = AerixSurface.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = AerixSpacing.sm)
                )
                BarIcon(R.drawable.aerix_ic_select_all, R.string.aerix_files_select_all) {
                    selection = (searchResults ?: items).orEmpty().map { it.file }.toSet()
                }
                BarIcon(R.drawable.aerix_ic_deselect, R.string.aerix_files_clear_selection) { selection = emptySet() }
                BarIcon(R.drawable.aerix_ic_content_copy_filled, R.string.aerix_files_copy) {
                    FmClipboard.files = selection.toList()
                    FmClipboard.move = false
                    selection = emptySet()
                }
                BarIcon(R.drawable.aerix_ic_content_cut, R.string.aerix_files_cut) {
                    FmClipboard.files = selection.toList()
                    FmClipboard.move = true
                    selection = emptySet()
                }
                if (selection.size == 1) {
                    val single = selection.first()
                    BarIcon(R.drawable.aerix_ic_edit_outlined, R.string.aerix_files_rename) {
                        nameRequest = NameRequest(R.string.aerix_files_rename, single.name) { PojavFileBridge.rename(single, it) }
                    }
                    if (single.isFile) {
                        BarIcon(R.drawable.aerix_ic_share_filled, R.string.aerix_files_share) {
                            PojavFileBridge.open(activity, single, true)
                            selection = emptySet()
                        }
                    }
                    if (single.isFile && PojavFileBridge.isArchive(single)) {
                        BarIcon(R.drawable.aerix_ic_unarchive_outlined, R.string.aerix_files_extract) {
                            runOp { PojavFileBridge.extract(single) != null }
                        }
                    }
                }
                BarIcon(R.drawable.aerix_ic_archive_filled, R.string.aerix_files_compress) {
                    val files = selection.toList()
                    val initial = if (files.size == 1) files[0].nameWithoutExtension else compressDefault
                    nameRequest = NameRequest(R.string.aerix_files_compress, initial) { PojavFileBridge.compress(files, current, it) != null }
                }
                BarIcon(R.drawable.aerix_ic_delete_outlined, R.string.aerix_files_delete, AerixSurface.danger) {
                    deleteRequest = selection.toList()
                }
            }
        } else if (clip.isNotEmpty()) {
            ActionBar {
                Text(
                    stringResource(if (FmClipboard.move) R.string.aerix_files_clip_move else R.string.aerix_files_clip_copy, clip.size),
                    color = AerixSurface.textPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = AerixSpacing.sm)
                )
                AerixGhostButton(stringResource(R.string.aerix_files_paste), R.drawable.aerix_ic_content_paste, {
                    val files = clip
                    val move = FmClipboard.move
                    FmClipboard.files = emptyList()
                    runOp { PojavFileBridge.paste(files, current, move) == files.size }
                })
                BarIcon(R.drawable.aerix_ic_close, R.string.aerix_settings_cancel) { FmClipboard.files = emptyList() }
            }
        }

        val shown = searchResults ?: items
        when {
            shown == null -> QuietEmptyState(R.drawable.aerix_ic_warning_outlined, stringResource(R.string.aerix_files_unreadable))
            loading && shown.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent)
            }
            shown.isEmpty() -> QuietEmptyState(R.drawable.aerix_ic_folder_outlined, stringResource(
                if (searchResults != null) R.string.aerix_files_no_match else R.string.aerix_files_empty
            ))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
            ) {
                items(shown, key = { it.file.absolutePath }) { item ->
                    FileRow(
                        item = item,
                        selected = item.file in selection,
                        onClick = {
                            when {
                                selection.isNotEmpty() -> selection = if (item.file in selection) selection - item.file else selection + item.file
                                item.isDirectory -> {
                                    query = ""
                                    currentPath = item.file.absolutePath
                                }
                                PojavFileBridge.isArchive(item.file) -> archivePrompt = item.file
                                PojavFileBridge.isText(item.file) && item.size <= PojavFileBridge.MAX_EDIT_BYTES -> editing = item.file
                                else -> PojavFileBridge.open(activity, item.file, false)
                            }
                        },
                        onLongClick = {
                            selection = if (item.file in selection) selection - item.file else selection + item.file
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    DropdownMenuItem(
        text = { Text(label, color = AerixSurface.textPrimary) },
        trailingIcon = {
            Checkbox(
                checked = checked,
                onCheckedChange = onChange,
                colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = AerixSurface.textSecondary, checkmarkColor = AerixSurface.onAccent)
            )
        },
        onClick = { onChange(!checked) }
    )
}

@Composable
private fun ActionBar(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(AerixRadii.panelSmall)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AerixSurface.panelRaised.copy(alpha = 0.7f), shape)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AerixSpacing.xs, vertical = AerixSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
    ) { content() }
}

@Composable
private fun BarIcon(iconRes: Int, labelRes: Int, tint: Color = AerixSurface.textPrimary, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(iconRes), stringResource(labelRes), tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Breadcrumb(root: File, current: File, rootLabel: String, modifier: Modifier, onNavigate: (File) -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val chain = remember(root, current) {
        val list = ArrayList<File>()
        var cursor: File? = current
        val rootPath = root.absolutePath
        while (cursor != null) {
            list.add(0, cursor)
            if (cursor.absolutePath == rootPath) break
            cursor = cursor.parentFile
        }
        list
    }
    val scroll = rememberScrollState()
    LaunchedEffect(chain.size) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        modifier = modifier.horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically
    ) {
        chain.forEachIndexed { index, dir ->
            if (index > 0) Text("/", color = AerixSurface.textMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 2.dp))
            val last = index == chain.size - 1
            Text(
                text = if (index == 0) rootLabel else dir.name,
                color = if (last) accent else AerixSurface.textSecondary,
                fontSize = 13.sp,
                fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !last) { onNavigate(dir) }
                    .padding(horizontal = AerixSpacing.xs, vertical = AerixSpacing.xxs)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(item: FileItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val accent = AerixThemeManager.currentAccent()
    val shape = RoundedCornerShape(AerixRadii.panelSmall)
    val date = remember(item.lastModified) {
        val d = Date(item.lastModified)
        DateFormat.getMediumDateFormat(context).format(d) + " " + DateFormat.getTimeFormat(context).format(d)
    }
    val meta = if (item.isDirectory) {
        if (item.childCount >= 0) context.resources.getQuantityString(R.plurals.aerix_files_items, item.childCount, item.childCount) + " · " + date
        else date
    } else {
        Formatter.formatShortFileSize(context, item.size) + " · " + date
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.22f) else AerixSurface.panelRaised.copy(alpha = 0.42f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        Icon(
            painter = painterResource(if (selected) R.drawable.aerix_ic_check else fileIcon(item.file, item.isDirectory)),
            contentDescription = null,
            tint = if (selected || item.isDirectory) accent else AerixSurface.textSecondary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, color = AerixSurface.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, color = AerixSurface.textMuted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun NameDialog(request: NameRequest, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    var text by remember(request) { mutableStateOf(request.initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AerixSurface.panelRaised,
        title = { Text(stringResource(request.titleRes), color = AerixSurface.textPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.aerix_files_name), color = AerixSurface.textSecondary) },
                colors = aerixFieldColors()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.aerix_files_ok), color = accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary) }
        }
    )
}

@Composable
private fun DeleteDialog(count: Int, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    var permanent by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AerixSurface.panelRaised,
        title = { Text(stringResource(R.string.aerix_files_delete), color = AerixSurface.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                Text(stringResource(R.string.aerix_files_delete_confirm, count), color = AerixSurface.textSecondary)
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { permanent = !permanent },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = permanent,
                        onCheckedChange = { permanent = it },
                        colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = AerixSurface.textSecondary, checkmarkColor = AerixSurface.onAccent)
                    )
                    Text(stringResource(R.string.aerix_files_delete_permanent), color = AerixSurface.textPrimary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(permanent) }) { Text(stringResource(R.string.aerix_files_delete), color = AerixSurface.danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary) }
        }
    )
}

@Composable
private fun ColumnScope.TrashPane(onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()
    var entries by remember { mutableStateOf<List<TrashEntry>?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val failedText = stringResource(R.string.aerix_files_failed)

    LaunchedEffect(revision) { entries = PojavFileBridge.trashEntries() }

    fun op(block: suspend () -> Boolean) {
        scope.launch {
            if (!block()) Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
            revision++
            onChanged()
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            containerColor = AerixSurface.panelRaised,
            title = { Text(stringResource(R.string.aerix_files_empty_trash), color = AerixSurface.textPrimary) },
            text = { Text(stringResource(R.string.aerix_files_empty_trash_confirm), color = AerixSurface.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    op { PojavFileBridge.emptyTrash() }
                }) { Text(stringResource(R.string.aerix_files_empty_trash), color = AerixSurface.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary) }
            }
        )
    }

    val list = entries
    when {
        list == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
        list.isEmpty() -> QuietEmptyState(R.drawable.aerix_ic_delete_outlined, stringResource(R.string.aerix_files_trash_empty))
        else -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AerixGhostButton(stringResource(R.string.aerix_files_empty_trash), R.drawable.aerix_ic_delete_outlined, { confirmEmpty = true })
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
            ) {
                items(list, key = { it.file.absolutePath }) { entry ->
                    val shape = RoundedCornerShape(AerixRadii.panelSmall)
                    val date = remember(entry.deletedAt) {
                        val d = Date(entry.deletedAt)
                        DateFormat.getMediumDateFormat(context).format(d) + " " + DateFormat.getTimeFormat(context).format(d)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(AerixSurface.panelRaised.copy(alpha = 0.42f), shape)
                            .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                    ) {
                        Icon(painterResource(fileIcon(entry.file, entry.isDirectory)), null, tint = AerixSurface.textSecondary, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.file.name, color = AerixSurface.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.originalPath, color = AerixSurface.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Formatter.formatShortFileSize(context, entry.size) + " · " + date, color = AerixSurface.textMuted, fontSize = 11.sp, maxLines = 1)
                        }
                        IconButton(onClick = { op { PojavFileBridge.restore(entry) } }) {
                            Icon(painterResource(R.drawable.aerix_ic_restore_from_trash_filled), stringResource(R.string.aerix_files_restore), tint = accent)
                        }
                        IconButton(onClick = { op { PojavFileBridge.purge(entry) } }) {
                            Icon(painterResource(R.drawable.aerix_ic_delete_outlined), stringResource(R.string.aerix_files_delete_permanent), tint = AerixSurface.danger)
                        }
                    }
                }
            }
        }
    }
}

/** Text editor for configs and logs (mirai FmEditorScreen: save, word wrap, find, replace, go to line). */
@Composable
private fun FileEditorPane(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()
    var original by remember(file) { mutableStateOf<String?>(null) }
    var value by remember(file) { mutableStateOf(TextFieldValue("")) }
    var loadFailed by remember(file) { mutableStateOf(false) }
    var wrap by rememberSaveable { mutableStateOf(true) }
    var findOpen by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var gotoOpen by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val savedText = stringResource(R.string.aerix_files_saved)
    val failedText = stringResource(R.string.aerix_files_failed)
    val notFoundText = stringResource(R.string.aerix_files_find_none)

    LaunchedEffect(file) {
        val text = PojavFileBridge.readText(file)
        if (text == null) loadFailed = true else {
            original = text
            value = TextFieldValue(text)
        }
    }
    val dirty = original != null && value.text != original
    val lines = remember(value.text) { value.text.count { it == '\n' } + 1 }
    val matches = remember(value.text, find) {
        if (find.isEmpty()) 0 else Regex(Regex.escape(find), RegexOption.IGNORE_CASE).findAll(value.text).count()
    }

    fun save() {
        scope.launch {
            saving = true
            val ok = PojavFileBridge.writeText(file, value.text)
            saving = false
            if (ok) original = value.text
            Toast.makeText(context, if (ok) savedText else failedText, Toast.LENGTH_SHORT).show()
        }
    }

    fun findNext() {
        if (find.isEmpty()) return
        val text = value.text
        var index = text.indexOf(find, value.selection.max, ignoreCase = true)
        if (index < 0) index = text.indexOf(find, 0, ignoreCase = true)
        if (index < 0) {
            Toast.makeText(context, notFoundText, Toast.LENGTH_SHORT).show()
        } else {
            value = value.copy(selection = TextRange(index, index + find.length))
        }
    }

    fun replaceCurrent() {
        val sel = value.selection
        val text = value.text
        if (find.isNotEmpty() && !sel.collapsed && text.substring(sel.min, sel.max).equals(find, ignoreCase = true)) {
            val updated = text.substring(0, sel.min) + replace + text.substring(sel.max)
            value = TextFieldValue(updated, TextRange(sel.min + replace.length))
        }
        findNext()
    }

    fun replaceAll() {
        if (find.isEmpty()) return
        val updated = value.text.replace(find, replace, ignoreCase = true)
        value = TextFieldValue(updated, TextRange(0))
    }

    BackHandler { if (dirty) confirmDiscard = true else onClose() }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = AerixSurface.panelRaised,
            title = { Text(stringResource(R.string.aerix_files_unsaved), color = AerixSurface.textPrimary) },
            text = { Text(stringResource(R.string.aerix_files_unsaved_confirm), color = AerixSurface.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.aerix_files_discard), color = AerixSurface.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary) }
            }
        )
    }
    if (gotoOpen) {
        var line by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { gotoOpen = false },
            containerColor = AerixSurface.panelRaised,
            title = { Text(stringResource(R.string.aerix_files_goto_line), color = AerixSurface.textPrimary) },
            text = {
                OutlinedTextField(
                    value = line,
                    onValueChange = { line = it.filter(Char::isDigit) },
                    singleLine = true,
                    label = { Text("1 – $lines", color = AerixSurface.textSecondary) },
                    colors = aerixFieldColors()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    gotoOpen = false
                    val target = (line.toIntOrNull() ?: 1).coerceIn(1, lines)
                    var offset = 0
                    repeat(target - 1) {
                        val next = value.text.indexOf('\n', offset)
                        if (next >= 0) offset = next + 1
                    }
                    value = value.copy(selection = TextRange(offset))
                }) { Text(stringResource(R.string.aerix_files_ok), color = accent) }
            },
            dismissButton = {
                TextButton(onClick = { gotoOpen = false }) { Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(net.kdt.pojavlaunch.aerix.theme.AerixLayoutDefaults.pagePadding()),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
            IconButton(onClick = { if (dirty) confirmDiscard = true else onClose() }) {
                Icon(painterResource(R.drawable.aerix_ic_arrow_back), stringResource(R.string.aerix_files_close), tint = AerixSurface.textPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    (if (dirty) "• " else "") + file.name,
                    color = AerixSurface.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    context.resources.getQuantityString(R.plurals.aerix_files_lines, lines, lines) + " · " + file.parent.orEmpty(),
                    color = AerixSurface.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { findOpen = !findOpen }) {
                Icon(painterResource(R.drawable.aerix_ic_search), stringResource(R.string.aerix_files_find), tint = if (findOpen) accent else AerixSurface.textPrimary)
            }
            IconButton(onClick = { gotoOpen = true }) {
                Icon(painterResource(R.drawable.aerix_ic_more_vert), stringResource(R.string.aerix_files_goto_line), tint = AerixSurface.textPrimary)
            }
            IconButton(onClick = { wrap = !wrap }) {
                Icon(painterResource(R.drawable.aerix_ic_text_snippet_filled), stringResource(R.string.aerix_files_wrap), tint = if (wrap) accent else AerixSurface.textSecondary)
            }
            AerixPrimaryButton(stringResource(R.string.aerix_files_save), R.drawable.aerix_ic_check, { save() }, enabled = dirty && !saving)
        }
        if (findOpen) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                OutlinedTextField(
                    value = find,
                    onValueChange = { find = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.aerix_files_find), color = AerixSurface.textMuted) },
                    modifier = Modifier.weight(1f),
                    colors = aerixFieldColors()
                )
                OutlinedTextField(
                    value = replace,
                    onValueChange = { replace = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.aerix_files_replace), color = AerixSurface.textMuted) },
                    modifier = Modifier.weight(1f),
                    colors = aerixFieldColors()
                )
                Text(matches.toString(), color = AerixSurface.textMuted, fontSize = 12.sp)
                TextButton(onClick = { findNext() }) { Text(stringResource(R.string.aerix_files_find_next), color = accent) }
                TextButton(onClick = { replaceCurrent() }) { Text(stringResource(R.string.aerix_files_replace), color = accent) }
                TextButton(onClick = { replaceAll() }) { Text(stringResource(R.string.aerix_files_replace_all), color = accent) }
            }
        }
        val shape = RoundedCornerShape(AerixRadii.panelSmall)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(AerixSurface.canvas.copy(alpha = 0.72f), shape)
                .padding(AerixSpacing.sm)
        ) {
            when {
                loadFailed -> QuietEmptyState(R.drawable.aerix_ic_warning_outlined, stringResource(R.string.aerix_files_unreadable))
                original == null -> CircularProgressIndicator(color = accent, modifier = Modifier.align(Alignment.Center))
                else -> {
                    val verticalScroll = rememberScrollState()
                    val horizontalScroll = rememberScrollState()
                    val scrollModifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScroll)
                        .let { if (wrap) it else it.horizontalScroll(horizontalScroll) }
                    Box(modifier = scrollModifier) {
                        BasicTextField(
                            value = value,
                            onValueChange = { value = it },
                            textStyle = TextStyle(color = AerixSurface.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            cursorBrush = SolidColor(accent),
                            modifier = if (wrap) Modifier.fillMaxWidth() else Modifier
                        )
                    }
                }
            }
        }
    }
}
