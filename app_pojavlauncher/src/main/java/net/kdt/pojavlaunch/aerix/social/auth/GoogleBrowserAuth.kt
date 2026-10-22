/*
 * Aerix Launcher Social layer.
 *
 * Google sign-in through the system browser, for devices where the Play services account sheet
 * refuses the app (an unregistered signing key, a missing Android OAuth client, an old Play
 * services build).
 *
 * This is the standard OAuth 2.0 authorization-code flow with PKCE, done the way an installed
 * app is supposed to do it: the browser is opened on Google's own page — never an embedded
 * WebView, which Google blocks — and Google returns to a loopback address that this app is
 * listening on. No password passes through the launcher and there is no client secret to leak
 * when the client is a "Desktop app" client, which is what Google recommends for installed apps.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The browser half of Google sign-in.
 *
 * Google's rules for a loopback redirect: the URI must be `http://127.0.0.1:<port>` and the
 * request must come back to the same loopback address. A "Desktop app" OAuth client accepts any
 * port, so nothing has to be registered for it; a "Web application" client needs this exact
 * redirect URI added under Authorised redirect URIs.
 */
object GoogleBrowserAuth {

    private const val TAG = "AerixGoogleWeb"

    /** Loopback port this app listens on while the browser is open. */
    const val PORT = 53682

    /** How long to wait for Google to bounce the player back. */
    const val TIMEOUT_MS = 180_000L

    fun redirectUri(port: Int = PORT): String = "http://127.0.0.1:$port"

    fun isUsable(clientId: String): Boolean = GoogleAuth.isUsable(clientId)

    /**
     * Opens the browser, waits for Google's redirect, and turns the code into an ID token.
     * [clientSecret] is only needed for a "Web application" client; a Desktop client works
     * without one.
     */
    suspend fun signIn(
        context: Context,
        clientId: String,
        clientSecret: String = "",
        port: Int = PORT
    ): GoogleAuthResult {
        if (!isUsable(clientId)) return GoogleAuthResult.NotConfigured

        val verifier = randomToken(64)
        val challenge = sha256Url(verifier)
        val state = randomToken(16)
        val redirect = redirectUri(port)
        val url = buildString {
            append("https://accounts.google.com/o/oauth2/v2/auth")
            append("?client_id=").append(encode(clientId))
            append("&redirect_uri=").append(encode(redirect))
            append("&response_type=code")
            append("&scope=").append(encode("openid email profile"))
            append("&code_challenge=").append(encode(challenge))
            append("&code_challenge_method=S256")
            append("&state=").append(encode(state))
            // Always let the player pick which Google account, in case the browser is already
            // signed in to a different one.
            append("&prompt=select_account")
        }

        // Bind first: the browser can come back before the intent call even returns.
        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
                soTimeout = TIMEOUT_MS.toInt()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Could not listen on the loopback port", error)
            return GoogleAuthResult.Failed(
                "Port $port is busy, so Google cannot hand the answer back to the launcher. " +
                    "Close what is using it and try again. (${error.message ?: "bind failed"})"
            )
        }

        return try {
            withContext(Dispatchers.Main.immediate) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            val redirectResult = withContext(Dispatchers.IO) { awaitRedirect(server, state) }
            when (redirectResult) {
                is RedirectResult.Error -> GoogleAuthResult.Failed(redirectResult.message)
                is RedirectResult.Code -> {
                    val account = withContext(Dispatchers.IO) {
                        exchange(
                            code = redirectResult.code,
                            verifier = verifier,
                            redirect = redirect,
                            clientId = clientId,
                            clientSecret = clientSecret
                        )
                    }
                    account
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Browser sign-in failed", error)
            GoogleAuthResult.Failed(error.message ?: "The browser sign-in failed.")
        } finally {
            runCatching { server.close() }
        }
    }

    // ---------------------------------------------------------------- the loopback listener

    private sealed class RedirectResult {
        data class Code(val code: String) : RedirectResult()
        data class Error(val message: String) : RedirectResult()
    }

    /** Waits for Google's browser redirect and answers it with a small page of its own. */
    private fun awaitRedirect(server: ServerSocket, expectedState: String): RedirectResult {
        while (true) {
            val socket = try {
                server.accept()
            } catch (timeout: java.net.SocketTimeoutException) {
                return RedirectResult.Error(
                    "Google did not come back to the launcher within " +
                        "${TIMEOUT_MS / 1000} seconds. Finish the sign-in in the browser, then " +
                        "tap the button again."
                )
            } catch (error: Throwable) {
                return RedirectResult.Error(error.message ?: "The loopback listener closed.")
            }

            val outcome = handleConnection(socket, expectedState)
            runCatching { socket.close() }
            if (outcome != null) return outcome
            // Otherwise it was a browser probe (favicon, preconnect): keep listening.
        }
    }

    /** Returns a result when this request finishes the flow, or null for a harmless probe. */
    private fun handleConnection(socket: java.net.Socket, expectedState: String): RedirectResult? {
        val requestLine = try {
            socket.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val line = reader.readLine().orEmpty()
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
            }
            line
        } catch (error: Throwable) {
            Log.w(TAG, "Could not read the loopback request", error)
            return null
        }

        val target = requestLine.split(' ').getOrNull(1).orEmpty()
        val params = runCatching { Uri.parse(target) }.getOrNull()
        val state = params?.getQueryParameter("state")
        if (state == null) {
            // A browser probe, not Google's redirect.
            respond(socket, 200, page("Waiting for Google…"))
            return null
        }

        val code = params.getQueryParameter("code").orEmpty()
        val error = params.getQueryParameter("error").orEmpty()
        if (error.isNotBlank()) {
            val description = params.getQueryParameter("error_description").orEmpty().trim()
            respond(socket, 200, page("Google did not finish the sign-in", "$error $description".trim()))
            return RedirectResult.Error(
                when (error) {
                    "access_denied" -> "Sign-in was cancelled in the browser."
                    "invalid_client" -> "Google does not recognise this client id. Check the id " +
                        "and the redirect URI in Google Cloud Console."
                    "redirect_uri_mismatch" -> "Add " + redirectUri() + " to the client's " +
                        "Authorised redirect URIs in Google Cloud Console, exactly as written."
                    else -> "Google refused the sign-in: $error $description".trim()
                }
            )
        }
        if (state != expectedState) {
            respond(socket, 200, page("Signed in", "You can close this tab."))
            return RedirectResult.Error(
                "The browser came back from a different sign-in attempt, so it was ignored for " +
                    "safety. Try again."
            )
        }
        if (code.isBlank()) {
            respond(socket, 200, page("Waiting for Google…"))
            return null
        }
        respond(socket, 200, page("Aerix is signing you in", "You can close this tab."))
        return RedirectResult.Code(code)
    }

    private fun respond(connection: java.net.Socket, status: Int, body: String) {
        runCatching {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 $status OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
            val out = connection.getOutputStream()
            out.write(header.toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
        }
    }

    private fun page(title: String, line: String = "You can close this tab."): String = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>$title</title></head>
        <body style="margin:0;background:#07131D;color:#F1F6FA;font-family:sans-serif;
                     display:flex;min-height:100vh;align-items:center;justify-content:center">
          <div style="text-align:center;padding:28px">
            <div style="color:#8DEFE0;font-size:20px;margin-bottom:10px">$title</div>
            <div style="color:#A8B6C3;font-size:14px">$line</div>
          </div>
        </body></html>
    """.trimIndent()

    // ---------------------------------------------------------------- the token exchange

    /** Trades the one-time code for an ID token, then reads the account out of it. */
    private fun exchange(
        code: String,
        verifier: String,
        redirect: String,
        clientId: String,
        clientSecret: String
    ): GoogleAuthResult {
        val fields = linkedMapOf(
            "code" to code,
            "client_id" to clientId,
            "redirect_uri" to redirect,
            "grant_type" to "authorization_code",
            "code_verifier" to verifier
        )
        if (clientSecret.isNotBlank()) fields["client_secret"] = clientSecret
        val body = fields.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

        val connection = (URL("https://oauth2.googleapis.com/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        val payload = try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
            val text = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            JSONObject(text.ifBlank { "{}" })
        } catch (error: Throwable) {
            Log.e(TAG, "Token exchange failed", error)
            return GoogleAuthResult.Failed("Could not reach Google to finish signing in: ${error.message}")
        } finally {
            runCatching { connection.disconnect() }
        }

        payload.optString("error").takeIf { it.isNotBlank() }?.let { error ->
            val description = payload.optString("error_description")
            Log.e(TAG, "Token exchange refused: $error $description")
            return GoogleAuthResult.Failed(
                when (error) {
                    "invalid_grant" -> "Google rejected the one-time code (it expires in about a " +
                        "minute). Tap the button and finish in the browser without pausing."
                    "invalid_client" -> "Google does not recognise this client. If this client is " +
                        "a Web application type, paste its client secret too; a Desktop app client " +
                        "needs neither."
                    "redirect_uri_mismatch" -> "Add $redirect to the client's Authorised redirect " +
                        "URIs in Google Cloud Console, exactly as written."
                    else -> "Google refused the token request: $error ${description.trim()}".trim()
                }
            )
        }

        val idToken = payload.optString("id_token")
        val claims = GoogleIdToken.parseClaims(idToken)
        var email = claims?.email.orEmpty()
        var name = claims?.name.orEmpty()

        if (email.isBlank()) {
            // No openid claim in hand: ask the userinfo endpoint, which the access token opens.
            val accessToken = payload.optString("access_token")
            if (accessToken.isNotBlank()) {
                val info = userInfo(accessToken)
                email = info?.optString("email").orEmpty()
                name = name.ifBlank { info?.optString("name").orEmpty() }
            }
        }

        if (email.isBlank()) {
            return GoogleAuthResult.Failed(
                "Google signed in but returned no account address, which the launcher needs to " +
                    "link your profile. Make sure the 'email' and 'profile' scopes are allowed on " +
                    "the OAuth consent screen."
            )
        }
        val display = name.ifBlank { email.substringBefore('@') }
        return GoogleAuthResult.Success(
            GoogleAccount(email = email, displayName = display, idToken = idToken)
        )
    }

    private fun userInfo(accessToken: String): JSONObject? = try {
        val connection = (URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val text = (if (connection.responseCode in 200..299) connection.inputStream else null)
            ?.bufferedReader()?.use { it.readText() }
        connection.disconnect()
        text?.let { JSONObject(it) }
    } catch (error: Throwable) {
        Log.w(TAG, "userinfo lookup failed", error)
        null
    }

    // ---------------------------------------------------------------- small helpers

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun randomToken(bytes: Int): String {
        val buffer = ByteArray(bytes)
        SecureRandom().nextBytes(buffer)
        return android.util.Base64.encodeToString(
            buffer,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
    }

    private fun sha256Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
    }
}
