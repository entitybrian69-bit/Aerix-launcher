package net.kdt.aerix.desktop.ui.components

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.core.auth.accounts.Account
import net.kdt.aerix.desktop.core.net.Http
import net.kdt.aerix.desktop.ui.theme.Paint
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * Minecraft skin handling: downloading the player's skin, cutting the 8×8 face texture out of it,
 * and rendering the character — flat for the library grid, in 3D for the wardrobe.
 *
 * Android used `skinview3d` (a WebView with three.js). This is a small software renderer instead:
 * the skin's own body parts are drawn as textured quads in an orthographic-ish projection, then
 * composited back-to-front. Rotating the model is a yaw/pitch pair, which is all the wardrobe needs
 * and keeps the whole character viewer inside the JVM (no browser, no WebGL, no bundling).
 */
object SkinFace {
    private val cache = ConcurrentHashMap<String, BufferedImage>()
    private val downloading = ConcurrentHashMap.newKeySet<String>()

    /** The URL template the Android build uses per auth type (`AuthType.skinUrl`). */
    fun skinUrl(account: Account?): String? = when {
        account == null -> null
        account.skinUrl != null -> account.skinUrl
        account.isMicrosoft -> "https://textures.minecraft.net/texture/__uuid__"
        else -> null
    }

    /** A 8×8 face (with hat layer) for the given account, or a placeholder. */
    fun face(account: Account?, size: Int): BufferedImage? {
        if (account == null) return null
        val key = "face:${account.effectiveUuid()}:${account.username}:$size"
        cache[key]?.let { return it }
        val skin = skin(account)
        val face = if (skin != null) faceOf(skin, size) else placeholderFace(account.username, size)
        cache[key] = face
        return face
    }

    /** The account's 64×64 skin, downloaded once and cached on disk. */
    fun skin(account: Account): BufferedImage? {
        val cached = skinFile(account)
        if (cached.isFile) {
            runCatching { ImageIO.read(cached) }.getOrNull()?.let { return it }
        }
        val url = when {
            account.isMicrosoft -> "https://c.textures.minecraft.net/texture/${account.profileId.replace("-", "")}"
            account.skinUrl != null -> account.skinUrl!!
            else -> null
        }
        // `skinUrl` comes out of the on-disk account store, so it is treated as untrusted input:
        // a skin is only ever fetched over TLS, and never over a scheme that could be redirected by
        // something sitting between the launcher and the network.
        val safeUrl = url?.takeIf { it.startsWith("https://", ignoreCase = true) }
        if (url != null && safeUrl == null) {
            Log.w("skin", "ignoring a non-HTTPS skin URL for ${account.username}")
        }
        if (safeUrl != null && downloading.add(account.effectiveUuid())) {
            Scheduler.io("skin") {
                runCatching {
                    val bytes = Http.download(safeUrl)
                    val img = ImageIO.read(bytes.inputStream())
                    if (img != null) {
                        cached.parentFile?.mkdirs()
                        ImageIO.write(img, "png", cached)
                        cache.keys.filter { it.startsWith("face:${account.effectiveUuid()}") }.forEach(cache::remove)
                    }
                }.onFailure { Log.w("skin", "could not fetch skin for ${account.username}: ${it.message}") }
                downloading.remove(account.effectiveUuid())
            }
        }
        // An imported or locally customised skin wins immediately.
        val local = AppEnv.skinsDir.resolve("${account.effectiveUuid()}.png").toFile()
        if (local.isFile) return runCatching { ImageIO.read(local) }.getOrNull()
        return runCatching { ImageIO.read(cached) }.getOrNull()
    }

    fun skinFile(account: Account): File = AppEnv.skinsDir.resolve("${account.effectiveUuid()}.png").toFile()

    /** Installs a locally chosen skin PNG for an account (import from file / drag and drop). */
    fun importSkin(account: Account, png: File): Boolean = runCatching {
        val img = ImageIO.read(png) ?: return false
        if (img.width != 64 || (img.height != 64 && img.height != 32)) return false
        val target = skinFile(account)
        target.parentFile?.mkdirs()
        ImageIO.write(img, "png", target)
        cache.keys.filter { it.startsWith("face:${account.effectiveUuid()}") || it.startsWith("body:${account.effectiveUuid()}") }
            .forEach(cache::remove)
        true
    }.getOrDefault(false)

    /** Cuts the head (and overlay) out of a skin. Supports both 64×64 and legacy 64×32. */
    fun faceOf(skin: BufferedImage, size: Int): BufferedImage {
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        Paint.quality(g)
        val s = skin.width.toFloat() / 64f
        fun region(x: Int, y: Int, w: Int, h: Int, dx: Int, dy: Int, dw: Int, dh: Int) {
            g.drawImage(
                skin,
                dx, dy, dx + dw, dy + dh,
                (x * s).toInt(), (y * s).toInt(), ((x + w) * s).toInt(), ((y + h) * s).toInt(), null
            )
        }
        region(8, 8, 8, 8, 0, 0, size, size)
        if (skin.height >= 64) region(40, 8, 8, 8, 0, 0, size, size)
        g.dispose()
        return out
    }

    /** Fallback: a deterministic Steve-like face so every profile looks like a Minecraft head. */
    fun placeholderFace(username: String, size: Int): BufferedImage {
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        Paint.quality(g)
        val hue = (username.hashCode() and 0x7fffffff) % 360
        val skinTone = java.awt.Color.getHSBColor(0.07f, 0.32f, 0.86f)
        val hair = java.awt.Color.getHSBColor(hue / 360f, 0.45f, 0.35f)
        g.color = skinTone
        g.fillRect(0, 0, size, size)
        g.color = hair
        g.fillRect(0, 0, size, size / 3)
        val px = size / 8
        g.color = java.awt.Color(0x3B, 0x2A, 0x1F)
        g.fillRect(px, size / 2 - px / 2, px, px)          // left eye
        g.fillRect(size - px * 2, size / 2 - px / 2, px, px) // right eye
        g.color = java.awt.Color(0x8C, 0x5A, 0x4A)
        g.fillRect(px * 2, size - px * 2, size - px * 4, px / 2) // mouth
        g.dispose()
        return out
    }

    // ---- The 3D character --------------------------------------------------------------------

    /**
     * Renders the character from a skin at [width]×[height], yawed and pitched.
     *
     * Box parts are drawn as three visible quads each (front/side/top), which is enough to read as
     * a Minecraft player and costs a few hundred draw calls — cheap enough to repaint on drag.
     */
    fun renderCharacter(
        skin: BufferedImage?,
        width: Int,
        height: Int,
        yawDegrees: Float = -18f,
        pitchDegrees: Float = 0f,
        slimModel: Boolean = true,
        username: String = "Steve"
    ): BufferedImage {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        Paint.quality(g)
        val source = skin ?: defaultSkin()
        val atlas = Atlas(source)
        // Model space is in skin pixels: the player is 32 wide and 64 tall standing still.
        // the standing model spans 38 units vertically and ~14 horizontally
        val scale = minOf(width / 17f, height / 46f) * 0.96f
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        val cosY = Math.cos(yaw); val sinY = Math.sin(yaw)
        val cosP = Math.cos(pitch); val sinP = Math.sin(pitch)

        fun project(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
            val rx = x * cosY + z * sinY
            val rz = -x * sinY + z * cosY
            val ry = y * cosP - rz * sinP
            val rz2 = y * sinP + rz * cosP
            val depth = (180.0 + rz2).toFloat()
            val f = (170.0 / depth.coerceAtLeast(40f)).toFloat()
            // the model's own vertical centre is 13 units above its feet, so re-centre here
            return Triple((width / 2f + (rx * f * scale)).toFloat(),
                (height * 0.50f + ((ry + 13.0) * f * scale)).toFloat(), depth)
        }

        data class Quad(val points: List<Triple<Float, Float, Float>>, val image: BufferedImage, val shade: Float)

        class Box(
            val front: IntArray, val right: IntArray, val top: IntArray,
            val cx: Float, val cy: Float, val cz: Float,
            val sx: Float, val sy: Float, val sz: Float,
            val faceShade: Float = 1f
        )

        val quads = ArrayList<Quad>()

        fun addBox(box: Box) {
            val hx = box.sx / 2; val hy = box.sy / 2; val hz = box.sz / 2
            fun p(x: Float, y: Float, z: Float) = project(box.cx + x, box.cy + y, box.cz + z)
            val front = listOf(p(-hx, -hy, hz), p(hx, -hy, hz), p(hx, hy, hz), p(-hx, hy, hz))
            val right = listOf(p(hx, -hy, hz), p(hx, -hy, -hz), p(hx, hy, -hz), p(hx, hy, hz))
            val top = listOf(p(-hx, -hy, hz), p(hx, -hy, hz), p(hx, -hy, -hz), p(-hx, -hy, -hz))
            quads.add(Quad(front, atlas.crop(box.front), box.faceShade))
            quads.add(Quad(right, atlas.crop(box.right), 0.72f))
            if (pitchDegrees < 32f) quads.add(Quad(top, atlas.crop(box.top), 1.06f))
        }

        val armWidth = if (slimModel) 3f else 4f
        val armUvWidth = 4
        // head: front (8,8) side (16,8) top (8,24)
        addBox(Box(intArrayOf(8, 8, 8, 8), intArrayOf(16, 8, 8, 8), intArrayOf(8, 24, 8, 8),
            0f, -28f, 0f, 8f, 8f, 8f))
        // body: front (20,20) side (28,20) top (28,16)
        addBox(Box(intArrayOf(20, 20, 8, 12), intArrayOf(28, 20, 4, 12), intArrayOf(28, 16, 8, 4),
            0f, -18f, 0f, 8f, 12f, 4f))
        // right arm (player's right = screen left): front (44,20) side (40,20) top (44,16)
        addBox(Box(intArrayOf(44, 20, armUvWidth, 12), intArrayOf(40, 20, 4, 12), intArrayOf(44, 16, armUvWidth, 4),
            -(4f + armWidth / 2f), -18f, 0f, armWidth, 12f, 4f, 0.86f))
        // left arm: front (36,52) side (32,52) top (36,48)
        addBox(Box(intArrayOf(36, 52, armUvWidth, 12), intArrayOf(32, 52, 4, 12), intArrayOf(36, 48, armUvWidth, 4),
            (4f + armWidth / 2f), -18f, 0f, armWidth, 12f, 4f, 0.86f))
        // right leg: front (4,20) side (0,20) top (4,16)
        addBox(Box(intArrayOf(4, 20, 4, 12), intArrayOf(0, 20, 4, 12), intArrayOf(4, 16, 4, 4),
            -2f, -6f, 0f, 4f, 12f, 4f, 0.94f))
        // left leg: front (20,52) side (16,52) top (20,48)
        addBox(Box(intArrayOf(20, 52, 4, 12), intArrayOf(16, 52, 4, 12), intArrayOf(20, 48, 4, 4),
            2f, -6f, 0f, 4f, 12f, 4f, 0.94f))

        quads.sortByDescending { q -> q.points.map { it.third }.average() }
        val shape = java.awt.geom.Path2D.Float()
        for (q in quads) {
            shape.reset()
            q.points.forEachIndexed { i, pt ->
                if (i == 0) shape.moveTo(pt.first, pt.second) else shape.lineTo(pt.first, pt.second)
            }
            shape.closePath()
            g.clip = shape
            // Fill with the crop's mean colour before stretching it, otherwise the antialiased
            // quad edges keep a sliver of transparency and show up as bright seams.
            g.color = averageColor(q.image)
            g.fill(shape)
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            val minX = q.points.minOf { it.first }
            val minY = q.points.minOf { it.second }
            val maxX = q.points.maxOf { it.first }
            val maxY = q.points.maxOf { it.second }
            // NB: the 10-argument drawImage takes the opposite *corner*, not a size.
            g.drawImage(q.image, minX.toInt(), minY.toInt(),
                minX.toInt() + (maxX - minX).toInt().coerceAtLeast(1),
                minY.toInt() + (maxY - minY).toInt().coerceAtLeast(1),
                0, 0, q.image.width, q.image.height, null)
            if (q.shade < 0.99f) {
                g.color = java.awt.Color(0, 0, 0, ((1f - q.shade) * 105).toInt())
                g.fill(shape)
            }
            g.clip = null
        }

        // name plate
        g.font = net.kdt.aerix.desktop.ui.theme.Fonts.labelStrong(12f)
        val fm = g.fontMetrics
        val nameX = (width - fm.stringWidth(username)) / 2
        g.color = java.awt.Color(0, 8, 14, 120)
        g.fillRoundRect(nameX - 6, height - fm.height - 10, fm.stringWidth(username) + 12, fm.height + 4, 8, 8)
        g.color = java.awt.Color(0xF1, 0xF6, 0xFA)
        g.drawString(username, nameX, height - 8)
        g.dispose()
        return out
    }

    private val averages = java.util.concurrent.ConcurrentHashMap<BufferedImage, java.awt.Color>()

    private fun averageColor(img: BufferedImage): java.awt.Color = averages.getOrPut(img) {
        var r = 0L; var g = 0L; var b = 0L; var a = 0L; var n = 0L
        for (y in 0 until img.height step maxOf(1, img.height / 8)) {
            for (x in 0 until img.width step maxOf(1, img.width / 8)) {
                val c = java.awt.Color(img.getRGB(x, y), true)
                r += c.red; g += c.green; b += c.blue; a += c.alpha; n++
            }
        }
        if (n == 0L) java.awt.Color(0x8C, 0x8C, 0x8C) else java.awt.Color(
            (r / n).toInt(), (g / n).toInt(), (b / n).toInt(), (a / n).toInt()
        )
    }

    private class Atlas(private val skin: BufferedImage) {
        private val cache = HashMap<String, BufferedImage>()
        fun crop(uv: IntArray): BufferedImage = crop(uv[0], uv[1], uv[2], uv[3])
        fun crop(x: Int, y: Int, w: Int, h: Int): BufferedImage {
            val key = "$x,$y,$w,$h"
            return cache.getOrPut(key) {
                if (skin.width < 64) return@getOrPut defaultSkin()
                val sx = skin.width / 64
                val sy = skin.height / 64
                val bw = (w * sx).coerceAtLeast(1)
                val bh = (h * sy).coerceAtLeast(1)
                val bx = (x * sx).coerceAtMost(skin.width - 1)
                val by = (y * sy).coerceAtMost(skin.height - 1)
                val cw = minOf(bw, skin.width - bx)
                val ch = minOf(bh, skin.height - by)
                if (cw <= 0 || ch <= 0) return@getOrPut defaultSkin()
                skin.getSubimage(bx, by, cw, ch)
            }
        }
    }

    private var cachedDefault: BufferedImage? = null

    /** The bundled `steve.png` from the Android assets, so both launchers show the same default. */
    fun defaultSkin(): BufferedImage {
        cachedDefault?.let { return it }
        runCatching {
            val stream = SkinFace::class.java.getResourceAsStream("/aerix/brand/steve.png")
            if (stream != null) {
                val img = stream.use { ImageIO.read(it) }
                if (img != null && img.width >= 64) {
                    cachedDefault = img
                    return img
                }
            }
        }
        val img = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(0x00, 0x00, 0x00, 0)
        g.fillRect(0, 0, 64, 64)
        // head
        g.color = java.awt.Color(0xC8, 0x9E, 0x7C); g.fillRect(8, 8, 8, 8)
        g.color = java.awt.Color(0x3B, 0x26, 0x16); g.fillRect(8, 8, 8, 3)
        g.color = java.awt.Color(0x4E, 0x3A, 0x2C); g.fillRect(10, 12, 1, 1); g.fillRect(13, 12, 1, 1)
        g.color = java.awt.Color(0xA8, 0x6E, 0x58); g.fillRect(11, 14, 2, 1)
        // body
        g.color = java.awt.Color(0x1F, 0x9D, 0x8F); g.fillRect(20, 20, 8, 12)
        g.color = java.awt.Color(0x17, 0x7E, 0x73); g.fillRect(20, 20, 8, 2)
        // arms
        g.color = java.awt.Color(0xC8, 0x9E, 0x7C); g.fillRect(44, 20, 4, 12); g.fillRect(36, 52, 4, 12)
        g.color = java.awt.Color(0x1F, 0x9D, 0x8F); g.fillRect(44, 20, 4, 4); g.fillRect(36, 52, 4, 4)
        // legs
        g.color = java.awt.Color(0x3B, 0x4A, 0x8C); g.fillRect(4, 20, 4, 12); g.fillRect(20, 52, 4, 12)
        g.color = java.awt.Color(0x54, 0x5A, 0x5F); g.fillRect(4, 29, 4, 3); g.fillRect(20, 61, 4, 3)
        g.dispose()
        cachedDefault = img
        return img
    }
}
