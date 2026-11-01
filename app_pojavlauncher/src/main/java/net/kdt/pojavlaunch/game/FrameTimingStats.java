package net.kdt.pojavlaunch.game;

/** Window statistics from 1-ms native frame-interval buckets (100 means 100 ms or more). */
public final class FrameTimingStats {
    public final long samples;
    public final double meanMsCapped;
    public final int p95Ms;
    public final long stutters50ms;
    private FrameTimingStats(long samples, double mean, int p95, long stutters) {
        this.samples = samples; this.meanMsCapped = mean; this.p95Ms = p95; this.stutters50ms = stutters;
    }
    public static FrameTimingStats calculate(long[] previous, long[] current) {
        if (previous == null || current == null || previous.length != 101 || current.length != 101) return null;
        long[] delta = new long[101];
        long count = 0, sum = 0, stutters = 0;
        for (int i = 0; i < delta.length; i++) {
            delta[i] = (current[i] - previous[i]) & 0xffffffffL;
            count += delta[i];
            sum += delta[i] * i;
            if (i >= 50) stutters += delta[i];
        }
        if (count == 0) return null;
        long threshold = (count * 95 + 99) / 100;
        long cumulative = 0;
        int p95 = 100;
        for (int i = 0; i < delta.length; i++) {
            cumulative += delta[i];
            if (cumulative >= threshold) { p95 = i; break; }
        }
        return new FrameTimingStats(count, (double) sum / count, p95, stutters);
    }
}
