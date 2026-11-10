import net.kdt.pojavlaunch.recording.GameCapture
import net.kdt.pojavlaunch.recording.GameCapture.RowCopy

/**
 * Offline checks for the arithmetic that decides whether a game-surface recording is watchable.
 *
 * A stride that is off by one produces a video sheared diagonally, a pacing loop that "catches up"
 * starves the render thread it is supposed to be watching, and a give-up rule that fires too early
 * silently downgrades a session that was working. None of those need a phone to get wrong, and none
 * of them can be seen from a log on a device either, so they are pinned here and run in CI.
 */
private var checks = 0

private fun expect(condition: Boolean, what: String) {
    checks++
    if (!condition) throw AssertionError("game capture: $what")
}

private fun expectEquals(actual: Any?, expected: Any?, what: String) {
    expect(actual == expected, "$what (expected $expected, got $actual)")
}

fun main() {
    // 1. Matching strides: the whole image is one contiguous copy, which is the only way to move
    //    3.7 MB per frame without a row loop on the render thread's side.
    val plan = GameCapture.planCopy(1280, 720, 1280 * 4, 1280 * 4)
    expect(plan != null, "a 720p copy with matching strides must be plannable")
    expectEquals(plan?.size, 1, "matching strides must collapse to a single copy")
    expectEquals(plan?.get(0)?.pixels, 1280 * 720, "the bulk copy must cover every pixel")
    expect(GameCapture.isBulkCopy(plan!!), "a single full-image row is a bulk copy")

    // 2. A padded encoder stride: rows have to be walked, and source and destination advance by
    //    different amounts. Getting either of those wrong is the diagonal-smear bug.
    val stride = 5248
    val padded = GameCapture.planCopy(1280, 720, 5120, stride)
    expect(padded != null, "a padded stride must still be plannable")
    expectEquals(padded?.size, 720, "every row needs its own copy when the stride is padded")
    expectEquals(padded?.get(0)?.dstOffset, 0, "the first row starts at the top of the buffer")
    expectEquals(padded?.get(1)?.dstOffset, stride, "row 1 must start one stride in")
    expectEquals(padded?.get(1)?.srcOffset, 1280, "row 1 must start one bitmap row in, in pixels")
    expectEquals(padded?.last()?.dstOffset, 719 * stride, "the last row must land inside the buffer")
    expect(!GameCapture.isBulkCopy(padded!!), "a row-walked copy must not be reported as bulk")
    expect(
        padded.sumOf { it.pixels } == 1280 * 720,
        "the rows must cover the frame exactly once, got ${padded.sumOf { it.pixels }}"
    )
    expect(
        padded.all { it.dstOffset + it.pixels * GameCapture.BYTES_PER_PIXEL <= 720 * stride },
        "no row may be written past the end of the plane buffer"
    )

    // 3. Geometry that cannot be expressed must be refused, not approximated.
    expect(GameCapture.planCopy(0, 720, 5120, 5120) == null, "a zero-width frame is not plannable")
    expect(GameCapture.planCopy(1280, -1, 5120, 5120) == null, "a negative height is not plannable")
    expect(GameCapture.planCopy(1280, 720, 1280 * 2, 5120) == null, "a bitmap narrower than the row is not plannable")
    expect(GameCapture.planCopy(1280, 720, 5120, 1280) == null, "a stride smaller than a row is not plannable")
    expect(GameCapture.planCopy(1440, 1080, 5760, 5760)?.size == 1, "1080p with matching strides must stay a bulk copy")
    val padded1080 = GameCapture.planCopy(1440, 1080, 5760, 6016)
    expectEquals(padded1080?.size, 1080, "1080p with a padded stride must walk every row")
    expect(
        padded1080 != null && padded1080.windowed(2).all { (a, b) -> b.srcOffset - a.srcOffset == 1440 },
        "the source step must be exactly one bitmap row, or the video smears"
    )

    // 4. Pacing: never negative, never a burst. The render thread is the thing that matters.
    expectEquals(GameCapture.nextDelayMs(30, 0L), 33L, "an instant frame waits a full interval")
    expectEquals(GameCapture.nextDelayMs(60, 0L), 16L, "60fps is a 16ms interval")
    expectEquals(GameCapture.nextDelayMs(30, 40_000_000L), 0L, "an overrun frame must not go negative")
    expect(GameCapture.nextDelayMs(30, 900_000_000L) == 0L, "a huge overrun must still wait zero, not catch up")
    expect(GameCapture.nextDelayMs(0, 0L) == 33L, "an unset frame rate falls back to 30fps pacing")
    expect(GameCapture.nextDelayMs(-5, 12_000_000L) == 21L, "a nonsense frame rate must not stall the loop")

    // 5. Giving up: the difference between a quiet downgrade and a black file.
    expectEquals(GameCapture.decide(0L, 100L, 1_500L), GameCapture.Feed.WAITING_FOR_FIRST_FRAME,
        "a frame that has not arrived inside the grace period is not a failure yet")
    expectEquals(GameCapture.decide(1L, 100L, 1_500L), GameCapture.Feed.FEEDING,
        "one frame is enough to commit to the game surface")
    expectEquals(GameCapture.decide(0L, 1_501L, 1_500L), GameCapture.Feed.GIVE_UP,
        "silence past the grace period means mirror instead")
    expectEquals(GameCapture.decide(9_000L, 300_000L, 1_500L), GameCapture.Feed.FEEDING,
        "a session that was working is never abandoned for being slow")

    // 6. The grace period itself scales with the work the readback has to do.
    expect(GameCapture.startupGraceMs(1280, 720, 30) == 1_400L, "720p30 gets a 1.4s grace, got ${GameCapture.startupGraceMs(1280, 720, 30)}")
    expect(GameCapture.startupGraceMs(3840, 2160, 30) > GameCapture.startupGraceMs(640, 360, 30),
        "a 4K readback must be given more time than a 360p one")
    expect(GameCapture.startupGraceMs(3840, 3840, 15) <= 6_000L, "the grace period must stay bounded")
    expect(GameCapture.startupGraceMs(16, 16, 240) >= 1_200L, "the grace period must have a floor")
    expect(GameCapture.startupGraceMs(1280, 720, 15) > GameCapture.startupGraceMs(1280, 720, 30),
        "a low target frame rate must widen the grace window")

    // 7. The gate in front of all of it: never risk a worse recording for the sake of a clean one.
    expect(!GameCapture.shouldUseGameSurface(false, 1280, 720), "the default path must stay the display mirror")
    expect(GameCapture.shouldUseGameSurface(true, 1280, 720), "an opted-in 720p surface must be used")
    expect(!GameCapture.shouldUseGameSurface(true, 8, 8), "an absurd surface size must refuse, not record garbage")
    expect(!GameCapture.shouldUseGameSurface(true, 0, 0), "no measured size means no game capture")
    expect(GameCapture.shouldUseGameSurface(true, 16, 16), "the documented floor must be accepted")

    // 8. Message ids must stay distinct: a reused code makes two unrelated replies look like each other.
    val codes = listOf(GameCapture.MSG_BEGIN, GameCapture.MSG_BEGIN_ACK, GameCapture.MSG_BEGIN_FAILED,
        GameCapture.MSG_STOP, GameCapture.MSG_STOP_ACK)
    expectEquals(codes.distinct().size, codes.size, "the recorder handshake codes must be unique")
    expect(GameCapture.Unavailable.values().all { it.reason.isNotBlank() },
        "every give-up reason has to be sayable to the player")
    expect(RowCopy(0, 0, 1).pixels == 1, "the row record must stay a plain value type")

    // 9. What the settings row is allowed to promise depends on which render path is drawing the game.
    expect(GameCapture.readbackIsDirect(false), "a TextureView render path reads its own frames back")
    expect(!GameCapture.readbackIsDirect(true), "a SurfaceView render path must never be promised as clean")

    // 10. The PixelCopy status value differs by platform generation, so it is looked up, not assumed.
    expectEquals(GameCapture.pixelCopySuccessCode(mapOf("SUCCESS" to 0, "ERROR_TIMEOUT" to 2)), 0,
        "a platform that names it SUCCESS reports 0")
    expectEquals(GameCapture.pixelCopySuccessCode(mapOf("RESULT_SUCCESS" to 1, "RESULT_ERROR_TIMEOUT" to 2)), 1,
        "an older platform that names it RESULT_SUCCESS reports 1")
    expectEquals(GameCapture.pixelCopySuccessCode(mapOf("SUCCESS" to 0, "RESULT_SUCCESS" to 1)), 0,
        "the current name wins when a platform carries both")
    expectEquals(GameCapture.pixelCopySuccessCode(mapOf("RESULT_ERROR_TIMEOUT" to 2)),
        GameCapture.NO_SUCCESS_CODE, "an unrecognisable PixelCopy must never be read as success")
    expect(GameCapture.pixelCopySuccessCode(emptyMap()) == GameCapture.NO_SUCCESS_CODE,
        "no constants at all must not silently mean zero")
    expect(GameCapture.NO_SUCCESS_CODE != 0 && GameCapture.NO_SUCCESS_CODE != 1,
        "the unknown marker must not collide with either platform's success value")

    println("game capture: $checks checks passed")
}
