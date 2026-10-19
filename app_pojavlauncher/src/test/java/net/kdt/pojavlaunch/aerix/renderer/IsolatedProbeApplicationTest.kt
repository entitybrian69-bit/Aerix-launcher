package net.kdt.pojavlaunch.aerix.renderer

import android.app.Application
import net.kdt.pojavlaunch.PojavApplication
import org.junit.Test
import org.junit.runner.RunWith
import android.content.Context
import net.kdt.pojavlaunch.mobileglues.MGApplication
import org.junit.Assert.*
import org.robolectric.RuntimeEnvironment
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class IsolatedProbeApplicationTest {
    @Test fun isolatedApplicationSkipsLauncherAndMobileGluesPrivateStorageInitialization() {
        ShadowProcess.setUid(99001)
        val app = PojavApplication()
        ReflectionHelpers.callInstanceMethod<Unit>(app, "attach",
            ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        assertNotEquals(99001, app.applicationInfo.uid)
        app.onCreate()
        val auth = MGApplication::class.java.getDeclaredField("authController\$delegate")
        auth.isAccessible = true
        assertFalse((auth.get(app) as Lazy<*>).isInitialized())
    }
}
