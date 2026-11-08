/*
 * Aerix Millida Shell - HOME in Millida's visual language.
 *
 * Reference: millida/launcher-mobile screenshots (lobby, builds, character, catalog, more) and
 * millida/launcher 2.0 lobby. Near-black chrome, notched/stepped pixel panels with hard shadows,
 * a green grass character stage, a blue "START HERE" instance card, a big lime-green PLAY button,
 * "MY BUILDS" tiles and a five-tab bottom bar. See docs/millida-shell.md.
 *
 * This is a real launcher surface: PLAY launches, the build tiles select real instances, and every
 * card opens an existing Aerix destination. The 3D character keeps the living emote engine
 * (autonomous emotes, reactions to downloads/wardrobe/account changes, square-wave sounds).
 */
package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
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
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyEmote
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockyReactions
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockySound
import net.kdt.pojavlaunch.aerix.ui.millida.MillidaButton
import net.kdt.pojavlaunch.aerix.ui.millida.MillidaChip
import net.kdt.pojavlaunch.aerix.ui.millida.MillidaEntryCard
import net.kdt.pojavlaunch.aerix.ui.millida.MillidaFont
import net.kdt.pojavlaunch.aerix.ui.millida.MillidaFrame
import net.kdt.pojavlaunch.aerix.ui.millida.MillidaIconButton
import net.kdt.pojavlaunch.aerix.ui.millida.MillidaPalette
import net.kdt.pojavlaunch.aerix.ui.millida.MillidaPromoCard
import net.kdt.pojavlaunch.aerix.ui.millida.MillidaSectionTitle
import net.kdt.pojavlaunch.aerix.ui.millida.MillidaTab
import net.kdt.pojavlaunch.aerix.ui.millida.MillidaTabBar
import net.kdt.pojavlaunch.aerix.ui.millida.MillidaTone
import net.kdt.pojavlaunch.aerix.ui.millida.millidaScene
import java.util.Locale
import kotlin.random.Random

private const val TAB_PLAY = "play"
private const val TAB_CATALOG = "catalog"
private const val TAB_CHARACTER = "character"
private const val TAB_SHOP = "shop"
private const val TAB_MORE = "more"

private data class MillidaEntry(
    val action: String,
    val label: String,
    val subtitle: String,
    val icon: Int,
    val tone: MillidaTone,
)

private class MillidaState(
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
    val onTab: (String) -> Unit,
    val onInstanceMenu: (Boolean) -> Unit,
    val onSelectInstance: (AerixInstance) -> Unit,
    val onLibrary: () -> Unit,
    val onChest: () -> Unit,
    val onHelp: () -> Unit,
    val onInvite: () -> Unit,
)

@Composable
fun MillidaShellScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
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

    var activeTab by rememberSaveable { mutableStateOf(TAB_PLAY) }
    var menuSheet by remember { mutableStateOf(false) }
    var helpSheet by remember { mutableStateOf(false) }
    var rewardSheet by remember { mutableStateOf(false) }
    var rewardId by remember { mutableStateOf("") }
    var instanceMenu by remember { mutableStateOf(false) }
    var autoEmotes by remember { mutableStateOf(AerixPreferences.blockyEmotes) }

    var emote by remember { mutableStateOf<String?>(null) }
    var emoteNonce by remember { mutableIntStateOf(0) }
    var emoteLabel by remember { mutableStateOf(context.getString(R.string.aerix_blocky_emote_idle)) }

    /** The character acts on its own; idle is only the resting state. */
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
    var lastProgress by remember { mutableFloatStateOf(progress) }
    LaunchedEffect(progress) {
        if (progress > lastProgress + 0.04f) {
            lastProgress = progress
            if (emoteNonce % 2 == 0) trigger(BlockyEmote.ACTION)
        }
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
            "discord" -> PojavNavigator.openDiscord(activity)
            "website" -> PojavNavigator.openWebsite(activity)
            "sharelogs" -> PojavNavigator.shareLogs(activity)
            "classic" -> {
                AerixPreferences.saveMillidaUi(false)
                AerixPreferences.saveBlockyUi(false)
            }
            "blocky" -> AerixPreferences.saveMillidaUi(false)
            "sound" -> AerixPreferences.saveBlockySound(!AerixPreferences.blockySound)
        }
    }

    val tabs = listOf(
        MillidaTab(TAB_PLAY, stringResource(R.string.aerix_millida_tab_play), R.drawable.aerix_millida_ic_grass),
        MillidaTab(TAB_CATALOG, stringResource(R.string.aerix_millida_tab_catalog), R.drawable.aerix_millida_ic_book),
        MillidaTab(TAB_CHARACTER, stringResource(R.string.aerix_millida_tab_character), R.drawable.aerix_millida_ic_player),
        MillidaTab(TAB_SHOP, stringResource(R.string.aerix_millida_tab_shop), R.drawable.aerix_millida_ic_emerald),
        MillidaTab(TAB_MORE, stringResource(R.string.aerix_millida_tab_more), R.drawable.aerix_millida_ic_dots),
    )

    val entries = listOf(
        MillidaEntry("library", stringResource(R.string.aerix_millida_entry_library),
            stringResource(R.string.aerix_millida_entry_library_sub), R.drawable.aerix_millida_ic_chest, MillidaTone.ORANGE),
        MillidaEntry("discover", stringResource(R.string.aerix_millida_entry_discover),
            stringResource(R.string.aerix_millida_entry_discover_sub), R.drawable.aerix_millida_ic_book, MillidaTone.BLUE),
        MillidaEntry("content", stringResource(R.string.aerix_millida_entry_content),
            stringResource(R.string.aerix_millida_entry_content_sub), R.drawable.aerix_millida_ic_emerald, MillidaTone.TEAL),
        MillidaEntry("wallpapers", stringResource(R.string.aerix_millida_entry_wallpapers),
            stringResource(R.string.aerix_millida_entry_wallpapers_sub), R.drawable.aerix_millida_ic_sparkle, MillidaTone.PURPLE),
        MillidaEntry("downloads", stringResource(R.string.aerix_millida_entry_downloads),
            stringResource(R.string.aerix_millida_entry_downloads_sub), R.drawable.aerix_millida_ic_arrow, MillidaTone.YELLOW),
        MillidaEntry("settings", stringResource(R.string.aerix_millida_entry_logs),
            stringResource(R.string.aerix_millida_entry_logs_sub), R.drawable.aerix_millida_ic_settings, MillidaTone.RED),
        MillidaEntry("chest", stringResource(R.string.aerix_millida_entry_chest),
            stringResource(R.string.aerix_millida_entry_chest_sub), R.drawable.aerix_millida_ic_gift, MillidaTone.PINK),
        MillidaEntry("help", stringResource(R.string.aerix_millida_entry_help),
            stringResource(R.string.aerix_millida_entry_help_sub), R.drawable.aerix_millida_ic_wrench, MillidaTone.GREEN),
    )

    val state = MillidaState(
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
        onEmoteChanged = { id -> emoteLabel = context.getString(BlockyEmote.fromId(id).label) },
        onEmote = { next -> trigger(next) },
        onToggleAuto = {
            autoEmotes = !autoEmotes
            AerixPreferences.saveBlockyEmotes(autoEmotes)
            trigger(BlockyEmote.JUMP)
        },
        onOption = { option(it) },
        onPlay = { option("play") },
        onTab = { id ->
            activeTab = id
            when (id) {
                TAB_PLAY -> Unit
                TAB_CATALOG -> option("discover")
                TAB_CHARACTER -> option("wardrobe")
                TAB_SHOP -> option("content")
                TAB_MORE -> menuSheet = true
            }
        },
        onInstanceMenu = { instanceMenu = it },
        onSelectInstance = { instance -> PojavInstanceBridge.select(instance); react("instance") },
        onLibrary = { option("library") },
        onChest = openChest,
        onHelp = { helpSheet = true },
        onInvite = invite,
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MillidaPalette.Bg)
    ) {
        val wide = maxWidth >= 760.dp && maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            MillidaTopBar(state)
            if (wide) {
                MillidaWideLayout(Modifier.weight(1f), state, entries)
            } else {
                MillidaCompactLayout(Modifier.weight(1f), state, entries)
            }
            MillidaTabBar(tabs, activeTab, state.onTab)
        }

        if (menuSheet) {
            MillidaMoreSheet(
                state = state,
                entries = entries,
                onDismiss = { menuSheet = false; activeTab = TAB_PLAY },
                onOption = { action -> menuSheet = false; option(action) },
            )
        }
        if (helpSheet) {
            MillidaHelpSheet(
                state = state,
                onDismiss = { helpSheet = false },
                onOption = { action -> helpSheet = false; option(action) },
            )
        }
        if (rewardSheet) {
            MillidaRewardSheet(
                rewardId = rewardId,
                onDismiss = { rewardSheet = false },
                onApply = { id ->
                    AerixPreferences.saveSelectedWallpaper(id)
                    trigger(BlockyEmote.DANCE)
                    BlockySound.play(BlockySound.Kind.REWARD)
                },
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Top chrome                                                                                 */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun MillidaTopBar(state: MillidaState) {
    val account = state.account
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MillidaPalette.Bar)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MillidaFrame(
            modifier = Modifier.height(48.dp).widthIn(min = 150.dp, max = 230.dp),
            fill = MillidaPalette.Panel,
            notch = 5.dp,
            shadow = 4.dp,
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (account?.face != null) {
                    Image(account.face!!, null, Modifier.size(30.dp))
                } else {
                    Image(painterResource(R.drawable.aerix_millida_ic_player), null, Modifier.size(30.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        account?.username ?: stringResource(R.string.aerix_millida_no_account),
                        color = MillidaPalette.Text,
                        fontFamily = MillidaFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            account == null -> stringResource(R.string.aerix_millida_account_signin)
                            account.expired -> stringResource(R.string.aerix_millida_account_expired)
                            else -> stringResource(R.string.aerix_millida_account_ready)
                        },
                        color = MillidaPalette.Muted,
                        fontFamily = MillidaFont,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        MillidaIconButton(
            R.drawable.aerix_millida_ic_friends,
            stringResource(R.string.aerix_millida_tab_more),
            { state.onOption("friends") },
            size = 44.dp,
        )
        Spacer(Modifier.width(8.dp))
        MillidaIconButton(
            R.drawable.aerix_millida_ic_settings,
            stringResource(R.string.aerix_millida_entry_logs),
            { state.onOption("settings") },
            size = 44.dp,
        )
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Character stage                                                                            */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun MillidaCharacterStage(state: MillidaState, modifier: Modifier = Modifier) {
    Box(modifier.millidaScene()) {
        AerixSkinPreview3D(
            account = state.account,
            modifier = Modifier.fillMaxSize().padding(bottom = 36.dp),
            azimuth = 22,
            pitch = 8,
            interactionEnabled = true,
            isVisible = true,
            useClientCape = true,
            animated = true,
            emote = state.emote,
            emoteNonce = state.emoteNonce,
            onEmoteChanged = state.onEmoteChanged,
        )

        // Top-left: the character is alive and the auto-emote engine can be toggled.
        Column(Modifier.align(Alignment.TopStart).padding(10.dp)) {
            MillidaChip(
                stringResource(R.string.aerix_millida_auto),
                state.onToggleAuto,
                active = state.autoEmotes,
            )
            Spacer(Modifier.height(6.dp))
            MillidaChip(state.emoteLabel.uppercase(Locale.ROOT), { state.onEmote(BlockyEmote.WAVE) })
        }

        // Top-right: Millida's pose chips. They drive the same real emote engine.
        Column(
            Modifier.align(Alignment.TopEnd).padding(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            MillidaChip(
                stringResource(R.string.aerix_millida_pose_stand),
                { state.onEmote(BlockyEmote.IDLE) },
                active = state.emoteLabel.equals("Idle", ignoreCase = true),
            )
            Spacer(Modifier.height(6.dp))
            MillidaChip(
                stringResource(R.string.aerix_millida_pose_wave),
                { state.onEmote(BlockyEmote.WAVE) },
            )
            Spacer(Modifier.height(6.dp))
            MillidaChip(
                stringResource(R.string.aerix_millida_pose_dance),
                { state.onEmote(BlockyEmote.DANCE) },
                active = state.emoteLabel.equals("Dancing", ignoreCase = true),
            )
        }

        // Bottom: Millida's party / invite chips sitting just above the grass.
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MillidaChip(
                stringResource(R.string.aerix_millida_find_party),
                { state.onOption("friends") },
                iconRes = R.drawable.aerix_millida_ic_friends,
            )
            MillidaChip(
                stringResource(R.string.aerix_millida_invite),
                state.onInvite,
                iconRes = R.drawable.aerix_millida_ic_gift,
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* START HERE + PLAY                                                                          */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun MillidaStartCard(state: MillidaState, modifier: Modifier = Modifier) {
    Box(modifier) {
        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        MillidaFrame(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clickable(interactionSource = interaction, indication = null) {
                    BlockySound.play(BlockySound.Kind.TAP)
                    if (state.instances.isEmpty()) state.onLibrary() else state.onInstanceMenu(true)
                },
            fill = MillidaPalette.Blue,
            notch = 7.dp,
            shadow = 6.dp,
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MillidaFrame(
                    modifier = Modifier.size(58.dp),
                    fill = MillidaPalette.BlueDark,
                    notch = 4.dp,
                    shadow = 3.dp,
                ) {
                    Image(painterResource(R.drawable.aerix_millida_ic_chest), null, Modifier.size(32.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.aerix_millida_start_here),
                        color = MillidaPalette.Yellow,
                        fontFamily = MillidaFont,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                    Text(
                        state.selected?.name ?: stringResource(R.string.aerix_millida_no_instance),
                        color = Color.White,
                        fontFamily = MillidaFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.selected?.let { millidaVersion(it.versionId) }
                            ?: stringResource(R.string.aerix_millida_instance_hint),
                        color = Color(0xFFD8E2F8),
                        fontFamily = MillidaFont,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        DropdownMenu(expanded = state.instanceMenuOpen, onDismissRequest = { state.onInstanceMenu(false) }) {
            state.instances.forEach { instance ->
                DropdownMenuItem(
                    text = { Text(instance.name, maxLines = 1) },
                    onClick = { state.onSelectInstance(instance); state.onInstanceMenu(false) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.aerix_millida_manage)) },
                onClick = { state.onInstanceMenu(false); state.onLibrary() },
            )
        }
    }
}

private fun millidaVersion(versionId: String): String =
    Regex("""\d+\.\d+(?:\.\d+)?""").find(versionId)?.value ?: versionId

@Composable
private fun MillidaPlayRow(state: MillidaState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MillidaButton(
            label = if (state.installing) {
                "${stringResource(R.string.aerix_millida_downloading)} ${(state.progress * 100).toInt()}%"
            } else {
                stringResource(R.string.aerix_millida_play)
            },
            onClick = state.onPlay,
            modifier = Modifier.weight(1f).guideAnchor(AerixGuideState.PLAY),
            iconRes = R.drawable.aerix_millida_ic_arrow,
            height = 62.dp,
            textSize = 19,
            sound = BlockySound.Kind.LAUNCH,
        )
    }
    Spacer(Modifier.height(8.dp))
    MillidaButton(
        label = stringResource(R.string.aerix_millida_build),
        onClick = state.onLibrary,
        modifier = Modifier.width(128.dp),
        fill = MillidaPalette.PanelSoft,
        textColor = MillidaPalette.Text,
        height = 40.dp,
        textSize = 12,
    )
}

/* ------------------------------------------------------------------------------------------ */
/* Build tiles                                                                                */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun MillidaBuildTile(
    label: String,
    subtitle: String,
    iconRes: Int,
    tone: MillidaTone?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    MillidaFrame(
        modifier = modifier
            .height(132.dp)
            .clickable(interactionSource = interaction, indication = null) {
                BlockySound.play(BlockySound.Kind.OPEN)
                onClick()
            },
        fill = tone?.fill ?: MillidaPalette.Panel,
        pressed = pressed,
        notch = 6.dp,
        shadow = 5.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(painterResource(iconRes), null, Modifier.size(42.dp))
            }
            if (tone != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(tone.dark)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                ) {
                    Column {
                        Text(label, color = Color.White, fontFamily = MillidaFont, fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, color = Color(0xFFE2E2E8), fontFamily = MillidaFont,
                            fontSize = 9.sp, maxLines = 1)
                    }
                }
            } else {
                Text(label, color = MillidaPalette.Text, fontFamily = MillidaFont,
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MillidaBuildRow(state: MillidaState, modifier: Modifier = Modifier) {
    val builds = state.instances.take(2)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MillidaBuildTile(
            stringResource(R.string.aerix_millida_new_build),
            "", R.drawable.aerix_millida_ic_plus, null,
            { state.onOption("library") },
            Modifier.weight(1f),
        )
        builds.forEach { instance ->
            MillidaBuildTile(
                instance.name,
                millidaVersion(instance.versionId),
                R.drawable.aerix_millida_ic_chest,
                MillidaTone.ORANGE,
                { state.onSelectInstance(instance) },
                Modifier.weight(1f),
            )
        }
        if (builds.isEmpty()) {
            MillidaBuildTile(
                stringResource(R.string.aerix_millida_manage),
                "", R.drawable.aerix_millida_ic_chest, MillidaTone.BLUE,
                state.onLibrary,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MillidaPromoRow(state: MillidaState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MillidaPromoCard(
            stringResource(R.string.aerix_millida_card_shop),
            stringResource(R.string.aerix_millida_card_shop_sub),
            R.drawable.aerix_millida_ic_gift, MillidaTone.PINK,
            { state.onOption("content") }, Modifier.weight(1f), height = 116.dp,
        )
        MillidaPromoCard(
            stringResource(R.string.aerix_millida_card_skin),
            stringResource(R.string.aerix_millida_card_skin_sub),
            R.drawable.aerix_millida_ic_skin, MillidaTone.BLUE,
            { state.onOption("wardrobe") }, Modifier.weight(1f), height = 116.dp,
        )
        MillidaPromoCard(
            stringResource(R.string.aerix_millida_card_friends),
            stringResource(R.string.aerix_millida_card_friends_sub),
            R.drawable.aerix_millida_ic_friends, MillidaTone.GREEN,
            { state.onOption("friends") }, Modifier.weight(1f), height = 116.dp,
        )
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Layouts                                                                                    */
/* ------------------------------------------------------------------------------------------ */

/** Portrait: Millida's mobile lobby, one hard-edged scrolling column above the bottom bar. */
@Composable
private fun MillidaCompactLayout(modifier: Modifier, state: MillidaState, entries: List<MillidaEntry>) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MillidaCharacterStage(state, Modifier.fillMaxWidth().height(300.dp))
        MillidaStartCard(state)
        MillidaPlayRow(state)
        MillidaSectionTitle(
            stringResource(R.string.aerix_millida_my_builds),
            actionLabel = stringResource(R.string.aerix_millida_all),
            onAction = state.onLibrary,
        )
        MillidaBuildRow(state)
        MillidaPromoRow(state)
        MillidaSectionTitle(
            stringResource(R.string.aerix_millida_where_to_play),
            actionLabel = stringResource(R.string.aerix_millida_all),
            onAction = { state.onOption("servers") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MillidaEntryCard(
                stringResource(R.string.aerix_millida_card_servers),
                stringResource(R.string.aerix_millida_servers_sub),
                R.drawable.aerix_millida_ic_globe,
                { state.onOption("servers") }, Modifier.weight(1f), MillidaTone.RED,
            )
            MillidaEntryCard(
                stringResource(R.string.aerix_millida_card_discord),
                stringResource(R.string.aerix_millida_card_discord_sub),
                R.drawable.aerix_millida_ic_discord,
                { state.onOption("discord") }, Modifier.weight(1f), MillidaTone.PURPLE,
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** Landscape: Millida 2.0 desktop lobby - promo column, central character, dock at the bottom. */
@Composable
private fun MillidaWideLayout(modifier: Modifier, state: MillidaState, entries: List<MillidaEntry>) {
    Row(modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.width(190.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MillidaPromoCard(
                stringResource(R.string.aerix_millida_card_shop),
                stringResource(R.string.aerix_millida_card_shop_sub),
                R.drawable.aerix_millida_ic_gift, MillidaTone.PINK,
                { state.onOption("content") }, Modifier.fillMaxWidth(), height = 104.dp,
            )
            MillidaPromoCard(
                stringResource(R.string.aerix_millida_card_skin),
                stringResource(R.string.aerix_millida_card_skin_sub),
                R.drawable.aerix_millida_ic_skin, MillidaTone.BLUE,
                { state.onOption("wardrobe") }, Modifier.fillMaxWidth(), height = 104.dp,
            )
            MillidaPromoCard(
                stringResource(R.string.aerix_millida_card_friends),
                stringResource(R.string.aerix_millida_card_friends_sub),
                R.drawable.aerix_millida_ic_friends, MillidaTone.GREEN,
                { state.onOption("friends") }, Modifier.fillMaxWidth(), height = 104.dp,
            )
        }

        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MillidaCharacterStage(state, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MillidaStartCard(state, Modifier.weight(1.3f))
                MillidaButton(
                    label = if (state.installing) {
                        "${stringResource(R.string.aerix_millida_downloading)} ${(state.progress * 100).toInt()}%"
                    } else {
                        stringResource(R.string.aerix_millida_play)
                    },
                    onClick = state.onPlay,
                    modifier = Modifier.weight(0.85f).height(84.dp).guideAnchor(AerixGuideState.PLAY),
                    iconRes = R.drawable.aerix_millida_ic_arrow,
                    height = 84.dp,
                    textSize = 20,
                    sound = BlockySound.Kind.LAUNCH,
                )
            }
        }

        Column(
            Modifier.width(190.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MillidaSectionTitle(stringResource(R.string.aerix_millida_my_builds))
            MillidaBuildTile(
                stringResource(R.string.aerix_millida_new_build), "",
                R.drawable.aerix_millida_ic_plus, null,
                state.onLibrary, Modifier.fillMaxWidth(),
            )
            state.instances.take(2).forEach { instance ->
                MillidaBuildTile(
                    instance.name, millidaVersion(instance.versionId),
                    R.drawable.aerix_millida_ic_chest, MillidaTone.ORANGE,
                    { state.onSelectInstance(instance) }, Modifier.fillMaxWidth(),
                )
            }
            entries.take(2).forEach { entry ->
                MillidaEntryCard(
                    entry.label, entry.subtitle, entry.icon,
                    { state.onOption(entry.action) }, Modifier.fillMaxWidth(), entry.tone,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Sheets                                                                                     */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun MillidaDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xB0000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        MillidaFrame(
            Modifier.fillMaxWidth(0.92f).heightIn(min = 180.dp, max = 620.dp),
            fill = MillidaPalette.Panel,
            notch = 8.dp,
            shadow = 8.dp,
        ) {
            Column(
                Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { content() }
        }
    }
}

@Composable
private fun MillidaSheetAction(
    label: String,
    tone: MillidaTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MillidaButton(
        label = label.uppercase(Locale.ROOT),
        onClick = onClick,
        modifier = modifier,
        fill = tone.fill,
        textColor = Color.White,
        height = 46.dp,
        textSize = 11,
    )
}

/** Millida's "More" screen as a notched overlay: coloured cards, then the dark entry grid. */
@Composable
private fun MillidaMoreSheet(
    state: MillidaState,
    entries: List<MillidaEntry>,
    onDismiss: () -> Unit,
    onOption: (String) -> Unit,
) {
    MillidaDialog(onDismiss) {
        Text(
            stringResource(R.string.aerix_millida_more_title),
            color = MillidaPalette.Text, fontFamily = MillidaFont,
            fontWeight = FontWeight.Bold, fontSize = 20.sp,
        )
        MillidaFrame(Modifier.fillMaxWidth().height(62.dp), fill = MillidaPalette.PanelSoft,
            contentAlignment = Alignment.CenterStart) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.aerix_millida_ic_player), null, Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(state.account?.username ?: stringResource(R.string.aerix_millida_no_account),
                        color = MillidaPalette.Text, fontFamily = MillidaFont, fontSize = 13.sp)
                    Text(stringResource(R.string.aerix_millida_account_ready),
                        color = MillidaPalette.Muted, fontFamily = MillidaFont, fontSize = 9.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MillidaPromoCard(
                stringResource(R.string.aerix_millida_card_friends),
                stringResource(R.string.aerix_millida_card_friends_sub),
                R.drawable.aerix_millida_ic_friends, MillidaTone.BLUE,
                { onOption("friends") }, Modifier.weight(1f), height = 108.dp,
            )
            MillidaPromoCard(
                stringResource(R.string.aerix_millida_card_servers),
                stringResource(R.string.aerix_millida_servers_sub),
                R.drawable.aerix_millida_ic_globe, MillidaTone.ORANGE,
                { onOption("servers") }, Modifier.weight(1f), height = 108.dp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MillidaPromoCard(
                stringResource(R.string.aerix_millida_card_client),
                stringResource(R.string.aerix_millida_card_client_sub),
                R.drawable.aerix_millida_ic_controller, MillidaTone.TEAL,
                { onOption("mod") }, Modifier.weight(1f), height = 108.dp,
            )
            MillidaPromoCard(
                stringResource(R.string.aerix_millida_card_news),
                stringResource(R.string.aerix_millida_card_news_sub),
                R.drawable.aerix_millida_ic_crown, MillidaTone.PINK,
                { onOption("chest") }, Modifier.weight(1f), height = 108.dp,
            )
        }
        entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { entry ->
                    MillidaEntryCard(
                        entry.label, entry.subtitle, entry.icon,
                        { onOption(entry.action) }, Modifier.weight(1f), entry.tone,
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        MillidaSheetAction(
            stringResource(R.string.aerix_millida_help),
            MillidaTone.GREEN, { onOption("help") }, Modifier.fillMaxWidth(),
        )
        MillidaSheetAction(
            stringResource(R.string.aerix_millida_sound),
            if (AerixPreferences.blockySound) MillidaTone.PURPLE else MillidaTone.YELLOW,
            { onOption("sound") }, Modifier.fillMaxWidth(),
        )
        MillidaSheetAction(
            stringResource(R.string.aerix_millida_classic),
            MillidaTone.RED, { onOption("classic") }, Modifier.fillMaxWidth(),
        )
        MillidaSheetAction(
            stringResource(R.string.aerix_millida_close),
            MillidaTone.BLUE, onDismiss, Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MillidaHelpSheet(
    state: MillidaState,
    onDismiss: () -> Unit,
    onOption: (String) -> Unit,
) {
    MillidaDialog(onDismiss) {
        Text(
            stringResource(R.string.aerix_millida_help_title),
            color = MillidaPalette.Text, fontFamily = MillidaFont,
            fontWeight = FontWeight.Bold, fontSize = 18.sp,
        )
        Text(
            stringResource(R.string.aerix_blocky_help_body),
            color = MillidaPalette.Muted, fontFamily = MillidaFont, fontSize = 11.sp,
        )
        MillidaSheetAction(stringResource(R.string.aerix_blocky_help_guide),
            MillidaTone.GREEN, { onOption("guide") }, Modifier.fillMaxWidth())
        MillidaSheetAction(stringResource(R.string.aerix_blocky_help_invite),
            MillidaTone.BLUE, { onOption("invite") }, Modifier.fillMaxWidth())
        MillidaSheetAction(stringResource(R.string.aerix_blocky_help_website),
            MillidaTone.ORANGE, { onOption("website") }, Modifier.fillMaxWidth())
        MillidaSheetAction(stringResource(R.string.aerix_blocky_help_discord),
            MillidaTone.PURPLE, { onOption("discord") }, Modifier.fillMaxWidth())
        MillidaSheetAction(stringResource(R.string.aerix_blocky_help_logs),
            MillidaTone.TEAL, { onOption("sharelogs") }, Modifier.fillMaxWidth())
        MillidaSheetAction(stringResource(R.string.aerix_millida_close),
            MillidaTone.RED, onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MillidaRewardSheet(
    rewardId: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    MillidaDialog(onDismiss) {
        Text(
            stringResource(R.string.aerix_millida_reward_title),
            color = MillidaPalette.Yellow, fontFamily = MillidaFont,
            fontWeight = FontWeight.Bold, fontSize = 18.sp,
        )
        MillidaFrame(Modifier.fillMaxWidth().height(96.dp), fill = MillidaPalette.GreenDeep) {
            Image(painterResource(R.drawable.aerix_millida_ic_chest), null, Modifier.size(58.dp))
        }
        Text(
            rewardId.ifEmpty { stringResource(R.string.aerix_blocky_chest_empty) },
            color = MillidaPalette.Text, fontFamily = MillidaFont, fontSize = 12.sp,
        )
        if (rewardId.isNotEmpty()) {
            MillidaSheetAction(stringResource(R.string.aerix_blocky_reward_apply),
                MillidaTone.GREEN, { onApply(rewardId); onDismiss() }, Modifier.fillMaxWidth())
        }
        MillidaSheetAction(stringResource(R.string.aerix_millida_close),
            MillidaTone.BLUE, onDismiss, Modifier.fillMaxWidth())
    }
}
