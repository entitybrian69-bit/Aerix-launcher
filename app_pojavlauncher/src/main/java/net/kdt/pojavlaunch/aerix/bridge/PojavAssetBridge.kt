/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: in-launcher downloads of mods, resource packs, shader packs and worlds, ported from
 * mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/DownloadModScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/DownloadResourcePackScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/DownloadShadersScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/DownloadSavesScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/assets/search/*Screen.kt
 * HTTP goes through Pojav's ApiHandler and files through Pojav's DownloadUtils (SHA-1 verified).
 * Files land in the selected instance's folders, the same ones PojavContentBridge manages.
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

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler
import net.kdt.pojavlaunch.utils.DownloadUtils
import java.io.File

enum class AssetPlatform { MODRINTH, CURSEFORGE }

/** Downloadable content types; [folder] is the instance folder that receives the file. */
enum class AssetType(
    val folder: ContentKind,
    /** Modrinth project_type, or null when Modrinth has no such type. */
    val modrinthType: String?,
    /** CurseForge classId for Minecraft (gameId 432). */
    val curseForgeClassId: Int
) {
    MOD(ContentKind.MODS, "mod", 6),
    RESOURCE_PACK(ContentKind.RESOURCE_PACKS, "resourcepack", 12),
    SHADER_PACK(ContentKind.SHADER_PACKS, "shader", 6552),
    WORLD(ContentKind.SAVES, null, 17)
}

class AssetProject(
    val platform: AssetPlatform,
    val id: String,
    val title: String,
    val description: String,
    val author: String,
    val iconUrl: String?,
    val downloads: Long
)

class AssetFile(
    val name: String,
    val fileName: String,
    val url: String?,
    val sha1: String?,
    val gameVersions: List<String>,
    val loaders: List<String>
)

class AssetPage(val projects: List<AssetProject>, val total: Int)

/** Minecraft version and loader of the selected instance, used as default search filters. */
class InstanceTarget(val mcVersion: String?, val loader: String?)

object PojavAssetBridge {
    private const val CURSEFORGE_GAME_ID = 432
    private const val PAGE_SIZE = 30
    private val mcVersionPattern = Regex("""(\d+\.\d+(?:\.\d+)?)""")

    private val modrinth = ApiHandler("https://api.modrinth.com/v2")

    /** User key (Aerix "curseForgeApiKey") first, then the key baked into the build. */
    fun curseForgeKey(context: Context): String? {
        val user = AerixPreferences.curseForgeApiKey.trim()
        if (user.isNotEmpty()) return user
        val built = context.getString(R.string.curseforge_api_key).trim()
        return built.takeIf { it.isNotEmpty() && it != "DUMMY" }
    }

    fun platforms(context: Context, type: AssetType): List<AssetPlatform> = buildList {
        if (type.modrinthType != null) add(AssetPlatform.MODRINTH)
        if (curseForgeKey(context) != null) add(AssetPlatform.CURSEFORGE)
    }

    suspend fun instanceTarget(): InstanceTarget = withContext(Dispatchers.IO) {
        val versionId = runCatching { Instances.loadSelectedInstance()?.versionId }.getOrNull()
            ?: return@withContext InstanceTarget(null, null)
        val id = versionId.lowercase()
        val loader = when {
            id.contains("neoforge") -> "neoforge"
            id.contains("fabric") -> "fabric"
            id.contains("quilt") -> "quilt"
            id.contains("forge") -> "forge"
            else -> null
        }
        InstanceTarget(mcVersionPattern.find(versionId)?.value, loader)
    }

    suspend fun search(
        context: Context,
        platform: AssetPlatform,
        type: AssetType,
        query: String,
        mcVersion: String?,
        loader: String?,
        offset: Int
    ): AssetPage? = withContext(Dispatchers.IO) {
        runCatching {
            when (platform) {
                AssetPlatform.MODRINTH -> searchModrinth(type, query, mcVersion, loader, offset)
                AssetPlatform.CURSEFORGE -> searchCurseForge(context, type, query, mcVersion, loader, offset)
            }
        }.getOrNull()
    }

    suspend fun files(context: Context, project: AssetProject): List<AssetFile>? = withContext(Dispatchers.IO) {
        runCatching {
            when (project.platform) {
                AssetPlatform.MODRINTH -> modrinthFiles(project.id)
                AssetPlatform.CURSEFORGE -> curseForgeFiles(context, project.id)
            }
        }.getOrNull()
    }

    /**
     * Downloads [file] into the [type] folder of the selected instance with SHA-1 verification.
     * Worlds are extracted into saves/ through PojavContentBridge.
     */
    suspend fun install(type: AssetType, file: AssetFile): Boolean = withContext(Dispatchers.IO) {
        val url = file.url ?: return@withContext false
        val dir = PojavContentBridge.directory(type.folder) ?: return@withContext false
        if (!dir.isDirectory && !dir.mkdirs()) return@withContext false
        val safeName = File(file.fileName).name.ifBlank { return@withContext false }
        runCatching {
            if (type == AssetType.WORLD) {
                val temp = File.createTempFile("aerix_world", ".zip", dir.parentFile)
                try {
                    temp.delete()
                    DownloadUtils.ensureSha1(temp, file.sha1) { DownloadUtils.downloadFile(url, temp) }
                    PojavContentBridge.extractWorldFile(temp, dir, safeName.removeSuffix(".zip"))
                } finally {
                    temp.delete()
                }
            } else {
                val target = File(dir, safeName)
                DownloadUtils.ensureSha1(target, file.sha1) { DownloadUtils.downloadFile(url, target) }
            }
        }.isSuccess
    }

    // Modrinth

    private fun searchModrinth(type: AssetType, query: String, mcVersion: String?, loader: String?, offset: Int): AssetPage? {
        val projectType = type.modrinthType ?: return AssetPage(emptyList(), 0)
        val facets = StringBuilder("[[\"project_type:$projectType\"]")
        if (!mcVersion.isNullOrBlank()) facets.append(",[\"versions:$mcVersion\"]")
        if (type == AssetType.MOD && !loader.isNullOrBlank()) facets.append(",[\"categories:$loader\"]")
        facets.append("]")
        val params = HashMap<String, Any>()
        params["facets"] = facets.toString()
        params["query"] = query
        params["limit"] = PAGE_SIZE
        params["offset"] = offset
        params["index"] = if (query.isBlank()) "downloads" else "relevance"
        val response = modrinth.get("search", params, JsonObject::class.java) ?: return null
        val hits = response.getAsJsonArray("hits") ?: return null
        val projects = hits.mapNotNull { element ->
            val hit = element.asJsonObject
            AssetProject(
                platform = AssetPlatform.MODRINTH,
                id = hit.string("project_id") ?: return@mapNotNull null,
                title = hit.string("title") ?: "",
                description = hit.string("description") ?: "",
                author = hit.string("author") ?: "",
                iconUrl = hit.string("icon_url")?.takeIf { it.isNotBlank() },
                downloads = hit.long("downloads")
            )
        }
        return AssetPage(projects, response.get("total_hits")?.asInt ?: projects.size)
    }

    private fun modrinthFiles(projectId: String): List<AssetFile>? {
        val versions = modrinth.get("project/$projectId/version", JsonArray::class.java) ?: return null
        return versions.mapNotNull { element ->
            val version = element.asJsonObject
            val files = version.getAsJsonArray("files") ?: return@mapNotNull null
            if (files.size() == 0) return@mapNotNull null
            val primary = files.firstOrNull { it.asJsonObject.get("primary")?.asBoolean == true }?.asJsonObject
                ?: files[0].asJsonObject
            AssetFile(
                name = version.string("name") ?: version.string("version_number") ?: "",
                fileName = primary.string("filename") ?: return@mapNotNull null,
                url = primary.string("url"),
                sha1 = primary.getAsJsonObject("hashes")?.string("sha1"),
                gameVersions = version.getAsJsonArray("game_versions").strings(),
                loaders = version.getAsJsonArray("loaders").strings()
            )
        }
    }

    // CurseForge

    private fun curseForge(context: Context): ApiHandler? =
        curseForgeKey(context)?.let { ApiHandler("https://api.curseforge.com/v1", it) }

    private fun searchCurseForge(context: Context, type: AssetType, query: String, mcVersion: String?, loader: String?, offset: Int): AssetPage? {
        val api = curseForge(context) ?: return null
        val params = HashMap<String, Any>()
        params["gameId"] = CURSEFORGE_GAME_ID
        params["classId"] = type.curseForgeClassId
        params["searchFilter"] = query
        params["sortField"] = if (query.isBlank()) 6 else 1 // 6 = total downloads, 1 = featured/relevancy
        params["sortOrder"] = "desc"
        params["pageSize"] = PAGE_SIZE
        params["index"] = offset
        if (!mcVersion.isNullOrBlank()) params["gameVersion"] = mcVersion
        if (type == AssetType.MOD) curseForgeLoaderType(loader)?.let { params["modLoaderType"] = it }
        val response = api.get("mods/search", params, JsonObject::class.java) ?: return null
        val data = response.getAsJsonArray("data") ?: return null
        val projects = data.mapNotNull { element ->
            val mod = element.asJsonObject
            AssetProject(
                platform = AssetPlatform.CURSEFORGE,
                id = mod.get("id")?.asString ?: return@mapNotNull null,
                title = mod.string("name") ?: "",
                description = mod.string("summary") ?: "",
                author = mod.getAsJsonArray("authors")?.firstOrNull()?.asJsonObject?.string("name") ?: "",
                iconUrl = mod.getAsJsonObject("logo")?.string("thumbnailUrl"),
                downloads = mod.long("downloadCount")
            )
        }
        val total = response.getAsJsonObject("pagination")?.get("totalCount")?.asInt ?: projects.size
        // CurseForge caps index + pageSize at 10000.
        return AssetPage(projects, minOf(total, 10000 - PAGE_SIZE))
    }

    private fun curseForgeLoaderType(loader: String?): Int? = when (loader) {
        "forge" -> 1
        "fabric" -> 4
        "quilt" -> 5
        "neoforge" -> 6
        else -> null
    }

    private fun curseForgeFiles(context: Context, modId: String): List<AssetFile>? {
        val api = curseForge(context) ?: return null
        val result = ArrayList<AssetFile>()
        var index = 0
        while (true) {
            val params = HashMap<String, Any>()
            params["index"] = index
            params["pageSize"] = 50
            val response = api.get("mods/$modId/files", params, JsonObject::class.java) ?: return if (result.isEmpty()) null else result
            val data = response.getAsJsonArray("data") ?: break
            data.forEach { element ->
                val file = element.asJsonObject
                val versions = file.getAsJsonArray("gameVersions").strings()
                result.add(
                    AssetFile(
                        name = file.string("displayName") ?: file.string("fileName") ?: "",
                        fileName = file.string("fileName") ?: return@forEach,
                        url = file.string("downloadUrl"),
                        sha1 = file.getAsJsonArray("hashes")
                            ?.firstOrNull { it.asJsonObject.get("algo")?.asInt == 1 }
                            ?.asJsonObject?.string("value"),
                        gameVersions = versions.filter { mcVersionPattern.matches(it) },
                        loaders = versions.filter { !mcVersionPattern.matches(it) && it.lowercase() in setOf("forge", "fabric", "quilt", "neoforge") }
                            .map { it.lowercase() }
                    )
                )
            }
            index += data.size()
            val total = response.getAsJsonObject("pagination")?.get("totalCount")?.asInt ?: index
            if (data.size() == 0 || index >= total || index >= 1000) break
        }
        return result
    }

    // JSON helpers

    private fun JsonObject.string(key: String): String? {
        val element: JsonElement = get(key) ?: return null
        return if (element.isJsonNull) null else element.asString
    }

    private fun JsonObject.long(key: String): Long {
        val element = get(key) ?: return 0L
        return if (element.isJsonNull) 0L else runCatching { element.asLong }.getOrDefault(0L)
    }

    private fun JsonArray?.strings(): List<String> =
        this?.mapNotNull { if (it.isJsonNull) null else it.asString } ?: emptyList()
}
