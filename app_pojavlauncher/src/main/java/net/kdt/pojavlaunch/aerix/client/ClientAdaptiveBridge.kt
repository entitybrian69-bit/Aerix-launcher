package net.kdt.pojavlaunch.aerix.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Properties

/** Local requests to the Fabric bridge and crash-safe recovery of the pre-adaptive option. */
object ClientAdaptiveBridge {
    var status by mutableStateOf("Off")
    var recovering by mutableStateOf(false)
    private fun recoveryFile(dir: File) = File(dir, "config/aerix_adaptive_recovery.properties")

    private fun atomicWrite(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        val time = maxOf(System.currentTimeMillis(), file.lastModified() + 1)
        try {
            temp.writeText(text)
            check(temp.renameTo(file)) { "Could not save adaptive distance" }
            file.setLastModified(time)
        } finally { temp.delete() }
    }

    fun request(gameDir: File, distance: Int) {
        atomicWrite(File(gameDir, "config/aerix_adaptive_distance.properties"), "distance=${distance.coerceIn(-1, 64)}\n")
    }

    fun saveRecovery(gameDir: File, original: Int) {
        require(original in 2..64)
        atomicWrite(recoveryFile(gameDir), "original=$original\n")
    }

    fun clearRecovery(gameDir: File) {
        val file = recoveryFile(gameDir)
        check(!file.exists() || file.delete()) { "Could not clear adaptive recovery" }
    }

    /** Before game startup, undo a value the game may have persisted during an interrupted session. */
    fun restoreAtLaunch(gameDir: File) {
        val recovery = recoveryFile(gameDir)
        if (recovery.isFile) {
            val props = Properties().apply { recovery.inputStream().use { load(it) } }
            val original = props.getProperty("original")?.toIntOrNull()
            require(original != null && original in 2..64) { "Invalid adaptive recovery file" }
            val options = File(gameDir, "options.txt")
            require(!options.exists() || options.length() <= 2_000_000L) { "Minecraft options file is too large" }
            val lines = if (options.isFile) options.readLines() else emptyList()
            val restored = ClientOptionLines.merge(lines, mapOf("renderDistance" to original.toString()))
            atomicWrite(options, restored.joinToString("\n", postfix = "\n"))
            clearRecovery(gameDir)
        }
        request(gameDir, -1)
    }
}
