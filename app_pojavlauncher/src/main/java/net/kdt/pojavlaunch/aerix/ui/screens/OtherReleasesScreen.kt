package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.PojavAssetBridge
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.LauncherSection

/** Curated, not an advertising feed. Add only projects explicitly supplied by the team. */
internal data class TeamRelease(val title: String, val slug: String, val type: AssetType, val iconUrl: String)
internal val teamReleases = listOf(TeamRelease("Aetherium (Fabric)", "aetherium-fabric", AssetType.MOD,
    "https://media.forgecdn.net/avatars/thumbnails/2125/90/64/64/639278007572469420.png"))

@Composable
internal fun OtherReleasesScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val project = teamReleases.single()
    val icon = rememberRemoteBitmap(project.iconUrl)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 1000.dp).fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FROM TEAM AERIX", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Our other new releases", color = AerixSurface.textPrimary,
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("More than a launcher. Explore our mods, modpacks, resource packs, shaders and world saves here as we release them.",
                        color = AerixSurface.textSecondary, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                Surface(shape = RoundedCornerShape(28.dp), color = AerixSurface.panel) {
                    Column(Modifier.background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.13f), AerixSurface.panel)))
                        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Surface(shape = RoundedCornerShape(18.dp), color = accent.copy(alpha = 0.12f)) {
                                if (icon != null) Image(icon, contentDescription = "Aetherium project logo", modifier = Modifier.size(72.dp).padding(8.dp))
                                else Icon(painterResource(R.drawable.aerix_ic_public), contentDescription = null,
                                    tint = accent, modifier = Modifier.size(72.dp).padding(18.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(project.title, style = MaterialTheme.typography.headlineSmall, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Mod · Fabric · Client-side", style = MaterialTheme.typography.labelLarge, color = accent)
                                Text("By Team_Aerix · CurseForge", style = MaterialTheme.typography.bodyMedium, color = AerixSurface.textSecondary)
                            }
                        }
                        Text("A new view of Minecraft performance", style = MaterialTheme.typography.titleLarge, color = AerixSurface.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Aetherium brings its own terrain renderer, frame-pacing controls and a redesigned video-settings experience to Fabric. The project describes features for both desktop and Android/Pojav setups.",
                            style = MaterialTheme.typography.bodyLarge, color = AerixSurface.textSecondary)
                        ReleaseFeature("Rendering & frame pacing", "Terrain rendering, entity and chunk culling, threaded chunk loading, and a frame-time/FPS overlay.")
                        ReleaseFeature("Your world, your settings", "Six animated settings pages, presets, dynamic lights, fullbright, zoom and freelook.")
                        ReleaseFeature("Android-aware options", "Memory and touch options, battery FPS caps and thermal controls, depending on the Minecraft version.")
                        HorizontalDivider(color = AerixSurface.borderSoft)
                        ReleaseFeature("Check compatibility first", "Fabric Loader only; Fabric API is not required. The project lists Minecraft 1.16.5–26.3, with separate files for exact game versions. Choose a compatible file in Discover.")
                        Surface(shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = 0.09f)) {
                            Text("Do not combine with Sodium, Embeddium, Rubidium or OptiFine. If rendering glitches occur, the project recommends disabling the Aetherium renderer in Video Settings → Backend.",
                                Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = AerixSurface.textPrimary)
                        }
                        Text("Features and compatibility are described by the project; performance depends on your device, renderer and game version. No FPS gain is guaranteed.",
                            style = MaterialTheme.typography.bodySmall, color = AerixSurface.textSecondary)
                        if (error != null) Text(error!!, color = AerixSurface.danger, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = {
                            opening = true; error = null
                            scope.launch {
                                try {
                                    val resolved = PojavAssetBridge.resolveCurseForgeSlug(activity, project.type, project.slug)
                                    if (resolved == null) error = "Couldn't find this exact CurseForge project. Check your connection and CurseForge access in Settings, then retry."
                                    else AerixDownloadRequest.openProject(resolved, navigate)
                                } finally { opening = false }
                            }
                        }, enabled = !opening, shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp)) {
                            if (opening) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)) }
                            Text(if (opening) "Opening CurseForge project…" else "View & download in Discover")
                        }
                        Text("Opens this exact Fabric mod in Aerix's CurseForge detail page. Review live files and choose your Minecraft instance before installing.",
                            style = MaterialTheme.typography.bodyMedium, color = AerixSurface.textSecondary)
                    }
                }
            }
            item {
                Text("Only our published projects appear here. More releases will be added when we share them.",
                    style = MaterialTheme.typography.bodyMedium, color = AerixSurface.textSecondary)
            }
        }
    }
}

@Composable
private fun ReleaseFeature(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AerixSurface.textPrimary)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = AerixSurface.textSecondary)
    }
}
