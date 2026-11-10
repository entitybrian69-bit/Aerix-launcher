package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import android.system.Os
import android.util.Log
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.instances.Instances
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Atomically writes the validated, local-only v1 loadout for one supported game instance. */
object TrinkiesLoadout {
    private const val TAG = "TrinkiesLoadout"
    private const val SCHEMA = 1
    private const val FILE_NAME = "equipped.json"

    /** The client writes this after it has read [FILE_NAME]; it is the game's side of the receipt. */
    private const val APPLIED_FILE_NAME = "applied.json"

    /** Number keys 1-8, matching the schema's fixed wheel length. */
    private const val WHEEL_SLOTS = 8

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

        // Turning cosmetics off writes an emptied loadout rather than skipping the write: a client
        // that is already running reads this file, so "no receipt change" would leave it wearing
        // everything the player just switched off.
        val wardrobeEnabled = AerixClientStore.trinkiesCosmeticsEnabled
        val equipped = CATEGORIES.associateWith { category ->
            AerixClientStore.equippedIdFor(category).takeIf { id ->
                wardrobeEnabled && id != "none" && TrinkiesCatalog.hasId(context, id, category)
            }
        }
        val json = JSONObject()
            .put("schema", SCHEMA)
            .apply { equipped.forEach { (category, id) -> put(category, id ?: JSONObject.NULL) } }
            // The wheel is the keybound list the Wardrobe edits: eight slots, each an emote id or an
            // explicit null, so a reader can tell "nothing bound" from "field never existed". Binding
            // any item other than the worn emote is fine - the mod resolves each id through the
            // catalog and plays that item's own clip, and cosmetics being off empties the wheel too.
            .put("emote_wheel", JSONArray().apply {
                repeat(WHEEL_SLOTS) { index ->
                    put(AerixClientStore.emoteWheelSlot(index)
                        .takeIf { id -> wardrobeEnabled && id != "none" && TrinkiesCatalog.find(context, id) != null }
                        ?: JSONObject.NULL)
                }
            })
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

    /**
     * Rewrites the loadout into every instance that can consume one, on a background thread.
     *
     * A launch does this anyway; the point is a change made *while the game is running* - a rebound
     * emote key, a wardrobe pulled from the cloud - which would otherwise sit in the store until the
     * next launch. Safe to call from the UI thread: the file writes happen off it, each write is an
     * atomic same-directory rename, and the mod re-reads the file on its own clock.
     */
    @JvmStatic
    fun refreshSupportedInstances(context: Context) {
        PojavApplication.sExecutorService.execute {
            runCatching {
                Instances.loadAllInstances()
                    .filter { TrinkiesInstaller.isSupportedInstance(it) }
                    .forEach { instance ->
                        instance.gameDirectory?.let { directory -> writeForInstance(context, directory) }
                    }
            }.onFailure { error ->
                Log.w(TAG, "Could not refresh the Trinkies loadout of every instance", error)
            }
        }
    }

    /**
     * One line describing what a Fabric-enabled instance read back from its loadout, or null when no
     * supported instance has run with Trinkies yet.
     *
     * The client validates [FILE_NAME] against the packaged catalog and drops `applied.json` next to
     * it, so this is the game confirming the wardrobe rather than the launcher assuming it. The newest
     * receipt across supported instances wins, because the store is shared by every instance.
     */
    @JvmStatic
    fun describeGameConfirmation(context: Context): String? = runCatching {
        val receipts = Instances.loadAllInstances()
            .filter { TrinkiesInstaller.isSupportedInstance(it) }
            .mapNotNull { instance ->
                runCatching {
                    val directory = instance.gameDirectory ?: return@runCatching null
                    val file = File(File(directory, "trinkies"), APPLIED_FILE_NAME)
                    if (!file.isFile) return@runCatching null
                    JSONObject(file.readText(Charsets.UTF_8))
                        .takeIf { it.optInt("schema", -1) == SCHEMA }
                }.getOrNull()
            }
        val receipt = receipts.maxByOrNull { it.optLong("confirmedAtMs") } ?: return null
        val equipped = receipt.optJSONObject("equipped")
        // optString() turns a JSON null into the string "null", so the raw value is what to inspect.
        val worn = CATEGORIES.count { category ->
            equipped?.opt(category)?.let { it != JSONObject.NULL && it.toString().startsWith("trinkies:") } == true
        }
        buildString {
            append("GAME READ ").append(worn).append(" OF ").append(CATEGORIES.size).append(" SLOTS")
            if (!receipt.optBoolean("bake_matches_loadout", true)) {
                append(" · BAKE IS STALE, RESTART THE INSTANCE")
            } else {
                append(" · CAPE PACE ").append(receipt.optInt("frameTicks", 1)).append(" TICKS/FRAME")
            }
            // The wheel is listed separately because it binds keys rather than pixels: a player who
            // bound three emotes and sees "0/8" knows the loadout never made it into the game folder.
            val bound = receipt.optInt("emoteWheelSlots", 0)
            if (bound > 0 || receipt.has("emoteWheelSlots")) {
                append(" · EMOTE WHEEL ").append(bound.coerceIn(0, WHEEL_SLOTS)).append('/').append(WHEEL_SLOTS)
            }
        }
    }.onFailure { error ->
        Log.w(TAG, "Could not read the client's Trinkies confirmation", error)
    }.getOrNull()
}
