package aerix.flashback.compat;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AerixFlashbackPreLaunch implements PreLaunchEntrypoint {

    private static final String IMGUI_LIB = "libimgui-moulberry90-java64.so";
    private static final String LIBCXX_LIB = "libc++_flashtoch.so";

    @Override
    public void onPreLaunch() {
        System.setProperty("org.lwjgl.nfd.linux.portal", "false");

        String existingImguiPath = System.getProperty("imgui.library.path");
        if (existingImguiPath == null || !new File(existingImguiPath, IMGUI_LIB).isFile()) {
            String detected = findNativeLibDir();
            if (detected != null) {
                System.setProperty("imgui.library.path", detected);
                existingImguiPath = detected;
            }
        }

        if (existingImguiPath != null) {
            File libcxx = new File(existingImguiPath, LIBCXX_LIB);
            if (libcxx.isFile()) {
                try {
                    System.load(libcxx.getAbsolutePath());
                } catch (Throwable ignored) {
                }
            }
        }

        String javacppCache = System.getProperty("org.bytedeco.javacpp.cachedir");
        if (javacppCache == null || !javacppCache.startsWith("/data")) {
            String tmp = System.getProperty("java.io.tmpdir", "");
            if (!tmp.isEmpty()) {
                Path cacheDir = Paths.get(tmp, "javacpp_cache");
                try {
                    Files.createDirectories(cacheDir);
                    System.setProperty("org.bytedeco.javacpp.cachedir", cacheDir.toAbsolutePath().toString());
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String findNativeLibDir() {
        String javaLibPath = System.getProperty("java.library.path", "");
        for (String entry : javaLibPath.split(File.pathSeparator)) {
            if (entry.isEmpty()) continue;
            if (new File(entry, IMGUI_LIB).isFile()) {
                return entry;
            }
        }
        return null;
    }
}
