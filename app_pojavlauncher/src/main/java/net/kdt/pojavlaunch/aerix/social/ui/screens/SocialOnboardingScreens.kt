/*
 * Aerix Launcher Social layer.
 *
 * First run: Google sign-in / sign-up, then the character screen (name, username, skin,
 * cape and an optional theme), then the dashboard.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.auth.GoogleAccount
import net.kdt.pojavlaunch.aerix.social.auth.GoogleAuth
import net.kdt.pojavlaunch.aerix.social.auth.GoogleAuthResult
import net.kdt.pojavlaunch.aerix.social.auth.GoogleSignInVariant
import net.kdt.pojavlaunch.aerix.social.data.SocialWardrobe
import net.kdt.pojavlaunch.aerix.social.data.WardrobeItem
import net.kdt.pojavlaunch.aerix.social.ui.SocialAvatar
import net.kdt.pojavlaunch.aerix.social.ui.SocialBody
import net.kdt.pojavlaunch.aerix.social.ui.SocialColors
import net.kdt.pojavlaunch.aerix.social.ui.SocialDimens
import net.kdt.pojavlaunch.aerix.social.ui.SocialDialog
import net.kdt.pojavlaunch.aerix.social.ui.SocialGhostButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialPage
import net.kdt.pojavlaunch.aerix.social.ui.SocialPanel
import net.kdt.pojavlaunch.aerix.social.ui.SocialPrimaryButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialSectionTitle
import net.kdt.pojavlaunch.aerix.social.ui.SocialTextField
import net.kdt.pojavlaunch.aerix.social.ui.SocialTitle
import net.kdt.pojavlaunch.aerix.social.ui.socialAvatarRes
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.aerix.social.vm.SocialProfileViewModel

/**
 * Google account step. The real screen lives in [PortalAuthScreen]: portal, player, one wide
 * pill, and the Credential Manager flow behind it. This keeps the shell's call site stable.
 */
@Composable
fun SocialAuthScreen(
    inProgress: Boolean,
    googleError: String?,
    stage: String?,
    lastEmail: String,
    onStartGoogle: (creatingAccount: Boolean, variant: GoogleSignInVariant) -> Unit,
    onNotNow: () -> Unit
) {
    PortalAuthScreen(
        inProgress = inProgress,
        googleError = googleError,
        stage = stage,
        lastEmail = lastEmail,
        onStartGoogle = onStartGoogle,
        onNotNow = onNotNow
    )
}

/** Name, username, skin, cape and the optional theme — all changeable later. */
@Composable
fun SocialCharacterScreen(
    profile: SocialProfileViewModel,
    name: String,
    username: String,
    skinId: String,
    capeId: String,
    nameError: String?,
    onOpenSkin: () -> Unit,
    onOpenCape: () -> Unit,
    onOpenTheme: () -> Unit,
    onFinish: () -> Unit
) {
    SocialPage {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SocialPanel(modifier = Modifier.weight(0.8f).fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(socialAvatarRes(SocialWardrobe.skins.firstOrNull { it.id == skinId }?.avatar ?: 1)),
                            contentDescription = null,
                            modifier = Modifier.size(148.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        SocialBody("Front · Back", size = 12.sp, color = SocialColors.textMuted)
                        SocialBody(SocialWardrobe.capeName(capeId), size = 12.sp, color = SocialColors.textSecondary)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1.2f).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SocialSectionTitle("Display name")
                SocialTextField(value = name, onValueChange = profile::onNameChange, placeholder = "Nova")
                SocialSectionTitle("Username")
                SocialTextField(value = username, onValueChange = profile::onUsernameChange, placeholder = "nova_plays")
                if (nameError != null) {
                    SocialBody(nameError, size = 12.sp, color = SocialColors.danger)
                } else {
                    SocialBody(
                        "3–16 letters, numbers or underscores. Your username can be changed once a week.",
                        size = 12.sp,
                        color = SocialColors.textMuted
                    )
                }
                WardrobeRow(title = "Skin", subtitle = SocialWardrobe.skinName(skinId), avatar = SocialWardrobe.skins.firstOrNull { it.id == skinId }?.avatar ?: 1, onClick = onOpenSkin)
                WardrobeRow(title = "Cape (optional)", subtitle = SocialWardrobe.capeName(capeId), avatar = 0, onClick = onOpenCape)
                WardrobeRow(
                    title = "Theme",
                    subtitle = "Optional — uses the launcher wallpapers",
                    avatar = 0,
                    onClick = onOpenTheme,
                    iconRes = R.drawable.aerix_social_ic_theme
                )
                SocialPrimaryButton(
                    text = "Finish setup",
                    onClick = { if (profile.saveCharacter()) onFinish() },
                    modifier = Modifier.fillMaxWidth()
                )
                SocialBody("Everything here can be changed later in Customise and Edit profile.", size = 11.sp, color = SocialColors.textMuted)
            }
        }
    }
}

@Composable
private fun WardrobeRow(
    title: String,
    subtitle: String,
    avatar: Int,
    onClick: () -> Unit,
    iconRes: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SocialDimens.row)
            .clip(RoundedCornerShape(SocialDimens.controlRadius))
            .background(SocialColors.panelSoft)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(SocialColors.panelRaised),
            contentAlignment = Alignment.Center
        ) {
            when {
                avatar > 0 -> SocialAvatar(avatar = avatar, size = 36.dp)
                iconRes != null -> Icon(painterResource(iconRes), contentDescription = null, tint = SocialColors.accentBright, modifier = Modifier.size(18.dp))
                else -> Text("A", color = SocialColors.accentBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            SocialBody(title, color = SocialColors.textPrimary, size = 14.sp, weight = FontWeight.Medium)
            SocialBody(subtitle, size = 12.sp, color = SocialColors.textMuted)
        }
        Text("Choose", color = SocialColors.accentBright, fontSize = 13.sp)
    }
}

@Composable
fun SocialSkinScreen(profile: SocialProfileViewModel, selected: String, onBack: () -> Unit) {
    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(0.7f).fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(socialAvatarRes(SocialWardrobe.skins.firstOrNull { it.id == selected }?.avatar ?: 1)),
                        contentDescription = null,
                        modifier = Modifier.size(140.dp)
                    )
                }
                SocialBody("Skin · ${SocialWardrobe.skinName(selected)}", size = 12.sp, color = SocialColors.textMuted)
            }
            Column(modifier = Modifier.weight(1.3f).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialSectionTitle("Choose a skin")
                SocialWardrobe.skins.forEach { item ->
                    SkinTile(item = item, selected = item.id == selected, onClick = { profile.selectSkin(item.id) })
                }
                Spacer(Modifier.weight(1f))
                SocialPrimaryButton("Done", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SkinTile(item: WardrobeItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SocialDimens.controlRadius))
            .background(if (selected) SocialColors.accent.copy(alpha = 0.22f) else SocialColors.panelSoft)
            .border(BorderStroke(1.dp, if (selected) SocialColors.accent else SocialColors.border), RoundedCornerShape(SocialDimens.controlRadius))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SocialAvatar(avatar = item.avatar, size = 40.dp)
        SocialBody(item.label, size = 14.sp, color = SocialColors.textPrimary, modifier = Modifier.weight(1f))
        if (selected) Text("Selected", color = SocialColors.accentBright, fontSize = 12.sp)
    }
}

@Composable
fun SocialCapeScreen(profile: SocialProfileViewModel, selected: String, onBack: () -> Unit) {
    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(0.7f).fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(R.drawable.aerix_social_avatar_1),
                            contentDescription = null,
                            modifier = Modifier.size(120.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        SocialBody("Cape · ${SocialWardrobe.capeName(selected)}", size = 12.sp, color = SocialColors.textMuted)
                    }
                }
            }
            Column(modifier = Modifier.weight(1.3f).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialSectionTitle("Choose a cape")
                SocialWardrobe.capes.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(SocialDimens.controlRadius))
                            .background(if (item.id == selected) SocialColors.accent.copy(alpha = 0.22f) else SocialColors.panelSoft)
                            .border(
                                BorderStroke(1.dp, if (item.id == selected) SocialColors.accent else SocialColors.border),
                                RoundedCornerShape(SocialDimens.controlRadius)
                            )
                            .clickable { profile.selectCape(item.id) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SocialBody(item.label, size = 14.sp, color = SocialColors.textPrimary, modifier = Modifier.weight(1f))
                        if (item.id == selected) Text("Selected", color = SocialColors.accentBright, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                SocialPrimaryButton("Done", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
