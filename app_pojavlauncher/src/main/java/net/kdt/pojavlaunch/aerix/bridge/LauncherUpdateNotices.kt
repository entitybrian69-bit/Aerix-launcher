package net.kdt.pojavlaunch.aerix.bridge

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Acknowledgement survives restarts. Merely displaying an offer does not acknowledge it. */
internal object LauncherUpdateNotices {
    var revision by mutableIntStateOf(0); private set
    var openDialogs by mutableIntStateOf(0); private set
    var manualDialogs by mutableIntStateOf(0); private set
    private var lastAutomaticCheck: Long? = null
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("aerix_update_notices", 0)
    fun acknowledged(context: Context) = prefs(context).getString("acknowledged_version", null)?.let(LauncherVersion::parse)
    fun shouldOffer(installed: LauncherVersion?, available: LauncherVersion?, acknowledged: LauncherVersion?): Boolean =
        installed != null && available != null && available > installed && (acknowledged == null || available > acknowledged)
    fun acknowledge(context: Context, release: LauncherRelease) {
        val previous = acknowledged(context)
        if (previous == null || release.version > previous) {
            val version = release.version
            prefs(context).edit().putString("acknowledged_version", "${version.major}.${version.minor}.${version.patch}").apply()
            revision++
        }
    }
    fun opened(automatic: Boolean) { openDialogs++; if (!automatic) manualDialogs++ }
    fun closed(automatic: Boolean) { openDialogs = (openDialogs - 1).coerceAtLeast(0); if (!automatic) manualDialogs = (manualDialogs - 1).coerceAtLeast(0) }
    fun checkIfDue() {
        if (openDialogs > 0) return
        val now = SystemClock.elapsedRealtime()
        if (lastAutomaticCheck?.let { now - it < 15 * 60_000L } == true) return
        lastAutomaticCheck = now
        LauncherUpdater.check()
    }
}
