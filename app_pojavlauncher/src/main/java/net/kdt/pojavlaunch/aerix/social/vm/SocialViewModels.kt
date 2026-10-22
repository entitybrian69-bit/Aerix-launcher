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
    private val repository: SocialRepository? = null
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
            _state.update { it.copy(signInInProgress = true, googleError = null) }
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
            onDone(outcome)
        }

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
        repository?.clearAll()
        withContext(io) { store.update { SocialPersisted() } }
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

    fun snapshot(): SocialPersisted = SocialPersisted(
        signedIn = _state.value.signedIn,
        email = _state.value.email,
        googleClientId = _state.value.googleClientId,
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
