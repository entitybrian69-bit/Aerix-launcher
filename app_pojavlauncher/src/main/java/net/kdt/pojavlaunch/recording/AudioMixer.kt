package net.kdt.pojavlaunch.recording

/** 50/50 game + microphone mix; mono mic is centered in the stereo game track. */
internal object AudioMixer {
    fun mix(game: ShortArray, mic: ShortArray, frames: Int, output: java.nio.ByteBuffer) {
        require(frames >= 0 && frames * 2 <= game.size && frames <= mic.size)
        require(output.remaining() >= frames * 4)
        output.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until frames) {
            output.putShort(((game[frame * 2].toInt() + mic[frame].toInt()) / 2).toShort())
            output.putShort(((game[frame * 2 + 1].toInt() + mic[frame].toInt()) / 2).toShort())
        }
    }
}
