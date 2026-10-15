/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The download that is currently running: the queue of files, the progress of the file that is
 * being written and the state of the run. The launcher draws it as a page that can be minimized
 * to a small progress bar, so the user keeps browsing the app while files are downloaded.
 * The downloads themselves are done by PojavAssetBridge, this object only keeps the state and
 * starts the run.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.bridge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One file of a running download. */
class DownloadEntry(
    /** Name of the mod or of the dependency the file belongs to. */
    val title: String,
    val fileName: String,
    val sizeBytes: Long,
    /** Instance the file is written to. */
    val targetName: String
)

/** State of the download that is running. */
enum class DownloadState { IDLE, RUNNING, DONE, FAILED }

/**
 * The download that is running. The launcher shows it as a page with a minimize button; minimized,
 * only a small progress bar stays on screen.
 */
object AerixDownloadSession {
    val entries = mutableStateListOf<DownloadEntry>()

    /** Position of the file that is being written. */
    var index by mutableIntStateOf(0)
        private set

    /** Progress of the file that is being written, 0 to 1. */
    var fileProgress by mutableFloatStateOf(0f)
        private set

    var state by mutableStateOf(DownloadState.IDLE)
        private set

    /** Text shown under the title, e.g. the name of the file that failed. */
    var message by mutableStateOf<String?>(null)
        private set

    /** True when the download page is collapsed to a small progress bar. */
    var minimized by mutableStateOf(false)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Progress of the whole run, 0 to 1. */
    val progress: Float
        get() {
            val total = entries.size
            if (total <= 0) return 0f
            return ((index + fileProgress) / total).coerceIn(0f, 1f)
        }

    val running: Boolean get() = state == DownloadState.RUNNING

    /** File that is being written right now. */
    fun currentEntry(): DownloadEntry? = entries.getOrNull(index)

    /**
     * Queues [file] and the picked [dependencies] for every instance of [targets] and starts the
     * download. Nothing happens when a download is already running.
     */
    fun start(
        type: AssetType,
        project: AssetProject,
        file: AssetFile,
        dependencies: List<Pair<AssetDependency, AssetFile>>,
        targets: List<AssetInstallTarget>
    ) {
        if (state == DownloadState.RUNNING) return
        if (targets.isEmpty()) return

        val queue = ArrayList<DownloadTask>()
        for (target in targets) {
            queue.add(
                DownloadTask(
                    entry = DownloadEntry(project.title, file.fileName, file.sizeBytes, target.name),
                    file = file,
                    target = target
                )
            )
            for ((dependency, dependencyFile) in dependencies) {
                queue.add(
                    DownloadTask(
                        entry = DownloadEntry(
                            dependency.title,
                            dependencyFile.fileName,
                            dependencyFile.sizeBytes,
                            target.name
                        ),
                        file = dependencyFile,
                        target = target
                    )
                )
            }
        }
        if (queue.isEmpty()) return

        entries.clear()
        for (task in queue) entries.add(task.entry)
        index = 0
        fileProgress = 0f
        message = null
        minimized = false
        state = DownloadState.RUNNING

        scope.launch {
            var failed = 0
            var failedName: String? = null
            for ((position, task) in queue.withIndex()) {
                index = position
                fileProgress = 0f
                val ok = PojavAssetBridge.installInto(task.target, type, task.file) { current, max ->
                    if (max > 0) fileProgress = (current.toFloat() / max).coerceIn(0f, 1f)
                }
                if (!ok) {
                    failed++
                    if (failedName == null) failedName = task.entry.fileName
                }
            }
            fileProgress = 1f
            message = failedName
            state = if (failed == 0) DownloadState.DONE else DownloadState.FAILED
        }
    }

    /** Hides the download page, the progress bar stays on screen. */
    fun minimize() {
        minimized = true
    }

    /** Shows the download page again. */
    fun expand() {
        minimized = false
    }

    /** Removes the finished download from the screen. */
    fun dismiss() {
        if (state == DownloadState.RUNNING) return
        state = DownloadState.IDLE
        entries.clear()
        index = 0
        fileProgress = 0f
        message = null
    }

    private class DownloadTask(
        val entry: DownloadEntry,
        val file: AssetFile,
        val target: AssetInstallTarget
    )
}
