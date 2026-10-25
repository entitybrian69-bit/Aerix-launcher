package net.kdt.pojavlaunch.aerix.bridge

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import java.io.File

/** A fresh snapshot of installed layouts, never the importer's unverified staging file. */
internal class ControlLayoutCatalog(
    private val directory: File,
    private val preferences: SharedPreferences
) : DefaultLifecycleObserver {
    var files by mutableStateOf(emptyList<File>())
        private set
    var selectedPath by mutableStateOf(defaultFile.absolutePath)
        private set
    private val defaultFile get() = File(directory, "default.json")

    init { refresh() }

    fun refresh() {
        files = directory.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true) &&
                !file.name.equals("TMP_IMPORT_FILE.json", ignoreCase = true)
        }?.sortedWith(compareBy<File> { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.name })
            .orEmpty()
        selectedPath = preferences.getString("defaultCtrl", defaultFile.absolutePath)
            ?: defaultFile.absolutePath
    }

    // ImportControlActivity uses singleInstance: activity results are not a reliable refresh signal.
    // Also covers layouts saved/renamed by the separate custom-controls editor.
    override fun onResume(owner: LifecycleOwner) = refresh()

    fun select(file: File): Boolean {
        if (file !in files || !file.isFile) return false
        preferences.edit().putString("defaultCtrl", file.absolutePath).apply()
        LauncherPreferences.PREF_DEFAULTCTRL_PATH = file.absolutePath
        selectedPath = file.absolutePath
        PojavSettingsBridge.touch()
        return true
    }

    fun delete(file: File): Boolean {
        if (file !in files || file.name.equals("default.json", ignoreCase = true) || !file.delete()) return false
        if (selectedPath == file.absolutePath) {
            // Never leave the global selection pointing at a deleted layout.
            preferences.edit().remove("defaultCtrl").apply()
            LauncherPreferences.PREF_DEFAULTCTRL_PATH = defaultFile.absolutePath
            PojavSettingsBridge.touch()
        }
        refresh()
        return true
    }
}

/** Shared by global Layouts and the instance's layout dropdown. */
@Composable
internal fun rememberControlLayoutCatalog(activity: FragmentActivity): ControlLayoutCatalog {
    val catalog = remember(activity) {
        ControlLayoutCatalog(File(Tools.CTRLMAP_PATH).also { it.mkdirs() }, LauncherPreferences.DEFAULT_PREF)
    }
    DisposableEffect(activity, catalog) {
        activity.lifecycle.addObserver(catalog)
        // Needed even when this tab is first opened while the activity is already resumed.
        catalog.refresh()
        onDispose { activity.lifecycle.removeObserver(catalog) }
    }
    LaunchedEffect(PojavSettingsBridge.revision) { catalog.refresh() }
    return catalog
}
