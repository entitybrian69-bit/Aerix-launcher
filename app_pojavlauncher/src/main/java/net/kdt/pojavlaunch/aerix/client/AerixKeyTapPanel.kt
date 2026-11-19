/*
 * Aerix Launcher - the in-game "tap a key" panel.
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

package net.kdt.pojavlaunch.aerix.client

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.utils.KeycodeUtils

/**
 * A key the panel can press, with the name a player recognises.
 *
 * The keycodes come from [KeycodeUtils], which is the same table the launcher's own control buttons
 * and its "Send custom keycode" dialog use: pressing a key through a different table would be a
 * second, quieter way to be wrong.
 */
data class KeyTap(val keyCode: Int, val label: String, val group: String)

/**
 * Every key this launcher can press, grouped so a player can find one without scrolling a wall.
 *
 * A ghost client's menu key is usually Right Shift, which most touch layouts have no button for at
 * all - that is the whole reason this exists. The list is generated from the launcher's own table
 * rather than typed out, so it can never drift from what the press path actually supports.
 */
object AerixKeyCatalog {
    private val groups = listOf(
        "Movement" to setOf(
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT
        ),
        "Actions" to setOf(
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9,
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL
        ),
        "Menu keys" to setOf(
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_CAPS_LOCK
        ),
        "Function" to (KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12).toSet()
    )

    /** Built once: the underlying name array is already cached by KeycodeUtils. */
    val all: List<KeyTap> by lazy {
        val names = KeycodeUtils.generateKeyName()
        buildList {
            names.indices.forEach { index ->
                val code = KeycodeUtils.getValueByIndex(index)
                if (code == KeyEvent.KEYCODE_UNKNOWN) return@forEach
                val name = names[index].removePrefix("KEYCODE_")
                add(KeyTap(code, name, groupOf(code)))
            }
        }.distinctBy { it.keyCode }
            .sortedWith(compareBy({ groupOrder(it.group) }, { it.keyCode }))
    }

    fun find(keyCode: Int): KeyTap? = all.firstOrNull { it.keyCode == keyCode }

    /** The keys a ghost/hack client most often opens on, shown first because that is the use. */
    val suggested: List<KeyTap> by lazy {
        listOfNotNull(
            find(KeyEvent.KEYCODE_SHIFT_RIGHT), find(KeyEvent.KEYCODE_SHIFT_LEFT),
            find(KeyEvent.KEYCODE_INSERT), find(KeyEvent.KEYCODE_MOVE_HOME),
            find(KeyEvent.KEYCODE_TAB), find(KeyEvent.KEYCODE_GRAVE)
        )
    }

    private fun groupOf(code: Int): String =
        groups.firstOrNull { it.second.contains(code) }?.first ?: "Other"

    private fun groupOrder(group: String): Int =
        groups.indexOfFirst { it.first == group }.let { if (it < 0) groups.size else it }
}

/**
 * The panel: the keys a client is most likely to open on, then the whole table.
 *
 * @param onTap called with the keycode. The caller sends a real held press-and-release, which is
 *        what a control button does - a single-frame tap is missed by clients that poll
 *        `wasPressed()` once per tick, and that difference is not visible to the player.
 */
@Composable
fun AerixKeyTapPanel(onTap: (Int) -> Unit, onDismiss: () -> Unit) {
    var lastSent by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xCC0A0B0E))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(shape)
                .background(LiquidCrystalPalette.DarkPanel)
                .border(BorderStroke(1.2.dp, LiquidCrystalPalette.CrimsonPrimary.copy(alpha = 0.55f)), shape)
                .clickable(enabled = false, onClick = {})
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "TAP A KEY",
                    color = LiquidCrystalPalette.CrimsonPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Close",
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(LiquidCrystalPalette.DarkTile)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    color = LiquidCrystalPalette.TextSecondary,
                    fontSize = 10.sp
                )
            }
            Text(
                "Sends a real key press into the game, the same way a control button does. Use it for a " +
                    "client whose menu is bound to a key your touch layout has no button for - Right " +
                    "Shift is the usual one.",
                color = LiquidCrystalPalette.TextMuted,
                fontSize = 9.5.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                "MOST LIKELY",
                color = LiquidCrystalPalette.TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AerixKeyCatalog.suggested.forEach { key ->
                    KeyChip(key.label, emphasised = true) {
                        onTap(key.keyCode)
                        lastSent = key.label
                    }
                }
            }

            lastSent?.let { sent ->
                Text(
                    "Sent $sent",
                    color = LiquidCrystalPalette.CrimsonGlow,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Text(
                "ALL KEYS",
                color = LiquidCrystalPalette.TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 62.dp),
                modifier = Modifier.fillMaxWidth().height(190.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(AerixKeyCatalog.all, key = { it.keyCode }) { key ->
                    KeyChip(key.label, emphasised = false) {
                        onTap(key.keyCode)
                        lastSent = key.label
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyChip(label: String, emphasised: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .aspectRatio(1.9f)
            .clip(shape)
            .background(
                if (emphasised) LiquidCrystalPalette.CrimsonTintFill else LiquidCrystalPalette.DarkTile
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (emphasised) LiquidCrystalPalette.CrimsonPrimary else LiquidCrystalPalette.DarkTileBorder
                ),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (emphasised) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
