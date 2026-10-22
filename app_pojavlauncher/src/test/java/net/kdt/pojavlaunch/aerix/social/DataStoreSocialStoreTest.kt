package net.kdt.pojavlaunch.aerix.social

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kdt.pojavlaunch.aerix.social.data.DataStoreSocialStore
import net.kdt.pojavlaunch.aerix.social.data.LinkPlatform
import net.kdt.pojavlaunch.aerix.social.data.NotificationSettings
import net.kdt.pojavlaunch.aerix.social.data.PrivacySettings
import net.kdt.pojavlaunch.aerix.social.data.SocialLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The real DataStore implementation, exercised on the JVM. These are the tests that prove the
 * profile, links, chat rules and microphone settings a player picks survive a restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSocialStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun store(scope: CoroutineScope, file: File) = DataStoreSocialStore(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    )

    @Test
    fun defaultsMatchTheMockups() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val file = File(temporary.newFolder(), "social.preferences_pb")
        val socialStore = store(scope, file)

        val defaults = socialStore.data.first()

        assertFalse(defaults.signedIn)
        assertEquals("skin_1", defaults.skinId)
        assertEquals("cape_none", defaults.capeId)
        assertEquals("", defaults.username)
        assertEquals(0L, defaults.usernameChangedAt)
        assertTrue(defaults.privacy.onlineStatus)
        assertFalse(defaults.privacy.profileLock)
        assertEquals("Everyone", defaults.privacy.friendRequests)
        assertEquals("Accepted friends only", defaults.privacy.privateMessages)
        assertTrue(defaults.notifications.friendRequests)
        assertFalse(defaults.notifications.messagePreview)
        assertFalse(defaults.chat.allowStrangers)
        assertTrue(defaults.chat.allowFriends)
        assertTrue(defaults.mic.muteOnJoin)
        assertEquals(70, defaults.mic.inputVolume)
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun profileLinksAndSettingsAreWrittenAndReadBack() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val file = File(temporary.newFolder(), "social.preferences_pb")
        val socialStore = store(scope, file)

        socialStore.update {
            it.copy(
                signedIn = true,
                name = "Nova",
                username = "nova_plays",
                usernameChangedAt = 1_700_000_000_000L,
                bio = "Survival builder",
                links = listOf(
                    SocialLink(LinkPlatform.YOUTUBE, "@nova"),
                    SocialLink(LinkPlatform.DISCORD, "nova")
                ),
                avatar = 3,
                skinId = "skin_3",
                capeId = "cape_aerix",
                themeWallpaperId = "wp_06_the_end",
                privacy = it.privacy.copy(profileLock = true, status = "In a party", onlineStatus = false),
                notifications = it.notifications.copy(messagePreview = true),
                chat = it.chat.copy(allowStrangers = true, allowParty = false),
                mic = it.mic.copy(muteOnJoin = false, inputVolume = 45)
            )
        }

        val persisted = socialStore.data.first()
        assertTrue(persisted.signedIn)
        assertEquals("Nova", persisted.name)
        assertEquals("nova_plays", persisted.username)
        assertEquals(1_700_000_000_000L, persisted.usernameChangedAt)
        assertEquals("Survival builder", persisted.bio)
        assertEquals(2, persisted.links.size)
        assertEquals("https://youtube.com/@nova", persisted.links.first().url)
        assertEquals(3, persisted.avatar)
        assertEquals("wp_06_the_end", persisted.themeWallpaperId)
        assertTrue(persisted.privacy.profileLock)
        assertEquals("In a party", persisted.privacy.status)
        assertFalse(persisted.privacy.onlineStatus)
        assertTrue(persisted.notifications.messagePreview)
        assertTrue(persisted.chat.allowStrangers)
        assertFalse(persisted.chat.allowParty)
        assertFalse(persisted.mic.muteOnJoin)
        assertEquals(45, persisted.mic.inputVolume)

        // A second update must merge with what is already there, not reset it.
        socialStore.update { it.copy(privacy = it.privacy.copy(onlineStatus = true)) }
        val merged = socialStore.data.first()
        assertTrue(merged.privacy.onlineStatus)
        assertEquals("nova_plays", merged.username)
        assertEquals(2, merged.links.size)
        assertEquals("wp_06_the_end", merged.themeWallpaperId)
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun dataClassDefaultsAreExplicit() {
        val privacy = PrivacySettings()
        val notifications = NotificationSettings()
        assertFalse(privacy.showServerDetails)
        assertFalse(privacy.profileLock)
        assertFalse(notifications.messagePreview)
    }
}
