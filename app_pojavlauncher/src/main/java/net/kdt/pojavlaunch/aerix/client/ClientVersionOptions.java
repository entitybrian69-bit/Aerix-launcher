package net.kdt.pojavlaunch.aerix.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Translate explicitly selected launcher options to the target game's on-disk keys. */
public final class ClientVersionOptions {
    private ClientVersionOptions() { }
    private static void putIfAbsent(Map<String, String> map, String key, String value) {
        if (!map.containsKey(key)) map.put(key, value);
    }
    public static Map<String, String> translate(String version, Map<String, String> selected) {
        Map<String, String> out = new LinkedHashMap<String, String>(selected);
        if (out.containsKey("viewBobbing")) out.put("bobView", out.remove("viewBobbing"));
        if ("26.3".equals(version) && out.containsKey("graphicsMode")) {
            String mode = out.remove("graphicsMode");
            if ("0".equals(mode) || "1".equals(mode) || "2".equals(mode)) {
                boolean fast = "0".equals(mode);
                boolean fabulous = "2".equals(mode);
                out.put("graphicsPreset", fast ? "\"fast\"" : fabulous ? "\"fabulous\"" : "\"fancy\"");
                putIfAbsent(out, "ao", fast ? "false" : "true");
                putIfAbsent(out, "cutoutLeaves", fast ? "false" : "true");
                putIfAbsent(out, "improvedTransparency", fabulous ? "true" : "false");
                putIfAbsent(out, "weatherRadius", fast ? "5" : "10");
                putIfAbsent(out, "entityDistanceScaling", fast ? "0.75" : fabulous ? "1.25" : "1.0");
            }
        }
        return out;
    }
}
