/*
 * Aerix Launcher - the game's own frame rate, from the client mod.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package net.kdt.pojavlaunch.aerix.client

import android.os.SystemClock

/**
 * The FPS the game itself reported, or null when nothing reported it.
 *
 * [net.kdt.pojavlaunch.game.NativeFrameCounter] counts frames at the GLFW swap entry point, which is
 * the right source for that renderer. Renderers that present through their own path - a Vulkan
 * swapchain, a mod-replaced window - never touch it, and the launcher had no second source, so the
 * on-screen readout sat on its placeholder for the whole session. The bundled client mod writes the
 * game's own counter into the local telemetry file; reading that gives the pill a value there too.
 *
 * The instance directory comes from [ClientLiveOptionsBridge.activeGameDir], which is the single
 * place the launcher records "this is the running game", so the probe cannot read a stale file.
 */
object ClientFpsProbe {
    private const val CACHE_MS = 350L

    @Volatile private var cachedFps: Int? = null
    @Volatile private var cachedAt: Long = -1L

    /** @return frames per second as the game counted them, or null when no mod is publishing. */
    @JvmStatic
    fun read(): Int? {
        // The bridge already tracks which instance owns the running game; a second copy of that
        // fact would drift the first time a launch failed halfway.
        val directory = ClientLiveOptionsBridge.activeGameDir ?: return null
        val now = SystemClock.elapsedRealtime()
        if (cachedAt >= 0 && now - cachedAt < CACHE_MS) return cachedFps
        cachedFps = runCatching { ClientTelemetryBridge.read(directory)?.fps }.getOrNull()
        cachedAt = now
        return cachedFps
    }

    /** Forget the memoised value, e.g. when a different instance is launched. */
    @JvmStatic
    fun invalidate() {
        cachedAt = -1L
        cachedFps = null
    }
}
