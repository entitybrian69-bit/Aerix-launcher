package net.kdt.pojavlaunch.aerix.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.social.SocialFriend
import net.kdt.pojavlaunch.aerix.social.SocialJoinToast
import net.kdt.pojavlaunch.aerix.social.SocialParty
import net.kdt.pojavlaunch.aerix.social.SocialSharedServer
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.theme.ThemeMotion
import net.kdt.pojavlaunch.aerix.theme.accentGlow
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.instances.Instances

@Composable
fun SocialPartiesSection(
    activity: Activity,
    onOpenPartyChat: (SocialParty) -> Unit,
    onNavigateLauncher: ((LauncherSection) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val parties = SocialBackend.parties
    val friends = SocialBackend.friends.filter { !it.pendingIncoming && !it.pendingOutgoing }
    var showCreate by remember { mutableStateOf(false) }
    var focusedPartyId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        SocialBackend.refreshParties()
    }

    val focusedParty = parties.firstOrNull { it.id == focusedPartyId }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Parties",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AerixSurface.textPrimary
                )
                Text(
                    "Create a session, invite friends, chat and launch into the host's exact setup.",
                    fontSize = 11.5.sp,
                    color = AerixSurface.textMuted
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SocialIconBubble(R.drawable.aerix_ic_refresh, "Refresh parties") {
                    scope.launch { SocialBackend.refreshParties() }
                }
                SocialButton("+ Create Party", filled = true) { showCreate = true }
            }
        }

        if (parties.isEmpty()) {
            SocialPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_group_outlined),
                        contentDescription = null,
                        tint = AerixThemeManager.currentAccent(),
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        "No active parties right now",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AerixSurface.textPrimary
                    )
                    Text(
                        "Start a party with a 2-hour default timer, invite friends or publish to Looking-for-Group.",
                        fontSize = 12.sp,
                        color = AerixSurface.textMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    SocialButton("Start a Party", filled = true) { showCreate = true }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(parties, key = { it.id }) { party ->
                    PartyCard(
                        party = party,
                        isHost = party.hostId == SocialBackend.myUserId(),
                        onOpenDetails = { focusedPartyId = party.id },
                        onOpenChat = { onOpenPartyChat(party) },
                        onJoinInstance = {
                            PojavLaunchBridge.joinSocialSession(
                                activity = activity,
                                mcVersion = party.mcVersion,
                                loaderName = party.loader,
                                instanceHint = party.instanceName,
                                modpackSource = party.modpackSource,
                                modpackId = party.modpackId,
                                modpackIconUrl = party.modpackIconUrl,
                                serverAddress = party.serverAddress,
                                onNavigate = onNavigateLauncher
                            )
                        }
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreatePartyDialog(
            defaultPublic = false,
            onDismiss = { showCreate = false },
            onCreated = { created ->
                showCreate = false
                focusedPartyId = created.id
            }
        )
    }

    if (focusedParty != null) {
        PartyDetailDialog(
            activity = activity,
            party = focusedParty,
            friends = friends,
            onDismiss = { focusedPartyId = null },
            onOpenChat = {
                focusedPartyId = null
                onOpenPartyChat(focusedParty)
            },
            onNavigateLauncher = onNavigateLauncher
        )
    }
}

@Composable
private fun PartyCard(
    party: SocialParty,
    isHost: Boolean,
    onOpenDetails: () -> Unit,
    onOpenChat: () -> Unit,
    onJoinInstance: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    SocialPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetails() }
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SocialRemoteImage(
                    url = party.hostAvatar,
                    fallbackIcon = R.drawable.aerix_ic_group_outlined,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            party.title,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AerixSurface.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isHost) SocialTag("Host", accent)
                        if (party.isPublic) SocialTag("LFG", AerixSurface.success)
                    }
                    Text(
                        "Hosted by ${party.hostName} · ${party.remainingText()}",
                        fontSize = 11.5.sp,
                        color = AerixSurface.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SocialTag("${party.memberCount}/${party.maxMembers} players", accent)
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                party.mcVersion?.takeIf { it.isNotBlank() }?.let { SocialTag("MC $it", accent) }
                party.loader?.takeIf { it.isNotBlank() }?.let { SocialTag(it, AerixSurface.textSecondary) }
                party.instanceName?.takeIf { it.isNotBlank() }?.let { SocialTag(it, AerixSurface.textSecondary) }
                party.serverAddress?.takeIf { it.isNotBlank() }?.let { SocialTag("Server: $it", AerixSurface.success) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialButton("Join Instance", filled = true, onClick = onJoinInstance)
                SocialButton("Party Chat", filled = false, onClick = onOpenChat)
                SocialButton("Manage / Invite", filled = false, onClick = onOpenDetails)
            }
        }
    }
}

@Composable
fun CreatePartyDialog(
    defaultPublic: Boolean,
    onDismiss: () -> Unit,
    onCreated: (SocialParty) -> Unit
) {
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()
    val instances = PojavInstanceBridge.instances
    val selectedInst = PojavInstanceBridge.selected ?: instances.firstOrNull()

    var title by remember { mutableStateOf("") }
    var pickedInstanceId by remember { mutableStateOf(selectedInst?.id) }
    var instanceName by remember { mutableStateOf(selectedInst?.name ?: "") }
    var versionId by remember { mutableStateOf(selectedInst?.versionId ?: "") }
    var mcVersion by remember {
        mutableStateOf(SocialBackend.extractMcVersion(selectedInst?.versionId) ?: "1.21.4")
    }
    var loader by remember {
        mutableStateOf(SocialBackend.detectLoader(selectedInst?.versionId))
    }
    var modpackSource by remember { mutableStateOf<String?>(null) }
    var modpackId by remember { mutableStateOf<String?>(null) }
    var modpackIconUrl by remember { mutableStateOf<String?>(null) }
    var serverAddress by remember { mutableStateOf("") }
    var maxMembers by remember { mutableIntStateOf(4) }
    var expiryMinutes by remember { mutableIntStateOf(120) } // 2-hour default
    var isPublic by remember { mutableStateOf(defaultPublic) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(pickedInstanceId) {
        val id = pickedInstanceId ?: return@LaunchedEffect
        val full = withContext(Dispatchers.IO) {
            runCatching { Instances.loadInstance(id) }.getOrNull()
        }
        if (full != null) {
            modpackSource = full.modpackSource
            modpackId = full.modpackProjectId
            modpackIconUrl = full.modpackIconUrl
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        SocialPanel(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (defaultPublic) "Post Looking-for-Group Party" else "Create a Party",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AerixSurface.textPrimary
                    )
                    SocialIconBubble(R.drawable.aerix_ic_close, "Close", onDismiss)
                }

                SocialGlassInput(
                    value = title,
                    onValueChange = { title = it },
                    label = "Party title (e.g. Evening Co-op Survival)"
                )

                if (instances.isNotEmpty()) {
                    Text("Pick from your instances", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AerixSurface.textMuted)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        instances.forEach { inst ->
                            val active = pickedInstanceId == inst.id
                            FilterChipPill(
                                label = inst.name,
                                selected = active,
                                onClick = {
                                    pickedInstanceId = inst.id
                                    instanceName = inst.name
                                    versionId = inst.versionId
                                    SocialBackend.extractMcVersion(inst.versionId)?.let { mcVersion = it }
                                    loader = SocialBackend.detectLoader(inst.versionId)
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SocialGlassInput(
                        value = instanceName,
                        onValueChange = { instanceName = it },
                        label = "Instance / Pack name",
                        modifier = Modifier.weight(1f)
                    )
                    SocialGlassInput(
                        value = mcVersion,
                        onValueChange = { mcVersion = it },
                        label = "MC version",
                        modifier = Modifier.weight(0.75f)
                    )
                }

                Text("Mod loader", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AerixSurface.textMuted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Vanilla", "Fabric", "NeoForge", "Forge", "Quilt").forEach { option ->
                        FilterChipPill(
                            label = option,
                            selected = loader.equals(option, ignoreCase = true),
                            onClick = { loader = option }
                        )
                    }
                }

                SocialGlassInput(
                    value = serverAddress,
                    onValueChange = { serverAddress = it },
                    label = "Server address (optional, auto-joins on launch)"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Member cap", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AerixSurface.textMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(2, 4, 8, 12).forEach { cap ->
                                FilterChipPill("$cap", maxMembers == cap) { maxMembers = cap }
                            }
                        }
                    }
                    Column {
                        Text("Auto-expire", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AerixSurface.textMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(60 to "1h", 120 to "2h", 240 to "4h", 480 to "8h").forEach { (mins, lbl) ->
                                FilterChipPill(lbl, expiryMinutes == mins) { expiryMinutes = mins }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AerixRadii.controlSmall))
                        .clickable { isPublic = !isPublic }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        colors = CheckboxDefaults.colors(checkedColor = accent)
                    )
                    Column {
                        Text("List on Looking-for-Group (LFG) board", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AerixSurface.textPrimary)
                        Text("Anyone filtering for $mcVersion · $loader can join up to the cap", fontSize = 10.5.sp, color = AerixSurface.textMuted)
                    }
                }

                SocialBackend.lastError?.let { err ->
                    Text(err, fontSize = 11.sp, color = AerixSurface.danger)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SocialButton("Cancel", filled = false, onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    SocialButton(
                        label = if (saving) "Creating..." else "Create Party",
                        filled = true,
                        enabled = !saving && title.isNotBlank()
                    ) {
                        saving = true
                        SocialBackend.clearError()
                        scope.launch {
                            val created = SocialBackend.createParty(
                                title = title,
                                instanceName = instanceName.ifBlank { null },
                                versionId = versionId.ifBlank { mcVersion },
                                mcVersion = mcVersion.ifBlank { null },
                                loader = loader,
                                modpackSource = modpackSource,
                                modpackId = modpackId,
                                modpackIconUrl = modpackIconUrl,
                                serverAddress = serverAddress.ifBlank { null },
                                maxMembers = maxMembers,
                                expiryMinutes = expiryMinutes,
                                isPublic = isPublic
                            )
                            saving = false
                            if (created != null) onCreated(created)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PartyDetailDialog(
    activity: Activity,
    party: SocialParty,
    friends: List<SocialFriend>,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onNavigateLauncher: ((LauncherSection) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val accent = AerixThemeManager.currentAccent()
    val myId = SocialBackend.myUserId()
    val amHost = party.hostId == myId
    var invitedIds by remember(party.id) { mutableStateOf<Set<String>>(emptySet()) }

    val memberIdSet = party.members.map { it.userId }.toSet()
    val invitableFriends = friends.filter { it.id !in memberIdSet }

    Dialog(onDismissRequest = onDismiss) {
        SocialPanel(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(party.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = AerixSurface.textPrimary)
                        Text("${party.summaryTag()} · ${party.remainingText()}", fontSize = 11.5.sp, color = AerixSurface.textMuted)
                    }
                    SocialIconBubble(R.drawable.aerix_ic_close, "Close", onDismiss)
                }

                // Launch host's setup CTA
                Surface(
                    shape = RoundedCornerShape(AerixRadii.control),
                    color = accent.copy(alpha = 0.14f),
                    border = BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.48f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_rocket_launch_filled),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Host's instance ready",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AerixSurface.textPrimary
                            )
                            Text(
                                buildString {
                                    append(party.mcVersion ?: "Minecraft")
                                    party.loader?.let { append(" · $it") }
                                    party.modpackId?.let { append(" · Modpack") }
                                    party.serverAddress?.let { append(" · $it") }
                                },
                                fontSize = 11.sp,
                                color = AerixSurface.textSecondary
                            )
                        }
                        SocialButton("Join Instance", filled = true) {
                            onDismiss()
                            PojavLaunchBridge.joinSocialSession(
                                activity = activity,
                                mcVersion = party.mcVersion,
                                loaderName = party.loader,
                                instanceHint = party.instanceName,
                                modpackSource = party.modpackSource,
                                modpackId = party.modpackId,
                                modpackIconUrl = party.modpackIconUrl,
                                serverAddress = party.serverAddress,
                                onNavigate = onNavigateLauncher
                            )
                        }
                    }
                }

                // Members list (with host transfer & kick controls for the host)
                Text(
                    "Members (${party.memberCount}/${party.maxMembers})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AerixSurface.textPrimary
                )
                party.members.forEach { member ->
                    Surface(
                        shape = RoundedCornerShape(AerixRadii.controlSmall),
                        color = AerixSurface.panelTrack,
                        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SocialRemoteImage(
                                url = member.avatarUrl,
                                fallbackIcon = R.drawable.aerix_ic_person_outlined,
                                modifier = Modifier.size(30.dp),
                                shape = CircleShape
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(member.username, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AerixSurface.textPrimary)
                                    if (member.isHost) SocialTag("Host", accent)
                                }
                                member.statusLine?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, fontSize = 10.5.sp, color = AerixSurface.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (amHost && member.userId != myId) {
                                FilterChipPill("Make Host", selected = false) {
                                    scope.launch {
                                        if (SocialBackend.transferPartyHost(party.id, member.userId)) {
                                            Toast.makeText(activity, "Transferred host to ${member.username}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                FilterChipPill("Kick", selected = false) {
                                    scope.launch {
                                        SocialBackend.kickPartyMember(party.id, member.userId)
                                    }
                                }
                            }
                        }
                    }
                }

                // Invite from friend list
                if (party.memberCount < party.maxMembers && invitableFriends.isNotEmpty()) {
                    Text(
                        "Invite friends (lands in their Activity)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AerixSurface.textPrimary
                    )
                    invitableFriends.take(8).forEach { friend ->
                        val alreadySent = friend.id in invitedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AerixRadii.controlSmall))
                                .background(AerixSurface.panelTrack)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(friend.effectiveName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AerixSurface.textPrimary)
                                Text(friend.presenceSummary(), fontSize = 10.5.sp, color = AerixSurface.textMuted, maxLines = 1)
                            }
                            SocialButton(
                                label = if (alreadySent) "Invited ✓" else "Invite",
                                filled = !alreadySent,
                                enabled = !alreadySent
                            ) {
                                scope.launch {
                                    if (SocialBackend.inviteFriendToParty(party, friend.id)) {
                                        invitedIds = invitedIds + friend.id
                                    } else {
                                        Toast.makeText(activity, SocialBackend.lastError ?: "Could not send invite", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SocialButton(
                        label = if (amHost && party.memberCount <= 1) "Close Party" else "Leave Party",
                        filled = false
                    ) {
                        scope.launch {
                            SocialBackend.leaveParty(party)
                            onDismiss()
                        }
                    }
                    SocialButton("Open Party Chat", filled = true, onClick = onOpenChat)
                }
            }
        }
    }
}

@Composable
fun SocialLfgBoardSection(
    activity: Activity,
    onOpenPartyChat: (SocialParty) -> Unit,
    onNavigateLauncher: ((LauncherSection) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var versionFilter by remember { mutableStateOf("") }
    var loaderFilter by remember { mutableStateOf("All") }
    var loading by remember { mutableStateOf(false) }
    var showCreateLfg by remember { mutableStateOf(false) }
    val lfgList = SocialBackend.lfgParties

    suspend fun reload() {
        loading = true
        SocialBackend.loadLfgBoard(
            mcVersionFilter = versionFilter.takeIf { it.isNotBlank() },
            loaderFilter = loaderFilter.takeIf { it != "All" }
        )
        loading = false
    }

    LaunchedEffect(versionFilter, loaderFilter) {
        reload()
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Looking for Group (LFG)", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AerixSurface.textPrimary)
                Text("Public co-op sessions filtered by Minecraft version and mod loader.", fontSize = 11.5.sp, color = AerixSurface.textMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialIconBubble(R.drawable.aerix_ic_refresh, "Refresh LFG") { scope.launch { reload() } }
                SocialButton("+ Post LFG", filled = true) { showCreateLfg = true }
            }
        }

        // Filter bar: Version chips + custom version input + Loader chips
        SocialPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Version:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AerixSurface.textMuted)
                    listOf("" to "Any", "1.21.4" to "1.21.4", "1.21.1" to "1.21.1", "1.20.1" to "1.20.1", "1.19.2" to "1.19.2", "1.8.9" to "1.8.9").forEach { (v, lbl) ->
                        FilterChipPill(lbl, versionFilter == v) { versionFilter = v }
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Loader:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AerixSurface.textMuted)
                    listOf("All", "Vanilla", "Fabric", "NeoForge", "Forge", "Quilt").forEach { l ->
                        FilterChipPill(l, loaderFilter == l) { loaderFilter = l }
                    }
                }
            }
        }

        if (loading && lfgList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Loading LFG board...", fontSize = 12.sp, color = AerixSurface.textMuted)
            }
        } else if (lfgList.isEmpty()) {
            SocialPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No open LFG groups match this filter", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                    Text("Post your own group so players on the same version & loader can hop right in.", fontSize = 11.5.sp, color = AerixSurface.textMuted)
                    SocialButton("Post LFG Group", filled = true) { showCreateLfg = true }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(lfgList, key = { it.id }) { party ->
                    val alreadyIn = SocialBackend.parties.any { it.id == party.id }
                    val isFull = party.memberCount >= party.maxMembers && !alreadyIn
                    SocialPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SocialRemoteImage(
                                    url = party.hostAvatar,
                                    fallbackIcon = R.drawable.aerix_ic_public,
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(party.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                                    Text("Host: ${party.hostName} · ${party.remainingText()}", fontSize = 11.sp, color = AerixSurface.textMuted)
                                }
                                SocialTag("${party.memberCount}/${party.maxMembers}", AerixThemeManager.currentAccent())
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                party.mcVersion?.let { SocialTag("MC $it", AerixThemeManager.currentAccent()) }
                                party.loader?.let { SocialTag(it, AerixSurface.textSecondary) }
                                party.instanceName?.let { SocialTag(it, AerixSurface.textSecondary) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SocialButton(
                                    label = when {
                                        alreadyIn -> "Launch & Play"
                                        isFull -> "Party Full"
                                        else -> "Join & Launch"
                                    },
                                    filled = true,
                                    enabled = !isFull
                                ) {
                                    scope.launch {
                                        val joined = if (alreadyIn) true else SocialBackend.joinPublicParty(party)
                                        if (joined) {
                                            PojavLaunchBridge.joinSocialSession(
                                                activity = activity,
                                                mcVersion = party.mcVersion,
                                                loaderName = party.loader,
                                                instanceHint = party.instanceName,
                                                modpackSource = party.modpackSource,
                                                modpackId = party.modpackId,
                                                modpackIconUrl = party.modpackIconUrl,
                                                serverAddress = party.serverAddress,
                                                onNavigate = onNavigateLauncher
                                            )
                                        } else {
                                            Toast.makeText(activity, SocialBackend.lastError ?: "Could not join", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                if (alreadyIn) {
                                    SocialButton("Party Chat", filled = false) { onOpenPartyChat(party) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateLfg) {
        CreatePartyDialog(
            defaultPublic = true,
            onDismiss = { showCreateLfg = false },
            onCreated = { created ->
                showCreateLfg = false
                onOpenPartyChat(created)
            }
        )
    }
}

@Composable
fun SocialServerHubSection(
    activity: Activity,
    onNavigateLauncher: ((LauncherSection) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val servers = SocialBackend.sharedServers
    val myId = SocialBackend.myUserId()
    var showShareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SocialBackend.loadSharedServers()
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Friends' Server Hub", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AerixSurface.textPrimary)
                Text("Servers shared by you and your friends — one tap launches straight in.", fontSize = 11.5.sp, color = AerixSurface.textMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialIconBubble(R.drawable.aerix_ic_refresh, "Refresh servers") {
                    scope.launch { SocialBackend.loadSharedServers() }
                }
                SocialButton("+ Share Server", filled = true) { showShareDialog = true }
            }
        }

        if (servers.isEmpty()) {
            SocialPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_sports_esports_outlined),
                        contentDescription = null,
                        tint = AerixThemeManager.currentAccent(),
                        modifier = Modifier.size(30.dp)
                    )
                    Text("No shared servers yet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                    Text("Share your favourite SMP or minigame IP so friends can hop on in one tap.", fontSize = 12.sp, color = AerixSurface.textMuted)
                    SocialButton("Share a Server", filled = true) { showShareDialog = true }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(servers, key = { "${it.ownerId}_${it.id}" }) { srv ->
                    SocialPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SocialRemoteImage(
                                url = srv.ownerAvatar,
                                fallbackIcon = R.drawable.aerix_ic_public,
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(srv.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                                    srv.mcVersion?.takeIf { it.isNotBlank() }?.let { SocialTag(it, AerixThemeManager.currentAccent()) }
                                    srv.loader?.takeIf { it.isNotBlank() }?.let { SocialTag(it, AerixSurface.textSecondary) }
                                }
                                Text(
                                    "${srv.address} · shared by ${srv.ownerName}",
                                    fontSize = 11.5.sp,
                                    color = AerixSurface.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                srv.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, fontSize = 11.sp, color = AerixSurface.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (srv.ownerId == myId) {
                                SocialIconBubble(R.drawable.aerix_ic_delete_outlined, "Remove server") {
                                    scope.launch { SocialBackend.deleteSharedServer(srv) }
                                }
                            }
                            SocialButton("Join", filled = true) {
                                if (srv.ownerId != myId) {
                                    SocialBackend.recordRecentPlayer(
                                        otherId = srv.ownerId,
                                        name = srv.ownerName,
                                        mcUsername = srv.ownerMcUsername,
                                        avatarUrl = srv.ownerAvatar,
                                        contextLabel = "Joined server ${srv.name}"
                                    )
                                }
                                PojavLaunchBridge.joinSocialSession(
                                    activity = activity,
                                    mcVersion = srv.mcVersion,
                                    loaderName = srv.loader,
                                    serverAddress = srv.address,
                                    onNavigate = onNavigateLauncher
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShareDialog) {
        ShareServerDialog(
            onDismiss = { showShareDialog = false },
            onShared = { showShareDialog = false }
        )
    }
}

@Composable
private fun ShareServerDialog(
    onDismiss: () -> Unit,
    onShared: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val selectedInst = PojavInstanceBridge.selected
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var mcVersion by remember { mutableStateOf(SocialBackend.extractMcVersion(selectedInst?.versionId) ?: "1.21.4") }
    var loader by remember { mutableStateOf(SocialBackend.detectLoader(selectedInst?.versionId)) }
    var description by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        SocialPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Share a Server with Friends", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = AerixSurface.textPrimary)
                    SocialIconBubble(R.drawable.aerix_ic_close, "Close", onDismiss)
                }
                SocialGlassInput(name, { name = it }, "Server name (e.g. Cozy SMP)")
                SocialGlassInput(address, { address = it }, "Server address (e.g. play.cozysmp.net:25565)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SocialGlassInput(mcVersion, { mcVersion = it }, "MC version", modifier = Modifier.weight(1f))
                    SocialGlassInput(loader, { loader = it }, "Loader", modifier = Modifier.weight(1f))
                }
                SocialGlassInput(description, { description = it }, "Short note (optional)", singleLine = false)
                SocialBackend.lastError?.let { Text(it, fontSize = 11.sp, color = AerixSurface.danger) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SocialButton("Cancel", filled = false, onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    SocialButton(
                        label = if (saving) "Sharing..." else "Share",
                        filled = true,
                        enabled = !saving && name.isNotBlank() && address.isNotBlank()
                    ) {
                        saving = true
                        SocialBackend.clearError()
                        scope.launch {
                            val ok = SocialBackend.shareServer(name, address, mcVersion, loader, description)
                            saving = false
                            if (ok) onShared()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    val shape = CircleShape
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.24f) else AerixSurface.panelTrack)
            .border(
                BorderStroke(AerixSpacing.hairline, if (selected) accent else AerixSurface.borderSoft),
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else AerixSurface.textSecondary,
            maxLines = 1
        )
    }
}

/**
 * Floating launcher toast: `"Marie started 1.21.4 · Join?"` with ThemeMotion glow and one-tap join.
 */
@Composable
fun SocialJoinToastBanner(
    toast: SocialJoinToast,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = AerixThemeManager.currentAccent()
    val motion = ThemeMotion.rememberEnabled()
    val liveAccent = ThemeMotion.rememberPulse(accent, active = true, enabled = motion)
    val shape = RoundedCornerShape(AerixRadii.cardLarge)

    Surface(
        shape = shape,
        color = AerixSurface.panelRaised,
        border = BorderStroke(1.dp, liveAccent),
        modifier = modifier
            .widthIn(max = 420.dp)
            .accentGlow(liveAccent, active = true, enabled = motion)
            .liquidGlass(
                shape = shape,
                tint = AerixSurface.panelGlassTint,
                strength = 0.95f,
                elevation = AerixMetrics.glassFloatingElevation
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_sports_esports_outlined),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${toast.friendName} started ${toast.mcVersion} · Join?",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AerixSurface.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                toast.statusLine?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 10.5.sp, color = AerixSurface.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SocialButton("Join", filled = true, onClick = onJoin)
            SocialIconBubble(R.drawable.aerix_ic_close, "Dismiss", onDismiss)
        }
    }
}
