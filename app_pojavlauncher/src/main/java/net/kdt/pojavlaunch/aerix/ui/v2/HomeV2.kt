/*
 * Aerix Launcher — Home, rebuilt.
 *
 * Layout follows the approved concept exactly: title at the top left, one wide hero card with
 * the selected instance, its version pill, the big Play button and three action chips, then a
 * right-hand column of three live tiles (friends online, recent worlds, news).
 *
 * Everything is either alive or reacts to touch: the hero levitates, the Play button glows and
 * sweeps, the instance art drifts, the presence dots pulse, the friend avatars breathe, tiles
 * tilt toward the finger, and the whole screen assembles with a staggered rise.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.v2

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixInstance
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavTaskBridge
import net.kdt.pojavlaunch.aerix.social.SocialGraph
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.aerix.ui.glass.Glass
import net.kdt.pojavlaunch.aerix.ui.glass.GlassMotion
import net.kdt.pojavlaunch.aerix.ui.glass.Presence
import net.kdt.pojavlaunch.aerix.ui.glass.PresenceDot
import kotlin.math.sin

/**
 * Home v2. Real instances, real launch path, real friends from Social.
 */
@Composable
fun HomeV2(
    activity: FragmentActivity,
    navigate: (LauncherSection) -> Unit,
    onOpenSocial: () -> Unit
) {
    val instances = PojavInstanceBridge.instances
    val selected = PojavInstanceBridge.selected ?: instances.firstOrNull()
    val tasks = PojavTaskBridge.taskCount
    val friends = SocialGraph.repository.friends.collectAsState().value
    val onlineFriends = remember(friends) {
        friends.filter { it.status != net.kdt.pojavlaunch.aerix.social.data.UserStatus.OFFLINE }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp && maxWidth > maxHeight
        val contentPadding = 18.dp
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            // Title row. The right-hand cluster (back / account / gear) belongs to the shell.
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("HOME")
                Spacer(Modifier.width(10.dp))
                if (tasks > 0) {
                    TaskPill(count = tasks, onClick = { navigate(LauncherSection.DOWNLOADS) })
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            if (wide) {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(2.1f).fillMaxHeight()) {
                        HeroCard(
                            instance = selected,
                            onPlay = {
                                selected?.let { PojavInstanceBridge.select(it) }
                                PojavLaunchBridge.play(activity)
                            },
                            onManage = {
                                selected?.let { PojavInstanceBridge.edit(activity, it) }
                                    ?: navigate(LauncherSection.LIBRARY)
                            },
                            onMods = {
                                selected?.let { PojavInstanceBridge.edit(activity, it, "mods") }
                                    ?: navigate(LauncherSection.LIBRARY)
                            },
                            onCopy = { navigate(LauncherSection.LIBRARY) }
                        )
                        Spacer(Modifier.height(16.dp))
                        QuickStrip(
                            onLibrary = { navigate(LauncherSection.LIBRARY) },
                            onCreate = { navigate(LauncherSection.CREATE_INSTANCE) },
                            onAccounts = { navigate(LauncherSection.ACCOUNTS) },
                            onSocial = onOpenSocial,
                            onSettings = { navigate(LauncherSection.SETTINGS) }
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FriendsOnlineTile(onlineFriends, onOpen = onOpenSocial)
                        RecentTile(
                            recent = instances.filterNot { it == selected }.take(2).ifEmpty { instances.take(2) },
                            onOpen = { instance ->
                                PojavInstanceBridge.select(instance)
                                PojavLaunchBridge.play(activity)
                            }
                        )
                        NewsTile(onOpen = onOpenSocial)
                    }
                }
            } else {
                // Portrait: hero first, then the tiles in one scrollable column.
                Column(modifier = Modifier.fillMaxSize().verticalScrollCompat()) {
                    HeroCard(
                        instance = selected,
                        onPlay = {
                            selected?.let { PojavInstanceBridge.select(it) }
                            PojavLaunchBridge.play(activity)
                        },
                        onManage = { selected?.let { PojavInstanceBridge.edit(activity, it) } ?: navigate(LauncherSection.LIBRARY) },
                        onMods = { selected?.let { PojavInstanceBridge.edit(activity, it, "mods") } ?: navigate(LauncherSection.LIBRARY) },
                        onCopy = { navigate(LauncherSection.LIBRARY) }
                    )
                    Spacer(Modifier.height(12.dp))
                    QuickStrip(
                        onLibrary = { navigate(LauncherSection.LIBRARY) },
                        onCreate = { navigate(LauncherSection.CREATE_INSTANCE) },
                        onAccounts = { navigate(LauncherSection.ACCOUNTS) },
                        onSocial = onOpenSocial,
                        onSettings = { navigate(LauncherSection.SETTINGS) }
                    )
                    Spacer(Modifier.height(12.dp))
                    FriendsOnlineTile(onlineFriends, onOpen = onOpenSocial)
                    Spacer(Modifier.height(12.dp))
                    RecentTile(instances.take(2)) { instance ->
                        PojavInstanceBridge.select(instance)
                        PojavLaunchBridge.play(activity)
                    }
                    Spacer(Modifier.height(12.dp))
                    NewsTile(onOpen = onOpenSocial)
                }
            }
        }
    }
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    verticalScroll(rememberScrollState())


/* -------------------------------------------------------------------- hero ----- */

/**
 * The hero card: instance art on the left, name, version pill and the big Play button on the
 * right, three chips beneath. Play is the most physical element on the screen.
 */
@Composable
private fun HeroCard(
    instance: AerixInstance?,
    onPlay: () -> Unit,
    onManage: () -> Unit,
    onMods: () -> Unit,
    onCopy: () -> Unit
) {
    val accent = Glass.accent
    // Slow levitation: the card never sits perfectly still.
    val float by rememberInfiniteTransition(label = "heroFloat").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heroFloatValue"
    )
    InteractiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = (float - 0.5f) * 6f },
        shape = RoundedCornerShape(24.dp),
        tint = Glass.raised.copy(alpha = 0.55f),
        borderColor = Color.White.copy(alpha = 0.16f),
        contentPadding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            InstanceArt(
                instance = instance,
                modifier = Modifier
                    .weight(0.9f)
                    .height(190.dp)
            )
            Column(
                modifier = Modifier.weight(1.1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = instance?.name ?: "No instance yet",
                    color = Glass.textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(text = "v" + (instance?.versionId ?: "—"), tint = Glass.track)
                    if (instance != null) Pill(text = loaderName(instance), tint = accent.copy(alpha = 0.18f))
                }
                Spacer(Modifier.height(16.dp))
                PlayButton(onPlay = onPlay, enabled = instance != null)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip("Manage", onClick = onManage)
                    ActionChip("Mods", onClick = onMods)
                    ActionChip("Copy", onClick = onCopy)
                }
            }
        }
    }
}

/** Real instance icon when the launcher has one, otherwise a generated blocky scene. */
@Composable
private fun InstanceArt(instance: AerixInstance?, modifier: Modifier = Modifier) {
    val icon = instance?.icon
    val drift by rememberInfiniteTransition(label = "artDrift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "artDriftValue"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Glass.canvas)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = instance?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // Sky gradient.
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF0E2A3A), Color(0xFF123A44), Color(0xFF1B4A46)))
                )
                // Two hill layers, slowly sliding to fake parallax.
                val shift = sin(drift * 6.2831855f) * w * 0.03f
                drawRect(
                    color = Color(0xFF0B2A33),
                    topLeft = Offset(-w * 0.2f + shift, h * 0.62f),
                    size = Size(w * 1.4f, h * 0.4f)
                )
                drawRect(
                    color = Color(0xFF07202A),
                    topLeft = Offset(-w * 0.4f - shift, h * 0.74f),
                    size = Size(w * 1.6f, h * 0.3f)
                )
                // A cottage block, lit.
                val bx = w * 0.18f + shift * 2f
                val by = h * 0.46f
                drawRect(Color(0xFF20323B), Offset(bx, by), Size(w * 0.18f, h * 0.18f))
                drawRect(Color(0xFF8DEFE0).copy(alpha = 0.55f), Offset(bx + w * 0.05f, by + h * 0.06f), Size(w * 0.035f, h * 0.045f))
                drawRect(Color(0xFF2C4450), Offset(bx - w * 0.02f, by - h * 0.05f), Size(w * 0.22f, h * 0.05f))
                // Stars.
                repeat(18) { index ->
                    val sx = ((index * 97) % 100) / 100f * w
                    val sy = ((index * 53) % 55) / 100f * h
                    val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(drift * 6.28f + index))
                    drawCircle(Color.White.copy(alpha = 0.5f * twinkle), radius = 1.2f, center = Offset(sx, sy))
                }
            }
        }
        // Aurora wash over the art.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    listOf(accentAlpha(0.16f), Color.Transparent),
                    center = Offset(size.width * 0.65f, size.height * 0.1f),
                    radius = size.width * 0.9f
                )
            )
        }
    }
}

private fun accentAlpha(alpha: Float): Color = Color(0xFF8DEFE0).copy(alpha = alpha)

/**
 * Play: the centrepiece interaction. Held -> sinks, glows, takes a specular sweep and throws a
 * ripple; released -> launches the selected instance for real.
 */
@Composable
private fun PlayButton(onPlay: () -> Unit, enabled: Boolean) {
    val accent = Glass.accent
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(dampingRatio = 0.36f, stiffness = Spring.StiffnessMediumLow),
        label = "playScale"
    )
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0.5f,
        animationSpec = tween(180),
        label = "playGlow"
    )
    val breathe by rememberInfiniteTransition(label = "playBreathe").animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "playBreatheValue"
    )
    val sweep by rememberInfiniteTransition(label = "playSweep").animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(GlassMotion.SWEEP, easing = LinearEasing)),
        label = "playSweepValue"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = 22f * maxOf(glow, breathe)
                shape = CircleShape
                clip = false
            }
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    if (enabled) listOf(Color(0xFFB9FBEF), accent) else listOf(Glass.track, Glass.track)
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(32.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) onPlay()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bandWidth = size.width * 0.3f
            val x = size.width * sweep
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.32f), Color.Transparent),
                    startX = x - bandWidth,
                    endX = x + bandWidth
                ),
                topLeft = Offset(x - bandWidth, 0f),
                size = Size(bandWidth * 2f, size.height)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_play_arrow_filled),
                contentDescription = null,
                tint = Glass.onAccent,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = "Play",
                color = Glass.onAccent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/* ------------------------------------------------------------------- tiles ----- */

@Composable
private fun FriendsOnlineTile(
    friends: List<net.kdt.pojavlaunch.aerix.social.data.AerixUser>,
    onOpen: () -> Unit
) {
    InteractiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tint = Glass.track.copy(alpha = 0.5f),
        contentPadding = 12.dp,
        onClick = onOpen
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Friends online", color = Glass.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (friends.isEmpty()) {
                    Text("Nobody yet", color = Glass.textMuted, fontSize = 11.sp)
                } else {
                    friends.take(3).forEach { friend ->
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AvatarBubble(avatarIndex = friend.avatar, story = friend.hasStory, size = 40)
                            Box(modifier = Modifier.padding(1.dp)) {
                                PresenceDot(
                                    state = if (friend.status == net.kdt.pojavlaunch.aerix.social.data.UserStatus.IN_VOICE)
                                        Presence.PARTY else Presence.ONLINE,
                                    size = 10.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarBubble(avatarIndex: Int, story: Boolean, size: Int) {
    val ring by rememberInfiniteTransition(label = "storyRing").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "storyRingValue"
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (story) Glass.story.copy(alpha = ring) else Color.Transparent)
            .padding(if (story) 2.dp else 0.dp)
    ) {
        Image(
            painter = painterResource(net.kdt.pojavlaunch.aerix.social.ui.socialAvatarRes(avatarIndex)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().clip(CircleShape)
        )
    }
}

@Composable
private fun RecentTile(recent: List<AerixInstance>, onOpen: (AerixInstance) -> Unit) {
    InteractiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tint = Glass.track.copy(alpha = 0.5f),
        contentPadding = 12.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent instances", color = Glass.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (recent.isEmpty()) {
                Text("Create one in Library", color = Glass.textMuted, fontSize = 11.sp)
            } else {
                recent.forEach { instance ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .pointerInput(instance.id) { detectTapGestures { onOpen(instance) } }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InstanceArt(instance = instance, modifier = Modifier.size(46.dp, 32.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                instance.name,
                                color = Glass.textPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                instance.versionId,
                                color = Glass.textMuted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsTile(onOpen: () -> Unit) {
    InteractiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tint = Glass.track.copy(alpha = 0.5f),
        contentPadding = 12.dp,
        onClick = onOpen
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Social", color = Glass.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Your friends, messages and parties",
                color = Glass.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_favorite_filled),
                    contentDescription = null,
                    tint = Glass.story,
                    modifier = Modifier.size(12.dp)
                )
                Text("Open", color = Glass.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun QuickStrip(
    onLibrary: () -> Unit,
    onCreate: () -> Unit,
    onAccounts: () -> Unit,
    onSocial: () -> Unit,
    onSettings: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        QuickAction("Library", R.drawable.aerix_ic_folder_outlined, onLibrary, Modifier.weight(1f))
        QuickAction("Create", R.drawable.aerix_ic_add, onCreate, Modifier.weight(1f))
        QuickAction("Accounts", R.drawable.aerix_ic_person_outlined, onAccounts, Modifier.weight(1f))
        QuickAction("Social", R.drawable.aerix_social_ic_friends, onSocial, Modifier.weight(1f), badge = SocialGraph.repository.requests.value.size)
        QuickAction("Settings", R.drawable.aerix_ic_settings_filled, onSettings, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0
) {
    InteractiveCard(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        tint = Glass.track.copy(alpha = 0.45f),
        contentPadding = 8.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Glass.accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(label, color = Glass.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Glass.story)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(if (badge > 9) "9+" else badge.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TaskPill(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Glass.accent.copy(alpha = 0.16f))
            .border(1.dp, Glass.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .pointerInput(count) { detectTapGestures { onClick() } }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Glass.accent))
        Text("$count downloading", color = Glass.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Pill(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = Glass.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun loaderName(instance: AerixInstance): String {
    val version = instance.versionId.lowercase()
    return when {
        version.contains("fabric") -> "Fabric"
        version.contains("neoforge") -> "NeoForge"
        version.contains("forge") -> "Forge"
        version.contains("quilt") -> "Quilt"
        else -> "Vanilla"
    }
}
