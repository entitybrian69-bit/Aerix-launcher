/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Renderer plugin discovery and launch adapter, ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/game/plugin/PluginLoader.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/game/plugin/renderer/RendererPlugin.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/game/plugin/renderer/RendererPluginManager.kt
 * Discovers installed FCL / Zalith renderer plugin APKs (meta-data fclPlugin or zalithRendererPlugin)
 * and registers each one in PojavLauncher's RendererCache as an external RenderSpec (tag
 * "plugin:<packageName>") without modifying Pojav's backend classes. When a plugin renderer is
 * selected, applyLaunchOverride() replaces the instance's GameRenderer spec at launch time so
 * GameRenderer.setupEnvironment() and GameRenderer.maybeSetupRenderer() use the plugin's native
 * directory, EGL library, environment variables and dlopen list.
 * Zalith Launcher 2 Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import git.artdeell.mojoexec.MojoExec
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.game.renderer.GameRenderer
import net.kdt.pojavlaunch.game.renderer.RenderSpec
import net.kdt.pojavlaunch.game.renderer.RendererCache
import java.io.File

class AerixRendererPlugin(
    val packageName: String,
    val appName: String,
    val appVersion: String,
    val rendererId: String,
    val displayName: String,
    val summary: String,
    val minMCVer: String?,
    val maxMCVer: String?,
    val glName: String,
    val eglName: String,
    val nativeLibDir: String,
    val env: Map<String, String>,
    val dlopen: List<String>
) : RenderSpec {
    val pluginTag: String = "$TAG_PREFIX$packageName"

    override fun compatibleDevice(context: Context): Boolean = File(nativeLibDir).isDirectory

    override fun name(): String = displayName

    override fun displayName(): Int = R.string.aerix_plugin_renderer_fallback

    override fun tag(): String = pluginTag

    override fun library(): String = eglName

    override fun librarySearchPath(): String = nativeLibDir

    override fun setupEnvironment(context: Context, envMap: MutableMap<String, String>) {
        envMap.putAll(env)
        envMap["POJAV_RENDERER"] = rendererId
        if (glName.isNotEmpty()) {
            envMap["LIBGL_NAME"] = glName
        }
    }

    override fun setupRenderer(): Boolean {
        val isVulkan = rendererId.contains("zink", ignoreCase = true) ||
            rendererId.contains("vulkan", ignoreCase = true) ||
            env.containsKey("MESA_LOADER_DRIVER_OVERRIDE")
        if (isVulkan) {
            runCatching { MojoExec.preloadVulkan() }
        }
        val eglTarget = when {
            eglName.startsWith("/") -> eglName
            File(nativeLibDir, eglName).exists() -> File(nativeLibDir, eglName).absolutePath
            else -> eglName
        }
        return MojoExec.prepareEgl(eglTarget, true, !isVulkan, 3)
    }

    companion object {
        const val TAG_PREFIX = "plugin:"
    }
}

object PojavPluginBridge {
    private const val PACKAGE_FLAGS = PackageManager.GET_META_DATA or PackageManager.GET_SHARED_LIBRARY_FILES

    var plugins: List<AerixRendererPlugin> = emptyList()
        private set

    @SuppressLint("QueryPermissionsNeeded")
    fun loadPlugins(context: Context, force: Boolean = false): List<AerixRendererPlugin> {
        if (plugins.isNotEmpty() && !force) {
            injectIntoRendererCache(context, plugins)
            return plugins
        }
        val pm = context.packageManager
        val discovered = ArrayList<AerixRendererPlugin>()
        val seen = HashSet<String>()

        runCatching {
            val activities = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN), PACKAGE_FLAGS)
            for (resolve in activities) {
                val appInfo = resolve.activityInfo?.applicationInfo ?: continue
                if (!seen.add(appInfo.packageName)) continue
                parsePlugin(context, pm, appInfo)?.let { discovered.add(it) }
            }
        }
        runCatching {
            val packages = pm.getInstalledApplications(PACKAGE_FLAGS)
            for (appInfo in packages) {
                if (!seen.add(appInfo.packageName)) continue
                parsePlugin(context, pm, appInfo)?.let { discovered.add(it) }
            }
        }

        plugins = discovered.sortedBy { it.displayName.lowercase() }
        injectIntoRendererCache(context, plugins)
        return plugins
    }

    fun findByTag(tag: String?): AerixRendererPlugin? {
        if (tag == null || !tag.startsWith(AerixRendererPlugin.TAG_PREFIX)) return null
        return plugins.firstOrNull { it.pluginTag == tag }
    }

    /**
     * Replaces [gameRenderer]'s current spec when [rendererTag] selects an external plugin renderer.
     * For built-in Pojav renderers this is a no-op.
     */
    @JvmStatic
    fun applyLaunchOverride(context: Context, gameRenderer: GameRenderer, rendererTag: String?) {
        if (rendererTag == null || !rendererTag.startsWith(AerixRendererPlugin.TAG_PREFIX)) return
        if (plugins.isEmpty()) loadPlugins(context)
        val plugin = findByTag(rendererTag) ?: return
        gameRenderer.setCurrentRenderer(plugin)
    }

    private fun parsePlugin(
        context: Context,
        pm: PackageManager,
        info: ApplicationInfo
    ): AerixRendererPlugin? = runCatching {
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) return null
        val meta = info.metaData ?: return null
        val isFcl = meta.getBoolean("fclPlugin", false)
        val isZalith = meta.getBoolean("zalithRendererPlugin", false)
        if (!isFcl && !isZalith) return null

        val rendererRaw = meta.getString("renderer") ?: return null
        val parts = rendererRaw.split(":")
        if (parts.size < 3) return null
        val des = meta.getString("des") ?: info.loadLabel(pm).toString()
        val pojavEnv = meta.getString("pojavEnv") ?: ""
        val nativeDir = info.nativeLibraryDir ?: return null
        val appName = info.loadLabel(pm).toString()
        val appVersion = runCatching { pm.getPackageInfo(info.packageName, 0).versionName ?: "" }.getOrDefault("")

        var rendererId = parts[0]
        val env = LinkedHashMap<String, String>()
        val dlopen = ArrayList<String>()
        pojavEnv.split(":").forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val key = pair.substring(0, eq)
                val value = pair.substring(eq + 1)
                when (key) {
                    "POJAV_RENDERER" -> rendererId = value
                    "DLOPEN" -> value.split(",").filter { it.isNotBlank() }.forEach { dlopen.add(it) }
                    "LIB_MESA_NAME", "MESA_LIBRARY" -> env[key] = "$nativeDir/$value"
                    else -> env[key] = value
                }
            }
        }

        val eglRaw = parts[2]
        val eglName = if (eglRaw.startsWith("/")) "$nativeDir$eglRaw" else eglRaw

        AerixRendererPlugin(
            packageName = info.packageName,
            appName = appName,
            appVersion = appVersion,
            rendererId = rendererId,
            displayName = "$des ($appName)",
            summary = "Plugin: $appName",
            minMCVer = meta.versionString("minMCVer"),
            maxMCVer = meta.versionString("maxMCVer"),
            glName = parts[1],
            eglName = eglName,
            nativeLibDir = nativeDir,
            env = env,
            dlopen = dlopen
        )
    }.getOrNull()

    private fun Bundle.versionString(key: String): String? {
        val raw = get(key) ?: return null
        return raw.toString().takeIf { it.isNotBlank() }
    }

    /**
     * Appends discovered plugin renderers into Pojav's cached RendererCache so every renderer list
     * (Settings, Instance Editor, Pojav's own preference screen) sees them.
     */
    private fun injectIntoRendererCache(context: Context, list: List<AerixRendererPlugin>) {
        if (list.isEmpty()) return
        runCatching {
            val current = RendererCache.getCompatibleRenderers(context)
            val ids = ArrayList(current.rendererIds)
            val names = current.rendererDisplayNames.toMutableList()
            var changed = false
            for (plugin in list) {
                if (!ids.contains(plugin.pluginTag)) {
                    ids.add(plugin.pluginTag)
                    names.add(plugin.displayName)
                    changed = true
                }
            }
            if (changed) {
                val field = RendererCache::class.java.getDeclaredField("sCompatibleRenderers")
                field.isAccessible = true
                field.set(null, RendererCache(ids, names.toTypedArray()))
            }
        }
    }
}
