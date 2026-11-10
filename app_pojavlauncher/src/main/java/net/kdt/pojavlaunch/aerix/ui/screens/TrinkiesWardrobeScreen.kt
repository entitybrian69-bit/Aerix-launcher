package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixSkinPreset
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.client.AerixClientCosmetics
import net.kdt.pojavlaunch.aerix.client.AerixClientStore
import net.kdt.pojavlaunch.aerix.client.TrinkiesCatalog
import net.kdt.pojavlaunch.aerix.client.TrinkiesCatalogItem
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import java.util.concurrent.atomic.AtomicBoolean

private enum class WardrobeCategory(val title: String, val icon: Int) {
    LOOKS("Looks", R.drawable.aerix_ic_style_outlined),
    SHOP("Shop", R.drawable.aerix_ic_cloud_download),
    SKINS("Skins", R.drawable.aerix_ic_person_outlined),
    CAPES("Capes", R.drawable.aerix_ic_style_outlined),
    HEADWEAR("Head", R.drawable.aerix_ic_styler_outlined),
    SUITS("Body", R.drawable.aerix_ic_format_paint_outlined),
    BACK_ITEMS("Back", R.drawable.aerix_ic_favorite_outlined),
    PETS("Pets", R.drawable.aerix_ic_people),
    EMOTES("Emotes", R.drawable.aerix_ic_touch_app_outlined),
    AURAS("Auras", R.drawable.aerix_ic_styler_outlined)
}

private enum class WardrobeFilter(val title: String) {
    MINE("Mine"), POPULAR("Popular"), NEW("New"), RANDOM("Random")
}

private val wardrobeCategories = listOf(
    WardrobeCategory.LOOKS,
    WardrobeCategory.SHOP,
    WardrobeCategory.SKINS,
    WardrobeCategory.CAPES,
    WardrobeCategory.HEADWEAR,
    WardrobeCategory.SUITS,
    WardrobeCategory.BACK_ITEMS,
    WardrobeCategory.PETS,
    WardrobeCategory.EMOTES,
    WardrobeCategory.AURAS
)

private val wardrobeGreen = Color(0xFF5CC84B)
private val wardrobeCanvas = Color(0xFF18191B)
private val wardrobePanel = Color(0xFF222427)
private val wardrobePanelRaised = Color(0xFF2A2C30)
private val wardrobePixelFont = FontFamily(Font(R.font.minecraft_ten))

private data class TrinkiesRarityStyle(val background: Color, val accent: Color)

private val trinkiesRarityStyles = mapOf(
    "common" to TrinkiesRarityStyle(Color(0xFF1D6233), Color(0xFF82E394)),
    "epic" to TrinkiesRarityStyle(Color(0xFF4D2674), Color(0xFFD0A0FF)),
    "legendary" to TrinkiesRarityStyle(Color(0xFF76540C), Color(0xFFFFDF75)),
    "mythic" to TrinkiesRarityStyle(Color(0xFF7A2028), Color(0xFFFF8989)),
    "eternal" to TrinkiesRarityStyle(Color(0xFF59646F), Color(0xFFE5EBF0))
)

private fun trinkiesRarityStyle(rarity: String): TrinkiesRarityStyle =
    trinkiesRarityStyles[rarity.lowercase()] ?: TrinkiesRarityStyle(wardrobePanelRaised, wardrobeGreen)

private data class WardrobePose(val label: String, val animation: String)

private val wardrobePoses = listOf(
    WardrobePose("Idle", "NewIdle"),
    WardrobePose("Wave", "Wave"),
    WardrobePose("Walk", "Walking"),
    WardrobePose("Run", "Running"),
    WardrobePose("Fly", "Flying"),
    WardrobePose("Crouch", "Crouch")
)

/** Trinkies cosmetics hub, arranged like the compact pixel-art wardrobe reference. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrinkiesWardrobeScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val accent = wardrobeGreen
    val account = PojavAccountBridge.current
    var category by remember { mutableStateOf(WardrobeCategory.SKINS) }
    var filter by remember { mutableStateOf(WardrobeFilter.MINE) }
    var pose by remember { mutableStateOf("NewIdle") }
    var savedSkins by remember { mutableStateOf<List<AerixSkinPreset>>(emptyList()) }
    val wardrobeRevision = PojavAccountBridge.wardrobeRevision
    val configuration = LocalConfiguration.current

    LaunchedEffect(Unit) {
        PojavAccountBridge.ensureLoaded()
        AerixClientStore.init(activity.applicationContext)
        SocialBackend.attach(activity)
    }
    LaunchedEffect(wardrobeRevision) {
        savedSkins = withContext(Dispatchers.IO) { PojavAccountBridge.loadSavedSkinPresets(activity) }
    }

    val skinPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val target = PojavAccountBridge.current
            if (target == null) {
                Toast.makeText(activity, "Add or select a Minecraft account first", Toast.LENGTH_LONG).show()
            } else {
                PojavAccountBridge.equipSkinFromUri(activity, target, uri)
            }
        }
    }
    val capePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val target = PojavAccountBridge.current
            if (target == null) {
                Toast.makeText(activity, "Add or select a Minecraft account first", Toast.LENGTH_LONG).show()
            } else {
                PojavAccountBridge.equipCapeFromUri(activity, target, uri) { ok ->
                    if (ok) {
                        AerixClientStore.equippedCapeId = "custom"
                        AerixClientStore.persist()
                        syncTrinkiesCosmetics(activity)
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(wardrobeCanvas).padding(5.dp)
    ) {
        val wideLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || maxWidth >= 760.dp
        Column(
            modifier = Modifier.fillMaxSize()
                .border(1.dp, Color(0xFF37393C), RoundedCornerShape(0.dp))
                .background(wardrobeCanvas)
                .padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            WardrobeTopBar(
                onLobby = { AerixNav.finishTo(LauncherSection.HOME) },
                onImport = {
                    if (account == null) navigate(LauncherSection.ACCOUNTS)
                    else skinPicker.launch(arrayOf("image/png"))
                },
                onUploadSkin = {
                    if (account == null) navigate(LauncherSection.ACCOUNTS)
                    else {
                        SocialsNavigation.requestMyProfile()
                        navigate(LauncherSection.SOCIALS)
                    }
                }
            )

            if (wideLayout) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WardrobePreviewPanel(
                        account = account,
                        pose = pose,
                        onPose = { pose = it },
                        modifier = Modifier.weight(0.40f).fillMaxHeight()
                    )
                    WardrobeCollectionPanel(
                        activity = activity,
                        account = account,
                        category = category,
                        onCategory = { category = it },
                        filter = filter,
                        onFilter = { filter = it },
                        accent = accent,
                        savedSkins = savedSkins,
                        onImportSkin = { skinPicker.launch(arrayOf("image/png")) },
                        onImportCape = { capePicker.launch(arrayOf("image/png")) },
                        onOpenAccounts = { navigate(LauncherSection.ACCOUNTS) },
                        scrollable = true,
                        modifier = Modifier.weight(0.60f).fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WardrobePreviewPanel(
                        account = account,
                        pose = pose,
                        onPose = { pose = it },
                        modifier = Modifier.fillMaxWidth().height(250.dp)
                    )
                    WardrobeCollectionPanel(
                        activity = activity,
                        account = account,
                        category = category,
                        onCategory = { category = it },
                        filter = filter,
                        onFilter = { filter = it },
                        accent = accent,
                        savedSkins = savedSkins,
                        onImportSkin = { skinPicker.launch(arrayOf("image/png")) },
                        onImportCape = { capePicker.launch(arrayOf("image/png")) },
                        onOpenAccounts = { navigate(LauncherSection.ACCOUNTS) },
                        scrollable = false,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 290.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WardrobeTopBar(onLobby: () -> Unit, onImport: () -> Unit, onUploadSkin: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PixelWardrobeButton("‹ Lobby", tone = true, onClick = onLobby)
        Spacer(Modifier.weight(1f))
        PixelWardrobeButton("Import", R.drawable.aerix_ic_download_2_outlined, onClick = onImport)
        PixelWardrobeButton("Upload skin", R.drawable.aerix_ic_upload, tone = true, onClick = onUploadSkin)
    }
}

@Composable
private fun PixelWardrobeButton(
    label: String,
    icon: Int? = null,
    tone: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier,
        shape = RoundedCornerShape(0.dp),
        color = if (tone) wardrobeGreen else wardrobePanelRaised,
        contentColor = if (tone) Color(0xFF101510) else Color(0xFFE8E8E8),
        border = BorderStroke(2.dp, if (tone) Color(0xFF2C7A30) else Color(0xFF424449))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp))
            Text(label, fontFamily = wardrobePixelFont, fontWeight = FontWeight.ExtraBold, fontSize = 8.sp, maxLines = 1)
        }
    }
}

@Composable
private fun WardrobeCompatibilityNote() {
    Text(
        "Trinkies auto-installs only to Fabric-family instances. Vanilla, Forge and NeoForge instances are not modified. Skins and standard capes may work through the active skin service when it and the server allow; remote viewers are not guaranteed to see them. Trinkies-only attachments need compatible clients/server support.",
        color = Color(0xFF9A9B9E), fontSize = 7.sp, lineHeight = 9.sp
    )
}

@Composable
private fun WardrobeCategoryTabs(
    selected: WardrobeCategory,
    accent: Color,
    onSelect: (WardrobeCategory) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        wardrobeCategories.forEach { tab ->
            val active = selected == tab
            Surface(
                onClick = { onSelect(tab) },
                modifier = Modifier.width(42.dp).height(42.dp),
                shape = RoundedCornerShape(0.dp),
                color = if (active) accent else wardrobePanelRaised,
                contentColor = if (active) Color(0xFF101510) else Color(0xFFC7C8CA),
                border = BorderStroke(2.dp, if (active) Color(0xFF2C7A30) else Color(0xFF191A1C))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(13.dp))
                    Text(tab.title, fontFamily = wardrobePixelFont, fontSize = 6.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }
}

@Composable
private fun WardrobeFilterTabs(
    selected: WardrobeFilter,
    accent: Color,
    onSelect: (WardrobeFilter) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        WardrobeFilter.values().forEach { filter ->
            val active = selected == filter
            Surface(
                onClick = { onSelect(filter) },
                modifier = Modifier.weight(1f).height(23.dp),
                shape = RoundedCornerShape(0.dp),
                color = if (active) Color(0xFF233523) else wardrobePanelRaised,
                contentColor = if (active) accent else Color(0xFFD0D0D0),
                border = BorderStroke(1.dp, if (active) Color(0xFF397D3A) else Color(0xFF191A1C))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(filter.title, fontFamily = wardrobePixelFont,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium, fontSize = 7.sp)
                }
            }
        }
    }
}

@Composable
private fun WardrobePreviewPanel(
    account: net.kdt.pojavlaunch.aerix.bridge.AerixAccount?,
    pose: String,
    onPose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
            .background(wardrobePanel)
            .border(2.dp, Color(0xFF3B3D40), RoundedCornerShape(0.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PREVIEW", color = Color(0xFFE3E3E3), fontWeight = FontWeight.ExtraBold,
                fontFamily = wardrobePixelFont, fontSize = 8.sp)
            Text(account?.username?.uppercase() ?: "LOCAL PLAYER", color = wardrobeGreen,
                fontWeight = FontWeight.Bold, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .clip(RoundedCornerShape(0.dp))
                .border(1.dp, Color(0xFF303236), RoundedCornerShape(0.dp))
                .background(wardrobeCanvas),
            contentAlignment = Alignment.Center
        ) {
            AerixSkinPreview3D(
                account = account,
                interactionEnabled = true,
                isVisible = true,
                useClientCape = true,
                showOfficialCape = true,
                animationName = pose,
                applyClientCosmetics = true,
                cameraDistance = 48,
                autoRotate = true,
                modifier = Modifier.fillMaxSize().padding(2.dp)
            )
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.End
            ) {
                WardrobePoseButton("Standing", "NewIdle", pose, onPose)
                WardrobePoseButton("Walking", "Walking", pose, onPose)
                WardrobePoseButton("Waving", "Wave", pose, onPose)
            }
        }
        Text("DRAG TO ROTATE · POSES ARE LOCAL PREVIEWS", color = Color(0xFF9A9B9E),
            fontFamily = FontFamily.Monospace, fontSize = 6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        WardrobeEmoteWheel()
        // The game's own reply, read from the receipt a Fabric-enabled instance leaves behind. It is
        // deliberately absent rather than optimistic when no instance has confirmed the wardrobe yet.
        val appContext = androidx.compose.ui.platform.LocalContext.current
        // Read off the main thread: it touches every supported instance's game directory.
        val confirmation by androidx.compose.runtime.produceState<String?>(null, appContext) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                net.kdt.pojavlaunch.aerix.client.TrinkiesLoadout.describeGameConfirmation(appContext)
            }
        }
        val cosmeticsOn = AerixClientStore.trinkiesCosmeticsEnabled
        Text(
            text = if (cosmeticsOn) "TRINKIES COSMETICS ON · TAP TO TURN OFF"
            else "TRINKIES COSMETICS OFF · TAP TO TURN ON",
            color = if (cosmeticsOn) wardrobeGreen else Color(0xFF9A9B9E),
            fontFamily = FontFamily.Monospace, fontSize = 6.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable {
                AerixClientStore.trinkiesCosmeticsEnabled = !cosmeticsOn
                AerixClientStore.persist()
            }
        )
        WardrobeCloudSyncLine()
        // A delegated local has no smart cast, so the nullable read goes through let.
        confirmation?.let { worn ->
            Text(worn, color = wardrobeGreen, fontFamily = FontFamily.Monospace,
                fontSize = 6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The opt-in wardrobe backup, kept next to the cosmetics switch because it is the same decision
 * written down: ids off the device, or nothing off the device. Push, restore and erase are all taps,
 * never timers, and the note under them is the outcome the server actually reported - there is no
 * optimistic "synced" here, because a green lie about a cloud row is worse than a quiet one.
 */
@Composable
private fun WardrobeCloudSyncLine() {
    val context = LocalContext.current
    val cloudOn = AerixClientStore.trinkiesCloudSyncEnabled
    val socialSignedIn = net.kdt.pojavlaunch.aerix.social.SocialBackend.signedIn
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // A tap counter plus the operation, rather than the operation as the key: tapping "BACK UP NOW"
    // twice in a row is a legitimate second request, and a key that does not change never re-runs.
    var request by remember { mutableStateOf(0) }
    var requestedOp by remember { mutableStateOf(0) }
    LaunchedEffect(request) {
        if (request == 0) return@LaunchedEffect
        busy = true
        note = withContext(Dispatchers.IO) {
            when (requestedOp) {
                1 -> net.kdt.pojavlaunch.aerix.client.TrinkiesCloudSync.push(context)
                2 -> net.kdt.pojavlaunch.aerix.client.TrinkiesCloudSync.pull(context)
                else -> net.kdt.pojavlaunch.aerix.client.TrinkiesCloudSync.erase(context)
            }.message
        }
        busy = false
    }
    fun ask(op: Int) {
        if (busy) return
        requestedOp = op
        request++
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = when {
                !cloudOn -> "WARDROBE CLOUD SYNC OFF · TAP TO TURN ON"
                !socialSignedIn -> "CLOUD SYNC ON · SIGN IN TO AERIX SOCIAL TO USE IT"
                else -> "WARDROBE CLOUD SYNC ON · IDS ONLY"
            },
            color = if (cloudOn) wardrobeGreen else Color(0xFF9A9B9E),
            fontFamily = FontFamily.Monospace, fontSize = 6.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable {
                AerixClientStore.trinkiesCloudSyncEnabled = !cloudOn
                AerixClientStore.persist()
            }
        )
        if (cloudOn && socialSignedIn) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                WardrobeWheelChip(label = if (busy) "WORKING" else "BACK UP NOW", selected = false,
                    onClick = { ask(1) })
                WardrobeWheelChip(label = "RESTORE", selected = false,
                    onClick = { ask(2) })
                WardrobeWheelChip(label = "ERASE COPY", selected = false,
                    onClick = { ask(3) })
            }
        }
        note?.let { message ->
            Text(message, color = Color(0xFF9A9B9E), fontFamily = FontFamily.Monospace, fontSize = 6.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The eight keybound emotes, bound to number keys 1-8 in game. A slot holds any catalog id: the mod
 * resolves it through the packaged item list and plays that item's own `clips.play` length, so binding
 * a hat here is a hat flourish rather than a mistake. Bindings are written straight to every supported
 * instance's loadout, which the mod re-reads on its own clock, so a rebind lands mid-session instead of
 * waiting for the next launch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WardrobeEmoteWheel() {
    val context = LocalContext.current
    var editingSlot by remember { mutableStateOf(-1) }
    val emotes = remember { TrinkiesCatalog.category(context, "emote") }
    val bound = AerixClientStore.trinkiesEmoteWheel
    val cosmeticsOn = AerixClientStore.trinkiesCosmeticsEnabled
    val slot = editingSlot
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("EMOTE WHEEL · KEYS 1-8", color = Color(0xFF9A9B9E),
                fontFamily = FontFamily.Monospace, fontSize = 6.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(if (!cosmeticsOn) "COSMETICS OFF · NOTHING PLAYS"
                else "${bound.count { it != "none" }}/8 BOUND",
                color = if (cosmeticsOn) wardrobeGreen else Color(0xFFB45309),
                fontFamily = FontFamily.Monospace, fontSize = 6.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            bound.forEachIndexed { index, id ->
                // An id the catalog no longer lists (a pack downgrade) still shows which key it holds.
                val name = emotes.firstOrNull { it.id == id }?.name
                    ?: id.takeUnless { it == "none" }?.substringAfter(':')?.replace('_', ' ')
                WardrobeWheelChip(label = "${index + 1} ${name ?: "-"}",
                    selected = slot == index || (cosmeticsOn && name != null),
                    onClick = { editingSlot = if (slot == index) -1 else index })
            }
        }
        if (slot >= 0) {
            Box(Modifier.fillMaxWidth().heightIn(max = 92.dp)
                .clip(RoundedCornerShape(0.dp))
                .background(Color(0xFF141517))
                .border(1.dp, Color(0xFF303236), RoundedCornerShape(0.dp))
                .verticalScroll(rememberScrollState())
                .padding(3.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    WardrobeWheelChip(label = "UNBIND", selected = false, onClick = {
                        bindEmoteWheelSlot(context, slot, "none", "Key ${slot + 1} does nothing now")
                        editingSlot = -1
                    })
                    emotes.forEach { item ->
                        WardrobeWheelChip(label = item.name, selected = bound[slot] == item.id, onClick = {
                            bindEmoteWheelSlot(context, slot, item.id, "${item.name} on key ${slot + 1}")
                            editingSlot = -1
                        })
                    }
                }
            }
        }
    }
}

/** Remembers the binding, then hands it to every instance that can consume it; the mod polls for it. */
private fun bindEmoteWheelSlot(context: android.content.Context, slot: Int, id: String, feedback: String) {
    AerixClientStore.setEmoteWheelSlot(slot, id)
    AerixClientStore.persist()
    PojavAccountBridge.notifyWardrobeChanged()
    Toast.makeText(context, feedback, Toast.LENGTH_SHORT).show()
    // Not just stored: written straight into every instance that can consume it, so a running client
    // picks the binding up on its next poll instead of waiting for the next launch.
    net.kdt.pojavlaunch.aerix.client.TrinkiesLoadout.refreshSupportedInstances(context)
}

@Composable
private fun WardrobeWheelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(0.dp),
        color = if (selected) Color(0xFF234325) else Color(0xFF292B2E),
        contentColor = if (selected) wardrobeGreen else Color(0xFFD1D1D1),
        border = BorderStroke(1.dp, if (selected) Color(0xFF3A853D) else Color(0xFF141517))
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 6.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp))
    }
}

@Composable
private fun WardrobePoseButton(label: String, animation: String, pose: String, onPose: (String) -> Unit) {
    val selected = pose == animation
    Surface(
        onClick = { onPose(animation) },
        shape = RoundedCornerShape(0.dp),
        color = if (selected) Color(0xFF234325) else Color(0xFF292B2E),
        contentColor = if (selected) wardrobeGreen else Color(0xFFD1D1D1),
        border = BorderStroke(1.dp, if (selected) Color(0xFF3A853D) else Color(0xFF141517))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            fontFamily = wardrobePixelFont, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun WardrobeCollectionPanel(
    activity: FragmentActivity,
    account: net.kdt.pojavlaunch.aerix.bridge.AerixAccount?,
    category: WardrobeCategory,
    onCategory: (WardrobeCategory) -> Unit,
    filter: WardrobeFilter,
    onFilter: (WardrobeFilter) -> Unit,
    accent: Color,
    savedSkins: List<AerixSkinPreset>,
    onImportSkin: () -> Unit,
    onImportCape: () -> Unit,
    onOpenAccounts: () -> Unit,
    scrollable: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
            .background(wardrobePanel)
            .border(2.dp, Color(0xFF3B3D40), RoundedCornerShape(0.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WardrobeCategoryTabs(category, accent, onCategory)
        WardrobeFilterTabs(filter, accent, onFilter)
        Column(
            modifier = if (scrollable) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(collectionTitle(category), color = Color(0xFFF0F0F0),
                        fontFamily = wardrobePixelFont, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(categorySubtitle(category, activity.applicationContext), color = Color(0xFF9A9B9E), fontSize = 7.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(categoryCount(category, savedSkins.size, activity.applicationContext), color = accent,
                    fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp, modifier = Modifier.padding(start = 5.dp))
            }

            if (filter != WardrobeFilter.MINE) {
                Text(
                    "${filter.title} online collections are not connected in this build. Your local items are available under Mine.",
                    color = Color(0xFFB0B1B3), fontSize = 8.sp, lineHeight = 10.sp
                )
            } else when (category) {
                WardrobeCategory.LOOKS -> SkinCollection(
                    activity, account, accent, savedSkins, onImportSkin, onOpenAccounts, suitsMode = false
                )
                WardrobeCategory.SHOP -> UnsupportedCollection(
                    title = "Trinkies shop",
                    detail = "An online catalog and purchase flow are not connected in this build.",
                    icon = R.drawable.aerix_ic_cloud_download,
                    accent = accent,
                    cards = listOf("Looks", "Capes", "Headwear")
                )
                WardrobeCategory.SKINS -> SkinCollection(
                    activity, account, accent, savedSkins, onImportSkin, onOpenAccounts, suitsMode = false
                )
                WardrobeCategory.SUITS -> TrinkiesCatalogCollection(activity, "suit", accent)
                WardrobeCategory.CAPES -> CapeCollection(activity, account, accent, onImportCape, backItems = false)
                WardrobeCategory.BACK_ITEMS -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    CapeCollection(activity, account, accent, onImportCape, backItems = true)
                    TrinkiesCatalogCollection(activity, "back", accent)
                }
                WardrobeCategory.HEADWEAR -> HeadwearCollection(activity, accent)
                WardrobeCategory.PETS -> TrinkiesCatalogCollection(activity, "pet", accent)
                WardrobeCategory.EMOTES -> TrinkiesCatalogCollection(activity, "emote", accent)
                WardrobeCategory.AURAS -> TrinkiesCatalogCollection(activity, "aura", accent)
            }
            WardrobeCompatibilityNote()
        }
    }
}

private fun collectionTitle(category: WardrobeCategory): String = when (category) {
    WardrobeCategory.LOOKS -> "My looks"
    WardrobeCategory.SHOP -> "Shop"
    WardrobeCategory.SKINS -> "My skins"
    WardrobeCategory.CAPES -> "My capes"
    WardrobeCategory.HEADWEAR -> "Headwear"
    WardrobeCategory.SUITS -> "Trinkies suits"
    WardrobeCategory.BACK_ITEMS -> "Back items"
    WardrobeCategory.PETS -> "Trinkies pets"
    WardrobeCategory.EMOTES -> "Trinkies emotes"
    WardrobeCategory.AURAS -> "Trinkies auras"
}

private fun categoryCount(category: WardrobeCategory, skinCount: Int, context: android.content.Context): String = when (category) {
    WardrobeCategory.LOOKS, WardrobeCategory.SKINS -> skinCount.toString()
    WardrobeCategory.HEADWEAR -> TrinkiesCatalog.category(context, "hat").size.toString()
    WardrobeCategory.SUITS -> TrinkiesCatalog.category(context, "suit").size.toString()
    WardrobeCategory.PETS -> TrinkiesCatalog.category(context, "pet").size.toString()
    WardrobeCategory.EMOTES -> TrinkiesCatalog.category(context, "emote").size.toString()
    WardrobeCategory.AURAS -> TrinkiesCatalog.category(context, "aura").size.toString()
    WardrobeCategory.CAPES -> "15+"
    WardrobeCategory.BACK_ITEMS -> "${TrinkiesCatalog.category(context, "back").size}+"
    WardrobeCategory.SHOP -> "—"
}

private fun categorySubtitle(category: WardrobeCategory, context: android.content.Context): String = when (category) {
    WardrobeCategory.LOOKS -> "Saved player styles"
    WardrobeCategory.SHOP -> "Online catalog · unavailable in this build"
    WardrobeCategory.SKINS -> "Minecraft skins and saved presets"
    WardrobeCategory.CAPES -> "Animated client capes and imported PNGs"
    WardrobeCategory.HEADWEAR -> "${TrinkiesCatalog.category(context, "hat").size} hats · baked into your served skin"
    WardrobeCategory.SUITS -> "${TrinkiesCatalog.category(context, "suit").size} suits · baked into your served skin"
    WardrobeCategory.BACK_ITEMS -> "Animated capes work · ${TrinkiesCatalog.category(context, "back").size} back items · jacket bake"
    WardrobeCategory.PETS -> "${TrinkiesCatalog.category(context, "pet").size} pets · shoulder bake only, no follower yet"
    WardrobeCategory.EMOTES -> "${TrinkiesCatalog.category(context, "emote").size} emote looks · cuff/sleeve bake, no wheel yet"
    WardrobeCategory.AURAS -> "${TrinkiesCatalog.category(context, "aura").size} auras · emissive trim bake, no particles yet"
}

@Composable
private fun SkinCollection(
    activity: FragmentActivity,
    account: net.kdt.pojavlaunch.aerix.bridge.AerixAccount?,
    accent: Color,
    skins: List<AerixSkinPreset>,
    onImport: () -> Unit,
    onOpenAccounts: () -> Unit,
    suitsMode: Boolean
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        WardrobeAction("IMPORT PNG", R.drawable.aerix_ic_upload, accent, onImport, enabled = account != null)
        WardrobeAction("ACCOUNTS", R.drawable.aerix_ic_person_outlined, accent, onOpenAccounts)
    }
    if (account == null) {
        EmptyWardrobeState(
            "Select a Minecraft account first",
            "Skins are stored per account and handed to Minecraft by the launcher's skin service.",
            R.drawable.aerix_ic_person_outlined,
            accent,
            onOpenAccounts
        )
        return
    }

    if (suitsMode) {
        Text("A suit/body outfit is a full Minecraft skin. Import a skin PNG or choose a saved preset below.",
            color = AerixSurface.textSecondary, fontSize = 10.sp)
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ARM MODEL", color = AerixSurface.textSecondary, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val slim = PojavAccountBridge.isSlimModel(account)
                ModelChoice("CLASSIC", !slim, accent) { PojavAccountBridge.setSlimModel(activity, account, false) }
                ModelChoice("SLIM", slim, accent) { PojavAccountBridge.setSlimModel(activity, account, true) }
            }
        }
    }

    val currentPreset = PojavAccountBridge.activePresetId(activity, account)
    if (skins.isEmpty()) {
        Text("Your saved skin collection is empty. Import a 64×64 or legacy skin PNG to add one.",
            color = AerixSurface.textSecondary, fontSize = 10.sp)
    } else {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 420.dp) 2 else 3
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                skins.chunked(columns).forEach { rowSkins ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowSkins.forEach { skin ->
                            SkinPresetCard(
                                skin = skin,
                                selected = currentPreset == skin.id,
                                accent = accent,
                                modifier = Modifier.weight(1f)
                            ) {
                                PojavAccountBridge.equipSkinPreset(activity, account, skin)
                            }
                        }
                        repeat(columns - rowSkins.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    Text("Skin, body outfit and arm model are local to this account. Visibility to other players depends on the account service and the server.",
        color = AerixSurface.textSecondary, fontSize = 9.sp, lineHeight = 12.sp)
}

@Composable
private fun ModelChoice(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(0.dp),
        color = if (selected) accent.copy(alpha = 0.2f) else AerixSurface.panelRaised,
        border = BorderStroke(AerixSpacing.hairline, if (selected) accent else AerixSurface.borderSoft)
    ) {
        Text(label, color = if (selected) AerixSurface.textPrimary else AerixSurface.textSecondary,
            fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
    }
}

@Composable
private fun SkinPresetCard(
    skin: AerixSkinPreset,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(0.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else AerixSurface.panelRaised.copy(alpha = 0.72f),
        border = BorderStroke(AerixSpacing.hairline, if (selected) accent else AerixSurface.borderSoft)
    ) {
        Column(Modifier.fillMaxWidth().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val preview = skin.preview
            if (preview != null) {
                Image(preview, contentDescription = skin.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(58.dp))
            } else {
                Icon(painterResource(R.drawable.aerix_ic_person_outlined), contentDescription = null,
                    tint = if (selected) accent else AerixSurface.textSecondary, modifier = Modifier.size(42.dp))
            }
            Text(skin.name, color = if (selected) AerixSurface.textPrimary else AerixSurface.textSecondary,
                fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CapeCollection(
    activity: FragmentActivity,
    account: net.kdt.pojavlaunch.aerix.bridge.AerixAccount?,
    accent: Color,
    onImport: () -> Unit,
    backItems: Boolean
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        WardrobeAction("IMPORT CAPE PNG", R.drawable.aerix_ic_upload, accent, onImport, enabled = account != null)
        WardrobeAction("SYNC CLIENTS", R.drawable.aerix_ic_refresh, accent, { syncTrinkiesCosmetics(activity) })
    }
    val customCapeAvailable = account?.let { PojavAccountBridge.customCapeFile(it.source).isFile } == true
    if (customCapeAvailable) {
        val customSelected = AerixClientStore.equippedCapeId == "custom"
        Surface(
            modifier = Modifier.fillMaxWidth().clickable {
                AerixClientStore.equippedCapeId = "custom"
                AerixClientStore.persist()
                syncTrinkiesCosmetics(activity)
            },
            shape = RoundedCornerShape(0.dp),
            color = if (customSelected) accent.copy(alpha = 0.16f) else AerixSurface.panelRaised.copy(alpha = 0.7f),
            border = BorderStroke(AerixSpacing.hairline, if (customSelected) accent else AerixSurface.borderSoft)
        ) {
            Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(painterResource(R.drawable.aerix_ic_style_outlined), contentDescription = null,
                    tint = accent, modifier = Modifier.size(16.dp))
                Text("Imported cape", color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                    modifier = Modifier.weight(1f))
                Text(if (customSelected) "EQUIPPED" else "TAP TO EQUIP", color = accent,
                    fontFamily = FontFamily.Monospace, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Text(
        if (backItems) "The animated cape is the supported back item in this build. More attachments appear only when a compatible server/client pack is installed."
        else "Choose a built-in animated cape or import a standard cape PNG. Animation is local; seeing it on other players needs compatible server support.",
        color = AerixSurface.textSecondary, fontSize = 9.sp, lineHeight = 12.sp
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 390.dp) 2 else 3
        val selectedId = AerixClientStore.equippedCapeId
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AerixClientCosmetics.capes.chunked(columns).forEach { rowCapes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowCapes.forEach { cape ->
                        CapeCard(
                            spec = cape,
                            selected = selectedId == cape.id,
                            unlocked = AerixClientCosmetics.canEquipCape(cape),
                            accent = accent,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (!AerixClientCosmetics.canEquipCape(cape)) {
                                Toast.makeText(activity, "This cape is reserved for its Aerix account", Toast.LENGTH_SHORT).show()
                            } else {
                                AerixClientStore.equippedCapeId = cape.id
                                AerixClientStore.persist()
                                syncTrinkiesCosmetics(activity)
                            }
                        }
                    }
                    repeat(columns - rowCapes.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CapeCard(
    spec: net.kdt.pojavlaunch.aerix.client.ClientCapeSpec,
    selected: Boolean,
    unlocked: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val preview: ImageBitmap = remember(spec.id) {
        AerixClientCosmetics.buildCapeAndElytraBitmap(spec).asImageBitmap()
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(0.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else AerixSurface.panelRaised.copy(alpha = 0.68f),
        border = BorderStroke(AerixSpacing.hairline, when {
            selected -> accent
            !unlocked -> AerixSurface.borderSoft.copy(alpha = 0.5f)
            else -> AerixSurface.borderSoft
        })
    ) {
        Column(Modifier.fillMaxWidth().padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Image(preview, contentDescription = spec.title, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(0.dp)))
            Text(spec.title, color = if (unlocked) AerixSurface.textPrimary else AerixSurface.textSecondary,
                fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(when {
                !unlocked -> spec.badgeText ?: "LOCKED"
                selected -> "EQUIPPED"
                else -> spec.animStyle.name.replace('_', ' ')
            }, color = if (selected) accent else AerixSurface.textSecondary,
                fontSize = 7.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** What the "nothing equipped" card is called, per wardrobe category. */
private val NONE_LABELS = mapOf(
    "hat" to "No headwear", "suit" to "Default outfit", "back" to "Nothing on your back",
    "pet" to "No companion", "emote" to "No emote look", "aura" to "No aura trim",
)

@Composable
private fun HeadwearCollection(activity: FragmentActivity, accent: Color) {
    val context = LocalContext.current
    val hats = remember { TrinkiesCatalog.category(context, "hat") }
    Text(
        "${hats.size} hats. A hat is baked into the outer head layer of the skin that gets served to the game, so it needs a " +
            "custom skin to paint on; on a plain account skin there is nothing to overlay and the choice stays in the launcher. " +
            "In-game appearance has not been visually verified on device yet.",
        color = AerixSurface.textSecondary, fontSize = 9.sp, lineHeight = 12.sp
    )
    TrinkiesCatalogGrid(activity, hats, accent, "hat", includeNone = true)
}

@Composable
private fun TrinkiesCatalogCollection(activity: FragmentActivity, category: String, accent: Color) {
    val context = LocalContext.current
    val items = remember(category) { TrinkiesCatalog.category(context, category) }
    Text(
        "${items.size} ${category.replaceFirstChar { it.uppercase() }} items. Equipping bakes the item's garment patch into the skin " +
            "the launcher serves to the game, so it shows up as worn pixels on you and on anyone else who can see your skin. " +
            "Free-floating 3D extras for this category (followers, prop rigs, particle trails) are not in game yet.",
        color = AerixSurface.textSecondary, fontSize = 9.sp, lineHeight = 12.sp
    )
    TrinkiesCatalogGrid(activity, items, accent, category)
}

@Composable
private fun TrinkiesCatalogGrid(
    activity: FragmentActivity,
    items: List<TrinkiesCatalogItem>,
    accent: Color,
    category: String,
    includeNone: Boolean = false
) {
    val entries: List<TrinkiesCatalogItem?> = remember(items, includeNone) {
        if (includeNone) listOf(null) + items else items.map { it }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 390.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entries.chunked(columns).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowItems.forEach { item ->
                        CatalogItemCard(
                            activity = activity,
                            item = item,
                            category = category,
                            selected = (item?.id ?: "none") == AerixClientStore.equippedIdFor(category),
                            accent = accent,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (item == null || item.locallyRendered) {
                                AerixClientStore.setEquippedId(category, item?.id ?: "none")
                                AerixClientStore.persist()
                                PojavAccountBridge.notifyWardrobeChanged()
                                val selection = item?.name ?: NONE_LABELS[category].orEmpty()
                                Toast.makeText(activity, "$selection is worn the next time the skin is served",
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CatalogItemCard(
    activity: FragmentActivity,
    item: TrinkiesCatalogItem?,
    category: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, item?.id, context) {
        value = withContext(Dispatchers.IO) {
            item?.let {
                runCatching {
                    context.assets.open(it.previewAsset).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    val imageBitmap = bitmap
    val equipped = item == null || item.locallyRendered
    val rarityStyle = item?.let { trinkiesRarityStyle(it.rarity) }
    Surface(
        modifier = modifier.clickable(enabled = equipped, onClick = onClick),
        shape = RoundedCornerShape(0.dp),
        color = rarityStyle?.background ?: AerixSurface.panelRaised,
        border = BorderStroke(AerixSpacing.hairline, if (selected) Color.White else rarityStyle?.accent ?: AerixSurface.borderSoft)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (imageBitmap != null) {
                Image(imageBitmap, contentDescription = item?.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(0.dp)))
            } else {
                Box(Modifier.size(68.dp).background(accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.aerix_ic_styler_outlined), contentDescription = null,
                        tint = if (selected) accent else AerixSurface.textSecondary, modifier = Modifier.size(24.dp))
                }
            }
            Text(item?.name ?: NONE_LABELS[category].orEmpty(), color = AerixSurface.textPrimary, fontSize = 8.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item?.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(description, color = AerixSurface.textSecondary, fontSize = 6.sp, lineHeight = 7.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                when {
                    selected -> "EQUIPPED · LOCAL"
                    item == null -> "RESTORE ORIGINAL SKIN"
                    item.locallyRendered -> "${item.rarity.uppercase()} · ${category.uppercase()}"
                    else -> "3D PREVIEW · ${item.rarity.uppercase()}"
                },
                color = if (selected) accent else rarityStyle?.accent ?: AerixSurface.textSecondary,
                fontSize = 6.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmoteCollection(accent: Color, onPose: (String) -> Unit) {
    Text("These are preview animations only. They do not send emote packets to a server or other players.",
        color = AerixSurface.textSecondary, fontSize = 9.sp, lineHeight = 12.sp)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        wardrobePoses.forEach { item ->
            Surface(
                modifier = Modifier.clickable { onPose(item.animation) },
                shape = RoundedCornerShape(0.dp),
                color = AerixSurface.panelRaised,
                border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
            ) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(painterResource(R.drawable.aerix_ic_touch_app_outlined), contentDescription = null,
                        tint = accent, modifier = Modifier.size(13.dp))
                    Text(item.label, color = AerixSurface.textPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    UnsupportedCollection(
        title = "Multiplayer emotes",
        detail = "A server-side Trinkies bridge is required to sync expressions or gestures to other players.",
        icon = R.drawable.aerix_ic_info_outlined,
        accent = accent,
        cards = listOf("Wave", "Cheer", "Victory")
    )
}

@Composable
private fun UnsupportedCollection(title: String, detail: String, icon: Int, accent: Color, cards: List<String>) {
    Text(title, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    Text(detail, color = AerixSurface.textSecondary, fontSize = 9.sp, lineHeight = 12.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cards.forEach { card ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(0.dp),
                color = AerixSurface.panelRaised.copy(alpha = 0.5f),
                border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft.copy(alpha = 0.7f))
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(painterResource(icon), contentDescription = null, tint = accent.copy(alpha = 0.6f), modifier = Modifier.size(19.dp))
                    Text(card, color = AerixSurface.textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("NOT AVAILABLE", color = AerixSurface.textSecondary, fontSize = 6.sp,
                        fontFamily = FontFamily.Monospace, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun EmptyWardrobeState(title: String, detail: String, icon: Int, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(0.dp),
        color = AerixSurface.panelRaised.copy(alpha = 0.58f),
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
            Column {
                Text(title, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text(detail, color = AerixSurface.textSecondary, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun WardrobeAction(
    label: String,
    icon: Int,
    accent: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(0.dp),
        color = if (enabled) accent.copy(alpha = 0.16f) else AerixSurface.panelRaised.copy(alpha = 0.45f),
        border = BorderStroke(AerixSpacing.hairline, if (enabled) accent.copy(alpha = 0.75f) else AerixSurface.borderSoft)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(painterResource(icon), contentDescription = null,
                tint = if (enabled) accent else AerixSurface.textSecondary, modifier = Modifier.size(13.dp))
            Text(label, color = if (enabled) AerixSurface.textPrimary else AerixSurface.textSecondary,
                fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

private val capeSyncRunning = AtomicBoolean(false)

private fun syncTrinkiesCosmetics(activity: FragmentActivity) {
    if (!capeSyncRunning.compareAndSet(false, true)) return
    PojavApplication.sExecutorService.execute {
        try {
            synchronized(AerixClientCosmetics) {
                AerixClientCosmetics.syncCapeAssetsToAllClientInstances(activity.applicationContext)
            }
        } finally {
            capeSyncRunning.set(false)
        }
    }
}
