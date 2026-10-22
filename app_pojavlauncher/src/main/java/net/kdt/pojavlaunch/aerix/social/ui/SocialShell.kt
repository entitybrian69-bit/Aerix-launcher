/*
 * Aerix Launcher Social layer.
 *
 * The second launcher. It replaces the Play dashboard for as long as it is open and now uses
 * the launcher's own glass theme. The rail holds the four Social entries asked for: add a
 * friend, activity, customise, profile. Chat lives on the dashboard, so there is no chat
 * entry and no exit entry: the top-left arrow is the way out.
 *
 * The top bar is deliberately compact: a 20sp title, and the only two controls (the exit arrow on
 * the dashboard, the settings gear everywhere) gathered in the top-right corner at 36dp, so the
 * content of every screen starts right under it.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.SocialGraph
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialActivityScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialAddFriendScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialAuthScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialCapeScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialCharacterScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialChatScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialDashboardScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialEditProfileScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialProfileScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialSafetyScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialSettingsAccountScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialSettingsChatsScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialSettingsHubScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialSettingsMicScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialSettingsNotificationsScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialSettingsPrivacyScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialSkinScreen
import net.kdt.pojavlaunch.aerix.social.ui.screens.SocialThemeScreen
import net.kdt.pojavlaunch.aerix.social.vm.SocialActivityViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialAddFriendViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialChatViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialDashboardViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SignInOutcome
import net.kdt.pojavlaunch.aerix.social.vm.SocialProfileViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialRoute
import net.kdt.pojavlaunch.aerix.social.vm.SocialSettingsViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialShellViewModel
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private val socialFactory = viewModelFactory {
    initializer { SocialShellViewModel() }
    initializer { SocialProfileViewModel(SocialGraph.store, repository = SocialGraph.repository) }
    initializer { SocialDashboardViewModel(SocialGraph.repository) }
    initializer { SocialChatViewModel(SocialGraph.repository) }
    initializer { SocialSettingsViewModel(SocialGraph.store, repository = SocialGraph.repository) }
    initializer { SocialAddFriendViewModel(SocialGraph.repository) }
    initializer { SocialActivityViewModel(SocialGraph.repository) }
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

    val profileVm: SocialProfileViewModel = viewModel(factory = socialFactory)
    val dashboardVm: SocialDashboardViewModel = viewModel(factory = socialFactory)
    val chatVm: SocialChatViewModel = viewModel(factory = socialFactory)
    val settingsVm: SocialSettingsViewModel = viewModel(factory = socialFactory)
    val addFriendVm: SocialAddFriendViewModel = viewModel(factory = socialFactory)
    val activityVm: SocialActivityViewModel = viewModel(factory = socialFactory)

    val context = LocalContext.current
    val profile by profileVm.state.collectAsState()
    val dashboard by dashboardVm.state.collectAsState()

    // Dashboard -> shell: the shell replaces the whole launcher instantly, so it fades in over
    // 240ms from 0.98 scale instead of snapping into place.
    var shellShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shellShown = true }

    // Android 13+ needs an explicit yes before Social can post message alerts. Asking once here
    // keeps the permission prompt out of a conversation.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val shellProgress by animateFloatAsState(
        targetValue = if (shellShown) 1f else 0f,
        animationSpec = tween(SocialMotion.SHELL_MS),
        label = "socialShell"
    )
    val chatSlide = with(LocalDensity.current) { 12.dp.roundToPx() }

    BackHandler(enabled = true) {
        if (!shell.back()) onExitToLauncher()
    }

    // Navigation happens from state, not from a callback: whatever path the Google flow takes,
    // the moment a profile is signed in the shell leaves the auth screen. A returning account
    // goes to the dashboard; a new or switched one goes through the character step.
    LaunchedEffect(profile.signedIn, profile.signInOutcome, route) {
        val outcome = profile.signInOutcome ?: return@LaunchedEffect
        if (route != SocialRoute.AUTH) return@LaunchedEffect
        shell.reset(
            if (outcome == SignInOutcome.RETURNING && !profile.forceCharacterStep) SocialRoute.DASHBOARD
            else SocialRoute.CHARACTER
        )
        profileVm.consumeSignInOutcome()
    }

    SocialBackground(
        modifier = modifier.graphicsLayer {
            alpha = shellProgress
            val scale = 0.98f + 0.02f * shellProgress
            scaleX = scale
            scaleY = scale
        }
    ) {
        // Sign-in and character setup are full-bleed: no rail, no header, so the portal scene
        // owns the whole screen. "Not now" is the way back out.
        val onboarding = route == SocialRoute.AUTH || route == SocialRoute.CHARACTER
        Row(modifier = Modifier.fillMaxSize()) {
            if (!onboarding) {
                SocialRail(
                    current = route,
                    onHome = { shell.reset(SocialRoute.DASHBOARD) },
                    onAddFriend = { shell.reset(SocialRoute.ADD_FRIEND) },
                    onActivity = { shell.reset(SocialRoute.ACTIVITY) },
                    onCustomise = { shell.reset(SocialRoute.THEME) },
                    onProfile = { shell.reset(SocialRoute.PROFILE) }
                )
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (!onboarding) {
                    SocialHeader(
                        route = route,
                        onBack = { if (!shell.back()) onExitToLauncher() },
                        onSettings = { shell.push(SocialRoute.SETTINGS) }
                    )
                }
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        if (targetState == SocialRoute.CHAT || initialState == SocialRoute.CHAT) {
                            (fadeIn(tween(SocialMotion.CHAT_MS)) + slideInHorizontally(tween(SocialMotion.CHAT_MS)) { chatSlide })
                                .togetherWith(fadeOut(tween(SocialMotion.CHAT_MS)))
                        } else {
                            (fadeIn(tween(SocialMotion.SCREEN_MS)) + slideInHorizontally(tween(SocialMotion.SCREEN_MS)) { it / 12 })
                                .togetherWith(fadeOut(tween(SocialMotion.SCREEN_MS)) + slideOutHorizontally(tween(SocialMotion.SCREEN_MS)) { -it / 12 })
                        }
                    },
                    label = "socialRoute"
                ) { target ->
                    when (target) {
                        SocialRoute.AUTH -> SocialAuthScreen(
                            inProgress = profile.signInInProgress,
                            googleError = profile.googleError,
                            stage = profile.googleStage,
                            lastEmail = profile.email,
                            onStartGoogle = { creating ->
                                // Holds the ViewModel, so the result survives the trip to the
                                // Google account sheet and back.
                                profileVm.signInWithGoogle(context, profile.googleClientId, creating)
                            },
                            onNotNow = onExitToLauncher
                        )
                        SocialRoute.CHARACTER -> SocialCharacterScreen(
                            profile = profileVm,
                            name = profile.name,
                            username = profile.username,
                            skinId = profile.skinId,
                            capeId = profile.capeId,
                            nameError = profile.nameError,
                            onOpenSkin = { shell.push(SocialRoute.SKIN) },
                            onOpenCape = { shell.push(SocialRoute.CAPE) },
                            onOpenTheme = { shell.push(SocialRoute.THEME) },
                            onFinish = { shell.reset(SocialRoute.DASHBOARD) }
                        )
                        SocialRoute.DASHBOARD -> SocialDashboardScreen(
                            viewModel = dashboardVm,
                            onMessage = { friend ->
                                chatVm.open(friend)
                                shell.push(SocialRoute.CHAT)
                            },
                            onAddFriend = { shell.push(SocialRoute.ADD_FRIEND) },
                            onActivity = { shell.push(SocialRoute.ACTIVITY) },
                            onOpenParty = { shell.push(SocialRoute.PARTY) }
                        )
                        SocialRoute.ADD_FRIEND -> SocialAddFriendScreen(
                            viewModel = addFriendVm,
                            onBack = { shell.back() }
                        )
                        SocialRoute.ACTIVITY -> SocialActivityScreen(
                            viewModel = activityVm,
                            friends = dashboard.friends,
                            onBack = { shell.back() }
                        )
                        SocialRoute.THEME -> SocialThemeScreen(
                            profile = profileVm,
                            selected = profile.themeWallpaperId,
                            onBack = { shell.back() }
                        )
                        SocialRoute.CHAT -> SocialChatScreen(
                            viewModel = chatVm,
                            onBack = { shell.back() },
                            onVoice = { shell.push(SocialRoute.PARTY) }
                        )
                        SocialRoute.PARTY -> SocialDashboardScreen(
                            viewModel = dashboardVm,
                            onMessage = { friend ->
                                chatVm.open(friend)
                                shell.push(SocialRoute.CHAT)
                            },
                            onAddFriend = { shell.push(SocialRoute.ADD_FRIEND) },
                            onActivity = { shell.push(SocialRoute.ACTIVITY) },
                            onOpenParty = { shell.back() },
                            forcePartyTab = true
                        )
                        SocialRoute.PROFILE -> SocialProfileScreen(
                            profile = profile,
                            onBack = { shell.back() },
                            onEdit = { shell.push(SocialRoute.EDIT_PROFILE) },
                            onOpenSettings = { shell.push(SocialRoute.SETTINGS) },
                            onOpenSafety = { shell.push(SocialRoute.SAFETY) },
                            onSignOut = {
                                profileVm.signOut()
                                shell.reset(SocialRoute.AUTH)
                            }
                        )
                        SocialRoute.EDIT_PROFILE -> SocialEditProfileScreen(
                            profile = profileVm,
                            state = profile,
                            onBack = { shell.back() }
                        )
                        SocialRoute.SETTINGS -> SocialSettingsHubScreen(
                            profile = profile,
                            onBack = { shell.back() },
                            onOpenPrivacy = { shell.push(SocialRoute.SETTINGS_PRIVACY) },
                            onOpenMic = { shell.push(SocialRoute.SETTINGS_MIC) },
                            onOpenChats = { shell.push(SocialRoute.SETTINGS_CHATS) },
                            onOpenAccount = { shell.push(SocialRoute.SETTINGS_ACCOUNT) },
                            onOpenNotifications = { shell.push(SocialRoute.SETTINGS_NOTIFICATIONS) }
                        )
                        SocialRoute.SETTINGS_PRIVACY -> SocialSettingsPrivacyScreen(
                            viewModel = settingsVm,
                            onBack = { shell.back() }
                        )
                        SocialRoute.SETTINGS_MIC -> SocialSettingsMicScreen(
                            viewModel = settingsVm,
                            onBack = { shell.back() }
                        )
                        SocialRoute.SETTINGS_CHATS -> SocialSettingsChatsScreen(
                            viewModel = settingsVm,
                            onBack = { shell.back() }
                        )
                        SocialRoute.SETTINGS_ACCOUNT -> SocialSettingsAccountScreen(
                            profile = profileVm,
                            state = profile,
                            onBack = { shell.back() },
                            onOpenSafety = { shell.push(SocialRoute.SAFETY) },
                            onDeleted = { shell.reset(SocialRoute.AUTH) },
                            onSignedOut = { shell.reset(SocialRoute.AUTH) }
                        )
                        SocialRoute.SETTINGS_NOTIFICATIONS -> SocialSettingsNotificationsScreen(
                            viewModel = settingsVm,
                            onBack = { shell.back() }
                        )
                        SocialRoute.SAFETY -> SocialSafetyScreen(
                            viewModel = settingsVm,
                            onBack = { shell.back() },
                            onDeleteAccount = {
                                profileVm.deleteAccount()
                                shell.reset(SocialRoute.AUTH)
                            }
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
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialHeader(route: SocialRoute, onBack: () -> Unit, onSettings: () -> Unit) {
    val title = when (route) {
        SocialRoute.AUTH -> "Aerix account"
        SocialRoute.CHARACTER -> "Create your character"
        SocialRoute.DASHBOARD -> "Social"
        SocialRoute.ADD_FRIEND -> "Add a friend"
        SocialRoute.ACTIVITY -> "Activity"
        SocialRoute.THEME -> "Customise"
        SocialRoute.CHAT -> "Chat"
        SocialRoute.PARTY -> "Party"
        SocialRoute.PROFILE -> "My profile"
        SocialRoute.EDIT_PROFILE -> "Edit profile"
        SocialRoute.SETTINGS -> "Settings"
        SocialRoute.SETTINGS_PRIVACY -> "Privacy"
        SocialRoute.SETTINGS_MIC -> "Microphone"
        SocialRoute.SETTINGS_CHATS -> "Chats"
        SocialRoute.SETTINGS_ACCOUNT -> "Account settings"
        SocialRoute.SETTINGS_NOTIFICATIONS -> "Notifications"
        SocialRoute.SAFETY -> "Safety"
        SocialRoute.SKIN -> "Choose skin"
        SocialRoute.CAPE -> "Choose cape"
    }
    // Compact bar: no big leading arrow, no tall padding. The content starts right under it,
    // and the only controls are small and gathered in the top-right corner next to Settings.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SocialDimens.pagePaddingH, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            color = SocialColors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        // Only the dashboard keeps the exit arrow, and it sits next to the gear, at 36dp.
        if (route == SocialRoute.DASHBOARD) {
            SocialIconButton(
                iconRes = R.drawable.aerix_ic_arrow_back,
                contentDescription = "Back to launcher",
                onClick = onBack,
                size = 36.dp,
                iconSize = 17.dp
            )
        }
        SocialIconButton(
            iconRes = R.drawable.aerix_ic_settings_filled,
            contentDescription = "Settings",
            onClick = onSettings,
            selected = route == SocialRoute.SETTINGS,
            size = 36.dp,
            iconSize = 18.dp
        )
    }
}

/** Add a friend, Activity, Customise, Profile. No chat entry and no exit entry. */
@Composable
private fun SocialRail(
    current: SocialRoute,
    onHome: () -> Unit,
    onAddFriend: () -> Unit,
    onActivity: () -> Unit,
    onCustomise: () -> Unit,
    onProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(SocialDimens.railWidth)
            .fillMaxHeight()
            .background(SocialColors.panel.copy(alpha = 0.72f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RailItem(R.drawable.aerix_ic_home_filled, "Social", current == SocialRoute.DASHBOARD, onHome)
        RailItem(R.drawable.aerix_social_ic_add_person, "Add friend", current == SocialRoute.ADD_FRIEND, onAddFriend)
        RailItem(R.drawable.aerix_social_ic_activity, "Activity", current == SocialRoute.ACTIVITY, onActivity)
        RailItem(R.drawable.aerix_social_ic_theme, "Customise", current == SocialRoute.THEME, onCustomise)
        Spacer(Modifier.weight(1f))
        RailItem(R.drawable.aerix_social_ic_profile, "Profile", current == SocialRoute.PROFILE, onProfile)
    }
}

@Composable
private fun RailItem(iconRes: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = SocialColors.accent
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressScale(interaction)
    // Selection springs: the tile grows a touch, the icon lifts, the colours cross-fade.
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "railSelection"
    )
    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.85f) else SocialColors.panelSoft.copy(alpha = 0.55f),
        animationSpec = tween(220),
        label = "railBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) SocialColors.onAccent else SocialColors.textMuted,
        animationSpec = tween(220),
        label = "railContent"
    )
    Box(
        modifier = Modifier
            .width(68.dp)
            .height(60.dp)
            .graphicsLayer {
                scaleX = press * (1f + 0.04f * selection)
                scaleY = press * (1f + 0.04f * selection)
            }
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { translationY = -3f * selection }
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

/** Small avatar chip used by the top bar of sub-screens. */
@Composable
fun SocialAvatarChip(avatar: Int, name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(SocialColors.panelSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SocialAvatar(avatar = avatar, size = 28.dp)
        Text(text = name, color = SocialColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SocialDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SocialColors.border)
    )
}

@Composable
fun SocialSectionCard(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SocialDimens.cardRadius))
            .background(SocialColors.panelSoft)
            .border(BorderStroke(1.dp, SocialColors.border), RoundedCornerShape(SocialDimens.cardRadius))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SocialColors.accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(iconRes), contentDescription = null, tint = SocialColors.accentBright, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            SocialBody(title, color = SocialColors.textPrimary, size = 15.sp, weight = FontWeight.SemiBold)
            SocialBody(subtitle, size = 12.sp, color = SocialColors.textMuted)
        }
        Icon(
            painter = painterResource(R.drawable.aerix_social_ic_chevron),
            contentDescription = null,
            tint = SocialColors.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Convenience spacer used by several screens. */
@Composable
fun SocialGap(height: Int = 12) {
    Spacer(Modifier.height(height.dp))
}
