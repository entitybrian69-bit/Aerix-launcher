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

    /**
     * The largest heap this device can actually give the game, in MiB.
     *
     * <p>Pojav's own ceiling is "device RAM minus a gigabyte", which counts memory the launcher
     * process has already promised to the GL driver, to native textures and to Android. Dragging
     * the slider to that maximum is what produces the reported "10 fps, freezes when switching
     * settings, cannot enter a world": the JVM accepts the heap and the kernel then reclaims it.
     * Minecraft does not gain frames past roughly 3 GB of heap either - a bigger heap only means a
     * longer, starker garbage collection - so a practical cap is the third constraint.
     *
     * @param deviceRamMb total device memory in MiB
     */
    public static int safeMb(long deviceRamMb, boolean is32Bit) {
        if (deviceRamMb < 2048 || is32Bit) {
            return (int) Math.max(MIN_MB, Math.min(1024, Math.max(MIN_MB, deviceRamMb / 2)));
        }
        long systemReserve = Math.max(1536, deviceRamMb * 2 / 5);
        long headroom = deviceRamMb - systemReserve;
        long practicalCap = deviceRamMb >= 12288 ? 4096 : deviceRamMb >= 8192 ? 3584 : 3072;
        long PojavCeiling = deviceRamMb - (deviceRamMb < 3064 ? 800 : 1024);
        return (int) Math.max(MIN_MB, Math.min(Math.min(headroom, practicalCap), PojavCeiling));
    }

    /** The device's own safe maximum, read live. Cheap enough to call from a UI callback. */
    public static int safeMaxMb(android.content.Context context) {
        // Tools.getTotalDeviceMemory already reports megabytes, not bytes.
        return safeMb(net.kdt.pojavlaunch.Tools.getTotalDeviceMemory(context),
                net.kdt.pojavlaunch.Architecture.is32BitsDevice());
    }

    /**
     * Brings a requested allocation inside the device's [safeMb] ceiling, so a value written by the
     * old, wider slider heals itself on the next launch instead of needing a manual reset. Returns
     * the device default when nothing was ever chosen.
     */
    public static int clamp(android.content.Context context, int requestedMb) {
        int max = safeMaxMb(context);
        if (requestedMb <= 0) return max;
        return Math.max(MIN_MB, Math.min(requestedMb, max));
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
