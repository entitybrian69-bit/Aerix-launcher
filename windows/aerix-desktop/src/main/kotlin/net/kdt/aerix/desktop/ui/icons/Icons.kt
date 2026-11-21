package net.kdt.aerix.desktop.ui.icons

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.core.image.SvgPath
import net.kdt.aerix.desktop.ui.theme.Paint
import net.kdt.aerix.desktop.ui.theme.UiScale
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The launcher icon set.
 *
 * Icons are loaded from the very same Android vector drawables that ship in
 * `app_pojavlauncher/src/main/res/drawable/` (copied to `resources/aerix/icons/vector/`). Each
 * drawable is parsed once into a set of paths in viewport coordinates and then painted at any
 * size in any tint, which keeps the desktop UI's icons identical to the Android launcher's and
 * avoids shipping a hand-drawn replacement set.
 *
 * A handful of the drawables are multi-colour (the crystal client mark, the Modrinth/CurseForge
 * glyphs); those keep their own fill colours when [tint] is null.
 */
object Icons {
    class VectorIcon(val name: String, val viewportW: Float, val viewportH: Float, val paths: List<Glyph>) {
        val hasColor: Boolean get() = paths.any { it.fill != null && it.fill.alpha == 255 && !isNearBlack(it.fill) }
    }

    class Glyph(
        val path: Path2D,
        val fill: Color?,
        val evenOdd: Boolean,
        val stroke: Color?,
        val strokeWidth: Float
    )

    private val cache = ConcurrentHashMap<String, VectorIcon>()
    private val imageCache = ConcurrentHashMap<String, BufferedImage>()

    /** Aliases used by the UI for names that read better at the call site. */
    private val aliases = mapOf(
        "home" to "home_filled",
        "create" to "add",
        "library" to "videogame_asset_outlined",
        "discover" to "public",
        "settings" to "settings_filled",
        "accounts" to "person_outlined",
        "account" to "person_outlined",
        "clients" to "client_crystal",
        "modpacks" to "package_2_outlined",
        "releases" to "rocket_launch_filled",
        "servers" to "sports_esports_outlined",
        "logs" to "terminal_outlined",
        "files" to "folder_outlined",
        "search" to "search",
        "play" to "play_arrow_filled",
        "pause" to "pause",
        "delete" to "delete_outlined",
        "edit" to "edit_outlined",
        "copy" to "content_copy_filled",
        "close" to "close",
        "check" to "check",
        "favorite" to "favorite_filled",
        "favorite_border" to "favorite_outlined",
        "download" to "download",
        "upload" to "upload",
        "refresh" to "refresh",
        "settings_gear" to "settings_filled",
        "info" to "info_outlined",
        "warning" to "warning_outlined",
        "java" to "java",
        "folder" to "folder_filled",
        "person" to "people",
        "chat" to "chat",
        "bell" to "bell",
        "github" to "github",
        "link" to "link",
        "more" to "more_vert",
        "back" to "arrow_back",
        "chevron" to "chevron_right",
        "image" to "image_outlined",
        "wallpaper" to "wallpaper",
        "keyboard" to "keyboard_outlined",
        "mouse" to "mouse_filled",
        "archive" to "archive_filled",
        "zip" to "folder_zip_outlined",
        "import" to "import_modpack",
        "share" to "share_filled",
        "schedule" to "schedule_outlined",
        "sort" to "sort",
        "list" to "list_down",
        "visibility" to "visibility_outlined",
        "eye_off" to "visibility_off_outlined",
        "send" to "send",
        "update" to "update",
        "restore" to "restore_from_trash_filled",
        "unarchive" to "unarchive_outlined",
        "select_all" to "select_all",
        "deselect" to "deselect",
        "cursor" to "touch_app_outlined",
        "gamepad" to "videogame_asset_outlined",
        "monitor" to "video_settings",
        "palette" to "format_paint_outlined",
        "style" to "style_outlined",
        "text" to "text_snippet_filled",
        "lightbulb" to "lightbulb",
        "record" to "record",
        "build" to "build_outlined",
        "deselect_icon" to "deselect",
        "drag" to "select_all"
    )

    /** Missing icons are cached as an empty glyph, because ConcurrentHashMap refuses null values. */
    private val missing = VectorIcon("missing", 24f, 24f, emptyList())

    fun icon(name: String): VectorIcon? {
        val resolved = aliases[name] ?: name
        cache[resolved]?.let { return it.takeIf { icon -> icon !== missing } }
        val loaded = load(resolved) ?: missing
        cache[resolved] = loaded
        return if (loaded === missing) null else loaded
    }

    private fun load(name: String): VectorIcon? {
        val resource = "/aerix/icons/vector/aerix_ic_$name.xml"
        val stream = Icons::class.java.getResourceAsStream(resource)
        if (stream == null) {
            Log.w("icons", "missing vector icon $name")
            return null
        }
        return runCatching { stream.use { parse(it) } }
            .onFailure { Log.w("icons", "could not parse $name: ${it.message}") }
            .getOrNull()
    }

    private fun parse(input: java.io.InputStream): VectorIcon {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.isNamespaceAware = false
        val doc = factory.newDocumentBuilder().parse(input)
        val vector = doc.documentElement
        val vw = vector.getAttribute("android:viewportWidth").toFloatOrNull()
            ?: vector.getAttribute("viewportWidth").toFloatOrNull() ?: 24f
        val vh = vector.getAttribute("android:viewportHeight").toFloatOrNull()
            ?: vector.getAttribute("viewportHeight").toFloatOrNull() ?: 24f
        val glyphs = ArrayList<Glyph>()
        val nodes = doc.getElementsByTagName("path")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? org.w3c.dom.Element ?: continue
            val data = attr(el, "pathData") ?: continue
            val fillRaw = attr(el, "fillColor")
            val strokeRaw = attr(el, "strokeColor")
            val strokeWidth = attr(el, "strokeWidth")?.toFloatOrNull() ?: 0f
            val evenOdd = attr(el, "fillType")?.equals("evenOdd", true) == true
            val path = runCatching { SvgPath.parse(data, vw, vh, evenOdd = evenOdd) }
                .onFailure { Log.w("icons", "bad pathData: ${it.message}") }
                .getOrNull() ?: continue
            glyphs.add(Glyph(path, parseColor(fillRaw), evenOdd, parseColor(strokeRaw), strokeWidth))
        }
        return VectorIcon("", vw, vh, glyphs)
    }

    private fun attr(el: org.w3c.dom.Element, name: String): String? {
        val v = el.getAttribute("android:$name").ifEmpty { el.getAttribute(name) }
        return v.ifEmpty { null }
    }

    private fun parseColor(raw: String?): Color? {
        if (raw == null) return null
        return when {
            raw.startsWith("#") -> runCatching {
                val hex = raw.substring(1)
                when (hex.length) {
                    6 -> Color(Integer.parseInt(hex, 16))
                    8 -> Color(
                        Integer.parseInt(hex.substring(2, 4), 16),
                        Integer.parseInt(hex.substring(4, 6), 16),
                        Integer.parseInt(hex.substring(6, 8), 16),
                        Integer.parseInt(hex.substring(0, 2), 16)
                    )
                    3 -> Color(
                        Integer.parseInt("${hex[0]}${hex[0]}", 16),
                        Integer.parseInt("${hex[1]}${hex[1]}", 16),
                        Integer.parseInt("${hex[2]}${hex[2]}", 16)
                    )
                    else -> null
                }
            }.getOrNull()
            raw.equals("@android:color/white", true) -> Color.WHITE
            raw.equals("@android:color/black", true) -> Color.BLACK
            else -> null
        }
    }

    private fun isNearBlack(c: Color): Boolean = c.red < 0x40 && c.green < 0x40 && c.blue < 0x40

    /** Paints [icon] inside the box, centred, with the given [tint] (null keeps original colours). */
    fun draw(g: Graphics2D, icon: VectorIcon?, x: Int, y: Int, size: Int, tint: Color?) {
        if (icon == null) return
        val s = size.toFloat()
        val scale = minOf(s / icon.viewportW, s / icon.viewportH)
        val offX = x + (s - icon.viewportW * scale) / 2f
        val offY = y + (s - icon.viewportH * scale) / 2f
        val old = g.transform
        // translate first, then scale about the icon's own origin: getScaleInstance alone would
        // throw away the offset and paint every icon at the component's top-left corner.
        val placed = AffineTransform.getTranslateInstance(offX.toDouble(), offY.toDouble())
        placed.scale(scale.toDouble(), scale.toDouble())
        g.transform(placed)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        for (glyph in icon.paths) {
            val fill = when {
                tint != null -> tint
                glyph.fill == null -> null
                else -> glyph.fill
            }
            if (fill != null) {
                g.color = fill
                g.fill(glyph.path)
            }
            if (glyph.stroke != null && glyph.strokeWidth > 0f) {
                g.color = tint ?: glyph.stroke
                g.stroke = BasicStroke(glyph.strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.draw(glyph.path)
            }
        }
        g.transform = old
    }

    fun draw(g: Graphics2D, name: String, x: Int, y: Int, size: Int, tint: Color?) =
        draw(g, icon(name), x, y, size, tint)

    /** A standalone ARGB image of an icon, for windows, tray images and table cells. */
    fun image(name: String, size: Int, tint: Color? = null, scale: Float = 1f): BufferedImage {
        val key = "$name:$size:${tint?.rgb ?: -1}:$scale"
        return imageCache.getOrPut(key) {
            val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            Paint.quality(g)
            if (scale != 1f) {
                val s = Math.round(size * scale)
                val off = (size - s) / 2
                draw(g, name, off, off, s, tint)
            } else {
                draw(g, name, 0, 0, size, tint)
            }
            g.dispose()
            img
        }
    }

    /** Tints the black play glyph used on the primary Play button. */
    fun playBadge(size: Int, background: Color, foreground: Color, radius: Int = size / 2): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        Paint.quality(g)
        g.color = background
        g.fillRoundRect(0, 0, size, size, radius, radius)
        draw(g, "play", Math.round(size * 0.34f).toInt(), Math.round(size * 0.33f).toInt(),
            Math.round(size * 0.34f).toInt(), foreground)
        g.dispose()
        return img
    }

    fun roundImage(img: BufferedImage, size: Int): BufferedImage {
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        Paint.quality(g)
        val shape = java.awt.geom.Ellipse2D.Float(0f, 0f, size.toFloat(), size.toFloat())
        g.clip = shape
        val scale = maxOf(size.toDouble() / img.width, size.toDouble() / img.height)
        g.drawImage(img, ((size - img.width * scale) / 2).toInt(), ((size - img.height * scale) / 2).toInt(),
            (img.width * scale).toInt(), (img.height * scale).toInt(), null)
        g.composite = AlphaComposite.SrcOver
        g.dispose()
        return out
    }
}
