package net.kdt.pojavlaunch.aerix.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object AerixAudiusClient {
    private const val APP_NAME = "AerixLauncher"
    private const val DISCOVERY_BOOTSTRAP = "https://api.audius.co"
    private const val HOST_CACHE_TTL_MS = 10 * 60_000L
    const val PAGE_SIZE = 15

    private val FALLBACK_HOSTS = listOf(
        "https://discoveryprovider.audius.co",
        "https://discoveryprovider2.audius.co",
        "https://discoveryprovider3.audius.co",
        "https://audius-discovery-1.cultur3stake.com",
        "https://audius-discovery-2.theblueprint.xyz"
    )

    internal val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val redirectProbeClient: OkHttpClient = httpClient.newBuilder()
        .followRedirects(false)
        .build()

    @Volatile
    private var cachedHost: String? = null
    @Volatile
    private var cachedHostAtMs: Long = 0L
    @Volatile
    private var cachedHostsPool: List<String> = emptyList()

    private fun candidateHosts(): List<String> {
        val now = System.currentTimeMillis()
        val current = cachedHost
        if (current != null && now - cachedHostAtMs < HOST_CACHE_TTL_MS && cachedHostsPool.isNotEmpty()) {
            return listOf(current) + cachedHostsPool.filterNot { it == current }
        }
        val fetched = runCatching {
            val req = Request.Builder()
                .url(DISCOVERY_BOOTSTRAP)
                .header("User-Agent", APP_NAME)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<String>()
                val body = resp.body?.string().orEmpty()
                val arr = JSONObject(body).optJSONArray("data") ?: return@use emptyList<String>()
                buildList {
                    for (i in 0 until arr.length()) {
                        val h = arr.optString(i).trim().trimEnd('/')
                        if (h.startsWith("https://")) add(h)
                    }
                }
            }
        }.getOrDefault(emptyList())
        val merged = (fetched + FALLBACK_HOSTS).distinct()
        cachedHostsPool = merged
        return merged
    }

    suspend fun search(
        query: String,
        sortMode: MusicSortMode,
        page: Int,
        pageSize: Int = PAGE_SIZE
    ): Result<MusicSearchPage> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val safePage = page.coerceAtLeast(1)
        val hosts = candidateHosts()
        var lastError: Throwable? = null

        for (host in hosts.take(5)) {
            val attempt = runCatching {
                val url = buildQueryUrl(host, cleanQuery, sortMode)
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", APP_NAME)
                    .header("Accept", "application/json")
                    .build()
                val jsonText = httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Audius HTTP ${resp.code}")
                    resp.body?.string() ?: throw IOException("Empty Audius response")
                }
                val rawTracks = parseTracks(JSONObject(jsonText).optJSONArray("data"), host)
                val sorted = sortTracks(rawTracks, sortMode)
                val from = ((safePage - 1) * pageSize).coerceAtLeast(0)
                val slice = if (from < sorted.size) {
                    sorted.subList(from, minOf(from + pageSize, sorted.size)).toList()
                } else {
                    emptyList()
                }
                cachedHost = host
                cachedHostAtMs = System.currentTimeMillis()
                MusicSearchPage(
                    tracks = slice,
                    page = safePage,
                    hasPrev = safePage > 1,
                    hasNext = from + pageSize < sorted.size,
                    totalCount = sorted.size
                )
            }
            if (attempt.isSuccess) return@withContext attempt
            lastError = attempt.exceptionOrNull()
            if (cachedHost == host) cachedHost = null
        }

        Result.failure(lastError ?: IOException("Could not reach Audius music servers"))
    }

    private fun buildQueryUrl(host: String, query: String, sortMode: MusicSortMode): String {
        val encodedApp = URLEncoder.encode(APP_NAME, "UTF-8")
        if (query.isBlank()) {
            val timeWindow = when (sortMode) {
                MusicSortMode.MOST_PLAYED -> "allTime"
                MusicSortMode.RELEVANCE -> "month"
                else -> "week"
            }
            return "$host/v1/tracks/trending?time=$timeWindow&limit=60&app_name=$encodedApp"
        }
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val sortParam = when (sortMode) {
            MusicSortMode.POPULAR -> "&sort_method=popular"
            MusicSortMode.RELEVANCE -> "&sort_method=relevant"
            else -> ""
        }
        return "$host/v1/tracks/search?query=$encodedQuery$sortParam&limit=60&app_name=$encodedApp"
    }

    private fun parseTracks(arr: JSONArray?, host: String): List<MusicTrack> {
        if (arr == null) return emptyList()
        val seen = HashSet<String>()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                if (id.isBlank() || !seen.add(id)) continue
                if (obj.optBoolean("is_streamable", true).not()) continue
                val title = obj.optString("title").trim().ifBlank { "Untitled" }
                val user = obj.optJSONObject("user")
                val artist = user?.optString("name")?.trim().orEmpty().ifBlank { "Unknown artist" }
                val durationSec = obj.optInt("duration", 0).coerceAtLeast(0)
                val playCount = obj.optLong("play_count", 0L).coerceAtLeast(0L)
                val favoriteCount = obj.optLong("favorite_count", 0L).coerceAtLeast(0L)

                val artworkObj = obj.optJSONObject("artwork")
                val profileObj = user?.optJSONObject("profile_picture")
                val artworkUrl = pickArtwork(artworkObj) ?: pickArtwork(profileObj)

                add(
                    MusicTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        durationSec = durationSec,
                        playCount = playCount,
                        favoriteCount = favoriteCount,
                        artworkUrl = artworkUrl,
                        streamUrl = "$host/v1/tracks/$id/stream?app_name=$APP_NAME",
                        source = MusicTrackSource.ONLINE
                    )
                )
            }
        }
    }

    private fun pickArtwork(obj: JSONObject?): String? {
        if (obj == null) return null
        for (key in listOf("480x480", "150x150", "1000x1000")) {
            val url = obj.optString(key).trim()
            if (url.startsWith("https://") || url.startsWith("http://")) return url
        }
        return null
    }

    private fun sortTracks(tracks: List<MusicTrack>, sortMode: MusicSortMode): List<MusicTrack> = when (sortMode) {
        MusicSortMode.RELEVANCE -> tracks
        MusicSortMode.POPULAR -> tracks.sortedByDescending { it.favoriteCount * 3L + it.playCount }
        MusicSortMode.MOST_PLAYED -> tracks.sortedByDescending { it.playCount }
        MusicSortMode.LETTERS -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    /**
     * Resolves the direct CDN stream URL so Android MediaPlayer starts playback immediately
     * without hanging on multi-hop discovery redirects.
     */
    fun resolvePlayableUrl(track: MusicTrack): String? {
        val local = track.localPath?.let { java.io.File(it) }
        if (local != null && local.isFile && local.length() > 0L) {
            return local.absolutePath
        }
        val raw = track.streamUrl?.takeIf { it.isNotBlank() }
            ?: cachedHost?.let { "$it/v1/tracks/${track.id}/stream?app_name=$APP_NAME" }
            ?: "${FALLBACK_HOSTS.first()}/v1/tracks/${track.id}/stream?app_name=$APP_NAME"

        val candidates = buildList {
            add(raw)
            val fallbackHosts = (listOfNotNull(cachedHost) + FALLBACK_HOSTS).distinct()
            fallbackHosts.forEach { h ->
                val url = "$h/v1/tracks/${track.id}/stream?app_name=$APP_NAME"
                if (url != raw) add(url)
            }
        }

        for (candidate in candidates.take(4)) {
            val resolved = runCatching {
                val req = Request.Builder()
                    .url(candidate)
                    .header("User-Agent", APP_NAME)
                    .get()
                    .build()
                redirectProbeClient.newCall(req).execute().use { resp ->
                    val location = resp.header("Location")
                    if (!location.isNullOrBlank() && location.startsWith("http")) {
                        location
                    } else if (resp.isSuccessful) {
                        candidate
                    } else {
                        null
                    }
                }
            }.getOrNull()
            if (!resolved.isNullOrBlank()) return resolved
        }
        return raw
    }
}
