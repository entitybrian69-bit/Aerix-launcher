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

    /** A plain DNS address, never a URL or command. Used only for an explicit copy action. */
    public static boolean isJoinAddress(String text) {
        if (text == null || text.length() > 253 || !text.equals(text.trim())) return false;
        Matcher m = Pattern.compile("(?i)^([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+)(?::([0-9]{1,5}))?$").matcher(text);
        if (!m.matches()) return false;
        if (m.group(2) == null) return true;
        int port = Integer.parseInt(m.group(2));
        return port > 0 && port <= 65535;
    }

    // Handle vanilla/Fabric and Forge logger prefixes, but not player-chat messages.
    private static final Pattern LOG_LINE = Pattern.compile(
        "^\\[\\d{2}:\\d{2}:\\d{2}\\] \\[[^\\]\\r\\n]+/(?:INFO|WARN|ERROR)\\](?: \\[[^\\]\\r\\n]+\\]| \\([^()\\r\\n]+\\))?:? (?:(?:\\[e4all\\]|\\(e4all\\)) )?(.*)$",
        Pattern.CASE_INSENSITIVE);
    private static String logMessage(String line) {
        if (line == null || line.toLowerCase(java.util.Locale.ROOT).contains("[chat]")) return null;
        Matcher m = LOG_LINE.matcher(line.trim());
        return m.matches() ? m.group(1) : null;
    }

    /** Only parse a logger message, never a chat line containing a forged domain. */
    public static String addressEvent(String line) {
        String message = logMessage(line);
        if (message == null) return null;
        Matcher match = Pattern.compile("^Domain (?:assigned|reassigned): ([a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+(?::[0-9]{1,5})?)(?: \\(was: [^)]*\\))?$", Pattern.CASE_INSENSITIVE).matcher(message);
        if (!match.matches()) return null;
        String value = match.group(1);
        return isJoinAddress(value) ? value : null;
    }
    public static boolean endedEvent(String line) {
        String message = logMessage(line);
        if (message == null) return false;
        return message.startsWith("e4all: world stopping, tearing down the relay session") ||
            message.startsWith("e4all: world listener stopping, tearing down the relay session") ||
            message.startsWith("e4all: relay session stopped") ||
            message.startsWith("e4all: relay session failed") ||
            message.startsWith("e4all: relay connection lost, session stopped") ||
            message.startsWith("QUIC channel became inactive (relay connection lost)");
    }
}
