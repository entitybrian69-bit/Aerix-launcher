package net.kdt.pojavlaunch.aerix.music

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import okhttp3.Request
import java.io.File

private val musicArtworkCache = object : LruCache<String, ImageBitmap>(12 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        (value.width * value.height * 4 / 1024).coerceAtLeast(1)
}

fun clearMusicArtworkCache() {
    musicArtworkCache.evictAll()
}

@Composable
fun rememberMusicArtwork(source: String?, targetPx: Int = 112): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = source?.let { musicArtworkCache.get(it) }, source) {
        if (source.isNullOrBlank() || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes: ByteArray = if (source.startsWith("http://") || source.startsWith("https://")) {
                    val req = Request.Builder().url(source).header("User-Agent", "AerixLauncher").build()
                    AerixAudiusClient.httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@runCatching null
                        resp.body?.bytes() ?: return@runCatching null
                    }
                } else {
                    val file = File(source)
                    if (!file.isFile || file.length() <= 0L || file.length() > 6_000_000L) return@runCatching null
                    file.readBytes()
                }
                if (bytes.isEmpty()) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= targetPx &&
                    bounds.outHeight / (sample * 2) >= targetPx
                ) {
                    sample *= 2
                }
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )?.asImageBitmap()?.also { musicArtworkCache.put(source, it) }
            }.getOrNull()
        }
    }
    return state.value
}

@Composable
fun MusicArtworkImage(
    artworkUrl: String?,
    size: Dp = 48.dp,
    radius: Dp = 10.dp,
    tint: Color = Color(0xFF8DEFE0)
) {
    val bmp = rememberMusicArtwork(artworkUrl, targetPx = 112)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(Color(0x28FFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_music_note),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}
