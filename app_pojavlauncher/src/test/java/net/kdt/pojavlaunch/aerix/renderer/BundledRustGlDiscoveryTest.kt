package net.kdt.pojavlaunch.aerix.renderer

import android.app.ActivityManager
import android.app.Application
import android.content.pm.ConfigurationInfo
import android.os.Build
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BundledRustGlDiscoveryTest {
    private fun context(gles: Int): Application {
        val app = RuntimeEnvironment.getApplication()
        val present = app.assets.list("renderer-bundles/rustgl")?.contains("index.json") == true
        if (System.getenv("GITHUB_ACTIONS") == "true") assertTrue("CI must build a real RustGL payload", present)
        assumeTrue("Generate source-built assets before running this integration test locally", present)
        ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_32_BIT_ABIS", arrayOf("armeabi-v7a"))
        ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_64_BIT_ABIS", arrayOf("arm64-v8a"))
        shadowOf(app.getSystemService(ActivityManager::class.java)).setDeviceConfigurationInfo(
            ConfigurationInfo().apply { reqGlEsVersion = gles })
        return app
    }

    @Test fun sourceBuiltPayloadRegistersRealSelectableRendererWithoutPluginApk() {
        val plugin = BundledRustGl.discover(context(0x30000))
        assertNotNull("Generated assets must produce an actual RenderSpec", plugin)
        requireNotNull(plugin)
        assertEquals("plugin:net.kdt.pojavlaunch.renderer.rustgl", plugin.pluginTag)
        assertEquals("RustGL (Experimental)", plugin.displayName)
        assertEquals("gles", plugin.env["RENDERER_BACKEND"])
        assertTrue(File(plugin.glName).length() > 64)
        assertEquals(plugin.glName, plugin.eglName)
        assertEquals(File(plugin.glName).parentFile.canonicalPath, plugin.librarySearchPath())
    }

    @Test fun gles2OnlyDeviceDoesNotGetUnsupportedSelectableRenderer() {
        assertNull(BundledRustGl.discover(context(0x20000)))
    }
}
