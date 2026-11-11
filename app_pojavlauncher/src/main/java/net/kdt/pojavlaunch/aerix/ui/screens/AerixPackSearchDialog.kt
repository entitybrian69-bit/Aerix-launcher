/*
 * Aerix Launcher UI layer for PojavLauncher.
 *
 * Modpack search for the two client pages. A pack publishes every build it has ever made, so the
 * answer to "install this pack" is never a single file: this dialog asks which Minecraft version to
 * take, and offers only the ones the client being set up actually exists for.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.bridge.AssetFile
import net.kdt.pojavlaunch.aerix.bridge.AssetProject
import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.PojavAssetBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.client.AerixClientInstaller
import net.kdt.pojavlaunch.aerix.client.ClientVersions
import net.kdt.pojavlaunch.aerix.client.PackVersionFilter
import net.kdt.pojavlaunch.aerix.fastclient.FastClientInstaller
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.instances.Instances

/**
 * What a searched pack has to be able to run on: the client the player is standing in front of.
 *
 * [minecraftVersions] is the client's own release list, newest first, and it is the whole filter -
 * a pack file for a version outside it cannot be launched with this client, so it is never offered.
 * [canJoinThePack] is false for a client that is a complete instance of its own rather than a mod
 * that can be dropped into a pack: the pack then keeps its own mod list, as it must.
 */
internal class PackClientProfile(
    val clientName: String,
    val loader: String,
    val minecraftVersions: List<String>,
    val canJoinThePack: Boolean,
    val withClientNote: String
)

internal fun packClientProfile(owner: ClientOwnerPage): PackClientProfile = when (owner) {
    ClientOwnerPage.MX_INSPIRE -> PackClientProfile(
        clientName = "FastClient",
        loader = "fabric",
        minecraftVersions = FastClientInstaller.builds.map { it.minecraft },
        canJoinThePack = false,
        withClientNote = "FastClient is an instance of its own, so this pack keeps the mods it ships with."
    )
    else -> PackClientProfile(
        clientName = "Aerix Client",
        loader = "fabric",
        minecraftVersions = ClientVersions.RELEASES.toList(),
        canJoinThePack = true,
        withClientNote = "With client adds the Aerix Client mod and the Fabric API only - the pack keeps every " +
            "mod it ships with, and no optimization mods are put in beside them."
    )
}

/** One file of a pack, resolved to the client version it can run on. */
internal class PackOffer(val file: AssetFile, val minecraft: String, val rank: Int) {
    val loaderLine: String
        get() = file.loaders.joinToString(" · ").ifBlank { "no loader named" }

    val detailLine: String
        get() {
            val size = if (file.sizeBytes > 0) " · " + Tools.formatFileSize(file.sizeBytes) else ""
            val day = file.datePublished.take(10)
            return listOfNotNull(
                loaderLine,
                file.releaseType.takeIf { it.isNotBlank() },
                day.takeIf { it.isNotBlank() && it != "-" }?.let { "published $it" },
                size.takeIf { it.isNotBlank() }?.removePrefix(" · ")
            ).joinToString(" · ")
        }
}

/**
 * The window's list: only the pack's files this client can actually run, newest supported version
 * first. [PackVersionFilter] holds the rule - including the loader and the "no download" cases -
 * and is asserted on its own by {@code tests/PackVersionFilterSmokeTest.java}, which runs with javac
 * alone in CI.
 */
internal fun packOffers(files: List<AssetFile>, profile: PackClientProfile): List<PackOffer> {
    val offers = ArrayList<PackOffer>()
    val seen = HashSet<String>()
    for (file in files) {
        val match = PackVersionFilter.match(
            file.url, file.gameVersions, file.loaders, profile.minecraftVersions, profile.loader
        ) ?: continue
        if (!seen.add(file.fileName + "@" + match.minecraft)) continue
        offers.add(PackOffer(file, match.minecraft, match.clientRank))
    }
    offers.sortBy { it.rank }
    return offers
}

/**
 * The pack search both client pages open.
 *
 * Three steps in one window, so nothing is installed before the player has seen it: the results,
 * then which Minecraft version of the pack to take - only the ones this client exists for - then,
 * for a client that can be added to a pack, whether to add it. Tapping Install used to take the
 * first file the platform returned, which was any version at all, often one this client cannot run.
 */
@Composable
internal fun AerixPackSearchDialog(
    activity: FragmentActivity,
    owner: ClientOwnerPage,
    onDismiss: () -> Unit,
    onInstalled: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = remember(owner) { packClientProfile(owner) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var results by remember { mutableStateOf<List<AssetProject>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Search Modrinth and CurseForge for a modpack.") }
    // Step two and three of one window: the pack whose versions are being read, the versions that
    // arrived, and the one row the player picked.
    var choosingFor by remember { mutableStateOf<AssetProject?>(null) }
    var offers by remember { mutableStateOf<List<PackOffer>?>(null) }
    var offerNote by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<PackOffer?>(null) }
    var installing by remember { mutableStateOf<String?>(null) }

    // Reading a pack's versions is a network call, so it happens once per pick and the window says
    // what it is doing rather than showing an empty list.
    LaunchedEffect(choosingFor) {
        val project = choosingFor ?: return@LaunchedEffect
        offers = null
        offerNote = "Reading this pack's versions..."
        val files = runCatching { PojavAssetBridge.files(context, project) }.getOrNull()
        if (files == null) {
            offers = emptyList()
            offerNote = "The version list could not be reached for ${project.title}."
            return@LaunchedEffect
        }
        val list = packOffers(files, profile)
        offers = list
        offerNote = when {
            list.isNotEmpty() && list.size < files.size ->
                "${list.size} of ${files.size} files run on ${profile.clientName}; " +
                    "${files.size - list.size} hidden - other Minecraft versions, other loaders, or no download."
            list.isNotEmpty() -> "All ${files.size} published files run on ${profile.clientName}."
            files.isEmpty() -> "This pack publishes no files on ${project.platform.displayName}."
            else -> "None of this pack's ${files.size} files are built for ${profile.loader} on a Minecraft " +
                "${profile.clientName} ships. Discover can still install it, without the client."
        }
    }

    val stepBack: () -> Unit = {
        when {
            confirm != null -> confirm = null
            choosingFor != null -> { choosingFor = null; offers = null }
            else -> onDismiss()
        }
    }

    Dialog(onDismissRequest = { if (installing == null) stepBack() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF20B141B))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val picked = choosingFor
            val chosen = confirm
            when {
                picked != null && chosen == null -> PickPackVersionBody(
                    project = picked,
                    profile = profile,
                    offers = offers,
                    note = offerNote,
                    installing = installing != null,
                    onPick = { offer ->
                        if (profile.canJoinThePack) {
                            confirm = offer
                        } else {
                            installing = picked.id
                            choosingFor = null
                            offers = null
                            runPackInstall(context, scope, activity, picked, offer, false, profile) { message ->
                                status = message
                                installing = null
                                onInstalled()
                            }
                        }
                    },
                    onBack = { choosingFor = null; offers = null }
                )
                picked != null && chosen != null -> ConfirmPackInstallBody(
                    project = picked,
                    offer = chosen,
                    profile = profile,
                    busy = installing != null,
                    onAnswer = { withClient ->
                        confirm = null
                        choosingFor = null
                        installing = picked.id
                        runPackInstall(context, scope, activity, picked, chosen, withClient, profile) { message ->
                            status = message
                            installing = null
                            onInstalled()
                        }
                    },
                    onBack = { confirm = null }
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Modpacks", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Only packs that ${profile.clientName} can run on ${profile.loader} are offered a version here.",
                        color = Color(0x99FFFFFF), fontSize = 10.sp
                    )
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
                    Text(status, color = Color(0xAAFFFFFF), fontSize = 11.sp, maxLines = 3)
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(results.orEmpty().take(40)) { project ->
                            PackSearchRow(
                                project = project,
                                busy = installing != null,
                                onInstall = {
                                    choosingFor = project
                                    offers = null
                                    offerNote = "Reading this pack's versions..."
                                }
                            )
                        }
                    }
                }
            }

            Text(
                text = if (confirm != null || choosingFor != null) "Back" else "Close",
                modifier = Modifier.align(androidx.compose.ui.Alignment.End)
                    .clickable(enabled = installing == null) { stepBack() }
                    .padding(vertical = 4.dp),
                color = Color(0xAAFFFFFF),
                fontSize = 12.sp
            )
        }
    }
}

/** One result: the pack's own logo, what it is, and the Install button that opens the version window. */
@Composable
private fun PackSearchRow(project: AssetProject, busy: Boolean, onInstall: () -> Unit) {
    val icon = rememberRemoteBitmap(project.iconUrl, targetMinEdgePx = 64)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x1AFFFFFF))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // The logo is the pack's own, fetched through the same loader the Discover list uses. Until
        // now this row drew no image at all, which is why every result came up blank.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = project.title, modifier = Modifier.fillMaxSize())
            } else {
                Text(project.title.take(1).uppercase(), color = Color(0x99FFFFFF), fontSize = 14.sp,
                    fontWeight = FontWeight.Black)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(project.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${project.author} · ${project.downloads} downloads",
                color = Color(0x99FFFFFF), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(project.loaders.joinToString(" · ").ifBlank { project.platform.displayName },
                color = Color(0x77FFFFFF), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            text = "Install",
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(AerixSurface.accent.copy(alpha = 0.22f))
                .clickable(enabled = !busy, onClick = onInstall)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Step two: which Minecraft version of the pack to download. */
@Composable
private fun PickPackVersionBody(
    project: AssetProject,
    profile: PackClientProfile,
    offers: List<PackOffer>?,
    note: String,
    installing: Boolean,
    onPick: (PackOffer) -> Unit,
    onBack: () -> Unit
) {
    val icon = rememberRemoteBitmap(project.iconUrl, targetMinEdgePx = 64)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Which version of Minecraft?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(project.title, color = Color(0x99FFFFFF), fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
    Text(
        note.ifBlank { "Reading this pack's versions..." },
        color = Color(0xAAFFFFFF), fontSize = 10.sp, maxLines = 3
    )
    val list = offers.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        list.forEach { offer ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1AFFFFFF))
                    .clickable(enabled = !installing) { onPick(offer) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Minecraft ${offer.minecraft}", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                    Text(offer.file.versionNumber, color = Color(0xAAFFFFFF), fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(offer.detailLine, color = Color(0x77FFFFFF), fontSize = 9.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "Install",
                    color = AerixSurface.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
    if (list.isEmpty() && offers != null) {
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Go back to the results", fontSize = 11.sp)
        }
    }
}

/** Step three: the answer the player asked for - with the client, or on its own. */
@Composable
private fun ConfirmPackInstallBody(
    project: AssetProject,
    offer: PackOffer,
    profile: PackClientProfile,
    busy: Boolean,
    onAnswer: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Install ${project.title}?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Minecraft ${offer.minecraft} · ${offer.file.versionNumber}\n\n${profile.withClientNote}",
            color = Color(0xAAFFFFFF), fontSize = 11.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(enabled = !busy, onClick = { onAnswer(true) }) { Text("Install with client") }
            TextButton(enabled = !busy, onClick = { onAnswer(false) }) { Text("Install without client") }
            TextButton(enabled = !busy, onClick = onBack) { Text("Cancel") }
        }
    }
}

/**
 * Install the file the player chose, then - when asked - put the client into the instance the pack
 * created, and say one line about what happened either way.
 *
 * The pack is installed through the same path the Discover page uses, and only afterwards is the
 * client added to the instance that appeared. The before and after instance names are diffed rather
 * than guessed, because a pack chooses its own instance name and a wrong guess would add the client
 * mod to some unrelated install.
 */
private fun runPackInstall(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    activity: FragmentActivity,
    project: AssetProject,
    offer: PackOffer,
    withClient: Boolean,
    profile: PackClientProfile,
    report: (String) -> Unit
) {
    scope.launch {
        val before = runCatching { Instances.loadAllInstances().mapNotNull { it.name }.toSet() }
            .getOrDefault(emptySet())
        val ok = runCatching { PojavAssetBridge.install(context, project, offer.file) }.getOrDefault(false)
        if (!ok) {
            report("Minecraft ${offer.minecraft} of ${project.title} could not be installed.")
            return@launch
        }
        PojavInstanceBridge.reload()
        val created = runCatching { Instances.loadAllInstances() }.getOrDefault(emptyList())
            .firstOrNull { instance -> instance.name != null && instance.name !in before }
        if (!withClient) {
            report("${project.title} · Minecraft ${offer.minecraft} installed as its own instance.")
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
        report("Minecraft ${offer.minecraft} ready for ${profile.clientName} · $summary")
    }
}
