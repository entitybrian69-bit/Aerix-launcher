/*
 * Aerix Launcher — regression tests for Social shell state and avatar identity.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.screens.social

import net.kdt.pojavlaunch.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SocialShellStateTest {

    @Before
    fun closeShell() = SocialShellState.exit()

    @Test
    fun shellStartsClosedAndEnterExitToggleIt() {
        assertFalse(SocialShellState.isOpen)
        SocialShellState.enter()
        assertTrue(SocialShellState.isOpen)
        SocialShellState.exit()
        assertFalse(SocialShellState.isOpen)
    }

    @Test
    fun avatarChoiceIsStableForAName() {
        assertEquals(socialAvatarRes("Kai"), socialAvatarRes("Kai"))
        assertEquals(socialAvatarRes("Nova"), socialAvatarRes("Nova"))
    }

    @Test
    fun avatarChoiceAlwaysLandsOnOneOfTheSixHumans() {
        val avatars = setOf(
            R.drawable.aerix_social_avatar_1,
            R.drawable.aerix_social_avatar_2,
            R.drawable.aerix_social_avatar_3,
            R.drawable.aerix_social_avatar_4,
            R.drawable.aerix_social_avatar_5,
            R.drawable.aerix_social_avatar_6
        )
        listOf("Kai", "Mira", "Luna", "Alex", "Sam", "Ash", "Rowan", "Nova", "").forEach { name ->
            assertTrue(socialAvatarRes(name) in avatars)
        }
    }

    @Test
    fun mockDataMatchesTheApprovedSession() {
        SocialHubStore.reset()
        assertEquals(4, SocialHubStore.onlineCount)
        assertEquals(listOf("Ash", "Rowan"), SocialHubStore.requests.map { it.name })
        assertEquals(null, SocialHubStore.party.value)
        assertEquals("Nova", SocialProfileStore.aerixName)
        assertEquals("None", SocialProfileStore.capeName)
    }
}
