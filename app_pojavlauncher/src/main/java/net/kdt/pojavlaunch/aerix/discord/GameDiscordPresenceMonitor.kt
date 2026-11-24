/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Live in-game Discord Rich Presence monitor: resolves modpack identity, server list names &
 * favicons, singleplayer worlds, and real-time player activity from the running Minecraft session.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.discord

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import net.kdt.pojavlaunch.aerix.modpacks.AerixModpackCatalog
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.worldshare.WorldSharePolicy
import net.kdt.pojavlaunch.instances.Instance
import net.kdt.pojavlaunch.recording.RecordingState
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.zip.GZIPInputStream

internal data class GameInstanceMeta(
    val instanceName: String?,
    val mcVersion: String?,
    val loaderName: String?,
    val modCount: Int,
    val modpackName: String?,
    val modpackIconUrl: String?,
    val modpackPageUrl: String?
)

internal enum class GameActivityStage {
    LAUNCHING,
    MAIN_MENU,
    LOADING_WORLD,
    IN_WORLD,
    CONNECTING_SERVER,
    IN_SERVER,
    WORLD_SHARE
}

internal data class LiveGameActivityState(
    val stage: GameActivityStage = GameActivityStage.LAUNCHING,
    val serverAddress: String? = null,
    val serverName: String? = null,
    val serverIconUrl: String? = null,
    val worldName: String? = null,
    val worldShareAddress: String? = null,
    val dimension: String? = null,
    val loadingPercent: Int? = null,
    val isRecording: Boolean = false
) {
    fun activityLine(serverNames: Map<String, String> = emptyMap()): String {
        val resolvedServer = displayServerName(serverNames)
        return when (stage) {
            GameActivityStage.LAUNCHING -> "Launching game"
            GameActivityStage.MAIN_MENU -> "In Main Menu"
            GameActivityStage.LOADING_WORLD -> loadingPercent?.let { "Loading world ($it%)" } ?: "Loading world"
            GameActivityStage.IN_WORLD -> {
                val base = worldName?.takeIf { it.isNotBlank() }?.let { "Singleplayer: $it" } ?: "Playing Singleplayer"
                if (!dimension.isNullOrBlank()) "$base ($dimension)" else base
            }
            GameActivityStage.CONNECTING_SERVER -> resolvedServer?.let { "Joining $it" } ?: "Joining server"
            GameActivityStage.IN_SERVER -> {
                val base = resolvedServer?.let { "Playing on $it" } ?: "Playing Multiplayer"
                if (!dimension.isNullOrBlank()) "$base ($dimension)" else base
            }
            GameActivityStage.WORLD_SHARE -> "Hosting World Share"
        }
    }

    fun displayServerName(serverNames: Map<String, String> = emptyMap()): String? {
        val raw = serverAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return serverName
        if (!serverName.isNullOrBlank()) return serverName
        val key = raw.lowercase(Locale.ROOT)
        serverNames[key]?.let { return it }
        serverNames[key.substringBefore(':')]?.let { return it }
        return if (GameDiscordPresenceMonitor.isLocalOrLanAddress(raw)) "LAN World" else raw
    }
}

/**
 * Tails `<gameDir>/logs/latest.log`, parses `servers.dat` custom server names, inspects the
 * active modpack/loader/mod-count, and pushes live Discord Rich Presence snapshots to `:launcher`.
 */
internal object GameDiscordPresenceMonitor {
    private const val POLL_INTERVAL_MS = 2_000L
    private const val MAX_CONNECTING_PROMOTE_MS = 4_000L
    private const val MAX_LOG_INITIAL_TAIL_BYTES = 512_000L
    private const val MAX_SERVERS_DAT_BYTES = 2 * 1024 * 1024L

    private val CONNECTING_REGEX = Regex("""Connecting to ([^,\s]+),\s*(\d+)""")
    private val PREPARING_LEVEL_REGEX = Regex("""Preparing level "([^"]+)"""")
    private val SERVER_LEVEL_REGEX = Regex("""ServerLevel\[([^\]]+)]""")
    private val SPAWN_PERCENT_REGEX = Regex("""Preparing spawn area:\s*(\d+)%""")
    private val ADVANCEMENTS_REGEX = Regex("""Loaded \d+ advancements""")
    private val FORMAT_CODE_REGEX = Regex("""§.""")

    private val lock = Any()
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null
    private var appContext: Context? = null
    private var gameDir: File? = null
    private var activeMeta: GameInstanceMeta? = null
    private var serverNamesCache: Map<String, String> = emptyMap()
    private var lastServersDatModified: Long = -1L
    private var logOffset: Long = 0L
    private var logPartial: String = ""
    private var discardPartialLine: Boolean = false
    private var tracker = LogActivityTracker()
    private var lastEmittedSignature: String? = null
    private var running = false
    private var paused = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            synchronized(lock) {
                if (!running || paused) return
            }
            runCatching { pollOnce() }
            synchronized(lock) {
                if (running && !paused) {
                    worker?.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }
    }

    fun extractMetadata(instance: Instance?, rawVersion: String?): GameInstanceMeta {
        val versionSource = rawVersion?.takeIf { it.isNotBlank() } ?: instance?.versionId
        val mcVersion = SocialBackend.extractMcVersion(versionSource)
            ?: SocialBackend.extractMcVersion(instance?.versionId)
            ?: versionSource?.takeIf { it.isNotBlank() }
        val loader = SocialBackend.detectLoader(versionSource ?: instance?.versionId)
        val modCount = countEnabledMods(instance?.gameDirectory)

        val aerixPackId = instance?.aerixPackId?.takeIf { it.isNotBlank() }
        val catalogPack = aerixPackId?.let { id ->
            AerixModpackCatalog.packs.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }

        val modpackName = when {
            catalogPack != null -> catalogPack.name
            aerixPackId != null -> instance.name?.takeIf { it.isNotBlank() }
                ?: aerixPackId.replace('-', ' ').replaceFirstChar { it.uppercase() }
            instance?.modpackSource.equals("modrinth", ignoreCase = true) ||
                instance?.modpackSource.equals("curseforge", ignoreCase = true) ||
                instance?.modpackSource.equals("aerix", ignoreCase = true) ||
                !instance?.modpackIconUrl.isNullOrBlank() -> instance?.name?.takeIf { it.isNotBlank() }
            instance?.isAerixClient == true -> "Aerix Client"
            !instance?.fastClientVersion.isNullOrBlank() -> "FastClient"
            else -> null
        }

        val modpackIconUrl = when {
            !instance?.modpackIconUrl.isNullOrBlank() -> instance?.modpackIconUrl
            catalogPack != null -> catalogPack.iconUrls.firstOrNull { it.startsWith("http") }
            aerixPackId != null ->
                "https://raw.githubusercontent.com/entitybrian69-bit/Aerix-launcher/v3_openjdk/modpacks/icons/$aerixPackId.png"
            instance?.isAerixClient == true || !instance?.fastClientVersion.isNullOrBlank() ->
                AerixDiscordPresence.AERIX_LOGO_URL
            else -> null
        }

        val modpackPageUrl = when {
            instance?.modpackSource.equals("modrinth", ignoreCase = true) &&
                !instance?.modpackProjectId.isNullOrBlank() ->
                "https://modrinth.com/modpack/${instance.modpackProjectId}"
            else -> null
        }

        return GameInstanceMeta(
            instanceName = instance?.name?.takeIf { it.isNotBlank() },
            mcVersion = mcVersion,
            loaderName = loader,
            modCount = modCount,
            modpackName = modpackName,
            modpackIconUrl = modpackIconUrl,
            modpackPageUrl = modpackPageUrl
        )
    }

    fun start(context: Context, instance: Instance?, rawVersion: String?, launchServer: String?): GameInstanceMeta {
        val app = context.applicationContext
        val meta = extractMetadata(instance, rawVersion)
        val dir = instance?.gameDirectory

        synchronized(lock) {
            appContext = app
            gameDir = dir
            activeMeta = meta
            serverNamesCache = dir?.let { readServerListNames(it) } ?: emptyMap()
            lastServersDatModified = dir?.let { File(it, "servers.dat").lastModified() } ?: -1L
            logOffset = 0L
            logPartial = ""
            discardPartialLine = false
            tracker = LogActivityTracker(serverNamesCache)
            if (!launchServer.isNullOrBlank()) {
                tracker.seedLaunchServer(launchServer)
            }
            lastEmittedSignature = null
            running = true
            paused = false

            if (workerThread == null || workerThread?.isAlive != true) {
                val thread = HandlerThread("AerixDiscordMonitor", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
                workerThread = thread
                worker = Handler(thread.looper)
            }
            worker?.removeCallbacks(pollRunnable)
            worker?.postDelayed(pollRunnable, POLL_INTERVAL_MS)
        }
        return meta
    }

    fun resume() {
        synchronized(lock) {
            if (!running) return
            paused = false
            worker?.removeCallbacks(pollRunnable)
            worker?.post(pollRunnable)
        }
    }

    fun pause() {
        synchronized(lock) {
            paused = true
            worker?.removeCallbacks(pollRunnable)
        }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            paused = false
            worker?.removeCallbacks(pollRunnable)
            workerThread?.quitSafely()
            workerThread = null
            worker = null
            lastEmittedSignature = null
        }
    }

    fun currentStateSnapshot(): LiveGameActivityState = synchronized(lock) {
        tracker.snapshot(RecordingState.isActive())
    }

    private fun pollOnce() {
        val dir: File
        val app: Context
        val meta: GameInstanceMeta
        synchronized(lock) {
            if (!running || paused) return
            app = appContext ?: return
            meta = activeMeta ?: return
            dir = gameDir ?: return
        }

        val serversFile = File(dir, "servers.dat")
        if (serversFile.isFile) {
            val mod = serversFile.lastModified()
            if (mod != lastServersDatModified) {
                val refreshed = readServerListNames(dir)
                synchronized(lock) {
                    serverNamesCache = refreshed
                    lastServersDatModified = mod
                    tracker.updateServerNames(refreshed)
                }
            }
        }

        val logFile = File(dir, "logs/latest.log")
        if (logFile.isFile) {
            readLogUpdates(logFile)
        }

        val state: LiveGameActivityState
        val shouldEmit: Boolean
        synchronized(lock) {
            if (!running || paused) return
            tracker.tick(System.currentTimeMillis())
            state = tracker.snapshot(RecordingState.isActive())
            val sig = listOf(
                state.stage.name,
                state.serverAddress.orEmpty(),
                state.serverName.orEmpty(),
                state.serverIconUrl.orEmpty(),
                state.worldName.orEmpty(),
                state.worldShareAddress.orEmpty(),
                state.dimension.orEmpty(),
                state.loadingPercent?.toString().orEmpty(),
                state.isRecording.toString()
            ).joinToString("|")
            shouldEmit = sig != lastEmittedSignature
            if (shouldEmit) {
                lastEmittedSignature = sig
            }
        }

        if (shouldEmit) {
            AerixDiscordPresence.sendInGameActivityUpdate(app, meta, state)
        }
    }

    private fun readLogUpdates(logFile: File) {
        runCatching {
            RandomAccessFile(logFile, "r").use { raf ->
                val size = raf.length()
                if (size < logOffset) {
                    logOffset = 0L
                    logPartial = ""
                    discardPartialLine = false
                }
                if (logOffset == 0L && size > MAX_LOG_INITIAL_TAIL_BYTES) {
                    logOffset = size - MAX_LOG_INITIAL_TAIL_BYTES
                    discardPartialLine = true
                }
                if (size <= logOffset) return
                raf.seek(logOffset)
                val toRead = (size - logOffset).coerceAtMost(131_072L).toInt()
                val buf = ByteArray(toRead)
                val count = raf.read(buf)
                if (count <= 0) return
                logOffset += count
                val chunk = logPartial + String(buf, 0, count, Charsets.UTF_8)
                val lines = chunk.split('\n')
                val now = System.currentTimeMillis()
                synchronized(lock) {
                    for (i in 0 until lines.size - 1) {
                        val line = lines[i].trimEnd('\r')
                        if (discardPartialLine) {
                            discardPartialLine = false
                        } else {
                            tracker.onLogLine(line, now)
                        }
                    }
                }
                logPartial = lines.last()
                if (logPartial.length > 8192) {
                    logPartial = ""
                    discardPartialLine = true
                }
            }
        }
    }

    internal class LogActivityTracker(
        private var serverNames: Map<String, String> = emptyMap()
    ) {
        private var stage: GameActivityStage = GameActivityStage.LAUNCHING
        private var serverAddress: String? = null
        private var serverName: String? = null
        private var serverIconUrl: String? = null
        private var worldName: String? = null
        private var worldShareAddress: String? = null
        private var dimension: String? = null
        private var loadingPercent: Int? = null
        private var connectingAtMs: Long = 0L

        fun updateServerNames(names: Map<String, String>) {
            serverNames = names
            serverAddress?.let { addr ->
                serverName = resolveServerName(addr, names)
            }
        }

        fun seedLaunchServer(rawAddress: String) {
            val clean = normalizeServerAddress(rawAddress) ?: return
            stage = GameActivityStage.CONNECTING_SERVER
            serverAddress = clean
            serverName = resolveServerName(clean, serverNames)
            serverIconUrl = serverIconUrlFor(clean)
            connectingAtMs = System.currentTimeMillis()
        }

        fun onLogLine(line: String, nowMs: Long = System.currentTimeMillis()) {
            if (line.isBlank()) return

            WorldSharePolicy.addressEvent(line)?.let { addr ->
                worldShareAddress = addr
                stage = GameActivityStage.WORLD_SHARE
                return
            }
            if (WorldSharePolicy.endedEvent(line)) {
                worldShareAddress = null
                if (stage == GameActivityStage.WORLD_SHARE) {
                    stage = if (!worldName.isNullOrBlank()) GameActivityStage.IN_WORLD else GameActivityStage.MAIN_MENU
                }
                return
            }

            CONNECTING_REGEX.find(line)?.let { match ->
                val host = match.groupValues[1].trimEnd('.')
                val port = match.groupValues[2]
                val combined = if (port == "25565") host else "$host:$port"
                val clean = normalizeServerAddress(combined)
                if (clean != null) {
                    stage = GameActivityStage.CONNECTING_SERVER
                    serverAddress = clean
                    serverName = resolveServerName(clean, serverNames)
                    serverIconUrl = serverIconUrlFor(clean)
                    worldName = null
                    dimension = null
                    loadingPercent = null
                    connectingAtMs = nowMs
                }
                return
            }

            if (line.contains("Failed to connect to the server") ||
                line.contains("Disconnected from server") ||
                line.contains("Lost connection:") ||
                line.contains("Disconnecting from server") ||
                line.contains("Couldn't connect to server") ||
                line.contains("Stopping singleplayer server") ||
                line.contains("Stopping integrated server")
            ) {
                stage = GameActivityStage.MAIN_MENU
                serverAddress = null
                serverName = null
                serverIconUrl = null
                worldName = null
                dimension = null
                loadingPercent = null
                connectingAtMs = 0L
                return
            }

            if (line.contains("Starting integrated minecraft server")) {
                stage = GameActivityStage.LOADING_WORLD
                serverAddress = null
                serverName = null
                serverIconUrl = null
                dimension = null
                loadingPercent = 0
                return
            }

            PREPARING_LEVEL_REGEX.find(line)?.let { match ->
                worldName = match.groupValues[1].trim().takeIf { it.isNotEmpty() }
                if (stage == GameActivityStage.LAUNCHING || stage == GameActivityStage.MAIN_MENU) {
                    stage = GameActivityStage.LOADING_WORLD
                }
            }

            SERVER_LEVEL_REGEX.find(line)?.let { match ->
                val level = match.groupValues[1].trim()
                if (level.isNotEmpty()) {
                    worldName = level
                    if (serverAddress == null && worldShareAddress == null) {
                        stage = GameActivityStage.IN_WORLD
                        loadingPercent = null
                    }
                }
            }

            SPAWN_PERCENT_REGEX.find(line)?.let { match ->
                val pct = match.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
                if (pct != null && serverAddress == null) {
                    loadingPercent = pct
                    stage = if (pct >= 100) GameActivityStage.IN_WORLD else GameActivityStage.LOADING_WORLD
                }
            }

            if (ADVANCEMENTS_REGEX.containsMatchIn(line) || line.contains("joined the game")) {
                loadingPercent = null
                stage = when {
                    worldShareAddress != null -> GameActivityStage.WORLD_SHARE
                    serverAddress != null -> GameActivityStage.IN_SERVER
                    else -> GameActivityStage.IN_WORLD
                }
                return
            }

            if (stage == GameActivityStage.IN_WORLD || stage == GameActivityStage.IN_SERVER) {
                when {
                    line.contains("minecraft:the_nether") -> dimension = "The Nether"
                    line.contains("minecraft:the_end") -> dimension = "The End"
                    line.contains("minecraft:overworld") -> dimension = "Overworld"
                }
            }

            if (stage == GameActivityStage.LAUNCHING &&
                (line.contains("OpenAL initialized") ||
                    line.contains("Sound engine started") ||
                    line.contains("textures-atlas"))
            ) {
                stage = GameActivityStage.MAIN_MENU
            }
        }

        fun tick(nowMs: Long) {
            if (stage == GameActivityStage.CONNECTING_SERVER &&
                connectingAtMs > 0L &&
                nowMs - connectingAtMs >= MAX_CONNECTING_PROMOTE_MS
            ) {
                stage = GameActivityStage.IN_SERVER
            }
        }

        fun snapshot(isRecording: Boolean): LiveGameActivityState = LiveGameActivityState(
            stage = stage,
            serverAddress = serverAddress,
            serverName = serverName,
            serverIconUrl = serverIconUrl,
            worldName = worldName,
            worldShareAddress = worldShareAddress,
            dimension = dimension,
            loadingPercent = loadingPercent,
            isRecording = isRecording
        )
    }

    private fun resolveServerName(address: String, names: Map<String, String>): String? {
        val lower = address.lowercase(Locale.ROOT)
        return names[lower] ?: names[lower.substringBefore(':')]
    }

    fun normalizeServerAddress(raw: String?): String? {
        val trimmed = raw?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() } ?: return null
        return if (trimmed.endsWith(":25565")) trimmed.removeSuffix(":25565") else trimmed
    }

    fun isLocalOrLanAddress(rawAddress: String?): Boolean {
        val addr = rawAddress?.trim()?.lowercase(Locale.ROOT) ?: return true
        val host = if (addr.startsWith("[")) {
            addr.substringAfter('[').substringBefore(']')
        } else {
            addr.substringBefore(':')
        }
        return host.isEmpty() ||
            host == "localhost" ||
            host == "0.0.0.0" ||
            host == "::1" ||
            host.startsWith("127.") ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            host.endsWith(".local") ||
            host.endsWith(".lan") ||
            host.endsWith(".internal") ||
            is172Private(host)
    }

    private fun is172Private(host: String): Boolean {
        if (!host.startsWith("172.")) return false
        val second = host.removePrefix("172.").substringBefore('.').toIntOrNull() ?: return false
        return second in 16..31
    }

    /** Public server favicon PNG endpoint for Discord's `assets.large_image` proxy. */
    fun serverIconUrlFor(rawAddress: String?): String? {
        val normalized = normalizeServerAddress(rawAddress) ?: return null
        if (isLocalOrLanAddress(normalized)) return null
        if (normalized.any { it.isWhitespace() || it == '/' || it == '?' || it == '#' }) return null
        val url = "https://api.mcsrvstat.us/icon/$normalized"
        return url.takeIf { it.length <= 256 }
    }

    private fun countEnabledMods(gameDir: File?): Int {
        if (gameDir == null) return 0
        val modsDir = File(gameDir, "mods")
        if (!modsDir.isDirectory) return 0
        return runCatching {
            modsDir.listFiles()?.count { it.isFile && it.name.lowercase(Locale.US).endsWith(".jar") } ?: 0
        }.getOrDefault(0)
    }

    /**
     * Lightweight, bounds-checked NBT reader for Minecraft's `servers.dat` so named servers
     * (e.g. `"Hypixel"` -> `mc.hypixel.net`) appear with their friendly title instead of raw IP.
     */
    fun readServerListNames(gameDir: File): Map<String, String> {
        val file = File(gameDir, "servers.dat")
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_SERVERS_DAT_BYTES) return emptyMap()
        return runCatching {
            val rawStream = BufferedInputStream(FileInputStream(file))
            rawStream.mark(2)
            val b0 = rawStream.read()
            val b1 = rawStream.read()
            rawStream.reset()
            val input = if (b0 == 0x1F && b1 == 0x8B) {
                DataInputStream(GZIPInputStream(rawStream))
            } else {
                DataInputStream(rawStream)
            }
            input.use { dis ->
                val rootType = dis.readUnsignedByte()
                if (rootType != TAG_COMPOUND) return emptyMap()
                dis.readNbtString() // root name
                val result = LinkedHashMap<String, String>()
                readCompound(dis, depth = 0) { tagName, tagType ->
                    if (tagName == "servers" && tagType == TAG_LIST) {
                        val elemType = dis.readUnsignedByte()
                        val count = dis.readInt().coerceIn(0, 500)
                        for (i in 0 until count) {
                            if (elemType == TAG_COMPOUND) {
                                var entryName: String? = null
                                var entryIp: String? = null
                                readCompound(dis, depth = 1) { field, fieldType ->
                                    when {
                                        field == "name" && fieldType == TAG_STRING ->
                                            entryName = dis.readNbtString()
                                        field == "ip" && fieldType == TAG_STRING ->
                                            entryIp = dis.readNbtString()
                                        else -> skipNbtPayload(dis, fieldType, depth = 2)
                                    }
                                }
                                val cleanName = entryName
                                    ?.replace(FORMAT_CODE_REGEX, "")
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() }
                                val cleanIp = normalizeServerAddress(entryIp)?.lowercase(Locale.ROOT)
                                if (cleanName != null && cleanIp != null) {
                                    result[cleanIp] = cleanName
                                    result[cleanIp.substringBefore(':')] = cleanName
                                }
                            } else {
                                skipNbtPayload(dis, elemType, depth = 1)
                            }
                        }
                    } else {
                        skipNbtPayload(dis, tagType, depth = 1)
                    }
                }
                result
            }
        }.getOrDefault(emptyMap())
    }

    private const val TAG_END = 0
    private const val TAG_BYTE = 1
    private const val TAG_SHORT = 2
    private const val TAG_INT = 3
    private const val TAG_LONG = 4
    private const val TAG_FLOAT = 5
    private const val TAG_DOUBLE = 6
    private const val TAG_BYTE_ARRAY = 7
    private const val TAG_STRING = 8
    private const val TAG_LIST = 9
    private const val TAG_COMPOUND = 10
    private const val TAG_INT_ARRAY = 11
    private const val TAG_LONG_ARRAY = 12

    private fun DataInputStream.readNbtString(): String {
        val len = readUnsignedShort()
        val bytes = ByteArray(len)
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private inline fun readCompound(
        dis: DataInputStream,
        depth: Int,
        onTag: (name: String, type: Int) -> Unit
    ) {
        if (depth > 16) throw EOFException("NBT depth limit exceeded")
        while (true) {
            val type = dis.readUnsignedByte()
            if (type == TAG_END) break
            val name = dis.readNbtString()
            onTag(name, type)
        }
    }

    private fun skipNbtPayload(dis: DataInputStream, type: Int, depth: Int) {
        if (depth > 16) throw EOFException("NBT depth limit exceeded")
        when (type) {
            TAG_END -> Unit
            TAG_BYTE -> dis.skipBytes(1)
            TAG_SHORT -> dis.skipBytes(2)
            TAG_INT, TAG_FLOAT -> dis.skipBytes(4)
            TAG_LONG, TAG_DOUBLE -> dis.skipBytes(8)
            TAG_BYTE_ARRAY -> {
                val len = dis.readInt().coerceAtLeast(0)
                dis.skipBytes(len)
            }
            TAG_STRING -> {
                val len = dis.readUnsignedShort()
                dis.skipBytes(len)
            }
            TAG_LIST -> {
                val elemType = dis.readUnsignedByte()
                val count = dis.readInt().coerceIn(0, 10_000)
                for (i in 0 until count) {
                    skipNbtPayload(dis, elemType, depth + 1)
                }
            }
            TAG_COMPOUND -> readCompound(dis, depth + 1) { _, childType ->
                skipNbtPayload(dis, childType, depth + 1)
            }
            TAG_INT_ARRAY -> {
                val len = dis.readInt().coerceAtLeast(0)
                dis.skipBytes(len * 4)
            }
            TAG_LONG_ARRAY -> {
                val len = dis.readInt().coerceAtLeast(0)
                dis.skipBytes(len * 8)
            }
        }
    }
}
