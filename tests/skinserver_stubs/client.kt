package net.kdt.pojavlaunch.aerix.client
import android.content.Context
object AerixClientCosmetics {
 fun applyHatAccessoryToSkin(context: Context, bytes: ByteArray?, slim: Boolean) = bytes
 fun resolveInGameCapeBytes(context: Context): ByteArray? = null
}
object TrinkiesInstaller {
 fun isSupportedInstance(instance: net.kdt.pojavlaunch.instances.Instance?): Boolean =
  instance?.isAerixClient == true
}
