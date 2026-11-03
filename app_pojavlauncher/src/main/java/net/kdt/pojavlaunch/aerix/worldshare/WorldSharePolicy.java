package net.kdt.pojavlaunch.aerix.worldshare;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicitly supported upstream Android assets; do not guess for new versions or old Forge. */
public final class WorldSharePolicy {
    private WorldSharePolicy() { }
    public static final String FABRIC = "e4all-fabric-2.3.0-android.1.jar";
    public static final String FABRIC_MODERN = "e4all-fabric-2.3.0-modern-android.1.jar";
    public static final String FORGE = "e4all-forge-2.3.0-android.1.jar";
    public static final String NEOFORGE = "e4all-neoforge-2.3.0-android.jar";

    public static String asset(String loader, String version) {
        if (loader == null || version == null) return null;
        boolean modern = version.matches("26\\.1(?:\\.[12])?|26\\.[23]|26\\.4-snapshot-[1-5]");
        Matcher m = Pattern.compile("1\\.(18|19|20|21)(?:\\.(\\d{1,2}))?").matcher(version);
        int minor = 0, patch = 0;
        boolean legacy = m.matches();
        if (legacy) {
            minor = Integer.parseInt(m.group(1));
            patch = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            legacy = patch <= (minor == 18 ? 2 : minor == 19 ? 4 : minor == 20 ? 6 : 11);
        }
        if (loader.equals("fabric")) return modern ? FABRIC_MODERN : legacy ? FABRIC : null;
        if (loader.equals("forge")) {
            // The 1.18–1.20.1 upstream jar is not published as an Android build.
            if (version.startsWith("26.4-")) return null;
            return modern || (legacy && (minor == 21 || (minor == 20 && patch == 6))) ? FORGE : null;
        }
        if (loader.equals("neoforge")) return modern || (legacy && (minor == 21 || (minor == 20 && patch >= 2))) ? NEOFORGE : null;
        return null;
    }

    /** Only parse a logger message, never a chat line containing a forged domain. */
    public static String addressEvent(String line) {
        if (line == null || line.contains("[CHAT]")) return null;
        Matcher match = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}\\] \\[[^\\r\\n]+/(?:INFO|WARN)\\]: (?:(?:\\[e4all\\]|\\(e4all\\)) )?Domain (?:assigned|reassigned): ([a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+(?::[0-9]{1,5})?)(?: \\(was: [^)]*\\))?$", Pattern.CASE_INSENSITIVE).matcher(line.trim());
        if (!match.matches()) return null;
        String value = match.group(1);
        int i = value.lastIndexOf(':');
        if (i >= 0) {
            int port = Integer.parseInt(value.substring(i + 1));
            if (port < 1 || port > 65535) return null;
        }
        return value;
    }
    public static boolean endedEvent(String line) {
        if (line == null || line.contains("[CHAT]")) return false;
        return line.contains("e4all: world stopping, tearing down the relay session") ||
            line.contains("e4all: relay session stopped") ||
            line.contains("e4all: relay session failed") ||
            line.contains("e4all: relay connection lost, session stopped") ||
            line.contains("QUIC channel became inactive (relay connection lost)");
    }
}
