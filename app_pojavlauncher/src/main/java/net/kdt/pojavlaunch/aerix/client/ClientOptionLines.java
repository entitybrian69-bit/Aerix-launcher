package net.kdt.pojavlaunch.aerix.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Line-preserving editing of Minecraft options.txt; duplicate target keys are all updated. */
public final class ClientOptionLines {
    private ClientOptionLines() { }

    public static Map<String, String> keybinds(List<String> lines) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : lines) {
            int at = line.indexOf(':');
            if (at > 4 && line.startsWith("key_") && line.length() <= 1024) {
                result.put(line.substring(0, at), line.substring(at + 1));
            }
        }
        return result;
    }

    public static List<String> merge(List<String> lines, Map<String, String> values) {
        Map<String, String> remaining = new LinkedHashMap<>(values);
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            int at = line.indexOf(':');
            if (at <= 0) { result.add(line); continue; }
            String key = line.substring(0, at);
            if (values.containsKey(key)) {
                result.add(key + ":" + values.get(key));
                remaining.remove(key);
            } else result.add(line);
        }
        for (Map.Entry<String, String> entry : remaining.entrySet()) result.add(entry.getKey() + ":" + entry.getValue());
        return result;
    }
}
