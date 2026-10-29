/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Socials: the page the launcher steps into when the player wants other players, not a world.
 *
 * Laid out after the reviewed design set (site-preview/social-designs-sheet.png): crimson and
 * white on a light sheet, the launcher's own rail on the left, one page per job - sign in,
 * friends, messages, activity, settings.
 *
 * The page is fully drawn; the data behind it arrives from the Aerix social backend
 * (supabase/schema.sql). Until a project URL is configured the page says so plainly instead of
 * inventing friends, which is what the old mock shell did.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell

// ---- palette of the social pages ---------------------------------------------------------

private val SocialInk = Color(0xFF14141A)
private val SocialMuted = Color(0xFF6F6E73)
private val SocialLine = Color(0xFFE7E3E0)
private val SocialSheet = Color(0xFFFCFBFA)
private val SocialCard = Color(0xFFFFFFFF)
private val SocialCrimson = Color(0xFFC81E3A)
private val SocialTint = Color(0xFFFBEAED)
private val SocialOnline = Color(0xFF3DA35D)
private val SocialOffline = Color(0xFFB9B6B3)

/** Which page of Socials is open. */
private enum class SocialPage(val label: String) {
    FRIENDS("Friends"),
    MESSAGES("Messages"),
    ACTIVITY("Activity"),
    SETTINGS("Settings")
}

/**
 * Whether an Aerix social backend answers.
 *
 * Read from one place so the rest of the page never has to guess: the Google sign-in flow, the
 * friend list and the conversations all appear the moment this reports true.
 */
object SocialsBackend {
    /** Filled in with the Supabase project once it exists (see supabase/schema.sql). */
    private const val PROJECT_URL = ""

    val isConfigured: Boolean
        get() = PROJECT_URL.startsWith("https://")
}

@Composable
fun SocialsScreen(activity: FragmentActivity) {
    var page by remember { mutableStateOf(SocialPage.FRIENDS) }
    val account = PojavAccountBridge.current
    val signedIn = SocialsBackend.isConfigured

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 2.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = SocialSheet,
        tonalElevation = 0.dp
    ) {
        if (!signedIn) {
            SocialsSignIn(activity)
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
                    SocialsHeader(
                        onlineCount = 0,
                        onAddFriend = {
                            Toast.makeText(activity, "Add a friend by username", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    SocialTabs(page) { page = it }
                    Spacer(Modifier.height(16.dp))
                    when (page) {
                        SocialPage.FRIENDS -> SocialsFriends(activity, account?.username)
                        SocialPage.MESSAGES -> SocialsMessages()
                        SocialPage.ACTIVITY -> SocialsActivity()
                        SocialPage.SETTINGS -> SocialsSettings(activity, account?.username)
                    }
                }
            }
        }
    }
}

// ---- sign in -----------------------------------------------------------------------------

@Composable
private fun SocialsSignIn(activity: FragmentActivity) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SocialCard,
            shadowElevation = 8.dp,
            modifier = Modifier.width(420.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = SocialCrimson) {
                    Icon(
                        painter = painterResource(R.drawable.ic_aerix_mark),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp).size(26.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Aerix Social",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SocialInk
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Sign in to play with friends",
                    fontSize = 14.sp,
                    color = SocialMuted
                )
                Spacer(Modifier.height(26.dp))
                SocialWideButton("Continue with Google", SocialCrimson, Color.White) {
                    Toast.makeText(activity, "The social backend is not connected yet", Toast.LENGTH_LONG).show()
                }
                Spacer(Modifier.height(10.dp))
                SocialWideButton("Continue with email", Color(0xFF1D1D22), Color.White) {
                    Toast.makeText(activity, "The social backend is not connected yet", Toast.LENGTH_LONG).show()
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "New here? Create an account",
                    fontSize = 13.sp,
                    color = SocialCrimson,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Signing in only shares the name and picture of the account you pick. " +
                        "Your Minecraft account stays in the launcher.",
                    fontSize = 11.sp,
                    color = SocialMuted.copy(alpha = 0.85f),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// ---- shared pieces -------------------------------------------------------------------------

@Composable
private fun SocialsHeader(onlineCount: Int, onAddFriend: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Socials", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = SocialInk)
        Spacer(Modifier.width(10.dp))
        Text(text = "$onlineCount online", fontSize = 14.sp, color = SocialMuted)
        Spacer(Modifier.weight(1f))
        Surface(shape = CircleShape, color = SocialCrimson, onClick = onAddFriend) {
            Text(
                text = "+  Add friend",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun SocialTabs(current: SocialPage, onSelect: (SocialPage) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SocialPage.entries.forEach { page ->
            val selected = page == current
            Surface(
                shape = CircleShape,
                color = if (selected) SocialTint else Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (selected) SocialCrimson.copy(alpha = 0.45f) else SocialLine
                ),
                onClick = { onSelect(page) }
            ) {
                Text(
                    text = page.label,
                    color = if (selected) SocialCrimson else SocialMuted,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun SocialWideButton(
    label: String,
    background: Color,
    content: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = background,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(text = label, color = content, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** One line of the empty state every social page starts on. */
@Composable
private fun SocialEmptyState(title: String, body: String, action: String?, onAction: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(20.dp), color = SocialCard, border = androidx.compose.foundation.BorderStroke(1.dp, SocialLine), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_people),
                contentDescription = null,
                tint = SocialCrimson.copy(alpha = 0.75f),
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SocialInk)
            Spacer(Modifier.height(6.dp))
            Text(text = body, fontSize = 13.sp, color = SocialMuted, lineHeight = 19.sp)
            if (action != null && onAction != null) {
                Spacer(Modifier.height(18.dp))
                Surface(shape = CircleShape, color = SocialCrimson, onClick = onAction) {
                    Text(
                        text = action,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

// ---- friends ------------------------------------------------------------------------------

@Composable
private fun SocialsFriends(activity: FragmentActivity, mcUsername: String?) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SocialTint,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "You appear as ${mcUsername ?: "no account yet"} to your friends",
                    color = SocialInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(text = "Change", color = SocialCrimson, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        SocialEmptyState(
            title = "No friends yet",
            body = "Search a Minecraft username and send a request. When they accept, you see what " +
                "world they are in and can invite them to yours.",
            action = "Find a player",
            onAction = { Toast.makeText(activity, "Friend search arrives with the backend", Toast.LENGTH_SHORT).show() }
        )
    }
}

// ---- messages -----------------------------------------------------------------------------

@Composable
private fun SocialsMessages() {
    Column(modifier = Modifier.fillMaxSize()) {
        SocialEmptyState(
            title = "No conversations",
            body = "Messages you exchange with friends live here, in the launcher and over the game.",
            action = null,
            onAction = null
        )
    }
}

// ---- activity -----------------------------------------------------------------------------

@Composable
private fun SocialsActivity() {
    Column(modifier = Modifier.fillMaxSize()) {
        SocialEmptyState(
            title = "Nothing yet",
            body = "Friend requests, party invitations and messages show up here.",
            action = null,
            onAction = null
        )
    }
}

// ---- settings -----------------------------------------------------------------------------

@Composable
private fun SocialsSettings(activity: FragmentActivity, mcUsername: String?) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SocialSettingCard(
            title = "Visibility",
            rows = listOf(
                "Show when I am in the launcher" to true,
                "Show what world I am playing" to true,
                "Show my Minecraft head" to true,
                "Allow party invites from anyone" to false
            )
        )
        Surface(shape = RoundedCornerShape(20.dp), color = SocialCard, border = androidx.compose.foundation.BorderStroke(1.dp, SocialLine), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Account", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SocialInk)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AerixImageWell(image = PojavAccountBridge.current?.face, fallback = R.drawable.aerix_ic_person_outlined, size = 34.dp, radius = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(text = mcUsername ?: "No account selected", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SocialInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = "Minecraft account shown to friends", fontSize = 12.sp, color = SocialMuted)
                    }
                }
                Text(
                    text = if (SocialsBackend.isConfigured) "Connected" else "Social backend not connected yet",
                    fontSize = 12.sp,
                    color = if (SocialsBackend.isConfigured) SocialOnline else SocialCrimson,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SocialSettingCard(title: String, rows: List<Pair<String, Boolean>>) {
    Surface(shape = RoundedCornerShape(20.dp), color = SocialCard, border = androidx.compose.foundation.BorderStroke(1.dp, SocialLine), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SocialInk)
            rows.forEach { (label, on) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text = label, fontSize = 14.sp, color = SocialInk, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 22.dp)
                            .clip(CircleShape)
                            .background(if (on) SocialCrimson else SocialOffline.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                        )
                    }
                }
            }
        }
    }
}
