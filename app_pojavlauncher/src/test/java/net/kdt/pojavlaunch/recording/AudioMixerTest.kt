package net.kdt.pojavlaunch.recording

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioMixerTest {
    private fun mixed(game: ShortArray, mic: ShortArray): ShortArray {
        val bytes = ByteBuffer.allocate(mic.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        AudioMixer.mix(game, mic, mic.size, bytes)
        bytes.flip()
        return ShortArray(mic.size * 2) { bytes.short }
    }
    @Test fun mixesBothSourcesAndCentersMonoMicrophone() {
        assertArrayEquals(shortArrayOf(6000, 2000), mixed(shortArrayOf(10000, 2000), shortArrayOf(2000)))
    }
    @Test fun fullScaleSourcesDoNotOverflowOrClip() {
        assertArrayEquals(shortArrayOf(32767, 32767, -32768, -32768),
            mixed(shortArrayOf(32767, 32767, -32768, -32768), shortArrayOf(32767, -32768)))
    }
    @Test fun silenceInOneSourceDoesNotDiscardOtherSource() {
        assertArrayEquals(shortArrayOf(5000, -5000), mixed(shortArrayOf(10000, -10000), shortArrayOf(0)))
        assertArrayEquals(shortArrayOf(5000, 5000), mixed(shortArrayOf(0, 0), shortArrayOf(10000)))
    }
    @Test fun oppositePhaseSignalsCancelWithoutIntegerOverflow() {
        assertArrayEquals(shortArrayOf(0, 0), mixed(shortArrayOf(12000, 12000), shortArrayOf(-12000)))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUndersizedOutputBuffer() {
        AudioMixer.mix(shortArrayOf(1, 2), shortArrayOf(3), 1, ByteBuffer.allocate(2))
    }
    @Test fun gameOnlyPreservesStereoAtFullVolumeAndNeedsNoMicrophoneBuffer() {
        val output = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        AudioMixer.mix(shortArrayOf(12000, -8000, 32767, -32768), shortArrayOf(), 2, output, RecordingOptions.AudioSource.GAME)
        output.flip()
        assertArrayEquals(shortArrayOf(12000, -8000, 32767, -32768), ShortArray(4) { output.short })
    }
    @Test fun microphoneOnlyPreservesFullVolumeAndNeedsNoGameBuffer() {
        val output = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        AudioMixer.mix(shortArrayOf(), shortArrayOf(12000, -32768), 2, output, RecordingOptions.AudioSource.MICROPHONE)
        output.flip()
        assertArrayEquals(shortArrayOf(12000, 12000, -32768, -32768), ShortArray(4) { output.short })
    }
    @Test fun sourcePolicyDoesNotEnableUnselectedHardware() {
        assertTrue(RecordingOptions.AudioSource.GAME.game)
        assertFalse(RecordingOptions.AudioSource.GAME.microphone)
        assertFalse(RecordingOptions.AudioSource.MICROPHONE.game)
        assertTrue(RecordingOptions.AudioSource.MICROPHONE.microphone)
        assertTrue(RecordingOptions.AudioSource.BOTH.game && RecordingOptions.AudioSource.BOTH.microphone)
        assertEquals(RecordingOptions.AudioSource.BOTH, RecordingOptions.AudioSource.saved("invalid"))
    }

}
