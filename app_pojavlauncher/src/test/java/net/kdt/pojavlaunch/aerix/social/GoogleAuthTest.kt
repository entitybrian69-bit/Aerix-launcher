/*
 * Aerix Launcher Social layer.
 *
 * Tests for the real Google sign-in path: reading the claims out of the ID token Google returns,
 * and what a successful sign-in writes into the local profile.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kdt.pojavlaunch.aerix.social.auth.GoogleAccount
import net.kdt.pojavlaunch.aerix.social.auth.GoogleIdToken
import net.kdt.pojavlaunch.aerix.social.data.InMemorySocialStore
import net.kdt.pojavlaunch.aerix.social.data.LocalSocialRepository
import net.kdt.pojavlaunch.aerix.social.vm.SignInOutcome
import net.kdt.pojavlaunch.aerix.social.vm.SocialProfileViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleAuthTest {

    @get:Rule
    val main = MainDispatcherRule()

    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body.signature-ignored"
    }

    @Test
    fun readingTheIdTokenGivesTheEmailAndName() {
        val claims = GoogleIdToken.parseClaims(
            jwt("""{"email":"nova@gmail.com","name":"Nova","sub":"1024"}""")
        )
        assertEquals("nova@gmail.com", claims?.email)
        assertEquals("Nova", claims?.name)
        assertEquals("1024", claims?.subject)
    }

    @Test
    fun aMalformedTokenIsRejectedInsteadOfCrashing() {
        assertNull(GoogleIdToken.parseClaims("not-a-jwt"))
        assertNull(GoogleIdToken.parseClaims("onlyonepart"))
        assertNull(GoogleIdToken.parseClaims("header.%%%notbase64%%%.sig"))
    }

    @Test
    fun base64UrlDecodesWithoutPadding() {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val text = "aerix launcher · 100%"
        val decoded = String(GoogleIdToken.base64UrlDecode(encoder.encodeToString(text.toByteArray())))
        assertEquals(text, decoded)
    }

    @Test
    fun signingInStoresTheGoogleAccountOnTheProfile() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store, dispatcher)
        var outcome = SignInOutcome.RETURNING

        viewModel.signInWithGoogle(
            GoogleAccount(email = "nova@gmail.com", displayName = "Nova", idToken = "token")
        ) { outcome = it }
        advanceUntilIdle()

        assertEquals(SignInOutcome.NEW_ACCOUNT, outcome)
        assertTrue(viewModel.state.value.signedIn)
        assertEquals("nova@gmail.com", viewModel.state.value.email)
        assertEquals("Nova", viewModel.state.value.name)
        assertEquals("nova@gmail.com", viewModel.snapshot().email)

        val persisted = store.data.first()
        assertTrue(persisted.signedIn)
        assertEquals("nova@gmail.com", persisted.email)
    }

    @Test
    fun signingInAgainKeepsTheChosenName() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store, dispatcher)
        var outcome = SignInOutcome.NEW_ACCOUNT

        viewModel.signInWithGoogle(
            GoogleAccount(email = "nova@gmail.com", displayName = "Nova", idToken = "token")
        )
        advanceUntilIdle()
        viewModel.onNameChange("Kai")
        viewModel.saveCharacter()
        advanceUntilIdle()

        viewModel.signInWithGoogle(
            GoogleAccount(email = "nova@gmail.com", displayName = "Nova", idToken = "token")
        ) { outcome = it }
        advanceUntilIdle()

        assertEquals(SignInOutcome.RETURNING, outcome)
        assertEquals("Kai", viewModel.state.value.name)
    }

    @Test
    fun signingInWithADifferentGoogleAccountStartsFresh() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = InMemorySocialStore()
        val repository = LocalSocialRepository(io = dispatcher)
        val viewModel = SocialProfileViewModel(store, dispatcher, repository)
        advanceUntilIdle()

        viewModel.signInWithGoogle(
            GoogleAccount(email = "nova@gmail.com", displayName = "Nova", idToken = "token")
        )
        advanceUntilIdle()
        viewModel.onNameChange("Kai")
        viewModel.saveCharacter()
        advanceUntilIdle()
        assertTrue(repository.friends.value.isNotEmpty())

        var outcome = SignInOutcome.RETURNING
        viewModel.signInWithGoogle(
            GoogleAccount(email = "luna@gmail.com", displayName = "Luna", idToken = "token")
        ) { outcome = it }
        advanceUntilIdle()

        assertEquals(SignInOutcome.SWITCHED_ACCOUNT, outcome)
        assertEquals("luna@gmail.com", viewModel.state.value.email)
        assertEquals("Luna", viewModel.state.value.name)
        // The previous account's friends and chats must not follow the new one in.
        assertTrue(repository.friends.value.isEmpty())
        assertTrue(repository.chats.value.isEmpty())
    }

    @Test
    fun signingOutKeepsTheAddressButMarksTheProfileSignedOut() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store, dispatcher)

        viewModel.signInWithGoogle(
            GoogleAccount(email = "nova@gmail.com", displayName = "Nova", idToken = "token")
        )
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.signedIn)
        assertEquals("nova@gmail.com", viewModel.state.value.email)

        var outcome = SignInOutcome.NEW_ACCOUNT
        viewModel.signInWithGoogle(
            GoogleAccount(email = "nova@gmail.com", displayName = "Nova", idToken = "token")
        ) { outcome = it }
        advanceUntilIdle()
        assertEquals(SignInOutcome.RETURNING, outcome)
    }

    @Test
    fun forgettingTheAccountRemovesTheGoogleLink() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store, dispatcher)

        viewModel.signInWithGoogle(
            GoogleAccount(email = "nova@gmail.com", displayName = "Nova", idToken = "token")
        )
        advanceUntilIdle()

        viewModel.forgetGoogleAccount()
        advanceUntilIdle()
        assertEquals("", viewModel.state.value.email)
        assertFalse(store.snapshot().signedIn)
    }

    @Test
    fun theTestingClientIdIsRemembered() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = InMemorySocialStore()
        val viewModel = SocialProfileViewModel(store, dispatcher)

        viewModel.setGoogleClientId("  123-abc.apps.googleusercontent.com ")
        advanceUntilIdle()

        assertEquals("123-abc.apps.googleusercontent.com", viewModel.state.value.googleClientId)
        assertEquals("123-abc.apps.googleusercontent.com", store.snapshot().googleClientId)
    }

    @Test
    fun aFailedSignInSurfacesTheReason() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = SocialProfileViewModel(InMemorySocialStore(), dispatcher)

        viewModel.onGoogleSignInFailed("No Google account is set up on this device.")
        assertEquals("No Google account is set up on this device.", viewModel.state.value.googleError)

        viewModel.clearGoogleError()
        assertNull(viewModel.state.value.googleError)
    }
}
