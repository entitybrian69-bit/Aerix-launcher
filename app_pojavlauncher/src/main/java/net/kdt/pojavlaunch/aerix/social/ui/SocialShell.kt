/*
 * Aerix Launcher Social layer.
 *
 * The second launcher. It replaces the Play dashboard for as long as it is open: its own
 * background, its own 84dp rail, its own top bar and its own screens. The launcher rail,
 * dashboard and play button are not composed while this is on screen.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.SocialGraph
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialAppearanceScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialAuthScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialCapeScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialChatScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialDashboardScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialNotificationsScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialPrivacyScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialProfileScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialSafetyScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialSkinScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialVoiceScreen
import net.kdt.pojavlaunch.aerix.social.vm.SocialChatViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialDashboardViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialProfileViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialRoute
import net.kdt.pojavlaunch.aerix.social.vm.SocialSettingsViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialShellViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialTab

private val socialFactory = viewModelFactory {
    initializer { SocialShellViewModel() }
    initializer { SocialProfileViewModel(SocialGraph.store) }
    initializer { SocialDashboardViewModel(SocialGraph.repository) }
    initializer { SocialChatViewModel(SocialGraph.repository) }
    initializer { SocialSettingsViewModel(SocialGraph.store) }
}

/**
 * Root of the second launcher. Everything below is drawn by this composable; the Play
 * dashboard is not composed at all while it is visible.
 */
@Composable
fun SocialShell(
    shell: SocialShellViewModel,
    onExitToLauncher: () -> Unit,
    modifier: Modifier = Modifier
) {
    val route by shell.route.collectAsState()
    val tab by shell.tab.collectAsState()

    val profileVm: SocialProfileViewModel = viewModel(factory = socialFactory)
    val dashboardVm: SocialDashboardViewModel = viewModel(factory = socialFactory)
    val chatVm: SocialChatViewModel = viewModel(factory = socialFactory)
    val settingsVm: SocialSettingsViewModel = viewModel(factory = socialFactory)

    val profile by profileVm.state.collectAsState()

    BackHandler(enabled = true) {
        if (!shell.back()) onExitToLauncher()
    }

    SocialBackground(modifier = modifier) {
        Row(modifier = Modifier.fillMaxSize()) {
            SocialRail(
                current = route,
                onHome = { shell.reset(SocialRoute.DASHBOARD) },
                onChats = { shell.selectTab(SocialTab.CHATS) },
                onParty = { shell.selectTab(SocialTab.PARTY) },
                onProfile = { shell.push(SocialRoute.PROFILE) },
                onExit = onExitToLauncher
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                SocialHeader(
                    route = route,
                    name = profile.name,
                    onBack = { if (!shell.back()) onExitToLauncher() },
                    onProfile = { shell.push(SocialRoute.PROFILE) },
                    onSettings = { shell.push(SocialRoute.PRIVACY) }
                )
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        (fadeIn(tween(SocialMotion.SCREEN_MS)) + slideInHorizontally(tween(SocialMotion.SCREEN_MS)) { it / 12 })
                            .togetherWith(fadeOut(tween(SocialMotion.SCREEN_MS)) + slideOutHorizontally(tween(SocialMotion.SCREEN_MS)) { -it / 12 })
                    },
                    label = "socialRoute"
                ) { target ->
                    when (target) {
                        SocialRoute.AUTH -> SocialAuthScreen(
                            inProgress = profile.signInInProgress,
                            onSignIn = {
                                profileVm.signIn()
                                shell.push(SocialRoute.APPEARANCE)
                            },
                            onNotNow = onExitToLauncher
                        )
                        SocialRoute.APPEARANCE -> SocialAppearanceScreen(
                            profile = profileVm,
                            name = profile.name,
                            skinId = profile.skinId,
                            capeId = profile.capeId,
                            nameError = profile.nameError,
                            onOpenSkin = { shell.push(SocialRoute.SKIN) },
                            onOpenCape = { shell.push(SocialRoute.CAPE) },
                            onFinish = { shell.reset(SocialRoute.DASHBOARD) }
                        )
                        SocialRoute.DASHBOARD -> SocialDashboardScreen(
                            viewModel = dashboardVm,
                            tab = tab,
                            onTabSelected = { shell.selectTab(it) },
                            onMessage = { friend ->
                                chatVm.open(friend)
                                shell.push(SocialRoute.CHAT)
                            },
                            onOpenPrivacy = { shell.push(SocialRoute.PRIVACY) },
                            onOpenSafety = { shell.push(SocialRoute.SAFETY) },
                            onOpenVoice = { shell.push(SocialRoute.VOICE) }
                        )
                        SocialRoute.CHAT -> SocialChatScreen(
                            viewModel = chatVm,
                            onBack = { shell.back() },
                            onVoice = { shell.push(SocialRoute.VOICE) }
                        )
                        SocialRoute.PROFILE -> SocialProfileScreen(
                            profile = profile,
                            onBack = { shell.back() },
                            onOpenPrivacy = { shell.push(SocialRoute.PRIVACY) },
                            onOpenNotifications = { shell.push(SocialRoute.NOTIFICATIONS) },
                            onOpenSafety = { shell.push(SocialRoute.SAFETY) },
                            onOpenSkin = { shell.push(SocialRoute.SKIN) },
                            onOpenCape = { shell.push(SocialRoute.CAPE) },
                            onSignOut = { profileVm.signOut(); shell.reset(SocialRoute.AUTH) }
                        )
                        SocialRoute.PRIVACY -> SocialPrivacyScreen(
                            viewModel = settingsVm,
                            onBack = { shell.back() }
                        )
                        SocialRoute.NOTIFICATIONS -> SocialNotificationsScreen(
                            viewModel = settingsVm,
                            onBack = { shell.back() }
                        )
                        SocialRoute.SAFETY -> SocialSafetyScreen(
                            onBack = { shell.back() },
                            onDeleteAccount = { shell.reset(SocialRoute.AUTH) }
                        )
                        SocialRoute.SKIN -> SocialSkinScreen(
                            profile = profileVm,
                            selected = profile.skinId,
                            onBack = { shell.back() }
                        )
                        SocialRoute.CAPE -> SocialCapeScreen(
                            profile = profileVm,
                            selected = profile.capeId,
                            onBack = { shell.back() }
                        )
                        SocialRoute.VOICE -> SocialVoiceScreen(onBack = { shell.back() })
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialHeader(
    route: SocialRoute,
    name: String,
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit
) {
    val title = when (route) {
        SocialRoute.AUTH -> "Aerix account"
        SocialRoute.APPEARANCE -> "Create your Aerix profile"
        SocialRoute.DASHBOARD -> "Social"
        SocialRoute.CHAT -> "Chat"
        SocialRoute.PROFILE -> "My profile"
        SocialRoute.PRIVACY -> "Privacy & presence"
        SocialRoute.NOTIFICATIONS -> "Notifications"
        SocialRoute.SAFETY -> "Safety"
        SocialRoute.SKIN -> "Choose skin"
        SocialRoute.CAPE -> "Choose cape"
        SocialRoute.VOICE -> "Voice chat"
    }
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SocialDimens.pagePaddingH, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(SocialDimens.touch),
            shape = CircleShape,
            color = SocialColors.panelRaised,
            border = BorderStroke(1.dp, SocialColors.border),
            onClick = onBack
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_arrow_back),
                    contentDescription = "Back",
                    tint = SocialColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(text = title, color = SocialColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (route == SocialRoute.DASHBOARD || route == SocialRoute.CHAT) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(SocialDimens.controlRadius))
                    .clickable(onClick = onProfile)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SocialAvatar(avatar = 6, size = 32.dp)
                Text(text = name.ifBlank { "Nova" }, color = SocialColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = SocialColors.panelRaised,
                border = BorderStroke(1.dp, SocialColors.border),
                onClick = { menuOpen = true }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_social_ic_more),
                        contentDescription = "More",
                        tint = SocialColors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
    if (menuOpen) {
        SocialDialog(onDismiss = { menuOpen = false }) {
            SocialSectionTitle("Social menu")
            SocialGhostButton("Privacy & presence", onClick = { menuOpen = false; onSettings() }, modifier = Modifier.fillMaxWidth())
            SocialGhostButton("My profile", onClick = { menuOpen = false; onProfile() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SocialRail(
    current: SocialRoute,
    onHome: () -> Unit,
    onChats: () -> Unit,
    onParty: () -> Unit,
    onProfile: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(SocialDimens.railWidth)
            .fillMaxHeight()
            .background(Color(0xFF171029))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SocialColors.accent),
            contentAlignment = Alignment.Center
        ) {
            Text("A", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(8.dp))
        RailItem(R.drawable.aerix_social_ic_friends, "Friends", current == SocialRoute.DASHBOARD, onHome)
        RailItem(R.drawable.aerix_social_ic_chat, "Chats", current == SocialRoute.CHAT, onChats)
        RailItem(R.drawable.aerix_social_ic_party, "Party", current == SocialRoute.VOICE, onParty)
        RailItem(R.drawable.aerix_social_ic_profile, "Profile", current == SocialRoute.PROFILE, onProfile)
        Spacer(Modifier.weight(1f))
        RailItem(R.drawable.aerix_social_ic_exit, "Exit", false, onExit)
    }
}

@Composable
private fun RailItem(iconRes: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) SocialColors.accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = if (selected) Color.White else SocialColors.textMuted,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = if (selected) Color.White else SocialColors.textMuted,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
