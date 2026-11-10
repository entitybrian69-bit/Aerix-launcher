package net.kdt.pojavlaunch.aerix.client
import android.content.Context
import java.io.File
/**
 * The two cosmetics entry points the skin server is allowed to call, recorded so the test can prove
 * the per-instance opt-out reaches the seam rather than merely compiling. Signatures have to stay in
 * step with the real objects; tests/trinkies_integration_smoke.py fails the build on arity drift.
 */
object AerixClientCosmetics {
 var skinBakes = 0
 var lastSkinAllowed: Boolean? = null
 var lastCapeAllowed: Boolean? = null
 fun applyWardrobeToSkin(
  context: Context, bytes: ByteArray?, slim: Boolean, enabledForInstance: Boolean = true
 ): ByteArray? {
  skinBakes++
  lastSkinAllowed = enabledForInstance
  // "Baked" is recorded as one extra byte, so a test could tell the two paths apart by content.
  return if (enabledForInstance && bytes != null) bytes + 0x2a.toByte() else bytes
 }
 fun resolveInGameCapeBytes(context: Context, enabledForInstance: Boolean = true): ByteArray? {
  lastCapeAllowed = enabledForInstance
  return null
 }
}
object TrinkiesInstaller {
 /** Same marker path and same "on unless the file exists" rule as the launcher's real reader. */
 fun isCosmeticsOptedOut(gameDirectory: File?): Boolean =
  gameDirectory?.let { File(it, "trinkies/disabled").isFile } == true
 fun isSupportedInstance(instance: net.kdt.pojavlaunch.instances.Instance?): Boolean =
  instance?.isAerixClient == true
}
