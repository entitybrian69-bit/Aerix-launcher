/*
 * Aerix Launcher Social layer.
 *
 * DataStore implementation of [SocialStore]. One preferences file holds the profile
 * (name, username, bio, links, skin, cape, theme), the privacy / notification / chat / mic
 * settings and the username-change timestamp used for the one-week cooldown.
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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.socialDataStore: DataStore<Preferences> by preferencesDataStore(name = "aerix_social")

class DataStoreSocialStore(private val store: DataStore<Preferences>) : SocialStore {
    /** Production entry point: one preferences file per application. */
    constructor(context: Context) : this(context.applicationContext.socialDataStore)

    override val data: Flow<SocialPersisted> = store.data.map(::read)

    override suspend fun update(transform: (SocialPersisted) -> SocialPersisted) {
        store.edit { prefs ->
            val next = transform(read(prefs))
            prefs[Keys.SIGNED_IN] = next.signedIn
            prefs[Keys.EMAIL] = next.email
            prefs[Keys.GOOGLE_CLIENT_ID] = next.googleClientId
            prefs[Keys.GOOGLE_BROWSER_CLIENT_ID] = next.googleBrowserClientId
            prefs[Keys.GOOGLE_BROWSER_SECRET] = next.googleBrowserSecret
            prefs[Keys.NAME] = next.name
            prefs[Keys.USERNAME] = next.username
            prefs[Keys.USERNAME_CHANGED_AT] = next.usernameChangedAt
            prefs[Keys.BIO] = next.bio
            prefs[Keys.LINKS] = encodeLinks(next.links)
            prefs[Keys.AVATAR] = next.avatar
            prefs[Keys.SKIN] = next.skinId
            prefs[Keys.CAPE] = next.capeId
            prefs[Keys.THEME] = next.themeWallpaperId

            prefs[Keys.PRIVACY_ONLINE] = next.privacy.onlineStatus
            prefs[Keys.PRIVACY_ACTIVITY] = next.privacy.shareActivity
            prefs[Keys.PRIVACY_SERVER] = next.privacy.showServerDetails
            prefs[Keys.PRIVACY_LOCK] = next.privacy.profileLock
            prefs[Keys.PRIVACY_STATUS] = next.privacy.status
            prefs[Keys.PRIVACY_REQUESTS] = next.privacy.friendRequests
            prefs[Keys.PRIVACY_MESSAGES] = next.privacy.privateMessages
            prefs[Keys.PRIVACY_PARTY] = next.privacy.partyInvites

            prefs[Keys.NOTIF_REQUESTS] = next.notifications.friendRequests
            prefs[Keys.NOTIF_MESSAGES] = next.notifications.privateMessages
            prefs[Keys.NOTIF_PARTY] = next.notifications.partyInvites
            prefs[Keys.NOTIF_SERVER] = next.notifications.serverInvites
            prefs[Keys.NOTIF_LAUNCHER] = next.notifications.inLauncher
            prefs[Keys.NOTIF_INGAME] = next.notifications.inGame
            prefs[Keys.NOTIF_PREVIEW] = next.notifications.messagePreview
            prefs[Keys.NOTIF_ANDROID] = next.notifications.androidNotifications

            prefs[Keys.CHAT_STRANGERS] = next.chat.allowStrangers
            prefs[Keys.CHAT_FRIENDS] = next.chat.allowFriends
            prefs[Keys.CHAT_CLOSE] = next.chat.allowCloseFriends
            prefs[Keys.CHAT_PARTY] = next.chat.allowParty
            prefs[Keys.CHAT_AUTO_MEDIA] = next.chat.autoDownloadMedia
            prefs[Keys.CHAT_RECEIPTS] = next.chat.readReceipts
            prefs[Keys.CHAT_KEEP_MEDIA] = next.chat.keepMediaOnDevice

            prefs[Keys.MIC_MUTE_ON_JOIN] = next.mic.muteOnJoin
            prefs[Keys.MIC_PUSH_TO_TALK] = next.mic.pushToTalk
            prefs[Keys.MIC_NOISE] = next.mic.noiseSuppression
            prefs[Keys.MIC_ECHO] = next.mic.echoCancellation
            prefs[Keys.MIC_INPUT] = next.mic.inputVolume
            prefs[Keys.MIC_OUTPUT] = next.mic.outputVolume
            prefs[Keys.MIC_VOICE_ACTIVITY] = next.mic.voiceActivity
        }
    }

    private fun read(prefs: Preferences): SocialPersisted = SocialPersisted(
        signedIn = prefs[Keys.SIGNED_IN] ?: false,
        email = prefs[Keys.EMAIL].orEmpty(),
        googleClientId = prefs[Keys.GOOGLE_CLIENT_ID].orEmpty(),
        googleBrowserClientId = prefs[Keys.GOOGLE_BROWSER_CLIENT_ID].orEmpty(),
        googleBrowserSecret = prefs[Keys.GOOGLE_BROWSER_SECRET].orEmpty(),
        name = prefs[Keys.NAME].orEmpty(),
        username = prefs[Keys.USERNAME].orEmpty(),
        usernameChangedAt = prefs[Keys.USERNAME_CHANGED_AT] ?: 0L,
        bio = prefs[Keys.BIO].orEmpty(),
        links = decodeLinks(prefs[Keys.LINKS].orEmpty()),
        avatar = prefs[Keys.AVATAR] ?: 6,
        skinId = prefs[Keys.SKIN] ?: "skin_1",
        capeId = prefs[Keys.CAPE] ?: "cape_none",
        themeWallpaperId = prefs[Keys.THEME].orEmpty(),
        privacy = PrivacySettings(
            onlineStatus = prefs[Keys.PRIVACY_ONLINE] ?: true,
            shareActivity = prefs[Keys.PRIVACY_ACTIVITY] ?: true,
            showServerDetails = prefs[Keys.PRIVACY_SERVER] ?: false,
            profileLock = prefs[Keys.PRIVACY_LOCK] ?: false,
            status = prefs[Keys.PRIVACY_STATUS] ?: "Available to play",
            friendRequests = prefs[Keys.PRIVACY_REQUESTS] ?: "Everyone",
            privateMessages = prefs[Keys.PRIVACY_MESSAGES] ?: "Accepted friends only",
            partyInvites = prefs[Keys.PRIVACY_PARTY] ?: "Accepted friends only"
        ),
        notifications = NotificationSettings(
            friendRequests = prefs[Keys.NOTIF_REQUESTS] ?: true,
            privateMessages = prefs[Keys.NOTIF_MESSAGES] ?: true,
            partyInvites = prefs[Keys.NOTIF_PARTY] ?: true,
            serverInvites = prefs[Keys.NOTIF_SERVER] ?: true,
            inLauncher = prefs[Keys.NOTIF_LAUNCHER] ?: true,
            inGame = prefs[Keys.NOTIF_INGAME] ?: true,
            messagePreview = prefs[Keys.NOTIF_PREVIEW] ?: false,
            androidNotifications = prefs[Keys.NOTIF_ANDROID] ?: false
        ),
        chat = ChatSettings(
            allowStrangers = prefs[Keys.CHAT_STRANGERS] ?: false,
            allowFriends = prefs[Keys.CHAT_FRIENDS] ?: true,
            allowCloseFriends = prefs[Keys.CHAT_CLOSE] ?: true,
            allowParty = prefs[Keys.CHAT_PARTY] ?: true,
            autoDownloadMedia = prefs[Keys.CHAT_AUTO_MEDIA] ?: true,
            readReceipts = prefs[Keys.CHAT_RECEIPTS] ?: true,
            keepMediaOnDevice = prefs[Keys.CHAT_KEEP_MEDIA] ?: true
        ),
        mic = MicSettings(
            muteOnJoin = prefs[Keys.MIC_MUTE_ON_JOIN] ?: true,
            pushToTalk = prefs[Keys.MIC_PUSH_TO_TALK] ?: true,
            noiseSuppression = prefs[Keys.MIC_NOISE] ?: true,
            echoCancellation = prefs[Keys.MIC_ECHO] ?: true,
            inputVolume = prefs[Keys.MIC_INPUT] ?: 70,
            outputVolume = prefs[Keys.MIC_OUTPUT] ?: 80,
            voiceActivity = prefs[Keys.MIC_VOICE_ACTIVITY] ?: true
        )
    )

    /** "YOUTUBE|@kai;DISCORD|kai" — compact and readable in the preferences file. */
    private fun encodeLinks(links: List<SocialLink>): String =
        links.joinToString(";") { "${it.platform.name}|${it.value.replace(";", "").replace("|", "")}" }

    private fun decodeLinks(raw: String): List<SocialLink> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val platform = runCatching { LinkPlatform.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
            if (parts[1].isBlank()) null else SocialLink(platform, parts[1])
        }
    }

    private object Keys {
        val SIGNED_IN = booleanPreferencesKey("social_signed_in")
        val EMAIL = stringPreferencesKey("social_email")
        val GOOGLE_CLIENT_ID = stringPreferencesKey("social_google_client_id")
        val GOOGLE_BROWSER_CLIENT_ID = stringPreferencesKey("social_google_browser_client_id")
        val GOOGLE_BROWSER_SECRET = stringPreferencesKey("social_google_browser_secret")
        val NAME = stringPreferencesKey("social_name")
        val USERNAME = stringPreferencesKey("social_username")
        val USERNAME_CHANGED_AT = longPreferencesKey("social_username_changed_at")
        val BIO = stringPreferencesKey("social_bio")
        val LINKS = stringPreferencesKey("social_links")
        val AVATAR = intPreferencesKey("social_avatar")
        val SKIN = stringPreferencesKey("social_skin")
        val CAPE = stringPreferencesKey("social_cape")
        val THEME = stringPreferencesKey("social_theme_wallpaper")

        val PRIVACY_ONLINE = booleanPreferencesKey("privacy_online_status")
        val PRIVACY_ACTIVITY = booleanPreferencesKey("privacy_share_activity")
        val PRIVACY_SERVER = booleanPreferencesKey("privacy_server_details")
        val PRIVACY_LOCK = booleanPreferencesKey("privacy_profile_lock")
        val PRIVACY_STATUS = stringPreferencesKey("privacy_status_text")
        val PRIVACY_REQUESTS = stringPreferencesKey("privacy_friend_requests")
        val PRIVACY_MESSAGES = stringPreferencesKey("privacy_private_messages")
        val PRIVACY_PARTY = stringPreferencesKey("privacy_party_invites")

        val NOTIF_REQUESTS = booleanPreferencesKey("notif_friend_requests")
        val NOTIF_MESSAGES = booleanPreferencesKey("notif_private_messages")
        val NOTIF_PARTY = booleanPreferencesKey("notif_party_invites")
        val NOTIF_SERVER = booleanPreferencesKey("notif_server_invites")
        val NOTIF_LAUNCHER = booleanPreferencesKey("notif_in_launcher")
        val NOTIF_INGAME = booleanPreferencesKey("notif_in_game")
        val NOTIF_PREVIEW = booleanPreferencesKey("notif_message_preview")
        val NOTIF_ANDROID = booleanPreferencesKey("notif_android")

        val CHAT_STRANGERS = booleanPreferencesKey("chat_allow_strangers")
        val CHAT_FRIENDS = booleanPreferencesKey("chat_allow_friends")
        val CHAT_CLOSE = booleanPreferencesKey("chat_allow_close_friends")
        val CHAT_PARTY = booleanPreferencesKey("chat_allow_party")
        val CHAT_AUTO_MEDIA = booleanPreferencesKey("chat_auto_media")
        val CHAT_RECEIPTS = booleanPreferencesKey("chat_read_receipts")
        val CHAT_KEEP_MEDIA = booleanPreferencesKey("chat_keep_media")

        val MIC_MUTE_ON_JOIN = booleanPreferencesKey("mic_mute_on_join")
        val MIC_PUSH_TO_TALK = booleanPreferencesKey("mic_push_to_talk")
        val MIC_NOISE = booleanPreferencesKey("mic_noise_suppression")
        val MIC_ECHO = booleanPreferencesKey("mic_echo_cancellation")
        val MIC_INPUT = intPreferencesKey("mic_input_volume")
        val MIC_OUTPUT = intPreferencesKey("mic_output_volume")
        val MIC_VOICE_ACTIVITY = booleanPreferencesKey("mic_voice_activity")
    }
}
