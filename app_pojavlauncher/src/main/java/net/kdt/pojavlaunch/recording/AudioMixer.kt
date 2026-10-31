package net.kdt.pojavlaunch.recording

/** Single sources retain full gain; Both uses a 50/50 mix with the mono mic centered. */
internal object AudioMixer {
    fun mix(
        game: ShortArray,
        mic: ShortArray,
        frames: Int,
        output: java.nio.ByteBuffer,
        source: RecordingOptions.AudioSource = RecordingOptions.AudioSource.GAME
    ) {
        require(frames >= 0 && (!source.game || frames * 2 <= game.size) && (!source.microphone || frames <= mic.size))
        require(output.remaining() >= frames * 4)
        output.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until frames) {
            when (source) {
                RecordingOptions.AudioSource.GAME -> {
                    output.putShort(game[frame * 2])
                    output.putShort(game[frame * 2 + 1])
                }
                RecordingOptions.AudioSource.MICROPHONE -> {
                    output.putShort(mic[frame])
                    output.putShort(mic[frame])
                }
                RecordingOptions.AudioSource.BOTH -> {
                    output.putShort(((game[frame * 2].toInt() + mic[frame].toInt()) / 2).toShort())
                    output.putShort(((game[frame * 2 + 1].toInt() + mic[frame].toInt()) / 2).toShort())
                }
                RecordingOptions.AudioSource.NONE -> {
                    output.putShort(0)
                    output.putShort(0)
                }
            }
        }
    }
}
