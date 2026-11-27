package net.kdt.pojavlaunch.aerix.music

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class OverlayMusicSubSheet {
    NONE,
    SORT,
    ADD_TO_PLAYLIST,
    DOWNLOADS
}

/**
 * In-game Music panel shared by both GameMenuOverlayView and AerixClientOverlayView.
 * Uses inline Compose sheets instead of separate Android Dialog windows so window focus never
 * leaves GameActivity (preventing unwanted ESC pauses or control-panel bounces).
 */
@Composable
fun InGameMusicPanel(
    accent: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AerixMusicController.attach(context)
    }

    var inlineSheet by remember { mutableStateOf(OverlayMusicSubSheet.NONE) }
    var targetTrackForPlaylist by remember { mutableStateOf<MusicTrack?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    var creatingPlaylistInline by remember { mutableStateOf(false) }

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
    val activeDownloads = AerixMusicDownloader.activeCount()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Now Playing + Fast Forward + Volume + Turn off Vanilla Music
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0x30000000),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val current = playback.currentTrack
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MusicArtworkImage(current?.artworkUrl, size = 42.dp, radius = 9.dp, tint = accent)
                        Column(Modifier.weight(1f)) {
                            Text(
                                current?.title ?: "Nothing playing",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = if (current != null) {
                                buildString {
                                    append(current.artist)
                                    playback.activePlaylistName?.let { append(" · $it") }
                                    append(" · ")
                                    append(formatDurationMs(playback.positionMs))
                                    append(" / ")
                                    append(formatDurationMs(playback.durationMs.takeIf { it > 0 } ?: (current.durationSec * 1000L)))
                                    if (playback.isBuffering) append(" · Buffering…")
                                }
                            } else {
                                "Pick a song or playlist below"
                            }
                            Text(subtitle, color = Color(0xAAFFFFFF), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OverlayActionChip("Prev", accent) { AerixMusicController.previousTrack(context) }
                        OverlayActionChip(if (playback.isPlaying) "Pause" else "Play", accent, filled = true) {
                            AerixMusicController.togglePlayPause(context)
                        }
                        OverlayActionChip("+10s", accent) { AerixMusicController.fastForward(context) }
                        OverlayActionChip("Next", accent) { AerixMusicController.nextTrack(context) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverlayActionChip(
                            if (playback.volume > 0f) "Vol ${(playback.volume * 100).toInt()}%" else "Muted",
                            accent
                        ) {
                            AerixMusicController.setVolume(context, if (playback.volume > 0f) 0f else 0.85f)
                        }
                        Slider(
                            value = playback.volume,
                            onValueChange = { AerixMusicController.setVolume(context, it) },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Mute Vanilla Music", color = Color(0xCCFFFFFF), fontSize = 10.sp)
                            Switch(
                                checked = playback.vanillaMusicMuted,
                                onCheckedChange = { AerixMusicController.setVanillaMusicMuted(context, it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accent)
                            )
                        }
                    }
                }
            }

            // 2. Search bar + Sort by + Downloads button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x28FFFFFF),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        if (query.isEmpty()) Text("Search songs…", color = Color(0x88FFFFFF), fontSize = 12.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = { AerixMusicController.searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                AerixMusicController.selectedTab = MusicSectionTab.SONGS
                                AerixMusicController.submitSearch()
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                OverlayActionChip("Search", accent, filled = true) {
                    AerixMusicController.selectedTab = MusicSectionTab.SONGS
                    AerixMusicController.submitSearch()
                }
                OverlayActionChip("Sort: ${sortMode.label}", accent) {
                    inlineSheet = OverlayMusicSubSheet.SORT
                }
                OverlayActionChip(
                    if (activeDownloads > 0) "DL ($activeDownloads)" else "DL",
                    accent,
                    filled = activeDownloads > 0
                ) {
                    inlineSheet = OverlayMusicSubSheet.DOWNLOADS
                }
            }

            // 3. Middle section buttons (preserves search query & results across tab changes)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MusicSectionTab.entries.forEach { tab ->
                        val active = selectedTab == tab
                        val countLabel = when (tab) {
                            MusicSectionTab.DOWNLOADED -> " (${downloadedTracks.size})"
                            MusicSectionTab.EXPORTED -> " (${exportedTracks.size})"
                            MusicSectionTab.PLAYLISTS -> " (${playlists.size})"
                            MusicSectionTab.SONGS -> ""
                        }
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (active) accent else Color.Transparent,
                            onClick = {
                                AerixMusicController.selectedTab = tab
                                if (tab == MusicSectionTab.DOWNLOADED || tab == MusicSectionTab.EXPORTED) {
                                    AerixMusicController.refreshLibrary(context)
                                }
                            }
                        ) {
                            Text(
                                "${tab.label}$countLabel",
                                color = if (active) Color.Black else Color(0xCCFFFFFF),
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // 4. List content
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    MusicSectionTab.SONGS -> {
                        when {
                            isSearching && searchPage.tracks.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
                                }
                            }
                            searchError != null && searchPage.tracks.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(searchError, color = Color(0xFFFF8A80), fontSize = 11.sp)
                                        OverlayActionChip("Retry", accent, filled = true) {
                                            AerixMusicController.loadSearchPage(AerixMusicController.currentPage)
                                        }
                                    }
                                }
                            }
                            searchPage.tracks.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No songs found.", color = Color(0x99FFFFFF), fontSize = 11.sp)
                                }
                            }
                            else -> {
                                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(searchPage.tracks, key = { it.id }) { track ->
                                        OverlayTrackRow(
                                            track = track,
                                            isCurrent = playback.currentTrack?.id == track.id,
                                            isDownloaded = AerixMusicController.isTrackDownloaded(track.id),
                                            downloadItem = AerixMusicDownloader.items[track.id],
                                            accent = accent,
                                            onPlay = { AerixMusicController.playTrack(context, track, searchPage.tracks) },
                                            onAddPlaylist = {
                                                targetTrackForPlaylist = track
                                                creatingPlaylistInline = false
                                                newPlaylistName = ""
                                                inlineSheet = OverlayMusicSubSheet.ADD_TO_PLAYLIST
                                            },
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
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OverlayActionChip("‹ Back", accent) { AerixMusicController.openedPlaylistId = null }
                                    Text(opened.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (opened.tracks.isNotEmpty()) {
                                        OverlayActionChip("Play all", accent, filled = true) {
                                            AerixMusicController.playPlaylist(context, opened)
                                        }
                                    }
                                }
                                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    itemsIndexed(opened.tracks, key = { idx, t -> "${t.id}_$idx" }) { idx, track ->
                                        OverlayTrackRow(
                                            track = track,
                                            isCurrent = playback.currentTrack?.id == track.id,
                                            isDownloaded = track.localPath != null || AerixMusicController.isTrackDownloaded(track.id),
                                            downloadItem = AerixMusicDownloader.items[track.id],
                                            accent = accent,
                                            onPlay = { AerixMusicController.playPlaylist(context, opened, idx) },
                                            onAddPlaylist = {
                                                targetTrackForPlaylist = track
                                                creatingPlaylistInline = false
                                                inlineSheet = OverlayMusicSubSheet.ADD_TO_PLAYLIST
                                            },
                                            onDownload = if (track.localPath == null) {
                                                { AerixMusicController.downloadTrack(context, track) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(playlists, key = { it.id }) { pl ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (playback.activePlaylistId == pl.id) accent.copy(alpha = 0.2f) else Color(0x1EFFFFFF),
                                        onClick = { AerixMusicController.openedPlaylistId = pl.id }
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            MusicArtworkImage(pl.tracks.firstOrNull()?.artworkUrl, size = 36.dp, radius = 8.dp, tint = accent)
                                            Column(Modifier.weight(1f)) {
                                                Text(pl.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("${pl.tracks.size} songs", color = Color(0x99FFFFFF), fontSize = 10.sp)
                                            }
                                            if (pl.tracks.isNotEmpty()) {
                                                OverlayActionChip("Play", accent, filled = true) {
                                                    AerixMusicController.playPlaylist(context, pl)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MusicSectionTab.DOWNLOADED, MusicSectionTab.EXPORTED -> {
                        val isExported = selectedTab == MusicSectionTab.EXPORTED
                        val list = if (isExported) exportedTracks else downloadedTracks
                        val pl = playlists.firstOrNull {
                            it.id == if (isExported) MusicPlaylist.ID_EXPORTED else MusicPlaylist.ID_DOWNLOADED
                        }
                        if (list.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (isExported) "Put audio files into musics/ to play them here offline."
                                    else "Download songs to play them offline here.",
                                    color = Color(0x99FFFFFF),
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(list, key = { idx, t -> "${t.id}_$idx" }) { idx, track ->
                                    OverlayTrackRow(
                                        track = track,
                                        isCurrent = playback.currentTrack?.id == track.id,
                                        isDownloaded = true,
                                        downloadItem = null,
                                        accent = accent,
                                        onPlay = {
                                            if (pl != null) AerixMusicController.playTrack(context, track, list, pl)
                                            else AerixMusicController.playTrack(context, track, list)
                                        },
                                        onAddPlaylist = {
                                            targetTrackForPlaylist = track
                                            creatingPlaylistInline = false
                                            inlineSheet = OverlayMusicSubSheet.ADD_TO_PLAYLIST
                                        },
                                        onDownload = null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Bottom middle Previous / Next pagination
            if (selectedTab == MusicSectionTab.SONGS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FFFFFF))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(enabled = searchPage.hasPrev && !isSearching, onClick = { AerixMusicController.previousPage() }) {
                            Text("Previous", color = if (searchPage.hasPrev) accent else Color(0x66FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Page ${AerixMusicController.currentPage}", color = Color(0xCCFFFFFF), fontSize = 11.sp)
                        TextButton(enabled = searchPage.hasNext && !isSearching, onClick = { AerixMusicController.nextPage() }) {
                            Text("Next", color = if (searchPage.hasNext) accent else Color(0x66FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Inline sheets inside the same view hierarchy (zero focus-stealing Dialog windows!)
        if (inlineSheet != OverlayMusicSubSheet.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xBB000000))
                    .clickable { inlineSheet = OverlayMusicSubSheet.NONE }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xF5121820),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .clickable(enabled = false) {}
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (inlineSheet) {
                            OverlayMusicSubSheet.SORT -> {
                                Text("Sort by", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                MusicSortMode.entries.forEach { mode ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (mode == sortMode) accent.copy(alpha = 0.25f) else Color(0x1EFFFFFF),
                                        onClick = {
                                            inlineSheet = OverlayMusicSubSheet.NONE
                                            AerixMusicController.selectedTab = MusicSectionTab.SONGS
                                            AerixMusicController.selectSort(mode)
                                        }
                                    ) {
                                        Text(
                                            mode.label,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                            }

                            OverlayMusicSubSheet.ADD_TO_PLAYLIST -> {
                                val track = targetTrackForPlaylist
                                Text("Save to Playlist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (track != null) {
                                    Text("${track.title} · ${track.artist}", color = Color(0xAAFFFFFF), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!creatingPlaylistInline) {
                                        Button(
                                            onClick = { creatingPlaylistInline = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("+ Make new playlist", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                        val custom = playlists.filterNot { it.isBuiltIn }
                                        Column(Modifier.heightIn(max = 160.dp)) {
                                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                items(custom, key = { it.id }) { pl ->
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = Color(0x1EFFFFFF),
                                                        onClick = {
                                                            AerixMusicController.addTrackToPlaylist(context, pl.id, track)
                                                            Toast.makeText(context, "Added to ${pl.name}", Toast.LENGTH_SHORT).show()
                                                            inlineSheet = OverlayMusicSubSheet.NONE
                                                        }
                                                    ) {
                                                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(pl.name, color = Color.White, fontSize = 12.sp)
                                                            Text("${pl.tracks.size}", color = Color(0x99FFFFFF), fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Surface(shape = RoundedCornerShape(10.dp), color = Color(0x28FFFFFF)) {
                                            Box(Modifier.fillMaxWidth().padding(10.dp)) {
                                                if (newPlaylistName.isEmpty()) Text("Playlist name…", color = Color(0x88FFFFFF), fontSize = 12.sp)
                                                BasicTextField(
                                                    value = newPlaylistName,
                                                    onValueChange = { newPlaylistName = it.take(60) },
                                                    singleLine = true,
                                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                                    cursorBrush = SolidColor(accent),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { creatingPlaylistInline = false }) { Text("Back", color = Color(0xAAFFFFFF)) }
                                            Button(
                                                onClick = {
                                                    val created = AerixMusicController.createPlaylistAndAdd(context, newPlaylistName.ifBlank { "My Playlist" }, track)
                                                    Toast.makeText(context, "Added to ${created.name}", Toast.LENGTH_SHORT).show()
                                                    inlineSheet = OverlayMusicSubSheet.NONE
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                                            ) { Text("Create & Add", fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                }
                            }

                            OverlayMusicSubSheet.DOWNLOADS -> {
                                val list = AerixMusicDownloader.allItems()
                                Text("Downloads", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (list.isEmpty()) {
                                    Text("No active or completed downloads.", color = Color(0x99FFFFFF), fontSize = 11.sp)
                                } else {
                                    Column(Modifier.heightIn(max = 200.dp)) {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(list, key = { it.track.id }) { item ->
                                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Text("${item.track.title} · ${(item.progress * 100).toInt()}%", color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    LinearProgressIndicator(
                                                        progress = { item.progress.coerceIn(0f, 1f) },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        color = accent,
                                                        trackColor = Color(0x33FFFFFF)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            OverlayMusicSubSheet.NONE -> Unit
                        }
                        TextButton(
                            onClick = { inlineSheet = OverlayMusicSubSheet.NONE },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Close", color = Color(0xCCFFFFFF), fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayTrackRow(
    track: MusicTrack,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    downloadItem: MusicDownloadItem?,
    accent: Color,
    onPlay: () -> Unit,
    onAddPlaylist: () -> Unit,
    onDownload: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrent) accent.copy(alpha = 0.22f) else Color(0x1EFFFFFF),
        border = BorderStroke(1.dp, if (isCurrent) accent.copy(alpha = 0.6f) else Color(0x22FFFFFF)),
        onClick = onPlay
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MusicArtworkImage(track.artworkUrl, size = 38.dp, radius = 8.dp, tint = accent)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.22f))
                    .clickable(onClick = onAddPlaylist),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dur = if (track.durationSec > 0) " · ${formatDuration(track.durationSec)}" else ""
                Text("${track.artist}$dur", color = Color(0xAAFFFFFF), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onDownload != null) {
                when {
                    downloadItem?.status == MusicDownloadStatus.DOWNLOADING ||
                        downloadItem?.status == MusicDownloadStatus.QUEUED -> {
                        Text("${(downloadItem.progress * 100).toInt()}%", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    isDownloaded -> {
                        Text("Offline", color = accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    else -> {
                        OverlayActionChip("DL", accent, onClick = onDownload)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayActionChip(
    label: String,
    accent: Color,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (filled) accent else Color(0x28FFFFFF),
        onClick = onClick
    ) {
        Text(
            label,
            color = if (filled) Color.Black else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}
