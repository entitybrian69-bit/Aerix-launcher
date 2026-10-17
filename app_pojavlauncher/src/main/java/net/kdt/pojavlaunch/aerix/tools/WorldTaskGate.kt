package net.kdt.pojavlaunch.aerix.tools

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/** Serializes disk writes with the launch hand-off in the launcher process. */
internal object WorldTaskGate {
    private val writing = AtomicBoolean()
    private fun preferences(context: Context) = context.getSharedPreferences("aerix-world-tasks", 0)
    fun beginWrite(context: Context) {
        check(writing.compareAndSet(false, true)) { "Another world task is already running" }
        try {
            val pending = preferences(context).getLong("launch-pending", 0) - System.currentTimeMillis()
            check(!AerixToolsBridge.gameRunning(context) && pending !in 1..60_000) { "Close Minecraft and finish any pending launch first" }
        } catch (e: Exception) { writing.set(false); throw e }
    }
    fun endWrite() { writing.set(false) }
    @Synchronized fun beginLaunch(context: Context) {
        check(writing.compareAndSet(false, true)) { "Wait for the world task to finish before launching" }
        try {
            check(!AerixToolsBridge.gameRunning(context)) { "Minecraft is already running" }
            check(preferences(context).getLong("launch-pending", 0) - System.currentTimeMillis() !in 1..60_000) { "A launch is already pending" }
            check(preferences(context).edit().putLong("launch-pending", System.currentTimeMillis() + 60_000).commit()) { "Unable to reserve launch" }
        } finally { writing.set(false) }
    }
    fun cancelLaunch(context: Context) { preferences(context).edit().remove("launch-pending").commit() }
}
