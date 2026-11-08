package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import android.util.Log
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
                compatible.forEach { instance ->
                    runCatching {
                        val gameDir = instance.gameDirectory ?: return@runCatching
                        AerixClientInstaller.deployBundledTrinkies(appContext, File(gameDir, "mods"))
                    }.onFailure { error ->
                        Log.w(TAG, "Could not update Trinkies for ${instance.name}", error)
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
            AerixClientInstaller.deployBundledTrinkies(context, File(gameDir, "mods"))
            AerixClientCosmetics.syncCapeAssetsToInstance(context, gameDir)
        }.onFailure { error ->
            Log.w(TAG, "Trinkies local sync failed for ${instance.name}; continuing launch", error)
        }
    }
}
