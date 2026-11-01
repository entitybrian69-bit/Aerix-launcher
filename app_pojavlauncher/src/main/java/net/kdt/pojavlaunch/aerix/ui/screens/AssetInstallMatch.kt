/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Decides which instance a downloaded file belongs to: the version and the loader the file was
 * built for, checked against what each instance actually runs.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.ui.screens

import net.kdt.pojavlaunch.aerix.bridge.AssetFile
import net.kdt.pojavlaunch.aerix.bridge.AssetTarget
import net.kdt.pojavlaunch.aerix.bridge.AssetType

/** The loaders a mod file can name, lowercase, as the platforms report them. */
private val MOD_LOADERS = setOf("fabric", "forge", "neoforge", "quilt", "liteloader", "rift")

/**
 * Whether a loader checkbox means anything for [type]. Resource packs, shaders and worlds run on
 * any instance, and their platform "loaders" are things like "iris" or "minecraft", which no
 * instance runs: only mods and modpacks are tied to a loader.
 */
internal fun loaderMatters(type: AssetType): Boolean =
    type.supportsLoaderFilter && type != AssetType.MODPACK

/** "1.16" in a file's version list covers every 1.16.x instance; anything else has to match. */
internal fun fileVersionMatches(fileVersion: String, instanceVersion: String?): Boolean {
    if (instanceVersion.isNullOrBlank()) return true
    val listed = fileVersion.trim()
    if (listed.isEmpty() || listed.equals("any", ignoreCase = true)) return true
    if (listed.equals(instanceVersion, ignoreCase = true)) return true
    // A two-part version ("1.16") stands for the whole 1.16.x line, not for "1.16" alone.
    return listed.count { it == '.' } == 1 && instanceVersion.startsWith("$listed.", ignoreCase = true)
}

/** What a file asks for, and whether [target] offers it. */
internal class TargetCheck(
    /** The version the badge should name, or null when the file covers the instance's version. */
    val wantedVersion: String?,
    /** The loader the badge should name, or null when the file runs on the instance's loader. */
    val wantedLoader: String?,
    /** The file covers the instance's Minecraft version and runs on its loader. */
    val fits: Boolean
)

/**
 * Check one instance against the file. Nothing is rejected on a guess: an instance whose version
 * or loader the launcher cannot read counts as a fit, and the row is left to the player.
 */
internal fun checkTarget(file: AssetFile, target: AssetTarget, loadersMatter: Boolean): TargetCheck {
    val versions = file.gameVersions.map { it.trim() }.filter { it.isNotEmpty() && !it.equals("any", true) }
    val versionMiss = target.mcVersion != null && versions.isNotEmpty() &&
        versions.none { fileVersionMatches(it, target.mcVersion) }

    val wanted = file.loaders.map { it.trim().lowercase() }.filter { it in MOD_LOADERS }
    val running = target.loader?.trim()?.lowercase()?.substringBefore('-')?.takeIf { it.isNotEmpty() }
    // Quilt runs Fabric mods; nothing else crosses over.
    val loaderMiss = loadersMatter && wanted.isNotEmpty() && running != null &&
        running !in wanted && !(running == "quilt" && "fabric" in wanted)

    val wantedVersion = if (versionMiss) {
        versions.firstOrNull()?.let { first ->
            if (versions.size > 1) "$first +${versions.size - 1}" else first
        }
    } else null
    val wantedLoader = if (loaderMiss) {
        wanted.firstOrNull()?.replaceFirstChar { it.uppercase() }
    } else null
    return TargetCheck(wantedVersion, wantedLoader, !versionMiss && !loaderMiss)
}

/**
 * The single instance the dialog ticks when it opens: the instance the launcher is sitting on when
 * the file fits it, otherwise the first instance the file does fit. Null when the file fits none of
 * them, in which case nothing is ticked and the player picks one deliberately.
 *
 * One tick, never all of them: a download lands in the instance the player is playing, and ticking
 * every instance installed on the device put files into worlds that could never load them.
 */
internal fun defaultInstallTarget(
    file: AssetFile,
    type: AssetType,
    targets: List<AssetTarget>
): AssetTarget? {
    if (targets.isEmpty()) return null
    val loadersMatter = loaderMatters(type)
    val fits = targets.filter { checkTarget(file, it, loadersMatter).fits }
    return fits.firstOrNull { it.detected } ?: fits.firstOrNull()
}
