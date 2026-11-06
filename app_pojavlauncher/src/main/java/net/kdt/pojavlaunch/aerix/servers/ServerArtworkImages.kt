package net.kdt.pojavlaunch.aerix.servers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Bounded, in-memory image cache; only URLs from the pinned, local catalog are requested. */
object ServerArtworkImages {
    private val requests = Semaphore(3)
    private val cache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    suspend fun load(artwork: ServerArtworkCatalog.Artwork): Bitmap? {
        cache.get(artwork.url)?.let { return it }
        return requests.withPermit {
            cache.get(artwork.url)?.let { return@withPermit it }
            runCatching {
                val connection = URL(artwork.url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("User-Agent", "Aerix-Launcher/servers")
                    if (connection.responseCode != 200) return@runCatching null
                    if (connection.contentLengthLong > 2 * 1024 * 1024) return@runCatching null
                    val bytes = connection.inputStream.use { input ->
                        val out = ByteArrayOutputStream(); val chunk = ByteArray(8192); var total = 0
                        while (true) {
                            val n = input.read(chunk); if (n < 0) break
                            total += n; if (total > 2 * 1024 * 1024) return@runCatching null
                            out.write(chunk, 0, n)
                        }
                        out.toByteArray()
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outMimeType != "image/png" || bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return@runCatching null
                    var sample = 1
                    while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 1_000_000) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.also {
                        cache.put(artwork.url, it)
                    }
                } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
}
