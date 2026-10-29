/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Socials hub: Friends (pins, nicknames, recent, blocks), Realtime Messages & Party Channels,
 * Parties, LFG & Server Hub Discovery, Activity (mutual friends & party invites), and Settings.
 */

package net.kdt.pojavlaunch.aerix.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.social.SocialFriend
import net.kdt.pojavlaunch.aerix.social.SocialParty
import net.kdt.pojavlaunch.aerix.social.SocialProfile
import net.kdt.pojavlaunch.aerix.social.SocialRealtime
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.theme.ThemeMotion
import net.kdt.pojavlaunch.aerix.theme.accentGlow
import net.kdt.pojavlaunch.aerix.theme.accentRing
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import java.net.HttpURLConnection
import java.net.URL

private enum class SocialTab(val label: String) {
    FRIENDS("Friends"),
    MESSAGES("Messages"),
    PARTIES("Parties"),
    DISCOVER("Discover"),
    ACTIVITY("Activity"),
    SETTINGS("Settings")
}

private sealed interface SocialDestination {
    val depth: Int
    data class Page(val tab: SocialTab) : SocialDestination {
        override val depth: Int get() = tab.ordinal
    }
    data class DmChat(val friendId: String) : SocialDestination {
        override val depth: Int get() = 10
    }
    data class PartyChat(val partyId: String) : SocialDestination {
        override val depth: Int get() = 11
    }
    data class Profile(val username: String?) : SocialDestination {
        override val depth: Int get() = 12
    }
}

@Composable
fun SocialsScreen(activity: FragmentActivity, onExit: () -> Unit = {}) {
    val account = PojavAccountBridge.current
    var tab by remember { mutableStateOf(SocialTab.FRIENDS) }
    var activeDmFriendId by remember { mutableStateOf<String?>(null) }
    var activePartyChatId by remember { mutableStateOf<String?>(null) }
    var profileOpen by remember { mutableStateOf(false) }
    var profileUser by remember { mutableStateOf<String?>(null) }
    val signedIn = SocialBackend.signedIn
    val motion = ThemeMotion.rememberEnabled()

    LaunchedEffect(Unit) {
        SocialBackend.attach(activity)
        SocialBackend.consumeInvite()?.let { code ->
            SocialBackend.findByCode(code)?.let { person ->
                profileUser = person.username
                profileOpen = true
            }
        }
        if (SocialBackend.signedIn) {
            SocialBackend.rememberMinecraftName(account?.username)
            SocialBackend.loadProfile()
            SocialBackend.loadMyProfile()
            SocialBackend.refreshFriends()
            SocialBackend.refreshParties()
            SocialBackend.refreshPartyInvites()
            SocialBackend.unreadCount()
        }
    }

    LaunchedEffect(signedIn) {
        if (signedIn) {
            SocialBackend.rememberMinecraftName(account?.username)
            SocialBackend.loadProfile()
            SocialBackend.loadMyProfile()
            SocialBackend.refreshFriends()
            SocialBackend.refreshParties()
            SocialBackend.refreshPartyInvites()
            SocialBackend.unreadCount()
        }
        while (signedIn) {
            SocialBackend.heartbeat()
            delay(60_000)
            SocialBackend.refreshFriends()
            SocialBackend.refreshParties()
            SocialBackend.refreshPartyInvites()
            SocialBackend.unreadCount()
        }
    }

    val destination: SocialDestination = when {
        activeDmFriendId != null -> SocialDestination.DmChat(activeDmFriendId!!)
        activePartyChatId != null -> SocialDestination.PartyChat(activePartyChatId!!)
        profileOpen -> SocialDestination.Profile(profileUser)
        else -> SocialDestination.Page(tab)
    }

    BackHandler(enabled = true) {
        when {
            signedIn && SocialBackend.showOnboarding -> SocialBackend.deferOnboardingForSession()
            activeDmFriendId != null -> activeDmFriendId = null
            activePartyChatId != null -> activePartyChatId = null
            profileOpen -> profileOpen = false
            tab != SocialTab.FRIENDS -> tab = SocialTab.FRIENDS
            else -> onExit()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 2.dp,
                end = AerixSpacing.sm,
                top = AerixSpacing.sm,
                bottom = AerixSpacing.sm
            )
    ) {
        SocialSidebar(
            tab = tab,
            signedIn = signedIn,
            inSubScreen = activeDmFriendId != null || activePartyChatId != null || profileOpen || SocialBackend.showOnboarding,
            onTab = { picked ->
                SocialBackend.deferOnboardingForSession()
                activeDmFriendId = null
                activePartyChatId = null
                profileOpen = false
                tab = picked
            },
            onExit = onExit,
            onProfile = {
                SocialBackend.deferOnboardingForSession()
                activeDmFriendId = null
                activePartyChatId = null
                profileUser = null
                profileOpen = true
            }
        )
        Spacer(Modifier.width(AerixSpacing.sm))
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .liquidGlass(
                    shape = RoundedCornerShape(AerixRadii.panel),
                    tint = AerixSurface.panelGlassTint,
                    strength = 1f,
                    elevation = AerixMetrics.glassFloatingElevation
                ),
            shape = RoundedCornerShape(AerixRadii.panel),
            color = AerixSurface.panel,
            contentColor = AerixSurface.textPrimary
        ) {
            if (!signedIn) {
                SocialSignIn(activity)
            } else if (SocialBackend.showOnboarding) {
                SocialProfileForm(
                    activity = activity,
                    profile = SocialBackend.myProfile,
                    editing = false,
                    onClose = { SocialBackend.deferOnboardingForSession() },
                    onSaved = { SocialBackend.deferOnboardingForSession() }
                )
            } else {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        val forward = targetState.depth >= initialState.depth
                        val enterSpec = spring<IntOffset>(dampingRatio = 0.88f, stiffness = 340f)
                        val enter = slideInHorizontally(enterSpec) { full ->
                            if (forward) (full * 0.12f).toInt() else -(full * 0.12f).toInt()
                        } + fadeIn(tween(if (motion) 220 else 0))
                        val exit = slideOutHorizontally(tween(if (motion) 190 else 0)) { full ->
                            if (forward) -(full * 0.08f).toInt() else (full * 0.08f).toInt()
                        } + fadeOut(tween(if (motion) 150 else 0))
                        enter togetherWith exit
                    },
                    label = "socialDestination"
                ) { dest ->
                    when (dest) {
                        is SocialDestination.DmChat -> {
                            val friend = SocialBackend.friends.firstOrNull { it.id == dest.friendId }
                            if (friend != null) {
                                Box(Modifier.fillMaxSize().padding(AerixSpacing.md)) {
                                    SocialConversationPanel(
                                        friend = friend,
                                        party = null,
                                        onBack = { activeDmFriendId = null },
                                        onOpenFriendProfile = { f ->
                                            val uname = f.username ?: f.mcUsername
                                            activeDmFriendId = null
                                            profileUser = uname
                                            profileOpen = true
                                        },
                                        onJoinSession = {
                                            val friendParty = SocialBackend.parties.firstOrNull { it.hostId == friend.id }
                                            PojavLaunchBridge.joinSocialSession(
                                                activity = activity,
                                                mcVersion = friendParty?.mcVersion ?: friend.mcVersion,
                                                loaderName = friendParty?.loader,
                                                instanceHint = friendParty?.instanceName,
                                                modpackSource = friendParty?.modpackSource,
                                                modpackId = friendParty?.modpackId,
                                                modpackIconUrl = friendParty?.modpackIconUrl,
                                                serverAddress = friendParty?.serverAddress,
                                                onNavigate = { AerixNav.navigate(it) }
                                            )
                                        }
                                    )
                                }
                            } else {
                                LaunchedEffect(Unit) { activeDmFriendId = null }
                            }
                        }
                        is SocialDestination.PartyChat -> {
                            val party = SocialBackend.parties.firstOrNull { it.id == dest.partyId }
                            if (party != null) {
                                var manageParty by remember { mutableStateOf(false) }
                                Box(Modifier.fillMaxSize().padding(AerixSpacing.md)) {
                                    SocialConversationPanel(
                                        friend = null,
                                        party = party,
                                        onBack = { activePartyChatId = null },
                                        onOpenPartyDetails = { manageParty = true },
                                        onJoinSession = {
                                            PojavLaunchBridge.joinSocialSession(
                                                activity = activity,
                                                mcVersion = party.mcVersion,
                                                loaderName = party.loader,
                                                instanceHint = party.instanceName,
                                                modpackSource = party.modpackSource,
                                                modpackId = party.modpackId,
                                                modpackIconUrl = party.modpackIconUrl,
                                                serverAddress = party.serverAddress,
                                                onNavigate = { AerixNav.navigate(it) }
                                            )
                                        }
                                    )
                                }
                                if (manageParty) {
                                    PartyDetailDialog(
                                        activity = activity,
                                        party = party,
                                        friends = SocialBackend.friends.filter { !it.pendingIncoming && !it.pendingOutgoing },
                                        onDismiss = { manageParty = false },
                                        onOpenChat = { manageParty = false },
                                        onNavigateLauncher = { AerixNav.navigate(it) }
                                    )
                                }
                            } else {
                                LaunchedEffect(Unit) { activePartyChatId = null }
                            }
                        }
                        is SocialDestination.Profile -> SocialProfilePage(
                            activity = activity,
                            username = dest.username
                        ) { profileOpen = false }
                        is SocialDestination.Page -> when (dest.tab) {
                            SocialTab.FRIENDS -> SocialFriendsPage(
                                activity = activity,
                                onOpenChat = { f -> activeDmFriendId = f.id },
                                onOpenProfile = { uname ->
                                    profileUser = uname
                                    profileOpen = true
                                }
                            )
                            SocialTab.MESSAGES -> SocialMessagesHubPage(
                                activity = activity,
                                onOpenDm = { fid -> activeDmFriendId = fid },
                                onOpenPartyChat = { pid -> activePartyChatId = pid }
                            )
                            SocialTab.PARTIES -> Box(Modifier.fillMaxSize().padding(AerixSpacing.lg)) {
                                SocialPartiesSection(
                                    activity = activity,
                                    onOpenPartyChat = { p -> activePartyChatId = p.id },
                                    onNavigateLauncher = { AerixNav.navigate(it) }
                                )
                            }
                            SocialTab.DISCOVER -> SocialDiscoverHubPage(
                                activity = activity,
                                onOpenPartyChat = { p -> activePartyChatId = p.id }
                            )
                            SocialTab.ACTIVITY -> SocialActivityPage(
                                activity = activity,
                                onOpenPartyChat = { pid -> activePartyChatId = pid }
                            )
                            SocialTab.SETTINGS -> SocialSettingsPage(
                                activity = activity,
                                launcherName = account?.username,
                                onSignOut = { SocialBackend.signOut() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialSidebar(
    tab: SocialTab,
    signedIn: Boolean,
    inSubScreen: Boolean,
    onTab: (SocialTab) -> Unit,
    onExit: () -> Unit,
    onProfile: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    val activityBadge = SocialBackend.friends.count { it.pendingIncoming } + SocialBackend.partyInvites.size

    Column(
        modifier = Modifier
            .width(AerixMetrics.navigationRailWidth)
            .fillMaxHeight()
            .liquidGlass(
                shape = CircleShape,
                tint = AerixSurface.glassTint,
                strength = 0.94f,
                elevation = AerixMetrics.glassRailElevation
            )
            .padding(vertical = AerixSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
    ) {
        SocialRailItem(
            iconRes = R.drawable.aerix_ic_arrow_back,
            label = "Back to launcher",
            selected = false,
            accent = accent,
            compact = true,
            onClick = onExit
        )
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(1.dp)
                .background(AerixSurface.borderSoft)
        )

        SocialRailItem(
            iconRes = R.drawable.aerix_ic_people,
            label = SocialTab.FRIENDS.label,
            selected = signedIn && !inSubScreen && tab == SocialTab.FRIENDS,
            accent = accent,
            compact = false
        ) { onTab(SocialTab.FRIENDS) }

        SocialRailItem(
            iconRes = R.drawable.aerix_ic_chat,
            label = SocialTab.MESSAGES.label,
            selected = signedIn && !inSubScreen && tab == SocialTab.MESSAGES,
            accent = accent,
            compact = false,
            badge = SocialBackend.unread
        ) { onTab(SocialTab.MESSAGES) }

        SocialRailItem(
            iconRes = R.drawable.aerix_ic_group_outlined,
            label = SocialTab.PARTIES.label,
            selected = signedIn && !inSubScreen && tab == SocialTab.PARTIES,
            accent = accent,
            compact = false,
            badge = SocialBackend.parties.size
        ) { onTab(SocialTab.PARTIES) }

        SocialRailItem(
            iconRes = R.drawable.aerix_ic_public,
            label = SocialTab.DISCOVER.label,
            selected = signedIn && !inSubScreen && tab == SocialTab.DISCOVER,
            accent = accent,
            compact = false
        ) { onTab(SocialTab.DISCOVER) }

        SocialRailItem(
            iconRes = R.drawable.aerix_ic_bell,
            label = SocialTab.ACTIVITY.label,
            selected = signedIn && !inSubScreen && tab == SocialTab.ACTIVITY,
            accent = accent,
            compact = false,
            badge = activityBadge
        ) { onTab(SocialTab.ACTIVITY) }

        SocialRailItem(
            iconRes = R.drawable.aerix_ic_settings_filled,
            label = SocialTab.SETTINGS.label,
            selected = signedIn && !inSubScreen && tab == SocialTab.SETTINGS,
            accent = accent,
            compact = false
        ) { onTab(SocialTab.SETTINGS) }

        Spacer(Modifier.weight(1f))

        if (signedIn) {
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.clickable(role = Role.Button, onClick = onProfile)
            ) {
                SocialHead(SocialBackend.mcUsername ?: PojavAccountBridge.current?.username, 32)
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (SocialRealtime.isConnected) AerixSurface.success else AerixSurface.warning)
                        .border(1.5.dp, AerixSurface.canvas, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun SocialRailItem(
    iconRes: Int,
    label: String,
    selected: Boolean,
    accent: Color,
    compact: Boolean,
    badge: Int = 0,
    onClick: () -> Unit
) {
    val motion = ThemeMotion.rememberEnabled()
    val liveAccent = ThemeMotion.rememberPulse(accent, selected, motion)
    val shape = RoundedCornerShape(AerixRadii.control)
    val container = if (selected) liveAccent else AerixSurface.panelTrack
    val tint = if (selected) AerixSurface.canvas else AerixSurface.textSecondary
    Box(
        modifier = Modifier
            .size(width = if (compact) 34.dp else 36.dp, height = if (compact) 30.dp else 34.dp)
            .clip(shape)
            .background(container)
            .accentGlow(liveAccent, selected, motion)
            .accentRing(liveAccent, shape, selected, motion, AerixSpacing.hairline)
            .border(BorderStroke(AerixSpacing.hairline, if (selected) liveAccent else AerixSurface.borderSoft), shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(if (compact) 15.dp else 17.dp)
        )
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 1.dp)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(AerixSurface.danger),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (badge > 9) "9+" else "$badge",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = AerixSurface.canvas
                )
            }
        }
    }
}

// ---- sign in ------------------------------------------------------------------------------

@Composable
private fun SocialSignIn(activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val accent = AerixThemeManager.currentAccent()

    fun submit(create: Boolean) {
        if (address.isBlank() || password.length < 6) {
            Toast.makeText(activity, "Enter an email and a password of 6+ characters", Toast.LENGTH_SHORT).show()
            return
        }
        working = true
        scope.launch {
            if (create) SocialBackend.signUp(address, password) else SocialBackend.signIn(address, password)
            working = false
            if (SocialBackend.signedIn) {
                SocialBackend.rememberMinecraftName(PojavAccountBridge.current?.username)
                SocialBackend.loadProfile()
                SocialBackend.refreshFriends()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(AerixRadii.panel),
            color = AerixSurface.panelRaised,
            border = BorderStroke(AerixSpacing.hairline, AerixSurface.border),
            modifier = Modifier.liquidGlass(
                shape = RoundedCornerShape(AerixRadii.panel),
                tint = AerixSurface.panelGlassTint,
                strength = 1f,
                elevation = AerixMetrics.glassFloatingElevation
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(178.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(AerixRadii.control))
                            .background(accent.copy(alpha = 0.18f))
                            .border(BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.55f)), RoundedCornerShape(AerixRadii.control))
                            .liquidGlass(RoundedCornerShape(AerixRadii.control), tint = accent, strength = 0.7f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_aerix_mark),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Aerix Social",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AerixSurface.textPrimary
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Parties, Realtime chat, LFG and friends — in launcher and in game",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = AerixSurface.textSecondary
                    )
                }

                Column(modifier = Modifier.width(316.dp)) {
                    SocialField(address, { address = it }, "Email")
                    Spacer(Modifier.height(7.dp))
                    SocialField(password, { password = it }, "Password", secret = true)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            SocialPillButton(
                                label = if (working) "Please wait…" else "Sign in",
                                filled = true,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { if (!working) submit(false) }
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            SocialPillButton(
                                label = "Continue with Google",
                                filled = false,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val opened = SocialBackend.beginGoogleSignIn(activity)
                                    if (!opened) {
                                        Toast.makeText(
                                            activity,
                                            "Google sign-in needs the Google provider switched on in Supabase",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Create an account",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AerixSurface.accent,
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clickable { if (!working) submit(true) }
                        )
                        Text(
                            "Forgot password?",
                            fontSize = 12.sp,
                            color = AerixSurface.textSecondary,
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clickable {
                                    if (address.isBlank()) {
                                        Toast.makeText(activity, "Type your email first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        scope.launch {
                                            val sent = SocialBackend.sendPasswordReset(address)
                                            Toast.makeText(
                                                activity,
                                                if (sent) "Reset link sent to ${address.trim()}" else "Could not send the link",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                        )
                    }
                    SocialBackend.lastError?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(text = message, color = AerixSurface.danger, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (SocialBackend.needsConfirmation()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Resend confirmation email",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AerixSurface.accent,
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clickable {
                                    scope.launch {
                                        val sent = SocialBackend.resendConfirmation(address)
                                        Toast.makeText(
                                            activity,
                                            if (sent) "Sent to ${address.trim()}" else "Could not send the email",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

// ---- Friends QoL (Pinned, Nicknames, Recent Players, Blocked, Same-Version Nudge) --------

private enum class FriendsSubTab(val label: String) {
    ALL("All Friends"),
    RECENT("Recent Players"),
    BLOCKED("Blocked")
}

@Composable
private fun SocialFriendsPage(
    activity: FragmentActivity,
    onOpenChat: (SocialFriend) -> Unit,
    onOpenProfile: (String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()
    var subTab by remember { mutableStateOf(FriendsSubTab.ALL) }
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<SocialProfile?>(null) }
    var searched by remember { mutableStateOf(false) }
    var nicknameTarget by remember { mutableStateOf<SocialFriend?>(null) }
    var nicknameDraft by remember { mutableStateOf("") }
    var blockConfirmTarget by remember { mutableStateOf<SocialFriend?>(null) }
    var invitePartyTarget by remember { mutableStateOf<SocialFriend?>(null) }

    val allFriends = SocialBackend.friends
    val accepted = allFriends.filter { !it.pendingIncoming && !it.pendingOutgoing }
    val selectedInstMc = SocialBackend.extractMcVersion(PojavInstanceBridge.selected?.versionId)
    val sameVersionFriends = remember(accepted, selectedInstMc) {
        if (selectedInstMc == null) emptyList()
        else accepted.filter { it.online && it.mcVersion.equals(selectedInstMc, ignoreCase = true) }
    }

    LaunchedEffect(subTab) {
        if (subTab == FriendsSubTab.BLOCKED) SocialBackend.loadBlockedUsers()
    }

    Column(modifier = Modifier.fillMaxSize().padding(AerixSpacing.lg), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Friends", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = AerixSurface.textPrimary)
                val onlineCount = accepted.count { it.online }
                if (onlineCount > 0) SocialTag("$onlineCount online", AerixSurface.success)
                if (SocialRealtime.isConnected) SocialTag("Realtime", accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FriendsSubTab.values().forEach { t ->
                    val label = when (t) {
                        FriendsSubTab.ALL -> "${t.label} (${accepted.size})"
                        FriendsSubTab.RECENT -> "${t.label} (${SocialBackend.recentPlayers.size})"
                        FriendsSubTab.BLOCKED -> "${t.label} (${SocialBackend.blockedUsers.size})"
                    }
                    FilterChipPill(label, subTab == t) { subTab = t }
                }
            }
        }

        // Same-version nudge banner when online friends are playing the exact same MC version!
        if (subTab == FriendsSubTab.ALL && sameVersionFriends.isNotEmpty() && selectedInstMc != null) {
            val names = sameVersionFriends.take(2).joinToString(", ") { it.effectiveName }
            Surface(
                shape = RoundedCornerShape(AerixRadii.control),
                color = accent.copy(alpha = 0.14f),
                border = BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_sports_esports_outlined),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "$names ${if (sameVersionFriends.size == 1) "is" else "are"} also on $selectedInstMc!",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AerixSurface.textPrimary
                        )
                        Text("Start a party or hop into a session together.", fontSize = 11.sp, color = AerixSurface.textSecondary)
                    }
                    SocialSmallButton("Join / Party", true) {
                        val first = sameVersionFriends.first()
                        val party = SocialBackend.parties.firstOrNull { it.hostId == first.id }
                        if (party != null) {
                            PojavLaunchBridge.joinSocialSession(
                                activity = activity,
                                mcVersion = party.mcVersion,
                                loaderName = party.loader,
                                instanceHint = party.instanceName,
                                modpackSource = party.modpackSource,
                                modpackId = party.modpackId,
                                modpackIconUrl = party.modpackIconUrl,
                                serverAddress = party.serverAddress,
                                onNavigate = { AerixNav.navigate(it) }
                            )
                        } else {
                            onOpenChat(first)
                        }
                    }
                }
            }
        }

        when (subTab) {
            FriendsSubTab.ALL -> {
                // Exact username or 8-char invite code lookup bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SocialField(
                            value = query,
                            onValueChange = {
                                query = it
                                searched = false
                            },
                            placeholder = "Find player by @username or 8-char invite code..."
                        )
                    }
                    SocialButton("Find", filled = true, enabled = query.trim().length >= 3) {
                        scope.launch {
                            SocialBackend.clearError()
                            val clean = query.trim()
                            found = SocialBackend.findPlayer(clean)
                                ?: if (clean.length in 6..12) SocialBackend.findByCode(clean) else null
                            searched = true
                        }
                    }
                }

                found?.let { match ->
                    SocialPanel(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SocialRemoteImage(
                                url = match.avatarUrl,
                                fallbackIcon = R.drawable.aerix_ic_person_outlined,
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(match.displayName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                                match.username?.let { Text("@$it", fontSize = 11.sp, color = AerixSurface.textMuted) }
                            }
                            SocialSmallButton("Add Friend", true) {
                                scope.launch {
                                    val ok = if (!match.username.isNullOrBlank()) {
                                        SocialBackend.addFriendByName(match.username)
                                    } else {
                                        SocialBackend.addFriendById(match.id)
                                    }
                                    Toast.makeText(
                                        activity,
                                        if (ok) "Friend request sent!" else (SocialBackend.lastError ?: "Could not send request"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (ok) {
                                        found = null
                                        query = ""
                                    }
                                }
                            }
                            SocialSmallButton("Profile", false) { onOpenProfile(match.username) }
                        }
                    }
                } ?: if (searched) {
                    Text(
                        SocialBackend.lastError ?: "No player matched \"${query.trim()}\"",
                        fontSize = 11.5.sp,
                        color = AerixSurface.textMuted
                    )
                } else Unit

                if (accepted.isEmpty()) {
                    SocialPanel(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Your friends list is ready", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                            Text("Look up a friend's @username or share your invite code from your Profile card.", fontSize = 12.sp, color = AerixSurface.textMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(accepted, key = { it.id }) { friend ->
                            FriendQoLRow(
                                friend = friend,
                                onChat = { onOpenChat(friend) },
                                onProfile = { onOpenProfile(friend.username ?: friend.mcUsername) },
                                onTogglePin = { SocialBackend.toggleFriendPin(friend.id) },
                                onEditNickname = {
                                    nicknameTarget = friend
                                    nicknameDraft = friend.nickname.orEmpty()
                                },
                                onInviteToParty = { invitePartyTarget = friend },
                                onJoin = {
                                    val hosted = SocialBackend.parties.firstOrNull { it.hostId == friend.id }
                                    PojavLaunchBridge.joinSocialSession(
                                        activity = activity,
                                        mcVersion = hosted?.mcVersion ?: friend.mcVersion,
                                        loaderName = hosted?.loader,
                                        instanceHint = hosted?.instanceName,
                                        modpackSource = hosted?.modpackSource,
                                        modpackId = hosted?.modpackId,
                                        modpackIconUrl = hosted?.modpackIconUrl,
                                        serverAddress = hosted?.serverAddress,
                                        onNavigate = { AerixNav.navigate(it) }
                                    )
                                },
                                onBlock = { blockConfirmTarget = friend },
                                onRemove = { scope.launch { SocialBackend.removeFriend(friend.id) } }
                            )
                        }
                    }
                }
            }

            FriendsSubTab.RECENT -> {
                val recent = SocialBackend.recentPlayers
                if (recent.isEmpty()) {
                    SocialPanel(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
                            Text("Players you party up with, message or join on servers appear here.", fontSize = 12.sp, color = AerixSurface.textMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recent, key = { it.userId }) { rp ->
                            val isAlreadyFriend = accepted.any { it.id == rp.userId }
                            SocialPanel(Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SocialRemoteImage(
                                        url = rp.avatarUrl,
                                        fallbackIcon = R.drawable.aerix_ic_person_outlined,
                                        modifier = Modifier.size(36.dp),
                                        shape = CircleShape
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rp.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                                        Text(
                                            "${rp.contextLabel} · ${SocialFriend.formatLastSeen(rp.seenAtMs)}",
                                            fontSize = 11.sp,
                                            color = AerixSurface.textMuted
                                        )
                                    }
                                    if (!isAlreadyFriend) {
                                        SocialSmallButton("Add Friend", true) {
                                            scope.launch {
                                                val ok = SocialBackend.addFriendById(rp.userId)
                                                Toast.makeText(activity, if (ok) "Friend request sent!" else "Could not send", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        SocialTag("Friends", AerixSurface.success)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FriendsSubTab.BLOCKED -> {
                val blocked = SocialBackend.blockedUsers
                if (blocked.isEmpty()) {
                    SocialPanel(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
                            Text("You haven't blocked anyone.", fontSize = 12.sp, color = AerixSurface.textMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(blocked, key = { it.userId }) { b ->
                            SocialPanel(Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SocialRemoteImage(
                                        url = b.avatarUrl,
                                        fallbackIcon = R.drawable.aerix_ic_person_outlined,
                                        modifier = Modifier.size(36.dp),
                                        shape = CircleShape
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(b.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                                        Text("Messages and requests blocked server-side", fontSize = 11.sp, color = AerixSurface.textMuted)
                                    }
                                    SocialSmallButton("Unblock", false) {
                                        scope.launch { SocialBackend.unblock(b.userId) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Nickname dialog
    nicknameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { nicknameTarget = null },
            title = { Text("Nickname for ${target.displayName}") },
            text = {
                SocialGlassInput(
                    value = nicknameDraft,
                    onValueChange = { nicknameDraft = it.take(32) },
                    label = "Custom nickname (leave empty to clear)"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    SocialBackend.setFriendNickname(target.id, nicknameDraft)
                    nicknameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { nicknameTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Block confirmation dialog
    blockConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { blockConfirmTarget = null },
            title = { Text("Block ${target.effectiveName}?") },
            text = {
                Text(
                    "They will be removed from your friends list and blocked server-side from messaging or inviting you.",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uid = target.id
                    blockConfirmTarget = null
                    scope.launch {
                        SocialBackend.block(uid)
                        Toast.makeText(activity, "Blocked ${target.effectiveName}", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Block", color = AerixSurface.danger) }
            },
            dismissButton = {
                TextButton(onClick = { blockConfirmTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Quick party invite picker for a friend
    invitePartyTarget?.let { target ->
        val myParties = SocialBackend.parties
        AlertDialog(
            onDismissRequest = { invitePartyTarget = null },
            title = { Text("Invite ${target.effectiveName} to Party") },
            text = {
                if (myParties.isEmpty()) {
                    Text("You don't have an active party yet. Open the Parties tab to create one first!", fontSize = 12.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        myParties.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AerixRadii.controlSmall))
                                    .background(AerixSurface.panelTrack)
                                    .clickable {
                                        invitePartyTarget = null
                                        scope.launch {
                                            val ok = SocialBackend.inviteFriendToParty(p, target.id)
                                            Toast.makeText(
                                                activity,
                                                if (ok) "Invited ${target.effectiveName} to ${p.title}" else (SocialBackend.lastError ?: "Failed"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    Text("${p.summaryTag()} · ${p.memberCount}/${p.maxMembers}", fontSize = 10.5.sp, color = AerixSurface.textMuted)
                                }
                                SocialTag("Send", accent)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { invitePartyTarget = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun FriendQoLRow(
    friend: SocialFriend,
    onChat: () -> Unit,
    onProfile: () -> Unit,
    onTogglePin: () -> Unit,
    onEditNickname: () -> Unit,
    onInviteToParty: () -> Unit,
    onJoin: () -> Unit,
    onBlock: () -> Unit,
    onRemove: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    var menuOpen by remember { mutableStateOf(false) }

    SocialPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChat() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                if (!friend.avatarUrl.isNullOrBlank()) {
                    SocialRemoteImage(
                        url = friend.avatarUrl,
                        fallbackIcon = R.drawable.aerix_ic_person_outlined,
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape
                    )
                } else {
                    SocialHead(friend.mcUsername, 38)
                }
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(if (friend.online) AerixSurface.success else AerixSurface.textMuted)
                        .border(1.5.dp, AerixSurface.canvas, CircleShape)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        friend.effectiveName,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AerixSurface.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!friend.nickname.isNullOrBlank()) {
                        Text("(${friend.displayName})", fontSize = 10.5.sp, color = AerixSurface.textMuted, maxLines = 1)
                    }
                    if (friend.pinned) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_favorite_filled),
                            contentDescription = "Favourite",
                            tint = accent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (friend.online && friend.joinable) {
                        SocialTag("Joinable", AerixSurface.success)
                    }
                }
                Text(
                    friend.presenceSummary(),
                    fontSize = 11.sp,
                    color = if (friend.online) AerixSurface.success else AerixSurface.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (friend.online && (friend.joinable || !friend.mcVersion.isNullOrBlank())) {
                SocialSmallButton("Join", true, onClick = onJoin)
            }
            SocialSmallButton("Chat", false, onClick = onChat)

            Box {
                SocialIconBubble(R.drawable.aerix_ic_more_vert, "More options") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (friend.pinned) "Unpin from top" else "Pin / Favourite", fontSize = 12.sp) },
                        onClick = { menuOpen = false; onTogglePin() }
                    )
                    DropdownMenuItem(
                        text = { Text("Set nickname", fontSize = 12.sp) },
                        onClick = { menuOpen = false; onEditNickname() }
                    )
                    DropdownMenuItem(
                        text = { Text("Invite to party", fontSize = 12.sp) },
                        onClick = { menuOpen = false; onInviteToParty() }
                    )
                    DropdownMenuItem(
                        text = { Text("View profile", fontSize = 12.sp) },
                        onClick = { menuOpen = false; onProfile() }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove friend", fontSize = 12.sp) },
                        onClick = { menuOpen = false; onRemove() }
                    )
                    DropdownMenuItem(
                        text = { Text("Block player", fontSize = 12.sp, color = AerixSurface.danger) },
                        onClick = { menuOpen = false; onBlock() }
                    )
                }
            }
        }
    }
}

// ---- Messages Hub (Party channels beside DMs + Full-text search) -------------------------

@Composable
private fun SocialMessagesHubPage(
    activity: FragmentActivity,
    onOpenDm: (String) -> Unit,
    onOpenPartyChat: (String) -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    val parties = SocialBackend.parties
    val friends = SocialBackend.friends.filter { !it.pendingIncoming && !it.pendingOutgoing }

    Column(
        modifier = Modifier.fillMaxSize().padding(AerixSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Messages", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = AerixSurface.textPrimary)
                if (SocialBackend.unread > 0) SocialTag("${SocialBackend.unread} unread", AerixSurface.danger)
            }
            if (SocialBackend.offlineQueueSize > 0) {
                SocialTag("${SocialBackend.offlineQueueSize} queued offline", AerixSurface.warning)
            }
        }

        // Full-text search across every DM and Party channel
        SocialMessageSearchSection(
            onOpenDm = onOpenDm,
            onOpenParty = onOpenPartyChat
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            if (parties.isNotEmpty()) {
                item {
                    Text(
                        "PARTY CHANNELS",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                    )
                }
                items(parties, key = { "party_${it.id}" }) { party ->
                    val typer = SocialRealtime.activeTypers[SocialRealtime.partyTypingKey(party.id)]?.first
                    SocialPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPartyChat(party.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(AerixRadii.controlSmall))
                                    .background(accent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.aerix_ic_group_outlined),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(party.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                                    SocialTag("${party.memberCount}/${party.maxMembers}", accent)
                                }
                                Text(
                                    typer?.let { "$it is typing..." } ?: "${party.summaryTag()} · ${party.remainingText()}",
                                    fontSize = 11.sp,
                                    color = if (typer != null) accent else AerixSurface.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            SocialSmallButton("Open", true) { onOpenPartyChat(party.id) }
                        }
                    }
                }
            }

            item {
                Text(
                    "DIRECT MESSAGES",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AerixSurface.textMuted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }

            if (friends.isEmpty()) {
                item {
                    SocialPanel(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("Add a friend to start direct messaging.", fontSize = 12.sp, color = AerixSurface.textMuted)
                        }
                    }
                }
            } else {
                items(friends, key = { "dm_${it.id}" }) { friend ->
                    val typer = SocialRealtime.activeTypers[SocialRealtime.dmTypingKey(friend.id)]?.first
                    SocialPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDm(friend.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!friend.avatarUrl.isNullOrBlank()) {
                                SocialRemoteImage(
                                    url = friend.avatarUrl,
                                    fallbackIcon = R.drawable.aerix_ic_person_outlined,
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape
                                )
                            } else {
                                SocialHead(friend.mcUsername, 36)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(friend.effectiveName, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                                    if (friend.online && friend.joinable) SocialTag("Joinable", AerixSurface.success)
                                }
                                Text(
                                    typer?.let { "$it is typing..." } ?: friend.presenceSummary(),
                                    fontSize = 11.sp,
                                    color = when {
                                        typer != null -> accent
                                        friend.online -> AerixSurface.success
                                        else -> AerixSurface.textMuted
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_chevron_right),
                                contentDescription = null,
                                tint = AerixSurface.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Discovery Hub (LFG Board + Friends' Server Hub) -------------------------------------

private enum class SocialDiscoverSubTab(val label: String) {
    LFG("Looking for Group (LFG)"),
    SERVERS("Friends' Server Hub")
}

@Composable
private fun SocialDiscoverHubPage(
    activity: FragmentActivity,
    onOpenPartyChat: (SocialParty) -> Unit
) {
    var mode by remember { mutableStateOf(SocialDiscoverSubTab.LFG) }

    Column(
        modifier = Modifier.fillMaxSize().padding(AerixSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SocialDiscoverSubTab.values().forEach { t ->
                FilterChipPill(t.label, mode == t) { mode = t }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (mode) {
                SocialDiscoverSubTab.LFG -> SocialLfgBoardSection(
                    activity = activity,
                    onOpenPartyChat = onOpenPartyChat,
                    onNavigateLauncher = { AerixNav.navigate(it) }
                )
                SocialDiscoverSubTab.SERVERS -> SocialServerHubSection(
                    activity = activity,
                    onNavigateLauncher = { AerixNav.navigate(it) }
                )
            }
        }
    }
}

// ---- Activity (Party Invites + Mutual Friends on Pending Friend Requests) ----------------

@Composable
private fun SocialActivityPage(
    activity: FragmentActivity,
    onOpenPartyChat: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()
    val incomingFriends = SocialBackend.friends.filter { it.pendingIncoming }
    val outgoingFriends = SocialBackend.friends.filter { it.pendingOutgoing }
    val partyInvites = SocialBackend.partyInvites

    LaunchedEffect(Unit) {
        SocialBackend.refreshPartyInvites()
        SocialBackend.refreshFriends()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(AerixSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Activity", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = AerixSurface.textPrimary)
            SocialIconBubble(R.drawable.aerix_ic_refresh, "Refresh activity") {
                scope.launch {
                    SocialBackend.refreshPartyInvites()
                    SocialBackend.refreshFriends()
                }
            }
        }

        if (partyInvites.isEmpty() && incomingFriends.isEmpty() && outgoingFriends.isEmpty()) {
            SocialPanel(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
                    Text("All caught up — party invites and friend requests land here.", fontSize = 12.sp, color = AerixSurface.textMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                if (partyInvites.isNotEmpty()) {
                    item {
                        Text("PARTY INVITES", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = accent)
                    }
                    items(partyInvites, key = { "pinv_${it.id}" }) { inv ->
                        SocialPanel(Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SocialRemoteImage(
                                        url = inv.fromAvatar,
                                        fallbackIcon = R.drawable.aerix_ic_group_outlined,
                                        modifier = Modifier.size(38.dp),
                                        shape = CircleShape
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${inv.fromName} invited you to \"${inv.partyTitle}\"",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AerixSurface.textPrimary
                                        )
                                        Text(
                                            listOfNotNull(inv.mcVersion, inv.loader, inv.instanceName).joinToString(" · ").ifEmpty { "Minecraft session" },
                                            fontSize = 11.5.sp,
                                            color = AerixSurface.textSecondary
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SocialButton("Accept & Join", filled = true) {
                                        scope.launch {
                                            val ok = SocialBackend.respondToPartyInvite(inv, accept = true)
                                            if (ok) {
                                                Toast.makeText(activity, "Joined ${inv.partyTitle}!", Toast.LENGTH_SHORT).show()
                                                onOpenPartyChat(inv.partyId)
                                            } else {
                                                Toast.makeText(activity, SocialBackend.lastError ?: "Could not join party", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    SocialButton("Decline", filled = false) {
                                        scope.launch { SocialBackend.respondToPartyInvite(inv, accept = false) }
                                    }
                                }
                            }
                        }
                    }
                }

                if (incomingFriends.isNotEmpty()) {
                    item {
                        Text("FRIEND REQUESTS", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = accent)
                    }
                    items(incomingFriends, key = { "fin_${it.id}" }) { friend ->
                        SocialPanel(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SocialHead(friend.mcUsername, 36)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(friend.displayName, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                                    val mutualLine = if (friend.mutualCount > 0) {
                                        val preview = friend.mutualNames.take(3).joinToString(", ")
                                        if (preview.isNotEmpty()) "${friend.mutualCount} mutual friends: $preview"
                                        else "${friend.mutualCount} mutual friends"
                                    } else {
                                        "Wants to be friends"
                                    }
                                    Text(
                                        mutualLine,
                                        fontSize = 11.sp,
                                        color = if (friend.mutualCount > 0) accent else AerixSurface.textMuted
                                    )
                                }
                                SocialSmallButton("Accept", true) {
                                    scope.launch { SocialBackend.answerRequest(friend.id, true) }
                                }
                                SocialSmallButton("Decline", false) {
                                    scope.launch { SocialBackend.answerRequest(friend.id, false) }
                                }
                            }
                        }
                    }
                }

                if (outgoingFriends.isNotEmpty()) {
                    item {
                        Text("SENT REQUESTS", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textMuted)
                    }
                    items(outgoingFriends, key = { "fout_${it.id}" }) { friend ->
                        SocialPanel(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SocialHead(friend.mcUsername, 32)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(friend.displayName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AerixSurface.textPrimary)
                                    Text("Waiting for response", fontSize = 11.sp, color = AerixSurface.textMuted)
                                }
                                SocialSmallButton("Cancel", false) {
                                    scope.launch { SocialBackend.removeFriend(friend.id) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Settings ----------------------------------------------------------------------------

@Composable
private fun SocialSettingsPage(
    activity: FragmentActivity,
    launcherName: String?,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()
    val myProf = SocialBackend.myProfile
    var customStatus by remember(myProf?.statusText) { mutableStateOf(myProf?.statusText.orEmpty()) }
    var lookingToPlay by remember(myProf?.lookingToPlay) { mutableStateOf(myProf?.lookingToPlay ?: false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AerixSpacing.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Social Settings", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = AerixSurface.textPrimary)

        SocialPanel(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SocialHead(SocialBackend.mcUsername ?: launcherName, 42)
                Column(modifier = Modifier.weight(1f)) {
                    Text(SocialBackend.email ?: "Signed in", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                    Text(
                        "Plays as ${SocialBackend.mcUsername ?: launcherName ?: "no account selected"}",
                        fontSize = 11.5.sp,
                        color = AerixSurface.textSecondary
                    )
                    Text(
                        if (SocialRealtime.isConnected) "Supabase Realtime connected (low-overhead WebSocket)"
                        else "Realtime reconnecting — messages queue offline automatically",
                        fontSize = 10.5.sp,
                        color = if (SocialRealtime.isConnected) AerixSurface.success else AerixSurface.warning
                    )
                }
            }
        }

        SocialPanel(Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Custom Status & Presence", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                Text(
                    "While Minecraft runs, Rich Presence automatically shows your active version and mod count (e.g. \"Playing 1.21.4 · 34 mods\").",
                    fontSize = 11.sp,
                    color = AerixSurface.textMuted
                )
                SocialGlassInput(
                    value = customStatus,
                    onValueChange = { customStatus = it.take(60) },
                    label = "Launcher status line (optional)"
                )
                Row(
                    modifier = Modifier.clickable { lookingToPlay = !lookingToPlay },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = lookingToPlay,
                        onCheckedChange = { lookingToPlay = it },
                        colors = CheckboxDefaults.colors(checkedColor = accent)
                    )
                    Text("Show \"Looking to play\" badge when online", fontSize = 12.sp, color = AerixSurface.textPrimary)
                }
                SocialButton("Save Presence", filled = true) {
                    scope.launch {
                        SocialBackend.setPresence(customStatus.ifBlank { null }, lookingToPlay)
                        Toast.makeText(activity, "Presence updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (SocialBackend.needsOnboarding) {
                SocialSmallButton("Finish profile setup", true) {
                    SocialBackend.resumeOnboarding()
                }
            }
            SocialSmallButton("Resync name and head", !SocialBackend.needsOnboarding) {
                scope.launch {
                    SocialBackend.rememberMinecraftName(PojavAccountBridge.current?.username)
                    SocialBackend.loadProfile()
                    SocialBackend.refreshFriends()
                    Toast.makeText(activity, "Minecraft identity synced", Toast.LENGTH_SHORT).show()
                }
            }
            if (SocialBackend.offlineQueueSize > 0) {
                SocialSmallButton("Flush ${SocialBackend.offlineQueueSize} queued messages", false) {
                    scope.launch { SocialBackend.flushOfflineOutbox() }
                }
            }
            SocialSmallButton("Sign out", false, onClick = onSignOut)
        }
    }
}

// ---- Shared local controls ---------------------------------------------------------------

@Composable
private fun SocialField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean = false
) {
    val accent = AerixThemeManager.currentAccent()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AerixRadii.control))
            .background(AerixSurface.panelTrack)
            .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft), RoundedCornerShape(AerixRadii.control))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = AerixSurface.textMuted, fontSize = 12.5.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(color = AerixSurface.textPrimary, fontSize = 12.5.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SocialPillButton(
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "socialPillPress"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(AerixRadii.pill))
            .background(if (filled) accent else AerixSurface.panelTrack)
            .border(
                BorderStroke(AerixSpacing.hairline, if (filled) accent else AerixSurface.borderSoft),
                RoundedCornerShape(AerixRadii.pill)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled) AerixSurface.canvas else AerixSurface.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SocialSmallButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AerixRadii.controlSmall))
            .background(if (filled) accent else AerixSurface.panelTrack)
            .border(
                BorderStroke(AerixSpacing.hairline, if (filled) accent else AerixSurface.borderSoft),
                RoundedCornerShape(AerixRadii.controlSmall)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled) AerixSurface.canvas else AerixSurface.textPrimary,
            maxLines = 1
        )
    }
}

private val mcHeadCache = HashMap<String, ImageBitmap?>()

@Composable
private fun SocialHead(name: String?, size: Int = 36) {
    val clean = name?.trim()?.takeIf { it.isNotEmpty() } ?: "MHF_Steve"
    var bitmap by remember(clean) { mutableStateOf(mcHeadCache[clean]) }
    LaunchedEffect(clean) {
        if (mcHeadCache.containsKey(clean)) {
            bitmap = mcHeadCache[clean]
            return@LaunchedEffect
        }
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("https://mc-heads.net/avatar/$clean/64").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                }
                val bytes = conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        if (fetched != null) mcHeadCache[clean] = fetched
        bitmap = fetched
    }
    val shape = RoundedCornerShape(AerixRadii.compact)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .background(AerixSurface.panelTrack)
            .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft), shape),
        contentAlignment = Alignment.Center
    ) {
        val img = bitmap
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = clean,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_person_outlined),
                contentDescription = clean,
                tint = AerixSurface.textMuted,
                modifier = Modifier.size((size * 0.55f).dp)
            )
        }
    }
}
