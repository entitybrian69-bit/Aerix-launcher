/*
 * Aerix Launcher — game-style home dashboard.
 * The layout follows the supplied Minecraft launcher reference: feature cards on the left,
 * a live player skin in the center, social shortcuts and a compact instance/play dock along the
 * bottom, with account/settings shortcuts and a friends card on the right. Actions remain wired to
 * Aerix's real account, instance, discovery, social and launch flows.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixInstance
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.guide.AerixGuideState
import net.kdt.pojavlaunch.aerix.guide.guideAnchor
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D
import net.kdt.pojavlaunch.aerix.theme.AerixFonts
import net.kdt.pojavlaunch.aerix.theme.ThemeMotion
import net.kdt.pojavlaunch.aerix.ui.LauncherSection

private val HomeRed = Color(0x4D8B0000)
private val HomeBlue = Color(0x4D004080)
private val HomeGreen = Color(0x4D006400)
private val HomeGold = Color(0xFFFFD54F)
private val ReadyGreen = Color(0xFF4CAF50)

@Composable
fun MinecraftHomeDashboard(
    instances: List<AerixInstance>,
    selected: AerixInstance?,
    taskCount: Int,
    taskProgressPercent: Int,
    taskProgressTitle: String,
    accent: Color,
    onPlay: () -> Unit,
    onCreate: () -> Unit,
    onLibrary: () -> Unit,
    onDiscover: () -> Unit,
    onAccounts: () -> Unit,
    onSocials: () -> Unit,
    onSettings: () -> Unit,
    onNavigate: (LauncherSection) -> Unit,
    onHelp: () -> Unit
) {
    val account = PojavAccountBridge.current
    val context = LocalContext.current
    val motionEnabled = ThemeMotion.rememberEnabled()
    val onlineFriends = net.kdt.pojavlaunch.aerix.social.SocialBackend.friends.count {
        it.online && !it.pendingIncoming && !it.pendingOutgoing
    }
    val friendsSubtitle = if (onlineFriends > 0) "$onlineFriends online now" else "Find your next world"
    val selectedFont = AerixFonts.selected()
    val fontFamily = remember(selectedFont.id) { selectedFont.family() }
    var entered by remember { mutableStateOf(false) }
    var progressDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        net.kdt.pojavlaunch.aerix.social.SocialBackend.attach(context.applicationContext)
        if (net.kdt.pojavlaunch.aerix.social.SocialBackend.signedIn) {
            net.kdt.pojavlaunch.aerix.social.SocialBackend.refreshFriends()
            net.kdt.pojavlaunch.aerix.social.SocialBackend.refreshParties()
            net.kdt.pojavlaunch.aerix.social.SocialBackend.refreshPartyInvites()
            net.kdt.pojavlaunch.aerix.social.SocialBackend.unreadCount()
        }
    }
    LaunchedEffect(Unit) { entered = true }
    LaunchedEffect(taskCount) {
        if (taskCount == 0) progressDismissed = false
    }
    val entryProgress by animateFloatAsState(
        targetValue = if (entered || !motionEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = if (motionEnabled) 420 else 0, easing = FastOutSlowInEasing),
        label = "minecraftHomeEntry"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 390.dp || maxWidth < 720.dp
        val dashboardHeight = maxHeight
        val toolbarSize = if (compact) 36.dp else 40.dp
        val dockHeight = if (compact) 74.dp else 84.dp
        val socialHeight = if (compact) 32.dp else 38.dp
        val sideWidth = maxOf(144.dp, minOf(220.dp, maxWidth * 0.28f))
        val promoWidth = maxOf(132.dp, minOf(210.dp, maxWidth * 0.25f))
        val stageStart = sideWidth + 22.dp
        val stageEnd = promoWidth + 22.dp

        Box(modifier = Modifier.fillMaxSize()) {
            // The selected Minecraft world wallpaper remains visible through the translucent panels.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.29f),
                                Color.Black.copy(alpha = 0.08f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.12f),
                                Color.Black.copy(alpha = 0.32f)
                            )
                        )
                    )
            )

            // Center stage is inset between the feature cards, right promo and launch dock.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = stageStart,
                        end = stageEnd,
                        top = toolbarSize + 16.dp,
                        bottom = dockHeight + 24.dp
                    )
                    .graphicsLayer {
                        alpha = entryProgress
                        translationY = (1f - entryProgress) * 10.dp.toPx()
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                                .size(width = 152.dp, height = 19.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.52f))
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 228.dp, height = 246.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(accent.copy(alpha = 0.22f), Color.Transparent)
                                    ),
                                    CircleShape
                                )
                        )
                        AerixSkinPreview3D(
                            account = account,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = if (compact) 4.dp else 18.dp, vertical = 2.dp),
                            azimuth = 24,
                            pitch = 8,
                            interactionEnabled = true,
                            isVisible = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().height(socialHeight),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SocialActionButton(
                            label = "Find a party",
                            icon = R.drawable.aerix_ic_search,
                            compact = compact,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = onSocials
                        )
                        SocialActionButton(
                            label = "Invite a friend",
                            icon = R.drawable.aerix_ic_people,
                            compact = compact,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = onSocials
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 16.dp)
                    .width(sideWidth),
                verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
            ) {
                FeatureCard(
                    title = "Shop",
                    subtitle = "Bonus waiting",
                    icon = R.drawable.aerix_ic_gift_filled,
                    color = HomeRed,
                    borderColor = Color(0xFFFF5151),
                    badgeText = "!",
                    compact = compact,
                    fontFamily = fontFamily,
                    onClick = onDiscover
                )
                FeatureCard(
                    title = "My skin",
                    subtitle = account?.username?.takeIf { it.isNotBlank() } ?: "Choose a look",
                    icon = R.drawable.aerix_ic_person_outlined,
                    color = HomeBlue,
                    borderColor = Color(0xFF3A8DFF),
                    face = account?.face,
                    compact = compact,
                    fontFamily = fontFamily,
                    onClick = onAccounts
                )
                FeatureCard(
                    title = "Play with friends",
                    subtitle = friendsSubtitle,
                    icon = R.drawable.aerix_group_pixel_art,
                    color = HomeGreen,
                    borderColor = Color(0xFF63D45C),
                    compact = compact,
                    fontFamily = fontFamily,
                    onClick = onSocials
                )
            }

            FriendsPromoPanel(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = maxOf(toolbarSize + 20.dp, dashboardHeight * 0.30f),
                        end = 16.dp
                    )
                    .width(promoWidth)
                    .height(if (compact) 106.dp else 124.dp)
                    .clickable(role = Role.Button, onClick = onSocials),
                compact = compact,
                fontFamily = fontFamily
            )

            MinecraftToolbar(
                face = account?.face,
                compact = compact,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp),
                onNavigate = onNavigate,
                onSkin = onAccounts,
                onGift = onSocials,
                onSettings = onSettings
            )

            InstancePlayDock(
                instances = instances,
                selected = selected,
                taskCount = taskCount,
                taskProgressPercent = taskProgressPercent,
                taskProgressTitle = taskProgressTitle,
                progressDismissed = progressDismissed,
                compact = compact,
                fontFamily = fontFamily,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .height(dockHeight),
                onChooseInstance = onLibrary,
                onPlay = when {
                    selected != null -> onPlay
                    instances.isEmpty() -> onCreate
                    else -> onLibrary
                },
                onManage = onLibrary,
                onDismissProgress = { progressDismissed = true },
                onShowProgress = { progressDismissed = false }
            )

            // The floating help pill sits just above the footer so it never covers the chest action.
            HelpShortcut(
                compact = compact,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = dockHeight + 24.dp),
                onClick = onHelp
            )
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    icon: Int,
    color: Color,
    borderColor: Color,
    compact: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    face: ImageBitmap? = null,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    val shape = CutCornerShape(6.dp)
    BlockPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 68.dp else 78.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title. $subtitle" },
        color = color,
        borderColor = borderColor,
        shape = shape,
        padding = if (compact) 6.dp else 8.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (badgeText != null) {
                        Surface(
                            shape = CutCornerShape(4.dp),
                            color = Color(0xFFD6283A),
                            contentColor = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFFF858C))
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 10.sp
                            )
                        }
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 11.sp else 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = if (compact) 8.sp else 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(if (compact) 40.dp else 48.dp)
                        .clip(CutCornerShape(5.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(borderColor.copy(alpha = 0.58f), color.copy(alpha = 0.10f))
                            )
                        )
                        .border(1.dp, borderColor.copy(alpha = 0.48f), CutCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        face != null -> Image(
                            bitmap = face,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().padding(4.dp).clip(CutCornerShape(3.dp))
                        )
                        title == "My skin" -> Image(
                            painter = painterResource(R.drawable.aerix_head_steve),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                        else -> Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(if (compact) 23.dp else 28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstancePlayDock(
    instances: List<AerixInstance>,
    selected: AerixInstance?,
    taskCount: Int,
    taskProgressPercent: Int,
    taskProgressTitle: String,
    progressDismissed: Boolean,
    compact: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier,
    onChooseInstance: () -> Unit,
    onPlay: () -> Unit,
    onManage: () -> Unit,
    onDismissProgress: () -> Unit,
    onShowProgress: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BlockPanel(
            modifier = Modifier
                .weight(65f)
                .fillMaxHeight()
                .clickable(role = Role.Button, onClick = onChooseInstance),
            color = Color(0xFF1A3A5A),
            borderColor = Color(0xFF3A8DFF),
            shape = RoundedCornerShape(8.dp),
            padding = if (compact) 7.dp else 10.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 11.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 38.dp else 48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF142F4B))
                        .border(1.dp, Color(0xFF609EE3), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_chest_filled),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(if (compact) 24.dp else 30.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "START HERE",
                        color = Color(0xFF8DD6FF),
                        fontSize = if (compact) 8.sp else 9.sp,
                        letterSpacing = 1.15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Text(
                        text = selected?.name ?: if (instances.isEmpty()) "Create your first instance" else "Choose an instance",
                        color = Color.White,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 12.sp else 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = instanceVersionLine(selected),
                        color = Color(0xFFB8D2EC),
                        fontSize = if (compact) 8.sp else 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(35f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)
        ) {
            if (taskCount > 0) {
                if (progressDismissed) {
                    TaskProgressHiddenCard(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        title = taskProgressTitle,
                        onClick = onShowProgress
                    )
                } else {
                    TaskProgressCard(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        title = taskProgressTitle,
                        percent = taskProgressPercent,
                        onDismiss = onDismissProgress
                    )
                }
            } else {
                MinecraftPlayButton(
                    label = when {
                        selected != null -> "Play"
                        instances.isEmpty() -> "Create instance"
                        else -> "Choose instance"
                    },
                    sublabel = when {
                        selected != null -> "0 h"
                        instances.isEmpty() -> "Start your library"
                        else -> "Select from your library"
                    },
                    compact = compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .guideAnchor(AerixGuideState.PLAY)
                        .semantics {
                            contentDescription = when {
                                selected != null -> "Play ${selected.name}, 0 hours"
                                instances.isEmpty() -> "Create an instance"
                                else -> "Choose an instance"
                            }
                        },
                    onClick = onPlay
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth().height(if (compact) 25.dp else 29.dp),
                shape = RoundedCornerShape(6.dp),
                color = HomeGold,
                contentColor = Color(0xFF30260E),
                border = BorderStroke(1.dp, Color(0xFFFFE99B)),
                onClick = onManage
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_chest_filled),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(if (compact) 17.dp else 19.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Open chest",
                        color = Color(0xFF30260E),
                        fontFamily = fontFamily,
                        fontSize = if (compact) 9.sp else 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun instanceVersionLine(instance: AerixInstance?): String {
    if (instance == null) return "Browse versions and modpacks"
    val id = instance.versionId
    val version = Regex("\\d+\\.\\d+(?:\\.\\d+)?").find(id)?.value ?: id.ifBlank { "Minecraft" }
    val loader = when {
        id.contains("neoforge", ignoreCase = true) -> "NeoForge"
        id.contains("forge", ignoreCase = true) -> "Forge"
        id.contains("quilt", ignoreCase = true) -> "Quilt"
        id.contains("fabric", ignoreCase = true) -> "Fabric"
        else -> "Minecraft"
    }
    return "$loader · $version"
}

@Composable
private fun TaskProgressCard(
    modifier: Modifier,
    title: String,
    percent: Int,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xE5163421),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFF4CAF50))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_cloud_download),
                    contentDescription = null,
                    tint = Color(0xFF8DE398),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (percent in 1..100) "$title · $percent%" else "$title …",
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    modifier = Modifier.size(22.dp),
                    shape = CutCornerShape(4.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    contentColor = Color.White,
                    onClick = onDismiss
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_close),
                            contentDescription = "Hide task progress",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            if (percent in 1..100) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color(0xFF76E184),
                    trackColor = Color.White.copy(alpha = 0.13f)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color(0xFF76E184),
                    trackColor = Color.White.copy(alpha = 0.13f)
                )
            }
        }
    }
}

@Composable
private fun TaskProgressHiddenCard(modifier: Modifier, title: String, onClick: () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xC9163421),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFF438D50)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_cloud_download),
                contentDescription = null,
                tint = Color(0xFF8DE398),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$title in background",
                modifier = Modifier.weight(1f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("SHOW", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFF9EECA6))
        }
    }
}

@Composable
private fun MinecraftPlayButton(
    label: String,
    sublabel: String,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val green = ThemeMotion.rememberPulse(ReadyGreen, active = true)
    Surface(
        modifier = modifier,
        shape = CutCornerShape(6.dp),
        color = green,
        contentColor = Color(0xFF09290C),
        border = BorderStroke(1.dp, Color(0xFFB8F99C)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = if (compact) 2.dp else 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_play_arrow_filled),
                    contentDescription = null,
                    tint = Color(0xFF09290C),
                    modifier = Modifier.size(if (compact) 14.dp else 17.dp)
                )
                Text(
                    text = label,
                    fontSize = if (compact) 10.sp else 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = sublabel,
                fontSize = if (compact) 7.sp else 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E461E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SocialActionButton(
    label: String,
    icon: Int,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = CutCornerShape(5.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color(0xA6000000),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.17f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 5.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color(0xFFFFD36A),
                modifier = Modifier.size(if (compact) 13.dp else 16.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = if (compact) 8.sp else 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FriendsPromoPanel(
    modifier: Modifier,
    compact: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily
) {
    BlockPanel(
        modifier = modifier,
        color = Color(0xD916171A),
        borderColor = Color(0xFF4A4B4F),
        shape = CutCornerShape(7.dp),
        padding = if (compact) 9.dp else 12.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth(0.78f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "+1 D OF PLUS FOR EVERY FRIEND",
                    color = Color(0xFFD4D1C8),
                    fontSize = if (compact) 5.sp else 7.sp,
                    letterSpacing = 0.35.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "FREE PLUS",
                        color = HomeGold,
                        fontFamily = fontFamily,
                        fontSize = if (compact) 12.sp else 16.sp,
                        lineHeight = if (compact) 14.sp else 18.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "FOR A FRIEND",
                        color = Color.White,
                        fontFamily = fontFamily,
                        fontSize = if (compact) 10.sp else 13.sp,
                        lineHeight = if (compact) 12.sp else 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(if (compact) 42.dp else 52.dp),
                shape = CutCornerShape(6.dp),
                color = HomeGold,
                contentColor = Color(0xFF4B3810),
                border = BorderStroke(1.dp, Color(0xFFFFE998))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_ticket_filled),
                        contentDescription = null,
                        tint = Color(0xFF8F6717),
                        modifier = Modifier.size(if (compact) 19.dp else 24.dp)
                    )
                    Text("PLUS", fontSize = 7.sp, fontWeight = FontWeight.Black, lineHeight = 8.sp)
                }
            }
        }
    }
}

@Composable
private fun MinecraftToolbar(
    face: ImageBitmap?,
    compact: Boolean,
    modifier: Modifier,
    onNavigate: (LauncherSection) -> Unit,
    onSkin: () -> Unit,
    onGift: () -> Unit,
    onSettings: () -> Unit
) {
    val size = if (compact) 34.dp else 40.dp
    var menuExpanded by remember { mutableStateOf(false) }
    val destinations = remember {
        listOf(
            "Home" to LauncherSection.HOME,
            "Aerix Client" to LauncherSection.AERIX_CLIENT,
            "Library" to LauncherSection.LIBRARY,
            "Servers" to LauncherSection.SERVERS,
            "Socials" to LauncherSection.SOCIALS,
            "Discover" to LauncherSection.DISCOVER,
            "Files" to LauncherSection.FILES,
            "Wallpapers" to LauncherSection.WALLPAPERS,
            "Logs" to LauncherSection.LOGS,
            "Accounts" to LauncherSection.ACCOUNTS,
            "Aerix account" to LauncherSection.AERIX_ACCOUNT,
            "Settings" to LauncherSection.SETTINGS,
            "About" to LauncherSection.ABOUT
        )
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            ToolbarSquare(R.drawable.aerix_ic_person_outlined, "Profile and launcher navigation", size) {
                menuExpanded = true
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                destinations.forEach { (label, destination) ->
                    DropdownMenuItem(
                        text = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        onClick = {
                            menuExpanded = false
                            onNavigate(destination)
                        }
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.size(size).semantics { contentDescription = "My skin and accounts" },
            shape = CutCornerShape(5.dp),
            color = Color(0x80000000),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
            onClick = onSkin
        ) {
            if (face != null) {
                Image(bitmap = face, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Image(
                    painter = painterResource(R.drawable.aerix_head_steve),
                    contentDescription = "Steve player avatar",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(3.dp)
                )
            }
        }
        ToolbarSquare(R.drawable.aerix_ic_gift_filled, "Daily rewards and friend invites", size, onGift)
        ToolbarSquare(R.drawable.aerix_ic_settings_filled, "Settings", size, onSettings)
    }
}

@Composable
private fun ToolbarSquare(icon: Int, description: String, size: Dp, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(size).semantics { contentDescription = description },
        shape = CutCornerShape(5.dp),
        color = Color(0x80000000),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
        onClick = onClick
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.48f)
            )
        }
    }
}

@Composable
private fun HelpShortcut(compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(if (compact) 31.dp else 36.dp),
        shape = CutCornerShape(5.dp),
        color = Color(0x80000000),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
            Text("Help", fontSize = if (compact) 9.sp else 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun BlockPanel(
    modifier: Modifier,
    color: Color,
    borderColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    padding: Dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Box(modifier = modifier.padding(end = 3.dp, bottom = 3.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .clip(shape)
                .background(Color(0xB5000000))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(color.copy(alpha = color.alpha * 0.82f), color)))
                .border(1.dp, borderColor, shape)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}
