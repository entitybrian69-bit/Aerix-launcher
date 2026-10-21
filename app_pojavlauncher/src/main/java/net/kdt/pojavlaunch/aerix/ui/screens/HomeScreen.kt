/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/LauncherScreen.kt
 *     (LauncherScreen, ActionMenuCardContent, VersionsContent)
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/home/MiraiHomeDashboard.kt
 *     (MiraiHomeDashboard, WideHomeDashboard, CompactHomeDashboard, LiquidHomeHero, ActiveWorldPanel,
 *      TaskSummaryPanel, RecentWorldsPanel, RecentWorldRow, QuickToolsPanel, HomeToolAction)
 * Data comes from PojavInstanceBridge / PojavAccountBridge / PojavTaskBridge; PLAY goes through PojavLaunchBridge.
 * Zalith Launcher 2 Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixInstance
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.bridge.PojavTaskBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D
import net.kdt.pojavlaunch.aerix.components.MetaPill
import net.kdt.pojavlaunch.aerix.components.QuickActionTile
import net.kdt.pojavlaunch.aerix.components.QuietEmptyState
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.aerix.ui.screens.social.SocialAvatar
import net.kdt.pojavlaunch.aerix.ui.screens.social.SocialHubStore
import net.kdt.pojavlaunch.aerix.ui.screens.social.SocialPalette
import net.kdt.pojavlaunch.aerix.ui.screens.social.SocialShellState
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private const val ContentWeight = 7.4f
private const val ActionMenuWeight = 2.6f
private val ActionMenuOuterPadding = 12.dp

/** Degrees of doll rotation per pixel of horizontal drag (mirai LauncherScreen). */
private const val DOLL_DRAG_DEG_PER_PX = 0.45f
private const val DOLL_FLING_FRICTION = 5f
private const val DOLL_FLING_MIN_VELOCITY = 60f
private const val DOLL_FLING_STOP_VELOCITY = 20f

private val mcVersionRegex = Regex("""(\d+\.\d+(?:\.\d+)?)""")

private class HomeActions(
    val play: () -> Unit,
    val playInstance: (AerixInstance) -> Unit,
    val select: (AerixInstance) -> Unit,
    val edit: (AerixInstance) -> Unit,
    val create: () -> Unit,
    val explore: () -> Unit,
    val library: () -> Unit,
    val files: () -> Unit,
    val controls: () -> Unit,
    val runJar: () -> Unit,
    val shareLogs: () -> Unit,
    val gamepad: () -> Unit,
    val content: () -> Unit,
    val downloads: () -> Unit,
    val fileManager: () -> Unit,
    val logs: () -> Unit,
    val accounts: () -> Unit,
    val social: () -> Unit
)

@Composable
fun HomeScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val instances = PojavInstanceBridge.instances
    val selected = PojavInstanceBridge.selected
    val taskCount = PojavTaskBridge.taskCount
    val accent = AerixThemeManager.currentAccent()
    val recent = remember(instances, selected) {
        instances.filterNot { it == selected }.take(8).ifEmpty { instances.take(8) }
    }
    val jarLauncher = rememberLauncherForActivityResult(OpenDocumentWithExtension("jar")) { uri ->
        if (uri != null) PojavNavigator.runJar(activity, uri)
    }
    val actions = HomeActions(
        play = { PojavLaunchBridge.play(activity) },
        playInstance = { instance ->
            PojavInstanceBridge.select(instance)
            PojavLaunchBridge.play(activity)
        },
        select = { PojavInstanceBridge.select(it) },
        edit = { PojavInstanceBridge.edit(activity, it) },
        create = { AerixNav.navigate(LauncherSection.CREATE_INSTANCE) },
        explore = { navigate(LauncherSection.DISCOVER) },
        library = { navigate(LauncherSection.LIBRARY) },
        files = { PojavInstanceBridge.openGameDirectory(activity) },
        controls = { PojavNavigator.openCustomControls(activity) },
        runJar = { if (PojavNavigator.canRunJar(activity)) jarLauncher.launch(null) },
        shareLogs = { PojavNavigator.shareLogs(activity) },
        gamepad = { PojavNavigator.openGamepadMapper(activity) },
        content = {
            selected?.let { PojavInstanceBridge.edit(activity, it) } ?: navigate(LauncherSection.LIBRARY)
        },
        downloads = { navigate(LauncherSection.DISCOVER) },
        fileManager = { navigate(LauncherSection.FILES) },
        logs = { navigate(LauncherSection.LOGS) },
        accounts = { navigate(LauncherSection.ACCOUNTS) },
        // The one launcher-side way into Social: the dashboard widget opens the shell.
        social = { SocialShellState.enter() }
    )

    // Mirai LauncherScreen: content dashboard on the left (weight 7.4f) and the docked 3D skin
    // ActionMenu card on the right (weight 2.6f) whenever maxWidth >= 500.dp.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val showActionMenu = maxWidth >= 500.dp
        Row(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(if (showActionMenu) ContentWeight else 1f)
                    .fillMaxHeight()
                    .padding(start = AerixSpacing.mdPlus, end = AerixSpacing.smCompact, top = AerixSpacing.smPlus)
            ) {
                if (maxWidth >= 720.dp && maxWidth > maxHeight) {
                    WideHomeDashboard(selected, recent, instances.size, accent, taskCount, actions)
                } else {
                    CompactHomeDashboard(selected, recent, instances.size, accent, taskCount, actions)
                }
            }
            if (showActionMenu) {
                ActionMenuCard(
                    selected = selected,
                    instances = instances,
                    actions = actions,
                    modifier = Modifier
                        .weight(ActionMenuWeight)
                        .fillMaxHeight()
                        .padding(
                            top = ActionMenuOuterPadding,
                            bottom = ActionMenuOuterPadding,
                            end = ActionMenuOuterPadding
                        )
                )
            }
        }
    }
}

/**
 * Right-hand ActionMenu card from Mirai's LauncherScreen.kt: 3D rotatable skin stage on a dark
 * pedestal with the username pill, the two-line instance selector pill and the PLAY button.
 */
@Composable
private fun ActionMenuCard(
    selected: AerixInstance?,
    instances: List<AerixInstance>,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    val account = PojavAccountBridge.current
    val accent = AerixThemeManager.currentAccent()
    val vanilla = stringResource(R.string.aerix_home_vanilla)
    var dollAzimuth by remember(account?.username) { mutableFloatStateOf(28f) }
    val flingScope = rememberCoroutineScope()
    val dollVelocityTracker = remember(account?.username) { VelocityTracker() }
    var dollFlingJob by remember(account?.username) { mutableStateOf<Job?>(null) }
    var showInstanceMenu by remember { mutableStateOf(false) }

    val versionSubtitle = remember(selected, vanilla) {
        if (selected == null) "Select Instance"
        else {
            val loader = loaderLabel(selected.versionId, vanilla)
            val mcVer = mcVersionRegex.find(selected.versionId)?.value ?: selected.versionId
            if (loader == vanilla) "Minecraft $mcVer" else "$loader $mcVer"
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AerixRadii.card),
        color = AerixSurface.panel,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 3D Skin & Cape Stage on Dark Pedestal (Mirai LauncherScreen ActionMenuCardContent)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = AerixSpacing.smCompact, start = AerixSpacing.sm, end = AerixSpacing.sm)
                    .clip(RoundedCornerShape(AerixRadii.control))
                    .pointerInput(actions) {
                        detectTapGestures(onTap = { actions.accounts() })
                    }
                    .pointerInput(account?.username) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dollFlingJob?.cancel()
                                dollFlingJob = null
                                dollVelocityTracker.resetTracking()
                            },
                            onDragEnd = {
                                val releaseVelocity = dollVelocityTracker.calculateVelocity().x * DOLL_DRAG_DEG_PER_PX
                                dollFlingJob?.cancel()
                                if (abs(releaseVelocity) < DOLL_FLING_MIN_VELOCITY) {
                                    dollFlingJob = null
                                    return@detectHorizontalDragGestures
                                }
                                dollFlingJob = flingScope.launch {
                                    var velocity = releaseVelocity
                                    var lastFrame = withFrameNanos { it }
                                    while (abs(velocity) > DOLL_FLING_STOP_VELOCITY) {
                                        ensureActive()
                                        val now = withFrameNanos { it }
                                        val dt = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                                        lastFrame = now
                                        dollAzimuth = (dollAzimuth + velocity * dt).mod(360f)
                                        velocity *= exp(-DOLL_FLING_FRICTION * dt)
                                    }
                                    dollFlingJob = null
                                }
                            },
                            onDragCancel = {
                                dollFlingJob?.cancel()
                                dollFlingJob = null
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                dollVelocityTracker.addPosition(change.uptimeMillis, change.position)
                                dollAzimuth = (dollAzimuth + dragAmount * DOLL_DRAG_DEG_PER_PX).mod(360f)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AerixSkinPreview3D(
                    account = account,
                    azimuth = dollAzimuth.roundToInt(),
                    interactionEnabled = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = AerixSpacing.mdPlus)
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-4).dp),
                    shape = RoundedCornerShape(AerixRadii.compact),
                    color = AerixSurface.canvas,
                    border = BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = account?.username ?: "+ Add Account",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (account != null) Color.White else accent,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.tiny)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                // Compact 2-line Instance Selector Pill (Mirai VersionsContent)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AerixSpacing.smPlus)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AerixRadii.cardSmall),
                        color = AerixSurface.panel,
                        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
                        onClick = {
                            if (instances.isNotEmpty()) showInstanceMenu = true else actions.library()
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.smTight),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
                        ) {
                            Text(
                                text = selected?.name ?: stringResource(R.string.aerix_home_no_active_world),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = versionSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showInstanceMenu,
                        onDismissRequest = { showInstanceMenu = false }
                    ) {
                        instances.forEach { instance ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                                    ) {
                                        AerixImageWell(
                                            image = instance.icon,
                                            fallback = R.drawable.aerix_ic_videogame_asset_outlined,
                                            size = 24.dp,
                                            radius = AerixRadii.tiny
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(instance.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text(instance.versionId, style = MaterialTheme.typography.labelSmall, color = AerixSurface.textSecondary, maxLines = 1)
                                        }
                                    }
                                },
                                onClick = {
                                    actions.select(instance)
                                    showInstanceMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Manage All Instances...",
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            onClick = {
                                showInstanceMenu = false
                                actions.library()
                            }
                        )
                    }
                }

                // Vibrant Pill PLAY Button (Mirai VersionsContent)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AerixSpacing.smPlus)
                        .padding(bottom = AerixSpacing.smPlus)
                        .height(42.dp),
                    shape = RoundedCornerShape(AerixRadii.panel),
                    color = accent,
                    contentColor = AerixSurface.onAccent,
                    onClick = if (selected != null) actions.play else actions.create
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "PLAY",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = AerixSurface.onAccent
                        )
                        Spacer(Modifier.width(AerixSpacing.xs))
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_play_arrow_filled),
                            contentDescription = stringResource(R.string.aerix_home_play),
                            tint = AerixSurface.onAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WideHomeDashboard(
    selected: AerixInstance?,
    recent: List<AerixInstance>,
    count: Int,
    accent: Color,
    taskCount: Int,
    actions: HomeActions
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = AerixSpacing.smPlus),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.57f),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
        ) {
            LiquidHomeHero(selected, count, accent, actions, Modifier.weight(1.55f).fillMaxHeight())
            Column(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
            ) {
                ActiveWorldPanel(selected, accent, actions, Modifier.weight(1f))
                SocialWidgetCard(modifier = Modifier.fillMaxWidth().height(84.dp), onClick = actions.social)
                TaskSummaryPanel(taskCount, accent, Modifier.height(74.dp))
            }
        }
        QuickTiles(count, accent, actions, Modifier.fillMaxWidth().height(78.dp), scrollable = false)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.43f),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
        ) {
            RecentWorldsPanel(recent, selected, accent, actions, Modifier.weight(1.7f).fillMaxHeight(), lazy = true)
            QuickToolsPanel(accent, actions, Modifier.weight(0.72f).fillMaxHeight(), scrollable = true)
        }
    }
}

@Composable
private fun CompactHomeDashboard(
    selected: AerixInstance?,
    recent: List<AerixInstance>,
    count: Int,
    accent: Color,
    taskCount: Int,
    actions: HomeActions
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        item { LiquidHomeHero(selected, count, accent, actions, Modifier.fillMaxWidth().height(312.dp)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                HomeSectionLabel(stringResource(R.string.aerix_home_your_space), null)
                QuickTiles(count, accent, actions, Modifier.fillMaxWidth(), scrollable = true)
            }
        }
        item { ActiveWorldPanel(selected, accent, actions, Modifier.fillMaxWidth().height(190.dp)) }
        item { SocialWidgetCard(modifier = Modifier.fillMaxWidth().height(84.dp), onClick = actions.social) }
        item { TaskSummaryPanel(taskCount, accent, Modifier.fillMaxWidth().height(74.dp)) }
        item { RecentWorldsPanel(recent, selected, accent, actions, Modifier.fillMaxWidth(), lazy = false) }
        item { QuickToolsPanel(accent, actions, Modifier.fillMaxWidth(), scrollable = false) }
    }
}

@Composable
private fun QuickTiles(count: Int, accent: Color, actions: HomeActions, modifier: Modifier, scrollable: Boolean) {
    val rowModifier = if (scrollable) modifier.horizontalScroll(rememberScrollState()) else modifier
    Row(modifier = rowModifier, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)) {
        val tiles = listOf(
            Triple(R.drawable.aerix_ic_add, stringResource(R.string.aerix_home_quick_create) to stringResource(R.string.aerix_home_quick_create_sub), accent to actions.create),
            Triple(R.drawable.aerix_ic_search, stringResource(R.string.aerix_home_quick_explore) to stringResource(R.string.aerix_home_quick_explore_sub), AerixSurface.glassBlue to actions.explore),
            Triple(R.drawable.aerix_ic_dashboard_filled, stringResource(R.string.aerix_home_quick_library) to stringResource(R.string.aerix_home_quick_library_sub, count), AerixSurface.glassViolet to actions.library),
            Triple(R.drawable.aerix_ic_folder_outlined, stringResource(R.string.aerix_home_quick_files) to stringResource(R.string.aerix_home_quick_files_sub), AerixSurface.glassRose to actions.fileManager)
        )
        tiles.forEach { (icon, texts, style) ->
            QuickActionTile(
                iconRes = icon,
                title = texts.first,
                subtitle = texts.second,
                accent = style.first,
                onClick = style.second,
                modifier = if (scrollable) Modifier.width(198.dp).height(78.dp) else Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun LiquidHomeHero(selected: AerixInstance?, count: Int, accent: Color, actions: HomeActions, modifier: Modifier) {
    val shape = RoundedCornerShape(AerixRadii.hero)
    Box(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderHighlight.copy(alpha = 0.62f)), shape)
    ) {
        Image(
            painter = painterResource(R.drawable.aerix_hero_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val width = size.width.coerceAtLeast(1f)
                    val height = size.height.coerceAtLeast(1f)
                    val longEdge = maxOf(width, height)
                    val center = Offset(width * 0.92f, height * 0.1f)
                    val inset = 30.dp.toPx()
                    val rimWidth = 1.dp.toPx()
                    val wash = Brush.horizontalGradient(
                        colors = listOf(
                            AerixSurface.canvas.copy(alpha = 0.90f),
                            AerixSurface.canvas.copy(alpha = 0.82f),
                            AerixSurface.canvas.copy(alpha = 0.30f),
                            AerixSurface.canvas.copy(alpha = 0.07f)
                        ),
                        startX = 0f,
                        endX = width
                    )
                    val lens = Brush.radialGradient(
                        colors = listOf(AerixSurface.glassBlue.copy(alpha = 0.36f), AerixSurface.glassViolet.copy(alpha = 0.12f), Color.Transparent),
                        center = center,
                        radius = longEdge * 0.72f
                    )
                    onDrawBehind {
                        drawRect(brush = wash)
                        drawCircle(brush = lens, radius = longEdge * 0.72f, center = center)
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(inset, rimWidth * 0.5f),
                            end = Offset(width - inset, rimWidth * 0.5f),
                            strokeWidth = rimWidth
                        )
                    }
                }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AerixSpacing.xxl, vertical = AerixSpacing.xl),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(AerixSurface.success))
                Text(
                    text = stringResource(if (selected != null) R.string.aerix_home_ready else R.string.aerix_home_empty),
                    color = AerixSurface.textPrimary.copy(alpha = 0.88f),
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.fillMaxWidth(0.76f), verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                Text(
                    text = stringResource(if (selected != null) R.string.aerix_home_ready_title else R.string.aerix_home_empty_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    lineHeight = 42.sp,
                    maxLines = 2
                )
                Text(
                    text = stringResource(if (selected != null) R.string.aerix_home_ready_sub else R.string.aerix_home_empty_sub),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = AerixSurface.textPrimary.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = AerixSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetaPill(text = selected?.name ?: stringResource(R.string.aerix_home_quick_library_sub, count), iconRes = R.drawable.aerix_ic_dashboard_filled)
                    if (selected != null) {
                        MetaPill(
                            text = loaderLabel(selected.versionId, stringResource(R.string.aerix_home_vanilla)),
                            iconRes = R.drawable.aerix_ic_videogame_asset_outlined
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .height(46.dp)
                        .liquidGlass(shape = CircleShape, tint = accent, strength = 0.92f, elevation = AerixMetrics.glassSelectedElevation),
                    shape = CircleShape,
                    color = accent,
                    contentColor = AerixSurface.onAccent,
                    shadowElevation = 0.dp,
                    onClick = if (selected != null) actions.play else actions.create
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AerixSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                    ) {
                        Icon(
                            painter = painterResource(if (selected != null) R.drawable.aerix_ic_play_arrow_filled else R.drawable.aerix_ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(if (selected != null) R.string.aerix_home_play_now else R.string.aerix_home_create_world),
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.aerix_home_open_library),
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = actions.library)
                        .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.smPlus)
                )
            }
        }
    }
}

/**
 * The one Play-dashboard widget for Social: online friend count and faces, in the fixed
 * Social purple (never the launcher accent). Tapping it opens the Social shell; there is
 * no Social entry in the launcher rail.
 */
@Composable
private fun SocialWidgetCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = SocialPalette.accent
    val online = SocialHubStore.onlineCount
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AerixRadii.card),
        color = AerixSurface.panel,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.sm),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(AerixRadii.controlSmall))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_group_outlined),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(AerixSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Social",
                        color = AerixSurface.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$online friends online",
                        color = AerixSurface.textSecondary,
                        fontSize = 11.sp
                    )
                }
                Row {
                    SocialHubStore.friends.filter { it.online }.take(3).forEachIndexed { index, friend ->
                        Box(modifier = Modifier.offset(x = if (index == 0) 0.dp else (-8).dp)) {
                            SocialAvatar(size = 26, name = friend.name)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open social",
                    color = AerixSurface.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("›", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HomeSectionLabel(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = title.uppercase(), color = AerixSurface.textPrimary, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.ExtraBold)
        if (subtitle != null) {
            Text(text = subtitle, color = AerixSurface.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActiveWorldPanel(selected: AerixInstance?, accent: Color, actions: HomeActions, modifier: Modifier) {
    val shape = RoundedCornerShape(AerixRadii.panel)
    val vanilla = stringResource(R.string.aerix_home_vanilla)
    Surface(
        modifier = modifier.liquidGlass(shape = shape, tint = accent, strength = 0.82f, elevation = AerixMetrics.glassSubtleElevation),
        shape = shape,
        color = Color.Transparent,
        contentColor = AerixSurface.textPrimary,
        border = BorderStroke(1.dp, AerixSurface.borderSoft)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AerixSpacing.mdPlus),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            HomeSectionLabel(stringResource(R.string.aerix_home_active_world), stringResource(R.string.aerix_home_active_sub))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
            ) {
                AerixImageWell(image = selected?.icon, fallback = R.drawable.aerix_ic_videogame_asset_outlined, size = 58.dp, radius = AerixRadii.card)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                    Text(
                        text = selected?.name ?: stringResource(R.string.aerix_home_no_active_world),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = selected?.let { "${loaderLabel(it.versionId, vanilla)}  ·  ${stringResource(R.string.aerix_home_minecraft, it.versionId)}" }
                            ?: stringResource(R.string.aerix_home_create_first),
                        color = AerixSurface.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .liquidGlass(shape = CircleShape, tint = accent, strength = 0.9f, elevation = AerixMetrics.glassSubtleElevation),
                    shape = CircleShape,
                    color = accent,
                    contentColor = AerixSurface.onAccent,
                    onClick = if (selected != null) actions.play else actions.create
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AerixSpacing.md),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(if (selected != null) R.drawable.aerix_ic_play_arrow_filled else R.drawable.aerix_ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(AerixSpacing.xs))
                        Text(
                            text = stringResource(if (selected != null) R.string.aerix_home_play else R.string.aerix_home_create),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                IconButton(
                    onClick = { selected?.let { actions.edit(it) } },
                    enabled = selected != null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AerixSurface.panelTrack)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_settings_filled),
                        contentDescription = stringResource(R.string.aerix_home_instance_settings),
                        tint = if (selected != null) AerixSurface.textPrimary else AerixSurface.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskSummaryPanel(taskCount: Int, accent: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(AerixRadii.panelSmall)
    Surface(
        modifier = modifier.liquidGlass(
            shape = shape,
            tint = if (taskCount > 0) accent else AerixSurface.glassBlue,
            strength = 0.5f,
            elevation = AerixMetrics.glassSubtleElevation
        ),
        shape = shape,
        color = Color.Transparent,
        contentColor = AerixSurface.textPrimary,
        border = BorderStroke(1.dp, AerixSurface.borderSoft)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AerixSpacing.mdPlus),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(AerixRadii.control))
                    .background((if (taskCount > 0) accent else AerixSurface.glassBlue).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_schedule_outlined),
                    contentDescription = null,
                    tint = if (taskCount > 0) accent else AerixSurface.glassBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.aerix_home_tasks), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
                Text(
                    text = if (taskCount > 0) stringResource(R.string.aerix_home_tasks_running, taskCount) else stringResource(R.string.aerix_home_tasks_idle),
                    fontSize = 11.sp,
                    color = AerixSurface.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RecentWorldsPanel(
    recent: List<AerixInstance>,
    selected: AerixInstance?,
    accent: Color,
    actions: HomeActions,
    modifier: Modifier,
    lazy: Boolean
) {
    val shape = RoundedCornerShape(AerixRadii.panel)
    Surface(
        modifier = modifier.liquidGlass(shape = shape, strength = 0.6f, elevation = AerixMetrics.glassSubtleElevation),
        shape = shape,
        color = AerixSurface.panel,
        contentColor = AerixSurface.textPrimary,
        border = BorderStroke(1.dp, AerixSurface.borderSoft)
    ) {
        Column(modifier = Modifier.padding(AerixSpacing.mdPlus), verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeSectionLabel(stringResource(R.string.aerix_home_recent), stringResource(R.string.aerix_home_recent_sub), Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.aerix_home_view_all).uppercase(),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = actions.library)
                        .padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.xs)
                )
            }
            if (recent.isEmpty()) {
                QuietEmptyState(R.drawable.aerix_ic_videogame_asset_outlined, stringResource(R.string.aerix_library_empty), Modifier.fillMaxWidth())
            } else if (lazy) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                    items(recent) { instance ->
                        RecentWorldRow(instance, instance == selected, accent, actions)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                    recent.forEach { instance -> RecentWorldRow(instance, instance == selected, accent, actions) }
                }
            }
        }
    }
}

@Composable
private fun RecentWorldRow(instance: AerixInstance, isSelected: Boolean, accent: Color, actions: HomeActions) {
    val shape = RoundedCornerShape(AerixRadii.control)
    val vanilla = stringResource(R.string.aerix_home_vanilla)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) accent.copy(alpha = 0.14f) else AerixSurface.panelRaised.copy(alpha = 0.35f))
            .clickable(role = Role.Button) { actions.select(instance) }
            .padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
    ) {
        AerixImageWell(image = instance.icon, fallback = R.drawable.aerix_ic_videogame_asset_outlined, size = 36.dp, radius = AerixRadii.controlSmall)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = instance.name, color = AerixSurface.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${loaderLabel(instance.versionId, vanilla)} · ${instance.versionId}",
                color = AerixSurface.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { actions.edit(instance) }, modifier = Modifier.size(32.dp)) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_edit_outlined),
                contentDescription = stringResource(R.string.aerix_home_instance_settings),
                tint = AerixSurface.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(onClick = { actions.playInstance(instance) }, modifier = Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f))) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_play_arrow_filled),
                contentDescription = stringResource(R.string.aerix_home_play),
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun QuickToolsPanel(accent: Color, actions: HomeActions, modifier: Modifier, scrollable: Boolean) {
    val shape = RoundedCornerShape(AerixRadii.panel)
    Surface(
        modifier = modifier.liquidGlass(shape = shape, strength = 0.6f, elevation = AerixMetrics.glassSubtleElevation),
        shape = shape,
        color = AerixSurface.panel,
        contentColor = AerixSurface.textPrimary,
        border = BorderStroke(1.dp, AerixSurface.borderSoft)
    ) {
        val padded = Modifier.padding(AerixSpacing.mdPlus)
        Column(
            modifier = if (scrollable) padded.verticalScroll(rememberScrollState()) else padded,
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
        ) {
            HomeSectionLabel(stringResource(R.string.aerix_home_quick_tools), null)
            HomeToolAction(stringResource(R.string.aerix_download_title), R.drawable.aerix_ic_download, AerixSurface.modrinthBrand, actions.downloads)
            HomeToolAction(stringResource(R.string.aerix_content_title), R.drawable.aerix_ic_extension_outlined, AerixSurface.success, actions.content)
            HomeToolAction(stringResource(R.string.mcl_option_customcontrol), R.drawable.aerix_ic_touch_app_outlined, accent, actions.controls)
            HomeToolAction(stringResource(R.string.main_install_jar_file), R.drawable.aerix_ic_java, AerixSurface.glassBlue, actions.runJar)
            HomeToolAction(stringResource(R.string.aerix_logs_title), R.drawable.aerix_ic_terminal_outlined, AerixSurface.warning, actions.logs)
            HomeToolAction(stringResource(R.string.main_share_logs), R.drawable.aerix_ic_share_filled, AerixSurface.glassViolet, actions.shareLogs)
            HomeToolAction(stringResource(R.string.aerix_files_title), R.drawable.aerix_ic_folder_filled, AerixSurface.glassBlue, actions.fileManager)
            HomeToolAction(stringResource(R.string.mcl_button_open_directory), R.drawable.aerix_ic_folder_outlined, AerixSurface.glassRose, actions.files)
            HomeToolAction(stringResource(R.string.preference_remap_controller_title), R.drawable.aerix_ic_sports_esports_outlined, AerixSurface.auroraCyan, actions.gamepad)
        }
    }
}

@Composable
private fun HomeToolAction(label: String, iconRes: Int, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AerixRadii.control))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AerixSpacing.xs, vertical = AerixSpacing.smCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(AerixRadii.compact))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Text(text = label, color = AerixSurface.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
