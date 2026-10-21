package net.kdt.pojavlaunch.aerix.ui.screens.social

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D

// Image 2: left friend artwork and headline; right sign-in card with a white Google action.
// Blue ribbons, cool glass, white headline; Privacy and Terms sit below the action card.
@Composable
fun SocialAuthScreen(onExit: () -> Unit) {
    SocialColumns(left = {
        Spacer(Modifier.height(32.dp)); SocialHeading("Your friends, together.", true)
        Row(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(4) { SocialAvatar(it, Modifier.size(64.dp)) }
        }
    }, right = {
        SocialPanel {
            SocialHeading("Connect with Aerix", true)
            SocialCaption("Friends, messages and voice in one place.")
            Spacer(Modifier.height(20.dp))
            Button(onClick = { SocialStore.signIn() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF202A36))) {
                Text("G", color = Color(0xFF4285F4), fontSize = 22.sp)
                Spacer(Modifier.width(12.dp)); Text("Continue with Google")
            }
            SocialButton("Not now", onExit, Modifier.fillMaxWidth(), secondary = true)
            Spacer(Modifier.height(16.dp))
            SocialCaption("Aerix accounts are separate from your Minecraft account.")
            SocialCaption("Google sign-in opens a secure system screen.")
            Text("Local demo: no Google account is contacted.", color = SocialPalette.secondary, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TextButton(onClick = { SocialStore.notice = "Demo privacy: all Social data stays on this device. No Social service is contacted." }) { Text("Privacy") }
            TextButton(onClick = { SocialStore.notice = "This Social interface is a local prototype. Messaging, voice and invitations do not reach other players." }) { Text("Terms") }
        }
    })
}

@Composable
fun SocialModel(modifier: Modifier = Modifier, skin: String = SocialStore.data.skin,
    cape: String = SocialStore.data.cape, slim: Boolean = SocialStore.data.slim) {
    val context = LocalContext.current
    var back by remember { mutableStateOf(false) }
    var bytes by remember { mutableStateOf<Pair<ByteArray?, ByteArray?>>(null to null) }
    LaunchedEffect(skin, cape) {
        bytes = withContext(Dispatchers.IO) {
            val skinFile = context.filesDir.resolve("social_skin.png")
            val capeFile = context.filesDir.resolve("social_cape.png")
            val skinBytes = if (skin == "Imported skin" && skinFile.exists()) skinFile.readBytes()
                else runCatching { context.assets.open("social/skin_${if (skin == "Explorer") 2 else if (skin == "Midnight") 3 else 1}.png").use { it.readBytes() } }.getOrNull()
            val capeBytes = if (cape == "Imported cape" && capeFile.exists()) capeFile.readBytes()
                else if (cape != "None") runCatching { context.assets.open("social/cape.png").use { it.readBytes() } }.getOrNull() else null
            skinBytes to capeBytes
        }
    }
    SocialPanel(modifier) {
        AerixSkinPreview3D(null, Modifier.fillMaxWidth().height(260.dp), azimuth = if (back) 180 else 0,
            localSkin = bytes.first, localCape = bytes.second, localSlim = slim, loadingColor = SocialPalette.accent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialButton("Front", { back = false }, Modifier.weight(1f), secondary = back)
            SocialButton("Back", { back = true }, Modifier.weight(1f), secondary = !back)
        }
    }
}

// Image 3: left large interactive model; right name, skin and cape rows, then Finish setup.
// Image profile: same two-column order with settings below appearance; blue-glass panels and blue actions.
@Composable
fun SocialAppearanceScreen() = ProfileContents(setup = true)
@Composable
fun SocialProfileScreen() = ProfileContents(setup = false)
@Composable
private fun ProfileContents(setup: Boolean) {
    val data = SocialStore.data
    var name by remember(data.name) { mutableStateOf(data.name) }
    var signOut by remember { mutableStateOf(false) }
    val valid = Regex("[A-Za-z][A-Za-z0-9_]{2,19}").matches(name)
    SocialColumns(left = { SocialModel() }, right = {
        SocialPanel {
            SocialHeading(if (setup) "Aerix name" else "Aerix profile")
            SocialField(name, { name = it.take(20) }, "Aerix name", Modifier.fillMaxWidth(),
                if (valid) null else "Use 3–20 letters, numbers or underscores.")
            SocialCaption(if (valid) "✓ Name available in this demo" else "Choose a valid name")
            SocialCaption("This is your Aerix name, not your Minecraft login.")
            SocialActionRow("Skin", data.skin) { SocialShellNav.push(SocialRoute.SKIN) }
            SocialActionRow("Cape (optional)", data.cape) { SocialShellNav.push(SocialRoute.CAPE) }
            SocialButton(if (setup) "Finish setup" else "Save profile", {
                SocialStore.update { it.copy(name = name, appearanceComplete = true) }
                if (setup) SocialShellNav.reset() else SocialStore.notice = "Profile saved on this device."
            }, Modifier.fillMaxWidth(), enabled = valid)
            SocialCaption("You can change these later. In-game visibility requires supported integration.")
        }
        if (!setup) SocialPanel {
            listOf("Privacy & presence" to SocialRoute.PRIVACY, "Notifications" to SocialRoute.NOTIFICATIONS,
                "Voice chat" to SocialRoute.VOICE, "Safety" to SocialRoute.SAFETY).forEach { (label, route) ->
                SocialActionRow(label) { SocialShellNav.push(route) }
            }
            SocialActionRow("Sign out", "Sign out from this device") { signOut = true }
        }
    })
    if (signOut) SocialSignOutDialog { signOut = false }
}
@Composable
fun SocialActionRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.White.copy(alpha = .055f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SocialHeading(title); if (subtitle != null) SocialCaption(subtitle) }
            Text("›", color = SocialPalette.secondary, fontSize = 24.sp)
        }
    }
}

// Images 6/7: left full-height model and Front/Back controls; right a selection grid and details.
// Selection uses a blue border/fill; Apply and Cancel follow the grid, never overlay the model.
@Composable
fun SocialSkinPicker() = SocialWardrobe(false)
@Composable
fun SocialCapePicker() = SocialWardrobe(true)
@Composable
private fun SocialWardrobe(capeMode: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(if (capeMode) SocialStore.data.cape else SocialStore.data.skin) }
    var slim by remember { mutableStateOf(SocialStore.data.slim) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            val result = withContext(Dispatchers.IO) { runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(1024 * 1024) }
                    ?: error("Could not open PNG")
                check(bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) { "Choose a PNG image." }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                check(if (capeMode) options.outWidth == 64 && options.outHeight == 32
                    else options.outWidth == 64 && options.outHeight in listOf(32, 64)) { if (capeMode) "Capes must be 64×32 PNG." else "Skins must be 64×64 or 64×32 PNG." }
                context.filesDir.resolve(if (capeMode) "social_cape.png" else "social_skin.png").writeBytes(bytes)
            } }
            importing = false
            if (result.isSuccess) selected = if (capeMode) "Imported cape" else "Imported skin"
            else error = result.exceptionOrNull()?.message
        }
    }
    SocialColumns(left = {
        SocialModel(skin = if (capeMode) SocialStore.data.skin else selected,
            cape = if (capeMode) selected else SocialStore.data.cape, slim = slim)
        if (!capeMode) Row {
            SocialButton("Classic", { slim = false }, Modifier.weight(1f), secondary = slim)
            SocialButton("Slim", { slim = true }, Modifier.weight(1f), secondary = !slim)
        }
    }, right = {
        SocialPanel {
            SocialHeading(if (capeMode) "Your capes" else "Your skins")
            SocialButton(if (importing) "Importing…" else "Import PNG", { importer.launch(arrayOf("image/png")) }, Modifier.fillMaxWidth(), enabled = !importing)
            val choices = if (capeMode) listOf("None", "Blue cape", "Imported cape") else listOf("Current skin", "Explorer", "Midnight", "Imported skin")
            choices.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { choice ->
                        Column(Modifier.weight(1f).heightIn(min = 110.dp)
                            .background(if (selected == choice) SocialPalette.accent.copy(alpha = .4f) else Color.White.copy(alpha = .07f), RoundedCornerShape(14.dp))
                            .clickable { if (!choice.startsWith("Imported") || context.filesDir.resolve(if (capeMode) "social_cape.png" else "social_skin.png").exists()) selected = choice }
                            .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            SocialCosmeticThumbnail(choice, capeMode, Modifier.size(72.dp))
                            SocialCaption((if (selected == choice) "✓ " else "") + choice)
                        }
                    }
                }
            }
            error?.let { Text(it, color = SocialPalette.danger) }
            SocialCaption(if (capeMode) "For your Aerix profile. In-game cape visibility requires supported integration." else "PNG skin files • 64×64 or legacy 64×32. Changes your Aerix profile only.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SocialButton(if (capeMode) "Apply cape" else "Apply skin", {
                    SocialStore.update { if (capeMode) it.copy(cape = selected) else it.copy(skin = selected, slim = slim) }
                    SocialShellNav.back()
                }, Modifier.weight(1f), enabled = !importing)
                SocialButton("Cancel", { SocialShellNav.back() }, Modifier.weight(1f), secondary = true)
            }
        }
    })
}
private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) { val count = read(buffer); if (count < 0) break; check(output.size() + count <= limit) { "PNG must be smaller than 1 MB." }; output.write(buffer, 0, count) }
    return output.toByteArray()
}

// Image 22: centered blue-glass sign-out confirmation, explanation, two bottom actions.
// No Minecraft account or instance deletion; other signed-in sessions are not affected.
@Composable
fun SocialSignOutDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF395773),
        title = { Text("Sign out of Aerix?") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Friends, requests, and accepted-friend messages stay on your account. Sign in again to access them on this device.")
            Text("Your Minecraft account and local instances are not deleted.")
            SocialCaption("Unsaved changes will be discarded. Other signed-in devices are not affected.")
            SocialCaption("Demo: data is stored only on this device.")
        } }, confirmButton = { TextButton(onClick = { SocialStore.signOut(); onDismiss() }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun SocialCosmeticThumbnail(choice: String, cape: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, choice, cape) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val imported = choice.startsWith("Imported")
                val bytes = if (imported) context.filesDir.resolve(if (cape) "social_cape.png" else "social_skin.png").readBytes()
                    else context.assets.open(if (cape) "social/cape_preview.png" else "social/skin_preview_${if (choice == "Explorer") 2 else if (choice == "Midnight") 3 else 1}.png").use { it.readBytes() }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                bitmap?.let { it.asImageBitmap() }
            }.getOrNull()
        }
    }
    if (choice == "None") Box(modifier, contentAlignment = Alignment.Center) { SocialCaption("None") }
    else image?.let { androidx.compose.foundation.Image(it, contentDescription = choice, modifier = modifier,
        contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
        ?: Box(modifier, contentAlignment = Alignment.Center) { SocialCaption("Import PNG") }
}
