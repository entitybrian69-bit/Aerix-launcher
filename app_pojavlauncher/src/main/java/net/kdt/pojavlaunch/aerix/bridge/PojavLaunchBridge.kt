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
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.aerix.ui.screens.AerixDownloadRequest
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.game.GameActivity
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.multirt.MultiRTUtils
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.utils.jre.GameRunner
import java.util.Locale

object PojavLaunchBridge {
    @Volatile
    private var pendingServerAddress: String? = null

    @JvmStatic
    fun setPendingServerAddress(address: String?) {
        pendingServerAddress = address?.trim()?.takeIf { it.isNotEmpty() }
    }

    @JvmStatic
    fun consumePendingServerAddress(): String? {
        val addr = pendingServerAddress
        pendingServerAddress = null
        return addr
    }
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

    /**
     * Launches (or installs & launches) the exact Minecraft version, loader, modpack and optional
     * multiplayer server of a party, join toast or shared server hub card - never a blank launch.
     */
    fun joinSocialSession(
        activity: Activity,
        mcVersion: String?,
        loaderName: String?,
        instanceHint: String? = null,
        modpackSource: String? = null,
        modpackId: String? = null,
        modpackIconUrl: String? = null,
        serverAddress: String? = null,
        onNavigate: ((LauncherSection) -> Unit)? = null
    ) {
        val cleanMc = mcVersion?.trim()?.takeIf { it.isNotEmpty() }
        val cleanLoader = loaderName?.trim()?.takeIf { it.isNotEmpty() }
        val cleanServer = serverAddress?.trim()?.takeIf { it.isNotEmpty() }

        PojavApplication.sExecutorService.execute {
            val allInstances = runCatching { Instances.loadAllInstances() }.getOrDefault(emptyList())

            // 1. Exact modpack project match first.
            val matchedByModpack = if (!modpackId.isNullOrBlank()) {
                allInstances.firstOrNull { inst ->
                    inst.modpackProjectId?.equals(modpackId, ignoreCase = true) == true
                }
            } else null

            // 2. Match by instance name + MC version, or MC version + loader, or MC version.
            val matchedBySpec = matchedByModpack ?: allInstances.firstOrNull { inst ->
                val instMc = SocialBackend.extractMcVersion(inst.versionId)
                val instLoader = SocialBackend.detectLoader(inst.versionId)
                !instanceHint.isNullOrBlank() &&
                    inst.name?.equals(instanceHint, ignoreCase = true) == true &&
                    (cleanMc == null || instMc.equals(cleanMc, ignoreCase = true))
            } ?: allInstances.firstOrNull { inst ->
                val instMc = SocialBackend.extractMcVersion(inst.versionId)
                val instLoader = SocialBackend.detectLoader(inst.versionId)
                cleanMc != null &&
                    instMc.equals(cleanMc, ignoreCase = true) &&
                    (cleanLoader == null || instLoader.equals(cleanLoader, ignoreCase = true))
            } ?: allInstances.firstOrNull { inst ->
                val instMc = SocialBackend.extractMcVersion(inst.versionId)
                cleanMc != null && instMc.equals(cleanMc, ignoreCase = true)
            }

            if (matchedBySpec != null) {
                Tools.runOnUiThread {
                    if (activity is GameActivity) {
                        Tools.restartLauncherActivityToSocialTarget(
                            activity,
                            matchedBySpec.instanceId,
                            cleanServer
                        )
                        Tools.MAIN_HANDLER.postDelayed({ Tools.fullyExit() }, 700L)
                    } else {
                        Instances.setSelectedInstance(matchedBySpec)
                        PojavInstanceBridge.reload()
                        setPendingServerAddress(cleanServer)
                        val label = matchedBySpec.name ?: matchedBySpec.versionId ?: "instance"
                        Toast.makeText(
                            activity,
                            if (cleanServer != null) "Launching $label -> $cleanServer" else "Launching $label",
                            Toast.LENGTH_SHORT
                        ).show()
                        play(activity)
                    }
                }
                return@execute
            }

            // 3. No local match: if it's a Modrinth / CurseForge modpack, open that exact pack in Discover!
            if (!modpackId.isNullOrBlank()) {
                val platform = if (modpackSource.equals("curseforge", ignoreCase = true)) {
                    AssetPlatform.CURSEFORGE
                } else {
                    AssetPlatform.MODRINTH
                }
                val project = AssetProject(
                    platform = platform,
                    type = AssetType.MODPACK,
                    id = modpackId,
                    title = instanceHint?.takeIf { it.isNotBlank() } ?: "Party Modpack",
                    description = "Install the host's modpack to join this party",
                    author = "",
                    iconUrl = modpackIconUrl,
                    downloads = 0L,
                    loaders = listOfNotNull(cleanLoader)
                )
                Tools.runOnUiThread {
                    if (activity is GameActivity) {
                        Toast.makeText(activity, "Return to launcher Discover to install ${project.title}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(activity, "Opening ${project.title} in Discover...", Toast.LENGTH_SHORT).show()
                        AerixDownloadRequest.openProject(project, onNavigate ?: { AerixNav.navigate(it) })
                    }
                }
                return@execute
            }

            // 4. Auto-create & install the host's exact MC version + loader instance, then launch!
            if (cleanMc != null) {
                val targetLoader = when (cleanLoader?.lowercase(Locale.US)) {
                    "fabric" -> AerixLoader.FABRIC
                    "quilt" -> AerixLoader.QUILT
                    "neoforge" -> AerixLoader.NEOFORGE
                    "forge" -> AerixLoader.FORGE
                    "legacy fabric", "legacy_fabric" -> AerixLoader.LEGACY_FABRIC
                    else -> AerixLoader.VANILLA
                }
                val pickedBuild = if (targetLoader.hasBuilds) {
                    runCatching {
                        val builds = PojavCreateInstanceBridge.loadLoaderVersions(targetLoader, cleanMc)
                        builds.firstOrNull { it.stable } ?: builds.firstOrNull()
                    }.getOrNull()
                } else null

                val effectiveLoader = if (targetLoader.hasBuilds && pickedBuild == null) AerixLoader.VANILLA else targetLoader
                val instName = instanceHint?.takeIf { it.isNotBlank() }
                    ?: PojavCreateInstanceBridge.suggestedName(effectiveLoader, cleanMc, pickedBuild)

                Tools.runOnUiThread {
                    if (activity is GameActivity) {
                        Toast.makeText(activity, "Exit to the launcher to install $instName", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    Toast.makeText(activity, "Setting up $instName...", Toast.LENGTH_SHORT).show()
                    PojavCreateInstanceBridge.install(
                        context = activity,
                        loader = effectiveLoader,
                        gameVersion = cleanMc,
                        loaderVersion = pickedBuild,
                        name = instName,
                        icon = null
                    ) { ok ->
                        if (ok) {
                            setPendingServerAddress(cleanServer)
                            play(activity)
                        }
                    }
                }
                return@execute
            }

            // 5. Fallback only when joining a server hub entry with no specific version requirement:
            // use the user's selected instance with server auto-connect.
            Tools.runOnUiThread {
                if (activity is GameActivity) {
                    Tools.restartLauncherActivityToSocialTarget(activity, null, cleanServer)
                    Tools.MAIN_HANDLER.postDelayed({ Tools.fullyExit() }, 700L)
                } else {
                    setPendingServerAddress(cleanServer)
                    play(activity)
                }
            }
        }
    }
}
