package net.kdt.pojavlaunch.aerix.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.instances.Instances
import java.io.File

/**
 * Backend of the in-game downloader.
 *
 * The downloader runs while the game is in the background, so everything here works against the
 * instance that is currently running: its folder is where resource packs, shaders and worlds are
 * placed, and its version is the one every search is filtered by.
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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

/** The instance the game is running, as the downloader needs to see it. */
class GameDownloadTarget(
    val instanceName: String,
    val mcVersion: String?,
    val loader: String?,
    val gameDirectory: File?
) {
    /** Version line of the header, e.g. "1.21.4 · Fabric". */
    val versionLabel: String
        get() {
            val version = mcVersion ?: "?"
            return if (loader.isNullOrEmpty()) version else "$version · ${loader.replaceFirstChar { it.uppercase() }}"
        }

    companion object {
        private val MC_VERSION = Regex("\\d+\\.\\d+(\\.\\d+)?")

        fun of(versionId: String?, instanceName: String, gameDirectory: File?): GameDownloadTarget {
            val id = versionId.orEmpty()
            val loader = when {
                id.contains("neoforge", true) -> "neoforge"
                id.contains("fabric", true) -> "fabric"
                id.contains("quilt", true) -> "quilt"
                id.contains("forge", true) -> "forge"
                else -> null
            }
            return GameDownloadTarget(
                instanceName = instanceName,
                mcVersion = MC_VERSION.find(id)?.value,
                loader = loader,
                gameDirectory = gameDirectory
            )
        }
    }
}

/** What went wrong while searching or downloading, in the words of the menu. */
enum class DownloadError { OFFLINE, API, NO_TARGET, STORAGE, FILE }

/** Result of one download: the file that was written, or why it was not. */
class DownloadOutcome(val fileName: String?, val error: DownloadError?) {
    val isSuccess: Boolean get() = fileName != null
}

object PojavGameDownloadsBridge {
    /** How many results a page of the in-game downloader holds. */
    const val PAGE_SIZE = 20

    /** The instance the game runs, read the same way the game itself reads it. */
    fun runningTarget(): GameDownloadTarget {
        val instance = runCatching { Instances.loadSelectedInstance() }.getOrNull()
        return GameDownloadTarget.of(
            versionId = instance?.versionId,
            instanceName = instance?.name.orEmpty(),
            gameDirectory = runCatching { instance?.gameDirectory }.getOrNull()
        )
    }

    /** Whether the device has a usable connection; used to tell offline from a failing API. */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** CurseForge answers only with an API key, the one from the launcher settings is used. */
    fun hasCurseForgeKey(context: Context): Boolean = PojavAssetBridge.curseForgeKey(context) != null

    /**
     * Make sure the folder of a content kind exists and can be written to.
     *
     * Everything is written inside the instance folder, which belongs to the app, so no storage
     * permission is involved. A folder that is not writable means the storage is full or the
     * instance lives on a mounted medium that went away, and the downloader says so instead of
     * starting a download that would fail halfway through.
     */
    fun prepareDirectory(type: AssetType): DownloadError? = with(type.folder) {
        if (this == null) return DownloadError.NO_TARGET
        val directory = PojavContentBridge.directory(this) ?: return DownloadError.NO_TARGET
        if (!directory.isDirectory && !directory.mkdirs()) return DownloadError.STORAGE
        if (!directory.canWrite()) return DownloadError.STORAGE
        null
    }

    /** Search results for a page of the downloader, or the reason there are none. */
    suspend fun search(
        context: Context,
        platform: AssetPlatform,
        type: AssetType,
        query: String,
        target: GameDownloadTarget,
        offset: Int = 0
    ): Pair<AssetPage?, DownloadError?> = withContext(Dispatchers.IO) {
        if (platform == AssetPlatform.CURSEFORGE && !hasCurseForgeKey(context)) {
            return@withContext null to DownloadError.API
        }
        val page = PojavAssetBridge.search(
            context = context,
            platform = platform,
            type = type,
            query = query,
            mcVersion = target.mcVersion,
            loader = null,
            offset = offset
        )
        if (page != null) return@withContext page to null
        if (!isOnline(context)) return@withContext null to DownloadError.OFFLINE
        null to DownloadError.API
    }

    /** Names of what is already installed in the instance folder of [type]. */
    suspend fun installed(type: AssetType): Set<String> = runCatching {
        PojavAssetBridge.installedFingerprints(type)
    }.getOrDefault(emptySet())

    /**
     * Download the best file of a project into the instance folder.
     *
     * The file matching the version of the running game is preferred; when a project has no file
     * for that version the newest one is used, and the caller is told which file that was, so the
     * player knows what landed in the folder.
     */
    suspend fun install(
        context: Context,
        type: AssetType,
        project: AssetProject,
        target: GameDownloadTarget
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        prepareDirectory(type)?.let { return@withContext DownloadOutcome(null, it) }

        val files = runCatching { PojavAssetBridge.files(context, project) }.getOrNull()
        if (files.isNullOrEmpty()) {
            return@withContext DownloadOutcome(
                null,
                if (!isOnline(context)) DownloadError.OFFLINE else DownloadError.API
            )
        }
        val file = pickFile(files, target.mcVersion)
            ?: return@withContext DownloadOutcome(null, DownloadError.FILE)

        val installed = runCatching { PojavAssetBridge.install(type, file) }.getOrDefault(false)
        if (installed) DownloadOutcome(file.fileName, null)
        else DownloadOutcome(null, if (!isOnline(context)) DownloadError.OFFLINE else DownloadError.FILE)
    }

    /**
     * The file to install: an exact version match first, then a file without a version list
     * (resource packs and shaders often carry none), then the newest file of the project.
     */
    fun pickFile(files: List<AssetFile>, mcVersion: String?): AssetFile? {
        if (files.isEmpty()) return null
        val byNewest = compareByDescending<AssetFile> { it.releaseType.equals("release", true) }
            .thenByDescending { it.datePublished }
        if (mcVersion != null) {
            val matching = files.filter { file ->
                file.gameVersions.any { it.equals(mcVersion, true) }
            }
            matching.sortedWith(byNewest).firstOrNull()?.let { return it }
        }
        val unversioned = files.filter { it.gameVersions.isEmpty() }
        if (unversioned.isNotEmpty()) return unversioned.sortedWith(byNewest).first()
        return files.sortedWith(byNewest).first()
    }

    /** Short human size, used in the result rows. */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "?"
        return when {
            bytes >= 1024L * 1024L * 1024L -> String.format("%.1f GB", bytes / 1073741824.0)
            bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / 1048576.0)
            bytes >= 1024L -> String.format("%.0f kB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /** Whether the device runs a system old enough to need the legacy storage permission. */
    fun needsLegacyStoragePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
}
