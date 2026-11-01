/*
 * Aerix Launcher UI layer for PojavLauncher.
 *
 * Branding for the renderer plugins this launcher knows by name: the mark shown beside the name
 * and the one-line description under it.
 *
 * A plugin APK can only carry what its manifest carries, so the nicest a raw plugin gets is its
 * own label and a puzzle icon. These entries are for the plugins that ship with Aerix, keyed by
 * package name because that is the one thing a plugin cannot change between builds.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.bridge

import androidx.annotation.DrawableRes
import net.kdt.pojavlaunch.R

/** What a known plugin looks like in the renderer list. */
data class RendererBrand(
    val name: String,
    val description: String,
    @DrawableRes val logoRes: Int
)

object AerixRendererBrands {

    /**
     * Known plugins, by package name. Anything not listed still works: it falls back to its own
     * label, its own icon and a plain "Installed renderer plugin" line.
     */
    private val brands: Map<String, RendererBrand> = mapOf(
        "com.entitybrian.emesis" to RendererBrand(
            name = "Emesis Renderer",
            description = "A super performative renderer only made to out match and out perform " +
                "other renderers supporting all devices and versions made by Entitybrian",
            logoRes = R.drawable.aerix_logo_emesis
        ),
        "np.proton.renderer.powervr" to RendererBrand(
            name = "Proton Renderer",
            description = "A high performance renderer for only Power vr gpus",
            logoRes = R.drawable.aerix_logo_proton
        )
    )

    /** Exact package match first, then a prefix match so a v2 package name still brands. */
    fun forPackage(packageName: String): RendererBrand? {
        brands[packageName]?.let { return it }
        val entry = brands.entries.firstOrNull { packageName.startsWith(it.key) } ?: return null
        return entry.value
    }

    /** The description for a package, or null to let the card keep its neutral wording. */
    fun descriptionFor(packageName: String): String? = forPackage(packageName)?.description

    /** The mark for a package, or null to fall back to the generic plugin icon. */
    @DrawableRes
    fun logoFor(packageName: String): Int? = forPackage(packageName)?.logoRes

    /** The display name for a package, or null to keep the plugin's own label. */
    fun nameFor(packageName: String): String? = forPackage(packageName)?.name
}
