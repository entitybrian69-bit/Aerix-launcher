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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.AssetProject
import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.PojavAssetBridge
import java.io.File

/**
 * Minimal Liquid Glass `Aerix Client` Sidebar Tab:
 * Transparent backdrop over the launcher's live wallpaper, showing ONLY `Aerix Client`
 * and Liquid Glass client version download / launch cards below it.
 */
@Composable
fun AerixClientScreen(activity: FragmentActivity, onBack: () -> Unit) {
    LaunchedEffect(Unit) {
        AerixClientStore.init(activity)
    }

    var refreshTick by remember { mutableIntStateOf(0) }
    var modpackSearchOpen by remember { mutableStateOf(false) }
    var preparingLaunchVer by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val accent = AerixSurface.accent

    AerixPage(
        title = "Aerix Client",
        subtitle = null,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Modpacks used to be reachable only from the Discover tab, which does not know
                // that the player is here to set up a client: searching for a pack and installing it
                // with or without Aerix Client belongs on this page.
                Text(
                    text = "Search modpacks",
                    modifier = Modifier
                        .clip(RoundedCornerShape(AerixRadii.card))
                        .background(accent.copy(alpha = 0.18f))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(AerixRadii.card))
                        .clickable { modpackSearchOpen = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                ClientOwnerBackButton(onBack)
            }
        }
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

        if (modpackSearchOpen) {
            AerixModpackSearchDialog(activity, onDismiss = { modpackSearchOpen = false }) {
                // A pack creates its own instance, so the version list below is stale.
                refreshTick++
            }
        }

        // Load the instance list once, rather than scanning disk for every version card.
        val installedClients = remember(refreshTick, AerixClientInstaller.isInstalling) {
            runCatching { Instances.loadAllInstances() }.getOrDefault(emptyList())
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
                    AerixClientInstaller.findInstalledClientInstance(spec.mcVersion, installedClients)
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

                        // "Update my mods" - the only path that writes jars of ours into an
                        // instance folder, and it needs a tap. The answer is shown, not swallowed,
                        // so a Modrinth that did not answer reads as "unavailable" instead of success.
                        if (installed != null) {
                            Box(
                                modifier = Modifier
                                    .liquidGlass(
                                        shape = pillShape,
                                        tint = AerixSurface.glassTint,
                                        strength = 0.9f
                                    )
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.14f),
                                                AerixSurface.glassBlue.copy(alpha = 0.22f)
                                            )
                                        ),
                                        shape = pillShape
                                    )
                                    .clickable(enabled = !busy && !AerixClientInstaller.isUpdatingMods) {
                                        scope.launch {
                                            val summary = AerixClientInstaller.updateOwnMods(activity, installed)
                                            refreshTick++
                                            summary.onSuccess { text ->
                                                Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
                                            }.onFailure { err ->
                                                Toast.makeText(
                                                    activity,
                                                    err.message ?: "The mod update failed",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (AerixClientInstaller.isUpdatingMods)
                                        AerixClientInstaller.modUpdateState.ifBlank { "Updating mods..." }
                                    else "Update my mods",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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


/**
 * Modpack search for the Aerix Client page, with the choice the players asked for: a pack installed
 * on its own, or the same pack with Aerix Client put into it.
 *
 * "With client" adds only what the launcher itself owns - the Aerix Client mod and the Fabric API it
 * needs - and never the third-party optimization mods the client bundle ships with. A modpack decides
 * its own mod list, and overwriting that with ours is how packs break; [AerixClientInstaller.updateOwnMods]
 * is the routine that already keeps that line for existing instances, so it is used here unchanged.
 */
@Composable
private fun AerixModpackSearchDialog(
    activity: FragmentActivity,
    onDismiss: () -> Unit,
    onInstalled: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var results by remember { mutableStateOf<List<AssetProject>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Search Modrinth and CurseForge for a modpack.") }
    var pending by remember { mutableStateOf<AssetProject?>(null) }
    var installing by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (installing == null) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF20B141B))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Modpacks", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FFFFFF))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.text.isBlank()) Text("Search packs...", color = Color(0x88FFFFFF), fontSize = 13.sp)
                            inner()
                        }
                    }
                )
                Text(
                    text = if (busy) "Searching..." else "Search",
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (busy) Color(0x33FFFFFF) else AerixSurface.accent.copy(alpha = 0.25f))
                        .clickable(enabled = !busy) {
                            val term = query.text.trim()
                            if (term.isEmpty()) { status = "Type something to search for."; return@clickable }
                            busy = true
                            scope.launch {
                                val page = runCatching {
                                    PojavAssetBridge.search(
                                        context, AerixPreferences.assetPlatformSource, AssetType.MODPACK,
                                        term, null, null, 0
                                    )
                                }.getOrNull()
                                results = page?.projects.orEmpty()
                                busy = false
                                status = when {
                                    page == null -> "The search could not be reached."
                                    results.isNullOrEmpty() -> "No pack matches \"$term\" on this source."
                                    else -> "${results?.size} pack(s) for \"$term\"."
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(status, color = Color(0xAAFFFFFF), fontSize = 11.sp, maxLines = 2)

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(results.orEmpty().take(40)) { project ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x1AFFFFFF))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(project.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${project.author} · ${project.downloads} downloads",
                                color = Color(0x99FFFFFF), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            text = if (installing == project.id) "Working..." else "Install",
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(AerixSurface.accent.copy(alpha = 0.22f))
                                .clickable(enabled = installing == null) { pending = project }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "Close",
                modifier = Modifier.align(androidx.compose.ui.Alignment.End)
                    .clickable(enabled = installing == null) { onDismiss() }
                    .padding(vertical = 4.dp),
                color = Color(0xAAFFFFFF),
                fontSize = 12.sp
            )
        }
    }

    val project = pending
    if (project != null) {
        AlertDialog(
            onDismissRequest = { if (installing == null) pending = null },
            title = { Text(project.title) },
            text = {
                Text("Install \"${project.title}\"?\n\n" +
                    "With client adds the Aerix Client mod and the Fabric API only - the pack keeps " +
                    "every mod it ships with, and no optimization mods are put in beside them.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    runModpackInstall(context, scope, activity, project, withClient = true) { message ->
                        status = message
                        installing = null
                        onInstalled()
                    }.also { installing = project.id }
                }) { Text("Install with client") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        pending = null
                        runModpackInstall(context, scope, activity, project, withClient = false) { message ->
                            status = message
                            installing = null
                            onInstalled()
                        }.also { installing = project.id }
                    }) { Text("Install without client") }
                    TextButton(onClick = { pending = null }) { Text("Cancel") }
                }
            }
        )
    }
}

/**
 * Start the install and report back one line, either way.
 *
 * The pack is installed through the same path the Discover page uses - which creates the instance the
 * pack describes - and only afterwards is the client added to the instance that appeared. The before
 * and after instance names are diffed rather than guessed, because a pack chooses its own instance
 * name and a wrong guess would add the client mod to some unrelated install.
 */
private fun runModpackInstall(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    activity: FragmentActivity,
    project: AssetProject,
    withClient: Boolean,
    report: (String) -> Unit,
) {
    scope.launch {
        val before = runCatching { Instances.loadAllInstances().mapNotNull { it.name }.toSet() }
            .getOrDefault(emptySet())
        val files = runCatching { PojavAssetBridge.files(context, project) }.getOrNull()
        val file = files?.firstOrNull()
        if (file == null) {
            report("This pack has no downloadable file on ${project.platform}.")
            return@launch
        }
        val ok = runCatching { PojavAssetBridge.install(context, project, file) }.getOrDefault(false)
        if (!ok) {
            report("The pack could not be installed.")
            return@launch
        }
        PojavInstanceBridge.reload()
        val created = runCatching { Instances.loadAllInstances() }.getOrDefault(emptyList())
            .firstOrNull { instance -> instance.name != null && instance.name !in before }
        if (!withClient) {
            report("Installed \"${project.title}\" as its own instance.")
            if (created != null) Instances.setSelectedInstance(created)
            return@launch
        }
        if (created == null) {
            report("Installed, but the new instance could not be found - open it and use \"Update my mods\".")
            return@launch
        }
        val summary = AerixClientInstaller.updateOwnMods(activity, created).getOrElse { error ->
            report("Pack installed; the client could not be added (${error.message}).")
            return@launch
        }
        Instances.setSelectedInstance(created)
        report("Pack + client ready · $summary")
    }
}
