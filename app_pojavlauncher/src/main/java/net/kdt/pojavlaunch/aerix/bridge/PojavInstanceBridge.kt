/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: maps PojavLauncher's Instances / DisplayInstance / InstanceIconProvider onto Compose
 * state, and provides the Instance Management actions of mirai-launcher-'s VersionsManageScreen:
 *   pin/unpin, rename, copy, delete, open directory, and modpack export (.mrpack / CurseForge .zip /
 *   MultiMC .zip).
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
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.fragment.app.FragmentActivity
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.components.toAerixBitmap
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.extra.ExtraListener
import net.kdt.pojavlaunch.instances.DisplayInstance
import net.kdt.pojavlaunch.instances.Instance
import net.kdt.pojavlaunch.instances.InstanceIconProvider
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.utils.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One row of the Aerix library. [source] is the untouched Pojav object handed back to Pojav APIs. */
class AerixInstance(
    val source: DisplayInstance,
    val name: String,
    val versionId: String,
    val icon: ImageBitmap?
) {
    /** Folder identity stays unique across copies and stable across renames/loader updates. */
    val id: String = source.instanceId
}

/** Defensive boundary: repeated records cannot become duplicate Compose keys.
 * Copies with identical labels remain distinct because identity is the instance folder ID.
 */
internal fun uniqueInstanceRows(rows: List<AerixInstance>): List<AerixInstance> = rows.distinctBy { it.id }

enum class ModpackExportFormat(val label: String, val extension: String, val description: String) {
    MODRINTH("Modrinth (.mrpack)", "mrpack", "Modrinth index + overrides archive"),
    CURSEFORGE("CurseForge (.zip)", "zip", "CurseForge manifest.json + overrides archive"),
    MULTIMC("MultiMC / Prism (.zip)", "zip", "instance.cfg + mmc-pack.json + .minecraft archive")
}

object PojavInstanceBridge {
    var instances by mutableStateOf<List<AerixInstance>>(emptyList())
        private set
    var selectedIndex by mutableIntStateOf(-1)
        private set
    var loading by mutableStateOf(false)
        private set

    /** Desired sub-tab when InstanceEditorScreen opens ("mods", "packs", "shaders", "worlds", "shots", "settings", "overview", "loader"). */
    var requestedSubTab by mutableStateOf("mods")

    val selected: AerixInstance?
        get() = instances.getOrNull(selectedIndex)

    private val reloadGeneration = java.util.concurrent.atomic.AtomicLong()
    private var attached = false
    private var appContext: Context? = null

    private val mcVersionRegex = Regex("""(\d+\.\d+(?:\.\d+)?)""")

    // Held strongly: ExtraCore keeps only weak references.
    private val refreshListener = ExtraListener<Any> { _, _ ->
        Tools.runOnUiThread { reload() }
        false
    }

    fun attach(context: Context) {
        appContext = context.applicationContext
        if (attached) return
        attached = true
        ExtraCore.addExtraListener(ExtraConstants.REFRESH_VERSION_SPINNER, refreshListener)
    }

    /** Reloads the instance list off the main thread, same source as Pojav's mcVersionSpinner. */
    fun reload() {
        val context = appContext ?: return
        val generation = reloadGeneration.incrementAndGet()
        val editorId = PojavInstanceEditorBridge.draft?.instance?.instanceId
        loading = true
        PojavApplication.sExecutorService.execute {
            val result = runCatching {
                val display = Instances.loadDisplay()
                // Modpack instances without the pack's own artwork get it in the background; the
                // refresh it fires redraws this list with the publisher's logo in place.
                PojavModpackIcons.sweep(context, display.list)
                val rows = uniqueInstanceRows(display.list.map { instance ->
                    val icon = runCatching {
                        InstanceIconProvider.fetchIcon(context.resources, instance).toAerixBitmap()
                    }.getOrNull()
                    AerixInstance(
                        source = instance,
                        name = displayName(context, instance),
                        versionId = instance.versionId ?: "",
                        icon = icon
                    )
                })
                val editorInstance = editorId?.let { runCatching { Instances.loadInstance(it) }.getOrNull() }
                val selectedId = display.list.getOrNull(display.selectedIndex)?.instanceId
                Triple(rows, rows.indexOfFirst { it.id == selectedId }, editorInstance)
            }
            Tools.runOnUiThread {
                // A slower earlier scan must not overwrite a just-completed installation.
                if (generation != reloadGeneration.get()) return@runOnUiThread
                result.onSuccess { (rows, index, editorInstance) ->
                    instances = rows
                    selectedIndex = index
                    editorInstance?.let {
                        PojavInstanceEditorBridge.syncLoaderState(it)
                        PojavInstanceEditorBridge.syncIcon(context, it)
                    }
                }.onFailure { Tools.showErrorRemote(it) }
                loading = false
            }
        }
    }

    private fun displayName(context: Context, instance: DisplayInstance): String {
        val name = instance.name
        if (!name.isNullOrBlank()) return name
        return when (instance.versionId) {
            Instance.VERSION_LATEST_RELEASE -> context.getString(R.string.profiles_latest_release)
            Instance.VERSION_LATEST_SNAPSHOT -> context.getString(R.string.profiles_latest_snapshot)
            null -> context.getString(R.string.unnamed)
            else -> instance.versionId
        }
    }

    fun select(instance: AerixInstance) {
        Instances.setSelectedInstance(instance.source)
        selectedIndex = instances.indexOf(instance)
    }

    /** Opens the Aerix instance management / settings page on [subTab]. */
    fun edit(activity: FragmentActivity, instance: AerixInstance, subTab: String = "mods") {
        select(instance)
        requestedSubTab = subTab
        if (PojavInstanceEditorBridge.open(activity)) AerixNav.navigate(LauncherSection.INSTANCE_EDITOR)
    }

    /** Creates a vanilla instance and opens its settings. */
    fun createVanilla(activity: FragmentActivity) {
        try {
            val instance = Instances.createDefaultInstance()
            Instances.setSelectedInstance(instance)
            reload()
            requestedSubTab = "settings"
            if (PojavInstanceEditorBridge.open(activity)) AerixNav.navigate(LauncherSection.INSTANCE_EDITOR)
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.aerix_discover_create_failed, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
        }
    }

    /** Opens the selected instance's game directory, mirroring MainMenuFragment.openGameDirectory. */
    fun openGameDirectory(context: Context) {
        val instance = Instances.loadSelectedInstance()
        if (instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show()
            return
        }
        val gameDirectory = instance.gameDirectory
        if (FileUtils.ensureDirectorySilently(gameDirectory)) {
            Tools.openPath(context, gameDirectory, false)
        } else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    // ---- Pin / Rename / Copy / Delete / Export for Instance Management -----------------------

    fun pinnedSet(): Set<String> = runCatching {
        val arr = JsonParser.parseString(AerixPreferences.pinnedJson).asJsonArray
        arr.mapNotNull { if (it.isJsonNull) null else it.asString }.toSet()
    }.getOrDefault(emptySet())

    fun isPinned(instance: AerixInstance): Boolean = instance.name in pinnedSet()

    fun togglePinned(instance: AerixInstance) {
        val set = pinnedSet().toMutableSet()
        if (!set.add(instance.name)) set.remove(instance.name)
        val arr = JsonArray()
        set.forEach { arr.add(it) }
        AerixPreferences.savePinnedJson(arr.toString())
    }

    fun rename(instance: AerixInstance, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        select(instance)
        PojavApplication.sExecutorService.execute {
            runCatching {
                val loaded = Instances.loadSelectedInstance() ?: return@execute
                loaded.name = trimmed
                loaded.write()
                Instances.renameInstanceDirectory(loaded, trimmed)
            }
            Tools.runOnUiThread {
                ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, Object())
            }
        }
    }

    fun copy(context: Context, instance: AerixInstance, copyName: String) {
        val trimmed = copyName.trim().ifEmpty { "${instance.name} Copy" }
        select(instance)
        PojavApplication.sExecutorService.execute {
            val result = runCatching {
                val src = Instances.loadSelectedInstance() ?: error("No instance")
                val created = Instances.createInstance({ dst ->
                    dst.name = trimmed
                    dst.versionId = src.versionId
                    dst.icon = src.icon
                    dst.renderer = src.renderer
                    dst.jvmArgs = src.jvmArgs
                    dst.argsMode = src.argsMode
                    dst.selectedRuntime = src.selectedRuntime
                    dst.controlLayout = src.controlLayout
                    dst.sharedData = src.sharedData
                }, trimmed.take(16))
                if (!src.sharedData) {
                    val srcDir = src.gameDirectory
                    val dstDir = created.gameDirectory
                    for (folder in listOf("mods", "config", "resourcepacks", "shaderpacks", "options.txt")) {
                        val s = File(srcDir, folder)
                        val d = File(dstDir, folder)
                        if (s.isDirectory) org.apache.commons.io.FileUtils.copyDirectory(s, d)
                        else if (s.isFile) org.apache.commons.io.FileUtils.copyFile(s, d)
                    }
                }
                Instances.setSelectedInstance(created)
            }
            Tools.runOnUiThread {
                result.onSuccess {
                    Toast.makeText(context, "Duplicated \"$trimmed\"", Toast.LENGTH_SHORT).show()
                    ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, Object())
                }.onFailure { Tools.showErrorRemote(it) }
            }
        }
    }

    fun deleteInstance(instance: AerixInstance) {
        select(instance)
        PojavApplication.sExecutorService.execute {
            runCatching {
                val loaded = Instances.loadSelectedInstance() ?: return@execute
                Instances.removeInstance(loaded)
            }
            Tools.runOnUiThread {
                ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, Object())
            }
        }
    }

    /**
     * Exports [instance] as a Modrinth (.mrpack), CurseForge (.zip) or MultiMC (.zip) modpack archive
     * into `<DIR_GAME_HOME>/exports/` and opens the Android share sheet for the created file.
     */
    fun exportModpack(
        context: Context,
        instance: AerixInstance,
        format: ModpackExportFormat,
        packName: String,
        packVersion: String,
        packAuthor: String,
        includeFolders: Set<String>,
        onFinished: (File?) -> Unit
    ) {
        select(instance)
        PojavApplication.sExecutorService.execute {
            val created = runCatching {
                val loaded = Instances.loadSelectedInstance() ?: error("No instance selected")
                val gameDir = loaded.gameDirectory
                val exportsDir = File(Tools.DIR_GAME_HOME, "exports").also { it.mkdirs() }
                val safeName = FileUtils.escapeFileName(packName.ifBlank { instance.name }).ifBlank { "modpack" }
                val safeVer = FileUtils.escapeFileName(packVersion.ifBlank { "1.0.0" }).ifBlank { "1.0.0" }
                val outFile = File(exportsDir, "$safeName-$safeVer.${format.extension}")

                val versionId = instance.versionId
                val mcVersion = mcVersionRegex.find(versionId)?.value ?: versionId.ifBlank { "1.20.1" }
                val idLower = versionId.lowercase()
                val loaderPair: Pair<String, String>? = when {
                    idLower.contains("neoforge") -> "neoforge" to versionId.substringAfterLast('-', "latest")
                    idLower.contains("fabric") -> "fabric-loader" to versionId.substringAfter("fabric-loader-", "").substringBefore('-').ifBlank { "0.16.9" }
                    idLower.contains("quilt") -> "quilt-loader" to versionId.substringAfter("quilt-loader-", "").substringBefore('-').ifBlank { "0.26.0" }
                    idLower.contains("forge") -> "forge" to versionId.substringAfterLast('-', "latest")
                    else -> null
                }

                ZipOutputStream(FileOutputStream(outFile)).use { zip ->
                    when (format) {
                        ModpackExportFormat.MODRINTH -> {
                            val root = JsonObject().apply {
                                addProperty("formatVersion", 1)
                                addProperty("game", "minecraft")
                                addProperty("versionId", packVersion.ifBlank { "1.0.0" })
                                addProperty("name", packName.ifBlank { instance.name })
                                addProperty("summary", "Exported by Aerix Launcher ($packAuthor)")
                                add("files", JsonArray())
                                val deps = JsonObject().apply {
                                    addProperty("minecraft", mcVersion)
                                    if (loaderPair != null) addProperty(loaderPair.first, loaderPair.second)
                                }
                                add("dependencies", deps)
                            }
                            putTextEntry(zip, "modrinth.index.json", root.toString())
                            writeIncludedFolders(zip, gameDir, "overrides", includeFolders)
                        }
                        ModpackExportFormat.CURSEFORGE -> {
                            val modLoaders = JsonArray()
                            if (loaderPair != null) {
                                modLoaders.add(JsonObject().apply {
                                    addProperty("id", "${loaderPair.first}-${loaderPair.second}")
                                    addProperty("primary", true)
                                })
                            }
                            val manifest = JsonObject().apply {
                                add("minecraft", JsonObject().apply {
                                    addProperty("version", mcVersion)
                                    add("modLoaders", modLoaders)
                                })
                                addProperty("manifestType", "minecraftModpack")
                                addProperty("manifestVersion", 1)
                                addProperty("name", packName.ifBlank { instance.name })
                                addProperty("version", packVersion.ifBlank { "1.0.0" })
                                addProperty("author", packAuthor.ifBlank { "Aerix" })
                                add("files", JsonArray())
                                addProperty("overrides", "overrides")
                            }
                            putTextEntry(zip, "manifest.json", manifest.toString())
                            writeIncludedFolders(zip, gameDir, "overrides", includeFolders)
                        }
                        ModpackExportFormat.MULTIMC -> {
                            val cfg = buildString {
                                appendLine("InstanceType=OneSix")
                                appendLine("name=${packName.ifBlank { instance.name }}")
                                appendLine("notes=Exported by Aerix Launcher")
                            }
                            val components = JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("uid", "net.minecraft")
                                    addProperty("version", mcVersion)
                                    addProperty("important", true)
                                })
                            }
                            val mmcPack = JsonObject().apply {
                                addProperty("formatVersion", 1)
                                add("components", components)
                            }
                            putTextEntry(zip, "instance.cfg", cfg)
                            putTextEntry(zip, "mmc-pack.json", mmcPack.toString())
                            writeIncludedFolders(zip, gameDir, ".minecraft", includeFolders)
                        }
                    }
                }
                outFile
            }.getOrNull()

            Tools.runOnUiThread {
                onFinished(created)
                if (created != null) {
                    Toast.makeText(context, "Exported ${created.name}", Toast.LENGTH_LONG).show()
                    runCatching { Tools.openPath(context, created, true) }
                } else {
                    Toast.makeText(context, "Modpack export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun putTextEntry(zip: ZipOutputStream, entryName: String, content: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeIncludedFolders(
        zip: ZipOutputStream,
        gameDir: File,
        prefix: String,
        includeFolders: Set<String>
    ) {
        for (item in includeFolders) {
            val target = File(gameDir, item)
            if (!target.exists()) continue
            zipRecursively(zip, target, "$prefix/$item")
        }
    }

    private fun zipRecursively(zip: ZipOutputStream, file: File, entryPath: String) {
        if (file.name.startsWith(".")) return
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                zipRecursively(zip, child, "$entryPath/${child.name}")
            }
        } else if (file.isFile) {
            zip.putNextEntry(ZipEntry(entryPath))
            FileInputStream(file).use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}
