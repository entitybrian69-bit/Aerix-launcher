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
        /** The mode's nominal rate: rounded, and the only rate member every SDK level binds. */
        @Suppress("DEPRECATION")
        val refreshRate: Float get() = spec.peakHz
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
        @Suppress("DEPRECATION")
        var preferredRefreshRate: Float = 0f

        fun copyFrom(other: LayoutParams) {
            preferredDisplayModeId = other.preferredDisplayModeId
            preferredRefreshRate = other.preferredRefreshRate
        }

        companion object {
            const val FRAME_RATE_COMPATIBILITY_DEFAULT = 0
        }
    }
}

class View {
    companion object {
        const val REQUESTED_FRAME_RATE_CATEGORY_UNSPECIFIED = 0
        const val REQUESTED_FRAME_RATE_CATEGORY_NORMAL = 1
        const val REQUESTED_FRAME_RATE_CATEGORY_HIGH = 2
    }

    fun setRequestedFrameRate(category: Int) {
        FakeScreen.writtenFrameRateCategory = category
    }
}

class Window {
    val decorView: View = View()

    private val attrs = WindowManager.LayoutParams()

    /**
     * Getters hand out a copy and setters record only what actually changed, because that is what the
     * framework does: production code reads the attributes, mutates them, and writes the same instance
     * back, so a naive recorder would report every field as written on every pass.
     */
    var attributes: WindowManager.LayoutParams
        get() = WindowManager.LayoutParams().also { it.copyFrom(attrs) }
        set(value) {
            if (attrs.preferredDisplayModeId != value.preferredDisplayModeId) {
                FakeScreen.writtenModeId = value.preferredDisplayModeId
            }
            if (attrs.preferredRefreshRate != value.preferredRefreshRate) {
                FakeScreen.writtenPreferredRate = value.preferredRefreshRate
            }
            attrs.copyFrom(value)
        }

    /** Reached only reflectively by the production code, so it must be a real public method. */
    fun setFrameRate(rate: Float, compatibility: Int) {
        FakeScreen.writtenFrameRate = rate
    }
}
