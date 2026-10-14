/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/DownloadScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/download/(all .kt files) (tab layout)
 * Every entry opens the Pojav installer that owns it (Pojav fragments, unchanged): vanilla instance,
 * profile type picker, OptiFine, Fabric, Forge, NeoForge, Quilt, Legacy Fabric, BTA, modpack search,
 * and the .jar runner.
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

package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.PojavInstaller
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.components.PillTabs
import net.kdt.pojavlaunch.aerix.components.QuickActionTile
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension

private class DiscoverEntry(
    val icon: Int,
    val title: Int,
    val subtitle: Int,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
fun DiscoverScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val accent = AerixThemeManager.currentAccent()
    val jarLauncher = rememberLauncherForActivityResult(OpenDocumentWithExtension("jar")) { uri ->
        if (uri != null) PojavNavigator.runJar(activity, uri)
    }
    val open: (PojavInstaller) -> Unit = { PojavNavigator.openInstaller(activity, it) }

    val versions = listOf(
        DiscoverEntry(R.drawable.aerix_ic_videogame_asset_outlined, R.string.aerix_discover_vanilla, R.string.aerix_discover_vanilla_sub, accent) {
            PojavInstanceBridge.createVanilla(activity)
        },
        DiscoverEntry(R.drawable.aerix_ic_add, R.string.aerix_discover_custom, R.string.aerix_discover_custom_sub, AerixSurface.glassBlue) {
            open(PojavInstaller.PROFILE_TYPE)
        }
    )
    val loaders = listOf(
        DiscoverEntry(R.drawable.aerix_ic_video_settings, R.string.aerix_discover_optifine, R.string.aerix_discover_optifine_sub, AerixSurface.warning) { open(PojavInstaller.OPTIFINE) },
        DiscoverEntry(R.drawable.aerix_ic_extension_outlined, R.string.aerix_discover_fabric, R.string.aerix_discover_fabric_sub, AerixSurface.glassBlue) { open(PojavInstaller.FABRIC) },
        DiscoverEntry(R.drawable.aerix_ic_build_outlined, R.string.aerix_discover_forge, R.string.aerix_discover_forge_sub, AerixSurface.curseForgeBrand) { open(PojavInstaller.FORGE) },
        DiscoverEntry(R.drawable.aerix_ic_build_outlined, R.string.aerix_discover_neoforge, R.string.aerix_discover_neoforge_sub, AerixSurface.warning) { open(PojavInstaller.NEOFORGE) },
        DiscoverEntry(R.drawable.aerix_ic_style_outlined, R.string.aerix_discover_quilt, R.string.aerix_discover_quilt_sub, AerixSurface.glassViolet) { open(PojavInstaller.QUILT) },
        DiscoverEntry(R.drawable.aerix_ic_extension_outlined, R.string.aerix_discover_legacy_fabric, R.string.aerix_discover_legacy_fabric_sub, AerixSurface.glassRose) { open(PojavInstaller.LEGACY_FABRIC) },
        DiscoverEntry(R.drawable.aerix_ic_rocket_launch_filled, R.string.aerix_discover_bta, R.string.aerix_discover_bta_sub, AerixSurface.success) { open(PojavInstaller.BTA) }
    )
    val content = listOf(
        DiscoverEntry(R.drawable.aerix_ic_extension_outlined, R.string.aerix_discover_dl_mods, R.string.aerix_discover_dl_mods_sub, AerixSurface.modrinthBrand) {
            AerixDownloadRequest.open(AssetType.MOD, navigate)
        },
        DiscoverEntry(R.drawable.aerix_ic_photo_library_outlined, R.string.aerix_discover_dl_resourcepacks, R.string.aerix_discover_dl_resourcepacks_sub, AerixSurface.glassBlue) {
            AerixDownloadRequest.open(AssetType.RESOURCE_PACK, navigate)
        },
        DiscoverEntry(R.drawable.aerix_ic_wallpaper, R.string.aerix_discover_dl_shaders, R.string.aerix_discover_dl_shaders_sub, AerixSurface.glassViolet) {
            AerixDownloadRequest.open(AssetType.SHADER_PACK, navigate)
        },
        DiscoverEntry(R.drawable.aerix_ic_videogame_asset_outlined, R.string.aerix_discover_dl_worlds, R.string.aerix_discover_dl_worlds_sub, AerixSurface.curseForgeBrand) {
            AerixDownloadRequest.open(AssetType.WORLD, navigate)
        },
        DiscoverEntry(R.drawable.aerix_ic_package_2_outlined, R.string.aerix_discover_modpack, R.string.aerix_discover_modpack_sub, AerixSurface.modrinthBrand) { open(PojavInstaller.MODPACK) },
        DiscoverEntry(R.drawable.aerix_ic_java, R.string.aerix_discover_jar, R.string.aerix_discover_jar_sub, AerixSurface.glassBlue) {
            if (PojavNavigator.canRunJar(activity)) jarLauncher.launch(null)
        }
    )
    val entries = when (tab) {
        0 -> versions
        1 -> loaders
        else -> content
    }

    AerixPage(
        title = stringResource(R.string.aerix_discover_title),
        subtitle = stringResource(R.string.aerix_discover_subtitle)
    ) {
        PillTabs(
            tabs = listOf(
                stringResource(R.string.aerix_discover_tab_versions),
                stringResource(R.string.aerix_discover_tab_loaders),
                stringResource(R.string.aerix_discover_tab_content)
            ),
            selected = tab,
            onSelect = { tab = it }
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
        ) {
            items(entries) { entry ->
                QuickActionTile(
                    iconRes = entry.icon,
                    title = stringResource(entry.title),
                    subtitle = stringResource(entry.subtitle),
                    accent = entry.accent,
                    onClick = entry.onClick,
                    modifier = Modifier.height(78.dp)
                )
            }
        }
    }
}
