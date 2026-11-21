package net.kdt.aerix.desktop.core.content

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.core.json.JsonArray
import net.kdt.aerix.desktop.core.json.JsonObject
import net.kdt.aerix.desktop.core.json.JsonValue
import net.kdt.aerix.desktop.core.net.Http
import java.net.URLEncoder

/**
 * Discover: Modrinth v2 + CurseForge proxies.
 *
 * Same sources as the Android launcher (Modrinth v2 and CurseForge v1 with game id 432, mirroring
 * `AerixModrinth`/`AerixCurseForge`). This file is the transport+model layer; UI screens call
 * [search] and [versions] only.
 */
object Content {

    enum class Kind(val folder: String, val modrinthType: String, val cfClass: Int) {
        MODPACK("modpacks", "modpack", 4471),
        MOD("mods", "mod", 6),
        RESOURCE_PACK("resourcepacks", "resourcepack", 12),
        SHADER("shaderpacks", "shader", 6552),
        WORLD("saves", "datapack", 17);

        val label: String get() = when (this) {
            MODPACK -> "Modpacks"
            MOD -> "Mods"
            RESOURCE_PACK -> "Resource packs"
            SHADER -> "Shaders"
            WORLD -> "Worlds"
        }
    }

    enum class Source { MODRINTH, CURSEFORGE }

    class Project(
        val id: String,
        val slug: String,
        val title: String,
        val description: String,
        val author: String,
        val iconUrl: String?,
        val downloads: Long,
        val follows: Long,
        val categories: List<String>,
        val loaders: List<String>,
        val gameVersions: List<String>,
        val source: Source
    ) {
        var projectType: String = ""
    }

    class ProjectVersion(
        val id: String,
        val projectId: String,
        val name: String,
        val versionNumber: String,
        val gameVersions: List<String>,
        val loaders: List<String>,
        val downloadUrl: String?,
        val fileName: String?,
        val sha1: String?,
        val dependencies: List<Dependency> = emptyList()
    ) {
        class Dependency(val projectId: String, val versionId: String?, val kind: String)
    }

    private const val MODRINTH = "https://api.modrinth.com/v2"
    private const val CURSEFORGE = "https://api.curseforge.com/v1"
    private const val PAGE = 30

    fun search(
        query: String,
        kind: Kind,
        source: Source,
        gameVersion: String? = null,
        loader: String? = null,
        category: String? = null,
        offset: Int = 0,
        sortByRelevance: Boolean = true
    ): List<Project> = when (source) {
        Source.MODRINTH -> searchModrinth(query, kind, gameVersion, loader, category, offset, sortByRelevance)
        Source.CURSEFORGE -> searchCurseForge(query, kind, gameVersion, loader, category, offset, sortByRelevance)
    }

    private fun searchModrinth(
        query: String, kind: Kind, gameVersion: String?, loader: String?, category: String?,
        offset: Int, relevance: Boolean
    ): List<Project> {
        val facets = ArrayList<String>()
        facets.add("[\"project_type:${kind.modrinthType}\"]")
        if (!gameVersion.isNullOrBlank()) facets.add("[\"versions:${gameVersion.trim()}\"]")
        if (!loader.isNullOrBlank() && kind != Kind.WORLD) facets.add("[\"categories:${loader.trim().lowercase()}\"]")
        if (!category.isNullOrBlank()) facets.add("[\"categories:${category.trim().lowercase()}\"]")
        val index = if (query.isBlank() && relevance) "downloads" else if (relevance) "relevance" else "downloads"
        val params = buildString {
            append("query=").append(enc(query))
            append("&limit=$PAGE&offset=$offset")
            append("&index=$index")
            append("&facets=").append(enc(JsonArray().also { facets.forEach { f -> it.add(f) } }.toString()))
        }
        val doc = Json.parseArray(Http.get("$MODRINTH/search?$params", cacheSeconds = 120))
        return doc.objects().map { o ->
            Project(
                id = o.str("project_id") ?: "",
                slug = o.str("slug") ?: "",
                title = o.str("title") ?: "(untitled)",
                description = o.str("description") ?: "",
                author = o.str("author") ?: "",
                iconUrl = o.str("icon_url"),
                downloads = o.num("downloads") ?: 0,
                follows = o.num("follows") ?: 0,
                categories = o.arr("categories")?.strings() ?: emptyList(),
                loaders = o.arr("categories")?.strings() ?: emptyList(),
                gameVersions = o.arr("versions")?.strings() ?: emptyList(),
                source = Source.MODRINTH
            )
        }
    }

    private fun searchCurseForge(
        query: String, kind: Kind, gameVersion: String?, loader: String?, category: String?,
        offset: Int, relevance: Boolean
    ): List<Project> {
        val key = Settings.string("curseForgeApiKey", "")
        if (key.isBlank()) throw Http.HttpException("CurseForge needs an API key. Add one in Settings → Discover.")
        val params = buildString {
            append("gameId=432&classId=${kind.cfClass}")
            append("&index=$offset&pageSize=$PAGE")
            append("&sortField=").append(if (relevance && query.isNotBlank()) 2 else 6) // 2 relevance, 6 total downloads
            append("&sortOrder=desc")
            if (query.isNotBlank()) append("&searchFilter=").append(enc(query))
            if (!gameVersion.isNullOrBlank()) append("&gameVersion=").append(enc(gameVersion))
            if (!loader.isNullOrBlank()) append("&modLoaderType=").append(curseLoaderId(loader))
        }
        val doc = Http.getJson("$CURSEFORGE/mods/search?$params", headers = mapOf("x-api-key" to key), cacheSeconds = 60)
        return doc.arr("data")?.objects()?.map { o ->
            Project(
                id = (o.num("id") ?: 0).toString(),
                slug = o.str("slug") ?: "",
                title = o.str("name") ?: "(untitled)",
                description = o.str("summary") ?: "",
                author = "",
                iconUrl = o.obj("logo")?.str("thumbnailUrl"),
                downloads = o.num("downloadCount") ?: 0,
                follows = o.num("thumbsUpCount") ?: 0,
                categories = o.arr("categories")?.objects()?.mapNotNull { it.str("name") } ?: emptyList(),
                loaders = o.arr("latestFilesIndexes")?.objects()?.mapNotNull { it.str("modLoader") }?.distinct() ?: emptyList(),
                gameVersions = o.arr("latestFilesIndexes")?.objects()?.mapNotNull { it.str("gameVersion") }?.distinct() ?: emptyList(),
                source = Source.CURSEFORGE
            )
        } ?: emptyList()
    }

    private fun curseLoaderId(loader: String): Int = when (loader.lowercase()) {
        "fabric" -> 4
        "quilt" -> 5
        "forge" -> 1
        "neoforge" -> 6
        else -> 0
    }

    /** The downloadable files of a project, newest first. */
    fun versions(project: Project, gameVersion: String? = null, loader: String? = null): List<ProjectVersion> =
        when (project.source) {
            Source.MODRINTH -> versionsModrinth(project, gameVersion, loader)
            Source.CURSEFORGE -> versionsCurseForge(project, gameVersion, loader)
        }

    /** A single Modrinth version by id, used to resolve `required` dependencies exactly. */
    fun modrinthVersion(versionId: String): ProjectVersion? = runCatching {
        val doc = Http.getJson("$MODRINTH/version/$versionId", cacheSeconds = 3600)
        parseModrinthVersion(doc)
    }.onFailure { Log.w("content", "could not fetch version $versionId: ${it.message}") }.getOrNull()

    /** The current project document for [projectId] (title, slug, kind), for dependency picks. */
    fun modrinthProject(projectId: String): Project? = runCatching {
        val o = Http.getJson("$MODRINTH/project/$projectId", cacheSeconds = 3600)
        val type = o.str("project_type")
        Project(
            id = projectId,
            slug = o.str("slug") ?: projectId,
            title = o.str("title") ?: projectId,
            description = o.str("description") ?: "",
            author = "",
            iconUrl = o.str("icon_url"),
            downloads = 0,
            follows = 0,
            categories = emptyList(),
            loaders = o.arr("loaders")?.strings() ?: emptyList(),
            gameVersions = o.arr("game_versions")?.strings() ?: emptyList(),
            source = Source.MODRINTH
        ).also {
            it.projectType = when (type) {
                "resourcepack" -> "resourcepack"
                "shader" -> "shader"
                "modpack" -> "modpack"
                "datapack" -> "datapack"
                else -> "mod"
            }
        }
    }.onFailure { Log.w("content", "could not fetch project $projectId: ${it.message}") }.getOrNull()

    private fun versionsModrinth(project: Project, gameVersion: String?, loader: String?): List<ProjectVersion> {
        val url = "$MODRINTH/project/${project.id}/version"
        val doc = Json.parseArray(Http.get(url, cacheSeconds = 60))
        return doc.objects().mapNotNull { o -> parseModrinthVersion(o) }
            .filter { v -> gameVersion.isNullOrBlank() || v.gameVersions.contains(gameVersion) }
            .filter { v -> loader.isNullOrBlank() || v.loaders.any { it.equals(loader, true) } || v.loaders.isEmpty() }
    }

    private fun parseModrinthVersion(o: JsonObject): ProjectVersion? {
        val id = o.str("id") ?: return null
        val files = o.arr("files")?.objects() ?: emptyList()
        val primary = files.firstOrNull { it.bool("primary") == true } ?: files.firstOrNull()
        return ProjectVersion(
            id = id,
            projectId = o.str("project_id") ?: "",
            name = o.str("name") ?: "",
            versionNumber = o.str("version_number") ?: "",
            gameVersions = o.arr("game_versions")?.strings() ?: emptyList(),
            loaders = o.arr("loaders")?.strings() ?: emptyList(),
            downloadUrl = primary?.str("url"),
            fileName = primary?.str("filename"),
            sha1 = primary?.obj("hashes")?.str("sha1"),
            dependencies = o.arr("dependencies")?.objects()?.mapNotNull { d ->
                val pid = d.str("project_id") ?: return@mapNotNull null
                ProjectVersion.Dependency(pid, d.str("version_id"), d.str("dependency_type") ?: "required")
            } ?: emptyList()
        )
    }

    private fun versionsCurseForge(project: Project, gameVersion: String?, loader: String?): List<ProjectVersion> {
        val key = Settings.string("curseForgeApiKey", "")
        if (key.isBlank()) throw Http.HttpException("CurseForge needs an API key (Settings → Discover)")
        val params = buildString {
            append("gameVersion=").append(gameVersion ?: "")
            if (!loader.isNullOrBlank()) append("&modLoaderType=").append(curseLoaderId(loader))
        }
        val doc = Http.getJson("$CURSEFORGE/mods/${project.id}/files?$params",
            headers = mapOf("x-api-key" to key), cacheSeconds = 60)
        return doc.arr("data")?.objects()?.mapNotNull { f ->
            val download = f.obj("downloadUrl")?.asString() ?: f.str("downloadUrl")
            ProjectVersion(
                id = (f.num("id") ?: 0).toString(),
                projectId = project.id,
                name = f.str("displayName") ?: "",
                versionNumber = f.str("displayName") ?: "",
                gameVersions = f.arr("gameVersions")?.strings() ?: emptyList(),
                loaders = emptyList(),
                downloadUrl = download,
                fileName = f.str("fileName"),
                sha1 = null
            )
        } ?: emptyList()
    }

    /** Best-effort author lookup for a Modrinth project (the search response omits it for orgs). */
    fun modrinthAuthor(project: Project): String = runCatching {
        project.author.ifBlank {
            val doc = Http.getJson("$MODRINTH/project/${project.id}", cacheSeconds = 600)
            doc.str("team") ?: ""
        }
    }.getOrDefault("")

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
