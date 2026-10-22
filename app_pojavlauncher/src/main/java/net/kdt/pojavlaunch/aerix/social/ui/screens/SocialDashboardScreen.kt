/*
 * Aerix Launcher Social layer.
 *
 * Social hub (friends, requests, chats, party) and the chat thread. Every list reads the
 * ViewModel StateFlow; accept/decline/send update the repository and the UI follows.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.data.AerixUser
import net.kdt.pojavlaunch.aerix.social.data.ChatMessage
import net.kdt.pojavlaunch.aerix.social.data.SocialConnection
import net.kdt.pojavlaunch.aerix.social.data.UserStatus
import net.kdt.pojavlaunch.aerix.social.ui.SocialAvatar
import net.kdt.pojavlaunch.aerix.social.ui.SocialBody
import net.kdt.pojavlaunch.aerix.social.ui.SocialColors
import net.kdt.pojavlaunch.aerix.social.ui.SocialDimens
import net.kdt.pojavlaunch.aerix.social.ui.SocialGhostButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialPanel
import net.kdt.pojavlaunch.aerix.social.ui.SocialPrimaryButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialSectionTitle
import net.kdt.pojavlaunch.aerix.social.ui.SocialTitle
import net.kdt.pojavlaunch.aerix.social.vm.SocialChatViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialDashboardViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialTab

@Composable
fun SocialDashboardScreen(
    viewModel: SocialDashboardViewModel,
    tab: SocialTab,
    onTabSelected: (SocialTab) -> Unit,
    onMessage: (AerixUser) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenVoice: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = SocialDimens.pagePaddingH)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SocialTab.entries.forEach { entry ->
                val selected = entry == tab
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onTabSelected(entry) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (entry) {
                            SocialTab.FRIENDS -> "Friends"
                            SocialTab.CHATS -> "Chats"
                            SocialTab.PARTY -> "Party"
                        },
                        color = if (selected) SocialColors.textPrimary else SocialColors.textMuted,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(2.dp)
                            .background(if (selected) SocialColors.accent else Color.Transparent)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.connection == SocialConnection.ERROR) {
                SocialBody("Connection unavailable", size = 12.sp, color = SocialColors.danger)
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            state.connection == SocialConnection.LOADING && state.friends.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SocialBody("Loading your friends…", size = 14.sp)
                }
            }
            state.connection == SocialConnection.ERROR && state.friends.isEmpty() -> {
                SocialErrorState(onRetry = viewModel::refresh)
            }
            tab == SocialTab.PARTY -> SocialPartyTab(onOpenVoice = onOpenVoice, onJoin = viewModel::createParty, onLeave = viewModel::leaveParty)
            else -> SocialFriendsTab(
                friends = viewModel.visibleFriends,
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onMessage = onMessage,
                onAddFriend = viewModel::addFriend,
                pendingRequests = state.requests.size,
                onAccept = viewModel::accept,
                onDecline = viewModel::decline,
                sentTo = state.addFriendSentTo,
                onDismissSent = viewModel::dismissAddFriend,
                tab = tab,
                onOpenPrivacy = onOpenPrivacy,
                onOpenSafety = onOpenSafety
            )
        }
    }
}

@Composable
private fun SocialErrorState(onRetry: () -> Unit) {
    SocialPanel(modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_social_ic_friends),
                contentDescription = null,
                tint = SocialColors.textMuted,
                modifier = Modifier.size(44.dp)
            )
            SocialTitle("Could not connect to Social")
            SocialBody("Your friends list could not be loaded. Check your connection and try again.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialPrimaryButton("Retry", onClick = onRetry)
                SocialGhostButton("Back", onClick = {})
            }
            SocialBody("Your account and saved preferences have not been removed.", size = 11.sp, color = SocialColors.textMuted)
        }
    }
}

@Composable
private fun SocialFriendsTab(
    friends: List<AerixUser>,
    query: String,
    onQueryChange: (String) -> Unit,
    onMessage: (AerixUser) -> Unit,
    onAddFriend: (String) -> Unit,
    pendingRequests: Int,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    sentTo: String?,
    onDismissSent: () -> Unit,
    tab: SocialTab,
    onOpenPrivacy: () -> Unit,
    onOpenSafety: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 620.dp
        if (twoColumns) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1.7f).fillMaxHeight()) {
                    SocialSearchRow(query = query, onQueryChange = onQueryChange, onAddFriend = onAddFriend)
                    Spacer(Modifier.height(12.dp))
                    if (friends.isEmpty()) SocialEmptyState(onAddFriend = onAddFriend)
                    else FriendList(friends, onMessage)
                }
                Column(
                    modifier = Modifier.weight(0.8f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PartyCard(joined = false, onJoin = {}, onOpenVoice = {})
                    RequestsCard(pendingRequests, onOpenPrivacy, onOpenSafety)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SocialSearchRow(query = query, onQueryChange = onQueryChange, onAddFriend = onAddFriend)
                if (friends.isEmpty()) SocialEmptyState(onAddFriend = onAddFriend) else FriendList(friends, onMessage)
            }
        }
    }
    if (sentTo != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            net.kdt.pojavlaunch.aerix.social.ui.SocialDialog(onDismiss = onDismissSent) {
                SocialSectionTitle("Friend request sent")
                SocialBody("You can message $sentTo after they accept your request.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialPrimaryButton("Back to Social", onClick = onDismissSent, modifier = Modifier.weight(1f))
                    SocialGhostButton("Cancel request", onClick = onDismissSent, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SocialSearchRow(query: String, onQueryChange: (String) -> Unit, onAddFriend: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = SocialDimens.touch)
                .clip(CircleShape)
                .background(SocialColors.panel)
                .border(BorderStroke(1.dp, SocialColors.border), CircleShape)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = SocialColors.textPrimary, fontSize = 14.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) SocialBody("Search friends", size = 14.sp, color = SocialColors.textMuted)
                        inner()
                    }
                }
            )
            Icon(
                painter = painterResource(R.drawable.aerix_social_ic_friends),
                contentDescription = null,
                tint = SocialColors.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }
        SocialPrimaryButton(
            text = "Add friend",
            onClick = { if (query.length >= 3) onAddFriend(query) },
            enabled = query.length >= 3
        )
    }
}

@Composable
private fun FriendList(friends: List<AerixUser>, onMessage: (AerixUser) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(friends, key = { it.id }) { friend ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SocialDimens.row)
                    .clip(RoundedCornerShape(SocialDimens.controlRadius))
                    .background(SocialColors.panel)
                    .border(BorderStroke(1.dp, SocialColors.border), RoundedCornerShape(SocialDimens.controlRadius))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SocialAvatar(avatar = friend.avatar, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SocialBody(friend.name, color = SocialColors.textPrimary, size = 14.sp, weight = FontWeight.SemiBold)
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (friend.status == UserStatus.OFFLINE) SocialColors.textMuted else SocialColors.online)
                        )
                    }
                    SocialBody(friend.activity ?: statusLabel(friend.status), size = 12.sp, color = SocialColors.textMuted, maxLines = 1)
                }
                SocialGhostButton(text = "Message", onClick = { onMessage(friend) })
            }
        }
    }
}

private fun statusLabel(status: UserStatus): String = when (status) {
    UserStatus.ONLINE -> "Online in Aerix"
    UserStatus.PLAYING -> "Minecraft running"
    UserStatus.IN_VOICE -> "In a voice party"
    UserStatus.OFFLINE -> "Offline"
}

@Composable
private fun SocialEmptyState(onAddFriend: () -> Unit) {
    SocialPanel(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_social_ic_friends),
                contentDescription = null,
                tint = SocialColors.accentBright,
                modifier = Modifier.size(44.dp)
            )
            SocialTitle("Your friends list starts here")
            SocialBody("Add someone by their unique Aerix name to get started.")
            SocialPrimaryButton("Add friend", onClick = onAddFriend)
            SocialBody("Accepted friends can send private messages and party invitations.", size = 11.sp, color = SocialColors.textMuted)
        }
    }
}

@Composable
private fun PartyCard(joined: Boolean, onJoin: () -> Unit, onOpenVoice: () -> Unit) {
    SocialPanel(modifier = Modifier.fillMaxWidth()) {
        SocialSectionTitle("Your party")
        SocialBody(if (joined) "Connected" else "Not connected", size = 12.sp, color = SocialColors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialPrimaryButton(if (joined) "Open" else "Create party", onClick = if (joined) onOpenVoice else onJoin, modifier = Modifier.weight(1f))
            SocialGhostButton("Voice", onClick = onOpenVoice, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RequestsCard(pending: Int, onOpenPrivacy: () -> Unit, onOpenSafety: () -> Unit) {
    SocialPanel(modifier = Modifier.fillMaxWidth()) {
        SocialSectionTitle("Friend requests")
        SocialBody("$pending pending", size = 13.sp, color = SocialColors.textSecondary)
        SocialGhostButton("Privacy & presence", onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth())
        SocialGhostButton("Safety", onClick = onOpenSafety, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SocialPartyTab(onOpenVoice: () -> Unit, onJoin: () -> Unit, onLeave: () -> Unit) {
    SocialPanel(modifier = Modifier.fillMaxWidth()) {
        SocialSectionTitle("Voice party")
        SocialBody("Party audio needs a connection. Create a party to invite your friends.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SocialPrimaryButton("Create party", onClick = onJoin, modifier = Modifier.weight(1f))
            SocialGhostButton("Voice settings", onClick = onOpenVoice, modifier = Modifier.weight(1f))
        }
        SocialGhostButton("Leave party", onClick = onLeave, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SocialChatScreen(
    viewModel: SocialChatViewModel,
    onBack: () -> Unit,
    onVoice: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = SocialDimens.pagePaddingH)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SocialAvatar(avatar = state.friendAvatar, size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                SocialBody(state.friendName, size = 15.sp, color = SocialColors.textPrimary, weight = FontWeight.SemiBold)
                SocialBody("Playing Minecraft", size = 11.sp, color = SocialColors.textMuted)
            }
            SocialGhostButton("Invite to party", onClick = onVoice)
        }
        Spacer(Modifier.height(12.dp))
        if (state.messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SocialBody("Say hello to ${state.friendName}", size = 13.sp, color = SocialColors.textMuted)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        onReact = { emoji -> viewModel.react(message.id, emoji) }
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .clip(CircleShape)
                .background(SocialColors.panel)
                .border(BorderStroke(1.dp, SocialColors.border), CircleShape)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = state.draft,
                onValueChange = viewModel::onDraftChange,
                singleLine = true,
                textStyle = TextStyle(color = SocialColors.textPrimary, fontSize = 14.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (state.draft.isEmpty()) SocialBody("Message ${state.friendName}…", size = 14.sp, color = SocialColors.textMuted)
                        inner()
                    }
                }
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SocialColors.accent)
                    .clickable { viewModel.send() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_social_ic_send),
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, onReact: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.mine) 16.dp else 4.dp,
                        bottomEnd = if (message.mine) 4.dp else 16.dp
                    )
                )
                .background(if (message.mine) SocialColors.accent else SocialColors.panelRaised)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            SocialBody(message.text, size = 14.sp, color = Color.White)
        }
        SocialBody(message.timestamp, size = 10.sp, color = SocialColors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("👍", "❤️", "😂").forEach { emoji ->
                val count = message.reactions[emoji] ?: 0
                val mine = message.myReaction == emoji
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (mine) SocialColors.accent.copy(alpha = 0.35f) else SocialColors.panelSoft)
                        .clickable { onReact(emoji) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(emoji, fontSize = 11.sp)
                    if (count > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text("$count", color = SocialColors.textSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
