/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: exposes ProgressKeeper task counts and current progress to Compose.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.bridge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kdt.mcgui.ProgressLayout
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.progresskeeper.ProgressListener
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener

/** Read-only Compose view of Pojav's background task counter and its latest useful progress. */
object PojavTaskBridge {
    var taskCount by mutableIntStateOf(0)
        private set
    var taskProgressPercent by mutableIntStateOf(-1)
        private set
    var taskProgressTitle by mutableStateOf("Assets")
        private set

    private val progressLock = Any()
    private val activeProgress = LinkedHashMap<String, Int>()
    private val progressKeys = listOf(
        ProgressLayout.DOWNLOAD_GAME,
        ProgressLayout.INSTANCE_INSTALL,
        ProgressLayout.INSTALL_MODPACK,
        ProgressLayout.UNPACK_RUNTIME,
        ProgressLayout.EXTRACT_COMPONENTS,
        ProgressLayout.EXTRACT_SINGLE_FILES,
        ProgressLayout.DATA_MIGRATION,
        ProgressLayout.DOWNLOAD_VERSION_LIST,
        ProgressLayout.AUTHENTICATE
    )
    private val priorityKeys = listOf(
        ProgressLayout.DOWNLOAD_GAME,
        ProgressLayout.INSTANCE_INSTALL,
        ProgressLayout.INSTALL_MODPACK,
        ProgressLayout.UNPACK_RUNTIME,
        ProgressLayout.EXTRACT_COMPONENTS,
        ProgressLayout.EXTRACT_SINGLE_FILES,
        ProgressLayout.DATA_MIGRATION,
        ProgressLayout.DOWNLOAD_VERSION_LIST,
        ProgressLayout.AUTHENTICATE
    )
    private val progressListeners = progressKeys.associateWith { key ->
        object : ProgressListener {
            override fun onProgressStarted() {
                synchronized(progressLock) {
                    activeProgress.remove(key)
                    activeProgress[key] = -1
                }
                publishProgress()
            }

            override fun onProgressUpdated(progress: Int, resid: Int, vararg va: Any?) {
                synchronized(progressLock) {
                    activeProgress[key] = progress.takeIf { it in 1..100 } ?: -1
                }
                publishProgress()
            }

            override fun onProgressEnded() {
                synchronized(progressLock) { activeProgress.remove(key) }
                publishProgress()
            }
        }
    }

    private var attached = false

    // Held strongly by this object for the lifetime of the process.
    private val listener = TaskCountListener { count ->
        Tools.runOnUiThread { taskCount = count }
        false
    }

    @Synchronized
    fun attach() {
        if (attached) return
        attached = true
        ProgressKeeper.addTaskCountListener(listener)
        progressListeners.forEach { (key, progressListener) ->
            ProgressKeeper.addListener(key, progressListener)
        }
    }

    fun hasTasks(): Boolean = ProgressKeeper.getTaskCount() > 0

    private fun publishProgress() {
        val snapshot = synchronized(progressLock) {
            val key = priorityKeys.firstOrNull { activeProgress.containsKey(it) }
                ?: activeProgress.keys.lastOrNull()
            if (key == null) "Assets" to -1
            else progressTitle(key) to (activeProgress[key] ?: -1)
        }
        Tools.runOnUiThread {
            taskProgressTitle = snapshot.first
            taskProgressPercent = snapshot.second
        }
    }

    private fun progressTitle(key: String): String = when (key) {
        ProgressLayout.DOWNLOAD_GAME, ProgressLayout.INSTANCE_INSTALL, ProgressLayout.INSTALL_MODPACK -> "Assets"
        ProgressLayout.UNPACK_RUNTIME, ProgressLayout.EXTRACT_COMPONENTS, ProgressLayout.EXTRACT_SINGLE_FILES -> "Preparing"
        ProgressLayout.DOWNLOAD_VERSION_LIST -> "Game versions"
        ProgressLayout.DATA_MIGRATION -> "Moving files"
        ProgressLayout.AUTHENTICATE -> "Signing in"
        else -> "Working"
    }
}
