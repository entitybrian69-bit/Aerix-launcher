package net.kdt.pojavlaunch.aerix.social

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Supabase Realtime client (Phoenix Channels v1 over OkHttp WebSocket).
 *
 * Designed for the Supabase free tier (2M messages/month):
 * - Multiplexes database changes (`postgres_changes`), typing indicators (`broadcast`),
 *   party signals (`broadcast`) and live presence over a single WebSocket channel.
 * - Throttles outbound typing events to >= 2.5s intervals and auto-expires remote typing after 4.5s.
 * - Reconnects with exponential backoff and triggers offline outbox flush on connect.
 */
object SocialRealtime {
    private const val TAG = "AerixSocialRealtime"
    private const val TOPIC = "realtime:aerix:sync"
    private const val HEARTBEAT_MS = 25_000L
    private const val TYPING_TTL_MS = 4_500L
    private const val TYPING_SEND_THROTTLE_MS = 2_500L

    private val gson = Gson()
    private val refSeq = AtomicInteger(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var socket: WebSocket? = null
    @Volatile private var connected: Boolean = false
    @Volatile private var joined: Boolean = false
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

    /**
     * Map of conversation key (`dm:<peerUserId>` or `party:<partyId>`) ->
     * Pair(typerDisplayName, expiresAtEpochMs). Observed directly by Compose chat headers.
     */
    val activeTypers = mutableStateMapOf<String, Pair<String, Long>>()

    interface Listener {
        fun onRealtimeConnected()
        fun onMessageTableChanged(eventType: String, record: JsonObject?)
        fun onPartyMessageChanged(eventType: String, record: JsonObject?)
        fun onFriendshipTableChanged(eventType: String, record: JsonObject?)
        fun onPartyTableChanged(eventType: String, record: JsonObject?)
        fun onProfileUpdated(record: JsonObject)
        fun onBroadcastPartyChat(payload: JsonObject)
        fun onBroadcastPartySignal(payload: JsonObject)
        fun onBroadcastPresence(payload: JsonObject)
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
            if (tokenChanged) {
                pushAccessToken(accessToken)
            }
            return
        }

        reconnectJob?.cancel()
        openSocket(endpoint)
    }

    @Synchronized
    fun updateAccessToken(accessToken: String) {
        currentAccessToken = accessToken
        if (connected) {
            pushAccessToken(accessToken)
        }
    }

    @Synchronized
    fun disconnect() {
        shouldStayConnected = false
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        typingCleanupJob?.cancel()
        connected = false
        joined = false
        try {
            socket?.close(1000, "Signed out")
        } catch (_: Throwable) {
        }
        socket = null
        scope.launch(Dispatchers.Main) { activeTypers.clear() }
    }

    fun dmTypingKey(peerId: String): String = "dm:$peerId"
    fun partyTypingKey(partyId: String): String = "party:$partyId"

    /** Broadcasts a lightweight typing ping without touching PostgreSQL. */
    fun sendTyping(targetPeerId: String?, partyId: String?, myDisplayName: String) {
        val me = currentUserId ?: return
        if (!isConnected) return
        val now = System.currentTimeMillis()
        val prev = lastTypingSentMs.get()
        if (now - prev < TYPING_SEND_THROTTLE_MS) return
        lastTypingSentMs.set(now)

        val inner = JsonObject().apply {
            addProperty("senderId", me)
            addProperty("senderName", myDisplayName)
            targetPeerId?.let { addProperty("recipientId", it) }
            partyId?.let { addProperty("partyId", it) }
            addProperty("ts", now)
        }
        sendBroadcast("typing", inner)
    }

    fun sendPartyChatBroadcast(message: SocialMessage) {
        if (!isConnected) return
        val inner = JsonObject().apply {
            addProperty("id", message.id)
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
        sendPhoenixFrame(TOPIC, "broadcast", payload)
    }

    private fun openSocket(endpoint: String) {
        try {
            socket?.cancel()
        } catch (_: Throwable) {
        }
        connected = false
        joined = false

        val req = Request.Builder().url(endpoint).build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                backoffStep = 0
                joinSyncChannel()
                startLoops()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                joined = false
                try {
                    webSocket.close(code, reason)
                } catch (_: Throwable) {
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                joined = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d(TAG, "Realtime socket disconnected: ${t.message}")
                connected = false
                joined = false
                scheduleReconnect()
            }
        })
    }

    private fun joinSyncChannel() {
        val token = currentAccessToken ?: return
        val me = currentUserId ?: ""

        val postgresChanges = JsonArray().apply {
            listOf("messages", "friendships", "parties", "party_members", "party_messages", "profiles").forEach { tbl ->
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
            add("presence", JsonObject().apply {
                addProperty("key", me)
            })
            add("postgres_changes", postgresChanges)
        }

        val payload = JsonObject().apply {
            add("config", config)
            addProperty("access_token", token)
        }

        sendPhoenixFrame(TOPIC, "phx_join", payload)
    }

    private fun pushAccessToken(token: String) {
        val payload = JsonObject().apply {
            addProperty("access_token", token)
        }
        sendPhoenixFrame(TOPIC, "access_token", payload)
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
                delay(1_500L)
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
                0 -> 2_000L
                1 -> 4_000L
                2 -> 8_000L
                3 -> 15_000L
                else -> 30_000L
            }
            delay(waitMs)
            if (shouldStayConnected && !connected) {
                openSocket(endpoint)
            }
        }
    }

    private fun sendPhoenixFrame(topic: String, event: String, payload: JsonObject) {
        val ws = socket ?: return
        val frame = JsonObject().apply {
            addProperty("topic", topic)
            addProperty("event", event)
            add("payload", payload)
            addProperty("ref", refSeq.getAndIncrement().toString())
        }
        try {
            ws.send(gson.toJson(frame))
        } catch (_: Throwable) {
        }
    }

    private fun handleFrame(raw: String) {
        try {
            val root = JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject ?: return
            val event = root.get("event")?.takeIf { !it.isJsonNull }?.asString ?: return
            val payload = root.get("payload")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()

            when (event) {
                "phx_reply" -> {
                    val status = payload.get("status")?.takeIf { !it.isJsonNull }?.asString
                    if (status == "ok" && !joined && root.get("topic")?.asString == TOPIC) {
                        joined = true
                        currentAccessToken?.let { pushAccessToken(it) }
                        listener?.onRealtimeConnected()
                    }
                }
                "postgres_changes" -> {
                    val data = payload.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: payload
                    val table = data.get("table")?.takeIf { !it.isJsonNull }?.asString ?: return
                    val changeType = data.get("type")?.takeIf { !it.isJsonNull }?.asString
                        ?: data.get("eventType")?.takeIf { !it.isJsonNull }?.asString
                        ?: "UPDATE"
                    val record = data.get("record")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?: data.get("old_record")?.takeIf { it.isJsonObject }?.asJsonObject
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
                    val inner = payload.get("payload")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
                    when (bcEvent) {
                        "typing" -> handleIncomingTyping(inner)
                        "party_chat" -> listener?.onBroadcastPartyChat(inner)
                        "party_signal" -> listener?.onBroadcastPartySignal(inner)
                        "presence_update" -> listener?.onBroadcastPresence(inner)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Frame parse error: ${t.message}")
        }
    }

    private fun handleIncomingTyping(inner: JsonObject) {
        val me = currentUserId ?: return
        val senderId = inner.get("senderId")?.takeIf { !it.isJsonNull }?.asString ?: return
        if (senderId == me) return
        val senderName = inner.get("senderName")?.takeIf { !it.isJsonNull }?.asString ?: "Player"
        val recipientId = inner.get("recipientId")?.takeIf { !it.isJsonNull }?.asString
        val partyId = inner.get("partyId")?.takeIf { !it.isJsonNull }?.asString

        val key = when {
            !partyId.isNullOrBlank() -> partyTypingKey(partyId)
            recipientId == me -> dmTypingKey(senderId)
            else -> return
        }
        val expires = System.currentTimeMillis() + TYPING_TTL_MS
        scope.launch(Dispatchers.Main) {
            activeTypers[key] = senderName to expires
        }
    }
}
