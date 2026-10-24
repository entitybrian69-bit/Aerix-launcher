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
}
