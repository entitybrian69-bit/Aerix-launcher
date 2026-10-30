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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class CrosshairShape(val label: String) {
    TACTICAL_GAP("Tactical Gap"),
    CLASSIC_PLUS("Classic +"),
    PRECISION_DOT("Precision Dot"),
    CIRCLE("Crystal Ring"),
    CIRCLE_DOT("Ring + Dot"),
    X_HIT("Tactical X"),
    CHEVRON("Chevron ^"),
    DIAMOND("Prism Diamond")
}

enum class KeystrokesStyle(val label: String) {
    LIQUID_CRYSTAL("Liquid Crystal"),
    MINIMAL_CLEAR("Minimal Clear"),
    CHROMA_PRISM("Chroma Prism"),
    CPS_PILL_ONLY("CPS Pill Only")
}

enum class TouchCurveMode(val label: String, val desc: String) {
    LINEAR_RAW("1:1 Raw Input", "Unmodified direct touch delta"),
    PVP_FLICK("PvP Flick Curve", "Steady micro-aim + fast 180° flicks"),
    SMOOTH_CINEMA("Smooth Gliding", "Damped camera motion for cinematic clips")
}

enum class VisualFilterPreset(val label: String, val tintArgb: Long, val desc: String) {
    NONE("Crystal Pure (Off)", 0x00000000L, "Unmodified display output"),
    VIBRANT_CRYSTAL("Vibrant Shaders-Lite", 0x1638F4E8L, "Lifted aqua-sunlit vibrance, 0 FPS cost"),
    CRISP_PVP("Crisp PvP Visibility", 0x18FFFFFFL, "Brightens dark caves & arena shadows"),
    WARM_SUNSET("Golden Hour", 0x1EFFA45BL, "Warm sunset atmosphere"),
    CINEMA("Cinema Contrast", 0x2407131DL, "Deep contrast mood"),
    NIGHT_SHIELD("Night Eye-Shield", 0x24FF9E44L, "Cuts harsh blue light late at night")
}

enum class ClientChromaTheme(val label: String, val primaryHex: Long, val secondaryHex: Long) {
    LIQUID_CRYSTAL("Liquid Crystal Ice", 0xFFC8FAFFL, 0xFF8DEFE0L),
    CRIMSON_SOVEREIGN("Crimson Sovereign", 0xFFFF4769L, 0xFFFF9EB0L),
    CELESTIAL_SKY("Celestial Sky", 0xFF5CE1FFL, 0xFFB8F2FFL),
    SAKURA_PRISM("Sakura Prism", 0xFFF7A8D8L, 0xFFFFD6EEL),
    AMETHYST_VOID("Amethyst Void", 0xFFB794FFL, 0xFFE2D4FFL),
    CHAMPION_GOLD("Champion Gold", 0xFFFFD166L, 0xFFFFF0BDL),
    WAVE_RGB("Synchronized Wave RGB", 0xFF8DEFE0L, 0xFF5CE1FFL)
}

enum class SkyboxTheme(val label: String) {
    AURORA_CRYSTAL("Aurora Crystal"),
    SAKURA_SUNSET("Sakura Horizon"),
    STARRY_NEBULA("Starry Nebula"),
    DEEP_VOID("Esports Deep Void")
}

enum class HitSoundPreset(val label: String, val soundEvent: String, val pitch: Float) {
    CRYSTAL_BELL("Crystal Bell Ding", "block.note_block.bell", 1.35f),
    AMETHYST_CHIME("Prism Chime", "block.note_block.chime", 1.45f),
    CRISP_CLICK("Mechanical Click", "block.note_block.hat", 1.25f),
    CLASSIC_ORB("Classic 1.8 Orb", "entity.experience_orb.pickup", 1.15f),
    VANILLA("Vanilla Default", "", 1.0f)
}

enum class AfkFarmAction(val label: String, val desc: String) {
    PASSIVE_HOLD("Passive Stand-Still", "Keeps session & Wi-Fi alive with OLED off"),
    AUTO_FISH_RCLICK("Hold Right-Click", "Continuous right-click hold (AFK Fish / Concrete)"),
    XP_GRINDER_SWING("Mob Grinder Sword Swing", "Timed left-click swing for XP farms (1.25s)"),
    ANTI_IDLE_PULSE("Anti-Idle Gentle Pulse", "Subtle movement tap every 40s to prevent idle kick")
}

enum class ClientLoadoutPreset(val label: String, val subtitle: String) {
    SPEEDRUN_1165("1.16.5 Speedrun", "FPS Pill + Speedrun Timer + Stronghold Ready"),
    PVP_COMBAT("PvP & Bedwars Claw", "Keystrokes + CPS + Tactical Crosshair + 180° Flick"),
    SURVIVAL_SMP("Technical Survival", "Compass + Waypoints + Fullbright + Coords"),
    CLEAN_CREATOR("Clean Creator", "Minimal Crystal Crosshair + Streamer Privacy")
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
    val dimension: String, // "Overworld" | "Nether" | "The End"
    val colorHex: Long = 0xFF8DEFE0L,
    val pinned: Boolean = false
) {
    fun netherMatchText(): String = when (dimension) {
        "Nether" -> "Overworld Match: X ${x * 8}, Z ${z * 8}"
        "Overworld" -> "Nether Portal: X ${x / 8}, Z ${z / 8}"
        else -> "The End · Chunk (${x shr 4}, ${z shr 4})"
    }
}

data class BuildChecklistItem(
    val id: String,
    val label: String,
    val amount: String,
    val done: Boolean = false
)

data class CosmeticOutfitSlot(
    val slot: Int,
    val name: String,
    val capeId: String,
    val hatId: String,
    val animatedCape: Boolean,
    val chromaTheme: String
)

data class EnderThrowSample(
    val x: Double,
    val z: Double,
    val yawDeg: Double
)

data class TriangulationResult(
    val strongholdX: Int,
    val strongholdZ: Int,
    val chunkX: Int,
    val chunkZ: Int,
    val distanceBlocks: Int,
    val netherPortalX: Int,
    val netherPortalZ: Int,
    val parallelWarning: Boolean = false
)

data class EnchantChoice(
    val id: String,
    val name: String,
    val maxLevel: Int,
    val bookMultiplier: Int
)

object AerixClientStore {
    const val SUPPORTED_MC_VERSION = "1.16.5"
    const val CLIENT_INSTANCE_NAME = "Aerix Client 1.16.5"
    private const val PREFS_FILE = "aerix_client_v1_prefs"

    private var prefs: SharedPreferences? = null

    /* ── 1-Click Installer Add-On Toggles ─────────────────────────────────── */
    var installFpsStack by mutableStateOf(true)
    var installShadersAddon by mutableStateOf(true)
    var installSurvivalPolishAddon by mutableStateOf(true)
    var installZoomAddon by mutableStateOf(true)
    var installClientResourcePack by mutableStateOf(true)

    /* ── Dynamic Resource Pack Tweaks (1.16.5 pack_format 6) ──────────────── */
    var packLowFire by mutableStateOf(true)
    var packShortSwords by mutableStateOf(true)
    var packLoweredShield by mutableStateOf(true)
    var packCompactTotem by mutableStateOf(true)
    var packOutlinedOres by mutableStateOf(true)
    var packClearWater by mutableStateOf(true)
    var packNoPumpkinBlur by mutableStateOf(true)
    var packBorderlessGlass by mutableStateOf(true)
    var packRgbBowStages by mutableStateOf(true)
    var packMiningProgressBar by mutableStateOf(true)
    var packCustomMainMenu by mutableStateOf(true)
    var packCustomMojangSplash by mutableStateOf(true)
    var packSkyboxTheme by mutableStateOf(SkyboxTheme.AURORA_CRYSTAL)
    var packHitSound by mutableStateOf(HitSoundPreset.CRYSTAL_BELL)

    /* ── Cosmetics Locker State ───────────────────────────────────────────── */
    var equippedCapeId by mutableStateOf("liquid_crystal")
    var equippedHatId by mutableStateOf("none")
    var animatedCapeShimmer by mutableStateOf(true)
    var customCapePixelsJson by mutableStateOf("")
    var outfitSlots by mutableStateOf<List<CosmeticOutfitSlot>>(emptyList())

    /* ── In-Game Client Modules ───────────────────────────────────────────── */
    var crosshairEnabled by mutableStateOf(true)
    var crosshairShape by mutableStateOf(CrosshairShape.TACTICAL_GAP)
    var crosshairSize by mutableFloatStateOf(10f)
    var crosshairGap by mutableFloatStateOf(4f)
    var crosshairThickness by mutableFloatStateOf(2f)
    var crosshairOutline by mutableFloatStateOf(1.5f)
    var crosshairOpacity by mutableFloatStateOf(0.95f)
    var crosshairChroma by mutableStateOf(false)
    var crosshairAttackPulse by mutableStateOf(true)

    var keystrokesEnabled by mutableStateOf(true)
    var keystrokesStyle by mutableStateOf(KeystrokesStyle.LIQUID_CRYSTAL)
    var cpsSparklineEnabled by mutableStateOf(true)
    var swordCooldownBarEnabled by mutableStateOf(true)
    var hitmarkerEnabled by mutableStateOf(true)
    var comboCounterEnabled by mutableStateOf(true)

    var toggleSprintEnabled by mutableStateOf(true)
    var toggleSneakEnabled by mutableStateOf(false)
    var autoWalkEnabled by mutableStateOf(false)

    var perfPillEnabled by mutableStateOf(true)
    var compassEnabled by mutableStateOf(true)
    var matchStatsEnabled by mutableStateOf(true)
    var speedrunTimerEnabled by mutableStateOf(false)
    var checklistPinEnabled by mutableStateOf(false)

    var gyroAdsEnabled by mutableStateOf(false)
    var gyroSensitivity by mutableFloatStateOf(1.0f)
    var touchCurveMode by mutableStateOf(TouchCurveMode.PVP_FLICK)
    var palmShieldEnabled by mutableStateOf(true)
    var hotbarSwipeEnabled by mutableStateOf(true)
    var quickPvpButtonsEnabled by mutableStateOf(true)

    var fullbrightEnabled by mutableStateOf(true)
    var visualFilter by mutableStateOf(VisualFilterPreset.NONE)
    var chromaTheme by mutableStateOf(ClientChromaTheme.LIQUID_CRYSTAL)

    var autoGgEnabled by mutableStateOf(true)
    var smartChatFilterEnabled by mutableStateOf(true)
    var streamerPrivacyEnabled by mutableStateOf(false)
    var thermalGuardEnabled by mutableStateOf(true)
    var queueAlarmEnabled by mutableStateOf(false)
    var queueAlertThreshold by mutableIntStateOf(5)

    var afkAction by mutableStateOf(AfkFarmAction.XP_GRINDER_SWING)
    var afkSwingIntervalMs by mutableLongStateOf(1250L)

    /* ── HUD Positions, Waypoints, Checklist & Progression ────────────────── */
    var hudPositions by mutableStateOf(defaultHudPositions())
    var waypoints by mutableStateOf<List<ClientWaypoint>>(emptyList())
    var checklist by mutableStateOf<List<BuildChecklistItem>>(emptyList())
    var quickMacros by mutableStateOf(
        listOf("/spawn", "/hub", "/home", "/tpa ", "/gamemode creative", "/gamemode survival", "gg", "inc!")
    )

    var clientXp by mutableIntStateOf(420)
    var installsCompleted by mutableIntStateOf(0)
    var peakCpsRecord by mutableIntStateOf(0)
    var sessionWins by mutableIntStateOf(0)
    var sessionStreak by mutableIntStateOf(0)

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        prefs = p

        installShadersAddon = p.getBoolean("installShadersAddon", true)
        installSurvivalPolishAddon = p.getBoolean("installSurvivalPolishAddon", true)
        installZoomAddon = p.getBoolean("installZoomAddon", true)
        installClientResourcePack = p.getBoolean("installClientResourcePack", true)

        packLowFire = p.getBoolean("packLowFire", true)
        packShortSwords = p.getBoolean("packShortSwords", true)
        packLoweredShield = p.getBoolean("packLoweredShield", true)
        packCompactTotem = p.getBoolean("packCompactTotem", true)
        packOutlinedOres = p.getBoolean("packOutlinedOres", true)
        packClearWater = p.getBoolean("packClearWater", true)
        packNoPumpkinBlur = p.getBoolean("packNoPumpkinBlur", true)
        packBorderlessGlass = p.getBoolean("packBorderlessGlass", true)
        packRgbBowStages = p.getBoolean("packRgbBowStages", true)
        packMiningProgressBar = p.getBoolean("packMiningProgressBar", true)
        packCustomMainMenu = p.getBoolean("packCustomMainMenu", true)
        packCustomMojangSplash = p.getBoolean("packCustomMojangSplash", true)
        packSkyboxTheme = enumOrDefault(p.getString("packSkyboxTheme", null), SkyboxTheme.AURORA_CRYSTAL)
        packHitSound = enumOrDefault(p.getString("packHitSound", null), HitSoundPreset.CRYSTAL_BELL)

        equippedCapeId = p.getString("equippedCapeId", "liquid_crystal") ?: "liquid_crystal"
        equippedHatId = p.getString("equippedHatId", "none") ?: "none"
        animatedCapeShimmer = p.getBoolean("animatedCapeShimmer", true)
        customCapePixelsJson = p.getString("customCapePixelsJson", "") ?: ""

        crosshairEnabled = p.getBoolean("crosshairEnabled", true)
        crosshairShape = enumOrDefault(p.getString("crosshairShape", null), CrosshairShape.TACTICAL_GAP)
        crosshairSize = p.getFloat("crosshairSize", 10f)
        crosshairGap = p.getFloat("crosshairGap", 4f)
        crosshairThickness = p.getFloat("crosshairThickness", 2f)
        crosshairOutline = p.getFloat("crosshairOutline", 1.5f)
        crosshairOpacity = p.getFloat("crosshairOpacity", 0.95f)
        crosshairChroma = p.getBoolean("crosshairChroma", false)
        crosshairAttackPulse = p.getBoolean("crosshairAttackPulse", true)

        keystrokesEnabled = p.getBoolean("keystrokesEnabled", true)
        keystrokesStyle = enumOrDefault(p.getString("keystrokesStyle", null), KeystrokesStyle.LIQUID_CRYSTAL)
        cpsSparklineEnabled = p.getBoolean("cpsSparklineEnabled", true)
        swordCooldownBarEnabled = p.getBoolean("swordCooldownBarEnabled", true)
        hitmarkerEnabled = p.getBoolean("hitmarkerEnabled", true)
        comboCounterEnabled = p.getBoolean("comboCounterEnabled", true)

        toggleSprintEnabled = p.getBoolean("toggleSprintEnabled", true)
        toggleSneakEnabled = p.getBoolean("toggleSneakEnabled", false)
        autoWalkEnabled = p.getBoolean("autoWalkEnabled", false)

        perfPillEnabled = p.getBoolean("perfPillEnabled", true)
        compassEnabled = p.getBoolean("compassEnabled", true)
        matchStatsEnabled = p.getBoolean("matchStatsEnabled", true)
        speedrunTimerEnabled = p.getBoolean("speedrunTimerEnabled", false)
        checklistPinEnabled = p.getBoolean("checklistPinEnabled", false)

        gyroAdsEnabled = p.getBoolean("gyroAdsEnabled", false)
        gyroSensitivity = p.getFloat("gyroSensitivity", 1.0f)
        touchCurveMode = enumOrDefault(p.getString("touchCurveMode", null), TouchCurveMode.PVP_FLICK)
        palmShieldEnabled = p.getBoolean("palmShieldEnabled", true)
        hotbarSwipeEnabled = p.getBoolean("hotbarSwipeEnabled", true)
        quickPvpButtonsEnabled = p.getBoolean("quickPvpButtonsEnabled", true)

        fullbrightEnabled = p.getBoolean("fullbrightEnabled", true)
        visualFilter = enumOrDefault(p.getString("visualFilter", null), VisualFilterPreset.NONE)
        chromaTheme = enumOrDefault(p.getString("chromaTheme", null), ClientChromaTheme.LIQUID_CRYSTAL)

        autoGgEnabled = p.getBoolean("autoGgEnabled", true)
        smartChatFilterEnabled = p.getBoolean("smartChatFilterEnabled", true)
        streamerPrivacyEnabled = p.getBoolean("streamerPrivacyEnabled", false)
        thermalGuardEnabled = p.getBoolean("thermalGuardEnabled", true)
        queueAlarmEnabled = p.getBoolean("queueAlarmEnabled", false)
        queueAlertThreshold = p.getInt("queueAlertThreshold", 5)

        afkAction = enumOrDefault(p.getString("afkAction", null), AfkFarmAction.XP_GRINDER_SWING)
        afkSwingIntervalMs = p.getLong("afkSwingIntervalMs", 1250L)

        clientXp = p.getInt("clientXp", 420)
        installsCompleted = p.getInt("installsCompleted", 0)
        peakCpsRecord = p.getInt("peakCpsRecord", 0)

        loadHudPositions(p.getString("hudPositionsJson", null))
        loadWaypoints(p.getString("waypointsJson", null))
        loadChecklist(p.getString("checklistJson", null))
        loadOutfits(p.getString("outfitsJson", null))
    }

    fun persist() {
        val p = prefs ?: return
        p.edit().apply {
            putBoolean("installShadersAddon", installShadersAddon)
            putBoolean("installSurvivalPolishAddon", installSurvivalPolishAddon)
            putBoolean("installZoomAddon", installZoomAddon)
            putBoolean("installClientResourcePack", installClientResourcePack)

            putBoolean("packLowFire", packLowFire)
            putBoolean("packShortSwords", packShortSwords)
            putBoolean("packLoweredShield", packLoweredShield)
            putBoolean("packCompactTotem", packCompactTotem)
            putBoolean("packOutlinedOres", packOutlinedOres)
            putBoolean("packClearWater", packClearWater)
            putBoolean("packNoPumpkinBlur", packNoPumpkinBlur)
            putBoolean("packBorderlessGlass", packBorderlessGlass)
            putBoolean("packRgbBowStages", packRgbBowStages)
            putBoolean("packMiningProgressBar", packMiningProgressBar)
            putBoolean("packCustomMainMenu", packCustomMainMenu)
            putBoolean("packCustomMojangSplash", packCustomMojangSplash)
            putString("packSkyboxTheme", packSkyboxTheme.name)
            putString("packHitSound", packHitSound.name)

            putString("equippedCapeId", equippedCapeId)
            putString("equippedHatId", equippedHatId)
            putBoolean("animatedCapeShimmer", animatedCapeShimmer)
            putString("customCapePixelsJson", customCapePixelsJson)

            putBoolean("crosshairEnabled", crosshairEnabled)
            putString("crosshairShape", crosshairShape.name)
            putFloat("crosshairSize", crosshairSize)
            putFloat("crosshairGap", crosshairGap)
            putFloat("crosshairThickness", crosshairThickness)
            putFloat("crosshairOutline", crosshairOutline)
            putFloat("crosshairOpacity", crosshairOpacity)
            putBoolean("crosshairChroma", crosshairChroma)
            putBoolean("crosshairAttackPulse", crosshairAttackPulse)

            putBoolean("keystrokesEnabled", keystrokesEnabled)
            putString("keystrokesStyle", keystrokesStyle.name)
            putBoolean("cpsSparklineEnabled", cpsSparklineEnabled)
            putBoolean("swordCooldownBarEnabled", swordCooldownBarEnabled)
            putBoolean("hitmarkerEnabled", hitmarkerEnabled)
            putBoolean("comboCounterEnabled", comboCounterEnabled)

            putBoolean("toggleSprintEnabled", toggleSprintEnabled)
            putBoolean("toggleSneakEnabled", toggleSneakEnabled)
            putBoolean("autoWalkEnabled", autoWalkEnabled)

            putBoolean("perfPillEnabled", perfPillEnabled)
            putBoolean("compassEnabled", compassEnabled)
            putBoolean("matchStatsEnabled", matchStatsEnabled)
            putBoolean("speedrunTimerEnabled", speedrunTimerEnabled)
            putBoolean("checklistPinEnabled", checklistPinEnabled)

            putBoolean("gyroAdsEnabled", gyroAdsEnabled)
            putFloat("gyroSensitivity", gyroSensitivity)
            putString("touchCurveMode", touchCurveMode.name)
            putBoolean("palmShieldEnabled", palmShieldEnabled)
            putBoolean("hotbarSwipeEnabled", hotbarSwipeEnabled)
            putBoolean("quickPvpButtonsEnabled", quickPvpButtonsEnabled)

            putBoolean("fullbrightEnabled", fullbrightEnabled)
            putString("visualFilter", visualFilter.name)
            putString("chromaTheme", chromaTheme.name)

            putBoolean("autoGgEnabled", autoGgEnabled)
            putBoolean("smartChatFilterEnabled", smartChatFilterEnabled)
            putBoolean("streamerPrivacyEnabled", streamerPrivacyEnabled)
            putBoolean("thermalGuardEnabled", thermalGuardEnabled)
            putBoolean("queueAlarmEnabled", queueAlarmEnabled)
            putInt("queueAlertThreshold", queueAlertThreshold)

            putString("afkAction", afkAction.name)
            putLong("afkSwingIntervalMs", afkSwingIntervalMs)

            putInt("clientXp", clientXp)
            putInt("installsCompleted", installsCompleted)
            putInt("peakCpsRecord", peakCpsRecord)

            putString("hudPositionsJson", encodeHudPositions())
            putString("waypointsJson", encodeWaypoints())
            putString("checklistJson", encodeChecklist())
            putString("outfitsJson", encodeOutfits())
            apply()
        }
    }

    fun awardXp(points: Int) {
        clientXp = (clientXp + points).coerceAtLeast(0)
        persist()
    }

    fun clientLevel(): Int = 1 + (clientXp / 150)

    fun clientRankLabel(): String = when (val lvl = clientLevel()) {
        in 1..3 -> "Crystal Initiate · Lv.$lvl"
        in 4..7 -> "Silver Prism · Lv.$lvl"
        in 8..14 -> "Diamond Crystal · Lv.$lvl"
        in 15..24 -> "Amethyst Vanguard · Lv.$lvl"
        else -> "Celestial Sovereign · Lv.$lvl"
    }

    fun levelProgressFraction(): Float = ((clientXp % 150) / 150f).coerceIn(0.04f, 1f)

    fun activeThemeColor(nowMs: Long = System.currentTimeMillis()): Color {
        if (chromaTheme == ClientChromaTheme.WAVE_RGB) {
            val hue = ((nowMs / 22L) % 360L).toFloat()
            return Color.hsv(hue, 0.55f, 1.0f)
        }
        return Color(chromaTheme.primaryHex)
    }

    fun applyLoadout(preset: ClientLoadoutPreset) {
        when (preset) {
            ClientLoadoutPreset.SPEEDRUN_1165 -> {
                perfPillEnabled = true
                speedrunTimerEnabled = true
                compassEnabled = true
                keystrokesEnabled = false
                matchStatsEnabled = false
                quickPvpButtonsEnabled = false
                fullbrightEnabled = true
                crosshairShape = CrosshairShape.CLASSIC_PLUS
            }
            ClientLoadoutPreset.PVP_COMBAT -> {
                perfPillEnabled = true
                keystrokesEnabled = true
                cpsSparklineEnabled = true
                swordCooldownBarEnabled = true
                quickPvpButtonsEnabled = true
                toggleSprintEnabled = true
                matchStatsEnabled = true
                speedrunTimerEnabled = false
                crosshairShape = CrosshairShape.TACTICAL_GAP
            }
            ClientLoadoutPreset.SURVIVAL_SMP -> {
                perfPillEnabled = true
                compassEnabled = true
                checklistPinEnabled = true
                keystrokesEnabled = false
                quickPvpButtonsEnabled = false
                fullbrightEnabled = true
                visualFilter = VisualFilterPreset.VIBRANT_CRYSTAL
            }
            ClientLoadoutPreset.CLEAN_CREATOR -> {
                perfPillEnabled = false
                keystrokesEnabled = false
                compassEnabled = false
                matchStatsEnabled = false
                quickPvpButtonsEnabled = false
                streamerPrivacyEnabled = true
                crosshairShape = CrosshairShape.PRECISION_DOT
            }
        }
        persist()
    }

    fun updateWidgetPos(id: String, xNorm: Float, yNorm: Float, scale: Float? = null) {
        hudPositions = hudPositions.toMutableMap().apply {
            val cur = this[id] ?: return@apply
            this[id] = cur.copy(
                xNorm = xNorm.coerceIn(0.01f, 0.92f),
                yNorm = yNorm.coerceIn(0.02f, 0.90f),
                scale = (scale ?: cur.scale).coerceIn(0.6f, 1.5f)
            )
        }
        persist()
    }

    fun resetHudPositions() {
        hudPositions = defaultHudPositions()
        persist()
    }

    private fun defaultHudPositions(): Map<String, HudWidgetPos> = mapOf(
        "perf" to HudWidgetPos("perf", "Performance Pill", 0.02f, 0.03f, 1.0f),
        "compass" to HudWidgetPos("compass", "Direction Compass", 0.33f, 0.03f, 1.0f),
        "match" to HudWidgetPos("match", "Match & Session Pill", 0.72f, 0.03f, 1.0f),
        "keystrokes" to HudWidgetPos("keystrokes", "Keystrokes + CPS", 0.02f, 0.14f, 1.0f),
        "speedrun" to HudWidgetPos("speedrun", "Speedrun Split Timer", 0.02f, 0.56f, 1.0f),
        "checklist" to HudWidgetPos("checklist", "Build Checklist", 0.76f, 0.16f, 1.0f),
        "quick_pvp" to HudWidgetPos("quick_pvp", "Claw PvP Buttons", 0.87f, 0.42f, 1.0f),
        "hotbar_swipe" to HudWidgetPos("hotbar_swipe", "Hotbar Swipe Strip", 0.31f, 0.88f, 1.0f)
    )

    /* ── Waypoint, F3+C Parser, Stronghold Triangulator & Anvil Math ──────── */

    fun addWaypoint(name: String, x: Int, y: Int, z: Int, dimension: String, pin: Boolean = false) {
        val clean = name.trim().ifEmpty { "Waypoint ${waypoints.size + 1}" }
        val item = ClientWaypoint(
            id = "wp_${System.currentTimeMillis()}",
            name = clean,
            x = x,
            y = y,
            z = z,
            dimension = dimension,
            pinned = pin
        )
        waypoints = if (pin) {
            waypoints.map { it.copy(pinned = false) } + item
        } else {
            waypoints + item
        }
        awardXp(15)
        persist()
    }

    fun togglePinWaypoint(id: String) {
        waypoints = waypoints.map {
            if (it.id == id) it.copy(pinned = !it.pinned) else it.copy(pinned = false)
        }
        persist()
    }

    fun removeWaypoint(id: String) {
        waypoints = waypoints.filterNot { it.id == id }
        persist()
    }

    /**
     * Parses Minecraft Java's `F3+C` clipboard output:
     * `/execute in minecraft:overworld run tp @s 142.50 69.00 -840.25 -142.30 12.10`
     */
    fun parseF3CClipboard(raw: String?): Triple<Triple<Double, Double, Double>, Double, String>? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        val dim = when {
            text.contains("the_nether", ignoreCase = true) -> "Nether"
            text.contains("the_end", ignoreCase = true) -> "The End"
            else -> "Overworld"
        }
        val tpIdx = text.indexOf("@s")
        val numsPart = if (tpIdx >= 0) text.substring(tpIdx + 2).trim() else text
        val tokens = numsPart.split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
        if (tokens.size < 3) return null
        val x = tokens[0]
        val y = tokens[1]
        val z = tokens[2]
        val yaw = if (tokens.size >= 4) tokens[3] else 0.0
        return Triple(Triple(x, y, z), yaw, dim)
    }

    /**
     * Exact 2-Throw Eye-of-Ender ray intersection for 1.16.5 Speedrunning!
     * In Minecraft, yaw `θ` (degrees) has unit direction vector: `dx = -sin(θ)`, `dz = cos(θ)`.
     */
    fun triangulateStronghold(t1: EnderThrowSample, t2: EnderThrowSample): TriangulationResult? {
        val r1 = t1.yawDeg * PI / 180.0
        val r2 = t2.yawDeg * PI / 180.0
        val dx1 = -sin(r1)
        val dz1 = cos(r1)
        val dx2 = -sin(r2)
        val dz2 = cos(r2)

        val det = dx1 * dz2 - dz1 * dx2
        if (abs(det) < 1e-4) {
            return null
        }
        val relX = t2.x - t1.x
        val relZ = t2.z - t1.z
        val s = (relX * dz2 - relZ * dx2) / det
        val ix = t1.x + s * dx1
        val iz = t1.z + s * dz1
        // Snap to 1.16.5 Stronghold starter staircase (4, 4 offset inside chunk)
        val chunkX = kotlin.math.floor(ix / 16.0).toInt()
        val chunkZ = kotlin.math.floor(iz / 16.0).toInt()
        val snappedX = chunkX * 16 + 4
        val snappedZ = chunkZ * 16 + 4
        val dist = sqrt((snappedX - t2.x) * (snappedX - t2.x) + (snappedZ - t2.z) * (snappedZ - t2.z)).roundToInt()
        return TriangulationResult(
            strongholdX = snappedX,
            strongholdZ = snappedZ,
            chunkX = chunkX,
            chunkZ = chunkZ,
            distanceBlocks = dist,
            netherPortalX = snappedX / 8,
            netherPortalZ = snappedZ / 8,
            parallelWarning = abs(det) < 0.06
        )
    }

    val swordEnchantCatalog = listOf(
        EnchantChoice("sharp5", "Sharpness V", 5, 1),
        EnchantChoice("loot3", "Looting III", 3, 2),
        EnchantChoice("unb3", "Unbreaking III", 3, 1),
        EnchantChoice("sweep3", "Sweeping Edge III", 3, 2),
        EnchantChoice("fire2", "Fire Aspect II", 2, 2),
        EnchantChoice("mend1", "Mending", 1, 2),
        EnchantChoice("kb2", "Knockback II", 2, 1)
    )

    /**
     * Computes a balanced binary anvil tree sorted descending by sacrifice cost so prior-work
     * penalties stay minimal and never trigger the 40-level "Too Expensive!" cap.
     */
    fun optimizeAnvilOrder(itemName: String, selectedIds: Set<String>): Pair<Int, List<String>> {
        val books = swordEnchantCatalog.filter { it.id in selectedIds }
            .sortedByDescending { it.maxLevel * it.bookMultiplier }
        if (books.isEmpty()) return 0 to emptyList()

        data class Node(val label: String, val valueCost: Int, val workCount: Int, val isTargetItem: Boolean)

        val pool = ArrayList<Node>()
        pool.add(Node(itemName, 0, 0, true))
        books.forEach { b ->
            pool.add(Node("${b.name} Book", b.maxLevel * b.bookMultiplier, 0, false))
        }

        val steps = ArrayList<String>()
        var totalLevels = 0
        var stepNum = 1
        while (pool.size > 1) {
            val nextRound = ArrayList<Node>()
            var idx = 0
            while (idx < pool.size) {
                if (idx + 1 < pool.size) {
                    val a = pool[idx]
                    val b = pool[idx + 1]
                    val target = if (b.isTargetItem) b else if (a.isTargetItem) a else if (a.valueCost >= b.valueCost) a else b
                    val sac = if (target === a) b else a
                    val penalty = ((1 shl target.workCount) - 1) + ((1 shl sac.workCount) - 1)
                    val stepCost = sac.valueCost + penalty
                    totalLevels += stepCost
                    val newName = if (target.isTargetItem) itemName else "(${target.label} + ${sac.label})"
                    steps.add("Step $stepNum: ${target.label} + ${sac.label}  [$stepCost XP lvls]")
                    stepNum++
                    nextRound.add(
                        Node(
                            label = newName,
                            valueCost = target.valueCost + sac.valueCost,
                            workCount = maxOf(target.workCount, sac.workCount) + 1,
                            isTargetItem = target.isTargetItem || sac.isTargetItem
                        )
                    )
                    idx += 2
                } else {
                    nextRound.add(pool[idx])
                    idx += 1
                }
            }
            pool.clear()
            pool.addAll(nextRound)
        }
        return totalLevels to steps
    }

    fun formatShulkerBreakdown(totalBlocks: Int): String {
        val n = totalBlocks.coerceAtLeast(0)
        val perShulker = 27 * 64 // 1,728
        val shulkers = n / perShulker
        val remAfterShulker = n % perShulker
        val stacks = remAfterShulker / 64
        val loose = remAfterShulker % 64
        val doubleChests = n / (54 * 64.0)
        return "$shulkers Shulker Boxes + $stacks Stacks + $loose Blocks  (%.2f Double Chests)".format(doubleChests)
    }

    /* ── Checklist & Outfits ──────────────────────────────────────────────── */

    fun addChecklistItem(label: String, amount: String) {
        if (label.isBlank()) return
        checklist = checklist + BuildChecklistItem(
            id = "chk_${System.currentTimeMillis()}",
            label = label.trim(),
            amount = amount.trim().ifEmpty { "x64" }
        )
        persist()
    }

    fun toggleChecklistDone(id: String) {
        checklist = checklist.map { if (it.id == id) it.copy(done = !it.done) else it }
        persist()
    }

    fun removeChecklistItem(id: String) {
        checklist = checklist.filterNot { it.id == id }
        persist()
    }

    fun saveCurrentToOutfitSlot(slotNumber: Int, customLabel: String? = null) {
        val slot = slotNumber.coerceIn(1, 10)
        val entry = CosmeticOutfitSlot(
            slot = slot,
            name = customLabel?.takeIf { it.isNotBlank() } ?: "Crystal Outfit #$slot",
            capeId = equippedCapeId,
            hatId = equippedHatId,
            animatedCape = animatedCapeShimmer,
            chromaTheme = chromaTheme.name
        )
        outfitSlots = (outfitSlots.filterNot { it.slot == slot } + entry).sortedBy { it.slot }
        awardXp(10)
        persist()
    }

    fun equipOutfitSlot(entry: CosmeticOutfitSlot) {
        equippedCapeId = entry.capeId
        equippedHatId = entry.hatId
        animatedCapeShimmer = entry.animatedCape
        chromaTheme = enumOrDefault(entry.chromaTheme, ClientChromaTheme.LIQUID_CRYSTAL)
        persist()
    }

    /* ── Cloud Backup / Shareable Config Code ─────────────────────────────── */

    fun exportConfigCode(): String {
        val obj = JSONObject()
            .put("cape", equippedCapeId)
            .put("hat", equippedHatId)
            .put("xhairShape", crosshairShape.name)
            .put("xhairSize", crosshairSize.toDouble())
            .put("xhairGap", crosshairGap.toDouble())
            .put("keysStyle", keystrokesStyle.name)
            .put("chroma", chromaTheme.name)
            .put("filter", visualFilter.name)
            .put("fullbright", fullbrightEnabled)
            .put("hud", encodeHudPositions())
        return "AERIX1165:" + android.util.Base64.encodeToString(
            obj.toString().toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
    }

    fun importConfigCode(code: String): Boolean = runCatching {
        val clean = code.trim().removePrefix("AERIX1165:")
        val json = JSONObject(String(android.util.Base64.decode(clean, android.util.Base64.DEFAULT), Charsets.UTF_8))
        equippedCapeId = json.optString("cape", equippedCapeId)
        equippedHatId = json.optString("hat", equippedHatId)
        crosshairShape = enumOrDefault(json.optString("xhairShape"), crosshairShape)
        crosshairSize = json.optDouble("xhairSize", crosshairSize.toDouble()).toFloat()
        crosshairGap = json.optDouble("xhairGap", crosshairGap.toDouble()).toFloat()
        keystrokesStyle = enumOrDefault(json.optString("keysStyle"), keystrokesStyle)
        chromaTheme = enumOrDefault(json.optString("chroma"), chromaTheme)
        visualFilter = enumOrDefault(json.optString("filter"), visualFilter)
        fullbrightEnabled = json.optBoolean("fullbright", fullbrightEnabled)
        val hudRaw = json.optString("hud", "")
        if (hudRaw.isNotEmpty()) loadHudPositions(hudRaw)
        persist()
        true
    }.getOrDefault(false)

    /* ── JSON Serialization Helpers ───────────────────────────────────────── */

    private fun encodeHudPositions(): String {
        val arr = JSONArray()
        hudPositions.values.forEach { w ->
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("label", w.label)
                    .put("x", w.xNorm.toDouble())
                    .put("y", w.yNorm.toDouble())
                    .put("s", w.scale.toDouble())
            )
        }
        return arr.toString()
    }

    private fun loadHudPositions(raw: String?) {
        val base = defaultHudPositions().toMutableMap()
        if (!raw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id")
                    val cur = base[id] ?: continue
                    base[id] = cur.copy(
                        xNorm = o.optDouble("x", cur.xNorm.toDouble()).toFloat().coerceIn(0.01f, 0.92f),
                        yNorm = o.optDouble("y", cur.yNorm.toDouble()).toFloat().coerceIn(0.02f, 0.90f),
                        scale = o.optDouble("s", cur.scale.toDouble()).toFloat().coerceIn(0.6f, 1.5f)
                    )
                }
            }
        }
        hudPositions = base
    }

    private fun encodeWaypoints(): String {
        val arr = JSONArray()
        waypoints.forEach { w ->
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("name", w.name)
                    .put("x", w.x)
                    .put("y", w.y)
                    .put("z", w.z)
                    .put("dim", w.dimension)
                    .put("col", w.colorHex)
                    .put("pin", w.pinned)
            )
        }
        return arr.toString()
    }

    private fun loadWaypoints(raw: String?) {
        if (raw.isNullOrBlank()) {
            waypoints = listOf(
                ClientWaypoint("wp_spawn", "Spawn Portal", 0, 68, 0, "Overworld", 0xFF8DEFE0L, true)
            )
            return
        }
        runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<ClientWaypoint>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    ClientWaypoint(
                        id = o.optString("id", "wp_$i"),
                        name = o.optString("name", "Waypoint"),
                        x = o.optInt("x", 0),
                        y = o.optInt("y", 64),
                        z = o.optInt("z", 0),
                        dimension = o.optString("dim", "Overworld"),
                        colorHex = o.optLong("col", 0xFF8DEFE0L),
                        pinned = o.optBoolean("pin", false)
                    )
                )
            }
            waypoints = list
        }
    }

    private fun encodeChecklist(): String {
        val arr = JSONArray()
        checklist.forEach { c ->
            arr.put(JSONObject().put("id", c.id).put("label", c.label).put("amt", c.amount).put("done", c.done))
        }
        return arr.toString()
    }

    private fun loadChecklist(raw: String?) {
        if (raw.isNullOrBlank()) {
            checklist = listOf(
                BuildChecklistItem("c1", "Obsidian (Portal)", "x10", false),
                BuildChecklistItem("c2", "Ender Pearls", "x12", false),
                BuildChecklistItem("c3", "Blaze Rods", "x7", false)
            )
            return
        }
        runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<BuildChecklistItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    BuildChecklistItem(
                        id = o.optString("id", "c_$i"),
                        label = o.optString("label", "Item"),
                        amount = o.optString("amt", "x64"),
                        done = o.optBoolean("done", false)
                    )
                )
            }
            checklist = list
        }
    }

    private fun encodeOutfits(): String {
        val arr = JSONArray()
        outfitSlots.forEach { s ->
            arr.put(
                JSONObject()
                    .put("slot", s.slot)
                    .put("name", s.name)
                    .put("cape", s.capeId)
                    .put("hat", s.hatId)
                    .put("anim", s.animatedCape)
                    .put("chroma", s.chromaTheme)
            )
        }
        return arr.toString()
    }

    private fun loadOutfits(raw: String?) {
        if (raw.isNullOrBlank()) {
            outfitSlots = listOf(
                CosmeticOutfitSlot(1, "Crystal Signature", "liquid_crystal", "golden_crown", true, ClientChromaTheme.LIQUID_CRYSTAL.name),
                CosmeticOutfitSlot(2, "Nebula Wingman", "nebula_supernova", "crystal_halo", true, ClientChromaTheme.AMETHYST_VOID.name)
            )
            return
        }
        runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<CosmeticOutfitSlot>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    CosmeticOutfitSlot(
                        slot = o.optInt("slot", i + 1),
                        name = o.optString("name", "Outfit #${i + 1}"),
                        capeId = o.optString("cape", "liquid_crystal"),
                        hatId = o.optString("hat", "none"),
                        animatedCape = o.optBoolean("anim", true),
                        chromaTheme = o.optString("chroma", ClientChromaTheme.LIQUID_CRYSTAL.name)
                    )
                )
            }
            outfitSlots = list
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return T::class.java.enumConstants?.firstOrNull { it.name == raw } ?: fallback
    }
}
