/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/MiraiNavigationRail.kt
 * Changes: account face comes from PojavAccountBridge; an About item is added for the Pojav
 * website / Discord / license entries that lived on Pojav's main menu.
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

package net.kdt.pojavlaunch.aerix.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixAccount
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.theme.AERIX_COMPACT_HEIGHT_THRESHOLD_DP
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

/** Primary destinations kept visible while the Aerix launcher is open. */
enum class LauncherSection {
    HOME,
    LIBRARY,
    DISCOVER,
    DOWNLOADS,
    CONTENT,
    FILES,
    WALLPAPERS,
    LOGS,
    SETTINGS,
    ACCOUNTS,
    ABOUT
}

@Composable
fun AerixNavigationRail(
    selectedSection: LauncherSection,
    onNavigate: (LauncherSection) -> Unit,
    onCreateInstance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val account = PojavAccountBridge.current
    val scrollState = rememberScrollState()
    val activeAccent = AerixThemeManager.currentAccent()
    val configuration = LocalConfiguration.current
    // Short screens (landscape phones): tighten the rail so every entry stays visible.
    val compactRail = configuration.screenHeightDp < AERIX_COMPACT_HEIGHT_THRESHOLD_DP
    val expandedRail = configuration.screenWidthDp >= 900 && !compactRail

    Row(modifier = modifier.fillMaxHeight()) {
        Surface(
            modifier = Modifier
                .width(if (expandedRail) AerixMetrics.expandedNavigationRailWidth else AerixMetrics.navigationRailWidth)
                .fillMaxHeight()
                .padding(horizontal = AerixSpacing.xs, vertical = AerixSpacing.smPlus)
                .liquidGlass(shape = CircleShape, tint = AerixSurface.glassTint, strength = 0.94f, elevation = AerixMetrics.glassRailElevation),
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(vertical = if (compactRail) AerixSpacing.xs else AerixSpacing.smPlus),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compactRail) AerixSpacing.xs else AerixSpacing.sm)
            ) {
                // Aerix monogram and wordmark establish a persistent brand anchor.
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AerixRadii.panelSmall))
                        .clickable(role = Role.Button) { onNavigate(LauncherSection.HOME) }
                        .semantics { contentDescription = "Aerix Home" }
                        .padding(vertical = AerixSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.xxs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (compactRail) 32.dp else 38.dp)
                            .clip(RoundedCornerShape(AerixRadii.control))
                            .background(activeAccent.copy(alpha = 0.12f))
                            .liquidGlass(
                                shape = RoundedCornerShape(AerixRadii.control),
                                tint = activeAccent,
                                strength = 0.82f,
                                elevation = AerixMetrics.glassSubtleElevation
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_aerix_mark),
                            contentDescription = "Aerix",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(if (compactRail) 20.dp else 24.dp)
                        )
                    }
                    if (expandedRail) {
                        Text(text = "AERIX", fontSize = 8.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.ExtraBold, color = AerixSurface.textSecondary)
                    }
                }

                Spacer(Modifier.height(if (compactRail) AerixSpacing.zero else AerixSpacing.xs))

                RailIconItem(R.drawable.aerix_ic_home_filled, stringResource(R.string.aerix_nav_home),
                    selectedSection == LauncherSection.HOME, activeAccent, expandedRail, compactRail) { onNavigate(LauncherSection.HOME) }
                RailIconItem(R.drawable.aerix_ic_add, stringResource(R.string.create_instance),
                    false, activeAccent, expandedRail, compactRail, onCreateInstance)
                RailIconItem(R.drawable.aerix_ic_dashboard_filled, stringResource(R.string.aerix_nav_library),
                    selectedSection == LauncherSection.LIBRARY, activeAccent, expandedRail, compactRail) { onNavigate(LauncherSection.LIBRARY) }
                RailIconItem(R.drawable.aerix_ic_public, stringResource(R.string.aerix_nav_discover),
                    selectedSection == LauncherSection.DISCOVER, activeAccent, expandedRail, compactRail) { onNavigate(LauncherSection.DISCOVER) }
                RailIconItem(R.drawable.aerix_ic_download, stringResource(R.string.aerix_nav_downloads),
                    selectedSection == LauncherSection.DOWNLOADS, activeAccent, expandedRail, compactRail) { onNavigate(LauncherSection.DOWNLOADS) }
                RailIconItem(R.drawable.aerix_ic_extension_outlined, stringResource(R.string.aerix_nav_content),
                    selectedSection == LauncherSection.CONTENT, activeAccent, expandedRail, compactRail) { onNavigate(LauncherSection.CONTENT) }
                RailIconItem(R.drawable.aerix_ic_folder_outlined, stringResource(R.string.aerix_nav_files),
                    selectedSection == LauncherSection.FILES, activeAccent, expandedRail, compactRail) { onNavigate(LauncherSection.FILES) }
                RailIconItem(R.drawable.aerix_ic_format_paint_outlined, stringResource(R.string.aerix_nav_wallpapers),
                    selectedSection == LauncherSection.WALLPAPERS, activeAccent, expandedRail, compactRail) { onNavigate(LauncherSection.WALLPAPERS) }

                HorizontalDivider(modifier = Modifier.width(28.dp).padding(vertical = AerixSpacing.xxs), color = AerixSurface.borderSoft)

                RailIconItem(R.drawable.aerix_ic_settings_filled, stringResource(R.string.aerix_nav_settings),
                    selectedSection == LauncherSection.SETTINGS, activeAccent, expandedRail, compactRail) { onNavigate(LauncherSection.SETTINGS) }
                RailIconItem(R.drawable.aerix_ic_terminal_outlined, stringResource(R.string.aerix_nav_logs),
                    selectedSection == LauncherSection.LOGS, activeAccent, expandedRail, compactRail) { onNavigate(LauncherSection.LOGS) }
                RailIconItem(R.drawable.aerix_ic_info_outlined, stringResource(R.string.aerix_nav_about),
                    selectedSection == LauncherSection.ABOUT, activeAccent, expandedRail, compactRail) { onNavigate(LauncherSection.ABOUT) }

                AccountAvatarRailButton(
                    account = account,
                    selected = selectedSection == LauncherSection.ACCOUNTS,
                    expanded = expandedRail,
                    compact = compactRail,
                    onClick = { onNavigate(LauncherSection.ACCOUNTS) }
                )
            }
        }
    }
}

@Composable
private fun RailIconItem(
    iconRes: Int,
    label: String,
    selected: Boolean,
    accentColor: Color,
    expanded: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    // The selected item uses the current theme accent; icon, fill and border update together.
    val selectedContentColor = MaterialTheme.colorScheme.onPrimary
    val containerColor by animateColorAsState(
        targetValue = if (selected) accentColor else AerixSurface.panel,
        animationSpec = tween(140),
        label = "railContainerColor"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) selectedContentColor else AerixSurface.textSecondary,
        animationSpec = tween(140),
        label = "railIconTint"
    )
    val itemWidth = when {
        expanded -> 64.dp
        compact -> 36.dp
        else -> 44.dp
    }
    val itemHeight = when {
        expanded -> 54.dp
        compact -> 36.dp
        else -> 42.dp
    }
    val itemShape = RoundedCornerShape(AerixRadii.control)
    Box(
        modifier = Modifier
            .size(width = itemWidth, height = itemHeight)
            .clip(itemShape)
            .background(containerColor)
            .border(BorderStroke(AerixSpacing.hairline, if (selected) accentColor else AerixSurface.borderSoft), itemShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (expanded) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(painter = painterResource(iconRes), contentDescription = label, tint = iconTint, modifier = Modifier.size(19.dp))
                Text(
                    text = label,
                    color = if (selected) iconTint else AerixSurface.textSecondary,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        } else {
            Icon(painter = painterResource(iconRes), contentDescription = label, tint = iconTint, modifier = Modifier.size(if (compact) 18.dp else 21.dp))
        }
    }
}

@Composable
private fun AccountAvatarRailButton(
    account: AerixAccount?,
    selected: Boolean,
    expanded: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val activeAccent = AerixThemeManager.currentAccent()
    val ringColor by animateColorAsState(
        targetValue = if (selected) activeAccent else AerixSurface.borderSoft,
        animationSpec = tween(150),
        label = "accountAvatarRing"
    )
    val accountsLabel = stringResource(R.string.aerix_nav_accounts)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(AerixRadii.panelSmall))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = account?.username ?: accountsLabel }
            .padding(AerixSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 32.dp else 36.dp)
                .clip(CircleShape)
                .background(AerixSurface.panel)
                .liquidGlass(shape = CircleShape, tint = activeAccent, strength = if (selected) 0.82f else 0.34f, elevation = AerixMetrics.glassSelectedElevation)
                .border(width = if (selected) 2.dp else AerixSpacing.hairline, color = ringColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AerixImageWell(
                image = account?.face,
                fallback = R.drawable.aerix_ic_person_outlined,
                size = if (compact) 26.dp else 30.dp,
                radius = 15.dp
            )
        }
        if (expanded) {
            Text(
                text = accountsLabel,
                color = if (selected) activeAccent else AerixSurface.textSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
