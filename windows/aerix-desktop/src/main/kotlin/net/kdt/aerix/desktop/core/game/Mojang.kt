package net.kdt.aerix.desktop.core.game

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.core.json.JsonArray
import net.kdt.aerix.desktop.core.json.JsonObject
import net.kdt.aerix.desktop.core.net.Http
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Access to Mojang's public metadata (version manifest, per-version JSON, asset index).
 *
 * The Android launcher read the same documents; the URLs are unchanged, so a version downloaded by
 * either launcher is byte-identical. All responses are cached on disk under
 * `<game>/versions/<id>/<id>.json` and `assets/indexes/`, exactly where the vanilla launcher puts them.
 */
object Mojang {
    const val VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    const val RESOURCES = "https://resources.download.minecraft.net/"
    const val LIBRARIES = "https://libraries.minecraft.net/"

    /** Mirrors kept for players behind networks where the official hosts are slow (Android has the same). */
    val manifestMirrors = listOf(
        VERSION_MANIFEST,
        "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
    )

    fun versionManifest(gameDir: Path, forceRefresh: Boolean = false): VersionManifest {
        val cacheFile = gameDir.resolve("versions").resolve("version_manifest_v2.json")
        val cached = if (Files.isRegularFile(cacheFile)) runCatching {
            val age = System.currentTimeMillis() - Files.getLastModifiedTime(cacheFile).toMillis()
            if (!forceRefresh && age < 30 * 60 * 1000L) Json.parseObject(Files.readString(cacheFile)) else null
        }.getOrNull() else null
        val doc = cached ?: run {
            var lastError: Exception? = null
            for (url in manifestMirrors) {
                try {
                    val body = Http.get(url, cacheSeconds = 0)
                    val parsed = Json.parseObject(body)
                    Files.createDirectories(cacheFile.parent)
                    Files.writeString(cacheFile, body, StandardCharsets.UTF_8)
                    return@run parsed
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw lastError ?: Http.HttpException("Could not download the Minecraft version list")
        }
        val versions = ArrayList<VersionSummary>()
        doc.arr("versions")?.objects()?.forEach { v ->
            val id = v.str("id") ?: return@forEach
            versions.add(
                VersionSummary(
                    id = id,
                    type = v.str("type") ?: "release",
                    url = v.str("url") ?: "",
                    sha1 = v.str("sha1"),
                    releaseTime = v.str("releaseTime") ?: ""
                )
            )
        }
        return VersionManifest(doc.str("latest", "release"), doc.str("latest", "snapshot"), versions)
    }

    /** Reads a version JSON from disk, downloading it if missing. */
    fun versionJson(gameDir: Path, versionId: String, url: String? = null): VersionInfo {
        val file = gameDir.resolve("versions").resolve(versionId).resolve("$versionId.json")
        if (Files.isRegularFile(file)) {
            return VersionInfo(Json.parseObject(Files.readString(file)))
        }
        val downloadUrl = url ?: throw Http.HttpException("Version $versionId is not installed and has no download URL")
        val body = Http.get(downloadUrl)
        Files.createDirectories(file.parent)
        Files.writeString(file, body, StandardCharsets.UTF_8)
        return VersionInfo(Json.parseObject(body))
    }

    /** Local (installed) version ids. */
    fun localVersions(gameDir: Path): List<String> {
        val dir = gameDir.resolve("versions")
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { s ->
            s.filter { Files.isRegularFile(it.resolve(it.fileName.toString() + ".json")) }
                .map { it.fileName.toString() }.toList()
        }.sorted()
    }

    /** Merges a version with its `inheritsFrom` chain (loader on top of vanilla). */
    fun mergeChain(gameDir: Path, versionId: String, depth: Int = 0): List<VersionInfo> {
        if (depth > 8) throw IllegalStateException("Version inheritance is too deep for $versionId")
        val info = versionJson(gameDir, versionId)
        val parent = info.inheritsFrom
        return if (parent.isNullOrBlank()) listOf(info) else listOf(info) + mergeChain(gameDir, parent, depth + 1)
    }

    /**
     * Evaluates the full inheritance chain into everything a launch needs.
     *
     * Child entries win: a loader's `mainClass` and its `-D` arguments replace the vanilla ones,
     * while library lists are unioned with child priority, which is how Fabric/Forge/NeoForge
     * profiles are meant to be consumed.
     */
    fun resolve(gameDir: Path, versionId: String): MergedVersion {
        val chain = mergeChain(gameDir, versionId) // child first
        val merged = chain.last()

        // ---- libraries: union, child first, de-duplicated by coordinates+classifier
        val seen = LinkedHashMap<String, ResolvedLibrary>()
        val natives = ArrayList<ResolvedLibrary>()
        for (info in chain) {
            for (lib in info.libraries) {
                val rules = lib.arr("rules")
                if (!MetadataRules.allowed(rules)) continue
                val nameRaw = lib.str("name") ?: continue
                val coords = MavenName.parse(nameRaw) ?: continue
                val downloads = lib.obj("downloads")
                val baseUrl = lib.str("url") ?: LIBRARIES
                val artifact = downloads?.obj("artifact")
                var url = artifact?.str("url")
                var sha1 = artifact?.str("sha1")
                var size = artifact?.num("size") ?: -1
                var path = artifact?.str("path") ?: coords.toPath()
                if (url == null) {
                    // pre-1.13 libraries only carry `url` + `name`
                    if (!baseUrl.endsWith("/")) {
                        // a plain maven repository base
                    }
                    url = baseUrl.trimEnd('/') + "/" + coords.toPath()
                }
                // ---- class artifact vs. natives
                //
                // Three shapes appear in the wild, and an entry can be two of them at once:
                //  * modern (1.19+): natives are their own library whose classifier is
                //    `natives-windows-x86_64`; they must never reach the class path.
                //  * classic (1.6–1.18): one library with `downloads.artifact` plus a `natives`
                //    map and `downloads.classifiers`. The class jar is still needed — Android's
                //    MoJsonDownloader submits both — and the classifier jar is extracted.
                //  * raw (pre-1.6): no `downloads` at all, just `url` + `name`; natives are
                //    derived from the same base URL.
                val modernNative = coords.classifier?.startsWith("natives-") == true
                if (modernNative) {
                    addNative(
                        natives,
                        ResolvedLibrary(coords, url, sha1, size, path, nativeExtract = true)
                    )
                } else {
                    // the class jar (Android: processLibraryWithDownloads → submitArtifact)
                    if (artifact != null || lib.str("url") != null || !lib.has("downloads") || downloads?.has("artifact") == true) {
                        seen.putIfAbsent(coords.toNotation() + ":" + path, ResolvedLibrary(coords, url, sha1, size, path))
                    }
                    val nativesMap = lib.obj("natives")
                    if (nativesMap != null) {
                        val key = MetadataRules.nativeKeys().firstOrNull { nativesMap.has(it) }
                        if (key != null) {
                            val classifier = (nativesMap.str(key) ?: "").replace("\${arch}", archBits())
                            if (classifier.isNotBlank()) {
                                val natCoords = MavenName(coords.group, coords.artifact, coords.version, classifier, coords.extension)
                                val natArtifact = downloads?.obj("classifiers")?.obj(classifier)
                                addNative(
                                    natives,
                                    ResolvedLibrary(
                                        natCoords,
                                        natArtifact?.str("url") ?: (baseUrl.trimEnd('/') + "/" + natCoords.toPath()),
                                        natArtifact?.str("sha1"),
                                        natArtifact?.num("size") ?: -1,
                                        natArtifact?.str("path") ?: natCoords.toPath(),
                                        nativeExtract = true,
                                        excludeFromNatives = lib.arr("extract", "exclude")?.strings() ?: emptyList()
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- main class / args
        val mainClass = chain.firstOrNull { it.mainClass.isNotBlank() }?.mainClass ?: merged.mainClass
        // Parent (vanilla) arguments first, loader arguments after, so the loader's later
        // -Djava.library.path / -p module-path flags take precedence — that is the order the
        // vanilla launcher builds a loader profile in. Each level contributes exactly once.
        val jvmArgs = ArrayList<net.kdt.aerix.desktop.core.json.JsonValue>()
        for (info in chain.asReversed()) jvmArgs.addAll(info.jvmArgs)
        val gameArgs = ArrayList<net.kdt.aerix.desktop.core.json.JsonValue>()
        for (info in chain.asReversed()) gameArgs.addAll(info.gameArgs)
        val legacyArgs = chain.firstNotNullOfOrNull { it.minecraftArguments }

        val clientJar = chain.firstNotNullOfOrNull { it.client }
        val assetIndex = chain.firstNotNullOfOrNull { it.assetIndex }
        val assetIndexId = chain.firstNotNullOfOrNull { it.assetIndexId } ?: "legacy"
        val logging = chain.firstNotNullOfOrNull { it.loggingFile }
        val loggingArg = chain.firstNotNullOfOrNull { it.loggingArgument }
        val javaMajor = merged.javaMajor
        val jarVersion = chain.firstNotNullOfOrNull { it.jarVersion } ?: merged.id
        val legacyAssets = assetIndexId == "legacy" || assetIndexId == "pre-1.6"

        return MergedVersion(
            version = merged,
            libraries = seen.values.toList(),
            natives = natives,
            clientJar = clientJar,
            assetIndex = assetIndex,
            assetIndexId = assetIndexId,
            assetsRoot = "",
            mainClass = mainClass,
            jvmArgs = jvmArgs,
            gameArgs = gameArgs,
            legacyArguments = legacyArgs,
            loggingFile = logging,
            loggingArgument = loggingArg,
            javaMajor = javaMajor,
            jarVersion = jarVersion,
            legacyAssets = legacyAssets
        )
    }

    /** Adds a native jar at most once: inheritance chains repeat the same libraries. */
    private fun addNative(list: MutableList<ResolvedLibrary>, lib: ResolvedLibrary) {
        if (list.none { it.relativePath == lib.relativePath }) list.add(lib)
    }

    private fun archBits(): String = when (MetadataRules.osArch) {
        "x86_64" -> "64"
        "x86" -> "32"
        else -> "arm64"
    }

    /** Asset objects for a version, from the cached index. */
    fun assetIndex(gameDir: Path, indexId: String): JsonObject {
        val file = gameDir.resolve("assets").resolve("indexes").resolve("$indexId.json")
        if (Files.isRegularFile(file)) return Json.parseObject(Files.readString(file))
        throw Http.HttpException("Asset index $indexId is not downloaded yet")
    }
}
