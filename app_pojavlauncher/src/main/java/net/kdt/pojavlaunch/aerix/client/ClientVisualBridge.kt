package net.kdt.pojavlaunch.aerix.client

import java.io.File

/** Local IPC for optional client-only sky/weather presentation. */
object ClientVisualBridge {
    fun sync(gameDir: File) {
        val file = File(gameDir, "config/aerix_visual.properties")
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "aerix_visual.properties.tmp")
        val nextStamp = maxOf(System.currentTimeMillis(), file.lastModified() + 1L)
        try {
            temp.writeText("time=${AerixClientStore.visualTimeMode}\nweather=${AerixClientStore.visualWeatherMode}\n")
            check(temp.renameTo(file)) { "Could not save visual settings" }
            file.setLastModified(nextStamp)
        } finally { temp.delete() }
    }
}
