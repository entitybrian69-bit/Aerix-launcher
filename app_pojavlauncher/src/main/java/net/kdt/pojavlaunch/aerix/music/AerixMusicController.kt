package net.kdt.pojavlaunch.aerix.music

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.aerix.client.ClientPerformanceSettings
import net.kdt.pojavlaunch.aerix.discord.AerixDiscordPresence
import net.kdt.pojavlaunch.utils.MCOptionUtils
import org.json.JSONObject

object AerixMusicController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var receiverRegistered = false
    @Volatile
    private var initialized = false
    private var searchJob: Job? = null
    private var libraryJob: Job? = null

    // Persistent search + tab state (never wiped when switching middle section buttons).
    var selectedTab by mutableStateOf(MusicSectionTab.SONGS)
    var searchQuery by mutableStateOf("")
    var submittedQuery by mutableStateOf("")
        private set
    var sortMode by mutableStateOf(MusicSortMode.POPULAR)
        private set
    var currentPage by mutableIntStateOf(1)
        private set
    var searchPage by mutableStateOf(MusicSearchPage())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set

    // Library + playlists
    var playlists by mutableStateOf<List<MusicPlaylist>>(emptyList())
        private set
    var downloadedTracks by mutableStateOf<List<MusicTrack>>(emptyList())
        private set
    var exportedTracks by mutableStateOf<List<MusicTrack>>(emptyList())
        private set
    var openedPlaylistId by mutableStateOf<String?>(null)

    // Playback state synced with AerixMusicService; position/duration tick separately so
    // per-second timer updates never recompose track lists or artwork.
    var playbackState by mutableStateOf(MusicPlaybackState())
        private set
    var playbackPositionMs by mutableLongStateOf(0L)
        private set
    var playbackDurationMs by mutableLongStateOf(0L)
        private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AerixMusicService.ACTION_MUSIC_POSITION_TICK -> {
                    playbackPositionMs = intent.getLongExtra(AerixMusicService.EXTRA_POSITION_MS, playbackPositionMs)
                    val dur = intent.getLongExtra(AerixMusicService.EXTRA_DURATION_MS, playbackDurationMs)
                    if (dur > 0L && dur != playbackDurationMs) {
                        playbackDurationMs = dur
                    }
                }
                AerixMusicService.ACTION_MUSIC_STATE_CHANGED -> {
                    val raw = intent.getStringExtra(AerixMusicService.EXTRA_STATE_JSON)
                    val parsed = raw?.let { parsePlaybackJson(it) } ?: AerixMusicStorage.loadPlaybackState(context)
                    playbackPositionMs = parsed.positionMs
                    playbackDurationMs = parsed.durationMs
                    val merged = parsed.copy(
                        positionMs = 0L,
                        durationMs = 0L,
                        vanillaMusicMuted = playbackState.vanillaMusicMuted
                    )
                    if (playbackState != merged) {
                        playbackState = merged
                    }
                    AerixDiscordPresence.updateMusicState(
                        context,
                        merged.currentTrack?.title,
                        merged.currentTrack?.artist,
                        merged.isPlaying
                    )
                }
                AerixMusicDownloader.ACTION_MUSIC_PLAYLISTS_CHANGED -> {
                    refreshLibrary(context)
                }
            }
        }
    }

    fun attach(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!receiverRegistered) {
            receiverRegistered = true
            val filter = IntentFilter().apply {
                addAction(AerixMusicService.ACTION_MUSIC_STATE_CHANGED)
                addAction(AerixMusicService.ACTION_MUSIC_POSITION_TICK)
                addAction(AerixMusicDownloader.ACTION_MUSIC_PLAYLISTS_CHANGED)
            }
            registerInternalReceiver(app, receiver, filter)
        }
        if (!initialized) {
            initialized = true
            scope.launch {
                val (persisted, vanillaMuted) = withContext(Dispatchers.IO) {
                    val p = AerixMusicStorage.loadPlaybackState(app)
                    val muted = runCatching {
                        ClientPerformanceSettings.init(app)
                        ClientPerformanceSettings.overrides["soundCategory_music"] == "0.0" ||
                            MCOptionUtils.get("soundCategory_music") == "0.0"
                    }.getOrDefault(p.vanillaMusicMuted)
                    p to muted
                }
                playbackPositionMs = persisted.positionMs
                playbackDurationMs = persisted.durationMs
                playbackState = persisted.copy(
                    positionMs = 0L,
                    durationMs = 0L,
                    vanillaMusicMuted = vanillaMuted
                )
                refreshLibrary(app)
                runCatching {
                    app.startService(
                        Intent(app, AerixMusicService::class.java).setAction(AerixMusicService.ACTION_SYNC_STATE)
                    )
                }
            }
        }
        if (searchPage.tracks.isEmpty() && !isSearching && searchError == null) {
            loadSearchPage(page = 1)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerInternalReceiver(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun refreshLibrary(context: Context? = appContext) {
        val app = context?.applicationContext ?: return
        libraryJob?.cancel()
        libraryJob = scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                AerixMusicStorage.musicsDir(app)
                AerixMusicStorage.loadAllPlaylists(app)
            }
            playlists = loaded
            downloadedTracks = loaded.firstOrNull { it.id == MusicPlaylist.ID_DOWNLOADED }?.tracks.orEmpty()
            exportedTracks = loaded.firstOrNull { it.id == MusicPlaylist.ID_EXPORTED }?.tracks.orEmpty()
        }
    }

    fun submitSearch(queryOverride: String? = null) {
        if (queryOverride != null) {
            searchQuery = queryOverride
        }
        submittedQuery = searchQuery.trim()
        loadSearchPage(page = 1)
    }

    fun selectSort(mode: MusicSortMode) {
        if (sortMode == mode && searchPage.tracks.isNotEmpty()) return
        sortMode = mode
        loadSearchPage(page = 1)
    }

    fun nextPage() {
        if (searchPage.hasNext && !isSearching) {
            loadSearchPage(page = currentPage + 1)
        }
    }

    fun previousPage() {
        if (searchPage.hasPrev && !isSearching && currentPage > 1) {
            loadSearchPage(page = currentPage - 1)
        }
    }

    fun loadSearchPage(page: Int = currentPage) {
        val safePage = page.coerceAtLeast(1)
        currentPage = safePage
        searchJob?.cancel()
        isSearching = true
        searchError = null
        val q = submittedQuery
        val sort = sortMode
        searchJob = scope.launch {
            val result = AerixAudiusClient.search(q, sort, safePage)
            result.onSuccess { pageData ->
                searchPage = pageData
                currentPage = pageData.page
                searchError = null
            }.onFailure { err ->
                searchError = err.message ?: "Could not load songs"
            }
            isSearching = false
        }
    }

    fun playTrack(context: Context, track: MusicTrack, queue: List<MusicTrack> = listOf(track), playlist: MusicPlaylist? = null) {
        val app = context.applicationContext
        val idx = queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
        val intent = Intent(app, AerixMusicService::class.java)
            .setAction(AerixMusicService.ACTION_PLAY_QUEUE)
            .putExtra(AerixMusicService.EXTRA_QUEUE_JSON, MusicTrack.listToJson(queue).toString())
            .putExtra(AerixMusicService.EXTRA_START_INDEX, idx)
            .putExtra(AerixMusicService.EXTRA_PLAYLIST_ID, playlist?.id ?: "")
            .putExtra(AerixMusicService.EXTRA_PLAYLIST_NAME, playlist?.name ?: "")
        ContextCompat.startForegroundService(app, intent)
    }

    fun playPlaylist(context: Context, playlist: MusicPlaylist, startIndex: Int = 0) {
        if (playlist.tracks.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, playlist.tracks.lastIndex)
        playTrack(context, playlist.tracks[safeIdx], playlist.tracks, playlist)
    }

    fun togglePlayPause(context: Context) {
        sendCommand(context, AerixMusicService.ACTION_TOGGLE_PLAY_PAUSE)
    }

    fun nextTrack(context: Context) {
        sendCommand(context, AerixMusicService.ACTION_NEXT)
    }

    fun previousTrack(context: Context) {
        sendCommand(context, AerixMusicService.ACTION_PREVIOUS)
    }

    fun fastForward(context: Context) {
        sendCommand(context, AerixMusicService.ACTION_FAST_FORWARD)
    }

    fun rewind(context: Context) {
        sendCommand(context, AerixMusicService.ACTION_REWIND)
    }

    fun seekTo(context: Context, positionMs: Long) {
        val app = context.applicationContext
        app.startService(
            Intent(app, AerixMusicService::class.java)
                .setAction(AerixMusicService.ACTION_SEEK_TO)
                .putExtra(AerixMusicService.EXTRA_POSITION_MS, positionMs.coerceAtLeast(0L))
        )
    }

    fun setVolume(context: Context, volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        playbackState = playbackState.copy(volume = clamped)
        val app = context.applicationContext
        val snapshot = playbackState.copy(positionMs = playbackPositionMs, durationMs = playbackDurationMs)
        scope.launch(Dispatchers.IO) {
            AerixMusicStorage.savePlaybackState(app, snapshot)
        }
        app.startService(
            Intent(app, AerixMusicService::class.java)
                .setAction(AerixMusicService.ACTION_SET_VOLUME)
                .putExtra(AerixMusicService.EXTRA_VOLUME, clamped)
        )
    }

    fun setVanillaMusicMuted(context: Context, muted: Boolean) {
        val app = context.applicationContext
        val value = if (muted) "0.0" else "1.0"
        playbackState = playbackState.copy(vanillaMusicMuted = muted)
        val snapshot = playbackState.copy(positionMs = playbackPositionMs, durationMs = playbackDurationMs)
        scope.launch(Dispatchers.IO) {
            AerixMusicStorage.savePlaybackState(app, snapshot)
            runCatching {
                ClientPerformanceSettings.init(app)
                ClientPerformanceSettings.set("soundCategory_music", value)
            }
            runCatching {
                MCOptionUtils.set("soundCategory_music", value)
                MCOptionUtils.save()
            }
        }
    }

    fun createPlaylistAndAdd(context: Context, name: String, track: MusicTrack?): MusicPlaylist {
        val app = context.applicationContext
        val created = AerixMusicStorage.createPlaylist(app, name, track)
        refreshLibrary(app)
        app.sendBroadcast(Intent(AerixMusicDownloader.ACTION_MUSIC_PLAYLISTS_CHANGED).setPackage(app.packageName))
        return created
    }

    fun addTrackToPlaylist(context: Context, playlistId: String, track: MusicTrack) {
        val app = context.applicationContext
        AerixMusicStorage.addTrackToPlaylist(app, playlistId, track)
        refreshLibrary(app)
        app.sendBroadcast(Intent(AerixMusicDownloader.ACTION_MUSIC_PLAYLISTS_CHANGED).setPackage(app.packageName))
    }

    fun removeTrackFromPlaylist(context: Context, playlistId: String, trackId: String) {
        val app = context.applicationContext
        AerixMusicStorage.removeTrackFromPlaylist(app, playlistId, trackId)
        refreshLibrary(app)
        app.sendBroadcast(Intent(AerixMusicDownloader.ACTION_MUSIC_PLAYLISTS_CHANGED).setPackage(app.packageName))
    }

    fun deletePlaylist(context: Context, playlistId: String) {
        val app = context.applicationContext
        AerixMusicStorage.deletePlaylist(app, playlistId)
        if (openedPlaylistId == playlistId) openedPlaylistId = null
        refreshLibrary(app)
        app.sendBroadcast(Intent(AerixMusicDownloader.ACTION_MUSIC_PLAYLISTS_CHANGED).setPackage(app.packageName))
    }

    fun downloadTrack(context: Context, track: MusicTrack) {
        AerixMusicDownloader.enqueue(context.applicationContext, track)
    }

    fun isTrackDownloaded(trackId: String): Boolean =
        downloadedTracks.any { it.id == trackId }

    private fun sendCommand(context: Context, action: String) {
        val app = context.applicationContext
        runCatching {
            app.startService(Intent(app, AerixMusicService::class.java).setAction(action))
        }
    }

    private fun parsePlaybackJson(raw: String): MusicPlaybackState? = runCatching {
        val obj = JSONObject(raw)
        val trackObj = obj.optJSONObject("currentTrack")
        MusicPlaybackState(
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
    }.getOrNull()
}
