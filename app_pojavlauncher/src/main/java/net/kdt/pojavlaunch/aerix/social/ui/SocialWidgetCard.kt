/*
 * Aerix Launcher Social layer.
 *
 * The Play dashboard entry point. It is a widget, not a navigation item: tapping it opens
 * the second launcher on top of the dashboard.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R

/**
 * Social widget for the Play dashboard.
 *
 * @param onOpen opens the Social shell; it never navigates inside the launcher rail.
 */
@Composable
fun SocialWidgetCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    signedIn: Boolean = false,
    friendsOnline: Int = 0,
    pendingRequests: Int = 0
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(SocialColors.panelRaised, SocialColors.backgroundBottom)
                )
            )
            .border(BorderStroke(1.dp, SocialColors.border), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SocialColors.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_social_ic_friends),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Aerix Social",
                color = SocialColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = when {
                    !signedIn -> "Sign in to add friends and join parties"
                    friendsOnline == 0 -> "Your friends list starts here"
                    else -> "$friendsOnline friends online · $pendingRequests pending"
                },
                color = SocialColors.textSecondary,
                fontSize = 12.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(1, 2, 3).forEach { index ->
                SocialAvatar(avatar = index, size = 26.dp)
            }
        }
        Spacer(Modifier.size(2.dp))
        Icon(
            painter = painterResource(R.drawable.aerix_social_ic_chevron),
            contentDescription = null,
            tint = SocialColors.accentBright,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Small dot used by the widget and the lists. */
@Composable
fun SocialStatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(8.dp).clip(CircleShape).background(color))
}
