package net.kdt.pojavlaunch.aerix.bridge

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/** V2 metadata interoperability. This is Aerix code, not vendored RustGL renderer source. */
internal data class RendererPluginConfig(
    val name: String, val id: String, val gl: String, val egl: String,
    val env: Map<String, String>, val minVersion: String?, val maxVersion: String?
)

internal object RendererPluginV2 {
    const val RUSTGL_PACKAGE = "dev.rustrenderer.plugin"
    private val libraryName = Regex("lib[A-Za-z0-9_.+-]+\\.so")
    private val environmentKey = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun usesVulkan(id: String, env: Map<String, String>): Boolean =
        id.contains("zink", true) || id.contains("vulkan", true) ||
            env["MESA_LOADER_DRIVER_OVERRIDE"].orEmpty().trim().equals("zink", true)

    /** Resolve only packaged native libraries. A metadata marker is not a literal dlopen path. */
    fun nativeLibrary(value: String, directory: File, systemEgl: Boolean = false): String {
        if (systemEgl && value == "libEGL.so") return value
        val name = value.removePrefix("**|")
        require(libraryName.matches(name)) { "Invalid plugin native library path" }
        val root = directory.canonicalFile
        val file = File(root, name).canonicalFile
        require(file.parentFile == root && file.isFile) { "Plugin native library is missing for this ABI: $name" }
        return file.absolutePath
    }

    fun parse(json: String, nativeDirectory: File): RendererPluginConfig {
        require(json.length <= 64 * 1024) { "Renderer metadata exceeds limit" }
        val root = JsonParser.parseString(json).asJsonObject
        val name = root.required("displayName").also { require(it.isNotBlank() && it.length <= 160) }
        val id = root.required("rendererId").also { require(Regex("[A-Za-z0-9_.-]{1,128}").matches(it)) }
        val gl = nativeLibrary(root.required("rendererGLPath"), nativeDirectory)
        val egl = nativeLibrary(root.required("rendererEGLPath"), nativeDirectory, systemEgl = true)
        // RustGL requires none. Do not silently ignore libraries another plugin needs preloaded.
        require(root.getAsJsonArray("dlopenLibPaths")?.size()?.let { it == 0 } != false) {
            "This V2 plugin requires preloading, which this adapter does not yet support"
        }
        val env = linkedMapOf<String, String>()
        val variables = root.getAsJsonArray("env")
        require((variables?.size() ?: 0) <= 128)
        variables?.forEach { element ->
            val variable = element.asJsonObject
            val key = variable.required("key")
            require(environmentKey.matches(key) && !env.containsKey(key)) { "Invalid or duplicate environment key" }
            val value = when (variable.required("type")) {
                "NormalEnv" -> variable.required("value")
                "SelectableEnv" -> if (variable.get("check")?.takeUnless { it.isJsonNull }?.asBoolean != false) {
                    val items = variable.getAsJsonObject("items")
                    items.required("defaultValue").also { default ->
                        require(items.getAsJsonArray("values").any { it.asString == default })
                    }
                } else null
                "CustomizableEnv" -> variable.optional("defaultValue")
                "ToggleableEnv" -> if (variable.get("toggle")?.asBoolean != false) variable.required("value") else null
                else -> error("Unsupported V2 environment type")
            }
            if (value != null) {
                require(value.length <= 8192 && '\u0000' !in value && '\n' !in value && '\r' !in value)
                env[key] = if (value.startsWith("**|")) nativeLibrary(value, nativeDirectory) else value.also {
                    require(!it.contains("**|")) { "Unsupported embedded native-path marker" }
                }
            }
        }
        require(env["POJAV_RENDERER"] == null || env["POJAV_RENDERER"] == id)
        return RendererPluginConfig(name, id, gl, egl, env, root.optional("minMCVer"), root.optional("maxMCVer"))
    }
    private fun JsonObject.optional(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
    private fun JsonObject.required(key: String): String = requireNotNull(optional(key)) { "Missing $key" }
}
