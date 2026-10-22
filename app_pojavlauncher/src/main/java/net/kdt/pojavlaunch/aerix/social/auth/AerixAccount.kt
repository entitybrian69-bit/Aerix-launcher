/*
 * Aerix Launcher Social layer.
 *
 * Aerix accounts: the launcher's own sign-in, with no Google and no third party. Credentials are
 * never stored: a random salt plus a PBKDF2 hash are, so a stolen preferences file does not hand
 * over a password. Accounts can be marked deleted and restored later by signing in again.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * One Aerix account on this device.
 *
 * [deletedAt] is a soft delete: the row stays so the same person can sign back in and restore
 * everything, which is what "I deleted my account" has to mean if it is not to be a trap.
 */
data class AerixAccount(
    val id: String,
    val username: String,
    val email: String,
    val salt: String,
    val hash: String,
    val algorithm: String,
    val iterations: Int,
    val createdAt: Long,
    val deletedAt: Long = 0L
) {
    val isDeleted: Boolean get() = deletedAt > 0L
}

/** Validation and password hashing for Aerix accounts. */
object AerixCredentials {

    const val MIN_PASSWORD = 8
    const val SALT_BYTES = 16

    /** API 26 added PBKDF2 with SHA-256; older devices fall back to the SHA-1 variant. */
    private fun algorithmFor(apiLevel: Int): String =
        if (apiLevel >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"

    fun preferredAlgorithm(): String = algorithmFor(android.os.Build.VERSION.SDK_INT)

    fun preferredIterations(): Int = 120_000

    private val usernamePattern = Regex("^[A-Za-z0-9_]{3,16}$")

    /** Null when valid, otherwise a single-line reason. */
    fun usernameProblem(value: String): String? {
        val name = value.trim()
        return when {
            name.isEmpty() -> "Pick a username"
            !usernamePattern.matches(name) -> "3 to 16 letters, numbers or _"
            else -> null
        }
    }

    fun emailProblem(value: String): String? {
        val email = value.trim()
        if (email.isEmpty()) return "Add your email"
        val at = email.indexOf('@')
        val dot = email.lastIndexOf('.')
        val looksWrong = at <= 0 || dot <= at + 1 || dot >= email.length - 1 ||
            email.contains(' ') || email.length > 254
        return if (looksWrong) "That email doesn't look right" else null
    }

    fun passwordProblem(value: String): String? = when {
        value.isEmpty() -> "Choose a password"
        value.length < MIN_PASSWORD -> "At least $MIN_PASSWORD characters"
        value.all { it.isDigit() } -> "Add letters, not just numbers"
        else -> null
    }

    /** Usernames and emails are matched without case, the way people type them. */
    fun normalise(value: String): String = value.trim().lowercase()

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    fun hashPassword(password: String, salt: String, algorithm: String, iterations: Int): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            android.util.Base64.decode(salt, android.util.Base64.NO_WRAP),
            iterations,
            256
        )
        return try {
            val factory = SecretKeyFactory.getInstance(normaliseAlgorithm(algorithm))
            val key = factory.generateSecret(spec).encoded
            android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    /** Compare in constant time, so a wrong password cannot be narrowed down by timing. */
    fun verify(account: AerixAccount, password: String): Boolean {
        val attempt = runCatching {
            hashPassword(password, account.salt, account.algorithm, account.iterations)
        }.getOrNull() ?: return false
        return MessageDigest.isEqual(attempt.toByteArray(), account.hash.toByteArray())
    }

    /**
     * A device that moved between API levels, or a record written by a newer build, may name an
     * algorithm this runtime does not have. Fall back rather than lock the person out.
     */
    private fun normaliseAlgorithm(stored: String): String = try {
        SecretKeyFactory.getInstance(stored)
        stored
    } catch (unsupported: Throwable) {
        preferredAlgorithm()
    }

    fun create(
        username: String,
        email: String,
        password: String,
        now: Long = System.currentTimeMillis()
    ): AerixAccount {
        val salt = newSalt()
        val algorithm = preferredAlgorithm()
        val iterations = preferredIterations()
        return AerixAccount(
            id = java.util.UUID.randomUUID().toString(),
            username = username.trim(),
            email = email.trim(),
            salt = salt,
            hash = hashPassword(password, salt, algorithm, iterations),
            algorithm = algorithm,
            iterations = iterations,
            createdAt = now
        )
    }
}
