package net.kdt.pojavlaunch.aerix.client

import android.graphics.Bitmap

/**
 * Bakes the catalog's 64x64 garment patches onto a Minecraft player skin.
 *
 * Every Trinkies item ships a `garment.png` laid out exactly like a skin: each texel sits at the UV
 * coordinate it belongs to. Baking is therefore plain source-over compositing in UV space, which is
 * what lets one asset serve the launcher card, the skin handed to the game, and the mod at once.
 *
 * Patches are authored on the classic 64x64 grid and scaled by an integer factor for HD skins, so a
 * 512x512 skin gets 8x8 blocks of texels instead of a blurred resample.
 */
object TrinkiesGarmentComposite {
    /** The layout every patch is authored against, in UV units. */
    private const val PATCH_SIZE = 64

    /** The cape sheet's own grid, which is where back items are authored. */
    private const val CAPE_WIDTH = 64
    private const val CAPE_HEIGHT = 32

    /**
     * UV blocks of the classic skin as `u, v, width, height, depth`. These are the game's own
     * regions — the same table `tools/trinkies_assets/mc_skin_render.py` draws with, which is why a
     * generated patch and a baked skin line up without anyone hand-typing coordinates.
     */
    private val SKIN_BLOCKS: Map<String, IntArray> = mapOf(
        "head" to intArrayOf(0, 0, 8, 8, 8),
        "body" to intArrayOf(16, 16, 8, 12, 4),
        "right_arm" to intArrayOf(40, 16, 4, 12, 4),
        "right_leg" to intArrayOf(0, 16, 4, 12, 4),
        "left_arm" to intArrayOf(32, 48, 4, 12, 4),
        "left_leg" to intArrayOf(16, 48, 4, 12, 4),
        "hat" to intArrayOf(32, 0, 8, 8, 8),
        "jacket" to intArrayOf(16, 32, 8, 12, 4),
        "right_arm_hat" to intArrayOf(40, 32, 4, 12, 4),
        "right_leg_hat" to intArrayOf(0, 32, 4, 12, 4),
        "left_arm_hat" to intArrayOf(48, 48, 4, 12, 4),
        "left_leg_hat" to intArrayOf(0, 48, 4, 12, 4),
    )

    /**
     * Which blocks a category may touch, so a stray patch cannot repaint the whole player. A hat may
     * reach the jacket because hoods drape onto shoulders; a pet may reach the outer arms because it
     * rides there. Mirrors `CATEGORY_PATCH_PARTS` in the asset tooling.
     */
    private val CATEGORY_BLOCKS: Map<String, List<String>> = mapOf(
        "hat" to listOf("head", "hat", "jacket"),
        "suit" to listOf(
            "body", "jacket", "right_arm", "right_arm_hat", "left_arm", "left_arm_hat",
            "right_leg", "right_leg_hat", "left_leg", "left_leg_hat", "hat",
        ),
        "back" to listOf("jacket", "hat"),
        "pet" to listOf("head", "hat", "jacket", "right_arm_hat", "left_arm_hat"),
        "emote" to listOf("right_arm", "right_arm_hat", "left_arm", "left_arm_hat", "body", "jacket"),
        "aura" to listOf(
            "head", "hat", "body", "jacket", "right_arm", "right_arm_hat", "left_arm",
            "left_arm_hat", "right_leg", "right_leg_hat", "left_leg", "left_leg_hat",
        ),
    )

    /** The outer layers only exist on a 64x64 skin; a legacy 64x32 skin has no jacket to paint. */
    private val OVERLAY_BLOCKS: Set<String> =
        setOf("hat", "jacket", "right_arm_hat", "right_leg_hat", "left_arm_hat", "left_leg_hat")

    /**
     * Paint order: garments that replace fabric go first, then trims that should survive on top of
     * them, with headwear last so a hat still reads over a helm or an aura's hood trim.
     */
    val BAKE_ORDER: List<String> = listOf("suit", "back", "pet", "emote", "aura", "hat")

    /**
     * Composite [patches] over [skin] in place.
     *
     * @param skin the skin's pixels, row-major, length `width * height`
     * @param patches `(category, 64x64 RGBA pixels)` pairs, already sorted into [BAKE_ORDER]
     * @return true when at least one texel was painted
     */
    @JvmStatic
    fun bake(skin: IntArray, width: Int, height: Int, patches: List<Pair<String, IntArray>>): Boolean {
        if (width % PATCH_SIZE != 0 || height < PATCH_SIZE) return false
        val scale = width / PATCH_SIZE
        var painted = false
        for ((category, patch) in patches) {
            if (patch.size < PATCH_SIZE * PATCH_SIZE) continue
            val parts = categoryParts(category) ?: continue
            for (y in 0 until PATCH_SIZE) {
                for (x in 0 until PATCH_SIZE) {
                    val argb = patch[y * PATCH_SIZE + x]
                    val alpha = argb ushr 24
                    if (alpha == 0) continue
                    if (!parts.any { covers(it, x, y, width, height) }) continue
                    painted = paintBlock(skin, width, height, x * scale, y * scale, scale, argb, alpha) || painted
                }
            }
        }
        return painted
    }

    /** The same bake, applied to a mutable [Bitmap] decoded from the skin's PNG bytes. */
    fun bakeInto(bitmap: Bitmap, patches: List<Pair<String, IntArray>>): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        if (!bake(pixels, width, height, patches)) return false
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return true
    }

    /**
     * True when a patch is worth baking: it is on the 64x64 grid and paints at least one texel
     * inside the regions its category owns. A patch that only paints elsewhere is refused rather
     * than baked, so a malformed asset can never quietly repaint the face or the legs.
     */
    fun isUsableGarment(patch: IntArray, category: String): Boolean {
        if (patch.size < PATCH_SIZE * PATCH_SIZE) return false
        val parts = categoryParts(category) ?: return false
        for (y in 0 until PATCH_SIZE) {
            for (x in 0 until PATCH_SIZE) {
                if (patch[y * PATCH_SIZE + x] ushr 24 == 0) continue
                if (parts.any { covers(it, x, y, PATCH_SIZE, PATCH_SIZE) }) return true
            }
        }
        return false
    }

    /**
     * Source-over a back item's `cape_patch.png` onto a `64x32` cape sheet.
     *
     * A cape patch is authored in the cape's own UV space instead of the skin's, so there is no
     * footprint to police here: every texel of the sheet is fair game, which is what lets one asset
     * dress the elytra wings as well as the cape panel.
     *
     * @param cape the cape sheet's pixels, row-major, length `64 * 32`
     * @param patch the cape patch's pixels in the same layout
     * @return true when at least one texel was painted
     */
    @JvmStatic
    fun bakeCape(cape: IntArray, patch: IntArray): Boolean {
        val area = CAPE_WIDTH * CAPE_HEIGHT
        if (cape.size < area || patch.size < area) return false
        var painted = false
        for (y in 0 until CAPE_HEIGHT) {
            val row = y * CAPE_WIDTH
            for (x in 0 until CAPE_WIDTH) {
                val argb = patch[row + x]
                val alpha = argb ushr 24
                if (alpha == 0) continue
                painted = paintBlock(cape, CAPE_WIDTH, CAPE_HEIGHT, x, y, 1, argb, alpha) || painted
            }
        }
        return painted
    }

    /**
     * The same cape bake, applied to a mutable [Bitmap]. Sheets of any other size are refused
     * rather than stretched, because a stretched cape reads as a torn one.
     */
    fun bakeCapeInto(bitmap: Bitmap, patch: IntArray): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width != CAPE_WIDTH || height != CAPE_HEIGHT) return false
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        if (!bakeCape(pixels, patch)) return false
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return true
    }

    private fun categoryParts(category: String): List<String>? = CATEGORY_BLOCKS[category]

    /**
     * Whether a patch texel belongs to [part] on a skin of the given size. The footprint is the
     * union of the six face rectangles, unfolded exactly the way `PartBlock.face_rect` in
     * `mc_skin_render.py` does it: sharing that derivation is the whole reason a generated patch
     * and a baked skin agree without anyone re-typifying coordinates.
     */
    private fun covers(part: String, x: Int, y: Int, skinWidth: Int, skinHeight: Int): Boolean {
        val block = SKIN_BLOCKS[part] ?: return false
        val u = block[0]
        val v = block[1]
        val w = block[2]
        val h = block[3]
        val d = block[4]
        if (part in OVERLAY_BLOCKS && (u + 2 * w + 2 * d > skinWidth || v + h + d > skinHeight)) return false
        if (inside(x, y, u + d, v, w, d)) return true                        // top
        if (inside(x, y, u + d + w, v, w, d)) return true                     // bottom
        if (inside(x, y, u, v + d, d, h)) return true                        // right
        if (inside(x, y, u + d, v + d, w, h)) return true                    // front
        if (inside(x, y, u + d + w, v + d, d, h)) return true                // left
        return inside(x, y, u + d + w + d, v + d, w, h)                      // back
    }

    private fun inside(x: Int, y: Int, left: Int, top: Int, width: Int, height: Int): Boolean =
        x in left until (left + width) && y in top until (top + height)

    /**
     * Source-over a garment texel across its scaled footprint. Semi-transparent texels blend with
     * the skin underneath, which is how glints and aura veils stay readable on dark skins.
     */
    private fun paintBlock(
        skin: IntArray,
        skinWidth: Int,
        skinHeight: Int,
        targetX: Int,
        targetY: Int,
        scale: Int,
        argb: Int,
        alpha: Int
    ): Boolean {
        val sourceR = argb shr 16 and 0xFF
        val sourceG = argb shr 8 and 0xFF
        val sourceB = argb and 0xFF
        var wrote = false
        for (offsetY in 0 until scale) {
            val y = targetY + offsetY
            if (y < 0 || y >= skinHeight) continue
            for (offsetX in 0 until scale) {
                val x = targetX + offsetX
                if (x < 0 || x >= skinWidth) continue
                val index = y * skinWidth + x
                val target = skin[index]
                val targetAlpha = target ushr 24
                val blended = if (targetAlpha == 0 || alpha == 255) {
                    (0xFF shl 24) or (argb and 0x00FFFFFF)
                } else {
                    val inverse = 255 - alpha
                    val outAlpha = (alpha + targetAlpha * inverse / 255).coerceAtLeast(1)
                    val red = blendChannel(sourceR, target shr 16 and 0xFF, alpha, targetAlpha, inverse, outAlpha)
                    val green = blendChannel(sourceG, target shr 8 and 0xFF, alpha, targetAlpha, inverse, outAlpha)
                    val blue = blendChannel(sourceB, target and 0xFF, alpha, targetAlpha, inverse, outAlpha)
                    (outAlpha shl 24) or (red shl 16) or (green shl 8) or blue
                }
                if (blended != target) {
                    skin[index] = blended
                    wrote = true
                }
            }
        }
        return wrote
    }

    /** One channel of source-over compositing, in integer 0..255 space. */
    private fun blendChannel(
        source: Int,
        target: Int,
        alpha: Int,
        targetAlpha: Int,
        inverse: Int,
        outAlpha: Int
    ): Int = ((source * alpha + target * targetAlpha * inverse / 255) / outAlpha).coerceIn(0, 255)
}
