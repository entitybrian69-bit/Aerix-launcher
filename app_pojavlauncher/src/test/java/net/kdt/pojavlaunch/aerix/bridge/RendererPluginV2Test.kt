package net.kdt.pojavlaunch.aerix.bridge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RendererPluginV2Test {
    @get:Rule val temporary = TemporaryFolder()
    private fun directory() = temporary.newFolder().also { File(it, "librust_gl.so").writeBytes(byteArrayOf()) }
    private fun normal(key: String, value: String) = JsonObject().apply {
        addProperty("type", "NormalEnv"); addProperty("key", key); addProperty("value", value)
    }
    private fun metadata(vararg vars: JsonObject) = JsonObject().apply {
        addProperty("displayName", "RustGL"); addProperty("rendererId", "opengles3_rust")
        addProperty("rendererGLPath", "**|librust_gl.so"); addProperty("rendererEGLPath", "librust_gl.so")
        add("dlopenLibPaths", JsonArray()); add("env", JsonArray().apply { vars.forEach { add(it) } })
    }
    @Test fun realRustGlSchemaResolvesNativePathsAndKeepsBlankMesaOverrideOnGles() {
        val dir = directory()
        val config = RendererPluginV2.parse(metadata(normal("MESA_LOADER_DRIVER_OVERRIDE", ""),
            normal("LIBGL_ES", "3"), normal("RENDERER_BACKEND", "gles")).toString(), dir)
        assertEquals("RustGL", config.name)
        assertEquals(File(dir, "librust_gl.so").canonicalPath, config.gl)
        assertEquals(config.gl, config.egl)
        assertFalse(RendererPluginV2.usesVulkan(config.id, config.env))
        assertFalse(RendererPluginV2.usesDesktopGl(config.id, config.env))
    }
    @Test fun actualVulkanRenderersStillUseVulkanSetup() {
        assertTrue(RendererPluginV2.usesVulkan("zink", emptyMap()))
        assertTrue(RendererPluginV2.usesVulkan("vulkan_plugin", emptyMap()))
        assertTrue(RendererPluginV2.usesVulkan("mesa", mapOf("MESA_LOADER_DRIVER_OVERRIDE" to "zink")))
        assertFalse(RendererPluginV2.usesVulkan("gles", mapOf("MESA_LOADER_DRIVER_OVERRIDE" to "virpipe")))
        assertTrue(RendererPluginV2.usesDesktopGl("gles", mapOf("MESA_LOADER_DRIVER_OVERRIDE" to "virpipe")))
        assertTrue(RendererPluginV2.usesDesktopGl("zink", emptyMap()))
    }
    @Test fun selectableDefaultsAndDisabledSettingsFollowV2Schema() {
        val selectable = JsonObject().apply {
            addProperty("type", "SelectableEnv"); addProperty("key", "RENDERER_TRACE_GL")
            addProperty("check", true)
            add("items", JsonObject().apply {
                addProperty("defaultValue", "0"); add("values", JsonArray().apply { add("0"); add("1") })
            })
        }
        val config = RendererPluginV2.parse(metadata(selectable).toString(), directory())
        assertEquals("0", config.env["RENDERER_TRACE_GL"])
        selectable.addProperty("check", false)
        assertFalse(RendererPluginV2.parse(metadata(selectable).toString(), directory()).env.containsKey("RENDERER_TRACE_GL"))
    }
    @Test fun optionalAndToggleDefaultsAreAppliedWithoutInventingValues() {
        val custom = JsonObject().apply { addProperty("type", "CustomizableEnv"); addProperty("key", "CUSTOM") }
        val toggle = JsonObject().apply { addProperty("type", "ToggleableEnv"); addProperty("key", "TOGGLE"); addProperty("value", "1"); addProperty("toggle", false) }
        assertTrue(RendererPluginV2.parse(metadata(custom, toggle).toString(), directory()).env.isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun missingNativeAbiIsRejected() {
        RendererPluginV2.parse(metadata().toString(), temporary.newFolder())
    }
    @Test(expected = IllegalArgumentException::class) fun pathTraversalIsRejected() {
        val root = metadata().apply { addProperty("rendererGLPath", "**|../librust_gl.so") }
        RendererPluginV2.parse(root.toString(), directory())
    }
    @Test(expected = IllegalArgumentException::class) fun arbitraryAbsoluteLibrariesAreRejected() {
        RendererPluginV2.parse(metadata().apply { addProperty("rendererGLPath", "/data/elsewhere/lib.so") }.toString(), directory())
    }
    @Test(expected = IllegalArgumentException::class) fun unresolvedPreloadDependenciesAreNotSilentlyIgnored() {
        val data = metadata().apply { getAsJsonArray("dlopenLibPaths").add("**|libdependency.so") }
        RendererPluginV2.parse(data.toString(), directory())
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateEnvironmentKeysAreRejected() {
        RendererPluginV2.parse(metadata(normal("X", "1"), normal("X", "2")).toString(), directory())
    }
    @Test(expected = IllegalArgumentException::class) fun embeddedNulIsRejected() {
        RendererPluginV2.parse(metadata(normal("X", "bad\u0000value")).toString(), directory())
    }
    @Test fun nativeMarkerInEnvironmentIsResolved() {
        val dir = directory()
        val config = RendererPluginV2.parse(metadata(normal("LIBGL_NAME", "**|librust_gl.so")).toString(), dir)
        assertEquals(File(dir, "librust_gl.so").canonicalPath, config.env["LIBGL_NAME"])
    }
    @Test(expected = IllegalArgumentException::class) fun invalidSelectableDefaultIsRejected() {
        val variable = JsonObject().apply {
            addProperty("type", "SelectableEnv"); addProperty("key", "CHOICE")
            add("items", JsonObject().apply { addProperty("defaultValue", "wrong"); add("values", JsonArray().apply { add("valid") }) })
        }
        RendererPluginV2.parse(metadata(variable).toString(), directory())
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedMetadataIsRejected() {
        RendererPluginV2.parse(" ".repeat(65537), directory())
    }
    @Test fun systemEglIsAcceptedWithoutPretendingItIsBundled() {
        val config = RendererPluginV2.parse(metadata().apply { addProperty("rendererEGLPath", "libEGL.so") }.toString(), directory())
        assertEquals("libEGL.so", config.egl)
    }
    @Test fun binaryAbiMustMatchTheActualProcessArchitecture() {
        val library = File(directory(), "librust_gl.so")
        val header = ByteArray(20).apply {
            this[0] = 0x7f; this[1] = 'E'.code.toByte(); this[2] = 'L'.code.toByte(); this[3] = 'F'.code.toByte()
            this[4] = 2; this[5] = 1; this[18] = 183.toByte()
        }
        library.writeBytes(header)
        assertTrue(RendererPluginV2.matchesElfAbi(library, "arm64-v8a"))
        assertFalse(RendererPluginV2.matchesElfAbi(library, "armeabi-v7a"))
        assertFalse(RendererPluginV2.matchesElfAbi(library, "x86_64"))
        header[4] = 1; header[18] = 40
        library.writeBytes(header)
        assertTrue(RendererPluginV2.matchesElfAbi(library, "armeabi-v7a"))
        assertFalse(RendererPluginV2.matchesElfAbi(library, "arm64-v8a"))
    }
    @Test fun missingTruncatedOrNonElfLibrariesDoNotPassAbiChecks() {
        val dir = directory()
        assertFalse(RendererPluginV2.matchesElfAbi(File(dir, "missing.so"), "arm64-v8a"))
        val file = File(dir, "librust_gl.so")
        assertFalse(RendererPluginV2.matchesElfAbi(file, "arm64-v8a"))
        file.writeText("This is not an ELF binary even if its filename ends with .so")
        assertFalse(RendererPluginV2.matchesElfAbi(file, "arm64-v8a"))
    }

}
