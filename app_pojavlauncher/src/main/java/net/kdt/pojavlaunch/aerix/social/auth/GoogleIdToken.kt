/*
 * Aerix Launcher Social layer.
 *
 * Reads the claims out of a Google ID token (the JWT that Google hands back after a real
 * sign-in). The token is only a transport here: the email/name are used to fill the local
 * profile, and a hosted backend would verify the signature before trusting them.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.auth

import org.json.JSONObject

/** What a Google ID token says about the person who just signed in. */
data class GoogleClaims(
    val email: String,
    val name: String,
    val subject: String
)

object GoogleIdToken {

    /**
     * Decodes the middle segment of the token. Deliberately dependency free so it can be tested
     * as plain Kotlin; it does NOT verify the signature — that belongs on a server.
     */
    fun parseClaims(jwt: String): GoogleClaims? {
        val parts = jwt.split('.')
        if (parts.size < 2) return null
        val payload = runCatching { String(base64UrlDecode(parts[1]), Charsets.UTF_8) }.getOrNull() ?: return null
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        return GoogleClaims(
            email = json.optString("email"),
            name = json.optString("name"),
            subject = json.optString("sub")
        )
    }

    /** base64url without padding, decoded by hand because `java.util.Base64` needs API 26. */
    fun base64UrlDecode(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val cleaned = input.trim().trimEnd('=')
        val output = ByteArray(cleaned.length * 3 / 4)
        var buffer = 0
        var bits = 0
        var index = 0
        for (character in cleaned) {
            val value = alphabet.indexOf(character)
            if (value < 0) throw IllegalArgumentException("Not base64url: $character")
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output[index++] = ((buffer shr bits) and 0xFF).toByte()
            }
        }
        return output.copyOf(index)
    }
}
