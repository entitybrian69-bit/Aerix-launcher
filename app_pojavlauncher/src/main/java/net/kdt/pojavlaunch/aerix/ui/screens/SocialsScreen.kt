/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Socials: the page the launcher steps into when the player wants other players, not a world.
 *
 * Laid out after the reviewed design set (site-preview/social-designs-sheet.png): crimson and
 * white on a light sheet, one tab per job - friends, messages, activity, settings - and a sign-in
 * page in front of them. The data comes from the Aerix social project (supabase/schema.sql).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---- palette of the social pages ---------------------------------------------------------

private val SocialInk = Color(0xFF14141A)
private val SocialMuted = Color(0xFF6F6E73)
private val SocialLine = Color(0xFFE7E3E0)
private val SocialSheet = Color(0xFFFCFBFA)
private val SocialCard = Color(0xFFFFFFFF)
private val SocialCrimson = Color(0xFFC81E3A)
private val SocialTint = Color(0xFFFBEAED)
private val SocialOnline = Color(0xFF3DA35D)

private enum class SocialPage(val label: String) {
    FRIENDS("Friends"),
    MESSAGES("Messages"),
    ACTIVITY("Activity"),
    SETTINGS("Settings")
}

@Composable
fun SocialsScreen(activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(SocialPage.FRIENDS) }
    var openChatWith by remember { mutableStateOf<SocialFriend?>(null) }
    val account = PojavAccountBridge.current
    val signedIn = SocialBackend.signedIn

    // One attach per screen, then keep the presence heartbeat and the friend list fresh for as
    // long as the page is open.
    LaunchedEffect(Unit) {
        SocialBackend.attach(activity)
        if (SocialBackend.signedIn) {
            SocialBackend.rememberMinecraftName(account?.username)
            SocialBackend.loadProfile()
            SocialBackend.refreshFriends()
        }
    }
    LaunchedEffect(signedIn) {
        while (signedIn) {
            SocialBackend.heartbeat()
            delay(60_000)
            SocialBackend.refreshFriends()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 2.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = SocialSheet
    ) {
        if (!signedIn) {
            SocialsSignIn(activity)
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                SocialsHeader(
                    onlineCount = SocialBackend.friends.count { it.online },
                    onAddFriend = { page = SocialPage.FRIENDS }
                )
                Spacer(Modifier.height(14.dp))
                SocialTabs(page) { page = it; openChatWith = null }
                Spacer(Modifier.height(16.dp))
                when (page) {
                    SocialPage.FRIENDS -> SocialsFriends(activity, openChatWith) { openChatWith = it }
                    SocialPage.MESSAGES -> SocialsMessages(activity, openChatWith) { openChatWith = it }
                    SocialPage.ACTIVITY -> SocialsActivity(activity)
                    SocialPage.SETTINGS -> SocialsSettings(activity, account?.username) {
                        SocialBackend.signOut()
                    }
                }
                SocialBackend.lastError?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Text(text = message, color = SocialCrimson, fontSize = 12.sp)
                }
            }
        }
    }
}

// ---- sign in -----------------------------------------------------------------------------

@Composable
private fun SocialsSignIn(activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    fun submit(create: Boolean) {
        if (address.isBlank() || password.length < 6) {
            Toast.makeText(activity, "Enter an email and a password of 6+ characters", Toast.LENGTH_SHORT).show()
            return
        }
        working = true
        scope.launch {
            if (create) SocialBackend.signUp(address, password) else SocialBackend.signIn(address, password)
            working = false
            if (SocialBackend.signedIn) {
                SocialBackend.rememberMinecraftName(PojavAccountBridge.current?.username)
                SocialBackend.loadProfile()
                SocialBackend.refreshFriends()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SocialCard,
            shadowElevation = 8.dp,
            modifier = Modifier.width(430.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 34.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = SocialCrimson) {
                    Icon(
                        painter = painterResource(R.drawable.ic_aerix_mark),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp).size(24.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Aerix Social", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = SocialInk)
                Text("Sign in to play with friends", fontSize = 13.sp, color = SocialMuted)
                Spacer(Modifier.height(20.dp))
                SocialField(address, { address = it }, "Email")
                Spacer(Modifier.height(10.dp))
                SocialField(password, { password = it }, "Password", secret = true)
                Spacer(Modifier.height(16.dp))
                SocialWideButton(if (working) "Please wait…" else "Sign in", SocialCrimson, Color.White) {
                    if (!working) submit(false)
                }
                Spacer(Modifier.height(8.dp))
                SocialWideButton("Create an account", Color(0xFF1D1D22), Color.White) {
                    if (!working) submit(true)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (SocialBackend.GOOGLE_WEB_CLIENT_ID.isEmpty())
                        "Google sign-in unlocks as soon as the Web client ID is added."
                    else "Google sign-in is ready.",
                    fontSize = 11.sp,
                    color = SocialMuted
                )
                SocialBackend.lastError?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Text(text = message, color = SocialCrimson, fontSize = 12.sp)
                }
                // Somebody signing up while the project still asks for a confirmed address lands
                // here: the button sends the mail again instead of leaving them stuck.
                if (SocialBackend.needsConfirmation()) {
                    Spacer(Modifier.height(8.dp))
                    SocialWideButton("Resend confirmation email", Color(0xFFEFECE9), SocialInk) {
                        scope.launch {
                            val sent = SocialBackend.resendConfirmation(address)
                            Toast.makeText(
                                activity,
                                if (sent) "Confirmation email sent to ${address.trim()}" else "Could not send the email",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = SocialInk,
            unfocusedTextColor = SocialInk,
            focusedBorderColor = SocialCrimson,
            unfocusedBorderColor = SocialLine,
            cursorColor = SocialCrimson
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ---- shared pieces -------------------------------------------------------------------------

@Composable
private fun SocialsHeader(onlineCount: Int, onAddFriend: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Socials", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = SocialInk)
        Spacer(Modifier.width(10.dp))
        Text("$onlineCount online", fontSize = 13.sp, color = SocialMuted)
        Spacer(Modifier.weight(1f))
        Surface(shape = CircleShape, color = SocialCrimson, onClick = onAddFriend) {
            Text(
                "+  Add friend",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun SocialTabs(current: SocialPage, onSelect: (SocialPage) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SocialPage.entries.forEach { page ->
            val selected = page == current
            Surface(
                shape = CircleShape,
                color = if (selected) SocialTint else Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (selected) SocialCrimson.copy(alpha = 0.45f) else SocialLine
                ),
                onClick = { onSelect(page) }
            ) {
                Text(
                    text = page.label,
                    color = if (selected) SocialCrimson else SocialMuted,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SocialWideButton(label: String, background: Color, content: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = background,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SocialHead(name: String?, size: Int = 40) {
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
    AerixImageWell(image = face, fallback = R.drawable.aerix_ic_person_outlined, size = size.dp, radius = 10.dp)
}

@Composable
private fun SocialEmptyState(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SocialCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, SocialLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_people),
                contentDescription = null,
                tint = SocialCrimson.copy(alpha = 0.75f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SocialInk)
            Spacer(Modifier.height(5.dp))
            Text(body, fontSize = 13.sp, color = SocialMuted, lineHeight = 18.sp)
        }
    }
}

private fun timeLabel(ms: Long): String =
    if (ms <= 0) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

// ---- friends ------------------------------------------------------------------------------

@Composable
private fun SocialsFriends(
    activity: FragmentActivity,
    openChatWith: SocialFriend?,
    onOpenChat: (SocialFriend?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val friends = SocialBackend.friends
    val incoming = friends.filter { it.pendingIncoming }
    val accepted = friends.filter { !it.pendingIncoming && !it.pendingOutgoing }

    if (openChatWith != null) {
        SocialChat(activity, openChatWith) { onOpenChat(null) }
        return
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Add a friend by Minecraft name", fontSize = 13.sp, color = SocialMuted) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SocialInk,
                    unfocusedTextColor = SocialInk,
                    focusedBorderColor = SocialCrimson,
                    unfocusedBorderColor = SocialLine,
                    cursorColor = SocialCrimson
                ),
                modifier = Modifier.weight(1f)
            )
            Surface(shape = RoundedCornerShape(12.dp), color = SocialCrimson, onClick = {
                scope.launch {
                    if (SocialBackend.addFriendByName(query)) {
                        query = ""
                        Toast.makeText(activity, "Request sent", Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text(
                    "Send",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(incoming) { friend ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SocialTint,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SocialHead(friend.mcUsername, 34)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${friend.mcUsername} sent you a friend request",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SocialInk,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(shape = CircleShape, color = SocialCrimson, onClick = {
                            scope.launch { SocialBackend.answerRequest(friend.id, true) }
                        }) {
                            Text("Accept", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = CircleShape, color = Color(0xFFE9E6E3), onClick = {
                            scope.launch { SocialBackend.answerRequest(friend.id, false) }
                        }) {
                            Text("Decline", color = SocialInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                }
            }

            if (accepted.isEmpty() && incoming.isEmpty()) {
                item {
                    SocialEmptyState(
                        "No friends yet",
                        "Send a request with a Minecraft name above. When they accept, you see what " +
                            "world they are in and can message them here or inside the game."
                    )
                }
            }

            items(accepted) { friend ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SocialCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SocialLine),
                    onClick = { onOpenChat(friend) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SocialHead(friend.mcUsername, 38)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(friend.mcUsername, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SocialInk)
                            Text(
                                if (friend.online) "Online" else "Offline",
                                fontSize = 12.sp,
                                color = if (friend.online) SocialOnline else SocialMuted
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (friend.online) SocialOnline else Color(0xFFB9B6B3))
                        )
                    }
                }
            }

            items(friends.filter { it.pendingOutgoing }) { friend ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SocialCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SocialLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SocialHead(friend.mcUsername, 34)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${friend.mcUsername} · request pending",
                            fontSize = 13.sp,
                            color = SocialMuted
                        )
                    }
                }
            }
        }
    }
}

// ---- chat ---------------------------------------------------------------------------------

@Composable
private fun SocialChat(activity: FragmentActivity, friend: SocialFriend, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<SocialMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    val myId = remember { SocialBackend.myUserId() }

    suspend fun reload() {
        messages = SocialBackend.loadMessages(friend.id)
    }
    LaunchedEffect(friend.id) {
        while (true) {
            reload()
            delay(3_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color(0xFFEFECE9), onClick = onBack) {
                Text("‹", fontSize = 18.sp, color = SocialInk, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }
            Spacer(Modifier.width(12.dp))
            SocialHead(friend.mcUsername, 34)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(friend.mcUsername, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SocialInk)
                Text(
                    if (friend.online) "Online" else "Offline",
                    fontSize = 12.sp,
                    color = if (friend.online) SocialOnline else SocialMuted
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages) { message ->
                val mine = message.sender == myId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (mine) SocialCrimson else Color(0xFFEDEAE7)
                        ) {
                            Text(
                                message.body,
                                color = if (mine) Color.White else SocialInk,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Text(timeLabel(message.atMs), fontSize = 10.sp, color = SocialMuted)
                    }
                }
            }
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Say hello to ${friend.mcUsername}. They see it here and inside the game.",
                        fontSize = 13.sp,
                        color = SocialMuted
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message…", fontSize = 13.sp, color = SocialMuted) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SocialInk,
                    unfocusedTextColor = SocialInk,
                    focusedBorderColor = SocialCrimson,
                    unfocusedBorderColor = SocialLine,
                    cursorColor = SocialCrimson
                ),
                modifier = Modifier.weight(1f)
            )
            Surface(shape = CircleShape, color = SocialCrimson, onClick = {
                val text = draft
                if (text.isNotBlank()) {
                    draft = ""
                    scope.launch {
                        if (SocialBackend.sendMessage(friend.id, text)) reload()
                        else Toast.makeText(activity, "Message did not send", Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text(
                    "Send",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

// ---- messages / activity / settings -------------------------------------------------------

@Composable
private fun SocialsMessages(activity: FragmentActivity, openChatWith: SocialFriend?, onOpenChat: (SocialFriend?) -> Unit) {
    if (openChatWith != null) {
        SocialChat(activity, openChatWith) { onOpenChat(null) }
        return
    }
    val friends = SocialBackend.friends.filter { !it.pendingIncoming && !it.pendingOutgoing }
    if (friends.isEmpty()) {
        SocialEmptyState(
            "No conversations",
            "Add a friend first. Your chats with them live here, and in the in-game panel."
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(friends) { friend ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SocialCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SocialLine),
                    onClick = { onOpenChat(friend) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SocialHead(friend.mcUsername, 36)
                        Spacer(Modifier.width(12.dp))
                        Text(friend.mcUsername, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SocialInk)
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialsActivity(activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    val incoming = SocialBackend.friends.filter { it.pendingIncoming }
    if (incoming.isEmpty()) {
        SocialEmptyState(
            "Nothing yet",
            "Friend requests and invitations show up here."
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(incoming) { friend ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SocialCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SocialLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SocialHead(friend.mcUsername, 34)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${friend.mcUsername} sent you a friend request",
                            fontSize = 13.sp,
                            color = SocialInk,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(shape = CircleShape, color = SocialCrimson, onClick = {
                            scope.launch {
                                SocialBackend.answerRequest(friend.id, true)
                                Toast.makeText(activity, "Friend added", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Accept", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialsSettings(activity: FragmentActivity, mcUsername: String?, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SocialCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, SocialLine),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Account", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SocialInk)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SocialHead(SocialBackend.mcUsername ?: mcUsername, 36)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            SocialBackend.email ?: "Signed in",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SocialInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Plays as ${SocialBackend.mcUsername ?: mcUsername ?: "no account selected"}",
                            fontSize = 12.sp,
                            color = SocialMuted
                        )
                    }
                }
                Text(
                    "Friends see the Minecraft account selected in the launcher. Change it above to " +
                        "change what they see.",
                    fontSize = 12.sp,
                    color = SocialMuted
                )
                Surface(shape = CircleShape, color = Color(0xFFEFECE9), onClick = onSignOut) {
                    Text(
                        "Sign out",
                        color = SocialInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SocialCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, SocialLine),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Privacy", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SocialInk)
                Text(
                    "Only people you accepted as friends can see your name, your head and whether " +
                        "you are online. Messages are readable by the two people in the conversation.",
                    fontSize = 12.sp,
                    color = SocialMuted,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
