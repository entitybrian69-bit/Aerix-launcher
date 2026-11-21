package net.kdt.aerix.desktop.core.content

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.Loader
import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.core.json.JsonObject
import net.kdt.aerix.desktop.core.net.Downloader
import net.kdt.aerix.desktop.core.net.Http
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/**
 * Installing content into an instance.
 *
 * Three shapes, all handled for real rather than as a file copy with a hope:
 *
 * * **Mod / resource pack / shader / datapack** — a single jar or zip dropped in the right folder.
 * * **`.mrpack` (Modrinth)** — a zip with `modrinth.index.json` listing every file's URL and hash,
 *   plus `overrides/` for configs. The launcher downloads each entry, verifies the hash and copies
 *   the overrides, exactly as the Modrinth app does.
 * * **CurseForge `.zip`** — a zip with `manifest.json` naming project ids and file ids; overrides
 *   live in `overrides/`. Files that CurseForge does not serve without an API key are reported, not
 *   silently skipped.
 */
object ContentInstaller {

    class Progress(val phase: String, val done: Int, val total: Int) {
        val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
    }

    class Result(val installedFiles: Int, val skipped: List<String>, val notes: List<String>)

    fun folderFor(instance: Instance, kind: Content.Kind): Path = instance.gameDir.resolve(kind.folder)

    /** A plain file install (the common case from Discover). */
    fun installFile(
        instance: Instance,
        kind: Content.Kind,
        url: String,
        fileName: String,
        sha1: String? = null,
        onProgress: (Progress) -> Unit = {}
    ): Path {
        val dir = folderFor(instance, kind)
        Files.createDirectories(dir)
        val safeName = sanitiseFile(fileName)
        val target = dir.resolve(safeName)
        onProgress(Progress("Downloading $safeName", 0, 1))
        val bytes = Http.download(url)
        if (bytes.isEmpty()) throw IllegalStateException("The download for $safeName was empty")
        val tmp = dir.resolve("$safeName.part")
        Files.write(tmp, bytes)
        if (sha1 != null && !Downloader.verify(tmp, sha1 = sha1, size = bytes.size.toLong())) {
            val actual = Downloader.sha1(tmp)
            Files.deleteIfExists(tmp)
            throw IllegalStateException("$safeName failed its SHA-1 check (expected $sha1, got $actual)")
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        onProgress(Progress("Installed $safeName", 1, 1))
        Log.i("content", "installed $safeName into $dir")
        return target
    }

    /** Installs a downloaded pack archive (`.mrpack` or CurseForge `.zip`) into [instance]. */
    fun installPack(instance: Instance, archive: Path, onProgress: (Progress) -> Unit = {}): Result {
        val entries = readZipIndex(archive)
        return when {
            entries.contains("modrinth.index.json") -> installMrpack(instance, archive, onProgress)
            entries.contains("manifest.json") -> installCurseZip(instance, archive, onProgress)
            else -> throw IllegalStateException(
                "This zip is not a modpack: it has no modrinth.index.json or manifest.json.\n" +
                        "If it is a plain mod, use Discover → Mods instead."
            )
        }
    }

    private fun installMrpack(instance: Instance, archive: Path, onProgress: (Progress) -> Unit): Result {
        val game = instance.gameDir
        Files.createDirectories(game)
        val index = unzipEntry(archive, "modrinth.index.json")
            ?: throw IllegalStateException("modrinth.index.json is missing from the pack")
        val doc = Json.parseObject(String(index, StandardCharsets.UTF_8))
        val files = doc.arr("files")?.objects() ?: emptyList()
        val skipped = ArrayList<String>()
        var count = 0
        val total = files.size
        var done = 0
        for (file in files) {
            val path = file.str("path") ?: continue
            val downloads = file.arr("downloads")?.strings() ?: emptyList()
            val hashes = file.obj("hashes")
            val sha1 = hashes?.str("sha1")
            if (downloads.isEmpty()) { skipped.add(path); continue }
            val target = safeResolve(game, path)
            if (target == null) { skipped.add("$path (would escape the instance)"); continue }
            Files.createDirectories(target.parent)
            onProgress(Progress("Downloading ${target.fileName} (${done + 1}/$total)", done, total))
            var ok = false
            for (url in downloads) {
                try {
                    val bytes = Http.download(url)
                    Files.write(target, bytes)
                    if (sha1 != null && !Downloader.verify(target, sha1 = sha1, size = bytes.size.toLong())) {
                        Files.deleteIfExists(target)
                        throw IllegalStateException("hash mismatch")
                    }
                    ok = true
                    break
                } catch (e: Exception) {
                    Log.w("content", "pack file $url failed: ${e.message}")
                }
            }
            if (ok) count++ else skipped.add(path)
            done++
        }
        // copy overrides/ into the instance, preserving the tree
        val overrides = extractPrefix(archive, "overrides/")
        overrides.forEach { (rel, data) ->
            val target = safeResolve(game, rel) ?: return@forEach
            Files.createDirectories(target.parent)
            Files.write(target, data)
        }
        // The pack names itself, and says which loader and version it was built for. Adopting that
        // is what makes an imported pack launchable: without it the instance keeps whatever it had,
        // which for a fresh import is nothing.
        val notes = ArrayList<String>()
        runCatching {
            val name = doc.str("name")?.ifBlank { null }
            if (name != null) {
                instance.name = name
                notes.add("named “$name”")
            }
            val deps = doc.obj("dependencies") ?: return@runCatching
            val mcVersion = deps.str("minecraft")?.ifBlank { null }
            if (mcVersion != null) {
                instance.mcVersion = mcVersion
                if (instance.versionId.isBlank()) instance.versionId = mcVersion
                notes.add("Minecraft $mcVersion")
            }
            val loaders = listOf(
                "fabric-loader" to Loader.FABRIC,
                "quilt-loader" to Loader.QUILT,
                "forge" to Loader.FORGE,
                "neoforge" to Loader.NEOFORGE
            )
            for ((key, id) in loaders) {
                val raw = deps.str(key)?.ifBlank { null } ?: continue
                instance.loader = id
                instance.loaderVersion = raw
                notes.add("${Loader.displayName(id)} $raw")
                break
            }
            instance.save()
        }.onFailure { Log.w("content", "pack metadata could not be applied: ${it.message}") }
        onProgress(Progress("Pack installed", 1, 1))
        return Result(count + overrides.size, skipped, notes.ifEmpty { listOf("Minecraft ${instance.mcVersion}") })
    }

    private fun installCurseZip(instance: Instance, archive: Path, onProgress: (Progress) -> Unit): Result {
        val game = instance.gameDir
        val manifestBytes = unzipEntry(archive, "manifest.json")
            ?: throw IllegalStateException("manifest.json is missing from the pack")
        val manifest = Json.parseObject(String(manifestBytes, StandardCharsets.UTF_8))
        val version = manifest.str("minecraft") ?: ""
        val loaders = manifest.obj("minecraft")?.arr("modLoaders")?.objects() ?: emptyList()
        val primary = loaders.firstOrNull { it.bool("primary") == true } ?: loaders.firstOrNull()
        val loaderId = primary?.str("id") ?: ""
        val skipped = ArrayList<String>()
        val notes = ArrayList<String>()
        if (version.isNotBlank() && instance.mcVersion.isBlank()) {
            instance.mcVersion = version
            instance.save()
        }
        notes.add(if (loaderId.isBlank()) "no loader in manifest" else "loader $loaderId")

        // CurseForge files need their CDN URL, which requires the API key. When it is present the
        // packs install fully; otherwise the overrides still land and the player is told exactly why.
        val key = net.kdt.aerix.desktop.app.Settings.string("curseForgeApiKey", "")
        val files = manifest.arr("files")?.objects() ?: emptyList()
        var installed = 0
        files.forEachIndexed { i, f ->
            val projectId = f.num("projectID") ?: return@forEachIndexed
            val fileId = f.num("fileID") ?: return@forEachIndexed
            val path = f.str("path") ?: ""
            if (key.isBlank()) { skipped.add("project $projectId file $fileId"); return@forEachIndexed }
            onProgress(Progress("Downloading file ${i + 1}/${files.size}", i, files.size))
            runCatching {
                val meta = Http.getJson(
                    "https://api.curseforge.com/v1/mods/$projectId/files/$fileId",
                    headers = mapOf("x-api-key" to key)
                )
                val url = meta.obj("data")?.str("downloadUrl") ?: throw IllegalStateException("CurseForge hides this download")
                val name = meta.obj("data")?.str("fileName") ?: "file-$fileId.jar"
                val folder = when {
                    path.isNotBlank() -> game.resolve(path)
                    name.endsWith(".jar") -> game.resolve("mods").resolve(name)
                    else -> game.resolve("mods").resolve(name)
                }
                Files.createDirectories(folder.parent)
                Files.write(folder, Http.download(url))
                installed++
            }.onFailure { skipped.add("project $projectId: ${it.message}") }
        }
        if (key.isBlank() && files.isNotEmpty()) {
            skipped.add("${files.size} file(s) need a CurseForge API key (Settings → Discover)")
        }
        val overrides = extractPrefix(archive, "overrides/")
        overrides.forEach { (rel, data) ->
            val target = safeResolve(game, rel) ?: return@forEach
            Files.createDirectories(target.parent)
            Files.write(target, data)
        }
        onProgress(Progress("Pack installed", 1, 1))
        return Result(installed + overrides.size, skipped, notes)
    }

    // ---- zip helpers -------------------------------------------------------------------------

    private fun readZipIndex(archive: Path): Set<String> {
        val names = HashSet<String>()
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names.add(entry.name)
                entry = zip.nextEntry
            }
        }
        return names
    }

    private fun unzipEntry(archive: Path, name: String): ByteArray? {
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) return zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun extractPrefix(archive: Path, prefix: String): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.startsWith(prefix)) {
                    val rel = entry.name.removePrefix(prefix)
                    if (rel.isNotBlank()) out[rel] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return out
    }

    /** Path traversal guard: a pack must never write outside the instance. */
    fun safeResolve(base: Path, relative: String): Path? {
        if (relative.isBlank()) return null
        val normal = base.toAbsolutePath().normalize()
        val target = normal.resolve(relative.replace('\\', '/')).normalize()
        return if (target.startsWith(normal) && target != normal) target else null
    }

    fun sanitiseFile(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]"), "-").trim()
        return if (cleaned.isBlank() || cleaned.length > 120) "download-${System.currentTimeMillis()}.bin" else cleaned
    }
}
