package net.kdt.pojavlaunch.aerix.renderer

import org.junit.Assert.*
import org.junit.Test

class VulkanEligibilityTest {
    private val vk13 = (1L shl 22) + (3L shl 12)
    private val capable = VulkanDevice(0x5143, 1, 42, vk13, false, true)
    @Test fun unfinishedVulkanRequestFallsBackToGlesBeforeEnvironmentSetup() {
        val env = RustGlBackendPolicy.environment(mapOf("RENDERER_BACKEND" to "directvk", "MESA_LOADER_DRIVER_OVERRIDE" to "zink", "OTHER" to "kept"))
        assertEquals("gles", env["RENDERER_BACKEND"])
        assertEquals("", env["MESA_LOADER_DRIVER_OVERRIDE"])
        assertEquals("kept", env["OTHER"])
    }
    @Test fun core13DoesNotRequireExtensionString() {
        assertNull(VulkanEligibility.rejection(capable, emptyList(), true))
    }
    @Test fun extensionAndFeatureRequiredBelow13() {
        val d = capable.copy(apiVersion = vk13 - (1L shl 12))
        assertNotNull(VulkanEligibility.rejection(d, emptyList(), true))
        assertNull(VulkanEligibility.rejection(d.copy(dynamicExtension = true), emptyList(), true))
        assertNotNull(VulkanEligibility.rejection(d.copy(dynamicExtension = true, dynamicFeature = false), emptyList(), true))
    }
    @Test fun api10CannotQualifyEvenWithExtension() {
        assertNotNull(VulkanEligibility.rejection(capable.copy(apiVersion = 1L shl 22, dynamicExtension = true), emptyList(), true))
    }
    @Test fun unknownCapabilitiesAndUnfinishedRendererCannotQualify() {
        assertNotNull(VulkanEligibility.rejection(null, emptyList(), true))
        assertNotNull(VulkanEligibility.rejection(capable, emptyList(), false))
    }
    @Test fun exactBadDriverDeniedWithoutBlacklistingWholeVendor() {
        val rule = BadVulkanDriver(capable.vendorId, capable.deviceId, capable.driverVersion, "device report URI")
        assertNotNull(VulkanEligibility.rejection(capable, listOf(rule), true))
        assertNull(VulkanEligibility.rejection(capable.copy(driverVersion = 43), listOf(rule), true))
    }
}
