/*
 * Aerix Launcher — Social hub, chat and profile panels.
 *
 * UI only: every friend, message and call is simulated by SocialHubStore until the
 * Aerix account backend exists.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing

private val SocialTabs = listOf("Friends", "Chats", "Party")

/* ---------------------------------------------------------------------------------------- */
/* Main hub                                                                                 */
/* ---------------------------------------------------------------------------------------- */

@Composable
internal fun SocialHubPanel(
    onExit: () -> Unit,
    onChat: (String) -> Unit,
    onProfile: () -> Unit,
    onPrivacy: () -> Unit,
    onNotifications: () -> Unit,
    onVoice: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showAddFriend by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val tab = SocialHubStore.hubTab

    SocialPage {
        Column(modifier = Modifier.fillMaxSize()) {
            SocialHeader(title = "Social", onBack = onExit) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { menuOpen = true }
                            .padding(horizontal = AerixSpacing.xs, vertical = AerixSpacing.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SocialAvatar(size = 30, name = SocialProfileStore.aerixName, ring = true)
                        Spacer(Modifier.width(AerixSpacing.sm))
                        Text(
                            text = SocialProfileStore.aerixName,
                            color = SocialPalette.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(AerixSpacing.xs))
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_more_vert),
                            contentDescription = "Account menu",
                            tint = SocialPalette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("My profile") }, onClick = { menuOpen = false; onProfile() })
                        DropdownMenuItem(text = { Text("Privacy and presence") }, onClick = { menuOpen = false; onPrivacy() })
                        DropdownMenuItem(text = { Text("Notifications") }, onClick = { menuOpen = false; onNotifications() })
                        DropdownMenuItem(text = { Text("Voice chat") }, onClick = { menuOpen = false; onVoice() })
                    }
                }
            }

            Spacer(Modifier.height(SocialDimens.gap))
            SocialTabRow(selected = tab, onSelect = { SocialHubStore.hubTab = it })
            Spacer(Modifier.height(SocialDimens.gap))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    modifier = Modifier.weight(1f).height(50.dp),
                    singleLine = true,
                    shape = CircleShape,
                    placeholder = { Text("Search friends", color = SocialPalette.textMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_search),
                            contentDescription = null,
                            tint = SocialPalette.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SocialPalette.accent,
                        unfocusedBorderColor = SocialPalette.borderSoft,
                        focusedTextColor = SocialPalette.textPrimary,
                        unfocusedTextColor = SocialPalette.textPrimary,
                        cursorColor = SocialPalette.accent,
                        focusedContainerColor = SocialPalette.panelRaised.copy(alpha = 0.35f),
                        unfocusedContainerColor = SocialPalette.panelRaised.copy(alpha = 0.35f)
                    )
                )
                Spacer(Modifier.width(AerixSpacing.sm))
                SocialPrimaryButton(
                    text = "Add friend",
                    iconRes = null,
                    onClick = { showAddFriend = true }
                )
            }

            Spacer(Modifier.height(SocialDimens.gap))

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= 640.dp
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                    ) {
                        SocialTabContent(
                            tab = tab,
                            filter = filter,
                            modifier = Modifier.weight(1.65f).fillMaxHeight(),
                            onChat = onChat,
                            onAddFriend = { showAddFriend = true },
                            onProfile = onProfile
                        )
                        Column(
                            modifier = Modifier.weight(0.85f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                        ) {
                            PartySummaryCard(onOpenParty = { SocialHubStore.hubTab = 2 })
                            FriendRequestsCard()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                    ) {
                        SocialTabContent(
                            tab = tab,
                            filter = filter,
                            modifier = Modifier.fillMaxWidth(),
                            onChat = onChat,
                            onAddFriend = { showAddFriend = true },
                            onProfile = onProfile
                        )
                        PartySummaryCard(onOpenParty = { SocialHubStore.hubTab = 2 })
                        FriendRequestsCard()
                    }
                }
            }
        }
    }

    if (showAddFriend) {
        SocialAddFriendDialog(
            onDismiss = { showAddFriend = false },
            onSend = { name ->
                SocialHubStore.requests.add(SocialRequest(name, incoming = false))
                SocialHubStore.hubTab = 0
                showAddFriend = false
            }
        )
    }
}

/** Friends / Chats / Party, drawn as the underlined text tabs from the mockup. */
@Composable
private fun SocialTabRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.lg)) {
        SocialTabs.forEachIndexed { index, label ->
            val active = index == selected
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(AerixRadii.micro))
                    .clickable { onSelect(index) }
                    .padding(vertical = AerixSpacing.xs, horizontal = AerixSpacing.xxs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    color = if (active) SocialPalette.accent else SocialPalette.textSecondary,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(if (active) 26.dp else 0.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(SocialPalette.accent)
                )
            }
        }
    }
}

@Composable
private fun SocialTabContent(
    tab: Int,
    filter: String,
    modifier: Modifier,
    onChat: (String) -> Unit,
    onAddFriend: () -> Unit,
    onProfile: () -> Unit
) {
    when (tab) {
        1 -> ChatsList(modifier = modifier, onChat = onChat)
        2 -> PartyPage(modifier = modifier, onAddFriend = onAddFriend)
        else -> FriendsList(
            modifier = modifier,
            filter = filter,
            onChat = onChat,
            onAddFriend = onAddFriend,
            onProfile = onProfile
        )
    }
}

@Composable
private fun FriendsList(
    modifier: Modifier,
    filter: String,
    onChat: (String) -> Unit,
    onAddFriend: () -> Unit,
    onProfile: () -> Unit
) {
    val visible = SocialHubStore.friends.filter {
        filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true)
    }
    SocialCard(modifier = modifier, contentPadding = AerixSpacing.sm) {
        when (SocialHubStore.state) {
            SocialHubState.UNAVAILABLE -> {
                Text("Could not connect to Social", style = MaterialTheme.typography.titleMedium, color = SocialPalette.textPrimary)
                Text(
                    "Your friends list could not be loaded. Check your connection and try again.",
                    color = SocialPalette.textSecondary,
                    fontSize = 12.sp
                )
                SocialGhostButton(text = "Retry", iconRes = null, onClick = { SocialHubStore.state = SocialHubState.FRIENDS })
            }
            SocialHubState.EMPTY -> {
                Text("Your friends list starts here", style = MaterialTheme.typography.titleMedium, color = SocialPalette.textPrimary)
                Text(
                    "Add someone by their unique Aerix name to get started.",
                    color = SocialPalette.textSecondary,
                    fontSize = 12.sp
                )
                SocialPrimaryButton(text = "Add friend", iconRes = null, onClick = onAddFriend)
            }
            SocialHubState.FRIENDS -> {
                if (visible.isEmpty()) {
                    Text(
                        text = if (SocialHubStore.friends.isEmpty()) "No friends yet." else "No friends match \"${filter.trim()}\".",
                        color = SocialPalette.textSecondary,
                        fontSize = 13.sp
                    )
                }
                visible.forEach { friend ->
                    FriendRow(
                        friend = friend,
                        onMessage = { onChat(friend.name) },
                        onOpenProfile = {
                            SocialNavStore.friendName = friend.name
                            onProfile()
                        }
                    )
                    Spacer(Modifier.height(SocialDimens.gapSmall))
                }
            }
        }
    }
}

@Composable
private fun FriendRow(friend: SocialFriend, onMessage: () -> Unit, onOpenProfile: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AerixRadii.control),
        color = SocialPalette.panelRaised.copy(alpha = 0.34f),
        border = androidx.compose.foundation.BorderStroke(AerixSpacing.hairline, SocialPalette.borderSoft)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = SocialDimens.friendRow)
                .padding(horizontal = SocialDimens.cardPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                SocialAvatar(size = 40, name = friend.name)
                // Presence dot: green when online, grey when away.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (friend.online) Color(0xFF5BD98A) else SocialPalette.textMuted)
                        .border(androidx.compose.foundation.BorderStroke(1.5.dp, SocialPalette.canvas), CircleShape)
                )
            }
            Spacer(Modifier.width(SocialDimens.gap))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AerixRadii.micro))
                    .clickable(onClick = onOpenProfile)
                    .padding(vertical = AerixSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(friend.name, color = SocialPalette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    text = " — ${friend.presence.label}",
                    color = SocialPalette.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            SocialGhostButton(text = "Message", iconRes = null, onClick = onMessage)
        }
    }
}

@Composable
private fun ChatsList(modifier: Modifier, onChat: (String) -> Unit) {
    SocialCard(modifier = modifier, contentPadding = AerixSpacing.sm) {
        if (SocialHubStore.conversations.isEmpty()) {
            Text("No chats yet", style = MaterialTheme.typography.titleMedium, color = SocialPalette.textPrimary)
            Text("Open a friend to start a private chat.", color = SocialPalette.textSecondary, fontSize = 12.sp)
            return@SocialCard
        }
        SocialHubStore.conversations.toList().forEach { conversation ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AerixRadii.control),
                    color = SocialPalette.panelRaised.copy(alpha = 0.34f),
                    border = androidx.compose.foundation.BorderStroke(AerixSpacing.hairline, SocialPalette.borderSoft),
                    onClick = { onChat(conversation.name) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.smCompact),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SocialAvatar(size = 36, name = conversation.name)
                        Spacer(Modifier.width(AerixSpacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(conversation.name, color = SocialPalette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                SocialHubStore.lastMessage(conversation.name),
                                color = SocialPalette.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                        Text("›", color = SocialPalette.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            Spacer(Modifier.height(AerixSpacing.xs))
        }
    }
}

@Composable
private fun PartyPage(modifier: Modifier, onAddFriend: () -> Unit) {
    val party = SocialHubStore.party.value
    SocialCard(modifier = modifier, contentPadding = AerixSpacing.md) {
        if (party == null) {
            Text("Your party", style = MaterialTheme.typography.titleLarge, color = SocialPalette.textPrimary)
            Text("Not connected", color = SocialPalette.textSecondary, fontSize = 13.sp)
            Text(
                "A party lets you play and talk with friends. Voice is optional and you can stay muted.",
                color = SocialPalette.textMuted,
                fontSize = 12.sp
            )
            SocialPrimaryButton(text = "Create party", iconRes = null, onClick = { SocialHubStore.createParty() })
            return@SocialCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(party.name, style = MaterialTheme.typography.titleLarge, color = SocialPalette.textPrimary)
                Text(
                    "${party.members.size} in the party · Voice " + if (party.voiceConnected) "connected" else "not connected",
                    color = SocialPalette.textSecondary,
                    fontSize = 12.sp
                )
            }
            SocialGhostButton(
                text = if (party.voiceConnected) "Leave voice" else "Join voice",
                iconRes = null,
                onClick = { party.voiceConnected = !party.voiceConnected }
            )
        }
        HorizontalDivider(color = SocialPalette.borderSoft)
        party.members.toList().forEach { member ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SocialAvatar(size = 32, name = member)
                Spacer(Modifier.width(AerixSpacing.sm))
                Text(member, modifier = Modifier.weight(1f), color = SocialPalette.textPrimary, fontSize = 13.sp)
                Text("In party", color = SocialPalette.textMuted, fontSize = 11.sp)
            }
        }
        val invitable = SocialHubStore.friends.filter { it.name !in party.members }
        if (invitable.isNotEmpty()) {
            Text("Invite a friend", color = SocialPalette.textSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                invitable.take(4).forEach { friend ->
                    SocialGhostButton(text = friend.name, iconRes = null, onClick = { SocialHubStore.invite(friend.name) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            SocialGhostButton(text = "Add friend", iconRes = null, onClick = onAddFriend)
            SocialGhostButton(text = "Leave party", iconRes = null, onClick = { SocialHubStore.leaveParty() })
        }
    }
}

@Composable
private fun PartySummaryCard(onOpenParty: () -> Unit) {
    val party = SocialHubStore.party.value
    SocialCard(contentPadding = AerixSpacing.md) {
        Text("Your party", style = MaterialTheme.typography.titleMedium, color = SocialPalette.textPrimary)
        if (party == null) {
            Text("Not connected", color = SocialPalette.textSecondary, fontSize = 12.sp)
            SocialPrimaryButton(text = "Create party", iconRes = null, onClick = { SocialHubStore.createParty() })
        } else {
            Text(party.name, color = SocialPalette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("${party.members.size} in the party", color = SocialPalette.textSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                party.members.take(3).forEach { SocialAvatar(size = 26, name = it) }
            }
            SocialGhostButton(text = "Open party", iconRes = null, onClick = onOpenParty)
        }
    }
}

@Composable
private fun FriendRequestsCard() {
    val pending = SocialHubStore.requests.filter { it.incoming }
    SocialCard(contentPadding = AerixSpacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Friend requests", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = SocialPalette.textPrimary)
            if (pending.isNotEmpty()) {
                Text(
                    text = "${pending.size} pending",
                    color = SocialPalette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SocialPalette.accent.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        if (pending.isEmpty()) {
            Text("No pending requests", color = SocialPalette.textSecondary, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                pending.forEach { request ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SocialAvatar(size = 30, name = request.name)
                        Spacer(Modifier.width(AerixSpacing.sm))
                        Text(request.name, modifier = Modifier.weight(1f), color = SocialPalette.textPrimary, fontSize = 13.sp)
                        SocialGhostButton(text = "Decline", iconRes = null, onClick = { SocialHubStore.decline(request) })
                        Spacer(Modifier.width(AerixSpacing.xxs))
                        SocialPrimaryButton(text = "Accept", iconRes = null, onClick = { SocialHubStore.accept(request) })
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------- */
/* Chat                                                                                     */
/* ---------------------------------------------------------------------------------------- */

@Composable
internal fun SocialChatPanel(onBack: () -> Unit, onOpenProfile: () -> Unit) {
    val name = SocialNavStore.friendName ?: SocialProfileStore.aerixName
    var draft by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var callState by remember { mutableStateOf("Voice call") }
    val conversation = SocialHubStore.conversation(name)
    val listState = rememberLazyListState()
    val count = conversation?.messages?.size ?: 0

    LaunchedEffect(count) {
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    SocialPage {
        Column(modifier = Modifier.fillMaxSize()) {
            SocialHeader(
                title = name,
                onBack = onBack,
                trailing = {
                    Box {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_more_vert),
                            contentDescription = "Chat options",
                            tint = SocialPalette.textSecondary,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { menuOpen = true }
                                .padding(6.dp)
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Friend profile") }, onClick = { menuOpen = false; onOpenProfile() })
                            DropdownMenuItem(
                                text = { Text("Report player") },
                                onClick = { menuOpen = false; SocialHubStore.report(name) }
                            )
                            DropdownMenuItem(
                                text = { Text("Block player") },
                                onClick = { menuOpen = false; SocialHubStore.block(name); onBack() }
                            )
                        }
                    }
                }
            )

            SocialCard(modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = AerixSpacing.sm) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                ) {
                    itemsIndexed(
                        conversation?.messages?.toList() ?: emptyList(),
                        key = { index, _ -> index }
                    ) { index, message ->
                        ChatBubble(
                            message = message,
                            onReact = { emoji -> SocialHubStore.react(name, index, emoji) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(AerixSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SocialGhostButton(
                    text = "Invite to server",
                    iconRes = null,
                    onClick = { showInvite = true }
                )
                Spacer(Modifier.width(AerixSpacing.xs))
                SocialGhostButton(text = callState, iconRes = R.drawable.ic_microphone, onClick = { callState = "Calling…" })
            }
            Spacer(Modifier.height(AerixSpacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(500) },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    shape = CircleShape,
                    placeholder = { Text("Message $name", color = SocialPalette.textMuted, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SocialPalette.accent,
                        unfocusedBorderColor = SocialPalette.borderSoft,
                        focusedTextColor = SocialPalette.textPrimary,
                        unfocusedTextColor = SocialPalette.textPrimary,
                        cursorColor = SocialPalette.accent,
                        focusedContainerColor = SocialPalette.panelRaised.copy(alpha = 0.35f),
                        unfocusedContainerColor = SocialPalette.panelRaised.copy(alpha = 0.35f)
                    )
                )
                Spacer(Modifier.width(AerixSpacing.sm))
                SocialPrimaryButton(
                    text = "Send",
                    iconRes = null,
                    enabled = draft.isNotBlank(),
                    onClick = {
                        SocialHubStore.sendMessage(name, draft)
                        draft = ""
                    }
                )
            }
        }
    }

    if (showInvite) {
        SocialChoiceDialog(
            title = "Invite $name",
            subtitle = "Send an invitation without leaving the chat.",
            options = listOf("Invite to your world", "Invite to your server", "Invite to your party"),
            selected = -1,
            onSelect = { index ->
                if (index == 2) SocialHubStore.invite(name)
                SocialHubStore.sendMessage(
                    name,
                    when (index) {
                        0 -> "I invited you to my world."
                        1 -> "I invited you to my server."
                        else -> "I invited you to my party."
                    }
                )
                showInvite = false
            },
            onDismiss = { showInvite = false }
        )
    }
}

@Composable
private fun ChatBubble(message: SocialMessage, onReact: (String) -> Unit) {
    val alignment = if (message.fromMe) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.78f)
        ) {
            Surface(
                shape = RoundedCornerShape(AerixRadii.card),
                color = if (message.fromMe) SocialPalette.accent.copy(alpha = 0.24f) else SocialPalette.panelRaised.copy(alpha = 0.62f),
                border = androidx.compose.foundation.BorderStroke(AerixSpacing.hairline, SocialPalette.borderSoft)
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.sm),
                    color = SocialPalette.textPrimary,
                    fontSize = 13.sp
                )
            }
            Row(
                modifier = Modifier.padding(top = AerixSpacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                message.reactions.forEach { (emoji, count) ->
                    Text(
                        text = "$emoji $count",
                        color = SocialPalette.textSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SocialPalette.panelTrack)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = "＋",
                    color = SocialPalette.textMuted,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SocialPalette.panelRaised.copy(alpha = 0.4f))
                        .clickable { onReact("👍") }
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------- */
/* Profile                                                                                  */
/* ---------------------------------------------------------------------------------------- */

@Composable
internal fun SocialProfilePanel(
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onNotifications: () -> Unit,
    onSignedOut: () -> Unit
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var showSkinPicker by remember { mutableStateOf(false) }
    var showCapePicker by remember { mutableStateOf(false) }

    SocialPage {
        Column(modifier = Modifier.fillMaxSize()) {
            SocialHeader(title = "My profile", onBack = onBack)
            Spacer(Modifier.height(AerixSpacing.sm))
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                if (maxWidth >= 640.dp) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                    ) {
                        SocialCard(modifier = Modifier.weight(0.8f).fillMaxHeight(), contentPadding = AerixSpacing.md) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                SocialAvatar(size = 96, name = SocialProfileStore.aerixName, ring = true)
                            }
                            Text(
                                text = SocialProfileStore.aerixName,
                                style = MaterialTheme.typography.titleLarge,
                                color = SocialPalette.textPrimary
                            )
                            Text(
                                text = "Skin: ${SocialProfileStore.skinName} · Cape: ${SocialProfileStore.capeName}",
                                color = SocialPalette.textSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = SocialProfileStore.googleEmail?.let { "Connected as $it" } ?: "Google not connected",
                                color = SocialPalette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        SocialCard(modifier = Modifier.weight(1.2f).fillMaxHeight(), contentPadding = AerixSpacing.md) {
                            ProfileActions(
                                onEditName = { editName = true },
                                onChooseSkin = { showSkinPicker = true },
                                onChooseCape = { showCapePicker = true },
                                onPrivacy = onPrivacy,
                                onNotifications = onNotifications,
                                onSignOut = { confirmSignOut = true },
                                onDelete = { confirmDelete = true }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                    ) {
                        SocialCard(modifier = Modifier.fillMaxWidth(), contentPadding = AerixSpacing.md) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SocialAvatar(size = 64, name = SocialProfileStore.aerixName, ring = true)
                                Spacer(Modifier.width(AerixSpacing.md))
                                Column {
                                    Text(
                                        text = SocialProfileStore.aerixName,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = SocialPalette.textPrimary
                                    )
                                    Text(
                                        text = "Skin: ${SocialProfileStore.skinName} · Cape: ${SocialProfileStore.capeName}",
                                        color = SocialPalette.textSecondary,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = SocialProfileStore.googleEmail?.let { "Connected as $it" } ?: "Google not connected",
                                        color = SocialPalette.textMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                        SocialCard(modifier = Modifier.fillMaxWidth(), contentPadding = AerixSpacing.md) {
                            ProfileActions(
                                onEditName = { editName = true },
                                onChooseSkin = { showSkinPicker = true },
                                onChooseCape = { showCapePicker = true },
                                onPrivacy = onPrivacy,
                                onNotifications = onNotifications,
                                onSignOut = { confirmSignOut = true },
                                onDelete = { confirmDelete = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (editName) {
        SocialNameDialog(
            current = SocialProfileStore.aerixName,
            onDismiss = { editName = false },
            onSave = {
                SocialProfileStore.aerixName = it
                editName = false
            }
        )
    }
    if (confirmSignOut) {
        SocialChoiceDialog(
            title = "Sign out of Aerix?",
            subtitle = "Your friends and chats stay on your account.",
            options = listOf("Sign out on this device"),
            selected = -1,
            onSelect = {
                confirmSignOut = false
                onSignedOut()
            },
            onDismiss = { confirmSignOut = false }
        )
    }
}

@Composable
private fun ProfileActions(
    onEditName: () -> Unit,
    onChooseSkin: () -> Unit,
    onChooseCape: () -> Unit,
    onPrivacy: () -> Unit,
    onNotifications: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
    ) {
        SocialGhostButton(text = "Edit name", iconRes = null, onClick = onEditName)
        SocialGhostButton(text = "Change skin", iconRes = null, onClick = onChooseSkin)
        SocialGhostButton(text = "Change cape", iconRes = null, onClick = onChooseCape)
        SocialGhostButton(text = "Privacy and presence", iconRes = null, onClick = onPrivacy)
        SocialGhostButton(text = "Notifications", iconRes = null, onClick = onNotifications)
        HorizontalDivider(color = SocialPalette.borderSoft)
        Text(
            text = "Safety",
            color = SocialPalette.textPrimary,
            fontSize = SocialType.section,
            fontWeight = FontWeight.SemiBold
        )
        if (SocialHubStore.blocked.isEmpty()) {
            Text(
                text = "Nobody is blocked. Block a player from their message menu.",
                color = SocialPalette.textSecondary,
                fontSize = SocialType.secondary
            )
        } else {
            SocialHubStore.blocked.forEach { name ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = SocialDimens.listRowMin),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                ) {
                    SocialAvatar(size = 36, name = name)
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        color = SocialPalette.textPrimary,
                        fontSize = SocialType.body
                    )
                    SocialGhostButton(
                        text = "Unblock",
                        iconRes = null,
                        onClick = { SocialHubStore.unblock(name) }
                    )
                }
            }
        }
        if (SocialHubStore.reported.isNotEmpty()) {
            Text(
                text = "Reported: ${SocialHubStore.reported.joinToString(", ")}",
                color = SocialPalette.textMuted,
                fontSize = SocialType.caption
            )
        }
        HorizontalDivider(color = SocialPalette.borderSoft)
        SocialGhostButton(text = "Sign out", iconRes = null, onClick = onSignOut)
        SocialGhostButton(text = "Delete account", iconRes = null, onClick = onDelete)
        Text(
            text = "Aerix accounts are separate from your Minecraft account.",
            color = SocialPalette.textMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SocialNameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    val valid = isValidAerixName(name.trim())
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        SocialCard(modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 360.dp).then(socialDialogModifier()), contentPadding = AerixSpacing.md) {
            Text("Edit Aerix name", style = MaterialTheme.typography.titleLarge, color = SocialPalette.textPrimary)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(16) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SocialPalette.accent,
                    unfocusedBorderColor = SocialPalette.borderSoft,
                    focusedTextColor = SocialPalette.textPrimary,
                    unfocusedTextColor = SocialPalette.textPrimary,
                    cursorColor = SocialPalette.accent
                )
            )
            Text("3 to 16 letters, numbers, or underscores.", color = SocialPalette.textMuted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                SocialPrimaryButton(text = "Save", iconRes = null, enabled = valid, onClick = { onSave(name.trim()) })
                SocialGhostButton(text = "Cancel", iconRes = null, onClick = onDismiss)
            }
        }
    }
}
