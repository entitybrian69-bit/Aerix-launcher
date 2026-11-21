package net.kdt.aerix.desktop.app

import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.core.json.JsonObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

/**
 * The launcher's settings store, the desktop counterpart of the Android build's
 * `SharedPreferences` + `AerixPreferences` pair.
 *
 * Values are persisted as one versioned JSON document at `%APPDATA%\Aerix Launcher\settings.json`,
 * written through a temporary file and an atomic move so a crash mid-save cannot leave a truncated
 * file behind. Every write is observable: screens subscribe with [onChange] and repaint, which is
 * what makes the Android "changes apply on the spot" behaviour work here too.
 *
 * Keys mirror the Android names wherever the meaning is the same (`launcherColorTheme`,
 * `selected_wallpaper_id`, `layoutTransparency`, `mcl_memory_allocation`, …) so a settings export
 * from the Android launcher imports with little or no translation.
 */
object Settings {
    private const val SCHEMA = 1

    private lateinit var file: Path
    private val values = ConcurrentHashMap<String, Any>()
    private val listeners = ConcurrentHashMap.newKeySet<(String) -> Unit>()
    private var saveScheduled = false
    private val saveLock = Any()

    // ---- lifecycle -------------------------------------------------------------------------

    fun load(path: Path) {
        file = path
        values.clear()
        if (!Files.exists(path)) return
        runCatching {
            val doc = Json.parseObject(Files.readString(path, StandardCharsets.UTF_8))
            val schema = doc.num("schema")?.toInt() ?: 0
            val data = doc.obj("values") ?: doc
            data.fields.forEach { (k, v) ->
                when (v) {
                    is net.kdt.aerix.desktop.core.json.JsonBool -> values[k] = v.value
                    is net.kdt.aerix.desktop.core.json.JsonNumber -> {
                        val d = v.value
                        values[k] = if (d == d.toLong().toDouble()) d.toLong() else d
                    }
                    is net.kdt.aerix.desktop.core.json.JsonString -> values[k] = v.value
                    else -> { /* nested objects are not used for settings */ }
                }
            }
            if (schema < SCHEMA) migrate(schema)
        }.onFailure { e ->
            // A corrupt file is moved aside, never silently overwritten: the user can inspect it.
            val broken = path.resolveSibling(path.fileName.toString() + ".corrupt-" + System.currentTimeMillis())
            runCatching { Files.move(path, broken, StandardCopyOption.REPLACE_EXISTING) }
            Log.w("settings", "could not read settings (${e.message}); moved to $broken")
        }
    }

    private fun migrate(from: Int) {
        // Schema 0 -> 1: Android exports may carry the transparent-window flags of a phone UI.
        if (from < 1) {
            listOf("launcherFullScreen", "ignoreNotch", "maxRefresh").forEach { values.remove(it) }
        }
    }

    // ---- typed access ----------------------------------------------------------------------

    fun string(key: String, def: String = ""): String = (values[key] as? String) ?: def
    fun int(key: String, def: Int = 0): Int = when (val v = values[key]) {
        is Long -> v.toInt()
        is Int -> v
        is Double -> v.toInt()
        is String -> v.toIntOrNull() ?: def
        else -> def
    }
    fun long(key: String, def: Long = 0L): Long = when (val v = values[key]) {
        is Long -> v
        is Int -> v.toLong()
        is Double -> v.toLong()
        is String -> v.toLongOrNull() ?: def
        else -> def
    }
    fun float(key: String, def: Float = 0f): Float = when (val v = values[key]) {
        is Long -> v.toFloat()
        is Double -> v.toFloat()
        is String -> v.toFloatOrNull() ?: def
        else -> def
    }
    fun bool(key: String, def: Boolean = false): Boolean = when (val v = values[key]) {
        is Boolean -> v
        is String -> v.equals("true", true)
        else -> def
    }
    fun stringList(key: String): List<String> =
        string(key).split('\u0001').filter { it.isNotEmpty() }

    fun contains(key: String): Boolean = values.containsKey(key)
    fun snapshot(): Map<String, Any> = HashMap(values)

    fun set(key: String, value: Any?) {
        val previous = if (value == null) values.remove(key) else values.put(key, normalise(value))
        if (previous == value) return
        notify(key)
        scheduleSave()
    }

    fun remove(key: String) = set(key, null)

    fun setAll(map: Map<String, Any?>) {
        map.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = normalise(v) }
        map.keys.forEach(::notify)
        scheduleSave()
    }

    private fun normalise(value: Any): Any = when (value) {
        is Int -> value.toLong()
        is Float -> value.toDouble()
        is Short -> value.toLong()
        is java.io.File -> value.absolutePath
        else -> value
    }

    fun toggle(key: String, def: Boolean = false): Boolean = bool(key, def).not().also { set(key, it) }

    // ---- observation -----------------------------------------------------------------------

    fun onChange(listener: (String) -> Unit) { listeners.add(listener) }
    fun removeListener(listener: (String) -> Unit) { listeners.remove(listener) }

    private fun notify(key: String) {
        listeners.forEach { l -> runCatching { l(key) }.onFailure { Log.w("settings", "listener failed: ${it.message}") } }
    }

    // ---- persistence -----------------------------------------------------------------------

    fun scheduleSave() {
        synchronized(saveLock) {
            if (saveScheduled) return
            saveScheduled = true
        }
        Scheduler.io("settings-save") {
            synchronized(saveLock) { saveScheduled = false }
            Thread.sleep(120) // coalesce bursts of slider changes into one write
            saveNow()
        }
    }

    fun saveNow() {
        if (!::file.isInitialized) return
        try {
            Files.createDirectories(file.parent)
            val doc = JsonObject()
            doc.put("schema", SCHEMA)
            doc.put("app", "Aerix Launcher for Windows")
            val body = JsonObject()
            values.forEach { (k, v) ->
                when (v) {
                    is Boolean, is Long, is Double, is String -> body.put(k, v)
                }
            }
            doc.put("values", body)
            val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
            Files.writeString(
                tmp, Json.pretty(doc), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
            )
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: IOException) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            Log.e("settings", "could not save settings: ${e.message}")
        }
    }
}
