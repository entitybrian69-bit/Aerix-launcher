/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: every navigation from the Aerix Compose UI into a PojavLauncher screen goes through here.
 * Pojav screens are pushed with Tools.swapFragment onto R.id.container_fragment, so Pojav's back
 * stack handling (and installers' popBackStackImmediate on completion) returns to the Aerix UI.
 * Replaces the Navigation3 destinations of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/NavigationKeys.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/MainScreen.kt
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
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.CustomControlsActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.fragments.BTAInstallFragment
import net.kdt.pojavlaunch.fragments.FabricInstallFragment
import net.kdt.pojavlaunch.fragments.ForgeInstallFragment
import net.kdt.pojavlaunch.fragments.GamepadMapperFragment
import net.kdt.pojavlaunch.fragments.LegacyFabricInstallFragment
import net.kdt.pojavlaunch.fragments.MainMenuFragment
import net.kdt.pojavlaunch.fragments.NeoforgeInstallFragment
import net.kdt.pojavlaunch.fragments.OptiFineInstallFragment
import net.kdt.pojavlaunch.fragments.ProfileTypeSelectFragment
import net.kdt.pojavlaunch.fragments.QuiltInstallFragment
import net.kdt.pojavlaunch.fragments.SearchModFragment
import net.kdt.pojavlaunch.LauncherActivity
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper

/** Installer / content entries listed on the Aerix Discover page, each owned by a Pojav fragment. */
enum class PojavInstaller(val fragment: Class<out Fragment>, val tag: String) {
    OPTIFINE(OptiFineInstallFragment::class.java, OptiFineInstallFragment.TAG),
    FABRIC(FabricInstallFragment::class.java, FabricInstallFragment.TAG),
    FORGE(ForgeInstallFragment::class.java, ForgeInstallFragment.TAG),
    NEOFORGE(NeoforgeInstallFragment::class.java, NeoforgeInstallFragment.TAG),
    QUILT(QuiltInstallFragment::class.java, QuiltInstallFragment.TAG),
    LEGACY_FABRIC(LegacyFabricInstallFragment::class.java, LegacyFabricInstallFragment.TAG),
    BTA(BTAInstallFragment::class.java, BTAInstallFragment.TAG),
    MODPACK(SearchModFragment::class.java, SearchModFragment.TAG),
    PROFILE_TYPE(ProfileTypeSelectFragment::class.java, ProfileTypeSelectFragment.TAG)
}

object PojavNavigator {
    fun openInstaller(activity: FragmentActivity, installer: PojavInstaller, targetId: String? = null) {
        val arguments = targetId?.let { id -> android.os.Bundle().apply {
            putString(net.kdt.pojavlaunch.instances.InstanceLoaderUpdate.TARGET_ARGUMENT, id)
        } }
        Tools.swapFragment(activity, installer.fragment, installer.tag, arguments)
    }

    fun openCustomControls(context: Context) {
        context.startActivity(Intent(context, CustomControlsActivity::class.java))
    }

    fun openGamepadMapper(activity: FragmentActivity) {
        Tools.swapFragment(activity, GamepadMapperFragment::class.java, GamepadMapperFragment.TAG, null)
    }

    /** Pojav's original home screen (version spinner + news/discord/controls/jar/log buttons). */
    fun openClassicHome(activity: FragmentActivity) {
        Tools.swapFragment(activity, MainMenuFragment::class.java, MainMenuFragment.TAG, null)
    }

    fun openClassicSettings(activity: FragmentActivity) {
        Tools.swapFragment(activity, PojavSettingsBridge.classicRoot, LauncherActivity.SETTING_FRAGMENT_TAG, null)
    }

    fun shareLogs(context: Context) {
        Tools.shareLog(context)
    }

    fun openUrl(activity: FragmentActivity, url: String) {
        Tools.openURL(activity, url)
    }

    fun openWebsite(activity: FragmentActivity) = openUrl(activity, Tools.URL_HOME)

    const val DISCORD_INVITE = "https://discord.gg/RS7q9KaCm6"

    fun openDiscord(activity: FragmentActivity) = openUrl(activity, DISCORD_INVITE)

    /** Guard used before the .jar picker, same as MainMenuFragment.runInstallerWithConfirmation. */
    fun canRunJar(context: Context): Boolean {
        if (ProgressKeeper.getTaskCount() == 0) return true
        Toast.makeText(context, R.string.tasks_ongoing, Toast.LENGTH_LONG).show()
        return false
    }

    fun runJar(context: Context, uri: Uri) {
        Tools.launchModInstaller(context, uri)
    }
}
