/*
 * Aerix Launcher in-game menu.
 *
 * A full screen overlay in the style of the desktop client overlays: the game keeps running behind
 * a darkened scrim while the entries are laid out as large tiles. Every colour is mixed from the
 * accent of the current launcher theme, so the menu looks like the rest of the launcher and
 * changes with it.
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

package net.kdt.pojavlaunch.aerix.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixAccount
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D
import net.kdt.pojavlaunch.aerix.bridge.GameDownloadTarget
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.authenticator.AuthType
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.loading.AerixLoadingWallpaper

/** The pages of the in-game menu. */
enum class GameMenuPage { MAIN, DOWNLOADS }

/** Everything the menu asks the game activity to do. */
interface GameMenuCallbacks {
    fun onScreenRecorder()
    fun onResumeGame()
    fun onForceCloseGame()
    fun onLogOutput()
    fun onSendCustomKey()
    fun onQuickSettings()
    fun onCustomControls()
    /** Opens the system dialog that gives the launcher access to the instance storage. */
    fun onRequestStoragePermission()
}

/** Colours of the menu, all derived from the accent of the current launcher theme. */
private class GameMenuPalette(val accent: Color, val tiles: List<Color>) {
    val resume: Color get() = accent
    val scrim: Color get() = AerixSurface.canvas.copy(alpha = 0.93f)
}

@Composable
private fun rememberGameMenuPalette(): GameMenuPalette {
    val scheme = MaterialTheme.colorScheme
    val accent = AerixThemeManager.currentAccent()
    return remember(accent, scheme) {
        GameMenuPalette(
            accent = accent,
            tiles = listOf(
                accent,                                    // custom controls
                lerp(accent, scheme.secondary, 0.8f),      // send custom keycode
                lerp(accent, scheme.tertiary, 0.55f),      // quick settings
                lerp(accent, Color.White, 0.2f),           // in-game downloads
                lerp(accent, scheme.secondary, 0.4f),      // log output
                lerp(accent, scheme.error, 0.9f)           // force close game
            )
        )
    }
}

/**
 * The overlay itself.
 *
 * @param revision bumped by the host every time the menu is opened, so it animates in again
 * @param target instance the game runs, used by the downloader page
 * @param versionLine version of the launcher, shown in the bottom bar
 * @param onBackHandlerChanged receives the handler of the back button while the menu is on screen
 * @param onCloseRequest closes the menu
 */
@Composable
fun GameMenuScreen(
    callbacks: GameMenuCallbacks,
    revision: Int,
    target: GameDownloadTarget,
    versionLine: String,
    onBackHandlerChanged: ((() -> Unit)?) -> Unit,
    onCloseRequest: () -> Unit
) {
    val context = LocalContext.current
    val palette = rememberGameMenuPalette()
    val account = remember { PojavAccountBridge.current }
    var page by remember { mutableStateOf(GameMenuPage.MAIN) }
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(revision) {
        page = GameMenuPage.MAIN
        shown = false
        shown = true
    }

    // The back button walks out of the downloader before it closes the menu.
    DisposableEffect(page) {
        onBackHandlerChanged {
            if (page == GameMenuPage.DOWNLOADS) page = GameMenuPage.MAIN else onCloseRequest()
        }
        onDispose { onBackHandlerChanged(null) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = {}
            )
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.97f),
            exit = fadeOut(tween(120))
        ) {
            when (page) {
                GameMenuPage.MAIN -> GameMenuMainPage(
                    callbacks = callbacks,
                    palette = palette,
                    account = account,
                    versionLine = versionLine,
                    onCloseRequest = onCloseRequest,
                    onOpenDownloads = { page = GameMenuPage.DOWNLOADS }
                )
                GameMenuPage.DOWNLOADS -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AerixSpacing.mdPlus, vertical = AerixSpacing.smPlus)
                ) {
                    GameDownloadsScreen(
                        target = target,
                        accent = palette.accent,
                        onBack = { page = GameMenuPage.MAIN },
                        onStorageProblem = { callbacks.onRequestStoragePermission() }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameMenuMainPage(
    callbacks: GameMenuCallbacks,
    palette: GameMenuPalette,
    account: AerixAccount?,
    versionLine: String,
    onCloseRequest: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxHeight < 480.dp
        val columnWidth = maxWidth.coerceAtMost(980.dp)
        Column(
            modifier = Modifier
                .width(columnWidth)
                .padding(horizontal = AerixSpacing.mdPlus, vertical = AerixSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MenuHeader(compact = compact, versionLine = versionLine)
            Spacer(Modifier.height(if (compact) AerixSpacing.sm else AerixSpacing.mdPlus))

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Left half: every action, small and stacked. The other half belongs to the player.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                ) {
                    CompactMenuButton(
                        label = stringResource(R.string.aerix_ingame_menu_controls),
                        iconRes = R.drawable.aerix_ic_touch_app_outlined,
                        color = palette.tiles[0],
                        compact = compact,
                        onClick = { onCloseRequest(); callbacks.onCustomControls() }
                    )
                    CompactMenuButton(
                        label = stringResource(R.string.aerix_ingame_menu_keycode),
                        iconRes = R.drawable.aerix_ic_keyboard_outlined,
                        color = palette.tiles[1],
                        compact = compact,
                        onClick = { onCloseRequest(); callbacks.onSendCustomKey() }
                    )
                    CompactMenuButton(
                        label = stringResource(R.string.aerix_ingame_menu_quick_settings),
                        iconRes = R.drawable.aerix_ic_settings_filled,
                        color = palette.tiles[2],
                        compact = compact,
                        onClick = { onCloseRequest(); callbacks.onQuickSettings() }
                    )
                    CompactMenuButton(
                        label = stringResource(R.string.aerix_ingame_menu_downloads),
                        iconRes = R.drawable.aerix_ic_cloud_download,
                        color = palette.tiles[3],
                        compact = compact,
                        onClick = onOpenDownloads
                    )
                    CompactMenuButton(
                        label = stringResource(R.string.aerix_ingame_menu_logs),
                        iconRes = R.drawable.aerix_ic_terminal_outlined,
                        color = palette.tiles[4],
                        compact = compact,
                        onClick = { onCloseRequest(); callbacks.onLogOutput() }
                    )
                    CompactMenuButton(
                        label = stringResource(R.string.aerix_ingame_menu_force_close),
                        iconRes = R.drawable.aerix_ic_close,
                        color = palette.tiles[5],
                        compact = compact,
                        onClick = { onCloseRequest(); callbacks.onForceCloseGame() }
                    )
                    CompactMenuButton(
                        label = if (net.kdt.pojavlaunch.recording.RecordingState.isActive()) "Stop recording" else "Screen recorder",
                        iconRes = R.drawable.aerix_ic_record,
                        color = palette.tiles[0],
                        compact = compact,
                        onClick = { onCloseRequest(); callbacks.onScreenRecorder() }
                    )
                    LoadingWallpaperRow(accent = palette.accent, compact = compact)

                    Spacer(Modifier.height(AerixSpacing.xs))
                    ResumeButton(
                        color = palette.resume,
                        compact = compact,
                        onClick = callbacks::onResumeGame
                    )
                }

                Spacer(Modifier.width(AerixSpacing.md))

                // Right half: the character the game is running as, and how they signed in.
                AccountStage(
                    account = account,
                    accent = palette.accent,
                    compact = compact,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(Modifier.height(AerixSpacing.sm))
            MenuBottomBar(
                versionLine = versionLine,
                compact = compact,
                onClose = onCloseRequest
            )
        }
    }
}

@Composable
private fun MenuHeader(compact: Boolean, versionLine: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.ic_aerix_mark),
            contentDescription = null,
            modifier = Modifier.size(if (compact) 34.dp else 44.dp)
        )
        Spacer(Modifier.height(AerixSpacing.xs))
        Text(
            text = stringResource(R.string.aerix_ingame_menu_title),
            color = AerixSurface.textPrimary,
            fontSize = if (compact) 18.sp else 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.aerix_ingame_menu_subtitle, versionLine),
            color = AerixSurface.textMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The switch for the wallpaper behind the game while it starts.
 *
 * It is here, in the Aerix menu, because that is the one screen the player can open during those
 * first seconds - which means it can be flipped while the game is still booting and the change
 * lands immediately, rather than on the next launch.
 */
@Composable
private fun LoadingWallpaperRow(accent: Color, compact: Boolean) {
    val context = LocalContext.current
    val checked = AerixPreferences.loadingWallpaper
    val shape = RoundedCornerShape(AerixRadii.controlSmall)

    fun apply(value: Boolean) {
        AerixPreferences.saveLoadingWallpaper(value)
        AerixLoadingWallpaper.refresh(context)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 34.dp else 40.dp)
            .clip(shape)
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .clickable(role = Role.Switch) { apply(!checked) }
            .padding(horizontal = AerixSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 20.dp else 24.dp)
                .clip(RoundedCornerShape(AerixRadii.micro))
                .background(accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_wallpaper),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
            )
        }
        Spacer(Modifier.width(AerixSpacing.sm))
        Text(
            text = stringResource(R.string.aerix_ingame_menu_loading_wallpaper),
            color = AerixSurface.textPrimary,
            fontSize = if (compact) 10.sp else 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = { apply(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = AerixSurface.onAccent,
                checkedTrackColor = accent,
                uncheckedThumbColor = AerixSurface.textSecondary,
                uncheckedTrackColor = AerixSurface.panelTrack,
                uncheckedBorderColor = AerixSurface.borderSoft
            )
        )
    }
}

@Composable
private fun CompactMenuButton(
    label: String,
    iconRes: Int,
    color: Color,
    compact: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(AerixRadii.controlSmall)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "compactButton")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 34.dp else 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = AerixSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 20.dp else 24.dp)
                .clip(RoundedCornerShape(AerixRadii.micro))
                .background(color.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
            )
        }
        Spacer(Modifier.width(AerixSpacing.sm))
        Text(
            text = label,
            color = AerixSurface.textPrimary,
            fontSize = if (compact) 10.sp else 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ResumeButton(color: Color, compact: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AerixRadii.controlSmall)
    val interaction = remember { MutableInteractionSource() }
    val onColor = if (color.luminance() > 0.45f) AerixSurface.canvas else Color.White
    val pressedState by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressedState) 0.98f else 1f, label = "resumeButton")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 34.dp else 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.8f))))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.aerix_ic_play_arrow_filled),
            contentDescription = null,
            tint = onColor,
            modifier = Modifier.size(if (compact) 13.dp else 15.dp)
        )
        Spacer(Modifier.width(AerixSpacing.sm))
        Text(
            text = stringResource(R.string.aerix_ingame_menu_resume),
            color = onColor,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MenuBottomBar(
    versionLine: String,
    compact: Boolean,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = versionLine,
            color = AerixSurface.textMuted,
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 220.dp)
        )

        Spacer(Modifier.width(AerixSpacing.md))
        Box(
            modifier = Modifier
                .size(if (compact) 32.dp else 36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, AerixSurface.borderSoft, CircleShape)
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_close),
                contentDescription = stringResource(R.string.aerix_ingame_menu_close),
                tint = AerixSurface.textPrimary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * The right half of the menu: the character the game is running as.
 *
 * The figure is the player's own skin, the cape and the Wide/Slim model included, and it turns
 * through a full 360° under a horizontal drag - with a little momentum, so a flick keeps it
 * spinning and settles.
 *
 * Under it, the name - tap it to see the other accounts - and under that, the way this account
 * signed in.
 */
@Composable
private fun AccountStage(
    account: AerixAccount?,
    accent: Color,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }
    var azimuth by remember(account?.username) { mutableFloatStateOf(28f) }
    val scope = rememberCoroutineScope()
    val tracker = remember(account?.username) { VelocityTracker() }
    var flingJob by remember(account?.username) { mutableStateOf<Job?>(null) }
    val shape = RoundedCornerShape(AerixRadii.cardLarge)
    val name = account?.username ?: stringResource(R.string.aerix_ingame_menu_no_account)

    Column(
        modifier = modifier
            .clip(shape)
            .background(AerixSurface.panelTrack.copy(alpha = 0.45f))
            .border(1.dp, AerixSurface.borderSoft, shape)
            .padding(AerixSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(AerixRadii.control))
                .pointerInput(account?.username) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            flingJob?.cancel()
                            flingJob = null
                            tracker.resetTracking()
                        },
                        onDragEnd = {
                            val release = tracker.calculateVelocity().x * GAME_DOLL_DEG_PER_PX
                            flingJob?.cancel()
                            if (abs(release) < GAME_DOLL_FLING_MIN) {
                                flingJob = null
                                return@detectHorizontalDragGestures
                            }
                            flingJob = scope.launch {
                                var velocity = release
                                var lastFrame = withFrameNanos { it }
                                while (abs(velocity) > GAME_DOLL_FLING_STOP) {
                                    val now = withFrameNanos { it }
                                    val dt = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                                    lastFrame = now
                                    azimuth = (azimuth + velocity * dt).mod(360f)
                                    velocity *= exp(-GAME_DOLL_FLING_FRICTION * dt)
                                }
                                flingJob = null
                            }
                        },
                        onDragCancel = {
                            flingJob?.cancel()
                            flingJob = null
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            azimuth = (azimuth + dragAmount * GAME_DOLL_DEG_PER_PX).mod(360f)
                        }
                    )
                }
        ) {
            // A soft halo behind the figure so it stands off the glass.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.18f), Color.Transparent)
                        )
                    )
            )
            AerixSkinPreview3D(
                account = account,
                azimuth = azimuth.roundToInt(),
                interactionEnabled = false,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = stringResource(R.string.aerix_ingame_menu_rotate_hint),
                color = AerixSurface.textMuted,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AerixSpacing.xxs)
            )
        }

        Spacer(Modifier.height(AerixSpacing.xs))

        // The name. Tapping it is how the player asks to be somebody else.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(AerixRadii.controlSmall))
                .clickable(role = Role.Button) { showPicker = true }
                .padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = name,
                color = AerixSurface.textPrimary,
                fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(AerixSpacing.xs))
            Icon(
                painter = painterResource(R.drawable.aerix_ic_arrow_drop_down_rounded),
                contentDescription = stringResource(R.string.aerix_ingame_menu_switch_account),
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }

        // How this account signed in: Microsoft, Ely.by or offline.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AuthIcon(account?.authType, accent)
            Spacer(Modifier.width(AerixSpacing.xs))
            Text(
                text = authLabel(account?.authType),
                color = AerixSurface.textMuted,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }

    if (showPicker) {
        AccountPickerDialog(
            accounts = PojavAccountBridge.accounts,
            currentKey = account?.key,
            accent = accent,
            onDismiss = { showPicker = false },
            onPick = {
                // Switching accounts cannot happen here: the running game already has its session.
                showPicker = false
                showBlocked = true
            }
        )
    }

    if (showBlocked) {
        AlertDialog(
            onDismissRequest = { showBlocked = false },
            title = {
                Text(
                    text = stringResource(R.string.aerix_ingame_menu_switch_blocked_title),
                    color = AerixSurface.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.aerix_ingame_menu_switch_blocked),
                    color = AerixSurface.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showBlocked = false }) {
                    Text(stringResource(R.string.aerix_ingame_menu_switch_blocked_ok), color = accent)
                }
            }
        )
    }
}

@Composable
private fun AuthIcon(authType: AuthType?, accent: Color) {
    val iconRes = when (authType) {
        AuthType.MICROSOFT -> R.drawable.ic_auth_ms
        AuthType.ELY_BY -> R.drawable.ic_auth_elyby
        else -> R.drawable.aerix_ic_person_outlined
    }
    // The Microsoft and Ely.by marks keep their own colours; only the generic person
    // glyph is tinted with the accent.
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = if (authType == AuthType.LOCAL || authType == null) accent.copy(alpha = 0.85f)
        else Color.Unspecified,
        modifier = Modifier.size(13.dp)
    )
}

@Composable
private fun authLabel(authType: AuthType?): String = when (authType) {
    AuthType.MICROSOFT -> stringResource(R.string.aerix_accounts_type_microsoft)
    AuthType.ELY_BY -> stringResource(R.string.aerix_accounts_type_elyby)
    AuthType.LOCAL -> stringResource(R.string.aerix_accounts_type_local)
    null -> stringResource(R.string.aerix_ingame_menu_no_account)
}

/**
 * The accounts already made, so the player can see who they could be next time.
 *
 * Picking one cannot take effect here - the game in front of them is already signed in - so the
 * caller answers it with the dashboard reminder.
 */
@Composable
private fun AccountPickerDialog(
    accounts: List<AerixAccount>,
    currentKey: String?,
    accent: Color,
    onDismiss: () -> Unit,
    onPick: (AerixAccount) -> Unit
) {
    val shape = RoundedCornerShape(AerixRadii.control)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.aerix_accounts_title),
                color = AerixSurface.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (accounts.isEmpty()) {
                Text(
                    text = stringResource(R.string.aerix_accounts_empty),
                    color = AerixSurface.textSecondary
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((minOf(accounts.size, 4) * 56).dp) // keep the dialog inside the screen
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                ) {
                    accounts.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(shape)
                                .background(
                                    if (entry.key == currentKey) accent.copy(alpha = 0.14f)
                                    else AerixSurface.panelTrack.copy(alpha = 0.45f)
                                )
                                .border(
                                    1.dp,
                                    if (entry.key == currentKey) accent.copy(alpha = 0.45f)
                                    else AerixSurface.borderSoft,
                                    shape
                                )
                                .clickable(role = Role.Button) { onPick(entry) }
                                .padding(horizontal = AerixSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val face = entry.face
                            if (face != null) {
                                Image(
                                    bitmap = face,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp).clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = entry.username.take(1).uppercase(),
                                        color = accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.width(AerixSpacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.username,
                                    color = AerixSurface.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = authLabel(entry.authType),
                                    color = AerixSurface.textMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                            if (entry.key == currentKey) {
                                Text(
                                    text = stringResource(R.string.aerix_accounts_current),
                                    color = accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.aerix_ingame_menu_switch_blocked_ok), color = accent)
            }
        }
    )
}

private const val GAME_DOLL_DEG_PER_PX = 0.45f
private const val GAME_DOLL_FLING_FRICTION = 5f
private const val GAME_DOLL_FLING_MIN = 60f
private const val GAME_DOLL_FLING_STOP = 20f
