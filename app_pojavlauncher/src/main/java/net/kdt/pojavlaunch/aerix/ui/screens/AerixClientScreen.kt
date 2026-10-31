package net.kdt.pojavlaunch.aerix.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.client.AerixClientInstaller
import net.kdt.pojavlaunch.aerix.client.AerixClientStore
import net.kdt.pojavlaunch.aerix.components.GlassPanel
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.instances.Instances

/**
 * Minimal Liquid Glass `Aerix Client` Sidebar Tab:
 * Transparent backdrop over the launcher's live wallpaper, showing ONLY `Aerix Client`
 * and Liquid Glass client version download / launch cards below it.
 */
@Composable
fun AerixClientScreen(activity: FragmentActivity) {
    LaunchedEffect(Unit) {
        AerixClientStore.init(activity)
    }

    var refreshTick by remember { mutableIntStateOf(0) }
    var preparingLaunchVer by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val accent = AerixSurface.accent

    AerixPage(
        title = "Aerix Client",
        subtitle = null
    ) {
        // Active download progress banner in Liquid Glass
        if (AerixClientInstaller.isInstalling) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = AerixRadii.card,
                strength = 1.0f,
                color = AerixSurface.panelRaised
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AerixClientInstaller.statusHeadline,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(AerixClientInstaller.progressFraction * 100).toInt()}%",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { AerixClientInstaller.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.16f)
                )
            }
        }

        AerixClientInstaller.lastError?.takeIf { it.isNotBlank() && !AerixClientInstaller.isInstalling }?.let { err ->
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                radius = AerixRadii.card,
                color = AerixSurface.errorDarkContainer.copy(alpha = 0.55f)
            ) {
                Text(text = err, color = Color.White, fontSize = 12.sp)
            }
        }

        // Liquid Glass Version Download & Launch List (Nothing More)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            AerixClientInstaller.availableVersions.forEach { spec ->
                val installed = remember(refreshTick, AerixClientInstaller.isInstalling, spec.mcVersion) {
                    AerixClientInstaller.findInstalledClientInstance(spec.mcVersion)
                }
                val isSelected = AerixClientStore.selectedClientVersion == spec.mcVersion
                val rowShape = RoundedCornerShape(AerixRadii.card)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            shape = rowShape,
                            tint = if (isSelected) accent else AerixSurface.glassTint,
                            strength = if (isSelected) 1.0f else 0.90f
                        )
                        .background(
                            brush = Brush.linearGradient(
                                colors = if (isSelected) {
                                    listOf(
                                        Color.White.copy(alpha = 0.20f),
                                        accent.copy(alpha = 0.14f),
                                        AerixSurface.panelRaised
                                    )
                                } else {
                                    listOf(
                                        Color.White.copy(alpha = 0.12f),
                                        AerixSurface.panel
                                    )
                                }
                            ),
                            shape = rowShape
                        )
                        .clickable {
                            AerixClientStore.selectedClientVersion = spec.mcVersion
                            AerixClientStore.persist()
                            if (installed != null) {
                                Instances.setSelectedInstance(installed)
                                PojavInstanceBridge.reload()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Minecraft ${spec.mcVersion}",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (installed != null) {
                                val badgeShape = RoundedCornerShape(999.dp)
                                Box(
                                    modifier = Modifier
                                        .clip(badgeShape)
                                        .background(accent.copy(alpha = 0.22f))
                                        .border(0.9.dp, accent.copy(alpha = 0.65f), badgeShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "INSTALLED",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = spec.summary,
                            color = Color.White.copy(alpha = 0.76f),
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val busy = AerixClientInstaller.isInstalling || preparingLaunchVer != null
                        val pillShape = RoundedCornerShape(999.dp)

                        // Liquid Glass Download / Re-download Pill
                        Box(
                            modifier = Modifier
                                .liquidGlass(
                                    shape = pillShape,
                                    tint = if (installed == null) accent else AerixSurface.glassTint,
                                    strength = 0.95f
                                )
                                .background(
                                    brush = if (installed == null) {
                                        Brush.horizontalGradient(
                                            listOf(
                                                accent.copy(alpha = 0.42f),
                                                AerixSurface.glassBlue.copy(alpha = 0.30f)
                                            )
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.14f),
                                                Color.White.copy(alpha = 0.07f)
                                            )
                                        )
                                    },
                                    shape = pillShape
                                )
                                .clickable(enabled = !busy) {
                                    AerixClientStore.selectedClientVersion = spec.mcVersion
                                    AerixClientStore.persist()
                                    scope.launch {
                                        val res = AerixClientInstaller.installClientVersion(activity, spec.mcVersion)
                                        refreshTick++
                                        PojavInstanceBridge.reload()
                                        res.onSuccess {
                                            Toast.makeText(
                                                activity,
                                                "Aerix Client ${spec.mcVersion} ready!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }.onFailure { err ->
                                            Toast.makeText(
                                                activity,
                                                err.message ?: "Download failed",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val isThisDownloading =
                                AerixClientInstaller.isInstalling && AerixClientInstaller.installingVersion == spec.mcVersion
                            Text(
                                text = when {
                                    isThisDownloading -> "Downloading..."
                                    installed == null -> "Download"
                                    else -> "Re-download"
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        // Liquid Glass Launch Pill (upgrades existing instance pack + WaveyCapes + FPS before launch!)
                        if (installed != null) {
                            Box(
                                modifier = Modifier
                                    .liquidGlass(
                                        shape = pillShape,
                                        tint = accent,
                                        strength = 1.0f
                                    )
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            listOf(
                                                accent.copy(alpha = 0.55f),
                                                AerixSurface.glassBlue.copy(alpha = 0.38f)
                                            )
                                        ),
                                        shape = pillShape
                                    )
                                    .clickable(enabled = !busy) {
                                        AerixClientStore.selectedClientVersion = spec.mcVersion
                                        AerixClientStore.persist()
                                        scope.launch {
                                            preparingLaunchVer = spec.mcVersion
                                            val sync = AerixClientInstaller.syncExistingInstanceForLaunch(
                                                activity,
                                                installed,
                                                spec.mcVersion
                                            )
                                            sync.onFailure { error ->
                                                Toast.makeText(activity, "Client update failed: ${error.message}", Toast.LENGTH_LONG).show()
                                            }
                                            preparingLaunchVer = null
                                            Instances.setSelectedInstance(installed)
                                            PojavInstanceBridge.reload()
                                            PojavLaunchBridge.play(activity)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (preparingLaunchVer == spec.mcVersion) "Syncing..." else "Launch",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
