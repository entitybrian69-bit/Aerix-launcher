package net.kdt.pojavlaunch.aerix.social

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit

/** One other player, as the friends list and presence layer show them. */
data class SocialFriend(
    val id: String,
    val mcUsername: String,
    val displayName: String,
    val lastSeenMs: Long,
    val pendingIncoming: Boolean = false,
    val pendingOutgoing: Boolean = false,
    val username: String? = null,
    val avatarUrl: String? = null,
    val statusLine: String? = null,
    val mcVersion: String? = null,
    val joinable: Boolean = false,
    val lookingToPlay: Boolean = false,
    val nickname: String? = null,
    val pinned: Boolean = false,
    val mutualCount: Int = 0,
    val mutualNames: List<String> = emptyList(),
    val role: SocialRole? = null,
    val presenceMode: SocialPresenceMode = SocialPresenceMode.ONLINE
) {
    /** Online when a heartbeat arrived in the last two minutes and not appearing offline. */
    val online: Boolean
        get() = isOnlineAt(System.currentTimeMillis())

    fun isOnlineAt(nowMs: Long = System.currentTimeMillis()): Boolean =
        presenceMode != SocialPresenceMode.INVISIBLE &&
            lastSeenMs > 0L &&
            nowMs - lastSeenMs < 2 * 60 * 1000L

    /** Active availability mode when online (`ONLINE`, `IDLE`, or `DND`), or null when offline. */
    fun activePresenceMode(nowMs: Long = System.currentTimeMillis()): SocialPresenceMode? =
        if (isOnlineAt(nowMs)) presenceMode else null

    /** Clean display name stripped of any wire role suffix. */
    val cleanDisplayName: String
        get() = SocialRole.stripRoleTags(displayName) ?: displayName

    /** Resolved role from either explicit `role` or wire `displayName`. */
    val effectiveRole: SocialRole?
        get() = role ?: SocialRole.extract(displayName).second

    /** Primary display label honouring any user-set nickname first. */
    val effectiveName: String
        get() = nickname?.takeIf { it.isNotBlank() } ?: cleanDisplayName

    /** Friendly presence or relative last-seen copy ("Do Not Disturb · Playing 1.21.4" or "Last seen 2 h ago"). */
    fun presenceSummary(nowMs: Long = System.currentTimeMillis()): String {
        if (pendingIncoming) return "Sent you a friend request"
        if (pendingOutgoing) return "Friend request pending"
        val parsed = SocialPresenceCodec.parse(statusLine, presenceMode.key)
        val cleanRich = parsed.customText?.takeIf { it.isNotEmpty() }
        if (isOnlineAt(nowMs)) {
            val detail = cleanRich
                ?: mcVersion?.takeIf { it.isNotBlank() }?.let { "Playing $it" }
                ?: if (lookingToPlay) "Looking to play" else null
            return when (parsed.mode) {
                SocialPresenceMode.DND -> if (detail != null) "Do Not Disturb · $detail" else "Do Not Disturb"
                SocialPresenceMode.IDLE -> if (detail != null) "Idle · $detail" else "Idle"
                else -> detail?.let {
                    if (it == "Looking to play") "Online · Looking to play" else it
                } ?: "Online"
            }
        }
        return formatLastSeen(lastSeenMs, nowMs)
    }

    companion object {
        fun formatLastSeen(lastSeenMs: Long, nowMs: Long = System.currentTimeMillis()): String {
            if (lastSeenMs <= 0L) return "Offline"
            val diff = (nowMs - lastSeenMs).coerceAtLeast(0L)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            return when {
                minutes < 2L -> "Last seen just now"
                minutes < 60L -> "Last seen $minutes m ago"
                hours < 24L -> "Last seen $hours h ago"
                days == 1L -> "Last seen yesterday"
                days < 30L -> "Last seen $days d ago"
                else -> "Offline"
            }
        }
    }
}

data class SocialMessage(
    val id: Long,
    val sender: String,
    val recipient: String,
    val body: String,
    val atMs: Long,
    val readAtMs: Long = 0L,
    val editedAtMs: Long = 0L,
    val replyTo: Long? = null,
    val replyPreview: String? = null,
    val attachmentUrl: String? = null,
    val partyId: String? = null,
    val senderName: String? = null,
    val senderRole: SocialRole? = null,
    val pendingOffline: Boolean = false,
    val localTempId: String? = null
) {
    val isRead: Boolean get() = readAtMs > 0L
    val isEdited: Boolean get() = editedAtMs > 0L
    val cleanSenderName: String?
        get() = SocialRole.stripRoleTags(senderName) ?: senderName
    val effectiveSenderRole: SocialRole?
        get() = senderRole ?: SocialRole.extract(senderName).second
}

data class SocialPartyMember(
    val userId: String,
    val username: String,
    val mcUsername: String?,
    val avatarUrl: String?,
    val isHost: Boolean = false,
    val online: Boolean = false,
    val statusLine: String? = null,
    val role: SocialRole? = null,
    val presenceMode: SocialPresenceMode = SocialPresenceMode.ONLINE
) {
    val cleanName: String
        get() = SocialRole.stripRoleTags(username) ?: username
    val effectiveRole: SocialRole?
        get() = role ?: SocialRole.extract(username).second
}

data class SocialParty(
    val id: String,
    val hostId: String,
    val hostName: String,
    val hostAvatar: String? = null,
    val hostRole: SocialRole? = null,
    val title: String,
    val instanceName: String? = null,
    val versionId: String? = null,
    val mcVersion: String? = null,
    val loader: String? = null,
    val modpackSource: String? = null,
    val modpackId: String? = null,
    val modpackIconUrl: String? = null,
    val serverAddress: String? = null,
    val maxMembers: Int = 4,
    val isPublic: Boolean = false,
    val memberCount: Int = 1,
    val expiresAtMs: Long = 0L,
    val createdAtMs: Long = 0L,
    val members: List<SocialPartyMember> = emptyList()
) {
    val cleanHostName: String
        get() = SocialRole.stripRoleTags(hostName) ?: hostName
    val effectiveHostRole: SocialRole?
        get() = hostRole ?: SocialRole.extract(hostName).second
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtMs in 1..nowMs

    fun remainingText(nowMs: Long = System.currentTimeMillis()): String {
        if (expiresAtMs <= 0L) return "Active"
        val rem = expiresAtMs - nowMs
        if (rem <= 0L) return "Expired"
        val mins = TimeUnit.MILLISECONDS.toMinutes(rem)
        val hrs = TimeUnit.MILLISECONDS.toHours(rem)
        return when {
            hrs >= 1L -> {
                val remMins = mins - hrs * 60
                if (remMins > 0) "${hrs}h ${remMins}m left" else "${hrs}h left"
            }
            mins >= 1L -> "${mins}m left"
            else -> "<1m left"
        }
    }

    fun summaryTag(): String {
        val parts = mutableListOf<String>()
        mcVersion?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        loader?.takeIf { it.isNotBlank() && !it.equals("vanilla", ignoreCase = true) }?.let {
            parts.add(it.replaceFirstChar { ch -> ch.uppercase() })
        }
        instanceName?.takeIf { it.isNotBlank() && it != mcVersion }?.let { parts.add(it) }
        return if (parts.isEmpty()) "Minecraft session" else parts.joinToString(" · ")
    }
}

data class SocialPartyInvite(
    val id: String,
    val partyId: String,
    val fromUserId: String,
    val fromName: String,
    val fromMcUsername: String? = null,
    val fromAvatar: String? = null,
    val fromRole: SocialRole? = null,
    val partyTitle: String,
    val instanceName: String? = null,
    val versionId: String? = null,
    val mcVersion: String? = null,
    val loader: String? = null,
    val modpackSource: String? = null,
    val modpackId: String? = null,
    val modpackIconUrl: String? = null,
    val serverAddress: String? = null,
    val maxMembers: Int = 4,
    val expiresAtMs: Long = 0L,
    val createdAtMs: Long = System.currentTimeMillis(),
    val messageRowId: Long? = null
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtMs in 1..nowMs

    val cleanFromName: String
        get() = SocialRole.stripRoleTags(fromName) ?: fromName
    val effectiveFromRole: SocialRole?
        get() = fromRole ?: SocialRole.extract(fromName).second
}

data class SocialSharedServer(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val ownerMcUsername: String? = null,
    val ownerAvatar: String? = null,
    val ownerRole: SocialRole? = null,
    val name: String,
    val address: String,
    val mcVersion: String? = null,
    val loader: String? = null,
    val description: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    val cleanOwnerName: String
        get() = SocialRole.stripRoleTags(ownerName) ?: ownerName
    val effectiveOwnerRole: SocialRole?
        get() = ownerRole ?: SocialRole.extract(ownerName).second
}

data class SocialBlockedUser(
    val userId: String,
    val name: String,
    val username: String? = null,
    val mcUsername: String? = null,
    val avatarUrl: String? = null,
    val role: SocialRole? = null,
    val createdAtMs: Long = 0L
) {
    val cleanName: String
        get() = SocialRole.stripRoleTags(name) ?: name
    val effectiveRole: SocialRole?
        get() = role ?: SocialRole.extract(name).second
}

data class SocialRecentPlayer(
    val userId: String,
    val name: String,
    val username: String? = null,
    val mcUsername: String? = null,
    val avatarUrl: String? = null,
    val contextLabel: String,
    val seenAtMs: Long,
    val role: SocialRole? = null
) {
    val cleanName: String
        get() = SocialRole.stripRoleTags(name) ?: name
    val effectiveRole: SocialRole?
        get() = role ?: SocialRole.extract(name).second
}

data class SocialJoinToast(
    val id: String,
    val friendId: String,
    val friendName: String,
    val friendRole: SocialRole? = null,
    val mcUsername: String?,
    val mcVersion: String,
    val statusLine: String?,
    val loader: String? = null,
    val serverAddress: String? = null,
    val partyId: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    val cleanFriendName: String
        get() = SocialRole.stripRoleTags(friendName) ?: friendName
    val effectiveFriendRole: SocialRole?
        get() = friendRole ?: SocialRole.extract(friendName).second
}

data class SocialSearchHit(
    val messageId: Long,
    val peerId: String?,
    val partyId: String?,
    val conversationTitle: String,
    val senderName: String,
    val body: String,
    val atMs: Long
)

data class SocialOutboxItem(
    val localId: String,
    val recipientId: String? = null,
    val partyId: String? = null,
    val body: String,
    val replyTo: Long? = null,
    val attachmentLocalPath: String? = null,
    val attachmentRemoteUrl: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * Encodes structured social envelopes (party invites, fallback party chat, fallback party metadata)
 * so new social features remain 100% functional even before optional SQL migrations are applied.
 */
object SocialEnvelopeCodec {
    const val PARTY_INVITE_PREFIX = "__aerix_party_invite__:"
    const val PARTY_CHAT_PREFIX = "__aerix_party_chat__:"
    const val PARTY_META_DELIM = "||aerix_meta:"
    const val DM_META_DELIM = "\n||aerix_dm:"

    data class DecodedDmBody(
        val body: String,
        val replyTo: Long? = null,
        val attachmentUrl: String? = null
    )

    private val gson = Gson()

    data class PackedVersionMeta(
        val versionId: String?,
        val loader: String? = null,
        val modpackSource: String? = null,
        val modpackId: String? = null,
        val modpackIconUrl: String? = null,
        val serverAddress: String? = null,
        val maxMembers: Int = 4,
        val isPublic: Boolean = false
    )

    fun packVersionWithMeta(meta: PackedVersionMeta): String {
        val base = meta.versionId.orEmpty()
        val obj = JsonObject().apply {
            meta.loader?.let { addProperty("l", it) }
            meta.modpackSource?.let { addProperty("ms", it) }
            meta.modpackId?.let { addProperty("mi", it) }
            meta.modpackIconUrl?.let { addProperty("mu", it) }
            meta.serverAddress?.let { addProperty("sa", it) }
            addProperty("mm", meta.maxMembers)
            if (meta.isPublic) addProperty("pub", true)
        }
        return "$base$PARTY_META_DELIM${gson.toJson(obj)}"
    }

    fun unpackVersionWithMeta(rawVersion: String?): PackedVersionMeta {
        if (rawVersion.isNullOrBlank()) return PackedVersionMeta(null)
        val idx = rawVersion.indexOf(PARTY_META_DELIM)
        if (idx < 0) return PackedVersionMeta(rawVersion)
        val base = rawVersion.substring(0, idx).takeIf { it.isNotBlank() }
        val jsonPart = rawVersion.substring(idx + PARTY_META_DELIM.length)
        return try {
            val obj = JsonParser.parseString(jsonPart).asJsonObject
            PackedVersionMeta(
                versionId = base,
                loader = obj.optStr("l"),
                modpackSource = obj.optStr("ms"),
                modpackId = obj.optStr("mi"),
                modpackIconUrl = obj.optStr("mu"),
                serverAddress = obj.optStr("sa"),
                maxMembers = obj.get("mm")?.takeIf { !it.isJsonNull }?.asInt ?: 4,
                isPublic = obj.get("pub")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
            )
        } catch (_: Throwable) {
            PackedVersionMeta(base)
        }
    }

    fun encodePartyInvite(invite: SocialPartyInvite): String {
        val obj = JsonObject().apply {
            addProperty("id", invite.id)
            addProperty("partyId", invite.partyId)
            addProperty("fromId", invite.fromUserId)
            addProperty("fromName", invite.fromName)
            invite.fromMcUsername?.let { addProperty("fromMc", it) }
            invite.fromAvatar?.let { addProperty("fromAv", it) }
            addProperty("title", invite.partyTitle)
            invite.instanceName?.let { addProperty("inst", it) }
            invite.versionId?.let { addProperty("ver", it) }
            invite.mcVersion?.let { addProperty("mc", it) }
            invite.loader?.let { addProperty("loader", it) }
            invite.modpackSource?.let { addProperty("mpSrc", it) }
            invite.modpackId?.let { addProperty("mpId", it) }
            invite.modpackIconUrl?.let { addProperty("mpIcon", it) }
            invite.serverAddress?.let { addProperty("srv", it) }
            addProperty("max", invite.maxMembers)
            addProperty("exp", invite.expiresAtMs)
            addProperty("ts", invite.createdAtMs)
        }
        return PARTY_INVITE_PREFIX + gson.toJson(obj)
    }

    fun decodePartyInvite(body: String, rowId: Long? = null, fallbackSender: String = ""): SocialPartyInvite? {
        if (!body.startsWith(PARTY_INVITE_PREFIX)) return null
        return try {
            val obj = JsonParser.parseString(body.removePrefix(PARTY_INVITE_PREFIX)).asJsonObject
            SocialPartyInvite(
                id = obj.optStr("id") ?: return null,
                partyId = obj.optStr("partyId") ?: return null,
                fromUserId = obj.optStr("fromId") ?: fallbackSender,
                fromName = obj.optStr("fromName") ?: "Friend",
                fromMcUsername = obj.optStr("fromMc"),
                fromAvatar = obj.optStr("fromAv"),
                partyTitle = obj.optStr("title") ?: "Party",
                instanceName = obj.optStr("inst"),
                versionId = obj.optStr("ver"),
                mcVersion = obj.optStr("mc"),
                loader = obj.optStr("loader"),
                modpackSource = obj.optStr("mpSrc"),
                modpackId = obj.optStr("mpId"),
                modpackIconUrl = obj.optStr("mpIcon"),
                serverAddress = obj.optStr("srv"),
                maxMembers = obj.get("max")?.takeIf { !it.isJsonNull }?.asInt ?: 4,
                expiresAtMs = obj.get("exp")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                createdAtMs = obj.get("ts")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                messageRowId = rowId
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun encodeFallbackPartyChat(
        partyId: String,
        senderId: String,
        senderName: String,
        text: String,
        replyTo: Long?,
        attachmentUrl: String?,
        createdAtMs: Long
    ): String {
        val obj = JsonObject().apply {
            addProperty("p", partyId)
            addProperty("s", senderId)
            addProperty("sn", senderName)
            addProperty("b", text)
            replyTo?.let { addProperty("r", it) }
            attachmentUrl?.let { addProperty("a", it) }
            addProperty("ts", createdAtMs)
        }
        return PARTY_CHAT_PREFIX + gson.toJson(obj)
    }

    fun decodeFallbackPartyChat(
        rawBody: String,
        rowId: Long,
        atMs: Long
    ): SocialMessage? {
        if (!rawBody.startsWith(PARTY_CHAT_PREFIX)) return null
        return try {
            val obj = JsonParser.parseString(rawBody.removePrefix(PARTY_CHAT_PREFIX)).asJsonObject
            val partyId = obj.optStr("p") ?: return null
            val sender = obj.optStr("s") ?: return null
            SocialMessage(
                id = rowId,
                sender = sender,
                recipient = "",
                body = obj.optStr("b") ?: "",
                atMs = obj.get("ts")?.takeIf { !it.isJsonNull }?.asLong ?: atMs,
                replyTo = obj.get("r")?.takeIf { !it.isJsonNull }?.asLong,
                attachmentUrl = obj.optStr("a"),
                partyId = partyId,
                senderName = obj.optStr("sn")
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun encodeDmBodyWithMeta(
        body: String,
        replyTo: Long? = null,
        attachmentUrl: String? = null
    ): String {
        val hasReply = replyTo != null && replyTo > 0L
        val hasAtt = !attachmentUrl.isNullOrBlank()
        if (!hasReply && !hasAtt) return body.take(2000)
        val obj = JsonObject().apply {
            if (hasReply) addProperty("r", replyTo)
            if (hasAtt) addProperty("a", attachmentUrl)
        }
        val suffix = "$DM_META_DELIM${gson.toJson(obj)}"
        val maxBodyLen = (2000 - suffix.length).coerceAtLeast(1)
        return body.take(maxBodyLen) + suffix
    }

    fun decodeDmBodyWithMeta(rawBody: String): DecodedDmBody {
        val idx = rawBody.lastIndexOf(DM_META_DELIM)
        if (idx < 0) return DecodedDmBody(rawBody)
        val cleanText = rawBody.substring(0, idx)
        val jsonPart = rawBody.substring(idx + DM_META_DELIM.length)
        return try {
            val obj = JsonParser.parseString(jsonPart).asJsonObject
            DecodedDmBody(
                body = cleanText,
                replyTo = obj.get("r")?.takeIf { !it.isJsonNull }?.asLong,
                attachmentUrl = obj.optStr("a")
            )
        } catch (_: Throwable) {
            DecodedDmBody(rawBody)
        }
    }

    private fun JsonObject.optStr(key: String): String? {
        val el = get(key) ?: return null
        if (el.isJsonNull) return null
        return el.asString.takeIf { it.isNotEmpty() }
    }
}
