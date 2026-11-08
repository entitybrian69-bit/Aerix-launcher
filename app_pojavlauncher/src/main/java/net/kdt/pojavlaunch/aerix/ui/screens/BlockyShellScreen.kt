/*
 * Aerix Blocky Shell - a whole new launcher identity, drawn from the user's sketch.
 *
 * Design contract: docs/blocky-shell.md
 *  - Every option has a BLACK outline, its OWN colour and theme, and white pixel text.
 *  - In the middle, a smooth 3D character wearing the applied skin / cosmetics.
 *  - The character emotes on its own (dance, jump, wave, perform action) AND reacts to the
 *    user's actions; it is rotatable and never a still frame.
 *  - Blocky look, blocky feel, blocky sound, blocky animations, blocky transitions, blocky
 *    motions. All of it.
 *
 * Nothing here is a mock destination: every tile calls a real launcher / wardrobe / mod action.
 */
package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixAccount
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
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyBadge
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyBox
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyButton
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyCard
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyEmote
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyFont
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyIconButton
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyOutline
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyPanel
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyReactions
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockySound
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockySpikes
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyStepsProgress
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockySunburst
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyTag
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyTheme
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyWhite
import java.util.Locale
import kotlin.random.Random

private const val BLOCKY_PLAZA_ASSET = "wallpapers/wp_27_blocky_plaza.jpg"

private data class BlockyOption(
    val action: String,
    val title: String,
    val subtitle: String,
    val icon: Int,
    val theme: BlockyTheme,
    val badge: BlockyBadge = BlockyBadge.NONE
)

private class BlockyState(
    val selected: AerixInstance?,
    val instances: List<AerixInstance>,
    val account: AerixAccount?,
    val installing: Boolean,
    val progress: Float,
    val emote: String?,
    val emoteNonce: Int,
    val emoteLabel: String,
    val autoEmotes: Boolean,
    val instanceMenuOpen: Boolean,
    val onEmoteChanged: (String) -> Unit,
    val onEmote: (BlockyEmote) -> Unit,
    val onToggleAuto: () -> Unit,
    val onOption: (String) -> Unit,
    val onPlay: () -> Unit,
    val onChest: () -> Unit,
    val onHelp: () -> Unit,
    val onMenu: () -> Unit,
    val onInstanceMenu: (Boolean) -> Unit,
    val onSelectInstance: (AerixInstance) -> Unit,
    val onLibrary: () -> Unit
)

@Composable
fun BlockyShellScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        PojavAccountBridge.ensureLoaded()
        BlockySound.init(context.applicationContext)
    }

    val instances = PojavInstanceBridge.instances
    val selected = PojavInstanceBridge.selected
    val account = PojavAccountBridge.current
    val installing = AerixInstallQueue.running
    val progress = AerixInstallQueue.overallProgress

    var menuSheet by remember { mutableStateOf(false) }
    var helpSheet by remember { mutableStateOf(false) }
    var rewardSheet by remember { mutableStateOf(false) }
    var rewardId by remember { mutableStateOf("") }
    var instanceMenu by remember { mutableStateOf(false) }
    var autoEmotes by remember { mutableStateOf(AerixPreferences.blockyEmotes) }

    var emote by remember { mutableStateOf<String?>(null) }
    var emoteNonce by remember { mutableIntStateOf(0) }
    var emoteLabel by remember { mutableStateOf(context.getString(R.string.aerix_blocky_emote_idle)) }

    // The character acts on its own: a fresh emote every few seconds, never a still frame.
    fun trigger(next: BlockyEmote) {
        emote = next.id
        emoteNonce++
        emoteLabel = context.getString(next.label)
        BlockySound.playEmote(next.id)
    }

    fun react(action: String) = trigger(BlockyReactions.of(action))

    LaunchedEffect(Unit) {
        while (true) {
            delay(8_000L)
            if (AerixPreferences.blockyEmotes) trigger(BlockyEmote.random())
        }
    }

    // Reactions to what the user is doing, not just to taps: downloads, wardrobe changes and
    // switching accounts all get their own emote.
    var lastProgress by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(progress) {
        if (lastProgress >= 0f && progress != lastProgress && progress < 0.999f) {
            trigger(BlockyEmote.ACTION)
        }
        lastProgress = progress
    }
    var lastWardrobe by remember { mutableIntStateOf(PojavAccountBridge.wardrobeRevision) }
    LaunchedEffect(PojavAccountBridge.wardrobeRevision) {
        val now = PojavAccountBridge.wardrobeRevision
        if (now != lastWardrobe) {
            lastWardrobe = now
            trigger(BlockyEmote.WAVE)
        }
    }
    var lastAccount by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(account?.key) {
        val key = account?.key
        if (lastAccount != null && key != lastAccount) trigger(BlockyEmote.WAVE)
        lastAccount = key
    }

    val invite: () -> Unit = {
        react("invite")
        val code = SocialBackend.inviteCode()
        if (code == null) {
            Toast.makeText(context, R.string.aerix_blocky_invite_missing, Toast.LENGTH_SHORT).show()
        } else {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Aerix invite", "aerix-social://friend/$code"))
            Toast.makeText(context, R.string.aerix_blocky_invite_copied, Toast.LENGTH_SHORT).show()
        }
    }

    val openChest: () -> Unit = {
        react("chest")
        val pool = AerixThemeManager.wallpapers
        rewardId = if (pool.isEmpty()) "" else pool[Random.nextInt(pool.size)].id
        rewardSheet = true
    }

    fun option(action: String) {
        react(action)
        when (action) {
            "play" -> if (installing) navigate(LauncherSection.DISCOVER) else PojavLaunchBridge.play(activity)
            "library" -> navigate(LauncherSection.LIBRARY)
            "wardrobe" -> {
                PojavAccountBridge.current?.let { PojavAccountBridge.openDetail(it) }
                navigate(LauncherSection.ACCOUNTS)
            }
            "mod" -> navigate(LauncherSection.AERIX_CLIENT)
            "friends" -> navigate(LauncherSection.SOCIALS)
            "discover" -> navigate(LauncherSection.DISCOVER)
            "servers" -> navigate(LauncherSection.SERVERS)
            "accounts" -> navigate(LauncherSection.ACCOUNTS)
            "settings" -> navigate(LauncherSection.SETTINGS)
            "content" -> navigate(LauncherSection.CONTENT)
            "wallpapers" -> navigate(LauncherSection.WALLPAPERS)
            "logs" -> navigate(LauncherSection.LOGS)
            "downloads" -> navigate(LauncherSection.DOWNLOADS)
            "chest" -> openChest()
            "help" -> helpSheet = true
            "menu" -> menuSheet = true
            "guide" -> AerixGuideState.replay()
            "invite" -> invite()
            "classic" -> AerixPreferences.saveBlockyUi(false)
            "sound" -> {
                AerixPreferences.saveBlockySound(!AerixPreferences.blockySound)
                if (AerixPreferences.blockySound) BlockySound.play(BlockySound.Kind.OPEN)
            }
        }
    }

    val options = listOf(
        BlockyOption("play", stringResource(R.string.aerix_blocky_opt_play),
            stringResource(R.string.aerix_blocky_opt_play_sub), R.drawable.aerix_blocky_ic_play,
            BlockyTheme.GRASS),
        BlockyOption("library", stringResource(R.string.aerix_blocky_opt_library),
            stringResource(R.string.aerix_blocky_opt_library_sub), R.drawable.aerix_blocky_ic_library,
            BlockyTheme.BLUE),
        BlockyOption("wardrobe", stringResource(R.string.aerix_blocky_opt_wardrobe),
            stringResource(R.string.aerix_blocky_opt_wardrobe_sub), R.drawable.aerix_blocky_ic_wardrobe,
            BlockyTheme.VIOLET),
        BlockyOption("mod", stringResource(R.string.aerix_blocky_opt_mod),
            stringResource(R.string.aerix_blocky_opt_mod_sub), R.drawable.aerix_blocky_ic_mod,
            BlockyTheme.ORANGE),
        BlockyOption("friends", stringResource(R.string.aerix_blocky_opt_friends),
            stringResource(R.string.aerix_blocky_opt_friends_sub), R.drawable.aerix_blocky_ic_friends,
            BlockyTheme.CYAN),
        BlockyOption("discover", stringResource(R.string.aerix_blocky_opt_discover),
            stringResource(R.string.aerix_blocky_opt_discover_sub), R.drawable.aerix_blocky_ic_discover,
            BlockyTheme.PINK),
        BlockyOption("servers", stringResource(R.string.aerix_blocky_opt_servers),
            stringResource(R.string.aerix_blocky_opt_servers_sub), R.drawable.aerix_blocky_ic_globe,
            BlockyTheme.INDIGO),
        BlockyOption("accounts", stringResource(R.string.aerix_blocky_opt_accounts),
            stringResource(R.string.aerix_blocky_opt_accounts_sub), R.drawable.aerix_blocky_ic_accounts,
            BlockyTheme.MAGENTA),
        BlockyOption("settings", stringResource(R.string.aerix_blocky_opt_settings),
            stringResource(R.string.aerix_blocky_opt_settings_sub), R.drawable.aerix_blocky_ic_settings,
            BlockyTheme.SLATE),
        BlockyOption("content", stringResource(R.string.aerix_blocky_opt_content),
            stringResource(R.string.aerix_blocky_opt_content_sub), R.drawable.aerix_blocky_ic_folder,
            BlockyTheme.TEAL),
        BlockyOption("wallpapers", stringResource(R.string.aerix_blocky_opt_wallpapers),
            stringResource(R.string.aerix_blocky_opt_wallpapers_sub), R.drawable.aerix_blocky_ic_sun,
            BlockyTheme.GOLD),
        BlockyOption("chest", stringResource(R.string.aerix_blocky_opt_chest),
            stringResource(R.string.aerix_blocky_opt_chest_sub), R.drawable.aerix_blocky_ic_gift,
            BlockyTheme.COCOA, BlockyBadge.EXCLAMATION)
    )

    val state = BlockyState(
        selected = selected,
        instances = instances,
        account = account,
        installing = installing,
        progress = progress,
        emote = emote,
        emoteNonce = emoteNonce,
        emoteLabel = emoteLabel,
        autoEmotes = autoEmotes,
        instanceMenuOpen = instanceMenu,
        onEmoteChanged = { name ->
            emoteLabel = context.getString(BlockyEmote.fromId(name).label)
        },
        onEmote = { picked -> trigger(picked) },
        onToggleAuto = {
            val next = !AerixPreferences.blockyEmotes
            AerixPreferences.saveBlockyEmotes(next)
            autoEmotes = next
            BlockySound.play(if (next) BlockySound.Kind.OPEN else BlockySound.Kind.CLOSE)
            trigger(BlockyEmote.JUMP)
        },
        onOption = { action -> option(action) },
        onPlay = { option("play") },
        onChest = openChest,
        onHelp = { helpSheet = true },
        onMenu = { menuSheet = true },
        onInstanceMenu = { instanceMenu = it },
        onSelectInstance = { instance ->
            PojavInstanceBridge.select(instance)
            trigger(BlockyEmote.ACTION)
        },
        onLibrary = { option("library") }
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (AerixPreferences.blockyBackdrop) BlockyBackdrop()
        val wide = maxWidth >= 680.dp && maxWidth > maxHeight
        if (wide) {
            BlockyWideLayout(Modifier.fillMaxSize(), state, options)
        } else {
            BlockyCompactLayout(Modifier.fillMaxSize(), state, options)
        }

        if (menuSheet) {
            BlockyMenuSheet(
                onDismiss = { menuSheet = false },
                onOption = { action -> menuSheet = false; option(action) }
            )
        }
        if (helpSheet) {
            BlockyHelpSheet(
                activity = activity,
                onDismiss = { helpSheet = false },
                onOption = { action -> helpSheet = false; option(action) }
            )
        }
        if (rewardSheet) {
            BlockyRewardSheet(
                rewardId = rewardId,
                onDismiss = { rewardSheet = false },
                onApply = { id ->
                    AerixPreferences.saveSelectedWallpaper(id)
                    // Let the unlocked wallpaper show through immediately.
                    AerixPreferences.saveBlockyBackdrop(false)
                    trigger(BlockyEmote.DANCE)
                    BlockySound.play(BlockySound.Kind.REWARD)
                }
            )
        }
    }
}

/** Crisp, unblurred pixel art: the "blocky look" rule. */
@Composable
private fun BlockyBackdrop() {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(BLOCKY_PLAZA_ASSET).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B1A24))) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x44070C14), Color(0x00070C14), Color(0x99070C14))
                )
            )
        )
    }
}

@Composable
private fun BlockyWideLayout(modifier: Modifier, state: BlockyState, options: List<BlockyOption>) {
    Column(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BlockySunburst(Modifier.fillMaxWidth().height(12.dp))
        BlockyHeader(state)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The tall left panel from the sketch: character above, hard divider, emote deck below.
            BlockyStage(Modifier.weight(0.44f).fillMaxHeight(), state)
            // The stacked right panels: twelve differently themed options.
            BlockyBox(
                modifier = Modifier.weight(0.56f).fillMaxHeight(),
                fill = BlockyPanel,
                shadowOffset = 7.dp
            ) {
                BlockyOptionGrid(
                    modifier = Modifier.fillMaxSize().padding(9.dp),
                    options = options,
                    onOption = state.onOption,
                    columns = 2
                )
            }
        }
        BlockySpikes()
        BlockyDock(state)
    }
}

@Composable
private fun BlockyCompactLayout(modifier: Modifier, state: BlockyState, options: List<BlockyOption>) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BlockySunburst(Modifier.fillMaxWidth().height(12.dp))
        BlockyHeader(state)
        BlockyStage(Modifier.fillMaxWidth().height(400.dp), state)
        BlockyBox(modifier = Modifier.fillMaxWidth().height(480.dp), fill = BlockyPanel) {
            BlockyOptionGrid(
                modifier = Modifier.fillMaxSize().padding(9.dp),
                options = options,
                onOption = state.onOption,
                columns = 2
            )
        }
        BlockySpikes()
        BlockyDock(state)
    }
}

@Composable
private fun BlockyHeader(state: BlockyState) {
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BlockyBox(modifier = Modifier.size(58.dp), fill = BlockyTheme.INDIGO.fill, shadowOffset = 4.dp) {
            Image(
                painter = painterResource(R.drawable.aerix_blocky_ic_sun),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(36.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.aerix_brand).uppercase(Locale.ROOT),
                color = BlockyWhite,
                fontFamily = BlockyFont,
                fontSize = 19.sp,
                maxLines = 1
            )
            Text(
                text = stringResource(R.string.aerix_blocky_tagline),
                color = BlockyWhite,
                fontFamily = BlockyFont,
                fontSize = 8.sp,
                maxLines = 1
            )
        }
        BlockyIconButton(Modifier.size(44.dp), R.drawable.aerix_blocky_ic_grid,
            stringResource(R.string.aerix_blocky_menu), BlockyTheme.RED, state.onMenu)
        BlockyIconButton(Modifier.size(44.dp), R.drawable.aerix_blocky_ic_accounts,
            stringResource(R.string.aerix_blocky_opt_accounts), BlockyTheme.BLUE) {
            state.onOption("accounts")
        }
        BlockyIconButton(Modifier.size(44.dp), R.drawable.aerix_blocky_ic_settings,
            stringResource(R.string.aerix_blocky_opt_settings), BlockyTheme.SLATE) {
            state.onOption("settings")
        }
        BlockyIconButton(Modifier.size(44.dp), R.drawable.aerix_blocky_ic_sound,
            stringResource(R.string.aerix_blocky_sound), BlockyTheme.GOLD) {
            state.onOption("sound")
        }
        BlockyIconButton(Modifier.size(44.dp), R.drawable.aerix_blocky_ic_help,
            stringResource(R.string.aerix_blocky_help), BlockyTheme.TEAL, state.onHelp)
    }
}

/** The centre of the shell: a smooth, rotatable 3D character wearing the applied skin. */
@Composable
private fun BlockyStage(modifier: Modifier, state: BlockyState) {
    BlockyBox(
        modifier = modifier,
        fill = BlockyPanel,
        shadowOffset = 8.dp,
        pattern = BlockyTheme.INDIGO.pattern,
        patternColor = Color(0x14FFFFFF)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AerixSkinPreview3D(
                    account = state.account,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    azimuth = 22,
                    pitch = 8,
                    interactionEnabled = true,
                    isVisible = true,
                    useClientCape = true,
                    animated = true,
                    emote = state.emote,
                    emoteNonce = state.emoteNonce,
                    onEmoteChanged = state.onEmoteChanged
                )
                BlockyTag(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    text = state.account?.username
                        ?: stringResource(R.string.aerix_blocky_no_account)
                )
                BlockyTag(
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    text = stringResource(R.string.aerix_blocky_3d),
                    fill = BlockyTheme.CYAN.fill
                )
            }
            // The hard horizontal divider from the sketch.
            Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(BlockyOutline))
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BlockyTag(text = state.emoteLabel, fill = BlockyTheme.VIOLET.fill)
                    Box(modifier = Modifier.clickable(onClick = state.onToggleAuto)) {
                        BlockyTag(
                            text = stringResource(R.string.aerix_blocky_auto),
                            fill = if (state.autoEmotes) BlockyTheme.GRASS.fill else BlockyTheme.SLATE.fill
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(BlockyEmote.WAVE, BlockyEmote.DANCE, BlockyEmote.JUMP, BlockyEmote.ACTION)
                        .forEach { picked ->
                            BlockyButton(
                                modifier = Modifier.weight(1f).height(40.dp),
                                fill = picked.theme.fill,
                                onClick = { state.onEmote(picked) },
                                sound = BlockySound.Kind.TAP,
                                pattern = picked.theme.pattern,
                                patternColor = picked.theme.shade.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Image(
                                        painter = painterResource(picked.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(picked.label),
                                        color = BlockyWhite,
                                        fontFamily = BlockyFont,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        softWrap = false
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
private fun BlockyOptionGrid(
    modifier: Modifier,
    options: List<BlockyOption>,
    onOption: (String) -> Unit,
    columns: Int
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        options.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                rowItems.forEach { item ->
                    BlockyCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        theme = item.theme,
                        icon = item.icon,
                        title = item.title,
                        subtitle = item.subtitle,
                        badge = item.badge,
                        onClick = { onOption(item.action) }
                    )
                }
                repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BlockyDock(state: BlockyState) {
    BlockyBox(
        modifier = Modifier.fillMaxWidth().height(84.dp),
        fill = BlockyPanel,
        shadowOffset = 6.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BlockyInstanceCard(Modifier.weight(1.25f).fillMaxHeight(), state)
            BlockyPlayCard(Modifier.weight(0.9f).fillMaxHeight(), state)
            BlockyIconButton(
                Modifier.size(58.dp), R.drawable.aerix_blocky_ic_gift,
                stringResource(R.string.aerix_blocky_opt_chest), BlockyTheme.GOLD, state.onChest
            )
            BlockyIconButton(
                Modifier.size(58.dp), R.drawable.aerix_blocky_ic_help,
                stringResource(R.string.aerix_blocky_help), BlockyTheme.TEAL, state.onHelp
            )
        }
    }
}

@Composable
private fun BlockyInstanceCard(modifier: Modifier, state: BlockyState) {
    Box(modifier = modifier) {
        BlockyButton(
            modifier = Modifier.fillMaxSize(),
            fill = BlockyTheme.BLUE.fill,
            onClick = {
                if (state.instances.isEmpty()) state.onLibrary() else state.onInstanceMenu(true)
            },
            pattern = BlockyTheme.BLUE.pattern,
            patternColor = BlockyTheme.BLUE.shade.copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.aerix_blocky_ic_mod),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.aerix_blocky_start_here).uppercase(Locale.ROOT),
                        color = BlockyWhite,
                        fontFamily = BlockyFont,
                        fontSize = 8.sp,
                        maxLines = 1
                    )
                    Text(
                        text = state.selected?.name ?: stringResource(R.string.aerix_blocky_no_instance),
                        color = BlockyWhite,
                        fontFamily = BlockyFont,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.selected?.let { blockyVersion(it.versionId) }
                            ?: stringResource(R.string.aerix_blocky_instance_hint),
                        color = BlockyWhite,
                        fontFamily = BlockyFont,
                        fontSize = 8.sp,
                        maxLines = 1
                    )
                }
            }
        }
        DropdownMenu(expanded = state.instanceMenuOpen, onDismissRequest = { state.onInstanceMenu(false) }) {
            state.instances.forEach { instance ->
                DropdownMenuItem(
                    text = { Text(instance.name, maxLines = 1) },
                    onClick = {
                        state.onSelectInstance(instance)
                        state.onInstanceMenu(false)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.aerix_blocky_manage)) },
                onClick = {
                    state.onInstanceMenu(false)
                    state.onLibrary()
                }
            )
        }
    }
}

private fun blockyVersion(versionId: String): String =
    Regex("\\d+\\.\\d+(?:\\.\\d+)?").find(versionId)?.value ?: versionId

@Composable
private fun BlockyPlayCard(modifier: Modifier, state: BlockyState) {
    BlockyButton(
        modifier = modifier.guideAnchor(AerixGuideState.PLAY),
        fill = BlockyTheme.GRASS.fill,
        onClick = state.onPlay,
        sound = BlockySound.Kind.LAUNCH,
        pattern = BlockyTheme.GRASS.pattern,
        patternColor = BlockyTheme.GRASS.shade.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Image(
                    painter = painterResource(R.drawable.aerix_blocky_ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = if (state.installing) stringResource(R.string.aerix_blocky_assets)
                    else stringResource(R.string.aerix_blocky_play),
                    color = BlockyWhite,
                    fontFamily = BlockyFont,
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }
            if (state.installing) {
                BlockyStepsProgress(modifier = Modifier.fillMaxWidth(), progress = state.progress)
            } else {
                Text(
                    text = stringResource(R.string.aerix_blocky_play_sub),
                    color = BlockyWhite,
                    fontFamily = BlockyFont,
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BlockyDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB005070C))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        BlockyBox(
            modifier = Modifier.fillMaxWidth(0.86f).heightIn(min = 160.dp, max = 560.dp),
            fill = BlockyPanel,
            shadowOffset = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { content() }
        }
    }
}

@Composable
private fun BlockySheetAction(
    modifier: Modifier = Modifier,
    label: String,
    theme: BlockyTheme,
    onClick: () -> Unit
) {
    BlockyButton(
        modifier = modifier.height(46.dp),
        fill = theme.fill,
        onClick = onClick,
        pattern = theme.pattern,
        patternColor = theme.shade.copy(alpha = 0.4f)
    ) {
        Text(
            text = label.uppercase(Locale.ROOT),
            color = BlockyWhite,
            fontFamily = BlockyFont,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun BlockyMenuSheet(onDismiss: () -> Unit, onOption: (String) -> Unit) {
    val entries = listOf(
        R.string.aerix_blocky_menu_library to "library",
        R.string.aerix_blocky_opt_wardrobe to "wardrobe",
        R.string.aerix_blocky_opt_mod to "mod",
        R.string.aerix_blocky_opt_friends to "friends",
        R.string.aerix_blocky_opt_discover to "discover",
        R.string.aerix_blocky_opt_servers to "servers",
        R.string.aerix_blocky_opt_accounts to "accounts",
        R.string.aerix_blocky_opt_content to "content",
        R.string.aerix_blocky_opt_wallpapers to "wallpapers",
        R.string.aerix_blocky_menu_downloads to "downloads",
        R.string.aerix_blocky_menu_logs to "logs",
        R.string.aerix_blocky_menu_settings to "settings"
    )
    BlockyDialog(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.aerix_blocky_menu_title),
            color = BlockyWhite,
            fontFamily = BlockyFont,
            fontSize = 16.sp
        )
        var index = 0
        entries.forEach { (label, action) ->
            BlockySheetAction(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(label),
                theme = BlockyTheme.values()[index % BlockyTheme.values().size],
                onClick = { onOption(action) }
            )
            index++
        }
        BlockySheetAction(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.aerix_blocky_menu_classic),
            theme = BlockyTheme.RED,
            onClick = { onOption("classic") }
        )
        BlockySheetAction(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.aerix_blocky_close),
            theme = BlockyTheme.SLATE,
            onClick = onDismiss
        )
    }
}

@Composable
private fun BlockyHelpSheet(
    activity: FragmentActivity,
    onDismiss: () -> Unit,
    onOption: (String) -> Unit
) {
    BlockyDialog(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.aerix_blocky_help_title),
            color = BlockyWhite,
            fontFamily = BlockyFont,
            fontSize = 16.sp
        )
        Text(
            text = stringResource(R.string.aerix_blocky_help_body),
            color = BlockyWhite,
            fontFamily = BlockyFont,
            fontSize = 9.sp
        )
        BlockySheetAction(Modifier.fillMaxWidth(), stringResource(R.string.aerix_blocky_help_guide),
            BlockyTheme.GRASS) { onOption("guide") }
        BlockySheetAction(Modifier.fillMaxWidth(), stringResource(R.string.aerix_blocky_help_invite),
            BlockyTheme.CYAN) { onOption("invite") }
        BlockySheetAction(
            Modifier.fillMaxWidth(), stringResource(R.string.aerix_blocky_help_website),
            BlockyTheme.BLUE
        ) { onDismiss(); PojavNavigator.openWebsite(activity) }
        BlockySheetAction(
            Modifier.fillMaxWidth(), stringResource(R.string.aerix_blocky_help_discord),
            BlockyTheme.VIOLET
        ) { onDismiss(); PojavNavigator.openDiscord(activity) }
        BlockySheetAction(
            Modifier.fillMaxWidth(), stringResource(R.string.aerix_blocky_help_logs),
            BlockyTheme.GOLD
        ) { onDismiss(); PojavNavigator.shareLogs(activity) }
        BlockySheetAction(Modifier.fillMaxWidth(), stringResource(R.string.aerix_blocky_close),
            BlockyTheme.SLATE, onDismiss)
    }
}

@Composable
private fun BlockyRewardSheet(rewardId: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    val preset = AerixThemeManager.wallpapers.firstOrNull { it.id == rewardId }
    BlockyDialog(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.aerix_blocky_reward_title),
            color = BlockyWhite,
            fontFamily = BlockyFont,
            fontSize = 16.sp
        )
        Text(
            text = preset?.title ?: stringResource(R.string.aerix_blocky_chest_empty),
            color = BlockyWhite,
            fontFamily = BlockyFont,
            fontSize = 14.sp
        )
        Text(
            text = preset?.biome ?: "",
            color = BlockyWhite,
            fontFamily = BlockyFont,
            fontSize = 9.sp
        )
        BlockySheetAction(
            Modifier.fillMaxWidth(), stringResource(R.string.aerix_blocky_reward_apply),
            BlockyTheme.GRASS
        ) {
            if (preset != null) onApply(preset.id)
            onDismiss()
        }
        BlockySheetAction(
            Modifier.fillMaxWidth(), stringResource(R.string.aerix_blocky_close),
            BlockyTheme.SLATE, onDismiss
        )
    }
}
