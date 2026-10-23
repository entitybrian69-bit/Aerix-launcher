/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The Contribution page: everyone behind the launcher, and a page for each of them.
 *
 * Layout follows the sketch supplied with the request: the owner sits centred on top, the two
 * co-owners sit side by side underneath, every logo is a circle with its card directly below it,
 * and tapping a logo opens that person's page with their logo in the top corner.
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

package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.GlassPanel
import net.kdt.pojavlaunch.aerix.components.MetaPill
import net.kdt.pojavlaunch.aerix.theme.AerixLayoutDefaults
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface

/** One member of the team, as the Contribution page shows them. */
data class ContributionMember(
    val id: String,
    val name: String,
    val role: String,
    val title: String,
    val roleColor: Color,
    @DrawableRes val logoRes: Int,
    /** What this person does, one line per item, for their own page. */
    val duties: List<String>
)

object AerixContribution {
    val ownerRed = Color(0xFFE2374E)
    val coOwnerBlue = Color(0xFF63C7FF)

    val members: List<ContributionMember> = listOf(
        ContributionMember(
            id = "entitybrian",
            name = "Entitybrian",
            role = "Owner",
            title = "Developer / Programmer",
            roleColor = ownerRed,
            logoRes = R.drawable.aerix_contrib_owner,
            duties = listOf(
                "Writes the launcher: the Aerix shell, its settings and its launcher screens.",
                "Programs the renderer backends and the plugin support that loads them.",
                "Builds, signs and ships every release.",
                "Answers the bug reports and fixes what they turn up."
            )
        ),
        ContributionMember(
            id = "zerx-mc",
            name = "Zerx-mc",
            role = "Co-owner",
            title = "Runtime and renderer manager",
            roleColor = coOwnerBlue,
            logoRes = R.drawable.aerix_contrib_zerx,
            duties = listOf(
                "Keeps the renderers working: Proton, Emesis and the bundled GL backends.",
                "Manages the Java runtimes the launcher installs and picks.",
                "Tests renderer and runtime builds on real devices before they ship.",
                "Tracks the upstream renderer projects this launcher bundles."
            )
        ),
        ContributionMember(
            id = "fire-playz",
            name = "Fire Playz",
            role = "Co-owner",
            title = "Social and online manager",
            roleColor = coOwnerBlue,
            logoRes = R.drawable.aerix_contrib_fire,
            duties = listOf(
                "Runs the launcher's social presence and announcements.",
                "Handles the community: questions, reports and feedback.",
                "Keeps the download pages and online listings up to date.",
                "Passes what the community asks for on to the developers."
            )
        )
    )

    fun byId(id: String?): ContributionMember? = members.firstOrNull { it.id == id }

    /** The member whose page is open; cleared whenever the Contribution page is shown. */
    var openedId by mutableStateOf<String?>(null)
}

/**
 * The team page. The owner is centred on top, the co-owners share the row underneath, and each
 * logo is a circle with its card directly below.
 */
@Composable
fun ContributionScreen(onOpen: (ContributionMember) -> Unit) {
    AerixPage(
        title = "Contribution",
        subtitle = "The people who build and run Aerix Launcher"
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            val owner = AerixContribution.members.firstOrNull()
            if (owner != null) {
                MemberColumn(
                    member = owner,
                    circleSize = 96.dp,
                    modifier = Modifier.fillMaxWidth(),
                    cardModifier = Modifier.widthIn(max = 220.dp),
                    onOpen = onOpen
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus),
                verticalAlignment = Alignment.Top
            ) {
                // Both co-owners share the row evenly, and their cards hold two title lines either
                // way, so the two halves line up exactly.
                AerixContribution.members.drop(1).forEach { member ->
                    MemberColumn(
                        member = member,
                        circleSize = 76.dp,
                        modifier = Modifier.weight(1f),
                        cardModifier = Modifier.fillMaxWidth(),
                        onOpen = onOpen
                    )
                }
            }
        }
    }
}

/** A circle logo with the member's card directly below it. */
@Composable
private fun MemberColumn(
    member: ContributionMember,
    circleSize: Dp,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    onOpen: (ContributionMember) -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        MemberLogo(
            member = member,
            size = circleSize,
            onClick = { onOpen(member) }
        )
        MemberCard(member = member, modifier = cardModifier)
    }
}

/** The logo, always a circle, ringed in the member's role colour. */
@Composable
private fun MemberLogo(member: ContributionMember, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(AerixSurface.panelRaised)
            .border(2.dp, member.roleColor.copy(alpha = 0.85f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(3.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(member.logoRes),
            contentDescription = "${member.name}, ${member.role}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    }
}

/** Role in the member's colour, then name, then what they do. */
@Composable
private fun MemberCard(member: ContributionMember, modifier: Modifier = Modifier) {
    GlassPanel(
        modifier = modifier,
        radius = AerixRadii.cardLarge,
        tint = member.roleColor,
        strength = 0.6f,
        contentPadding = AerixSpacing.smPlus
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
        ) {
            Text(
                text = member.role,
                color = member.roleColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = member.name,
                color = AerixSurface.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = member.title,
                color = AerixSurface.textSecondary,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                // Two lines either way, so the two co-owner cards are exactly the same height.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * One member's page: their logo in the top corner, and the rest of the screen for what they do.
 */
@Composable
fun ContributionDetailScreen(member: ContributionMember, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AerixLayoutDefaults.pagePadding()),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        // Back on the left, who this is in the middle, their logo in the top corner.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
        ) {
            AerixGhostButton(text = "Back", iconRes = null, onClick = onBack)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name,
                    color = AerixSurface.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${member.role} \u00B7 ${member.title}",
                    color = AerixSurface.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AerixSurface.panelRaised)
                    .border(2.dp, member.roleColor.copy(alpha = 0.85f), CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
            ) {
                Image(
                    painter = painterResource(member.logoRes),
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = AerixRadii.cardLarge,
                tint = member.roleColor,
                strength = 0.75f,
                contentPadding = AerixSpacing.lgPlus
            ) {
                MetaPill(text = member.role.uppercase())
                Text(
                    text = member.title,
                    color = AerixSurface.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "What they do in Aerix Launcher",
                    color = AerixSurface.textSecondary,
                    fontSize = 11.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                member.duties.forEach { duty ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AerixRadii.cardSmall))
                            .background(AerixSurface.panel.copy(alpha = 0.75f))
                            .border(
                                AerixSpacing.hairline,
                                AerixSurface.borderSoft,
                                RoundedCornerShape(AerixRadii.cardSmall)
                            )
                            .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.smPlus),
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = AerixSpacing.xsPlus)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(member.roleColor)
                        )
                        Text(
                            text = duty,
                            color = AerixSurface.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
