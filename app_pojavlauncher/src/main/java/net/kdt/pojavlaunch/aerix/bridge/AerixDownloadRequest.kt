/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Reconstructed download-request hop from the instance content panes to the Discover section.
 */
package net.kdt.pojavlaunch.aerix.bridge

import net.kdt.pojavlaunch.aerix.ui.LauncherSection

/** Type of downloadable content a pane can ask to fetch. */
enum class AssetType {
    MOD,
    MODPACK,
    RESOURCE_PACK,
    SHADER_PACK,
    WORLD
}

/** Carries a pending "download this kind of content" intent into the Discover section. */
object AerixDownloadRequest {
    var pendingAssetType: AssetType? = null
        private set

    fun open(assetType: AssetType, navigate: (LauncherSection) -> Unit) {
        pendingAssetType = assetType
        navigate(LauncherSection.DISCOVER)
    }

    fun consume(): AssetType? {
        val type = pendingAssetType
        pendingAssetType = null
        return type
    }
}
