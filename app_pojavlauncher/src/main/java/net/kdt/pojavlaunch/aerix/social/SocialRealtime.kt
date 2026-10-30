package net.kdt.pojavlaunch.aerix.social

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
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
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Supabase Realtime client (Phoenix Channels v1 over OkHttp WebSocket).
 *
 * Splits Realtime into TWO isolated Phoenix topics on the same WebSocket connection:
 * 1. [TOPIC_LIVE] (`realtime:aerix:live`) — Pure `broadcast` + `presence` (ZERO `postgres_changes`).
 *    Joins in ~20ms regardless of database schema or publication settings so live DM chat,
 *    party chat, seen receipts, typing wave indicators, and socket presence NEVER fail to join.
 * 2. [TOPIC_DB] (`realtime:aerix:db`) — Best-effort `postgres_changes` on core tables
 *    (`messages`, `friendships`, `profiles`). Any missing publication on [TOPIC_DB] never affects [TOPIC_LIVE].
 */
object SocialRealtime {
    private const val TAG = "AerixSocialRealtime"
    private const val TOPIC_LIVE = "realtime:aerix:live"
    private const val TOPIC_DB = "realtime:aerix:db"
    private const val HEARTBEAT_MS = 22_000L
    private const val TYPING_TTL_MS = 4_200L
    private const val TYPING_SEND_THROTTLE_MS = 1_350L

    private val BROADCAST_EVENTS = setOf(
        "typing",
        "dm_chat",
        "dm_read",
        "message_edit",
        "party_chat",
        "party_signal",
        "presence_update"
    )

    private val gson = Gson()
    private val refSeq = AtomicInteger(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sharedPool = ConnectionPool(8, 2, TimeUnit.MINUTES)

    /** Shared warm HTTP/2 client for fast Supabase REST & Storage calls. */
    val restClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedPool)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val wsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedPool)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Volatile private var socket: WebSocket? = null
    @Volatile private var connected: Boolean = false
    @Volatile private var joined: Boolean = false
    @Volatile private var dbJoined: Boolean = false
    @Volatile private var liveJoinRef: String? = null
    @Volatile private var dbJoinRef: String? = null
    @Volatile private var currentUrl: String? = null
    @Volatile private var currentApiKey: String? = null
    @Volatile private var currentAccessToken: String? = null
    @Volatile private var currentUserId: String? = null
    @Volatile private var shouldStayConnected: Boolean = false

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var typingCleanupJob: Job? = null
    private var backoffStep: Int = 0
    private val lastTypingSentMs = AtomicLong(0L)

    /** Peers currently tracked on Phoenix Presence over live WebSockets. */
    val onlineSocketPeers: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /**
     * Map of conversation key (`dm:<peerUserId>` or `party:<partyId>`) ->
     * Pair(typerDisplayName, expiresAtEpochMs). Observed directly by Compose chat headers & bubbles.
     */
    val activeTypers = mutableStateMapOf<String, Pair<String, Long>>()

    interface Listener {
        fun onRealtimeConnected()
        fun onMessageTableChanged(eventType: String, record: JsonObject?)
        fun onPartyMessageChanged(eventType: String, record: JsonObject?)
        fun onFriendshipTableChanged(eventType: String, record: JsonObject?)
        fun onPartyTableChanged(eventType: String, record: JsonObject?)
        fun onProfileUpdated(record: JsonObject)
        fun onBroadcastDmChat(payload: JsonObject)
        fun onBroadcastDmRead(payload: JsonObject)
        fun onBroadcastMessageEdit(payload: JsonObject)
        fun onBroadcastPartyChat(payload: JsonObject)
        fun onBroadcastPartySignal(payload: JsonObject)
        fun onBroadcastPresence(payload: JsonObject)
        fun onPeerSocketPresenceChanged(userId: String, connected: Boolean, modeKey: String?)
    }

    @Volatile var listener: Listener? = null

    val isConnected: Boolean
        get() = connected && joined

    @Synchronized
    fun connect(
        supabaseHttpUrl: String,
        apiKey: String,
        accessToken: String,
        userId: String
    ) {
        val wsBase = supabaseHttpUrl
            .trimEnd('/')
            .replaceFirst("^https://".toRegex(), "wss://")
            .replaceFirst("^http://".toRegex(), "ws://")
        val endpoint = "$wsBase/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"

        val tokenChanged = currentAccessToken != accessToken
        currentUrl = endpoint
        currentApiKey = apiKey
        currentAccessToken = accessToken
        currentUserId = userId
        shouldStayConnected = true

        if (socket != null && connected) {
            if (!joined) {
                joinLiveChannel(includeUserJwt = true)
            } else if (tokenChanged) {
                pushAccessToken(accessToken)
            }
            return
        }

        backoffStep = 0
        reconnectJob?.cancel()
        openSocket(endpoint)
    }

    @Synchronized
    fun reconnectImmediately() {
        val endpoint = currentUrl ?: return
        if (!shouldStayConnected) return
        if (connected && joined) return
        backoffStep = 0
        reconnectJob?.cancel()
        openSocket(endpoint)
    }

    /** Immediately tears down active socket when local Wi-Fi/network drops, keeping auto-reconnect armed. */
    @Synchronized
    fun suspendForNetworkLoss() {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        connected = false
        joined = false
        dbJoined = false
        liveJoinRef = null
        dbJoinRef = null
        onlineSocketPeers.clear()
        try {
            socket?.cancel()
        } catch (_: Throwable) {
        }
        socket = null
        scope.launch(Dispatchers.Main) { activeTypers.clear() }
    }

    @Synchronized
    fun updateAccessToken(accessToken: String) {
        currentAccessToken = accessToken
        if (connected) {
            if (joined) {
                pushAccessToken(accessToken)
            } else {
                joinLiveChannel(includeUserJwt = true)
            }
        }
    }

    @Synchronized
    fun disconnect() {
        shouldStayConnected = false
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        typingCleanupJob?.cancel()
        untrackPresence()
        connected = false
        joined = false
        dbJoined = false
        liveJoinRef = null
        dbJoinRef = null
        onlineSocketPeers.clear()
        try {
            socket?.close(1000, "Signed out")
        } catch (_: Throwable) {
        }
        socket = null
        scope.launch(Dispatchers.Main) { activeTypers.clear() }
    }

    fun dmTypingKey(peerId: String): String = "dm:$peerId"
    fun partyTypingKey(partyId: String): String = "party:$partyId"

    /**
     * Broadcasts a lightweight typing state without touching PostgreSQL.
     * `active = false` bypasses the throttle so typing bubbles vanish immediately on send/clear.
     */
    fun sendTyping(
        targetPeerId: String?,
        partyId: String?,
        myDisplayName: String,
        active: Boolean = true
    ) {
        val me = currentUserId ?: return
        if (!isConnected) return
        val now = System.currentTimeMillis()
        if (active) {
            val prev = lastTypingSentMs.get()
            if (now - prev < TYPING_SEND_THROTTLE_MS) return
            lastTypingSentMs.set(now)
        } else {
            lastTypingSentMs.set(0L)
        }

        val inner = JsonObject().apply {
            addProperty("senderId", me)
            addProperty("senderName", myDisplayName)
            targetPeerId?.let { addProperty("recipientId", it) }
            partyId?.let { addProperty("partyId", it) }
            addProperty("active", active)
            addProperty("ts", now)
        }
        sendBroadcast("typing", inner)
    }

    /** Instant WebSocket DM delivery so open chats update in ~50ms even without DB replication. */
    fun sendDmChatBroadcast(message: SocialMessage, tempId: String? = null) {
        if (!isConnected) return
        val inner = JsonObject().apply {
            addProperty("id", message.id)
            tempId?.let { addProperty("tempId", it) }
            addProperty("sender", message.sender)
            addProperty("recipient", message.recipient)
            message.senderName?.let { addProperty("senderName", it) }
            addProperty("body", message.body)
            message.replyTo?.let { addProperty("replyTo", it) }
            message.attachmentUrl?.let { addProperty("attachmentUrl", it) }
            addProperty("atMs", message.atMs)
            if (message.editedAtMs > 0L) addProperty("editedAtMs", message.editedAtMs)
        }
        sendBroadcast("dm_chat", inner)
    }

    /** Instant WebSocket read-receipt (`✓✓ Seen`) delivery when recipient views open chat. */
    fun sendDmReadBroadcast(peerId: String, readAtMs: Long) {
        val me = currentUserId ?: return
        if (!isConnected) return
        val inner = JsonObject().apply {
            addProperty("readerId", me)
            addProperty("senderId", peerId)
            addProperty("readAtMs", readAtMs)
        }
        sendBroadcast("dm_read", inner)
    }

    /** Instant WebSocket message edit broadcast for both DMs and party chat. */
    fun sendMessageEditBroadcast(
        messageId: Long,
        newBody: String,
        editedAtMs: Long,
        recipientId: String? = null,
        partyId: String? = null
    ) {
        val me = currentUserId ?: return
        if (!isConnected) return
        val inner = JsonObject().apply {
            addProperty("id", messageId)
            addProperty("sender", me)
            recipientId?.let { addProperty("recipient", it) }
            partyId?.let { addProperty("partyId", it) }
            addProperty("body", newBody)
            addProperty("editedAtMs", editedAtMs)
        }
        sendBroadcast("message_edit", inner)
    }

    fun sendPartyChatBroadcast(message: SocialMessage, tempId: String? = null) {
        if (!isConnected) return
        val inner = JsonObject().apply {
            addProperty("id", message.id)
            tempId?.let { addProperty("tempId", it) }
            addProperty("partyId", message.partyId)
            addProperty("sender", message.sender)
            addProperty("senderName", message.senderName ?: "Player")
            addProperty("body", message.body)
            message.replyTo?.let { addProperty("replyTo", it) }
            message.attachmentUrl?.let { addProperty("attachmentUrl", it) }
            addProperty("atMs", message.atMs)
        }
        sendBroadcast("party_chat", inner)
    }

    fun sendPartySignal(kind: String, partyId: String, targetUserId: String? = null) {
        if (!isConnected) return
        val inner = JsonObject().apply {
            addProperty("kind", kind)
            addProperty("partyId", partyId)
            currentUserId?.let { addProperty("senderId", it) }
            targetUserId?.let { addProperty("targetUserId", it) }
            addProperty("ts", System.currentTimeMillis())
        }
        sendBroadcast("party_signal", inner)
    }

    /** Tracks or untracks socket-level Phoenix Presence so peers detect Wi-Fi drops automatically. */
    fun trackPresence(modeKey: String, appearOffline: Boolean) {
        val me = currentUserId ?: return
        if (!isConnected) return
        if (appearOffline) {
            untrackPresence()
            return
        }
        val payload = JsonObject().apply {
            addProperty("type", "presence")
            addProperty("event", "track")
            add("payload", JsonObject().apply {
                addProperty("user_id", me)
                addProperty("mode", modeKey)
                addProperty("online_at", System.currentTimeMillis())
            })
        }
        sendPhoenixFrame(TOPIC_LIVE, "presence", payload, joinRef = liveJoinRef)
    }

    fun untrackPresence() {
        if (!connected || !joined) return
        val payload = JsonObject().apply {
            addProperty("type", "presence")
            addProperty("event", "untrack")
        }
        sendPhoenixFrame(TOPIC_LIVE, "presence", payload, joinRef = liveJoinRef)
    }

    /** Broadcasts an immediate presence / status / role update so online friends see it instantly. */
    fun sendPresenceBroadcast(
        modeKey: String,
        wireStatus: String?,
        lookingToPlay: Boolean,
        appearOffline: Boolean,
        displayName: String? = null,
        roleKey: String? = null
    ) {
        val me = currentUserId ?: return
        if (!isConnected) return
        trackPresence(modeKey, appearOffline)
        val inner = JsonObject().apply {
            addProperty("userId", me)
            addProperty("mode", modeKey)
            if (wireStatus != null) addProperty("statusLine", wireStatus)
            addProperty("lookingToPlay", lookingToPlay)
            addProperty("appearOffline", appearOffline)
            displayName?.let { addProperty("displayName", it) }
            roleKey?.let { addProperty("role", it) }
            addProperty("ts", System.currentTimeMillis())
        }
        sendBroadcast("presence_update", inner)
    }

    private fun sendBroadcast(eventName: String, data: JsonObject) {
        val payload = JsonObject().apply {
            addProperty("type", "broadcast")
            addProperty("event", eventName)
            add("payload", data)
        }
        sendPhoenixFrame(TOPIC_LIVE, "broadcast", payload, joinRef = liveJoinRef)
    }

    private fun openSocket(endpoint: String) {
        try {
            socket?.cancel()
        } catch (_: Throwable) {
        }
        connected = false
        joined = false
        dbJoined = false
        liveJoinRef = null
        dbJoinRef = null

        val reqBuilder = Request.Builder().url(endpoint)
        currentApiKey?.takeIf { it.isNotBlank() }?.let { reqBuilder.header("apikey", it) }

        socket = wsClient.newWebSocket(reqBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                backoffStep = 0
                joinLiveChannel(includeUserJwt = true)
                joinDbChannel()
                startLoops()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                joined = false
                dbJoined = false
                try {
                    webSocket.close(code, reason)
                } catch (_: Throwable) {
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                joined = false
                dbJoined = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d(TAG, "Realtime socket disconnected: ${t.message}")
                connected = false
                joined = false
                dbJoined = false
                scheduleReconnect()
            }
        })
    }

    /**
     * Pure Broadcast + Presence channel (NO `postgres_changes`).
     * Guaranteed to join even when optional SQL migrations or publications haven't been run.
     */
    private fun joinLiveChannel(includeUserJwt: Boolean) {
        val me = currentUserId ?: ""
        val token = currentAccessToken

        val config = JsonObject().apply {
            add("broadcast", JsonObject().apply {
                addProperty("ack", false)
                addProperty("self", false)
            })
            add("presence", JsonObject().apply {
                addProperty("key", me)
            })
            addProperty("private", false)
        }

        val payload = JsonObject().apply {
            add("config", config)
            if (includeUserJwt && !token.isNullOrBlank()) {
                addProperty("access_token", token)
            }
        }

        val jRef = refSeq.getAndIncrement().toString()
        liveJoinRef = jRef
        sendPhoenixFrame(TOPIC_LIVE, "phx_join", payload, refOverride = jRef, joinRef = jRef)
    }

    /**
     * Separate secondary channel for PostgreSQL WAL changes on core v1 tables.
     * Isolated from [TOPIC_LIVE] so any table/publication mismatch never disrupts live chat.
     */
    private fun joinDbChannel() {
        val token = currentAccessToken ?: return
        val postgresChanges = JsonArray().apply {
            listOf("messages", "friendships", "profiles").forEach { tbl ->
                add(JsonObject().apply {
                    addProperty("event", "*")
                    addProperty("schema", "public")
                    addProperty("table", tbl)
                })
            }
        }
        val config = JsonObject().apply {
            add("broadcast", JsonObject().apply {
                addProperty("ack", false)
                addProperty("self", false)
            })
            add("postgres_changes", postgresChanges)
        }
        val payload = JsonObject().apply {
            add("config", config)
            addProperty("access_token", token)
        }
        val jRef = refSeq.getAndIncrement().toString()
        dbJoinRef = jRef
        sendPhoenixFrame(TOPIC_DB, "phx_join", payload, refOverride = jRef, joinRef = jRef)
    }

    private fun pushAccessToken(token: String) {
        val payload = JsonObject().apply {
            addProperty("access_token", token)
        }
        if (joined) {
            sendPhoenixFrame(TOPIC_LIVE, "access_token", payload, joinRef = liveJoinRef)
        }
        if (dbJoined) {
            sendPhoenixFrame(TOPIC_DB, "access_token", payload, joinRef = dbJoinRef)
        }
    }

    private fun startLoops() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && connected) {
                delay(HEARTBEAT_MS)
                if (connected) {
                    sendPhoenixFrame("phoenix", "heartbeat", JsonObject())
                }
            }
        }
        typingCleanupJob?.cancel()
        typingCleanupJob = scope.launch {
            while (isActive && connected) {
                delay(900L)
                val now = System.currentTimeMillis()
                scope.launch(Dispatchers.Main) {
                    val expired = activeTypers.entries.filter { it.value.second <= now }.map { it.key }
                    expired.forEach { activeTypers.remove(it) }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (!shouldStayConnected) return
        val endpoint = currentUrl ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val step = backoffStep.coerceAtMost(4)
            backoffStep = (backoffStep + 1).coerceAtMost(5)
            val waitMs = when (step) {
                0 -> 1_000L
                1 -> 2_200L
                2 -> 4_500L
                3 -> 8_000L
                else -> 15_000L
            }
            delay(waitMs)
            if (shouldStayConnected && !connected) {
                openSocket(endpoint)
            }
        }
    }

    private fun sendPhoenixFrame(
        topic: String,
        event: String,
        payload: JsonObject,
        refOverride: String? = null,
        joinRef: String? = null
    ) {
        val ws = socket ?: return
        val ref = refOverride ?: refSeq.getAndIncrement().toString()
        val frame = JsonObject().apply {
            addProperty("topic", topic)
            addProperty("event", event)
            add("payload", payload)
            addProperty("ref", ref)
            if (joinRef != null) {
                addProperty("join_ref", joinRef)
            }
        }
        try {
            ws.send(gson.toJson(frame))
        } catch (_: Throwable) {
        }
    }

    private fun handleFrame(raw: String) {
        try {
            val parsed = JsonParser.parseString(raw)
            if (parsed.isJsonArray) {
                // Phoenix V2 array serializer fallback: [join_ref, ref, topic, event, payload]
                val arr = parsed.asJsonArray
                if (arr.size() >= 5) {
                    val topic = arr[2].takeIf { !it.isJsonNull }?.asString ?: ""
                    val event = arr[3].takeIf { !it.isJsonNull }?.asString ?: return
                    val payload = arr[4].takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                    dispatchEvent(topic, event, payload)
                }
                return
            }
            val root = parsed.takeIf { it.isJsonObject }?.asJsonObject ?: return
            val topic = root.get("topic")?.takeIf { !it.isJsonNull }?.asString ?: ""
            val event = root.get("event")?.takeIf { !it.isJsonNull }?.asString ?: return
            val payload = root.get("payload")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
            dispatchEvent(topic, event, payload)
        } catch (t: Throwable) {
            Log.d(TAG, "Frame parse error: ${t.message}")
        }
    }

    private fun dispatchEvent(topic: String, event: String, payload: JsonObject) {
        when (event) {
            "phx_reply" -> {
                val status = payload.get("status")?.takeIf { !it.isJsonNull }?.asString
                if (topic == TOPIC_LIVE) {
                    if (status == "ok") {
                        val wasJoined = joined
                        joined = true
                        if (!wasJoined) {
                            currentAccessToken?.takeIf { it.isNotBlank() }?.let { pushAccessToken(it) }
                            listener?.onRealtimeConnected()
                        }
                    } else if (status == "error" && !joined) {
                        val reason = payload.toString()
                        Log.w(TAG, "Live channel join rejected ($reason), retrying public join")
                        if (reason.contains("JWT", ignoreCase = true) || reason.contains("token", ignoreCase = true)) {
                            joinLiveChannel(includeUserJwt = false)
                        }
                    }
                } else if (topic == TOPIC_DB && status == "ok") {
                    dbJoined = true
                }
            }
            "phx_error", "phx_close" -> {
                if (topic == TOPIC_LIVE && connected && shouldStayConnected) {
                    joined = false
                    scope.launch {
                        delay(1_200L)
                        if (connected && shouldStayConnected && !joined) {
                            joinLiveChannel(includeUserJwt = true)
                        }
                    }
                } else if (topic == TOPIC_DB) {
                    dbJoined = false
                }
            }
            "presence_state" -> {
                handlePresenceState(payload)
            }
            "presence_diff" -> {
                handlePresenceDiff(payload)
            }
            "postgres_changes" -> {
                val data = payload.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: payload
                val table = data.get("table")?.takeIf { !it.isJsonNull }?.asString ?: return
                val changeType = data.get("type")?.takeIf { !it.isJsonNull }?.asString
                    ?: data.get("eventType")?.takeIf { !it.isJsonNull }?.asString
                    ?: "UPDATE"
                val record = data.get("record")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: data.get("old_record")?.takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject
                when (table) {
                    "messages" -> listener?.onMessageTableChanged(changeType, record)
                    "party_messages" -> listener?.onPartyMessageChanged(changeType, record)
                    "friendships" -> listener?.onFriendshipTableChanged(changeType, record)
                    "parties", "party_members" -> listener?.onPartyTableChanged(changeType, record)
                    "profiles" -> if (record != null) listener?.onProfileUpdated(record)
                }
            }
            "broadcast" -> {
                val bcEvent = payload.get("event")?.takeIf { !it.isJsonNull }?.asString ?: return
                val inner = payload.get("payload")?.takeIf { it.isJsonObject }?.asJsonObject ?: payload
                dispatchBroadcastEvent(bcEvent, inner)
            }
            else -> {
                if (event in BROADCAST_EVENTS) {
                    val inner = payload.get("payload")?.takeIf { it.isJsonObject }?.asJsonObject ?: payload
                    dispatchBroadcastEvent(event, inner)
                }
            }
        }
    }

    private fun dispatchBroadcastEvent(bcEvent: String, inner: JsonObject) {
        when (bcEvent) {
            "typing" -> handleIncomingTyping(inner)
            "dm_chat" -> {
                clearTyperOnIncomingMessage(inner)
                listener?.onBroadcastDmChat(inner)
            }
            "dm_read" -> listener?.onBroadcastDmRead(inner)
            "message_edit" -> listener?.onBroadcastMessageEdit(inner)
            "party_chat" -> {
                clearTyperOnIncomingMessage(inner)
                listener?.onBroadcastPartyChat(inner)
            }
            "party_signal" -> listener?.onBroadcastPartySignal(inner)
            "presence_update" -> listener?.onBroadcastPresence(inner)
        }
    }

    private fun handlePresenceState(state: JsonObject) {
        val me = currentUserId
        onlineSocketPeers.clear()
        for ((key, value) in state.entrySet()) {
            if (key.isBlank() || key == me) continue
            onlineSocketPeers.add(key)
            val modeKey = extractModeFromPresenceMeta(value)
            listener?.onPeerSocketPresenceChanged(key, connected = true, modeKey = modeKey)
        }
    }

    private fun handlePresenceDiff(diff: JsonObject) {
        val me = currentUserId
        val joins = diff.get("joins")?.takeIf { it.isJsonObject }?.asJsonObject
        val leaves = diff.get("leaves")?.takeIf { it.isJsonObject }?.asJsonObject

        joins?.entrySet()?.forEach { (key, value) ->
            if (key.isNotBlank() && key != me) {
                onlineSocketPeers.add(key)
                val modeKey = extractModeFromPresenceMeta(value)
                listener?.onPeerSocketPresenceChanged(key, connected = true, modeKey = modeKey)
            }
        }
        leaves?.entrySet()?.forEach { (key, _) ->
            if (key.isNotBlank() && key != me && joins?.has(key) != true) {
                onlineSocketPeers.remove(key)
                listener?.onPeerSocketPresenceChanged(key, connected = false, modeKey = null)
            }
        }
    }

    private fun extractModeFromPresenceMeta(entry: JsonElement?): String? {
        val obj = entry?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val metas = obj.get("metas")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val first = metas.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return first.get("mode")?.takeIf { !it.isJsonNull }?.asString
    }

    private fun clearTyperOnIncomingMessage(inner: JsonObject) {
        val sender = inner.get("sender")?.takeIf { !it.isJsonNull }?.asString ?: return
        val partyId = inner.get("partyId")?.takeIf { !it.isJsonNull }?.asString
        val key = if (!partyId.isNullOrBlank()) partyTypingKey(partyId) else dmTypingKey(sender)
        scope.launch(Dispatchers.Main) {
            activeTypers.remove(key)
        }
    }

    private fun handleIncomingTyping(inner: JsonObject) {
        val me = currentUserId ?: return
        val senderId = inner.get("senderId")?.takeIf { !it.isJsonNull }?.asString ?: return
        if (senderId == me) return
        val senderName = inner.get("senderName")?.takeIf { !it.isJsonNull }?.asString ?: "Player"
        val recipientId = inner.get("recipientId")?.takeIf { !it.isJsonNull }?.asString
        val partyId = inner.get("partyId")?.takeIf { !it.isJsonNull }?.asString
        val active = inner.get("active")?.takeIf { !it.isJsonNull }?.asBoolean ?: true

        val key = when {
            !partyId.isNullOrBlank() -> partyTypingKey(partyId)
            recipientId == me -> dmTypingKey(senderId)
            else -> return
        }
        scope.launch(Dispatchers.Main) {
            if (!active) {
                activeTypers.remove(key)
            } else {
                val wasAlreadyTyping = activeTypers.containsKey(key)
                activeTypers[key] = senderName to (System.currentTimeMillis() + TYPING_TTL_MS)
                if (!wasAlreadyTyping) {
                    SocialSoundEffects.playPeerTyping()
                }
            }
        }
    }
}
