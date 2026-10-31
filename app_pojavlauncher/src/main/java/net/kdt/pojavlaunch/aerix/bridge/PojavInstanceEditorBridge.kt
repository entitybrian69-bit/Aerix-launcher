/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter behind the Aerix instance settings page. Reads and writes exactly the fields Pojav's
 * InstanceEditorFragment edits (name, icon, versionId, renderer, selectedRuntime, controlLayout, jvmArgs,
 * sharedData) plus Instance.argsMode, with the same rules:
 *   renderer null       -> global renderer (LauncherPreferences.PREF_RENDERER)
 *   selectedRuntime null -> "<Default>" runtime or a runtime without versionString
 *   empty controlLayout / jvmArgs -> null
 *   save: InstanceIconProvider.dropIcon, Instances.renameInstanceDirectory when the name changed, Instance.write
 *   delete: InstanceIconProvider.dropIcon + Instances.removeInstance (DeleteConfirmDialogFragment)
 * Replaces the UI state of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/VersionConfigScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/VersionOverViewScreen.kt
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
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.components.toAerixBitmap
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.fragments.FileSelectorFragment
import net.kdt.pojavlaunch.game.renderer.RendererCache
import net.kdt.pojavlaunch.instances.Instance
import net.kdt.pojavlaunch.instances.InstanceIconProvider
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.multirt.MultiRTUtils
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.utils.CropperUtils
import net.kdt.pojavlaunch.utils.FileUtils

/** Java runtime choice. [name] null is Pojav's "<Default>" entry. */
class AerixRuntimeChoice(val name: String?, val label: String)

/** Renderer choice. [id] null keeps the global renderer. */
class AerixRendererChoice(val id: String?, val label: String)

/** Editable copy of one Pojav Instance. Nothing reaches disk until [PojavInstanceEditorBridge.save]. */
class AerixInstanceDraft(val instance: Instance, icon: ImageBitmap?) {
    var name by mutableStateOf(instance.name ?: "")
    var versionId by mutableStateOf(instance.versionId ?: "")
    var renderer by mutableStateOf(instance.renderer?.takeIf { it.isNotEmpty() })
    var runtime by mutableStateOf(instance.selectedRuntime?.takeIf { it.isNotEmpty() })
    var controlLayout by mutableStateOf(instance.controlLayout?.takeIf { it.isNotEmpty() })
    var jvmArgs by mutableStateOf(instance.jvmArgs ?: "")
    var argsMode by mutableIntStateOf(instance.argsMode)
    var sharedData by mutableStateOf(instance.sharedData)
    var icon by mutableStateOf(icon)
    /** Icon cropped on this page, written by save() through Instance.encodeNewIcon. */
    var pendingIcon: Bitmap? = null
}

object PojavInstanceEditorBridge {
    var draft by mutableStateOf<AerixInstanceDraft?>(null)
        private set

    /** True while Pojav's FileSelectorFragment was opened for the control layout. */
    private var awaitingControlLayout = false

    /** Loads the selected instance into a draft, as InstanceEditorFragment.loadValues does. Returns false without one. */
    fun open(context: Context): Boolean {
        val instance = Instances.loadSelectedInstance()
        if (instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show()
            return false
        }
        val icon = runCatching { InstanceIconProvider.fetchIcon(context.resources, instance).toAerixBitmap() }.getOrNull()
        draft = AerixInstanceDraft(instance, icon)
        return true
    }

    /**
     * Re-read the icon shown on the open editor after something gave the instance new artwork - a
     * modpack install, or the sweep that restores a pack's own logo.
     */
    internal fun syncIcon(context: Context, updated: Instance) {
        val current = draft ?: return
        if (current.instance.instanceId != updated.instanceId) return
        current.icon = runCatching {
            InstanceIconProvider.fetchIcon(context.resources, updated).toAerixBitmap()
        }.getOrNull()
    }

    /** Keep an open editor from saving stale loader metadata after an external installer returns. */
    internal fun syncLoaderState(installed: Instance) {
        val current = draft ?: return
        if (current.instance.instanceId != installed.instanceId) return
        if (current.instance.versionId != installed.versionId) {
            current.versionId = installed.versionId.orEmpty()
            current.instance.versionId = installed.versionId
        }
        current.instance.installer = installed.installer
    }

    fun close() {
        draft = null
        awaitingControlLayout = false
    }

    // ---- choices --------------------------------------------------------------------------

    /** RendererCache.getCompatibleRenderers plus the trailing "global default" entry of the Pojav spinner. */
    fun rendererChoices(context: Context): List<AerixRendererChoice> {
        PojavPluginBridge.loadPlugins(context)
        val cache = RendererCache.getCompatibleRenderers(context)
        val result = ArrayList<AerixRendererChoice>(cache.rendererIds.size + 1)
        cache.rendererIds.forEachIndexed { index, id ->
            result.add(AerixRendererChoice(id, cache.rendererDisplayNames.getOrElse(index) { id }))
        }
        result.add(AerixRendererChoice(null, context.getString(R.string.global_default)))
        return result
    }

    fun rendererLabel(context: Context, id: String?): String {
        val choices = rendererChoices(context)
        if (id == null) {
            val global = choices.firstOrNull { it.id == LauncherPreferences.PREF_RENDERER }?.label ?: LauncherPreferences.PREF_RENDERER
            return context.getString(R.string.aerix_editor_global_value, global ?: "")
        }
        return choices.firstOrNull { it.id == id }?.label ?: id
    }

    /** MultiRTUtils.getRuntimes plus "<Default>", labelled like RTSpinnerAdapter. */
    fun runtimeChoices(context: Context): List<AerixRuntimeChoice> {
        val result = ArrayList<AerixRuntimeChoice>()
        runCatching { MultiRTUtils.getRuntimes() }.getOrDefault(emptyList()).forEach { runtime ->
            val version = runtime.versionString ?: context.getString(R.string.multirt_runtime_corrupt)
            // A runtime without versionString is saved as null, the same rule InstanceEditorFragment.save applies.
            result.add(AerixRuntimeChoice(if (runtime.versionString == null) null else runtime.name,
                runtime.name.replace(".tar.xz", "") + " - " + version))
        }
        result.add(AerixRuntimeChoice(null, "<Default>"))
        return result
    }

    fun runtimeLabel(context: Context, name: String?): String {
        if (name == null) {
            val global = LauncherPreferences.PREF_DEFAULT_RUNTIME
            return if (global.isNullOrEmpty()) context.getString(R.string.aerix_editor_auto)
            else context.getString(R.string.aerix_editor_global_value, global)
        }
        return runtimeChoices(context).firstOrNull { it.name == name }?.label ?: name
    }

    fun controlLayoutLabel(context: Context, layout: String?): String =
        layout ?: context.getString(R.string.aerix_editor_global_value, LauncherPreferences.PREF_DEFAULTCTRL_PATH.substringAfterLast('/'))

    fun memoryLabel(context: Context): String =
        context.getString(R.string.aerix_editor_memory_global, LauncherPreferences.PREF_RAM_ALLOCATION)

    // ---- control layout through Pojav's file selector -------------------------------------

    /** Opens FileSelectorFragment on the controls folder with the bundle InstanceEditorFragment uses. */
    fun pickControlLayout(activity: FragmentActivity) {
        awaitingControlLayout = true
        val bundle = Bundle(3)
        bundle.putBoolean(FileSelectorFragment.BUNDLE_SELECT_FOLDER, false)
        bundle.putString(FileSelectorFragment.BUNDLE_ROOT_PATH, Tools.CTRLMAP_PATH)
        Tools.swapFragment(activity, FileSelectorFragment::class.java, FileSelectorFragment.TAG, bundle)
    }

    /** Called when the Aerix shell resumes; takes the file selector result like InstanceEditorFragment.onCreateView. */
    fun consumeFileSelectorResult() {
        if (!awaitingControlLayout) return
        awaitingControlLayout = false
        val value = ExtraCore.consumeValue(ExtraConstants.FILE_SELECTOR) as? String ?: return
        draft?.controlLayout = value.ifEmpty { null }
    }

    // ---- save / delete --------------------------------------------------------------------

    fun save(context: Context): Boolean {
        val current = draft ?: return false
        val instance = current.instance
        InstanceIconProvider.dropIcon(instance)
        instance.versionId = current.versionId
        instance.controlLayout = current.controlLayout?.ifEmpty { null }
        instance.jvmArgs = current.jvmArgs.ifEmpty { null }
        instance.argsMode = current.argsMode
        instance.selectedRuntime = current.runtime
        instance.renderer = current.renderer
        instance.sharedData = current.sharedData
        val newName = current.name
        return try {
            current.pendingIcon?.let { instance.encodeNewIcon(it) }
            if (newName.isNotEmpty() && newName != instance.name) Instances.renameInstanceDirectory(instance, newName)
            instance.name = newName
            instance.write()
            InstanceIconProvider.dropIcon(instance)
            PojavInstanceBridge.reload()
            true
        } catch (e: Exception) {
            Tools.showError(context, e)
            false
        }
    }

    fun delete(context: Context): Boolean {
        val instance = draft?.instance ?: return false
        InstanceIconProvider.dropIcon(instance)
        return try {
            Instances.removeInstance(instance)
            PojavInstanceBridge.reload()
            true
        } catch (e: Exception) {
            Tools.showError(context, e)
            false
        }
    }

    /** Opens the instance game directory (shared data or the instance root), as MainMenuFragment does. */
    fun openFolder(context: Context) {
        val current = draft ?: return
        current.instance.sharedData = current.sharedData
        val directory = current.instance.gameDirectory
        if (FileUtils.ensureDirectorySilently(directory)) {
            Tools.openPath(context, directory, false)
        } else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    /** Hands the cropped icon to the draft; written on save. */
    fun applyIcon(bitmap: Bitmap) {
        val current = draft ?: return
        current.pendingIcon = bitmap
        current.icon = bitmap.asImageBitmap()
    }
}

/**
 * Pojav's image cropper (CropperUtils) for Compose pages. The launcher is registered by AerixMainFragment,
 * because ActivityResult registration has to happen while the fragment is created.
 */
object AerixIconCropper : CropperUtils.CropperReceiver {
    var launcher: ActivityResultLauncher<*>? = null
    private var target: ((Bitmap) -> Unit)? = null

    fun start(context: Context, onCropped: (Bitmap) -> Unit) {
        val current = launcher
        if (current == null) {
            Toast.makeText(context, R.string.aerix_editor_icon_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        target = onCropped
        CropperUtils.startCropper(current)
    }

    override fun getAspectRatio(): Float = 1f

    override fun getTargetMaxSide(): Int = 256

    override fun onCropped(contentBitmap: Bitmap) {
        target?.invoke(contentBitmap)
        target = null
    }

    override fun onFailed(exception: Exception) {
        target = null
        Tools.showErrorRemote(exception)
    }
}
