package net.kdt.aerix.desktop.core.auth

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.core.json.JsonObject
import net.kdt.aerix.desktop.core.net.Http
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Microsoft account authentication for Minecraft: Java Edition.
 *
 * This is the same chain the Android build runs in `MicrosoftBackgroundLogin`, with the browser
 * replaced by whichever mechanism the player can complete on a desktop:
 *
 * 1. **Device code** (preferred): `oauth20_connect.srf` returns a short code, the player enters it on
 *    `microsoft.com/link`, and we poll `oauth20_token.srf` until they finish. No redirect URI is
 *    needed, so any client id works.
 * 2. **Browser code**: the system browser opens the authorise URL; when the player lands on
 *    `login.live.com/oauth20_desktop.srf?code=…` they paste the address back and the code is
 *    exchanged. This is exactly the Android flow, with "paste the URL" standing in for the WebView.
 *
 * Then XBL → XSTS → `login_with_xbox` → `/minecraft/profile`, identical to the Android
 * implementation, including the XSTS error codes (no account, child account, region).
 */
object MicrosoftAuth {
    /** The public client id the Android launcher already uses for `login.live.com`. */
    const val CLIENT_ID = "00000000402b5328"

    private const val AUTHORIZE_URL = "https://login.live.com/oauth20_authorize.srf"
    private const val TOKEN_URL = "https://login.live.com/oauth20_token.srf"
    private const val DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf"
    private const val REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf"
    private const val SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"

    private const val XBL_URL = "https://user.auth.xboxlive.com/user/authenticate"
    private const val XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
    private const val MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox"
    private const val MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"
    private const val MC_ENTITLEMENTS_URL = "https://api.minecraftservices.com/entitlements/mcstore"

    class AuthException(message: String, val code: String? = null, val xerr: Long? = null) : RuntimeException(message)

    class Session(
        val username: String,
        val uuid: String,
        val accessToken: String,
        val xuid: String?,
        val expiresAt: Long,
        val refreshToken: String,
        val ownsGame: Boolean
    )

    class DeviceCode(
        val userCode: String,
        val verificationUri: String,
        val deviceCode: String,
        val intervalSeconds: Int,
        val expiresAt: Long
    )

    fun browserUrl(): String {
        val params = listOf(
            "client_id" to CLIENT_ID,
            "response_type" to "code",
            "scope" to SCOPE,
            "redirect_uri" to REDIRECT_URI
        ).joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, StandardCharsets.UTF_8)}" }
        return "$AUTHORIZE_URL?$params"
    }

    /** Starts the device-code flow. Returns null when Microsoft declines the request. */
    fun requestDeviceCode(): DeviceCode? {
        val response = Http.postForm(
            DEVICE_CODE_URL,
            listOf(
                "client_id" to CLIENT_ID,
                "scope" to SCOPE,
                "response_type" to "device_code"
            ).joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, StandardCharsets.UTF_8)}" }
        )
        if (response.statusCode() !in 200..299) {
            Log.w("msauth", "device code request failed with ${response.statusCode()}: ${RedactSafe(response.body())}")
            return null
        }
        val json = Json.parseObject(response.body())
        return DeviceCode(
            userCode = json.str("user_code") ?: return null,
            verificationUri = json.str("verification_uri") ?: "https://microsoft.com/link",
            deviceCode = json.str("device_code") ?: return null,
            intervalSeconds = (json.num("interval") ?: 5).toInt().coerceIn(2, 30),
            expiresAt = System.currentTimeMillis() + (json.num("expires_in") ?: 900) * 1000
        )
    }

    /**
     * Polls for the token until the player finishes (or [shouldCancel] asks to stop).
     * Microsoft answers `authorization_pending` while waiting and `slow_down` when polled too fast.
     */
    fun pollDeviceCode(deviceCode: DeviceCode, shouldCancel: () -> Boolean = { false }): Pair<String, String>? {
        var interval = deviceCode.intervalSeconds
        while (System.currentTimeMillis() < deviceCode.expiresAt) {
            if (shouldCancel()) return null
            Thread.sleep(interval * 1000L)
            val response = Http.postForm(
                TOKEN_URL,
                form("client_id" to CLIENT_ID, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code", "device_code" to deviceCode.deviceCode)
            )
            val body = response.body()
            if (response.statusCode() in 200..299) {
                val json = Json.parseObject(body)
                val refresh = json.str("refresh_token") ?: return null
                val access = json.str("access_token") ?: return null
                return refresh to access
            }
            val error = runCatching { Json.parseObject(body).str("error") }.getOrNull()
            when (error) {
                "authorization_pending" -> continue
                "slow_down" -> interval += 2
                "authorization_declined" -> throw AuthException("Sign-in was declined in the browser.", error)
                "expired_token", "code_expired" -> throw AuthException("The sign-in code expired. Start again.", error)
                else -> throw AuthException("Microsoft returned: ${error ?: "HTTP " + response.statusCode()}", error)
            }
        }
        throw AuthException("The sign-in code expired. Start again.")
    }

    /** Extracts `code=` from a pasted redirect URL (or accepts a bare code). */
    fun extractCode(pasted: String): String? {
        val text = pasted.trim()
        if (text.isEmpty()) return null
        val marker = Regex("[?&#]code=([^&#\\s]+)").find(text)
        if (marker != null) return java.net.URLDecoder.decode(marker.groupValues[1], StandardCharsets.UTF_8)
        if (text.startsWith("M.") || (text.length in 20..2000 && !text.contains(' ') && !text.contains("://"))) return text
        return null
    }

    /** The whole browser-code flow in one call: exchange the code, then the Xbox chain. */
    fun completeWithBrowserCode(code: String): Session {
        val (refreshToken, msToken) = exchangeCodeForRefreshToken(code)
        return completeSession(refreshToken, msToken)
    }

    /** The whole device-code flow in one call: request, poll until approved, then the Xbox chain. */
    fun completeWithDeviceCode(device: DeviceCode, shouldCancel: () -> Boolean = { false }): Session? {
        val pair = pollDeviceCode(device, shouldCancel) ?: return null
        return completeSession(pair.first, pair.second)
    }

    fun exchangeCodeForRefreshToken(code: String): Pair<String, String> {
        val response = Http.postForm(
            TOKEN_URL,
            form(
                "client_id" to CLIENT_ID,
                "code" to code,
                "grant_type" to "authorization_code",
                "redirect_uri" to REDIRECT_URI,
                "scope" to SCOPE
            )
        )
        if (response.statusCode() !in 200..299) {
            throw AuthException("Microsoft rejected the sign-in code (HTTP ${response.statusCode()}).")
        }
        val json = Json.parseObject(response.body())
        return (json.str("refresh_token") ?: throw AuthException("No refresh token in the Microsoft response.")) to
                (json.str("access_token") ?: throw AuthException("No access token in the Microsoft response."))
    }

    fun refresh(refreshToken: String): Pair<String, String> {
        val response = Http.postForm(
            TOKEN_URL,
            form("client_id" to CLIENT_ID, "refresh_token" to refreshToken, "grant_type" to "refresh_token", "scope" to SCOPE)
        )
        if (response.statusCode() !in 200..299) {
            throw AuthException("The Microsoft session expired; sign in again.")
        }
        val json = Json.parseObject(response.body())
        return (json.str("refresh_token") ?: refreshToken) to
                (json.str("access_token") ?: throw AuthException("No access token in the refresh response."))
    }

    /** Runs the Xbox → Minecraft half of the chain and returns a launch-ready session. */
    fun completeSession(refreshToken: String, msAccessToken: String): Session {
        val xbl = acquireXbl(msAccessToken)
        val (xuid, xsts) = acquireXsts(xbl)
        val mcToken = acquireMinecraftToken(xuid, xsts)
        val profile = profile(mcToken)
        val owns = entitlements(mcToken)
        return Session(
            username = profile.str("name") ?: "Player",
            uuid = profile.str("id") ?: "",
            accessToken = mcToken,
            xuid = xuid,
            expiresAt = System.currentTimeMillis() + 20L * 60 * 60 * 1000,
            refreshToken = refreshToken,
            ownsGame = owns
        )
    }

    private fun acquireXbl(msAccessToken: String): String {
        val payload = JsonObject()
            .put("Properties", JsonObject().put("AuthMethod", "RPS").put("SiteName", "user.auth.xboxlive.com").put("RpsTicket", "d=$msAccessToken"))
            .put("RelyingParty", "http://auth.xboxlive.com")
            .put("TokenType", "JWT")
        val response = Http.postJson(XBL_URL, payload.toString())
        if (response.statusCode() !in 200..299) throw AuthException("Xbox Live rejected the Microsoft token (HTTP ${response.statusCode()}).")
        return Json.parseObject(response.body()).str("Token") ?: throw AuthException("No Xbox Live token in the response.")
    }

    private fun acquireXsts(xblToken: String): Pair<String, String> {
        val payload = JsonObject()
            .put("Properties", JsonObject().put("SandboxId", "RETAIL").put("UserTokens", net.kdt.aerix.desktop.core.json.JsonArray().add(xblToken)))
            .put("RelyingParty", "rp://api.minecraftservices.com/")
            .put("TokenType", "JWT")
        val response = Http.postJson(XSTS_URL, payload.toString())
        val body = response.body() ?: ""
        if (response.statusCode() !in 200..299) {
            val xerr = runCatching { Json.parseObject(body).num("XErr") }.getOrNull()
            val message = when (xerr) {
                2148916233L -> "This Microsoft account has no Xbox profile. Create one at xbox.com, then try again."
                2148916235L -> "Xbox Live is not available in this account's country or region."
                2148916236L, 2148916237L -> "This account needs adult verification on Xbox.com before Minecraft can be played."
                2148916238L -> "This account is managed by a parent (child account). Add it to a Microsoft family first."
                else -> "Xbox Live refused the sign-in (XErr ${xerr ?: response.statusCode()})."
            }
            throw AuthException(message, xerr = xerr)
        }
        val json = Json.parseObject(body)
        val uhs = json.obj("DisplayClaims", "xui")?.get(0)?.str("uhs") ?: throw AuthException("No Xbox user hash in the response.")
        val token = json.str("Token") ?: throw AuthException("No XSTS token in the response.")
        return uhs to token
    }

    private fun acquireMinecraftToken(uhs: String, xsts: String): String {
        val payload = JsonObject().put("identityToken", "XBL3.0 x=$uhs;$xsts")
        val response = Http.postJson(MC_LOGIN_URL, payload.toString())
        if (response.statusCode() !in 200..299) throw AuthException("Minecraft services refused the Xbox ticket (HTTP ${response.statusCode()}).")
        return Json.parseObject(response.body()).str("access_token") ?: throw AuthException("No Minecraft access token in the response.")
    }

    fun profile(mcToken: String): JsonObject {
        val response = runCatching {
            Http.get(MC_PROFILE_URL, headers = mapOf("Authorization" to "Bearer $mcToken"))
        }.getOrElse {
            throw AuthException("This account does not own Minecraft: Java Edition, or the session is not valid.")
        }
        return Json.parseObject(response)
    }

    private fun entitlements(mcToken: String): Boolean = runCatching {
        val body = Http.get(MC_ENTITLEMENTS_URL, headers = mapOf("Authorization" to "Bearer $mcToken"))
        val json = Json.parseObject(body)
        json.arr("items")?.objects()?.any { it.str("name")?.startsWith("product_minecraft") == true } ?: false
    }.getOrDefault(false)

    /** Refreshes the access token for an existing account, keeping the new refresh token. */
    fun refreshSession(account: net.kdt.aerix.desktop.core.auth.accounts.Account): Session {
        val stored = account.refreshToken ?: throw AuthException("No stored session for this account.")
        val (newRefresh, msToken) = refresh(stored)
        return completeSession(newRefresh, msToken).also { account.refreshToken = it.refreshToken }
    }

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { "${URLEncoder.encode(it.first, StandardCharsets.UTF_8)}=${URLEncoder.encode(it.second, StandardCharsets.UTF_8)}" }

    private fun RedactSafe(body: String): String = body.take(300)

    /** Opens the given URL in the user's default browser, with a Windows fallback chain. */
    fun openBrowser(url: String): Boolean {
        runCatching {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url))
                return true
            }
        }
        return runCatching {
            val os = System.getProperty("os.name").lowercase()
            val command = when {
                os.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
                os.contains("mac") -> listOf("open", url)
                else -> listOf("xdg-open", url)
            }
            ProcessBuilder(command).start()
            true
        }.onFailure { Log.w("browser", "could not open $url: ${it.message}") }.getOrDefault(false)
    }
}
