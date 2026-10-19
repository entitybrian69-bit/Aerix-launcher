#include <jni.h>
#include <vulkan/vulkan.h>
#include <dlfcn.h>
#include <algorithm>
#include <cstring>
#include <sstream>
#include <vector>

static std::string quoted(const char* text) {
    std::string out = "\"";
    for (const unsigned char* p = reinterpret_cast<const unsigned char*>(text); *p; ++p) {
        if (*p == '"' || *p == '\\') { out += '\\'; out += static_cast<char>(*p); }
        else if (*p >= 32 && *p < 127) out += static_cast<char>(*p);
        else out += '?';
    }
    return out + "\"";
}

static std::string probe() {
    void* library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) return R"({"error":"system Vulkan loader unavailable"})";
    struct Close { void* handle; ~Close() { dlclose(handle); } } close{library};
    auto get = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(library, "vkGetInstanceProcAddr"));
    if (!get) return R"({"error":"vkGetInstanceProcAddr unavailable"})";
    auto enumerateVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(get(VK_NULL_HANDLE, "vkEnumerateInstanceVersion"));
    uint32_t loaderApi = VK_API_VERSION_1_0;
    if (enumerateVersion && enumerateVersion(&loaderApi) != VK_SUCCESS) return R"({"error":"loader version query failed"})";
    if (loaderApi < VK_API_VERSION_1_1) return R"({"error":"Vulkan 1.1 loader required"})";
    auto create = reinterpret_cast<PFN_vkCreateInstance>(get(VK_NULL_HANDLE, "vkCreateInstance"));
    if (!create) return R"({"error":"vkCreateInstance unavailable"})";
    VkApplicationInfo app{}; app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "Aerix isolated capability probe";
    app.apiVersion = std::min(loaderApi, static_cast<uint32_t>(VK_API_VERSION_1_3));
    VkInstanceCreateInfo info{}; info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO; info.pApplicationInfo = &app;
    VkInstance instance = VK_NULL_HANDLE;
    const VkResult result = create(&info, nullptr, &instance);
    if (result != VK_SUCCESS || instance == VK_NULL_HANDLE) return "{\"error\":\"vkCreateInstance failed\",\"vkResult\":" + std::to_string(result) + "}";
    auto destroy = reinterpret_cast<PFN_vkDestroyInstance>(get(instance, "vkDestroyInstance"));
    struct Destroy { PFN_vkDestroyInstance fn; VkInstance instance; ~Destroy() { if (fn) fn(instance, nullptr); } } cleanup{destroy, instance};
    auto enumerate = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(get(instance, "vkEnumeratePhysicalDevices"));
    auto properties = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(get(instance, "vkGetPhysicalDeviceProperties"));
    auto extensions = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(get(instance, "vkEnumerateDeviceExtensionProperties"));
    auto features = reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures2>(get(instance, "vkGetPhysicalDeviceFeatures2"));
    if (!destroy || !enumerate || !properties || !extensions || !features) return R"({"error":"required instance entry point missing"})";
    uint32_t count = 0;
    if (enumerate(instance, &count, nullptr) != VK_SUCCESS || count == 0 || count > 32) return R"({"error":"physical device enumeration failed"})";
    std::vector<VkPhysicalDevice> devices(count);
    if (enumerate(instance, &count, devices.data()) != VK_SUCCESS) return R"({"error":"physical device list changed"})";
    std::ostringstream out;
    out << "{\"loaderApi\":" << loaderApi << ",\"driverSource\":\"system\",\"devices\":[";
    for (uint32_t i = 0; i < count; ++i) {
        VkPhysicalDeviceProperties props{}; properties(devices[i], &props);
        uint32_t n = 0;
        if (extensions(devices[i], nullptr, &n, nullptr) != VK_SUCCESS || n > 4096) return R"({"error":"extension enumeration failed"})";
        std::vector<VkExtensionProperties> available(n);
        if (n && extensions(devices[i], nullptr, &n, available.data()) != VK_SUCCESS) return R"({"error":"extension list changed"})";
        bool dynamicExtension = false;
        for (const auto& extension : available) if (!strcmp(extension.extensionName, "VK_KHR_dynamic_rendering")) dynamicExtension = true;
        VkPhysicalDeviceDynamicRenderingFeatures dynamic{}; dynamic.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES;
        VkPhysicalDeviceFeatures2 flags{}; flags.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2; flags.pNext = &dynamic;
        if (props.apiVersion >= VK_API_VERSION_1_3 || dynamicExtension) features(devices[i], &flags);
        if (i) out << ',';
        out << "{\"name\":" << quoted(props.deviceName) << ",\"vendorId\":" << props.vendorID
            << ",\"deviceId\":" << props.deviceID << ",\"driverVersion\":" << props.driverVersion
            << ",\"apiVersion\":" << props.apiVersion << ",\"dynamicExtension\":" << (dynamicExtension ? "true" : "false")
            << ",\"dynamicFeature\":" << (dynamic.dynamicRendering ? "true" : "false") << '}';
    }
    return out.str() + "]}";
}

extern "C" JNIEXPORT jstring JNICALL
Java_net_kdt_pojavlaunch_aerix_renderer_VulkanProbeService_nativeProbe(JNIEnv* env, jobject) {
    try { return env->NewStringUTF(probe().c_str()); }
    catch (...) { return env->NewStringUTF("{\"error\":\"native probe exception\"}"); }
}
