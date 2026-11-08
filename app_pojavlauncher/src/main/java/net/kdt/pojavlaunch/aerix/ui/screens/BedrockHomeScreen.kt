/*
 * Aerix Launcher — Bedrock-inspired Home
 * Inspired by Minecraft Bedrock's home panorama layout, rebuilt as an original Aerix
 * composition. No Mojang textures are copied; all shapes, colors and motion are authored
 * for Aerix and rendered with Compose.
 *
 * Layout (matches screenshots 1:1 in structure, not in asset-rip):
 *  - Left: three notched cards (Shop / My skin / Play with friends)
 *  - Center: large draggable player doll on a soft stage
 *  - Top-right: utility icons
 *  - Right: promo card (Free Plus)
 *  - Bottom: instance dock (Modpack 1.20.1) + secondary actions
 *
 * Smooth feel: spring entrance, parallax, fling on drag, liquid-glass blur.
 */

package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixInstance
import net.kdt.pojavlaunch.aerix.bridge.GameDownloadTarget
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.bridge.PojavTaskBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.withFrameNanos
import net.kdt.pojavlaunch.aerix.social.SocialBackend

private const val DOLL_DRAG_DEG_PER_PX = 0.42f
private const val DOLL_FLING_FRICTION = 5.4f
private const val DOLL_FLING_MIN_V = 55f
private const val DOLL_FLING_STOP_V = 18f

// ---------- Public entry ----------

@Composable
fun BedrockHomeScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    LaunchedEffect(Unit) { PojavAccountBridge.ensureLoaded() }
    val instances = PojavInstanceBridge.instances
    val selected = PojavInstanceBridge.selected
    val taskCount = PojavTaskBridge.taskCount
    val accent = AerixThemeManager.currentAccent()
    val progress = remember(taskCount) { activeTaskProgress(taskCount) }
    // Assets simulating download progress (use real taskCount to drive bar)
    val assetProgress = if (taskCount > 0) (0.12f + (progress ?: 0f) * 0.88f).coerceIn(0f, 0.99f) else 0.93f

    val jarLauncher = rememberLauncherForActivityResult(OpenDocumentWithExtension("jar")) { uri ->
        if (uri != null) PojavNavigator.runJar(activity, uri)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp
        val isCompactHeight = maxHeight < 520.dp

        // Panorama background is already drawn by AerixBackground outside; we add a soft vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)),
                        startY = 0f, endY = 1200f
                    )
                )
        )

        // Staggered entrance
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }

        if (isWide) {
            BedrockWideLayout(
                activity = activity,
                navigate = navigate,
                instances = instances,
                selected = selected,
                accent = accent,
                assetProgress = assetProgress,
                taskCount = taskCount,
                jarLauncher = jarLauncher,
                entered = entered
            )
        } else {
            BedrockCompactLayout(
                activity = activity,
                navigate = navigate,
                instances = instances,
                selected = selected,
                accent = accent,
                assetProgress = assetProgress,
                taskCount = taskCount,
                entered = entered
            )
        }
    }
}

@Composable
private fun BedrockWideLayout(
    activity: FragmentActivity,
    navigate: (LauncherSection) -> Unit,
    instances: List<AerixInstance>,
    selected: AerixInstance?,
    accent: Color,
    assetProgress: Float,
    taskCount: Int,
    jarLauncher: androidx.activity.result.ActivityResultLauncher<Any?>,
    entered: Boolean
) {
    val infinite = rememberInfiniteTransition(label = "bedrockIdle")
    val bob by infinite.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )

    // Main 3-column
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // LEFT stack — entrance stagger
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BedrockNotchedCard(
                title = "Shop",
                subtitle = "Bonus waiting",
                badge = "!",
                accent = Color(0xFFD11B3A),
                accentDark = Color(0xFF8E0F24),
                iconRes = R.drawable.aerix_ic_package_2_outlined,
                imageTint = Color.White,
                onClick = { navigate(LauncherSection.DISCOVER) },
                entered = entered,
                delayMs = 0,
                modifier = Modifier.fillMaxWidth().height(86.dp)
            )
            BedrockNotchedCard(
                title = "My skin",
                subtitle = null,
                accent = Color(0xFF1E6FE0),
                accentDark = Color(0xFF1349A3),
                iconRes = R.drawable.aerix_ic_person_outlined,
                imageTint = Color.White,
                onClick = { navigate(LauncherSection.ACCOUNTS) },
                entered = entered,
                delayMs = 70,
                showAvatar = true,
                modifier = Modifier.fillMaxWidth().height(86.dp)
            )
            BedrockNotchedCard(
                title = "Play with friends",
                subtitle = null,
                accent = Color(0xFF1FA24A),
                accentDark = Color(0xFF12612E),
                iconRes = R.drawable.aerix_ic_people,
                imageTint = Color.White,
                onClick = { navigate(LauncherSection.SOCIALS) },
                entered = entered,
                delayMs = 140,
                compact = false,
                modifier = Modifier.fillMaxWidth().height(88.dp)
            )
            Spacer(Modifier.weight(1f))
            // Secondary actions anchored below stack (visible on wide)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BedrockPillButton("Find a party", R.drawable.aerix_ic_search, Modifier.weight(1f)) { navigate(LauncherSection.SOCIALS) }
                BedrockPillButton("Invite a friend", R.drawable.aerix_ic_group_outlined, Modifier.weight(1f)) { navigate(LauncherSection.SOCIALS) }
            }
        }

        // CENTER stage
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            BedrockCenterDoll(
                modifier = Modifier.fillMaxSize(),
                accent = accent,
                bobOffset = bob,
                onTap = { navigate(LauncherSection.ACCOUNTS) }
            )
        }

        // RIGHT column: top icons + promo
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            BedrockTopIcons(navigate)
            Spacer(Modifier.height(6.dp))
            BedrockPromoCard(
                entered = entered,
                onClick = { navigate(LauncherSection.SOCIALS) }
            )
            Spacer(Modifier.weight(1f))
            // Instance dock is drawn as overlay below, but keep spacer
        }
    }

    // BOTTOM dock — overlay across center + right
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        BedrockBottomDock(
            selected = selected,
            instances = instances,
            accent = accent,
            assetProgress = assetProgress,
            taskCount = taskCount,
            activity = activity,
            navigate = navigate,
            jarLauncher = jarLauncher,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .align(Alignment.BottomCenter)
        )
    }

    // Top-right help FAB
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        BedrockHelpFab(Modifier.padding(end = 12.dp, bottom = 72.dp))
    }
}

@Composable
private fun BedrockCompactLayout(
    activity: FragmentActivity,
    navigate: (LauncherSection) -> Unit,
    instances: List<AerixInstance>,
    selected: AerixInstance?,
    accent: Color,
    assetProgress: Float,
    taskCount: Int,
    entered: Boolean
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BedrockTopIcons(navigate, compact = true)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.18f))
        ) {
            BedrockCenterDoll(modifier = Modifier.fillMaxSize(), accent = accent, bobOffset = 0f, onTap = { navigate(LauncherSection.ACCOUNTS) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BedrockNotchedCard(
                title = "Shop",
                subtitle = "Bonus",
                badge = "!",
                accent = Color(0xFFD11B3A),
                accentDark = Color(0xFF8E0F24),
                iconRes = R.drawable.aerix_ic_package_2_outlined,
                imageTint = Color.White,
                onClick = { navigate(LauncherSection.DISCOVER) },
                entered = entered,
                delayMs = 0,
                modifier = Modifier.weight(1f).height(86.dp)
            )
            BedrockNotchedCard(
                title = "My skin",
                subtitle = null,
                accent = Color(0xFF1E6FE0),
                accentDark = Color(0xFF1349A3),
                iconRes = R.drawable.aerix_ic_person_outlined,
                imageTint = Color.White,
                onClick = { navigate(LauncherSection.ACCOUNTS) },
                entered = entered,
                delayMs = 70,
                modifier = Modifier.weight(1f).height(86.dp),
                showAvatar = true
            )
        }
        BedrockNotchedCard(
            title = "Play with friends",
            subtitle = null,
            accent = Color(0xFF1FA24A),
            accentDark = Color(0xFF12612E),
            iconRes = R.drawable.aerix_ic_people,
            imageTint = Color.White,
            onClick = { navigate(LauncherSection.SOCIALS) },
            entered = entered,
            delayMs = 140,
            modifier = Modifier.fillMaxWidth().height(88.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BedrockPillButton("Find a party", R.drawable.aerix_ic_search, Modifier.weight(1f)) { navigate(LauncherSection.SOCIALS) }
            BedrockPillButton("Invite a friend", R.drawable.aerix_ic_group_outlined, Modifier.weight(1f)) { navigate(LauncherSection.SOCIALS) }
        }
        BedrockPromoCard(entered = entered, onClick = { navigate(LauncherSection.SOCIALS) }, modifier = Modifier.fillMaxWidth())
        BedrockBottomDock(
            selected = selected,
            instances = instances,
            accent = accent,
            assetProgress = assetProgress,
            taskCount = taskCount,
            activity = activity,
            navigate = navigate,
            jarLauncher = null,
            modifier = Modifier.fillMaxWidth()
        )
        BedrockHelpFab(Modifier.align(Alignment.End))
        Spacer(Modifier.height(12.dp))
    }
}

// ---------- Center doll ----------

@Composable
private fun BedrockCenterDoll(
    modifier: Modifier,
    accent: Color,
    bobOffset: Float,
    onTap: () -> Unit
) {
    val account = PojavAccountBridge.current
    var azimuth by remember(account?.username) { mutableFloatStateOf(28f) }
    val scope = rememberCoroutineScope()
    val tracker = remember(account?.username) { VelocityTracker() }
    var flingJob by remember(account?.username) { mutableStateOf<Job?>(null) }

    // idle slow spin when not dragging
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(isDragging, account?.username) {
        if (!isDragging) {
            while (true) {
                kotlinx.coroutines.delay(50)
                if (!isDragging) azimuth = (azimuth + 0.08f).mod(360f)
            }
        }
    }

    Box(
        modifier = modifier
            .pointerInput(account?.username) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        flingJob?.cancel(); flingJob = null
                        tracker.resetTracking()
                    },
                    onDragEnd = {
                        isDragging = false
                        val v = tracker.calculateVelocity().x * DOLL_DRAG_DEG_PER_PX
                        flingJob?.cancel()
                        if (abs(v) < DOLL_FLING_MIN_V) { flingJob = null; return@detectHorizontalDragGestures }
                        flingJob = scope.launch {
                            var vel = v
                            var last = withFrameNanos { it }
                            while (abs(vel) > DOLL_FLING_STOP_V) {
                                ensureActive()
                                val now = withFrameNanos { it }
                                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                                last = now
                                azimuth = (azimuth + vel * dt).mod(360f)
                                vel *= exp(-DOLL_FLING_FRICTION * dt)
                            }
                            flingJob = null
                        }
                    },
                    onDragCancel = { isDragging = false; flingJob?.cancel(); flingJob = null },
                    onHorizontalDrag = { change, amt ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        azimuth = (azimuth + amt * DOLL_DRAG_DEG_PER_PX).mod(360f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // soft stage shadow
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 12.dp)
                .size(width = 180.dp, height = 22.dp)
                .background(Color.Black.copy(alpha = 0.32f), CircleShape)
        )
        AerixSkinPreview3D(
            account = account,
            azimuth = azimuth.roundToInt(),
            interactionEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .offset(y = bobOffset.dp)
                .padding(bottom = 18.dp)
        )
        // subtle tap hint
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-8).dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clickable(onClick = onTap)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = account?.username ?: "+ Add Account",
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
    }
}

// ---------- Notched card ----------

@Composable
fun BedrockNotchedCard(
    title: String,
    subtitle: String?,
    badge: String? = null,
    accent: Color,
    accentDark: Color,
    iconRes: Int,
    imageTint: Color,
    onClick: () -> Unit,
    entered: Boolean,
    delayMs: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showAvatar: Boolean = false
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "press")
    val offsetX by animateIntAsState(
        targetValue = if (entered) 0 else -18,
        animationSpec = tween(420, delayMillis = delayMs, easing = FastOutSlowInEasing), label = "enterX"
    )
    val alpha by animateFloatAsState(
        if (entered) 1f else 0f,
        tween(320, delayMillis = delayMs), label = "alpha"
    )

    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX, 0) }
            .scale(scale)
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(accent, accentDark))
            )
            .border(2.dp, Color.Black.copy(alpha = 0.42f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                // bevel highlight
                val r = 10.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.18f),
                    style = Stroke(1.4.dp.toPx()),
                    cornerRadius = CornerRadius(r, r)
                )
            }
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        // inner
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(accent, accentDark)))
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            // badge
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.5.dp, Color.Black, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("!", color = Color(0xFFD11B3A), fontSize = 12.sp, fontWeight = FontWeight.Black) }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // left avatar/icon thumb
                Box(
                    modifier = Modifier
                        .size(if (compact) 44.dp else 46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.22f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (showAvatar) {
                        val acc = PojavAccountBridge.current
                        AerixImageWell(
                            image = acc?.face,
                            fallback = R.drawable.aerix_head_steve,
                            size = 32.dp,
                            radius = 6.dp
                        )
                    } else {
                        Icon(painterResource(iconRes), contentDescription = null, tint = imageTint, modifier = Modifier.size(26.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.2.sp, maxLines = 1)
                    if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp, maxLines = 1)
                }
                if (compact) Icon(painterResource(R.drawable.aerix_ic_chevron_right), null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

// Custom notched/beveled shape mimicking Bedrock cards — bottom edge has a stepped notch.
private class BedrockCardShape(
    private val notchDepth: Dp,
    private val radius: Dp
) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val r = with(density) { radius.toPx() }
        val notch = with(density) { notchDepth.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            // top-left rounded
            moveTo(0f, r)
            quadraticBezierTo(0f, 0f, r, 0f)
            lineTo(w - r, 0f)
            quadraticBezierTo(w, 0f, w, r)
            lineTo(w, h - notch - r)
            quadraticBezierTo(w, h - notch, w - r, h - notch)
            lineTo(r + notch, h - notch)
            lineTo(r + notch, h)
            lineTo(r, h)
            quadraticBezierTo(0f, h, 0f, h - r)
            close()
        }
        return Outline.Generic(path)
    }
}

// ---------- Top icons ----------

@Composable
private fun BedrockTopIcons(navigate: (LauncherSection) -> Unit, compact: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val items = listOf(
            Triple(R.drawable.aerix_ic_person_outlined, "Profile") { navigate(LauncherSection.ACCOUNTS) },
            Triple(R.drawable.aerix_ic_person_outlined, "Skin") { navigate(LauncherSection.ACCOUNTS) },
            Triple(R.drawable.aerix_ic_favorite_outlined, "Gift") { navigate(LauncherSection.DISCOVER) },
            Triple(R.drawable.aerix_ic_settings_filled, "Settings") { navigate(LauncherSection.SETTINGS) },
        )
        items.forEach { (icon, cd, action) ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .clickable(onClick = action),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(icon), contentDescription = cd, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ---------- Promo card ----------

@Composable
private fun BedrockPromoCard(entered: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val offsetX by animateIntAsState(if (entered) 0 else 22, tween(420, delayMillis = 180, easing = FastOutSlowInEasing), label = "promoX")
    val alpha by animateFloatAsState(if (entered) 1f else 0f, tween(300, delayMillis = 180), label = "promoA")
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .offset { IntOffset(offsetX, 0) }
            .clip(shape)
            .background(Color(0xFF101214))
            .border(1.5.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("+1 D OF PLUS FOR EVERY FRIEND", color = Color(0xFF9AA0A6), fontSize = 8.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold)
            Text("FREE PLUS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, lineHeight = 18.sp)
            Text("FOR A FRIEND", color = Color(0xFFFFC107), fontSize = 18.sp, fontWeight = FontWeight.Black, lineHeight = 18.sp)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFFC107))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) { Text("M PLUS", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

// ---------- Pill button ----------

@Composable
private fun BedrockPillButton(text: String, icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "pill")
    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1E21))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(painterResource(icon), null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(14.dp))
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ---------- Bottom dock ----------

@Composable
private fun BedrockBottomDock(
    selected: AerixInstance?,
    instances: List<AerixInstance>,
    accent: Color,
    assetProgress: Float,
    taskCount: Int,
    activity: FragmentActivity,
    navigate: (LauncherSection) -> Unit,
    jarLauncher: androidx.activity.result.ActivityResultLauncher<Any?>?,
    modifier: Modifier
) {
    val vanilla = stringResource(R.string.aerix_home_vanilla)
    val shape = RoundedCornerShape(14.dp)
    val isDownloading = taskCount > 0 || assetProgress in 0.01f..0.99f
    // Use taskCount as source of truth for bar vs play toggle (matches screenshots: 93% -> Play)
    val showPlay = !isDownloading || assetProgress >= 1f

    Row(
        modifier = modifier
            .clip(shape)
            .background(Color(0xE6141A1F))
            .border(1.5.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // LEFT: instance info (blue)
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1B5ECC),
            contentColor = Color.White,
            onClick = { selected?.let { PojavInstanceBridge.edit(activity, it) } ?: navigate(LauncherSection.LIBRARY) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F3A8A))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AerixImageWell(
                        image = selected?.icon,
                        fallback = R.drawable.aerix_ic_videogame_asset_outlined,
                        size = 28.dp,
                        radius = 6.dp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("START HERE", color = Color.White.copy(alpha = 0.84f), fontSize = 8.sp, letterSpacing = 0.7.sp, fontWeight = FontWeight.Bold)
                    Text(
                        selected?.name ?: "Modpack 1.20.1",
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        selected?.let { "${loaderLabel(it.versionId, vanilla)} · ${GameDownloadTarget.minecraftVersionIn(it.versionId) ?: it.versionId}" } ?: "Fabric · 1.20.1",
                        color = Color.White.copy(alpha = 0.92f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(painterResource(R.drawable.aerix_ic_chevron_right), null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
            }
        }

        // RIGHT: assets/play + yellow Open chest (stacked, matches screenshots)
        Column(
            modifier = Modifier.width(184.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showPlay) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF4CAF50),
                    contentColor = Color.White,
                    onClick = {
                        if (selected != null) PojavLaunchBridge.play(activity) else navigate(LauncherSection.CREATE_INSTANCE)
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(painterResource(R.drawable.aerix_ic_play_arrow_filled), null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Text("Play", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        }
                        Text("0 h", color = Color.Black.copy(alpha = 0.78f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF2E7D32)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Assets", color = Color.White.copy(alpha = 0.92f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("‥ ${ (assetProgress * 100).toInt() }%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black)
                                    .clickable { /* dismiss */ },
                                contentAlignment = Alignment.Center
                            ) { Text("×", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))
                        ) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(assetProgress).background(Color.White))
                        }
                    }
                }
            }
            // Yellow Open chest — directly under Play/Assets, same width
            Surface(
                modifier = Modifier.fillMaxWidth().height(30.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFD54F),
                contentColor = Color(0xFF3E2723),
                onClick = { selected?.let { PojavInstanceBridge.edit(activity, it) } ?: navigate(LauncherSection.FILES) }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(painterResource(R.drawable.aerix_ic_videogame_asset_outlined), null, tint = Color(0xFF5D4037), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open chest", color = Color(0xFF3E2723), fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun BedrockHelpFab(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1E21))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(R.drawable.aerix_ic_info_outlined), null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text("Help", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// Inline Open chest pill rendered below dock (Bedrock has yellow bar)
@Composable
fun BedrockOpenChestPill(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFD54F))
            .border(1.5.dp, Color(0xFF8D6E00), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(painterResource(R.drawable.aerix_ic_videogame_asset_outlined), null, tint = Color(0xFF5D4037), modifier = Modifier.size(16.dp))
        Text("Open chest", color = Color(0xFF3E2723), fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

private fun activeTaskProgress(taskCount: Int): Float? {
    // PojavTaskBridge doesn't expose percent; approximate by mapping count to pulse
    return if (taskCount > 0) 0.42f else null
}
