/*
 * Test doubles for `android.media.Image` / `ImageWriter`, see AndroidGraphics.kt for why they exist.
 * `ImageWriterTestConfig` is what makes the interesting cases reachable without a device: a padded row
 * stride (the case that needs the row-by-row copy) and a buffer smaller than the frame (the case that
 * must be refused rather than half-written).
 */
package android.media

import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImageWriterTestConfig {
    var rowStride: Int = 0
    var capacityShort: Boolean = false
    var failCreation: Boolean = false
    var holdAllBuffers: Boolean = false
    var maxImages: Int = 4
    var created: Int = 0
    var closed: Int = 0
    val sizes = ArrayList<String>()

    fun reset() {
        rowStride = 0
        capacityShort = false
        failCreation = false
        holdAllBuffers = false
        maxImages = 4
        created = 0
        closed = 0
        sizes.clear()
    }
}

class Plane internal constructor(val rowStride: Int, val pixelStride: Int, val buffer: ByteBuffer)

/**
 * `PixelCopy` is the only supported way to read a SurfaceView's composited layer, and it is
 * asynchronous, so the fake has to be too: `outcome` picks the three answers a real device gives -
 * success, refusal, and silence (the case that must not recycle the destination it may still write).
 */
object PixelCopyOutcome {
    var mode: String = "success"
    var requests = 0
    fun reset() {
        mode = "success"
        requests = 0
    }
}

class PixelCopy {
    fun interface OnPixelCopyFinishedListener {
        fun onPixelCopyFinished(copyResult: Int)
    }

    companion object {
        const val RESULT_SUCCESS = 1
        const val RESULT_UNKNOWN_ERROR = 0
        const val RESULT_ERROR_TIMEOUT = 2
        const val RESULT_ERROR_SOURCE_NOT_EMPTY = 4

        @JvmStatic
        fun request(
            view: android.view.SurfaceView,
            dest: android.graphics.Bitmap,
            listener: OnPixelCopyFinishedListener,
            handler: android.os.Handler
        ) {
            PixelCopyOutcome.requests++
            when (PixelCopyOutcome.mode) {
                "refuse" -> handler.post(Runnable { listener.onPixelCopyFinished(RESULT_UNKNOWN_ERROR) })
                // No callback at all: the caller has to give up without touching dest again.
                "hang" -> Unit
                "throw" -> throw IllegalArgumentException("surface is not available")
                else -> handler.post(Runnable {
                    android.view.fillBitmap(dest)
                    listener.onPixelCopyFinished(RESULT_SUCCESS)
                })
            }
        }

    }
}

class Image internal constructor(
    val width: Int,
    val height: Int,
    val planes: Array<Plane>,
    private val owner: ImageWriter?
) {
    var timestamp: Long = 0L
    var isClosed = false
        private set

    fun close() {
        if (isClosed) throw IllegalStateException("Image closed twice")
        isClosed = true
        owner?.release(this)
    }
}

class QueuedFrame(val timestamp: Long, val bytes: ByteArray, val width: Int, val height: Int, val rowStride: Int)

class ImageWriter private constructor(
    private val width: Int,
    private val height: Int,
    private val maxImages: Int
) {
    val stride = if (ImageWriterTestConfig.rowStride > 0) ImageWriterTestConfig.rowStride else width * 4
    val queued = ArrayList<QueuedFrame>()
    private val outstanding = ArrayList<Image>()
    private var next = 0
    var isClosed = false
        private set

    internal fun acquire(): Image? {
        if (isClosed) return null
        if (outstanding.size >= maxImages) return null
        val rows = if (ImageWriterTestConfig.capacityShort) height - 1 else height
        val buffer = ByteBuffer.allocateDirect(stride * rows).order(ByteOrder.nativeOrder())
        val image = Image(width, height, arrayOf(Plane(stride, 4, buffer)), this)
        outstanding.add(image)
        next++
        return image
    }

    internal fun release(image: Image) {
        outstanding.remove(image)
    }

    fun acquireNextImage(): Image? = synchronized(this) {
        if (ImageWriterTestConfig.holdAllBuffers) {
            while (outstanding.size < maxImages) {
                val buffer = ByteBuffer.allocateDirect(stride * height).order(ByteOrder.nativeOrder())
                outstanding.add(Image(width, height, arrayOf(Plane(stride, 4, buffer)), this))
            }
            return@synchronized null
        }
        acquire()
    }

    /** The codec's side of the exchange: whatever lands here is what a decoder would have seen. */
    fun queueInputImage(image: Image) {
        val snapshot = synchronized(this) {
            val plane = image.planes[0]
            val buffer = plane.buffer.duplicate().order(ByteOrder.nativeOrder())
            val bytes = ByteArray(buffer.capacity())
            val mark = buffer.position()
            buffer.position(0)
            buffer.get(bytes, 0, bytes.size)
            buffer.position(mark)
            QueuedFrame(image.timestamp, bytes, image.width, image.height, plane.rowStride)
        }
        runCatching { image.close() }
        synchronized(this) { queued.add(snapshot) }
    }

    fun close() {
        synchronized(this) {
            isClosed = true
            ImageWriterTestConfig.closed++
        }
    }

    companion object {
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun newInstance(surface: Surface, width: Int, height: Int, maxImages: Int): ImageWriter {
            ImageWriterTestConfig.created++
            ImageWriterTestConfig.sizes.add("${width}x$height/$maxImages")
            if (ImageWriterTestConfig.failCreation || surface.released) {
                throw IllegalArgumentException("this surface cannot be produced into")
            }
            require(maxImages in 1..31) { "maxImages" }
            return ImageWriter(width, height, ImageWriterTestConfig.maxImages.coerceAtLeast(1))
        }
    }
}
