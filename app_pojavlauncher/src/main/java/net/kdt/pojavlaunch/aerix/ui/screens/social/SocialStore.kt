/*
 * Aerix Launcher — Social session model. In-memory only: friends, presence, party,
 * chats and moderation lists are simulated until the Aerix account backend exists.
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

package net.kdt.pojavlaunch.aerix.ui.screens.social

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Presence shown next to a friend. The wording is the one used by the approved mockups. */
enum class SocialPresence(val label: String) {
    MINECRAFT_RUNNING("Minecraft running"),
    ONLINE_IN_AERIX("Online in Aerix"),
    IN_A_VOICE_PARTY("In a voice party"),
    IN_LAUNCHER("Online in launcher"),
    AWAY("Away"),
    OFFLINE("Offline")
}

/** A friend or request shown in the Social hub. [avatarColor] seeds the mock avatar tint. */
data class SocialFriend(
    val name: String,
    val presence: SocialPresence,
    val online: Boolean = true,
    val avatarColor: Long = 0L
)

/**
 * A single chat line. [fromMe] decides the side of the thread; reactions are emoji counts
 * kept in a snapshot list so adding one recomposes the bubble.
 */
class SocialMessage(
    val fromMe: Boolean,
    val text: String,
    initialReactions: List<Pair<String, Int>> = emptyList()
) {
    val reactions = mutableStateListOf<Pair<String, Int>>().apply { addAll(initialReactions) }
}

/** One conversation, keyed by the friend's Aerix name. */
class SocialConversation(val name: String, initial: List<SocialMessage>) {
    val messages = mutableStateListOf<SocialMessage>().apply { addAll(initial) }
}

/** An incoming or outgoing friend request. */
data class SocialRequest(val name: String, val incoming: Boolean)

/** The party the user is in. Voice is optional and starts disconnected. */
class SocialParty(val name: String) {
    val members = mutableStateListOf<String>()
    var voiceConnected by mutableStateOf(false)
}

/** Aerix names are 3 to 16 letters, numbers, or underscores. */
fun isValidAerixName(name: String): Boolean =
    name.length in 3..16 && name.all { it.isLetterOrDigit() || it == '_' }

/** The report reasons offered in the report flow, in the approved order. */
val SocialReportReasons = listOf(
    "Harassment or bullying",
    "Spam or advertising",
    "Inappropriate name or skin",
    "Scam or impersonation",
    "Something else"
)

private fun mockFriends() = listOf(
    SocialFriend("Kai", SocialPresence.MINECRAFT_RUNNING, true, 0xFF6C8CFF),
    SocialFriend("Mira", SocialPresence.IN_A_VOICE_PARTY, true, 0xFFB58CFF),
    SocialFriend("Luna", SocialPresence.ONLINE_IN_AERIX, true, 0xFF71E0C8),
    SocialFriend("Alex", SocialPresence.MINECRAFT_RUNNING, true, 0xFFFFB37A),
    SocialFriend("Sam", SocialPresence.OFFLINE, false, 0xFF9AA7B4)
)

private fun mockRequests() = listOf(
    SocialRequest("Ash", incoming = true),
    SocialRequest("Rowan", incoming = true)
)

private fun mockConversations() = listOf(
    SocialConversation(
        "Kai",
        listOf(
            SocialMessage(false, "Survival tonight?"),
            SocialMessage(true, "Yeah, after 8."),
            SocialMessage(false, "I'll send a party invite.")
        )
    ),
    SocialConversation(
        "Mira",
        listOf(
            SocialMessage(false, "You joining Alex's world?"),
            SocialMessage(true, "In a bit, finishing this modpack update.")
        )
    )
)

/**
 * Session-only social data. Nothing is persisted or sent anywhere; saving is wired to
 * the Aerix account service when the backend exists.
 */
object SocialHubStore {
    val friends = mutableStateListOf<SocialFriend>().apply { addAll(mockFriends()) }
    val requests = mutableStateListOf<SocialRequest>().apply { addAll(mockRequests()) }
    val conversations = mutableStateListOf<SocialConversation>().apply { addAll(mockConversations()) }
    /** Null means "Not connected", the state shown by the approved hub mockup. */
    val party = mutableStateOf<SocialParty?>(null)
    val blocked = mutableStateListOf<String>()
    val reported = mutableStateListOf<String>()

    /** Selected hub tab: 0 Friends, 1 Chats, 2 Party. */
    var hubTab by mutableStateOf(0)

    /** Friends-list state: friends, empty, or unavailable. */
    var state by mutableStateOf(SocialHubState.FRIENDS)

    val onlineCount: Int get() = friends.count { it.online }

    fun friend(name: String): SocialFriend? = friends.firstOrNull { it.name == name }

    fun conversation(name: String): SocialConversation? =
        conversations.firstOrNull { it.name == name }

    fun accept(request: SocialRequest) {
        requests.remove(request)
        if (friends.none { it.name == request.name }) {
            friends.add(SocialFriend(request.name, SocialPresence.ONLINE_IN_AERIX, true))
        }
    }

    fun decline(request: SocialRequest) {
        requests.remove(request)
    }

    fun invite(name: String) {
        val current = party.value ?: return
        if (name !in current.members) current.members.add(name)
    }

    fun removeFriend(name: String) {
        friends.removeAll { it.name == name }
        conversations.removeAll { it.name == name }
        requests.removeAll { it.name == name }
        party.value?.members?.remove(name)
    }

    fun block(name: String) {
        if (name !in blocked) blocked.add(name)
        removeFriend(name)
    }

    fun unblock(name: String) {
        blocked.remove(name)
    }

    fun report(name: String) {
        if (name !in reported) reported.add(name)
    }

    fun sendMessage(name: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val conversation = conversation(name)
            ?: SocialConversation(name, emptyList()).also { conversations.add(it) }
        conversation.messages.add(SocialMessage(fromMe = true, text = trimmed))
    }

    fun react(name: String, index: Int, emoji: String) {
        val message = conversation(name)?.messages?.getOrNull(index) ?: return
        val existing = message.reactions.indexOfFirst { it.first == emoji }
        if (existing >= 0) {
            val count = message.reactions[existing].second + 1
            message.reactions[existing] = emoji to count
        } else {
            message.reactions.add(emoji to 1)
        }
    }

    fun lastMessage(name: String): String =
        conversation(name)?.messages?.lastOrNull()?.text ?: "No messages yet"

    fun createParty(name: String = "Survival crew") {
        party.value = SocialParty(name).apply { members.add(SocialProfileStore.aerixName) }
    }

    fun leaveParty() {
        party.value = null
    }

    /** Restores the mock session data. Used by tests and after signing out. */
    fun reset() {
        friends.clear()
        friends.addAll(mockFriends())
        requests.clear()
        requests.addAll(mockRequests())
        conversations.clear()
        conversations.addAll(mockConversations())
        party.value = null
        blocked.clear()
        reported.clear()
        hubTab = 0
        state = SocialHubState.FRIENDS
    }
}

/** The three hub states from the approved designs. */
enum class SocialHubState { FRIENDS, EMPTY, UNAVAILABLE }

/** Profile data for the signed-in Aerix account. Session only until the backend exists. */
object SocialProfileStore {
    var aerixName by mutableStateOf("Nova")
    var skinName by mutableStateOf("Current skin")
    var capeName by mutableStateOf("None")
    var signedIn by mutableStateOf(false)
    var profileReady by mutableStateOf(false)
    var googleEmail by mutableStateOf<String?>(null)
    /** 0 shows the front of the model, 180 the back. */
    var previewAzimuth by mutableStateOf(20)

    fun signIn(email: String) {
        signedIn = true
        googleEmail = email
    }

    fun signOut() {
        signedIn = false
        profileReady = false
        googleEmail = null
        previewAzimuth = 20
    }
}

/** Target of the friend profile, chat, report and block screens. */
object SocialNavStore {
    var friendName by mutableStateOf<String?>(null)
}
