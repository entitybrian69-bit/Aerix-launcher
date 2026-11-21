package net.kdt.pojavlaunch.utils.jre;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JvmGcTuningSmokeTest {
    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // 1. Default 2048 MB server VM gets low-pause G1GC + 8M region + PerfDisableSharedMem.
        List<String> defaults = new ArrayList<>();
        JvmGcTuning.appendDefaultGcArgs(defaults, 2048, true);
        check(defaults.contains("-XX:+UseG1GC"), "must enable G1GC by default");
        check(defaults.contains("-XX:MaxGCPauseMillis=50"), "must cap GC pause target");
        check(defaults.contains("-XX:+DisableExplicitGC"), "must disable explicit System.gc() stalls");
        check(defaults.contains("-XX:+PerfDisableSharedMem"), "must disable hsperfdata mmap stalls");
        check(defaults.contains("-XX:G1HeapRegionSize=8M"), "must pick 8M regions for >=1024MB heap");

        // 2. Smaller heap (<1024 MB) picks 4M regions.
        List<String> small = new ArrayList<>();
        JvmGcTuning.appendDefaultGcArgs(small, 512, true);
        check(small.contains("-XX:G1HeapRegionSize=4M"), "must pick 4M regions for <1024MB heap");

        // 3. Explicit user GC choice is respected without forcing G1GC alongside it.
        List<String> zgc = new ArrayList<>(Arrays.asList("-XX:+UseZGC"));
        JvmGcTuning.appendDefaultGcArgs(zgc, 2048, true);
        check(!zgc.contains("-XX:+UseG1GC"), "must not force G1GC when user chose ZGC");
        check(zgc.contains("-XX:+PerfDisableSharedMem"), "still disables hsperfdata mmap");

        // 4. Individual user overrides (e.g. MaxGCPauseMillis, -XX:-PerfDisableSharedMem) win.
        List<String> custom = new ArrayList<>(Arrays.asList(
                "-XX:MaxGCPauseMillis=25",
                "-XX:-PerfDisableSharedMem"
        ));
        JvmGcTuning.appendDefaultGcArgs(custom, 2048, true);
        check(custom.contains("-XX:MaxGCPauseMillis=25"), "keeps user pause target");
        check(!custom.contains("-XX:MaxGCPauseMillis=50"), "does not overwrite user pause target");
        check(!custom.contains("-XX:+PerfDisableSharedMem"), "respects -XX:-PerfDisableSharedMem");

        // 5. Client VM skips G1GC flags safely.
        List<String> clientVm = new ArrayList<>();
        JvmGcTuning.appendDefaultGcArgs(clientVm, 512, false);
        check(!clientVm.contains("-XX:+UseG1GC"), "client VM must not receive G1GC");
        check(clientVm.contains("-XX:+PerfDisableSharedMem"), "client VM keeps PerfDisableSharedMem");

        System.out.println("JvmGcTuningSmokeTest passed");
    }
}
