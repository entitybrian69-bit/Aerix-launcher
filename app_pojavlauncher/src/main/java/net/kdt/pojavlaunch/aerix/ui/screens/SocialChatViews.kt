package net.kdt.pojavlaunch.aerix.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.social.SocialFriend
import net.kdt.pojavlaunch.aerix.social.SocialMessage
import net.kdt.pojavlaunch.aerix.social.SocialParty
import net.kdt.pojavlaunch.aerix.social.SocialRealtime
import net.kdt.pojavlaunch.aerix.social.SocialSearchHit
import net.kdt.pojavlaunch.aerix.social.SocialSoundEffects
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.theme.ThemeMotion
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SocialChatImage(
    urlOrPath: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(AerixRadii.control),
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
        Box(
            modifier = modifier
                .clip(shape)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        ) {
            SocialRemoteImage(
                url = urlOrPath,
                fallbackIcon = R.drawable.aerix_ic_image_outlined,
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                contentScale = ContentScale.Crop
            )
        }
    } else {
        var localBmp by remember(urlOrPath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        LaunchedEffect(urlOrPath) {
            localBmp = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = if (urlOrPath.startsWith("content://")) {
                        context.contentResolver.openInputStream(Uri.parse(urlOrPath))?.use { it.readBytes() }
                    } else {
                        File(urlOrPath).takeIf { it.isFile }?.readBytes()
                    }
                    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                }.getOrNull()
            }
        }
        Box(
            modifier = modifier
                .clip(shape)
                .background(AerixSurface.panelTrack)
                .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft), shape)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            val bmp = localBmp
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_image_outlined),
                    contentDescription = null,
                    tint = AerixSurface.textMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SocialConversationPanel(
    friend: SocialFriend?,
    party: SocialParty?,
    compact: Boolean = false,
    onBack: (() -> Unit)? = null,
    onOpenFriendProfile: ((SocialFriend) -> Unit)? = null,
    onOpenPartyDetails: ((SocialParty) -> Unit)? = null,
    onJoinSession: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isParty = party != null
    val convKey = party?.id ?: friend?.id ?: return
    val accent = AerixThemeManager.currentAccent()
    val scope = rememberCoroutineScope()
    val myId = SocialBackend.myUserId()

    val liveFriend = friend?.let { target ->
        SocialBackend.friends.firstOrNull { it.id == target.id } ?: target
    }
    val liveParty = party?.let { target ->
        SocialBackend.parties.firstOrNull { it.id == target.id } ?: target
    }

    val dmRev = SocialBackend.messageRevision
    val partyRev = SocialBackend.partyMessageRevision

    var syncedMessages by remember(convKey) {
        mutableStateOf(
            if (party != null) SocialBackend.getCachedPartyMessages(party.id)
            else if (friend != null) SocialBackend.getCachedMessages(friend.id)
            else emptyList()
        )
    }

    // Recomputes on the EXACT same Compose frame whenever dmRev / partyRev / syncedMessages updates!
    val messages = remember(convKey, dmRev, partyRev, syncedMessages) {
        val raw = if (party != null) {
            val live = SocialBackend.getCachedPartyMessages(party.id)
            if (live.isEmpty()) syncedMessages
            else (syncedMessages + live).sortedBy { it.atMs }
        } else if (friend != null) {
            val live = SocialBackend.getCachedMessages(friend.id)
            if (live.isNotEmpty() || syncedMessages.isEmpty()) live else syncedMessages
        } else {
            emptyList()
        }
        raw.distinctBy { m ->
            m.localTempId ?: if (m.id != 0L) "id_${m.id}" else "ts_${m.sender}_${m.atMs}_${m.body.hashCode()}"
        }
    }

    var draft by remember(convKey) { mutableStateOf("") }
    var replyTarget by remember(convKey) { mutableStateOf<SocialMessage?>(null) }
    var editTarget by remember(convKey) { mutableStateOf<SocialMessage?>(null) }
    var pendingAttachmentUri by remember(convKey) { mutableStateOf<Uri?>(null) }
    var lightboxUrl by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    DisposableEffect(convKey) {
        SocialBackend.setOpenConversation(peerId = friend?.id, partyId = party?.id)
        onDispose {
            SocialBackend.notifyStopTyping(peerId = friend?.id, partyId = party?.id)
            if (SocialBackend.activeOpenPeerId == friend?.id && SocialBackend.activeOpenPartyId == party?.id) {
                SocialBackend.setOpenConversation(null, null)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pendingAttachmentUri = uri
    }

    // Initial full load on conversation open
    LaunchedEffect(convKey) {
        val loaded = if (party != null) {
            SocialBackend.loadPartyMessages(party.id)
        } else if (friend != null) {
            SocialBackend.loadMessages(friend.id)
        } else emptyList()
        syncedMessages = loaded
        if (friend != null && loaded.any { it.sender == friend.id && !it.isRead }) {
            SocialBackend.markRead(friend.id)
        }
    }

    // Mark incoming unread messages as read immediately while viewing open DM
    LaunchedEffect(convKey, dmRev) {
        if (!isParty && friend != null) {
            val cached = SocialBackend.getCachedMessages(friend.id)
            syncedMessages = cached
            if (cached.any { it.sender == friend.id && !it.isRead }) {
                SocialBackend.markRead(friend.id)
            }
        }
    }

    LaunchedEffect(convKey, partyRev) {
        if (isParty && party != null) {
            syncedMessages = SocialBackend.loadPartyMessages(party.id)
        }
    }

    // Rapid 1.0s open-chat delta-sync (`select=*&id=gt.$maxServerId`) so replies & Seen receipts
    // ALWAYS appear immediately in-place even on networks that block WebSockets!
    LaunchedEffect(convKey, SocialBackend.isNetworkAvailable) {
        if (!SocialBackend.isNetworkAvailable) return@LaunchedEffect
        while (isActive) {
            delay(1_000L)
            if (party != null) {
                syncedMessages = SocialBackend.loadPartyMessages(party.id)
            } else if (friend != null) {
                syncedMessages = SocialBackend.syncNewMessages(friend.id)
            }
        }
    }

    val typingKey = if (party != null) {
        SocialRealtime.partyTypingKey(party.id)
    } else {
        SocialRealtime.dmTypingKey(friend!!.id)
    }
    val activeTyper = SocialRealtime.activeTypers[typingKey]?.first

    // Gentle Messenger typing bubble pulse sound if peer keeps typing a long message
    LaunchedEffect(activeTyper != null) {
        if (activeTyper != null) {
            SocialSoundEffects.playPeerTyping()
            while (isActive && SocialRealtime.activeTypers.containsKey(typingKey)) {
                delay(3_000L)
                if (SocialRealtime.activeTypers.containsKey(typingKey)) {
                    SocialSoundEffects.playPeerTyping()
                }
            }
        }
    }

    val lastMsgKey = messages.lastOrNull()?.let { "${it.id}_${it.isRead}" }
    LaunchedEffect(messages.size, lastMsgKey, activeTyper != null) {
        val totalItems = messages.size + (if (activeTyper != null) 1 else 0)
        if (totalItems > 0) {
            runCatching { listState.animateScrollToItem(totalItems - 1) }
        }
    }

    val latestReadSentId = remember(messages, myId) {
        messages.lastOrNull { it.sender == myId && it.isRead && !it.pendingOffline }?.id
    }

    SocialPanel(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(if (compact) 10.dp else 14.dp)) {
            // Conversation header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onBack != null) {
                    SocialIconBubble(R.drawable.aerix_ic_arrow_back, "Back", onBack)
                }
                if (liveParty != null) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 32.dp else 38.dp)
                            .clip(RoundedCornerShape(AerixRadii.controlSmall))
                            .background(accent.copy(alpha = 0.18f))
                            .border(BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.5f)), RoundedCornerShape(AerixRadii.controlSmall)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_group_outlined),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = onOpenPartyDetails != null) { onOpenPartyDetails?.invoke(liveParty) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                liveParty.title,
                                fontSize = if (compact) 13.sp else 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AerixSurface.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            SocialTag("${liveParty.memberCount}/${liveParty.maxMembers}", accent)
                        }
                        if (activeTyper != null) {
                            SocialTypingIndicatorRow(
                                text = "$activeTyper is typing",
                                tint = accent,
                                fontSize = 11.sp
                            )
                        } else {
                            Text(
                                "${liveParty.summaryTag()} · ${liveParty.remainingText()}",
                                fontSize = 11.sp,
                                color = AerixSurface.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (onJoinSession != null) {
                        SocialButton("Join", filled = true, onClick = onJoinSession)
                    }
                    if (onOpenPartyDetails != null) {
                        SocialIconBubble(R.drawable.aerix_ic_settings_filled, "Party settings") {
                            onOpenPartyDetails(liveParty)
                        }
                    }
                } else if (liveFriend != null) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        SocialRemoteImage(
                            url = liveFriend.avatarUrl,
                            fallbackIcon = R.drawable.aerix_ic_person_outlined,
                            modifier = Modifier
                                .size(if (compact) 32.dp else 38.dp)
                                .clickable(enabled = onOpenFriendProfile != null) { onOpenFriendProfile?.invoke(liveFriend) },
                            shape = CircleShape
                        )
                        SocialStatusDot(mode = liveFriend.activePresenceMode(), size = if (compact) 9.dp else 10.dp)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = onOpenFriendProfile != null) { onOpenFriendProfile?.invoke(liveFriend) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SocialNameWithRole(
                                name = liveFriend.effectiveName,
                                role = liveFriend.effectiveRole,
                                fontSize = if (compact) 13.sp else 15.sp,
                                fontWeight = FontWeight.Bold,
                                compactBadge = compact,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (liveFriend.pinned) {
                                Icon(
                                    painter = painterResource(R.drawable.aerix_ic_favorite_filled),
                                    contentDescription = "Pinned",
                                    tint = accent,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            if (liveFriend.online && liveFriend.joinable) {
                                SocialTag("Joinable", AerixSurface.success)
                            }
                        }
                        if (activeTyper != null) {
                            SocialTypingIndicatorRow(
                                text = "$activeTyper is typing",
                                tint = accent,
                                fontSize = 11.sp
                            )
                        } else {
                            Text(
                                liveFriend.presenceSummary(),
                                fontSize = 11.sp,
                                color = presenceModeColor(liveFriend.activePresenceMode()),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (onJoinSession != null && liveFriend.online && (liveFriend.joinable || !liveFriend.mcVersion.isNullOrBlank())) {
                        SocialButton("Join", filled = true, onClick = onJoinSession)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = AerixSurface.borderSoft.copy(alpha = 0.65f), thickness = AerixSpacing.hairline)

            // Offline send queue or disconnected Wi-Fi notice
            if (!SocialBackend.isNetworkAvailable || SocialBackend.offlineQueueSize > 0) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AerixRadii.compact))
                        .background(AerixSurface.warning.copy(alpha = 0.14f))
                        .border(BorderStroke(AerixSpacing.hairline, AerixSurface.warning.copy(alpha = 0.4f)), RoundedCornerShape(AerixRadii.compact))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_schedule_outlined),
                        contentDescription = null,
                        tint = AerixSurface.warning,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        if (!SocialBackend.isNetworkAvailable) {
                            "Offline (Wi-Fi off) · messages will queue and send on reconnect"
                        } else {
                            "${SocialBackend.offlineQueueSize} queued offline · sending on reconnect"
                        },
                        fontSize = 10.5.sp,
                        color = AerixSurface.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Message stream
            if (messages.isEmpty() && activeTyper == null) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isParty) "Party channel ready — say hi or share screenshots!"
                        else "No messages yet — start the conversation.",
                        color = AerixSurface.textMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = messages,
                        key = { m ->
                            m.localTempId ?: if (m.id != 0L) "id_${m.id}" else "ts_${m.sender}_${m.atMs}_${m.body.hashCode()}"
                        }
                    ) { msg ->
                        val isMine = msg.sender == myId
                        var menuOpen by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                        ) {
                            if (isParty && !isMine && !msg.cleanSenderName.isNullOrBlank()) {
                                SocialNameWithRole(
                                    name = msg.cleanSenderName ?: "",
                                    role = msg.effectiveSenderRole,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent,
                                    compactBadge = true,
                                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                                )
                            }
                            Box {
                                val bubbleShape = RoundedCornerShape(
                                    topStart = 14.dp,
                                    topEnd = 14.dp,
                                    bottomStart = if (isMine) 14.dp else 4.dp,
                                    bottomEnd = if (isMine) 4.dp else 14.dp
                                )
                                Surface(
                                    shape = bubbleShape,
                                    color = if (isMine) accent.copy(alpha = 0.22f) else AerixSurface.panelTrack,
                                    border = BorderStroke(
                                        AerixSpacing.hairline,
                                        if (isMine) accent.copy(alpha = 0.52f) else AerixSurface.borderSoft
                                    ),
                                    modifier = Modifier
                                        .widthIn(max = if (compact) 250.dp else 360.dp)
                                        .combinedClickable(
                                            onClick = { menuOpen = true },
                                            onLongClick = { menuOpen = true }
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                                        if (!msg.replyPreview.isNullOrBlank()) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(bottom = 5.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Black.copy(alpha = 0.22f))
                                                    .padding(horizontal = 7.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(2.dp)
                                                        .height(14.dp)
                                                        .background(accent)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    msg.replyPreview,
                                                    fontSize = 10.5.sp,
                                                    color = AerixSurface.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        if (!msg.attachmentUrl.isNullOrBlank()) {
                                            SocialChatImage(
                                                urlOrPath = msg.attachmentUrl,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 110.dp, max = 180.dp)
                                                    .padding(bottom = 5.dp),
                                                onClick = { lightboxUrl = msg.attachmentUrl }
                                            )
                                        }

                                        if (msg.body.isNotBlank() && !(msg.body == "📷 Image" && !msg.attachmentUrl.isNullOrBlank())) {
                                            Text(
                                                msg.body,
                                                fontSize = if (compact) 12.sp else 13.sp,
                                                color = AerixSurface.textPrimary
                                            )
                                        }

                                        Spacer(Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            if (msg.isEdited) {
                                                Text("(edited)", fontSize = 9.sp, color = AerixSurface.textMuted)
                                            }
                                            Text(
                                                formatChatTimestamp(msg.atMs),
                                                fontSize = 9.sp,
                                                color = AerixSurface.textMuted
                                            )
                                            if (isMine) {
                                                val receiptText = when {
                                                    msg.pendingOffline -> "⏳"
                                                    isParty -> "✓"
                                                    msg.isRead -> "✓✓"
                                                    else -> "✓"
                                                }
                                                val receiptColor = when {
                                                    msg.pendingOffline -> AerixSurface.warning
                                                    msg.isRead -> accent
                                                    else -> AerixSurface.textMuted
                                                }
                                                Text(
                                                    receiptText,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = receiptColor
                                                )
                                            }
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false }
                                ) {
                                    if (msg.id > 0L) {
                                        DropdownMenuItem(
                                            text = { Text("Reply", fontSize = 12.sp) },
                                            onClick = {
                                                menuOpen = false
                                                editTarget = null
                                                replyTarget = msg
                                            }
                                        )
                                    }
                                    if (isMine && msg.id > 0L) {
                                        DropdownMenuItem(
                                            text = { Text("Edit message", fontSize = 12.sp) },
                                            onClick = {
                                                menuOpen = false
                                                replyTarget = null
                                                editTarget = msg
                                                draft = msg.body
                                            }
                                        )
                                    }
                                    if (msg.id > 0L) {
                                        DropdownMenuItem(
                                            text = { Text("Delete for me", fontSize = 12.sp, color = AerixSurface.danger) },
                                            onClick = {
                                                menuOpen = false
                                                SocialBackend.deleteMessageForMe(msg.id)
                                                syncedMessages = syncedMessages.filterNot { it.id == msg.id }
                                            }
                                        )
                                    }
                                }
                            }

                            // Messenger-style "Seen" badge with mini avatar right under the latest read DM!
                            if (!isParty && isMine && msg.id == latestReadSentId && liveFriend != null) {
                                Row(
                                    modifier = Modifier.padding(top = 2.dp, end = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Seen",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accent
                                    )
                                    SocialRemoteImage(
                                        url = liveFriend.avatarUrl,
                                        fallbackIcon = R.drawable.aerix_ic_person_outlined,
                                        modifier = Modifier.size(13.dp),
                                        shape = CircleShape
                                    )
                                }
                            }
                        }
                    }

                    // Live Messenger-style animated 3-dot typing bubble right inside the open chat stream!
                    item(key = "live_typing_bubble") {
                        AnimatedVisibility(
                            visible = activeTyper != null,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                        ) {
                            val typerLabel = activeTyper ?: liveFriend?.effectiveName ?: "Player"
                            SocialTypingChatBubble(
                                typerName = typerLabel,
                                avatarUrl = liveFriend?.avatarUrl,
                                showNameCaption = isParty,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                    }
                }
            }

            // Reply / Edit / Attachment preview strip above composer
            if (replyTarget != null || editTarget != null || pendingAttachmentUri != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AerixRadii.controlSmall))
                        .background(AerixSurface.panelTrack)
                        .border(BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.45f)), RoundedCornerShape(AerixRadii.controlSmall))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pendingAttachmentUri != null) {
                        SocialChatImage(
                            urlOrPath = pendingAttachmentUri.toString(),
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(6.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val headerText = when {
                            editTarget != null -> "Editing message"
                            replyTarget != null -> "Replying to message"
                            else -> "Image ready (auto-downscaled · 30-day retention)"
                        }
                        Text(headerText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent)
                        val sub = editTarget?.body ?: replyTarget?.body
                        if (!sub.isNullOrBlank()) {
                            Text(sub, fontSize = 11.sp, color = AerixSurface.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    SocialIconBubble(R.drawable.aerix_ic_close, "Cancel") {
                        replyTarget = null
                        if (editTarget != null) draft = ""
                        editTarget = null
                        pendingAttachmentUri = null
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Composer bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (editTarget == null) {
                    SocialIconBubble(R.drawable.aerix_ic_image_outlined, "Attach image") {
                        imagePicker.launch("image/*")
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(AerixRadii.control))
                        .background(AerixSurface.panelTrack)
                        .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft), RoundedCornerShape(AerixRadii.control))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            if (editTarget != null) "Update message..."
                            else if (isParty) "Message ${party?.title}..."
                            else "Message ${friend?.effectiveName}...",
                            color = AerixSurface.textMuted,
                            fontSize = 12.5.sp
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { next ->
                            val wasNonEmpty = draft.isNotEmpty()
                            if (next.length > draft.length) {
                                SocialSoundEffects.playTypingTick()
                            }
                            draft = next
                            if (editTarget == null) {
                                if (next.isNotBlank()) {
                                    SocialBackend.notifyTyping(
                                        peerId = friend?.id,
                                        partyId = party?.id
                                    )
                                } else if (wasNonEmpty) {
                                    SocialBackend.notifyStopTyping(
                                        peerId = friend?.id,
                                        partyId = party?.id
                                    )
                                }
                            }
                        },
                        singleLine = false,
                        maxLines = 4,
                        textStyle = TextStyle(color = AerixSurface.textPrimary, fontSize = 12.5.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val canSend = draft.isNotBlank() || pendingAttachmentUri != null
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (canSend) accent else AerixSurface.panelTrack)
                        .border(BorderStroke(AerixSpacing.hairline, if (canSend) accent else AerixSurface.borderSoft), CircleShape)
                        .clickable(enabled = canSend) {
                            val textToSend = draft
                            val attUri = pendingAttachmentUri
                            val replying = replyTarget
                            val editing = editTarget

                            // 0ms Messenger UX: clear composer immediately + play Send pop!
                            draft = ""
                            replyTarget = null
                            editTarget = null
                            pendingAttachmentUri = null
                            SocialSoundEffects.playMessageSent()

                            scope.launch {
                                if (editing != null) {
                                    SocialBackend.editMessage(editing.id, textToSend, isParty = isParty)
                                } else if (party != null) {
                                    SocialBackend.sendPartyMessage(party.id, textToSend, replying?.id, attUri)
                                } else if (friend != null) {
                                    SocialBackend.sendMessage(friend.id, textToSend, replying?.id, attUri)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (editTarget != null) R.drawable.aerix_ic_check else R.drawable.aerix_ic_send),
                        contentDescription = "Send",
                        tint = if (canSend) AerixSurface.canvas else AerixSurface.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    val zoomUrl = lightboxUrl
    if (zoomUrl != null) {
        Dialog(onDismissRequest = { lightboxUrl = null }) {
            SocialPanel(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
                Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    SocialChatImage(
                        urlOrPath = zoomUrl,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(AerixRadii.control)
                    )
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                        SocialIconBubble(R.drawable.aerix_ic_close, "Close") { lightboxUrl = null }
                    }
                }
            }
        }
    }
}

@Composable
fun SocialMessageSearchSection(
    onOpenDm: (String) -> Unit,
    onOpenParty: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SocialSearchHit>>(emptyList()) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SocialGlassInput(
                value = query,
                onValueChange = { next ->
                    query = next
                    if (next.trim().length < 2) {
                        results = emptyList()
                    } else {
                        searching = true
                        scope.launch {
                            results = SocialBackend.searchConversations(next)
                            searching = false
                        }
                    }
                },
                label = "Search across all DMs & party chats...",
                modifier = Modifier.weight(1f)
            )
            if (query.isNotEmpty()) {
                SocialIconBubble(R.drawable.aerix_ic_close, "Clear search") {
                    query = ""
                    results = emptyList()
                }
            }
        }

        if (query.trim().length >= 2) {
            SocialPanel(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                if (searching) {
                    Box(modifier = Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                        Text("Searching conversations...", fontSize = 12.sp, color = AerixSurface.textMuted)
                    }
                } else if (results.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                        Text("No messages matched \"${query.trim()}\"", fontSize = 12.sp, color = AerixSurface.textMuted)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(results, key = { "${it.partyId ?: it.peerId}_${it.messageId}" }) { hit ->
                            Surface(
                                shape = RoundedCornerShape(AerixRadii.controlSmall),
                                color = AerixSurface.panelTrack,
                                border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (hit.partyId != null) onOpenParty(hit.partyId)
                                        else if (hit.peerId != null) onOpenDm(hit.peerId)
                                        query = ""
                                        results = emptyList()
                                    }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${hit.conversationTitle} · ${hit.senderName}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AerixThemeManager.currentAccent(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            formatChatTimestamp(hit.atMs),
                                            fontSize = 10.sp,
                                            color = AerixSurface.textMuted
                                        )
                                    }
                                    Text(
                                        hit.body,
                                        fontSize = 12.sp,
                                        color = AerixSurface.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatChatTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val now = System.currentTimeMillis()
    val sameDay = (now / 86_400_000L) == (epochMs / 86_400_000L)
    val pattern = if (sameDay) "HH:mm" else "MMM d, HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}
