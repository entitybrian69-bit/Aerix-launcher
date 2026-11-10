/*
 * Test doubles for the SDK types `GameSurfaceFeed` touches, used only by the offline harness in
 * tests/GameSurfaceFeedOfflineTest.kt. They are not in `src/main` and never ship; what they are for is
 * twofold: they make the Kotlin compiler check every Android signature the feed relies on (this repo has
 * no android.jar it can compile against), and they *behave* - a fake `ImageWriter` with a padded row stride
 * and a fake view that fills a recognizable pattern - so the copy math can be executed and asserted
 * instead of reasoned about. Signature shapes mirror the public SDK.
 */
package android.graphics

import java.nio.ByteBuffer
import java.nio.ByteOrder

class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

object PixelFormat {
    const val RGBA_8888 = 1
}

object PorterDuff {
    enum class Mode { CLEAR, SRC }
}

class Paint(flags: Int = 0) {
    companion object {
        const val FILTER_BITMAP_FLAG = 0x00000002
    }
}

class Bitmap private constructor(
    val width: Int,
    val height: Int,
    private val strideRows: Int
) {
    enum class Config { ARGB_8888, ALPHA_8, RGB_565 }

    /** Packed ARGB pixels, one int per pixel, `strideRows` ints per row. */
    val data = IntArray(strideRows * height)
    var isRecycled = false
        private set

    /**
     * The SDK exposes this in bytes, and a padded bitmap is the case that makes a bulk
     * `copyPixelsToBuffer` unsafe, so it is deliberately settable here.
     */
    val rowBytes: Int get() = strideRows * 4
    val byteCount: Int get() = rowBytes * height
    val allocationByteCount: Int get() = byteCount

    fun recycle() {
        isRecycled = true
    }

    fun getPixels(pixels: IntArray, stride: Int, offset: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) {
            for (column in 0 until w) {
                val from = (y + row) * strideRows + (x + column)
                val to = offset + row * stride + column
                pixels[to] = data[from]
            }
        }
    }

    fun setPixels(src: IntArray, stride: Int, offset: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) {
            for (column in 0 until w) {
                data[(y + row) * strideRows + (x + column)] = src[offset + row * stride + column]
            }
        }
    }

    fun copyPixelsToBuffer(dst: ByteBuffer) {
        val out = dst.order(ByteOrder.nativeOrder())
        for (row in 0 until height) {
            for (column in 0 until strideRows) {
                out.putInt(data[row * strideRows + column])
            }
        }
    }

    fun eraseColor(color: Int) {
        data.fill(color)
    }

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap = Bitmap(width, height, width)
        fun createBitmap(width: Int, height: Int, config: Config, strideRows: Int): Bitmap =
            Bitmap(width, height, strideRows)
    }
}

class Canvas(bitmap: Bitmap? = null) {
    private var target: Bitmap? = bitmap

    fun setBitmap(bitmap: Bitmap?) {
        target = bitmap
    }

    fun drawColor(color: Int, mode: PorterDuff.Mode) {
        if (mode == PorterDuff.Mode.CLEAR) target?.eraseColor(0)
    }

    /**
     * The only draw the feed performs: a nearest/bilinear-free whole-image scale into the staging bitmap.
     * Sampling one pixel per destination cell is enough to prove the geometry - a wrong destination size
     * or a swapped axis shows up in the assertions.
     */
    fun drawBitmap(source: Bitmap, srcRect: Rect?, dstRect: Rect, paint: Paint?) {
        val dst = target ?: return
        val left = dstRect.left
        val top = dstRect.top
        val right = dstRect.right
        val bottom = dstRect.bottom
        val scaleX = source.width.toDouble() / (right - left).coerceAtLeast(1)
        val scaleY = source.height.toDouble() / (bottom - top).coerceAtLeast(1)
        for (y in top until bottom) {
            for (x in left until right) {
                if (x < 0 || y < 0 || x >= dst.width || y >= dst.height) continue
                val sx = ((x - left) * scaleX).toInt().coerceAtMost(source.width - 1)
                val sy = ((y - top) * scaleY).toInt().coerceAtMost(source.height - 1)
                dst.data[y * dst.width + x] = source.data[sy * source.width + sx]
            }
        }
    }
}
