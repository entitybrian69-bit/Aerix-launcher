package net.kdt.pojavlaunch.aerix.social

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps Aerix Socials connected in the background so incoming DMs and party messages trigger
 * instant Messenger-style notifications with inline Direct Reply even after closing the app.
 */
class SocialMessagingService : Service() {
    companion object {
        private const val TAG = "AerixSocialService"
        private const val ACTION_STOP = "net.kdt.pojavlaunch.aerix.STOP_SOCIAL_SERVICE"

        @JvmStatic
        fun ensureRunningIfSignedIn(context: Context) {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("aerix_social", Context.MODE_PRIVATE)
            val hasSession = !prefs.getString("refresh_token", null).isNullOrBlank() &&
                !prefs.getString("user_id", null).isNullOrBlank()
            val bgEnabled = prefs.getBoolean("bg_notifications_enabled", true)
            if (!hasSession || !bgEnabled) {
                stop(app)
                return
            }
            SocialInboxJobService.schedule(app)
            val intent = Intent(app, SocialMessagingService::class.java)
            try {
                ContextCompat.startForegroundService(app, intent)
            } catch (t: Throwable) {
                // Android 12+ restricts FGS start from background on some wake paths;
                // try plain startService + JobScheduler fallback without crashing.
                Log.d(TAG, "FGS start deferred to JobScheduler/background: ${t.message}")
                runCatching { app.startService(intent) }
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            val app = context.applicationContext
            runCatching {
                app.stopService(Intent(app, SocialMessagingService::class.java))
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watcherJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SocialBackend.attach(applicationContext)
        promoteToForegroundSafely()
        startInboxWatcherLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP ||
            !SocialBackend.signedIn ||
            !SocialBackend.backgroundNotificationsEnabled
        ) {
            runCatching { stopForeground(true) }
            stopSelf()
            return START_NOT_STICKY
        }

        promoteToForegroundSafely()
        if (watcherJob?.isActive != true) {
            startInboxWatcherLoop()
        }
        return START_STICKY
    }

    private fun promoteToForegroundSafely() {
        try {
            val notif = SocialNotificationHelper.buildBackgroundSyncNotification(applicationContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SocialNotificationHelper.NOTIFICATION_ID_BG_SERVICE,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                )
            } else {
                startForeground(SocialNotificationHelper.NOTIFICATION_ID_BG_SERVICE, notif)
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Foreground promotion skipped: ${t.message}")
        }
    }

    private fun startInboxWatcherLoop() {
        watcherJob?.cancel()
        watcherJob = serviceScope.launch {
            // Baseline or fetch immediately on startup
            runCatching {
                SocialBackend.ensureBackgroundRealtimeAndPollInbox()
            }
            while (isActive) {
                val intervalMs = when {
                    !SocialBackend.isNetworkAvailable -> 15_000L
                    SocialBackend.isLauncherInForeground -> 6_000L
                    else -> 8_500L
                }
                delay(intervalMs)
                if (SocialBackend.signedIn &&
                    SocialBackend.backgroundNotificationsEnabled &&
                    SocialBackend.isNetworkAvailable
                ) {
                    runCatching {
                        SocialBackend.ensureBackgroundRealtimeAndPollInbox()
                    }
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        SocialBackend.onAppTaskSwipedAway()
        if (SocialBackend.signedIn && SocialBackend.backgroundNotificationsEnabled) {
            scheduleRestartPulse(applicationContext)
            SocialInboxJobService.schedule(applicationContext)
        }
    }

    override fun onTimeout(startId: Int) {
        SocialInboxJobService.schedule(applicationContext)
        runCatching { stopForeground(true) }
        stopSelf(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ FGS timeout callback safety: hand off cleanly to JobScheduler
        SocialInboxJobService.schedule(applicationContext)
        runCatching { stopForeground(true) }
        stopSelf(startId)
    }

    override fun onDestroy() {
        watcherJob?.cancel()
        super.onDestroy()
    }

    private fun scheduleRestartPulse(context: Context) {
        try {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val restartIntent = Intent(context, SocialNotificationReceiver::class.java).apply {
                action = SocialNotificationHelper.ACTION_RESTART_SYNC
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, 74099, restartIntent, flags)
            val triggerAt = SystemClock.elapsedRealtime() + 2_500L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        } catch (_: Throwable) {
        }
    }
}

/**
 * OS-managed `JobScheduler` safety net so even if OEM battery optimization stops the background
 * service after closing the app, Android periodically wakes `:launcher` on network availability
 * to deliver unread message notifications.
 */
class SocialInboxJobService : JobService() {
    companion object {
        private const val JOB_ID = 74020
        private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @JvmStatic
        fun schedule(context: Context) {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("aerix_social", Context.MODE_PRIVATE)
            if (prefs.getString("refresh_token", null).isNullOrBlank()) return
            if (!prefs.getBoolean("bg_notifications_enabled", true)) return

            try {
                val scheduler = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                val existing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    scheduler.getPendingJob(JOB_ID)
                } else {
                    scheduler.allPendingJobs.firstOrNull { it.id == JOB_ID }
                }
                if (existing != null) return

                val component = ComponentName(app, SocialInboxJobService::class.java)
                val info = JobInfo.Builder(JOB_ID, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(15L * 60L * 1000L)
                    .setPersisted(true)
                    .build()
                scheduler.schedule(info)
            } catch (_: Throwable) {
            }
        }

        @JvmStatic
        fun cancel(context: Context) {
            try {
                val scheduler = context.applicationContext
                    .getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                scheduler.cancel(JOB_ID)
            } catch (_: Throwable) {
            }
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val app = applicationContext
        jobScope.launch {
            try {
                SocialBackend.attach(app)
                if (SocialBackend.signedIn && SocialBackend.backgroundNotificationsEnabled) {
                    SocialBackend.ensureBackgroundRealtimeAndPollInbox()
                    SocialMessagingService.ensureRunningIfSignedIn(app)
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
