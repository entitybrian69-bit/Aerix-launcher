/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Section state of the Aerix shell, kept outside the composition so it survives the Compose view being
 * recreated when a Pojav fragment (file selector, login, installer) is pushed and popped.
 * Plays the role of the Navigation3 back stacks of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/viewmodel/ScreenBackStackViewModel.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/MainScreen.kt (rail onNavigate)
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

package net.kdt.pojavlaunch.aerix.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences

object AerixNav {
    /** Settings tab id shown while the Wallpapers rail entry is active (mirai NormalNavKey.Settings.Wallpapers). */
    const val TAB_WALLPAPERS = "wallpapers"
    const val TAB_APPEARANCE = "appearance"

    private val pageSections = setOf(
        LauncherSection.CREATE_INSTANCE,
        LauncherSection.INSTANCE_EDITOR,
        LauncherSection.ADD_ACCOUNT,
        LauncherSection.OFFLINE_ACCOUNT
    )

    var section by mutableStateOf(LauncherSection.HOME)
        private set

    /** Last Settings tab other than Wallpapers, restored when the Settings rail entry is pressed. */
    var settingsTab by mutableStateOf(TAB_APPEARANCE)

    private val stack = ArrayList<LauncherSection>()
    private var initialized = false

    val canGoBack: Boolean
        get() = section in pageSections

    fun init() {
        if (initialized) return
        initialized = true
        val saved = runCatching { LauncherSection.valueOf(AerixPreferences.lastSection) }.getOrDefault(LauncherSection.HOME)
        section = if (saved in pageSections) LauncherSection.HOME else saved
    }

    fun isPage(target: LauncherSection): Boolean = target in pageSections

    /** Opens [target]. Pages remember where they were opened from; sections reset the page stack. */
    fun navigate(target: LauncherSection) {
        val apply = {
            if (target in pageSections) {
                if (section != target) {
                    stack.remove(target)
                    stack.add(section)
                }
            } else {
                stack.clear()
                AerixPreferences.saveLastSection(target.name)
            }
            section = target
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) apply() else Tools.runOnUiThread { apply() }
    }

    /** Leaves the current page. Returns false when a top-level section is shown. */
    fun back(): Boolean {
        if (section !in pageSections) return false
        val previous = if (stack.isEmpty()) fallbackFor(section) else stack.removeAt(stack.size - 1)
        section = previous
        if (previous !in pageSections) AerixPreferences.saveLastSection(previous.name)
        return true
    }

    /** Leaves every page and shows [target] (used after a page finished its job). */
    fun finishTo(target: LauncherSection) {
        stack.clear()
        navigate(target)
    }

    private fun fallbackFor(page: LauncherSection): LauncherSection = when (page) {
        LauncherSection.OFFLINE_ACCOUNT -> LauncherSection.ADD_ACCOUNT
        LauncherSection.ADD_ACCOUNT -> LauncherSection.ACCOUNTS
        LauncherSection.INSTANCE_EDITOR -> LauncherSection.LIBRARY
        else -> LauncherSection.HOME
    }
}
