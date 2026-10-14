/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: Pojav instances (net.kdt.pojavlaunch.instances.*) exposed to the Aerix Library / Home UI.
 * Replaces the UI-facing parts of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/game/version/installed/VersionsManager.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/VersionsManageScreen.kt (list state)
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
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.components.toAerixBitmap
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.extra.ExtraListener
import net.kdt.pojavlaunch.fragments.InstanceEditorFragment
import net.kdt.pojavlaunch.instances.DisplayInstance
import net.kdt.pojavlaunch.instances.Instance
import net.kdt.pojavlaunch.instances.InstanceIconProvider
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.utils.FileUtils

/** One row of the Aerix library. [source] is the untouched Pojav object handed back to Pojav APIs. */
class AerixInstance(
    val source: DisplayInstance,
    val name: String,
    val versionId: String,
    val icon: ImageBitmap?
)

object PojavInstanceBridge {
    var instances by mutableStateOf<List<AerixInstance>>(emptyList())
        private set
    var selectedIndex by mutableIntStateOf(-1)
        private set
    var loading by mutableStateOf(false)
        private set

    val selected: AerixInstance?
        get() = instances.getOrNull(selectedIndex)

    private var attached = false
    private var appContext: Context? = null

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
        loading = true
        PojavApplication.sExecutorService.execute {
            val result = runCatching {
                val display = Instances.loadDisplay()
                val rows = display.list.map { instance ->
                    val icon = runCatching {
                        InstanceIconProvider.fetchIcon(context.resources, instance).toAerixBitmap()
                    }.getOrNull()
                    AerixInstance(
                        source = instance,
                        name = displayName(context, instance),
                        versionId = instance.versionId ?: "",
                        icon = icon
                    )
                }
                rows to display.selectedIndex
            }
            Tools.runOnUiThread {
                result.onSuccess { (rows, index) ->
                    instances = rows
                    selectedIndex = index
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

    /** Opens the Aerix instance settings page (rename, icon, version, renderer, runtime, controls, JVM args, delete). */
    fun edit(activity: FragmentActivity, instance: AerixInstance) {
        select(instance)
        if (PojavInstanceEditorBridge.open(activity)) AerixNav.navigate(LauncherSection.INSTANCE_EDITOR)
    }

    /** Pojav's original InstanceEditorFragment for the given instance, kept reachable from the Aerix page. */
    fun editClassic(activity: FragmentActivity) {
        Tools.swapFragment(activity, InstanceEditorFragment::class.java, InstanceEditorFragment.TAG, null)
    }

    /** Creates a vanilla instance and opens its settings, exactly like ProfileTypeSelectFragment's vanilla button. */
    fun createVanilla(activity: FragmentActivity) {
        try {
            val instance = Instances.createDefaultInstance()
            Instances.setSelectedInstance(instance)
            reload()
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
}
