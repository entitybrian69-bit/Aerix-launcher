/*
 * Aerix Launcher Social layer.
 *
 * In-game overlay. It shares the launcher's repository, so a message sent in the overlay
 * appears in the launcher thread and the other way round.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui.ingame

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.media.SocialMic
import net.kdt.pojavlaunch.aerix.social.ui.SocialMicMeter
import net.kdt.pojavlaunch.aerix.social.data.ServerInviteState
import net.kdt.pojavlaunch.aerix.social.ui.SocialAvatar
import net.kdt.pojavlaunch.aerix.social.ui.SocialBody
import net.kdt.pojavlaunch.aerix.social.ui.SocialColors
import net.kdt.pojavlaunch.aerix.social.ui.SocialDimens
import net.kdt.pojavlaunch.aerix.social.ui.SocialDialog
import net.kdt.pojavlaunch.aerix.social.ui.SocialGhostButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialPanel
import net.kdt.pojavlaunch.aerix.social.ui.SocialPrimaryButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialSectionTitle
import net.kdt.pojavlaunch.aerix.social.ui.SocialTitle
import net.kdt.pojavlaunch.aerix.social.vm.CompactMode
import net.kdt.pojavlaunch.aerix.social.vm.InGameSocialViewModel

@Composable
fun SocialCompactPanel(
    viewModel: InGameSocialViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var draft by remember { mutableStateOf("") }
    var blockTarget by remember { mutableStateOf<String?>(null) }
    var confirmJoin by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SocialColors.scrim.copy(alpha = 0.55f))
                .clickable(onClick = onClose)
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(340.dp)
                .fillMaxHeight()
                .background(SocialColors.backgroundTop)
                .border(BorderStroke(1.dp, SocialColors.border))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (state.mode) {
                        CompactMode.CHAT -> "Chat"
                        CompactMode.VOICE -> "Voice party"
                        CompactMode.INVITE -> "Server invitation"
                        CompactMode.AUTH_REQUIRED -> "Social"
                    },
                    color = SocialColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SocialColors.panelRaised)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_close),
                        contentDescription = "Close",
                        tint = SocialColors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            when (state.mode) {
                CompactMode.AUTH_REQUIRED -> {
                    SocialPanel(modifier = Modifier.fillMaxWidth()) {
                        SocialTitle("Sign in from the launcher")
                        SocialBody("Your Aerix account is needed for friends, private messages and voice parties.")
                        SocialBody("Sign-in is available in the launcher, not inside this overlay.", size = 12.sp, color = SocialColors.textMuted)
                        SocialGhostButton("Back to game", onClick = onClose, modifier = Modifier.fillMaxWidth())
                    }
                }
                CompactMode.CHAT -> {
                    // Every friend is reachable without leaving the game: tap to switch, call
                    // them, or just keep typing while the game keeps running behind the panel.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.friends.take(5).forEach { friend ->
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (friend.id == state.activeFriendId) SocialColors.accent.copy(alpha = 0.35f)
                                            else Color.Transparent
                                        )
                                        .clickable { viewModel.selectFriend(friend) }
                                        .padding(3.dp)
                                ) {
                                    SocialAvatar(
                                        avatar = friend.avatar,
                                        size = 30.dp,
                                        story = friend.hasStory
                                    )
                                }
                            }
                        }
                        SocialGhostButton("Call", onClick = viewModel::startCall, modifier = Modifier)
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        state.messages.forEach { message ->
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (message.mine) SocialColors.accent else SocialColors.panelRaised)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    SocialBody(message.text, size = 13.sp, color = Color.White)
                                }
                                SocialBody(message.timestamp, size = 9.sp, color = SocialColors.textMuted)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SocialGhostButton("Invite to party", onClick = { viewModel.show(CompactMode.VOICE) }, modifier = Modifier.weight(1f))
                        SocialGhostButton("Server invite", onClick = { viewModel.show(CompactMode.INVITE) }, modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(SocialColors.panel)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            singleLine = true,
                            textStyle = TextStyle(color = SocialColors.textPrimary, fontSize = 13.sp),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                Box {
                                    if (draft.isEmpty()) SocialBody("Message Nova…", size = 13.sp, color = SocialColors.textMuted)
                                    inner()
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(SocialColors.accent)
                                .clickable {
                                    viewModel.send(draft)
                                    draft = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_social_ic_send),
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                CompactMode.VOICE -> VoicePanel(
                    joined = state.party.joined,
                    reconnecting = state.party.reconnecting,
                    muted = state.party.muted,
                    onJoin = viewModel::joinParty,
                    onLeave = viewModel::leaveParty,
                    onMute = viewModel::toggleMute
                )
                CompactMode.INVITE -> {
                    val invite = state.invite
                    if (invite == null) {
                        SocialPanel(modifier = Modifier.fillMaxWidth()) {
                            SocialBody("No pending server invitation.", size = 13.sp)
                        }
                    } else if (invite.state == ServerInviteState.EXPIRED) {
                        SocialPanel(modifier = Modifier.fillMaxWidth()) {
                            SocialAvatar(avatar = invite.avatar, size = 44.dp)
                            SocialSectionTitle(invite.fromName)
                            SocialTitle("This invitation has expired.")
                            SocialBody("Ask ${invite.fromName} to send a new invitation if you still want to join.")
                            SocialGhostButton("Message ${invite.fromName}", onClick = { viewModel.show(CompactMode.CHAT) }, modifier = Modifier.fillMaxWidth())
                            SocialGhostButton("Dismiss", onClick = onClose, modifier = Modifier.fillMaxWidth())
                        }
                    } else {
                        SocialPanel(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SocialAvatar(avatar = invite.avatar, size = 40.dp)
                                Column {
                                    SocialBody("${invite.fromName} invited you", size = 14.sp, color = SocialColors.textPrimary, weight = FontWeight.SemiBold)
                                    SocialBody("Join us for survival", size = 12.sp, color = SocialColors.textMuted)
                                }
                            }
                            if (!invite.matchesInstance) {
                                SocialBody("Your current instance does not match", size = 13.sp, color = SocialColors.textPrimary)
                                SocialBody("Required: Minecraft ${invite.requiredVersion} • ${invite.requiredLoader}", size = 12.sp)
                                SocialBody("Current: Minecraft ${invite.currentVersion} • ${invite.currentLoader}", size = 12.sp)
                                SocialBody(
                                    "Matching version and loader may not be enough; the server may require specific mods.",
                                    size = 11.sp,
                                    color = SocialColors.textMuted
                                )
                            }
                            SocialBody("Joining will leave your current game session.", size = 12.sp, color = SocialColors.textMuted)
                            SocialPrimaryButton("Join", onClick = { confirmJoin = true }, modifier = Modifier.fillMaxWidth())
                            SocialGhostButton("Message ${invite.fromName}", onClick = { viewModel.show(CompactMode.CHAT) }, modifier = Modifier.fillMaxWidth())
                            SocialGhostButton("Dismiss", onClick = onClose, modifier = Modifier.fillMaxWidth())
                        }
                        if (confirmJoin) {
                            SocialDialog(onDismiss = { confirmJoin = false }) {
                                SocialTitle("Leave your current game?")
                                SocialBody("Joining ${invite.fromName}'s server closes your current game session. Unsaved progress in your world may be lost.")
                                SocialBody("Your own instance is not deleted; you can open it again from the launcher.", size = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SocialPrimaryButton(
                                        "Leave and join",
                                        onClick = { confirmJoin = false; onClose() },
                                        modifier = Modifier.weight(1f)
                                    )
                                    SocialGhostButton("Stay in game", onClick = { confirmJoin = false }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Nothing to block while signed out: the account-level actions only exist once the
            // player has an Aerix profile.
            if (state.mode != CompactMode.AUTH_REQUIRED) {
                SocialGhostButton(
                    text = "Block ${state.invite?.fromName ?: "player"}",
                    onClick = { blockTarget = state.invite?.fromName ?: "Kai" },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (blockTarget != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SocialDialog(onDismiss = { blockTarget = null }) {
                SocialTitle("Block $blockTarget?")
                SocialBody("$blockTarget will not be able to message you, invite you to parties, or send server invitations. You will stop seeing each other as friends.")
                SocialBody("You can unblock $blockTarget later in Launcher Social settings.", size = 11.sp, color = SocialColors.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialPrimaryButton("Block", onClick = { blockTarget = null; onClose() }, danger = true, modifier = Modifier.weight(1f))
                    SocialGhostButton("Cancel", onClick = { blockTarget = null }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun VoicePanel(
    joined: Boolean,
    reconnecting: Boolean,
    muted: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onMute: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(SocialMic.hasPermission(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
    }
    // The in-game call uses the device microphone: starting and stopping follows join/mute.
    LaunchedEffect(joined, muted, granted) {
        if (joined && !muted && granted) SocialMic.start(scope) else SocialMic.stop()
    }
    DisposableEffect(Unit) { onDispose { SocialMic.stop() } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        SocialPanel(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SocialAvatar(avatar = 6, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SocialBody("Nova's party", size = 14.sp, color = SocialColors.textPrimary, weight = FontWeight.SemiBold)
                    SocialBody("Invite only", size = 11.sp, color = SocialColors.textMuted)
                }
                SocialBody(if (joined) "Connected" else "Not connected", size = 11.sp, color = SocialColors.textMuted)
            }
            if (reconnecting) {
                SocialBody("Voice connection lost. Reconnecting…", size = 12.sp, color = SocialColors.danger)
            }
            if (muted) {
                SocialBody("Microphone access is off", size = 12.sp, color = SocialColors.textSecondary)
            } else if (joined) {
                SocialMicMeter(active = granted)
            }
        }
        if (!joined) {
            SocialPrimaryButton("Join party", onClick = onJoin, modifier = Modifier.fillMaxWidth())
        } else {
            SocialPrimaryButton("Hold to talk", onClick = onMute, modifier = Modifier.fillMaxWidth())
            if (!granted) {
                SocialGhostButton(
                    "Allow microphone",
                    onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth(),
                    iconRes = R.drawable.aerix_social_ic_mic
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialGhostButton("Mute", onClick = onMute, modifier = Modifier.weight(1f), iconRes = R.drawable.aerix_social_ic_mic_off)
                SocialGhostButton("Audio settings", onClick = onMute, modifier = Modifier.weight(1f))
            }
        }
        SocialGhostButton("Leave party", onClick = onLeave, modifier = Modifier.fillMaxWidth())
        SocialBody("Leaving the party does not leave the game.", size = 11.sp, color = SocialColors.textMuted)
        Spacer(Modifier.height(8.dp))
    }
}
