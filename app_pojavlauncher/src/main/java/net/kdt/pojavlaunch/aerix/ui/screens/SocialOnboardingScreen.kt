/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The first-run profile form: it takes over the social panel after the very first sign-in, and it
 * is the same form used later by "Edit profile".
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.social.SocialCountries
import net.kdt.pojavlaunch.aerix.social.SocialPresenceCodec
import net.kdt.pojavlaunch.aerix.social.SocialProfile
import net.kdt.pojavlaunch.aerix.social.SocialRole
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface

/** Fields the form owns while it is open, so a half-filled profile is never written by accident. */
private class ProfileDraft(profile: SocialProfile?) {
    var username by mutableStateOf(profile?.username.orEmpty())
    var name by mutableStateOf(SocialRole.stripRoleTags(profile?.name).orEmpty())
    var country by mutableStateOf(profile?.country.orEmpty())
    var bio by mutableStateOf(profile?.bio.orEmpty())
    var avatarUrl by mutableStateOf(profile?.avatarUrl)
    var bannerUrl by mutableStateOf(profile?.bannerUrl)
    var skinUrl by mutableStateOf(profile?.skinUrl)
    var capeUrl by mutableStateOf(profile?.capeUrl)
    var statusText by mutableStateOf(SocialPresenceCodec.stripModePrefix(profile?.statusText).orEmpty())
    var lookingToPlay by mutableStateOf(profile?.lookingToPlay ?: false)
}

/**
 * The three-step profile form.
 *
 * @param editing true when opened from "Edit profile": the username is locked unless a month has
 *                passed, and the heading says so instead of "Welcome".
 */
@Composable
fun SocialProfileForm(
    activity: FragmentActivity,
    profile: SocialProfile?,
    editing: Boolean,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val draft = remember(profile?.id) { ProfileDraft(profile) }
    var step by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf<String?>(null) }
    val usernameLocked = editing && (profile?.daysUntilUsernameChange() ?: 0L) > 0L

    val steps = listOf("You", "About", "Look")

    LaunchedEffect(Unit) {
        SocialBackend.clearError()
    }

    fun finish() {
        if (draft.username.length < 3) {
            val msg = "Pick a username first"
            SocialBackend.reportError(msg)
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            step = 0
            return
        }
        busy = true
        SocialBackend.clearError()
        scope.launch {
            // The username is the only field the database has an opinion about.
            if (!usernameLocked || draft.username.trim() != profile?.username) {
                val problem = SocialBackend.claimUsername(draft.username)
                if (problem != null) {
                    busy = false
                    step = 0
                    SocialBackend.reportError(problem)
                    Toast.makeText(activity, problem, Toast.LENGTH_LONG).show()
                    return@launch
                }
            }
            val saved = SocialBackend.saveProfile(
                name = draft.name,
                country = draft.country,
                bio = draft.bio,
                avatarUrl = draft.avatarUrl,
                bannerUrl = draft.bannerUrl,
                skinUrl = draft.skinUrl,
                capeUrl = draft.capeUrl,
                onboarded = true,
                statusText = draft.statusText,
                lookingToPlay = draft.lookingToPlay
            )
            busy = false
            if (saved) {
                Toast.makeText(activity, "Profile saved", Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(
                    activity,
                    SocialBackend.lastError ?: "Could not save the profile",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // The progress dots double as the step buttons' turnstile: Next advances, Finish saves.
    fun goNext() {
        if (step < steps.lastIndex) step++ else finish()
    }

    fun reportPickerError(message: String) {
        SocialBackend.reportError(message)
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    fun upload(kind: String, bytes: ByteArray, extension: String, apply: (String) -> Unit) {
        uploading = kind
        SocialBackend.clearError()
        scope.launch {
            val url = SocialBackend.uploadImage(kind, bytes, extension)
            uploading = null
            if (url != null) {
                cacheSocialImage(url, bytes)
                apply(url)
            } else {
                Toast.makeText(
                    activity,
                    SocialBackend.lastError ?: "Upload failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val pickAvatar = rememberPicturePicker(512, false, onError = ::reportPickerError) { bytes, ext ->
        upload("avatar", bytes, ext) { draft.avatarUrl = it }
    }
    val pickBanner = rememberPicturePicker(1280, false, onError = ::reportPickerError) { bytes, ext ->
        upload("banner", bytes, ext) { draft.bannerUrl = it }
    }
    val pickSkin = rememberPicturePicker(0, true, onError = ::reportPickerError) { bytes, ext ->
        upload("skin", bytes, ext) { draft.skinUrl = it }
    }
    val pickCape = rememberPicturePicker(0, true, onError = ::reportPickerError) { bytes, ext ->
        upload("cape", bytes, ext) { draft.capeUrl = it }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = AerixSpacing.lg, vertical = AerixSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (editing) "Edit profile" else "Set up your profile",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AerixSurface.textPrimary
                )
                Text(
                    if (editing) {
                        "Everything but the username can change whenever you like"
                    } else {
                        "This is what friends see when they tap your head"
                    },
                    fontSize = 11.sp,
                    color = AerixSurface.textSecondary
                )
            }
            SocialButton(if (editing) "Cancel" else "Later", filled = false, onClick = onClose)
        }
        Spacer(Modifier.height(AerixSpacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
            steps.forEachIndexed { index, label ->
                val active = index <= step
                val width by animateFloatAsState(
                    targetValue = if (index == step) 86f else 46f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f),
                    label = "stepWidth"
                )
                Box(
                    modifier = Modifier
                        .width(width.dp)
                        .height(22.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (active) {
                                net.kdt.pojavlaunch.aerix.theme.AerixThemeManager.currentAccent()
                                    .copy(alpha = if (index == step) 0.9f else 0.4f)
                            } else {
                                AerixSurface.panelTrack
                            },
                            shape = CircleShape
                        )
                        .clickable { if (!busy) step = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) AerixSurface.canvas else AerixSurface.textMuted
                    )
                }
            }
        }

        Spacer(Modifier.height(AerixSpacing.sm))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                val enter = slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 320f)) { full ->
                    if (forward) (full * 0.14f).toInt() else -(full * 0.14f).toInt()
                } + fadeIn(tween(190))
                val exit = slideOutHorizontally(tween(170)) { full ->
                    if (forward) -(full * 0.1f).toInt() else (full * 0.1f).toInt()
                } + fadeOut(tween(140))
                enter togetherWith exit
            },
            label = "profileStep",
            modifier = Modifier.weight(1f)
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                when (current) {
                    0 -> IdentityStep(draft, usernameLocked, profile)
                    1 -> AboutStep(draft)
                    else -> LookStep(
                        draft = draft,
                        uploading = uploading,
                        errorMessage = SocialBackend.lastError,
                        pickAvatar = { if (uploading == null) pickAvatar() },
                        pickBanner = { if (uploading == null) pickBanner() },
                        pickSkin = { if (uploading == null) pickSkin() },
                        pickCape = { if (uploading == null) pickCape() }
                    )
                }
            }
        }

        Spacer(Modifier.height(AerixSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            if (step > 0) {
                SocialButton("Back", filled = false) { step-- }
            }
            Spacer(Modifier.weight(1f))
            SocialButton(
                label = when {
                    busy -> "Saving…"
                    step < steps.lastIndex -> "Next"
                    else -> if (editing) "Save changes" else "Finish"
                },
                enabled = !busy && uploading == null,
                onClick = { goNext() }
            )
        }
        SocialBackend.lastError?.let { message ->
            Spacer(Modifier.height(4.dp))
            Text(message, fontSize = 11.sp, color = AerixSurface.danger)
        }
    }
}

@Composable
private fun IdentityStep(draft: ProfileDraft, usernameLocked: Boolean, profile: SocialProfile?) {
    SocialGlassInput(
        value = draft.username,
        onValueChange = { draft.username = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) },
        label = "Username",
        enabled = !usernameLocked,
        supporting = when {
            usernameLocked -> {
                val days = profile?.daysUntilUsernameChange() ?: 0L
                "Usernames change once a month. $days day${if (days == 1L) "" else "s"} to go."
            }
            else -> "Letters and numbers, at least one of each. 3-20 characters, _ allowed."
        }
    )
    SocialGlassInput(
        value = draft.name,
        onValueChange = { draft.name = (SocialRole.stripRoleTags(it) ?: it.trimStart()).take(40) },
        label = "Name",
        supporting = "Shown above your username"
    )
    val myRole = SocialBackend.myRole
    if (myRole != null) {
        val previewName = draft.name.trim().ifEmpty {
            draft.username.trim().ifEmpty {
                SocialBackend.mcUsername ?: "Your Name"
            }
        }
        SocialPanel(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Live tag:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AerixSurface.textMuted
                )
                SocialNameWithRole(
                    name = previewName,
                    role = myRole,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    SocialGlassInput(
        value = draft.country,
        onValueChange = { draft.country = it.take(40) },
        label = "Country"
    )
    val suggestions = SocialCountries.search(draft.country)
    if (suggestions.isNotEmpty() && draft.country !in suggestions) {
        Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
            suggestions.take(3).forEach { suggestion ->
                Box(modifier = Modifier.clickable { draft.country = suggestion }) {
                    SocialTag(suggestion)
                }
            }
        }
    }
    SocialGlassInput(
        value = draft.statusText,
        onValueChange = { draft.statusText = it.take(60) },
        label = "Status",
        supporting = "A line friends see under your name"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        SocialButton(
            label = if (draft.lookingToPlay) "Looking to play ✓" else "Looking to play",
            filled = draft.lookingToPlay
        ) { draft.lookingToPlay = !draft.lookingToPlay }
        Spacer(Modifier.width(AerixSpacing.sm))
        Text(
            "Puts you in the find-someone strip",
            fontSize = 10.sp,
            color = AerixSurface.textMuted
        )
    }
}

@Composable
private fun AboutStep(draft: ProfileDraft) {
    SocialGlassInput(
        value = draft.bio,
        onValueChange = { draft.bio = it.take(300) },
        label = "Bio",
        singleLine = false,
        supporting = "${draft.bio.length}/300 · shown on your profile, not clickable"
    )
    Spacer(Modifier.height(AerixSpacing.sm))
    Text(
        "Keep it to what you want a stranger to know: how long you have played, what you build, " +
            "the versions you like. Your country is optional.",
        fontSize = 11.sp,
        color = AerixSurface.textSecondary
    )
}

@Composable
private fun LookStep(
    draft: ProfileDraft,
    uploading: String?,
    errorMessage: String?,
    pickAvatar: () -> Unit,
    pickBanner: () -> Unit,
    pickSkin: () -> Unit,
    pickCape: () -> Unit
) {
    val busy = uploading != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        SocialPickBox(
            label = if (uploading == "avatar") "Uploading…" else "Logo",
            url = draft.avatarUrl,
            fallbackIcon = R.drawable.aerix_ic_person_outlined,
            shape = CircleShape,
            width = 76.dp,
            height = 76.dp,
            onClick = pickAvatar
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Logo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
            Text(
                "The round picture next to your name",
                fontSize = 10.sp,
                color = AerixSurface.textMuted
            )
            SocialButton(
                label = if (uploading == "avatar") "Uploading…" else "Choose logo",
                filled = false,
                enabled = !busy,
                onClick = pickAvatar
            )
        }
    }
    Spacer(Modifier.height(AerixSpacing.xs))
    SocialPickBox(
        label = if (uploading == "banner") "Uploading…" else "Background",
        url = draft.bannerUrl,
        fallbackIcon = R.drawable.aerix_ic_format_paint_outlined,
        shape = RoundedCornerShape(AerixRadii.cardLarge),
        width = null,
        height = 84.dp,
        onClick = pickBanner
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Background image", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
            Text("The wide picture behind your logo", fontSize = 10.sp, color = AerixSurface.textMuted)
            Spacer(Modifier.height(6.dp))
            SocialButton(
                label = if (uploading == "banner") "Uploading…" else "Choose background",
                filled = false,
                enabled = !busy,
                onClick = pickBanner
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SocialPickBox(
                label = if (uploading == "skin") "…" else "Skin",
                url = draft.skinUrl,
                fallbackIcon = R.drawable.aerix_ic_person_outlined,
                shape = RoundedCornerShape(AerixRadii.control),
                width = 64.dp,
                height = 64.dp,
                contentScale = ContentScale.Fit,
                onClick = pickSkin
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (uploading == "skin") "Uploading…" else "Choose skin",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = AerixSurface.accent,
                modifier = Modifier.clickable(enabled = !busy, onClick = pickSkin).padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SocialPickBox(
                label = if (uploading == "cape") "…" else "Cape",
                url = draft.capeUrl,
                fallbackIcon = R.drawable.aerix_ic_format_paint_outlined,
                shape = RoundedCornerShape(AerixRadii.control),
                width = 64.dp,
                height = 64.dp,
                contentScale = ContentScale.Fit,
                onClick = pickCape
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (uploading == "cape") "Uploading…" else "Choose cape",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = AerixSurface.accent,
                modifier = Modifier.clickable(enabled = !busy, onClick = pickCape).padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
    if (!errorMessage.isNullOrBlank()) {
        Spacer(Modifier.height(AerixSpacing.xs))
        SocialPanel(Modifier.fillMaxWidth()) {
            Text(
                text = errorMessage,
                fontSize = 11.sp,
                color = AerixSurface.danger,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
    Spacer(Modifier.height(AerixSpacing.sm))
    Text(
        "Skins and capes keep their pixels exactly as picked; logos and backgrounds are shrunk so " +
            "the free storage lasts.",
        fontSize = 10.sp,
        color = AerixSurface.textMuted
    )
}
