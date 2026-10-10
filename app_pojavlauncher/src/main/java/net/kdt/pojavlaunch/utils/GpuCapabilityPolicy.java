package net.kdt.pojavlaunch.utils;

/** Pure capability predicates that can be tested without a device or Android framework. */
public final class GpuCapabilityPolicy {
    private static final int API_VULKAN_FEATURES_ADDED = 24;

    private GpuCapabilityPolicy() {
    }

    /**
     * Android's Vulkan feature flags are a prerequisite for Vulkan renderers, not a guarantee that
     * a particular driver exposes every extension or feature required by a game/backend.
     */
    public static boolean declaresVulkanHardwareSupport(int androidApiLevel,
                                                        boolean hasHardwareLevel,
                                                        boolean hasHardwareVersion) {
        return androidApiLevel >= API_VULKAN_FEATURES_ADDED
                && hasHardwareLevel
                && hasHardwareVersion;
    }
}
