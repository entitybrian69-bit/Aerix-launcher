package net.kdt.pojavlaunch.aerix.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixInstallQueue
import net.kdt.pojavlaunch.aerix.bridge.AerixInstance
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.bridge.PojavTaskBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension

/** Home's former quick-action, task and recent-instance cards, collected on one destination. */
@Composable
fun QuickToolsScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val instances = PojavInstanceBridge.instances
    val selected = PojavInstanceBridge.selected
    val taskCount = PojavTaskBridge.taskCount
    val jarPicker = rememberLauncherForActivityResult(OpenDocumentWithExtension("jar")) { uri ->
        if (uri != null) PojavNavigator.runJar(activity, uri)
    }

    AerixPage(title = "Quick Tools", subtitle = "Launcher shortcuts, recent instances and background work") {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AerixRadii.card),
                color = AerixSurface.panelRaised,
                border = BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.34f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(AerixSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                ) {
                    Icon(
                        painter = painterResource(if (AerixInstallQueue.visible) R.drawable.aerix_ic_download else R.drawable.aerix_ic_schedule_outlined),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(25.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (taskCount == 0 && !AerixInstallQueue.visible) "All caught up" else "Work in progress",
                            color = AerixSurface.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                AerixInstallQueue.visible -> "An install is running; its progress panel stays available above the launcher."
                                taskCount > 0 -> "$taskCount background task(s) are active."
                                else -> "Downloads and installs continue while you move around the launcher."
                            },
                            color = AerixSurface.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    if (AerixInstallQueue.visible) {
                        Text("OPEN", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp,
                            modifier = Modifier.clickable { AerixInstallQueue.openPanel() }.padding(6.dp))
                    }
                }
            }

            Text("QUICK ACTIONS", color = AerixSurface.textSecondary, fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
            ToolRow("Instance library", "Manage, create and edit Minecraft instances", R.drawable.aerix_ic_dashboard_filled) {
                navigate(LauncherSection.LIBRARY)
            }
            ToolRow("Open game folder", "Browse the selected instance's files", R.drawable.aerix_ic_folder_outlined) {
                PojavInstanceBridge.openGameDirectory(activity)
            }
            ToolRow("File manager", "Browse launcher and Minecraft files", R.drawable.aerix_ic_folder_filled) {
                navigate(LauncherSection.FILES)
            }
            ToolRow("Run a JAR", "Choose a Java archive to launch", R.drawable.aerix_ic_java) {
                if (PojavNavigator.canRunJar(activity)) jarPicker.launch(null)
                else Toast.makeText(activity, "No active instance or Java runtime is available", Toast.LENGTH_LONG).show()
            }
            ToolRow("Control layout", "Edit touch controls and on-screen buttons", R.drawable.aerix_ic_keyboard_outlined) {
                PojavNavigator.openCustomControls(activity)
            }
            ToolRow("Gamepad mapper", "Configure controller buttons", R.drawable.aerix_ic_sports_esports_outlined) {
                PojavNavigator.openGamepadMapper(activity)
            }
            ToolRow("Logs", "Review launcher logs", R.drawable.aerix_ic_description_outlined) {
                navigate(LauncherSection.LOGS)
            }
            ToolRow("Share launcher logs", "Send a diagnostic log bundle", R.drawable.aerix_ic_share_filled) {
                PojavNavigator.shareLogs(activity)
            }
            ToolRow("Aerix account", "Sign in to your Aerix community account", R.drawable.aerix_ic_person_outlined) {
                navigate(LauncherSection.AERIX_ACCOUNT)
            }
            ToolRow("Downloads & discover", "Find modpacks, versions and content", R.drawable.aerix_ic_cloud_download) {
                navigate(LauncherSection.DISCOVER)
            }
            ToolRow("Settings", "Renderer, controls and launcher preferences", R.drawable.aerix_ic_settings_filled) {
                navigate(LauncherSection.SETTINGS)
            }
            ToolRow("Social profile", "Friends, messages, parties and your Aerix profile", R.drawable.aerix_ic_person_outlined) {
                SocialsNavigation.requestMyProfile()
                navigate(LauncherSection.SOCIALS)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = AerixSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("RECENT INSTANCES", color = AerixSurface.textSecondary, fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Text("VIEW ALL", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { navigate(LauncherSection.INSTANCE_SELECTOR) }.padding(6.dp))
            }
            if (instances.isEmpty()) {
                Text("No instances yet. Create one from the library to get started.",
                    color = AerixSurface.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            } else {
                instances.asReversed().take(6).forEach { instance ->
                    RecentInstanceRow(
                        instance = instance,
                        selected = selected?.id == instance.id,
                        accent = accent,
                        onSelect = {
                            PojavInstanceBridge.select(instance)
                            AerixNav.finishTo(LauncherSection.HOME)
                        },
                        onPlay = {
                            PojavInstanceBridge.select(instance)
                            PojavLaunchBridge.play(activity)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolRow(title: String, description: String, icon: Int, onClick: () -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(AerixRadii.cardSmall),
        color = AerixSurface.panel.copy(alpha = 0.82f),
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(AerixSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(title, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium)
                Text(description, color = AerixSurface.textSecondary, fontSize = 10.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(painterResource(R.drawable.aerix_ic_chevron_right), contentDescription = null,
                tint = AerixSurface.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RecentInstanceRow(
    instance: AerixInstance,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onSelect: () -> Unit,
    onPlay: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AerixRadii.cardSmall),
        color = if (selected) accent.copy(alpha = 0.12f) else AerixSurface.panel.copy(alpha = 0.72f),
        border = BorderStroke(AerixSpacing.hairline, if (selected) accent.copy(alpha = 0.45f) else AerixSurface.borderSoft)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AerixImageWell(instance.icon, R.drawable.aerix_ic_videogame_asset_outlined, 32.dp, radius = AerixRadii.tiny)
            Spacer(Modifier.width(AerixSpacing.sm))
            Column(Modifier.weight(1f).clickable(onClick = onSelect).padding(vertical = 4.dp)) {
                Text(instance.name, color = AerixSurface.textPrimary, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(instance.versionId, color = AerixSurface.textSecondary, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("PLAY", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp,
                modifier = Modifier.clickable(onClick = onPlay).padding(8.dp))
        }
    }
}
