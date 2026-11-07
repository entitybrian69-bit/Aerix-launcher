package net.kdt.pojavlaunch.aerix.bridge
import android.content.Context
import net.kdt.pojavlaunch.authenticator.accounts.Account
import java.io.File
import java.util.UUID
object PojavAccountBridge {
 fun isSlimModelFor(context: Context, account: Account) = false
 fun customSkinFile(account: Account) = File("/missing")
 fun customCapeFile(account: Account) = File("/missing")
 fun normalizeSkinBytesForGame(bytes: ByteArray): ByteArray? = bytes
 fun effectiveProfileId(account: Account) = offlineUuid(account.username ?: "Steve").toString()
 fun offlineUuid(name: String): UUID = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray())
}
