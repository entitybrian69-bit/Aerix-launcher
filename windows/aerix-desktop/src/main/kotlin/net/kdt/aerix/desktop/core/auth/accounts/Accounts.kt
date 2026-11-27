package net.kdt.aerix.desktop.core.auth.accounts

import net.kdt.aerix.desktop.app.AppEnv
import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.auth.MicrosoftAuth
import net.kdt.aerix.desktop.core.auth.SecureStore
import net.kdt.aerix.desktop.core.auth.SecretUtil
import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.core.json.JsonObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * One launcher account, mirroring `authenticator/accounts/Account.java` (same field names) with the
 * desktop additions: a DPAPI-sealed refresh token, XUID, skin model and the wardrobe presets.
 */
class Account {
    var username: String = "Steve"
    var profileId: String = "00000000-0000-0000-0000-000000000000"
    var accessToken: String = "0"
    var refreshToken: String? = null
    var authType: String = TYPE_OFFLINE
    var xuid: String? = null
    var expiresAt: Long = 0
    var skinModel: String = "classic"
    var skinUrl: String? = null
    var capeUrl: String? = null

    lateinit var file: Path

    val isMicrosoft: Boolean get() = authType == TYPE_MICROSOFT
    val isOffline: Boolean get() = authType == TYPE_OFFLINE
    val isElyBy: Boolean get() = authType == TYPE_ELYBY

    /** The tokens of a Microsoft account never outlive their expiry in memory writes. */
    fun isExpired(): Boolean = isMicrosoft && expiresAt > 0 && System.currentTimeMillis() > expiresAt - 60_000

    fun effectiveUuid(): String {
        val stripped = profileId.replace("-", "")
        return if (isOffline || stripped.length != 32 || stripped.all { it == '0' }) offlineUuid(username).toString() else profileId
    }

    fun effectiveToken(): String = if (isOffline || accessToken.isBlank() || accessToken == "0") offlineToken(username) else accessToken

    fun toJson(): JsonObject {
        val doc = JsonObject()
        doc.put("username", username)
        doc.put("profileId", profileId)
        doc.put("authType", authType)
        doc.put("xuid", xuid)
        doc.put("expiresAt", expiresAt)
        doc.put("skinModel", skinModel)
        doc.put("skinUrl", skinUrl)
        doc.put("capeUrl", capeUrl)
        // Refresh token only, sealed. Access tokens are intentionally never written.
        refreshToken?.takeIf { it.isNotBlank() && it != "0" }?.let { doc.put("refreshTokenSealed", SecureStore.seal(it)) }
        return doc
    }

    fun save() {
        try {
            Files.createDirectories(file.parent)
            SecretUtil.writeOwnerOnly(file.toFile(), Json.pretty(toJson()))
        } catch (e: Exception) {
            Log.e("accounts", "could not save ${file.fileName}: ${e.message}")
        }
    }

    companion object {
        const val TYPE_OFFLINE = "offline"
        const val TYPE_MICROSOFT = "microsoft"
        const val TYPE_ELYBY = "elyby"

        /** Mojang's offline UUID: UUIDv3 of "OfflinePlayer:<name>". */
        fun offlineUuid(name: String): UUID = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(StandardCharsets.UTF_8))

        /**
         * The token an offline account gets for 1.20.5+. Modern versions treat the literal `"0"` as
         * "no session" and skip the skin/profile call; a shaped value keeps the game behaving like
         * the Android launcher's `PojavAccountBridge.effectiveAccessToken`.
         */
        fun offlineToken(name: String): String =
            "0" + UUID.nameUUIDFromBytes("AerixOffline:$name".toByteArray(StandardCharsets.UTF_8)).toString().replace("-", "").take(30)

        fun load(file: Path): Account? = runCatching {
            val doc = Json.parseObject(Files.readString(file))
            val acc = Account()
            acc.file = file
            acc.username = doc.str("username") ?: "Steve"
            acc.profileId = doc.str("profileId") ?: "00000000-0000-0000-0000-000000000000"
            acc.authType = doc.str("authType") ?: TYPE_OFFLINE
            acc.xuid = doc.str("xuid")
            acc.expiresAt = doc.num("expiresAt") ?: 0
            acc.skinModel = doc.str("skinModel") ?: "classic"
            acc.skinUrl = doc.str("skinUrl")
            acc.capeUrl = doc.str("capeUrl")
            acc.refreshToken = doc.str("refreshTokenSealed")?.let { SecureStore.open(it) }
                ?: doc.str("refreshToken") // an Android export may hold it in clear; re-sealed on save
            acc
        }.onFailure { Log.w("accounts", "could not read $file: ${it.message}") }.getOrNull()
    }
}

/** The account list: load, add, remove, select, refresh. */
object Accounts {
    private val list = ArrayList<Account>()
    // Copy-on-write, like Log and GameLauncher: listeners are registered as screens are built, but
    // notifyChanged runs on whatever thread performed the change, which is usually an IO thread.
    // With a plain ArrayList, `toList()` copies while another thread is appending and dies with
    // ArrayIndexOutOfBoundsException - it did exactly that on the Windows runner, inside
    // ArrayList.add, one frame below HomeScreen's constructor.
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    var selectedIndex: Int = -1
        private set

    fun all(): List<Account> = list
    fun current(): Account? = list.getOrNull(selectedIndex)
    fun onChange(l: () -> Unit) { listeners.add(l) }
    private fun notifyChanged() = listeners.forEach { runCatching { it } }

    fun load() {
        list.clear()
        val dir = AppEnv.accountsDir
        runCatching { Files.createDirectories(dir) }
        if (Files.isDirectory(dir)) {
            Files.list(dir).use { s ->
                s.filter { it.fileName.toString().endsWith(".json") }.sorted().forEach { f ->
                    Account.load(f)?.let { list.add(it) }
                }
            }
        }
        val selectedName = Settings.string("selectedAccount", "")
        selectedIndex = list.indexOfFirst { it.file.fileName.toString() == selectedName || it.username == selectedName }
        if (selectedIndex < 0) selectedIndex = if (list.isEmpty()) -1 else 0
        notifyChanged()
    }

    fun addOffline(name: String, skinModel: String = "classic"): Account {
        val clean = name.trim().ifBlank { "Steve" }.take(16)
        val acc = Account()
        acc.username = clean
        acc.profileId = Account.offlineUuid(clean).toString()
        acc.authType = Account.TYPE_OFFLINE
        acc.skinModel = skinModel
        acc.file = uniqueFile("offline-$clean")
        acc.save()
        list.add(acc)
        select(acc)
        notifyChanged()
        Log.i("accounts", "added offline profile $clean")
        return acc
    }

    fun addMicrosoft(session: MicrosoftAuth.Session, model: String = "classic"): Account {
        // Reuse the entry for the same UUID so re-signing in updates rather than duplicates.
        val existing = list.firstOrNull { it.isMicrosoft && it.effectiveUuid().equals(session.uuid, true) }
        val acc = existing ?: Account()
        if (existing == null) acc.file = uniqueFile("msa-${session.username}")
        acc.username = session.username
        acc.profileId = session.uuid
        acc.authType = Account.TYPE_MICROSOFT
        acc.accessToken = session.accessToken
        acc.refreshToken = session.refreshToken
        acc.xuid = session.xuid
        acc.expiresAt = session.expiresAt
        acc.skinModel = model
        acc.save()
        if (existing == null) list.add(acc)
        select(acc)
        notifyChanged()
        Log.i("accounts", "signed in as ${session.username} (owns game: ${session.ownsGame})")
        return acc
    }

    fun remove(account: Account) {
        runCatching { Files.deleteIfExists(account.file) }
        val idx = list.indexOf(account)
        list.remove(account)
        if (idx >= 0) {
            selectedIndex = when {
                list.isEmpty() -> -1
                idx >= list.size -> list.size - 1
                idx < selectedIndex -> selectedIndex - 1
                else -> selectedIndex
            }
            if (selectedIndex in list.indices) select(list[selectedIndex]) else persistSelection()
        }
        notifyChanged()
    }

    fun select(account: Account) {
        selectedIndex = list.indexOf(account)
        persistSelection()
        notifyChanged()
    }

    private fun persistSelection() {
        val current = current()
        if (current != null) Settings.set("selectedAccount", current.file.fileName.toString()) else Settings.remove("selectedAccount")
    }

    /** Ensures the selected account has a valid access token before a launch. */
    fun ensureFreshToken(account: Account): String {
        if (!account.isMicrosoft) return account.effectiveToken()
        if (!account.isExpired() && account.accessToken.isNotBlank() && account.accessToken != "0") return account.accessToken
        val session = MicrosoftAuth.refreshSession(account)
        account.accessToken = session.accessToken
        account.expiresAt = session.expiresAt
        account.xuid = session.xuid
        account.username = session.username
        account.save()
        return session.accessToken
    }

    private fun uniqueFile(base: String): Path {
        val dir = AppEnv.accountsDir
        var candidate = "${sanitise(base)}.json"
        var i = 2
        while (Files.exists(dir.resolve(candidate)) || list.any { it.file.fileName.toString() == candidate }) {
            candidate = "${sanitise(base)}-$i.json"
            i++
        }
        return dir.resolve(candidate)
    }

    private fun sanitise(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]"), "-").take(48).ifBlank { "account" }

    fun reload() = load()
}
