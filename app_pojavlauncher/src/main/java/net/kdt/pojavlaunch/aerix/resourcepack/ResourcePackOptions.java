package net.kdt.pojavlaunch.aerix.resourcepack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import net.kdt.pojavlaunch.aerix.client.ClientOptionLines;

/**
 * Pure Java helper for reading and updating Minecraft's {@code resourcePacks} entry in
 * {@code options.txt} without letting Aerix's built-in client pack override user resource packs.
 */
public final class ResourcePackOptions {
    private static final String KEY = "resourcePacks";
    private static final String VANILLA = "vanilla";
    private static final String FILE_PREFIX = "file/";

    private ResourcePackOptions() { }

    /**
     * Parses a {@code resourcePacks} JSON array string, defaulting to {@code ["vanilla"]} when
     * missing, blank, empty, or malformed. Always guarantees {@code "vanilla"} is present.
     */
    public static List<String> parseResourcePacks(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>(Collections.singletonList(VANILLA));
        }
        try {
            return parseStrict(raw);
        } catch (RuntimeException ignored) {
            return new ArrayList<>(Collections.singletonList(VANILLA));
        }
    }

    /**
     * Parses a {@code resourcePacks} JSON array string, treating missing/blank/{@code []} as
     * {@code ["vanilla"]}, and rejecting non-empty malformed JSON so corrupt config is not
     * silently clobbered.
     */
    public static List<String> parseStrict(String raw) {
        List<String> result = new ArrayList<>();
        if (raw != null && !raw.trim().isEmpty()) {
            JsonElement parsed;
            try {
                parsed = new JsonParser().parse(raw);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Malformed resourcePacks entry", e);
            }
            if (!parsed.isJsonArray()) {
                throw new IllegalArgumentException("Malformed resourcePacks entry");
            }
            JsonArray array = parsed.getAsJsonArray();
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < array.size(); i++) {
                JsonElement elem = array.get(i);
                if (elem == null || !elem.isJsonPrimitive() || !elem.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Malformed resourcePacks element");
                }
                String value = elem.getAsString();
                if (value != null && !value.trim().isEmpty() && seen.add(value)) {
                    result.add(value);
                }
            }
        }
        if (!result.contains(VANILLA)) {
            result.add(0, VANILLA);
        }
        return result;
    }

    public static String toJsonArray(List<String> packs) {
        JsonArray array = new JsonArray();
        if (packs != null) {
            for (String pack : packs) {
                if (pack != null && !pack.isEmpty()) {
                    array.add(pack);
                }
            }
        }
        return array.toString();
    }

    /**
     * Places {@code clientPackId} (e.g. {@code "file/Aerix-Client-Pack.zip"}) after built-in packs
     * ({@code "vanilla"}, {@code "fabric"}, etc.) and strictly before any user {@code "file/..."}
     * resource packs so the client base pack never overrides user texture packs.
     */
    public static String mergeClientPack(String raw, String clientPackId, boolean enabled) {
        List<String> packs = parseResourcePacks(raw);
        packs.removeIf(p -> p.equals(clientPackId));
        if (enabled && clientPackId != null && !clientPackId.isEmpty()) {
            int insertAt = 0;
            while (insertAt < packs.size() && !packs.get(insertAt).startsWith(FILE_PREFIX)) {
                insertAt++;
            }
            packs.add(insertAt, clientPackId);
        }
        return toJsonArray(packs);
    }

    public static String findRawValue(List<String> optionLines) {
        if (optionLines == null) return null;
        String raw = null;
        for (String line : optionLines) {
            if (line != null && line.startsWith(KEY + ":")) {
                raw = line.substring(KEY.length() + 1).trim();
            }
        }
        return raw;
    }

    public static boolean isPackEnabled(List<String> optionLines, String packFileName) {
        if (packFileName == null || packFileName.isEmpty()) return false;
        String raw = findRawValue(optionLines);
        if (raw == null || raw.isEmpty()) return false;
        try {
            return parseStrict(raw).contains(FILE_PREFIX + packFileName);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static List<String> setPackEnabled(List<String> optionLines, String packFileName, boolean enabled) {
        if (packFileName == null || packFileName.isEmpty()) {
            throw new IllegalArgumentException("Invalid pack name");
        }
        List<String> lines = optionLines == null ? Collections.emptyList() : optionLines;
        List<String> packs = parseStrict(findRawValue(lines));
        String id = FILE_PREFIX + packFileName;
        if (enabled) {
            if (!packs.contains(id)) {
                packs.add(id);
            }
        } else {
            packs.removeIf(p -> p.equals(id));
        }
        return ClientOptionLines.merge(lines, Collections.singletonMap(KEY, toJsonArray(packs)));
    }
}
