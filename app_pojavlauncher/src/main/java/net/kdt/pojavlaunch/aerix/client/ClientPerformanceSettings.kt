package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

/** Explicit user-selected overrides, applied before Minecraft starts (not while it is running). */
object ClientPerformanceSettings {
    private const val PREFS = "aerix_client_performance"
    private const val OPTIONS = "options"
    private var context: Context? = null
    var overrides by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    fun init(context: Context) {
        if (this.context != null) return
        this.context = context.applicationContext
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS).getString(OPTIONS, "{}")
        overrides = runCatching {
            val json = JSONObject(text ?: "{}")
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun reload(context: Context) {
        this.context = null
        init(context)
    }

    fun set(key: String, value: String) {
        require(key in supportedKeys)
        overrides = overrides + (key to value)
        persist()
    }

    fun profile(name: String) {
        val values = when (name) {
            "Maximum FPS" -> mapOf("renderDistance" to "6", "simulationDistance" to "5", "graphicsMode" to "0", "renderClouds" to "\"false\"", "entityShadows" to "false", "particles" to "1", "biomeBlendRadius" to "0", "mipmapLevels" to "0", "enableVsync" to "false", "maxFps" to "120")
            "Balanced" -> mapOf("renderDistance" to "8", "simulationDistance" to "6", "graphicsMode" to "0", "renderClouds" to "\"fast\"", "entityShadows" to "false", "particles" to "1", "biomeBlendRadius" to "2", "mipmapLevels" to "2", "enableVsync" to "false", "maxFps" to "120")
            "Charging" -> mapOf("renderDistance" to "12", "simulationDistance" to "8", "graphicsMode" to "0", "renderClouds" to "\"fast\"", "entityShadows" to "false", "particles" to "1", "biomeBlendRadius" to "2", "mipmapLevels" to "2", "enableVsync" to "false", "maxFps" to "120")
            "Cool Down" -> mapOf("renderDistance" to "4", "simulationDistance" to "5", "graphicsMode" to "0", "renderClouds" to "\"false\"", "entityShadows" to "false", "particles" to "2", "biomeBlendRadius" to "0", "mipmapLevels" to "0", "enableVsync" to "false", "maxFps" to "30")
            "PvP" -> mapOf("renderDistance" to "8", "simulationDistance" to "5", "graphicsMode" to "0", "renderClouds" to "\"false\"", "entityShadows" to "false", "particles" to "1", "biomeBlendRadius" to "0", "mipmapLevels" to "0", "enableVsync" to "false", "maxFps" to "120")
            "Battery Saver" -> mapOf("renderDistance" to "6", "simulationDistance" to "5", "graphicsMode" to "0", "renderClouds" to "\"false\"", "entityShadows" to "false", "particles" to "1", "biomeBlendRadius" to "0", "mipmapLevels" to "2", "enableVsync" to "false", "maxFps" to "30")
            else -> emptyMap()
        }
        // Presets affect performance settings only, retaining separate game/audio choices.
        overrides = overrides.filterKeys { it !in performanceKeys } + values
        persist()
    }

    fun validateImported(values: Map<String, String>): Map<String, String> {
        require(values.size <= supportedKeys.size) { "Too many performance options" }
        require(values.all { (key, value) -> key in supportedKeys && value.length <= 64 && !value.contains('\n') && !value.contains('\r') }) { "Invalid performance option" }
        return values.toMap()
    }

    fun restoreImported(values: Map<String, String>) {
        overrides = validateImported(values)
        persist()
    }

    private fun persist() {
        val json = JSONObject()
        overrides.forEach { (key, value) -> json.put(key, value) }
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(OPTIONS, json.toString())?.apply()
    }

    private val performanceKeys = setOf("renderDistance", "simulationDistance", "graphicsMode", "renderClouds", "entityShadows", "particles", "biomeBlendRadius", "mipmapLevels", "enableVsync", "maxFps", "entityDistanceScaling")
    private val supportedKeys = setOf("renderDistance", "simulationDistance", "graphicsMode", "renderClouds", "entityShadows", "particles", "biomeBlendRadius", "mipmapLevels", "enableVsync", "maxFps", "viewBobbing", "autoJump", "rawMouseInput", "showSubtitles", "fovEffectScale", "screenEffectScale", "guiScale", "fov", "soundCategory_music", "soundCategory_master", "entityDistanceScaling", "mouseSensitivity", "damageTiltStrength", "glintStrength", "glintSpeed", "darknessEffectScale")

    /** Preserve every unknown line and setting, including third-party settings and resource packs. */
    fun applyAtLaunch(gameDir: File) {
        val selected = overrides.filterKeys { it in supportedKeys }
        if (selected.isEmpty()) return
        val file = File(gameDir, "options.txt")
        val lines = if (file.isFile) file.readLines() else emptyList()
        file.writeText(mergeOptions(lines, selected).joinToString("\n", postfix = "\n"))
    }

    internal fun mergeOptions(lines: List<String>, selected: Map<String, String>): List<String> {
        val remaining = selected.toMutableMap()
        val result = lines.map { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) line else {
                val key = line.substring(0, colon)
                val value = remaining.remove(key)
                if (value == null) line else "$key:$value"
            }
        }.toMutableList()
        remaining.forEach { (key, value) -> result.add("$key:$value") }
        return result
    }
}
