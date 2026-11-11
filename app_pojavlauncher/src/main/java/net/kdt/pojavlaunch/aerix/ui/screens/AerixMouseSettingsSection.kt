/*
 * Aerix Launcher - Settings → Mouse settings: the cursor the player picks, and its preview.
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

package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import net.kdt.pojavlaunch.aerix.client.AerixCursorSkin
import net.kdt.pojavlaunch.aerix.components.CardPosition
import net.kdt.pojavlaunch.aerix.components.MiraiIntSliderSettingsCard
import net.kdt.pojavlaunch.aerix.components.MiraiSwitchSettingsCard
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import kotlin.math.roundToInt

/**
 * The cursor section of the launcher settings: which image the pointer draws, how big and how
 * opaque it is, and what that will look like over a game frame.
 *
 * The preview is drawn with the same numbers the game view uses - the box is the 54 dp tall arrow
 * footprint scaled by [LauncherPreferences.PREF_MOUSESCALE], the alpha is the opacity slider - so
 * what the player sees here is what the cursor does on screen. A preview that scaled its own way
 * would be decoration, not information.
 */
@Composable
fun AerixMouseSettingsSection(
    onOpenPreview: () -> Unit = {},
) {
    val context = LocalContext.current
    val options = remember { AerixCursorSkin.presets(context) }
    var report by remember { mutableStateOf<String?>(null) }
    var previewOpen by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // The import is reported whatever its outcome, because a silently ignored image looks
        // identical to a cursor setting that "does nothing".
        report = AerixCursorSkin.importImage(context, uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Cursor skin · ${AerixCursorSkin.describe(context)}",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                CursorSkinTile(
                    option = option,
                    selected = option.id == AerixCursorSkin.skin,
                    onClick = {
                        AerixCursorSkin.select(context, option.id)
                        report = "Applied: ${AerixCursorSkin.describe(context)}"
                    },
                    onLongPress = {
                        if (option.id.startsWith("file:")) {
                            AerixCursorSkin.forget(context, option.id)
                            report = "Removed that imported cursor."
                        }
                    }
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .size(width = 74.dp, height = 74.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AerixSurface.panel)
                        .border(1.dp, AerixSurface.borderSoft, RoundedCornerShape(14.dp))
                        .clickable { picker.launch(arrayOf("image/*")) },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("+", color = AerixSurface.accent, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("From photos", color = AerixSurface.accentSecondary, fontSize = 9.sp)
                }
            }
        }
        Text(
            "Long press an imported image to remove it. Bundled presets stay.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )

        MiraiIntSliderSettingsCard(
            position = CardPosition.Top,
            title = "Cursor size",
            summary = "How big the launcher's pointer is drawn, in and out of the game.",
            value = remember { AerixCursorSkin.scalePercent },
            valueRange = 25..300,
            steps = 0,
            fineTuningStep = 5,
            suffix = " %",
            onValueChange = { AerixCursorSkin.setScale(context, it) }
        )
        MiraiIntSliderSettingsCard(
            position = CardPosition.Middle,
            title = "Cursor opacity",
            summary = "A see-through pointer stays out of the way of hotbar slots and crosshair lines.",
            value = remember { AerixCursorSkin.opacityPercent },
            valueRange = 20..100,
            steps = 0,
            fineTuningStep = 5,
            suffix = " %",
            onValueChange = { AerixCursorSkin.setOpacity(context, it) }
        )
        MiraiSwitchSettingsCard(
            position = CardPosition.Middle,
            title = "Point from the image tip",
            summary = if (AerixCursorSkin.anchoredCenter)
                "Off: the picture is centred on the pointer - right for items and crosshairs."
            else "On: the top-left corner of the image is the pointer - right for an arrow.",
            checked = !AerixCursorSkin.anchoredCenter,
            onCheckedChange = { AerixCursorSkin.select(context, AerixCursorSkin.skin, center = !it) }
        )
        MiraiIntSliderSettingsCard(
            position = CardPosition.Middle,
            title = "Look sensitivity (in game)",
            summary = "Turns the camera while the game holds the mouse. Does not move the launcher cursor.",
            value = remember { (LauncherPreferences.PREF_GAME_MOUSE_SENSITIVITY * 100).roundToInt() },
            valueRange = 25..300,
            steps = 0,
            fineTuningStep = 5,
            suffix = " %",
            onValueChange = {
                LauncherPreferences.DEFAULT_PREF?.edit()?.putInt("gameMouseSensitivity", it)?.apply()
                LauncherPreferences.PREF_GAME_MOUSE_SENSITIVITY = it / 100f
            }
        )
        MiraiIntSliderSettingsCard(
            position = CardPosition.Bottom,
            title = "Launcher cursor speed",
            summary = "How fast the pointer travels on launcher screens and in game menus.",
            value = remember { (LauncherPreferences.PREF_MOUSESPEED * 100).roundToInt() },
            valueRange = 25..300,
            steps = 0,
            fineTuningStep = 5,
            suffix = " %",
            onValueChange = {
                LauncherPreferences.DEFAULT_PREF?.edit()?.putInt("mousespeed", it)?.apply()
                LauncherPreferences.PREF_MOUSESPEED = it / 100f
            }
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Preview in game",
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AerixSurface.accentContainer)
                    .clickable {
                        previewOpen = true
                        onOpenPreview()
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                color = AerixSurface.onAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            report?.let {
                Text(it, color = AerixSurface.accentSecondary, fontSize = 11.sp)
            }
        }

        // Shown inline as well, so the size and opacity sliders have something to change while
        // they are being dragged.
        CursorPreview(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            emphasize = false
        )
    }

    if (previewOpen) {
        Dialog(onDismissRequest = { previewOpen = false }) {
            Box(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                CursorPreview(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)), emphasize = true)
                Text(
                    "This is the launcher's own pointer, drawn over the game at the size and opacity " +
                        "above. Tap anywhere outside to close.",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CursorSkinTile(
    option: AerixCursorSkin.Option,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .size(width = 74.dp, height = 74.dp)
            .clip(shape)
            .background(if (selected) AerixSurface.accentContainer else AerixSurface.panel)
            .border(1.dp, if (selected) AerixSurface.borderHighlight else AerixSurface.borderSoft, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (option.id.isEmpty()) {
            Text("→", color = AerixSurface.accent, fontSize = 22.sp, fontWeight = FontWeight.Black)
        } else {
            val bitmap = remember(option.id) {
                runCatching {
                    val stream = if (option.id.startsWith("asset:"))
                        context.assets.open(option.source)
                    else java.io.File(option.source).inputStream()
                    stream.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = option.label,
                    modifier = Modifier.size(38.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            option.label.take(12),
            color = if (selected) AerixSurface.accent else AerixSurface.accentSecondary,
            fontSize = 8.sp,
            maxLines = 1
        )
    }
}

/** The game frame the cursor is judged against: sky, blocks, and a chest-height GUI edge. */
@Composable
private fun CursorPreview(modifier: Modifier = Modifier, emphasize: Boolean) {
    val context = LocalContext.current
    val bitmap = remember(AerixCursorSkin.skin) { AerixCursorSkin.currentBitmap(context) }
    val sizePercent = AerixCursorSkin.scalePercent
    val alpha = AerixCursorSkin.opacityPercent / 100f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF6FA8DC), Color(0xFF9BC4E2), Color(0xFF6D8F5B), Color(0xFF55743F))
                )
            )
    ) {
        // A hotbar, because that is where an oversized or too-transparent cursor is noticed.
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(9) {
                Box(
                    modifier = Modifier
                        .size(if (emphasize) 30.dp else 20.dp)
                        .background(Color(0x99FFFFFF))
                        .border(1.dp, Color(0x66101410))
                )
            }
        }
        if (bitmap != null) {
            // The same footprint the game cursor view uses: a 54-point box, scaled by the size
            // slider, aspect preserved, drawn with the opacity the player set.
            val box = ((if (emphasize) 54 else 40) * sizePercent / 100f).roundToInt().coerceAtLeast(6)
            val (w, h) = AerixCursorSkin.fitIn(bitmap.width, bitmap.height, box)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Cursor preview",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = w.dp, height = h.dp)
                    .graphicsLayer { this.alpha = alpha },
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                "default arrow",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                fontSize = 11.sp
            )
        }
        Text(
            "${sizePercent}% · ${(alpha * 100).roundToInt()}% opacity",
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            color = Color(0xE6FFFFFF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
