package android.hardware.display

import android.view.Display
import net.kdt.pojavlaunch.refreshstub.FakeScreen

/**
 * The display service, which - unlike `Context.getDisplay()` - a Context tied to no window is still
 * allowed to ask about the panel. `DisplayRefreshMode` falls back to exactly this on an Application
 * context, so the stub answers the way the platform does: the default display exists whenever a screen
 * exists at all, and no other id does.
 */
class DisplayManager {
    fun getDisplay(displayId: Int): Display? =
        if (displayId == Display.DEFAULT_DISPLAY && FakeScreen.hasDisplay) Display() else null
}
