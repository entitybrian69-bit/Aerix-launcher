package net.kdt.pojavlaunch.aerix.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Translate explicitly selected launcher options to the target game's on-disk keys. */
public final class ClientVersionOptions {
    private ClientVersionOptions() { }
    public static Map<String, String> translate(String version, Map<String, String> selected) {
        Map<String, String> out = new LinkedHashMap<String, String>(selected);
        if (out.containsKey("viewBobbing")) out.put("bobView", out.remove("viewBobbing"));
        if ("26.3".equals(version) && out.containsKey("graphicsMode")) {
            String mode = out.remove("graphicsMode");
            if ("0".equals(mode)) out.put("graphicsPreset", "\"fast\"");
            else if ("1".equals(mode)) out.put("graphicsPreset", "\"fancy\"");
            else if ("2".equals(mode)) out.put("graphicsPreset", "\"fabulous\"");
        }
        return out;
    }
}
