/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Generic renderer plugin discovery and launch adapter, ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/game/plugin/PluginLoader.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/game/plugin/renderer/RendererPlugin.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/game/plugin/renderer/RendererPluginManager.kt
 * Discovers every installed renderer plugin APK. A plugin opts in with a boolean meta-data key inside its
 * <application> element; any of these markers is accepted:
 *   fclPlugin            - Fold Craft Launcher renderer plugins
 *   zalithRendererPlugin - Zalith Launcher renderer plugins
 *   pojavRendererPlugin  - PojavLauncher-style renderer plugins
 *   aerixPlugin          - Aerix-native renderer plugins
 *   emesisPlugin         - Emesis renderer plugins (the com.emesis package space counts as an implicit marker)
 * A plugin declares meta-data "renderer" as "<rendererId>:<glLibrary>:<eglLibrary>", optionally
 * "des" (display name), "minMCVer", "maxMCVer" and "pojavEnv" (KEY=VALUE pairs, ':'-separated;
 * POJAV_RENDERER overrides the id, DLOPEN lists extra comma-separated libraries, LIB_MESA_NAME /
 * MESA_LIBRARY values are resolved against the plugin's native directory).
 * Emesis-style plugins may omit "renderer"; then rendererId "emesis" and libemesis.so are assumed.
 * Each plugin is registered in PojavLauncher's RendererCache as an external RenderSpec (tag
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
    val marker: String,
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
    private val loadedLibraries = HashSet<String>()

    override fun compatibleDevice(context: Context): Boolean =
        File(nativeLibDir).isDirectory && libraryAvailable(eglName)

    override fun name(): String = displayName

    override fun displayName(): Int = R.string.aerix_plugin_renderer_fallback

    override fun tag(): String = pluginTag

    override fun library(): String = eglName

    override fun librarySearchPath(): String = nativeLibDir

    override fun setupEnvironment(context: Context, envMap: MutableMap<String, String>) {
        envMap.putAll(env)
        envMap["POJAV_RENDERER"] = rendererId
        if (glName.isNotEmpty()) envMap["LIBGL_NAME"] = glName
    }

    override fun setupRenderer(): Boolean {
        loadPluginLibraries()
        val vulkan = usesVulkan()
        if (vulkan) runCatching { MojoExec.preloadVulkan() }
        return MojoExec.prepareEgl(resolveLibrary(eglName), true, !vulkan, glesVersion())
    }

    /** Absolute path of a library the plugin declared, relative names resolve inside its native dir. */
    fun resolveLibrary(name: String): String = when {
        name.isEmpty() -> name
        name.startsWith("/") -> name
        else -> "$nativeLibDir/$name"
    }

    private fun libraryAvailable(name: String): Boolean {
        if (name.isEmpty()) return false
        if (File(resolveLibrary(name)).exists()) return true
        return !name.startsWith("/") && !name.contains('/')
    }

    private fun usesVulkan(): Boolean =
        env.containsKey("MESA_LOADER_DRIVER_OVERRIDE") ||
            rendererId.contains("zink", ignoreCase = true) ||
            rendererId.contains("vulkan", ignoreCase = true) ||
            rendererId.contains("freedreno", ignoreCase = true) ||
            rendererId.contains("turnip", ignoreCase = true)

    private fun glesVersion(): Int =
        if (rendererId.contains("gles2", ignoreCase = true) ||
            rendererId.contains("opengles2", ignoreCase = true)) 2 else 3

    private fun loadPluginLibraries() {
        for (name in dlopen) {
            val path = resolveLibrary(name)
            if (!File(path).exists() || !loadedLibraries.add(path)) continue
            runCatching { System.load(path) }
        }
    }

    companion object {
        const val TAG_PREFIX = "plugin:"
    }
}

object PojavPluginBridge {
    private const val PACKAGE_FLAGS = PackageManager.GET_META_DATA or PackageManager.GET_SHARED_LIBRARY_FILES
    private const val EMESIS_PACKAGE_PREFIX = "com.emesis"
    private const val EMESIS_DEFAULT_LIBRARY = "libemesis.so"

    /** Boolean meta-data keys that mark an APK as a renderer plugin, checked in this order. */
    private val PLUGIN_MARKERS = listOf(
        "fclPlugin",
        "zalithRendererPlugin",
        "pojavRendererPlugin",
        "aerixPlugin",
        "emesisPlugin"
    )

    var plugins: List<AerixRendererPlugin> = emptyList()
        private set

    @JvmStatic
    @JvmOverloads
    @SuppressLint("QueryPermissionsNeeded")
    fun loadPlugins(context: Context, force: Boolean = false): List<AerixRendererPlugin> {
        if (!force && plugins.isNotEmpty()) {
            injectIntoRendererCache(context, plugins)
            return plugins
        }
        val appContext = context.applicationContext ?: context
        val pm = appContext.packageManager
        val ownPackage = appContext.packageName
        val discovered = LinkedHashMap<String, AerixRendererPlugin>()
        runCatching {
            for (info in candidatePackages(pm)) {
                if (info.packageName == ownPackage) continue
                val plugin = parsePlugin(pm, info) ?: continue
                if (!discovered.containsKey(plugin.packageName)) {
                    discovered[plugin.packageName] = plugin
                }
            }
        }
        plugins = discovered.values.sortedBy { it.displayName.lowercase() }
        injectIntoRendererCache(appContext, plugins)
        return plugins
    }

    /** Rescans installed packages and rebuilds the renderer cache. */
    @JvmStatic
    fun refreshPlugins(context: Context) {
        runCatching { RendererCache.releaseRendererCache() }
        loadPlugins(context, true)
    }

    fun findByTag(tag: String?): AerixRendererPlugin? {
        if (tag == null || !tag.startsWith(AerixRendererPlugin.TAG_PREFIX)) return null
        return plugins.firstOrNull { it.pluginTag == tag }
    }

    /** Display name for a renderer tag, including the "plugin:" tags. */
    fun labelForTag(tag: String?): String? {
        if (tag == null) return null
        findByTag(tag)?.let { return it.displayName }
        return null
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

    @SuppressLint("QueryPermissionsNeeded")
    private fun candidatePackages(pm: PackageManager): List<ApplicationInfo> {
        val packages = ArrayList<ApplicationInfo>()
        runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN), PACKAGE_FLAGS).forEach { resolve ->
                resolve.activityInfo?.applicationInfo?.let { packages.add(it) }
            }
        }
        runCatching { packages.addAll(pm.getInstalledApplications(PACKAGE_FLAGS)) }
        return packages
    }

    /** First meta-data marker declaring the package a renderer plugin, or the implicit Emesis one. */
    private fun pluginMarker(meta: Bundle, packageName: String): String? {
        for (key in PLUGIN_MARKERS) {
            if (meta.getBoolean(key, false)) return key
        }
        return if (packageName.startsWith(EMESIS_PACKAGE_PREFIX)) "emesisPlugin" else null
    }

    private fun parsePlugin(pm: PackageManager, info: ApplicationInfo): AerixRendererPlugin? = runCatching {
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) return null
        val meta = info.metaData ?: return null
        val marker = pluginMarker(meta, info.packageName) ?: return null
        val emesisStyle = marker == "emesisPlugin" || marker == "aerixPlugin"

        val parts = meta.getString("renderer")?.split(":") ?: emptyList()
        if (!emesisStyle && parts.size < 3) return null

        val nativeDir = info.nativeLibraryDir ?: return null
        val appName = info.loadLabel(pm).toString()
        val appVersion = runCatching { pm.getPackageInfo(info.packageName, 0).versionName ?: "" }.getOrDefault("")
        val des = meta.getString("des") ?: if (emesisStyle) "Emesis" else appName

        var rendererId = if (parts.size >= 3) parts[0] else "emesis"
        val env = LinkedHashMap<String, String>()
        val dlopen = ArrayList<String>()
        (meta.getString("pojavEnv") ?: "").split(":").forEach { pair ->
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

        val glName = if (parts.size >= 3) parts[1] else EMESIS_DEFAULT_LIBRARY
        val eglRaw = if (parts.size >= 3) parts[2] else EMESIS_DEFAULT_LIBRARY
        if (parts.size < 3 && EMESIS_DEFAULT_LIBRARY !in dlopen) dlopen.add(EMESIS_DEFAULT_LIBRARY)
        val eglName = if (eglRaw.startsWith("/")) "$nativeDir$eglRaw" else eglRaw

        AerixRendererPlugin(
            packageName = info.packageName,
            appName = appName,
            appVersion = appVersion,
            marker = marker,
            rendererId = rendererId,
            displayName = if (des.contains(appName)) des else "$des ($appName)",
            summary = "Plugin: $appName $appVersion".trim(),
            minMCVer = meta.text("minMCVer"),
            maxMCVer = meta.text("maxMCVer"),
            glName = glName,
            eglName = eglName,
            nativeLibDir = nativeDir,
            env = env,
            dlopen = dlopen
        )
    }.getOrNull()

    private fun Bundle.text(key: String): String? {
        val raw = get(key) ?: return null
        return raw.toString().takeIf { it.isNotBlank() }
    }

    /**
     * Appends discovered plugin renderers into Pojav's cached RendererCache so every renderer list
     * (Aerix screens, instance editor, classic preference screens) sees them.
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
