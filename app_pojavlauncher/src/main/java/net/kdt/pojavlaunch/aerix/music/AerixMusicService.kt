package net.kdt.pojavlaunch.aerix.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.LauncherActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.discord.AerixDiscordPresence
import org.json.JSONArray
import org.json.JSONObject

class AerixMusicService : Service() {
    companion object {
        const val ACTION_PLAY_QUEUE = "net.kdt.pojavlaunch.aerix.music.PLAY_QUEUE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "net.kdt.pojavlaunch.aerix.music.TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT = "net.kdt.pojavlaunch.aerix.music.NEXT"
        const val ACTION_PREVIOUS = "net.kdt.pojavlaunch.aerix.music.PREVIOUS"
        const val ACTION_FAST_FORWARD = "net.kdt.pojavlaunch.aerix.music.FAST_FORWARD"
        const val ACTION_REWIND = "net.kdt.pojavlaunch.aerix.music.REWIND"
        const val ACTION_SEEK_TO = "net.kdt.pojavlaunch.aerix.music.SEEK_TO"
        const val ACTION_SET_VOLUME = "net.kdt.pojavlaunch.aerix.music.SET_VOLUME"
        const val ACTION_STOP = "net.kdt.pojavlaunch.aerix.music.STOP"
        const val ACTION_SYNC_STATE = "net.kdt.pojavlaunch.aerix.music.SYNC_STATE"

        const val ACTION_MUSIC_STATE_CHANGED = "net.kdt.pojavlaunch.aerix.ACTION_MUSIC_STATE_CHANGED"

        const val EXTRA_QUEUE_JSON = "extra_queue_json"
        const val EXTRA_START_INDEX = "extra_start_index"
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
        const val EXTRA_PLAYLIST_NAME = "extra_playlist_name"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_VOLUME = "extra_volume"
        const val EXTRA_STATE_JSON = "extra_state_json"

        private const val CHANNEL_ID = "aerix_music_playback"
        private const val NOTIF_ID = 94210
        private const val SEEK_STEP_MS = 10_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var prepareJob: Job? = null
    private var isPrepared = false

    private var queue: List<MusicTrack> = emptyList()
    private var queueIndex: Int = -1
    private var state: MusicPlaybackState = MusicPlaybackState()

    private val ticker = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (mp != null && isPrepared && state.isPlaying) {
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(state.positionMs)
                val dur = runCatching { mp.duration.toLong() }.getOrDefault(state.durationMs)
                    .let { if (it > 0L) it else (state.currentTrack?.durationSec?.times(1000L) ?: 0L) }
                state = state.copy(positionMs = pos.coerceAtLeast(0L), durationMs = dur.coerceAtLeast(0L))
                publishState(persist = false, updateNotification = false)
            }
            if (state.isPlaying) {
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        state = AerixMusicStorage.loadPlaybackState(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_PLAY_QUEUE) {
            promoteForeground("Preparing music…")
        }

        when (action) {
            ACTION_PLAY_QUEUE -> {
                val queueJson = intent.getStringExtra(EXTRA_QUEUE_JSON).orEmpty()
                val parsed = runCatching { MusicTrack.listFromJson(JSONArray(queueJson)) }.getOrDefault(emptyList())
                val startIdx = intent.getIntExtra(EXTRA_START_INDEX, 0)
                val plId = intent.getStringExtra(EXTRA_PLAYLIST_ID)?.takeIf { it.isNotBlank() }
                val plName = intent.getStringExtra(EXTRA_PLAYLIST_NAME)?.takeIf { it.isNotBlank() }
                if (parsed.isNotEmpty()) {
                    queue = parsed
                    queueIndex = startIdx.coerceIn(0, parsed.lastIndex)
                    state = state.copy(activePlaylistId = plId, activePlaylistName = plName, error = null)
                    startTrack(queue[queueIndex])
                } else {
                    stopForegroundSafely()
                }
            }
            ACTION_TOGGLE_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playOffset(1)
            ACTION_PREVIOUS -> playOffset(-1)
            ACTION_FAST_FORWARD -> seekBy(SEEK_STEP_MS)
            ACTION_REWIND -> seekBy(-SEEK_STEP_MS)
            ACTION_SEEK_TO -> {
                val pos = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
                seekAbsolute(pos)
            }
            ACTION_SET_VOLUME -> {
                val vol = intent.getFloatExtra(EXTRA_VOLUME, state.volume).coerceIn(0f, 1f)
                state = state.copy(volume = vol)
                runCatching { mediaPlayer?.setVolume(vol, vol) }
                publishState(persist = true, updateNotification = false)
            }
            ACTION_STOP -> stopPlayback()
            ACTION_SYNC_STATE -> publishState(persist = false, updateNotification = false)
        }
        return START_NOT_STICKY
    }

    private fun startTrack(track: MusicTrack) {
        prepareJob?.cancel()
        mainHandler.removeCallbacks(ticker)
        releasePlayer()
        isPrepared = false

        val expectedDurMs = track.durationSec * 1000L
        state = state.copy(
            currentTrack = track,
            isPlaying = false,
            isBuffering = true,
            positionMs = 0L,
            durationMs = expectedDurMs,
            error = null
        )
        promoteForeground("${track.title} · ${track.artist}")
        publishState(persist = true, updateNotification = true)

        prepareJob = scope.launch {
            val resolved = AerixAudiusClient.resolvePlayableUrl(track)
            if (resolved.isNullOrBlank()) {
                mainHandler.post {
                    state = state.copy(isBuffering = false, isPlaying = false, error = "Stream unavailable")
                    publishState(persist = true, updateNotification = true)
                }
                return@launch
            }
            mainHandler.post {
                if (state.currentTrack?.id != track.id) return@post
                prepareMediaPlayer(track, resolved)
            }
        }
    }

    private fun prepareMediaPlayer(track: MusicTrack, dataSource: String) {
        releasePlayer()
        val mp = MediaPlayer()
        mediaPlayer = mp
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        mp.setOnPreparedListener { player ->
            if (state.currentTrack?.id != track.id) return@setOnPreparedListener
            isPrepared = true
            val vol = state.volume.coerceIn(0f, 1f)
            player.setVolume(vol, vol)
            player.start()
            val actualDur = player.duration.toLong().takeIf { it > 0L } ?: (track.durationSec * 1000L)
            state = state.copy(
                isPlaying = true,
                isBuffering = false,
                durationMs = actualDur,
                positionMs = 0L,
                error = null
            )
            promoteForeground("${track.title} · ${track.artist}")
            publishState(persist = true, updateNotification = true)
            mainHandler.removeCallbacks(ticker)
            mainHandler.postDelayed(ticker, 1000L)
        }
        mp.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    state = state.copy(isBuffering = true)
                    publishState(persist = false, updateNotification = false)
                }
                MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    state = state.copy(isBuffering = false)
                    publishState(persist = false, updateNotification = false)
                }
            }
            false
        }
        mp.setOnCompletionListener {
            if (queueIndex in queue.indices && queueIndex + 1 < queue.size) {
                playOffset(1)
            } else {
                isPrepared = false
                mainHandler.removeCallbacks(ticker)
                state = state.copy(isPlaying = false, isBuffering = false, positionMs = state.durationMs)
                publishState(persist = true, updateNotification = true)
                stopForegroundSafely()
            }
        }
        mp.setOnErrorListener { _, what, extra ->
            isPrepared = false
            mainHandler.removeCallbacks(ticker)
            state = state.copy(
                isPlaying = false,
                isBuffering = false,
                error = "Playback error ($what/$extra)"
            )
            publishState(persist = true, updateNotification = true)
            stopForegroundSafely()
            true
        }

        try {
            mp.setDataSource(dataSource)
            mp.prepareAsync()
        } catch (err: Throwable) {
            isPrepared = false
            state = state.copy(
                isPlaying = false,
                isBuffering = false,
                error = err.message ?: "Could not open audio"
            )
            publishState(persist = true, updateNotification = true)
            stopForegroundSafely()
        }
    }

    private fun togglePlayPause() {
        val mp = mediaPlayer
        val track = state.currentTrack
        if (mp != null && isPrepared) {
            if (mp.isPlaying) {
                mp.pause()
                mainHandler.removeCallbacks(ticker)
                state = state.copy(isPlaying = false, isBuffering = false)
                publishState(persist = true, updateNotification = true)
                stopForegroundSafely()
            } else {
                mp.start()
                state = state.copy(isPlaying = true, isBuffering = false)
                track?.let { promoteForeground("${it.title} · ${it.artist}") }
                publishState(persist = true, updateNotification = true)
                mainHandler.removeCallbacks(ticker)
                mainHandler.postDelayed(ticker, 1000L)
            }
        } else if (track != null) {
            if (queue.isEmpty()) {
                queue = listOf(track)
                queueIndex = 0
            }
            startTrack(track)
        }
    }

    private fun playOffset(delta: Int) {
        if (queue.isEmpty()) return
        val next = (queueIndex + delta).let { idx ->
            when {
                idx < 0 -> queue.lastIndex
                idx >= queue.size -> 0
                else -> idx
            }
        }
        queueIndex = next
        startTrack(queue[queueIndex])
    }

    private fun seekBy(deltaMs: Long) {
        val mp = mediaPlayer
        if (mp == null || !isPrepared) return
        val cur = runCatching { mp.currentPosition.toLong() }.getOrDefault(state.positionMs)
        val dur = runCatching { mp.duration.toLong() }.getOrDefault(state.durationMs)
        val target = (cur + deltaMs).coerceIn(0L, dur.coerceAtLeast(0L))
        seekAbsolute(target)
    }

    private fun seekAbsolute(targetMs: Long) {
        val mp = mediaPlayer
        if (mp == null || !isPrepared) return
        val dur = runCatching { mp.duration.toLong() }.getOrDefault(state.durationMs)
        val clamped = targetMs.coerceIn(0L, dur.coerceAtLeast(0L))
        runCatching { mp.seekTo(clamped.toInt()) }
        state = state.copy(positionMs = clamped)
        publishState(persist = true, updateNotification = false)
    }

    private fun stopPlayback() {
        prepareJob?.cancel()
        mainHandler.removeCallbacks(ticker)
        releasePlayer()
        isPrepared = false
        state = state.copy(isPlaying = false, isBuffering = false, positionMs = 0L)
        publishState(persist = true, updateNotification = false)
        stopForegroundSafely()
        stopSelf()
    }

    private fun releasePlayer() {
        runCatching {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            }
        }
        mediaPlayer = null
    }

    private fun publishState(persist: Boolean, updateNotification: Boolean) {
        if (persist) {
            AerixMusicStorage.savePlaybackState(this, state)
        }
        val track = state.currentTrack
        if (updateNotification || persist) {
            AerixDiscordPresence.updateMusicState(this, track?.title, track?.artist, state.isPlaying)
        }
        val payload = JSONObject()
            .put("currentTrack", track?.toJson() ?: JSONObject.NULL)
            .put("activePlaylistId", state.activePlaylistId ?: "")
            .put("activePlaylistName", state.activePlaylistName ?: "")
            .put("isPlaying", state.isPlaying)
            .put("isBuffering", state.isBuffering)
            .put("positionMs", state.positionMs)
            .put("durationMs", state.durationMs)
            .put("volume", state.volume.toDouble())
            .put("vanillaMusicMuted", state.vanillaMusicMuted)
            .put("error", state.error ?: "")
            .toString()
        sendBroadcast(
            Intent(ACTION_MUSIC_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_STATE_JSON, payload)
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Aerix Music", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background music playback"
                setShowBadge(false)
            }
        )
    }

    private fun promoteForeground(subtitle: String) {
        ensureChannel()
        val openIntent = PendingIntent.getActivity(
            this,
            94211,
            Intent(this, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.aerix_ic_music_note)
            .setContentTitle("Aerix Music")
            .setContentText(subtitle)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .build()
        runCatching { startForeground(NOTIF_ID, notification) }
    }

    private fun stopForegroundSafely() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(ticker)
        prepareJob?.cancel()
        releasePlayer()
        AerixDiscordPresence.updateMusicState(this, null, null, false)
        scope.cancel()
        super.onDestroy()
    }
}
