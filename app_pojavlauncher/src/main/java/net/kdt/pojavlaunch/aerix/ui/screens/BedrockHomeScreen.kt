/*
 * Aerix Launcher — Bedrock-inspired Home (minimal safe build)
 * 1:1 structure to screenshots, original Aerix rendering.
 */

package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.GameDownloadTarget
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.LauncherSection

@Composable
fun BedrockHomeScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    LaunchedEffect(Unit) { PojavAccountBridge.ensureLoaded() }
    val instances = PojavInstanceBridge.instances
    val selected = PojavInstanceBridge.selected
    val accent = AerixThemeManager.currentAccent()
    // asset progress driven by task count
    val taskCount = net.kdt.pojavlaunch.aerix.bridge.PojavTaskBridge.taskCount
    val assetProgress = if (taskCount > 0) 0.42f else 0.93f
    val isDownloading = taskCount > 0

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp

        // vignette
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)))
            )
        )

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.width(220.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BedrockCardSimple("Shop", "Bonus waiting", "!", Color(0xFFD11B3A), Color(0xFF8E0F24), R.drawable.aerix_ic_package_2_outlined) { navigate(LauncherSection.DISCOVER) }
                    BedrockCardSimple("My skin", null, null, Color(0xFF1E6FE0), Color(0xFF1349A3), R.drawable.aerix_ic_person_outlined, showAvatar = true) { navigate(LauncherSection.ACCOUNTS) }
                    BedrockCardSimple("Play with friends", null, null, Color(0xFF1FA24A), Color(0xFF12612E), R.drawable.aerix_ic_people) { navigate(LauncherSection.SOCIALS) }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        BedrockPill("Find a party", R.drawable.aerix_ic_search, Modifier.weight(1f)) { navigate(LauncherSection.SOCIALS) }
                        BedrockPill("Invite a friend", R.drawable.aerix_ic_group_outlined, Modifier.weight(1f)) { navigate(LauncherSection.SOCIALS) }
                    }
                }
                // center doll
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    val account = PojavAccountBridge.current
                    Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 12.dp).size(width = 160.dp, height = 18.dp).background(Color.Black.copy(alpha = 0.24f), RoundedCornerShape(50))) {}
                    AerixSkinPreview3D(account = account, azimuth = 28, interactionEnabled = false, modifier = Modifier.fillMaxSize().padding(bottom = 22.dp))
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-6).dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.55f)).border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).clickable { navigate(LauncherSection.ACCOUNTS) }.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) { Text(account?.username ?: "+ Add Account", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
                Column(modifier = Modifier.width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            R.drawable.aerix_ic_person_outlined to LauncherSection.ACCOUNTS,
                            R.drawable.aerix_ic_favorite_outlined to LauncherSection.DISCOVER,
                            R.drawable.aerix_ic_settings_filled to LauncherSection.SETTINGS
                        ).forEach { (ic, dest) ->
                            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).clickable { navigate(dest) }, contentAlignment = Alignment.Center) {
                                Icon(painterResource(ic), null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF101214)).border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).clickable { navigate(LauncherSection.SOCIALS) }.padding(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("+1 D OF PLUS FOR EVERY FRIEND", color = Color(0xFF9AA0A6), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("FREE PLUS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text("FOR A FRIEND", color = Color(0xFFFFC107), fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFC107)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text("M PLUS", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            // bottom dock overlay
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                BedrockDock(activity, navigate, selected, isDownloading, assetProgress, Modifier.fillMaxWidth().padding(12.dp).align(Alignment.BottomCenter))
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                Box(modifier = Modifier.padding(end = 12.dp, bottom = 74.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1E21)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).clickable {}.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(painterResource(R.drawable.aerix_ic_info_outlined), null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Help", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // compact
            val scroll = rememberScrollState()
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    val acc = PojavAccountBridge.current
                    AerixSkinPreview3D(account = acc, azimuth = 28, interactionEnabled = false, modifier = Modifier.fillMaxSize())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BedrockCardSimple("Shop", "Bonus", "!", Color(0xFFD11B3A), Color(0xFF8E0F24), R.drawable.aerix_ic_package_2_outlined, modifier = Modifier.weight(1f).height(86.dp)) { navigate(LauncherSection.DISCOVER) }
                    BedrockCardSimple("My skin", null, null, Color(0xFF1E6FE0), Color(0xFF1349A3), R.drawable.aerix_ic_person_outlined, showAvatar = true, modifier = Modifier.weight(1f).height(86.dp)) { navigate(LauncherSection.ACCOUNTS) }
                }
                BedrockCardSimple("Play with friends", null, null, Color(0xFF1FA24A), Color(0xFF12612E), R.drawable.aerix_ic_people, modifier = Modifier.fillMaxWidth().height(88.dp)) { navigate(LauncherSection.SOCIALS) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BedrockPill("Find a party", R.drawable.aerix_ic_search, Modifier.weight(1f)) { navigate(LauncherSection.SOCIALS) }
                    BedrockPill("Invite a friend", R.drawable.aerix_ic_group_outlined, Modifier.weight(1f)) { navigate(LauncherSection.SOCIALS) }
                }
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF101214)).border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).clickable { navigate(LauncherSection.SOCIALS) }.padding(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("+1 D OF PLUS FOR EVERY FRIEND", color = Color(0xFF9AA0A6), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("FREE PLUS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("FOR A FRIEND", color = Color(0xFFFFC107), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                }
                BedrockDock(activity, navigate, selected, isDownloading, assetProgress, Modifier.fillMaxWidth())
                Box(modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1E21)).padding(horizontal = 12.dp, vertical = 8.dp)) { Text("Help", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun BedrockCardSimple(title: String, subtitle: String?, badge: String?, c1: Color, c2: Color, iconRes: Int, modifier: Modifier = Modifier, showAvatar: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier = modifier.clip(shape).background(Brush.verticalGradient(listOf(c1, c2))).border(2.dp, Color.Black.copy(alpha = 0.42f), shape).clickable(onClick = onClick).padding(3.dp)) {
        Box(modifier = Modifier.fillMaxSize().clip(shape).background(Brush.verticalGradient(listOf(c1, c2))).padding(horizontal = 8.dp, vertical = 7.dp)) {
            if (badge != null) {
                Box(modifier = Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-4).dp).size(18.dp).clip(RoundedCornerShape(50)).background(Color.White).border(1.dp, Color.Black, RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Text("!", color = Color(0xFFD11B3A), fontSize = 12.sp, fontWeight = FontWeight.Black) }
            }
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.22f)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    if (showAvatar) {
                        val acc = PojavAccountBridge.current
                        AerixImageWell(image = acc?.face, fallback = R.drawable.aerix_head_steve, size = 32.dp, radius = 6.dp)
                    } else Icon(painterResource(iconRes), null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BedrockPill(text: String, icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1E21)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(icon), null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(14.dp))
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun BedrockDock(activity: FragmentActivity, navigate: (LauncherSection) -> Unit, selected: net.kdt.pojavlaunch.aerix.bridge.AerixInstance?, isDownloading: Boolean, assetProgress: Float, modifier: Modifier) {
    val vanilla = stringResource(R.string.aerix_home_vanilla)
    val shape = RoundedCornerShape(14.dp)
    val showPlay = !isDownloading
    Row(modifier = modifier.clip(shape).background(Color(0xE6141A1F)).border(1.dp, Color.White.copy(alpha = 0.08f), shape).padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = Color(0xFF1B5ECC), contentColor = Color.White, onClick = { selected?.let { net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge.edit(activity, it) } ?: navigate(LauncherSection.LIBRARY) }) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F3A8A)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    AerixImageWell(image = selected?.icon, fallback = R.drawable.aerix_ic_videogame_asset_outlined, size = 28.dp, radius = 6.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("START HERE", color = Color.White.copy(alpha = 0.84f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text(selected?.name ?: "Modpack 1.20.1", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(selected?.let { "${loaderLabel(it.versionId, vanilla)} · ${GameDownloadTarget.minecraftVersionIn(it.versionId) ?: it.versionId}" } ?: "Fabric · 1.20.1", color = Color.White.copy(alpha = 0.92f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(painterResource(R.drawable.aerix_ic_chevron_right), null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
            }
        }
        Column(modifier = Modifier.width(184.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showPlay) {
                Surface(modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(10.dp), color = Color(0xFF4CAF50), onClick = { if (selected != null) PojavLaunchBridge.play(activity) else navigate(LauncherSection.CREATE_INSTANCE) }) {
                    Column(modifier = Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(painterResource(R.drawable.aerix_ic_play_arrow_filled), null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Text("Play", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        }
                        Text("0 h", color = Color.Black.copy(alpha = 0.78f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Surface(modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(10.dp), color = Color(0xFF2E7D32)) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Assets", color = Color.White.copy(alpha = 0.92f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("‥ ${(assetProgress * 100).toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                            Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black).clickable {}, contentAlignment = Alignment.Center) { Text("×", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f))) { Box(Modifier.fillMaxHeight().fillMaxWidth(assetProgress).background(Color.White)) }
                    }
                }
            }
            Surface(modifier = Modifier.fillMaxWidth().height(30.dp), shape = RoundedCornerShape(8.dp), color = Color(0xFFFFD54F), onClick = { selected?.let { PojavInstanceBridge.edit(activity, it) } ?: navigate(LauncherSection.FILES) }) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(painterResource(R.drawable.aerix_ic_videogame_asset_outlined), null, tint = Color(0xFF5D4037), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open chest", color = Color(0xFF3E2723), fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
