/*
 * Aerix Launcher home screen coordinator.
 * The home dashboard is implemented by MinecraftHomeDashboard; this file keeps its actions wired
 * to the existing Pojav account, instance, navigation and game-launch bridges.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavTaskBridge
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection

private class HomeActions(
    val play: () -> Unit,
    val create: () -> Unit,
    val socials: () -> Unit,
    val discover: () -> Unit,
    val library: () -> Unit,
    val accounts: () -> Unit
)

@Composable
fun HomeScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    LaunchedEffect(Unit) {
        PojavAccountBridge.ensureLoaded()
    }

    val instances = PojavInstanceBridge.instances
    val selected = PojavInstanceBridge.selected
    val taskCount = PojavTaskBridge.taskCount
    val accent = AerixThemeManager.currentAccent()
    val actions = HomeActions(
        play = { PojavLaunchBridge.play(activity) },
        create = { AerixNav.navigate(LauncherSection.CREATE_INSTANCE) },
        socials = { navigate(LauncherSection.SOCIALS) },
        discover = { navigate(LauncherSection.DISCOVER) },
        library = { navigate(LauncherSection.LIBRARY) },
        accounts = { navigate(LauncherSection.ACCOUNTS) }
    )

    MinecraftHomeDashboard(
        instances = instances,
        selected = selected,
        taskCount = taskCount,
        accent = accent,
        onPlay = actions.play,
        onCreate = actions.create,
        onLibrary = actions.library,
        onDiscover = actions.discover,
        onAccounts = actions.accounts,
        onSocials = actions.socials,
        onSettings = { navigate(LauncherSection.SETTINGS) },
        onNavigate = navigate,
        onHelp = { navigate(LauncherSection.ABOUT) }
    )
}
