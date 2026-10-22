/*
 * Aerix Launcher Social layer.
 *
 * Real Google sign-in through Android's Credential Manager. The system sheet lists the Google
 * accounts on the phone and returns an ID token; nothing is typed into the launcher and no
 * password ever reaches the app.
 *
 * The client id is a Google Cloud "Web application" OAuth client, injected at build time as the
 * `aerixGoogleWebClientId` Gradle property (see gradle.properties). Without it the button says
 * so plainly instead of pretending to sign in.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R

/** The account Google just confirmed. [idToken] is the proof a server would verify. */
data class GoogleAccount(
    val email: String,
    val displayName: String,
    val idToken: String
)

sealed class GoogleAuthResult {
    data class Success(val account: GoogleAccount) : GoogleAuthResult()
    data object Cancelled : GoogleAuthResult()
    data object NotConfigured : GoogleAuthResult()
    data class Failed(val message: String) : GoogleAuthResult()
}

object GoogleAuth {

    private const val PLACEHOLDER = "PASTE_YOUR_WEB_CLIENT_ID"

    /** The OAuth client id baked in at build time, or "" when the build has none. */
    fun clientId(context: Context): String =
        runCatching { context.getString(R.string.aerix_google_web_client_id) }.getOrDefault("").trim()

    /**
     * Build value first, then the testing override typed into Settings on a debug build, so a
     * test APK can sign in without a rebuild.
     */
    fun resolveClientId(context: Context, override: String = ""): String {
        val fromBuild = clientId(context)
        if (isUsable(fromBuild)) return fromBuild
        return override.trim()
    }

    fun isConfigured(context: Context): Boolean = isUsable(clientId(context))

    fun isUsable(id: String): Boolean =
        id.isNotBlank() && id != PLACEHOLDER && id.endsWith(".apps.googleusercontent.com")

    /**
     * Opens the system account sheet. Suspends until the player picks an account, cancels, or
     * the device has no Google account at all.
     */
    suspend fun signIn(context: Context, clientIdOverride: String = ""): GoogleAuthResult {
        val clientId = resolveClientId(context, clientIdOverride)
        if (!isUsable(clientId)) return GoogleAuthResult.NotConfigured
        val activity = context.findActivity()
            ?: return GoogleAuthResult.Failed("Google sign-in needs the launcher window.")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(clientId).build()
            )
            .build()
        return withContext(Dispatchers.Main) {
            try {
                val response = CredentialManager.create(activity).getCredential(activity, request)
                val credential = response.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val google = GoogleIdTokenCredential.createFrom(credential.data)
                    val claims = GoogleIdToken.parseClaims(google.idToken)
                    val email = google.id.ifBlank { claims?.email.orEmpty() }
                    val display = google.displayName?.takeIf { it.isNotBlank() }
                        ?: claims?.name?.takeIf { it.isNotBlank() }
                        ?: email.substringBefore('@')
                    GoogleAuthResult.Success(
                        GoogleAccount(email = email, displayName = display, idToken = google.idToken)
                    )
                } else {
                    GoogleAuthResult.Failed("Google returned a credential this launcher cannot read.")
                }
            } catch (cancelled: GetCredentialCancellationException) {
                GoogleAuthResult.Cancelled
            } catch (none: NoCredentialException) {
                GoogleAuthResult.Failed("No Google account is set up on this device.")
            } catch (error: GetCredentialException) {
                GoogleAuthResult.Failed(error.errorMessage?.toString() ?: "Google sign-in failed.")
            } catch (error: Throwable) {
                GoogleAuthResult.Failed(error.message ?: "Google sign-in failed.")
            }
        }
    }

    /**
     * Real sign-out: clears the credential state Google shares with this app, so the next
     * sign-in asks again instead of silently reusing the last account.
     */
    suspend fun signOut(context: Context) {
        val activity = context.findActivity() ?: return
        withContext(Dispatchers.Main) {
            runCatching {
                CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
