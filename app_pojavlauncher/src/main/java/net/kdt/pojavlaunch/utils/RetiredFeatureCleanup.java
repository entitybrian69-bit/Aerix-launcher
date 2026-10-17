package net.kdt.pojavlaunch.utils;

import android.content.Context;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Retire only the exact mod installed by the removed Studio feature; preserve worlds/backups. */
public final class RetiredFeatureCleanup {
    private RetiredFeatureCleanup() {}
    public static void beforeLaunch(Context context, File gameDirectory) throws IOException {
        File old = new File(gameDirectory, "mods/aerix-companion-fabric-1.20.1.jar");
        if (old.isFile()) {
            boolean owned = false;
            try (ZipFile zip = new ZipFile(old)) {
                ZipEntry entry = zip.getEntry("fabric.mod.json");
                if (entry != null && entry.getSize() > 0 && entry.getSize() <= 65536) {
                    try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        owned = json.has("id") && "aerix_companion".equals(json.get("id").getAsString());
                    }
                }
            } catch (Exception ignored) { /* Unrecognized/user-supplied files are never moved. */ }
            if (owned) {
                File disabled = new File(gameDirectory, "disabled-aerix-features");
                if (!disabled.isDirectory() && !disabled.mkdirs()) throw new IOException("Unable to disable the retired Aerix companion");
                File saved = new File(disabled, System.currentTimeMillis() + "-" + old.getName());
                if (!old.renameTo(saved)) throw new IOException("Unable to disable the retired Aerix companion before launch");
            }
        }
        File bridge = new File(context.getFilesDir(), "aerix-game-bridge");
        for (String name : new String[]{"switch.json", "switch.json.bak", "switch.json.new", "switch-pending.json", "switch-pending.json.bak", "switch-pending.json.new", "status.json", "status.json.tmp"}) {
            new File(bridge, name).delete();
        }
    }
}
