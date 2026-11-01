package net.kdt.pojavlaunch.utils.jre;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Per-instance maximum heap stored in that instance's JVM arguments. */
public final class InstanceMemory {
    private static final Pattern HEAP = Pattern.compile("(?<!\\S)-Xmx([0-9]+)([mMgG])(?!\\S)");
    private static final int MIN_MB = 256;

    private InstanceMemory() { }

    /** Returns the last valid instance override in MiB, or -1 for the launcher default. */
    public static int overrideMb(String instanceArgs) {
        if (instanceArgs == null) return -1;
        Matcher matcher = HEAP.matcher(instanceArgs);
        int found = -1;
        while (matcher.find()) {
            try {
                long amount = Long.parseLong(matcher.group(1));
                if (matcher.group(2).equalsIgnoreCase("g")) {
                    if (amount > Integer.MAX_VALUE / 1024L) continue;
                    amount *= 1024L;
                }
                if (amount >= MIN_MB && amount <= Integer.MAX_VALUE) found = (int) amount;
            } catch (NumberFormatException ignored) {
                // Invalid manual JVM input must not replace the launcher default.
            }
        }
        return found;
    }

    public static int effectiveMb(String instanceArgs, int globalMb) {
        int override = overrideMb(instanceArgs);
        return override > 0 ? override : globalMb;
    }

    /** Exactly one maximum and one matching initial heap, so -Xms cannot exceed -Xmx. */
    public static String[] heapOptions(int memoryMb) {
        return new String[] { "-Xmx" + memoryMb + "M", "-Xms" + memoryMb + "M" };
    }

    /** Removes only standalone supported heap arguments, retaining all other instance options. */
    public static String withOverride(String instanceArgs, int ramMb) {
        String original = instanceArgs == null ? "" : instanceArgs;
        String stripped = HEAP.matcher(original).replaceAll("").trim();
        if (ramMb < MIN_MB) return stripped;
        return stripped.isEmpty() ? "-Xmx" + ramMb + "M" : "-Xmx" + ramMb + "M " + stripped;
    }
}
