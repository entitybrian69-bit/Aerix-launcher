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
import net.kdt.pojavlaunch.Tools
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

    /**
     * Directory the native libraries are loaded from.
     *
     * A plugin APK lives in another application's directory. Android's linker only hands a process
     * its own native libraries, so the plugin's `.so` files are copied into our private storage
     * once per plugin version and loaded from there; that directory is always readable by us and
     * is the directory the plugin's own libraries are searched in as well.
     */
    @Volatile
    private var preparedLibDir: String? = null

    private fun libraryDir(): String = preparedLibDir ?: nativeLibDir

    /** Absolute path of a library from the plugin, resolved against [libraryDir]. */
    private fun libraryIn(dir: String, name: String): String? {
        if (name.isEmpty()) return null
        if (name.startsWith("/")) return name.takeIf { File(it).isFile }
        return File(dir, name).takeIf { it.isFile }?.absolutePath
    }

    /**
     * Copies this plugin's libraries for the device ABI into private storage. Safe to call from
     * any thread and any number of times; the copy is skipped when the plugin did not change.
     */
    @Synchronized
    fun installLibraries(context: Context): String? {
        preparedLibDir?.let { if (File(it).isDirectory) return it }
        val source = File(nativeLibDir)
        if (!source.isDirectory) return null
        val target = File(context.noBackupFilesDir, "renderer-plugins/$packageName")
        val stampFile = File(target, ".stamp")
        val stamp = "$appVersion|${source.absolutePath}"
        if (stampFile.isFile && runCatching { stampFile.readText() }.getOrNull() == stamp) {
            preparedLibDir = target.absolutePath
            return preparedLibDir
        }
        val copied = runCatching {
            if (target.exists() && !target.deleteRecursively()) return null
            if (!target.mkdirs() && !target.isDirectory) return null
            var count = 0
            source.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".so")) {
                    file.copyTo(File(target, file.name), overwrite = true)
                    count++
                }
            }
            if (count > 0) stampFile.writeText(stamp)
            count
        }.getOrDefault(0)
        if (copied <= 0) {
            Logger.appendToLog("Renderer plugin $packageName: could not copy native libraries, using them in place")
            return null
        }
        preparedLibDir = target.absolutePath
        return preparedLibDir
    }

    override fun compatibleDevice(context: Context): Boolean {
        if (!File(libraryDir()).isDirectory) return false
        if (metadataVersion < 2) return true
        val gles = context.getSystemService(ActivityManager::class.java)?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        val abi = (if (android.os.Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS).firstOrNull() ?: return false
        return Build.VERSION.SDK_INT >= 26 && gles >= 0x30000 &&
            RendererPluginV2.matchesElfAbi(File(libraryIn(libraryDir(), glName) ?: glName), abi) &&
            (eglName == "libEGL.so" || eglName == glName ||
                RendererPluginV2.matchesElfAbi(File(libraryIn(libraryDir(), eglName) ?: eglName), abi))
    }

    override fun name(): String = displayName

    override fun displayName(): Int = R.string.aerix_plugin_renderer_fallback

    override fun tag(): String = pluginTag

    override fun library(): String = eglName

    /**
     * Both the private copy and the plugin's own directory. The plugin resolves its own libraries
     * by bare file name, and MojoExec builds one linker namespace out of this string on first use,
     * so everything the plugin may ask for has to be in here from the start.
     */
    override fun librarySearchPath(): String {
        val privateDir = preparedLibDir
        return if (privateDir == null || privateDir == nativeLibDir) nativeLibDir
        else "$privateDir:$nativeLibDir"
    }

    /** Absolute path of a packaged library, or null when the plugin ships it in the APK only. */
    private fun packagedLibrary(name: String): String? =
        libraryIn(preparedLibDir ?: nativeLibDir, name) ?: libraryIn(nativeLibDir, name)

    /**
     * The OpenGL library of this plugin for LWJGL.
     *
     * A plugin whose EGL wrapper and OpenGL implementation are the same file is already handed to
     * LWJGL through MojoExec, so it keeps the shim name. When they are different files (PowerVR
     * ships `libpowervr.so` next to the system EGL) LWJGL must bind OpenGL to the plugin library
     * itself, otherwise every GL entry point resolves against the EGL library and the game dies on
     * the first GL call.
     */
    override fun glLibraryPath(): String {
        val gl = packagedLibrary(glName) ?: return "libGLMojo.so"
        val egl = packagedLibrary(eglName) ?: return gl
        val sameFile = runCatching { File(egl).canonicalPath == File(gl).canonicalPath }.getOrDefault(false)
        return if (sameFile) "libGLMojo.so" else gl
    }

    /** LWJGL's own EGL loader, pointed at the plugin EGL implementation when it ships one. */
    override fun eglLibraryPath(): String? = packagedLibrary(eglName)

    override fun setupEnvironment(context: Context, envMap: MutableMap<String, String>) {
        envMap.putAll(env)
        envMap["POJAV_RENDERER"] = rendererId
        // How a plugin's EGL provider is handed to the runtime, following the contract the plugin
        // was written against: POJAVEXEC_EGL carries the name exactly as the plugin declares it
        // (a bare name is resolved through the plugin's own library directory), while SDL asks for
        // a real file and therefore gets the resolved path.
        envMap["POJAVEXEC_EGL"] = eglName
        packagedLibrary(eglName)?.let { envMap["SDL_EGL_LIBRARY"] = it }
        packagedLibrary(glName)?.let { envMap["SDL_OPENGL_LIBRARY"] = it }
    }

    /** Values passed directly to MojoExec; inspecting this plan never loads native code. */
    internal data class EglLaunchPlan(val library: String, val namespaceBypass: Boolean, val gles: Boolean, val major: Int)

    internal fun eglLaunchPlan(): EglLaunchPlan {
        val target = packagedLibrary(eglName) ?: eglName
        return EglLaunchPlan(target, useNamespaceBypass, !RendererPluginV2.usesDesktopGl(rendererId, env), 3)
    }

    /**
     * Every way this plugin's EGL provider can be loaded, in order, as (directory, useNamespace).
     *
     * A plugin's libraries are preloaded through a linker namespace built from the plugin's own
     * library directory, because those libraries are searched by their bare file names from inside
     * the plugin (Emesis loads `libemesis_modern.so` this way). A plain `dlopen` only sees the
     * system paths, so it is the last resort and never the plugin's only chance to load. V2
     * metadata states the mode it needs, so that mode keeps its place at the front.
     */
    private fun loadAttempts(): List<Pair<String, Boolean>> {
        val directories = listOfNotNull(preparedLibDir, nativeLibDir).distinct()
        val namespaced = directories.map { it to true }
        val plain = directories.map { it to false }
        return if (metadataVersion >= 2 && !useNamespaceBypass) plain + namespaced
        else namespaced + plain
    }

    override fun setupRenderer(): Boolean = try {
        val initialize = {
            if (RendererPluginV2.usesVulkan(rendererId, env)) MojoExec.preloadVulkan()
            val gles = !RendererPluginV2.usesDesktopGl(rendererId, env)
            // MojoExec builds one linker namespace per process out of this path and keeps it, so
            // every directory the plugin may load from has to be in it before the first attempt.
            GameRenderer.setRendererLibraryPath(Tools.NATIVE_LIB_DIR, librarySearchPath())
            var loaded = false
            for ((directory, bypass) in loadAttempts()) {
                val library = libraryIn(directory, eglName) ?: continue
                loaded = MojoExec.prepareEgl(library, bypass, gles, 3)
                Logger.appendToLog("Renderer native load: tag=$pluginTag egl=$library gles=$gles " +
                    "directory=${if (directory == nativeLibDir) "plugin" else "private"} " +
                    "namespaceBypass=$bypass loaded=$loaded")
                if (loaded) break
            }
            // prepareEgl only dlopens the provider. EGL context creation happens later in GLFW/SDL.
            Logger.appendToLog("Renderer native setup finished loaded=$loaded tag=$pluginTag; EGL context/gameplay not yet verified")
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
        "emesisPlugin",
        // Emesis builds also declare this one; without it they only matched by package prefix.
        "pgwRendererPlugin"
    )

    var plugins: List<AerixRendererPlugin> = emptyList()
        private set

    /**
     * Why plugin-looking packages were skipped during the last scan. Written to the launcher log
     * and kept here so a bug report can say what the plugin scanner saw.
     */
    var lastScanNotes: List<String> = emptyList()
        private set
    private val scanNotes = ArrayList<String>()

    private fun note(message: String) {
        scanNotes.add(message)
    }

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
        scanNotes.clear()

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
        lastScanNotes = scanNotes.toList()
        runCatching {
            Logger.appendToLog("Renderer plugins: " + if (plugins.isEmpty()) "none" else
                plugins.joinToString { "${it.packageName} -> ${it.rendererId} (${it.displayName})" })
            scanNotes.forEach { Logger.appendToLog("Renderer plugin skipped: $it") }
        }
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
        try {
            if (plugins.isEmpty()) loadPlugins(context, force = true)
            val plugin = findByTag(rendererTag)
            if (plugin == null) {
                Log.w("AerixRendererPlugin", "Selected renderer plugin is not installed; keeping built-in renderer")
                return
            }
            // Copy the plugin's libraries into our own storage before anything tries to load them.
            plugin.installLibraries(context)
            if (!plugin.compatibleDevice(context)) {
                Log.w("AerixRendererPlugin", "Selected renderer unavailable; keeping built-in fallback before environment setup")
                android.widget.Toast.makeText(context, "Selected renderer unavailable; using built-in renderer", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            gameRenderer.setCurrentRenderer(plugin)
        } catch (error: Throwable) {
            // A broken plugin must never crash the launcher on the way into the game.
            Logger.appendToLog("Renderer plugin $rendererTag could not be applied: ${error.javaClass.simpleName}: ${error.message}")
            Log.e("AerixRendererPlugin", "Could not apply renderer plugin", error)
            android.widget.Toast.makeText(context, "Renderer plugin failed to load; using built-in renderer", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /**
     * A meta-data flag, whatever type the plugin author's build tool wrote it as.
     * `android:value="true"` becomes a boolean, but a plugin that builds its manifest by hand can
     * leave the string "true" there; [Bundle.getBoolean] silently reports false for that.
     */
    private fun metaFlag(meta: Bundle, key: String): Boolean {
        val raw = runCatching { meta.get(key) }.getOrNull() ?: return false
        return when (raw) {
            is Boolean -> raw
            is Int -> raw != 0
            is String -> raw.trim().equals("true", true) || raw.trim() == "1"
            else -> false
        }
    }

    /** First meta-data marker declaring the package a renderer plugin, or the implicit Emesis one. */
    internal fun markerForMeta(meta: Bundle, packageName: String): String? {
        for (key in PLUGIN_MARKERS) {
            if (key == "fclPlugin_V2") {
                if (meta.containsKey(key)) return key
                continue
            }
            if (metaFlag(meta, key)) return key
        }
        if (packageName.startsWith(EMESIS_PACKAGE_PREFIX)) return "emesisPlugin"
        // Some renderer APKs (PowerVR builds among them) only publish the renderer contract
        // itself. If all three legacy keys are present the package is a renderer plugin, whether
        // or not its author used one of the marker names above.
        val declaresRenderer = meta.getString("renderer") != null &&
            (meta.getString("pojavEnv") != null || meta.getString("boatEnv") != null)
        return if (declaresRenderer) "rendererMetadata" else null
    }

    /**
     * Where the plugin's native libraries were extracted. A plugin APK without code
     * (`hasCode="false"`, which PowerVR builds use) still gets its libs unpacked, but not every
     * Android version fills [ApplicationInfo.nativeLibraryDir] for it, so fall back to the
     * conventional per-ABI directory before giving up on the plugin.
     */
    private fun nativeLibraryDirOf(info: ApplicationInfo): String? {
        info.nativeLibraryDir?.let { if (File(it).isDirectory) return it }
        // The device ABI, which is also the one the plugin's libraries must match. This used to
        // read ApplicationInfo.primaryCpuAbi, a field that is not part of the public SDK stubs.
        val abi = (if (android.os.Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS
            else Build.SUPPORTED_32_BIT_ABIS).firstOrNull() ?: return null
        if (Build.VERSION.SDK_INT >= 24) {
            val dataDir = runCatching { info.dataDir }.getOrNull()
            if (!dataDir.isNullOrEmpty()) {
                val unpacked = File(dataDir, "lib/$abi")
                if (unpacked.isDirectory) return unpacked.absolutePath
            }
        }
        // Shared, read-only library directory used when the ABI matches the launcher itself.
        val appLibrary = File(info.sourceDir).parentFile?.let { File(it, "lib/$abi") }
        if (appLibrary != null && appLibrary.isDirectory) return appLibrary.absolutePath
        return null
    }

    private fun parsePlugin(
        context: Context,
        pm: PackageManager,
        info: ApplicationInfo
    ): AerixRendererPlugin? = runCatching {
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0 || RetiredRendererCleanup.isRetiredPackage(info.packageName)) return null
        val meta = info.metaData ?: return null
        val marker = markerForMeta(meta, info.packageName) ?: return null
        val emesisStyle = marker == "emesisPlugin" || marker == "aerixPlugin"
        if (meta.containsKey("fclPlugin_V2")) {
            val plugin = runCatching {
                val metadata = when (val value = meta.get("fclPlugin_V2")) {
                    is Int -> pm.getResourcesForApplication(info).getString(value)
                    is String -> value
                    else -> error("Invalid V2 config resource")
                }
                val nativeDir = requireNotNull(nativeLibraryDirOf(info)) { "No native library directory" }
                val config = RendererPluginV2.parse(metadata, File(nativeDir))
                AerixRendererPlugin(
                    packageName = info.packageName, appName = info.loadLabel(pm).toString(),
                    appVersion = pm.getPackageInfo(info.packageName, 0).versionName.orEmpty(),
                    rendererId = config.id,
                    displayName = AerixRendererBrands.nameFor(info.packageName) ?: config.name,
                    summary = AerixRendererBrands.descriptionFor(info.packageName)
                        ?: "External V2 renderer plugin",
                    minMCVer = config.minVersion, maxMCVer = config.maxVersion,
                    glName = config.gl, eglName = config.egl, nativeLibDir = nativeDir,
                    env = config.env,
                    dlopen = emptyList(), metadataVersion = 2
                ).also { require(it.compatibleDevice(context)) { "Plugin requires Android 8+, GLES 3 and matching native libraries" } }
            }.onFailure { Log.w("AerixRendererPlugin", "Cannot load V2 renderer ${info.packageName}: ${it.message}") }.getOrNull()
            if (plugin != null) return plugin
            // A malformed/unsupported V2 config may still supply valid legacy metadata.
        }

        val parts = (meta.getString("renderer") ?: "").split(":").filter { it.isNotBlank() }
        if (!emesisStyle && parts.isEmpty()) {
            note("${info.packageName}: no renderer string")
            return null
        }
        val des = meta.getString("des") ?: if (emesisStyle) "Emesis" else info.loadLabel(pm).toString()
        val pojavEnv = meta.getString("pojavEnv") ?: meta.getString("boatEnv") ?: ""
        val nativeDir = nativeLibraryDirOf(info) ?: run {
            note("${info.packageName}: no extracted native libraries for this ABI")
            return null
        }
        val appName = info.loadLabel(pm).toString()
        val appVersion = runCatching { pm.getPackageInfo(info.packageName, 0).versionName ?: "" }.getOrDefault("")

        var rendererId = parts.firstOrNull() ?: EMESIS_RENDERER_ID
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

        val glName = parts.getOrNull(1) ?: if (emesisStyle) EMESIS_LIBRARY else parts.first()
        val eglRaw = parts.getOrNull(2) ?: glName
        if (parts.size < 3 && emesisStyle && EMESIS_LIBRARY !in dlopen) dlopen.add(EMESIS_LIBRARY)
        val eglName = if (eglRaw.startsWith("/")) "$nativeDir$eglRaw" else eglRaw

        val brand = AerixRendererBrands.forPackage(info.packageName)
        AerixRendererPlugin(
            packageName = info.packageName,
            appName = appName,
            appVersion = appVersion,
            rendererId = rendererId,
            // A known plugin is shown under its own name; anything else keeps "<des> (<app>)".
            displayName = brand?.name ?: "$des ($appName)",
            summary = brand?.description ?: "Plugin: $appName",
            minMCVer = meta.versionString("minMCVer"),
            maxMCVer = meta.versionString("maxMCVer"),
            glName = glName,
            eglName = eglName,
            nativeLibDir = nativeDir,
            env = env,
            dlopen = dlopen
        )
    }.onFailure {
        // Never swallow the reason a renderer plugin could not be read: it is the only clue a
        // report about a missing renderer carries.
        note("${info.packageName}: ${it.message ?: it.javaClass.simpleName}")
        Log.w("AerixRendererPlugin", "Cannot read renderer plugin ${info.packageName}", it)
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
