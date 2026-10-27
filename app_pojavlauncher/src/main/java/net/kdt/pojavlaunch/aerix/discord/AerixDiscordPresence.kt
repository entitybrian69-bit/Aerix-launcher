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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.discord.socialsdk.rpc.IDiscordRpcCallback
import com.discord.socialsdk.rpc.IDiscordRpcConnection
import com.discord.socialsdk.rpc.IDiscordRpcService
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import org.json.JSONObject
import java.util.UUID

/**
 * Shows what you are doing in Aerix Launcher on your Discord profile: "Playing Aerix Launcher —
 * In the launcher — Browsing versions", with a timer.
 *
 * How: since Discord Social SDK 1.10 the Android Discord app publishes Rich Presence through a
 * bound service (`com.discord.socialsdk.rpc.IDiscordRpcService`), and it does so **without
 * authentication** — no account token, no OAuth, no gateway and no bot. We speak the same JSON RPC
 * frames the desktop client sends over its local socket, just carried over Binder instead. Discord
 * only ever sees the application id. The AIDL in `src/main/aidl` is Nightdavisao's MIT-licensed
 * reverse-engineered interface (antisocialcord-kt); nothing of Discord's closed SDK ships here.
 *
 * Requirements, both checked at runtime: the Discord app is installed and signed in, and the
 * application id comes from a Discord application registered as "Aerix Launcher" (Developer Portal
 * → New Application). Without an id there is nothing Discord can show, so the whole thing no-ops.
 */
object AerixDiscordPresence {

    private const val TAG = "AerixDiscordPresence"
    private const val ACTION = "com.discord.socialsdk.rpc.IDiscordRpcService"
    private const val RPC_VERSION = "1"
    private const val HANDLER_PID = 1

    /** Official app first, then the main client mods that keep the same service. */
    private val DISCORD_PACKAGES = listOf("com.discord", "cocobo1.pupu.app")

    private val main = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var service: IDiscordRpcService? = null
    private var connection: IDiscordRpcConnection? = null
    private var bound = false
    private var ready = false

    /** What the profile should show right now, kept so a reconnect can replay it. */
    private var details: String = InLauncher.DETAILS
    private var state: String = InLauncher.HOME
    private var startedAt: Long = 0L

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val id = DiscordAppId.asLong(appContext) ?: run {
                Log.i(TAG, "No Discord application id set; presence stays off")
                unbind()
                return
            }
            val rpc = IDiscordRpcService.Stub.asInterface(binder)
            service = rpc
            connection = runCatching { rpc.connect(id, RPC_VERSION, callback) }.getOrNull()
            if (connection == null) {
                Log.w(TAG, "Discord refused the RPC connection for id $id")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connection = null
            ready = false
        }
    }

    private val callback = object : IDiscordRpcCallback.Stub() {
        override fun onFrame(frame: String?) {
            if (frame == null) return
            // The first frame is READY; the app then accepts SET_ACTIVITY frames.
            if (frame.contains("\"READY\"")) {
                ready = true
                sendActivity()
            }
        }

        override fun onClose(code: Int, message: String?) {
            ready = false
            connection = null
        }
    }

    /* ── lifecycle ─────────────────────────────────────────────────────────── */

    /** Bind once, when the launcher becomes visible. Safe to call repeatedly. */
    fun attach(context: Context?) {
        if (context == null) return
        appContext = context.applicationContext
        if (!AerixPreferences.discordPresenceEnabled) return
        if (DiscordAppId.asLong(appContext) == null) return
        if (bound) return

        val intent = resolveService() ?: run {
            Log.i(TAG, "Discord is not installed on this device")
            return
        }
        bound = runCatching {
            appContext!!.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) Log.w(TAG, "Could not bind to Discord's RPC service")
    }

    /** Drop the connection and the card when the launcher goes away. */
    fun detach() {
        ready = false
        runCatching { connection?.disconnect() }
        connection = null
        service = null
        unbind()
    }

    fun refresh(context: Context?) {
        detach()
        attach(context)
    }

    /* ── what the profile says ─────────────────────────────────────────────── */

    /** Launcher pages: the same two lines for every screen, with the section in the second one. */
    fun inLauncher(section: String?) {
        details = InLauncher.DETAILS
        state = InLauncher.stateFor(section)
        if (startedAt == 0L) startedAt = System.currentTimeMillis()
        sendActivity()
    }

    /** A game is starting or running: the instance takes over the card. */
    fun playing(instance: String?, minecraftVersion: String?) {
        details = "Playing Minecraft${if (minecraftVersion.isNullOrBlank()) "" else " $minecraftVersion"}"
        state = instance?.takeIf { it.isNotBlank() } ?: "In game"
        startedAt = System.currentTimeMillis()
        sendActivity()
    }

    /** Remove the card, e.g. when the setting is switched off. */
    fun clear() {
        val frame = JSONObject()
            .put("cmd", "SET_ACTIVITY")
            .put("nonce", UUID.randomUUID().toString())
            .put("args", JSONObject().put("pid", HANDLER_PID).put("activity", JSONObject.NULL))
        send(frame.toString())
    }

    /* ── the RPC frames ────────────────────────────────────────────────────── */

    private fun sendActivity() {
        if (!ready || connection == null) return
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
