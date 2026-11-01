package net.kdt.pojavlaunch.utils.jre;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.Manifest;
import java.util.regex.*;
import java.util.zip.*;

/** Reads enabled mods' declared Java and Mixin requirements without loading or changing mod code. */
public final class ModJavaRequirements {
    private static final int MAX_JSON = 512 * 1024;
    private static final Pattern LEVEL = Pattern.compile("JAVA_(?:1_)?(\\d{1,2})");
    private static final Pattern MINIMUM = Pattern.compile("^(?:>=?|=|~|\\^)?(?:1\\.)?(\\d{1,2})(?:\\..*)?$");
    private static final Map<String, Cached> CACHE = new LinkedHashMap<String, Cached>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Cached> entry) { return size() > 512; }
    };
    private static final class Cached {
        final long size, modified;
        final int java;
        Cached(File file, int java) { size = file.length(); modified = file.lastModified(); this.java = java; }
    }
    private ModJavaRequirements() { }

    public static int minimumJava(File gameDirectory, int minecraftMinimum) {
        int required = Math.max(8, minecraftMinimum);
        File[] mods = new File(gameDirectory, "mods").listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (mods == null) return required;
        for (File mod : mods) required = Math.max(required, cachedMinimum(mod));
        return required;
    }
    private static int cachedMinimum(File mod) {
        synchronized (CACHE) {
            Cached cached = CACHE.get(mod.getAbsolutePath());
            if (cached != null && cached.size == mod.length() && cached.modified == mod.lastModified()) return cached.java;
        }
        long size = mod.length(), modified = mod.lastModified();
        int required = scan(mod);
        if (size == mod.length() && modified == mod.lastModified())
            synchronized (CACHE) { CACHE.put(mod.getAbsolutePath(), new Cached(mod, required)); }
        return required;
    }
    private static int scan(File mod) {
        int required = 8;
        try (ZipFile zip = new ZipFile(mod)) {
            Set<String> configs = new HashSet<>();
            JsonObject fabric = json(zip, "fabric.mod.json");
            if (fabric != null) {
                if (fabric.has("environment") && "server".equals(fabric.get("environment").getAsString())) return 8;
                JsonElement depends = fabric.get("depends");
                if (depends != null && depends.isJsonObject()) required = Math.max(required, javaConstraint(depends.getAsJsonObject().get("java")));
                mixins(fabric.get("mixins"), configs);
            }
            JsonObject quilt = json(zip, "quilt.mod.json");
            if (quilt != null) mixins(quilt.get("mixin"), configs);
            ZipEntry manifest = zip.getEntry("META-INF/MANIFEST.MF");
            if (manifest != null) {
                byte[] bytes = bytes(zip, manifest);
                String declared = new Manifest(new ByteArrayInputStream(bytes)).getMainAttributes().getValue("MixinConfigs");
                if (declared != null) for (String config : declared.split(",")) configs.add(config.trim());
            }
            for (String config : configs) {
                try {
                    JsonObject mixin = json(zip, config);
                    if (mixin == null || !mixin.has("compatibilityLevel")) continue;
                    Matcher level = LEVEL.matcher(mixin.get("compatibilityLevel").getAsString());
                    if (level.matches()) required = Math.max(required, Integer.parseInt(level.group(1)));
                } catch (IOException | RuntimeException ignored) { /* Let the loader diagnose invalid metadata. */ }
            }
        } catch (IOException | RuntimeException ignored) { /* Invalid/non-mod archives must not crash runtime selection. */ }
        return required;
    }
    private static void mixins(JsonElement declaration, Set<String> configs) {
        if (declaration == null || declaration.isJsonNull()) return;
        if (declaration.isJsonArray()) { for (JsonElement value : declaration.getAsJsonArray()) mixins(value, configs); }
        else if (declaration.isJsonPrimitive() && declaration.getAsJsonPrimitive().isString()) configs.add(declaration.getAsString());
        else if (declaration.isJsonObject()) {
            JsonElement config = declaration.getAsJsonObject().get("config");
            JsonElement environment = declaration.getAsJsonObject().get("environment");
            if (environment == null || !"server".equals(environment.getAsString())) mixins(config, configs);
        }
    }
    private static int javaConstraint(JsonElement declaration) {
        if (declaration == null || declaration.isJsonNull()) return 8;
        if (declaration.isJsonArray()) {
            int minimum = Integer.MAX_VALUE;
            for (JsonElement option : declaration.getAsJsonArray()) minimum = Math.min(minimum, javaConstraint(option));
            return minimum == Integer.MAX_VALUE ? 8 : minimum;
        }
        if (!declaration.isJsonPrimitive()) return 8;
        int alternatives = Integer.MAX_VALUE;
        for (String alternative : declaration.getAsString().split("\\|\\|")) {
            int lower = 8;
            for (String token : alternative.trim().replaceAll("([<>=~^]+)\\s+", "$1").split("[\\s,]+")) {
                Matcher matcher = MINIMUM.matcher(token);
                if (matcher.matches()) lower = Math.max(lower, Integer.parseInt(matcher.group(1)));
            }
            alternatives = Math.min(alternatives, lower);
        }
        return alternatives == Integer.MAX_VALUE ? 8 : alternatives;
    }
    private static JsonObject json(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory()) return null;
        JsonElement parsed = new JsonParser().parse(new String(bytes(zip, entry), StandardCharsets.UTF_8));
        return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    }
    private static byte[] bytes(ZipFile zip, ZipEntry entry) throws IOException {
        if (entry.getSize() > MAX_JSON) throw new IOException("Mod metadata too large");
        try (InputStream in = zip.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > MAX_JSON) throw new IOException("Mod metadata too large");
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
