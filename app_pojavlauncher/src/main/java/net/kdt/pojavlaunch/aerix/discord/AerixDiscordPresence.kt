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
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.social.SocialNotificationReceiver
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Publishes rich, clickable Discord Rich Presence cards from Aerix Launcher to the Discord app:
 *
 * 1. **Aerix Launcher Card Mode** (always used in launcher, and optionally in-game):
 *    - Published under `Aerix Launcher` (`1563812135744839771`) with crisp Aerix / modpack cover
 *      art (`assets.large_image`), clickable cover & badge URLs (`large_url`, `small_url`),
 *      clickable headline & status links (`details_url`, `state_url`) opening the official Aerix
 *      Launcher website and Discord community, action buttons (`About Aerix` + `Join Discord`),
 *      and live Aerix Social party size `(current of max)`.
 * 2. **Official Minecraft Game Profile Mode** (Hybrid in-game option, enabled by default):
 *    - While in-game, switches RPC connection to Minecraft's verified Discord catalog ID
 *      (`1402418491272986635`, with automatic cascade fallback to `356875570916753438` and then
 *      Aerix's ID `1563812135744839771` if a mobile client rejects external catalog IDs) so
 *      tapping the card on Discord opens Minecraft's native full-screen Game Profile sheet while
 *      displaying `"via Aerix Launcher"` and the Aerix badge.
 * 3. **1-Minute Post-Exit Clear**:
 *    - Stays visible for 1 minute ([POST_EXIT_TIMEOUT_MS] = 60,000 ms) after leaving the app or
 *      closing the game, then sends an explicit Social SDK clear frame (`SET_ACTIVITY` without
 *      `activity`) on the active application ID before disconnecting so Discord clears it cleanly.
 */
object AerixDiscordPresence {

    private const val TAG = "AerixDiscordPresence"
    private const val ACTION = "com.discord.socialsdk.rpc.IDiscordRpcService"
    private const val RPC_VERSION = "1"
    private const val HANDLER_PID = 1
    private const val CLOSE_INVALID_CLIENT_ID = 4000
    private const val MAX_ASSET_URL_LEN = 256

    /** Official clickable destinations & artwork for Aerix Rich Presence cards. */
    const val AERIX_WEBSITE_URL = "https://entitybrian69-bit.github.io/Aerix-launcher/"
    const val AERIX_DISCORD_URL = "https://discord.gg/RS7q9KaCm6"
    const val AERIX_LOGO_URL =
        "https://raw.githubusercontent.com/entitybrian69-bit/Aerix-launcher/v3_openjdk/assets/aerix-logo.png"

    /** Verified Discord catalog IDs for Minecraft's native Game Profile sheet. */
    const val MINECRAFT_PRIMARY_APP_ID = 1402418491272986635L
    const val MINECRAFT_LEGACY_APP_ID = 356875570916753438L

    /** Keep Rich Presence visible for 1 minute after leaving the app / closing the game, then clear. */
    const val POST_EXIT_TIMEOUT_MS = 60_000L
    private const val CLEAR_FLUSH_DELAY_MS = 400L
    private const val CLEAR_BIND_TIMEOUT_MS = 5_500L
    private const val PENDING_INTENT_REQUEST_CLEAR = 94120
    private const val STATE_FILE_NAME = "aerix_discord_presence.json"

    const val ACTION_DISCORD_GAME_STARTED = "net.kdt.pojavlaunch.aerix.ACTION_DISCORD_GAME_STARTED"
    const val ACTION_DISCORD_SCHEDULE_CLEAR = "net.kdt.pojavlaunch.aerix.ACTION_DISCORD_SCHEDULE_CLEAR"
    const val ACTION_DISCORD_CLEAR_NOW = "net.kdt.pojavlaunch.aerix.ACTION_DISCORD_CLEAR_NOW"

    const val EXTRA_INSTANCE_NAME = "extra_instance_name"
    const val EXTRA_MC_VERSION = "extra_mc_version"
    const val EXTRA_MODPACK_ICON_URL = "extra_modpack_icon_url"
    const val EXTRA_STARTED_AT = "extra_started_at"
    const val EXTRA_DELAY_MS = "extra_delay_ms"

    /** Official app first, then the main client mods that keep the same service. */
    private val DISCORD_PACKAGES = listOf("com.discord", "cocobo1.pupu.app")

    private val main = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var service: IDiscordRpcService? = null
    private var connection: IDiscordRpcConnection? = null
    private var connectedAppId: Long = 0L
    private var bound = false
    private var ready = false

    /** Catalog IDs rejected by the installed Discord app during this session (e.g. close 4000). */
    private val rejectedAppIds = HashSet<Long>()

    /**
     * Automatically set to false if the installed Discord Android client returns an RPC ERROR frame
     * for `buttons` / `party`, preserving `assets` and clickable `details_url` / `state_url`.
     */
    @Volatile
    private var supportsExtendedFields = true

    /** Previous app ID whose card must be cleared before switching to a different app ID. */
    @Volatile
    private var staleAppIdToClearFirst: Long = 0L

    /** True when binding solely at the 1-minute mark to clear the active card and disconnect. */
    @Volatile
    private var clearOnlyMode = false
    @Volatile
    private var pendingClearAppId: Long = 0L
    private val pendingClearCallbacks = mutableListOf<() -> Unit>()

    /** Set right before LauncherActivity kills itself to hand off to GameActivity. */
    @Volatile
    private var handoffToGameInProgress = false

    /** Cached in `:game` process so returning to GameActivity from background restores game card. */
    private var gameInstanceCache: String? = null
    private var gameVersionCache: String? = null
    private var gameModpackIconCache: String? = null
    private var gameStartedAtCache: Long = 0L

    /** Current state published to Discord. */
    private var inGameMode: Boolean = false
    private var activeModpackIconUrl: String? = null
    private var details: String = InLauncher.DETAILS
    private var state: String = InLauncher.HOME
    private var startedAt: Long = 0L

    private data class PresenceDiskState(
        val owner: String, // "launcher" | "game" | "none"
        val exitedAtElapsedMs: Long,
        val details: String,
        val state: String,
        val startedAt: Long,
        val activeAppId: Long = 0L,
        val modpackIconUrl: String? = null
    )

    private val postExitClearRunnable = Runnable {
        val ctx = appContext ?: resolveAnyContext()
        performScheduledClear(ctx, null)
    }

    private val clearBindTimeoutRunnable = Runnable {
        if (clearOnlyMode) {
            clearOnlyMode = false
            pendingClearAppId = 0L
            detach()
            finishPendingClearCallbacks()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val rpc = IDiscordRpcService.Stub.asInterface(binder)
            service = rpc
            main.post {
                val initialId = when {
                    clearOnlyMode -> resolveClearTargetAppId()
                    staleAppIdToClearFirst > 0L -> staleAppIdToClearFirst
                    else -> resolveDesiredAppId()
                }
                if (initialId == null || initialId <= 0L) {
                    Log.i(TAG, "No Discord application id set; presence stays off")
                    clearOnlyMode = false
                    pendingClearAppId = 0L
                    staleAppIdToClearFirst = 0L
                    unbind()
                    finishPendingClearCallbacks()
                    return@post
                }
                connectServiceWithId(initialId)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connection = null
            connectedAppId = 0L
            ready = false
            if (clearOnlyMode) {
                clearOnlyMode = false
                pendingClearAppId = 0L
                finishPendingClearCallbacks()
            }
        }
    }

    private val callback = object : IDiscordRpcCallback.Stub() {
        override fun onFrame(frame: String?) {
            if (frame == null) return
            if (frame.contains("\"READY\"")) {
                main.post {
                    ready = true
                    if (staleAppIdToClearFirst > 0L && connectedAppId == staleAppIdToClearFirst && !clearOnlyMode) {
                        // Flush clear on the previous app ID before connecting the new app ID!
                        sendClearFrameImmediate()
                        main.postDelayed({
                            staleAppIdToClearFirst = 0L
                            ready = false
                            runCatching { connection?.disconnect() }
                            connection = null
                            connectedAppId = 0L
                            val nextId = resolveDesiredAppId()
                            if (nextId != null && service != null) {
                                connectServiceWithId(nextId)
                            }
                        }, CLEAR_FLUSH_DELAY_MS)
                        return@post
                    }

                    if (clearOnlyMode) {
                        main.removeCallbacks(clearBindTimeoutRunnable)
                        sendClearFrameImmediate()
                        main.postDelayed({
                            clearOnlyMode = false
                            pendingClearAppId = 0L
                            appContext?.let { app ->
                                readDiskState(app)?.let { d ->
                                    writeDiskState(app, d.copy(activeAppId = 0L))
                                }
                            }
                            detach()
                            finishPendingClearCallbacks()
                        }, CLEAR_FLUSH_DELAY_MS)
                    } else {
                        appContext?.let { app ->
                            val d = readDiskState(app)
                            if (d != null && d.activeAppId != connectedAppId) {
                                writeDiskState(app, d.copy(activeAppId = connectedAppId))
                            }
                        }
                        sendActivity()
                    }
                }
            } else if (frame.contains("\"ERROR\"")) {
                Log.w(TAG, "Discord RPC error frame: $frame")
                if (!clearOnlyMode && supportsExtendedFields) {
                    supportsExtendedFields = false
                    main.post { sendActivity() }
                }
            }
        }

        override fun onClose(code: Int, message: String?) {
            main.post {
                val closedId = connectedAppId
                ready = false
                connection = null
                connectedAppId = 0L

                if (staleAppIdToClearFirst > 0L && closedId == staleAppIdToClearFirst && !clearOnlyMode) {
                    staleAppIdToClearFirst = 0L
                    val nextId = resolveDesiredAppId()
                    if (nextId != null && service != null) {
                        connectServiceWithId(nextId)
                        return@post
                    }
                }

                if (clearOnlyMode) {
                    clearOnlyMode = false
                    pendingClearAppId = 0L
                    finishPendingClearCallbacks()
                    return@post
                }

                if (isMinecraftCatalogId(closedId) &&
                    (code == CLOSE_INVALID_CLIENT_ID ||
                        message?.contains("client", ignoreCase = true) == true ||
                        code != 1000)
                ) {
                    Log.i(TAG, "Discord closed catalog id $closedId (code=$code: $message); falling back")
                    rejectedAppIds.add(closedId)
                    val fallbackId = resolveDesiredAppId()
                    if (fallbackId != null && service != null) {
                        connectServiceWithId(fallbackId)
                    } else {
                        attach(appContext)
                    }
                }
            }
        }
    }

    private fun isMinecraftCatalogId(id: Long): Boolean =
        id == MINECRAFT_PRIMARY_APP_ID || id == MINECRAFT_LEGACY_APP_ID

    private fun resolveDesiredAppId(): Long? {
        val baseId = DiscordAppId.asLong(appContext) ?: return null
        if (inGameMode && AerixPreferences.discordGameProfileMode && !DiscordAppId.hasCustomOverride()) {
            val candidates = listOf(MINECRAFT_PRIMARY_APP_ID, MINECRAFT_LEGACY_APP_ID, baseId)
            return candidates.firstOrNull { it !in rejectedAppIds } ?: baseId
        }
        return baseId
    }

    private fun resolveClearTargetAppId(): Long? {
        if (pendingClearAppId > 0L) return pendingClearAppId
        if (connectedAppId > 0L) return connectedAppId
        val diskId = appContext?.let { readDiskState(it)?.activeAppId } ?: 0L
        if (diskId > 0L) return diskId
        return DiscordAppId.asLong(appContext)
    }

    private fun connectServiceWithId(id: Long) {
        val rpc = service ?: return
        ready = false
        val conn = runCatching { rpc.connect(id, RPC_VERSION, callback) }.getOrNull()
        if (conn != null) {
            connection = conn
            connectedAppId = id
            return
        }
        Log.w(TAG, "Discord refused RPC connection for id $id")
        if (staleAppIdToClearFirst == id) {
            staleAppIdToClearFirst = 0L
            val next = resolveDesiredAppId()
            if (next != null && next != id) {
                connectServiceWithId(next)
            }
            return
        }
        if (!clearOnlyMode && isMinecraftCatalogId(id)) {
            rejectedAppIds.add(id)
            val fallback = resolveDesiredAppId()
            if (fallback != null && fallback != id) {
                connectServiceWithId(fallback)
                return
            }
        }
        if (clearOnlyMode) {
            clearOnlyMode = false
            pendingClearAppId = 0L
            unbind()
            finishPendingClearCallbacks()
        }
    }

    private fun switchConnectionIfNeededOrSend() {
        if (!AerixPreferences.discordPresenceEnabled) return
        val desiredId = resolveDesiredAppId() ?: return
        val ctx = appContext ?: return

        if (!bound || service == null) {
            attach(ctx)
            return
        }

        if (connection != null && connectedAppId == desiredId && staleAppIdToClearFirst == 0L) {
            if (ready) sendActivity()
            return
        }

        if (connection != null && connectedAppId != 0L && connectedAppId != desiredId) {
            // Clear the currently active app ID card before switching so Discord never leaves it stuck!
            if (ready) {
                sendClearFrameImmediate()
                val targetAfterClear = desiredId
                main.postDelayed({
                    ready = false
                    runCatching { connection?.disconnect() }
                    connection = null
                    connectedAppId = 0L
                    staleAppIdToClearFirst = 0L
                    if (service != null) {
                        connectServiceWithId(targetAfterClear)
                    } else {
                        attach(ctx)
                    }
                }, CLEAR_FLUSH_DELAY_MS)
            } else {
                ready = false
                runCatching { connection?.disconnect() }
                connection = null
                connectedAppId = 0L
                connectServiceWithId(desiredId)
            }
            return
        }

        if (connection == null) {
            val target = if (staleAppIdToClearFirst > 0L && staleAppIdToClearFirst != desiredId) {
                staleAppIdToClearFirst
            } else {
                staleAppIdToClearFirst = 0L
                desiredId
            }
            connectServiceWithId(target)
        }
    }

    /* ── cross-process lifecycle hooks ─────────────────────────────────────── */

    /** Captures application Context early in every process (`:launcher` and `:game`). */
    @JvmStatic
    fun captureApplicationContext(context: Context?) {
        if (context != null) {
            appContext = context.applicationContext
        }
    }

    /** Called from LauncherActivity.onStart() in `:launcher`. */
    @JvmStatic
    fun onLauncherStarted(context: Context?) {
        val app = context?.applicationContext ?: return
        appContext = app
        handoffToGameInProgress = false
        AerixPreferences.init(app)
        clearOnlyMode = false
        pendingClearAppId = 0L
        main.removeCallbacks(postExitClearRunnable)
        main.removeCallbacks(clearBindTimeoutRunnable)
        cancelClearAlarm(app)

        val previousDisk = readDiskState(app)
        inGameMode = false
        activeModpackIconUrl = null
        if (details.startsWith("Playing Minecraft")) {
            details = InLauncher.DETAILS
            state = InLauncher.HOME
            startedAt = System.currentTimeMillis()
        } else if (startedAt == 0L) {
            startedAt = System.currentTimeMillis()
        }

        val desired = resolveDesiredAppId() ?: 0L
        if (previousDisk != null &&
            previousDisk.activeAppId > 0L &&
            desired > 0L &&
            previousDisk.activeAppId != desired &&
            connectedAppId != previousDisk.activeAppId
        ) {
            staleAppIdToClearFirst = previousDisk.activeAppId
        }

        writeDiskState(
            app,
            PresenceDiskState(
                owner = "launcher",
                exitedAtElapsedMs = 0L,
                details = details,
                state = state,
                startedAt = startedAt,
                activeAppId = if (connectedAppId > 0L) connectedAppId else desired,
                modpackIconUrl = null
            )
        )
        main.post { switchConnectionIfNeededOrSend() }
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
                startedAt = startedAt,
                activeAppId = if (connectedAppId > 0L) connectedAppId else (disk?.activeAppId ?: 0L),
                modpackIconUrl = activeModpackIconUrl
            )
        )
        schedulePostExitClearInOwnerProcess(app, POST_EXIT_TIMEOUT_MS)
    }

    /**
     * Called right before `:launcher` kills its own PID in ContextAwareDoneListener to launch
     * GameActivity in `:game`. If Hybrid Game Profile mode will switch to Minecraft's catalog ID,
     * sends an immediate clear on the Aerix Launcher card before detaching.
     */
    @JvmStatic
    fun prepareHandoffToGame(context: Context?) {
        handoffToGameInProgress = true
        val app = context?.applicationContext ?: appContext
        if (app != null) {
            AerixPreferences.init(app)
            main.removeCallbacks(postExitClearRunnable)
            cancelClearAlarm(app)
            if (AerixPreferences.discordGameProfileMode && !DiscordAppId.hasCustomOverride() && ready) {
                sendClearFrameImmediate()
                readDiskState(app)?.let { d ->
                    writeDiskState(app, d.copy(activeAppId = 0L))
                }
            }
        }
        detach()
    }

    /** Called from GameActivity (`initLayout` and `onStart`) in `:game`. */
    @JvmStatic
    @JvmOverloads
    fun onGameStarted(
        context: Context?,
        instanceName: String?,
        minecraftVersion: String?,
        modpackIconUrl: String? = null
    ) {
        val app = context?.applicationContext ?: return
        appContext = app
        if (!instanceName.isNullOrBlank()) gameInstanceCache = instanceName
        if (!minecraftVersion.isNullOrBlank()) gameVersionCache = minecraftVersion
        if (!modpackIconUrl.isNullOrBlank()) gameModpackIconCache = modpackIconUrl
        if (gameStartedAtCache == 0L) gameStartedAtCache = System.currentTimeMillis()

        val effInstance = gameInstanceCache
        val effVer = gameVersionCache
        val effIcon = gameModpackIconCache
        val gameDetails = "Playing Minecraft${if (effVer.isNullOrBlank()) "" else " $effVer"}"
        val gameState = effInstance?.takeIf { it.isNotBlank() } ?: "In game"
        val prevActiveId = readDiskState(app)?.activeAppId ?: 0L

        writeDiskState(
            app,
            PresenceDiskState(
                owner = "game",
                exitedAtElapsedMs = 0L,
                details = gameDetails,
                state = gameState,
                startedAt = gameStartedAtCache,
                activeAppId = prevActiveId,
                modpackIconUrl = effIcon
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
                putExtra(EXTRA_MODPACK_ICON_URL, effIcon)
                putExtra(EXTRA_STARTED_AT, gameStartedAtCache)
            }
            app.sendBroadcast(intent)
        }
    }

    /** Called from GameActivity.onStart() when resuming an already-running game session. */
    @JvmStatic
    fun onGameResumed(context: Context?) {
        if (gameInstanceCache != null || gameVersionCache != null || gameStartedAtCache != 0L) {
            onGameStarted(context, gameInstanceCache, gameVersionCache, gameModpackIconCache)
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
                startedAt = disk?.startedAt ?: gameStartedAtCache,
                activeAppId = disk?.activeAppId ?: connectedAppId,
                modpackIconUrl = disk?.modpackIconUrl ?: gameModpackIconCache
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
    @JvmStatic
    @JvmOverloads
    fun handleGameStartedBroadcast(
        context: Context,
        instanceName: String?,
        minecraftVersion: String?,
        gameStartedAt: Long,
        modpackIconUrl: String? = null
    ) {
        val app = context.applicationContext
        appContext = app
        AerixPreferences.init(app)
        clearOnlyMode = false
        pendingClearAppId = 0L
        main.removeCallbacks(postExitClearRunnable)
        main.removeCallbacks(clearBindTimeoutRunnable)
        cancelClearAlarm(app)

        val prevDisk = readDiskState(app)
        inGameMode = true
        activeModpackIconUrl = modpackIconUrl?.takeIf { it.isNotBlank() } ?: prevDisk?.modpackIconUrl
        details = "Playing Minecraft${if (minecraftVersion.isNullOrBlank()) "" else " $minecraftVersion"}"
        state = instanceName?.takeIf { it.isNotBlank() } ?: "In game"
        startedAt = if (gameStartedAt > 0L) gameStartedAt else System.currentTimeMillis()

        val desired = resolveDesiredAppId() ?: 0L
        val prevActive = prevDisk?.activeAppId ?: 0L
        if (prevActive > 0L && desired > 0L && prevActive != desired && connectedAppId != prevActive) {
            staleAppIdToClearFirst = prevActive
        }

        writeDiskState(
            app,
            PresenceDiskState(
                owner = "game",
                exitedAtElapsedMs = 0L,
                details = details,
                state = state,
                startedAt = startedAt,
                activeAppId = if (connectedAppId > 0L) connectedAppId else desired,
                modpackIconUrl = activeModpackIconUrl
            )
        )

        if (!AerixPreferences.discordPresenceEnabled) return
        main.post { switchConnectionIfNeededOrSend() }
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
        if (resolveDesiredAppId() == null) return
        clearOnlyMode = false
        pendingClearAppId = 0L

        if (bound && service != null) {
            main.post { switchConnectionIfNeededOrSend() }
            return
        }
        if (bound && service == null) {
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
        connectedAppId = 0L
        service = null
        unbind()
    }

    /**
     * Explicitly sends a Social SDK clear `SET_ACTIVITY` frame on the active application ID,
     * flushes over Binder, and then disconnects and unbinds so Discord removes the Rich Presence
     * card cleanly instead of keeping an orphaned session alive for ~20 minutes.
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
                    pendingClearAppId = 0L
                    appContext?.let { a ->
                        readDiskState(a)?.let { d ->
                            writeDiskState(a, d.copy(activeAppId = 0L))
                        }
                    }
                    detach()
                    finishPendingClearCallbacks()
                }, CLEAR_FLUSH_DELAY_MS)
                return@post
            }

            // Cold wakeup (e.g. from 1-min AlarmManager): bind briefly to the exact activeAppId
            // that published the card so READY flushes the clear frame on that app ID.
            val ctx = appContext
            val targetId = resolveClearTargetAppId()
            if (ctx == null || targetId == null || targetId <= 0L) {
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
            pendingClearAppId = targetId
            bound = runCatching {
                ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
            if (!bound) {
                clearOnlyMode = false
                pendingClearAppId = 0L
                finishPendingClearCallbacks()
            } else {
                main.removeCallbacks(clearBindTimeoutRunnable)
                main.postDelayed(clearBindTimeoutRunnable, CLEAR_BIND_TIMEOUT_MS)
            }
        }
    }

    /** Re-evaluates desired app ID & card fields immediately when Settings toggles change. */
    fun syncSettingsChanged(context: Context?) {
        val app = context?.applicationContext ?: appContext ?: return
        appContext = app
        AerixPreferences.init(app)
        rejectedAppIds.clear()
        supportsExtendedFields = true
        if (!AerixPreferences.discordPresenceEnabled) {
            clearAndDetach(app)
            return
        }
        main.post { switchConnectionIfNeededOrSend() }
    }

    fun refresh(context: Context?) {
        clearOnlyMode = false
        pendingClearAppId = 0L
        rejectedAppIds.clear()
        supportsExtendedFields = true
        syncSettingsChanged(context)
    }

    /* ── what the profile says ─────────────────────────────────────────────── */

    /** Launcher pages: the same two lines for every screen, with the section in the second one. */
    fun inLauncher(section: String?) {
        clearOnlyMode = false
        inGameMode = false
        activeModpackIconUrl = null
        details = InLauncher.DETAILS
        state = InLauncher.stateFor(section)
        if (startedAt == 0L) startedAt = System.currentTimeMillis()
        appContext?.let { app ->
            val disk = readDiskState(app)
            if (disk == null || disk.owner == "launcher") {
                writeDiskState(
                    app,
                    PresenceDiskState(
                        owner = "launcher",
                        exitedAtElapsedMs = 0L,
                        details = details,
                        state = state,
                        startedAt = startedAt,
                        activeAppId = if (connectedAppId > 0L) connectedAppId else (resolveDesiredAppId() ?: 0L),
                        modpackIconUrl = null
                    )
                )
            }
        }
        main.post { switchConnectionIfNeededOrSend() }
    }

    /** A game is starting or running: the instance takes over the card. */
    fun playing(instance: String?, minecraftVersion: String?, modpackIconUrl: String? = null) {
        clearOnlyMode = false
        inGameMode = true
        activeModpackIconUrl = modpackIconUrl?.takeIf { it.isNotBlank() }
        details = "Playing Minecraft${if (minecraftVersion.isNullOrBlank()) "" else " $minecraftVersion"}"
        state = instance?.takeIf { it.isNotBlank() } ?: "In game"
        startedAt = System.currentTimeMillis()
        main.post { switchConnectionIfNeededOrSend() }
    }

    /** Remove the card, e.g. when the setting is switched off. */
    fun clear() {
        send(buildClearFrame())
    }

    /* ── the RPC frames ────────────────────────────────────────────────────── */

    /**
     * Discord's Social SDK clear format: `args` contains only `pid` with `"activity"` omitted
     * (`{"cmd":"SET_ACTIVITY","nonce":"...","args":{"pid":1}}`).
     */
    private fun buildClearFrame(): String =
        JSONObject()
            .put("cmd", "SET_ACTIVITY")
            .put("nonce", UUID.randomUUID().toString())
            .put("args", JSONObject().put("pid", HANDLER_PID))
            .toString()

    private fun sendClearFrameImmediate() {
        val target = connection ?: return
        val frame = buildClearFrame()
        runCatching { target.sendFrame(frame) }
    }

    private fun isValidHttpAssetUrl(url: String?): Boolean {
        if (url.isNullOrBlank() || url.length > MAX_ASSET_URL_LEN) return false
        return url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("http://", ignoreCase = true)
    }

    private fun sendActivity() {
        if (clearOnlyMode || !ready || connection == null) return
        val usingMinecraftProfile = isMinecraftCatalogId(connectedAppId)
        val clickable = AerixPreferences.discordClickableLinks

        val cardName = if (usingMinecraftProfile) "Minecraft" else "Aerix Launcher"
        // status_display_type: 0 = Name ("Playing Minecraft"), 2 = Details ("In the launcher" / "Playing Minecraft 1.21")
        val statusDisplayType = if (usingMinecraftProfile) 0 else 2
        val effectiveDetails = if (usingMinecraftProfile && !details.contains("Aerix", ignoreCase = true)) {
            "$details · via Aerix Launcher"
        } else {
            details
        }

        val activity = JSONObject()
            .put("name", cardName)
            .put("type", 0) // PLAYING
            .put("status_display_type", statusDisplayType)
            .put("details", effectiveDetails)
            .put("state", state)
            .put("timestamps", JSONObject().put("start", startedAt.coerceAtLeast(1L)))

        // Live Aerix Social Party size `(current of max)` when in an active party!
        if (supportsExtendedFields) {
            val activeParty = runCatching {
                SocialBackend.parties.firstOrNull { !it.isExpired() }
            }.getOrNull()
            if (activeParty != null && activeParty.id.isNotBlank()) {
                val currentSize = activeParty.memberCount.coerceAtLeast(1)
                val maxSize = activeParty.maxMembers.coerceAtLeast(currentSize).coerceAtLeast(2)
                val sizeArr = JSONArray().put(currentSize).put(maxSize)
                activity.put(
                    "party",
                    JSONObject()
                        .put("id", "aerix_${activeParty.id.take(48)}")
                        .put("size", sizeArr)
                )
            }
        }

        if (clickable) {
            activity.put("details_url", AERIX_WEBSITE_URL)
            activity.put("state_url", AERIX_DISCORD_URL)

            val assets = JSONObject()
            val validPackIcon = activeModpackIconUrl?.takeIf { inGameMode && isValidHttpAssetUrl(it) }
            if (validPackIcon != null) {
                assets.put("large_image", validPackIcon)
                assets.put("large_text", "$state · Aerix Launcher")
                assets.put("large_url", AERIX_WEBSITE_URL)
                assets.put("small_image", AERIX_LOGO_URL)
                assets.put("small_text", "Aerix Launcher — Tap to join Discord")
                assets.put("small_url", AERIX_DISCORD_URL)
            } else if (usingMinecraftProfile) {
                assets.put("large_image", AERIX_LOGO_URL)
                assets.put("large_text", "Aerix Launcher — Minecraft Java Edition on Android")
                assets.put("large_url", AERIX_WEBSITE_URL)
                assets.put("small_image", AERIX_LOGO_URL)
                assets.put("small_text", "Powered by Aerix Launcher")
                assets.put("small_url", AERIX_DISCORD_URL)
            } else {
                assets.put("large_image", AERIX_LOGO_URL)
                assets.put("large_text", "Aerix Launcher — Official Website & Info")
                assets.put("large_url", AERIX_WEBSITE_URL)
            }
            activity.put("assets", assets)

            if (supportsExtendedFields) {
                val buttons = JSONArray()
                    .put(
                        JSONObject()
                            .put("label", "About Aerix Launcher")
                            .put("url", AERIX_WEBSITE_URL)
                    )
                    .put(
                        JSONObject()
                            .put("label", "Join Aerix Discord")
                            .put("url", AERIX_DISCORD_URL)
                    )
                activity.put("buttons", buttons)
            }
        }

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
                .put("activeAppId", stateObj.activeAppId)
                .put("modpackIconUrl", stateObj.modpackIconUrl ?: "")
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
            startedAt = json.optLong("startedAt", 0L),
            activeAppId = json.optLong("activeAppId", 0L),
            modpackIconUrl = json.optString("modpackIconUrl", "").takeIf { it.isNotBlank() }
        )
    }.getOrNull()

    private fun resolveAnyContext(): Context? = appContext

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
        const val AERIX_CLIENT = "Configuring Aerix Client 1.16.5"
        const val GENERIC = "Browsing"

        fun stateFor(section: String?): String = when (section?.uppercase()) {
            null, "HOME" -> HOME
            "AERIX_CLIENT" -> AERIX_CLIENT
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
    fun hasCustomOverride(): Boolean =
        AerixPreferences.discordAppId.trim().trim('"', '\'').length >= 15

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
