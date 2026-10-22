/*
 * Aerix Launcher Social layer.
 *
 * One MediaPlayer for the whole Social surface: a shared track keeps playing while you keep
 * typing, switch screens or open a party, and stops when you leave.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.media

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object SocialAudioPlayer {

    private var player: MediaPlayer? = null

    private val _playingId = MutableStateFlow<String?>(null)

    /** The id of the chat message whose track is playing, or null. */
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    private val _position = MutableStateFlow(0f)

    /** 0f..1f progress of the current track. */
    val position: StateFlow<Float> = _position.asStateFlow()

    /** Plays a real file; a track already playing under the same id is paused instead. */
    fun toggle(messageId: String, path: String?) {
        if (_playingId.value == messageId) {
            pause()
            return
        }
        stop()
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) return
        runCatching {
            val media = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { stop() }
                start()
            }
            player = media
            _playingId.value = messageId
            tick()
        }.onFailure { stop() }
    }

    fun pause() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
        _playingId.value = null
    }

    fun stop() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        _playingId.value = null
        _position.value = 0f
    }

    private fun tick() {
        val media = player ?: return
        runCatching {
            val total = media.duration.coerceAtLeast(1)
            _position.value = (media.currentPosition.toFloat() / total).coerceIn(0f, 1f)
        }
        if (_playingId.value != null) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tick() }, 500)
        }
    }
}
