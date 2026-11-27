package net.kdt.pojavlaunch.aerix.bridge

import android.content.Context
import android.util.Log
import net.kdt.pojavlaunch.Logger
import net.kdt.pojavlaunch.Tools
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/**
 * Automatic Android compatibility bridge for Moulberry's Flashback Fabric mod.
 *
 * When a Flashback JAR is detected in `<gameDir>/mods/`:
 * 1. Points `-Dimgui.library.path` at `Tools.NATIVE_LIB_DIR`, where Aerix bundles the ARM64
 *    Android Bionic `libimgui-moulberry90-java64.so` and `libc++_flashtoch.so` binaries (instead of
 *    Flashback's desktop x86_64 `.so`, which crashes with `UnsatisfiedLinkError`).
 * 2. Points `-Dorg.bytedeco.javacpp.cachedir` inside `Tools.DIR_CACHE` (`/data/...`) so JavaCPP
 *    never tries to `dlopen()` from non-executable `/storage/emulated/0`.
 * 3. Stages `aerix_flashback_compat.jar` from APK assets and injects it via `-Dfabric.addMods`
 *    to bypass desktop `NativeFileDialog` / `SDLDialog`, unsupported `mediacodec`/`v4l2m2m` probes,
 *    missing OpenAL loopback, and missing Bionic FFmpeg natives without touching `mods/`.
 */
object AerixFlashbackBridge {

    private const val TAG = "AerixFlashbackBridge"
    const val ASSET_DIR = "components/aerix_flashback_compat"
    private const val ASSET_JAR = "aerix_flashback_compat.jar"
    private const val ASSET_VERSION = "version"
    private const val IMGUI_SO = "libimgui-moulberry90-java64.so"
    private const val PREFS = "aerix_flashback_compat_install"

    @JvmStatic
    fun hasFlashback(gameDir: File?): Boolean {
        if (gameDir == null) return false
        val modsDir = File(gameDir, "mods")
        val jars = modsDir.listFiles { file -> file.isFile && file.name.endsWith(".jar", ignoreCase = true) }
            ?: return false
        for (jar in jars) {
            if (jar.name.contains("flashback", ignoreCase = true)) {
                return true
            }
            if (readFabricModId(jar) == "flashback") {
                return true
            }
        }
        return false
    }

    private fun hasThirdPartyFlashbackCompanion(gameDir: File?): Boolean {
        if (gameDir == null) return false
        val modsDir = File(gameDir, "mods")
        val jars = modsDir.listFiles { file -> file.isFile && file.name.endsWith(".jar", ignoreCase = true) }
            ?: return false
        for (jar in jars) {
            val lower = jar.name.lowercase()
            if (lower.contains("flashbacktouch") || lower.contains("flashtoch")) {
                return true
            }
            val id = readFabricModId(jar)
            if (id == "flashbacktouch" || id == "flashtoch") {
                return true
            }
        }
        return false
    }

    private fun readFabricModId(jar: File): String? = runCatching {
        ZipFile(jar).use { zip ->
            val entry = zip.getEntry("fabric.mod.json") ?: return null
            if (entry.size !in 1L..262_144L) return null
            val json = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            JSONObject(json).optString("id", "").takeIf { it.isNotEmpty() }
        }
    }.getOrNull()

    @JvmStatic
    fun stageCompatMod(context: Context): File? {
        return runCatching {
            val assetVersion = runCatching {
                context.assets.open("$ASSET_DIR/$ASSET_VERSION").bufferedReader().use { it.readText().trim() }
            }.getOrDefault("")
            val jarBytes = context.assets.open("$ASSET_DIR/$ASSET_JAR").use { it.readBytes() }
            if (jarBytes.isEmpty()) return null

            val dir = File(Tools.DIR_CACHE, "flashback_compat").apply { mkdirs() }
            val target = File(dir, ASSET_JAR)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val recorded = prefs.getString(target.absolutePath, null)
            val upToDate = assetVersion.isNotEmpty() &&
                recorded == assetVersion &&
                target.isFile &&
                target.length() == jarBytes.size.toLong()
            if (upToDate) return target

            val temp = File(dir, "$ASSET_JAR.tmp")
            temp.writeBytes(jarBytes)
            if (!temp.renameTo(target)) {
                temp.delete()
                return if (target.isFile) target else null
            }
            prefs.edit().putString(target.absolutePath, assetVersion).apply()
            target
        }.onFailure { err ->
            Log.w(TAG, "Failed to stage aerix_flashback_compat.jar", err)
        }.getOrNull()
    }

    @JvmStatic
    fun appendLaunchArgs(context: Context, gameDir: File?, javaArgList: MutableList<String>) {
        // Always keep JavaCPP cache in internal executable app cache (/data/...) rather than /storage/emulated/0.
        if (javaArgList.none { it.startsWith("-Dorg.bytedeco.javacpp.cachedir=") }) {
            val javacppCache = File(Tools.DIR_CACHE, "javacpp_cache").apply { mkdirs() }
            javaArgList.add("-Dorg.bytedeco.javacpp.cachedir=${javacppCache.absolutePath}")
        }

        if (!hasFlashback(gameDir)) return

        Logger.appendToLog("Flashback mod detected; enabling Android ARM64 ImGui & compatibility bridge")

        if (javaArgList.none { it.startsWith("-Dorg.lwjgl.nfd.linux.portal=") }) {
            javaArgList.add("-Dorg.lwjgl.nfd.linux.portal=false")
        }

        if (javaArgList.none { it.startsWith("-Dimgui.library.path=") }) {
            val nativeDir = Tools.NATIVE_LIB_DIR
            if (!nativeDir.isNullOrBlank() && File(nativeDir, IMGUI_SO).isFile) {
                javaArgList.add("-Dimgui.library.path=$nativeDir")
            }
        }

        if (!hasThirdPartyFlashbackCompanion(gameDir)) {
            val compatJar = stageCompatMod(context)
            if (compatJar != null && compatJar.isFile) {
                val existingIdx = javaArgList.indexOfFirst { it.startsWith("-Dfabric.addMods=") }
                if (existingIdx >= 0) {
                    val existing = javaArgList[existingIdx]
                    if (!existing.contains(compatJar.absolutePath)) {
                        javaArgList[existingIdx] = "$existing${File.pathSeparator}${compatJar.absolutePath}"
                    }
                } else {
                    javaArgList.add("-Dfabric.addMods=${compatJar.absolutePath}")
                }
            }
        }
    }
}
