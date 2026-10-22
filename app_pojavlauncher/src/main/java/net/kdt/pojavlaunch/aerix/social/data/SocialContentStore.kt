/*
 * Aerix Launcher Social layer.
 *
 * Everything the player creates — friends, requests, conversations, attachments, party and the
 * activity feed — is written to disk through this store, so the second launcher keeps its state
 * after a restart instead of falling back to the seed data.
 *
 * The payload is a single JSON document stored in a dedicated Preferences DataStore file.
 * It is deliberately a plain interface so a server-backed implementation can replace it later
 * without touching the repository or a single screen.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** The whole mutable content of the Social ecosystem, as one snapshot. */
data class SocialContent(
    val friends: List<AerixUser> = emptyList(),
    val requests: List<FriendRequest> = emptyList(),
    val chats: Map<String, List<ChatMessage>> = emptyMap(),
    val posts: List<ActivityPost> = emptyList(),
    val blocked: List<AerixUser> = emptyList(),
    val party: PartyState = PartyState(),
    val seenMessageIds: Set<String> = emptySet()
)

interface SocialContentStore {
    /** Returns null the first time, which is what triggers the seed data. */
    suspend fun load(): SocialContent?
    suspend fun save(content: SocialContent)
}

class InMemorySocialContentStore(initial: SocialContent? = null) : SocialContentStore {
    private var content: SocialContent? = initial
    override suspend fun load(): SocialContent? = content
    override suspend fun save(content: SocialContent) {
        this.content = content
    }
}

private val Context.socialContentStore: DataStore<Preferences> by preferencesDataStore(name = "aerix_social_content")

class DataStoreSocialContentStore(private val store: DataStore<Preferences>) : SocialContentStore {
    constructor(context: Context) : this(context.applicationContext.socialContentStore)

    override suspend fun load(): SocialContent? {
        val raw = store.data.map { it[KEY] }.first() ?: return null
        return runCatching { SocialJson.decode(raw) }.getOrNull()
    }

    override suspend fun save(content: SocialContent) {
        val encoded = SocialJson.encode(content)
        store.edit { it[KEY] = encoded }
    }

    private companion object {
        val KEY = stringPreferencesKey("social_content_json")
    }
}

/**
 * Hand written JSON (org.json ships with Android, so this adds no dependency). Every field is
 * read defensively: a half-written document must never crash the launcher.
 */
object SocialJson {
    fun encode(content: SocialContent): String = JSONObject().apply {
        put("version", 1)
        put("friends", JSONArray().apply { content.friends.forEach { put(user(it)) } })
        put("requests", JSONArray().apply {
            content.requests.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("fromName", it.fromName)
                    put("avatar", it.avatar)
                    put("pending", it.pending)
                    put("fromUserId", it.fromUserId)
                })
            }
        })
        put("chats", JSONObject().apply {
            content.chats.forEach { (id, messages) ->
                put(id, JSONArray().apply { messages.forEach { put(message(it)) } })
            }
        })
        put("posts", JSONArray().apply { content.posts.forEach { put(post(it)) } })
        put("blocked", JSONArray().apply { content.blocked.forEach { put(user(it)) } })
        put("party", JSONObject().apply {
            put("joined", content.party.joined)
            put("name", content.party.name)
            put("logo", content.party.logo)
            put("inCall", content.party.inCall)
            put("muted", content.party.muted)
            put("members", JSONArray().apply {
                content.party.members.forEach {
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("name", it.name)
                        put("avatar", it.avatar)
                        put("muted", it.muted)
                        put("isYou", it.isYou)
                    })
                }
            })
        })
        put("seen", JSONArray().apply { content.seenMessageIds.forEach { put(it) } })
    }.toString()

    fun decode(raw: String): SocialContent {
        val root = JSONObject(raw)
        val chats = mutableMapOf<String, List<ChatMessage>>()
        root.optJSONObject("chats")?.let { object_ ->
            object_.keys().forEach { key ->
                chats[key] = object_.optJSONArray(key)?.mapObjects(::message).orEmpty()
            }
        }
        return SocialContent(
            friends = root.optJSONArray("friends")?.mapObjects(::user).orEmpty(),
            requests = root.optJSONArray("requests")?.mapObjects { json ->
                FriendRequest(
                    id = json.optString("id"),
                    fromName = json.optString("fromName"),
                    avatar = json.optInt("avatar", 1),
                    pending = json.optBoolean("pending", true),
                    fromUserId = json.optString("fromUserId")
                )
            }.orEmpty(),
            chats = chats,
            posts = root.optJSONArray("posts")?.mapObjects(::post).orEmpty(),
            blocked = root.optJSONArray("blocked")?.mapObjects(::user).orEmpty(),
            party = root.optJSONObject("party")?.let { json ->
                PartyState(
                    joined = json.optBoolean("joined"),
                    name = json.optString("name"),
                    logo = json.optInt("logo", 3),
                    inCall = json.optBoolean("inCall"),
                    muted = json.optBoolean("muted"),
                    members = json.optJSONArray("members")?.mapObjects { member ->
                        PartyMember(
                            id = member.optString("id"),
                            name = member.optString("name"),
                            avatar = member.optInt("avatar", 1),
                            muted = member.optBoolean("muted"),
                            isYou = member.optBoolean("isYou")
                        )
                    }.orEmpty()
                )
            } ?: PartyState(),
            seenMessageIds = root.optJSONArray("seen")?.let { array ->
                (0 until array.length()).map { array.optString(it) }.toSet()
            }.orEmpty()
        )
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }

    private fun user(user: AerixUser): JSONObject = JSONObject().apply {
        put("id", user.id)
        put("name", user.name)
        put("avatar", user.avatar)
        put("status", user.status.name)
        put("activity", user.activity.orEmpty())
        put("email", user.email.orEmpty())
        put("bio", user.bio)
        put("followers", user.followers)
        put("following", user.following)
        put("posts", user.posts)
        put("likes", user.likes)
        put("hasStory", user.hasStory)
        put("closeFriend", user.closeFriend)
        put("friend", user.friend)
        put("links", JSONArray().apply {
            user.links.forEach { link ->
                put(JSONObject().apply {
                    put("platform", link.platform.name)
                    put("value", link.value)
                })
            }
        })
    }

    private fun user(json: JSONObject): AerixUser = AerixUser(
        id = json.optString("id"),
        name = json.optString("name"),
        avatar = json.optInt("avatar", 1),
        status = runCatching { UserStatus.valueOf(json.optString("status", "ONLINE")) }.getOrDefault(UserStatus.ONLINE),
        activity = json.optString("activity").ifBlank { null },
        email = json.optString("email").ifBlank { null },
        bio = json.optString("bio"),
        followers = json.optInt("followers"),
        following = json.optInt("following"),
        posts = json.optInt("posts"),
        likes = json.optInt("likes"),
        hasStory = json.optBoolean("hasStory"),
        closeFriend = json.optBoolean("closeFriend"),
        friend = json.optBoolean("friend"),
        links = json.optJSONArray("links")?.mapObjects { link ->
            val platform = runCatching { LinkPlatform.valueOf(link.optString("platform")) }.getOrNull()
            platform?.let { SocialLink(it, link.optString("value")) }
        }?.filterNotNull().orEmpty()
    )

    private fun message(message: ChatMessage): JSONObject = JSONObject().apply {
        put("id", message.id)
        put("author", message.author)
        put("text", message.text)
        put("mine", message.mine)
        put("timestamp", message.timestamp)
        put("myReaction", message.myReaction.orEmpty())
        put("reactions", JSONObject().apply { message.reactions.forEach { (key, value) -> put(key, value) } })
        put("attachments", JSONArray().apply {
            message.attachments.forEach { attachment ->
                put(JSONObject().apply {
                    put("kind", attachment.kind.name)
                    put("title", attachment.title)
                    put("subtitle", attachment.subtitle)
                    put("payload", attachment.payload)
                    put("sizeLabel", attachment.sizeLabel)
                    put("localPath", attachment.localPath.orEmpty())
                    put("mimeType", attachment.mimeType.orEmpty())
                })
            }
        })
    }

    private fun message(json: JSONObject): ChatMessage {
        val reactions = mutableMapOf<String, Int>()
        json.optJSONObject("reactions")?.let { object_ ->
            object_.keys().forEach { key -> reactions[key] = object_.optInt(key) }
        }
        return ChatMessage(
            id = json.optString("id"),
            author = json.optString("author"),
            text = json.optString("text"),
            mine = json.optBoolean("mine"),
            timestamp = json.optString("timestamp"),
            reactions = reactions,
            myReaction = json.optString("myReaction").ifBlank { null },
            attachments = json.optJSONArray("attachments")?.mapObjects { attachment ->
                ChatAttachment(
                    kind = runCatching { AttachmentKind.valueOf(attachment.optString("kind")) }.getOrDefault(AttachmentKind.FILE),
                    title = attachment.optString("title"),
                    subtitle = attachment.optString("subtitle"),
                    payload = attachment.optString("payload"),
                    sizeLabel = attachment.optString("sizeLabel"),
                    localPath = attachment.optString("localPath").ifBlank { null },
                    mimeType = attachment.optString("mimeType").ifBlank { null }
                )
            }.orEmpty()
        )
    }

    private fun post(post: ActivityPost): JSONObject = JSONObject().apply {
        put("id", post.id)
        put("authorId", post.authorId)
        put("authorName", post.authorName)
        put("authorAvatar", post.authorAvatar)
        put("caption", post.caption)
        put("kind", post.kind.name)
        put("likes", post.likes)
        put("liked", post.liked)
        put("hoursAgo", post.hoursAgo)
        put("following", post.following)
        put("comments", JSONArray().apply {
            post.comments.forEach { comment ->
                put(JSONObject().apply {
                    put("id", comment.id)
                    put("author", comment.author)
                    put("text", comment.text)
                })
            }
        })
    }

    private fun post(json: JSONObject): ActivityPost = ActivityPost(
        id = json.optString("id"),
        authorId = json.optString("authorId"),
        authorName = json.optString("authorName"),
        authorAvatar = json.optInt("authorAvatar", 1),
        caption = json.optString("caption"),
        kind = runCatching { PostKind.valueOf(json.optString("kind", "PHOTO")) }.getOrDefault(PostKind.PHOTO),
        likes = json.optInt("likes"),
        liked = json.optBoolean("liked"),
        hoursAgo = json.optInt("hoursAgo", 1),
        following = json.optBoolean("following"),
        comments = json.optJSONArray("comments")?.mapObjects { comment ->
            ActivityComment(
                id = comment.optString("id"),
                author = comment.optString("author"),
                text = comment.optString("text")
            )
        }.orEmpty()
    )
}
