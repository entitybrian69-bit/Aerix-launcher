/*
 * Aerix Launcher — regression tests for the Social hub mock store.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.screens.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SocialHubStoreTest {

    @Before
    fun resetStore() = SocialHubStore.reset()

    @Test
    fun acceptingARequestAddsTheFriendAndRemovesTheRequest() {
        val request = SocialHubStore.requests.first { it.name == "Ash" }
        SocialHubStore.accept(request)
        assertNull(SocialHubStore.requests.firstOrNull { it.name == "Ash" })
        assertEquals("Online in Aerix", SocialHubStore.friend("Ash")?.presence?.label)
    }

    @Test
    fun decliningAndCancellingBothRemoveTheRequest() {
        SocialHubStore.requests.toList().forEach { SocialHubStore.decline(it) }
        assertTrue(SocialHubStore.requests.isEmpty())
        assertNull(SocialHubStore.friend("Ash"))
        assertNull(SocialHubStore.friend("Rowan"))
    }

    @Test
    fun blockingRemovesFriendChatAndPartyMembership() {
        SocialHubStore.createParty()
        SocialHubStore.invite("Kai")
        SocialHubStore.block("Kai")
        assertNull(SocialHubStore.friend("Kai"))
        assertNull(SocialHubStore.conversation("Kai"))
        assertTrue(SocialHubStore.blocked.contains("Kai"))
        assertFalse(SocialHubStore.party.value?.members?.contains("Kai") ?: true)
    }

    @Test
    fun unblockingDoesNotRestoreFriendship() {
        SocialHubStore.block("Kai")
        SocialHubStore.unblock("Kai")
        assertFalse(SocialHubStore.blocked.contains("Kai"))
        assertNull(SocialHubStore.friend("Kai"))
    }

    @Test
    fun reportingTheSamePlayerTwiceKeepsOneRecord() {
        SocialHubStore.report("Kai")
        SocialHubStore.report("Kai")
        assertEquals(1, SocialHubStore.reported.count { it == "Kai" })
    }

    @Test
    fun sendingAMessageTrimsItAndIgnoresBlankInput() {
        val before = SocialHubStore.conversation("Kai")!!.messages.size
        SocialHubStore.sendMessage("Kai", "   ")
        assertEquals(before, SocialHubStore.conversation("Kai")!!.messages.size)
        SocialHubStore.sendMessage("Kai", "  see you soon  ")
        val messages = SocialHubStore.conversation("Kai")!!.messages
        assertEquals(before + 1, messages.size)
        assertEquals("see you soon", messages.last().text)
        assertTrue(messages.last().fromMe)
    }

    @Test
    fun invitingAFriendAddsThemToThePartyOnlyOnce() {
        SocialHubStore.createParty()
        SocialHubStore.invite("Kai")
        SocialHubStore.invite("Kai")
        assertEquals(1, SocialHubStore.party.value!!.members.count { it == "Kai" })
        SocialHubStore.invite("Ash")
        assertTrue(SocialHubStore.party.value!!.members.contains("Ash"))
    }

    @Test
    fun aerixNamesAllowOnlyThreeToSixteenWordCharacters() {
        assertTrue(isValidAerixName("Nova_7"))
        assertFalse(isValidAerixName("ab"))
        assertFalse(isValidAerixName("has space"))
        assertFalse(isValidAerixName("dash-name"))
        assertFalse(isValidAerixName("x".repeat(17)))
    }

    @Test
    fun aNewSessionHasNoPartyUntilOneIsCreated() {
        assertNull(SocialHubStore.party.value)
        SocialHubStore.createParty("Survival crew")
        assertTrue(SocialHubStore.party.value!!.members.contains(SocialProfileStore.aerixName))
        SocialHubStore.leaveParty()
        assertNull(SocialHubStore.party.value)
    }

    @Test
    fun reactingTwiceCountsTwiceAndPicksKnownEmoji() {
        SocialHubStore.react("Kai", 0, "👍")
        SocialHubStore.react("Kai", 0, "👍")
        val reactions = SocialHubStore.conversation("Kai")!!.messages[0].reactions
        assertEquals(1, reactions.size)
        assertEquals(2, reactions.first().second)
    }

    @Test
    fun blockingAndReportingAreIndependentRecords() {
        SocialHubStore.block("Kai")
        SocialHubStore.report("Kai")
        assertTrue(SocialHubStore.blocked.contains("Kai"))
        assertTrue(SocialHubStore.reported.contains("Kai"))
        assertNull(SocialHubStore.friend("Kai"))
    }

    @Test
    fun resetRestoresTheMockSession() {
        SocialHubStore.block("Kai")
        SocialHubStore.reset()
        assertEquals(5, SocialHubStore.friends.size)
        assertEquals(2, SocialHubStore.requests.size)
        assertTrue(SocialHubStore.blocked.isEmpty())
        assertTrue(SocialHubStore.reported.isEmpty())
        assertEquals(0, SocialHubStore.hubTab)
        assertEquals(SocialHubState.FRIENDS, SocialHubStore.state)
        assertEquals(4, SocialHubStore.onlineCount)
    }
}
