package android.view

import net.kdt.pojavlaunch.refreshstub.FakeScreen
import net.kdt.pojavlaunch.utils.RefreshModeChoice.ScreenMode

/**
 * A stand-in for the pieces of `android.view` that `DisplayRefreshMode` touches, shaped like the real
 * SDK for those members and no further. It exists so the request path can be executed in CI.
 */
class Display {
    val mode: Mode get() = Mode(FakeScreen.current ?: error("this screen will not report its mode"))
    val supportedModes: Array<Mode> get() = FakeScreen.modes.map { Mode(it) }.toTypedArray()

    @Suppress("DEPRECATION")
    val refreshRate: Float get() = FakeScreen.current?.liveHz ?: 0f

    @Suppress("DEPRECATION")
    fun getRealSize(point: android.graphics.Point) {
        point.x = FakeScreen.current?.width ?: 0
        point.y = FakeScreen.current?.height ?: 0
    }

    class Mode internal constructor(private val spec: ScreenMode) {
        val physicalWidth: Int get() = spec.width
        val physicalHeight: Int get() = spec.height
        val physicalRefreshRate: Float get() = spec.liveHz
        val possibleRefreshRates: FloatArray get() = listOf(spec.liveHz, spec.peakHz).distinct().toFloatArray()
        val modeId: Int get() = spec.modeId
        /**
         * Emulates the pre-API-30 shape, where the id existed only as the hidden `getId()`, which is
         * what `DisplayRefreshMode` falls back to by reflection. Public here so `getMethod` finds it.
         */
        @Suppress("unused") fun getId(): Int = spec.modeId
    }
}

class WindowManager {
    val defaultDisplay: Display = Display()

    class LayoutParams {
        var preferredDisplayModeId: Int = 0
        companion object {
            const val FRAME_RATE_COMPATIBILITY_DEFAULT = 0
        }
    }
}

class Window {
    var attributes: WindowManager.LayoutParams = WindowManager.LayoutParams()
        set(value) {
            field = value
            FakeScreen.writtenModeId = value.preferredDisplayModeId
        }

    fun setFrameRate(rate: Float, compatibility: Int) {
        FakeScreen.writtenFrameRate = rate
    }
}
