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

import android.app.Activity
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
import net.kdt.pojavlaunch.instances.Instance
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
 * 3. **Exit cleanup**:
 *    - Keeps launcher (and backgrounded game) Rich Presence alive for a 5-minute grace period
 *      when switching apps so Discord still shows `"In the launcher"` after stepping away for a
 *      few minutes, then sends an explicit clear before disconnecting (without an AlarmManager
 *      cold-boot wakeup). Explicit game exit or swiping from Recents clears immediately.
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
        "https://raw.githubusercontent.com/entitybrian69-bit/Aerix-launcher/4a3b1c37a8d4838eaa5cb8933610db7fb6efdec5/assets/aerix-logo.png"

    /** Verified Discord catalog IDs for Minecraft's native Game Profile sheet. */
    const val MINECRAFT_PRIMARY_APP_ID = 1402418491272986635L
    const val MINECRAFT_LEGACY_APP_ID = 356875570916753438L

    private const val CLEAR_FLUSH_DELAY_MS = 400L
    private const val CLEAR_BIND_TIMEOUT_MS = 5_500L
    internal const val LAUNCHER_EXIT_GRACE_MS = 5 * 60_000L
    internal const val GAME_EXIT_GRACE_MS = 5 * 60_000L
    private const val RECONNECT_DELAY_MS = 5_000L
    private const val MAX_RECONNECT_ATTEMPTS = 2
    private const val PENDING_INTENT_REQUEST_CLEAR = 94120
    private const val STATE_FILE_NAME = "aerix_discord_presence.json"

    const val ACTION_DISCORD_GAME_STARTED = "net.kdt.pojavlaunch.aerix.ACTION_DISCORD_GAME_STARTED"
    const val ACTION_DISCORD_GAME_UPDATE = "net.kdt.pojavlaunch.aerix.ACTION_DISCORD_GAME_UPDATE"
    const val ACTION_DISCORD_SCHEDULE_CLEAR = "net.kdt.pojavlaunch.aerix.ACTION_DISCORD_SCHEDULE_CLEAR"
    const val ACTION_DISCORD_CLEAR_NOW = "net.kdt.pojavlaunch.aerix.ACTION_DISCORD_CLEAR_NOW"

    const val EXTRA_INSTANCE_NAME = "extra_instance_name"
    const val EXTRA_MC_VERSION = "extra_mc_version"
    const val EXTRA_MODPACK_ICON_URL = "extra_modpack_icon_url"
    const val EXTRA_MODPACK_NAME = "extra_modpack_name"
    const val EXTRA_MODPACK_PAGE_URL = "extra_modpack_page_url"
    const val EXTRA_LOADER_NAME = "extra_loader_name"
    const val EXTRA_MOD_COUNT = "extra_mod_count"
    const val EXTRA_SERVER_ADDRESS = "extra_server_address"
    const val EXTRA_SERVER_NAME = "extra_server_name"
    const val EXTRA_SERVER_ICON_URL = "extra_server_icon_url"
    const val EXTRA_WORLD_NAME = "extra_world_name"
    const val EXTRA_DIMENSION = "extra_dimension"
    const val EXTRA_ACTIVITY_STAGE = "extra_activity_stage"
    const val EXTRA_ACTIVITY_LINE = "extra_activity_line"
    const val EXTRA_IS_RECORDING = "extra_is_recording"
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

    /** True when binding solely to clear the active card and disconnect. */
    @Volatile
    private var clearOnlyMode = false
    @Volatile
    private var pendingClearAppId: Long = 0L
    private var clearOnlyRequiresExit = false
    private val pendingClearCallbacks = mutableListOf<() -> Unit>()
    private val clearGeneration = java.util.concurrent.atomic.AtomicLong()
    private val graceGeneration = java.util.concurrent.atomic.AtomicLong()
    private var graceClearRunnable: Runnable? = null
    private var reconnectRunnable: Runnable? = null
    private var reconnectAttempts = 0

    /** Set right before LauncherActivity kills itself to hand off to GameActivity. */
    @Volatile
    private var handoffToGameInProgress = false

    /** Cached in `:game` process so returning to GameActivity from background restores game card. */
    private var gameInstanceCache: String? = null
    private var gameVersionCache: String? = null
    private var gameModpackIconCache: String? = null
    private var gameMetaCache: GameInstanceMeta? = null
    private var gameStartedAtCache: Long = 0L

    /** Current state published to Discord. */
    private var inGameMode: Boolean = false
    private var activeModpackIconUrl: String? = null
    private var activeModpackName: String? = null
    private var activeModpackPageUrl: String? = null
    private var activeLoaderName: String? = null
    private var activeModCount: Int = 0
    private var activeServerAddress: String? = null
    private var activeServerName: String? = null
    private var activeServerIconUrl: String? = null
    private var activeWorldName: String? = null
    private var activeDimension: String? = null
    private var activeActivityLine: String? = null
    private var activeIsRecording: Boolean = false
    private var activeInstanceName: String? = null
    private var activeMcVersion: String? = null
    private var details: String = InLauncher.DETAILS
    private var state: String = InLauncher.HOME
    private var startedAt: Long = 0L
    private var lastSentSignature: String? = null

    private data class PresenceDiskState(
        val owner: String, // "launcher" | "game" | "none"
        val exitedAtElapsedMs: Long,
        val details: String,
        val state: String,
        val startedAt: Long,
        val activeAppId: Long = 0L,
        val modpackIconUrl: String? = null
    )

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
            service = null
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
                        val generation = clearGeneration.get()
                        main.postDelayed({
                            if (generation != clearGeneration.get()) return@postDelayed
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
                        if (clearOnlyRequiresExit && appContext?.let { readDiskState(it)?.owner } != "none") {
                            clearOnlyMode = false
                            pendingClearAppId = 0L
                            finishPendingClearCallbacks()
                            return@post
                        }
                        sendClearFrameImmediate()
                        val generation = clearGeneration.get()
                        main.postDelayed({
                            if (generation != clearGeneration.get()) return@postDelayed
                            if (clearOnlyRequiresExit && appContext?.let { readDiskState(it)?.owner } != "none") {
                                clearOnlyMode = false
                                finishPendingClearCallbacks()
                                return@postDelayed
                            }
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
                        reconnectAttempts = 0
                        cancelPendingReconnect()
                        appContext?.let { app ->
                            val d = readDiskState(app)
                            if (d != null && d.owner != "none" && d.activeAppId != connectedAppId) {
                                writeDiskState(app, d.copy(activeAppId = connectedAppId))
                            }
                        }
                        lastSentSignature = null
                        sendActivity()
                    }
                }
            } else if (frame.contains("\"ERROR\"")) {
                Log.w(TAG, "Discord RPC error frame: $frame")
                if (!clearOnlyMode && supportsExtendedFields) {
                    supportsExtendedFields = false
                    lastSentSignature = null
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
                    return@post
                }

                // If Discord transiently closed the non-fallback session while we still own
                // presence (including during the post-exit grace window), retry up to a small cap.
                val ctx = appContext
                if (ctx != null &&
                    AerixPreferences.discordPresenceEnabled &&
                    code != 1000 &&
                    code != CLOSE_INVALID_CLIENT_ID &&
                    readDiskState(ctx)?.owner != "none" &&
                    reconnectAttempts < MAX_RECONNECT_ATTEMPTS
                ) {
                    reconnectAttempts++
                    scheduleReconnect(ctx)
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
        if (readDiskState(ctx)?.owner == "none") return

        val aliveService = service?.takeIf { it.asBinder()?.isBinderAlive == true }
        if (!bound || aliveService == null) {
            service = null
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
                val generation = clearGeneration.get()
                main.postDelayed({
                    if (generation != clearGeneration.get()) return@postDelayed
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
        reconnectAttempts = 0
        AerixPreferences.init(app)
        invalidatePendingClear()
        cancelClearAlarm(app)

        val previousDisk = readDiskState(app)
        inGameMode = false
        clearInGameFields()
        if (details.startsWith("Playing Minecraft") ||
            details.startsWith("Playing on ") ||
            details.startsWith("Joining ") ||
            details.startsWith("Singleplayer") ||
            details.startsWith("Hosting World Share") ||
            details.startsWith("In Main Menu") ||
            details.startsWith("Launching ") ||
            details.startsWith("Loading world")
        ) {
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
        AerixPreferences.init(app)
        if (handoffToGameInProgress) return
        val disk = readDiskState(app)
        // If `:game` already took ownership, do not schedule an exit from `:launcher`.
        if (disk?.owner == "game") return
        val nowElapsed = SystemClock.elapsedRealtime().coerceAtLeast(1L)
        if (!AerixPreferences.discordPresenceEnabled) {
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
            clearAndDetach(app, onlyIfExited = true)
            return
        }
        // Keep the launcher Rich Presence active for a few minutes after the user leaves
        // the launcher UI, then clear cleanly without scheduling an AlarmManager wakeup.
        writeDiskState(
            app,
            PresenceDiskState(
                owner = "launcher",
                exitedAtElapsedMs = nowElapsed,
                details = details,
                state = state,
                startedAt = startedAt,
                activeAppId = if (connectedAppId > 0L) connectedAppId else (disk?.activeAppId ?: 0L),
                modpackIconUrl = activeModpackIconUrl
            )
        )
        main.post {
            val aliveService = service?.takeIf { it.asBinder()?.isBinderAlive == true }
            if (!bound || aliveService == null) {
                attach(app)
            }
        }
        scheduleGraceClear(app, expectedOwner = "launcher", delayMs = LAUNCHER_EXIT_GRACE_MS)
    }

    /**
     * Called right before `:launcher` kills its own PID in ContextAwareDoneListener to launch
     * GameActivity in `:game`. If Hybrid Game Profile mode will switch to Minecraft's catalog ID,
     * sends an immediate clear on the Aerix Launcher card before detaching.
     */
    @JvmStatic
    fun prepareHandoffToGame(context: Context?) {
        handoffToGameInProgress = true
        cancelGraceClear()
        cancelPendingReconnect()
        val app = context?.applicationContext ?: appContext
        if (app != null) {
            AerixPreferences.init(app)
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

    /** Called from GameActivity (`initLayout` and `onStart`) in `:game` with full [Instance] context. */
    @JvmStatic
    @JvmOverloads
    fun onGameStarted(
        context: Context?,
        instance: Instance?,
        minecraftVersion: String?,
        launchServer: String? = null
    ) {
        val app = context?.applicationContext ?: return
        appContext = app
        val meta = GameDiscordPresenceMonitor.start(app, instance, minecraftVersion, launchServer)
        gameMetaCache = meta
        if (!meta.instanceName.isNullOrBlank()) gameInstanceCache = meta.instanceName
        if (!meta.mcVersion.isNullOrBlank()) gameVersionCache = meta.mcVersion
        if (!meta.modpackIconUrl.isNullOrBlank()) gameModpackIconCache = meta.modpackIconUrl
        if (gameStartedAtCache == 0L) gameStartedAtCache = System.currentTimeMillis()

        val initialActivity = GameDiscordPresenceMonitor.currentStateSnapshot()
        emitGameBroadcast(app, ACTION_DISCORD_GAME_STARTED, meta, initialActivity, writeDiskOwner = true)
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

        val meta = gameMetaCache ?: GameInstanceMeta(
            instanceName = gameInstanceCache,
            mcVersion = SocialBackend.extractMcVersion(gameVersionCache) ?: gameVersionCache,
            loaderName = SocialBackend.detectLoader(gameVersionCache),
            modCount = 0,
            modpackName = null,
            modpackIconUrl = gameModpackIconCache,
            modpackPageUrl = null
        )
        val initialActivity = GameDiscordPresenceMonitor.currentStateSnapshot()
        emitGameBroadcast(app, ACTION_DISCORD_GAME_STARTED, meta, initialActivity, writeDiskOwner = true)
    }

    internal fun sendInGameActivityUpdate(
        context: Context,
        meta: GameInstanceMeta,
        activityState: LiveGameActivityState
    ) {
        val app = context.applicationContext
        if (readDiskState(app)?.owner != "game") return
        emitGameBroadcast(app, ACTION_DISCORD_GAME_UPDATE, meta, activityState, writeDiskOwner = false)
    }

    private fun emitGameBroadcast(
        app: Context,
        actionName: String,
        meta: GameInstanceMeta,
        activityState: LiveGameActivityState,
        writeDiskOwner: Boolean
    ) {
        val formatted = formatInGamePresence(meta, activityState)
        if (writeDiskOwner) {
            val prevActiveId = readDiskState(app)?.activeAppId ?: 0L
            writeDiskState(
                app,
                PresenceDiskState(
                    owner = "game",
                    exitedAtElapsedMs = 0L,
                    details = formatted.details,
                    state = formatted.state,
                    startedAt = gameStartedAtCache,
                    activeAppId = prevActiveId,
                    modpackIconUrl = meta.modpackIconUrl
                )
            )
            cancelClearAlarm(app)
        }

        // Delegate RPC connection ownership to `:launcher` so `:game` Process.killProcess()
        // never orphans a dead Binder inside Discord.
        runCatching {
            val intent = Intent(app, SocialNotificationReceiver::class.java).apply {
                action = actionName
                putExtra(EXTRA_INSTANCE_NAME, meta.instanceName)
                putExtra(EXTRA_MC_VERSION, meta.mcVersion)
                putExtra(EXTRA_MODPACK_ICON_URL, meta.modpackIconUrl)
                putExtra(EXTRA_MODPACK_NAME, meta.modpackName)
                putExtra(EXTRA_MODPACK_PAGE_URL, meta.modpackPageUrl)
                putExtra(EXTRA_LOADER_NAME, meta.loaderName)
                putExtra(EXTRA_MOD_COUNT, meta.modCount)
                putExtra(EXTRA_SERVER_ADDRESS, activityState.serverAddress)
                putExtra(EXTRA_SERVER_NAME, activityState.displayServerName())
                putExtra(EXTRA_SERVER_ICON_URL, activityState.serverIconUrl)
                putExtra(EXTRA_WORLD_NAME, activityState.worldName)
                putExtra(EXTRA_DIMENSION, activityState.dimension)
                putExtra(EXTRA_ACTIVITY_STAGE, activityState.stage.name)
                putExtra(EXTRA_ACTIVITY_LINE, activityState.activityLine())
                putExtra(EXTRA_IS_RECORDING, activityState.isRecording)
                putExtra(EXTRA_STARTED_AT, gameStartedAtCache)
            }
            app.sendBroadcast(intent)
        }
    }

    /** Called from GameActivity.onStart() when resuming an already-running game session. */
    @JvmStatic
    fun onGameResumed(context: Context?) {
        val app = context?.applicationContext ?: return
        val cachedMeta = gameMetaCache
        if (cachedMeta != null) {
            if (gameStartedAtCache == 0L) gameStartedAtCache = System.currentTimeMillis()
            GameDiscordPresenceMonitor.resume()
            emitGameBroadcast(
                app,
                ACTION_DISCORD_GAME_STARTED,
                cachedMeta,
                GameDiscordPresenceMonitor.currentStateSnapshot(),
                writeDiskOwner = true
            )
            return
        }
        if (gameInstanceCache != null || gameVersionCache != null || gameStartedAtCache != 0L) {
            onGameStarted(context, gameInstanceCache, gameVersionCache, gameModpackIconCache)
        }
    }

    /** Called from GameActivity.onStop() in `:game`. */
    @JvmStatic
    fun onGameStopped(context: Context?) {
        val isFinishingOrDestroyed = (context as? Activity)?.let { it.isFinishing || it.isDestroyed } == true
        onGameStoppedInternal(context, immediate = isFinishingOrDestroyed)
    }

    private fun onGameStoppedInternal(context: Context?, immediate: Boolean) {
        val app = context?.applicationContext ?: appContext ?: resolveAnyContext() ?: return
        appContext = app
        GameDiscordPresenceMonitor.pause()
        val disk = readDiskState(app)
        if (disk?.owner == "launcher") return

        if (disk?.owner != "game") return
        val nowElapsed = SystemClock.elapsedRealtime().coerceAtLeast(1L)
        if (immediate) {
            writeDiskState(app, disk.copy(owner = "none", exitedAtElapsedMs = nowElapsed))
            runCatching {
                app.sendBroadcast(Intent(app, SocialNotificationReceiver::class.java).apply {
                    action = ACTION_DISCORD_SCHEDULE_CLEAR
                    putExtra(EXTRA_DELAY_MS, 0L)
                })
            }
        } else {
            // Game went to background without finishing; keep presence alive during grace period.
            writeDiskState(app, disk.copy(owner = "game", exitedAtElapsedMs = nowElapsed))
            runCatching {
                app.sendBroadcast(Intent(app, SocialNotificationReceiver::class.java).apply {
                    action = ACTION_DISCORD_SCHEDULE_CLEAR
                    putExtra(EXTRA_DELAY_MS, GAME_EXIT_GRACE_MS)
                })
            }
        }
    }

    /** Called from GameActivity.onDestroy() in `:game`. */
    @JvmStatic
    fun onGameDestroyed(context: Context?) {
        GameDiscordPresenceMonitor.stop()
        onGameStoppedInternal(context, immediate = true)
    }

    /**
     * Called right before Process.killProcess() in Tools.fullyExit() or GameService exit paths,
     * attempting exit cleanup even when onStop/onDestroy are skipped.
     */
    @JvmStatic
    fun onProcessExiting(context: Context? = null) {
        val app = context?.applicationContext ?: appContext ?: resolveAnyContext() ?: return
        onGameStoppedInternal(app, immediate = true)
    }

    /**
     * Invoked when the user explicitly swipes/removes Aerix from Recent Apps so Discord Rich
     * Presence clears immediately and no delayed wakeup alarm brings the process back later.
     */
    @JvmStatic
    fun onAppSwipedFromRecents(context: Context?, onComplete: (() -> Unit)? = null) {
        val app = context?.applicationContext ?: appContext ?: resolveAnyContext() ?: return
        appContext = app
        cancelGraceClear()
        cancelPendingReconnect()
        cancelClearAlarm(app)
        // Preserve the ID that actually published the card for a cold Discord bind.
        val disk = readDiskState(app)
        if (disk != null) writeDiskState(app, disk.copy(owner = "none", exitedAtElapsedMs = SystemClock.elapsedRealtime()))
        clearAndDetach(app, onComplete)
    }

    /** Invoked in `:launcher` when SocialNotificationReceiver receives ACTION_DISCORD_GAME_STARTED or UPDATE. */
    @JvmStatic
    @JvmOverloads
    fun handleGameStartedBroadcast(
        context: Context,
        instanceName: String?,
        minecraftVersion: String?,
        gameStartedAt: Long,
        modpackIconUrl: String? = null
    ) {
        val intent = Intent().apply {
            putExtra(EXTRA_INSTANCE_NAME, instanceName)
            putExtra(EXTRA_MC_VERSION, minecraftVersion)
            putExtra(EXTRA_MODPACK_ICON_URL, modpackIconUrl)
            putExtra(EXTRA_STARTED_AT, gameStartedAt)
        }
        handleGameBroadcast(context, intent, isUpdate = false)
    }

    @JvmStatic
    fun handleGameBroadcast(context: Context, intent: Intent, isUpdate: Boolean) {
        val app = context.applicationContext
        appContext = app
        AerixPreferences.init(app)
        val prevDisk = readDiskState(app) ?: return
        val gameStartedAt = intent.getLongExtra(EXTRA_STARTED_AT, 0L)
        // A queued start/update broadcast must not revive a game that has already exited.
        if (prevDisk.owner != "game" || (gameStartedAt > 0L && prevDisk.startedAt != gameStartedAt)) return
        invalidatePendingClear()
        cancelClearAlarm(app)
        inGameMode = true

        val instanceName = intent.getStringExtra(EXTRA_INSTANCE_NAME)
        val mcVersion = intent.getStringExtra(EXTRA_MC_VERSION)
        val modpackIconUrl = intent.getStringExtra(EXTRA_MODPACK_ICON_URL)
        val modpackName = intent.getStringExtra(EXTRA_MODPACK_NAME)
        val modpackPageUrl = intent.getStringExtra(EXTRA_MODPACK_PAGE_URL)
        val loaderName = intent.getStringExtra(EXTRA_LOADER_NAME)
        val modCount = intent.getIntExtra(EXTRA_MOD_COUNT, 0)
        val serverAddress = intent.getStringExtra(EXTRA_SERVER_ADDRESS)
        val serverName = intent.getStringExtra(EXTRA_SERVER_NAME)
        val serverIconUrl = intent.getStringExtra(EXTRA_SERVER_ICON_URL)
        val worldName = intent.getStringExtra(EXTRA_WORLD_NAME)
        val dimension = intent.getStringExtra(EXTRA_DIMENSION)
        val stageName = intent.getStringExtra(EXTRA_ACTIVITY_STAGE)
        val activityLine = intent.getStringExtra(EXTRA_ACTIVITY_LINE)
        val isRecording = intent.getBooleanExtra(EXTRA_IS_RECORDING, false)

        activeInstanceName = instanceName?.takeIf { it.isNotBlank() }
        activeMcVersion = mcVersion?.takeIf { it.isNotBlank() }
        activeModpackIconUrl = modpackIconUrl?.takeIf { it.isNotBlank() } ?: prevDisk.modpackIconUrl
        activeModpackName = modpackName?.takeIf { it.isNotBlank() }
        activeModpackPageUrl = modpackPageUrl?.takeIf { it.isNotBlank() }
        activeLoaderName = loaderName?.takeIf { it.isNotBlank() }
        activeModCount = modCount
        activeServerAddress = serverAddress?.takeIf { it.isNotBlank() }
        activeServerName = serverName?.takeIf { it.isNotBlank() }
        activeServerIconUrl = serverIconUrl?.takeIf { it.isNotBlank() }
        activeWorldName = worldName?.takeIf { it.isNotBlank() }
        activeDimension = dimension?.takeIf { it.isNotBlank() }
        activeActivityLine = activityLine?.takeIf { it.isNotBlank() }
        activeIsRecording = isRecording

        val meta = GameInstanceMeta(
            instanceName = activeInstanceName,
            mcVersion = activeMcVersion,
            loaderName = activeLoaderName,
            modCount = activeModCount,
            modpackName = activeModpackName,
            modpackIconUrl = activeModpackIconUrl,
            modpackPageUrl = activeModpackPageUrl
        )
        val stage = runCatching {
            stageName?.let { GameActivityStage.valueOf(it) }
        }.getOrNull() ?: GameActivityStage.LAUNCHING
        val activitySnapshot = LiveGameActivityState(
            stage = stage,
            serverAddress = activeServerAddress,
            serverName = activeServerName,
            serverIconUrl = activeServerIconUrl,
            worldName = activeWorldName,
            dimension = activeDimension,
            isRecording = activeIsRecording
        )
        val formatted = formatInGamePresence(meta, activitySnapshot)
        details = formatted.details
        state = formatted.state
        startedAt = if (gameStartedAt > 0L) gameStartedAt else System.currentTimeMillis()

        val desired = resolveDesiredAppId() ?: 0L
        val prevActive = prevDisk.activeAppId
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

    /** The game has exited or entered background; clear immediately or after [delayMs] grace period. */
    fun handleScheduleClearBroadcast(context: Context, onComplete: () -> Unit) {
        handleScheduleClearBroadcast(context, delayMs = 0L, onComplete = onComplete)
    }

    fun handleScheduleClearBroadcast(context: Context, delayMs: Long, onComplete: () -> Unit = {}) {
        val app = context.applicationContext
        appContext = app
        AerixPreferences.init(app)
        val disk = readDiskState(app)
        if (delayMs > 0L) {
            if (disk?.owner == "game" && disk.exitedAtElapsedMs > 0L && AerixPreferences.discordPresenceEnabled) {
                scheduleGraceClear(app, expectedOwner = "game", delayMs = delayMs)
            } else if (disk?.owner == "none") {
                clearAndDetach(app, onlyIfExited = true)
            }
            onComplete()
            return
        }
        cancelGraceClear()
        if (disk?.owner != "none") {
            onComplete()
            return
        }
        clearAndDetach(app, onComplete, onlyIfExited = true)
    }

    /** Handles an alarm from an older build without scheduling another background wake. */
    fun performScheduledClear(context: Context?, onComplete: (() -> Unit)?) {
        val app = context?.applicationContext ?: appContext
        if (app == null || readDiskState(app)?.owner != "none") {
            onComplete?.invoke()
            return
        }
        clearAndDetach(app, onComplete, onlyIfExited = true)
    }

    private fun cancelGraceClear() {
        graceGeneration.incrementAndGet()
        graceClearRunnable?.let { main.removeCallbacks(it) }
        graceClearRunnable = null
    }

    private fun cancelPendingReconnect() {
        reconnectRunnable?.let { main.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun scheduleReconnect(app: Context) {
        cancelPendingReconnect()
        val runnable = Runnable {
            reconnectRunnable = null
            if (!AerixPreferences.discordPresenceEnabled) return@Runnable
            if (clearOnlyMode) return@Runnable
            if (readDiskState(app)?.owner == "none") return@Runnable
            attach(app)
        }
        reconnectRunnable = runnable
        main.postDelayed(runnable, RECONNECT_DELAY_MS)
    }

    private fun scheduleGraceClear(app: Context, expectedOwner: String, delayMs: Long) {
        cancelGraceClear()
        if (delayMs <= 0L) {
            val disk = readDiskState(app)
            if (disk == null || disk.owner == expectedOwner || disk.owner == "none") {
                if (disk != null && disk.owner != "none") {
                    writeDiskState(app, disk.copy(owner = "none", exitedAtElapsedMs = SystemClock.elapsedRealtime()))
                }
                clearAndDetach(app, onlyIfExited = true)
            }
            return
        }
        val token = graceGeneration.get()
        val runnable = Runnable {
            if (token != graceGeneration.get()) return@Runnable
            graceClearRunnable = null
            val disk = readDiskState(app) ?: return@Runnable
            if (disk.owner != expectedOwner) return@Runnable
            if (disk.exitedAtElapsedMs <= 0L) return@Runnable
            val elapsedAway = SystemClock.elapsedRealtime() - disk.exitedAtElapsedMs
            val remaining = delayMs - elapsedAway
            if (remaining > 250L) {
                scheduleGraceClear(app, expectedOwner, remaining)
                return@Runnable
            }
            writeDiskState(app, disk.copy(owner = "none"))
            clearAndDetach(app, onlyIfExited = true)
        }
        graceClearRunnable = runnable
        main.postDelayed(runnable, delayMs)
    }

    private fun invalidatePendingClear() {
        clearGeneration.incrementAndGet()
        cancelGraceClear()
        cancelPendingReconnect()
        clearOnlyMode = false
        pendingClearAppId = 0L
        main.removeCallbacks(clearBindTimeoutRunnable)
        main.post { finishPendingClearCallbacks() }
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

        val aliveService = service?.takeIf { it.asBinder()?.isBinderAlive == true }
        if (bound && aliveService != null) {
            main.post { switchConnectionIfNeededOrSend() }
            return
        }
        if (bound && aliveService == null) {
            service = null
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
        cancelPendingReconnect()
        ready = false
        lastSentSignature = null
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
    fun clearAndDetach(context: Context? = appContext, onComplete: (() -> Unit)? = null, onlyIfExited: Boolean = false) {
        cancelGraceClear()
        cancelPendingReconnect()
        val app = context?.applicationContext ?: appContext
        if (app != null) {
            appContext = app
            cancelClearAlarm(app)
        }
        startedAt = 0L
        val generation = clearGeneration.incrementAndGet()

        main.post {
            if (generation != clearGeneration.get() ||
                (onlyIfExited && appContext?.let { readDiskState(it)?.owner } != "none")) {
                finishPendingClearCallbacks()
                onComplete?.invoke()
                return@post
            }
            if (onComplete != null) {
                pendingClearCallbacks.add(onComplete)
            }
            if (connection != null && ready) {
                sendClearFrameImmediate()
                main.postDelayed({
                    if (generation != clearGeneration.get()) return@postDelayed
                    if (onlyIfExited && appContext?.let { readDiskState(it)?.owner } != "none") {
                        finishPendingClearCallbacks()
                        return@postDelayed
                    }
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

            // Bind briefly to the app ID that published the card when no connection is ready.
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
            clearOnlyRequiresExit = onlyIfExited
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
        lastSentSignature = null
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

    /**
     * The specific thing the player is doing in the launcher, when the shell knows it: the pack
     * whose page they are reading, the modpack they are searching for, the instance they are
     * building. Reported by [net.kdt.pojavlaunch.aerix.social.SocialBackend.reportLauncherActivity],
     * which every screen already calls for the friends list - so the card and the friends list can
     * never say two different things, and a screen that reports nothing falls back to the page.
     */
    @Volatile private var launcherActivityDetail: String? = null
    @Volatile private var lastLauncherSection: String? = null

    /** @param line what they are doing, or null to go back to the plain page name. */
    fun launcherActivity(line: String?) {
        val detail = line?.trim()?.takeIf { it.isNotEmpty() }?.take(56)
        if (detail == launcherActivityDetail) return
        launcherActivityDetail = detail
        inLauncher(lastLauncherSection)
    }

    /** Launcher pages: the section in the second line, or the activity when one is known. */
    fun inLauncher(section: String?) {
        // Ignore late Compose updates after leaving, which would otherwise republish the card.
        val disk = appContext?.let { readDiskState(it) }
        if (disk != null && (disk.owner != "launcher" || disk.exitedAtElapsedMs > 0L)) return
        clearOnlyMode = false
        inGameMode = false
        clearInGameFields()
        lastLauncherSection = section
        details = InLauncher.DETAILS
        state = launcherActivityDetail ?: InLauncher.stateFor(section)
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
        // The next time the launcher owns the card, it starts from the page name again rather than
        // from whatever they were reading before the game booted.
        launcherActivityDetail = null
        activeModpackIconUrl = modpackIconUrl?.takeIf { it.isNotBlank() }
        details = "Playing Minecraft${if (minecraftVersion.isNullOrBlank()) "" else " $minecraftVersion"}"
        state = instance?.takeIf { it.isNotBlank() } ?: "In game"
        startedAt = System.currentTimeMillis()
        main.post { switchConnectionIfNeededOrSend() }
    }

    private fun clearInGameFields() {
        activeModpackIconUrl = null
        activeModpackName = null
        activeModpackPageUrl = null
        activeLoaderName = null
        activeModCount = 0
        activeServerAddress = null
        activeServerName = null
        activeServerIconUrl = null
        activeWorldName = null
        activeDimension = null
        activeActivityLine = null
        activeIsRecording = false
        activeInstanceName = null
        activeMcVersion = null
        lastSentSignature = null
    }

    internal data class InGameFormatted(val details: String, val state: String)

    internal fun formatInGamePresence(
        meta: GameInstanceMeta,
        activity: LiveGameActivityState
    ): InGameFormatted {
        val loaderVersion = listOfNotNull(
            meta.loaderName?.takeIf { it.isNotBlank() },
            meta.mcVersion?.takeIf { it.isNotBlank() }
        ).joinToString(" ").ifBlank { "Minecraft" }
        val modsSuffix = if (meta.modCount > 0) {
            "${meta.modCount} ${if (meta.modCount == 1) "mod" else "mods"}"
        } else {
            null
        }
        val stackLabel = meta.modpackName?.takeIf { it.isNotBlank() } ?: loaderVersion

        val serverLabel = activity.displayServerName()?.takeIf { it.isNotBlank() }
            ?: activity.serverAddress?.takeIf { it.isNotBlank() }

        val detailsLine = when (activity.stage) {
            GameActivityStage.IN_SERVER, GameActivityStage.CONNECTING_SERVER -> {
                val target = serverLabel ?: "Multiplayer"
                val verb = if (activity.stage == GameActivityStage.CONNECTING_SERVER) {
                    "Joining $target"
                } else {
                    "Playing on $target"
                }
                val suffix = meta.modpackName?.takeIf { !it.equals(target, ignoreCase = true) } ?: loaderVersion
                "$verb · $suffix"
            }
            GameActivityStage.WORLD_SHARE -> "Hosting World Share · $stackLabel"
            GameActivityStage.IN_WORLD -> {
                val world = activity.worldName?.takeIf { it.isNotBlank() }
                    ?.let { "Singleplayer: $it" } ?: "Playing Singleplayer"
                "$world · $stackLabel"
            }
            GameActivityStage.LAUNCHING,
            GameActivityStage.MAIN_MENU,
            GameActivityStage.LOADING_WORLD -> "${activity.activityLine()} · $stackLabel"
        }.take(120)

        val stateParts = ArrayList<String>(4)
        if ((activity.stage == GameActivityStage.IN_SERVER || activity.stage == GameActivityStage.CONNECTING_SERVER) &&
            !activity.serverAddress.isNullOrBlank() &&
            !activity.serverAddress.equals(serverLabel, ignoreCase = true) &&
            !GameDiscordPresenceMonitor.isLocalOrLanAddress(activity.serverAddress)
        ) {
            stateParts += activity.serverAddress
        }
        if (!meta.modpackName.isNullOrBlank()) {
            stateParts += loaderVersion
        }
        if (modsSuffix != null) {
            stateParts += modsSuffix
        }
        if (!activity.dimension.isNullOrBlank() &&
            (activity.stage == GameActivityStage.IN_WORLD || activity.stage == GameActivityStage.IN_SERVER)
        ) {
            stateParts += activity.dimension
        }
        if (activity.isRecording) {
            stateParts += "● Recording"
        }
        if (stateParts.isEmpty()) {
            val fallback = when {
                !meta.instanceName.isNullOrBlank() -> "Instance: ${meta.instanceName}"
                else -> "In game"
            }
            stateParts += fallback
        }
        val stateLine = stateParts.joinToString(" · ").take(120)
        return InGameFormatted(details = detailsLine, state = stateLine)
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
        if (appContext?.let { readDiskState(it)?.owner } == "none") return
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

        val assets = JSONObject()
        val validPackIcon = activeModpackIconUrl?.takeIf { clickable && inGameMode && isValidHttpAssetUrl(it) }
        val validServerIcon = activeServerIconUrl?.takeIf { clickable && inGameMode && isValidHttpAssetUrl(it) }
        val stackCaption = listOfNotNull(
            activeModpackName?.let { "Modpack: $it" },
            listOfNotNull(activeLoaderName, activeMcVersion).joinToString(" ").takeIf { it.isNotBlank() },
            if (activeModCount > 0) "$activeModCount mods" else null
        ).joinToString(" · ").ifBlank { state }

        when {
            validServerIcon != null -> {
                val serverLabel = activeServerName ?: activeServerAddress ?: "Minecraft Server"
                val addrNote = activeServerAddress?.takeIf { !it.equals(serverLabel, true) }?.let { " ($it)" } ?: ""
                assets.put("large_image", validServerIcon)
                assets.put("large_text", "Server: $serverLabel$addrNote · ${activeActivityLine ?: "Playing Multiplayer"}".take(128))
                if (validPackIcon != null) {
                    assets.put("small_image", validPackIcon)
                    assets.put("small_text", "$stackCaption · via Aerix Launcher".take(128))
                } else {
                    assets.put("small_image", AERIX_LOGO_URL)
                    assets.put("small_text", "Aerix Launcher · $stackCaption".take(128))
                }
            }
            validPackIcon != null -> {
                assets.put("large_image", validPackIcon)
                assets.put("large_text", "$stackCaption · ${activeActivityLine ?: state}".take(128))
                assets.put("small_image", AERIX_LOGO_URL)
                assets.put("small_text", "Aerix Launcher — ${activeActivityLine ?: "Tap to join Discord"}".take(128))
            }
            else -> {
                assets.put("large_image", AERIX_LOGO_URL)
                assets.put("large_text", if (usingMinecraftProfile) {
                    "Aerix Launcher · $stackCaption".take(128)
                } else {
                    "Aerix Launcher — Official Website & Info"
                })
                if (usingMinecraftProfile) {
                    assets.put("small_image", AERIX_LOGO_URL)
                    assets.put("small_text", "Powered by Aerix Launcher")
                }
            }
        }
        val sig = "$cardName|$effectiveDetails|$state|${assets.optString("large_image")}|${assets.optString("small_image")}|${activity.optString("party")}"
        if (sig == lastSentSignature) return
        lastSentSignature = sig

        if (clickable) {
            val serverPageUrl = activeServerAddress?.takeIf { validServerIcon != null }?.let {
                "https://mcsrvstat.us/server/$it"
            }
            val packUrl = activeModpackPageUrl?.takeIf { isValidHttpAssetUrl(it) }
            activity.put("details_url", packUrl ?: AERIX_WEBSITE_URL)
            activity.put("state_url", AERIX_DISCORD_URL)
            assets.put("large_url", serverPageUrl ?: packUrl ?: AERIX_WEBSITE_URL)
            if (validPackIcon != null || validServerIcon != null || usingMinecraftProfile) {
                assets.put("small_url", packUrl ?: AERIX_DISCORD_URL)
            }
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
        activity.put("assets", assets)

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
        val generation = clearGeneration.get()
        main.post {
            if (generation == clearGeneration.get() && target === connection) {
                runCatching { target.sendFrame(frame) }
            }
        }
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
        const val MODPACKS = "Browsing Aerix modpacks"
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
            "MODPACKS" -> MODPACKS
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
