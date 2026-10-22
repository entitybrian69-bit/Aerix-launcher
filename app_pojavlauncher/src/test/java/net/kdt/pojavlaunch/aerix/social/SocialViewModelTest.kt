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
import net.kdt.pojavlaunch.aerix.social.data.AttachmentKind
import net.kdt.pojavlaunch.aerix.social.data.InMemorySocialStore
import net.kdt.pojavlaunch.aerix.social.data.InstanceShareState
import net.kdt.pojavlaunch.aerix.social.data.LinkPlatform
import net.kdt.pojavlaunch.aerix.social.data.MockSocialRepository
import net.kdt.pojavlaunch.aerix.social.data.PostKind
import net.kdt.pojavlaunch.aerix.social.data.SocialLink
import net.kdt.pojavlaunch.aerix.social.data.SocialPersisted
import net.kdt.pojavlaunch.aerix.social.data.formatCooldown
import net.kdt.pojavlaunch.aerix.social.vm.SocialActivityViewModel
import net.kdt.pojavlaunch.aerix.social.vm.SocialAddFriendViewModel
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

    @Test
    fun acceptingARequestMovesTheSenderIntoFriends() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialDashboardViewModel(repository)

        val request = repository.requests.value.first { it.fromName == "Nova" }
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
    fun dashboardSplitsFriendsIntoOnlineAndOffline() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialDashboardViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.online.isNotEmpty())
        assertTrue(viewModel.state.value.offline.isNotEmpty())
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

    @Test
    fun attachingAnInstanceCreatesADownloadableMessage() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialChatViewModel(repository)
        val kai = repository.friends.value.first { it.name == "Kai" }

        viewModel.open(kai)
        advanceUntilIdle()
        viewModel.attach(AttachmentKind.INSTANCE)
        advanceUntilIdle()

        val message = viewModel.state.value.messages.last()
        assertEquals(AttachmentKind.INSTANCE, message.attachments.first().kind)
        assertEquals(InstanceShareState.IDLE, viewModel.state.value.shares[message.id]?.state)

        viewModel.install(message.id)
        advanceUntilIdle()
        assertEquals(InstanceShareState.READY, viewModel.state.value.shares[message.id]?.state)
    }

    @Test
    fun musicPlaybackIsAToggle() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialChatViewModel(repository)
        val luna = repository.friends.value.first { it.name == "Luna" }

        viewModel.open(luna)
        advanceUntilIdle()
        val music = viewModel.state.value.messages.first { it.attachments.any { a -> a.kind == AttachmentKind.MUSIC } }

        viewModel.toggleMusic(music.id)
        assertEquals(music.id, viewModel.state.value.playingMusicId)
        viewModel.toggleMusic(music.id)
        assertNull(viewModel.state.value.playingMusicId)
    }

    @Test
    fun searchingByEmailFindsTheAccountAndSendsARequest() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialAddFriendViewModel(repository)

        viewModel.setByEmail(true)
        viewModel.onQueryChange("alex@gmail.com")
        viewModel.search()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.results.size)
        val userId = viewModel.state.value.results.first().user.id
        viewModel.send(userId)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sentTo.contains(userId))
        assertTrue(repository.requests.value.any { it.fromUserId == userId })
    }

    @Test
    fun searchingByUsernameOnlyReturnsStrangers() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialAddFriendViewModel(repository)

        viewModel.onQueryChange("nova")
        viewModel.search()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.results.isNotEmpty())
        assertTrue(viewModel.state.value.results.none { it.user.friend })
    }

    @Test
    fun likingFollowingAndCommentingUpdateTheFeed() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialActivityViewModel(repository)
        advanceUntilIdle()

        val post = viewModel.state.value.posts.first()
        viewModel.toggleLike(post.id)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.posts.first { it.id == post.id }.liked)

        viewModel.toggleFollow(post.authorId)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.posts.first { it.authorId == post.authorId }.following)

        viewModel.openComments(post.id)
        viewModel.onCommentChange("nice build")
        viewModel.submitComment()
        advanceUntilIdle()
        assertEquals("nice build", viewModel.state.value.posts.first { it.id == post.id }.comments.last().text)
    }

    @Test
    fun publishingAPostAddsItToTheTop() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialActivityViewModel(repository)
        advanceUntilIdle()
        val before = viewModel.state.value.posts.size

        viewModel.openComposer()
        viewModel.onCaptionChange("first post")
        viewModel.publish(PostKind.PHOTO)
        advanceUntilIdle()

        assertEquals(before + 1, viewModel.state.value.posts.size)
        assertEquals("first post", viewModel.state.value.posts.first().caption)
    }

    @Test
    fun creatingAndRenamingAPartyKeepsItsMembers() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = MockSocialRepository(io = dispatcher)
        val viewModel = SocialDashboardViewModel(repository)

        viewModel.createParty("Builders", 2)
        advanceUntilIdle()
        assertEquals("Builders", repository.party.value.name)
        assertTrue(repository.party.value.members.size >= 3)

        repository.renameParty("Builders United")
        repository.startGroupCall()
        advanceUntilIdle()
        assertEquals("Builders United", repository.party.value.name)
        assertTrue(repository.party.value.inCall)
    }
}

/** Profile, the one-week username cooldown and DataStore-backed persistence. */
@OptIn(ExperimentalCoroutinesApi::class)
class SocialProfileViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun shortNamesAreRejectedBeforeAnythingIsWritten() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store, UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.onNameChange("ab")
        assertFalse(viewModel.saveCharacter())
        assertNotNull(viewModel.state.value.nameError)
        assertFalse(store.snapshot().signedIn)
    }

    @Test
    fun validNameIsPersistedAndSignsTheUserIn() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store, UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        var finished = false
        viewModel.onNameChange("Nova_1")
        viewModel.onUsernameChange("nova_plays")
        assertTrue(viewModel.saveCharacter { finished = true })
        advanceUntilIdle()

        assertTrue(finished)
        assertEquals("Nova_1", store.snapshot().name)
        assertEquals("nova_plays", store.snapshot().username)
        assertTrue(store.snapshot().signedIn)
        assertNull(viewModel.state.value.nameError)
    }

    @Test
    fun usernameCanOnlyChangeOnceAWeek() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store, UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.onNameChange("Nova")
        viewModel.onUsernameChange("nova_one")
        viewModel.saveCharacter()
        advanceUntilIdle()
        val firstChange = store.snapshot().usernameChangedAt
        assertTrue(firstChange > 0L)

        // A second change right away is rejected and the stored username stays.
        assertTrue(viewModel.changeUsername("nova_two"))
        advanceUntilIdle()
        assertFalse(viewModel.changeUsername("nova_three"))
        assertEquals("nova_two", store.snapshot().username)
        assertNotNull(viewModel.state.value.nameError)

        // A week later it is allowed again.
        val later = SocialPersisted(
            usernameChangedAt = System.currentTimeMillis() - SocialPersisted.USERNAME_COOLDOWN_MS - 1000
        )
        assertTrue(later.canChangeUsername(System.currentTimeMillis()))
        assertEquals("available now", formatCooldown(0))
    }

    @Test
    fun bioAndLinksArePersisted() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store, UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.onBioChange("Survival builder")
        viewModel.addLink(LinkPlatform.YOUTUBE, "@nova")
        viewModel.addLink(LinkPlatform.DISCORD, "nova#0001")
        advanceUntilIdle()

        assertEquals("Survival builder", store.snapshot().bio)
        assertEquals(2, store.snapshot().links.size)
        assertEquals("https://youtube.com/@nova", store.snapshot().links.first { it.platform == LinkPlatform.YOUTUBE }.url)

        viewModel.removeLink(LinkPlatform.DISCORD)
        advanceUntilIdle()
        assertEquals(1, store.snapshot().links.size)
    }

    @Test
    fun signingOutKeepsTheProfileName() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store, UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.signIn()
        advanceUntilIdle()
        viewModel.onNameChange("Kai")
        viewModel.saveCharacter()
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertFalse(store.snapshot().signedIn)
        assertEquals("Kai", store.snapshot().name)
    }

    @Test
    fun deletingAnAccountClearsEverything() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store, UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.onNameChange("Nova")
        viewModel.saveCharacter()
        viewModel.onBioChange("hello")
        advanceUntilIdle()

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(SocialPersisted(), store.snapshot())
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
        val viewModel = SocialSettingsViewModel(store, UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.setOnlineStatus(false)
        advanceUntilIdle()

        assertFalse(store.snapshot().privacy.onlineStatus)
    }

    @Test
    fun profileLockAndStatusArePersisted() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialSettingsViewModel(store, UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.setProfileLock(true)
        viewModel.setStatus("Do not disturb")
        advanceUntilIdle()

        assertTrue(store.snapshot().privacy.profileLock)
        assertEquals("Do not disturb", viewModel.state.value.privacy.status)
    }

    @Test
    fun chatPermissionsArePersisted() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialSettingsViewModel(store, UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.setAllowStrangers(true)
        viewModel.setAllowParty(false)
        viewModel.setReadReceipts(false)
        advanceUntilIdle()

        val chat = viewModel.state.value.chat
        assertTrue(chat.allowStrangers)
        assertFalse(chat.allowParty)
        assertFalse(chat.readReceipts)
        assertTrue(store.snapshot().chat.allowStrangers)
    }

    @Test
    fun microphoneSettingsArePersisted() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialSettingsViewModel(store, UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.setMuteOnJoin(false)
        viewModel.setInputVolume(42)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.mic.muteOnJoin)
        assertEquals(42, store.snapshot().mic.inputVolume)
    }

    @Test
    fun notificationTogglesAndDropdownsArePersisted() = runTest {
        val store = InMemorySocialStore()
        val viewModel = SocialSettingsViewModel(store, UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.setMessagePreview(true)
        viewModel.setFriendRequests("Nobody")
        advanceUntilIdle()

        assertTrue(store.snapshot().notifications.messagePreview)
        assertEquals("Nobody", store.snapshot().privacy.friendRequests)
        assertEquals("Nobody", viewModel.state.value.privacy.friendRequests)
    }
}
