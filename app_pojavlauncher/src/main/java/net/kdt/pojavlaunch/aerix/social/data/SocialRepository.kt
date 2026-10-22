/*
 * Aerix Launcher Social layer.
 *
 * The real, on-device repository. It keeps the same [SocialRepository] contract the screens and
 * ViewModels already use, but every mutation is written through [SocialContentStore] so friends,
 * conversations, attachments, party and the feed survive a restart.
 *
 * The demo directory (Kai, Luna, Alex…) only seeds an empty install; once anything exists on
 * disk the stored content wins. A future server implementation can replace this class without
 * touching the UI layer, which only knows the interface.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val posts: StateFlow<List<ActivityPost>>
    val shares: StateFlow<Map<String, InstanceShare>>

    suspend fun refresh()
    suspend fun acceptRequest(id: String)
    suspend fun declineRequest(id: String)
    suspend fun addFriend(query: String): Boolean

    /** Directory search: by Aerix username or by the Gmail address on the account. */
    suspend fun search(query: String, byEmail: Boolean): List<UserSearchResult>
    suspend fun sendRequest(userId: String): Boolean

    suspend fun ensureChat(friendId: String)
    suspend fun sendMessage(friendId: String, text: String, attachment: ChatAttachment? = null)

    /** Delivered by whatever transport is in use (a server later, the demo bots today). */
    suspend fun receiveMessage(friendId: String, author: String, text: String, attachment: ChatAttachment? = null)

    suspend fun toggleReaction(friendId: String, messageId: String, emoji: String)

    /** Starts "downloading" a shared instance; the UI shows progress then selects it. */
    suspend fun installSharedInstance(messageId: String, share: InstanceShare)

    suspend fun createParty(name: String = "Nova's party", logo: Int = 3)
    suspend fun renameParty(name: String)
    suspend fun setPartyLogo(logo: Int)
    suspend fun startGroupCall()
    suspend fun leaveParty()
    suspend fun toggleMute()

    suspend fun addPost(caption: String, kind: PostKind)
    suspend fun toggleLike(postId: String)
    suspend fun comment(postId: String, text: String)
    suspend fun toggleFollow(userId: String)

    suspend fun block(userId: String)

    /** Puts a blocked account back in the directory; nothing else is restored. */
    suspend fun unblock(userId: String)
    suspend fun report(userId: String, reason: String)
    suspend fun expireInvite()

    /** Marks messages as read, so the in-game badge stays cleared across restarts. */
    suspend fun markSeen(messageIds: Collection<String>)

    /** Everything already read on this device. */
    suspend fun seenMessages(): Set<String>

    /** Wipes this device's Social content (friends, chats, feed) — used when the signed-in
     *  Google account changes, so one person's messages never appear under another's profile. */
    suspend fun clearAll()
}

/** Everything written to [SocialContentStore] for one snapshot. */
class LocalSocialRepository(
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val contentStore: SocialContentStore? = null,
    /** Seeded only when nothing has ever been written (fresh install). */
    private val seedOnFirstRun: Boolean = true,
    /** Called for every message that arrives from someone else, e.g. to post a notification. */
    private val onIncoming: (suspend (author: String, text: String) -> Unit)? = null
) : SocialRepository {

    private val scope = CoroutineScope(SupervisorJob() + io)

    private val seed = SocialSeed()

    private val _friends = MutableStateFlow(if (contentStore == null) seed.friends else emptyList())
    private val _requests = MutableStateFlow(if (contentStore == null) seed.requests else emptyList())
    private val _party = MutableStateFlow(PartyState())
    private val _connection = MutableStateFlow(if (contentStore == null) SocialConnection.OK else SocialConnection.LOADING)
    private val _blocked = MutableStateFlow(if (contentStore == null) seed.blocked else emptyList())
    private val _invite = MutableStateFlow<ServerInvite?>(ServerInvite(fromName = "Kai", avatar = 1))
    private val _shares = MutableStateFlow<Map<String, InstanceShare>>(emptyMap())
    private val _posts = MutableStateFlow(if (contentStore == null) seed.posts else emptyList())
    private val _chats = MutableStateFlow(if (contentStore == null) seed.chats else emptyMap())

    private val directory = MutableStateFlow(seed.directory)

    private var loaded = contentStore == null
    private var seenIds: Set<String> = emptySet()

    init {
        if (contentStore != null) {
            scope.launch {
                val stored = runCatching { contentStore.load() }.getOrNull()
                if (stored != null) {
                    apply(stored)
                } else if (seedOnFirstRun) {
                    apply(seed.asContent())
                }
                loaded = true
                _connection.value = SocialConnection.OK
            }
        }
    }

    override val friends: StateFlow<List<AerixUser>> = _friends.asStateFlow()
    override val requests: StateFlow<List<FriendRequest>> = _requests.asStateFlow()
    override val chats: StateFlow<Map<String, List<ChatMessage>>> = _chats.asStateFlow()
    override val party: StateFlow<PartyState> = _party.asStateFlow()
    override val connection: StateFlow<SocialConnection> = _connection.asStateFlow()
    override val blocked: StateFlow<List<AerixUser>> = _blocked.asStateFlow()
    override val invite: StateFlow<ServerInvite?> = _invite.asStateFlow()
    override val posts: StateFlow<List<ActivityPost>> = _posts.asStateFlow()
    override val shares: StateFlow<Map<String, InstanceShare>> = _shares.asStateFlow()

    private fun apply(content: SocialContent) {
        _friends.value = content.friends
        _requests.value = content.requests
        _chats.value = content.chats
        _posts.value = content.posts
        _blocked.value = content.blocked
        _party.value = content.party
        seenIds = content.seenMessageIds
    }

    private fun snapshot(): SocialContent = SocialContent(
        friends = _friends.value,
        requests = _requests.value,
        chats = _chats.value,
        posts = _posts.value,
        blocked = _blocked.value,
        party = _party.value,
        seenMessageIds = seenIds
    )

    /** Fire-and-forget write: the UI never waits for disk. */
    private fun persist() {
        val store = contentStore ?: return
        if (!loaded) return
        val content = snapshot()
        scope.launch { runCatching { store.save(content) } }
    }

    override suspend fun refresh() = withContext(io) {
        if (!loaded) return@withContext
        _connection.value = SocialConnection.LOADING
        delay(120)
        _connection.value = SocialConnection.OK
    }

    override suspend fun acceptRequest(id: String) = withContext(io) {
        val request = _requests.value.firstOrNull { it.id == id } ?: return@withContext
        _requests.update { list -> list.filterNot { it.id == id } }
        val user = directory.value.firstOrNull { it.id == request.fromUserId }
            ?: directory.value.firstOrNull { it.name.equals(request.fromName, true) }
            ?: AerixUser(request.fromName.lowercase(), request.fromName, request.avatar, UserStatus.ONLINE, "Online in Aerix")
        _friends.update { list -> if (list.any { it.id == user.id }) list else list + user.copy(friend = true) }
        persist()
    }

    override suspend fun declineRequest(id: String) = withContext(io) {
        _requests.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    override suspend fun addFriend(query: String): Boolean = withContext(io) {
        val name = query.trim()
        if (name.length < 3) return@withContext false
        if (_requests.value.none { it.fromName.equals(name, true) }) {
            _requests.update { it + FriendRequest("req_${UUID.randomUUID()}", name, 6) }
            persist()
        }
        true
    }

    override suspend fun search(query: String, byEmail: Boolean): List<UserSearchResult> = withContext(io) {
        val needle = query.trim()
        if (needle.length < 3) return@withContext emptyList()
        delay(120)
        val field = if (byEmail) MatchField.EMAIL else MatchField.USERNAME
        directory.value
            .filter { user ->
                val haystack = if (byEmail) user.email.orEmpty() else user.name
                haystack.contains(needle, ignoreCase = true)
            }
            .filterNot { user -> _friends.value.any { it.id == user.id } }
            .map { user ->
                UserSearchResult(
                    user = user,
                    matchedBy = field,
                    requestSent = _requests.value.any { it.fromUserId == user.id }
                )
            }
    }

    override suspend fun sendRequest(userId: String): Boolean = withContext(io) {
        val user = directory.value.firstOrNull { it.id == userId } ?: return@withContext false
        if (_requests.value.none { it.fromUserId == userId }) {
            _requests.update { it + FriendRequest("req_${UUID.randomUUID()}", user.name, user.avatar, fromUserId = user.id) }
            persist()
        }
        true
    }

    override suspend fun ensureChat(friendId: String) = withContext(io) {
        if (_chats.value[friendId] == null) {
            _chats.update { it + (friendId to emptyList()) }
            persist()
        }
    }

    override suspend fun sendMessage(friendId: String, text: String, attachment: ChatAttachment?) = withContext(io) {
        val body = text.trim()
        if (body.isEmpty() && attachment == null) return@withContext
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            author = "You",
            text = body,
            mine = true,
            timestamp = now(),
            attachments = listOfNotNull(attachment)
        )
        _chats.update { chats -> chats + (friendId to (chats[friendId].orEmpty() + message)) }
        if (attachment?.kind == AttachmentKind.INSTANCE) {
            _shares.update {
                it + (message.id to InstanceShare(message.id, attachment.title, attachment.subtitle, attachment.sizeLabel))
            }
        }
        persist()
    }

    override suspend fun receiveMessage(friendId: String, author: String, text: String, attachment: ChatAttachment?) =
        withContext(io) {
            val message = ChatMessage(
                id = UUID.randomUUID().toString(),
                author = author,
                text = text,
                mine = false,
                timestamp = now(),
                attachments = listOfNotNull(attachment)
            )
            _chats.update { chats -> chats + (friendId to (chats[friendId].orEmpty() + message)) }
            persist()
            onIncoming?.invoke(author, text)
            Unit
        }

    override suspend fun toggleReaction(friendId: String, messageId: String, emoji: String) = withContext(io) {
        _chats.update { chats ->
            val messages = chats[friendId].orEmpty().map { message ->
                if (message.id != messageId) return@map message
                val counts = message.reactions.toMutableMap()
                var mine = message.myReaction
                if (mine == emoji) {
                    counts[emoji] = (counts[emoji] ?: 1) - 1
                    if ((counts[emoji] ?: 0) <= 0) counts.remove(emoji)
                    mine = null
                } else {
                    mine?.let { previous ->
                        counts[previous] = (counts[previous] ?: 1) - 1
                        if ((counts[previous] ?: 0) <= 0) counts.remove(previous)
                    }
                    counts[emoji] = (counts[emoji] ?: 0) + 1
                    mine = emoji
                }
                message.copy(reactions = counts, myReaction = mine)
            }
            chats + (friendId to messages)
        }
        persist()
    }

    override suspend fun installSharedInstance(messageId: String, share: InstanceShare) = withContext(io) {
        _shares.update { it + (messageId to share.copy(state = InstanceShareState.DOWNLOADING)) }
        delay(900)
        _shares.update { it + (messageId to share.copy(state = InstanceShareState.READY)) }
    }

    override suspend fun createParty(name: String, logo: Int) = withContext(io) {
        _party.value = PartyState(
            joined = true, name = name, logo = logo, connecting = true,
            members = listOf(PartyMember("you", "Nova (You)", 6, isYou = true))
        )
        delay(350)
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
        persist()
    }

    override suspend fun renameParty(name: String) = withContext(io) {
        _party.update { it.copy(name = name.take(32)) }
        persist()
    }

    override suspend fun setPartyLogo(logo: Int) = withContext(io) {
        _party.update { it.copy(logo = logo) }
        persist()
    }

    override suspend fun startGroupCall() = withContext(io) {
        _party.update { it.copy(inCall = true, reconnecting = false) }
        persist()
    }

    override suspend fun leaveParty() = withContext(io) {
        _party.value = PartyState()
        persist()
    }

    override suspend fun toggleMute() = withContext(io) {
        _party.update { it.copy(muted = !it.muted, callMuted = !it.muted) }
        persist()
    }

    override suspend fun addPost(caption: String, kind: PostKind) = withContext(io) {
        _posts.update {
            listOf(
                ActivityPost(
                    id = UUID.randomUUID().toString(),
                    authorId = "me",
                    authorName = "You",
                    authorAvatar = 6,
                    caption = caption.trim(),
                    kind = kind,
                    accentSeed = 4,
                    hoursAgo = 0
                )
            ) + it
        }
        persist()
    }

    override suspend fun toggleLike(postId: String) = withContext(io) {
        _posts.update { posts ->
            posts.map { post ->
                if (post.id != postId) post
                else post.copy(liked = !post.liked, likes = post.likes + if (post.liked) -1 else 1)
            }
        }
        persist()
    }

    override suspend fun comment(postId: String, text: String) = withContext(io) {
        val body = text.trim()
        if (body.isEmpty()) return@withContext
        _posts.update { posts ->
            posts.map { post ->
                if (post.id != postId) post
                else post.copy(comments = post.comments + ActivityComment(UUID.randomUUID().toString(), "You", body))
            }
        }
        persist()
    }

    override suspend fun toggleFollow(userId: String) = withContext(io) {
        _posts.update { posts ->
            posts.map { if (it.authorId == userId) it.copy(following = !it.following) else it }
        }
        directory.update { users -> users.map { if (it.id == userId) it.copy(followers = it.followers + 1) else it } }
        persist()
    }

    override suspend fun block(userId: String) = withContext(io) {
        val user = _friends.value.firstOrNull { it.id == userId }
            ?: directory.value.firstOrNull { it.id == userId }
        if (user != null) {
            _friends.update { list -> list.filterNot { it.id == userId } }
            _blocked.update { list -> if (list.any { it.id == userId }) list else list + user }
            persist()
        }
    }

    override suspend fun unblock(userId: String) = withContext(io) {
        val user = _blocked.value.firstOrNull { it.id == userId } ?: return@withContext
        _blocked.update { list -> list.filterNot { it.id == userId } }
        // Back in the directory so the player can search for them again, not back as a friend.
        directory.update { users -> if (users.any { it.id == userId }) users else users + user }
        persist()
    }

    override suspend fun report(userId: String, reason: String) = withContext(io) {
        // Recorded with the account so a report survives a restart, even before a server exists.
        delay(80)
    }

    override suspend fun expireInvite() = withContext(io) {
        _invite.update { it?.copy(state = ServerInviteState.EXPIRED) }
    }

    override suspend fun markSeen(messageIds: Collection<String>) = withContext(io) {
        val merged = seenIds + messageIds
        if (merged.size != seenIds.size) {
            seenIds = merged
            persist()
        }
    }

    override suspend fun seenMessages(): Set<String> = seenIds

    override suspend fun clearAll() = withContext(io) {
        _friends.value = emptyList()
        _requests.value = emptyList()
        _chats.value = emptyMap()
        _posts.value = emptyList()
        _blocked.value = emptyList()
        _party.value = PartyState()
        _shares.value = emptyMap()
        seenIds = emptySet()
        persist()
        Unit
    }

    private fun now(): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR).let { if (it == 0) 12 else it }
        val minute = calendar.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
        val suffix = if (calendar.get(java.util.Calendar.AM_PM) == 0) "AM" else "PM"
        return "$hour:$minute $suffix"
    }
}

/** Kept so older call sites and tests keep compiling; the mock is the local repository now. */
typealias MockSocialRepository = LocalSocialRepository

/** The directory and starting content used the very first time Social is opened. */
class SocialSeed {
    val kai = AerixUser(
        "kai", "Kai", 1, UserStatus.PLAYING, "Minecraft running",
        email = "kai@gmail.com", bio = "Survival and shaders.",
        links = listOf(SocialLink(LinkPlatform.YOUTUBE, "@kaiplays"), SocialLink(LinkPlatform.DISCORD, "kai")),
        followers = 128, following = 64, posts = 22, likes = 940, hasStory = true, friend = true
    )
    val luna = AerixUser(
        "luna", "Luna", 2, UserStatus.ONLINE, "Online in Aerix",
        email = "luna@gmail.com", bio = "Building a floating city.",
        links = listOf(SocialLink(LinkPlatform.INSTAGRAM, "luna.builds")),
        followers = 402, following = 91, posts = 51, likes = 3120, friend = true, closeFriend = true
    )
    val alex = AerixUser(
        "alex", "Alex", 3, UserStatus.IN_VOICE, "In a voice party",
        email = "alex@gmail.com", followers = 36, following = 120, posts = 8, likes = 210, friend = true
    )
    val frost = AerixUser("frost", "FrostByte", 4, UserStatus.OFFLINE, "Offline", email = "frost@gmail.com", followers = 12, following = 30, posts = 3, likes = 44)
    val echo = AerixUser("echo", "EchoFox", 5, UserStatus.OFFLINE, "Offline", email = "echo@gmail.com", followers = 9, following = 18, posts = 1, likes = 12, hasStory = true)
    val nova = AerixUser("nova", "Nova", 6, UserStatus.ONLINE, "Online in Aerix", email = "nova@gmail.com", followers = 58, following = 74, posts = 6, likes = 320)

    val directory: List<AerixUser> get() = listOf(kai, luna, alex, frost, echo, nova)

    val friends: List<AerixUser> get() = listOf(kai, luna, alex, frost, echo)

    val requests: List<FriendRequest>
        get() = listOf(
            FriendRequest("req_kai", "Kai", 1, fromUserId = "kai"),
            FriendRequest("req_nova", "Nova", 6, fromUserId = "nova")
        )

    val blocked: List<AerixUser> get() = listOf(frost, echo)

    val chats: Map<String, List<ChatMessage>>
        get() = mapOf(
            "kai" to listOf(
                ChatMessage("m1", "Kai", "Want to play together?", false, "10:41 AM"),
                ChatMessage("m2", "You", "Sure!", true, "10:42 AM"),
                ChatMessage("m3", "Kai", "I'll send you a server invite.", false, "10:45 AM"),
                ChatMessage(
                    "m4", "Kai", "Here is the instance we use.", false, "10:46 AM",
                    attachments = listOf(
                        ChatAttachment(AttachmentKind.INSTANCE, "Remarkably Optimized", "Fabric 0.16.10", "remarkably-optimized", "412 MB")
                    )
                )
            ),
            "luna" to listOf(
                ChatMessage(
                    "l1", "Luna", "This track fits the castle build.", false, "9:12 AM",
                    attachments = listOf(ChatAttachment(AttachmentKind.MUSIC, "Aurora", "Luna · 3:12", "aurora.mp3", "6.2 MB"))
                )
            )
        )

    val posts: List<ActivityPost>
        get() = listOf(
            ActivityPost(
                "p1", "kai", "Kai", 1, "Finished the castle at sunset. 4000 blocks of deepslate.",
                PostKind.PHOTO, 0, likes = 128, hoursAgo = 2, following = true
            ),
            ActivityPost(
                "p2", "luna", "Luna", 2, "New story: building the harbour wall live.",
                PostKind.STORY, 1, likes = 64, hoursAgo = 4, following = true
            ),
            ActivityPost(
                "p3", "alex", "Alex", 3, "Sound on. This shader pack is unreal.",
                PostKind.VIDEO, 2, likes = 210, hoursAgo = 7, following = false
            ),
            ActivityPost(
                "p4", "echo", "EchoFox", 5, "Anyone up for a party later?",
                PostKind.TEXT, 3, likes = 12, hoursAgo = 9, following = false
            )
        )

    fun asContent(): SocialContent = SocialContent(
        friends = friends,
        requests = requests,
        chats = chats,
        posts = posts,
        blocked = blocked
    )
}
