/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The social backend: accounts, friend requests, presence and messages.
 *
 * Talks to the Aerix Supabase project directly over HTTPS - no intermediate server, no cost.
 * The key below is the project's *publishable* key, which is public by design: every row is
 * protected by the row-level security in supabase/schema.sql, so a client can only ever read its
 * own conversations.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** One other player, as the friends list shows them. */
data class SocialFriend(
    val id: String,
    val mcUsername: String,
    val displayName: String,
    val lastSeenMs: Long,
    val pendingIncoming: Boolean = false,
    val pendingOutgoing: Boolean = false
) {
    /** Online when a heartbeat arrived in the last two minutes. */
    val online: Boolean get() = lastSeenMs > 0 && System.currentTimeMillis() - lastSeenMs < 2 * 60 * 1000
}

data class SocialMessage(
    val id: Long,
    val sender: String,
    val recipient: String,
    val body: String,
    val atMs: Long
)

object SocialBackend {
    private const val TAG = "AerixSocial"
    private const val PREFS = "aerix_social"

    /** The Aerix social project. Both values are public; row-level security protects the rows. */
    const val PROJECT_URL = "https://lktupxfszfefsoyksfkg.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_mA8stbSVwAg4Il3xgHDH5g_9a7WwojI"

    /** Set once the Google Web client ID exists; until then the Google button explains itself. */
    const val GOOGLE_WEB_CLIENT_ID = ""

    var signedIn by mutableStateOf(false)
        private set
    var email by mutableStateOf<String?>(null)
        private set
    var mcUsername by mutableStateOf<String?>(null)
        private set
    var friends by mutableStateOf<List<SocialFriend>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var userId: String? = null
    private var expiresAtMs = 0L
    private var appContext: Context? = null

    // ---- session ---------------------------------------------------------------------------

    fun attach(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (signedIn) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        refreshToken = prefs.getString("refresh_token", null)
        userId = prefs.getString("user_id", null)
        email = prefs.getString("email", null)
        // A stored refresh token is enough to come back: the first request refreshes it.
        if (refreshToken != null && userId != null) signedIn = true
    }

    /** This user's row id, so the chat knows which bubbles are theirs. */
    fun myUserId(): String? = userId

    fun signOut() {
        accessToken = null
        refreshToken = null
        userId = null
        expiresAtMs = 0
        signedIn = false
        email = null
        mcUsername = null
        friends = emptyList()
        lastError = null
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }

    suspend fun signIn(address: String, password: String): Boolean {
        val ok = attempt {
            val body = JsonObject().apply {
                addProperty("email", address.trim())
                addProperty("password", password)
            }
            val json = asObject(call("POST", "$PROJECT_URL/auth/v1/token?grant_type=password", body.toString()))
            if (json == null) {
                lastError = "Wrong email or password"
                return@attempt false
            }
            storeSession(json)
            lastError = null
            true
        } ?: false
        if (ok) loadProfile()
        return ok
    }

    suspend fun signUp(address: String, password: String): Boolean {
        val created = attempt {
            val body = JsonObject().apply {
                addProperty("email", address.trim())
                addProperty("password", password)
            }
            val json = asObject(call("POST", "$PROJECT_URL/auth/v1/signup", body.toString()))
            if (json == null) {
                lastError = "Could not create the account. Is that email already in use?"
                return@attempt null
            }
            lastError = null
            json
        }
        val json = created ?: return false
        // With "Confirm email" on, Supabase returns the user but no session: the player has to
        // open the mail first. With it off (what the setup guide asks for) the session is here.
        if (json.has("access_token")) {
            storeSession(json)
            loadProfile()
            return true
        }
        lastError = "Account created. Confirm it from your email, then sign in."
        return false
    }

    private fun storeSession(json: JsonObject) {
        accessToken = json.stringOrNull("access_token")
        refreshToken = json.stringOrNull("refresh_token")
        expiresAtMs = System.currentTimeMillis() + (json.longOr("expires_in", 3600) * 1000) - 60_000
        val user = asObject(json.get("user"))
        userId = user?.stringOrNull("id")
        email = user?.stringOrNull("email")
        signedIn = true
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("refresh_token", refreshToken)
            ?.putString("user_id", userId)
            ?.putString("email", email)
            ?.apply()
    }

    private suspend fun ensureToken() {
        if (accessToken != null && System.currentTimeMillis() < expiresAtMs) return
        val token = refreshToken ?: return
        val body = JsonObject().apply { addProperty("refresh_token", token) }
        val json = asObject(
            call(
                "POST",
                "$PROJECT_URL/auth/v1/token?grant_type=refresh_token",
                body.toString(),
                authenticated = false
            )
        )
        if (json == null) {
            signOut()
            return
        }
        storeSession(json)
    }

    // ---- profile ---------------------------------------------------------------------------

    /** What the launcher knows about this user's Minecraft name, remembered across sessions. */
    fun rememberMinecraftName(name: String?) {
        val trimmed = name?.trim()
        mcUsername = trimmed?.takeIf { it.isNotEmpty() }
        val id = userId ?: return
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("mc_$id", mcUsername)?.apply()
    }

    private fun storedMinecraftName(): String? {
        val id = userId ?: return null
        return appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString("mc_$id", null)
    }

    suspend fun loadProfile() {
        attempt {
            ensureToken()
            val id = userId ?: return@attempt Unit
            val json = call(
                "GET",
                "$PROJECT_URL/rest/v1/profiles?id=eq.$id&select=id,mc_username,display_name"
            )
            val rows = json?.takeIf { it.isJsonArray }?.asJsonArray
            if (rows != null && rows.size() > 0) {
                mcUsername = (rows[0] as? JsonObject)?.stringOrNull("mc_username")
                    ?.takeIf { it.isNotBlank() } ?: storedMinecraftName()
            } else {
                mcUsername = storedMinecraftName()
            }
            upsertProfile()
        }
    }

    private suspend fun upsertProfile() {
        val id = userId ?: return
        val body = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("id", id)
                addProperty("display_name", mcUsername ?: email ?: "Player")
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

    // ---- friends ---------------------------------------------------------------------------

    suspend fun refreshFriends() {
        attempt {
            ensureToken()
            val me = userId ?: return@attempt Unit
            val json = call(
                "GET",
                "$PROJECT_URL/rest/v1/friendships?select=requester,addressee,status" +
                    "&or=(requester.eq.$me,addressee.eq.$me)"
            )
            val rows = json?.takeIf { it.isJsonArray }?.asJsonArray
            if (rows == null || rows.size() == 0) {
                friends = emptyList()
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
                when (row.stringOrNull("status")) {
                    "accepted" -> accepted.add(other)
                    "pending" -> if (requester == me) outgoing.add(other) else incoming.add(other)
                }
            }
            val ids = (accepted + incoming + outgoing).distinct()
            if (ids.isEmpty()) {
                friends = emptyList()
                return@attempt Unit
            }
            val quoted = ids.joinToString(",") { "\"$it\"" }
            val profiles = call(
                "GET",
                "$PROJECT_URL/rest/v1/profiles?id=in.($quoted)" +
                    "&select=id,mc_username,display_name,last_seen"
            )
            val byId = HashMap<String, SocialFriend>()
            profiles?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                val row = element as? JsonObject ?: return@forEach
                val id = row.stringOrNull("id") ?: return@forEach
                byId[id] = SocialFriend(
                    id = id,
                    mcUsername = row.stringOrNull("mc_username")?.takeIf { it.isNotBlank() }
                        ?: row.stringOrNull("display_name") ?: "Player",
                    displayName = row.stringOrNull("display_name") ?: "Player",
                    lastSeenMs = parseTimestamp(row.stringOrNull("last_seen") ?: ""),
                    pendingIncoming = id in incoming,
                    pendingOutgoing = id in outgoing
                )
            }
            friends = ids.mapNotNull { byId[it] }
        }
    }

    /** Sends a friend request to the player whose Minecraft name is [name]. */
    suspend fun addFriendByName(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) {
            lastError = "Enter a Minecraft username"
            return false
        }
        val sent = attempt {
            ensureToken()
            val me = userId ?: return@attempt null
            val found = call(
                "GET",
                "$PROJECT_URL/rest/v1/profiles?mc_username=eq." +
                    URLEncoder.encode(clean, "UTF-8") + "&select=id,mc_username"
            )
            val rows = found?.takeIf { it.isJsonArray }?.asJsonArray
            if (rows == null || rows.size() == 0) {
                lastError = "$clean has not opened Socials yet"
                return@attempt null
            }
            val target = (rows[0] as? JsonObject)?.stringOrNull("id") ?: return@attempt null
            if (target == me) {
                lastError = "That is you"
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
            lastError = if (result == null) "Could not send the request" else null
            result
        }
        refreshFriends()
        return sent != null
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

    // ---- messages --------------------------------------------------------------------------

    suspend fun loadMessages(otherId: String): List<SocialMessage> {
        val messages = attempt {
            ensureToken()
            val me = userId ?: return@attempt emptyList<SocialMessage>()
            val json = call(
                "GET",
                "$PROJECT_URL/rest/v1/messages?select=id,sender,recipient,body,created_at" +
                    "&or=(and(sender.eq.$me,recipient.eq.$otherId)," +
                    "and(sender.eq.$otherId,recipient.eq.$me))" +
                    "&order=created_at.asc&limit=200"
            ) ?: return@attempt emptyList<SocialMessage>()
            val rows = json.takeIf { it.isJsonArray }?.asJsonArray
                ?: return@attempt emptyList<SocialMessage>()
            rows.mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                SocialMessage(
                    id = row.longOr("id", 0L),
                    sender = row.stringOrNull("sender") ?: "",
                    recipient = row.stringOrNull("recipient") ?: "",
                    body = row.stringOrNull("body") ?: "",
                    atMs = parseTimestamp(row.stringOrNull("created_at") ?: "")
                )
            }
        }
        return messages ?: emptyList()
    }

    suspend fun sendMessage(otherId: String, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val sent = attempt {
            ensureToken()
            val me = userId ?: return@attempt null
            call(
                "POST",
                "$PROJECT_URL/rest/v1/messages",
                JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("sender", me)
                        addProperty("recipient", otherId)
                        addProperty("body", trimmed)
                    })
                }.toString(),
                extraHeaders = mapOf("Prefer" to "return=minimal")
            )
        }
        return sent != null
    }

    /** Heartbeat: lets friends see this player as online. Cheap, called about once a minute. */
    suspend fun heartbeat() {
        if (!signedIn) return
        attempt {
            ensureToken()
            call("POST", "$PROJECT_URL/rest/v1/rpc/touch_presence", "{}")
        }
    }

    // ---- plumbing --------------------------------------------------------------------------

    /** Runs [block] off the main thread; a failure is reported, never thrown at the UI. */
    private suspend fun <T> attempt(block: suspend () -> T): T? = withContext(Dispatchers.IO) {
        runCatching { block() }.onFailure {
            Log.w(TAG, "Social request failed", it)
            lastError = it.message ?: "Network error"
        }.getOrNull()
    }

    /**
     * One HTTP call against the project.
     *
     * @param authenticated send the current user's access token, so row-level security applies
     * @return the parsed body, or null when the request failed
     */
    private fun call(
        method: String,
        url: String,
        body: String? = null,
        authenticated: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap()
    ): JsonElement? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("apikey", PUBLISHABLE_KEY)
                setRequestProperty("Content-Type", "application/json")
                if (authenticated) accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
                extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            connection.disconnect()
            if (code !in 200..299) {
                Log.w(TAG, "$method $url -> $code $text")
                if (code == 401) signOut()
                return null
            }
            if (text.isBlank()) JsonParser.parseString("{}") else JsonParser.parseString(text)
        } catch (error: Throwable) {
            Log.w(TAG, "$method $url failed", error)
            null
        }
    }

    /** The body of a call, when it is a JSON object. */
    private fun asObject(element: JsonElement?): JsonObject? = element as? JsonObject

    /** A string field, or null when it is missing, null or not a string. */
    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        return runCatching { element.asString }.getOrNull()
    }

    private fun JsonObject.longOr(key: String, fallback: Long): Long {
        val element = get(key) ?: return fallback
        return runCatching { element.asLong }.getOrDefault(fallback)
    }

    /** Supabase writes `2026-10-29T05:04:31.123456+00:00`; only the seconds matter here. */
    private fun parseTimestamp(value: String): Long {
        if (value.length < 19) return 0L
        return runCatching {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(value.substring(0, 19))?.time ?: 0L
        }.getOrDefault(0L)
    }
}
