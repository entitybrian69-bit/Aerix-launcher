/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: the launcher-side install queue. A file, with the dependencies the user left ticked, is
 * installed into every instance the user picked a file at a time, off the screen that asked for it.
 * The work lives here rather than inside a composable, so closing the dialog, leaving the page or
 * switching section cannot stop it: that is what "minimize" means.
 * Replaces the task part of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/coroutine/TaskSystem.kt
 * Zalith Launcher 2 Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.bridge

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.Tools

enum class AerixInstallStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

/** One file going into one instance: the smallest step an install can stop between. */
class AerixInstallStep(
    val label: String,
    val target: AssetTarget?,
    val file: AssetFile,
    /** True when the step came from the dependency list rather than from the file the user tapped. */
    val dependency: Boolean
)

class AerixInstallTask internal constructor(
    val id: Long,
    val type: AssetType,
    /** The file the user picked, e.g. "sodium-fabric-0.6.0.jar". */
    val title: String,
    val projectTitle: String,
    val steps: List<AerixInstallStep>
) {
    internal lateinit var appContext: Context
    internal lateinit var project: AssetProject
    internal var job: Job? = null

    var status by mutableStateOf(AerixInstallStatus.QUEUED)
        internal set

    /** 0f..1f over every step of the job, the step in flight included. */
    var progress by mutableFloatStateOf(0f)
        internal set

    var completedSteps by mutableIntStateOf(0)
        internal set

    var failedSteps by mutableIntStateOf(0)
        internal set

    /** File being downloaded right now, empty while the task waits its turn. */
    var currentStep by mutableStateOf("")
        internal set

    /** Set when the task ends early: why it stopped. */
    var failure by mutableStateOf("")
        internal set

    val totalSteps: Int get() = steps.size.coerceAtLeast(1)

    val finished: Boolean
        get() = status == AerixInstallStatus.DONE || status == AerixInstallStatus.FAILED ||
            status == AerixInstallStatus.CANCELLED

    val dependencySteps: Int get() = steps.count { it.dependency }

    /** Instances this task touches, in the order the user picked them. */
    val instanceNames: List<String> get() = steps.mapNotNull { it.target?.name }.distinct()

    /** "Minecraft 1.21.4 and Warp 9", or the selected instance when no target was chosen. */
    val targetSummary: String
        get() {
            val names = instanceNames
            return when {
                names.isEmpty() -> ""
                names.size == 1 -> names.first()
                else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
            }
        }
}

/**
 * Process-lifetime install queue. Tasks are handed to one worker that runs them one after another,
 * so two installs can never write the same file at once. Each task gets its own job, so cancelling
 * one leaves the rest of the queue alone.
 */
object AerixInstallQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val pending = ArrayDeque<AerixInstallTask>()
    private var working = false
    private var nextId = 1L

    private val _tasks = mutableStateListOf<AerixInstallTask>()

    /** Newest first: the task the user just started sits at the top of the panel. */
    val tasks: List<AerixInstallTask> get() = _tasks.asReversed()

    /**
     * Whether the progress panel is on screen. Minimizing is nothing more than setting this false
     * while the work keeps running; tapping the pill sets it back to true.
     */
    var panelOpen by mutableStateOf(false)
        private set

    /** Tasks still queued or running: what the pill and the panel count. */
    val activeTasks: List<AerixInstallTask>
        get() = _tasks.filter {
            it.status == AerixInstallStatus.QUEUED || it.status == AerixInstallStatus.RUNNING
        }

    val running: Boolean get() = activeTasks.isNotEmpty()

    /** Anything worth showing: a running task, or a finished one the user has not dismissed. */
    val visible: Boolean get() = _tasks.isNotEmpty()

    /** 0f..1f across everything still to do, for the pill's bar. */
    val overallProgress: Float
        get() {
            val active = activeTasks
            if (active.isEmpty()) return 1f
            return active.sumOf { it.progress.toDouble() }.toFloat() / active.size
        }

    fun openPanel() {
        panelOpen = true
    }

    /** The minimize button: the panel goes away, the install does not. */
    fun minimizePanel() {
        panelOpen = false
    }

    fun dismiss(task: AerixInstallTask) {
        _tasks.remove(task)
        if (_tasks.isEmpty()) panelOpen = false
    }

    fun clearFinished() {
        _tasks.removeAll { it.finished }
        if (_tasks.isEmpty()) panelOpen = false
    }

    fun cancel(task: AerixInstallTask) {
        if (task.finished) return
        task.job?.cancel()
    }

    /**
     * Installs [file] into every target in [targets], each of them followed by the dependency files
     * the user left ticked. With no target the file goes through Pojav's own path, which is what a
     * modpack does (it brings its own instance) and what an install without a pickable instance
     * falls back to.
     */
    fun submit(
        context: Context,
        project: AssetProject,
        file: AssetFile,
        targets: List<AssetTarget>,
        dependencies: List<ResolvedDependency>
    ): AerixInstallTask {
        val appContext = context.applicationContext ?: context
        val dependencyFiles = dependencies.mapNotNull { it.file }
        val steps = ArrayList<AerixInstallStep>()
        if (targets.isEmpty()) {
            steps.add(AerixInstallStep(file.fileName, null, file, false))
        } else {
            for (target in targets) {
                steps.add(AerixInstallStep(file.fileName, target, file, false))
                for (dependency in dependencyFiles) {
                    steps.add(AerixInstallStep(dependency.fileName, target, dependency, true))
                }
            }
        }
        val task = AerixInstallTask(
            id = nextId++,
            type = project.type,
            title = file.name.ifBlank { file.fileName },
            projectTitle = project.title,
            steps = steps
        )
        task.appContext = appContext
        task.project = project
        _tasks.add(task)
        panelOpen = true
        enqueue(task)
        return task
    }

    /**
     * Adds the task to the queue and, if nothing is working, starts the worker. Both happen under
     * the same lock, so a task added while the worker is on its way out is never left waiting.
     */
    private fun enqueue(task: AerixInstallTask) {
        synchronized(lock) {
            pending.addLast(task)
            if (working) return
            working = true
        }
        scope.launch {
            while (true) {
                val next = synchronized(lock) {
                    val head = pending.removeFirstOrNull()
                    if (head == null) working = false
                    head
                } ?: break
                // A child job per task: cancelling one must not take the worker down with it.
                val job = launch { runTask(next) }
                next.job = job
                job.join()
            }
        }
    }

    private suspend fun runTask(task: AerixInstallTask) {
        update { task.status = AerixInstallStatus.RUNNING }
        var done = 0
        var failed = 0
        try {
            for ((index, step) in task.steps.withIndex()) {
                update {
                    task.currentStep = step.label
                    task.progress = index / task.totalSteps.toFloat()
                }
                val target = step.target
                val ok = when {
                    // A modpack needs its project, not just the file: it becomes a new instance.
                    task.type == AssetType.MODPACK ->
                        PojavAssetBridge.install(task.appContext, task.project, step.file)
                    target == null -> PojavAssetBridge.install(task.appContext, task.type, step.file)
                    else -> PojavAssetBridge.installInto(task.appContext, task.type, step.file, target) { current, total ->
                        if (total > 0) {
                            val within = (current.toFloat() / total).coerceIn(0f, 1f)
                            update { task.progress = (index + within) / task.totalSteps.toFloat() }
                        }
                    }
                }
                if (ok) done++ else failed++
                update {
                    task.completedSteps = done
                    task.failedSteps = failed
                    task.progress = (index + 1) / task.totalSteps.toFloat()
                }
            }
        } catch (cancelled: CancellationException) {
            update {
                task.status = AerixInstallStatus.CANCELLED
                task.currentStep = ""
            }
            throw cancelled
        } catch (error: Throwable) {
            failed++
            update {
                task.failedSteps = failed
                task.failure = error.message.orEmpty()
            }
        }
        update {
            task.status = if (failed > 0) AerixInstallStatus.FAILED else AerixInstallStatus.DONE
            task.currentStep = ""
            task.progress = 1f
            task.completedSteps = done
            task.failedSteps = failed
        }
    }

    /**
     * State is written from the worker thread. Compose state is thread safe, but writing it where
     * the rest of the launcher writes it keeps snapshot ordering predictable.
     */
    private fun update(block: () -> Unit) {
        runCatching { Tools.runOnUiThread(block) }.getOrElse { block() }
    }
}
