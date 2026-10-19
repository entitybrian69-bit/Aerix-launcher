package net.kdt.pojavlaunch.aerix.renderer

/** Upstream's unfinished Vulkan draw paths must never be selected by release metadata. */
internal object RustGlBackendPolicy {
    fun environment(requested: Map<String, String>): Map<String, String> = requested + mapOf(
        "RENDERER_BACKEND" to "gles", "MESA_LOADER_DRIVER_OVERRIDE" to "", "LIBGL_ES" to "3"
    )
}
