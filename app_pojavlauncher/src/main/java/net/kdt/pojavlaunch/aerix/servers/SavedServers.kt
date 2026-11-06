package net.kdt.pojavlaunch.aerix.servers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SavedServer(val id: String, val name: String, val address: String, val banner: Boolean = false)

object SavedServers {
    private const val PREFS = "aerix_saved_servers"
    private const val MAX_SERVERS = 100
    fun load(context: Context): List<SavedServer> = runCatching {
        val json = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("servers", "[]"))
        (0 until minOf(json.length(), MAX_SERVERS)).mapNotNull { i ->
            val o = json.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id"); val name = o.optString("name"); val address = o.optString("address")
            if (!id.matches(Regex("[0-9a-f-]{36}")) || name.isBlank() || ServerAddress.parse(address) == null) null
            else SavedServer(id, name.take(80), address, o.optBoolean("banner"))
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())
    fun save(context: Context, entries: List<SavedServer>) {
        require(entries.size <= MAX_SERVERS)
        val array = JSONArray()
        entries.forEach { item -> array.put(JSONObject().put("id", item.id).put("name", item.name).put("address", item.address).put("banner", item.banner)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("servers", array.toString()).apply()
    }
    fun newId(): String = UUID.randomUUID().toString()
    fun bannerFile(context: Context, id: String): File {
        require(id.matches(Regex("[0-9a-f-]{36}")))
        return File(File(context.filesDir, "server_banners"), "$id.jpg")
    }
    fun clearBanner(context: Context, id: String) { bannerFile(context, id).delete() }
    fun importBanner(context: Context, id: String, uri: Uri) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = java.io.ByteArrayOutputStream(); val chunk = ByteArray(8192); var total = 0
            while (true) {
                val count = stream.read(chunk); if (count < 0) break
                total += count; require(total <= 8 * 1024 * 1024) { "Image is larger than 8 MB" }
                out.write(chunk, 0, count)
            }
            out.toByteArray()
        } ?: error("Could not open image")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192) { "Unsupported image dimensions" }
        var sample = 1
        while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 1_500_000L) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Could not decode image")
        val file = bannerFile(context, id)
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true) { "Cannot save banner" }
            temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it)) { "Could not save banner" } }
            check(temp.renameTo(file)) { "Could not save banner" }
        } finally { bitmap.recycle(); temp.delete() }
    }
}
