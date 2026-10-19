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
        assertEquals("0.3.1", plugin.appVersion)
        val plan = plugin.eglLaunchPlan()
        assertEquals(plugin.glName, plan.library)
        assertFalse("System EGL/GLES must not be duplicated into a driver namespace", plan.namespaceBypass)
        assertTrue(plan.gles)
        assertEquals(3, plan.major)
        assertTrue(File(plugin.glName).length() > 64)
        assertEquals(plugin.glName, plugin.eglName)
        assertEquals(File(plugin.glName).parentFile.canonicalPath, plugin.librarySearchPath())
    }

    @Test fun quarantinedOldBundleDoesNotHideRevisedIntegration() {
        val app = context(0x30000)
        val revised = requireNotNull(BundledRustGl.discover(app))
        val index = com.google.gson.JsonParser.parseString(app.assets.open("renderer-bundles/rustgl/index.json")
            .bufferedReader().use { it.readText() }).asJsonObject
        val entry = index.getAsJsonArray("releases").first { it.asJsonObject.get("abi").asString ==
            (if (android.os.Process.is64Bit()) "arm64-v8a" else "armeabi-v7a") }.asJsonObject
        val files = entry.getAsJsonArray("files").map { item -> item.asJsonObject.let {
            BundleFile(it.get("name").asString, it.get("bytes").asLong, it.get("sha256").asString)
        } }
        val old = BundleRelease("0.3.0", entry.get("revision").asString, entry.get("abi").asString, files)
        val store = RendererBundleStore(File(app.noBackupFilesDir, "renderer-bundles/rustgl"))
        store.install(old) { name -> app.assets.open("renderer-bundles/rustgl/0.3.1/${old.abi}/$name") }
        store.activate(old)
        store.beginInit(old).finish(false)
        val restored = requireNotNull(BundledRustGl.discover(app))
        assertEquals("0.3.1", restored.appVersion)
        assertEquals(revised.glName, restored.glName)
        assertTrue("Do not erase the old failure record to restore discovery", store.rejected(old))
        assertFalse(restored.eglLaunchPlan().namespaceBypass)
    }

    @Test fun gles2OnlyDeviceDoesNotGetUnsupportedSelectableRenderer() {
        assertNull(BundledRustGl.discover(context(0x20000)))
    }
}
