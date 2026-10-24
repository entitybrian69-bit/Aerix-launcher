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

/** Libraries the platform provides, which a plugin is not expected to package. */
private val SYSTEM_LIBRARIES = setOf("libEGL.so", "libGLESv2.so", "libGLESv1_CM.so", "libGL.so", "libGL.so.1")

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
     * Absolute path of a library shipped by the plugin, or null when the plugin does not package
     * it. A plugin APK with `extractNativeLibs="true"` (both known plugins) has its libraries
     * unpacked into [nativeLibDir], which is exactly where the proven plugin hosts load them from.
     */
    private fun packagedLibrary(name: String): String? {
        if (name.isEmpty()) return null
        if (name.startsWith("/")) return name.takeIf { File(it).isFile }
        return File(nativeLibDir, name).takeIf { it.isFile }?.absolutePath
    }

    override fun compatibleDevice(context: Context): Boolean {
        if (!File(nativeLibDir).isDirectory) return false
        // A plugin that declares a library it does not actually package used to be accepted, and
        // the failure only surfaced later, inside LWJGL's dlopen - which is fatal and takes the
        // whole game down at window creation. Check the declared libraries here, where refusing a
        // plugin is harmless and the launcher simply keeps its own renderer.
        if (!declaresItsLibraries()) return false
        if (metadataVersion < 2) return true
        val gles = context.getSystemService(ActivityManager::class.java)?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        val abi = (if (android.os.Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS).firstOrNull() ?: return false
        val gl = packagedLibrary(glName) ?: return false
        return Build.VERSION.SDK_INT >= 26 && gles >= 0x30000 &&
            RendererPluginV2.matchesElfAbi(File(gl), abi) &&
            (eglName == "libEGL.so" || eglName == glName ||
                packagedLibrary(eglName)?.let { RendererPluginV2.matchesElfAbi(File(it), abi) } == true)
    }

    override fun name(): String = displayName

    override fun displayName(): Int = R.string.aerix_plugin_renderer_fallback

    override fun tag(): String = pluginTag

    override fun library(): String = eglName

    override fun librarySearchPath(): String = nativeLibDir

    /**
     * OpenGL library handed to LWJGL. The launchers these plugins were written for pass the
     * plugin's own OpenGL library by absolute path; the shim name every built-in renderer uses
     * only resolves to the EGL library, which is the wrong library whenever a plugin ships its
     * OpenGL implementation separately (PowerVR: `libpowervr.so` next to the system `libEGL.so`).
     */
    override fun glLibraryPath(): String = packagedLibrary(glName) ?: "libGLMojo.so"

    override fun setupEnvironment(context: Context, envMap: MutableMap<String, String>) {
        envMap.putAll(env)
        envMap["POJAV_RENDERER"] = rendererId
        // POJAVEXEC_EGL carries the name as the plugin declares it (a bare name is resolved
        // against the plugin's library directory by the loader), while SDL asks for a real file
        // and gets the resolved path when the plugin ships one.
        envMap["POJAVEXEC_EGL"] = eglName
        packagedLibrary(eglName)?.let { envMap["SDL_EGL_LIBRARY"] = it }
        packagedLibrary(glName)?.let { envMap["SDL_OPENGL_LIBRARY"] = it }
    }

    /**
     * True when every library this plugin declares is really there.
     *
     * Names that belong to the platform loader are expected to be absent from the plugin and are
     * not judged; anything else it names has to exist, or loading it later fails fatally.
     */
    private fun declaresItsLibraries(): Boolean {
        fun usable(name: String): Boolean =
            packagedLibrary(name) != null || name in SYSTEM_LIBRARIES
        if (!usable(glName)) return false
        if (eglName == glName) return true
        return usable(eglName)
    }

    /** Values passed directly to MojoExec; inspecting this plan never loads native code. */
    internal data class EglLaunchPlan(val library: String, val namespaceBypass: Boolean, val gles: Boolean, val major: Int)

    /** ES major version the plugin asks for, 3 when it does not say. */
    internal fun glesMajor(): Int =
        env["LIBGL_ES"]?.trim()?.toIntOrNull()?.takeIf { it == 2 || it == 3 } ?: 3

    /**
     * How MojoExec should prepare the plugin's EGL.
     *
     * A renderer plugin is a desktop-GL-over-GLES translator: it is asked for an OpenGL **ES**
     * context and exposes the desktop GL API on top of it (Emesis: "OpenGL 3.2+ Core over OpenGL
     * ES 3.x", GL4ES and LTW work the same way). Asking it for a desktop context instead makes
     * GLFW fail with "EGL: Failed to find support for OpenGL", because such an EGL only publishes
     * ES configs. Only plugins that declare a Mesa/Vulkan backend really provide desktop GL.
     */
    internal fun eglLaunchPlan(): EglLaunchPlan {
        val target = packagedLibrary(eglName) ?: eglName
        val desktop = RendererPluginV2.usesDesktopGl(rendererId, env)
        return EglLaunchPlan(target, useNamespaceBypass, !desktop, if (desktop) 0 else glesMajor())
    }

    /**
     * How the plugin's EGL provider is loaded, in order, as (library, useNamespace).
     *
     * A plugin is written against a host that exposes its library directory to the linker: Emesis
     * resolves its own directory with dladdr and loads its cores from there, and a bare library
     * name in a plugin manifest (PowerVR's `libEGL.so` is the system one) has to keep resolving
     * against system paths. The namespaced load is therefore tried first - it covers the plugin
     * directory while still inheriting the system search path - with a plain load as the last
     * resort, so a device where the namespace cannot be created behaves as it did before.
     */
    private fun loadAttempts(plan: EglLaunchPlan): List<Pair<String, Boolean>> {
        val plainFirst = metadataVersion >= 2 && !useNamespaceBypass
        return if (plainFirst) listOf(plan.library to false, plan.library to true)
        else listOf(plan.library to true, plan.library to false)
    }

    override fun setupRenderer(): Boolean = try {
        val initialize = {
            if (RendererPluginV2.usesVulkan(rendererId, env)) MojoExec.preloadVulkan()
            val plan = eglLaunchPlan()
            // Load the plugin's own OpenGL library into the process first, the way the launchers
            // these plugins were written for do: it registers the library under its soname, so the
            // plugin's internal bare-name lookups resolve and LWJGL binds GL to an already loaded
            // library instead of a second copy. A plugin that cannot be preloaded this way is not
            // fatal here; the loader below still runs.
            packagedLibrary(glName)?.let { gl ->
                val preloaded = runCatching { System.load(gl) }
                Logger.appendToLog("Renderer preload: $gl ok=${preloaded.isSuccess}" +
                    (preloaded.exceptionOrNull()?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""))
            }
            // MojoExec builds one linker namespace per process out of this path and keeps it, so
            // the plugin directory has to be in it before the first attempt.
            GameRenderer.setRendererLibraryPath(Tools.NATIVE_LIB_DIR, nativeLibDir)
            Logger.appendToLog("Renderer plan: tag=$pluginTag egl=${plan.library} eglDeclared=$eglName " +
                "gl=${glLibraryPath()} gles=${plan.gles} major=${plan.major} " +
                "namespaceBypass=${plan.namespaceBypass}")
            var loaded = false
            for ((library, bypass) in loadAttempts(plan)) {
                loaded = MojoExec.prepareEgl(library, bypass, plan.gles, plan.major)
                Logger.appendToLog("Renderer native load: tag=$pluginTag egl=$library gles=${plan.gles} " +
                    "major=${plan.major} namespaceBypass=$bypass loaded=$loaded")
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


    /**
     * Renderer plugins the launcher ships with, probed by name on every scan.
     *
     * Enumerating installed applications is not guaranteed to return a package that has no code and
     * no activity (the PowerVR plugin is exactly that: hasCode="false", no component at all), and
     * some devices filter such packages out of the list. Asking for the package by name is covered
     * by the <queries> entries in the manifest and returns it anyway.
     */
    private val KNOWN_PLUGIN_PACKAGES = listOf(
        "com.entitybrian.emesis",
        "np.proton.renderer.powervr"
    )

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
        // Ask for the plugins this launcher knows by name. A code-less plugin APK without any
        // component is the case application enumeration drops on some Android versions, and a
        // silent miss looks exactly like "the plugin is not installed".
        for (packageName in KNOWN_PLUGIN_PACKAGES) {
            if (!seen.add(packageName)) continue
            val appInfo = try {
                pm.getApplicationInfo(packageName, PACKAGE_FLAGS)
            } catch (error: PackageManager.NameNotFoundException) {
                note("$packageName: not installed (or not visible to the launcher)")
                null
            } catch (error: Exception) {
                note("$packageName: ${error.javaClass.simpleName}: ${error.message}")
                null
            }
            if (appInfo != null) parsePlugin(context, pm, appInfo)?.let { discovered.add(it) }
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

    /**
     * Whether the renderer plugins this launcher knows by name were found, for the Renderer tab
     * and the launcher log. A plugin APK that is installed but cannot be seen by the launcher looks
     * exactly like one that was never installed, so the difference is stated.
     */
    @JvmStatic
    fun pluginStatus(context: Context): String {
        runCatching { loadPlugins(context) }
        val missing = ArrayList<String>()
        for (known in KNOWN_PLUGIN_PACKAGES) {
            var found = false
            for (plugin in plugins) {
                if (plugin.packageName == known) found = true
            }
            if (!found) {
                val label = AerixRendererBrands.nameFor(known)
                missing.add(if (label == null) known else label)
            }
        }
        val status = if (missing.isEmpty()) "all detected"
            else "not detected: " + missing.joinToString(", ")
        Logger.appendToLog("Renderer plugin status: $status")
        for (note in scanNotes) Logger.appendToLog("Renderer plugin note: $note")
        return status
    }

    /**
     * The renderer to actually launch with.
     *
     * A stale selection is a real case: the tag of an Emesis plugin that is now ignored because the
     * launcher ships Emesis itself, or of a plugin whose libraries are gone. Returning that tag
     * would drop the player onto the fallback renderer silently; an Emesis selection becomes the
     * bundled Emesis, and anything else keeps the built-in choice that was already made.
     */
    @JvmStatic
    fun resolveLaunchTag(rendererTag: String?): String? {
        if (rendererTag == null || !rendererTag.startsWith(AerixRendererPlugin.TAG_PREFIX)) return rendererTag
        if (findByTag(rendererTag) != null) return rendererTag
        if (net.kdt.pojavlaunch.game.renderer.impl.EmesisRenderSpec.available()) {
            return net.kdt.pojavlaunch.game.renderer.def.Renderers.EMESIS_RENDERER
        }
        return null
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
            if (plugin != null && !File(plugin.glLibraryPath()).isFile) {
                // The plugin is installed but its OpenGL library is not on disk. Passing that path
                // to LWJGL is exactly the crash this check exists to prevent.
                Log.w("AerixRendererPlugin", "Selected plugin ${plugin.packageName} has no usable GL library; keeping the built-in renderer")
                Logger.appendToLog("Renderer plugin ${plugin.packageName} has no usable GL library; using the built-in renderer")
                return
            }
            if (plugin == null) {
                Log.w("AerixRendererPlugin", "Selected renderer plugin is not installed; keeping built-in renderer")
                return
            }
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
    private fun nativeLibraryDirOf(context: Context, info: ApplicationInfo): String? {
        info.nativeLibraryDir?.let { dir -> if (File(dir).isDirectory) return dir }
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
        // Nothing was unpacked for this package. An APK can keep its libraries compressed inside
        // itself (`extractNativeLibs="false"`), in which case Android never creates a library
        // directory. Unpack them ourselves, per ABI, into our private storage.
        return extractLibrariesFromApk(context, info, abi)
    }

    /** Unpacks the libraries of `lib/<abi>` of a plugin APK that keeps them compressed. */
    private fun extractLibrariesFromApk(context: Context, info: ApplicationInfo, abi: String): String? {
        val target = File(context.noBackupFilesDir, "renderer-libs/" + info.packageName)
        val stamp = File(target, ".stamp")
        val expected = info.sourceDir + "|" + abi
        if (stamp.isFile && runCatching { stamp.readText() }.getOrNull() == expected) {
            val unpacked = target.listFiles()
            if (unpacked != null && unpacked.any { it.name.endsWith(".so") }) return target.absolutePath
        }
        var extracted: String? = null
        runCatching {
            val zip = java.util.zip.ZipFile(File(info.sourceDir))
            try {
                val prefix = "lib/" + abi + "/"
                val entries = zip.entries().asSequence()
                    .filter { it.name.startsWith(prefix) && it.name.endsWith(".so") && !it.isDirectory }
                    .toList()
                if (entries.isNotEmpty()) {
                    if (target.exists()) target.deleteRecursively()
                    if (target.mkdirs() || target.isDirectory) {
                        for (entry in entries) {
                            val name = entry.name.substringAfterLast('/')
                            zip.getInputStream(entry).use { input ->
                                File(target, name).outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        stamp.writeText(expected)
                        extracted = target.absolutePath
                    }
                }
            } finally {
                zip.close()
            }
        }
        return extracted
    }

    private fun parsePlugin(
        context: Context,
        pm: PackageManager,
        info: ApplicationInfo
    ): AerixRendererPlugin? = runCatching {
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0 || RetiredRendererCleanup.isRetiredPackage(info.packageName)) return null
        val meta = info.metaData ?: return null
        val marker = markerForMeta(meta, info.packageName)
        if (marker == null) {
            if (meta.containsKey("renderer")) {
                note("${info.packageName}: declares a renderer but no plugin marker")
            }
            return null
        }
        val emesisStyle = marker == "emesisPlugin" || marker == "aerixPlugin"
        if (meta.containsKey("fclPlugin_V2")) {
            val plugin = runCatching {
                val metadata = when (val value = meta.get("fclPlugin_V2")) {
                    is Int -> pm.getResourcesForApplication(info).getString(value)
                    is String -> value
                    else -> error("Invalid V2 config resource")
                }
                val nativeDir = requireNotNull(nativeLibraryDirOf(context, info)) { "No native library directory" }
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
        val nativeDir = nativeLibraryDirOf(context, info) ?: run {
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

        // The launcher ships Emesis itself, complete and matched to its own libraries. An
        // installed Emesis plugin that is older, partial or built for another ABI would only
        // shadow it - and when its libraries are missing the game dies at window creation - so
        // the bundled renderer wins and the plugin is left alone.
        if (rendererId == EMESIS_RENDERER_ID &&
            net.kdt.pojavlaunch.game.renderer.impl.EmesisRenderSpec.available()
        ) {
            note("${info.packageName}: ignored, the launcher ships its own Emesis")
            return null
        }

        val brand = AerixRendererBrands.forPackage(info.packageName)
        AerixRendererPlugin(
            packageName = info.packageName,
            appName = appName,
            appVersion = appVersion,
            rendererId = rendererId,
            // A known plugin is shown under its own name; anything else keeps "<des> (<app>)".
            displayName = brand?.name ?: if (des.contains(appName, ignoreCase = true) ||
                appName.contains(des, ignoreCase = true)) des else "$des ($appName)",
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
