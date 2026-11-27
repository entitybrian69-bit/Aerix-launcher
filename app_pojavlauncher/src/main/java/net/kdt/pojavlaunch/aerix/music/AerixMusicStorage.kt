package net.kdt.pojavlaunch.aerix.music

import android.content.Context
import android.media.MediaMetadataRetriever
import net.kdt.pojavlaunch.Tools
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object AerixMusicStorage {
    private val lock = Any()
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "flac", "wav", "opus")
    private const val PLAYLISTS_FILE = "playlists.json"
    private const val DOWNLOADED_FILE = "downloaded.json"
    private const val PLAYBACK_FILE = "playback_state.json"

    fun musicsDir(context: Context): File {
        val app = context.applicationContext
        Tools.ensureStorageConstants(app)
        val root = Tools.DIR_GAME_HOME
            ?: app.getExternalFilesDir(null)?.absolutePath
            ?: app.filesDir.absolutePath
        return File(root, "musics").apply { if (!exists()) mkdirs() }
    }

    fun metaDir(context: Context): File =
        File(context.applicationContext.filesDir, "aerix_music").apply { if (!exists()) mkdirs() }

    fun coversDir(context: Context): File =
        File(metaDir(context), "covers").apply { if (!exists()) mkdirs() }

    private fun playlistsFile(context: Context): File = File(metaDir(context), PLAYLISTS_FILE)
    private fun downloadedFile(context: Context): File = File(metaDir(context), DOWNLOADED_FILE)
    private fun playbackFile(context: Context): File = File(metaDir(context), PLAYBACK_FILE)

    private fun writeAtomic(target: File, text: String) {
        synchronized(lock) {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    private fun readJsonArraySafe(file: File): JSONArray? = synchronized(lock) {
        if (!file.isFile) return null
        runCatching { JSONArray(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun readJsonObjectSafe(file: File): JSONObject? = synchronized(lock) {
        if (!file.isFile) return null
        runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    // ── Downloaded tracks ────────────────────────────────────────────────────────────────────────

    fun loadDownloadedTracks(context: Context): List<MusicTrack> {
        val arr = readJsonArraySafe(downloadedFile(context)) ?: return emptyList()
        val covers = coversDir(context)
        val valid = ArrayList<MusicTrack>(arr.length())
        var changed = false
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val track = MusicTrack.fromJson(obj)
            val file = track.localPath?.let { File(it) }
            if (file != null && file.isFile && file.length() > 0L) {
                val localCover = File(covers, "${sanitizeId(track.id)}.jpg")
                val resolvedArt = if (localCover.isFile && localCover.length() > 0L) {
                    localCover.absolutePath
                } else {
                    track.artworkUrl
                }
                valid += track.copy(source = MusicTrackSource.DOWNLOADED, artworkUrl = resolvedArt)
            } else {
                changed = true
            }
        }
        if (changed) {
            saveDownloadedTracks(context, valid)
        }
        return valid
    }

    fun saveDownloadedTracks(context: Context, tracks: List<MusicTrack>) {
        writeAtomic(downloadedFile(context), MusicTrack.listToJson(tracks).toString())
    }

    fun addDownloadedTrack(context: Context, track: MusicTrack) {
        synchronized(lock) {
            val existing = loadDownloadedTracks(context).filterNot { it.id == track.id }
            val updated = listOf(track.copy(source = MusicTrackSource.DOWNLOADED)) + existing
            saveDownloadedTracks(context, updated)
        }
    }

    fun deleteDownloadedTrack(context: Context, trackId: String) {
        synchronized(lock) {
            val current = loadDownloadedTracks(context)
            val removed = current.firstOrNull { it.id == trackId }
            removed?.localPath?.let { runCatching { File(it).delete() } }
            saveDownloadedTracks(context, current.filterNot { it.id == trackId })
        }
    }

    // ── Exported Music's (external files placed in musics/) ──────────────────────────────────────

    fun scanExportedTracks(context: Context): List<MusicTrack> {
        val dir = musicsDir(context)
        val downloadedPaths = loadDownloadedTracks(context)
            .mapNotNull { it.localPath?.let { p -> canonicalOrAbs(File(p)) } }
            .toSet()

        val files = collectAudioFiles(dir)
            .filter { file -> canonicalOrAbs(file) !in downloadedPaths }
            .sortedBy { it.name.lowercase() }

        val covers = coversDir(context)
        return files.map { file ->
            val key = sha1("${file.absolutePath}|${file.length()}|${file.lastModified()}").take(16)
            val trackId = "exported_$key"
            val coverFile = File(covers, "$trackId.jpg")
            var title = file.nameWithoutExtension.replace('_', ' ').trim().ifBlank { file.name }
            var artist = "Exported Music"
            var durationSec = 0

            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.trim()?.takeIf { it.isNotEmpty() }?.let { title = it }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.trim()?.takeIf { it.isNotEmpty() }?.let { artist = it }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.let { ms -> durationSec = (ms / 1000L).toInt().coerceAtLeast(0) }
                    if (!coverFile.isFile) {
                        val art = retriever.embeddedPicture
                        if (art != null && art.isNotEmpty() && art.size <= 4_000_000) {
                            coverFile.writeBytes(art)
                        }
                    }
                } finally {
                    retriever.release()
                }
            }

            MusicTrack(
                id = trackId,
                title = title,
                artist = artist,
                durationSec = durationSec,
                artworkUrl = coverFile.takeIf { it.isFile && it.length() > 0L }?.absolutePath,
                localPath = file.absolutePath,
                source = MusicTrackSource.EXPORTED
            )
        }
    }

    private fun collectAudioFiles(dir: File): List<File> {
        val result = ArrayList<File>()
        val children = dir.listFiles() ?: return result
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                result.addAll(collectAudioFiles(child))
            } else if (child.isFile && child.extension.lowercase() in AUDIO_EXTENSIONS && child.length() > 0L) {
                result.add(child)
            }
        }
        return result
    }

    // ── Custom playlists ─────────────────────────────────────────────────────────────────────────

    fun loadCustomPlaylists(context: Context): List<MusicPlaylist> {
        val arr = readJsonArraySafe(playlistsFile(context)) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val pl = MusicPlaylist.fromJson(obj)
                if (pl.id.isNotBlank() && !pl.isBuiltIn) add(pl)
            }
        }
    }

    fun saveCustomPlaylists(context: Context, playlists: List<MusicPlaylist>) {
        val arr = JSONArray().apply {
            playlists.filterNot { it.isBuiltIn }.forEach { put(it.toJson()) }
        }
        writeAtomic(playlistsFile(context), arr.toString())
    }

    fun createPlaylist(context: Context, rawName: String, initialTrack: MusicTrack? = null): MusicPlaylist {
        val clean = rawName.trim().ifBlank { "My Playlist" }.take(60)
        synchronized(lock) {
            val current = loadCustomPlaylists(context).toMutableList()
            val playlist = MusicPlaylist(
                id = "pl_${UUID.randomUUID()}",
                name = clean,
                tracks = if (initialTrack != null) listOf(initialTrack) else emptyList(),
                isBuiltIn = false
            )
            current.add(0, playlist)
            saveCustomPlaylists(context, current)
            return playlist
        }
    }

    fun addTrackToPlaylist(context: Context, playlistId: String, track: MusicTrack) {
        synchronized(lock) {
            val current = loadCustomPlaylists(context).map { pl ->
                if (pl.id != playlistId) pl
                else if (pl.tracks.any { it.id == track.id }) pl
                else pl.copy(tracks = pl.tracks + track)
            }
            saveCustomPlaylists(context, current)
        }
    }

    fun removeTrackFromPlaylist(context: Context, playlistId: String, trackId: String) {
        synchronized(lock) {
            val current = loadCustomPlaylists(context).map { pl ->
                if (pl.id != playlistId) pl
                else pl.copy(tracks = pl.tracks.filterNot { it.id == trackId })
            }
            saveCustomPlaylists(context, current)
        }
    }

    fun deletePlaylist(context: Context, playlistId: String) {
        synchronized(lock) {
            saveCustomPlaylists(context, loadCustomPlaylists(context).filterNot { it.id == playlistId })
        }
    }

    fun loadAllPlaylists(context: Context): List<MusicPlaylist> {
        val downloaded = MusicPlaylist(
            id = MusicPlaylist.ID_DOWNLOADED,
            name = "Downloaded",
            tracks = loadDownloadedTracks(context),
            isBuiltIn = true
        )
        val exported = MusicPlaylist(
            id = MusicPlaylist.ID_EXPORTED,
            name = "Exported Music's",
            tracks = scanExportedTracks(context),
            isBuiltIn = true
        )
        return listOf(downloaded, exported) + loadCustomPlaylists(context)
    }

    // ── Cross-process playback snapshot ──────────────────────────────────────────────────────────

    fun savePlaybackState(context: Context, state: MusicPlaybackState) {
        val obj = JSONObject()
            .put("currentTrack", state.currentTrack?.toJson() ?: JSONObject.NULL)
            .put("activePlaylistId", state.activePlaylistId ?: "")
            .put("activePlaylistName", state.activePlaylistName ?: "")
            .put("isPlaying", state.isPlaying)
            .put("isBuffering", state.isBuffering)
            .put("positionMs", state.positionMs)
            .put("durationMs", state.durationMs)
            .put("volume", state.volume.toDouble())
            .put("vanillaMusicMuted", state.vanillaMusicMuted)
            .put("error", state.error ?: "")
        writeAtomic(playbackFile(context), obj.toString())
    }

    fun loadPlaybackState(context: Context): MusicPlaybackState {
        val obj = readJsonObjectSafe(playbackFile(context)) ?: return MusicPlaybackState()
        val trackObj = obj.optJSONObject("currentTrack")
        return MusicPlaybackState(
            currentTrack = trackObj?.let { MusicTrack.fromJson(it) }?.takeIf { it.id.isNotBlank() },
            activePlaylistId = obj.optString("activePlaylistId").takeIf { it.isNotBlank() },
            activePlaylistName = obj.optString("activePlaylistName").takeIf { it.isNotBlank() },
            isPlaying = obj.optBoolean("isPlaying", false),
            isBuffering = obj.optBoolean("isBuffering", false),
            positionMs = obj.optLong("positionMs", 0L).coerceAtLeast(0L),
            durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
            volume = obj.optDouble("volume", 0.85).toFloat().coerceIn(0f, 1f),
            vanillaMusicMuted = obj.optBoolean("vanillaMusicMuted", false),
            error = obj.optString("error").takeIf { it.isNotBlank() }
        )
    }

    fun sanitizeId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48).ifBlank { "track" }

    fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim().take(48).ifBlank { "track" }

    private fun canonicalOrAbs(file: File): String =
        runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
