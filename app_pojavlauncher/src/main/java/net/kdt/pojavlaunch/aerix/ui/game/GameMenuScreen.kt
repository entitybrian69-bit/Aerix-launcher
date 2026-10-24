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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixAccount
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.GameDownloadTarget
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
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
        val columnWidth = maxWidth.coerceAtMost(760.dp)
        Column(
            modifier = Modifier
                .width(columnWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AerixSpacing.mdPlus, vertical = AerixSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MenuHeader(compact = compact, versionLine = versionLine)
            Spacer(Modifier.height(if (compact) AerixSpacing.sm else AerixSpacing.mdPlus))

            LoadingWallpaperRow(accent = palette.accent, compact = compact)
            Spacer(Modifier.height(if (compact) AerixSpacing.sm else AerixSpacing.mdPlus))

            val tileHeight = if (compact) 74.dp else 100.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
            ) {
                MenuTile(
                    label = stringResource(R.string.aerix_ingame_menu_controls),
                    iconRes = R.drawable.aerix_ic_touch_app_outlined,
                    color = palette.tiles[0],
                    compact = compact,
                    onClick = { onCloseRequest(); callbacks.onCustomControls() },
                    modifier = Modifier.weight(1f).height(tileHeight)
                )
                MenuTile(
                    label = stringResource(R.string.aerix_ingame_menu_keycode),
                    iconRes = R.drawable.aerix_ic_keyboard_outlined,
                    color = palette.tiles[1],
                    compact = compact,
                    onClick = { onCloseRequest(); callbacks.onSendCustomKey() },
                    modifier = Modifier.weight(1f).height(tileHeight)
                )
                MenuTile(
                    label = stringResource(R.string.aerix_ingame_menu_quick_settings),
                    iconRes = R.drawable.aerix_ic_settings_filled,
                    color = palette.tiles[2],
                    compact = compact,
                    onClick = { onCloseRequest(); callbacks.onQuickSettings() },
                    modifier = Modifier.weight(1f).height(tileHeight)
                )
            }

            Spacer(Modifier.height(AerixSpacing.smPlus))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
            ) {
                MenuTile(
                    label = stringResource(R.string.aerix_ingame_menu_downloads),
                    iconRes = R.drawable.aerix_ic_cloud_download,
                    color = palette.tiles[3],
                    compact = compact,
                    onClick = onOpenDownloads,
                    modifier = Modifier.weight(1f).height(tileHeight)
                )
                MenuTile(
                    label = stringResource(R.string.aerix_ingame_menu_logs),
                    iconRes = R.drawable.aerix_ic_terminal_outlined,
                    color = palette.tiles[4],
                    compact = compact,
                    onClick = { onCloseRequest(); callbacks.onLogOutput() },
                    modifier = Modifier.weight(1f).height(tileHeight)
                )
                MenuTile(
                    label = stringResource(R.string.aerix_ingame_menu_force_close),
                    iconRes = R.drawable.aerix_ic_close,
                    color = palette.tiles[5],
                    compact = compact,
                    onClick = { onCloseRequest(); callbacks.onForceCloseGame() },
                    modifier = Modifier.weight(1f).height(tileHeight)
                )
            }

            Spacer(Modifier.height(if (compact) AerixSpacing.smPlus else AerixSpacing.md))
            MenuTile(
                label = if (net.kdt.pojavlaunch.recording.RecordingState.isActive()) "Stop recording" else "Screen recorder",
                iconRes = R.drawable.aerix_ic_record,
                color = palette.tiles[0], compact = compact,
                onClick = { onCloseRequest(); callbacks.onScreenRecorder() },
                modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 60.dp)
            )
            Spacer(Modifier.height(AerixSpacing.sm))
            ResumeButton(
                color = palette.resume,
                compact = compact,
                onClick = callbacks::onResumeGame
            )
            Spacer(Modifier.height(if (compact) AerixSpacing.sm else AerixSpacing.mdPlus))
            MenuBottomBar(
                account = account,
                versionLine = versionLine,
                accent = palette.accent,
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
 * The switch for the loading screen background.
 *
 * It lives here, in the menu, rather than on the loading screen itself: the menu is where the rest
 * of the in-game options are, and the loading screen stays the clean black-and-spinner it is.
 * Flipping it takes effect on the next launch, which is when a loading screen exists.
 */
@Composable
private fun LoadingWallpaperRow(accent: Color, compact: Boolean) {
    val checked = AerixPreferences.loadingWallpaper
    val shape = RoundedCornerShape(AerixRadii.card)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AerixSurface.panelTrack.copy(alpha = 0.55f))
            .border(1.dp, AerixSurface.borderSoft, shape)
            .clickable(role = Role.Switch) {
                AerixPreferences.saveLoadingWallpaper(!checked)
                AerixLoadingWallpaper.refresh(LocalContext.current)
            }
            .padding(
                horizontal = if (compact) AerixSpacing.smPlus else AerixSpacing.md,
                vertical = if (compact) AerixSpacing.xsPlus else AerixSpacing.sm
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 34.dp)
                .clip(RoundedCornerShape(AerixRadii.controlSmall))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_wallpaper),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(if (compact) 16.dp else 18.dp)
            )
        }
        Spacer(Modifier.width(AerixSpacing.smPlus))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.aerix_ingame_menu_loading_wallpaper),
                color = AerixSurface.textPrimary,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                Text(
                    text = stringResource(R.string.aerix_ingame_menu_loading_wallpaper_summary),
                    color = AerixSurface.textMuted,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(AerixSpacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = { value ->
                AerixPreferences.saveLoadingWallpaper(value)
                AerixLoadingWallpaper.refresh(LocalContext.current)
            },
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
private fun MenuTile(
    label: String,
    iconRes: Int,
    color: Color,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(AerixRadii.cardLarge)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "menuTile")

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.06f))
                )
            )
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(AerixSpacing.smCompact),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 36.dp else 44.dp)
                .clip(RoundedCornerShape(AerixRadii.control))
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(if (compact) 20.dp else 24.dp)
            )
        }
        Spacer(Modifier.height(AerixSpacing.xsPlus))
        Text(
            text = label,
            color = AerixSurface.textPrimary,
            fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ResumeButton(color: Color, compact: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AerixRadii.control)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "resumeButton")
    val onColor = if (color.luminance() > 0.45f) AerixSurface.canvas else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.8f))))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = if (compact) AerixSpacing.smCompact else AerixSpacing.mdTight),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.aerix_ic_play_arrow_filled),
            contentDescription = null,
            tint = onColor,
            modifier = Modifier.size(if (compact) 16.dp else 20.dp)
        )
        Spacer(Modifier.width(AerixSpacing.sm))
        Text(
            text = stringResource(R.string.aerix_ingame_menu_resume),
            color = onColor,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MenuBottomBar(
    account: AerixAccount?,
    versionLine: String,
    accent: Color,
    compact: Boolean,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val face = account?.face
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
                        text = account?.username?.take(1)?.uppercase() ?: "?",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(AerixSpacing.sm))
            Text(
                text = account?.username ?: stringResource(R.string.aerix_ingame_menu_no_account),
                color = AerixSurface.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

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
