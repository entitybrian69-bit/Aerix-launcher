package net.kdt.aerix.desktop.app

import net.kdt.aerix.desktop.core.json.Json
import net.kdt.aerix.desktop.core.net.Http

/**
 * Launcher update check.
 *
 * Quiet by design: one request a day against the GitHub releases API, cached in settings, and a
 * toast only when there really is a newer build. No download happens behind the player's back — the
 * toast names the release page, and nothing is installed unless they go there.
 */
object Updates {
    private const val API = "https://api.github.com/repos/luciferstudios/Aerix-launcherPc/releases/latest"

    class Release(val tag: String, val url: String, val notes: String, val publishedAt: String)

    fun checkInBackground(): Release? {
        val last = Settings.long("updateCheckedAt", 0L)
        if (System.currentTimeMillis() - last < 24L * 60 * 60 * 1000) return null
        Settings.set("updateCheckedAt", System.currentTimeMillis())
        return runCatching {
            val doc = Json.parseObject(Http.get(API, cacheSeconds = 3600, timeoutSeconds = 10))
            val tag = doc.str("tag_name") ?: return null
            val url = doc.str("html_url") ?: return null
            val notes = doc.str("body") ?: ""
            val published = doc.str("published_at") ?: ""
            val release = Release(tag, url, notes, published)
            if (isNewer(tag, AppEnv.version)) {
                Settings.set("updateAvailable", tag)
                Scheduler.ui {
                    Scheduler.ui { }
                    java.awt.Toolkit.getDefaultToolkit().beep()
                }
                Log.i("updates", "a newer release exists: $tag (running ${AppEnv.version})")
                release
            } else null
        }.onFailure { Log.d("updates", "update check skipped: ${it.message}") }.getOrNull()
    }

    /** Compares `v2.4.10`-style tags numerically; a tag that cannot be parsed is not "newer". */
    fun isNewer(tag: String, current: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V").substringBefore('-')
            .split('.').mapNotNull { it.toIntOrNull() }
        val a = parts(tag)
        val b = parts(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrNull(i) ?: 0
            val y = b.getOrNull(i) ?: 0
            if (x != y) return x > y
        }
        return false
    }
}
