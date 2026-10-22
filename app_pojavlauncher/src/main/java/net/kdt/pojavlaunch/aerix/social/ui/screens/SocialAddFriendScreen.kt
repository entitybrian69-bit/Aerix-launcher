/*
 * Aerix Launcher Social layer.
 *
 * Add a friend by Aerix username or by Gmail address. The result row carries the Add button
 * that sends the request; the other side has to accept before chat, calls and parties unlock.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.data.MatchField
import net.kdt.pojavlaunch.aerix.social.ui.SocialAvatar
import net.kdt.pojavlaunch.aerix.social.ui.SocialBody
import net.kdt.pojavlaunch.aerix.social.ui.SocialChip
import net.kdt.pojavlaunch.aerix.social.ui.SocialColors
import net.kdt.pojavlaunch.aerix.social.ui.SocialDimens
import net.kdt.pojavlaunch.aerix.social.ui.SocialGhostButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialPanel
import net.kdt.pojavlaunch.aerix.social.ui.SocialPrimaryButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialSectionTitle
import net.kdt.pojavlaunch.aerix.social.ui.SocialTextField
import net.kdt.pojavlaunch.aerix.social.ui.SocialTitle
import net.kdt.pojavlaunch.aerix.social.vm.SocialAddFriendViewModel

@Composable
fun SocialAddFriendScreen(
    viewModel: SocialAddFriendViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = SocialDimens.pagePaddingH)) {
        SocialPanel(modifier = Modifier.fillMaxWidth()) {
            SocialSectionTitle("Find someone")
            SocialBody(
                "Search by the Aerix username or by the Gmail address they signed up with. You can only chat, call and share files once they accept your request.",
                size = 12.sp,
                color = SocialColors.textMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialChip("Username", !state.byEmail, { viewModel.setByEmail(false) })
                SocialChip("Gmail", state.byEmail, { viewModel.setByEmail(true) })
            }
            SocialTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = if (state.byEmail) "name@gmail.com" else "Aerix username"
            )
            SocialPrimaryButton(
                text = if (state.searching) "Searching…" else "Search",
                onClick = viewModel::search,
                enabled = !state.searching,
                iconRes = R.drawable.aerix_ic_search,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.error != null) {
                SocialBody(state.error ?: "", size = 12.sp, color = SocialColors.danger)
            } else {
                SocialBody("At least 3 characters.", size = 11.sp, color = SocialColors.textMuted)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_social_ic_add_person),
                        contentDescription = null,
                        tint = SocialColors.textMuted,
                        modifier = Modifier.size(44.dp)
                    )
                    SocialTitle("Search to add a friend")
                    SocialBody("Results show the profile picture, username and how you matched.", size = 12.sp, color = SocialColors.textMuted)
                    SocialGhostButton("Back to Social", onClick = onBack)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.results, key = { it.user.id }) { result ->
                    val sent = result.requestSent || state.sentTo.contains(result.user.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SocialDimens.row)
                            .clip(RoundedCornerShape(SocialDimens.controlRadius))
                            .background(SocialColors.panelSoft)
                            .border(BorderStroke(1.dp, SocialColors.border), RoundedCornerShape(SocialDimens.controlRadius))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SocialAvatar(avatar = result.user.avatar, size = 40.dp, story = result.user.hasStory)
                        Column(modifier = Modifier.weight(1f)) {
                            SocialBody(result.user.name, size = 14.sp, color = SocialColors.textPrimary, weight = FontWeight.SemiBold)
                            SocialBody(
                                if (result.matchedBy == MatchField.EMAIL) result.user.email.orEmpty() else result.user.bio.ifBlank { "@${result.user.name.lowercase()}" },
                                size = 11.sp,
                                color = SocialColors.textMuted,
                                maxLines = 1
                            )
                        }
                        if (sent) {
                            SocialBody("Requested", size = 12.sp, color = SocialColors.success)
                        } else {
                            SocialPrimaryButton(
                                text = "Add",
                                onClick = { viewModel.send(result.user.id) },
                                iconRes = R.drawable.aerix_ic_add
                            )
                        }
                    }
                }
            }
        }
    }
}
