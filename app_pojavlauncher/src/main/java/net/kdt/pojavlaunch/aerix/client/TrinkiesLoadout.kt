package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import android.system.Os
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Atomically writes the validated, local-only v1 loadout for one supported game instance. */
object TrinkiesLoadout {
    private const val TAG = "TrinkiesLoadout"
    private const val SCHEMA = 1
    private const val FILE_NAME = "equipped.json"

    /**
     * Categories the mod can consume, in bake order. `emote` here is the worn sleeve/cuff look; the
     * keybound wheel is the separate `emote_wheel` array below.
     */
    private val CATEGORIES = listOf("hat", "suit", "back", "pet", "emote", "aura")

    /**
     * Writes `<instance>/trinkies/equipped.json`. Every category carries the catalog id the player
     * picked, or null when nothing is equipped, and each id is re-checked against the catalog before
     * it is written so a stale preference cannot point the mod at a missing item.
     */
    @JvmStatic
    fun writeForInstance(context: Context, gameDirectory: File): Boolean = runCatching {
        AerixClientStore.init(context)
        val root = gameDirectory.canonicalFile
        if (!root.isDirectory) {
            Log.w(TAG, "Instance game directory is missing: $root")
            return false
        }
        val outputDir = File(root, "trinkies")
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            Log.w(TAG, "Could not create the per-instance Trinkies directory")
            return false
        }
        val canonicalDir = outputDir.canonicalFile
        if (canonicalDir.parentFile != root) {
            Log.w(TAG, "Refusing to write loadout outside the instance game directory")
            return false
        }
        val target = File(canonicalDir, FILE_NAME)
        if (target.exists() && target.canonicalFile.parentFile != canonicalDir) {
            Log.w(TAG, "Refusing to replace an equipped-state symlink outside the instance")
            return false
        }

        val equipped = CATEGORIES.associateWith { category ->
            AerixClientStore.equippedIdFor(category).takeIf { id ->
                id != "none" && TrinkiesCatalog.hasId(context, id, category)
            }
        }
        val json = JSONObject()
            .put("schema", SCHEMA)
            .apply { equipped.forEach { (category, id) -> put(category, id ?: JSONObject.NULL) } }
            // The wheel is the keybound list, which no launcher UI feeds yet; it is written as eight
            // explicit nulls so a reader can tell "nothing bound" from "field never existed".
            .put("emote_wheel", JSONArray().apply { repeat(8) { put(JSONObject.NULL) } })
            .toString(2) + "\n"

        val temporary = File.createTempFile(".equipped-", ".tmp", canonicalDir)
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            // Same-directory rename is atomic on the instance filesystem; never expose partial JSON.
            Os.rename(temporary.absolutePath, target.absolutePath)
            true
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }.onFailure { error ->
        Log.w(TAG, "Could not atomically write the local Trinkies loadout", error)
    }.getOrDefault(false)
}
