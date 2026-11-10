package net.kdt.pojavlaunch.refreshstub

import net.kdt.pojavlaunch.utils.RefreshModeChoice.ScreenMode

/**
 * The slice of a phone screen this code talks to, as data.
 *
 * It lives in the test tree rather than the app because nothing in `DisplayRefreshMode` needs more
 * than "which modes exist, which one is live, and what did the window end up asking for" - which is
 * exactly what makes the whole request path assertable without a device. The `android.*` stubs beside
 * this file are shells that read these fields, so the production source is compiled and executed
 * unmodified.
 */
object FakeScreen {
    /** Every mode the panel advertises, at every resolution it can run. */
    var modes: List<ScreenMode> = emptyList()

    /** The mode the panel is in right now; the read-back path looks here, not at what was asked. */
    var current: ScreenMode? = null

    /** Emulated `Build.VERSION.SDK_INT`, so a test can stand in for Android 11 and for Android 12+. */
    var sdkInt: Int = 34

    /** What the window was told: the mode id written into its LayoutParams, and the rate voted. */
    var writtenModeId: Int? = null
    var writtenFrameRate: Float? = null

    /** Everything the production code logged, so the test can assert the player was told the truth. */
    val logs: MutableList<String> = mutableListOf()

    /** Whether this emulated Activity has a display at all. */
    var hasDisplay: Boolean = true

    fun reset() {
        modes = emptyList()
        current = null
        sdkInt = 34
        writtenModeId = null
        writtenFrameRate = null
        logs.clear()
        hasDisplay = true
    }

    /** A 1080x2400 panel offering 60/90/120 as separate modes, sitting on the 60Hz entry. */
    fun tripleRatePanel() {
        modes = listOf(
            ScreenMode(1, 1080, 2400, 60f, 60f),
            ScreenMode(2, 1080, 2400, 90f, 90f),
            ScreenMode(3, 1080, 2400, 120f, 120f)
        )
        current = modes[0]
    }

    /** A single adaptive mode that idles at 60 and can be voted up to 120 without changing ids. */
    fun adaptivePanel() {
        val one = ScreenMode(0, 1080, 2400, 120f, 60f)
        modes = listOf(one)
        current = one
    }

    /** A 60Hz-only panel: the shape that must produce no request at all. */
    fun sixtyOnlyPanel() {
        val one = ScreenMode(1, 1080, 2400, 60f, 60f)
        modes = listOf(one)
        current = one
    }
}
