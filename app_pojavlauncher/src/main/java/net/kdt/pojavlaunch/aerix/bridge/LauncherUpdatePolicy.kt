package net.kdt.pojavlaunch.aerix.bridge

import com.google.gson.JsonParser
import java.net.URI

internal data class LauncherVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<LauncherVersion> {
    override fun compareTo(other: LauncherVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    companion object {
        fun parse(value: String): LauncherVersion? {
            val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-noruntime)?$").matchEntire(value) ?: return null
            val numbers = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            return LauncherVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}
internal data class LauncherRelease(val tag: String, val version: LauncherVersion, val notes: String, val asset: LauncherUpdateAsset)
internal data class LauncherUpdateAsset(val name: String, val url: String, val size: Long, val sha256: String?)

internal object LauncherUpdatePolicy {
    const val MAX_APK_BYTES = 512L * 1024 * 1024
    fun trustedAsset(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && uri.path == URI(null, null, uri.path, null).normalize().path &&
            uri.path.startsWith("/entitybrian69-bit/Aerix-launcher/releases/download/")
    }.getOrDefault(false)

    fun release(json: String, noRuntime: Boolean): LauncherRelease {
        val root = JsonParser().parse(json).asJsonObject
        require(!root.get("draft").asBoolean && !root.get("prerelease").asBoolean) { "This is not a stable release" }
        val tag = root.get("tag_name").asString
        require(Regex("^v?\\d+\\.\\d+\\.\\d+$").matches(tag)) { "Unsupported stable version tag" }
        val version = LauncherVersion.parse(tag) ?: error("Release has an unsupported version tag")
        val plain = tag.removePrefix("v")
        val preferred = if (noRuntime) "Aerix-Launcher-$plain-noruntime.apk" else "Aerix-Launcher-$plain.apk"
        val fallback = if (noRuntime) "Aerix-Launcher-noruntime.apk" else "Aerix-Launcher.apk"
        val assets = root.getAsJsonArray("assets").map { it.asJsonObject }
        val selected = assets.firstOrNull { it.get("name").asString == preferred }
            ?: assets.firstOrNull { it.get("name").asString == fallback }
            ?: error("This release has no APK for your launcher edition")
        val url = selected.get("browser_download_url").asString
        require(trustedAsset(url) && URI(url).path == "/entitybrian69-bit/Aerix-launcher/releases/download/$tag/${selected.get("name").asString}") { "Untrusted update download location" }
        val size = selected.get("size").asLong
        require(size in 1..MAX_APK_BYTES) { "Invalid update size" }
        val digest = selected.get("digest")?.takeUnless { it.isJsonNull }?.asString
        if (digest != null) require(Regex("sha256:[a-fA-F0-9]{64}").matches(digest)) { "Unsupported release checksum" }
        return LauncherRelease(tag, version, root.get("body")?.takeUnless { it.isJsonNull }?.asString.orEmpty().take(5000),
            LauncherUpdateAsset(selected.get("name").asString, url, size, digest?.substringAfter(':')?.lowercase()))
    }
}
