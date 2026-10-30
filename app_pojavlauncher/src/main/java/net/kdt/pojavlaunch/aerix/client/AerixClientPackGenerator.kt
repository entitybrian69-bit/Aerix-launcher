package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import net.kdt.pojavlaunch.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Procedurally generates `Aerix-Client-Pack.zip` (`pack_format: 6` for Minecraft 1.16.5) inside
 * the Aerix Client 1.16.5 instance's `resourcepacks/` folder and syncs `options.txt`.
 */
object AerixClientPackGenerator {

    const val PACK_FILE_NAME = "Aerix-Client-Pack.zip"

    fun packFormatForVersion(mcVersion: String): Int = when {
        mcVersion.startsWith("1.21.4") -> 46
        mcVersion.startsWith("1.21") -> 34
        mcVersion.startsWith("1.20.4") || mcVersion.startsWith("1.20.3") -> 22
        mcVersion.startsWith("1.20.2") -> 18
        mcVersion.startsWith("1.20") -> 15
        mcVersion.startsWith("1.19.4") -> 13
        mcVersion.startsWith("1.19") -> 9
        mcVersion.startsWith("1.18") -> 8
        mcVersion.startsWith("1.17") -> 7
        else -> 6
    }

    fun buildAndApplyToInstance(
        context: Context,
        instanceGameDir: File,
        mcVersion: String = "1.16.5"
    ): File {
        AerixClientStore.init(context)
        val rpDir = File(instanceGameDir, "resourcepacks").apply { mkdirs() }
        val zipFile = File(rpDir, PACK_FILE_NAME)
        val packFmt = packFormatForVersion(mcVersion)

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val mcmeta = """
                {
                  "pack": {
                    "pack_format": $packFmt,
                    "description": "\u00a7c\u2726 Aerix Client $mcVersion \u00a78\u00b7 Crimson Edition"
                  }
                }
            """.trimIndent()
            putText(zos, "pack.mcmeta", mcmeta)
            putBitmap(zos, "pack.png", buildPackIcon(context))

            // 2. Custom Minecraft 1.16.5 Title Dashboard, Liquid-Crystal Buttons/Hotbar & Skybox
            if (AerixClientStore.packCustomMainMenu) {
                putBitmap(zos, "assets/minecraft/textures/gui/widgets.png", buildLiquidCrystalWidgets())
                val panoFaces = buildSkyboxPanoramaFaces(context, AerixClientStore.packSkyboxTheme)
                panoFaces.forEachIndexed { idx, face ->
                    putBitmap(zos, "assets/minecraft/textures/gui/title/background/panorama_$idx.png", face)
                }
                putText(
                    zos,
                    "assets/minecraft/texts/splashes.txt",
                    listOf(
                        "✦ Aerix Client 1.16.5!",
                        "Liquid Crystal Clear!",
                        "Built for Competitive PvP & Speedruns!",
                        "Real 3D Capes & Elytras!",
                        "Zero Red Flashbang!",
                        "Powered by Aerix Launcher!"
                    ).joinToString("\n")
                )
            }

            // 3. Low Fire (-65% height animated 16x32 fire_0 & fire_1)
            if (AerixClientStore.packLowFire) {
                val fireMeta = """{"animation":{"frametime":3}}"""
                putBitmap(zos, "assets/minecraft/textures/block/fire_0.png", buildLowFireStrip(seed = 0))
                putText(zos, "assets/minecraft/textures/block/fire_0.png.mcmeta", fireMeta)
                putBitmap(zos, "assets/minecraft/textures/block/fire_1.png", buildLowFireStrip(seed = 1))
                putText(zos, "assets/minecraft/textures/block/fire_1.png.mcmeta", fireMeta)
            }

            // 4. Short PvP Swords (1.16.5 Wooden -> Netherite)
            if (AerixClientStore.packShortSwords) {
                val swordSpecs = listOf(
                    "wooden_sword" to (0xFF9C7444.toInt() to 0xFF6B4C2B.toInt()),
                    "stone_sword" to (0xFF9AA1A6.toInt() to 0xFF5D6469.toInt()),
                    "iron_sword" to (0xFFE4ECF2.toInt() to 0xFF8FA0AE.toInt()),
                    "golden_sword" to (0xFFFFDF6B.toInt() to 0xFFC78F1E.toInt()),
                    "diamond_sword" to (0xFF7CF7EE.toInt() to 0xFF1DA8B8.toInt()),
                    "netherite_sword" to (0xFF5A525C.toInt() to 0xFF8DEFE0.toInt())
                )
                for ((name, colors) in swordSpecs) {
                    putBitmap(
                        zos,
                        "assets/minecraft/textures/item/$name.png",
                        buildShortSword16(colors.first, colors.second)
                    )
                }
            }

            // 5. Lowered Shield (-50% first-person offset so shield never blocks crosshair)
            if (AerixClientStore.packLoweredShield) {
                putText(zos, "assets/minecraft/models/item/shield.json", buildShieldModelJson(blocking = false))
                putText(zos, "assets/minecraft/models/item/shield_blocking.json", buildShieldModelJson(blocking = true))
            }

            // 6. Compact Totem of Undying (prevents blinding screen-fill on pop)
            if (AerixClientStore.packCompactTotem) {
                putText(zos, "assets/minecraft/models/item/totem_of_undying.json", buildCompactTotemJson())
            }

            // 7. Glowing Outlined Ores + High-Vis 1.16.5 Ancient Debris
            if (AerixClientStore.packOutlinedOres) {
                val ores = listOf(
                    "diamond_ore" to (0xFF7D8287.toInt() to 0xFF5CE1FF.toInt()),
                    "emerald_ore" to (0xFF7D8287.toInt() to 0xFF45F59B.toInt()),
                    "gold_ore" to (0xFF7D8287.toInt() to 0xFFFFD166.toInt()),
                    "iron_ore" to (0xFF7D8287.toInt() to 0xFFF2D0B6.toInt()),
                    "redstone_ore" to (0xFF7D8287.toInt() to 0xFFFF4769.toInt()),
                    "lapis_ore" to (0xFF7D8287.toInt() to 0xFF5B8CFF.toInt()),
                    "coal_ore" to (0xFF7D8287.toInt() to 0xFFC8FAFF.toInt()),
                    "nether_gold_ore" to (0xFF6B3030.toInt() to 0xFFFFD166.toInt()),
                    "nether_quartz_ore" to (0xFF6B3030.toInt() to 0xFFFFFFFF.toInt()),
                    "ancient_debris_side" to (0xFF4D3933.toInt() to 0xFF8DEFE0.toInt()),
                    "ancient_debris_top" to (0xFF59423B.toInt() to 0xFFFF9F5A.toInt())
                )
                for ((name, pair) in ores) {
                    putBitmap(
                        zos,
                        "assets/minecraft/textures/block/$name.png",
                        buildOutlinedOre16(pair.first, pair.second)
                    )
                }
            }

            // 8. Crystal Clear Underwater Overlay
            if (AerixClientStore.packClearWater) {
                val uw = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                uw.eraseColor(Color.argb(18, 141, 239, 224))
                putBitmap(zos, "assets/minecraft/textures/misc/underwater.png", uw)
            }

            // 9. No Pumpkin Blur (100% clear Enderman vision in 1.16.5 Speedruns!)
            if (AerixClientStore.packNoPumpkinBlur) {
                val pb = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                pb.eraseColor(Color.TRANSPARENT)
                putBitmap(zos, "assets/minecraft/textures/misc/pumpkinblur.png", pb)
            }

            // 10. Borderless Clean Crystal Glass
            if (AerixClientStore.packBorderlessGlass) {
                putBitmap(zos, "assets/minecraft/textures/block/glass.png", buildCleanGlass16())
            }

            // 11. RGB Bow Charge Stages (Stage 0 = Red, Stage 1 = Gold, Stage 2 = Bright Mint)
            if (AerixClientStore.packRgbBowStages) {
                putBitmap(zos, "assets/minecraft/textures/item/bow_pulling_0.png", buildBowStage16(0xFFFF4769.toInt(), 1))
                putBitmap(zos, "assets/minecraft/textures/item/bow_pulling_1.png", buildBowStage16(0xFFFFD166.toInt(), 2))
                putBitmap(zos, "assets/minecraft/textures/item/bow_pulling_2.png", buildBowStage16(0xFF73F5DC.toInt(), 3))
            }

            // 12. Color-Shift Block Mining Progress Bar (destroy_stage_0..9)
            if (AerixClientStore.packMiningProgressBar) {
                for (stage in 0..9) {
                    putBitmap(
                        zos,
                        "assets/minecraft/textures/block/destroy_stage_$stage.png",
                        buildDestroyStage16(stage)
                    )
                }
            }

            // 13. Custom PvP Hit-Sound Event Remapping
            val hit = AerixClientStore.packHitSound
            if (hit != HitSoundPreset.VANILLA && hit.soundEvent.isNotEmpty()) {
                val soundsJson = """
                    {
                      "entity.arrow.hit_player": {
                        "replace": true,
                        "sounds": [
                          {
                            "name": "${hit.soundEvent}",
                            "type": "event",
                            "pitch": ${hit.pitch},
                            "volume": 1.0
                          }
                        ]
                      }
                    }
                """.trimIndent()
                putText(zos, "assets/minecraft/sounds.json", soundsJson)
            }
        }

        applyClientOptionsTxt(instanceGameDir)
        return zipFile
    }

    /**
     * Updates `<instanceGameDir>/options.txt` non-destructively:
     * - Enables `Aerix-Client-Pack.zip` when `installClientResourcePack` is true
     * - Sets `monochromeLogo:true` when `packCustomMojangSplash` is true (replaces bright red Mojang splash with sleek black!)
     * - Sets `gamma:100.0` when `fullbrightEnabled` is true (or `1.0` when toggled off)
     * - Disables `autoJump` by default on fresh Client installs
     */
    fun applyClientOptionsTxt(instanceGameDir: File) {
        instanceGameDir.mkdirs()
        val optFile = File(instanceGameDir, "options.txt")
        val existingLines = if (optFile.isFile) optFile.readLines().toMutableList() else mutableListOf()
        val map = LinkedHashMap<String, String>()
        for (line in existingLines) {
            val idx = line.indexOf(':')
            if (idx > 0) {
                map[line.substring(0, idx)] = line.substring(idx + 1)
            }
        }

        if (AerixClientStore.installClientResourcePack) {
            val currentPacks = map["resourcePacks"].orEmpty()
            if (!currentPacks.contains(PACK_FILE_NAME)) {
                map["resourcePacks"] = "[\"vanilla\",\"file/$PACK_FILE_NAME\"]"
            }
        }
        map["monochromeLogo"] = if (AerixClientStore.packCustomMojangSplash) "true" else "false"
        map["gamma"] = if (AerixClientStore.fullbrightEnabled) "100.0" else "1.0"
        if (!map.containsKey("autoJump")) map["autoJump"] = "false"
        if (!map.containsKey("pauseOnLostFocus")) map["pauseOnLostFocus"] = "false"

        val outText = map.entries.joinToString("\n") { "${it.key}:${it.value}" } + "\n"
        optFile.writeText(outText)
    }

    /* ── Procedural Texture Builders ──────────────────────────────────────── */

    private fun buildPackIcon(context: Context): Bitmap {
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            0f, 0f, 128f, 128f,
            intArrayOf(0xFF28151B.toInt(), 0xFF141519.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        c.drawRoundRect(4f, 4f, 124f, 124f, 28f, 28f, p)
        runCatching {
            val raw = BitmapFactory.decodeResource(context.resources, R.drawable.aerix_logo)
            if (raw != null) {
                val scaled = Bitmap.createScaledBitmap(raw, 92, 92, true)
                c.drawBitmap(scaled, 18f, 18f, null)
            }
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0xFFDC1E3A.toInt()
        }
        c.drawRoundRect(5f, 5f, 123f, 123f, 26f, 26f, border)
        return bmp
    }

    /**
     * Exact 256×256 `widgets.png` in locked Crimson Red & Dark Gray theme.
     */
    private fun buildLiquidCrystalWidgets(): Bitmap {
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fillPaint = Paint()
        val borderPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f }

        // 1. 9-Slot Hotbar (0, 0, w=182, h=22)
        fillPaint.color = Color.argb(175, 22, 24, 30)
        c.drawRect(0f, 0f, 182f, 22f, fillPaint)
        borderPaint.color = Color.argb(210, 220, 30, 58)
        c.drawRect(0.5f, 0.5f, 181.5f, 21.5f, borderPaint)
        borderPaint.color = Color.argb(95, 90, 96, 112)
        for (i in 1..8) {
            val sx = 1f + i * 20f
            c.drawLine(sx, 2f, sx, 20f, borderPaint)
        }

        // 2. Selected Hotbar Slot Selector (0, 22, w=24, h=24)
        fillPaint.color = Color.argb(75, 220, 30, 58)
        c.drawRect(1f, 23f, 23f, 45f, fillPaint)
        borderPaint.color = Color.argb(255, 255, 46, 76)
        c.drawRect(0.5f, 22.5f, 23.5f, 45.5f, borderPaint)
        borderPaint.color = Color.argb(220, 220, 30, 58)
        c.drawRect(1.5f, 23.5f, 22.5f, 44.5f, borderPaint)

        // 3. Offhand Left/Right Slots (24, 22, 29x24) & (53, 22, 29x24)
        fillPaint.color = Color.argb(175, 22, 24, 30)
        c.drawRect(24f, 23f, 46f, 45f, fillPaint)
        c.drawRect(60f, 23f, 82f, 45f, fillPaint)
        borderPaint.color = Color.argb(195, 220, 30, 58)
        c.drawRect(24.5f, 23.5f, 45.5f, 44.5f, borderPaint)
        c.drawRect(60.5f, 23.5f, 81.5f, 44.5f, borderPaint)

        // 4. Disabled Button (0, 46, w=200, h=20)
        drawCrystalButton(c, 0f, 46f, 200f, 20f, Color.argb(145, 20, 22, 27), Color.argb(110, 72, 76, 90))
        // 5. Normal Button (0, 66, w=200, h=20) — Dark Gray + Crimson Trim
        drawCrystalButton(c, 0f, 66f, 200f, 20f, Color.argb(205, 28, 30, 38), Color.argb(220, 220, 30, 58))
        // 6. Hovered / Focused Button (0, 86, w=200, h=20) — Glowing Crimson Red
        drawCrystalButton(c, 0f, 86f, 200f, 20f, Color.argb(225, 68, 18, 28), Color.argb(255, 255, 58, 86))

        // 7. Language & Accessibility Buttons
        drawCrystalButton(c, 0f, 106f, 20f, 20f, Color.argb(205, 28, 30, 38), Color.argb(220, 220, 30, 58))
        drawCrystalButton(c, 0f, 126f, 20f, 20f, Color.argb(225, 68, 18, 28), Color.argb(255, 255, 58, 86))
        borderPaint.color = Color.WHITE
        c.drawCircle(10f, 116f, 5f, borderPaint)
        c.drawCircle(10f, 136f, 5f, borderPaint)
        return bmp
    }

    private fun drawCrystalButton(c: Canvas, x: Float, y: Float, w: Float, h: Float, fill: Int, rim: Int) {
        val fp = Paint().apply { color = fill }
        c.drawRect(x + 1f, y + 1f, x + w - 1f, y + h - 1f, fp)
        // Top specular white prism sheen
        val sheen = Paint().apply { color = Color.argb(95, 255, 255, 255) }
        c.drawRect(x + 2f, y + 1f, x + w - 2f, y + 3f, sheen)
        val bp = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = rim }
        c.drawRect(x + 0.5f, y + 0.5f, x + w - 0.5f, y + h - 0.5f, bp)
    }

    private fun buildSkyboxPanoramaFaces(context: Context, theme: SkyboxTheme): List<Bitmap> {
        val posterRes = when (theme) {
            SkyboxTheme.SAKURA_SUNSET -> R.drawable.aerix_clip_poster_sakura_forest
            SkyboxTheme.STARRY_NEBULA -> R.drawable.aerix_clip_poster_portal
            SkyboxTheme.DEEP_VOID -> R.drawable.aerix_clip_poster_falling_snow
            SkyboxTheme.AURORA_CRYSTAL -> R.drawable.aerix_clip_poster_northern_light
        }
        val raw = runCatching { BitmapFactory.decodeResource(context.resources, posterRes) }.getOrNull()
        val tintColor = when (theme) {
            SkyboxTheme.AURORA_CRYSTAL -> Color.argb(46, 141, 239, 224)
            SkyboxTheme.SAKURA_SUNSET -> Color.argb(38, 247, 168, 216)
            SkyboxTheme.STARRY_NEBULA -> Color.argb(68, 92, 60, 180)
            SkyboxTheme.DEEP_VOID -> Color.argb(150, 5, 11, 20)
        }
        return List(6) { idx ->
            val face = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(face)
            if (raw != null && raw.width > 64 && raw.height > 64) {
                val sliceW = (raw.width / 4).coerceAtLeast(32)
                val sx = ((idx % 4) * ((raw.width - sliceW) / 3.coerceAtLeast(1))).coerceIn(0, raw.width - sliceW)
                val crop = Bitmap.createBitmap(raw, sx, 0, sliceW, raw.height)
                canvas.drawBitmap(Bitmap.createScaledBitmap(crop, 256, 256, true), 0f, 0f, null)
            } else {
                face.eraseColor(0xFF0A1E2C.toInt())
            }
            val p = Paint().apply { color = tintColor }
            canvas.drawRect(0f, 0f, 256f, 256f, p)
            face
        }
    }

    private fun buildLowFireStrip(seed: Int): Bitmap {
        // 16×32 (2 frames of 16×16). Top 10 rows of each frame are transparent -> 65% lower fire!
        val bmp = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888)
        for (frame in 0..1) {
            val yOff = frame * 16
            for (y in 10..15) {
                for (x in 0..15) {
                    val wave = (x + frame * 3 + seed * 5) % 4
                    if (y == 10 && wave > 1) continue
                    val col = when {
                        y >= 14 -> 0xFFFFF3A1.toInt()
                        y >= 12 -> 0xFFFF9E2C.toInt()
                        else -> 0xFFFF4D2E.toInt()
                    }
                    bmp.setPixel(x, yOff + y, col)
                }
            }
        }
        return bmp
    }

    private fun buildShortSword16(bladeMain: Int, edgeTrim: Int): Bitmap {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val hilt = 0xFF5C3D24.toInt()
        val pommel = edgeTrim
        // Handle bottom-left (1..4, 11..14)
        bmp.setPixel(1, 14, pommel)
        bmp.setPixel(2, 13, hilt)
        bmp.setPixel(3, 12, hilt)
        // Crossguard
        for ((gx, gy) in listOf(2 to 10, 3 to 11, 4 to 11, 5 to 12, 4 to 10, 5 to 13)) {
            bmp.setPixel(gx, gy, edgeTrim)
        }
        // Compact short PvP blade (diagonal up to x=11, y=4 instead of x=15, y=0!)
        for (step in 0..6) {
            val bx = 5 + step
            val by = 10 - step
            bmp.setPixel(bx, by, bladeMain)
            if (step < 6) {
                bmp.setPixel(bx - 1, by, 0xFFFFFFFF.toInt())
                bmp.setPixel(bx, by + 1, edgeTrim)
            }
        }
        return bmp
    }

    private fun buildShieldModelJson(blocking: Boolean): String {
        val ty = if (blocking) -3.2 else -5.5
        return """
            {
              "parent": "builtin/entity",
              "gui_light": "front",
              "textures": { "particle": "block/dark_oak_planks" },
              "display": {
                "thirdperson_righthand": { "rotation": [ 0, 90, 0 ], "translation": [ 10, 6, -4 ], "scale": [ 1, 1, 1 ] },
                "thirdperson_lefthand": { "rotation": [ 0, 90, 0 ], "translation": [ 10, 6, 12 ], "scale": [ 1, 1, 1 ] },
                "firstperson_righthand": { "rotation": [ 0, 180, 5 ], "translation": [ -10, $ty, -10 ], "scale": [ 0.9, 0.9, 0.9 ] },
                "firstperson_lefthand": { "rotation": [ 0, 180, 5 ], "translation": [ 10, $ty, -10 ], "scale": [ 0.9, 0.9, 0.9 ] },
                "gui": { "rotation": [ 15, -25, -5 ], "translation": [ 2, 3, 0 ], "scale": [ 0.65, 0.65, 0.65 ] },
                "fixed": { "rotation": [ 0, 180, 0 ], "translation": [ -2, 4, -5 ], "scale": [ 0.5, 0.5, 0.5 ] },
                "ground": { "rotation": [ 0, 0, 0 ], "translation": [ 4, 4, 2 ], "scale": [ 0.25, 0.25, 0.25 ] }
              }
            }
        """.trimIndent()
    }

    private fun buildCompactTotemJson(): String = """
        {
          "parent": "minecraft:item/generated",
          "textures": { "layer0": "minecraft:item/totem_of_undying" },
          "display": {
            "firstperson_righthand": { "rotation": [ 0, -90, 25 ], "translation": [ 1.13, 1.2, 1.13 ], "scale": [ 0.42, 0.42, 0.42 ] },
            "firstperson_lefthand": { "rotation": [ 0, 90, -25 ], "translation": [ 1.13, 1.2, 1.13 ], "scale": [ 0.42, 0.42, 0.42 ] },
            "fixed": { "scale": [ 0.48, 0.48, 0.48 ] }
          }
        }
    """.trimIndent()

    private fun buildOutlinedOre16(rockColor: Int, glowColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0..15) {
            for (x in 0..15) {
                val edge = x == 0 || x == 15 || y == 0 || y == 15
                if (edge) {
                    bmp.setPixel(x, y, glowColor)
                } else {
                    val shade = if ((x * 7 + y * 13) % 5 == 0) darken(rockColor, 0.86f) else rockColor
                    bmp.setPixel(x, y, shade)
                }
            }
        }
        // Crisp gem clusters inside
        val gems = listOf(4 to 4, 5 to 4, 4 to 5, 10 to 6, 11 to 6, 10 to 7, 6 to 10, 7 to 10, 7 to 11, 11 to 11)
        for ((gx, gy) in gems) bmp.setPixel(gx, gy, glowColor)
        bmp.setPixel(5, 5, 0xFFFFFFFF.toInt())
        bmp.setPixel(11, 7, 0xFFFFFFFF.toInt())
        return bmp
    }

    private fun buildCleanGlass16(): Bitmap {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val rim = Color.argb(190, 216, 252, 255)
        for (i in 0..15) {
            bmp.setPixel(i, 0, rim)
            bmp.setPixel(i, 15, rim)
            bmp.setPixel(0, i, rim)
            bmp.setPixel(15, i, rim)
        }
        // Subtle crystal glint in top-left corner
        bmp.setPixel(2, 2, Color.argb(150, 255, 255, 255))
        bmp.setPixel(3, 2, Color.argb(110, 200, 250, 255))
        bmp.setPixel(2, 3, Color.argb(110, 200, 250, 255))
        return bmp
    }

    private fun buildBowStage16(stageColor: Int, pullAmount: Int): Bitmap {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val wood = 0xFF8B5E34.toInt()
        // Curved bow limbs
        val limb = listOf(4 to 1, 3 to 2, 2 to 3, 2 to 4, 3 to 5, 4 to 6, 5 to 7, 6 to 8, 7 to 9, 8 to 10, 9 to 11, 10 to 12, 11 to 13, 12 to 13, 13 to 12, 14 to 11)
        for ((lx, ly) in limb) bmp.setPixel(lx, ly, wood)
        // Glowing colored string & arrow tip indicating exact charge stage!
        val pullOffset = pullAmount.coerceIn(1, 3)
        for (i in 2..(10 + pullOffset)) {
            val sx = i.coerceIn(0, 15)
            val sy = (15 - i).coerceIn(0, 15)
            bmp.setPixel(sx, sy, stageColor)
        }
        bmp.setPixel(12, 3, 0xFFFFFFFF.toInt())
        bmp.setPixel(13, 2, stageColor)
        return bmp
    }

    private fun buildDestroyStage16(stage: Int): Bitmap {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val frac = (stage + 1) / 10f
        val col = when {
            stage <= 3 -> 0xCC8DEFE0.toInt()
            stage <= 6 -> 0xCCFFD166.toInt()
            else -> 0xCCFF4769.toInt()
        }
        val fillW = (14 * frac).toInt().coerceIn(1, 14)
        for (x in 1..fillW) {
            bmp.setPixel(x, 7, col)
            bmp.setPixel(x, 8, col)
        }
        for (i in 0..15) {
            if (i <= (15 * frac).toInt()) {
                bmp.setPixel(i, 0, col)
                bmp.setPixel(i, 15, col)
            }
        }
        return bmp
    }

    private fun darken(c: Int, factor: Float): Int = Color.argb(
        Color.alpha(c),
        (Color.red(c) * factor).toInt().coerceIn(0, 255),
        (Color.green(c) * factor).toInt().coerceIn(0, 255),
        (Color.blue(c) * factor).toInt().coerceIn(0, 255)
    )

    private fun putText(zos: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun putBitmap(zos: ZipOutputStream, path: String, bmp: Bitmap) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        zos.write(out.toByteArray())
        zos.closeEntry()
    }
}
