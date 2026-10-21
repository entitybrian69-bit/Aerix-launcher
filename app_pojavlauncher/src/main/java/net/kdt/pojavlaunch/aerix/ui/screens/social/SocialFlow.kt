/*
 * Aerix Launcher — Social ecosystem shell. A launcher inside the launcher: its own
 * screens, its own header with a back arrow, and its own flow state, with no
 * dependency on the Play dashboard until the user backs out.
 *
 * UI only. Friends, messages and calls are simulated by SocialHubStore until the
 * Aerix account backend exists.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.ui.screens.social

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixAccount
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing

/** Every screen inside the Social ecosystem. */
enum class SocialRoute { AUTH, PROFILE_SETUP, HUB, CHAT, PROFILE, PRIVACY, NOTIFICATIONS, VOICE }

/** Mock addresses: Google sign-in has no OAuth client yet, so the flow simulates the result. */
private const val MOCK_GOOGLE_ACCOUNT = "nova@gmail.com"

/**
 * Entry point for the Social ecosystem. Pushes its own screens over the launcher content
 * and returns to the Play dashboard only when the back arrow leaves the last Social screen.
 */
@Composable
fun SocialFlow(onExit: () -> Unit) {
    val start = when {
        SocialProfileStore.profileReady -> SocialRoute.HUB
        SocialProfileStore.signedIn -> SocialRoute.PROFILE_SETUP
        else -> SocialRoute.AUTH
    }
    var route by remember { mutableStateOf(start) }
    var history by remember { mutableStateOf(listOf<SocialRoute>()) }

    fun open(next: SocialRoute) {
        history = history + route
        route = next
    }

    fun back(): Boolean {
        if (history.isEmpty()) return false
        route = history.last()
        history = history.dropLast(1)
        return true
    }

    // Rail destinations land on the dashboard tab (or Profile) and reset the shell
    // back stack, like a top-level destination in its own app.
    fun railTo(tab: Int) {
        SocialHubStore.hubTab = tab
        history = emptyList()
        route = SocialRoute.HUB
    }

    BackHandler { if (!back()) onExit() }

    // DECISION: the rail destinations and the dashboard tabs are the same three
    // places (Friends/Chats/Party share SocialHubStore.hubTab); both stay visible
    // and in sync, with the rail also carrying Profile and Exit.
    Row(modifier = Modifier.fillMaxSize()) {
        if (route != SocialRoute.AUTH && route != SocialRoute.PROFILE_SETUP) {
            SocialRail(
                destination = when (route) {
                    SocialRoute.CHAT -> SocialRailDestination.CHATS
                    SocialRoute.PROFILE, SocialRoute.PRIVACY,
                    SocialRoute.NOTIFICATIONS, SocialRoute.VOICE -> SocialRailDestination.PROFILE
                    else -> when (SocialHubStore.hubTab) {
                        1 -> SocialRailDestination.CHATS
                        2 -> SocialRailDestination.PARTY
                        else -> SocialRailDestination.FRIENDS
                    }
                },
                onFriends = { railTo(0) },
                onChats = { railTo(1) },
                onParty = { railTo(2) },
                onProfile = { if (route != SocialRoute.PROFILE) open(SocialRoute.PROFILE) },
                onExit = onExit
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            (fadeIn(tween(SocialMotion.PAGE_MS)) +
                slideInHorizontally(tween(SocialMotion.PAGE_MS)) { it / 12 }) togetherWith
                (fadeOut(tween(SocialMotion.PAGE_EXIT_MS)) +
                    slideOutHorizontally(tween(SocialMotion.PAGE_MS)) { -it / 14 })
        },
        label = "socialRoute"
    ) { current ->
        when (current) {
            SocialRoute.AUTH -> SocialAuthScreen(
                onBack = onExit,
                onSignedIn = {
                    SocialProfileStore.signIn(MOCK_GOOGLE_ACCOUNT)
                    open(
                        if (SocialProfileStore.profileReady) SocialRoute.HUB
                        else SocialRoute.PROFILE_SETUP
                    )
                }
            )
            SocialRoute.PROFILE_SETUP -> SocialProfileSetupScreen(
                onBack = { if (!back()) onExit() },
                onFinish = {
                    SocialProfileStore.profileReady = true
                    open(SocialRoute.HUB)
                }
            )
            SocialRoute.HUB -> SocialHubPanel(
                onExit = onExit,
                onChat = { name ->
                    SocialNavStore.friendName = name
                    open(SocialRoute.CHAT)
                },
                onProfile = { open(SocialRoute.PROFILE) },
                onPrivacy = { open(SocialRoute.PRIVACY) },
                onNotifications = { open(SocialRoute.NOTIFICATIONS) },
                onVoice = { open(SocialRoute.VOICE) }
            )
            SocialRoute.CHAT -> SocialChatPanel(
                onBack = { back() },
                onOpenProfile = { open(SocialRoute.PROFILE) }
            )
            SocialRoute.PROFILE -> SocialProfilePanel(
                onBack = { back() },
                onPrivacy = { open(SocialRoute.PRIVACY) },
                onNotifications = { open(SocialRoute.NOTIFICATIONS) },
                onSignedOut = {
                    SocialProfileStore.signOut()
                    history = emptyList()
                    route = SocialRoute.AUTH
                }
            )
            SocialRoute.PRIVACY -> SocialPrivacyScreen(
                state = SocialSettingsStore.privacy,
                onBack = { back() },
                onSave = { back() },
                onCancel = { back() }
            )
            SocialRoute.NOTIFICATIONS -> SocialNotificationsScreen(
                state = SocialSettingsStore.notifications,
                androidNotificationsEnabled = true,
                onOpenAndroidSettings = { },
                onBack = { back() },
                onSave = { back() },
                onCancel = { back() }
            )
            SocialRoute.VOICE -> SocialVoicePanel(onBack = { back() })
        }
    }
        }
    }
}

/* ---------------------------------------------------------------------------------------- */
/* Shared chrome                                                                            */
/* ---------------------------------------------------------------------------------------- */

/** Header used by every Social screen: back arrow, title, optional trailing content. */
@Composable
internal fun SocialHeader(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SocialDimens.headerHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "Back to the launcher" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_arrow_back),
                contentDescription = null,
                tint = SocialPalette.textPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(SocialDimens.gapSmall))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = SocialPalette.textPrimary,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** Standard Social page padding; keeps the layout breathable on phones and tablets. */
@Composable
internal fun SocialPage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SocialDimens.pageHorizontal, vertical = SocialDimens.pageVertical)
    ) {
        content()
    }
}

/**
 * Circular placeholder avatar for Social people. The mockups use photo avatars we do not
 * ship, so the app draws a generic human illustration instead; the same name always gets
 * the same face.
 */
@Composable
internal fun SocialAvatar(size: Int = 40, name: String = "", ring: Boolean = false) {
    val head = socialAvatarRes(name)
    val shape = CircleShape
    Image(
        painter = painterResource(head),
        contentDescription = name.ifEmpty { "Aerix member" },
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .border(
                BorderStroke(
                    if (ring) 2.dp else 1.dp,
                    if (ring) SocialPalette.accent else SocialPalette.borderSoft
                ),
                shape
            )
    )
}

/**
 * Minecraft head used only where the game itself is on screen: the in-game Social tab keeps
 * the player's Minecraft identity, while the launcher Social UI uses placeholder people.
 */
@Composable
internal fun SocialMinecraftHead(size: Int = 32, name: String = "") {
    val head = if (name.hashCode() % 2 == 0) R.drawable.aerix_head_steve else R.drawable.aerix_head_alex
    val shape = RoundedCornerShape((size / 4).dp)
    Image(
        painter = painterResource(head),
        contentDescription = null,
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, SocialPalette.borderSoft), shape)
    )
}

/* ---------------------------------------------------------------------------------------- */
/* 1. Authentication                                                                        */
/* ---------------------------------------------------------------------------------------- */

@Composable
internal fun SocialAuthScreen(onBack: () -> Unit, onSignedIn: () -> Unit) {
    SocialPage {
        Column(modifier = Modifier.fillMaxSize()) {
            SocialHeader(title = "Aerix account", onBack = onBack)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= 680.dp
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xl),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AuthFriendsArt(Modifier.weight(1f))
                        AuthCard(
                            modifier = Modifier.widthIn(min = 320.dp, max = 400.dp),
                            onSignedIn = onSignedIn,
                            onNotNow = onBack
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                    ) {
                        AuthFriendsArt(Modifier.fillMaxWidth().height(150.dp))
                        AuthCard(modifier = Modifier.fillMaxWidth(), onSignedIn = onSignedIn, onNotNow = onBack)
                    }
                }
            }
        }
    }
}

/** The four glowing friend tiles from the approved sign-in art. */
@Composable
private fun AuthFriendsArt(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            Text(
                text = "Your friends, together.",
                color = SocialPalette.textPrimary.copy(alpha = 0.92f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)) {
                listOf("Kai", "Mira", "Luna", "Alex").forEachIndexed { index, name ->
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        SocialPalette.panelRaised.copy(alpha = 0.9f),
                                        SocialPalette.panel.copy(alpha = 0.6f)
                                    )
                                )
                            )
                            .border(BorderStroke(1.dp, SocialPalette.border), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        SocialAvatar(size = 40, name = name, ring = index % 2 == 0)
                    }
                }
            }
            Text(
                text = "One Aerix name for every world you play in.",
                color = SocialPalette.textMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AuthCard(modifier: Modifier = Modifier, onSignedIn: () -> Unit, onNotNow: () -> Unit) {
    SocialCard(modifier = modifier, contentPadding = AerixSpacing.lg) {
        Text(
            text = "Connect with Aerix",
            style = MaterialTheme.typography.headlineSmall,
            color = SocialPalette.textPrimary
        )
        Text(
            text = "Friends, messages and voice in one place.",
            color = SocialPalette.textSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(AerixSpacing.sm))

        // Google button: white surface, dark label, the multicolour G badge.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = CircleShape,
            color = Color(0xFFF4F6F8),
            onClick = onSignedIn
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("G", color = Color(0xFF4285F4), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(AerixSpacing.sm))
                Text(
                    text = "Continue with Google",
                    color = Color(0xFF1B1F24),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        SocialGhostButton(
            text = "Not now",
            iconRes = null,
            onClick = onNotNow,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Aerix accounts are separate from your Minecraft account.",
            color = SocialPalette.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Google sign-in opens a secure system screen.",
            color = SocialPalette.textMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/* ---------------------------------------------------------------------------------------- */
/* 2. Profile creation                                                                      */
/* ---------------------------------------------------------------------------------------- */

@Composable
internal fun SocialProfileSetupScreen(onBack: () -> Unit, onFinish: () -> Unit) {
    val account: AerixAccount? = PojavAccountBridge.current
    var name by remember { mutableStateOf(SocialProfileStore.aerixName) }
    var slim by remember { mutableStateOf(false) }
    var showSkinPicker by remember { mutableStateOf(false) }
    var showCapePicker by remember { mutableStateOf(false) }

    val trimmed = name.trim()
    val valid = isValidAerixName(trimmed)
    val taken = remember(trimmed) { SocialHubStore.friends.any { it.name.equals(trimmed, true) } }
    val available = valid && !taken

    SocialPage {
        Column(modifier = Modifier.fillMaxSize()) {
            SocialHeader(title = "Create your Aerix profile", onBack = onBack)
            Spacer(Modifier.height(SocialDimens.gap))
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= 700.dp
                val preview: @Composable (Modifier) -> Unit = { modifier ->
                    ProfilePreviewCard(
                        modifier = modifier,
                        account = account,
                        onFront = { SocialProfileStore.previewAzimuth = 20 },
                        onBackView = { SocialProfileStore.previewAzimuth = 160 }
                    )
                }
                val form: @Composable (Modifier) -> Unit = { modifier ->
                    ProfileFormCard(
                        modifier = modifier,
                        name = name,
                        available = available,
                        taken = taken && valid,
                        skinName = SocialProfileStore.skinName,
                        capeName = SocialProfileStore.capeName,
                        onNameChange = { name = it.take(16) },
                        onChooseSkin = { showSkinPicker = true },
                        onChooseCape = { showCapePicker = true },
                        onFinish = {
                            SocialProfileStore.aerixName = trimmed
                            onFinish()
                        },
                        scrollable = wide
                    )
                }
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                    ) {
                        preview(Modifier.weight(0.85f).fillMaxHeight())
                        form(Modifier.weight(1.15f).fillMaxHeight())
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                    ) {
                        preview(Modifier.fillMaxWidth().height(260.dp))
                        form(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (showSkinPicker) {
        SocialChoiceDialog(
            title = "Choose skin",
            subtitle = "PNG skins, 64x64 or legacy 64x32.",
            options = listOf("Current skin (Classic)", "Current skin (Slim)"),
            selected = if (slim) 1 else 0,
            onSelect = { index ->
                slim = index == 1
                SocialProfileStore.skinName = if (slim) "Current skin · Slim" else "Current skin"
                showSkinPicker = false
            },
            onDismiss = { showSkinPicker = false }
        )
    }
    if (showCapePicker) {
        SocialChoiceDialog(
            title = "Choose cape",
            subtitle = "Optional. For your Aerix profile.",
            options = listOf("None", "Aerix cape"),
            selected = if (SocialProfileStore.capeName == "None") 0 else 1,
            onSelect = { index ->
                SocialProfileStore.capeName = if (index == 0) "None" else "Aerix cape"
                showCapePicker = false
            },
            onDismiss = { showCapePicker = false }
        )
    }
}

/** 3D model on its pedestal with the Front / Back toggle. */
@Composable
private fun ProfilePreviewCard(
    modifier: Modifier,
    account: AerixAccount?,
    onFront: () -> Unit,
    onBackView: () -> Unit
) {
    SocialCard(modifier = modifier, contentPadding = AerixSpacing.md) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Pedestal: a soft ellipse under the model, matching the approved mockup.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.72f)
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(SocialPalette.accent.copy(alpha = 0.22f), Color.Transparent)
                        )
                    )
            )
            AerixSkinPreview3D(
                account = account,
                modifier = Modifier.fillMaxSize(),
                azimuth = SocialProfileStore.previewAzimuth,
                pitch = 8,
                interactionEnabled = false,
                isVisible = true
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm, Alignment.CenterHorizontally)
        ) {
            val front = SocialProfileStore.previewAzimuth < 90
            if (front) {
                SocialPrimaryButton(text = "Front", iconRes = null, onClick = onFront)
            } else {
                SocialGhostButton(text = "Front", iconRes = null, onClick = onFront)
            }
            if (!front) {
                SocialPrimaryButton(text = "Back", iconRes = null, onClick = onBackView)
            } else {
                SocialGhostButton(text = "Back", iconRes = null, onClick = onBackView)
            }
        }
    }
}

@Composable
private fun ProfileFormCard(
    modifier: Modifier,
    name: String,
    available: Boolean,
    taken: Boolean,
    skinName: String,
    capeName: String,
    onNameChange: (String) -> Unit,
    onChooseSkin: () -> Unit,
    onChooseCape: () -> Unit,
    onFinish: () -> Unit,
    scrollable: Boolean
) {
    SocialCard(modifier = modifier, contentPadding = AerixSpacing.md) {
        // Only scroll inside the card when the card itself has a bounded height (wide
        // layout). In the narrow layout the page already scrolls; a scrollable here
        // would be measured with infinite max height and crash (device report 2026-10-21).
        Column(
            modifier = if (scrollable) {
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            },
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            Text("Aerix name", color = SocialPalette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Nova", color = SocialPalette.textMuted) },
                trailingIcon = {
                    if (available) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_check),
                            contentDescription = "Name available",
                            tint = Color(0xFF5BD98A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SocialPalette.accent,
                    unfocusedBorderColor = SocialPalette.borderSoft,
                    focusedTextColor = SocialPalette.textPrimary,
                    unfocusedTextColor = SocialPalette.textPrimary,
                    cursorColor = SocialPalette.accent
                )
            )
            when {
                taken -> Text("That name is already taken.", color = Color(0xFFFF9C9C), fontSize = 12.sp)
                available -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_check),
                        contentDescription = null,
                        tint = Color(0xFF5BD98A),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(AerixSpacing.xxs))
                    Text("Name available", color = Color(0xFF5BD98A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                else -> Text("Use 3 to 16 letters, numbers, or underscores.", color = SocialPalette.textMuted, fontSize = 12.sp)
            }
            Text(
                "This is your Aerix name, not your Minecraft login.",
                color = SocialPalette.textMuted,
                fontSize = 11.sp
            )

            ProfileChoiceRow(
                leading = { SocialAvatar(size = 36, name = name, ring = true) },
                title = "Skin",
                subtitle = skinName,
                action = "Choose skin",
                onClick = onChooseSkin
            )
            ProfileChoiceRow(
                leading = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(AerixRadii.controlSmall))
                            .background(SocialPalette.panelTrack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_style_outlined),
                            contentDescription = null,
                            tint = SocialPalette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                title = "Cape (optional)",
                subtitle = if (capeName == "None") "Add a cape to your profile" else capeName,
                action = "Choose cape",
                onClick = onChooseCape
            )

            Spacer(Modifier.height(AerixSpacing.xs))
            SocialPrimaryButton(
                text = "Finish setup",
                iconRes = null,
                onClick = onFinish,
                enabled = available,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "You can change these later.",
                color = SocialPalette.textMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "In-game skin and cape visibility requires supported integration.",
                color = SocialPalette.textMuted.copy(alpha = 0.7f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProfileChoiceRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AerixRadii.control),
        color = SocialPalette.panelRaised.copy(alpha = 0.42f),
        border = BorderStroke(AerixSpacing.hairline, SocialPalette.borderSoft)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Spacer(Modifier.width(AerixSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = SocialPalette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = SocialPalette.textSecondary, fontSize = 11.sp)
            }
            SocialGhostButton(text = action, iconRes = null, onClick = onClick)
        }
    }
}

/* ---------------------------------------------------------------------------------------- */
/* Dialogs and small shared pieces                                                          */
/* ---------------------------------------------------------------------------------------- */

/** Single-choice dialog used by the skin and cape pickers. */
@Composable
internal fun SocialChoiceDialog(
    title: String,
    subtitle: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        SocialCard(modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 360.dp).then(socialDialogModifier()), contentPadding = AerixSpacing.md) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = SocialPalette.textPrimary)
            Text(subtitle, color = SocialPalette.textMuted, fontSize = 12.sp)
            options.forEachIndexed { index, option ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AerixRadii.control),
                    color = if (index == selected) SocialPalette.accent.copy(alpha = 0.18f) else SocialPalette.panelRaised.copy(alpha = 0.4f),
                    onClick = { onSelect(index) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.weight(1f),
                            color = SocialPalette.textPrimary,
                            fontSize = 13.sp
                        )
                        if (index == selected) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_check),
                                contentDescription = null,
                                tint = SocialPalette.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            SocialGhostButton(text = "Cancel", iconRes = null, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Text prompt used by "Add friend". */
@Composable
internal fun SocialAddFriendDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val valid = isValidAerixName(trimmed)
    Dialog(onDismissRequest = onDismiss) {
        SocialCard(modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 360.dp).then(socialDialogModifier()), contentPadding = AerixSpacing.md) {
            Text("Add friend", style = MaterialTheme.typography.titleLarge, color = SocialPalette.textPrimary)
            Text("Friends are added by their unique Aerix name.", color = SocialPalette.textMuted, fontSize = 12.sp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(16) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Aerix name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SocialPalette.accent,
                    unfocusedBorderColor = SocialPalette.borderSoft,
                    focusedTextColor = SocialPalette.textPrimary,
                    unfocusedTextColor = SocialPalette.textPrimary,
                    cursorColor = SocialPalette.accent
                )
            )
            Text(
                "Use 3 to 16 letters, numbers, or underscores.",
                color = SocialPalette.textMuted,
                fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                SocialPrimaryButton(
                    text = "Send request",
                    iconRes = null,
                    enabled = valid,
                    onClick = { onSend(trimmed) }
                )
                SocialGhostButton(text = "Cancel", iconRes = null, onClick = onDismiss)
            }
        }
    }
}

/** Voice chat explainer, inside the Social flow. */
@Composable
internal fun SocialVoicePanel(onBack: () -> Unit) {
    var joinedMuted by remember { mutableStateOf(false) }
    SocialPage {
        Column(modifier = Modifier.fillMaxSize()) {
            SocialHeader(title = "Voice chat", onBack = onBack)
            Spacer(Modifier.height(SocialDimens.gap))
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= 680.dp
                val cards: @Composable (Modifier) -> Unit = { modifier ->
                    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)) {
                        SocialCard(modifier = Modifier.weight(1f)) {
                            Text("Talk with your party", style = MaterialTheme.typography.titleLarge, color = SocialPalette.textPrimary)
                            Text("Microphone access is optional.", color = SocialPalette.textMuted, fontSize = 12.sp)
                            Text(
                                "Microphone permission — Android asks for access after you continue.",
                                color = SocialPalette.textSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                "You stay in control — join muted, then use mute or push-to-talk.",
                                color = SocialPalette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        SocialCard(modifier = Modifier.weight(1f)) {
                            Text("Before you enable your microphone", style = MaterialTheme.typography.titleMedium, color = SocialPalette.textPrimary)
                            Text(
                                "Listen without a microphone — you can still hear the party without granting access.",
                                color = SocialPalette.textSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                "Android controls permission prompts. An app switch or permission prompt may interrupt gameplay.",
                                color = SocialPalette.textMuted,
                                fontSize = 11.sp
                            )
                            SocialPrimaryButton(
                                text = if (joinedMuted) "Join unmuted" else "Join muted",
                                iconRes = null,
                                onClick = { joinedMuted = !joinedMuted }
                            )
                        }
                    }
                }
                if (wide) cards(Modifier.fillMaxSize()) else Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)) {
                        SocialCard(modifier = Modifier.weight(1f)) {
                            Text("Talk with your party", style = MaterialTheme.typography.titleLarge, color = SocialPalette.textPrimary)
                            Text("Microphone access is optional.", color = SocialPalette.textMuted, fontSize = 12.sp)
                            Text("You join muted and can unmute when you are ready.", color = SocialPalette.textSecondary, fontSize = 12.sp)
                        }
                        SocialCard(modifier = Modifier.weight(1f)) {
                            Text("Stay in control", style = MaterialTheme.typography.titleMedium, color = SocialPalette.textPrimary)
                            Text("You can listen without granting microphone access.", color = SocialPalette.textSecondary, fontSize = 12.sp)
                            SocialPrimaryButton(
                                text = if (joinedMuted) "Join unmuted" else "Join muted",
                                iconRes = null,
                                onClick = { joinedMuted = !joinedMuted }
                            )
                        }
                    }
                }
            }
        }
    }
}
