package net.kdt.pojavlaunch.aerix.ui.screens.social

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Image 4: friends/search occupy the wide left column; party and requests stack at right.
// Images 13/17 replace that content with one generous centered error/empty panel.
// Blue actions and underline; white titles and soft blue-glass row backgrounds.
@Composable
fun SocialDashboardScreen() {
    val tab = SocialShellNav.tab
    var search by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Friends", "Chats", "Party").forEachIndexed { i, label ->
                Column {
                    TextButton(onClick = { SocialShellNav.showTab(i) }) { Text(label, color = if (i == tab) SocialPalette.bright else SocialPalette.secondary, fontSize = 15.sp) }
                    if (tab == i) Box(Modifier.align(Alignment.CenterHorizontally).width(26.dp).height(2.dp).background(SocialPalette.accent))
                }
            }
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 22.sp) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Preview connection error") }, onClick = { menu = false; SocialStore.connectionError = true })
                    DropdownMenuItem(text = { Text("Preview empty list") }, onClick = { menu = false; search = "__empty_preview__" })
                    DropdownMenuItem(text = { Text("Restore list") }, onClick = { menu = false; search = ""; SocialStore.connectionError = false })
                }
            }
        }
        Crossfade(tab, animationSpec = tween(120), modifier = Modifier.weight(1f), label = "socialTab") { selected ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (SocialStore.connectionError) SocialEmptyPanel(true, { SocialStore.connectionError = false }, { SocialShellState.close() })
                else if (selected == 2) SocialPartyScreen()
                else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SocialField(search, { search = it }, "Search friends", Modifier.weight(1f))
                        SocialButton("Add friend", { add = true }, Modifier.widthIn(min = 100.dp))
                    }
                    val people = SocialStore.data.friends.filter { it.name.contains(search, true) }
                    if (people.isEmpty()) SocialEmptyPanel(false, { add = true }, { search = "" })
                    else SocialColumns(left = {
                        SocialPanel {
                            people.forEach { person -> SocialFriendRow(person, onMessage = { SocialShellNav.chat(person.name) }, onProfile = {
                                SocialShellNav.selectedPerson = person.name; SocialShellNav.push(SocialRoute.FRIEND)
                            }) }
                        }
                    }, right = {
                        SocialPanel {
                            SocialHeading("Your party")
                            SocialCaption(if (SocialStore.data.party.isEmpty()) "Not connected" else SocialStore.data.party.joinToString())
                            SocialButton(if (SocialStore.data.party.isEmpty()) "Create party" else "Open party", {
                                if (SocialStore.data.party.isEmpty()) SocialStore.createParty()
                                SocialShellNav.showTab(2)
                            }, Modifier.fillMaxWidth())
                        }
                        SocialPanel {
                            SocialHeading("Friend requests")
                            SocialCaption("${SocialStore.data.incoming.size} pending")
                            SocialStore.data.incoming.forEachIndexed { index, name ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SocialAvatar(index + 4); Text(name, Modifier.weight(1f).padding(8.dp), color = SocialPalette.text)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SocialButton("Decline", { SocialStore.update { it.copy(incoming = it.incoming - name) } }, Modifier.weight(1f), secondary = true)
                                    SocialButton("Accept", { SocialStore.accept(name) }, Modifier.weight(1f))
                                }
                            }
                            SocialStore.data.outgoing.forEach { name ->
                                SocialActionRow(name, "Request pending • Tap to cancel") { SocialStore.update { it.copy(outgoing = it.outgoing - name) } }
                            }
                        }
                    })
                }
            }
        }
    }
    if (add) SocialAddFriendDialog { add = false }
}
@Composable
fun SocialFriendRow(person: SocialPerson, onMessage: () -> Unit, onProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(Color.White.copy(alpha = .055f), RoundedCornerShape(12.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).clickable(onClick = onProfile), verticalAlignment = Alignment.CenterVertically) {
            Box {
                SocialAvatar(person.avatar)
                Box(Modifier.align(Alignment.BottomEnd).size(9.dp).background(if (person.online) Color(0xFF77D4A2) else Color.Gray, RoundedCornerShape(9.dp)))
            }
            Column(Modifier.padding(start = 12.dp)) { SocialHeading(person.name); SocialCaption(person.status) }
        }
        SocialButton("Message", onMessage, Modifier.widthIn(min = 96.dp), secondary = true)
    }
}
@Composable
private fun SocialEmptyPanel(error: Boolean, primary: () -> Unit, secondary: () -> Unit) {
    SocialPanel(Modifier.fillMaxWidth().heightIn(min = 300.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (error) "☁" else "♧", fontSize = 48.sp, color = SocialPalette.secondary)
            SocialHeading(if (error) "Could not connect to Social" else "Your friends list starts here", true)
            SocialCaption(if (error) "Your friends list could not be loaded. Check your connection and try again." else "Add someone by their unique Aerix name to get started.")
            SocialButton(if (error) "Retry" else "Add friend", primary, Modifier.width(200.dp))
            SocialButton(if (error) "Back" else "View requests", secondary, Modifier.width(200.dp), secondary = true)
            SocialCaption(if (error) "Your account and saved preferences have not been removed." else "Accepted friends can send private messages and party invitations.")
        }
    }
}
@Composable
fun SocialFriendScreen() {
    val name = SocialShellNav.selectedPerson
    val person = SocialStore.data.friends.find { it.name == name }
    var block by remember { mutableStateOf(false) }
    SocialPanel {
        SocialAvatar(person?.avatar ?: 0, Modifier.size(64.dp))
        SocialHeading(name, true); SocialCaption(person?.status ?: "Not friends")
        SocialButton("Message", { SocialShellNav.chat(name) }, Modifier.fillMaxWidth(), enabled = person != null)
        SocialActionRow("Invite to party") { if (SocialStore.data.party.isEmpty()) SocialStore.createParty(); SocialStore.update { it.copy(party = (it.party + name).distinct()) }; SocialShellNav.showTab(2) }
        SocialActionRow("Report") { SocialShellNav.push(SocialRoute.REPORT) }
        SocialActionRow("Block") { block = true }
        SocialActionRow("Remove friend") { SocialStore.remove(name); SocialShellNav.back() }
    }
    if (block) SocialBlockDialog(name, { block = false }) { SocialShellNav.back() }
}

// Image 10: header above a scrolling conversation, left/right bubbles, actions above composer.
// Mine is blue tinted; theirs is glass. Send is disabled for whitespace-only messages.
@Composable
fun SocialChatScreen(person: String) {
    val messages = SocialStore.messages(person)
    val list = rememberLazyListState()
    var draft by remember(person) { mutableStateOf("") }
    var actions by remember { mutableStateOf(false) }
    var block by remember { mutableStateOf(false) }
    var invite by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialCaption("Friends • Playing Minecraft")
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { actions = true }) { Text("⋮", fontSize = 24.sp) }
                DropdownMenu(actions, { actions = false }) {
                    DropdownMenuItem(text = { Text("Report") }, onClick = { actions = false; SocialShellNav.push(SocialRoute.REPORT) })
                    DropdownMenuItem(text = { Text("Block") }, onClick = { actions = false; block = true })
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(8.dp)) {
            items(messages, key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
                    Surface(color = if (message.mine) SocialPalette.accent.copy(alpha = .4f) else Color.White.copy(alpha = .1f), shape = RoundedCornerShape(14.dp)) {
                        Text(message.text, Modifier.widthIn(max = 380.dp).padding(14.dp), color = SocialPalette.text, fontSize = 14.sp)
                    }
                    TextButton(onClick = { SocialStore.like(message.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (message.likes > 0) "👍 ${message.likes}  +" else "+  👍", fontSize = 12.sp) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialButton("Invite to server", { invite = true }, Modifier.weight(1f), secondary = true)
            SocialButton("Voice call", { if (SocialStore.data.party.isEmpty()) SocialStore.createParty(); SocialShellNav.push(SocialRoute.VOICE) }, Modifier.weight(1f), secondary = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialField(draft, { draft = it.take(2000) }, "Message $person…", Modifier.weight(1f))
            SocialButton("Send", { SocialStore.send(person, draft); draft = "" }, Modifier.width(88.dp), enabled = draft.isNotBlank())
        }
    }
    if (block) SocialBlockDialog(person, { block = false }) { SocialShellNav.showTab(0) }
    if (invite) AlertDialog(onDismissRequest = { invite = false }, title = { Text("Send server invitation?") },
        text = { Text("This local demo does not share your server address or send invitations to another player.") },
        confirmButton = { TextButton(onClick = { invite = false; SocialStore.notice = "Demo invitation prepared. No invitation was sent." }) { Text("Preview invitation") } },
        dismissButton = { TextButton(onClick = { invite = false }) { Text("Cancel") } })
}

// Image 11/19: member rows and audio controls; reconnect notice above the last-known members.
// Leave party is muted red; it never leaves or terminates the Minecraft game.
@Composable
fun SocialPartyScreen() {
    val data = SocialStore.data
    var invite by remember { mutableStateOf(false) }
    SocialPanel {
        SocialHeading("${data.name}'s party", true); SocialCaption("Invite only")
        if (data.party.isEmpty()) {
            SocialCaption("Not connected")
            SocialButton("Create party", { SocialStore.createParty() }, Modifier.fillMaxWidth())
        } else {
            if (SocialStore.voiceReconnecting) SocialCaption("Voice connection lost. Reconnecting… Party audio is unavailable.")
            data.party.forEachIndexed { i, name ->
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                    SocialAvatar(i); Column(Modifier.weight(1f).padding(12.dp)) { SocialHeading(name); SocialCaption(if (name == data.name && data.muted) "Muted" else "Demo member") }
                    Text("▂▃▅▆  ♬", color = SocialPalette.secondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SocialButton(if (data.muted) "Unmute" else "Mute", { SocialShellNav.push(SocialRoute.VOICE) }, Modifier.weight(1f), secondary = true)
                SocialButton(if (data.deafened) "Undeafen" else "Deafen", { SocialStore.update { it.copy(deafened = !it.deafened, muted = if (!it.deafened) true else it.muted) } }, Modifier.weight(1f), secondary = true)
            }
            SocialActionRow("Audio settings") { SocialShellNav.push(SocialRoute.VOICE) }
            SocialActionRow("Invite a friend") { invite = true }
            SocialActionRow("Preview connection loss") { SocialStore.voiceReconnecting = true }
            if (SocialStore.voiceReconnecting) SocialButton("Retry now", { SocialStore.voiceReconnecting = false }, Modifier.fillMaxWidth())
            SocialButton("Leave party", { SocialStore.leaveParty() }, Modifier.fillMaxWidth(), secondary = true)
            SocialCaption("Leaving the party does not leave the game. Demo only: no voice audio is transmitted.")
        }
    }
    if (invite) AlertDialog(onDismissRequest = { invite = false }, title = { Text("Invite a friend") }, text = {
        Column { SocialStore.data.friends.filterNot { it.name in data.party }.forEach { person ->
            TextButton(onClick = { SocialStore.update { it.copy(party = (it.party + person.name).distinct()) }; invite = false }) { Text(person.name) }
        } }
    }, confirmButton = { TextButton(onClick = { invite = false }) { Text("Done") } })
}

// Image 20: add-friend validation becomes a sent confirmation with a Pending label.
// Blue-glass dialog with Back to Social and Cancel request actions.
@Composable
fun SocialAddFriendDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (sent) "Friend request sent" else "Add friend") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sent) { SocialHeading(name); SocialCaption("You can message $name after they accept your request."); SocialCaption("◷ Pending • local demo") }
            else SocialField(name, { name = it.trim().take(20); error = null }, "Aerix name", Modifier.fillMaxWidth(), error)
        }
    }, confirmButton = { TextButton(onClick = { if (sent) onDismiss() else { error = SocialStore.request(name); sent = error == null } }) { Text(if (sent) "Back to Social" else "Send request") } },
        dismissButton = { TextButton(onClick = { if (sent) SocialStore.update { it.copy(outgoing = it.outgoing - name) }; onDismiss() }) { Text(if (sent) "Cancel request" else "Cancel") } })
}

// Image 21: Block confirmation centered above the panel with a separate optional report checkbox.
// Red destructive action; a muted explanation makes the loss of friendship explicit.
@Composable
fun SocialBlockDialog(name: String, onDismiss: () -> Unit, onReport: (() -> Unit)? = null, onBlocked: () -> Unit) {
    var report by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Block $name?") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$name will not be able to message you, invite you to parties, or send server invitations. You will stop seeing each other as friends.")
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(report, { report = it }); Text("Also report $name") }
            SocialCaption("You can unblock $name later in Social settings. Unblocking does not restore a friendship.")
        }
    }, confirmButton = { TextButton(onClick = {
        SocialStore.block(name); onDismiss(); onBlocked()
        if (report) {
            if (onReport != null) onReport()
            else { SocialShellNav.selectedPerson = name; SocialShellNav.push(SocialRoute.REPORT) }
        }
    }) { Text("Block", color = SocialPalette.danger) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
