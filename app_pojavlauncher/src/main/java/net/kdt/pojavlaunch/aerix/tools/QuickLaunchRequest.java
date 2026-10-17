package net.kdt.pojavlaunch.aerix.tools;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.List;

import net.kdt.pojavlaunch.JVersionList;

/** A one-use launch destination; authentication data is never stored here. */
public final class QuickLaunchRequest {
    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "aerix-launch-destination.json"));
    }

    public static void write(Context context, File gameDirectory, String server, String world) throws Exception {
        AtomicFile file = file(context);
        file.delete();
        if (server == null && world == null) return;
        JsonObject json = new JsonObject();
        json.addProperty("directory", gameDirectory.getCanonicalPath());
        json.addProperty("server", server);
        json.addProperty("world", world);
        json.addProperty("created", System.currentTimeMillis());
        FileOutputStream stream = file.startWrite();
        try {
            stream.write(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            file.finishWrite(stream);
        } catch (Exception e) { file.failWrite(stream); throw e; }
    }

    public static void consume(Context context, File gameDirectory, JVersionList.Version version, List<String> args) {
        AtomicFile file = file(context);
        try {
            if (!file.getBaseFile().exists()) return;
            JsonObject request;
            try (InputStreamReader reader = new InputStreamReader(file.openRead(), java.nio.charset.StandardCharsets.UTF_8)) {
                request = JsonParser.parseReader(reader).getAsJsonObject();
            }
            file.delete();
            long age = System.currentTimeMillis() - request.get("created").getAsLong();
            if (age < 0 || age > 600_000 || !gameDirectory.getCanonicalPath().equals(request.get("directory").getAsString())) return;
            boolean quickPlay = version.arguments != null && version.arguments.game != null
                && net.kdt.pojavlaunch.Tools.GLOBAL_GSON.toJson(version.arguments.game).contains("\"--quickPlayMultiplayer\"");
            if (request.has("server") && !request.get("server").isJsonNull()) {
                String server = request.get("server").getAsString();
                if (quickPlay) {
                    args.add("--quickPlayMultiplayer");
                    args.add(server);
                } else {
                    URI uri = URI.create("minecraft://" + server);
                    args.add("--server");
                    args.add(uri.getHost());
                    args.add("--port");
                    args.add(String.valueOf(uri.getPort() == -1 ? 25565 : uri.getPort()));
                }
            } else if (quickPlay && request.has("world") && !request.get("world").isJsonNull()) {
                String world = request.get("world").getAsString();
                File saves = new File(gameDirectory, "saves").getCanonicalFile();
                File target = new File(saves, world).getCanonicalFile();
                if (!saves.equals(target.getParentFile()) || !new File(target, "level.dat").isFile()) return;
                args.add("--quickPlaySingleplayer");
                args.add(world);
            }
        } catch (Exception e) {
            file.delete();
            Log.w("QuickLaunch", "Unable to apply launch destination", e);
        }
    }
}
