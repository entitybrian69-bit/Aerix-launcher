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
import net.kdt.pojavlaunch.instances.Instance
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler
import net.kdt.pojavlaunch.modloaders.modpacks.api.CurseforgeApi
import net.kdt.pojavlaunch.modloaders.modpacks.api.CurseForgeApiHandler
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
    val discordUrl: String?,
    /** Modrinth project description, which can carry [[install-rules]] bundles. Empty elsewhere. */
    val body: String = ""
)

/** Something the platform says a file needs besides itself. */
class AssetDependency(
    val projectId: String,
    val platform: AssetPlatform,
    /** Required dependencies are ticked for the user; optional ones are offered unticked. */
    val required: Boolean
)

/** A dependency with the file that fits the version being installed, ready to go. */
class ResolvedDependency(
    val project: AssetProject,
    val file: AssetFile?,
    val required: Boolean
) {
    val installable: Boolean get() = file?.url != null
}

/** An instance a file can be installed into, with the version it is built on. */
class AssetTarget(
    val instanceId: String,
    val name: String,
    val versionId: String,
    val mcVersion: String?,
    val loader: String?,
    /** The instance the launcher is sitting on: the obvious default. */
    val detected: Boolean,
    val gameDirectory: File?
) {
    val subtitle: String
        get() = listOfNotNull(
            mcVersion?.takeIf { it.isNotBlank() } ?: versionId.takeIf { it.isNotBlank() },
            loader?.replaceFirstChar { it.uppercase() }
        ).joinToString(" · ")
}

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
    val loaders: List<String>,
    /** What this file needs, as the platform reports it. Empty when it needs nothing. */
    val dependencies: List<AssetDependency> = emptyList()
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

/**
 * One choice of a filter: the id a search is asked for, or the id of a category; [label] is what
 * the player reads.
 */
class AssetFacet(val id: String, val label: String)

/**
 * The filter lists of one source: every Minecraft version it knows and the categories of the
 * content type. Both come from the source itself - the platform decides what it offers - so a
 * category that only CurseForge has is only shown there, and vice versa.
 */
class AssetFacets(
    val versions: List<AssetFacet> = emptyList(),
    val categories: List<AssetFacet> = emptyList()
)

/** Minecraft version and loader of the selected instance, used as default search filters. */
class InstanceTarget(val mcVersion: String?, val loader: String?)

object PojavAssetBridge {
    private const val CURSEFORGE_GAME_ID = 432
    const val PAGE_SIZE = 30
    private val mcVersionPattern = Regex("""(\d+\.\d+(?:\.\d+)?)""")

    private const val MODRINTH_OFFICIAL_API = "https://api.modrinth.com/v2"
    private const val MODRINTH_MIRROR_API = "https://mod.mcimirror.top/modrinth/v2"

    private val modrinthOfficial = ApiHandler(MODRINTH_OFFICIAL_API)
    private val modrinthMirror = ApiHandler(MODRINTH_MIRROR_API)

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
        // The version of the game, not the version of its loader: see GameDownloadTarget.of.
        val target = PojavGameDownloadsBridge.runningTarget()
        InstanceTarget(target.mcVersion, target.loader)
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
        sort: AssetSort = AssetSort.RELEVANCE,
        categories: List<String> = emptyList()
    ): AssetPage? = withContext(Dispatchers.IO) {
        runCatching {
            val chosenCategories = (categories + listOfNotNull(category)).filter { it.isNotBlank() }.distinct()
            when (platform) {
                AssetPlatform.MODRINTH -> searchModrinth(type, query, mcVersion, loader, chosenCategories, sort, offset)
                AssetPlatform.CURSEFORGE -> searchCurseForge(context, type, query, mcVersion, loader, chosenCategories, sort, offset)
            }
        }.getOrNull()
    }

    /**
     * The Minecraft versions and the categories a source offers for [type].
     *
     * Modrinth answers both from its tag endpoints. CurseForge answers its categories from the class
     * the content type belongs to, by the ids CurseForge itself uses; its game version list is only
     * reachable with an API key, so when that stays silent the full Modrinth tag list stands in -
     * the same Minecraft versions, all of them - and the version table the launcher already holds
     * after that. A filter may end up with the short list the type ships with only when the device
     * has neither a network nor a downloaded version table, never because one endpoint failed.
     */
    suspend fun facets(context: Context, platform: AssetPlatform, type: AssetType): AssetFacets =
        withContext(Dispatchers.IO) {
            val fallbackCategories = type.categories.map { (id, label) -> AssetFacet(id, label) }
            runCatching {
                val versions = when (platform) {
                    AssetPlatform.MODRINTH -> modrinthVersions()
                    AssetPlatform.CURSEFORGE -> curseForgeVersions(context).ifEmpty { modrinthVersions() }
                }.ifEmpty { localVersions() }
                val categories = when (platform) {
                    AssetPlatform.MODRINTH -> modrinthCategories(type)
                    AssetPlatform.CURSEFORGE -> curseForgeCategories(context, type.curseForgeClassId)
                }.ifEmpty { fallbackCategories }
                AssetFacets(versions, categories)
            }.getOrElse { AssetFacets(localVersions(), fallbackCategories) }
        }

    // ---- Version lists -----------------------------------------------------------------------

    /**
     * Every game version Modrinth knows, newest first. This is the whole history of the game -
     * releases, snapshots, pre-releases, betas and alphas - and it needs no key, which is why it is
     * the list a source that cannot answer its own falls back to.
     */
    private fun modrinthVersions(): List<AssetFacet> {
        val array = modrinthGet("tag/game_version", null, JsonArray::class.java) ?: return emptyList()
        val dated = array.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val version = element.asJsonObject
            val id = version.string("version") ?: return@mapNotNull null
            // ISO-8601 timestamps compare as text, so no date parsing is needed.
            (version.string("date") ?: "") to id
        }
        // Distinct ids: the dropdowns key their lazy rows by id, and a repeated key is a crash.
        return dated.sortedByDescending { it.first }.map { AssetFacet(it.second, it.second) }.distinctBy { it.id }
    }

    /** CurseForge's own game version list, empty when the key of the moment cannot reach it. */
    private fun curseForgeVersions(context: Context): List<AssetFacet> {
        val array = curseForgeGet(
            context, "minecraft/version",
            hashMapOf<String, Any>("gameId" to CURSEFORGE_GAME_ID), JsonArray::class.java
        ) ?: return emptyList()
        return array.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val version = element.asJsonObject.string("versionString") ?: return@mapNotNull null
            AssetFacet(version, version)
        }.distinctBy { it.id }
    }

    /**
     * The version table the launcher itself works from: what is installed plus the whole Mojang
     * manifest behind the version list on the create screen. Offline and complete, so a version
     * filter is never left with a handful of entries.
     */
    private fun localVersions(): List<AssetFacet> = runCatching {
        PojavCreateInstanceBridge.gameVersions().map { AssetFacet(it.id, it.id) }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    // ---- Modrinth facets ---------------------------------------------------------------------

    private fun modrinthCategories(type: AssetType): List<AssetFacet> {
        val projectType = type.modrinthType ?: return emptyList()
        val array = modrinthGet("tag/category", null, JsonArray::class.java) ?: return emptyList()
        val rows = array.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val category = element.asJsonObject
            // The endpoint answers for every kind of project at once; the project type is what puts
            // a category on a mod rather than on a shader pack.
            if (category.string("project_type") != projectType) return@mapNotNull null
            val slug = category.string("name") ?: return@mapNotNull null
            val header = category.string("header") ?: "categories"
            Triple(header, slug, categoryLabel(slug))
        }
        // The plain categories come first, the way the site orders its filter, then the rest, each
        // alphabetically.
        return rows.sortedWith(compareBy({ if (it.first == "categories") 0 else 1 }, { it.second }))
            .map { AssetFacet(it.second, it.third) }.distinctBy { it.id }
    }

    // ---- CurseForge facets -------------------------------------------------------------------

    /**
     * The categories CurseForge has for [classId]: the parents first, each followed by the
     * sub-categories that belong to it and labelled "Parent - Child". The website shows the parents
     * and reveals the sub-categories under them; a sub-category is a filter of its own on the site,
     * so the list carries them all. The ids travel unchanged - they are the ids the search wants.
     */
    private fun curseForgeCategories(context: Context, classId: Int): List<AssetFacet> {
        val array = curseForgeGet(
            context, "categories",
            hashMapOf<String, Any>("gameId" to CURSEFORGE_GAME_ID, "classId" to classId),
            JsonArray::class.java
        ) ?: return emptyList()
        data class Row(val id: String, val name: String, val parent: String?, val index: Int)
        val rows = array.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val category = element.asJsonObject
            val id = category.get("id")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
            if (id == classId.toString()) return@mapNotNull null
            if (category.get("isClass")?.takeIf { !it.isJsonNull }?.asBoolean == true) return@mapNotNull null
            val name = category.string("name") ?: return@mapNotNull null
            val parentId = category.get("parentCategoryId")?.takeIf { !it.isJsonNull }?.asString
            Row(id, name, parentId?.takeIf { it != id && it != classId.toString() }, category.int("displayIndex"))
        }
        val names = rows.associate { it.id to it.name }
        val order = compareBy<Row>({ it.index }, { it.name })
        val children = rows.filter { it.parent != null && it.parent in names }.groupBy { it.parent }
        val result = ArrayList<AssetFacet>(rows.size)
        rows.filter { it.parent == null || it.parent !in names }.sortedWith(order).forEach { parent ->
            result.add(AssetFacet(parent.id, parent.name))
            children[parent.id]?.sortedWith(order)?.forEach { child ->
                result.add(AssetFacet(child.id, "${parent.name} - ${child.name}"))
            }
        }
        return result.distinctBy { it.id }
    }

    /** "vanilla-like" reads as "Vanilla Like", "16x" stays "16x", "pbr" becomes "PBR". */
    private fun categoryLabel(slug: String): String =
        slug.split('-', '_').joinToString(" ") { word ->
            when (word.lowercase()) {
                "pbr" -> "PBR"
                "gui" -> "GUI"
                "hud" -> "HUD"
                "rpg" -> "RPG"
                "qol" -> "QoL"
                "api" -> "API"
                "mmo" -> "MMO"
                "pvp" -> "PvP"
                "pve" -> "PvE"
                "3d" -> "3D"
                else -> word.replaceFirstChar { it.uppercase() }
            }
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
                createCurseForgeModpackApi(context)
            }
            api.handleModpackInstallation(context.applicationContext, detail, 0)
            return@withContext true
        }
        install(project.type, file)
    }

    private fun createCurseForgeModpackApi(context: Context): CurseforgeApi =
        CurseforgeApi(curseForgeKey(context))

    /**
     * Every instance a file can be installed into, the one the launcher is sitting on first.
     * Each row carries the Minecraft version the instance actually runs, so the picker can say
     * which of them the file was built for.
     */
    suspend fun targets(): List<AssetTarget> = withContext(Dispatchers.IO) {
        runCatching {
            val selected = runCatching { Instances.loadSelectedInstance() }.getOrNull()
            val selectedRoot = runCatching { selected?.gameDirectory }.getOrNull()
            // DisplayInstance records are not Instance subclasses. Casting the lightweight
            // display list silently discarded every installed instance and disabled Install.
            contentInstallTargets(Instances.loadAllInstances(), selectedRoot)
        }.getOrDefault(emptyList())
    }

    /**
     * Every dependency of [file] that fits [mcVersion] and [loader]: what the platform records on
     * the file itself plus the project's Modrinth install rules, so the dialog can offer the whole
     * set as tick boxes instead of sending the user hunting for it.
     *
     * A dependency the target version has no build of is still returned, with no file, and the
     * dialog says so rather than silently installing a half-working set.
     */
    suspend fun planDependencies(
        context: Context,
        type: AssetType,
        file: AssetFile,
        mcVersion: String?,
        loader: String?,
        body: String? = null
    ): List<ResolvedDependency> {
        val fromFile = resolveDependencies(context, type, file.dependencies, mcVersion, loader)
        // Modrinth lets a project spell out "install these together" in its description body.
        val known = fromFile.map { it.project.id }.toMutableSet()
        val ruleIds = ModrinthInstallRules.requiredProjectIds(body)
            .filter { it.isNotBlank() && known.add(it) }
        if (ruleIds.isEmpty()) return fromFile
        val ruleDependencies = ruleIds.map { AssetDependency(it, AssetPlatform.MODRINTH, true) }
        return fromFile + resolveDependencies(context, type, ruleDependencies, mcVersion, loader)
    }

    private suspend fun resolveDependencies(
        context: Context,
        type: AssetType,
        dependencies: List<AssetDependency>,
        mcVersion: String?,
        loader: String?
    ): List<ResolvedDependency> = withContext(Dispatchers.IO) {
        dependencies.mapNotNull { dependency ->
            runCatching {
                when (dependency.platform) {
                    AssetPlatform.MODRINTH -> {
                        val meta = modrinthGet("project/${dependency.projectId}", null, JsonObject::class.java)
                        val project = AssetProject(
                            platform = AssetPlatform.MODRINTH,
                            type = type,
                            id = dependency.projectId,
                            title = meta?.string("title") ?: dependency.projectId,
                            description = meta?.string("description") ?: "",
                            author = "",
                            iconUrl = meta?.string("icon_url"),
                            downloads = meta?.long("downloads") ?: 0L
                        )
                        ResolvedDependency(
                            project,
                            pickFor(modrinthFiles(dependency.projectId), mcVersion, loader),
                            dependency.required
                        )
                    }
                    AssetPlatform.CURSEFORGE -> {
                        val meta = curseForgeGet(context, "mods/${dependency.projectId}", null, JsonObject::class.java)
                            ?.obj("data")
                        val project = AssetProject(
                            platform = AssetPlatform.CURSEFORGE,
                            type = type,
                            id = dependency.projectId,
                            title = meta?.string("name") ?: dependency.projectId,
                            description = meta?.string("summary") ?: "",
                            author = "",
                            iconUrl = meta?.obj("logo")?.string("thumbnailUrl"),
                            downloads = meta?.long("downloadCount") ?: 0L
                        )
                        ResolvedDependency(
                            project,
                            pickFor(curseForgeFiles(context, dependency.projectId), mcVersion, loader),
                            dependency.required
                        )
                    }
                }
            }.getOrNull()
        }
    }

    /**
     * The file of [files] that belongs on a [mcVersion] running [loader]: the newest release for
     * that version first, then any build for it, then anything the project has.
     */
    private fun pickFor(files: List<AssetFile>?, mcVersion: String?, loader: String?): AssetFile? {
        val all = files.orEmpty().filter { it.url != null }
        if (all.isEmpty()) return null
        val byVersion = if (mcVersion.isNullOrBlank()) all
        else all.filter { file -> file.gameVersions.any { it.equals(mcVersion, true) } }
        val pool = byVersion.ifEmpty { all }
        val byLoader = if (loader.isNullOrBlank()) pool
        else pool.filter { file -> file.loaders.any { it.equals(loader, true) } }
        val best = byLoader.ifEmpty { pool }
        return best.firstOrNull { it.releaseType == "release" } ?: best.first()
    }

    /**
     * One file, reporting how many bytes have arrived. [DownloadUtils.downloadFile] has no progress
     * hook, so the monitored variant stands in for it while keeping the SHA-1 check around it.
     */
    private fun download(url: String, target: File, onProgress: ((Long, Long) -> Unit)?) {
        if (onProgress == null) {
            DownloadUtils.downloadFile(url, target)
            return
        }
        DownloadUtils.downloadFileMonitored(url, target, null) { current, total ->
            onProgress(current.toLong(), total.toLong())
        }
    }

    /** Installs [file] into [target] instead of into whichever instance happens to be selected. */
    suspend fun installInto(
        context: Context,
        type: AssetType,
        file: AssetFile,
        target: AssetTarget,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean {
        val kind = type.folder ?: return false
        val root = target.gameDirectory ?: return false
        return install(type, file, File(root, kind.directoryName), onProgress)
    }

    suspend fun install(type: AssetType, file: AssetFile): Boolean = withContext(Dispatchers.IO) {
        val folder = type.folder ?: return@withContext false
        val dir = PojavContentBridge.directory(folder) ?: return@withContext false
        install(type, file, dir)
    }

    private suspend fun install(
        type: AssetType,
        file: AssetFile,
        dir: File,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val url = file.url ?: return@withContext false
        if (!dir.isDirectory && !dir.mkdirs()) return@withContext false
        val safeName = File(file.fileName).name.ifBlank { return@withContext false }
        runCatching {
            if (type == AssetType.WORLD) {
                val temp = File.createTempFile("aerix_world", ".zip", dir.parentFile)
                try {
                    temp.delete()
                    DownloadUtils.ensureSha1(temp, file.sha1) { download(url, temp, onProgress) }
                    PojavContentBridge.extractWorldFile(temp, dir, safeName.removeSuffix(".zip"))
                } finally {
                    temp.delete()
                }
            } else {
                val target = File(dir, safeName)
                DownloadUtils.ensureSha1(target, file.sha1) { download(url, target, onProgress) }
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
        if (categories.isNotEmpty()) facets.append(",").append(discoveryCategoryFacet(categories))
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
            discordUrl = obj.string("discord_url"),
            body = obj.string("body") ?: ""
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
                loaders = version.arr("loaders").strings(),
                dependencies = version.arr("dependencies")?.mapNotNull { dep ->
                    if (!dep.isJsonObject) return@mapNotNull null
                    val dependency = dep.asJsonObject
                    val id = dependency.string("project_id") ?: return@mapNotNull null
                    // "embedded" is already inside the file and "incompatible" is a warning, not
                    // something to install.
                    when (dependency.string("dependency_type")) {
                        "required" -> AssetDependency(id, AssetPlatform.MODRINTH, true)
                        "optional" -> AssetDependency(id, AssetPlatform.MODRINTH, false)
                        else -> null
                    }
                } ?: emptyList()
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
        val handler = CurseForgeApiHandler(curseForgeKey(context))
        return if (params == null) handler.get(endpoint, tClass) else handler.get(endpoint, params, tClass)
    }

    /** Resolve a curated link by its exact platform slug, never by title or first search result. */
    suspend fun resolveCurseForgeSlug(context: Context, type: AssetType, slug: String, projectId: String? = null): AssetProject? =
        withContext(Dispatchers.IO) {
            require(Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(slug))
            val params = hashMapOf<String, Any>("gameId" to CURSEFORGE_GAME_ID,
                "classId" to type.curseForgeClassId, "slug" to slug,
                "searchFilter" to slug.substringBefore('-'), "pageSize" to 50, "index" to 0)
            try {
                val response = if (projectId != null) {
                    require((projectId.toLongOrNull() ?: 0) > 0)
                    val metadata = curseForgeGet(context, "mods/$projectId", null, JsonObject::class.java)
                    metadata?.obj("data")?.let { mod -> JsonObject().apply {
                        add("data", com.google.gson.JsonArray().apply { add(mod) })
                    } }
                } else curseForgeGet(context, "mods/search", params, JsonObject::class.java)
                response?.let { CurseForgeProjectLink.resolve(it, type, slug) }
                    ?.takeIf { projectId == null || it.id == projectId }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
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
        val categoryIds = categories.mapNotNull { curseForgeCategoryId(type, it) }.distinct()
        if (categoryIds.size == 1) params["categoryId"] = categoryIds.single()
        else if (categoryIds.isNotEmpty()) params["categoryIds"] = categoryIds.joinToString(prefix = "[", postfix = "]")
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
        // The category filter of the UI carries the id CurseForge itself uses once the lists come
        // from its API, so it can be used as it is.
        slug.toIntOrNull()?.let { return it }
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
        while (true) {
            val params = HashMap<String, Any>()
            params["index"] = index
            params["pageSize"] = 50
            val response = curseForgeGet(context, "mods/$modId/files", params, JsonObject::class.java)
                ?: return if (result.isEmpty()) null else result
            val data = response.arr("data") ?: break
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
                        gameVersions = versions.filter { mcVersionPattern.matches(it) },
                        loaders = versions.filter { !mcVersionPattern.matches(it) && it.lowercase() in knownLoaders }
                            .map { it.lowercase() },
                        // relationType: 3 is a required dependency, 2 an optional one. 1 is an
                        // embedded library (already inside the jar), 4 a tool, 5 an incompatible mod.
                        dependencies = file.arr("dependencies")?.mapNotNull { dep ->
                            if (!dep.isJsonObject) return@mapNotNull null
                            val relation = dep.asJsonObject.int("relationType")
                            val id = dep.asJsonObject.get("modId")?.takeIf { !it.isJsonNull }?.asString
                                ?: return@mapNotNull null
                            when (relation) {
                                3 -> AssetDependency(id, AssetPlatform.CURSEFORGE, true)
                                2 -> AssetDependency(id, AssetPlatform.CURSEFORGE, false)
                                else -> null
                            }
                        } ?: emptyList()
                    )
                )
            }
            index += data.size()
            val total = response.obj("pagination")?.int("totalCount") ?: index
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
    /**
     * Modrinth projects can describe, in their description body, which other projects belong
     * alongside them:
     *
     *     [[install-rules]]
     *     title = "Install with these"
     *     [[install-rules.entries]]
     *     project-id = "P7dR8mSH"
     *     type = "required"
     *
     * Only entries the project marks as required are read, and only when the entry spells out a
     * project id, so a body that merely mentions a project can never add it. Version filters that
     * may sit inside an entry are not read: [pickFor] already picks the file that fits the
     * instance being installed into, and a dependency with no build for it is shown as such.
     */
    fun installRuleProjectIds(body: String?): List<String> = ModrinthInstallRules.requiredProjectIds(body)
}

/**
 * Reads the required projects out of a Modrinth project body's `[[install-rules]]` block. A tiny
 * line reader rather than a YAML parser: the block is flat and only three keys are read.
 *
 * Version filters a rule can carry in its own nested block are deliberately not read: [pickFor]
 * already picks the file that fits the instance being installed into, and a dependency with no
 * build for that instance is shown as missing instead of being dropped in silence.
 */
object ModrinthInstallRules {
    private const val BLOCK = "[[install-rules]]"
    private const val ENTRIES = "[[install-rules.entries"
    private const val ENTRY = "[[install-rules.entries]]"

    /** Characters a YAML key or value can be wrapped in. */
    private val QUOTES = charArrayOf('"', '\u0027')

    fun requiredProjectIds(body: String?): List<String> {
        val text = body.orEmpty()
        if (!text.contains(BLOCK)) return emptyList()
        val ids = ArrayList<String>()
        var inRules = false
        var inEntry = false
        // A nested block inside an entry, e.g. [[install-rules.entries.rules]]: keys in there are
        // the rule's own filters, not the entry's project.
        var nested = false
        var currentId: String? = null
        var currentType: String? = null

        fun flush() {
            // An entry with no type of its own counts as required, which is how Modrinth's own
            // install dialog reads it.
            val id = currentId
            if (inEntry && id != null && (currentType ?: "required") == "required") ids.add(id)
            currentId = null
            currentType = null
            nested = false
        }

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith(BLOCK)) {
                flush()
                inRules = true
                inEntry = false
            } else if (line.startsWith(ENTRIES)) {
                if (!inRules) continue
                if (line == ENTRY) {
                    // A new entry: the previous one ends here.
                    flush()
                    inEntry = true
                } else {
                    // A nested block of the entry being read.
                    nested = true
                }
            } else if (line.startsWith("[[")) {
                // Any other top level block ends the install rules.
                flush()
                inRules = false
                inEntry = false
            } else if (inRules && inEntry && !nested && line.contains('=')) {
                val key = line.substringBefore('=').trim().trim(*QUOTES).lowercase()
                val value = line.substringAfter('=').trim().trim(*QUOTES)
                if (key == "project-id" || key == "project_id") currentId = value
                if (key == "type") currentType = value.lowercase()
            }
        }
        flush()
        return ids.filter { it.isNotBlank() }.distinct()
    }
}

/** Resolve full instance records; one broken profile must not hide all healthy destinations. */
internal fun contentInstallTargets(
    instances: List<Instance>,
    selectedRoot: File?,
    describe: (Instance) -> GameDownloadTarget = { GameDownloadTarget.of(it) }
): List<AssetTarget> = instances.mapNotNull { instance ->
    runCatching {
        val game = describe(instance)
        AssetTarget(
            instanceId = instance.instanceId,
            name = instance.name?.takeIf { it.isNotBlank() } ?: instance.versionId.orEmpty(),
            versionId = instance.versionId.orEmpty(),
            mcVersion = game.mcVersion,
            loader = game.loader,
            detected = selectedRoot != null && game.gameDirectory == selectedRoot,
            gameDirectory = game.gameDirectory
        ).takeIf { it.gameDirectory != null }
    }.getOrNull()
}.sortedByDescending { it.detected }
