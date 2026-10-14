/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/MainScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/LauncherScreen.kt
 * The Navigation3 back stack is replaced by a section state + AnimatedContent; nested Pojav screens
 * (installers, instance editor, login, classic preferences) are pushed on the fragment back stack.
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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstaller
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.components.AerixBackground
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.screens.AboutScreen
import net.kdt.pojavlaunch.aerix.ui.screens.AccountsScreen
import net.kdt.pojavlaunch.aerix.ui.screens.DiscoverScreen
import net.kdt.pojavlaunch.aerix.ui.screens.HomeScreen
import net.kdt.pojavlaunch.aerix.ui.screens.LibraryScreen
import net.kdt.pojavlaunch.aerix.ui.screens.SettingsScreen
import net.kdt.pojavlaunch.aerix.ui.screens.WallpapersScreen

@Composable
fun AerixLauncherScreen(activity: FragmentActivity) {
    var section by rememberSaveable {
        mutableStateOf(runCatching { LauncherSection.valueOf(AerixPreferences.lastSection) }.getOrDefault(LauncherSection.HOME))
    }
    val navigate: (LauncherSection) -> Unit = { target ->
        section = target
        AerixPreferences.saveLastSection(target.name)
    }
    val duration = AerixPreferences.transitionMillis()

    AerixBackground {
        Row(modifier = Modifier.fillMaxSize()) {
            AerixNavigationRail(
                selectedSection = section,
                onNavigate = navigate,
                onCreateInstance = { PojavNavigator.openInstaller(activity, PojavInstaller.PROFILE_TYPE) }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                AnimatedContent(
                    targetState = section,
                    transitionSpec = {
                        if (duration == 0) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            (fadeIn(tween(duration)) + slideInVertically(tween(duration)) { it / 24 }) togetherWith
                                fadeOut(tween(duration / 2))
                        }
                    },
                    label = "aerixSection"
                ) { target ->
                    when (target) {
                        LauncherSection.HOME -> HomeScreen(activity, navigate)
                        LauncherSection.LIBRARY -> LibraryScreen(activity)
                        LauncherSection.DISCOVER -> DiscoverScreen(activity)
                        LauncherSection.WALLPAPERS -> WallpapersScreen()
                        LauncherSection.SETTINGS -> SettingsScreen(activity)
                        LauncherSection.ACCOUNTS -> AccountsScreen(activity)
                        LauncherSection.ABOUT -> AboutScreen(activity)
                    }
                }

                // Login progress bar, the Aerix replacement for the bar drawn under Pojav's AccountSpinner.
                val progress = PojavAccountBridge.loginProgress
                if (progress < 1f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = AerixSpacing.lg),
                        color = if (PojavAccountBridge.loginError) AerixSurface.danger else AerixThemeManager.currentAccent(),
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    }
}
