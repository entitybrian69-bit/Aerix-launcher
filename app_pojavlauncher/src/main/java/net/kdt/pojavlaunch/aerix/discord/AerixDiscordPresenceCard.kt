/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Settings card + live interactive preview for Discord Rich Presence.
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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.components.CardPosition
import net.kdt.pojavlaunch.aerix.components.MiraiSettingsCard
import net.kdt.pojavlaunch.aerix.components.MiraiSwitchSettingsCard
import net.kdt.pojavlaunch.aerix.components.MiraiTextInputSettingsCard
import net.kdt.pojavlaunch.aerix.components.SettingsCardColumn
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

/**
 * "Discord Rich Presence" settings group:
 * 1. Master Rich Presence toggle (clears on exit).
 * 2. Mode toggle between **Hybrid Official Minecraft Game Profile in-game** and **Aerix Launcher
 *    Card everywhere**.
 * 3. Toggle for **Cover Art, Clickable Info Links & Profile Action Buttons**.
 * 4. Live Discord profile card preview + optional custom Application ID override.
 */
@Composable
fun DiscordPresenceSettingsCard() {
    val activityContext = LocalContext.current
    val appContext = activityContext.applicationContext
    var enabled by remember { mutableStateOf(AerixPreferences.discordPresenceEnabled) }
    var gameProfileMode by remember { mutableStateOf(AerixPreferences.discordGameProfileMode) }
    var clickableLinks by remember { mutableStateOf(AerixPreferences.discordClickableLinks) }
    var appId by remember { mutableStateOf(AerixPreferences.discordAppId) }
    var previewInGame by remember { mutableStateOf(true) }

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            activityContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // Re-connect once custom App ID typing settles instead of reconnecting on every keystroke.
    LaunchedEffect(appId) {
        delay(700)
        if (enabled && (appId.isEmpty() || appId.length >= 15)) {
            AerixDiscordPresence.syncSettingsChanged(appContext)
        }
    }

    SettingsCardColumn(modifier = Modifier.fillMaxWidth()) {
        MiraiSwitchSettingsCard(
            position = CardPosition.Top,
            title = "Discord Rich Presence",
            summary = "Show what you are doing in Aerix Launcher on your Discord profile, " +
                "and clear when you leave the app or close the game.",
            checked = enabled,
            onCheckedChange = { on ->
                enabled = on
                AerixPreferences.saveDiscordPresenceEnabled(on)
                if (on) {
                    AerixDiscordPresence.syncSettingsChanged(appContext)
                } else {
                    AerixDiscordPresence.clearAndDetach(appContext)
                }
            }
        )

        MiraiSwitchSettingsCard(
            position = CardPosition.Middle,
            title = "Official Minecraft Game Profile in-game",
            summary = "Hybrid mode: shows the clickable Aerix Launcher card while in the launcher, " +
                "and switches to Minecraft's verified Discord ID while playing so tapping your " +
                "presence opens Minecraft's official full Game Profile sheet (\u201Cvia Aerix Launcher\u201D). " +
                "Turn off to keep \u201CAerix Launcher\u201D as the card title everywhere.",
            checked = gameProfileMode,
            enabled = enabled,
            onCheckedChange = { on ->
                gameProfileMode = on
                AerixPreferences.saveDiscordGameProfileMode(on)
                if (enabled) AerixDiscordPresence.syncSettingsChanged(appContext)
            }
        )

        MiraiSwitchSettingsCard(
            position = CardPosition.Middle,
            title = "Cover art, clickable info links & buttons",
            summary = "Attach Aerix / modpack cover artwork, make the headline & status clickable " +
                "(opening the official Aerix website & Discord), and display \u201CAbout Aerix Launcher\u201D " +
                "+ \u201CJoin Aerix Discord\u201D buttons on your profile card.",
            checked = clickableLinks,
            enabled = enabled,
            onCheckedChange = { on ->
                clickableLinks = on
                AerixPreferences.saveDiscordClickableLinks(on)
                if (enabled) AerixDiscordPresence.syncSettingsChanged(appContext)
            }
        )

        MiraiSettingsCard(
            position = CardPosition.Middle,
            innerPadding = PaddingValues(16.dp)
        ) {
            DiscordRichPresenceLivePreview(
                enabled = enabled,
                previewInGame = previewInGame,
                onPreviewModeChange = { previewInGame = it },
                gameProfileMode = gameProfileMode && appId.trim().length < 15,
                clickableLinks = clickableLinks,
                onOpenWebsite = { openUrl(AerixDiscordPresence.AERIX_WEBSITE_URL) },
                onOpenDiscord = { openUrl(AerixDiscordPresence.AERIX_DISCORD_URL) }
            )
        }

        MiraiTextInputSettingsCard(
            value = appId,
            title = "Discord application ID",
            summary = "Already set to the Aerix Launcher application, so nothing has to be done here. " +
                "Fill this in only to publish under a custom Discord application ID.",
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

@Composable
private fun DiscordRichPresenceLivePreview(
    enabled: Boolean,
    previewInGame: Boolean,
    onPreviewModeChange: (Boolean) -> Unit,
    gameProfileMode: Boolean,
    clickableLinks: Boolean,
    onOpenWebsite: () -> Unit,
    onOpenDiscord: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    val usingOfficialMcProfile = previewInGame && gameProfileMode

    val cardHeader = if (usingOfficialMcProfile) "Playing a game" else "Playing Aerix Launcher"
    val titleLine = when {
        usingOfficialMcProfile -> "Minecraft"
        previewInGame -> "Playing Minecraft 1.21.4"
        else -> "In the launcher"
    }
    val detailsLine = when {
        usingOfficialMcProfile -> "Playing Minecraft 1.21.4 · via Aerix Launcher"
        previewInGame -> "Aerix Launcher · Survival SMP"
        else -> "Browsing the dashboard"
    }
    val stateLine = if (usingOfficialMcProfile) "Survival SMP (2 of 4)" else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Discord Card Preview",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PreviewStageChip(
                    label = "Launcher",
                    selected = !previewInGame,
                    accent = accent,
                    onClick = { onPreviewModeChange(false) }
                )
                PreviewStageChip(
                    label = "In-Game",
                    selected = previewInGame,
                    accent = accent,
                    onClick = { onPreviewModeChange(true) }
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF111214).copy(alpha = 0.88f),
            border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cardHeader.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.68f),
                        letterSpacing = 0.6.sp
                    )
                    if (usingOfficialMcProfile) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accent.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "Opens Game Profile",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accent
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .let { mod ->
                                if (clickableLinks) mod.clickable(onClick = onOpenWebsite) else mod
                            }
                    ) {
                        if (clickableLinks) {
                            Image(
                                painter = painterResource(R.drawable.aerix_logo),
                                contentDescription = "Aerix Cover Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E1F22))
                            )
                            if (usingOfficialMcProfile) {
                                Image(
                                    painter = painterResource(R.drawable.aerix_logo),
                                    contentDescription = "Aerix Badge",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 4.dp, y = 4.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, Color(0xFF111214), CircleShape)
                                        .clickable(onClick = onOpenDiscord)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2B2D31)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (usingOfficialMcProfile) "MC" else "AX",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = titleLine,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (clickableLinks && !usingOfficialMcProfile) {
                                TextDecoration.Underline
                            } else {
                                TextDecoration.None
                            },
                            modifier = if (clickableLinks) {
                                Modifier.clickable(onClick = onOpenWebsite)
                            } else {
                                Modifier
                            }
                        )
                        Text(
                            text = detailsLine,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.84f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (clickableLinks) {
                                TextDecoration.Underline
                            } else {
                                TextDecoration.None
                            },
                            modifier = if (clickableLinks) {
                                Modifier.clickable(
                                    onClick = if (usingOfficialMcProfile) onOpenWebsite else onOpenDiscord
                                )
                            } else {
                                Modifier
                            }
                        )
                        if (stateLine != null) {
                            Text(
                                text = stateLine,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (clickableLinks) {
                                    Modifier.clickable(onClick = onOpenDiscord)
                                } else {
                                    Modifier
                                }
                            )
                        }
                        Text(
                            text = "00:14:28 elapsed",
                            fontSize = 11.sp,
                            color = Color(0xFF23A55A),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (clickableLinks) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiscordActionButtonPreview(
                            label = "About Aerix Launcher",
                            modifier = Modifier.weight(1f),
                            onClick = onOpenWebsite
                        )
                        DiscordActionButtonPreview(
                            label = "Join Aerix Discord",
                            modifier = Modifier.weight(1f),
                            onClick = onOpenDiscord
                        )
                    }
                }
            }
        }

        Text(
            text = if (usingOfficialMcProfile) {
                "Tip: In Official Minecraft mode, friends tapping the game card open Minecraft's native " +
                    "Discord Game Profile sheet, while tapping the text/buttons opens Aerix Launcher's website & Discord."
            } else {
                "Note: Discord shows Rich Presence action buttons to friends and server members viewing your " +
                    "profile (tap any link or button above to test where it opens)."
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.62f)
        )
    }
}

@Composable
private fun PreviewStageChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accent.copy(alpha = 0.22f) else AerixSurface.panelTrack,
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.65f) else AerixSurface.borderSoft
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else Color.White.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun DiscordActionButtonPreview(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF2B2D31),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
