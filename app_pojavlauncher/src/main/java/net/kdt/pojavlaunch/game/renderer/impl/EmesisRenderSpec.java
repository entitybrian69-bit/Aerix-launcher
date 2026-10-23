package net.kdt.pojavlaunch.game.renderer.impl;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.game.renderer.RenderSpec;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;

import java.io.File;
import java.util.Map;

import git.artdeell.mojoexec.MojoExec;

/**
 * Emesis renderer, bundled with the launcher for every ABI it ships.
 *
 * A single library, {@code libemesis_renderer.so}, provides both the EGL implementation and the
 * desktop OpenGL API, and picks its own core at runtime: a gl4es-based core for old game versions
 * and a Mesa/gallium core for modern ones. The libraries it loads are packaged next to it in the
 * app's native library directory, which is where it resolves them from.
 *
 * The values below are exactly what the standalone Emesis plugin APK declares for a host, so the
 * bundled renderer behaves the same as the plugin it replaces: an ES context of the same major
 * version, the same wrapper variables, and the renderer library handed to LWJGL as OpenGL.
 */
public class EmesisRenderSpec implements RenderSpec {
    /** The renderer: EGL provider, OpenGL provider and core dispatcher in one library. */
    public static final String LIBRARY = "libemesis_renderer.so";
    /** Cores the renderer loads itself; packaged so they are always next to it. */
    public static final String[] CORES = {"libemesis_legacy.so", "libemesis_modern.so"};
    /** ES version the plugin asks its host for; the renderer exposes desktop GL 3.2+ on top. */
    private static final int GLES_MAJOR = 3;

    /** True when every library this renderer needs is packaged and unpacked for this process. */
    public static boolean available() {
        if (!new File(Tools.NATIVE_LIB_DIR, LIBRARY).isFile()) return false;
        for (String core : CORES) {
            if (!new File(Tools.NATIVE_LIB_DIR, core).isFile()) return false;
        }
        return true;
    }

    @Override
    public boolean compatibleDevice(Context context) {
        return available();
    }

    @Override
    public String name() {
        return "Emesis";
    }

    @Override
    public int displayName() {
        return R.string.mcl_setting_renderer_emesis;
    }

    @Override
    public String tag() {
        return Renderers.EMESIS_RENDERER;
    }

    /** The renderer brings its own EGL, so the host must not create a context from the system one. */
    @Override
    public String library() {
        File library = new File(Tools.NATIVE_LIB_DIR, LIBRARY);
        return library.isFile() ? library.getAbsolutePath() : LIBRARY;
    }

    /** LWJGL binds OpenGL to the same library: it exports the GL entry points too. */
    @Override
    public String glLibraryPath() {
        File library = new File(Tools.NATIVE_LIB_DIR, LIBRARY);
        return library.isFile() ? library.getAbsolutePath() : LIBRARY;
    }

    @Override
    public void setupEnvironment(Context context, Map<String, String> envMap) {
        // The plugin's own pojavEnv block, verbatim: these are how its wrapper is configured.
        envMap.put("POJAV_RENDERER", "opengles3");
        envMap.put("POJAVEXEC_EGL", LIBRARY);
        envMap.put("LIBGL_USE_MC_COLOR", "1");
        envMap.put("LIBGL_GL", "31");
        envMap.put("LIBGL_ES", String.valueOf(GLES_MAJOR));
        envMap.put("LIBGL_NORMALIZE", "1");
        envMap.put("LIBGL_NOINTOVLHACK", "1");
        envMap.put("LIBGL_NOERROR", "1");
        File library = new File(Tools.NATIVE_LIB_DIR, LIBRARY);
        if (library.isFile()) {
            String path = library.getAbsolutePath();
            envMap.put("SDL_EGL_LIBRARY", path);
            envMap.put("SDL_OPENGL_LIBRARY", path);
        }
    }

    @Override
    public boolean setupRenderer() {
        File library = new File(Tools.NATIVE_LIB_DIR, LIBRARY);
        if (!library.isFile()) {
            Log.e("EmesisRenderSpec", "Renderer library is missing from " + Tools.NATIVE_LIB_DIR);
            return false;
        }
        String path = library.getAbsolutePath();
        // Load the renderer into the process first, the way the plugin's host does: it registers
        // under its soname, so the renderer's own lookups resolve to this copy and LWJGL binds GL
        // to an already loaded library instead of a second one.
        try {
            System.load(path);
        } catch (Throwable error) {
            Logger.appendToLog("Emesis preload failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        // Both libraries live in the app's own library directory, so the plain load finds the copy
        // that is already in the process - the one LWJGL binds OpenGL to - instead of a second
        // mapping. One copy means the EGL context and the GL calls above it are the same library
        // state. A namespaced load is only the fallback for a device where the plain one fails.
        if (MojoExec.prepareEgl(path, false, true, GLES_MAJOR)) return true;
        return MojoExec.prepareEgl(path, true, true, GLES_MAJOR);
    }
}
