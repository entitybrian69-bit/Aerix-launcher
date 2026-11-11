package android.content

import net.kdt.pojavlaunch.refreshstub.FakeScreen

/**
 * Enough of `android.content.Context` to tell the two kinds apart, because that distinction is the bug:
 * a visual Context answers `getDisplay()`, and the Application context - the one the preference loader
 * holds - throws at it.
 */
open class Context {
    /** True for an Activity or a window/display context; false for one tied to no window at all. */
    open val associatedWithDisplay: Boolean get() = true

    open val display: android.view.Display?
        get() {
            if (!associatedWithDisplay) {
                if (FakeScreen.refusesDisplaylessContext) {
                    throw UnsupportedOperationException(
                        "Tried to obtain display from a Context not associated with one")
                }
                return null
            }
            return if (FakeScreen.hasDisplay) android.view.Display() else null
        }

    open fun getSystemService(name: String): Any? = when (name) {
        WINDOW_SERVICE -> android.view.WindowManager()
        DISPLAY_SERVICE -> android.hardware.display.DisplayManager()
        else -> null
    }

    companion object {
        const val WINDOW_SERVICE = "window"
        const val DISPLAY_SERVICE = "display"
    }
}
