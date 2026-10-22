/*
 * Aerix Launcher Social layer.
 *
 * Real microphone: a live input-level meter driven by AudioRecord, so the voice screen shows the
 * actual signal instead of a decorative animation. Nothing is recorded to disk.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object SocialMic {

    private val _level = MutableStateFlow(0f)

    /** 0f..1f input amplitude, normalised for the meter. */
    val level: StateFlow<Float> = _level.asStateFlow()

    private var job: Job? = null

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)
            val record = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer * 2
                )
            }.getOrNull() ?: return@launch

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return@launch
            }
            val buffer = ShortArray(minBuffer)
            runCatching { record.startRecording() }
            try {
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var peak = 0
                        for (index in 0 until read) {
                            val value = kotlin.math.abs(buffer[index].toInt())
                            if (value > peak) peak = value
                        }
                        _level.value = (peak / 32768f).coerceIn(0f, 1f)
                    }
                }
            } finally {
                runCatching { record.stop() }
                record.release()
                _level.value = 0f
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _level.value = 0f
    }
}
