/*
 * Aerix Launcher UI layer for PojavLauncher.
 * One person's profile: banner, logo, counts, bio, ranks - and the edit form for your own.
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.social.SocialProfile
import net.kdt.pojavlaunch.aerix.social.SocialRanks
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

private enum class ProfileView { CARD, EDIT, PEOPLE }

private enum class Relation(val title: String) {
    FOLLOWING("Following"),
    FOLLOWERS("Followers"),
    FRIENDS("Friends")
}

/**
 * The profile page, with the edit form and the followers/following lists as pages inside it.
 *
 * @param username whose profile; null means your own
 */
@Composable
fun SocialProfilePage(
    activity: FragmentActivity,
    username: String?,
    onBack: () -> Unit
) {
    var profile by remember(username) { mutableStateOf<SocialProfile?>(null) }
    var loading by remember(username) { mutableStateOf(true) }
    var view by remember(username) { mutableStateOf(ProfileView.CARD) }
    var relation by remember(username) { mutableStateOf(Relation.FOLLOWING) }
    var reportOpen by remember { mutableStateOf(false) }
    var reportText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val isMe = username.isNullOrBlank() || profile?.isMe == true

    suspend fun reload() {
        loading = true
        profile = if (username.isNullOrBlank()) {
            SocialBackend.loadMyProfile()
        } else {
            SocialBackend.loadProfileCard(username)
        }
        loading = false
    }

    LaunchedEffect(username) { reload() }

    AnimatedContent(
        targetState = view,
        transitionSpec = {
            val forward = targetState.ordinal >= initialState.ordinal
            val enter = slideInHorizontally(tween(230, easing = FastOutSlowInEasing)) { full ->
                if (forward) (full * 0.12f).toInt() else -(full * 0.12f).toInt()
            } + fadeIn(tween(180))
            val exit = slideOutHorizontally(tween(180)) { full ->
                if (forward) -(full * 0.08f).toInt() else (full * 0.08f).toInt()
            } + fadeOut(tween(140))
            enter togetherWith exit
        },
        label = "profileView"
    ) { current ->
        when (current) {
            ProfileView.CARD -> ProfileCard(
                activity = activity,
                profile = profile,
                loading = loading,
                isMe = isMe,
                onBack = onBack,
                onEdit = { view = ProfileView.EDIT },
                onRelation = { picked ->
                    relation = picked
                    view = ProfileView.PEOPLE
                },
                onFollow = {
                    val target = profile ?: return@ProfileCard
                    scope.launch {
                        if (SocialBackend.setFollow(target.id, !target.iFollow)) reload()
                    }
                },
                onAddFriend = {
                    val target = profile ?: return@ProfileCard
                    scope.launch {
                        if (SocialBackend.addFriendByName(target.username ?: return@launch)) {
                            Toast.makeText(activity, "Friend request sent", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(activity, SocialBackend.lastError ?: "Could not send", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onReport = { reportOpen = true }
            )

            ProfileView.EDIT -> SocialProfileForm(
                activity = activity,
                profile = profile,
                editing = true,
                onClose = { view = ProfileView.CARD },
                onSaved = {
                    view = ProfileView.CARD
                    scope.launch { reload() }
                }
            )

            ProfileView.PEOPLE -> SocialPeoplePage(
                activity = activity,
                relation = relation.name,
                title = relation.title,
                onBack = { view = ProfileView.CARD }
            )
        }
    }

    if (reportOpen) {
        AlertDialog(
            onDismissRequest = { reportOpen = false },
            title = { Text("Report ${profile?.displayName ?: "this player"}") },
            text = {
                Column {
                    Text(
                        "Tell us what happened. Only the launcher team can read this.",
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    SocialGlassInput(
                        value = reportText,
                        onValueChange = { reportText = it.take(500) },
                        label = "What happened?"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = profile
                    reportOpen = false
                    if (target != null && reportText.trim().length >= 3) {
                        scope.launch {
                            val sent = SocialBackend.report(target.id, reportText.trim())
                            Toast.makeText(
                                activity,
                                if (sent) "Report sent" else "Could not send the report",
                                Toast.LENGTH_SHORT
                            ).show()
                            reportText = ""
                        }
                    }
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { reportOpen = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    activity: FragmentActivity,
    profile: SocialProfile?,
    loading: Boolean,
    isMe: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRelation: (Relation) -> Unit,
    onFollow: () -> Unit,
    onAddFriend: () -> Unit,
    onReport: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AerixSpacing.lg)
    ) {
        SocialPageBar(
            title = profile?.displayName ?: if (loading) "Profile" else "Profile",
            onBack = onBack,
            trailing = {
                if (isMe) SocialButton("Edit profile", filled = false, onClick = onEdit)
            }
        )
        Spacer(Modifier.height(AerixSpacing.sm))

        if (profile == null) {
            SocialPanel(Modifier.fillMaxWidth()) {
                Text(
                    if (loading) "Loading the profile…" else "This profile could not be opened.",
                    fontSize = 12.sp,
                    color = AerixSurface.textSecondary,
                    modifier = Modifier.padding(18.dp)
                )
            }
            return@Column
        }

        // Banner, with the round logo sitting half over its bottom edge.
        Box(modifier = Modifier.fillMaxWidth()) {
            SocialRemoteImage(
                url = profile.bannerUrl,
                fallbackIcon = R.drawable.aerix_ic_format_paint_outlined,
                modifier = Modifier.fillMaxWidth().height(96.dp),
                shape = RoundedCornerShape(AerixRadii.panel),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = AerixSpacing.md, bottom = 0.dp)
                    .size(72.dp)
            ) {
                SocialRemoteImage(
                    url = profile.avatarUrl,
                    fallbackIcon = R.drawable.aerix_ic_person_outlined,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape
                )
            }
        }
        Spacer(Modifier.height(AerixSpacing.lgPlus))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AerixSurface.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    profile.handle.ifBlank { "@" + (profile.mcUsername ?: "player") },
                    fontSize = 11.sp,
                    color = AerixSurface.textMuted
                )
            }
            if (!profile.isMe) {
                SocialButton(if (profile.iFollow) "Following" else "Follow", filled = !profile.iFollow, onClick = onFollow)
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
            if (!profile.country.isNullOrBlank()) SocialTag(profile.country, AerixSurface.glassBlue)
            if (profile.lookingToPlay) SocialTag("Looking to play", AerixSurface.success)
            if (profile.online) SocialTag("Online", AerixSurface.success)
            else if (profile.away) SocialTag("Away", AerixSurface.warning)
        }
        if (!profile.statusText.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(profile.statusText, fontSize = 11.sp, color = AerixSurface.textSecondary)
        }

        Spacer(Modifier.height(AerixSpacing.sm))

        // The three counts, side by side, each one opening its list.
        SocialPanel(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CountCell(profile.following, Relation.FOLLOWING.title) { onRelation(Relation.FOLLOWING) }
                CountCell(profile.followers, Relation.FOLLOWERS.title) { onRelation(Relation.FOLLOWERS) }
                CountCell(profile.friends, Relation.FRIENDS.title) { onRelation(Relation.FRIENDS) }
            }
        }

        Spacer(Modifier.height(AerixSpacing.sm))

        SocialPanel(Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Bio", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    profile.bio?.takeIf { it.isNotBlank() }
                        ?: "No bio yet.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = if (profile.bio.isNullOrBlank()) AerixSurface.textMuted else AerixSurface.textSecondary
                )
            }
        }

        if (profile.ranks.isNotEmpty()) {
            Spacer(Modifier.height(AerixSpacing.sm))
            SocialPanel(Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Ranks", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                        profile.ranks.take(4).forEach { key ->
                            val rank = SocialRanks.of(key)
                            SocialTag(rank.label, Color(rank.tint))
                        }
                    }
                    if (profile.ranks.size > 4) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "+${profile.ranks.size - 4} more",
                            fontSize = 9.sp,
                            color = AerixSurface.textMuted
                        )
                    }
                }
            }
        }

        if (!profile.skinUrl.isNullOrBlank() || !profile.capeUrl.isNullOrBlank()) {
            Spacer(Modifier.height(AerixSpacing.sm))
            SocialPanel(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                ) {
                    if (!profile.skinUrl.isNullOrBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SocialRemoteImage(
                                url = profile.skinUrl,
                                fallbackIcon = R.drawable.aerix_ic_person_outlined,
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(AerixRadii.control),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                            Text("Skin", fontSize = 9.sp, color = AerixSurface.textMuted)
                        }
                    }
                    if (!profile.capeUrl.isNullOrBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SocialRemoteImage(
                                url = profile.capeUrl,
                                fallbackIcon = R.drawable.aerix_ic_format_paint_outlined,
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(AerixRadii.control),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                            Text("Cape", fontSize = 9.sp, color = AerixSurface.textMuted)
                        }
                    }
                }
            }
        }

        if (!profile.isMe) {
            Spacer(Modifier.height(AerixSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                SocialButton("Add friend", filled = false, onClick = onAddFriend)
                SocialButton("Report", filled = false, onClick = onReport)
            }
        }
        if (isMe) {
            Spacer(Modifier.height(AerixSpacing.sm))
            SocialPanel(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Invite code",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AerixSurface.textPrimary
                        )
                        Text(
                            SocialBackend.inviteCode() ?: "—",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AerixThemeManager.currentAccent()
                        )
                    }
                    SocialButton("Copy link", filled = false) {
                        val code = SocialBackend.inviteCode() ?: return@SocialButton
                        val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "Aerix invite",
                                "aerix-social://friend/$code"
                            )
                        )
                        Toast.makeText(activity, "Invite link copied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        Spacer(Modifier.height(AerixSpacing.sm))
        Text(
            if (isMe) "Your profile is only reachable by your exact username or this code." else
                "Found by username only — this profile is not listed anywhere.",
            fontSize = 9.sp,
            color = AerixSurface.textMuted
        )
    }
}

@Composable
private fun CountCell(count: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .clickable(onClick = onClick)
    ) {
        Text("$count", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = AerixSurface.textPrimary)
        Text(label, fontSize = 10.sp, color = AerixSurface.textMuted)
    }
}

@Composable
private fun SocialPeoplePage(
    activity: FragmentActivity,
    relation: String,
    title: String,
    onBack: () -> Unit
) {
    var people by remember(relation) { mutableStateOf<List<SocialProfile>>(emptyList()) }
    var loading by remember(relation) { mutableStateOf(true) }
    var openUser by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(relation) {
        loading = true
        people = when (relation) {
            "FOLLOWERS" -> SocialBackend.loadFollowers()
            "FOLLOWING" -> SocialBackend.loadFollowing()
            else -> SocialBackend.friends.map { friend ->
                SocialProfile(
                    id = friend.id,
                    username = friend.mcUsername,
                    name = friend.displayName,
                    mcUsername = friend.mcUsername,
                    lastSeenMs = friend.lastSeenMs
                )
            }
        }
        loading = false
    }

    if (openUser != null) {
        SocialProfilePage(activity = activity, username = openUser) { openUser = null }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(AerixSpacing.lg)) {
        SocialPageBar(title = title, onBack = onBack)
        Spacer(Modifier.height(AerixSpacing.sm))
        if (loading) {
            Text("Loading…", fontSize = 11.sp, color = AerixSurface.textMuted)
            return@Column
        }
        if (people.isEmpty()) {
            SocialPanel(Modifier.fillMaxWidth()) {
                Text(
                    "Nobody here yet.",
                    fontSize = 11.sp,
                    color = AerixSurface.textSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
            itemsIndexed(people) { _, person ->
                SocialPersonRow(
                    avatarUrl = person.avatarUrl,
                    title = person.displayName,
                    subtitle = person.handle.ifBlank { person.mcUsername },
                    badge = if (person.online) "Online" else null,
                    onClick = { person.username?.let { openUser = it } }
                )
            }
        }
    }
}
