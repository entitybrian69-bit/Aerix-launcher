package net.kdt.pojavlaunch.aerix.bridge

import com.google.gson.JsonObject

/** Exact-link matching: an ignored slug filter must never open an unrelated search result. */
internal object CurseForgeProjectLink {
    fun resolve(response: JsonObject, type: AssetType, slug: String): AssetProject? {
        if (!Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(slug)) return null
        val entries = runCatching { response.getAsJsonArray("data") }.getOrNull() ?: return null
        return entries.firstNotNullOfOrNull { element ->
            runCatching {
                val mod = element.asJsonObject
                if (mod.get("slug")?.asString != slug || mod.get("gameId")?.asInt != 432 ||
                    mod.get("classId")?.asInt != type.curseForgeClassId) return@runCatching null
                val id = mod.get("id")?.asString?.takeIf { (it.toLongOrNull() ?: 0) > 0 } ?: return@runCatching null
                fun text(key: String) = mod.get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                val logo = mod.getAsJsonObject("logo")
                val author = mod.getAsJsonArray("authors")?.firstOrNull { it.isJsonObject }?.asJsonObject
                AssetProject(AssetPlatform.CURSEFORGE, type, id, text("name"), text("summary"),
                    author?.get("name")?.asString.orEmpty(),
                    logo?.get("thumbnailUrl")?.takeUnless { it.isJsonNull }?.asString,
                    mod.get("downloadCount")?.asLong ?: 0)
            }.getOrNull()
        }
    }
}
