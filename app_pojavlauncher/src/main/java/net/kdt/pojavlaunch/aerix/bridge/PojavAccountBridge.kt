/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: Pojav accounts (net.kdt.pojavlaunch.authenticator.*) exposed to the Aerix Accounts / Home UI,
 * plus the Account Details & Skin Wardrobe (equip .png skin/cape from internal storage, Wide/Slim model
 * toggle, and Saved Skins & Presets library in <DIR_GAME_HOME>/skins).
 * Takes over the login listeners that com.kdt.mcgui.AccountSpinner registered, with identical behaviour.
 * Replaces the UI-facing parts of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/game/account/AccountsManager.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/viewmodel/AccountViewModel.kt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.authenticator.AuthType
import net.kdt.pojavlaunch.authenticator.accounts.Account
import net.kdt.pojavlaunch.authenticator.accounts.Accounts
import net.kdt.pojavlaunch.authenticator.accounts.SkinHeadRenderer
import net.kdt.pojavlaunch.authenticator.listener.LoginListener
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.extra.ExtraListener
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** One row of the Aerix account list. [source] is the untouched Pojav Account. */
class AerixAccount(
    val source: Account,
    val username: String,
    val authType: AuthType,
    val expired: Boolean,
    val face: ImageBitmap?
) {
    val key: String
        get() = PojavAccountBridge.accountKey(source)

    val displayUuid: String
        get() = PojavAccountBridge.effectiveProfileId(source)
}

/** One entry in the Saved Skins & Presets grid of the Account Details & Skin Wardrobe page. */
class AerixSkinPreset(
    val id: String,
    val name: String,
    val file: File?,
    val preview: ImageBitmap?
)

object PojavAccountBridge : LoginListener {
    private const val WARDROBE_PREFS = "aerix_skin_wardrobe"

    var accounts by mutableStateOf<List<AerixAccount>>(emptyList())
        private set
    var selectedIndex by mutableIntStateOf(-1)
        private set
    /** Key of the account currently opened in the Account Details & Skin Wardrobe page, or null for the list view. */
    var focusedAccountKey by mutableStateOf<String?>(null)
    /** Bumped whenever an account's skin, cape, or Wide/Slim model changes. */
    var wardrobeRevision by mutableIntStateOf(0)
        private set
    /** 0..1 login progress, shown as the bar AccountSpinner used to draw. */
    var loginProgress by mutableFloatStateOf(1f)
        private set
    var loginError by mutableStateOf(false)
        private set

    val current: AerixAccount?
        get() = accounts.getOrNull(selectedIndex)

    /** True while [ensureLoaded] is reading the accounts, so it only reads them once. */
    private var loadingAccounts = false
    private var maxSteps = 5
    private var attached = false
    private var appContext: Context? = null

    private class LoginExtraListener(private val authType: AuthType) : ExtraListener<String> {
        override fun onValueSet(key: String, value: String): Boolean {
            loginError = false
            loginProgress = 0f
            authType.createAuth().createAccount(PojavAccountBridge, value)
            return false
        }
    }

    // All listeners are held strongly here: ExtraCore keeps only weak references.
    private val microsoftLoginListener = LoginExtraListener(AuthType.MICROSOFT)
    private val elyByLoginListener = LoginExtraListener(AuthType.ELY_BY)
    private val localLoginListener = ExtraListener<Array<String>> { _, value ->
        try {
            val account = Accounts.create { acc ->
                val name = value[0].trim().ifEmpty { "Steve" }
                acc.username = name
                acc.authType = AuthType.LOCAL
                acc.profileId = offlineUuid(name).toString()
                acc.accessToken = offlineAccessToken(name)
            }
            onLoginDone(account)
        } catch (e: IOException) {
            onLoginError(e)
        }
        false
    }

    /** Standard Minecraft v3 offline UUID (`UUIDUtil.createOfflinePlayerUUID`). */
    @JvmStatic
    fun offlineUuid(username: String): UUID {
        val clean = username.trim().ifEmpty { "Steve" }
        return UUID.nameUUIDFromBytes("OfflinePlayer:$clean".toByteArray(Charsets.UTF_8))
    }

    /** Deterministic non-zero 32-hex session token so MC 1.20.4+ does not enter `"0"` offline dev mode. */
    @JvmStatic
    fun offlineAccessToken(username: String): String =
        UUID.nameUUIDFromBytes("AerixOfflineToken:${username.trim().ifEmpty { "Steve" }}".toByteArray(Charsets.UTF_8))
            .toString()
            .replace("-", "")

    /**
     * Resolves a valid non-nil UUID for [account]. Existing offline accounts created before this
     * fix carry `00000000-0000-0000-0000-000000000000` (`Util.NIL_UUID`), which Minecraft 1.20.2+
     * and 1.21+ treat as an absent profile and replace with `OfflinePlayer:<username>` in worlds.
     */
    @JvmStatic
    fun effectiveProfileId(account: Account): String {
        val raw = account.profileId?.trim().orEmpty()
        val stripped = raw.replace("-", "")
        val isNilOrInvalid = stripped.length != 32 || stripped.all { it == '0' }
        return if (account.authType == AuthType.LOCAL || isNilOrInvalid) {
            offlineUuid(account.username ?: "Steve").toString()
        } else {
            raw
        }
    }

    /**
     * Resolves a non-`"0"` access token for [account] so Minecraft 1.20.4+ / 1.21+ does not
     * short-circuit session/skin loading via `"0".equals(accessToken)`.
     */
    @JvmStatic
    fun effectiveAccessToken(account: Account): String {
        val raw = account.accessToken?.trim().orEmpty()
        return if (account.authType == AuthType.LOCAL || raw.isEmpty() || raw == "0") {
            offlineAccessToken(account.username ?: "Steve")
        } else {
            raw
        }
    }
    private val refreshAccountsListener = ExtraListener<Boolean> { _, _ ->
        reload()
        false
    }

    fun attach(context: Context) {
        appContext = context.applicationContext
        if (attached) return
        attached = true
        ExtraCore.addExtraListener(ExtraConstants.MOJANG_LOGIN_TODO, localLoginListener)
        ExtraCore.addExtraListener(ExtraConstants.MICROSOFT_LOGIN_TODO, microsoftLoginListener)
        ExtraCore.addExtraListener(ExtraConstants.ELYBY_LOGIN_TODO, elyByLoginListener)
        ExtraCore.addExtraListener(ExtraConstants.REFRESH_ACCOUNT_SPINNER, refreshAccountsListener)
    }

    fun accountKey(account: Account): String =
        account.mSaveLocation?.name ?: "${account.authType?.name ?: "LOCAL"}:${account.username}"

    fun openDetail(account: AerixAccount) {
        select(account)
        focusedAccountKey = account.key
    }

    fun closeDetail() {
        focusedAccountKey = null
    }

    /**
     * Loads the saved accounts once, for screens that only need to read them - the in-game menu,
     * which shows who the game is running as. Unlike [reload] this never touches the network: it is
     * called while the game is running and must stay cheap.
     */
    fun ensureLoaded() {
        if (accounts.isNotEmpty() || loadingAccounts) return
        loadingAccounts = true
        val ctx = appContext
        PojavApplication.sExecutorService.execute {
            val rows = runCatching { Accounts.load() }.getOrNull()?.let { result ->
                val now = System.currentTimeMillis()
                result.accounts.map { account ->
                    val faceBitmap = resolveAccountFaceBitmap(ctx, account)
                    AerixAccount(
                        source = account,
                        username = account.username ?: "",
                        authType = account.authType ?: AuthType.LOCAL,
                        expired = (account.authType ?: AuthType.LOCAL).requiresLogin() && now > account.expiresAt,
                        face = runCatching { faceBitmap?.asImageBitmap() }.getOrNull()
                    )
                } to result.selectionIndex
            }
            Tools.runOnUiThread {
                // Always released, so a failure does not stop the next attempt.
                loadingAccounts = false
                if (rows != null) {
                    accounts = rows.first
                    selectedIndex = rows.second
                }
            }
        }
    }

    /** Same flow as AccountSpinner.reload(): load on the executor, then refresh the selected account. */
    fun reload() {
        val ctx = appContext
        PojavApplication.sExecutorService.execute {
            val loaded = runCatching { Accounts.load() }
            val rows = loaded.getOrNull()?.let { result ->
                val now = System.currentTimeMillis()
                result.accounts.map { account ->
                    val faceBitmap = resolveAccountFaceBitmap(ctx, account)
                    AerixAccount(
                        source = account,
                        username = account.username ?: "",
                        authType = account.authType ?: AuthType.LOCAL,
                        expired = (account.authType ?: AuthType.LOCAL).requiresLogin() && now > account.expiresAt,
                        face = runCatching { faceBitmap?.asImageBitmap() }.getOrNull()
                    )
                } to (if (result.accounts.isEmpty()) -1 else result.selectionIndex)
            }
            Tools.runOnUiThread {
                loaded.onSuccess { result ->
                    if (rows != null) {
                        accounts = rows.first
                        selectedIndex = rows.second
                        if (focusedAccountKey != null && rows.first.none { it.key == focusedAccountKey }) {
                            focusedAccountKey = rows.first.getOrNull(rows.second)?.key
                        }
                    }
                    result.accounts.getOrNull(result.selectionIndex)?.let { refreshAccount(it) }
                }.onFailure { Tools.showErrorRemote(it) }
            }
        }
    }

    private fun refreshAccount(account: Account) {
        // Wait until all tasks (including other login tasks) are done before refreshing, like AccountSpinner.
        ProgressKeeper.waitUntilDone {
            val refreshed = account.reload() ?: return@waitUntilDone
            val authType = refreshed.authType
            if (authType.requiresLogin() && System.currentTimeMillis() > refreshed.expiresAt) {
                authType.createAuth().refreshAccount(this, refreshed)
            }
        }
    }

    fun select(account: AerixAccount) {
        Accounts.setCurrent(account.source)
        selectedIndex = accounts.indexOf(account)
        refreshAccount(account.source)
    }

    fun forceRefresh(account: AerixAccount) {
        val authType = account.authType
        if (!authType.requiresLogin()) return
        loginError = false
        loginProgress = 0f
        ProgressKeeper.waitUntilDone {
            val refreshed = account.source.reload() ?: return@waitUntilDone
            refreshed.authType.createAuth().refreshAccount(this, refreshed)
        }
    }

    fun delete(account: AerixAccount) {
        if (focusedAccountKey == account.key) {
            focusedAccountKey = null
        }
        customSkinFile(account.source).delete()
        customCapeFile(account.source).delete()
        customFaceFile(account.source).delete()
        File(Tools.DIR_CACHE, "aerix-skin-${safeAccountTag(account.source)}.png").delete()
        Accounts.delete(account.source)
        reload()
    }

    /** Opens Pojav's SelectAuthFragment through the activity listener, as the spinner's "add" row did. */
    fun requestAddAccount() {
        ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true)
    }

    /** Creates an offline account directly from the Account Manage screen (+ Offline pill). */
    fun createOfflineAccount(username: String) {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) return
        ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, arrayOf(trimmed, ""))
    }

    /** Downloads a skin PNG from the first working URL in [candidateUrls], saves it to skins/, and equips it. */
    fun equipOnlineSkin(
        context: Context,
        account: AerixAccount,
        skinName: String,
        candidateUrls: List<String>,
        isSlim: Boolean,
        onDone: (Boolean) -> Unit = {}
    ) {
        val appCtx = context.applicationContext
        PojavApplication.sExecutorService.execute {
            val safeStem = skinName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Skin" }
            val cachedFile = File(skinsDirectory(), "$safeStem.png")
            var resolvedSlim = isSlim
            val ok = runCatching {
                var normalized: Pair<ByteArray, Bitmap>? = null
                val urlsToTry = ArrayList<String>(candidateUrls)
                // Official Mojang texture fallback when searching by Minecraft player name.
                if (skinName.matches(Regex("^[A-Za-z0-9_]{2,16}$"))) {
                    resolveMojangSkinUrl(skinName)?.let { (mojangUrl, mojangSlim) ->
                        urlsToTry.add(0, mojangUrl)
                        resolvedSlim = mojangSlim
                    }
                }
                for (url in urlsToTry.distinct()) {
                    val raw = downloadBytesFollowingRedirects(url) ?: continue
                    val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: continue
                    val valid = normalizeSkinPng(decoded)
                    decoded.recycle()
                    if (valid != null) {
                        normalized = valid
                        break
                    }
                }
                if (normalized == null && cachedFile.isFile && cachedFile.length() > 0) {
                    val raw = cachedFile.readBytes()
                    val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                    if (decoded != null) {
                        normalized = normalizeSkinPng(decoded)
                        decoded.recycle()
                    }
                }
                val (finalBytes, cleanBmp) = normalized ?: return@runCatching false
                cachedFile.parentFile?.mkdirs()
                cachedFile.writeBytes(finalBytes)
                applySkinBytesToAccount(appCtx, account.source, finalBytes, cleanBmp, safeStem)
                cleanBmp.recycle()
                if (Accounts.getCurrent() == null || selectedIndex < 0) {
                    Accounts.setCurrent(account.source)
                }
                true
            }.getOrDefault(false)
            Tools.runOnUiThread {
                if (ok) {
                    if (selectedIndex < 0 && accounts.contains(account)) {
                        selectedIndex = accounts.indexOf(account)
                    }
                    setSlimModel(appCtx, account, resolvedSlim)
                    wardrobeRevision++
                    reload()
                    Toast.makeText(appCtx, "Equipped skin: $skinName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appCtx, "Unable to download skin: $skinName", Toast.LENGTH_SHORT).show()
                }
                onDone(ok)
            }
        }
    }

    private fun downloadBytesFollowingRedirects(initialUrl: String, maxHops: Int = 5): ByteArray? {
        var currentUrl = initialUrl
        repeat(maxHops) {
            val conn = (java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 9000
                readTimeout = 9000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "AerixLauncher/1.6")
                setRequestProperty("Accept", "image/png,image/*;q=0.9,*/*;q=0.5")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return null
                    currentUrl = java.net.URL(java.net.URL(currentUrl), loc).toString()
                    return@repeat
                }
                if (code in 200..299) {
                    return conn.inputStream.use { it.readBytes() }.takeIf { it.isNotEmpty() }
                }
                return null
            } catch (_: Throwable) {
                return null
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun resolveMojangSkinByUuid(uuidRaw: String): Pair<String, Boolean>? = runCatching {
        val uuid = uuidRaw.replace("-", "").trim()
        if (uuid.length != 32 || uuid.all { it == '0' }) return null
        val sessionBytes = downloadBytesFollowingRedirects("https://sessionserver.mojang.com/session/minecraft/profile/$uuid")
            ?: return null
        val props = com.google.gson.JsonParser.parseString(String(sessionBytes, Charsets.UTF_8))
            .asJsonObject.getAsJsonArray("properties") ?: return null
        for (el in props) {
            val obj = el.asJsonObject
            if (obj.get("name")?.asString == "textures") {
                val b64 = obj.get("value")?.asString ?: continue
                val texJson = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                val skinObj = com.google.gson.JsonParser.parseString(texJson)
                    .asJsonObject.getAsJsonObject("textures")?.getAsJsonObject("SKIN") ?: continue
                val url = skinObj.get("url")?.asString ?: continue
                val slim = skinObj.getAsJsonObject("metadata")?.get("model")?.asString == "slim"
                return url to slim
            }
        }
        null
    }.getOrNull()

    private fun resolveMojangSkinUrl(username: String): Pair<String, Boolean>? = runCatching {
        val profileBytes = downloadBytesFollowingRedirects("https://api.mojang.com/users/profiles/minecraft/$username")
            ?: return null
        val uuid = com.google.gson.JsonParser.parseString(String(profileBytes, Charsets.UTF_8))
            .asJsonObject.get("id")?.asString ?: return null
        resolveMojangSkinByUuid(uuid)
    }.getOrNull()

    /**
     * Validates and normalizes any decoded skin bitmap into a clean 64x64 (or legacy 64x32)
     * ARGB_8888 PNG. Vanilla Minecraft 1.20.2+ / 1.21+ (`SkinTextureDownloader.processLegacySkin`)
     * strictly enforces `width == 64 && (height == 32 || height == 64)` and discards 128x128+
     * textures completely.
     */
    private fun normalizeSkinPng(source: Bitmap): Pair<ByteArray, Bitmap>? {
        val w = source.width
        val h = source.height
        if (w < 32 || h < 16) return null
        // Reject non-skin aspect ratios (such as 100x100 avatars or error banners).
        val isSquare = w == h
        val isLegacyHalf = w == h * 2
        if (!isSquare && !isLegacyHalf) return null

        val targetW = 64
        val targetH = if (isLegacyHalf) 32 else 64
        val clean = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(clean)
        val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        canvas.drawBitmap(source, Rect(0, 0, w, h), Rect(0, 0, targetW, targetH), paint)
        val out = java.io.ByteArrayOutputStream()
        if (!clean.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            clean.recycle()
            return null
        }
        return out.toByteArray() to clean
    }

    /**
     * Ensures already-saved skin files on disk (including HD 128x128/256x256 skins equipped in
     * earlier builds) are converted to 64x64 / 64x32 PNGs before [AerixSkinServer] serves them to
     * Minecraft 1.20.2+ / 1.21+.
     */
    @JvmStatic
    fun normalizeSkinBytesForGame(rawBytes: ByteArray): ByteArray {
        return runCatching {
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return rawBytes
            if (decoded.width == 64 && (decoded.height == 64 || decoded.height == 32)) {
                decoded.recycle()
                return rawBytes
            }
            val normalized = normalizeSkinPng(decoded)
            decoded.recycle()
            if (normalized != null) {
                normalized.second.recycle()
                normalized.first
            } else {
                rawBytes
            }
        }.getOrDefault(rawBytes)
    }

    /** Loads or downloads a 2D pixel face preview for a SkinLibrary entry. */
    fun loadSkinFacePreview(
        cacheKey: String,
        localFile: File?,
        avatarUrl: String?
    ): ImageBitmap? {
        if (localFile != null && localFile.isFile && localFile.length() > 0) {
            runCatching {
                val decoded = BitmapFactory.decodeFile(localFile.absolutePath)
                if (decoded != null) {
                    val face = (renderSkinFace(decoded) ?: decoded).asImageBitmap()
                    if (decoded.width > 16) decoded.recycle()
                    return face
                }
            }
        }
        val safeKey = cacheKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val cacheFile = File(Tools.DIR_CACHE, "aerix-skinlib-face-$safeKey.png")
        if (cacheFile.isFile && cacheFile.length() > 0) {
            runCatching { BitmapFactory.decodeFile(cacheFile.absolutePath)?.asImageBitmap() }
                .getOrNull()
                ?.let { return it }
        }
        if (!avatarUrl.isNullOrBlank()) {
            val bytes = runCatching {
                val conn = (java.net.URL(avatarUrl).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "AerixLauncher/1.6")
                }
                try {
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.use { it.readBytes() }
                    } else null
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (decoded != null) {
                    val faceBmp = if (decoded.width >= 64 && decoded.height >= 32 && decoded.width != decoded.height) {
                        renderSkinFace(decoded) ?: decoded
                    } else {
                        decoded
                    }
                    runCatching {
                        FileOutputStream(cacheFile).use { out ->
                            faceBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                    return faceBmp.asImageBitmap()
                }
            }
        }
        return null
    }

    override fun onLoginDone(account: Account) {
        Tools.runOnUiThread {
            loginProgress = 1f
            loginError = false
            appContext?.let { Toast.makeText(it, R.string.main_login_done, Toast.LENGTH_SHORT).show() }
            Accounts.setCurrent(account)
            focusedAccountKey = null
            reload()
        }
    }

    override fun onLoginError(errorMessage: Throwable) {
        Tools.runOnUiThread {
            loginError = true
            loginProgress = 1f
        }
        Tools.showErrorRemote(errorMessage)
    }

    override fun onLoginProgress(step: Int) {
        Tools.runOnUiThread { loginProgress = (step.toFloat() / maxSteps.coerceAtLeast(1)).coerceIn(0f, 1f) }
    }

    override fun setMaxLoginProgress(max: Int) {
        maxSteps = max
    }

    // ---- Skin & Cape Wardrobe ----------------------------------------------------------------

    fun skinsDirectory(): File {
        val dir = File(Tools.DIR_GAME_HOME, "skins")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeAccountTag(account: Account): String {
        val raw = "${account.authType?.name ?: "LOCAL"}_${account.username ?: "Steve"}"
        return raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    /**
     * Where the equipped skin, cape and face of every account are kept.
     *
     * These used to live in the cache directory, which the system is free to empty whenever it
     * wants space: an equipped skin could then disappear between the wardrobe and the launch, and
     * the game showed the default one with nothing in the launcher explaining why. Application
     * data is only removed by the user.
     */
    private fun wardrobeDirectory(): File {
        val dir = File(Tools.DIR_DATA, "aerix/wardrobe")
        if (!dir.isDirectory) dir.mkdirs()
        return dir
    }

    /** Moves an equipped file out of the cache directory, once, keeping its contents. */
    private fun persistentWardrobeFile(account: Account, name: String, legacyName: String): File {
        val target = File(wardrobeDirectory(), name)
        if (target.isFile) return target
        val legacy = File(Tools.DIR_CACHE, legacyName)
        if (legacy.isFile) {
            runCatching {
                target.parentFile?.mkdirs()
                legacy.copyTo(target, overwrite = true)
                legacy.delete()
            }
        }
        return target
    }

    fun customSkinFile(account: Account): File = persistentWardrobeFile(
        account,
        "skin-${safeAccountTag(account)}.png",
        "aerix-custom-skin-${safeAccountTag(account)}.png"
    )

    fun customCapeFile(account: Account): File = persistentWardrobeFile(
        account,
        "cape-${safeAccountTag(account)}.png",
        "aerix-custom-cape-${safeAccountTag(account)}.png"
    )

    private fun customFaceFile(account: Account): File = persistentWardrobeFile(
        account,
        "face-${safeAccountTag(account)}.png",
        "aerix-custom-face-${safeAccountTag(account)}.png"
    )

    fun resolveCustomSkinBytes(account: AerixAccount?): ByteArray? {
        if (account == null) return null
        val file = customSkinFile(account.source)
        if (file.isFile && file.length() > 0) {
            return runCatching { file.readBytes() }.getOrNull()
        }
        return null
    }

    /** Resolves a wardrobe cape or the official Microsoft account cape. Call off the main thread. */
    fun resolveCapeBytes(account: AerixAccount?, includeOfficial: Boolean = false): ByteArray? {
        if (account == null) return null
        val file = customCapeFile(account.source)
        if (file.isFile && file.length() > 0) return runCatching { file.readBytes() }.getOrNull()
        if (!includeOfficial || account.authType != AuthType.MICROSOFT) return null

        val uuid = account.source.profileId?.replace("-", "")?.trim().orEmpty()
        if (uuid.length != 32 || uuid.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' } || uuid.all { it == '0' }) return null
        val cached = File(Tools.DIR_CACHE, "aerix-cape-${safeAccountTag(account.source)}.png")
        val cachedBytes = { runCatching { cached.takeIf { it.isFile && it.length() > 0 }?.readBytes() }.getOrNull() }
        if (cached.isFile && System.currentTimeMillis() - cached.lastModified() < 15 * 60 * 1000L) return cachedBytes()
        val profile = downloadBytesFollowingRedirects("https://sessionserver.mojang.com/session/minecraft/profile/$uuid")
            ?: return cachedBytes()
        return runCatching {
            val properties = com.google.gson.JsonParser.parseString(String(profile, Charsets.UTF_8))
                .asJsonObject.getAsJsonArray("properties") ?: return@runCatching cachedBytes()
            val property = properties.firstOrNull { it.asJsonObject.get("name")?.asString == "textures" }
                ?: return@runCatching cachedBytes()
            val encoded = property.asJsonObject.get("value")?.asString ?: return@runCatching cachedBytes()
            val textures = com.google.gson.JsonParser.parseString(
                String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
            ).asJsonObject.getAsJsonObject("textures")
            val url = textures?.getAsJsonObject("CAPE")?.get("url")?.asString
            if (url.isNullOrBlank()) {
                cached.delete()
                return@runCatching null
            }
            val parsed = java.net.URI(url)
            if (parsed.host != "textures.minecraft.net" || parsed.scheme !in listOf("http", "https")) return@runCatching null
            val secureUrl = if (parsed.scheme == "http") "https://" + url.removePrefix("http://") else url
            val bytes = downloadBytesFollowingRedirects(secureUrl) ?: return@runCatching cachedBytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching cachedBytes()
            val valid = bitmap.width >= 22 && bitmap.height >= 17 && bitmap.width <= 1024 && bitmap.height <= 1024
            bitmap.recycle()
            if (!valid) return@runCatching cachedBytes()
            runCatching { cached.writeBytes(bytes) }
            bytes
        }.getOrElse { cachedBytes() }
    }

    fun isSlimModel(account: AerixAccount?): Boolean {
        if (account == null) return false
        val ctx = appContext ?: return false
        val prefs = ctx.getSharedPreferences(WARDROBE_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean("slim_${safeAccountTag(account.source)}", false)
    }

    /** Wide/Slim choice of [account], as the game has to be told it (see [AerixSkinServer]). */
    fun isSlimModelFor(context: Context, account: Account): Boolean =
        context.applicationContext
            .getSharedPreferences(WARDROBE_PREFS, Context.MODE_PRIVATE)
            .getBoolean("slim_${safeAccountTag(account)}", false)

    fun setSlimModel(context: Context, account: AerixAccount, slim: Boolean) {
        context.applicationContext
            .getSharedPreferences(WARDROBE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("slim_${safeAccountTag(account.source)}", slim)
            .apply()
        wardrobeRevision++
    }

    fun activePresetId(context: Context, account: AerixAccount?): String {
        if (account == null) return "default"
        val prefs = context.applicationContext.getSharedPreferences(WARDROBE_PREFS, Context.MODE_PRIVATE)
        return prefs.getString("preset_${safeAccountTag(account.source)}", "default") ?: "default"
    }

    private fun saveActivePresetId(context: Context, account: Account, presetId: String) {
        context.applicationContext
            .getSharedPreferences(WARDROBE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("preset_${safeAccountTag(account)}", presetId)
            .apply()
    }

    private fun resolveAccountFaceBitmap(context: Context?, account: Account): Bitmap? {
        val customFace = customFaceFile(account)
        if (customFace.isFile && customFace.length() > 0) {
            runCatching { BitmapFactory.decodeFile(customFace.absolutePath) }
                .getOrNull()
                ?.let { return it }
        }
        val customSkin = customSkinFile(account)
        if (customSkin.isFile && customSkin.length() > 0) {
            runCatching {
                val decoded = BitmapFactory.decodeFile(customSkin.absolutePath)
                if (decoded != null) {
                    val face = renderSkinFace(decoded)
                    decoded.recycle()
                    face
                } else null
            }.getOrNull()?.let { return it }
        }
        runCatching { account.skinFace }.getOrNull()?.let { return it }
        if (context != null) {
            return runCatching {
                context.assets.open("steve.png").use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream) ?: return@use null
                    val face = renderSkinFace(decoded)
                    decoded.recycle()
                    face
                }
            }.getOrNull()
        }
        return null
    }

    fun equipSkinFromUri(
        context: Context,
        account: AerixAccount,
        uri: Uri,
        onDone: (Boolean) -> Unit = {}
    ) {
        val appCtx = context.applicationContext
        PojavApplication.sExecutorService.execute {
            val result = runCatching {
                val rawBytes = appCtx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@runCatching false
                val rawDecoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    ?: return@runCatching false
                val normalized = normalizeSkinPng(rawDecoded)
                val (bytes, decoded) = if (normalized != null) {
                    rawDecoded.recycle()
                    normalized
                } else {
                    rawBytes to rawDecoded
                }
                val displayName = queryDisplayName(appCtx, uri)
                    ?.removeSuffix(".png")
                    ?.replace(Regex("[^A-Za-z0-9 _-]"), "")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "Skin_${System.currentTimeMillis() % 10000}"
                val savedFile = File(skinsDirectory(), "$displayName.png")
                savedFile.writeBytes(bytes)
                applySkinBytesToAccount(appCtx, account.source, bytes, decoded, savedFile.nameWithoutExtension)
                decoded.recycle()
                if (Accounts.getCurrent() == null || selectedIndex < 0) {
                    Accounts.setCurrent(account.source)
                }
                true
            }.getOrDefault(false)
            Tools.runOnUiThread {
                if (result) {
                    wardrobeRevision++
                    reload()
                    Toast.makeText(appCtx, "Skin equipped for ${account.username}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appCtx, "Unable to read .png skin file", Toast.LENGTH_SHORT).show()
                }
                onDone(result)
            }
        }
    }

    fun equipSkinPreset(
        context: Context,
        account: AerixAccount,
        preset: AerixSkinPreset,
        onDone: (Boolean) -> Unit = {}
    ) {
        val appCtx = context.applicationContext
        PojavApplication.sExecutorService.execute {
            val ok = runCatching {
                val file = preset.file
                val applied = if (file == null || preset.id == "default") {
                    customSkinFile(account.source).delete()
                    customFaceFile(account.source).delete()
                    saveActivePresetId(appCtx, account.source, "default")
                    true
                } else if (file.isFile) {
                    val raw = file.readBytes()
                    val rawDecoded = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@runCatching false
                    val normalized = normalizeSkinPng(rawDecoded)
                    val (bytes, decoded) = if (normalized != null) {
                        rawDecoded.recycle()
                        normalized
                    } else {
                        raw to rawDecoded
                    }
                    applySkinBytesToAccount(appCtx, account.source, bytes, decoded, preset.id)
                    decoded.recycle()
                    true
                } else {
                    false
                }
                if (applied && (Accounts.getCurrent() == null || selectedIndex < 0)) {
                    Accounts.setCurrent(account.source)
                }
                applied
            }.getOrDefault(false)
            Tools.runOnUiThread {
                if (ok) {
                    wardrobeRevision++
                    reload()
                }
                onDone(ok)
            }
        }
    }

    /**
     * Resolves the PNG skin texture bytes for [account]:
     * 1. Equipped wardrobe skin (`Tools.DIR_DATA/aerix/wardrobe/skin-<tag>.png`)
     * 2. Per-account remote cache (`Tools.DIR_CACHE/aerix-skin-<tag>.png`)
     * 3. Official Mojang sessionserver texture (for Microsoft accounts) or Ely.by / mirror URLs
     */
    fun resolveAccountSkinBytes(account: AerixAccount?): ByteArray? {
        if (account == null) return null
        resolveCustomSkinBytes(account)?.let { return normalizeSkinBytesForGame(it) }

        val tagCache = File(Tools.DIR_CACHE, "aerix-skin-${safeAccountTag(account.source)}.png")
        if (tagCache.isFile && tagCache.length() > 0) {
            runCatching { return normalizeSkinBytesForGame(tagCache.readBytes()) }
        }

        val rawPid = account.source.profileId?.trim().orEmpty()
        val strippedPid = rawPid.replace("-", "")
        if (strippedPid.length == 32 && !strippedPid.all { it == '0' }) {
            val legacyCache = File(Tools.DIR_CACHE, "aerix-skin-$rawPid-${account.authType.name}.png")
            if (legacyCache.isFile && legacyCache.length() > 0) {
                runCatching { return normalizeSkinBytesForGame(legacyCache.readBytes()) }
            }
        }

        if (account.authType == AuthType.LOCAL) return null

        val candidateUrls = ArrayList<String>()
        if (account.authType == AuthType.MICROSOFT) {
            resolveMojangSkinByUuid(rawPid)?.let { (url, _) -> candidateUrls.add(url) }
            if (candidateUrls.isEmpty() && account.username.isNotBlank()) {
                resolveMojangSkinUrl(account.username)?.let { (url, _) -> candidateUrls.add(url) }
            }
            if (strippedPid.length == 32 && !strippedPid.all { it == '0' }) {
                candidateUrls.add("https://mc-heads.net/skin/$strippedPid")
            }
            if (account.username.isNotBlank()) {
                candidateUrls.add("https://minotar.net/skin/${account.username}")
            }
        }
        account.authType.skinUrl?.let { template ->
            if (account.username.isNotBlank()) {
                candidateUrls.add(String.format(template, account.username))
            }
        }

        for (url in candidateUrls.distinct()) {
            val raw = downloadBytesFollowingRedirects(url) ?: continue
            val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: continue
            val normalized = normalizeSkinPng(decoded)
            decoded.recycle()
            if (normalized != null) {
                val (cleanBytes, cleanBmp) = normalized
                cleanBmp.recycle()
                runCatching { tagCache.writeBytes(cleanBytes) }
                return cleanBytes
            }
        }
        return null
    }

    /** The actual equipped skin's face, including its outer layer. Call off the main thread. */
    fun loadAccountFacePreview(context: Context, account: AerixAccount?): ImageBitmap? {
        if (account == null) return null
        val bytes = resolveAccountSkinBytes(account)
        if (bytes == null && account.authType != AuthType.LOCAL && account.face != null) return account.face
        val skin = if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            else runCatching { context.assets.open("steve.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
        if (skin == null) return account.face
        return try {
            renderSkinFace(skin)?.asImageBitmap() ?: account.face
        } finally {
            skin.recycle()
        }
    }

    /** Crisp 2D full-body preview of [account]'s equipped skin (or default Steve), used while 3D WebGL loads or on non-WebGL devices. */
    fun loadAccountBodyPreview(context: Context, account: AerixAccount?): ImageBitmap? {
        val skinBytes = resolveAccountSkinBytes(account)
        if (skinBytes != null) {
            val bmp = BitmapFactory.decodeByteArray(skinBytes, 0, skinBytes.size)
            if (bmp != null) {
                val body = renderSkinBodyPreview(bmp).asImageBitmap()
                bmp.recycle()
                return body
            }
        }
        return runCatching {
            context.assets.open("steve.png").use { stream ->
                val bmp = BitmapFactory.decodeStream(stream) ?: return@use null
                val body = renderSkinBodyPreview(bmp).asImageBitmap()
                bmp.recycle()
                body
            }
        }.getOrNull()
    }

    fun equipCapeFromUri(
        context: Context,
        account: AerixAccount,
        uri: Uri,
        onDone: (Boolean) -> Unit = {}
    ) {
        val appCtx = context.applicationContext
        PojavApplication.sExecutorService.execute {
            val ok = runCatching {
                val bytes = appCtx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@runCatching false
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@runCatching false
                decoded.recycle()
                val target = customCapeFile(account.source)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
                if (Accounts.getCurrent() == null) {
                    Accounts.setCurrent(account.source)
                }
                true
            }.getOrDefault(false)
            Tools.runOnUiThread {
                if (ok) {
                    reload()
                    wardrobeRevision++
                    Toast.makeText(appCtx, "Cape equipped for ${account.username}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appCtx, "Unable to read .png cape file", Toast.LENGTH_SHORT).show()
                }
                onDone(ok)
            }
        }
    }

    fun resetSkinAndCape(context: Context, account: AerixAccount) {
        val appCtx = context.applicationContext
        PojavApplication.sExecutorService.execute {
            customSkinFile(account.source).delete()
            customCapeFile(account.source).delete()
            customFaceFile(account.source).delete()
            File(Tools.DIR_CACHE, "aerix-skin-${safeAccountTag(account.source)}.png").delete()
            saveActivePresetId(appCtx, account.source, "default")
            setSlimModel(appCtx, account, false)
            Tools.runOnUiThread {
                wardrobeRevision++
                reload()
                Toast.makeText(appCtx, "Reset ${account.username} to default skin", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applySkinBytesToAccount(
        context: Context,
        account: Account,
        bytes: ByteArray,
        decoded: Bitmap,
        presetId: String
    ) {
        val skinTarget = customSkinFile(account)
        skinTarget.parentFile?.mkdirs()
        skinTarget.writeBytes(bytes)

        val sharedCache = File(Tools.DIR_CACHE, "aerix-skin-${safeAccountTag(account)}.png")
        runCatching { sharedCache.writeBytes(bytes) }

        val face = renderSkinFace(decoded)
        if (face != null) {
            val faceTarget = customFaceFile(account)
            FileOutputStream(faceTarget).use { out ->
                face.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val effectivePid = effectiveProfileId(account)
            val pojavFace = File(Tools.DIR_CACHE, "skin-face-$effectivePid-${account.authType?.name ?: "LOCAL"}.webp")
            runCatching {
                FileOutputStream(pojavFace).use { out ->
                    face.compress(Bitmap.CompressFormat.WEBP, 90, out)
                }
            }
            face.recycle()
        }
        saveActivePresetId(context, account, presetId)
    }

    private fun renderSkinFace(skinBitmap: Bitmap): Bitmap? {
        if (skinBitmap.width >= 64 && skinBitmap.height >= 32) {
            val scale = (skinBitmap.width / 64).coerceAtLeast(1)
            val out = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
            canvas.drawBitmap(
                skinBitmap,
                Rect(8 * scale, 8 * scale, 16 * scale, 16 * scale),
                Rect(0, 0, 96, 96),
                paint
            )
            canvas.drawBitmap(
                skinBitmap,
                Rect(40 * scale, 8 * scale, 48 * scale, 16 * scale),
                Rect(0, 0, 96, 96),
                paint
            )
            return out
        }
        return runCatching {
            SkinHeadRenderer().render(96, skinBitmap)
        }.getOrNull()
    }

    /** Renders a crisp 2D front-facing Minecraft character preview from a 64x64/64x32/HD skin texture. */
    private fun renderSkinBodyPreview(skinBitmap: Bitmap): Bitmap {
        val uvScale = (skinBitmap.width / 64).coerceAtLeast(1)
        val is64Tall = skinBitmap.height >= 64 * uvScale
        val scale = 6
        val width = 16 * scale
        val height = 32 * scale
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }

        fun blit(sx: Int, sy: Int, sw: Int, sh: Int, dx: Int, dy: Int) {
            val rx = sx * uvScale
            val ry = sy * uvScale
            val rw = sw * uvScale
            val rh = sh * uvScale
            if (rx + rw <= skinBitmap.width && ry + rh <= skinBitmap.height) {
                canvas.drawBitmap(
                    skinBitmap,
                    Rect(rx, ry, rx + rw, ry + rh),
                    Rect(dx * scale, dy * scale, (dx + sw) * scale, (dy + sh) * scale),
                    paint
                )
            }
        }

        // Head + Hat overlay
        blit(8, 8, 8, 8, 4, 0)
        blit(40, 8, 8, 8, 4, 0)
        // Torso + Jacket overlay
        blit(20, 20, 8, 12, 4, 8)
        if (is64Tall) blit(20, 36, 8, 12, 4, 8)
        // Right Arm (viewer's left)
        blit(44, 20, 4, 12, 0, 8)
        if (is64Tall) blit(44, 36, 4, 12, 0, 8)
        // Left Arm (viewer's right)
        if (is64Tall) {
            blit(36, 52, 4, 12, 12, 8)
            blit(52, 52, 4, 12, 12, 8)
        } else {
            blit(44, 20, 4, 12, 12, 8)
        }
        // Right Leg (viewer's left)
        blit(4, 20, 4, 12, 4, 20)
        if (is64Tall) blit(4, 36, 4, 12, 4, 20)
        // Left Leg (viewer's right)
        if (is64Tall) {
            blit(20, 52, 4, 12, 8, 20)
            blit(4, 52, 4, 12, 8, 20)
        } else {
            blit(4, 20, 4, 12, 8, 20)
        }
        return out
    }

    /**
     * Loads the Saved Skins & Presets grid:
     * 1. "Default" (built-in Steve texture)
     * 2. The 7 themed preset skins from the Account Wardrobe design (seeded into <DIR_GAME_HOME>/skins)
     * 3. Any custom .png skin files the user imported or placed in <DIR_GAME_HOME>/skins
     */
    fun loadSavedSkinPresets(context: Context): List<AerixSkinPreset> {
        val dir = skinsDirectory()
        seedBuiltInPresetsIfNeeded(dir)

        val result = ArrayList<AerixSkinPreset>()
        val stevePreview = runCatching {
            context.assets.open("steve.png").use { stream ->
                val bmp = BitmapFactory.decodeStream(stream) ?: return@use null
                val body = renderSkinBodyPreview(bmp).asImageBitmap()
                bmp.recycle()
                body
            }
        }.getOrNull()
        result.add(AerixSkinPreset(id = "default", name = "Default", file = null, preview = stevePreview))

        val presetOrder = listOf(
            "Neon_Blue" to "Neon Blue",
            "Shadow" to "Shadow",
            "Creeper_Hoodie" to "Creeper Hoodie",
            "Nether_Warrior" to "Nether Warrior",
            "Ender_Knight" to "Ender Knight",
            "Astrix" to "Astrix",
            "Pastel_Dream" to "Pastel Dream"
        )
        val seenFiles = HashSet<String>()
        for ((fileStem, label) in presetOrder) {
            val file = File(dir, "$fileStem.png")
            if (file.isFile) {
                seenFiles.add(file.name.lowercase())
                val preview = decodeBodyPreview(file)
                result.add(AerixSkinPreset(id = fileStem, name = label, file = file, preview = preview))
            }
        }

        val customFiles = dir.listFiles { f ->
            f.isFile && f.name.endsWith(".png", ignoreCase = true) && f.name.lowercase() !in seenFiles
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        for (file in customFiles) {
            val stem = file.nameWithoutExtension
            val label = stem.replace('_', ' ')
            result.add(
                AerixSkinPreset(
                    id = stem,
                    name = label,
                    file = file,
                    preview = decodeBodyPreview(file)
                )
            )
        }
        return result
    }

    private fun decodeBodyPreview(file: File): ImageBitmap? = runCatching {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val preview = renderSkinBodyPreview(bmp).asImageBitmap()
        bmp.recycle()
        preview
    }.getOrNull()

    private fun seedBuiltInPresetsIfNeeded(dir: File) {
        data class Palette(
            val fileStem: String,
            val skinTone: Int,
            val hairColor: Int,
            val eyeColor: Int,
            val torsoPrimary: Int,
            val torsoAccent: Int,
            val sleeveColor: Int,
            val pantsColor: Int,
            val bootsColor: Int
        )
        val presets = listOf(
            Palette("Neon_Blue", 0xFF1E293B.toInt(), 0xFF0B0F19.toInt(), 0xFF38BDF8.toInt(), 0xFF0F172A.toInt(), 0xFF0EA5E9.toInt(), 0xFF111827.toInt(), 0xFF0B1120.toInt(), 0xFF38BDF8.toInt()),
            Palette("Shadow", 0xFFF1D3B3.toInt(), 0xFF18181B.toInt(), 0xFF38BDF8.toInt(), 0xFFE4E4E7.toInt(), 0xFF27272A.toInt(), 0xFFD4D4D8.toInt(), 0xFF18181B.toInt(), 0xFFF4F4F5.toInt()),
            Palette("Creeper_Hoodie", 0xFFE8C39E.toInt(), 0xFF1E1B18.toInt(), 0xFF22C55E.toInt(), 0xFF15803D.toInt(), 0xFF4ADE80.toInt(), 0xFF166534.toInt(), 0xFF1E293B.toInt(), 0xFF14532D.toInt()),
            Palette("Nether_Warrior", 0xFF291714.toInt(), 0xFF1C100E.toInt(), 0xFFF97316.toInt(), 0xFF451A03.toInt(), 0xFFEA580C.toInt(), 0xFF27120B.toInt(), 0xFF1C1917.toInt(), 0xFFD97706.toInt()),
            Palette("Ender_Knight", 0xFF1F1633.toInt(), 0xFF130D21.toInt(), 0xFFD946EF.toInt(), 0xFF2E1065.toInt(), 0xFFA855F7.toInt(), 0xFF1E1B4B.toInt(), 0xFF0F0A1C.toInt(), 0xFF9333EA.toInt()),
            Palette("Astrix", 0xFFF3D5B5.toInt(), 0xFF991B1B.toInt(), 0xFFEF4444.toInt(), 0xFF1F2937.toInt(), 0xFFDC2626.toInt(), 0xFF111827.toInt(), 0xFF18181B.toInt(), 0xFF991B1B.toInt()),
            Palette("Pastel_Dream", 0xFFFDE6D2.toInt(), 0xFFF9A8D4.toInt(), 0xFF60A5FA.toInt(), 0xFF93C5FD.toInt(), 0xFFF472B6.toInt(), 0xFFBFDBFE.toInt(), 0xFF64748B.toInt(), 0xFFF9A8D4.toInt())
        )
        for (p in presets) {
            val file = File(dir, "${p.fileStem}.png")
            if (file.isFile && file.length() > 0) continue
            runCatching {
                val bmp = generateSkinBitmap(
                    skinTone = p.skinTone,
                    hairColor = p.hairColor,
                    eyeColor = p.eyeColor,
                    torsoPrimary = p.torsoPrimary,
                    torsoAccent = p.torsoAccent,
                    sleeveColor = p.sleeveColor,
                    pantsColor = p.pantsColor,
                    bootsColor = p.bootsColor
                )
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bmp.recycle()
            }
        }
    }

    /** Builds a valid 64x64 Minecraft player skin texture so 3D skinview3d and 2D previews render cleanly. */
    private fun generateSkinBitmap(
        skinTone: Int,
        hairColor: Int,
        eyeColor: Int,
        torsoPrimary: Int,
        torsoAccent: Int,
        sleeveColor: Int,
        pantsColor: Int,
        bootsColor: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }

        fun fill(x: Int, y: Int, w: Int, h: Int, color: Int) {
            paint.color = color
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
        }

        // Head UV (0..32, 0..16)
        fill(8, 0, 8, 8, hairColor)
        fill(16, 0, 8, 8, skinTone)
        fill(0, 8, 8, 8, hairColor)
        fill(8, 8, 8, 8, skinTone)
        fill(16, 8, 8, 8, hairColor)
        fill(24, 8, 8, 8, hairColor)
        // Hair bangs & eyes on front face (8, 8, 8, 8)
        fill(8, 8, 8, 2, hairColor)
        fill(8, 10, 2, 1, hairColor)
        fill(14, 10, 2, 1, hairColor)
        fill(9, 12, 2, 1, eyeColor)
        fill(13, 12, 2, 1, eyeColor)

        // Hat overlay (32..64, 0..16)
        fill(40, 8, 8, 2, hairColor)

        // Torso UV (16..40, 16..32)
        fill(20, 16, 8, 4, torsoPrimary)
        fill(28, 16, 8, 4, torsoPrimary)
        fill(16, 20, 24, 12, torsoPrimary)
        // Chest stripe / hoodie detail on front torso (20, 20, 8, 12)
        fill(23, 20, 2, 12, torsoAccent)
        fill(21, 22, 6, 2, torsoAccent)

        // Right Arm (40..56, 16..32) & Left Arm (32..48, 48..64)
        fill(44, 16, 8, 4, sleeveColor)
        fill(40, 20, 16, 10, sleeveColor)
        fill(40, 28, 16, 2, torsoAccent)
        fill(40, 30, 16, 2, skinTone)

        fill(36, 48, 8, 4, sleeveColor)
        fill(32, 52, 16, 10, sleeveColor)
        fill(32, 60, 16, 2, torsoAccent)
        fill(32, 62, 16, 2, skinTone)

        // Right Leg (0..16, 16..32) & Left Leg (16..32, 48..64)
        fill(4, 16, 8, 4, pantsColor)
        fill(0, 20, 16, 10, pantsColor)
        fill(0, 30, 16, 2, bootsColor)

        fill(20, 48, 8, 4, pantsColor)
        fill(16, 52, 16, 10, pantsColor)
        fill(16, 62, 16, 2, bootsColor)

        return bmp
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}
