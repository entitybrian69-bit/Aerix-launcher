package net.kdt.aerix.desktop.ui.theme

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Settings
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * The Aerix palette, ported value for value from
 * `app_pojavlauncher/.../aerix/theme/AerixSurface.kt`.
 *
 * Keeping the same numbers here is the whole point of the desktop port: a screenshot of the
 * Android launcher and a screenshot of the Windows launcher must be recognisably the same product.
 * The only deliberate changes are the platform ones — surfaces are drawn into an opaque window
 * instead of over a transparent activity, so alpha maths happens in [Glass] rather than in
 * Compose's elevation shadows.
 */
object AerixColors {
    val canvas = Color(0x07131D)

    // Neutral smoky glass (same values as AerixSurface.panel / panelRaised / panelTrack)
    val panelBase = Color(0x34, 0x38, 0x39)
    val panelRaisedBase = Color(0x51, 0x58, 0x58)
    val panelTrackBase = Color(0x4A, 0x50, 0x50)

    val border = Color(0xF2, 0xF7, 0xF5, 0x78)
    val borderSoft = Color(0xF2, 0xF7, 0xF5, 0x48)
    val borderHighlight = Color(0xF5, 0xFF, 0xFA, 0xDD)

    val glassTint = Color(0x7A8582)
    val glassBlue = Color(0x91E9FF)
    val glassViolet = Color(0xB7A9FF)
    val glassRose = Color(0xF3A9D9)
    val auroraCyan = Color(0x37DCEC)
    val auroraViolet = Color(0x7A71F5)
    val glassShadow = Color(0x00, 0x06, 0x10, 0x66)
    val glassShine = Color(0xFF, 0xFF, 0xFF, 0x48)

    /** Built-in accent (Aerix mint). */
    val accentDefault = Color(0x8DEFE0)
    val accentSecondary = Color(0xC1B5FF)
    val onAccentDefault = Color(0x06201D)
    val onAccentContainer = Color(0xBDFAF3)
    val accentLight = Color(0x006D73)

    val lightPanel = Color(0xFCFEFF)
    val lightTextPrimary = Color(0x14212B)

    val errorDarkContainer = Color(0x7A2635)
    val dangerContainer = Color(0x3B1A1E)
    val warningContainer = Color(0x3A2E16)
    val successContainer = Color(0x12352A)

    val modrinthBrand = Color(0x1BD96A)
    val curseForgeBrand = Color(0xF16436)

    val shadowSoft = Color(0, 0, 0, 0x3A)
    val scrimSoft = Color(0, 0, 0, 0x33)
    val scrimDialog = Color(0, 0, 0, 0x8C)

    val textPrimary = Color(0xF1F6FA)
    val textSecondary = Color(0xC8D1CF)
    val textMuted = Color(0x7D909D)
    val success = Color(0x73E5B0)
    val warning = Color(0xFFC777)
    val danger = Color(0xFF7189)

    // Log console tokens, from AerixSurface (values kept exactly).
    val logInfoSurface = Color(0x447152)
    val logDebugSurface = Color(0x43698D)
    val logWarningSurface = Color(0x656E76)
    val syntaxMuted = Color(0x6E7C83)
    val syntaxString = Color(0x6AAB73)
    val syntaxAccent = Color(0xC67CBA)
}

/** Live theme state: accent colour, transparency, and the derived surface colours. */
object AerixTheme {
    val accentValues = listOf(
        "Aerix Mint" to Color(0x8DEFE0),
        "Glacier Cyan" to Color(0x91E9FF),
        "Orchid Violet" to Color(0xB7A9FF),
        "Sakura Pink" to Color(0xF3A9D9),
        "Aurora Cyan" to Color(0x37DCEC),
        "Aurora Violet" to Color(0x7A71F5),
        "Emerald" to Color(0x73E5B0),
        "Sunset Amber" to Color(0xFFC777),
        "Crimson" to Color(0xFF7189),
        "Snow" to Color(0xDCEBF5)
    )

    @Volatile private var accentCached: Color = AerixColors.accentDefault

    val accent: Color get() = accentCached

    /** Accent on top of a dark surface, for text and thin strokes. */
    val accentSoft: Color get() = withAlpha(accent, 0x66)
    val accentGlow: Color get() = withAlpha(accent, 0x33)
    val accentContainer: Color get() = withAlpha(accent, 0x55)

    /** The text colour drawn on an accent-filled control. */
    val onAccent: Color
        get() = if (luminance(accent) > 0.45f) AerixColors.onAccentDefault else Color.WHITE

    /** 25..100, the "Layout transparency" slider. 100 is exactly the design. */
    fun layoutTransparency(): Int = Settings.int("layoutTransparency", 100).coerceIn(25, 100)

    /** 20..100, the wallpaper opacity slider (`launcherBackgroundOpacity`). */
    fun backgroundOpacity(): Int = Settings.int("launcherBackgroundOpacity", 60).coerceIn(20, 100)

    fun refresh() {
        accentCached = when (Settings.string("launcherColorTheme", "AERIX")) {
            "CUSTOM" -> Color(Settings.int("launcherCustomColor", AerixColors.accentDefault.rgb))
            else -> AerixColors.accentDefault
        }
        paintCache.clear()
    }

    fun panel(solid: Boolean = false): Color {
        val alpha = (0x66 * layoutTransparency() / 100f).toInt()
        return if (solid) Color(0x0F2130) else withAlpha(AerixColors.panelBase, alpha)
    }

    fun panelRaised(solid: Boolean = false): Color {
        val alpha = (0x80 * layoutTransparency() / 100f).toInt()
        return if (solid) Color(0x1B3243) else withAlpha(AerixColors.panelRaisedBase, alpha)
    }

    fun panelTrack(): Color {
        val alpha = (0x60 * layoutTransparency() / 100f).toInt()
        return withAlpha(AerixColors.panelTrackBase, alpha)
    }

    /** A hovered glass surface, slightly brighter than [panelRaised]. */
    fun panelHover(): Color = withAlpha(lighten(panelRaised(true), 0.12f), (0x9E * layoutTransparency() / 100))

    fun card(): Color = withAlpha(Color(0x1A2A38), (0xB4 * layoutTransparency() / 100))

    internal val paintCache = ConcurrentHashMap<String, Any>()
}

/** Spacing scale (dp on Android, px at 100% here), from AerixSpacing. */
object Spacing {
    const val xxs = 2
    const val tiny = 3
    const val xs = 4
    const val xsPlus = 5
    const val smCompact = 6
    const val sm = 8
    const val smPlus = 10
    const val md = 12
    const val mdPlus = 14
    const val lg = 16
    const val lgPlus = 18
    const val xl = 20
    const val xlPlus = 22
    const val xxl = 24
    const val xxxl = 32
    const val section = 40
}

/** Corner radii, from AerixRadii. */
object Radii {
    const val micro = 6
    const val compact = 8
    const val controlSmall = 10
    const val control = 12
    const val cardSmall = 16
    const val card = 18
    const val cardLarge = 20
    const val panelSmall = 22
    const val panel = 24
    const val dialog = 28
    const val hero = 32
    const val pill = 999
}

/** Metrics, from AerixMetrics (desktop values scale with DPI in [UiScale]). */
object Metrics {
    const val railWidth = 76
    const val railExpandedWidth = 208
    const val railButton = 46
    const val pageHeaderHeight = 52
    const val pillTabHeight = 38
    const val buttonHeight = 44
    const val buttonHeightLarge = 54
    const val inputHeight = 44
    const val titleBarHeight = 40
}

/** DPI scaling: one place that turns design px into device px. */
object UiScale {
    @Volatile var factor: Float = 1f
        private set
    private var base = 96

    fun update(dpi: Int) {
        base = if (dpi <= 0) 96 else dpi
        factor = (base / 96f).coerceIn(0.85f, 2.5f)
        Fonts.rebuild()
    }

    fun px(design: Int): Int = Math.round(design * factor)
    fun px(design: Float): Float = design * factor
}

/**
 * Typography. The Android launcher lets the player pick the launcher font; Poppins is the one the
 * screenshots and the shipped default use, and it is bundled (SIL OFL) so the desktop build never
 * depends on fonts installed on the machine.
 */
object Fonts {
    private const val BUNDLED = "/aerix/fonts/poppins.ttf"
    private var custom: Font? = null
    private val cache = ConcurrentHashMap<String, Font>()

    @Volatile var familyName: String = "SansSerif"
        private set

    init { rebuild() }

    fun rebuild() {
        cache.clear()
        val chosen = Settings.string("launcherFont", "Poppins")
        val loaded = when (chosen) {
            "Poppins" -> loadBundled()
            "System" -> null
            else -> SystemFonts.find(chosen)
        }
        custom = loaded
        familyName = loaded?.family ?: Font.SANS_SERIF
    }

    private fun loadBundled(): Font? {
        return runCatching {
            val stream: InputStream? = Fonts::class.java.getResourceAsStream(BUNDLED)
            if (stream == null) {
                Log.w("fonts", "bundled Poppins missing, falling back to the system font")
                return null
            }
            stream.use { Font.createFont(Font.TRUETYPE_FONT, it) }.also { f ->
                runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f) }
            }
        }.getOrNull()
    }

    /**
     * A font at the design size of [size], scaled for DPI.
     *
     * Poppins ships one weight; Swing's deriveFont can only synthesise bold, which looks close
     * enough at UI sizes and keeps the hierarchy (titles bold, body regular) the Android theme has.
     */
    fun font(size: Float, bold: Boolean = false): Font {
        val scaled = UiScale.px(size)
        val key = "$size/$bold/${UiScale.factor}"
        return cache.getOrPut(key) {
            val base = custom?.deriveFont(scaled) ?: Font(familyName, Font.PLAIN, Math.round(scaled))
            if (bold) base.deriveFont(Font.BOLD) else base
        }
    }

    // Named roles from the Compose Typography in AerixTheme.kt.
    fun display(size: Float = 34f) = font(size, bold = true)
    fun headline(size: Float = 24f) = font(size, bold = true)
    fun title(size: Float = 17f) = font(size, bold = true)
    fun titleMedium(size: Float = 15f) = font(size, bold = true)
    fun body(size: Float = 13.5f) = font(size)
    fun bodyStrong(size: Float = 13.5f) = font(size, bold = true)
    fun label(size: Float = 12f) = font(size)
    fun labelStrong(size: Float = 12f) = font(size, bold = true)
    fun caption(size: Float = 11f) = font(size)
    fun mono(size: Float = 12f): Font = monoAt(size)

    private val monoCache = ConcurrentHashMap<String, Font>()
    private fun monoAt(size: Float): Font =
        monoCache.getOrPut("$size/${UiScale.factor}") { Font(Font.MONOSPACED, Font.PLAIN, Math.round(UiScale.px(size))) }
}

/** Other bundled/installable fonts, mirroring AerixFonts' family list. */
object SystemFonts {
    private val available: Set<String> by lazy {
        runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
                .map { it.lowercase() }.toSet()
        }.getOrDefault(emptySet())
    }

    /** Every family the machine actually has, for the font picker. */
    fun families(): List<String> = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList().sorted()
    }.getOrDefault(emptyList())

    fun find(name: String): Font? {
        if (available.isEmpty()) return null
        val match = available.firstOrNull { it == name.lowercase() } ?: return null
        return Font(match, Font.PLAIN, 12)
    }
}

// ---- colour helpers ------------------------------------------------------------------------

fun withAlpha(c: Color, alpha: Int): Color = Color(c.red, c.green, c.blue, alpha.coerceIn(0, 255))

/** Multiplies the colour's own alpha, which is how AerixSurface.glass() behaves. */
fun scaledAlpha(c: Color, percent: Int): Color = Color(c.red, c.green, c.blue, (c.alpha * percent / 100f).toInt().coerceIn(0, 255))

fun mix(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        (a.red + (b.red - a.red) * f).toInt().coerceIn(0, 255),
        (a.green + (b.green - a.green) * f).toInt().coerceIn(0, 255),
        (a.blue + (b.blue - a.blue) * f).toInt().coerceIn(0, 255),
        (a.alpha + (b.alpha - a.alpha) * f).toInt().coerceIn(0, 255)
    )
}

fun lighten(c: Color, amount: Float): Color = mix(c, Color.WHITE, amount)
fun darken(c: Color, amount: Float): Color = mix(c, Color.BLACK, amount)

fun luminance(c: Color): Float {
    fun channel(v: Int): Float {
        val s = v / 255f
        return if (s <= 0.03928f) s / 12.92f else Math.pow(((s + 0.055) / 1.055).toDouble(), 2.4).toFloat()
    }
    return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
}

fun hueOf(c: Color): Float {
    val r = c.red / 255f; val g = c.green / 255f; val b = c.blue / 255f
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
    if (abs(d) < 1e-6f) return 0f
    val h = when (max) {
        r -> ((g - b) / d + (if (g < b) 6 else 0)) / 6f
        g -> ((b - r) / d + 2) / 6f
        else -> ((r - g) / d + 4) / 6f
    }
    return h
}

fun saturationOf(c: Color): Float {
    val r = c.red / 255f; val g = c.green / 255f; val b = c.blue / 255f
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    return if (max == 0f) 0f else (max - min) / max
}
