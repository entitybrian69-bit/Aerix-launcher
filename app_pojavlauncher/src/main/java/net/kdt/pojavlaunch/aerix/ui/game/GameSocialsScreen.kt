/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The in-game social panel: sits inside the Aerix game menu with full AerixSurface liquid glass
 * and ThemeMotion while Minecraft keeps rendering behind it.
 */

package net.kdt.pojavlaunch.aerix.ui.game

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.social.SocialRealtime
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.ThemeMotion
import net.kdt.pojavlaunch.aerix.theme.accentGlow
import net.kdt.pojavlaunch.aerix.theme.accentRing
import net.kdt.pojavlaunch.aerix.ui.screens.FilterChipPill
import net.kdt.pojavlaunch.aerix.ui.screens.PartyDetailDialog
import net.kdt.pojavlaunch.aerix.ui.screens.SocialButton
import net.kdt.pojavlaunch.aerix.ui.screens.SocialConversationPanel
import net.kdt.pojavlaunch.aerix.ui.screens.SocialIconBubble
import net.kdt.pojavlaunch.aerix.ui.screens.SocialPanel
import net.kdt.pojavlaunch.aerix.ui.screens.SocialPartiesSection
import net.kdt.pojavlaunch.aerix.ui.screens.SocialRemoteImage
import net.kdt.pojavlaunch.aerix.ui.screens.SocialTag

private enum class InGameSocialTab(val label: String) {
    CHATS("Chats"),
    PARTIES("Parties"),
    ACTIVITY("Activity")
}

@Composable
fun GameSocialsScreen(accent: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val motion = ThemeMotion.rememberEnabled()
    val liveAccent = ThemeMotion.rememberPulse(accent, active = true, enabled = motion)

    var ready by remember { mutableStateOf(SocialBackend.signedIn) }
    var tab by remember { mutableStateOf(InGameSocialTab.CHATS) }
    var selectedFriendId by remember { mutableStateOf<String?>(null) }
    var selectedPartyId by remember { mutableStateOf<String?>(null) }

    val friends = SocialBackend.friends
    val acceptedFriends = friends.filter { !it.pendingIncoming && !it.pendingOutgoing }
    val incomingFriends = friends.filter { it.pendingIncoming }
    val parties = SocialBackend.parties
    val partyInvites = SocialBackend.partyInvites
    val activityCount = incomingFriends.size + partyInvites.size

    LaunchedEffect(Unit) {
        SocialBackend.attach(context)
        ready = SocialBackend.signedIn
        if (ready) {
            SocialBackend.rememberMinecraftName(PojavAccountBridge.current?.username)
            SocialBackend.loadProfile()
            SocialBackend.refreshFriends()
            SocialBackend.refreshParties()
            SocialBackend.refreshPartyInvites()
            SocialBackend.unreadCount()
            if (selectedPartyId == null && selectedFriendId == null) {
                selectedPartyId = SocialBackend.parties.firstOrNull()?.id
                if (selectedPartyId == null) {
                    selectedFriendId = SocialBackend.friends.firstOrNull { !it.pendingIncoming && !it.pendingOutgoing }?.id
                }
            }
        }
    }

    val panelShape = RoundedCornerShape(AerixRadii.panel)
    Surface(
        shape = panelShape,
        color = AerixSurface.panel,
        contentColor = AerixSurface.textPrimary,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
        modifier = Modifier
            .fillMaxSize()
            .liquidGlass(
                shape = panelShape,
                tint = AerixSurface.panelGlassTint,
                strength = 1f,
                elevation = AerixMetrics.glassFloatingElevation
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AerixSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
        ) {
            // Glass top header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                SocialIconBubble(R.drawable.aerix_ic_arrow_back, "Back to game menu", onBack)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "In-Game Socials",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AerixSurface.textPrimary
                        )
                        if (SocialRealtime.isConnected) {
                            SocialTag("Live", AerixSurface.success)
                        }
                        if (SocialBackend.unread > 0) {
                            SocialTag("${SocialBackend.unread} unread", AerixSurface.danger)
                        }
                    }
                    Text(
                        "${acceptedFriends.count { it.online }} friends online · ${parties.size} active ${if (parties.size == 1) "party" else "parties"}",
                        fontSize = 11.sp,
                        color = AerixSurface.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Segmented glass tabs
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    InGameSocialTab.values().forEach { item ->
                        val selected = tab == item
                        val label = when (item) {
                            InGameSocialTab.ACTIVITY -> if (activityCount > 0) "${item.label} ($activityCount)" else item.label
                            InGameSocialTab.PARTIES -> if (parties.isNotEmpty()) "${item.label} (${parties.size})" else item.label
                            else -> item.label
                        }
                        val pillShape = RoundedCornerShape(AerixRadii.pill)
                        Box(
                            modifier = Modifier
                                .clip(pillShape)
                                .background(if (selected) liveAccent else AerixSurface.panelTrack)
                                .accentGlow(liveAccent, selected, motion)
                                .accentRing(liveAccent, pillShape, selected, motion, AerixSpacing.hairline)
                                .border(
                                    BorderStroke(AerixSpacing.hairline, if (selected) liveAccent else AerixSurface.borderSoft),
                                    pillShape
                                )
                                .clickable(role = Role.Tab) { tab = item }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) AerixSurface.canvas else AerixSurface.textPrimary
                            )
                        }
                    }
                    SocialIconBubble(R.drawable.aerix_ic_refresh, "Refresh") {
                        scope.launch {
                            SocialBackend.refreshFriends()
                            SocialBackend.refreshParties()
                            SocialBackend.refreshPartyInvites()
                            SocialBackend.unreadCount()
                        }
                    }
                }
            }

            if (!ready) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SocialPanel {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_people),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(28.dp)
                            )
                            Text("Sign in to Socials from the launcher", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                            Text(
                                "Once signed in on the main launcher screen, your friends, parties and Realtime chat stay live here in-game.",
                                fontSize = 11.5.sp,
                                color = AerixSurface.textSecondary
                            )
                        }
                    }
                }
                return@Column
            }

            when (tab) {
                InGameSocialTab.CHATS -> {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
                    ) {
                        // Left conversation rail (Party channels + DMs)
                        SocialPanel(
                            modifier = Modifier
                                .width(220.dp)
                                .fillMaxHeight()
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (parties.isNotEmpty()) {
                                    item {
                                        Text(
                                            "PARTIES",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accent,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    items(parties, key = { "ig_party_${it.id}" }) { p ->
                                        val isPicked = selectedPartyId == p.id
                                        val rowShape = RoundedCornerShape(AerixRadii.controlSmall)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(rowShape)
                                                .background(if (isPicked) accent.copy(alpha = 0.2f) else AerixSurface.panelTrack.copy(alpha = 0.55f))
                                                .border(
                                                    BorderStroke(AerixSpacing.hairline, if (isPicked) accent else AerixSurface.borderSoft),
                                                    rowShape
                                                )
                                                .clickable {
                                                    selectedFriendId = null
                                                    selectedPartyId = p.id
                                                }
                                                .padding(horizontal = 9.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.aerix_ic_group_outlined),
                                                contentDescription = null,
                                                tint = accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(p.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("${p.memberCount}/${p.maxMembers} · ${p.remainingText()}", fontSize = 10.sp, color = AerixSurface.textMuted, maxLines = 1)
                                            }
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        "FRIENDS",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AerixSurface.textMuted,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                                    )
                                }

                                if (acceptedFriends.isEmpty()) {
                                    item {
                                        Text(
                                            "No friends added yet",
                                            fontSize = 11.sp,
                                            color = AerixSurface.textMuted,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                } else {
                                    items(acceptedFriends, key = { "ig_dm_${it.id}" }) { friend ->
                                        val isPicked = selectedPartyId == null && selectedFriendId == friend.id
                                        val rowShape = RoundedCornerShape(AerixRadii.controlSmall)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(rowShape)
                                                .background(if (isPicked) accent.copy(alpha = 0.2f) else AerixSurface.panelTrack.copy(alpha = 0.55f))
                                                .border(
                                                    BorderStroke(AerixSpacing.hairline, if (isPicked) accent else AerixSurface.borderSoft),
                                                    rowShape
                                                )
                                                .clickable {
                                                    selectedPartyId = null
                                                    selectedFriendId = friend.id
                                                }
                                                .padding(horizontal = 9.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.BottomEnd) {
                                                SocialRemoteImage(
                                                    url = friend.avatarUrl,
                                                    fallbackIcon = R.drawable.aerix_ic_person_outlined,
                                                    modifier = Modifier.size(28.dp),
                                                    shape = CircleShape
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (friend.online) AerixSurface.success else AerixSurface.textMuted)
                                                        .border(1.dp, AerixSurface.canvas, CircleShape)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(friend.effectiveName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    friend.presenceSummary(),
                                                    fontSize = 10.sp,
                                                    color = if (friend.online) AerixSurface.success else AerixSurface.textMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Right live conversation pane
                        val activeParty = parties.firstOrNull { it.id == selectedPartyId }
                        val activeFriend = if (activeParty == null) acceptedFriends.firstOrNull { it.id == selectedFriendId } else null
                        var managePartyOpen by remember { mutableStateOf(false) }

                        if (activeParty != null || activeFriend != null) {
                            SocialConversationPanel(
                                friend = activeFriend,
                                party = activeParty,
                                compact = true,
                                onOpenPartyDetails = if (activeParty != null) { { managePartyOpen = true } } else null,
                                onJoinSession = if (activity != null) {
                                    {
                                        if (activeParty != null) {
                                            PojavLaunchBridge.joinSocialSession(
                                                activity = activity,
                                                mcVersion = activeParty.mcVersion,
                                                loaderName = activeParty.loader,
                                                instanceHint = activeParty.instanceName,
                                                modpackSource = activeParty.modpackSource,
                                                modpackId = activeParty.modpackId,
                                                modpackIconUrl = activeParty.modpackIconUrl,
                                                serverAddress = activeParty.serverAddress
                                            )
                                        } else if (activeFriend != null) {
                                            val hosted = parties.firstOrNull { it.hostId == activeFriend.id }
                                            PojavLaunchBridge.joinSocialSession(
                                                activity = activity,
                                                mcVersion = hosted?.mcVersion ?: activeFriend.mcVersion,
                                                loaderName = hosted?.loader,
                                                instanceHint = hosted?.instanceName,
                                                modpackSource = hosted?.modpackSource,
                                                modpackId = hosted?.modpackId,
                                                modpackIconUrl = hosted?.modpackIconUrl,
                                                serverAddress = hosted?.serverAddress
                                            )
                                        }
                                    }
                                } else null,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            if (managePartyOpen && activeParty != null && activity != null) {
                                PartyDetailDialog(
                                    activity = activity,
                                    party = activeParty,
                                    friends = acceptedFriends,
                                    onDismiss = { managePartyOpen = false },
                                    onOpenChat = { managePartyOpen = false }
                                )
                            }
                        } else {
                            SocialPanel(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Pick a party or friend on the left to chat live.", fontSize = 12.sp, color = AerixSurface.textMuted)
                                }
                            }
                        }
                    }
                }

                InGameSocialTab.PARTIES -> {
                    if (activity != null) {
                        SocialPartiesSection(
                            activity = activity,
                            onOpenPartyChat = { party ->
                                selectedFriendId = null
                                selectedPartyId = party.id
                                tab = InGameSocialTab.CHATS
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                }

                InGameSocialTab.ACTIVITY -> {
                    if (partyInvites.isEmpty() && incomingFriends.isEmpty()) {
                        SocialPanel(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No pending party invites or friend requests.", fontSize = 12.sp, color = AerixSurface.textMuted)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(partyInvites, key = { "ig_pinv_${it.id}" }) { inv ->
                                SocialPanel(Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${inv.fromName} invited you to \"${inv.partyTitle}\"", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text(listOfNotNull(inv.mcVersion, inv.loader).joinToString(" · "), fontSize = 11.sp, color = AerixSurface.textMuted)
                                        }
                                        SocialButton("Accept", filled = true) {
                                            scope.launch {
                                                if (SocialBackend.respondToPartyInvite(inv, true)) {
                                                    selectedFriendId = null
                                                    selectedPartyId = inv.partyId
                                                    tab = InGameSocialTab.CHATS
                                                } else {
                                                    Toast.makeText(context, SocialBackend.lastError ?: "Could not join", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        SocialButton("Decline", filled = false) {
                                            scope.launch { SocialBackend.respondToPartyInvite(inv, false) }
                                        }
                                    }
                                }
                            }
                            items(incomingFriends, key = { "ig_freq_${it.id}" }) { req ->
                                SocialPanel(Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${req.displayName} sent a friend request", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            if (req.mutualCount > 0) {
                                                Text("${req.mutualCount} mutual friends", fontSize = 11.sp, color = accent)
                                            }
                                        }
                                        FilterChipPill("Accept", true) { scope.launch { SocialBackend.answerRequest(req.id, true) } }
                                        FilterChipPill("Decline", false) { scope.launch { SocialBackend.answerRequest(req.id, false) } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
