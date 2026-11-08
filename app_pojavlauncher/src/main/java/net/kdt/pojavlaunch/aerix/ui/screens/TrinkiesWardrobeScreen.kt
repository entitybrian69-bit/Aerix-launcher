package net.kdt.pojavlaunch.aerix.ui.screens

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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import java.util.concurrent.atomic.AtomicBoolean

private enum class WardrobeCategory(val title: String, val icon: Int) {
    SKINS("Skins", R.drawable.aerix_ic_person_outlined),
    CAPES("Capes", R.drawable.aerix_ic_style_outlined),
    HEADWEAR("Headwear", R.drawable.aerix_ic_styler_outlined),
    SUITS("Suits / Body", R.drawable.aerix_ic_format_paint_outlined),
    BACK_ITEMS("Back Items", R.drawable.aerix_ic_favorite_outlined),
    PETS("Pets", R.drawable.aerix_ic_people),
    EMOTES("Emotes", R.drawable.aerix_ic_touch_app_outlined)
}

private data class WardrobePose(val label: String, val animation: String)

private val wardrobePoses = listOf(
    WardrobePose("Idle", "NewIdle"),
    WardrobePose("Wave", "Wave"),
    WardrobePose("Walk", "Walking"),
    WardrobePose("Run", "Running"),
    WardrobePose("Fly", "Flying"),
    WardrobePose("Crouch", "Crouch")
)

private data class TrinkiesHat(
    val id: String,
    val name: String,
    val detail: String,
    val color: Color
)

private val trinkiesHats = listOf(
    TrinkiesHat("none", "No headwear", "Use the original skin layer", Color(0xFF75808A)),
    TrinkiesHat("crimson_crown", "Crimson Crown", "Gold-trimmed pixel crown", Color(0xFFDC1E3A)),
    TrinkiesHat("void_horns", "Void Horns", "Violet outer-layer horns", Color(0xFF9B5DE5)),
    TrinkiesHat("frost_circlet", "Frost Circlet", "Ice-blue circlet", Color(0xFF45C7E8)),
    TrinkiesHat("sakura_bow", "Sakura Bow", "Rose-pink pixel bow", Color(0xFFFF79B0))
)

/** Trinkies cosmetics hub: existing account skins, animated client capes, headwear and preview poses. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrinkiesWardrobeScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val account = PojavAccountBridge.current
    var category by remember { mutableStateOf(WardrobeCategory.CAPES) }
    var pose by remember { mutableStateOf("NewIdle") }
    var savedSkins by remember { mutableStateOf<List<AerixSkinPreset>>(emptyList()) }
    val wardrobeRevision = PojavAccountBridge.wardrobeRevision

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

    AerixPage(
        title = "Trinkies · Cosmetics & Wardrobe",
        subtitle = "Build a look, preview it in 3D, and choose what your client can display"
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxWidth < 680.dp || maxHeight < 560.dp
            if (compact) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                ) {
                    TrinkiesCompatibilityBanner(accent)
                    WardrobeCategoryTabs(category, accent) { category = it }
                    WardrobePreviewPanel(
                        account = account,
                        category = category,
                        pose = pose,
                        onPose = { pose = it },
                        modifier = Modifier.fillMaxWidth().height(270.dp)
                    )
                    WardrobeCollectionPanel(
                        activity = activity,
                        account = account,
                        category = category,
                        accent = accent,
                        savedSkins = savedSkins,
                        onImportSkin = { skinPicker.launch(arrayOf("image/png")) },
                        onImportCape = { capePicker.launch(arrayOf("image/png")) },
                        onOpenAccounts = { navigate(LauncherSection.ACCOUNTS) },
                        onPose = { pose = it },
                        scrollable = false,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp)
                    )
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                    TrinkiesCompatibilityBanner(accent)
                    WardrobeCategoryTabs(category, accent) { category = it }
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                        WardrobePreviewPanel(
                            account = account,
                            category = category,
                            pose = pose,
                            onPose = { pose = it },
                            modifier = Modifier.weight(0.92f).fillMaxHeight()
                        )
                        WardrobeCollectionPanel(
                            activity = activity,
                            account = account,
                            category = category,
                            accent = accent,
                            savedSkins = savedSkins,
                            onImportSkin = { skinPicker.launch(arrayOf("image/png")) },
                            onImportCape = { capePicker.launch(arrayOf("image/png")) },
                            onOpenAccounts = { navigate(LauncherSection.ACCOUNTS) },
                            onPose = { pose = it },
                            scrollable = true,
                            modifier = Modifier.weight(1.38f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrinkiesCompatibilityBanner(accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = AerixSurface.panelRaised.copy(alpha = 0.82f),
        border = BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.44f))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(painterResource(R.drawable.aerix_ic_check), contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                Text("TRINKIES AUTO-INSTALL · FABRIC / QUILT / LEGACY FABRIC",
                    color = AerixSurface.textPrimary, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Vanilla, Forge and NeoForge instances are not modified. Account skins and standard capes may work through the active skin service when it and the server allow; remote viewers are not guaranteed to see them. Hats, animated capes, pets and emotes need Trinkies-capable clients, and multiplayer visibility also needs server support.",
                color = AerixSurface.textSecondary, fontSize = 10.sp, lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun WardrobeCategoryTabs(
    selected: WardrobeCategory,
    accent: Color,
    onSelect: (WardrobeCategory) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WardrobeCategory.values().forEach { tab ->
            val active = selected == tab
            Surface(
                modifier = Modifier.clickable { onSelect(tab) },
                shape = RoundedCornerShape(5.dp),
                color = if (active) accent.copy(alpha = 0.20f) else AerixSurface.panel.copy(alpha = 0.8f),
                border = BorderStroke(AerixSpacing.hairline, if (active) accent.copy(alpha = 0.8f) else AerixSurface.borderSoft)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(painterResource(tab.icon), contentDescription = null,
                        tint = if (active) accent else AerixSurface.textSecondary, modifier = Modifier.size(14.dp))
                    Text(tab.title, color = if (active) AerixSurface.textPrimary else AerixSurface.textSecondary,
                        fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WardrobePreviewPanel(
    account: net.kdt.pojavlaunch.aerix.bridge.AerixAccount?,
    category: WardrobeCategory,
    pose: String,
    onPose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = AerixThemeManager.currentAccent()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AerixSurface.panel.copy(alpha = 0.78f))
            .border(AerixSpacing.hairline, AerixSurface.borderSoft, RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("AVATAR PREVIEW", color = AerixSurface.textPrimary, fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Text("360°", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(4.dp))
                .background(AerixSurface.canvas.copy(alpha = 0.54f)),
            contentAlignment = Alignment.Center
        ) {
            AerixSkinPreview3D(
                account = account,
                interactionEnabled = true,
                isVisible = true,
                useClientCape = true,
                showOfficialCape = true,
                animationName = pose,
                applyClientHat = true,
                modifier = Modifier.fillMaxSize().padding(6.dp)
            )
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                shape = RoundedCornerShape(3.dp),
                color = AerixSurface.canvas.copy(alpha = 0.88f)
            ) {
                Text(account?.username ?: "STEVE · LOCAL PREVIEW", color = AerixSurface.textSecondary,
                    fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        Text("POSE / EMOTE PREVIEW", color = AerixSurface.textSecondary, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 8.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            wardrobePoses.forEach { item ->
                val active = pose == item.animation
                Surface(
                    modifier = Modifier.clickable { onPose(item.animation) },
                    shape = RoundedCornerShape(4.dp),
                    color = if (active) accent.copy(alpha = 0.18f) else AerixSurface.panelRaised,
                    border = BorderStroke(AerixSpacing.hairline, if (active) accent else AerixSurface.borderSoft)
                ) {
                    Text(item.label, color = if (active) AerixSurface.textPrimary else AerixSurface.textSecondary,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
        }
        Text("Drag the character to rotate. Poses are launcher previews; a server must explicitly support synced emotes.",
            color = AerixSurface.textSecondary, fontSize = 9.sp, lineHeight = 12.sp)
    }
}

@Composable
private fun WardrobeCollectionPanel(
    activity: FragmentActivity,
    account: net.kdt.pojavlaunch.aerix.bridge.AerixAccount?,
    category: WardrobeCategory,
    accent: Color,
    savedSkins: List<AerixSkinPreset>,
    onImportSkin: () -> Unit,
    onImportCape: () -> Unit,
    onOpenAccounts: () -> Unit,
    onPose: (String) -> Unit,
    scrollable: Boolean,
    modifier: Modifier = Modifier
) {
    val collectionModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AerixSurface.panel.copy(alpha = 0.84f))
            .border(AerixSpacing.hairline, AerixSurface.borderSoft, RoundedCornerShape(6.dp))
            .then(collectionModifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("COLLECTION / ${category.title.uppercase()}", color = AerixSurface.textPrimary,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                Text(categorySubtitle(category), color = AerixSurface.textSecondary, fontSize = 9.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                when (category) {
                    WardrobeCategory.CAPES, WardrobeCategory.BACK_ITEMS -> "15+"
                    WardrobeCategory.HEADWEAR -> "4"
                    WardrobeCategory.SKINS, WardrobeCategory.SUITS -> savedSkins.size.toString()
                    WardrobeCategory.EMOTES -> wardrobePoses.size.toString()
                    WardrobeCategory.PETS -> "0"
                },
                color = accent, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp)
            )
        }
        when (category) {
            WardrobeCategory.SKINS -> SkinCollection(
                activity, account, accent, savedSkins, onImportSkin, onOpenAccounts, suitsMode = false
            )
            WardrobeCategory.SUITS -> SkinCollection(
                activity, account, accent, savedSkins, onImportSkin, onOpenAccounts, suitsMode = true
            )
            WardrobeCategory.CAPES -> CapeCollection(activity, account, accent, onImportCape, backItems = false)
            WardrobeCategory.BACK_ITEMS -> CapeCollection(activity, account, accent, onImportCape, backItems = true)
            WardrobeCategory.HEADWEAR -> HeadwearCollection(activity, accent)
            WardrobeCategory.PETS -> UnsupportedCollection(
                title = "Pet companions",
                detail = "Pet rendering and remote presence need a compatible Trinkies client and server pack. No pet pack is bundled in this APK yet.",
                icon = R.drawable.aerix_ic_people,
                accent = accent,
                cards = listOf("Companion", "Shoulder pet", "Trail effect")
            )
            WardrobeCategory.EMOTES -> EmoteCollection(accent, onPose)
        }
    }
}

private fun categorySubtitle(category: WardrobeCategory): String = when (category) {
    WardrobeCategory.SKINS -> "Minecraft skin files and saved presets"
    WardrobeCategory.CAPES -> "Animated Trinkies capes and imported PNG capes"
    WardrobeCategory.HEADWEAR -> "Pixel-art overlays on the outer skin layer"
    WardrobeCategory.SUITS -> "Full-body appearance uses the Minecraft skin texture"
    WardrobeCategory.BACK_ITEMS -> "Capes work now; other back attachments need server support"
    WardrobeCategory.PETS -> "Collection slot · server/client support required"
    WardrobeCategory.EMOTES -> "Preview poses · synced emotes require server support"
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
                ModelChoice("WIDE", !slim, accent) { PojavAccountBridge.setSlimModel(activity, account, false) }
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
        shape = RoundedCornerShape(4.dp),
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
        shape = RoundedCornerShape(4.dp),
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
            shape = RoundedCornerShape(4.dp),
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
        shape = RoundedCornerShape(4.dp),
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
                modifier = Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(2.dp)))
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

@Composable
private fun HeadwearCollection(activity: FragmentActivity, accent: Color) {
    Text("Headwear is drawn onto the skin's outer head layer. This is a local cosmetic; remote players need a compatible texture/server path.",
        color = AerixSurface.textSecondary, fontSize = 9.sp, lineHeight = 12.sp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 390.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            trinkiesHats.chunked(columns).forEach { rowHats ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowHats.forEach { hat ->
                        val selected = AerixClientStore.equippedHatId == hat.id
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                AerixClientStore.equippedHatId = hat.id
                                AerixClientStore.persist()
                                PojavAccountBridge.notifyWardrobeChanged()
                                Toast.makeText(activity, "${hat.name} applied to the preview", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(4.dp),
                            color = if (selected) accent.copy(alpha = 0.18f) else AerixSurface.panelRaised.copy(alpha = 0.72f),
                            border = BorderStroke(AerixSpacing.hairline, if (selected) accent else AerixSurface.borderSoft)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(34.dp).clip(RoundedCornerShape(4.dp)).background(hat.color.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.aerix_ic_styler_outlined), contentDescription = null,
                                        tint = hat.color, modifier = Modifier.size(22.dp))
                                }
                                Text(hat.name, color = AerixSurface.textPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (selected) "EQUIPPED" else hat.detail, color = if (selected) accent else AerixSurface.textSecondary,
                                    fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    repeat(columns - rowHats.size) { Spacer(Modifier.weight(1f)) }
                }
            }
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
                shape = RoundedCornerShape(4.dp),
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
                shape = RoundedCornerShape(4.dp),
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
        shape = RoundedCornerShape(4.dp),
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
        shape = RoundedCornerShape(4.dp),
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
