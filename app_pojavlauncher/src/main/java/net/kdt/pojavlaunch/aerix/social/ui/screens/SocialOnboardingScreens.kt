/*
 * Aerix Launcher Social layer.
 *
 * Sign-in, appearance setup, skin/cape pickers and the voice permission intro.
 * These are the first screens a new account sees, so every state has a way out.
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
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
import net.kdt.pojavlaunch.aerix.social.ui.SocialTitle
import net.kdt.pojavlaunch.aerix.social.ui.socialAvatarRes
import net.kdt.pojavlaunch.aerix.social.vm.SocialProfileViewModel

@Composable
fun SocialAuthScreen(
    inProgress: Boolean,
    onSignIn: () -> Unit,
    onNotNow: () -> Unit
) {
    SocialPage {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            SocialTitle("Your friends, together.", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            SocialBody("Friends, messages and voice in one place.", size = 14.sp)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(1, 2, 3, 4).forEach { index ->
                    SocialAvatar(avatar = index, size = 52.dp)
                }
            }
            Spacer(Modifier.height(28.dp))
            SocialPanel(modifier = Modifier.fillMaxWidth()) {
                SocialTitle("Connect with Aerix")
                SocialBody("Friends, messages and voice in one place.", size = 13.sp)
                SocialPrimaryButton(
                    text = if (inProgress) "Connecting…" else "Continue with Google",
                    onClick = onSignIn,
                    enabled = !inProgress,
                    iconRes = R.drawable.aerix_social_ic_google,
                    modifier = Modifier.fillMaxWidth()
                )
                SocialGhostButton(
                    text = "Not now",
                    onClick = onNotNow,
                    modifier = Modifier.fillMaxWidth()
                )
                SocialBody(
                    "Aerix accounts are separate from your Minecraft account.",
                    size = 12.sp,
                    color = SocialColors.textMuted
                )
                SocialBody(
                    "Google sign-in opens a secure system screen.",
                    size = 12.sp,
                    color = SocialColors.textMuted
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            SocialBody("Privacy", size = 12.sp, color = SocialColors.textSecondary)
            Spacer(Modifier.width(16.dp))
            SocialBody("Terms", size = 12.sp, color = SocialColors.textSecondary)
        }
    }
}

@Composable
fun SocialAppearanceScreen(
    profile: SocialProfileViewModel,
    name: String,
    skinId: String,
    capeId: String,
    nameError: String?,
    onOpenSkin: () -> Unit,
    onOpenCape: () -> Unit,
    onFinish: () -> Unit
) {
    SocialPage {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SocialPanel(modifier = Modifier.weight(0.85f).fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(socialAvatarRes(SocialWardrobe.skins.firstOrNull { it.id == skinId }?.avatar ?: 1)),
                            contentDescription = null,
                            modifier = Modifier.size(148.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        SocialBody("Front · Back", size = 12.sp, color = SocialColors.textMuted)
                    }
                }
            }
            SocialPanel(modifier = Modifier.weight(1.15f).fillMaxSize()) {
                SocialSectionTitle("Aerix name")
                BasicTextField(
                    value = name,
                    onValueChange = profile::onNameChange,
                    singleLine = true,
                    textStyle = TextStyle(color = SocialColors.textPrimary, fontSize = 15.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SocialDimens.controlRadius))
                        .background(SocialColors.panelSoft)
                        .border(BorderStroke(1.dp, SocialColors.border), RoundedCornerShape(SocialDimens.controlRadius))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (name.isEmpty()) SocialBody("Nova", size = 15.sp, color = SocialColors.textMuted)
                            inner()
                        }
                    }
                )
                if (nameError != null) {
                    SocialBody(nameError, size = 12.sp, color = SocialColors.danger)
                } else {
                    SocialBody("Name available · this is your Aerix name, not your Minecraft login", size = 12.sp, color = SocialColors.success)
                }
                Spacer(Modifier.height(4.dp))
                WardrobeRow(
                    title = "Skin",
                    subtitle = SocialWardrobe.skinName(skinId),
                    avatar = SocialWardrobe.skins.firstOrNull { it.id == skinId }?.avatar ?: 1,
                    onClick = onOpenSkin
                )
                WardrobeRow(
                    title = "Cape (optional)",
                    subtitle = SocialWardrobe.capeName(capeId),
                    avatar = 0,
                    onClick = onOpenCape
                )
                Spacer(Modifier.weight(1f))
                SocialPrimaryButton(
                    text = "Finish setup",
                    onClick = { if (profile.saveAppearance()) onFinish() },
                    modifier = Modifier.fillMaxWidth()
                )
                SocialBody(
                    "You can change these later. In-game skin and cape visibility requires supported integration.",
                    size = 11.sp,
                    color = SocialColors.textMuted
                )
            }
        }
    }
}

@Composable
private fun WardrobeRow(title: String, subtitle: String, avatar: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            if (avatar > 0) {
                SocialAvatar(avatar = avatar, size = 36.dp)
            } else {
                Text("A", color = SocialColors.accentBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
fun SocialSkinScreen(
    profile: SocialProfileViewModel,
    selected: String,
    onBack: () -> Unit
) {
    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(0.85f).fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(socialAvatarRes(SocialWardrobe.skins.firstOrNull { it.id == selected }?.avatar ?: 1)),
                        contentDescription = null,
                        modifier = Modifier.size(148.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SocialGhostButton("Classic", onClick = {}, modifier = Modifier.weight(1f))
                    SocialGhostButton("Slim", onClick = {}, modifier = Modifier.weight(1f))
                }
            }
            SocialPanel(modifier = Modifier.weight(1.15f).fillMaxSize()) {
                SocialSectionTitle("Your skins")
                SocialBody("Import PNG", size = 13.sp, color = SocialColors.accentBright)
                val rows = SocialWardrobe.skins.chunked(3)
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { item ->
                            SkinTile(item, item.id == selected) { profile.selectSkin(item.id) }
                        }
                    }
                }
                SocialBody("PNG skin files · 64×64 or legacy 64×32", size = 11.sp, color = SocialColors.textMuted)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialPrimaryButton("Apply skin", onClick = onBack, modifier = Modifier.weight(1f))
                    SocialGhostButton("Cancel", onClick = onBack, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SkinTile(item: WardrobeItem, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(SocialDimens.controlRadius))
            .background(if (selected) SocialColors.accent.copy(alpha = 0.28f) else SocialColors.panelSoft)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SocialColors.accent else SocialColors.border),
                RoundedCornerShape(SocialDimens.controlRadius)
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SocialAvatar(avatar = item.avatar, size = 56.dp)
        SocialBody(item.label, size = 11.sp, color = SocialColors.textSecondary, maxLines = 1)
    }
}

@Composable
fun SocialCapeScreen(
    profile: SocialProfileViewModel,
    selected: String,
    onBack: () -> Unit
) {
    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(0.85f).fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.aerix_social_avatar_1),
                        contentDescription = null,
                        modifier = Modifier.size(140.dp)
                    )
                }
                SocialGhostButton("Import cape", onClick = {}, modifier = Modifier.fillMaxWidth())
            }
            SocialPanel(modifier = Modifier.weight(1.15f).fillMaxSize()) {
                SocialSectionTitle("Your capes")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialWardrobe.capes.forEach { item ->
                        Column(
                            modifier = Modifier
                                .width(88.dp)
                                .clip(RoundedCornerShape(SocialDimens.controlRadius))
                                .background(if (item.id == selected) SocialColors.accent.copy(alpha = 0.28f) else SocialColors.panelSoft)
                                .border(
                                    BorderStroke(if (item.id == selected) 2.dp else 1.dp, if (item.id == selected) SocialColors.accent else SocialColors.border),
                                    RoundedCornerShape(SocialDimens.controlRadius)
                                )
                                .clickable { profile.selectCape(item.id) }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (item.avatar == 0) SocialColors.panelRaised else SocialColors.accent.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (item.avatar == 0) "–" else "A", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            SocialBody(item.label, size = 11.sp, color = SocialColors.textSecondary, maxLines = 1)
                        }
                    }
                }
                SocialBody("For your Aerix profile. In-game cape visibility requires supported integration.", size = 11.sp, color = SocialColors.textMuted)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialPrimaryButton("Apply cape", onClick = onBack, modifier = Modifier.weight(1f))
                    SocialGhostButton("Cancel", onClick = onBack, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun SocialVoiceScreen(onBack: () -> Unit) {
    // The mock backend has no real capture: the screen shows the pre-permission explainer and
    // the "microphone denied" fallback, which is the state Android can leave us in.
    var promptOpen by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }

    SocialPage {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_social_ic_mic_off),
                            contentDescription = null,
                            tint = if (denied) SocialColors.danger else SocialColors.accentBright,
                            modifier = Modifier.size(72.dp)
                        )
                        SocialTitle(if (denied) "Microphone access is off" else "Talk with your party")
                        SocialBody(
                            if (denied) "You can still hear your party. Aerix cannot hear you until Android permission is granted."
                            else "Microphone access is optional",
                            size = 12.sp,
                            color = SocialColors.textMuted
                        )
                    }
                }
            }
            SocialPanel(modifier = Modifier.weight(1f).fillMaxSize()) {
                SocialSectionTitle(if (denied) "You can still play together" else "Before you enable your microphone")
                SocialBody("Microphone permission — Android will ask for access after you continue.")
                SocialBody("You stay in control — Join muted, then use mute or push-to-talk.")
                SocialBody("Listen without a microphone — You still hear the party without granting access.")
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (denied) {
                        SocialPrimaryButton("Join muted", onClick = onBack, modifier = Modifier.weight(1f))
                        SocialGhostButton(
                            "Open Android settings",
                            onClick = { denied = false },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        SocialPrimaryButton(
                            "Continue to permission",
                            onClick = { promptOpen = true },
                            modifier = Modifier.weight(1f)
                        )
                        SocialGhostButton("Not now", onClick = onBack, modifier = Modifier.weight(1f))
                    }
                }
                SocialBody(
                    if (denied) "Android controls permission prompts. Open Settings > Apps > Aerix > Permissions to allow the microphone."
                    else "Android controls permission prompts. An app switch or permission prompt may interrupt gameplay.",
                    size = 11.sp,
                    color = SocialColors.textMuted
                )
            }
        }
    }

    if (promptOpen) {
        SocialDialog(onDismiss = { promptOpen = false }) {
            SocialTitle("Allow Aerix to use your microphone?")
            SocialBody("Aerix uses the microphone only while you are in a voice party. You can revoke access at any time in Android settings.")
            SocialBody("You can join muted, or listen without a microphone.", size = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialPrimaryButton(
                    "Allow",
                    onClick = { promptOpen = false; onBack() },
                    modifier = Modifier.weight(1f)
                )
                SocialGhostButton(
                    "Deny",
                    onClick = { promptOpen = false; denied = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
