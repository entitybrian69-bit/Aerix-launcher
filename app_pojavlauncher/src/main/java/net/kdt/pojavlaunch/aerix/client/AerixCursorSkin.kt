/*
 * Aerix Launcher - the cursor skin the player picks in Settings -> Mouse settings.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * The pointer image the launcher draws, with its size and opacity.
 *
 * Pojav draws the in-game cursor itself ([net.kdt.pojavlaunch.game.GameCursorView]): the white arrow
 * is a launcher drawable, not the system pointer, which is why a "cursor" setting has to be applied
 * here to be visible at all. Presets ship inside the APK under `assets/cursors/` (a manifest lists
 * them, so adding one is a file drop, not a code change) and a player can pick any image from their
 * gallery, which is copied into the app's own storage and downscaled - decoding a 4000 px photo
 * every frame would cost more than the frame it is drawing.
 *
 * Values are Compose state, so a slider in the settings page repaints the preview and the game
 * window picks them up at the next cursor pass without a restart.
 */
object AerixCursorSkin {
    const val KEY_SKIN = "cursorSkin"
    const val KEY_OPACITY = "cursorOpacityPercent"
    const val KEY_ANCHOR = "cursorAnchorCenter"
    const val NONE = ""
    private const val ASSET_DIR = "cursors"
    private const val MAX_SIDE = 256

    /** `""` for the plain arrow, `asset:<file>` for a bundled preset, `file:<path>` for a photo. */
    var skin by mutableStateOf(NONE)
        private set

    /** 20..100 percent; a cursor that is fully invisible is a cursor you cannot find. */
    var opacityPercent by mutableIntStateOf(100)
        private set

    /**
     * Whether the image is centred on the pointer (items, crosshairs) or has its top-left corner
     * there (an arrow, whose point is its tip). Chosen in the preview, because the same image reads
     * as broken with the wrong one.
     */
    var anchoredCenter by mutableStateOf(true)
        private set

    /** The drawn size as a percent of the built-in arrow, kept as state so the slider label follows. */
    var scalePercent by mutableIntStateOf(100)
        private set

    @Volatile private var decoded: Bitmap? = null
    @Volatile private var decodedFor: String? = null
    private var initialised = false

    /** Read the stored choice; safe to call more than once and from any thread. */
    @JvmStatic
    fun init(context: Context) {
        val prefs = LauncherPreferences.DEFAULT_PREF ?: return
        skin = prefs.getString(KEY_SKIN, NONE) ?: NONE
        opacityPercent = (prefs.getInt(KEY_OPACITY, 100)).coerceIn(20, 100)
        anchoredCenter = prefs.getBoolean(KEY_ANCHOR, true)
        scalePercent = LauncherPreferences.PREF_MOUSESCALE.let { (it * 100).toInt() }
        initialised = true
    }

    private fun ensure(context: Context) {
        if (!initialised) init(context)
    }

    /** One entry of the picker. [label] is what the player reads, [id] is what is stored. */
    data class Option(val id: String, val label: String, val source: String)

    /** Bundled presets, in manifest order, with the plain arrow first. */
    fun presets(context: Context): List<Option> {
        val list = ArrayList<Option>()
        list += Option(NONE, "Default arrow", "")
        runCatching {
            val manifest = JSONArray(context.assets.open("$ASSET_DIR/manifest.json").reader().readText())
            for (i in 0 until manifest.length()) {
                val entry = manifest.getJSONObject(i)
                list += Option("asset:" + entry.getString("file"), entry.getString("name"), ASSET_DIR + "/" + entry.getString("file"))
            }
        }
        list += customEntries(context)
        return list
    }

    /** Images imported from the gallery, newest first. */
    private fun customEntries(context: Context): List<Option> {
        val dir = customDirectory(context)
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".png") } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }
            .map { Option("file:${it.absolutePath}", it.nameWithoutExtension.replace('-', ' '), it.absolutePath) }
    }

    private fun customDirectory(context: Context) = File(context.filesDir, "aerix_cursors").apply { mkdirs() }

    /**
     * The bitmap to draw, or null for the built-in arrow. Decoded once per choice and cached, since
     * the cursor view asks for this on every frame it paints.
     */
    @Synchronized
    fun bitmapFor(context: Context, id: String): Bitmap? {
        ensure(context)
        if (id.isEmpty()) return null
        if (id == decodedFor) return decoded
        val bitmap = runCatching {
            when {
                id.startsWith("asset:") -> context.assets.open("$ASSET_DIR/${id.removePrefix("asset:")}")
                    .use { decodeScaled(it) }
                id.startsWith("file:") -> File(id.removePrefix("file:")).inputStream().use { decodeScaled(it) }
                else -> null
            }
        }.getOrNull()
        decoded?.recycle()
        decoded = bitmap
        decodedFor = id
        return bitmap
    }

    fun currentBitmap(context: Context): Bitmap? {
        ensure(context)
        return bitmapFor(context, skin)
    }

    private fun decodeScaled(stream: java.io.InputStream): Bitmap? {
        val bytes = stream.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= MAX_SIDE) return decoded
        val scale = MAX_SIDE.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1), true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /** Store the choice. [opacity] is clamped here so every caller can pass a slider value. */
    @JvmOverloads
    fun select(context: Context, id: String, opacity: Int = opacityPercent, center: Boolean = anchoredCenter) {
        ensure(context)
        val prefs = LauncherPreferences.DEFAULT_PREF ?: return
        val opacityClamped = opacity.coerceIn(20, 100)
        skin = id
        opacityPercent = opacityClamped
        anchoredCenter = center
        bitmapFor(context, id)
        prefs.edit().putString(KEY_SKIN, id).putInt(KEY_OPACITY, opacityClamped)
            .putBoolean(KEY_ANCHOR, center).apply()
    }

    /** The size the cursor is drawn at, as the game view and the preview both compute it. */
    fun drawBox(heightPx: Int, scalePercent: Int): Pair<Int, Int> {
        val height = (heightPx * scalePercent / 100f).toInt().coerceAtLeast(6)
        return height to height
    }

    fun setOpacity(context: Context, percent: Int) = select(context, skin, percent)

    fun setScale(context: Context, percent: Int) {
        val prefs = LauncherPreferences.DEFAULT_PREF ?: return
        val clamped = percent.coerceIn(25, 300)
        prefs.edit().putInt("mousescale", clamped).apply()
        LauncherPreferences.PREF_MOUSESCALE = clamped / 100f
        scalePercent = clamped
    }

    /**
     * Copy an image the player picked in the system picker into app storage as a cursor.
     *
     * @return a one-line report of what happened, which the settings row shows next to the picker so
     * an import that failed for a reason the player cannot see (a raw HEIF, a 0-byte file, a URI the
     * provider revoked) never looks like a successful change.
     */
    fun importImage(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val type = runCatching { resolver.getType(uri) }.getOrNull()
        if (type != null && !type.startsWith("image/")) return "That file is not an image ($type)."
        val target = File(customDirectory(context), "custom-${System.currentTimeMillis()}.png")
        val bitmap = runCatching {
            resolver.openInputStream(uri)?.use { decodeScaled(it) }
        }.getOrNull() ?: run { target.delete(); return "That image could not be decoded. Try a PNG or JPG." }
        val wrote = runCatching {
            FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            target.length() > 0
        }.getOrDefault(false)
        if (!wrote) { target.delete(); bitmap.recycle(); return "The image could not be saved into the launcher's storage." }
        select(context, "file:${target.absolutePath}")
        return "Using ${target.name} · ${bitmap.width}×${bitmap.height} px"
    }

    /** Remove a gallery import. Bundled presets are never removable. */
    fun forget(context: Context, id: String) {
        if (!id.startsWith("file:")) return
        runCatching { File(id.removePrefix("file:")).delete() }
        if (skin == id) select(context, NONE)
    }

    fun openInGallery(context: Context, id: String) {
        if (!id.startsWith("file:")) return
        val file = File(id.removePrefix("file:"))
        if (!file.isFile) return
        val intent = Intent(Intent.ACTION_VIEW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(androidx.core.content.FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file), "image/*")
        } else intent.setDataAndType(Uri.fromFile(file), "image/*")
        runCatching { context.startActivity(intent) }
    }

    /** What the player's cursor is right now, for the settings row and the launch log. */
    fun describe(context: Context): String {
        ensure(context)
        val name = when {
            skin.isEmpty() -> "the default arrow"
            skin.startsWith("asset:") -> presets(context).firstOrNull { it.id == skin }?.label
                ?: skin.substringAfterLast('/').removePrefix("aerix_cursor_").removeSuffix(".png")
            else -> File(skin.removePrefix("file:")).nameWithoutExtension
        }
        val bitmap = currentBitmap(context)
        return if (bitmap == null) "Default arrow · ${opacityPercent}% opacity"
        else "$name · ${bitmap.width}×${bitmap.height} px · ${opacityPercent}% opacity"
    }

    /** Alpha for the paint the cursor view draws with, in the 0..255 range Canvas expects. */
    @JvmStatic
    fun alphaByte(): Int = (opacityPercent.coerceIn(20, 100) * 255 / 100)

    /** Manifest sanity: a shipped APK whose preset list is empty would silently offer nothing. */
    fun report(context: Context): String {
        val count = runCatching {
            JSONArray(context.assets.open("$ASSET_DIR/manifest.json").reader().readText()).length()
        }.getOrDefault(-1)
        return "cursor presets in APK: ${if (count < 0) "MISSING manifest" else "$count"} · selected: " +
            describe(context)
    }

    /** Used by the preview screen: draw order and geometry are decided by the caller. */
    fun scaleForHeight(bitmap: Bitmap?, boxHeightPx: Float, scalePercent: Float): Float {
        if (bitmap == null) return 1f
        val height = boxHeightPx.coerceAtLeast(1f) / bitmap.height.coerceAtLeast(1)
        return height * (scalePercent / 100f)
    }

    /** Keep the aspect while fitting a preview box. */
    fun fitIn(width: Int, height: Int, box: Int): Pair<Int, Int> {
        val longest = maxOf(width, height).coerceAtLeast(1)
        val factor = box.toFloat() / longest
        return (width * factor).toInt().coerceAtLeast(1) to (height * factor).toInt().coerceAtLeast(1)
    }
}
