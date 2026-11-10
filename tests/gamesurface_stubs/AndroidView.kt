/*
 * Test doubles for the view/surface types, see AndroidGraphics.kt. The two readback paths the feed
 * supports have very different failure modes, so each fake can be told to refuse - that refusal, not the
 * happy path, is what the fallback exists for and what has to be provable without a device.
 */
package android.view

import android.graphics.Bitmap

open class View {
    var width: Int = 0
    var height: Int = 0
}

class Surface(private val valid: Boolean = true) {
    var released = false
        private set
    val isValid: Boolean get() = valid && !released

    fun release() {
        released = true
    }
}

/**
 * A `TextureView` hands back a bitmap it allocated; the fake fills a pattern that encodes each pixel's
 * coordinates, so a stride or row-offset mistake in the copy is visible as a wrong pixel, not a blur.
 */
class TextureView : View() {
    var isAvailable = true
    var refuseReadback = false

    fun getBitmap(width: Int, height: Int): Bitmap? {
        if (refuseReadback || !isAvailable || width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        fill(bitmap)
        return bitmap
    }

    companion object {
        internal fun fill(bitmap: Bitmap) {
            var index = 0
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    bitmap.data[index++] = (y shl 16) or (x shl 8) or 0xAA
                }
            }
        }
    }
}

/** A `SurfaceView` writes into the caller's bitmap and is the one that can simply say no. */
class SurfaceView : View() {
    var refuseReadback = false
    var reads = 0

    fun getBitmap(dest: Bitmap): Boolean {
        reads++
        if (refuseReadback) return false
        if (dest.width != width || dest.height != height) return false
        TextureView.fill(dest)
        return true
    }
}
