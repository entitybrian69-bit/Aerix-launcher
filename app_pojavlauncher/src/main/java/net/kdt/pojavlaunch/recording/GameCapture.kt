package net.kdt.pojavlaunch.recording

/**
 * The rules behind game-surface recording, with no Android in them.
 *
 * Game-surface recording is the only way to leave the on-screen controls out of a video while the
 * player keeps seeing them: the recorder mirrors the composed display, so anything drawn on screen is
 * in that image by definition. Feeding the encoder from the game's own frames instead means pixels
 * copied by hand - a `Bitmap` into a `MediaCodec` input surface through an `ImageWriter` - and the
 * three things that make or break a hand-fed encoder are all arithmetic: where the bytes go (stride),
 * how fast they may go (pacing), and when to give up and mirror instead (the fallback policy). Those
 * live here so they can be tested with no device, and the Android side stays plumbing.
 */
object GameCapture {
    // --- IPC between `:game` (which owns the game's surface) and `:recorder` (which owns MediaCodec)
    // A `Surface` is Parcelable, so the encoder's input surface can be handed across the process
    // boundary and produced from the game side - the same producer/consumer split a camera app uses.
    const val MSG_BEGIN = 1     // :recorder -> :game, "here is the surface to draw into"
    const val MSG_BEGIN_ACK = 2 // :game -> :recorder, "frames are coming" (arg1 = frames seen so far)
    const val MSG_BEGIN_FAILED = 3 // :game -> :recorder, "this device will not give us frames" (arg1 = reason)
    const val MSG_STOP = 4      // :recorder -> :game, "stop producing"
    const val MSG_STOP_ACK = 5  // :game -> :recorder, "producer released"
    const val MSG_FALLBACK = 6  // :recorder -> :game, "we are mirroring the display after all" (arg1 = reason)

    /** Intent extras on the recorder's START action, for the one thing the game process must supply. */
    const val EXTRA_FEED = "feedMessenger"
    const val EXTRA_CLEAN_VIDEO = "cleanVideo"

    /**
     * A `Surface` is Parcelable so the encoder's input surface can be handed to the process that owns
     * the game's frames; the reply messenger is how the game says "frames are coming" or "this device
     * will not give me pixels", which is the difference between a clean video and a black one.
     */
    const val KEY_SURFACE = "surface"
    const val KEY_WIDTH = "width"
    const val KEY_HEIGHT = "height"
    const val KEY_FPS = "fps"

    /** Why a session is not using the game's frames, reported in the log and the settings row. */
    enum class Unavailable(val reason: String) {
        NO_SURFACE("the game is not drawing to a capturable surface"),
        TOO_SMALL("the game surface is too small to record"),
        STRIDE_MISMATCH("this device's encoder buffers cannot be written row-by-row"),
        WRITER_FAILED("the frame writer could not be created on this device"),
        NO_FRAMES("the game surface never produced a frame"),
        VIEW_TYPE("this render path cannot be read back directly")
    }

    /** How many bytes one pixel of the RGBA buffer occupies. */
    const val BYTES_PER_PIXEL = 4

    /**
     * One row of a `Bitmap` copied into an `Image` plane.
     *
     * [srcOffset] is in `Int`s (a bitmap pixel array), [dstOffset] in bytes (the plane buffer) -
     * mixing them up is the classic way to get a video that is sheared diagonally.
     */
    data class RowCopy(val srcOffset: Int, val dstOffset: Int, val pixels: Int)

    /**
     * Whether the captured bitmap can be handed to the plane buffer in one bulk copy, or has to be
     * walked row by row because the encoder's stride is wider than the bitmap's.
     *
     * `null` means the geometry cannot be expressed at all - the caller falls back to mirroring the
     * display rather than writing a partial row past the end of a buffer.
     */
    @JvmStatic
    fun planCopy(width: Int, height: Int, bitmapRowBytes: Int, imageRowStride: Int): List<RowCopy>? {
        if (width <= 0 || height <= 0) return null
        val rowBytes = width * BYTES_PER_PIXEL
        if (rowBytes > bitmapRowBytes || rowBytes > imageRowStride) return null
        if (bitmapRowBytes == imageRowStride) {
            // Strides agree: one contiguous copy of the whole image.
            return listOf(RowCopy(0, 0, width * height))
        }
        return (0 until height).map { row ->
            RowCopy(row * (bitmapRowBytes / BYTES_PER_PIXEL), row * imageRowStride, width)
        }
    }

    /** True when a bulk copy was planned, i.e. no per-row work will happen at 30-60 fps. */
    @JvmStatic
    fun isBulkCopy(plan: List<RowCopy>): Boolean = plan.size == 1 && plan[0].pixels > 0

    /**
     * The delay before the next frame, given how long this one took.
     *
     * Never negative and never "catch up": a recorder that tries to make up dropped frames starves the
     * game's render thread, which is the one thing a capture must not do. Overrun is absorbed as a
     * dropped frame instead of a burst.
     */
    @JvmStatic
    fun nextDelayMs(targetFps: Int, frameNanos: Long): Long {
        val frameUs = if (targetFps > 0) 1_000_000L / targetFps else 33_333L
        val spentMs = frameNanos / 1_000_000L
        return (frameUs / 1000L - spentMs).coerceAtLeast(0L)
    }

    /** Where a hand-fed session is at, from the game side's point of view. */
    enum class Feed { FEEDING, WAITING_FOR_FIRST_FRAME, GIVE_UP }

    /**
     * The give-up decision, in one place so it can be argued about and tested rather than buried in
     * callback spaghetti: frames flowing means keep going; nothing flowing while still inside the
     * grace period means the game may simply be paused behind a loading screen; nothing flowing past
     * it means this render path will not hand us pixels, and mirroring the display is strictly better
     * than recording a black file.
     */
    @JvmStatic
    fun decide(framesFed: Long, elapsedMs: Long, graceMs: Long): Feed = when {
        framesFed > 0 -> Feed.FEEDING
        elapsedMs < graceMs -> Feed.WAITING_FOR_FIRST_FRAME
        else -> Feed.GIVE_UP
    }

    /**
     * The grace period before giving up, scaled to the resolution: a 1080p readback on a slow device
     * takes a few frames to get going, and bailing early would quietly downgrade a session that was
     * about to work.
     */
    @JvmStatic
    fun startupGraceMs(width: Int, height: Int, fps: Int): Long {
        val pixels = width.toLong() * height.toLong()
        val scaled = 1_200L + pixels / 400_000L * 100L
        val slowFpsBonus = if (fps in 1..24) 600L else 0L
        return (scaled + slowFpsBonus).coerceIn(1_200L, 6_000L)
    }

    /** Returned by [pixelCopySuccessCode] when the platform has no recognisable status constant. */
    const val NO_SUCCESS_CODE = Int.MIN_VALUE

    /**
     * Which of `PixelCopy`'s status values means "the frame was copied".
     *
     * The name and the number both changed between Android releases - `RESULT_SUCCESS` was 1, `SUCCESS`
     * is 0 - and a session compiled against the wrong one would read every successful copy as a refusal,
     * which is a feature that quietly never works. So the value is taken from the running device's own
     * class rather than compiled in, and if it cannot be named at all the answer is a code that matches
     * nothing: giving up and mirroring the display is a worse video, but a black one is a broken one.
     */
    @JvmStatic
    fun pixelCopySuccessCode(valuesByName: Map<String, Int>): Int =
        valuesByName["SUCCESS"] ?: valuesByName["RESULT_SUCCESS"] ?: NO_SUCCESS_CODE

    /**
     * Whether the render path in use is the one that can be read back without argument.
     *
     * A `TextureView` owns a `SurfaceTexture`, and asking it for a bitmap is its documented purpose. A
     * `SurfaceView`'s frames may be composited by the display hardware instead of ever being copied into
     * memory the app can reach, so whether `getBitmap` succeeds is a device property. This only chooses
     * what the settings row promises the player, never what a session does - the recorder finds out by
     * trying, and falls back if the answer is no.
     */
    @JvmStatic
    fun readbackIsDirect(alternateSurface: Boolean): Boolean = !alternateSurface

    /**
     * Whether the encoder should be fed the game surface at all. Deliberately conservative: a mirror
     * recording with controls in it is a disappointment, a black recording is a bug report.
     */
    @JvmStatic
    fun shouldUseGameSurface(requested: Boolean, surfaceWidth: Int, surfaceHeight: Int): Boolean =
        requested && surfaceWidth >= 16 && surfaceHeight >= 16
}
