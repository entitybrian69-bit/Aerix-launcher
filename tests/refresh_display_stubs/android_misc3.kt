package android.content

import net.kdt.pojavlaunch.refreshstub.FakeScreen

open class Context {
    open val display: android.view.Display? get() = if (FakeScreen.hasDisplay) android.view.Display() else null
    open fun getSystemService(name: String): Any? =
        if (name == WINDOW_SERVICE) android.view.WindowManager() else null

    companion object { const val WINDOW_SERVICE = "window" }
}
