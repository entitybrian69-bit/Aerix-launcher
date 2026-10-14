/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter for the account pages. The Aerix "Add Account" page opens the PojavLauncher login fragments
 * directly (the same fragments SelectAuthFragment opened, with SelectAuthFragment's own
 * ProgressKeeper.AUTHENTICATE guard), and creates offline accounts through the same
 * ExtraConstants.MOJANG_LOGIN_TODO value LocalLoginFragment raised.
 * Replaces the account entries of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/settings/AccountManagerScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/account/LoginMethodScreen.kt
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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.kdt.mcgui.ProgressLayout
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.authenticator.accounts.Accounts
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.fragments.ElyByLoginFragment
import net.kdt.pojavlaunch.fragments.LocalLoginFragment
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment
import net.kdt.pojavlaunch.fragments.SelectAuthFragment
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper

/** The three sign-in methods of SelectAuthFragment, each owned by its PojavLauncher fragment. */
enum class PojavAuth(val fragment: Class<out Fragment>, val tag: String) {
    MICROSOFT(MicrosoftLoginFragment::class.java, MicrosoftLoginFragment.TAG),
    ELYBY(ElyByLoginFragment::class.java, ElyByLoginFragment.TAG),
    OFFLINE(LocalLoginFragment::class.java, LocalLoginFragment.TAG)
}

object PojavAuthBridge {
    /** Same guard and the same swap SelectAuthFragment.launchAuthFragment performed. */
    fun openLogin(activity: FragmentActivity, auth: PojavAuth) {
        if (ProgressKeeper.hasProgressKey(ProgressLayout.AUTHENTICATE)) {
            Toast.makeText(activity, R.string.tasks_ongoing, Toast.LENGTH_SHORT).show()
            return
        }
        Tools.swapFragment(activity, auth.fragment, auth.tag, null)
    }

    /** Pojav's own method chooser, kept reachable from the Aerix Add Account page. */
    fun openClassicChooser(activity: FragmentActivity) {
        if (ProgressKeeper.hasProgressKey(ProgressLayout.AUTHENTICATE)) {
            Toast.makeText(activity, R.string.tasks_ongoing, Toast.LENGTH_SHORT).show()
            return
        }
        Tools.swapFragment(activity, SelectAuthFragment::class.java, SelectAuthFragment.TAG, null)
    }

    /**
     * Creates a local account. Uses the pattern LocalLoginFragment.checkEditText applies
     * ("^[a-zA-Z0-9_]*$", 3..16 characters) and the license reminder it shows for first-time users.
     * @return true when the name is valid and the account was requested
     */
    fun createOfflineAccount(context: Context, name: String): Boolean {
        if (!isValidOfflineName(name)) return false
        if (Accounts.getCurrent() == null) Tools.maybeShowLicenseNag(context)
        ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, arrayOf(name, ""))
        return true
    }

    private val offlineNamePattern = Regex("^[a-zA-Z0-9_]*$")

    fun isValidOfflineName(name: String): Boolean =
        name.length in 3..16 && offlineNamePattern.matches(name)
}
