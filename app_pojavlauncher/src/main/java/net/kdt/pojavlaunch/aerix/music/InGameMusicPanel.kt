package net.kdt.pojavlaunch.aerix.music

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R

private enum class OverlayMusicSubSheet {
    NONE,
    SORT,
    ADD_TO_PLAYLIST,
    DOWNLOADS
}

/**
 * In-game Music panel shared by both GameMenuScreen and AerixClientOverlayView.
 * Uses inline Compose sheets instead of separate Android Dialog windows so window focus never
 * leaves GameActivity (preventing unwanted ESC pauses or control-panel bounces).
 */
@Composable
fun InGameMusicPanel(
    accent: Color,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
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

    val obsidianBg = Color(0xCC0D1218)
    val obsidianPane = Color(0xC40D1218)
    val warmBorder = accent.copy(alpha = 0.36f)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── TOP BAR: [< Back]  Music  [Mute Vanilla Music toggle]  [DL] ─────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = obsidianBg,
                border = BorderStroke(1.dp, warmBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onBack != null) {
                        Text(
                            "< Back",
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onBack)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        "Music",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                        textAlign = if (onBack != null) TextAlign.Center else TextAlign.Start
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0x1EFFFFFF),
                        border = BorderStroke(1.dp, Color(0x28FFFFFF))
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Mute Vanilla Music",
                                color = Color(0xDDFFFFFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Switch(
                                checked = playback.vanillaMusicMuted,
                                onCheckedChange = { AerixMusicController.setVanillaMusicMuted(context, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = accent,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0x36FFFFFF)
                                )
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (activeDownloads > 0) accent else Color(0x1EFFFFFF),
                        border = BorderStroke(1.dp, if (activeDownloads > 0) accent else warmBorder),
                        onClick = { inlineSheet = OverlayMusicSubSheet.DOWNLOADS }
                    ) {
                        Text(
                            if (activeDownloads > 0) "DL ($activeDownloads)" else "DL",
                            color = if (activeDownloads > 0) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // ── TWO-PANE BODY: Left Player Deck (36%) + Right Library Pane (64%) ───────────────
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // LEFT PANE: Compact Now Playing Deck
                Surface(
                    modifier = Modifier
                        .weight(0.36f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    color = obsidianPane,
                    border = BorderStroke(1.dp, warmBorder)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        val artSize = (maxHeight * 0.34f).coerceIn(72.dp, 124.dp)
                        val current = playback.currentTrack
                        val totalMs = (playback.durationMs.takeIf { it > 0L } ?: ((current?.durationSec ?: 0) * 1000L)).coerceAtLeast(1L)
                        var scrubbingFraction by remember(current?.id) { mutableStateOf<Float?>(null) }
                        val progressFraction = scrubbingFraction
                            ?: (playback.positionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                        val displayPosMs = if (scrubbingFraction != null) {
                            (scrubbingFraction!! * totalMs).toLong()
                        } else {
                            playback.positionMs
                        }

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            MusicArtworkImage(
                                artworkUrl = current?.artworkUrl,
                                size = artSize,
                                radius = 14.dp,
                                tint = accent,
                                targetPx = 280
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    current?.title ?: "Nothing playing",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    current?.let {
                                        if (playback.isBuffering) "${it.artist} · Buffering…" else it.artist
                                    } ?: "Pick a song on the right",
                                    color = Color(0xAAFFFFFF),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Thin Amber Scrub Slider + Centered Time
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
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
                                        .height(20.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = accent,
                                        activeTrackColor = accent,
                                        inactiveTrackColor = Color(0x33FFFFFF),
                                        disabledThumbColor = Color(0x44FFFFFF),
                                        disabledActiveTrackColor = Color(0x22FFFFFF)
                                    )
                                )
                                Text(
                                    if (current != null) {
                                        "${formatDurationMs(displayPosMs)} / ${formatDurationMs(totalMs)}"
                                    } else {
                                        "0:00 / 0:00"
                                    },
                                    color = Color(0xBBFFFFFF),
                                    fontSize = 10.sp
                                )
                            }

                            // Transport Controls: [Prev] [Big Play/Pause] [+10s] [Next]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0x1EFFFFFF),
                                    modifier = Modifier.size(32.dp),
                                    onClick = { AerixMusicController.previousTrack(context) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        MusicSkipPreviousIcon(
                                            tint = if (current != null) Color.White else Color(0x55FFFFFF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = accent,
                                    modifier = Modifier.size(44.dp),
                                    onClick = { AerixMusicController.togglePlayPause(context) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(
                                                if (playback.isPlaying) R.drawable.aerix_ic_pause else R.drawable.aerix_ic_play_arrow_filled
                                            ),
                                            contentDescription = if (playback.isPlaying) "Pause" else "Play",
                                            tint = Color.Black,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color(0x1EFFFFFF),
                                    onClick = { AerixMusicController.fastForward(context) }
                                ) {
                                    Text(
                                        "+10s",
                                        color = if (current != null) accent else Color(0x55FFFFFF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0x1EFFFFFF),
                                    modifier = Modifier.size(32.dp),
                                    onClick = { AerixMusicController.nextTrack(context) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        MusicSkipNextIcon(
                                            tint = if (current != null) Color.White else Color(0x55FFFFFF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Bottom Compact Volume Slider + Circular Speaker Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Slider(
                                    value = playback.volume,
                                    onValueChange = { AerixMusicController.setVolume(context, it) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(20.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = accent,
                                        activeTrackColor = accent,
                                        inactiveTrackColor = Color(0x33FFFFFF)
                                    )
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = accent,
                                    modifier = Modifier.size(26.dp),
                                    onClick = {
                                        AerixMusicController.setVolume(context, if (playback.volume > 0f) 0f else 0.85f)
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        MusicSpeakerIcon(
                                            muted = playback.volume <= 0f,
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // RIGHT PANE: Search + Sort + Underline Tabs + High-Density List + Pagination
                Surface(
                    modifier = Modifier
                        .weight(0.64f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    color = obsidianPane,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        // Search + Sort row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0x18FFFFFF),
                                border = BorderStroke(1.dp, Color(0x26FFFFFF))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.aerix_ic_search),
                                        contentDescription = "Search",
                                        tint = Color(0xAAFFFFFF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (query.isEmpty()) {
                                            Text("Search songs…", color = Color(0x88FFFFFF), fontSize = 12.sp)
                                        }
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
                                    if (query.isNotEmpty()) {
                                        Text(
                                            "Clear",
                                            color = Color(0xAAFFFFFF),
                                            fontSize = 10.sp,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    AerixMusicController.searchQuery = ""
                                                    AerixMusicController.submitSearch("")
                                                }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0x1EFFFFFF),
                                border = BorderStroke(1.dp, Color(0x28FFFFFF)),
                                onClick = { inlineSheet = OverlayMusicSubSheet.SORT }
                            ) {
                                Text(
                                    "Sort: ${sortMode.label}",
                                    color = accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = accent,
                                onClick = {
                                    AerixMusicController.selectedTab = MusicSectionTab.SONGS
                                    AerixMusicController.submitSearch()
                                }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.aerix_ic_search),
                                        contentDescription = "Submit search",
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // Underline Tabs: Songs | Playlists (N) | Downloaded | Exported
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MusicSectionTab.entries.forEach { tab ->
                                    val active = selectedTab == tab
                                    val tabText = when (tab) {
                                        MusicSectionTab.SONGS -> "Songs"
                                        MusicSectionTab.PLAYLISTS -> "Playlists (${playlists.size})"
                                        MusicSectionTab.DOWNLOADED -> "Downloaded (${downloadedTracks.size})"
                                        MusicSectionTab.EXPORTED -> "Exported (${exportedTracks.size})"
                                    }
                                    Column(
                                        modifier = Modifier
                                            .clickable {
                                                AerixMusicController.selectedTab = tab
                                                if (tab == MusicSectionTab.DOWNLOADED || tab == MusicSectionTab.EXPORTED) {
                                                    AerixMusicController.refreshLibrary(context)
                                                }
                                            }
                                            .padding(horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            tabText,
                                            color = if (active) Color.White else Color(0x99FFFFFF),
                                            fontSize = 11.sp,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(bottom = 5.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(44.dp)
                                                .height(2.dp)
                                                .background(if (active) accent else Color.Transparent)
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0x22FFFFFF))
                            )
                        }

                        // Library List Content
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
                                                CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
                                            }
                                        }
                                        searchError != null && searchPage.tracks.isEmpty() -> {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
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
                                            LazyColumn(
                                                Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                items(searchPage.tracks, key = { it.id }) { track ->
                                                    OverlayTrackRow(
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
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OverlayActionChip("‹ Back", accent) { AerixMusicController.openedPlaylistId = null }
                                                Text(
                                                    opened.name,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (opened.tracks.isNotEmpty()) {
                                                    OverlayActionChip("Play all", accent, filled = true) {
                                                        AerixMusicController.playPlaylist(context, opened)
                                                    }
                                                }
                                            }
                                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                itemsIndexed(opened.tracks, key = { idx, t -> "${t.id}_$idx" }) { idx, track ->
                                                    OverlayTrackRow(
                                                        track = track,
                                                        isCurrent = playback.currentTrack?.id == track.id,
                                                        isPlaying = playback.currentTrack?.id == track.id && playback.isPlaying,
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
                                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            items(playlists, key = { it.id }) { pl ->
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (playback.activePlaylistId == pl.id) accent.copy(alpha = 0.18f) else Color(0x16FFFFFF),
                                                    border = BorderStroke(1.dp, if (playback.activePlaylistId == pl.id) accent.copy(alpha = 0.5f) else Color(0x1EFFFFFF)),
                                                    onClick = { AerixMusicController.openedPlaylistId = pl.id }
                                                ) {
                                                    Row(
                                                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        MusicArtworkImage(pl.tracks.firstOrNull()?.artworkUrl, size = 34.dp, radius = 7.dp, tint = accent)
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
                                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            itemsIndexed(list, key = { idx, t -> "${t.id}_$idx" }) { idx, track ->
                                                OverlayTrackRow(
                                                    track = track,
                                                    isCurrent = playback.currentTrack?.id == track.id,
                                                    isPlaying = playback.currentTrack?.id == track.id && playback.isPlaying,
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

                        // Bottom Pagination Strip for Songs
                        if (selectedTab == MusicSectionTab.SONGS) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    enabled = searchPage.hasPrev && !isSearching,
                                    onClick = { AerixMusicController.previousPage() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "Previous",
                                        color = if (searchPage.hasPrev) accent else Color(0x55FFFFFF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "Page ${AerixMusicController.currentPage}",
                                    color = Color(0xCCFFFFFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    enabled = searchPage.hasNext && !isSearching,
                                    onClick = { AerixMusicController.nextPage() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "Next",
                                        color = if (searchPage.hasNext) accent else Color(0x55FFFFFF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
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
                        .fillMaxWidth(0.7f)
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
    isPlaying: Boolean,
    isDownloaded: Boolean,
    downloadItem: MusicDownloadItem?,
    accent: Color,
    onPlay: () -> Unit,
    onAddPlaylist: () -> Unit,
    onDownload: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrent) accent.copy(alpha = 0.16f) else Color(0x16FFFFFF),
        border = BorderStroke(1.dp, if (isCurrent) accent.copy(alpha = 0.58f) else Color(0x1EFFFFFF)),
        onClick = onPlay
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MusicArtworkImage(track.artworkUrl, size = 34.dp, radius = 7.dp, tint = accent)
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val dur = if (track.durationSec > 0) " · ${formatDuration(track.durationSec)}" else ""
                Text(
                    "${track.artist}$dur",
                    color = Color(0x99FFFFFF),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .clickable(onClick = onAddPlaylist),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_add),
                    contentDescription = "Add to playlist",
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (onDownload != null) {
                when {
                    downloadItem?.status == MusicDownloadStatus.DOWNLOADING ||
                        downloadItem?.status == MusicDownloadStatus.QUEUED -> {
                        Text(
                            "${(downloadItem.progress * 100).toInt()}%",
                            color = accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    isDownloaded -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_check),
                                contentDescription = "Offline ready",
                                tint = accent,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0x1EFFFFFF))
                                .clickable(onClick = onDownload),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_download),
                                contentDescription = "Download",
                                tint = Color(0xCCFFFFFF),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) accent else Color(0x1EFFFFFF))
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        if (isPlaying) R.drawable.aerix_ic_pause else R.drawable.aerix_ic_play_arrow_filled
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isCurrent) Color.Black else accent,
                    modifier = Modifier.size(13.dp)
                )
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
