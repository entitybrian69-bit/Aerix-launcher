package net.kdt.pojavlaunch.utils.jre;

import android.content.Context;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.Tools;

/**
 * The heap policy read against the device in front of us.
 *
 * <p>Split from {@link InstanceMemory} on purpose: that class is pure arithmetic and is compiled and
 * run on its own by tests/InstanceMemorySmokeTest.java, which is what keeps the ceiling honest. This
 * half needs a Context, and therefore only exists inside the app.
 */
public final class LaunchMemory {
    private LaunchMemory() { }

    /** The device's own safe maximum, in MiB. Cheap enough to call from a UI callback. */
    public static int safeMaxMb(Context context) {
        // Tools.getTotalDeviceMemory already reports megabytes, not bytes.
        return InstanceMemory.safeMb(Tools.getTotalDeviceMemory(context), Architecture.is32BitsDevice());
    }

    /**
     * Brings a requested allocation inside {@link InstanceMemory#safeMb}, so a value written by the
     * old, wider slider heals itself on the next launch instead of needing a manual reset. Returns
     * the device's own ceiling when nothing was ever chosen.
     */
    public static int clamp(Context context, int requestedMb) {
        int max = safeMaxMb(context);
        if (requestedMb <= 0) return max;
        return Math.max(InstanceMemory.MIN_MB, Math.min(requestedMb, max));
    }
}
