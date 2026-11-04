package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixInstance
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.bridge.PojavTaskBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.guide.AerixGuideState
import net.kdt.pojavlaunch.aerix.guide.guideAnchor
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection

/** Screenshot-inspired launcher home. Every control delegates to the existing launcher bridges. */
@Composable
fun GlassHomeScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit, onMoreTools: () -> Unit) {
    LaunchedEffect(Unit) { PojavAccountBridge.ensureLoaded() }
    val selected = PojavInstanceBridge.selected
    val count = PojavInstanceBridge.instances.size
    val accent = AerixThemeManager.currentAccent()
    val create = { AerixNav.navigate(LauncherSection.CREATE_INSTANCE) }
    val openTab: (String) -> Unit = { tab ->
        if (selected == null) navigate(LauncherSection.LIBRARY) else PojavInstanceBridge.edit(activity, selected, tab)
    }
    val shortcuts = listOf(
        HomeShortcut("Mods", R.drawable.aerix_ic_extension_outlined) { openTab("mods") },
        HomeShortcut("Packs", R.drawable.aerix_ic_style_outlined) { openTab("packs") },
        HomeShortcut("Worlds", R.drawable.aerix_ic_public) { openTab("worlds") },
        HomeShortcut("Controls", R.drawable.aerix_ic_videogame_asset_outlined) { PojavNavigator.openCustomControls(activity) },
        HomeShortcut("Screenshots", R.drawable.aerix_ic_image_outlined) { openTab("shots") },
        HomeShortcut("Logs", R.drawable.aerix_ic_description_outlined) { navigate(LauncherSection.LOGS) },
        HomeShortcut("Files", R.drawable.aerix_ic_folder_outlined) { navigate(LauncherSection.FILES) },
        HomeShortcut("Wallpapers", R.drawable.aerix_ic_wallpaper) { navigate(LauncherSection.WALLPAPERS) },
        HomeShortcut("More tools", R.drawable.aerix_ic_more_horiz) { onMoreTools() }
    )
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 7.dp)) {
        val landscape = maxWidth >= 620.dp && maxWidth > maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_aerix_mark), "Aerix logo", Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("AERIX LAUNCHER", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 1)
                    Text("Java Edition · $count ${if (count == 1) "instance" else "instances"}", color = AerixSurface.textSecondary, fontSize = 10.sp, maxLines = 1)
                }
                TextButton(onClick = create, modifier = Modifier.guideAnchor(AerixGuideState.CREATE)) { Text("+ New", color = accent) }
            }
            if (landscape) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectedInstanceCard(selected, accent, create, { PojavLaunchBridge.play(activity) },
                        { selected?.let { PojavInstanceBridge.edit(activity, it, "overview") } }, Modifier.weight(1f).fillMaxHeight())
                    NewInstanceTile(create, Modifier.weight(.75f).fillMaxHeight())
                    ShortcutGrid(shortcuts, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectedInstanceCard(selected, accent, create, { PojavLaunchBridge.play(activity) },
                        { selected?.let { PojavInstanceBridge.edit(activity, it, "overview") } }, Modifier.fillMaxWidth().heightIn(min = 190.dp))
                    NewInstanceTile(create, Modifier.fillMaxWidth().height(100.dp))
                    ShortcutGrid(shortcuts, Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 340.dp))
                }
            }
        }
    }
}

private data class HomeShortcut(val label: String, val icon: Int, val guide: String? = null, val action: () -> Unit)

@Composable
private fun SelectedInstanceCard(instance: AerixInstance?, accent: Color, create: () -> Unit, play: () -> Unit,
    edit: () -> Unit, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = Color(0x86171B1B),
        border = BorderStroke(1.5.dp, accent.copy(alpha = .8f))) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                AerixImageWell(instance?.icon, R.drawable.ic_aerix_mark, 44.dp)
                Spacer(Modifier.weight(1f))
                if (instance != null) IconButton(onClick = edit, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(R.drawable.aerix_ic_edit_outlined), "Edit instance", tint = Color.White)
                }
            }
            Text(instance?.name ?: "No instance selected", maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(instance?.versionId ?: "Create an instance to get started", maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = AerixSurface.textSecondary, fontSize = 12.sp)
            if (instance != null) {
                Surface(shape = CircleShape, color = accent.copy(alpha = .18f), border = BorderStroke(1.dp, accent.copy(alpha = .5f))) {
                    Text("READY", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                }
            }
            if (PojavTaskBridge.taskCount > 0) Text("${PojavTaskBridge.taskCount} active tasks", color = AerixSurface.textSecondary, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Button(onClick = if (instance == null) create else play, modifier = Modifier.fillMaxWidth().guideAnchor(AerixGuideState.PLAY),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF09231A))) {
                Icon(painterResource(if (instance == null) R.drawable.aerix_ic_add else R.drawable.aerix_ic_play_arrow_filled), null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (instance == null) "Create instance" else "Play", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NewInstanceTile(onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(22.dp),
        color = Color(0x541C2020), border = BorderStroke(1.dp, AerixSurface.borderSoft)) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(painterResource(R.drawable.aerix_ic_add), null, Modifier.size(32.dp), tint = Color.White)
            Text("New instance", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Vanilla, loaders or a modpack", color = AerixSurface.textSecondary, fontSize = 10.sp, maxLines = 2)
        }
    }
}

@Composable
private fun ShortcutGrid(shortcuts: List<HomeShortcut>, modifier: Modifier) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(shortcuts.chunked(2)) { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                pair.forEach { shortcut ->
                    Surface(onClick = shortcut.action, modifier = Modifier.weight(1f).then(
                        if (shortcut.guide != null) Modifier.guideAnchor(shortcut.guide) else Modifier),
                        shape = RoundedCornerShape(18.dp), color = Color(0x701B1E1D),
                        border = BorderStroke(1.dp, AerixSurface.borderSoft)) {
                        Row(Modifier.heightIn(min = 42.dp).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(shortcut.icon), null, Modifier.size(18.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(shortcut.label, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
