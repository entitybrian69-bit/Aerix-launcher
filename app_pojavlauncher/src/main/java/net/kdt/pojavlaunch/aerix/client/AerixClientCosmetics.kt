package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.Color
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File

data class ClientCapeSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val topHex: Long,
    val bottomHex: Long,
    val emblemHex: Long,
    val requiredEmail: String? = null,
    val badgeText: String? = null
) {
    fun isUnlockedFor(email: String?): Boolean {
        val req = requiredEmail ?: return true
        return email?.trim()?.equals(req, ignoreCase = true) == true
    }
}

data class ClientHatSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val accentHex: Long
)

object AerixClientCosmetics {

    const val OWNER_EMAIL = "entitybrian69@gmail.com"
    const val CO_OWNER_EMAIL = "kushaladhikari982648@gmail.com"

    val capes: List<ClientCapeSpec> = listOf(
        ClientCapeSpec(
            id = "owner_crimson",
            title = "(OWNER) Sovereign Cape",
            subtitle = "Crimson Royal Mantle + Dragon-Gold Crest",
            topHex = 0xFFDC1E45L,
            bottomHex = 0xFF4A0718L,
            emblemHex = 0xFFFFD166L,
            requiredEmail = OWNER_EMAIL,
            badgeText = "(OWNER)"
        ),
        ClientCapeSpec(
            id = "coowner_celestial",
            title = "(CO-OWNER) Celestial Cape",
            subtitle = "Sky-Blue Starlight + Ice Prism Wings",
            topHex = 0xFF38CFFF,
            bottomHex = 0xFF0A3958L,
            emblemHex = 0xFFE6FCFFL,
            requiredEmail = CO_OWNER_EMAIL,
            badgeText = "(CO-OWNER)"
        ),
        ClientCapeSpec(
            id = "liquid_crystal",
            title = "Aerix Liquid Crystal",
            subtitle = "Signature Transparent Ice-Prism Shimmer",
            topHex = 0xFFC8FAFFL,
            bottomHex = 0xFF288B9EL,
            emblemHex = 0xFFFFFFFFL,
            badgeText = "SIGNATURE"
        ),
        ClientCapeSpec(
            id = "aurora_prism",
            title = "Aurora Borealis",
            subtitle = "Northern Lights Mint-to-Violet Flow",
            topHex = 0xFF73F5DC,
            bottomHex = 0xFF5344B8L,
            emblemHex = 0xFFE8FFFFL
        ),
        ClientCapeSpec(
            id = "nebula_supernova",
            title = "Nebula Supernova",
            subtitle = "Deep Cosmos + Starburst Elytra",
            topHex = 0xFF8A5CFFL,
            bottomHex = 0xFF1A103CL,
            emblemHex = 0xFF8DEFE0L
        ),
        ClientCapeSpec(
            id = "sakura_blossom",
            title = "Sakura Crystal",
            subtitle = "Soft Petal Drift + Pearl Trim",
            topHex = 0xFFF9B4DC,
            bottomHex = 0xFF6B2D5CL,
            emblemHex = 0xFFFFF5FAL
        ),
        ClientCapeSpec(
            id = "void_dragon",
            title = "Void Ender Wings",
            subtitle = "Obsidian Weave + Ender Violet Spine",
            topHex = 0xFF2D1E4AL,
            bottomHex = 0xFF090612L,
            emblemHex = 0xFFC77DFFL
        ),
        ClientCapeSpec(
            id = "emerald_matrix",
            title = "Emerald Cyber Grid",
            subtitle = "Digital Neon Pulse + Tech Elytra",
            topHex = 0xFF2CEAA3L,
            bottomHex = 0xFF073B2AL,
            emblemHex = 0xFFD8FFF0L
        ),
        ClientCapeSpec(
            id = "golden_champion",
            title = "Golden Champion",
            subtitle = "1.16.5 Mastery Gold + Ivory Trim",
            topHex = 0xFFFFD166L,
            bottomHex = 0xFF6E470BL,
            emblemHex = 0xFFFFFFFFL
        ),
        ClientCapeSpec(
            id = "custom_painted",
            title = "Custom Painted Cape",
            subtitle = "Your Pixel-Studio 10×16 Design",
            topHex = 0xFF8DEFE0L,
            bottomHex = 0xFF173A4AL,
            emblemHex = 0xFFFFFFFFL,
            badgeText = "STUDIO"
        ),
        ClientCapeSpec(
            id = "custom_imported",
            title = "Imported 64×32 PNG",
            subtitle = "Loaded from your device storage",
            topHex = 0xFF91E9FFL,
            bottomHex = 0xFF1D3557L,
            emblemHex = 0xFFFFFFFFL,
            badgeText = "CUSTOM"
        )
    )

    val hats: List<ClientHatSpec> = listOf(
        ClientHatSpec("none", "No 3D Headwear", "Keep your raw skin head layer", 0xFF7D909DL),
        ClientHatSpec("golden_crown", "Royal Golden Crown", "3D pixel crown with cyan gems", 0xFFFFD166L),
        ClientHatSpec("crystal_halo", "Floating Crystal Halo", "Luminous ice-cyan ring above head", 0xFFC8FAFFL),
        ClientHatSpec("cyber_visor", "Crystal Cyber Visor", "Wrap-around neon aqua visor", 0xFF5CE1FFL),
        ClientHatSpec("pro_headset", "Esports Crystal Headset", "3D earcups + headband on outer layer", 0xFF8DEFE0L),
        ClientHatSpec("pixel_shades", "Deal-With-It Shades", "Crisp dark glasses with white glint", 0xFFF1F6FAL),
        ClientHatSpec("crimson_bandana", "Sovereign Bandana", "Crimson ninja headband + tails", 0xFFFF4769L),
        ClientHatSpec("sakura_crown", "Sakura Blossom Wreath", "Pink & mint pixel flower crown", 0xFFF7A8D8L)
    )

    fun currentEmail(): String? = SocialBackend.email

    fun canEquipCape(spec: ClientCapeSpec): Boolean = spec.isUnlockedFor(currentEmail())

    fun effectiveCapeSpec(): ClientCapeSpec {
        val wanted = capes.firstOrNull { it.id == AerixClientStore.equippedCapeId }
        if (wanted != null && canEquipCape(wanted)) return wanted
        // Auto-unlock owner/co-owner if signed into staff email and default not changed yet
        val email = currentEmail()?.trim()?.lowercase()
        if (email == OWNER_EMAIL && AerixClientStore.equippedCapeId == "owner_crimson") {
            return capes.first { it.id == "owner_crimson" }
        }
        if (email == CO_OWNER_EMAIL && AerixClientStore.equippedCapeId == "coowner_celestial") {
            return capes.first { it.id == "coowner_celestial" }
        }
        return capes.first { it.id == "liquid_crystal" }
    }

    private fun importedCapeFile(context: Context): File =
        File(context.applicationContext.filesDir, "aerix_client_imported_cape.png")

    fun importCustomCapeUri(context: Context, uri: Uri): Boolean = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        val normalized = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(normalized)
        val paint = Paint().apply { isFilterBitmap = false }
        canvas.drawBitmap(
            Bitmap.createScaledBitmap(
                decoded,
                if (decoded.width >= 64) 64 else 64,
                if (decoded.height >= 32) 32 else 32,
                false
            ),
            0f,
            0f,
            paint
        )
        importedCapeFile(context).outputStream().use { out ->
            normalized.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        AerixClientStore.equippedCapeId = "custom_imported"
        AerixClientStore.persist()
        true
    }.getOrDefault(false)

    /**
     * Generates a genuine `64×32` Minecraft Cape + Elytra PNG byte array compatible with 1.16.5:
     * - Front/Back/Sides Cape UV: `(0..21, 0..16)` (Front face is `1..10, 1..16`, Back is `12..21, 1..16`)
     * - Elytra Wing UV: `(22..45, 0..21)` so wearing an Elytra in 1.16.5 renders matching custom wings!
     */
    fun resolveInGameCapeBytes(context: Context): ByteArray? = runCatching {
        AerixClientStore.init(context)
        val spec = effectiveCapeSpec()
        if (spec.id == "custom_imported") {
            val f = importedCapeFile(context)
            if (f.isFile && f.length() > 0) return f.readBytes()
        }
        val bmp = buildCapeAndElytraBitmap(spec)
        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }.getOrNull()

    fun buildCapeAndElytraBitmap(spec: ClientCapeSpec): Bitmap {
        val bmp = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        val customGrid = if (spec.id == "custom_painted") decodePaintedGrid() else null

        val topC = spec.topHex.toInt()
        val botC = spec.bottomHex.toInt()
        val embC = spec.emblemHex.toInt()

        // 1. Fill Cape UV box (0..21, 0..16) — visible front (1..10, 1..16) & back (12..21, 1..16)
        for (y in 0..16) {
            val t = y / 16f
            val rowColor = lerpArgb(topC, botC, t)
            for (x in 0..21) {
                bmp.setPixel(x, y, rowColor)
            }
        }

        // Border trim on visible back face (x: 1..10, y: 1..16 in Mojang cape UV — note: Mojang cape
        // outer back face is x=1..10, y=1..16; inner face facing player body is x=12..21, y=1..16!)
        for (y in 1..16) {
            for (x in 1..10) {
                if (customGrid != null) {
                    val customCol = customGrid.getOrNull((y - 1) * 10 + (x - 1))
                    if (customCol != null) {
                        bmp.setPixel(x, y, customCol)
                        bmp.setPixel(x + 11, y, customCol)
                        continue
                    }
                }
                val isEdge = x == 1 || x == 10 || y == 1 || y == 16
                if (isEdge) {
                    bmp.setPixel(x, y, lerpArgb(embC, topC, 0.35f))
                }
            }
        }

        // Center Star / A Crest on outer cape face (x: 1..10, y: 1..16) when not custom painted
        if (customGrid == null) {
            val crestPixels = listOf(
                5 to 4, 6 to 4,
                5 to 5, 6 to 5,
                4 to 6, 5 to 6, 6 to 6, 7 to 6,
                3 to 7, 4 to 7, 7 to 7, 8 to 7,
                4 to 8, 5 to 8, 6 to 8, 7 to 8,
                3 to 9, 8 to 9,
                3 to 10, 8 to 10,
                5 to 12, 6 to 12
            )
            for ((cx, cy) in crestPixels) {
                bmp.setPixel(cx, cy, embC)
            }
        }

        // 2. Matching Elytra Wing UV (x: 22..45, y: 0..21) so 1.16.5 Elytras match the cape!
        for (y in 0..21) {
            val t = y / 21f
            val baseWing = lerpArgb(topC, botC, t)
            for (x in 22..45) {
                val featherEdge = x == 22 || x == 45 || y == 0 || y == 21 || (x + y) % 7 == 0
                bmp.setPixel(x, y, if (featherEdge) lerpArgb(embC, baseWing, 0.55f) else baseWing)
            }
        }
        return bmp
    }

    /**
     * Non-destructively composites the chosen 3D Hat-Layer Accessory (`x: 32..63, y: 0..15`) onto
     * the player's `64×64` skin bytes (creating a clean default crystal-accented Steve/Alex skin
     * if the player has no custom skin file equipped yet, so 3D headwear still renders in-game!).
     */
    fun applyHatAccessoryToSkin(context: Context, existingSkinPng: ByteArray?, slim: Boolean): ByteArray? {
        AerixClientStore.init(context)
        val hatId = AerixClientStore.equippedHatId
        if (hatId == "none") return existingSkinPng

        val baseBitmap: Bitmap = if (existingSkinPng != null) {
            val decoded = BitmapFactory.decodeByteArray(existingSkinPng, 0, existingSkinPng.size)
                ?: return existingSkinPng
            val copy = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            Canvas(copy).drawBitmap(decoded, 0f, 0f, null)
            copy
        } else {
            buildDefaultBaseSkin64(slim)
        }

        paintHatLayer(baseBitmap, hatId)
        return ByteArrayOutputStream().use { out ->
            baseBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    /**
     * Minecraft `64×64` Hat Outer Layer UV coordinates:
     * - Top:    x 40..47, y 0..7
     * - Bottom: x 48..55, y 0..7
     * - Right:  x 32..39, y 8..15
     * - Front:  x 40..47, y 8..15
     * - Left:   x 48..55, y 8..15
     * - Back:   x 56..63, y 8..15
     */
    private fun paintHatLayer(bmp: Bitmap, hatId: String) {
        val gold = 0xFFFFD166.toInt()
        val goldDark = 0xFFD99B26.toInt()
        val cyan = 0xFF8DEFE0.toInt()
        val ice = 0xFFE6FCFF.toInt()
        val crimson = 0xFFFF3B5C.toInt()
        val dark = 0xFF141C24.toInt()
        val sakura = 0xFFF7A8D8.toInt()

        when (hatId) {
            "golden_crown" -> {
                for (x in 32..63) {
                    bmp.setPixel(x, 10, goldDark)
                    bmp.setPixel(x, 11, gold)
                    if (x % 2 == 0) bmp.setPixel(x, 9, gold)
                }
                bmp.setPixel(43, 10, cyan)
                bmp.setPixel(44, 10, ice)
                bmp.setPixel(35, 10, crimson)
                bmp.setPixel(51, 10, crimson)
            }
            "crystal_halo" -> {
                for (i in 0..7) {
                    bmp.setPixel(40 + i, 0, ice)
                    bmp.setPixel(40 + i, 7, ice)
                    bmp.setPixel(40, i, cyan)
                    bmp.setPixel(47, i, cyan)
                }
                for (x in 33..62) {
                    if (x % 2 == 0) bmp.setPixel(x, 8, ice)
                }
            }
            "cyber_visor" -> {
                for (x in 36..51) {
                    bmp.setPixel(x, 11, ice)
                    bmp.setPixel(x, 12, cyan)
                }
                for (x in 32..63) {
                    if (x < 36 || x > 51) bmp.setPixel(x, 11, dark)
                }
            }
            "pro_headset" -> {
                // Top headband across x 40..47, y 3..4
                for (x in 40..47) {
                    bmp.setPixel(x, 3, dark)
                    bmp.setPixel(x, 4, cyan)
                }
                // Right earcup (x 34..37, y 10..14) & Left earcup (x 50..53, y 10..14)
                for (y in 10..14) {
                    for (dx in 0..3) {
                        val rim = dx == 0 || dx == 3 || y == 10 || y == 14
                        bmp.setPixel(34 + dx, y, if (rim) cyan else dark)
                        bmp.setPixel(50 + dx, y, if (rim) cyan else dark)
                    }
                }
            }
            "pixel_shades" -> {
                for (x in 32..55) bmp.setPixel(x, 11, dark)
                for (x in 41..43) {
                    bmp.setPixel(x, 12, dark)
                    bmp.setPixel(x + 4, 12, dark)
                }
                bmp.setPixel(41, 11, ice)
                bmp.setPixel(45, 11, ice)
            }
            "crimson_bandana" -> {
                for (x in 32..63) {
                    bmp.setPixel(x, 10, crimson)
                    bmp.setPixel(x, 11, 0xFFB81D3A.toInt())
                }
                bmp.setPixel(59, 12, crimson)
                bmp.setPixel(60, 13, crimson)
                bmp.setPixel(58, 13, crimson)
            }
            "sakura_crown" -> {
                for (x in 32..63) {
                    bmp.setPixel(x, 10, if (x % 3 == 0) cyan else sakura)
                    if (x % 2 == 1) bmp.setPixel(x, 9, ice)
                }
            }
        }
    }

    private fun buildDefaultBaseSkin64(slim: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val skinTone = 0xFFD9A886.toInt()
        val hair = 0xFF1F2D3A.toInt()
        val jacket = 0xFF152C3D.toInt()
        val trim = 0xFF8DEFE0.toInt()
        val pants = 0xFF101D29.toInt()

        fun rect(x1: Int, y1: Int, w: Int, h: Int, c: Int) {
            for (y in y1 until (y1 + h)) for (x in x1 until (x1 + w)) bmp.setPixel(x, y, c)
        }
        // Head (0..31, 0..15)
        rect(8, 0, 16, 8, hair)
        rect(0, 8, 32, 8, skinTone)
        rect(8, 8, 8, 2, hair)
        bmp.setPixel(10, 12, 0xFFFFFFFF.toInt())
        bmp.setPixel(11, 12, trim)
        bmp.setPixel(14, 12, trim)
        bmp.setPixel(15, 12, 0xFFFFFFFF.toInt())
        // Torso (16..39, 16..31)
        rect(16, 20, 24, 12, jacket)
        rect(20, 16, 16, 4, jacket)
        rect(23, 22, 2, 8, trim)
        // Right/Left legs & arms
        rect(0, 20, 16, 12, pants)
        rect(16, 52, 16, 12, pants)
        val armW = if (slim) 14 else 16
        rect(40, 20, armW, 12, jacket)
        rect(32, 52, armW, 12, jacket)
        return bmp
    }

    fun defaultPaintedGrid(): List<Int> {
        val out = ArrayList<Int>(160)
        for (y in 0 until 16) {
            val base = lerpArgb(0xFFC8FAFF.toInt(), 0xFF1D6F82.toInt(), y / 15f)
            for (x in 0 until 10) {
                val edge = x == 0 || x == 9 || y == 0 || y == 15
                out.add(if (edge) 0xFFFFFFFF.toInt() else base)
            }
        }
        return out
    }

    fun decodePaintedGrid(): List<Int> {
        val raw = AerixClientStore.customCapePixelsJson
        if (raw.isBlank()) return defaultPaintedGrid()
        return runCatching {
            val arr = JSONArray(raw)
            if (arr.length() < 160) return defaultPaintedGrid()
            List(160) { idx -> arr.getInt(idx) }
        }.getOrDefault(defaultPaintedGrid())
    }

    fun savePaintedGrid(pixels: List<Int>) {
        val arr = JSONArray()
        pixels.take(160).forEach { arr.put(it) }
        AerixClientStore.customCapePixelsJson = arr.toString()
        AerixClientStore.equippedCapeId = "custom_painted"
        AerixClientStore.persist()
    }

    private fun lerpArgb(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val aa = AndroidColor.alpha(a) + ((AndroidColor.alpha(b) - AndroidColor.alpha(a)) * f).toInt()
        val rr = AndroidColor.red(a) + ((AndroidColor.red(b) - AndroidColor.red(a)) * f).toInt()
        val gg = AndroidColor.green(a) + ((AndroidColor.green(b) - AndroidColor.green(a)) * f).toInt()
        val bb = AndroidColor.blue(a) + ((AndroidColor.blue(b) - AndroidColor.blue(a)) * f).toInt()
        return AndroidColor.argb(aa, rr, gg, bb)
    }

    fun colorForHex(hex: Long): Color = Color(hex)
}
