package net.kdt.pojavlaunch.game;

/** Render-swap rate and time-weighted five-second average. Independent of display refresh rate. */
final class FrameRateSampler {
    private final long[] frames = new long[10], durations = new long[10];
    private long lastFrames = -1, lastTime;
    private int index, count, fps, average;
    void reset() { lastFrames = -1; lastTime = 0; index = count = fps = average = 0; }
    boolean sample(long totalFrames, long nowNanos) {
        if (totalFrames < 0) { reset(); return false; }
        if (lastFrames < 0 || nowNanos <= lastTime) {
            lastFrames = totalFrames; lastTime = nowNanos; return false;
        }
        long delta = (totalFrames - lastFrames) & 0xffffffffL;
        long elapsed = nowNanos - lastTime;
        lastFrames = totalFrames; lastTime = nowNanos;
        frames[index] = delta; durations[index] = elapsed;
        index = (index + 1) % frames.length;
        count = Math.min(count + 1, frames.length);
        fps = (int) Math.round(delta * 1_000_000_000d / elapsed);
        long sum = 0, time = 0;
        for (int i = 0; i < count; i++) { sum += frames[i]; time += durations[i]; }
        average = (int) Math.round(sum * 1_000_000_000d / time);
        return true;
    }
    int fps() { return fps; }
    int average() { return average; }
}
