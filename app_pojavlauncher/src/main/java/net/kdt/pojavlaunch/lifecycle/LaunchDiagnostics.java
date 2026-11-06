package net.kdt.pojavlaunch.lifecycle;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Small, private launch breadcrumb. Never records account data, tokens or JVM arguments. */
public final class LaunchDiagnostics {
    public static final String FILE_NAME = "aerix-launch-status.txt";

    private LaunchDiagnostics() {}

    public static void mark(Context context, String stage) {
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            String text = "Aerix launch status\nTime (UTC milliseconds): " + System.currentTimeMillis()
                    + "\nLast stage: " + stage + "\n"
                    + "This is a startup breadcrumb, not a crash diagnosis.\n";
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(text.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w("AerixLaunch", "Could not save launch status", e);
        }
    }
}
