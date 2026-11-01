/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: device and graphics compatibility report, the Aerix presentation of mirai's Vulkan checker:
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/vulkan_checker/VulkanChecker.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/utils/device/VulkanCapabilities.kt
 * Values come from Pojav's own probes (GpuUtils, Architecture, Tools, RendererCache) and the
 * Android PackageManager Vulkan features; no native code is added.
 * Zalith Launcher 2 Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.game.renderer.GameRenderer
import net.kdt.pojavlaunch.game.renderer.RendererCache
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.utils.GpuUtils

class VulkanInfo(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** FEATURE_VULKAN_HARDWARE_LEVEL (0 or 1), -1 when unknown. */
    val level: Int,
    /** FEATURE_VULKAN_DEQP_LEVEL as yyyy-mm-dd, null when unknown. */
    val deqpDate: String?
) {
    val versionString: String get() = "$major.$minor.$patch"

    /** mirai VulkanCapabilities.isVersionSupported: Vulkan 1.2 baseline. */
    val meetsBaseline: Boolean get() = major > 1 || (major == 1 && minor >= 2)
}

class DeviceReport(
    val manufacturer: String,
    val model: String,
    val soc: String,
    val androidRelease: String,
    val sdk: Int,
    val architecture: String,
    val abis: List<String>,
    val is32BitOnly: Boolean,
    val totalRamMb: Int,
    val freeRamMb: Int,
    val glVendor: String,
    val glRenderer: String,
    val glesMajor: Int,
    val vulkan: VulkanInfo?,
    val compatibleRenderers: List<String>,
    val currentRenderer: String
)

object PojavDeviceBridge {
    suspend fun report(context: Context): DeviceReport = withContext(Dispatchers.IO) {
        val gl = runCatching { GpuUtils.getGlInfo() }.getOrNull()
        val renderers = runCatching { RendererCache.getCompatibleRenderers(context).rendererDisplayNames.toList() }.getOrDefault(emptyList())
        val current = PojavPluginBridge.labelForTag(LauncherPreferences.PREF_RENDERER) ?: runCatching {
            GameRenderer.getKnownRenderer(LauncherPreferences.PREF_RENDERER)?.let { context.getString(it.displayName()) }
        }.getOrNull() ?: LauncherPreferences.PREF_RENDERER
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { !it.isNullOrBlank() && it != Build.UNKNOWN }.joinToString(" ")
        } else ""
        DeviceReport(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            soc = soc.ifBlank { Build.HARDWARE },
            androidRelease = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            architecture = Architecture.archAsString(Architecture.getDeviceArchitecture()),
            abis = Build.SUPPORTED_ABIS.toList(),
            is32BitOnly = Architecture.is32BitsDevice(),
            totalRamMb = runCatching { Tools.getTotalDeviceMemory(context) }.getOrDefault(0),
            freeRamMb = runCatching { Tools.getFreeDeviceMemory(context) }.getOrDefault(0),
            glVendor = gl?.vendor.orEmpty(),
            glRenderer = gl?.renderer.orEmpty(),
            glesMajor = gl?.glesMajorVersion ?: 0,
            vulkan = vulkanInfo(context.packageManager),
            compatibleRenderers = renderers,
            currentRenderer = current
        )
    }

    private fun vulkanInfo(pm: PackageManager): VulkanInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val features = pm.systemAvailableFeatures ?: return null
        var version = -1
        var level = -1
        var deqp = -1
        for (feature in features) {
            when (feature.name) {
                PackageManager.FEATURE_VULKAN_HARDWARE_VERSION -> version = feature.version
                PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL -> level = feature.version
                "android.software.vulkan.deqp.level" -> deqp = feature.version
            }
        }
        if (version <= 0) return null
        val deqpDate = if (deqp > 0) {
            "%04d-%02d-%02d".format((deqp shr 16) and 0xFFFF, (deqp shr 8) and 0xFF, deqp and 0xFF)
        } else null
        return VulkanInfo(
            major = (version shr 22) and 0x7F,
            minor = (version shr 12) and 0x3FF,
            patch = version and 0xFFF,
            level = level,
            deqpDate = deqpDate
        )
    }
}
