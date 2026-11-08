/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: exposes ProgressKeeper's task count to Compose.
 * Replaces the task indicator of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/home/MiraiHomeDashboard.kt (TaskSummaryPanel)
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/coroutine/TaskSystem.kt (UI-facing part only)
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener

/** Read-only view of Pojav's background task counter. ProgressKeeper stays the owner. */
object PojavTaskBridge {
    var taskCount by mutableIntStateOf(0)
        private set

    private var attached = false

    // Held strongly by this object for the lifetime of the process.
    private val listener = TaskCountListener { count ->
        Tools.runOnUiThread { taskCount = count }
        false
    }

    fun attach() {
        if (attached) return
        attached = true
        ProgressKeeper.addTaskCountListener(listener)
    }

    fun hasTasks(): Boolean = ProgressKeeper.getTaskCount() > 0
}
