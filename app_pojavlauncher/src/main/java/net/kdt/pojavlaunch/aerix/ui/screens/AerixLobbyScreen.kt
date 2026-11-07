/*
 * Aerix Lobby - the full-bleed, blocky launcher home.
 *
 * Visual language: a Minecraft build behind everything, one big rotatable player skin in the
 * middle, hard-edged square cards with stepped dark shadows, chunky pixel type, saturated
 * blue/green/yellow action blocks, and no navigation rail on the lobby itself.
 *
 * Every tile is a real launcher action. Nothing here is a mock-up: Play launches the selected
 * instance, the instance card switches the profile, the two party buttons open Socials or copy
 * an invite, the promo card is the same invite, the gift/chest buttons reveal a wallpaper from
 * the bundled presets, the top row opens the section menu / accounts / settings, and Help offers
 * the guide, the website, Discord and the log share. The old glass dashboard stays reachable
 * from the section menu and from the two settings switches.
 */
package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixInstallQueue
import net.kdt.pojavlaunch.aerix.bridge.AerixInstance
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D
import net.kdt.pojavlaunch.aerix.guide.AerixGuideState
import net.kdt.pojavlaunch.aerix.guide.guideAnchor
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import java.util.Locale
import kotlin.random.Random

private val LobbyFont = FontFamily(Font(R.font.minecraft_ten))
private val LobbyTextShadow = Shadow(color = Color(0xE6070A10), offset = Offset(3f, 4f))

private val LobbyInk = Color(0xFF0A0E14)
private val LobbyPanel = Color(0xF0131B26)
private val LobbyPanelSoft = Color(0xFF1D2835)
private val LobbyOutline = Color(0xFF33404E)
private val LobbyShadow = Color(0xE004070C)
private val LobbyBlue = Color(0xFF2E7AD4)
private val LobbyBlueDark = Color(0xFF17457F)
private val LobbyGreen = Color(0xFF55AF39)
private val LobbyGreenDark = Color(0xFF2D6B22)
private val LobbyYellow = Color(0xFFF4C53E)
private val LobbyYellowDark = Color(0xFF9A7415)
private val LobbyRed = Color(0xFFD93B4E)
private val LobbyWhite = Color(0xFFF5F7FA)
private val LobbyMuted = Color(0xFFAFBAC6)

private const val LOBBY_VILLAGE_ASSET = "wallpapers/wp_26_lobby_village.jpg"

private enum class LobbyBadge { NONE, EXCLAMATION, DOT }

/** Hard-edged rectangle with a stepped dark drop shadow, the blocky launcher's basic tile. */
@Composable
private fun BlockyBox(
    modifier: Modifier = Modifier,
    fill: Color,
    outline: Color = LobbyInk,
    shadow: Color = LobbyShadow,
    shadowOffset: Dp = 6.dp,
    outlineWidth: Dp = 3.dp,
    tilt: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = tilt }
            .padding(end = shadowOffset, bottom = shadowOffset)
            .drawBehind {
                val step = shadowOffset.toPx()
                drawRect(
                    color = shadow,
                    topLeft = Offset(step, step),
                    size = size
                )
            }
            .background(fill)
            .border(outlineWidth, outline)
    ) { content() }
}

@Composable
private fun BlockyButton(
    modifier: Modifier,
    fill: Color,
    onClick: () -> Unit,
    outline: Color = LobbyInk,
    shadow: Color = LobbyShadow,
    shadowOffset: Dp = 5.dp,
    tilt: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    BlockyBox(
        modifier = modifier.clickable(onClick = onClick),
        fill = fill,
        outline = outline,
        shadow = shadow,
        shadowOffset = shadowOffset,
        tilt = tilt
    ) { content() }
}

/** A small dark square chrome button in the top-right corner. */
@Composable
private fun LobbyIconButton(icon: Int, label: String, size: Dp, onClick: () -> Unit) {
    BlockyButton(
        modifier = Modifier.size(size),
        fill = LobbyPanel,
        outline = Color(0xFF070A0F),
        onClick = onClick,
        shadowOffset = 4.dp
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier
                .align(Alignment.Center)
                .size(size * 0.52f)
        )
    }
}

/** Full-bleed voxel village, softly blurred and darkened at the edges so the cards stay legible. */
@Composable
private fun LobbyBackdrop() {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(LOBBY_VILLAGE_ASSET).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B1A24))) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.blur(3.dp) else it },
                contentScale = ContentScale.Crop
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x59081018), Color(0x00081018), Color(0x8C07101A))
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0x66070D14), Color.Transparent, Color(0x66070D14))
                    )
                )
        )
    }
}

/** The big rotatable 3D player, with the existing 2D pixel-body fallback on WebView-less devices. */
@Composable
private fun LobbyPlayer(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AerixSkinPreview3D(
            account = PojavAccountBridge.current,
            modifier = Modifier.fillMaxSize(),
            azimuth = 24,
            pitch = 8,
            interactionEnabled = true,
            isVisible = true,
            useClientCape = true
        )
    }
}

@Composable
private fun LobbyFeatureCard(
    modifier: Modifier,
    art: Int,
    title: String,
    note: String,
    badge: LobbyBadge,
    cardHeight: Dp,
    tilt: Float = 0f,
    onClick: () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BlockyButton(
            modifier = Modifier.fillMaxWidth().height(cardHeight),
            fill = LobbyPanelSoft,
            outline = Color(0xFF080B10),
            shadowOffset = 6.dp,
            tilt = tilt,
            onClick = onClick
        ) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color(0xD0090E14))
            ) {
                Text(
                    text = title,
                    color = LobbyWhite,
                    fontFamily = LobbyFont,
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (badge == LobbyBadge.EXCLAMATION) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset((-7).dp, (-7).dp)
                        .size(26.dp)
                        .background(Color.White)
                        .border(2.dp, LobbyInk)
                ) {
                    Image(
                        painter = painterResource(R.drawable.aerix_lobby_ic_excl),
                        contentDescription = stringResource(R.string.aerix_lobby_bonus),
                        modifier = Modifier.align(Alignment.Center).size(18.dp)
                    )
                }
            }
            if (badge == LobbyBadge.DOT) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(5.dp, (-5).dp)
                        .size(14.dp)
                        .background(LobbyRed, CircleShape)
                        .border(2.dp, LobbyInk, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = note,
            color = LobbyWhite,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LobbyFont,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = LobbyTextShadow)
        )
    }
}

@Composable
private fun LobbyPromoCard(modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BlockyButton(
            modifier = Modifier.fillMaxWidth().weight(1f),
            fill = Color(0xF50B0E14),
            outline = Color(0xFF2C333D),
            onClick = onClick
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.aerix_lobby_promo_kicker),
                    color = LobbyMuted,
                    fontSize = 9.sp,
                    fontFamily = LobbyFont,
                    maxLines = 2
                )
                Column {
                    Text(
                        text = stringResource(R.string.aerix_lobby_promo_title),
                        color = LobbyWhite,
                        fontSize = 20.sp,
                        fontFamily = LobbyFont,
                        maxLines = 1,
                        style = TextStyle(shadow = LobbyTextShadow)
                    )
                    Text(
                        text = stringResource(R.string.aerix_lobby_promo_action),
                        color = LobbyYellow,
                        fontSize = 15.sp,
                        fontFamily = LobbyFont,
                        maxLines = 1,
                        style = TextStyle(shadow = LobbyTextShadow)
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.End)
                        .background(Color(0xFF7B45C9))
                        .border(2.dp, Color(0xFF4B2A7E))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.aerix_brand).uppercase(Locale.ROOT),
                        color = LobbyWhite,
                        fontFamily = LobbyFont,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(28.dp).height(4.dp).background(LobbyWhite))
    }
}

@Composable
private fun LobbySocialButton(modifier: Modifier, icon: Int, label: String, onClick: () -> Unit) {
    BlockyButton(
        modifier = modifier,
        fill = Color(0xE6121822),
        outline = Color(0xFF39424D),
        onClick = onClick
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = LobbyWhite,
                fontFamily = LobbyFont,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

private fun lobbyInstanceVersionLabel(versionId: String, vanilla: String): String {
    val match = Regex("\\d+\\.\\d+(?:\\.\\d+)?").find(versionId)?.value ?: versionId
    val loader = loaderLabel(versionId, vanilla)
    return if (loader == vanilla) match else "$loader · $match"
}

/** Blue "START HERE" instance card with the live instance picker. */
@Composable
private fun LobbyInstanceCard(
    modifier: Modifier,
    selected: AerixInstance?,
    instances: List<AerixInstance>,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    onSelect: (AerixInstance) -> Unit,
    onLibrary: () -> Unit
) {
    Box(modifier = modifier) {
        BlockyButton(
            modifier = Modifier.fillMaxSize(),
            fill = LobbyBlue,
            outline = LobbyBlueDark,
            onClick = { if (instances.isEmpty()) onLibrary() else onMenu(true) }
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.aerix_lobby_start_here),
                        color = LobbyYellow,
                        fontFamily = LobbyFont,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                    Text(
                        text = selected?.name ?: stringResource(R.string.aerix_lobby_no_instance),
                        color = LobbyWhite,
                        fontFamily = LobbyFont,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = LobbyTextShadow)
                    )
                    Text(
                        text = selected?.let {
                            lobbyInstanceVersionLabel(it.versionId, stringResource(R.string.aerix_home_vanilla))
                        } ?: stringResource(R.string.aerix_lobby_instance_hint),
                        color = Color(0xFFDCEBFF),
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
                Image(
                    painter = painterResource(R.drawable.aerix_lobby_ic_chest),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
            instances.forEach { instance ->
                DropdownMenuItem(
                    text = { Text(instance.name, maxLines = 1) },
                    onClick = { onSelect(instance); onMenu(false) }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.aerix_lobby_manage)) },
                onClick = { onMenu(false); onLibrary() }
            )
        }
    }
}

/** Green Play tile. While assets install it becomes the "Assets 93%" progress tile. */
@Composable
private fun LobbyPlayCard(
    modifier: Modifier,
    installing: Boolean,
    progress: Float,
    onPlay: () -> Unit,
    onDownloads: () -> Unit
) {
    BlockyButton(
        modifier = modifier,
        fill = LobbyGreen,
        outline = LobbyGreenDark,
        onClick = if (installing) onDownloads else onPlay
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.aerix_lobby_play),
                    color = LobbyWhite,
                    fontFamily = LobbyFont,
                    fontSize = 21.sp,
                    maxLines = 1,
                    style = TextStyle(shadow = LobbyTextShadow)
                )
                Text(
                    text = if (installing) {
                        stringResource(R.string.aerix_lobby_assets, (progress * 100).toInt())
                    } else {
                        stringResource(R.string.aerix_lobby_ready)
                    },
                    color = Color(0xFFE8FFE0),
                    fontFamily = LobbyFont,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            if (!installing) {
                Image(
                    painter = painterResource(R.drawable.aerix_lobby_ic_cursor),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        if (installing) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                color = LobbyWhite,
                trackColor = Color(0x330A1A08)
            )
        }
    }
}

@Composable
private fun LobbyChestCard(modifier: Modifier, onClick: () -> Unit) {
    BlockyButton(
        modifier = modifier,
        fill = LobbyYellow,
        outline = LobbyYellowDark,
        onClick = onClick
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.aerix_lobby_ic_chest),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = stringResource(R.string.aerix_lobby_chest),
                color = LobbyInk,
                fontFamily = LobbyFont,
                fontSize = 10.sp,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LobbyHelpButton(modifier: Modifier, onClick: () -> Unit) {
    BlockyButton(
        modifier = modifier,
        fill = Color(0xE6121822),
        outline = Color(0xFF39424D),
        onClick = onClick
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.aerix_lobby_ic_headset),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.aerix_lobby_help),
                color = LobbyWhite,
                fontFamily = LobbyFont,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun LobbyPlayDockWide(
    modifier: Modifier,
    selected: AerixInstance?,
    instances: List<AerixInstance>,
    installing: Boolean,
    progress: Float,
    hintVisible: Boolean,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    onSelect: (AerixInstance) -> Unit,
    onLibrary: () -> Unit,
    onPlay: () -> Unit,
    onChest: () -> Unit,
    onDownloads: () -> Unit,
    onDismissHint: () -> Unit
) {
    BlockyBox(
        modifier = modifier,
        fill = LobbyPanel,
        outline = LobbyOutline,
        shadowOffset = 7.dp
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (hintVisible) {
                LobbyInstanceCard(
                    modifier = Modifier.weight(1.25f).fillMaxHeight(),
                    selected = selected,
                    instances = instances,
                    menuOpen = menuOpen,
                    onMenu = onMenu,
                    onSelect = onSelect,
                    onLibrary = onLibrary
                )
            }
            LobbyPlayCard(
                modifier = Modifier
                    .weight(if (hintVisible) 0.85f else 1.6f)
                    .fillMaxHeight()
                    .guideAnchor(AerixGuideState.PLAY),
                installing = installing,
                progress = progress,
                onPlay = onPlay,
                onDownloads = onDownloads
            )
            LobbyChestCard(
                modifier = Modifier.weight(0.62f).fillMaxHeight(),
                onClick = onChest
            )
            BlockyButton(
                modifier = Modifier.size(30.dp),
                fill = Color(0xFF2A3440),
                outline = Color(0xFF0A0E13),
                onClick = onDismissHint
            ) {
                Image(
                    painter = painterResource(R.drawable.aerix_lobby_ic_close),
                    contentDescription = stringResource(R.string.aerix_lobby_close),
                    modifier = Modifier.align(Alignment.Center).size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun LobbyPlayDockCompact(
    modifier: Modifier,
    selected: AerixInstance?,
    instances: List<AerixInstance>,
    installing: Boolean,
    progress: Float,
    hintVisible: Boolean,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    onSelect: (AerixInstance) -> Unit,
    onLibrary: () -> Unit,
    onPlay: () -> Unit,
    onChest: () -> Unit,
    onDownloads: () -> Unit,
    onDismissHint: () -> Unit
) {
    BlockyBox(
        modifier = modifier.height(if (hintVisible) 196.dp else 108.dp),
        fill = LobbyPanel,
        outline = LobbyOutline,
        shadowOffset = 7.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (hintVisible) {
                LobbyInstanceCard(
                    modifier = Modifier.fillMaxWidth().height(76.dp),
                    selected = selected,
                    instances = instances,
                    menuOpen = menuOpen,
                    onMenu = onMenu,
                    onSelect = onSelect,
                    onLibrary = onLibrary
                )
            }
            Row(
                Modifier.fillMaxWidth().height(76.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                LobbyPlayCard(
                    modifier = Modifier.weight(1f).fillMaxHeight().guideAnchor(AerixGuideState.PLAY),
                    installing = installing,
                    progress = progress,
                    onPlay = onPlay,
                    onDownloads = onDownloads
                )
                LobbyChestCard(
                    modifier = Modifier.weight(0.7f).fillMaxHeight(),
                    onClick = onChest
                )
                BlockyButton(
                    modifier = Modifier.size(30.dp),
                    fill = Color(0xFF2A3440),
                    outline = Color(0xFF0A0E13),
                    onClick = onDismissHint
                ) {
                    Image(
                        painter = painterResource(R.drawable.aerix_lobby_ic_close),
                        contentDescription = stringResource(R.string.aerix_lobby_close),
                        modifier = Modifier.align(Alignment.Center).size(16.dp)
                    )
                }
            }
        }
    }
}

/** Centred blocky dialog used for the section menu, the reward and Help. */
@Composable
private fun LobbyDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xC0050910))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        BlockyBox(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .wrapContentHeight()
                .clickable { },
            fill = LobbyPanel,
            outline = LobbyOutline,
            shadowOffset = 8.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) { content() }
        }
    }
}

@Composable
private fun LobbyDialogAction(modifier: Modifier, label: String, onClick: () -> Unit) {
    BlockyButton(
        modifier = modifier.height(44.dp),
        fill = LobbyPanelSoft,
        outline = LobbyOutline,
        onClick = onClick,
        shadowOffset = 4.dp
    ) {
        Text(
            text = label,
            color = LobbyWhite,
            fontFamily = LobbyFont,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/** All the launcher sections, since the lobby hides the glass rail. */
@Composable
private fun LobbyMenuSheet(onDismiss: () -> Unit, navigate: (LauncherSection) -> Unit) {
    val sections = listOf(
        LauncherSection.HOME to R.string.aerix_nav_home,
        LauncherSection.LIBRARY to R.string.aerix_nav_library,
        LauncherSection.SERVERS to R.string.aerix_nav_servers,
        LauncherSection.DISCOVER to R.string.aerix_nav_discover,
        LauncherSection.SOCIALS to R.string.aerix_nav_socials,
        LauncherSection.WALLPAPERS to R.string.aerix_nav_wallpapers,
        LauncherSection.CONTENT to R.string.aerix_nav_content,
        LauncherSection.FILES to R.string.aerix_nav_files,
        LauncherSection.LOGS to R.string.aerix_nav_logs,
        LauncherSection.ACCOUNTS to R.string.aerix_nav_accounts,
        LauncherSection.SETTINGS to R.string.aerix_nav_settings,
        LauncherSection.ABOUT to R.string.aerix_nav_about
    )
    LobbyDialog(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.aerix_lobby_menu),
            color = LobbyWhite,
            fontFamily = LobbyFont,
            fontSize = 16.sp,
            style = TextStyle(shadow = LobbyTextShadow)
        )
        sections.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (section, label) ->
                    LobbyDialogAction(
                        modifier = Modifier.weight(1f),
                        label = stringResource(label),
                        onClick = {
                            onDismiss()
                            if (section != LauncherSection.HOME) navigate(section)
                        }
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        LobbyDialogAction(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.aerix_lobby_classic),
            onClick = {
                AerixPreferences.saveLobbyUi(false)
                onDismiss()
            }
        )
    }
}

@Composable
private fun LobbyHelpSheet(activity: FragmentActivity, onDismiss: () -> Unit) {
    LobbyDialog(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.aerix_lobby_help_title),
            color = LobbyWhite,
            fontFamily = LobbyFont,
            fontSize = 16.sp
        )
        LobbyDialogAction(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.aerix_lobby_help_guide),
            onClick = { onDismiss(); AerixGuideState.replay() }
        )
        LobbyDialogAction(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.aerix_lobby_help_website),
            onClick = { onDismiss(); PojavNavigator.openWebsite(activity) }
        )
        LobbyDialogAction(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.aerix_lobby_help_discord),
            onClick = { onDismiss(); PojavNavigator.openDiscord(activity) }
        )
        LobbyDialogAction(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.aerix_lobby_help_logs),
            onClick = { onDismiss(); PojavNavigator.shareLogs(activity) }
        )
        LobbyDialogAction(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.aerix_lobby_close),
            onClick = onDismiss
        )
    }
}

@Composable
private fun LobbyRewardSheet(rewardId: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    val preset = AerixThemeManager.wallpapers.firstOrNull { it.id == rewardId }
    LobbyDialog(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.aerix_lobby_reward_title),
            color = LobbyYellow,
            fontFamily = LobbyFont,
            fontSize = 15.sp
        )
        Text(
            text = preset?.title ?: stringResource(R.string.aerix_lobby_chest_empty),
            color = LobbyWhite,
            fontFamily = LobbyFont,
            fontSize = 18.sp,
            style = TextStyle(shadow = LobbyTextShadow)
        )
        Text(
            text = preset?.biome ?: "",
            color = LobbyMuted,
            fontSize = 11.sp
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LobbyDialogAction(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.aerix_lobby_reward_keep),
                onClick = onDismiss
            )
            if (preset != null) {
                LobbyDialogAction(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.aerix_lobby_reward_apply),
                    onClick = { onApply(preset.id); onDismiss() }
                )
            }
        }
    }
}

@Composable
private fun LobbyTopBar(buttonSize: Dp, onMenu: () -> Unit, onAccounts: () -> Unit, onChest: () -> Unit, onSettings: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LobbyIconButton(
            icon = R.drawable.aerix_lobby_ic_grid,
            label = stringResource(R.string.aerix_lobby_menu),
            size = buttonSize,
            onClick = onMenu
        )
        LobbyIconButton(
            icon = R.drawable.aerix_lobby_ic_head,
            label = stringResource(R.string.aerix_lobby_accounts),
            size = buttonSize,
            onClick = onAccounts
        )
        LobbyIconButton(
            icon = R.drawable.aerix_lobby_ic_gift,
            label = stringResource(R.string.aerix_lobby_gift),
            size = buttonSize,
            onClick = onChest
        )
        LobbyIconButton(
            icon = R.drawable.aerix_lobby_ic_settings,
            label = stringResource(R.string.aerix_lobby_settings),
            size = buttonSize,
            onClick = onSettings
        )
    }
}

@Composable
private fun LobbyWideLayout(
    maxWidth: Dp,
    maxHeight: Dp,
    state: LobbyState
) {
    val cardWidth = maxWidth * 0.17f
    val cardHeight = maxHeight * 0.225f
    Box(Modifier.fillMaxSize()) {
        LobbyPlayer(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -(maxHeight * 0.02f))
                .fillMaxWidth(0.40f)
                .fillMaxHeight(0.96f)
        )

        LobbyFeatureCard(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = maxWidth * 0.028f, y = maxHeight * 0.045f)
                .width(cardWidth),
            art = R.drawable.aerix_lobby_card_shop,
            title = stringResource(R.string.aerix_lobby_shop),
            note = stringResource(R.string.aerix_lobby_bonus),
            badge = LobbyBadge.EXCLAMATION,
            cardHeight = cardHeight,
            tilt = -1.5f,
            onClick = state.discover
        )
        LobbyFeatureCard(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = maxWidth * 0.023f, y = maxHeight * 0.335f)
                .width(cardWidth),
            art = R.drawable.aerix_lobby_card_skin,
            title = stringResource(R.string.aerix_lobby_my_skin),
            note = stringResource(R.string.aerix_lobby_skin_hint),
            badge = LobbyBadge.NONE,
            cardHeight = cardHeight,
            tilt = 1.2f,
            onClick = state.accounts
        )
        LobbyFeatureCard(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = maxWidth * 0.028f, y = maxHeight * 0.625f)
                .width(cardWidth),
            art = R.drawable.aerix_lobby_card_friends,
            title = stringResource(R.string.aerix_lobby_friends),
            note = stringResource(R.string.aerix_lobby_party_hint),
            badge = LobbyBadge.DOT,
            cardHeight = cardHeight,
            tilt = -1f,
            onClick = state.socials
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = -(maxWidth * 0.014f), y = maxHeight * 0.05f)
        ) {
            LobbyTopBar(
                buttonSize = (maxHeight * 0.105f).coerceAtLeastCompat(34.dp),
                onMenu = state.onMenu,
                onAccounts = state.accounts,
                onChest = state.onChest,
                onSettings = state.settings
            )
        }

        LobbyPromoCard(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = -(maxWidth * 0.018f), y = -(maxHeight * 0.09f))
                .width(maxWidth * 0.195f)
                .height(maxHeight * 0.34f),
            onClick = state.invite
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(maxHeight * 0.295f)),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LobbySocialButton(
                modifier = Modifier.width(maxWidth * 0.155f).height(maxHeight * 0.085f),
                icon = R.drawable.aerix_lobby_ic_search,
                label = stringResource(R.string.aerix_lobby_find_party),
                onClick = state.socials
            )
            LobbySocialButton(
                modifier = Modifier.width(maxWidth * 0.155f).height(maxHeight * 0.085f),
                icon = R.drawable.aerix_lobby_ic_person_add,
                label = stringResource(R.string.aerix_lobby_invite_friend),
                onClick = state.invite
            )
        }

        LobbyPlayDockWide(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(maxHeight * 0.025f))
                .width(maxWidth * 0.58f)
                .height(maxHeight * 0.215f),
            selected = state.selected,
            instances = state.instances,
            installing = state.installing,
            progress = state.progress,
            hintVisible = state.hintVisible,
            menuOpen = state.menuOpen,
            onMenu = state.onMenuChange,
            onSelect = state.onSelect,
            onLibrary = state.library,
            onPlay = state.onPlay,
            onChest = state.onChest,
            onDownloads = state.onDownloads,
            onDismissHint = state.onDismissHint
        )

        LobbyHelpButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = -(maxWidth * 0.012f), y = -(maxHeight * 0.035f))
                .width(maxWidth * 0.095f)
                .height(maxHeight * 0.085f),
            onClick = state.onHelp
        )
    }
}

@Composable
private fun LobbyCompactLayout(maxHeight: Dp, state: LobbyState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LobbyTopBar(
                buttonSize = 40.dp,
                onMenu = state.onMenu,
                onAccounts = state.accounts,
                onChest = state.onChest,
                onSettings = state.settings
            )
        }
        LobbyPlayer(
            modifier = Modifier
                .fillMaxWidth()
                .height((maxHeight * 0.42f).coerceAtLeastCompat(220.dp))
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LobbyFeatureCard(
                modifier = Modifier.weight(1f),
                art = R.drawable.aerix_lobby_card_shop,
                title = stringResource(R.string.aerix_lobby_shop),
                note = stringResource(R.string.aerix_lobby_bonus),
                badge = LobbyBadge.EXCLAMATION,
                cardHeight = 106.dp,
                onClick = state.discover
            )
            LobbyFeatureCard(
                modifier = Modifier.weight(1f),
                art = R.drawable.aerix_lobby_card_skin,
                title = stringResource(R.string.aerix_lobby_my_skin),
                note = stringResource(R.string.aerix_lobby_skin_hint),
                badge = LobbyBadge.NONE,
                cardHeight = 106.dp,
                onClick = state.accounts
            )
            LobbyFeatureCard(
                modifier = Modifier.weight(1f),
                art = R.drawable.aerix_lobby_card_friends,
                title = stringResource(R.string.aerix_lobby_friends),
                note = stringResource(R.string.aerix_lobby_party_hint),
                badge = LobbyBadge.DOT,
                cardHeight = 106.dp,
                onClick = state.socials
            )
        }
        LobbyPromoCard(
            modifier = Modifier.fillMaxWidth().height(132.dp),
            onClick = state.invite
        )
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LobbySocialButton(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = R.drawable.aerix_lobby_ic_search,
                label = stringResource(R.string.aerix_lobby_find_party),
                onClick = state.socials
            )
            LobbySocialButton(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = R.drawable.aerix_lobby_ic_person_add,
                label = stringResource(R.string.aerix_lobby_invite_friend),
                onClick = state.invite
            )
        }
        LobbyPlayDockCompact(
            modifier = Modifier.fillMaxWidth(),
            selected = state.selected,
            instances = state.instances,
            installing = state.installing,
            progress = state.progress,
            hintVisible = state.hintVisible,
            menuOpen = state.menuOpen,
            onMenu = state.onMenuChange,
            onSelect = state.onSelect,
            onLibrary = state.library,
            onPlay = state.onPlay,
            onChest = state.onChest,
            onDownloads = state.onDownloads,
            onDismissHint = state.onDismissHint
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LobbyHelpButton(
                modifier = Modifier.width(120.dp).height(44.dp),
                onClick = state.onHelp
            )
        }
    }
}

/** Dp.clamp without relying on version-specific coerceIn overloads. */
private fun Dp.coerceAtLeastCompat(min: Dp): Dp = if (this < min) min else this

/** All of the live actions and the small bit of UI state, collected for the two layouts. */
private class LobbyState(
    val selected: AerixInstance?,
    val instances: List<AerixInstance>,
    val installing: Boolean,
    val progress: Float,
    val hintVisible: Boolean,
    val menuOpen: Boolean,
    val onMenu: () -> Unit,
    val onMenuChange: (Boolean) -> Unit,
    val onSelect: (AerixInstance) -> Unit,
    val library: () -> Unit,
    val accounts: () -> Unit,
    val socials: () -> Unit,
    val discover: () -> Unit,
    val settings: () -> Unit,
    val invite: () -> Unit,
    val onPlay: () -> Unit,
    val onChest: () -> Unit,
    val onDownloads: () -> Unit,
    val onHelp: () -> Unit,
    val onDismissHint: () -> Unit
)

@Composable
fun AerixLobbyScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    LaunchedEffect(Unit) { PojavAccountBridge.ensureLoaded() }
    val context = LocalContext.current
    val instances = PojavInstanceBridge.instances
    val selected = PojavInstanceBridge.selected
    val installing = AerixInstallQueue.running
    val progress = AerixInstallQueue.overallProgress
    val villageBackdrop = AerixPreferences.lobbyVillageBackdrop

    var menuSheet by rememberSaveable { mutableStateOf(false) }
    var helpSheet by rememberSaveable { mutableStateOf(false) }
    var rewardSheet by rememberSaveable { mutableStateOf(false) }
    var rewardId by rememberSaveable { mutableStateOf("") }
    var instanceMenu by rememberSaveable { mutableStateOf(false) }
    var hintVisible by rememberSaveable { mutableStateOf(true) }

    val invite: () -> Unit = {
        val code = SocialBackend.inviteCode()
        if (code == null) {
            Toast.makeText(context, R.string.aerix_lobby_invite_missing, Toast.LENGTH_SHORT).show()
        } else {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText("Aerix invite", "aerix-social://friend/$code")
            )
            Toast.makeText(context, R.string.aerix_lobby_invite_copied, Toast.LENGTH_SHORT).show()
        }
    }
    val openChest: () -> Unit = {
        val pool = AerixThemeManager.wallpapers
        rewardId = if (pool.isEmpty()) "" else pool[Random.nextInt(pool.size)].id
        rewardSheet = true
    }
    val state = LobbyState(
        selected = selected,
        instances = instances,
        installing = installing,
        progress = progress,
        hintVisible = hintVisible,
        menuOpen = instanceMenu,
        onMenu = { menuSheet = true },
        onMenuChange = { instanceMenu = it },
        onSelect = { PojavInstanceBridge.select(it) },
        library = { navigate(LauncherSection.LIBRARY) },
        accounts = { navigate(LauncherSection.ACCOUNTS) },
        socials = { navigate(LauncherSection.SOCIALS) },
        discover = { navigate(LauncherSection.DISCOVER) },
        settings = { navigate(LauncherSection.SETTINGS) },
        invite = invite,
        onPlay = { PojavLaunchBridge.play(activity) },
        onChest = openChest,
        onDownloads = { navigate(LauncherSection.DISCOVER) },
        onHelp = { helpSheet = true },
        onDismissHint = { hintVisible = !hintVisible }
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (villageBackdrop) LobbyBackdrop()
        val wide = maxWidth >= 680.dp && maxWidth > maxHeight
        if (wide) {
            LobbyWideLayout(maxWidth, maxHeight, state)
        } else {
            LobbyCompactLayout(maxHeight, state)
        }

        if (menuSheet) LobbyMenuSheet(onDismiss = { menuSheet = false }, navigate = navigate)
        if (helpSheet) LobbyHelpSheet(activity = activity, onDismiss = { helpSheet = false })
        if (rewardSheet) {
            LobbyRewardSheet(
                rewardId = rewardId,
                onDismiss = { rewardSheet = false },
                onApply = { id ->
                    AerixPreferences.saveSelectedWallpaper(id)
                    // Show the reward right away, even if the bundled village backdrop was on.
                    AerixPreferences.saveLobbyVillageBackdrop(false)
                }
            )
        }
    }
}
