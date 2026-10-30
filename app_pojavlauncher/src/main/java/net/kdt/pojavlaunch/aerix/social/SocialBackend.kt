/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The social backend: accounts, friend requests, Realtime sync, parties, LFG, server hub,
 * rich presence, safety controls and messages.
 *
 * Talks to the Aerix Supabase project directly over HTTPS and Realtime WebSocket - no
 * intermediate server, no paid relays, designed for the Supabase free tier.
 */

package net.kdt.pojavlaunch.aerix.social

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.instances.Instance
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object SocialBackend {
    private const val TAG = "AerixSocial"
    private const val PREFS = "aerix_social"
    private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L

    /** The Aerix social project. Both values are public; row-level security protects the rows. */
    const val PROJECT_URL = "https://lktupxfszfefsoyksfkg.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_mA8stbSVwAg4Il3xgHDH5g_9a7WwojI"

    /** Set once the Google Web client ID exists; until then the Google button explains itself. */
    const val GOOGLE_WEB_CLIENT_ID = ""

    /** The scheme the browser comes back on after a Google sign-in (see the manifest). */
    const val OAUTH_REDIRECT = "aerix-social://auth"

    private val gson = Gson()
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var signedIn by mutableStateOf(false)
        private set
    var email by mutableStateOf<String?>(null)
        private set
    var mcUsername by mutableStateOf<String?>(null)
        private set
    var friends by mutableStateOf<List<SocialFriend>>(emptyList())
        private set
    var parties by mutableStateOf<List<SocialParty>>(emptyList())
        private set
    var partyInvites by mutableStateOf<List<SocialPartyInvite>>(emptyList())
        private set
    var lfgParties by mutableStateOf<List<SocialParty>>(emptyList())
        private set
    var sharedServers by mutableStateOf<List<SocialSharedServer>>(emptyList())
        private set
    var blockedUsers by mutableStateOf<List<SocialBlockedUser>>(emptyList())
        private set
    var recentPlayers by mutableStateOf<List<SocialRecentPlayer>>(emptyList())
        private set
    var activeJoinToast by mutableStateOf<SocialJoinToast?>(null)
        private set
    var offlineQueueSize by mutableIntStateOf(0)
        private set

    /** Bumped whenever Realtime delivers DM or party message changes so open chats update instantly. */
    var messageRevision by mutableIntStateOf(0)
        private set
    var partyMessageRevision by mutableIntStateOf(0)
        private set

    var busy by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    /** Unread DM total, kept in step so the Home tile, in-game menu and sidebar badge agree. */
    var unread by mutableStateOf(0)
        private set

    /** Availability mode & custom status controlled from Change Status across launcher and in-game. */
    var myPresenceMode by mutableStateOf(SocialPresenceMode.ONLINE)
        private set
    var myCustomStatus by mutableStateOf<String?>(null)
        private set
    var myLookingToPlay by mutableStateOf(false)
        private set

    /**
     * When `true` (default), status updates automatically:
     * - **Green (`Online`)** while actively using the Launcher or playing Minecraft.
     * - **Yellow (`Idle`)** when the Launcher moves to the background.
     * - **Offline** immediately when Wi-Fi / internet connectivity turns off.
     * Players can also switch to Manual mode anytime via Change Status.
     */
    var isAutoPresence by mutableStateOf(true)
        private set

    /** Live network state driven by [ConnectivityManager.NetworkCallback]. */
    var isNetworkAvailable by mutableStateOf(true)
        private set

    /** Live foreground/background state driven by [Application.ActivityLifecycleCallbacks]. */
    var isLauncherInForeground by mutableStateOf(true)
        private set

    /** Messenger-style sound effects preference (typing, peer typing, sent, received, seen). */
    var chatSoundsEnabled by mutableStateOf(true)
        private set

    /**
     * Effective availability mode right now:
     * - `null` when Wi-Fi / internet is off (renders Offline immediately).
     * - `ONLINE` (Green) in Auto mode when foreground/in-game, `IDLE` (Yellow) in Auto mode when backgrounded.
     * - [myPresenceMode] when the player selected a Manual override.
     */
    val effectivePresenceMode: SocialPresenceMode?
        get() = SocialPresenceCodec.resolveEffectiveMode(
            isNetworkAvailable = isNetworkAvailable,
            isAutoPresence = isAutoPresence,
            isInForeground = isLauncherInForeground,
            isPlayingGame = activeGameStatusLine != null,
            manualMode = myPresenceMode
        )

    /** Whether our own status dot should render as visibly online (non-grey). */
    val isSelfVisiblyOnline: Boolean
        get() {
            val eff = effectivePresenceMode ?: return false
            return eff != SocialPresenceMode.INVISIBLE
        }

    /** Verified role derived strictly from the signed-in account email (`OWNER` or `CO_OWNER`). */
    val myRole: SocialRole?
        get() = SocialRole.fromEmail(email) ?: myProfile?.role

    fun myEffectiveDisplayName(): String =
        myProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: mcUsername?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "Player"

    fun myStatusSummary(): String {
        if (!isNetworkAvailable) return "Offline · Wi-Fi disconnected"
        val clean = myCustomStatus?.trim()?.takeIf { it.isNotEmpty() }
            ?: myProfile?.statusText?.let { SocialPresenceCodec.stripModePrefix(it) }
        val eff = effectivePresenceMode ?: return "Offline"
        val autoSuffix = if (isAutoPresence) " (Auto)" else ""
        return when (eff) {
            SocialPresenceMode.INVISIBLE -> if (clean != null) "Appear Offline · $clean" else "Appear Offline"
            SocialPresenceMode.DND -> if (clean != null) "Do Not Disturb · $clean" else "Do Not Disturb"
            SocialPresenceMode.IDLE -> if (clean != null) "Idle$autoSuffix · $clean" else "Idle$autoSuffix"
            SocialPresenceMode.ONLINE -> clean
                ?: if (myLookingToPlay) "Online$autoSuffix · Looking to play" else "Online$autoSuffix"
        }
    }

    fun toggleChatSounds(enabled: Boolean) {
        chatSoundsEnabled = enabled
        prefs()?.edit()?.putBoolean("chat_sounds_enabled", enabled)?.apply()
        if (enabled) {
            SocialSoundEffects.playStatusChanged()
        }
    }

    /** Tracks which conversation is currently visible on screen for instant read-receipts & sound routing. */
    @Volatile var activeOpenPeerId: String? = null
        private set
    @Volatile var activeOpenPartyId: String? = null
        private set

    fun setOpenConversation(peerId: String? = null, partyId: String? = null) {
        activeOpenPeerId = peerId
        activeOpenPartyId = partyId
    }

    /** Combined badge count (unread DMs + incoming friend requests + pending party invites). */
    val totalActionableBadge: Int
        get() = unread + friends.count { it.pendingIncoming } + partyInvites.size

    fun clearError() {
        lastError = null
    }

    fun reportError(message: String) {
        lastError = message
    }

    fun dismissJoinToast() {
        activeJoinToast = null
    }

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var userId: String? = null
    private var expiresAtMs = 0L
    private var appContext: Context? = null

    /** Cached schema capabilities discovered dynamically so optional SQL tables degrade cleanly. */
    @Volatile private var hasExtendedPartiesColumns: Boolean? = null
    @Volatile private var hasPartyMessagesTable: Boolean? = null
    @Volatile private var hasServerSharesTable: Boolean? = null
    @Volatile private var hasMutualFriendsRpc: Boolean? = null
    @Volatile private var hasLfgRpc: Boolean? = null

    /** Live in-memory DM cache per peer userId for 0ms optimistic sends & instant open-chat delivery. */
    private val liveDmCache = ConcurrentHashMap<String, MutableList<SocialMessage>>()
    private val optimisticIdGenerator = AtomicLong(-1_000_000L)

    /** Ephemeral buffer of Realtime broadcast party messages (augments persisted messages). */
    private val broadcastPartyMessages = ConcurrentHashMap<String, MutableList<SocialMessage>>()

    /** Tracks each friend's previously observed mcVersion so join toasts fire only on fresh launches. */
    private val observedFriendVersions = ConcurrentHashMap<String, String>()
    private var initialFriendsSnapshotLoaded = false

    /** Lifecycle & network monitors for smart automatic status. */
    private val lifecycleHooked = AtomicBoolean(false)
    private val networkHooked = AtomicBoolean(false)
    private val startedActivities = AtomicInteger(0)
    private var backgroundTransitionJob: Job? = null
    private var presenceKeepAliveJob: Job? = null

    /** Active in-game rich presence state when running inside the `:game` process. */
    @Volatile private var activeGameStatusLine: String? = null
    @Volatile private var activeGameMcVersion: String? = null
    @Volatile private var activeGameJoinable: Boolean = false
    private var gamePresenceJob: Job? = null

    init {
        SocialRealtime.listener = object : SocialRealtime.Listener {
            override fun onRealtimeConnected() {
                bgScope.launch {
                    val eff = effectivePresenceMode
                    SocialRealtime.trackPresence(
                        modeKey = eff?.key ?: SocialPresenceMode.INVISIBLE.key,
                        appearOffline = eff == null || eff == SocialPresenceMode.INVISIBLE
                    )
                    syncRichPresenceToServer()
                    flushOfflineOutbox()
                    unreadCount()
                    refreshFriends()
                    refreshParties()
                    refreshPartyInvites()
                }
            }

            override fun onMessageTableChanged(eventType: String, record: JsonObject?) {
                bgScope.launch {
                    val body = record?.stringOrNull("body") ?: ""
                    if (body.startsWith(SocialEnvelopeCodec.PARTY_INVITE_PREFIX)) {
                        refreshPartyInvites()
                    } else if (body.startsWith(SocialEnvelopeCodec.PARTY_CHAT_PREFIX)) {
                        bgScope.launch(Dispatchers.Main) { partyMessageRevision++ }
                    } else {
                        handleMessageRecordFromPostgres(eventType, record)
                    }
                }
            }

            override fun onPartyMessageChanged(eventType: String, record: JsonObject?) {
                bgScope.launch(Dispatchers.Main) { partyMessageRevision++ }
            }

            override fun onFriendshipTableChanged(eventType: String, record: JsonObject?) {
                bgScope.launch { refreshFriends() }
            }

            override fun onPartyTableChanged(eventType: String, record: JsonObject?) {
                bgScope.launch {
                    refreshParties()
                    refreshPartyInvites()
                }
            }

            override fun onProfileUpdated(record: JsonObject) {
                val id = record.stringOrNull("id") ?: return
                if (id == userId) return
                val currentList = friends
                val idx = currentList.indexOfFirst { it.id == id }
                if (idx < 0) return
                val old = currentList[idx]
                val newLastSeen = if (record.has("last_seen")) {
                    parseTimestamp(record.stringOrNull("last_seen") ?: "")
                } else old.lastSeenMs
                val rawStatus = if (record.has("status_line")) {
                    record.stringOrNull("status_line")
                } else if (record.has("status_text")) {
                    record.stringOrNull("status_text")
                } else old.statusLine
                val parsedPresence = SocialPresenceCodec.parse(rawStatus, record.stringOrNull("presence_mode"))
                val rawName = record.stringOrNull("name") ?: record.stringOrNull("display_name")
                val extractedRole = if (rawName != null || record.has("role_badge")) {
                    SocialRole.extract(rawName, record.stringOrNull("role_badge"))
                } else old.cleanDisplayName to old.role
                val newMcVer = if (record.has("mc_version")) {
                    record.stringOrNull("mc_version")
                } else old.mcVersion
                val newJoinable = record.get("joinable")?.takeIf { it.isJsonPrimitive }?.asBoolean
                    ?: old.joinable
                val newLooking = record.get("looking_to_play")?.takeIf { it.isJsonPrimitive }?.asBoolean
                    ?: old.lookingToPlay

                val updated = old.copy(
                    displayName = extractedRole.first ?: old.cleanDisplayName,
                    role = extractedRole.second ?: old.role,
                    lastSeenMs = newLastSeen,
                    statusLine = parsedPresence.customText,
                    presenceMode = parsedPresence.mode,
                    mcVersion = newMcVer,
                    joinable = newJoinable,
                    lookingToPlay = newLooking
                )
                bgScope.launch(Dispatchers.Main) {
                    val mutable = friends.toMutableList()
                    val pos = mutable.indexOfFirst { it.id == id }
                    if (pos >= 0) {
                        mutable[pos] = updated
                        friends = sortFriends(mutable)
                    }
                    maybeEmitJoinToast(old, updated)
                }
            }

            override fun onBroadcastDmChat(payload: JsonObject) {
                val me = userId ?: return
                val sender = payload.stringOrNull("sender") ?: return
                val recipient = payload.stringOrNull("recipient") ?: return
                if (sender != me && recipient != me) return
                val peerId = if (sender == me) recipient else sender
                if (blockedUsers.any { it.userId == peerId }) return

                val msgId = payload.longOr("id", System.currentTimeMillis())
                val tempId = payload.stringOrNull("tempId")
                val body = payload.stringOrNull("body") ?: return
                val atMs = payload.longOr("atMs", System.currentTimeMillis())
                val replyTo = payload.get("replyTo")?.takeIf { !it.isJsonNull }?.asLong
                val attachmentUrl = payload.stringOrNull("attachmentUrl")
                val editedAtMs = payload.get("editedAtMs")?.takeIf { !it.isJsonNull }?.asLong

                val incoming = SocialMessage(
                    id = msgId,
                    sender = sender,
                    recipient = recipient,
                    body = body,
                    atMs = atMs,
                    editedAtMs = editedAtMs,
                    replyTo = replyTo,
                    attachmentUrl = attachmentUrl,
                    localTempId = tempId
                )

                val isBrandNew = upsertIntoDmCache(peerId, incoming)
                bgScope.launch(Dispatchers.Main) {
                    messageRevision++
                }
                if (sender != me && isBrandNew) {
                    if (effectivePresenceMode != SocialPresenceMode.DND) {
                        SocialSoundEffects.playMessageReceived()
                    }
                    if (activeOpenPeerId == sender) {
                        bgScope.launch { markRead(sender) }
                    } else {
                        bgScope.launch(Dispatchers.Main) { unread += 1 }
                        bgScope.launch { unreadCount() }
                    }
                }
            }

            override fun onBroadcastDmRead(payload: JsonObject) {
                val me = userId ?: return
                val readerId = payload.stringOrNull("readerId") ?: return
                val senderId = payload.stringOrNull("senderId") ?: return
                if (senderId != me) return
                val readAtMs = payload.longOr("readAtMs", System.currentTimeMillis()).coerceAtLeast(1L)

                var newlySeen = false
                liveDmCache[readerId]?.let { list ->
                    synchronized(list) {
                        for (i in list.indices) {
                            val m = list[i]
                            if (m.sender == me && !m.isRead) {
                                list[i] = m.copy(readAtMs = readAtMs)
                                newlySeen = true
                            }
                        }
                    }
                }
                if (newlySeen) {
                    SocialSoundEffects.playMessageSeen()
                }
                bgScope.launch(Dispatchers.Main) {
                    messageRevision++
                }
            }

            override fun onBroadcastMessageEdit(payload: JsonObject) {
                val me = userId ?: return
                val id = payload.longOr("id", 0L)
                if (id <= 0L) return
                val sender = payload.stringOrNull("sender") ?: return
                val newBody = payload.stringOrNull("body") ?: return
                val editedAtMs = payload.longOr("editedAtMs", System.currentTimeMillis())
                val partyId = payload.stringOrNull("partyId")
                val recipient = payload.stringOrNull("recipient")

                if (!partyId.isNullOrBlank()) {
                    broadcastPartyMessages[partyId]?.let { list ->
                        synchronized(list) {
                            for (i in list.indices) {
                                if (list[i].id == id) list[i] = list[i].copy(body = newBody, editedAtMs = editedAtMs)
                            }
                        }
                    }
                    bgScope.launch(Dispatchers.Main) { partyMessageRevision++ }
                } else if (!recipient.isNullOrBlank()) {
                    val peerId = if (sender == me) recipient else sender
                    liveDmCache[peerId]?.let { list ->
                        synchronized(list) {
                            for (i in list.indices) {
                                if (list[i].id == id) list[i] = list[i].copy(body = newBody, editedAtMs = editedAtMs)
                            }
                        }
                    }
                    bgScope.launch(Dispatchers.Main) { messageRevision++ }
                }
            }

            override fun onBroadcastPartyChat(payload: JsonObject) {
                val partyId = payload.stringOrNull("partyId") ?: return
                val sender = payload.stringOrNull("sender") ?: return
                val tempId = payload.stringOrNull("tempId")
                val rawSenderName = payload.stringOrNull("senderName")
                val (cleanSender, senderRole) = SocialRole.extract(rawSenderName)
                val msg = SocialMessage(
                    id = payload.longOr("id", System.currentTimeMillis()),
                    sender = sender,
                    recipient = "",
                    body = payload.stringOrNull("body") ?: return,
                    atMs = payload.longOr("atMs", System.currentTimeMillis()),
                    replyTo = payload.get("replyTo")?.takeIf { !it.isJsonNull }?.asLong,
                    attachmentUrl = payload.stringOrNull("attachmentUrl"),
                    partyId = partyId,
                    senderName = cleanSender ?: rawSenderName,
                    senderRole = senderRole,
                    localTempId = tempId
                )
                val list = broadcastPartyMessages.getOrPut(partyId) { ArrayList() }
                var insertedNew = false
                synchronized(list) {
                    val existingIdx = list.indexOfFirst {
                        (!tempId.isNullOrBlank() && it.localTempId == tempId) ||
                            (it.id > 0L && msg.id > 0L && it.id == msg.id) ||
                            (it.sender == msg.sender && it.body == msg.body && abs(it.atMs - msg.atMs) < 3_500L)
                    }
                    if (existingIdx >= 0) {
                        list[existingIdx] = msg
                    } else {
                        list.add(msg)
                        insertedNew = true
                    }
                }
                if (insertedNew && sender != userId && effectivePresenceMode != SocialPresenceMode.DND) {
                    SocialSoundEffects.playMessageReceived()
                }
                bgScope.launch(Dispatchers.Main) { partyMessageRevision++ }
            }

            override fun onBroadcastPartySignal(payload: JsonObject) {
                bgScope.launch {
                    refreshParties()
                    refreshPartyInvites()
                }
            }

            override fun onBroadcastPresence(payload: JsonObject) {
                val uid = payload.stringOrNull("userId") ?: return
                if (uid == userId) return
                val appearOffline = payload.get("appearOffline")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                val modeKey = payload.stringOrNull("mode")
                val rawStatus = payload.stringOrNull("statusLine")
                val parsed = SocialPresenceCodec.parse(rawStatus, modeKey)
                val looking = payload.get("lookingToPlay")?.takeIf { it.isJsonPrimitive }?.asBoolean
                val rawName = payload.stringOrNull("displayName")
                val roleKey = payload.stringOrNull("role")
                val (cleanName, role) = SocialRole.extract(rawName, roleKey)

                bgScope.launch(Dispatchers.Main) {
                    val idx = friends.indexOfFirst { it.id == uid }
                    if (idx >= 0) {
                        val old = friends[idx]
                        val updated = old.copy(
                            displayName = cleanName ?: old.cleanDisplayName,
                            role = role ?: old.role,
                            lastSeenMs = if (appearOffline) 0L else System.currentTimeMillis(),
                            statusLine = if (appearOffline) null else parsed.customText,
                            presenceMode = if (appearOffline) SocialPresenceMode.INVISIBLE else parsed.mode,
                            lookingToPlay = if (appearOffline) false else (looking ?: old.lookingToPlay)
                        )
                        val mutable = friends.toMutableList()
                        mutable[idx] = updated
                        friends = sortFriends(mutable)
                    }
                }
            }

            override fun onPeerSocketPresenceChanged(userId: String, connected: Boolean, modeKey: String?) {
                if (userId == this@SocialBackend.userId) return
                bgScope.launch(Dispatchers.Main) {
                    val idx = friends.indexOfFirst { it.id == userId }
                    if (idx >= 0) {
                        val old = friends[idx]
                        val nextMode = if (connected && modeKey != null) {
                            SocialPresenceMode.fromKey(modeKey) ?: old.presenceMode
                        } else old.presenceMode
                        val updated = if (connected) {
                            if (old.presenceMode == SocialPresenceMode.INVISIBLE) old
                            else old.copy(
                                lastSeenMs = System.currentTimeMillis(),
                                presenceMode = nextMode
                            )
                        } else {
                            // Peer's Wi-Fi dropped or app closed -> immediately mark offline!
                            old.copy(lastSeenMs = 1L)
                        }
                        val mutable = friends.toMutableList()
                        mutable[idx] = updated
                        friends = sortFriends(mutable)
                    }
                }
            }
        }
    }

    private fun handleMessageRecordFromPostgres(eventType: String, record: JsonObject?) {
        val me = userId ?: return
        if (record == null) {
            bgScope.launch { unreadCount() }
            bgScope.launch(Dispatchers.Main) { messageRevision++ }
            return
        }
        val sender = record.stringOrNull("sender") ?: return
        val recipient = record.stringOrNull("recipient") ?: return
        if (sender != me && recipient != me) return
        val peerId = if (sender == me) recipient else sender
        if (blockedUsers.any { it.userId == peerId }) return
        val id = record.longOr("id", 0L)
        if (id in deletedForMeSet()) return

        val rawBody = record.stringOrNull("body") ?: ""
        val displayBody = if (rawBody.startsWith(SocialEnvelopeCodec.PARTY_INVITE_PREFIX)) {
            val inv = SocialEnvelopeCodec.decodePartyInvite(rawBody, id)
            if (inv != null) "Invited to party \"${inv.partyTitle}\"${inv.mcVersion?.let { " · $it" } ?: ""}"
            else rawBody
        } else rawBody

        val readAt = parseTimestamp(record.stringOrNull("read_at") ?: "")
        val msg = SocialMessage(
            id = id,
            sender = sender,
            recipient = recipient,
            body = displayBody,
            atMs = parseTimestamp(record.stringOrNull("created_at") ?: "").takeIf { it > 0L } ?: System.currentTimeMillis(),
            readAtMs = readAt,
            editedAtMs = parseTimestamp(record.stringOrNull("edited_at") ?: ""),
            replyTo = record.get("reply_to")?.takeIf { !it.isJsonNull }?.asLong,
            attachmentUrl = record.stringOrNull("attachment_url")?.takeIf { it.isNotBlank() }
        )

        val wasUnreadByPeer = sender == me && readAt > 0L &&
            (liveDmCache[peerId]?.let { l -> synchronized(l) { l.any { it.id == id && !it.isRead } } } == true)

        val isBrandNew = upsertIntoDmCache(peerId, msg)
        if (wasUnreadByPeer) {
            SocialSoundEffects.playMessageSeen()
        }
        if (isBrandNew && sender != me) {
            if (effectivePresenceMode != SocialPresenceMode.DND) {
                SocialSoundEffects.playMessageReceived()
            }
            if (activeOpenPeerId == sender) {
                bgScope.launch { markRead(sender) }
            }
        }
        bgScope.launch { unreadCount() }
        bgScope.launch(Dispatchers.Main) { messageRevision++ }
    }

    /**
     * Thread-safe merge into [liveDmCache] deduplicating optimistic sends (`tempId` or matching
     * recent body) and enriching `replyPreview`. Returns `true` if a brand-new message was added.
     */
    private fun upsertIntoDmCache(peerId: String, incoming: SocialMessage): Boolean {
        val list = liveDmCache.getOrPut(peerId) { ArrayList() }
        var addedNew = false
        synchronized(list) {
            val matchIdx = list.indexOfFirst { existing ->
                (incoming.id > 0L && existing.id == incoming.id) ||
                    (!incoming.localTempId.isNullOrBlank() && existing.localTempId == incoming.localTempId) ||
                    (existing.id < 0L && !existing.pendingOffline &&
                        existing.sender == incoming.sender &&
                        existing.body == incoming.body &&
                        abs(existing.atMs - incoming.atMs) < 12_000L)
            }
            val parentPreview = incoming.replyPreview ?: incoming.replyTo?.let { pid ->
                list.firstOrNull { it.id == pid }?.body?.take(90)
            }
            val enriched = if (parentPreview != null) incoming.copy(replyPreview = parentPreview) else incoming
            if (matchIdx >= 0) {
                val prev = list[matchIdx]
                list[matchIdx] = enriched.copy(
                    id = if (enriched.id > 0L) enriched.id else prev.id,
                    readAtMs = maxOf(prev.readAtMs ?: 0L, enriched.readAtMs ?: 0L).takeIf { it > 0L },
                    editedAtMs = maxOf(prev.editedAtMs ?: 0L, enriched.editedAtMs ?: 0L).takeIf { it > 0L },
                    localTempId = enriched.localTempId ?: prev.localTempId,
                    pendingOffline = false
                )
            } else {
                list.add(enriched)
                list.sortWith(compareBy<SocialMessage> { it.atMs }.thenBy { if (it.id > 0L) it.id else Long.MAX_VALUE })
                addedNew = true
            }
        }
        return addedNew
    }

    /** Returns the live cached DM stream for [otherId] in 0ms so chats render immediately. */
    fun getCachedMessages(otherId: String): List<SocialMessage> {
        val list = liveDmCache[otherId] ?: return emptyList()
        val deleted = deletedForMeSet()
        return synchronized(list) {
            list.filter { it.id !in deleted }.toList()
        }
    }

    // ---- session, lifecycle & Wi-Fi monitors ------------------------------------------------

    @JvmStatic
    fun attach(context: Context) {
        val app = context.applicationContext
        appContext = app
        SocialSoundEffects.init(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        chatSoundsEnabled = prefs.getBoolean("chat_sounds_enabled", true)
        installLifecycleMonitor(app)
        installNetworkMonitor(app)
        startPresenceKeepAliveLoop()

        if (signedIn) {
            ensureRealtimeConnectedAsync()
            return
        }
        refreshToken = prefs.getString("refresh_token", null)
        userId = prefs.getString("user_id", null)
        email = prefs.getString("email", null)
        if (refreshToken != null && userId != null) {
            signedIn = true
            loadLocalCollections()
            ensureRealtimeConnectedAsync()
        }
    }

    private fun installLifecycleMonitor(app: Context) {
        val application = app as? Application ?: return
        if (!lifecycleHooked.compareAndSet(false, true)) return

        isLauncherInForeground = isAnyAppProcessInForeground(app)
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                val count = startedActivities.incrementAndGet()
                backgroundTransitionJob?.cancel()
                if (count >= 1 && !isLauncherInForeground) {
                    isLauncherInForeground = true
                    onAutomaticPresenceTriggerChanged()
                } else if (count == 1) {
                    isLauncherInForeground = true
                    onAutomaticPresenceTriggerChanged()
                }
            }

            override fun onActivityResumed(activity: Activity) {
                backgroundTransitionJob?.cancel()
                if (!isLauncherInForeground) {
                    isLauncherInForeground = true
                    onAutomaticPresenceTriggerChanged()
                }
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                val remaining = startedActivities.decrementAndGet().coerceAtLeast(0)
                startedActivities.set(remaining)
                if (remaining == 0 && !activity.isChangingConfigurations) {
                    backgroundTransitionJob?.cancel()
                    backgroundTransitionJob = bgScope.launch {
                        // Brief debounce avoids yellow flicker during activity transitions or launching Minecraft (:game).
                        delay(650L)
                        val anyForeground = isAnyAppProcessInForeground(app) || activeGameStatusLine != null
                        withContext(Dispatchers.Main) {
                            if (startedActivities.get() == 0 && !anyForeground && isLauncherInForeground) {
                                isLauncherInForeground = false
                                onAutomaticPresenceTriggerChanged()
                            }
                        }
                    }
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun isAnyAppProcessInForeground(context: Context): Boolean {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            val procs = am.runningAppProcesses ?: return false
            val pkg = context.packageName
            procs.any { info ->
                (info.processName == pkg || info.processName.startsWith("$pkg:")) &&
                    info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        }.getOrDefault(false)
    }

    private fun installNetworkMonitor(app: Context) {
        if (!networkHooked.compareAndSet(false, true)) return
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        isNetworkAvailable = checkActiveInternet(cm)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val online = checkActiveInternet(cm)
                bgScope.launch(Dispatchers.Main) {
                    val changed = !isNetworkAvailable && online
                    isNetworkAvailable = online
                    if (changed && signedIn) {
                        SocialRealtime.reconnectImmediately()
                        bgScope.launch {
                            syncRichPresenceToServer()
                            flushOfflineOutbox()
                            refreshFriends()
                        }
                    }
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val online = hasInternetCapability(capabilities) || checkActiveInternet(cm)
                bgScope.launch(Dispatchers.Main) {
                    val wasOnline = isNetworkAvailable
                    isNetworkAvailable = online
                    if (!wasOnline && online && signedIn) {
                        SocialRealtime.reconnectImmediately()
                        bgScope.launch {
                            syncRichPresenceToServer()
                            flushOfflineOutbox()
                            refreshFriends()
                        }
                    } else if (wasOnline && !online) {
                        SocialRealtime.suspendForNetworkLoss()
                    }
                }
            }

            override fun onLost(network: Network) {
                val stillOnline = checkActiveInternet(cm)
                bgScope.launch(Dispatchers.Main) {
                    isNetworkAvailable = stillOnline
                    if (!stillOnline) {
                        SocialRealtime.suspendForNetworkLoss()
                    }
                }
            }
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            }
        }
    }

    private fun checkActiveInternet(cm: ConnectivityManager): Boolean {
        return runCatching {
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            hasInternetCapability(caps)
        }.getOrDefault(true)
    }

    private fun hasInternetCapability(caps: NetworkCapabilities): Boolean {
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun onAutomaticPresenceTriggerChanged() {
        if (!signedIn || !isNetworkAvailable) return
        if (!isAutoPresence) return
        val eff = effectivePresenceMode ?: return
        myProfile = myProfile?.copy(presenceMode = eff, role = myRole)
        bgScope.launch {
            syncRichPresenceToServer()
            val clean = myCustomStatus
            val hosting = parties.firstOrNull { it.hostId == userId && !it.isExpired() }
            val richBase = activeGameStatusLine
                ?: hosting?.let { "In party: ${it.title}" }
                ?: clean
            SocialRealtime.sendPresenceBroadcast(
                modeKey = eff.key,
                wireStatus = SocialPresenceCodec.encodeForWire(eff, richBase),
                lookingToPlay = myLookingToPlay,
                appearOffline = false,
                displayName = SocialRole.encodeTaggedName(myEffectiveDisplayName(), myRole, myEffectiveDisplayName()),
                roleKey = myRole?.key
            )
        }
    }

    /** Keeps presence fresh even when the Launcher is in the background (`Idle` yellow) while Wi-Fi is on. */
    private fun startPresenceKeepAliveLoop() {
        if (presenceKeepAliveJob?.isActive == true) return
        presenceKeepAliveJob = bgScope.launch {
            while (isActive) {
                delay(50_000L)
                if (signedIn && isNetworkAvailable) {
                    runCatching { syncRichPresenceToServer() }
                }
            }
        }
    }

    private fun ensureRealtimeConnectedAsync() {
        if (!signedIn) return
        bgScope.launch {
            runCatching {
                ensureToken()
                val tok = accessToken
                val uid = userId
                if (!tok.isNullOrBlank() && !uid.isNullOrBlank()) {
                    SocialRealtime.connect(PROJECT_URL, PUBLISHABLE_KEY, tok, uid)
                    maybeRunPeriodicMediaCleanup()
                    flushOfflineOutbox()
                }
            }
        }
    }

    /** This user's row id, so the chat knows which bubbles are theirs. */
    @JvmStatic
    fun myUserId(): String? = userId

    @JvmStatic
    fun signOut() {
        SocialRealtime.disconnect()
        accessToken = null
        refreshToken = null
        userId = null
        expiresAtMs = 0
        signedIn = false
        email = null
        mcUsername = null
        friends = emptyList()
        parties = emptyList()
        partyInvites = emptyList()
        lfgParties = emptyList()
        sharedServers = emptyList()
        blockedUsers = emptyList()
        recentPlayers = emptyList()
        activeJoinToast = null
        offlineQueueSize = 0
        myProfile = null
        myPresenceMode = SocialPresenceMode.ONLINE
        isAutoPresence = true
        myCustomStatus = null
        myLookingToPlay = false
        onboardingDismissedForSession = false
        unread = 0
        lastError = null
        initialFriendsSnapshotLoaded = false
        observedFriendVersions.clear()
        liveDmCache.clear()
        broadcastPartyMessages.clear()
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.remove("refresh_token")
            ?.remove("user_id")
            ?.remove("email")
            ?.apply()
    }

    suspend fun signIn(address: String, password: String): Boolean {
        val ok = attempt {
            val body = JsonObject().apply {
                addProperty("email", address.trim())
                addProperty("password", password)
            }
            val json = asObject(call("POST", "$PROJECT_URL/auth/v1/token?grant_type=password", body.toString(), authenticated = false))
            if (json == null) {
                lastError = lastError ?: "Wrong email or password"
                return@attempt false
            }
            storeSession(json)
            lastError = null
            true
        } ?: false
        if (ok) {
            loadLocalCollections()
            loadProfile()
            ensureRealtimeConnectedAsync()
        }
        return ok
    }

    suspend fun signUp(address: String, password: String): Boolean {
        val created = attempt {
            val body = JsonObject().apply {
                addProperty("email", address.trim())
                addProperty("password", password)
            }
            val json = asObject(call("POST", "$PROJECT_URL/auth/v1/signup", body.toString(), authenticated = false))
            if (json == null) {
                lastError = lastError ?: "Could not create the account. Is that email already in use?"
                return@attempt null
            }
            lastError = null
            json
        }
        val json = created ?: return false
        if (json.has("access_token")) {
            storeSession(json)
            loadLocalCollections()
            loadProfile()
            ensureRealtimeConnectedAsync()
            return true
        }
        lastError = "Account created. Confirm it from your email, then sign in."
        return false
    }

    @JvmStatic
    fun beginGoogleSignIn(context: Context): Boolean {
        val url = "$PROJECT_URL/auth/v1/authorize?provider=google" +
            "&redirect_to=" + URLEncoder.encode(OAUTH_REDIRECT, "UTF-8") +
            "&apikey=" + URLEncoder.encode(PUBLISHABLE_KEY, "UTF-8")
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Could not open the Google sign-in page", error)
            false
        }
    }

    @JvmStatic
    fun completeBrowserSession(redirect: String?): Boolean {
        if (redirect.isNullOrBlank()) return false
        val fragment = redirect.substringAfter('#', "")
        if (fragment.isEmpty()) return false
        val values = fragment.split('&').mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) null
            else pair.substring(0, index) to Uri.decode(pair.substring(index + 1))
        }.toMap()
        val token = values["access_token"] ?: return false
        val refresh = values["refresh_token"] ?: return false
        accessToken = token
        refreshToken = refresh
        expiresAtMs = System.currentTimeMillis() + ((values["expires_in"]?.toLongOrNull() ?: 3600L) * 1000) - 60_000
        applyJwtClaims(token)
        signedIn = true
        onboardingDismissedForSession = false
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("refresh_token", refresh)
            ?.putString("user_id", userId)
            ?.putString("email", email)
            ?.apply()
        loadLocalCollections()
        ensureRealtimeConnectedAsync()
        return true
    }

    suspend fun resendConfirmation(address: String): Boolean {
        val ok = attempt {
            call(
                "POST",
                "$PROJECT_URL/auth/v1/resend",
                JsonObject().apply {
                    addProperty("type", "signup")
                    addProperty("email", address.trim())
                }.toString(),
                authenticated = false
            )
        }
        lastError = if (ok == null) "Could not send the mail. Try again in a minute." else null
        return ok != null
    }

    suspend fun sendPasswordReset(address: String): Boolean {
        val ok = attempt {
            call(
                "POST",
                "$PROJECT_URL/auth/v1/recover",
                JsonObject().apply { addProperty("email", address.trim()) }.toString(),
                authenticated = false
            )
        }
        lastError = if (ok == null) "Could not send the reset mail. Try again in a minute." else null
        return ok != null
    }

    @JvmStatic
    fun needsConfirmation(): Boolean =
        lastError?.contains("confirm", ignoreCase = true) == true

    private fun storeSession(json: JsonObject) {
        accessToken = json.stringOrNull("access_token")
        refreshToken = json.stringOrNull("refresh_token")
        expiresAtMs = System.currentTimeMillis() + (json.longOr("expires_in", 3600) * 1000) - 60_000
        val user = asObject(json.get("user"))
        userId = user?.stringOrNull("id") ?: userId
        email = user?.stringOrNull("email") ?: email
        applyJwtClaims(accessToken)
        signedIn = true
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("refresh_token", refreshToken)
            ?.putString("user_id", userId)
            ?.putString("email", email)
            ?.apply()
        accessToken?.let { tok ->
            userId?.let { uid ->
                SocialRealtime.connect(PROJECT_URL, PUBLISHABLE_KEY, tok, uid)
            }
        }
    }

    private fun applyJwtClaims(token: String?) {
        if (token.isNullOrBlank()) return
        val parts = token.split('.')
        if (parts.size < 2) return
        val payload = runCatching {
            val raw = parts[1]
            val padded = raw + "=".repeat((4 - raw.length % 4) % 4)
            val decoded = android.util.Base64.decode(padded, android.util.Base64.URL_SAFE)
            JsonParser.parseString(String(decoded, Charsets.UTF_8)) as? JsonObject
        }.getOrNull() ?: return
        payload.stringOrNull("sub")?.takeIf { it.isNotBlank() }?.let { if (userId == null) userId = it }
        payload.stringOrNull("email")?.takeIf { it.isNotBlank() }?.let { claimed ->
            if (email.isNullOrBlank()) {
                email = claimed
                appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
                    ?.putString("email", claimed)
                    ?.apply()
            }
        }
    }

    private suspend fun ensureToken() {
        if (accessToken != null && System.currentTimeMillis() < expiresAtMs) {
            if (userId == null || email.isNullOrBlank()) applyJwtClaims(accessToken)
            return
        }
        val token = refreshToken ?: return
        val body = JsonObject().apply { addProperty("refresh_token", token) }
        val resp = callRaw(
            "POST",
            "$PROJECT_URL/auth/v1/token?grant_type=refresh_token",
            body.toString(),
            authenticated = false
        )
        if (resp.code == 400 || resp.code == 401) {
            // Only sign out on explicit auth rejection, never on transient offline failure.
            if (resp.body.contains("invalid", ignoreCase = true) || resp.body.contains("expired", ignoreCase = true)) {
                signOut()
            }
            return
        }
        val json = resp.json as? JsonObject ?: return
        storeSession(json)
        accessToken?.let { SocialRealtime.updateAccessToken(it) }
    }

    // ---- local QoL storage (nicknames, pins, delete-for-me, recent players, offline outbox) -

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadLocalCollections() {
        recentPlayers = readRecentPlayers()
        offlineQueueSize = readOutbox().size
        val p = prefs() ?: return
        chatSoundsEnabled = p.getBoolean("chat_sounds_enabled", true)
        val me = userId ?: return
        isAutoPresence = p.getBoolean("status_auto_$me", true)
        myPresenceMode = SocialPresenceMode.fromKey(p.getString("presence_mode_$me", null))
            ?: SocialPresenceMode.ONLINE
        myCustomStatus = p.getString("custom_status_$me", null)?.takeIf { it.isNotBlank() }
        myLookingToPlay = p.getBoolean("looking_$me", false)
    }

    private fun persistLocalStatus(
        mode: SocialPresenceMode,
        customStatus: String?,
        looking: Boolean,
        autoMode: Boolean = isAutoPresence
    ) {
        val me = userId ?: return
        val editor = prefs()?.edit() ?: return
        editor.putBoolean("status_auto_$me", autoMode)
        editor.putString("presence_mode_$me", mode.key)
        if (customStatus.isNullOrBlank()) editor.remove("custom_status_$me")
        else editor.putString("custom_status_$me", customStatus)
        editor.putBoolean("looking_$me", looking)
        editor.apply()
    }

    fun getFriendNickname(friendId: String): String? {
        val me = userId ?: return null
        return prefs()?.getString("nick_${me}_$friendId", null)?.takeIf { it.isNotBlank() }
    }

    fun setFriendNickname(friendId: String, nickname: String?) {
        val me = userId ?: return
        val clean = nickname?.trim()?.take(32)?.takeIf { it.isNotEmpty() }
        val editor = prefs()?.edit() ?: return
        if (clean == null) editor.remove("nick_${me}_$friendId")
        else editor.putString("nick_${me}_$friendId", clean)
        editor.apply()
        friends = sortFriends(friends.map {
            if (it.id == friendId) it.copy(nickname = clean) else it
        })
    }

    private fun pinnedSet(): Set<String> {
        val me = userId ?: return emptySet()
        return prefs()?.getStringSet("pins_$me", emptySet())?.toSet() ?: emptySet()
    }

    fun toggleFriendPin(friendId: String) {
        val me = userId ?: return
        val current = pinnedSet().toMutableSet()
        val nowPinned = if (current.contains(friendId)) {
            current.remove(friendId)
            false
        } else {
            current.add(friendId)
            true
        }
        prefs()?.edit()?.putStringSet("pins_$me", current)?.apply()
        friends = sortFriends(friends.map {
            if (it.id == friendId) it.copy(pinned = nowPinned) else it
        })
    }

    private fun deletedForMeSet(): Set<Long> {
        val me = userId ?: return emptySet()
        return prefs()?.getStringSet("del_msg_$me", emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    fun deleteMessageForMe(messageId: Long) {
        val me = userId ?: return
        val next = (prefs()?.getStringSet("del_msg_$me", emptySet()) ?: emptySet()).toMutableSet()
        next.add(messageId.toString())
        prefs()?.edit()?.putStringSet("del_msg_$me", next)?.apply()
        messageRevision++
        partyMessageRevision++
    }

    private fun resolvedInviteIds(): Set<String> {
        val me = userId ?: return emptySet()
        return prefs()?.getStringSet("resolved_invites_$me", emptySet())?.toSet() ?: emptySet()
    }

    private fun markInviteResolved(inviteId: String) {
        val me = userId ?: return
        val next = resolvedInviteIds().toMutableSet()
        next.add(inviteId)
        prefs()?.edit()?.putStringSet("resolved_invites_$me", next)?.apply()
    }

    fun recordRecentPlayer(
        otherId: String,
        name: String,
        username: String? = null,
        mcUsername: String? = null,
        avatarUrl: String? = null,
        contextLabel: String
    ) {
        val me = userId ?: return
        if (otherId.isBlank() || otherId == me) return
        val current = readRecentPlayers().filterNot { it.userId == otherId }.toMutableList()
        current.add(
            0,
            SocialRecentPlayer(
                userId = otherId,
                name = name,
                username = username,
                mcUsername = mcUsername,
                avatarUrl = avatarUrl,
                contextLabel = contextLabel,
                seenAtMs = System.currentTimeMillis()
            )
        )
        val trimmed = current.take(25)
        prefs()?.edit()?.putString("recent_$me", gson.toJson(trimmed))?.apply()
        recentPlayers = trimmed
    }

    private fun readRecentPlayers(): List<SocialRecentPlayer> {
        val me = userId ?: return emptyList()
        val raw = prefs()?.getString("recent_$me", null) ?: return emptyList()
        return runCatching {
            val arr = JsonParser.parseString(raw).asJsonArray
            arr.mapNotNull { gson.fromJson(it, SocialRecentPlayer::class.java) }
        }.getOrDefault(emptyList())
    }

    private fun readOutbox(): List<SocialOutboxItem> {
        val me = userId ?: return emptyList()
        val raw = prefs()?.getString("outbox_$me", null) ?: return emptyList()
        return runCatching {
            val arr = JsonParser.parseString(raw).asJsonArray
            arr.mapNotNull { gson.fromJson(it, SocialOutboxItem::class.java) }
        }.getOrDefault(emptyList())
    }

    private fun writeOutbox(items: List<SocialOutboxItem>) {
        val me = userId ?: return
        prefs()?.edit()?.putString("outbox_$me", gson.toJson(items))?.apply()
        offlineQueueSize = items.size
    }

    // ---- profile ---------------------------------------------------------------------------

    @JvmStatic
    fun rememberMinecraftName(name: String?) {
        val trimmed = name?.trim()
        mcUsername = trimmed?.takeIf { it.isNotEmpty() }
        val id = userId ?: return
        prefs()?.edit()?.putString("mc_$id", mcUsername)?.apply()
    }

    private fun storedMinecraftName(): String? {
        val id = userId ?: return null
        return prefs()?.getString("mc_$id", null)
    }

    suspend fun loadProfile() {
        attempt {
            ensureToken()
            if (email.isNullOrBlank()) {
                val authUser = callRaw("GET", "$PROJECT_URL/auth/v1/user")
                (authUser.json as? JsonObject)?.stringOrNull("email")?.takeIf { it.isNotBlank() }?.let { resolved ->
                    email = resolved
                    prefs()?.edit()?.putString("email", resolved)?.apply()
                }
            }
            val id = userId ?: return@attempt Unit
            val resp = callRaw(
                "GET",
                "$PROJECT_URL/rest/v1/profiles?id=eq.$id&select=id,username,name,display_name,mc_username,status_text,looking_to_play"
            )
            val json = if (resp.code in 200..299) {
                resp.json
            } else {
                call("GET", "$PROJECT_URL/rest/v1/profiles?id=eq.$id&select=id,mc_username,display_name")
            }
            val row = json?.takeIf { it.isJsonArray }?.asJsonArray
                ?.takeIf { it.size() > 0 }
                ?.get(0) as? JsonObject
            if (row != null) {
                mcUsername = row.stringOrNull("mc_username")
                    ?.takeIf { it.isNotBlank() } ?: storedMinecraftName()
                val rawStatus = row.stringOrNull("status_text")
                if (rawStatus != null) {
                    val parsed = SocialPresenceCodec.parse(rawStatus)
                    if (!isAutoPresence && myPresenceMode != SocialPresenceMode.INVISIBLE) {
                        myPresenceMode = parsed.mode
                    }
                    if (myCustomStatus == null) myCustomStatus = parsed.customText
                    myLookingToPlay = row.get("looking_to_play")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: myLookingToPlay
                    persistLocalStatus(myPresenceMode, myCustomStatus, myLookingToPlay)
                }
            } else {
                mcUsername = storedMinecraftName()
            }
            upsertProfile(existingRow = row)
            syncRichPresenceToServer()
        }
    }

    private suspend fun upsertProfile(existingRow: JsonObject? = null) {
        val id = userId ?: return
        val role = SocialRole.fromEmail(email)
        val existingRawName = existingRow?.stringOrNull("name")?.takeIf { it.isNotBlank() }
            ?: existingRow?.stringOrNull("display_name")?.takeIf { it.isNotBlank() }
        val cleanExisting = SocialRole.stripRoleTags(existingRawName)
        val existingUsername = existingRow?.stringOrNull("username")?.takeIf { it.isNotBlank() }
        val fallbackBase = cleanExisting
            ?: existingUsername
            ?: mcUsername
            ?: email?.substringBefore('@')
            ?: "Player"
        val encodedName = SocialRole.encodeTaggedName(cleanExisting, role, fallbackBase)
        val encodedDisplay = SocialRole.encodeTaggedName(cleanExisting ?: fallbackBase, role, fallbackBase)

        val body = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("id", id)
                if (encodedName != null) {
                    addProperty("name", encodedName)
                }
                addProperty("display_name", encodedDisplay ?: fallbackBase)
                addProperty("mc_username", mcUsername)
            })
        }
        call(
            "POST",
            "$PROJECT_URL/rest/v1/profiles?on_conflict=id",
            body.toString(),
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
        )
    }

    // ---- friends, mutuals, nicknames, pins & join toasts ------------------------------------

    private fun sortFriends(list: List<SocialFriend>): List<SocialFriend> =
        list.sortedWith(
            compareByDescending<SocialFriend> { it.pendingIncoming }
                .thenByDescending { it.pinned }
                .thenByDescending { it.online }
                .thenByDescending { it.joinable }
                .thenBy { it.effectiveName.lowercase(Locale.US) }
        )

    private fun maybeEmitJoinToast(previous: SocialFriend?, current: SocialFriend) {
        if (effectivePresenceMode == SocialPresenceMode.DND) return
        if (current.pendingIncoming || current.pendingOutgoing) return
        if (blockedUsers.any { it.userId == current.id }) return
        val ver = current.mcVersion?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (!current.online) return
        val prevVer = observedFriendVersions[current.id] ?: previous?.mcVersion
        observedFriendVersions[current.id] = ver
        if (!initialFriendsSnapshotLoaded) return
        if (prevVer == ver && previous?.online == true) return

        val friendParty = parties.firstOrNull { it.hostId == current.id && !it.isExpired() }
        activeJoinToast = SocialJoinToast(
            id = "${current.id}:$ver:${System.currentTimeMillis()}",
            friendId = current.id,
            friendName = current.effectiveName,
            friendRole = current.effectiveRole,
            mcUsername = current.mcUsername,
            mcVersion = ver,
            statusLine = current.statusLine,
            loader = friendParty?.loader,
            serverAddress = friendParty?.serverAddress,
            partyId = friendParty?.id
        )
    }

    suspend fun refreshFriends() {
        attempt {
            ensureToken()
            val me = userId ?: return@attempt Unit
            loadBlockedUsersInternal(me)
            val blockedSet = blockedUsers.map { it.userId }.toSet()

            val json = call(
                "GET",
                "$PROJECT_URL/rest/v1/friendships?select=requester,addressee,status" +
                    "&or=(requester.eq.$me,addressee.eq.$me)"
            )
            val rows = json?.takeIf { it.isJsonArray }?.asJsonArray
            if (rows == null || rows.size() == 0) {
                friends = emptyList()
                initialFriendsSnapshotLoaded = true
                return@attempt Unit
            }
            val incoming = ArrayList<String>()
            val outgoing = ArrayList<String>()
            val accepted = ArrayList<String>()
            for (element in rows) {
                val row = element as? JsonObject ?: continue
                val requester = row.stringOrNull("requester") ?: continue
                val addressee = row.stringOrNull("addressee") ?: continue
                val other = if (requester == me) addressee else requester
                if (other in blockedSet) continue
                when (row.stringOrNull("status")) {
                    "accepted" -> accepted.add(other)
                    "pending" -> if (requester == me) outgoing.add(other) else incoming.add(other)
                }
            }
            val ids = (accepted + incoming + outgoing).distinct()
            if (ids.isEmpty()) {
                friends = emptyList()
                initialFriendsSnapshotLoaded = true
                return@attempt Unit
            }
            val quoted = ids.joinToString(",") { "\"$it\"" }
            val profilesResp = callRaw(
                "GET",
                "$PROJECT_URL/rest/v1/profiles?id=in.($quoted)" +
                    "&select=id,username,name,display_name,mc_username,avatar_url,last_seen,status_line,status_text,mc_version,joinable,looking_to_play"
            )
            val profiles = if (profilesResp.code in 200..299) {
                profilesResp.json
            } else {
                call(
                    "GET",
                    "$PROJECT_URL/rest/v1/profiles?id=in.($quoted)" +
                        "&select=id,username,name,display_name,mc_username,avatar_url,last_seen"
                )
            }

            val pins = pinnedSet()
            val previousById = friends.associateBy { it.id }
            val byId = HashMap<String, SocialFriend>()

            profiles?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                val row = element as? JsonObject ?: return@forEach
                val id = row.stringOrNull("id") ?: return@forEach
                val uname = row.stringOrNull("username")
                val rawName = row.stringOrNull("name")?.takeIf { it.isNotBlank() }
                    ?: row.stringOrNull("display_name")?.takeIf { it.isNotBlank() }
                val (cleanName, detectedRole) = SocialRole.extract(rawName, row.stringOrNull("role_badge"))
                val display = cleanName
                    ?: uname
                    ?: row.stringOrNull("mc_username")
                    ?: "Player"
                val rawStatus = row.stringOrNull("status_line")?.takeIf { it.isNotBlank() }
                    ?: row.stringOrNull("status_text")?.takeIf { it.isNotBlank() }
                val parsedPresence = SocialPresenceCodec.parse(rawStatus, row.stringOrNull("presence_mode"))
                val mutualPair = if (id in incoming) fetchMutualFriendsInternal(id, accepted) else (0 to emptyList())

                byId[id] = SocialFriend(
                    id = id,
                    mcUsername = row.stringOrNull("mc_username")?.takeIf { it.isNotBlank() }
                        ?: uname ?: display,
                    displayName = display,
                    lastSeenMs = parseTimestamp(row.stringOrNull("last_seen") ?: ""),
                    pendingIncoming = id in incoming,
                    pendingOutgoing = id in outgoing,
                    username = uname,
                    avatarUrl = row.stringOrNull("avatar_url"),
                    statusLine = parsedPresence.customText,
                    mcVersion = row.stringOrNull("mc_version")?.takeIf { it.isNotBlank() },
                    joinable = row.get("joinable")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                    lookingToPlay = row.get("looking_to_play")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                    nickname = getFriendNickname(id),
                    pinned = id in pins,
                    mutualCount = mutualPair.first,
                    mutualNames = mutualPair.second,
                    role = detectedRole,
                    presenceMode = parsedPresence.mode
                )
            }

            val nextList = sortFriends(ids.mapNotNull { byId[it] })
            nextList.forEach { f ->
                maybeEmitJoinToast(previousById[f.id], f)
                f.mcVersion?.takeIf { it.isNotBlank() && f.online }?.let { observedFriendVersions[f.id] = it }
            }
            initialFriendsSnapshotLoaded = true
            friends = nextList
        }
    }

    private suspend fun fetchMutualFriendsInternal(otherId: String, myAcceptedIds: List<String>): Pair<Int, List<String>> {
        if (hasMutualFriendsRpc != false) {
            val resp = callRaw(
                "POST",
                "$PROJECT_URL/rest/v1/rpc/mutual_friends",
                JsonObject().apply { addProperty("p_other", otherId) }.toString()
            )
            if (resp.code in 200..299 && resp.json is JsonObject) {
                hasMutualFriendsRpc = true
                val obj = resp.json
                val count = obj.longOr("count", 0L).toInt()
                val names = obj.get("names")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { runCatching { it.asString }.getOrNull() } ?: emptyList()
                return count to names
            } else if (resp.code == 404) {
                hasMutualFriendsRpc = false
            }
        }
        return 0 to emptyList()
    }

    suspend fun loadMutualFriends(otherId: String): Pair<Int, List<String>> =
        attempt {
            ensureToken()
            fetchMutualFriendsInternal(otherId, friends.filter { !it.pendingIncoming && !it.pendingOutgoing }.map { it.id })
        } ?: (0 to emptyList())

    suspend fun addFriendByName(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) {
            lastError = "Enter a username"
            return false
        }
        val sent = attempt {
            ensureToken()
            val me = userId ?: return@attempt null
            val lookup = JsonObject().apply { addProperty("p_username", clean.removePrefix("@")) }
            val found = call("POST", "$PROJECT_URL/rest/v1/rpc/find_player", lookup.toString())
            val target = (found as? JsonObject)?.stringOrNull("id")
            if (target == null) {
                lastError = "No one is called @${clean.removePrefix("@")}"
                return@attempt null
            }
            if (target == me) {
                lastError = "That is you"
                return@attempt null
            }
            if (blockedUsers.any { it.userId == target }) {
                lastError = "Unblock this player before sending a friend request"
                return@attempt null
            }
            val body = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("requester", me)
                    addProperty("addressee", target)
                    addProperty("status", "pending")
                })
            }
            val result = call(
                "POST",
                "$PROJECT_URL/rest/v1/friendships?on_conflict=requester,addressee",
                body.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
            )
            lastError = if (result == null) (lastError ?: "Could not send the request") else null
            result
        }
        refreshFriends()
        return sent != null
    }

    suspend fun addFriendById(targetId: String): Boolean {
        val sent = attempt {
            ensureToken()
            val me = userId ?: return@attempt null
            if (targetId == me) {
                lastError = "That is you"
                return@attempt null
            }
            if (blockedUsers.any { it.userId == targetId }) {
                lastError = "Unblock this player first"
                return@attempt null
            }
            val body = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("requester", me)
                    addProperty("addressee", targetId)
                    addProperty("status", "pending")
                })
            }
            call(
                "POST",
                "$PROJECT_URL/rest/v1/friendships?on_conflict=requester,addressee",
                body.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
            )
        }
        refreshFriends()
        return sent != null
    }

    suspend fun removeFriend(otherId: String): Boolean {
        val done = attempt {
            ensureToken()
            val me = userId ?: return@attempt null
            call(
                "DELETE",
                "$PROJECT_URL/rest/v1/friendships?or=(and(requester.eq.$me,addressee.eq.$otherId),and(requester.eq.$otherId,addressee.eq.$me))"
            )
        }
        refreshFriends()
        return done != null
    }

    suspend fun answerRequest(otherId: String, accept: Boolean) {
        attempt {
            ensureToken()
            val me = userId ?: return@attempt Unit
            call(
                "PATCH",
                "$PROJECT_URL/rest/v1/friendships?requester=eq.$otherId&addressee=eq.$me",
                JsonObject().apply {
                    addProperty("status", if (accept) "accepted" else "declined")
                }.toString()
            )
        }
        refreshFriends()
    }

    // ---- messages (Realtime, replies, edits, delete-for-me, attachments, offline queue, search)

    fun notifyTyping(peerId: String? = null, partyId: String? = null) {
        val label = myEffectiveDisplayName()
        SocialRealtime.sendTyping(peerId, partyId, label, active = true)
    }

    fun notifyStopTyping(peerId: String? = null, partyId: String? = null) {
        val label = myEffectiveDisplayName()
        SocialRealtime.sendTyping(peerId, partyId, label, active = false)
    }

    suspend fun loadMessages(otherId: String): List<SocialMessage> {
        val loaded = attempt {
            ensureToken()
            val me = userId ?: return@attempt getCachedMessages(otherId)
            if (blockedUsers.any { it.userId == otherId }) return@attempt emptyList<SocialMessage>()
            val deletedIds = deletedForMeSet()

            val resp = callRaw(
                "GET",
                "$PROJECT_URL/rest/v1/messages?select=id,sender,recipient,body,reply_to,attachment_url,edited_at,read_at,created_at" +
                    "&or=(and(sender.eq.$me,recipient.eq.$otherId)," +
                    "and(sender.eq.$otherId,recipient.eq.$me))" +
                    "&order=created_at.asc&limit=250"
            )
            val json = if (resp.code in 200..299) {
                resp.json
            } else {
                call(
                    "GET",
                    "$PROJECT_URL/rest/v1/messages?select=id,sender,recipient,body,created_at" +
                        "&or=(and(sender.eq.$me,recipient.eq.$otherId)," +
                        "and(sender.eq.$otherId,recipient.eq.$me))" +
                        "&order=created_at.asc&limit=200"
                )
            } ?: return@attempt getCachedMessages(otherId)

            val rows = json.takeIf { it.isJsonArray }?.asJsonArray ?: return@attempt getCachedMessages(otherId)
            val rawParsed = ArrayList<SocialMessage>(rows.size())

            rows.forEach { element ->
                val row = element as? JsonObject ?: return@forEach
                val id = row.longOr("id", 0L)
                if (id in deletedIds) return@forEach
                val rawBody = row.stringOrNull("body") ?: ""
                if (rawBody.startsWith(SocialEnvelopeCodec.PARTY_CHAT_PREFIX)) return@forEach

                val displayBody = if (rawBody.startsWith(SocialEnvelopeCodec.PARTY_INVITE_PREFIX)) {
                    val inv = SocialEnvelopeCodec.decodePartyInvite(rawBody, id)
                    if (inv != null) {
                        "Invited to party \"${inv.partyTitle}\"" +
                            (inv.mcVersion?.let { " · $it" } ?: "")
                    } else rawBody
                } else {
                    rawBody
                }

                rawParsed.add(
                    SocialMessage(
                        id = id,
                        sender = row.stringOrNull("sender") ?: "",
                        recipient = row.stringOrNull("recipient") ?: "",
                        body = displayBody,
                        atMs = parseTimestamp(row.stringOrNull("created_at") ?: ""),
                        readAtMs = parseTimestamp(row.stringOrNull("read_at") ?: ""),
                        editedAtMs = parseTimestamp(row.stringOrNull("edited_at") ?: ""),
                        replyTo = row.get("reply_to")?.takeIf { !it.isJsonNull }?.asLong,
                        attachmentUrl = row.stringOrNull("attachment_url")?.takeIf { it.isNotBlank() }
                    )
                )
            }

            val byId = rawParsed.associateBy { it.id }
            val enriched = rawParsed.map { msg ->
                val parent = msg.replyTo?.let { byId[it] }
                if (parent != null) {
                    msg.copy(replyPreview = parent.body.take(90))
                } else msg
            }.toMutableList()

            // Preserve any in-flight optimistic messages that haven't finished their POST yet.
            liveDmCache[otherId]?.let { existingList ->
                synchronized(existingList) {
                    existingList.filter { it.id < 0L && !it.pendingOffline }.forEach { inflight ->
                        val alreadyPersisted = enriched.any { dbMsg ->
                            dbMsg.sender == inflight.sender &&
                                dbMsg.body == inflight.body &&
                                abs(dbMsg.atMs - inflight.atMs) < 12_000L
                        }
                        if (!alreadyPersisted) enriched.add(inflight)
                    }
                }
            }

            // Append pending offline outbox messages for this conversation.
            val meId = userId ?: ""
            readOutbox().filter { it.recipientId == otherId }.forEachIndexed { index, queued ->
                enriched.add(
                    SocialMessage(
                        id = -(queued.createdAtMs + index),
                        sender = meId,
                        recipient = otherId,
                        body = queued.body,
                        atMs = queued.createdAtMs,
                        replyTo = queued.replyTo,
                        replyPreview = queued.replyTo?.let { byId[it]?.body?.take(90) },
                        attachmentUrl = queued.attachmentRemoteUrl ?: queued.attachmentLocalPath,
                        pendingOffline = true,
                        localTempId = queued.localId
                    )
                )
            }

            enriched.sortWith(compareBy<SocialMessage> { it.atMs }.thenBy { if (it.id > 0L) it.id else Long.MAX_VALUE })
            val cacheList = liveDmCache.getOrPut(otherId) { ArrayList() }
            synchronized(cacheList) {
                cacheList.clear()
                cacheList.addAll(enriched)
            }
            enriched.toList()
        }
        return loaded ?: getCachedMessages(otherId)
    }

    /**
     * Lightweight incremental delta-sync while a DM chat is open.
     * Fetches only messages with `id > maxServerId` plus recent read-receipt updates so open
     * conversations ALWAYS stay live even if a carrier NAT drops a WebSocket broadcast frame.
     */
    suspend fun syncNewMessages(otherId: String): List<SocialMessage> {
        if (!signedIn || !isNetworkAvailable) return getCachedMessages(otherId)
        val cached = liveDmCache[otherId]
        if (cached == null) return loadMessages(otherId)
        val maxServerId = synchronized(cached) {
            cached.filter { it.id > 0L }.maxOfOrNull { it.id } ?: 0L
        }
        if (maxServerId <= 0L) return loadMessages(otherId)

        attempt {
            ensureToken()
            val me = userId ?: return@attempt Unit
            if (blockedUsers.any { it.userId == otherId }) return@attempt Unit
            val deletedIds = deletedForMeSet()

            // 1. Pull any brand-new rows (`id > maxServerId`) in one tiny indexed query.
            val newResp = callRaw(
                "GET",
                "$PROJECT_URL/rest/v1/messages?select=id,sender,recipient,body,reply_to,attachment_url,edited_at,read_at,created_at" +
                    "&id=gt.$maxServerId" +
                    "&or=(and(sender.eq.$me,recipient.eq.$otherId),and(sender.eq.$otherId,recipient.eq.$me))" +
                    "&order=id.asc&limit=50"
            )
            var changed = false
            var incomingFromPeer = false
            newResp.json?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
                val row = el as? JsonObject ?: return@forEach
                val id = row.longOr("id", 0L)
                if (id <= 0L || id in deletedIds) return@forEach
                val rawBody = row.stringOrNull("body") ?: ""
                if (rawBody.startsWith(SocialEnvelopeCodec.PARTY_CHAT_PREFIX)) return@forEach
                val displayBody = if (rawBody.startsWith(SocialEnvelopeCodec.PARTY_INVITE_PREFIX)) {
                    val inv = SocialEnvelopeCodec.decodePartyInvite(rawBody, id)
                    if (inv != null) "Invited to party \"${inv.partyTitle}\"${inv.mcVersion?.let { " · $it" } ?: ""}"
                    else rawBody
                } else rawBody

                val sender = row.stringOrNull("sender") ?: ""
                val msg = SocialMessage(
                    id = id,
                    sender = sender,
                    recipient = row.stringOrNull("recipient") ?: "",
                    body = displayBody,
                    atMs = parseTimestamp(row.stringOrNull("created_at") ?: ""),
                    readAtMs = parseTimestamp(row.stringOrNull("read_at") ?: ""),
                    editedAtMs = parseTimestamp(row.stringOrNull("edited_at") ?: ""),
                    replyTo = row.get("reply_to")?.takeIf { !it.isJsonNull }?.asLong,
                    attachmentUrl = row.stringOrNull("attachment_url")?.takeIf { it.isNotBlank() }
                )
                if (upsertIntoDmCache(otherId, msg)) {
                    changed = true
                    if (sender == otherId) incomingFromPeer = true
                }
            }

            // 2. Check if any of our sent messages that were still unread have now been seen (`read_at != null`).
            val unreadSentIds = synchronized(cached) {
                cached.filter { it.id > 0L && it.sender == me && !it.isRead }.takeLast(12).map { it.id }
            }
            if (unreadSentIds.isNotEmpty()) {
                val idList = unreadSentIds.joinToString(",")
                val seenResp = callRaw(
                    "GET",
                    "$PROJECT_URL/rest/v1/messages?select=id,read_at,edited_at,body&id=in.($idList)"
                )
                var newlySeen = false
                seenResp.json?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
                    val r = el as? JsonObject ?: return@forEach
                    val rid = r.longOr("id", 0L)
                    val rAt = parseTimestamp(r.stringOrNull("read_at") ?: "")
                    if (rAt > 0L) {
                        synchronized(cached) {
                            val pos = cached.indexOfFirst { it.id == rid }
                            if (pos >= 0 && !cached[pos].isRead) {
                                cached[pos] = cached[pos].copy(readAtMs = rAt)
                                newlySeen = true
                                changed = true
                            }
                        }
                    }
                }
                if (newlySeen) {
                    SocialSoundEffects.playMessageSeen()
                }
            }

            if (incomingFromPeer) {
                if (effectivePresenceMode != SocialPresenceMode.DND) {
                    SocialSoundEffects.playMessageReceived()
                }
                if (activeOpenPeerId == otherId) {
                    markRead(otherId)
                }
            }
            if (changed) {
                withContext(Dispatchers.Main) { messageRevision++ }
            }
        }
        return getCachedMessages(otherId)
    }

    suspend fun sendMessage(otherId: String, text: String): Boolean =
        sendMessage(otherId = otherId, text = text, replyTo = null, attachmentUri = null)

    suspend fun sendMessage(
        otherId: String,
        text: String,
        replyTo: Long? = null,
        attachmentUri: Uri? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (blockedUsers.any { it.userId == otherId }) {
            lastError = "You blocked this player. Unblock them to message."
            return@withContext false
        }
        val cleanText = text.trim().ifEmpty { if (attachmentUri != null) "📷 Image" else "" }
        if (cleanText.isEmpty()) return@withContext false

        val meQuick = userId ?: run {
            ensureToken()
            userId
        } ?: run {
            lastError = "Sign in first"
            return@withContext false
        }

        val nowMs = System.currentTimeMillis()
        val tempId = UUID.randomUUID().toString()
        val tempNumericId = optimisticIdGenerator.decrementAndGet()
        val cleanBody = cleanText.take(2000)

        // 1. 0ms Optimistic UI insertion so the sender sees their bubble immediately!
        val optimisticMsg = SocialMessage(
            id = tempNumericId,
            sender = meQuick,
            recipient = otherId,
            body = cleanBody,
            atMs = nowMs,
            replyTo = replyTo,
            attachmentUrl = attachmentUri?.toString(),
            localTempId = tempId
        )
        upsertIntoDmCache(otherId, optimisticMsg)
        notifyStopTyping(peerId = otherId)
        withContext(Dispatchers.Main) { messageRevision++ }

        // If pure text, broadcast immediately over WebSocket (~40ms) before waiting on PostgreSQL!
        if (attachmentUri == null && isNetworkAvailable) {
            SocialRealtime.sendDmChatBroadcast(optimisticMsg, tempId = tempId)
        }

        ensureToken()
        val me = userId ?: meQuick

        var localAttachmentPath: String? = null
        var remoteAttachmentUrl: String? = null
        if (attachmentUri != null) {
            val downscaled = downscaleAttachmentToJpeg(attachmentUri)
            if (downscaled == null) {
                removeOptimisticFromDmCache(otherId, tempId)
                withContext(Dispatchers.Main) { messageRevision++ }
                lastError = "Could not read that image"
                return@withContext false
            }
            remoteAttachmentUrl = if (isNetworkAvailable) uploadImage("chat-att", downscaled, "jpg") else null
            if (remoteAttachmentUrl == null) {
                localAttachmentPath = saveOutboxAttachmentLocally(downscaled)
            }
        }

        if (!isNetworkAvailable || (attachmentUri != null && remoteAttachmentUrl == null)) {
            markOptimisticPendingOffline(otherId, tempId, localAttachmentPath)
            enqueueOfflineItem(
                SocialOutboxItem(
                    localId = tempId,
                    recipientId = otherId,
                    body = cleanBody,
                    replyTo = replyTo,
                    attachmentLocalPath = localAttachmentPath,
                    attachmentRemoteUrl = remoteAttachmentUrl
                )
            )
            lastError = null
            withContext(Dispatchers.Main) { messageRevision++ }
            return@withContext true
        }

        val payload = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("sender", me)
                addProperty("recipient", otherId)
                addProperty("body", cleanBody)
                if (replyTo != null && replyTo > 0L) addProperty("reply_to", replyTo)
                if (!remoteAttachmentUrl.isNullOrBlank()) addProperty("attachment_url", remoteAttachmentUrl)
            })
        }

        val resp = callRaw(
            "POST",
            "$PROJECT_URL/rest/v1/messages",
            payload.toString(),
            extraHeaders = mapOf("Prefer" to "return=representation")
        )

        if (resp.code in 200..299) {
            val row = resp.json?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull() as? JsonObject
            val confirmedId = row?.longOr("id", 0L)?.takeIf { it > 0L } ?: nowMs
            val confirmedAtMs = row?.stringOrNull("created_at")?.let { parseTimestamp(it) }?.takeIf { it > 0L } ?: nowMs
            val confirmedMsg = optimisticMsg.copy(
                id = confirmedId,
                atMs = confirmedAtMs,
                attachmentUrl = remoteAttachmentUrl ?: optimisticMsg.attachmentUrl,
                pendingOffline = false
            )
            upsertIntoDmCache(otherId, confirmedMsg)
            SocialRealtime.sendDmChatBroadcast(confirmedMsg, tempId = tempId)
            friends.firstOrNull { it.id == otherId }?.let { f ->
                recordRecentPlayer(f.id, f.effectiveName, f.username, f.mcUsername, f.avatarUrl, "Direct message")
            }
            withContext(Dispatchers.Main) { messageRevision++ }
            return@withContext true
        }

        // If blocked server-side by RLS (HTTP 403 / 42501), remove optimistic bubble and surface clearly.
        if (resp.code == 401 || resp.code == 403 || resp.body.contains("row-level security", ignoreCase = true)) {
            removeOptimisticFromDmCache(otherId, tempId)
            withContext(Dispatchers.Main) { messageRevision++ }
            lastError = "Message could not be delivered (blocked or restricted)"
            return@withContext false
        }

        // Network failure -> mark bubble queued in persistent offline outbox!
        markOptimisticPendingOffline(otherId, tempId, remoteAttachmentUrl ?: localAttachmentPath)
        enqueueOfflineItem(
            SocialOutboxItem(
                localId = tempId,
                recipientId = otherId,
                body = cleanBody,
                replyTo = replyTo,
                attachmentLocalPath = localAttachmentPath,
                attachmentRemoteUrl = remoteAttachmentUrl
            )
        )
        lastError = null
        withContext(Dispatchers.Main) { messageRevision++ }
        true
    }

    private fun removeOptimisticFromDmCache(peerId: String, tempId: String) {
        liveDmCache[peerId]?.let { list ->
            synchronized(list) {
                list.removeAll { it.localTempId == tempId }
            }
        }
    }

    private fun markOptimisticPendingOffline(peerId: String, tempId: String, attachmentPath: String?) {
        liveDmCache[peerId]?.let { list ->
            synchronized(list) {
                val idx = list.indexOfFirst { it.localTempId == tempId }
                if (idx >= 0) {
                    list[idx] = list[idx].copy(
                        pendingOffline = true,
                        attachmentUrl = attachmentPath ?: list[idx].attachmentUrl
                    )
                }
            }
        }
    }

    private fun enqueueOfflineItem(item: SocialOutboxItem) {
        val current = readOutbox().toMutableList()
        current.add(item)
        writeOutbox(current)
    }

    suspend fun flushOfflineOutbox() = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable) return@withContext
        val pending = readOutbox()
        if (pending.isEmpty()) return@withContext
        ensureToken()
        val me = userId ?: return@withContext
        val remaining = ArrayList<SocialOutboxItem>()

        for (item in pending) {
            var remoteUrl = item.attachmentRemoteUrl
            if (remoteUrl == null && !item.attachmentLocalPath.isNullOrBlank()) {
                val file = File(item.attachmentLocalPath)
                if (file.isFile) {
                    remoteUrl = uploadImage("chat-att", file.readBytes(), "jpg")
                    if (remoteUrl != null) runCatching { file.delete() }
                }
            }
            val delivered = if (!item.recipientId.isNullOrBlank()) {
                val payload = JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("sender", me)
                        addProperty("recipient", item.recipientId)
                        addProperty("body", item.body)
                        item.replyTo?.takeIf { it > 0L }?.let { addProperty("reply_to", it) }
                        remoteUrl?.let { addProperty("attachment_url", it) }
                    })
                }
                val r = callRaw(
                    "POST",
                    "$PROJECT_URL/rest/v1/messages",
                    payload.toString(),
                    extraHeaders = mapOf("Prefer" to "return=representation")
                )
                if (r.code in 200..299) {
                    val row = r.json?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull() as? JsonObject
                    val realId = row?.longOr("id", System.currentTimeMillis()) ?: System.currentTimeMillis()
                    val msg = SocialMessage(
                        id = realId,
                        sender = me,
                        recipient = item.recipientId,
                        body = item.body,
                        atMs = System.currentTimeMillis(),
                        replyTo = item.replyTo,
                        attachmentUrl = remoteUrl,
                        localTempId = item.localId
                    )
                    upsertIntoDmCache(item.recipientId, msg)
                    SocialRealtime.sendDmChatBroadcast(msg, tempId = item.localId)
                }
                r.code in 200..299 || r.code == 403
            } else if (!item.partyId.isNullOrBlank()) {
                sendPartyMessageInternal(item.partyId, item.body, item.replyTo, remoteUrl, queueOnFail = false)
            } else true

            if (!delivered) {
                remaining.add(item.copy(attachmentRemoteUrl = remoteUrl))
            }
        }
        writeOutbox(remaining)
        bgScope.launch(Dispatchers.Main) {
            messageRevision++
            partyMessageRevision++
        }
    }

    suspend fun editMessage(messageId: Long, newBody: String, isParty: Boolean = false): Boolean {
        val trimmed = newBody.trim().take(2000)
        if (trimmed.isEmpty() || messageId <= 0L) return false
        val nowMs = System.currentTimeMillis()
        var targetPeerId: String? = null
        var targetPartyId: String? = null

        if (isParty) {
            broadcastPartyMessages.forEach { (pid, list) ->
                synchronized(list) {
                    for (i in list.indices) {
                        if (list[i].id == messageId) {
                            list[i] = list[i].copy(body = trimmed, editedAtMs = nowMs)
                            targetPartyId = pid
                        }
                    }
                }
            }
            partyMessageRevision++
        } else {
            liveDmCache.forEach { (peerId, list) ->
                synchronized(list) {
                    for (i in list.indices) {
                        if (list[i].id == messageId) {
                            list[i] = list[i].copy(body = trimmed, editedAtMs = nowMs)
                            targetPeerId = peerId
                        }
                    }
                }
            }
            messageRevision++
        }

        val ok = attempt {
            ensureToken()
            val me = userId ?: return@attempt null
            val table = if (isParty && hasPartyMessagesTable == true) "party_messages" else "messages"
            call(
                "PATCH",
                "$PROJECT_URL/rest/v1/$table?id=eq.$messageId&sender=eq.$me",
                JsonObject().apply {
                    addProperty("body", trimmed)
                    addProperty("edited_at", isoAt(nowMs))
                }.toString(),
                extraHeaders = mapOf("Prefer" to "return=minimal")
            )
        } != null
        if (ok) {
            SocialRealtime.sendMessageEditBroadcast(
                messageId = messageId,
                newBody = trimmed,
                editedAtMs = nowMs,
                recipientId = targetPeerId,
                partyId = targetPartyId ?: activeOpenPartyId
            )
            if (isParty) partyMessageRevision++ else messageRevision++
        }
        return ok
    }

    /** Full-text search across all DM conversations and active party channels. */
    suspend fun searchConversations(query: String): List<SocialSearchHit> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        return attempt {
            ensureToken()
            val me = userId ?: return@attempt emptyList<SocialSearchHit>()
            val deleted = deletedForMeSet()
            val blocked = blockedUsers.map { it.userId }.toSet()
            val friendById = friends.associateBy { it.id }
            val partyById = parties.associateBy { it.id }

            val sanitized = clean.replace("*", "").replace("%", "").replace("(", "").replace(")", "")
            val encodedPattern = URLEncoder.encode("*$sanitized*", "UTF-8")
            val hits = ArrayList<SocialSearchHit>()

            val dmJson = call(
                "GET",
                "$PROJECT_URL/rest/v1/messages?select=id,sender,recipient,body,created_at" +
                    "&or=(sender.eq.$me,recipient.eq.$me)" +
                    "&body=ilike.$encodedPattern" +
                    "&order=created_at.desc&limit=80"
            )
            dmJson?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
                val row = el as? JsonObject ?: return@forEach
                val id = row.longOr("id", 0L)
                if (id in deleted) return@forEach
                val sender = row.stringOrNull("sender") ?: return@forEach
                val recipient = row.stringOrNull("recipient") ?: return@forEach
                val peerId = if (sender == me) recipient else sender
                if (peerId in blocked) return@forEach
                val rawBody = row.stringOrNull("body") ?: return@forEach
                if (rawBody.startsWith(SocialEnvelopeCodec.PARTY_INVITE_PREFIX)) return@forEach

                if (rawBody.startsWith(SocialEnvelopeCodec.PARTY_CHAT_PREFIX)) {
                    val decoded = SocialEnvelopeCodec.decodeFallbackPartyChat(
                        rawBody, id, parseTimestamp(row.stringOrNull("created_at") ?: "")
                    ) ?: return@forEach
                    if (!decoded.body.contains(clean, ignoreCase = true)) return@forEach
                    val pTitle = partyById[decoded.partyId]?.title ?: "Party chat"
                    hits.add(
                        SocialSearchHit(
                            messageId = id,
                            peerId = null,
                            partyId = decoded.partyId,
                            conversationTitle = pTitle,
                            senderName = if (decoded.sender == me) "You" else (decoded.senderName ?: "Member"),
                            body = decoded.body,
                            atMs = decoded.atMs
                        )
                    )
                    return@forEach
                }

                val peerName = friendById[peerId]?.effectiveName ?: "Player"
                hits.add(
                    SocialSearchHit(
                        messageId = id,
                        peerId = peerId,
                        partyId = null,
                        conversationTitle = peerName,
                        senderName = if (sender == me) "You" else peerName,
                        body = rawBody,
                        atMs = parseTimestamp(row.stringOrNull("created_at") ?: "")
                    )
                )
            }

            if (hasPartyMessagesTable != false && parties.isNotEmpty()) {
                val pResp = callRaw(
                    "GET",
                    "$PROJECT_URL/rest/v1/party_messages?select=id,party_id,sender,body,created_at" +
                        "&body=ilike.$encodedPattern&order=created_at.desc&limit=50"
                )
                if (pResp.code in 200..299) {
                    hasPartyMessagesTable = true
                    pResp.json?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
                        val row = el as? JsonObject ?: return@forEach
                        val id = row.longOr("id", 0L)
                        if (id in deleted) return@forEach
                        val pId = row.stringOrNull("party_id") ?: return@forEach
                        val sender = row.stringOrNull("sender") ?: return@forEach
                        val pTitle = partyById[pId]?.title ?: "Party"
                        val sName = if (sender == me) "You" else (friendById[sender]?.effectiveName ?: "Member")
                        hits.add(
                            SocialSearchHit(
                                messageId = id,
                                peerId = null,
                                partyId = pId,
                                conversationTitle = pTitle,
                                senderName = sName,
                                body = row.stringOrNull("body") ?: "",
                                atMs = parseTimestamp(row.stringOrNull("created_at") ?: "")
                            )
                        )
                    }
                } else if (pResp.code == 404) {
                    hasPartyMessagesTable = false
                }
            }

            hits.distinctBy { "${it.partyId ?: it.peerId}:${it.messageId}" }
                .sortedByDescending { it.atMs }
        } ?: emptyList()
    }

    // ---- image downscaling & 30-day cleanup -------------------------------------------------

    private fun downscaleAttachmentToJpeg(uri: Uri, maxDim: Int = 1280, quality: Int = 82): ByteArray? {
        val ctx = appContext ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDim * 2) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val longest = max(decoded.width, decoded.height)
            val scaled = if (longest > maxDim) {
                val ratio = maxDim.toFloat() / longest.toFloat()
                val nw = (decoded.width * ratio).roundToInt().coerceAtLeast(1)
                val nh = (decoded.height * ratio).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, nw, nh, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else decoded

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            scaled.recycle()
            out.toByteArray()
        } catch (t: Throwable) {
            Log.w(TAG, "Attachment downscale failed", t)
            null
        }
    }

    private fun saveOutboxAttachmentLocally(bytes: ByteArray): String? {
        val ctx = appContext ?: return null
        return runCatching {
            val dir = File(ctx.cacheDir, "social_outbox").apply { mkdirs() }
            val file = File(dir, "att_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    /** Runs at most once every 24h to prune chat attachments older than 30 days on the free tier. */
    private suspend fun maybeRunPeriodicMediaCleanup() {
        val me = userId ?: return
        val tok = accessToken ?: return
        val lastRun = prefs()?.getLong("media_cleanup_$me", 0L) ?: 0L
        val now = System.currentTimeMillis()
        if (now - lastRun < 24L * 60L * 60L * 1000L) return
        prefs()?.edit()?.putLong("media_cleanup_$me", now)?.apply()

        // 1. Null out attachment_url on DB rows older than 30 days (RPC if deployed, plus own rows).
        callRaw("POST", "$PROJECT_URL/rest/v1/rpc/prune_chat_media", "{}")
        val cutoffIso = isoAt(now - THIRTY_DAYS_MS)
        callRaw(
            "PATCH",
            "$PROJECT_URL/rest/v1/messages?sender=eq.$me&attachment_url=not.is.null&created_at=lt.$cutoffIso",
            JsonObject().apply { add("attachment_url", com.google.gson.JsonNull.INSTANCE) }.toString(),
            extraHeaders = mapOf("Prefer" to "return=minimal")
        )

        // 2. Delete own storage objects `chat-att-<timestamp>.jpg` older than 30 days in `social-media`.
        val listResp = callRaw(
            "POST",
            "$PROJECT_URL/storage/v1/object/list/social-media",
            JsonObject().apply {
                addProperty("prefix", "$me/")
                addProperty("limit", 100)
            }.toString()
        )
        val arr = listResp.json?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        val stalePrefixes = JsonArray()
        arr.forEach { el ->
            val obj = el as? JsonObject ?: return@forEach
            val name = obj.stringOrNull("name") ?: return@forEach
            if (!name.startsWith("chat-att-")) return@forEach
            val ts = name.removePrefix("chat-att-").substringBefore('.').toLongOrNull()
                ?: parseTimestamp(obj.stringOrNull("created_at") ?: "")
            if (ts in 1..(now - THIRTY_DAYS_MS)) {
                stalePrefixes.add("$me/$name")
            }
        }
        if (stalePrefixes.size() > 0) {
            val delBody = JsonObject().apply { add("prefixes", stalePrefixes) }.toString()
            callRaw("DELETE", "$PROJECT_URL/storage/v1/object/social-media", delBody)
        }
    }

    // ---- parties (create, invite, accept/decline in Activity, party chat, transfer, kick, cap, expire)

    suspend fun createParty(
        title: String,
        instanceName: String?,
        versionId: String?,
        mcVersion: String?,
        loader: String?,
        modpackSource: String? = null,
        modpackId: String? = null,
        modpackIconUrl: String? = null,
        serverAddress: String? = null,
        maxMembers: Int = 4,
        expiryMinutes: Int = 120,
        isPublic: Boolean = false
    ): SocialParty? {
        val cleanTitle = title.trim().take(80)
        if (cleanTitle.isEmpty()) {
            lastError = "Give the party a title"
            return null
        }
        val cap = maxMembers.coerceIn(2, 16)
        val expiresMs = System.currentTimeMillis() + expiryMinutes.coerceIn(15, 720) * 60_000L
        val expiresIso = isoAt(expiresMs)

        val createdId = attempt {
            ensureToken()
            val me = userId ?: return@attempt null
            val packedVersion = SocialEnvelopeCodec.packVersionWithMeta(
                SocialEnvelopeCodec.PackedVersionMeta(
                    versionId = versionId,
                    loader = loader,
                    modpackSource = modpackSource,
                    modpackId = modpackId,
                    modpackIconUrl = modpackIconUrl,
                    serverAddress = serverAddress,
                    maxMembers = cap,
                    isPublic = isPublic
                )
            )

            var partyRow: JsonObject? = null
            if (hasExtendedPartiesColumns != false) {
                val extendedPayload = JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("host", me)
                        addProperty("title", cleanTitle)
                        instanceName?.takeIf { it.isNotBlank() }?.let { addProperty("instance", it) }
                        versionId?.takeIf { it.isNotBlank() }?.let { addProperty("version", it) }
                        mcVersion?.takeIf { it.isNotBlank() }?.let { addProperty("mc_version", it) }
                        loader?.takeIf { it.isNotBlank() }?.let { addProperty("loader", it) }
                        modpackSource?.takeIf { it.isNotBlank() }?.let { addProperty("modpack_source", it) }
                        modpackId?.takeIf { it.isNotBlank() }?.let { addProperty("modpack_id", it) }
                        modpackIconUrl?.takeIf { it.isNotBlank() }?.let { addProperty("modpack_icon_url", it) }
                        serverAddress?.takeIf { it.isNotBlank() }?.let { addProperty("server_address", it) }
                        addProperty("max_members", cap)
                        addProperty("is_public", isPublic)
                        addProperty("expires_at", expiresIso)
                    })
                }
                val r = callRaw(
                    "POST",
                    "$PROJECT_URL/rest/v1/parties",
                    extendedPayload.toString(),
                    extraHeaders = mapOf("Prefer" to "return=representation")
                )
                if (r.code in 200..299) {
                    hasExtendedPartiesColumns = true
                    partyRow = r.json?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull() as? JsonObject
                } else if (r.body.contains("column", ignoreCase = true) || r.code == 400) {
                    hasExtendedPartiesColumns = false
                }
            }

            if (partyRow == null) {
                val basePayload = JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("host", me)
                        addProperty("title", cleanTitle)
                        instanceName?.takeIf { it.isNotBlank() }?.let { addProperty("instance", it) }
                        addProperty("version", packedVersion)
                        mcVersion?.takeIf { it.isNotBlank() }?.let { addProperty("mc_version", it) }
                        addProperty("expires_at", expiresIso)
                    })
                }
                val r = callRaw(
                    "POST",
                    "$PROJECT_URL/rest/v1/parties",
                    basePayload.toString(),
                    extraHeaders = mapOf("Prefer" to "return=representation")
                )
                if (r.code !in 200..299) {
                    lastError = serverMessage(r.body) ?: "Could not create party"
                    return@attempt null
                }
                partyRow = r.json?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull() as? JsonObject
            }

            val pid = partyRow?.stringOrNull("id") ?: return@attempt null
            // Ensure host is also in party_members so member count and chat membership match.
            callRaw(
                "POST",
                "$PROJECT_URL/rest/v1/party_members?on_conflict=party_id,member",
                JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("party_id", pid)
                        addProperty("member", me)
                    })
                }.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
            )
            if (isPublic) publishFallbackLfgEntry(pid, cleanTitle, instanceName, versionId, mcVersion, loader, modpackSource, modpackId, modpackIconUrl, serverAddress, cap, expiresMs)
            pid
        } ?: return null

        refreshParties()
        syncRichPresenceToServer()
        if (isPublic) loadLfgBoard()
        return parties.firstOrNull { it.id == createdId }
    }

    suspend fun refreshParties() {
        attempt {
            ensureToken()
            val me = userId ?: return@attempt Unit
            val nowMs = System.currentTimeMillis()
            val nowIso = isoAt(nowMs)

            // Auto-expire cleanup: delete any of my hosted parties whose expiry has passed.
            callRaw("DELETE", "$PROJECT_URL/rest/v1/parties?host=eq.$me&expires_at=lte.$nowIso")

            val json = call(
                "GET",
                "$PROJECT_URL/rest/v1/parties?select=*&expires_at=gt.$nowIso&order=created_at.desc&limit=30"
            )
            val rows = json?.takeIf { it.isJsonArray }?.asJsonArray
            if (rows == null || rows.size() == 0) {
                parties = emptyList()
                return@attempt Unit
            }

            val parsedParties = ArrayList<SocialParty>()
            val partyIds = ArrayList<String>()
            rows.forEach { el ->
                val row = el as? JsonObject ?: return@forEach
                val p = parsePartyRow(row) ?: return@forEach
                if (!p.isExpired(nowMs)) {
                    parsedParties.add(p)
                    partyIds.add(p.id)
                }
            }
            if (parsedParties.isEmpty()) {
                parties = emptyList()
                return@attempt Unit
            }

            // Load party members + profile details
            val quotedPids = partyIds.joinToString(",") { "\"$it\"" }
            val memRows = call(
                "GET",
                "$PROJECT_URL/rest/v1/party_members?party_id=in.($quotedPids)&select=party_id,member"
            )?.takeIf { it.isJsonArray }?.asJsonArray

            val memberIdsByParty = HashMap<String, MutableSet<String>>()
            parsedParties.forEach { p ->
                memberIdsByParty.getOrPut(p.id) { LinkedHashSet() }.add(p.hostId)
            }
            memRows?.forEach { el ->
                val r = el as? JsonObject ?: return@forEach
                val pid = r.stringOrNull("party_id") ?: return@forEach
                val uid = r.stringOrNull("member") ?: return@forEach
                memberIdsByParty.getOrPut(pid) { LinkedHashSet() }.add(uid)
            }

            val allUserIds = memberIdsByParty.values.flatten().distinct()
            val profileMap = fetchSimpleProfileMap(allUserIds)

            parties = parsedParties.map { party ->
                val uids = memberIdsByParty[party.id] ?: setOf(party.hostId)
                val members = uids.map { uid ->
                    val prof = profileMap[uid]
                    SocialPartyMember(
                        userId = uid,
                        username = prof?.cleanDisplayName ?: "Player",
                        mcUsername = prof?.mcUsername,
                        avatarUrl = prof?.avatarUrl,
                        isHost = uid == party.hostId,
                        online = prof?.online ?: false,
                        statusLine = prof?.statusLine,
                        role = if (uid == me) (myRole ?: prof?.effectiveRole) else prof?.effectiveRole,
                        presenceMode = prof?.presenceMode ?: SocialPresenceMode.ONLINE
                    )
                }.sortedByDescending { it.isHost }
                val hostProf = profileMap[party.hostId]
                val hostRole = if (party.hostId == me) (myRole ?: hostProf?.effectiveRole) else (hostProf?.effectiveRole ?: party.effectiveHostRole)
                party.copy(
                    hostName = hostProf?.cleanDisplayName ?: party.cleanHostName,
                    hostAvatar = hostProf?.avatarUrl ?: party.hostAvatar,
                    hostRole = hostRole,
                    memberCount = members.size,
                    members = members
                )
            }
        }
    }

    private fun parsePartyRow(row: JsonObject): SocialParty? {
        val id = row.stringOrNull("id") ?: return null
        val hostId = row.stringOrNull("host") ?: return null
        val rawVersion = row.stringOrNull("version")
        val unpacked = SocialEnvelopeCodec.unpackVersionWithMeta(rawVersion)
        val expiresMs = row.longOr("expires_at_ms", 0L).takeIf { it > 0L }
            ?: parseTimestamp(row.stringOrNull("expires_at") ?: "")
        val createdMs = row.longOr("created_at_ms", 0L).takeIf { it > 0L }
            ?: parseTimestamp(row.stringOrNull("created_at") ?: "")
        val rawHostName = row.stringOrNull("host_name") ?: "Host"
        val (cleanHost, hostRole) = SocialRole.extract(rawHostName, row.stringOrNull("host_role"))

        return SocialParty(
            id = id,
            hostId = hostId,
            hostName = cleanHost ?: "Host",
            hostAvatar = row.stringOrNull("host_avatar"),
            hostRole = if (hostId == userId) (myRole ?: hostRole) else hostRole,
            title = row.stringOrNull("title") ?: "Party",
            instanceName = row.stringOrNull("instance"),
            versionId = unpacked.versionId,
            mcVersion = row.stringOrNull("mc_version"),
            loader = row.stringOrNull("loader") ?: unpacked.loader,
            modpackSource = row.stringOrNull("modpack_source") ?: unpacked.modpackSource,
            modpackId = row.stringOrNull("modpack_id") ?: unpacked.modpackId,
            modpackIconUrl = row.stringOrNull("modpack_icon_url") ?: unpacked.modpackIconUrl,
            serverAddress = row.stringOrNull("server_address") ?: unpacked.serverAddress,
            maxMembers = row.get("max_members")?.takeIf { !it.isJsonNull }?.asInt ?: unpacked.maxMembers,
            isPublic = row.get("is_public")?.takeIf { !it.isJsonNull }?.asBoolean ?: unpacked.isPublic,
            memberCount = row.get("member_count")?.takeIf { !it.isJsonNull }?.asInt ?: 1,
            expiresAtMs = expiresMs,
            createdAtMs = createdMs
        )
    }

    private suspend fun fetchSimpleProfileMap(userIds: List<String>): Map<String, SocialFriend> {
        if (userIds.isEmpty()) return emptyMap()
        val quoted = userIds.distinct().joinToString(",") { "\"$it\"" }
        val resp = callRaw(
            "GET",
            "$PROJECT_URL/rest/v1/profiles?id=in.($quoted)" +
                "&select=id,username,name,display_name,mc_username,avatar_url,last_seen,status_line,status_text,mc_version,joinable"
        )
        val json = if (resp.code in 200..299) {
            resp.json
        } else {
            call(
                "GET",
                "$PROJECT_URL/rest/v1/profiles?id=in.($quoted)" +
                    "&select=id,username,name,display_name,mc_username,avatar_url,last_seen"
            )
        }
        val out = HashMap<String, SocialFriend>()
        json?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
            val r = el as? JsonObject ?: return@forEach
            val uid = r.stringOrNull("id") ?: return@forEach
            val uname = r.stringOrNull("username")
            val rawName = r.stringOrNull("name")?.takeIf { it.isNotBlank() }
                ?: r.stringOrNull("display_name")?.takeIf { it.isNotBlank() }
            val (cleanName, detectedRole) = SocialRole.extract(
                rawName,
                r.stringOrNull("role_badge"),
                if (uid == userId) email else null
            )
            val display = getFriendNickname(uid)
                ?: cleanName
                ?: uname
                ?: r.stringOrNull("mc_username")
                ?: "Player"
            val parsedPresence = SocialPresenceCodec.parse(
                r.stringOrNull("status_line") ?: r.stringOrNull("status_text"),
                r.stringOrNull("presence_mode")
            )
            out[uid] = SocialFriend(
                id = uid,
                mcUsername = r.stringOrNull("mc_username") ?: uname ?: display,
                displayName = display,
                lastSeenMs = parseTimestamp(r.stringOrNull("last_seen") ?: ""),
                username = uname,
                avatarUrl = r.stringOrNull("avatar_url"),
                statusLine = parsedPresence.customText,
                role = detectedRole,
                presenceMode = parsedPresence.mode
            )
        }
        return out
    }

    suspend fun inviteFriendToParty(party: SocialParty, friendId: String): Boolean {
        if (party.isExpired()) {
            lastError = "This party has expired"
            return false
        }
        if (party.memberCount >= party.maxMembers) {
            lastError = "Party is full (${party.maxMembers}/${party.maxMembers})"
            return false
        }
        return attempt {
            ensureToken()
            val me = userId ?: return@attempt false
            val myBase = myEffectiveDisplayName()
            val myLabel = SocialRole.encodeTaggedName(myBase, myRole, myBase) ?: myBase
            val invite = SocialPartyInvite(
                id = "${party.id}:$friendId:${System.currentTimeMillis() / 60_000L}",
                partyId = party.id,
                fromUserId = me,
                fromName = myLabel,
                fromMcUsername = mcUsername,
                fromAvatar = myProfile?.avatarUrl,
                fromRole = myRole,
                partyTitle = party.title,
                instanceName = party.instanceName,
                versionId = party.versionId,
                mcVersion = party.mcVersion,
                loader = party.loader,
                modpackSource = party.modpackSource,
                modpackId = party.modpackId,
                modpackIconUrl = party.modpackIconUrl,
                serverAddress = party.serverAddress,
                maxMembers = party.maxMembers,
                expiresAtMs = party.expiresAtMs
            )
            val encoded = SocialEnvelopeCodec.encodePartyInvite(invite)
            val sent = call(
                "POST",
                "$PROJECT_URL/rest/v1/messages",
                JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("sender", me)
                        addProperty("recipient", friendId)
                        addProperty("body", encoded)
                    })
                }.toString(),
                extraHeaders = mapOf("Prefer" to "return=minimal")
            ) != null
            if (sent) {
                SocialRealtime.sendPartySignal("invite", party.id, friendId)
            }
            sent
        } ?: false
    }

    /** Loads active incoming party invites so accept/decline lands right in Activity. */
    suspend fun refreshPartyInvites() {
        attempt {
            ensureToken()
            val me = userId ?: return@attempt Unit
            val resolved = resolvedInviteIds()
            val blocked = blockedUsers.map { it.userId }.toSet()
            val prefixEncoded = URLEncoder.encode("${SocialEnvelopeCodec.PARTY_INVITE_PREFIX}*", "UTF-8")
            val json = call(
                "GET",
                "$PROJECT_URL/rest/v1/messages?select=id,sender,body,created_at" +
                    "&recipient=eq.$me&body=like.$prefixEncoded&order=created_at.desc&limit=40"
            )
            val rows = json?.takeIf { it.isJsonArray }?.asJsonArray ?: run {
                partyInvites = emptyList()
                return@attempt Unit
            }
            val now = System.currentTimeMillis()
            val activeIds = parties.map { it.id }.toSet()
            val parsed = ArrayList<SocialPartyInvite>()

            rows.forEach { el ->
                val row = el as? JsonObject ?: return@forEach
                val msgId = row.longOr("id", 0L)
                val sender = row.stringOrNull("sender") ?: return@forEach
                if (sender in blocked) return@forEach
                val body = row.stringOrNull("body") ?: return@forEach
                val inv = SocialEnvelopeCodec.decodePartyInvite(body, msgId, sender) ?: return@forEach
                if (inv.id in resolved) return@forEach
                if (inv.partyId in activeIds) return@forEach
                if (inv.isExpired(now)) return@forEach
                if (parsed.none { it.partyId == inv.partyId }) {
                    parsed.add(inv)
                }
            }
            partyInvites = parsed
        }
    }

    suspend fun respondToPartyInvite(invite: SocialPartyInvite, accept: Boolean): Boolean {
        markInviteResolved(invite.id)
        val ok = attempt {
            ensureToken()
            val me = userId ?: return@attempt false
            invite.messageRowId?.let { rowId ->
                callRaw(
                    "PATCH",
                    "$PROJECT_URL/rest/v1/messages?id=eq.$rowId&recipient=eq.$me",
                    JsonObject().apply { addProperty("read_at", isoNow()) }.toString(),
                    extraHeaders = mapOf("Prefer" to "return=minimal")
                )
            }
            if (!accept) return@attempt true
            joinPartyByIdInternal(
                partyId = invite.partyId,
                maxMembers = invite.maxMembers,
                hostId = invite.fromUserId,
                hostName = invite.fromName,
                hostMcUsername = invite.fromMcUsername,
                hostAvatar = invite.fromAvatar
            )
        } ?: false
        refreshPartyInvites()
        refreshParties()
        unreadCount()
        return ok
    }

    suspend fun joinPublicParty(party: SocialParty): Boolean {
        if (party.isExpired()) {
            lastError = "That LFG party has expired"
            return false
        }
        val ok = attempt {
            ensureToken()
            joinPartyByIdInternal(
                partyId = party.id,
                maxMembers = party.maxMembers,
                hostId = party.hostId,
                hostName = party.hostName,
                hostMcUsername = null,
                hostAvatar = party.hostAvatar
            )
        } ?: false
        refreshParties()
        loadLfgBoard()
        return ok
    }

    private suspend fun joinPartyByIdInternal(
        partyId: String,
        maxMembers: Int,
        hostId: String,
        hostName: String,
        hostMcUsername: String?,
        hostAvatar: String?
    ): Boolean {
        val me = userId ?: return false
        val countJson = call(
            "GET",
            "$PROJECT_URL/rest/v1/party_members?party_id=eq.$partyId&select=member"
        )?.takeIf { it.isJsonArray }?.asJsonArray
        val existingCount = countJson?.size() ?: 0
        if (existingCount >= maxMembers && countJson?.none { (it as? JsonObject)?.stringOrNull("member") == me } == true) {
            lastError = "Party is already full ($maxMembers/$maxMembers)"
            return false
        }
        val r = callRaw(
            "POST",
            "$PROJECT_URL/rest/v1/party_members?on_conflict=party_id,member",
            JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("party_id", partyId)
                    addProperty("member", me)
                })
            }.toString(),
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
        )
        if (r.code !in 200..299) {
            lastError = serverMessage(r.body) ?: "Could not join party (it may be full or expired)"
            return false
        }
        recordRecentPlayer(hostId, hostName, null, hostMcUsername, hostAvatar, "Party session")
        SocialRealtime.sendPartySignal("member_joined", partyId)
        return true
    }

    suspend fun leaveParty(party: SocialParty): Boolean {
        val done = attempt {
            ensureToken()
            val me = userId ?: return@attempt false
            if (party.hostId == me) {
                val successor = party.members.firstOrNull { it.userId != me }
                if (successor != null) {
                    val transferred = transferPartyHostInternal(party.id, successor.userId)
                    if (transferred) {
                        callRaw("DELETE", "$PROJECT_URL/rest/v1/party_members?party_id=eq.${party.id}&member=eq.$me")
                        SocialRealtime.sendPartySignal("host_transferred", party.id, successor.userId)
                        return@attempt true
                    }
                }
                callRaw("DELETE", "$PROJECT_URL/rest/v1/parties?id=eq.${party.id}&host=eq.$me")
                SocialRealtime.sendPartySignal("party_closed", party.id)
                true
            } else {
                val r = callRaw("DELETE", "$PROJECT_URL/rest/v1/party_members?party_id=eq.${party.id}&member=eq.$me")
                SocialRealtime.sendPartySignal("member_left", party.id)
                r.code in 200..299
            }
        } ?: false
        refreshParties()
        syncRichPresenceToServer()
        return done
    }

    suspend fun transferPartyHost(partyId: String, newHostId: String): Boolean {
        val ok = attempt {
            ensureToken()
            transferPartyHostInternal(partyId, newHostId)
        } ?: false
        if (ok) {
            SocialRealtime.sendPartySignal("host_transferred", partyId, newHostId)
            refreshParties()
            syncRichPresenceToServer()
        }
        return ok
    }

    private suspend fun transferPartyHostInternal(partyId: String, newHostId: String): Boolean {
        val me = userId ?: return false
        val rpc = callRaw(
            "POST",
            "$PROJECT_URL/rest/v1/rpc/transfer_party_host",
            JsonObject().apply {
                addProperty("p_party", partyId)
                addProperty("p_new_host", newHostId)
            }.toString()
        )
        if (rpc.code in 200..299 && rpc.json?.isJsonPrimitive == true && rpc.json.asBoolean) {
            return true
        }
        val patch = callRaw(
            "PATCH",
            "$PROJECT_URL/rest/v1/parties?id=eq.$partyId&host=eq.$me",
            JsonObject().apply { addProperty("host", newHostId) }.toString(),
            extraHeaders = mapOf("Prefer" to "return=minimal")
        )
        if (patch.code !in 200..299) {
            lastError = serverMessage(patch.body) ?: "Could not transfer party host"
            return false
        }
        return true
    }

    suspend fun kickPartyMember(partyId: String, memberId: String): Boolean {
        val ok = attempt {
            ensureToken()
            val rpc = callRaw(
                "POST",
                "$PROJECT_URL/rest/v1/rpc/kick_party_member",
                JsonObject().apply {
                    addProperty("p_party", partyId)
                    addProperty("p_member", memberId)
                }.toString()
            )
            if (rpc.code in 200..299 && rpc.json?.isJsonPrimitive == true && rpc.json.asBoolean) {
                return@attempt true
            }
            val del = callRaw(
                "DELETE",
                "$PROJECT_URL/rest/v1/party_members?party_id=eq.$partyId&member=eq.$memberId"
            )
            if (del.code !in 200..299) {
                lastError = serverMessage(del.body) ?: "Could not remove member"
                return@attempt false
            }
            true
        } ?: false
        if (ok) {
            SocialRealtime.sendPartySignal("member_kicked", partyId, memberId)
            refreshParties()
        }
        return ok
    }

    suspend fun loadPartyMessages(partyId: String): List<SocialMessage> {
        return attempt {
            ensureToken()
            val me = userId ?: return@attempt emptyList<SocialMessage>()
            val deleted = deletedForMeSet()
            val party = parties.firstOrNull { it.id == partyId }
            val memberById = party?.members?.associateBy { it.userId } ?: emptyMap()
            val collected = ArrayList<SocialMessage>()

            if (hasPartyMessagesTable != false) {
                val r = callRaw(
                    "GET",
                    "$PROJECT_URL/rest/v1/party_messages?party_id=eq.$partyId" +
                        "&select=id,party_id,sender,body,reply_to,attachment_url,edited_at,created_at" +
                        "&order=created_at.asc&limit=250"
                )
                if (r.code in 200..299) {
                    hasPartyMessagesTable = true
                    r.json?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
                        val row = el as? JsonObject ?: return@forEach
                        val id = row.longOr("id", 0L)
                        if (id in deleted) return@forEach
                        val sender = row.stringOrNull("sender") ?: return@forEach
                        val mem = memberById[sender]
                        val fr = friends.firstOrNull { it.id == sender }
                        collected.add(
                            SocialMessage(
                                id = id,
                                sender = sender,
                                recipient = "",
                                body = row.stringOrNull("body") ?: "",
                                atMs = parseTimestamp(row.stringOrNull("created_at") ?: ""),
                                editedAtMs = parseTimestamp(row.stringOrNull("edited_at") ?: ""),
                                replyTo = row.get("reply_to")?.takeIf { !it.isJsonNull }?.asLong,
                                attachmentUrl = row.stringOrNull("attachment_url"),
                                partyId = partyId,
                                senderName = mem?.cleanName ?: fr?.effectiveName ?: "Member",
                                senderRole = if (sender == me) myRole else (mem?.effectiveRole ?: fr?.effectiveRole)
                            )
                        )
                    }
                } else if (r.code == 404) {
                    hasPartyMessagesTable = false
                }
            }

            if (hasPartyMessagesTable == false) {
                val prefixEncoded = URLEncoder.encode("${SocialEnvelopeCodec.PARTY_CHAT_PREFIX}*", "UTF-8")
                val r = callRaw(
                    "GET",
                    "$PROJECT_URL/rest/v1/messages?select=id,sender,body,created_at" +
                        "&or=(sender.eq.$me,recipient.eq.$me)&body=like.$prefixEncoded" +
                        "&order=created_at.asc&limit=250"
                )
                val seenKeys = HashSet<String>()
                r.json?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
                    val row = el as? JsonObject ?: return@forEach
                    val id = row.longOr("id", 0L)
                    if (id in deleted) return@forEach
                    val raw = row.stringOrNull("body") ?: return@forEach
                    val decoded = SocialEnvelopeCodec.decodeFallbackPartyChat(
                        raw, id, parseTimestamp(row.stringOrNull("created_at") ?: "")
                    ) ?: return@forEach
                    if (decoded.partyId != partyId) return@forEach
                    val dedupeKey = "${decoded.sender}:${decoded.atMs / 2000L}:${decoded.body}"
                    if (seenKeys.add(dedupeKey)) {
                        collected.add(decoded)
                    }
                }
            }

            broadcastPartyMessages[partyId]?.let { live ->
                synchronized(live) {
                    live.forEach { bMsg ->
                        if (collected.none { it.sender == bMsg.sender && it.body == bMsg.body && kotlin.math.abs(it.atMs - bMsg.atMs) < 5000L }) {
                            collected.add(bMsg)
                        }
                    }
                }
            }

            val byId = collected.associateBy { it.id }
            val enriched = collected.sortedBy { it.atMs }.map { m ->
                val parent = m.replyTo?.let { byId[it] }
                if (parent != null) m.copy(replyPreview = parent.body.take(90)) else m
            }.toMutableList()

            readOutbox().filter { it.partyId == partyId }.forEachIndexed { idx, q ->
                enriched.add(
                    SocialMessage(
                        id = -(q.createdAtMs + idx),
                        sender = me,
                        recipient = "",
                        body = q.body,
                        atMs = q.createdAtMs,
                        replyTo = q.replyTo,
                        attachmentUrl = q.attachmentRemoteUrl ?: q.attachmentLocalPath,
                        partyId = partyId,
                        senderName = "You",
                        pendingOffline = true,
                        localTempId = q.localId
                    )
                )
            }
            enriched
        } ?: emptyList()
    }

    fun getCachedPartyMessages(partyId: String): List<SocialMessage> {
        val list = broadcastPartyMessages[partyId] ?: return emptyList()
        val deleted = deletedForMeSet()
        return synchronized(list) {
            list.filter { it.id !in deleted }.sortedBy { it.atMs }
        }
    }

    suspend fun sendPartyMessage(
        partyId: String,
        text: String,
        replyTo: Long? = null,
        attachmentUri: Uri? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val clean = text.trim().ifEmpty { if (attachmentUri != null) "📷 Image" else "" }
        if (clean.isEmpty()) return@withContext false
        val meQuick = userId ?: run {
            ensureToken()
            userId
        } ?: return@withContext false

        val tempId = UUID.randomUUID().toString()
        val nowMs = System.currentTimeMillis()
        val cleanBody = clean.take(2000)
        val optimisticMsg = SocialMessage(
            id = optimisticIdGenerator.decrementAndGet(),
            sender = meQuick,
            recipient = "",
            body = cleanBody,
            atMs = nowMs,
            replyTo = replyTo,
            attachmentUrl = attachmentUri?.toString(),
            partyId = partyId,
            senderName = myEffectiveDisplayName(),
            senderRole = myRole,
            localTempId = tempId
        )
        val liveList = broadcastPartyMessages.getOrPut(partyId) { ArrayList() }
        synchronized(liveList) { liveList.add(optimisticMsg) }
        notifyStopTyping(partyId = partyId)
        withContext(Dispatchers.Main) { partyMessageRevision++ }

        ensureToken()

        var localPath: String? = null
        var remoteUrl: String? = null
        if (attachmentUri != null) {
            val bytes = downscaleAttachmentToJpeg(attachmentUri) ?: run {
                synchronized(liveList) { liveList.removeAll { it.localTempId == tempId } }
                withContext(Dispatchers.Main) { partyMessageRevision++ }
                lastError = "Could not process image"
                return@withContext false
            }
            remoteUrl = if (isNetworkAvailable) uploadImage("chat-att", bytes, "jpg") else null
            if (remoteUrl == null) localPath = saveOutboxAttachmentLocally(bytes)
        }

        val sent = sendPartyMessageInternal(
            partyId = partyId,
            cleanText = cleanBody,
            replyTo = replyTo,
            remoteAttachmentUrl = remoteUrl,
            queueOnFail = true,
            localAttachmentPath = localPath,
            tempId = tempId
        )
        withContext(Dispatchers.Main) { partyMessageRevision++ }
        sent
    }

    private suspend fun sendPartyMessageInternal(
        partyId: String,
        cleanText: String,
        replyTo: Long?,
        remoteAttachmentUrl: String?,
        queueOnFail: Boolean,
        localAttachmentPath: String? = null,
        tempId: String? = null
    ): Boolean {
        val me = userId ?: return false
        val myBase = myEffectiveDisplayName()
        val myName = SocialRole.encodeTaggedName(myBase, myRole, myBase) ?: myBase
        val nowMs = System.currentTimeMillis()

        if (isNetworkAvailable && hasPartyMessagesTable != false) {
            val payload = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("party_id", partyId)
                    addProperty("sender", me)
                    addProperty("body", cleanText)
                    replyTo?.takeIf { it > 0L }?.let { addProperty("reply_to", it) }
                    remoteAttachmentUrl?.let { addProperty("attachment_url", it) }
                })
            }
            val r = callRaw(
                "POST",
                "$PROJECT_URL/rest/v1/party_messages",
                payload.toString(),
                extraHeaders = mapOf("Prefer" to "return=representation")
            )
            if (r.code in 200..299) {
                hasPartyMessagesTable = true
                val insertedId = (r.json?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull() as? JsonObject)
                    ?.longOr("id", nowMs) ?: nowMs
                val wireMsg = SocialMessage(
                    id = insertedId,
                    sender = me,
                    recipient = "",
                    body = cleanText,
                    atMs = nowMs,
                    replyTo = replyTo,
                    attachmentUrl = remoteAttachmentUrl,
                    partyId = partyId,
                    senderName = myName,
                    localTempId = tempId
                )
                broadcastPartyMessages[partyId]?.let { list ->
                    synchronized(list) {
                        val idx = list.indexOfFirst { !tempId.isNullOrBlank() && it.localTempId == tempId }
                        if (idx >= 0) list[idx] = wireMsg.copy(senderName = myBase, senderRole = myRole)
                    }
                }
                SocialRealtime.sendPartyChatBroadcast(wireMsg, tempId = tempId)
                return true
            } else if (r.code == 404) {
                hasPartyMessagesTable = false
            }
        }

        if (isNetworkAvailable && hasPartyMessagesTable == false) {
            val party = parties.firstOrNull { it.id == partyId }
            val targets = (party?.members?.map { it.userId }?.filter { it != me } ?: emptyList())
                .ifEmpty { listOf(me) }
            val encoded = SocialEnvelopeCodec.encodeFallbackPartyChat(
                partyId, me, myName, cleanText, replyTo, remoteAttachmentUrl, nowMs
            )
            val rows = JsonArray().apply {
                targets.forEach { target ->
                    add(JsonObject().apply {
                        addProperty("sender", me)
                        addProperty("recipient", target)
                        addProperty("body", encoded)
                    })
                }
            }
            val r = callRaw(
                "POST",
                "$PROJECT_URL/rest/v1/messages",
                rows.toString(),
                extraHeaders = mapOf("Prefer" to "return=minimal")
            )
            if (r.code in 200..299) {
                val wireMsg = SocialMessage(
                    id = nowMs,
                    sender = me,
                    recipient = "",
                    body = cleanText,
                    atMs = nowMs,
                    replyTo = replyTo,
                    attachmentUrl = remoteAttachmentUrl,
                    partyId = partyId,
                    senderName = myName,
                    localTempId = tempId
                )
                SocialRealtime.sendPartyChatBroadcast(wireMsg, tempId = tempId)
                return true
            }
        }

        if (queueOnFail) {
            enqueueOfflineItem(
                SocialOutboxItem(
                    localId = UUID.randomUUID().toString(),
                    partyId = partyId,
                    body = cleanText,
                    replyTo = replyTo,
                    attachmentLocalPath = localAttachmentPath,
                    attachmentRemoteUrl = remoteAttachmentUrl
                )
            )
            return true
        }
        return false
    }

    // ---- Discovery: LFG board & Friends' Server Hub -----------------------------------------

    suspend fun loadLfgBoard(mcVersionFilter: String? = null, loaderFilter: String? = null): List<SocialParty> {
        val board = attempt {
            ensureToken()
            val blocked = blockedUsers.map { it.userId }.toSet()
            val vClean = mcVersionFilter?.trim()?.takeIf { it.isNotEmpty() }
            val lClean = loaderFilter?.trim()?.takeIf { it.isNotEmpty() && !it.equals("All", ignoreCase = true) }

            if (hasLfgRpc != false) {
                val r = callRaw(
                    "POST",
                    "$PROJECT_URL/rest/v1/rpc/list_lfg_parties",
                    JsonObject().apply {
                        addProperty("p_mc_version", vClean ?: "")
                        addProperty("p_loader", lClean ?: "")
                    }.toString()
                )
                if (r.code in 200..299 && r.json?.isJsonArray == true) {
                    hasLfgRpc = true
                    val list = r.json.asJsonArray.mapNotNull { el ->
                        parsePartyRow(el as? JsonObject ?: return@mapNotNull null)
                    }.filter { it.hostId !in blocked && !it.isExpired() }
                    lfgParties = list
                    return@attempt list
                } else if (r.code == 404) {
                    hasLfgRpc = false
                }
            }

            // Fallback when RPC isn't deployed yet: read public parties + friends' published LFG cards in storage.
            val nowMs = System.currentTimeMillis()
            val nowIso = isoAt(nowMs)
            val fallback = ArrayList<SocialParty>()
            val qResp = callRaw("GET", "$PROJECT_URL/rest/v1/parties?select=*&expires_at=gt.$nowIso&order=created_at.desc&limit=50")
            qResp.json?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
                val p = parsePartyRow(el as? JsonObject ?: return@forEach) ?: return@forEach
                if (p.isPublic && !p.isExpired(nowMs) && p.hostId !in blocked) fallback.add(p)
            }

            val candidateOwners = (listOfNotNull(userId) + friends.map { it.id }).distinct()
            val profMap = fetchSimpleProfileMap(candidateOwners)
            candidateOwners.forEach { uid ->
                if (uid in blocked) return@forEach
                readPublicStorageJson("$uid/lfg_party.json")?.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                    val p = parsePartyRow(obj) ?: return@let
                    if (!p.isExpired(nowMs) && fallback.none { it.id == p.id }) {
                        val prof = profMap[uid]
                        fallback.add(
                            p.copy(
                                hostName = prof?.cleanDisplayName ?: p.cleanHostName,
                                hostAvatar = prof?.avatarUrl ?: p.hostAvatar,
                                hostRole = prof?.effectiveRole ?: p.effectiveHostRole
                            )
                        )
                    }
                }
            }

            val filtered = fallback.filter { p ->
                val okVer = vClean == null || p.mcVersion.equals(vClean, ignoreCase = true)
                val okLoader = lClean == null || p.loader.equals(lClean, ignoreCase = true)
                okVer && okLoader
            }.sortedByDescending { it.createdAtMs }
            lfgParties = filtered
            filtered
        } ?: emptyList()
        return board
    }

    private suspend fun publishFallbackLfgEntry(
        id: String,
        title: String,
        instanceName: String?,
        versionId: String?,
        mcVersion: String?,
        loader: String?,
        modpackSource: String?,
        modpackId: String?,
        modpackIconUrl: String?,
        serverAddress: String?,
        maxMembers: Int,
        expiresMs: Long
    ) {
        val me = userId ?: return
        val myBase = myEffectiveDisplayName()
        val taggedHost = SocialRole.encodeTaggedName(myBase, myRole, myBase) ?: myBase
        val obj = JsonObject().apply {
            addProperty("id", id)
            addProperty("host", me)
            addProperty("host_name", taggedHost)
            myRole?.let { addProperty("host_role", it.key) }
            myProfile?.avatarUrl?.let { addProperty("host_avatar", it) }
            addProperty("title", title)
            instanceName?.let { addProperty("instance", it) }
            versionId?.let { addProperty("version", it) }
            mcVersion?.let { addProperty("mc_version", it) }
            loader?.let { addProperty("loader", it) }
            modpackSource?.let { addProperty("modpack_source", it) }
            modpackId?.let { addProperty("modpack_id", it) }
            modpackIconUrl?.let { addProperty("modpack_icon_url", it) }
            serverAddress?.let { addProperty("server_address", it) }
            addProperty("max_members", maxMembers)
            addProperty("is_public", true)
            addProperty("expires_at_ms", expiresMs)
            addProperty("created_at_ms", System.currentTimeMillis())
        }
        writeOwnStorageJson("lfg_party.json", obj.toString())
    }

    suspend fun loadSharedServers(): List<SocialSharedServer> {
        val result = attempt {
            ensureToken()
            val me = userId ?: return@attempt emptyList<SocialSharedServer>()
            val blocked = blockedUsers.map { it.userId }.toSet()
            val friendIds = friends.filter { !it.pendingIncoming && !it.pendingOutgoing }.map { it.id }
            val owners = (listOf(me) + friendIds).distinct().filter { it !in blocked }
            val profMap = fetchSimpleProfileMap(owners)

            if (hasServerSharesTable != false) {
                val r = callRaw(
                    "GET",
                    "$PROJECT_URL/rest/v1/server_shares?select=*&order=created_at.desc&limit=80"
                )
                if (r.code in 200..299 && r.json?.isJsonArray == true) {
                    hasServerSharesTable = true
                    val list = r.json.asJsonArray.mapNotNull { el ->
                        val row = el as? JsonObject ?: return@mapNotNull null
                        val owner = row.stringOrNull("owner") ?: return@mapNotNull null
                        if (owner in blocked) return@mapNotNull null
                        val prof = profMap[owner]
                        SocialSharedServer(
                            id = row.longOr("id", 0L).toString(),
                            ownerId = owner,
                            ownerName = if (owner == me) myEffectiveDisplayName() else (prof?.cleanDisplayName ?: "Friend"),
                            ownerMcUsername = prof?.mcUsername,
                            ownerAvatar = prof?.avatarUrl,
                            ownerRole = if (owner == me) myRole else prof?.effectiveRole,
                            name = row.stringOrNull("name") ?: "Minecraft Server",
                            address = row.stringOrNull("address") ?: return@mapNotNull null,
                            mcVersion = row.stringOrNull("mc_version"),
                            loader = row.stringOrNull("loader"),
                            description = row.stringOrNull("description"),
                            createdAtMs = parseTimestamp(row.stringOrNull("created_at") ?: "")
                        )
                    }
                    sharedServers = list
                    return@attempt list
                } else if (r.code == 404) {
                    hasServerSharesTable = false
                }
            }

            // Storage JSON fallback (`<uid>/shared_servers.json`) so Server Hub works immediately!
            val fallback = ArrayList<SocialSharedServer>()
            owners.forEach { owner ->
                val prof = profMap[owner]
                val arr = readPublicStorageJson("$owner/shared_servers.json")
                    ?.takeIf { it.isJsonArray }?.asJsonArray ?: return@forEach
                arr.forEach { el ->
                    val obj = el as? JsonObject ?: return@forEach
                    val addr = obj.stringOrNull("address") ?: return@forEach
                    fallback.add(
                        SocialSharedServer(
                            id = obj.stringOrNull("id") ?: addr,
                            ownerId = owner,
                            ownerName = if (owner == me) myEffectiveDisplayName() else (prof?.cleanDisplayName ?: "Friend"),
                            ownerMcUsername = prof?.mcUsername,
                            ownerAvatar = prof?.avatarUrl,
                            ownerRole = if (owner == me) myRole else prof?.effectiveRole,
                            name = obj.stringOrNull("name") ?: addr,
                            address = addr,
                            mcVersion = obj.stringOrNull("mc_version"),
                            loader = obj.stringOrNull("loader"),
                            description = obj.stringOrNull("description"),
                            createdAtMs = obj.longOr("created_at_ms", 0L)
                        )
                    )
                }
            }
            val sorted = fallback.sortedByDescending { it.createdAtMs }
            sharedServers = sorted
            sorted
        } ?: emptyList()
        return result
    }

    suspend fun shareServer(
        name: String,
        address: String,
        mcVersion: String?,
        loader: String?,
        description: String?
    ): Boolean {
        val cleanName = name.trim().take(80)
        val cleanAddress = address.trim().removePrefix("http://").removePrefix("https://").trim('/').take(255)
        if (cleanName.isEmpty() || cleanAddress.length < 3) {
            lastError = "Enter a server name and address (e.g. play.example.net)"
            return false
        }
        val ok = attempt {
            ensureToken()
            val me = userId ?: return@attempt false
            if (hasServerSharesTable != false) {
                val r = callRaw(
                    "POST",
                    "$PROJECT_URL/rest/v1/server_shares",
                    JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("owner", me)
                            addProperty("name", cleanName)
                            addProperty("address", cleanAddress)
                            mcVersion?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("mc_version", it) }
                            loader?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("loader", it) }
                            description?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("description", it.take(240)) }
                        })
                    }.toString(),
                    extraHeaders = mapOf("Prefer" to "return=minimal")
                )
                if (r.code in 200..299) {
                    hasServerSharesTable = true
                    return@attempt true
                } else if (r.code == 404) {
                    hasServerSharesTable = false
                }
            }

            // Fallback storage array
            val existing = readPublicStorageJson("$me/shared_servers.json")
                ?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
            val updated = JsonArray()
            updated.add(JsonObject().apply {
                addProperty("id", UUID.randomUUID().toString())
                addProperty("name", cleanName)
                addProperty("address", cleanAddress)
                mcVersion?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("mc_version", it) }
                loader?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("loader", it) }
                description?.trim()?.takeIf { it.isNotEmpty() }?.let { addProperty("description", it.take(240)) }
                addProperty("created_at_ms", System.currentTimeMillis())
            })
            existing.take(24).forEach { updated.add(it) }
            writeOwnStorageJson("shared_servers.json", updated.toString())
        } ?: false
        if (ok) loadSharedServers()
        return ok
    }

    suspend fun deleteSharedServer(server: SocialSharedServer): Boolean {
        val ok = attempt {
            ensureToken()
            val me = userId ?: return@attempt false
            if (server.ownerId != me) return@attempt false
            if (hasServerSharesTable == true && server.id.toLongOrNull() != null) {
                val r = callRaw("DELETE", "$PROJECT_URL/rest/v1/server_shares?id=eq.${server.id}&owner=eq.$me")
                return@attempt r.code in 200..299
            }
            val existing = readPublicStorageJson("$me/shared_servers.json")
                ?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
            val filtered = JsonArray()
            existing.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                if (obj.stringOrNull("id") != server.id && obj.stringOrNull("address") != server.address) {
                    filtered.add(obj)
                }
            }
            writeOwnStorageJson("shared_servers.json", filtered.toString())
        } ?: false
        if (ok) loadSharedServers()
        return ok
    }

    // ---- Rich Presence & Game Lifecycle Hooks -----------------------------------------------

    /**
     * Called when `GameActivity` boots in the `:game` process so friends see
     * `"Playing 1.21.4 · 34 mods"`, joinable badges, and live in-game unread counts.
     */
    @JvmStatic
    fun onGameStarted(context: Context, instance: Instance?, normalizedVersion: String?) {
        attach(context)
        if (!signedIn) return
        val mcVer = extractMcVersion(normalizedVersion)
            ?: extractMcVersion(instance?.versionId)
            ?: normalizedVersion?.takeIf { it.isNotBlank() }
            ?: instance?.versionId?.takeIf { it.isNotBlank() }
            ?: "Minecraft"
        val modCount = countEnabledMods(instance?.gameDirectory)
        val status = if (modCount > 0) {
            "Playing $mcVer · $modCount ${if (modCount == 1) "mod" else "mods"}"
        } else {
            "Playing $mcVer"
        }
        activeGameMcVersion = mcVer
        activeGameStatusLine = status

        gamePresenceJob?.cancel()
        gamePresenceJob = bgScope.launch {
            refreshParties()
            unreadCount()
            refreshPartyInvites()
            while (isActive && signedIn) {
                val eff = effectivePresenceMode
                if (eff != null && eff != SocialPresenceMode.INVISIBLE) {
                    activeGameJoinable = parties.any { it.hostId == userId && !it.isExpired() }
                    val wire = SocialPresenceCodec.encodeForWire(eff, activeGameStatusLine)
                    pushRichPresence(
                        statusLine = wire,
                        mcVersion = activeGameMcVersion,
                        joinable = activeGameJoinable
                    )
                }
                delay(45_000L)
            }
        }
    }

    @JvmStatic
    fun onGameStopped() {
        gamePresenceJob?.cancel()
        gamePresenceJob = null
        activeGameStatusLine = null
        activeGameMcVersion = null
        activeGameJoinable = false
        if (!signedIn || effectivePresenceMode == SocialPresenceMode.INVISIBLE) return
        bgScope.launch {
            syncRichPresenceToServer()
        }
    }

    private val mcSemverRegex = Regex("""(1\.\d+(?:\.\d+)?|\d{2}w\d{2}[a-z])""")

    fun extractMcVersion(versionId: String?): String? {
        if (versionId.isNullOrBlank()) return null
        if (versionId == Instance.VERSION_LATEST_RELEASE || versionId == Instance.VERSION_LATEST_SNAPSHOT) return null
        return mcSemverRegex.find(versionId)?.value
    }

    fun detectLoader(versionId: String?): String {
        val lower = versionId?.lowercase(Locale.US).orEmpty()
        return when {
            "neoforge" in lower -> "NeoForge"
            "forge" in lower -> "Forge"
            "quilt" in lower -> "Quilt"
            "legacy-fabric" in lower || "legacy_fabric" in lower -> "Legacy Fabric"
            "fabric" in lower -> "Fabric"
            "optifine" in lower -> "OptiFine"
            "bta" in lower -> "BTA"
            else -> "Vanilla"
        }
    }

    private fun countEnabledMods(gameDir: File?): Int {
        if (gameDir == null) return 0
        val modsDir = File(gameDir, "mods")
        if (!modsDir.isDirectory) return 0
        return modsDir.listFiles()?.count { file ->
            file.isFile && file.name.lowercase(Locale.US).endsWith(".jar")
        } ?: 0
    }

    private suspend fun syncRichPresenceToServer() {
        if (!signedIn || !isNetworkAvailable) return
        val eff = effectivePresenceMode ?: return
        if (eff == SocialPresenceMode.INVISIBLE) return
        val hosting = parties.firstOrNull { it.hostId == userId && !it.isExpired() }
        val baseStatus = activeGameStatusLine
            ?: hosting?.let { "In party: ${it.title}" }
            ?: myCustomStatus
            ?: myProfile?.statusText
        val wireStatus = SocialPresenceCodec.encodeForWire(eff, baseStatus)
        val ver = activeGameMcVersion ?: hosting?.mcVersion
        val joinable = hosting != null || activeGameJoinable
        pushRichPresence(wireStatus, ver, joinable)
    }

    private suspend fun pushRichPresence(statusLine: String?, mcVersion: String?, joinable: Boolean) {
        ensureToken()
        val me = userId ?: return
        val patch = JsonObject().apply {
            addProperty("last_seen", isoNow())
            if (statusLine != null) addProperty("status_line", statusLine.take(60))
            else add("status_line", com.google.gson.JsonNull.INSTANCE)
            if (mcVersion != null) addProperty("mc_version", mcVersion.take(32))
            else add("mc_version", com.google.gson.JsonNull.INSTANCE)
            addProperty("joinable", joinable)
        }
        val r = callRaw(
            "PATCH",
            "$PROJECT_URL/rest/v1/profiles?id=eq.$me",
            patch.toString(),
            extraHeaders = mapOf("Prefer" to "return=minimal")
        )
        if (r.code !in 200..299) {
            callRaw("POST", "$PROJECT_URL/rest/v1/rpc/touch_presence", "{}")
        }
    }

    /** Heartbeat: lets friends see this player as online while preserving any active party/game rich presence. */
    suspend fun heartbeat() {
        if (!signedIn) return
        attempt {
            syncRichPresenceToServer()
        }
    }

    // ---- profile card & onboarding ----------------------------------------------------------

    var myProfile by mutableStateOf<SocialProfile?>(null)
        private set

    val needsOnboarding: Boolean
        get() = myProfile?.onboarded != true

    var onboardingDismissedForSession by mutableStateOf(false)
        private set

    val showOnboarding: Boolean
        get() = needsOnboarding && !onboardingDismissedForSession

    fun deferOnboardingForSession() {
        lastError = null
        onboardingDismissedForSession = true
    }

    fun resumeOnboarding() {
        lastError = null
        onboardingDismissedForSession = false
    }

    suspend fun loadMyProfile(): SocialProfile? {
        val card = attempt {
            ensureToken()
            val json = call("POST", "$PROJECT_URL/rest/v1/rpc/profile_card", "{}")
            parseProfile(json)
        }
        if (card != null) {
            val resolvedRole = SocialRole.fromEmail(email) ?: card.role
            val withLocalState = if (isAutoPresence) {
                val eff = effectivePresenceMode ?: SocialPresenceMode.ONLINE
                myCustomStatus = myCustomStatus ?: card.statusText
                myLookingToPlay = card.lookingToPlay
                persistLocalStatus(myPresenceMode, myCustomStatus, myLookingToPlay, autoMode = true)
                card.copy(
                    role = resolvedRole,
                    presenceMode = eff,
                    statusText = myCustomStatus
                )
            } else if (myPresenceMode == SocialPresenceMode.INVISIBLE) {
                card.copy(
                    role = resolvedRole,
                    presenceMode = SocialPresenceMode.INVISIBLE,
                    statusText = myCustomStatus ?: card.statusText
                )
            } else {
                myPresenceMode = card.presenceMode
                myCustomStatus = card.statusText
                myLookingToPlay = card.lookingToPlay
                persistLocalStatus(myPresenceMode, myCustomStatus, myLookingToPlay, autoMode = false)
                card.copy(role = resolvedRole)
            }
            myProfile = withLocalState
            if (userId == null) userId = withLocalState.id
            mcUsername = withLocalState.mcUsername ?: mcUsername
            return withLocalState
        }
        return null
    }

    suspend fun loadProfileCard(username: String): SocialProfile? = attempt {
        ensureToken()
        val body = JsonObject().apply { addProperty("p_username", username.trim()) }
        parseProfile(call("POST", "$PROJECT_URL/rest/v1/rpc/profile_card", body.toString()))
    }

    suspend fun findPlayer(username: String): SocialProfile? {
        val clean = username.trim().removePrefix("@")
        if (clean.length < 3) {
            lastError = "Type the full username"
            return null
        }
        val found = attempt {
            ensureToken()
            val body = JsonObject().apply { addProperty("p_username", clean) }
            val json = call("POST", "$PROJECT_URL/rest/v1/rpc/find_player", body.toString())
            val row = json as? JsonObject ?: return@attempt null
            val id = row.stringOrNull("id") ?: return@attempt null
            val rawName = row.stringOrNull("name") ?: row.stringOrNull("display_name")
            val (cleanName, detectedRole) = SocialRole.extract(
                rawName,
                row.stringOrNull("role_badge"),
                if (id == userId) email else null
            )
            SocialProfile(
                id = id,
                username = row.stringOrNull("username"),
                name = cleanName,
                mcUsername = row.stringOrNull("mc_username"),
                avatarUrl = row.stringOrNull("avatar_url"),
                country = row.stringOrNull("country"),
                lastSeenMs = row.longOr("last_seen_ms", 0L),
                role = detectedRole
            )
        }
        if (found == null && lastError == null) lastError = "No one is called @$clean"
        return found
    }

    fun inviteCode(): String? = myUserId()?.replace("-", "")?.take(8)?.uppercase()

    var pendingInvite by mutableStateOf<String?>(null)
        private set

    @JvmStatic
    fun acceptInvite(code: String) {
        pendingInvite = code.trim()
    }

    fun consumeInvite(): String? {
        val code = pendingInvite
        pendingInvite = null
        return code
    }

    suspend fun findByCode(code: String): SocialProfile? = attempt {
        ensureToken()
        val body = JsonObject().apply { addProperty("p_code", code.trim()) }
        val json = call("POST", "$PROJECT_URL/rest/v1/rpc/find_by_code", body.toString())
        val row = json as? JsonObject ?: return@attempt null
        val id = row.stringOrNull("id") ?: return@attempt null
        val rawName = row.stringOrNull("name") ?: row.stringOrNull("display_name")
        val (cleanName, detectedRole) = SocialRole.extract(
            rawName,
            row.stringOrNull("role_badge"),
            if (id == userId) email else null
        )
        SocialProfile(
            id = id,
            username = row.stringOrNull("username"),
            name = cleanName,
            mcUsername = row.stringOrNull("mc_username"),
            avatarUrl = row.stringOrNull("avatar_url"),
            country = row.stringOrNull("country"),
            lastSeenMs = row.longOr("last_seen_ms", 0L),
            role = detectedRole
        )
    }

    suspend fun claimUsername(username: String): String? {
        val clean = username.trim()
        if (clean.length < 3) return "Use at least 3 characters."
        if (clean.any { !it.isLetterOrDigit() && it != '_' }) return "Only letters, numbers and _ ."
        if (clean.none { it.isLetter() }) return "The username needs at least one letter."
        if (clean.none { it.isDigit() }) return "The username needs at least one number."
        return attempt {
            ensureToken()
            val body = JsonObject().apply { addProperty("p_username", clean) }
            val json = call("POST", "$PROJECT_URL/rest/v1/rpc/claim_username", body.toString())
            if (json == null) return@attempt "Could not save the username."
            if (json.isJsonNull) null else json.asString
        }
    }

    suspend fun saveProfile(
        name: String?,
        country: String?,
        bio: String?,
        avatarUrl: String?,
        bannerUrl: String?,
        skinUrl: String?,
        capeUrl: String?,
        onboarded: Boolean,
        statusText: String? = null,
        lookingToPlay: Boolean = false
    ): Boolean {
        val cleanStatus = SocialPresenceCodec.stripModePrefix(statusText)
        val effMode = effectivePresenceMode ?: myPresenceMode
        val saved = attempt {
            ensureToken()
            val me = userId ?: myProfile?.id ?: return@attempt null
            val role = SocialRole.fromEmail(email)
            val fallbackBase = myProfile?.username
                ?: mcUsername
                ?: email?.substringBefore('@')
                ?: "Player"
            val encodedName = SocialRole.encodeTaggedName(name, role, fallbackBase)
            val wireStatus = SocialPresenceCodec.encodeForWire(effMode, cleanStatus)

            val row = JsonObject().apply {
                addProperty("id", me)
                if (encodedName != null) {
                    addProperty("name", encodedName)
                    addProperty("display_name", encodedName)
                } else {
                    add("name", com.google.gson.JsonNull.INSTANCE)
                    addProperty("display_name", fallbackBase)
                }
                addProperty("country", country?.trim()?.takeIf { it.isNotBlank() })
                addProperty("bio", bio?.trim()?.takeIf { it.isNotBlank() })
                if (wireStatus != null) addProperty("status_text", wireStatus)
                else add("status_text", com.google.gson.JsonNull.INSTANCE)
                addProperty("looking_to_play", if (effMode == SocialPresenceMode.INVISIBLE) false else lookingToPlay)
                addProperty("onboarded", onboarded)
                if (avatarUrl != null) addProperty("avatar_url", avatarUrl)
                if (bannerUrl != null) addProperty("banner_url", bannerUrl)
                if (skinUrl != null) addProperty("skin_url", skinUrl)
                if (capeUrl != null) addProperty("cape_url", capeUrl)
            }
            val result = call(
                "POST",
                "$PROJECT_URL/rest/v1/profiles?on_conflict=id",
                JsonArray().apply { add(row) }.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
            )
            if (result == null) lastError = lastError ?: "Could not save the profile"
            result
        }
        if (saved != null) {
            myCustomStatus = cleanStatus
            myLookingToPlay = lookingToPlay
            persistLocalStatus(myPresenceMode, myCustomStatus, myLookingToPlay)
            SocialRealtime.sendPresenceBroadcast(
                modeKey = effMode.key,
                wireStatus = SocialPresenceCodec.encodeForWire(effMode, cleanStatus),
                lookingToPlay = lookingToPlay,
                appearOffline = effMode == SocialPresenceMode.INVISIBLE,
                displayName = SocialRole.encodeTaggedName(name, myRole, myEffectiveDisplayName()),
                roleKey = myRole?.key
            )
        }
        loadMyProfile()
        return saved != null
    }

    suspend fun uploadImage(folder: String, bytes: ByteArray, extension: String = "png"): String? =
        withContext(Dispatchers.IO) {
            lastError = null
            ensureToken()
            val me = userId ?: myProfile?.id
            val token = accessToken
            if (me == null || token == null) {
                if (lastError == null) lastError = "Sign in first"
                return@withContext null
            }
            val path = "$me/${folder}-${System.currentTimeMillis()}.$extension"
            val mime = when (extension.lowercase(Locale.US)) {
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> "image/png"
            }
            try {
                val req = Request.Builder()
                    .url("$PROJECT_URL/storage/v1/object/social-media/$path")
                    .post(bytes.toRequestBody(mime.toMediaType()))
                    .header("apikey", PUBLISHABLE_KEY)
                    .header("Authorization", "Bearer $token")
                    .header("x-upsert", "true")
                    .build()
                SocialRealtime.restClient.newCall(req).execute().use { resp ->
                    val code = resp.code
                    val text = resp.body?.string() ?: ""
                    if (code !in 200..299) {
                        val detail = serverMessage(text)?.takeIf { it.isNotBlank() }
                            ?: text.trim().take(140).takeIf { it.isNotBlank() }
                        lastError = if (detail != null) "$detail (HTTP $code)" else "Upload failed (HTTP $code)"
                        return@withContext null
                    }
                }
                lastError = null
                "$PROJECT_URL/storage/v1/object/public/social-media/$path"
            } catch (error: Throwable) {
                Log.w(TAG, "Upload failed", error)
                lastError = error.message ?: "Upload failed"
                null
            }
        }

    private fun writeOwnStorageJson(fileName: String, jsonContent: String): Boolean {
        val me = userId ?: return false
        val token = accessToken ?: return false
        return try {
            val req = Request.Builder()
                .url("$PROJECT_URL/storage/v1/object/social-media/$me/$fileName")
                .post(jsonContent.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("apikey", PUBLISHABLE_KEY)
                .header("Authorization", "Bearer $token")
                .header("x-upsert", "true")
                .build()
            SocialRealtime.restClient.newCall(req).execute().use { it.code in 200..299 }
        } catch (_: Throwable) {
            false
        }
    }

    private fun readPublicStorageJson(path: String): JsonElement? {
        return try {
            val req = Request.Builder()
                .url("$PROJECT_URL/storage/v1/object/public/social-media/$path")
                .get()
                .build()
            SocialRealtime.restClient.newCall(req).execute().use { resp ->
                if (resp.code !in 200..299) return null
                val text = resp.body?.string() ?: return null
                if (text.isBlank()) null else JsonParser.parseString(text)
            }
        } catch (_: Throwable) {
            null
        }
    }

    // ---- follows ----------------------------------------------------------------------------

    suspend fun setFollow(otherId: String, follow: Boolean): Boolean {
        val done = attempt {
            ensureToken()
            val me = userId ?: return@attempt null
            if (follow) {
                call(
                    "POST",
                    "$PROJECT_URL/rest/v1/follows?on_conflict=follower,following",
                    JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("follower", me)
                            addProperty("following", otherId)
                        })
                    }.toString(),
                    extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
                )
            } else {
                call("DELETE", "$PROJECT_URL/rest/v1/follows?follower=eq.$me&following=eq.$otherId")
            } ?: return@attempt null
            Unit
        }
        return done != null
    }

    suspend fun loadFollowers(): List<SocialProfile> = loadRelated("follows?select=follower&following=eq.", "follower")

    suspend fun loadFollowing(): List<SocialProfile> = loadRelated("follows?select=following&follower=eq.", "following")

    private suspend fun loadRelated(query: String, column: String): List<SocialProfile> {
        val list = attempt {
            ensureToken()
            val me = userId ?: return@attempt emptyList<SocialProfile>()
            val json = call("GET", "$PROJECT_URL/rest/v1/$query$me&limit=100")
                ?: return@attempt emptyList<SocialProfile>()
            val ids = json.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { element ->
                (element as? JsonObject)?.stringOrNull(column)
            } ?: return@attempt emptyList<SocialProfile>()
            if (ids.isEmpty()) return@attempt emptyList<SocialProfile>()
            val quoted = ids.joinToString(",") { "\"$it\"" }
            val rows = call(
                "GET",
                "$PROJECT_URL/rest/v1/profiles?id=in.($quoted)&select=id,username,name,display_name,mc_username,avatar_url,country,last_seen"
            )?.takeIf { it.isJsonArray }?.asJsonArray ?: return@attempt emptyList<SocialProfile>()
            rows.mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                val uid = row.stringOrNull("id") ?: return@mapNotNull null
                val rawName = row.stringOrNull("name") ?: row.stringOrNull("display_name")
                val (cleanName, detectedRole) = SocialRole.extract(
                    rawName,
                    row.stringOrNull("role_badge"),
                    if (uid == userId) email else null
                )
                SocialProfile(
                    id = uid,
                    username = row.stringOrNull("username"),
                    name = cleanName,
                    mcUsername = row.stringOrNull("mc_username"),
                    avatarUrl = row.stringOrNull("avatar_url"),
                    country = row.stringOrNull("country"),
                    lastSeenMs = parseTimestamp(row.stringOrNull("last_seen") ?: ""),
                    role = detectedRole
                )
            }
        }
        return list ?: emptyList()
    }

    // ---- safety (blocks & reports) ----------------------------------------------------------

    fun isBlocked(otherId: String): Boolean = blockedUsers.any { it.userId == otherId }

    suspend fun loadBlockedUsers(): List<SocialBlockedUser> {
        attempt {
            ensureToken()
            val me = userId ?: return@attempt Unit
            loadBlockedUsersInternal(me)
        }
        return blockedUsers
    }

    private suspend fun loadBlockedUsersInternal(me: String) {
        val json = call(
            "GET",
            "$PROJECT_URL/rest/v1/blocks?blocker=eq.$me&select=blocked,created_at&order=created_at.desc"
        )?.takeIf { it.isJsonArray }?.asJsonArray
        if (json == null || json.size() == 0) {
            blockedUsers = emptyList()
            return
        }
        val rows = json.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val uid = obj.stringOrNull("blocked") ?: return@mapNotNull null
            uid to parseTimestamp(obj.stringOrNull("created_at") ?: "")
        }
        val profMap = fetchSimpleProfileMap(rows.map { it.first })
        blockedUsers = rows.map { (uid, ts) ->
            val p = profMap[uid]
            SocialBlockedUser(
                userId = uid,
                name = p?.cleanDisplayName ?: "Blocked player",
                username = p?.username,
                mcUsername = p?.mcUsername,
                avatarUrl = p?.avatarUrl,
                role = p?.effectiveRole,
                createdAtMs = ts
            )
        }
    }

    suspend fun block(otherId: String): Boolean {
        val done = attempt {
            ensureToken()
            val me = userId ?: return@attempt null
            if (otherId == me) return@attempt null
            call(
                "POST",
                "$PROJECT_URL/rest/v1/blocks?on_conflict=blocker,blocked",
                JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("blocker", me)
                        addProperty("blocked", otherId)
                    })
                }.toString(),
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
            ) ?: return@attempt null
            call("DELETE", "$PROJECT_URL/rest/v1/friendships?or=(and(requester.eq.$me,addressee.eq.$otherId),and(requester.eq.$otherId,addressee.eq.$me))")
            call("DELETE", "$PROJECT_URL/rest/v1/follows?or=(and(follower.eq.$me,following.eq.$otherId),and(follower.eq.$otherId,following.eq.$me))")
            loadBlockedUsersInternal(me)
            Unit
        }
        refreshFriends()
        return done != null
    }

    suspend fun unblock(otherId: String): Boolean {
        val done = attempt {
            ensureToken()
            val me = userId ?: return@attempt null
            call("DELETE", "$PROJECT_URL/rest/v1/blocks?blocker=eq.$me&blocked=eq.$otherId") ?: return@attempt null
            loadBlockedUsersInternal(me)
            Unit
        }
        refreshFriends()
        return done != null
    }

    suspend fun report(otherId: String, reason: String): Boolean = attempt {
        ensureToken()
        val me = userId ?: return@attempt false
        call(
            "POST",
            "$PROJECT_URL/rest/v1/reports",
            JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("reporter", me)
                    addProperty("target", otherId)
                    addProperty("reason", reason.take(500))
                })
            }.toString(),
            extraHeaders = mapOf("Prefer" to "return=minimal")
        ) != null
    } ?: false

    // ---- presence and unread ----------------------------------------------------------------

    /**
     * Switches back to Smart Automatic Presence:
     * Green (`Online`) while in Launcher/Game, Yellow (`Idle`) in background, Offline without Wi-Fi.
     */
    suspend fun enableAutoStatus(
        customStatus: String? = myCustomStatus,
        looking: Boolean = myLookingToPlay
    ): Boolean {
        if (!signedIn) return false
        val cleanStatus = SocialPresenceCodec.stripModePrefix(customStatus)?.take(52)
        isAutoPresence = true
        myCustomStatus = cleanStatus
        myLookingToPlay = looking
        val autoMode = if (isLauncherInForeground || activeGameStatusLine != null) {
            SocialPresenceMode.ONLINE
        } else {
            SocialPresenceMode.IDLE
        }
        persistLocalStatus(myPresenceMode, cleanStatus, looking, autoMode = true)
        SocialSoundEffects.playStatusChanged()
        return syncStatusInternal(autoMode, cleanStatus, looking)
    }

    /**
     * Updates the signed-in player's availability mode (`Online`, `Idle`, `Do Not Disturb`,
     * `Appear Offline`), custom status message, and Looking-to-Play badge, syncing both to
     * Supabase and over Realtime WebSocket so other players see the change immediately.
     */
    suspend fun updateMyStatus(
        mode: SocialPresenceMode,
        customStatus: String?,
        looking: Boolean,
        manualOverride: Boolean = true
    ): Boolean {
        if (!signedIn) return false
        val cleanStatus = SocialPresenceCodec.stripModePrefix(customStatus)?.take(52)
        if (manualOverride) {
            isAutoPresence = false
            myPresenceMode = mode
        }
        myCustomStatus = cleanStatus
        myLookingToPlay = looking
        persistLocalStatus(myPresenceMode, cleanStatus, looking, autoMode = isAutoPresence)
        SocialSoundEffects.playStatusChanged()
        val effective = effectivePresenceMode ?: mode
        return syncStatusInternal(effective, cleanStatus, looking)
    }

    private suspend fun syncStatusInternal(
        mode: SocialPresenceMode,
        cleanStatus: String?,
        looking: Boolean
    ): Boolean {
        myProfile = myProfile?.copy(
            presenceMode = mode,
            statusText = cleanStatus,
            lookingToPlay = looking,
            lastSeenMs = if (mode == SocialPresenceMode.INVISIBLE || !isNetworkAvailable) 0L else System.currentTimeMillis(),
            role = myRole
        )
        if (!isNetworkAvailable) return true

        val done = attempt {
            ensureToken()
            val me = userId ?: return@attempt false
            if (mode == SocialPresenceMode.INVISIBLE) {
                callRaw(
                    "PATCH",
                    "$PROJECT_URL/rest/v1/profiles?id=eq.$me",
                    JsonObject().apply {
                        addProperty("last_seen", "1970-01-01T00:00:00Z")
                        add("status_text", com.google.gson.JsonNull.INSTANCE)
                        add("status_line", com.google.gson.JsonNull.INSTANCE)
                        add("mc_version", com.google.gson.JsonNull.INSTANCE)
                        addProperty("joinable", false)
                        addProperty("looking_to_play", false)
                    }.toString(),
                    extraHeaders = mapOf("Prefer" to "return=minimal")
                )
                SocialRealtime.sendPresenceBroadcast(
                    modeKey = mode.key,
                    wireStatus = null,
                    lookingToPlay = false,
                    appearOffline = true,
                    displayName = SocialRole.encodeTaggedName(myEffectiveDisplayName(), myRole, myEffectiveDisplayName()),
                    roleKey = myRole?.key
                )
                true
            } else {
                val wireProfileStatus = SocialPresenceCodec.encodeForWire(mode, cleanStatus)
                val hosting = parties.firstOrNull { it.hostId == me && !it.isExpired() }
                val activeRichBase = activeGameStatusLine
                    ?: hosting?.let { "In party: ${it.title}" }
                    ?: cleanStatus
                val wireRichStatus = SocialPresenceCodec.encodeForWire(mode, activeRichBase)

                call(
                    "POST",
                    "$PROJECT_URL/rest/v1/rpc/set_presence",
                    JsonObject().apply {
                        addProperty("p_status", wireProfileStatus ?: "")
                        addProperty("p_looking", looking)
                    }.toString()
                )
                callRaw(
                    "PATCH",
                    "$PROJECT_URL/rest/v1/profiles?id=eq.$me",
                    JsonObject().apply {
                        addProperty("last_seen", isoNow())
                        if (wireProfileStatus != null) addProperty("status_text", wireProfileStatus)
                        else add("status_text", com.google.gson.JsonNull.INSTANCE)
                        if (wireRichStatus != null) addProperty("status_line", wireRichStatus)
                        else add("status_line", com.google.gson.JsonNull.INSTANCE)
                        addProperty("looking_to_play", looking)
                    }.toString(),
                    extraHeaders = mapOf("Prefer" to "return=minimal")
                )
                SocialRealtime.sendPresenceBroadcast(
                    modeKey = mode.key,
                    wireStatus = wireRichStatus,
                    lookingToPlay = looking,
                    appearOffline = false,
                    displayName = SocialRole.encodeTaggedName(myEffectiveDisplayName(), myRole, myEffectiveDisplayName()),
                    roleKey = myRole?.key
                )
                true
            }
        }
        return done == true
    }

    suspend fun setPresence(status: String?, looking: Boolean) {
        if (isAutoPresence) {
            enableAutoStatus(status, looking)
        } else {
            updateMyStatus(myPresenceMode, status, looking, manualOverride = false)
        }
    }

    suspend fun unreadCount(): Int {
        if (!isNetworkAvailable) return unread
        val count = attempt {
            ensureToken()
            val me = userId ?: return@attempt 0
            val blocked = blockedUsers.map { it.userId }.toSet()
            val deleted = deletedForMeSet()
            val json = call(
                "GET",
                "$PROJECT_URL/rest/v1/messages?select=id,sender,body&recipient=eq.$me&read_at=is.null&limit=500"
            ) ?: return@attempt 0
            val rows = json.takeIf { it.isJsonArray }?.asJsonArray ?: return@attempt 0
            rows.count { el ->
                val row = el as? JsonObject ?: return@count false
                val id = row.longOr("id", 0L)
                val sender = row.stringOrNull("sender") ?: ""
                val body = row.stringOrNull("body") ?: ""
                id !in deleted &&
                    sender !in blocked &&
                    !body.startsWith(SocialEnvelopeCodec.PARTY_INVITE_PREFIX) &&
                    !body.startsWith(SocialEnvelopeCodec.PARTY_CHAT_PREFIX)
            }
        }
        unread = count ?: 0
        return unread
    }

    suspend fun markRead(otherId: String) {
        val nowMs = System.currentTimeMillis()
        var hadUnreadInCache = false
        liveDmCache[otherId]?.let { list ->
            synchronized(list) {
                for (i in list.indices) {
                    val m = list[i]
                    if (m.sender == otherId && !m.isRead) {
                        list[i] = m.copy(readAtMs = nowMs)
                        hadUnreadInCache = true
                    }
                }
            }
        }
        // Broadcast instant read receipt (`✓✓ Seen`) over WebSocket so peer sees it in ~50ms!
        SocialRealtime.sendDmReadBroadcast(otherId, nowMs)
        if (hadUnreadInCache) {
            withContext(Dispatchers.Main) {
                unread = (unread - 1).coerceAtLeast(0)
                messageRevision++
            }
        }
        if (!isNetworkAvailable) return
        attempt {
            ensureToken()
            val me = userId ?: return@attempt Unit
            call(
                "PATCH",
                "$PROJECT_URL/rest/v1/messages?recipient=eq.$me&sender=eq.$otherId&read_at=is.null",
                JsonObject().apply { addProperty("read_at", isoAt(nowMs)) }.toString(),
                extraHeaders = mapOf("Prefer" to "return=minimal")
            )
            Unit
        }
        unreadCount()
    }

    private fun isoNow(): String = isoAt(System.currentTimeMillis())

    private fun isoAt(epochMs: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(epochMs))
    }

    private fun parseProfile(json: JsonElement?): SocialProfile? {
        val row = json as? JsonObject ?: return null
        val id = row.stringOrNull("id") ?: return null
        val isMe = row.get("is_me")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: (id == userId)
        val ranks = row.get("ranks")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { element -> runCatching { element.asString }.getOrNull() }
            ?: emptyList()
        val rawName = row.stringOrNull("name") ?: row.stringOrNull("display_name")
        val (cleanName, detectedRole) = SocialRole.extract(
            rawName,
            row.stringOrNull("role_badge"),
            if (isMe) email else null
        )
        val parsedPresence = SocialPresenceCodec.parse(
            row.stringOrNull("status_line") ?: row.stringOrNull("status_text"),
            row.stringOrNull("presence_mode")
        )
        return SocialProfile(
            id = id,
            username = row.stringOrNull("username"),
            name = cleanName,
            mcUsername = row.stringOrNull("mc_username"),
            country = row.stringOrNull("country"),
            bio = row.stringOrNull("bio"),
            avatarUrl = row.stringOrNull("avatar_url"),
            bannerUrl = row.stringOrNull("banner_url"),
            skinUrl = row.stringOrNull("skin_url"),
            capeUrl = row.stringOrNull("cape_url"),
            statusText = parsedPresence.customText,
            lookingToPlay = row.get("looking_to_play")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            onboarded = row.get("onboarded")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            usernameChangedAtMs = parseTimestamp(row.stringOrNull("username_changed_at") ?: ""),
            lastSeenMs = row.longOr("last_seen_ms", 0L),
            followers = row.longOr("followers", 0L).toInt(),
            following = row.longOr("following", 0L).toInt(),
            friends = row.longOr("friends", 0L).toInt(),
            messages = row.longOr("messages", 0L).toInt(),
            ranks = ranks,
            isMe = isMe,
            iFollow = row.get("i_follow")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            role = detectedRole,
            presenceMode = parsedPresence.mode
        )
    }

    // ---- plumbing --------------------------------------------------------------------------

    private data class RawHttpResult(
        val code: Int,
        val body: String,
        val json: JsonElement?
    )

    private suspend fun <T> attempt(block: suspend () -> T): T? = withContext(Dispatchers.IO) {
        runCatching { block() }.onFailure {
            Log.w(TAG, "Social request failed", it)
            lastError = it.message ?: "Network error"
        }.getOrNull()
    }

    private fun callRaw(
        method: String,
        url: String,
        body: String? = null,
        authenticated: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap()
    ): RawHttpResult {
        return try {
            val upper = method.uppercase(Locale.US)
            val jsonMedia = "application/json; charset=utf-8".toMediaType()
            val reqBody = when {
                body != null -> body.toRequestBody(jsonMedia)
                upper == "POST" || upper == "PUT" || upper == "PATCH" -> "".toRequestBody(jsonMedia)
                else -> null
            }
            val builder = Request.Builder()
                .url(url)
                .method(upper, reqBody)
                .header("apikey", PUBLISHABLE_KEY)
            if (reqBody != null) {
                builder.header("Content-Type", "application/json")
            }
            if (authenticated) {
                accessToken?.let { builder.header("Authorization", "Bearer $it") }
            }
            extraHeaders.forEach { (key, value) -> builder.header(key, value) }

            SocialRealtime.restClient.newCall(builder.build()).execute().use { resp ->
                val code = resp.code
                val text = resp.body?.string() ?: ""
                val parsed = if (code in 200..299) {
                    runCatching {
                        if (text.isBlank()) JsonParser.parseString("{}") else JsonParser.parseString(text)
                    }.getOrNull()
                } else null
                RawHttpResult(code, text, parsed)
            }
        } catch (t: Throwable) {
            RawHttpResult(-1, t.message ?: "Network error", null)
        }
    }

    private fun call(
        method: String,
        url: String,
        body: String? = null,
        authenticated: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap()
    ): JsonElement? {
        val r = callRaw(method, url, body, authenticated, extraHeaders)
        if (r.code !in 200..299) {
            if (r.code > 0) {
                Log.w(TAG, "$method $url -> ${r.code} ${r.body}")
                serverMessage(r.body)?.let { lastError = "$it (HTTP ${r.code})" }
                    ?: run { lastError = "Server said HTTP ${r.code}" }
                if (r.code == 401 && authenticated && !url.contains("/auth/v1/token")) signOut()
            } else {
                lastError = r.body
            }
            return null
        }
        return r.json
    }

    private fun serverMessage(body: String): String? {
        if (body.isBlank()) return null
        val json = runCatching { JsonParser.parseString(body) as? JsonObject }.getOrNull() ?: return null
        return json.stringOrNull("msg")
            ?: json.stringOrNull("message")
            ?: json.stringOrNull("error_description")
            ?: json.stringOrNull("error")
    }

    private fun asObject(element: JsonElement?): JsonObject? = element as? JsonObject

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        return runCatching { element.asString }.getOrNull()
    }

    private fun JsonObject.longOr(key: String, fallback: Long): Long {
        val element = get(key) ?: return fallback
        return runCatching { element.asLong }.getOrDefault(fallback)
    }

    private fun parseTimestamp(value: String): Long {
        if (value.length < 19) return 0L
        return runCatching {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(value.substring(0, 19))?.time ?: 0L
        }.getOrDefault(0L)
    }
}
