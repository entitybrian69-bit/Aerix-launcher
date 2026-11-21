package net.kdt.aerix.desktop.core.game

import net.kdt.aerix.desktop.core.json.JsonObject

/**
 * Models for Mojang's version manifest and the per-version JSON, matching `JVersionList.java`
 * plus the extra fields a desktop launcher needs (downloads.client, assetIndex, arguments).
 *
 * These are plain data holders: [MetadataRules] does the platform work.
 */
class VersionSummary(
    val id: String,
    val type: String,
    val url: String,
    val sha1: String?,
    val releaseTime: String,
    val local: Boolean = false
) {
    val isRelease: Boolean get() = type == "release"
    val isSnapshot: Boolean get() = type == "snapshot"
    val isOld: Boolean get() = type == "old_beta" || type == "old_alpha"
    val label: String get() = if (local) "$id (installed)" else id
}

class VersionManifest(
    val latestRelease: String?,
    val latestSnapshot: String?,
    val versions: List<VersionSummary>
)

class DownloadInfo(
    val url: String?,
    val sha1: String?,
    val size: Long,
    val path: String? = null
)

class VersionInfo(val json: JsonObject) {
    val id: String = json.str("id") ?: ""
    val type: String = json.str("type") ?: "release"
    val mainClass: String = json.str("mainClass") ?: ""
    val minecraftArguments: String? = json.str("minecraftArguments")
    val inheritsFrom: String? = json.str("inheritsFrom")
    val assetIndexId: String? = json.str("assetIndex", "id") ?: json.str("assets")
    val assets: String = json.str("assets") ?: "legacy"
    val javaMajor: Int = json.num("javaVersion", "majorVersion")?.toInt()
        ?: json.num("javaVersion", "version")?.toInt()
        ?: 8
    val releaseTime: String = json.str("releaseTime") ?: ""
    val client: DownloadInfo? = json.obj("downloads", "client")?.let {
        DownloadInfo(it.str("url"), it.str("sha1"), it.num("size") ?: 0, "client")
    }
    val assetIndex: DownloadInfo? = json.obj("assetIndex")?.let {
        DownloadInfo(it.str("url"), it.str("sha1"), it.num("size") ?: 0, json.str("assetIndex", "id"))
    }
    val loggingFile: DownloadInfo? = json.obj("logging", "client", "file")?.let {
        DownloadInfo(it.str("url"), it.str("sha1"), it.num("size") ?: 0, it.str("id"))
    }
    val loggingArgument: String? = json.str("logging", "client", "argument")
    val libraries: List<JsonObject> = json.arr("libraries")?.objects() ?: emptyList()
    val gameArgs: List<net.kdt.aerix.desktop.core.json.JsonValue> = json.arr("arguments", "game")?.items ?: emptyList()
    val jvmArgs: List<net.kdt.aerix.desktop.core.json.JsonValue> = json.arr("arguments", "jvm")?.items ?: emptyList()
    val jarVersion: String? = json.str("jar")

    override fun toString(): String = "VersionInfo($id from ${inheritsFrom ?: "vanilla"})"
}

/**
 * Rule evaluation for `libraries[].rules`, evaluated for **this** machine.
 *
 * `MoJsonRule` on Android hard-codes `os.name == "linux"` and the Android architecture; this
 * implementation reads the real Windows values, honours the rule precedence the spec describes
 * (an OS-version rule beats an OS-name rule), and treats a missing `arch` as "any".
 */
object MetadataRules {
    val osName: String = when {
        System.getProperty("os.name").lowercase().contains("win") -> "windows"
        System.getProperty("os.name").lowercase().contains("mac") -> "osx"
        else -> "linux"
    }
    val osVersion: String = System.getProperty("os.version") ?: ""
    val osArch: String = when (val a = System.getProperty("os.arch").lowercase()) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        "x86", "i386", "i686" -> "x86"
        else -> a
    }

    /**
     * Default allow: Minecraft treats a library with no rules as allowed. Rules with an OS that
     * does not match are skipped; the last matching rule with the highest precedence wins.
     */
    fun allowed(rules: net.kdt.aerix.desktop.core.json.JsonArray?): Boolean {
        if (rules == null || rules.size == 0) return true
        var decision = "disallow"
        var precedence = 0
        for (ruleValue in rules) {
            val rule = ruleValue as? JsonObject ?: continue
            val os = rule.obj("os")
            val level = if (os == null) 1 else 2 + (if (os.has("version")) 2 else 0) + (if (os.has("arch")) 3 else 0)
            if (level <= precedence) continue
            if (!matches(rule, os)) continue
            decision = rule.str("action") ?: "allow"
            precedence = level
        }
        return decision == "allow"
    }

    private fun matches(rule: JsonObject, os: JsonObject?): Boolean {
        // feature rules (demo, custom_resolution, has_quick_plays_support…) never apply to a normal launch
        val features = rule.obj("features")
        if (features != null) {
            for ((key, value) in features.fields) {
                val expected = value.asBoolean() ?: false
                val actual = when (key) {
                    "is_demo_user" -> false
                    "has_custom_resolution" -> false
                    "has_quick_plays_support" -> false
                    "is_quick_play_singleplayer" -> false
                    "is_quick_play_multiplayer" -> false
                    "is_quick_play_realms" -> false
                    else -> false
                }
                if (actual != expected) return false
            }
        }
        if (os == null) return true
        val name = os.str("name")
        if (name != null && !name.equals(osName, true)) return false
        val arch = os.str("arch")
        if (arch != null && !arch.equals(osArch, true)) return false
        val version = os.str("version")
        if (version != null) {
            return runCatching { Regex(version).containsMatchIn(osVersion) }.getOrDefault(false)
        }
        return true
    }

    /** Native classifier for this OS/arch, e.g. `natives-windows-x86_64`. */
    fun nativeClassifier(): String = when (osArch) {
        "x86_64" -> "natives-windows-x86_64"
        "arm64" -> "natives-windows-arm64"
        "x86" -> "natives-windows-32"
        else -> "natives-windows"
    }

    fun nativeKeys(): List<String> = listOf(
        "natives-$osName", "natives-$osName-$osArch", "natives-$osName-${osArch.replace("x86_64", "64")}",
        "natives-$osName-${osArch.replace("x86_64", "x86_64")}"
    ).distinct()
}

/** Maven coordinate parsing, mirroring `utils/maven/MavenName`. */
class MavenName(val group: String, val artifact: String, val version: String, val classifier: String? = null, val extension: String = "jar") {
    fun toPath(): String = buildString {
        append(group.replace('.', '/')).append('/')
        append(artifact).append('/')
        append(version).append('/')
        append(artifact).append('-').append(version)
        if (!classifier.isNullOrBlank()) append('-').append(classifier)
        append('.').append(extension)
    }

    fun toNotation(): String = buildString {
        append(group).append(':').append(artifact).append(':').append(version)
        if (!classifier.isNullOrBlank()) append(':').append(classifier)
    }

    companion object {
        fun parse(raw: String): MavenName? {
            val parts = raw.split(':')
            if (parts.size < 3) return null
            return MavenName(
                group = parts[0],
                artifact = parts[1],
                version = parts[2],
                classifier = parts.getOrNull(3),
                extension = parts.getOrNull(4) ?: "jar"
            )
        }
    }
}

/** A resolved library entry ready to download and put on the class path. */
class ResolvedLibrary(
    val name: MavenName,
    val url: String,
    val sha1: String?,
    val size: Long,
    val relativePath: String,
    /** Non-null when this artifact is a native jar that must be extracted rather than class-loaded. */
    val nativeExtract: Boolean = false,
    val excludeFromNatives: List<String> = emptyList()
)

/** The result of walking a version's inheritance chain. */
class MergedVersion(
    val version: VersionInfo,
    val libraries: List<ResolvedLibrary>,
    val natives: List<ResolvedLibrary>,
    val clientJar: DownloadInfo?,
    val assetIndex: DownloadInfo?,
    val assetIndexId: String,
    val assetsRoot: String,
    val mainClass: String,
    val jvmArgs: List<net.kdt.aerix.desktop.core.json.JsonValue>,
    val gameArgs: List<net.kdt.aerix.desktop.core.json.JsonValue>,
    val legacyArguments: String?,
    val loggingFile: DownloadInfo?,
    val loggingArgument: String?,
    val javaMajor: Int,
    val jarVersion: String,
    /** `true` when the version needs the pre-1.13 virtual asset layout. */
    val legacyAssets: Boolean
)
