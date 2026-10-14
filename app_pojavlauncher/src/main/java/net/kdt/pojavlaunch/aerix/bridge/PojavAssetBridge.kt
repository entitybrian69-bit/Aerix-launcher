/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: in-launcher discovery, project details, screenshots and downloads of modpacks, mods,
 * resource packs, shader packs and worlds, ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/DownloadScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/assets/search/SearchAssetsScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/assets/download/DownloadAssetsScreen.kt
 * HTTP goes through Pojav's ApiHandler; modpacks install through Pojav's ModrinthApi / CurseforgeApi;
 * single files go through Pojav's DownloadUtils (SHA-1 verified) into the selected instance's folders.
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
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler
import net.kdt.pojavlaunch.modloaders.modpacks.api.CurseforgeApi
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem
import net.kdt.pojavlaunch.utils.DownloadUtils
import java.io.File

enum class AssetPlatform(val displayName: String) {
    MODRINTH("Modrinth"),
    CURSEFORGE("CurseForge")
}

/** Downloadable content types; [folder] is the instance folder that receives single-file assets. */
enum class AssetType(
    val folder: ContentKind?,
    val modrinthType: String?,
    val curseForgeClassId: Int,
    val supportsLoaderFilter: Boolean,
    val categories: List<Pair<String, String>>
) {
    MODPACK(
        folder = null,
        modrinthType = "modpack",
        curseForgeClassId = 4471,
        supportsLoaderFilter = true,
        categories = listOf(
            "adventure" to "Adventure",
            "optimization" to "Optimization",
            "technology" to "Technology",
            "magic" to "Magic",
            "quests" to "Quests",
            "lightweight" to "Lightweight",
            "kitchen-sink" to "Kitchen Sink",
            "multiplayer" to "Multiplayer"
        )
    ),
    MOD(
        folder = ContentKind.MODS,
        modrinthType = "mod",
        curseForgeClassId = 6,
        supportsLoaderFilter = true,
        categories = listOf(
            "optimization" to "Optimization",
            "utility" to "Utility",
            "library" to "Library",
            "worldgen" to "World Generation",
            "technology" to "Technology",
            "magic" to "Magic",
            "adventure" to "Adventure",
            "storage" to "Storage",
            "equipment" to "Equipment",
            "mobs" to "Mobs",
            "food" to "Food",
            "decoration" to "Decoration"
        )
    ),
    RESOURCE_PACK(
        folder = ContentKind.RESOURCE_PACKS,
        modrinthType = "resourcepack",
        curseForgeClassId = 12,
        supportsLoaderFilter = false,
        categories = listOf(
            "16x" to "16x",
            "32x" to "32x",
            "64x" to "64x",
            "realistic" to "Realistic",
            "simplistic" to "Simplistic",
            "tweaks" to "Tweaks",
            "gui" to "GUI",
            "vanilla-like" to "Vanilla-like"
        )
    ),
    SHADER_PACK(
        folder = ContentKind.SHADER_PACKS,
        modrinthType = "shader",
        curseForgeClassId = 6552,
        supportsLoaderFilter = false,
        categories = listOf(
            "realistic" to "Realistic",
            "vanilla-like" to "Vanilla-like",
            "potato" to "Potato / Low",
            "low" to "Low",
            "medium" to "Medium",
            "high" to "High",
            "fantasy" to "Fantasy"
        )
    ),
    WORLD(
        folder = ContentKind.SAVES,
        modrinthType = "datapack",
        curseForgeClassId = 17,
        supportsLoaderFilter = false,
        categories = listOf(
            "adventure" to "Adventure",
            "survival" to "Survival",
            "creation" to "Creation",
            "parkour" to "Parkour",
            "puzzle" to "Puzzle"
        )
    )
}

enum class AssetSort(val label: String, val modrinthIndex: String, val curseForgeField: Int) {
    RELEVANCE("Relevance", "relevance", 1),
    DOWNLOADS("Downloads", "downloads", 6),
    FOLLOWS("Popular", "follows", 2),
    NEWEST("Newest", "newest", 11),
    UPDATED("Updated", "updated", 3)
}

class AssetProject(
    val platform: AssetPlatform,
    val type: AssetType,
    val id: String,
    val title: String,
    val description: String,
    val author: String,
    val iconUrl: String?,
    val downloads: Long,
    val loaders: List<String> = emptyList()
)

class AssetScreenshot(
    val url: String,
    val title: String?,
    val description: String?
)

class AssetProjectDetails(
    val project: AssetProject,
    val followers: Long?,
    val screenshots: List<AssetScreenshot>,
    val sourceUrl: String?,
    val issuesUrl: String?,
    val wikiUrl: String?,
    val discordUrl: String?
)

class AssetFile(
    val name: String,
    val versionNumber: String,
    val fileName: String,
    val url: String?,
    val sha1: String?,
    val sizeBytes: Long,
    val releaseType: String,
    val datePublished: String,
    val gameVersions: List<String>,
    val loaders: List<String>
) {
    constructor(
        name: String,
        fileName: String,
        url: String?,
        sha1: String?,
        gameVersions: List<String>,
        loaders: List<String>
    ) : this(name, name, fileName, url, sha1, 0L, "release", "", gameVersions, loaders)
}

class AssetPage(val projects: List<AssetProject>, val total: Int)

/** Minecraft version and loader of the selected instance, used as default search filters. */
class InstanceTarget(val mcVersion: String?, val loader: String?)

object PojavAssetBridge {
    private const val CURSEFORGE_GAME_ID = 432
    const val PAGE_SIZE = 30
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

    /** Names of files currently in the selected instance's folder for [type], lowercased. */
    suspend fun installedFingerprints(type: AssetType): Set<String> = withContext(Dispatchers.IO) {
        if (type == AssetType.MODPACK) {
            return@withContext runCatching {
                Instances.loadDisplay().list.mapNotNull { it.name?.lowercase() }.toSet()
            }.getOrDefault(emptySet())
        }
        val folder = type.folder ?: return@withContext emptySet()
        val dir = PojavContentBridge.directory(folder) ?: return@withContext emptySet()
        dir.listFiles()?.map {
            it.name.lowercase().removeSuffix(".disabled").removeSuffix(".jar").removeSuffix(".zip")
        }?.toSet() ?: emptySet()
    }

    fun isProjectInstalled(project: AssetProject, fingerprints: Set<String>): Boolean {
        if (fingerprints.isEmpty()) return false
        val slug = project.title.lowercase().replace(" ", "")
        val rawTitle = project.title.lowercase()
        return fingerprints.any { fp ->
            fp == rawTitle ||
                (slug.length >= 4 && fp.replace("-", "").replace("_", "").replace(" ", "").contains(slug))
        }
    }

    suspend fun search(
        context: Context,
        platform: AssetPlatform,
        type: AssetType,
        query: String,
        mcVersion: String?,
        loader: String?,
        offset: Int,
        category: String? = null,
        sort: AssetSort = AssetSort.RELEVANCE
    ): AssetPage? = withContext(Dispatchers.IO) {
        runCatching {
            when (platform) {
                AssetPlatform.MODRINTH -> searchModrinth(type, query, mcVersion, loader, category, sort, offset)
                AssetPlatform.CURSEFORGE -> searchCurseForge(context, type, query, mcVersion, loader, sort, offset)
            }
        }.getOrNull()
    }

    suspend fun details(context: Context, project: AssetProject): AssetProjectDetails = withContext(Dispatchers.IO) {
        runCatching {
            when (project.platform) {
                AssetPlatform.MODRINTH -> modrinthDetails(project)
                AssetPlatform.CURSEFORGE -> curseForgeDetails(context, project)
            }
        }.getOrNull() ?: AssetProjectDetails(project, null, emptyList(), null, null, null, null)
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
     * Downloads [file] into the [type] folder of the selected instance with SHA-1 verification,
     * or installs it as a new modpack instance when [type] is [AssetType.MODPACK].
     */
    suspend fun install(context: Context, project: AssetProject, file: AssetFile): Boolean = withContext(Dispatchers.IO) {
        if (project.type == AssetType.MODPACK) {
            val url = file.url ?: return@withContext false
            val mcVer = file.gameVersions.firstOrNull() ?: "1.20.1"
            val apiSource = if (project.platform == AssetPlatform.MODRINTH) {
                Constants.SOURCE_MODRINTH
            } else {
                Constants.SOURCE_CURSEFORGE
            }
            val modItem = ModItem(apiSource, true, project.id, project.title, project.description, project.iconUrl)
            val detail = ModDetail(
                modItem,
                arrayOf(file.name.ifBlank { file.versionNumber }),
                arrayOf(mcVer),
                arrayOf(url),
                arrayOf(file.sha1)
            )
            val api = if (project.platform == AssetPlatform.MODRINTH) {
                ModrinthApi()
            } else {
                val key = curseForgeKey(context) ?: return@withContext false
                CurseforgeApi(key)
            }
            api.handleModpackInstallation(context.applicationContext, detail, 0)
            return@withContext true
        }
        install(project.type, file)
    }

    suspend fun install(type: AssetType, file: AssetFile): Boolean = withContext(Dispatchers.IO) {
        val url = file.url ?: return@withContext false
        val folder = type.folder ?: return@withContext false
        val dir = PojavContentBridge.directory(folder) ?: return@withContext false
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

    // ---- Favorites (persisted in AerixPreferences.favoritesJson) -----------------------------

    fun favorites(): List<AssetProject> = runCatching {
        val array = JsonParser.parseString(AerixPreferences.favoritesJson).asJsonArray
        array.mapNotNull { elem ->
            val obj = elem.asJsonObject
            val platform = runCatching { AssetPlatform.valueOf(obj.string("platform") ?: "") }.getOrNull() ?: return@mapNotNull null
            val type = runCatching { AssetType.valueOf(obj.string("type") ?: "MOD") }.getOrDefault(AssetType.MOD)
            AssetProject(
                platform = platform,
                type = type,
                id = obj.string("id") ?: return@mapNotNull null,
                title = obj.string("title") ?: "",
                description = obj.string("description") ?: "",
                author = obj.string("author") ?: "",
                iconUrl = obj.string("iconUrl"),
                downloads = obj.long("downloads"),
                loaders = obj.getAsJsonArray("loaders").strings()
            )
        }
    }.getOrDefault(emptyList())

    fun isFavorite(project: AssetProject): Boolean =
        favorites().any { it.platform == project.platform && it.id == project.id }

    fun toggleFavorite(project: AssetProject) {
        val current = favorites().toMutableList()
        val index = current.indexOfFirst { it.platform == project.platform && it.id == project.id }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(0, project)
        }
        val array = JsonArray()
        current.forEach { item ->
            val obj = JsonObject()
            obj.addProperty("platform", item.platform.name)
            obj.addProperty("type", item.type.name)
            obj.addProperty("id", item.id)
            obj.addProperty("title", item.title)
            obj.addProperty("description", item.description)
            obj.addProperty("author", item.author)
            if (item.iconUrl != null) obj.addProperty("iconUrl", item.iconUrl)
            obj.addProperty("downloads", item.downloads)
            val loadersArr = JsonArray()
            item.loaders.forEach { loadersArr.add(it) }
            obj.add("loaders", loadersArr)
            array.add(obj)
        }
        AerixPreferences.saveFavoritesJson(array.toString())
    }

    // ---- Modrinth ----------------------------------------------------------------------------

    private val knownLoaders = setOf("fabric", "forge", "neoforge", "quilt")

    private fun searchModrinth(
        type: AssetType,
        query: String,
        mcVersion: String?,
        loader: String?,
        category: String?,
        sort: AssetSort,
        offset: Int
    ): AssetPage? {
        val projectType = type.modrinthType ?: return AssetPage(emptyList(), 0)
        val facets = StringBuilder("[[\"project_type:$projectType\"]")
        if (!mcVersion.isNullOrBlank()) facets.append(",[\"versions:$mcVersion\"]")
        if (type.supportsLoaderFilter && !loader.isNullOrBlank()) facets.append(",[\"categories:${loader.lowercase()}\"]")
        if (!category.isNullOrBlank()) facets.append(",[\"categories:$category\"]")
        facets.append("]")
        val params = HashMap<String, Any>()
        params["facets"] = facets.toString()
        params["query"] = query
        params["limit"] = PAGE_SIZE
        params["offset"] = offset
        params["index"] = sort.modrinthIndex
        val response = modrinth.get("search", params, JsonObject::class.java) ?: return null
        val hits = response.getAsJsonArray("hits") ?: return null
        val projects = hits.mapNotNull { element ->
            val hit = element.asJsonObject
            val cats = hit.getAsJsonArray("display_categories").strings()
                .ifEmpty { hit.getAsJsonArray("categories").strings() }
            val loaders = cats.filter { it.lowercase() in knownLoaders }.map { displayLoader(it) }.distinct()
            AssetProject(
                platform = AssetPlatform.MODRINTH,
                type = type,
                id = hit.string("project_id") ?: return@mapNotNull null,
                title = hit.string("title") ?: "",
                description = hit.string("description") ?: "",
                author = hit.string("author") ?: "",
                iconUrl = hit.string("icon_url")?.takeIf { it.isNotBlank() },
                downloads = hit.long("downloads"),
                loaders = loaders
            )
        }
        return AssetPage(projects, response.get("total_hits")?.asInt ?: projects.size)
    }

    private fun modrinthDetails(project: AssetProject): AssetProjectDetails? {
        val obj = modrinth.get("project/${project.id}", JsonObject::class.java) ?: return null
        val gallery = obj.getAsJsonArray("gallery")?.mapNotNull { elem ->
            val g = elem.asJsonObject
            val url = g.string("url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AssetScreenshot(url, g.string("title"), g.string("description"))
        } ?: emptyList()
        val loaders = obj.getAsJsonArray("loaders").strings().map { displayLoader(it) }.distinct()
        val updated = AssetProject(
            platform = project.platform,
            type = project.type,
            id = project.id,
            title = obj.string("title") ?: project.title,
            description = obj.string("description") ?: project.description,
            author = project.author,
            iconUrl = obj.string("icon_url")?.takeIf { it.isNotBlank() } ?: project.iconUrl,
            downloads = obj.long("downloads").takeIf { it > 0 } ?: project.downloads,
            loaders = loaders.ifEmpty { project.loaders }
        )
        return AssetProjectDetails(
            project = updated,
            followers = obj.long("followers"),
            screenshots = gallery,
            sourceUrl = obj.string("source_url"),
            issuesUrl = obj.string("issues_url"),
            wikiUrl = obj.string("wiki_url"),
            discordUrl = obj.string("discord_url")
        )
    }

    private fun modrinthFiles(projectId: String): List<AssetFile>? {
        val versions = modrinth.get("project/$projectId/version", JsonArray::class.java) ?: return null
        return versions.mapNotNull { element ->
            val version = element.asJsonObject
            val files = version.getAsJsonArray("files") ?: return@mapNotNull null
            if (files.size() == 0) return@mapNotNull null
            val primary = files.firstOrNull { it.asJsonObject.get("primary")?.asBoolean == true }?.asJsonObject
                ?: files[0].asJsonObject
            val verNum = version.string("version_number") ?: ""
            AssetFile(
                name = version.string("name") ?: verNum,
                versionNumber = verNum,
                fileName = primary.string("filename") ?: return@mapNotNull null,
                url = primary.string("url"),
                sha1 = primary.getAsJsonObject("hashes")?.string("sha1"),
                sizeBytes = primary.long("size"),
                releaseType = version.string("version_type") ?: "release",
                datePublished = (version.string("date_published") ?: "").take(10),
                gameVersions = version.getAsJsonArray("game_versions").strings(),
                loaders = version.getAsJsonArray("loaders").strings()
            )
        }
    }

    // ---- CurseForge --------------------------------------------------------------------------

    private fun curseForge(context: Context): ApiHandler? =
        curseForgeKey(context)?.let { ApiHandler("https://api.curseforge.com/v1", it) }

    private fun searchCurseForge(
        context: Context,
        type: AssetType,
        query: String,
        mcVersion: String?,
        loader: String?,
        sort: AssetSort,
        offset: Int
    ): AssetPage? {
        val api = curseForge(context) ?: return null
        val params = HashMap<String, Any>()
        params["gameId"] = CURSEFORGE_GAME_ID
        params["classId"] = type.curseForgeClassId
        params["searchFilter"] = query
        params["sortField"] = sort.curseForgeField
        params["sortOrder"] = "desc"
        params["pageSize"] = PAGE_SIZE
        params["index"] = offset
        if (!mcVersion.isNullOrBlank()) params["gameVersion"] = mcVersion
        if (type.supportsLoaderFilter) curseForgeLoaderType(loader)?.let { params["modLoaderType"] = it }
        val response = api.get("mods/search", params, JsonObject::class.java) ?: return null
        val data = response.getAsJsonArray("data") ?: return null
        val projects = data.mapNotNull { element ->
            val mod = element.asJsonObject
            val loaders = mod.getAsJsonArray("latestFilesIndexes")
                ?.mapNotNull { idx ->
                    when (idx.asJsonObject.get("modLoader")?.takeIf { !it.isJsonNull }?.asInt) {
                        1 -> "Forge"
                        4 -> "Fabric"
                        5 -> "Quilt"
                        6 -> "NeoForge"
                        else -> null
                    }
                }?.distinct() ?: emptyList()
            AssetProject(
                platform = AssetPlatform.CURSEFORGE,
                type = type,
                id = mod.get("id")?.asString ?: return@mapNotNull null,
                title = mod.string("name") ?: "",
                description = mod.string("summary") ?: "",
                author = mod.getAsJsonArray("authors")?.firstOrNull()?.asJsonObject?.string("name") ?: "",
                iconUrl = mod.getAsJsonObject("logo")?.string("thumbnailUrl"),
                downloads = mod.long("downloadCount"),
                loaders = loaders
            )
        }
        val total = response.getAsJsonObject("pagination")?.get("totalCount")?.asInt ?: projects.size
        return AssetPage(projects, minOf(total, 10000 - PAGE_SIZE))
    }

    private fun curseForgeDetails(context: Context, project: AssetProject): AssetProjectDetails? {
        val api = curseForge(context) ?: return null
        val response = api.get("mods/${project.id}", JsonObject::class.java) ?: return null
        val mod = response.getAsJsonObject("data") ?: return null
        val screenshots = mod.getAsJsonArray("screenshots")?.mapNotNull { elem ->
            val s = elem.asJsonObject
            val url = s.string("url")?.takeIf { it.isNotBlank() }
                ?: s.string("thumbnailUrl")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            AssetScreenshot(url, s.string("title"), s.string("description"))
        } ?: emptyList()
        val links = mod.getAsJsonObject("links")
        return AssetProjectDetails(
            project = project,
            followers = null,
            screenshots = screenshots,
            sourceUrl = links?.string("sourceUrl"),
            issuesUrl = links?.string("issuesUrl"),
            wikiUrl = links?.string("wikiUrl"),
            discordUrl = null
        )
    }

    private fun curseForgeLoaderType(loader: String?): Int? = when (loader?.lowercase()) {
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
                val relType = when (file.get("releaseType")?.takeIf { !it.isJsonNull }?.asInt) {
                    2 -> "beta"
                    3 -> "alpha"
                    else -> "release"
                }
                val fileName = file.string("fileName") ?: return@forEach
                result.add(
                    AssetFile(
                        name = file.string("displayName") ?: fileName,
                        versionNumber = fileName,
                        fileName = fileName,
                        url = file.string("downloadUrl"),
                        sha1 = file.getAsJsonArray("hashes")
                            ?.firstOrNull { it.asJsonObject.get("algo")?.asInt == 1 }
                            ?.asJsonObject?.string("value"),
                        sizeBytes = file.long("fileLength"),
                        releaseType = relType,
                        datePublished = (file.string("fileDate") ?: "").take(10),
                        gameVersions = versions.filter { mcVersionPattern.matches(it) },
                        loaders = versions.filter { !mcVersionPattern.matches(it) && it.lowercase() in knownLoaders }
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

    private fun displayLoader(raw: String): String = when (raw.lowercase()) {
        "fabric" -> "Fabric"
        "neoforge" -> "NeoForge"
        "forge" -> "Forge"
        "quilt" -> "Quilt"
        else -> raw
    }

    // ---- JSON helpers ------------------------------------------------------------------------

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
