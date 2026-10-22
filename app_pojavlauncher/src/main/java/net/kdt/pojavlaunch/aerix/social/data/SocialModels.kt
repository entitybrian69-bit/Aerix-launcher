/*
 * Aerix Launcher Social layer.
 *
 * State models for the Social ecosystem: profile, friends, requests, chat (with attachments),
 * activity feed and stories, parties, platform links and the settings that are persisted.
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
    val activity: String? = null,
    val email: String? = null,
    val bio: String = "",
    val links: List<SocialLink> = emptyList(),
    val followers: Int = 0,
    val following: Int = 0,
    val posts: Int = 0,
    val likes: Int = 0,
    val hasStory: Boolean = false,
    val closeFriend: Boolean = false,
    val friend: Boolean = false
)

enum class UserStatus { ONLINE, PLAYING, IN_VOICE, OFFLINE }

data class FriendRequest(
    val id: String,
    val fromName: String,
    val avatar: Int,
    val pending: Boolean = true,
    val fromUserId: String = ""
)

/** Every platform a profile can link to. [iconRes] is resolved in the UI layer. */
enum class LinkPlatform(val label: String, val prefix: String) {
    YOUTUBE("YouTube", "https://youtube.com/"),
    INSTAGRAM("Instagram", "https://instagram.com/"),
    DISCORD("Discord", "https://discord.gg/"),
    X("X", "https://x.com/"),
    TIKTOK("TikTok", "https://tiktok.com/@"),
    TWITCH("Twitch", "https://twitch.tv/"),
    WEB("Website", "https://")
}

data class SocialLink(val platform: LinkPlatform, val value: String) {
    val url: String
        get() = if (value.startsWith("http://") || value.startsWith("https://")) value else platform.prefix + value
}

/** Anything that can travel inside a chat message. */
enum class AttachmentKind { IMAGE, GIF, FILE, LINK, INSTANCE, MUSIC }

data class ChatAttachment(
    val kind: AttachmentKind,
    val title: String,
    val subtitle: String = "",
    /** Instance id, URL or track id depending on [kind]. */
    val payload: String = "",
    val sizeLabel: String = "",
    /** File copied into the app's own storage, for media that really lives on this device. */
    val localPath: String? = null,
    val mimeType: String? = null
)

data class ChatMessage(
    val id: String,
    val author: String,
    val text: String,
    val mine: Boolean,
    val timestamp: String,
    val reactions: Map<String, Int> = emptyMap(),
    val myReaction: String? = null,
    val attachments: List<ChatAttachment> = emptyList()
)

/** State of a shared instance that is being downloaded into the launcher. */
enum class InstanceShareState { IDLE, DOWNLOADING, READY }

data class InstanceShare(
    val messageId: String,
    val name: String,
    val version: String,
    val loader: String,
    val state: InstanceShareState = InstanceShareState.IDLE
)

data class PartyMember(
    val id: String,
    val name: String,
    val avatar: Int,
    val muted: Boolean = false,
    val speaking: Boolean = false,
    val isYou: Boolean = false
)

/**
 * A voice party / group chat. [maxMembers] is 0 for "no limit": the group always needs at
 * least three people to be worth starting, which the UI explains.
 */
data class PartyState(
    val joined: Boolean = false,
    val name: String = "",
    val logo: Int = 3,
    val inviteOnly: Boolean = true,
    val connecting: Boolean = false,
    val reconnecting: Boolean = false,
    val inCall: Boolean = false,
    val callMuted: Boolean = false,
    val members: List<PartyMember> = emptyList(),
    val muted: Boolean = false,
    val deafened: Boolean = false
) {
    companion object {
        const val MIN_MEMBERS = 3
    }
}

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

/* ------------------------------------------------------------------ activity ----- */

enum class PostKind { STORY, PHOTO, VIDEO, TEXT }

data class ActivityComment(val id: String, val author: String, val text: String)

data class ActivityPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: Int,
    val caption: String,
    val kind: PostKind = PostKind.PHOTO,
    val accentSeed: Int = 0,
    val likes: Int = 0,
    val liked: Boolean = false,
    val comments: List<ActivityComment> = emptyList(),
    val hoursAgo: Int = 1,
    val following: Boolean = false
)

/* ------------------------------------------------------------------ settings ----- */

/** Who is allowed to reach the account. */
data class PrivacySettings(
    val onlineStatus: Boolean = true,
    val shareActivity: Boolean = true,
    val showServerDetails: Boolean = false,
    val profileLock: Boolean = false,
    val status: String = "Available to play",
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

/** Chat rules: who may start a conversation and what media is fetched automatically. */
data class ChatSettings(
    val allowStrangers: Boolean = false,
    val allowFriends: Boolean = true,
    val allowCloseFriends: Boolean = true,
    val allowParty: Boolean = true,
    val autoDownloadMedia: Boolean = true,
    val readReceipts: Boolean = true,
    val keepMediaOnDevice: Boolean = true
)

data class MicSettings(
    val muteOnJoin: Boolean = true,
    val pushToTalk: Boolean = true,
    val noiseSuppression: Boolean = true,
    val echoCancellation: Boolean = true,
    val inputVolume: Int = 70,
    val outputVolume: Int = 80,
    val voiceActivity: Boolean = true
)

/** Everything the launcher writes to DataStore. */
data class SocialPersisted(
    val signedIn: Boolean = false,
    val name: String = "",
    val username: String = "",
    val usernameChangedAt: Long = 0L,
    val bio: String = "",
    val links: List<SocialLink> = emptyList(),
    val avatar: Int = 6,
    val skinId: String = "skin_1",
    val capeId: String = "cape_none",
    val themeWallpaperId: String = "",
    val privacy: PrivacySettings = PrivacySettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val chat: ChatSettings = ChatSettings(),
    val mic: MicSettings = MicSettings()
) {
    /** Usernames may only be changed once a week. */
    fun usernameChangeAvailableAt(): Long = usernameChangedAt + USERNAME_COOLDOWN_MS

    fun canChangeUsername(now: Long): Boolean = now >= usernameChangeAvailableAt()

    companion object {
        const val USERNAME_COOLDOWN_MS = 7L * 24L * 60L * 60L * 1000L
    }
}

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

/** A user returned by the "add friend" search. */
data class UserSearchResult(
    val user: AerixUser,
    val matchedBy: MatchField,
    val requestSent: Boolean = false
)

enum class MatchField { USERNAME, EMAIL }

/** 3..16 letters, numbers or underscores, the rule shown on the appearance screen. */
fun validateAerixName(name: String): String? = when {
    name.isBlank() -> "Enter a name to continue"
    name.length < 3 -> "Use at least 3 characters"
    name.length > 16 -> "Use at most 16 characters"
    !name.matches(Regex("[A-Za-z0-9_]+")) -> "Only letters, numbers and underscores"
    else -> null
}

/** Pretty "3d 4h" countdown for the username cooldown. */
fun formatCooldown(remainingMs: Long): String {
    if (remainingMs <= 0) return "available now"
    val totalMinutes = remainingMs / 60000L
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
