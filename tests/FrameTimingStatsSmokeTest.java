import net.kdt.pojavlaunch.game.FrameTimingStats;

public final class FrameTimingStatsSmokeTest {
    static void eq(long a, long b) { if (a != b) throw new AssertionError(a + " != " + b); }
    public static void main(String[] args) {
        long[] before = new long[101], after = new long[101];
        if (FrameTimingStats.calculate(before, after) != null) throw new AssertionError("empty window");
        after[16] = 95; after[80] = 4; after[100] = 1;
        FrameTimingStats data = FrameTimingStats.calculate(before, after);
        eq(100, data.samples); eq(16, data.p95Ms); eq(5, data.stutters50ms);
        if (Math.abs(data.meanMsCapped - 19.4) > 0.001) throw new AssertionError("mean");
        before[33] = 0xfffffffeL; after[33] = 1;
        data = FrameTimingStats.calculate(before, after);
        eq(103, data.samples);
        System.out.println("Frame timing stats smoke tests passed");
    }
}
