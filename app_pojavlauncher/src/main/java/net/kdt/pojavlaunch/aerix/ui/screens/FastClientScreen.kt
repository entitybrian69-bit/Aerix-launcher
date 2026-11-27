package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.components.GlassPanel
import net.kdt.pojavlaunch.aerix.components.PageHeader
import net.kdt.pojavlaunch.aerix.fastclient.FastClientBuild
import net.kdt.pojavlaunch.aerix.fastclient.FastClientInstaller
import net.kdt.pojavlaunch.aerix.modpacks.AerixModpack
import net.kdt.pojavlaunch.aerix.modpacks.AerixModpackCatalog
import net.kdt.pojavlaunch.aerix.modpacks.AerixModpackInstaller
import net.kdt.pojavlaunch.aerix.modpacks.AerixModpackVersion
import net.kdt.pojavlaunch.aerix.theme.AerixLayoutDefaults
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.instances.Instances

/** The creator's builds can be installed standalone or combined with a matching-version modpack. */
@Composable
fun FastClientScreen(activity: FragmentActivity, onBack: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var searchPacks by remember { mutableStateOf(false) }
    var lockedPackSearchMc by remember { mutableStateOf<String?>(null) }
    var installChoiceFor by remember { mutableStateOf<FastClientBuild?>(null) }
    var combinedInstalling by remember { mutableStateOf(false) }
    var combinedError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { AerixModpackCatalog.load() }

    val instances = remember(refresh, FastClientInstaller.installingVersion, combinedInstalling) {
        runCatching { Instances.loadAllInstances() }.getOrDefault(emptyList())
    }
    val busyAny = FastClientInstaller.installingVersion != null ||
        AerixModpackInstaller.isInstalling || combinedInstalling
    Column(Modifier.fillMaxSize().padding(AerixLayoutDefaults.pagePadding()), verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 560.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(0.9f)) { FastClientTitle() }
                    FastClientThanks(activity, Modifier.weight(2f))
                    ClientOwnerBackButton(onBack)
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    PageHeader(title = "FastClient", subtitle = "By Mxinspire1 · Fabric", trailing = { ClientOwnerBackButton(onBack) })
                    FastClientThanks(activity, Modifier.fillMaxWidth())
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            Text("Install a standalone FastClient instance or pair the matching version with a modpack. The client, Fabric Loader, Fabric API, Lithium and Entity Culling are downloaded during setup, so nothing is bundled in the app; Minecraft files download when you launch.", color = AerixSurface.textSecondary, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                Button(enabled = !busyAny, onClick = {
                    lockedPackSearchMc = null
                    searchPacks = true
                }) { Text("Search modpacks") }
                Text("Pick a pack, then choose whether to install it with FastClient or on its own.", color = AerixSurface.textSecondary, fontSize = 11.sp)
            }
            if (AerixModpackInstaller.isInstalling) {
                GlassPanel(Modifier.fillMaxWidth(), radius = AerixRadii.card) {
                    Text(AerixModpackInstaller.statusHeadline.ifBlank { "Installing modpack…" }, color = Color.White, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { AerixModpackInstaller.progressFraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = AerixSurface.accent)
                }
            }
            if (FastClientInstaller.installingVersion != null) {
                GlassPanel(Modifier.fillMaxWidth(), radius = AerixRadii.card) {
                    Text(FastClientInstaller.status, color = Color.White, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { FastClientInstaller.progress }, modifier = Modifier.fillMaxWidth(), color = AerixSurface.accent)
                }
            }
            (combinedError ?: AerixModpackInstaller.lastError ?: FastClientInstaller.lastError)?.let { error ->
                GlassPanel(Modifier.fillMaxWidth(), radius = AerixRadii.card, color = AerixSurface.errorDarkContainer.copy(alpha = 0.55f)) {
                    Text(error, color = Color.White, fontSize = 12.sp)
                }
            }
            FastClientInstaller.builds.forEach { build ->
                val installed = FastClientInstaller.findInstalled(build, instances)
                GlassPanel(Modifier.fillMaxWidth(), radius = AerixRadii.card) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Minecraft ${build.minecraft}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Fabric · Java ${build.java}" + if (installed != null) " · Installed" else "", color = AerixSurface.textSecondary, fontSize = 12.sp)
                        }
                        if (installed == null) {
                            Button(enabled = !busyAny, onClick = {
                                installChoiceFor = build
                            }) { Text(if (FastClientInstaller.installingVersion == build.minecraft) "Installing…" else "Install") }
                        } else {
                            Button(enabled = !busyAny, onClick = {
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

    installChoiceFor?.let { build ->
        val matchingPacks: List<Pair<AerixModpack, AerixModpackVersion>> = AerixModpackCatalog.packs.mapNotNull { pack ->
            val ver = pack.offeredVersions.firstOrNull { it.mcVersion == build.minecraft }
            if (ver != null) pack to ver else null
        }
        Dialog(onDismissRequest = { if (!busyAny) installChoiceFor = null }) {
            Surface(shape = RoundedCornerShape(22.dp), color = AerixSurface.panel) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Install FastClient ${build.minecraft}?",
                        color = AerixSurface.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Choose whether to install FastClient ${build.minecraft} on its own or " +
                            "together with a matching Minecraft ${build.minecraft} modpack.",
                        color = AerixSurface.textSecondary,
                        fontSize = 12.sp
                    )
                    Button(
                        enabled = !busyAny,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AerixSurface.accent,
                            contentColor = AerixSurface.onAccent
                        ),
                        onClick = {
                            installChoiceFor = null
                            combinedError = null
                            scope.launch {
                                FastClientInstaller.install(activity, build).onSuccess {
                                    Toast.makeText(activity, "FastClient ${build.minecraft} ready", Toast.LENGTH_SHORT).show()
                                }.onFailure { error ->
                                    Toast.makeText(activity, error.message ?: "Installation failed", Toast.LENGTH_LONG).show()
                                }
                                refresh++
                                PojavInstanceBridge.reload()
                            }
                        }
                    ) {
                        Text("Install client only (FastClient ${build.minecraft})")
                    }
                    matchingPacks.forEach { (pack, version) ->
                        Button(
                            enabled = !busyAny,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AerixSurface.accent.copy(alpha = 0.22f),
                                contentColor = AerixSurface.textPrimary
                            ),
                            onClick = {
                                installChoiceFor = null
                                combinedError = null
                                combinedInstalling = true
                                scope.launch {
                                    AerixModpackInstaller.install(activity, pack, version)
                                    val created = AerixModpackInstaller.installedInstance
                                    if (created != null && AerixModpackInstaller.lastError == null) {
                                        FastClientInstaller.installIntoInstance(activity, created, build)
                                            .onSuccess {
                                                Instances.setSelectedInstance(created)
                                                Toast.makeText(
                                                    activity,
                                                    "${pack.name} (${version.mcVersion}) + FastClient ready",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            .onFailure { error ->
                                                combinedError = error.message ?: "Could not add FastClient"
                                                Toast.makeText(activity, combinedError, Toast.LENGTH_LONG).show()
                                            }
                                    } else if (AerixModpackInstaller.lastError != null) {
                                        combinedError = AerixModpackInstaller.lastError
                                    }
                                    combinedInstalling = false
                                    refresh++
                                    PojavInstanceBridge.reload()
                                }
                            }
                        ) {
                            Text("Install with ${pack.name} modpack (${version.mcVersion})")
                        }
                    }
                    Button(
                        enabled = !busyAny,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AerixSurface.accent.copy(alpha = 0.16f),
                            contentColor = AerixSurface.textPrimary
                        ),
                        onClick = {
                            installChoiceFor = null
                            lockedPackSearchMc = build.minecraft
                            searchPacks = true
                        }
                    ) {
                        Text("Install with modpack (search ${build.minecraft})")
                    }
                    TextButton(
                        onClick = { installChoiceFor = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel", color = AerixSurface.textSecondary)
                    }
                }
            }
        }
    }

    if (searchPacks) {
        AerixPackSearchDialog(activity, ClientOwnerPage.MX_INSPIRE, lockedMinecraft = lockedPackSearchMc, onDismiss = {
            searchPacks = false
            lockedPackSearchMc = null
        }) {
            refresh++
            PojavInstanceBridge.reload()
        }
    }
}

@Composable
private fun FastClientTitle() {
    Text("FastClient", color = AerixSurface.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
    Text("By Mxinspire1 · Fabric", color = AerixSurface.textSecondary, fontSize = 12.sp)
}

@Composable
private fun FastClientThanks(activity: FragmentActivity, modifier: Modifier = Modifier) {
    fun openLink(url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(activity, "No app available to open this link", Toast.LENGTH_SHORT).show()
        }
    }
    Column(modifier) {
        Text("Thanks Mx inspire for supporting and giving us permission to use their client",
            color = AerixSurface.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { openLink("https://m.youtube.com/channel/UCjC7cPjoCxQycsw0DITzLQA") }) {
                Text("YouTube", color = AerixSurface.accent, fontSize = 12.sp)
            }
            TextButton(onClick = { openLink("https://discord.gg/Z6aCUNfPJH") }) {
                Text("Discord", color = AerixSurface.accent, fontSize = 12.sp)
            }
        }
    }
}
