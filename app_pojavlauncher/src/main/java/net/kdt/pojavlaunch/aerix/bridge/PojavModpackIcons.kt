/*
 * Aerix Launcher UI layer for PojavLauncher.
 *
 * A modpack instance wears the artwork its publisher uses for the pack - in the dashboard list and
 * in instance management, not the launcher's mark and not the loader's.
 *
 * Installing from Discover writes that artwork as the instance icon, but an instance can end up
 * without one: it was installed before the launcher recorded the pack's image, Discovery draws its
 * cards from its own memory cache so there is often no cached file to copy, or the install happened
 * while the image was already unreachable. This sweep fills those in from the pack's own project.
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
import android.util.Log
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.instances.DisplayInstance
import net.kdt.pojavlaunch.instances.Instance
import net.kdt.pojavlaunch.instances.InstanceIconProvider
import net.kdt.pojavlaunch.instances.InstanceRefreshEvents
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.modloaders.modpacks.api.CurseforgeApi
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters
import net.kdt.pojavlaunch.utils.DownloadUtils
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Gives modpack instances the artwork their publisher uses.
 *
 * The work happens on its own thread, one instance at a time, and only for instances that have no
 * artwork of their own - the instance list is never held up by it. When an icon lands the instance
 * refresh event is fired, which redraws the dashboard and instance management with it.
 */
object PojavModpackIcons {
    private const val TAG = "ModpackIcons"

    private const val SOURCE_MODRINTH = "modrinth"
    private const val SOURCE_CURSEFORGE = "curseforge"

    /** Recorded in [Instance.modpackSource] once a title has been looked up and matched nothing. */
    private const val SOURCE_NONE = "none"

    private val queue = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "modpack-icons").apply { isDaemon = true }
    }

    /** Instances already looked at in this run of the launcher; a failure is not retried in a loop. */
    private val attempted: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /**
     * Queues any instance that should be wearing a pack's logo but is not.
     *
     * @param instances the instance list as it was just read; only the identity is used here
     */
    fun sweep(context: Context, instances: List<DisplayInstance>) {
        if (instances.isEmpty()) return
        val appContext = context.applicationContext
        for (instance in instances) {
            // An instance with its own artwork already has what this would fetch.
            if (runCatching { instance.hasOwnIcon() }.getOrDefault(true)) continue
            val id = runCatching { instance.instanceId }.getOrNull()?.takeIf { it.isNotBlank() } ?: continue
            if (!attempted.add(id)) continue
            queue.execute {
                runCatching { repair(appContext, id) }
                    .onFailure { Log.w(TAG, "Could not restore the modpack icon of $id", it) }
            }
        }
    }

    private fun repair(context: Context, id: String) {
        val instance = runCatching { Instances.loadInstance(id) }.getOrNull() ?: return
        if (instance.hasOwnIcon()) return
        // Already looked up once and found not to be a pack: never ask again.
        if (SOURCE_NONE.equals(instance.modpackSource, ignoreCase = true) &&
            instance.modpackIconUrl.isNullOrBlank()
        ) return

        // An instance with no recorded pack is only worth searching for when it is built on a
        // modloader: a modpack always is, a hand-made vanilla instance never is.
        if (instance.modpackSource.isNullOrBlank() &&
            InstanceIconProvider.loaderIconFor(instance.versionId) == null
        ) {
            instance.modpackSource = SOURCE_NONE
            instance.maybeWrite()
            return
        }

        var iconUrl = instance.modpackIconUrl?.takeIf { it.isNotBlank() }
        if (iconUrl == null) {
            val project = findPack(context, instance) ?: return
            instance.modpackIconUrl = project.imageUrl
            instance.modpackProjectId = project.id
            instance.modpackSource =
                if (project.apiSource == Constants.SOURCE_MODRINTH) SOURCE_MODRINTH else SOURCE_CURSEFORGE
            instance.maybeWrite()
            iconUrl = project.imageUrl?.takeIf { it.isNotBlank() } ?: return
        }

        val bytes = download(iconUrl) ?: return
        if (!ModIconCache.writeInstanceIcon(instance, bytes)) return
        InstanceIconProvider.dropIcon(instance)
        InstanceRefreshEvents.notifyChanged()
    }

    /**
     * The pack an instance was built from.
     *
     * Installs made from Discover record the pack's image on the instance, so this only has to find
     * the pack for instances that predate that - their name is the pack's title, which the install
     * writes when it creates the instance. Only an exact title match counts: a wrong logo is worse
     * than none, and the launcher's own mark is not used as a stand-in.
     */
    private fun findPack(context: Context, instance: Instance): ModItem? {
        val title = instance.name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        exactTitle(ModrinthApi(), title)?.let { return it }
        curseForgeKey(context)?.let { key ->
            exactTitle(CurseforgeApi(key), title)?.let { return it }
        }
        // Remembered, so the next launch does not search for the same name again - but only for an
        // instance with no known pack behind it: a known pack whose project could not be reached
        // (offline, rate limited) is worth asking about again rather than writing off for good.
        if (instance.modpackSource.isNullOrBlank()) {
            instance.modpackSource = SOURCE_NONE
            instance.maybeWrite()
        }
        return null
    }

    private fun exactTitle(api: ModpackApi, title: String): ModItem? = runCatching {
        val filters = SearchFilters().apply {
            isModpack = true
            name = title
        }
        api.searchMod(filters)?.results?.firstOrNull {
            it.isModpack && it.title?.trim()?.equals(title, ignoreCase = true) == true
        }
    }.getOrNull()

    /** User key (Aerix "curseForgeApiKey") first, then the key baked into the build. */
    private fun curseForgeKey(context: Context): String? {
        val user = runCatching { AerixPreferences.curseForgeApiKey.trim() }.getOrDefault("")
        if (user.isNotEmpty() && user != "DUMMY") return user
        val built = runCatching { context.getString(R.string.curseforge_api_key).trim() }.getOrDefault("")
        return built.takeIf { it.isNotEmpty() && it != "DUMMY" }
    }

    private fun download(url: String): ByteArray? = runCatching {
        ByteArrayOutputStream().also { DownloadUtils.download(url, it) }.toByteArray()
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}
