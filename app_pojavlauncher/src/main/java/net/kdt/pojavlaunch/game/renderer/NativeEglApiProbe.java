package net.kdt.pojavlaunch.game.renderer;

/**
 * Checks that the renderer loaded through MojoExec implements the EGL entry points SDL needs.
 */
public final class NativeEglApiProbe {
    static {
        System.loadLibrary("pojavexec");
    }

    private NativeEglApiProbe() {
    }

    public static native boolean hasRequiredEglApi();
}
