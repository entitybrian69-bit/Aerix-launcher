package net.kdt.pojavlaunch.aerix.bridge

enum class MinecraftVersionKind(val label: String) {
    ALL("All types"), RELEASE("Release"), SNAPSHOT("Snapshot"),
    PRE_RELEASE("Pre-release"), RELEASE_CANDIDATE("Release candidate"),
    OLD_BETA("Old beta"), OLD_ALPHA("Old alpha"), OTHER("Other")
}

data class AssetGameVersion(val id: String, val kind: MinecraftVersionKind)

/** Provider/manifest metadata takes priority; name classification covers project-only versions. */
object MinecraftVersionFilters {
    fun classify(id: String, declared: String? = null): MinecraftVersionKind {
        val name = id.lowercase()
        if (Regex(".*(?:-pre(?:-?release)?-?|pre-release[- ]?)\\d+$").matches(name)) return MinecraftVersionKind.PRE_RELEASE
        if (Regex(".*-rc-?\\d+$").matches(name)) return MinecraftVersionKind.RELEASE_CANDIDATE
        return when (declared?.lowercase()) {
            "release" -> MinecraftVersionKind.RELEASE
            "snapshot" -> MinecraftVersionKind.SNAPSHOT
            "old_beta", "beta" -> MinecraftVersionKind.OLD_BETA
            "old_alpha", "alpha" -> MinecraftVersionKind.OLD_ALPHA
            else -> when {
                Regex("\\d+\\.\\d+(?:\\.\\d+)?").matches(name) -> MinecraftVersionKind.RELEASE
                Regex("\\d{2}w\\d{2}[a-z].*").matches(name) || "snapshot" in name -> MinecraftVersionKind.SNAPSHOT
                Regex("b\\d.*").matches(name) -> MinecraftVersionKind.OLD_BETA
                Regex("a\\d.*").matches(name) -> MinecraftVersionKind.OLD_ALPHA
                else -> MinecraftVersionKind.OTHER
            }
        }
    }
    fun matches(filter: MinecraftVersionKind, kind: MinecraftVersionKind): Boolean =
        filter == MinecraftVersionKind.ALL || filter == kind ||
            filter == MinecraftVersionKind.SNAPSHOT && kind in setOf(MinecraftVersionKind.PRE_RELEASE, MinecraftVersionKind.RELEASE_CANDIDATE)
}
