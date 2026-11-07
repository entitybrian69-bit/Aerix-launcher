package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.servers.SavedServer
import net.kdt.pojavlaunch.aerix.servers.SavedServers
import net.kdt.pojavlaunch.aerix.servers.ServerAddress
import net.kdt.pojavlaunch.aerix.servers.ServerArtworkCatalog
import net.kdt.pojavlaunch.aerix.servers.ServerArtworkImages
import net.kdt.pojavlaunch.aerix.servers.ServerStatusClient
import net.kdt.pojavlaunch.aerix.servers.SrvLookup
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

private data class ServerResult(val status: ServerStatusClient.Status? = null, val error: Boolean = false)

@Composable
fun ServersScreen(activity: FragmentActivity) {
    val context = LocalContext.current
    val accent = AerixThemeManager.currentAccent()
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(SavedServers.load(context)) }
    var results by remember { mutableStateOf<Map<String, ServerResult>>(emptyMap()) }
    var refreshing by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<SavedServer?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedServer?>(null) }
    var bannerTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var bannerRevisions by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var pickerOpen by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = entries.firstOrNull { it.id == selectedId } ?: entries.firstOrNull()
    val selectedInstance = PojavInstanceBridge.selected
    val instances = PojavInstanceBridge.instances
    val artworkCatalog = remember(context) {
        runCatching { context.assets.open("server_artwork/catalog.json").bufferedReader().use { ServerArtworkCatalog.load(it) } }.getOrNull()
    }

    fun save(list: List<SavedServer>) { SavedServers.save(context, list); entries = list }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = bannerTarget
        bannerTarget = null
        if (uri != null && target != null) scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { SavedServers.importBanner(context, target, uri) } }
            if (outcome.isSuccess && entries.any { it.id == target }) {
                save(entries.map { if (it.id == target) it.copy(banner = true) else it })
                bannerRevisions = bannerRevisions + (target to ((bannerRevisions[target] ?: 0) + 1))
                Toast.makeText(context, "Banner saved", Toast.LENGTH_SHORT).show()
            } else if (outcome.isSuccess) {
                withContext(Dispatchers.IO) { SavedServers.clearBanner(context, target) }
            } else Toast.makeText(context, "Could not load banner: ${outcome.exceptionOrNull()?.message ?: "invalid image"}", Toast.LENGTH_LONG).show()
        }
    }

    val serverKeys = entries.map { "${it.id}:${it.address}" }
    LaunchedEffect(serverKeys, refreshing) {
        results = emptyMap()
        val semaphore = Semaphore(4)
        coroutineScope {
            entries.forEach { entry -> launch {
                semaphore.withPermit {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val parsed = ServerAddress.parse(entry.address) ?: error("Invalid address")
                            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                            val dns = cm.getLinkProperties(cm.activeNetwork)?.dnsServers.orEmpty()
                            val resolver = ServerStatusClient.Resolver { host -> SrvLookup.resolve(host, dns, 1300) }
                            ServerStatusClient.ping(parsed, resolver, 3500)
                        }.fold({ ServerResult(status = it) }, { ServerResult(error = true) })
                    }
                    results = results + (entry.id to result)
                }
            } }
        }
    }

    // Keep the detail pane showing a server from the list, picking the first one after a change.
    LaunchedEffect(entries) {
        if (selectedId == null || selectedId !in entries.map { it.id }) selectedId = entries.firstOrNull()?.id
    }

    AerixPage(title = "Servers", subtitle = "Your Minecraft Java servers") {
        Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
            AerixGhostButton("Refresh", R.drawable.aerix_ic_refresh, { refreshing++ })
            AerixGhostButton("Add", R.drawable.aerix_ic_add, { adding = true })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
            Text("Play using:", color = AerixSurface.textSecondary, fontSize = 11.sp)
            Box {
                TextButton(onClick = { pickerOpen = true }) { Text(selectedInstance?.name ?: "Choose an instance", maxLines = 1) }
                DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                    if (instances.isEmpty()) DropdownMenuItem(text = { Text("Create an instance in Library first") }, onClick = { pickerOpen = false })
                    instances.forEach { instance ->
                        DropdownMenuItem(text = { Text("${instance.name} · ${instance.versionId}", maxLines = 1) }, onClick = {
                            PojavInstanceBridge.select(instance); pickerOpen = false
                        })
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val listWidth = (maxWidth * 0.42f).coerceIn(112.dp, 224.dp)
            Row(Modifier.fillMaxSize()) {
                // LEFT: scrollable list of server names.
                Surface(
                    shape = RoundedCornerShape(AerixRadii.card),
                    color = AerixSurface.panel,
                    modifier = Modifier.width(listWidth).fillMaxHeight()
                ) {
                    if (entries.isEmpty()) {
                        Column(Modifier.padding(AerixSpacing.lg), verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                            Text("Your server list is empty", color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Add a Java server address to see its live status and join with your selected instance.", color = AerixSurface.textSecondary, fontSize = 12.sp)
                            Button(onClick = { adding = true }) { Text("Add a server") }
                        }
                    } else {
                        Column {
                            LazyColumn(
                                Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(AerixSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
                            ) {
                                items(entries, key = { it.id }) { entry ->
                                    ServerListItem(
                                        entry = entry,
                                        result = results[entry.id],
                                        selected = entry.id == selected?.id,
                                        accent = accent,
                                        onClick = { selectedId = entry.id }
                                    )
                                }
                            }
                            Text(
                                "Server artwork, when available, comes from LabyMod server-media; other banners are decorative. Reported versions may not be complete. Saved on this device; Minecraft's own server list is separate.",
                                color = AerixSurface.textMuted, fontSize = 10.sp, modifier = Modifier.padding(AerixSpacing.md)
                            )
                        }
                    }
                }
                VerticalDivider(color = AerixSurface.borderSoft, modifier = Modifier.fillMaxHeight().width(AerixSpacing.hairline))
                // RIGHT: the tapped server's banner, name, address, logo, player count, ping, versions and actions.
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (selected == null) {
                        Surface(shape = RoundedCornerShape(AerixRadii.card), color = AerixSurface.panel, modifier = Modifier.fillMaxWidth().padding(AerixSpacing.lg)) {
                            Column(Modifier.padding(AerixSpacing.xl), verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                                Text("No server selected", color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Tap a server name on the left to see its details here.", color = AerixSurface.textSecondary, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState())) {
                            ServerDetail(
                                entry = selected,
                                result = results[selected.id],
                                accent = accent,
                                bannerRevision = bannerRevisions[selected.id] ?: 0,
                                artwork = remember(selected.address, artworkCatalog) { artworkCatalog?.find(selected.address) },
                                onRefresh = { refreshing++ }, onEdit = { edit = selected }, onDelete = { deleting = selected },
                                onBanner = { bannerTarget = selected.id; bannerPicker.launch("image/*") },
                                onClearBanner = {
                                    SavedServers.clearBanner(context, selected.id)
                                    save(entries.map { if (it.id == selected.id) it.copy(banner = false) else it })
                                },
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Minecraft server address", selected.address))
                                    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                                },
                                onJoin = {
                                    if (selectedInstance == null) Toast.makeText(context, "Select an instance first", Toast.LENGTH_LONG).show()
                                    else PojavLaunchBridge.joinSocialSession(activity = activity, mcVersion = null, loaderName = null, serverAddress = selected.address)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    if (adding || edit != null) {
        ServerEditor(initial = edit, atLimit = entries.size >= 100 && edit == null, onDismiss = { adding = false; edit = null }) { name, address ->
            val item = edit?.copy(name = name, address = address) ?: SavedServer(SavedServers.newId(), name, address)
            val duplicate = entries.any { it.id != item.id && it.address.equals(item.address, ignoreCase = true) }
            if (duplicate) Toast.makeText(context, "That address is already saved", Toast.LENGTH_SHORT).show()
            else {
                save(if (edit == null) entries + item else entries.map { if (it.id == item.id) item else it })
                if (edit == null) selectedId = item.id
                adding = false; edit = null
            }
        }
    }
    deleting?.let { item ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Remove server?") },
            text = { Text("Remove ${item.name} from Aerix's saved list? Your Minecraft worlds and server are not changed.") },
            confirmButton = { TextButton(onClick = {
                save(entries.filterNot { it.id == item.id }); SavedServers.clearBanner(context, item.id); deleting = null
            }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ServerEditor(initial: SavedServer?, atLimit: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var address by remember(initial?.id) { mutableStateOf(initial?.address.orEmpty()) }
    val parsed = remember(address) { ServerAddress.parse(address.trim()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Add server" else "Edit server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it.take(300) }, singleLine = true,
                    label = { Text("Java server IP or hostname") }, placeholder = { Text("play.example.com:25565") },
                    isError = address.isNotBlank() && parsed == null, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth())
                Text(if (atLimit) "You can save up to 100 servers." else "Supports hostnames, IPv4, [IPv6] and optional ports. Java Edition only.", fontSize = 11.sp, color = AerixSurface.textSecondary)
            }
        }, confirmButton = { TextButton(enabled = parsed != null && !atLimit, onClick = {
            if (parsed != null) onSave(name.trim().ifEmpty { parsed.host }.take(80), parsed.canonical())
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ServerListItem(entry: SavedServer, result: ServerResult?, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val status = result?.status
    Surface(
        shape = RoundedCornerShape(AerixRadii.cardSmall),
        color = if (selected) accent.copy(alpha = 0.16f) else AerixSurface.panel,
        border = if (selected) BorderStroke(AerixSpacing.hairline, accent) else null,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = AerixSpacing.xxs)
    ) {
        Column(Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        when {
                            result == null -> accent
                            result.error -> AerixSurface.danger
                            else -> Color(0xFF3ECF8E)
                        }
                    )
                )
                Text(entry.name, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Text(entry.address, color = AerixSurface.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = AerixSpacing.xxs))
            Text(
                when {
                    result == null -> "Checking server…"
                    result.error -> "Unavailable or status disabled"
                    else -> "${status?.online ?: "?"}/${status?.max ?: "?"} players · ${status?.pingMs?.let { "$it ms" } ?: "Ping unavailable"}"
                },
                color = if (result?.error == true) AerixSurface.danger else AerixSurface.textSecondary,
                fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ServerDetail(entry: SavedServer, result: ServerResult?, accent: Color, bannerRevision: Int, artwork: ServerArtworkCatalog.Artwork?, onRefresh: () -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit, onBanner: () -> Unit, onClearBanner: () -> Unit,
    onCopy: () -> Unit, onJoin: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val status = result?.status
    val banner = remember(entry.id, entry.banner, bannerRevision) {
        if (entry.banner) runCatching { BitmapFactory.decodeFile(SavedServers.bannerFile(context, entry.id).absolutePath)?.asImageBitmap() }.getOrNull() else null
    }
    val logo = remember(status?.favicon) { decodeServerLogo(status?.favicon) }
    // The ID is random and persisted with the entry: artwork stays stable across refreshes and edits.
    val automaticBanner = remember(entry.id) { automaticBannerFor(entry.id) }
    val publicArtwork by produceState<ImageBitmap?>(null, artwork?.url, banner == null) {
        value = null
        if (banner == null && artwork != null) value = withContext(Dispatchers.IO) {
            ServerArtworkImages.load(artwork)?.asImageBitmap()
        }
    }
    Surface(shape = RoundedCornerShape(AerixRadii.card), color = AerixSurface.panel, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(132.dp).background(Brush.linearGradient(listOf(accent.copy(alpha = 0.5f), Color(0xFF151820))))) {
                Image(painterResource(automaticBanner), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (banner != null) Image(banner, contentDescription = "${entry.name} custom banner", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else if (publicArtwork != null) Image(publicArtwork!!, contentDescription = "${entry.name} server artwork", contentScale = if (artwork?.banner == true) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC111217)))))
                Text(status?.motd?.takeIf { it.isNotBlank() } ?: entry.address, modifier = Modifier.align(Alignment.BottomStart).padding(AerixSpacing.md),
                    color = Color.White, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.fillMaxWidth().padding(AerixSpacing.md), verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(AerixRadii.compact)).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        if (logo != null) Image(logo, contentDescription = "${entry.name} logo", modifier = Modifier.fillMaxSize())
                        else Icon(painterResource(R.drawable.aerix_ic_sports_esports_outlined), null, tint = accent, modifier = Modifier.size(26.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.name, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.address, color = AerixSurface.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val summary = when {
                            result == null -> "Checking server…"
                            result.error -> "Unavailable or status disabled"
                            else -> "${status?.online ?: "?"}/${status?.max ?: "?"} players · ${status?.pingMs?.let { "$it ms" } ?: "Ping unavailable"}"
                        }
                        Text(summary, color = if (result?.error == true) AerixSurface.danger else AerixSurface.textSecondary, fontSize = 11.sp)
                        if (status != null) Text("Reported versions: ${status.version ?: "Not provided"}", color = AerixSurface.textSecondary, fontSize = 10.sp, maxLines = 2)
                        if (status != null && status.sample.isNotEmpty()) Text("Players: ${status.sample.joinToString()}", color = AerixSurface.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = AerixSpacing.md, end = AerixSpacing.md, bottom = AerixSpacing.md),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onJoin, contentPadding = PaddingValues(horizontal = AerixSpacing.md)) { Text("Join") }
                Spacer(Modifier.width(AerixSpacing.sm))
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                        Text("⋮", color = AerixSurface.textPrimary, fontSize = 23.sp)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Refresh status") }, onClick = { menu = false; onRefresh() })
                        DropdownMenuItem(text = { Text("Copy address") }, onClick = { menu = false; onCopy() })
                        DropdownMenuItem(text = { Text("Edit server") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text(if (entry.banner) "Change banner" else "Choose banner") }, onClick = { menu = false; onBanner() })
                        if (entry.banner) DropdownMenuItem(text = { Text("Remove banner") }, onClick = { menu = false; onClearBanner() })
                        DropdownMenuItem(text = { Text("Remove server") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

private fun automaticBannerFor(id: String): Int {
    val scenes = intArrayOf(R.drawable.server_scene_forest, R.drawable.server_scene_nether, R.drawable.server_scene_night)
    return scenes[(id.hashCode() and Int.MAX_VALUE) % scenes.size]
}

private fun decodeServerLogo(data: String?): ImageBitmap? = runCatching {
    val prefix = "data:image/png;base64,"
    if (data == null || !data.startsWith(prefix) || data.length > 100000) return null
    val bytes = Base64.decode(data.substring(prefix.length), Base64.DEFAULT)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth != 64 || bounds.outHeight != 64 || bounds.outMimeType != "image/png") return null
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()
