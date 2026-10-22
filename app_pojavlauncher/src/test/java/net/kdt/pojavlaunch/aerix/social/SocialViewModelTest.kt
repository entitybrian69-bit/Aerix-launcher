package net.kdt.pojavlaunch.aerix.social

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kdt.pojavlaunch.aerix.social.data.InMemorySocialStore
import net.kdt.pojavlaunch.aerix.social.data.MockSocialRepository
import net.kdt.pojavlaunch.aerix.social.vm.SocialChatViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialDashboardViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialProfileViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialSettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** ViewModel state changes driven by repository events. */
@OptIn(ExperimentalCoroutinesApi::class)
class SocialViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun repository(dispatcher: TestDispatcher) = MockSocialRepository(io = dispatcher)

    @Test
    fun acceptingARequestMovesTheSenderIntoFriends() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialDashboardViewModel(repository)

        val request = repository.requests.value.first()
        val friendsBefore = repository.friends.value.size

        viewModel.accept(request.id)
        advanceUntilIdle()

        assertTrue(repository.requests.value.none { it.id == request.id })
        assertEquals(friendsBefore + 1, repository.friends.value.size)
        assertTrue(repository.friends.value.any { it.name == request.fromName })
    }

    @Test
    fun decliningARequestOnlyRemovesTheRequest() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialDashboardViewModel(repository)

        val request = repository.requests.value.first()
        val friendsBefore = repository.friends.value.size

        viewModel.decline(request.id)
        advanceUntilIdle()

        assertTrue(repository.requests.value.none { it.id == request.id })
        assertEquals(friendsBefore, repository.friends.value.size)
    }

    @Test
    fun sendingAMessageAppendsItAndClearsTheDraft() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialChatViewModel(repository)
        val kai = repository.friends.value.first { it.name == "Kai" }

        viewModel.open(kai)
        advanceUntilIdle()
        val before = viewModel.state.value.messages.size

        viewModel.onDraftChange("see you at spawn")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(before + 1, viewModel.state.value.messages.size)
        assertEquals("see you at spawn", viewModel.state.value.messages.last().text)
        assertEquals("", viewModel.state.value.draft)
    }

    @Test
    fun reactionTogglesOnAndOff() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialChatViewModel(repository)
        val kai = repository.friends.value.first { it.name == "Kai" }

        viewModel.open(kai)
        advanceUntilIdle()
        val message = viewModel.state.value.messages.first()

        viewModel.react(message.id, "like")
        advanceUntilIdle()
        assertEquals("like", viewModel.state.value.messages.first().myReaction)
        assertEquals(1, viewModel.state.value.messages.first().reactions["like"])

        viewModel.react(message.id, "like")
        advanceUntilIdle()
        assertNull(viewModel.state.value.messages.first().myReaction)
        assertNull(viewModel.state.value.messages.first().reactions["like"])
    }
}

/** Profile validation and DataStore-backed persistence, exercised through the store contract. */
@OptIn(ExperimentalCoroutinesApi::class)
class SocialProfileViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun shortNamesAreRejectedBeforeAnythingIsWritten() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store)
        advanceUntilIdle()

        viewModel.onNameChange("ab")
        assertFalse(viewModel.saveAppearance())
        assertNotNull(viewModel.state.value.nameError)
        assertFalse(store.snapshot().signedIn)
    }

    @Test
    fun validNameIsPersistedAndSignsTheUserIn() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store)
        advanceUntilIdle()

        var finished = false
        viewModel.onNameChange("Nova_1")
        assertTrue(viewModel.saveAppearance { finished = true })
        advanceUntilIdle()

        assertTrue(finished)
        assertEquals("Nova_1", store.snapshot().name)
        assertTrue(store.snapshot().signedIn)
        assertNull(viewModel.state.value.nameError)
    }

    @Test
    fun signingOutKeepsTheProfileName() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store)
        advanceUntilIdle()

        viewModel.signIn()
        advanceUntilIdle()
        viewModel.onNameChange("Kai")
        viewModel.saveAppearance()
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertFalse(store.snapshot().signedIn)
        assertEquals("Kai", store.snapshot().name)
    }
}

/** Settings toggles are written straight through to the store. */
@OptIn(ExperimentalCoroutinesApi::class)
class SocialSettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun privacyToggleIsPersisted() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialSettingsViewModel(store)
        advanceUntilIdle()

        viewModel.setOnlineStatus(false)
        advanceUntilIdle()

        assertFalse(store.snapshot().privacy.onlineStatus)
    }

    @Test
    fun notificationTogglesAndDropdownsArePersisted() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialSettingsViewModel(store)
        advanceUntilIdle()

        viewModel.setMessagePreview(true)
        viewModel.setFriendRequests("Nobody")
        advanceUntilIdle()

        assertTrue(store.snapshot().notifications.messagePreview)
        assertEquals("Nobody", store.snapshot().privacy.friendRequests)
        assertEquals("Nobody", viewModel.state.value.privacy.friendRequests)
    }
}
