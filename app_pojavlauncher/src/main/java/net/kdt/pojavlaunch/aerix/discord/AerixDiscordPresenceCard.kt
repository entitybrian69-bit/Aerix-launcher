/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Settings card for Discord Rich Presence.
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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.discord

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.components.CardPosition
import net.kdt.pojavlaunch.aerix.components.MiraiSwitchSettingsCard
import net.kdt.pojavlaunch.aerix.components.MiraiTextInputSettingsCard
import net.kdt.pojavlaunch.aerix.components.SettingsCardColumn

/**
 * "Discord Rich Presence": a switch, and the application id the card is published under.
 *
 * One Discord application id is all Discord needs. It is created for free at
 * discord.com/developers → New Application, and the name typed there is the title on the profile
 * card, so naming it "Aerix Launcher" is what makes the card read that way.
 */
@Composable
fun DiscordPresenceSettingsCard() {
    val context = LocalContext.current.applicationContext
    var enabled by remember { mutableStateOf(AerixPreferences.discordPresenceEnabled) }
    var appId by remember { mutableStateOf(AerixPreferences.discordAppId) }

    // Re-connect once the typing settles instead of on every keystroke.
    LaunchedEffect(appId, enabled) {
        delay(700)
        if (enabled && appId.length >= 15) AerixDiscordPresence.refresh(context)
    }

    SettingsCardColumn(modifier = Modifier.fillMaxWidth()) {
        MiraiSwitchSettingsCard(
            position = CardPosition.Top,
            title = "Discord Rich Presence",
            summary = "Show what you are doing in Aerix Launcher on your own Discord profile: " +
                "\u201CIn the launcher\u201D, then the page you are on, with a timer. Needs the Discord " +
                "app installed and signed in; nothing about your account is ever read or sent.",
            checked = enabled,
            onCheckedChange = { on ->
                enabled = on
                AerixPreferences.saveDiscordPresenceEnabled(on)
                if (on) {
                    AerixDiscordPresence.refresh(context)
                } else {
                    AerixDiscordPresence.clear()
                    AerixDiscordPresence.detach()
                }
            }
        )
        MiraiTextInputSettingsCard(
            value = appId,
            title = "Discord application ID",
            summary = "Already set to the Aerix Launcher application, so nothing has to be done here. " +
                "Fill this in only to publish under a different Discord application.",
            label = "Built in: 1563812135744839771 — override if you know why",
            position = CardPosition.Bottom,
            onValueChange = { raw ->
                val clean = raw.filter { it.isDigit() }.take(20)
                appId = clean
                AerixPreferences.saveDiscordAppId(clean)
            }
        )
    }
}
