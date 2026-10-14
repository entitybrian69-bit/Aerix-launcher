/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/AboutInfoScreen.kt
 * Carries the Pojav main-menu links (website, Discord) and GPL-3.0 attribution for both projects.
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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.BuildConfig
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.GlassPanel
import net.kdt.pojavlaunch.aerix.components.SectionLabel
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface

private const val TARGET_SOURCE_URL = "https://github.com/entitybrian69-bit/Aerix-launcher"
private const val UI_SOURCE_URL = "https://github.com/entitybrian69-bit/mirai-launcher-"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(activity: FragmentActivity) {
    AerixPage(
        title = stringResource(R.string.aerix_about_title),
        subtitle = stringResource(R.string.aerix_about_subtitle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.lg)) {
                    Image(painter = painterResource(R.drawable.aerix_logo), contentDescription = null, modifier = Modifier.size(72.dp))
                    Column {
                        Text(text = stringResource(R.string.aerix_app_label), color = AerixSurface.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text(text = stringResource(R.string.aerix_about_version, BuildConfig.VERSION_NAME), color = AerixSurface.textSecondary, fontSize = 12.sp)
                        Text(text = BuildConfig.APPLICATION_ID, color = AerixSurface.textMuted, fontSize = 11.sp)
                    }
                }
            }
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                SectionLabel(title = stringResource(R.string.aerix_about_license))
                Text(
                    text = stringResource(R.string.aerix_about_credits),
                    color = AerixSurface.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                ) {
                    AerixGhostButton(stringResource(R.string.aerix_about_website), R.drawable.aerix_ic_public, { PojavNavigator.openWebsite(activity) })
                    AerixGhostButton("Discord", R.drawable.aerix_ic_link, { PojavNavigator.openDiscord(activity) })
                    AerixGhostButton(stringResource(R.string.aerix_about_source), R.drawable.aerix_ic_terminal_outlined, { PojavNavigator.openUrl(activity, TARGET_SOURCE_URL) })
                    AerixGhostButton(stringResource(R.string.aerix_about_ui_source), R.drawable.aerix_ic_style_outlined, { PojavNavigator.openUrl(activity, UI_SOURCE_URL) })
                    AerixGhostButton("GPL-3.0", R.drawable.aerix_ic_copyright_outlined, { PojavNavigator.openUrl(activity, LICENSE_URL) })
                }
            }
        }
    }
}
