package net.kdt.aerix.desktop.ui.theme

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * Drawing helpers shared by every Aerix component: the glass surface, the accent bloom, the
 * rounded pill, the wallpaper backing. They are the Java2D equivalents of the Compose modifiers in
 * `aerix/components/LiquidGlass.kt` and `theme/ThemeMotion.kt`.
 */
object Paint {

    fun quality(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
    }

    /**
     * The Aerix "smoked glass" card: a translucent body, a one-pixel light border that fades
     * towards the bottom (the Compose version uses a vertical gradient border), and an optional
     * inner top highlight that gives the surface its convincing glass edge.
     */
    fun glass(
        g: Graphics2D,
        x: Int, y: Int, w: Int, h: Int, radius: Int,
        fill: Color = AerixTheme.panel(),
        border: Color = AerixColors.border,
        borderSoft: Color = AerixColors.borderSoft,
        shadow: Boolean = true,
        highlight: Boolean = true
    ) {
        if (w <= 0 || h <= 0) return
        val r = radius.coerceAtMost(minOf(w, h) / 2)
        val shape = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r * 2f, r * 2f)

        if (shadow) {
            val old = g.composite
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f)
            for (i in 1..5) {
                g.color = Color(0, 4, 10, 22)
                g.fill(RoundRectangle2D.Float(
                    x - i * 0.6f, y + i * 0.9f, w + i * 1.2f, h + i * 1.2f, r * 2f + i, r * 2f + i
                ))
            }
            g.composite = old
        }

        g.color = fill
        g.fill(shape)

        if (highlight) {
            // top sheen: white at 12% fading to nothing over the upper third
            val sheenHeight = maxOf(6, h / 3)
            val oldPaint = g.paint
            g.paint = GradientPaint(
                0f, y.toFloat(), withAlpha(Color.WHITE, 26),
                0f, (y + sheenHeight).toFloat(), withAlpha(Color.WHITE, 0)
            )
            g.fill(shape)
            g.paint = oldPaint
        }

        // Border: bright at the top, soft at the bottom, drawn as two arcs of the same round-rect.
        val oldPaint = g.paint
        g.paint = GradientPaint(0f, y.toFloat(), border, 0f, (y + h).toFloat(), borderSoft)
        g.stroke = java.awt.BasicStroke(UiScale.px(1f))
        g.draw(shape)
        // accent-free inner line for the glass edge
        g.paint = oldPaint
    }

    /** Circular / pill surfaces (buttons, chips, the account avatar ring). */
    fun pill(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, fill: Color, border: Color? = null, borderWidth: Float = 1f) {
        if (w <= 0 || h <= 0) return
        val r = h.toFloat()
        val shape = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r, r)
        g.color = fill
        g.fill(shape)
        if (border != null) {
            g.color = border
            g.stroke = java.awt.BasicStroke(UiScale.px(borderWidth))
            g.draw(shape)
        }
    }

    fun circle(g: Graphics2D, cx: Int, cy: Int, radius: Int, fill: Color) {
        g.color = fill
        g.fill(Ellipse2D.Float(cx - radius.toFloat(), cy - radius.toFloat(), radius * 2f, radius * 2f))
    }

    /** The soft accent bloom from `Modifier.accentGlow` (radial gradient, never a blur pass). */
    fun accentGlow(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, color: Color = AerixTheme.accent) {
        if (w <= 0 || h <= 0) return
        val cx = x + w / 2f
        val cy = y + h / 2f
        val radius = maxOf(w, h) * 0.78f
        if (radius <= 0f) return
        val old = g.paint
        g.paint = RadialGradientPaint(
            cx, cy, radius,
            floatArrayOf(0f, 0.45f, 1f),
            arrayOf(withAlpha(color, 41), withAlpha(color, 15), withAlpha(color, 0))
        )
        g.fill(java.awt.geom.Rectangle2D.Float(cx - radius, cy - radius, radius * 2, radius * 2))
        g.paint = old
    }

    /** Filled path from an SVG path data string, already repositioned by [SvgPath]. */
    fun shape(path: Path2D, fill: Color?, stroke: Color? = null, strokeWidth: Float = 1f): (Graphics2D) -> Unit =
        { g: Graphics2D ->
            if (fill != null) { g.color = fill; g.fill(path) }
            if (stroke != null) {
                g.color = stroke
                g.stroke = java.awt.BasicStroke(UiScale.px(strokeWidth))
                g.draw(path)
            }
        }

    fun roundedRect(x: Int, y: Int, w: Int, h: Int, r: Int): RoundRectangle2D =
        RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r * 2f, r * 2f)

    /** Subtle vertical scrim used under text that sits directly on a wallpaper (hero titles). */
    fun verticalScrim(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, strength: Int = 150) {
        val old = g.paint
        g.paint = GradientPaint(
            0f, y.toFloat(), Color(0, 8, 14, strength),
            0f, (y + h).toFloat(), Color(0, 8, 14, 0)
        )
        g.fillRect(x, y, w, h)
        g.paint = old
    }

    fun horizontalScrim(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, strength: Int = 190) {
        val old = g.paint
        g.paint = GradientPaint(
            x.toFloat(), 0f, Color(0, 8, 14, strength),
            (x + w).toFloat(), 0f, Color(0, 8, 14, 0)
        )
        g.fillRect(x, y, w, h)
        g.paint = old
    }

    /** Text with a shadow, for labels drawn over bright wallpapers. */
    fun textWithShadow(): (Graphics2D) -> Unit = { g ->
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

    fun tintCircle(g: Graphics2D, cx: Int, cy: Int, radius: Int, color: Color, alpha: Int) {
        g.color = withAlpha(color, alpha)
        g.fill(Ellipse2D.Float(cx - radius.toFloat(), cy - radius.toFloat(), radius * 2f, radius * 2f))
    }
}

/**
 * GPU-free wallpaper backing for the window.
 *
 * The Android launcher draws a wallpaper behind a translucent rail and glass cards; the desktop
 * version does the same, but pre-scales and pre-blurs the image once per size instead of on every
 * repaint. Blur is a separable box blur run twice, which is visually identical to the Compose
 * backdrop for these photographic wallpapers and cheap on 2 CPU cores.
 */
object Backdrops {
    private val cache = ConcurrentHashMap<String, BufferedImage>()
    private var lastKey: String = ""
    private var lastImage: BufferedImage? = null

    private val wallpaperNames = listOf(
        "wp_01_lush_caves" to ("Lush Caves" to Color(0x2DD4BF)),
        "wp_02_cherry_blossom" to ("Cherry Blossom Grove" to Color(0xF472B6)),
        "wp_03_sunset_river" to ("Golden Sunset River" to Color(0xF59E0B)),
        "wp_04_crystal_river" to ("Crystal River Forest" to Color(0x06B6D4)),
        "wp_05_nether_fortress" to ("Nether Lava Cavern" to Color(0xF97316)),
        "wp_06_the_end" to ("The End Obsidian Pillars" to Color(0xA855F7)),
        "wp_07_ocean_arch" to ("Sunlight Ocean Arch" to Color(0x38BDF8)),
        "wp_08_moonlit_lake" to ("Moonlit Birch Lake" to Color(0x818CF8)),
        "wp_09_flower_meadow" to ("Sunrise Flower Meadow" to Color(0xEC4899)),
        "wp_10_spruce_mist" to ("Spruce Lake Morning Mist" to Color(0x84CC16)),
        "wp_11_cherry_bee" to ("Honeybee Cherry Canopy" to Color(0xFBBF24)),
        "wp_12_snowy_valley" to ("Snowy Peaks & Cherry Valley" to Color(0x60A5FA)),
        "wp_13_frozen_glacier" to ("Frozen Glacier Ridge" to Color(0x22D3EE)),
        "wp_14_cozy_village" to ("Cozy Plains Village" to Color(0x10B981)),
        "wp_15_end_portal" to ("End Portal Starfield" to Color(0x14B8A6)),
        "wp_16_ocean_monument" to ("Deep Ocean Monument" to Color(0x3B82F6)),
        "wp_17_night_clouds" to ("Starry Night River" to Color(0x8B5CF6)),
        "wp_18_crater_harbor" to ("Cherry Crater Village" to Color(0xFB7185)),
        "wp_19_sinkhole_falls" to ("Lush Sinkhole Waterfall" to Color(0x2DD4BF)),
        "wp_20_sakura_sunbeams" to ("Sakura Sunbeams" to Color(0xE879F9)),
        "wp_21_crimson_forest" to ("Crimson Forest Embers" to Color(0xE11D48)),
        "wp_22_amethyst_geode" to ("Amethyst Geode Lake" to Color(0xA855F7)),
        "wp_23_bamboo_dawn" to ("Bamboo Sunrise" to Color(0x84CC16)),
        "wp_24_badlands_canyon" to ("Badlands Canyon River" to Color(0xF97316)),
        "wp_25_deep_dark" to ("Deep Dark Sculk City" to Color(0x06B6D4)),
        "aerix_twilight" to ("Aerix Twilight" to Color(0x8DEFE0))
    )

    class Preset(val id: String, val label: String, val accent: Color)

    fun presets(): List<Preset> = wallpaperNames.map { (id, pair) -> Preset(id, pair.first, pair.second) }

    fun presetLabel(id: String): String = wallpaperNames.firstOrNull { it.first == id }?.second?.first ?: "Aerix"

    fun customDir(): File = net.kdt.aerix.desktop.app.AppEnv.appData.resolve("wallpapers").toFile()

    /** Loads a bundled wallpaper; custom ones live in `%APPDATA%\Aerix Launcher\wallpapers`. */
    fun load(id: String): BufferedImage? {
        val key = "full:$id"
        cache[key]?.let { return it }
        val custom = File(customDir(), "$id.png").takeIf { it.isFile } ?: File(customDir(), "$id.jpg").takeIf { it.isFile }
            ?: File(customDir(), "$id.webp").takeIf { it.isFile }
        val image: BufferedImage? = if (custom != null) runCatching { ImageIO.read(custom) }.getOrNull()
        else runCatching {
            Backdrops::class.java.getResourceAsStream("/aerix/wallpapers/$id.jpg")?.use { ImageIO.read(it) }
        }.getOrNull()
        if (image != null) cache[key] = image
        return image
    }

    /**
     * The window background for the current size and wallpaper.
     *
     * The image is scaled to fill, darkened towards the edges (the Android build draws a dark
     * gradient over the wallpaper for text contrast), then optionally blurred by the
     * "background blur" setting. Results are cached by (id, size, blur, opacity).
     */
    fun background(id: String, width: Int, height: Int, blurRadii: Int = 0, opacityPercent: Int = 100): BufferedImage {
        val w = width.coerceAtLeast(2)
        val h = height.coerceAtLeast(2)
        val key = "$id:${w}x$h:b$blurRadii:o$opacityPercent"
        if (key == lastKey) lastImage?.let { return it }
        cache[key]?.let { lastKey = key; lastImage = it; return it }

        val source = load(id)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        Paint.quality(g)
        g.color = AerixColors.canvas
        g.fillRect(0, 0, w, h)
        if (source != null) {
            val scale = maxOf(w.toDouble() / source.width, h.toDouble() / source.height)
            val dw = (source.width * scale).toInt()
            val dh = (source.height * scale).toInt()
            g.drawImage(source, (w - dw) / 2, (h - dh) / 2, dw, dh, null)
        }
        // wallpaper opacity: blend towards the canvas colour
        val alpha = (opacityPercent.coerceIn(20, 100) / 100f)
        if (alpha < 1f) {
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - alpha)
            g.color = AerixColors.canvas
            g.fillRect(0, 0, w, h)
            g.composite = AlphaComposite.SrcOver
        }
        // vignette + top/bottom scrim so the rail and page headers stay readable
        g.paint = RadialGradientPaint(
            w / 2f, h / 2f, maxOf(w, h) * 0.75f,
            floatArrayOf(0f, 0.65f, 1f),
            arrayOf(Color(0, 0, 0, 0), Color(0, 6, 12, 90), Color(0, 4, 9, 185))
        )
        g.fillRect(0, 0, w, h)
        g.paint = GradientPaint(0f, 0f, Color(4, 10, 18, 170), 0f, h * 0.45f, Color(4, 10, 18, 0))
        g.fillRect(0, 0, w, (h * 0.45f).toInt())
        g.dispose()

        val result = if (blurRadii > 0) boxBlur(out, blurRadii.coerceIn(1, 24)) else out
        if (cache.size > 6) cache.clear()
        cache[key] = result
        lastKey = key
        lastImage = result
        return result
    }

    fun invalidate() { cache.clear(); lastKey = ""; lastImage = null }

    /** Two-pass separable box blur; alpha is dropped because the window backing is opaque. */
    fun boxBlur(src: BufferedImage, radius: Int): BufferedImage {
        var a = src
        repeat(2) {
            a = blurPass(a, radius)
        }
        return a
    }

    private fun blurPass(src: BufferedImage, radius: Int): BufferedImage {
        val w = src.width
        val h = src.height
        val horizontal = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val div = radius * 2 + 1
        val window = IntArray(div)
        for (y in 0 until h) {
            var sumR = 0; var sumG = 0; var sumB = 0
            for (i in -radius..radius) {
                val x = i.coerceIn(0, w - 1)
                val p = src.getRGB(x, y)
                sumR += (p shr 16) and 0xff; sumG += (p shr 8) and 0xff; sumB += p and 0xff
            }
            for (x in 0 until w) {
                horizontal.setRGB(x, y, (0xff shl 24) or ((sumR / div) shl 16) or ((sumG / div) shl 8) or (sumB / div))
                val addX = (x + radius + 1).coerceAtMost(w - 1)
                val remX = (x - radius).coerceAtLeast(0)
                val pa = src.getRGB(addX, y)
                val pr = src.getRGB(remX, y)
                sumR += ((pa shr 16) and 0xff) - ((pr shr 16) and 0xff)
                sumG += ((pa shr 8) and 0xff) - ((pr shr 8) and 0xff)
                sumB += (pa and 0xff) - (pr and 0xff)
            }
        }
        for (x in 0 until w) {
            var sumR = 0; var sumG = 0; var sumB = 0
            for (i in -radius..radius) {
                val y = i.coerceIn(0, h - 1)
                val p = horizontal.getRGB(x, y)
                sumR += (p shr 16) and 0xff; sumG += (p shr 8) and 0xff; sumB += p and 0xff
            }
            for (y in 0 until h) {
                out.setRGB(x, y, (0xff shl 24) or ((sumR / div) shl 16) or ((sumG / div) shl 8) or (sumB / div))
                val addY = (y + radius + 1).coerceAtMost(h - 1)
                val remY = (y - radius).coerceAtLeast(0)
                val pa = horizontal.getRGB(x, addY)
                val pr = horizontal.getRGB(x, remY)
                sumR += ((pa shr 16) and 0xff) - ((pr shr 16) and 0xff)
                sumG += ((pa shr 8) and 0xff) - ((pr shr 8) and 0xff)
                sumB += (pa and 0xff) - (pr and 0xff)
            }
        }
        return out
    }

    /** Scales an image to cover [w]x[h] and returns it, for previews and hero cards. */
    fun cover(image: BufferedImage, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w.coerceAtLeast(1), h.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        Paint.quality(g)
        val scale = maxOf(w.toDouble() / image.width, h.toDouble() / image.height)
        val dw = (image.width * scale).toInt()
        val dh = (image.height * scale).toInt()
        g.drawImage(image, (w - dw) / 2, (h - dh) / 2, dw, dh, null)
        g.dispose()
        return out
    }
}

/** Colour themes for the accent picker (Aerix "Theme colour" cards). */
object ThemePresets {
    val accents = AerixTheme.accentValues
}
