package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
internal data class TeamRelease(
    val title: String, val slug: String, val type: AssetType, val iconUrl: String,
    val attribution: String, val badges: String, val headline: String, val summary: String,
    val features: List<Pair<String, String>>, val compatibility: String,
    val warning: String, val footnote: String, val projectId: String? = null
)
internal val teamReleases = listOf(TeamRelease(
    // Verified from the CurseForge API/mirror on 2026-10-19, not a guessed title match.
    title = "Aetherium (Fabric)", slug = "aetherium-fabric", projectId = "1749327", type = AssetType.MOD,
    iconUrl = "https://media.forgecdn.net/avatars/thumbnails/2125/90/256/256/639278007572469420.png",
    attribution = "By Team_Aerix · CurseForge", badges = "Mod · Fabric · Client-side",
    headline = "A new view of Minecraft performance",
    summary = "Aetherium brings its own terrain renderer, frame-pacing controls and a redesigned video-settings experience to Fabric. The project describes features for both desktop and Android/Pojav setups.",
    features = listOf(
        "Rendering & frame pacing" to "Terrain rendering, entity and chunk culling, threaded chunk loading, and a frame-time/FPS overlay.",
        "Your world, your settings" to "Six animated settings pages, presets, dynamic lights, fullbright, zoom and freelook.",
        "Android-aware options" to "Memory and touch options, battery FPS caps and thermal controls, depending on the Minecraft version."
    ),
    compatibility = "Fabric Loader only; Fabric API is not required. The project lists Minecraft 1.16.5–26.3, with separate files for exact game versions. Choose a compatible file in Discover.",
    warning = "Do not combine with Sodium, Embeddium, Rubidium or OptiFine. If rendering glitches occur, the project recommends disabling the Aetherium renderer in Video Settings → Backend.",
    footnote = "Features and compatibility are described by the project; performance depends on your device, renderer and game version. No FPS gain is guaranteed."
))

@Composable
internal fun OtherReleasesScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxWidth(0.94f).fillMaxHeight().padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FROM TEAM AERIX", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Our other new releases", color = AerixSurface.textPrimary,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("More than a launcher. Explore our mods, modpacks, resource packs, shaders and world saves here as we release them.",
                        color = AerixSurface.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(teamReleases, key = { it.slug }) { project -> TeamReleaseCard(activity, project, navigate) }
            item {
                Text("Only our published projects appear here. More releases will be added when we share them.",
                    style = MaterialTheme.typography.bodyMedium, color = AerixSurface.textSecondary)
            }
        }
    }
}

@Composable
private fun TeamReleaseCard(activity: FragmentActivity, project: TeamRelease, navigate: (LauncherSection) -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val icon = rememberRemoteBitmap(project.iconUrl)
    Surface(shape = RoundedCornerShape(22.dp), color = AerixSurface.panel) {
        Column(Modifier.background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.13f), AerixSurface.panel)))
            .padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BoxWithConstraints {
                val artwork: @Composable () -> Unit = {
                    Surface(shape = RoundedCornerShape(18.dp), color = accent.copy(alpha = 0.12f)) {
                        if (icon != null) Image(icon, contentDescription = "${project.title} project logo", modifier = Modifier.size(60.dp).padding(8.dp))
                        else Icon(painterResource(R.drawable.aerix_ic_public), contentDescription = null,
                            tint = accent, modifier = Modifier.size(60.dp).padding(18.dp))
                    }
                }
                val heading: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(project.title, style = MaterialTheme.typography.titleLarge, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold)
                        Text(project.badges, style = MaterialTheme.typography.labelLarge, color = accent)
                        Text(project.attribution, style = MaterialTheme.typography.bodyMedium, color = AerixSurface.textSecondary)
                    }
                }
                if (maxWidth < 360.dp) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { artwork(); heading() }
                else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    artwork()
                    Box(Modifier.weight(1f)) { heading() }
                }
            }
            Text(project.headline, style = MaterialTheme.typography.titleLarge, color = AerixSurface.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(project.summary,
                style = MaterialTheme.typography.bodyMedium, color = AerixSurface.textSecondary)
            project.features.forEach { (title, description) -> ReleaseFeature(title, description) }
            HorizontalDivider(color = AerixSurface.borderSoft)
            ReleaseFeature("Check compatibility first", project.compatibility)
            Surface(shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = 0.09f)) {
                Text(project.warning,
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = AerixSurface.textPrimary)
            }
            Text(project.footnote,
                style = MaterialTheme.typography.bodySmall, color = AerixSurface.textSecondary)
            if (error != null) Text(error!!, color = AerixSurface.danger, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                opening = true; error = null
                scope.launch {
                    try {
                        val resolved = PojavAssetBridge.resolveCurseForgeSlug(activity, project.type, project.slug, project.projectId)
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
            Text("Opens this exact project in Aerix's CurseForge detail page. Review live files and choose your Minecraft instance before installing.",
                style = MaterialTheme.typography.bodyMedium, color = AerixSurface.textSecondary)
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
