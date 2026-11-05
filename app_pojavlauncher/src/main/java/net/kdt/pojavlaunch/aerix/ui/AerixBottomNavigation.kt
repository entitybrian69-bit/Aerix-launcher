package net.kdt.pojavlaunch.aerix.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.guide.AerixGuideState
import net.kdt.pojavlaunch.aerix.guide.guideAnchor
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

private data class BottomDestination(val label: String, val icon: Int, val section: LauncherSection, val guide: String? = null)
private val bottomDestinations = listOf(
    BottomDestination("Home", R.drawable.aerix_ic_home_filled, LauncherSection.HOME),
    BottomDestination("Instances", R.drawable.aerix_ic_dashboard_filled, LauncherSection.LIBRARY, AerixGuideState.LIBRARY),
    BottomDestination("Browse", R.drawable.aerix_ic_search, LauncherSection.DISCOVER),
    BottomDestination("Client", R.drawable.aerix_ic_client_crystal, LauncherSection.AERIX_CLIENT),
    BottomDestination("Rust", R.drawable.aerix_ic_videogame_asset_outlined, LauncherSection.HORIZON),
    BottomDestination("Social", R.drawable.aerix_ic_people, LauncherSection.SOCIALS),
    BottomDestination("Settings", R.drawable.aerix_ic_settings_filled, LauncherSection.SETTINGS, AerixGuideState.SETTINGS)
)

@Composable
fun AerixBottomNavigation(selectedSection: LauncherSection, onNavigate: (LauncherSection) -> Unit, modifier: Modifier = Modifier) {
    val account = PojavAccountBridge.current
    val accent = AerixThemeManager.currentAccent()
    var moreOpen by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 700.dp
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = { onNavigate(LauncherSection.ACCOUNTS) }, modifier = Modifier.semantics { contentDescription = "Accounts" }, shape = CircleShape,
                color = Color(0xE91A1E20), border = BorderStroke(1.dp, AerixSurface.borderSoft)) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    AerixImageWell(account?.face, R.drawable.aerix_ic_person_outlined, 30.dp)
                    if (wide) {
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.widthIn(max = 90.dp)) {
                            Text(account?.username ?: "Account", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(account?.authType?.name ?: "Sign in", maxLines = 1, color = AerixSurface.textSecondary, fontSize = 9.sp)
                        }
                    }
                }
            }
            Surface(modifier = Modifier.weight(1f), shape = CircleShape, color = Color(0xE91A1E20),
                border = BorderStroke(1.dp, AerixSurface.borderSoft)) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    bottomDestinations.forEach { destination ->
                        val selected = when (destination.section) {
                            LauncherSection.LIBRARY -> selectedSection in listOf(LauncherSection.LIBRARY, LauncherSection.INSTANCE_EDITOR, LauncherSection.CONTENT, LauncherSection.CREATE_INSTANCE)
                            LauncherSection.DISCOVER -> selectedSection in listOf(LauncherSection.DISCOVER, LauncherSection.DOWNLOADS)
                            else -> selectedSection == destination.section
                        }
                        Surface(onClick = { onNavigate(destination.section) }, shape = CircleShape,
                            color = if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                            modifier = (if (destination.guide != null) Modifier.guideAnchor(destination.guide) else Modifier)) {
                            Column(Modifier.width(if (wide) 74.dp else 61.dp).padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painterResource(destination.icon), destination.label,
                                    Modifier.size(19.dp), tint = if (selected) accent else AerixSurface.textSecondary)
                                Text(destination.label, fontSize = 9.sp, maxLines = 1,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else AerixSurface.textSecondary)
                            }
                        }
                    }
                }
            }
            Box {
                Surface(onClick = { moreOpen = true }, modifier = Modifier.guideAnchor(AerixGuideState.WALLPAPERS), shape = CircleShape, color = Color(0xE91A1E20),
                    border = BorderStroke(1.dp, AerixSurface.borderSoft)) {
                    Icon(painterResource(R.drawable.aerix_ic_more_horiz), "More launcher pages",
                        Modifier.padding(12.dp).size(20.dp), tint = Color.White)
                }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    listOf("Create instance" to LauncherSection.CREATE_INSTANCE, "Files" to LauncherSection.FILES,
                        "Logs" to LauncherSection.LOGS, "Wallpapers" to LauncherSection.WALLPAPERS,
                        "Other releases" to LauncherSection.OTHER_RELEASES, "Contributors" to LauncherSection.CONTRIBUTION,
                        "About" to LauncherSection.ABOUT).forEach { (label, target) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { moreOpen = false; onNavigate(target) })
                    }
                }
            }
        }
    }
}
