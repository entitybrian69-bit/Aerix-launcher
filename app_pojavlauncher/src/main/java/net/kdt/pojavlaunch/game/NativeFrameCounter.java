package net.kdt.pojavlaunch.game;

import androidx.annotation.Keep;

/** A shared native counter, not Android window-vsync callbacks. */
@Keep
public final class NativeFrameCounter {
    private static final boolean AVAILABLE;
    static {
        boolean loaded;
        try { System.loadLibrary("aerix_frame_counter"); loaded = true; }
        catch (UnsatisfiedLinkError | SecurityException e) { loaded = false; }
        AVAILABLE = loaded;
    }
    public static long read() { return AVAILABLE ? readFrames() : -1; }
    /** Optional 1-ms frame-interval histogram, with the final bucket representing 100 ms or more. */
    public static void setTimingEnabled(boolean enabled) { if (AVAILABLE) setTiming(enabled); }
    public static long[] timingHistogram() { return AVAILABLE ? readTiming() : null; }
    private static native long readFrames();
    private static native void setTiming(boolean enabled);
    private static native long[] readTiming();
}
