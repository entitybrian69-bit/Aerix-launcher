package net.kdt.pojavlaunch.aerix.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import okhttp3.Request
import java.io.File

private val musicArtworkCache = object : LruCache<String, ImageBitmap>(16 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        (value.width * value.height * 2 / 1024).coerceAtLeast(1)
}

// Cap concurrent network + decode work so fast list scrolling never stalls game frames.
private val artworkDecodePermits = Semaphore(4)

fun clearMusicArtworkCache() {
    musicArtworkCache.evictAll()
}

private fun decodeScaledArtwork(source: String, targetPx: Int, cacheKey: String): ImageBitmap? {
    return runCatching {
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
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?.asImageBitmap()
            ?.also { musicArtworkCache.put(cacheKey, it) }
    }.getOrNull()
}

@Composable
fun rememberMusicArtwork(source: String?, targetPx: Int = 112): ImageBitmap? {
    val cacheKey = if (source.isNullOrBlank()) null else "$source#$targetPx"
    var bitmap by remember(cacheKey) {
        mutableStateOf(cacheKey?.let { musicArtworkCache.get(it) })
    }
    LaunchedEffect(cacheKey) {
        if (source.isNullOrBlank() || cacheKey == null) {
            bitmap = null
            return@LaunchedEffect
        }
        val cached = musicArtworkCache.get(cacheKey)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            artworkDecodePermits.withPermit {
                musicArtworkCache.get(cacheKey) ?: decodeScaledArtwork(source, targetPx, cacheKey)
            }
        }
        bitmap = loaded
    }
    return bitmap
}

@Composable
fun MusicArtworkImage(
    artworkUrl: String?,
    size: Dp = 48.dp,
    radius: Dp = 10.dp,
    tint: Color = Color(0xFF8DEFE0),
    targetPx: Int = 112
) {
    val bmp = rememberMusicArtwork(artworkUrl, targetPx = targetPx)
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
                modifier = Modifier.size(size * 0.48f)
            )
        }
    }
}

@Composable
fun MusicSkipPreviousIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.14f
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.16f, h * 0.2f),
            size = Size(barW, h * 0.6f),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
        )
        val path = Path().apply {
            moveTo(w * 0.84f, h * 0.2f)
            lineTo(w * 0.84f, h * 0.8f)
            lineTo(w * 0.34f, h * 0.5f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun MusicSkipNextIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.14f
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.70f, h * 0.2f),
            size = Size(barW, h * 0.6f),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
        )
        val path = Path().apply {
            moveTo(w * 0.16f, h * 0.2f)
            lineTo(w * 0.16f, h * 0.8f)
            lineTo(w * 0.66f, h * 0.5f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun MusicSpeakerIcon(muted: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val speaker = Path().apply {
            moveTo(w * 0.14f, h * 0.38f)
            lineTo(w * 0.34f, h * 0.38f)
            lineTo(w * 0.56f, h * 0.18f)
            lineTo(w * 0.56f, h * 0.82f)
            lineTo(w * 0.34f, h * 0.62f)
            lineTo(w * 0.14f, h * 0.62f)
            close()
        }
        drawPath(speaker, color = tint)
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        if (muted) {
            drawLine(tint, Offset(w * 0.68f, h * 0.34f), Offset(w * 0.90f, h * 0.66f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.90f, h * 0.34f), Offset(w * 0.68f, h * 0.66f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
        } else {
            drawArc(
                color = tint,
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(w * 0.52f, h * 0.28f),
                size = Size(w * 0.26f, h * 0.44f),
                style = stroke
            )
            drawArc(
                color = tint,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(w * 0.56f, h * 0.14f),
                size = Size(w * 0.36f, h * 0.72f),
                style = stroke
            )
        }
    }
}
