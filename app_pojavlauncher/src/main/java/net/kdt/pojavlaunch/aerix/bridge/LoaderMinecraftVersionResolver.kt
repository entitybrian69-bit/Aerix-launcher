package net.kdt.pojavlaunch.aerix.bridge

/** Resolve metadata inheritance before considering known profile-name formats. No latest fallback. */
internal object LoaderMinecraftVersionResolver {
    fun resolve(versionId: String, available: Set<String>, parent: (String) -> String?): String? {
        var current = versionId
        val visited = HashSet<String>()
        repeat(16) {
            if (current in available) return current
            if (!visited.add(current)) return@repeat
            val inherited = parent(current)
            if (inherited.isNullOrBlank()) return@repeat
            current = inherited
        }
        return available.sortedByDescending { it.length }.firstOrNull {
            versionId.endsWith("-$it") || versionId.startsWith("$it-")
        }
    }
}
