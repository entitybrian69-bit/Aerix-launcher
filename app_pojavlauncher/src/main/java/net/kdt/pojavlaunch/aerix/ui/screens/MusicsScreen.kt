package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import net.kdt.pojavlaunch.aerix.music.MusicDownloadItem
import net.kdt.pojavlaunch.aerix.music.MusicDownloadStatus
import net.kdt.pojavlaunch.aerix.music.MusicPlaybackState
import net.kdt.pojavlaunch.aerix.music.MusicPlaylist
import net.kdt.pojavlaunch.aerix.music.MusicSectionTab
import net.kdt.pojavlaunch.aerix.music.MusicSkipNextIcon
import net.kdt.pojavlaunch.aerix.music.MusicSkipPreviousIcon
import net.kdt.pojavlaunch.aerix.music.MusicSortMode
import net.kdt.pojavlaunch.aerix.music.MusicSpeakerIcon
import net.kdt.pojavlaunch.aerix.music.MusicTrack
import net.kdt.pojavlaunch.aerix.music.formatDuration
import net.kdt.pojavlaunch.aerix.music.formatDurationMs
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── LEFT PANE: Glassmorphic Library & Search ────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, AerixSurface.borderSoft)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                AerixSurface.panel.copy(alpha = 0.84f),
                                AerixSurface.panelTrack.copy(alpha = 0.88f)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // Header row: Music's + Folder + Downloads / Sort pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Music's",
                            color = AerixSurface.textPrimary,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Text(
                            "Folder: $musicsFolder",
                            color = AerixSurface.textSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LauncherGlassPillButton(
                        iconRes = R.drawable.aerix_ic_download,
                        label = if (activeDownloadCount > 0) "Downloads ($activeDownloadCount)" else "Downloads",
                        highlighted = activeDownloadCount > 0,
                        accent = accent,
                        onClick = { showDownloadsWindow = true }
                    )
                    LauncherGlassPillButton(
                        iconRes = R.drawable.aerix_ic_sort,
                        label = "Sort: ${sortMode.label}",
                        highlighted = false,
                        accent = accent,
                        onClick = { showSortWindow = true }
                    )
                }

                // Pill Search Bar (search query preserved across tab switches)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    color = Color(0x28FFFFFF),
                    border = BorderStroke(1.dp, Color(0x38FFFFFF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_search),
                            contentDescription = "Search",
                            tint = Color(0xCCFFFFFF),
                            modifier = Modifier.size(16.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    "Search songs or artists…",
                                    color = Color(0xAAFFFFFF),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                                color = Color(0xCCFFFFFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        AerixMusicController.searchQuery = ""
                                        AerixMusicController.submitSearch("")
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = accent,
                                onClick = {
                                    AerixMusicController.selectedTab = MusicSectionTab.SONGS
                                    AerixMusicController.submitSearch()
                                }
                            ) {
                                Text(
                                    "Search",
                                    color = AerixSurface.onAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Segmented Pill Tabs: Songs | Playlists (N) | Downloaded (N) | Exported (N)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MusicSectionTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        val label = when (tab) {
                            MusicSectionTab.SONGS -> "Songs"
                            MusicSectionTab.PLAYLISTS -> "Playlists (${playlists.count { !it.isBuiltIn }})"
                            MusicSectionTab.DOWNLOADED -> "Downloaded (${downloadedTracks.size})"
                            MusicSectionTab.EXPORTED -> "Exported (${exportedTracks.size})"
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) accent else Color(0x26FFFFFF),
                            border = if (selected) null else BorderStroke(1.dp, Color(0x36FFFFFF)),
                            onClick = {
                                AerixMusicController.selectedTab = tab
                                if (tab == MusicSectionTab.EXPORTED || tab == MusicSectionTab.DOWNLOADED) {
                                    AerixMusicController.refreshLibrary(context)
                                }
                            }
                        ) {
                            Text(
                                text = label,
                                color = if (selected) AerixSurface.onAccent else AerixSurface.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // Main Scrollable Track / Playlist / Offline List
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        MusicSectionTab.SONGS -> {
                            when {
                                isSearching && searchPage.tracks.isEmpty() -> {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(color = accent)
                                            Text("Loading songs…", color = AerixSurface.textSecondary, fontSize = 12.sp)
                                        }
                                    }
                                }
                                searchError != null && searchPage.tracks.isEmpty() -> {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(searchError, color = AerixSurface.danger, fontSize = 13.sp, textAlign = TextAlign.Center)
                                            Button(
                                                onClick = { AerixMusicController.loadSearchPage(AerixMusicController.currentPage) },
                                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent)
                                            ) { Text("Retry", fontWeight = FontWeight.Bold) }
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
                                            LauncherTrackCard(
                                                track = track,
                                                isCurrent = playback.currentTrack?.id == track.id,
                                                isPlaying = playback.currentTrack?.id == track.id && playback.isPlaying,
                                                isDownloaded = AerixMusicController.isTrackDownloaded(track.id),
                                                downloadItem = AerixMusicDownloader.items[track.id],
                                                accent = accent,
                                                onPlay = {
                                                    if (playback.currentTrack?.id == track.id) {
                                                        AerixMusicController.togglePlayPause(context)
                                                    } else {
                                                        AerixMusicController.playTrack(context, track, searchPage.tracks)
                                                    }
                                                },
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
                                                shape = RoundedCornerShape(50),
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
                                                LauncherTrackCard(
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
                                            shape = RoundedCornerShape(50),
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
                                                color = Color(0x26FFFFFF),
                                                border = BorderStroke(1.dp, Color(0x2EFFFFFF)),
                                                onClick = { AerixMusicController.openedPlaylistId = pl.id }
                                            ) {
                                                Row(
                                                    Modifier.fillMaxWidth().padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    MusicArtworkImage(pl.tracks.firstOrNull()?.artworkUrl, size = 44.dp, radius = 10.dp, tint = accent)
                                                    Column(Modifier.weight(1f)) {
                                                        Text(pl.name, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Text(
                                                            "${pl.tracks.size} song${if (pl.tracks.size == 1) "" else "s"}" +
                                                                if (pl.id == MusicPlaylist.ID_DOWNLOADED || pl.id == MusicPlaylist.ID_EXPORTED) " · Offline ready" else "",
                                                            color = AerixSurface.textSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    if (pl.tracks.isNotEmpty()) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(34.dp)
                                                                .clip(CircleShape)
                                                                .background(accent)
                                                                .clickable { AerixMusicController.playPlaylist(context, pl) },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.aerix_ic_play_arrow_filled),
                                                                contentDescription = "Play playlist",
                                                                tint = AerixSurface.onAccent,
                                                                modifier = Modifier.size(18.dp)
                                                            )
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
                                emptyMessage = "No downloaded songs yet. Tap the download icon on any song to listen offline.",
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

                // Slim Left-Aligned Pagination Strip for Songs tab (leaves maximum room for scrolling)
                if (selectedTab == MusicSectionTab.SONGS) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (searchPage.hasPrev) Color(0x20FFFFFF) else Color.Transparent)
                                .clickable(enabled = searchPage.hasPrev && !isSearching) {
                                    AerixMusicController.previousPage()
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Previous",
                                color = if (searchPage.hasPrev) accent else AerixSurface.textMuted,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Page ${AerixMusicController.currentPage}",
                            color = AerixSurface.textSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (searchPage.hasNext) Color(0x20FFFFFF) else Color.Transparent)
                                .clickable(enabled = searchPage.hasNext && !isSearching) {
                                    AerixMusicController.nextPage()
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Next",
                                color = if (searchPage.hasNext) accent else AerixSurface.textMuted,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ── RIGHT PANE: Obsidian Glass Now Playing Card ─────────────────────────────────────────
        Surface(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, Color(0x36FFFFFF))
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xD610202D),
                                Color(0xEA09121A)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                val artSize = (maxHeight * 0.30f).coerceIn(66.dp, 128.dp)
                val current = playback.currentTrack

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top label
                    Text(
                        text = if (playback.isBuffering) "BUFFERING…" else "NOW PLAYING",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.6.sp
                    )

                    // Centerpiece Artwork with warm ambient glow
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(artSize + 16.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            accent.copy(alpha = if (current != null) 0.28f else 0.14f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        MusicArtworkImage(
                            artworkUrl = current?.artworkUrl,
                            size = artSize,
                            radius = 18.dp,
                            tint = accent,
                            targetPx = 320
                        )
                    }

                    // Track Title + Artist
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = current?.title ?: "Nothing Playing",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = current?.artist ?: "Pick a song on the left",
                            color = Color(0xB8FFFFFF),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Isolated Seek Slider + Timestamps (per-second ticks only recompose this row)
                    LauncherNowPlayingSeekBar(current = current, accent = accent)

                    // Transport Row: [-10s / Rewind] [Prev] [Big Play/Pause] [+10s] [Next]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(enabled = current != null) { AerixMusicController.rewind(context) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_refresh),
                                contentDescription = "Rewind 10s",
                                tint = if (current != null) Color.White else Color(0x55FFFFFF),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(enabled = current != null) { AerixMusicController.previousTrack(context) },
                            contentAlignment = Alignment.Center
                        ) {
                            MusicSkipPreviousIcon(
                                tint = if (current != null) Color.White else Color(0x55FFFFFF),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = accent,
                            modifier = Modifier.size(48.dp),
                            onClick = { AerixMusicController.togglePlayPause(context) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(
                                        if (playback.isPlaying) R.drawable.aerix_ic_pause else R.drawable.aerix_ic_play_arrow_filled
                                    ),
                                    contentDescription = if (playback.isPlaying) "Pause" else "Play",
                                    tint = AerixSurface.onAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0x28FFFFFF),
                            border = BorderStroke(1.dp, Color(0x28FFFFFF)),
                            onClick = { AerixMusicController.fastForward(context) }
                        ) {
                            Text(
                                "+10s",
                                color = if (current != null) Color.White else Color(0x66FFFFFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(enabled = current != null) { AerixMusicController.nextTrack(context) },
                            contentAlignment = Alignment.Center
                        ) {
                            MusicSkipNextIcon(
                                tint = if (current != null) Color.White else Color(0x55FFFFFF),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Volume Slider Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    AerixMusicController.setVolume(context, if (playback.volume > 0f) 0f else 0.85f)
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            MusicSpeakerIcon(
                                muted = playback.volume <= 0f,
                                tint = Color(0xDDFFFFFF),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                "Vol ${(playback.volume * 100).toInt()}%",
                                color = Color(0xDDFFFFFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Slider(
                            value = playback.volume,
                            onValueChange = { AerixMusicController.setVolume(context, it) },
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = Color(0x36FFFFFF)
                            )
                        )
                    }

                    // Bottom Action Pills: Download + Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val currentDownload = current?.let { AerixMusicDownloader.items[it.id] }
                        val isCurrentDownloaded = current != null &&
                            (current.localPath != null || AerixMusicController.isTrackDownloaded(current.id))
                        val downloadLabel = when {
                            current == null -> "Download"
                            currentDownload?.status == MusicDownloadStatus.DOWNLOADING ||
                                currentDownload?.status == MusicDownloadStatus.QUEUED ->
                                "${(currentDownload.progress * 100).toInt()}%"
                            isCurrentDownloaded -> "Offline"
                            else -> "Download"
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            color = Color(0x22FFFFFF),
                            border = BorderStroke(1.dp, Color(0x38FFFFFF)),
                            onClick = {
                                val track = current ?: return@Surface
                                if (!isCurrentDownloaded &&
                                    currentDownload?.status != MusicDownloadStatus.DOWNLOADING
                                ) {
                                    AerixMusicController.downloadTrack(context, track)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (isCurrentDownloaded) R.drawable.aerix_ic_check else R.drawable.aerix_ic_download
                                    ),
                                    contentDescription = null,
                                    tint = if (isCurrentDownloaded) accent else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    downloadLabel,
                                    color = if (isCurrentDownloaded) accent else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            color = Color(0x22FFFFFF),
                            border = BorderStroke(1.dp, Color(0x38FFFFFF)),
                            onClick = {
                                val track = current ?: return@Surface
                                val shareText = "Listening to \"${track.title}\" by ${track.artist} on Aerix Launcher"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, track.title)
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                runCatching {
                                    activity.startActivity(Intent.createChooser(shareIntent, "Share song"))
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.aerix_ic_share_filled),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    "Share",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────────────────────────

    if (showSortWindow) {
        Dialog(onDismissRequest = { showSortWindow = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AerixSurface.panel,
                border = BorderStroke(1.dp, AerixSurface.borderSoft)
            ) {
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
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AerixSurface.panel,
                border = BorderStroke(1.dp, AerixSurface.borderSoft)
            ) {
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
}

private fun filterByQuery(tracks: List<MusicTrack>, query: String): List<MusicTrack> {
    val clean = query.trim()
    if (clean.isEmpty()) return tracks
    return tracks.filter {
        it.title.contains(clean, ignoreCase = true) || it.artist.contains(clean, ignoreCase = true)
    }
}

@Composable
private fun LauncherGlassPillButton(
    iconRes: Int,
    label: String,
    highlighted: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (highlighted) accent.copy(alpha = 0.24f) else Color(0x28FFFFFF),
        border = BorderStroke(1.dp, if (highlighted) accent else Color(0x38FFFFFF)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (highlighted) accent else Color.White,
                modifier = Modifier.size(13.dp)
            )
            Text(
                label,
                color = if (highlighted) accent else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OfflineCollectionBody(
    emptyMessage: String,
    tracks: List<MusicTrack>,
    playlist: MusicPlaylist?,
    playback: MusicPlaybackState,
    accent: Color,
    onPlay: (Int) -> Unit,
    onAddPlaylist: (MusicTrack) -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (tracks.isNotEmpty()) "${tracks.size} offline song${if (tracks.size == 1) "" else "s"}" else "Offline music",
                color = AerixSurface.textSecondary,
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRefresh) { Text("Refresh folder", color = accent, fontSize = 11.sp) }
                if (tracks.isNotEmpty() && playlist != null) {
                    val context = LocalContext.current
                    Button(
                        onClick = { AerixMusicController.playTrack(context, tracks.first(), tracks, playlist) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("Play all", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyMessage, color = AerixSurface.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(tracks, key = { idx, t -> "${t.id}_$idx" }) { idx, track ->
                    LauncherTrackCard(
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
private fun LauncherTrackCard(
    track: MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDownloaded: Boolean,
    downloadItem: MusicDownloadItem?,
    accent: Color,
    onPlay: () -> Unit,
    onAddPlaylist: () -> Unit,
    onDownload: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) accent.copy(alpha = 0.18f) else Color(0x28FFFFFF),
        border = BorderStroke(1.dp, if (isCurrent) accent.copy(alpha = 0.72f) else Color(0x2CFFFFFF)),
        onClick = onPlay
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MusicArtworkImage(track.artworkUrl, size = 46.dp, radius = 10.dp, tint = accent)

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
                Text(
                    meta,
                    color = AerixSurface.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // "+" add-to-playlist circular icon
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.22f))
                    .clickable(onClick = onAddPlaylist),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_add),
                    contentDescription = "Add to playlist",
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Download / Offline / Progress circular icon
            if (onDownload != null) {
                when {
                    downloadItem?.status == MusicDownloadStatus.DOWNLOADING ||
                        downloadItem?.status == MusicDownloadStatus.QUEUED -> {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0x28FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${(downloadItem.progress * 100).toInt()}%",
                                color = accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    isDownloaded -> {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_check),
                                contentDescription = "Offline ready",
                                tint = accent,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .clickable(onClick = onDownload),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_download),
                                contentDescription = "Download",
                                tint = Color(0xDDFFFFFF),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            if (onRemoveFromPlaylist != null) {
                TextButton(
                    onClick = onRemoveFromPlaylist,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Remove", color = AerixSurface.danger, fontSize = 10.sp)
                }
            }

            // Circular accent Play / Pause button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        if (isPlaying) R.drawable.aerix_ic_pause else R.drawable.aerix_ic_play_arrow_filled
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = AerixSurface.onAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
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
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AerixSurface.panel,
            border = BorderStroke(1.dp, AerixSurface.borderSoft)
        ) {
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
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AerixSurface.panel,
            border = BorderStroke(1.dp, AerixSurface.borderSoft)
        ) {
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
private fun LauncherNowPlayingSeekBar(current: MusicTrack?, accent: Color) {
    val context = LocalContext.current
    val posMs = AerixMusicController.playbackPositionMs
    val durMs = AerixMusicController.playbackDurationMs
    val totalMs = (durMs.takeIf { it > 0L } ?: ((current?.durationSec ?: 0) * 1000L)).coerceAtLeast(1L)
    var scrubbingFraction by remember(current?.id) { mutableStateOf<Float?>(null) }
    val progressFraction = scrubbingFraction
        ?: (posMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    val displayPosMs = if (scrubbingFraction != null) {
        (scrubbingFraction!! * totalMs).toLong()
    } else {
        posMs
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (current != null) formatDurationMs(displayPosMs) else "0:00",
                color = Color(0xBBFFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (current != null) formatDurationMs(totalMs) else "0:00",
                color = Color(0xBBFFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = if (current != null) progressFraction else 0f,
            enabled = current != null,
            onValueChange = { scrubbingFraction = it.coerceIn(0f, 1f) },
            onValueChangeFinished = {
                val target = ((scrubbingFraction ?: progressFraction) * totalMs).toLong()
                scrubbingFraction = null
                AerixMusicController.seekTo(context, target)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color(0x36FFFFFF),
                disabledThumbColor = Color(0x55FFFFFF),
                disabledActiveTrackColor = Color(0x33FFFFFF)
            )
        )
    }
}

private fun formatPlays(plays: Long): String = when {
    plays >= 1_000_000L -> "%.1fM plays".format(plays / 1_000_000.0)
    plays >= 1_000L -> "%.1fK plays".format(plays / 1_000.0)
    else -> "$plays plays"
}
