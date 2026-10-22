/*
 * Aerix Launcher Social layer.
 *
 * State models for the Social ecosystem: profile, friends, requests, chat, parties and the
 * privacy / notification settings that are persisted with DataStore.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.data

/** A person the launcher knows about. [avatar] indexes aerix_social_avatar_1..6. */
data class AerixUser(
    val id: String,
    val name: String,
    val avatar: Int = 1,
    val status: UserStatus = UserStatus.ONLINE,
    val activity: String? = null
)

enum class UserStatus { ONLINE, PLAYING, IN_VOICE, OFFLINE }

data class FriendRequest(
    val id: String,
    val fromName: String,
    val avatar: Int,
    val pending: Boolean = true
)

data class ChatMessage(
    val id: String,
    val author: String,
    val text: String,
    val mine: Boolean,
    val timestamp: String,
    val reactions: Map<String, Int> = emptyMap(),
    val myReaction: String? = null
)

data class PartyMember(
    val id: String,
    val name: String,
    val avatar: Int,
    val muted: Boolean = false,
    val speaking: Boolean = false,
    val isYou: Boolean = false
)

data class PartyState(
    val joined: Boolean = false,
    val name: String = "",
    val inviteOnly: Boolean = true,
    val connecting: Boolean = false,
    val reconnecting: Boolean = false,
    val members: List<PartyMember> = emptyList(),
    val muted: Boolean = false,
    val deafened: Boolean = false
)

enum class ServerInviteState { ACTIVE, EXPIRED }

data class ServerInvite(
    val fromName: String,
    val avatar: Int = 1,
    val state: ServerInviteState = ServerInviteState.ACTIVE,
    val requiredVersion: String = "1.21.1",
    val requiredLoader: String = "Fabric",
    val currentVersion: String = "1.20.1",
    val currentLoader: String = "Forge"
) {
    val matchesInstance: Boolean
        get() = requiredVersion == currentVersion && requiredLoader == currentLoader
}

/** Who is allowed to reach the account. */
data class PrivacySettings(
    val onlineStatus: Boolean = true,
    val shareActivity: Boolean = true,
    val showServerDetails: Boolean = false,
    val friendRequests: String = "Everyone",
    val privateMessages: String = "Accepted friends only",
    val partyInvites: String = "Accepted friends only"
)

data class NotificationSettings(
    val friendRequests: Boolean = true,
    val privateMessages: Boolean = true,
    val partyInvites: Boolean = true,
    val serverInvites: Boolean = true,
    val inLauncher: Boolean = true,
    val inGame: Boolean = true,
    val messagePreview: Boolean = false,
    val androidNotifications: Boolean = false
)

/** Everything the launcher writes to DataStore. */
data class SocialPersisted(
    val signedIn: Boolean = false,
    val name: String = "",
    val skinId: String = "skin_1",
    val capeId: String = "cape_none",
    val privacy: PrivacySettings = PrivacySettings(),
    val notifications: NotificationSettings = NotificationSettings()
)

enum class SocialConnection { OK, LOADING, ERROR }

/** Skins and capes offered by the appearance pickers. */
object SocialWardrobe {
    val skins: List<WardrobeItem> = listOf(
        WardrobeItem("skin_1", "Current skin", 1),
        WardrobeItem("skin_2", "Blonde", 2),
        WardrobeItem("skin_3", "Red hoodie", 3),
        WardrobeItem("skin_4", "Void", 4),
        WardrobeItem("skin_5", "Creeper", 5),
        WardrobeItem("skin_6", "Frost", 6)
    )

    val capes: List<WardrobeItem> = listOf(
        WardrobeItem("cape_none", "None", 0),
        WardrobeItem("cape_aerix", "Aerix cape", 1),
        WardrobeItem("cape_violet", "Saved cape", 2),
        WardrobeItem("cape_aurora", "Saved cape", 3)
    )

    fun skinName(id: String): String = skins.firstOrNull { it.id == id }?.label ?: "Current skin"
    fun capeName(id: String): String = capes.firstOrNull { it.id == id }?.label ?: "None"
}

data class WardrobeItem(val id: String, val label: String, val avatar: Int)

/** 3..16 letters, numbers or underscores, the rule shown on the appearance screen. */
fun validateAerixName(name: String): String? = when {
    name.isBlank() -> "Enter a name to continue"
    name.length < 3 -> "Use at least 3 characters"
    name.length > 16 -> "Use at most 16 characters"
    !name.matches(Regex("[A-Za-z0-9_]+")) -> "Only letters, numbers and underscores"
    else -> null
}
