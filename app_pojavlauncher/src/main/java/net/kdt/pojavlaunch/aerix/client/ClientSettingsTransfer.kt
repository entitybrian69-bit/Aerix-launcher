package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, local settings export. Never includes accounts or authentication data. */
object ClientSettingsTransfer {
    private const val PREFS = "aerix_client_v2_prefs"
    private val boolKeys = setOf("packVanillaAnimatedGear", "packEnhancedGlint", "packLowFire", "packLoweredShield", "packCompactTotem", "packClearWater", "packNoPumpkinBlur", "wavingCapeEnabled", "crosshairEnabled", "crosshairAttackPulse", "perfPillEnabled", "fpsHistoryEnabled", "frameTimingEnabled", "cpsPillEnabled", "keystrokesEnabled", "swordCooldownBarEnabled", "hitmarkerEnabled", "comboCounterEnabled", "toggleSprintEnabled", "toggleSneakEnabled", "autoWalkEnabled", "coordsHudEnabled", "armorHudEnabled", "effectsHudEnabled", "worldTimeHudEnabled", "serverHudEnabled", "pingHudEnabled", "lightHudEnabled", "biomeHudEnabled", "clockHudEnabled", "waypointHudEnabled", "deathWaypointEnabled", "healthHudEnabled", "compassEnabled", "matchStatsEnabled", "speedrunTimerEnabled", "touchAimEnabled", "gyroAdsEnabled", "palmShieldEnabled", "hotbarSwipeEnabled", "quickPvpButtonsEnabled", "fullbrightEnabled", "autoGgEnabled", "streamerPrivacyEnabled", "thermalGuardEnabled", "queueAlarmEnabled")
    private val floatKeys = setOf("crosshairSize", "crosshairGap", "crosshairThickness", "crosshairOutline", "crosshairOpacity", "touchAimMultiplier", "gyroSensitivity")
    private val intKeys = setOf("peakCpsRecord", "installsCompleted")
    private val stringKeys = setOf("selectedClientVersion", "equippedCapeId", "equippedHatId", "crosshairShape", "clientWaypoints", "clientChatShortcuts", "hudPositionsJson")
    private val allowedKeys = boolKeys + floatKeys + intKeys + stringKeys

    fun export(context: Context): String {
        AerixClientStore.persist()
        val values = JSONObject()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (key, value) ->
            if (key in allowedKeys && value != null) values.put(key, value)
        }
        val performance = JSONObject()
        ClientPerformanceSettings.overrides.forEach { (key, value) -> performance.put(key, value) }
        return JSONObject().put("format", "AerixClientSettings").put("version", 1)
            .put("client", values).put("performance", performance).toString(2)
    }

    /** Validates the entire document before changing either preference set. */
    fun importSettings(context: Context, text: String) {
        require(text.length <= 250_000) { "Settings file is too large" }
        val root = JSONObject(text)
        require(root.optString("format") == "AerixClientSettings" && root.optInt("version") == 1) { "Not a supported Aerix settings file" }
        val client = root.getJSONObject("client")
        val values = mutableMapOf<String, Any>()
        val allKeys = client.keys().asSequence().toList()
        require(allKeys.size <= allowedKeys.size && allKeys.all { it in allowedKeys }) { "Unknown client setting" }
        allKeys.forEach { key ->
            val value = client.get(key)
            values[key] = when {
                key in boolKeys -> value as? Boolean ?: error("Invalid $key")
                key in floatKeys -> {
                    val f = (value as? Number)?.toFloat() ?: error("Invalid $key")
                    val range = when (key) {
                        "crosshairSize" -> 4f..18f
                        "crosshairGap" -> 0f..10f
                        "crosshairThickness" -> 0.5f..5f
                        "crosshairOutline" -> 0f..4f
                        "crosshairOpacity" -> 0.1f..1f
                        else -> 0.25f..2f
                    }
                    require(f.isFinite() && f in range) { "Invalid $key" }
                    f
                }
                key in intKeys -> {
                    val number = (value as? Number)?.toLong() ?: error("Invalid $key")
                    require(number in 0..1_000_000) { "Invalid $key" }
                    number.toInt()
                }
                else -> {
                    val string = value as? String ?: error("Invalid $key")
                    require(string.length <= 64_000) { "Invalid $key" }
                    if (key == "clientWaypoints") require(JSONArray(string).length() <= 30) { "Too many waypoints" }
                    if (key == "clientChatShortcuts") {
                        val messages = JSONArray(string)
                        require(messages.length() <= 12) { "Too many chat shortcuts" }
                        for (i in 0 until messages.length()) {
                            val message = messages.getString(i)
                            require(message.length <= 100 && !message.contains('\n') && !message.contains('\r')) { "Invalid chat shortcut" }
                        }
                    }
                    if (key == "hudPositionsJson") require(JSONArray(string).length() <= 40) { "Too many HUD positions" }
                    string
                }
            }
        }
        val perfJson = root.getJSONObject("performance")
        val performance = perfJson.keys().asSequence().associateWith { key ->
            perfJson.get(key) as? String ?: error("Invalid performance option")
        }
        ClientPerformanceSettings.validateImported(performance)

        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
            }
        }
        // Never restore held movement actions from a file while a world is open.
        editor.putBoolean("toggleSprintEnabled", false).putBoolean("toggleSneakEnabled", false).putBoolean("autoWalkEnabled", false)
        check(editor.commit()) { "Could not save Client settings" }
        ClientPerformanceSettings.restoreImported(performance)
        AerixClientInputBridge.setSprintLatch(false)
        AerixClientInputBridge.setSneakLatch(false)
        AerixClientInputBridge.setAutoWalkLatch(false)
        AerixClientStore.reload(context)
    }
}
