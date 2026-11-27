package net.kdt.aerix.desktop.core.auth

import net.kdt.aerix.desktop.app.Log
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secret storage for account credentials.
 *
 * On Windows this is DPAPI (`CryptProtectData`) through [Dpapi], which ties the ciphertext to the
 * signed-in Windows user: copying the accounts folder to another machine or another profile yields
 * nothing usable. When DPAPI is unavailable (a non-Windows dev box, or an exotic JVM), a local
 * AES-GCM key with owner-only file permissions is used instead, and a warning is written to the log
 * so the weaker mode is never silent.
 *
 * Access tokens are never written to disk at all — only refresh tokens and the profile id, exactly
 * like the Android launcher's `refreshToken` field.
 */
object SecureStore {
    private const val PREFIX_DPAPI = "dpapi:"
    private const val PREFIX_AES = "aes:"
    private val random = SecureRandom()

    fun isDpapiAvailable(): Boolean = Dpapi.available

    fun seal(plain: String): String {
        if (plain.isEmpty()) return ""
        if (Dpapi.available) {
            // Even after a successful probe, a later call can fail (a locked profile, a policy
            // change). Losing the ability to save an account over that is worse than using the
            // fallback, so a failure downgrades this write instead of propagating.
            runCatching {
                PREFIX_DPAPI + Base64.getEncoder()
                    .encodeToString(Dpapi.protect(plain.toByteArray(StandardCharsets.UTF_8)))
            }.onSuccess { return it }.onFailure {
                Log.w("securestore", "DPAPI seal failed (${it.message}); sealing with the AES-GCM fallback")
            }
        }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, fallbackKey(), GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return PREFIX_AES + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(cipherText)
    }

    fun open(sealed: String): String? {
        if (sealed.isEmpty()) return null
        return runCatching {
            when {
                sealed.startsWith(PREFIX_DPAPI) -> {
                    // A blob sealed by DPAPI can only be read by DPAPI; if this machine cannot do
                    // that, the token is unreadable and the caller must sign in again.
                    if (!Dpapi.available) return null
                    val data = Base64.getDecoder().decode(sealed.removePrefix(PREFIX_DPAPI))
                    String(Dpapi.unprotect(data), StandardCharsets.UTF_8)
                }
                sealed.startsWith(PREFIX_AES) -> {
                    val body = sealed.removePrefix(PREFIX_AES)
                    val iv = Base64.getDecoder().decode(body.substringBefore(':'))
                    val ct = Base64.getDecoder().decode(body.substringAfter(':'))
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, fallbackKey(), GCMParameterSpec(128, iv))
                    String(cipher.doFinal(ct), StandardCharsets.UTF_8)
                }
                // Plain values from an imported export are accepted but immediately re-sealed by the
                // caller; they are only ever read, never written back in clear.
                else -> sealed
            }
        }.onFailure { Log.w("secure", "could not open sealed value: ${it.message}") }.getOrNull()
    }

    private fun fallbackKey(): SecretKeySpec {
        if (!Dpapi.available && !warned) {
            warned = true
            Log.w("secure", "DPAPI is unavailable; account tokens use a local AES-GCM key instead")
        }
        val keyFile = keyPath()
        val key: ByteArray = if (Files.isRegularFile(keyFile)) {
            Base64.getDecoder().decode(Files.readString(keyFile).trim())
        } else {
            ByteArray(32).also { random.nextBytes(it) }.also { k ->
                Files.createDirectories(keyFile.parent)
                Files.writeString(keyFile, Base64.getEncoder().encodeToString(k))
                runCatching { keyFile.toFile().setReadable(false, false); keyFile.toFile().setReadable(true, true) }
                runCatching { keyFile.toFile().setWritable(false, false); keyFile.toFile().setWritable(true, true) }
            }
        }
        return SecretKeySpec(key, "AES")
    }

    @Volatile private var warned = false

    private fun keyPath(): Path = net.kdt.aerix.desktop.app.AppEnv.appData.resolve("secrets.key")
}

/**
 * Windows DPAPI access through `crypt32.dll`, using the JDK's Foreign Function & Memory API
 * (JDK 22+). On JDK 17-21 that API does not exist, so [available] reports false and
 * [SecureStore] uses its AES-GCM fallback instead; the launcher never requires DPAPI.
 */
object Dpapi {
    /**
     * True only if DPAPI has *demonstrably* worked, not merely if the API that could reach it is
     * present. The distinction matters: the JDK's foreign-function API exists from 22, but calling
     * into it still needs native access and can be refused, and `CryptProtectData` itself can fail.
     * An earlier version checked only for `Linker`, so on Windows it advertised DPAPI, then threw
     * from [protect] and took account saving down with it. Now it round-trips a small buffer once
     * and falls back to AES-GCM if anything at all goes wrong.
     */
    val available: Boolean = detect()

    private const val PROBE = "aerix-dpapi-probe"

    private fun detect(): Boolean {
        if (!net.kdt.aerix.desktop.app.AppEnv.isWindows) return false
        val apiPresent = runCatching {
            val linkMethod = Class.forName("java.lang.foreign.Linker").getMethod("nativeLinker")
            linkMethod.invoke(null) != null
        }.getOrDefault(false)
        if (!apiPresent) return false
        // A real seal/open pair is the only honest test, and it catches the layout and
        // native-access mistakes that a capability check cannot see.
        return runCatching {
            val sealed = FfmDpapi.protect(PROBE.toByteArray(StandardCharsets.UTF_8))
            val opened = FfmDpapi.unprotect(sealed)
            opened.contentEquals(PROBE.toByteArray(StandardCharsets.UTF_8))
        }.getOrElse {
            Log.w("securestore", "DPAPI is present but not usable (${it.message}); using the AES-GCM fallback")
            false
        }
    }

    fun protect(data: ByteArray): ByteArray = runCatching { FfmDpapi.protect(data) }
        .getOrElse { throw IllegalStateException("DPAPI unavailable: ${it.message}", it) }

    fun unprotect(data: ByteArray): ByteArray = runCatching { FfmDpapi.unprotect(data) }
        .getOrElse { throw IllegalStateException("DPAPI unavailable: ${it.message}", it) }

    /**
     * The actual `CryptProtectData`/`CryptUnprotectData` calls, isolated behind reflection so the
     * class loads on JDK 17-21 where `java.lang.foreign` does not exist yet.
     */
    private object FfmDpapi {
        private const val CRYPTPROTECT_UI_FORBIDDEN = 0x1

        fun protect(data: ByteArray): ByteArray = invoke("protect", data)
        fun unprotect(data: ByteArray): ByteArray = invoke("unprotect", data)

        private fun invoke(mode: String, data: ByteArray): ByteArray {
            val bridge = Class.forName("net.kdt.aerix.desktop.core.auth.DpapiBridge")
            val method = bridge.getMethod(mode, ByteArray::class.java)
            return method.invoke(null, data) as ByteArray
        }
    }
}

/** Helpers shared by the account screens. */
object SecretUtil {
    fun newState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun writeOwnerOnly(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
        runCatching {
            file.setReadable(false, false); file.setReadable(true, true)
            file.setWritable(false, false); file.setWritable(true, true)
            file.setExecutable(false, false)
        }
    }
}
