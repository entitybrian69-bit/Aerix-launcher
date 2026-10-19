package net.kdt.pojavlaunch.aerix.renderer

import android.app.Application
import net.kdt.pojavlaunch.PojavApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class IsolatedProbeApplicationTest {
    @Test fun isolatedApplicationSkipsLauncherAndMobileGluesPrivateStorageInitialization() {
        ShadowProcess.setUid(99001)
        Robolectric.buildApplication(PojavApplication::class.java).create().get()
    }
}
