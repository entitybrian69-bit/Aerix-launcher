package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.instances.Instance
import net.kdt.pojavlaunch.instances.Instances
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs the bundled Trinkies client bridge into every compatible Fabric-family game folder.
 * Vanilla and Forge/NeoForge instances are deliberately left untouched: putting a Fabric entrypoint
 * in those loaders would be misleading and could make an otherwise working instance fail to start.
 *
 * Existing instances are upgraded when Home opens; newly-created or imported instances are also
 * repaired immediately before launch. Cape files are synced for the selected game folder then.
 */
object TrinkiesInstaller {
    private const val TAG = "TrinkiesInstaller"
    private val scanRunning = AtomicBoolean(false)

    @JvmStatic
    fun isSupportedInstance(instance: Instance?): Boolean {
        if (instance == null) return false
        if (instance.isAerixClient) return true
        val id = instance.versionId.orEmpty().lowercase()
        val name = instance.name.orEmpty().lowercase()
        return id.contains("fabric") || id.contains("quilt") ||
            name.startsWith("fabric ") || name.startsWith("quilt ") ||
            name.startsWith("legacy fabric")
    }

    /**
     * Per-instance cosmetics opt-out, kept as a marker file inside the instance rather than a new
     * `Instance` field: `<game directory>/trinkies/disabled`. A file travels with the folder on
     * import, export and world-share without every reader of `instances.json` having to learn a new
     * name first, and it sits next to the `equipped.json` it makes the point of. Cosmetics are on
     * unless the marker exists.
     *
     * This turns off *cosmetics*, not the mod: an opted-out instance still gets the Trinkies JAR and
     * the launcher's own animated cape, because the player's client tweaks there are not what the
     * switch is about. What stops is the skin bake, the cape patch bake, and the wardrobe the mod
     * reads - which is emptied rather than left behind, so a running client unequips.
     */
    const val COSMETICS_OPT_OUT_MARKER = "trinkies/disabled"

    @JvmStatic
    fun isCosmeticsOptedOut(gameDirectory: File?): Boolean {
        val dir = gameDirectory ?: return false
        return runCatching { File(dir, COSMETICS_OPT_OUT_MARKER).isFile }.getOrDefault(false)
    }

    /**
     * Writes or clears the marker and brings the instance's own state in line immediately, so a game
     * that is already running follows the change on its next loadout poll instead of at the next
     * launch. Returns false when the marker could not be changed, which the caller reports plainly.
     */
    @JvmStatic
    fun setCosmeticsOptedOut(context: Context, instance: Instance?, optedOut: Boolean): Boolean =
        runCatching {
            val dir = instance?.gameDirectory ?: return false
            val marker = File(dir, COSMETICS_OPT_OUT_MARKER)
            val changed = if (optedOut) {
                if (marker.isFile) true
                else {
                    marker.parentFile?.mkdirs()
                    marker.createNewFile()
                }
            } else {
                !marker.exists() || marker.delete()
            }
            if (!changed) return false
            AerixClientCosmetics.syncCapeAssetsToInstance(context, dir)
            TrinkiesLoadout.writeForInstance(context, dir)
            true
        }.onFailure { error ->
            Log.w(TAG, "Could not change the per-instance Trinkies cosmetics state", error)
        }.getOrDefault(false)

    /** One local-only pass; safe to call whenever the Home screen is entered. */
    @JvmStatic
    fun installIntoSupportedInstances(context: Context) {
        if (!scanRunning.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        PojavApplication.sExecutorService.execute {
            try {
                val compatible = runCatching { Instances.loadAllInstances() }
                    .getOrDefault(emptyList())
                    .filter(::isSupportedInstance)
                val conflicts = mutableListOf<String>()
                compatible.forEach { instance ->
                    runCatching {
                        val gameDir = instance.gameDirectory ?: return@runCatching
                        val result = AerixClientInstaller.deployBundledTrinkies(appContext, File(gameDir, "mods"))
                        if (result.status == TrinkiesDeploymentStatus.CONFLICT || result.status == TrinkiesDeploymentStatus.FAILED) {
                            conflicts += "${instance.name}: ${result.detail}"
                            Log.w(TAG, "Trinkies not changed for ${instance.name}: ${result.detail}")
                        }
                    }.onFailure { error ->
                        conflicts += "${instance.name}: ${error.message ?: "install failed"}"
                        Log.w(TAG, "Could not update Trinkies for ${instance.name}", error)
                    }
                }
                if (conflicts.isNotEmpty()) {
                    val first = conflicts.first().take(120)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            appContext,
                            "Trinkies skipped for ${conflicts.size} instance(s) to protect existing files. First: $first",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                Log.i(TAG, "Checked ${compatible.size} Fabric-family instance(s) for Trinkies")
            } finally {
                scanRunning.set(false)
            }
        }
    }

    /** Fast local-only install before a supported game starts; no downloads or loader changes. */
    @JvmStatic
    fun syncLocalAssetsAtGameStart(context: Context, instance: Instance) {
        if (!isSupportedInstance(instance)) return
        runCatching {
            val gameDir = instance.gameDirectory?.apply { mkdirs() } ?: return
            val result = AerixClientInstaller.deployBundledTrinkies(context, File(gameDir, "mods"))
            if (result.status == TrinkiesDeploymentStatus.CONFLICT || result.status == TrinkiesDeploymentStatus.FAILED) {
                Log.w(TAG, "Trinkies not changed for ${instance.name}: ${result.detail}")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context.applicationContext,
                        "Trinkies was not changed for ${instance.name}; existing mods were protected. ${result.detail}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            AerixClientCosmetics.syncCapeAssetsToInstance(context, gameDir)
        }.onFailure { error ->
            Log.w(TAG, "Trinkies local sync failed for ${instance.name}; continuing launch", error)
        }
    }
}
