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
import net.kdt.pojavlaunch.NewJREUtil
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.multirt.MultiRTUtils
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.utils.jre.GameRunner

object PojavLaunchBridge {
    /**
     * The Java version this instance really needs: the version metadata, raised by the mods it
     * carries. A legacy game stays on Java 8; nothing is guessed from the "latest" version.
     */
    private fun requiredJavaVersion(instance: net.kdt.pojavlaunch.instances.Instance): Int {
        val declared = runCatching {
            Tools.getVersionInfo(instance.versionId)?.javaVersion?.majorVersion
        }.getOrNull() ?: 8
        return runCatching {
            net.kdt.pojavlaunch.utils.jre.ModJavaRequirements.minimumJava(instance.gameDirectory, declared)
        }.getOrDefault(declared)
    }

    /**
     * The runtime to install when the launcher starts from nothing: the closest one at or above
     * what the game needs, so a 1.8.9 instance gets Java 8 and not a 17 it cannot run on.
     */
    private fun runtimeFor(javaVersion: Int): NewJREUtil.ExternalRuntime {
        val runtimes = NewJREUtil.ExternalRuntime.values().toList()
        return runtimes.filter { it.majorVersion >= javaVersion }.minByOrNull { it.majorVersion }
            ?: runtimes.maxByOrNull { it.majorVersion }!!
    }

    private fun fire(activity: Activity) {
        try {
            val runtimes = runCatching { MultiRTUtils.getRuntimes() }.getOrDefault(emptyList())
            if (runtimes.isEmpty()) {
                val appContext = activity.applicationContext
                val wanted = Instances.loadSelectedInstance()?.let { runtimeFor(requiredJavaVersion(it)) }
                    ?: NewJREUtil.ExternalRuntime.JRE_17
                Toast.makeText(activity, R.string.downloading_java_runtime_uni, Toast.LENGTH_SHORT).show()
                PojavApplication.sExecutorService.execute {
                    runCatching {
                        wanted.downloadRuntime(appContext.assets)
                        val installed = MultiRTUtils.getRuntimes()
                        if (installed.isNotEmpty() && LauncherPreferences.PREF_DEFAULT_RUNTIME.isNullOrEmpty()) {
                            LauncherPreferences.PREF_DEFAULT_RUNTIME =
                                installed.firstOrNull { it.name == wanted.name }?.name ?: installed[0].name
                            LauncherPreferences.DEFAULT_PREF.edit()
                                .putString("defaultRuntime", LauncherPreferences.PREF_DEFAULT_RUNTIME)
                                .apply()
                        }
                    }.onFailure { err ->
                        Tools.showErrorRemote(err)
                    }
                    Tools.runOnUiThread {
                        runCatching { ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true) }
                            .onFailure { Tools.showError(activity, it) }
                    }
                }
            } else {
                ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true)
            }
        } catch (t: Throwable) {
            Tools.showError(activity, t)
        }
    }

    fun play(activity: Activity) {
        try {
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
                    .setPositiveButton(R.string.continue_button) { _, _ -> fire(activity) }
                    .show()

                GameRunner.hasReplay(gameDir) && GameRunner.hasFfmpeg(activity) -> AlertDialog.Builder(activity)
                    .setTitle(R.string.no_ffmpeg_title)
                    .setMessage(R.string.no_ffmpeg_message)
                    .setPositiveButton(R.string.install_button) { _, _ ->
                        Tools.openURL(activity, "https://github.com/MojoLauncher/FFmpegPlugin/releases")
                    }
                    .setNegativeButton(R.string.continue_button) { _, _ -> fire(activity) }
                    .show()

                else -> fire(activity)
            }
        } catch (t: Throwable) {
            Tools.showError(activity, t)
        }
    }
}
