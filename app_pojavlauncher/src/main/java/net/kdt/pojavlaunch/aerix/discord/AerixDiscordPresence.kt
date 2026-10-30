/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Discord Rich Presence, published from the phone to the Discord app installed on it.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.discord

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.discord.socialsdk.rpc.IDiscordRpcCallback
import com.discord.socialsdk.rpc.IDiscordRpcConnection
import com.discord.socialsdk.rpc.IDiscordRpcService
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.social.SocialNotificationReceiver
import net.kdt.pojavlaunch.lifecycle.ContextExecutor
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Shows what you are doing in Aerix Launcher on your Discord profile: "Playing Aerix Launcher —
 * In the launcher — Browsing versions" (or "Playing Minecraft 1.21.4 — Instance"), with a timer.
 *
 * Lifecycle policy:
 * - While the user is inside the launcher or in-game, Rich Presence stays live.
 * - When the user leaves the app or closes the game, Rich Presence stays visible for 1 minute
 *   ([POST_EXIT_TIMEOUT_MS] = 60,000 ms) so switching to Discord still displays it, and then
 *   explicitly sends a null `SET_ACTIVITY` frame before disconnecting so Discord clears it cleanly
 *   at 1 minute instead of lingering for ~20 minutes.
 */
object AerixDiscordPresence {

    private const val TAG = "AerixDiscordPresence"
    private const val ACTION = "com.discord.socialsdk.rpc.IDiscordRpcService"
    private const val RPC_VERSION = "1"
    private const val HANDLER_PID = 1

    /** Keep Rich Presence visible for 1 minute after leaving the app / closing the game, then clear. */
    const val POST_EXIT_TIMEOUT_MS = 60_000L
    private const val CLEAR_FLUSH_DELAY_MS = 450L
    private const val CLEAR_BIND_TIMEOUT_MS = 5_500L
    private const val PENDING_INTENT_REQUEST_CLEAR = 94120
    private const val STATE_FILE_NAME = "aerix_discord_presence.json"

    const val ACTION_DISCORD_GAME_STARTED = "net.kdt.pojavlaunch.aerix.ACTION_DISCORD_GAME_STARTED"
    const val ACTION_DISCORD_SCHEDULE_CLEAR = "net.kdt.pojavlaunch.aerix.ACTION_DISCORD_SCHEDULE_CLEAR"
    const val ACTION_DISCORD_CLEAR_NOW = "net.kdt.pojavlaunch.aerix.ACTION_DISCORD_CLEAR_NOW"

    const val EXTRA_INSTANCE_NAME = "extra_instance_name"
    const val EXTRA_MC_VERSION = "extra_mc_version"
    const val EXTRA_STARTED_AT = "extra_started_at"
    const val EXTRA_DELAY_MS = "extra_delay_ms"

    /** Official app first, then the main client mods that keep the same service. */
    private val DISCORD_PACKAGES = listOf("com.discord", "cocobo1.pupu.app")

    private val main = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var service: IDiscordRpcService? = null
    private var connection: IDiscordRpcConnection? = null
    private var bound = false
    private var ready = false

    /** True when binding solely at the 1-minute mark to send SET_ACTIVITY=null and disconnect. */
    @Volatile
    private var clearOnlyMode = false
    private var pendingClearCallbacks = mutableListOf<() -> Unit>()

    /** Set right before LauncherActivity kills itself to hand off to GameActivity. */
    @Volatile
    private var handoffToGameInProgress = false

    /** Cached in `:game` process so returning to GameActivity from background restores game card. */
    private var gameInstanceCache: String? = null
    private var gameVersionCache: String? = null
    private var gameStartedAtCache: Long = 0L

    /** What the profile should show right now, kept so a reconnect can replay it. */
    private var details: String = InLauncher.DETAILS
    private var state: String = InLauncher.HOME
    private var startedAt: Long = 0L

    private data class PresenceDiskState(
        val owner: String, // "launcher" | "game" | "none"
        val exitedAtElapsedMs: Long,
        val details: String,
        val state: String,
        val startedAt: Long
    )

    private val postExitClearRunnable = Runnable {
        val ctx = appContext ?: resolveAnyContext()
        performScheduledClear(ctx, null)
    }

    private val clearBindTimeoutRunnable = Runnable {
        if (clearOnlyMode) {
            clearOnlyMode = false
            detach()
            finishPendingClearCallbacks()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val id = DiscordAppId.asLong(appContext) ?: run {
                Log.i(TAG, "No Discord application id set; presence stays off")
                clearOnlyMode = false
                unbind()
                finishPendingClearCallbacks()
                return
            }
            val rpc = IDiscordRpcService.Stub.asInterface(binder)
            service = rpc
            connection = runCatching { rpc.connect(id, RPC_VERSION, callback) }.getOrNull()
            if (connection == null) {
                Log.w(TAG, "Discord refused the RPC connection for id $id")
                if (clearOnlyMode) {
                    clearOnlyMode = false
                    unbind()
                    finishPendingClearCallbacks()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connection = null
            ready = false
            if (clearOnlyMode) {
                clearOnlyMode = false
                finishPendingClearCallbacks()
            }
        }
    }

    private val callback = object : IDiscordRpcCallback.Stub() {
        override fun onFrame(frame: String?) {
            if (frame == null) return
            // The first frame is READY; the app then accepts SET_ACTIVITY frames.
            if (frame.contains("\"READY\"")) {
                ready = true
                if (clearOnlyMode) {
                    main.removeCallbacks(clearBindTimeoutRunnable)
                    sendClearFrameImmediate()
                    main.postDelayed({
                        clearOnlyMode = false
                        detach()
                        finishPendingClearCallbacks()
                    }, CLEAR_FLUSH_DELAY_MS)
                } else {
                    sendActivity()
                }
            }
        }

        override fun onClose(code: Int, message: String?) {
            ready = false
            connection = null
            if (clearOnlyMode) {
                clearOnlyMode = false
                finishPendingClearCallbacks()
            }
        }
    }

    /* ── cross-process lifecycle hooks ─────────────────────────────────────── */

    /** Called from LauncherActivity.onStart() in `:launcher`. */
    @JvmStatic
    fun onLauncherStarted(context: Context?) {
        val app = context?.applicationContext ?: return
        appContext = app
        handoffToGameInProgress = false
        AerixPreferences.init(app)
        clearOnlyMode = false
        main.removeCallbacks(postExitClearRunnable)
        main.removeCallbacks(clearBindTimeoutRunnable)
        cancelClearAlarm(app)
        if (startedAt == 0L) startedAt = System.currentTimeMillis()
        writeDiskState(
            app,
            PresenceDiskState(
                owner = "launcher",
                exitedAtElapsedMs = 0L,
                details = details,
                state = state,
                startedAt = startedAt
            )
        )
        attach(app)
        sendActivity()
    }

    /** Called from LauncherActivity.onStop() in `:launcher`. */
    @JvmStatic
    fun onLauncherStopped(context: Context?) {
        val app = context?.applicationContext ?: appContext ?: return
        appContext = app
        if (handoffToGameInProgress) return
        val disk = readDiskState(app)
        // If `:game` already took ownership, do not schedule an exit from `:launcher`.
        if (disk?.owner == "game") return
        val nowElapsed = SystemClock.elapsedRealtime()
        writeDiskState(
            app,
            PresenceDiskState(
                owner = "none",
                exitedAtElapsedMs = nowElapsed,
                details = details,
                state = state,
                startedAt = startedAt
            )
        )
        schedulePostExitClearInOwnerProcess(app, POST_EXIT_TIMEOUT_MS)
    }

    /**
     * Called right before `:launcher` kills its own PID in ContextAwareDoneListener to launch
     * GameActivity in `:game`, cleanly closing the old Binder without clearing the card.
     */
    @JvmStatic
    fun prepareHandoffToGame(context: Context?) {
        handoffToGameInProgress = true
        val app = context?.applicationContext ?: appContext
        if (app != null) {
            main.removeCallbacks(postExitClearRunnable)
            cancelClearAlarm(app)
        }
        detach()
    }

    /** Called from GameActivity (`initLayout` and `onStart`) in `:game`. */
    @JvmStatic
    fun onGameStarted(context: Context?, instanceName: String?, minecraftVersion: String?) {
        val app = context?.applicationContext ?: return
        appContext = app
        if (!instanceName.isNullOrBlank()) gameInstanceCache = instanceName
        if (!minecraftVersion.isNullOrBlank()) gameVersionCache = minecraftVersion
        if (gameStartedAtCache == 0L) gameStartedAtCache = System.currentTimeMillis()

        val effInstance = gameInstanceCache
        val effVer = gameVersionCache
        val gameDetails = "Playing Minecraft${if (effVer.isNullOrBlank()) "" else " $effVer"}"
        val gameState = effInstance?.takeIf { it.isNotBlank() } ?: "In game"

        writeDiskState(
            app,
            PresenceDiskState(
                owner = "game",
                exitedAtElapsedMs = 0L,
                details = gameDetails,
                state = gameState,
                startedAt = gameStartedAtCache
            )
        )
        cancelClearAlarm(app)

        // Delegate RPC connection ownership to `:launcher` so `:game` Process.killProcess()
        // never orphans a dead Binder inside Discord.
        runCatching {
            val intent = Intent(app, SocialNotificationReceiver::class.java).apply {
                action = ACTION_DISCORD_GAME_STARTED
                putExtra(EXTRA_INSTANCE_NAME, effInstance)
                putExtra(EXTRA_MC_VERSION, effVer)
                putExtra(EXTRA_STARTED_AT, gameStartedAtCache)
            }
            app.sendBroadcast(intent)
        }
    }

    /** Called from GameActivity.onStart() when resuming an already-running game session. */
    @JvmStatic
    fun onGameResumed(context: Context?) {
        if (gameInstanceCache != null || gameVersionCache != null || gameStartedAtCache != 0L) {
            onGameStarted(context, gameInstanceCache, gameVersionCache)
        }
    }

    /** Called from GameActivity.onStop() / onDestroy() in `:game`. */
    @JvmStatic
    fun onGameStopped(context: Context?) {
        val app = context?.applicationContext ?: appContext ?: resolveAnyContext() ?: return
        appContext = app
        val disk = readDiskState(app)
        if (disk?.owner == "launcher") return

        val nowElapsed = SystemClock.elapsedRealtime()
        val exitAt = if (disk != null && disk.owner == "none" && disk.exitedAtElapsedMs > 0L) {
            disk.exitedAtElapsedMs
        } else {
            nowElapsed
        }
        val remainingMs = (POST_EXIT_TIMEOUT_MS - (nowElapsed - exitAt)).coerceIn(1_000L, POST_EXIT_TIMEOUT_MS)

        writeDiskState(
            app,
            PresenceDiskState(
                owner = "none",
                exitedAtElapsedMs = exitAt,
                details = disk?.details ?: details,
                state = disk?.state ?: state,
                startedAt = disk?.startedAt ?: gameStartedAtCache
            )
        )

        // 1. OS-level AlarmManager backup (survives Process.killProcess in `:game` & Recents swipe)
        scheduleClearAlarm(app, remainingMs)

        // 2. Immediate broadcast to `:launcher` so its live RPC connection runs an exact 1-min timer
        runCatching {
            val intent = Intent(app, SocialNotificationReceiver::class.java).apply {
                action = ACTION_DISCORD_SCHEDULE_CLEAR
                putExtra(EXTRA_DELAY_MS, remainingMs)
            }
            app.sendBroadcast(intent)
        }
    }

    /**
     * Called right before Process.killProcess() in Tools.fullyExit() or GameService exit paths,
     * ensuring the 1-minute post-exit clear is always scheduled even when onStop/onDestroy are skipped.
     */
    @JvmStatic
    fun onProcessExiting(context: Context? = null) {
        val app = context?.applicationContext ?: appContext ?: resolveAnyContext() ?: return
        onGameStopped(app)
    }

    /** Invoked in `:launcher` when SocialNotificationReceiver receives ACTION_DISCORD_GAME_STARTED. */
    fun handleGameStartedBroadcast(
        context: Context,
        instanceName: String?,
        minecraftVersion: String?,
        gameStartedAt: Long
    ) {
        val app = context.applicationContext
        appContext = app
        AerixPreferences.init(app)
        clearOnlyMode = false
        main.removeCallbacks(postExitClearRunnable)
        main.removeCallbacks(clearBindTimeoutRunnable)
        cancelClearAlarm(app)

        details = "Playing Minecraft${if (minecraftVersion.isNullOrBlank()) "" else " $minecraftVersion"}"
        state = instanceName?.takeIf { it.isNotBlank() } ?: "In game"
        startedAt = if (gameStartedAt > 0L) gameStartedAt else System.currentTimeMillis()

        if (!AerixPreferences.discordPresenceEnabled) return
        attach(app)
        sendActivity()
    }

    /** Invoked in `:launcher` when SocialNotificationReceiver receives ACTION_DISCORD_SCHEDULE_CLEAR. */
    fun handleScheduleClearBroadcast(context: Context, delayMs: Long) {
        val app = context.applicationContext
        appContext = app
        AerixPreferences.init(app)
        val clamped = delayMs.coerceIn(1_000L, POST_EXIT_TIMEOUT_MS)
        schedulePostExitClearInOwnerProcess(app, clamped)
    }

    /** Invoked at the 1-minute mark (either via Handler or ACTION_DISCORD_CLEAR_NOW alarm). */
    fun performScheduledClear(context: Context?, onComplete: (() -> Unit)?) {
        val app = context?.applicationContext ?: appContext ?: resolveAnyContext()
        if (app == null) {
            onComplete?.invoke()
            return
        }
        appContext = app
        AerixPreferences.init(app)

        val disk = readDiskState(app)
        if (disk != null && disk.owner != "none") {
            // User returned to the launcher or game within the 1-minute window; keep presence live!
            onComplete?.invoke()
            return
        }
        if (disk != null && disk.exitedAtElapsedMs > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - disk.exitedAtElapsedMs
            val remaining = POST_EXIT_TIMEOUT_MS - elapsed
            if (remaining > 2_500L) {
                schedulePostExitClearInOwnerProcess(app, remaining)
                onComplete?.invoke()
                return
            }
        }
        clearAndDetach(app, onComplete)
    }

    private fun schedulePostExitClearInOwnerProcess(app: Context, delayMs: Long) {
        main.removeCallbacks(postExitClearRunnable)
        main.postDelayed(postExitClearRunnable, delayMs)
        scheduleClearAlarm(app, delayMs)
    }

    /* ── core RPC lifecycle ────────────────────────────────────────────────── */

    /** Bind once, when the launcher becomes visible. Safe to call repeatedly. */
    fun attach(context: Context?) {
        if (context == null) return
        appContext = context.applicationContext
        if (!AerixPreferences.discordPresenceEnabled) return
        if (DiscordAppId.asLong(appContext) == null) return
        clearOnlyMode = false
        if (bound && connection != null) return
        if (bound && connection == null) {
            unbind()
        }

        val intent = resolveService() ?: run {
            Log.i(TAG, "Discord is not installed on this device")
            return
        }
        bound = runCatching {
            appContext!!.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) Log.w(TAG, "Could not bind to Discord's RPC service")
    }

    /** Drop the connection immediately (internal helper; use [clearAndDetach] to clear profile first). */
    fun detach() {
        ready = false
        runCatching { connection?.disconnect() }
        connection = null
        service = null
        unbind()
    }

    /**
     * Explicitly sends `SET_ACTIVITY` with `activity: null`, flushes over Binder, and then
     * disconnects and unbinds so Discord removes the Rich Presence card immediately instead of
     * keeping an orphaned session alive for ~20 minutes.
     */
    fun clearAndDetach(context: Context? = appContext, onComplete: (() -> Unit)? = null) {
        val app = context?.applicationContext ?: appContext
        if (app != null) {
            appContext = app
            cancelClearAlarm(app)
        }
        main.removeCallbacks(postExitClearRunnable)
        startedAt = 0L

        main.post {
            if (onComplete != null) {
                pendingClearCallbacks.add(onComplete)
            }
            if (connection != null && ready) {
                sendClearFrameImmediate()
                main.postDelayed({
                    clearOnlyMode = false
                    detach()
                    finishPendingClearCallbacks()
                }, CLEAR_FLUSH_DELAY_MS)
                return@post
            }

            // Not currently connected in this process (e.g. cold wakeup from 1-min AlarmManager):
            // bind briefly in clearOnlyMode so READY immediately pushes SET_ACTIVITY=null.
            val ctx = appContext
            if (ctx == null || DiscordAppId.asLong(ctx) == null) {
                detach()
                finishPendingClearCallbacks()
                return@post
            }
            val intent = resolveService()
            if (intent == null) {
                detach()
                finishPendingClearCallbacks()
                return@post
            }
            if (bound) unbind()
            clearOnlyMode = true
            bound = runCatching {
                ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
            if (!bound) {
                clearOnlyMode = false
                finishPendingClearCallbacks()
            } else {
                main.removeCallbacks(clearBindTimeoutRunnable)
                main.postDelayed(clearBindTimeoutRunnable, CLEAR_BIND_TIMEOUT_MS)
            }
        }
    }

    fun refresh(context: Context?) {
        clearOnlyMode = false
        detach()
        attach(context)
    }

    /* ── what the profile says ─────────────────────────────────────────────── */

    /** Launcher pages: the same two lines for every screen, with the section in the second one. */
    fun inLauncher(section: String?) {
        clearOnlyMode = false
        details = InLauncher.DETAILS
        state = InLauncher.stateFor(section)
        if (startedAt == 0L) startedAt = System.currentTimeMillis()
        appContext?.let { app ->
            val disk = readDiskState(app)
            if (disk == null || disk.owner == "launcher") {
                writeDiskState(
                    app,
                    PresenceDiskState("launcher", 0L, details, state, startedAt)
                )
            }
        }
        sendActivity()
    }

    /** A game is starting or running: the instance takes over the card. */
    fun playing(instance: String?, minecraftVersion: String?) {
        clearOnlyMode = false
        details = "Playing Minecraft${if (minecraftVersion.isNullOrBlank()) "" else " $minecraftVersion"}"
        state = instance?.takeIf { it.isNotBlank() } ?: "In game"
        startedAt = System.currentTimeMillis()
        sendActivity()
    }

    /** Remove the card, e.g. when the setting is switched off. */
    fun clear() {
        send(buildClearFrame())
    }

    /* ── the RPC frames ────────────────────────────────────────────────────── */

    private fun buildClearFrame(): String =
        JSONObject()
            .put("cmd", "SET_ACTIVITY")
            .put("nonce", UUID.randomUUID().toString())
            .put("args", JSONObject().put("pid", HANDLER_PID).put("activity", JSONObject.NULL))
            .toString()

    private fun sendClearFrameImmediate() {
        val target = connection ?: return
        val frame = buildClearFrame()
        runCatching { target.sendFrame(frame) }
    }

    private fun sendActivity() {
        if (clearOnlyMode || !ready || connection == null) return
        val activity = JSONObject()
            .put("name", "Aerix Launcher")
            .put("type", 0) // PLAYING
            .put("status_display_type", 2) // show the detail line as the headline
            .put("details", details)
            .put("state", state)
            .put("timestamps", JSONObject().put("start", startedAt.coerceAtLeast(1L)))

        val args = JSONObject()
            .put("pid", HANDLER_PID)
            .put("activity", activity)

        send(
            JSONObject()
                .put("cmd", "SET_ACTIVITY")
                .put("nonce", UUID.randomUUID().toString())
                .put("args", args)
                .toString()
        )
    }

    private fun send(frame: String) {
        val target = connection ?: return
        main.post { runCatching { target.sendFrame(frame) } }
    }

    /* ── alarm & cross-process state helpers ───────────────────────────────── */

    private fun buildClearAlarmPendingIntent(app: Context, flagsExtra: Int = 0): PendingIntent? {
        val intent = Intent(app, SocialNotificationReceiver::class.java).apply {
            action = ACTION_DISCORD_CLEAR_NOW
        }
        val baseFlags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        return runCatching {
            PendingIntent.getBroadcast(app, PENDING_INTENT_REQUEST_CLEAR, intent, baseFlags or flagsExtra)
        }.getOrNull()
    }

    private fun scheduleClearAlarm(app: Context, delayMs: Long) {
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildClearAlarmPendingIntent(app, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        runCatching {
            am.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, 5_000L, pi)
        }
    }

    private fun cancelClearAlarm(app: Context) {
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = buildClearAlarmPendingIntent(app, PendingIntent.FLAG_NO_CREATE) ?: return
        runCatching {
            am.cancel(pi)
            pi.cancel()
        }
    }

    private fun stateFile(app: Context): File = File(app.filesDir, STATE_FILE_NAME)

    private fun writeDiskState(app: Context, stateObj: PresenceDiskState) {
        runCatching {
            val json = JSONObject()
                .put("owner", stateObj.owner)
                .put("exitedAtElapsedMs", stateObj.exitedAtElapsedMs)
                .put("details", stateObj.details)
                .put("state", stateObj.state)
                .put("startedAt", stateObj.startedAt)
                .toString()
            stateFile(app).writeText(json)
        }
    }

    private fun readDiskState(app: Context): PresenceDiskState? = runCatching {
        val file = stateFile(app)
        if (!file.isFile) return null
        val json = JSONObject(file.readText())
        PresenceDiskState(
            owner = json.optString("owner", "none"),
            exitedAtElapsedMs = json.optLong("exitedAtElapsedMs", 0L),
            details = json.optString("details", InLauncher.DETAILS),
            state = json.optString("state", InLauncher.HOME),
            startedAt = json.optLong("startedAt", 0L)
        )
    }.getOrNull()

    private fun resolveAnyContext(): Context? =
        runCatching { ContextExecutor.getApplication() }.getOrNull()

    private fun finishPendingClearCallbacks() {
        val copy = ArrayList(pendingClearCallbacks)
        pendingClearCallbacks.clear()
        copy.forEach { cb -> runCatching { cb.invoke() } }
    }

    /* ── plumbing ──────────────────────────────────────────────────────────── */

    private fun resolveService(): Intent? {
        val ctx = appContext ?: return null
        for (pkg in DISCORD_PACKAGES) {
            val intent = Intent(ACTION).setPackage(pkg)
            if (ctx.packageManager.resolveService(intent, 0) != null) return intent
        }
        return null
    }

    private fun unbind() {
        if (!bound) return
        runCatching { appContext?.unbindService(serviceConnection) }
        bound = false
    }

    /** The strings the card uses, in one place. */
    object InLauncher {
        const val DETAILS = "In the launcher"

        const val HOME = "Browsing the dashboard"
        const val LIBRARY = "Browsing instances"
        const val DISCOVER = "Browsing versions"
        const val CREATE_INSTANCE = "Creating an instance"
        const val WALLPAPERS = "Picking a wallpaper"
        const val SETTINGS = "In settings"
        const val ACCOUNTS = "Managing accounts"
        const val FILES = "In the file browser"
        const val ABOUT = "Reading about the launcher"
        const val SOCIALS = "Chatting in Socials"
        const val GENERIC = "Browsing"

        fun stateFor(section: String?): String = when (section?.uppercase()) {
            null, "HOME" -> HOME
            "LIBRARY" -> LIBRARY
            "DISCOVER", "DOWNLOADS" -> DISCOVER
            "CREATE_INSTANCE" -> CREATE_INSTANCE
            "WALLPAPERS" -> WALLPAPERS
            "SETTINGS", "LOGS", "CONTRIBUTION", "CONTRIBUTION_DETAIL" -> SETTINGS
            "ACCOUNTS", "ADD_ACCOUNT", "OFFLINE_ACCOUNT" -> ACCOUNTS
            "FILES" -> FILES
            "SOCIALS" -> SOCIALS
            "ABOUT", "OTHER_RELEASES" -> ABOUT
            else -> GENERIC
        }
    }
}

/**
 * The Discord application the presence is published under, registered once for the project as
 * "Aerix Launcher" (Developer Portal → New Application). Its name is the title on the card, so it
 * has to be this application and not a per-user one: every install shows the same card with no
 * setup. A user can still override it in Settings → Launcher; blank means "use this".
 */
object AerixDiscordApp {
    const val DEFAULT_APPLICATION_ID = "1563812135744839771"
}

/**
 * Discord application ids are 17–20 digit numbers. Anything else (an empty field, a client id, a
 * public key) is rejected before it can reach Discord, which would otherwise drop the connection.
 */
object DiscordAppId {
    /** The override from settings, or the application id this build ships with. */
    fun effective(): String {
        val raw = AerixPreferences.discordAppId.trim().trim('"', '\'')
        return if (raw.length >= 15) raw else AerixDiscordApp.DEFAULT_APPLICATION_ID
    }

    fun asLong(context: Context?): Long? {
        val raw = effective()
        val id = raw.toLongOrNull() ?: return null
        return id.takeIf { it > 0 }
    }
}
