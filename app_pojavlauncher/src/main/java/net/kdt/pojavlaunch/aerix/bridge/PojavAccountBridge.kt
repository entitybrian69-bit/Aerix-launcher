/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Adapter: Pojav accounts (net.kdt.pojavlaunch.authenticator.*) exposed to the Aerix Accounts / Home UI.
 * Takes over the login listeners that com.kdt.mcgui.AccountSpinner registered, with identical behaviour.
 * Replaces the UI-facing parts of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/game/account/AccountsManager.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/viewmodel/AccountViewModel.kt
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.authenticator.AuthType
import net.kdt.pojavlaunch.authenticator.accounts.Account
import net.kdt.pojavlaunch.authenticator.accounts.Accounts
import net.kdt.pojavlaunch.authenticator.listener.LoginListener
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.extra.ExtraListener
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import java.io.IOException

/** One row of the Aerix account list. [source] is the untouched Pojav Account. */
class AerixAccount(
    val source: Account,
    val username: String,
    val authType: AuthType,
    val expired: Boolean,
    val face: ImageBitmap?
)

object PojavAccountBridge : LoginListener {
    var accounts by mutableStateOf<List<AerixAccount>>(emptyList())
        private set
    var selectedIndex by mutableIntStateOf(-1)
        private set
    /** 0..1 login progress, shown as the bar AccountSpinner used to draw. */
    var loginProgress by mutableFloatStateOf(1f)
        private set
    var loginError by mutableStateOf(false)
        private set

    val current: AerixAccount?
        get() = accounts.getOrNull(selectedIndex)

    private var maxSteps = 5
    private var attached = false
    private var appContext: Context? = null

    private class LoginExtraListener(private val authType: AuthType) : ExtraListener<String> {
        override fun onValueSet(key: String, value: String): Boolean {
            loginError = false
            loginProgress = 0f
            authType.createAuth().createAccount(PojavAccountBridge, value)
            return false
        }
    }

    // All listeners are held strongly here: ExtraCore keeps only weak references.
    private val microsoftLoginListener = LoginExtraListener(AuthType.MICROSOFT)
    private val elyByLoginListener = LoginExtraListener(AuthType.ELY_BY)
    private val localLoginListener = ExtraListener<Array<String>> { _, value ->
        try {
            val account = Accounts.create { acc -> acc.username = value[0] }
            onLoginDone(account)
        } catch (e: IOException) {
            onLoginError(e)
        }
        false
    }
    private val refreshAccountsListener = ExtraListener<Boolean> { _, _ ->
        reload()
        false
    }

    fun attach(context: Context) {
        appContext = context.applicationContext
        if (attached) return
        attached = true
        ExtraCore.addExtraListener(ExtraConstants.MOJANG_LOGIN_TODO, localLoginListener)
        ExtraCore.addExtraListener(ExtraConstants.MICROSOFT_LOGIN_TODO, microsoftLoginListener)
        ExtraCore.addExtraListener(ExtraConstants.ELYBY_LOGIN_TODO, elyByLoginListener)
        ExtraCore.addExtraListener(ExtraConstants.REFRESH_ACCOUNT_SPINNER, refreshAccountsListener)
    }

    /** Same flow as AccountSpinner.reload(): load on the executor, then refresh the selected account. */
    fun reload() {
        PojavApplication.sExecutorService.execute {
            val loaded = runCatching { Accounts.load() }
            Tools.runOnUiThread {
                loaded.onSuccess { result ->
                    val now = System.currentTimeMillis()
                    accounts = result.accounts.map { account ->
                        AerixAccount(
                            source = account,
                            username = account.username ?: "",
                            authType = account.authType ?: AuthType.LOCAL,
                            expired = (account.authType ?: AuthType.LOCAL).requiresLogin() && now > account.expiresAt,
                            face = runCatching { account.skinFace?.asImageBitmap() }.getOrNull()
                        )
                    }
                    selectedIndex = if (result.accounts.isEmpty()) -1 else result.selectionIndex
                    result.accounts.getOrNull(result.selectionIndex)?.let { refreshAccount(it) }
                }.onFailure { Tools.showErrorRemote(it) }
            }
        }
    }

    private fun refreshAccount(account: Account) {
        // Wait until all tasks (including other login tasks) are done before refreshing, like AccountSpinner.
        ProgressKeeper.waitUntilDone {
            val refreshed = account.reload() ?: return@waitUntilDone
            val authType = refreshed.authType
            if (authType.requiresLogin() && System.currentTimeMillis() > refreshed.expiresAt) {
                authType.createAuth().refreshAccount(this, refreshed)
            }
        }
    }

    fun select(account: AerixAccount) {
        Accounts.setCurrent(account.source)
        selectedIndex = accounts.indexOf(account)
        refreshAccount(account.source)
    }

    fun forceRefresh(account: AerixAccount) {
        val authType = account.authType
        if (!authType.requiresLogin()) return
        loginError = false
        loginProgress = 0f
        ProgressKeeper.waitUntilDone {
            val refreshed = account.source.reload() ?: return@waitUntilDone
            refreshed.authType.createAuth().refreshAccount(this, refreshed)
        }
    }

    fun delete(account: AerixAccount) {
        Accounts.delete(account.source)
        reload()
    }

    /** Opens Pojav's SelectAuthFragment through the activity listener, as the spinner's "add" row did. */
    fun requestAddAccount() {
        ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true)
    }

    override fun onLoginDone(account: Account) {
        Tools.runOnUiThread {
            loginProgress = 1f
            loginError = false
            appContext?.let { Toast.makeText(it, R.string.main_login_done, Toast.LENGTH_SHORT).show() }
            Accounts.setCurrent(account)
            reload()
        }
    }

    override fun onLoginError(errorMessage: Throwable) {
        Tools.runOnUiThread {
            loginError = true
            loginProgress = 1f
        }
        Tools.showErrorRemote(errorMessage)
    }

    override fun onLoginProgress(step: Int) {
        Tools.runOnUiThread { loginProgress = (step.toFloat() / maxSteps.coerceAtLeast(1)).coerceIn(0f, 1f) }
    }

    override fun setMaxLoginProgress(max: Int) {
        maxSteps = max
    }
}
