/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: file operations for the Aerix file manager, ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/viewmodel/controllers/BrowseController.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/viewmodel/controllers/EntryController.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/viewmodel/controllers/PasteController.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/viewmodel/controllers/CompressController.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/viewmodel/controllers/ExtractController.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/viewmodel/controllers/ImportController.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/viewmodel/controllers/EditorController.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/viewmodel/controllers/TrashController.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/filemanager/viewmodel/SortConfig.kt
 * Roots are Pojav's own folders (Tools.DIR_GAME_HOME, the selected instance's game directory,
 * Tools.DIR_HOME_CRASH); open and share go through Tools.openPath and Pojav's FolderProvider.
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

package net.kdt.pojavlaunch.aerix.bridge

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.instances.Instances
import org.apache.commons.io.FileUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileRoot(@StringRes val titleRes: Int, val dir: File)

class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    /** Number of children for folders, -1 when unreadable. */
    val childCount: Int
)

enum class FileSortField { NAME, SIZE, DATE, TYPE }

class FileSort(val field: FileSortField = FileSortField.NAME, val descending: Boolean = false, val foldersFirst: Boolean = true)

class TrashEntry(val file: File, val originalPath: String, val deletedAt: Long, val isDirectory: Boolean, val size: Long)

object PojavFileBridge {
    /** Largest file opened in the text editor; bigger files go to an external app. */
    const val MAX_EDIT_BYTES = 512L * 1024

    private const val TRASH_DIR = ".aerix_trash"
    private const val TRASH_INFO = ".origin"

    private val textExtensions = setOf(
        "txt", "json", "json5", "properties", "cfg", "conf", "config", "toml", "ini", "log", "yml", "yaml",
        "mcmeta", "snbt", "js", "lang", "md", "xml", "csv", "options", "vsh", "fsh", "glsl", "zs", "mcfunction"
    )
    private val archiveExtensions = setOf("zip", "jar", "mrpack")

    fun roots(): List<FileRoot> = buildList {
        runCatching { Instances.loadSelectedInstance()?.gameDirectory }.getOrNull()?.let {
            add(FileRoot(R.string.aerix_files_root_instance, it))
        }
        add(FileRoot(R.string.aerix_files_root_home, File(Tools.DIR_GAME_HOME)))
        Tools.DIR_HOME_CRASH?.let { add(FileRoot(R.string.aerix_files_root_crash, File(it))) }
    }

    private fun trashDir(): File = File(Tools.DIR_GAME_HOME, TRASH_DIR)

    fun isText(file: File): Boolean = file.extension.lowercase() in textExtensions ||
        (file.extension.isEmpty() && file.length() < 64 * 1024)

    fun isArchive(file: File): Boolean = file.extension.lowercase() in archiveExtensions

    suspend fun list(dir: File, sort: FileSort, showHidden: Boolean): List<FileItem>? = withContext(Dispatchers.IO) {
        val files = dir.listFiles() ?: return@withContext null
        val items = files
            .filter { showHidden || !it.name.startsWith(".") }
            .map {
                val directory = it.isDirectory
                FileItem(
                    file = it,
                    name = it.name,
                    isDirectory = directory,
                    size = if (directory) 0L else it.length(),
                    lastModified = it.lastModified(),
                    childCount = if (directory) it.list()?.size ?: -1 else 0
                )
            }
        val comparator: Comparator<FileItem> = when (sort.field) {
            FileSortField.NAME -> compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            FileSortField.SIZE -> compareBy<FileItem> { it.size }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            FileSortField.DATE -> compareBy<FileItem> { it.lastModified }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            FileSortField.TYPE -> compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.file.extension }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        val ordered = if (sort.descending) comparator.reversed() else comparator
        if (sort.foldersFirst) items.sortedWith(compareBy<FileItem> { !it.isDirectory }.then(ordered))
        else items.sortedWith(ordered)
    }

    /** Recursive name search below [dir] (mirai SearchController). */
    suspend fun search(dir: File, query: String, showHidden: Boolean, limit: Int = 500): List<FileItem> = withContext(Dispatchers.IO) {
        val result = ArrayList<FileItem>()
        val walker = dir.walkTopDown()
            .onEnter { showHidden || !it.name.startsWith(".") || it == dir }
        for (file in walker) {
            if (file == dir) continue
            if (!showHidden && file.name.startsWith(".")) continue
            if (file.name.contains(query, ignoreCase = true)) {
                val directory = file.isDirectory
                result.add(FileItem(file, file.relativeTo(dir).path, directory, if (directory) 0L else file.length(), file.lastModified(),
                    if (directory) file.list()?.size ?: -1 else 0))
                if (result.size >= limit) break
            }
        }
        result
    }

    private fun validName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\u0000')

    suspend fun createFolder(parent: File, name: String): Boolean = withContext(Dispatchers.IO) {
        validName(name) && File(parent, name).let { !it.exists() && it.mkdirs() }
    }

    suspend fun createFile(parent: File, name: String): Boolean = withContext(Dispatchers.IO) {
        validName(name) && runCatching { File(parent, name).createNewFile() }.getOrDefault(false)
    }

    suspend fun rename(file: File, name: String): Boolean = withContext(Dispatchers.IO) {
        if (!validName(name)) return@withContext false
        val target = File(file.parentFile, name)
        !target.exists() && file.renameTo(target)
    }

    /** Copies or moves [sources] into [targetDir]; name clashes get a " (n)" suffix. */
    suspend fun paste(sources: List<File>, targetDir: File, move: Boolean): Int = withContext(Dispatchers.IO) {
        var done = 0
        for (source in sources) {
            if (!source.exists()) continue
            // Refuse to copy a folder into itself or one of its children.
            if (source.isDirectory && (targetDir.canonicalPath + File.separator).startsWith(source.canonicalPath + File.separator)) continue
            if (move && source.parentFile?.canonicalPath == targetDir.canonicalPath) {
                done++
                continue
            }
            val target = PojavContentBridge.uniqueFile(targetDir, source.name)
            val ok = runCatching {
                if (move) {
                    if (!source.renameTo(target)) {
                        if (source.isDirectory) FileUtils.moveDirectory(source, target) else FileUtils.moveFile(source, target)
                    }
                } else {
                    if (source.isDirectory) FileUtils.copyDirectory(source, target) else FileUtils.copyFile(source, target)
                }
            }.isSuccess
            if (ok) done++
        }
        done
    }

    /** Moves [files] to the Aerix trash (mirai TrashController) instead of deleting them. */
    suspend fun trash(files: List<File>): Int = withContext(Dispatchers.IO) {
        val trash = trashDir()
        var done = 0
        for (file in files) {
            if (file.canonicalPath.startsWith(trash.canonicalPath + File.separator)) {
                if (runCatching { FileUtils.forceDelete(file) }.isSuccess) done++
                continue
            }
            val slot = File(trash, System.currentTimeMillis().toString() + "_" + done)
            val ok = runCatching {
                if (!slot.mkdirs()) throw IOException("Unable to create ${slot.absolutePath}")
                File(slot, TRASH_INFO).writeText(file.absolutePath)
                val target = File(slot, file.name)
                if (!file.renameTo(target)) {
                    if (file.isDirectory) FileUtils.moveDirectory(file, target) else FileUtils.moveFile(file, target)
                }
            }.isSuccess
            if (ok) done++ else runCatching { FileUtils.deleteDirectory(slot) }
        }
        done
    }

    suspend fun deletePermanently(files: List<File>): Int = withContext(Dispatchers.IO) {
        files.count { runCatching { FileUtils.forceDelete(it) }.isSuccess }
    }

    suspend fun trashEntries(): List<TrashEntry> = withContext(Dispatchers.IO) {
        val slots = trashDir().listFiles() ?: return@withContext emptyList()
        slots.mapNotNull { slot ->
            val info = File(slot, TRASH_INFO)
            val content = slot.listFiles()?.firstOrNull { it.name != TRASH_INFO } ?: return@mapNotNull null
            val original = runCatching { info.readText() }.getOrNull() ?: return@mapNotNull null
            TrashEntry(
                file = content,
                originalPath = original,
                deletedAt = slot.name.substringBefore('_').toLongOrNull() ?: slot.lastModified(),
                isDirectory = content.isDirectory,
                size = if (content.isDirectory) FileUtils.sizeOfDirectory(content) else content.length()
            )
        }.sortedByDescending { it.deletedAt }
    }

    suspend fun restore(entry: TrashEntry): Boolean = withContext(Dispatchers.IO) {
        val original = File(entry.originalPath)
        val parent = original.parentFile ?: return@withContext false
        if (!parent.isDirectory && !parent.mkdirs()) return@withContext false
        val target = PojavContentBridge.uniqueFile(parent, original.name)
        val ok = runCatching {
            if (!entry.file.renameTo(target)) {
                if (entry.isDirectory) FileUtils.moveDirectory(entry.file, target) else FileUtils.moveFile(entry.file, target)
            }
        }.isSuccess
        if (ok) entry.file.parentFile?.let { runCatching { FileUtils.deleteDirectory(it) } }
        ok
    }

    suspend fun purge(entry: TrashEntry): Boolean = withContext(Dispatchers.IO) {
        val slot = entry.file.parentFile ?: return@withContext false
        runCatching { FileUtils.deleteDirectory(slot) }.isSuccess
    }

    suspend fun emptyTrash(): Boolean = withContext(Dispatchers.IO) {
        runCatching { FileUtils.deleteDirectory(trashDir()) }.isSuccess
    }

    /** Packs [files] into [zipName] inside [targetDir] (mirai CompressController, zip format). */
    suspend fun compress(files: List<File>, targetDir: File, zipName: String): File? = withContext(Dispatchers.IO) {
        val name = if (zipName.endsWith(".zip", ignoreCase = true)) zipName else "$zipName.zip"
        if (!validName(name)) return@withContext null
        val out = PojavContentBridge.uniqueFile(targetDir, name)
        val ok = runCatching {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
                for (file in files) addToZip(zip, file, file.name)
            }
        }.isSuccess
        if (ok) out else {
            out.delete()
            null
        }
    }

    private fun addToZip(zip: ZipOutputStream, file: File, path: String) {
        if (file.isDirectory) {
            zip.putNextEntry(ZipEntry("$path/"))
            zip.closeEntry()
            file.listFiles()?.forEach { addToZip(zip, it, "$path/${it.name}") }
        } else {
            val entry = ZipEntry(path)
            entry.time = file.lastModified()
            zip.putNextEntry(entry)
            BufferedInputStream(FileInputStream(file)).use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    /** Extracts [archive] into a new folder next to it; entries escaping the folder are rejected. */
    suspend fun extract(archive: File): File? = withContext(Dispatchers.IO) {
        val parent = archive.parentFile ?: return@withContext null
        val dir = PojavContentBridge.uniqueFile(parent, archive.nameWithoutExtension)
        val ok = runCatching {
            if (!dir.mkdirs()) throw IOException("Unable to create ${dir.absolutePath}")
            val root = dir.canonicalPath + File.separator
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val path = entry.name.replace('\\', '/').trimStart('/')
                    if (path.isEmpty()) continue
                    val out = File(dir, path)
                    if (!out.canonicalPath.startsWith(root)) throw IOException("Blocked entry ${entry.name}")
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { zip.copyTo(it) }
                        if (entry.time > 0) out.setLastModified(entry.time)
                    }
                }
            }
        }.isSuccess
        if (ok) dir else {
            runCatching { FileUtils.deleteDirectory(dir) }
            null
        }
    }

    /** Copies picked documents into [targetDir] (mirai ImportController). */
    suspend fun import(context: Context, uris: List<Uri>, targetDir: File): Int = withContext(Dispatchers.IO) {
        uris.count { uri ->
            runCatching {
                val name = PojavContentBridge.displayName(context, uri) ?: throw IOException("No name for $uri")
                val target = PojavContentBridge.uniqueFile(targetDir, File(name).name)
                context.contentResolver.openInputStream(uri).use { input ->
                    if (input == null) throw IOException("Unable to open $uri")
                    FileOutputStream(target).use { input.copyTo(it) }
                }
            }.isSuccess
        }
    }

    suspend fun readText(file: File): String? = withContext(Dispatchers.IO) {
        if (file.length() > MAX_EDIT_BYTES) null else runCatching { file.readText() }.getOrNull()
    }

    suspend fun writeText(file: File, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File(file.parentFile, file.name + ".aerix_tmp")
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                FileUtils.copyFile(temp, file)
                temp.delete()
            }
        }.isSuccess
    }

    suspend fun folderSize(dir: File): Long = withContext(Dispatchers.IO) {
        runCatching { FileUtils.sizeOfDirectory(dir) }.getOrDefault(0L)
    }

    /**
     * Aerix "launcherLogRetentionDays": removes crash reports in Tools.DIR_HOME_CRASH and
     * Aerix trash slots older than [days]. Blocking; call from a background thread.
     */
    fun pruneOldLogs(days: Int) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        Tools.DIR_HOME_CRASH?.let { dir ->
            File(dir).listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) file.delete()
            }
        }
        trashDir().listFiles()?.forEach { slot ->
            val deletedAt = slot.name.substringBefore('_').toLongOrNull() ?: slot.lastModified()
            if (deletedAt < cutoff) runCatching { FileUtils.deleteDirectory(slot) }
        }
    }

    fun open(context: Context, file: File, share: Boolean) {
        Tools.openPath(context, file, share)
    }
}
