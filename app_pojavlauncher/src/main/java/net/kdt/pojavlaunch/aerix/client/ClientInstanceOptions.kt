package net.kdt.pojavlaunch.aerix.client

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Per-game-directory, opt-in resource-pack and keybind queues. Never modifies a running game's options. */
object ClientInstanceOptions {
    data class Pack(val name: String, val active: Boolean, val queued: Boolean?)
    data class KeyProfile(val name: String, val bindings: Map<String, String>)
    data class KeyProfileView(val profiles: List<KeyProfile>, val queued: String?)

    private fun optionFile(dir: File) = File(dir, "options.txt")
    private fun packQueue(dir: File) = File(dir, "config/aerix_pack_queue.json")
    private fun keyFile(dir: File) = File(dir, "config/aerix_key_profiles.json")

    private fun readLines(dir: File): List<String> {
        val file = optionFile(dir)
        if (!file.isFile) return emptyList()
        require(file.length() <= 2_000_000L) { "Minecraft options file is too large" }
        return file.readLines()
    }

    private fun readJson(file: File): JSONObject {
        if (!file.isFile) return JSONObject()
        require(file.length() <= 1_000_000L) { "Aerix configuration is too large" }
        return JSONObject(file.readText())
    }

    private fun writeAtomic(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".aerix-tmp")
        try {
            temp.writeText(text)
            check(temp.renameTo(file)) { "Could not save ${file.name}" }
        } finally { temp.delete() }
    }

    private fun packsFromOptions(lines: List<String>): MutableList<String> {
        val raw = lines.lastOrNull { it.startsWith("resourcePacks:") }?.substringAfter(':') ?: return mutableListOf("vanilla")
        val array = JSONArray(raw) // Do not overwrite a malformed pack list.
        return (0 until array.length()).map { array.getString(it) }.toMutableList()
    }

    private fun validPackName(name: String) = name.isNotBlank() && name.length <= 200 &&
        name != "." && name != ".." && !name.contains('/') && !name.contains('\\') &&
        !name.contains('\n') && !name.contains('\r')

    fun listPacks(dir: File): List<Pack> {
        val selected = packsFromOptions(readLines(dir)).toSet()
        val queue = readJson(packQueue(dir))
        return File(dir, "resourcepacks").listFiles().orEmpty().asSequence()
            .filter { validPackName(it.name) && (it.isDirectory || it.extension.equals("zip", true)) }
            .filterNot { it.name == AerixClientPackGenerator.PACK_FILE_NAME }
            .sortedBy { it.name.lowercase() }.take(100)
            .map { file ->
                val id = "file/${file.name}"
                Pack(file.name, id in selected, if (queue.has(id)) queue.getBoolean(id) else null)
            }.toList()
    }

    fun queuePack(dir: File, name: String, enabled: Boolean) {
        require(validPackName(name) && name != AerixClientPackGenerator.PACK_FILE_NAME)
        val file = File(File(dir, "resourcepacks"), name)
        require(file.exists() && (file.isDirectory || file.extension.equals("zip", true))) { "Pack no longer exists" }
        val queued = readJson(packQueue(dir))
        queued.put("file/$name", enabled)
        writeAtomic(packQueue(dir), queued.toString())
    }

    fun listKeyProfiles(dir: File): KeyProfileView {
        val json = readJson(keyFile(dir))
        val profiles = json.optJSONArray("profiles") ?: JSONArray()
        val list = (0 until profiles.length().coerceAtMost(10)).mapNotNull { i ->
            val entry = profiles.optJSONObject(i) ?: return@mapNotNull null
            val name = entry.optString("name").takeIf { it.isNotBlank() && it.length <= 30 } ?: return@mapNotNull null
            val bindings = entry.optJSONObject("bindings") ?: return@mapNotNull null
            val keys = bindings.keys().asSequence().take(500).filter { validKey(it) }
                .associateWith { bindings.optString(it) }.filterValues { validValue(it) }
            if (keys.isEmpty()) null else KeyProfile(name, keys)
        }
        return KeyProfileView(list, json.optString("pending").takeIf { name -> list.any { it.name == name } })
    }

    private fun validKey(key: String) = key.startsWith("key_") && key.length in 5..160 && !key.contains(':') && !key.contains('\n') && !key.contains('\r')
    private fun validValue(value: String) = value.length <= 500 && !value.contains('\n') && !value.contains('\r')

    fun saveKeyProfile(dir: File, name: String) {
        require(name.isNotBlank() && name.length <= 30 && !name.contains('\n') && !name.contains('\r')) { "Enter a profile name" }
        val keys = ClientOptionLines.keybinds(readLines(dir)).filter { validKey(it.key) && validValue(it.value) }
        require(keys.isNotEmpty()) { "No saved keybinds found in options.txt yet" }
        require(keys.size <= 500) { "Too many keybinds" }
        val current = listKeyProfiles(dir)
        require(current.profiles.size < 10 || current.profiles.any { it.name == name }) { "Maximum 10 profiles" }
        val updated = current.profiles.filterNot { it.name == name } + KeyProfile(name, keys)
        writeProfiles(dir, KeyProfileView(updated, current.queued))
    }

    fun queueKeyProfile(dir: File, name: String) {
        val current = listKeyProfiles(dir)
        require(current.profiles.any { it.name == name }) { "Profile no longer exists" }
        writeProfiles(dir, current.copy(queued = name))
    }

    fun deleteKeyProfile(dir: File, name: String) {
        val current = listKeyProfiles(dir)
        writeProfiles(dir, KeyProfileView(current.profiles.filterNot { it.name == name }, current.queued?.takeUnless { it == name }))
    }

    private fun writeProfiles(dir: File, data: KeyProfileView) {
        val profiles = JSONArray()
        data.profiles.forEach { p ->
            val bindings = JSONObject()
            p.bindings.forEach { (k, v) -> bindings.put(k, v) }
            profiles.put(JSONObject().put("name", p.name).put("bindings", bindings))
        }
        writeAtomic(keyFile(dir), JSONObject().put("profiles", profiles).put("pending", data.queued ?: "").toString())
    }

    /** Apply queued changes after other launcher setup, before Minecraft reads options.txt. */
    fun applyAtLaunch(dir: File) {
        val queueFile = packQueue(dir)
        val queue = readJson(queueFile)
        val profiles = listKeyProfiles(dir)
        val selectedProfile = profiles.profiles.firstOrNull { it.name == profiles.queued }
        if (queue.length() == 0 && selectedProfile == null) return
        val lines = readLines(dir)
        val updates = linkedMapOf<String, String>()
        if (queue.length() > 0) {
            val packs = packsFromOptions(lines)
            queue.keys().asSequence().toList().forEach { id ->
                val name = id.removePrefix("file/")
                if (!id.startsWith("file/") || !validPackName(name) || name == AerixClientPackGenerator.PACK_FILE_NAME) return@forEach
                val file = File(File(dir, "resourcepacks"), name)
                if (queue.optBoolean(id, false) && file.exists()) {
                    if (id !in packs) packs.add(id)
                } else if (!queue.optBoolean(id, false)) packs.removeAll { it == id }
            }
            updates["resourcePacks"] = JSONArray(packs).toString()
        }
        selectedProfile?.bindings?.let { updates.putAll(it) }
        val merged = ClientOptionLines.merge(lines, updates)
        writeAtomic(optionFile(dir), merged.joinToString("\n", postfix = "\n"))
        if (queue.length() > 0 && queueFile.exists()) check(queueFile.delete()) { "Could not clear queued packs" }
        if (selectedProfile != null) writeProfiles(dir, profiles.copy(queued = null))
    }
}
