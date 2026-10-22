/*
 * Aerix Launcher Social layer.
 *
 * My profile and Edit profile: avatar, display name, username (one change per week), bio,
 * followers/following/likes/posts counters and every platform link, each opening its page.
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.data.LinkPlatform
import net.kdt.pojavlaunch.aerix.social.data.SocialWardrobe
import net.kdt.pojavlaunch.aerix.social.data.formatCooldown
import net.kdt.pojavlaunch.aerix.social.ui.SocialAvatar
import net.kdt.pojavlaunch.aerix.social.ui.SocialBody
import net.kdt.pojavlaunch.aerix.social.ui.SocialChip
import net.kdt.pojavlaunch.aerix.social.ui.SocialColors
import net.kdt.pojavlaunch.aerix.social.ui.SocialDimens
import net.kdt.pojavlaunch.aerix.social.ui.SocialDropdownRow
import net.kdt.pojavlaunch.aerix.social.ui.SocialGhostButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialLinkRow
import net.kdt.pojavlaunch.aerix.social.ui.SocialPage
import net.kdt.pojavlaunch.aerix.social.ui.SocialPanel
import net.kdt.pojavlaunch.aerix.social.ui.SocialPrimaryButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialSectionTitle
import net.kdt.pojavlaunch.aerix.social.ui.SocialTextField
import net.kdt.pojavlaunch.aerix.social.ui.SocialTitle
import net.kdt.pojavlaunch.aerix.social.ui.linkPlatformIcon
import net.kdt.pojavlaunch.aerix.social.vm.ProfileUiState
import net.kdt.pojavlaunch.aerix.social.vm.SocialProfileViewModel

@Composable
fun SocialProfileScreen(
    profile: ProfileUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSafety: () -> Unit,
    onSignOut: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(0.85f).fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SocialAvatar(avatar = profile.avatar, size = 128.dp)
                        SocialTitle(profile.name.ifBlank { "Nova" })
                        SocialBody("@${profile.username.ifBlank { profile.name.lowercase() }}", size = 12.sp, color = SocialColors.textMuted)
                        SocialBody("Skin · ${SocialWardrobe.skinName(profile.skinId)}", size = 11.sp, color = SocialColors.textMuted)
                        SocialBody("Cape · ${SocialWardrobe.capeName(profile.capeId)}", size = 11.sp, color = SocialColors.textMuted)
                    }
                }
            }
            SocialPanel(modifier = Modifier.weight(1.15f).fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ProfileCount("Likes", 320)
                    ProfileCount("Followers", 58)
                    ProfileCount("Following", 74)
                    ProfileCount("Posts", 6)
                }
                SocialBody(profile.bio.ifBlank { "Add a bio so people know what you play." }, size = 13.sp, color = SocialColors.textPrimary)
                if (profile.links.isEmpty()) {
                    SocialBody("No links yet. Add YouTube, Instagram, Discord and more in Edit profile.", size = 11.sp, color = SocialColors.textMuted)
                } else {
                    profile.links.forEach { link ->
                        SocialLinkRow(link = link, onOpen = { runCatching { uriHandler.openUri(link.url) } })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialPrimaryButton("Edit profile", onClick = onEdit, iconRes = R.drawable.aerix_ic_edit_outlined, modifier = Modifier.weight(1f))
                    SocialGhostButton("Settings", onClick = onOpenSettings, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialGhostButton("Safety", onClick = onOpenSafety, modifier = Modifier.weight(1f))
                    SocialGhostButton("Sign out", onClick = onSignOut, iconRes = R.drawable.aerix_social_ic_logout, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ProfileCount(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = SocialColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        SocialBody(label, size = 10.sp, color = SocialColors.textMuted)
    }
}

@Composable
fun SocialEditProfileScreen(
    profile: SocialProfileViewModel,
    state: ProfileUiState,
    onBack: () -> Unit
) {
    val canChangeUsername = state.canChangeUsername()
    var platform by remember { mutableStateOf(LinkPlatform.YOUTUBE) }
    var linkValue by remember { mutableStateOf("") }

    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(0.8f).fillMaxSize().verticalScroll(rememberScrollState())) {
                SocialSectionTitle("Account logo")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..6).forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(if (state.avatar == index) SocialColors.accent else SocialColors.panelSoft)
                                .clickable { profile.selectAvatar(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            SocialAvatar(avatar = index, size = 38.dp)
                        }
                    }
                }
                SocialSectionTitle("Display name")
                SocialTextField(value = state.name, onValueChange = profile::onNameChange, placeholder = "Nova")
                SocialSectionTitle("Bio")
                SocialTextField(value = state.bio, onValueChange = profile::onBioChange, placeholder = "Tell people what you play")
                SocialBody("${state.bio.length}/140", size = 11.sp, color = SocialColors.textMuted)
            }
            SocialPanel(modifier = Modifier.weight(1.2f).fillMaxSize().verticalScroll(rememberScrollState())) {
                SocialSectionTitle("Username")
                SocialTextField(value = state.username, onValueChange = profile::onUsernameChange, placeholder = "nova_plays")
                if (state.nameError != null) {
                    SocialBody(state.nameError ?: "", size = 12.sp, color = SocialColors.danger)
                } else {
                    SocialBody(
                        if (canChangeUsername) "You can save a new username today."
                        else "Next username change in ${formatCooldown(state.cooldownRemaining())}.",
                        size = 12.sp,
                        color = if (canChangeUsername) SocialColors.success else SocialColors.textMuted
                    )
                }
                SocialPrimaryButton(
                    text = if (canChangeUsername) "Save username" else "Username locked for a week",
                    onClick = {
                        if (profile.changeUsername(state.username)) profile.saveCharacter()
                    },
                    enabled = canChangeUsername
                )
                SocialSectionTitle("Links")
                state.links.forEach { link ->
                    SocialLinkRow(
                        link = link,
                        onOpen = { },
                        onRemove = { profile.removeLink(link.platform) }
                    )
                }
                SocialDropdownRow(
                    title = "Platform",
                    value = platform.label,
                    options = LinkPlatform.entries.map { it.label },
                    onSelect = { label -> platform = LinkPlatform.entries.first { it.label == label } },
                    iconRes = linkPlatformIcon(platform)
                )
                SocialTextField(value = linkValue, onValueChange = { linkValue = it }, placeholder = "@your-handle or full URL")
                SocialGhostButton(
                    text = "Add link",
                    onClick = {
                        profile.addLink(platform, linkValue)
                        linkValue = ""
                    },
                    iconRes = R.drawable.aerix_ic_add,
                    modifier = Modifier.fillMaxWidth()
                )
                SocialBody("Tapping a link on your profile opens that page directly.", size = 11.sp, color = SocialColors.textMuted)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialPrimaryButton("Done", onClick = { profile.saveCharacter(); onBack() }, modifier = Modifier.weight(1f))
                    SocialGhostButton("Close", onClick = onBack, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
