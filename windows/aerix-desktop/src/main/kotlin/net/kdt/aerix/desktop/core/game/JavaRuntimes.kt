package net.kdt.aerix.desktop.core.game

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.core.net.DownloadJob
import net.kdt.aerix.desktop.core.net.Downloader
import net.kdt.aerix.desktop.core.net.Http
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Java runtime discovery and installation — the desktop replacement for the Android MultiRT
 * manager, whose runtimes are Android-ABI tarballs and must never be reused here.
 *
 * Sources, in priority order:
 *   1. an explicit per-instance `javaPath`
 *   2. the global `javaPath` setting
 *   3. runtimes the launcher installed into `%APPDATA%\Aerix Launcher\runtimes`
 *   4. `JAVA_HOME`, `PATH`, and the usual Windows install locations (Adoptium, Oracle, Zulu,
 *      Corretto, Microsoft, Liberica, Scoop, Chocolatey, Program Files\Java, \Eclipse Adoptium…)
 *
 * Auto-install pulls Temurin from the Adoptium API for the exact major version the game asks for
 * (8, 17, 21, 25…), verifies the SHA-256 the API publishes, and unpacks the ZIP into `runtimes/`.
 */
object JavaRuntimes {
    class Runtime(
        val path: Path,
        val major: Int,
        val vendor: String,
        val version: String,
        val managed: Boolean
    ) {
        fun withManaged(flag: Boolean) = Runtime(path, major, vendor, version, flag)

        val javaExe: Path get() = path.resolve("bin").resolve(if (AppEnv.isWindows) "java.exe" else "java")
        val javawExe: Path get() = path.resolve("bin").resolve(if (AppEnv.isWindows) "javaw.exe" else "java")
        val label: String get() = "Java $major · $vendor"
        val detail: String get() = "$version — $path"
    }

    private var cache: List<Runtime>? = null

    fun invalidate() { cache = null }

    fun managedRoot(): Path = AppEnv.runtimesDir

    /** Every usable runtime on this machine, newest major first. */
    fun discover(force: Boolean = false): List<Runtime> {
        cache?.let { if (!force) return it }
        val found = LinkedHashMap<String, Runtime>()
        fun add(dir: Path?, managed: Boolean = false) {
            if (dir == null) return
            val home = if (dir.fileName.toString() == "bin") dir.parent else dir
            val exe = home.resolve("bin").resolve(if (AppEnv.isWindows) "java.exe" else "java")
            if (!Files.isRegularFile(exe)) return
            val key = home.toAbsolutePath().normalize().toString()
            if (found.containsKey(key)) return
            val info = probe(home) ?: return
            found[key] = info.withManaged(managed)
        }

        // managed runtimes (downloaded by this launcher)
        if (Files.isDirectory(managedRoot())) {
            Files.list(managedRoot()).use { s ->
                s.filter { Files.isDirectory(it) }.forEach { r ->
                    // either the JDK root itself or a single nested folder (zip layout)
                    add(r, managed = true)
                    Files.list(r).use { inner -> inner.filter { Files.isDirectory(it) }.forEach { add(it, managed = true) } }
                }
            }
        }

        add(System.getenv("JAVA_HOME")?.let { Path.of(it) })
        // The launcher is itself a JVM: its own runtime is always a valid game runtime, and it is
        // the only one guaranteed to exist on a machine with no system Java installed.
        runCatching {
            ProcessHandle.current().info().command().orElse(null)
                ?.let { Path.of(it) }?.parent   // .../bin/java -> .../bin
        }.getOrNull()?.let { add(it) }
        System.getenv("PATH")?.split(File.pathSeparatorChar)?.forEach { add(Path.of(it)) }
        // A runtime the player picked by hand in Settings → Java always wins over the scan.
        val override = Settings.string("javaHomeOverride", "")
        if (override.isNotBlank()) add(Path.of(override))

        val candidates = ArrayList<Path>()
        fun scan(base: Path?, depth: Int) {
            if (base == null || !Files.isDirectory(base)) return
            Files.list(base).use { s ->
                s.filter { Files.isDirectory(it) }.toList().forEach { child ->
                    candidates.add(child)
                    if (depth > 0) scan(child, depth - 1)
                }
            }
        }
        val pf = System.getenv("ProgramFiles")?.let { Path.of(it) }
        val pf86 = System.getenv("ProgramFiles(x86)")?.let { Path.of(it) }
        val localAppData = System.getenv("LOCALAPPDATA")?.let { Path.of(it) }
        listOf(
            pf?.resolve("Eclipse Adoptium"), pf?.resolve("Java"), pf?.resolve("Zulu"), pf?.resolve("Amazon Corretto"),
            pf?.resolve("Microsoft"), pf?.resolve("BellSoft"), pf?.resolve("Semeru"),
            pf86?.resolve("Java"), localAppData?.resolve("Programs").let { it?.resolve("Eclipse Adoptium") },
            localAppData?.resolve("Programs").let { it?.resolve("Microsoft") },
            Path.of("C:/Program Files/Eclipse Adoptium"),
            Path.of("C:/Program Files/Java"),
            AppEnv.home.resolve("scoop/apps"), Path.of("C:/ProgramData/chocolatey/lib")
        ).forEach { base -> scan(base, 2) }
        candidates.forEach { add(it) }

        val list = found.values.sortedWith(compareByDescending<Runtime> { it.major }.thenBy { it.vendor })
        cache = list
        Log.i("java", "found ${list.size} runtime(s): ${list.joinToString { "Java ${it.major} (${it.vendor})" }}")
        return list
    }

    fun probe(home: Path): Runtime? {
        val exe = home.resolve("bin").resolve(if (AppEnv.isWindows) "java.exe" else "java")
        if (!Files.isRegularFile(exe)) return null
        return runCatching {
            val process = ProcessBuilder(exe.toString(), "-XshowSettings:properties", "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(6, TimeUnit.SECONDS)
            if (process.isAlive) process.destroyForcibly()
            val major = Regex("java\\.specification\\.version\\s*=\\s*(\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()
                ?: runCatching { Regex("version \"(\\d+)(\\.(\\d+))?").find(output)?.groupValues?.get(1)?.toInt() }.getOrNull()
                ?: return null
            val runtimeName = Regex("java\\.runtime\\.name\\s*=\\s*(.+)").find(output)?.groupValues?.get(1)?.trim()
            val vendor = Regex("java\\.vendor\\s*=\\s*(.+)").find(output)?.groupValues?.get(1)?.trim()
                ?: runtimeName?.substringBefore(' ') ?: "Unknown"
            val version = Regex("java\\.runtime\\.version\\s*=\\s*(.+)").find(output)?.groupValues?.get(1)?.trim()
                ?: runtimeName ?: "?"
            Runtime(home, major, vendor.take(28), version, managed = false)
        }.getOrNull()
    }

    /** Best runtime for [requiredMajor]: an exact (or newer, within reason) match. */
    fun pick(requiredMajor: Int, instanceJavaPath: String = ""): Runtime? {
        val explicit = instanceJavaPath.ifBlank { Settings.string("javaPath", "") }
        if (explicit.isNotBlank()) {
            val home = Path.of(explicit)
            val direct = probe(home)
                ?: probe(home.parent ?: home)
            if (direct != null) return direct
        }
        val all = discover()
        return all.firstOrNull { it.major == requiredMajor }
            ?: all.firstOrNull { it.major > requiredMajor && it.major <= requiredMajor + 4 }
            ?: all.firstOrNull()
    }

    // ---- Adoptium auto-install ---------------------------------------------------------------

    private const val ADOPTIUM_API = "https://api.adoptium.net/v3"

    class ReleaseAsset(val name: String, val url: String, val sha256: String, val size: Long, val version: String)

    /** Finds the Temurin build for [major] on this OS/arch. */
    fun findAdoptiumRelease(major: Int): ReleaseAsset? {
        val os = if (AppEnv.isWindows) "windows" else if (AppEnv.isMac) "mac" else "linux"
        val arch = when (MetadataRules.osArch) { "x86_64" -> "x64"; "arm64" -> "aarch64"; else -> "x86" }
        val url = "$ADOPTIUM_API/assets/latest/$major/hotspot?architecture=$arch&image_type=jdk&os=$os&vendor=eclipse"
        val doc = runCatching { Http.getJson(url, cacheSeconds = 3600) }.getOrNull() ?: return null
        val asset = doc.obj("binary", "package") ?: return null
        return ReleaseAsset(
            name = asset.str("name") ?: "jdk-$major.zip",
            url = asset.str("link") ?: return null,
            sha256 = asset.str("checksum") ?: "",
            size = asset.num("size") ?: -1,
            version = doc.str("version_data", "semver") ?: doc.str("release_name") ?: "Temurin $major"
        )
    }

    /**
     * Downloads and unpacks a JDK for [major]. Blocking; call from a worker thread.
     * @return the new runtime, or null when the download failed
     */
    fun install(major: Int, job: DownloadJob = DownloadJob("jre-$major"), onProgress: (String, Float) -> Unit = { _, _ -> }): Runtime? {
        onProgress("Finding a Java $major build", 0.02f)
        val release = findAdoptiumRelease(major)
        if (release == null) {
            Log.w("java", "no Adoptium release found for Java $major")
            return null
        }
        val target = managedRoot().resolve("temurin-$major")
        Files.createDirectories(target)
        val archive = AppEnv.cacheDir.resolve(release.name)
        Files.createDirectories(archive.parent)
        onProgress("Downloading ${release.name}", 0.05f)
        job.onProgress = { j -> onProgress("Downloading Java $major", 0.05f + j.progress() * 0.85f) }
        Downloader.run(job, listOf(DownloadJob.Task(release.url, archive, sha512 = null, size = release.size)))
        if (job.failed > 0) return null
        // A Java runtime downloaded here is unpacked and then *executed* to launch the game, so an
        // unchecked download is the highest-value place in the whole launcher for a supply-chain
        // attack (a mirror compromise, a DNS answer, a redirect). The check used to be skipped
        // entirely whenever Adoptium returned no checksum; it is now mandatory.
        if (release.sha256.isBlank()) {
            Log.e("java", "refusing to install ${release.name}: the API published no SHA-256")
            Files.deleteIfExists(archive)
            return null
        }
        onProgress("Verifying download", 0.92f)
        val actual = runCatching {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            Files.newInputStream(archive).use { input ->
                val buf = ByteArray(128 * 1024)
                while (true) { val n = input.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
        if (actual == null) {
            // Hashing itself failed; that is not a reason to proceed.
            Log.e("java", "could not hash ${release.name}; refusing to install it")
            Files.deleteIfExists(archive)
            return null
        }
        if (!actual.equals(release.sha256, true)) {
            Log.e("java", "SHA-256 mismatch for ${release.name} (expected ${release.sha256}, got $actual)")
            Files.deleteIfExists(archive)
            return null
        }
        onProgress("Unpacking Java $major", 0.94f)
        unpackArchive(archive, target)
        Files.deleteIfExists(archive)
        invalidate()
        val runtime = probe(target) ?: run {
            val inner = Files.list(target).use { st -> st.filter { Files.isDirectory(it) }.toList() }
            inner.firstNotNullOfOrNull { probe(it) }
        }
        if (runtime == null) {
            Log.e("java", "unpacked Java $major but no java.exe was found in $target")
            return null
        }
        Settings.set("lastInstalledJava", "$major:${runtime.path}")
        Log.i("java", "installed ${runtime.label} into ${runtime.path}")
        onProgress("Java $major ready", 1f)
        return runtime
    }

    private fun unpackArchive(archive: Path, target: Path) {
        val name = archive.fileName.toString().lowercase()
        when {
            name.endsWith(".zip") -> ZipFile(archive.toFile()).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val out = Installer.safeResolve(target, entry.name.replace('\\', '/')) ?: continue
                    if (entry.isDirectory) Files.createDirectories(out)
                    else {
                        Files.createDirectories(out.parent)
                        zip.getInputStream(entry).use { input -> Files.copy(input, out, StandardCopyOption.REPLACE_EXISTING) }
                        if (AppEnv.isWindows || out.toString().endsWith(".dylib") || out.toString().contains("/bin/")) {
                            runCatching { out.toFile().setExecutable(true, false) }
                        }
                    }
                }
            }
            // The Adoptium API serves .zip on Windows and .tar.gz elsewhere; runtimes are installed
            // for the current OS only, so both cases are handled by the JDK tools at hand.
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> unpackTarGz(archive, target)
        }
    }

    /** Minimal tar.gz reader (JDK has no public tar API; Commons Compress is not on the classpath). */
    private fun unpackTarGz(archive: Path, target: Path) {
        java.util.zip.GZIPInputStream(Files.newInputStream(archive)).use { gz ->
            val header = ByteArray(512)
            while (true) {
                var read = 0
                while (read < 512) {
                    val n = gz.read(header, read, 512 - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < 512) break
                if (header.all { it == 0.toByte() }) break
                val rawName = String(header, 0, 100, Charsets.UTF_8).trim('\u0000')
                val prefix = String(header, 345, 155, Charsets.UTF_8).trim('\u0000')
                val name = if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
                val sizeField = String(header, 124, 12, Charsets.US_ASCII).trim('\u0000', ' ')
                val size = if (sizeField.isEmpty()) 0L else sizeField.toLong(8)
                val type = header[156].toInt().toChar()
                val out = Installer.safeResolve(target, name)
                if (out != null) {
                    if (type == '5') {
                        Files.createDirectories(out)
                    } else if (out != null) {
                        Files.createDirectories(out.parent)
                        Files.newOutputStream(out).use { o ->
                            var remaining = size
                            val buf = ByteArray(64 * 1024)
                            while (remaining > 0) {
                                val n = gz.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                                if (n <= 0) break
                                o.write(buf, 0, n)
                                remaining -= n
                            }
                        }
                        runCatching { out.toFile().setExecutable(true, false) }
                    }
                }
                // skip padding to the next 512-byte boundary
                var padding = (512 - (size % 512)).toLong() % 512
                while (padding > 0) {
                    val n = gz.skip(padding)
                    if (n <= 0) break
                    padding -= n
                }
            }
        }
    }

    /** Uninstalls a launcher-managed runtime. */
    fun removeManaged(runtime: Runtime) {
        if (!runtime.path.startsWith(managedRoot())) return
        net.kdt.aerix.desktop.core.instance.Instances.deleteTree(runtime.path)
        invalidate()
    }

    /** The Java major a Minecraft version needs, from its metadata. */
    fun requiredJavaFor(gameDir: Path, versionId: String): Int = runCatching {
        Mojang.resolve(gameDir, versionId).javaMajor
    }.getOrElse { estimateJavaFor(versionId) }

    /** Fallback for versions that are not installed yet. */
    fun estimateJavaFor(versionId: String): Int {
        val m = Regex("(\\d+)\\.(\\d+)(?:\\.(\\d+))?").find(versionId)
        if (m == null) return 21
        val major = m.groupValues[1].toIntOrNull() ?: return 21
        val minor = m.groupValues[2].toIntOrNull() ?: 0
        val patch = m.groupValues[3].toIntOrNull() ?: 0
        return when {
            major < 1 -> 8
            major == 1 && minor <= 16 -> 8
            major == 1 && minor < 20 -> 17
            major == 1 && minor == 20 && patch <= 4 -> 17
            else -> 21
        }
    }
}
