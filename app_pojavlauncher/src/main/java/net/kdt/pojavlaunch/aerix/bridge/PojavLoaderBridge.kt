package net.kdt.pojavlaunch.aerix.bridge

import androidx.annotation.DrawableRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.instances.InstanceLoaderUpdate
import net.kdt.pojavlaunch.instances.InstanceRefreshEvents
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.modloaders.ComparableVersionString
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils
import net.kdt.pojavlaunch.modloaders.ForgelikeUtils
import java.io.IOException

/**
 * The loaders the Aerix loader screen offers.
 *
 * Fabric, Quilt, NeoForge, Forge and Legacy Fabric are installed from here, through the same
 * utilities Pojav's own installer screens use. OptiFine and BTA keep their own screens: their
 * versions come from a scrape of a website and from the BTA version list, both of which are flows
 * of their own, so those entries open those pages instead.
 */
enum class LoaderKind(
    val title: String,
    @DrawableRes val iconRes: Int,
    val fabricLike: FabriclikeUtils?,
    val forgeLike: ForgelikeUtils?,
    private val ownInstaller: PojavInstaller?
) {
    FABRIC("Fabric", R.drawable.ic_fabric, FabriclikeUtils.FABRIC_UTILS, null, null),
    QUILT("Quilt", R.drawable.ic_quilt, FabriclikeUtils.QUILT_UTILS, null, null),
    NEOFORGE("NeoForge", R.drawable.ic_neoforge, null, ForgelikeUtils.NEOFORGE_UTILS, null),
    FORGE("Forge", R.drawable.ic_forge, null, ForgelikeUtils.FORGE_UTILS, null),
    LEGACY_FABRIC(
        "Legacy Fabric",
        R.drawable.aerix_loader_legacy_fabric,
        FabriclikeUtils.LEGACY_FABRIC_UTILS,
        null,
        null
    ),
    OPTIFINE("OptiFine", R.drawable.aerix_loader_optifine, null, null, PojavInstaller.OPTIFINE),
    BTA("BTA", R.drawable.aerix_loader_bta, null, null, PojavInstaller.BTA);

    /** True when this loader installs from this screen instead of its own page. */
    val installsHere: Boolean get() = ownInstaller == null

    val installer: PojavInstaller? get() = ownInstaller
}

/** One row of a version bench: what the player reads, and what an install is asked for. */
class LoaderVersion(val value: String, val label: String, val stable: Boolean)

/**
 * Version metadata and installation of the loader screen.
 *
 * Both kinds of loader are served by Pojav's own code: the Fabric-like ones (Fabric, Quilt, Legacy
 * Fabric) answer with a game version list and a list of loader builds per game version, the
 * Forge-like ones (Forge, NeoForge) answer with one flat list of full versions whose game version
 * is derived the same way Pojav's own screen derives it.
 */
object PojavLoaderBridge {

    /** Minecraft versions a loader has builds for. */
    suspend fun gameVersions(kind: LoaderKind): List<LoaderVersion> = withContext(Dispatchers.IO) {
        // Nothing thrown from here reaches the screen as a crash: an unreachable source is an
        // empty list, which the screen shows as "could not load, retry".
        runCatching {
            val fabricLike = kind.fabricLike
            if (fabricLike != null) {
                val versions = runCatching { fabricLike.downloadGameVersions() }.getOrNull()
                return@runCatching versions.orEmpty()
                    .map { LoaderVersion(it.version, it.version, it.stable) }
            }
            val forgeLike = kind.forgeLike ?: return@runCatching emptyList()
            val all = runCatching { forgeLike.downloadVersions() }.getOrNull().orEmpty()
            all.filterNot { forgeLike.shouldSkipVersion(it) }
                .map { forgeLike.processVersionString(it) }
                .distinct()
                .map { LoaderVersion(it, it, isRelease(it)) }
        }.getOrDefault(emptyList())
    }

    /** Builds of [kind] for [mcVersion], newest first. */
    suspend fun builds(kind: LoaderKind, mcVersion: String): List<LoaderVersion> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fabricLike = kind.fabricLike
                if (fabricLike != null) {
                    val versions = runCatching { fabricLike.downloadLoaderVersions(mcVersion) }.getOrNull()
                    return@runCatching versions.orEmpty()
                        .map { LoaderVersion(it.version, it.version, it.stable) }
                        .newestFirst()
                }
                val forgeLike = kind.forgeLike ?: return@runCatching emptyList()
                val all = runCatching { forgeLike.downloadVersions() }.getOrNull().orEmpty()
                all.filterNot { forgeLike.shouldSkipVersion(it) }
                    .filter { forgeLike.processVersionString(it) == mcVersion }
                    .map { full ->
                        // A Forge version is "<minecraft>-<forge>", NeoForge's is its own number.
                        val label = if (kind == LoaderKind.FORGE) full.substringAfter('-', full) else full
                        LoaderVersion(full, label, !full.contains("beta", true))
                    }
                    .newestFirst()
            }.getOrDefault(emptyList())
        }

    /** Resolve the base game, not the loader's own version number. Never silently choose latest. */
    suspend fun minecraftVersion(versionId: String, available: List<LoaderVersion>): String? =
        withContext(Dispatchers.IO) {
            LoaderMinecraftVersionResolver.resolve(versionId, available.map { it.value }.toSet()) { id ->
                runCatching { Tools.getVersionInfo(id, true)?.inheritsFrom }.getOrNull()
            }
        }

    /** Update the explicitly captured instance; selecting another profile cannot redirect the job. */
    suspend fun install(targetId: String, kind: LoaderKind, mcVersion: String, build: LoaderVersion): String =
        withContext(Dispatchers.IO) {
            val original = Instances.loadInstance(targetId)
            if (original.installer != null) throw IOException("Finish the pending loader installation first")
            val fabricLike = kind.fabricLike
            if (fabricLike != null) {
                val versionId = fabricLike.install(mcVersion, build.value)
                    ?: throw IOException("${kind.title} did not return a version id")
                val target = Instances.loadInstance(targetId)
                if (target.versionId != original.versionId || target.installer != null)
                    throw IOException("Instance changed during installation; please retry")
                InstanceLoaderUpdate.version(target, versionId)
                InstanceRefreshEvents.notifyChanged()
                return@withContext versionId
            }
            val forgeLike = kind.forgeLike ?: throw IOException("${kind.title} cannot install from here")
            val installer = forgeLike.createInstaller(build.value)
                ?: throw IOException("${kind.title} has no installer for ${build.value}")
            val target = Instances.loadInstance(targetId)
            if (target.versionId != original.versionId || target.installer != null)
                throw IOException("Instance changed during installation; please retry")
            InstanceLoaderUpdate.installer(target, installer)
            InstanceRefreshEvents.notifyChanged()
            // Existing postInstallCheck updates this same instance after the GUI installer finishes.
            installer.start()
            target.versionId.orEmpty()
        }

    /** A Minecraft release looks like 1.21.4; anything else is a snapshot or a pre-release. */
    private fun isRelease(version: String): Boolean =
        Regex("^\\d+\\.\\d+(\\.\\d+)?$").matches(version)

    /**
     * Newest first.
     *
     * The order has to be total: a comparator that answers 0 for two versions it cannot parse
     * while ordering them both against a third one makes TimSort throw "Comparison method violates
     * its general contract". The position in the source list is therefore the last word of every
     * tie, which keeps the order consistent, and the two comparisons above it are only used when
     * both sides can be compared at all.
     */
    private fun List<LoaderVersion>.newestFirst(): List<LoaderVersion> =
        mapIndexed { index, version -> version to index }
            .sortedWith(Comparator { left, right ->
                val a = ComparableVersionString.parse(left.first.label)
                val b = ComparableVersionString.parse(right.first.label)
                val byVersion = when {
                    a.isValid && b.isValid -> b.compareTo(a)
                    a.isValid -> -1
                    b.isValid -> 1
                    else -> 0
                }
                if (byVersion != 0) byVersion else left.second.compareTo(right.second)
            })
            .map { it.first }
}
