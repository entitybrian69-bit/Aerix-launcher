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
import android.app.ActivityManager
import android.os.Build
import android.util.Log
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
    val dlopen: List<String>,
    val metadataVersion: Int = 1,
    val initializeBundle: ((() -> Boolean) -> Boolean)? = null
) : RenderSpec {
    val pluginTag: String = "$TAG_PREFIX$packageName"

    override fun compatibleDevice(context: Context): Boolean {
        if (!File(nativeLibDir).isDirectory) return false
        if (metadataVersion < 2) return true
        val gles = context.getSystemService(ActivityManager::class.java)?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        val abi = (if (android.os.Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS).firstOrNull() ?: return false
        return Build.VERSION.SDK_INT >= 26 && gles >= 0x30000 &&
            RendererPluginV2.matchesElfAbi(File(glName), abi) &&
            (eglName == "libEGL.so" || eglName == glName || RendererPluginV2.matchesElfAbi(File(eglName), abi))
    }

    override fun name(): String = displayName

    override fun displayName(): Int = R.string.aerix_plugin_renderer_fallback

    override fun tag(): String = pluginTag

    override fun library(): String = eglName

    override fun librarySearchPath(): String = nativeLibDir

    override fun setupEnvironment(context: Context, envMap: MutableMap<String, String>) {
        envMap.putAll(env)
        envMap["POJAV_RENDERER"] = rendererId
        if (metadataVersion >= 2) envMap["SDL_EGL_LIBRARY"] = eglName
        if (glName.isNotEmpty()) {
            envMap["LIBGL_NAME"] = glName
        }
    }

    override fun setupRenderer(): Boolean = try {
        val initialize = {
            if (RendererPluginV2.usesVulkan(rendererId, env)) MojoExec.preloadVulkan()
            val eglTarget = when {
                eglName.startsWith("/") -> eglName
                File(nativeLibDir, eglName).exists() -> File(nativeLibDir, eglName).absolutePath
                else -> eglName
            }
            MojoExec.prepareEgl(eglTarget, true, !RendererPluginV2.usesDesktopGl(rendererId, env), 3)
        }
        initializeBundle?.invoke(initialize) ?: initialize()
    } catch (error: Exception) {
        Log.e("AerixRendererPlugin", "Renderer initialization failed", error); false
    } catch (error: LinkageError) {
        Log.e("AerixRendererPlugin", "Renderer native linkage failed", error); false
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
        runCatching { net.kdt.pojavlaunch.aerix.renderer.BundledRustGl.discover(context) }
            .onFailure { Log.e("AerixRendererPlugin", "Bundled RustGL rejected", it) }
            .getOrNull()?.let { discovered.add(it); seen.add(it.packageName) }

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

        if (discovered.any { it.packageName == RendererPluginV2.RUSTGL_PACKAGE || it.packageName == net.kdt.pojavlaunch.aerix.renderer.BundledRustGl.PACKAGE }) {
            net.kdt.pojavlaunch.aerix.renderer.VulkanProbeClient.ensureProbed(context)
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
        val plugin = findByTag(rendererTag)
        if (plugin == null || !plugin.compatibleDevice(context)) {
            Log.w("AerixRendererPlugin", "Selected renderer unavailable; keeping built-in fallback before environment setup")
            android.widget.Toast.makeText(context, "Selected renderer unavailable; using built-in renderer", android.widget.Toast.LENGTH_LONG).show()
            return
        }
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
        if (!isFcl && !isZalith && !meta.containsKey("fclPlugin_V2")) return null
        if (meta.containsKey("fclPlugin_V2")) {
            val plugin = runCatching {
                val metadata = when (val value = meta.get("fclPlugin_V2")) {
                    is Int -> pm.getResourcesForApplication(info).getString(value)
                    is String -> value
                    else -> error("Invalid V2 config resource")
                }
                val nativeDir = requireNotNull(info.nativeLibraryDir)
                val config = RendererPluginV2.parse(metadata, File(nativeDir))
                val rustGl = info.packageName == RendererPluginV2.RUSTGL_PACKAGE
                AerixRendererPlugin(
                    packageName = info.packageName, appName = info.loadLabel(pm).toString(),
                    appVersion = pm.getPackageInfo(info.packageName, 0).versionName.orEmpty(),
                    rendererId = config.id,
                    displayName = config.name + if (rustGl) " (Experimental)" else "",
                    summary = if (rustGl) "External RustGL · Experimental GLES translation" else "External V2 renderer plugin",
                    minMCVer = config.minVersion, maxMCVer = config.maxVersion,
                    glName = config.gl, eglName = config.egl, nativeLibDir = nativeDir,
                    env = config.env, dlopen = emptyList(), metadataVersion = 2
                ).also { require(it.compatibleDevice(context)) { "Plugin requires Android 8+, GLES 3 and matching native libraries" } }
            }.onFailure { Log.w("AerixRendererPlugin", "Cannot load V2 renderer ${info.packageName}: ${it.message}") }.getOrNull()
            if (plugin != null) return plugin
            // A malformed/unsupported V2 config may still supply valid legacy metadata.
        }

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
