package net.aerix.companion;

import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class CapeCache {
    private static final ScheduledExecutorService IO = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "AerixCapeIO"); t.setDaemon(true); return t;
    });
    private static final Map<UUID, Entry> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final java.util.concurrent.atomic.AtomicInteger IN_FLIGHT = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile String endpoint = "";
    private static final class Entry { Identifier texture; long nextFetch; boolean loading; }

    static void initialize(Path gameDirectory) {
        IO.scheduleWithFixedDelay(() -> {
            try {
                Path config = gameDirectory.resolve("config/aerix-capes.json");
                if (!Files.isRegularFile(config) || Files.size(config) > 8192) return;
                String value = JsonParser.parseString(Files.readString(config)).getAsJsonObject().get("endpoint").getAsString();
                URI uri = URI.create(value);
                if (!value.isEmpty() && (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)) return;
                String next = value.replaceAll("/+$", "");
                if (!endpoint.equals(next)) {
                    endpoint = next;
                    MinecraftClient.getInstance().execute(() -> {
                        CACHE.values().forEach(e -> { if (e.texture != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(e.texture); });
                        CACHE.clear();
                    });
                }
            } catch (Exception ignored) { }
        }, 0, 60, TimeUnit.SECONDS);
    }

    static Identifier texture(UUID uuid) {
        if (endpoint.isEmpty()) return null;
        Entry entry = CACHE.computeIfAbsent(uuid, key -> new Entry());
        while (CACHE.size() > 512) {
            UUID oldest = CACHE.keySet().iterator().next();
            Entry removed = CACHE.remove(oldest);
            if (removed.texture != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(removed.texture);
        }
        if (!entry.loading && IN_FLIGHT.get() < 16 && System.currentTimeMillis() >= entry.nextFetch) {
            IN_FLIGHT.incrementAndGet();
            entry.loading = true;
            entry.nextFetch = System.currentTimeMillis() + 60_000;
            String source = endpoint;
            IO.execute(() -> download(uuid, entry, source));
        }
        return entry.texture;
    }

    private static void download(UUID uuid, Entry entry, String source) {
        NativeImage image = null;
        boolean missing = false;
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(source + "/v1/capes/" + uuid).toURL().openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(5000);
                if (connection.getResponseCode() == 404) { missing = true; }
                else if (connection.getResponseCode() == 200) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    try (var input = connection.getInputStream()) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = input.read(buffer)) != -1) {
                            if (output.size() + n > 1_048_576) throw new IllegalArgumentException("Cape too large");
                            output.write(buffer, 0, n);
                        }
                    }
                    byte[] data = output.toByteArray();
                    if (data.length < 24 || ByteBuffer.wrap(data).getLong() != 0x89504e470d0a1a0aL || ByteBuffer.wrap(data).getInt(12) != 0x49484452 || ByteBuffer.wrap(data).getInt(16) != 64 || ByteBuffer.wrap(data).getInt(20) != 32) throw new IllegalArgumentException("Invalid cape dimensions");
                    image = NativeImage.read(data);
                }
            } finally { connection.disconnect(); }
        } catch (Exception ignored) { }
        NativeImage ready = image;
        boolean remove = missing;
        MinecraftClient.getInstance().execute(() -> {
            IN_FLIGHT.decrementAndGet();
            entry.loading = false;
            if (CACHE.get(uuid) != entry || !endpoint.equals(source)) { if (ready != null) ready.close(); return; }
            if (ready != null || remove) {
                if (entry.texture != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.texture);
                entry.texture = null;
            }
            if (ready != null) {
                entry.texture = new Identifier("aerix_companion", "capes/" + uuid);
                MinecraftClient.getInstance().getTextureManager().registerTexture(entry.texture, new NativeImageBackedTexture(ready));
            }
        });
    }
}
