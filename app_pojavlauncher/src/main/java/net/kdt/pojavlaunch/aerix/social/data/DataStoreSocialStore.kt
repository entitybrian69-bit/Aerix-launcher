/*
 * Aerix Launcher Social layer.
 *
 * DataStore implementation of [SocialStore]. One preferences file holds the profile
 * (name, skin, cape, sign-in flag) and the privacy / notification settings.
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
            prefs[Keys.NAME] = next.name
            prefs[Keys.SKIN] = next.skinId
            prefs[Keys.CAPE] = next.capeId

            prefs[Keys.PRIVACY_ONLINE] = next.privacy.onlineStatus
            prefs[Keys.PRIVACY_ACTIVITY] = next.privacy.shareActivity
            prefs[Keys.PRIVACY_SERVER] = next.privacy.showServerDetails
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
        }
    }

    private fun read(prefs: Preferences): SocialPersisted = SocialPersisted(
        signedIn = prefs[Keys.SIGNED_IN] ?: false,
        name = prefs[Keys.NAME].orEmpty(),
        skinId = prefs[Keys.SKIN] ?: "skin_1",
        capeId = prefs[Keys.CAPE] ?: "cape_none",
        privacy = PrivacySettings(
            onlineStatus = prefs[Keys.PRIVACY_ONLINE] ?: true,
            shareActivity = prefs[Keys.PRIVACY_ACTIVITY] ?: true,
            showServerDetails = prefs[Keys.PRIVACY_SERVER] ?: false,
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
        )
    )

    private object Keys {
        val SIGNED_IN = booleanPreferencesKey("social_signed_in")
        val NAME = stringPreferencesKey("social_name")
        val SKIN = stringPreferencesKey("social_skin")
        val CAPE = stringPreferencesKey("social_cape")

        val PRIVACY_ONLINE = booleanPreferencesKey("privacy_online_status")
        val PRIVACY_ACTIVITY = booleanPreferencesKey("privacy_share_activity")
        val PRIVACY_SERVER = booleanPreferencesKey("privacy_server_details")
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
    }
}
