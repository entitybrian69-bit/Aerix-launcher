package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import android.util.Log
import org.json.JSONObject

/** A launcher/mod-shared entry from assets/trinkies/catalog.json. */
data class TrinkiesCatalogItem(
    val id: String,
    val category: String,
    val name: String,
    val description: String,
    val rarity: String,
    /** Source texture atlas used by skin overlays and model previews. */
    val textureAsset: String,
    /** Transparent render of the item worn by the mannequin, used for the wardrobe card. */
    val previewAsset: String,
    /** 64x64 garment patch laid out like a skin; this is what the bake composites onto a player. */
    val garmentAsset: String,
    /** Optional 64x32 cape patch, present when a back item paints the cape sheet as well. */
    val capeAsset: String? = null,
    val shape: String,
    val palette: List<String>,
    val locallyRendered: Boolean
)

/**
 * Validated, read-only view of the bundled catalog. This class does not perform network requests;
 * the same JSON and textures are packaged into the launcher and the Fabric client mod.
 */
object TrinkiesCatalog {
    private const val TAG = "TrinkiesCatalog"
    private const val ASSET_CATALOG = "trinkies/catalog.json"

    @Volatile
    private var cached: List<TrinkiesCatalogItem>? = null

    fun all(context: Context): List<TrinkiesCatalogItem> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = runCatching {
                val json = context.applicationContext.assets.open(ASSET_CATALOG)
                    .bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                val entries = json.getJSONArray("items")
                buildList(entries.length()) {
                    for (index in 0 until entries.length()) {
                        val item = entries.getJSONObject(index)
                        val id = item.optString("id")
                        val category = item.optString("category")
                        val relativeTexture = item.optString("texture")
                        val relativePreview = item.optString("preview", relativeTexture)
                        // Older catalogs predate the patch field; its path is derivable from the texture.
                        val relativeGarment = item.optString("garment")
                            .ifBlank { relativeTexture.removeSuffix("texture.png") + "garment.png" }
                        val relativeCape = item.optString("cape_texture")
                            .ifBlank { if (category == "back") relativeGarment.replace("garment.png", "cape_patch.png") else "" }
                        if (!id.startsWith("trinkies:") || category !in CATEGORIES ||
                            !ID_PATTERN.matches(id) || !ASSET_PATH_PATTERN.matches(relativeTexture) ||
                            !PREVIEW_PATH_PATTERN.matches(relativePreview) ||
                            !GARMENT_PATH_PATTERN.matches(relativeGarment) ||
                            (relativeCape.isNotEmpty() && !CAPE_PATH_PATTERN.matches(relativeCape))) {
                            throw IllegalArgumentException("Invalid Trinkies catalog entry at index $index")
                        }
                        val paletteJson = item.optJSONArray("palette")
                            ?: throw IllegalArgumentException("Missing palette for $id")
                        val palette = buildList(paletteJson.length()) {
                            for (colorIndex in 0 until paletteJson.length()) {
                                val color = paletteJson.optString(colorIndex)
                                if (COLOR_PATTERN.matches(color)) add(color) else {
                                    throw IllegalArgumentException("Invalid palette color for $id")
                                }
                            }
                        }
                        if (palette.size !in 5..8) throw IllegalArgumentException("Invalid palette size for $id")
                        add(
                            TrinkiesCatalogItem(
                                id = id,
                                category = category,
                                name = item.optString("name", id.substringAfter(':')),
                                description = item.optString("description"),
                                rarity = item.optString("rarity", "common"),
                                textureAsset = "trinkies/$relativeTexture",
                                previewAsset = "trinkies/$relativePreview",
                                garmentAsset = "trinkies/$relativeGarment",
                                capeAsset = relativeCape.takeIf { it.isNotEmpty() }?.let { "trinkies/$it" },
                                shape = item.optString("shape"),
                                palette = palette,
                                locallyRendered = item.optBoolean("local_render_support", false)
                            )
                        )
                    }
                }.also { items ->
                    if (items.size != json.optInt("item_count", -1) || items.map { it.id }.toSet().size != items.size) {
                        throw IllegalArgumentException("Catalog item count or IDs are inconsistent")
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "Could not read bundled Trinkies catalog", error)
            }.getOrNull() ?: emptyList()
            cached = loaded
            return loaded
        }
    }

    fun category(context: Context, category: String): List<TrinkiesCatalogItem> =
        all(context).filter { it.category == category }

    fun find(context: Context, id: String): TrinkiesCatalogItem? = all(context).firstOrNull { it.id == id }

    fun hasId(context: Context, id: String?, category: String): Boolean =
        id != null && all(context).any { it.id == id && it.category == category }

    private val CATEGORIES = setOf("hat", "suit", "back", "pet", "emote", "aura")
    private val ID_PATTERN = Regex("trinkies:[a-z0-9_]+")
    private val ASSET_PATH_PATTERN = Regex("items/[a-z0-9_]+/texture\\.png")
    private val PREVIEW_PATH_PATTERN = Regex("items/[a-z0-9_]+/(?:preview|texture)\\.png")
    private val GARMENT_PATH_PATTERN = Regex("items/[a-z0-9_]+/garment\\.png")
    private val CAPE_PATH_PATTERN = Regex("items/[a-z0-9_]+/cape_patch\\.png")
    private val COLOR_PATTERN = Regex("#[0-9A-Fa-f]{6}")
}
