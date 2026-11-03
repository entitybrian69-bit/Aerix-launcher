package net.kdt.pojavlaunch.aerix.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.components.GlassPanel
import net.kdt.pojavlaunch.aerix.fastclient.FastClientInstaller
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.instances.Instances

/** The creator's builds are separate profiles and never receive Aerix Client's own mod stack. */
@Composable
fun FastClientScreen(activity: FragmentActivity, onBack: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val instances = remember(refresh, FastClientInstaller.installingVersion) {
        runCatching { Instances.loadAllInstances() }.getOrDefault(emptyList())
    }
    AerixPage(title = "FastClient", subtitle = "By Mxinspire1 · Fabric", trailing = { ClientOwnerBackButton(onBack) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            Text("Install a separate FastClient instance. Fabric Loader and Fabric API are downloaded during setup; Minecraft files download when you launch.", color = AerixSurface.textSecondary, fontSize = 12.sp)
            if (FastClientInstaller.installingVersion != null) {
                GlassPanel(Modifier.fillMaxWidth(), radius = AerixRadii.card) {
                    Text(FastClientInstaller.status, color = Color.White, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { FastClientInstaller.progress }, modifier = Modifier.fillMaxWidth(), color = AerixSurface.accent)
                }
            }
            FastClientInstaller.lastError?.let { error ->
                GlassPanel(Modifier.fillMaxWidth(), radius = AerixRadii.card, color = AerixSurface.errorDarkContainer.copy(alpha = 0.55f)) {
                    Text(error, color = Color.White, fontSize = 12.sp)
                }
            }
            FastClientInstaller.builds.forEach { build ->
                val installed = FastClientInstaller.findInstalled(build, instances)
                val busy = FastClientInstaller.installingVersion != null
                GlassPanel(Modifier.fillMaxWidth(), radius = AerixRadii.card) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Minecraft ${build.minecraft}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Fabric · Java ${build.java}" + if (installed != null) " · Installed" else "", color = AerixSurface.textSecondary, fontSize = 12.sp)
                        }
                        if (installed == null) {
                            Button(enabled = !busy, onClick = {
                                scope.launch {
                                    FastClientInstaller.install(activity, build).onSuccess {
                                        Toast.makeText(activity, "FastClient ${build.minecraft} ready", Toast.LENGTH_SHORT).show()
                                    }.onFailure { error ->
                                        Toast.makeText(activity, error.message ?: "Installation failed", Toast.LENGTH_LONG).show()
                                    }
                                    refresh++
                                    PojavInstanceBridge.reload()
                                }
                            }) { Text(if (FastClientInstaller.installingVersion == build.minecraft) "Installing…" else "Install") }
                        } else {
                            Button(enabled = !busy, onClick = {
                                Instances.setSelectedInstance(installed)
                                PojavInstanceBridge.reload()
                                PojavLaunchBridge.play(activity)
                            }) { Text("Launch") }
                        }
                    }
                }
            }
            Text("Android compatibility and performance depend on the device and renderer. FastClient is provided by its creator.", color = AerixSurface.textSecondary, fontSize = 12.sp)
        }
    }
}
