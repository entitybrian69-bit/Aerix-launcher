package net.kdt.pojavlaunch.aerix.ui.screens

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.tools.*
import net.kdt.pojavlaunch.game.renderer.GameRenderer
import net.kdt.pojavlaunch.game.renderer.def.Renderers
import net.kdt.pojavlaunch.instances.Instances
import java.io.File
import java.util.UUID

private enum class StudioTool(val title: String, val summary: String) {
    WORLDS("World library", "Real world icons, last-played dates, sizes, backups and quick launch."),
    SERVERS("Server profiles", "Connect with the right instance, controls, renderer and memory settings."),
    CONTROLS("Touch-control studio", "Undo, redo, mirroring, grid snapping, guides and layout sharing."),
    RENDERERS("Renderer test bench", "Isolated graphics probes with pixel checks and synchronized-clear timings."),
    CAPES("Shared capes & companion", "Configure your own cape service and install the optional Fabric 1.20.1 companion.")
}

@Composable
fun StudioScreen(activity: FragmentActivity) {
    var tool by rememberSaveable { mutableStateOf(StudioTool.entries.firstOrNull { it.name == net.kdt.pojavlaunch.aerix.ui.AerixNav.studioTool }) }
    BackHandler(enabled = tool != null) { tool = null }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tool != null) OutlinedButton(onClick = { tool = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
            Text(tool?.title ?: "Aerix Studio", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = AerixSurface.textPrimary)
        }
        when (tool) {
            null -> LazyVerticalGrid(GridCells.Adaptive(280.dp), Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(StudioTool.entries) { entry ->
                    Surface(onClick = { tool = entry }, shape = RoundedCornerShape(20.dp), color = AerixSurface.panelRaised,
                        border = BorderStroke(1.dp, AerixSurface.borderSoft)) {
                        Column(Modifier.fillMaxWidth().heightIn(min = 170.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(entry.title, color = AerixSurface.textPrimary, style = MaterialTheme.typography.titleLarge)
                            Text(entry.summary, color = AerixSurface.textSecondary)
                        }
                    }
                }
            }
            StudioTool.WORLDS -> WorldLibrary(activity)
            StudioTool.SERVERS -> ServerProfiles(activity)
            StudioTool.CONTROLS -> ControlStudio(activity)
            StudioTool.RENDERERS -> RendererBench(activity)
            StudioTool.CAPES -> SharedCapes(activity)
        }
    }
}

@Composable
private fun StudioCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = AerixSurface.panelRaised, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, AerixSurface.borderSoft)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioActions(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun StudioMessage(message: String?) {
    if (message != null) Text(message, color = AerixSurface.textSecondary)
}

@Composable
private fun WorldLibrary(activity: FragmentActivity) {
    var worlds by remember { mutableStateOf(emptyList<WorldCard>()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var export by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val source = export
        export = null
        if (uri != null && source != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val output = activity.contentResolver.openOutputStream(uri) ?: error("Unable to open destination")
                    output.use { stream -> source.inputStream().use { it.copyTo(stream) } }
                }
                message = "Backup exported"
            } catch (e: Exception) { if (e is CancellationException) throw e; message = e.message }
            finally { busy = false }
        }
    }
    LaunchedEffect(revision) {
        busy = true
        try { worlds = AerixToolsBridge.worlds(activity) }
        catch (e: Exception) { if (e is CancellationException) throw e; message = e.message }
        finally { busy = false }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text("Backups run only while Minecraft is closed. Five backups per world are retained. Direct world launch needs Minecraft 1.20+; older versions open the game menu.", color = AerixSurface.textSecondary)
            StudioActions { OutlinedButton(onClick = { revision++ }, enabled = !busy) { Text("Refresh worlds") } }
            StudioMessage(message)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (worlds.isEmpty() && !busy) item { StudioMessage("No worlds found. Create a world in one of your instances first.") }
        items(worlds, key = { it.directory.path }) { world ->
            StudioCard(world.name) {
                world.icon?.let { Image(it, world.name, Modifier.size(72.dp)) }
                Text("${world.instanceName} · ${world.version ?: "Unknown game version"}", color = AerixSurface.textSecondary)
                Text("${(if (world.bytes < 0) "Size unavailable" else Formatter.formatFileSize(activity, world.bytes))} · " + if (world.lastPlayed > 0) "Played ${DateUtils.getRelativeTimeSpanString(world.lastPlayed)}" else "Last played unavailable", color = AerixSurface.textSecondary)
                Text(world.lastBackup?.let { "Backed up ${DateUtils.getRelativeTimeSpanString(it.lastModified())}" } ?: "No Aerix backup yet", color = AerixSurface.textSecondary)
                StudioActions {
                    Button(onClick = {
                        scope.launch {
                            val instance = AerixToolsBridge.instance(world.instanceId)
                            if (instance == null) { message = "Instance no longer exists"; return@launch }
                            Instances.setSelectedInstance(instance)
                            PojavInstanceBridge.reload()
                            PojavLaunchBridge.play(activity, worldName = world.directory.name)
                        }
                    }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Play world") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            busy = true
                            try { AerixToolsBridge.backup(activity, world); message = "Backup complete"; revision++ }
                            catch (e: Exception) { if (e is CancellationException) throw e; message = e.message }
                            finally { busy = false }
                        }
                    }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back up") }
                    OutlinedButton(onClick = { export = world.lastBackup; saveZip.launch("${world.directory.name}-backup.zip") },
                        enabled = !busy && world.lastBackup != null, modifier = Modifier.heightIn(min = 48.dp)) { Text("Export backup") }
                    OutlinedButton(onClick = { Tools.openPath(activity, world.directory, false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open folder") }
                }
            }
        }
    }
}

@Composable
private fun ServerProfiles(activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(emptyList<ServerProfile>()) }
    var edit by remember { mutableStateOf(false) }
    var id by remember { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var instanceId by rememberSaveable { mutableStateOf("") }
    var chooseInstance by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val instances = PojavInstanceBridge.instances
    LaunchedEffect(Unit) {
        try { profiles = AerixToolsBridge.servers(activity) }
        catch (e: Exception) { if (e is CancellationException) throw e; message = "Unable to read server profiles: ${e.message}" }
    }
    BackHandler(enabled = edit) { edit = false }
    if (chooseInstance) AlertDialog(onDismissRequest = { chooseInstance = false }, title = { Text("Choose instance") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(instances) { instance ->
                OutlinedButton(onClick = { instanceId = instance.source.directoryId; chooseInstance = false }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(instance.name) }
            }
        }
    }, confirmButton = { TextButton(onClick = { chooseInstance = false }) { Text("Close") } })
    LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { StudioMessage(message) }
        if (edit) {
            item { StudioCard("Server profile") {
                OutlinedTextField(name, { name = it }, label = { Text("Profile name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Server address · host:port") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedButton(onClick = { chooseInstance = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(instances.firstOrNull { it.source.directoryId == instanceId }?.name ?: "Choose instance")
                }
                OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text("Required mods, packs and server notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Text("Uses this instance's saved renderer, memory and touch controls. Required mods/packs are notes; they are not installed automatically.", color = AerixSurface.textSecondary)
                StudioActions {
                    Button(onClick = { scope.launch {
                        busy = true
                        try {
                            require(name.isNotBlank() && name.length <= 80) { "Enter a name up to 80 characters" }
                            require(instances.any { it.source.directoryId == instanceId }) { "Choose an existing instance" }
                            val profile = ServerProfile(id ?: UUID.randomUUID().toString(), name.trim(), AerixToolsBridge.validateAddress(address), instanceId, notes)
                            val next = profiles.filterNot { it.id == profile.id } + profile
                            AerixToolsBridge.saveServers(activity, next)
                            profiles = next; edit = false; message = "Profile saved"
                        } catch (e: Exception) { if (e is CancellationException) throw e; message = e.message }
                        finally { busy = false }
                    } }, enabled = !busy) { Text("Save profile") }
                    OutlinedButton(onClick = { edit = false }) { Text("Cancel") }
                }
            } }
        } else {
            item { Button(onClick = { id = null; name = ""; address = ""; notes = ""; instanceId = PojavInstanceBridge.selected?.source?.directoryId.orEmpty(); edit = true }, Modifier.heightIn(min = 48.dp)) { Text("Add server profile") } }
            items(profiles, key = { it.id }) { profile ->
                StudioCard(profile.name) {
                    Text(profile.address, color = AerixSurface.textPrimary)
                    Text(instances.firstOrNull { it.source.directoryId == profile.instanceId }?.name ?: "Instance missing", color = AerixSurface.textSecondary)
                    if (profile.notes.isNotBlank()) Text(profile.notes, color = AerixSurface.textSecondary)
                    StudioActions {
                        Button(onClick = { scope.launch {
                            try {
                                val instance = AerixToolsBridge.instance(profile.instanceId) ?: error("Instance no longer exists")
                                Instances.setSelectedInstance(instance); PojavInstanceBridge.reload()
                                PojavLaunchBridge.play(activity, serverAddress = AerixToolsBridge.validateAddress(profile.address))
                            } catch (e: Exception) { if (e is CancellationException) throw e; message = e.message }
                        } }, Modifier.heightIn(min = 48.dp)) { Text("Connect") }
                        OutlinedButton(onClick = { id = profile.id; name = profile.name; address = profile.address; notes = profile.notes; instanceId = profile.instanceId; edit = true }) { Text("Edit") }
                        OutlinedButton(onClick = { instances.firstOrNull { it.source.directoryId == profile.instanceId }?.let { PojavInstanceBridge.edit(activity, it, "settings") } }) { Text("Controls & graphics") }
                        OutlinedButton(onClick = { instances.firstOrNull { it.source.directoryId == profile.instanceId }?.let { PojavInstanceBridge.edit(activity, it, "packs") } }) { Text("Resource packs") }
                        OutlinedButton(onClick = { instances.firstOrNull { it.source.directoryId == profile.instanceId }?.let { PojavInstanceBridge.edit(activity, it, "mods") } }) { Text("Instance mods") }
                        OutlinedButton(onClick = { scope.launch {
                            try { val next = profiles.filterNot { it.id == profile.id }; AerixToolsBridge.saveServers(activity, next); profiles = next }
                            catch (e: Exception) { if (e is CancellationException) throw e; message = e.message }
                        } }) { Text("Delete profile") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlStudio(activity: FragmentActivity) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { StudioCard("A full-screen editing canvas") {
            Text("Open the control editor, then open its drawer → Studio tools. Undo/redo covers layout geometry and button properties; imported image assets are not copied into history.", color = AerixSurface.textSecondary)
            Text("Grid snapping adds 24 dp alignment guides. Mirror flips the layout for left-handed play. Menu / gameplay presets assign selected buttons to gameplay, menus, or both.", color = AerixSurface.textSecondary)
            Text("Use Load, Save and Share in the editor drawer to import/export layouts. Up to 20 edits are retained while the editor is open.", color = AerixSurface.textSecondary)
            Button(onClick = { PojavNavigator.openCustomControls(activity) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open touch-control studio") }
        } }
    }
}

@Composable
private fun RendererBench(activity: FragmentActivity) {
    val prefs = remember { activity.getSharedPreferences("aerix-renderer-probes", 0) }
    val options = remember {
        listOf(Renderers.LTW_RENDERER, Renderers.GL4ES_RENDERER, Renderers.MOBILEGLUES_RENDERER, Renderers.NGGL4ES_RENDERER)
            .mapNotNull { tag -> GameRenderer.getKnownRenderer(tag)?.let { tag to it } }
            .filter { File(activity.applicationInfo.nativeLibraryDir, it.second.library()).isFile }
    }
    var results by remember { mutableStateOf(options.associate { it.first to prefs.getString(it.first, "Not tested").orEmpty() }) }
    var running by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Experimental GLES-wrapper-only off-screen 256 × 256 probe. Mesa/Vulkan renderers are not tested. Checks EGL setup, pixel correctness and 120 synchronized clears after 10 warm-up clears. Timings are NOT Minecraft FPS, shader compatibility, battery usage, or a renderer recommendation. Close Minecraft first. Driver crashes are isolated from the launcher.", color = AerixSurface.textSecondary) }
        items(options, key = { it.first }) { (tag, renderer) ->
            StudioCard(renderer.name()) {
                val raw = results[tag].orEmpty()
                val lines = raw.lines()
                Text(if (lines.firstOrNull() == "PASS" && lines.size >= 7) {
                    "Pixel check passed\n${lines[1]}\n${lines[2]}\n${lines[3]}\nAverage clear: ${lines[4]} ms · p95: ${lines[5]} ms\n${lines[6]} measured clears"
                } else raw.removePrefix("ERROR\n"), color = AerixSurface.textSecondary)
                Button(onClick = { scope.launch {
                    running = tag
                    try {
                        val result = RendererProbe.run(activity.applicationContext, tag)
                        results = results + (tag to result)
                        prefs.edit().putString(tag, result).apply()
                    } catch (e: Exception) {
                        if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                        results = results + (tag to "ERROR\n${e.message ?: "Probe failed"}")
                    } finally { running = null }
                } }, enabled = running == null, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (running == tag) "Testing…" else "Run graphics probe") }
            }
        }
    }
}

@Composable
private fun SharedCapes(activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    val current = PojavAccountBridge.current
    val selected = PojavInstanceBridge.selected
    var endpoint by rememberSaveable { mutableStateOf(AerixToolsBridge.capeEndpoint(activity)) }
    var key by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val capePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImage = uri }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { StudioCard("Your own shared cape service") {
            Text("Not hosted by Aerix or Ely.by. Configure an HTTPS deployment of services/capes first. Other players need the companion mod and the same service URL; vanilla clients will not see these capes.", color = AerixSurface.textSecondary)
            OutlinedTextField(endpoint, { endpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("HTTPS cape service URL") }, singleLine = true)
            Button(onClick = { runCatching { AerixToolsBridge.saveCapeEndpoint(activity, endpoint) }.onSuccess { message = "Endpoint saved. Install/update the companion to apply it to the selected instance." }.onFailure { message = it.message } }, enabled = !busy) { Text("Save endpoint") }
        } }
        item { StudioCard("Fabric 1.20.1 companion") {
            Text("Selected instance: ${selected?.name ?: "None"}. Includes shared capes and title-screen account switching without restarting Minecraft. Install only while Minecraft is closed. Other versions/loaders are not supported yet.", color = AerixSurface.textSecondary)
            Button(onClick = { scope.launch {
                busy = true
                try {
                    val id = selected?.source?.directoryId ?: error("Select a Fabric 1.20.1 instance in the library first")
                    AerixToolsBridge.installCompanion(activity, id)
                    message = "Companion installed. Launch the instance; disconnect to the title screen before using the game drawer's Switch account option."
                } catch (e: Exception) { if (e is CancellationException) throw e; message = e.message }
                finally { busy = false }
            } }, enabled = !busy && selected != null, modifier = Modifier.heightIn(min = 48.dp)) { Text("Install / update companion") }
        } }
        item { StudioCard("Upload a cape") {
            Text("Account: ${current?.username ?: "Select an account first"}. Use the upload key assigned to this account's UUID by your service administrator. Never enter a Minecraft password or access token here. Upload keys are not saved.", color = AerixSurface.textSecondary)
            OutlinedTextField(key, { key = it }, label = { Text("Cape service upload key") }, modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
            OutlinedButton(onClick = { capePicker.launch(arrayOf("image/png")) }, enabled = !busy) { Text(if (pendingImage == null) "Choose 64 × 32 PNG" else "Choose another PNG") }
            Button(onClick = { scope.launch {
                busy = true
                try {
                    val account = current ?: error("Select an account first")
                    val image = pendingImage ?: error("Choose a cape PNG")
                    val raw = account.displayUuid.replace("-", "")
                    val uuid = raw.replace(Regex("(.{8})(.{4})(.{4})(.{4})(.{12})"), "$1-$2-$3-$4-$5")
                    AerixToolsBridge.uploadCape(activity, uuid, key, image)
                    key = ""; pendingImage = null
                    message = "Uploaded. Compatible clients using your cape service can see it in worlds and servers; allow up to one minute for refresh."
                } catch (e: Exception) { if (e is CancellationException) throw e; message = e.message }
                finally { busy = false }
            } }, enabled = !busy && current != null && pendingImage != null && key.isNotBlank()) { Text("Upload to configured service") }
        } }
        item { StudioMessage(message); if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
}

@Composable
internal fun HomeWorldCards(activity: FragmentActivity) {
    var worlds by remember { mutableStateOf(emptyList<WorldCard>()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val instances = PojavInstanceBridge.instances
    val scope = rememberCoroutineScope()
    LaunchedEffect(instances, revision) {
        try { worlds = AerixToolsBridge.worlds(activity).take(3) }
        catch (e: Exception) { if (e is CancellationException) throw e; message = e.message }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your worlds", style = MaterialTheme.typography.titleLarge, color = AerixSurface.textPrimary)
        worlds.forEach { world ->
            StudioCard(world.name) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    world.icon?.let { Image(it, null, Modifier.size(64.dp)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(world.instanceName, color = AerixSurface.textSecondary)
                        Text("${(if (world.bytes < 0) "Size unavailable" else Formatter.formatFileSize(activity, world.bytes))} · " + if (world.lastPlayed > 0) DateUtils.getRelativeTimeSpanString(world.lastPlayed) else "Last played unavailable", color = AerixSurface.textSecondary)
                        Text(if (world.lastBackup == null) "No Aerix backup yet" else "Backup ${DateUtils.getRelativeTimeSpanString(world.lastBackup.lastModified())}", color = AerixSurface.textSecondary)
                    }
                }
                StudioActions {
                    Button(onClick = { scope.launch {
                        val instance = AerixToolsBridge.instance(world.instanceId) ?: return@launch
                        Instances.setSelectedInstance(instance); PojavInstanceBridge.reload()
                        PojavLaunchBridge.play(activity, worldName = world.directory.name)
                    } }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Play world") }
                    OutlinedButton(onClick = { scope.launch {
                        busy = true
                        try { AerixToolsBridge.backup(activity, world); message = "Backup complete"; revision++ }
                        catch (e: Exception) { if (e is CancellationException) throw e; message = e.message }
                        finally { busy = false }
                    } }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back up") }
                    OutlinedButton(onClick = { Tools.openPath(activity, world.directory, false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open folder") }
                }
            }
        }
        StudioMessage(message)
        OutlinedButton(onClick = {
            net.kdt.pojavlaunch.aerix.ui.AerixNav.studioTool = "WORLDS"
            net.kdt.pojavlaunch.aerix.ui.AerixNav.navigate(net.kdt.pojavlaunch.aerix.ui.LauncherSection.STUDIO)
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open world library & backups") }
    }
}
