package net.kdt.aerix.desktop.app

import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Launcher logging, replacing the Android `Log` calls and the LoggerView.
 *
 * Lines land in memory (bounded, for the in-app Console), in a rotating text file and — for the
 * lines that matter — in the log screen. Everything is redacted on the way in: Minecraft access
 * tokens, refresh tokens and CurseForge keys must never reach a log file, a crash report or a
 * screenshot of the console, so the same redaction runs for every sink.
 */
object Log {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    class Line(
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
        val time: Long = System.currentTimeMillis(),
        val game: Boolean = false
    ) {
        val rendered: String by lazy {
            val ts = TIME.format(java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()))
            buildString {
                append(ts).append(' ').append(level.name.first()).append('/').append(tag).append(": ").append(message)
                if (throwable != null) {
                    append('\n').append(StringWriter().also { throwable.printStackTrace(PrintWriter(it)) })
                }
            }
        }
    }

    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val STAMP = DateTimeFormatter.ofPattern("HH:mm:ss")
    private const val MAX_MEMORY_LINES = 4000

    private val buffer = LinkedBlockingQueue<Line>(MAX_MEMORY_LINES)
    private val listeners = CopyOnWriteArrayList<(Line) -> Unit>()
    private val lineCount = AtomicLong()
    private var dir: Path? = null
    private var stream: java.io.BufferedWriter? = null
    private var currentDay: String = ""
    private val closed = AtomicBoolean(false)

    @Volatile var minimum: Level = Level.INFO

    fun init(logDir: Path, level: Level = Level.INFO) {
        minimum = level
        dir = logDir
        runCatching { Files.createDirectories(logDir) }
        rotateIfNeeded(force = true)
        val hook = Thread { flushNow() }
        Runtime.getRuntime().addShutdownHook(hook)
        Info("log", "launcher log started at $logDir")
    }

    fun d(tag: String, message: String) = write(Level.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = Info(tag, message)
    fun w(tag: String, message: String, error: Throwable? = null) = write(Level.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write(Level.ERROR, tag, message, error)

    /** Kept for the Android-named call sites so the port reads the same. */
    fun Info(tag: String, message: String) = write(Level.INFO, tag, message, null)

    /** A line from the Minecraft process, shown in the Console with a `[game]` tag. */
    fun game(line: String) {
        val l = Line(Level.INFO, "game", line, game = true)
        push(l)
    }

    fun addListener(listener: (Line) -> Unit) { listeners.add(listener) }
    fun removeListener(listener: (Line) -> Unit) { listeners.remove(listener) }
    fun history(): List<Line> = buffer.toList()
    fun clearHistory() { buffer.clear() }

    fun logFile(): Path? = dir?.resolve("launcher-${FILE_TIME.format(LocalDateTime.now())}.log")

    /** All launcher and game log files, newest first, for the "share logs" action. */
    fun allFiles(): List<Path> =
        dir?.let { d -> runCatching { Files.list(d).use { s -> s.filter { Files.isRegularFile(it) }.toList() } }.getOrDefault(emptyList()) }
            ?.sortedByDescending { runCatching { Files.getLastModifiedTime(it) }.getOrDefault(java.nio.file.attribute.FileTime.fromMillis(0)) }
            ?: emptyList()

    private fun write(level: Level, tag: String, message: String, error: Throwable?) {
        if (level.ordinal < minimum.ordinal) return
        push(Line(level, tag, Redact.apply(message), error?.let { Redact.throwable(it) }))
    }

    private fun push(line: Line) {
        if (closed.get()) return
        buffer.offer(line)
        while (buffer.size > MAX_MEMORY_LINES) buffer.poll()
        lineCount.incrementAndGet()
        persist(line)
        listeners.forEach { runCatching { it(line) } }
        if (line.level == Level.ERROR && !line.game) {
            System.err.println(line.rendered)
        }
    }

    private fun persist(line: Line) {
        synchronized(this) {
            rotateIfNeeded(false)
            runCatching { stream?.write(line.rendered); stream?.write("\n"); stream?.flush() }
        }
    }

    private fun rotateIfNeeded(force: Boolean) {
        val d = dir ?: return
        val day = FILE_TIME.format(LocalDateTime.now())
        if (!force && day == currentDay && stream != null) return
        runCatching { stream?.flush(); stream?.close() }
        currentDay = day
        runCatching {
            Files.createDirectories(d)
            val f = d.resolve("launcher-$day.log")
            val size = if (Files.exists(f)) Files.size(f) else 0L
            stream = Files.newBufferedWriter(
                f, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE
            )
            if (size == 0L) {
                stream?.write("# Aerix Launcher ${AppEnv.version} - ${System.getProperty("os.name")} ${System.getProperty("os.version")} " +
                        "(${System.getProperty("os.arch")}), Java ${System.getProperty("java.version")}\n")
            }
        }
        pruneOldLogs(d)
    }

    private fun pruneOldLogs(dir: Path) {
        runCatching {
            val keepDays = Settings.int("launcherLogRetentionDays", 14).coerceIn(1, 90)
            val cutoff = java.time.Instant.now().minusSeconds(keepDays * 86400L)
            Files.list(dir).use { s ->
                s.filter { Files.isRegularFile(it) }.forEach { f ->
                    val modified = runCatching { Files.getLastModifiedTime(f).toInstant() }.getOrNull() ?: return@forEach
                    if (modified.isBefore(cutoff)) runCatching { Files.deleteIfExists(f) }
                }
            }
        }
    }

    fun flushNow() {
        synchronized(this) {
            runCatching { stream?.flush() }
        }
    }

    fun shutdown() {
        closed.set(true)
        synchronized(this) {
            runCatching { stream?.flush(); stream?.close() }
            stream = null
        }
    }

    /** Convenience for the Console: one timestamped plain-text line. */
    fun plain(line: Line): String =
        if (line.game) line.message else "${STAMP.format(java.time.Instant.ofEpochMilli(line.time).atZone(java.time.ZoneId.systemDefault()))} ${line.message}"
}

/**
 * Redacts anything that looks like a credential before it can be written anywhere.
 *
 * Patterns are deliberately broad: a false positive only shortens a log line, while a missed token
 * would sit in a file the user may share with a bug report.
 */
object Redact {
    private val Q = "\u0022"
    private val patterns: List<Pair<Regex, String>> = listOf(
        Regex("($Q(?:access_token|refresh_token|client_secret|identityToken|xstsToken|Authorization)$Q\\s*:\\s*$Q)[^$Q]+($Q)", RegexOption.IGNORE_CASE) to "$1<redacted>$2",
        Regex("(access_token=)[^&\\s$Q]+", RegexOption.IGNORE_CASE) to "$1<redacted>",
        Regex("(refresh_token=)[^&\\s$Q]+", RegexOption.IGNORE_CASE) to "$1<redacted>",
        Regex("(XBL3\\.0 x=)[^\\s$Q]+") to "$1<redacted>",
        Regex("(bearer\\s+)[A-Za-z0-9._\\-]+", RegexOption.IGNORE_CASE) to "$1<redacted>",
        Regex("--accessToken\\s+\\S+") to "--accessToken <redacted>",
        Regex("(x-api-key[^A-Za-z0-9]{1,3})[A-Za-z0-9$\\-]{8,}", RegexOption.IGNORE_CASE) to "$1<redacted>"
    )

    fun apply(text: String): String {
        if (text.length > 200_000) return text.take(200_000) + "…"
        var out = text
        patterns.forEach { (re, repl) -> out = re.replace(out, repl) }
        return out
    }

    fun throwable(t: Throwable): Throwable {
        // Wrap rather than mutate: the stack trace is printed through the same redaction.
        return RedactedThrowable(t)
    }

    private class RedactedThrowable(cause: Throwable) : Throwable(apply(cause.toString()), cause) {
        override fun printStackTrace(w: PrintWriter) {
            w.println(apply(toString()))
            cause?.let { c -> for (el in c.stackTrace) w.println("\tat $el") }
        }
    }
}

/** Where the program keeps its own data. Replaces the Android constants in `Tools`. */
object AppEnv {
    const val APP_NAME = "Aerix Launcher"
    var version: String = "dev"
        internal set
    var buildChannel: String = "desktop"
        internal set

    val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")
    val isMac: Boolean = System.getProperty("os.name").lowercase().contains("mac")
    val home: Path = java.nio.file.Paths.get(System.getProperty("user.home"))

    /** `%APPDATA%\Aerix Launcher` (settings, accounts, skins, logs). */
    val appData: Path by lazy {
        val override = System.getenv("AERIX_APPDATA")
        when {
            !override.isNullOrBlank() -> java.nio.file.Paths.get(override)
            isWindows -> java.nio.file.Paths.get(System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").toString()).resolve("Aerix Launcher")
            isMac -> home.resolve("Library/Application Support/Aerix Launcher")
            else -> (System.getenv("XDG_CONFIG_HOME")?.let { java.nio.file.Paths.get(it) } ?: home.resolve(".config")).resolve("aerix-launcher")
        }
    }

    /** The game home: versions, libraries, assets and instances. */
    val defaultGameDir: Path by lazy {
        val override = System.getenv("AERIX_GAME_DIR")
        when {
            !override.isNullOrBlank() -> java.nio.file.Paths.get(override)
            isWindows -> home.resolve("Aerix")
            else -> home.resolve("Aerix")
        }
    }

    val logsDir: Path get() = appData.resolve("logs")
    val accountsDir: Path get() = appData.resolve("accounts")
    val skinsDir: Path get() = appData.resolve("skins")
    val runtimesDir: Path get() = appData.resolve("runtimes")
    val cacheDir: Path get() = appData.resolve("cache")

    fun ensureDirs() {
        listOf(appData, logsDir, accountsDir, skinsDir, runtimesDir, cacheDir).forEach {
            runCatching { Files.createDirectories(it) }
        }
    }

    /** Physical memory in MiB, the cap for the RAM slider (`PREF_SYSMEM` on Android). */
    val totalMemoryMb: Int by lazy {
        // java.lang.management is the `java.management` module. A `by lazy` that throws would
        // propagate out of every later read, so a runtime image without it must degrade to the
        // heap size rather than take the launcher (or the RAM slider) down.
        val physical = runCatching {
            val osBean = (java.lang.management.ManagementFactory.getOperatingSystemMXBean()
                    as? com.sun.management.OperatingSystemMXBean)
            osBean?.totalMemorySize
        }.getOrNull() ?: Runtime.getRuntime().maxMemory()
        (physical / (1024 * 1024)).toInt()
    }

    /** Free physical memory in MiB, as reported by the OS. */
    fun freeMemoryMb(): Int = runCatching {
        val osBean = (java.lang.management.ManagementFactory.getOperatingSystemMXBean()
                as? com.sun.management.OperatingSystemMXBean)
        val free = osBean?.freeMemorySize ?: Runtime.getRuntime().freeMemory()
        (free / (1024 * 1024)).toInt()
    }.getOrDefault(0)
}
