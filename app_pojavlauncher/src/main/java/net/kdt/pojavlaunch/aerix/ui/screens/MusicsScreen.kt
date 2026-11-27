package net.kdt.pojavlaunch.aerix.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.music.AerixMusicController
import net.kdt.pojavlaunch.aerix.music.AerixMusicDownloader
import net.kdt.pojavlaunch.aerix.music.AerixMusicStorage
import net.kdt.pojavlaunch.aerix.music.MusicArtworkImage
import net.kdt.pojavlaunch.aerix.music.MusicDownloadStatus
import net.kdt.pojavlaunch.aerix.music.MusicPlaylist
import net.kdt.pojavlaunch.aerix.music.MusicSectionTab
import net.kdt.pojavlaunch.aerix.music.MusicSortMode
import net.kdt.pojavlaunch.aerix.music.MusicTrack
import net.kdt.pojavlaunch.aerix.music.formatDuration
import net.kdt.pojavlaunch.aerix.music.formatDurationMs
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

@Composable
fun MusicsScreen(activity: FragmentActivity) {
    val context = LocalContext.current
    val accent = AerixThemeManager.currentAccent()

    LaunchedEffect(Unit) {
        AerixMusicController.attach(context)
    }

    var showSortWindow by remember { mutableStateOf(false) }
    var saveTargetTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var showDownloadsWindow by remember { mutableStateOf(false) }
    var showCreatePlaylistOnly by remember { mutableStateOf(false) }
    var showVolumeWindow by remember { mutableStateOf(false) }

    val selectedTab = AerixMusicController.selectedTab
    val query = AerixMusicController.searchQuery
    val sortMode = AerixMusicController.sortMode
    val searchPage = AerixMusicController.searchPage
    val isSearching = AerixMusicController.isSearching
    val searchError = AerixMusicController.searchError
    val playback = AerixMusicController.playbackState
    val playlists = AerixMusicController.playlists
    val downloadedTracks = AerixMusicController.downloadedTracks
    val exportedTracks = AerixMusicController.exportedTracks
    val activeDownloadCount = AerixMusicDownloader.activeCount()
    val musicsFolder = remember { AerixMusicStorage.musicsDir(context).absolutePath }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AerixSpacing.mdPlus, vertical = AerixSpacing.smPlus),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Music's",
                    color = AerixSurface.textPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Folder: $musicsFolder",
                    color = AerixSurface.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MusicHeaderChip(
                label = if (activeDownloadCount > 0) "Downloads ($activeDownloadCount)" else "Downloads",
                highlighted = activeDownloadCount > 0,
                accent = accent
            ) { showDownloadsWindow = true }
            MusicHeaderChip(
                label = if (playback.vanillaMusicMuted) "MC Music: OFF" else "MC Music: ON",
                highlighted = playback.vanillaMusicMuted,
                accent = accent
            ) {
                AerixMusicController.setVanillaMusicMuted(context, !playback.vanillaMusicMuted)
                Toast.makeText(
                    context,
                    if (!playback.vanillaMusicMuted) "Vanilla background music muted" else "Vanilla background music enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
            MusicHeaderChip(
                label = "Vol ${(playback.volume * 100).toInt()}%",
                highlighted = false,
                accent = accent
            ) { showVolumeWindow = true }
        }

        // Search bar + Sort by button (never cleared on section tab switches)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = AerixSurface.panel,
                border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_music_note),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search songs or artists…", color = AerixSurface.textSecondary, fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { AerixMusicController.searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = AerixSurface.textPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                AerixMusicController.selectedTab = MusicSectionTab.SONGS
                                AerixMusicController.submitSearch()
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        Text(
                            "Clear",
                            color = AerixSurface.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    AerixMusicController.searchQuery = ""
                                    AerixMusicController.submitSearch("")
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Button(
                onClick = {
                    AerixMusicController.selectedTab = MusicSectionTab.SONGS
                    AerixMusicController.submitSearch()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Search", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AerixSurface.panelRaised,
                border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
                onClick = { showSortWindow = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Sort by: ${sortMode.label}", color = AerixSurface.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Marked middle section tabs: switching preserves searchQuery & searchPage!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(AerixSurface.panel)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MusicSectionTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    val countSuffix = when (tab) {
                        MusicSectionTab.DOWNLOADED -> " (${downloadedTracks.size})"
                        MusicSectionTab.EXPORTED -> " (${exportedTracks.size})"
                        MusicSectionTab.PLAYLISTS -> " (${playlists.count { !it.isBuiltIn }})"
                        MusicSectionTab.SONGS -> ""
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) accent else Color.Transparent,
                        onClick = {
                            AerixMusicController.selectedTab = tab
                            if (tab == MusicSectionTab.EXPORTED || tab == MusicSectionTab.DOWNLOADED) {
                                AerixMusicController.refreshLibrary(context)
                            }
                        }
                    ) {
                        Text(
                            text = "${tab.label}$countSuffix",
                            color = if (selected) AerixSurface.onAccent else AerixSurface.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // Main content area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                MusicSectionTab.SONGS -> {
                    when {
                        isSearching && searchPage.tracks.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(color = accent)
                                    Text("Loading songs…", color = AerixSurface.textSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                        searchError != null && searchPage.tracks.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(searchError, color = AerixSurface.danger, fontSize = 13.sp)
                                    Button(onClick = { AerixMusicController.loadSearchPage(AerixMusicController.currentPage) }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                        searchPage.tracks.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No songs matched that search.", color = AerixSurface.textSecondary, fontSize = 13.sp)
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(searchPage.tracks, key = { it.id }) { track ->
                                    MusicTrackRow(
                                        track = track,
                                        isCurrent = playback.currentTrack?.id == track.id,
                                        isPlaying = playback.currentTrack?.id == track.id && playback.isPlaying,
                                        isDownloaded = AerixMusicController.isTrackDownloaded(track.id),
                                        downloadItem = AerixMusicDownloader.items[track.id],
                                        accent = accent,
                                        onPlay = { AerixMusicController.playTrack(context, track, searchPage.tracks) },
                                        onAddPlaylist = { saveTargetTrack = track },
                                        onDownload = { AerixMusicController.downloadTrack(context, track) }
                                    )
                                }
                            }
                        }
                    }
                }

                MusicSectionTab.PLAYLISTS -> {
                    val opened = playlists.firstOrNull { it.id == AerixMusicController.openedPlaylistId }
                    if (opened != null) {
                        val filteredTracks = filterByQuery(opened.tracks, query)
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(onClick = { AerixMusicController.openedPlaylistId = null }) {
                                    Text("‹ Playlists", color = accent, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "${opened.name} (${opened.tracks.size})",
                                    color = AerixSurface.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (opened.tracks.isNotEmpty()) {
                                    Button(
                                        onClick = { AerixMusicController.playPlaylist(context, opened) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) { Text("Play all", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                }
                                if (!opened.isBuiltIn) {
                                    TextButton(onClick = { AerixMusicController.deletePlaylist(context, opened.id) }) {
                                        Text("Delete", color = AerixSurface.danger, fontSize = 11.sp)
                                    }
                                }
                            }
                            if (filteredTracks.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No songs in this playlist yet.", color = AerixSurface.textSecondary, fontSize = 12.sp)
                                }
                            } else {
                                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(filteredTracks, key = { idx, t -> "${t.id}_$idx" }) { idx, track ->
                                        MusicTrackRow(
                                            track = track,
                                            isCurrent = playback.currentTrack?.id == track.id,
                                            isPlaying = playback.currentTrack?.id == track.id && playback.isPlaying,
                                            isDownloaded = track.localPath != null || AerixMusicController.isTrackDownloaded(track.id),
                                            downloadItem = AerixMusicDownloader.items[track.id],
                                            accent = accent,
                                            onPlay = { AerixMusicController.playPlaylist(context, opened, idx) },
                                            onAddPlaylist = { saveTargetTrack = track },
                                            onDownload = if (track.localPath == null) {
                                                { AerixMusicController.downloadTrack(context, track) }
                                            } else null,
                                            onRemoveFromPlaylist = if (!opened.isBuiltIn) {
                                                { AerixMusicController.removeTrackFromPlaylist(context, opened.id, track.id) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Your playlists (offline & online)", color = AerixSurface.textSecondary, fontSize = 12.sp)
                                Button(
                                    onClick = { showCreatePlaylistOnly = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("+ New playlist", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            }
                            val visiblePlaylists = if (query.isBlank()) playlists
                            else playlists.filter { it.name.contains(query, ignoreCase = true) || it.tracks.any { t -> t.title.contains(query, true) } }
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(visiblePlaylists, key = { it.id }) { pl ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = AerixSurface.panel,
                                        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
                                        onClick = { AerixMusicController.openedPlaylistId = pl.id }
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            MusicArtworkImage(pl.tracks.firstOrNull()?.artworkUrl, size = 44.dp, tint = accent)
                                            Column(Modifier.weight(1f)) {
                                                Text(pl.name, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(
                                                    "${pl.tracks.size} song${if (pl.tracks.size == 1) "" else "s"}" +
                                                        if (pl.id == MusicPlaylist.ID_DOWNLOADED || pl.id == MusicPlaylist.ID_EXPORTED) " · Offline ready" else "",
                                                    color = AerixSurface.textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            if (pl.tracks.isNotEmpty()) {
                                                TextButton(onClick = { AerixMusicController.playPlaylist(context, pl) }) {
                                                    Text("Play", color = accent, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                MusicSectionTab.DOWNLOADED -> {
                    val items = filterByQuery(downloadedTracks, query)
                    val playlist = playlists.firstOrNull { it.id == MusicPlaylist.ID_DOWNLOADED }
                    OfflineCollectionBody(
                        emptyMessage = "No downloaded songs yet. Tap Download on any song to listen offline.",
                        tracks = items,
                        playlist = playlist,
                        playback = playback,
                        accent = accent,
                        onPlay = { idx ->
                            if (playlist != null) AerixMusicController.playTrack(context, items[idx], items, playlist)
                            else AerixMusicController.playTrack(context, items[idx], items)
                        },
                        onAddPlaylist = { saveTargetTrack = it },
                        onRefresh = { AerixMusicController.refreshLibrary(context) }
                    )
                }

                MusicSectionTab.EXPORTED -> {
                    val items = filterByQuery(exportedTracks, query)
                    val playlist = playlists.firstOrNull { it.id == MusicPlaylist.ID_EXPORTED }
                    OfflineCollectionBody(
                        emptyMessage = "Put .mp3 / .m4a / .ogg / .flac / .wav files into:\n$musicsFolder\nThey will appear here automatically.",
                        tracks = items,
                        playlist = playlist,
                        playback = playback,
                        accent = accent,
                        onPlay = { idx ->
                            if (playlist != null) AerixMusicController.playTrack(context, items[idx], items, playlist)
                            else AerixMusicController.playTrack(context, items[idx], items)
                        },
                        onAddPlaylist = { saveTargetTrack = it },
                        onRefresh = { AerixMusicController.refreshLibrary(context) }
                    )
                }
            }
        }

        // Bottom middle pagination buttons (Previous & Next)
        if (selectedTab == MusicSectionTab.SONGS) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AerixSurface.panel,
                    border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            enabled = searchPage.hasPrev && !isSearching,
                            onClick = { AerixMusicController.previousPage() }
                        ) {
                            Text("Previous", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Page ${AerixMusicController.currentPage}",
                            color = AerixSurface.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            enabled = searchPage.hasNext && !isSearching,
                            onClick = { AerixMusicController.nextPage() }
                        ) {
                            Text("Next", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Mini Now Playing bar at bottom
        playback.currentTrack?.let { current ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AerixSurface.panelRaised,
                border = BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MusicArtworkImage(current.artworkUrl, size = 40.dp, radius = 8.dp, tint = accent)
                    Column(Modifier.weight(1f)) {
                        Text(
                            current.title,
                            color = AerixSurface.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val statusLine = buildString {
                            append(current.artist)
                            append(" · ")
                            append(formatDurationMs(playback.positionMs))
                            append(" / ")
                            append(formatDurationMs(playback.durationMs.takeIf { it > 0 } ?: (current.durationSec * 1000L)))
                            if (playback.isBuffering) append(" · Buffering…")
                        }
                        Text(statusLine, color = AerixSurface.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    MusicSmallButton("Prev", accent) { AerixMusicController.previousTrack(context) }
                    MusicSmallButton(if (playback.isPlaying) "Pause" else "Play", accent, filled = true) {
                        AerixMusicController.togglePlayPause(context)
                    }
                    MusicSmallButton("+10s", accent) { AerixMusicController.fastForward(context) }
                    MusicSmallButton("Next", accent) { AerixMusicController.nextTrack(context) }
                }
            }
        }
    }

    // ── Windows / Popups ─────────────────────────────────────────────────────────────────────────

    if (showSortWindow) {
        Dialog(onDismissRequest = { showSortWindow = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = AerixSurface.panel) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sort by", color = AerixSurface.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    MusicSortMode.entries.forEach { mode ->
                        val picked = mode == sortMode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (picked) accent.copy(alpha = 0.22f) else AerixSurface.panelRaised,
                            onClick = {
                                showSortWindow = false
                                AerixMusicController.selectedTab = MusicSectionTab.SONGS
                                AerixMusicController.selectSort(mode)
                            }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(mode.label, color = AerixSurface.textPrimary, fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium)
                                if (picked) Text("Active", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    TextButton(onClick = { showSortWindow = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = AerixSurface.textSecondary)
                    }
                }
            }
        }
    }

    saveTargetTrack?.let { target ->
        SaveToPlaylistDialog(
            track = target,
            playlists = playlists.filterNot { it.isBuiltIn },
            accent = accent,
            onDismiss = { saveTargetTrack = null },
            onSelectPlaylist = { pl ->
                AerixMusicController.addTrackToPlaylist(context, pl.id, target)
                Toast.makeText(context, "Added to ${pl.name}", Toast.LENGTH_SHORT).show()
                saveTargetTrack = null
            },
            onCreateAndAdd = { name ->
                val created = AerixMusicController.createPlaylistAndAdd(context, name, target)
                Toast.makeText(context, "Created \"${created.name}\" and added song", Toast.LENGTH_SHORT).show()
                saveTargetTrack = null
            }
        )
    }

    if (showCreatePlaylistOnly) {
        CreatePlaylistDialog(
            accent = accent,
            onDismiss = { showCreatePlaylistOnly = false },
            onCreate = { name ->
                val created = AerixMusicController.createPlaylistAndAdd(context, name, null)
                Toast.makeText(context, "Created \"${created.name}\"", Toast.LENGTH_SHORT).show()
                showCreatePlaylistOnly = false
            }
        )
    }

    if (showDownloadsWindow) {
        val downloads = AerixMusicDownloader.allItems()
        Dialog(onDismissRequest = { showDownloadsWindow = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = AerixSurface.panel) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Music Downloads", color = AerixSurface.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        if (downloads.any { it.status == MusicDownloadStatus.COMPLETED || it.status == MusicDownloadStatus.FAILED }) {
                            TextButton(onClick = { AerixMusicDownloader.clearCompleted() }) {
                                Text("Clear finished", color = accent, fontSize = 11.sp)
                            }
                        }
                    }
                    if (downloads.isEmpty()) {
                        Text(
                            "No active or recent downloads. Tap Download on any song to save it to musics/ for offline playback.",
                            color = AerixSurface.textSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(downloads, key = { it.track.id }) { item ->
                                    Surface(shape = RoundedCornerShape(12.dp), color = AerixSurface.panelRaised) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                MusicArtworkImage(item.track.artworkUrl, size = 34.dp, radius = 8.dp, tint = accent)
                                                Column(Modifier.weight(1f)) {
                                                    Text(item.track.title, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    val label = when (item.status) {
                                                        MusicDownloadStatus.QUEUED -> "Queued…"
                                                        MusicDownloadStatus.DOWNLOADING -> "Downloading ${(item.progress * 100).toInt()}%"
                                                        MusicDownloadStatus.COMPLETED -> "Saved to Downloaded (offline ready)"
                                                        MusicDownloadStatus.FAILED -> item.error ?: "Failed"
                                                    }
                                                    Text(label, color = if (item.status == MusicDownloadStatus.FAILED) AerixSurface.danger else AerixSurface.textSecondary, fontSize = 10.sp)
                                                }
                                                if (item.status == MusicDownloadStatus.FAILED) {
                                                    TextButton(onClick = { AerixMusicController.downloadTrack(context, item.track) }) {
                                                        Text("Retry", color = accent, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                            LinearProgressIndicator(
                                                progress = { item.progress.coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = if (item.status == MusicDownloadStatus.FAILED) AerixSurface.danger else accent,
                                                trackColor = AerixSurface.panelTrack
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showDownloadsWindow = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = AerixSurface.textSecondary)
                    }
                }
            }
        }
    }

    if (showVolumeWindow) {
        Dialog(onDismissRequest = { showVolumeWindow = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = AerixSurface.panel) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Music Volume", color = AerixSurface.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("${(playback.volume * 100).toInt()}%", color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Slider(
                        value = playback.volume,
                        onValueChange = { AerixMusicController.setVolume(context, it) },
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { AerixMusicController.setVolume(context, if (playback.volume > 0f) 0f else 0.85f) }) {
                            Text(if (playback.volume > 0f) "Mute Music" else "Unmute Music", color = accent)
                        }
                        TextButton(onClick = { showVolumeWindow = false }) {
                            Text("Done", color = AerixSurface.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

private fun filterByQuery(tracks: List<MusicTrack>, query: String): List<MusicTrack> {
    val clean = query.trim()
    if (clean.isEmpty()) return tracks
    return tracks.filter {
        it.title.contains(clean, ignoreCase = true) || it.artist.contains(clean, ignoreCase = true)
    }
}

@Composable
private fun OfflineCollectionBody(
    emptyMessage: String,
    tracks: List<MusicTrack>,
    playlist: MusicPlaylist?,
    playback: net.kdt.pojavlaunch.aerix.music.MusicPlaybackState,
    accent: Color,
    onPlay: (Int) -> Unit,
    onAddPlaylist: (MusicTrack) -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (tracks.isNotEmpty()) "${tracks.size} offline song${if (tracks.size == 1) "" else "s"}" else "Offline music",
                color = AerixSurface.textSecondary,
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRefresh) { Text("Refresh folder", color = accent, fontSize = 11.sp) }
                if (tracks.isNotEmpty() && playlist != null) {
                    val context = LocalContext.current
                    Button(
                        onClick = { AerixMusicController.playTrack(context, tracks.first(), tracks, playlist) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("Play all", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyMessage, color = AerixSurface.textSecondary, fontSize = 12.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(tracks, key = { idx, t -> "${t.id}_$idx" }) { idx, track ->
                    MusicTrackRow(
                        track = track,
                        isCurrent = playback.currentTrack?.id == track.id,
                        isPlaying = playback.currentTrack?.id == track.id && playback.isPlaying,
                        isDownloaded = true,
                        downloadItem = null,
                        accent = accent,
                        onPlay = { onPlay(idx) },
                        onAddPlaylist = { onAddPlaylist(track) },
                        onDownload = null
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicTrackRow(
    track: MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDownloaded: Boolean,
    downloadItem: net.kdt.pojavlaunch.aerix.music.MusicDownloadItem?,
    accent: Color,
    onPlay: () -> Unit,
    onAddPlaylist: () -> Unit,
    onDownload: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) accent.copy(alpha = 0.14f) else AerixSurface.panel,
        border = BorderStroke(AerixSpacing.hairline, if (isCurrent) accent.copy(alpha = 0.6f) else AerixSurface.borderSoft),
        onClick = onPlay
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MusicArtworkImage(track.artworkUrl, size = 48.dp, radius = 10.dp, tint = accent)

            // "+" logo beside the track logo to save into / create a playlist
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .clickable(onClick = onAddPlaylist),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = accent, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    track.title,
                    color = AerixSurface.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildString {
                    append(track.artist)
                    if (track.durationSec > 0) {
                        append(" · ")
                        append(formatDuration(track.durationSec))
                    }
                    if (track.playCount > 0) {
                        append(" · ")
                        append(formatPlays(track.playCount))
                    }
                }
                Text(meta, color = AerixSurface.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (onDownload != null) {
                when {
                    downloadItem?.status == MusicDownloadStatus.DOWNLOADING ||
                        downloadItem?.status == MusicDownloadStatus.QUEUED -> {
                        Text(
                            "${(downloadItem.progress * 100).toInt()}%",
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    isDownloaded -> {
                        Text("Offline", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AerixSurface.panelRaised,
                            onClick = onDownload
                        ) {
                            Text(
                                "Download",
                                color = AerixSurface.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (onRemoveFromPlaylist != null) {
                TextButton(onClick = onRemoveFromPlaylist) {
                    Text("Remove", color = AerixSurface.danger, fontSize = 11.sp)
                }
            }

            Text(
                if (isPlaying) "Playing" else "Play",
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SaveToPlaylistDialog(
    track: MusicTrack,
    playlists: List<MusicPlaylist>,
    accent: Color,
    onDismiss: () -> Unit,
    onSelectPlaylist: (MusicPlaylist) -> Unit,
    onCreateAndAdd: (String) -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = AerixSurface.panel) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Save to Playlist", color = AerixSurface.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text("${track.title} · ${track.artist}", color = AerixSurface.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

                if (!creating) {
                    Button(
                        onClick = { creating = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent)
                    ) {
                        Text("+ Make new playlist", fontWeight = FontWeight.Bold)
                    }
                    if (playlists.isEmpty()) {
                        Text("No custom playlists yet.", color = AerixSurface.textSecondary, fontSize = 12.sp)
                    } else {
                        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(playlists, key = { it.id }) { pl ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = AerixSurface.panelRaised,
                                        onClick = { onSelectPlaylist(pl) }
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(pl.name, color = AerixSurface.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text("${pl.tracks.size} songs", color = AerixSurface.textSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AerixSurface.panelRaised,
                        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
                    ) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                            if (newName.isEmpty()) Text("Playlist name…", color = AerixSurface.textSecondary, fontSize = 13.sp)
                            BasicTextField(
                                value = newName,
                                onValueChange = { newName = it.take(60) },
                                singleLine = true,
                                textStyle = TextStyle(color = AerixSurface.textPrimary, fontSize = 13.sp),
                                cursorBrush = SolidColor(accent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { creating = false }) { Text("Back", color = AerixSurface.textSecondary) }
                        Button(
                            onClick = { onCreateAndAdd(newName.ifBlank { "My Playlist" }) },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent)
                        ) { Text("Create & Add", fontWeight = FontWeight.Bold) }
                    }
                }

                if (!creating) {
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", color = AerixSurface.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    accent: Color,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = AerixSurface.panel) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Create Playlist", color = AerixSurface.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AerixSurface.panelRaised,
                    border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
                ) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                        if (name.isEmpty()) Text("Playlist name…", color = AerixSurface.textSecondary, fontSize = 13.sp)
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it.take(60) },
                            singleLine = true,
                            textStyle = TextStyle(color = AerixSurface.textPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = AerixSurface.textSecondary) }
                    Button(
                        onClick = { onCreate(name.ifBlank { "My Playlist" }) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent)
                    ) { Text("Create", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun MusicHeaderChip(label: String, highlighted: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) accent.copy(alpha = 0.22f) else AerixSurface.panel,
        border = BorderStroke(AerixSpacing.hairline, if (highlighted) accent else AerixSurface.borderSoft),
        onClick = onClick
    ) {
        Text(
            label,
            color = if (highlighted) accent else AerixSurface.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun MusicSmallButton(label: String, accent: Color, filled: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (filled) accent else AerixSurface.panel,
        onClick = onClick
    ) {
        Text(
            label,
            color = if (filled) AerixSurface.onAccent else AerixSurface.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun formatPlays(plays: Long): String = when {
    plays >= 1_000_000L -> "%.1fM plays".format(plays / 1_000_000.0)
    plays >= 1_000L -> "%.1fK plays".format(plays / 1_000.0)
    else -> "$plays plays"
}
