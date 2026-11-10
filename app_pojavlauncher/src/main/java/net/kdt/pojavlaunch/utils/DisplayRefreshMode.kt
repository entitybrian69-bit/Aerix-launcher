package net.kdt.pojavlaunch.utils

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.Display
import android.view.View
import android.view.Window
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
 *    mode id at all, so nothing is gained by naming a mode; the rate has to be requested as a number.
 *    Three levers do that, in order of how modern they are: [View.setRequestedFrameRate]'s HIGH
 *    category (API 31+), `Window.setFrameRate` with a compatibility hint (API 31+), and the older
 *    [WindowManager.LayoutParams.preferredRefreshRate], which is deprecated but is the one that works
 *    back to API 17 and on the ROMs that dropped the newer paths.
 *
 * All of them are attempted, because which one a given OEM firmware honors is not knowable in
 * advance, and the caller is then told what the display ended up at rather than what was asked for -
 * [activeRefreshHz] is the only number that proves anything. Which mode to pick is arithmetic with
 * no Android in it and lives in [RefreshModeChoice], where it can be tested.
 *
 * Deliberate limits: only a mode at the game's own resolution is considered; nothing is requested when
 * no faster mode exists; and every Android call is best-effort, because an OEM ROM ignoring the
 * request - or an SDK surface that will not even bind these members - must not turn into a crash.
 */
object DisplayRefreshMode {
    /** The preference key, shared with `pref_video.xml` and the launcher's settings bridge. */
    const val PREF_KEY = "requestMaxRefreshRate"

    /** `Surface.FRAME_RATE_COMPATIBILITY_DEFAULT`, which is what an ordinary game wants. */
    private const val FRAME_RATE_COMPATIBILITY_DEFAULT = 0

    /** Rate accessors that this module's `android.jar` does not bind by name are read reflectively. */
    private fun floatMember(target: Any, name: String): Float = runCatching {
        (target.javaClass.getMethod(name).invoke(target) as? Float) ?: 0f
    }.getOrDefault(0f)

    private fun floatArrayMax(target: Any, name: String): Float = runCatching {
        (target.javaClass.getMethod(name).invoke(target) as? FloatArray)?.maxOrNull() ?: 0f
    }.getOrDefault(0f)

    /**
     * The rate a mode tops out at. `getPossibleRefreshRates` carries the range on an adaptive mode and
     * `getPhysicalRefreshRate` is exact where it exists, so the highest of those and of the mode's own
     * nominal rate is taken - each is a lower bound on the truth, and the nominal one is rounded
     * (119.98 reads as 120, which is the number that belongs in a log line).
     */
    private fun peakHz(mode: Display.Mode): Float {
        @Suppress("DEPRECATION")
        val nominal = runCatching { mode.refreshRate }.getOrDefault(0f)
        return maxOf(floatArrayMax(mode, "getPossibleRefreshRates"), floatMember(mode, "getPhysicalRefreshRate"), nominal)
    }

    /** The rate a mode is actually painting at, which on an adaptive mode is the bottom of its range. */
    private fun liveHz(mode: Display.Mode): Float {
        val exact = floatMember(mode, "getPhysicalRefreshRate")
        if (exact > 0f) return exact
        @Suppress("DEPRECATION")
        return runCatching { mode.refreshRate }.getOrDefault(0f)
    }

    /** The display this context is on. `Context.getDisplay()` only exists on API 30+. */
    private fun displayOf(context: Context): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display else runCatching {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }.getOrNull()

    /**
     * The id WindowManager wants. Public as `Display.Mode.getModeId` from API 30; before that the
     * getter exists but is not on the SDK, so it is read reflectively, and a refusal means this
     * device cannot be asked by mode - the rate levers are unaffected.
     */
    private fun modeIdOf(mode: Display.Mode): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { mode.modeId }.getOrDefault(RefreshModeChoice.NO_MODE)
        } else runCatching {
            (mode.javaClass.getMethod("getId").invoke(mode) as? Int) ?: RefreshModeChoice.NO_MODE
        }.getOrDefault(RefreshModeChoice.NO_MODE)

    private fun describe(mode: Display.Mode): ScreenMode = ScreenMode(
        modeId = modeIdOf(mode),
        width = mode.physicalWidth,
        height = mode.physicalHeight,
        peakHz = peakHz(mode),
        liveHz = liveHz(mode)
    )

    /** The mode the display is serving right now, or null when this Android version will not say. */
    private fun currentModeOf(display: Display): ScreenMode? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { display.mode }.getOrNull()?.let { describe(it) }
        } else {
            // No getMode() before API 30, so the geometry comes from the display itself and the rate
            // from the live getter, which on these versions is all we need to compare against.
            val size = runCatching { Point().also { @Suppress("DEPRECATION") display.getRealSize(it) } }.getOrNull()
                ?: return null
            @Suppress("DEPRECATION")
            val hz = runCatching { display.refreshRate }.getOrDefault(0f)
            ScreenMode(RefreshModeChoice.NO_MODE, size.x, size.y, peakHz = hz, liveHz = hz)
        }

    private fun modesOf(display: Display): List<ScreenMode> =
        runCatching { display.supportedModes?.map { describe(it) } }.getOrNull() ?: emptyList()

    /** The rate the display is serving, read off the display itself: this is the live number. */
    private fun liveHzOf(display: Display): Float {
        @Suppress("DEPRECATION")
        val fromDisplay = runCatching { display.refreshRate }.getOrDefault(0f)
        if (fromDisplay > 0f) return fromDisplay
        return runCatching { display.mode }.getOrNull()?.let { liveHz(it) } ?: 0f
    }

    /** The mode to request for this context, or null when there is nothing this device can be asked for. */
    @JvmStatic
    fun planFor(context: Context?): Plan? {
        val display = context?.let { displayOf(it) } ?: return null
        val current = currentModeOf(display) ?: return null
        return RefreshModeChoice.choose(modesOf(display), current)
    }

    /**
     * What the display is serving right now. Zero means "no display to look at". This must never read
     * the top of an adaptive mode's range - that reads 120 while the panel paints at 60.
     */
    @JvmStatic
    fun activeRefreshHz(context: Context?): Float =
        context?.let { displayOf(it) }?.let { liveHzOf(it) } ?: 0f

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
    fun applyTo(activity: Activity?): Boolean = write(activity, LauncherPreferences.PREF_MAX_REFRESH_RATE)

    /** Hands the display back to the system, for the switch being turned off mid-session. */
    @JvmStatic
    fun clear(activity: Activity?) {
        write(activity, false)
    }

    /**
     * The single place the window is touched, so asking and withdrawing cannot drift apart: [enabled]
     * writes the requested mode and rate, and `false` writes the "no preference" value of each.
     */
    private fun write(activity: Activity?, enabled: Boolean): Boolean {
        if (activity == null) return false
        val plan = if (enabled) planFor(activity) else null
        if (enabled && plan == null) {
            // Nothing to gain on this panel, so withdraw instead of leaving a stale request behind.
            return write(activity, false)
        }
        var touched = false
        runCatching {
            val window = activity.window
            val params = window.attributes
            var paramsChanged = false
            val wantedModeId = if (plan != null && plan.needsModeSwitch) plan.modeId else 0
            if (wantedModeId != params.preferredDisplayModeId &&
                (plan == null || plan.modeId != RefreshModeChoice.NO_MODE)) {
                params.preferredDisplayModeId = wantedModeId
                paramsChanged = true
            }
            @Suppress("DEPRECATION")
            val wantedRate = plan?.requestedHz ?: 0f
            @Suppress("DEPRECATION")
            if (wantedRate != params.preferredRefreshRate) {
                @Suppress("DEPRECATION")
                params.preferredRefreshRate = wantedRate
                paramsChanged = true
            }
            if (paramsChanged) {
                window.attributes = params
                touched = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Neither of these binds by name from this module's SDK surface, and a typo in a
                // hidden-ish Android API is a build failure in a launcher, so both are looked up at
                // runtime and skipped when the platform does not have them.
                if (askViewForHighRate(window.decorView, high = plan != null)) touched = true
                // Window.setFrameRate carries the compatibility hint that adaptive panels act on, so
                // it is worth the extra call on top of the levers above.
                if (invokeSetFrameRate(window, wantedRate)) touched = true
            }
        }.onFailure { error ->
            Logger.appendToLog(
                "Display refresh: request failed on ${activity.javaClass.simpleName}: " +
                    "${error.javaClass.simpleName} ${error.message}"
            )
            return false
        }
        // "Something was asked for" is the only thing this return value means, so a withdrawal that
        // changed the window on the way back is deliberately reported as false.
        if (plan == null || !touched) return false
        Logger.appendToLog(
            "Display refresh: " +
                (if (plan.needsModeSwitch) "requested display mode ${plan.modeId} at " else "asked for ") +
                "${fmt(plan.requestedHz)}Hz for ${plan.width}x${plan.height} (was ${fmt(plan.currentHz)}Hz, " +
                "${plan.fasterModes} faster mode(s) on this display); display now at " +
                "${fmt(activeRefreshHz(activity))}Hz"
        )
        return true
    }

    /** `Window.setFrameRate(float, int)`: public since API 31, absent from this module's SDK surface. */
    private fun invokeSetFrameRate(window: Window, rateHz: Float): Boolean = runCatching {
        window.javaClass.getMethod(
            "setFrameRate", Float::class.javaPrimitiveType, Int::class.javaPrimitiveType
        ).invoke(window, rateHz, FRAME_RATE_COMPATIBILITY_DEFAULT)
        true
    }.getOrDefault(false)

    /**
     * `View.setRequestedFrameRate(int)`, with the category constant read from the platform rather than
     * hardcoded - guessing at a framework constant is how a silent 60 Hz bug gets shipped. Passing
     * UNSPECIFIED is the documented way to withdraw the request.
     */
    private fun askViewForHighRate(view: View, high: Boolean): Boolean = runCatching {
        val constant = if (high) "REQUESTED_FRAME_RATE_CATEGORY_HIGH" else "REQUESTED_FRAME_RATE_CATEGORY_UNSPECIFIED"
        val category = View::class.java.getField(constant).getInt(null)
        View::class.java.getMethod("setRequestedFrameRate", Int::class.javaPrimitiveType)
            .invoke(view, category)
        true
    }.getOrDefault(false)

    /** One decimal: 119.98 should read as 120.0 in a log line, 59.94 should read as 59.9. */
    private fun fmt(hz: Float): String = String.format(java.util.Locale.ROOT, "%.1f", hz)
}
