/*
 * Aerix Launcher Social layer.
 *
 * Dashboard: who is online and offline, friend requests, the party tab and the entry point to
 * the chat. Chat supports images, GIFs, files of any size, links, shared instances and music.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.data.AerixUser
import net.kdt.pojavlaunch.aerix.social.data.AttachmentKind
import net.kdt.pojavlaunch.aerix.social.data.ChatAttachment
import net.kdt.pojavlaunch.aerix.social.data.ChatMessage
import net.kdt.pojavlaunch.aerix.social.data.InstanceShareState
import net.kdt.pojavlaunch.aerix.social.data.PartyState
import net.kdt.pojavlaunch.aerix.social.data.SocialConnection
import net.kdt.pojavlaunch.aerix.social.data.UserStatus
import net.kdt.pojavlaunch.aerix.social.media.SocialAudioPlayer
import net.kdt.pojavlaunch.aerix.social.media.SocialInstances
import net.kdt.pojavlaunch.aerix.social.media.SocialMic
import net.kdt.pojavlaunch.aerix.social.media.SocialMedia
import net.kdt.pojavlaunch.aerix.social.ui.SocialAvatar
import net.kdt.pojavlaunch.aerix.social.ui.SocialBody
import net.kdt.pojavlaunch.aerix.social.ui.SocialColors
import net.kdt.pojavlaunch.aerix.social.ui.SocialDimens
import net.kdt.pojavlaunch.aerix.social.ui.SocialGhostButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialIconButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialMotion
import net.kdt.pojavlaunch.aerix.social.ui.SocialPanel
import net.kdt.pojavlaunch.aerix.social.ui.SocialPrimaryButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialSectionTitle
import net.kdt.pojavlaunch.aerix.social.ui.SocialDialog
import net.kdt.pojavlaunch.aerix.social.ui.SocialMicMeter
import net.kdt.pojavlaunch.aerix.social.ui.SocialTextField
import net.kdt.pojavlaunch.aerix.social.ui.SocialTitle
import net.kdt.pojavlaunch.aerix.social.vm.SocialChatViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialDashboardViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialTab

@Composable
fun SocialDashboardScreen(
    viewModel: SocialDashboardViewModel,
    onMessage: (AerixUser) -> Unit,
    onAddFriend: () -> Unit,
    onActivity: () -> Unit,
    onOpenParty: () -> Unit,
    forcePartyTab: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(if (forcePartyTab) SocialTab.PARTY else SocialTab.FRIENDS) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = SocialDimens.pagePaddingH)) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            SocialTab.entries.forEach { entry ->
                val selected = entry == tab
                // Colour and underline animate instead of snapping, so switching tabs feels alive.
                val labelColor by animateColorAsState(
                    targetValue = if (selected) SocialColors.textPrimary else SocialColors.textMuted,
                    animationSpec = tween(SocialMotion.TAB_MS),
                    label = "tabLabel"
                )
                val underlineWidth by animateDpAsState(
                    targetValue = if (selected) 22.dp else 0.dp,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
                    label = "tabUnderline"
                )
                val underlineColor by animateColorAsState(
                    targetValue = if (selected) SocialColors.accent else androidx.compose.ui.graphics.Color.Transparent,
                    animationSpec = tween(SocialMotion.TAB_MS),
                    label = "tabUnderlineColor"
                )
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { tab = entry }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (entry) {
                            SocialTab.FRIENDS -> "Friends"
                            SocialTab.CHATS -> "Chats"
                            SocialTab.PARTY -> "Party"
                        },
                        color = labelColor,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(underlineWidth)
                            .height(2.dp)
                            .background(underlineColor)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.connection == SocialConnection.ERROR) {
                SocialBody("Connection unavailable", size = 12.sp, color = SocialColors.danger)
            }
        }
        Spacer(Modifier.height(12.dp))

        Crossfade(targetState = tab, animationSpec = tween(SocialMotion.TAB_MS), label = "socialTab") { activeTab ->
            when {
                state.connection == SocialConnection.LOADING && state.friends.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SocialBody("Loading your friends…", size = 14.sp)
                    }
                }
                state.connection == SocialConnection.ERROR && state.friends.isEmpty() -> {
                    SocialErrorState(onRetry = viewModel::refresh)
                }
                activeTab == SocialTab.PARTY -> PartyTab(
                    party = state.party,
                    onCreate = { name, logo -> viewModel.createParty(name, logo) },
                    onLeave = viewModel::leaveParty,
                    onOpenVoice = onOpenParty
                )
                activeTab == SocialTab.CHATS -> ChatListTab(
                    friends = state.friends,
                    onMessage = onMessage,
                    onAddFriend = onAddFriend
                )
                else -> FriendsTab(
                    online = state.online,
                    offline = state.offline,
                    requests = state.requests,
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onMessage = onMessage,
                    onAddFriend = onAddFriend,
                    onActivity = onActivity,
                    onAccept = viewModel::accept,
                    onDecline = viewModel::decline
                )
            }
        }
    }
}

@Composable
private fun FriendsTab(
    online: List<AerixUser>,
    offline: List<AerixUser>,
    requests: List<net.kdt.pojavlaunch.aerix.social.data.FriendRequest>,
    query: String,
    onQueryChange: (String) -> Unit,
    onMessage: (AerixUser) -> Unit,
    onAddFriend: () -> Unit,
    onActivity: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    if (online.isEmpty() && offline.isEmpty()) {
        NoFriendsState(onAddFriend = onAddFriend, onActivity = onActivity)
        return
    }
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1.6f).fillMaxHeight()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    SocialTextField(value = query, onValueChange = onQueryChange, placeholder = "Search friends")
                }
                SocialPrimaryButton("Add friend", onClick = onAddFriend, iconRes = R.drawable.aerix_social_ic_add_person)
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (online.isNotEmpty()) {
                    item { SocialBody("ONLINE — ${online.size}", size = 11.sp, color = SocialColors.textMuted, weight = FontWeight.SemiBold) }
                    items(online, key = { it.id }) { friend -> FriendRow(friend, onMessage) }
                }
                if (offline.isNotEmpty()) {
                    item { SocialBody("OFFLINE — ${offline.size}", size = 11.sp, color = SocialColors.textMuted, weight = FontWeight.SemiBold) }
                    items(offline, key = { it.id }) { friend -> FriendRow(friend, onMessage) }
                }
            }
        }
        Column(
            modifier = Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RequestsCard(requests, onAccept, onDecline)
            SocialPanel(modifier = Modifier.fillMaxWidth()) {
                SocialSectionTitle("Activity")
                SocialBody("See what your friends posted, follow them and react to their stories.", size = 12.sp, color = SocialColors.textMuted)
                SocialGhostButton("Open activity", onClick = onActivity, iconRes = R.drawable.aerix_social_ic_activity, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun NoFriendsState(onAddFriend: () -> Unit, onActivity: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SocialPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_social_ic_friends),
                    contentDescription = null,
                    tint = SocialColors.accentBright,
                    modifier = Modifier.size(48.dp)
                )
                SocialTitle("You have no friends yet")
                SocialBody(
                    "Add friends by their Aerix username or by their Gmail address. They have to accept the request before you can chat, call or join a party together.",
                    size = 13.sp
                )
                SocialPrimaryButton(
                    text = "Add friends",
                    onClick = onAddFriend,
                    iconRes = R.drawable.aerix_social_ic_add_person,
                    modifier = Modifier.fillMaxWidth()
                )
                SocialGhostButton("Browse activity", onClick = onActivity, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FriendRow(friend: AerixUser, onMessage: (AerixUser) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SocialDimens.row)
            .clip(RoundedCornerShape(SocialDimens.controlRadius))
            .background(SocialColors.panelSoft)
            .border(BorderStroke(1.dp, SocialColors.border), RoundedCornerShape(SocialDimens.controlRadius))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SocialAvatar(avatar = friend.avatar, size = 40.dp, story = friend.hasStory)
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
        SocialGhostButton("Message", onClick = { onMessage(friend) })
    }
}

private fun statusLabel(status: UserStatus): String = when (status) {
    UserStatus.ONLINE -> "Online in Aerix"
    UserStatus.PLAYING -> "Minecraft running"
    UserStatus.IN_VOICE -> "In a voice party"
    UserStatus.OFFLINE -> "Offline"
}

@Composable
private fun RequestsCard(
    requests: List<net.kdt.pojavlaunch.aerix.social.data.FriendRequest>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    SocialPanel(modifier = Modifier.fillMaxWidth()) {
        SocialSectionTitle("Friend requests")
        SocialBody("${requests.size} pending", size = 12.sp, color = SocialColors.textMuted)
        if (requests.isEmpty()) {
            SocialBody("No pending requests.", size = 12.sp, color = SocialColors.textMuted)
        } else {
            requests.forEach { request ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialAvatar(avatar = request.avatar, size = 34.dp)
                    SocialBody(request.fromName, size = 13.sp, color = SocialColors.textPrimary, modifier = Modifier.weight(1f))
                    SocialPrimaryButton("Accept", onClick = { onAccept(request.id) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SocialGhostButton("Decline", onClick = { onDecline(request.id) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ChatListTab(friends: List<AerixUser>, onMessage: (AerixUser) -> Unit, onAddFriend: () -> Unit) {
    if (friends.isEmpty()) {
        NoFriendsState(onAddFriend = onAddFriend, onActivity = {})
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(friends, key = { it.id }) { friend ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SocialDimens.row)
                    .clip(RoundedCornerShape(SocialDimens.controlRadius))
                    .background(SocialColors.panelSoft)
                    .clickable { onMessage(friend) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SocialAvatar(avatar = friend.avatar, size = 40.dp, story = friend.hasStory)
                Column(modifier = Modifier.weight(1f)) {
                    SocialBody(friend.name, size = 14.sp, color = SocialColors.textPrimary, weight = FontWeight.SemiBold)
                    SocialBody(statusLabel(friend.status), size = 12.sp, color = SocialColors.textMuted)
                }
                Icon(
                    painter = painterResource(R.drawable.aerix_social_ic_chat),
                    contentDescription = null,
                    tint = SocialColors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Parties: any number of friends, renameable, with a logo and a group call. */
@Composable
private fun PartyTab(
    party: PartyState,
    onCreate: (String, Int) -> Unit,
    onLeave: () -> Unit,
    onOpenVoice: () -> Unit
) {
    var name by remember(party.name) { mutableStateOf(party.name.ifBlank { "Nova's party" }) }
    var callOpen by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SocialPanel(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SocialSectionTitle(if (party.joined) party.name else "Start a party")
            SocialBody(
                if (party.joined) "${party.members.size} in the party · ${if (party.inCall) "group call live" else "text only"}"
                else "Parties need at least ${PartyState.MIN_MEMBERS} people to be worth starting. There is no upper limit.",
                size = 12.sp,
                color = SocialColors.textMuted
            )
            if (!party.joined) {
                SocialTextField(value = name, onValueChange = { name = it }, placeholder = "Party name")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialWardrobeLogoRow(onSelect = { })
                }
                SocialPrimaryButton("Create party", onClick = { onCreate(name, 3) }, modifier = Modifier.fillMaxWidth())
                SocialBody("You can rename the party and change its logo at any time.", size = 11.sp, color = SocialColors.textMuted)
            } else {
                SocialTextField(value = name, onValueChange = { name = it }, placeholder = "Party name")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialGhostButton(
                        "Rename",
                        onClick = { onOpenVoice() },
                        modifier = Modifier.weight(1f)
                    )
                    SocialGhostButton("Leave", onClick = onLeave, modifier = Modifier.weight(1f))
                }
                // Opens the real voice room: the mic in there is the device microphone.
                SocialPrimaryButton("Group call", onClick = { callOpen = true }, iconRes = R.drawable.aerix_social_ic_mic, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
            }
        }
        SocialPanel(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SocialSectionTitle("Members")
            if (party.members.isEmpty()) {
                SocialBody("Nobody yet. Invite friends from your dashboard.", size = 12.sp, color = SocialColors.textMuted)
            } else {
                party.members.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SocialAvatar(avatar = member.avatar, size = 34.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            SocialBody(member.name, size = 13.sp, color = SocialColors.textPrimary)
                            SocialBody(if (member.muted) "Muted" else "Live", size = 11.sp, color = SocialColors.textMuted)
                        }
                        Icon(
                            painter = painterResource(if (member.muted) R.drawable.aerix_social_ic_mic_off else R.drawable.aerix_social_ic_mic),
                            contentDescription = null,
                            tint = SocialColors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            SocialBody("Everything you can send in a chat you can send here, including files, links, instances and music.", size = 11.sp, color = SocialColors.textMuted)
        }
    }
    if (callOpen) PartyCallSheet(party = party, onClose = { callOpen = false })
}

@Composable
private fun SocialWardrobeLogoRow(onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..4).forEach { index ->
            Box(modifier = Modifier.clip(CircleShape).clickable { onSelect(index) }) {
                SocialAvatar(avatar = index, size = 30.dp)
            }
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
            SocialTitle("Could not connect to Social")
            SocialBody("Your friends list could not be loaded. Check your connection and try again.")
            SocialPrimaryButton("Retry", onClick = onRetry)
        }
    }
}

/* -------------------------------------------------------------------- chat ----- */


/** Voice room: the meter below is the device microphone, not an animation. */
@Composable
private fun PartyCallSheet(party: PartyState, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(SocialMic.hasPermission(context)) }
    var live by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) {
            SocialMic.start(scope)
            live = true
        }
    }
    DisposableEffect(Unit) { onDispose { SocialMic.stop() } }

    SocialDialog(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                painter = painterResource(R.drawable.aerix_social_ic_mic),
                contentDescription = null,
                tint = SocialColors.accent,
                modifier = Modifier.size(20.dp)
            )
            SocialTitle(party.name.ifBlank { "Group call" })
        }
        SocialBody(
            if (live) "${party.members.size} in the call · you are live"
            else "Tap join and Android asks for the microphone once.",
            size = 12.sp,
            color = SocialColors.textMuted
        )
        SocialMicMeter(active = live)
        party.members.take(4).forEach { member ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialAvatar(avatar = member.avatar, size = 32.dp)
                Column(modifier = Modifier.weight(1f)) {
                    SocialBody(member.name, size = 13.sp, color = SocialColors.textPrimary, maxLines = 1)
                    SocialBody(if (member.muted) "Muted" else "Live", size = 11.sp, color = SocialColors.textMuted)
                }
            }
        }
        SocialPrimaryButton(
            text = when {
                !granted -> "Allow microphone"
                live -> "Leave call"
                else -> "Join call"
            },
            onClick = {
                when {
                    !granted -> permission.launch(android.Manifest.permission.RECORD_AUDIO)
                    live -> {
                        SocialMic.stop()
                        live = false
                    }
                    else -> {
                        SocialMic.start(scope)
                        live = true
                    }
                }
            },
            iconRes = if (live) R.drawable.aerix_social_ic_mic_off else R.drawable.aerix_social_ic_mic,
            modifier = Modifier.fillMaxWidth()
        )
        SocialGhostButton("Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playingId by SocialAudioPlayer.playingId.collectAsState()
    var linkDialog by remember { mutableStateOf(false) }
    var linkDraft by remember { mutableStateOf("") }
    var instanceDialog by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        sending = true
        scope.launch {
            val file = SocialMedia.copyToStorage(context, uri)
            sending = false
            if (file == null) return@launch
            val kind = when {
                file.mimeType.startsWith("audio/") -> AttachmentKind.MUSIC
                file.mimeType == "image/gif" -> AttachmentKind.GIF
                file.mimeType.startsWith("image/") -> AttachmentKind.IMAGE
                else -> AttachmentKind.FILE
            }
            viewModel.sendAttachment(
                ChatAttachment(
                    kind = kind,
                    title = file.displayName,
                    subtitle = file.mimeType,
                    payload = file.path,
                    sizeLabel = file.sizeLabel,
                    localPath = file.path,
                    mimeType = file.mimeType
                )
            )
        }
    }

    // Real document pickers: every chip opens the system picker for its own mime type.
    fun pick(kind: AttachmentKind) {
        val mime = when (kind) {
            AttachmentKind.IMAGE -> "image/*"
            AttachmentKind.GIF -> "image/gif"
            AttachmentKind.FILE -> "*/*"
            AttachmentKind.MUSIC -> "audio/*"
            else -> "*/*"
        }
        picker.launch(mime)
    }


    Column(modifier = Modifier.fillMaxSize().padding(horizontal = SocialDimens.pagePaddingH)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SocialAvatar(avatar = state.friendAvatar, size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                SocialBody(state.friendName, size = 15.sp, color = SocialColors.textPrimary, weight = FontWeight.SemiBold)
                SocialBody("Messages, files, music and voice", size = 11.sp, color = SocialColors.textMuted)
            }
            SocialGhostButton("Party", onClick = onVoice, iconRes = R.drawable.aerix_social_ic_party)
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
                        shareState = state.shares[message.id]?.state,
                        playing = playingId == message.id,
                        onReact = { emoji -> viewModel.react(message.id, emoji) },
                        onInstall = { viewModel.install(message.id) },
                        onToggleMusic = {
                            val path = message.attachments.firstOrNull()?.localPath
                            SocialAudioPlayer.toggle(message.id, path)
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AttachmentChip(R.drawable.aerix_ic_image_outlined, "Image") { pick(AttachmentKind.IMAGE) }
            AttachmentChip(R.drawable.aerix_social_ic_gif, "GIF") { pick(AttachmentKind.GIF) }
            AttachmentChip(R.drawable.aerix_ic_description_outlined, "File") { pick(AttachmentKind.FILE) }
            AttachmentChip(R.drawable.aerix_ic_link, "Link") { linkDialog = true }
            AttachmentChip(R.drawable.aerix_social_ic_instance, "Instance") { instanceDialog = true }
            AttachmentChip(R.drawable.aerix_social_ic_music, "Music") { pick(AttachmentKind.MUSIC) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(CircleShape)
                .background(SocialColors.panelSoft)
                .border(BorderStroke(1.dp, SocialColors.border), CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SocialTextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = if (sending) "Preparing file…" else "Message ${state.friendName}",
                    modifier = Modifier
                )
            }
            SocialIconButton(
                iconRes = R.drawable.aerix_social_ic_send,
                contentDescription = "Send",
                onClick = viewModel::send,
                selected = state.draft.isNotBlank()
            )
        }
    }

    if (linkDialog) {
        SocialDialog(onDismiss = { linkDialog = false }) {
            SocialTitle("Send a link")
            SocialTextField(value = linkDraft, onValueChange = { linkDraft = it }, placeholder = "https://…")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialPrimaryButton(
                    "Send",
                    onClick = {
                        viewModel.sendLink(linkDraft)
                        linkDraft = ""
                        linkDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                SocialGhostButton("Cancel", onClick = { linkDialog = false }, modifier = Modifier.weight(1f))
            }
        }
    }

    if (instanceDialog) {
        val instances = remember { SocialInstances.local() }
        SocialDialog(onDismiss = { instanceDialog = false }) {
            SocialTitle("Share an instance")
            if (instances.isEmpty()) {
                SocialBody("You have no instances yet. Create one in the launcher first.", size = 13.sp)
            } else {
                instances.take(6).forEach { instance ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SocialDimens.row)
                            .clip(RoundedCornerShape(SocialDimens.controlRadius))
                            .background(SocialColors.panelSoft)
                            .clickable {
                                viewModel.sendAttachment(SocialInstances.attachment(instance))
                                instanceDialog = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_social_ic_instance),
                            contentDescription = null,
                            tint = SocialColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            SocialBody(instance.name, size = 13.sp, color = SocialColors.textPrimary, weight = FontWeight.Medium, maxLines = 1)
                            SocialBody(instance.version.ifBlank { "Local instance" }, size = 11.sp, color = SocialColors.textMuted)
                        }
                    }
                }
            }
            SocialGhostButton("Cancel", onClick = { instanceDialog = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AttachmentChip(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(SocialColors.panelSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = SocialColors.accent, modifier = Modifier.size(14.dp))
        Text(text = label, color = SocialColors.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    shareState: InstanceShareState?,
    playing: Boolean,
    onReact: (String) -> Unit,
    onInstall: () -> Unit,
    onToggleMusic: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start
    ) {
        if (message.text.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (message.mine) SocialColors.accent else SocialColors.panelRaised)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.text,
                    color = if (message.mine) SocialColors.onAccent else SocialColors.textPrimary,
                    fontSize = 13.sp
                )
            }
        }
        message.attachments.forEach { attachment ->
            Spacer(Modifier.height(4.dp))
            AttachmentCard(
                attachment = attachment,
                shareState = shareState,
                playing = playing,
                onInstall = onInstall,
                onToggleMusic = onToggleMusic
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SocialBody(message.timestamp, size = 9.sp, color = SocialColors.textMuted)
            listOf("like" to "👍", "love" to "❤️", "laugh" to "😂").forEach { (key, emoji) ->
                val count = message.reactions[key] ?: 0
                val active = message.myReaction == key
                Text(
                    text = if (count > 0) "$emoji $count" else emoji,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (active) SocialColors.accent.copy(alpha = 0.3f) else SocialColors.panelSoft)
                        .clickable { onReact(key) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/** One attachment: real media preview, file, link, instance or a real music player. */
@Composable
private fun AttachmentCard(
    attachment: ChatAttachment,
    shareState: InstanceShareState?,
    playing: Boolean,
    onInstall: () -> Unit,
    onToggleMusic: () -> Unit
) {
    SocialPanel(modifier = Modifier.fillMaxWidth(0.72f)) {
        if (attachment.kind == AttachmentKind.IMAGE || attachment.kind == AttachmentKind.GIF) {
            val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, attachment.localPath) {
                value = attachment.localPath?.let { SocialMedia.thumbnail(it) }
            }
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = attachment.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SocialColors.accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        when (attachment.kind) {
                            AttachmentKind.IMAGE -> R.drawable.aerix_ic_image_outlined
                            AttachmentKind.GIF -> R.drawable.aerix_social_ic_gif
                            AttachmentKind.FILE -> R.drawable.aerix_ic_description_outlined
                            AttachmentKind.LINK -> R.drawable.aerix_ic_link
                            AttachmentKind.INSTANCE -> R.drawable.aerix_social_ic_instance
                            AttachmentKind.MUSIC -> R.drawable.aerix_social_ic_music
                        }
                    ),
                    contentDescription = null,
                    tint = SocialColors.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                SocialBody(attachment.title, size = 13.sp, color = SocialColors.textPrimary, weight = FontWeight.Medium, maxLines = 1)
                SocialBody(attachment.subtitle.ifBlank { attachment.sizeLabel }, size = 11.sp, color = SocialColors.textMuted, maxLines = 1)
            }
        }
        when (attachment.kind) {
            AttachmentKind.INSTANCE -> {
                val local = remember(attachment.payload) {
                    SocialInstances.local().any { it.id == attachment.payload || it.name.equals(attachment.title, true) }
                }
                val label = when {
                    shareState == InstanceShareState.DOWNLOADING -> "Downloading…"
                    local -> "Open in launcher"
                    else -> "Not installed here"
                }
                SocialPrimaryButton(
                    text = label,
                    onClick = {
                        // Real action: select the instance if this device has it, otherwise the
                        // player is told, instead of pretending a download happened.
                        if (!SocialInstances.select(attachment.payload)) onInstall()
                    },
                    enabled = shareState != InstanceShareState.DOWNLOADING,
                    iconRes = R.drawable.aerix_ic_download,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AttachmentKind.MUSIC -> {
                SocialGhostButton(
                    text = if (playing) "Pause" else "Play in chat",
                    onClick = onToggleMusic,
                    iconRes = if (playing) R.drawable.aerix_ic_pause else R.drawable.aerix_ic_play_arrow_filled,
                    modifier = Modifier.fillMaxWidth()
                )
                SocialBody(
                    if (attachment.localPath != null) "Plays from this device." else "Shared track",
                    size = 11.sp,
                    color = SocialColors.textMuted
                )
            }
            AttachmentKind.LINK -> {
                val uriHandler = LocalUriHandler.current
                SocialBody(attachment.payload, size = 11.sp, color = SocialColors.accent, maxLines = 1)
                SocialGhostButton(
                    text = "Open link",
                    onClick = { runCatching { uriHandler.openUri(attachment.payload) } },
                    iconRes = R.drawable.aerix_ic_link,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> SocialBody(
                listOf(attachment.sizeLabel, attachment.mimeType).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                size = 11.sp,
                color = SocialColors.textMuted
            )
        }
    }
}
