package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

enum class CrosshairShape(val label: String) {
    CLASSIC_PLUS("Classic +"),
    TACTICAL_GAP("Tactical Gap"),
    GAP_DOT("Gap + Dot"),
    PRECISION_DOT("Center Dot"),
    CIRCLE("Hollow Ring"),
    CIRCLE_DOT("Ring + Dot"),
    X_HIT("Diagonal X"),
    CHEVRON("Aim Chevron"),
    DIAMOND("Diamond"),
    TRI_STAR("Tri-Star"),
    CORNER_BOX("Corner Box"),
    SNIPER_T("Sniper T")
}

enum class KeystrokesStyle(val label: String) {
    CRIMSON_DARK("Crimson & Dark"),
    CPS_PILL_ONLY("CPS Only")
}

enum class TouchCurveMode(val label: String, val desc: String) {
    LINEAR_RAW("1:1 Raw Input", "Unmodified direct touch delta"),
    PVP_FLICK("PvP Flick Curve", "Steady micro-aim + fast 180° flicks"),
    SMOOTH_CINEMA("Smooth Gliding", "Damped camera motion")
}

enum class VisualFilterPreset(val label: String, val tintArgb: Long, val desc: String) {
    NONE("Off", 0x00000000L, "Unmodified display output"),
    CRISP_PVP("Crisp PvP", 0x14FFFFFFL, "Brightens dark shadows")
}

enum class SkyboxTheme(val label: String) {
    AURORA_CRYSTAL("Crimson Dusk"),
    SAKURA_SUNSET("Sakura Horizon"),
    STARRY_NEBULA("Starry Nebula"),
    DEEP_VOID("Esports Deep Void")
}

enum class HitSoundPreset(val label: String, val soundEvent: String, val pitch: Float) {
    CRYSTAL_BELL("Crimson Ding", "block.note_block.bell", 1.35f),
    VANILLA("Vanilla Default", "", 1.0f)
}

enum class AfkFarmAction(val label: String, val desc: String) {
    PASSIVE_HOLD("Passive Stand-Still", "Keeps session alive with OLED off"),
    AUTO_FISH_RCLICK("Hold Right-Click", "Continuous right-click hold"),
    XP_GRINDER_SWING("Sword Swing", "Timed left-click swing (1.25s)"),
    ANTI_IDLE_PULSE("Anti-Idle Pulse", "Gentle jump tap every 40s")
}

data class HudWidgetPos(
    val id: String,
    val label: String,
    val xNorm: Float,
    val yNorm: Float,
    val scale: Float = 1.0f
)

data class ClientWaypoint(
    val id: String,
    val name: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val dimension: String,
    val colorHex: Long = 0xFFDC1E3AL,
    val pinned: Boolean = false
)

object AerixClientStore {
    const val SUPPORTED_MC_VERSION = "1.16.5"
    const val CLIENT_INSTANCE_NAME = "Aerix Client 1.16.5"

    // Locked Crimson Red + Dark Gray Theme (immutable, cannot be changed)
    const val LOCKED_CRIMSON_HEX = 0xFFDC1E3AL
    const val LOCKED_CRIMSON_GLOW_HEX = 0xFFFF2E4CL
    const val LOCKED_DARK_BASE_HEX = 0xFF141519L
    const val LOCKED_DARK_PANEL_HEX = 0xFF1C1E24L
    const val LOCKED_DARK_TILE_HEX = 0xFF252830L

    // Fresh v2 preferences namespace so ALL HUD items default to OFF and zero-overlap positions apply immediately
    private const val PREFS_FILE = "aerix_client_v2_prefs"

    private var prefs: SharedPreferences? = null

    /* ── Selected Client Version in Launcher Tab ──────────────────────────── */
    var selectedClientVersion by mutableStateOf("1.16.5")

    /* ── Installer & Resource Pack Flags (locked defaults) ────────────────── */
    var installFpsStack by mutableStateOf(true)
    var installShadersAddon by mutableStateOf(true)
    var installSurvivalPolishAddon by mutableStateOf(true)
    var installZoomAddon by mutableStateOf(true)
    var installClientResourcePack by mutableStateOf(true)

    var packVanillaAnimatedGear by mutableStateOf(true)
    var packEnhancedGlint by mutableStateOf(true)
    var packLowFire by mutableStateOf(true)
    var packShortSwords by mutableStateOf(false)
    var packLoweredShield by mutableStateOf(true)
    var packCompactTotem by mutableStateOf(true)
    var packOutlinedOres by mutableStateOf(false)
    var packClearWater by mutableStateOf(true)
    var packNoPumpkinBlur by mutableStateOf(true)
    var packBorderlessGlass by mutableStateOf(false)
    var packRgbBowStages by mutableStateOf(false)
    var packMiningProgressBar by mutableStateOf(false)
    var packCustomMainMenu by mutableStateOf(true)
    var packCustomMojangSplash by mutableStateOf(true)
    var packSkyboxTheme by mutableStateOf(SkyboxTheme.DEEP_VOID)
    var packHitSound by mutableStateOf(HitSoundPreset.VANILLA)

    /* ── Capes State (3D Character + Waving Cape + 15 Animated Capes) ─────── */
    var equippedCapeId by mutableStateOf("crimson_sovereign")
    var equippedHatId by mutableStateOf("none")
    var wavingCapeEnabled by mutableStateOf(true)
    var animatedCapeShimmer by mutableStateOf(true)

    /* ── In-Game Client Modules — ALL HUD ITEMS OFF BY DEFAULT ────────────── */
    var crosshairEnabled by mutableStateOf(false)
    var crosshairShape by mutableStateOf(CrosshairShape.TACTICAL_GAP)
    var crosshairSize by mutableFloatStateOf(9f)
    var crosshairGap by mutableFloatStateOf(3.5f)
    var crosshairThickness by mutableFloatStateOf(2f)
    var crosshairOutline by mutableFloatStateOf(1.4f)
    var crosshairOpacity by mutableFloatStateOf(0.95f)
    var crosshairAttackPulse by mutableStateOf(false)

    var perfPillEnabled by mutableStateOf(false)
    var cpsPillEnabled by mutableStateOf(false)
    var keystrokesEnabled by mutableStateOf(false)
    var keystrokesStyle by mutableStateOf(KeystrokesStyle.CRIMSON_DARK)
    var swordCooldownBarEnabled by mutableStateOf(false)
    var hitmarkerEnabled by mutableStateOf(false)
    var comboCounterEnabled by mutableStateOf(false)

    var toggleSprintEnabled by mutableStateOf(false)
    var toggleSneakEnabled by mutableStateOf(false)
    var autoWalkEnabled by mutableStateOf(false)

    var compassEnabled by mutableStateOf(false)
    var matchStatsEnabled by mutableStateOf(false)
    var speedrunTimerEnabled by mutableStateOf(false)

    var gyroAdsEnabled by mutableStateOf(false)
    var gyroSensitivity by mutableFloatStateOf(1.0f)
    var touchCurveMode by mutableStateOf(TouchCurveMode.LINEAR_RAW)
    var palmShieldEnabled by mutableStateOf(false)
    var hotbarSwipeEnabled by mutableStateOf(false)
    var quickPvpButtonsEnabled by mutableStateOf(false)

    var fullbrightEnabled by mutableStateOf(false)
    var visualFilter by mutableStateOf(VisualFilterPreset.NONE)

    var autoGgEnabled by mutableStateOf(false)
    var smartChatFilterEnabled by mutableStateOf(false)
    var streamerPrivacyEnabled by mutableStateOf(false)
    var thermalGuardEnabled by mutableStateOf(false)
    var queueAlarmEnabled by mutableStateOf(false)
    var queueAlertThreshold by mutableIntStateOf(5)

    var afkAction by mutableStateOf(AfkFarmAction.XP_GRINDER_SWING)
    var afkSwingIntervalMs by mutableLongStateOf(1250L)

    /* ── HUD Positions (100% Zero-Overlap Safe Slots) & Telemetry ─────────── */
    var hudPositions by mutableStateOf(defaultHudPositions())
    var waypoints by mutableStateOf<List<ClientWaypoint>>(emptyList())

    var clientXp by mutableIntStateOf(0)
    var installsCompleted by mutableIntStateOf(0)
    var peakCpsRecord by mutableIntStateOf(0)
    var sessionWins by mutableIntStateOf(0)
    var sessionStreak by mutableIntStateOf(0)

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        prefs = p

        selectedClientVersion = p.getString("selectedClientVersion", "1.16.5") ?: "1.16.5"

        equippedCapeId = p.getString("equippedCapeId", "crimson_sovereign") ?: "crimson_sovereign"
        equippedHatId = p.getString("equippedHatId", "none") ?: "none"
        wavingCapeEnabled = p.getBoolean("wavingCapeEnabled", true)
        animatedCapeShimmer = true

        // Every HUD & gameplay overlay defaults strictly to FALSE unless explicitly enabled by user
        crosshairEnabled = p.getBoolean("crosshairEnabled", false)
        crosshairShape = enumOrDefault(p.getString("crosshairShape", null), CrosshairShape.TACTICAL_GAP)
        crosshairSize = p.getFloat("crosshairSize", 9f)
        crosshairGap = p.getFloat("crosshairGap", 3.5f)
        crosshairThickness = p.getFloat("crosshairThickness", 2f)
        crosshairOutline = p.getFloat("crosshairOutline", 1.4f)
        crosshairOpacity = p.getFloat("crosshairOpacity", 0.95f)
        crosshairAttackPulse = p.getBoolean("crosshairAttackPulse", false)

        perfPillEnabled = p.getBoolean("perfPillEnabled", false)
        cpsPillEnabled = p.getBoolean("cpsPillEnabled", false)
        keystrokesEnabled = p.getBoolean("keystrokesEnabled", false)
        swordCooldownBarEnabled = p.getBoolean("swordCooldownBarEnabled", false)
        hitmarkerEnabled = p.getBoolean("hitmarkerEnabled", false)
        comboCounterEnabled = p.getBoolean("comboCounterEnabled", false)

        toggleSprintEnabled = p.getBoolean("toggleSprintEnabled", false)
        toggleSneakEnabled = p.getBoolean("toggleSneakEnabled", false)
        autoWalkEnabled = p.getBoolean("autoWalkEnabled", false)

        compassEnabled = p.getBoolean("compassEnabled", false)
        matchStatsEnabled = p.getBoolean("matchStatsEnabled", false)
        speedrunTimerEnabled = p.getBoolean("speedrunTimerEnabled", false)

        gyroAdsEnabled = p.getBoolean("gyroAdsEnabled", false)
        gyroSensitivity = p.getFloat("gyroSensitivity", 1.0f)
        palmShieldEnabled = p.getBoolean("palmShieldEnabled", false)
        hotbarSwipeEnabled = p.getBoolean("hotbarSwipeEnabled", false)
        quickPvpButtonsEnabled = p.getBoolean("quickPvpButtonsEnabled", false)

        fullbrightEnabled = p.getBoolean("fullbrightEnabled", false)
        autoGgEnabled = p.getBoolean("autoGgEnabled", false)
        streamerPrivacyEnabled = p.getBoolean("streamerPrivacyEnabled", false)
        thermalGuardEnabled = p.getBoolean("thermalGuardEnabled", false)
        queueAlarmEnabled = p.getBoolean("queueAlarmEnabled", false)

        peakCpsRecord = p.getInt("peakCpsRecord", 0)
        installsCompleted = p.getInt("installsCompleted", 0)

        loadHudPositions(p.getString("hudPositionsJson", null))
    }

    fun persist() {
        val p = prefs ?: return
        p.edit().apply {
            putString("selectedClientVersion", selectedClientVersion)

            putString("equippedCapeId", equippedCapeId)
            putString("equippedHatId", equippedHatId)
            putBoolean("wavingCapeEnabled", wavingCapeEnabled)

            putBoolean("crosshairEnabled", crosshairEnabled)
            putString("crosshairShape", crosshairShape.name)
            putFloat("crosshairSize", crosshairSize)
            putFloat("crosshairGap", crosshairGap)
            putFloat("crosshairThickness", crosshairThickness)
            putFloat("crosshairOutline", crosshairOutline)
            putFloat("crosshairOpacity", crosshairOpacity)
            putBoolean("crosshairAttackPulse", crosshairAttackPulse)

            putBoolean("perfPillEnabled", perfPillEnabled)
            putBoolean("cpsPillEnabled", cpsPillEnabled)
            putBoolean("keystrokesEnabled", keystrokesEnabled)
            putBoolean("swordCooldownBarEnabled", swordCooldownBarEnabled)
            putBoolean("hitmarkerEnabled", hitmarkerEnabled)
            putBoolean("comboCounterEnabled", comboCounterEnabled)

            putBoolean("toggleSprintEnabled", toggleSprintEnabled)
            putBoolean("toggleSneakEnabled", toggleSneakEnabled)
            putBoolean("autoWalkEnabled", autoWalkEnabled)

            putBoolean("compassEnabled", compassEnabled)
            putBoolean("matchStatsEnabled", matchStatsEnabled)
            putBoolean("speedrunTimerEnabled", speedrunTimerEnabled)

            putBoolean("gyroAdsEnabled", gyroAdsEnabled)
            putFloat("gyroSensitivity", gyroSensitivity)
            putBoolean("palmShieldEnabled", palmShieldEnabled)
            putBoolean("hotbarSwipeEnabled", hotbarSwipeEnabled)
            putBoolean("quickPvpButtonsEnabled", quickPvpButtonsEnabled)

            putBoolean("fullbrightEnabled", fullbrightEnabled)
            putBoolean("autoGgEnabled", autoGgEnabled)
            putBoolean("streamerPrivacyEnabled", streamerPrivacyEnabled)
            putBoolean("thermalGuardEnabled", thermalGuardEnabled)
            putBoolean("queueAlarmEnabled", queueAlarmEnabled)

            putInt("peakCpsRecord", peakCpsRecord)
            putInt("installsCompleted", installsCompleted)

            putString("hudPositionsJson", encodeHudPositions())
            apply()
        }
    }

    fun awardXp(points: Int) {
        clientXp = (clientXp + points).coerceAtLeast(0)
    }

    /** Locked Crimson Red theme color — immutable across Aerix Client. */
    fun activeThemeColor(@Suppress("UNUSED_PARAMETER") nowMs: Long = 0L): Color =
        Color(LOCKED_CRIMSON_HEX)

    fun updateWidgetPos(id: String, xNorm: Float, yNorm: Float, scale: Float? = null) {
        hudPositions = hudPositions.toMutableMap().apply {
            val cur = this[id] ?: return@apply
            this[id] = cur.copy(
                xNorm = xNorm.coerceIn(0.01f, 0.92f),
                yNorm = yNorm.coerceIn(0.015f, 0.90f),
                scale = (scale ?: cur.scale).coerceIn(0.65f, 1.45f)
            )
        }
        persist()
    }

    fun resetHudPositions() {
        hudPositions = defaultHudPositions()
        persist()
    }

    /**
     * Verified non-overlapping HUD slots against PojavLauncher controls:
     * - Top-left DEBUG|CHAT|KEYBOARD|TAB (`0.04..0.46, 0.00..0.09`) & 3RD (`0.04..0.14, 0.09..0.18`) untouched.
     * - Top-right `A` button (`~0.77, 0.03`) & MOUSE (`0.89..1.00, 0.00..0.09`) untouched.
     * - Left D-pad / PRI / SEC / GUI / INV (`0.04..0.25, 0.54..0.98`) untouched.
     * - Bottom-center Vanilla 9-slot Hotbar & Hearts (`0.30..0.70, 0.78..1.00`) untouched.
     */
    private fun defaultHudPositions(): Map<String, HudWidgetPos> = mapOf(
        "client_btn" to HudWidgetPos("client_btn", "Client Menu Button", 0.57f, 0.018f, 1.0f),
        "perf" to HudWidgetPos("perf", "FPS & Telemetry", 0.02f, 0.21f, 1.0f),
        "cps" to HudWidgetPos("cps", "CPS Counter", 0.22f, 0.21f, 1.0f),
        "keystrokes" to HudWidgetPos("keystrokes", "Keystrokes", 0.02f, 0.29f, 1.0f),
        "compass" to HudWidgetPos("compass", "Direction Compass", 0.39f, 0.11f, 1.0f),
        "match" to HudWidgetPos("match", "Session Timer", 0.83f, 0.13f, 1.0f),
        "speedrun" to HudWidgetPos("speedrun", "Speedrun Stopwatch", 0.79f, 0.21f, 1.0f),
        "quick_pvp" to HudWidgetPos("quick_pvp", "PvP Action Pills", 0.88f, 0.33f, 1.0f),
        "hotbar_swipe" to HudWidgetPos("hotbar_swipe", "Touch Hotbar Strip", 0.35f, 0.70f, 1.0f)
    )

    private inline fun <reified E : Enum<E>> enumOrDefault(raw: String?, fallback: E): E {
        if (raw.isNullOrBlank()) return fallback
        return enumValues<E>().firstOrNull { it.name == raw } ?: fallback
    }

    private fun encodeHudPositions(): String {
        val arr = JSONArray()
        hudPositions.values.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("label", p.label)
                    .put("x", p.xNorm.toDouble())
                    .put("y", p.yNorm.toDouble())
                    .put("scale", p.scale.toDouble())
            )
        }
        return arr.toString()
    }

    private fun loadHudPositions(raw: String?) {
        val defaults = defaultHudPositions().toMutableMap()
        if (!raw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id")
                    val cur = defaults[id] ?: continue
                    defaults[id] = cur.copy(
                        xNorm = o.optDouble("x", cur.xNorm.toDouble()).toFloat().coerceIn(0.01f, 0.92f),
                        yNorm = o.optDouble("y", cur.yNorm.toDouble()).toFloat().coerceIn(0.015f, 0.90f),
                        scale = o.optDouble("scale", cur.scale.toDouble()).toFloat().coerceIn(0.65f, 1.45f)
                    )
                }
            }
        }
        hudPositions = defaults
    }
}
