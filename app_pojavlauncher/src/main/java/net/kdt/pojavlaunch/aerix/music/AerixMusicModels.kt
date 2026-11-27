package net.kdt.pojavlaunch.aerix.music

import org.json.JSONArray
import org.json.JSONObject

enum class MusicSortMode(val id: String, val label: String) {
    POPULAR("popular", "Popular"),
    RELEVANCE("relevance", "Relevance"),
    MOST_PLAYED("most_played", "Most Played"),
    LETTERS("letters", "Letters");

    companion object {
        fun fromId(id: String?): MusicSortMode =
            entries.firstOrNull { it.id == id } ?: POPULAR
    }
}

enum class MusicSectionTab(val label: String) {
    SONGS("Songs"),
    PLAYLISTS("Playlists"),
    DOWNLOADED("Downloaded"),
    EXPORTED("Exported Music's")
}

enum class MusicTrackSource {
    ONLINE,
    DOWNLOADED,
    EXPORTED;

    companion object {
        fun fromName(name: String?): MusicTrackSource =
            entries.firstOrNull { it.name == name } ?: ONLINE
    }
}

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val playCount: Long = 0L,
    val favoriteCount: Long = 0L,
    val artworkUrl: String? = null,
    val streamUrl: String? = null,
    val localPath: String? = null,
    val source: MusicTrackSource = MusicTrackSource.ONLINE
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("durationSec", durationSec)
        .put("playCount", playCount)
        .put("favoriteCount", favoriteCount)
        .put("artworkUrl", artworkUrl ?: "")
        .put("streamUrl", streamUrl ?: "")
        .put("localPath", localPath ?: "")
        .put("source", source.name)

    companion object {
        fun fromJson(obj: JSONObject): MusicTrack = MusicTrack(
            id = obj.optString("id"),
            title = obj.optString("title", "Unknown track"),
            artist = obj.optString("artist", "Unknown artist"),
            durationSec = obj.optInt("durationSec", 0).coerceAtLeast(0),
            playCount = obj.optLong("playCount", 0L).coerceAtLeast(0L),
            favoriteCount = obj.optLong("favoriteCount", 0L).coerceAtLeast(0L),
            artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
            streamUrl = obj.optString("streamUrl").takeIf { it.isNotBlank() },
            localPath = obj.optString("localPath").takeIf { it.isNotBlank() },
            source = MusicTrackSource.fromName(obj.optString("source"))
        )

        fun listToJson(tracks: List<MusicTrack>): JSONArray =
            JSONArray().apply { tracks.forEach { put(it.toJson()) } }

        fun listFromJson(arr: JSONArray?): List<MusicTrack> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val track = fromJson(obj)
                    if (track.id.isNotBlank()) add(track)
                }
            }
        }
    }
}

data class MusicPlaylist(
    val id: String,
    val name: String,
    val tracks: List<MusicTrack>,
    val isBuiltIn: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("tracks", MusicTrack.listToJson(tracks))
        .put("isBuiltIn", isBuiltIn)

    companion object {
        const val ID_DOWNLOADED = "__downloaded__"
        const val ID_EXPORTED = "__exported__"

        fun fromJson(obj: JSONObject): MusicPlaylist = MusicPlaylist(
            id = obj.optString("id"),
            name = obj.optString("name", "Playlist"),
            tracks = MusicTrack.listFromJson(obj.optJSONArray("tracks")),
            isBuiltIn = obj.optBoolean("isBuiltIn", false)
        )
    }
}

enum class MusicDownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class MusicDownloadItem(
    val track: MusicTrack,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Float = 0f,
    val status: MusicDownloadStatus = MusicDownloadStatus.QUEUED,
    val error: String? = null
)

data class MusicSearchPage(
    val tracks: List<MusicTrack> = emptyList(),
    val page: Int = 1,
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false,
    val totalCount: Int = 0
)

data class MusicPlaybackState(
    val currentTrack: MusicTrack? = null,
    val activePlaylistId: String? = null,
    val activePlaylistName: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 0.85f,
    val vanillaMusicMuted: Boolean = false,
    val error: String? = null
)

fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hrs = safe / 3600
    val mins = (safe % 3600) / 60
    val secs = safe % 60
    return if (hrs > 0) "%d:%02d:%02d".format(hrs, mins, secs)
    else "%d:%02d".format(mins, secs)
}

fun formatDurationMs(ms: Long): String =
    formatDuration((ms.coerceAtLeast(0L) / 1000L).toInt())
