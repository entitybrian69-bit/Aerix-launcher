/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Root fragment of LauncherActivity's R.id.container_fragment. Hosts the Aerix Compose UI in place of
 * Pojav's MainMenuFragment and wires the adapters to Pojav's backend singletons.
 * Plays the role of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/activities/MainActivity.kt (setContent host)
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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavSettingsBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavTaskBridge
import net.kdt.pojavlaunch.aerix.theme.AerixLauncherTheme
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore

class AerixMainFragment : Fragment() {
    companion object {
        const val TAG = "AerixMainFragment"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        AerixPreferences.init(context)
        PojavTaskBridge.attach()
        PojavInstanceBridge.attach(context)
        PojavAccountBridge.attach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val activity = requireActivity()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AerixLauncherTheme {
                    AerixLauncherScreen(activity)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PojavInstanceBridge.reload()
        PojavSettingsBridge.touch()
        // Same signal MainMenuFragment.onResume raised for AccountSpinner.
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true)
    }
}
