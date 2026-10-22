/*
 * Aerix Launcher Social layer.
 *
 * Repository contract and the in-memory mock backend. The ViewModels never touch the
 * repository's state directly; they read the StateFlows and call the suspend functions,
 * which run on Dispatchers.IO and emit new state.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.UUID

interface SocialRepository {
    val friends: StateFlow<List<AerixUser>>
    val requests: StateFlow<List<FriendRequest>>
    val chats: StateFlow<Map<String, List<ChatMessage>>>
    val party: StateFlow<PartyState>
    val connection: StateFlow<SocialConnection>
    val blocked: StateFlow<List<AerixUser>>
    val invite: StateFlow<ServerInvite?>

    suspend fun refresh()
    suspend fun acceptRequest(id: String)
    suspend fun declineRequest(id: String)
    suspend fun addFriend(query: String): Boolean
    suspend fun ensureChat(friendId: String)
    suspend fun sendMessage(friendId: String, text: String)
    suspend fun toggleReaction(friendId: String, messageId: String, emoji: String)
    suspend fun createParty()
    suspend fun leaveParty()
    suspend fun toggleMute()
    suspend fun block(userId: String)
    suspend fun report(userId: String, reason: String)
    suspend fun expireInvite()
}

/**
 * Mock backend. Everything is in memory and every mutation is delayed, so the loading
 * states of the UI are real instead of decorative.
 */
class MockSocialRepository(
    private val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : SocialRepository {
    private val _friends = MutableStateFlow(
        listOf(
            AerixUser("kai", "Kai", 1, UserStatus.PLAYING, "Minecraft running"),
            AerixUser("luna", "Luna", 2, UserStatus.ONLINE, "Online in Aerix"),
            AerixUser("alex", "Alex", 3, UserStatus.IN_VOICE, "In a voice party"),
            AerixUser("frost", "FrostByte", 4, UserStatus.ONLINE, "Online in Aerix"),
            AerixUser("echo", "EchoFox", 5, UserStatus.OFFLINE, "Offline")
        )
    )
    private val _requests = MutableStateFlow(
        listOf(
            FriendRequest("req_kai", "Kai", 1),
            FriendRequest("req_nova", "Nova", 6)
        )
    )
    private val _party = MutableStateFlow(PartyState())
    private val _connection = MutableStateFlow(SocialConnection.OK)
    private val _blocked = MutableStateFlow(listOf(AerixUser("frost", "FrostByte", 4), AerixUser("echo", "EchoFox", 5)))
    private val _invite = MutableStateFlow(ServerInvite(fromName = "Kai", avatar = 1))
    private val _chats = MutableStateFlow(
        mapOf(
            "kai" to listOf(
                ChatMessage("m1", "Kai", "Want to play together?", false, "10:41 AM"),
                ChatMessage("m2", "You", "Sure!", true, "10:42 AM"),
                ChatMessage("m3", "Kai", "I'll send you a server invite.", false, "10:45 AM"),
                ChatMessage("m4", "You", "Ready when you are.", true, "10:46 AM")
            )
        )
    )

    override val friends: StateFlow<List<AerixUser>> = _friends.asStateFlow()
    override val requests: StateFlow<List<FriendRequest>> = _requests.asStateFlow()
    override val chats: StateFlow<Map<String, List<ChatMessage>>> = _chats.asStateFlow()
    override val party: StateFlow<PartyState> = _party.asStateFlow()
    override val connection: StateFlow<SocialConnection> = _connection.asStateFlow()
    override val blocked: StateFlow<List<AerixUser>> = _blocked.asStateFlow()
    override val invite: StateFlow<ServerInvite?> = _invite.asStateFlow()

    override suspend fun refresh() = withContext(io) {
        _connection.value = SocialConnection.LOADING
        delay(350)
        _connection.value = SocialConnection.OK
    }

    override suspend fun acceptRequest(id: String) = withContext(io) {
        val request = _requests.value.firstOrNull { it.id == id } ?: return@withContext
        delay(120)
        _requests.update { list -> list.filterNot { it.id == id } }
        _friends.update { list ->
            if (list.any { it.name.equals(request.fromName, true) }) list
            else list + AerixUser(request.fromName.lowercase(), request.fromName, request.avatar, UserStatus.ONLINE, "Online in Aerix")
        }
    }

    override suspend fun declineRequest(id: String) = withContext(io) {
        delay(120)
        _requests.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun addFriend(query: String): Boolean = withContext(io) {
        val name = query.trim()
        if (name.length < 3) return@withContext false
        delay(200)
        if (_requests.value.none { it.fromName.equals(name, true) }) {
            _requests.update { it + FriendRequest("req_${UUID.randomUUID()}", name, 6) }
        }
        true
    }

    override suspend fun ensureChat(friendId: String) = withContext(io) {
        if (_chats.value[friendId] == null) {
            _chats.update { it + (friendId to emptyList()) }
        }
    }

    override suspend fun sendMessage(friendId: String, text: String) = withContext(io) {
        val body = text.trim()
        if (body.isEmpty()) return@withContext
        delay(80)
        val message = ChatMessage(UUID.randomUUID().toString(), "You", body, true, now())
        _chats.update { chats -> chats + (friendId to (chats[friendId].orEmpty() + message)) }
    }

    override suspend fun toggleReaction(friendId: String, messageId: String, emoji: String) = withContext(io) {
        delay(60)
        _chats.update { chats ->
            val messages = chats[friendId].orEmpty().map { message ->
                if (message.id != messageId) return@map message
                val counts = message.reactions.toMutableMap()
                var mine = message.myReaction
                if (mine == emoji) {
                    counts[emoji] = (counts[emoji] ?: 1) - 1
                    if (counts[emoji]!! <= 0) counts.remove(emoji)
                    mine = null
                } else {
                    mine?.let { previous ->
                        counts[previous] = (counts[previous] ?: 1) - 1
                        if (counts[previous]!! <= 0) counts.remove(previous)
                    }
                    counts[emoji] = (counts[emoji] ?: 0) + 1
                    mine = emoji
                }
                message.copy(reactions = counts, myReaction = mine)
            }
            chats + (friendId to messages)
        }
    }

    override suspend fun createParty() = withContext(io) {
        delay(180)
        _party.value = PartyState(
            joined = true, name = "Nova's party", connecting = true,
            members = listOf(PartyMember("you", "Nova (You)", 6, isYou = true))
        )
        delay(400)
        _party.update {
            it.copy(
                connecting = false,
                members = listOf(
                    PartyMember("you", "Nova (You)", 6, muted = true, isYou = true),
                    PartyMember("kai", "Kai", 1),
                    PartyMember("luna", "Luna", 2, muted = true),
                    PartyMember("alex", "Alex", 3, muted = true)
                )
            )
        }
    }

    override suspend fun leaveParty() = withContext(io) {
        delay(100)
        _party.value = PartyState()
    }

    override suspend fun toggleMute() = withContext(io) {
        _party.update { it.copy(muted = !it.muted) }
    }

    override suspend fun block(userId: String) = withContext(io) {
        delay(120)
        _friends.update { list -> list.filterNot { it.id == userId } }
        _blocked.update { list -> list + _friends.value.filter { it.id == userId } }
    }

    override suspend fun report(userId: String, reason: String) = withContext(io) {
        delay(200)
    }

    override suspend fun expireInvite() = withContext(io) {
        _invite.update { it?.copy(state = ServerInviteState.EXPIRED) }
    }

    private fun now(): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR).let { if (it == 0) 12 else it }
        val minute = calendar.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
        val suffix = if (calendar.get(java.util.Calendar.AM_PM) == 0) "AM" else "PM"
        return "$hour:$minute $suffix"
    }
}
