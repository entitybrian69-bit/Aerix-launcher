package net.kdt.pojavlaunch.aerix.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Release-only Client versions and resource pack metadata, independent of Android. */
public final class ClientVersions {
    private ClientVersions() { }
    public static final String[] RELEASES = {
        "26.3", "26.2", "26.1.2", "26.1.1", "26.1",
        "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
        "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1",
        "1.19.4", "1.18.2", "1.16.5"
    };
    private static final Pattern VERSION = Pattern.compile("(?<![0-9.])(?:1\\.(?:\\d+)(?:\\.\\d+)?|26\\.(?:\\d+)(?:\\.\\d+)?)(?![0-9.])");
    public static String resolve(String profile, String fallback) {
        if (profile != null) {
            Matcher m = VERSION.matcher(profile);
            if (m.find()) return m.group();
        }
        return fallback;
    }
    public static int format(String version) {
        if (version == null) return 6;
        if (version.equals("26.3")) return 97;
        if (version.equals("26.2")) return 88;
        if (version.startsWith("26.1")) return 84;
        if (version.equals("1.21.11")) return 75;
        if (version.equals("1.21.9") || version.equals("1.21.10")) return 69;
        if (version.equals("1.21.7") || version.equals("1.21.8")) return 64;
        if (version.equals("1.21.6")) return 63;
        if (version.equals("1.21.5")) return 55;
        if (version.equals("1.21.4")) return 46;
        if (version.equals("1.21.2") || version.equals("1.21.3")) return 42;
        if (version.equals("1.21") || version.equals("1.21.1")) return 34;
        if (version.equals("1.20.5") || version.equals("1.20.6")) return 32;
        if (version.equals("1.20.3") || version.equals("1.20.4")) return 22;
        if (version.equals("1.20.2")) return 18;
        if (version.equals("1.20") || version.equals("1.20.1")) return 15;
        if (version.equals("1.19.4")) return 13;
        if (version.startsWith("1.19")) return 9;
        if (version.startsWith("1.18")) return 8;
        if (version.startsWith("1.17")) return 7;
        return 6;
    }
    public static int formatMinor(String version) { return "26.3".equals(version) ? 1 : 0; }
    public static boolean modern(String version) { return version != null && version.startsWith("26."); }
}
