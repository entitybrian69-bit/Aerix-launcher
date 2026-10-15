/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Catalog of the renderers the launcher itself implements.
 *
 * The backend renderers live in net.kdt.pojavlaunch.game.renderer and are listed by
 * RendererCache.getCompatibleRenderers(), which only keeps the ones the current device can run.
 * This file holds the name, the short description and the Minecraft version range of each of
 * them, so the Aerix screens and the launcher agree on how a renderer is called.
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

package net.kdt.pojavlaunch.aerix.bridge

import net.kdt.pojavlaunch.game.renderer.def.Renderers

/** Description of a renderer implemented by the launcher. */
class AerixRendererInfo(
    val rendererId: String,
    val displayName: String,
    val summary: String? = null,
    val minVer: String? = null,
    val maxVer: String? = null
)

/**
 * Names, descriptions and Minecraft version ranges of the renderers shipped with the launcher.
 * The version ranges are the ones the launcher advertises in its own settings screen.
 */
object AerixRendererCatalog {
    private val renderers = listOf(
        AerixRendererInfo(
            Renderers.GL4ES_RENDERER,
            "GL4ES",
            "OpenGL ES 2 compatibility layer",
            maxVer = "1.21.4"
        ),
        AerixRendererInfo(
            Renderers.NGGL4ES_RENDERER,
            "Krypton Wrapper",
            "OpenGL ES 3.2 compatibility layer",
            minVer = "1.17",
            maxVer = "26.2"
        ),
        AerixRendererInfo(
            Renderers.LTW_RENDERER,
            "OpenLTW",
            "OpenGL 3.2 core wrapper on top of OpenGL ES 3",
            minVer = "1.17"
        ),
        AerixRendererInfo(
            Renderers.MOBILEGLUES_RENDERER,
            "MobileGlues",
            "OpenGL ES 3 driver with shader translation",
            minVer = "1.17"
        ),
        AerixRendererInfo(
            Renderers.SFPEW_RENDERER,
            "SFPEW",
            "Simple FPE wrapper running on top of MobileGlues"
        ),
        AerixRendererInfo(
            Renderers.ZINK_RENDERER,
            "Zink (Vulkan)",
            "Mesa3D Zink driver running on Vulkan"
        ),
        AerixRendererInfo(
            Renderers.LEGACYZINK_RENDERER,
            "Legacy Zink (Vulkan)",
            "Mesa3D Zink driver provided by the external Zink plugin"
        ),
        AerixRendererInfo(
            Renderers.FREEDRENO_RENDERER,
            "Freedreno (KGSL)",
            "Mesa3D Freedreno driver for Adreno GPUs"
        ),
        AerixRendererInfo(
            Renderers.MESA_RENDERER,
            "Mesa (DRM)",
            "Desktop Mesa3D on top of the Android DRM backend"
        ),
        AerixRendererInfo(
            Renderers.MESA_RENDERER_EXT,
            "Mesa (DRM, external)",
            "Desktop Mesa3D provided by the external Mesa plugin"
        )
    )

    private val byRendererId = renderers.associateBy { it.rendererId }

    /** Description of a renderer, or null when the renderer is unknown to the launcher. */
    fun info(rendererId: String): AerixRendererInfo? = byRendererId[rendererId]

    /** Name of a renderer, falling back to the name used by the launcher itself. */
    fun displayName(rendererId: String, fallback: String): String =
        byRendererId[rendererId]?.displayName ?: fallback
}
