package android.os

import net.kdt.pojavlaunch.refreshstub.FakeScreen

object Build {
    object VERSION {
        /** A getter rather than a final field so a test can emulate an older platform. */
        val SDK_INT: Int get() = FakeScreen.sdkInt
    }
    object VERSION_CODES {
        const val N = 24
        const val R = 30
        const val S = 31
    }
}
