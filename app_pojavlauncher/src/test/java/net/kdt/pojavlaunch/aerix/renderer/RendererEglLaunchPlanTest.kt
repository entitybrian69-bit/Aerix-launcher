package net.kdt.pojavlaunch.aerix.renderer

import net.kdt.pojavlaunch.aerix.bridge.AerixRendererPlugin
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** No native loading or Android device required: these are the exact MojoExec call arguments. */
class RendererEglLaunchPlanTest {
    @get:Rule val temp = TemporaryFolder()
    private fun plugin(egl: String, directory: String, env: Map<String, String> = emptyMap()) = AerixRendererPlugin(
        packageName = "test.renderer", appName = "Test", appVersion = "1", rendererId = "test",
        displayName = "Test", summary = "", minMCVer = null, maxMCVer = null,
        glName = egl, eglName = egl, nativeLibDir = directory, env = env, dlopen = emptyList())

    @Test fun externalPluginsRetainNamespaceBypassAndGles3() {
        val plan = plugin("libEGL.so", temp.newFolder().path).eglLaunchPlan()
        assertTrue(plan.namespaceBypass)
        assertTrue(plan.gles)
        assertEquals(3, plan.major)
        assertEquals("libEGL.so", plan.library)
    }

    @Test fun absolutePrivateLibraryIsNotRewritten() {
        val file = temp.newFile("librust_gl.so")
        val plan = plugin(file.absolutePath, file.parent!!).eglLaunchPlan()
        assertEquals(file.absolutePath, plan.library)
    }

    @Test fun relativeExistingPluginLibraryResolvesWithinNativeDirectory() {
        val file = temp.newFile("libcustom.so")
        val plan = plugin(file.name, file.parent!!).eglLaunchPlan()
        assertEquals(file.absolutePath, plan.library)
    }
}
