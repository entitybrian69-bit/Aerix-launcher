package net.kdt.pojavlaunch.utils

/**
 * The decision behind a max-refresh-rate switch, with no Android in it.
 *
 * Keeping this pure is what makes it testable: which mode to ask for is arithmetic over a list of
 * modes, and the cases that matter - one adaptive mode with a rate range, a faster mode only at a
 * resolution the game is not running at, a panel already at its ceiling - can all be asserted here
 * instead of needing a hand attached to a screen. [DisplayRefreshMode] is the thin adapter that
 * reads a real [android.view.Display] and writes the answer into a window.
 */
object RefreshModeChoice {
    /**
     * One mode of one screen, reduced to the numbers the decision uses.
     *
     * [peakHz] is the top of the mode's range, not the rate it happens to be idling at, and that
     * difference is the whole ballgame on adaptive panels: `Display.Mode.getPhysicalRefreshRate()`
     * there reports the bottom (60), so a chooser that read only that would conclude a 120 Hz screen
     * cannot do 120. [liveHz] is the rate actually being served, which is what has to be compared
     * against, and what the read-back reports.
     */
    data class ScreenMode(
        val modeId: Int,
        val width: Int,
        val height: Int,
        val peakHz: Float,
        val liveHz: Float = peakHz
    )

    /**
     * What to ask the window manager for. [currentHz] and [fasterModes] ride along so the caller can
     * report "60 to 120 Hz, 2 candidates" instead of a bare number, and [currentModeId] is kept
     * because not every request is a mode switch.
     */
    data class Plan(
        val modeId: Int,
        val currentModeId: Int,
        val width: Int,
        val height: Int,
        val requestedHz: Float,
        val currentHz: Float,
        val fasterModes: Int
    ) {
        /**
         * False on the adaptive panels where the mode id never changes and only the frame-rate vote
         * moves the needle. Writing [WindowManager.LayoutParams.preferredDisplayModeId] there would
         * be a no-op at best - mode id 0 means "no preference" - so the adapter leaves it alone.
         */
        val needsModeSwitch: Boolean get() = modeId != currentModeId
    }

    /** Anything within this of the live rate is measurement noise, not an upgrade worth the battery. */
    private const val NOISE_FLOOR_HZ = 0.1f

    /** No mode id on this Android version, so the request has to be made by rate vote alone. */
    const val NO_MODE = -1

    /** Modes at the same pixel geometry as [current], whatever their rate. */
    private fun candidates(modes: List<ScreenMode>, current: ScreenMode?): List<ScreenMode> {
        if (current == null) return emptyList()
        // A mode at another resolution can resize the surface under a live GL context, and a black game
        // is not a performance setting - so it is never considered, however fast it is.
        return modes.filter { it.width == current.width && it.height == current.height }
    }

    /**
     * The mode worth requesting, or null when asking would change nothing. Ties on rate go to the
     * lower id, which is the panel's native entry on the devices that list duplicates.
     */
    @JvmStatic
    fun choose(modes: List<ScreenMode>, current: ScreenMode?): Plan? {
        val from = current ?: return null
        val faster = candidates(modes, from).filter { it.peakHz > from.liveHz + NOISE_FLOOR_HZ }
        val best = faster.sortedWith(compareByDescending<ScreenMode> { it.peakHz }.thenBy { it.modeId })
            .firstOrNull() ?: return null
        return Plan(
            modeId = best.modeId,
            currentModeId = from.modeId,
            width = best.width,
            height = best.height,
            requestedHz = best.peakHz,
            currentHz = from.liveHz,
            fasterModes = faster.size
        )
    }

    /**
     * The fastest rate reachable at the current resolution, whether or not anything is left to ask
     * for. The settings row reads this so it can say something true on a 60 Hz panel instead of going
     * blank exactly when the player most wants to know.
     */
    @JvmStatic
    fun ceilingHz(modes: List<ScreenMode>, current: ScreenMode?): Float =
        candidates(modes, current).maxOfOrNull { it.peakHz } ?: 0f

    /** True when [choose] would return a plan, i.e. the screen has something to gain. */
    @JvmStatic
    fun canGoFaster(modes: List<ScreenMode>, current: ScreenMode?): Boolean = choose(modes, current) != null
}
