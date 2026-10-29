/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/MainScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/LauncherScreen.kt
 * The Navigation3 back stack is replaced by a section state + AnimatedContent; nested Pojav screens
 * (installers, login, gamepad remapper) are pushed on the fragment back stack.
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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.AerixTransitionType
import net.kdt.pojavlaunch.aerix.bridge.AerixWindowInsets
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceEditorBridge
import net.kdt.pojavlaunch.aerix.components.AerixBackground
import net.kdt.pojavlaunch.aerix.discord.AerixDiscordPresence
import net.kdt.pojavlaunch.aerix.festivals.FestivalEffectsHost
import net.kdt.pojavlaunch.aerix.festivals.FestivalTitleText
import net.kdt.pojavlaunch.aerix.festivals.rememberTodayFestivals
import net.kdt.pojavlaunch.aerix.guide.AerixGuideOverlay
import net.kdt.pojavlaunch.aerix.guide.AerixGuideState
import net.kdt.pojavlaunch.aerix.guide.GuideRegistry
import net.kdt.pojavlaunch.aerix.guide.LocalGuideRegistry
import net.kdt.pojavlaunch.aerix.guide.rememberGuideSteps
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.screens.AboutScreen
import net.kdt.pojavlaunch.aerix.ui.screens.AccountsScreen
import net.kdt.pojavlaunch.aerix.ui.screens.AddAccountScreen
import net.kdt.pojavlaunch.aerix.ui.screens.AerixContribution
import net.kdt.pojavlaunch.aerix.ui.screens.ContributionDetailScreen
import net.kdt.pojavlaunch.aerix.ui.screens.ContributionScreen
import net.kdt.pojavlaunch.aerix.ui.screens.CreateInstanceScreen
import net.kdt.pojavlaunch.aerix.ui.screens.DiscoverScreen
import net.kdt.pojavlaunch.aerix.ui.screens.FileManagerScreen
import net.kdt.pojavlaunch.aerix.ui.screens.HomeScreen
import net.kdt.pojavlaunch.aerix.ui.screens.InstanceEditorScreen
import net.kdt.pojavlaunch.aerix.ui.screens.LibraryScreen
import net.kdt.pojavlaunch.aerix.bridge.AerixInstallQueue
import net.kdt.pojavlaunch.aerix.ui.screens.AerixInstallPanel
import net.kdt.pojavlaunch.aerix.ui.screens.AerixInstallPill
import net.kdt.pojavlaunch.aerix.ui.screens.LogScreen
import net.kdt.pojavlaunch.aerix.ui.screens.OfflineAccountScreen
import net.kdt.pojavlaunch.aerix.ui.screens.SettingsScreen
import net.kdt.pojavlaunch.aerix.ui.screens.SocialsScreen
import net.kdt.pojavlaunch.aerix.ui.screens.WallpapersScreen

@Composable
fun AerixLauncherScreen(activity: FragmentActivity) {
    AerixNav.init()
    net.kdt.pojavlaunch.aerix.ui.screens.LauncherUpdateNoticeHost(activity)
    val section = AerixNav.section
    val navigate: (LauncherSection) -> Unit = { target ->
        when (target) {
            // The Wallpapers rail entry is the Wallpapers tab of the settings page, as in mirai's rail.
            LauncherSection.WALLPAPERS -> {
                AerixNav.settingsTab = AerixNav.TAB_WALLPAPERS
                AerixNav.navigate(LauncherSection.SETTINGS)
            }
            LauncherSection.SETTINGS -> {
                if (AerixNav.settingsTab == AerixNav.TAB_WALLPAPERS) AerixNav.settingsTab = AerixNav.TAB_RENDERER
                AerixNav.navigate(LauncherSection.SETTINGS)
            }
            LauncherSection.ABOUT -> {
                AerixNav.settingsTab = AerixNav.TAB_ABOUT
                AerixNav.navigate(LauncherSection.SETTINGS)
            }
            // Opening the rail entry always lands on the team list, never on someone's page.
            LauncherSection.CONTRIBUTION -> {
                AerixContribution.openedId = null
                AerixNav.navigate(LauncherSection.CONTRIBUTION)
            }
            LauncherSection.DOWNLOADS -> AerixNav.navigate(LauncherSection.DISCOVER)
            LauncherSection.CONTENT -> AerixNav.navigate(LauncherSection.INSTANCE_EDITOR)
            else -> AerixNav.navigate(target)
        }
    }
    val duration = AerixPreferences.transitionMillis()
    val transitionType = AerixPreferences.swapAnimateType
    val extent = AerixPreferences.extentScale()
    val festivals = rememberTodayFestivals()
    // The first-run tour: components register where they are, the overlay highlights them.
    val guideRegistry = remember { GuideRegistry() }
    val guideSteps = rememberGuideSteps()
    LaunchedEffect(Unit) {
        AerixGuideState.startOnFirstRun()
        // The steps highlight things on the home page, so the tour brings you there rather than
        // starting on whichever section the launcher was left on.
        if (AerixGuideState.running && AerixNav.section != LauncherSection.HOME) {
            AerixNav.navigate(LauncherSection.HOME)
        }
    }

    // Discord Rich Presence: the launcher is on screen, and it says which page you are on.
    // attach() is a no-op when the setting is off, no application id is set, or Discord is absent.
    DisposableEffect(Unit) {
        AerixDiscordPresence.attach(activity)
        onDispose { AerixDiscordPresence.detach() }
    }
    LaunchedEffect(section) {
        AerixDiscordPresence.inLauncher(section.name)
    }

    BackHandler(enabled = AerixNav.canGoBack) { AerixNav.back() }

    CompositionLocalProvider(LocalGuideRegistry provides guideRegistry) {
    AerixBackground {
      FestivalEffectsHost(festivals = festivals, modifier = Modifier.fillMaxSize()) {
        // The wallpaper and the festival effects run to the edges of the display; the shell
        // itself stays clear of the camera cutout and of the system bars.
        Box(modifier = Modifier.fillMaxSize().padding(AerixWindowInsets.padding())) {
            Row(modifier = Modifier.fillMaxSize()) {
                AerixNavigationRail(
                    selectedSection = section,
                    onNavigate = navigate,
                    onCreateInstance = { AerixNav.navigate(LauncherSection.CREATE_INSTANCE) }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AnimatedContent(
                        targetState = section,
                        transitionSpec = { sectionTransition(transitionType, duration, extent) },
                        label = "aerixSection"
                    ) { target ->
                        when (target) {
                            LauncherSection.OTHER_RELEASES -> net.kdt.pojavlaunch.aerix.ui.screens.OtherReleasesScreen(activity, navigate)
                            LauncherSection.HOME -> HomeScreen(activity, navigate)
                            LauncherSection.LIBRARY -> LibraryScreen(activity)
                            LauncherSection.SOCIALS -> SocialsScreen(activity)
                            LauncherSection.DISCOVER, LauncherSection.DOWNLOADS -> DiscoverScreen(activity, navigate)
                            LauncherSection.FILES -> FileManagerScreen(activity)
                            LauncherSection.LOGS -> LogScreen(activity)
                            LauncherSection.WALLPAPERS -> WallpapersScreen()
                            LauncherSection.SETTINGS -> SettingsScreen(
                                activity = activity,
                                tabKey = AerixNav.settingsTab,
                                onTabChanged = { AerixNav.settingsTab = it }
                            )
                            LauncherSection.CONTRIBUTION -> ContributionScreen(onOpen = { member ->
                                AerixContribution.openedId = member.id
                                AerixNav.navigate(LauncherSection.CONTRIBUTION_DETAIL)
                            })
                            LauncherSection.CONTRIBUTION_DETAIL -> {
                                val member = AerixContribution.byId(AerixContribution.openedId)
                                if (member == null) {
                                    ContributionScreen(onOpen = { opened -> AerixContribution.openedId = opened.id })
                                } else {
                                    ContributionDetailScreen(member = member, onBack = { AerixNav.back() })
                                }
                            }
                            LauncherSection.ACCOUNTS -> AccountsScreen(activity)
                            LauncherSection.ABOUT -> AboutScreen(activity)
                            LauncherSection.CREATE_INSTANCE -> CreateInstanceScreen(activity) {
                                AerixNav.finishTo(LauncherSection.LIBRARY)
                            }
                            LauncherSection.INSTANCE_EDITOR, LauncherSection.CONTENT -> InstanceEditorScreen(activity) {
                                PojavInstanceEditorBridge.close()
                                AerixNav.finishTo(LauncherSection.LIBRARY)
                            }
                            LauncherSection.ADD_ACCOUNT -> AddAccountScreen(
                                activity = activity,
                                onClose = { AerixNav.finishTo(LauncherSection.ACCOUNTS) },
                                onOffline = { AerixNav.navigate(LauncherSection.OFFLINE_ACCOUNT) }
                            )
                            LauncherSection.OFFLINE_ACCOUNT -> OfflineAccountScreen(
                                activity = activity,
                                onClose = { AerixNav.navigate(LauncherSection.ADD_ACCOUNT) },
                                onCreated = { AerixNav.finishTo(LauncherSection.ACCOUNTS) }
                            )
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

            FestivalTitleText(
                festivals = festivals,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = AerixSpacing.xs, end = AerixSpacing.lg)
            )

            // Installs outlive the page that started them, so their two surfaces live here: the
            // panel with the progress bars, and the pill it collapses into when minimized.
            if (AerixInstallQueue.visible) {
                if (AerixInstallQueue.panelOpen) {
                    AerixInstallPanel(onMinimize = { AerixInstallQueue.minimizePanel() })
                } else {
                    AerixInstallPill(
                        onExpand = { AerixInstallQueue.openPanel() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(AerixSpacing.lg)
                    )
                }
            }

            // Drawn last so it sits above the rail, the pages and the install panel. Only on the
            // home page: the steps highlight things that live there.
            if (AerixGuideState.running && section == LauncherSection.HOME) {
                key(AerixGuideState.runId) {
                    AerixGuideOverlay(
                        steps = guideSteps,
                        onFinished = { AerixGuideState.finish() }
                    )
                }
            }
        }
      }
    }
    }
}

/**
 * Section transition for the Aerix "launcherSwapAnimateType" setting
 * (mirai utils/animation/TransitionAnimationType.kt, ui/screens/_Navigation.kt):
 * CLOSE snaps, JELLY_BOUNCE scales in on a low-stiffness spring, BOUNCE rises on a bouncy spring,
 * SLICE_IN slides in from the side. "launcherAnimateExtent" scales the travel distance.
 */
private fun AnimatedContentTransitionScope<LauncherSection>.sectionTransition(
    type: AerixTransitionType,
    duration: Int,
    extent: Float
): ContentTransform {
    if (duration == 0 || type == AerixTransitionType.CLOSE) {
        return fadeIn(snap()) togetherWith fadeOut(snap())
    }
    val exit = fadeOut(tween((duration * 0.75f).toInt().coerceAtLeast(80), easing = FastOutSlowInEasing))
    val forward = targetState.ordinal >= initialState.ordinal
    return when (type) {
        AerixTransitionType.JELLY_BOUNCE -> (fadeIn(tween(duration)) +
            scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                initialScale = 1f - 0.08f * extent)) togetherWith exit
        AerixTransitionType.BOUNCE -> (fadeIn(tween(duration)) +
            slideInVertically(spring<IntOffset>(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow)) {
                (it / 10 * extent).toInt()
            }) togetherWith exit
        AerixTransitionType.SLICE_IN -> (fadeIn(tween(duration)) +
            slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) {
                val distance = (it / 6 * extent).toInt()
                if (forward) distance else -distance
            }) togetherWith exit
        AerixTransitionType.CLOSE -> fadeIn(snap()) togetherWith fadeOut(snap())
    }
}
