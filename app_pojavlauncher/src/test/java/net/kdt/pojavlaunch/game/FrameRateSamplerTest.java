package net.kdt.pojavlaunch.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class FrameRateSamplerTest {
    @Test public void gameFramesAreNotCappedAtDisplayRefresh() {
        FrameRateSampler s = new FrameRateSampler();
        assertFalse(s.sample(0, 1));
        assertTrue(s.sample(150, 500_000_001));
        assertEquals(300, s.fps()); assertEquals(300, s.average());
    }
    @Test public void slowGameDoesNotReportUiSixtyFps() {
        FrameRateSampler s = new FrameRateSampler();
        s.sample(100, 1); s.sample(110, 500_000_001);
        assertEquals(20, s.fps());
    }
    @Test public void noNewFramesReportsZero() {
        FrameRateSampler s = new FrameRateSampler();
        s.sample(500, 1); s.sample(500, 500_000_001); assertEquals(0, s.fps());
    }
    @Test public void averageAccountsForDelayedUiPolling() {
        FrameRateSampler s = new FrameRateSampler();
        s.sample(0, 1); s.sample(150, 500_000_001); s.sample(300, 2_000_000_001L);
        assertEquals(100, s.fps()); assertEquals(150, s.average());
    }
    @Test public void unsignedNativeCounterWrapIsHandled() {
        FrameRateSampler s = new FrameRateSampler();
        s.sample(0xfffffff0L, 1); s.sample(14, 500_000_001);
        assertEquals(60, s.fps());
    }
    @Test public void resumedOverlayDoesNotCountHiddenTime() {
        FrameRateSampler s = new FrameRateSampler();
        s.sample(0, 1); s.sample(100, 500_000_001); s.reset();
        assertFalse(s.sample(10000, 100_000_000_001L));
        s.sample(10060, 100_500_000_001L); assertEquals(120, s.fps());
    }
    @Test public void unavailableSourceResetsRatherThanFabricatingFrames() {
        FrameRateSampler s = new FrameRateSampler();
        s.sample(10, 1); assertFalse(s.sample(-1, 500_000_001));
        assertFalse(s.sample(20, 1_000_000_001));
    }
}
