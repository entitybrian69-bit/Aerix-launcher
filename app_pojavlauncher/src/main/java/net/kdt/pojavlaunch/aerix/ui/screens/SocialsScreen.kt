/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The social space: its own glass sidebar and its own pages, opened from Home.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.social.SocialFriend
import net.kdt.pojavlaunch.aerix.social.SocialMessage
import net.kdt.pojavlaunch.aerix.social.SocialProfile
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.theme.ThemeMotion
import net.kdt.pojavlaunch.aerix.theme.accentGlow
import net.kdt.pojavlaunch.aerix.theme.accentRing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---- destinations -------------------------------------------------------------------------

private enum class SocialTab(val label: String) {
    FRIENDS("Friends"),
    MESSAGES("Messages"),
    ACTIVITY("Activity"),
    SETTINGS("Settings")
}

private sealed interface SocialDestination {
    /** 0 for a sidebar page, 1 for a conversation opened on top of one. Drives the slide. */
    val depth: Int

    data class Page(val tab: SocialTab) : SocialDestination {
        override val depth: Int get() = 0
    }

    data class Chat(val friend: SocialFriend) : SocialDestination {
        override val depth: Int get() = 1
    }

    /** Your own card, or somebody else's, opened on top of a page. */
    data class Profile(val username: String?) : SocialDestination {
        override val depth: Int get() = 1
    }
}

/**
 * The social space. It owns its whole window: the launcher's own rail is hidden while this is on
 * screen and [SocialSidebar] takes its place, so friends, messages, activity and the social
 * settings are one level deep instead of competing with the launcher's sections.
 */
@Composable
fun SocialsScreen(activity: FragmentActivity, onExit: () -> Unit = {}) {
    val account = PojavAccountBridge.current
    var tab by remember { mutableStateOf(SocialTab.FRIENDS) }
    var chatWith by remember { mutableStateOf<SocialFriend?>(null) }
    var profileOpen by remember { mutableStateOf(false) }
    var profileUser by remember { mutableStateOf<String?>(null) }
    val signedIn = SocialBackend.signedIn
    val motion = ThemeMotion.rememberEnabled()

    LaunchedEffect(Unit) {
        SocialBackend.attach(activity)
        // An aerix-social://friend/CODE link brings us here with a code waiting.
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
            SocialBackend.unreadCount()
        }
    }
    LaunchedEffect(signedIn) {
        if (signedIn) {
            SocialBackend.rememberMinecraftName(account?.username)
            SocialBackend.loadProfile()
            SocialBackend.loadMyProfile()
            SocialBackend.refreshFriends()
            SocialBackend.unreadCount()
        }
        while (signedIn) {
            SocialBackend.setPresence(SocialBackend.myProfile?.statusText, SocialBackend.myProfile?.lookingToPlay ?: false)
            delay(60_000)
            SocialBackend.refreshFriends()
            SocialBackend.unreadCount()
        }
    }

    val destination: SocialDestination = when {
        chatWith != null -> SocialDestination.Chat(chatWith!!)
        profileOpen -> SocialDestination.Profile(profileUser)
        else -> SocialDestination.Page(tab)
    }

    // Back leaves the conversation first, then returns to Friends, then leaves the social space.
    // The social space owns the whole window, so its back never falls through to the launcher.
    BackHandler(enabled = true) {
        when {
            chatWith != null -> chatWith = null
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
            inChat = chatWith != null || profileOpen,
            onTab = { picked ->
                chatWith = null
                profileOpen = false
                tab = picked
            },
            onExit = onExit,
            onProfile = {
                chatWith = null
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
            } else if (SocialBackend.needsOnboarding) {
                // First sign-in: the profile form takes the panel until it is filled in.
                SocialProfileForm(
                    activity = activity,
                    profile = SocialBackend.myProfile,
                    editing = false,
                    onClose = { },
                    onSaved = { }
                )
            } else {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        val forward = targetState.depth >= initialState.depth
                        val enterSpec = spring<IntOffset>(dampingRatio = 0.88f, stiffness = 340f)
                        val enter = slideInHorizontally(enterSpec) { full ->
                            if (forward) (full * 0.12f).toInt() else -(full * 0.12f).toInt()
                        } + fadeIn(tween( if (motion) 220 else 0 ))
                        val exit = slideOutHorizontally(tween(if (motion) 190 else 0)) { full ->
                            if (forward) -(full * 0.08f).toInt() else (full * 0.08f).toInt()
                        } + fadeOut(tween(if (motion) 150 else 0))
                        enter togetherWith exit
                    },
                    label = "socialDestination"
                ) { dest ->
                    when (dest) {
                        is SocialDestination.Chat -> SocialChat(
                            activity = activity,
                            friend = dest.friend,
                            motion = motion,
                            onBack = { chatWith = null },
                            onOpenSettings = null
                        )
                        is SocialDestination.Profile -> SocialProfilePage(
                            activity = activity,
                            username = dest.username
                        ) { profileOpen = false }
                        is SocialDestination.Page -> when (dest.tab) {
                            SocialTab.FRIENDS -> SocialFriendsPage(activity, motion, onOpenChat = { friend ->
                                chatWith = friend
                            }, onOpenProfile = { who ->
                                profileUser = who
                                profileOpen = true
                            })
                            SocialTab.MESSAGES -> SocialMessagesPage(activity, motion) { friend ->
                                chatWith = friend
                            }
                            SocialTab.ACTIVITY -> SocialActivityPage(activity, motion)
                            SocialTab.SETTINGS -> SocialSettingsPage(activity, account?.username, motion, onSignOut = {
                                SocialBackend.signOut()
                            })
                        }
                    }
                }
            }
        }
    }
}

/** The social sidebar: Friends, Messages, Activity, Settings, and the way back to the launcher. */
@Composable
private fun SocialSidebar(
    tab: SocialTab,
    signedIn: Boolean,
    inChat: Boolean,
    onTab: (SocialTab) -> Unit,
    onExit: () -> Unit,
    onProfile: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
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
            .padding(vertical = AerixSpacing.smPlus),
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
        Spacer(Modifier.height(2.dp))
        Icon(
            painter = painterResource(R.drawable.ic_aerix_mark),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(1.dp)
                .background(AerixSurface.borderSoft)
        )
        Spacer(Modifier.height(2.dp))

        SocialRailItem(R.drawable.aerix_ic_people, SocialTab.FRIENDS.label, signedIn && !inChat && tab == SocialTab.FRIENDS, accent, false) {
            onTab(SocialTab.FRIENDS)
        }
        SocialRailItem(
            R.drawable.aerix_ic_chat,
            SocialTab.MESSAGES.label,
            signedIn && !inChat && tab == SocialTab.MESSAGES,
            accent,
            false,
            badge = SocialBackend.unread
        ) {
            onTab(SocialTab.MESSAGES)
        }
        SocialRailItem(R.drawable.aerix_ic_bell, SocialTab.ACTIVITY.label, signedIn && !inChat && tab == SocialTab.ACTIVITY, accent, false) {
            onTab(SocialTab.ACTIVITY)
        }
        SocialRailItem(R.drawable.aerix_ic_settings_filled, SocialTab.SETTINGS.label, signedIn && !inChat && tab == SocialTab.SETTINGS, accent, false) {
            onTab(SocialTab.SETTINGS)
        }

        Spacer(Modifier.weight(1f))

        if (signedIn) {
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.clickable(role = Role.Button, onClick = onProfile)
            ) {
                SocialHead(SocialBackend.mcUsername ?: PojavAccountBridge.current?.username, 34)
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AerixSurface.success)
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
            .size(width = if (compact) 34.dp else 38.dp, height = if (compact) 32.dp else 38.dp)
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
            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
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

    // One row, always: the mark and the promise on the left, the form on the right, both buttons
    // side by side. On a landscape phone the card is about 230 dp tall, so nothing hides.
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
                        "Play with friends without leaving the game",
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

// ---- friends ------------------------------------------------------------------------------

@Composable
private fun SocialFriendsPage(
    activity: FragmentActivity,
    motion: Boolean,
    onOpenChat: (SocialFriend) -> Unit,
    onOpenProfile: (String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<SocialProfile?>(null) }
    var searched by remember { mutableStateOf(false) }
    val friends = SocialBackend.friends
    val incoming = friends.filter { it.pendingIncoming }
    val accepted = friends.filter { !it.pendingIncoming && !it.pendingOutgoing }
    val outgoing = friends.filter { it.pendingOutgoing }

    Column(modifier = Modifier.fillMaxSize().padding(AerixSpacing.lg)) {
        SocialPageHeader(
            title = "Socials",
            badge = if (accepted.any { it.online }) "${accepted.count { it.online }} online" else null
        )
        Spacer(Modifier.height(AerixSpacing.md))

        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)) {
            Column(modifier = Modifier.weight(1.35f).fillMaxHeight()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                ) {
                    SocialField(
                        value = query,
                        onValueChange = { query = it },
                        label = "Add a friend by Minecraft name",
                        modifier = Modifier.weight(1f)
                    )
                    SocialPillButton(label = "Find", filled = true) {
                        if (query.isBlank()) return@SocialPillButton
                        scope.launch {
                            found = SocialBackend.findPlayer(query)
                            searched = true
                        }
                    }
                }
                // Only an exact username finds anyone: nothing is listed, nothing is suggested.
                if (searched) {
                    Spacer(Modifier.height(AerixSpacing.xs))
                    val match = found
                    if (match == null) {
                        Text(
                            "No one is called @${query.trim().removePrefix("@")}",
                            fontSize = 11.sp,
                            color = AerixSurface.textMuted
                        )
                    } else {
                        SocialGlassRow(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SocialHead(match.username, 30)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        match.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AerixSurface.textPrimary
                                    )
                                    Text(
                                        match.handle.ifBlank { "@${match.username}" },
                                        fontSize = 10.sp,
                                        color = AerixSurface.textMuted
                                    )
                                }
                                SocialSmallButton("Add", true) {
                                    val name = match.username ?: return@SocialSmallButton
                                    scope.launch {
                                        if (SocialBackend.addFriendByName(name)) {
                                            query = ""
                                            found = null
                                            searched = false
                                            Toast.makeText(activity, "Request sent", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(
                                                activity,
                                                SocialBackend.lastError ?: "Could not send",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                                SocialSmallButton("Profile", false) {
                                    match.username?.let { onOpenProfile(it) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(AerixSpacing.sm))

                if (accepted.isEmpty() && incoming.isEmpty() && outgoing.isEmpty()) {
                    SocialEmptyCard(
                        "No friends yet",
                        "Send a request with a Minecraft name above. When they accept, you see when " +
                            "they are online and can message them here or inside the game.",
                        motion = motion
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.xsPlus)
                    ) {
                        itemsIndexed(accepted) { index, friend ->
                            SocialFriendRow(friend, Modifier.socialStagger(index, motion)) {
                                onOpenChat(friend)
                            }
                        }
                        items(outgoing) { friend ->
                            SocialGlassRow(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SocialHead(friend.mcUsername, 32)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "${friend.mcUsername} · request pending",
                                        fontSize = 12.sp,
                                        color = AerixSurface.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                SocialGlassCard(Modifier.socialStagger(0, motion)) {
                    Text("Requests", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    if (incoming.isEmpty()) {
                        Text(
                            "Nothing waiting. Requests you receive land here.",
                            fontSize = 11.sp,
                            color = AerixSurface.textMuted
                        )
                    } else {
                        incoming.take(3).forEach { friend ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SocialHead(friend.mcUsername, 28)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    friend.mcUsername,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AerixSurface.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                SocialSmallButton("Accept", true) {
                                    scope.launch { SocialBackend.answerRequest(friend.id, true) }
                                }
                                Spacer(Modifier.width(4.dp))
                                SocialSmallButton("Decline", false) {
                                    scope.launch { SocialBackend.answerRequest(friend.id, false) }
                                }
                            }
                        }
                    }
                }

                SocialGlassCard(Modifier.socialStagger(1, motion)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Your profile", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "View",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AerixSurface.accent,
                            modifier = Modifier.clickable { onOpenProfile(null) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SocialHead(SocialBackend.mcUsername ?: PojavAccountBridge.current?.username, 34)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                SocialBackend.mcUsername ?: PojavAccountBridge.current?.username ?: "No account",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AerixSurface.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                SocialBackend.email?.takeIf { it.isNotBlank() } ?: "Signed in",
                                fontSize = 11.sp,
                                color = AerixSurface.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialFriendRow(friend: SocialFriend, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SocialGlassRow(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SocialHead(friend.mcUsername, 34)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    friend.mcUsername,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AerixSurface.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (friend.online) "Online" else "Offline",
                    fontSize = 11.sp,
                    color = if (friend.online) AerixSurface.success else AerixSurface.textMuted
                )
            }
            SocialPresenceDot(friend.online)
        }
    }
}

// ---- messages -----------------------------------------------------------------------------

@Composable
private fun SocialMessagesPage(activity: FragmentActivity, motion: Boolean, onOpenChat: (SocialFriend) -> Unit) {
    val friends = SocialBackend.friends.filter { !it.pendingIncoming && !it.pendingOutgoing }
    Column(modifier = Modifier.fillMaxSize().padding(AerixSpacing.lg)) {
        SocialPageHeader(
            title = "Messages",
            badge = if (friends.isEmpty()) null else "${friends.size}",
            hint = "Tap a friend to open the conversation"
        )
        Spacer(Modifier.height(AerixSpacing.md))
        if (friends.isEmpty()) {
            SocialEmptyCard(
                "No conversations",
                "Add a friend first. Your chats with them live here, and in the in-game panel.",
                motion = motion
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(AerixSpacing.xsPlus)) {
                itemsIndexed(friends) { index, friend ->
                    SocialGlassRow(
                        modifier = Modifier.fillMaxWidth().socialStagger(index, motion),
                        onClick = { onOpenChat(friend) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SocialHead(friend.mcUsername, 38)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    friend.mcUsername,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AerixSurface.textPrimary
                                )
                                Text(
                                    if (friend.online) "Online now" else "Offline",
                                    fontSize = 11.sp,
                                    color = if (friend.online) AerixSurface.success else AerixSurface.textMuted
                                )
                            }
                            Text("›", fontSize = 18.sp, color = AerixSurface.textMuted)
                        }
                    }
                }
            }
        }
    }
}

// ---- activity -----------------------------------------------------------------------------

@Composable
private fun SocialActivityPage(activity: FragmentActivity, motion: Boolean) {
    val scope = rememberCoroutineScope()
    val incoming = SocialBackend.friends.filter { it.pendingIncoming }
    val outgoing = SocialBackend.friends.filter { it.pendingOutgoing }
    Column(modifier = Modifier.fillMaxSize().padding(AerixSpacing.lg)) {
        SocialPageHeader(
            title = "Activity",
            badge = if (incoming.isEmpty()) null else "${incoming.size} new"
        )
        Spacer(Modifier.height(AerixSpacing.md))
        if (incoming.isEmpty() && outgoing.isEmpty()) {
            SocialEmptyCard(
                "Nothing yet",
                "Friend requests and invitations show up here.",
                motion = motion
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(AerixSpacing.xsPlus)) {
                itemsIndexed(incoming) { index, friend ->
                    SocialGlassRow(modifier = Modifier.fillMaxWidth().socialStagger(index, motion)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SocialHead(friend.mcUsername, 32)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${friend.mcUsername} sent you a friend request",
                                fontSize = 12.sp,
                                color = AerixSurface.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            SocialSmallButton("Accept", true) {
                                scope.launch {
                                    SocialBackend.answerRequest(friend.id, true)
                                    Toast.makeText(activity, "Friend added", Toast.LENGTH_SHORT).show()
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            SocialSmallButton("Decline", false) {
                                scope.launch { SocialBackend.answerRequest(friend.id, false) }
                            }
                        }
                    }
                }
                items(outgoing) { friend ->
                    SocialGlassRow(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SocialHead(friend.mcUsername, 32)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Waiting for ${friend.mcUsername} to accept",
                                fontSize = 12.sp,
                                color = AerixSurface.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- settings -----------------------------------------------------------------------------

@Composable
private fun SocialSettingsPage(
    activity: FragmentActivity,
    launcherName: String?,
    motion: Boolean,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AerixSpacing.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        SocialPageHeader(title = "Settings")
        Spacer(Modifier.height(AerixSpacing.xs))

        SocialGlassCard(Modifier.socialStagger(0, motion)) {
            Text("Account", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SocialHead(SocialBackend.mcUsername ?: launcherName, 40)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        SocialBackend.email ?: "Signed in",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AerixSurface.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Plays as ${SocialBackend.mcUsername ?: launcherName ?: "no account selected"}",
                        fontSize = 11.sp,
                        color = AerixSurface.textMuted
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Friends see the Minecraft account selected in the launcher. Change that account and " +
                    "press resync, then they see the new name and head.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = AerixSurface.textSecondary
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialSmallButton("Resync name and head", true) {
                    scope.launch {
                        SocialBackend.rememberMinecraftName(PojavAccountBridge.current?.username)
                        SocialBackend.loadProfile()
                        SocialBackend.refreshFriends()
                        Toast.makeText(activity, "Updated", Toast.LENGTH_SHORT).show()
                    }
                }
                SocialSmallButton("Sign out", false, onClick = onSignOut)
            }
        }

        SocialGlassCard(Modifier.socialStagger(1, motion)) {
            Text("Privacy", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Only people you accepted as friends can see your name, your head and whether you are " +
                    "online. Messages are readable by the two people in the conversation.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = AerixSurface.textSecondary
            )
        }

        SocialGlassCard(Modifier.socialStagger(2, motion)) {
            Text("Where it works", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "The same friends and messages are in the in-game menu: open the game menu while " +
                    "Minecraft runs and pick Socials.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = AerixSurface.textSecondary
            )
        }
    }
}

// ---- one conversation ---------------------------------------------------------------------

@Composable
private fun SocialChat(
    activity: FragmentActivity,
    friend: SocialFriend,
    motion: Boolean,
    onBack: () -> Unit,
    onOpenSettings: (() -> Unit)?
) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<SocialMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    val myId = remember { SocialBackend.myUserId() }

    suspend fun reload() {
        messages = SocialBackend.loadMessages(friend.id)
    }
    LaunchedEffect(friend.id) {
        SocialBackend.markRead(friend.id)
        while (true) {
            reload()
            delay(3_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = AerixSpacing.lg, vertical = AerixSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialRailItem(
                iconRes = R.drawable.aerix_ic_arrow_back,
                label = "Back",
                selected = false,
                accent = AerixThemeManager.currentAccent(),
                compact = true,
                onClick = onBack
            )
            Spacer(Modifier.width(AerixSpacing.sm))
            SocialHead(friend.mcUsername, 34)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    friend.mcUsername,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AerixSurface.textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SocialPresenceDot(friend.online, 7.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (friend.online) "online now" else "offline",
                        fontSize = 11.sp,
                        color = if (friend.online) AerixSurface.success else AerixSurface.textMuted
                    )
                }
            }
            onOpenSettings?.let { open ->
                Text(
                    "Settings",
                    fontSize = 11.sp,
                    color = AerixSurface.textSecondary,
                    modifier = Modifier.clickable { open() }
                )
            }
        }
        Spacer(Modifier.height(AerixSpacing.sm))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Say hello to ${friend.mcUsername}. They see it here and inside the game.",
                        fontSize = 12.sp,
                        color = AerixSurface.textMuted
                    )
                }
            }
            itemsIndexed(messages) { index, message ->
                val mine = message.sender == myId
                Row(
                    modifier = Modifier.fillMaxWidth().socialStagger(index, motion),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        if (serverAddress(message.body) != null) {
                            // A message that is only an address becomes a card you can copy into
                            // the multiplayer list instead of retyping it.
                            Surface(
                                shape = RoundedCornerShape(AerixRadii.cardSmall),
                                color = if (mine) AerixThemeManager.currentAccent() else AerixSurface.panelRaised,
                                border = if (mine) null else BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Server address",
                                            fontSize = 9.sp,
                                            color = if (mine) AerixSurface.canvas.copy(alpha = 0.7f)
                                            else AerixSurface.textMuted
                                        )
                                        Text(
                                            serverAddress(message.body) ?: message.body,
                                            color = if (mine) AerixSurface.canvas else AerixSurface.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Copy",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (mine) AerixSurface.canvas else AerixSurface.accent,
                                        modifier = Modifier.clickable {
                                            val clipboard = activity.getSystemService(
                                                android.content.Context.CLIPBOARD_SERVICE
                                            ) as android.content.ClipboardManager
                                            clipboard.setPrimaryClip(
                                                android.content.ClipData.newPlainText(
                                                    "Server",
                                                    serverAddress(message.body) ?: message.body
                                                )
                                            )
                                            Toast.makeText(activity, "Address copied", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(AerixRadii.cardSmall),
                                color = if (mine) AerixThemeManager.currentAccent() else AerixSurface.panelRaised,
                                border = if (mine) null else BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
                            ) {
                                Text(
                                    message.body,
                                    color = if (mine) AerixSurface.canvas else AerixSurface.textPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                )
                            }
                        }
                        Text(timeLabel(message.atMs), fontSize = 9.sp, color = AerixSurface.textMuted)
                    }
                }
            }
        }

        Spacer(Modifier.height(AerixSpacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            SocialField(
                value = draft,
                onValueChange = { draft = it },
                label = "Message…",
                modifier = Modifier.weight(1f)
            )
            SocialRoundSendButton {
                val text = draft
                if (text.isNotBlank()) {
                    draft = ""
                    scope.launch {
                        if (SocialBackend.sendMessage(friend.id, text)) reload()
                        else Toast.makeText(activity, "Message did not send", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

// ---- shared pieces ------------------------------------------------------------------------

@Composable
private fun SocialPageHeader(title: String, badge: String? = null, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AerixSurface.textPrimary
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AerixSurface.success.copy(alpha = 0.16f))
                    .border(BorderStroke(AerixSpacing.hairline, AerixSurface.success.copy(alpha = 0.45f)), CircleShape)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(badge, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AerixSurface.success)
            }
        }
        if (hint != null) {
            Spacer(Modifier.width(10.dp))
            Text(hint, fontSize = 11.sp, color = AerixSurface.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The glass strip a row sits in: hairline rim, raised tint, and a ripple-free press. */
@Composable
private fun SocialGlassRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "rowPress"
    )
    val shape = RoundedCornerShape(AerixRadii.card)
    Surface(
        shape = shape,
        color = AerixSurface.panelRaised,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
        modifier = modifier
            .graphicsLayer { scaleX = pressedScale; scaleY = pressedScale }
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
                } else Modifier
            )
    ) {
        content()
    }
}

@Composable
private fun SocialGlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(AerixRadii.cardLarge),
        color = AerixSurface.panelRaised,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) { content() }
    }
}

@Composable
private fun SocialEmptyCard(title: String, body: String, motion: Boolean) {
    Surface(
        shape = RoundedCornerShape(AerixRadii.cardLarge),
        color = AerixSurface.panelRaised,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
        modifier = Modifier.fillMaxWidth().socialStagger(0, motion)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_people),
                contentDescription = null,
                tint = AerixThemeManager.currentAccent(),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(body, fontSize = 12.sp, lineHeight = 17.sp, color = AerixSurface.textSecondary)
        }
    }
}

@Composable
private fun SocialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(AerixRadii.control),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AerixSurface.textPrimary,
            unfocusedTextColor = AerixSurface.textPrimary,
            focusedLabelColor = AerixThemeManager.currentAccent(),
            unfocusedLabelColor = AerixSurface.textMuted,
            focusedBorderColor = AerixThemeManager.currentAccent(),
            unfocusedBorderColor = AerixSurface.borderSoft,
            cursorColor = AerixThemeManager.currentAccent()
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Accent-filled or glass pill button, with the press animation the launcher's own buttons use. */
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
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "pillPress"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(AerixRadii.control))
            .background(if (filled) accent else AerixSurface.panelRaised)
            .border(
                BorderStroke(AerixSpacing.hairline, if (filled) accent.copy(alpha = 0.85f) else AerixSurface.border),
                RoundedCornerShape(AerixRadii.control)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (filled) AerixSurface.canvas else AerixSurface.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SocialSmallButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "smallPress"
    )
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (filled) accent else AerixSurface.panelTrack)
            .border(
                BorderStroke(AerixSpacing.hairline, if (filled) accent.copy(alpha = 0.85f) else AerixSurface.borderSoft),
                CircleShape
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (filled) AerixSurface.canvas else AerixSurface.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun SocialRoundSendButton(onClick: () -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
        label = "sendPress"
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(accent)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .semantics { contentDescription = "Send message" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.aerix_ic_send),
            contentDescription = null,
            tint = AerixSurface.canvas,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** A presence dot that breathes while the friend is online, and holds still when they are not. */
@Composable
private fun SocialPresenceDot(online: Boolean, size: Dp = 9.dp) {
    val motion = ThemeMotion.rememberEnabled()
    val pulse = if (online && motion) rememberOnlinePulse() else 1f
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .clip(CircleShape)
            .background(if (online) AerixSurface.success else AerixSurface.textMuted.copy(alpha = 0.55f))
    )
}

@Composable
private fun rememberOnlinePulse(): Float {
    val transition = rememberInfiniteTransition(label = "presence")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "presenceScale"
    )
    return scale
}

@Composable
private fun SocialHead(name: String?, size: Int = 36) {
    var face by remember(name) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(name) {
        if (name.isNullOrBlank()) return@LaunchedEffect
        face = withContext(Dispatchers.IO) {
            PojavAccountBridge.loadSkinFacePreview(
                cacheKey = "social_$name",
                localFile = null,
                avatarUrl = "https://mc-heads.net/avatar/$name/64"
            )
        }
    }
    AerixImageWell(
        image = face,
        fallback = R.drawable.aerix_ic_person_outlined,
        size = size.dp,
        radius = (size / 4).dp
    )
}

/**
 * Rows and bubbles rise into place, one after another. With motion off - or the first frame of the
 * animation - everything is simply at its resting position.
 */
@Composable
private fun Modifier.socialStagger(index: Int, motion: Boolean): Modifier {
    var entered by remember(motion) { mutableStateOf(!motion) }
    LaunchedEffect(motion) {
        if (motion) {
            delay((index.coerceAtMost(9) * 32).toLong())
            entered = true
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "rowEnter"
    )
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 16f
    }
}

private fun timeLabel(ms: Long): String =
    if (ms <= 0) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

/** A message that is nothing but a Minecraft server address, e.g. play.example.net:25565. */
private fun serverAddress(body: String): String? {
    val text = body.trim()
    val colon = text.lastIndexOf(':')
    if (colon <= 0 || colon == text.lastIndex) return null
    val host = text.substring(0, colon)
    val port = text.substring(colon + 1)
    if (port.length !in 2..5 || port.any { !it.isDigit() }) return null
    if (host.length !in 3..253) return null
    if (!host.contains('.') && !host.contains('-')) return null
    if (host.any { !(it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') }) return null
    return text
}
