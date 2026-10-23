package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import net.kdt.pojavlaunch.BuildConfig
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.*
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun LauncherUpdateDialog(activity: FragmentActivity, automatic: Boolean = false, onDismiss: () -> Unit) {
    val updater = LauncherUpdater
    val phase = updater.phase
    val release = updater.release
    val accent = AerixThemeManager.currentAccent()
    var showNotes by remember { mutableStateOf(false) }
    val dismiss = {
        updater.release?.let { LauncherUpdateNotices.acknowledge(activity, it) }
        onDismiss()
    }
    val shape = RoundedCornerShape(28.dp)
    val contentHeight = (LocalConfiguration.current.screenHeightDp * 0.48f).dp.coerceAtMost(360.dp)
    val progress by animateFloatAsState(
        targetValue = (updater.received.toFloat() / (release?.asset?.size ?: 1)).coerceIn(0f, 1f), label = "update-download"
    )
    // Download rate, sampled twice a second from the byte count the download reports.
    var speedMb by remember { mutableFloatStateOf(0f) }
    val downloading = phase == LauncherUpdatePhase.DOWNLOADING
    LaunchedEffect(downloading) {
        speedMb = 0f
        if (!downloading) return@LaunchedEffect
        var lastBytes = updater.received
        var lastAt = System.nanoTime()
        while (true) {
            delay(500)
            val now = System.nanoTime()
            val bytes = updater.received
            val elapsed = (now - lastAt) / 1_000_000_000.0
            if (elapsed > 0.0) speedMb = ((bytes - lastBytes) / 1048576.0 / elapsed).toFloat()
            lastBytes = bytes
            lastAt = now
        }
    }
    LaunchedEffect(Unit) { updater.permissionReturned(activity); if (!automatic) updater.check() }
    DisposableEffect(activity, automatic) {
        LauncherUpdateNotices.opened(automatic)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) updater.permissionReturned(activity)
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer); LauncherUpdateNotices.closed(automatic) }
    }
    val heading = when (phase) {
        LauncherUpdatePhase.CHECKING -> "Checking for updates"
        LauncherUpdatePhase.CURRENT -> "No update available"
        LauncherUpdatePhase.AVAILABLE -> "A new Aerix is ready"
        LauncherUpdatePhase.DOWNLOADING -> "Downloading update"
        LauncherUpdatePhase.READY -> "Ready to install"
        LauncherUpdatePhase.ERROR -> "Couldn't complete the update"
    }
    AlertDialog(
        onDismissRequest = dismiss,
        modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(0.94f).border(BorderStroke(1.dp, AerixSurface.borderSoft), shape),
        properties = DialogProperties(usePlatformDefaultWidth = false), shape = shape,
        containerColor = AerixSurface.panel, titleContentColor = AerixSurface.textPrimary,
        textContentColor = AerixSurface.textSecondary,
        icon = { Icon(painterResource(R.drawable.aerix_ic_cloud_download), contentDescription = null, tint = accent, modifier = Modifier.size(30.dp)) },
        title = { Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Crossfade(targetState = phase, label = "update-status") { state ->
                Column(Modifier.fillMaxWidth().heightIn(max = contentHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Installed version · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge)
                    if (release != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilterChip(selected = !showNotes, onClick = { showNotes = false }, label = { Text("Update") })
                            FilterChip(selected = showNotes, onClick = { showNotes = true }, label = { Text("What's new") })
                        }
                    }
                    if (showNotes && release != null) {
                        Text("What's new in ${release.tag}", style = MaterialTheme.typography.titleMedium, color = accent)
                        SelectionContainer {
                            Text(release.notes.ifBlank { "No release notes were published for this update." }, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else when (state) {
                        LauncherUpdatePhase.CHECKING -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
                            Text("Looking for the latest stable release…")
                        }
                        LauncherUpdatePhase.CURRENT -> {
                            Text("No newer published release is available for this version.")
                            if (BuildConfig.DEBUG && release != null) {
                                Text(
                                    "Debug build: measure how fast this release downloads, one connection against the full pool.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = { updater.measureDownloadSpeed(activity) },
                                        enabled = !updater.speedTestRunning
                                    ) {
                                        Text(if (updater.speedTestRunning) "Measuring…" else "Test download speed", color = accent)
                                    }
                                    if (updater.speedTestRunning) {
                                        TextButton(onClick = { updater.cancelSpeedTest() }) {
                                            Text("Stop", color = AerixSurface.danger)
                                        }
                                    }
                                }
                                if (updater.speedTestRunning) {
                                    Text(
                                        "${megabytes(updater.speedTestReceived)} MB sampled…",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (updater.speedTestResult.isNotBlank()) {
                                    Text(updater.speedTestResult, color = accent, fontWeight = FontWeight.SemiBold)
                                }
                                // The whole file, through the updater's own path: the number this
                                // prints is exactly what the next update would average.
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = { updater.measureFullDownload(activity) },
                                        enabled = !updater.fullTestRunning
                                    ) {
                                        Text(if (updater.fullTestRunning) "Downloading the whole APK…" else "Full download test", color = accent)
                                    }
                                    if (updater.fullTestRunning) {
                                        TextButton(onClick = { updater.cancelFullTest() }) {
                                            Text("Stop", color = AerixSurface.danger)
                                        }
                                    }
                                }
                                if (updater.fullTestRunning) {
                                    val size = updater.fullTestSize.coerceAtLeast(1L)
                                    val done = (updater.fullTestReceived.toFloat() / size).coerceIn(0f, 1f)
                                    LinearProgressIndicator(progress = { done }, Modifier.fillMaxWidth().height(6.dp), color = accent)
                                    Text(
                                        "${megabytes(updater.fullTestReceived)} / ${megabytes(updater.fullTestSize)} MB",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (updater.fullTestResult.isNotBlank()) {
                                    Text(updater.fullTestResult, color = accent, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        LauncherUpdatePhase.AVAILABLE -> {
                            Text("${release?.tag} · ${megabytes(release?.asset?.size ?: 0)} MB", color = accent,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Download the update here. Android will ask you to confirm installation; your instances stay in place.")
                            Text("See What's new for the published changes. Cancel or accept this download and this release won't prompt you again; manual updates remain in Settings.", style = MaterialTheme.typography.bodyMedium)
                        }
                        LauncherUpdatePhase.DOWNLOADING -> {
                            Text("${(progress * 100).toInt()}%", color = accent, style = MaterialTheme.typography.headlineSmall)
                            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp), color = accent)
                            Text(
                                "${megabytes(updater.received)} / ${megabytes(release?.asset?.size ?: 0)} MB" +
                                    if (speedMb > 0.05f) " \u00B7 ${String.format(Locale.US, "%.1f", speedMb)} MB/s" else ""
                            )
                            Text("You can close this window. The download will keep running.", style = MaterialTheme.typography.bodySmall)
                        }
                        LauncherUpdatePhase.READY -> {
                            Text("${release?.tag} downloaded and verified", color = accent, fontWeight = FontWeight.SemiBold)
                            Text("Tap Install to open Android's confirmation. If asked, allow Aerix to install app updates.")
                        }
                        LauncherUpdatePhase.ERROR -> Text(updater.error, color = AerixSurface.danger)
                    }
                    if (state == LauncherUpdatePhase.READY && updater.error.isNotBlank()) Text(updater.error, color = AerixSurface.danger)
                }
            }
        },
        confirmButton = {
            when (phase) {
                LauncherUpdatePhase.AVAILABLE -> Button(onClick = {
                    release?.let { LauncherUpdateNotices.acknowledge(activity, it) }
                    showNotes = false
                    updater.download(activity)
                },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent)) { Text("Download update") }
                LauncherUpdatePhase.READY -> Button(onClick = { updater.install(activity) }, enabled = !updater.installing,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent)) {
                    Text(if (updater.installing) "Verifying…" else "Install")
                }
                LauncherUpdatePhase.ERROR -> TextButton(onClick = { updater.check() }) { Text("Try again", color = accent) }
                LauncherUpdatePhase.DOWNLOADING -> TextButton(onClick = { updater.cancelDownload() }) { Text("Cancel download", color = AerixSurface.danger) }
                LauncherUpdatePhase.CURRENT -> TextButton(onClick = dismiss) { Text("Done", color = accent) }
                LauncherUpdatePhase.CHECKING -> Unit
            }
        },
        dismissButton = {
            if (phase != LauncherUpdatePhase.CURRENT) TextButton(onClick = dismiss) {
                Text(if (phase == LauncherUpdatePhase.DOWNLOADING) "Keep in background" else "Cancel", color = AerixSurface.textSecondary)
            }
        }
    )
}

private fun megabytes(bytes: Long) = String.format(Locale.getDefault(), "%.1f", bytes / (1024.0 * 1024.0))
