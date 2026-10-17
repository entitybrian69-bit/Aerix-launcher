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

    private const val MODRINTH_OFFICIAL_API = "https://api.modrinth.com/v2"
    private const val MODRINTH_MIRROR_API = "https://mod.mcimirror.top/modrinth/v2"
    private const val CURSEFORGE_OFFICIAL_API = "https://api.curseforge.com/v1"
    private const val CURSEFORGE_MIRROR_API = "https://mod.mcimirror.top/curseforge/v1"

    private val modrinthOfficial = ApiHandler(MODRINTH_OFFICIAL_API)
    private val modrinthMirror = ApiHandler(MODRINTH_MIRROR_API)
    private val curseForgeMirror = ApiHandler(CURSEFORGE_MIRROR_API)

    /** User key (Aerix "curseForgeApiKey") first, then the key baked into the build. */
    fun curseForgeKey(context: Context): String? {
        val user = runCatching { AerixPreferences.curseForgeApiKey.trim() }.getOrDefault("")
        if (user.isNotEmpty() && user != "DUMMY") return user
        val built = runCatching { context.getString(R.string.curseforge_api_key).trim() }.getOrDefault("")
        return built.takeIf { it.isNotEmpty() && it != "DUMMY" }
    }

    fun platforms(@Suppress("UNUSED_PARAMETER") context: Context, type: AssetType): List<AssetPlatform> = buildList {
        if (type.modrinthType != null) add(AssetPlatform.MODRINTH)
        add(AssetPlatform.CURSEFORGE)
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
        categories: List<String> = emptyList(),
        sort: AssetSort = AssetSort.RELEVANCE
    ): AssetPage? = withContext(Dispatchers.IO) {
        runCatching {
            when (platform) {
                AssetPlatform.MODRINTH -> searchModrinth(type, query, mcVersion, loader, categories, sort, offset)
                AssetPlatform.CURSEFORGE -> searchCurseForge(context, type, query, mcVersion, loader, categories, sort, offset)
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

    @Volatile private var gameVersionCatalog: List<AssetGameVersion>? = null

    /** Complete provider catalog; no hand-picked list or latest-N truncation. */
    suspend fun minecraftVersions(): List<AssetGameVersion> = withContext(Dispatchers.IO) {
        gameVersionCatalog?.let { return@withContext it }
        val tags = modrinthGet("tag/game_version", null, JsonArray::class.java)
            ?: throw java.io.IOException("The Minecraft version catalog could not be loaded. Retry or enter an exact version.")
        val versions = tags.mapNotNull { value ->
            if (!value.isJsonObject) return@mapNotNull null
            val tag = value.asJsonObject
            val id = tag.string("version") ?: return@mapNotNull null
            AssetGameVersion(id, MinecraftVersionFilters.classify(id, tag.string("version_type")))
        }.distinctBy { it.id }
        check(versions.isNotEmpty()) { "The provider returned an empty Minecraft version catalog" }
        gameVersionCatalog = versions
        versions
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
                createCurseForgeModpackApi(context)
            }
            api.handleModpackInstallation(context.applicationContext, detail, 0)
            return@withContext true
        }
        install(project.type, file)
    }

    private fun createCurseForgeModpackApi(context: Context): CurseforgeApi {
        val key = curseForgeKey(context)
        if (key != null) return CurseforgeApi(key)
        val cfApi = CurseforgeApi("DUMMY")
        runCatching {
            val field = CurseforgeApi::class.java.getDeclaredField("mApiHandler")
            field.isAccessible = true
            field.set(cfApi, curseForgeMirror)
        }
        return cfApi
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
                loaders = obj.arr("loaders").strings()
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

    private fun <T> modrinthGet(endpoint: String, params: HashMap<String, Any>?, tClass: Class<T>): T? {
        val primary = if (params != null) {
            runCatching { modrinthOfficial.get(endpoint, params, tClass) }.getOrNull()
        } else {
            runCatching { modrinthOfficial.get(endpoint, tClass) }.getOrNull()
        }
        if (primary != null) return primary
        return if (params != null) {
            runCatching { modrinthMirror.get(endpoint, params, tClass) }.getOrNull()
        } else {
            runCatching { modrinthMirror.get(endpoint, tClass) }.getOrNull()
        }
    }

    private fun searchModrinth(
        type: AssetType,
        query: String,
        mcVersion: String?,
        loader: String?,
        categories: List<String>,
        sort: AssetSort,
        offset: Int
    ): AssetPage? {
        val projectType = type.modrinthType ?: return AssetPage(emptyList(), 0)
        val facets = StringBuilder("[[\"project_type:$projectType\"]")
        if (!mcVersion.isNullOrBlank()) facets.append(",[\"versions:$mcVersion\"]")
        if (type.supportsLoaderFilter && !loader.isNullOrBlank()) facets.append(",[\"categories:${loader.lowercase()}\"]")
        val categoryFacets = AssetCategoryQuery.modrinthFacet(categories)
        if (categoryFacets.size() > 0) facets.append(",").append(categoryFacets)
        facets.append("]")
        val params = HashMap<String, Any>()
        params["facets"] = facets.toString()
        params["query"] = query
        params["limit"] = PAGE_SIZE
        params["offset"] = offset
        params["index"] = sort.modrinthIndex
        val response = modrinthGet("search", params, JsonObject::class.java) ?: return null
        val hits = response.arr("hits") ?: return null
        val projects = hits.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val hit = element.asJsonObject
            val cats = hit.arr("display_categories").strings()
                .ifEmpty { hit.arr("categories").strings() }
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
        return AssetPage(projects, response.int("total_hits").takeIf { it > 0 } ?: projects.size)
    }

    private fun modrinthDetails(project: AssetProject): AssetProjectDetails? {
        val obj = modrinthGet("project/${project.id}", null, JsonObject::class.java) ?: return null
        val gallery = obj.arr("gallery")?.mapNotNull { elem ->
            if (!elem.isJsonObject) return@mapNotNull null
            val g = elem.asJsonObject
            val url = g.string("url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AssetScreenshot(url, g.string("title"), g.string("description"))
        } ?: emptyList()
        val loaders = obj.arr("loaders").strings().map { displayLoader(it) }.distinct()
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
        val versions = modrinthGet("project/$projectId/version", null, JsonArray::class.java) ?: return null
        return versions.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val version = element.asJsonObject
            val files = version.arr("files") ?: return@mapNotNull null
            if (files.size() == 0) return@mapNotNull null
            val primary = files.firstOrNull { it.isJsonObject && it.asJsonObject.get("primary")?.takeIf { !it.isJsonNull }?.asBoolean == true }?.asJsonObject
                ?: files[0].takeIf { it.isJsonObject }?.asJsonObject
                ?: return@mapNotNull null
            val verNum = version.string("version_number") ?: ""
            AssetFile(
                name = version.string("name") ?: verNum,
                versionNumber = verNum,
                fileName = primary.string("filename") ?: return@mapNotNull null,
                url = primary.string("url"),
                sha1 = primary.obj("hashes")?.string("sha1"),
                sizeBytes = primary.long("size"),
                releaseType = version.string("version_type") ?: "release",
                datePublished = (version.string("date_published") ?: "").take(10),
                gameVersions = version.arr("game_versions").strings(),
                loaders = version.arr("loaders").strings()
            )
        }
    }

    // ---- CurseForge --------------------------------------------------------------------------

    /**
     * Queries CurseForge via the user/build API key when present, falling back to Mirai's MCIM
     * CurseForge mirror (`https://mod.mcimirror.top/curseforge/v1`) when no key is set or when the
     * primary request fails.
     */
    private fun <T> curseForgeGet(
        context: Context,
        endpoint: String,
        params: HashMap<String, Any>?,
        tClass: Class<T>
    ): T? {
        val key = curseForgeKey(context)
        if (key != null) {
            val official = ApiHandler(CURSEFORGE_OFFICIAL_API, key)
            val res = if (params != null) {
                runCatching { official.get(endpoint, params, tClass) }.getOrNull()
            } else {
                runCatching { official.get(endpoint, tClass) }.getOrNull()
            }
            if (res != null) return res
        }
        return if (params != null) {
            runCatching { curseForgeMirror.get(endpoint, params, tClass) }.getOrNull()
        } else {
            runCatching { curseForgeMirror.get(endpoint, tClass) }.getOrNull()
        }
    }

    private fun searchCurseForge(
        context: Context,
        type: AssetType,
        query: String,
        mcVersion: String?,
        loader: String?,
        categories: List<String>,
        sort: AssetSort,
        offset: Int
    ): AssetPage? {
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
        AssetCategoryQuery.curseForgeIds(categories.mapNotNull { curseForgeCategoryId(type, it) })
            ?.let { params["categoryIds"] = it }
        val response = curseForgeGet(context, "mods/search", params, JsonObject::class.java) ?: return null
        val data = response.arr("data") ?: return null
        val projects = data.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val mod = element.asJsonObject
            val loaders = mod.arr("latestFilesIndexes")
                ?.mapNotNull { idx ->
                    if (!idx.isJsonObject) return@mapNotNull null
                    when (idx.asJsonObject.int("modLoader")) {
                        1 -> "Forge"
                        4 -> "Fabric"
                        5 -> "Quilt"
                        6 -> "NeoForge"
                        else -> null
                    }
                }?.distinct() ?: emptyList()
            val modId = mod.get("id")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
            val firstAuthor = mod.arr("authors")
                ?.firstOrNull { it.isJsonObject }
                ?.asJsonObject
                ?.string("name") ?: ""
            val logoObj = mod.obj("logo")
            val iconUrl = logoObj?.string("thumbnailUrl")?.takeIf { it.isNotBlank() }
                ?: logoObj?.string("url")?.takeIf { it.isNotBlank() }
            AssetProject(
                platform = AssetPlatform.CURSEFORGE,
                type = type,
                id = modId,
                title = mod.string("name") ?: "",
                description = mod.string("summary") ?: "",
                author = firstAuthor,
                iconUrl = iconUrl,
                downloads = mod.long("downloadCount"),
                loaders = loaders
            )
        }
        val total = response.obj("pagination")?.int("totalCount")?.takeIf { it > 0 } ?: projects.size
        return AssetPage(projects, minOf(total, 10000 - PAGE_SIZE))
    }

    private fun curseForgeDetails(context: Context, project: AssetProject): AssetProjectDetails? {
        val response = curseForgeGet(context, "mods/${project.id}", null, JsonObject::class.java) ?: return null
        val mod = response.obj("data") ?: return null
        val screenshots = mod.arr("screenshots")?.mapNotNull { elem ->
            if (!elem.isJsonObject) return@mapNotNull null
            val s = elem.asJsonObject
            val url = s.string("url")?.takeIf { it.isNotBlank() }
                ?: s.string("thumbnailUrl")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            AssetScreenshot(url, s.string("title"), s.string("description"))
        } ?: emptyList()
        val links = mod.obj("links")
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

    /** Maps Aerix category slugs to CurseForge categoryId integers (from Mirai _CurseForgeCategories.kt). */
    private fun curseForgeCategoryId(type: AssetType, slug: String?): Int? {
        if (slug.isNullOrBlank()) return null
        return when (type) {
            AssetType.MODPACK -> when (slug) {
                "adventure" -> 4475
                "optimization", "lightweight" -> 4481
                "technology" -> 4472
                "magic" -> 4473
                "quests" -> 4478
                "kitchen-sink" -> 4482
                "multiplayer" -> 4483
                else -> null
            }
            AssetType.MOD -> when (slug) {
                "optimization" -> 6814
                "utility" -> 5191
                "library" -> 421
                "worldgen" -> 406
                "technology" -> 412
                "magic" -> 419
                "adventure" -> 422
                "storage" -> 420
                "equipment" -> 434
                "mobs" -> 411
                "food" -> 436
                "decoration" -> 408
                else -> null
            }
            AssetType.RESOURCE_PACK -> when (slug) {
                "16x" -> 393
                "32x" -> 394
                "64x" -> 395
                "realistic" -> 397
                "simplistic" -> 401
                "tweaks" -> 403
                "vanilla-like" -> 400
                else -> null
            }
            AssetType.SHADER_PACK -> when (slug) {
                "realistic" -> 6554
                "vanilla-like" -> 6555
                "fantasy" -> 6553
                else -> null
            }
            AssetType.WORLD -> when (slug) {
                "adventure" -> 248
                "survival" -> 249
                "creation" -> 250
                "parkour" -> 252
                "puzzle" -> 251
                else -> null
            }
        }
    }

    /**
     * Resolves a CurseForge file download URL, falling back to CurseForge's edge CDN
     * (`https://edge.forgecdn.net/files/{id/1000}/{id%1000}/{fileName}`) when 3rd-party distribution
     * leaves `downloadUrl` null (same as Mirai CurseForgeFile.fixedFileUrl).
     */
    private fun curseForgeDownloadUrl(file: JsonObject, fileName: String): String? {
        val direct = file.string("downloadUrl")
        if (!direct.isNullOrBlank()) return direct
        val id = file.int("id")
        if (id <= 0 || fileName.isBlank()) return null
        return "https://edge.forgecdn.net/files/${id / 1000}/${id % 1000}/$fileName"
    }

    private fun curseForgeFiles(context: Context, modId: String): List<AssetFile>? {
        val result = ArrayList<AssetFile>()
        var index = 0
        val seenPages = HashSet<String>()
        while (true) {
            val params = HashMap<String, Any>()
            params["index"] = index
            params["pageSize"] = 50
            val response = curseForgeGet(context, "mods/$modId/files", params, JsonObject::class.java)
                ?: throw java.io.IOException("Could not load all content versions; retry the download list")
            val data = response.arr("data") ?: throw java.io.IOException("Provider omitted the file list")
            val fingerprint = data.joinToString { it.toString() }
            check(seenPages.add(fingerprint)) { "Provider repeated a file page; retry instead of showing an incomplete version list" }
            data.forEach { element ->
                if (!element.isJsonObject) return@forEach
                val file = element.asJsonObject
                val versions = file.arr("gameVersions").strings()
                val relType = when (file.int("releaseType")) {
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
                        url = curseForgeDownloadUrl(file, fileName),
                        sha1 = file.arr("hashes")
                            ?.firstOrNull { it.isJsonObject && it.asJsonObject.int("algo") == 1 }
                            ?.asJsonObject?.string("value"),
                        sizeBytes = file.long("fileLength"),
                        releaseType = relType,
                        datePublished = (file.string("fileDate") ?: "").take(10),
                        gameVersions = versions.filter { it.lowercase() !in knownLoaders && !it.startsWith("Java ", true) && it.lowercase() !in setOf("client", "server") },
                        loaders = versions.filter { it.lowercase() in knownLoaders }
                            .map { it.lowercase() }
                    )
                )
            }
            index += data.size()
            val total = response.obj("pagination")?.int("totalCount") ?: index
            if (data.size() == 0 || index >= total) break
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

    private fun JsonObject.obj(key: String): JsonObject? {
        val element: JsonElement = get(key) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    private fun JsonObject.arr(key: String): JsonArray? {
        val element: JsonElement = get(key) ?: return null
        return if (element.isJsonArray) element.asJsonArray else null
    }

    private fun JsonObject.string(key: String): String? {
        val element: JsonElement = get(key) ?: return null
        return if (element.isJsonPrimitive) runCatching { element.asString }.getOrNull() else null
    }

    private fun JsonObject.int(key: String): Int {
        val element = get(key) ?: return 0
        return if (element.isJsonPrimitive) runCatching { element.asInt }.getOrDefault(0) else 0
    }

    private fun JsonObject.long(key: String): Long {
        val element = get(key) ?: return 0L
        return if (element.isJsonPrimitive) runCatching { element.asLong }.getOrDefault(0L) else 0L
    }

    private fun JsonArray?.strings(): List<String> =
        this?.mapNotNull { if (it == null || it.isJsonNull || !it.isJsonPrimitive) null else runCatching { it.asString }.getOrNull() } ?: emptyList()
}
