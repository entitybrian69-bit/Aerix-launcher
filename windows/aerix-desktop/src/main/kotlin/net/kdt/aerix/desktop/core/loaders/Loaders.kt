package net.kdt.aerix.desktop.core.loaders

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.core.game.JavaRuntimes
import net.kdt.aerix.desktop.core.instance.Loader
import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.core.net.Http
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/**
 * Mod loader support, mirroring `net.kdt.pojavlaunch.modloaders` plus the Aerix create flow.
 *
 * Two shapes exist in the wild, and they are handled differently on purpose:
 *
 * * **Fabric / Quilt** publish a ready-made *profile JSON* — a small document with `inheritsFrom`
 *   pointing at the vanilla version. Installing is one HTTP GET written into `versions/<id>/`.
 * * **Forge / NeoForge** only ship an *installer*. It has to be executed, because it unpacks
 *   patched classes and processor outputs that no metadata describes. The installer honours
 *   `--installClient <dir>` and writes the profile itself.
 *
 * Both paths end with a normal `versions/<id>/<id>.json`, so [net.kdt.aerix.desktop.core.game.Mojang.resolve]
 * and the launcher never need to know which loader produced a version.
 */
object Loaders {

    /** One selectable loader build for a Minecraft version. */
    class Build(
        val id: String,
        val gameVersion: String,
        val label: String,
        val stable: Boolean
    ) {
        /** The version id this build installs as. */
        val versionId: String get() = when {
            id.startsWith("fabric-") || id.startsWith("quilt-") -> id
            gameVersion.isEmpty() -> id
            id.startsWith(gameVersion) -> id           // Forge already carries the MC version prefix
            else -> "$gameVersion-$id"
        }

        override fun toString(): String = label
    }

    class InstallResult(val versionId: String, val mainClass: String?, val log: String)

    // ---- build listing -----------------------------------------------------------------------

    /**
     * Builds for [loader] on [mcVersion]. Network failures are surfaced as an empty list plus a log
     * line; the create screen shows a warning with a retry button rather than an empty dropdown.
     */
    fun builds(loader: String, mcVersion: String, stableOnly: Boolean = true): List<Build> = when (loader) {
        Loader.FABRIC -> fabricLike("https://meta.fabricmc.net/v2", "fabric", mcVersion, stableOnly)
        Loader.QUILT -> fabricLike("https://meta.quiltmc.org/v3", "quilt", mcVersion, stableOnly)
        Loader.FORGE -> forgeBuilds(mcVersion, stableOnly)
        Loader.NEOFORGE -> neoForgeBuilds(mcVersion)
        Loader.OPTIFINE -> optifineBuilds(mcVersion)
        Loader.BTA -> if (mcVersion.isEmpty() || mcVersion == BTA_BASE) btaBuilds(stableOnly) else emptyList()
        else -> emptyList()
    }

    private fun fabricLike(base: String, prefix: String, mcVersion: String, stableOnly: Boolean): List<Build> {
        val url = "$base/versions/loader/$mcVersion"
        return runCatching {
            val arr = Json.parseArray(Http.get(url, cacheSeconds = 600))
            arr.objects().mapNotNull { entry ->
                val loaderDoc = entry.obj("loader") ?: return@mapNotNull null
                val version = loaderDoc.str("version") ?: return@mapNotNull null
                val stable = loaderDoc.bool("stable") ?: true
                Build("$prefix-$version-$mcVersion", mcVersion, version, stable)
            }.filter { it.stable || !stableOnly }
        }.onFailure { Log.w("loaders", "could not list $prefix builds for $mcVersion: ${it.message}") }
            .getOrDefault(emptyList())
    }

    /** Forge lists versions as `<mc>-<forge>` in its maven metadata; promotions give the labels. */
    private fun forgeBuilds(mcVersion: String, stableOnly: Boolean): List<Build> {
        return runCatching {
            val xml = Http.get("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml", cacheSeconds = 1800)
            val all = Regex("<version>([^<]+)</version>").findAll(xml).map { it.groupValues[1] }.toList()
            val prefix = "$mcVersion-"
            val builds = all.filter { it.startsWith(prefix) && it.substringAfter(prefix).all { c -> c.isDigit() || c == '.' } }
                .map { full ->
                    val forge = full.substringAfter(prefix)
                    Build("forge-$full", mcVersion, forge, isForgeStable(mcVersion, forge))
                }
            builds.sortedByDescending { compareVersions(it.label, it.label) }
                .let { if (stableOnly) it.filter { b -> b.stable } else it }
                .ifEmpty { builds }
        }.onFailure { Log.w("loaders", "could not list Forge builds for $mcVersion: ${it.message}") }
            .getOrDefault(emptyList())
    }

    @Volatile private var forgePromotions: Map<String, String>? = null

    private fun isForgeStable(mcVersion: String, forge: String): Boolean {
        val promotions = forgePromotions ?: runCatching {
            val doc = Json.parse(Http.get("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json", cacheSeconds = 3600))
            doc.obj("promos")?.keys()?.associateWith { key -> doc.str("promos", key) ?: "" } ?: emptyMap()
        }.onFailure { Log.w("loaders", "Forge promotions unavailable: ${it.message}") }.getOrDefault(emptyMap())
            .also { forgePromotions = it }
        val recommended = promotions["$mcVersion-recommended"]
        val latest = promotions["$mcVersion-latest"]
        return forge == recommended || (recommended == null && forge == latest)
    }

    /**
     * NeoForge version numbers encode the Minecraft version for 1.20.2 and newer
     * (`21.1.72` is Minecraft 1.21.1); 1.20.1 was published as `forge/<mc>-<build>`.
     */
    private fun neoForgeBuilds(mcVersion: String): List<Build> {
        val mcParts = mcVersion.removePrefix("1.").split(".").mapNotNull { it.toIntOrNull() }
        val minor = mcParts.getOrNull(0) ?: return emptyList()
        val patch = mcParts.getOrNull(1) ?: 0
        val modern = compareVersions(mcVersion, "1.20.2") >= 0
        return runCatching {
            if (modern) {
                val xml = Http.get("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml", cacheSeconds = 1800)
                Regex("<version>([^<]+)</version>").findAll(xml).map { it.groupValues[1] }
                    // NeoForge 21.x means MC 1.21.x; 20.4 means 1.20.4
                    .filter { v -> matchesNeoVersion(v, minor, patch) }
                    .map { Build("neoforge-$it", mcVersion, it.removePrefix("1.20.1-"), true) }
                    .sortedByDescending { it.label }
                    .toList()
            } else {
                // 1.20.1 legacy: published under net/neoforged/forge
                val xml = Http.get("https://maven.neoforged.net/releases/net/neoforged/forge/maven-metadata.xml", cacheSeconds = 1800)
                Regex("<version>([^<]+)</version>").findAll(xml).map { it.groupValues[1] }
                    .filter { it.startsWith("$mcVersion-") }
                    .map { Build("neoforge-$it", mcVersion, it.removePrefix("$mcVersion-"), true) }
                    .sortedByDescending { it.label }
                    .toList()
            }
        }.onFailure { Log.w("loaders", "could not list NeoForge builds for $mcVersion: ${it.message}") }
            .getOrDefault(emptyList())
    }

    private fun matchesNeoVersion(v: String, minor: Int, patch: Int): Boolean {
        val parts = v.split(".")
        val a = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val b = parts.getOrNull(1)?.toIntOrNull() ?: return false
        // 1.21.1 -> 21.1.x ; 1.20.4 -> 20.4.x ; 1.20.2 -> 20.2.x ; 1.21 -> 21.0.x
        return a == minor && b == patch
    }

    // ---- OptiFine ----------------------------------------------------------------------------

    /**
     * OptiFine publishes no API, only https://optifine.net/downloads. The page is scraped exactly
     * like the Android `OptiFineScraper`: `<h2>Minecraft 1.21.1</h2>` headers introduce
     * `tr.downloadLine` rows whose `td.colFile` is `OptiFine_1.21.1_HD_U_J3.jar` and whose
     * `td.colMirror a` links to `adloadx?f=…`, a page that is itself scraped for the real jar.
     */
    private fun optifineBuilds(mcVersion: String): List<Build> {
        val builds = runCatching {
            val html = Http.get("https://optifine.net/downloads", cacheSeconds = 1800, timeoutSeconds = 20)
            parseOptifinePage(html)
        }.onFailure { Log.w("loaders", "could not read the OptiFine download page: ${it.message}") }
            .getOrDefault(emptyList())
        return if (mcVersion.isEmpty()) builds else builds.filter { it.gameVersion == mcVersion }
    }

    /** Exposed for the self test: scraping is pure string work and must not need the network. */
    fun parseOptifinePage(html: String): List<Build> {
        val builds = ArrayList<Build>()
        var gameVersion = ""
        // Each game version is an <h2>; the rows beneath it belong to it until the next one.
        val tokens = Regex(
            "<h2[^>]*>\\s*(?:Minecraft\\s*)?([0-9][^<]*?)\\s*</h2>|" +
                "<tr[^>]*class=\"[^\"]*downloadLine[^\"]*\"[^>]*>(.*?)</tr>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).findAll(html)
        for (match in tokens) {
            val header = match.groupValues[1]
            if (header.isNotEmpty()) {
                gameVersion = header.trim()
                continue
            }
            val row = match.groupValues[2]
            val file = Regex("colFile[^>]*>[^<]*?<[^>]*>([^<]+)</", RegexOption.IGNORE_CASE)
                .find(row)?.groupValues?.get(1)?.trim()
                ?: Regex("colFile[^>]*>([^<]+)<", RegexOption.IGNORE_CASE)
                    .find(row)?.groupValues?.get(1)?.trim()
                ?: continue
            // OptiFine_1.21.1_HD_U_J3.jar -> HD U J3
            val label = file.removeSuffix(".jar").substringAfter(gameVersion + "_", "")
                .takeIf { it.isNotEmpty() }?.replace('_', ' ') ?: file.removeSuffix(".jar")
            // Previews are marked in the row class ("downloadLine preview") and named "_preN"
            val preview = row.contains("preview", true) || Regex("_pre\\d", RegexOption.IGNORE_CASE).containsMatchIn(file)
            builds.add(Build("optifine-$gameVersion-$label", gameVersion, label, !preview))
        }
        return builds.distinctBy { it.id }
    }

    /** Resolves the real jar behind an `adloadx?f=…` link (the page has no direct URLs). */
    fun resolveOptiFineDownload(adloadxUrl: String): String? {
        val page = Http.get(adloadxUrl, timeoutSeconds = 20)
        return Regex("<a[^>]*onclick=\"onDownload\\(\\)\"[^>]*href=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(page)?.groupValues?.get(1)
            ?: Regex("<a[^>]*href=\"([^\"]+)\"[^>]*onclick=\"onDownload\\(\\)\"", RegexOption.IGNORE_CASE)
                .find(page)?.groupValues?.get(1)
    }

    // ---- Better than Adventure ----------------------------------------------------------------

    private const val BTA_BASE = "b1.7.3"
    private const val BTA_DOWNLOADS = "https://downloads.betterthanadventure.net/bta-client"

    /**
     * BTA's manifests are simple `{"versions": ["v7.3", …]}` documents, newest last. Releases are
     * tagged `v<major>.<minor>[_patch]`; nightlies are `YYYY-MM-DD`.
     */
    private fun btaBuilds(stableOnly: Boolean): List<Build> {
        val builds = ArrayList<Build>()
        if (!stableOnly) {
            runCatching {
                val doc = Json.parseObject(Http.get("$BTA_DOWNLOADS/nightly/versions.json", cacheSeconds = 900))
                doc.arr("versions")?.strings()?.reversed()?.take(12)?.forEach { v ->
                    builds.add(Build("bta-nightly-$v", BTA_BASE, "$v (nightly)", false))
                }
            }.onFailure { Log.d("loaders", "no BTA nightlies: ${it.message}") }
        }
        runCatching {
            val doc = Json.parseObject(Http.get("$BTA_DOWNLOADS/release/versions.json", cacheSeconds = 900))
            doc.arr("versions")?.strings()?.reversed()?.forEach { v ->
                builds.add(Build("bta-$v", BTA_BASE, v, true))
            }
        }.onFailure { Log.w("loaders", "could not list Better than Adventure: ${it.message}") }
        return builds
    }

    /** `v7.3_04` -> `7.3.4` (`_04` is a patch number, not a decimal); used for sorting and display. */
    fun btaSemver(label: String): String =
        label.removePrefix("v").split('.', '_').joinToString(".") { part ->
            val number = part.toIntOrNull()
            if (number != null) number.toString() else part
        }

    /**
     * Forge's `<version>` strings sort as text, which puts `47.2.9` after `47.2.10`. Split into
     * numeric segments and compare them numerically.
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-')
        val pb = b.split('.', '-')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i)?.toIntOrNull() ?: 0
            val y = pb.getOrNull(i)?.toIntOrNull() ?: 0
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    // ---- installation ------------------------------------------------------------------------

    /**
     * Installs [build] into [gameDir], writing `versions/<id>/<id>.json`.
     *
     * @param onProgress receives a phase description and a 0..1 fraction
     */
    fun install(
        gameDir: Path,
        loader: String,
        mcVersion: String,
        build: Build,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): InstallResult {
        Files.createDirectories(gameDir.resolve("versions"))
        return when (loader) {
            Loader.FABRIC, Loader.QUILT -> installProfileJson(gameDir, loader, mcVersion, build, onProgress)
            Loader.FORGE, Loader.NEOFORGE -> installWithInstaller(gameDir, loader, mcVersion, build, onProgress)
            Loader.OPTIFINE -> installOptiFine(gameDir, build, onProgress)
            Loader.BTA -> installBta(gameDir, build, onProgress)
            else -> throw IllegalStateException("${Loader.displayName(loader)} does not need an installation step")
        }
    }

    private fun installProfileJson(
        gameDir: Path,
        loader: String,
        mcVersion: String,
        build: Build,
        onProgress: (String, Float) -> Unit
    ): InstallResult {
        val base = if (loader == Loader.QUILT) "https://meta.quiltmc.org/v3" else "https://meta.fabricmc.net/v2"
        val prefix = if (loader == Loader.QUILT) "quilt" else "fabric"
        val url = "$base/versions/loader/$mcVersion/${build.label}/profile/json"
        onProgress("Fetching ${Loader.displayName(loader)} ${build.label}", 0.1f)
        val body = Http.get(url)
        val doc = Json.parseObject(body)
        // The profile must keep its own id: inheritsFrom is resolved by that string.
        val id = doc.str("id") ?: "$prefix-loader-${build.label}-$mcVersion"
        val dir = gameDir.resolve("versions").resolve(id)
        Files.createDirectories(dir)
        val target = dir.resolve("$id.json")
        val tmp = dir.resolve("$id.json.tmp")
        Files.writeString(tmp, body, StandardCharsets.UTF_8)
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        onProgress("Installed ${Loader.displayName(loader)} ${build.label}", 1f)
        Log.i("loaders", "installed $prefix profile $id into $gameDir")
        return InstallResult(id, doc.str("mainClass"), "profile json")
    }

    // ---- OptiFine install ---------------------------------------------------------------------

    /**
     * OptiFine ships a normal Forge-style installer. It is downloaded from the mirror page, then run
     * exactly like the Forge one because that is what it is — the only difference is where the jar
     * comes from.
     */
    private fun installOptiFine(gameDir: Path, build: Build, onProgress: (String, Float) -> Unit): InstallResult {
        val cacheDir = gameDir.resolve("installers").also { Files.createDirectories(it) }
        val jar = cacheDir.resolve("${build.id}.jar".replace(' ', '_'))
        if (!Files.isRegularFile(jar) || Files.size(jar) < 200_000) {
            val page = "https://optifine.net/adloadx?f=OptiFine_${build.gameVersion}_${build.label.replace(' ', '_')}.jar"
            onProgress("Locating the OptiFine download", 0.05f)
            val direct = resolveOptiFineDownload(page)
                ?: throw IllegalStateException("OptiFine did not offer a download for ${build.label} on ${build.gameVersion}")
            onProgress("Downloading OptiFine ${build.label}", 0.2f)
            downloadTo(direct, jar) { fraction -> onProgress("Downloading OptiFine", 0.2f + 0.6f * fraction) }
        }
        // The OptiFine installer writes versions/<mc>-OptiFine_<label>/; run it in the game dir.
        val java = JavaRuntimes.pick(8) ?: throw IllegalStateException("OptiFine's installer needs Java 8 or newer")
        onProgress("Running the OptiFine installer", 0.85f)
        val out = runInstaller(java, jar, gameDir)
        val versions = gameDir.resolve("versions")
        val installed = Files.list(versions).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .filter { it.fileName.toString().contains("OptiFine", true) }
                .max(Comparator.comparingLong { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) })
                .orElse(null)
        } ?: throw IllegalStateException("The OptiFine installer finished but wrote no version:\n$out")
        val id = installed.fileName.toString()
        onProgress("Installed OptiFine ${build.label}", 1f)
        return InstallResult(id, "net.minecraft.launchwrapper.Launch", out)
    }

    // ---- Better than Adventure install --------------------------------------------------------

    /**
     * BTA is game and loader at once: a client jar plus a profile that inherits from `b1.7.3`.
     * The profile carries the jar as a synthetic `bta-client:bta-client` library, so the normal
     * installer downloads it into `libraries/` and the launcher adds it to the classpath.
     */
    private fun installBta(gameDir: Path, build: Build, onProgress: (String, Float) -> Unit): InstallResult {
        val nightlies = build.id.startsWith("bta-nightly-")
        val versionName = if (nightlies) build.label.substringBefore(" (") else build.label
        val kind = if (nightlies) "nightly" else "release"
        val jarName = "bta-client-$versionName.jar"
        val jarPath = gameDir.resolve("libraries").resolve("bta-client").resolve(jarName)
        Files.createDirectories(jarPath.parent)
        if (!Files.isRegularFile(jarPath) || Files.size(jarPath) < 10_000) {
            onProgress("Downloading Better than Adventure $versionName", 0.15f)
            downloadTo("$BTA_DOWNLOADS/$kind/$versionName/client.jar", jarPath) { fraction ->
                onProgress("Downloading Better than Adventure", 0.15f + 0.6f * fraction)
            }
        }
        val id = "bta-$versionName"
        val dir = gameDir.resolve("versions").resolve(id).also { Files.createDirectories(it) }
        val libPath = "bta-client/bta-client-$versionName.jar"
        // The client is compiled for Java 8 unless a newer class version turns up; 8 is the safe ask.
        val profile = """
            {
              "id": "$id",
              "inheritsFrom": "$BTA_BASE",
              "mainClass": "net.minecraft.client.Minecraft",
              "type": "release",
              "libraries": [
                {
                  "name": "bta-client:bta-client:$versionName",
                  "downloads": { "artifact": { "path": "$libPath", "url": "$BTA_DOWNLOADS/$kind/$versionName/client.jar" } }
                }
              ],
              "javaVersion": { "majorVersion": 8 }
            }
        """.trimIndent()
        onProgress("Writing the Better than Adventure profile", 0.9f)
        val target = dir.resolve("$id.json")
        val tmp = dir.resolve("$id.json.tmp")
        Files.writeString(tmp, profile, StandardCharsets.UTF_8)
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        onProgress("Installed Better than Adventure $versionName", 1f)
        Log.i("loaders", "installed BTA $versionName into $gameDir")
        return InstallResult(id, "net.minecraft.client.Minecraft", "bta profile")
    }

    /**
     * Streams a URL to [target] via a `.part` file, so a cancelled or failed download never leaves a
     * truncated jar where the launcher expects a complete one.
     */
    private fun downloadTo(url: String, target: Path, onProgress: (Float) -> Unit) {
        Files.createDirectories(target.parent)
        val tmp = target.resolveSibling(target.fileName.toString() + ".part")
        // Sizes are unknown up front (the mirror sends no useful Content-Length), so the bar is
        // driven by bytes against an expected jar size and simply clamps at 99% until it lands.
        val expected = 2_000_000f
        Files.newOutputStream(tmp).use { out ->
            Http.downloadStream(url, onProgress = { bytes ->
                onProgress((bytes / expected).coerceIn(0f, 0.99f))
            }) { input -> input.copyTo(out) }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Runs an installer jar and returns its combined output; a non-zero exit becomes an exception. */
    private fun runInstaller(java: JavaRuntimes.Runtime, jar: Path, gameDir: Path): String {
        val process = ProcessBuilder(
            java.javaExe.toString(),
            "-jar", jar.toString(), "--installClient", gameDir.toAbsolutePath().toString()
        ).directory(gameDir.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(15, TimeUnit.MINUTES)
        if (process.isAlive) {
            process.destroyForcibly()
            throw IllegalStateException("The installer did not finish within 15 minutes")
        }
        if (process.exitValue() != 0) {
            throw IllegalStateException("Installer exited with code ${process.exitValue()}:\n" + output.takeLast(1200))
        }
        return output
    }

    private fun installerUrl(loader: String, mcVersion: String, build: Build): String {
        val full = build.id.removePrefix("forge-").removePrefix("neoforge-")
        return if (loader == Loader.NEOFORGE) {
            if (compareVersions(mcVersion, "1.20.2") >= 0)
                "https://maven.neoforged.net/releases/net/neoforged/neoforge/$full/neoforge-$full-installer.jar"
            else
                "https://maven.neoforged.net/releases/net/neoforged/forge/$full/forge-$full-installer.jar"
        } else {
            "https://maven.minecraftforge.net/net/minecraftforge/forge/$full/forge-$full-installer.jar"
        }
    }

    /**
     * Runs the official installer in `--installClient` mode.
     *
     * The installer needs a JDK and prints its own progress; that output goes to the launcher log
     * so a failed processor is diagnosable. A non-zero exit is turned into an exception carrying
     * the last lines, which the create screen shows verbatim.
     */
    private fun installWithInstaller(
        gameDir: Path,
        loader: String,
        mcVersion: String,
        build: Build,
        onProgress: (String, Float) -> Unit
    ): InstallResult {
        val cacheDir = gameDir.resolve("installers").also { Files.createDirectories(it) }
        val fileName = "installer-${build.id}.jar"
        val jar = cacheDir.resolve(fileName)
        if (!Files.isRegularFile(jar) || Files.size(jar) < 10_000) {
            onProgress("Downloading the ${Loader.displayName(loader)} installer", 0.15f)
            val url = installerUrl(loader, mcVersion, build)
            val bytes = Http.download(url)
            if (bytes.isEmpty()) throw IllegalStateException("The installer download was empty ($url)")
            Files.write(jar, bytes)
        }

        val requiredJava = when {
            loader == Loader.NEOFORGE && compareVersions(mcVersion, "1.20.5") >= 0 -> 21
            loader == Loader.NEOFORGE && compareVersions(mcVersion, "1.20.2") >= 0 -> 17
            loader == Loader.NEOFORGE -> 17
            compareVersions(mcVersion, "1.18") >= 0 -> 17
            compareVersions(mcVersion, "1.17") >= 0 -> 16
            else -> 8
        }
        val java = JavaRuntimes.pick(requiredJava) ?: JavaRuntimes.pick(8) ?: throw IllegalStateException(
            "Installing ${Loader.displayName(loader)} needs Java $requiredJava. Install it from Settings → Java."
        )
        onProgress("Running the ${Loader.displayName(loader)} installer (${java.label})", 0.35f)

        // A dedicated libraries folder inside the instance keeps the install self-contained; the
        // installer also writes its own versions/<id>/ JSON, which is all the launcher needs.
        val command = listOf(
            java.javaExe.toString(), "-jar", jar.toAbsolutePath().toString(),
            "--installClient", gameDir.toAbsolutePath().toString()
        )
        Log.i("loaders", "running installer: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .directory(gameDir.toFile())
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val started = System.currentTimeMillis()
        process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val clean = line.replace(Regex("\\u001b\\[[;\\d]*m"), "")
                output.append(clean).append('\n')
                Log.i("loaders-installer", clean)
                if (clean.contains("Extracting", true) || clean.contains("Downloading", true)) {
                    onProgress("Installing ${Loader.displayName(loader)}: ${clean.take(70)}", 0.5f)
                }
            }
        }
        val finished = process.waitFor(15, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            throw IllegalStateException("The ${Loader.displayName(loader)} installer did not finish in 15 minutes")
        }
        if (process.exitValue() != 0) {
            val tail = output.lines().filter { it.isNotBlank() }.takeLast(8).joinToString("\n")
            throw IllegalStateException("The ${Loader.displayName(loader)} installer failed (exit ${process.exitValue()}):\n$tail")
        }
        Log.i("loaders", "installer finished in ${(System.currentTimeMillis() - started) / 1000}s")

        // Find what the installer produced: newest version JSON that did not exist before.
        val versionId = findInstalledVersion(gameDir, loader, build, mcVersion)
            ?: throw IllegalStateException("The ${Loader.displayName(loader)} installer finished but wrote no version profile")
        onProgress("Installed ${Loader.displayName(loader)} ${build.label}", 1f)
        return InstallResult(versionId, null, output.toString())
    }

    /**
     * Locates the profile the installer wrote. Forge uses `<mc>-forge-<forge>`, NeoForge
     * `neoforge-<version>` (and `1.20.1-forge-…` for its legacy build), but some builds differ in
     * punctuation, so the id is discovered from disk rather than assumed.
     */
    private fun findInstalledVersion(gameDir: Path, loader: String, build: Build, mcVersion: String): String? {
        val versionsDir = gameDir.resolve("versions")
        if (!Files.isDirectory(versionsDir)) return null
        val candidates = Files.list(versionsDir).use { s ->
            s.filter { Files.isRegularFile(it.resolve(it.fileName.toString() + ".json")) }
                .map { it.fileName.toString() }.toList()
        }
        val hint = build.label.replace("-", ".")
        val exact = when (loader) {
            Loader.FORGE -> candidates.firstOrNull { it.equals("$mcVersion-forge-${build.label}", true) || it.equals(build.id, true) }
            Loader.NEOFORGE -> candidates.firstOrNull {
                it.equals("neoforge-${build.label}", true) || it.equals("neoforge-$hint", true) ||
                        it.endsWith(build.label, true)
            }
            else -> candidates.firstOrNull { it.equals(build.versionId, true) }
        }
        if (exact != null) return exact
        // fall back to any profile mentioning the loader and the loader build
        return candidates.firstOrNull { v ->
            v.contains(loader, true) && (v.contains(build.label, true) || v.contains(mcVersion))
        }
    }
}
