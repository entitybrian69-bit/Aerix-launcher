/*
 * Aerix Launcher Social layer.
 *
 * Profile, privacy, notifications and safety. Every toggle writes straight through to
 * DataStore, which is why the values survive a restart.
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
import net.kdt.pojavlaunch.aerix.social.data.SocialWardrobe
import net.kdt.pojavlaunch.aerix.social.ui.SocialAvatar
import net.kdt.pojavlaunch.aerix.social.ui.SocialBody
import net.kdt.pojavlaunch.aerix.social.ui.SocialColors
import net.kdt.pojavlaunch.aerix.social.ui.SocialDialog
import net.kdt.pojavlaunch.aerix.social.ui.SocialDimens
import net.kdt.pojavlaunch.aerix.social.ui.SocialDropdownRow
import net.kdt.pojavlaunch.aerix.social.ui.SocialGhostButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialPage
import net.kdt.pojavlaunch.aerix.social.ui.SocialPanel
import net.kdt.pojavlaunch.aerix.social.ui.SocialPrimaryButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialSectionTitle
import net.kdt.pojavlaunch.aerix.social.ui.SocialTitle
import net.kdt.pojavlaunch.aerix.social.ui.SocialToggleRow
import net.kdt.pojavlaunch.aerix.social.vm.ProfileUiState
import net.kdt.pojavlaunch.aerix.social.vm.SocialSettingsViewModel

/* ------------------------------------------------------------------ profile ----- */

@Composable
fun SocialProfileScreen(
    profile: ProfileUiState,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenSkin: () -> Unit,
    onOpenCape: () -> Unit,
    onSignOut: () -> Unit
) {
    var confirmSignOut by remember { mutableStateOf(false) }

    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(0.9f).fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SocialAvatar(avatar = SocialWardrobe.skins.firstOrNull { it.id == profile.skinId }?.avatar ?: 1, size = 132.dp)
                }
                SocialBody("Skin · ${SocialWardrobe.skinName(profile.skinId)}", size = 12.sp, color = SocialColors.textMuted)
            }
            SocialPanel(modifier = Modifier.weight(1.1f).fillMaxSize()) {
                SocialSectionTitle("Aerix profile")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialAvatar(avatar = 6, size = 42.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        SocialBody(profile.name.ifBlank { "Nova" }, size = 16.sp, color = SocialColors.textPrimary, weight = FontWeight.SemiBold)
                        SocialBody("Google account", size = 12.sp, color = SocialColors.textMuted)
                    }
                    SocialGhostButton("Edit name", onClick = {})
                }
                ProfileRow("Skin", SocialWardrobe.skinName(profile.skinId), onOpenSkin)
                ProfileRow("Cape (optional)", SocialWardrobe.capeName(profile.capeId), onOpenCape)
                ProfileRow("Privacy & presence", "Control what your friends can see", onOpenPrivacy)
                ProfileRow("Notifications", "Choose what Aerix notifies you about", onOpenNotifications)
                ProfileRow("Safety", "Blocked accounts and reports", onOpenSafety)
                Spacer(Modifier.weight(1f))
                SocialGhostButton(
                    text = "Sign out",
                    onClick = { confirmSignOut = true },
                    iconRes = R.drawable.aerix_social_ic_logout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (confirmSignOut) {
        SocialDialog(onDismiss = { confirmSignOut = false }) {
            SocialTitle("Sign out of Aerix?")
            SocialBody("Friends, requests and accepted-friend messages stay on your account. Sign in again to access them on this device.")
            SocialBody("Your Minecraft account and local instances are not deleted.")
            SocialBody("Unsaved changes will be discarded.", size = 12.sp, color = SocialColors.textSecondary)
            SocialBody("Other signed-in devices are not affected.", size = 12.sp, color = SocialColors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialPrimaryButton("Sign out", onClick = { confirmSignOut = false; onSignOut() }, modifier = Modifier.weight(1f))
                SocialGhostButton("Cancel", onClick = { confirmSignOut = false }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProfileRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(SocialDimens.controlRadius))
            .background(SocialColors.panelSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SocialBody(title, size = 14.sp, color = SocialColors.textPrimary, weight = FontWeight.Medium)
            SocialBody(subtitle, size = 11.sp, color = SocialColors.textMuted, maxLines = 1)
        }
        Icon(
            painter = painterResource(R.drawable.aerix_social_ic_chevron),
            contentDescription = null,
            tint = SocialColors.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/* ------------------------------------------------------------------ privacy ----- */

private val contactOptions = listOf("Everyone", "Accepted friends only", "Nobody")

@Composable
fun SocialPrivacyScreen(viewModel: SocialSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val privacy = state.privacy

    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize()) {
                SocialSectionTitle("Presence")
                SocialToggleRow(
                    title = "Online status",
                    subtitle = "Friends can see when you are online",
                    checked = privacy.onlineStatus,
                    onCheckedChange = viewModel::setOnlineStatus,
                    iconRes = R.drawable.aerix_social_ic_profile
                )
                SocialToggleRow(
                    title = "Share game activity",
                    subtitle = "Friends see Playing Minecraft, never a server address",
                    checked = privacy.shareActivity,
                    onCheckedChange = viewModel::setShareActivity,
                    iconRes = R.drawable.aerix_social_ic_party
                )
                SocialToggleRow(
                    title = "Show server details",
                    subtitle = "Off by default; friends see the server name only when you choose",
                    checked = privacy.showServerDetails,
                    onCheckedChange = viewModel::setShowServerDetails,
                    iconRes = R.drawable.aerix_social_ic_shield
                )
            }
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize()) {
                SocialSectionTitle("Who can contact you")
                SocialDropdownRow(
                    title = "Friend requests",
                    value = privacy.friendRequests,
                    options = contactOptions,
                    onSelect = viewModel::setFriendRequests,
                    iconRes = R.drawable.aerix_social_ic_friends
                )
                SocialDropdownRow(
                    title = "Private messages",
                    value = privacy.privateMessages,
                    options = contactOptions,
                    onSelect = viewModel::setPrivateMessages,
                    iconRes = R.drawable.aerix_social_ic_chat
                )
                SocialDropdownRow(
                    title = "Party invitations",
                    value = privacy.partyInvites,
                    options = contactOptions,
                    onSelect = viewModel::setPartyInvites,
                    iconRes = R.drawable.aerix_social_ic_party
                )
                Spacer(Modifier.weight(1f))
                SocialBody("Changes are saved as you make them.", size = 11.sp, color = SocialColors.textMuted)
                SocialPrimaryButton("Done", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/* ------------------------------------------------------------ notifications ----- */

@Composable
fun SocialNotificationsScreen(viewModel: SocialSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val notifications = state.notifications

    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize()) {
                SocialSectionTitle("Notify me about")
                SocialToggleRow("Friend requests", notifications.friendRequests, viewModel::setFriendRequestNotifications, iconRes = R.drawable.aerix_social_ic_friends)
                SocialToggleRow(
                    "Private messages",
                    notifications.privateMessages,
                    viewModel::setPrivateMessageNotifications,
                    subtitle = "Accepted friends only",
                    iconRes = R.drawable.aerix_social_ic_chat
                )
                SocialToggleRow("Party invitations", notifications.partyInvites, viewModel::setPartyInviteNotifications, iconRes = R.drawable.aerix_social_ic_party)
                SocialToggleRow("Server invitations", notifications.serverInvites, viewModel::setServerInviteNotifications, iconRes = R.drawable.aerix_social_ic_link)
            }
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize()) {
                SocialSectionTitle("Delivery")
                SocialToggleRow("In-launcher alerts", notifications.inLauncher, viewModel::setInLauncher, iconRes = R.drawable.aerix_ic_info_outlined)
                SocialToggleRow(
                    "In-game social alerts",
                    notifications.inGame,
                    viewModel::setInGame,
                    subtitle = "Small notices while playing",
                    iconRes = R.drawable.aerix_social_ic_party
                )
                SocialToggleRow(
                    "Message preview",
                    notifications.messagePreview,
                    viewModel::setMessagePreview,
                    subtitle = "Hide message text in alerts",
                    iconRes = R.drawable.aerix_social_ic_chat
                )
                SocialToggleRow(
                    "Android notifications",
                    notifications.androidNotifications,
                    viewModel::setAndroidNotifications,
                    subtitle = "Device notification permission is controlled by Android",
                    iconRes = R.drawable.aerix_social_ic_shield
                )
                Spacer(Modifier.weight(1f))
                SocialPrimaryButton("Save changes", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/* ------------------------------------------------------------------- safety ----- */

@Composable
fun SocialSafetyScreen(onBack: () -> Unit, onDeleteAccount: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var reportOpen by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(listOf("FrostByte", "EchoFox")) }

    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(1.2f).fillMaxSize()) {
                SocialSectionTitle("Blocked accounts")
                if (blocked.isEmpty()) {
                    SocialBody("Nobody is blocked.", size = 12.sp, color = SocialColors.textMuted)
                } else {
                    blocked.forEach { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = SocialDimens.row)
                                .clip(RoundedCornerShape(SocialDimens.controlRadius))
                                .background(SocialColors.panelSoft)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SocialAvatar(avatar = 4, size = 36.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                SocialBody(name, size = 14.sp, color = SocialColors.textPrimary)
                                SocialBody("@${name.lowercase()}", size = 11.sp, color = SocialColors.textMuted)
                            }
                            SocialGhostButton("Unblock", onClick = { blocked = blocked - name })
                        }
                    }
                }
                SocialBody("Blocked accounts cannot message you or send friend and party invitations.", size = 11.sp, color = SocialColors.textMuted)
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SocialDimens.controlRadius))
                        .background(SocialColors.danger.copy(alpha = 0.16f))
                        .clickable { confirmDelete = true }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Delete Aerix account", color = SocialColors.danger, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        painter = painterResource(R.drawable.aerix_social_ic_chevron),
                        contentDescription = null,
                        tint = SocialColors.danger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            SocialPanel(modifier = Modifier.weight(0.9f).fillMaxSize()) {
                SocialSectionTitle("Get help")
                ProfileRow("Report a concern", "Report a profile or conversation") { reportOpen = true }
                ProfileRow("Social guidelines", "How Aerix keeps play safe") {}
                ProfileRow("Explain", "Muting hides alerts. Blocking stops contact.") {}
                Spacer(Modifier.weight(1f))
                SocialBody("Confirmation required. Unblocking does not restore a friendship.", size = 11.sp, color = SocialColors.textMuted)
                SocialGhostButton("Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (confirmDelete) {
        SocialDialog(onDismiss = { confirmDelete = false }) {
            SocialTitle("Delete Aerix account?")
            SocialBody("This permanently deletes your Aerix profile, friends, requests, accepted-friend messages, blocked list, and uploaded skin or cape data.")
            SocialBody("Friends and messages are removed from Aerix.", size = 12.sp)
            SocialBody("Blocked and report records are handled under our privacy policy.", size = 12.sp)
            SocialBody("Your Minecraft purchase and Microsoft or Mojang account are not deleted.", size = 12.sp)
            SocialBody("Local Minecraft instances on this device are not deleted.", size = 12.sp)
            BasicTextField(
                value = typed,
                onValueChange = { typed = it.uppercase().take(6) },
                singleLine = true,
                textStyle = TextStyle(color = SocialColors.textPrimary, fontSize = 15.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SocialDimens.controlRadius))
                    .background(SocialColors.panelSoft)
                    .border(BorderStroke(1.dp, SocialColors.border), RoundedCornerShape(SocialDimens.controlRadius))
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
                    onClick = { confirmDelete = false; onDeleteAccount() },
                    enabled = typed == "DELETE",
                    danger = true,
                    modifier = Modifier.weight(1f)
                )
                SocialGhostButton("Cancel", onClick = { confirmDelete = false }, modifier = Modifier.weight(1f))
            }
        }
    }

    if (reportOpen) {
        SocialDialog(onDismiss = { reportOpen = false }) {
            SocialTitle("Report a concern")
            SocialBody("What is the problem?", size = 13.sp)
            listOf("Harassment", "Spam or scam", "Inappropriate name or skin", "Threats", "Something else").forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SocialColors.panelSoft)
                        .clickable { reportOpen = false }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    SocialBody(reason, size = 14.sp, color = SocialColors.textPrimary)
                }
            }
            SocialBody("Reports are reviewed by Aerix moderators. Your report may include recent messages from this chat.", size = 11.sp, color = SocialColors.textMuted)
            SocialGhostButton("Cancel", onClick = { reportOpen = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}
