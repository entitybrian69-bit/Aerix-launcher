package net.kdt.pojavlaunch.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayPositionTest {
    @Test public void dragStaysInsideSafeArea() {
        assertEquals(78f, OverlayPosition.clamp(-50, 78, 900), 0);
        assertEquals(900f, OverlayPosition.clamp(1200, 78, 900), 0);
        assertEquals(300f, OverlayPosition.clamp(300, 78, 900), 0);
    }
    @Test public void savedPositionAdaptsToRotationAndResizing() {
        float fraction = OverlayPosition.fraction(500, 100, 900);
        assertEquals(0.5f, fraction, 0);
        assertEquals(200f, OverlayPosition.restore(fraction, 0, 400), 0);
    }
    @Test public void oversizedOverlayDoesNotMoveOffscreen() {
        assertEquals(20f, OverlayPosition.clamp(200, 20, 10), 0);
        assertEquals(0f, OverlayPosition.fraction(200, 20, 10), 0);
        assertEquals(20f, OverlayPosition.restore(1, 20, 10), 0);
    }
    @Test public void invalidSavedCoordinatesAreSafe() {
        assertEquals(10f, OverlayPosition.restore(Float.NaN, 10, 100), 0);
        assertEquals(100f, OverlayPosition.restore(2, 10, 100), 0);
    }
}
