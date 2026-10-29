/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Socials inside the game: friends and messages in the game menu, over the running world.
 *
 * This is the page no other Android launcher has - you read and answer a friend without leaving
 * Minecraft, and the game keeps rendering behind it. It uses the same SocialBackend as the
 * launcher's Socials tab, so a conversation started there continues here.
 *
 * Drawn on the game menu's dark glass so it belongs to the menu it opens from, with the accent of
 * the current launcher theme.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.social.SocialFriend
import net.kdt.pojavlaunch.aerix.social.SocialMessage
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PanelBackground = Color(0xF20B0B0E)
private val PanelLine = Color(0x33FFFFFF)
private val IncomingBubble = Color(0xFF23232A)

/**
 * Friends and messages over the running game.
 *
 * @param accent the launcher accent, so the panel matches the menu it opened from
 * @param onBack leaves the panel and returns to the game menu
 */
@Composable
fun GameSocialsScreen(accent: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<SocialFriend?>(null) }
    var messages by remember { mutableStateOf<List<SocialMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(SocialBackend.signedIn) }
    val friends = SocialBackend.friends

    // Attach once; the launcher may have been in the background for hours while the game ran.
    LaunchedEffect(Unit) {
        SocialBackend.attach(context)
        ready = SocialBackend.signedIn
        if (ready) {
            SocialBackend.rememberMinecraftName(PojavAccountBridge.current?.username)
            SocialBackend.loadProfile()
            SocialBackend.refreshFriends()
        }
    }

    // Presence and the friend list stay fresh for as long as the panel is open.
    LaunchedEffect(ready) {
        while (ready) {
            SocialBackend.heartbeat()
            delay(20_000)
            SocialBackend.refreshFriends()
        }
    }

    // One conversation at a time, polled slowly enough to stay free.
    LaunchedEffect(selected?.id) {
        val friend = selected ?: return@LaunchedEffect
        while (true) {
            messages = SocialBackend.loadMessages(friend.id)
            delay(3_000)
        }
    }

    Surface(
        shape = RoundedCornerShape(AerixRadii.panel),
        color = PanelBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(AerixSpacing.mdPlus)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color(0x14FFFFFF),
                    onClick = { if (selected != null) selected = null else onBack() }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_arrow_back),
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.padding(AerixSpacing.sm).size(18.dp)
                    )
                }
                Spacer(Modifier.width(AerixSpacing.smPlus))
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_people),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(AerixSpacing.sm))
                Column {
                    Text(
                        "Socials",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (ready) "${friends.count { it.online }} online · the game keeps running"
                        else "Sign in to Socials in the launcher first",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(AerixSpacing.md))

            if (!ready) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Socials is not signed in.\nOpen Socials in the launcher once, then come back here.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
                return@Column
            }

            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)) {
                // Left: who is around.
                Column(
                    modifier = Modifier.width(230.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                ) {
                    if (friends.isEmpty()) {
                        Text(
                            "No friends yet. Add one in the launcher's Socials page.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(friends) { friend ->
                            val isSelected = friend.id == selected?.id
                            Surface(
                                shape = RoundedCornerShape(AerixRadii.control),
                                color = if (isSelected) accent.copy(alpha = 0.22f) else Color(0x0DFFFFFF),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) accent.copy(alpha = 0.5f) else PanelLine
                                ),
                                onClick = { selected = friend },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = AerixSpacing.sm, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GameHead(friend.mcUsername, 26)
                                    Spacer(Modifier.width(AerixSpacing.sm))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            friend.mcUsername,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            when {
                                                friend.pendingIncoming -> "wants to be friends"
                                                friend.pendingOutgoing -> "request sent"
                                                friend.online -> "online"
                                                else -> "offline"
                                            },
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 10.sp
                                        )
                                    }
                                    if (!friend.pendingIncoming && !friend.pendingOutgoing) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (friend.online) Color(0xFF3DA35D) else Color(0xFF6B6B70))
                                        )
                                    }
                                }
                            }
                            if (friend.pendingIncoming) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(shape = CircleShape, color = accent, onClick = {
                                        scope.launch { SocialBackend.answerRequest(friend.id, true) }
                                    }) {
                                        Text(
                                            "Accept",
                                            color = Color.Black,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(shape = CircleShape, color = Color(0x1AFFFFFF), onClick = {
                                        scope.launch { SocialBackend.answerRequest(friend.id, false) }
                                    }) {
                                        Text(
                                            "Decline",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Right: the conversation.
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val friend = selected
                    if (friend == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Pick a friend to message them without leaving the game.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        return@Column
                    }
                    val listState = rememberLazyListState()
                    LaunchedEffect(messages.size) {
                        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(messages) { message ->
                            val mine = message.sender == SocialBackend.myUserId()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                            ) {
                                Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (mine) accent else IncomingBubble
                                    ) {
                                        Text(
                                            message.body,
                                            color = if (mine) Color.Black else Color.White,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                    Text(
                                        formatTime(message.atMs),
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                        if (messages.isEmpty()) {
                            item {
                                Text(
                                    "Say hello to ${friend.mcUsername}.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(AerixSpacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = { Text("Message…", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(AerixRadii.control),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = accent,
                                unfocusedBorderColor = PanelLine,
                                cursorColor = accent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(shape = CircleShape, color = accent, onClick = {
                            val text = draft
                            if (text.isNotBlank()) {
                                draft = ""
                                scope.launch {
                                    SocialBackend.sendMessage(friend.id, text)
                                    messages = SocialBackend.loadMessages(friend.id)
                                }
                            }
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_send),
                                contentDescription = "Send",
                                tint = Color.Black,
                                modifier = Modifier.padding(11.dp).size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHead(name: String?, size: Int) {
    var face by remember(name) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(name) {
        if (name.isNullOrBlank()) return@LaunchedEffect
        face = withContext(Dispatchers.IO) {
            PojavAccountBridge.loadSkinFacePreview(
                cacheKey = "social_$name",
                localFile = null,
                avatarUrl = "https://mc-heads.net/avatar/$name/64"
            )
        }
    }
    AerixImageWell(
        image = face,
        fallback = R.drawable.aerix_ic_person_outlined,
        size = size.dp,
        radius = AerixRadii.control
    )
}

private fun formatTime(ms: Long): String =
    if (ms <= 0) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
