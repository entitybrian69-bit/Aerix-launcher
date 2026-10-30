package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.client.AerixClientCosmetics
import net.kdt.pojavlaunch.aerix.client.AerixClientInstaller
import net.kdt.pojavlaunch.aerix.client.AerixClientPackGenerator
import net.kdt.pojavlaunch.aerix.client.AerixClientStore
import net.kdt.pojavlaunch.aerix.client.ClientChromaTheme
import net.kdt.pojavlaunch.aerix.client.ClientLoadoutPreset
import net.kdt.pojavlaunch.aerix.client.CrosshairShape
import net.kdt.pojavlaunch.aerix.client.EnderThrowSample
import net.kdt.pojavlaunch.aerix.client.HitSoundPreset
import net.kdt.pojavlaunch.aerix.client.Interactive3DCosmeticStage
import net.kdt.pojavlaunch.aerix.client.KeystrokesStyle
import net.kdt.pojavlaunch.aerix.client.LiquidCrystalCard
import net.kdt.pojavlaunch.aerix.client.LiquidCrystalModuleToggleCard
import net.kdt.pojavlaunch.aerix.client.LiquidCrystalPalette
import net.kdt.pojavlaunch.aerix.client.LiquidCrystalPill
import net.kdt.pojavlaunch.aerix.client.LiquidCrystalPrimaryButton
import net.kdt.pojavlaunch.aerix.client.SkyboxTheme
import net.kdt.pojavlaunch.aerix.client.TouchCurveMode
import net.kdt.pojavlaunch.aerix.client.TriangulationResult
import net.kdt.pojavlaunch.aerix.client.VisualFilterPreset
import net.kdt.pojavlaunch.aerix.client.drawAerixCrosshair
import net.kdt.pojavlaunch.instances.Instances

private enum class ClientTab(val label: String) {
    HUB("✦ 1.16.5 Hub"),
    COSMETICS("3D Cosmetics"),
    PACK_STUDIO("Pack & Skybox"),
    MODULES("Modules & HUD"),
    COOP_TOOLS("Co-Op & Tools")
}

@Composable
fun AerixClientScreen(activity: FragmentActivity) {
    LaunchedEffect(Unit) {
        AerixClientStore.init(activity)
    }

    var activeTab by remember { mutableStateOf(ClientTab.HUB) }
    var installedTick by remember { mutableIntStateOf(0) }
    val installedInstance = remember(installedTick, AerixClientInstaller.isInstalling) {
        AerixClientInstaller.findInstalledClientInstance()
    }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Liquid Crystal Header Bar + Rank Progress + Tab Switcher
        LiquidCrystalCard(
            cornerRadius = 22.dp,
            strong = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.40f), LiquidCrystalPalette.PrismCyan.copy(alpha = 0.30f))
                                )
                            )
                            .border(1.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✦", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "AERIX CLIENT",
                                color = LiquidCrystalPalette.IceWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            LiquidCrystalPill(
                                label = "1.16.5 TESTING EDITION",
                                selected = true,
                                accent = LiquidCrystalPalette.PrismMint
                            )
                        }
                        Text(
                            text = "${AerixClientStore.clientRankLabel()} · ${AerixClientStore.clientXp} XP · Liquid Crystal Clear Engine",
                            color = LiquidCrystalPalette.IceSub,
                            fontSize = 11.sp
                        )
                    }
                }

                // Quick Play / Install CTA in top-right header
                LiquidCrystalPrimaryButton(
                    text = if (installedInstance != null) "▶ LAUNCH 1.16.5" else "⬇ 1-CLICK INSTALL",
                    subtitle = if (installedInstance != null) "Client Ready" else "Fabric + FPS Mods",
                    enabled = !AerixClientInstaller.isInstalling,
                    onClick = {
                        if (installedInstance != null) {
                            scope.launch {
                                AerixClientInstaller.rebuildPackOnlyIfInstalled(activity)
                                Instances.setSelectedInstance(installedInstance)
                                PojavInstanceBridge.reload()
                                PojavLaunchBridge.play(activity)
                            }
                        } else {
                            scope.launch {
                                val res = AerixClientInstaller.installOrSyncClient1165(activity)
                                installedTick++
                                PojavInstanceBridge.reload()
                                res.onSuccess {
                                    Toast.makeText(activity, "Aerix Client 1.16.5 installed & selected!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            // Sub-Navigation Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ClientTab.values().forEach { tab ->
                    LiquidCrystalPill(
                        label = tab.label,
                        selected = activeTab == tab,
                        onClick = { activeTab = tab }
                    )
                }
            }
        }

        // Main Tab Body
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                ClientTab.HUB -> ClientHubTab(
                    activity = activity,
                    installed = installedInstance != null,
                    onInstalledChanged = {
                        installedTick++
                        PojavInstanceBridge.reload()
                    },
                    onOpenCosmetics = { activeTab = ClientTab.COSMETICS },
                    onOpenPack = { activeTab = ClientTab.PACK_STUDIO }
                )
                ClientTab.COSMETICS -> ClientCosmeticsLockerTab(activity)
                ClientTab.PACK_STUDIO -> ClientPackStudioTab(activity)
                ClientTab.MODULES -> ClientModulesStudioTab(activity)
                ClientTab.COOP_TOOLS -> ClientCoopToolsTab(activity)
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * TAB 1: 1.16.5 CLIENT HUB & AUTOMATED 1-CLICK INSTALLER
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun ClientHubTab(
    activity: FragmentActivity,
    installed: Boolean,
    onInstalledChanged: () -> Unit,
    onOpenCosmetics: () -> Unit,
    onOpenPack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Column: Installer & Mod Stack Control
        Column(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidCrystalCard(strong = true) {
                Text(
                    "Minecraft 1.16.5 Competitive Client",
                    color = LiquidCrystalPalette.IceWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Exclusively engineered for Minecraft 1.16.5 testing: automated Fabric Loader setup, Modrinth 1.16.5 FPS optimization jars, isolated workspace, 3D Physics Capes & Elytras, Custom Startup & Main Menu screens, and the in-game Liquid Crystal HUD.",
                    color = LiquidCrystalPalette.IceSub,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))

                // Progress bar + live status
                LinearProgressIndicator(
                    progress = {
                        if (AerixClientInstaller.isInstalling) AerixClientInstaller.progressFraction
                        else if (installed) 1f else 0.06f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                    color = LiquidCrystalPalette.PrismCyan,
                    trackColor = Color.White.copy(alpha = 0.14f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = AerixClientInstaller.statusHeadline,
                    color = LiquidCrystalPalette.IceWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AerixClientInstaller.statusDetail,
                    color = LiquidCrystalPalette.IceSub,
                    fontSize = 11.sp
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiquidCrystalPrimaryButton(
                        text = if (installed) "↻ SYNC / REPAIR 1.16.5 STACK" else "✦ 1-CLICK INSTALL AERIX CLIENT 1.16.5",
                        subtitle = "Fabric 1.16.5 + Selected Add-Ons + Crystal Pack",
                        enabled = !AerixClientInstaller.isInstalling,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                val res = AerixClientInstaller.installOrSyncClient1165(activity)
                                onInstalledChanged()
                                res.onSuccess {
                                    Toast.makeText(activity, "Aerix Client 1.16.5 ready!", Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    Toast.makeText(activity, err.message ?: "Setup error", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }

            LiquidCrystalModuleToggleCard(
                title = "Core 1.16.5 FPS Engine Stack",
                subtitle = "Fabric API, Sodium, Lithium, FerriteCore (-45% RAM), LazyDFU (2x boot), Starlight, EntityCulling & ModMenu",
                checked = AerixClientStore.installFpsStack,
                badge = "CORE",
                onCheckedChange = { AerixClientStore.installFpsStack = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Iris Shaders 1.16.5 Support",
                subtitle = "Drop-in shaderpack compatibility on top of Sodium",
                checked = AerixClientStore.installShadersAddon,
                badge = "SHADERS",
                onCheckedChange = { AerixClientStore.installShadersAddon = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Survival & Dynamic Light Polish",
                subtitle = "AppleSkin food saturation overlay + LambDynamicLights offhand torch glow",
                checked = AerixClientStore.installSurvivalPolishAddon,
                badge = "SMP",
                onCheckedChange = { AerixClientStore.installSurvivalPolishAddon = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Logical Zoom (OptiFine C-Key Zoom)",
                subtitle = "Smooth cinematic spyglass zoom wired directly to our HUD Zoom pill",
                checked = AerixClientStore.installZoomAddon,
                badge = "ZOOM",
                onCheckedChange = { AerixClientStore.installZoomAddon = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Liquid Crystal 1.16.5 Resource Pack",
                subtitle = "Transparent crystal Main Menu & Hotbar, dark Mojang splash, Low Fire, Short Swords, Outlined Ores & Skybox",
                checked = AerixClientStore.installClientResourcePack,
                badge = "PACK",
                onCheckedChange = { AerixClientStore.installClientResourcePack = it; AerixClientStore.persist() }
            )
        }

        // Right Column: Quick Loadout Presets + Equipped Cosmetics Card + Client Milestones
        Column(
            modifier = Modifier
                .weight(0.95f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidCrystalCard {
                Text(
                    "Instant Playstyle Loadouts",
                    color = LiquidCrystalPalette.IceWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                ClientLoadoutPreset.values().forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.09f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                            .clickable {
                                AerixClientStore.applyLoadout(preset)
                                Toast.makeText(activity, "Applied ${preset.label} loadout", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.label, color = LiquidCrystalPalette.IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(preset.subtitle, color = LiquidCrystalPalette.IceSub, fontSize = 10.sp)
                        }
                        Text("APPLY →", color = LiquidCrystalPalette.PrismCyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            LiquidCrystalCard {
                val activeCape = AerixClientCosmetics.effectiveCapeSpec()
                val activeHat = AerixClientCosmetics.hats.firstOrNull { it.id == AerixClientStore.equippedHatId }
                    ?: AerixClientCosmetics.hats.first()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active 3D Loadout", color = LiquidCrystalPalette.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Cape: ${activeCape.title}", color = LiquidCrystalPalette.PrismCyan, fontSize = 12.sp)
                        Text("Headwear: ${activeHat.title}", color = LiquidCrystalPalette.IceSub, fontSize = 11.sp)
                    }
                    LiquidCrystalPill("Open Locker →", selected = true, onClick = onOpenCosmetics)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active 1.16.5 Pack", color = LiquidCrystalPalette.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Skybox: ${AerixClientStore.packSkyboxTheme.label} · Hit: ${AerixClientStore.packHitSound.label}",
                            color = LiquidCrystalPalette.IceSub,
                            fontSize = 11.sp
                        )
                    }
                    LiquidCrystalPill("Pack Studio →", selected = false, onClick = onOpenPack)
                }
            }

            LiquidCrystalCard {
                Text("Client Mastery & Challenges", color = LiquidCrystalPalette.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { AerixClientStore.levelProgressFraction() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = LiquidCrystalPalette.PrismMint,
                    trackColor = Color.White.copy(alpha = 0.14f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Peak CPS Record: ${AerixClientStore.peakCpsRecord} CPS · Session Wins: ${AerixClientStore.sessionWins} · Saved Waypoints: ${AerixClientStore.waypoints.size}",
                    color = LiquidCrystalPalette.IceSub,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * TAB 2: 3D COSMETICS LOCKER (CAPES, ELYTRAS, 3D HATS, OUTFITS & PAINTER)
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun ClientCosmeticsLockerTab(activity: FragmentActivity) {
    var showElytraPreview by remember { mutableStateOf(false) }
    var showPainter by remember { mutableStateOf(false) }
    val playerName = remember {
        PojavAccountBridge.current?.username?.takeIf { it.isNotBlank() } ?: "AerixPlayer"
    }
    val activeCape = AerixClientCosmetics.effectiveCapeSpec()
    val activeHat = AerixClientCosmetics.hats.firstOrNull { it.id == AerixClientStore.equippedHatId }
        ?: AerixClientCosmetics.hats.first()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = AerixClientCosmetics.importCustomCapeUri(activity, uri)
            Toast.makeText(
                activity,
                if (ok) "Imported custom 64×32 Cape & Elytra!" else "Could not decode PNG file",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left: 3D Character + Flowing Cape / Elytra Stage + Outfit Slots
        LiquidCrystalCard(
            modifier = Modifier.weight(0.95f).fillMaxHeight(),
            strong = true
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiquidCrystalPill("Cape View", selected = !showElytraPreview) { showElytraPreview = false }
                    LiquidCrystalPill("Elytra Wings", selected = showElytraPreview) { showElytraPreview = true }
                }
                LiquidCrystalPill(
                    label = if (AerixClientStore.animatedCapeShimmer) "✦ Shimmer ON" else "Shimmer OFF",
                    selected = AerixClientStore.animatedCapeShimmer,
                    onClick = {
                        AerixClientStore.animatedCapeShimmer = !AerixClientStore.animatedCapeShimmer
                        AerixClientStore.persist()
                    }
                )
            }

            Interactive3DCosmeticStage(
                capeSpec = activeCape,
                hatSpec = activeHat,
                showElytraMode = showElytraPreview,
                animatedShimmer = AerixClientStore.animatedCapeShimmer,
                playerName = playerName,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            // Quick Outfit Slots (1..5 + Save Current)
            Text("Quick Outfit Slots (1–5)", color = LiquidCrystalPalette.IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (slotNum in 1..5) {
                    val saved = AerixClientStore.outfitSlots.firstOrNull { it.slot == slotNum }
                    LiquidCrystalPill(
                        label = if (saved != null) "#$slotNum ${saved.name}" else "#$slotNum Save",
                        selected = saved?.capeId == AerixClientStore.equippedCapeId && saved.hatId == AerixClientStore.equippedHatId,
                        onClick = {
                            if (saved != null) {
                                AerixClientStore.equipOutfitSlot(saved)
                                Toast.makeText(activity, "Equipped Outfit #$slotNum", Toast.LENGTH_SHORT).show()
                            } else {
                                AerixClientStore.saveCurrentToOutfitSlot(slotNum)
                                Toast.makeText(activity, "Saved current look to Slot #$slotNum", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                LiquidCrystalPill(
                    label = "+ Overwrite #1",
                    onClick = {
                        AerixClientStore.saveCurrentToOutfitSlot(1)
                        Toast.makeText(activity, "Updated Outfit Slot #1", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Right: Capes Grid, Custom Cape Painter / Importer & 3D Headwear Grid
        Column(
            modifier = Modifier
                .weight(1.25f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidCrystalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("3D Capes & Matching 1.16.5 Elytras", color = LiquidCrystalPalette.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LiquidCrystalPill(
                            label = if (showPainter) "Close Painter" else "🎨 Pixel Painter",
                            selected = showPainter,
                            onClick = { showPainter = !showPainter }
                        )
                        LiquidCrystalPill(
                            label = "📂 Import PNG",
                            onClick = { importLauncher.launch("image/png") }
                        )
                    }
                }

                if (showPainter) {
                    Spacer(Modifier.height(10.dp))
                    CapePixelPainterPanel(
                        onSaved = {
                            Toast.makeText(activity, "Saved & equipped custom painted cape!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))
                AerixClientCosmetics.capes.forEach { spec ->
                    val unlocked = AerixClientCosmetics.canEquipCape(spec)
                    val equipped = activeCape.id == spec.id
                    val accentCol = when (spec.badgeText) {
                        "(OWNER)" -> LiquidCrystalPalette.OwnerCrimson
                        "(CO-OWNER)" -> LiquidCrystalPalette.CoOwnerSky
                        else -> Color(spec.topHex)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (equipped) accentCol.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .border(
                                width = if (equipped) 1.2.dp else 0.8.dp,
                                color = if (equipped) Color.White else accentCol.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                if (unlocked) {
                                    AerixClientStore.equippedCapeId = spec.id
                                    AerixClientStore.awardXp(5)
                                    AerixClientStore.persist()
                                } else {
                                    Toast.makeText(
                                        activity,
                                        "Exclusive staff cape locked to ${spec.badgeText} account",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp, 36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Brush.verticalGradient(listOf(Color(spec.topHex), Color(spec.bottomHex))))
                                    .border(1.dp, Color(spec.emblemHex), RoundedCornerShape(6.dp))
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(spec.title, color = LiquidCrystalPalette.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    spec.badgeText?.let { b ->
                                        Text(b, color = accentCol, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Text(spec.subtitle, color = LiquidCrystalPalette.IceSub, fontSize = 11.sp)
                            }
                        }
                        Text(
                            text = when {
                                equipped -> "EQUIPPED"
                                !unlocked -> "LOCKED"
                                else -> "EQUIP"
                            },
                            color = when {
                                equipped -> LiquidCrystalPalette.PrismMint
                                !unlocked -> LiquidCrystalPalette.IceMuted
                                else -> LiquidCrystalPalette.IceWhite
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // 3D Wearable Hat-Layer Accessories
            LiquidCrystalCard {
                Text("3D Hat-Layer Headwear (Renders In-Game in 1.16.5)", color = LiquidCrystalPalette.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Non-destructively composited onto your 64×64 outer skin head layer via AerixSkinServer.",
                    color = LiquidCrystalPalette.IceSub,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                AerixClientCosmetics.hats.forEach { hat ->
                    val selected = AerixClientStore.equippedHatId == hat.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Color(hat.accentHex).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f))
                            .border(1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
                            .clickable {
                                AerixClientStore.equippedHatId = hat.id
                                AerixClientStore.persist()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(hat.title, color = LiquidCrystalPalette.IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(hat.subtitle, color = LiquidCrystalPalette.IceSub, fontSize = 10.sp)
                        }
                        Text(
                            if (selected) "ACTIVE" else "WEAR",
                            color = if (selected) LiquidCrystalPalette.PrismMint else LiquidCrystalPalette.PrismCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CapePixelPainterPanel(onSaved: () -> Unit) {
    val palette = listOf(
        0xFFC8FAFF.toInt(), 0xFF8DEFE0.toInt(), 0xFF288B9E.toInt(), 0xFF0E2433.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFF4769.toInt(), 0xFFFFD166.toInt(), 0xFFB794FF.toInt()
    )
    var brushColor by remember { mutableIntStateOf(palette[0]) }
    val grid = remember {
        mutableStateListOf<Int>().apply { addAll(AerixClientCosmetics.decodePaintedGrid()) }
    }

    LiquidCrystalCard(cornerRadius = 14.dp, strong = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("10×16 Cape Pixel Studio", color = LiquidCrystalPalette.IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LiquidCrystalPill("Reset") {
                    grid.clear()
                    grid.addAll(AerixClientCosmetics.defaultPaintedGrid())
                }
                LiquidCrystalPill("Save & Equip", selected = true) {
                    AerixClientCosmetics.savePaintedGrid(grid.toList())
                    onSaved()
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            palette.forEach { col ->
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(col))
                        .border(if (brushColor == col) 2.dp else 0.8.dp, Color.White, CircleShape)
                        .clickable { brushColor = col }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0 until 16) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in 0 until 10) {
                        val idx = r * 10 + c
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(Color(grid[idx]), RoundedCornerShape(2.dp))
                                .clickable { grid[idx] = brushColor }
                        )
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * TAB 3: PROCEDURAL 1.16.5 RESOURCE PACK & SKYBOX STUDIO
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun ClientPackStudioTab(activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidCrystalCard(strong = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aerix-Client-Pack.zip (pack_format: 6)", color = LiquidCrystalPalette.IceWhite, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Synthesized on-device for Minecraft 1.16.5 with zero external downloads.", color = LiquidCrystalPalette.IceSub, fontSize = 11.sp)
                    }
                    LiquidCrystalPrimaryButton(
                        text = "↻ APPLY TO 1.16.5",
                        onClick = {
                            scope.launch {
                                val applied = AerixClientInstaller.rebuildPackOnlyIfInstalled(activity)
                                Toast.makeText(
                                    activity,
                                    if (applied) "Rebuilt & synced Aerix-Client-Pack.zip!" else "Saved! Install Aerix Client 1.16.5 first to write pack.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }

            LiquidCrystalModuleToggleCard(
                title = "Custom Mojang Dark/Crystal Splash",
                subtitle = "Eliminates the blinding bright-red Mojang flashbang via monochromeLogo + Liquid Crystal boot plaque",
                checked = AerixClientStore.packCustomMojangSplash,
                badge = "BOOT",
                onCheckedChange = { AerixClientStore.packCustomMojangSplash = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Custom 1.16.5 Title Dashboard & Transparent GUI",
                subtitle = "Liquid Crystal clear Singleplayer/Multiplayer buttons, crystal 9-slot hotbar, custom splashes & 360° panorama",
                checked = AerixClientStore.packCustomMainMenu,
                badge = "MENU",
                onCheckedChange = { AerixClientStore.packCustomMainMenu = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Low Fire (-65% Screen Height)",
                subtitle = "Drops burning fire animation down so your PvP aim stays unobstructed",
                checked = AerixClientStore.packLowFire,
                badge = "PVP",
                onCheckedChange = { AerixClientStore.packLowFire = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Short PvP Swords (Wood → Netherite)",
                subtitle = "Compact pixel blades for cleaner melee tracking in 1.16.5 combat",
                checked = AerixClientStore.packShortSwords,
                badge = "PVP",
                onCheckedChange = { AerixClientStore.packShortSwords = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Lowered First-Person Shield (-50%)",
                subtitle = "Drops resting & blocking shields cleanly below crosshair height",
                checked = AerixClientStore.packLoweredShield,
                badge = "1.16.5",
                onCheckedChange = { AerixClientStore.packLoweredShield = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Compact Totem of Undying",
                subtitle = "42% compact offhand scale so holding a Totem never crowds the left side",
                checked = AerixClientStore.packCompactTotem,
                onCheckedChange = { AerixClientStore.packCompactTotem = it; AerixClientStore.persist() }
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidCrystalCard {
                Text("360° Skybox & Panorama Theme", color = LiquidCrystalPalette.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SkyboxTheme.values().forEach { s ->
                        LiquidCrystalPill(
                            label = s.label,
                            selected = AerixClientStore.packSkyboxTheme == s,
                            onClick = { AerixClientStore.packSkyboxTheme = s; AerixClientStore.persist() }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("PvP Bow / Crit Hit-Sound", color = LiquidCrystalPalette.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HitSoundPreset.values().forEach { h ->
                        LiquidCrystalPill(
                            label = h.label,
                            selected = AerixClientStore.packHitSound == h,
                            onClick = { AerixClientStore.packHitSound = h; AerixClientStore.persist() }
                        )
                    }
                }
            }

            LiquidCrystalModuleToggleCard(
                title = "Glowing Outlined Ores + Ancient Debris",
                subtitle = "Crisp neon borders on Diamond, Emerald, Gold, Iron, Redstone, Lapis & 1.16.5 Ancient Debris",
                checked = AerixClientStore.packOutlinedOres,
                badge = "NETHERITE",
                onCheckedChange = { AerixClientStore.packOutlinedOres = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Crystal-Clear Underwater Vision",
                subtitle = "Removes murky dark underwater vignette texture",
                checked = AerixClientStore.packClearWater,
                onCheckedChange = { AerixClientStore.packClearWater = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "No Pumpkin Blur",
                subtitle = "100% unobstructed vision when wearing Carved Pumpkins in The End",
                checked = AerixClientStore.packNoPumpkinBlur,
                badge = "SPEEDRUN",
                onCheckedChange = { AerixClientStore.packNoPumpkinBlur = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Borderless Clean Crystal Glass",
                subtitle = "Removes noisy diagonal streaks inside glass blocks",
                checked = AerixClientStore.packBorderlessGlass,
                onCheckedChange = { AerixClientStore.packBorderlessGlass = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "RGB Bow Charge Stages + Mining Progress Bar",
                subtitle = "Red → Gold → Mint bow charge indicator + color-shifting block break progress",
                checked = AerixClientStore.packRgbBowStages,
                onCheckedChange = {
                    AerixClientStore.packRgbBowStages = it
                    AerixClientStore.packMiningProgressBar = it
                    AerixClientStore.persist()
                }
            )
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * TAB 4: MODULES, TACTICAL CROSSHAIR STUDIO & CLOUD SHARE CODES
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun ClientModulesStudioTab(activity: FragmentActivity) {
    var importCodeInput by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left: Crosshair Studio + Visual & Chroma Theme
        Column(
            modifier = Modifier
                .weight(0.95f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidCrystalCard(strong = true) {
                Text("Tactical Crosshair Studio", color = LiquidCrystalPalette.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x55081520))
                        .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawAerixCrosshair(
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            shape = AerixClientStore.crosshairShape,
                            armSizePx = AerixClientStore.crosshairSize * 1.8f,
                            gapPx = AerixClientStore.crosshairGap * 1.6f,
                            thickPx = AerixClientStore.crosshairThickness * 1.4f,
                            outlinePx = AerixClientStore.crosshairOutline,
                            mainColor = AerixClientStore.activeThemeColor().copy(alpha = AerixClientStore.crosshairOpacity),
                            cooldownProgress = if (AerixClientStore.swordCooldownBarEnabled) 0.72f else null
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CrosshairShape.values().forEach { s ->
                        LiquidCrystalPill(
                            label = s.label,
                            selected = AerixClientStore.crosshairShape == s,
                            onClick = { AerixClientStore.crosshairShape = s; AerixClientStore.persist() }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Arm Size: ${AerixClientStore.crosshairSize.toInt()} · Center Gap: ${AerixClientStore.crosshairGap.toInt()}", color = LiquidCrystalPalette.IceSub, fontSize = 11.sp)
                Slider(
                    value = AerixClientStore.crosshairSize,
                    onValueChange = { AerixClientStore.crosshairSize = it; AerixClientStore.persist() },
                    valueRange = 3f..22f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = LiquidCrystalPalette.PrismCyan)
                )
                Slider(
                    value = AerixClientStore.crosshairGap,
                    onValueChange = { AerixClientStore.crosshairGap = it; AerixClientStore.persist() },
                    valueRange = 0f..14f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = LiquidCrystalPalette.PrismMint)
                )
            }

            LiquidCrystalCard {
                Text("Client Chroma Accent & GPU Visual Filter", color = LiquidCrystalPalette.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ClientChromaTheme.values().forEach { t ->
                        LiquidCrystalPill(
                            label = t.label,
                            selected = AerixClientStore.chromaTheme == t,
                            accent = Color(t.primaryHex),
                            onClick = { AerixClientStore.chromaTheme = t; AerixClientStore.persist() }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VisualFilterPreset.values().forEach { f ->
                        LiquidCrystalPill(
                            label = f.label,
                            selected = AerixClientStore.visualFilter == f,
                            onClick = { AerixClientStore.visualFilter = f; AerixClientStore.persist() }
                        )
                    }
                }
            }

            // Shareable Config Code Backup / Import
            LiquidCrystalCard {
                Text("Shareable Client Config Code", color = LiquidCrystalPalette.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidCrystalPill("📋 Copy My Config Code", selected = true) {
                        val code = AerixClientStore.exportConfigCode()
                        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("AerixClientConfig", code))
                        Toast.makeText(activity, "Copied AERIX1165 config code!", Toast.LENGTH_SHORT).show()
                    }
                    LiquidCrystalPill("Reset HUD Layout") {
                        AerixClientStore.resetHudPositions()
                        Toast.makeText(activity, "Reset HUD positions to default", Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = importCodeInput,
                        onValueChange = { importCodeInput = it },
                        placeholder = { Text("Paste AERIX1165:... code", fontSize = 11.sp, color = LiquidCrystalPalette.IceMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = crystalFieldColors()
                    )
                    LiquidCrystalPill("Import", selected = true) {
                        val ok = AerixClientStore.importConfigCode(importCodeInput)
                        Toast.makeText(activity, if (ok) "Config imported!" else "Invalid config code", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Right: Complete In-Game HUD & Mobile Claw Module Toggles
        Column(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiquidCrystalModuleToggleCard(
                title = "Live Performance Pill (Real GL FPS + 1% Low + RAM + Battery °C)",
                subtitle = "Reads native C swap-buffer counter directly with thermal guard warning",
                checked = AerixClientStore.perfPillEnabled,
                badge = "HUD",
                onCheckedChange = { AerixClientStore.perfPillEnabled = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Animated WASD Keystrokes + Dual LMB/RMB CPS",
                subtitle = "Liquid Crystal spring keycaps with live CPS counter",
                checked = AerixClientStore.keystrokesEnabled,
                badge = "PVP",
                onCheckedChange = { AerixClientStore.keystrokesEnabled = it; AerixClientStore.persist() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KeystrokesStyle.values().forEach { st ->
                        LiquidCrystalPill(
                            label = st.label,
                            selected = AerixClientStore.keystrokesStyle == st,
                            onClick = { AerixClientStore.keystrokesStyle = st; AerixClientStore.persist() }
                        )
                    }
                }
            }
            LiquidCrystalModuleToggleCard(
                title = "1.16.5 Sword Attack Cooldown Indicator",
                subtitle = "625ms recharge bar beneath crosshair for guaranteed 100% crit & sweep hits",
                checked = AerixClientStore.swordCooldownBarEnabled,
                badge = "1.16.5",
                onCheckedChange = { AerixClientStore.swordCooldownBarEnabled = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "360° Direction Compass + Pinned Waypoint Beacon",
                subtitle = "Top-center N/NE/E/SE/S/SW/W/NW ribbon with live waypoint distance",
                checked = AerixClientStore.compassEnabled,
                onCheckedChange = { AerixClientStore.compassEnabled = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Mobile Claw Quick-Action Pills + Hotbar Swipe Strip",
                subtitle = "180° Instant Flick, Hold-Zoom (C), Offhand Swap (F), Drop (Q) & 1–9 swipe",
                checked = AerixClientStore.quickPvpButtonsEnabled,
                badge = "MOBILE",
                onCheckedChange = {
                    AerixClientStore.quickPvpButtonsEnabled = it
                    AerixClientStore.hotbarSwipeEnabled = it
                    AerixClientStore.persist()
                }
            )
            LiquidCrystalModuleToggleCard(
                title = "Gyroscope Bow & Crossbow Micro-Aim (ADS)",
                subtitle = "Subtle phone tilt precision aiming when toggled on in-game",
                checked = AerixClientStore.gyroAdsEnabled,
                badge = "GYRO",
                onCheckedChange = { AerixClientStore.gyroAdsEnabled = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Fullbright Cave & Nether Vision",
                subtitle = "Writes gamma:100.0 into 1.16.5 options.txt for crystal-lit caves & fortresses",
                checked = AerixClientStore.fullbrightEnabled,
                onCheckedChange = { AerixClientStore.fullbrightEnabled = it; AerixClientStore.persist() }
            )
            LiquidCrystalModuleToggleCard(
                title = "Speedrun Split Timer + Auto-GG + Streamer Privacy",
                subtitle = "Millisecond 1.16.5 RTA split stopwatch & server IP masking",
                checked = AerixClientStore.speedrunTimerEnabled,
                onCheckedChange = { AerixClientStore.speedrunTimerEnabled = it; AerixClientStore.persist() }
            )
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * TAB 5: 1.16.5 STRONGHOLD TRIANGULATOR, ANVIL OPTIMIZER, SHULKER & WAYPOINTS
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun ClientCoopToolsTab(activity: FragmentActivity) {
    var throw1X by remember { mutableStateOf("120") }
    var throw1Z by remember { mutableStateOf("-240") }
    var throw1Yaw by remember { mutableStateOf("-34.5") }

    var throw2X by remember { mutableStateOf("310") }
    var throw2Z by remember { mutableStateOf("-180") }
    var throw2Yaw by remember { mutableStateOf("-61.2") }
    var triResult by remember { mutableStateOf<TriangulationResult?>(null) }

    var selectedEnchants by remember { mutableStateOf(setOf("sharp5", "loot3", "unb3", "sweep3", "mend1")) }
    var shulkerInput by remember { mutableStateOf("5000") }

    var wpName by remember { mutableStateOf("") }
    var wpX by remember { mutableStateOf("0") }
    var wpY by remember { mutableStateOf("64") }
    var wpZ by remember { mutableStateOf("0") }
    var wpDim by remember { mutableStateOf("Overworld") }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left: 2-Throw Eye-of-Ender Stronghold Triangulator + Waypoints & Nether ÷8 Linker
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidCrystalCard(strong = true) {
                Text("1.16.5 Eye-of-Ender Stronghold Triangulator", color = LiquidCrystalPalette.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text("Throw 2 Eyes of Ender ~150+ blocks apart (or paste F3+C from clipboard) to lock exact Stronghold Staircase & Nether Portal coords!", color = LiquidCrystalPalette.IceSub, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(throw1X, { throw1X = it }, label = { Text("T1 X", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(1f), colors = crystalFieldColors())
                    OutlinedTextField(throw1Z, { throw1Z = it }, label = { Text("T1 Z", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(1f), colors = crystalFieldColors())
                    OutlinedTextField(throw1Yaw, { throw1Yaw = it }, label = { Text("T1 Yaw°", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(1f), colors = crystalFieldColors())
                    LiquidCrystalPill("F3+C → #1", modifier = Modifier.align(Alignment.CenterVertically)) {
                        val clip = readClipText(activity)
                        val parsed = AerixClientStore.parseF3CClipboard(clip)
                        if (parsed != null) {
                            throw1X = "%.1f".format(parsed.first.first)
                            throw1Z = "%.1f".format(parsed.first.third)
                            throw1Yaw = "%.2f".format(parsed.second)
                        } else {
                            Toast.makeText(activity, "Press F3+C in 1.16.5 first!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(throw2X, { throw2X = it }, label = { Text("T2 X", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(1f), colors = crystalFieldColors())
                    OutlinedTextField(throw2Z, { throw2Z = it }, label = { Text("T2 Z", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(1f), colors = crystalFieldColors())
                    OutlinedTextField(throw2Yaw, { throw2Yaw = it }, label = { Text("T2 Yaw°", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(1f), colors = crystalFieldColors())
                    LiquidCrystalPill("F3+C → #2", modifier = Modifier.align(Alignment.CenterVertically)) {
                        val clip = readClipText(activity)
                        val parsed = AerixClientStore.parseF3CClipboard(clip)
                        if (parsed != null) {
                            throw2X = "%.1f".format(parsed.first.first)
                            throw2Z = "%.1f".format(parsed.first.third)
                            throw2Yaw = "%.2f".format(parsed.second)
                        } else {
                            Toast.makeText(activity, "Press F3+C in 1.16.5 first!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LiquidCrystalPrimaryButton(
                    text = "✦ TRIANGULATE 1.16.5 STRONGHOLD",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val s1 = EnderThrowSample(throw1X.toDoubleOrNull() ?: 0.0, throw1Z.toDoubleOrNull() ?: 0.0, throw1Yaw.toDoubleOrNull() ?: 0.0)
                        val s2 = EnderThrowSample(throw2X.toDoubleOrNull() ?: 0.0, throw2Z.toDoubleOrNull() ?: 0.0, throw2Yaw.toDoubleOrNull() ?: 0.0)
                        triResult = AerixClientStore.triangulateStronghold(s1, s2)
                        if (triResult == null) {
                            Toast.makeText(activity, "Throws are parallel — move sideways and re-measure!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                triResult?.let { r ->
                    Spacer(Modifier.height(8.dp))
                    LiquidCrystalCard(cornerRadius = 12.dp, accentBorder = LiquidCrystalPalette.PrismMint) {
                        Text(
                            "Stronghold Starter Staircase: X ${r.strongholdX}, Z ${r.strongholdZ}  (${r.distanceBlocks}m)",
                            color = LiquidCrystalPalette.PrismMint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Chunk (${r.chunkX}, ${r.chunkZ}) · Nether Travel Portal: X ${r.netherPortalX}, Z ${r.netherPortalZ}",
                            color = LiquidCrystalPalette.IceWhite,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        LiquidCrystalPill("📌 Pin Stronghold to HUD Compass", selected = true) {
                            AerixClientStore.addWaypoint("Stronghold", r.strongholdX, 36, r.strongholdZ, "Overworld", pin = true)
                            Toast.makeText(activity, "Pinned Stronghold to HUD Compass!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            // Waypoints & Nether ÷8 Portal Linker
            LiquidCrystalCard {
                Text("Waypoints & Automatic Nether ÷8 Portal Linker", color = LiquidCrystalPalette.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(wpName, { wpName = it }, label = { Text("Name", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(1.3f), colors = crystalFieldColors())
                    OutlinedTextField(wpX, { wpX = it }, label = { Text("X", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(0.8f), colors = crystalFieldColors())
                    OutlinedTextField(wpY, { wpY = it }, label = { Text("Y", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(0.7f), colors = crystalFieldColors())
                    OutlinedTextField(wpZ, { wpZ = it }, label = { Text("Z", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.weight(0.8f), colors = crystalFieldColors())
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Overworld", "Nether", "The End").forEach { d ->
                            LiquidCrystalPill(d, selected = wpDim == d) { wpDim = d }
                        }
                    }
                    LiquidCrystalPill("+ Save & Pin", selected = true) {
                        AerixClientStore.addWaypoint(
                            name = wpName,
                            x = wpX.toIntOrNull() ?: 0,
                            y = wpY.toIntOrNull() ?: 64,
                            z = wpZ.toIntOrNull() ?: 0,
                            dimension = wpDim,
                            pin = true
                        )
                        wpName = ""
                    }
                }
                Spacer(Modifier.height(8.dp))
                AerixClientStore.waypoints.forEach { w ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${if (w.pinned) "📌 " else ""}${w.name} · (${w.x}, ${w.y}, ${w.z}) [${w.dimension}]",
                                color = LiquidCrystalPalette.IceWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(w.netherMatchText(), color = LiquidCrystalPalette.PrismCyan, fontSize = 10.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LiquidCrystalPill(if (w.pinned) "Pinned" else "Pin", selected = w.pinned) {
                                AerixClientStore.togglePinWaypoint(w.id)
                            }
                            LiquidCrystalPill("✕") { AerixClientStore.removeWaypoint(w.id) }
                        }
                    }
                }
            }
        }

        // Right: Anvil Zero-"Too Expensive!" Optimizer + Shulker Math
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidCrystalCard(strong = true) {
                Text("Zero-\"Too Expensive!\" Anvil Enchant Optimizer", color = LiquidCrystalPalette.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text("Computes the minimum-XP binary tree combination order for 1.16.5 Netherite gear.", color = LiquidCrystalPalette.IceSub, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AerixClientStore.swordEnchantCatalog.forEach { ench ->
                        val on = ench.id in selectedEnchants
                        LiquidCrystalPill(
                            label = ench.name,
                            selected = on,
                            onClick = {
                                selectedEnchants = if (on) selectedEnchants - ench.id else selectedEnchants + ench.id
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val (totalLevels, steps) = remember(selectedEnchants) {
                    AerixClientStore.optimizeAnvilOrder("Netherite Sword", selectedEnchants)
                }
                Text(
                    "Optimal Total Cost: $totalLevels XP Levels (Safe below 40-lvl cap!)",
                    color = LiquidCrystalPalette.PrismMint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                steps.forEach { stepLine ->
                    Text(
                        text = stepLine,
                        color = LiquidCrystalPalette.IceWhite,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LiquidCrystalCard {
                Text("Mega-Build Shulker & Stack Calculator", color = LiquidCrystalPalette.IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = shulkerInput,
                    onValueChange = { shulkerInput = it },
                    label = { Text("Total Blocks Needed", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = crystalFieldColors()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = AerixClientStore.formatShulkerBreakdown(shulkerInput.toIntOrNull() ?: 0),
                    color = LiquidCrystalPalette.PrismCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun crystalFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = LiquidCrystalPalette.PrismCyan,
    unfocusedBorderColor = Color.White.copy(alpha = 0.30f),
    focusedLabelColor = LiquidCrystalPalette.PrismCyan,
    unfocusedLabelColor = LiquidCrystalPalette.IceSub
)

private fun readClipText(context: Context): String? = runCatching {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()
