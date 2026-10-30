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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import net.kdt.pojavlaunch.aerix.client.LiquidCrystalPalette
import net.kdt.pojavlaunch.instances.Instances

/**
 * Minimal Launcher `Aerix Client` Sidebar Screen (Locked Crimson Red & Dark Gray Theme):
 * Shows ONLY `Aerix Client` at the top and client version selection + download options below it.
 */
@Composable
fun AerixClientScreen(activity: FragmentActivity) {
    LaunchedEffect(Unit) {
        AerixClientStore.init(activity)
    }

    var refreshTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidCrystalPalette.DarkOuter.copy(alpha = 0.92f))
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Header: ONLY "Aerix Client"
        Text(
            text = "Aerix Client",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LiquidCrystalPalette.CrimsonPrimary)
        )
        Spacer(Modifier.height(16.dp))

        // Active download progress banner (shown only while a client version is downloading)
        if (AerixClientInstaller.isInstalling) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LiquidCrystalPalette.DarkPanel)
                    .border(1.dp, LiquidCrystalPalette.CrimsonPrimary, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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
                        color = LiquidCrystalPalette.CrimsonGlow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { AerixClientInstaller.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = LiquidCrystalPalette.CrimsonPrimary,
                    trackColor = LiquidCrystalPalette.DarkTile
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        AerixClientInstaller.lastError?.takeIf { it.isNotBlank() && !AerixClientInstaller.isInstalling }?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2A1419))
                    .border(1.dp, LiquidCrystalPalette.CrimsonGlow, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(text = err, color = Color.White, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
        }

        // Client Versions Selection + Download List (Nothing More)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AerixClientInstaller.availableVersions.forEach { spec ->
                val installed = remember(refreshTick, AerixClientInstaller.isInstalling, spec.mcVersion) {
                    AerixClientInstaller.findInstalledClientInstance(spec.mcVersion)
                }
                val isSelected = AerixClientStore.selectedClientVersion == spec.mcVersion
                val rowShape = RoundedCornerShape(12.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(
                            if (isSelected) LiquidCrystalPalette.CrimsonTintFill
                            else LiquidCrystalPalette.DarkPanel,
                            rowShape
                        )
                        .border(
                            width = if (isSelected) 1.4.dp else 1.dp,
                            color = if (isSelected) LiquidCrystalPalette.CrimsonPrimary else LiquidCrystalPalette.DarkTileBorder,
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
                        .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(LiquidCrystalPalette.CrimsonPrimary.copy(alpha = 0.25f))
                                        .border(0.8.dp, LiquidCrystalPalette.CrimsonPrimary, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
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
                            text = "Aerix Client ${spec.mcVersion}",
                            color = LiquidCrystalPalette.TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val busy = AerixClientInstaller.isInstalling
                        val buttonShape = RoundedCornerShape(9.dp)

                        // Download / Re-download Button
                        Box(
                            modifier = Modifier
                                .clip(buttonShape)
                                .background(
                                    if (installed == null) {
                                        Brush.horizontalGradient(
                                            listOf(
                                                LiquidCrystalPalette.CrimsonPrimary,
                                                LiquidCrystalPalette.CrimsonDeep
                                            )
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            listOf(
                                                LiquidCrystalPalette.DarkTile,
                                                LiquidCrystalPalette.DarkTile
                                            )
                                        )
                                    },
                                    buttonShape
                                )
                                .border(
                                    1.dp,
                                    if (installed == null) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.DarkTileBorder,
                                    buttonShape
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
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    busy && AerixClientInstaller.installingVersion == spec.mcVersion -> "Downloading..."
                                    installed != null -> "Re-download"
                                    else -> "Download"
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Launch Button if already downloaded
                        if (installed != null) {
                            Box(
                                modifier = Modifier
                                    .clip(buttonShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                LiquidCrystalPalette.CrimsonPrimary,
                                                LiquidCrystalPalette.CrimsonDeep
                                            )
                                        ),
                                        buttonShape
                                    )
                                    .border(1.dp, LiquidCrystalPalette.CrimsonGlow, buttonShape)
                                    .clickable(enabled = !busy) {
                                        AerixClientStore.selectedClientVersion = spec.mcVersion
                                        AerixClientStore.persist()
                                        Instances.setSelectedInstance(installed)
                                        PojavInstanceBridge.reload()
                                        PojavLaunchBridge.launchGame()
                                    }
                                    .padding(horizontal = 18.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Launch",
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
