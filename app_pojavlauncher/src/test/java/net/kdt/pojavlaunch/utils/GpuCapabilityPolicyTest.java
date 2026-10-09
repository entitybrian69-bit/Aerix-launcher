package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GpuCapabilityPolicyTest {
    @Test
    public void requiresBothAndroidVulkanHardwareFeatures() {
        assertTrue(GpuCapabilityPolicy.declaresVulkanHardwareSupport(24, true, true));
        assertFalse(GpuCapabilityPolicy.declaresVulkanHardwareSupport(24, true, false));
        assertFalse(GpuCapabilityPolicy.declaresVulkanHardwareSupport(24, false, true));
        assertFalse(GpuCapabilityPolicy.declaresVulkanHardwareSupport(24, false, false));
    }

    @Test
    public void doesNotTreatApi23AsVulkanCapableFromFeatureFlagsAlone() {
        assertFalse(GpuCapabilityPolicy.declaresVulkanHardwareSupport(23, true, true));
    }
}
