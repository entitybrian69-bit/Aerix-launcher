package net.kdt.pojavlaunch.game;

import androidx.annotation.Keep;

/** A shared native counter, not Android window-vsync callbacks. */
@Keep
final class NativeFrameCounter {
    private static final boolean AVAILABLE;
    static {
        boolean loaded;
        try { System.loadLibrary("aerix_frame_counter"); loaded = true; }
        catch (UnsatisfiedLinkError | SecurityException e) { loaded = false; }
        AVAILABLE = loaded;
    }
    static long read() { return AVAILABLE ? readFrames() : -1; }
    private static native long readFrames();
}
