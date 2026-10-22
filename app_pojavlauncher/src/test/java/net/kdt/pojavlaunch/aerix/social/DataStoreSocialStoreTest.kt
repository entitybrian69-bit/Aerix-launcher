package net.kdt.pojavlaunch.aerix.social

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kdt.pojavlaunch.aerix.social.data.DataStoreSocialStore
import net.kdt.pojavlaunch.aerix.social.data.NotificationSettings
import net.kdt.pojavlaunch.aerix.social.data.PrivacySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The real DataStore implementation, exercised on the JVM. This is the test that proves the
 * settings a player toggles are actually written somewhere durable.
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
        assertTrue(defaults.privacy.onlineStatus)
        assertTrue(defaults.privacy.shareActivity)
        assertFalse(defaults.privacy.showServerDetails)
        assertEquals("Everyone", defaults.privacy.friendRequests)
        assertEquals("Accepted friends only", defaults.privacy.privateMessages)
        assertEquals("Accepted friends only", defaults.privacy.partyInvites)
        assertTrue(defaults.notifications.friendRequests)
        assertFalse(defaults.notifications.messagePreview)
        assertFalse(defaults.notifications.androidNotifications)
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun profileAndSettingsAreWrittenAndReadBack() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val file = File(temporary.newFolder(), "social.preferences_pb")
        val socialStore = store(scope, file)

        socialStore.update {
            it.copy(
                signedIn = true,
                name = "Nova",
                skinId = "skin_3",
                capeId = "cape_aerix",
                privacy = it.privacy.copy(onlineStatus = false, showServerDetails = true, partyInvites = "Nobody"),
                notifications = it.notifications.copy(messagePreview = true, androidNotifications = true)
            )
        }

        val persisted = socialStore.data.first()
        assertTrue(persisted.signedIn)
        assertEquals("Nova", persisted.name)
        assertEquals("skin_3", persisted.skinId)
        assertEquals("cape_aerix", persisted.capeId)
        assertFalse(persisted.privacy.onlineStatus)
        assertTrue(persisted.privacy.showServerDetails)
        assertEquals("Nobody", persisted.privacy.partyInvites)
        assertTrue(persisted.notifications.messagePreview)
        assertTrue(persisted.notifications.androidNotifications)

        // A second update must merge with what is already there, not reset it.
        socialStore.update { it.copy(privacy = it.privacy.copy(onlineStatus = true)) }
        val merged = socialStore.data.first()
        assertTrue(merged.privacy.onlineStatus)
        assertEquals("Nova", merged.name)
        assertEquals("skin_3", merged.skinId)
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun dataClassDefaultsAreExplicit() {
        val privacy = PrivacySettings()
        val notifications = NotificationSettings()
        assertFalse(privacy.showServerDetails)
        assertFalse(notifications.messagePreview)
    }
}
