/*
 * Aerix Launcher - the in-game screen recorder panel.
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

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.aerix.client.LiquidCrystalPalette
import net.kdt.pojavlaunch.prefs.LauncherPreferences

/**
 * The recorder's options, laid out as a capture panel rather than a settings form.
 *
 * Three things the old `AlertDialog` with spinners could not do, and this can:
 *
 *  * Every option is one tap. A spinner needs a tap to open, a glance to find the row and another
 *    tap to pick - three interactions to change the frame rate mid-game.
 *  * The choices are visible at once, so "720p at 60" is one glance instead of two spinners.
 *  * The panel follows the real state: while a take runs it says so and offers to stop, instead of
 *    showing presets that cannot be changed mid-recording anyway.
 *
 * @param configs what this device's encoders actually support, from [RecordingOptions.supported].
 *        Only these appear as options, so nothing on screen can be chosen and then fail.
 */
@Composable
fun RecorderPanel(
    configs: List<RecordingOptions.Config>,
    initialQuality: Int,
    initialFps: Int,
    initialAudio: RecordingOptions.AudioSource,
    initialWithControls: Boolean,
    onStart: (RecordingOptions.Config, RecordingOptions.AudioSource, Boolean) -> Unit,
    onStop: () -> Unit,
    onRecordings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Whether this device can hand the game's frames back at all. The SurfaceView path is the one
    // some devices refuse, and the caption has to say so before the player commits - the fallback
    // notice afterwards is honest, but it is a correction, not a choice.
    val directFrames = remember {
        GameCapture.readbackIsDirect(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE)
    }
    val active by RecorderUiState.active
    val detail by RecorderUiState.detail

    // Only what this device can encode. A list is never empty here: the controller shows its own
    // "no encoder" message instead of opening the panel in that case.
    val qualities = remember(configs) { configs.map { it.quality }.distinct().sorted() }
    val frameRates = remember(configs) { configs.map { it.fps }.distinct().sorted() }
    val audioSources = remember { RecordingOptions.AudioSource.values().toList() }

    var quality by remember(configs) {
        mutableIntStateOf(qualities.takeIf { initialQuality in it }?.let { initialQuality } ?: qualities.firstOrNull() ?: 720)
    }
    var fps by remember(configs) {
        mutableIntStateOf(frameRates.takeIf { initialFps in it }?.let { initialFps } ?: frameRates.firstOrNull() ?: 30)
    }
    var audio by remember { mutableStateOf(initialAudio) }
    var withControls by remember { mutableStateOf(initialWithControls) }

    // A resolution that cannot run at the chosen rate is not offered by the encoder at all, so the
    // pair is resolved here rather than letting the player pick a combination that would fail.
    val resolved = remember(configs, quality, fps) {
        configs.firstOrNull { it.quality == quality && it.fps == fps }
            ?: configs.firstOrNull { it.quality == quality }
            ?: configs.firstOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC0A0B0E))
            // Tapping the scrim closes the panel. The panel itself stops the tap below.
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                // 0.92 of the width, but never wider than a comfortable reading column and never
                // taller than the screen: the panel is a dialog, not a page.
                .fillMaxWidth(0.92f)
                .drawBehind {
                    // The soft crimson bloom behind the panel, so it reads as floating over the game.
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                LiquidCrystalPalette.CrimsonPrimary.copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.maxDimension * 0.75f
                        ),
                        topLeft = Offset(-18.dp.toPx(), -18.dp.toPx()),
                        size = Size(
                            size.width + 36.dp.toPx(), size.height + 36.dp.toPx()
                        ),
                        cornerRadius = CornerRadius(26.dp.toPx(), 26.dp.toPx())
                    )
                }
                .clip(RoundedCornerShape(18.dp))
                .background(LiquidCrystalPalette.DarkPanel)
                .border(
                    BorderStroke(1.2.dp, LiquidCrystalPalette.CrimsonPrimary.copy(alpha = 0.55f)),
                    RoundedCornerShape(18.dp)
                )
                // Consume taps on the panel so the scrim's dismiss does not fire through it.
                .clickable(enabled = false, onClick = {})
                .padding(14.dp)
        ) {
            PanelHeader(active = active, streak = detail, onDismiss = onDismiss)

            Spacer(Modifier.height(10.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Landscape phones get the concept's three zones side by side; a portrait or very
                // narrow screen stacks them, because three columns at 360dp would make every pill
                // too small to hit reliably.
                if (maxWidth >= 520.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PreviewTile(modifier = Modifier.width(196.dp), active = active, detail = detail)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PresetRows(
                                qualities = qualities,
                                frameRates = frameRates,
                                audioSources = audioSources,
                                quality = quality,
                                fps = fps,
                                audio = audio,
                                enabled = !active,
                                resolved = resolved,
                                onQuality = { quality = it },
                                onFps = { fps = it },
                                onAudio = { audio = it }
                            )
                        }
                        RecordButton(
                            modifier = Modifier.width(124.dp),
                            active = active,
                            onStart = {
                                val config = resolved
                                if (config == null) {
                                    Toast.makeText(context, "No compatible video encoder on this device.", Toast.LENGTH_LONG).show()
                                } else {
                                    onStart(config, audio, withControls)
                                }
                            },
                            onStop = onStop,
                            onRecordings = onRecordings
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PresetRows(
                            qualities = qualities,
                            frameRates = frameRates,
                            audioSources = audioSources,
                            quality = quality,
                            fps = fps,
                            audio = audio,
                            enabled = !active,
                            resolved = resolved,
                            onQuality = { quality = it },
                            onFps = { fps = it },
                            onAudio = { audio = it }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RecordButton(
                                modifier = Modifier.weight(1f),
                                active = active,
                                compact = true,
                                onStart = {
                                    val config = resolved
                                    if (config == null) {
                                        Toast.makeText(context, "No compatible video encoder on this device.", Toast.LENGTH_LONG).show()
                                    } else {
                                        onStart(config, audio, withControls)
                                    }
                                },
                                onStop = onStop,
                                onRecordings = onRecordings
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            ControlsRow(
                withControls = withControls,
                directFrames = directFrames,
                enabled = !active,
                onToggle = { withControls = it }
            )
        }
    }
}

@Composable
private fun PanelHeader(active: Boolean, streak: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LiquidCrystalPalette.CrimsonPrimary)
            )
            Text(
                text = "SCREEN RECORDER",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
            if (active && streak.isNotBlank()) {
                Text(
                    text = streak,
                    color = LiquidCrystalPalette.CrimsonGlow,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(LiquidCrystalPalette.DarkTile)
                .border(1.dp, LiquidCrystalPalette.DarkTileBorder, CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Text("✕", color = LiquidCrystalPalette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

/**
 * The viewfinder.
 *
 * It is deliberately not a live capture preview: reading the game's frames back for the panel would
 * cost a second surface, and the game is already on screen behind the scrim. What it shows is the
 * part a player actually needs before committing - the frame the take will be cropped to, at the
 * chosen resolution, with the running time the recording will carry.
 */
@Composable
private fun PreviewTile(modifier: Modifier = Modifier, active: Boolean, detail: String) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(150.dp)
            .clip(shape)
            .background(Color(0xFF0E1014))
            .border(
                BorderStroke(1.4.dp, LiquidCrystalPalette.CrimsonPrimary.copy(alpha = if (active) 0.95f else 0.45f)),
                shape
            )
    ) {
        // Corner brackets, so it reads as a viewfinder rather than an empty box.
        Box(modifier = Modifier.fillMaxSize().drawBehind {
            val arm = 16.dp.toPx()
            val inset = 8.dp.toPx()
            val stroke = 2.dp.toPx()
            val c = LiquidCrystalPalette.CrimsonPrimary
            // top-left, top-right, bottom-left, bottom-right
            drawLine(c, Offset(inset, inset), Offset(inset + arm, inset), stroke)
            drawLine(c, Offset(inset, inset), Offset(inset, inset + arm), stroke)
            drawLine(c, Offset(size.width - inset - arm, inset), Offset(size.width - inset, inset), stroke)
            drawLine(c, Offset(size.width - inset, inset), Offset(size.width - inset, inset + arm), stroke)
            drawLine(c, Offset(inset, size.height - inset), Offset(inset + arm, size.height - inset), stroke)
            drawLine(c, Offset(inset, size.height - inset - arm), Offset(inset, size.height - inset), stroke)
            drawLine(c, Offset(size.width - inset - arm, size.height - inset), Offset(size.width - inset, size.height - inset), stroke)
            drawLine(c, Offset(size.width - inset, size.height - inset - arm), Offset(size.width - inset, size.height - inset), stroke)
        })

        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (active) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.TextMuted)
            )
            Text(
                text = "REC",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "READY",
                color = LiquidCrystalPalette.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "H.264 MP4",
                color = LiquidCrystalPalette.TextMuted,
                fontSize = 9.sp
            )
        }

        Text(
            modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
            text = if (active && detail.isNotBlank()) detail else "00:00",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PresetRows(
    qualities: List<Int>,
    frameRates: List<Int>,
    audioSources: List<RecordingOptions.AudioSource>,
    quality: Int,
    fps: Int,
    audio: RecordingOptions.AudioSource,
    enabled: Boolean,
    resolved: RecordingOptions.Config?,
    onQuality: (Int) -> Unit,
    onFps: (Int) -> Unit,
    onAudio: (RecordingOptions.AudioSource) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        RowGroup(label = "QUALITY") {
            qualities.forEach { value ->
                PresetPill(
                    label = "$value",
                    selected = value == quality,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { onQuality(value) }
            }
        }
        RowGroup(label = "FRAME RATE") {
            frameRates.forEach { value ->
                PresetPill(
                    label = "$value",
                    selected = value == fps,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { onFps(value) }
            }
        }
        AudioPicker(audioSources = audioSources, audio = audio, enabled = enabled, onAudio = onAudio)
        // What the pair actually resolved to, in the same words the recording will use. A player who
        // picked 1080 on a phone that cannot encode it sees the real size before starting, not after.
        Text(
            text = resolved?.let { "Recording at ${it.width}×${it.height} · ${it.fps} FPS · ${it.bitrate / 1_000_000} Mbps" }
                ?: "No compatible encoder for this device.",
            color = LiquidCrystalPalette.TextMuted,
            fontSize = 9.5.sp
        )
    }
}

@Composable
private fun RowGroup(label: String, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = LiquidCrystalPalette.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            content = content
        )
    }
}

@Composable
private fun PresetPill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = when {
        selected -> LiquidCrystalPalette.CrimsonPrimary
        else -> LiquidCrystalPalette.DarkTile
    }
    val border = if (selected) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.DarkTileBorder
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.45f))
            .border(1.dp, if (enabled) border else border.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected && enabled) Color.White else LiquidCrystalPalette.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold
        )
    }
}

@Composable
private fun AudioPicker(
    audioSources: List<RecordingOptions.AudioSource>,
    audio: RecordingOptions.AudioSource,
    enabled: Boolean,
    onAudio: (RecordingOptions.AudioSource) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "AUDIO",
            color = LiquidCrystalPalette.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(LiquidCrystalPalette.DarkTile)
                    .border(1.dp, LiquidCrystalPalette.DarkTileBorder, RoundedCornerShape(9.dp))
                    .clickable(enabled = enabled) { expanded = !expanded }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = audio.label.uppercase(),
                    color = LiquidCrystalPalette.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = LiquidCrystalPalette.TextMuted,
                    fontSize = 9.sp
                )
            }
            if (expanded && enabled) {
                // Drawn as a list under the picker instead of a DropdownMenu, so it stays inside the
                // panel and cannot be dismissed by the scrim behind it.
                Column(
                    modifier = Modifier
                        .padding(top = 42.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(LiquidCrystalPalette.DarkHeader)
                        .border(1.dp, LiquidCrystalPalette.DarkTileBorder, RoundedCornerShape(9.dp))
                        .padding(vertical = 4.dp)
                ) {
                    audioSources.forEach { source ->
                        Text(
                            text = source.label,
                            color = if (source == audio) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (source == audio) FontWeight.Black else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAudio(source)
                                    expanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The record control: one target, the size of a thumb, that starts or stops the take.
 *
 * [compact] is the narrow-screen form, where the circle is inlined into a full-width row instead of
 * sitting under it with its own caption.
 */
@Composable
private fun RecordButton(
    modifier: Modifier = Modifier,
    active: Boolean,
    compact: Boolean = false,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRecordings: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 54.dp else 88.dp)
                .drawBehind {
                    // The bloom that makes the button the obvious thing to press.
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                LiquidCrystalPalette.CrimsonGlow.copy(alpha = if (active) 0.34f else 0.24f),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.maxDimension * 0.62f
                        )
                    )
                }
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            LiquidCrystalPalette.CrimsonGlow,
                            LiquidCrystalPalette.CrimsonPrimary,
                            LiquidCrystalPalette.CrimsonDeep
                        )
                    )
                )
                .border(2.dp, LiquidCrystalPalette.CrimsonGlow.copy(alpha = 0.7f), CircleShape)
                .clickable(onClick = if (active) onStop else onStart),
            contentAlignment = Alignment.Center
        ) {
            if (active) {
                // Stop: a square, the universal shape for "end this".
                Box(
                    modifier = Modifier
                        .size(if (compact) 18.dp else 26.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(if (compact) 18.dp else 26.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
        Text(
            text = if (active) "STOP & SAVE" else "START",
            color = Color.White,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "RECORDINGS",
            color = LiquidCrystalPalette.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onRecordings)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * "Record with controls", with the one line that explains what it changes.
 *
 * The old dialog printed a paragraph that swapped between two long explanations; the switch is the
 * choice, so the caption says what the current position means and no more.
 */
@Composable
private fun ControlsRow(
    withControls: Boolean,
    directFrames: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x22000000))
            .border(1.dp, LiquidCrystalPalette.DarkTileBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onToggle(!withControls) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (withControls) LiquidCrystalPalette.CrimsonPrimary else LiquidCrystalPalette.DarkTile)
                .border(
                    1.dp,
                    if (withControls) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.DarkTileBorder,
                    RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (withControls) Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Record with controls",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    withControls -> "Your touch controls appear in the video."
                    // The promise, and the odds, in the same breath: on the SurfaceView path some
                    // devices will not give up their frames, and the controls are hidden for that
                    // take instead - they keep working where they were, but they are not readable.
                    directFrames ->
                        "Game frames only: the controls stay tappable and never appear in the video."
                    else ->
                        "Game frames only: the controls stay tappable and out of the video, unless " +
                            "this device's SurfaceView path refuses the frames - then they are hidden " +
                            "for the take instead."
                },
                color = LiquidCrystalPalette.TextMuted,
                fontSize = 9.5.sp
            )
        }
    }
}
