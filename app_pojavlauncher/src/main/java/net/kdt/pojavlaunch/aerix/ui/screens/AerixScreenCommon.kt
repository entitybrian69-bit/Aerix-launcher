/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Shared page scaffold and helpers for the Aerix screens, following the page layout of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/elements/*.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/theme/AerixSurface.kt (AerixLayoutDefaults)
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.kdt.pojavlaunch.aerix.components.PageHeader
import net.kdt.pojavlaunch.aerix.theme.AerixLayoutDefaults
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing

/** Title + subtitle + content column used by every Aerix page except Home. */
@Composable
fun AerixPage(
    title: String,
    subtitle: String?,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AerixLayoutDefaults.pagePadding()),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        PageHeader(title = title, subtitle = subtitle, trailing = trailing)
        content()
    }
}

/**
 * Loader name derived from a Pojav version id ("fabric-loader-0.16.9-1.21.4", "1.20.1-forge-47.3.0",
 * "neoforge-21.1.77", "quilt-loader-…", "1.20.1-OptiFine_HD_U_I6", "bta-…"). Presentation only.
 */
fun loaderLabel(versionId: String, vanilla: String): String {
    val id = versionId.lowercase()
    return when {
        id.contains("neoforge") -> "NeoForge"
        id.contains("legacy-fabric") || id.contains("legacyfabric") -> "Legacy Fabric"
        id.contains("fabric") -> "Fabric"
        id.contains("quilt") -> "Quilt"
        id.contains("forge") -> "Forge"
        id.contains("optifine") -> "OptiFine"
        id.contains("bta") -> "BTA"
        else -> vanilla
    }
}
