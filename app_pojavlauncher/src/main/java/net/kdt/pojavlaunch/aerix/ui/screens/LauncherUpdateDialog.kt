package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
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
import java.util.Locale

@Composable
internal fun LauncherUpdateDialog(activity: FragmentActivity, onDismiss: () -> Unit) {
    val updater = LauncherUpdater
    val phase = updater.phase
    val release = updater.release
    val accent = AerixThemeManager.currentAccent()
    val shape = RoundedCornerShape(24.dp)
    val contentHeight = (LocalConfiguration.current.screenHeightDp * 0.34f).dp.coerceAtMost(220.dp)
    val progress by animateFloatAsState(
        targetValue = (updater.received.toFloat() / (release?.asset?.size ?: 1)).coerceIn(0f, 1f), label = "update-download"
    )
    LaunchedEffect(Unit) { updater.permissionReturned(activity); updater.check() }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) updater.permissionReturned(activity)
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
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
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(0.94f).border(BorderStroke(1.dp, AerixSurface.borderSoft), shape),
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
                    when (state) {
                        LauncherUpdatePhase.CHECKING -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
                            Text("Looking for the latest stable release…")
                        }
                        LauncherUpdatePhase.CURRENT -> Text("No newer published release is available for this version.")
                        LauncherUpdatePhase.AVAILABLE -> {
                            Text("${release?.tag} · ${megabytes(release?.asset?.size ?: 0)} MB", color = accent,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Download the update here. Android will ask you to confirm installation; your instances stay in place.")
                            if (!release?.notes.isNullOrBlank()) Text(release?.notes.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                        LauncherUpdatePhase.DOWNLOADING -> {
                            Text("${(progress * 100).toInt()}%", color = accent, style = MaterialTheme.typography.headlineSmall)
                            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp), color = accent)
                            Text("${megabytes(updater.received)} / ${megabytes(release?.asset?.size ?: 0)} MB")
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
                LauncherUpdatePhase.AVAILABLE -> Button(onClick = { updater.download(activity) },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent)) { Text("Download update") }
                LauncherUpdatePhase.READY -> Button(onClick = { updater.install(activity) }, enabled = !updater.installing,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent)) {
                    Text(if (updater.installing) "Verifying…" else "Install")
                }
                LauncherUpdatePhase.ERROR -> TextButton(onClick = { updater.check() }) { Text("Try again", color = accent) }
                LauncherUpdatePhase.DOWNLOADING -> TextButton(onClick = { updater.cancelDownload() }) { Text("Cancel download", color = AerixSurface.danger) }
                LauncherUpdatePhase.CURRENT -> TextButton(onClick = onDismiss) { Text("Done", color = accent) }
                LauncherUpdatePhase.CHECKING -> Unit
            }
        },
        dismissButton = {
            if (phase != LauncherUpdatePhase.CURRENT) TextButton(onClick = onDismiss) {
                Text(if (phase == LauncherUpdatePhase.DOWNLOADING) "Background" else "Close", color = AerixSurface.textSecondary)
            }
        }
    )
}

private fun megabytes(bytes: Long) = String.format(Locale.getDefault(), "%.1f", bytes / (1024.0 * 1024.0))
