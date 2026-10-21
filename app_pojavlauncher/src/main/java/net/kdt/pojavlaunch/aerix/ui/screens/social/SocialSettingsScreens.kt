package net.kdt.pojavlaunch.aerix.ui.screens.social

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import net.kdt.pojavlaunch.aerix.components.SwitchSettingRow

// Images 5/26: presence switches in the left panel, contact permissions in the right panel.
// Save/Cancel sit below the left panel, with a friend-visible preview; blue glass and white type.
@Composable
fun SocialPrivacyScreen() {
    var prefs by remember { mutableStateOf(SocialStore.data.preferences) }
    SocialCaption("Choose what your Aerix friends can see.")
    SocialColumns(left = {
        SocialPanel {
            SocialHeading("Presence")
            SocialSwitch("Show online status", "Friends can see when you are online.", prefs.online) { prefs = prefs.copy(online = it) }
            SocialSwitch("Share game activity", "Friends see Playing Minecraft, never a server address.", prefs.activity) { prefs = prefs.copy(activity = it) }
            SocialSwitch("Show server details", "Off by default; friends see the server name only when you choose.", prefs.server) { prefs = prefs.copy(server = it) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SocialButton("Save changes", { SocialStore.update { it.copy(preferences = prefs) }; SocialShellNav.back() }, Modifier.weight(1f))
                SocialButton("Cancel", { SocialShellNav.back() }, Modifier.weight(1f), secondary = true)
            }
        }
        SocialPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SocialAvatar(0); Column(Modifier.padding(start = 12.dp)) {
                    SocialHeading(SocialStore.data.name + if (prefs.online) " • Online" else " • Offline")
                    SocialCaption(if (prefs.activity && prefs.online) "Playing Minecraft • Friends see this." else "Friends see this.")
                }
            }
        }
    }, right = {
        SocialPanel {
            SocialHeading("Who can contact you")
            SocialChoice("Friend requests", prefs.requests, listOf("Everyone", "Nobody")) { prefs = prefs.copy(requests = it) }
            SocialChoice("Private messages", prefs.messages, listOf("Accepted friends only", "Nobody")) { prefs = prefs.copy(messages = it) }
            SocialChoice("Party invitations", prefs.invitations, listOf("Accepted friends only", "Nobody")) { prefs = prefs.copy(invitations = it) }
        }
    })
}
@Composable
fun SocialSwitch(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SwitchSettingRow(title, summary, checked, onChange, accent = SocialPalette.accent)
}
@Composable
fun SocialChoice(title: String, value: String, choices: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        SocialCaption(title)
        Box {
            SocialButton("$value  ⌄", { expanded = true }, Modifier.fillMaxWidth(), secondary = true)
            DropdownMenu(expanded, { expanded = false }) { choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choice) }, onClick = { onChange(choice); expanded = false })
            } }
        }
    }
}

// Image 9: notify-about switches left; delivery switches and Android permission state right.
// Message previews default off. Save is bottom-right; blue switches retain 48dp touch targets.
@Composable
fun SocialNotificationsScreen() {
    val context = LocalContext.current
    var prefs by remember { mutableStateOf(SocialStore.data.preferences) }
    SocialCaption("Choose how Aerix keeps you updated.")
    SocialColumns(left = {
        SocialPanel {
            SocialHeading("Notify me about")
            SocialSwitch("Friend requests", null, prefs.friendAlerts) { prefs = prefs.copy(friendAlerts = it) }
            SocialSwitch("Private messages", "Accepted friends only", prefs.messageAlerts) { prefs = prefs.copy(messageAlerts = it) }
            SocialSwitch("Party invitations", null, prefs.partyAlerts) { prefs = prefs.copy(partyAlerts = it) }
            SocialSwitch("Server invitations", null, prefs.serverAlerts) { prefs = prefs.copy(serverAlerts = it) }
        }
    }, right = {
        SocialPanel {
            SocialHeading("Delivery")
            SocialSwitch("In-launcher alerts", null, prefs.launcherAlerts) { prefs = prefs.copy(launcherAlerts = it) }
            SocialSwitch("In-game social alerts", "Small notices while playing", prefs.gameAlerts) { prefs = prefs.copy(gameAlerts = it) }
            SocialSwitch("Message preview", "Hide message text in alerts", prefs.previews) { prefs = prefs.copy(previews = it) }
            SocialActionRow("Android notifications", "Device notification permission is controlled by Android.") {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }
    })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
        SocialButton("Cancel", { SocialShellNav.back() }, Modifier.weight(1f), secondary = true)
        SocialButton("Save changes", { SocialStore.update { it.copy(preferences = prefs) }; SocialShellNav.back() }, Modifier.weight(1f))
    }
}

// Images 12/16: microphone artwork left, three explanatory paragraphs right.
// Intro requests permission only after Continue; denied offers listen-only or app settings.
// Microphone access never starts capture in this mock, and the party starts muted.
@Composable
fun SocialVoiceScreen() {
    val context = LocalContext.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted; denied = !granted
        SocialStore.update { it.copy(muted = true) }
    }
    SocialColumns(left = {
        SocialPanel {
            SocialHeading(if (denied) "Microphone access is off" else "Talk with your party", true)
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(net.kdt.pojavlaunch.R.drawable.aerix_social_microphone),
                    contentDescription = if (denied) "Microphone disabled" else "Microphone", modifier = Modifier.size(180.dp))
                if (denied) Text("╱", fontSize = 130.sp, color = SocialPalette.bright)
            }
            SocialCaption("Microphone access is optional")
        }
    }, right = {
        SocialPanel {
            SocialHeading(if (denied) "Microphone access is off" else if (permission) "Your audio settings" else "Before you enable your microphone")
            SocialCaption(if (denied) "Android has not granted microphone access to Aerix. You can still join a party to listen." else "Microphone permission — Android will ask for access after you continue.")
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = SocialPalette.border)
            SocialCaption(if (denied) "To speak, enable microphone permission for Aerix in Android app settings." else "You stay in control — join muted, then use mute or push-to-talk.")
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = SocialPalette.border)
            SocialCaption("Listen without a microphone — you can still hear the party without granting access.")
            SocialCaption("Demo: no microphone is recorded and no audio is transmitted.")
            if (permission) {
                SocialSwitch("Muted", "Start every party muted", SocialStore.data.muted) {
                    SocialStore.update { data -> data.copy(muted = it, deafened = if (!it) false else data.deafened) }
                }
                SocialChoice("Input mode", "Push-to-talk", listOf("Push-to-talk", "Voice activity")) { SocialStore.notice = "$it selected for this demo." }
            }
        }
    })
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SocialButton(if (denied) "Continue without microphone" else if (permission) "Done" else "Continue to permission", {
            if (denied || permission) SocialShellNav.back() else request.launch(Manifest.permission.RECORD_AUDIO)
        }, Modifier.weight(1f))
        SocialButton(if (denied) "Open app settings" else "Not now", {
            if (denied) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            else SocialShellNav.back()
        }, Modifier.weight(1f), secondary = true)
    }
    SocialCaption("Android controls permission prompts. Opening settings may interrupt your current game session.")
}

// Image 8: searchable blocked list left; Get help and deletion action stacked at right.
// Unblock does not restore friendship. Destructive navigation is muted red on blue glass.
@Composable
fun SocialSafetyScreen() {
    var search by remember { mutableStateOf("") }
    SocialCaption("Manage blocked accounts and get help")
    SocialColumns(left = {
        SocialPanel {
            SocialHeading("Blocked accounts")
            SocialField(search, { search = it }, "Search blocked users…", Modifier.fillMaxWidth())
            SocialStore.data.blocked.filter { it.contains(search, true) }.forEachIndexed { i, name ->
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                    SocialAvatar(i); Column(Modifier.weight(1f).padding(12.dp)) { SocialHeading(name); SocialCaption("@${name.lowercase()}") }
                    SocialButton("Unblock", { SocialStore.update { it.copy(blocked = it.blocked - name) } }, Modifier.width(100.dp), secondary = true)
                }
            }
            SocialCaption("Blocked accounts cannot message you or send friend and party invitations.")
        }
    }, right = {
        SocialPanel {
            SocialHeading("Get help")
            SocialActionRow("Report a concern", "Report a profile or conversation") { SocialShellNav.push(SocialRoute.REPORT) }
            SocialActionRow("Social guidelines") { SocialShellNav.push(SocialRoute.GUIDELINES) }
            SocialActionRow("Explain", "Muting hides alerts. Blocking stops contact.") { SocialShellNav.push(SocialRoute.EXPLAIN) }
        }
        SocialPanel { SocialActionRow("Delete Aerix account", "Confirmation required. Unblocking does not restore a friendship.") { SocialShellNav.push(SocialRoute.DELETE) } }
    })
}

// Images 24/25: one broad glass card, explicit deletion consequences, DELETE input below.
// Destructive button remains disabled until the user acknowledges every item and types DELETE.
@Composable
fun SocialDeletionScreen() {
    var typed by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(setOf<Int>()) }
    var confirm by remember { mutableStateOf(false) }
    val consequences = listOf("Friends and messages are removed from Aerix.",
        "Blocked and report records are handled under our privacy policy.",
        "Your Minecraft purchase and Microsoft or Mojang account are not deleted.",
        "Local Minecraft instances on this device are not deleted.",
        "This demo stores data locally; no remote backups are created.")
    SocialPanel {
        SocialHeading("Delete Aerix account?", true)
        SocialCaption("This permanently deletes your local Aerix profile, friends, requests, accepted-friend messages, blocked list, and uploaded skin or cape data.")
        consequences.forEachIndexed { index, text ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(index in checked, { checked = if (it) checked + index else checked - index }); Text(text, color = SocialPalette.text, fontSize = 14.sp)
            }
        }
        SocialField(typed, { typed = it }, "Type DELETE to confirm", Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialButton("Delete account", { confirm = true }, Modifier.weight(1f), enabled = typed == "DELETE" && checked.size == consequences.size)
            SocialButton("Cancel", { SocialShellNav.back() }, Modifier.weight(1f), secondary = true)
        }
        TextButton(onClick = { SocialStore.notice = "Deleting the demo removes Social data and imported cosmetics from this device. Minecraft accounts and local game instances remain untouched." }) { Text("Read deletion policy") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Permanently delete local Social data?") },
        text = { Text("This cannot be undone. Your Minecraft account and local instances will stay on this device.") },
        confirmButton = { TextButton(onClick = { SocialStore.deleteAccount(); confirm = false }) { Text("Delete account", color = SocialPalette.danger) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

// Report reference: three-step reason, optional details and final review, never combined with Block.
// White title at top of a single dark glass panel, bottom Continue/Cancel actions.
@Composable
fun SocialReportScreen(person: String, onDone: () -> Unit = { SocialShellNav.back() }) {
    var step by remember { mutableIntStateOf(0) }
    var reason by remember { mutableStateOf("Harassment") }
    var details by remember { mutableStateOf("") }
    SocialPanel {
        SocialCaption("${step + 1} of 3")
        SocialHeading(listOf("What is the problem?", "Add details", "Review your report")[step], true)
        if (step == 0) listOf("Harassment", "Spam or scam", "Inappropriate name or skin", "Threats", "Something else").forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(reason == item, { reason = item }); Text(item, color = SocialPalette.text) }
        } else if (step == 1) OutlinedTextField(details, { details = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("Details (optional)") })
        else { SocialHeading(person); SocialCaption(reason); SocialCaption(details.ifBlank { "No additional details." }) }
        SocialCaption("Reports are private. Demo reports are saved only on this device, not sent to moderators.")
        SocialButton(if (step == 2) "Save report locally" else "Continue", {
            if (step < 2) step++ else { SocialStore.report(person, reason, details); onDone() }
        }, Modifier.fillMaxWidth())
        SocialButton("Cancel", onDone, Modifier.fillMaxWidth(), secondary = true)
    }
}
