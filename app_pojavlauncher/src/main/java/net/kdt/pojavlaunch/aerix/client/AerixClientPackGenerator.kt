package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.resourcepack.ResourcePackOptions
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Generates `Aerix-Client-Pack.zip` inside the Aerix Client instance's `resourcepacks/` folder,
 * preserving 100% authentic Mojang vanilla item/tool/armor artwork while adding live shimmer
 * & floating sparkle particle animations, replacing the Title Screen logo with `AERIX CLIENT`,
 * applying the dark Minecraft-style panorama, and tuning FPS configs.
 */
object AerixClientPackGenerator {

    const val PACK_FILE_NAME = "Aerix-Client-Pack.zip"
    private const val PACK_SCHEMA_REV = "v6_aerix_dark_landscape_2026"
    internal const val CLIENT_BACKGROUND_ASSET = "aerix/client_dark_background.webp"

    fun packFormatForVersion(mcVersion: String): Int = ClientVersions.format(mcVersion)

    fun resolveMcVersionFromInstance(versionId: String?, fallback: String = "1.16.5"): String =
        ClientVersions.resolve(versionId, fallback)

    fun findVanillaClientJar(mcVersion: String, instanceGameDir: File? = null): File? {
        val candidates = ArrayList<File>()
        val verRoot = Tools.DIR_HOME_VERSION
        if (!verRoot.isNullOrBlank()) {
            candidates += File(verRoot, "$mcVersion/$mcVersion.jar")
        }
        if (instanceGameDir != null) {
            candidates += File(instanceGameDir, "versions/$mcVersion/$mcVersion.jar")
        }
        for (c in candidates) {
            if (c.isFile && c.length() > 100_000L) return c
        }
        return null
    }

    /**
     * Fast stamp-checked sync called at launch (`attachForInstance` & Launcher Launch button) so
     * existing Client installs automatically upgrade to the new `AERIX CLIENT` logo, dark landscape
     * background, and genuine vanilla animated gear once `<mcVersion>.jar` is on disk.
     */
    fun ensurePackUpToDate(context: Context, instanceGameDir: File, mcVersion: String) {
        AerixClientStore.init(context)
        val rpDir = File(instanceGameDir, "resourcepacks").apply { mkdirs() }
        val zipFile = File(rpDir, PACK_FILE_NAME)
        val legacyStampFile = File(rpDir, ".aerix_pack_stamp")
        if (legacyStampFile.isFile) runCatching { legacyStampFile.delete() }
        val configDir = File(instanceGameDir, "config").apply { mkdirs() }
        val stampFile = File(configDir, ".aerix_pack_stamp")
        val jarReady = findVanillaClientJar(mcVersion, instanceGameDir) != null
        val desiredStamp = buildString {
            append(PACK_SCHEMA_REV).append('|')
            append(mcVersion).append('|')
            append("jar=").append(jarReady).append('|')
            append("animGear=").append(AerixClientStore.packVanillaAnimatedGear).append('|')
            append("allItemSprites=").append(AerixClientStore.packAnimateAllItemSprites).append('|')
            append("glint=").append(AerixClientStore.packEnhancedGlint).append('|')
            append("glintColor=").append(AerixClientStore.packGlintColor).append('|')
            append("fire=").append(AerixClientStore.packLowFire).append('|')
            append("shield=").append(AerixClientStore.packLoweredShield).append('|')
            append("totem=").append(AerixClientStore.packCompactTotem).append('|')
            append("pump=").append(AerixClientStore.packNoPumpkinBlur).append('|')
            append("water=").append(AerixClientStore.packClearWater).append('|')
            append("allDarkGui=1")
        }
        val currentStamp = if (stampFile.isFile) runCatching { stampFile.readText().trim() }.getOrDefault("") else ""
        if (!zipFile.isFile || zipFile.length() < 1024L || currentStamp != desiredStamp) {
            buildAndApplyToInstance(context, instanceGameDir, mcVersion)
            runCatching { stampFile.writeText(desiredStamp) }
        } else {
            applyClientOptionsTxt(instanceGameDir, mcVersion)
            applySodiumAndModConfigs(instanceGameDir)
            AerixClientCosmetics.syncCapeAssetsToInstance(context, instanceGameDir)
        }
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
        val writtenPaths = HashSet<String>()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val modernRange = if (packFmt >= 69) ",\n    \"min_format\": [$packFmt, ${ClientVersions.formatMinor(mcVersion)}],\n    \"max_format\": [$packFmt, ${ClientVersions.formatMinor(mcVersion)}]" else ""
            val mcmeta = """
                {
                  "pack": {
                    "pack_format": $packFmt$modernRange,
                    "description": "\u00a7c\u2726 Aerix Client $mcVersion \u00a78\u00b7 Crimson Performance"
                  }
                }
            """.trimIndent()
            putText(zos, "pack.mcmeta", mcmeta, writtenPaths)
            putBitmap(zos, "pack.png", buildPackIcon(context), writtenPaths)

            // 1. Title Screen: Replace "MINECRAFT JAVA EDITION" with "AERIX CLIENT" + Misty Forest Background
            if (AerixClientStore.packCustomMainMenu) {
                val logoBmp = if (packFmt >= 13) {
                    buildAerixTitleLogoModern(context)
                } else {
                    buildAerixTitleLogoLegacySplit(context)
                }
                putBitmap(zos, "assets/minecraft/textures/gui/title/minecraft.png", logoBmp, writtenPaths)
                putBitmap(zos, "assets/minecraft/textures/gui/title/minceraft.png", logoBmp, writtenPaths)

                // Transparent edition.png so vanilla "JAVA EDITION" never appears over "AERIX CLIENT"
                val emptyEdition = Bitmap.createBitmap(128, 16, Bitmap.Config.ARGB_8888)
                emptyEdition.eraseColor(Color.TRANSPARENT)
                putBitmap(zos, "assets/minecraft/textures/gui/title/edition.png", emptyEdition, writtenPaths)

                // Transparent panorama_overlay.png so Minecraft never adds milky white gradient washout
                val clearOverlay = Bitmap.createBitmap(16, 128, Bitmap.Config.ARGB_8888)
                clearOverlay.eraseColor(Color.TRANSPARENT)
                putBitmap(zos, "assets/minecraft/textures/gui/title/background/panorama_overlay.png", clearOverlay, writtenPaths)

                // Cubemap faces projected from the shared Client landscape.
                val panoFaces = buildClientPanoramaFaces(context)
                panoFaces.forEachIndexed { idx, face ->
                    putBitmap(zos, "assets/minecraft/textures/gui/title/background/panorama_$idx.png", face, writtenPaths)
                }

                putText(
                    zos,
                    "assets/minecraft/texts/splashes.txt",
                    listOf(
                        "✦ Aerix Client!",
                        "Crimson & Obsidian!",
                        "Maximum Mobile FPS!",
                        "Waving 3D Animated Capes!",
                        "Powered by Aerix Launcher!"
                    ).joinToString("\n"),
                    writtenPaths
                )
            }

            // 1b. All-Dark GUI across ALL Minecraft versions (1.16.5 - 26.3: containers, menus, widgets, sprites)
            if (packFmt < 18) {
                putBitmap(zos, "assets/minecraft/textures/gui/widgets.png", buildCrimsonDarkWidgets(), writtenPaths)
            }
            appendAllDarkGuiTextures(zos, mcVersion, instanceGameDir, packFmt, writtenPaths)

            // 2. 100% Genuine Vanilla Item, Tool, Weapon & Armor Skins + Shimmer & Sparkle Particle Animation!
            if (AerixClientStore.packVanillaAnimatedGear) {
                appendVanillaAnimatedGear(zos, mcVersion, instanceGameDir)
            }

            // 3. Animated Sparkle Enchantment Glint (for enchanted items & worn 3D armor!)
            if (AerixClientStore.packEnhancedGlint) {
                val glintBmp = buildSparkleEnchantGlint128()
                if (packFmt >= 13) {
                    putBitmap(zos, "assets/minecraft/textures/misc/enchanted_glint_item.png", glintBmp)
                    putBitmap(zos, "assets/minecraft/textures/misc/enchanted_glint_entity.png", glintBmp)
                } else {
                    putBitmap(zos, "assets/minecraft/textures/misc/enchanted_item_glint.png", glintBmp)
                }
            }

            // 4. Low Fire (-65% height animated fire_0 & fire_1 so fire never blinds PvP view)
            if (AerixClientStore.packLowFire) {
                val fireMeta = """{"animation":{"frametime":3}}"""
                putBitmap(zos, "assets/minecraft/textures/block/fire_0.png", buildLowFireStrip(seed = 0))
                putText(zos, "assets/minecraft/textures/block/fire_0.png.mcmeta", fireMeta)
                putBitmap(zos, "assets/minecraft/textures/block/fire_1.png", buildLowFireStrip(seed = 1))
                putText(zos, "assets/minecraft/textures/block/fire_1.png.mcmeta", fireMeta)
            }

            // 5. Lowered Shield (-50% first-person offset so shield never blocks crosshair)
            if (AerixClientStore.packLoweredShield) {
                putText(zos, "assets/minecraft/models/item/shield.json", buildShieldModelJson(blocking = false))
                putText(zos, "assets/minecraft/models/item/shield_blocking.json", buildShieldModelJson(blocking = true))
            }

            // 6. Compact Totem first-person scale (keeps 100% genuine vanilla totem texture!)
            if (AerixClientStore.packCompactTotem) {
                putText(zos, "assets/minecraft/models/item/totem_of_undying.json", buildCompactTotemJson())
            }

            // 7. Crystal Clear Underwater Overlay
            if (AerixClientStore.packClearWater) {
                val uw = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                uw.eraseColor(Color.argb(14, 180, 220, 245))
                putBitmap(zos, "assets/minecraft/textures/misc/underwater.png", uw)
            }

            // 8. No Pumpkin Blur
            if (AerixClientStore.packNoPumpkinBlur) {
                val pb = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                pb.eraseColor(Color.TRANSPARENT)
                putBitmap(zos, "assets/minecraft/textures/misc/pumpkinblur.png", pb)
            }
        }

        applyClientOptionsTxt(instanceGameDir, mcVersion)
        applySodiumAndModConfigs(instanceGameDir)
        AerixClientCosmetics.syncCapeAssetsToInstance(context, instanceGameDir)
        return zipFile
    }

    /**
     * Syncs `<instanceGameDir>/options.txt` with all competitive FPS boosters & active client settings.
     */
    fun applyClientOptionsTxt(instanceGameDir: File, mcVersion: String) {
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

        map["resourcePacks"] = ResourcePackOptions.mergeClientPack(
            map["resourcePacks"].orEmpty(),
            "file/$PACK_FILE_NAME",
            AerixClientStore.installClientResourcePack
        )

        if (!map.containsKey("panoramaScrollSpeed")) map["panoramaScrollSpeed"] = "0.0"
        if (!map.containsKey("monochromeLogo")) map["monochromeLogo"] = if (AerixClientStore.packCustomMojangSplash) "true" else "false"
        if (!map.containsKey("darkMojangStudiosBackground")) map["darkMojangStudiosBackground"] = "true"
        map["gamma"] = if (AerixClientStore.fullbrightEnabled) "100.0" else "1.0"
        // Initial defaults only: never overwrite existing user graphics or audio choices on launch.
        if (!map.containsKey("autoJump")) map["autoJump"] = "false"
        if (mcVersion == "26.3" && !map.containsKey("graphicsPreset")) {
            map["graphicsPreset"] = "\"fast\""
            // A preset name alone does not apply its component values while loading options.
            if (!map.containsKey("ao")) map["ao"] = "false"
            if (!map.containsKey("cutoutLeaves")) map["cutoutLeaves"] = "false"
            if (!map.containsKey("improvedTransparency")) map["improvedTransparency"] = "false"
            if (!map.containsKey("weatherRadius")) map["weatherRadius"] = "5"
            if (!map.containsKey("entityDistanceScaling")) map["entityDistanceScaling"] = "0.75"
        }
        if (!map.containsKey("renderDistance")) map["renderDistance"] = "8"
        if (!map.containsKey("simulationDistance")) map["simulationDistance"] = "5"
        if (!map.containsKey("entityShadows")) map["entityShadows"] = "false"
        if (!map.containsKey("pauseOnLostFocus")) map["pauseOnLostFocus"] = "false"

        val outText = map.entries.joinToString("\n") { "${it.key}:${it.value}" } + "\n"
        optFile.writeText(outText)

        // Same file the in-game bridge polls every tick, so a toggle also works without a restart.
        runCatching { ClientLiveOptionsBridge.syncAll(instanceGameDir) }
    }

    // Do not overwrite third-party mod configuration; versions have different schemas and
    // existing installations may contain the player's carefully tuned settings.
    fun applySodiumAndModConfigs(@Suppress("UNUSED_PARAMETER") instanceGameDir: File) = Unit

    /* ═══════════════════════════════════════════════════════════════════════
     * 1. TITLE SCREEN "AERIX CLIENT" LOGO BUILDERS (EXACT LEGACY & MODERN UV)
     * ═══════════════════════════════════════════════════════════════════════ */

    /** Draw the supplied A and handwritten wordmark on the Minecraft title's 44px strip. */
    private fun renderAerixBanner(context: Context, logicalWidth: Int, scale: Int = 4): Bitmap {
        val bmp = Bitmap.createBitmap(logicalWidth * scale, 44 * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val mark = BitmapFactory.decodeResource(context.resources, R.drawable.ic_aerix_mark)
        val wordmark = BitmapFactory.decodeResource(context.resources, R.drawable.aerix_wordmark)
        val left = (logicalWidth - 215f) / 2f * scale
        if (mark != null) {
            canvas.drawBitmap(mark, null, RectF(left, 1f * scale, left + 42f * scale, 43f * scale), paint)
        }
        if (wordmark != null) {
            val wordLeft = left + 50f * scale
            val wordWidth = 165f * scale
            val wordHeight = wordWidth * wordmark.height / wordmark.width
            canvas.drawBitmap(wordmark, null, RectF(wordLeft, 7f * scale, wordLeft + wordWidth, 7f * scale + wordHeight), paint)
        }
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = 7f * scale
            color = Color.WHITE
            setShadowLayer(1f * scale, 0f, 1f * scale, Color.BLACK)
        }
        canvas.drawText("CLIENT", left + 132.5f * scale, 37f * scale, subtitle)
        return bmp
    }

    /**
     * Exact Minecraft `1.16.5`–`1.19.2` (`packFmt < 13`) `textures/gui/title/minecraft.png` layout:
     * Canvas `256×256` (at `4×` HD = `1024×1024`):
     * - Left half drawn at `width/2 - 137` reads UV `(0, 0, w=155, h=44)`
     * - Right half drawn at `width/2 + 18` reads UV `(0, 45, w=155, h=44)` (centered visual span = `274` units: `155 + 119`)
     */
    private fun buildAerixTitleLogoLegacySplit(context: Context): Bitmap {
        val scale = 4
        val sheet = Bitmap.createBitmap(256 * scale, 256 * scale, Bitmap.Config.ARGB_8888)
        val banner = renderAerixBanner(context, logicalWidth = 274, scale = scale)
        val c = Canvas(sheet)

        val leftW = 155 * scale
        val rightW = 119 * scale
        val rowH = 44 * scale

        val leftSlice = Bitmap.createBitmap(banner, 0, 0, leftW, rowH)
        val rightSlice = Bitmap.createBitmap(banner, leftW, 0, rightW, rowH)

        c.drawBitmap(leftSlice, 0f, 0f, null)
        c.drawBitmap(rightSlice, 0f, (45 * scale).toFloat(), null)
        return sheet
    }

    /**
     * Exact Minecraft `1.19.4`–`1.21.4` (`packFmt >= 13`) `LogoRenderer` layout:
     * Canvas `256×64` (at `4×` HD = `1024×256`), single centered blit of UV `(0, 0, w=256, h=44)`.
     */
    private fun buildAerixTitleLogoModern(context: Context): Bitmap {
        val scale = 4
        val sheet = Bitmap.createBitmap(256 * scale, 64 * scale, Bitmap.Config.ARGB_8888)
        val banner = renderAerixBanner(context, logicalWidth = 256, scale = scale)
        Canvas(sheet).drawBitmap(banner, 0f, 0f, null)
        return sheet
    }

    /* ═══════════════════════════════════════════════════════════════════════
     * 2. DARK CLIENT PANORAMA (`panorama_0..5.png`)
     * ═══════════════════════════════════════════════════════════════════════ */

    /**
     * Projects the shared dark Client background
     * seamlessly across all 6 `384×384` cubemap faces (`0`=front, `1`=right, `2`=back, `3`=left, `4`=up, `5`=down)
     * using proper 3D ray-direction spherical projection + mirrored horizontal wrap so there are ZERO seams!
     */
    private fun buildClientPanoramaFaces(context: Context): List<Bitmap> {
        val raw = runCatching {
            context.assets.open(CLIENT_BACKGROUND_ASSET).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        if (raw == null) {
            return List(6) {
                Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(0xFF1A1B1E.toInt())
                }
            }
        }

        val srcW = raw.width.coerceAtLeast(2)
        val srcH = raw.height.coerceAtLeast(2)
        val srcPixels = IntArray(srcW * srcH)
        raw.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)
        raw.recycle()

        // Compute average top-mist color and bottom-forest-floor color for smooth zenith/nadir blending
        var topR = 0L; var topG = 0L; var topB = 0L
        var botR = 0L; var botG = 0L; var botB = 0L
        for (x in 0 until srcW) {
            val tc = srcPixels[x]
            topR += Color.red(tc); topG += Color.green(tc); topB += Color.blue(tc)
            val bc = srcPixels[(srcH - 1) * srcW + x]
            botR += Color.red(bc); botG += Color.green(bc); botB += Color.blue(bc)
        }
        val zenithCol = Color.rgb((topR / srcW).toInt(), (topG / srcW).toInt(), (topB / srcW).toInt())
        val nadirCol = Color.rgb((botR / srcW).toInt(), (botG / srcW).toInt(), (botB / srcW).toInt())

        val faceSize = 384
        val faces = ArrayList<Bitmap>(6)

        for (faceIdx in 0..5) {
            val outPixels = IntArray(faceSize * faceSize)
            for (py in 0 until faceSize) {
                val b = (2.0 * (py + 0.5) / faceSize) - 1.0 // -1 (top) .. +1 (bottom)
                for (px in 0 until faceSize) {
                    val a = (2.0 * (px + 0.5) / faceSize) - 1.0 // -1 (left) .. +1 (right)
                    // Minecraft panorama cubemap directions: 0=+Z, 1=-X, 2=-Z, 3=+X, 4=+Y(up), 5=-Y(down)
                    val dx: Double
                    val dy: Double
                    val dz: Double
                    when (faceIdx) {
                        0 -> { dx = a; dy = -b; dz = 1.0 }
                        1 -> { dx = -1.0; dy = -b; dz = a }
                        2 -> { dx = -a; dy = -b; dz = -1.0 }
                        3 -> { dx = 1.0; dy = -b; dz = -a }
                        4 -> { dx = a; dy = 1.0; dz = b }
                        else -> { dx = a; dy = -1.0; dz = -b }
                    }

                    val horiz = hypot(dx, dz)
                    val pitch = atan2(dy, horiz) // -pi/2 .. +pi/2
                    val yaw = atan2(-dx, dz)     // -pi .. +pi (0 at center of face 0)

                    // Mirror-wrap the landscape around the 360° horizon.
                    val normYaw = (yaw + Math.PI) / (2.0 * Math.PI) // 0..1
                    val saw = (normYaw * 2.0) % 2.0
                    val mirroredU = if (saw <= 1.0) saw else (2.0 - saw)

                    // Map vertical FOV onto the source height, blending into the sky and ground.
                    val vRaw = 0.5 - (pitch / 1.08)
                    val col = when {
                        vRaw <= 0.0 -> zenithCol
                        vRaw >= 1.0 -> nadirCol
                        else -> {
                            val sx = (mirroredU * (srcW - 1)).toInt().coerceIn(0, srcW - 1)
                            val sy = (vRaw * (srcH - 1)).toInt().coerceIn(0, srcH - 1)
                            val sample = srcPixels[sy * srcW + sx]
                            if (vRaw < 0.12) {
                                blendRgb(zenithCol, sample, (vRaw / 0.12).toFloat())
                            } else if (vRaw > 0.88) {
                                blendRgb(sample, nadirCol, ((vRaw - 0.88) / 0.12).toFloat())
                            } else {
                                sample or -0x1000000
                            }
                        }
                    }
                    outPixels[py * faceSize + px] = col or -0x1000000
                }
            }
            val bmp = Bitmap.createBitmap(faceSize, faceSize, Bitmap.Config.ARGB_8888)
            bmp.setPixels(outPixels, 0, faceSize, 0, 0, faceSize, faceSize)
            faces += bmp
        }
        return faces
    }

    private fun blendRgb(c1: Int, c2: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * f).toInt().coerceIn(0, 255)
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * f).toInt().coerceIn(0, 255)
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * f).toInt().coerceIn(0, 255)
        return Color.argb(255, r, g, b)
    }

    /* ═══════════════════════════════════════════════════════════════════════
     * 3. 100% AUTHENTIC VANILLA SKINS + LIVE SHIMMER & SPARKLE PARTICLES
     * ═══════════════════════════════════════════════════════════════════════ */

    private val ANIMATED_VANILLA_ITEM_SUFFIXES = listOf(
        "_sword.png", "_pickaxe.png", "_axe.png", "_shovel.png", "_hoe.png",
        "_helmet.png", "_chestplate.png", "_leggings.png", "_boots.png"
    )

    private val ANIMATED_VANILLA_EXACT_ITEMS = setOf(
        "bow.png", "bow_pulling_0.png", "bow_pulling_1.png", "bow_pulling_2.png",
        "crossbow_standby.png", "crossbow_pulling_0.png", "crossbow_pulling_1.png", "crossbow_pulling_2.png",
        "crossbow_arrow.png", "trident.png", "mace.png", "elytra.png",
        "golden_apple.png", "ender_pearl.png", "ender_eye.png", "blaze_rod.png",
        "diamond.png", "emerald.png", "netherite_ingot.png", "gold_ingot.png", "iron_ingot.png",
        "totem_of_undying.png", "fishing_rod.png", "shears.png", "flint_and_steel.png"
    )

    /**
     * Extracts genuine untouched Mojang item/tool/weapon/armor PNGs from `<mcVersion>.jar` and
     * builds an 8-frame vertical animation strip (`w × 8h`) + `.png.mcmeta` that keeps 100% of
     * the vanilla sprite design while adding a diagonal specular shimmer + tiny floating sparkle particles!
     */
    private fun appendVanillaAnimatedGear(
        zos: ZipOutputStream,
        mcVersion: String,
        instanceGameDir: File
    ) {
        val jar = findVanillaClientJar(mcVersion, instanceGameDir) ?: return
        runCatching {
            ZipFile(jar).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val path = entry.name
                    if (!path.startsWith("assets/minecraft/textures/item/") || !path.endsWith(".png")) continue

                    val fileName = path.substringAfterLast('/')
                    val shouldAnimate = ANIMATED_VANILLA_EXACT_ITEMS.contains(fileName) ||
                        ANIMATED_VANILLA_ITEM_SUFFIXES.any { fileName.endsWith(it) }
                    if (!AerixClientStore.packAnimateAllItemSprites && !shouldAnimate) continue

                    // Skip if vanilla already defines a custom .mcmeta animation for this sprite
                    if (zf.getEntry("$path.mcmeta") != null) continue

                    val bytes = zf.getInputStream(entry).use { it.readBytes() }
                    val baseBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                    // Only animate square single-frame sprites (16x16 or 32x32)
                    if (baseBmp.width <= 0 || baseBmp.width != baseBmp.height || baseBmp.width > 32) {
                        baseBmp.recycle()
                        continue
                    }
                    try {
                        val particleColor = resolveMaterialParticleColor(fileName)
                        val strip = buildVanillaShimmerAndParticleStrip(baseBmp, particleColor, fileName.hashCode())
                        try {
                            putBitmap(zos, path, strip)
                            putText(zos, "$path.mcmeta", """{"animation":{"frametime":2}}""")
                        } finally { strip.recycle() }
                    } finally { baseBmp.recycle() }
                }
            }
        }
    }

    private fun resolveMaterialParticleColor(fileName: String): Int = when {
        fileName.contains("diamond") -> 0xFF6BF7FF.toInt()
        fileName.contains("netherite") -> 0xFFFF385C.toInt()
        fileName.contains("golden") || fileName.contains("gold") || fileName.contains("totem") || fileName.contains("blaze") -> 0xFFFFDF6B.toInt()
        fileName.contains("emerald") || fileName.contains("ender") -> 0xFF4FF5A6.toInt()
        fileName.contains("iron") || fileName.contains("chainmail") || fileName.contains("trident") || fileName.contains("mace") -> 0xFFEAF6FF.toInt()
        else -> 0xFFFFD8A8.toInt()
    }

    /**
     * Preserves 100% of `baseBmp`'s vanilla pixels and silhouette while building an 8-frame
     * animation strip with:
     * 1) A subtle diagonal specular light gleam passing across opaque vanilla pixels, AND
     * 2) Tiny 1-pixel glowing aura/sparkle particles gently rising & twinkling in nearby transparent air pixels!
     */
    private fun buildVanillaShimmerAndParticleStrip(
        baseBmp: Bitmap,
        sparkColor: Int,
        seed: Int
    ): Bitmap {
        val sz = baseBmp.width
        val frames = 8
        val strip = Bitmap.createBitmap(sz, sz * frames, Bitmap.Config.ARGB_8888)
        val basePixels = IntArray(sz * sz)
        baseBmp.getPixels(basePixels, 0, sz, 0, 0, sz, sz)

        // Identify candidate transparent air pixels within 1-2px of opaque vanilla item pixels for floating particles
        val haloCandidates = ArrayList<Pair<Int, Int>>()
        for (y in 0 until sz) {
            for (x in 0 until sz) {
                if (Color.alpha(basePixels[y * sz + x]) > 20) continue
                var nearOpaque = false
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until sz && ny in 0 until sz && Color.alpha(basePixels[ny * sz + nx]) > 140) {
                            nearOpaque = true
                            break
                        }
                    }
                    if (nearOpaque) break
                }
                if (nearOpaque) {
                    haloCandidates += (x to y)
                }
            }
        }

        val framePixels = IntArray(sz * sz)
        for (f in 0 until frames) {
            // Start with 100% untouched authentic Mojang vanilla pixels
            System.arraycopy(basePixels, 0, framePixels, 0, basePixels.size)

            // 1. Subtle diagonal specular shimmer sweep across opaque item/tool/armor pixels
            val bandCenter = -4 + (f * (sz * 2 + 8)) / frames
            for (y in 0 until sz) {
                for (x in 0 until sz) {
                    val c = framePixels[y * sz + x]
                    val a = Color.alpha(c)
                    if (a < 80) continue
                    val diag = x + (sz - 1 - y)
                    val dist = abs(diag - bandCenter)
                    if (dist <= 1) {
                        val boost = if (dist == 0) 0.30f else 0.14f
                        val target = if (dist == 0) Color.WHITE else sparkColor
                        val nr = (Color.red(c) + (Color.red(target) - Color.red(c)) * boost).toInt().coerceIn(0, 255)
                        val ng = (Color.green(c) + (Color.green(target) - Color.green(c)) * boost).toInt().coerceIn(0, 255)
                        val nb = (Color.blue(c) + (Color.blue(target) - Color.blue(c)) * boost).toInt().coerceIn(0, 255)
                        framePixels[y * sz + x] = Color.argb(a, nr, ng, nb)
                    }
                }
            }

            // 2. Crisp floating 1px sparkle particles twinkling around the vanilla item silhouette
            if (haloCandidates.size >= 4) {
                val sparkCount = 3
                for (s in 0 until sparkCount) {
                    val idxBase = Math.floorMod(seed + s * 37, haloCandidates.size)
                    val (hx, hy) = haloCandidates[(idxBase + f * 2) % haloCandidates.size]
                    val twinkleAlpha = when ((f + s * 3) % 4) {
                        0 -> 235
                        1 -> 155
                        2 -> 95
                        else -> 0
                    }
                    if (twinkleAlpha > 0) {
                        val pCol = if ((f + s) % 2 == 0) {
                            Color.argb(twinkleAlpha, 255, 255, 255)
                        } else {
                            Color.argb(
                                twinkleAlpha,
                                Color.red(sparkColor),
                                Color.green(sparkColor),
                                Color.blue(sparkColor)
                            )
                        }
                        framePixels[hy * sz + hx] = pCol
                    }
                }
            }

            strip.setPixels(framePixels, 0, sz, 0, f * sz, sz, sz)
        }
        return strip
    }

    /**
     * Luminous crimson-prism + white starlight enchantment glint (`128×128`) so enchanted tools,
     * weapons, and 3D worn armor shimmer with crisp animated particle streaks!
     */
    private fun buildSparkleEnchantGlint128(): Bitmap {
        val sz = 128
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val tint = when (AerixClientStore.packGlintColor) {
            "VIOLET" -> Color.rgb(150, 70, 255)
            "BLUE" -> Color.rgb(48, 130, 255)
            "GOLD" -> Color.rgb(255, 180, 32)
            "GREEN" -> Color.rgb(50, 220, 90)
            "WHITE" -> Color.rgb(220, 220, 220)
            else -> Color.rgb(208, 48, 96)
        }
        for (y in 0 until sz) {
            for (x in 0 until sz) {
                val d1 = (x + y * 2) % 32
                val d2 = ((x * 2 - y) % 40 + 40) % 40
                var r = 0
                var g = 0
                var b = 0
                if (d1 in 0..3) {
                    val k = 4 - d1
                    r += Color.red(tint) * k / 4
                    g += Color.green(tint) * k / 4
                    b += Color.blue(tint) * k / 4
                }
                if (d2 in 0..2) {
                    val k = 3 - d2
                    r += 48 * k
                    g += 44 * k
                    b += 58 * k
                }
                // Crisp starlight particle glints embedded inside the scrolling enchantment layer
                if ((x * 13 + y * 29) % 97 == 0) {
                    r = 255; g = 240; b = 248
                }
                bmp.setPixel(x, y, Color.argb(255, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255)))
            }
        }
        return bmp
    }

    /* ── Pack Icon, Crimson Widgets, Low Fire & Shield Helpers ────────────── */

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
            val raw = BitmapFactory.decodeResource(context.resources, R.drawable.ic_aerix_mark)
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

    private fun buildCrimsonDarkWidgets(): Bitmap {
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fillPaint = Paint()
        val borderPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f }

        // 1. 9-Slot Hotbar (0, 0, w=182, h=22)
        fillPaint.color = Color.argb(180, 20, 22, 27)
        c.drawRect(0f, 0f, 182f, 22f, fillPaint)
        borderPaint.color = Color.argb(215, 220, 30, 58)
        c.drawRect(0.5f, 0.5f, 181.5f, 21.5f, borderPaint)
        borderPaint.color = Color.argb(90, 90, 96, 112)
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

        // 3. Offhand Left/Right Slots
        fillPaint.color = Color.argb(180, 20, 22, 27)
        c.drawRect(24f, 23f, 46f, 45f, fillPaint)
        c.drawRect(60f, 23f, 82f, 45f, fillPaint)
        borderPaint.color = Color.argb(195, 220, 30, 58)
        c.drawRect(24.5f, 23.5f, 45.5f, 44.5f, borderPaint)
        c.drawRect(60.5f, 23.5f, 81.5f, 44.5f, borderPaint)

        // 4-7. Title & Menu Buttons — Sleek Obsidian Dark Gray + Crimson Rim
        drawCrimsonButton(c, 0f, 46f, 200f, 20f, Color.argb(150, 18, 20, 24), Color.argb(110, 72, 76, 90))
        drawCrimsonButton(c, 0f, 66f, 200f, 20f, Color.argb(210, 24, 26, 32), Color.argb(225, 220, 30, 58))
        drawCrimsonButton(c, 0f, 86f, 200f, 20f, Color.argb(230, 68, 18, 28), Color.argb(255, 255, 58, 86))
        drawCrimsonButton(c, 0f, 106f, 20f, 20f, Color.argb(210, 24, 26, 32), Color.argb(225, 220, 30, 58))
        drawCrimsonButton(c, 0f, 126f, 20f, 20f, Color.argb(230, 68, 18, 28), Color.argb(255, 255, 58, 86))
        borderPaint.color = Color.WHITE
        c.drawCircle(10f, 116f, 5f, borderPaint)
        c.drawCircle(10f, 136f, 5f, borderPaint)
        return bmp
    }

    private fun drawCrimsonButton(c: Canvas, x: Float, y: Float, w: Float, h: Float, fill: Int, rim: Int) {
        val fp = Paint().apply { color = fill }
        c.drawRect(x + 1f, y + 1f, x + w - 1f, y + h - 1f, fp)
        val sheen = Paint().apply { color = Color.argb(70, 255, 255, 255) }
        c.drawRect(x + 2f, y + 1f, x + w - 2f, y + 2.5f, sheen)
        val bp = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = rim }
        c.drawRect(x + 0.5f, y + 0.5f, x + w - 0.5f, y + h - 0.5f, bp)
    }

    private fun buildLowFireStrip(seed: Int): Bitmap {
        val bmp = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888)
        for (frame in 0..1) {
            val yBase = frame * 16
            for (x in 0..15) {
                val tipHeight = 3 + ((x * 5 + frame * 3 + seed * 7) % 4)
                for (h in 0 until tipHeight) {
                    val py = yBase + 15 - h
                    val col = when {
                        h == 0 -> Color.argb(235, 255, 240, 150)
                        h <= 2 -> Color.argb(215, 255, 145, 42)
                        else -> Color.argb(175, 220, 38, 48)
                    }
                    bmp.setPixel(x, py, col)
                }
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

    /* ═══════════════════════════════════════════════════════════════════════
     * 4. ALL-DARK GUI ACROSS ALL MINECRAFT VERSIONS (1.16.5 – 26.3)
     * ═══════════════════════════════════════════════════════════════════════ */

    /**
     * Transforms every vanilla GUI texture & sprite under `assets/minecraft/textures/gui/` from
     * `<mcVersion>.jar` (plus `.png.mcmeta` 9-slice metadata and container title formatting in
     * `en_us.json`) into Aerix's Obsidian Dark + Crimson theme, and emits built-in dark
     * fallbacks when the vanilla JAR is not yet downloaded.
     */
    private fun appendAllDarkGuiTextures(
        zos: ZipOutputStream,
        mcVersion: String,
        instanceGameDir: File,
        packFmt: Int,
        writtenPaths: MutableSet<String>
    ) {
        val jar = findVanillaClientJar(mcVersion, instanceGameDir)
        if (jar != null) {
            runCatching {
                ZipFile(jar).use { zf ->
                    val entries = zf.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val path = entry.name
                        if (!path.startsWith("assets/minecraft/textures/gui/") || !path.endsWith(".png")) continue
                        if (writtenPaths.contains(path) || shouldKeepUntouchedGuiPath(path, packFmt)) continue

                        val bytes = zf.getInputStream(entry).use { it.readBytes() }
                        val rawBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                        val darkBmp = try {
                            darkenGuiBitmap(rawBmp, path)
                        } finally {
                            rawBmp.recycle()
                        } ?: continue
                        try {
                            putBitmap(zos, path, darkBmp, writtenPaths)
                        } finally {
                            darkBmp.recycle()
                        }

                        // Preserve 1.20.2+ 9-slice sprite scaling & animation .mcmeta alongside each PNG.
                        val metaEntry = zf.getEntry("$path.mcmeta")
                        if (metaEntry != null && !writtenPaths.contains("$path.mcmeta")) {
                            val metaBytes = zf.getInputStream(metaEntry).use { it.readBytes() }
                            putBytes(zos, "$path.mcmeta", metaBytes, writtenPaths)
                        }
                    }

                    darkenContainerLangJson(zos, zf, writtenPaths)
                }
            }
        }

        appendDarkFallbackGuiAssets(zos, packFmt, writtenPaths)
    }

    private fun shouldKeepUntouchedGuiPath(path: String, packFmt: Int): Boolean {
        if (path == "assets/minecraft/textures/gui/title/minecraft.png" ||
            path == "assets/minecraft/textures/gui/title/minceraft.png" ||
            path == "assets/minecraft/textures/gui/title/edition.png" ||
            path == "assets/minecraft/textures/gui/title/mojangstudios.png" ||
            path.startsWith("assets/minecraft/textures/gui/title/background/panorama")
        ) return true

        if (packFmt < 18 && path == "assets/minecraft/textures/gui/widgets.png") return true
        if (path == "assets/minecraft/textures/gui/icons.png" || path == "assets/minecraft/textures/gui/bars.png") return true

        if (path.contains("/mob_effect/") ||
            path.contains("/presets/") ||
            path.contains("/sprites/boss_bar/") ||
            path.contains("/sprites/icon/") ||
            path.contains("/sprites/hud/heart/") ||
            path.contains("/sprites/hud/food_") ||
            path.contains("/sprites/hud/armor_") ||
            path.contains("/sprites/hud/air") ||
            path.contains("/sprites/hud/crosshair") ||
            path.contains("/sprites/hud/experience_bar_progress") ||
            path.contains("/sprites/hud/jump_bar_progress") ||
            path.contains("/sprites/hud/locator_bar_progress")
        ) return true

        if (path.contains("/sprites/container/") && (
                path.contains("_progress") ||
                    path.contains("bubbles") ||
                    path.contains("fuel_length") ||
                    path.contains("dye_slot") ||
                    path.contains("armor_slot") ||
                    path.contains("smithing_template") ||
                    path.contains("banner_pattern") ||
                    path.contains("error")
                )
        ) return true

        return false
    }

    private fun darkenGuiBitmap(src: Bitmap, path: String): Bitmap? {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return null

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val isHighlighted = path.contains("_highlighted") ||
            path.contains("hotbar_selection") ||
            path.contains("tab_selected")
        val isSliderThumb = path.contains("slider_handle") || path.endsWith("/scroller.png")
        val isCheckboxSelected = path.contains("checkbox_selected")
        val isLegacyProgressContainer = path.endsWith("/furnace.png") ||
            path.endsWith("/blast_furnace.png") ||
            path.endsWith("/smoker.png") ||
            path.endsWith("/brewing_stand.png")
        val isBook = path.endsWith("/book.png")
        val isEarthOrMenuBg = path.endsWith("options_background.png") ||
            path.endsWith("demo_background.png") ||
            path.contains("menu_background") ||
            path.contains("menu_list_background") ||
            path.contains("advancements/backgrounds/")

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = Color.alpha(c)
            if (a == 0) continue

            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val sat = maxC - minC
            val luma = 0.299f * r + 0.587f * g + 0.114f * b

            val x = i % w
            val y = i / w

            if (isLegacyProgressContainer && x >= 176 && y < 60 && sat <= 26 && luma >= 240f) {
                pixels[i] = Color.argb(a, 230, 234, 242)
                continue
            }
            if (isCheckboxSelected && sat <= 26 && luma >= 245f && x in 3 until (w - 3) && y in 3 until (h - 3)) {
                pixels[i] = Color.argb(a, 242, 244, 248)
                continue
            }

            if (isBook) {
                // Keep black book ink readable while removing bright cream glare.
                if (luma > 135f) {
                    val scale = if (sat > 24) 0.52f else 0.48f
                    pixels[i] = Color.argb(
                        a,
                        (r * scale).roundToInt().coerceIn(22, 128),
                        (g * scale).roundToInt().coerceIn(24, 122),
                        (b * scale).roundToInt().coerceIn(28, 114)
                    )
                }
                continue
            }

            if (sat <= 26) {
                val (nr, ng, nb) = when {
                    luma >= 235f -> when {
                        isHighlighted -> Triple(220, 30, 58)
                        isSliderThumb -> Triple(82, 88, 104)
                        else -> Triple(56, 60, 71)
                    }
                    luma >= 175f -> {
                        val t = ((luma - 175f) / 60f).coerceIn(0f, 1f)
                        if (isSliderThumb) {
                            Triple((56 + t * 14).roundToInt(), (60 + t * 16).roundToInt(), (72 + t * 18).roundToInt())
                        } else if (isHighlighted) {
                            Triple((40 + t * 14).roundToInt(), (24 + t * 8).roundToInt(), (32 + t * 10).roundToInt())
                        } else {
                            Triple((28 + t * 10).roundToInt(), (30 + t * 11).roundToInt(), (37 + t * 13).roundToInt())
                        }
                    }
                    luma >= 115f -> {
                        val t = ((luma - 115f) / 60f).coerceIn(0f, 1f)
                        if (isSliderThumb) {
                            Triple((42 + t * 12).roundToInt(), (45 + t * 13).roundToInt(), (55 + t * 15).roundToInt())
                        } else {
                            Triple((18 + t * 8).roundToInt(), (20 + t * 8).roundToInt(), (25 + t * 10).roundToInt())
                        }
                    }
                    luma >= 55f -> {
                        val t = ((luma - 55f) / 60f).coerceIn(0f, 1f)
                        Triple((11 + t * 6).roundToInt(), (12 + t * 6).roundToInt(), (16 + t * 8).roundToInt())
                    }
                    else -> Triple(8, 9, 12)
                }
                pixels[i] = Color.argb(a, nr, ng, nb)
            } else if (isEarthOrMenuBg && r >= g && g >= b && (r - b) <= 80) {
                // Darken brown dirt / stone / menu backgrounds into obsidian dark slate.
                val darkLuma = (luma * 0.24f).roundToInt().coerceIn(12, 34)
                pixels[i] = Color.argb(a, darkLuma, darkLuma + 1, darkLuma + 5)
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun darkenContainerLangJson(
        zos: ZipOutputStream,
        zf: ZipFile,
        writtenPaths: MutableSet<String>
    ) {
        val langPath = "assets/minecraft/lang/en_us.json"
        if (writtenPaths.contains(langPath)) return
        val entry = zf.getEntry(langPath) ?: return
        runCatching {
            val rawJson = zf.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            val src = JSONObject(rawJson)
            val dst = JSONObject()
            val keys = src.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = src.optString(key, "")
                val isTitleKey = (key.startsWith("container.") &&
                    key != "container.isLocked" &&
                    key != "container.spectatorCantOpen") ||
                    key.startsWith("itemGroup.") ||
                    key.startsWith("merchant.") ||
                    key == "gui.advancements" ||
                    key == "inventory.binSlot" ||
                    key == "advancements.sad_label" ||
                    key == "advancements.empty"
                if (isTitleKey && value.isNotEmpty() && !value.startsWith("\u00a7")) {
                    dst.put(key, "\u00a77$value")
                } else {
                    dst.put(key, value)
                }
            }
            putText(zos, langPath, dst.toString(), writtenPaths)
        }
    }

    private fun appendDarkFallbackGuiAssets(
        zos: ZipOutputStream,
        packFmt: Int,
        writtenPaths: MutableSet<String>
    ) {
        val darkTile = buildDarkBackdropTile(16, 16, 0xFF13151B.toInt(), 0xFF181B22.toInt())
        try {
            putBitmap(zos, "assets/minecraft/textures/gui/options_background.png", darkTile, writtenPaths)
            if (packFmt >= 18) {
                putBitmap(zos, "assets/minecraft/textures/gui/menu_background.png", darkTile, writtenPaths)
                putBitmap(zos, "assets/minecraft/textures/gui/menu_list_background.png", darkTile, writtenPaths)
                putBitmap(zos, "assets/minecraft/textures/gui/tab_header_background.png", darkTile, writtenPaths)
            }
        } finally {
            darkTile.recycle()
        }

        if (packFmt < 18) return

        val inworldTile = buildDarkBackdropTile(16, 16, 0xC8101217.toInt(), 0xC814171E.toInt())
        try {
            putBitmap(zos, "assets/minecraft/textures/gui/inworld_menu_background.png", inworldTile, writtenPaths)
            putBitmap(zos, "assets/minecraft/textures/gui/inworld_menu_list_background.png", inworldTile, writtenPaths)
        } finally {
            inworldTile.recycle()
        }

        val sep = Bitmap.createBitmap(32, 2, Bitmap.Config.ARGB_8888)
        sep.setPixels(
            IntArray(64) { idx -> if (idx < 32) 0xFF0A0B0E.toInt() else 0xFF2D313C.toInt() },
            0, 32, 0, 0, 32, 2
        )
        try {
            putBitmap(zos, "assets/minecraft/textures/gui/header_separator.png", sep, writtenPaths)
            putBitmap(zos, "assets/minecraft/textures/gui/footer_separator.png", sep, writtenPaths)
            putBitmap(zos, "assets/minecraft/textures/gui/inworld_header_separator.png", sep, writtenPaths)
            putBitmap(zos, "assets/minecraft/textures/gui/inworld_footer_separator.png", sep, writtenPaths)
        } finally {
            sep.recycle()
        }

        val buttonMeta = """{"gui":{"scaling":{"type":"nine_slice","width":200,"height":20,"border":3}}}"""
        val buttons = listOf(
            "assets/minecraft/textures/gui/sprites/widget/button.png" to Pair(0xD2181A20.toInt(), 0xFF383C47.toInt()),
            "assets/minecraft/textures/gui/sprites/widget/button_disabled.png" to Pair(0x96121418.toInt(), 0xFF252830.toInt()),
            "assets/minecraft/textures/gui/sprites/widget/button_highlighted.png" to Pair(0xE634141C.toInt(), 0xFFDC1E3A.toInt())
        )
        for ((btnPath, colors) in buttons) {
            if (!writtenPaths.contains(btnPath)) {
                val bmp = Bitmap.createBitmap(200, 20, Bitmap.Config.ARGB_8888)
                drawCrimsonButton(Canvas(bmp), 0f, 0f, 200f, 20f, colors.first, colors.second)
                try {
                    putBitmap(zos, btnPath, bmp, writtenPaths)
                    putText(zos, "$btnPath.mcmeta", buttonMeta, writtenPaths)
                } finally {
                    bmp.recycle()
                }
            }
        }

        val hotbarPath = "assets/minecraft/textures/gui/sprites/hud/hotbar.png"
        if (!writtenPaths.contains(hotbarPath)) {
            val hb = Bitmap.createBitmap(182, 22, Bitmap.Config.ARGB_8888)
            val c = Canvas(hb)
            val fp = Paint().apply { color = Color.argb(185, 18, 20, 25) }
            val bp = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.argb(215, 220, 30, 58) }
            c.drawRect(0f, 0f, 182f, 22f, fp)
            c.drawRect(0.5f, 0.5f, 181.5f, 21.5f, bp)
            bp.color = Color.argb(90, 90, 96, 112)
            for (i in 1..8) c.drawLine(1f + i * 20f, 2f, 1f + i * 20f, 20f, bp)
            try {
                putBitmap(zos, hotbarPath, hb, writtenPaths)
                putText(zos, "$hotbarPath.mcmeta", """{"gui":{"scaling":{"type":"nine_slice","width":182,"height":22,"border":2}}}""", writtenPaths)
            } finally {
                hb.recycle()
            }
        }

        val hotbarSelPath = "assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png"
        if (!writtenPaths.contains(hotbarSelPath)) {
            val sel = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            val c = Canvas(sel)
            val fp = Paint().apply { color = Color.argb(75, 220, 30, 58) }
            val bp = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.argb(255, 255, 46, 76) }
            c.drawRect(1f, 1f, 23f, 23f, fp)
            c.drawRect(0.5f, 0.5f, 23.5f, 23.5f, bp)
            try {
                putBitmap(zos, hotbarSelPath, sel, writtenPaths)
            } finally {
                sel.recycle()
            }
        }
    }

    private fun buildDarkBackdropTile(w: Int, h: Int, primary: Int, accent: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val hash = (x * 73856093) xor (y * 19349663)
                pixels[y * w + x] = if ((hash and 3) == 0) accent else primary
            }
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun putBytes(
        zos: ZipOutputStream,
        path: String,
        bytes: ByteArray,
        writtenPaths: MutableSet<String>? = null
    ) {
        if (writtenPaths != null && !writtenPaths.add(path)) return
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun putText(
        zos: ZipOutputStream,
        path: String,
        content: String,
        writtenPaths: MutableSet<String>? = null
    ) {
        putBytes(zos, path, content.toByteArray(Charsets.UTF_8), writtenPaths)
    }

    private fun putBitmap(
        zos: ZipOutputStream,
        path: String,
        bmp: Bitmap,
        writtenPaths: MutableSet<String>? = null
    ) {
        if (writtenPaths != null && !writtenPaths.add(path)) return
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        zos.write(out.toByteArray())
        zos.closeEntry()
    }
}
