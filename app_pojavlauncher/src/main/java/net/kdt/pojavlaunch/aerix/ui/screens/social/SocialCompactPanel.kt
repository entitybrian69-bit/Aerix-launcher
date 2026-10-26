/*
 * Aerix Launcher — compact Social tab for the in-game overlay. Message, react, call and
 * invite without leaving the game, using the same simulated session as the launcher hub.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.screens.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing

/**
 * The in-game Social tab. [onBack] returns to the rest of the game menu, [onCloseOverlay]
 * lets the player jump straight back into the game.
 */
@Composable
fun SocialCompactPanel(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onCloseOverlay: () -> Unit
) {
    var openChat by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    val chatName = openChat

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AerixSpacing.mdPlus, vertical = AerixSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { if (chatName != null) openChat = null else onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_arrow_back),
                    contentDescription = "Back",
                    tint = SocialPalette.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(AerixSpacing.sm))
            Text(
                text = chatName ?: "Social",
                style = MaterialTheme.typography.titleMedium,
                color = SocialPalette.textPrimary
            )
            Spacer(Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = SocialPalette.accent.copy(alpha = 0.18f),
                onClick = onCloseOverlay
            ) {
                Text(
                    text = "Back to game",
                    modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.xs),
                    color = SocialPalette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(AerixSpacing.sm))

        if (chatName != null) {
            CompactChat(name = chatName, modifier = Modifier.fillMaxSize())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.lg)) {
                listOf("Friends", "Party").forEachIndexed { index, label ->
                    val active = tab == index
                    Text(
                        text = label,
                        color = if (active) SocialPalette.accent else SocialPalette.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(AerixRadii.micro))
                            .clickable { tab = index }
                            .padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(AerixSpacing.sm))
            if (tab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                ) {
                    items(SocialHubStore.friends, key = { it.name }) { friend ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AerixRadii.control),
                            color = SocialPalette.panelRaised.copy(alpha = 0.4f),
                            border = BorderStroke(AerixSpacing.hairline, SocialPalette.borderSoft),
                            onClick = { openChat = friend.name }
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SocialMinecraftHead(size = 34, name = friend.name)
                                Spacer(Modifier.width(AerixSpacing.sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(friend.name, color = SocialPalette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(friend.presence.label, color = SocialPalette.textSecondary, fontSize = 11.sp)
                                }
                                Text("Message", color = SocialPalette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                val party = SocialHubStore.party.value
                Column(verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                    if (party == null) {
                        Text("Not connected", color = SocialPalette.textSecondary, fontSize = 12.sp)
                        Surface(
                            shape = CircleShape,
                            color = SocialPalette.accent,
                            onClick = { SocialHubStore.createParty() }
                        ) {
                            Text(
                                text = "Create party",
                                modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.xs),
                                color = SocialPalette.onAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(party.name, color = SocialPalette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${party.members.size} in the party", color = SocialPalette.textSecondary, fontSize = 11.sp)
                        party.members.forEach { member ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SocialMinecraftHead(size = 28, name = member)
                                Spacer(Modifier.width(AerixSpacing.sm))
                                Text(member, modifier = Modifier.weight(1f), color = SocialPalette.textPrimary, fontSize = 12.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                            val invitable = SocialHubStore.friends.firstOrNull { it.name !in party.members }
                            if (invitable != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = SocialPalette.panelRaised.copy(alpha = 0.5f),
                                    border = BorderStroke(AerixSpacing.hairline, SocialPalette.borderSoft),
                                    onClick = { SocialHubStore.invite(invitable.name) }
                                ) {
                                    Text(
                                        text = "Invite ${invitable.name}",
                                        modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.xs),
                                        color = SocialPalette.textPrimary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = SocialPalette.panelRaised.copy(alpha = 0.5f),
                                border = BorderStroke(AerixSpacing.hairline, SocialPalette.borderSoft),
                                onClick = { SocialHubStore.leaveParty() }
                            ) {
                                Text(
                                    text = "Leave",
                                    modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.xs),
                                    color = SocialPalette.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactChat(name: String, modifier: Modifier = Modifier) {
    var draft by remember { mutableStateOf("") }
    var calling by remember { mutableStateOf(false) }
    var invited by remember { mutableStateOf(false) }
    val conversation = SocialHubStore.conversation(name)
    val listState = rememberLazyListState()
    val count = conversation?.messages?.size ?: 0

    LaunchedEffect(count) {
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
        ) {
            itemsIndexed(conversation?.messages?.toList() ?: emptyList()) { index, message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start
                ) {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(AerixRadii.control),
                            color = if (message.fromMe) SocialPalette.accent.copy(alpha = 0.24f) else SocialPalette.panelRaised.copy(alpha = 0.6f),
                            border = BorderStroke(AerixSpacing.hairline, SocialPalette.borderSoft)
                        ) {
                            Text(
                                text = message.text,
                                modifier = Modifier.padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.xs),
                                color = SocialPalette.textPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(AerixSpacing.xs))
                    Text(
                        text = if (message.reactions.isEmpty()) "＋" else message.reactions.joinToString(" ") { "${it.first}${it.second}" },
                        color = SocialPalette.textMuted,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { SocialHubStore.react(name, index, "👍") }
                            .padding(2.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
        ) {
            Surface(
                shape = CircleShape,
                color = SocialPalette.panelRaised.copy(alpha = 0.5f),
                border = BorderStroke(AerixSpacing.hairline, SocialPalette.borderSoft),
                onClick = {
                    SocialHubStore.sendMessage(name, "I invited you to my world.")
                    invited = true
                }
            ) {
                Text(
                    text = if (invited) "Invited to world" else "Invite to world",
                    modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.xs),
                    color = SocialPalette.textPrimary,
                    fontSize = 11.sp
                )
            }
            Surface(
                shape = CircleShape,
                color = SocialPalette.panelRaised.copy(alpha = 0.5f),
                border = BorderStroke(AerixSpacing.hairline, SocialPalette.borderSoft),
                onClick = { calling = !calling }
            ) {
                Text(
                    text = if (calling) "Calling…" else "Voice call",
                    modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.xs),
                    color = if (calling) SocialPalette.accent else SocialPalette.textPrimary,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(AerixSpacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft,
                onValueChange = { draft = it.take(300) },
                modifier = Modifier.weight(1f).height(52.dp),
                singleLine = true,
                placeholder = { Text("Message $name", fontSize = 11.sp, color = SocialPalette.textMuted) },
                keyboardActions = KeyboardActions(onSend = {
                    SocialHubStore.sendMessage(name, draft)
                    draft = ""
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SocialPalette.panelRaised.copy(alpha = 0.35f),
                    unfocusedContainerColor = SocialPalette.panelRaised.copy(alpha = 0.35f),
                    focusedTextColor = SocialPalette.textPrimary,
                    unfocusedTextColor = SocialPalette.textPrimary,
                    cursorColor = SocialPalette.accent,
                    focusedIndicatorColor = SocialPalette.accent,
                    unfocusedIndicatorColor = SocialPalette.borderSoft
                )
            )
            Spacer(Modifier.width(AerixSpacing.xs))
            Surface(
                shape = CircleShape,
                color = if (draft.isBlank()) SocialPalette.accent.copy(alpha = 0.4f) else SocialPalette.accent,
                enabled = draft.isNotBlank(),
                onClick = {
                    SocialHubStore.sendMessage(name, draft)
                    draft = ""
                }
            ) {
                Text(
                    text = "Send",
                    modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
                    color = SocialPalette.onAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
