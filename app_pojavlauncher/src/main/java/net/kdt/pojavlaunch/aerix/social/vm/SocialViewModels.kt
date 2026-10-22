/*
 * Aerix Launcher Social layer.
 *
 * ViewModels. Each one owns a StateFlow of immutable UI state, receives events from the
 * Composables and performs every mutation in viewModelScope on the IO dispatcher.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.aerix.social.data.ActivityPost
import net.kdt.pojavlaunch.aerix.social.auth.GoogleAccount
import net.kdt.pojavlaunch.aerix.social.auth.GoogleAuth
import net.kdt.pojavlaunch.aerix.social.auth.GoogleAuthResult
import net.kdt.pojavlaunch.aerix.social.auth.AerixAccount
import net.kdt.pojavlaunch.aerix.social.auth.AerixCredentials
import net.kdt.pojavlaunch.aerix.social.auth.AerixRemoteAccounts
import net.kdt.pojavlaunch.aerix.social.auth.RemoteAerixAccount
import net.kdt.pojavlaunch.aerix.social.auth.GoogleBrowserAuth
import net.kdt.pojavlaunch.aerix.social.auth.GoogleSignInVariant
import net.kdt.pojavlaunch.aerix.social.data.AerixUser
import net.kdt.pojavlaunch.aerix.social.data.AttachmentKind
import net.kdt.pojavlaunch.aerix.social.data.ChatAttachment
import net.kdt.pojavlaunch.aerix.social.data.ChatMessage
import net.kdt.pojavlaunch.aerix.social.data.ChatSettings
import net.kdt.pojavlaunch.aerix.social.data.FriendRequest
import net.kdt.pojavlaunch.aerix.social.data.InstanceShare
import net.kdt.pojavlaunch.aerix.social.data.LinkPlatform
import net.kdt.pojavlaunch.aerix.social.data.MicSettings
import net.kdt.pojavlaunch.aerix.social.data.NotificationSettings
import net.kdt.pojavlaunch.aerix.social.data.PartyState
import net.kdt.pojavlaunch.aerix.social.data.PostKind
import net.kdt.pojavlaunch.aerix.social.data.PrivacySettings
import net.kdt.pojavlaunch.aerix.social.data.ServerInvite
import net.kdt.pojavlaunch.aerix.social.data.SocialConnection
import net.kdt.pojavlaunch.aerix.social.data.SocialLink
import net.kdt.pojavlaunch.aerix.social.data.SocialPersisted
import net.kdt.pojavlaunch.aerix.social.data.SocialRepository
import net.kdt.pojavlaunch.aerix.social.data.AerixAccountsStore
import net.kdt.pojavlaunch.aerix.social.data.InMemoryAerixAccountsStore
import net.kdt.pojavlaunch.aerix.social.data.SocialStore
import net.kdt.pojavlaunch.aerix.social.data.UserSearchResult
import net.kdt.pojavlaunch.aerix.social.data.formatCooldown
import net.kdt.pojavlaunch.aerix.social.data.validateAerixName

/* ------------------------------------------------------------------ shell ----- */

enum class SocialRoute {
    AUTH, CHARACTER, DASHBOARD, ADD_FRIEND, ACTIVITY, THEME, CHAT, PARTY, PROFILE,
    EDIT_PROFILE, SETTINGS, SETTINGS_PRIVACY, SETTINGS_MIC, SETTINGS_CHATS,
    SETTINGS_ACCOUNT, SETTINGS_NOTIFICATIONS, SAFETY, SKIN, CAPE
}

enum class SocialTab { FRIENDS, CHATS, PARTY }

/**
 * Navigation and open/closed state of the second launcher.
 *
 * [open] decides the first screen: a returning player lands on the dashboard, a fresh
 * install starts on the Google sign-in page, and a player who signed up but never finished
 * the character screen goes back to it.
 */
class SocialShellViewModel : ViewModel() {
    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    private val _route = MutableStateFlow(SocialRoute.AUTH)
    val route: StateFlow<SocialRoute> = _route.asStateFlow()

    private val stack = ArrayDeque<SocialRoute>()

    fun open(signedIn: Boolean, characterReady: Boolean = signedIn) {
        stack.clear()
        _route.value = when {
            signedIn && characterReady -> SocialRoute.DASHBOARD
            signedIn -> SocialRoute.CHARACTER
            else -> SocialRoute.AUTH
        }
        _isOpen.value = true
    }

    fun close() {
        stack.clear()
        _isOpen.value = false
    }

    fun push(target: SocialRoute) {
        if (stack.isEmpty() || stack.last() != _route.value) stack.addLast(_route.value)
        _route.value = target
    }

    /** Replaces the current root route, used by sign-in, character setup and sign-out. */
    fun reset(target: SocialRoute) {
        stack.clear()
        _route.value = target
    }

    /** Returns true when the shell consumed the back press, false when it should close. */
    fun back(): Boolean {
        val previous = stack.removeLastOrNull() ?: return false
        _route.value = previous
        return true
    }
}

/* ---------------------------------------------------------------- profile ----- */

data class ProfileUiState(
    val signedIn: Boolean = false,
    /** The Google account this profile belongs to, once a real sign-in happened. */
    val email: String = "",
    val name: String = "",
    val username: String = "",
    val usernameChangedAt: Long = 0L,
    val bio: String = "",
    val links: List<SocialLink> = emptyList(),
    val avatar: Int = 6,
    val skinId: String = "skin_1",
    val capeId: String = "cape_none",
    val themeWallpaperId: String = "",
    val nameError: String? = null,
    val signInInProgress: Boolean = false,
    /** Why the last Google sign-in did not go through, shown on the auth screen. */
    val googleError: String? = null,
    /** Debug-only OAuth client id typed into Settings. */
    val googleClientId: String = "",
    /** Debug-only client for the browser sign-in fallback, typed into Settings. */
    val googleBrowserClientId: String = "",
    /** Secret for that client, only needed when it is a Web application client. */
    val googleBrowserSecret: String = "",
    /** Human readable stage of the Google flow, shown on the portal instead of a dead button. */
    val googleStage: String? = null,
    /** Set once after a successful sign-in so the shell can route, then consumed. */
    val signInOutcome: SignInOutcome? = null,
    /** True when the player pressed "Create a new account", so the character step always runs. */
    val forceCharacterStep: Boolean = false,
    /** Which picker the last attempt used, so the retry can use the other one. */
    val lastSignInVariant: GoogleSignInVariant = GoogleSignInVariant.SHEET,
    /** Aerix-account form state: busy, the last complaint, a note, and a pending restore. */
    val aerixBusy: Boolean = false,
    val aerixError: String? = null,
    val aerixNotice: String? = null,
    val aerixRestoreUsername: String? = null,
    /** Server config for Aerix accounts; blank means this device only. */
    val aerixServerUrl: String = "",
    val aerixAnonKey: String = "",
    /** Remembered while a deleted server account waits for its restore. */
    val aerixPendingEmail: String = "",
    val aerixPendingUserId: String = "",
    val aerixPendingToken: String = "",
    val saved: Boolean = false
) {
    fun canChangeUsername(now: Long = System.currentTimeMillis()): Boolean =
        now >= usernameChangedAt + SocialPersisted.USERNAME_COOLDOWN_MS

    fun cooldownRemaining(now: Long = System.currentTimeMillis()): Long =
        (usernameChangedAt + SocialPersisted.USERNAME_COOLDOWN_MS - now).coerceAtLeast(0L)
}

/** What a successful Google sign-in means for the screens: dashboard or character step. */
enum class SignInOutcome { RETURNING, NEW_ACCOUNT, SWITCHED_ACCOUNT }

class SocialProfileViewModel(
    private val store: SocialStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Cleared when a different Google account signs in, so chats never leak between accounts. */
    private val repository: SocialRepository? = null,
    /** The Aerix account book. Memory-only when nothing supplies a persistent one. */
    private val accounts: AerixAccountsStore = InMemoryAerixAccountsStore()
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.data.collect { persisted ->
                _state.update {
                    it.copy(
                        signedIn = persisted.signedIn,
                        email = persisted.email,
                        googleClientId = persisted.googleClientId,
                        googleBrowserClientId = persisted.googleBrowserClientId,
                        googleBrowserSecret = persisted.googleBrowserSecret,
                        aerixServerUrl = persisted.aerixServerUrl,
                        aerixAnonKey = persisted.aerixAnonKey,
                        name = persisted.name,
                        username = persisted.username.ifBlank { persisted.name },
                        usernameChangedAt = persisted.usernameChangedAt,
                        bio = persisted.bio,
                        links = persisted.links,
                        avatar = persisted.avatar,
                        skinId = persisted.skinId,
                        capeId = persisted.capeId,
                        themeWallpaperId = persisted.themeWallpaperId
                    )
                }
            }
        }
    }

    /**
     * Real Google sign-in. Stores the account's email and, when this device has no profile yet,
     * seeds the name from Google before the character screen refines it.
     *
     * Signing in with a *different* Google address than the one on file starts that account
     * fresh: the profile and the locally stored Social content are cleared, because they belong
     * to the previous account.
     */
    fun signInWithGoogle(account: GoogleAccount, onDone: (SignInOutcome) -> Unit = {}) =
        viewModelScope.launch {
            _state.update { it.copy(signInInProgress = true, googleError = null, googleStage = "Saving your profile…") }
            val previousEmail = _state.value.email
            val hasProfile = _state.value.name.isNotBlank()
            val switched = previousEmail.isNotBlank() &&
                !previousEmail.equals(account.email, ignoreCase = true)
            val fallbackName = account.displayName.ifBlank { account.email.substringBefore('@') }.take(16)
            val fallbackUsername = fallbackName.filter { it.isLetterOrDigit() }.take(16)

            if (switched) repository?.clearAll()

            withContext(io) {
                store.update { persisted ->
                    if (switched) {
                        // Different person: keep app-level preferences, drop the account's data.
                        SocialPersisted(
                            signedIn = true,
                            email = account.email,
                            googleClientId = persisted.googleClientId,
                            googleBrowserClientId = persisted.googleBrowserClientId,
                            googleBrowserSecret = persisted.googleBrowserSecret,
                            aerixServerUrl = persisted.aerixServerUrl,
                            aerixAnonKey = persisted.aerixAnonKey,
                            // No session for the previous account survives a switch.
                            name = fallbackName,
                            username = fallbackUsername,
                            privacy = persisted.privacy,
                            notifications = persisted.notifications,
                            chat = persisted.chat,
                            mic = persisted.mic
                        )
                    } else {
                        persisted.copy(
                            signedIn = true,
                            email = account.email,
                            name = persisted.name.ifBlank { fallbackName },
                            username = persisted.username.ifBlank {
                                (persisted.name.ifBlank { fallbackName }).filter { it.isLetterOrDigit() }.take(16)
                            }
                        )
                    }
                }
            }
            _state.update {
                it.copy(
                    signedIn = true,
                    email = account.email,
                    // A switched account starts from the Google name; its character step follows.
                    name = if (switched) fallbackName else it.name.ifBlank { fallbackName },
                    username = if (switched) fallbackUsername else it.username.ifBlank { fallbackUsername },
                    skinId = if (switched) "skin_1" else it.skinId,
                    capeId = if (switched) "cape_none" else it.capeId,
                    bio = if (switched) "" else it.bio,
                    links = if (switched) emptyList() else it.links,
                    signInInProgress = false
                )
            }
            val outcome = when {
                switched -> SignInOutcome.SWITCHED_ACCOUNT
                hasProfile -> SignInOutcome.RETURNING
                else -> SignInOutcome.NEW_ACCOUNT
            }
            _state.update { it.copy(googleStage = null, signInOutcome = outcome) }
            onDone(outcome)
        }

    /**
     * Runs the whole Google flow from the ViewModel, not from a composable.
     *
     * The account sheet takes the app out of the foreground, so a coroutine owned by the screen
     * can be cancelled while the player is picking an account — which looks exactly like "I
     * tapped my account and nothing happened". A ViewModel outlives that, so the result always
     * lands somewhere, and the screen navigates from state instead of a callback.
     */
    fun signInWithGoogle(
        context: android.content.Context,
        clientIdOverride: String = "",
        creatingAccount: Boolean = false,
        variant: GoogleSignInVariant = GoogleSignInVariant.SHEET
    ) = viewModelScope.launch {
            _state.update {
                it.copy(
                    signInInProgress = true,
                    googleError = null,
                    googleStage = "Opening Google…",
                    signInOutcome = null,
                    forceCharacterStep = creatingAccount,
                    lastSignInVariant = variant
                )
            }
            val result = try {
                kotlinx.coroutines.withTimeoutOrNull(GOOGLE_TIMEOUT_MS) {
                    GoogleAuth.signIn(context, clientIdOverride, variant)
                } ?: GoogleAuthResult.Failed(
                    "Google did not answer within ${GOOGLE_TIMEOUT_MS / 1000} seconds. " +
                        "Check that Play Services is signed in, then try again."
                )
            } catch (error: Throwable) {
                GoogleAuthResult.Failed(error.message ?: "Google sign-in failed.")
            }
            when (result) {
                is GoogleAuthResult.Success -> {
                    _state.update { it.copy(googleStage = "Signing you in…") }
                    signInWithGoogle(result.account)
                }
                is GoogleAuthResult.Cancelled -> _state.update {
                    // Not an error, but never silence either: an interrupted sheet used to look
                    // exactly like a dead button. A sheet that closes on *every* attempt is not
                    // the player cancelling — it is Play services refusing the app, and the fix
                    // is the signing key registration named in Settings → Account.
                    it.copy(
                        signInInProgress = false,
                        googleStage = null,
                        googleError = buildString {
                            append("Google closed before the account was confirmed.")
                            append(" Closing the sheet yourself is fine — tap again.")
                            append(" If Google refused it, the same tap through the other ")
                            append("picker often goes through: use the link below.")
                            append(" If it closes every single time, Play services is refusing this ")
                            append("app, which is a Google Cloud setting, not a launcher bug:")
                            append(" add an Android OAuth client with this build's package and ")
                            append("SHA-1 (Settings → Account → Sign-in diagnostics), and make ")
                            append("sure this Gmail is under Audience → Test users.")
                            val detail = result.detail
                            if (detail.isNotBlank()) append(" · ").append(detail)
                        }
                    )
                }
                GoogleAuthResult.NotConfigured -> _state.update {
                    it.copy(
                        signInInProgress = false,
                        googleStage = null,
                        googleError = "This build has no Google OAuth client id."
                    )
                }
                is GoogleAuthResult.Failed -> _state.update {
                    it.copy(signInInProgress = false, googleStage = null, googleError = result.message)
                }
            }
        }

    /**
     * Browser sign-in: the same Google login, through the system browser instead of the Play
     * services sheet. Works when the sheet refuses the app (unregistered signing key, missing
     * Android OAuth client, old Play services) because it never asks Play services anything.
     */
    fun signInWithBrowser(context: android.content.Context, creatingAccount: Boolean = false) =
        viewModelScope.launch {
            val clientId = _state.value.googleBrowserClientId.ifBlank {
                GoogleAuth.resolveClientId(context, _state.value.googleClientId)
            }
            if (!GoogleBrowserAuth.isUsable(clientId)) {
                _state.update {
                    it.copy(
                        googleError = "Browser sign-in has no client id yet. Add one in Settings → " +
                            "Account → Browser sign-in (testing)."
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    signInInProgress = true,
                    googleError = null,
                    googleStage = "Waiting for the browser…",
                    signInOutcome = null,
                    forceCharacterStep = creatingAccount
                )
            }
            val result = try {
                kotlinx.coroutines.withTimeoutOrNull(GoogleBrowserAuth.TIMEOUT_MS + 30_000) {
                    GoogleBrowserAuth.signIn(
                        context = context,
                        clientId = clientId,
                        clientSecret = _state.value.googleBrowserSecret
                    )
                } ?: GoogleAuthResult.Failed(
                    "The browser never came back to the launcher. Complete the Google page, then " +
                        "try again."
                )
            } catch (error: Throwable) {
                GoogleAuthResult.Failed(error.message ?: "Browser sign-in failed.")
            }
            when (result) {
                is GoogleAuthResult.Success -> {
                    _state.update { it.copy(googleStage = "Signing you in…") }
                    signInWithGoogle(result.account)
                }
                is GoogleAuthResult.Cancelled -> _state.update {
                    it.copy(
                        signInInProgress = false,
                        googleStage = null,
                        googleError = "Sign-in was cancelled in the browser."
                    )
                }
                GoogleAuthResult.NotConfigured -> _state.update {
                    it.copy(
                        signInInProgress = false,
                        googleStage = null,
                        googleError = "This build has no Google OAuth client id."
                    )
                }
                is GoogleAuthResult.Failed -> _state.update {
                    it.copy(signInInProgress = false, googleStage = null, googleError = result.message)
                }
            }
        }

    /** Saves the browser sign-in client. Debug only: the rows live behind [BuildConfig.DEBUG]. */
    fun setBrowserClient(clientId: String, secret: String) {
        val cleanId = clientId.trim()
        val cleanSecret = secret.trim()
        viewModelScope.launch {
            withContext(io) {
                store.update { it.copy(googleBrowserClientId = cleanId, googleBrowserSecret = cleanSecret) }
            }
            _state.update { it.copy(googleBrowserClientId = cleanId, googleBrowserSecret = cleanSecret) }
        }
    }

    /** Whether the browser fallback is ready to be offered on the portal. */
    fun browserSignInReady(): Boolean =
        GoogleBrowserAuth.isUsable(_state.value.googleBrowserClientId)

    /** The shell consumes the outcome once so a later sign-out cannot replay the navigation. */
    fun consumeSignInOutcome() {
        _state.update { it.copy(signInOutcome = null, forceCharacterStep = false) }
    }

    // ------------------------------------------------------------------ Aerix accounts

    /**
     * Creates an Aerix account and signs into it.
     *
     * With a server configured the account is created there, so it follows the person to any
     * device; without one it is created on this device only.
     */
    fun aerixSignUp(username: String, email: String, password: String) = viewModelScope.launch {
        clearAerixMessages()
        _state.update { it.copy(aerixBusy = true) }
        val problem = AerixCredentials.usernameProblem(username)
            ?: AerixCredentials.emailProblem(email)
            ?: AerixCredentials.passwordProblem(password)
        if (problem != null) {
            _state.update { it.copy(aerixBusy = false, aerixError = problem) }
            return@launch
        }

        val remote = remoteAccounts()
        if (remote != null) {
            val attempt = runCatching {
                withContext(io) { remote.signUp(username, email, password) }
            }
            attempt.onSuccess { account ->
                persistSession(account)
                _state.update {
                    it.copy(aerixBusy = false, aerixNotice = "Account created. Welcome, ${account.username}.")
                }
                signInWithAccount(account)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        aerixBusy = false,
                        aerixError = error.message ?: "The server could not create the account."
                    )
                }
            }
            return@launch
        }

        val account = AerixCredentials.create(username, email, password)
        val taken = withContext(io) { !accounts.insert(account) }
        if (taken) {
            val existing = withContext(io) { accounts.find(username) ?: accounts.find(email) }
            _state.update {
                it.copy(
                    aerixBusy = false,
                    aerixError = if (existing?.username?.equals(username.trim(), true) == true) {
                        "That username is taken. Try another one."
                    } else {
                        "That email already has an account. Sign in instead."
                    }
                )
            }
            return@launch
        }
        _state.update { it.copy(aerixBusy = false, aerixNotice = "Account created. Welcome, ${account.username}.") }
        signInWithAccount(account)
    }

    /**
     * Signs into an existing Aerix account. A deleted account is *not* signed in silently: the
     * correct password unlocks an explicit restore, so a deleted account can come back without
     * the person having to guess what happened to it.
     */
    fun aerixSignIn(identifier: String, password: String) = viewModelScope.launch {
        clearAerixMessages()
        _state.update { it.copy(aerixBusy = true) }
        if (identifier.isBlank()) {
            _state.update { it.copy(aerixBusy = false, aerixError = "Enter your email") }
            return@launch
        }
        if (password.isEmpty()) {
            _state.update { it.copy(aerixBusy = false, aerixError = "Enter your password") }
            return@launch
        }

        val remote = remoteAccounts()
        if (remote != null) {
            if (!identifier.contains('@')) {
                _state.update {
                    it.copy(
                        aerixBusy = false,
                        aerixError = "Server accounts sign in with their email address."
                    )
                }
                return@launch
            }
            val attempt = runCatching {
                withContext(io) { remote.signIn(identifier, password) }
            }
            attempt.onSuccess { account ->
                if (account.deletedAt != null) {
                    _state.update {
                        it.copy(
                            aerixBusy = false,
                            aerixRestoreUsername = account.username,
                            aerixPendingEmail = account.email,
                            aerixPendingUserId = account.userId,
                            aerixPendingToken = account.accessToken,
                            aerixNotice = "${account.username} was deleted on " +
                                "${formatDeletedAt(account.deletedAt)}. The password is right, so it " +
                                "can be restored with everything in it."
                        )
                    }
                    return@launch
                }
                persistSession(account)
                _state.update { it.copy(aerixBusy = false) }
                signInWithAccount(account)
            }.onFailure { error ->
                _state.update {
                    it.copy(aerixBusy = false, aerixError = error.message ?: "Sign-in failed.")
                }
            }
            return@launch
        }

        val account = withContext(io) { accounts.find(identifier) }
        if (account == null) {
            _state.update {
                it.copy(
                    aerixBusy = false,
                    aerixError = "No Aerix account called \"${identifier.trim()}\" on this device. " +
                        "Create one, or sign in with Google."
                )
            }
            return@launch
        }
        val correct = withContext(io) { AerixCredentials.verify(account, password) }
        if (!correct) {
            _state.update { it.copy(aerixBusy = false, aerixError = "That password doesn't match this account.") }
            return@launch
        }
        if (account.isDeleted) {
            _state.update {
                it.copy(
                    aerixBusy = false,
                    aerixRestoreUsername = account.username,
                    aerixNotice = "${account.username} was deleted on ${formatDeletedAt(account.deletedAt)}. " +
                        "The password is right, so it can be restored with everything in it."
                )
            }
            return@launch
        }
        _state.update { it.copy(aerixBusy = false) }
        signInWithAccount(account)
    }

    /** Brings a deleted account back and signs into it. */
    fun aerixRestore(username: String, password: String) = viewModelScope.launch {
        _state.update { it.copy(aerixBusy = true, aerixError = null) }

        val remote = remoteAccounts()
        val pendingToken = _state.value.aerixPendingToken
        val pendingId = _state.value.aerixPendingUserId
        val pendingEmail = _state.value.aerixPendingEmail
        if (remote != null && pendingToken.isNotBlank() && pendingId.isNotBlank()) {
            val attempt = runCatching {
                withContext(io) {
                    remote.restore(pendingToken, pendingId)
                    remote.signIn(pendingEmail, password)
                }
            }
            attempt.onSuccess { account ->
                persistSession(account)
                _state.update {
                    it.copy(
                        aerixBusy = false,
                        aerixRestoreUsername = null,
                        aerixNotice = "Welcome back. ${account.username} is restored."
                    )
                }
                signInWithAccount(account)
            }.onFailure { error ->
                _state.update {
                    it.copy(aerixBusy = false, aerixError = error.message ?: "Restore failed.")
                }
            }
            return@launch
        }

        val account = withContext(io) { accounts.find(username) }
        if (account == null || !AerixCredentials.verify(account, password)) {
            _state.update {
                it.copy(aerixBusy = false, aerixError = "That account could not be restored. Sign in first.")
            }
            return@launch
        }
        withContext(io) { accounts.restore(account.id) }
        _state.update {
            it.copy(
                aerixBusy = false,
                aerixRestoreUsername = null,
                aerixNotice = "Welcome back. ${account.username} is restored."
            )
        }
        signInWithAccount(account)
    }

    /** Stores the server URL and public key, then clears anything that belonged to a session. */
    fun setAerixServer(url: String, anonKey: String) {
        val cleanUrl = url.trim().trimEnd('/')
        val cleanKey = anonKey.trim()
        viewModelScope.launch {
            withContext(io) {
                store.update {
                    it.copy(
                        aerixServerUrl = cleanUrl,
                        aerixAnonKey = cleanKey,
                        aerixUserId = "",
                        aerixAccessToken = "",
                        aerixRefreshToken = ""
                    )
                }
            }
            _state.update {
                it.copy(
                    aerixServerUrl = cleanUrl,
                    aerixAnonKey = cleanKey,
                    aerixPendingEmail = "",
                    aerixPendingUserId = "",
                    aerixPendingToken = ""
                )
            }
        }
    }

    /** The server client, or null when this install has no project configured. */
    private fun remoteAccounts(): AerixRemoteAccounts? {
        val url = _state.value.aerixServerUrl
        val key = _state.value.aerixAnonKey
        if (url.isBlank() || key.isBlank()) return null
        return AerixRemoteAccounts(url, key).takeIf { it.isConfigured() }
    }

    private suspend fun persistSession(account: RemoteAerixAccount) {
        withContext(io) {
            store.update {
                it.copy(
                    aerixUserId = account.userId,
                    aerixAccessToken = account.accessToken,
                    aerixRefreshToken = account.refreshToken
                )
            }
        }
        _state.update {
            it.copy(
                aerixPendingEmail = "",
                aerixPendingUserId = "",
                aerixPendingToken = ""
            )
        }
    }

    /** Signs in as an Aerix account, reusing the same profile path as Google. */
    private fun signInWithAccount(account: AerixAccount) {
        signInWithAccount(account.email, account.username)
    }

    private fun signInWithAccount(account: RemoteAerixAccount) {
        signInWithAccount(account.email, account.username)
    }

    private fun signInWithAccount(email: String, username: String) {
        signInWithGoogle(
            GoogleAccount(
                email = email,
                displayName = username,
                // No Google token: this account is ours, so there is nothing to verify remotely.
                idToken = ""
            )
        )
    }

    fun clearAerixMessages() {
        _state.update {
            it.copy(aerixError = null, aerixNotice = null, aerixRestoreUsername = null)
        }
    }

    private fun formatDeletedAt(at: Long): String = runCatching {
        java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(at))
    }.getOrDefault("earlier")

    /** A sign-in that failed for a reason worth showing (no account, not configured, …). */
    fun onGoogleSignInFailed(message: String) {
        _state.update { it.copy(signInInProgress = false, googleError = message) }
    }

    fun clearGoogleError() {
        _state.update { it.copy(googleError = null) }
    }

    /**
     * Real sign-out. The Google session for this app is cleared by the caller
     * ([net.kdt.pojavlaunch.aerix.social.auth.GoogleAuth.signOut]); the address stays on file so
     * the same account is recognised (and not wiped) when it comes back.
     */
    fun signOut() = viewModelScope.launch {
        withContext(io) { store.update { it.copy(signedIn = false) } }
        _state.update { it.copy(signedIn = false, googleError = null) }
    }

    /** Removes the Google link entirely, so the next account is treated as a new person. */
    fun forgetGoogleAccount() = viewModelScope.launch {
        withContext(io) { store.update { it.copy(signedIn = false, email = "") } }
        _state.update { it.copy(signedIn = false, email = "") }
    }

    /** Debug-build testing hook: the OAuth client id to use when the APK was built without one. */
    fun setGoogleClientId(value: String) = viewModelScope.launch {
        val clean = value.trim()
        withContext(io) { store.update { it.copy(googleClientId = clean) } }
        _state.update { it.copy(googleClientId = clean) }
    }

    /**
     * Existing account: the stored profile is kept, so the player lands straight on the
     * dashboard. A blank name (fresh install) falls back to the account display name.
     */
    fun signIn(accountName: String = "Nova", onDone: () -> Unit = {}) = viewModelScope.launch {
        _state.update { it.copy(signInInProgress = true) }
        withContext(io) {
            store.update {
                it.copy(
                    signedIn = true,
                    name = it.name.ifBlank { accountName },
                    username = it.username.ifBlank { it.name.ifBlank { accountName } }
                )
            }
        }
        _state.update { it.copy(signInInProgress = false) }
        onDone()
    }

    /** New account: never touches an existing profile, the character screen fills it in. */
    fun createAccount() = viewModelScope.launch {
        withContext(io) { store.update { it.copy(signedIn = true) } }
    }

    fun disableAccount() = viewModelScope.launch {
        withContext(io) { store.update { it.copy(signedIn = false) } }
    }

    /** Deletes the profile and everything this device stored for it. */
    fun deleteAccount() = viewModelScope.launch {
        // When this profile belongs to an Aerix account, mark the account itself as deleted
        // rather than erasing the credential: the same username can then be signed back in and
        // restored by the person who knows the password. Google-linked profiles have no local
        // credential, so there is nothing to mark.
        val snapshot = withContext(io) { store.snapshot() }
        val accountEmail = _state.value.email
        if (accountEmail.isNotBlank()) {
            withContext(io) {
                accounts.find(accountEmail)?.let { accounts.softDelete(it.id) }
            }
        }
        // Server account: the row is marked deleted too, so the restore works from any device.
        val remote = remoteAccounts()
        if (remote != null && snapshot.aerixUserId.isNotBlank() && snapshot.aerixAccessToken.isNotBlank()) {
            runCatching {
                withContext(io) { remote.softDelete(snapshot.aerixAccessToken, snapshot.aerixUserId) }
            }.onFailure { error ->
                // Deleting locally must still succeed, but say what the server did not do.
                _state.update { it.copy(googleError = null) }
                android.util.Log.w("AerixAccounts", "Server delete failed: ${error.message}")
            }
        }
        repository?.clearAll()
        withContext(io) {
            store.update { previous ->
                // Clear the account and its session, keep preferences and the server config.
                SocialPersisted(
                    googleClientId = previous.googleClientId,
                    googleBrowserClientId = previous.googleBrowserClientId,
                    googleBrowserSecret = previous.googleBrowserSecret,
                    aerixServerUrl = previous.aerixServerUrl,
                    aerixAnonKey = previous.aerixAnonKey,
                    themeWallpaperId = previous.themeWallpaperId,
                    privacy = previous.privacy,
                    notifications = previous.notifications,
                    chat = previous.chat,
                    mic = previous.mic
                )
            }
        }
        _state.update {
            it.copy(signedIn = false, email = "", name = "", username = "", bio = "", links = emptyList())
        }
    }

    fun onNameChange(value: String) {
        _state.update { it.copy(name = value.take(16), nameError = null, saved = false) }
    }

    fun onUsernameChange(value: String) {
        _state.update { it.copy(username = value.take(16), nameError = null, saved = false) }
    }

    fun onBioChange(value: String) {
        _state.update { it.copy(bio = value.take(140)) }
        viewModelScope.launch {
            withContext(io) { store.update { it.copy(bio = value.take(140)) } }
        }
    }

    fun selectAvatar(index: Int) = viewModelScope.launch {
        _state.update { it.copy(avatar = index) }
        withContext(io) { store.update { it.copy(avatar = index) } }
    }

    fun selectSkin(id: String) = viewModelScope.launch {
        withContext(io) { store.update { it.copy(skinId = id) } }
    }

    fun selectCape(id: String) = viewModelScope.launch {
        withContext(io) { store.update { it.copy(capeId = id) } }
    }

    fun selectTheme(wallpaperId: String) = viewModelScope.launch {
        _state.update { it.copy(themeWallpaperId = wallpaperId) }
        withContext(io) { store.update { it.copy(themeWallpaperId = wallpaperId) } }
    }

    fun addLink(platform: LinkPlatform, value: String) {
        // Keep the @ for platforms that show it, drop it only when the prefix already has one.
        val raw = value.trim()
        val handle = if (platform.prefix.endsWith("@")) raw.removePrefix("@") else raw
        if (handle.isBlank()) return
        viewModelScope.launch {
            withContext(io) {
                store.update { current ->
                    val links = current.links.filterNot { it.platform == platform } + SocialLink(platform, handle)
                    current.copy(links = links)
                }
            }
        }
    }

    fun removeLink(platform: LinkPlatform) = viewModelScope.launch {
        withContext(io) { store.update { current -> current.copy(links = current.links.filterNot { it.platform == platform }) } }
    }

    /** Validates and persists the character screen. Returns true when the caller may move on. */
    fun saveCharacter(onSaved: () -> Unit = {}): Boolean {
        val name = _state.value.name.trim()
        val username = _state.value.username.trim().ifBlank { name }
        val error = validateAerixName(name) ?: validateAerixName(username)?.let { "Username: $it" }
        if (error != null) {
            _state.update { it.copy(nameError = error) }
            return false
        }
        viewModelScope.launch {
            withContext(io) {
                store.update {
                    it.copy(
                        name = name,
                        username = username,
                        signedIn = true
                    )
                }
            }
            _state.update { it.copy(saved = true, nameError = null, name = name, username = username) }
            onSaved()
        }
        return true
    }

    /**
     * Username changes are limited to once a week. Returns false (and surfaces the cooldown
     * in [ProfileUiState]) when the week is not over yet.
     */
    fun changeUsername(value: String): Boolean {
        val now = System.currentTimeMillis()
        val state = _state.value
        if (!state.canChangeUsername(now)) {
            _state.update { it.copy(nameError = "Username can be changed again in ${formatCooldown(state.cooldownRemaining(now))}") }
            return false
        }
        val error = validateAerixName(value.trim())
        if (error != null) {
            _state.update { it.copy(nameError = error) }
            return false
        }
        viewModelScope.launch {
            withContext(io) {
                store.update { it.copy(username = value.trim(), usernameChangedAt = now) }
            }
            _state.update { it.copy(username = value.trim(), usernameChangedAt = now, nameError = null) }
        }
        return true
    }

    private companion object {
        const val GOOGLE_TIMEOUT_MS = 90_000L
    }

    fun snapshot(): SocialPersisted = SocialPersisted(
        signedIn = _state.value.signedIn,
        email = _state.value.email,
        googleClientId = _state.value.googleClientId,
        googleBrowserClientId = _state.value.googleBrowserClientId,
        googleBrowserSecret = _state.value.googleBrowserSecret,
        name = _state.value.name,
        username = _state.value.username,
        usernameChangedAt = _state.value.usernameChangedAt,
        bio = _state.value.bio,
        links = _state.value.links,
        avatar = _state.value.avatar,
        skinId = _state.value.skinId,
        capeId = _state.value.capeId,
        themeWallpaperId = _state.value.themeWallpaperId
    )

}

/* -------------------------------------------------------------- dashboard ----- */

data class DashboardUiState(
    val friends: List<AerixUser> = emptyList(),
    val requests: List<FriendRequest> = emptyList(),
    val party: PartyState = PartyState(),
    val connection: SocialConnection = SocialConnection.LOADING,
    val query: String = "",
    val addFriendSentTo: String? = null
) {
    val online: List<AerixUser> get() = friends.filter { it.status != net.kdt.pojavlaunch.aerix.social.data.UserStatus.OFFLINE }
    val offline: List<AerixUser> get() = friends.filter { it.status == net.kdt.pojavlaunch.aerix.social.data.UserStatus.OFFLINE }
}

class SocialDashboardViewModel(private val repository: SocialRepository) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.friends.collect { friends -> _state.update { it.copy(friends = friends) } }
        }
        viewModelScope.launch {
            repository.requests.collect { requests -> _state.update { it.copy(requests = requests) } }
        }
        viewModelScope.launch {
            repository.party.collect { party -> _state.update { it.copy(party = party) } }
        }
        viewModelScope.launch {
            repository.connection.collect { connection -> _state.update { it.copy(connection = connection) } }
        }
        refresh()
    }

    fun refresh() = viewModelScope.launch { repository.refresh() }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun accept(id: String) = viewModelScope.launch { repository.acceptRequest(id) }
    fun decline(id: String) = viewModelScope.launch { repository.declineRequest(id) }
    fun createParty(name: String = "Nova's party", logo: Int = 3) = viewModelScope.launch { repository.createParty(name, logo) }
    fun leaveParty() = viewModelScope.launch { repository.leaveParty() }

    fun addFriend(name: String) = viewModelScope.launch {
        if (repository.addFriend(name)) _state.update { it.copy(addFriendSentTo = name) }
    }

    fun dismissAddFriend() {
        _state.update { it.copy(addFriendSentTo = null) }
    }

    val visibleFriends: List<AerixUser>
        get() {
            val query = _state.value.query.trim()
            if (query.isEmpty()) return _state.value.friends
            return _state.value.friends.filter { it.name.contains(query, true) }
        }
}

/* -------------------------------------------------------------- add friend ----- */

data class AddFriendUiState(
    val query: String = "",
    val byEmail: Boolean = false,
    val searching: Boolean = false,
    val results: List<UserSearchResult> = emptyList(),
    val sentTo: Set<String> = emptySet(),
    val error: String? = null
)

class SocialAddFriendViewModel(private val repository: SocialRepository) : ViewModel() {
    private val _state = MutableStateFlow(AddFriendUiState())
    val state: StateFlow<AddFriendUiState> = _state.asStateFlow()

    fun onQueryChange(value: String) = _state.update { it.copy(query = value, error = null) }

    fun setByEmail(value: Boolean) = _state.update { it.copy(byEmail = value, results = emptyList(), error = null) }

    fun search() = viewModelScope.launch {
        val query = _state.value.query.trim()
        if (query.length < 3) {
            _state.update { it.copy(error = if (_state.value.byEmail) "Enter the full Gmail address" else "Enter at least 3 characters") }
            return@launch
        }
        _state.update { it.copy(searching = true, error = null) }
        val results = repository.search(query, _state.value.byEmail)
        _state.update {
            it.copy(
                searching = false,
                results = results,
                error = if (results.isEmpty()) "No Aerix account matches that ${if (it.byEmail) "Gmail" else "username"}" else null
            )
        }
    }

    fun send(userId: String) = viewModelScope.launch {
        if (repository.sendRequest(userId)) {
            _state.update { it.copy(sentTo = it.sentTo + userId) }
        }
    }
}

/* ------------------------------------------------------------------- chat ----- */

data class ChatUiState(
    val friendId: String? = null,
    val friendName: String = "",
    val friendAvatar: Int = 1,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val shares: Map<String, InstanceShare> = emptyMap(),
    val playingMusicId: String? = null
)

class SocialChatViewModel(private val repository: SocialRepository) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.chats.collect { chats ->
                val id = _state.value.friendId ?: return@collect
                _state.update { it.copy(messages = chats[id].orEmpty()) }
            }
        }
        viewModelScope.launch {
            repository.shares.collect { shares -> _state.update { it.copy(shares = shares) } }
        }
    }

    fun open(friend: AerixUser) = viewModelScope.launch {
        repository.ensureChat(friend.id)
        _state.update {
            it.copy(
                friendId = friend.id,
                friendName = friend.name,
                friendAvatar = friend.avatar,
                messages = repository.chats.value[friend.id].orEmpty()
            )
        }
    }

    fun onDraftChange(value: String) {
        _state.update { it.copy(draft = value) }
    }

    fun send() {
        val id = _state.value.friendId ?: return
        val text = _state.value.draft
        if (text.isBlank()) return
        _state.update { it.copy(draft = "") }
        viewModelScope.launch { repository.sendMessage(id, text) }
    }

    /** Sends an attachment that really exists on this device (or a shared instance). */
    fun sendAttachment(attachment: ChatAttachment) {
        val id = _state.value.friendId ?: return
        viewModelScope.launch { repository.sendMessage(id, "", attachment) }
    }

    /**
     * Sends an attachment of [kind] without a picker. The chat screen now always feeds real
     * device files, so this stays for tests and for anything that builds a message by hand.
     */
    fun attach(kind: AttachmentKind) {
        val attachment = when (kind) {
            AttachmentKind.INSTANCE -> ChatAttachment(
                kind = kind,
                title = "Vanilla 1.21",
                subtitle = "1.21.4",
                payload = "vanilla-1.21"
            )
            AttachmentKind.MUSIC -> ChatAttachment(kind = kind, title = "Aurora Drift", subtitle = "Nova", payload = "aurora-drift")
            AttachmentKind.IMAGE -> ChatAttachment(kind = kind, title = "screenshot.png", subtitle = "image/png")
            AttachmentKind.GIF -> ChatAttachment(kind = kind, title = "creeper.gif", subtitle = "image/gif")
            AttachmentKind.FILE -> ChatAttachment(kind = kind, title = "mods.zip", subtitle = "application/zip")
            AttachmentKind.LINK -> ChatAttachment(kind = kind, title = "aerix.launcher", payload = "https://aerix.example")
        }
        sendAttachment(attachment)
    }

    /** Sends a link typed by the player. */
    fun sendLink(url: String) {
        val clean = url.trim()
        if (clean.isBlank()) return
        sendAttachment(
            ChatAttachment(
                kind = AttachmentKind.LINK,
                title = clean.substringAfter("://").take(48),
                subtitle = clean,
                payload = clean
            )
        )
    }

    fun install(messageId: String) = viewModelScope.launch {
        val share = _state.value.shares[messageId] ?: return@launch
        repository.installSharedInstance(messageId, share)
    }

    fun toggleMusic(messageId: String) {
        _state.update { it.copy(playingMusicId = if (it.playingMusicId == messageId) null else messageId) }
    }

    fun react(messageId: String, emoji: String) = viewModelScope.launch {
        val id = _state.value.friendId ?: return@launch
        repository.toggleReaction(id, messageId, emoji)
    }
}

/* ---------------------------------------------------------------- activity ----- */

data class ActivityUiState(
    val posts: List<ActivityPost> = emptyList(),
    val composerOpen: Boolean = false,
    val composerCaption: String = "",
    val storyOpen: ActivityPost? = null,
    val profileOpen: AerixUser? = null,
    val commentTarget: String? = null,
    val commentDraft: String = ""
)

class SocialActivityViewModel(
    private val repository: SocialRepository,
    private val people: () -> List<AerixUser> = { emptyList() }
) : ViewModel() {
    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.posts.collect { posts -> _state.update { it.copy(posts = posts) } }
        }
    }

    fun openComposer() = _state.update { it.copy(composerOpen = true) }
    fun closeComposer() = _state.update { it.copy(composerOpen = false, composerCaption = "") }
    fun onCaptionChange(value: String) = _state.update { it.copy(composerCaption = value.take(220)) }

    fun publish(kind: PostKind) {
        val caption = _state.value.composerCaption
        viewModelScope.launch {
            repository.addPost(caption.ifBlank { "New post" }, kind)
            _state.update { it.copy(composerOpen = false, composerCaption = "") }
        }
    }

    fun openStory(post: ActivityPost) = _state.update { it.copy(storyOpen = post) }
    fun closeStory() = _state.update { it.copy(storyOpen = null) }

    fun openProfile(userId: String, posts: List<ActivityPost>) {
        val post = posts.firstOrNull { it.authorId == userId }
        val known = people().firstOrNull { it.id == userId }
        val user = known ?: AerixUser(
            id = userId,
            name = post?.authorName ?: "Aerix user",
            avatar = post?.authorAvatar ?: 1,
            hasStory = posts.any { it.authorId == userId && it.kind == PostKind.STORY },
            followers = post?.likes ?: 0,
            posts = posts.count { it.authorId == userId }
        )
        _state.update { it.copy(profileOpen = user) }
    }

    fun closeProfile() = _state.update { it.copy(profileOpen = null) }

    fun toggleLike(postId: String) = viewModelScope.launch { repository.toggleLike(postId) }
    fun toggleFollow(userId: String) = viewModelScope.launch { repository.toggleFollow(userId) }

    fun openComments(postId: String) = _state.update { it.copy(commentTarget = postId, commentDraft = "") }
    fun closeComments() = _state.update { it.copy(commentTarget = null, commentDraft = "") }
    fun onCommentChange(value: String) = _state.update { it.copy(commentDraft = value) }
    fun submitComment() {
        val postId = _state.value.commentTarget ?: return
        val text = _state.value.commentDraft
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.comment(postId, text)
            _state.update { it.copy(commentTarget = null, commentDraft = "") }
        }
    }
}

/* ---------------------------------------------------------------- settings ----- */

data class SettingsUiState(
    val privacy: PrivacySettings = PrivacySettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val chat: ChatSettings = ChatSettings(),
    val mic: MicSettings = MicSettings(),
    /** The accounts actually blocked on this device. */
    val blocked: List<AerixUser> = emptyList()
)

class SocialSettingsViewModel(
    private val store: SocialStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Optional so the settings-only unit tests keep constructing this with a store alone. */
    private val repository: SocialRepository? = null
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        repository?.let { repo ->
            viewModelScope.launch {
                repo.blocked.collect { blocked -> _state.update { it.copy(blocked = blocked) } }
            }
        }
        viewModelScope.launch {
            store.data.collect { persisted ->
                _state.update { current ->
                    // copy(): the blocked list comes from the repository, not from preferences.
                    current.copy(
                        privacy = persisted.privacy,
                        notifications = persisted.notifications,
                        chat = persisted.chat,
                        mic = persisted.mic
                    )
                }
            }
        }
    }

    /** Unblocks for real: the repository drops the account from the blocked list on disk. */
    fun unblock(userId: String) = viewModelScope.launch { repository?.unblock(userId) }

    /** Records the report against the account. */
    fun report(userId: String, reason: String) = viewModelScope.launch {
        repository?.report(userId, reason)
    }

    /* privacy -- */
    fun setOnlineStatus(value: Boolean) = persistPrivacy { it.copy(onlineStatus = value) }
    fun setShareActivity(value: Boolean) = persistPrivacy { it.copy(shareActivity = value) }
    fun setShowServerDetails(value: Boolean) = persistPrivacy { it.copy(showServerDetails = value) }
    fun setProfileLock(value: Boolean) = persistPrivacy { it.copy(profileLock = value) }
    fun setStatus(value: String) = persistPrivacy { it.copy(status = value) }
    fun setFriendRequests(value: String) = persistPrivacy { it.copy(friendRequests = value) }
    fun setPrivateMessages(value: String) = persistPrivacy { it.copy(privateMessages = value) }
    fun setPartyInvites(value: String) = persistPrivacy { it.copy(partyInvites = value) }

    /* notifications -- */
    fun setFriendRequestNotifications(value: Boolean) = persistNotifications { it.copy(friendRequests = value) }
    fun setPrivateMessageNotifications(value: Boolean) = persistNotifications { it.copy(privateMessages = value) }
    fun setPartyInviteNotifications(value: Boolean) = persistNotifications { it.copy(partyInvites = value) }
    fun setServerInviteNotifications(value: Boolean) = persistNotifications { it.copy(serverInvites = value) }
    fun setInLauncher(value: Boolean) = persistNotifications { it.copy(inLauncher = value) }
    fun setInGame(value: Boolean) = persistNotifications { it.copy(inGame = value) }
    fun setMessagePreview(value: Boolean) = persistNotifications { it.copy(messagePreview = value) }
    fun setAndroidNotifications(value: Boolean) = persistNotifications { it.copy(androidNotifications = value) }

    /* chats -- */
    fun setAllowStrangers(value: Boolean) = persistChat { it.copy(allowStrangers = value) }
    fun setAllowFriends(value: Boolean) = persistChat { it.copy(allowFriends = value) }
    fun setAllowCloseFriends(value: Boolean) = persistChat { it.copy(allowCloseFriends = value) }
    fun setAllowParty(value: Boolean) = persistChat { it.copy(allowParty = value) }
    fun setAutoDownloadMedia(value: Boolean) = persistChat { it.copy(autoDownloadMedia = value) }
    fun setReadReceipts(value: Boolean) = persistChat { it.copy(readReceipts = value) }
    fun setKeepMedia(value: Boolean) = persistChat { it.copy(keepMediaOnDevice = value) }

    /* microphone -- */
    fun setMuteOnJoin(value: Boolean) = persistMic { it.copy(muteOnJoin = value) }
    fun setPushToTalk(value: Boolean) = persistMic { it.copy(pushToTalk = value) }
    fun setNoiseSuppression(value: Boolean) = persistMic { it.copy(noiseSuppression = value) }
    fun setEchoCancellation(value: Boolean) = persistMic { it.copy(echoCancellation = value) }
    fun setVoiceActivity(value: Boolean) = persistMic { it.copy(voiceActivity = value) }
    fun setInputVolume(value: Int) = persistMic { it.copy(inputVolume = value.coerceIn(0, 100)) }
    fun setOutputVolume(value: Int) = persistMic { it.copy(outputVolume = value.coerceIn(0, 100)) }

    private fun persistPrivacy(transform: (PrivacySettings) -> PrivacySettings) = viewModelScope.launch {
        withContext(io) { store.update { it.copy(privacy = transform(it.privacy)) } }
    }

    private fun persistNotifications(transform: (NotificationSettings) -> NotificationSettings) = viewModelScope.launch {
        withContext(io) { store.update { it.copy(notifications = transform(it.notifications)) } }
    }

    private fun persistChat(transform: (ChatSettings) -> ChatSettings) = viewModelScope.launch {
        withContext(io) { store.update { it.copy(chat = transform(it.chat)) } }
    }

    private fun persistMic(transform: (MicSettings) -> MicSettings) = viewModelScope.launch {
        withContext(io) { store.update { it.copy(mic = transform(it.mic)) } }
    }
}

/* ---------------------------------------------------------------- in game ----- */

enum class CompactMode { CHAT, VOICE, INVITE, AUTH_REQUIRED }

data class InGameSocialUiState(
    val visible: Boolean = false,
    val mode: CompactMode = CompactMode.CHAT,
    val party: PartyState = PartyState(),
    val invite: ServerInvite? = null,
    val messages: List<ChatMessage> = emptyList(),
    val signedIn: Boolean = false,
    val friends: List<AerixUser> = emptyList(),
    val activeFriendId: String = "kai",
    val activeFriendName: String = "Kai",
    val unread: Int = 0
)

/** Shares the same repository as the launcher shell, so chat and party stay in sync. */
class InGameSocialViewModel(
    private val repository: SocialRepository,
    private val store: SocialStore
) : ViewModel() {
    private val _state = MutableStateFlow(InGameSocialUiState())
    val state: StateFlow<InGameSocialUiState> = _state.asStateFlow()

    /** Ids of every incoming message the player has already seen in the overlay. */
    private val seen = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            // Read state comes back from disk, so a restart does not resurrect the badge.
            seen += runCatching { repository.seenMessages() }.getOrDefault(emptySet())
            val active = _state.value.activeFriendId
            val messages = repository.chats.value[active].orEmpty()
            _state.update { it.copy(unread = messages.count { m -> !m.mine && m.id !in seen }) }
        }
        viewModelScope.launch {
            repository.party.collect { party -> _state.update { it.copy(party = party) } }
        }
        viewModelScope.launch {
            repository.invite.collect { invite -> _state.update { it.copy(invite = invite) } }
        }
        viewModelScope.launch {
            repository.friends.collect { friends -> _state.update { it.copy(friends = friends) } }
        }
        viewModelScope.launch {
            repository.chats.collect { chats ->
                val active = _state.value.activeFriendId
                val messages = chats[active].orEmpty()
                // Anything from someone else that has not been opened yet is a notification.
                val unread = messages.count { !it.mine && it.id !in seen }
                _state.update { it.copy(messages = messages, unread = unread) }
            }
        }
        viewModelScope.launch {
            store.data.collect { persisted -> _state.update { it.copy(signedIn = persisted.signedIn) } }
        }
    }

    /** Switches the overlay to another conversation and marks it as read. */
    fun selectFriend(friend: AerixUser) = viewModelScope.launch {
        repository.ensureChat(friend.id)
        markRead()
        _state.update {
            it.copy(
                activeFriendId = friend.id,
                activeFriendName = friend.name,
                messages = repository.chats.value[friend.id].orEmpty()
            )
        }
    }

    /** Called when the overlay is opened or read: the red badge disappears. */
    fun markRead() = viewModelScope.launch {
        val incoming = repository.chats.value.values.flatten().filterNot { it.mine }
        incoming.forEach { seen += it.id }
        _state.update { it.copy(unread = 0) }
        // Persisted so a restart does not bring the badge back for messages already read.
        repository.markSeen(incoming.map { it.id })
    }

    /** Blocks the account for real, from the in-game overlay. */
    fun block(userId: String) = viewModelScope.launch { repository.block(userId) }

    fun startCall() = viewModelScope.launch {
        repository.startGroupCall()
        _state.update { it.copy(mode = CompactMode.VOICE) }
    }

    fun show(mode: CompactMode) {
        _state.update { it.copy(visible = true, mode = if (it.signedIn) mode else CompactMode.AUTH_REQUIRED) }
    }

    fun hide() {
        _state.update { it.copy(visible = false) }
    }

    fun joinParty() = viewModelScope.launch { repository.createParty() }
    fun leaveParty() = viewModelScope.launch { repository.leaveParty() }
    fun toggleMute() = viewModelScope.launch { repository.toggleMute() }
    fun startGroupCall() = viewModelScope.launch { repository.startGroupCall() }

    fun send(text: String) = viewModelScope.launch {
        val friendId = _state.value.activeFriendId
        repository.ensureChat(friendId)
        repository.sendMessage(friendId, text)
        markRead()
    }

    fun expireInvite() = viewModelScope.launch { repository.expireInvite() }
}
