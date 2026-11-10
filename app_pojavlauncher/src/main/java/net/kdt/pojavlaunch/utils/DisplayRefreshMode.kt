package net.kdt.pojavlaunch.utils

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.Display
import android.view.WindowManager
import net.kdt.pojavlaunch.Logger
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.utils.RefreshModeChoice.Plan
import net.kdt.pojavlaunch.utils.RefreshModeChoice.ScreenMode

/**
 * Asks Android for the display mode the panel can actually run the game at.
 *
 * Two separate things keep a phone at 60 Hz, and a setting that does only one of them is the reason
 * "unlock refresh rate" switches elsewhere so often do nothing:
 *
 *  - The **display mode** itself. Many panels list several modes at the *same* resolution with
 *    different peak rates, and WindowManager only switches when the focused window states a
 *    preference: [WindowManager.LayoutParams.preferredDisplayModeId].
 *  - The **frame-rate vote**. Panels that expose one adaptive mode with a rate range never change the
 *    mode id at all; SurfaceFlinger picks the rate per frame from what the window says it needs, and
 *    [Window.setFrameRate] (API 31+) is the public way to raise that vote.
 *
 * So both are requested, and the caller is told what the display ended up at rather than what was
 * asked for - [activeRefreshHz] is the only number that proves anything. Which mode to pick is
 * arithmetic with no Android in it and lives in [RefreshModeChoice], where it can be tested.
 *
 * Deliberate limits: only a mode at the game's own resolution is considered; nothing is requested
 * when no faster mode exists; and every Android call is best-effort, because an OEM ROM ignoring the
 * request must not turn into a crash in the launcher.
 */
object DisplayRefreshMode {
    /** The preference key, shared with `pref_video.xml` and the launcher's settings bridge. */
    const val PREF_KEY = "requestMaxRefreshRate"

    /** The rate a mode tops out at. `getPossibleRefreshRates` is API 30; below that there is one number. */
    private fun peakHz(mode: Display.Mode): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mode.possibleRefreshRates?.maxOrNull()?.let { if (it > 0f) return it }
        }
        return mode.physicalRefreshRate
    }

    /** The display this context is on. `Context.getDisplay()` only exists on API 30+. */
    private fun displayOf(context: Context): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display else runCatching {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }.getOrNull()

    /**
     * The id WindowManager wants. Public as [Display.Mode.getModeId] from API 30; before that the
     * getter exists but is not on the SDK, so it is read reflectively, and a refusal means this
     * device cannot be asked by id - the frame-rate vote path is unaffected.
     */
    private fun modeIdOf(mode: Display.Mode): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) mode.modeId else runCatching {
            (mode.javaClass.getMethod("getId").invoke(mode) as? Int) ?: RefreshModeChoice.NO_MODE
        }.getOrDefault(RefreshModeChoice.NO_MODE)

    /** The mode the display is serving right now, or null when this Android version will not say. */
    private fun currentModeOf(display: Display): ScreenMode? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { display.mode }.getOrNull()?.let { describe(it) }
        } else {
            // No getMode() before API 30, so the geometry comes from the display itself and the rate
            // from the deprecated getter, which on these versions is the live rate.
            val size = runCatching { Point().also { @Suppress("DEPRECATION") display.getRealSize(it) } }.getOrNull()
                ?: return null
            @Suppress("DEPRECATION")
            val hz = runCatching { display.refreshRate }.getOrDefault(0f)
            ScreenMode(RefreshModeChoice.NO_MODE, size.x, size.y, peakHz = hz, liveHz = hz)
        }

    private fun describe(mode: Display.Mode): ScreenMode = ScreenMode(
        modeId = modeIdOf(mode),
        width = mode.physicalWidth,
        height = mode.physicalHeight,
        peakHz = peakHz(mode),
        // getPhysicalRefreshRate is the rate the panel is running at inside this mode, which on an
        // adaptive mode is the low end - the number the request has to be compared against.
        liveHz = mode.physicalRefreshRate
    )

    private fun modesOf(display: Display): List<ScreenMode> =
        runCatching { display.supportedModes?.map { describe(it) } }.getOrNull() ?: emptyList()

    /** The mode to request for this context, or null when there is nothing this device can be asked for. */
    @JvmStatic
    fun planFor(context: Context?): Plan? {
        val display = context?.let { displayOf(it) } ?: return null
        val current = currentModeOf(display) ?: return null
        val plan = RefreshModeChoice.choose(modesOf(display), current) ?: return null
        // Below S the only lever is the display-mode id, and there is nothing to switch to when the
        // fastest mode is already the one in use (adaptive panels) or when this Android version will
        // not hand out ids. Reporting "unavailable" beats reporting a request that cannot change
        // anything - and on S and later the frame-rate vote covers exactly those panels.
        val voteOnly = !plan.needsModeSwitch || plan.modeId == RefreshModeChoice.NO_MODE
        if (voteOnly && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Logger.appendToLog(
                "Display refresh: ${fmt(plan.requestedHz)}Hz is reachable at ${plan.width}x${plan.height} " +
                    "without a mode switch this Android version can express, so nothing was requested"
            )
            return null
        }
        return plan
    }

    /**
     * What the display is serving right now, at the rate it is really running at. Zero means "no
     * display to look at". This is the number that decides whether a request was honoured, so it must
     * never read the top of an adaptive mode's range - that reads 120 while the panel paints at 60.
     */
    @JvmStatic
    fun activeRefreshHz(context: Context?): Float =
        context?.let { displayOf(it) }?.let { currentModeOf(it)?.liveHz } ?: 0f

    /**
     * The fastest rate this screen can run at its current resolution, whether or not anything was
     * requested - which is what lets the settings row stay truthful on a 60 Hz panel.
     */
    @JvmStatic
    fun ceilingRefreshHz(context: Context?): Float {
        val display = context?.let { displayOf(it) } ?: return 0f
        return RefreshModeChoice.ceilingHz(modesOf(display), currentModeOf(display))
    }

    /** True when this screen has a faster mode at its own resolution to ask for. */
    @JvmStatic
    fun isAvailable(context: Context?): Boolean = planFor(context) != null

    /**
     * Applies the request to [activity]'s window. True means something was actually asked for, which
     * is not the same as the display having accepted it - see [activeRefreshHz].
     */
    @JvmStatic
    fun applyTo(activity: Activity?): Boolean {
        if (activity == null) return false
        if (!LauncherPreferences.PREF_MAX_REFRESH_RATE) {
            clear(activity)
            return false
        }
        val plan = planFor(activity) ?: run {
            clear(activity)
            return false
        }
        var touched = false
        runCatching {
            if (plan.needsModeSwitch && plan.modeId != RefreshModeChoice.NO_MODE) {
                val params = activity.window.attributes
                if (params.preferredDisplayModeId != plan.modeId) {
                    params.preferredDisplayModeId = plan.modeId
                    activity.window.attributes = params
                }
                touched = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // DEFAULT rather than FIXED_SOURCE: the game's cadence is its own, and a compatibility
                // claim this launcher cannot keep would be paid for in judder.
                activity.window.setFrameRate(
                    plan.requestedHz,
                    WindowManager.LayoutParams.FRAME_RATE_COMPATIBILITY_DEFAULT
                )
                touched = true
            }
        }.onFailure { error ->
            Logger.appendToLog(
                "Display refresh: request failed on ${activity.javaClass.simpleName}: " +
                    "${error.javaClass.simpleName} ${error.message}"
            )
            return false
        }
        if (!touched) return false
        Logger.appendToLog(
            "Display refresh: ${if (plan.needsModeSwitch) "requested display mode ${plan.modeId} at " else "voted "}" +
                "${fmt(plan.requestedHz)}Hz for ${plan.width}x${plan.height} (was ${fmt(plan.currentHz)}Hz, " +
                "${plan.fasterModes} faster mode(s) on this display); display now at " +
                "${fmt(activeRefreshHz(activity))}Hz"
        )
        return true
    }

    /** Hands the display back to the system, for the switch being turned off mid-session. */
    @JvmStatic
    fun clear(activity: Activity?) {
        if (activity == null) return
        runCatching {
            val params = activity.window.attributes
            if (params.preferredDisplayModeId != 0) {
                params.preferredDisplayModeId = 0
                activity.window.attributes = params
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity.window.setFrameRate(0f, WindowManager.LayoutParams.FRAME_RATE_COMPATIBILITY_DEFAULT)
            }
        }
    }

    /** One decimal: 119.98 should read as 120 in a log line, 59.94 should not read as 60. */
    private fun fmt(hz: Float): String = String.format(java.util.Locale.ROOT, "%.1f", hz)
}
