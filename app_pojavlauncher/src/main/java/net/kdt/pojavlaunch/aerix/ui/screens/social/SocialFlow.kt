package net.kdt.pojavlaunch.aerix.ui.screens.social

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Image 1: compact glass widget, people tile left, overlapping avatars right, Open social footer.
// White heading and muted count; the blue accent is independent of the launcher theme.
@Composable
fun SocialWidgetCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SocialPanel(modifier.fillMaxWidth().heightIn(min = 96.dp).clickable {
        SocialStore.attach(context); SocialShellState.open()
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).background(SocialPalette.accent.copy(alpha = .4f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                SocialIcon(net.kdt.pojavlaunch.R.drawable.aerix_social_people, null)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Social", color = SocialPalette.text, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("4 friends online", color = SocialPalette.secondary, fontSize = 12.sp)
            }
            Box(Modifier.width(78.dp).height(32.dp)) {
                repeat(3) { SocialAvatar(it, Modifier.padding(start = (it * 18).dp).size(26.dp).border(2.dp, SocialPalette.background, RoundedCornerShape(8.dp))) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Open social", color = SocialPalette.secondary, fontSize = 11.sp); Text("›", color = SocialPalette.bright)
        }
    }
}

// The Social root owns its complete background and rail. No launcher chrome is hosted here.
// All screens use blue glass panels, white typography and the same fixed blue action palette.
@Composable
fun SocialShell(onExitToLauncher: () -> Unit) {
    val context = LocalContext.current
    SocialStore.attach(context)
    val route = SocialShellNav.route
    val back = { if (!SocialShellNav.back()) onExitToLauncher() }
    BackHandler(onBack = back)
    SocialTheme {
        SocialBackground {
            Row(Modifier.fillMaxSize()) {
                if (SocialStore.data.signedIn && SocialStore.data.appearanceComplete) SocialRail(onExitToLauncher)
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    SocialTopBar(route, back)
                    Spacer(Modifier.height(12.dp))
                    AnimatedContent(route, modifier = Modifier.weight(1f), transitionSpec = {
                        if (SocialShellNav.railChange) fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                        else (fadeIn(tween(200)) + slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { it / 12 }) togetherWith fadeOut(tween(140))
                    }, label = "socialPage") { page ->
                        when (page) {
                            SocialRoute.DASHBOARD -> SocialDashboardScreen()
                            SocialRoute.CHAT -> SocialChatScreen(SocialShellNav.selectedPerson)
                            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                when (page) {
                                    SocialRoute.AUTH -> SocialAuthScreen(onExitToLauncher)
                                    SocialRoute.APPEARANCE -> SocialAppearanceScreen()
                                    SocialRoute.PROFILE -> SocialProfileScreen()
                                    SocialRoute.SKIN -> SocialSkinPicker()
                                    SocialRoute.CAPE -> SocialCapePicker()
                                    SocialRoute.FRIEND -> SocialFriendScreen()
                                    SocialRoute.PRIVACY -> SocialPrivacyScreen()
                                    SocialRoute.NOTIFICATIONS -> SocialNotificationsScreen()
                                    SocialRoute.VOICE -> SocialVoiceScreen()
                                    SocialRoute.SAFETY -> SocialSafetyScreen()
                                    SocialRoute.DELETE -> SocialDeletionScreen()
                                    SocialRoute.REPORT -> SocialReportScreen(SocialShellNav.selectedPerson)
                                    SocialRoute.GUIDELINES -> SocialPanel { SocialHeading("Social guidelines"); SocialCaption("Be kind. No harassment, threats, scams or inappropriate content. Respect other players' privacy. This is a local UI demo; no moderation service is connected.") }
                                    SocialRoute.EXPLAIN -> SocialPanel { SocialHeading("Muting and blocking"); SocialCaption("Muting hides alerts. Blocking stops contact and removes friendship. Unblocking does not restore a friendship. Reports are separate and private.") }
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
            SocialStore.notice?.let { message ->
                AlertDialog(onDismissRequest = { SocialStore.notice = null }, title = { Text("Social demo") },
                    text = { Text(message) }, confirmButton = { TextButton(onClick = { SocialStore.notice = null }) { Text("OK") } })
            }
        }
    }
}
@Composable
private fun SocialRail(onExit: () -> Unit) {
    BoxWithConstraints {
        val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
        Column(Modifier.width(if (narrow) 64.dp else 84.dp).fillMaxHeight()
            .background(Color(0x55304256)).padding(8.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { if (!SocialShellNav.back()) onExit() }, modifier = Modifier.size(48.dp)) { SocialIcon(net.kdt.pojavlaunch.R.drawable.aerix_ic_arrow_back, "Back") }
            listOf("Friends", "Chats", "Party", "Profile").forEachIndexed { i, label ->
                val selected = if (i == 3) SocialShellNav.route == SocialRoute.PROFILE
                    else SocialShellNav.route == SocialRoute.DASHBOARD && SocialShellNav.tab == i
                Surface(onClick = { if (i == 3) SocialShellNav.push(SocialRoute.PROFILE) else SocialShellNav.showTab(i) },
                    color = if (selected) SocialPalette.accent else Color.White.copy(alpha = .08f), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        SocialIcon(listOf(net.kdt.pojavlaunch.R.drawable.aerix_social_people,
                            net.kdt.pojavlaunch.R.drawable.aerix_social_chat,
                            net.kdt.pojavlaunch.R.drawable.aerix_social_headphones,
                            net.kdt.pojavlaunch.R.drawable.aerix_ic_person_outlined)[i], label)
                        if (!narrow) Text(label, fontSize = 9.sp, color = SocialPalette.text)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (narrow) "×" else "×  Exit", color = SocialPalette.danger) }
        }
    }
}
@Composable
private fun SocialTopBar(route: SocialRoute, back: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var signOut by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = back, modifier = Modifier.size(48.dp)) { SocialIcon(net.kdt.pojavlaunch.R.drawable.aerix_ic_arrow_back, "Back") }
        Text(when (route) {
            SocialRoute.AUTH -> "Aerix account"
            SocialRoute.APPEARANCE -> "Create your Aerix profile"
            SocialRoute.DASHBOARD -> "Social"
            SocialRoute.PROFILE -> "My profile"
            SocialRoute.SKIN -> "Choose skin"
            SocialRoute.CAPE -> "Choose cape"
            SocialRoute.PRIVACY -> "Privacy & presence"
            SocialRoute.NOTIFICATIONS -> "Notifications"
            SocialRoute.SAFETY -> "Safety"
            SocialRoute.VOICE -> "Voice chat"
            SocialRoute.DELETE -> "Delete Aerix account?"
            SocialRoute.REPORT -> "Report ${SocialShellNav.selectedPerson}"
            SocialRoute.CHAT, SocialRoute.FRIEND -> SocialShellNav.selectedPerson
            else -> "Social"
        }, Modifier.weight(1f), fontSize = 22.sp, color = SocialPalette.text)
        if (route == SocialRoute.DASHBOARD) {
            if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) {
                SocialAvatar(0, Modifier.size(32.dp))
                Text(SocialStore.data.name, Modifier.padding(start = 8.dp), color = Color.White)
            }
            Box {
                TextButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) { SocialIcon(net.kdt.pojavlaunch.R.drawable.aerix_ic_more_vert, "Social options") }
                DropdownMenu(menu, { menu = false }) {
                    listOf("My profile" to SocialRoute.PROFILE, "Privacy and presence" to SocialRoute.PRIVACY,
                        "Notifications" to SocialRoute.NOTIFICATIONS, "Voice chat" to SocialRoute.VOICE,
                        "Safety" to SocialRoute.SAFETY).forEach { (title, page) ->
                        DropdownMenuItem(text = { Text(title) }, onClick = { menu = false; SocialShellNav.push(page) })
                    }
                    DropdownMenuItem(text = { Text("Preview connection error") }, onClick = { menu = false; SocialStore.connectionError = true })
                    DropdownMenuItem(text = { Text("Preview empty list") }, onClick = { menu = false; SocialStore.emptyPreview = true })
                    DropdownMenuItem(text = { Text("Restore list") }, onClick = { menu = false; SocialStore.emptyPreview = false; SocialStore.connectionError = false })
                    DropdownMenuItem(text = { Text("Sign out") }, onClick = { menu = false; signOut = true })
                }
            }
        }
    }
    if (signOut) SocialSignOutDialog { signOut = false }
}
