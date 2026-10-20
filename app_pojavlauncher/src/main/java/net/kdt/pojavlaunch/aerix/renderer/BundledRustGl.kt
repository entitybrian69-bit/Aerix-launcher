package net.kdt.pojavlaunch.aerix.renderer

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.JsonParser
import net.kdt.pojavlaunch.aerix.bridge.AerixRendererPlugin
import net.kdt.pojavlaunch.aerix.bridge.RendererPluginV2
import java.io.File

/** Assets and hashes are authenticated by the launcher's Android APK signing certificate. */
internal object BundledRustGl {
    const val PACKAGE = "net.kdt.pojavlaunch.renderer.rustgl"
    private const val BASE = "renderer-bundles/rustgl"
    private const val INDEX = "$BASE/index.json"
    private fun store(context: Context) = RendererBundleStore(File(context.noBackupFilesDir, "renderer-bundles/rustgl"))

    fun discover(context: Context): AerixRendererPlugin? {
        if (Build.VERSION.SDK_INT < 26) return null
        val index = try {
            context.assets.open(INDEX).bufferedReader().use {
                val text = it.readText(); require(text.length <= 64 * 1024)
                JsonParser.parseString(text).asJsonObject
            }
        } catch (_: java.io.FileNotFoundException) { return null }
        if (index.get("redistributionApproved")?.asBoolean != true && index.get("experimentalUserAuthorized")?.asBoolean != true) return null
        require(index.get("schema").asInt == 1)
        require(index.get("backend").asString == "gles") { "RustGL Vulkan implementation has no release qualification" }
        val abi = (if (android.os.Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS).firstOrNull() ?: return null
        val releases = index.getAsJsonArray("releases").map { element ->
            val entry = element.asJsonObject
            BundleRelease(entry.get("version").asString, entry.get("revision").asString, entry.get("abi").asString,
                entry.getAsJsonArray("files").map { item -> item.asJsonObject.let {
                    BundleFile(it.get("name").asString, it.get("bytes").asLong, it.get("sha256").asString)
                } }).also { it.validate() }
        }.filter { it.abi == abi }
        if (releases.isEmpty()) return null
        require(releases.map { it.id }.distinct().size == releases.size)
        val storage = store(context)
        storage.recover(releases)
        val candidate = releases.firstOrNull { !storage.rejected(it) }
        if (candidate != null) {
            runCatching {
                storage.install(candidate) { name -> context.assets.open("$BASE/${candidate.version}/${candidate.abi}/$name") }
                storage.activate(candidate)
            }.onFailure { Log.e("BundledRustGL", "Bundle update rejected; retaining verified previous release", it) }
        }
        val release = storage.active(releases) ?: return null
        val directory = storage.verify(release) ?: return null
        val gl = File(directory, "librust_gl.so")
        require(RendererPluginV2.matchesElfAbi(gl, abi)) { "RustGL ELF does not match the game process ABI" }
        require(release.files.filter { it.name.endsWith(".so") }.all {
            RendererPluginV2.matchesElfAbi(File(directory, it.name), abi)
        })
        return AerixRendererPlugin(
            packageName = PACKAGE, appName = "Aerix RustGL", appVersion = release.version,
            rendererId = "opengles3_rust", displayName = "RustGL (Experimental)",
            summary = "APK-authenticated RustGL bundle ${release.version} · GLES",
            minMCVer = null, maxMCVer = null, glName = gl.absolutePath, eglName = gl.absolutePath,
            nativeLibDir = directory.absolutePath,
            env = RustGlBackendPolicy.environment(mapOf(
                "AERIX_RENDERER_STARTUP_TRACE" to "1",
                "RENDERER_SPOOF_GL" to "1", "MESA_GL_VERSION_OVERRIDE" to "4.4",
                "MESA_GLSL_VERSION_OVERRIDE" to "330", "LIB_MESA_NAME" to "",
                "JAVA_TOOL_OPTIONS" to "-Dorg.lwjgl.util.NoChecks=true"
            )),
            dlopen = emptyList(), metadataVersion = 2,
            // This bundle depends on public system EGL/GLES, not private custom-driver libraries.
            // Keep Android's normal graphics-library namespace; other plugins retain their policy.
            useNamespaceBypass = false,
            initializeBundle = { initialize ->
                val attempt = storage.beginInit(release)
                var success = false
                try { success = initialize(); success } finally { attempt.finish(success) }
            }
        ).takeIf { it.compatibleDevice(context) }
    }
}
