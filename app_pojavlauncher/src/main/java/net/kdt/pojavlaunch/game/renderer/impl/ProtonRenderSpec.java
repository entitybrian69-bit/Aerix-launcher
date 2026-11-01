package net.kdt.pojavlaunch.game.renderer.impl;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.game.renderer.RenderSpec;
import net.kdt.pojavlaunch.game.renderer.def.Renderers;

import java.io.File;
import java.util.Map;

import git.artdeell.mojoexec.MojoExec;

/**
 * Proton renderer, bundled with the launcher for 32-bit devices.
 *
 * A desktop-GL-over-GLES translator that runs on top of the system EGL implementation, built from
 * the Large Thin Wrapper and Krypton Wrapper sources. It packages only OpenGL entry points (no EGL
 * symbols at all), so the EGL context - and therefore window creation - is the system one, and the
 * renderer library is what LWJGL binds OpenGL to. The plugin APK of the same renderer hands the
 * host exactly the same pair: {@code libEGL.so} as the EGL provider and its own library as GL.
 *
 * Only the 32-bit ARM build of the renderer is packaged, so the entry is offered on 32-bit devices
 * and hidden everywhere else instead of pointing at a library that is not there.
 */
public class ProtonRenderSpec implements RenderSpec {
    /** The renderer, packaged for 32-bit ARM in jniLibs. */
    public static final String LIBRARY = "libpowervr.so";
    /** ES version the wrapper is asked for; it exposes desktop GL 3.1+ on top of it. */
    private static final int GLES_MAJOR = 3;

    @Override
    public boolean compatibleDevice(Context context) {
        // Only the 32-bit ARM build is packaged, so a 64-bit process has nothing to load.
        if (android.os.Process.is64Bit()) return false;
        return new File(Tools.NATIVE_LIB_DIR, LIBRARY).isFile();
    }

    @Override
    public String name() {
        return "Proton";
    }

    @Override
    public int displayName() {
        return R.string.mcl_setting_renderer_proton;
    }

    @Override
    public String tag() {
        return Renderers.PROTON_RENDERER;
    }

    /** The EGL implementation this renderer runs on: the system one. */
    @Override
    public String library() {
        return "libEGL.so";
    }

    /** LWJGL binds OpenGL to the bundled renderer, not to the EGL shim. */
    @Override
    public String glLibraryPath() {
        File library = new File(Tools.NATIVE_LIB_DIR, LIBRARY);
        return library.isFile() ? library.getAbsolutePath() : "libGLMojo.so";
    }

    @Override
    public void setupEnvironment(Context context, Map<String, String> envMap) {
        // The values the renderer's own plugin declares, which is how its wrapper is configured.
        envMap.put("POJAV_RENDERER", "opengles3");
        envMap.put("LIBGL_ES", String.valueOf(GLES_MAJOR));
        envMap.put("LIBGL_USE_MC_COLOR", "1");
        envMap.put("LIBGL_NOINTOVLHACK", "1");
        envMap.put("LIBGL_NOERROR", "1");
        envMap.put("force_glsl_extensions_warn", "true");
        envMap.put("allow_higher_compat_version", "true");
        envMap.put("allow_glsl_extension_directive_midshader", "true");
    }

    @Override
    public boolean setupRenderer() {
        File library = new File(Tools.NATIVE_LIB_DIR, LIBRARY);
        if (!library.isFile()) {
            Log.e("ProtonRenderSpec", "Renderer library is missing from " + Tools.NATIVE_LIB_DIR);
            return false;
        }
        // EGL stays the system implementation: the renderer has no EGL entry points, so a context
        // created from it could not exist. GLES is forced, because that is what it translates from.
        return MojoExec.prepareEgl("libEGL.so", false, true, GLES_MAJOR);
    }
}
