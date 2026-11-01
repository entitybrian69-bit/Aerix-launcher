package net.kdt.pojavlaunch.aerix.bridge;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.IOException;

/** Upgrade tombstones only: no renderer discovery, installation or native loading. */
public final class RetiredRendererCleanup {
    private RetiredRendererCleanup() {}

    public static boolean isRetiredPackage(String name) {
        return "net.kdt.pojavlaunch.renderer.rustgl".equals(name) ||
                "dev.rustrenderer.plugin".equals(name);
    }

    public static boolean isRetiredTag(String tag) {
        return tag != null && tag.startsWith("plugin:") && isRetiredPackage(tag.substring(7));
    }

    /** Delete only our old private bundle cache, never installed apps, instances or game data. */
    public static synchronized void clean(Context context) {
        try {
            File parent = new File(context.getNoBackupFilesDir(), "renderer-bundles");
            // Do not follow a replaced parent directory outside the private application store.
            File expected = new File(context.getNoBackupFilesDir().getCanonicalFile(), "renderer-bundles");
            if (!parent.getCanonicalFile().equals(expected)) return;
            File retired = new File(expected, "rustgl");
            if (!deleteTree(retired)) Log.w("RendererCleanup", "Retired bundle cleanup will retry next launch");
        } catch (IOException | SecurityException failure) {
            Log.w("RendererCleanup", "Unable to clean retired renderer cache", failure);
        }
    }

    static boolean deleteTree(File file) throws IOException {
        // Unlink symlinks, not their targets. The parent of the initial root is checked above.
        if (!file.getCanonicalFile().equals(file.getAbsoluteFile())) return file.delete() || !file.exists();
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            for (File child : children) if (!deleteTree(child)) return false;
        }
        return file.delete() || !file.exists();
    }
}
