/*
 * Aerix Launcher Social layer.
 *
 * Proves that Social keeps what the player made: a repository written to the content store and
 * reopened must come back with the same friends, conversation, party and read state instead of
 * the seed data.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kdt.pojavlaunch.aerix.social.data.AttachmentKind
import net.kdt.pojavlaunch.aerix.social.data.ChatAttachment
import net.kdt.pojavlaunch.aerix.social.data.InMemorySocialContentStore
import net.kdt.pojavlaunch.aerix.social.data.LocalSocialRepository
import net.kdt.pojavlaunch.aerix.social.data.SocialContent
import net.kdt.pojavlaunch.aerix.social.data.SocialJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SocialPersistenceTest {

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun aConversationSurvivesAReopen() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = InMemorySocialContentStore()
        val first = LocalSocialRepository(io = dispatcher, contentStore = store)
        advanceUntilIdle()

        val kai = first.friends.value.first { it.name == "Kai" }
        first.ensureChat(kai.id)
        first.sendMessage(kai.id, "still here after a restart")
        advanceUntilIdle()

        val reopened = LocalSocialRepository(io = dispatcher, contentStore = store)
        advanceUntilIdle()

        val messages = reopened.chats.value[kai.id].orEmpty()
        assertTrue(messages.any { it.text == "still here after a restart" })
        assertEquals(first.friends.value.map { it.id }.sorted(), reopened.friends.value.map { it.id }.sorted())
    }

    @Test
    fun anAttachmentKeepsItsRealPath() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = InMemorySocialContentStore()
        val repository = LocalSocialRepository(io = dispatcher, contentStore = store)
        advanceUntilIdle()

        val kai = repository.friends.value.first { it.name == "Kai" }
        repository.sendMessage(
            kai.id,
            "",
            ChatAttachment(
                kind = AttachmentKind.IMAGE,
                title = "screenshot.png",
                payload = "/data/user/0/app/files/social_media/a.png",
                localPath = "/data/user/0/app/files/social_media/a.png",
                mimeType = "image/png"
            )
        )
        advanceUntilIdle()

        val reopened = LocalSocialRepository(io = dispatcher, contentStore = store)
        advanceUntilIdle()

        val attachment = reopened.chats.value[kai.id].orEmpty()
            .flatMap { it.attachments }
            .first { it.kind == AttachmentKind.IMAGE }
        assertEquals("/data/user/0/app/files/social_media/a.png", attachment.localPath)
        assertEquals("image/png", attachment.mimeType)
    }

    @Test
    fun readStateIsRemembered() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = InMemorySocialContentStore()
        val repository = LocalSocialRepository(io = dispatcher, contentStore = store)
        advanceUntilIdle()

        val kai = repository.friends.value.first { it.name == "Kai" }
        repository.receiveMessage(kai.id, "Kai", "hello")
        advanceUntilIdle()
        val messageId = repository.chats.value[kai.id].orEmpty().last().id

        repository.markSeen(listOf(messageId))
        advanceUntilIdle()

        val reopened = LocalSocialRepository(io = dispatcher, contentStore = store)
        advanceUntilIdle()
        assertTrue(reopened.seenMessages().contains(messageId))
    }

    @Test
    fun jsonRoundTripsTheWholeSnapshot() {
        val snapshot = SocialContent(
            party = net.kdt.pojavlaunch.aerix.social.data.PartyState(joined = true, name = "Night Shift"),
            seenMessageIds = setOf("m1", "m2")
        )
        val decoded = SocialJson.decode(SocialJson.encode(snapshot))
        assertEquals("Night Shift", decoded.party.name)
        assertTrue(decoded.party.joined)
        assertEquals(setOf("m1", "m2"), decoded.seenMessageIds)
    }
}
