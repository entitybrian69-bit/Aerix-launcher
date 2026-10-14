/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: instance content managers (mods, resource packs, shader packs, saves) ported from
 * mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/ModsManagerScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/ResourcePackManageScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/ShadersManagerScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/SavesManagerScreen.kt
 * Works on the folders of the selected Pojav instance (Instance.getGameDirectory()). Mods are
 * disabled with the ".disabled" suffix, which Fabric, Quilt, Forge and NeoForge all skip.
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
import android.provider.OpenableColumns
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.instances.Instances
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

enum class ContentKind(
    val directoryName: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    /** Entries can be switched off without deleting them. */
    val toggleable: Boolean,
    /** MIME types offered by the import picker. */
    val importMimeTypes: Array<String>
) {
    MODS("mods", R.string.aerix_content_mods, R.drawable.aerix_ic_extension_outlined, true,
        arrayOf("application/java-archive", "application/zip", "application/octet-stream")),
    RESOURCE_PACKS("resourcepacks", R.string.aerix_content_resourcepacks, R.drawable.aerix_ic_photo_library_outlined, false,
        arrayOf("application/zip", "application/octet-stream")),
    SHADER_PACKS("shaderpacks", R.string.aerix_content_shaderpacks, R.drawable.aerix_ic_wallpaper, false,
        arrayOf("application/zip", "application/octet-stream")),
    SAVES("saves", R.string.aerix_content_saves, R.drawable.aerix_ic_videogame_asset_outlined, false,
        arrayOf("application/zip", "application/octet-stream"))
}

class ContentEntry(
    val file: File,
    /** Name shown in the list, without the ".disabled" suffix. */
    val name: String,
    val enabled: Boolean,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)

object PojavContentBridge {
    private const val DISABLED_SUFFIX = ".disabled"

    /** Folder of [kind] inside the selected instance, or null when no instance is selected. */
    fun directory(kind: ContentKind): File? {
        val instance = Instances.loadSelectedInstance() ?: return null
        return File(instance.gameDirectory, kind.directoryName)
    }

    suspend fun list(kind: ContentKind): List<ContentEntry>? = withContext(Dispatchers.IO) {
        val dir = directory(kind) ?: return@withContext null
        val files = dir.listFiles() ?: return@withContext emptyList()
        files.filter { accepts(kind, it) }
            .map { file ->
                val disabled = file.name.endsWith(DISABLED_SUFFIX)
                ContentEntry(
                    file = file,
                    name = if (disabled) file.name.removeSuffix(DISABLED_SUFFIX) else file.name,
                    enabled = !disabled,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) runCatching { FileUtils.sizeOfDirectory(file) }.getOrDefault(0L) else file.length(),
                    lastModified = file.lastModified()
                )
            }
            .sortedWith(compareByDescending<ContentEntry> { it.enabled }.thenBy { it.name.lowercase() })
    }

    private fun accepts(kind: ContentKind, file: File): Boolean {
        val name = file.name.lowercase()
        if (name.startsWith(".")) return false
        return when (kind) {
            ContentKind.MODS -> file.isFile && (name.endsWith(".jar") || name.endsWith(".jar$DISABLED_SUFFIX") ||
                name.endsWith(".zip") || name.endsWith(".zip$DISABLED_SUFFIX") || name.endsWith(".litemod"))
            ContentKind.RESOURCE_PACKS, ContentKind.SHADER_PACKS -> file.isDirectory || name.endsWith(".zip")
            ContentKind.SAVES -> file.isDirectory
        }
    }

    /** Renames a mod to or from "<name>.disabled". */
    suspend fun setEnabled(entry: ContentEntry, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (entry.enabled == enabled) return@withContext true
        val target = if (enabled) File(entry.file.parentFile, entry.name) else File(entry.file.parentFile, entry.file.name + DISABLED_SUFFIX)
        !target.exists() && entry.file.renameTo(target)
    }

    suspend fun delete(entry: ContentEntry): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (entry.file.isDirectory) FileUtils.deleteDirectory(entry.file) else FileUtils.forceDelete(entry.file)
        }.isSuccess
    }

    /**
     * Copies the picked document into the [kind] folder. A .zip picked for saves is extracted as a
     * world (one folder per archive); every other file is copied as-is.
     */
    suspend fun import(context: Context, kind: ContentKind, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val dir = directory(kind) ?: return@withContext false
        if (!dir.isDirectory && !dir.mkdirs()) return@withContext false
        val name = displayName(context, uri) ?: return@withContext false
        runCatching {
            if (kind == ContentKind.SAVES && name.lowercase().endsWith(".zip")) {
                extractWorld(context, uri, dir, name.substring(0, name.length - 4))
            } else {
                val target = uniqueFile(dir, name)
                context.contentResolver.openInputStream(uri).use { input ->
                    if (input == null) throw IOException("Unable to open $uri")
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
        }.isSuccess
    }

    fun openFolder(context: Context, kind: ContentKind) {
        val dir = directory(kind) ?: return
        if (dir.isDirectory || dir.mkdirs()) Tools.openPath(context, dir, false)
    }

    private fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val value = cursor.getString(0)
                if (!value.isNullOrBlank()) return File(value).name
            }
        }
        return uri.lastPathSegment?.let { File(it).name }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($index)$ext")
            index++
        }
        return candidate
    }

    /**
     * Extracts a world archive. When every entry sits under one top-level folder that folder
     * becomes the world; otherwise the archive name is used. Entries escaping the target are rejected.
     */
    private fun extractWorld(context: Context, uri: Uri, savesDir: File, archiveName: String) {
        val entries = ArrayList<String>()
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IOException("Unable to open $uri")
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries.add(entry.name.replace('\\', '/'))
                }
            }
        }
        val roots = entries.map { it.trimStart('/').substringBefore('/') }.toSet()
        val singleRoot = roots.size == 1 && entries.all { it.trimStart('/').contains('/') }
        val worldDir = uniqueFile(savesDir, if (singleRoot) roots.first() else archiveName)
        if (!worldDir.mkdirs()) throw IOException("Unable to create ${worldDir.absolutePath}")
        val canonicalRoot = worldDir.canonicalPath + File.separator

        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IOException("Unable to open $uri")
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    var path = entry.name.replace('\\', '/').trimStart('/')
                    if (singleRoot) path = path.substringAfter('/', "")
                    if (path.isEmpty()) continue
                    val out = File(worldDir, path)
                    if (!out.canonicalPath.startsWith(canonicalRoot)) throw IOException("Blocked entry ${entry.name}")
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { zip.copyTo(it) }
                    }
                }
            }
        }
    }
}
