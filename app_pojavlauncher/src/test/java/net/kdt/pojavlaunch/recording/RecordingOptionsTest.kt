package net.kdt.pojavlaunch.recording

import org.junit.Assert.*
import org.junit.Test

class RecordingOptionsTest {
    @Test fun exposesRequestedIndependentQualityAndFpsRanges() {
        assertEquals(listOf(240, 360, 480, 720, 1080), RecordingOptions.qualities)
        assertEquals(listOf(15, 24, 30, 48, 60, 90, 120), RecordingOptions.frameRates)
        assertEquals(480, RecordingOptions.DEFAULT_QUALITY)
        assertEquals(24, RecordingOptions.DEFAULT_FPS)
    }
    @Test fun landscapeUsesShortEdgeWithoutStretchingToFixedSixteenNine() {
        assertEquals(RecordingOptions.Size(1066, 480), RecordingOptions.size(2400, 1080, 480, 2, 2))
    }
    @Test fun portraitKeepsPortraitAspect() {
        assertEquals(RecordingOptions.Size(480, 1066), RecordingOptions.size(1080, 2400, 480, 2, 2))
    }
    @Test fun allResolutionsRespectCodecAlignment() {
        RecordingOptions.qualities.forEach { q ->
            val size = RecordingOptions.size(2340, 1080, q, 16, 8)
            assertEquals(0, size.width % 16)
            assertEquals(0, size.height % 8)
            assertTrue(size.width > size.height)
            assertTrue(size.height <= q)
        }
    }
    @Test fun bitrateScalesWithQualityAndFrameRateButHasSafeBounds() {
        assertTrue(RecordingOptions.bitrate(1920, 1080, 60) > RecordingOptions.bitrate(1920, 1080, 30))
        assertTrue(RecordingOptions.bitrate(1920, 1080, 30) > RecordingOptions.bitrate(854, 480, 30))
        assertEquals(300_000, RecordingOptions.bitrate(2, 2, 15))
        assertEquals(40_000_000, RecordingOptions.bitrate(7680, 4320, 120))
    }
    @Test fun rejectsSoftwareAndUnreportedUnknownEncoders() {
        assertFalse(RecordingOptions.hardware("OMX.google.h264.encoder", null, null))
        assertFalse(RecordingOptions.hardware("c2.android.avc.encoder", null, null))
        assertFalse(RecordingOptions.hardware("vendor.software.encoder", true, false))
        assertFalse(RecordingOptions.hardware("vendor.encoder", false, false))
        assertFalse(RecordingOptions.hardware("vendor.encoder", true, true))
        assertFalse(RecordingOptions.hardware("unknown", null, null))
        assertFalse(RecordingOptions.hardware("OMX.SEC.avc.sw.enc", null, null))
        assertFalse(RecordingOptions.hardware("OMX.ffmpeg.avc.encoder", null, null))
    }
    @Test fun acceptsVendorHardwareAndConservativeLegacyNames() {
        assertTrue(RecordingOptions.hardware("c2.qti.avc.encoder", true, false))
        assertTrue(RecordingOptions.hardware("OMX.Exynos.AVC.Encoder", null, null))
        assertTrue(RecordingOptions.hardware("vendor.encoder", true, false))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsZeroScreenSize() {
        RecordingOptions.size(0, 1080, 480, 2, 2)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownQuality() {
        RecordingOptions.size(1920, 1080, 1440, 2, 2)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsZeroAlignment() {
        RecordingOptions.size(1920, 1080, 480, 0, 2)
    }
}
