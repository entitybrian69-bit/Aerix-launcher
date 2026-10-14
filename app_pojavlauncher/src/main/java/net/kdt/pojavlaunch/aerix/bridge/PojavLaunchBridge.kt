/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: the Aerix "PLAY NOW" button. It performs the same pre-launch checks MainMenuFragment did
 * (VulkanMod / ReplayMod-without-FFmpeg warnings) and then raises ExtraConstants.LAUNCH_GAME, which
 * LauncherActivity.mLaunchGameListener handles with Pojav's unchanged launch pipeline.
 * Replaces the UI trigger of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/home/MiraiHomeDashboard.kt (onPlay)
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/viewmodel/LaunchGameViewModel.kt (UI entry only)
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

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.utils.jre.GameRunner

object PojavLaunchBridge {
    private fun fire() {
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true)
    }

    fun play(activity: Activity) {
        val instance = Instances.loadSelectedInstance()
        if (instance == null) {
            Toast.makeText(activity, R.string.no_instance, Toast.LENGTH_LONG).show()
            return
        }
        val gameDir = instance.gameDirectory
        when {
            GameRunner.hasVkMod(gameDir) -> AlertDialog.Builder(activity)
                .setTitle(R.string.vk_mod_title)
                .setMessage(R.string.vk_mod_message)
                .setPositiveButton(R.string.continue_button) { _, _ -> fire() }
                .show()

            GameRunner.hasReplay(gameDir) && GameRunner.hasFfmpeg(activity) -> AlertDialog.Builder(activity)
                .setTitle(R.string.no_ffmpeg_title)
                .setMessage(R.string.no_ffmpeg_message)
                .setPositiveButton(R.string.install_button) { _, _ ->
                    Tools.openURL(activity, "https://github.com/MojoLauncher/FFmpegPlugin/releases")
                }
                .setNegativeButton(R.string.continue_button) { _, _ -> fire() }
                .show()

            else -> fire()
        }
    }
}
