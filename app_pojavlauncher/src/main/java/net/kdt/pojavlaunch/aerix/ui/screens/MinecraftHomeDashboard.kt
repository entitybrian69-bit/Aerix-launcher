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
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
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

private val HomeRed = Color(0xFFB51F3B)
private val HomeBlue = Color(0xFF267CC1)
private val HomeGreen = Color(0xFF4EA83A)
private val HomeGold = Color(0xFFF4C84F)
private val HomeSoftWhite = Color(0xFFF5F5F2)

@Composable
fun MinecraftHomeDashboard(
    instances: List<AerixInstance>,
    selected: AerixInstance?,
    taskCount: Int,
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
    LaunchedEffect(Unit) {
        net.kdt.pojavlaunch.aerix.social.SocialBackend.attach(context.applicationContext)
        if (net.kdt.pojavlaunch.aerix.social.SocialBackend.signedIn) {
            net.kdt.pojavlaunch.aerix.social.SocialBackend.refreshFriends()
            net.kdt.pojavlaunch.aerix.social.SocialBackend.refreshParties()
            net.kdt.pojavlaunch.aerix.social.SocialBackend.refreshPartyInvites()
            net.kdt.pojavlaunch.aerix.social.SocialBackend.unreadCount()
        }
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entryProgress by animateFloatAsState(
        targetValue = if (entered || !motionEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = if (motionEnabled) 420 else 0, easing = FastOutSlowInEasing),
        label = "minecraftHomeEntry"
    )
    val selectedFont = AerixFonts.selected()
    val fontFamily = remember(selectedFont.id) { selectedFont.family() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 300.dp
        val compactWidth = maxWidth < 650.dp
        val showFriendsPanel = maxWidth >= 680.dp
        val leftRailWidth = (maxWidth * 0.175f).coerceIn(112.dp, 154.dp)
        val friendsPanelWidth = if (showFriendsPanel) (maxWidth * 0.205f).coerceIn(144.dp, 194.dp) else 0.dp
        val edgePadding = if (compactHeight) 5.dp else 8.dp

        Box(modifier = Modifier.fillMaxSize()) {
            // Keep the chosen Aerix wallpaper visible, but add the subtle side shade of the reference
            // so the colorful cards and white labels stay legible on bright scenes.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.20f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.27f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (maxWidth >= 700.dp) 34.dp else 16.dp,
                        end = edgePadding,
                        top = edgePadding,
                        bottom = edgePadding
                    )
                    .graphicsLayer {
                        alpha = entryProgress
                        translationY = (1f - entryProgress) * 10.dp.toPx()
                    },
                horizontalArrangement = Arrangement.spacedBy(if (compactHeight) 6.dp else 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .width(leftRailWidth)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(if (compactHeight) 6.dp else 9.dp)) {
                        FeatureCard(
                            title = "Discover",
                            subtitle = "New packs waiting",
                            icon = R.drawable.aerix_ic_search,
                            color = HomeRed,
                            compact = compactHeight,
                            fontFamily = fontFamily,
                            onClick = onDiscover
                        )
                        FeatureCard(
                            title = "My skin",
                            subtitle = account?.username?.takeIf { it.isNotBlank() } ?: "Choose a look",
                            icon = R.drawable.aerix_ic_person_outlined,
                            color = HomeBlue,
                            compact = compactHeight,
                            face = account?.face,
                            fontFamily = fontFamily,
                            onClick = onAccounts
                        )
                        FeatureCard(
                            title = "Play with friends",
                            subtitle = friendsSubtitle,
                            icon = R.drawable.aerix_ic_people,
                            color = HomeGreen,
                            compact = compactHeight,
                            fontFamily = fontFamily,
                            onClick = onSocials
                        )
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val dockHeight = if (compactHeight) 63.dp else 76.dp
                    val socialButtonsHeight = if (compactHeight) 30.dp else 36.dp
                    val stageHeight = (maxHeight - dockHeight - socialButtonsHeight - 20.dp).coerceAtLeast(104.dp)
                    val socialBottomInset = dockHeight + if (compactHeight) 7.dp else 10.dp

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(stageHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                                .size(width = if (compactWidth) 128.dp else 190.dp, height = 21.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.43f))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(width = if (compactWidth) 136.dp else 228.dp, height = stageHeight * 0.92f)
                                .background(
                                    Brush.radialGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent)),
                                    CircleShape
                                )
                        )
                        AerixSkinPreview3D(
                            account = account,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = if (compactWidth) 6.dp else 18.dp, vertical = 2.dp),
                            azimuth = 24,
                            pitch = 8,
                            interactionEnabled = false,
                            isVisible = true
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = socialBottomInset)
                            .height(socialButtonsHeight),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val socialButtonWidth = if (maxWidth < 360.dp) 0.47f else 0.39f
                        SocialActionButton(
                            label = "Find a party",
                            icon = R.drawable.aerix_ic_search,
                            compact = compactWidth,
                            modifier = Modifier.fillMaxWidth(socialButtonWidth).height(socialButtonsHeight),
                            onClick = onSocials
                        )
                        Spacer(Modifier.width(if (compactHeight) 5.dp else 8.dp))
                        SocialActionButton(
                            label = "Invite a friend",
                            icon = R.drawable.aerix_ic_people,
                            compact = compactWidth,
                            modifier = Modifier.fillMaxWidth(socialButtonWidth).height(socialButtonsHeight),
                            onClick = onSocials
                        )
                    }

                    InstancePlayDock(
                        selected = selected,
                        taskCount = taskCount,
                        compact = compactHeight,
                        fontFamily = fontFamily,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(dockHeight),
                        onChooseInstance = onLibrary,
                        onPlay = when {
                            selected != null -> onPlay
                            instances.isEmpty() -> onCreate
                            else -> onLibrary
                        },
                        onManage = onLibrary
                    )
                }

                if (showFriendsPanel) {
                    FriendsPromoPanel(
                        modifier = Modifier
                            .align(Alignment.Top)
                            .width(friendsPanelWidth)
                            .padding(top = if (compactHeight) 46.dp else 54.dp)
                            .height(if (compactHeight) 88.dp else 116.dp),
                        compact = compactHeight,
                        fontFamily = fontFamily,
                        onClick = onSocials
                    )
                }
            }

            MinecraftToolbar(
                face = account?.face,
                compact = compactHeight,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = edgePadding, end = edgePadding),
                onNavigate = onNavigate,
                onSkin = onAccounts,
                onGift = onSocials,
                onSettings = onSettings
            )

            HelpShortcut(
                compact = compactHeight,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = edgePadding, bottom = edgePadding),
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
    compact: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    face: ImageBitmap? = null,
    onClick: () -> Unit
) {
    val shape = CutCornerShape(5.dp)
    BlockPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 58.dp else 76.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title. $subtitle" },
        color = color,
        borderColor = color.copy(alpha = 0.98f),
        shape = shape,
        padding = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        Brush.verticalGradient(
                            listOf(color.copy(alpha = 0.70f), color.copy(alpha = 0.98f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(if (compact) 23.dp else 32.dp)
                        .background(Color.White.copy(alpha = 0.08f), CutCornerShape(2.dp))
                )
                if (face != null) {
                    Image(
                        bitmap = face,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(if (compact) 26.dp else 34.dp).clip(CutCornerShape(3.dp))
                    )
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = HomeSoftWhite,
                        modifier = Modifier.size(if (compact) 22.dp else 28.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.20f))
                    .padding(horizontal = 7.dp, vertical = if (compact) 3.dp else 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = if (compact) 9.sp else 11.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compact) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun InstancePlayDock(
    selected: AerixInstance?,
    taskCount: Int,
    compact: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier,
    onChooseInstance: () -> Unit,
    onPlay: () -> Unit,
    onManage: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BlockPanel(
            modifier = Modifier
                .weight(1.25f)
                .fillMaxHeight()
                .clickable(role = Role.Button, onClick = onChooseInstance),
            color = Color(0xFF205499),
            borderColor = Color(0xFF76B3FF),
            shape = CutCornerShape(7.dp),
            padding = if (compact) 7.dp else 10.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 32.dp else 42.dp)
                        .clip(CutCornerShape(4.dp))
                        .background(Color(0xFF183E70))
                        .border(1.dp, Color.White.copy(alpha = 0.24f), CutCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_videogame_asset_outlined),
                        contentDescription = null,
                        tint = Color(0xFFFFD36A),
                        modifier = Modifier.size(if (compact) 18.dp else 23.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "START HERE",
                        color = Color(0xFFB9D7FF),
                        fontSize = if (compact) 8.sp else 9.sp,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Text(
                        text = selected?.name ?: "Choose an instance",
                        color = Color.White,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 12.sp else 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!compact || taskCount > 0) {
                        val detail = when {
                            taskCount > 0 -> "$taskCount setup task${if (taskCount == 1) "" else "s"} running"
                            selected != null -> selected.versionId
                            else -> "Browse versions and modpacks"
                        }
                        Text(
                            text = detail,
                            color = Color.White.copy(alpha = 0.86f),
                            fontSize = if (compact) 8.sp else 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.82f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)
        ) {
            MinecraftPlayButton(
                label = when {
                    selected != null -> "Play"
                    instances.isEmpty() -> "Create instance"
                    else -> "Choose instance"
                },
                sublabel = when {
                    taskCount > 0 -> "Preparing · $taskCount task${if (taskCount == 1) "" else "s"}"
                    selected != null -> "Ready to launch"
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
                            selected != null -> "Play ${selected.name}"
                            instances.isEmpty() -> "Create an instance"
                            else -> "Choose an instance"
                        }
                    },
                onClick = onPlay
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 22.dp else 27.dp),
                shape = CutCornerShape(4.dp),
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
                        painter = painterResource(R.drawable.aerix_ic_folder_filled),
                        contentDescription = null,
                        tint = Color(0xFF5B4720),
                        modifier = Modifier.size(if (compact) 13.dp else 16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Open chest",
                        color = Color(0xFF30260E),
                        fontFamily = fontFamily,
                        fontSize = if (compact) 8.sp else 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
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
    val green = ThemeMotion.rememberPulse(HomeGreen, active = true)
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
            if (!compact) {
                Text(
                    text = sublabel,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E461E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
        color = Color(0xE91A1C20),
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
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    onClick: () -> Unit
) {
    BlockPanel(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xF0101113),
        borderColor = Color(0xFF393B3E),
        shape = CutCornerShape(7.dp),
        padding = if (compact) 9.dp else 12.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "BETTER TOGETHER",
                color = Color(0xFFB5B5B5),
                fontSize = 7.sp,
                letterSpacing = 1.1.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "FIND YOUR\nNEXT PARTY",
                    color = Color.White,
                    fontFamily = fontFamily,
                    fontSize = if (compact) 12.sp else 15.sp,
                    lineHeight = if (compact) 14.sp else 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2
                )
                Text(
                    text = "Invite a friend and jump in together.",
                    color = Color(0xFFFFD15B),
                    fontSize = if (compact) 8.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth().height(if (compact) 23.dp else 27.dp),
                shape = CutCornerShape(3.dp),
                color = HomeGold,
                contentColor = Color(0xFF251D08),
                border = BorderStroke(1.dp, Color(0xFFFFE89B)),
                onClick = onClick
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_people),
                        contentDescription = null,
                        tint = Color(0xFF4A3912),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "OPEN SOCIALS",
                        fontFamily = fontFamily,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
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
            ToolbarSquare(R.drawable.aerix_ic_menu_launcher, "Navigation menu", size) {
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
            color = Color(0xE9222326),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
            onClick = onSkin
        ) {
            if (face != null) {
                Image(bitmap = face, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.aerix_ic_person_outlined), null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        ToolbarSquare(R.drawable.aerix_ic_gift_filled, "Invite a friend", size, onGift)
        ToolbarSquare(R.drawable.aerix_ic_settings_filled, "Settings", size, onSettings)
    }
}

@Composable
private fun ToolbarSquare(icon: Int, description: String, size: Dp, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(size).semantics { contentDescription = description },
        shape = CutCornerShape(5.dp),
        color = Color(0xE9222326),
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
        color = Color(0xE9191A1D),
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
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.92f), color)))
                .border(1.dp, borderColor, shape)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}
