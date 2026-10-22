/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Renderer plugin discovery and launch adapter, ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/game/plugin/PluginLoader.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/game/plugin/renderer/RendererPlugin.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/game/plugin/renderer/RendererPluginManager.kt
 * Discovers every installed renderer plugin APK. A host opts in with one of these meta-data markers:
 *   fclPlugin, fclPlugin_V2 (Fold Craft Launcher), zalithRendererPlugin (Zalith Launcher 2),
 *   pojavRendererPlugin, aerixPlugin, emesisPlugin (the com.emesis package space counts implicitly)
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
import net.kdt.pojavlaunch.Logger
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
    val useNamespaceBypass: Boolean = true
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

    /** Values passed directly to MojoExec; inspecting this plan never loads native code. */
    internal data class EglLaunchPlan(val library: String, val namespaceBypass: Boolean, val gles: Boolean, val major: Int)

    internal fun eglLaunchPlan(): EglLaunchPlan {
        val target = when {
            eglName.startsWith("/") -> eglName
            File(nativeLibDir, eglName).exists() -> File(nativeLibDir, eglName).absolutePath
            else -> eglName
        }
        return EglLaunchPlan(target, useNamespaceBypass, !RendererPluginV2.usesDesktopGl(rendererId, env), 3)
    }

    override fun setupRenderer(): Boolean = try {
        val initialize = {
            if (RendererPluginV2.usesVulkan(rendererId, env)) MojoExec.preloadVulkan()
            val plan = eglLaunchPlan()
            Logger.appendToLog("Renderer native load: tag=$pluginTag bundle=$appVersion " +
                "egl=${plan.library} namespaceBypass=${plan.namespaceBypass} gles=${plan.gles} major=${plan.major}")
            val loaded = MojoExec.prepareEgl(plan.library, plan.namespaceBypass, plan.gles, plan.major)
            // prepareEgl only dlopens the provider. EGL context creation happens later in GLFW/SDL.
            Logger.appendToLog("Renderer native provider loaded=$loaded tag=$pluginTag; EGL context/gameplay not yet verified")
            loaded
        }
        initialize()
    } catch (error: Exception) {
        Logger.appendToLog("Renderer initialization failed: tag=$pluginTag ${error.javaClass.simpleName}: ${error.message}")
        Log.e("AerixRendererPlugin", "Renderer initialization failed", error); false
    } catch (error: LinkageError) {
        Logger.appendToLog("Renderer native linkage failed: tag=$pluginTag ${error.javaClass.simpleName}: ${error.message}")
        Log.e("AerixRendererPlugin", "Renderer native linkage failed", error); false
    }

    companion object {
        const val TAG_PREFIX = "plugin:"
    }
}

object PojavPluginBridge {
    private const val PACKAGE_FLAGS = PackageManager.GET_META_DATA or PackageManager.GET_SHARED_LIBRARY_FILES
    private const val EMESIS_PACKAGE_PREFIX = "com.emesis"
    private const val EMESIS_RENDERER_ID = "emesis"
    private const val EMESIS_LIBRARY = "libemesis.so"

    /** Meta-data keys that mark an APK as a renderer plugin, checked in this order. */
    private val PLUGIN_MARKERS = listOf(
        "fclPlugin",
        "fclPlugin_V2",
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
        if (plugins.isNotEmpty() && !force) {
            injectIntoRendererCache(context, plugins)
            return plugins
        }
        val pm = context.packageManager
        val discovered = ArrayList<AerixRendererPlugin>()
        RetiredRendererCleanup.clean(context)
        val seen = hashSetOf<String>()

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

    /** Rescans installed packages and rebuilds the renderer list, used when a plugin APK changes. */
    @JvmStatic
    fun refreshPlugins(context: Context) {
        runCatching { RendererCache.releaseRendererCache() }
        loadPlugins(context.applicationContext ?: context, force = true)
    }

    /** Display name for a renderer tag, including the "plugin:" tags. */
    fun labelForTag(tag: String?): String? = findByTag(tag)?.displayName

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
        if (plugins.isEmpty()) loadPlugins(context, force = true)
        val plugin = findByTag(rendererTag)
        if (plugin == null || !plugin.compatibleDevice(context)) {
            Log.w("AerixRendererPlugin", "Selected renderer unavailable; keeping built-in fallback before environment setup")
            android.widget.Toast.makeText(context, "Selected renderer unavailable; using built-in renderer", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        gameRenderer.setCurrentRenderer(plugin)
    }

    /** First meta-data marker declaring the package a renderer plugin, or the implicit Emesis one. */
    private fun pluginMarker(meta: Bundle, packageName: String): String? {
        for (key in PLUGIN_MARKERS) {
            if (key == "fclPlugin_V2") {
                if (meta.containsKey(key)) return key
                continue
            }
            if (meta.getBoolean(key, false)) return key
        }
        return if (packageName.startsWith(EMESIS_PACKAGE_PREFIX)) "emesisPlugin" else null
    }

    private fun parsePlugin(
        context: Context,
        pm: PackageManager,
        info: ApplicationInfo
    ): AerixRendererPlugin? = runCatching {
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0 || RetiredRendererCleanup.isRetiredPackage(info.packageName)) return null
        val meta = info.metaData ?: return null
        val marker = pluginMarker(meta, info.packageName) ?: return null
        val emesisStyle = marker == "emesisPlugin" || marker == "aerixPlugin"
        if (meta.containsKey("fclPlugin_V2")) {
            val plugin = runCatching {
                val metadata = when (val value = meta.get("fclPlugin_V2")) {
                    is Int -> pm.getResourcesForApplication(info).getString(value)
                    is String -> value
                    else -> error("Invalid V2 config resource")
                }
                val nativeDir = requireNotNull(info.nativeLibraryDir)
                val config = RendererPluginV2.parse(metadata, File(nativeDir))
                AerixRendererPlugin(
                    packageName = info.packageName, appName = info.loadLabel(pm).toString(),
                    appVersion = pm.getPackageInfo(info.packageName, 0).versionName.orEmpty(),
                    rendererId = config.id,
                    displayName = config.name,
                    summary = "External V2 renderer plugin",
                    minMCVer = config.minVersion, maxMCVer = config.maxVersion,
                    glName = config.gl, eglName = config.egl, nativeLibDir = nativeDir,
                    env = config.env,
                    dlopen = emptyList(), metadataVersion = 2
                ).also { require(it.compatibleDevice(context)) { "Plugin requires Android 8+, GLES 3 and matching native libraries" } }
            }.onFailure { Log.w("AerixRendererPlugin", "Cannot load V2 renderer ${info.packageName}: ${it.message}") }.getOrNull()
            if (plugin != null) return plugin
            // A malformed/unsupported V2 config may still supply valid legacy metadata.
        }

        val parts = meta.getString("renderer")?.split(":") ?: emptyList()
        if (!emesisStyle && parts.size < 3) return null
        val des = meta.getString("des") ?: if (emesisStyle) "Emesis" else info.loadLabel(pm).toString()
        val pojavEnv = meta.getString("pojavEnv") ?: ""
        val nativeDir = info.nativeLibraryDir ?: return null
        val appName = info.loadLabel(pm).toString()
        val appVersion = runCatching { pm.getPackageInfo(info.packageName, 0).versionName ?: "" }.getOrDefault("")

        var rendererId = if (parts.size >= 3) parts[0] else EMESIS_RENDERER_ID
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

        val glName = if (parts.size >= 3) parts[1] else EMESIS_LIBRARY
        val eglRaw = if (parts.size >= 3) parts[2] else EMESIS_LIBRARY
        if (parts.size < 3 && EMESIS_LIBRARY !in dlopen) dlopen.add(EMESIS_LIBRARY)
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
            glName = glName,
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
