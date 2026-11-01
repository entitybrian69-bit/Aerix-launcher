package net.kdt.pojavlaunch.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingTimestampsTest {
    @Test fun audioAndVideoShareSessionClock() {
        val clock = RecordingTimestamps(100_000_000)
        assertEquals(50_000L, clock.video(100_050_000))
        assertEquals(50_000L, clock.audio(50_000))
    }
    @Test fun negativeAudioPrimingCannotProduceInvalidOrRepeatedMuxerTimestamps() {
        val clock = RecordingTimestamps(100_000_000)
        assertEquals(0L, clock.audio(-40_000))
        assertEquals(1L, clock.audio(-20_000))
        assertEquals(2L, clock.audio(0))
        assertEquals(20_000L, clock.audio(20_000))
    }
    @Test fun vendorRelativeVideoTimestampStartsAtZero() {
        val clock = RecordingTimestamps(100_000_000)
        assertEquals(0L, clock.video(1000))
        assertEquals(1000L, clock.video(2000))
    }
    @Test fun repeatedVideoTimestampStaysMonotonic() {
        val clock = RecordingTimestamps(100_000_000)
        assertEquals(0L, clock.video(100_000_000))
        assertEquals(1L, clock.video(100_000_000))
    }
}
