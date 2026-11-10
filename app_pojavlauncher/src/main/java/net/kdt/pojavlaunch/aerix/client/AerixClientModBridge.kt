/*
 * Aerix launcher: delivery of the launcher's own Fabric bridge mod into Client instances.
 *
 * The in-game half of the Aerix Client features (launcher-served animated capes, live option
 * changes such as the ZOOM button or Server Boost, and the status report that the Client menu
 * shows) lives in `:aerix_client_mod`. Gradle packages that jar as the APK asset
 * `components/aerix_client_mod/aerix_client_mod.jar`, exactly like the `forge_installer`
 * component; this object copies it into `<gameDir>/mods/` right before Minecraft starts.
 *
 * Without this step every bridge file written below `config/` would sit there unread, which is
 * why capes stayed hidden and the ZOOM pill did nothing on device.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Installs and reports on the bundled `Aerix Client` Fabric bridge mod. */
object AerixClientModBridge {
    /** Asset directory produced by `AssetTaskRegistrar.projectJarDependency`. */
    const val ASSET_DIR = "components/aerix_client_mod"
    private const val ASSET_JAR = "aerix_client_mod.jar"
    private const val ASSET_VERSION = "version"

    /** Name the bridge mod has inside the instance, so it can be told apart from third-party jars. */
    const val INSTALLED_NAME = "aerix_client.jar"
    private const val PREFS = "aerix_client_mod_install"

    /** Last outcome of [installIfNeeded], read by the Client menu status line. */
    @JvmStatic
    @Volatile
    var lastStatus: String = "Not checked yet"
        private set

    data class InstallResult(val installed: Boolean, val updated: Boolean, val detail: String)

    /**
     * Copies the bundled bridge mod into `mods/`, replacing an older copy.
     *
     * Deliberately never touches a jar the player put there themselves: only the file this object
     * owns (`mods/aerix_client.jar`) is written.
     */
    @JvmStatic
    fun installIfNeeded(context: Context, gameDir: File): InstallResult {
        val digest = runCatching { MessageDigest.getInstance("SHA-1") }.getOrNull()
            ?: return InstallResult(false, false, "This device cannot hash the bridge mod").also {
                lastStatus = it.detail
            }
        return runCatching { install(context, gameDir, digest) }
            .onFailure { error ->
                lastStatus = "Install failed: ${error.javaClass.simpleName}"
            }
            .getOrElse { InstallResult(false, false, lastStatus) }
    }

    private fun install(context: Context, gameDir: File, digest: MessageDigest): InstallResult {
        val assetVersion = readAssetText(context, "$ASSET_DIR/$ASSET_VERSION").trim()
        val jarBytes = context.assets.open("$ASSET_DIR/$ASSET_JAR").use { it.readBytes() }
        if (jarBytes.isEmpty()) return InstallResult(false, false, "Bundled bridge mod is empty")
            .also { lastStatus = it.detail }

        val target = File(gameDir, "mods").apply { mkdirs() }.let { File(it, INSTALLED_NAME) }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val recorded = prefs.getString(target.absolutePath, null)
        val sameVersion = assetVersion.isNotEmpty() && recorded == assetVersion
        val upToDate = sameVersion && target.isFile && target.length() == jarBytes.size.toLong()
        if (upToDate) {
            lastStatus = "Installed (build ${assetVersion.ifEmpty { "unknown" }})"
            return InstallResult(true, false, lastStatus)
        }

        // Atomic replace: a crash mid-copy must not leave a truncated jar in the mod folder.
        val temp = File(target.parentFile, "$INSTALLED_NAME.tmp")
        temp.writeBytes(jarBytes)
        if (!temp.renameTo(target)) {
            temp.delete()
            return InstallResult(target.isFile, false, "Could not replace the bridge mod file")
                .also { lastStatus = it.detail }
        }
        prefs.edit().putString(target.absolutePath, assetVersion).apply()
        lastStatus = "Updated to build ${assetVersion.ifEmpty { digest.digest(jarBytes).joinToString("") { "%02x".format(it) }.take(8) }}"
        return InstallResult(true, true, lastStatus)
    }

    /** Whether the instance already carries the bridge mod, without installing anything. */
    @JvmStatic
    fun isInstalled(gameDir: File?): Boolean =
        gameDir != null && File(gameDir, "mods/$INSTALLED_NAME").isFile

    /**
     * Reads the `aerix_status.properties` file the bridge mod writes from inside the game, so the
     * Client menu can say out loud whether capes / zoom / boost are actually live.
     */
    @JvmStatic
    fun readStatus(gameDir: File?): Map<String, String> {
        if (gameDir == null) return emptyMap()
        val file = File(gameDir, "config/aerix_status.properties")
        if (!file.isFile || file.length() > 262_144L) return emptyMap()
        return runCatching {
            file.readLines().mapNotNull { line ->
                val cut = line.indexOf('=')
                if (cut <= 0) null else line.substring(0, cut).trim() to line.substring(cut + 1).trim()
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun readAssetText(context: Context, path: String): String = runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrDefault("")
}
