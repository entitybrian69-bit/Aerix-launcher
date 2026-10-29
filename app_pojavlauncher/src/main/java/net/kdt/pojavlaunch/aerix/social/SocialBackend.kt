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

    /** The scheme the browser comes back on after a Google sign-in (see the manifest). */
    const val OAUTH_REDIRECT = "aerix-social://auth"

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

    @JvmStatic
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
    @JvmStatic
    fun myUserId(): String? = userId

    @JvmStatic
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

    /**
     * Google sign-in without any Google SDK: the browser opens the project's own authorization
     * endpoint, the player picks an account there, and the tokens come back on [OAUTH_REDIRECT],
     * which the launcher activity hands to [completeBrowserSession].
     *
     * @return false when the provider is not switched on in Supabase, so the caller can say so
     */
    @JvmStatic
    fun beginGoogleSignIn(context: Context): Boolean {
        val url = "$PROJECT_URL/auth/v1/authorize?provider=google" +
            "&redirect_to=" + java.net.URLEncoder.encode(OAUTH_REDIRECT, "UTF-8") +
            "&apikey=" + java.net.URLEncoder.encode(PUBLISHABLE_KEY, "UTF-8")
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Could not open the Google sign-in page", error)
            false
        }
    }

    /**
     * Finishes a browser sign-in: the redirect carries the tokens in its fragment.
     *
     * @return true when a session was restored from [redirect]
     */
    @JvmStatic
    fun completeBrowserSession(redirect: String?): Boolean {
        if (redirect.isNullOrBlank()) return false
        val fragment = redirect.substringAfter('#', "")
        if (fragment.isEmpty()) return false
        val values = fragment.split('&').mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) null
            else pair.substring(0, index) to android.net.Uri.decode(pair.substring(index + 1))
        }.toMap()
        val token = values["access_token"] ?: return false
        val refresh = values["refresh_token"] ?: return false
        accessToken = token
        refreshToken = refresh
        expiresAtMs = System.currentTimeMillis() + ((values["expires_in"]?.toLongOrNull() ?: 3600L) * 1000) - 60_000
        signedIn = true
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("refresh_token", refresh)
            ?.apply()
        return true
    }

    /**
     * Sends the confirmation mail again, for the players who sign up while the project still asks
     * for a confirmed address. Harmless when the address is already confirmed.
     */
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

    /** Mails a reset link, so a forgotten password is not a dead end. */
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

    /** True when the last failure was the project asking for a confirmed email address. */
    @JvmStatic
    fun needsConfirmation(): Boolean =
        lastError?.contains("confirm", ignoreCase = true) == true

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
    @JvmStatic
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
                    "&select=id,username,name,display_name,mc_username,avatar_url,last_seen"
            )
            val byId = HashMap<String, SocialFriend>()
            profiles?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                val row = element as? JsonObject ?: return@forEach
                val id = row.stringOrNull("id") ?: return@forEach
                val display = row.stringOrNull("name") ?: row.stringOrNull("display_name")
                byId[id] = SocialFriend(
                    id = id,
                    mcUsername = row.stringOrNull("mc_username")?.takeIf { it.isNotBlank() }
                        ?: row.stringOrNull("username")
                        ?: display ?: "Player",
                    displayName = display ?: "Player",
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
            // One door only: an exact username, answered by the database.
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

    // ---- profile ----------------------------------------------------------------------------

    /** The signed-in player's own card, refreshed whenever a profile screen opens. */
    var myProfile by mutableStateOf<SocialProfile?>(null)
        private set

    /** True until the profile form has been filled in once; the app shows the form until then. */
    val needsOnboarding: Boolean
        get() = myProfile?.onboarded != true

    suspend fun loadMyProfile(): SocialProfile? {
        val card = attempt {
            ensureToken()
            val json = call("POST", "$PROJECT_URL/rest/v1/rpc/profile_card", "{}")
            parseProfile(json)
        }
        if (card != null) {
            myProfile = card
            mcUsername = card.mcUsername ?: mcUsername
        }
        return card
    }

    /** Any profile, by exact username. The database only answers exact matches. */
    suspend fun loadProfileCard(username: String): SocialProfile? = attempt {
        ensureToken()
        val body = JsonObject().apply { addProperty("p_username", username.trim()) }
        parseProfile(call("POST", "$PROJECT_URL/rest/v1/rpc/profile_card", body.toString()))
    }

    /** Exact-username lookup used by "add friend". Returns null when nobody has that username. */
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
            SocialProfile(
                id = row.stringOrNull("id") ?: return@attempt null,
                username = row.stringOrNull("username"),
                name = row.stringOrNull("name"),
                mcUsername = row.stringOrNull("mc_username"),
                avatarUrl = row.stringOrNull("avatar_url"),
                country = row.stringOrNull("country"),
                lastSeenMs = row.longOr("last_seen_ms", 0L)
            )
        }
        if (found == null && lastError == null) lastError = "No one is called @$clean"
        return found
    }

    /**
     * Claims a username. Returns null when it worked, or the sentence to show the player - the
     * one-change-a-month rule lives in the database, not here.
     */
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
            // The function returns a JSON string: null on success, the message on failure.
            if (json == null) return@attempt "Could not save the username."
            if (json.isJsonNull) null else json.asString
        }
    }

    /** Writes the whole profile card. Blank strings are stored as null so the UI can fall back. */
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
        val saved = attempt {
            ensureToken()
            val me = userId ?: return@attempt null
            val row = JsonObject().apply {
                addProperty("id", me)
                addProperty("name", name?.trim()?.takeIf { it.isNotBlank() })
                addProperty("display_name", name?.trim()?.takeIf { it.isNotBlank() })
                addProperty("country", country?.trim()?.takeIf { it.isNotBlank() })
                addProperty("bio", bio?.trim()?.takeIf { it.isNotBlank() })
                addProperty("status_text", statusText?.trim()?.takeIf { it.isNotBlank() })
                addProperty("looking_to_play", lookingToPlay)
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
        loadMyProfile()
        return saved != null
    }

    /**
     * Puts a picture in the project's own storage bucket and returns its public URL, or null when
     * the upload failed. Each file is named after the player, so the storage rules can insist that
     * people only write inside their own folder.
     */
    suspend fun uploadImage(folder: String, bytes: ByteArray, extension: String = "png"): String? =
        withContext(Dispatchers.IO) {
            val me = userId
            val token = accessToken
            if (me == null || token == null) {
                lastError = "Sign in first"
                return@withContext null
            }
            val path = "$me/${folder}-${System.currentTimeMillis()}.$extension"
            val mime = when (extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> "image/png"
            }
            try {
                val connection = (URL("$PROJECT_URL/storage/v1/object/social-media/$path")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 20000
                    readTimeout = 30000
                    setRequestProperty("apikey", PUBLISHABLE_KEY)
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", mime)
                    setRequestProperty("x-upsert", "true")
                    outputStream.use { it.write(bytes) }
                }
                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                connection.disconnect()
                if (code !in 200..299) {
                    lastError = serverMessage(text) ?: "Upload failed (HTTP $code)"
                    return@withContext null
                }
                "$PROJECT_URL/storage/v1/object/public/social-media/$path"
            } catch (error: Throwable) {
                Log.w(TAG, "Upload failed", error)
                lastError = error.message ?: "Upload failed"
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

    /** Everyone following me, newest first. */
    suspend fun loadFollowers(): List<SocialProfile> = loadRelated("follows?select=follower&following=eq.", "follower")

    /** Everyone I follow, newest first. */
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
                SocialProfile(
                    id = row.stringOrNull("id") ?: return@mapNotNull null,
                    username = row.stringOrNull("username"),
                    name = row.stringOrNull("name") ?: row.stringOrNull("display_name"),
                    mcUsername = row.stringOrNull("mc_username"),
                    avatarUrl = row.stringOrNull("avatar_url"),
                    country = row.stringOrNull("country"),
                    lastSeenMs = parseTimestamp(row.stringOrNull("last_seen") ?: "")
                )
            }
        }
        return list ?: emptyList()
    }

    // ---- safety -----------------------------------------------------------------------------

    suspend fun block(otherId: String): Boolean {
        val done = attempt {
            ensureToken()
            val me = userId ?: return@attempt null
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

    /** Heartbeat with a status line and the "looking to play" flag. */
    suspend fun setPresence(status: String?, looking: Boolean) {
        if (!signedIn) return
        attempt {
            ensureToken()
            call(
                "POST",
                "$PROJECT_URL/rest/v1/rpc/set_presence",
                JsonObject().apply {
                    addProperty("p_status", status?.take(60) ?: "")
                    addProperty("p_looking", looking)
                }.toString()
            )
        }
    }

    /** How many unread messages are waiting for me, across every conversation. */
    suspend fun unreadCount(): Int {
        val count = attempt {
            ensureToken()
            val me = userId ?: return@attempt 0
            val json = call(
                "GET",
                "$PROJECT_URL/rest/v1/messages?select=id&recipient=eq.$me&read_at=is.null&limit=500"
            ) ?: return@attempt 0
            json.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
        }
        unread = count ?: 0
        return unread
    }

    /** Marks everything [otherId] sent me as read, so their ticks turn on and the badge clears. */
    suspend fun markRead(otherId: String) {
        attempt {
            ensureToken()
            val me = userId ?: return@attempt Unit
            call(
                "PATCH",
                "$PROJECT_URL/rest/v1/messages?recipient=eq.$me&sender=eq.$otherId&read_at=is.null",
                JsonObject().apply { addProperty("read_at", isoNow()) }.toString(),
                extraHeaders = mapOf("Prefer" to "return=minimal")
            )
            Unit
        }
        unreadCount()
    }

    private fun isoNow(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    /** A profile card, parsed from either RPC's JSON. */
    private fun parseProfile(json: JsonElement?): SocialProfile? {
        val row = json as? JsonObject ?: return null
        val id = row.stringOrNull("id") ?: return null
        val ranks = row.get("ranks")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { element -> runCatching { element.asString }.getOrNull() }
            ?: emptyList()
        return SocialProfile(
            id = id,
            username = row.stringOrNull("username"),
            name = row.stringOrNull("name") ?: row.stringOrNull("display_name"),
            mcUsername = row.stringOrNull("mc_username"),
            country = row.stringOrNull("country"),
            bio = row.stringOrNull("bio"),
            avatarUrl = row.stringOrNull("avatar_url"),
            bannerUrl = row.stringOrNull("banner_url"),
            skinUrl = row.stringOrNull("skin_url"),
            capeUrl = row.stringOrNull("cape_url"),
            statusText = row.stringOrNull("status_text"),
            lookingToPlay = row.get("looking_to_play")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            onboarded = row.get("onboarded")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            usernameChangedAtMs = parseTimestamp(row.stringOrNull("username_changed_at") ?: ""),
            lastSeenMs = row.longOr("last_seen_ms", 0L),
            followers = row.longOr("followers", 0L).toInt(),
            following = row.longOr("following", 0L).toInt(),
            friends = row.longOr("friends", 0L).toInt(),
            messages = row.longOr("messages", 0L).toInt(),
            ranks = ranks,
            isMe = row.get("is_me")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            iFollow = row.get("i_follow")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        )
    }

    /** Unread total, kept in step so the Home tile and the sidebar badge agree. */
    var unread by mutableStateOf(0)
        private set


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
                // The server says what is wrong in its own words ("Email not confirmed",
                // "Invalid login credentials"); show that instead of a generic sentence, so the
                // screen explains the problem and can be screenshotted.
                serverMessage(text)?.let { lastError = "$it (HTTP $code)" }
                    ?: run { lastError = "Server said HTTP $code" }
                if (code == 401 && authenticated && !url.contains("/auth/v1/token")) signOut()
                return null
            }
            if (text.isBlank()) JsonParser.parseString("{}") else JsonParser.parseString(text)
        } catch (error: Throwable) {
            Log.w(TAG, "$method $url failed", error)
            null
        }
    }

    /** The message inside a Supabase error body, or null when there is none. */
    private fun serverMessage(body: String): String? {
        if (body.isBlank()) return null
        val json = runCatching { JsonParser.parseString(body) as? JsonObject }.getOrNull() ?: return null
        return json.stringOrNull("msg")
            ?: json.stringOrNull("message")
            ?: json.stringOrNull("error_description")
            ?: json.stringOrNull("error")
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
