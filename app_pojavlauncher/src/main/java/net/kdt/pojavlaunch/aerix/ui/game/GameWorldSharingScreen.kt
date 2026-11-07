package net.kdt.pojavlaunch.aerix.ui.game

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.bridge.GameDownloadTarget
import net.kdt.pojavlaunch.aerix.client.AerixClientInputBridge
import net.kdt.pojavlaunch.game.platform.Platform
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.worldshare.WorldShareInstaller
import net.kdt.pojavlaunch.aerix.worldshare.WorldSharePolicy
import java.io.File
import java.io.RandomAccessFile

/** Tracks only e4all's own launch log messages, not player chat or local LAN addresses. */
private class WorldShareLogReader {
    private var offset = 0L
    private var partial = ""
    private var discardLine = false
    var address: String? = null
        private set

    fun read(file: File): String? {
        if (!file.isFile) { offset = 0L; partial = ""; discardLine = false; address = null; return null }
        RandomAccessFile(file, "r").use { raf ->
            val size = raf.length()
            if (size < offset) { offset = 0; partial = ""; discardLine = false; address = null }
            // The launcher truncates this log at game start. Skip a potentially partial line
            // when attaching to a long current-session log.
            if (offset == 0L && size > 2_000_000L) {
                offset = size - 2_000_000L
                discardLine = true
            }
            raf.seek(offset)
            val bytes = ByteArray((size - offset).coerceAtMost(131072).toInt())
            val count = raf.read(bytes)
            if (count <= 0) return address
            offset += count
            val content = partial + String(bytes, 0, count, Charsets.UTF_8)
            val lines = content.split('\n')
            lines.dropLast(1).forEach { line ->
                if (discardLine) discardLine = false else {
                    if (WorldSharePolicy.endedEvent(line)) address = null
                    WorldSharePolicy.addressEvent(line)?.let { address = it }
                }
            }
            partial = lines.last()
            if (partial.length > 8192) { partial = ""; discardLine = true }
            // Do not expose an old assignment while newer stop/reassignment events remain unread.
            if (offset < size) return null
        }
        return address
    }
}

@Composable
fun GameWorldSharingScreen(
    target: GameDownloadTarget, accent: Color, onBack: () -> Unit,
    onResumeGame: () -> Unit, onOpenChats: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("aerix_world_sharing", Context.MODE_PRIVATE) }
    val reader = remember(target.gameDirectory) { WorldShareLogReader() }
    var address by remember(target.gameDirectory) { mutableStateOf<String?>(null) }
    var installed by remember(target.gameDirectory) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf(false) }
    var stopConfirmation by remember { mutableStateOf(false) }
    var vanillaNotice by remember(target.gameDirectory) {
        mutableStateOf(target.loader == null && !prefs.getBoolean("vanilla_notice_shown", false))
    }
    val asset = remember(target.loader, target.mcVersion) { WorldSharePolicy.asset(target.loader, target.mcVersion) }
    val arm64 = remember { WorldShareInstaller.supportsArm64() }

    LaunchedEffect(target.gameDirectory) {
        installed = withContext(Dispatchers.IO) { WorldShareInstaller.installed(target) }
        val log = File(Tools.DIR_GAME_HOME, "latestlog.txt")
        while (isActive) {
            address = withContext(Dispatchers.IO) { runCatching { reader.read(log) }.getOrNull() }
            delay(1000)
        }
    }

    if (vanillaNotice) {
        AlertDialog(
            onDismissRequest = { vanillaNotice = false; prefs.edit().putBoolean("vanilla_notice_shown", true).apply() },
            title = { Text("Vanilla world sharing") },
            text = { Text("Vanilla Minecraft can still use LAN on the same network. Sharing over the internet needs a compatible mod loader and e4all. Install a compatible loader from the instance's Loader page, then return here.") },
            confirmButton = { TextButton(onClick = { vanillaNotice = false; prefs.edit().putBoolean("vanilla_notice_shown", true).apply() }) { Text("OK") } }
        )
    }
    if (stopConfirmation) {
        AlertDialog(
            onDismissRequest = { stopConfirmation = false },
            title = { Text("Stop public sharing?") },
            text = { Text("Aerix will return to your game and send /e4all stop through Minecraft chat. Check the chat response to confirm the relay stopped. This only works while gameplay is active in your hosted world.") },
            confirmButton = { TextButton(onClick = {
                stopConfirmation = false
                if (!Platform.isGrabbing()) {
                    Toast.makeText(context, "Resume your hosted world first, then try again.", Toast.LENGTH_LONG).show()
                } else {
                    onResumeGame()
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (Platform.isGrabbing()) {
                            AerixClientInputBridge.sendInGameChatMessage("/e4all stop")
                            Toast.makeText(context, "Stop requested; check Minecraft chat.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Game is not ready; use [Stop] in e4all chat.", Toast.LENGTH_LONG).show()
                        }
                    }, 200L)
                }
            }) { Text("Send stop command") } },
            dismissButton = { TextButton(onClick = { stopConfirmation = false }) { Text("Cancel") } }
        )
    }
    if (confirmation) {
        AlertDialog(
            onDismissRequest = { confirmation = false },
            title = { Text("Enable world sharing?") },
            text = { Text("Aerix will download e4all 2.3.0${if (target.loader == "fabric") " and Fabric API if needed" else ""}. After restarting, opening a world to LAN can publish a temporary address through e4mc's third-party relay. Anyone with the address can try to join.\n\nKeep Online Mode on for account authentication; offline mode lets people impersonate usernames.${if (target.sharedData) "\n\nThis instance uses shared data: its mods folder may also be used by other instances." else ""}") },
            confirmButton = { TextButton(onClick = {
                confirmation = false
                busy = true
                message = "Downloading and verifying e4all…"
                scope.launch {
                    val result = runCatching { WorldShareInstaller.install(target) }
                    message = result.getOrElse { it.message ?: "Installation failed." }
                    installed = withContext(Dispatchers.IO) { WorldShareInstaller.installed(target) }
                    busy = false
                }
            }) { Text("Install") } },
            dismissButton = { TextButton(onClick = { confirmation = false }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text("World Sharing", color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        }
        Text("${target.instanceName} · ${target.versionLabel}", color = AerixSurface.textSecondary)
        when {
            target.gameDirectory == null -> Text("The running instance could not be identified.", color = AerixSurface.textPrimary)
            target.loader == null -> Text("Vanilla and unrecognised loaders cannot host over the internet with e4all. Same-network LAN still works. Install a compatible loader from this instance's Loader page, then restart.", color = AerixSurface.textPrimary)
            !arm64 -> Text("e4all's Android build requires an ARM64 game runtime.", color = AerixSurface.textPrimary)
            asset == null -> Text("No supported Android e4all file is available for this version and loader. The e4all 2.3.0 release does not support every version.", color = AerixSurface.textPrimary)
            installed -> Text("e4all is installed. If you installed it in this session, restart Minecraft before using it.", color = AerixSurface.textPrimary)
            else -> {
                Text("Install e4all to share a LAN world over the internet. A restart is required.", color = AerixSurface.textPrimary)
                Button(onClick = { confirmation = true }, enabled = !busy) { Text(if (busy) "Installing…" else "Install world sharing") }
            }
        }
        if (message.isNotEmpty()) Text(message, color = AerixSurface.textSecondary)
        Text("1. Open your single-player world and choose Open to LAN.\n2. Open this A overlay and copy the address below.\n3. Open Chats here, select a friend and paste the address.\n4. Your friend copies it and pastes it into Multiplayer → Direct Connection.", color = AerixSurface.textPrimary)
        Text("e4all may automatically publish when you open a world to LAN. Anyone with the address can try to join. Do not share it publicly unless you intend to.", color = AerixSurface.textSecondary)
        if (address != null) {
            val value = address!!
            Text("Public address: $value", color = accent, fontWeight = FontWeight.Bold)
            Text("Detected from the game log. Verify it matches e4all's chat message before sharing; log detection can be stale or incorrect.", color = AerixSurface.textSecondary)
            OutlinedButton(onClick = { stopConfirmation = true }) { Text("Stop sharing") }
            Button(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Minecraft world address", value))
                Toast.makeText(context, "Address copied. Select a friend and paste it into chat.", Toast.LENGTH_LONG).show()
                onOpenChats()
            }) { Text("Copy address & open chats") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Minecraft world address", value))
                    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy address") }
                OutlinedButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Join my Minecraft Java world: $value")
                    context.startActivity(Intent.createChooser(intent, "Share world address"))
                }) { Text("Invite / Share") }
            }
        } else {
            Text("No active public address detected. If one appears in Minecraft chat but not here, copy it from chat.", color = AerixSurface.textSecondary)
        }
        Text("The host must keep the world open. Use the same Minecraft version and compatible mods. Connection and appearance depend on server authentication and the third-party relay.", color = AerixSurface.textSecondary)
        Text("To stop public sharing, use [Stop] in e4all's chat message or run /e4all stop in your own world. Closing the world also ends the session. Sharing uses the third-party e4mc relay; availability is not guaranteed.", color = AerixSurface.textSecondary)
        Text("e4all by Synquox, based on e4mc by vgskye · MIT license · github.com/Synquox/e4all", color = AerixSurface.textSecondary)
    }
}
