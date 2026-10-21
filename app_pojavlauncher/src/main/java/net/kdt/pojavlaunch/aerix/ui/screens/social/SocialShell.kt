/*
 * Aerix Launcher — Social shell. Tapping the Play-dashboard Social widget replaces the
 * whole launcher (rail, wallpaper, accent, page animations) with this shell: its own
 * background, rail, top bar, back stack and palette. Exit returns to the launcher
 * exactly as it was left.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.screens.social

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R

/**
 * Whether the Social shell covers the launcher. Kept outside the composition so it
 * survives the Compose view being recreated when a Pojav fragment is pushed and popped,
 * the same reason AerixNav lives outside.
 */
object SocialShellState {
    var isOpen by mutableStateOf(false)
        private set

    // DECISION: the in-game overlay is the one other entry point next to the Play
    // dashboard widget (spec section 11 lists both); inside the launcher the widget
    // is the only way in — the launcher rail has no Social entry.
    fun enter() {
        isOpen = true
    }

    fun exit() {
        isOpen = false
    }
}

/** Destinations of the Social rail. Exit is an action, not a destination. */
enum class SocialRailDestination { FRIENDS, CHATS, PARTY, PROFILE }

/**
 * Full-screen Social host. The background is a fixed violet gradient: no launcher
 * wallpaper, no glass tint, no festival effects.
 */
@Composable
fun SocialShell(onExit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        SocialPalette.backgroundDeep,
                        SocialPalette.background,
                        SocialPalette.accentDeep.copy(alpha = 0.22f)
                    )
                )
            )
    ) {
        SocialFlow(onExit = onExit)
    }
}

/**
 * The Social rail: Friends, Chats, Party, Profile, and Exit pinned to the bottom.
 * Shown on every post-setup Social screen; the sign-in and appearance steps run
 * without it.
 */
@Composable
fun SocialRail(
    destination: SocialRailDestination?,
    onFriends: () -> Unit,
    onChats: () -> Unit,
    onParty: () -> Unit,
    onProfile: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(SocialDimens.railWidth)
            .fillMaxHeight()
            .padding(vertical = SocialDimens.pageVertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SocialDimens.gapSmall)
    ) {
        SocialRailItem(
            label = "Friends",
            iconRes = R.drawable.aerix_ic_group_outlined,
            selected = destination == SocialRailDestination.FRIENDS,
            onClick = onFriends
        )
        // DECISION: no dedicated chat/party vectors exist in the drawable set; chat uses
        // the text-snippet glyph and party the game asset glyph until real icons land.
        SocialRailItem(
            label = "Chats",
            iconRes = R.drawable.aerix_ic_text_snippet_filled,
            selected = destination == SocialRailDestination.CHATS,
            onClick = onChats
        )
        SocialRailItem(
            label = "Party",
            iconRes = R.drawable.aerix_ic_videogame_asset_outlined,
            selected = destination == SocialRailDestination.PARTY,
            onClick = onParty
        )
        SocialRailItem(
            label = "Profile",
            iconRes = R.drawable.aerix_ic_person_outlined,
            selected = destination == SocialRailDestination.PROFILE,
            onClick = onProfile
        )
        Spacer(modifier = Modifier.weight(1f))
        SocialRailItem(
            label = "Exit",
            iconRes = R.drawable.aerix_ic_arrow_back,
            selected = false,
            onClick = onExit
        )
    }
}

@Composable
private fun SocialRailItem(
    label: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val tint = if (selected) SocialPalette.accentBright else SocialPalette.textSecondary
    Column(
        modifier = Modifier
            .size(width = 64.dp, height = 60.dp)
            .clip(shape)
            .background(if (selected) SocialPalette.accentSoft else Color.Transparent, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = tint,
            fontSize = SocialType.caption,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
