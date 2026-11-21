package net.kdt.aerix.desktop.core.game

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.core.json.JsonObject
import net.kdt.aerix.desktop.core.net.DownloadJob
import net.kdt.aerix.desktop.core.net.Downloader
import net.kdt.aerix.desktop.core.net.Http
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

/**
 * Installs a Minecraft version into the game directory: client jar, libraries, natives, assets and
 * the logging config. Everything lands in the standard `.minecraft` layout so other launchers and
 * the game itself see a normal installation.
 *
 * Behaviour carried over from `tasks/MoJsonDownloader`: verify before downloading, extract natives
 * into `<version>/natives`, map legacy assets into `assets/virtual/legacy`, and never leave a
 * half-written jar behind (downloads go through `.part` files).
 */
object Installer {

    class Progress(
        val phase: String,
        val done: Int,
        val total: Int,
        val bytes: Long = 0,
        val speed: Long = 0,
        val detail: String = ""
    ) {
        val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    fun nativesDir(gameDir: Path, versionId: String): Path = gameDir.resolve("versions").resolve(versionId).resolve("natives")

    /**
     * Installs [versionId] (vanilla or a loader profile already written to `versions/`).
     *
     * @param onProgress called on a background thread with phase updates
     * @return the merged version metadata that is now installed
     */
    fun install(
        gameDir: Path,
        versionId: String,
        versionUrl: String? = null,
        job: DownloadJob = DownloadJob("install-$versionId"),
        onProgress: (Progress) -> Unit = {}
    ): MergedVersion {
        Files.createDirectories(gameDir.resolve("versions"))
        // 1) version JSON (downloads it when the caller only has the id)
        val versionJsonPath = gameDir.resolve("versions").resolve(versionId).resolve("$versionId.json")
        if (!Files.isRegularFile(versionJsonPath)) {
            onProgress(Progress("Fetching version metadata", 0, 1, detail = versionId))
            Mojang.versionJson(gameDir, versionId, versionUrl)
        }
        val resolved = Mojang.resolve(gameDir, versionId)
        val tasks = ArrayList<DownloadJob.Task>()

        // 2) client jar
        val versionDir = gameDir.resolve("versions").resolve(resolved.jarVersion)
        val clientJarFile = versionDir.resolve("${resolved.jarVersion}.jar")
        resolved.clientJar?.let { client ->
            client.url?.let { url ->
                tasks.add(
                    DownloadJob.Task(
                        url = url,
                        destination = clientJarFile,
                        sha1 = client.sha1,
                        size = client.size,
                        after = { file ->
                            // 1.16.5 and older need a side copy named after the profile id
                            if (resolved.jarVersion != versionId) {
                                val copy = gameDir.resolve("versions").resolve(versionId).resolve("$versionId.jar")
                                runCatching {
                                    Files.createDirectories(copy.parent)
                                    Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING)
                                }
                            }
                        }
                    )
                )
            }
        }

        // 3) libraries + natives
        for (lib in resolved.libraries) {
            val dest = gameDir.resolve("libraries").resolve(lib.relativePath)
            tasks.add(DownloadJob.Task(lib.url, dest, sha1 = lib.sha1, size = lib.size))
        }
        val nativesTarget = nativesDir(gameDir, versionId)
        val extractTasks = ArrayList<DownloadJob.Task>()
        for (nat in resolved.natives) {
            val dest = gameDir.resolve("libraries").resolve(nat.relativePath)
            tasks.add(
                DownloadJob.Task(nat.url, dest, sha1 = nat.sha1, size = nat.size, after = { file ->
                    extractNatives(file, nativesTarget, nat.excludeFromNatives)
                })
            )
            extractTasks.add(DownloadJob.Task("file://${dest.toAbsolutePath()}", dest))
        }

        // 4) asset index
        resolved.assetIndex?.let { index ->
            index.url?.let { url ->
                val indexFile = gameDir.resolve("assets").resolve("indexes").resolve("${resolved.assetIndexId}.json")
                tasks.add(DownloadJob.Task(url, indexFile, sha1 = index.sha1, size = index.size))
            }
        }

        // 5) assets
        if (resolved.assetIndex != null && resolved.assetIndex.url != null) {
            onProgress(Progress("Indexing assets", 0, 1, detail = resolved.version.id))
            // the index must exist before its objects can be enumerated
            val indexFile = gameDir.resolve("assets").resolve("indexes").resolve("${resolved.assetIndexId}.json")
            if (!Files.isRegularFile(indexFile)) {
                Downloader.run(job, tasks.filter { it.destination == indexFile }, threads = 1) {}
            }
            if (Files.isRegularFile(indexFile)) {
                val objectsDir = gameDir.resolve("assets").resolve("objects")
                val indexJson = net.kdt.aerix.desktop.core.json.Json.parseObject(Files.readString(indexFile))
                indexJson.obj("objects")?.fields?.forEach { (name, value) ->
                    val hash = value.str("hash") ?: return@forEach
                    val size = value.num("size") ?: -1
                    if (hash.length < 2) return@forEach
                    val sub = hash.substring(0, 2)
                    val dest = objectsDir.resolve(sub).resolve(hash)
                    tasks.add(
                        DownloadJob.Task(
                            Mojang.RESOURCES + "$sub/$hash",
                            dest,
                            sha1 = hash,
                            size = size,
                            weight = 1
                        )
                    )
                }
                if (resolved.legacyAssets) {
                    tasks.add(
                        DownloadJob.Task("file://$indexFile", indexFile, after = {
                            materialiseVirtualAssets(gameDir, indexJson, resolved.assetIndexId)
                        })
                    )
                }
            }
        }

        // 6) logging config
        resolved.loggingFile?.let { log ->
            log.url?.let { url ->
                val id = log.path ?: "client-logging"
                val dest = gameDir.resolve("assets").resolve("log_configs").resolve(id)
                tasks.add(DownloadJob.Task(url, dest, sha1 = log.sha1, size = log.size))
            }
        }

        onProgress(Progress("Downloading game files", 0, tasks.size, detail = "${tasks.size} files"))
        job.onProgress = { j ->
            onProgress(Progress("Downloading game files", j.filesDone(), j.totalFiles, j.bytes, 0, j.currentFile))
        }
        Downloader.run(job, tasks)
        if (job.isCancelled) throw IOException("Cancelled")
        if (job.failed > 0) throw IOException("${job.failed} file(s) failed to download")

        // 7) write the "installed" marker so the library can show it without re-resolving
        val meta = JsonObject()
        meta.put("versionId", resolved.version.id)
        meta.put("jarVersion", resolved.jarVersion)
        meta.put("assets", resolved.assetIndexId)
        meta.put("javaMajor", resolved.javaMajor.toLong())
        meta.put("mainClass", resolved.mainClass)
        meta.put("installedAt", System.currentTimeMillis())
        meta.put("libraryCount", resolved.libraries.size.toLong())
        Files.writeString(
            gameDir.resolve("versions").resolve(versionId).resolve("aerix-install.json"),
            net.kdt.aerix.desktop.core.json.Json.pretty(meta)
        )
        onProgress(Progress("Install complete", tasks.size, tasks.size, detail = versionId))
        Log.i("installer", "installed $versionId (${resolved.libraries.size} libraries, ${resolved.natives.size} natives)")
        return resolved
    }

    /** Extracts a natives jar, skipping the exclusions declared by the library metadata. */
    fun extractNatives(jar: Path, targetDir: Path, exclude: List<String>) {
        Files.createDirectories(targetDir)
        ZipFile(jar.toFile()).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name
                if (name.startsWith("META-INF/") || name.endsWith(".git") || name.endsWith(".sha1")) continue
                if (exclude.any { name.startsWith(it.trimStart('/')) }) continue
                if (!name.endsWith(".dll") && !name.endsWith(".so") && !name.endsWith(".dylib") && !name.endsWith(".jnilib")) continue
                val out = safeResolve(targetDir, name) ?: continue
                Files.createDirectories(out.parent)
                zip.getInputStream(entry).use { input ->
                    Files.copy(input, out, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    /**
     * Copies hashed objects into the `assets/virtual/<indexId>` tree that pre-1.7 versions read.
     * Path traversal from a hostile index is rejected by [safeResolve].
     */
    fun materialiseVirtualAssets(gameDir: Path, index: JsonObject, indexId: String) {
        val objectsDir = gameDir.resolve("assets").resolve("objects")
        val virtualDir = gameDir.resolve("assets").resolve("virtual").resolve(indexId)
        index.obj("objects")?.fields?.forEach { (name, value) ->
            val hash = value.str("hash") ?: return@forEach
            if (hash.length < 2) return@forEach
            val source = objectsDir.resolve(hash.substring(0, 2)).resolve(hash)
            if (!Files.isRegularFile(source)) return@forEach
            val target = safeResolve(virtualDir, name) ?: return@forEach
            if (Files.isRegularFile(target)) return@forEach
            Files.createDirectories(target.parent)
            runCatching { Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING) }
        }
    }

    /** Resolves [relative] inside [base], rejecting anything that escapes it (zip-slip). */
    fun safeResolve(base: Path, relative: String): Path? {
        val clean = relative.replace('\\', '/').trimStart('/')
        if (clean.isEmpty()) return null
        val target = base.resolve(clean).normalize()
        return if (target.startsWith(base.normalize())) target else null
    }

    /** Re-hashes the installed files and returns the ids of versions whose files are incomplete. */
    fun verify(gameDir: Path, versionId: String, onProgress: (Progress) -> Unit = {}): List<String> {
        val problems = ArrayList<String>()
        val resolved = runCatching { Mojang.resolve(gameDir, versionId) }.getOrElse {
            return listOf("version metadata missing")
        }
        resolved.clientJar?.let { client ->
            val jar = gameDir.resolve("versions").resolve(resolved.jarVersion).resolve("${resolved.jarVersion}.jar")
            if (!Downloader.verify(jar, client.sha1, size = client.size)) problems.add("client jar")
        }
        resolved.libraries.forEachIndexed { i, lib ->
            if (i % 50 == 0) onProgress(Progress("Verifying libraries", i, resolved.libraries.size))
            val file = gameDir.resolve("libraries").resolve(lib.relativePath)
            if (!Downloader.verify(file, lib.sha1, size = lib.size)) problems.add("library ${lib.name.toNotation()}")
        }
        resolved.assetIndex?.let { index ->
            val file = gameDir.resolve("assets").resolve("indexes").resolve("${resolved.assetIndexId}.json")
            if (!Downloader.verify(file, index.sha1, size = index.size)) problems.add("asset index")
        }
        return problems
    }

    /** Deletes a version folder (client jar, natives, metadata) but never the shared libraries/assets. */
    fun uninstallVersion(gameDir: Path, versionId: String) {
        val dir = gameDir.resolve("versions").resolve(versionId)
        if (Files.isDirectory(dir)) net.kdt.aerix.desktop.core.instance.Instances.deleteTree(dir)
    }

    /** Downloads an arbitrary file with hash verification, used by the loader installers. */
    fun fetch(url: String, dest: Path, sha1: String? = null) {
        val job = DownloadJob("fetch")
        Downloader.run(job, listOf(DownloadJob.Task(url, dest, sha1 = sha1)))
        if (job.failed > 0) throw IOException("Could not download ${dest.fileName}")
    }

    fun httpGetJson(url: String): JsonObject = Http.getJson(url)
}
