package net.kdt.pojavlaunch.game.renderer;

import net.kdt.pojavlaunch.game.renderer.def.Renderers;

/**
 * Selects a conservative OpenGL renderer for the selected Minecraft version.
 */
public final class RendererAutoSelector {
    private RendererAutoSelector() {
    }

    /**
     * Choose an OpenGL renderer without relying on the presence of a Vulkan loader.
     *
     * @param gl4esCompatible whether the selected game version can use GL4ES
     * @param glesMajorVersion the detected GLES major version
     * @param ltwAvailable whether LTW is installed and compatible on this device
     * @return the renderer id, or {@code null} when no compatible route is available
     */
    public static String select(boolean gl4esCompatible, int glesMajorVersion, boolean ltwAvailable) {
        if (gl4esCompatible) return Renderers.GL4ES_RENDERER;
        if (glesMajorVersion >= 3 && ltwAvailable) return Renderers.LTW_RENDERER;
        return null;
    }
}
