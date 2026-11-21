package net.kdt.aerix.desktop.core.game

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.auth.accounts.Account
import net.kdt.aerix.desktop.core.auth.accounts.Accounts
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.Instances
import net.kdt.aerix.desktop.core.json.JsonArray
import net.kdt.aerix.desktop.core.json.JsonObject
import net.kdt.aerix.desktop.core.json.JsonValue
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Launches Minecraft as a real Windows process.
 *
 * Everything Android did inside the app's own JVM (LWJGL with Android bindings, GL4ES, SDL,
 * `JvmLauncher`) is replaced by the standard desktop mechanism: a `java.exe` child process with the
 * version's own arguments, the mod loader's tweaker/agent flags, and Mojang's own client jar.
 *
 * Design notes:
 *  * Arguments are written to a **response file** (`@argfile`) and passed by path. Tokens and paths
 *    never appear in a command line another process can read, and long classpaths (hundreds of
 *    libraries) comfortably exceed Windows' 32 KB command-line limit.
 *  * `arguments.jvm`/`arguments.game` are resolved with the same variable map as
 *    `GameRunner.getMoJsonClientArgs`, including `classpath_separator` = `;` on Windows.
 *  * The child's stdout/stderr are streamed line by line into the Console screen and a per-launch
 *    log file, so a crash is visible instead of a silently closed window.
 */
object GameLauncher {
    const val VAR_CLASSPATH_SEPARATOR = ";"

    class LaunchSpec(
        val instance: Instance,
        val account: Account,
        val runtime: JavaRuntimes.Runtime,
        val command: List<String>,
        val classpath: String,
        val nativesDir: Path,
        val mainClass: String,
        val gameDir: Path,
        val logFile: Path
    )

    class RunningGame(
        val instance: Instance,
        val spec: LaunchSpec,
        val process: Process,
        val startedAt: Long = System.currentTimeMillis()
    ) {
        val id: String = "${instance.id}-${startedAt}"
    }

    private val running = CopyOnWriteArrayList<RunningGame>()
    private val listeners = CopyOnWriteArrayList<(RunningGame) -> Unit>()

    fun runningGames(): List<RunningGame> = running.toList()
    fun isRunning(instance: Instance): Boolean = running.any { it.instance.id == instance.id }
    fun onStarted(l: (RunningGame) -> Unit) { listeners.add(l) }

    private val exitListeners = CopyOnWriteArrayList<(RunningGame, Int, Long) -> Unit>()

    /** Called on the watcher thread when a game process ends, including quick crashes. */
    fun onExited(l: (RunningGame, Int, Long) -> Unit) { exitListeners.add(l) }

    class LaunchException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    /**
     * Prepares a launch without starting it: resolves metadata, picks Java, builds arguments.
     * Throws [LaunchException] with a message that can be shown to the player as-is.
     */
    fun prepare(instance: Instance, gameDirOverride: Path? = null): LaunchSpec {
        val account = Accounts.current() ?: throw LaunchException(
            "Add an account first. Offline profiles work without a Microsoft sign-in."
        )
        val gameDir = gameDirOverride ?: instance.gameDir
        Files.createDirectories(gameDir)
        val versionId = instance.versionId.ifBlank { throw LaunchException("This instance has no Minecraft version selected.") }
        val merged = try {
            Mojang.resolve(gameDir, versionId)
        } catch (e: Exception) {
            throw LaunchException("Minecraft $versionId is not installed yet. Open the instance and press Install.", e)
        }
        val requiredJava = merged.javaMajor
        val runtime = JavaRuntimes.pick(requiredJava, instance.javaPath)
            ?: throw LaunchException("Java $requiredJava is required but was not found. Install it from Settings → Java, or let the launcher download it.")

        val natives = Installer.nativesDir(gameDir, versionId)
        Files.createDirectories(natives)
        val libDir = gameDir.resolve("libraries")
        val clientJar = gameDir.resolve("versions").resolve(merged.jarVersion).resolve("${merged.jarVersion}.jar")
        if (!Files.isRegularFile(clientJar)) throw LaunchException("The Minecraft client jar for ${merged.jarVersion} is missing. Re-install the version.")
        val classpath = buildClasspath(merged, libDir, clientJar)

        val token = try {
            Accounts.ensureFreshToken(account)
        } catch (e: Exception) {
            throw LaunchException("Your Microsoft session expired and could not be refreshed. Sign in again.", e)
        }

        val assetIndex = merged.assetIndexId
        val assetsDir = gameDir.resolve("assets")
        val logFile = AppEnv.logsDir.resolve("games").resolve("${instance.id}-${System.currentTimeMillis()}.log")
        Files.createDirectories(logFile.parent)

        val vars = linkedMapOf(
            "auth_player_name" to account.username,
            "auth_uuid" to account.effectiveUuid().replace("-", ""),
            "auth_access_token" to token,
            "auth_session" to token,
            "auth_xuid" to (account.xuid ?: "0"),
            "clientid" to (account.xuid ?: ""),
            "user_type" to if (account.isMicrosoft) "msa" else "legacy",
            "user_properties" to "{}",
            "version_name" to versionId,
            "version_type" to merged.version.type,
            "game_directory" to gameDir.toAbsolutePath().toString(),
            "assets_root" to assetsDir.toAbsolutePath().toString(),
            "game_assets" to assetsDir.resolve("virtual").resolve(assetIndex).toAbsolutePath().toString(),
            "assets_index_name" to assetIndex,
            "natives_directory" to natives.toAbsolutePath().toString(),
            "library_directory" to libDir.toAbsolutePath().toString(),
            "classpath" to classpath,
            "classpath_separator" to VAR_CLASSPATH_SEPARATOR,
            "launcher_name" to "AerixLauncher",
            "launcher_version" to AppEnv.version
        )

        val memory = instance.effectiveMemoryMb()
        val jvmArgs = ArrayList<String>()
        jvmArgs.add("-Xmx${memory}M")
        jvmArgs.add("-Xms${(memory / 2).coerceAtLeast(512)}M")
        jvmArgs.add("-Djava.library.path=${natives.toAbsolutePath()}")
        jvmArgs.add("-Dminecraft.launcher.brand=AerixLauncher")
        jvmArgs.add("-Dminecraft.launcher.version=${AppEnv.version}")
        jvmArgs.add("-Dfile.encoding=UTF-8")
        if (Settings.bool("useSystemProxy", true).not()) {
            jvmArgs.add("-Djava.net.useSystemProxies=false")
        }
        merged.loggingFile?.let { logging ->
            val loggingFile = assetsDir.resolve("log_configs").resolve(logging.path ?: "client-logging")
            if (Files.isRegularFile(loggingFile) && merged.loggingArgument != null) {
                jvmArgs.add(merged.loggingArgument!!.replace("\${path}", loggingFile.toAbsolutePath().toString()))
            }
        }
        // user arguments (global + per instance)
        instance.effectiveJavaArgs().let { raw ->
            if (raw.isNotBlank()) jvmArgs.addAll(splitArguments(raw))
        }
        // metadata JVM args (natives dir, classpath, Forge/NeoForge -p/-D flags, module path, …)
        for (arg in merged.jvmArgs) {
            when (arg) {
                is JsonValue -> {
                    val s = arg.asString()
                    if (s != null) {
                        val expanded = substitute(s, vars)
                        if (s.startsWith("-Dorg.lwjgl.librarypath") || s.startsWith("-Djava.library.path")) continue
                        jvmArgs.add(expanded)
                    } else if (arg is JsonArray) {
                        val rule = JsonObject().put("rules", arg.let { it.items.firstOrNull()?.get("rules") ?: it })
                        // conditional argument: {"rules":[…], "value":"…"|["…"]}
                        val rules = arg.items.firstOrNull()?.get("rules") as? JsonArray
                        if (MetadataRules.allowed(rules)) {
                            val value = arg.items.firstOrNull()?.get("value")
                            if (value is JsonArray) value.strings().forEach { jvmArgs.add(substitute(it, vars)) }
                            else value?.asString()?.let { jvmArgs.add(substitute(it, vars)) }
                        }
                    }
                }
            }
        }
        jvmArgs.add(merged.mainClass.ifBlank { throw LaunchException("The version metadata has no main class.") })

        val gameArgs = ArrayList<String>()
        val legacy = merged.legacyArguments
        if (legacy != null) {
            gameArgs.addAll(splitArguments(substitute(legacy, vars)))
        } else {
            for (arg in merged.gameArgs) {
                val s = arg.asString()
                if (s != null) {
                    gameArgs.add(substitute(s, vars))
                } else if (arg is JsonObject) {
                    val rules = arg.arr("rules")
                    if (MetadataRules.allowed(rules)) {
                        val value = arg.get("value")
                        if (value is JsonArray) value.strings().forEach { gameArgs.add(substitute(it, vars)) }
                        else value?.asString()?.let { gameArgs.add(substitute(it, vars)) }
                    }
                }
            }
        }
        // Quick Play server (1.20+), mirroring GameRunner.appendServerAutoConnectArgs
        val server = Settings.string("www.${instance.id}.server", "").ifBlank { Settings.string("autoConnectServer", "") }
        if (server.isNotBlank()) {
            val isModern = supportsQuickPlay(merged)
            if (isModern) gameArgs.addAll(listOf("--quickPlayMultiplayer", server))
            else {
                gameArgs.add("--server")
                gameArgs.add(server.substringBefore(':'))
                gameArgs.add("--port")
                gameArgs.add(server.substringAfter(':', "25565"))
            }
        }
        if (Settings.bool("fullscreenOnLaunch", false)) gameArgs.add("--fullscreen")

        val command = ArrayList<String>()
        command.add(runtime.javaExe.toString())
        command.addAll(jvmArgs)
        command.addAll(gameArgs)
        return LaunchSpec(instance, account, runtime, command, classpath, natives, merged.mainClass, gameDir, logFile)
    }

    private fun supportsQuickPlay(merged: MergedVersion): Boolean {
        val id = merged.jarVersion
        val m = Regex("1\\.(\\d+)").find(id)
        if (m != null) {
            val minor = m.groupValues[1].toIntOrNull() ?: 0
            if (minor >= 20) return true
        }
        return false
    }

    /** Classpath: every library jar that is not a native, then the client jar. */
    fun buildClasspath(merged: MergedVersion, librariesDir: Path, clientJar: Path): String {
        val entries = ArrayList<String>()
        merged.libraries.forEach { lib ->
            val file = librariesDir.resolve(lib.relativePath)
            if (Files.isRegularFile(file)) entries.add(file.toAbsolutePath().toString())
        }
        entries.add(clientJar.toAbsolutePath().toString())
        return entries.joinToString(VAR_CLASSPATH_SEPARATOR)
    }

    /**
     * Starts the prepared launch. Returns immediately; [onExit] is called on a worker thread.
     */
    fun launch(
        spec: LaunchSpec,
        onLine: (String) -> Unit = {},
        onExit: (Int, Long) -> Unit = { _, _ -> }
    ): RunningGame {
        val argDir = AppEnv.cacheDir.resolve("launch")
        Files.createDirectories(argDir)
        val argFile = argDir.resolve("launch-${spec.instance.id}-${System.currentTimeMillis()}.txt")
        // Java argument files: one argument per line, backslashes must be escaped.
        val content = spec.command.drop(1).joinToString("\n") { arg ->
            arg.replace("\\", "\\\\").replace("\"", "\\\"")
        }
        Files.writeString(argFile, content, StandardCharsets.UTF_8)
        runCatching {
            argFile.toFile().setReadable(false, false); argFile.toFile().setReadable(true, true)
            argFile.toFile().setWritable(false, false); argFile.toFile().setWritable(true, true)
        }

        val builder = ProcessBuilder(spec.command.first(), "@${argFile.toAbsolutePath()}")
            .directory(spec.gameDir.toFile())
            .redirectErrorStream(true)
        // Minecraft's own log output on stdout/stderr
        Log.i("launch", "starting ${spec.instance.name} with ${spec.runtime.label} (${spec.instance.effectiveMemoryMb()} MB)")
        val process = try {
            builder.start()
        } catch (e: IOException) {
            Files.deleteIfExists(argFile)
            throw LaunchException("Could not start Java: ${e.message}", e)
        }
        val game = RunningGame(spec.instance, spec, process)
        running.add(game)
        listeners.forEach { runCatching { it(game) } }
        spec.instance.touchLaunch()

        Thread({
            val logWriter = runCatching {
                Files.newBufferedWriter(spec.logFile, StandardCharsets.UTF_8)
            }.getOrNull()
            logWriter?.use { writer ->
                writer.write("# Aerix Launcher ${AppEnv.version} — ${spec.instance.name} — ${spec.runtime.label}\n")
                writer.write("# started ${java.time.LocalDateTime.now()}\n")
                writer.flush()
                runCatching {
                    process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            val clean = line.replace(Regex("\\u001b\\[[;\\d]*m"), "")
                            Log.game(clean)
                            onLine(clean)
                            runCatching { writer.write(clean); writer.write("\n") }
                        }
                    }
                }
            }
            val exit = runCatching { process.waitFor() }.getOrDefault(-1)
            val seconds = (System.currentTimeMillis() - game.startedAt) / 1000
            spec.instance.addPlayTime(seconds)
            running.remove(game)
            runCatching { Files.deleteIfExists(argFile) }
            Log.i("launch", "${spec.instance.name} exited with code $exit after ${seconds}s")
            val updated = RunningGame(spec.instance, spec, process, game.startedAt)
            if (exit != 0 && seconds < 120) writeCrashSummary(updated, exit)
            runCatching { onExit(exit, seconds) }
            exitListeners.forEach { runCatching { it(game, exit, seconds) } }
        }, "game-watch-${spec.instance.id}").apply { isDaemon = true }.start()
        return game
    }

    /** Kills a running game gracefully (taskkill on Windows so the JVM gets a real termination). */
    fun stop(game: RunningGame) {
        val pid = runCatching { game.process.pid() }.getOrDefault(-1)
        Log.i("launch", "stopping ${game.instance.name} (pid $pid)")
        runCatching {
            if (AppEnv.isWindows && pid > 0) {
                ProcessBuilder("taskkill", "/PID", pid.toString()).start().waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            }
            if (game.process.isAlive) game.process.destroy()
            Thread.sleep(1500)
            if (game.process.isAlive) game.process.destroyForcibly()
        }
    }

    fun stopAll() { running.toList().forEach(::stop) }

    // ---- argument helpers --------------------------------------------------------------------

    /** Substitutes `${key}` placeholders, case-insensitively, leaving unknown ones untouched. */
    fun substitute(input: String, vars: Map<String, String>): String {
        if (!input.contains("\${")) return input
        val sb = StringBuilder(input.length + 64)
        var i = 0
        while (i < input.length) {
            val start = input.indexOf("\${", i)
            if (start < 0) { sb.append(input, i, input.length); break }
            sb.append(input, i, start)
            val end = input.indexOf('}', start)
            if (end < 0) { sb.append(input, start, input.length); break }
            val key = input.substring(start + 2, end)
            val value = vars[key]
            if (value != null) sb.append(value) else sb.append(input, start, end + 1)
            i = end + 1
        }
        return sb.toString()
    }

    /** Splits a JVM argument string respecting quotes, as `JREUtils.parseJavaArguments` does. */
    fun splitArguments(raw: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var quoteChar = '"'
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '"' || c == '\'' -> {
                    if (inQuotes && c == quoteChar) inQuotes = false
                    else if (!inQuotes) { inQuotes = true; quoteChar = c }
                    else sb.append(c)
                }
                c == '\\' && i + 1 < raw.length && raw[i + 1] == '"' -> { sb.append('"'); i++ }
                (c == ' ' || c == '\t') && !inQuotes -> {
                    if (sb.isNotEmpty()) { out.add(sb.toString()); sb.clear() }
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    /** Writes a launcher-side crash report next to the game log when the game dies quickly. */
    fun writeCrashSummary(game: RunningGame, exitCode: Int) {
        runCatching {
            val dir = game.spec.gameDir.resolve("crash-reports")
            Files.createDirectories(dir)
            val file = dir.resolve("aerix-launcher-${System.currentTimeMillis()}.txt")
            Files.writeString(
                file,
                "Aerix Launcher crash summary\n" +
                        "instance: ${game.instance.name}\n" +
                        "version: ${game.instance.versionId}\n" +
                        "java: ${game.spec.runtime.detail}\n" +
                        "exit code: $exitCode\n" +
                        "game log: ${game.spec.logFile}\n"
            )
        }
    }

    /** A world folder with the level name and size the library shows. */
    class World(val path: Path, val levelName: String?, val sizeBytes: Long)

    /** The worlds (`saves`) of an instance, newest first. */
    fun worlds(instance: Instance): List<Path> {
        val saves = instance.gameDir.resolve("saves")
        if (!Files.isDirectory(saves)) return emptyList()
        return Files.list(saves).use { s -> s.filter { Files.isDirectory(it) }.toList() }
            .sortedByDescending { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0) }
    }

    /**
     * Reads `level.dat` only far enough to pull the display name: the file is gzip-compressed NBT,
     * so the name is searched in the decompressed bytes rather than parsed into a tree. Sizes are a
     * shallow sum, which is what the library shows next to the date.
     */
    fun worldDetails(instance: Instance): List<World> = worlds(instance).map { dir ->
        val levelDat = dir.resolve("level.dat")
        var name: String? = null
        runCatching {
            if (Files.isRegularFile(levelDat)) {
                java.util.zip.GZIPInputStream(Files.newInputStream(levelDat)).use { input ->
                    val bytes = input.readNBytes(4096)
                    val text = String(bytes, Charsets.ISO_8859_1)
                    val idx = text.indexOf("LevelName")
                    if (idx >= 0) {
                        // tag: short length (big endian) then UTF-8 bytes
                        val lenAt = idx + "LevelName".length + 1
                        if (lenAt + 2 <= bytes.size) {
                            val len = ((bytes[lenAt].toInt() and 0xFF) shl 8) or (bytes[lenAt + 1].toInt() and 0xFF)
                            if (len in 1..255 && lenAt + 2 + len <= bytes.size) {
                                name = String(bytes, lenAt + 2, len, Charsets.UTF_8)
                            }
                        }
                    }
                }
            }
        }
        var size = 0L
        runCatching {
            Files.walk(dir, 3).use { walk -> walk.forEach { p -> if (Files.isRegularFile(p)) size += runCatching { Files.size(p) }.getOrDefault(0) } }
        }
        World(dir, name, size)
    }
}
