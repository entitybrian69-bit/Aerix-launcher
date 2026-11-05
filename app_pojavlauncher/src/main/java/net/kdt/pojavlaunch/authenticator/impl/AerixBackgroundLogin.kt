package net.kdt.pojavlaunch.authenticator.impl

import kotlinx.coroutines.runBlocking
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.authenticator.AuthType
import net.kdt.pojavlaunch.authenticator.BackgroundLogin
import net.kdt.pojavlaunch.authenticator.accounts.Account
import net.kdt.pojavlaunch.authenticator.accounts.Accounts
import net.kdt.pojavlaunch.authenticator.listener.LoginListener
import java.io.IOException

/** Uses the current Aerix social login to issue a scoped, expiring game credential. */
class AerixBackgroundLogin : BackgroundLogin {
    companion object {
        @JvmField val CREATOR = BackgroundLogin.Creator { AerixBackgroundLogin() }
    }

    override fun createAccount(listener: LoginListener, code: String) {
        PojavApplication.sExecutorService.execute {
            try {
                val owner = SocialBackend.myUserId() ?: throw IOException("Sign in to Aerix first")
                val session = runBlocking { SocialBackend.issueGameSession(owner) }
                    ?: throw IOException(SocialBackend.lastError ?: "Could not start an Aerix game session")
                if (SocialBackend.myUserId() != owner) throw IOException("Aerix account changed")
                val existing = Accounts.load().accounts.firstOrNull { it.authType == AuthType.AERIX && it.aerixOwnerId == owner }
                val account = existing ?: Accounts.create { acc -> fill(acc, owner, session) }
                if (existing != null) { fill(account, owner, session); account.save() }
                Tools.runOnUiThread { listener.onLoginDone(account) }
            } catch (error: Exception) {
                Tools.runOnUiThread { listener.onLoginError(error) }
            }
        }
    }

    override fun refreshAccount(listener: LoginListener, account: Account) {
        PojavApplication.sExecutorService.execute {
            try {
                val owner = account.aerixOwnerId ?: throw IOException("Aerix account needs to be added again")
                if (SocialBackend.myUserId() != owner) throw IOException("Sign in to the Aerix account linked to this player")
                val session = runBlocking { SocialBackend.issueGameSession(owner) }
                    ?: throw IOException(SocialBackend.lastError ?: "Could not renew the Aerix game session")
                if (SocialBackend.myUserId() != owner || session.name != account.username ||
                    !session.profileId.equals(account.profileId, ignoreCase = true)) throw IOException("Aerix identity changed")
                fill(account, owner, session)
                account.save()
                Tools.runOnUiThread { listener.onLoginDone(account) }
            } catch (error: Exception) {
                Tools.runOnUiThread { listener.onLoginError(error) }
            }
        }
    }

    private fun fill(account: Account, owner: String, session: SocialBackend.GameSession) {
        account.authType = AuthType.AERIX
        account.aerixOwnerId = owner
        account.username = session.name
        account.profileId = session.profileId
        account.accessToken = session.token
        account.expiresAt = session.expiresAt
        account.refreshToken = "0"
        account.xuid = null
    }
}
