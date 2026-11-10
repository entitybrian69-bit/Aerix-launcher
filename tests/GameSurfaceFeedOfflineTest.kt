import android.graphics.Bitmap
import android.media.ImageWriterTestConfig
import android.media.PixelCopyOutcome
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.media.QueuedFrame
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import net.kdt.pojavlaunch.recording.GameCapture
import net.kdt.pojavlaunch.recording.GameSurfaceFeed
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections

/**
 * Drives a whole game-surface capture session against test doubles of the Android APIs, so the parts that
 * cannot be eyeballed on a screen are executed instead: which bytes land in which codec buffer, whether a
 * padded row stride is walked correctly, whether the producer lets go of the surface before the recorder
 * is told it may mirror the display, and whether a device that refuses a readback ends up reporting a
 * reason rather than recording black.
 *
 * This is not a substitute for a phone - it cannot prove `SurfaceView.getBitmap` succeeds on any real
 * device, which is exactly why the fallback path exists and is asserted here too.
 */
private var checks = 0

private fun expect(condition: Boolean, what: String) {
    checks++
    if (!condition) {
        // Without the feed's own log lines a failure here is indistinguishable from a harness bug.
        Log.lines.takeLast(14).forEach { System.err.println("  log: $it") }
        throw AssertionError("game surface feed: $what")
    }
}

private fun expectEquals(actual: Any?, expected: Any?, what: String) {
    expect(actual == expected, "$what (expected $expected, got $actual)")
}

/** The recorder's side of the handshake, as the feed sees it. */
private class Recorder {
    @Volatile var lastWhat = 0
    @Volatile var lastArg = -1
    @Volatile var writersClosedAtAck = -1
    val seen = Collections.synchronizedList(ArrayList<Int>())
    val messenger: Messenger

    init {
        val handler = Handler(Looper.getMainLooper()) { message ->
            lastWhat = message.what
            lastArg = message.arg1
            seen.add(message.what)
            if (message.what == GameCapture.MSG_STOP_ACK) writersClosedAtAck = ImageWriterTestConfig.closed
            true
        }
        messenger = Messenger(handler)
    }

    fun await(what: Int, millis: Long): Boolean {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            if (lastWhat == what) return true
            Thread.sleep(3)
        }
        return lastWhat == what
    }
}

private fun awaitUntil(millis: Long, predicate: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + millis
    while (System.currentTimeMillis() < deadline) {
        if (predicate()) return true
        Thread.sleep(3)
    }
    return predicate()
}

private fun begin(feed: GameSurfaceFeed, recorder: Recorder, surface: Surface, width: Int, height: Int, fps: Int) {
    val message = Message.obtain(null, GameCapture.MSG_BEGIN).apply {
        replyTo = recorder.messenger
        data = Bundle().apply {
            putParcelable(GameCapture.KEY_SURFACE, surface)
            putInt(GameCapture.KEY_WIDTH, width)
            putInt(GameCapture.KEY_HEIGHT, height)
            putInt(GameCapture.KEY_FPS, fps)
        }
    }
    feed.messenger.send(message)
}

private fun expected(x: Int, y: Int) = (y shl 16) or (x shl 8) or 0xAA

private fun frameAt(frame: QueuedFrame, x: Int, y: Int): Int {
    val words = frame.bytes.size / 4
    val perRow = frame.rowStride / 4
    val index = y * perRow + x
    if (index >= words) return -1
    return ByteBuffer.wrap(frame.bytes).order(ByteOrder.nativeOrder()).getInt(index * 4)
}

private fun writerOf(feed: GameSurfaceFeed): android.media.ImageWriter? = runCatching {
    val field = GameSurfaceFeed::class.java.getDeclaredField("writer")
    field.isAccessible = true
    field.get(feed) as android.media.ImageWriter?
}.getOrNull()

private fun framesOf(feed: GameSurfaceFeed): List<QueuedFrame> =
    writerOf(feed)?.queued.orEmpty()

fun main() {
    // 0. The SurfaceView happy path goes through PixelCopy at the surface's own size.
    ImageWriterTestConfig.reset()
    PixelCopyOutcome.reset()
    run {
        val view = SurfaceView().apply { width = 96; height = 64 }
        val feed = GameSurfaceFeed({ view })
        val recorder = Recorder()
        begin(feed, recorder, Surface(), 96, 64, 30)
        expect(recorder.await(GameCapture.MSG_BEGIN_ACK, 4_000L), "a SurfaceView session must be feedable")
        expect(awaitUntil(2_000L) { framesOf(feed).isNotEmpty() }, "the copied frame must be queued")
        val frame = framesOf(feed).first()
        expect(frameAt(frame, 0, 0) == expected(0, 0), "pixel 0,0 must survive the copy")
        expect(frameAt(frame, 95, 63) == expected(95, 63), "the bottom-right pixel must survive the copy")
        expect(PixelCopyOutcome.requests >= 1, "the readback must go through PixelCopy")
        feed.shutdown()
    }

    // 1. The happy path with matching strides: the whole frame is one bulk copy, and every pixel that
    //    reaches the codec must be the pixel the game drew - nothing of the control layer is in it
    //    because the control layer was never in this bitmap to begin with.
    ImageWriterTestConfig.reset()
    run {
        val view = TextureView().apply { width = 1280; height = 720 }
        val feed = GameSurfaceFeed({ view })
        val recorder = Recorder()
        val surface = Surface()
        begin(feed, recorder, surface, 640, 360, 30)
        expect(recorder.await(GameCapture.MSG_BEGIN_ACK, 4_000L), "the game must confirm the first frame")
        expect(awaitUntil(2_000L) { framesOf(feed).size >= 3 }, "at least three frames must reach the codec")
        val frames = framesOf(feed)
        expect(frames.isNotEmpty(), "frames must be queued")
        expect(ImageWriterTestConfig.created == 1, "exactly one producer may attach, got ${ImageWriterTestConfig.created}")
        expectEquals(ImageWriterTestConfig.sizes.firstOrNull(), "640x360/4",
            "the writer must be created for the encoder's size with a small buffer pool")
        frames.forEachIndexed { position, frame ->
            expectEquals(frame.width, 640, "frame $position width")
            expectEquals(frame.height, 360, "frame $position height")
            expect(frameAt(frame, 0, 0) == expected(0, 0), "frame $position must start with pixel 0,0")
            expect(frameAt(frame, 639, 359) == expected(639, 359), "frame $position bottom-right pixel is wrong")
            expect(frameAt(frame, 320, 180) == expected(320, 180), "frame $position centre pixel is wrong")
            expect(frame.timestamp > 0L, "a hand-fed frame must carry a timestamp")
        }
        val stamps = frames.map { it.timestamp }
        expect(stamps.zipWithNext().all { (a, b) -> b >= a }, "timestamps must never go backwards")
        expect(stamps.size < 2 || stamps.last() - stamps.first() > 1_000_000L,
            "timestamps are nanoseconds and must advance by real time between frames")
        // A zero here would make the codec substitute its own arrival time, which drifts against the
        // audio clock that the muxer offsets from the same base.
        expect(stamps.all { it != 0L }, "a frame timestamp must be set, not left to the producer")
        expect(feed.feeding, "the feed reports it is producing")
        expect(recorder.seen.count { it == GameCapture.MSG_BEGIN_FAILED } == 0,
            "a working session must not report a failure")
        feed.shutdown()
    }

    // 2. A padded stride (TextureView), which is the case that smears the picture diagonally if the row math is wrong.
    ImageWriterTestConfig.reset()
    run {
        // 64-byte rows with 4 bytes of tail padding: not a bulk copy, so the row walk has to run.
        ImageWriterTestConfig.rowStride = 64 * 4 + 16
        val view = TextureView().apply { width = 64; height = 32 }
        val feed = GameSurfaceFeed({ view })
        val recorder = Recorder()
        begin(feed, recorder, Surface(), 64, 32, 30)
        expect(recorder.await(GameCapture.MSG_BEGIN_ACK, 4_000L), "a padded stride must still be feedable")
        expect(awaitUntil(2_000L) { framesOf(feed).isNotEmpty() }, "a padded frame must be queued")
        val frame = framesOf(feed).first()
        expectEquals(frame.rowStride, 64 * 4 + 16, "the writer must keep the encoder's stride")
        var worst = -1
        for (y in 0 until 32) {
            for (x in 0 until 64) {
                val got = frameAt(frame, x, y)
                if (got != expected(x, y)) worst = y * 1000 + x
            }
        }
        expect(worst == -1, "every pixel of the padded frame must land on its own row (first bad: " +
            "${if (worst < 0) "none" else "x=${worst % 1000}, y=${worst / 1000}"})")
        feed.shutdown()
    }

    // 2b. Below the documented floor the attempt is refused up front, before any writer is created.
    ImageWriterTestConfig.reset()
    run {
        val view = TextureView().apply { width = 64; height = 64 }
        val feed = GameSurfaceFeed({ view })
        val recorder = Recorder()
        begin(feed, recorder, Surface(), 64, 8, 30)
        expect(recorder.await(GameCapture.MSG_BEGIN_FAILED, 2_000L), "a too-small frame must be refused")
        expectEquals(GameCapture.Unavailable.values().getOrNull(recorder.lastArg),
            GameCapture.Unavailable.TOO_SMALL, "the refusal must name the size")
        expectEquals(ImageWriterTestConfig.created, 0, "no writer may be created for a refused geometry")
        feed.shutdown()
    }

    // 3. A surface the encoder will not let us produce into: report the reason, do not hang, do not feed.
    ImageWriterTestConfig.reset()
    run {
        ImageWriterTestConfig.failCreation = true
        val view = TextureView().apply { width = 320; height = 240 }
        val feed = GameSurfaceFeed({ view })
        val recorder = Recorder()
        begin(feed, recorder, Surface(), 320, 240, 30)
        expect(recorder.await(GameCapture.MSG_BEGIN_FAILED, 2_000L), "an unusable writer must be reported")
        expectEquals(GameCapture.Unavailable.values().getOrNull(recorder.lastArg),
            GameCapture.Unavailable.WRITER_FAILED, "the reported reason")
        expect(!feed.feeding, "a failed session must not claim to be producing")
        expect(recorder.lastWhat != GameCapture.MSG_BEGIN_ACK, "a failure must never look like an ack")
        feed.shutdown()
    }

    // 4. A readback the device refuses - the SurfaceView case that makes the fallback mandatory.
    ImageWriterTestConfig.reset()
    PixelCopyOutcome.reset()
    PixelCopyOutcome.mode = "refuse"
    run {
        val view = SurfaceView().apply { width = 800; height = 480 }
        val feed = GameSurfaceFeed({ view })
        val recorder = Recorder()
        begin(feed, recorder, Surface(), 800, 480, 30)
        expect(!recorder.await(GameCapture.MSG_BEGIN_ACK, 300L),
            "no ack may be sent when nothing was ever read")
        expect(recorder.await(GameCapture.MSG_BEGIN_FAILED, 5_000L),
            "a surface that never yields a frame must be reported within the grace window")
        expectEquals(GameCapture.Unavailable.values().getOrNull(recorder.lastArg),
            GameCapture.Unavailable.NO_FRAMES, "the reported reason")
        expect(PixelCopyOutcome.requests > 0, "the readback must actually have been attempted")
        expect(framesOf(feed).isEmpty(), "a refused readback must not queue frames")
        expect(ImageWriterTestConfig.closed >= 1, "the producer must be released after giving up")
        feed.shutdown()
    }

    // 4b. A copy that never calls back: the destination must be abandoned, never recycled, because the
    //     driver may still be writing into it - that is a native crash rather than a dropped frame.
    ImageWriterTestConfig.reset()
    PixelCopyOutcome.reset()
    PixelCopyOutcome.mode = "hang"
    run {
        val view = SurfaceView().apply { width = 320; height = 240 }
        val feed = GameSurfaceFeed({ view })
        val recorder = Recorder()
        val recycledBefore = Bitmap.recycleCount
        begin(feed, recorder, Surface(), 320, 240, 30)
        expect(recorder.await(GameCapture.MSG_BEGIN_FAILED, 8_000L),
            "a hanging copy still has to end the attempt inside a bounded time")
        expectEquals(Bitmap.recycleCount, recycledBefore,
            "a bitmap an unfinished copy may still write into must not be recycled")
        expect(PixelCopyOutcome.requests >= 1, "the copy must have been requested before waiting on it")
        feed.shutdown()
    }

    // 5. A render path this cannot read at all (no view yet, e.g. a menu-only activity) is a clean refusal.
    ImageWriterTestConfig.reset()
    run {
        val feed = GameSurfaceFeed({ null })
        val recorder = Recorder()
        begin(feed, recorder, Surface(), 640, 360, 30)
        expect(recorder.await(GameCapture.MSG_BEGIN_FAILED, 2_000L), "no view must be reported, not stuck")
        expectEquals(GameCapture.Unavailable.values().getOrNull(recorder.lastArg),
            GameCapture.Unavailable.NO_SURFACE, "a missing view is reported as such")
        feed.shutdown()
        val otherPath = GameSurfaceFeed({ object : View() {} })
        val otherRecorder = Recorder()
        begin(otherPath, otherRecorder, Surface(), 640, 360, 30)
        expect(otherRecorder.await(GameCapture.MSG_BEGIN_FAILED, 2_000L), "an unknown view type must be reported")
        expectEquals(GameCapture.Unavailable.values().getOrNull(otherRecorder.lastArg),
            GameCapture.Unavailable.VIEW_TYPE, "an unreadable view type is named")
        otherPath.shutdown()
    }

    // 6. Stop ordering, which is what makes the fallback work at all: the recorder must not be told the
    //    producer is released before the ImageWriter has actually let go of the surface.
    ImageWriterTestConfig.reset()
    run {
        val view = TextureView().apply { width = 320; height = 240 }
        val feed = GameSurfaceFeed({ view })
        val recorder = Recorder()
        begin(feed, recorder, Surface(), 320, 240, 30)
        expect(recorder.await(GameCapture.MSG_BEGIN_ACK, 4_000L), "the session must be producing first")
        expect(awaitUntil(1_000L) { framesOf(feed).size >= 2 }, "two frames should be enough")
        val before = framesOf(feed).size
        Thread.sleep(120)
        val mid = framesOf(feed).size
        expect(mid >= before, "frames must keep flowing while a session runs")
        feed.messenger.send(Message.obtain(null, GameCapture.MSG_STOP))
        expect(recorder.await(GameCapture.MSG_STOP_ACK, 3_000L), "a stop must be acknowledged")
        expect(recorder.writersClosedAtAck >= 1,
            "the writer must be closed before the ack, or the display cannot take the surface over")
        expect(!feed.feeding, "a stopped feed must not claim to be producing")
        val settled = framesOf(feed).size
        Thread.sleep(200)
        expectEquals(framesOf(feed).size, settled, "no frame may be produced after the stop ack")
        feed.shutdown()
    }

    // 7. Back-pressure: when every buffer is with the codec the loop drops frames at the frame cadence
    //    instead of spinning, and gives up on a session that never got going.
    ImageWriterTestConfig.reset()
    run {
        ImageWriterTestConfig.holdAllBuffers = true
        ImageWriterTestConfig.maxImages = 1
        val view = TextureView().apply { width = 96; height = 64 }
        val feed = GameSurfaceFeed({ view })
        val recorder = Recorder()
        begin(feed, recorder, Surface(), 96, 64, 15)
        expect(recorder.await(GameCapture.MSG_BEGIN_FAILED, 5_000L),
            "a writer that never yields a buffer must end the attempt")
        expect(framesOf(feed).isEmpty(), "nothing can be queued while the codec holds every buffer")
        feed.shutdown()
    }

    // 8. A paused game (view not available) keeps the session alive: it must neither ack-fail instantly nor
    //    busy-spin, and the fallback only arrives after the grace window.
    ImageWriterTestConfig.reset()
    run {
        val view = TextureView().apply { width = 160; height = 120; isAvailable = false }
        val feed = GameSurfaceFeed({ view })
        val recorder = Recorder()
        begin(feed, recorder, Surface(), 160, 120, 30)
        Thread.sleep(250)
        expectEquals(recorder.lastWhat, 0, "a briefly unavailable surface must not be reported yet")
        expect(recorder.await(GameCapture.MSG_BEGIN_FAILED, 5_000L), "a permanently unavailable one must be")
        // And when it comes back before the window closes, frames resume.
        view.isAvailable = true
        feed.shutdown()
    }

    // 9. Resizing between sessions reuses the scratch bitmap only when the geometry still matches.
    ImageWriterTestConfig.reset()
    run {
        val view = TextureView().apply { width = 64; height = 64 }
        val feed = GameSurfaceFeed({ view })
        val recorder = Recorder()
        begin(feed, recorder, Surface(), 64, 64, 30)
        expect(recorder.await(GameCapture.MSG_BEGIN_ACK, 4_000L), "the first session must run")
        expect(awaitUntil(1_500L) { framesOf(feed).isNotEmpty() }, "the first session must queue a frame")
        val firstWriter = writerOf(feed)
        expect(firstWriter != null, "the writer must be reachable for this check")
        feed.messenger.send(Message.obtain(null, GameCapture.MSG_STOP))
        expect(recorder.await(GameCapture.MSG_STOP_ACK, 3_000L), "the first session must stop cleanly")
        begin(feed, recorder, Surface(), 32, 32, 30)
        expect(recorder.await(GameCapture.MSG_BEGIN_ACK, 4_000L), "a second session must be able to start")
        expect(awaitUntil(1_500L) { writerOf(feed)?.queued?.isNotEmpty() == true },
            "the second session must produce frames of its own")
        val second = writerOf(feed)
        expect(second !== firstWriter, "each session needs its own producer")
        expectEquals(second?.queued?.last()?.width, 32, "the second session must use the new size")
        expect(ImageWriterTestConfig.closed >= 1, "the first session's writer must be closed")
        feed.shutdown()
    }

    // 10. Shutdown is idempotent and never leaves a thread behind: an activity destroyed twice, or after a
    //     session that never started, must be silent.
    ImageWriterTestConfig.reset()
    run {
        val feed = GameSurfaceFeed({ null })
        feed.shutdown()
        feed.shutdown()
        val recorder = Recorder()
        begin(feed, recorder, Surface(), 640, 360, 30)
        expect(recorder.await(GameCapture.MSG_BEGIN_FAILED, 2_000L), "a shut-down feed must refuse, not crash")
        feed.shutdown()
        expect(Log.lines.any { it.contains("Game surface capture unavailable") },
            "the give-up reason has to be in the log for a bug report to be diagnosable")
    }

    println("game surface feed: $checks checks passed")
}
