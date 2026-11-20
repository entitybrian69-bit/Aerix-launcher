package net.kdt.pojavlaunch.aerix.bridge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class LauncherUpdatePolicyTest {
    private fun metadata(vararg names: String, tag: String = "v1.1.4", prerelease: Boolean = false, url: String? = null, digest: String? = null): String {
        val root = JsonObject().apply {
            addProperty("draft", false); addProperty("prerelease", prerelease); addProperty("tag_name", tag)
            addProperty("body", "A new launcher update")
            add("assets", JsonArray().apply {
                names.forEach { name -> add(JsonObject().apply {
                    addProperty("name", name); addProperty("size", 1024)
                    addProperty("browser_download_url", url ?: "https://github.com/entitybrian69-bit/Aerix-launcher/releases/download/$tag/$name")
                    if (digest != null) addProperty("digest", digest)
                }) }
            })
        }
        return root.toString()
    }
    @Test fun numericVersionsAreNotComparedAsText() {
        assertTrue(LauncherVersion.parse("1.10.0")!! > LauncherVersion.parse("1.9.9")!!)
        assertTrue(LauncherVersion.parse("1.1.3")!! > LauncherVersion.parse("1.1.2")!!)
        assertEquals(LauncherVersion.parse("1.1.3"), LauncherVersion.parse("v1.1.3-noruntime"))
    }
    @Test fun malformedVersionsAndPrereleasesAreNotStableUpdates() {
        assertNull(LauncherVersion.parse("1.6-beta")); assertNull(LauncherVersion.parse("latest"))
        assertNull(LauncherVersion.parse("1.1"))
    }
    @Test fun fullEditionGetsFullApkNotSmallerNoRuntimeApk() {
        val release = LauncherUpdatePolicy.release(metadata("Aerix-Launcher-1.1.4-noruntime.apk", "Aerix-Launcher-1.1.4.apk"), false)
        assertEquals("Aerix-Launcher-1.1.4.apk", release.asset.name)
    }
    @Test fun noRuntimeEditionStaysNoRuntime() {
        val release = LauncherUpdatePolicy.release(metadata("Aerix-Launcher-1.1.4.apk", "Aerix-Launcher-1.1.4-noruntime.apk"), true)
        assertEquals("Aerix-Launcher-1.1.4-noruntime.apk", release.asset.name)
    }
    @Test fun permanentAssetNameIsSupported() {
        assertEquals("Aerix-Launcher.apk", LauncherUpdatePolicy.release(metadata("Aerix-Launcher.apk"), false).asset.name)
    }
    @Test(expected = IllegalStateException::class) fun missingEditionDoesNotDownloadWrongApk() {
        LauncherUpdatePolicy.release(metadata("Aerix-Launcher-noruntime.apk"), false)
    }
    @Test(expected = IllegalArgumentException::class) fun prereleaseIsRejected() {
        LauncherUpdatePolicy.release(metadata("Aerix-Launcher.apk", prerelease = true), false)
    }
    @Test fun onlyOfficialHttpsReleaseAssetsAreTrusted() {
        assertTrue(LauncherUpdatePolicy.trustedAsset("https://github.com/entitybrian69-bit/Aerix-launcher/releases/download/v1.1.4/Aerix-Launcher.apk"))
        listOf("http://github.com/entitybrian69-bit/Aerix-launcher/releases/download/v1.1.4/app.apk",
            "https://github.com.evil.example/entitybrian69-bit/Aerix-launcher/releases/download/v1.1.4/app.apk",
            "https://github.com/other/repo/releases/download/v1.1.4/app.apk",
            "https://github.com/entitybrian69-bit/Aerix-launcher/releases/download/../../other/app.apk",
            "https://github.com/entitybrian69-bit/Aerix-launcher/releases/download/%2e%2e/other/app.apk").forEach {
            assertFalse(it, LauncherUpdatePolicy.trustedAsset(it))
        }
    }
    @Test(expected = IllegalArgumentException::class) fun wrongReleaseUrlIsRejected() {
        LauncherUpdatePolicy.release(metadata("Aerix-Launcher.apk", url = "https://github.com/entitybrian69-bit/Aerix-launcher/releases/download/v1.0.0/Aerix-Launcher.apk"), false)
    }
    @Test fun optionalSha256DigestIsPreserved() {
        val hash = "ab".repeat(32)
        assertEquals(hash, LauncherUpdatePolicy.release(metadata("Aerix-Launcher.apk", digest = "sha256:$hash"), false).asset.sha256)
    }
    @Test(expected = IllegalArgumentException::class) fun malformedChecksumIsRejected() {
        LauncherUpdatePolicy.release(metadata("Aerix-Launcher.apk", digest = "sha256:bad"), false)
    }

    @Test fun theArchitectureDecidesWhichApkIsOffered() {
        val arm64 = metadata(
            "Aerix-Launcher-1.1.4-arm64-v8a.apk",
            "Aerix-Launcher-1.1.4-armeabi-v7a.apk",
            "Aerix-Launcher-1.1.4.apk"
        )
        assertEquals("Aerix-Launcher-1.1.4-arm64-v8a.apk",
            LauncherUpdatePolicy.release(arm64, false, "arm64-v8a").asset.name)
        assertEquals("Aerix-Launcher-1.1.4-armeabi-v7a.apk",
            LauncherUpdatePolicy.release(arm64, false, "armeabi-v7a").asset.name)
        // No split for this device: the universal build is used rather than failing.
        assertEquals("Aerix-Launcher-1.1.4.apk",
            LauncherUpdatePolicy.release(arm64, false, null).asset.name)
    }
    @Test fun aReleaseWithoutSplitsStillUpdates() {
        val universalOnly = metadata("Aerix-Launcher-1.1.4.apk", "Aerix-Launcher-1.1.4-noruntime.apk")
        assertEquals("Aerix-Launcher-1.1.4.apk",
            LauncherUpdatePolicy.release(universalOnly, false, "arm64-v8a").asset.name)
        assertEquals("Aerix-Launcher-1.1.4-noruntime.apk",
            LauncherUpdatePolicy.release(universalOnly, true, "arm64-v8a").asset.name)
    }
    @Test fun theNoruntimeEditionPrefersItsOwnSplit() {
        val release = metadata(
            "Aerix-Launcher-1.1.4-arm64-v8a.apk",
            "Aerix-Launcher-1.1.4-noruntime-arm64-v8a.apk",
            "Aerix-Launcher-1.1.4-noruntime.apk"
        )
        assertEquals("Aerix-Launcher-1.1.4-noruntime-arm64-v8a.apk",
            LauncherUpdatePolicy.release(release, true, "arm64-v8a").asset.name)
        assertEquals("Aerix-Launcher-1.1.4-arm64-v8a.apk",
            LauncherUpdatePolicy.release(release, false, "arm64-v8a").asset.name)
    }
    @Test fun theDevicePickUses64BitWhenThereIsOne() {
        assertEquals("arm64-v8a", LauncherUpdatePolicy.preferredAbi(arrayOf("arm64-v8a", "armeabi-v7a"), arrayOf("armeabi-v7a")))
        assertEquals("armeabi-v7a", LauncherUpdatePolicy.preferredAbi(arrayOf(), arrayOf("armeabi-v7a")))
        // Neither ARM: x86 emulators and Chromebooks take the universal APK.
        assertNull(LauncherUpdatePolicy.preferredAbi(arrayOf("x86_64"), arrayOf("x86")))
    }
    @Test fun theAssetNameSaysWhichBuildItIs() {
        assertEquals("arm64", LauncherUpdateAsset("Aerix-Launcher-1.1.4-arm64-v8a.apk", "", 1, null).archLabel)
        assertEquals("arm32", LauncherUpdateAsset("Aerix-Launcher-1.1.4-armeabi-v7a.apk", "", 1, null).archLabel)
        assertNull(LauncherUpdateAsset("Aerix-Launcher-1.1.4.apk", "", 1, null).archLabel)
    }

    /** The noruntime flavor appends a suffix to versionName; the tag never has it. */
    @Test fun noruntimeArchivesAreAcceptedForTheirOwnTag() {
        assertTrue(LauncherUpdatePolicy.versionMatchesTag("2.4.2", "v2.4.2"))
        assertTrue("a noruntime archive reports 2.4.2-noruntime and is still 2.4.2",
            LauncherUpdatePolicy.versionMatchesTag("2.4.2-noruntime", "v2.4.2"))
        assertTrue(LauncherUpdatePolicy.versionMatchesTag("2.4.2-noruntime", "2.4.2"))
        assertFalse("a different version is still a different version",
            LauncherUpdatePolicy.versionMatchesTag("2.4.1-noruntime", "v2.4.2"))
        assertFalse("a missing version cannot be accepted",
            LauncherUpdatePolicy.versionMatchesTag(null, "v2.4.2"))
    }

    /**
     * A single-signed install accepts an update when any signer matches, because rotation history can
     * prove the old certificate. This is the rule for one signer only - see canInstallOver: an
     * install carrying two signers needs the identical set back, which is the wall 2.4.1 sits behind.
     */
    @Test fun anySharedSignerIsEnoughJustLikeAndroid() {
        val current = "current-key"
        val retired = "retired-key"
        assertTrue("the double-signed transition must install over an old install",
            LauncherUpdatePolicy.signaturesCompatible(setOf(retired), setOf(current, retired)))
        assertTrue("and over a new one",
            LauncherUpdatePolicy.signaturesCompatible(setOf(current), setOf(current, retired)))
        assertTrue(LauncherUpdatePolicy.signaturesCompatible(setOf(current), setOf(current)))
        assertFalse("an unrelated key is the impostor",
            LauncherUpdatePolicy.signaturesCompatible(setOf(current), setOf(retired)))
        assertFalse("no signers at all is never an update",
            LauncherUpdatePolicy.signaturesCompatible(emptySet(), setOf(current)))
        assertFalse(LauncherUpdatePolicy.signaturesCompatible(setOf(current), emptySet()))
    }

    /**
     * The wall that made "install 2.4.3 over 2.4.1" impossible, written down so it stays impossible.
     *
     * AOSP SigningDetails.checkCapability: "multiple-signer packages cannot rotate signing certs, so
     * we must have an exact match." The published 2.4.1 is signed with both the current and the
     * retired key, so it is a multiple-signer install, and a single-signed APK is refused by the
     * device with INSTALL_FAILED_UPDATE_INCOMPATIBLE no matter which certificate it shares.
     */
    @Test fun aMultiSignedInstallCannotMoveToASingleSignature() {
        val current = "current-key"
        val retired = "retired-key"
        assertFalse("2.4.1 is double-signed; a 2.4.3 signed with only the current key is refused",
            LauncherUpdatePolicy.canInstallOver(
                setOf(current, retired), setOf(current), sdkInt = 30, installedIsMultiSigned = true))
        assertFalse("even though one certificate is shared, which is the part that fooled us",
            LauncherUpdatePolicy.signaturesCompatible(setOf(current, retired), setOf(current)))
        assertTrue("the only APK that installs over it carries both certificates",
            LauncherUpdatePolicy.canInstallOver(
                setOf(current, retired), setOf(current, retired), sdkInt = 30, installedIsMultiSigned = true))
    }

    /** A single-signed install may still move, because rotation history can prove the old key. */
    @Test fun aSingleSignedInstallCanRotate() {
        val current = "current-key"
        val retired = "retired-key"
        assertTrue(LauncherUpdatePolicy.canInstallOver(
            setOf(retired), setOf(current, retired), sdkInt = 30, installedIsMultiSigned = false))
        assertTrue("an ordinary update is unaffected", LauncherUpdatePolicy.canInstallOver(
            setOf(current), setOf(current), sdkInt = 30, installedIsMultiSigned = false))
        assertFalse("and a stranger is still refused", LauncherUpdatePolicy.canInstallOver(
            setOf(current), setOf(retired), sdkInt = 30, installedIsMultiSigned = false))
    }

    /** Below API 28 there is no SigningDetails history, so only the sets themselves can be compared. */
    @Test fun oldAndroidComparesCertificateSetsDirectly() {
        val current = "current-key"
        val retired = "retired-key"
        assertFalse(LauncherUpdatePolicy.canInstallOver(
            setOf(current, retired), setOf(current), sdkInt = 26, installedIsMultiSigned = false))
        assertTrue(LauncherUpdatePolicy.canInstallOver(
            setOf(current), setOf(current), sdkInt = 26, installedIsMultiSigned = false))
    }
}
