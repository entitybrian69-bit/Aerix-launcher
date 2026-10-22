/*
 * Aerix Launcher Social layer.
 *
 * ViewModels. Each one owns a StateFlow of immutable UI state, receives events from the
 * Composables and performs every mutation in viewModelScope on Dispatchers.IO.
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
import net.kdt.pojavlaunch.aerix.social.data.AerixUser
import net.kdt.pojavlaunch.aerix.social.data.ChatMessage
import net.kdt.pojavlaunch.aerix.social.data.FriendRequest
import net.kdt.pojavlaunch.aerix.social.data.NotificationSettings
import net.kdt.pojavlaunch.aerix.social.data.PartyState
import net.kdt.pojavlaunch.aerix.social.data.PrivacySettings
import net.kdt.pojavlaunch.aerix.social.data.ServerInvite
import net.kdt.pojavlaunch.aerix.social.data.SocialConnection
import net.kdt.pojavlaunch.aerix.social.data.SocialPersisted
import net.kdt.pojavlaunch.aerix.social.data.SocialRepository
import net.kdt.pojavlaunch.aerix.social.data.SocialStore
import net.kdt.pojavlaunch.aerix.social.data.validateAerixName

/* ------------------------------------------------------------------ shell ----- */

enum class SocialRoute { AUTH, APPEARANCE, DASHBOARD, CHAT, PROFILE, PRIVACY, NOTIFICATIONS, SAFETY, SKIN, CAPE, VOICE }

enum class SocialTab { FRIENDS, CHATS, PARTY }

/** Navigation and open/closed state of the second launcher. */
class SocialShellViewModel : ViewModel() {
    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    private val _route = MutableStateFlow(SocialRoute.AUTH)
    val route: StateFlow<SocialRoute> = _route.asStateFlow()

    private val _tab = MutableStateFlow(SocialTab.FRIENDS)
    val tab: StateFlow<SocialTab> = _tab.asStateFlow()

    private val stack = ArrayDeque<SocialRoute>()

    fun open(signedIn: Boolean) {
        stack.clear()
        _route.value = if (signedIn) SocialRoute.DASHBOARD else SocialRoute.AUTH
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

    /** Replaces the current root route, used by sign-in and the appearance setup. */
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

    fun selectTab(value: SocialTab) {
        _tab.value = value
        stack.clear()
        _route.value = SocialRoute.DASHBOARD
    }
}

/* ---------------------------------------------------------------- profile ----- */

data class ProfileUiState(
    val signedIn: Boolean = false,
    val name: String = "",
    val skinId: String = "skin_1",
    val capeId: String = "cape_none",
    val nameError: String? = null,
    val signInInProgress: Boolean = false,
    val saved: Boolean = false
)

class SocialProfileViewModel(
    private val store: SocialStore,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.data.collect { persisted ->
                _state.update {
                    it.copy(
                        signedIn = persisted.signedIn,
                        name = persisted.name,
                        skinId = persisted.skinId,
                        capeId = persisted.capeId
                    )
                }
            }
        }
    }

    fun signIn() = viewModelScope.launch {
        _state.update { it.copy(signInInProgress = true) }
        withContext(io) { store.update { it.copy(signedIn = true) } }
        _state.update { it.copy(signInInProgress = false) }
    }

    fun signOut() = viewModelScope.launch {
        withContext(io) { store.update { it.copy(signedIn = false) } }
    }

    fun onNameChange(value: String) {
        _state.update { it.copy(name = value.take(16), nameError = null, saved = false) }
    }

    fun selectSkin(id: String) = viewModelScope.launch {
        withContext(io) { store.update { it.copy(skinId = id) } }
    }

    fun selectCape(id: String) = viewModelScope.launch {
        withContext(io) { store.update { it.copy(capeId = id) } }
    }

    /** Validates and persists the appearance. Returns true when the caller may move on. */
    fun saveAppearance(onSaved: () -> Unit = {}): Boolean {
        val name = _state.value.name.trim()
        val error = validateAerixName(name)
        if (error != null) {
            _state.update { it.copy(nameError = error) }
            return false
        }
        viewModelScope.launch {
            withContext(io) {
                store.update { it.copy(name = name, signedIn = true) }
            }
            _state.update { it.copy(saved = true, nameError = null, name = name) }
            onSaved()
        }
        return true
    }

    fun snapshot(): SocialPersisted = SocialPersisted(
        signedIn = _state.value.signedIn,
        name = _state.value.name,
        skinId = _state.value.skinId,
        capeId = _state.value.capeId
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
)

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
    fun createParty() = viewModelScope.launch { repository.createParty() }
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

/* ------------------------------------------------------------------- chat ----- */

data class ChatUiState(
    val friendId: String? = null,
    val friendName: String = "",
    val friendAvatar: Int = 1,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = ""
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
    }

    fun open(friend: AerixUser) = viewModelScope.launch {
        repository.ensureChat(friend.id)
        // Seed the existing history immediately: the chats flow only emits on the next change,
        // so without this a chat with history would open empty.
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

    fun react(messageId: String, emoji: String) = viewModelScope.launch {
        val id = _state.value.friendId ?: return@launch
        repository.toggleReaction(id, messageId, emoji)
    }
}

/* --------------------------------------------------------------- settings ----- */

data class SettingsUiState(
    val privacy: PrivacySettings = PrivacySettings(),
    val notifications: NotificationSettings = NotificationSettings()
)

class SocialSettingsViewModel(
    private val store: SocialStore,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.data.collect { persisted ->
                _state.value = SettingsUiState(persisted.privacy, persisted.notifications)
            }
        }
    }

    fun setOnlineStatus(value: Boolean) = persistPrivacy { it.copy(onlineStatus = value) }
    fun setShareActivity(value: Boolean) = persistPrivacy { it.copy(shareActivity = value) }
    fun setShowServerDetails(value: Boolean) = persistPrivacy { it.copy(showServerDetails = value) }
    fun setFriendRequests(value: String) = persistPrivacy { it.copy(friendRequests = value) }
    fun setPrivateMessages(value: String) = persistPrivacy { it.copy(privateMessages = value) }
    fun setPartyInvites(value: String) = persistPrivacy { it.copy(partyInvites = value) }

    fun setFriendRequestNotifications(value: Boolean) = persistNotifications { it.copy(friendRequests = value) }
    fun setPrivateMessageNotifications(value: Boolean) = persistNotifications { it.copy(privateMessages = value) }
    fun setPartyInviteNotifications(value: Boolean) = persistNotifications { it.copy(partyInvites = value) }
    fun setServerInviteNotifications(value: Boolean) = persistNotifications { it.copy(serverInvites = value) }
    fun setInLauncher(value: Boolean) = persistNotifications { it.copy(inLauncher = value) }
    fun setInGame(value: Boolean) = persistNotifications { it.copy(inGame = value) }
    fun setMessagePreview(value: Boolean) = persistNotifications { it.copy(messagePreview = value) }
    fun setAndroidNotifications(value: Boolean) = persistNotifications { it.copy(androidNotifications = value) }

    private fun persistPrivacy(transform: (PrivacySettings) -> PrivacySettings) = viewModelScope.launch {
        withContext(io) { store.update { it.copy(privacy = transform(it.privacy)) } }
    }

    private fun persistNotifications(transform: (NotificationSettings) -> NotificationSettings) = viewModelScope.launch {
        withContext(io) { store.update { it.copy(notifications = transform(it.notifications)) } }
    }
}

/* --------------------------------------------------------------- in game ----- */

enum class CompactMode { CHAT, VOICE, INVITE, AUTH_REQUIRED }

data class InGameSocialUiState(
    val visible: Boolean = false,
    val mode: CompactMode = CompactMode.CHAT,
    val party: PartyState = PartyState(),
    val invite: ServerInvite? = null,
    val messages: List<ChatMessage> = emptyList(),
    val signedIn: Boolean = false
)

/** Shares the same repository as the launcher shell, so chat and party stay in sync. */
class InGameSocialViewModel(
    private val repository: SocialRepository,
    private val store: SocialStore
) : ViewModel() {
    private val _state = MutableStateFlow(InGameSocialUiState())
    val state: StateFlow<InGameSocialUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.party.collect { party -> _state.update { it.copy(party = party) } }
        }
        viewModelScope.launch {
            repository.invite.collect { invite -> _state.update { it.copy(invite = invite) } }
        }
        viewModelScope.launch {
            repository.chats.collect { chats ->
                _state.update { it.copy(messages = chats["kai"].orEmpty()) }
            }
        }
        viewModelScope.launch {
            store.data.collect { persisted -> _state.update { it.copy(signedIn = persisted.signedIn) } }
        }
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

    fun send(text: String) = viewModelScope.launch {
        repository.ensureChat("kai")
        repository.sendMessage("kai", text)
    }

    fun expireInvite() = viewModelScope.launch { repository.expireInvite() }
}
