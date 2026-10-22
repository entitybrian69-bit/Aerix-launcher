/*
 * Aerix Launcher Social layer.
 *
 * Settings, opened from the gear in the top right: Privacy, Microphone, Chats and Account
 * settings, plus notifications and safety. Every value is written straight to DataStore.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.data.SocialPersisted
import net.kdt.pojavlaunch.aerix.social.data.formatCooldown
import net.kdt.pojavlaunch.aerix.social.ui.SocialBody
import net.kdt.pojavlaunch.aerix.social.ui.SocialColors
import net.kdt.pojavlaunch.aerix.social.ui.SocialDialog
import net.kdt.pojavlaunch.aerix.social.ui.SocialDimens
import net.kdt.pojavlaunch.aerix.social.ui.SocialDropdownRow
import net.kdt.pojavlaunch.aerix.social.ui.SocialGhostButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialPage
import net.kdt.pojavlaunch.aerix.social.ui.SocialPanel
import net.kdt.pojavlaunch.aerix.social.ui.SocialPrimaryButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialSectionCard
import net.kdt.pojavlaunch.aerix.social.ui.SocialSectionTitle
import net.kdt.pojavlaunch.aerix.social.ui.SocialSliderRow
import net.kdt.pojavlaunch.aerix.social.ui.SocialTextField
import net.kdt.pojavlaunch.aerix.social.ui.SocialTitle
import net.kdt.pojavlaunch.BuildConfig
import net.kdt.pojavlaunch.aerix.social.auth.GoogleAuth
import net.kdt.pojavlaunch.aerix.social.ui.SocialAvatar
import net.kdt.pojavlaunch.aerix.social.ui.SocialMicTestCard
import net.kdt.pojavlaunch.aerix.social.ui.SocialToggleRow
import net.kdt.pojavlaunch.aerix.social.vm.ProfileUiState
import net.kdt.pojavlaunch.aerix.social.vm.SocialProfileViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialSettingsViewModel

/* ------------------------------------------------------------------ hub ----- */

@Composable
fun SocialSettingsHubScreen(
    profile: ProfileUiState,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenMic: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    SocialPage {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialBody("Settings for ${profile.username.ifBlank { profile.name }} · signed in with Google", size = 12.sp, color = SocialColors.textMuted)
            SocialSectionCard(
                title = "Privacy",
                subtitle = "Status, profile lock and who can reach you",
                iconRes = R.drawable.aerix_social_ic_shield,
                onClick = onOpenPrivacy
            )
            MicrophoneCard(onClick = onOpenMic)
            SocialSectionCard(
                title = "Chats",
                subtitle = "Strangers, friends, close friends, parties and media",
                iconRes = R.drawable.aerix_social_ic_chat,
                onClick = onOpenChats
            )
            SocialSectionCard(
                title = "Account settings",
                subtitle = "Username, disable or delete the account, safety",
                iconRes = R.drawable.aerix_social_ic_profile,
                onClick = onOpenAccount
            )
            Spacer(Modifier.height(4.dp))
            SocialGhostButton("Notifications", onClick = onOpenNotifications, iconRes = R.drawable.aerix_ic_info_outlined, modifier = Modifier.fillMaxWidth())
            SocialGhostButton("Back to Social", onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The microphone section, with the mic itself in the middle of the card. */
@Composable
private fun MicrophoneCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SocialDimens.cardRadius))
            .background(SocialColors.panelSoft)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(SocialColors.accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_social_ic_mic),
                contentDescription = null,
                tint = SocialColors.accentBright,
                modifier = Modifier.size(28.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            SocialBody("Microphone", color = SocialColors.textPrimary, size = 15.sp, weight = FontWeight.SemiBold)
            SocialBody("Mute on join, push-to-talk, noise and volume", size = 12.sp, color = SocialColors.textMuted)
        }
        Icon(
            painter = painterResource(R.drawable.aerix_social_ic_chevron),
            contentDescription = null,
            tint = SocialColors.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/* --------------------------------------------------------------- privacy ----- */

@Composable
fun SocialSettingsPrivacyScreen(viewModel: SocialSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val privacy = state.privacy

    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) {
                SocialSectionTitle("Status")
                SocialBody("What friends see next to your name.", size = 12.sp, color = SocialColors.textMuted)
                SocialDropdownRow(
                    title = "Status",
                    value = privacy.status,
                    options = listOf("Available to play", "In a party", "Busy", "Do not disturb", "Looking for friends"),
                    onSelect = viewModel::setStatus,
                    iconRes = R.drawable.aerix_social_ic_shield
                )
                SocialToggleRow("Profile lock", privacy.profileLock, viewModel::setProfileLock, subtitle = "Only followers can open your profile")
                SocialGhostButton("Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) {
                SocialSectionTitle("Personal settings")
                SocialToggleRow("Show online status", privacy.onlineStatus, viewModel::setOnlineStatus, iconRes = R.drawable.aerix_social_ic_friends)
                SocialToggleRow("Share activity", privacy.shareActivity, viewModel::setShareActivity, iconRes = R.drawable.aerix_social_ic_activity)
                SocialToggleRow("Show server details", privacy.showServerDetails, viewModel::setShowServerDetails, iconRes = R.drawable.aerix_social_ic_instance)
                SocialDropdownRow("Friend requests", privacy.friendRequests, listOf("Everyone", "Friends of friends", "Nobody"), viewModel::setFriendRequests)
                SocialDropdownRow("Private messages", privacy.privateMessages, listOf("Everyone", "Accepted friends only", "Close friends", "Nobody"), viewModel::setPrivateMessages)
                SocialDropdownRow("Party invitations", privacy.partyInvites, listOf("Everyone", "Accepted friends only", "Nobody"), viewModel::setPartyInvites)
            }
        }
    }
}

/* ----------------------------------------------------------- microphone ----- */

@Composable
fun SocialSettingsMicScreen(viewModel: SocialSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val mic = state.mic

    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(0.8f).fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(SocialColors.accent.copy(alpha = 0.28f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(if (mic.muteOnJoin) R.drawable.aerix_social_ic_mic_off else R.drawable.aerix_social_ic_mic),
                                contentDescription = null,
                                tint = SocialColors.accentBright,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        SocialTitle(if (mic.muteOnJoin) "Join muted" else "Join with mic on")
                        SocialBody("Android asks for microphone permission the first time you join a call.", size = 11.sp, color = SocialColors.textMuted)
                    }
                }
            }
            SocialPanel(modifier = Modifier.weight(1.2f).fillMaxSize().verticalScroll(rememberScrollState())) {
                SocialMicTestCard()
                SocialSectionTitle("What the microphone does")
                SocialToggleRow("Join muted", mic.muteOnJoin, viewModel::setMuteOnJoin, subtitle = "You unmute when you want to talk")
                SocialToggleRow("Push to talk", mic.pushToTalk, viewModel::setPushToTalk, subtitle = "Hold the talk button instead of an open mic")
                SocialToggleRow("Voice activity", mic.voiceActivity, viewModel::setVoiceActivity, subtitle = "Unmute automatically when you speak")
                SocialToggleRow("Noise suppression", mic.noiseSuppression, viewModel::setNoiseSuppression)
                SocialToggleRow("Echo cancellation", mic.echoCancellation, viewModel::setEchoCancellation)
                SocialSliderRow("Input volume", mic.inputVolume, viewModel::setInputVolume)
                SocialSliderRow("Output volume", mic.outputVolume, viewModel::setOutputVolume)
                SocialPrimaryButton("Done", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/* ---------------------------------------------------------------- chats ----- */

@Composable
fun SocialSettingsChatsScreen(viewModel: SocialSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val chat = state.chat

    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) {
                SocialSectionTitle("Receive messages from")
                SocialToggleRow("Strangers", chat.allowStrangers, viewModel::setAllowStrangers, subtitle = "People who are not in your friends list")
                SocialToggleRow("Friends", chat.allowFriends, viewModel::setAllowFriends)
                SocialToggleRow("Close friends", chat.allowCloseFriends, viewModel::setAllowCloseFriends)
                SocialToggleRow("Party", chat.allowParty, viewModel::setAllowParty, subtitle = "Everyone in a party you joined")
                SocialBody("Blocked accounts never reach you, whatever is set here.", size = 11.sp, color = SocialColors.textMuted)
            }
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) {
                SocialSectionTitle("Media and receipts")
                SocialToggleRow("Auto-download media", chat.autoDownloadMedia, viewModel::setAutoDownloadMedia, subtitle = "Images, GIFs, files, instances and music")
                SocialToggleRow("Read receipts", chat.readReceipts, viewModel::setReadReceipts)
                SocialToggleRow("Keep media on device", chat.keepMediaOnDevice, viewModel::setKeepMedia)
                Spacer(Modifier.weight(1f))
                SocialPrimaryButton("Save changes", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/* -------------------------------------------------------------- account ----- */

@Composable
fun SocialSettingsAccountScreen(
    profile: SocialProfileViewModel,
    state: ProfileUiState,
    onBack: () -> Unit,
    onOpenSafety: () -> Unit,
    onDeleted: () -> Unit,
    onSignedOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var usernameDialog by remember { mutableStateOf(false) }
    var forgetDialog by remember { mutableStateOf(false) }
    var clientIdDialog by remember { mutableStateOf(false) }
    var clientIdDraft by remember { mutableStateOf(state.googleClientId) }
    var disableDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var newUsername by remember { mutableStateOf(state.username) }
    val canChange = state.canChangeUsername()

    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) {
                SocialSectionTitle("Profile")
                SocialBody("Username · ${state.username.ifBlank { "not set" }}", size = 13.sp, color = SocialColors.textPrimary)
                SocialBody(
                    if (canChange) "You can change your username now."
                    else "Next change in ${formatCooldown(state.cooldownRemaining())}.",
                    size = 12.sp,
                    color = if (canChange) SocialColors.success else SocialColors.textMuted
                )
                SocialGhostButton(
                    text = if (canChange) "Change username" else "Username locked",
                    onClick = { if (canChange) usernameDialog = true },
                    enabled = canChange,
                    modifier = Modifier.fillMaxWidth()
                )
                SocialBody("A username can only be changed once a week.", size = 11.sp, color = SocialColors.textMuted)
                Spacer(Modifier.height(8.dp))
                SocialStageRow("Blocked accounts and reports", onOpenSafety)
            }
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) {
                SocialSectionTitle("Account")
                // The real address Google returned, or a plain statement when there is none.
                SocialBody(
                    if (state.email.isNotBlank()) state.email
                    else "Local profile · no Google account linked",
                    size = 13.sp,
                    color = SocialColors.textPrimary,
                    maxLines = 1
                )
                SocialBody(
                    if (state.email.isNotBlank()) "Signed in with Google · ${state.name.ifBlank { "Aerix member" }}"
                    else "Signed in · ${state.name.ifBlank { "Aerix member" }}",
                    size = 12.sp,
                    color = SocialColors.textMuted
                )
                // Real sign-out: Google's credential state for this app is cleared, and the
                // profile stays on the device so the same account can come back to it.
                SocialStageRow("Sign out") {
                    scope.launch {
                        GoogleAuth.signOut(context)
                        profile.signOut()
                        onSignedOut()
                    }
                }
                SocialBody("Signing out keeps your profile on this device. It is hidden until the same Google account signs in again.", size = 11.sp, color = SocialColors.textMuted)
                SocialStageRow("Use a different Google account") { forgetDialog = true }
                SocialBody("Forgetting the link means the next account starts with its own character and its own chats.", size = 11.sp, color = SocialColors.textMuted)
                SocialStageRow("Disable account") { disableDialog = true }
                SocialBody("Disabling signs you out everywhere and hides your profile. Nothing is deleted.", size = 11.sp, color = SocialColors.textMuted)
                SocialStageRow("Delete account") { deleteDialog = true }
                SocialBody("Deleting removes your profile, friends, messages, posts and links permanently.", size = 11.sp, color = SocialColors.textMuted)
                if (BuildConfig.DEBUG) {
                    SocialStageRow("Google client id (testing)") { clientIdDialog = true }
                    SocialBody(
                        if (state.googleClientId.isBlank()) "No testing override · this build uses the baked-in id if it has one."
                        else "Testing override in use.",
                        size = 11.sp,
                        color = SocialColors.textMuted
                    )
                }
                Spacer(Modifier.weight(1f))
                SocialGhostButton("Done", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (forgetDialog) {
        SocialDialog(onDismiss = { forgetDialog = false }) {
            SocialTitle("Use a different account?")
            SocialBody("This removes ${state.email.ifBlank { "the current Google account" }} from this device. Your profile and chats stay until another account signs in, then they start fresh.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialPrimaryButton(
                    "Forget account",
                    onClick = {
                        scope.launch {
                            GoogleAuth.signOut(context)
                            profile.forgetGoogleAccount()
                            forgetDialog = false
                            onSignedOut()
                        }
                    },
                    danger = true,
                    modifier = Modifier.weight(1f)
                )
                SocialGhostButton("Cancel", onClick = { forgetDialog = false }, modifier = Modifier.weight(1f))
            }
        }
    }

    if (clientIdDialog) {
        SocialDialog(onDismiss = { clientIdDialog = false }) {
            SocialTitle("Google client id")
            SocialBody("Testing only. Paste the OAuth *Web application* client id from Google Cloud Console; it is used when the APK was built without one.")
            SocialTextField(
                value = clientIdDraft,
                onValueChange = { clientIdDraft = it },
                placeholder = "000000000000-abc.apps.googleusercontent.com"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialPrimaryButton(
                    "Save",
                    onClick = {
                        profile.setGoogleClientId(clientIdDraft)
                        clientIdDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                SocialGhostButton("Cancel", onClick = { clientIdDialog = false }, modifier = Modifier.weight(1f))
            }
        }
    }

    if (usernameDialog) {
        SocialDialog(onDismiss = { usernameDialog = false }) {
            SocialTitle("Change username")
            SocialBody("3–16 letters, numbers or underscores. After saving you cannot change it for 7 days.", size = 12.sp, color = SocialColors.textMuted)
            SocialTextField(value = newUsername, onValueChange = { newUsername = it.take(16) }, placeholder = "new_username")
            if (state.nameError != null) {
                SocialBody(state.nameError ?: "", size = 12.sp, color = SocialColors.danger)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialPrimaryButton(
                    "Save",
                    onClick = {
                        if (profile.changeUsername(newUsername)) usernameDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                SocialGhostButton("Cancel", onClick = { usernameDialog = false }, modifier = Modifier.weight(1f))
            }
        }
    }

    if (disableDialog) {
        SocialDialog(onDismiss = { disableDialog = false }) {
            SocialTitle("Disable account?")
            SocialBody("You will be signed out and your profile will be hidden. Your data stays until you sign in again.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialPrimaryButton(
                    "Disable",
                    onClick = { disableDialog = false; profile.disableAccount(); onDeleted() },
                    danger = true,
                    modifier = Modifier.weight(1f)
                )
                SocialGhostButton("Cancel", onClick = { disableDialog = false }, modifier = Modifier.weight(1f))
            }
        }
    }

    if (deleteDialog) {
        SocialDialog(onDismiss = { deleteDialog = false }) {
            SocialTitle("Delete account?")
            SocialBody("This permanently deletes your Aerix profile, friends, requests, messages, posts and uploaded skin or cape data.")
            SocialBody("Your Minecraft purchase and Microsoft or Mojang account are not deleted.", size = 12.sp)
            BasicTextField(
                value = typed,
                onValueChange = { typed = it.uppercase().take(6) },
                singleLine = true,
                textStyle = TextStyle(color = SocialColors.textPrimary, fontSize = 15.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(SocialDimens.controlRadius))
                    .background(SocialColors.panelSoft)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (typed.isEmpty()) SocialBody("Type DELETE to confirm", size = 14.sp, color = SocialColors.textMuted)
                        inner()
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialPrimaryButton(
                    "Delete account",
                    onClick = {
                        deleteDialog = false
                        scope.launch {
                            // Deleting also revokes the Google link, so the account sheet asks
                            // again instead of handing back the deleted profile.
                            GoogleAuth.signOut(context)
                            profile.deleteAccount()
                            onDeleted()
                        }
                    },
                    enabled = typed == "DELETE",
                    danger = true,
                    modifier = Modifier.weight(1f)
                )
                SocialGhostButton("Cancel", onClick = { deleteDialog = false }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SocialStageRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SocialDimens.row)
            .clip(RoundedCornerShape(SocialDimens.controlRadius))
            .background(SocialColors.panelSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SocialBody(title, size = 14.sp, color = SocialColors.textPrimary, modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.aerix_social_ic_chevron),
            contentDescription = null,
            tint = SocialColors.textMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

/* --------------------------------------------------------- notifications ----- */

@Composable
fun SocialSettingsNotificationsScreen(viewModel: SocialSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val notifications = state.notifications

    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) {
                SocialSectionTitle("Notify me about")
                SocialToggleRow("Friend requests", notifications.friendRequests, viewModel::setFriendRequestNotifications, iconRes = R.drawable.aerix_social_ic_friends)
                SocialToggleRow("Private messages", notifications.privateMessages, viewModel::setPrivateMessageNotifications, iconRes = R.drawable.aerix_social_ic_chat)
                SocialToggleRow("Party invitations", notifications.partyInvites, viewModel::setPartyInviteNotifications, iconRes = R.drawable.aerix_social_ic_party)
                SocialToggleRow("Server invitations", notifications.serverInvites, viewModel::setServerInviteNotifications, iconRes = R.drawable.aerix_social_ic_instance)
            }
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) {
                SocialSectionTitle("Delivery")
                SocialToggleRow("In-launcher alerts", notifications.inLauncher, viewModel::setInLauncher)
                SocialToggleRow("In-game social alerts", notifications.inGame, viewModel::setInGame, subtitle = "Small notices while playing")
                SocialToggleRow("Message preview", notifications.messagePreview, viewModel::setMessagePreview, subtitle = "Show message text in alerts")
                SocialToggleRow("Android notifications", notifications.androidNotifications, viewModel::setAndroidNotifications, subtitle = "Permission is controlled by Android")
                Spacer(Modifier.weight(1f))
                SocialPrimaryButton("Save changes", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/* ------------------------------------------------------------------ safety ----- */

@Composable
fun SocialSafetyScreen(
    viewModel: SocialSettingsViewModel,
    onBack: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    // Real blocked accounts, read from the repository that owns them.
    val blocked = state.blocked
    var reportOpen by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf(blocked.firstOrNull()?.id) }

    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(1.2f).fillMaxSize().verticalScroll(rememberScrollState())) {
                SocialSectionTitle("Blocked accounts")
                if (blocked.isEmpty()) {
                    SocialBody("Nobody is blocked.", size = 12.sp, color = SocialColors.textMuted)
                } else {
                    blocked.forEach { user ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SocialAvatar(avatar = user.avatar, size = 32.dp)
                            SocialBody(user.name, size = 14.sp, color = SocialColors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                            SocialGhostButton("Unblock", onClick = { viewModel.unblock(user.id) })
                        }
                    }
                }
                SocialBody("Blocked accounts cannot message you or send party and server invitations.", size = 11.sp, color = SocialColors.textMuted)
            }
            SocialPanel(modifier = Modifier.weight(0.9f).fillMaxSize()) {
                SocialSectionTitle("Get help")
                SocialStageRow("Report a concern") { reportOpen = true }
                SocialStageRow("Social guidelines") { }
                SocialBody("Muting hides alerts. Blocking stops contact.", size = 11.sp, color = SocialColors.textMuted)
                Spacer(Modifier.weight(1f))
                SocialGhostButton("Delete account", onClick = onDeleteAccount, modifier = Modifier.fillMaxWidth())
                SocialGhostButton("Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (reportOpen) {
        SocialDialog(onDismiss = { reportOpen = false }) {
            SocialTitle("Report a concern")
            SocialBody(
                reportTarget?.let { id -> "About ${blocked.firstOrNull { it.id == id }?.name ?: id}" }
                    ?: "Pick the account in the friends list first, then report it from their profile.",
                size = 12.sp,
                color = SocialColors.textMuted
            )
            listOf("Harassment", "Spam or scam", "Inappropriate name or skin", "Threats", "Something else").forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SocialColors.panelSoft)
                        .clickable {
                            val target = reportTarget
                            if (target != null) viewModel.report(target, reason)
                            reportOpen = false
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    SocialBody(reason, size = 14.sp, color = SocialColors.textPrimary)
                }
            }
            SocialBody("Reports are recorded with the account's recent activity.", size = 11.sp, color = SocialColors.textMuted)
        }
    }
}
