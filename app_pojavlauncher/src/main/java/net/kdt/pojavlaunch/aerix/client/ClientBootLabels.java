package net.kdt.pojavlaunch.aerix.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Labels for the Minecraft instance being launched, without guessing an unknown version. */
public final class ClientBootLabels {
    private static final Pattern PROFILE = Pattern.compile(
            "(?:^|-)((?:1\\.\\d+(?:\\.\\d+)?|26\\.\\d+(?:\\.\\d+)?))$");
    private static final Pattern LOADING = Pattern.compile(
            "\\bLoading Minecraft\\s+([a-zA-Z0-9][a-zA-Z0-9._+\\-]*)\\s+with\\s+Fabric Loader\\b",
            Pattern.CASE_INSENSITIVE);
    private ClientBootLabels() { }

    public static String fromProfile(String profileId) {
        if (profileId == null) return null;
        Matcher matcher = PROFILE.matcher(profileId);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String fromLoadingLog(String line) {
        if (line == null) return null;
        Matcher matcher = LOADING.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String header(String version) {
        return version == null || version.isEmpty() ? "Aerix Client" : "Aerix Client " + version;
    }

    public static String loadingMods(String version) {
        return version == null || version.isEmpty()
                ? "Loading Minecraft and Fabric mods..."
                : "Loading Minecraft " + version + " and Fabric mods...";
    }

    public static String textureAtlases(String version) {
        return version == null || version.isEmpty()
                ? "Stitching texture atlases..."
                : "Stitching Minecraft " + version + " texture atlases...";
    }
}
