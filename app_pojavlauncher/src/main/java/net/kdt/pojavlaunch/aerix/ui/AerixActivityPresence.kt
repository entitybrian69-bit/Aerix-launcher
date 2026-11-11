/*
 * Aerix Launcher - telling Discord and the friends list what the player is doing.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package net.kdt.pojavlaunch.aerix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import net.kdt.pojavlaunch.aerix.social.SocialBackend

/**
 * Publish one line about what the player is doing while this screen is on top.
 *
 * The launcher used to report only the page it was on, so a friend's card read "Browsing the
 * dashboard" whether the player was picking a modpack, editing an instance or reading a project
 * page. Screens report through this instead of calling the backend directly: the line is cleared
 * only if it is still the one this screen set, which matters because the outgoing screen is disposed
 * after the incoming one has already published - clearing unconditionally would blank the new line.
 *
 * Nothing leaves the device when the player has activity sharing off; [SocialBackend] drops the
 * line before either destination is touched.
 */
@Composable
fun aerixLauncherActivity(line: String?) {
    val current = line?.trim()?.takeIf { it.isNotEmpty() }
    DisposableEffect(current) {
        if (current != null) SocialBackend.reportLauncherActivity(current)
        onDispose { if (current != null) SocialBackend.clearLauncherActivity(current) }
    }
}
