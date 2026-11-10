package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * The opt-in wardrobe backup, and the only place that decides what leaves the device for it.
 *
 * A wardrobe is a list of catalog ids: which of the shipped items the player wears in each of six
 * slots, and what each of the eight emote keys is bound to. Those ids are the entire local state, so
 * a second device can rebuild the same outfit and the same wheel without ever receiving a skin, a
 * cape, a texture, a preview image or a file - the pixels are generated on device from the same catalog
 * that ships inside the APK. That is also why there is no blob in here to exfiltrate.
 *
 * Nothing runs unless [AerixClientStore.trinkiesCloudSyncEnabled] is on, which is per device and off
 * by default, and unless an account is signed in to Aerix social. Push, pull and erase are all explicit
 * player actions: there is no timer, no launch-time fetch and no background retry, because silently
 * replacing someone's outfit with a copy from another phone is a worse default than a stale one.
 */
object TrinkiesCloudSync {
    private const val SCHEMA = 1
    private const val WHEEL_SLOTS = 8

    /**
     * The six worn slots. Duplicated on purpose from [TrinkiesLoadout] rather than shared: the writer
     * keeps its list private so the loadout file cannot grow a slot by accident, and a test pins these
     * two copies against the reader's list in the client mod.
     */
    private val CATEGORIES = listOf("hat", "suit", "back", "pet", "emote", "aura")

    private val CATALOG_ID = Pattern.compile("trinkies:[a-z0-9_]+")

    /** What an attempt produced, phrased for the Toast the Wardrobe shows. */
    data class Outcome(val ok: Boolean, val message: String)

    /** The current wardrobe, in the shape the table's check constraint accepts. */
    @JvmStatic
    fun snapshot(context: Context): String {
        AerixClientStore.init(context)
        val slots = JSONObject()
        CATEGORIES.forEach { category ->
            val id = AerixClientStore.equippedIdFor(category)
            if (id != "none" && TrinkiesCatalog.hasId(context, id, category)) {
                slots.put(category, id)
            }
        }
        val wheel = JSONArray()
        for (index in 0 until WHEEL_SLOTS) {
            val id = AerixClientStore.emoteWheelSlot(index)
            val bound = id != "none" && TrinkiesCatalog.find(context, id) != null
            wheel.put(if (bound) id else JSONObject.NULL)
        }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("slots", slots)
            .put("emote_wheel", wheel)
            .toString(2) + "\n"
    }

    suspend fun push(context: Context): Outcome {
        if (!AerixClientStore.trinkiesCloudSyncEnabled) {
            return Outcome(false, "Turn Wardrobe cloud sync on first")
        }
        if (!SocialBackend.signedIn) return Outcome(false, "Sign in to Aerix social first")
        val json = snapshot(context)
        val worn = countSlots(json)
        return if (SocialBackend.pushWardrobeSnapshot(json)) {
            Outcome(true, "Wardrobe backed up · $worn of ${CATEGORIES.size} slots and the wheel")
        } else {
            Outcome(false, SocialBackend.lastError ?: "The server refused the backup")
        }
    }

    /**
     * Reads the stored copy and makes it the local wardrobe. Ids the local catalog does not carry are
     * left alone rather than guessed at, so a pack downgrade cannot equip something that no longer
     * exists; a slot the copy does not mention is unequipped, because the row is a whole snapshot.
     */
    suspend fun pull(context: Context): Outcome {
        if (!AerixClientStore.trinkiesCloudSyncEnabled) {
            return Outcome(false, "Turn Wardrobe cloud sync on first")
        }
        if (!SocialBackend.signedIn) return Outcome(false, "Sign in to Aerix social first")
        val (raw, _) = SocialBackend.pullWardrobeSnapshot()
            ?: return Outcome(false, "No backup for this account yet")
        val applied = applyRemote(context, raw)
        return if (applied.ok) {
            Outcome(true, "Wardrobe restored · ${applied.message}")
        } else {
            Outcome(false, applied.message)
        }
    }

    suspend fun erase(context: Context): Outcome {
        if (!SocialBackend.signedIn) return Outcome(false, "Sign in to Aerix social first")
        return if (SocialBackend.eraseWardrobeSnapshot()) {
            AerixClientStore.trinkiesCloudSyncEnabled = false
            AerixClientStore.persist()
            Outcome(true, "Cloud copy deleted; this device keeps its wardrobe")
        } else {
            Outcome(false, SocialBackend.lastError ?: "The server refused the deletion")
        }
    }

    /** Applies a decoded payload to the store. Split out so the validation rules are testable on JVM. */
    @JvmStatic
    fun applyRemote(context: Context, raw: String): Outcome {
        AerixClientStore.init(context)
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return Outcome(false, "The backup is not readable JSON, so nothing changed")
        if (json.optInt("schema", -1) != SCHEMA) {
            return Outcome(false, "The backup uses a newer wardrobe format than this launcher")
        }
        val slots = json.optJSONObject("slots") ?: JSONObject()
        var restored = 0
        var skipped = 0
        CATEGORIES.forEach { category ->
            val id = slots.opt(category)?.takeIf { it != JSONObject.NULL }?.toString()
            when {
                // A slot the copy names but this pack does not carry is left exactly as it is: the
                // player's local choice beats a remote id we cannot resolve.
                id != null && TrinkiesCatalog.hasId(context, id, category) -> {
                    AerixClientStore.setEquippedId(category, id)
                    restored++
                }
                id != null -> skipped++
                // Absent is an answer, not an unknown: the row is a whole snapshot, so a device that
                // pushed with nothing on the head really means "no hat here too".
                else -> AerixClientStore.setEquippedId(category, "none")
            }
        }
        val wheel = json.optJSONArray("emote_wheel")
        var bound = 0
        if (wheel != null) {
            for (index in 0 until WHEEL_SLOTS) {
                val value = if (index < wheel.length()) wheel.opt(index) else null
                when {
                    value == null || value == JSONObject.NULL -> AerixClientStore.setEmoteWheelSlot(index, "none")
                    value is String && CATALOG_ID.matcher(value).matches() &&
                        TrinkiesCatalog.find(context, value) != null -> {
                        AerixClientStore.setEmoteWheelSlot(index, value)
                        bound++
                    }
                    else -> skipped++
                }
            }
        }
        if (restored == 0 && bound == 0 && skipped == 0) {
            return Outcome(true, "The backup held an empty wardrobe")
        }
        AerixClientStore.persist()
        PojavAccountBridge.notifyWardrobeChanged()
        // Without this the ids would sit in the store until the next launch while the running game
        // kept wearing what it read at startup.
        TrinkiesLoadout.refreshSupportedInstances(context)
        val note = buildString {
            append("$restored slot${if (restored == 1) "" else "s"}")
            if (bound > 0) append(", $bound emote${if (bound == 1) "" else "s"}")
            if (skipped > 0) append(" · $skipped id${if (skipped == 1) "" else "s"} not in this pack, left as they were")
        }
        return Outcome(true, note)
    }

    private fun countSlots(raw: String): Int = runCatching {
        JSONObject(raw).optJSONObject("slots")?.length() ?: 0
    }.getOrDefault(0)
}
