package net.kdt.pojavlaunch.recording

import android.graphics.Bitmap
import android.media.Image
import android.media.ImageWriter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Produces the encoder's frames from the game's own surface, in `:game`.
 *
 * A `MediaProjection` mirror cannot leave one layer out of what it captures, so the only way to hand
 * over footage without the touch controls in it is to stop capturing the display and capture the game
 * instead. The game's frames live in this process and the codec lives in `:recorder`, so the codec's
 * input `Surface` is sent here over a [Messenger] and produced into directly - buffer handles travel
 * between the processes, pixels do not, which is why this is affordable at 1080p60 where piping raw
 * frames through a socket (about 110 MB/s) would not be.
 *
 * Everything here is best-effort by construction. A device that refuses a surface readback, or whose
 * encoder will not accept a remote producer, must end up with a working (if control-bearing) video
 * rather than a black one, so any failure stops feeding and tells `:recorder`, which then mirrors the
 * display and asks for the controls to be faded. The rules worth getting right - stride arithmetic,
 * pacing, and when to give up - live in [GameCapture], which has no Android in it and is executed by
 * tests/GameCaptureTest.kt.
 *
 * [onFallback] runs on the main looper with the recorder's reason for giving up, which is the only
 * moment the UI is allowed to fade the controls out: until then a clean capture is assumed to be
 * working, and fading early would show a controls-less screen for nothing.
 */
class GameSurfaceFeed(
    private val surfaceProvider: () -> View?,
    private val onFallback: (String) -> Unit = {}
) {
    private val lock = Any()
    private var reply: Messenger? = null
    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private var writer: ImageWriter? = null
    private var source: FrameSource? = null
    private var scratch: Bitmap? = null
    private var pixels: IntArray? = null
    private val running = AtomicBoolean(false)
    private val acked = AtomicBoolean(false)
    private var targetWidth = 0
    private var targetHeight = 0
    private var targetFps = DEFAULT_FPS
    private var graceMs = MIN_GRACE_MS
    private var startedAt = 0L
    private var framesFed = 0L
    private var reportedFallback = false

    /** Whether this process currently believes its frames are being encoded clean, unbelayed by a fallback. */
    @Volatile
    var feeding = false
        private set

    /** Handed to `:recorder` in the start intent; the only channel it has to reach this process. */
    val messenger = Messenger(Handler(Looper.getMainLooper()) { message -> receive(message) })

    /**
     * PixelCopy calls back on whatever looper it is given. It has to be one this thread is not blocked
     * on, which rules out the capture thread.
     */
    private val copyHandler = Handler(Looper.getMainLooper())

    private fun receive(message: Message): Boolean {
        when (message.what) {
            GameCapture.MSG_BEGIN -> {
                synchronized(lock) { reply = message.replyTo }
                val data = message.data
                val surface = readSurface(data)
                val handler = startWorker()
                if (surface == null || handler == null) {
                    report(GameCapture.MSG_BEGIN_FAILED, GameCapture.Unavailable.NO_SURFACE.ordinal)
                    return true
                }
                handler.post {
                    begin(
                        surface,
                        data?.getInt(GameCapture.KEY_WIDTH) ?: 0,
                        data?.getInt(GameCapture.KEY_HEIGHT) ?: 0,
                        data?.getInt(GameCapture.KEY_FPS) ?: DEFAULT_FPS
                    )
                }
            }
            GameCapture.MSG_FALLBACK -> {
                feeding = false
                if (reportedFallback) return true
                reportedFallback = true
                val reason = GameCapture.Unavailable.values().getOrNull(message.arg1)?.reason
                    ?: "the game surface was not usable"
                Log.i(TAG, "Recorder is mirroring the display: $reason")
                runCatching { onFallback(reason) }
            }
            GameCapture.MSG_STOP -> {
                val handler = synchronized(lock) { worker }
                if (handler == null) {
                    report(GameCapture.MSG_STOP_ACK, framesFed.toInt())
                } else {
                    handler.post {
                        stopFeeding()
                        report(GameCapture.MSG_STOP_ACK, framesFed.toInt())
                    }
                }
            }
        }
        return true
    }

    private fun readSurface(data: Bundle?): Surface? {
        if (data == null) return null
        if (Build.VERSION.SDK_INT >= 33) {
            return runCatching { data.getParcelable(GameCapture.KEY_SURFACE, Surface::class.java) }.getOrNull()
        }
        @Suppress("DEPRECATION")
        return runCatching { data.getParcelable<Surface>(GameCapture.KEY_SURFACE) }.getOrNull()
    }

    /** The capture thread is per session, but only ever created once, so a stop/start pair is cheap. */
    private fun startWorker(): Handler? = synchronized(lock) {
        val alive = thread
        if (alive != null && alive.isAlive && worker != null) return@synchronized worker
        val next = HandlerThread(FEED_THREAD)
        runCatching { next.start() }.onFailure {
            Log.w(TAG, "Could not start the capture thread", it)
            return@synchronized null
        }
        thread = next
        Handler(next.looper).also { worker = it }
    }

    private fun begin(surface: Surface, width: Int, height: Int, fps: Int) {
        if (!GameCapture.shouldUseGameSurface(true, width, height)) {
            fail(GameCapture.Unavailable.TOO_SMALL)
            return
        }
        // Two different reasons, worth keeping apart in the log: a game with no surface yet (usually
        // momentary, and the recorder's own timeout will have already switched to mirroring) and a render
        // path whose frames this process simply cannot read.
        val gameView = surfaceProvider()
        if (gameView == null) {
            fail(GameCapture.Unavailable.NO_SURFACE)
            return
        }
        val frameSource = selectSource(gameView)
        if (frameSource == null) {
            fail(GameCapture.Unavailable.VIEW_TYPE)
            return
        }
        targetWidth = width
        targetHeight = height
        targetFps = fps
        graceMs = GameCapture.startupGraceMs(width, height, fps)
        startedAt = System.nanoTime() / 1_000_000L
        framesFed = 0L
        acked.set(false)
        reportedFallback = false
        source = frameSource
        // The two-argument overload is the one the SDK exposes for a Surface producer: there is no public
        // way to ask ImageWriter for a size or a format, because the surface's own buffer geometry decides
        // both. So nothing below may assume the encoder's width and height - the loop reads the geometry
        // off each Image and verifies the plane is a single packed RGBA row.
        val imageWriter = runCatching { ImageWriter.newInstance(surface, MAX_IMAGES) }.getOrNull()
        if (imageWriter == null) {
            // Not being able to become a producer for this codec's input surface is a device property, not
            // something a retry fixes, so say so once and let the recorder mirror instead.
            stopFeeding()
            report(GameCapture.MSG_BEGIN_FAILED, GameCapture.Unavailable.WRITER_FAILED.ordinal)
            return
        }
        writer = imageWriter
        running.set(true)
        feeding = true
        Log.i(TAG, "Feeding ${width}x$height at $fps fps from the game surface")
        postFrame(0L)
    }

    /**
     * Which readback a render path allows. A `TextureView` owns its `SurfaceTexture` and can be asked for
     * a scaled bitmap directly; a `SurfaceView`'s frames are composited outside the view hierarchy, so
     * they have to be copied out of the surface, which some devices refuse.
     */
    private fun selectSource(view: View?): FrameSource? = when {
        view is TextureView -> TextureSource(view)
        view is SurfaceView && Build.VERSION.SDK_INT >= 24 -> PixelCopySource(view)
        else -> null
    }

    /** One frame of work: take a buffer, fill it, hand it to the codec, then pace the next one. */
    private fun pump() {
        if (!running.get()) return
        val imageWriter = writer
        if (imageWriter == null) {
            stopFeeding()
            return
        }
        val frameStart = System.nanoTime()
        val image = runCatching { imageWriter.acquireNextImage() }.getOrNull()
        if (image == null) {
            // Every buffer is still with the codec. Dropping this frame is the right answer; blocking
            // here would stall the render thread that is waiting for a buffer of its own - and retrying
            // immediately would turn back-pressure into a spin, so the retry keeps the frame cadence.
            if (!gaveUp()) postFrame(GameCapture.nextDelayMs(targetFps, 0L))
            return
        }
        // The image, not the request, fixes the size: that is what the surface says the codec will
        // accept, and a bitmap of any other size would be stretched rather than scaled.
        val frameWidth = image.width.takeIf { it > 0 } ?: targetWidth
        val frameHeight = image.height.takeIf { it > 0 } ?: targetHeight
        val bitmap = stagedBitmap(frameWidth, frameHeight)
        if (bitmap == null) {
            runCatching { image.close() }
            fail(GameCapture.Unavailable.TOO_SMALL)
            return
        }
        val captured = runCatching { source?.capture(frameWidth, frameHeight, bitmap) }.getOrNull()
        if (captured == null || captured.isRecycled ||
            captured.width != frameWidth || captured.height != frameHeight
        ) {
            recycle(captured, bitmap)
            runCatching { image.close() }
            if (!gaveUp()) postFrame(GameCapture.nextDelayMs(targetFps, 0L))
            return
        }
        val failure = writeFrame(image, captured)
        recycle(captured, bitmap)
        if (failure != null) {
            runCatching { image.close() }
            fail(failure)
            return
        }
        // Queueing hands the buffer to the codec, and with it the image: closing it here instead would
        // return an unwritten buffer, which is a dropped frame that looks like a good one.
        if (!runCatching { imageWriter.queueInputImage(image) }.isSuccess) {
            runCatching { image.close() }
            fail(GameCapture.Unavailable.WRITER_FAILED)
            return
        }
        framesFed++
        if (acked.compareAndSet(false, true)) report(GameCapture.MSG_BEGIN_ACK, 1)
        postFrame(GameCapture.nextDelayMs(targetFps, System.nanoTime() - frameStart))
    }

    /**
     * Nothing has reached the codec yet. [GameCapture.decide] owns that rule - a game behind a loading
     * screen legitimately draws nothing for a while - and giving up means the recorder mirrors the
     * display for this session instead of encoding a black file.
     */
    private fun gaveUp(): Boolean {
        if (!running.get()) return true
        if (GameCapture.decide(framesFed, elapsedMs(), graceMs) != GameCapture.Feed.GIVE_UP) return false
        fail(GameCapture.Unavailable.NO_FRAMES)
        return true
    }

    /** Copies one frame into one codec buffer. Null means the frame is in; otherwise, why not. */
    private fun writeFrame(image: Image, bitmap: Bitmap): GameCapture.Unavailable? {
        // One plane with a pixel stride of 4 is a packed RGBA_8888 row. Several planes mean the codec
        // asked for planar (YUV) input, which cannot be written without a colour conversion.
        val plane = if (image.planes.size == 1) image.planes[0] else null
            ?: return GameCapture.Unavailable.STRIDE_MISMATCH
        val buffer = plane.buffer ?: return GameCapture.Unavailable.WRITER_FAILED
        if (plane.pixelStride != GameCapture.BYTES_PER_PIXEL ||
            plane.rowStride % GameCapture.BYTES_PER_PIXEL != 0 ||
            bitmap.rowBytes % GameCapture.BYTES_PER_PIXEL != 0
        ) {
            return GameCapture.Unavailable.STRIDE_MISMATCH
        }
        val rowBytes = bitmap.width * GameCapture.BYTES_PER_PIXEL
        val plan = GameCapture.planCopy(bitmap.width, bitmap.height, bitmap.rowBytes, plane.rowStride)
            ?: return GameCapture.Unavailable.STRIDE_MISMATCH
        if (buffer.capacity().toLong() < plane.rowStride.toLong() * (bitmap.height - 1) + rowBytes) {
            return GameCapture.Unavailable.STRIDE_MISMATCH
        }
        if (GameCapture.isBulkCopy(plan)) {
            buffer.position(0)
            buffer.limit(buffer.capacity())
            return if (runCatching { bitmap.copyPixelsToBuffer(buffer) }.isSuccess) {
                stamp(image)
            } else {
                GameCapture.Unavailable.WRITER_FAILED
            }
        }
        val wordsPerRow = bitmap.rowBytes / GameCapture.BYTES_PER_PIXEL
        val rowWords = ensurePixels(wordsPerRow * bitmap.height) ?: return GameCapture.Unavailable.STRIDE_MISMATCH
        val written = runCatching {
            bitmap.getPixels(rowWords, wordsPerRow, 0, 0, 0, bitmap.width, bitmap.height)
            val words = buffer.asIntBuffer()
            for (row in plan) {
                words.position(row.dstOffset / GameCapture.BYTES_PER_PIXEL)
                words.put(rowWords, row.srcOffset, row.pixels)
            }
            true
        }.getOrDefault(false)
        if (!written) return GameCapture.Unavailable.STRIDE_MISMATCH
        return stamp(image)
    }

    /**
     * The codec timestamps whatever a producer queues, so a hand-fed frame has to carry this process's
     * monotonic clock or the file's PTS values and the recorder's muxing offset disagree and audio
     * drifts. `System.nanoTime()` is the system-wide monotonic clock, which is what the recorder based
     * its own epoch on, so the two processes agree without further negotiation.
     */
    private fun stamp(image: Image): GameCapture.Unavailable? =
        if (runCatching { image.timestamp = System.nanoTime() }.isSuccess) null else GameCapture.Unavailable.WRITER_FAILED

    private fun ensurePixels(size: Int): IntArray? {
        if (size <= 0) return null
        val current = pixels
        if (current != null && current.size >= size) return current
        return runCatching { IntArray(size) }.getOrNull()?.also { pixels = it }
    }

    private fun stagedBitmap(width: Int, height: Int): Bitmap? {
        val current = scratch
        if (current != null && !current.isRecycled && current.width == width && current.height == height) {
            return current
        }
        if (width.toLong() * height > MAX_STAGED_PIXELS) return null
        return runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull()?.also {
            runCatching { current?.recycle() }
            scratch = it
        }
    }

    /** A source may hand back the feed's scratch bitmap or its own; only the latter is ours to free. */
    private fun recycle(captured: Bitmap?, scratch: Bitmap) {
        if (captured != null && captured !== scratch) runCatching { captured.recycle() }
    }

    /** Abandons the staging bitmap without recycling it; see [PixelCopySource] for why that matters. */
    private fun abandonStaging() {
        scratch = null
        pixels = null
    }

    private fun elapsedMs(): Long = System.nanoTime() / 1_000_000L - startedAt

    private fun postFrame(delayMs: Long) {
        val handler = synchronized(lock) { worker } ?: return
        handler.removeCallbacks(frameTask)
        if (delayMs <= 0L) handler.post(frameTask) else handler.postDelayed(frameTask, delayMs)
    }

    private val frameTask = Runnable { pump() }

    private fun fail(reason: GameCapture.Unavailable) {
        Log.w(TAG, "Game surface capture unavailable: ${reason.reason}")
        stopFeeding()
        report(GameCapture.MSG_BEGIN_FAILED, reason.ordinal)
    }

    /**
     * Releases the producer side of the codec's input surface. The recorder waits for [GameCapture
     * .MSG_STOP_ACK] before handing that surface to a `VirtualDisplay`, because a BufferQueue accepts one
     * producer at a time and a stale connection here would turn the fallback into a silent failure.
     */
    private fun stopFeeding() {
        running.set(false)
        feeding = false
        synchronized(lock) { worker }?.removeCallbacks(frameTask)
        runCatching { writer?.close() }
        writer = null
        runCatching { source?.dispose() }
        source = null
    }

    /** Called when the activity goes away. Never touches the codec, which is not ours to touch. */
    fun shutdown() {
        reportedFallback = false
        val handler: Handler?
        val alive: HandlerThread?
        synchronized(lock) {
            handler = worker
            alive = thread
            worker = null
            thread = null
            reply = null
        }
        if (handler != null && alive != null && alive.isAlive) {
            handler.post {
                stopFeeding()
                releaseStaging()
                alive.quitSafely()
            }
        } else {
            stopFeeding()
            releaseStaging()
        }
    }

    private fun releaseStaging() {
        runCatching { scratch?.recycle() }
        scratch = null
        pixels = null
    }

    private fun report(what: Int, arg: Int) {
        val target = synchronized(lock) { reply } ?: return
        val message = Message.obtain(null, what).apply { arg1 = arg }
        runCatching { target.send(message) }
            .onFailure { Log.d(TAG, "Recorder no longer reachable; dropped message $what") }
    }

    /**
     * A way to get one game frame into a bitmap of the size the codec will accept.
     *
     * [reuse] is the feed's own scratch bitmap, which a source is expected to fill and return. Returning
     * a different bitmap is allowed and the feed recycles it, because the `TextureView` path has no way
     * to draw into someone else's bitmap - and going through the view is what makes it scale on the GPU
     * instead of on the CPU.
     */
    private interface FrameSource {
        fun capture(width: Int, height: Int, reuse: Bitmap): Bitmap?
        fun dispose() = Unit
    }

    private inner class TextureSource(private val view: TextureView) : FrameSource {
        override fun capture(width: Int, height: Int, reuse: Bitmap): Bitmap? {
            if (!view.isAvailable) return null
            return runCatching { view.getBitmap(width, height) }.getOrNull()?.takeIf { !it.isRecycled }
        }
    }

    /**
     * Reads the composited layer with `PixelCopy`, the only supported way to see a `SurfaceView`'s
     * frames - the view itself has no public readback, and `getBitmap` on it is a hidden API. The system
     * scales and converts into the destination, so the scratch bitmap is filled at the codec's size.
     *
     * A copy that has not finished may still be writing, so a timed-out destination is abandoned rather
     * than recycled: freeing a buffer the graphics stack still owns is a native crash, and a leaked
     * bitmap is only memory.
     */
    private inner class PixelCopySource(private val view: SurfaceView) : FrameSource {
        override fun capture(width: Int, height: Int, reuse: Bitmap): Bitmap? {
            if (view.width <= 0 || view.height <= 0) return null
            val done = CountDownLatch(1)
            val status = AtomicInteger(INVALID_STATUS)
            val listener = PixelCopy.OnPixelCopyFinishedListener { code ->
                status.set(code)
                done.countDown()
            }
            val requested = runCatching { PixelCopy.request(view, reuse, listener, copyHandler) }.isSuccess
            if (!requested) return null
            if (!runCatching { done.await(COPY_TIMEOUT_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)) {
                abandonStaging()
                return null
            }
            if (status.get() != successCode) {
                // ERROR_SOURCE_NO_DATA just means the game has not queued a frame yet: null tells the
                // loop to try again next interval, and the grace window bounds how long that can go on.
                return null
            }
            return reuse
        }
    }

    /**
     * The platform's own success value. Which constant exists, and what number it holds, changed between
     * releases (`RESULT_SUCCESS` was 1, `SUCCESS` is 0), and guessing is a feature that quietly never
     * works, so the value is read off the running device's class instead of compiled in.
     */
    private val successCode: Int by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val names = runCatching {
            PixelCopy::class.java.fields.associate { field ->
                field.name to runCatching { field.getInt(null) }.getOrDefault(INVALID_STATUS)
            }
        }.getOrDefault(emptyMap())
        GameCapture.pixelCopySuccessCode(names)
    }

    private companion object {
        private const val TAG = "AerixGameFeed"
        private const val FEED_THREAD = "AerixGameFeed"
        private const val MAX_IMAGES = 4
        private const val DEFAULT_FPS = 30
        private const val MIN_GRACE_MS = 1_200L
        private const val INVALID_STATUS = Int.MIN_VALUE

        /** A copy that never finishes is a refusal, not a slow frame; the loop has its own cadence. */
        private const val COPY_TIMEOUT_MS = 600L

        /** Refuses to stage a frame bigger than this; past it the readback costs more than it proves. */
        private const val MAX_STAGED_PIXELS = 10_000_000L
    }
}
