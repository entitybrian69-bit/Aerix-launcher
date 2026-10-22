/*
 * Aerix Launcher Social layer.
 *
 * Aerix accounts on a server, through Supabase (free tier). This is what makes an account follow
 * the person instead of the phone: sign in on any device, and come back to an account you deleted.
 *
 * Only two settings are needed — the project URL and the public anon key. The anon key is meant
 * to be public: row-level security in the schema is what protects the data, not the key.
 *
 * Everything here is plain HTTPS against Supabase's REST API, so the launcher carries no extra
 * dependency and nothing is hidden behind an SDK.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.auth

import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** One account as the server knows it, plus the tokens that prove the sign-in. */
data class RemoteAerixAccount(
    val userId: String,
    val email: String,
    val username: String,
    val accessToken: String,
    val refreshToken: String,
    /** Non-null when the account was deleted and can still be restored. */
    val deletedAt: Long?
)

/** A refusal from the server, already turned into something a player can read. */
class AerixRemoteException(message: String) : Exception(message)

/**
 * The smallest useful Supabase client: auth plus the one profile row.
 *
 * Callers are responsible for being off the main thread.
 */
class AerixRemoteAccounts(baseUrl: String, private val anonKey: String) {

    private val root: String = baseUrl.trim().trimEnd('/')

    /** Configured means a project URL and a key are both present and plausible. */
    fun isConfigured(): Boolean =
        root.startsWith("http") && root.length > 12 && anonKey.length > 20

    // ------------------------------------------------------------------ public API

    /**
     * True when the username is already in use. A failure here is treated as "unknown" rather
     * than blocking the signup; the unique index on the server is the real guard.
     */
    fun usernameTaken(username: String): Boolean = try {
        val body = JSONObject().put("candidate", username).toString()
        val response = request("POST", "$root/rest/v1/rpc/aerix_username_taken", body, token = null)
        response.trim() == "true"
    } catch (error: Throwable) {
        Log.w(TAG, "username check skipped", error)
        false
    }

    fun signUp(username: String, email: String, password: String): RemoteAerixAccount {
        if (usernameTaken(username)) {
            throw AerixRemoteException("That username is taken. Try another one.")
        }
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", JSONObject().put("username", username.trim()))
            .toString()
        val session = try {
            post("$root/auth/v1/signup", body, token = null)
        } catch (error: AerixRemoteException) {
            val text = error.message.orEmpty().lowercase()
            throw AerixRemoteException(
                when {
                    text.contains("already registered") || text.contains("already exists") ->
                        "That email already has an account. Sign in instead."
                    text.contains("password") -> "That password is not accepted: use at least 8 characters."
                    text.contains("database") || text.contains("500") ->
                        "The server could not create the account. If the username is unusual, try another one."
                    else -> error.message.orEmpty()
                }
            )
        }
        val account = readSession(session)
        return account.copy(username = profileUsername(account))
    }

    fun signIn(email: String, password: String): RemoteAerixAccount {
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()
        val session = try {
            post("$root/auth/v1/token?grant_type=password", body, token = null)
        } catch (error: AerixRemoteException) {
            val text = error.message.orEmpty().lowercase()
            throw AerixRemoteException(
                if (text.contains("invalid") || text.contains("credentials")) {
                    "That email and password don't match an Aerix account."
                } else {
                    error.message.orEmpty()
                }
            )
        }
        val account = readSession(session)
        return readProfile(account)
    }

    /** Brings a deleted account back. */
    fun restore(accessToken: String, userId: String) {
        val body = JSONObject().put("deleted_at", JSONObject.NULL).toString()
        patch("$root/rest/v1/profiles?id=eq.$userId", body, accessToken)
    }

    /** Marks the account deleted; the row and the password stay, so it can be restored. */
    fun softDelete(accessToken: String, userId: String) {
        val body = JSONObject()
            .put("deleted_at", isoNow())
            .toString()
        patch("$root/rest/v1/profiles?id=eq.$userId", body, accessToken)
    }

    /** Reads the profile row so the account carries the username the server knows. */
    fun readProfile(account: RemoteAerixAccount): RemoteAerixAccount {
        val raw = get(
            "$root/rest/v1/profiles?id=eq.${account.userId}&select=username,email,deleted_at",
            account.accessToken
        )
        val row = runCatching { org.json.JSONArray(raw).optJSONObject(0) }.getOrNull() ?: return account
        val deletedAt = row.optString("deleted_at").takeIf { it.isNotBlank() && it != "null" }
            ?.let { parseIso(it) }
        return account.copy(
            username = row.optString("username").ifBlank { account.username },
            email = row.optString("email").ifBlank { account.email },
            deletedAt = deletedAt
        )
    }

    // ------------------------------------------------------------------ plumbing

    private fun readSession(session: JSONObject): RemoteAerixAccount {
        val accessToken = session.optString("access_token")
        val refreshToken = session.optString("refresh_token")
        val user = session.optJSONObject("user")
        if (accessToken.isBlank() || user == null) {
            // Happens when "Confirm email" is on: Supabase creates the user but hands back no
            // session until the link is clicked.
            throw AerixRemoteException(
                "The account was created but the server wants the email confirmed first. In " +
                    "Supabase: Authentication → Sign In / Providers → Email → turn off " +
                    "\"Confirm email\", then sign in again."
            )
        }
        val email = user.optString("email")
        val meta = user.optJSONObject("user_metadata")
        val username = meta?.optString("username").orEmpty()
            .ifBlank { email.substringBefore('@') }
        return RemoteAerixAccount(
            userId = user.optString("id"),
            email = email,
            username = username,
            accessToken = accessToken,
            refreshToken = refreshToken,
            deletedAt = null
        )
    }

    private fun profileUsername(account: RemoteAerixAccount): String =
        runCatching { readProfile(account).username }.getOrDefault(account.username)

    private fun get(url: String, token: String?): String = request("GET", url, null, token)

    private fun post(url: String, body: String, token: String?): JSONObject =
        JSONObject(request("POST", url, body, token))

    private fun patch(url: String, body: String, token: String?) {
        request("PATCH", url, body, token)
    }

    private fun request(method: String, url: String, body: String?, token: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer ${token ?: anonKey}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (method == "PATCH") setRequestProperty("Prefer", "return=minimal")
            if (body != null) doOutput = true
        }
        try {
            if (body != null) {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                Log.w(TAG, "$method $url failed with $code: $text")
                throw AerixRemoteException(describe(text, code))
            }
            return text
        } catch (error: AerixRemoteException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "$method $url failed", error)
            throw AerixRemoteException(
                "Could not reach the Aerix server. Check the project URL and the connection."
            )
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** Supabase sends several shapes of error; pull out whichever one carries the reason. */
    private fun describe(text: String, code: Int): String {
        val json = runCatching { JSONObject(text) }.getOrNull()
        val detail = json?.optString("msg").orEmpty()
            .ifBlank { json?.optString("message").orEmpty() }
            .ifBlank { json?.optString("error_description").orEmpty() }
            .ifBlank { json?.optString("error").orEmpty() }
            .ifBlank { text.take(160) }
        return when {
            detail.isNotBlank() -> detail
            code == 401 || code == 403 -> "The server refused the request. Check the anon key."
            code == 404 -> "The server has no such endpoint. Check the project URL."
            else -> "The server answered $code."
        }
    }

    private fun isoNow(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date())

    private fun parseIso(value: String): Long? = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(value.take(19))
            ?.time
    }.getOrNull()

    private companion object {
        const val TAG = "AerixAccounts"
    }
}
