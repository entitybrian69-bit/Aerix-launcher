package net.kdt.pojavlaunch.aerix.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import java.io.File
import java.util.Properties

/**
 * Launcher half of the live client options.
 *
 * Everything the Aerix Client toggles is a plain options.txt value, and Minecraft only reads that
 * file while it starts. That is why Fullbright, the ZOOM button and the performance presets looked
 * like switches that did nothing until the next launch. The bundled bridge mod polls the files below
 * from inside the game instead, so a change lands on the render thread within a frame or two, and the
 * game's own values are handed back the moment a request is dropped.
 *
 * Files, all under the instance's `config/` folder:
 *  - `aerix_live.properties`           requests (gamma, zoomFov)
 *  - `aerix_server_boost.properties`   the Server Boost profile, applied only in multiplayer
 *  - `aerix_live_recovery.properties`   the game's pre-request values, replayed into options.txt
 *  - `aerix_status.properties`         what the game side actually managed to do
 */
object ClientLiveOptionsBridge {
    private const val LIVE = "aerix_live.properties"
    private const val BOOST = "aerix_server_boost.properties"
    private const val RECOVERY = "aerix_live_recovery.properties"
    private const val MAX_FILE_BYTES = 262_144L

    /** Instance the running game is using, set while a Client instance is on screen. */
    @JvmStatic
    @Volatile
    var activeGameDir: File? = null

    /** Latest `aerix_status.properties` the game wrote, for the Client menu to show. */
    var gameStatus by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    enum class BoostPreset(val label: String, val values: Map<String, String>) {
        LIGHT("Light", mapOf(
            "renderDistance" to "10", "simulationDistance" to "8", "entityDistanceScaling" to "0.75",
            "mipmapLevels" to "2", "particles" to "1", "entityShadows" to "true")),
        MEDIUM("Medium", mapOf(
            "renderDistance" to "8", "simulationDistance" to "6", "entityDistanceScaling" to "0.5",
            "mipmapLevels" to "2", "particles" to "1", "entityShadows" to "false")),
        EXTREME("Extreme", mapOf(
            "renderDistance" to "6", "simulationDistance" to "4", "entityDistanceScaling" to "0.5",
            "mipmapLevels" to "0", "particles" to "0", "entityShadows" to "false"))
    }

    /** Writes every request the store currently implies. Safe to call from any thread. */
    @JvmStatic
    @JvmOverloads
    fun syncAll(gameDir: File? = activeGameDir) {
        if (gameDir == null) return
        runCatching {
            writeLive(gameDir)
            writeBoost(gameDir)
        }
    }

    private fun writeLive(gameDir: File) {
        val lines = buildString {
            append("gamma=").append(if (AerixClientStore.fullbrightEnabled) "1.0" else "0.0").append('\n')
            append("zoomFov=").append(if (AerixClientInputBridge.zoomHolding) zoomTargetFov().toString() else "0").append('\n')
            append("boost=").append(if (AerixClientStore.serverBoostEnabled) "1" else "0").append('\n')
            append("updatedAt=").append(System.currentTimeMillis()).append('\n')
        }
        atomicWrite(File(gameDir, "config/$LIVE"), lines)
    }

    private fun writeBoost(gameDir: File) {
        val target = File(gameDir, "config/$BOOST")
        if (!AerixClientStore.serverBoostEnabled) {
            runCatching { if (target.isFile) target.delete() }
            return
        }
        val preset = BoostPreset.values().firstOrNull { it.name == AerixClientStore.serverBoostPreset }
            ?: BoostPreset.MEDIUM
        val lines = buildString {
            append("enabled=1\n")
            preset.values.forEach { (key, value) -> append(key).append('=').append(value).append('\n') }
            append("updatedAt=").append(System.currentTimeMillis()).append('\n')
        }
        atomicWrite(target, lines)
    }

    /** The FOV the ZOOM button asks for: the player's own field of view, narrowed by the slider. */
    fun zoomTargetFov(): Double {
        val base = runCatching { ClientPerformanceSettings.overrides["fov"]?.toDoubleOrNull() }.getOrNull() ?: 70.0
        val scale = LauncherPreferences.PREF_ZOOM_FOV_SCALE.takeIf { it > 0.05f && it < 1f } ?: 0.45f
        return (base * scale).coerceIn(30.0, 110.0)
    }

    /** Called when the ZOOM pill is pressed or released. */
    @JvmStatic
    fun setZoomHeld(gameDir: File?, held: Boolean) {
        if (gameDir == null) return
        runCatching {
            val file = File(gameDir, "config/$LIVE")
            val now = System.currentTimeMillis()
            val fov = if (held) zoomTargetFov().toString() else "0"
            atomicWrite(file, "gamma=${if (AerixClientStore.fullbrightEnabled) "1.0" else "0.0"}\n" +
                "zoomFov=$fov\nboost=${if (AerixClientStore.serverBoostEnabled) "1" else "0"}\nupdatedAt=$now\n")
        }
    }

    /**
     * Replays the values the game had before a live request changed them. Called before launch, so a
     * session that ended while boosted or zoomed cannot leave those numbers in the player's options.
     */
    fun restoreAtLaunch(gameDir: File) {
        val recovery = File(gameDir, "config/$RECOVERY")
        if (!recovery.isFile || recovery.length() > MAX_FILE_BYTES) return
        val values = runCatching {
            Properties().apply { recovery.inputStream().use { load(it) } }
                .entries.associate { (key, value) -> key.toString() to value.toString() }
        }.getOrNull().orEmpty()
        if (values.isEmpty()) { runCatching { recovery.delete() }; return }
        val options = File(gameDir, "options.txt")
        require(!options.exists() || options.length() <= 2_000_000L) { "Minecraft options file is too large" }
        val lines = if (options.isFile) options.readLines() else emptyList()
        atomicWrite(options, ClientOptionLines.merge(lines, values).joinToString("\n", postfix = "\n"))
        runCatching { recovery.delete() }
    }

    /** Reads the game side's report; [refreshGameStatus] publishes it to the UI. */
    @JvmStatic
    @JvmOverloads
    fun readGameStatus(gameDir: File? = activeGameDir): Map<String, String> =
        AerixClientModBridge.readStatus(gameDir)

    /** Re-reads the report; the Client menu calls this while it is open. */
    @JvmStatic
    @JvmOverloads
    fun refreshGameStatus(gameDir: File? = activeGameDir) {
        gameStatus = readGameStatus(gameDir)
    }

    /** One line a player can read: what the game side says about capes, zoom and boost. */
    @JvmStatic
    fun describeGameStatus(): String {
        val status = gameStatus
        if (status.isEmpty()) return "The game has not reported yet - start Minecraft to see live status here."
        return buildString {
            append("Bridge mod: ").append(AerixClientModBridge.lastStatus).append('\n')
            append("Capes: ").append(status["cape"] ?: "unknown").append('\n')
            append("Live options: ").append(status["live"] ?: "unknown")
            status["zoom"]?.let { append(" · zoom ").append(it) }
            status["boost"]?.let { append(" · boost ").append(it) }
            append(" · world ").append(status["world"] ?: "unknown")
        }
    }

    private fun atomicWrite(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        val stamp = maxOf(System.currentTimeMillis(), file.lastModified() + 1L)
        try {
            temp.writeText(text)
            check(temp.renameTo(file)) { "Could not write ${file.name}" }
            file.setLastModified(stamp)
        } finally {
            runCatching { if (temp.exists()) temp.delete() }
        }
    }
}
