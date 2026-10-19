package net.kdt.pojavlaunch.aerix.renderer

internal data class VulkanDevice(
    val vendorId: Long, val deviceId: Long, val driverVersion: Long, val apiVersion: Long,
    val dynamicExtension: Boolean, val dynamicFeature: Boolean
)
internal data class BadVulkanDriver(val vendorId: Long, val deviceId: Long, val driverVersion: Long, val evidence: String)

internal object VulkanEligibility {
    fun rejection(device: VulkanDevice?, badDrivers: List<BadVulkanDriver>, rendererQualified: Boolean): String? {
        if (device == null) return "No successful system-driver capability report"
        if (badDrivers.any { it.vendorId == device.vendorId && it.deviceId == device.deviceId && it.driverVersion == device.driverVersion })
            return "Driver disabled by an evidence-backed compatibility rule"
        if (device.apiVersion < (1L shl 22) + (1L shl 12)) return "Vulkan 1.1 device required"
        if (!device.dynamicFeature) return "dynamicRendering feature is unavailable"
        if (device.apiVersion < (1L shl 22) + (3L shl 12) && !device.dynamicExtension)
            return "VK_KHR_dynamic_rendering is required before Vulkan 1.3"
        if (!rendererQualified) return "Native RustGL Vulkan renderer has not passed rendering validation"
        return null
    }
}
