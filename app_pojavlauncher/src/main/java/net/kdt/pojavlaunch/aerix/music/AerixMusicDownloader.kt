package net.kdt.pojavlaunch.aerix.music

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import java.io.File
import java.io.IOException

object AerixMusicDownloader {
    const val ACTION_MUSIC_PLAYLISTS_CHANGED = "net.kdt.pojavlaunch.aerix.ACTION_MUSIC_PLAYLISTS_CHANGED"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(permits = 3)

    val items = mutableStateMapOf<String, MusicDownloadItem>()
    val order = mutableStateListOf<String>()

    fun activeCount(): Int =
        items.values.count { it.status == MusicDownloadStatus.QUEUED || it.status == MusicDownloadStatus.DOWNLOADING }

    fun allItems(): List<MusicDownloadItem> =
        order.mapNotNull { items[it] }

    fun clearCompleted() {
        val keep = order.filter { id ->
            val st = items[id]?.status
            st == MusicDownloadStatus.QUEUED || st == MusicDownloadStatus.DOWNLOADING
        }
        val toRemove = order.filterNot { it in keep }
        toRemove.forEach { items.remove(it) }
        order.clear()
        order.addAll(keep)
    }

    fun enqueue(context: Context, track: MusicTrack) {
        val app = context.applicationContext
        val existing = items[track.id]
        if (existing != null &&
            (existing.status == MusicDownloadStatus.QUEUED || existing.status == MusicDownloadStatus.DOWNLOADING)
        ) {
            return
        }
        if (track.id !in order) order.add(0, track.id)
        items[track.id] = MusicDownloadItem(track = track, status = MusicDownloadStatus.QUEUED, progress = 0f)

        scope.launch {
            semaphore.withPermit {
                downloadOne(app, track)
            }
        }
    }

    private fun downloadOne(context: Context, track: MusicTrack) {
        update(track.id) { it.copy(status = MusicDownloadStatus.DOWNLOADING, progress = 0.02f, error = null) }
        val musicsDir = AerixMusicStorage.musicsDir(context)
        val safeTitle = AerixMusicStorage.sanitizeFileName(track.title)
        val safeId = AerixMusicStorage.sanitizeId(track.id)
        val target = File(musicsDir, "$safeTitle-$safeId.mp3")
        val part = File(musicsDir, ".$safeId.part")

        try {
            val streamUrl = AerixAudiusClient.resolvePlayableUrl(track)
                ?: throw IOException("No stream URL available")
            if (!streamUrl.startsWith("http")) {
                throw IOException("Track is already local")
            }

            val req = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "AerixLauncher")
                .build()

            AerixAudiusClient.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty audio response")
                val total = body.contentLength().coerceAtLeast(0L)
                var copied = 0L
                part.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            copied += read
                            val frac = if (total > 0L) (copied.toFloat() / total.toFloat()).coerceIn(0f, 0.98f) else 0.5f
                            update(track.id) { cur ->
                                cur.copy(bytesDownloaded = copied, totalBytes = total, progress = frac)
                            }
                        }
                    }
                }
            }

            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }

            // Cache cover artwork for offline playback.
            var localCoverPath: String? = null
            track.artworkUrl?.takeIf { it.startsWith("http") }?.let { artUrl ->
                runCatching {
                    val coverFile = File(AerixMusicStorage.coversDir(context), "$safeId.jpg")
                    val artReq = Request.Builder().url(artUrl).header("User-Agent", "AerixLauncher").build()
                    AerixAudiusClient.httpClient.newCall(artReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.bytes()?.takeIf { it.isNotEmpty() && it.size <= 4_000_000 }?.let { bytes ->
                                coverFile.writeBytes(bytes)
                                localCoverPath = coverFile.absolutePath
                            }
                        }
                    }
                }
            }

            val savedTrack = track.copy(
                localPath = target.absolutePath,
                artworkUrl = localCoverPath ?: track.artworkUrl,
                source = MusicTrackSource.DOWNLOADED
            )
            AerixMusicStorage.addDownloadedTrack(context, savedTrack)
            update(track.id) { cur ->
                cur.copy(
                    track = savedTrack,
                    bytesDownloaded = target.length(),
                    totalBytes = target.length(),
                    progress = 1f,
                    status = MusicDownloadStatus.COMPLETED
                )
            }
            context.sendBroadcast(
                Intent(ACTION_MUSIC_PLAYLISTS_CHANGED).setPackage(context.packageName)
            )
        } catch (err: Throwable) {
            part.delete()
            update(track.id) { cur ->
                cur.copy(status = MusicDownloadStatus.FAILED, error = err.message ?: "Download failed")
            }
        }
    }

    private fun update(trackId: String, transform: (MusicDownloadItem) -> MusicDownloadItem) {
        val current = items[trackId] ?: return
        items[trackId] = transform(current)
    }
}
