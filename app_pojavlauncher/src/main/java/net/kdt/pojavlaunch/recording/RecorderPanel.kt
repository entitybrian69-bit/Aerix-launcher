/*
 * Aerix Launcher - the redesigned in-game screen recorder panel.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package net.kdt.pojavlaunch.recording

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.kdt.pojavlaunch.aerix.bridge.AerixWindowInsets

private val PanelBg = Color(0xF0111319)
private val CardBg = Color(0xFF191D26)
private val ChipIdleBg = Color(0xFF1F2430)
private val Crimson = Color(0xFFE11D48)
private val CrimsonSoft = Color(0x33E11D48)
private val BorderIdle = Color(0x28FFFFFF)
private val TextPrimary = Color(0xFFF4F6FA)
private val TextMuted = Color(0xFF9BA3B4)

@Composable
fun RecorderPanel(
    configs: List<RecordingOptions.Config>,
    initialQuality: Int,
    initialFps: Int,
    initialAudio: RecordingOptions.AudioSource,
    initialWithControls: Boolean,
    onStart: (RecordingOptions.Config, RecordingOptions.AudioSource, Boolean) -> Unit,
    onStop: () -> Unit,
    onToggleControlsDuringRecording: (Boolean) -> Unit = {},
    onRecordings: () -> Unit,
    onDismiss: () -> Unit
) {
    val active by RecorderUiState.active
    val phase by RecorderUiState.phase
    val detail by RecorderUiState.detail
    val startedAtMs by RecorderUiState.startedAtElapsedMs

    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, phase, startedAtMs) {
        if (!active || phase != RecordingState.Phase.RECORDING || startedAtMs <= 0L) {
            elapsedSeconds = 0L
            return@LaunchedEffect
        }
        while (true) {
            elapsedSeconds = ((SystemClock.elapsedRealtime() - startedAtMs) / 1000L).coerceAtLeast(0L)
            delay(500L)
        }
    }

    val qualities = remember(configs) { configs.map { it.quality }.distinct().sorted() }
    val frameRates = remember(configs) { configs.map { it.fps }.distinct().sorted() }
    val audioOptions = remember { RecordingOptions.AudioSource.available() }
    val defaultPick = remember(configs, initialQuality, initialFps) {
        RecordingOptions.bestMatch(configs, initialQuality, initialFps)
    }

    var quality by remember(defaultPick) { mutableIntStateOf(defaultPick?.quality ?: initialQuality) }
    var fps by remember(defaultPick) { mutableIntStateOf(defaultPick?.fps ?: initialFps) }
    var audio by remember(initialAudio, audioOptions) {
        mutableStateOf(if (initialAudio in audioOptions) initialAudio else audioOptions.first())
    }
    var withControls by remember { mutableStateOf(initialWithControls) }

    val resolved = remember(configs, quality, fps) {
        configs.firstOrNull { it.quality == quality && it.fps == fps }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x9905070B))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(AerixWindowInsets.padding())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .heightIn(max = 460.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xF5151821), PanelBg))
                )
                .border(1.dp, BorderIdle, RoundedCornerShape(22.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Pinned Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (active) Crimson else Color(0xFF4ADE80))
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Screen Recorder",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (active) {
                            when (phase) {
                                RecordingState.Phase.STARTING -> "Starting hardware capture…"
                                RecordingState.Phase.STOPPING -> "Finalizing MP4…"
                                else -> "Recording · ${formatDuration(elapsedSeconds)} · $detail"
                            }
                        } else {
                            "Zero-copy hardware capture"
                        },
                        color = if (active) Color(0xFFFDA4AF) else TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ChipIdleBg,
                    border = BorderStroke(1.dp, BorderIdle),
                    onClick = onDismiss
                ) {
                    Text(
                        text = "Close",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }

            if (active) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardBg)
                            .border(1.dp, Crimson.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (phase == RecordingState.Phase.RECORDING) {
                                    "Recording in progress (${formatDuration(elapsedSeconds)})"
                                } else {
                                    detail.ifBlank { "Processing recording…" }
                                },
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Finished videos are saved to this instance's folder and published to Movies/Aerix.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    ControlsRow(
                        withControls = withControls,
                        onToggle = { next ->
                            withControls = next
                            onToggleControlsDuringRecording(next)
                        }
                    )
                }

                // Pinned bottom actions while recording
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionButton(
                        label = "Recordings",
                        primary = false,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = onRecordings
                    )
                    ActionButton(
                        label = if (phase == RecordingState.Phase.STOPPING) "Saving…" else "Stop & Save",
                        primary = true,
                        enabled = phase != RecordingState.Phase.STOPPING,
                        modifier = Modifier.weight(1.4f),
                        onClick = {
                            onStop()
                            onDismiss()
                        }
                    )
                }
            } else {
                // Scrollable configuration section (keeps bottom Start / Recordings buttons always reachable on landscape phones)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quality selector
                    PresetSection(title = "Resolution") {
                        qualities.forEach { q ->
                            OptionChip(
                                label = "${q}p",
                                selected = quality == q,
                                onClick = {
                                    quality = q
                                    if (configs.none { it.quality == q && it.fps == fps }) {
                                        configs.filter { it.quality == q }
                                            .minByOrNull { kotlin.math.abs(it.fps - fps) }
                                            ?.let { fps = it.fps }
                                    }
                                }
                            )
                        }
                    }

                    // Frame rate selector — auto-selects closest compatible resolution if current quality lacks that FPS
                    PresetSection(title = "Frame rate") {
                        frameRates.forEach { f ->
                            val exactMatch = configs.any { it.quality == quality && it.fps == f }
                            OptionChip(
                                label = "$f FPS",
                                selected = fps == f,
                                dimmed = !exactMatch,
                                onClick = {
                                    fps = f
                                    if (configs.none { it.quality == quality && it.fps == f }) {
                                        val candidates = configs.filter { it.fps == f }
                                        val pick = candidates.filter { it.quality <= quality }
                                            .maxByOrNull { it.quality }
                                            ?: candidates.minByOrNull { kotlin.math.abs(it.quality - quality) }
                                        pick?.let { quality = it.quality }
                                    }
                                }
                            )
                        }
                    }

                    // Audio source selector
                    PresetSection(title = "Audio") {
                        audioOptions.forEach { src ->
                            OptionChip(
                                label = src.label,
                                selected = audio == src,
                                onClick = { audio = src }
                            )
                        }
                    }

                    // Controls toggle
                    ControlsRow(
                        withControls = withControls,
                        onToggle = { withControls = it }
                    )

                    // Resolved encoder details
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardBg)
                            .border(1.dp, BorderIdle, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = resolved?.let {
                                val mode = if (it.hardwareAccelerated) "Hardware H.264" else "Compatibility H.264"
                                "Output: ${it.width}×${it.height} · ${it.fps} FPS · ${formatBitrate(it.bitrate)} · $mode"
                            } ?: "No compatible video encoder for ${quality}p at ${fps} FPS on this device.",
                            color = if (resolved != null) TextMuted else Color(0xFFFCA5A5),
                            fontSize = 12.sp
                        )
                    }
                }

                // Pinned bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionButton(
                        label = "Recordings",
                        primary = false,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = onRecordings
                    )
                    ActionButton(
                        label = "Start Recording",
                        primary = true,
                        enabled = resolved != null,
                        modifier = Modifier.weight(1.5f),
                        onClick = {
                            val chosen = resolved ?: return@ActionButton
                            onStart(chosen, audio, withControls)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    dimmed: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (selected) CrimsonSoft else ChipIdleBg
    val border = if (selected) Crimson else BorderIdle
    val textColor = when {
        selected -> TextPrimary
        dimmed -> TextMuted.copy(alpha = 0.55f)
        else -> TextMuted
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        onClick = onClick
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun ControlsRow(
    withControls: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, BorderIdle, RoundedCornerShape(12.dp))
            .clickable(role = Role.Switch) { onToggle(!withControls) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Record with controls",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (withControls) {
                    "Your touch controls appear in the video."
                } else {
                    "Touch controls fade out during capture while staying tappable."
                },
                color = TextMuted,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = withControls,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Crimson,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = ChipIdleBg
            )
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = when {
        !enabled -> ChipIdleBg.copy(alpha = 0.5f)
        primary -> Crimson
        else -> ChipIdleBg
    }
    val border = if (primary && enabled) Crimson else BorderIdle
    Surface(
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        enabled = enabled,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (enabled) TextPrimary else TextMuted.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60L
    val secs = seconds % 60L
    return "%02d:%02d".format(mins, secs)
}

private fun formatBitrate(bps: Long): String {
    val mbps = bps / 1_000_000.0
    return if (mbps >= 1.0) String.format(java.util.Locale.US, "%.1f Mbps", mbps)
    else "${bps / 1000L} kbps"
}
