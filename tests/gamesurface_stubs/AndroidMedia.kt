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
    val format = ArrayList<Int>()

    fun reset() {
        rowStride = 0
        capacityShort = false
        failCreation = false
        holdAllBuffers = false
        maxImages = 4
        created = 0
        closed = 0
        format.clear()
    }
}

class Plane internal constructor(val rowStride: Int, val pixelStride: Int, val buffer: ByteBuffer)

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
    format: Int,
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
        fun newInstance(surface: Surface, width: Int, height: Int, format: Int, maxImages: Int): ImageWriter {
            ImageWriterTestConfig.created++
            ImageWriterTestConfig.format.add(format)
            if (ImageWriterTestConfig.failCreation || surface.released) {
                throw IllegalArgumentException("this surface cannot be produced into")
            }
            require(maxImages in 1..31) { "maxImages" }
            return ImageWriter(width, height, format, ImageWriterTestConfig.maxImages)
        }
    }
}
