package net.kdt.pojavlaunch.aerix.client

import java.io.File
import java.util.Properties
import kotlin.math.floor

data class ClientTelemetrySnapshot(
    val x: Double, val y: Double, val z: Double, val yaw: Float?, val health: Float?, val maxHealth: Float?,
    val dimension: String, val dayTime: Long?, val server: String?, val ping: Int?, val light: Int?,
    val armor: List<String>, val held: String?, val offhand: String?, val effects: List<String>
) {
    val blockX get() = floor(x).toInt()
    val blockY get() = floor(y).toInt()
    val blockZ get() = floor(z).toInt()
}

/** Opt-in, local-file IPC with the bundled Fabric mod. Data is never uploaded. */
object ClientTelemetryBridge {
    fun setEnabled(gameDir: File, enabled: Boolean) {
        val marker = File(gameDir, "config/aerix_telemetry.enabled")
        if (enabled) {
            marker.parentFile?.mkdirs()
            if (!marker.exists()) {
                File(gameDir, "config/aerix_telemetry.properties").delete()
                marker.writeText("1")
            }
        } else {
            marker.delete()
            File(gameDir, "config/aerix_telemetry.properties").delete()
        }
    }

    fun read(gameDir: File, now: Long = System.currentTimeMillis()): ClientTelemetrySnapshot? = runCatching {
        val file = File(gameDir, "config/aerix_telemetry.properties")
        if (!file.isFile || now - file.lastModified() > 5_000L) return null
        val p = Properties().apply { file.inputStream().use { load(it) } }
        if (p.getProperty("available") != "true") return null
        val x = p.getProperty("x")?.toDoubleOrNull() ?: return null
        val y = p.getProperty("y")?.toDoubleOrNull() ?: return null
        val z = p.getProperty("z")?.toDoubleOrNull() ?: return null
        fun item(prefix: String): String? {
            val count = p.getProperty("${prefix}Count")?.toIntOrNull() ?: return null
            if (count <= 0) return null
            val max = p.getProperty("${prefix}Max")?.toIntOrNull() ?: 0
            val damage = p.getProperty("${prefix}Damage")?.toIntOrNull() ?: 0
            val name = p.getProperty("${prefix}Name")?.takeIf { it.isNotBlank() } ?: prefix.replaceFirstChar { it.uppercase() }
            return if (max > 0) "$name ${((max - damage).coerceAtLeast(0) * 100 / max)}%" else "$name ×$count"
        }
        val effects = (0 until (p.getProperty("effects")?.toIntOrNull() ?: 0).coerceIn(0, 12)).mapNotNull { i ->
            val name = p.getProperty("effect${i}Name")?.substringAfterLast('.')?.replace('_', ' ') ?: return@mapNotNull null
            val ticks = p.getProperty("effect${i}Ticks")?.toIntOrNull() ?: return@mapNotNull null
            val duration = ticks.coerceAtLeast(0) / 20
            val level = (p.getProperty("effect${i}Level")?.toIntOrNull() ?: 0) + 1
            val time = if (ticks < 0) "∞" else "${duration / 60}:${(duration % 60).toString().padStart(2, '0')}"
            "$name $level $time"
        }
        ClientTelemetrySnapshot(x, y, z, p.getProperty("yaw")?.toFloatOrNull(), p.getProperty("health")?.toFloatOrNull(), p.getProperty("maxHealth")?.toFloatOrNull(),
            p.getProperty("dimension", "unknown"), p.getProperty("dayTime")?.toLongOrNull(),
            p.getProperty("server"), p.getProperty("ping")?.toIntOrNull(), p.getProperty("light")?.toIntOrNull(), (0..3).mapNotNull { item("armor$it") }, item("held"), item("offhand"), effects)
    }.getOrNull()
}
