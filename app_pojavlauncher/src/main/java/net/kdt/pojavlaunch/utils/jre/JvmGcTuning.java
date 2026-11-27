package net.kdt.pojavlaunch.utils.jre;

import java.util.List;

/**
 * Low-pause JVM garbage-collection defaults for Minecraft on Android.
 *
 * <p>Without explicit tuning, Java 8 HotSpot defaults to ParallelGC (long stop-the-world full
 * collections) and Java 17/21 G1GC defaults to a 200 ms pause budget with only 10% reserve,
 * producing multi-frame stutters during chunk generation and gameplay. User-specified GC choices
 * and individual {@code -XX:} flags are always preserved.
 */
public final class JvmGcTuning {
    private JvmGcTuning() { }

    public static void appendDefaultGcArgs(List<String> args, int memoryMb, boolean allowG1Gc) {
        if (args == null) return;
        // Avoid /tmp/hsperfdata_* mmap writes on safepoints and GC cycles across all HotSpot VMs.
        addIfUnset(args, "-XX:+PerfDisableSharedMem");
        if (!allowG1Gc || hasExplicitGcArg(args)) return;

        String regionSize = memoryMb >= 1024 ? "8M" : "4M";
        boolean lowMemHeap = memoryMb <= 1280;
        String[] g1Defaults = lowMemHeap ? new String[] {
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=50",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+DisableExplicitGC",
                "-XX:ConcGCThreads=1",
                "-XX:ParallelGCThreads=2",
                "-XX:G1NewSizePercent=10",
                "-XX:G1MaxNewSizePercent=25",
                "-XX:G1HeapRegionSize=" + regionSize,
                "-XX:G1ReservePercent=10",
                "-XX:G1HeapWastePercent=5",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:InitiatingHeapOccupancyPercent=30",
                "-XX:G1MixedGCLiveThresholdPercent=90",
                "-XX:G1RSetUpdatingPauseTimePercent=5",
                "-XX:SurvivorRatio=32",
                "-XX:MaxTenuringThreshold=1"
        } : new String[] {
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=50",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+DisableExplicitGC",
                "-XX:G1NewSizePercent=20",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1HeapRegionSize=" + regionSize,
                "-XX:G1ReservePercent=20",
                "-XX:G1HeapWastePercent=5",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:InitiatingHeapOccupancyPercent=20",
                "-XX:G1MixedGCLiveThresholdPercent=90",
                "-XX:G1RSetUpdatingPauseTimePercent=5",
                "-XX:SurvivorRatio=32",
                "-XX:MaxTenuringThreshold=1"
        };
        for (String flag : g1Defaults) {
            addIfUnset(args, flag);
        }
    }

    public static boolean hasExplicitGcArg(List<String> args) {
        if (args == null) return false;
        for (String arg : args) {
            if (arg == null) continue;
            if ("-Xincgc".equals(arg) || arg.startsWith("-Xgc:")) return true;
            if ((arg.startsWith("-XX:+Use") || arg.startsWith("-XX:-Use")) && arg.endsWith("GC")) {
                return true;
            }
        }
        return false;
    }

    private static void addIfUnset(List<String> args, String flag) {
        String key = xxOptionKey(flag);
        for (String existing : args) {
            if (key.equals(xxOptionKey(existing))) return;
        }
        args.add(flag);
    }

    private static String xxOptionKey(String arg) {
        if (arg == null) return "";
        if (arg.startsWith("-XX:+") || arg.startsWith("-XX:-")) {
            return arg.substring(5);
        }
        int eq = arg.indexOf('=');
        if (arg.startsWith("-XX:") && eq > 4) {
            return arg.substring(4, eq);
        }
        return arg;
    }
}
