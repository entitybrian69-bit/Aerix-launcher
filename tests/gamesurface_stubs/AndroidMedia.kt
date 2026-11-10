/*
 * Test doubles for `android.media.Image` / `ImageWriter`, see AndroidGraphics.kt for why they exist.
 * `ImageWriterTestConfig` is what makes the interesting cases reachable without a device: a padded row
 * stride (the case that needs the row-by-row copy), a multi-plane YUV layout (must be refused), a buffer
 * smaller than the frame (must be refused rather than half-written), and a surface whose geometry differs
 * from what the recorder asked for (the feed must follow the surface, since ImageWriter has no public way
 * to request a size).
 */
package android.media

import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImageWriterTestConfig {
    var rowStride: Int = 0
    var planes: Int = 1
    var pixelStride: Int = 4
    var capacityShort: Boolean = false
    var failCreation: Boolean = false
    var holdAllBuffers: Boolean = false
    var maxImages: Int = 4

    /** The size the *surface* reports, which is what a real ImageWriter would derive its images from. */
    var surfaceWidth: Int = 0
    var surfaceHeight: Int = 0

    /** Used when the surface geometry is unset, mirroring how a real producer window falls back. */
    var fallbackWidth: Int = 0
    var fallbackHeight: Int = 0

    /** A surface that reports no geometry at all: the feed must then use the size it was asked for. */
    var reportZeroSize: Boolean = false

    var created: Int = 0
    var closed: Int = 0
    val sizes = ArrayList<String>()
    var reportedWidth: Int = 0
    var reportedHeight: Int = 0

    fun reset() {
        rowStride = 0
        planes = 1
        pixelStride = 4
        capacityShort = false
        failCreation = false
        holdAllBuffers = false
        maxImages = 4
        surfaceWidth = 0
        surfaceHeight = 0
        reportZeroSize = false
        fallbackWidth = 0
        fallbackHeight = 0
        created = 0
        closed = 0
        reportedWidth = 0
        reportedHeight = 0
        sizes.clear()
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

class ImageWriter private constructor(maxImages: Int) {
    private val geometryWidth: Int
        get() = ImageWriterTestConfig.surfaceWidth.takeIf { it > 0 }
            ?: ImageWriterTestConfig.fallbackWidth

    private val geometryHeight: Int
        get() = ImageWriterTestConfig.surfaceHeight.takeIf { it > 0 }
            ?: ImageWriterTestConfig.fallbackHeight



    private val stride: Int
        get() = ImageWriterTestConfig.rowStride.takeIf { it > 0 } ?: geometryWidth * 4

    val queued = ArrayList<QueuedFrame>()
    private val outstanding = ArrayList<Image>()
    private val pool = maxImages
    var isClosed = false
        private set

    private fun newImage(): Image {
        val rows = if (ImageWriterTestConfig.capacityShort) (geometryHeight - 1).coerceAtLeast(1) else geometryHeight
        val buffer = ByteBuffer.allocateDirect(stride * rows).order(ByteOrder.nativeOrder())
        val planes = Array(ImageWriterTestConfig.planes) { Plane(stride, ImageWriterTestConfig.pixelStride, buffer) }
        val w = if (ImageWriterTestConfig.reportZeroSize) 0 else geometryWidth
        val h = if (ImageWriterTestConfig.reportZeroSize) 0 else geometryHeight
        return Image(w, h, planes, this)
    }

    internal fun release(image: Image) {
        outstanding.remove(image)
    }

    /**
     * The documented contract, kept exactly: `ImageWriter` has no `acquireNextImage`, and
     * `dequeueInputImage` *throws* rather than returning null when every image is already out. The fake
     * throws too (it does not model the blocking half of the real call, which no assertion depends on).
     */
    fun dequeueInputImage(): Image = synchronized(this) {
        if (isClosed) throw IllegalStateException("the writer is closed")
        if (ImageWriterTestConfig.holdAllBuffers) {
            while (outstanding.size < pool) outstanding.add(newImage())
        }
        if (outstanding.size >= pool) {
            throw IllegalStateException("Already dequeued max number of Images $pool")
        }
        newImage().also { outstanding.add(it) }
    }

    val maxImages: Int get() = pool

    /** `ImageWriter.getWidth`/`getHeight` are public, and the feed logs them; -1 means "from the surface". */
    val width: Int get() = ImageWriterTestConfig.reportedWidth
    val height: Int get() = ImageWriterTestConfig.reportedHeight

    /** The codec's side of the exchange: whatever lands here is what a decoder would have seen. */
    fun queueInputImage(image: Image) {
        val snapshot = synchronized(this) {
            val plane = image.planes[0]
            val buffer = plane.buffer.duplicate().order(ByteOrder.nativeOrder())
            val bytes = ByteArray(buffer.capacity())
            buffer.position(0)
            buffer.get(bytes, 0, bytes.size)
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
        /**
         * The SDK's public entry points, kept at the same arity: `maxImages` is the only knob, and the
         * geometry comes from the surface. There is deliberately no `(surface, width, height, maxImages)`
         * overload here, because inventing one is what cost a build.
         */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun newInstance(surface: Surface): ImageWriter = create(surface, DEFAULT_IMAGES)

        /** Present so `width`/`height` reads work the way they do on the real class. */
        @JvmStatic
        fun newInstance(surface: Surface, maxImages: Int, format: Int): ImageWriter = create(surface, maxImages)

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun newInstance(surface: Surface, maxImages: Int): ImageWriter = create(surface, maxImages)

        private const val DEFAULT_IMAGES = 12

        private fun create(surface: Surface, maxImages: Int): ImageWriter {
            ImageWriterTestConfig.created++
            ImageWriterTestConfig.sizes.add("maxImages=$maxImages")
            if (ImageWriterTestConfig.failCreation || surface.released) {
                throw IllegalArgumentException("this surface cannot be produced into")
            }
            require(maxImages in 1..31) { "maxImages must be at least 1" }
            return ImageWriter(maxImages.coerceAtLeast(1))
        }
    }
}
