package net.kdt.pojavlaunch.modloaders.modpacks.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.util.HashMap;
import java.util.concurrent.CancellationException;

/** One response contract and fallback policy for discovery AND modpack installation. */
public final class CurseForgeApiHandler extends ApiHandler {
    public static final String OFFICIAL = "https://api.curseforge.com/v1";
    public static final String MIRROR = "https://mod.mcimirror.top/curseforge/v1";
    private final String key;
    private final Transport transport;

    public interface Transport {
        JsonElement get(String base, String key, String endpoint, HashMap<String, Object> query) throws Exception;
    }

    public CurseForgeApiHandler(String apiKey) {
        this(apiKey, (base, key, endpoint, query) -> {
            ApiHandler handler = key == null ? new ApiHandler(base) : new ApiHandler(base, key);
            return query == null ? handler.get(endpoint, JsonElement.class) : handler.get(endpoint, query, JsonElement.class);
        });
    }

    public CurseForgeApiHandler(String apiKey, Transport transport) {
        super(OFFICIAL);
        String cleaned = apiKey == null ? "" : apiKey.trim();
        key = cleaned.isEmpty() || cleaned.equalsIgnoreCase("DUMMY") ? null : cleaned;
        this.transport = transport;
    }

    @Override public <T> T get(String endpoint, Class<T> type) { return get(endpoint, null, type); }

    @Override public <T> T get(String endpoint, HashMap<String, Object> query, Class<T> type) {
        if (key != null) {
            T result = request(OFFICIAL, key, endpoint, query, type);
            if (result != null) return result;
        }
        // Never forward the user's official API credential to a third-party mirror.
        return request(MIRROR, null, endpoint, query, type);
    }

    private <T> T request(String base, String credential, String endpoint, HashMap<String, Object> query, Class<T> type) {
        try {
            return decode(endpoint, transport.get(base, credential, endpoint, query), type);
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (Exception failure) {
            return null;
        }
    }

    static <T> T decode(String endpoint, JsonElement response, Class<T> type) {
        if (response == null || response.isJsonNull()) return null;
        JsonElement data = response.isJsonObject() ? response.getAsJsonObject().get("data") : response;
        if (data == null || data.isJsonNull()) return null;
        boolean list = endpoint.equals("categories") || endpoint.equals("minecraft/version") ||
                endpoint.equals("mods/search") || endpoint.matches("mods/[0-9]+/files");
        if (list && !data.isJsonArray()) return null;
        if (!list && endpoint.endsWith("/download-url") &&
                !(data.isJsonPrimitive() && data.getAsJsonPrimitive().isString())) return null;
        if (!list && !endpoint.endsWith("/download-url") && !data.isJsonObject()) return null;
        if (type == JsonArray.class) return data.isJsonArray() ? type.cast(data) : null;
        if (type == JsonObject.class) {
            if (response.isJsonObject()) return type.cast(response);
            JsonObject wrapped = new JsonObject(); wrapped.add("data", data);
            return type.cast(wrapped);
        }
        return new Gson().fromJson(response, type);
    }
}
