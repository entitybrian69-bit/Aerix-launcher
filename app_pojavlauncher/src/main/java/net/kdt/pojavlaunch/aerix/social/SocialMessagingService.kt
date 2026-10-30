package net.kdt.pojavlaunch.aerix.social

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.aerix.discord.AerixDiscordPresence

/**
 * Lightweight session-scoped Social watcher (`android:stopWithTask="true"`, `START_NOT_STICKY`).
 *
 * - Runs ONLY while the user has opened Aerix Launcher (`LauncherActivity`).
 * - NEVER auto-starts on boot, background broadcasts, or `AlarmManager` / `JobScheduler` wakeups.
 * - Immediately shuts down all background WebSockets, presence loops, and services when the user
 *   swipes/removes Aerix from Recent Apps (`onTaskRemoved`).
 */
class SocialMessagingService : Service() {

    companion object {
        private const val RESTART_ALARM_REQ_CODE = 88421

        @Volatile
        private var launcherSessionOpen: Boolean = false

        @JvmStatic
        fun markLauncherSessionOpened(context: Context) {
            launcherSessionOpen = true
            val app = context.applicationContext
            SocialInboxJobService.cancel(app)
            cancelLegacyRestartAlarm(app)
            ensureRunningIfSignedIn(app)
        }

        @JvmStatic
        fun ensureRunningIfSignedIn(context: Context) {
            val app = context.applicationContext
            SocialInboxJobService.cancel(app)
            cancelLegacyRestartAlarm(app)
            if (!launcherSessionOpen) return
            if (!SocialBackend.signedIn) {
                stop(app)
                return
            }
            // Plain startService while LauncherActivity is active — no persistent FGS notification,
            // and Android stops it automatically via stopWithTask="true" + onTaskRemoved!
            runCatching {
                app.startService(Intent(app, SocialMessagingService::class.java))
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            val app = context.applicationContext
            cancelLegacyRestartAlarm(app)
            SocialInboxJobService.cancel(app)
            runCatching {
                app.stopService(Intent(app, SocialMessagingService::class.java))
            }
        }

        private fun cancelLegacyRestartAlarm(context: Context) {
            runCatching {
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, SocialNotificationReceiver::class.java).apply {
                    action = SocialNotificationHelper.ACTION_RESTART_SYNC
                }
                val flags = PendingIntent.FLAG_NO_CREATE or
                    (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
                val pi = PendingIntent.getBroadcast(context, RESTART_ALARM_REQ_CODE, intent, flags)
                if (pi != null) {
                    am.cancel(pi)
                    pi.cancel()
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SocialInboxJobService.cancel(applicationContext)
        cancelLegacyRestartAlarm(applicationContext)

        if (!launcherSessionOpen || !SocialBackend.signedIn) {
            stopSelf()
            return START_NOT_STICKY
        }

        startSessionSyncLoop()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        launcherSessionOpen = false
        val app = applicationContext
        cancelLegacyRestartAlarm(app)
        SocialInboxJobService.cancel(app)
        scope.cancel()
        runCatching { SocialBackend.shutdownBackgroundActivity(app) }
        runCatching { AerixDiscordPresence.onAppSwipedFromRecents(app) }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        stopSelf()
        // Allow brief 180ms window for offline/clear frames to flush, then exit background process if idle
        Thread {
            runCatching { Thread.sleep(180L) }
            if (!launcherSessionOpen && !SocialBackend.hasStartedActivities()) {
                Process.killProcess(Process.myPid())
            }
        }.start()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private var loopStarted = false

    private fun startSessionSyncLoop() {
        if (loopStarted) return
        loopStarted = true
        scope.launch {
            while (isActive && launcherSessionOpen && SocialBackend.signedIn) {
                if (SocialBackend.backgroundNotificationsEnabled) {
                    runCatching { SocialBackend.pollBackgroundNotificationsOnce(applicationContext) }
                }
                delay(25_000L)
            }
            stopSelf()
        }
    }
}

/**
 * Legacy periodic job service kept only as a manifest stub that immediately cancels any previously
 * persisted OS job so upgrading installs never wake Aerix Launcher in the background.
 */
class SocialInboxJobService : JobService() {

    companion object {
        private const val JOB_ID = 77420

        @JvmStatic
        fun schedule(context: Context) {
            // Disabled intentionally: never wake the launcher automatically in the background.
            cancel(context)
        }

        @JvmStatic
        fun cancel(context: Context) {
            val app = context.applicationContext
            val js = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            runCatching { js.cancel(JOB_ID) }
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        cancel(applicationContext)
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
