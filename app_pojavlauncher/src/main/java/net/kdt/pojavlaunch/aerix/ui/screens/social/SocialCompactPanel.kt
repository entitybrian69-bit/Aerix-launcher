package net.kdt.pojavlaunch.aerix.ui.screens.social

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import net.kdt.pojavlaunch.R

// Images 10/11/14/18/19/20/27: one right-aligned dark translucent panel over the live game.
// Header and back controls stay above friends/chat/voice/invitation content, with no launcher rail.
// White primary invite actions, dim glass secondary actions, muted red destructive controls.
@Composable
fun SocialCompactPanel(onBack: () -> Unit, onResume: () -> Unit, onLeaveGame: () -> Unit) {
    var mode by remember { mutableStateOf("Friends") }
    var person by remember { mutableStateOf("Kai") }
    var add by remember { mutableStateOf(false) }
    var block by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var expired by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    var localReport by remember { mutableStateOf(false) }
    var voiceHelp by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun back() { if (mode == "Friends") onBack() else { mode = "Friends"; localReport = false } }
    androidx.activity.compose.BackHandler { back() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { back() }, modifier = Modifier.size(48.dp)) { Text("←", fontSize = 24.sp) }
            Text(if (mode == "Chat") person else if (mode == "Invite") "Server invitation" else "Social${if (mode == "Party") " / Voice party" else ""}",
                Modifier.weight(1f), color = SocialPalette.text, fontSize = 20.sp)
            TextButton(onClick = onResume, modifier = Modifier.size(48.dp)) { Text("×", fontSize = 28.sp) }
        }
        if (!SocialStore.data.signedIn || !SocialStore.data.appearanceComplete) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                SocialHeading("Sign in from the launcher", true)
                SocialCaption("Your Aerix account is needed for friends, private messages and voice parties.")
                Spacer(Modifier.height(20.dp)); SocialCaption("Sign-in is available in the launcher, not inside this overlay.")
                SocialButton("Back to game", onResume, Modifier.fillMaxWidth(), secondary = true)
            }
            SocialCaption("Return to the launcher when you are ready. Switching apps may interrupt your game session.")
        } else if (localReport) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { SocialReportScreen(person) { localReport = false } }
            SocialButton("Back to Social", { localReport = false }, Modifier.fillMaxWidth(), secondary = true)
        } else when (mode) {
            "Chat" -> {
                Box(Modifier.weight(1f)) { CompactChat(person, onInvite = { expired = false; mismatch = false; mode = "Invite" },
                    onVoice = { if (SocialStore.data.party.isEmpty()) SocialStore.createParty(); mode = "Party" }, onBlock = { block = true }, onReport = { localReport = true }) }
            }
            "Invite" -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { CompactHead(person); Column(Modifier.padding(12.dp)) { SocialHeading(person); SocialCaption("Invitation from $person") } }
                    if (expired) {
                        SocialCaption("Expired"); Spacer(Modifier.height(20.dp)); Text("◷", fontSize = 64.sp, color = SocialPalette.secondary)
                        SocialHeading("This invitation has expired.", true)
                        SocialCaption("Ask $person to send a new invitation if you still want to join.")
                    } else if (mismatch) {
                        SocialPanel {
                            SocialHeading("Your current instance does not match")
                            SocialCaption("Required: Minecraft 1.21.1 • Fabric")
                            SocialCaption("Current: Minecraft 1.20.1 • Forge")
                        }
                        SocialCaption("Matching version and loader may not be enough; the server may require specific mods.")
                        SocialCaption("Reviewing instances will not switch games. Changing instances requires leaving the current session and confirmation.")
                    } else {
                        SocialHeading("Join us for survival", true)
                        SocialCaption("Joining will leave your current game session.")
                    }
                    Spacer(Modifier.height(12.dp))
                    if (!expired) SocialButton(if (mismatch) "Review installed instances" else "Join", {
                        if (mismatch) SocialStore.notice = "Demo invitation: return to the launcher after leaving the game to review instances. No game was switched."
                        else leave = true
                    }, Modifier.fillMaxWidth())
                    SocialButton("Message $person", { mode = "Chat"; SocialStore.seedChat(person) }, Modifier.fillMaxWidth(), secondary = true)
                    SocialButton("Dismiss", { mode = "Friends" }, Modifier.fillMaxWidth(), secondary = true)
                    // Development previews exercise invite states without inventing a real server connection.
                    Row {
                        TextButton(onClick = { expired = !expired }) { Text("Preview expired", fontSize = 11.sp) }
                        TextButton(onClick = { mismatch = !mismatch; expired = false }) { Text("Preview mismatch", fontSize = 11.sp) }
                    }
                }
            }
            "Party" -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SocialHeading("${SocialStore.data.name}'s party"); SocialCaption("Invite only")
                    if (SocialStore.data.party.isEmpty()) SocialButton("Create party", { SocialStore.createParty() }, Modifier.fillMaxWidth())
                    else {
                        if (SocialStore.voiceReconnecting) { SocialHeading("Waiting for connection"); SocialCaption("Voice connection lost. Reconnecting… Party audio is unavailable while reconnecting.") }
                        SocialStore.data.party.forEach { member ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                                CompactHead(member); Column(Modifier.weight(1f).padding(12.dp)) { SocialHeading(member); SocialCaption(if (member == SocialStore.data.name && SocialStore.data.muted) "Muted" else "Demo member") }; Text("▂▃▅▆", color = SocialPalette.secondary)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SocialButton(if (SocialStore.data.muted) "Unmute" else "Mute", {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                                    SocialStore.update { it.copy(muted = !it.muted, deafened = false) }
                                else voiceHelp = true
                            }, Modifier.weight(1f), secondary = true)
                            SocialButton(if (SocialStore.data.deafened) "Undeafen" else "Deafen", { SocialStore.update { it.copy(deafened = !it.deafened, muted = true) } }, Modifier.weight(1f), secondary = true)
                        }
                        SocialActionRow("Audio settings") { voiceHelp = true }
                        SocialButton("Hold to talk", { voiceHelp = true }, Modifier.fillMaxWidth(), secondary = true)
                        SocialActionRow("Preview reconnecting") { SocialStore.voiceReconnecting = true }
                        if (SocialStore.voiceReconnecting) SocialButton("Retry now", { SocialStore.voiceReconnecting = false }, Modifier.fillMaxWidth())
                        SocialButton("Leave party", { SocialStore.leaveParty(); mode = "Friends" }, Modifier.fillMaxWidth(), secondary = true)
                        SocialCaption("Leaving the party does not leave the game.")
                        SocialCaption("Demo: no audio is recorded or transmitted.")
                    }
                }
            }
            else -> {
                Row { TextButton(onClick = { mode = "Friends" }) { Text("Friends") }; TextButton(onClick = { mode = "Party" }) { Text("Party") } }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SocialStore.data.friends.forEach { friend ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CompactHead(friend.name)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) { SocialHeading(friend.name); SocialCaption(friend.status) }
                            TextButton(onClick = { person = friend.name; SocialStore.seedChat(person); mode = "Chat" }) { Text("Message") }
                        }
                    }
                    SocialButton("Add friend", { add = true }, Modifier.fillMaxWidth(), secondary = true)
                    SocialButton("Preview server invitation", { person = "Kai"; expired = false; mismatch = false; mode = "Invite" }, Modifier.fillMaxWidth(), secondary = true)
                }
            }
        }
        if (SocialStore.data.signedIn && SocialStore.data.appearanceComplete) SocialButton("Back to game", onResume, Modifier.fillMaxWidth(), secondary = true)
    }
    if (add) SocialAddFriendDialog { add = false }
    if (block) SocialBlockDialog(person, { block = false }, onReport = { localReport = true }) { mode = "Friends" }
    if (leave) SocialLeaveGameDialog({ leave = false }) {
        leave = false
        // A mock server invite must never terminate or reconnect a real Minecraft session.
        SocialStore.notice = "Demo invitation only. Your current game has not been left or switched."
    }
    if (voiceHelp) AlertDialog(onDismissRequest = { voiceHelp = false }, title = { Text("Voice chat") },
        text = { Text("Manage microphone permission from Voice chat in the launcher. You can stay muted and listen. This demo does not capture or transmit audio.") },
        confirmButton = { TextButton(onClick = { voiceHelp = false }) { Text("Continue without microphone") } })
    SocialStore.notice?.let { message -> AlertDialog(onDismissRequest = { SocialStore.notice = null }, title = { Text("Social demo") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { SocialStore.notice = null }) { Text("OK") } }) }
}
@Composable
private fun CompactHead(name: String) {
    Image(painterResource(R.drawable.aerix_social_game_head), contentDescription = "$name Minecraft avatar", modifier = Modifier.size(40.dp))
}
@Composable
private fun CompactChat(person: String, onInvite: () -> Unit, onVoice: () -> Unit, onBlock: () -> Unit, onReport: () -> Unit) {
    var text by remember(person) { mutableStateOf("") }
    val scroll = rememberScrollState()
    val messages = SocialStore.messages(person)
    LaunchedEffect(messages.size) { scroll.animateScrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { TextButton(onClick = onBlock) { Text("Block") }; TextButton(onClick = onReport) { Text("Report") } }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            messages.forEach { message ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
                    Surface(color = if (message.mine) SocialPalette.accent.copy(alpha = .3f) else androidx.compose.ui.graphics.Color.White.copy(alpha = .12f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                        Text(message.text, Modifier.padding(12.dp), color = SocialPalette.text)
                    }
                    TextButton(onClick = { SocialStore.like(message.id) }) { Text("👍 ${message.likes}  +") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialButton("Invite to world", onInvite, Modifier.weight(1f), secondary = true)
            SocialButton("Voice call", onVoice, Modifier.weight(1f), secondary = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialField(text, { text = it.take(2000) }, "Message $person…", Modifier.weight(1f))
            TextButton(onClick = { SocialStore.send(person, text); text = "" }, enabled = text.isNotBlank()) { Text("Send") }
        }
    }
}

// Image 23: centered dark translucent confirmation, Stay left and Leave game muted red right.
// Leaving the game and leaving a voice party remain separate operations.
@Composable
fun SocialLeaveGameDialog(onDismiss: () -> Unit, onLeave: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = androidx.compose.ui.graphics.Color(0xF0252C2C),
        title = { Text("Leave the game?") }, text = { Text("Leaving the current game closes your session in this world or server. Party voice and game-session continuity are not guaranteed.") },
        confirmButton = { TextButton(onClick = onLeave) { Text("Leave game", color = SocialPalette.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Stay") } })
}
